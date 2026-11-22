package com.propio.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * Result metrics returned by the automated rent engine billing cycle run.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingCycleResult {

    private int generatedInvoicesCount;
    private int markedOverdueCount;
    private int lateFeesAppliedCount;
    private int skippedDuplicatesCount;
    private BigDecimal totalLateFeesAssessed;
    private String summaryMessage;
    private String triggerSource;
}
