# Instructions to Configure and Run the Application

This document provides step-by-step instructions to configure, launch, and execute both the Spring Boot 3 backend and React 19 frontend applications.

---

## 1. System Prerequisites

Ensure the following tools are installed on your machine:

| Tool | Minimum Version | Verification Command |
| :--- | :--- | :--- |
| **Java Development Kit (JDK)** | OpenJDK 17 or 21 | `java -version` |
| **Node.js & npm** | Node 20 LTS, npm 10+ | `node -v` && `npm -v` |
| **Docker & Docker Compose** | Docker 24.0+, Compose v2 | `docker compose version` |
| **Git** | 2.30+ | `git --version` |

---

## 2. Port Allocation Table

| Service | Port | Access Type | Description |
| :--- | :---: | :--- | :--- |
| **PostgreSQL** | `5432` | Local / Private | Relational database engine |
| **Keycloak** | `8080` | Local / Edge Proxy | Identity provider and OAuth2 server |
| **Spring Boot API** | `8082` | Local / Private | Agilify REST API & WebSocket server |
| **React Frontend** | `5173` | Local Browser | Vite development server (SPA) |
| **Swagger UI** | `8082` | Local Browser | OpenAPI documentation interface |

---

## 3. Step-by-Step Execution Guide

### Step 1: Clone Repositories
Ensure both repositories reside in the same parent directory:

```bash
# Backend repository
git clone https://github.com/im-rk/Agilify-Backend.git
cd Agilify-Backend

# Frontend repository (in sibling folder)
git clone https://github.com/im-rk/Agilify-Frontend.git
```

---

### Step 2: Start PostgreSQL & Keycloak (Docker Compose)
From the `Agilify-Backend` directory, launch the backing services:

```bash
docker compose up -d
```

- Keycloak automatically imports the pre-configured realm: `keycloak/agilify-realm.json`.
- This creates the `agilify` realm with client `agilify-app` and test accounts (`alice`, `bob`, `carol`).

Verify containers are running:
```bash
docker compose ps
```

---

### Step 3: Run the Spring Boot Backend
From the `Agilify-Backend` directory:

```bash
# On Linux / macOS:
./mvnw spring-boot:run

# On Windows PowerShell:
.\mvnw.cmd spring-boot:run
```

- Spring Boot connects to PostgreSQL on `localhost:5432`.
- Flyway executes schema migrations automatically.
- The server starts on **`http://localhost:8082`**.

#### Verification:
Open Swagger UI in your browser:  
👉 **[http://localhost:8082/swagger-ui.html](http://localhost:8082/swagger-ui.html)**

---

### Step 4: Run the React Frontend
Open a new terminal window and navigate to `Agilify-Frontend`:

```bash
cd Agilify-Frontend
npm install
npm run dev
```

- Vite starts the local development server on **`http://localhost:5173`**.

---

## 4. Environment Configuration

### Backend (`core/src/main/resources/application.yml`):
```yaml
server:
  port: 8082

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/bugtracker
    username: bugtracker_user
    password: bugtracker_password
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/agilify
          jwk-set-uri: http://localhost:8080/realms/agilify/protocol/openid-connect/certs

groq:
  api-key: ${GROQ_API_KEY:dummy_key_for_local}
```

### Frontend (`Agilify-Frontend/.env.development`):
```env
VITE_API_BASE_URL=http://localhost:8082/api/v1
VITE_KEYCLOAK_URL=http://localhost:8080
VITE_KEYCLOAK_REALM=agilify
VITE_KEYCLOAK_CLIENT_ID=agilify-app
VITE_WS_BASE_URL=ws://localhost:8082
```

---

## 5. Live Production Cloud Environment

The application is also continuously hosted live on AWS:
- **Public URL:** [https://35-73-249-109.sslip.io](https://35-73-249-109.sslip.io)
- **Architecture:** AWS EC2 `t3.micro` + Supabase PostgreSQL + Caddy HTTPS.
- No local setup required to test the live cloud system.
