# RMN Insights API

RMN Insights API is a backend service that delivers real-time and historical campaign performance metrics for the Retail Media Network (RMN) streaming platform. It is designed for retail media advertisers who need fast, reliable access to campaign data such as clicks, impressions, and click-to-basket conversions.

The API uses a two-tier data strategy: queries covering the last 72 hours are served from Redis and Apache Druid for sub-second latency, while queries beyond that window fall back to Snowflake for full historical accuracy. This routing is transparent to the caller — the same endpoint handles both cases.

All data access is scoped to the requesting tenant. The tenant identity is extracted from a signed JWT on every request, ensuring one advertiser can never access another's campaign data.

Built with **Spring Boot 3.3.11** · **Java 21** · **Maven**

---

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/v1/ad/{campaignId}/clicks` | Total clicks + unique users |
| GET | `/v1/ad/{campaignId}/impressions` | Total impressions + unique reach |
| GET | `/v1/ad/{campaignId}/clickToBasket` | Click-to-basket count and rate |
| GET | `/actuator/health` | Liveness / readiness check |
| GET | `/actuator/prometheus` | Prometheus scrape endpoint |

### Query parameters (all insight endpoints)

| Parameter | Type | Description |
|-----------|------|-------------|
| `start` | ISO 8601 datetime | Window start (optional; defaults to today 00:00 UTC) |
| `end` | ISO 8601 datetime | Window end (optional; required if `start` is supplied) |
| `timeSeries` | boolean | Include per-hour breakdown (clicks/impressions only) |

### Authentication

All endpoints require `Authorization: Bearer <JWT>` where the JWT payload contains `{"tenant_id": "<id>"}`.
The `tenant_id` is always extracted from the token — never from the request body.

---

## Architecture

```
Client → API Gateway (Kong) → Insights API (Spring Boot)
                                        ↓
                               CampaignService
                              (query routing logic)
                             /                    \
                   ≤ 72h window               > 72h window
                        ↓                          ↓
              RealtimeRepository          HistoricalRepository
             Redis  +  Druid REST          Snowflake JDBC
           (sub-second OLAP)            (historical batch)
```

---

## Prerequisites

| Tool | Version | Required for |
|------|---------|--------------|
| Java | 21+ | Local development |
| Maven | 3.9+ | Local development |
| Docker | 24+ | Container build & run |
| Helm | 3.x | Kubernetes deployment |
| kubectl | 1.28+ | Kubernetes deployment |
| Redis | 7.x | Runtime dependency |
| Apache Druid | 29+ | Runtime dependency |
| Snowflake | account + credentials | Runtime dependency |

---

## Getting started — import the project locally

### 1. Clone the repository

```bash
git clone <repository-url>
cd rmn-insights-api
```

### 2. Install Java 21

The project requires Java 21. Install Amazon Corretto 21 (recommended) or any other JDK 21 distribution.

**macOS (Homebrew):**
```bash
brew install --cask corretto21
```

**Verify:**
```bash
java -version
# openjdk version "21.x.x" ...
```

If you have multiple JDKs installed, point `JAVA_HOME` at JDK 21 explicitly:
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```
Add that line to your `~/.zshrc` or `~/.bashrc` to make it permanent.

### 3. Install Maven 3.9+

```bash
brew install maven   # macOS
# or download from https://maven.apache.org/download.cgi
```

**Verify:**
```bash
mvn -version
# Apache Maven 3.9.x ... Java version: 21
```

### 4. Import into your IDE

**IntelliJ IDEA (recommended):**
1. `File → Open` → select the `rmn-insights-api` folder
2. IntelliJ detects `pom.xml` automatically — click **Open as Project**
3. Wait for the Maven sync to complete (bottom status bar)
4. Set the project SDK: `File → Project Structure → SDK → Java 21`
5. Mark source roots if not auto-detected: `src/main/java` as **Sources**, `src/test/java` as **Tests**

**VS Code:**
1. Install the **Extension Pack for Java** (`vscjava.vscode-java-pack`)
2. `File → Open Folder` → select the `rmn-insights-api` folder
3. VS Code detects `pom.xml` and imports the Maven project automatically
4. Select Java 21 when prompted for the runtime

### 5. Configure environment variables

The application reads credentials from environment variables at startup. Set them in your shell or IDE run configuration.

