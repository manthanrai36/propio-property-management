package com.propio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyCardDto {
    private Long id;
    private String name;
    private String address;
    private String city;
    private String state;
    private String zipCode;
    private String propertyType;
    private String description;
    private String imageUrl;
    private int totalUnits;
    private int occupiedUnits;
    private int vacantUnits;
    private int bookedUnits;
    private BigDecimal minRent;
    private BigDecimal maxRent;
    private String statusBadge;
    private String gradientFallback;

    public String getFullAddress() {
        StringBuilder sb = new StringBuilder(address != null ? address : "");
        if (city != null && !city.isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(city);
        }
        if (state != null && !state.isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(state);
        }
        if (zipCode != null && !zipCode.isBlank()) {
            sb.append(" ").append(zipCode);
        }
        return sb.toString();
    }

    public String getRentRangeDisplay() {
        if (minRent == null && maxRent == null) {
            return "No rent set";
        }
        if (minRent != null && maxRent != null) {
            if (minRent.compareTo(maxRent) == 0) {
                return "$" + minRent.toPlainString() + "/mo";
            }
            return "$" + minRent.toPlainString() + " - $" + maxRent.toPlainString() + "/mo";
        }
        return "$" + (minRent != null ? minRent : maxRent).toPlainString() + "/mo";
    }
}
