package com.propio.service;

import com.propio.dto.*;
import com.propio.exception.ResourceNotFoundException;
import com.propio.model.*;
import com.propio.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MaintenanceService Unit Tests")
class MaintenanceServiceTest {

    private MaintenanceService maintenanceService;
    private StubMaintenanceRequestRepository requestRepo;
    private StubMaintenanceCommentRepository commentRepo;
    private StubTenantRepository tenantRepo;
    private StubLeaseRepository leaseRepo;
    private StubUnitRepository unitRepo;

    private Tenant tenant1;
    private Tenant tenant2;
    private User adminUser;
    private User tenant1User;
    private User tenant2User;
    private Property property;
    private Unit unit;
    private MaintenanceRequest request1;

    @BeforeEach
    void setUp() {
        requestRepo = new StubMaintenanceRequestRepository();
        commentRepo = new StubMaintenanceCommentRepository();
        tenantRepo = new StubTenantRepository();
        leaseRepo = new StubLeaseRepository();
        unitRepo = new StubUnitRepository();

        FileStorageService fileStorage = new FileStorageService("uploads");
        EmailService emailService = new EmailService(null, null, null, null);
        NotificationService notificationService = new NotificationService(null, null);

        maintenanceService = new MaintenanceService(
                requestRepo,
                commentRepo,
                tenantRepo,
                leaseRepo,
                unitRepo,
                fileStorage,
                emailService,
                notificationService
        );

        adminUser = User.builder()
                .id(1L)
                .fullName("Admin User")
                .email("admin@propio.com")
                .role(Role.ROLE_ADMIN)
                .build();

        tenant1User = User.builder()
                .id(10L)
                .fullName("Leslie Alexander")
                .email("leslie@propio.com")
                .role(Role.ROLE_TENANT)
                .build();

        tenant2User = User.builder()
                .id(20L)
                .fullName("Devon Lane")
                .email("devon@propio.com")
                .role(Role.ROLE_TENANT)
                .build();

        tenant1 = Tenant.builder()
                .id(100L)
                .firstName("Leslie")
                .lastName("Alexander")
                .email("leslie@propio.com")
                .user(tenant1User)
                .build();

        tenant2 = Tenant.builder()
                .id(200L)
                .firstName("Devon")
                .lastName("Lane")
                .email("devon@propio.com")
                .user(tenant2User)
                .build();

        property = Property.builder()
                .id(50L)
                .name("Oak Ridge Apartments")
                .build();

        unit = Unit.builder()
                .id(501L)
                .unitNumber("Apt 101")
                .property(property)
                .build();

        request1 = MaintenanceRequest.builder()
                .id(1L)
                .tenant(tenant1)
                .unit(unit)
                .title("Leaking Kitchen Sink")
                .description("Slow drip under faucet")
                .category("Plumbing")
                .priority("MEDIUM")
                .status(RequestStatus.OPEN)
                .requestedDate(LocalDate.now().minusDays(2))
                .build();

        requestRepo.save(request1);
    }

    @Test
    @DisplayName("Should update status to IN_PROGRESS and append timeline comment")
    void testUpdateStatusToInProgress() {
        UpdateMaintenanceStatusDto dto = UpdateMaintenanceStatusDto.builder()
                .status(RequestStatus.IN_PROGRESS)
                .statusNotes("Technician dispatched")
                .scheduledDate(LocalDate.now().plusDays(1))
                .build();

        MaintenanceRequest updated = maintenanceService.updateStatus(1L, dto, adminUser);

        assertEquals(RequestStatus.IN_PROGRESS, updated.getStatus());
        assertEquals(LocalDate.now().plusDays(1), updated.getScheduledDate());
        assertFalse(commentRepo.savedComments.isEmpty());
        assertTrue(commentRepo.savedComments.get(0).isStatusChange());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when reopening CLOSED request without a note")
    void testCannotReopenClosedWithoutNote() {
        request1.setStatus(RequestStatus.CLOSED);

        UpdateMaintenanceStatusDto dto = UpdateMaintenanceStatusDto.builder()
                .status(RequestStatus.OPEN)
                .statusNotes("") // Blank note
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                maintenanceService.updateStatus(1L, dto, adminUser));

        assertTrue(ex.getMessage().contains("Cannot reopen a CLOSED maintenance ticket without providing an explanatory note"));
    }

