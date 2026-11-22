package com.propio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Form DTO for Admin manually recording a rent payment (Cash, Bank Transfer, Card).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordPaymentDto {

    @NotNull(message = "Payment invoice ID is required")
    private Long paymentId;

    @NotBlank(message = "Payment method is required")
    private String paymentMethod; // CASH, BANK_TRANSFER, CARD

    @NotNull(message = "Paid date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate paidDate;

    private String notes;
}
