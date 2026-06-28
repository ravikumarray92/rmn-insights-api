# Running locally — Local profile (H2, no external services)

Redis, Druid, and Snowflake are replaced by an in-memory H2 database seeded with sample data. No external services needed.

## 1. Build

```bash
mvn clean package -DskipTests
```

## 2. Run

```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

## 3. Verify

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

## 4. Test with pre-built requests

Open `requests-local.http` in IntelliJ or VS Code (REST Client extension).
The file contains a ready-made JWT and requests for both seeded campaigns:

| Campaign | Tenant |
|---|---|
| `camp-spring-sale-2024` | `tenant-walmart-001` |
| `camp-abc-123` | `tenant-walmart-001` |

All six sections are covered: clicks, impressions, click-to-basket, actuator, Swagger, and error scenarios (401 / 403 / 400).

Swagger UI: **`http://localhost:8080/swagger-ui.html`**
