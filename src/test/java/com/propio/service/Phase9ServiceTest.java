package com.propio.service;

import com.propio.dto.*;
import com.propio.model.*;
import com.propio.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 9 Services Unit Tests (Search, Settings, Reports, Notifications)")
class Phase9ServiceTest {

    private SearchService searchService;
    private SystemSettingService systemSettingService;
    private NotificationService notificationService;
    private ReportService reportService;

    // Repositories
    private PropertyRepository propertyRepo;
    private TenantRepository tenantRepo;
    private LeaseRepository leaseRepo;
    private PaymentRepository paymentRepo;
    private MaintenanceRequestRepository maintenanceRepo;
    private SystemSettingRepository settingRepo;
    private NotificationRepository notificationRepo;
    private UnitRepository unitRepo;

    // In-Memory Data Maps
    private final Map<Long, Property> propertyData = new LinkedHashMap<>();
    private final Map<Long, Tenant> tenantData = new LinkedHashMap<>();
    private final Map<Long, Lease> leaseData = new LinkedHashMap<>();
    private final Map<Long, Payment> paymentData = new LinkedHashMap<>();
    private final Map<Long, MaintenanceRequest> maintenanceData = new LinkedHashMap<>();
    private final Map<String, SystemSetting> settingData = new LinkedHashMap<>();
    private final Map<Long, Notification> notificationData = new LinkedHashMap<>();
    private final Map<Long, Unit> unitData = new LinkedHashMap<>();

    private User adminUser;
    private User tenantUser1;
    private User tenantUser2;
    private Tenant tenant1;
    private Tenant tenant2;
    private Property propertyA;
    private Property propertyB;
    private Unit unit1;
    private Unit unit2;
    private Lease lease1;
    private Lease lease2;

