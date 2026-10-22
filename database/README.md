# Agilify: Core DBMS Assets

This directory contains the core database engineering assets for the **Agilify** project, meeting the submission requirements for **22AIE303: Database Management Systems**.

---

## Directory Contents

| File | Purpose | Description |
| :--- | :--- | :--- |
| **[`01_schema.sql`](01_schema.sql)** | **Complete DDL Scripts** | Defines all 14 tables, data types, `PRIMARY KEY`, `FOREIGN KEY`, `CHECK`, `UNIQUE`, `ON DELETE CASCADE`, partial unique indexes, and PL/pgSQL triggers. |
| **[`02_seed_data.sql`](02_seed_data.sql)** | **Sample Demo Data** | Populates realistic users (`Alice`, `Bob`, `Carol`), project `AD`, active/planned sprints, issues, labels, watchers, threaded comments, and audit history. |
| **[`03_queries.sql`](03_queries.sql)** | **Complex Documented Queries** | Demonstrates multi-table joins (3-5 tables), nested/correlated subqueries, `GROUP BY` with `HAVING`, Window functions, and Recursive CTEs. |

---

## How to Execute the Scripts

You can execute these scripts against PostgreSQL using the command-line client `psql`, pgAdmin, or DBeaver.

### 1. Using Command-Line `psql`

```bash
# Step 1: Create the database (if not already existing)
createdb -U postgres bugtracker

# Step 2: Apply the full DDL schema (Tables, Constraints, Triggers)
psql -U postgres -d bugtracker -f 01_schema.sql

# Step 3: Populate sample demo records
psql -U postgres -d bugtracker -f 02_seed_data.sql

# Step 4: Run the complex analytical queries
psql -U postgres -d bugtracker -f 03_queries.sql
```

### 2. Using Docker (Included in Local Dev Setup)

If running through the project's local Docker Compose setup:

```bash
# Execute schema creation inside the running postgres container
docker exec -i agilify-postgres psql -U bugtracker_user -d bugtracker < database/01_schema.sql

# Load sample data
docker exec -i agilify-postgres psql -U bugtracker_user -d bugtracker < database/02_seed_data.sql

# Execute queries
docker exec -i agilify-postgres psql -U bugtracker_user -d bugtracker < database/03_queries.sql
```

---

## Relational Schema Summary (14 Tables)

1. **`users`**: User identities, soft-deletions, and email partial unique index.
2. **`projects`**: Workspaces, project key formatting check (`^[A-Z]{2,10}$`).
3. **`project_members`**: User-project role assignment (`ADMIN`, `DEVELOPER`, `VIEWER`).
4. **`workflow_config`**: State-machine status transition rules.
5. **`sprints`**: Iterations with partial unique index ensuring **at most 1 active sprint**.
6. **`issues`**: Work items with optimistic locking (`version`), priority checks, and GIN indexes.
7. **`issue_history`**: Immutable, append-only field modification audit trail.
8. **`labels`**: Categorization tags with per-project uniqueness.
9. **`issue_labels`**: Associative junction table with composite primary key `(issue_id, label_id)`.
10. **`issue_watchers`**: Notification subscriptions with composite primary key `(issue_id, user_id)`.
11. **`comments`**: Threaded discussions with self-referential `parent_comment_id`.
12. **`notifications`**: Activity inbox for subscribed watchers.
13. **`project_docs`**: Collaborative document metadata.
14. **`doc_snapshots`**: Append-only Yjs CRDT binary deltas (`BYTEA`).
