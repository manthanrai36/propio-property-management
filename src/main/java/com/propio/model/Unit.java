package com.propio.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Individual leasable unit inside a property.
 */
@Entity
@Table(name = "units")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Unit extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Unit number is required")
    @Column(name = "unit_number", nullable = false, length = 50)
    private String unitNumber;

    @Column(name = "bedrooms")
    private int bedrooms;

    @Column(name = "bathrooms")
    private int bathrooms;

    @Column(name = "square_feet")
    private Double squareFeet;

    @NotNull(message = "Rent amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Rent must be greater than zero")
    @Column(name = "rent_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal rentAmount;

    @Column(name = "deposit_amount", precision = 12, scale = 2)
    private BigDecimal depositAmount;

    @NotNull(message = "Unit status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UnitStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Builder.Default
    @OneToMany(mappedBy = "unit", cascade = CascadeType.ALL)
    private List<Lease> leases = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "unit", cascade = CascadeType.ALL)
    private List<MaintenanceRequest> maintenanceRequests = new ArrayList<>();
}
