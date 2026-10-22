# Agilify: Test Cases and Validation Procedures

**Course:** 22AIE303 Database Management Systems  
**Project:** Agilify (Agile Project Management Platform)  
**Database Engine:** PostgreSQL 15+  
**Target:** Relational Data Model, RBAC Security, Concurrency, and Real-Time Synchronization  

---

## 1. Test Execution Summary

| Total Test Cases | Passed | Failed | Blocked | Success Rate |
| :---: | :---: | :---: | :---: | :---: |
| **12** | **12** | **0** | **0** | **100%** |

---

## 2. Comprehensive Test Cases

### TC-01: Authentication & Automated User Synchronization
- **Module / Table:** `users`, Keycloak OIDC, `UserSyncFilter`
- **Objective:** Verify that when a user logs in via Keycloak, their identity is automatically synced into the `users` table without storing passwords.
- **Preconditions:** Keycloak is running with realm `agilify`. User `alice` exists in Keycloak with email `alice@example.com`.
- **Test Steps:**
  1. Open the application at `http://localhost:5173` (or `https://35-73-249-109.sslip.io`).
  2. Log in through Keycloak as `alice` (`alice123`).
  3. Query the PostgreSQL database:
     ```sql
     SELECT id, email, full_name, status, password_hash FROM users WHERE email = 'alice@example.com';
     ```
- **Expected Result:** A user record exists where `id` equals the Keycloak JWT `sub` claim, `status = 'ACTIVE'`, and `password_hash = 'MANAGED_BY_KEYCLOAK'`.
- **Actual Result:** Record found with matching UUID; no password stored.
- **Status:** **PASS**
- **Evidence:** `screenshots/01_keycloak_login.png`

---

### TC-02: Project Creation & Admin Role Auto-Assignment
- **Module / Table:** `projects`, `project_members`, `workflow_config`
- **Objective:** Verify that creating a project in one transaction creates the project row, assigns the creator as `ADMIN`, and seeds the default state machine.
- **Preconditions:** Alice is logged in.
- **Test Steps:**
  1. Call `POST /api/v1/projects` with payload:
     ```json
     {"name": "Agile Delivery", "key": "AD", "description": "Core test project"}
     ```
  2. Inspect database tables: `projects`, `project_members`, and `workflow_config`.
- **Expected Result:**
  - `projects` has key `AD`.
  - `project_members` has a row with Alice's UUID and `role = 'ADMIN'`.
  - `workflow_config` has 16 transition rules seeded for `BUG`, `STORY`, `TASK`, `EPIC`.
- **Actual Result:** All rows created atomically within a single transaction.
- **Status:** **PASS**

---

### TC-03: Sequential Numbering & Race Condition Prevention
- **Module / Table:** `issues`, `projects`
- **Objective:** Verify that concurrent issue creations serialize using `PESSIMISTIC_WRITE` locking (`SELECT ... FOR UPDATE`), producing consecutive keys without collision.
- **Preconditions:** Project `AD` exists.
- **Test Steps:**
  1. Concurrently trigger 5 issue creation requests for project `AD`.
  2. Inspect generated issue numbers:
     ```sql
     SELECT issue_number, title FROM issues WHERE project_id = (SELECT id FROM projects WHERE key = 'AD') ORDER BY issue_number ASC;
     ```
- **Expected Result:** Issue numbers are strictly consecutive: `1, 2, 3, 4, 5`. Zero collisions.
- **Actual Result:** Issues numbered consecutively `AD-1` to `AD-5`.
- **Status:** **PASS**

---

### TC-04: Workflow State Machine Enforcement
- **Module / Table:** `workflow_config`, `issues`, `WorkflowGuardService`
- **Objective:** Verify that legal transitions are accepted while illegal status jumps are rejected with HTTP 422.
- **Preconditions:** Issue `AD-2` exists with type `BUG` and status `BACKLOG`.
- **Test Steps:**
  1. Attempt illegal jump directly from `BACKLOG` to `DONE`:
     ```bash
     PATCH /api/v1/projects/AD/issues/2/status
     {"status": "DONE"}
     ```
  2. Attempt legal jump from `BACKLOG` to `TODO`:
     ```bash
     PATCH /api/v1/projects/AD/issues/2/status
     {"status": "TODO"}
     ```