```bash
export REDIS_URL=redis://localhost:6379
export DRUID_BROKER_URL=http://localhost:8082
export SNOWFLAKE_ACCOUNT=myaccount
export SNOWFLAKE_USER=myuser
export SNOWFLAKE_PASSWORD=mypassword
export JWT_SECRET=changeme
```

**IntelliJ run configuration:**
`Run → Edit Configurations → Environment Variables` — add the above key/value pairs.

**VS Code launch.json:**
```json
{
  "type": "java",
  "env": {
    "REDIS_URL": "redis://localhost:6379",
    "JWT_SECRET": "changeme"
  }
}
```

### 6. Build the project

```bash
mvn clean package -DskipTests
```

A successful build produces `target/rmn-insights-api-0.1.0-SNAPSHOT.jar`.

### 7. Run the tests

```bash
mvn test
```

All tests run without Redis, Druid, or Snowflake — no external services needed.

### 8. Start the application

```bash
mvn spring-boot:run
# or
java -jar target/rmn-insights-api-0.1.0-SNAPSHOT.jar
```

The API starts on **`http://localhost:8080`**.
Verify with:
```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

Swagger UI is available at **`http://localhost:8080/swagger-ui.html`** once the app is running.

---

## Running locally

Two modes are available. See [README-LOCAL.md](README-LOCAL.md) for the recommended quick-start with no external services.

---

### Mode A — Local profile (H2, no external services)

See **[README-LOCAL.md](README-LOCAL.md)** for the full steps.

---

### Mode B — Full local run (real services)

Use this when you need to connect to actual Redis, Druid, and Snowflake.

**1. Set environment variables**

```bash
export REDIS_URL=redis://localhost:6379
export DRUID_BROKER_URL=http://localhost:8082
export SNOWFLAKE_ACCOUNT=myaccount
export SNOWFLAKE_USER=myuser
export SNOWFLAKE_PASSWORD=mypassword
export JWT_SECRET=changeme
```

**2. Build**

```bash
mvn clean package -DskipTests
```

**3. Run**

```bash
mvn spring-boot:run
```

**4. Verify**

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

Use `requests.http` for ready-made request examples against real data.

---

Swagger UI is available at **`http://localhost:8080/swagger-ui.html`** in both modes.

---

## Running with Docker

### 1. Build the image

```bash
docker build -t rmn-insights-api:latest .
```

### 2. Run the container

Replace the placeholder values with your actual credentials before running.

```bash
docker run -p 8080:8080 \
  -e REDIS_URL=redis://host.docker.internal:6379 \
  -e DRUID_BROKER_URL=http://host.docker.internal:8082 \
  -e SNOWFLAKE_ACCOUNT=myaccount \
  -e SNOWFLAKE_USER=myuser \
  -e SNOWFLAKE_PASSWORD=mypassword \
  -e JWT_SECRET=changeme \
  rmn-insights-api:latest
```

> `host.docker.internal` resolves to the host machine from inside a Docker container on Mac and Windows.
> On Linux, use `--network host` or the host's actual IP instead.

The API starts on `http://localhost:8080`.

---

## Deploying to Kubernetes with Helm

### 1. Build and push the Docker image to your registry

```bash
docker build -t <your-registry>/rmn-insights-api:1.0.0 .
docker push <your-registry>/rmn-insights-api:1.0.0
```

### 2. Create a secrets override file

Create a file called `secrets.values.yaml` **outside the repo** (or add it to `.gitignore`).
Never commit real credentials to source control.

```yaml
# secrets.values.yaml  — gitignored, never committed
secrets:
  SNOWFLAKE_ACCOUNT: myaccount
  SNOWFLAKE_USER: myuser
  SNOWFLAKE_PASSWORD: mypassword
  JWT_SECRET: my-production-secret
```

### 3. Install the Helm chart

```bash
helm install rmn-insights-api ./helm \
  --set image.repository=<your-registry>/rmn-insights-api \
  --set image.tag=1.0.0 \
  --set env.REDIS_URL=redis://my-redis:6379 \
  --set env.DRUID_BROKER_URL=http://my-druid:8082 \
  -f secrets.values.yaml
```

### 4. Verify the deployment

```bash
# Check pods are Running
kubectl get pods -l app.kubernetes.io/name=rmn-insights-api

# Check the service
kubectl get svc rmn-insights-api

# Tail application logs
kubectl logs -l app.kubernetes.io/name=rmn-insights-api --follow
```

### 5. Confirm the health endpoint responds

```bash
# Port-forward the service to your local machine
kubectl port-forward svc/rmn-insights-api 8080:80

# In a second terminal
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{ "status": "UP" }
```

