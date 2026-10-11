# MoveInSync Vendor & Driver Settlement Engine
> Case Study 4: Corporate transport month-end financial settlement, invoice reconciliation, and dispute delta engine.

**Author:** Deepanshu

---

## Tech Stack
- **Language:** Java 21 LTS
- **Framework:** Spring Boot 3.4.x
- **Database:** PostgreSQL 16
- **Migrations:** Flyway
- **Security:** Spring Security (Stateless JWT)
- **API Docs:** OpenAPI 3.1 (Swagger UI)

---

## Quick Start

### Prerequisites
- JDK 21
- Maven 3.9+
- PostgreSQL 16 (database: `settlement`, user: `settlement`, password: `settlement_dev`)

### Run Tests
```bash
mvn clean test
```

### Run Locally
```bash
mvn spring-boot:run
```
The application starts on port `8081`.

### Run with Docker Compose
```bash
docker compose up -d
```

---

## API Documentation & Verification

- **Swagger UI:** [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)
- **OpenAPI Spec:** [http://localhost:8081/v3/api-docs](http://localhost:8081/v3/api-docs)
- **Automated Demo Script:**
  ```bash
  ./scripts/demo_walkthrough.sh
  ```