    @BeforeEach
    void setUp() {
        propertyData.clear();
        tenantData.clear();
        leaseData.clear();
        paymentData.clear();
        maintenanceData.clear();
        settingData.clear();
        notificationData.clear();
        unitData.clear();

        propertyRepo = createProxy(PropertyRepository.class, Map.of(
                "findAll", args -> new ArrayList<>(propertyData.values()),
                "findById", args -> Optional.ofNullable(propertyData.get((Long) args[0]))
        ));

        tenantRepo = createProxy(TenantRepository.class, Map.of(
                "findAll", args -> new ArrayList<>(tenantData.values()),
                "findAllWithLeaseDetails", args -> new ArrayList<>(tenantData.values()),
                "findByUserId", args -> tenantData.values().stream()
                        .filter(t -> t.getUser() != null && t.getUser().getId().equals(args[0]))
                        .findFirst()
        ));

        leaseRepo = createProxy(LeaseRepository.class, Map.of(
                "findAll", args -> new ArrayList<>(leaseData.values()),
                "findByTenantId", args -> leaseData.values().stream()
                        .filter(l -> l.getTenant() != null && l.getTenant().getId().equals(args[0]))
                        .toList()
        ));

        paymentRepo = createProxy(PaymentRepository.class, Map.of(
                "findAll", args -> new ArrayList<>(paymentData.values()),
                "findPaymentsForReport", args -> new ArrayList<>(paymentData.values()),
                "findByTenantIdWithDetails", args -> paymentData.values().stream()
                        .filter(p -> p.getLease() != null && p.getLease().getTenant() != null && p.getLease().getTenant().getId().equals(args[0]))
                        .toList()
        ));

        maintenanceRepo = createProxy(MaintenanceRequestRepository.class, Map.of(
                "findAll", args -> new ArrayList<>(maintenanceData.values()),
                "findByTenantIdWithDetails", args -> maintenanceData.values().stream()
                        .filter(m -> m.getTenant() != null && m.getTenant().getId().equals(args[0]))
                        .toList()
        ));

        settingRepo = createProxy(SystemSettingRepository.class, Map.of(
                "findByKeyName", args -> Optional.ofNullable(settingData.get((String) args[0])),
                "save", args -> {
                    SystemSetting s = (SystemSetting) args[0];
                    settingData.put(s.getKeyName(), s);
                    return s;
                }
        ));

        notificationRepo = createProxy(NotificationRepository.class, Map.of(
                "save", args -> {
                    Notification n = (Notification) args[0];
                    if (n.getId() == null) n.setId((long) (notificationData.size() + 1));
                    notificationData.put(n.getId(), n);
                    return n;
                },
                "countByUserIdAndIsReadFalse", args -> notificationData.values().stream()
                        .filter(n -> n.getUser() != null && n.getUser().getId().equals(args[0]) && !n.isRead())
                        .count(),
                "findByUserIdOrderByCreatedAtDesc", args -> notificationData.values().stream()
                        .filter(n -> n.getUser() != null && n.getUser().getId().equals(args[0]))
                        .toList(),
                "markAsRead", args -> {
                    Notification n = notificationData.get((Long) args[0]);
                    if (n != null && n.getUser() != null && n.getUser().getId().equals(args[1])) {
                        n.setRead(true);
                        return 1;
                    }
                    return 0;
                },
                "markAllAsReadForUser", args -> {
                    notificationData.values().stream()
                            .filter(n -> n.getUser() != null && n.getUser().getId().equals(args[0]))
                            .forEach(n -> n.setRead(true));
                    return null;
                }
        ));

        unitRepo = createProxy(UnitRepository.class, Map.of(
                "findAll", args -> new ArrayList<>(unitData.values()),
                "findAllWithProperty", args -> new ArrayList<>(unitData.values())
        ));

        searchService = new SearchService(propertyRepo, tenantRepo, leaseRepo, paymentRepo, maintenanceRepo);
        systemSettingService = new SystemSettingService(settingRepo);
        notificationService = new NotificationService(notificationRepo, null);
        reportService = new ReportService(paymentRepo, propertyRepo, tenantRepo, unitRepo);

        // Seed Entities
        adminUser = User.builder().id(1L).fullName("Admin Manager").email("admin@propio.com").role(Role.ROLE_ADMIN).build();
        tenantUser1 = User.builder().id(2L).fullName("Sarah Connor").email("sarah@propio.com").role(Role.ROLE_TENANT).build();
        tenantUser2 = User.builder().id(3L).fullName("John Smith").email("john@propio.com").role(Role.ROLE_TENANT).build();

        tenant1 = Tenant.builder().id(101L).firstName("Sarah").lastName("Connor").email("sarah@propio.com").user(tenantUser1).build();
        tenant2 = Tenant.builder().id(102L).firstName("John").lastName("Smith").email("john@propio.com").user(tenantUser2).build();

        propertyA = Property.builder().id(201L).name("Skyline Towers").address("100 Main St").city("Metropolis").propertyType("APARTMENT").build();
        propertyB = Property.builder().id(202L).name("Ocean View Villas").address("200 Beach Blvd").city("Coast City").propertyType("CONDO").build();

        unit1 = Unit.builder().id(301L).unitNumber("101").property(propertyA).status(UnitStatus.OCCUPIED).build();
        unit2 = Unit.builder().id(302L).unitNumber("202").property(propertyB).status(UnitStatus.OCCUPIED).build();

        lease1 = Lease.builder().id(401L).tenant(tenant1).unit(unit1).monthlyRent(new BigDecimal("1500.00")).active(true).build();
        lease2 = Lease.builder().id(402L).tenant(tenant2).unit(unit2).monthlyRent(new BigDecimal("2200.00")).active(true).build();

        tenant1.getLeases().add(lease1);
        tenant2.getLeases().add(lease2);

        propertyData.put(propertyA.getId(), propertyA);
        propertyData.put(propertyB.getId(), propertyB);
        tenantData.put(tenant1.getId(), tenant1);
        tenantData.put(tenant2.getId(), tenant2);
        unitData.put(unit1.getId(), unit1);
        unitData.put(unit2.getId(), unit2);
        leaseData.put(lease1.getId(), lease1);
        leaseData.put(lease2.getId(), lease2);
    }

    @Test
    @DisplayName("Admin search finds all properties, tenants, and leases matching query")
    void testAdminSearchFindsAll() {
        List<SearchResultDto> results = searchService.search("Skyline", adminUser);
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(r -> "PROPERTY".equals(r.getCategory()) && r.getTitle().contains("Skyline")));

