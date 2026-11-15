# Customer Pulse (prototype)

Customer status and activity monitoring platform, built to match the Group 16 SRS (v1.1).
Spring Boot 3 · Spring Data JPA · Thymeleaf · Bootstrap 5 + custom CSS · H2 (local) / MySQL (Cloud SQL).

The original front-end-only mock-up is kept in `prototype/index.html` for reference.

## Group members

```
6831503027  Sai Khant Zay Lynn Yaung
6831503015  Pranto Nokrek
6831503030  Ye Wint Thura Aung
6831503031  Ye Yint Thiha Aung
6631503073  Myint Myat Pyae Sone
```

## Responsibilities

| Member | Features |
|---|---|
| Myint Myat Pyae Sone | Database design and integration (JPA entities, repositories, H2/MySQL configuration) |
| Sai Khant Zay Lynn Yaung | Backend: customer management, profiles, and search |
| Pranto Nokrek | Backend: activity logging and follow-up tracking |
| Ye Wint Thura Aung | Frontend: dashboard, customer overview, and reporting |
| Ye Yint Thiha Aung | Frontend: authentication, user management, and access control |

## Run locally

Needs Java 21 and Maven.

```bash
mvn spring-boot:run
# or
mvn -DskipTests package && java -jar target/customer-pulse.jar
```

Open http://localhost:8080. A fresh database starts with only the first Manager (created at installation, FR-10).
The default login is `priya@example.com` with password `password123`; configure `APP_FIRST_MANAGER_NAME`,
`APP_FIRST_MANAGER_EMAIL` and `APP_FIRST_MANAGER_PASSWORD` before starting with a new database, especially
for a public deployment. These settings do not change existing users.

For demo data, set `APP_SEED_DEMO_DATA=true` before startup. This adds demo accounts and sample customers;
demo accounts use the known password `password123`, so do not enable them on a public deployment. Phone numbers
are 10 digits, numbers only.

By default the app uses an **embedded H2** database stored in `./data/`. Stop the app and delete that folder to start clean.
Managers can browse it at http://localhost:8080/h2-console (JDBC URL `jdbc:h2:file:./data/customerpulse`, user `sa`, no password).

**Upgrading an older database:** earlier versions had teams and cases. Start from an empty database
(delete `./data/`, or drop and recreate the MySQL schema), because the old `users.team_id` column is NOT NULL.

All dates use Thailand time (UTC+7).

## Screens (SRS section 4)

| UI | Screen | Path | Who |
|---|---|---|---|
| UI-01 | Login | `/login` | everyone |
| UI-02 | Dashboard: overdue follow-ups and stagnant customers, "last refreshed" time | `/dashboard` | Staff (own customers), Manager (all) |
| UI-03 | Customer list and search, 10 per page | `/customers` | |
| UI-04 | Add / edit customer, duplicate block and warning, delete | `/customers/new`, `/customers/{id}/edit` | |
| UI-05 | Customer detail: profile, follow-ups, status history, timeline | `/customers/{id}` | |
| UI-06 | Log / edit activity | `/activities/new`, `/activities/{id}/edit` | |
| UI-07 | Add / edit follow-up | `/followups/new`, `/followups/{id}/edit` | |
| UI-08 | Change status dialog | on UI-05 | |
| UI-09 | Reassign owner dialog | on UI-05 | Manager |
| UI-10 | Customer overview | `/overview` | Manager |
| UI-11 | User management | `/users` | Manager |
| UI-12 | Audit log | `/audit` | Manager |

## Data model (Appendix A)

Six tables in `src/main/java/com/customerpulse/domain`: `users` (`AppUser`), `customers`, `activities`,
`follow_ups`, `status_history`, `audit_log`.

```
users 1──* customers            (owner_id)
users 1──* activities           (user_id = who logged it)
users 1──* follow_ups           (assigned_user_id)
users 1──* status_history       (changed_by_user_id)
users 1──* audit_log            (user_id)
customers 1──* activities / follow_ups / status_history
customers 0..1──* audit_log     (customer_id; empty for user records)
activities 0..1──* follow_ups   (activity_id, optional)
```

Customers, activities and follow-ups are soft-deleted (`deleted_at`); users are deactivated (`is_active`), never deleted.

## SRS rules implemented

