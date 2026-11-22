# Propio System Architecture, Diagrams & Java Feature Catalog

This document contains the official architectural design documentation for **Propio**, including Mermaid Entity-Relationship (ER) diagrams, Use Case diagrams, Class architecture diagrams, and a comprehensive mapping of Java enterprise features to class implementations.

---

## 1. Entity-Relationship (ER) Diagram

```mermaid
erDiagram
    USER ||--o| TENANT : "has profile"
    USER ||--o{ NOTIFICATION : "receives"
    PROPERTY ||--|{ UNIT : "contains"
    UNIT ||--o{ LEASE : "leased in"
    TENANT ||--o{ LEASE : "signs"
    LEASE ||--o{ PAYMENT : "generates invoices for"
    LEASE ||--o{ DOCUMENT : "attached to"
    PROPERTY ||--o{ MAINTENANCE_REQUEST : "located at"
    UNIT ||--o{ MAINTENANCE_REQUEST : "affects"
    TENANT ||--o{ MAINTENANCE_REQUEST : "reported by"
    MAINTENANCE_REQUEST ||--o{ MAINTENANCE_COMMENT : "contains comments"
    USER ||--o{ MAINTENANCE_COMMENT : "authored by"
    PAYMENT ||--o| DOCUMENT : "has receipt"

    USER {
        bigint id PK
        varchar email UK
        varchar password
        varchar full_name
        varchar role
        varchar phone
        varchar avatar_url
        varchar theme_preference
        boolean notify_rent_due
        boolean notify_payment_received
        boolean notify_maintenance
        boolean notify_lease_expiry
        boolean enabled
        datetime created_at
        datetime updated_at
    }

    TENANT {
        bigint id PK
        bigint user_id FK
        varchar first_name
        varchar last_name
        varchar email UK
        varchar phone
        varchar emergency_contact
        varchar avatar_url
        datetime created_at
    }

    PROPERTY {
        bigint id PK
        varchar name
        varchar address
        varchar city
        varchar state
        varchar zip_code
        varchar property_type
        text description
        varchar image_url
        datetime created_at
    }

    UNIT {
        bigint id PK
        bigint property_id FK
        varchar unit_number
        int bedrooms
        int bathrooms
        double square_feet
        decimal rent_amount
        decimal deposit_amount
        varchar status
        datetime created_at
    }

    LEASE {
        bigint id PK
        bigint unit_id FK
        bigint tenant_id FK
        date start_date
        date end_date
        decimal rent_amount
        decimal deposit_amount
        varchar status
        text notes
        datetime created_at
    }

    PAYMENT {
        bigint id PK
        bigint lease_id FK
        varchar transaction_reference UK
        decimal amount
        date due_date
        datetime paid_at
        varchar payment_method
        varchar status
        varchar billing_period
        datetime created_at
    }

    MAINTENANCE_REQUEST {
        bigint id PK
        bigint property_id FK
        bigint unit_id FK
        bigint tenant_id FK
        varchar title
        text description
        varchar category
        varchar priority
        varchar status
        varchar assigned_technician
        varchar photo_url
        datetime created_at
        datetime updated_at
    }

    MAINTENANCE_COMMENT {
        bigint id PK
        bigint request_id FK
        bigint user_id FK
        text comment
        datetime created_at
    }

    DOCUMENT {
        bigint id PK
        bigint lease_id FK
        bigint payment_id FK
        varchar title
        varchar document_type
        varchar file_path
        bigint file_size
        datetime created_at
    }

    NOTIFICATION {
        bigint id PK
        bigint user_id FK
        varchar title
        text message
        varchar type
        varchar link_url
        boolean is_read
        datetime created_at
    }

    EMAIL_LOG {
        bigint id PK
        varchar recipient
        varchar subject
        varchar email_type
        varchar status
        int retry_count
        text error_message
        datetime created_at
        datetime sent_at
    }

    SUPPORT_MESSAGE {
        bigint id PK
        varchar name
        varchar email
        varchar subject
        text message
        datetime created_at
    }

    SYSTEM_SETTING {
        bigint id PK
        varchar setting_key UK
        varchar setting_value
        varchar description
        datetime updated_at
    }
```

---

## 2. Use Case Diagrams

### Admin & Property Manager Use Cases

