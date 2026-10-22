# Demonstration Guide: Reproducing Project Results

This guide provides a scripted, end-to-end walkthrough for professors, evaluators, and examiners to execute the **Agilify** project and reproduce all database invariants, security rules, and real-time features.

---

## 1. Demo User Accounts

The system includes 3 pre-configured demo personas:

| Username | Password | Email | Role in Demo Project |
| :--- | :--- | :--- | :--- |
| **alice** | `alice123` | `alice@example.com` | **ADMIN** (Project Creator) |
| **bob** | `bob123` | `bob@example.com` | **DEVELOPER** (Full Edit Rights) |
| **carol** | `carol123` | `carol@example.com` | **VIEWER** (Read-Only Rights) |

---

## 2. End-to-End Demonstration Walkthrough

### Act 1: Keycloak Single Sign-On & User Sync
1. Open the application at `http://localhost:5173` (or `https://35-73-249-109.sslip.io`).
2. The browser redirects to the Keycloak login screen.
3. Sign in as **alice** (`alice123`).
4. **Database Verification:**  
   Open `psql` or pgAdmin and run:
   ```sql
   SELECT id, email, full_name, status, password_hash FROM users WHERE email = 'alice@example.com';
   ```
   *Notice:* Alice was automatically inserted with `password_hash = 'MANAGED_BY_KEYCLOAK'`.

---

### Act 2: Project Creation & Dynamic Workflow Seeding
1. On the dashboard, click **Create Project**.
2. Fill in:
   - **Project Name:** `Cloud Infrastructure Management`
   - **Key:** `CIM` (Must match regex `^[A-Z]{2,10}$`)
   - **Description:** `Semester project infrastructure tracker`
3. Click **Submit**.
4. **Database Verification:**
   ```sql
   SELECT p.name, p.key, m.role, u.full_name
   FROM projects p
   JOIN project_members m ON p.id = m.project_id
   JOIN users u ON m.user_id = u.id
   WHERE p.key = 'CIM';
   ```
   *Notice:* Alice is automatically assigned the `ADMIN` role in `project_members`.
   Check `workflow_config`:
   ```sql
   SELECT count(*) FROM workflow_config WHERE project_id = (SELECT id FROM projects WHERE key = 'CIM');
   ```
   *Notice:* 16 transition rules were seeded automatically.

---

### Act 3: Adding Team Members & Testing RBAC
1. Go to **Project Settings &rarr; Members**.
2. Search for `bob@example.com` and add him with role **DEVELOPER**.
3. Search for `carol@example.com` and add her with role **VIEWER**.
4. Attempt to remove Alice (the sole Admin).
   - *Result:* Application blocks the action: `400 Bad Request: Cannot remove the last project Admin`.

---

### Act 4: Creating Issues & Concurrency Locking
1. Navigate to **Backlog &rarr; Create Issue**.
2. Create:
   - **Issue 1:** `Set up PostgreSQL Session Pooler` (Type: `TASK`, Priority: `P1`, Points: `3`)
   - **Issue 2:** `OAuth callback timeout on mobile` (Type: `BUG`, Priority: `P1`, Points: `5`)
3. *Notice:* Sequential keys generated are `CIM-1` and `CIM-2`.
4. *Engineering proof:* The server locked the project row with `SELECT ... FOR UPDATE` (`PESSIMISTIC_WRITE`), preventing duplicate number race conditions.

---

### Act 5: AI Duplicate Detection Advisory
1. Click **Create Issue** again.
2. In the Title field, type:  
   `"Login fails over cellular network with OAuth error"`
3. *Observe:* An advisory blue alert banner appears:
   > *"AI Duplicate Warning: Similar to CIM-2 ('OAuth callback timeout on mobile'). Similarity: High."*
4. *Notice:* The check is advisory and non-blocking; the user can still proceed to submit.

---

### Act 6: Sprint Planning & Single Active Sprint Proof
1. Go to **Sprints &rarr; Create Sprint** (`Sprint 1`).
2. Drag `CIM-1` and `CIM-2` into `Sprint 1`.
3. Click **Start Sprint**. Its status changes to `ACTIVE`.
4. Create a second sprint: `Sprint 2`.
5. Attempt to start `Sprint 2` while `Sprint 1` is active.
   - *Result:* Blocked by application and backed by PostgreSQL index `idx_sprints_one_active_per_project`.

---

### Act 7: Kanban Board Drag-and-Drop & Trigger Execution
1. Navigate to the **Board**.
2. Drag `CIM-1` from `TODO` &rarr; `IN_PROGRESS`.
3. **Database Verification:**
   ```sql
   SELECT issue_number, status, started_at, completed_at FROM issues WHERE issue_number = 1;
   ```
   *Notice:* `started_at` was stamped automatically by the `trg_issues_timestamps` trigger!
4. Check `issue_history`:
   ```sql
   SELECT field_name, old_value, new_value, changed_at FROM issue_history WHERE issue_id = (SELECT id FROM issues WHERE issue_number = 1);
   ```
   *Notice:* The status change was audited immutably.
5. Drag `CIM-1` to `DONE`: `completed_at` is stamped automatically.

---

### Act 8: Workflow Guard State Machine Violation
1. Drag `CIM-2` directly from `BACKLOG` &rarr; `DONE`.
2. *Observe:* The drop is rejected and snaps back with an error toast:
   > *"Workflow Violation: Transition from BACKLOG to DONE is not allowed for issue type BUG."*
3. Permitted path: `BACKLOG &rarr; TODO &rarr; IN_PROGRESS &rarr; IN_REVIEW &rarr; DONE`.

---

### Act 9: Real-Time Collaborative CRDT Documents
1. Open a second browser window in **Incognito Mode**.
2. Log in as **bob** (`bob123`).
3. Both Alice (Browser 1) and Bob (Browser 2) open **Documents &rarr; Project Specification**.
4. Both users type simultaneously in different paragraphs.
5. *Observe:* Both cursors and text updates synchronize in real time over WebSockets with zero merge conflicts or locking.
6. Check `doc_snapshots`:
   ```sql
   SELECT doc_id, version, saved_at, octet_length(snapshot) AS bytes FROM doc_snapshots;
   ```
   *Notice:* Raw binary Yjs delta vectors (`BYTEA`) are appended chronologically.

---

### Act 10: Role-Based Authorization Enforcement
1. In the incognito window, log out and log in as **carol** (`carol123`, role: `VIEWER`).
2. Carol can view issues and the board, but all creation/editing buttons are hidden.
3. If Carol attempts a `POST` or `PATCH` mutation directly via curl/Postman:
   ```bash
   curl -X POST http://localhost:8082/api/v1/projects/CIM/issues \
     -H "Authorization: Bearer <carol_jwt_token>" \
     -H "Content-Type: application/json" \
     -d '{"title": "Hacked Issue", "type": "BUG"}'
   ```
   - *Result:* HTTP **`403 Forbidden`** returned by `@PreAuthorize`.
