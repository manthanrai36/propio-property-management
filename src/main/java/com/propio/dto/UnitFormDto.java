package com.propio.dto;

import com.propio.model.UnitStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnitFormDto {

    private Long id;

    private Long propertyId;

    @NotBlank(message = "Unit number is required (e.g. Apt 101, Suite B)")
    @Size(max = 50, message = "Unit number must not exceed 50 characters")
    private String unitNumber;

    @NotNull(message = "Bedrooms count is required")
    @Min(value = 0, message = "Bedrooms cannot be negative")
    private Integer bedrooms;

    @NotNull(message = "Bathrooms count is required")
    @Min(value = 0, message = "Bathrooms cannot be negative")
    private Integer bathrooms;

    @NotNull(message = "Square footage is required")
    @DecimalMin(value = "50.0", message = "Square footage must be at least 50 sq ft")
    private Double squareFeet;

    @NotNull(message = "Monthly rent is required")
    @DecimalMin(value = "1.00", message = "Rent must be greater than zero")
    private BigDecimal rentAmount;

    @DecimalMin(value = "0.00", message = "Deposit must be zero or positive")
    private BigDecimal depositAmount;

    @NotNull(message = "Unit status is required")
    private UnitStatus status;
}
