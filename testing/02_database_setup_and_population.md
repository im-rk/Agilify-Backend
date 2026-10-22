# Steps to Create and Populate the Database

This guide explains how to set up, initialize, and populate the PostgreSQL database for the **Agilify** platform, either via automated Flyway migrations or standalone SQL scripts.

---

## 1. Database Connection Parameters

| Parameter | Local Docker Setup | Supabase Cloud Setup |
| :--- | :--- | :--- |
| **Host** | `localhost` | `aws-0-ap-northeast-1.pooler.supabase.com` |
| **Port** | `5432` | `5432` (Session Pooler) |
| **Database Name** | `bugtracker` | `postgres` |
| **Username** | `bugtracker_user` | `postgres.your-project-ref` |
| **Password** | `bugtracker_password` | *(Stored in AWS Parameter Store)* |
| **SSL Mode** | `disable` (Local) | `require` (Cloud) |

---

## 2. Option A: Automated Setup via Docker Compose & Flyway (Recommended)

When using the project's standard development workflow, the database is spun up and migrated automatically:

### Step 1: Start PostgreSQL & Keycloak Containers
From the `core/` directory:

```bash
docker compose up -d
```

This starts:
- PostgreSQL 15 on port `5432`.
- Keycloak 24 on port `8080` (auto-importing realm `agilify`).

### Step 2: Apply Flyway Migrations
Start the Spring Boot backend:

```bash
./mvnw spring-boot:run
```

Flyway detects the database and executes all 6 versioned migration scripts in `core/src/main/resources/db/migration/`:
- `V1__initial_schema.sql`: Core 11 tables, constraints, updated_at trigger.
- `V2__add_board_position.sql`: Board drag-and-drop position column.
- `V3__notifications.sql`: User activity notifications.
- `V4__project_docs.sql`: Collaborative documents and Yjs binary snapshot store.
- `V5__notification_project_key.sql`: Deep link project keys.
- `V6__enable_vector_extension.sql`: pgvector vector store extension.

---

## 3. Option B: Manual Setup via Standalone SQL Scripts

If evaluating purely at the database layer (e.g. via pgAdmin, DBeaver, or terminal `psql`):

### Step 1: Create Database & User (If not using existing)
Connect as PostgreSQL superuser (`postgres`):

```sql
CREATE USER bugtracker_user WITH PASSWORD 'bugtracker_password';
CREATE DATABASE bugtracker OWNER bugtracker_user;
GRANT ALL PRIVILEGES ON DATABASE bugtracker TO bugtracker_user;
```

### Step 2: Run DDL Schema Script
Execute `database/01_schema.sql` to create all 14 tables, constraints, partial indexes, and triggers:

```bash
psql -U bugtracker_user -d bugtracker -f database/01_schema.sql
```

### Step 3: Populate Sample Seed Data
Execute `database/02_seed_data.sql` to populate realistic users, project `AD`, active/planned sprints, issues, and comments:

```bash
psql -U bugtracker_user -d bugtracker -f database/02_seed_data.sql
```

---

## 4. Database Population Verification

Run this SQL query to verify that all 14 application tables exist and contain populated rows:

```sql
SELECT 
    table_name,
    (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM %I', table_name), false, true, '')))[1]::text::int AS total_rows
FROM information_schema.tables
WHERE table_schema = 'public' 
  AND table_name != 'flyway_schema_history'
ORDER BY table_name;
```

### Expected Output:

| table_name | total_rows |
| :--- | :---: |
| `comments` | 3 |
| `doc_snapshots` | 1 |
| `issue_history` | 3 |
| `issue_labels` | 8 |
| `issue_watchers` | 5 |
| `issues` | 6 |
| `labels` | 5 |
| `notifications` | 2 |
| `project_docs` | 1 |
| `project_members` | 3 |
| `projects` | 1 |
| `sprints` | 2 |
| `users` | 3 |
| `workflow_config` | 12 |

---

## 5. Constraint & Trigger Integrity Check

Verify the automated timestamp trigger `trg_issues_timestamps`:

```sql
SELECT issue_number, title, status, started_at, completed_at
FROM issues
ORDER BY issue_number;
```

- Issues with status `IN_PROGRESS` or `DONE` will display non-null `started_at`.
- Issues with status `DONE` will display non-null `completed_at`.
