package com.propio.dto;

import com.propio.model.RequestStatus;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceFilterDto {

    private RequestStatus status;
    private String priority;
    private String category;
    private Long propertyId;
    private String search;

    @Builder.Default
    private String viewMode = "board"; // "board" (Kanban) or "list"

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 15;
}