- **FR-01** bcrypt passwords, generic "Invalid email or password", 30-minute idle timeout, deactivated users are signed out on their next request
- **FR-02** Staff create (owned by themselves), view, edit and soft-delete their own customers; Managers do this for all and choose the owner on create. New customers start NEGOTIATING with a "New customer created" history entry
- **FR-03** timeline newest first, "No activity recorded"; soft-deleted customers are "not found"
- **FR-04** activity date cannot be in the future; outcome up to 2,000 characters; the owner or a Manager can edit or delete any activity of the customer (the original logger is kept); deleting keeps linked follow-ups
- **FR-05** follow-ups are assigned to the customer's owner automatically; due date today or later when set or changed; OVERDUE with a new date returns to PENDING; completed ones are read-only (no reopen) and record the completion time; a daily job at 00:05 marks past-due PENDING as OVERDUE (not audited)
- **FR-06** status change needs a reason (1–255 characters); same status saves nothing; status, history and audit entry in one transaction
- **FR-07** overdue follow-ups (oldest first) and stagnant customers: NEGOTIATING/ACTIVE with no activity for more than 14 days, counted from the latest activity or the creation date (most days first)
- **FR-08** Customer overview: counts per status, customer list filtered by owner and status, status changes and activities filtered by date range (default last 30 days), workload per person
- **FR-09** reassign to another active user; all open follow-ups move in the same transaction (the dialog shows how many); completed ones keep their assignee
- **FR-10** users are never deleted; deactivation is refused for yourself, for anyone who still owns customers, and for the last active Manager (also applies to changing their role); passwords at least 8 characters, also on reset
- **FR-11** search by company or contact name with an optional status filter, 10 per page, sorted by company name
- **FR-12** exact duplicate names (ignoring upper/lower case) are blocked; similar names (one contains the other, ignoring case, spaces and punctuation; 3+ characters) show up to 5 names with owners and "Save anyway"
- **FR-13 / NFR-07** every create, update and soft delete by a user is written to `audit_log` in the same transaction; the audit log has no edit or delete; passwords are recorded only as "password changed"
- **NFR-02** all access rules are checked on the server, including direct URLs and record IDs for activities and follow-ups

## Tests

Run with `mvn test`.

- `ScenarioTests` runs the 8 scenarios from *Customer Pulse — Scenario-Based Use Cases* in order on a clean start.
- `CustomerPulseApplicationTests` checks the SRS rules above against the demo data.

## Project layout

```
src/main/java/com/customerpulse/
  domain/       entities + enums
  repository/   Spring Data JPA repositories
  service/      business rules, access checks (AccessGuard), audit log (AuditService)
  security/     Spring Security config, login lookup
  web/          MVC controllers, form classes
  config/       InitialSetup (first Manager), DataSeeder (optional demo data)
src/main/resources/
  templates/    Thymeleaf pages
  static/       CSS, JS, favicon
```

## Deploy on Google Cloud

### 1. Create the Cloud SQL (MySQL) database

```bash
gcloud sql instances create customer-pulse-db --database-version=MYSQL_8_0 \
  --tier=db-f1-micro --region=asia-southeast1
gcloud sql databases create customer_pulse --instance=customer-pulse-db
gcloud sql users create pulse --instance=customer-pulse-db --password=YOUR_PASSWORD
gcloud sql instances describe customer-pulse-db --format='value(connectionName)'
# -> PROJECT:asia-southeast1:customer-pulse-db
```

Tables are created automatically on first start (`ddl-auto=update`), and the first Manager account is added if the `users` table is empty (set `APP_FIRST_MANAGER_EMAIL` / `APP_FIRST_MANAGER_PASSWORD` to choose it). Change the password after the first login.

### 2a. App Engine

Put the connection name and password into `app.yaml`, then:

```bash
mvn -DskipTests package
gcloud app deploy target/customer-pulse.jar --appyaml=app.yaml
```

The App Engine service account needs the **Cloud SQL Client** role.

### 2b. Cloud Run (alternative)

```bash
gcloud run deploy customer-pulse --source . --region=asia-southeast1 --allow-unauthenticated \
  --add-cloudsql-instances=PROJECT:asia-southeast1:customer-pulse-db \
  --set-env-vars="SPRING_PROFILES_ACTIVE=mysql,DB_USER=pulse,DB_PASS=YOUR_PASSWORD,DB_URL=jdbc:mysql:///customer_pulse?cloudSqlInstance=PROJECT:asia-southeast1:customer-pulse-db&socketFactory=com.google.cloud.sql.mysql.SocketFactory"
```

### Local MySQL

```bash
SPRING_PROFILES_ACTIVE=mysql DB_USER=root DB_PASS=secret mvn spring-boot:run
```

### Local app with Cloud SQL

The existing Cloud Run service and the local app can use the same Cloud SQL database. Changes made
locally affect the deployed app's data, including the startup job that marks overdue follow-ups.
Use a separate Cloud SQL database for isolated testing.

1. Authenticate with Application Default Credentials and start the Cloud SQL Auth Proxy in a terminal:

  ```bash
  gcloud auth application-default login
  cloud-sql-proxy lad-cd:us-central1:customerpulse --port 3306
  ```

2. In another terminal, enter the database password without placing it in shell history, then start
  the application:

  ```bash
  read -rs DB_PASS
  export DB_PASS
  SPRING_PROFILES_ACTIVE=mysql \
  DB_URL='jdbc:mysql://127.0.0.1:3306/customer_pulse' \
  DB_USER=pulse APP_SEED_DEMO_DATA=false \
  SPRING_JPA_HIBERNATE_DDL_AUTO=validate mvn spring-boot:run
  ```

  The password prompt is intentionally hidden. Open http://localhost:8080 and keep the proxy running
  until the application is stopped. `validate` checks the existing schema without changing it.

The deployed service is https://customerpulse-z7geytsu6q-uc.a.run.app. It uses the MySQL profile and
the Cloud SQL socket factory; its runtime service account needs the Cloud SQL Client role. Store the
database password in Secret Manager rather than committing it to source control.