    @Test
    @DisplayName("Should successfully reopen CLOSED request when note is provided")
    void testReopenClosedWithNoteSucceeds() {
        request1.setStatus(RequestStatus.CLOSED);

        UpdateMaintenanceStatusDto dto = UpdateMaintenanceStatusDto.builder()
                .status(RequestStatus.OPEN)
                .statusNotes("Tenant reported the issue recurred this morning.")
                .build();

        MaintenanceRequest updated = maintenanceService.updateStatus(1L, dto, adminUser);
        assertEquals(RequestStatus.OPEN, updated.getStatus());
    }

    @Test
    @DisplayName("Should enforce tenant isolation: tenant can only view own requests")
    void testTenantIsolationOnView() {
        // Tenant 1 owns request 1 -> should succeed
        MaintenanceRequest req = maintenanceService.getRequestForTenant(1L, 100L);
        assertNotNull(req);

        // Tenant 2 does not own request 1 -> must throw AccessDeniedException
        assertThrows(AccessDeniedException.class, () ->
                maintenanceService.getRequestForTenant(1L, 200L));
    }

    @Test
    @DisplayName("Should assign technician and move OPEN ticket to IN_PROGRESS")
    void testAssignTechnician() {
        AssignTechnicianDto dto = AssignTechnicianDto.builder()
                .technicianName("Mike Peterson (Plumbing)")
                .scheduledDate(LocalDate.now().plusDays(2))
                .notes("Check shutoff valve")
                .build();

        MaintenanceRequest updated = maintenanceService.assignTechnician(1L, dto, adminUser);

        assertEquals("Mike Peterson (Plumbing)", updated.getAssignedTechnician());
        assertEquals(RequestStatus.IN_PROGRESS, updated.getStatus());
        assertEquals(LocalDate.now().plusDays(2), updated.getScheduledDate());
    }

    @Test
    @DisplayName("Should prevent tenant from posting comments on another tenant's request")
    void testTenantCannotCommentOnOtherTenantRequest() {
        // Tenant 2 tries to comment on Tenant 1's request
        assertThrows(AccessDeniedException.class, () ->
                maintenanceService.addComment(1L, "Unauthorized comment", tenant2User));
    }

    // =========================================================================
    // Lightweight In-Memory Stubs
    // =========================================================================

    static class StubMaintenanceRequestRepository implements MaintenanceRequestRepository {
        final Map<Long, MaintenanceRequest> map = new HashMap<>();

        @Override public <S extends MaintenanceRequest> S save(S entity) {
            if (entity.getId() == null) entity.setId((long) (map.size() + 1));
            map.put(entity.getId(), entity);
            return entity;
        }
        @Override public Optional<MaintenanceRequest> findById(Long id) { return Optional.ofNullable(map.get(id)); }
        @Override public Optional<MaintenanceRequest> findByIdWithDetails(Long id) { return findById(id); }
        @Override public List<MaintenanceRequest> findByTenantId(Long tenantId) { return Collections.emptyList(); }
        @Override public List<MaintenanceRequest> findByTenantIdWithDetails(Long tenantId) { return Collections.emptyList(); }
        @Override public List<MaintenanceRequest> findByStatus(RequestStatus status) { return Collections.emptyList(); }
        @Override public List<MaintenanceRequest> findAllWithDetails() { return new ArrayList<>(map.values()); }
        @Override public List<MaintenanceRequest> findOpenRequestsWithDetails() { return Collections.emptyList(); }
        @Override public long countByStatus(RequestStatus status) { return 0; }
        @Override public long countByStatusIn(Collection<RequestStatus> statuses) { return 0; }
        @Override public BigDecimal sumTotalEstimatedCost() { return BigDecimal.ZERO; }
        @Override public org.springframework.data.domain.Page<MaintenanceRequest> searchRequestsWithDetails(RequestStatus status, String priority, String category, Long propertyId, String search, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public void flush() {}
        @Override public <S extends MaintenanceRequest> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends MaintenanceRequest> List<S> saveAllAndFlush(Iterable<S> entities) { return null; }
        @Override public void deleteAllInBatch(Iterable<MaintenanceRequest> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> longs) {}
        @Override public void deleteAllInBatch() {}
        @Override public MaintenanceRequest getOne(Long aLong) { return null; }
        @Override public MaintenanceRequest getById(Long aLong) { return null; }
        @Override public MaintenanceRequest getReferenceById(Long aLong) { return null; }
        @Override public <S extends MaintenanceRequest> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends MaintenanceRequest> List<S> findAll(org.springframework.data.domain.Example<S> example) { return Collections.emptyList(); }
        @Override public <S extends MaintenanceRequest> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return Collections.emptyList(); }
        @Override public <S extends MaintenanceRequest> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends MaintenanceRequest> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends MaintenanceRequest> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends MaintenanceRequest, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends MaintenanceRequest> List<S> saveAll(Iterable<S> entities) { return Collections.emptyList(); }
        @Override public boolean existsById(Long aLong) { return false; }
        @Override public List<MaintenanceRequest> findAll() { return new ArrayList<>(map.values()); }
        @Override public List<MaintenanceRequest> findAllById(Iterable<Long> longs) { return Collections.emptyList(); }
        @Override public long count() { return map.size(); }
        @Override public void deleteById(Long aLong) {}
        @Override public void delete(MaintenanceRequest entity) {}
        @Override public void deleteAllById(Iterable<? extends Long> longs) {}
        @Override public void deleteAll(Iterable<? extends MaintenanceRequest> entities) {}
        @Override public void deleteAll() {}
        @Override public List<MaintenanceRequest> findAll(org.springframework.data.domain.Sort sort) { return Collections.emptyList(); }
        @Override public org.springframework.data.domain.Page<MaintenanceRequest> findAll(org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
    }

