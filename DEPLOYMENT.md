# Production Deployment Guide: Railway & Render with MySQL

This guide walks you through deploying **Propio** as a containerized Spring Boot 3 enterprise application with a managed **MySQL 8** database on **Railway** or **Render**.

---

## 1. Architecture Overview

```
 [ Client Browser (HTTPS) ]
         │
         ▼
 [ Reverse Proxy / Cloud Load Balancer (SSL Termination) ]
         │ (Native Header Forwarding)
         ▼
 [ Propio Spring Boot Container (Port: ${PORT}) ]
         │ (HikariCP Connection Pool with SSL)
         ▼
 [ Managed MySQL 8 Database (propio_db) ]
```

- **Active Profile**: `prod` (`application-prod.properties`), activated strictly through the `SPRING_PROFILES_ACTIVE` environment variable.
- **Container**: Multi-stage Docker image using `maven:3.9-eclipse-temurin-17` for building and `eclipse-temurin:17-jre-jammy` minimal JRE for runtime.
- **Container Security**: Runs as an unprivileged, non-root system user (`spring:spring`, UID 1001).
- **JVM Constraints**: Tuned for 512 MB memory limit (`-XX:+UseContainerSupport -XX:MaxRAMPercentage=70.0 -XX:+UseSerialGC -Xss512k -XX:+ExitOnOutOfMemoryError`).
- **Healthcheck**: Public `/actuator/health` endpoint returning database readiness and disk space.
- **Security Baseline**: CSP, HSTS, X-Frame-Options (`SAMEORIGIN`), X-Content-Type-Options (`nosniff`), encrypted `HttpOnly; Secure; SameSite=Lax` cookies, and brute-force lockout protection (5 failed attempts).

---

## 2. Complete Environment Variables Reference

Every parameter in `application-prod.properties` is configured via environment variables. Configure these in your cloud provider's environment variables dashboard:

### Core Application & Server
| Variable | Description | Required | Default / Example Value |
| :--- | :--- | :---: | :--- |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | **Yes** | `prod` |
| `PORT` | HTTP server port | **Yes** | `8080` (automatically set by Railway/Render) |
| `PROPIO_UPLOAD_DIR` | Directory path for uploaded photos/documents | No | `/app/uploads` |

### Database & Persistence (MySQL 8)
| Variable | Description | Required | Default / Example Value |
| :--- | :--- | :---: | :--- |
| `DB_URL` | MySQL JDBC Connection URL | **Yes** | `jdbc:mysql://${MYSQLHOST}:${MYSQLPORT}/${MYSQLDATABASE}?useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC` |
| `DB_USERNAME` | Database username | **Yes** | `${MYSQLUSER}` (from managed database) |
| `DB_PASSWORD` | Database password | **Yes** | `${MYSQLPASSWORD}` (from managed database) |
| `JPA_DDL_AUTO` | Hibernate schema creation mode | **Yes** | `update` (first run to create tables) then `validate` |
| `DB_POOL_MAX` | HikariCP maximum connection pool size | No | `15` |
| `DB_POOL_MIN` | HikariCP minimum idle connections | No | `3` |

### Administrator Bootstrap & Security
| Variable | Description | Required | Default / Example Value |
| :--- | :--- | :---: | :--- |
| `PROPIO_ADMIN_EMAIL` | Initial administrator login email | **Yes** | `<SET-INITIAL-ADMIN-EMAIL>` |
| `PROPIO_ADMIN_PASSWORD` | Initial administrator password (min 8 chars, mixed case + digit) | **Yes** | `<SET-STRONG-ADMIN-PASSWORD>` |
| `PROPIO_ADMIN_NAME` | Initial administrator full name | No | `System Administrator` |
| `PROPIO_SEED_ENABLED` | Populate mock tenants/properties (Disabled in prod) | No | `false` |
| `PROPIO_REMEMBER_ME_KEY` | Secret signing key for remember-me tokens | **Yes** | `<GENERATE-RANDOM-64-HEX-STRING>` |

### Rent Engine & Billing Scheduler
| Variable | Description | Required | Default / Example Value |
| :--- | :--- | :---: | :--- |
| `PROPIO_BILLING_ENABLED` | Enable automated recurring rent engine | No | `true` |
| `PROPIO_LATE_FEE_PCT` | Late fee percentage assessed on overdue rent | No | `5.0` |

### Outbound Email (Spring Mail via SMTP)
| Variable | Description | Required | Default / Example Value |
| :--- | :--- | :---: | :--- |
| `PROPIO_MAIL_ENABLED` | Enable live SMTP email dispatch (`false` = safe log fallback) | No | `false` (or `true` with SMTP credentials) |
| `PROPIO_MAIL_FROM` | Outbound sender email address | When mail on | `<SET-SENDER-EMAIL>` |
| `PROPIO_MAIL_FROM_NAME` | Outbound sender display name | No | `Propio Property Management` |
| `SMTP_HOST` | Outbound mail server hostname | When mail on | `<SET-SMTP-HOST>` |
| `SMTP_PORT` | Outbound mail server port | When mail on | `587` |
| `SMTP_USERNAME` | SMTP authentication username | When mail on | `<SET-SMTP-USERNAME>` |
| `SMTP_PASSWORD` | SMTP authentication password or API key | When mail on | `<SET-SMTP-PASSWORD>` |
| `SMTP_AUTH` | Require SMTP authentication | No | `true` |
| `SMTP_STARTTLS` | Require STARTTLS encryption | No | `true` |