### 6. Generate a test JWT for the API

```bash
pip install pyjwt
python3 - <<'EOF'
import jwt, time
token = jwt.encode(
  {"tenant_id": "tenant-001", "sub": "test-user", "exp": int(time.time()) + 3600},
  "changeme",
  algorithm="HS256"
)
print(token)
EOF
```

Use the printed token in the `Authorization: Bearer <token>` header for all API requests.
See `requests.http` for ready-made request examples.

### Upgrading

```bash
# Deploy a new image tag
helm upgrade rmn-insights-api ./helm --reuse-values --set image.tag=1.1.0
```

### Enabling autoscaling

```bash
helm upgrade rmn-insights-api ./helm --reuse-values \
  --set autoscaling.enabled=true \
  --set autoscaling.minReplicas=2 \
  --set autoscaling.maxReplicas=10
```

---

## API Documentation (Swagger UI)

Swagger UI is available automatically when the application is running. No extra setup is needed.

| Resource | URL |
|---|---|
| Interactive UI | `http://localhost:8080/swagger-ui.html` |
| Raw OpenAPI JSON | `http://localhost:8080/v3/api-docs` |

Both URLs are publicly accessible — no token is required to open the docs page itself.

### Authorising requests inside Swagger UI

1. Generate a test token (see step 6 in the Kubernetes section, or the snippet below)
2. Open `http://localhost:8080/swagger-ui.html`
3. Click the **Authorize** button (top right)
4. Enter `<your-token>` in the **bearerAuth** field and click **Authorize**
5. All subsequent "Try it out" calls will include `Authorization: Bearer <token>`

### Generate a test token

```bash
pip install pyjwt

python3 - <<'EOF'
import jwt, time
token = jwt.encode(
  {"tenant_id": "tenant-001", "sub": "test-user", "exp": int(time.time()) + 3600},
  "changeme",
  algorithm="HS256"
)
print(token)
EOF
```

> The default `JWT_SECRET` is `changeme` (set in `application.yml`). Use the same value here unless you have overridden it.

### Disabling Swagger in production

Add the following to your production environment or Helm values override:

```yaml
# values-prod.yaml
env:
  SPRINGDOC_API_DOCS_ENABLED: "false"
  SPRINGDOC_SWAGGER_UI_ENABLED: "false"
```

Or via environment variable when running locally:

```bash
SPRINGDOC_API_DOCS_ENABLED=false mvn spring-boot:run
```

---

## Running tests

```bash
mvn test
```

Tests use `@WebMvcTest` (controller slice) and `@ExtendWith(MockitoExtension.class)` (service unit tests).
No external services (Redis, Druid, Snowflake) are required to run the test suite.

---

## Configuration reference

All properties live under the `rmn.*` namespace in `application.yml` and can be overridden via environment variables.

| Property | Env var | Default | Description |
|----------|---------|---------|-------------|
| `rmn.druid.broker-url` | `DRUID_BROKER_URL` | `http://localhost:8082` | Druid Broker REST endpoint |
| `rmn.druid.query-timeout-ms` | `DRUID_QUERY_TIMEOUT_MS` | `5000` | HTTP connect timeout (ms) |
| `rmn.snowflake.account` | `SNOWFLAKE_ACCOUNT` | — | Snowflake account identifier |
| `rmn.snowflake.user` | `SNOWFLAKE_USER` | — | Snowflake username |
| `rmn.snowflake.password` | `SNOWFLAKE_PASSWORD` | — | Snowflake password |
| `rmn.snowflake.warehouse` | `SNOWFLAKE_WAREHOUSE` | `INSIGHTS_WH` | Snowflake warehouse |
| `rmn.snowflake.database` | `SNOWFLAKE_DATABASE` | `RMN` | Snowflake database |
| `rmn.snowflake.schema` | `SNOWFLAKE_SCHEMA` | `ANALYTICS` | Snowflake schema |
| `rmn.realtime-window-hours` | `REALTIME_WINDOW_HOURS` | `72` | Queries ≤ this window use Druid; beyond uses Snowflake |
| `rmn.attribution-window-minutes` | — | `30` | Post-click attribution window for click-to-basket |
| `rmn.jwt.secret` | `JWT_SECRET` | `changeme` | HMAC-256 secret for JWT validation |
| `spring.data.redis.url` | `REDIS_URL` | `redis://localhost:6379` | Redis connection URL |
| `springdoc.api-docs.path` | — | `/v3/api-docs` | Path for raw OpenAPI JSON |
| `springdoc.swagger-ui.path` | — | `/swagger-ui.html` | Path for Swagger UI |
| `springdoc.api-docs.enabled` | `SPRINGDOC_API_DOCS_ENABLED` | `true` | Set to `false` to disable in production |
| `springdoc.swagger-ui.enabled` | `SPRINGDOC_SWAGGER_UI_ENABLED` | `true` | Set to `false` to disable in production |

