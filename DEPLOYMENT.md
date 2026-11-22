# Production Deployment Guide: Render & Railway with MySQL

This guide walks you through deploying **Propio** as a containerized Spring Boot service with a managed **MySQL 8** database on **Railway** or **Render**.

---

## 1. Architecture Overview

```
 [ Client Browser ]
        │ HTTPS (SSL Termination)
        ▼
 [ Reverse Proxy (Render / Railway) ]
        │ Native Header Forwarding
        ▼
 [ Propio Spring Boot Container (port 8080) ]
        │ HikariCP Connection Pool (SSL enabled)
        ▼
 [ Managed MySQL 8 Database (propio_db) ]
```

- **Base Profile**: `prod` (`application-prod.properties`)
- **Container**: Multi-stage Docker image running Eclipse Temurin 17 JRE as an unprivileged user (`spring`).
- **Healthcheck**: Public `/actuator/health` endpoint returning JSON DB status and disk headroom.
- **Security**: Zero stack trace exposure, CSP/HSTS/Frame security headers, encrypted cookies, login rate limiting.

---

## 2. Environment Variables Reference

Configure the following environment variables in your cloud provider's dashboard:

| Variable | Description | Example / Recommended Value | Required |
| :--- | :--- | :--- | :---: |
| `SPRING_PROFILES_ACTIVE` | Spring Boot active profile | `prod` | Yes |
| `PORT` | HTTP server port | `8080` (automatically set by Render/Railway) | Yes |
| `DB_URL` | JDBC MySQL connection URL | `jdbc:mysql://${MYSQLHOST}:${MYSQLPORT}/${MYSQLDATABASE}?useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC` | Yes |
| `DB_USERNAME` | MySQL database user | `${MYSQLUSER}` (e.g. `root`) | Yes |
| `DB_PASSWORD` | MySQL database password | `${MYSQLPASSWORD}` | Yes |
| `JPA_DDL_AUTO` | Hibernate schema strategy | `update` (initial) or `validate` (strict) | Yes |
| `PROPIO_ADMIN_EMAIL` | Email for initial administrator bootstrap | `admin@yourdomain.com` | Yes |
| `PROPIO_ADMIN_PASSWORD` | Strong initial admin password (min 8 chars, 1 upper, 1 lower, 1 digit) | `Admin@Propio2026!` | Yes |
| `PROPIO_ADMIN_NAME` | Initial administrator full name | `Property Manager` | No |
| `PROPIO_SEED_ENABLED` | Populate mock tenants/properties | `false` (Production) / `true` (Staging) | No |
| `PROPIO_REMEMBER_ME_KEY`| Secret signing key for Remember-Me tokens | High-entropy 64-char random hex string | Yes |
| `PROPIO_MAIL_ENABLED` | Enable live SMTP email dispatch | `true` (Production) / `false` (Log-only) | No |
| `SMTP_HOST` | Outbound mail server hostname | `smtp.sendgrid.net` or `smtp.mailgun.org` | When mail on |
| `SMTP_PORT` | Outbound mail server port | `587` | When mail on |
| `SMTP_USERNAME` | Outbound mail username | `apikey` | When mail on |
| `SMTP_PASSWORD` | Outbound mail password / API key | Secret API token | When mail on |
| `PROPIO_MAIL_FROM` | Sender email address | `noreply@yourdomain.com` | When mail on |

---

## 3. Deployment on Railway

### Step 1: Provision Managed MySQL
1. Log in to [Railway](https://railway.app).
2. Click **New Project** → **Provision MySQL**.
3. Once the database is online, navigate to the **Variables** tab to view your credentials:
   - `MYSQLHOST`
   - `MYSQLPORT`
   - `MYSQLUSER`
   - `MYSQLPASSWORD`
   - `MYSQLDATABASE`

### Step 2: Deploy Propio Service
1. In the same project, click **New** → **GitHub Repo** → select your `propio` repository.
2. Railway automatically detects the `Dockerfile` at root.
3. In the service **Settings**:
   - Set **Healthcheck Path** to `/actuator/health`.
   - Set **Healthcheck Timeout** to `100`.
4. In the service **Variables** tab, add:
   ```properties
   SPRING_PROFILES_ACTIVE=prod
   DB_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC
   DB_USERNAME=${{MySQL.MYSQLUSER}}
   DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
   JPA_DDL_AUTO=update
   PROPIO_SEED_ENABLED=false
   PROPIO_ADMIN_EMAIL=admin@yourdomain.com
   PROPIO_ADMIN_PASSWORD=Admin@Propio2026!
   PROPIO_ADMIN_NAME=Head Administrator
   PROPIO_REMEMBER_ME_KEY=a7d8e9f2b1c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9
   ```
5. Click **Deploy**. Railway will build the multi-stage Docker image and start the service.
6. Under **Settings** → **Networking**, click **Generate Domain** (e.g. `propio-production.up.railway.app`).

---

## 4. Deployment on Render

### Step 1: Create MySQL Database
1. In Render, you can attach an external managed MySQL database (such as PlanetScale, Aiven, or Railway MySQL) or provision a containerized MySQL service.
2. Note your connection details: `host`, `port`, `database`, `user`, and `password`.

### Step 2: Create Web Service
1. In the Render Dashboard, click **New +** → **Web Service**.
2. Connect your Git repository.
3. Choose:
   - **Environment**: `Docker`
   - **Region**: Select closest to your database.
   - **Branch**: `main`
   - **Plan**: Starter or Standard.
4. Under **Advanced** → **Health Check Path**, enter:
   ```
   /actuator/health
   ```
5. Under **Environment Variables**, define:
   ```properties
   SPRING_PROFILES_ACTIVE=prod
   PORT=8080
   DB_URL=jdbc:mysql://<MYSQL_HOST>:<MYSQL_PORT>/<MYSQL_DATABASE>?useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC
   DB_USERNAME=<MYSQL_USER>
   DB_PASSWORD=<MYSQL_PASSWORD>
   JPA_DDL_AUTO=update
   PROPIO_SEED_ENABLED=false
   PROPIO_ADMIN_EMAIL=admin@yourdomain.com
   PROPIO_ADMIN_PASSWORD=Admin@Propio2026!
   PROPIO_ADMIN_NAME=Enterprise Admin
   PROPIO_REMEMBER_ME_KEY=f4c8b2a1e9d3c5f7a0b2c4d6e8f1a3b5c7d9e0f2a4b6c8d0e2f4a6b8c0d2e4f6
   ```
6. Click **Create Web Service**. Render builds the image and conducts the health check.

---

## 5. First Boot & Verification

1. **Verify Healthcheck**:
   ```bash
   curl -i https://your-propio-domain/actuator/health
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
2. **Access Administrator Dashboard**:
   - Navigate to `https://your-propio-domain/login`.
   - Notice that Quick Demo Credentials are **not visible** in production.
   - Log in using your configured `PROPIO_ADMIN_EMAIL` and `PROPIO_ADMIN_PASSWORD`.
   - Access is granted directly to `/dashboard`.

---

## 6. Backup & Maintenance

1. **Database Backups**: Schedule automated daily MySQL dumps via cloud backup or CLI:
   ```bash
   mysqldump -h <HOST> -u <USER> -p<PASS> propio_db --single-transaction --quick > backup_$(date +%F).sql
   ```
2. **File Storage**: In clustered production environments, set `PROPIO_UPLOAD_DIR` to a shared persistent volume or mount AWS S3 / Cloudflare R2 bucket.
