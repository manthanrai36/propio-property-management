package com.propio.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchResultDto {

    private String category; // PROPERTY, TENANT, LEASE, PAYMENT
    private String categoryDisplayName;
    private String title;
    private String subtitle;
    private String badge;
    private String badgeClass;
    private String url;
    private String icon;
}
