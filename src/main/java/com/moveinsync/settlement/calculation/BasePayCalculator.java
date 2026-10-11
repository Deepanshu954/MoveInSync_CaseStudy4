package com.moveinsync.settlement.calculation;

import com.moveinsync.settlement.common.exception.PolicyViolationException;
import com.moveinsync.settlement.entity.ContractRate;
import com.moveinsync.settlement.entity.SettlementLine;
import com.moveinsync.settlement.entity.Trip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * Pure calculation engine resolving base trip compensation from time-sliced contract rate cards.
 *
 * <p>Business Rules:
 * <ol>
 *   <li><b>Duty Type Mapping:</b>
 *     <ul>
 *       <li>{@link Trip.DutyType#TRIP} maps to {@link ContractRate.ComponentType#PER_TRIP}</li>
 *       <li>{@link Trip.DutyType#DUTY} maps to {@link ContractRate.ComponentType#PER_DUTY}</li>
 *     </ul>
 *   </li>
 *   <li><b>Mid-Month Half-Open Validity Interval:</b>
 *     A rate card applies to a completed trip if and only if:
 *     <pre>
 *       rate.effectiveFrom &le; trip.completedAt &lt; rate.effectiveTo (or rate.effectiveTo is NULL)
 *     </pre>
 *   </li>
 *   <li><b>Version Precedence:</b> If multiple rate schedules match a trip's completion timestamp,
 *       the one with the latest {@code effectiveFrom} and highest {@code version} is selected.</li>
 *   <li><b>Completeness Guarantee:</b> If no active rate card matches the trip timestamp,
 *       a {@link PolicyViolationException} is thrown immediately to prevent silent billing omission.</li>
 * </ol>
 *
 * @author MoveInSync Core Settlement Team
 */
@Component
public class BasePayCalculator {

    private static final Logger log = LoggerFactory.getLogger(BasePayCalculator.class);

    /**
     * Calculates base pay line items for a collection of commute trips against available contract rates.
     *
     * @param trips          collection of completed trips to evaluate
     * @param availableRates pool of candidate contract rate cards
     * @return list of credit {@link CalculatedLineItem} records
     * @throws PolicyViolationException if any trip cannot be priced against an effective rate card
     */
    public List<CalculatedLineItem> calculate(List<Trip> trips, List<ContractRate> availableRates) {
        if (trips == null || trips.isEmpty()) {
            return Collections.emptyList();
        }

        List<CalculatedLineItem> lineItems = new ArrayList<>(trips.size());

        for (Trip trip : trips) {
            ContractRate matchingRate = findMatchingRate(trip, availableRates);
            if (matchingRate == null) {
                String errorMsg = String.format(
                        "No effective contract rate found for trip ref: %s, contract: %s, dutyType: %s at completedAt: %s",
                        trip.getExternalRef(),
                        trip.getContract() != null ? trip.getContract().getId() : "null",
                        trip.getDutyType(),
                        trip.getCompletedAt()
                );
                log.error(errorMsg);
                throw new PolicyViolationException(errorMsg);
            }

            BigDecimal lineAmount = matchingRate.getAmount().setScale(2, RoundingMode.HALF_UP);

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("tripRef", trip.getExternalRef());
            details.put("dutyType", trip.getDutyType().name());
            details.put("completedAt", trip.getCompletedAt().toString());
            details.put("contractId", matchingRate.getContract().getId());
            details.put("rateId", matchingRate.getId());
            details.put("rateVersion", matchingRate.getVersion());
            details.put("rateAmount", matchingRate.getAmount().toPlainString());

            CalculatedLineItem line = new CalculatedLineItem(
                    trip.getDriver(),
                    SettlementLine.LineType.BASE_PAY,
                    "TRIP",
                    trip.getId(),
                    String.format("Base pay for trip %s (%s)", trip.getExternalRef(), trip.getDutyType()),
                    lineAmount,
                    matchingRate.getVersion(),
                    null,
                    details
            );

            lineItems.add(line);
        }

        return lineItems;
    }

    private ContractRate findMatchingRate(Trip trip, List<ContractRate> availableRates) {
        ContractRate.ComponentType expectedComponent = (trip.getDutyType() == Trip.DutyType.TRIP)
                ? ContractRate.ComponentType.PER_TRIP
                : ContractRate.ComponentType.PER_DUTY;

        Instant completedAt = trip.getCompletedAt();
        Long contractId = trip.getContract() != null ? trip.getContract().getId() : null;

        return availableRates.stream()
                .filter(r -> r.getContract() != null && Objects.equals(r.getContract().getId(), contractId))
                .filter(r -> r.getComponentType() == expectedComponent)
                .filter(r -> !completedAt.isBefore(r.getEffectiveFrom())) // completedAt >= effectiveFrom
                .filter(r -> r.getEffectiveTo() == null || completedAt.isBefore(r.getEffectiveTo())) // completedAt < effectiveTo
                .max(Comparator.comparing(ContractRate::getEffectiveFrom)
                        .thenComparing(ContractRate::getVersion))
                .orElse(null);
    }
}
