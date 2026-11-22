package com.propio.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AdminSettingsDto {

    @NotNull(message = "Late fee percentage is required")
    @DecimalMin(value = "0.0", message = "Late fee percentage cannot be negative")
    @DecimalMax(value = "50.0", message = "Late fee percentage cannot exceed 50%")
    private BigDecimal lateFeePercentage;

    @NotNull(message = "Invoice generation day is required")
    @Min(value = 1, message = "Invoice day must be between 1 and 28")
    @Max(value = 28, message = "Invoice day must be between 1 and 28")
    private Integer invoiceDay;

    @NotNull(message = "Reminder days is required")
    @Min(value = 1, message = "Reminder must be at least 1 day before due date")
    @Max(value = 30, message = "Reminder cannot exceed 30 days")
    private Integer reminderDays;
}