---

## Project structure

```
src/
├── main/
│   ├── java/rmn/insights/
│   │   ├── RmnInsightsApplication.java          Spring Boot entry point
│   │   ├── config/
│   │   │   ├── AppProperties.java               @ConfigurationProperties(prefix="rmn")
│   │   │   ├── SecurityConfig.java              Spring Security — stateless JWT, CSRF disabled
│   │   │   ├── RedisConfig.java                 StringRedisTemplate bean (@Profile "!local")
│   │   │   ├── DruidRestClientConfig.java       RestClient bean with JDK HttpClient timeout (@Profile "!local")
│   │   │   ├── SnowflakeDataSourceConfig.java   DataSource + NamedParameterJdbcTemplate (@Profile "!local")
│   │   │   └── OpenApiConfig.java               Swagger UI — API info + JWT Bearer security scheme
│   │   ├── security/
│   │   │   └── JwtAuthenticationFilter.java     OncePerRequestFilter — extracts tenant_id from JWT
│   │   ├── controller/
│   │   │   └── CampaignController.java          REST endpoints, @Observed tracing
│   │   ├── service/
│   │   │   └── CampaignService.java             Query routing, ownership check, business logic
│   │   ├── repository/
│   │   │   ├── ICampaignRepository.java         Ownership check interface
│   │   │   ├── IHistoricalRepository.java       Historical query interface
│   │   │   ├── IRealtimeRepository.java         Realtime query interface
│   │   │   ├── CampaignRepository.java          Snowflake — campaign ownership check (@Profile "!local")
│   │   │   ├── HistoricalRepository.java        Snowflake JDBC queries (@Profile "!local")
│   │   │   ├── RealtimeRepository.java          Redis HLL + Druid HTTP/JSON (@Profile "!local")
│   │   │   ├── H2CampaignRepository.java        H2 — campaign ownership check (@Profile "local")
│   │   │   ├── H2HistoricalRepository.java      H2 JDBC historical queries (@Profile "local")
│   │   │   └── H2RealtimeRepository.java        H2 JDBC realtime queries (@Profile "local")
│   │   ├── dto/
│   │   │   ├── TimeRange.java                   Domain record (validates end > start)
│   │   │   ├── TimeRangeDto.java                Response-only serialisation record
│   │   │   ├── MetricType.java                  Enum with SQL-safe columnName field
│   │   │   ├── MetricPoint.java                 Time-series data point record
│   │   │   ├── AggregationResult.java           Internal query result carrier
│   │   │   ├── ClicksResponse.java
│   │   │   ├── ImpressionsResponse.java
│   │   │   ├── ClickToBasketResponse.java
│   │   │   └── ErrorResponse.java
│   │   └── exception/
│   │       └── GlobalExceptionHandler.java      @RestControllerAdvice
│   └── resources/
│       ├── application.yml                      Base configuration (all profiles)
│       ├── application-local.yml                Local profile — H2 datasource, disables Redis autoconfigure
│       ├── schema-h2.sql                        H2 DDL — campaigns + campaign_metrics_hourly tables
│       ├── data-h2.sql                          H2 seed data for local development
│       └── log4j2-spring.xml                    Log4j2 configuration (DEBUG in local, INFO elsewhere)
└── test/
    └── java/rmn/insights/
        ├── controller/CampaignControllerTest.java   MockMvc + @WebMvcTest
        └── service/CampaignServiceTest.java         Mockito unit tests
```

---

## Observability

| Concern | Tool | Endpoint / config |
|---------|------|-------------------|
| Metrics | Micrometer + Prometheus | `GET /actuator/prometheus` |
| Distributed tracing | Micrometer Tracing + OpenTelemetry | OTLP exporter via `management.otlp.*` |
| Health check | Spring Boot Actuator | `GET /actuator/health` |

Service methods are annotated with `@Observed` for automatic span creation.
Set `management.tracing.sampling.probability=1.0` (default) for 100% trace sampling in development.

---

