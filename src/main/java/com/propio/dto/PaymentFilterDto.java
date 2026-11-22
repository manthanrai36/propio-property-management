package com.propio.dto;

import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Filter parameters for Admin payment ledger pagination and search.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentFilterDto {

    private String status; // ALL, PAID, PENDING, OVERDUE

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    private String search;

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 10;
}
