# ConnectSphere — Auth Service

Microservice **#1 of 9** in the ConnectSphere Social Media Mini Platform.

---

## Overview

The Auth Service is the **security and identity foundation** of ConnectSphere.  
Every protected operation across all other microservices is gated through this service's JWT validation.

| Attribute       | Value                          |
|-----------------|--------------------------------|
| Port            | `8080                          |
| Base Package    | `com.connectsphere.auth`       |
| Base Path       | `/api/v1/auth`                 |
| Swagger UI      | http://localhost:8080/swagger-ui.html |
| Health Check    | http://localhost:8080/actuator/health |

---

## Architecture — 5-Layer Pattern

```
AuthResource (REST)          ← HTTP layer, input validation
    │
AuthService (Interface)      ← Business contract
    │
AuthServiceImpl              ← Business logic
    │
UserRepository (Interface)   ← Data-access contract
    │
User (Entity / POJO)         ← Domain model → MySQL table: users
```

Supporting classes:
- `JwtUtils`                  — token generation, parsing, validation
- `JwtAuthFilter`             — per-request Bearer token check
- `UserDetailsServiceImpl`    — Spring Security integration
- `SecurityConfig`            — HTTP security rules, stateless session
- `OAuth2LoginSuccessHandler` — GitHub/Google OAuth2 callback
- `GlobalExceptionHandler`    — structured JSON error responses
- `UserMapper`                — entity → response DTO conversion
- `OpenApiConfig`             — Swagger / OpenAPI 3.0 setup

---

## REST Endpoints

### Public (no token required)

| Method | Path                    | Description                        |
|--------|-------------------------|------------------------------------|
| POST   | `/api/v1/auth/register` | Register with email + password     |
| POST   | `/api/v1/auth/login`    | Login and receive JWT              |
| POST   | `/api/v1/auth/refresh`  | Refresh a token before expiry      |

### Authenticated (Bearer JWT required)

| Method | Path                      | Description                        |
|--------|---------------------------|------------------------------------|
| POST   | `/api/v1/auth/logout`     | Logout (client discards token)     |
| GET    | `/api/v1/auth/profile`    | Get own profile                    |
| PUT    | `/api/v1/auth/profile`    | Update username / bio / avatar     |
| PUT    | `/api/v1/auth/password`   | Change password                    |
| DELETE | `/api/v1/auth/deactivate` | Soft-deactivate account            |
| GET    | `/api/v1/auth/search?q=`  | Search users by name / username    |
| GET    | `/api/v1/auth/users/{id}` | Get any user profile by ID         |

### Admin (ROLE_ADMIN)

| Method | Path                    | Description           |
|--------|-------------------------|-----------------------|
| GET    | `/api/v1/auth/admin/users` | List all users     |

---

## User Entity — Key Fields

| Field          | Type    | Notes                                     |
|----------------|---------|-------------------------------------------|
| userId         | INT PK  | Auto-generated                            |
| username       | VARCHAR | Unique, 3–50 chars                        |
| email          | VARCHAR | Unique, validated                         |
| passwordHash   | VARCHAR | BCrypt (strength 12); null for OAuth users|
| fullName       | VARCHAR | Display name                              |
| bio            | VARCHAR | Up to 300 chars                           |
| profilePicUrl  | VARCHAR | CDN/S3 URL                                |
| role           | VARCHAR | `ROLE_USER` / `ROLE_ADMIN` / `ROLE_MODERATOR` |
| provider       | VARCHAR | `LOCAL` / `GOOGLE` / `GITHUB`             |
| providerId     | VARCHAR | OAuth2 subject ID                         |
| isActive       | BOOLEAN | Soft-delete flag                          |
| createdAt      | DATETIME| Set on insert                             |
| updatedAt      | DATETIME| Set on update via `@PreUpdate`            |

---

## JWT Token

- **Algorithm:** HS256
- **Claims:** `sub` (userId), `email`, `role`
- **Expiry:** 24 hours (configurable via `app.jwt.expiration-ms`)
- **Header:** `Authorization: Bearer <token>`

---

## Running Locally

### Prerequisites
- Java 17+
- Maven 3.9+
- MySQL 8+ running on port 3306

### 1. Start MySQL

```sql
CREATE DATABASE IF NOT EXISTS connectsphere_auth;
```

### 2. Configure credentials

Edit `src/main/resources/application.properties`:
```properties
spring.datasource.username=your_user
spring.datasource.password=your_password
```

### 3. Build and Run

```bash
cd auth-service
mvn clean install
mvn spring-boot:run
```

The service starts on **http://localhost:8081**.  
Swagger UI is at **http://localhost:8081/swagger-ui.html**.

---

## Running Tests

Tests use H2 in-memory database — no MySQL needed.

```bash
mvn test
```

### Test Coverage

| Test Class              | Type        | What it covers                              |
|-------------------------|-------------|---------------------------------------------|
| `AuthServiceImplTest`   | Unit        | All service methods with Mockito mocks      |
| `AuthResourceTest`      | Integration | MockMvc HTTP layer, status codes, JSON body |
| `JwtUtilsTest`          | Unit        | Token generation, parsing, refresh, validation |
| `UserRepositoryTest`    | Integration | DataJpaTest with H2 — all custom queries    |

---

## OAuth2 Setup (Optional)

Add real credentials to `application.properties`:

```properties
# Google
spring.security.oauth2.client.registration.google.client-id=YOUR_ID
spring.security.oauth2.client.registration.google.client-secret=YOUR_SECRET

# GitHub
spring.security.oauth2.client.registration.github.client-id=YOUR_ID
spring.security.oauth2.client.registration.github.client-secret=YOUR_SECRET
```

OAuth2 flow returns a JWT via `OAuth2LoginSuccessHandler` at `/login/oauth2/success`.

---

## Security Notes

- Passwords hashed with **BCrypt (strength 12)**
- JWT tokens expire after **24 hours**
- Deactivated accounts cannot log in
- Admin endpoints protected by `@PreAuthorize("hasRole('ADMIN')")`
- CSRF disabled (stateless REST API)
- HTTPS should be enforced in production via reverse proxy

---

## Next Microservices

| # | Service             | Port  | Domain                          |
|---|---------------------|-------|---------------------------------|
| 1 | **auth-service**    | 8081  | Identity, JWT, OAuth2           |
| 2 | post-service        | 8082  | Posts, feed, visibility         |
| 3 | comment-service     | 8083  | Threaded comments & replies     |
| 4 | like-service        | 8084  | Polymorphic reactions           |
| 5 | follow-service      | 8085  | Social graph, suggestions       |
| 6 | notification-service| 8086  | In-app & email alerts           |
| 7 | media-service       | 8087  | Upload, CDN, stories            |
| 8 | search-service      | 8088  | Hashtags, full-text search      |
| 9 | connectsphere-web   | 8080  | Spring MVC + Thymeleaf          |
