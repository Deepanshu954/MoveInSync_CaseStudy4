package com.moveinsync.settlement.calculation;

import com.moveinsync.settlement.entity.Driver;
import com.moveinsync.settlement.entity.IncentivePolicy;
import com.moveinsync.settlement.entity.SettlementLine;
import com.moveinsync.settlement.entity.Trip;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Calculates driver incentives such as on-time bonuses, attendance bonuses,
 * and days-worked milestones based on active incentive policies.
 *
 * @author Deepanshu
 */
@Component
@Slf4j
public class IncentiveCalculator {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    /**
     * Calculates bonus incentive line items for all drivers active in the settlement window.
     *
     * @param trips       all completed commute trips within the billing cycle
     * @param policies    pool of candidate incentive policies
     * @param periodStart billing cycle start date (inclusive)
     * @param periodEnd   billing cycle end date (inclusive)
     * @return list of credit {@link CalculatedLineItem} bonus entries
     */
    public List<CalculatedLineItem> calculate(
            List<Trip> trips,
            List<IncentivePolicy> policies,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
        if (trips == null || trips.isEmpty() || policies == null || policies.isEmpty()) {
            return Collections.emptyList();
        }

        // Group trips by driver
        Map<Driver, List<Trip>> tripsByDriver = trips.stream()
                .filter(t -> t.getDriver() != null)
                .collect(Collectors.groupingBy(Trip::getDriver, LinkedHashMap::new, Collectors.toList()));

        List<CalculatedLineItem> incentiveLines = new ArrayList<>();

        for (Map.Entry<Driver, List<Trip>> entry : tripsByDriver.entrySet()) {
            Driver driver = entry.getKey();
            List<Trip> driverTrips = entry.getValue();

            // 1. Calculate trip volume and on-time performance
            long totalTrips = driverTrips.size();
            long onTimeCount = driverTrips.stream()
                    .filter(t -> Boolean.TRUE.equals(t.getIsOnTime()))
                    .count();

            BigDecimal onTimePercent = (totalTrips > 0)
                    ? BigDecimal.valueOf(onTimeCount)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalTrips), 4, RoundingMode.HALF_UP)
                    .setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            // 2. Calculate distinct calendar days worked in IST
            Set<LocalDate> distinctDaysWorked = driverTrips.stream()
                    .map(t -> t.getCompletedAt().atZone(IST_ZONE).toLocalDate())
                    .filter(d -> !d.isBefore(periodStart) && !d.isAfter(periodEnd))
                    .collect(Collectors.toSet());
            int distinctDays = distinctDaysWorked.size();

            // 3. Evaluate each policy
            for (IncentivePolicy policy : policies) {
                if (!isPolicyEffectiveForPeriod(policy, periodStart, periodEnd)) {
                    continue;
                }

                // Check optional vendor scoping
                if (policy.getVendor() != null && driver.getVendor() != null
                        && !Objects.equals(policy.getVendor().getId(), driver.getVendor().getId())) {
                    continue;
                }

                boolean qualified = false;
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("driverId", driver.getId());
                details.put("policyId", policy.getId());
                details.put("policyType", policy.getType().name());
                details.put("totalTrips", totalTrips);
                details.put("onTimeTrips", onTimeCount);
                details.put("onTimePercent", onTimePercent.toPlainString());
                details.put("distinctDaysWorked", distinctDays);

                switch (policy.getType()) {
                    case ON_TIME_BONUS -> {
                        if (policy.getThresholdPercent() != null) {
                            details.put("thresholdPercent", policy.getThresholdPercent().toPlainString());
                            if (onTimePercent.compareTo(policy.getThresholdPercent()) >= 0) {
                                qualified = true;
                            }
                        }
                    }
                    case WORKED_DAYS_BONUS, ATTENDANCE_BONUS -> {
                        if (policy.getThresholdValue() != null) {
                            details.put("thresholdDays", policy.getThresholdValue());
                            if (distinctDays >= policy.getThresholdValue()) {
                                qualified = true;
                            }
                        }
                    }
                }

                if (qualified) {
                    BigDecimal bonusAmount = policy.getAmount().setScale(2, RoundingMode.HALF_UP);
                    details.put("bonusAmount", bonusAmount.toPlainString());

                    CalculatedLineItem line = new CalculatedLineItem(
                            driver,
                            SettlementLine.LineType.INCENTIVE,
                            "INCENTIVE_POLICY",
                            policy.getId(),
                            String.format("Incentive reward: %s (₹%s)", policy.getType(), bonusAmount.toPlainString()),
                            bonusAmount,
                            null,
                            policy.getVersion(),
                            details
                    );

                    incentiveLines.add(line);
                    log.debug("Driver {} earned incentive: {}", driver.getId(), line);
                }
            }
        }

        return incentiveLines;
    }

    private boolean isPolicyEffectiveForPeriod(IncentivePolicy policy, LocalDate start, LocalDate end) {
        Instant periodStartInstant = start.atStartOfDay(IST_ZONE).toInstant();
        Instant periodEndInstant = end.plusDays(1).atStartOfDay(IST_ZONE).toInstant();

        // Effective interval intersects billing cycle
        boolean startsBeforePeriodEnd = !policy.getEffectiveFrom().isAfter(periodEndInstant);
        boolean endsAfterPeriodStart = policy.getEffectiveTo() == null || policy.getEffectiveTo().isAfter(periodStartInstant);
        return startsBeforePeriodEnd && endsAfterPeriodStart;
    }
}
