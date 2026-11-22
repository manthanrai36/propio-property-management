package com.propio.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Comment or timeline entry in a maintenance request thread, visible to both admin and tenant.
 */
@Entity
@Table(name = "maintenance_comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceComment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private MaintenanceRequest request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User author;

    @Column(name = "author_name", nullable = false, length = 120)
    private String authorName;

    @Column(name = "author_role", nullable = false, length = 30)
    private String authorRole; // "ADMIN" or "TENANT"

    @NotBlank(message = "Comment text cannot be blank")
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "is_status_change", nullable = false)
    @Builder.Default
    private boolean statusChange = false;

    @Column(name = "new_status", length = 30)
    private String newStatus;
}
