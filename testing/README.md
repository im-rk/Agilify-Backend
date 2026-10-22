# Agilify: Testing, Validation, and Demonstration Assets

This directory contains the testing, validation, configuration, and demonstration assets for **Agilify**, satisfying the academic evaluation criteria for **22AIE303: Database Management Systems**.

---

## Directory Navigation

| File / Folder | Contents & Scope |
| :--- | :--- |
| **[`01_test_cases_and_validation.md`](01_test_cases_and_validation.md)** | **12 Formal Test Cases (TC-01 to TC-12)** covering authentication, user sync, sequential key locks, workflow state machine, single active sprint, triggers, RBAC, CRDT docs, and soft deletes. |
| **[`02_database_setup_and_population.md`](02_database_setup_and_population.md)** | **Database Creation & Population Guide** for both automated Flyway migrations and standalone SQL scripts (`01_schema.sql`, `02_seed_data.sql`), with row verification queries. |
| **[`03_application_run_instructions.md`](03_application_run_instructions.md)** | **Application Execution Manual** detailing prerequisites, port mappings (5432, 8080, 8082, 5173), Docker Compose startup, Spring Boot backend run, and React frontend launch. |
| **[`04_demonstration_guide.md`](04_demonstration_guide.md)** | **Step-by-Step Demonstration Script** with 10 structured acts illustrating how to log in as demo personas (`Alice`, `Bob`, `Carol`), reproduce invariants, and test security rules. |
| **[`05_sample_query_outputs.md`](05_sample_query_outputs.md)** | **Actual Terminal Execution Outputs** from running multi-table joins, subqueries, sprint velocity metrics, Window functions, and recursive comment trees. |
| **[`screenshots/`](screenshots/)** | **Visual Evidence Index** containing high-resolution screenshots of the Keycloak login, Kanban board, workflow guard violations, AI duplicate alerts, and CRDT live docs. |

---

## Visual Proofs Available in `screenshots/`

- `01_keycloak_login.png`: Single Sign-On via Keycloak OIDC with PKCE.
- `02_board.png`: Kanban board with card drag-and-drop order.
- `03_workflow_guard_history.png`: Workflow state machine error toast and audit table.
- `04_ai_duplicate_warning.png`: Real-time Groq LLM advisory duplicate check.
- `05_live_doc_two_users.png`: Side-by-side simultaneous multi-user CRDT document sync.
- `06_rbac_viewer_denied.png`: Viewer role restriction (HTTP 403 Forbidden).