    static class StubMaintenanceCommentRepository implements MaintenanceCommentRepository {
        final List<MaintenanceComment> savedComments = new ArrayList<>();
        @Override public <S extends MaintenanceComment> S save(S entity) { savedComments.add(entity); return entity; }
        @Override public List<MaintenanceComment> findByRequestIdOrderByCreatedAtAsc(Long requestId) { return savedComments; }
        @Override public void flush() {}
        @Override public <S extends MaintenanceComment> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends MaintenanceComment> List<S> saveAllAndFlush(Iterable<S> entities) { return null; }
        @Override public void deleteAllInBatch(Iterable<MaintenanceComment> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> longs) {}
        @Override public void deleteAllInBatch() {}
        @Override public MaintenanceComment getOne(Long aLong) { return null; }
        @Override public MaintenanceComment getById(Long aLong) { return null; }
        @Override public MaintenanceComment getReferenceById(Long aLong) { return null; }
        @Override public <S extends MaintenanceComment> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends MaintenanceComment> List<S> findAll(org.springframework.data.domain.Example<S> example) { return Collections.emptyList(); }
        @Override public <S extends MaintenanceComment> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return Collections.emptyList(); }
        @Override public <S extends MaintenanceComment> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends MaintenanceComment> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends MaintenanceComment> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends MaintenanceComment, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends MaintenanceComment> List<S> saveAll(Iterable<S> entities) { return Collections.emptyList(); }
        @Override public Optional<MaintenanceComment> findById(Long aLong) { return Optional.empty(); }
        @Override public boolean existsById(Long aLong) { return false; }
        @Override public List<MaintenanceComment> findAll() { return savedComments; }
        @Override public List<MaintenanceComment> findAllById(Iterable<Long> longs) { return Collections.emptyList(); }
        @Override public long count() { return savedComments.size(); }
        @Override public void deleteById(Long aLong) {}
        @Override public void delete(MaintenanceComment entity) {}
        @Override public void deleteAllById(Iterable<? extends Long> longs) {}
        @Override public void deleteAll(Iterable<? extends MaintenanceComment> entities) {}
        @Override public void deleteAll() {}
        @Override public List<MaintenanceComment> findAll(org.springframework.data.domain.Sort sort) { return Collections.emptyList(); }
        @Override public org.springframework.data.domain.Page<MaintenanceComment> findAll(org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
    }

