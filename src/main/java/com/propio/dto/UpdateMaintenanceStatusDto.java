package com.propio.dto;

import com.propio.model.RequestStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateMaintenanceStatusDto {

    @NotNull(message = "New status is required")
    private RequestStatus status;

    private String statusNotes;

    private String assignedTechnician;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate scheduledDate;

    private BigDecimal estimatedCost;

    private BigDecimal actualCost;
}
