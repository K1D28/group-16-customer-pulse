# Customer Pulse (prototype)

Customer status and activity monitoring platform, built from the Group 16 SRS (v1.1).
Spring Boot 3 · Spring Data JPA · Thymeleaf · Bootstrap 5 + custom CSS · H2 (local) / MySQL (Cloud SQL).

The original front-end-only mock-up is kept in `prototype/index.html` for reference.

## Run locally

Needs Java 21 and Maven.

```bash
mvn spring-boot:run
# or
mvn -DskipTests package && java -jar target/customer-pulse.jar
```

Open http://localhost:8080. A fresh database starts **clean**: 3 teams, one Manager each, and no other data
(password `password123` for all, also one click away on the login page):

| Team | Manager |
|---|---|
| Team A | priya@example.com |
| Team B | daniel@example.com |
| Team C | sofia@example.com |

Each manager adds their own staff under **User management** (new users join that manager's team), then customers and cases.
Want sample data instead? Set `app.seed-demo-data=true` to add 2 staff, 15 customers, activities, follow-ups and cases to Team A.

By default the app uses an **embedded H2** database stored in `./data/`. Stop the app and delete that folder to start clean again.
Managers can browse it at http://localhost:8080/h2-console (JDBC URL `jdbc:h2:file:./data/customerpulse`, user `sa`, no password).

## How this maps to the assignment

| Rubric item | Where |
|---|---|
| **Controls: Listing / Searching** | Customers list with search + status filter, 10 per page (`/customers`); activity log with type/channel filters (`/activities`, Manager-only); follow-up tabs (`/followups`); team overview filters (`/team`) |
| **Controls: Creating** | Customers, activities, follow-ups, users |
| **Controls: Updating** | Edit customer, activity, follow-up, user; change status; complete/reopen follow-up |
| **Controls: Deleting** | Delete customer (removes its activities, follow-ups and history), activity, follow-up, user (blocked while they still own customers) |
| **Controls: Managing related entities** | Customer page manages its activities, follow-ups (optionally linked to an activity) and status history; reassign owner; follow-up assignee |
| **5 related entities** | `AppUser` (users), `Customer` (customers), `Activity` (activities), `FollowUp` (follow_ups), `StatusHistory` (status_history), plus `Team` (teams), `ServiceCase` (cases) and `CaseAssignment` (case_assignments) in `src/main/java/com/customerpulse/domain` |
| **Front-end** | Thymeleaf + Bootstrap 5 + `static/css/app.css`; works down to 360 px wide |
| **Database (H2 / MySQL)** | H2 by default; `mysql` profile for Cloud SQL (`application-mysql.properties`) |
| **Deployment on GCP** | `app.yaml` (App Engine) or `Dockerfile` (Cloud Run), see below |

### Relationships

```
teams 1──* users                (team_id)
users 1──* customers            (owner_id)
users 1──* activities           (user_id = who logged it)
users 1──* follow_ups           (assigned_user_id)
users 1──* status_history       (changed_by_user_id)
users 1──* cases                (created_by_user_id)
customers 1──* activities / follow_ups / status_history / cases
activities 1──* follow_ups      (activity_id, optional)
cases 1──* case_assignments *──1 users   (who is working on the case)
cases 1──* activities           (case_id, optional)
```

### SRS features covered

- FR-01 login with bcrypt-hashed passwords; generic "Invalid email or password"; 30-minute idle timeout
- FR-02 customer create/edit with a "similar name already exists" warning (e.g. "ABC Co." vs "ABC Company Ltd."); new customers start as NEGOTIATING with a "New lead created" history entry
- FR-03 timeline newest first, "No activity recorded" when empty
- FR-04 log activity (type, channel, outcome, date/time) with per-field errors, then an offer to add a follow-up
- FR-05 follow-ups: PENDING → COMPLETED; PENDING past due becomes OVERDUE at startup and every night
- FR-06 status change + history entry in one transaction; same status = nothing saved
- FR-07 dashboard alerts: overdue follow-ups and customers with no activity for more than 14 days (or none), most urgent first
- FR-08 team overview: counts per status, workload per person, recent status changes, filters
- FR-09 reassign owner (Manager)
- FR-10 user management (Manager): unique email, role MANAGER/STAFF, can't delete a user who owns customers
- FR-11 search by company/contact name and status
- NFR-02 access rules are checked on the server: Staff get a 403 for other people's customers (including typed URLs), `/users` and `/team`

## Scenario tests

`src/test/java/com/customerpulse/ScenarioTests.java` runs the 8 scenarios from *Customer Pulse — Scenario-Based Use Cases* in order on a clean start, one test per scenario, with each main and alternative step checked:

| # | Scenario | Test |
|---|---|---|
| 1 | Registering a new customer (FR-02) | `scenario1_registeringANewCustomer` |
| 2 | Recording a meeting and scheduling a follow-up (FR-04, FR-05) | `scenario2_…` |
| 3 | Detecting and completing an overdue follow-up (FR-05, FR-07) | `scenario3_…` |
| 4 | Identifying a stagnant customer (FR-03, FR-07) | `scenario4_…` |
| 5 | Changing a customer's lifecycle status (FR-06) | `scenario5_…` |
| 6 | Reassigning to another staff member (FR-09) | `scenario6_…` |
| 7 | Preventing unauthorized access (NFR-02) | `scenario7_…` |
| 8 | Monitoring the team's customer statuses (FR-08) | `scenario8_…` |

Run them with `mvn test`.

**Deactivating users (Scenario 6):** on User management, a Manager can **Deactivate** someone who is leaving (after reassigning their customers). Inactive users cannot log in and are not offered as owners, case assignees or follow-up assignees; the server refuses them too. When reassigning a customer, the Manager can also move the old owner's open follow-ups to the new owner.

## Teams (added after the SRS)

- Every user belongs to one team. A **Manager only sees their own team**: its staff, customers, cases, follow-ups, activity and status history. Other teams' records return "no access" or "not found".
- Customer owners, case assignees and follow-up assignees must be in the same team.
- A Manager can **rename their team** on Team overview (**Rename team**). Names must be unique.
- The **Activity log** page is Manager-only. Staff still log activities from a customer or a case.

## Cases (added after the SRS)

A Manager creates a **case** for a customer and assigns one or more employees.

1. **Manager** → Cases → New case: title, customer, priority, optional due date, what needs doing, and the assignees.
2. **Assignees** see it under Cases (the menu badge counts cases waiting for them) and on their dashboard. They can **log activity on the case**, even when someone else owns the customer.
3. Each assignee clicks **Mark my part done** (with an optional note), and can undo it with **Not done yet**.
4. Status: **Open** (nothing done yet) → **In progress** (first activity or first part done) → **Closed** automatically when **every** assignee is done. No new activity can be logged on a closed case.
5. The Manager can also close a case early, reopen it (everyone has to mark done again), edit it, change the assignees (adding someone to a closed case reopens it), or delete it. Deleting a case keeps its activities on the customer timeline.

The case page shows the progress bar ("1 of 2 done"), each assignee's state, the case activity, and the customer's contact details. Case activities also appear on the customer timeline with a `CASE-0001` tag.

## Project layout

```
src/main/java/com/customerpulse/
  domain/       entities + enums
  repository/   Spring Data JPA repositories
  service/      business rules and access checks (AccessGuard)
  security/     Spring Security config, login lookup
  web/          MVC controllers, form classes
  config/       DataSeeder (demo data on first start)
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

Tables are created automatically on first start (`ddl-auto=update`), and the 3 teams and their managers are added if the `users` table is empty. Change their passwords after the first login.

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