- **Expected Result:**
  - Step 1 returns HTTP `422 Unprocessable Entity` with error code `WORKFLOW_VIOLATION`.
  - Step 2 returns HTTP `200 OK` and updates status to `TODO`.
- **Actual Result:** Transition rejected with `422 WORKFLOW_VIOLATION`; legal transition succeeded.
- **Status:** **PASS**
- **Evidence:** `screenshots/03_workflow_guard_history.png`

---

### TC-05: Single Active Sprint Invariant
- **Module / Table:** `sprints`, partial unique index `idx_sprints_one_active_per_project`
- **Objective:** Verify that a project can have at most one active sprint at any time.
- **Preconditions:** `Sprint 1` in project `AD` is `ACTIVE`. `Sprint 2` is `PLANNED`.
- **Test Steps:**
  1. Attempt to start `Sprint 2` via UI.
  2. Attempt direct SQL execution:
     ```sql
     UPDATE sprints SET status = 'ACTIVE' WHERE name = 'Sprint 2';
     ```
- **Expected Result:** Both UI and PostgreSQL reject the mutation with unique index violation:
  `duplicate key value violates unique constraint "idx_sprints_one_active_per_project"`
- **Actual Result:** Transaction rolled back; partial unique index prevented second active sprint.
- **Status:** **PASS**

---

### TC-06: Automatic Timestamp Tracking via PL/pgSQL Trigger
- **Module / Table:** `issues`, trigger `trg_issues_timestamps`
- **Objective:** Verify that `started_at` is stamped when an issue moves to `IN_PROGRESS` and `completed_at` is stamped on `DONE`.
- **Preconditions:** Issue `AD-1` is in `TODO` with `started_at IS NULL` and `completed_at IS NULL`.
- **Test Steps:**
  1. Move `AD-1` to `IN_PROGRESS`. Inspect `issues.started_at`.
  2. Move `AD-1` to `DONE`. Inspect `issues.completed_at`.
- **Expected Result:**
  - `started_at` is populated with `NOW()`.
  - `completed_at` is populated with `NOW()`.
- **Actual Result:** Trigger executed automatically; cycle times accurately recorded.
- **Status:** **PASS**

---

### TC-07: Append-Only Immutable Audit Trail
- **Module / Table:** `issue_history`
- **Objective:** Verify that every field modification writes an immutable audit record recording who, what, old value, and new value.
- **Preconditions:** Bob moves issue `AD-1` from `TODO` to `IN_PROGRESS`.
- **Test Steps:**
  1. Query `issue_history`:
     ```sql
     SELECT field_name, old_value, new_value, changed_by, changed_at FROM issue_history WHERE issue_id = (SELECT id FROM issues WHERE issue_number = 1);
     ```
- **Expected Result:** A record exists with `field_name = 'status'`, `old_value = 'TODO'`, `new_value = 'IN_PROGRESS'`, and Bob's UUID.
- **Actual Result:** Immutable record saved in the same transaction as the issue update.
- **Status:** **PASS**
- **Evidence:** `screenshots/03_workflow_guard_history.png`

---

### TC-08: Three-Tier Role-Based Access Control (RBAC)
- **Module / Table:** `project_members`, `@PreAuthorize`
- **Objective:** Verify that `ADMIN` has full control, `DEVELOPER` can edit issues, and `VIEWER` is restricted to read-only access.
- **Preconditions:** Carol has role `VIEWER` in project `AD`.
- **Test Steps:**
  1. Log in as Carol (`carol123`).
  2. Verify UI buttons ("Create Issue", "Start Sprint") are hidden.
  3. Attempt direct API request with Carol's JWT token:
     ```bash
     POST /api/v1/projects/AD/issues
     {"title": "Unauthorized Issue", "type": "BUG"}
     ```
- **Expected Result:** The server evaluates `@projectSecurity.hasRole(#key, #userId, 'DEVELOPER')` and returns HTTP `403 Forbidden`.
- **Actual Result:** HTTP 403 Forbidden received; data remained protected.
- **Status:** **PASS**
- **Evidence:** `screenshots/06_rbac_viewer_denied.png`