```mermaid
flowchart LR
    Admin([Property Manager / Admin])
    
    subgraph Portfolio_Management [Portfolio Management]
        UC1[Manage Properties & Units]
        UC2[Assign & Update Unit Statuses]
    end

    subgraph Leasing_Operations [Leasing Operations]
        UC3[Draft & Execute Leases]
        UC4[Terminate / Renew Leases]
        UC5[Download Lease Agreement PDF]
    end

    subgraph Financial_Engine [Financial Engine]
        UC6[Trigger Automated Rent Engine]
        UC7[Record Offline Payments]
        UC8[Generate Payment Receipt PDFs]
        UC9[Export CSV Ledgers & Financial Reports]
    end

    subgraph Operations_Maintenance [Maintenance & Operations]
        UC10[Triage Maintenance Requests]
        UC11[Assign Technicians & Add Notes]
        UC12[Audit Async Email Dispatches]
        UC13[Configure System & Late Fee Parameters]
    end

    Admin --> UC1
    Admin --> UC2
    Admin --> UC3
    Admin --> UC4
    Admin --> UC5
    Admin --> UC6
    Admin --> UC7
    Admin --> UC8
    Admin --> UC9
    Admin --> UC10
    Admin --> UC11
    Admin --> UC12
    Admin --> UC13
```

### Tenant Portal Use Cases

```mermaid
flowchart LR
    Tenant([Registered Tenant])

    subgraph Tenant_Portal [Tenant Self-Service Portal]
        TC1[View Active Lease Details]
        TC2[Download Signed Lease Agreements]
        TC3[View Monthly Invoices]
        TC4[Pay Rent Online via Secure Gateway]
        TC5[Download Official Payment Receipts]
        TC6[Submit Maintenance Tickets with Photos]
        TC7[Track Maintenance Status Timeline]
        TC8[Post Comments on Tickets]
        TC9[Receive In-App & Email Notifications]
        TC10[Update Profile & Change Password]
    end

    Tenant --> TC1
    Tenant --> TC2
    Tenant --> TC3
    Tenant --> TC4
    Tenant --> TC5
    Tenant --> TC6
    Tenant --> TC7
    Tenant --> TC8
    Tenant --> TC9
    Tenant --> TC10
```

---

## 3. High-Level Class & Component Architecture

```mermaid
classDiagram
    class SecurityConfig {
        +SecurityFilterChain filterChain(HttpSecurity)
        +PasswordEncoder passwordEncoder()
    }

    class CustomUserDetailsService {
        +loadUserByUsername(String email) UserDetails
    }

    class LoginAttemptService {
        +loginSucceeded(String key)
        +loginFailed(String key)
        +isBlocked(String key) boolean
        +getRemainingLockoutMinutes(String key) int
    }

    class PaymentService {
        +runRentEngine() BillingSummaryDto
        +recordPayment(PaymentRecordDto) Payment
        +processOnlinePayment(Long id, CardDto) Payment
    }

    class PdfGenerationService {
        +generatePaymentReceiptPdf(Payment) byte[]
        +generateLeaseAgreementPdf(Lease) byte[]
        +generateTenantStatementPdf(Tenant, int year) byte[]
        +generateFinancialReportPdf(ReportDto) byte[]
    }

    class EmailService {
        +sendRentDueNotification(Lease, Payment)
        +sendPaymentReceivedWithReceipt(Payment, byte[])
        +sendMaintenanceStatusUpdate(MaintenanceRequest)
        +sendWelcomeEmail(User, String tempPassword)
        +resendEmail(Long emailLogId)
    }

    class MaintenanceService {
        +createRequest(Tenant, MaintenanceDto) MaintenanceRequest
        +updateStatus(Long id, RequestStatus, String note) MaintenanceRequest
        +assignTechnician(Long id, String techName) MaintenanceRequest
        +addComment(Long id, User author, String text) MaintenanceComment
    }

    class ReportService {
        +getDashboardMetrics() DashboardMetricsDto
        +getRevenueChartData(LocalDate from, LocalDate to) ChartDataDto
        +exportPaymentsCsv(LocalDate from, LocalDate to) String
    }

    SecurityConfig --> CustomUserDetailsService : configures
    SecurityConfig --> LoginAttemptService : invokes on failure/success
    PaymentService --> PdfGenerationService : generates receipts
    PaymentService --> EmailService : dispatches receipt emails
    MaintenanceService --> EmailService : dispatches ticket updates
```

---

## 4. Java Features & Class Mapping Catalog

Below is the exhaustive catalog of Java 17 and Spring Boot enterprise features implemented across Propio, with exact package paths and class names.

