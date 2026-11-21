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
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds realistic property management data on initial application boot.
 * Populates Admin, Tenants, 14 Properties with diverse units, Leases, Payments, and Maintenance Requests.
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

        log.info("Seeding realistic sample data for Propio SaaS (14 Properties with full portfolio)...");

        // 1. Create Users
        User adminUser = User.builder()
                .fullName("Manthan")
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

        // 3. Create 14 Properties across US & Europe
        Property p1 = createProp("Hermiston Residence", "377 NW 10th Hermiston", "Hermiston", "OR", "97838",
                "Single Unit type", "Modern standalone single-family luxury home rented as a private residence.",
                "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=600&h=350&q=80");

        Property p2 = createProp("Ledeberg 25", "Ledebergplein 25", "Ghent", "OV", "9050",
                "Multi-family Apartment", "Prime urban residential complex featuring modern finishes, balconies, and underground parking.",
                "https://images.unsplash.com/photo-1545324418-cc1a3fa10c00?auto=format&fit=crop&w=600&h=350&q=80");

        Property p3 = createProp("Skyline Tower", "800 5th Avenue", "Seattle", "WA", "98104",
                "Luxury Condominium", "High-rise downtown tower with panoramic Sound views, concierge, and fitness club.",
                "https://images.unsplash.com/photo-1568605117036-5fe5e7bab0b7?auto=format&fit=crop&w=600&h=350&q=80");

        Property p4 = createProp("The Austin Lofts", "401 Colorado Street", "Austin", "TX", "78701",
                "Modern Loft", "Industrial-chic loft apartments with 14-foot exposed concrete ceilings and floor-to-ceiling glass.",
                "https://images.unsplash.com/photo-1512917774080-9991f1c4c750?auto=format&fit=crop&w=600&h=350&q=80");

        Property p5 = createProp("Magnolia Gardens", "1280 Peachtree St NE", "Atlanta", "GA", "30309",
                "Townhouse", "Serene luxury townhomes nestled in Midtown Atlanta with private courtyard patios.",
                "https://images.unsplash.com/photo-1580587771525-78b9dba3b914?auto=format&fit=crop&w=600&h=350&q=80");

        Property p6 = createProp("Biscayne Bay Residences", "1100 Biscayne Blvd", "Miami", "FL", "33132",
                "Luxury High-Rise", "Waterfront residential high-rise with infinity pool, spa, and valet parking.",
                "https://images.unsplash.com/photo-1613490493576-7fde63acd811?auto=format&fit=crop&w=600&h=350&q=80");

        Property p7 = createProp("Beacon Hill Brownstone", "84 Mount Vernon St", "Boston", "MA", "02108",
                "Historic Brownstone", "Classic 19th-century brick brownstone with restored hardwood floors and marble fireplaces.",
                "https://images.unsplash.com/photo-1570129477492-45c003edd2be?auto=format&fit=crop&w=600&h=350&q=80");

        Property p8 = createProp("Silicon Valley Tech Plaza", "350 S 1st Street", "San Jose", "CA", "95113",
                "Commercial Office", "Class-A commercial suites optimized for high-growth tech firms and professional services.",
                "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=600&h=350&q=80");

        Property p9 = createProp("Mile High Commons", "1900 16th St Mall", "Denver", "CO", "80202",
                "Multi-family Apartment", "Contemporary LoDo living steps from Union Station with rooftop mountain vistas.",
                "https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?auto=format&fit=crop&w=600&h=350&q=80");

        Property p10 = createProp("Lincoln Park Manor", "2140 N Lincoln Park West", "Chicago", "IL", "60614",
                "Multi-family Apartment", "Vintage elegance meets modern luxury overlooking Lincoln Park and Lake Michigan.",
                "https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?auto=format&fit=crop&w=600&h=350&q=80");

        Property p11 = createProp("SoHo Arts Building", "452 West Broadway", "New York", "NY", "10012",
                "Modern Loft", "Cast-iron landmark building featuring expansive open layouts and artist studio spaces.",
                "https://images.unsplash.com/photo-1600566753376-12c8ab7fb75b?auto=format&fit=crop&w=600&h=350&q=80");

        Property p12 = createProp("Sunset Ridge Villas", "6200 E Camelback Rd", "Phoenix", "AZ", "85018",
                "Single Unit type", "Desert-modern estate surrounded by saguaro cacti and Camelback mountain scenery.",
                "https://images.unsplash.com/photo-1600585152220-90363fe7e115?auto=format&fit=crop&w=600&h=350&q=80");

        Property p13 = createProp("Cascade View Apartments", "1420 NW Lovejoy St", "Portland", "OR", "97209",
                "Multi-family Apartment", "Pearl District residence with eco-friendly design, bike storage, and community garden.",
                "https://images.unsplash.com/photo-1600607687920-4e2a09cf159d?auto=format&fit=crop&w=600&h=350&q=80");

        // Property 14 has units but NO active leases, ideal for testing property deletion safely
        Property p14 = createProp("Greenfield Park Complex", "520 South Tryon St", "Charlotte", "NC", "28202",
                "Townhouse", "Brand new urban townhome community with attached two-car garages and private roof decks.",
                "https://images.unsplash.com/photo-1600573472591-ee6b68d14c68?auto=format&fit=crop&w=600&h=350&q=80");

        // 4. Create Units across all properties
        // P1: Hermiston (1 unit, occupied by Jacob Jones)
        Unit u1 = addUnit(p1, "Main House", 3, 2, 1950.0, "2450.00", "2450.00", UnitStatus.OCCUPIED);

        // P2: Ledeberg 25 (4 units)
        Unit u2_1 = addUnit(p2, "Apt 101", 2, 1, 920.0, "1450.00", "1450.00", UnitStatus.OCCUPIED); // Leslie's lease
        Unit u2_2 = addUnit(p2, "Apt 102", 1, 1, 680.0, "1150.00", "1150.00", UnitStatus.VACANT);
        Unit u2_3 = addUnit(p2, "Apt 201", 2, 2, 1150.0, "1650.00", "1650.00", UnitStatus.BOOKED);
        Unit u2_4 = addUnit(p2, "Apt 202", 3, 2, 1400.0, "1950.00", "1950.00", UnitStatus.OCCUPIED);

        // P3: Skyline Tower (6 units)
        addUnit(p3, "Suite 8A", 1, 1, 750.0, "2100.00", "2100.00", UnitStatus.VACANT);
        addUnit(p3, "Suite 10C", 2, 2, 1100.0, "2850.00", "2850.00", UnitStatus.OCCUPIED);
        addUnit(p3, "Suite 12B", 2, 2, 1150.0, "2950.00", "2950.00", UnitStatus.BOOKED);
        addUnit(p3, "Suite 14A", 2, 2, 1280.0, "3200.00", "3200.00", UnitStatus.VACANT);
        addUnit(p3, "Suite 18B", 3, 3, 2100.0, "4800.00", "4800.00", UnitStatus.VACANT);
        addUnit(p3, "Penthouse 25A", 4, 4, 3400.0, "7500.00", "7500.00", UnitStatus.OCCUPIED);

        // P4: The Austin Lofts (5 units)
        addUnit(p4, "Loft 101", 1, 1, 850.0, "1850.00", "1850.00", UnitStatus.OCCUPIED);
        addUnit(p4, "Loft 102", 1, 1, 890.0, "1900.00", "1900.00", UnitStatus.VACANT);
        addUnit(p4, "Loft 201", 2, 2, 1350.0, "2650.00", "2650.00", UnitStatus.OCCUPIED);
        addUnit(p4, "Loft 202", 2, 2, 1400.0, "2750.00", "2750.00", UnitStatus.BOOKED);
        addUnit(p4, "Loft 301", 3, 2, 1850.0, "3600.00", "3600.00", UnitStatus.VACANT);

        // P5: Magnolia Gardens (4 units)
        addUnit(p5, "Townhome 101", 3, 3, 2200.0, "3100.00", "3100.00", UnitStatus.OCCUPIED);
        addUnit(p5, "Townhome 102", 3, 3, 2200.0, "3100.00", "3100.00", UnitStatus.VACANT);
        addUnit(p5, "Townhome 201", 4, 3, 2650.0, "3750.00", "3750.00", UnitStatus.OCCUPIED);
        addUnit(p5, "Townhome 202", 4, 3, 2650.0, "3750.00", "3750.00", UnitStatus.BOOKED);

        // P6: Biscayne Bay Residences (5 units)
        addUnit(p6, "Apt 401", 2, 2, 1250.0, "3400.00", "3400.00", UnitStatus.OCCUPIED);
        addUnit(p6, "Apt 502", 2, 2, 1300.0, "3550.00", "3550.00", UnitStatus.BOOKED);
        addUnit(p6, "Apt 603", 1, 1, 880.0, "2600.00", "2600.00", UnitStatus.VACANT);
        addUnit(p6, "Apt 704", 3, 3, 1950.0, "5200.00", "5200.00", UnitStatus.OCCUPIED);
        addUnit(p6, "Apt 805", 3, 3, 2050.0, "5500.00", "5500.00", UnitStatus.VACANT);

        // P7: Beacon Hill Brownstone (3 units)
        addUnit(p7, "Unit 1", 1, 1, 720.0, "2400.00", "2400.00", UnitStatus.OCCUPIED);
        addUnit(p7, "Unit 2", 2, 1, 1050.0, "3250.00", "3250.00", UnitStatus.VACANT);
        addUnit(p7, "Unit 3", 2, 2, 1200.0, "3800.00", "3800.00", UnitStatus.BOOKED);

        // P8: Silicon Valley Tech Plaza (3 units)
        addUnit(p8, "Suite 100", 0, 2, 3500.0, "8500.00", "8500.00", UnitStatus.OCCUPIED);
        addUnit(p8, "Suite 200", 0, 2, 4200.0, "10500.00", "10500.00", UnitStatus.VACANT);
        addUnit(p8, "Suite 300", 0, 4, 7800.0, "18000.00", "18000.00", UnitStatus.OCCUPIED);

        // P9: Mile High Commons (6 units)
        addUnit(p9, "Unit 1A", 1, 1, 650.0, "1550.00", "1550.00", UnitStatus.OCCUPIED);
        addUnit(p9, "Unit 1B", 1, 1, 650.0, "1550.00", "1550.00", UnitStatus.VACANT);
        addUnit(p9, "Unit 2A", 2, 1, 880.0, "1950.00", "1950.00", UnitStatus.OCCUPIED);
        addUnit(p9, "Unit 2B", 2, 2, 950.0, "2100.00", "2100.00", UnitStatus.BOOKED);
        addUnit(p9, "Unit 3A", 2, 2, 980.0, "2150.00", "2150.00", UnitStatus.OCCUPIED);
        addUnit(p9, "Unit 3B", 3, 2, 1250.0, "2750.00", "2750.00", UnitStatus.VACANT);

        // P10: Lincoln Park Manor (5 units)
        addUnit(p10, "Apt 201", 1, 1, 740.0, "1750.00", "1750.00", UnitStatus.OCCUPIED);
        addUnit(p10, "Apt 202", 2, 1, 980.0, "2250.00", "2250.00", UnitStatus.OCCUPIED);
        addUnit(p10, "Apt 301", 2, 2, 1120.0, "2650.00", "2650.00", UnitStatus.VACANT);
        addUnit(p10, "Apt 302", 3, 2, 1450.0, "3300.00", "3300.00", UnitStatus.BOOKED);
        addUnit(p10, "Apt 401", 3, 3, 1800.0, "4200.00", "4200.00", UnitStatus.OCCUPIED);

        // P11: SoHo Arts Building (4 units)
        addUnit(p11, "Studio 3A", 1, 1, 920.0, "3400.00", "3400.00", UnitStatus.OCCUPIED);
        addUnit(p11, "Studio 3B", 1, 1, 950.0, "3500.00", "3500.00", UnitStatus.VACANT);
        addUnit(p11, "Studio 4A", 2, 2, 1500.0, "5200.00", "5200.00", UnitStatus.OCCUPIED);
        addUnit(p11, "Studio 4B", 2, 2, 1550.0, "5400.00", "5400.00", UnitStatus.VACANT);

        // P12: Sunset Ridge Villas (3 units)
        addUnit(p12, "Villa 12", 3, 2, 2100.0, "2950.00", "2950.00", UnitStatus.OCCUPIED);
        addUnit(p12, "Villa 14", 3, 3, 2400.0, "3400.00", "3400.00", UnitStatus.VACANT);
        addUnit(p12, "Villa 16", 4, 3, 2800.0, "4100.00", "4100.00", UnitStatus.BOOKED);

        // P13: Cascade View Apartments (4 units)
        addUnit(p13, "Unit 101", 1, 1, 710.0, "1650.00", "1650.00", UnitStatus.OCCUPIED);
        addUnit(p13, "Unit 102", 1, 1, 710.0, "1650.00", "1650.00", UnitStatus.VACANT);
        addUnit(p13, "Unit 201", 2, 2, 1020.0, "2250.00", "2250.00", UnitStatus.OCCUPIED);
        addUnit(p13, "Unit 202", 2, 2, 1040.0, "2300.00", "2300.00", UnitStatus.VACANT);

        // P14: Greenfield Park Complex (3 units - all VACANT, NO LEASES -> test delete target!)
        addUnit(p14, "Townhome A", 3, 2, 1850.0, "2400.00", "2400.00", UnitStatus.VACANT);
        addUnit(p14, "Townhome B", 3, 2, 1850.0, "2400.00", "2400.00", UnitStatus.VACANT);
        addUnit(p14, "Townhome C", 4, 3, 2250.0, "2950.00", "2950.00", UnitStatus.VACANT);

        // 5. Create Leases
        // Lease 1: Leslie Alexander in Ledeberg Apt 101 (Rent: $1,450)
        Lease leaseLeslie = Lease.builder()
                .unit(u2_1)
                .tenant(tenantLeslie)
                .startDate(LocalDate.of(2023, 12, 1))
                .endDate(LocalDate.now().plusMonths(10))
                .monthlyRent(new BigDecimal("1450.00"))
                .securityDeposit(new BigDecimal("1450.00"))
                .active(true)
                .build();
        leaseRepository.save(leaseLeslie);

        // Lease 2: Jacob Jones in Hermiston Main House (Expires Today!)
        Lease leaseJacob = Lease.builder()
                .unit(u1)
                .tenant(tenantJacob)
                .startDate(LocalDate.now().minusYears(1))
                .endDate(LocalDate.now())
                .monthlyRent(new BigDecimal("2450.00"))
                .securityDeposit(new BigDecimal("2450.00"))
                .active(true)
                .build();
        leaseRepository.save(leaseJacob);

        // 6. Create Payments (Spanning the last 30 days for live chart and financials)
        Payment paymentOverdue = Payment.builder()
                .lease(leaseLeslie)
                .amount(new BigDecimal("1450.00"))
                .dueDate(LocalDate.now().minusDays(8))
                .status(PaymentStatus.OVERDUE)
                .paymentMethod("BANK_TRANSFER")
                .transactionReference("TXN-OD-90214")
                .notes("Late rent payment for current billing cycle")
                .build();
        paymentRepository.save(paymentOverdue);

        Payment paymentUpcoming = Payment.builder()
                .lease(leaseJacob)
                .amount(new BigDecimal("2450.00"))
                .dueDate(LocalDate.now().plusDays(4))
                .status(PaymentStatus.PENDING)
                .paymentMethod("CREDIT_CARD")
                .transactionReference("TXN-UP-88219")
                .notes("Upcoming monthly rent")
                .build();
        paymentRepository.save(paymentUpcoming);

        // Paid payments spanning the last 30 days for live Rental Payments chart
        Payment paymentPaid1 = Payment.builder()
                .lease(leaseLeslie)
                .amount(new BigDecimal("1450.00"))
                .dueDate(LocalDate.now().minusDays(26))
                .paidDate(LocalDate.now().minusDays(25))
                .status(PaymentStatus.PAID)
                .paymentMethod("BANK_TRANSFER")
                .transactionReference("TXN-PD-10291")
                .build();
        paymentRepository.save(paymentPaid1);

        Payment paymentPaid2 = Payment.builder()
                .lease(leaseJacob)
                .amount(new BigDecimal("2450.00"))
                .dueDate(LocalDate.now().minusDays(20))
                .paidDate(LocalDate.now().minusDays(19))
                .status(PaymentStatus.PAID)
                .paymentMethod("CREDIT_CARD")
                .transactionReference("TXN-PD-10292")
                .build();
        paymentRepository.save(paymentPaid2);

        Payment paymentPaid3 = Payment.builder()
                .lease(leaseLeslie)
                .amount(new BigDecimal("1450.00"))
                .dueDate(LocalDate.now().minusDays(15))
                .paidDate(LocalDate.now().minusDays(14))
                .status(PaymentStatus.PAID)
                .paymentMethod("BANK_TRANSFER")
                .transactionReference("TXN-PD-10293")
                .build();
        paymentRepository.save(paymentPaid3);

        Payment paymentPaid4 = Payment.builder()
                .lease(leaseJacob)
                .amount(new BigDecimal("2450.00"))
                .dueDate(LocalDate.now().minusDays(9))
                .paidDate(LocalDate.now().minusDays(8))
                .status(PaymentStatus.PAID)
                .paymentMethod("CREDIT_CARD")
                .transactionReference("TXN-PD-10294")
                .build();
        paymentRepository.save(paymentPaid4);

        Payment paymentPaid5 = Payment.builder()
                .lease(leaseLeslie)
                .amount(new BigDecimal("1450.00"))
                .dueDate(LocalDate.now().minusDays(3))
                .paidDate(LocalDate.now().minusDays(2))
                .status(PaymentStatus.PAID)
                .paymentMethod("BANK_TRANSFER")
                .transactionReference("TXN-PD-10295")
                .build();
        paymentRepository.save(paymentPaid5);

        // 7. Create Maintenance Requests with realistic estimated costs
        MaintenanceRequest req1 = MaintenanceRequest.builder()
                .unit(u1)
                .tenant(tenantJacob)
                .title("HVAC filter replacement & air check")
                .description("Routine seasonal heating & AC system air filter replacement and duct inspection.")
                .priority("MEDIUM")
                .status(RequestStatus.PENDING)
                .estimatedCost(new BigDecimal("180.00"))
                .requestedDate(LocalDate.now())
                .scheduledDate(LocalDate.now().plusDays(2))
                .build();
        maintenanceRequestRepository.save(req1);

        MaintenanceRequest req2 = MaintenanceRequest.builder()
                .unit(u2_1)
                .tenant(tenantLeslie)
                .title("Water heater thermostat check")
                .description("Tenant reported minor temperature fluctuation in master bath shower.")
                .priority("LOW")
                .status(RequestStatus.IN_PROGRESS)
                .estimatedCost(new BigDecimal("220.00"))
                .requestedDate(LocalDate.now().minusDays(3))
                .scheduledDate(LocalDate.now().plusDays(1))
                .build();
        maintenanceRequestRepository.save(req2);

        MaintenanceRequest req3 = MaintenanceRequest.builder()
                .unit(u1)
                .tenant(tenantJacob)
                .title("Bathroom fixture seal replacement")
                .description("Re-caulking tub surround and faucet hardware inspection.")
                .priority("LOW")
                .status(RequestStatus.RESOLVED)
                .estimatedCost(new BigDecimal("140.00"))
                .requestedDate(LocalDate.now().minusDays(18))
                .scheduledDate(LocalDate.now().minusDays(17))
                .build();
        maintenanceRequestRepository.save(req3);

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

        log.info("Propio database seeding completed successfully! Seeded 14 properties and {} units.",
                unitRepository.count());
    }

    private Property createProp(String name, String address, String city, String state, String zip,
                                String type, String desc, String imageUrl) {
        Property property = Property.builder()
                .name(name)
                .address(address)
                .city(city)
                .state(state)
                .zipCode(zip)
                .propertyType(type)
                .description(desc)
                .imageUrl(imageUrl)
                .build();
        return propertyRepository.save(property);
    }

    private Unit addUnit(Property property, String unitNumber, int beds, int baths, double sqft,
                         String rent, String deposit, UnitStatus status) {
        Unit unit = Unit.builder()
                .unitNumber(unitNumber)
                .bedrooms(beds)
                .bathrooms(baths)
                .squareFeet(sqft)
                .rentAmount(new BigDecimal(rent))
                .depositAmount(deposit != null ? new BigDecimal(deposit) : new BigDecimal(rent))
                .status(status)
                .property(property)
                .build();
        return unitRepository.save(unit);
    }
}
