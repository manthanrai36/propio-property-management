package com.propio.config;

import com.propio.model.*;
import com.propio.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Seeds realistic property management data on initial application boot.
 * Populates Admin, Tenants, Properties, Units, Leases, Payments, and Maintenance Requests.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final UnitRepository unitRepository;
    private final TenantRepository tenantRepository;
    private final LeaseRepository leaseRepository;
    private final PaymentRepository paymentRepository;
    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final DocumentRepository documentRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Database already seeded with {} users. Skipping initial seed.", userRepository.count());
            return;
        }

        log.info("Seeding realistic sample data for Propio SaaS...");

        // 1. Create Users
        User adminUser = User.builder()
                .fullName("Imran Hossain")
                .email("admin@propio.com")
                .password(passwordEncoder.encode("admin123"))
                .role(Role.ROLE_ADMIN)
                .phone("+1 (555) 019-2834")
                .avatarUrl("https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=120&h=120&q=80")
                .enabled(true)
                .build();
        userRepository.save(adminUser);

        User tenantUser1 = User.builder()
                .fullName("Leslie Alexander")
                .email("tenant@propio.com")
                .password(passwordEncoder.encode("tenant123"))
                .role(Role.ROLE_TENANT)
                .phone("+1 (555) 384-9182")
                .avatarUrl("https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=120&h=120&q=80")
                .enabled(true)
                .build();
        userRepository.save(tenantUser1);

        User tenantUser2 = User.builder()
                .fullName("Jacob Jones")
                .email("jacob@propio.com")
                .password(passwordEncoder.encode("tenant123"))
                .role(Role.ROLE_TENANT)
                .phone("+1 (555) 728-4910")
                .avatarUrl("https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=120&h=120&q=80")
                .enabled(true)
                .build();
        userRepository.save(tenantUser2);

        // 2. Create Tenant Profiles
        Tenant tenantLeslie = Tenant.builder()
                .firstName("Leslie")
                .lastName("Alexander")
                .email("tenant@propio.com")
                .phone("+1 (555) 384-9182")
                .occupation("Senior UI/UX Designer")
                .emergencyContact("Sarah Alexander (+1 555-992-1823)")
                .avatarUrl("https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=120&h=120&q=80")
                .user(tenantUser1)
                .build();
        tenantRepository.save(tenantLeslie);

        Tenant tenantJacob = Tenant.builder()
                .firstName("Jacob")
                .lastName("Jones")
                .email("jacob@propio.com")
                .phone("+1 (555) 728-4910")
                .occupation("Software Architect")
                .emergencyContact("Michael Jones (+1 555-832-1920)")
                .avatarUrl("https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=120&h=120&q=80")
                .user(tenantUser2)
                .build();
        tenantRepository.save(tenantJacob);

        Tenant tenantKathryn = Tenant.builder()
                .firstName("Kathryn")
                .lastName("Murphy")
                .email("kathryn.murphy@example.com")
                .phone("+1 (555) 492-8172")
                .occupation("Financial Analyst")
                .emergencyContact("David Murphy (+1 555-481-9283)")
                .avatarUrl("https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=120&h=120&q=80")
                .build();
        tenantRepository.save(tenantKathryn);

        // 3. Create Properties
        Property propHermiston = Property.builder()
                .name("Hermiston Residence")
                .address("377 NW 10th Hermiston")
                .city("Hermiston")
                .state("OR")
                .zipCode("97838")
                .propertyType("Single Unit type")
                .description("Modern standalone single-family luxury home rented as a private residence.")
                .imageUrl("https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=500&h=260&q=80")
                .build();
        propertyRepository.save(propHermiston);

        Property propLedeberg = Property.builder()
                .name("Ledeberg 25")
                .address("Ledebergplein 25")
                .city("Ghent")
                .state("OV")
                .zipCode("9050")
                .propertyType("Multi-family Apartment")
                .description("Prime urban residential complex featuring modern finishes, balconies, and underground parking.")
                .imageUrl("https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=500&h=260&q=80")
                .build();
        propertyRepository.save(propLedeberg);

        Property propSkyline = Property.builder()
                .name("Skyline Tower")
                .address("800 5th Avenue")
                .city("Seattle")
                .state("WA")
                .zipCode("98104")
                .propertyType("Luxury Condos")
                .description("High-rise downtown tower with rooftop terrace, concierge, and fitness center.")
                .imageUrl("https://images.unsplash.com/photo-1568605117036-5fe5e7bab0b7?auto=format&fit=crop&w=500&h=260&q=80")
                .build();
        propertyRepository.save(propSkyline);

        // 4. Create Units (with distribution matching the 289 units donut chart concept)
        Unit unitSingle = Unit.builder()
                .unitNumber("Main House")
                .bedrooms(3)
                .bathrooms(2)
                .squareFeet(1950.0)
                .rentAmount(new BigDecimal("2450.00"))
                .depositAmount(new BigDecimal("2450.00"))
                .status(UnitStatus.OCCUPIED)
                .property(propHermiston)
                .build();
        unitRepository.save(unitSingle);

        Unit unit101 = Unit.builder()
                .unitNumber("Apt 101")
                .bedrooms(2)
                .bathrooms(1)
                .squareFeet(920.0)
                .rentAmount(new BigDecimal("1450.00"))
                .depositAmount(new BigDecimal("1450.00"))
                .status(UnitStatus.OCCUPIED)
                .property(propLedeberg)
                .build();
        unitRepository.save(unit101);

        Unit unit102 = Unit.builder()
                .unitNumber("Apt 102")
                .bedrooms(1)
                .bathrooms(1)
                .squareFeet(680.0)
                .rentAmount(new BigDecimal("1150.00"))
                .depositAmount(new BigDecimal("1150.00"))
                .status(UnitStatus.VACANT)
                .property(propLedeberg)
                .build();
        unitRepository.save(unit102);

        Unit unit201 = Unit.builder()
                .unitNumber("Apt 201")
                .bedrooms(2)
                .bathrooms(2)
                .squareFeet(1150.0)
                .rentAmount(new BigDecimal("1650.00"))
                .depositAmount(new BigDecimal("1650.00"))
                .status(UnitStatus.BOOKED)
                .property(propLedeberg)
                .build();
        unitRepository.save(unit201);

        Unit unitSky14A = Unit.builder()
                .unitNumber("Suite 14A")
                .bedrooms(2)
                .bathrooms(2)
                .squareFeet(1280.0)
                .rentAmount(new BigDecimal("3200.00"))
                .depositAmount(new BigDecimal("3200.00"))
                .status(UnitStatus.MAINTENANCE)
                .property(propSkyline)
                .build();
        unitRepository.save(unitSky14A);

        Unit unitSky18B = Unit.builder()
                .unitNumber("Suite 18B")
                .bedrooms(3)
                .bathrooms(3)
                .squareFeet(2100.0)
                .rentAmount(new BigDecimal("4800.00"))
                .depositAmount(new BigDecimal("4800.00"))
                .status(UnitStatus.VACANT)
                .property(propSkyline)
                .build();
        unitRepository.save(unitSky18B);

        // 5. Create Leases
        // Lease 1: Leslie Alexander in Apt 101 (Rent: $1,450)
        Lease leaseLeslie = Lease.builder()
                .unit(unit101)
                .tenant(tenantLeslie)
                .startDate(LocalDate.of(2023, 12, 1))
                .endDate(LocalDate.now().plusMonths(10))
                .monthlyRent(new BigDecimal("1450.00"))
                .securityDeposit(new BigDecimal("1450.00"))
                .active(true)
                .build();
        leaseRepository.save(leaseLeslie);

        // Lease 2: Jacob Jones in Main House (Expires Today! matching reference banner)
        Lease leaseJacob = Lease.builder()
                .unit(unitSingle)
                .tenant(tenantJacob)
                .startDate(LocalDate.now().minusYears(1))
                .endDate(LocalDate.now()) // Expires today!
                .monthlyRent(new BigDecimal("2450.00"))
                .securityDeposit(new BigDecimal("2450.00"))
                .active(true)
                .build();
        leaseRepository.save(leaseJacob);

        // 6. Create Payments (Matching Screenshot Figures: $1,450.00 Overdue, $2,450.00 Upcoming)
        Payment paymentOverdue = Payment.builder()
                .lease(leaseLeslie)
                .amount(new BigDecimal("1450.00"))
                .dueDate(LocalDate.now().minusDays(12))
                .status(PaymentStatus.OVERDUE)
                .paymentMethod("BANK_TRANSFER")
                .transactionReference("TXN-OD-90214")
                .notes("Late rent payment for current billing cycle")
                .build();
        paymentRepository.save(paymentOverdue);

        Payment paymentUpcoming = Payment.builder()
                .lease(leaseJacob)
                .amount(new BigDecimal("2450.00"))
                .dueDate(LocalDate.now().plusDays(5))
                .status(PaymentStatus.PENDING)
                .paymentMethod("CREDIT_CARD")
                .transactionReference("TXN-UP-88219")
                .notes("Upcoming monthly rent")
                .build();
        paymentRepository.save(paymentUpcoming);

        // Historical paid payments for revenue charts
        Payment paymentPaid1 = Payment.builder()
                .lease(leaseLeslie)
                .amount(new BigDecimal("1450.00"))
                .dueDate(LocalDate.now().minusMonths(1))
                .paidDate(LocalDate.now().minusMonths(1).plusDays(2))
                .status(PaymentStatus.PAID)
                .paymentMethod("BANK_TRANSFER")
                .transactionReference("TXN-PD-10294")
                .build();
        paymentRepository.save(paymentPaid1);

        Payment paymentPaid2 = Payment.builder()
                .lease(leaseJacob)
                .amount(new BigDecimal("2450.00"))
                .dueDate(LocalDate.now().minusMonths(1))
                .paidDate(LocalDate.now().minusMonths(1).plusDays(1))
                .status(PaymentStatus.PAID)
                .paymentMethod("CREDIT_CARD")
                .transactionReference("TXN-PD-10295")
                .build();
        paymentRepository.save(paymentPaid2);

        // 7. Create Maintenance Requests (Matching Banner Events: Check bill, Insurance)
        MaintenanceRequest req1 = MaintenanceRequest.builder()
                .unit(unitSingle)
                .tenant(tenantJacob)
                .title("Check bill / Utility audit")
                .description("Routine monthly utility inspection and energy sub-meter verification.")
                .priority("MEDIUM")
                .status(RequestStatus.PENDING)
                .estimatedCost(new BigDecimal("120.00"))
                .requestedDate(LocalDate.now())
                .scheduledDate(LocalDate.now())
                .build();
        maintenanceRequestRepository.save(req1);

        MaintenanceRequest req2 = MaintenanceRequest.builder()
                .unit(unit101)
                .tenant(tenantLeslie)
                .title("Water heater thermostat check")
                .description("Tenant reported minor temperature fluctuation in master bath shower.")
                .priority("LOW")
                .status(RequestStatus.IN_PROGRESS)
                .estimatedCost(new BigDecimal("85.00"))
                .requestedDate(LocalDate.now().minusDays(3))
                .build();
        maintenanceRequestRepository.save(req2);

        // 8. Create Documents
        Document doc1 = Document.builder()
                .fileName("Standard_Lease_Agreement_Apt101.pdf")
                .fileType("application/pdf")
                .fileUrl("/documents/lease-101.pdf")
                .category("LEASE_AGREEMENT")
                .lease(leaseLeslie)
                .tenant(tenantLeslie)
                .build();
        documentRepository.save(doc1);

        Document doc2 = Document.builder()
                .fileName("Hermiston_House_Inspection_Report.pdf")
                .fileType("application/pdf")
                .fileUrl("/documents/inspection-house-a.pdf")
                .category("INSPECTION_REPORT")
                .lease(leaseJacob)
                .tenant(tenantJacob)
                .build();
        documentRepository.save(doc2);

        log.info("Propio database seeding completed successfully with Admin and Tenant accounts!");
    }
}
