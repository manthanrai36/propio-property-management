package com.propio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Null-safe View DTO for rendering Tenant Portal Dashboard without exposing lazy JPA entities.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantDashboardDto {

    private boolean hasActiveLease;
    private String propertyName;
    private String unitNumber;
    private String propertyAddress;
    private BigDecimal monthlyRent;
    private LocalDate leaseEndDate;

    private BigDecimal currentBalanceDue;
    private boolean hasOverduePayment;
    private int openTicketsCount;
}
