package com.propio.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * Summary totals across payment statuses for dashboard cards and table totals row.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTotalsDto {

    private BigDecimal totalCollected;
    private BigDecimal totalPending;
    private BigDecimal totalOverdue;
    private BigDecimal totalInvoiced;

    private long countPaid;
    private long countPending;
    private long countOverdue;
    private long countTotal;
}
