package com.propio.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportSummaryDto {

    private LocalDate startDate;
    private LocalDate endDate;

    private BigDecimal totalRevenueCollected;
    private BigDecimal totalOutstandingOverdue;
    private BigDecimal totalInvoiced;

    private long totalProperties;
    private long totalUnits;
    private long occupiedUnits;
    private long vacantUnits;
    private long maintenanceUnits;
    private double occupancyRate;

    private double collectionRate;

    @Builder.Default
    private List<MonthlyRevenueItem> monthlyRevenue = new ArrayList<>();

    @Builder.Default
    private List<PropertyReportItem> propertyReports = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlyRevenueItem {
        private String monthLabel; // e.g. "Jan 2026"
        private String monthKey;   // e.g. "2026-01"
        private BigDecimal revenue;
        private BigDecimal overdue;
        private long transactionCount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PropertyReportItem {
        private Long propertyId;
        private String propertyName;
        private String city;
        private long totalUnits;
        private long occupiedUnits;
        private double occupancyRate;
        private BigDecimal revenueCollected;
        private BigDecimal outstandingOverdue;
        private double collectionRate;
    }
}
