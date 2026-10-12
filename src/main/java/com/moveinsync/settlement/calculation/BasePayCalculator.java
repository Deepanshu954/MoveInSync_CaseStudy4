package com.moveinsync.settlement.calculation;

import com.moveinsync.settlement.common.exception.PolicyViolationException;
import com.moveinsync.settlement.entity.ContractRate;
import com.moveinsync.settlement.entity.SettlementLine;
import com.moveinsync.settlement.entity.Trip;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * Calculates base pay for completed trips based on active contract rate cards.
 * Handles rate card validity intervals (effectiveFrom <= completedAt < effectiveTo)
 * and resolves version precedence when multiple rates match.
 *
 * @author Deepanshu
 */
@Component
@Slf4j
public class BasePayCalculator {

    
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

    @SuppressWarnings("null")
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
                .max(Comparator.comparing((ContractRate r) -> r.getEffectiveFrom())
                        .thenComparing(r -> r.getVersion()))
                .orElse(null);
    }
}
