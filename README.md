# Propio — Enterprise Property Management SaaS Platform

[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)
[![Thymeleaf](https://img.shields.io/badge/Thymeleaf-3.1-green.svg)](https://www.thymeleaf.org/)
[![Bootstrap](https://img.shields.io/badge/Bootstrap-5.3-purple.svg)](https://getbootstrap.com/)
[![Security](https://img.shields.io/badge/Security-Spring%20Security%206-red.svg)](https://spring.io/projects/spring-security)
[![License](https://img.shields.io/badge/License-Proprietary-lightgrey.svg)]()

> **Propio** is a full-featured, multi-tenant property management platform engineered with Spring Boot 3, MySQL 8, Thymeleaf, and Bootstrap 5. Designed for property management firms, landlords, and tenants, Propio automates lease lifecycle management, invoicing, automated rent engine execution, PDF document generation, maintenance request workflows, and analytics reporting.

---

## 1. Key Features & Capabilities

### 🏢 Property & Unit Portfolio Management
- Multi-unit residential and commercial property management.
- Unit-level specifications (bedrooms, bathrooms, square footage, rent, deposit).
- Occupancy tracking, status changes (Available, Occupied, Under Maintenance), and image uploads.
- Real-time aggregated portfolio metrics (Occupancy rate, total units, overdue counts).

### 📄 Comprehensive Lease Lifecycle
- Digital lease creation with start/end terms, rent, security deposit, and automated late fee terms.
- Lease termination modal with date validation and unit vacancy state updates.
- One-click lease renewal with term updates.
- Dynamic lease agreement PDF generation with signature blocks.

### 💳 Payments & Automated Rent Engine
- Recurring scheduled billing engine (`0 0 1 1 * ?`) and manual "Run Rent Engine" trigger.
- Automatic invoice generation with idempotency checks to prevent duplicate billings.
- Automated overdue marking and late fee calculation.
- Offline payment recording (Cash, Check, Wire, Bank Transfer) with unique transaction references.
- Online credit/debit card simulated payments for tenants (zero card data stored in MySQL).
- Instant OpenPDF payment receipt generation with official PAID stamp.

### 🛠️ Maintenance Request Workflow
- Role-based maintenance workflow with strict state machine (`OPEN` → `IN_PROGRESS` → `RESOLVED` → `CLOSED`).
- Tenant issue submission with priority levels, categories, and optional photo upload.
- Admin assignment to maintenance technicians with status notes.
- Bi-directional comment threads with full timestamped audit trails.

### 📊 Real-Time Analytics & Financial Reports
- Interactive Chart.js dashboards:
  - Monthly Revenue trends.
  - Occupancy breakdown by property.
  - Overdue invoices distribution.
  - Overall rent collection efficiency.
- CSV export for Payments and Tenant ledgers.
- PDF Financial Report export.

### 🔔 Notifications & Email Service
- In-app notification center with dynamic unread badges and one-click mark-as-read.
- Async Spring Mail dispatch (`@Async`) with exponential retry mechanism.
- Responsive HTML Thymeleaf email templates:
  - Rent Due Reminders (3 days prior).
  - Overdue Rent Notices.
  - Payment Receipts (with attached PDF).
  - Lease Expiration Notices.
  - Maintenance Status Updates.
  - Tenant Welcome Onboarding.
- Safe local fallback: logs formatted emails when SMTP is disabled (`propio.mail.enabled=false`).
- Email audit log table with administrative resend actions.

### 🔒 Enterprise Security Hardening
- **Authentication**: Role-based access control (`ROLE_ADMIN`, `ROLE_TENANT`) with BCrypt hashing.
- **Brute-Force Protection**: Account lockout after 5 consecutive failed login attempts (15-minute lockout).
- **Password Policy**: Enforces minimum 8 characters with lowercase, uppercase, and numeric complexity.
- **Enterprise Headers**: Strict Content Security Policy (CSP), HTTP Strict Transport Security (HSTS), X-Frame-Options (`SAMEORIGIN`), X-Content-Type-Options (`nosniff`).
- **Cookie Security**: `HttpOnly`, `Secure` in production, `SameSite=Lax`.
- **Tenant Isolation**: Service-layer authorization guarantees tenants cannot inspect or alter cross-tenant records.
- **CSRF Protection**: Form tokens and AJAX CSRF headers enforced across all state-mutating requests.
- **Healthcheck & Actuator**: Standard `/actuator/health` endpoint for Kubernetes/cloud orchestration.

---

## 2. User Interface Showcase

| Admin Dashboard | Payments & Rent Engine |
| :---: | :---: |
| ![Admin Dashboard](docs/screenshots/dashboard.png) | ![Payments & Invoices](docs/screenshots/payments.png) |

| Maintenance Workflow | Financial Reports & Analytics |
| :---: | :---: |
| ![Maintenance Kanban](docs/screenshots/maintenance.png) | ![Reports & Charts](docs/screenshots/reports.png) |

*Full visual verification logs and high-resolution captures are recorded in the test suite and artifacts directory.*

---

## 3. Technology Stack

| Layer | Technologies |
| :--- | :--- |
| **Backend Framework** | Java 17, Spring Boot 3.2.5, Spring MVC |
| **Security & Auth** | Spring Security 6, BCrypt, Remember-Me Tokens |
| **Persistence & ORM**| Spring Data JPA, Hibernate 6, HikariCP Connection Pool |
| **Database** | MySQL 8.0 (Production & Local), H2 (Embedded Dev/Test) |
| **Template Engine** | Thymeleaf 3.1, Thymeleaf Layout Dialect, Extras Spring Security 6 |
| **Frontend & UI** | Bootstrap 5.3, Bootstrap Icons, Chart.js, Vanilla ES6 JavaScript |
| **Document Engine** | OpenPDF 1.3.40 (LibrePDF) |
| **Email & Async** | Spring Boot Starter Mail, JavaMailSender, `@Async` Thread Pools |
| **Containerization** | Docker (Multi-stage build), Alpine JRE 17 Runtime |
| **Testing** | JUnit 5 (Jupiter), AssertJ, Spring Test |

---

## 4. Getting Started & Local Development

### Prerequisites
- **JDK 17** (e.g. Eclipse Temurin 17 or OpenJDK 17)
- **MySQL 8.0+** running locally on port 3306
- **Maven 3.8+** (or included build scripts)

### Step 1: Database Setup
Create the MySQL database:
```sql
CREATE DATABASE IF NOT EXISTS propio_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### Step 2: Configure Local Credentials
Set your local database password in your environment or `application-local.properties`:
```powershell
$env:DB_PASSWORD = "your_mysql_password"
```

### Step 3: Build & Compile
```powershell
.\build.ps1
```

### Step 4: Run the Application
```powershell
.\run_app.ps1
```
The application will start on `http://localhost:8080`.

### Step 5: Default Test Credentials (Local Profile)
When running with the `local` profile (`propio.seed.enabled=true`):
- **Admin**: `admin@propio.com` / `admin123`
- **Tenant**: `tenant@propio.com` / `tenant123`

---

## 5. Environment Variables Reference

| Variable | Description | Default |
| :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile (`local`, `prod`, `dev`) | `dev` |
| `PORT` | Web server listening port | `8080` |
| `DB_URL` | JDBC connection URL for MySQL | `jdbc:mysql://localhost:3306/propio_db` |
| `DB_USERNAME` | Database username | `root` |
| `DB_PASSWORD` | Database password | *None* |
| `JPA_DDL_AUTO` | Hibernate schema creation mode | `update` (local) / `validate` (prod) |
| `PROPIO_ADMIN_EMAIL` | First administrator email to bootstrap | `admin@propio.com` |
| `PROPIO_ADMIN_PASSWORD` | First administrator password | `<CHOOSE-A-STRONG-PASSWORD>` |
| `PROPIO_ADMIN_NAME` | First administrator name | `System Administrator` |
| `PROPIO_SEED_ENABLED` | Populate mock tenants and properties | `false` |
| `PROPIO_MAIL_ENABLED` | Enable outbound SMTP mailing | `false` |
| `SMTP_HOST` | Outbound SMTP host | `localhost` |
| `SMTP_PORT` | Outbound SMTP port | `587` |
| `SMTP_USERNAME` | Outbound SMTP user | *None* |
| `SMTP_PASSWORD` | Outbound SMTP password | *None* |
| `PROPIO_REMEMBER_ME_KEY`| Secret signing key for Remember-Me | Random high-entropy token |

---

## 6. Default Roles & Permissions Matrix

| Feature / Resource | Role: ADMIN | Role: TENANT | Anonymous |
| :--- | :---: | :---: | :---: |
| Property Management (Add, Edit, Units) | ✅ Full Access | ❌ Forbidden (403) | ❌ Redirect to Login |
| Lease Creation & Termination | ✅ Full Access | ❌ Forbidden (403) | ❌ Redirect to Login |
| Run Automated Rent Engine | ✅ Full Access | ❌ Forbidden (403) | ❌ Redirect to Login |
| Offline Payment Recording | ✅ Full Access | ❌ Forbidden (403) | ❌ Redirect to Login |
| Pay Rent Online | ❌ N/A | ✅ Own Invoices Only | ❌ Redirect to Login |
| Download Receipts & Agreements | ✅ All Records | ✅ Own Documents Only | ❌ Redirect to Login |
| Maintenance Board & Assignment | ✅ Full Access | ❌ Forbidden (403) | ❌ Redirect to Login |
| Submit Maintenance Request | ✅ Allowed | ✅ Allowed (Own Unit) | ❌ Redirect to Login |
| Email Audit Log (`/emails`) | ✅ Full Access | ❌ Forbidden (403) | ❌ Redirect to Login |
| Analytics & Reports (`/reports`) | ✅ Full Access | ❌ Forbidden (403) | ❌ Redirect to Login |
| Health Check (`/actuator/health`)| ✅ Allowed | ✅ Allowed | ✅ Allowed (200 OK) |

---

## 7. Project Structure

```
propio/
├── Dockerfile                      # Multi-stage production container build
├── .dockerignore                   # Docker exclusion rules
├── DEPLOYMENT.md                   # Railway & Render deployment guide
├── pom.xml                         # Maven dependencies & build plugins
├── src/
│   ├── main/
│   │   ├── java/com/propio/
│   │   │   ├── config/             # Security, Seeder, Auth Handlers
│   │   │   ├── controller/         # Spring MVC & REST Controllers
│   │   │   ├── dto/                # Data Transfer Objects & Validators
│   │   │   ├── exception/          # Custom Exceptions & Global Handler
│   │   │   ├── model/              # JPA Entities & Enums
│   │   │   ├── repository/         # Spring Data JPA Repositories
│   │   │   └── service/            # Business Logic & Transactional Services
│   │   └── resources/
│   │       ├── application.properties        # Shared default properties
│   │       ├── application-local.properties  # Local MySQL profile
│   │       ├── application-prod.properties   # Cloud production profile
│   │       ├── static/                       # CSS, JS, Branding Icons
│   │       └── templates/                    # Thymeleaf HTML Templates
│   └── test/java/com/propio/       # JUnit 5 Unit & Integration Tests
└── docs/
    └── architecture_and_diagrams.md # Mermaid ER, Use Case, and Class Diagrams
```
