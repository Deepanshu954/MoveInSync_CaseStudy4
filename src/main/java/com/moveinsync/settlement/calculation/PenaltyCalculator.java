package com.moveinsync.settlement.calculation;

import com.moveinsync.settlement.entity.Driver;
import com.moveinsync.settlement.entity.PenaltyPolicy;
import com.moveinsync.settlement.entity.SettlementLine;
import com.moveinsync.settlement.entity.TripViolation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Calculates penalty deductions for safety and SLA violations.
 * Groups violations by frequency window (daily, weekly, monthly), enforces max occurrence caps,
 * and ignores dismissed violations. Deductions are recorded as negative amounts.
 *
 * @author Deepanshu
 */
@Component
@Slf4j
public class PenaltyCalculator {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    /**
     * Calculates deduction line items for safety violations subject to policy capping.
     *
     * @param violations collection of telematics violations recorded in the billing cycle
     * @param policies   active penalty deduction policies
     * @return list of debit (negative signed) {@link CalculatedLineItem} deduction records
     */
    public List<CalculatedLineItem> calculate(List<TripViolation> violations, List<PenaltyPolicy> policies) {
        if (violations == null || violations.isEmpty() || policies == null || policies.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. Filter ACTIVE violations only
        List<TripViolation> activeViolations = violations.stream()
                .filter(v -> v.getStatus() == TripViolation.ViolationStatus.ACTIVE)
                .filter(v -> v.getTrip() != null && v.getTrip().getDriver() != null)
                .sorted(Comparator.comparing(TripViolation::getEventAt))
                .toList();

        // 2. Group by Driver and ViolationType
        Map<Driver, Map<PenaltyPolicy.ViolationType, List<TripViolation>>> grouped = activeViolations.stream()
                .collect(Collectors.groupingBy(
                        v -> v.getTrip().getDriver(),
                        LinkedHashMap::new,
                        Collectors.groupingBy(TripViolation::getViolationType, LinkedHashMap::new, Collectors.toList())
                ));

        List<CalculatedLineItem> penaltyLines = new ArrayList<>();

        for (Map.Entry<Driver, Map<PenaltyPolicy.ViolationType, List<TripViolation>>> driverEntry : grouped.entrySet()) {
            Driver driver = driverEntry.getKey();

            for (Map.Entry<PenaltyPolicy.ViolationType, List<TripViolation>> typeEntry : driverEntry.getValue().entrySet()) {
                PenaltyPolicy.ViolationType violationType = typeEntry.getKey();
                List<TripViolation> typeViolations = typeEntry.getValue();

                // Find matching policy
                PenaltyPolicy policy = findMatchingPolicy(driver, violationType, policies);
                if (policy == null) {
                    log.warn("No active penalty policy found for violation type: {}", violationType);
                    continue;
                }

                // Partition into frequency windows
                Map<String, List<TripViolation>> windowBuckets = new LinkedHashMap<>();
                for (TripViolation v : typeViolations) {
                    String windowKey = resolveWindowKey(v.getEventAt(), policy.getFrequencyWindow());
                    windowBuckets.computeIfAbsent(windowKey, k -> new ArrayList<>()).add(v);
                }

                // Apply frequency capping per bucket
                for (Map.Entry<String, List<TripViolation>> bucket : windowBuckets.entrySet()) {
                    String windowKey = bucket.getKey();
                    List<TripViolation> bucketViolations = bucket.getValue();
                    Integer maxCap = policy.getMaxOccurrences();

                    int occurrenceIndex = 1;
                    for (TripViolation violation : bucketViolations) {
                        boolean capped = (maxCap != null && occurrenceIndex > maxCap);

                        if (!capped) {
                            BigDecimal penaltyAmount = policy.getAmount().negate().setScale(2, RoundingMode.HALF_UP);

                            Map<String, Object> details = new LinkedHashMap<>();
                            details.put("driverId", driver.getId());
                            details.put("violationId", violation.getId());
                            details.put("violationType", violationType.name());
                            details.put("policyId", policy.getId());
                            details.put("frequencyWindow", policy.getFrequencyWindow().name());
                            details.put("windowKey", windowKey);
                            details.put("occurrenceNumber", occurrenceIndex);
                            details.put("maxAllowedOccurrences", maxCap != null ? maxCap : "UNLIMITED");
                            details.put("eventAt", violation.getEventAt().toString());
                            details.put("deductionAmount", penaltyAmount.toPlainString());

                            CalculatedLineItem line = new CalculatedLineItem(
                                    driver,
                                    SettlementLine.LineType.PENALTY,
                                    "TRIP_VIOLATION",
                                    violation.getId(),
                                    String.format("Safety deduction for %s (occur %d in %s)", violationType, occurrenceIndex, windowKey),
                                    penaltyAmount,
                                    null,
                                    policy.getVersion(),
                                    details
                            );

                            penaltyLines.add(line);
                        } else {
                            log.info("Violation {} for driver {} capped (occur {} > max {}) in window {}",
                                    violation.getId(), driver.getId(), occurrenceIndex, maxCap, windowKey);
                        }

                        occurrenceIndex++;
                    }
                }
            }
        }

        return penaltyLines;
    }

    private PenaltyPolicy findMatchingPolicy(Driver driver, PenaltyPolicy.ViolationType type, List<PenaltyPolicy> policies) {
        return policies.stream()
                .filter(p -> p.getViolationType() == type)
                .filter(p -> p.getVendor() == null || (driver.getVendor() != null && Objects.equals(p.getVendor().getId(), driver.getVendor().getId())))
                .max(Comparator.comparing(PenaltyPolicy::getVersion))
                .orElse(null);
    }

    private String resolveWindowKey(java.time.Instant eventAt, PenaltyPolicy.FrequencyWindow window) {
        LocalDate localDate = eventAt.atZone(IST_ZONE).toLocalDate();
        return switch (window) {
            case DAILY -> localDate.toString(); // YYYY-MM-DD
            case WEEKLY -> localDate.get(IsoFields.WEEK_BASED_YEAR) + "-W" + String.format("%02d", localDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case MONTHLY -> YearMonth.from(localDate).toString(); // YYYY-MM
        };
    }
}
