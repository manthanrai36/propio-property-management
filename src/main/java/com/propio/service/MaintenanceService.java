package com.propio.service;

import com.propio.dto.*;
import com.propio.exception.ResourceNotFoundException;
import com.propio.model.*;
import com.propio.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service managing maintenance workflow, status transitions, technician assignment,
 * tenant isolation, and comment threads.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MaintenanceService {

    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final MaintenanceCommentRepository maintenanceCommentRepository;
    private final TenantRepository tenantRepository;
    private final LeaseRepository leaseRepository;
    private final UnitRepository unitRepository;
    private final FileStorageService fileStorageService;
    private final EmailService emailService;
    private final NotificationService notificationService;

    // =========================================================================
    // Tenant Workflow Operations
    // =========================================================================

    @Transactional
    public MaintenanceRequest createTenantRequest(CreateMaintenanceRequestDto dto, Long tenantId, User currentUser) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant profile not found for ID: " + tenantId));

        // Determine unit: from active lease or requested unit
        Unit unit = null;
        if (dto.getUnitId() != null) {
            unit = unitRepository.findById(dto.getUnitId())
                    .orElse(null);
        }
        if (unit == null) {
            List<Lease> activeLeases = leaseRepository.findByTenantIdAndActiveTrue(tenantId);
            if (!activeLeases.isEmpty()) {
                unit = activeLeases.get(0).getUnit();
            }
        }
        if (unit == null) {
            throw new IllegalArgumentException("No associated unit found for tenant. Please contact management.");
        }

        // Store optional photo attachment
        String photoUrl = null;
        if (dto.getPhotoFile() != null && !dto.getPhotoFile().isEmpty()) {
            photoUrl = fileStorageService.storeMaintenancePhoto(dto.getPhotoFile());
        }

        MaintenanceRequest request = MaintenanceRequest.builder()
                .unit(unit)
                .tenant(tenant)
                .title(dto.getTitle().trim())
                .description(dto.getDescription().trim())
                .category(dto.getCategory() != null ? dto.getCategory().trim() : "General")
                .priority(dto.getPriority() != null ? dto.getPriority().toUpperCase() : "MEDIUM")
                .status(RequestStatus.OPEN)
                .photoUrl(photoUrl)
                .requestedDate(LocalDate.now())
                .build();

        request = maintenanceRequestRepository.save(request);

        // Add initial audit timeline comment
        MaintenanceComment initialComment = MaintenanceComment.builder()
                .request(request)
                .author(currentUser)
                .authorName(currentUser != null ? currentUser.getFullName() : tenant.getFullName())
                .authorRole("TENANT")
                .content("Service request submitted for " + unit.getUnitNumber() + " (" + unit.getProperty().getName() + ").")
                .statusChange(true)
                .newStatus("OPEN")
                .build();
        maintenanceCommentRepository.save(initialComment);

        // Notify admins of new resident request
        try {
            notificationService.notifyAllAdmins(
                    "New Service Request #REQ-" + request.getId(),
                    tenant.getFullName() + " submitted: " + request.getTitle() + " (" + request.getCategory() + ")",
                    com.propio.model.NotificationType.MAINTENANCE_UPDATE,
                    "/maintenance"
            );
        } catch (Exception ex) {
            log.warn("Failed creating notification for new ticket #{}: {}", request.getId(), ex.getMessage());
        }

        log.info("Tenant #{} submitted maintenance request #{} ({})", tenantId, request.getId(), request.getTitle());
        return request;
    }

    @Transactional(readOnly = true)
    public List<MaintenanceRequest> getTenantRequests(Long tenantId) {
        return maintenanceRequestRepository.findByTenantIdWithDetails(tenantId);
    }

    @Transactional(readOnly = true)
    public MaintenanceRequest getRequestForTenant(Long requestId, Long tenantId) {
        MaintenanceRequest request = maintenanceRequestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance request #" + requestId + " not found."));

        if (!request.getTenant().getId().equals(tenantId)) {
            throw new AccessDeniedException("Forbidden: You are only authorized to view your own maintenance requests.");
        }
        return request;
    }

    // =========================================================================
    // Admin Operations & Workflow State Transitions
    // =========================================================================

    @Transactional(readOnly = true)
    public MaintenanceRequest getRequestById(Long requestId) {
        return maintenanceRequestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance request #" + requestId + " not found."));
    }

    @Transactional(readOnly = true)
    public Page<MaintenanceRequest> getAdminRequestsPaged(MaintenanceFilterDto filter) {
        int pageNum = Math.max(0, filter.getPage());
        int pageSize = filter.getSize() > 0 ? filter.getSize() : 15;
        Pageable pageable = PageRequest.of(pageNum, pageSize);

        return maintenanceRequestRepository.searchRequestsWithDetails(
                filter.getStatus(),
                filter.getPriority(),
                filter.getCategory(),
                filter.getPropertyId(),
                filter.getSearch(),
                pageable);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceRequest> getAllAdminRequestsFiltered(MaintenanceFilterDto filter) {
        return maintenanceRequestRepository.findAllWithDetails().stream()
                .filter(req -> {
                    if (filter.getStatus() != null && req.getStatus() != filter.getStatus()) {
                        // Allow grouping OPEN and PENDING
                        if (!(filter.getStatus() == RequestStatus.OPEN && req.getStatus().isOpen())) {
                            return false;
                        }
                    }
                    if (filter.getPriority() != null && !filter.getPriority().isBlank()) {
                        if (!filter.getPriority().equalsIgnoreCase(req.getPriority())) return false;
                    }
                    if (filter.getCategory() != null && !filter.getCategory().isBlank()) {
                        if (!filter.getCategory().equalsIgnoreCase(req.getCategory())) return false;
                    }
                    if (filter.getPropertyId() != null) {
                        if (req.getUnit() == null || req.getUnit().getProperty() == null ||
                            !filter.getPropertyId().equals(req.getUnit().getProperty().getId())) return false;
                    }
                    if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                        String s = filter.getSearch().toLowerCase();
                        boolean match = req.getTitle().toLowerCase().contains(s) ||
                                (req.getDescription() != null && req.getDescription().toLowerCase().contains(s)) ||
                                (req.getAssignedTechnician() != null && req.getAssignedTechnician().toLowerCase().contains(s)) ||
                                req.getTenant().getFullName().toLowerCase().contains(s) ||
                                req.getUnit().getUnitNumber().toLowerCase().contains(s);
                        if (!match) return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    /**
     * Updates request status with strict business rules:
     * - Cannot reopen a CLOSED request without a mandatory explanatory note.
     * - Records transition timestamps (scheduledDate, resolvedDate, closedDate).
     * - Creates timeline audit comment.
     * - Dispatches email notification to tenant via EmailService.
     */
    @Transactional
    public MaintenanceRequest updateStatus(Long requestId, UpdateMaintenanceStatusDto dto, User currentUser) {
        MaintenanceRequest request = getRequestById(requestId);
        RequestStatus oldStatus = request.getStatus();
        RequestStatus newStatus = dto.getStatus();

        // Business Rule: Reopening a CLOSED ticket requires mandatory note
        if (oldStatus == RequestStatus.CLOSED && newStatus != RequestStatus.CLOSED) {
            if (dto.getStatusNotes() == null || dto.getStatusNotes().trim().isBlank()) {
                throw new IllegalArgumentException("Cannot reopen a CLOSED maintenance ticket without providing an explanatory note.");
            }
        }

        // Timestamp & state adjustments
        if (newStatus == RequestStatus.RESOLVED && request.getResolvedDate() == null) {
            request.setResolvedDate(LocalDate.now());
        }
        if (newStatus == RequestStatus.CLOSED && request.getClosedDate() == null) {
            request.setClosedDate(LocalDate.now());
        }
        if (newStatus == RequestStatus.IN_PROGRESS && dto.getScheduledDate() != null) {
            request.setScheduledDate(dto.getScheduledDate());
        }

        if (dto.getAssignedTechnician() != null && !dto.getAssignedTechnician().isBlank()) {
            request.setAssignedTechnician(dto.getAssignedTechnician().trim());
        }
        if (dto.getEstimatedCost() != null) {
            request.setEstimatedCost(dto.getEstimatedCost());
        }
        if (dto.getActualCost() != null) {
            request.setActualCost(dto.getActualCost());
        }

        String note = dto.getStatusNotes() != null ? dto.getStatusNotes().trim() : "";
        request.setStatus(newStatus);
        request.setStatusNotes(note);

        request = maintenanceRequestRepository.save(request);

        // Append timeline comment
        String timelineContent = "Status changed from " + oldStatus.getDisplayName() + " to " + newStatus.getDisplayName() + ".";
        if (!note.isEmpty()) {
            timelineContent += " Note: " + note;
        }

        MaintenanceComment comment = MaintenanceComment.builder()
                .request(request)
                .author(currentUser)
                .authorName(currentUser != null ? currentUser.getFullName() : "Property Admin")
                .authorRole(currentUser != null && currentUser.getRole() == Role.ROLE_TENANT ? "TENANT" : "ADMIN")
                .content(timelineContent)
                .statusChange(true)
                .newStatus(newStatus.name())
                .build();
        maintenanceCommentRepository.save(comment);

        // Phase 7 Integration: Trigger email notification to tenant
        try {
            emailService.sendMaintenanceUpdateEmail(request, note);
        } catch (Exception ex) {
            log.warn("Failed to dispatch maintenance update email for ticket #{}: {}", request.getId(), ex.getMessage());
        }

        // Phase 9: Trigger in-app notification to tenant
        try {
            if (request.getTenant() != null && request.getTenant().getUser() != null) {
                notificationService.createNotification(
                        request.getTenant().getUser(),
                        "Maintenance Update: " + request.getTitle(),
                        "Ticket #REQ-" + request.getId() + " is now " + newStatus.getDisplayName() + (note.isEmpty() ? "" : ". " + note),
                        com.propio.model.NotificationType.MAINTENANCE_UPDATE,
                        "/maintenance/" + request.getId()
                );
            }
        } catch (Exception ex) {
            log.warn("Failed creating in-app notification for ticket #{}: {}", request.getId(), ex.getMessage());
        }

        log.info("Updated maintenance request #{} status to {} (User: {})",
                request.getId(), newStatus, currentUser != null ? currentUser.getEmail() : "system");
        return request;
    }

    @Transactional
    public MaintenanceRequest assignTechnician(Long requestId, AssignTechnicianDto dto, User currentUser) {
        MaintenanceRequest request = getRequestById(requestId);

        request.setAssignedTechnician(dto.getTechnicianName().trim());
        if (dto.getScheduledDate() != null) {
            request.setScheduledDate(dto.getScheduledDate());
        }
        // If still OPEN or PENDING, automatically move to IN_PROGRESS on assignment
        if (request.getStatus().isOpen()) {
            request.setStatus(RequestStatus.IN_PROGRESS);
        }

        request = maintenanceRequestRepository.save(request);

        String memo = "Technician assigned: " + dto.getTechnicianName().trim();
        if (dto.getScheduledDate() != null) {
            memo += " (Scheduled for " + dto.getScheduledDate() + ")";
        }
        if (dto.getNotes() != null && !dto.getNotes().isBlank()) {
            memo += ". " + dto.getNotes().trim();
        }

        MaintenanceComment comment = MaintenanceComment.builder()
                .request(request)
                .author(currentUser)
                .authorName(currentUser != null ? currentUser.getFullName() : "Property Admin")
                .authorRole("ADMIN")
                .content(memo)
                .statusChange(true)
                .newStatus(request.getStatus().name())
                .build();
        maintenanceCommentRepository.save(comment);

        // Phase 7 Integration: Notify tenant about technician scheduling
        try {
            emailService.sendMaintenanceUpdateEmail(request, memo);
        } catch (Exception ex) {
            log.warn("Failed to dispatch technician assignment email for ticket #{}: {}", request.getId(), ex.getMessage());
        }

        // Phase 9: Trigger in-app notification to tenant
        try {
            if (request.getTenant() != null && request.getTenant().getUser() != null) {
                notificationService.createNotification(
                        request.getTenant().getUser(),
                        "Technician Assigned: " + request.getTitle(),
                        "Technician " + dto.getTechnicianName() + " assigned to Request #REQ-" + request.getId(),
                        com.propio.model.NotificationType.MAINTENANCE_UPDATE,
                        "/maintenance/" + request.getId()
                );
            }
        } catch (Exception ex) {
            log.warn("Failed creating in-app notification for technician assignment on #{}: {}", request.getId(), ex.getMessage());
        }

        log.info("Assigned technician '{}' to request #{}", dto.getTechnicianName(), request.getId());
        return request;
    }

    // =========================================================================
    // Shared Comment Thread
    // =========================================================================

    @Transactional
    public MaintenanceComment addComment(Long requestId, String content, User currentUser) {
        if (content == null || content.trim().isBlank()) {
            throw new IllegalArgumentException("Comment text cannot be empty.");
        }

        MaintenanceRequest request = getRequestById(requestId);

        // Security check: If tenant, can only comment on own request
        boolean isTenant = currentUser.getRole() == Role.ROLE_TENANT;
        if (isTenant) {
            if (request.getTenant().getUser() == null ||
                !request.getTenant().getUser().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Forbidden: You cannot post comments on another resident's maintenance request.");
            }
        }

        MaintenanceComment comment = MaintenanceComment.builder()
                .request(request)
                .author(currentUser)
                .authorName(currentUser.getFullName())
                .authorRole(isTenant ? "TENANT" : "ADMIN")
                .content(content.trim())
                .statusChange(false)
                .build();

        comment = maintenanceCommentRepository.save(comment);

        // Notify other party of new message in thread
        try {
            if (isTenant) {
                notificationService.notifyAllAdmins(
                        "New Message on #REQ-" + requestId,
                        currentUser.getFullName() + ": " + (content.length() > 60 ? content.substring(0, 60) + "..." : content),
                        com.propio.model.NotificationType.MAINTENANCE_UPDATE,
                        "/maintenance/" + requestId
                );
            } else if (request.getTenant() != null && request.getTenant().getUser() != null) {
                notificationService.createNotification(
                        request.getTenant().getUser(),
                        "Management Reply on #REQ-" + requestId,
                        currentUser.getFullName() + ": " + (content.length() > 60 ? content.substring(0, 60) + "..." : content),
                        com.propio.model.NotificationType.MAINTENANCE_UPDATE,
                        "/maintenance/" + requestId
                );
            }
        } catch (Exception ex) {
            log.warn("Failed creating comment notification for ticket #{}: {}", requestId, ex.getMessage());
        }

        log.info("User '{}' added comment to maintenance request #{}", currentUser.getEmail(), requestId);
        return comment;
    }

    @Transactional(readOnly = true)
    public List<MaintenanceComment> getRequestComments(Long requestId) {
        return maintenanceCommentRepository.findByRequestIdOrderByCreatedAtAsc(requestId);
    }

    // =========================================================================
    // Metrics & Counts
    // =========================================================================

    @Transactional(readOnly = true)
    public long getOpenCount() {
        return maintenanceRequestRepository.countByStatusIn(List.of(RequestStatus.OPEN, RequestStatus.PENDING));
    }

    @Transactional(readOnly = true)
    public long getInProgressCount() {
        return maintenanceRequestRepository.countByStatus(RequestStatus.IN_PROGRESS);
    }

    @Transactional(readOnly = true)
    public long getResolvedCount() {
        return maintenanceRequestRepository.countByStatus(RequestStatus.RESOLVED);
    }

    @Transactional(readOnly = true)
    public long getClosedCount() {
        return maintenanceRequestRepository.countByStatusIn(List.of(RequestStatus.CLOSED, RequestStatus.CANCELLED));
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalEstimatedCost() {
        return maintenanceRequestRepository.sumTotalEstimatedCost();
    }
}
