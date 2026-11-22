package com.propio.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Form DTO for Tenant simulated online payment checkout.
 * NO real card data is processed or stored.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulatedCheckoutDto {

    @NotBlank(message = "Cardholder name is required")
    private String cardholderName;

    private String cardNumberMasked;

    private String expiry;

    private String cvv;

    @Builder.Default
    private String simulationOutcome = "SUCCESS"; // SUCCESS or FAILURE
}
