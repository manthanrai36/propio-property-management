package com.propio.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Form DTO for creating or modifying a lease agreement.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaseFormDto {

    private Long id;

    @NotNull(message = "Please select a tenant")
    private Long tenantId;

    @NotNull(message = "Please select a property unit")
    private Long unitId;

    @NotNull(message = "Start date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @NotNull(message = "Monthly rent is required")
    @DecimalMin(value = "0.01", message = "Monthly rent must be greater than zero")
    private BigDecimal monthlyRent;

    @DecimalMin(value = "0.00", message = "Security deposit cannot be negative")
    private BigDecimal securityDeposit;

    private String notes;
}
