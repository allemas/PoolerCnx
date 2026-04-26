# PoolerCnx

**PoolerCnx** is an educational project that demonstrates the core concepts behind database connection pooling and shows how to build production-grade observability on top of it.

## What Is a Connection Pool?

Opening a new TCP connection to a database is expensive: it involves a TLS handshake, server-side session setup, and network round-trips. A connection pool solves this by pre-creating a set of connections and lending them to the application on demand. When the application finishes a query it returns the connection to the pool rather than closing it, making the connection available for the next caller immediately.

```
Application                  Pool                     Database
    │                          │                           │
    │──── borrow connection ───▶│                           │
    │                          │──── (reuse existing) ────▶│
    │◀─── connection returned ──│                           │
    │                          │                           │
    │──── execute SQL ─────────────────────────────────────▶│
    │◀─── results ──────────────────────────────────────────│
    │                          │                           │
    │──── return connection ───▶│                           │
```

## Key Concepts

| Concept | Description |
|---|---|
| `maximumPoolSize` | Upper bound on connections. Callers that arrive when the pool is full wait in a queue. |
| `minimumIdle` | Connections HikariCP keeps warm even when the application is idle. |
| `connectionTimeout` | Max time (ms) a caller waits before receiving a timeout exception. |
| `idleTimeout` | How long an idle connection is kept before being pruned. |
| `maxLifetime` | Absolute TTL for any connection — keeps server-side state fresh. |
| Pool utilization | `activeConnections / maximumPoolSize`. Sustained values near 1.0 indicate saturation. |

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│  PoolerCnx Application (Spring Boot)                         │
│                                                              │
│  REST API  ──▶  QueryService  ──▶  HikariCP Pool             │
│                     │                    │                   │
│              Micrometer Timers     MXBean gauges             │
│                     │                    │                   │
│              /actuator/prometheus  PoolMetrics               │
└──────────────────────┬───────────────────────────────────────┘
                       │ scrape (10s)
                  Prometheus
                       │
                  Grafana (dashboards)
```

## Quick Start

### Prerequisites

- Docker & Docker Compose

### Run everything

```bash
# 1. Build the application JAR
./mvnw package -DskipTests

# 2. Start PostgreSQL, the app, Prometheus and Grafana
docker compose up --build
```

| Service    | URL                                        |
|------------|--------------------------------------------|
| App API    | <http://localhost:8080>                    |
| Prometheus | <http://localhost:9090>                    |
| Grafana    | <http://localhost:3000> (admin / admin)    |

A pre-built dashboard is automatically provisioned in Grafana under **PoolerCnx → PoolerCnx – Connection Pool Observability**.

## REST API

### Execute a query

```
GET /api/query?sql=SELECT%201
```

Returns timing and the connection identifier used.

### Current pool state

```
GET /api/pool/status
```

Returns `active`, `idle`, `pending`, `total`, `maximumPoolSize`, `minimumIdle`.

### Start a load simulation

```
POST /api/load/start
Content-Type: application/json

{ "concurrency": 8, "holdMs": 2000 }
```

Spawns `concurrency` worker threads each holding a connection for `holdMs` ms.  
Use this to drive pool utilization up and watch the Grafana dashboard react in real time.

### Stop the load simulation

```
POST /api/load/stop
```

### Check whether a load is running

```
GET /api/load/status
```

## Observability

### Prometheus metrics

| Metric | Description |
|--------|-------------|
| `poolercnx_pool_active` | Connections currently in use |
| `poolercnx_pool_idle` | Connections sitting idle |
| `poolercnx_pool_pending` | Threads waiting for a connection |
| `poolercnx_pool_total` | Total connections in the pool |
| `poolercnx_pool_utilization` | `active / maximumPoolSize` (0–1) |
| `poolercnx_query_duration_seconds` | Histogram of query round-trip times |
| `hikaricp_pool_wait_seconds` | HikariCP native: time waiting to acquire |
| `hikaricp_pool_usage_seconds` | HikariCP native: time a connection was held |

All metrics are exposed at `/actuator/prometheus` and scraped by Prometheus every 10 s.

### Health endpoint

```
GET /actuator/health
```

### Alerting example

A Prometheus alerting rule for pool saturation:

```yaml
- alert: ConnectionPoolSaturated
  expr: poolercnx_pool_utilization > 0.85
  for: 1m
  labels:
    severity: warning
  annotations:
    summary: "Connection pool utilization above 85%"
```

## Development

### Run locally (no Docker)

```bash
# Start only PostgreSQL
docker compose up postgres -d

# Run the app
./mvnw spring-boot:run
```

### Run tests

```bash
./mvnw test
```

Tests use an in-memory H2 database — no external dependencies required.

## Project structure

```
PoolerCnx/
├── src/main/java/com/poolercnx/
│   ├── PoolerCnxApplication.java      # Entry point
│   ├── config/DataSourceConfig.java   # HikariCP pool configuration
│   ├── controller/PoolController.java # REST API
│   ├── service/QueryService.java      # Pool usage + load simulation
│   ├── metrics/PoolMetrics.java       # Custom Micrometer gauges
│   └── model/                        # PoolStatus, QueryResult records
├── src/main/resources/application.yml # All pool & observability settings
├── docker/
│   ├── prometheus/prometheus.yml      # Prometheus scrape config
│   └── grafana/provisioning/          # Auto-provisioned Grafana datasource + dashboard
├── docker-compose.yml
├── Dockerfile
└── pom.xml
```