---

## 3. Step-by-Step Deployment on Railway

### Step 1: Create Railway Project and Provision MySQL
1. Log in to your [Railway Dashboard](https://railway.app).
2. Click **New Project** $\rightarrow$ **Provision MySQL**.
3. Railway starts a dedicated MySQL 8 instance. Click on the MySQL card and go to the **Variables** tab to observe Railway's auto-generated database connection variables:
   - `MYSQLHOST`
   - `MYSQLPORT`
   - `MYSQLUSER`
   - `MYSQLPASSWORD`
   - `MYSQLDATABASE`

### Step 2: Deploy the Propio Service from Git
1. In the same Railway project canvas, click **Create** $\rightarrow$ **GitHub Repo**.
2. Select your `propio` repository.
3. Railway automatically detects the root `Dockerfile` and configures Docker build mode.

### Step 3: Configure Service Healthcheck
1. Click on the newly added Propio service card $\rightarrow$ open **Settings**.
2. Scroll to **Healthcheck Path** and enter:
   ```text
   /actuator/health
   ```
3. Set **Healthcheck Timeout** to `120` seconds (allowing Spring Boot and Hibernate to initialize).

### Step 4: Configure Environment Variables
1. Navigate to the **Variables** tab of the Propio service.
2. Click **Raw Editor** and paste the following configuration (Railway automatically resolves `${{MySQL...}}` references to your MySQL service):

```properties
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USERNAME=${{MySQL.MYSQLUSER}}
DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
JPA_DDL_AUTO=update
PROPIO_SEED_ENABLED=false
PROPIO_ADMIN_EMAIL=<SET-INITIAL-ADMIN-EMAIL>
PROPIO_ADMIN_PASSWORD=<SET-STRONG-ADMIN-PASSWORD>
PROPIO_ADMIN_NAME=System Administrator
PROPIO_REMEMBER_ME_KEY=<GENERATE-RANDOM-64-HEX-STRING>
PROPIO_MAIL_ENABLED=false
```

3. Click **Save Changes**.

### Step 5: Deploy & Generate Public Domain
1. Railway automatically triggers a build using the multi-stage `Dockerfile`.
2. Under **Settings** $\rightarrow$ **Networking**, click **Generate Domain** to obtain your public HTTPS URL (e.g., `https://propio-production.up.railway.app`).
3. Once the build completes, Railway runs the container and probes `/actuator/health`. When it returns `HTTP 200 OK`, the service transitions to **Active**.

---

## 4. Step-by-Step Deployment on Render

### Step 1: Provision MySQL Database
1. Provision a managed MySQL 8 database instance (via Render PostgreSQL/MySQL add-on or external managed provider).
2. Note your host, port, database name, username, and password.

### Step 2: Create Web Service
1. In the Render Dashboard, click **New +** $\rightarrow$ **Web Service**.
2. Connect your Git repository.
3. Select **Environment**: `Docker`.
4. Under **Advanced** $\rightarrow$ **Health Check Path**, enter `/actuator/health`.
5. Under **Environment Variables**, define:
   - `SPRING_PROFILES_ACTIVE`: `prod`
   - `DB_URL`: `jdbc:mysql://<DB_HOST>:<DB_PORT>/<DB_NAME>?useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC`
   - `DB_USERNAME`: `<DB_USERNAME>`
   - `DB_PASSWORD`: `<DB_PASSWORD>`
   - `JPA_DDL_AUTO`: `update`
   - `PROPIO_SEED_ENABLED`: `false`
   - `PROPIO_ADMIN_EMAIL`: `<SET-INITIAL-ADMIN-EMAIL>`
   - `PROPIO_ADMIN_PASSWORD`: `<SET-STRONG-ADMIN-PASSWORD>`
   - `PROPIO_ADMIN_NAME`: `System Administrator`
   - `PROPIO_REMEMBER_ME_KEY`: `<GENERATE-RANDOM-64-HEX-STRING>`
   - `PROPIO_MAIL_ENABLED`: `false`
6. Click **Create Web Service**.

---

## 5. Post-Deployment Verification

1. **Verify Health Endpoint**:
   ```bash
   curl -i https://<YOUR-APP-DOMAIN>/actuator/health
   ```
   Expected response (`HTTP/1.1 200 OK`):
   ```json
   {
     "status": "UP",
     "components": {
       "db": {
         "status": "UP",
         "details": {
           "database": "MySQL",
           "validationQuery": "isValid(2)"
         }
       },
       "diskSpace": {
         "status": "UP"
       }
     }
   }
   ```

2. **Verify Admin Authentication**:
   - Open `https://<YOUR-APP-DOMAIN>/login` in your browser.
   - Confirm the Quick Demo Credentials card is **not present**.
   - Sign in using your `PROPIO_ADMIN_EMAIL` and `PROPIO_ADMIN_PASSWORD`.
   - Access to `/dashboard` is granted with full administrative privileges.

3. **Schema Lockdown (Post-Initialization)**:
   - Once all tables have been verified on initial boot, update `JPA_DDL_AUTO=validate` in the environment variables to lock the production database schema against accidental modifications.
