package com.propio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyFilterDto {

    private String search;
    private String propertyType;
    private String status; // "ALL", "VACANT", "OCCUPIED", "BOOKED"
    private String sortBy; // "name_asc", "name_desc", "units_desc", "newest"

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 6;
}
