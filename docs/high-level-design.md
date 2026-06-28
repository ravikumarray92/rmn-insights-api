# High-Level Design: Retail Media Network (RMN) Real-Time Streaming Platform

**Version:** 1.0
**Date:** 2026-06-27
**Status:** COMPLETED

> **Architecture Diagram:** [`rmn-architecture.drawio`](https://github.com/ravikumarray92/rmn-insights-api/blob/main/docs/Retail%20Media%20Network%20(RMN)%20%E2%80%94%20Real-Time%20Streaming%20Platform-Final.jpg)\
> Open with [app.diagrams.net](https://app.diagrams.net)\
> Design Document - https://drive.google.com/file/d/1MxDKi2qShK8xCw52mmDsRSPDVWZDCrKH/view?usp=sharing

---

## 1. Purpose

This document describes the high-level design of a real-time streaming platform for a Retail Media Network (RMN). The platform ingests customer interaction events (product views, ad impressions, clicks, add-to-cart) from retailer websites, processes them in real time, and exposes the resulting insights via APIs to enable marketers and retailers to optimize advertising campaigns and improve user engagement.

---

## 2. Goals and Non-Goals

### Goals

- Ingest high-velocity event streams from multiple retailer sources with sub-second latency.
- Process events in real time, including stateful attribution (click → cart, click → purchase).
- Store raw events and pre-aggregated metrics across hot, warm, and cold storage tiers.
- Expose low-latency REST APIs for campaign insights (clicks, impressions, click-to-basket).
- Support multiple retailers (tenants) on a single platform with strict data isolation.
- Scale horizontally to handle peak traffic events (e.g., Black Friday, Prime Day).

### Non-Goals

- Real-time bidding (RTB) or ad serving — this platform is analytics only.
- User identity resolution or cross-device graph — assumed to be a separate upstream service.
- Billing and invoicing for advertisers.

---

## 3. System Context

```
┌──────────────────────────────────────────────────────────────┐
│                      External Systems                        │
│                                                              │
│   Retailer Website    Mobile App    Third-Party Ad Server    │
│   (JS Pixel SDK)      (iOS/Android) (Server-Side Events)     │
└────────────┬─────────────────┬──────────────────┬────────────┘
             │                 │                  │
             ▼                 ▼                  ▼
┌──────────────────────────────────────────────────────────────┐
│              RMN Real-Time Streaming Platform                │
│                                                              │
│             Ingest → Process → Store → Serve                 │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│                      Consumers                               │
│                                                              │
│    Marketer Dashboards  Retailer Portals    BI / ML Tools    │
└──────────────────────────────────────────────────────────────┘
```

---

## 4. Architecture Overview

> **See the draw.io diagram for the full visual:** [`rmn-architecture.drawio`](Design Document - https://drive.google.com/file/d/1MxDKi2qShK8xCw52mmDsRSPDVWZDCrKH/view?usp=sharing)

The platform is organized into **six horizontal tiers**. Data flows top to bottom: client → ingestion → processing → storage → API → consumers, with observability as a cross-cutting layer.

| Tier | Components                                                                        | Primary Role |
|---|-----------------------------------------------------------------------------------|---|
| **1 — Client** | Web Pixel (JS SDK), Mobile SDK, Server-Side Events                                | Emit raw interaction events over HTTPS |
| **2 — Ingestion** | Envoy Edge Collectors (3 regions), Apache Kafka                                   | Buffer, fan-in, and durably queue events at high throughput |
| **3 — Processing** | Apache Flink (3 jobs: Enrichment, Attribution, Aggregation)                       | Validate, enrich, attribute, and aggregate events in real time |
| **4 — Storage** | Redis + Cassandra (hot), Druid + S3/Iceberg (warm), Snowflake + S3 Glacier (cold) | Tiered storage by query latency and retention need |
| **5 — API** | Kong API Gateway, SpringBoot API Insights Service                                 | Serve campaign metrics; route queries to the correct storage tier |
| **6 — Observability** | Prometheus, Grafana, OpenTelemetry/Jaeger, ELK, PagerDuty, Kafka Burrow           | Platform-wide metrics, tracing, logs, and alerting |

### Data flow summary

```
Client SDKs
    │ HTTPS (batched)
    ▼
Edge Collectors (Envoy, 3 regions)
    │ Produce — partition key: hash(tenantID + campaignID)
    ▼
Apache Kafka  [rmn.ad.impressions · rmn.ad.clicks · rmn.product.views · rmn.cart.add · rmn.purchases]
    │ Consumer group
    ▼
Flink Job 1 (Enrichment) ──enriched stream──▶ Flink Job 2 (Attribution) ──attributed stream──▶ Flink Job 3 (Aggregations)
    │ Raw events sink                                                                                  │ Counter / rollup sinks
    ▼                                                                                                  ▼
Cassandra                                                             Redis (hot) · Druid (warm) · Snowflake (cold) · S3 Glacier (archive)
                                                                                    │
                                                                                    ▼
                                                     Kong API Gateway ──▶ Insights API (SpringBoot API)
                                                                                    │ JSON / REST
                                                                                    ▼
                                                              Marketer Dashboards · Retailer Portals · BI / ML Tools
```

---

## 5. Component Descriptions

### 5.1 Edge Collectors

Lightweight Envoy proxies deployed in multiple regions. Responsibilities:
- Accept HTTP/HTTPS event payloads from client SDKs.
- Buffer and batch events to reduce Kafka producer round-trips.
- Apply IP-based rate limiting to prevent abuse.
- Forward to the nearest Kafka cluster via regional producers.

### 5.2 Apache Kafka

The central event bus. Key configuration decisions:

| Parameter | Value | Rationale |
|---|---|---|
| Partition key | `hash(tenantID + campaignID)` | Co-locates a campaign's events on the same partition for ordered processing |
| Replication factor | 3 | Tolerates loss of 2 brokers without data loss |
| Retention | 7 days | Allows replay for reprocessing after bugs |
| Compression | LZ4 | Best throughput/CPU trade-off for small JSON events |
| `acks` | `all` | Ensures no data loss on producer side |

### 5.3 Apache Flink

Three independent Flink jobs consume from Kafka and write to storage sinks.

**Job 1 — Enrichment & Validation**
- Deserializes and schema-validates every event against an Avro registry.
- Masks or drops PII fields (email, raw IP) before downstream processing.
- Routes malformed events to a dead-letter Kafka topic for alerting and replay.

**Job 2 — Attribution Engine**
- Keyed state per `(userID, tenantID)` tracks the most recent ad click.
- A 30-minute event-time session window fires a click-to-basket signal when a `cart.add` event follows a `ad.click` within the window.
- A 7-day window tracks click-to-purchase.
- State is backed by RocksDB with incremental checkpoints to S3 every 60 seconds (recovery point objective ≤ 60s).

**Job 3 — Aggregations**
- Tumbling 1-minute windows produce per-campaign counters written to Redis.
- Hourly windows produce rollups written to Druid and Cassandra.
- HyperLogLog sketches are merged across windows for approximate unique user counts (±0.81% error).

### 5.4 Storage Tiers

| Tier | Technology | Query Latency | Use Case |
|---|---|---|---|
| Hot | Redis Cluster | < 1ms | Live counters for real-time dashboards |
| Hot | Cassandra | < 10ms | Raw event lookup by eventID or userID |
| Warm | Apache Druid | < 1s | OLAP aggregations, time-series drill-down up to 72h |
| Cold | Snowflake | 2–30s | Historical reporting, ML feature extraction |
| Archive | S3 + Iceberg | minutes | Cost-optimized long-term storage, schema evolution |

### 5.5 Insights API

A Spring Boot or Go service that routes queries to the appropriate storage tier based on the requested time window. See Section 7 for endpoint specifications.

---

## 6. Multi-Tenancy Design

Every component enforces tenant isolation:

| Layer | Isolation Mechanism |
|---|---|
| Kafka | Partition key includes `tenantID`; large tenants get dedicated topic partitions |
| Flink | Keyed streams ensure state never crosses tenant boundaries |
| Cassandra | `tenant_id` is the leading column in every partition key |
| Druid | Queries always filter on `tenant_id` dimension; row-level security on datasource |
| Snowflake | Row-level access policies; separate schema per tenant for large customers |
| API Gateway | Kong extracts `tenant_id` from JWT and injects as a trusted header; API layer rejects any request where the JWT tenant does not own the requested `campaignID` |
| Kubernetes | Namespace-level resource quotas per tenant; large tenants can request dedicated Flink job slots |

---

## 7. API Design

All endpoints require `Authorization: Bearer <JWT>` where the token contains `{"tenant_id": "<id>"}`. The `tenant_id` from the token — never from the request body — scopes every query.

### 7.1 GET /v1/ad/{campaignID}/clicks

Returns the number of users who clicked the ad.

**Query parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `start` | ISO 8601 | No | Window start. Defaults to today 00:00 UTC |
| `end` | ISO 8601 | No | Window end. Required if `start` is supplied |
| `time_series` | bool | No | Include per-hour breakdown |

**Response:**
```json
{
  "campaign_id": "camp-spring-sale-2026",
  "tenant_id": "tenant-walmart-001",
  "total_clicks": 42000,
  "unique_users": 38500,
  "time_series": null,
  "query_window": { "start": "...", "end": "..." },
  "data_freshness_seconds": 60
}
```

### 7.2 GET /v1/ad/{campaignID}/impressions

Returns the number of times the ad was viewed and the deduplicated unique reach.

**Response:**
```json
{
  "campaign_id": "camp-spring-sale-2026",
  "tenant_id": "tenant-walmart-001",
  "total_impressions": 1200000,
  "unique_reach": 980000,
  "time_series": null,
  "query_window": { "start": "...", "end": "..." },
  "data_freshness_seconds": 60
}
```

### 7.3 GET /v1/ad/{campaignID}/clickToBasket

Returns the count and rate of users who added a product to their cart within 30 minutes of clicking the ad.

**Response:**
```json
{
  "campaign_id": "camp-spring-sale-2024",
  "tenant_id": "tenant-walmart-001",
  "click_to_basket_count": 8400,
  "click_to_basket_rate": 0.2,
  "attribution_window_minutes": 30,
  "query_window": { "start": "...", "end": "..." },
  "data_freshness_seconds": 60
}
```

### 7.4 Query Routing

```
Request time range ≤ 72 hours  →  Redis (live) + Druid (OLAP)   → data_freshness ≤ 60s
Request time range  > 72 hours  →  Snowflake (historical)        → data_freshness ≤ 3600s
```

The 72-hour threshold aligns with the Druid hot-segment retention window and can be adjusted via configuration without an API change.

---

## 8. Scalability Strategy

### Horizontal scaling

| Component       | Scaling Mechanism                                                  |
|-----------------|--------------------------------------------------------------------|
| Edge Collectors | Kubernetes Horizontal Pod Autoscaler (HPA) on inbound request rate |
| Kafka           | Add brokers; rebalance partitions via Cruise Control               |
| Apache Flink    | Increase task slots; KEDA-based autoscaler watches consumer lag    |
| Redis           | Add shards (cluster mode); read replicas per availability zone     |
| Apache Druid    | Add Historical nodes; Middle Manager nodes for indexing            |
| API Service     | Kubernetes HPA on CPU and RPS                                      |

### Peak traffic (e.g., Black Friday)

- Kafka partition count is pre-scaled 2 weeks prior (partition count increases are one-way).
- Flink jobs are pre-warmed with higher parallelism the night before.
- Snowflake warehouse size is scaled up for the event window and reverted after.
- Redis keyspace is pre-populated with zero-value counters to avoid cold-start amplification.

### Load balancing

- API tier: round-robin via Kong across API pods; sticky sessions not required (stateless).
- Kafka: producer-side partition assignment by `hash(tenantID + campaignID)` ensures even spread and locality.
- Druid: Broker layer distributes queries across Historical nodes; queries are scatter-gathered.

---

## 9. Observability

### Key metrics

| Metric | Source | Alert Threshold |
|---|---|---|
| Kafka consumer lag | Burrow → Prometheus | > 10,000 msgs/partition for > 2min |
| Flink checkpoint duration | Flink metrics → Prometheus | > 30s |
| Flink restart rate | Flink metrics | > 1 restart/min |
| API P99 latency | Prometheus histogram | > 200ms |
| API error rate (5xx) | Prometheus counter | > 0.1% of requests |
| Cassandra read P99 | JMX exporter | > 50ms |
| Redis hit rate | Redis INFO | < 95% |
| Dead-letter queue depth | Kafka topic offset | > 0 for 5 continuous minutes |
| Druid query timeout rate | Druid metrics | > 1% |

### Tooling

| Concern | Tool                                   |
|---|----------------------------------------|
| Metrics collection | Prometheus                             |
| Dashboards | Grafana                                |
| Distributed tracing | OpenTelemetry → Jaeger                 |
| Log aggregation | Elasticsearch + Kibana (ELK)           |
| Alerting / on-call | PagerDuty (routed from Grafana alerts) |
| Synthetic monitoring | Locust load tests on staging           |

---

## 10. Data Retention Strategy

| Data Type | Store | Retention | Rationale |
|---|---|---|---|
| Raw events | Cassandra | 7 days | Fast lookup for debugging; beyond 7d use S3 |
| Raw events (archive) | S3 + Iceberg | 2 years | Replay capability, regulatory compliance |
| Hourly aggregates | Druid (hot segments) | 72 hours | Sub-second API queries |
| Hourly aggregates | Druid (deep storage on S3) | 90 days | Cost-effective warm access |
| Historical aggregates | Snowflake | 2+ years | Reporting, ML training data |
| Long-term archive | S3 Glacier | 7 years | Compliance / audit requirements |
| Kafka topic logs | Kafka | 7 days | Event replay after processing bugs |

---

## 11. Key Challenges and Trade-offs

### Real-time accuracy vs. latency

Flink uses event-time processing with a 2-minute allowed lateness. Events arriving more than 2 minutes late are routed to a side-output for manual reconciliation. This bounds state size while accepting a small undercount in the most recent 2-minute window — a deliberate trade-off for operational simplicity.

### Exactly-once semantics

Kafka + Flink checkpointing + idempotent Cassandra writes (by event UUID) achieve end-to-end exactly-once. This is expensive: checkpoint intervals add ~200ms of overhead per Flink operator. Under extreme latency pressure, teams may switch to at-least-once with deduplication downstream, accepting occasional double-counting.

### Unique user counting

Exact distinct-count queries (e.g., `COUNT(DISTINCT userID)`) over billions of events are prohibitively expensive. HyperLogLog in Redis and Druid gives ±0.81% error at any scale with constant memory. This is disclosed to API consumers via the `data_freshness_seconds` field and API documentation.

### Multi-tenant resource contention

A large tenant running a viral campaign can saturate shared Kafka partitions or Flink task slots. Mitigations: per-tenant Kafka partition quotas, Flink job-level resource profiles, and Kubernetes namespace quotas. Very large tenants (e.g., top 5 retailers) should receive dedicated Flink job clusters.

### Cost management

| Lever | Action |
|---|---|
| Snowflake | Auto-suspend warehouse after 5 minutes idle; use clustering keys to minimize scan |
| Druid | Only last 72h on NVMe SSDs; older segments on S3-backed deep storage |
| Kafka | LZ4 compression reduces storage ~60%; tiered storage offloads old segments to S3 |
| Redis | Key TTL of 24h; eviction policy `allkeys-lru` for graceful degradation |
| Flink | Spot/preemptible instances for non-checkpointing workers; on-demand only for state-heavy jobs |

---

## 12. Technology Summary

| Category | Technology             | Version |
|---|------------------------|---|
| Event bus | Apache Kafka           | 3.7+ |
| Stream processing | Apache Flink           | 1.19+ |
| Hot store (counters) | Redis Cluster          | 7.2+ |
| Hot store (raw events) | Apache Cassandra       | 4.1+ |
| Warm store (OLAP) | Apache Druid           | 29+ |
| Cold store | Snowflake              | Current |
| Data lake format | Apache Iceberg on S3   | 1.5+ |
| API framework | Spring Boot / Go       | 0.111+ |
| API gateway | Kong                   | 3.7+ |
| Container platform | Kubernetes + Helm      | 1.30+ |
| Metrics | Prometheus + Grafana   | Current |
| Tracing | OpenTelemetry + Jaeger | Current |
| Logging | Elasticsearch + Kibana | 8.x |
| Alerting | PagerDuty              | — |

---

## 13. Deployment Overview

```
┌──────────────────────────────────────────────────────────────────────────┐
│  Kubernetes Cluster (multi-region: us-east-1, us-west-2, eu-west-1)      │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  Namespace: rmn-ingestion                                          │  │
│  │    Deployment: edge-collector   (HPA: 3–50 pods, scale on RPS)     │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  Namespace: rmn-kafka                                              │  │
│  │    StatefulSet: kafka-broker     (3 brokers, persistent volumes)   │  │
│  │    StatefulSet: zookeeper        (3 nodes)                         │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  Namespace: rmn-processing                                         │  │
│  │    Deployment: flink-jobmanager  (1 pod, HA via ZK leader elect)   │  │
│  │    Deployment: flink-taskmanager (HPA: 4–100 pods, scale on lag)   │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  Namespace: rmn-storage                                            │  │
│  │    StatefulSet: cassandra        (6 nodes, 3 per AZ)               │  │
│  │    StatefulSet: redis-cluster    (6 nodes, 3 primary + 3 replica)  │  │
│  │    StatefulSet: druid-historical (3 nodes, NVMe SSDs)              │  │
│  │    Deployment:  druid-broker     (2 pods)                          │  │
│  │    Deployment:  druid-coordinator (1 pod)                          │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  Namespace: rmn-api                                                │  │
│  │    Deployment: insights-api      (HPA: 2–20 pods, scale on CPU)    │  │
│  │    Deployment: kong-gateway      (2 pods, active-passive)          │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  Namespace: rmn-observability                                      │  │
│  │    Deployment: prometheus        Deployment: grafana               │  │
│  │    Deployment: jaeger-collector  Deployment: elasticsearch         │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  External (managed services):                                            │
│    Snowflake (SaaS)  ·  S3 + Glacier (object store)  ·  PagerDuty (SaaS) │
└──────────────────────────────────────────────────────────────────────────┘
```

---

*End of document*