### A. Core Architecture & Dependency Injection
| Capability / Design Pattern | Spring / Java Feature | Implementation Classes |
| :--- | :--- | :--- |
| **Inversion of Control (IoC)** | `@Service`, `@Component`, `@Configuration`, Constructor Injection | All classes in `com.propio.service`, `com.propio.config` |
| **Boilerplate Reduction** | Lombok (`@Getter`, `@Setter`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`) | All Models in `com.propio.model`, Services, DTOs |
| **Declarative Transactions** | Spring `@Transactional` (with read-only optimizations) | `PaymentService`, `LeaseService`, `MaintenanceService`, `UserService` |
| **REST & MVC Controllers** | `@Controller`, `@RestController`, `@GetMapping`, `@PostMapping` | All classes in `com.propio.controller` |

### B. Security & Authentication
| Security Feature | Implementation Mechanism | Class Names |
| :--- | :--- | :--- |
| **Role-Based Authorization** | `hasRole('ADMIN')`, `hasAnyRole('ADMIN', 'TENANT')`, `@EnableMethodSecurity` | `com.propio.config.SecurityConfig` |
| **Password Hashing** | Spring `BCryptPasswordEncoder` (cost factor 10) | `SecurityConfig`, `UserService`, `DataSeeder` |
| **Password Complexity Policy** | Jakarta `@Pattern` (Regex for min 8 chars, uppercase, lowercase, digit) | `com.propio.dto.RegisterDto`, `com.propio.dto.ChangePasswordDto`, `UserService` |
| **Brute-Force Rate Limiting** | Concurrent in-memory tracking, 5-attempt limit, 15-minute temporary lockout | `com.propio.service.LoginAttemptService` |
| **Custom Auth Handlers** | `SimpleUrlAuthenticationFailureHandler`, `SavedRequestAwareAuthenticationSuccessHandler` | `com.propio.config.CustomAuthenticationFailureHandler`, `CustomAuthenticationSuccessHandler` |
| **Tenant Isolation Engine** | Service-layer checks comparing authenticated tenant ID with resource owner | `PaymentService`, `LeaseService`, `MaintenanceService`, `DocumentController` |
| **Security Headers** | CSP directives, HSTS (31536000s), FrameOptions (SAMEORIGIN), ReferrerPolicy | `com.propio.config.SecurityConfig` |
| **CSRF Defense** | Form tokens & AJAX CSRF request token verification | Standard Spring Security Filter Chain |

### C. Financial & Billing Engine
| Feature | Implementation Mechanism | Class Names |
| :--- | :--- | :--- |
| **Exact Money Precision** | `java.math.BigDecimal` (arithmetic with `RoundingMode.HALF_UP`) | `Payment`, `Lease`, `Unit`, `PaymentService`, `ReportService` |
| **Automated Rent Engine** | Spring `@Scheduled(cron = "0 0 1 1 * ?")` with manual on-demand trigger | `com.propio.service.PaymentService`, `com.propio.controller.PaymentController` |
| **Billing Idempotency** | Checks existing invoices for given `leaseId` and `billingPeriod` to eliminate duplicates | `com.propio.repository.PaymentRepository`, `PaymentService` |
| **Late Fee Rule Application**| Calculates percentage charge after due date grace period expires | `PaymentService`, `SystemSettingService` |

### D. PDF Document Generation
| Feature | Implementation Mechanism | Class Names |
| :--- | :--- | :--- |
| **Payment Receipt PDF** | OpenPDF (`Document`, `PdfWriter`, `PdfPTable`, custom headers, PAID stamp) | `com.propio.service.PdfGenerationService`, `PdfDownloadController` |
| **Lease Agreement PDF** | OpenPDF multi-page legal agreement with signature blocks and terms | `com.propio.service.PdfGenerationService`, `PdfDownloadController` |
| **Yearly Statement PDF** | OpenPDF tabular ledger showing 12-month tenant transaction history | `com.propio.service.PdfGenerationService`, `PdfDownloadController` |
| **Financial Summary PDF** | OpenPDF executive overview of portfolio collections, revenues, and arrears | `com.propio.service.PdfGenerationService`, `ReportController` |

### E. Asynchronous Processing & Email Dispatch
| Feature | Implementation Mechanism | Class Names |
| :--- | :--- | :--- |
| **Non-blocking Execution** | Spring `@Async` thread pool task executors | `com.propio.service.EmailService` |
| **HTML Email Templating** | Thymeleaf `SpringTemplateEngine` evaluating HTML layouts with model attributes | `com.propio.service.EmailService`, `templates/mail/*` |
| **Attachment Handling** | Spring `MimeMessageHelper` with `ByteArrayResource` attachment streaming | `com.propio.service.EmailService` |
| **Safe Dev Fallback** | `propio.mail.enabled` check; logs styled payload when SMTP is offline | `com.propio.service.EmailService` |
| **Email Audit & Resend** | JPA persistence in `EmailLog` table; admin retry handler | `com.propio.repository.EmailLogRepository`, `com.propio.controller.EmailController` |

### F. Health Checks & Observability
| Feature | Implementation Mechanism | Class Names |
| :--- | :--- | :--- |
| **Actuator Liveness/Readiness**| Standard JSON health response querying `DataSource.getConnection().isValid(2)` | `com.propio.controller.ActuatorHealthController` |
| **Zero Stacktrace Leakage**| Spring Boot `server.error.include-stacktrace=never`, custom 404/403/500 templates | `com.propio.controller.CustomErrorController`, `application-prod.properties` |
| **Structured Logging** | SLF4J with Logback; level segregation (`INFO` for app, `WARN` for framework) | `logback-spring.xml` / `application-prod.properties` |