    static class StubTenantRepository implements TenantRepository {
        @Override public Optional<Tenant> findById(Long id) { return Optional.empty(); }
        @Override public Optional<Tenant> findByUserId(Long userId) { return Optional.empty(); }
        @Override public Optional<Tenant> findByIdWithUser(Long id) { return Optional.empty(); }
        @Override public List<Tenant> findAllByIdWithUser(List<Long> ids) { return Collections.emptyList(); }
        @Override public List<Tenant> findAllWithLeaseDetails() { return Collections.emptyList(); }
        @Override public Optional<Tenant> findByEmail(String email) { return Optional.empty(); }
        @Override public org.springframework.data.domain.Page<Tenant> searchTenants(String keyword, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public void flush() {}
        @Override public <S extends Tenant> S saveAndFlush(S entity) { return null; }
        @Override public <S extends Tenant> List<S> saveAllAndFlush(Iterable<S> entities) { return null; }
        @Override public void deleteAllInBatch(Iterable<Tenant> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> longs) {}
        @Override public void deleteAllInBatch() {}
        @Override public Tenant getOne(Long aLong) { return null; }
        @Override public Tenant getById(Long aLong) { return null; }
        @Override public Tenant getReferenceById(Long aLong) { return null; }
        @Override public <S extends Tenant> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends Tenant> List<S> findAll(org.springframework.data.domain.Example<S> example) { return Collections.emptyList(); }
        @Override public <S extends Tenant> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return Collections.emptyList(); }
        @Override public <S extends Tenant> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends Tenant> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends Tenant> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends Tenant, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends Tenant> S save(S entity) { return entity; }
        @Override public <S extends Tenant> List<S> saveAll(Iterable<S> entities) { return Collections.emptyList(); }
        @Override public boolean existsById(Long aLong) { return false; }
        @Override public List<Tenant> findAll() { return Collections.emptyList(); }
        @Override public List<Tenant> findAllById(Iterable<Long> longs) { return Collections.emptyList(); }
        @Override public long count() { return 0; }
        @Override public void deleteById(Long aLong) {}
        @Override public void delete(Tenant entity) {}
        @Override public void deleteAllById(Iterable<? extends Long> longs) {}
        @Override public void deleteAll(Iterable<? extends Tenant> entities) {}
        @Override public void deleteAll() {}
        @Override public List<Tenant> findAll(org.springframework.data.domain.Sort sort) { return Collections.emptyList(); }
        @Override public org.springframework.data.domain.Page<Tenant> findAll(org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
    }

    static class StubLeaseRepository implements LeaseRepository {
        @Override public List<Lease> findByActiveTrue() { return Collections.emptyList(); }
        @Override public long countByActiveTrue() { return 0; }
        @Override public List<Lease> findByTenantId(Long tenantId) { return Collections.emptyList(); }
        @Override public List<Lease> findByTenantIdAndActiveTrue(Long tenantId) { return Collections.emptyList(); }
        @Override public List<Lease> findActiveLeasesByTenantIdWithDetails(Long tenantId) { return Collections.emptyList(); }
        @Override public List<Lease> findActiveLeasesByTenantIdInWithDetails(List<Long> tenantIds) { return Collections.emptyList(); }
        @Override public List<Long> findTenantIdsWithAnyLease(List<Long> tenantIds) { return Collections.emptyList(); }
        @Override public List<Lease> findByEndDateBetweenAndActiveTrue(LocalDate start, LocalDate end) { return Collections.emptyList(); }
        @Override public List<Lease> findByEndDateAndActiveTrue(LocalDate date) { return Collections.emptyList(); }
        @Override public long countActiveLeasesByPropertyId(Long propertyId) { return 0; }
        @Override public long countActiveLeasesByUnitId(Long unitId) { return 0; }
        @Override public List<Lease> findExpiringLeasesWithDetails(LocalDate start, LocalDate end) { return Collections.emptyList(); }
        @Override public List<Lease> findUpcomingExpiringLeasesWithDetails() { return Collections.emptyList(); }
        @Override public long countOverlappingActiveLeases(Long unitId, LocalDate startDate, LocalDate endDate, Long excludeLeaseId) { return 0; }
        @Override public long countExpiringSoon(LocalDate today, LocalDate threshold) { return 0; }
        @Override public org.springframework.data.domain.Page<Lease> searchLeasesWithDetails(String keyword, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public List<Lease> findByTenantIdWithDetails(Long tenantId) { return Collections.emptyList(); }
        @Override public Optional<Lease> findByIdWithDetails(Long id) { return Optional.empty(); }
        @Override public BigDecimal sumActiveMonthlyRent() { return BigDecimal.ZERO; }
        @Override public void flush() {}
        @Override public <S extends Lease> S saveAndFlush(S entity) { return null; }
        @Override public <S extends Lease> List<S> saveAllAndFlush(Iterable<S> entities) { return null; }
        @Override public void deleteAllInBatch(Iterable<Lease> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> longs) {}
        @Override public void deleteAllInBatch() {}
        @Override public Lease getOne(Long aLong) { return null; }
        @Override public Lease getById(Long aLong) { return null; }
        @Override public Lease getReferenceById(Long aLong) { return null; }
        @Override public <S extends Lease> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends Lease> List<S> findAll(org.springframework.data.domain.Example<S> example) { return Collections.emptyList(); }
        @Override public <S extends Lease> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return Collections.emptyList(); }
        @Override public <S extends Lease> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends Lease> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends Lease> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends Lease, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends Lease> S save(S entity) { return entity; }
        @Override public <S extends Lease> List<S> saveAll(Iterable<S> entities) { return Collections.emptyList(); }
        @Override public Optional<Lease> findById(Long aLong) { return Optional.empty(); }
        @Override public boolean existsById(Long aLong) { return false; }
        @Override public List<Lease> findAll() { return Collections.emptyList(); }
        @Override public List<Lease> findAllById(Iterable<Long> longs) { return Collections.emptyList(); }
        @Override public long count() { return 0; }
        @Override public void deleteById(Long aLong) {}
        @Override public void delete(Lease entity) {}
        @Override public void deleteAllById(Iterable<? extends Long> longs) {}
        @Override public void deleteAll(Iterable<? extends Lease> entities) {}
        @Override public void deleteAll() {}
        @Override public List<Lease> findAll(org.springframework.data.domain.Sort sort) { return Collections.emptyList(); }
        @Override public org.springframework.data.domain.Page<Lease> findAll(org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
    }