        List<SearchResultDto> tenantResults = searchService.search("Smith", adminUser);
        assertFalse(tenantResults.isEmpty());
        assertTrue(tenantResults.stream().anyMatch(r -> "TENANT".equals(r.getCategory()) && r.getTitle().contains("Smith")));
    }

    @Test
    @DisplayName("Tenant search enforces strict isolation: Tenant cannot see other tenants or unrelated properties")
    void testTenantSearchEnforcesIsolation() {
        // Sarah Connor searches for "Smith" (other tenant)
        List<SearchResultDto> results = searchService.search("Smith", tenantUser1);
        assertTrue(results.isEmpty(), "Tenant should NOT see records for other tenants");

        // Sarah Connor searches for "Ocean" (other property she does not lease)
        List<SearchResultDto> oceanResults = searchService.search("Ocean", tenantUser1);
        assertTrue(oceanResults.isEmpty(), "Tenant should NOT see properties they do not lease");

        // Sarah Connor searches for her own unit "101"
        List<SearchResultDto> myResults = searchService.search("101", tenantUser1);
        assertFalse(myResults.isEmpty(), "Tenant should find their own lease/unit");
        assertEquals("LEASE", myResults.get(0).getCategory());
    }

    @Test
    @DisplayName("SystemSettingService saves and retrieves admin billing parameters")
    void testSystemSettingService() {
        AdminSettingsDto dto = new AdminSettingsDto();
        dto.setLateFeePercentage(new BigDecimal("7.5"));
        dto.setInvoiceDay(5);
        dto.setReminderDays(4);

        systemSettingService.saveAdminSettings(dto);

        AdminSettingsDto retrieved = systemSettingService.getAdminSettings();
        assertEquals(new BigDecimal("7.5"), retrieved.getLateFeePercentage());
        assertEquals(5, retrieved.getInvoiceDay());
        assertEquals(4, retrieved.getReminderDays());
    }

    @Test
    @DisplayName("NotificationService creates notifications and marks them as read")
    void testNotificationServiceLifecycle() {
        notificationService.createNotification(
                tenantUser1,
                "Rent Due Reminder",
                "Your rent is due in 3 days.",
                NotificationType.RENT_OVERDUE,
                "/payments"
        );

        long unread = notificationService.getUnreadCount(tenantUser1);
        assertEquals(1, unread);

        List<NotificationDto> recent = notificationService.getRecentNotifications(tenantUser1, 5);
        assertEquals(1, recent.size());
        assertEquals("Rent Due Reminder", recent.get(0).getTitle());

        boolean marked = notificationService.markAsRead(recent.get(0).getId(), tenantUser1);
        assertTrue(marked);

        long afterRead = notificationService.getUnreadCount(tenantUser1);
        assertEquals(0, afterRead);
    }

    @Test
    @DisplayName("ReportService calculates accurate financial and occupancy metrics")
    void testReportServiceMetrics() {
        Payment paid = Payment.builder()
                .id(1L)
                .lease(lease1)
                .amount(new BigDecimal("1500.00"))
                .status(PaymentStatus.PAID)
                .dueDate(LocalDate.now())
                .paidDate(LocalDate.now())
                .build();

        Payment overdue = Payment.builder()
                .id(2L)
                .lease(lease2)
                .amount(new BigDecimal("2200.00"))
                .status(PaymentStatus.OVERDUE)
                .dueDate(LocalDate.now().minusDays(5))
                .build();

        paymentData.put(paid.getId(), paid);
        paymentData.put(overdue.getId(), overdue);

        ReportSummaryDto summary = reportService.generateReportSummary(LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1));

        assertEquals(new BigDecimal("1500.00"), summary.getTotalRevenueCollected());
        assertEquals(new BigDecimal("2200.00"), summary.getTotalOutstandingOverdue());
        assertEquals(100.0, summary.getOccupancyRate(), 0.1);
        // Collection rate = 1500 / (1500 + 2200) * 100 = 40.5%
        assertEquals(40.5, summary.getCollectionRate(), 0.1);
    }

    @SuppressWarnings("unchecked")
    private static <T> T createProxy(Class<T> repoInterface, Map<String, java.util.function.Function<Object[], Object>> handlers) {
        return (T) Proxy.newProxyInstance(
                repoInterface.getClassLoader(),
                new Class<?>[]{repoInterface},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (handlers.containsKey(name)) {
                        return handlers.get(name).apply(args);
                    }
                    if ("equals".equals(name)) return proxy == (args != null && args.length > 0 ? args[0] : null);
                    if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                    if ("toString".equals(name)) return "Stub<" + repoInterface.getSimpleName() + ">";
                    if (method.getReturnType().equals(Optional.class)) return Optional.empty();
                    if (method.getReturnType().equals(List.class)) return Collections.emptyList();
                    if (method.getReturnType().equals(long.class) || method.getReturnType().equals(Long.class)) return 0L;
                    if (method.getReturnType().equals(int.class) || method.getReturnType().equals(Integer.class)) return 0;
                    if (method.getReturnType().equals(boolean.class) || method.getReturnType().equals(Boolean.class)) return false;
                    return null;
                }
        );
    }
}