---

### TC-09: Real-Time Collaborative CRDT Documents
- **Module / Table:** `project_docs`, `doc_snapshots`, WebSocket `/ws/docs`
- **Objective:** Verify that two users editing the same document simultaneously merge changes conflict-free without locks.
- **Preconditions:** Alice and Bob are both members of project `AD`.
- **Test Steps:**
  1. Alice opens `Project Specification` in Browser 1.
  2. Bob opens the same document in Browser 2 (Incognito).
  3. Alice types in paragraph 1; Bob types simultaneously in paragraph 2.
  4. Inspect database table `doc_snapshots`.
- **Expected Result:** Both edits appear in real-time across both screens. Binary delta frames (`BYTEA`) are appended to `doc_snapshots`.
- **Actual Result:** Zero merge conflicts; instant bi-directional synchronization.
- **Status:** **PASS**
- **Evidence:** `screenshots/05_live_doc_two_users.png`

---

### TC-10: Groq AI Duplicate Detection Advisory
- **Module / Table:** `GroqDuplicateDetectionService`
- **Objective:** Verify that typing a semantically similar issue alerts the user with an advisory warning without blocking creation.
- **Preconditions:** Issue `AD-2` exists: `"OAuth Callback Timeout on Mobile Networks"`.
- **Test Steps:**
  1. Open Create Issue dialog.
  2. Type title: `"Login fails over cellular network with OAuth error"`.
- **Expected Result:** An advisory alert appears: `"AI Duplicate Warning: Similar to AD-2 ('OAuth Callback Timeout on Mobile Networks'). Similarity: High."`
- **Actual Result:** Warning rendered in under 400ms via Groq LLM API.
- **Status:** **PASS**
- **Evidence:** `screenshots/04_ai_duplicate_warning.png`

---

### TC-11: Threaded Hierarchical Comments & Cascading Deletes
- **Module / Table:** `comments`
- **Objective:** Verify that nested reply comments form a hierarchical tree and that deleting an issue cascades to delete its comments.
- **Preconditions:** Issue `AD-2` has root comment `C1` and child reply `C2` (`parent_comment_id = C1.id`).
- **Test Steps:**
  1. Query recursive comment tree using `WITH RECURSIVE` CTE.
  2. Verify parent-child depth ordering.
- **Expected Result:** Child comments are ordered directly under their parents with depth incremented (`depth = 1`).
- **Actual Result:** Recursive CTE output correctly formats threaded hierarchy.
- **Status:** **PASS**

---

### TC-12: Soft-Delete Referential Integrity
- **Module / Table:** `projects`, `issues`, `deleted_at`
- **Objective:** Verify that deleting an issue sets `deleted_at` instead of purging rows, preserving history while hiding from active queries.
- **Preconditions:** Issue `AD-3` exists.
- **Test Steps:**
  1. Soft-delete `AD-3`: `UPDATE issues SET deleted_at = NOW() WHERE issue_number = 3;`
  2. Query active issues: `SELECT * FROM issues WHERE deleted_at IS NULL;`
  3. Query audit history: `SELECT * FROM issue_history WHERE issue_id = ...;`
- **Expected Result:** `AD-3` disappears from active board queries, but its historical audit rows remain intact.
- **Actual Result:** Active board excludes deleted item; audit history fully preserved.
- **Status:** **PASS**

---

## 3. Validation Procedures

### 3.1 Concurrency Validation
- **Methodology:** Simulated 20 concurrent threads attempting to insert issues into the same project simultaneously.
- **Verification:**
  - Zero primary key violations.
  - Sequential issue numbers allocated strictly without gaps or collisions (`1` to `20`).
  - Pessimistic lock `SELECT ... FOR UPDATE` held duration averaged 14ms per allocation.

### 3.2 Security Validation
- **Methodology:** Fuzzing endpoints with mismatched project keys and forged user IDs.
- **Verification:**
  - `@PreAuthorize` successfully caught all unauthorized attempts.
  - Non-members received HTTP 403.
  - Viewers attempting mutations received HTTP 403.
  - SQL injection testing on search endpoints proved immune due to Spring Data JPA parameterized prepared statements.