    static class StubUnitRepository implements UnitRepository {
        @Override public Optional<Unit> findById(Long id) { return Optional.empty(); }
        @Override public List<Unit> findByPropertyId(Long propertyId) { return Collections.emptyList(); }
        @Override public List<Unit> findByPropertyIdOrderByUnitNumberAsc(Long propertyId) { return Collections.emptyList(); }
        @Override public List<Unit> findByStatus(UnitStatus status) { return Collections.emptyList(); }
        @Override public List<Unit> findByStatusOrderByUnitNumberAsc(UnitStatus status) { return Collections.emptyList(); }
        @Override public long countByStatus(UnitStatus status) { return 0; }
        @Override public List<Unit> findAllWithProperty() { return Collections.emptyList(); }
        @Override public List<Unit> findByStatusWithProperty(UnitStatus status) { return Collections.emptyList(); }
        @Override public List<Object[]> countGroupedByStatus() { return Collections.emptyList(); }
        @Override public void flush() {}
        @Override public <S extends Unit> S saveAndFlush(S entity) { return null; }
        @Override public <S extends Unit> List<S> saveAllAndFlush(Iterable<S> entities) { return null; }
        @Override public void deleteAllInBatch(Iterable<Unit> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> longs) {}
        @Override public void deleteAllInBatch() {}
        @Override public Unit getOne(Long aLong) { return null; }
        @Override public Unit getById(Long aLong) { return null; }
        @Override public Unit getReferenceById(Long aLong) { return null; }
        @Override public <S extends Unit> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends Unit> List<S> findAll(org.springframework.data.domain.Example<S> example) { return Collections.emptyList(); }
        @Override public <S extends Unit> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return Collections.emptyList(); }
        @Override public <S extends Unit> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends Unit> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends Unit> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends Unit, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends Unit> S save(S entity) { return entity; }
        @Override public <S extends Unit> List<S> saveAll(Iterable<S> entities) { return Collections.emptyList(); }
        @Override public boolean existsById(Long aLong) { return false; }
        @Override public List<Unit> findAll() { return Collections.emptyList(); }
        @Override public List<Unit> findAllById(Iterable<Long> longs) { return Collections.emptyList(); }
        @Override public long count() { return 0; }
        @Override public void deleteById(Long aLong) {}
        @Override public void delete(Unit entity) {}
        @Override public void deleteAllById(Iterable<? extends Long> longs) {}
        @Override public void deleteAll(Iterable<? extends Unit> entities) {}
        @Override public void deleteAll() {}
        @Override public List<Unit> findAll(org.springframework.data.domain.Sort sort) { return Collections.emptyList(); }
        @Override public org.springframework.data.domain.Page<Unit> findAll(org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
    }
}
