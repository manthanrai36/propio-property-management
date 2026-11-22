package com.propio.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Maintenance ticket submitted by tenant or property manager for a unit.
 */
@Entity
@Table(name = "maintenance_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Unit reference is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

    @NotNull(message = "Tenant reference is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @NotBlank(message = "Title is required")
    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(name = "category", length = 50)
    private String category = "General";

    @Builder.Default
    @Column(name = "priority", length = 30)
    private String priority = "MEDIUM";

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RequestStatus status;

    @Column(name = "assigned_technician", length = 120)
    private String assignedTechnician;

    @Column(name = "photo_url", length = 255)
    private String photoUrl;

    @Column(name = "estimated_cost", precision = 12, scale = 2)
    private BigDecimal estimatedCost;

    @Column(name = "actual_cost", precision = 12, scale = 2)
    private BigDecimal actualCost;

    @Column(name = "requested_date", nullable = false)
    private LocalDate requestedDate;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "resolved_date")
    private LocalDate resolvedDate;

    @Column(name = "closed_date")
    private LocalDate closedDate;

    @Column(name = "status_notes", columnDefinition = "TEXT")
    private String statusNotes;

    @Builder.Default
    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<MaintenanceComment> comments = new ArrayList<>();

    public void addComment(MaintenanceComment comment) {
        comments.add(comment);
        comment.setRequest(this);
    }
}
