# Trading Platform

A production-grade crypto trading platform built to demonstrate distributed systems architecture:
event-driven microservices, double-entry financial ledgers, saga-based transactions, and
real-time market data delivery.

> Demo platform. Testnet crypto and mock fiat only — no real funds, no MiCA licence required.

---

## Architecture

```
                        ┌──────────────────────────────────┐
  Web / Mobile ──JWT───►│                                  │
  Partner API ──ApiKey─►│         API Gateway :8080        │
                        │  JWT validation · rate limiting  │
                        │  routing · X-User-Id injection   │
                        └────────────────┬─────────────────┘
                                         │
        ┌────────────────┬───────────────┼───────────────┬────────────────┐
        ▼                ▼               ▼               ▼                ▼
   auth-service    wallet-service   trade-service   order-service   notification-service
      :8081            :8082            :8083           :8084             :8085
   JWT + refresh   double-entry     saga + idem-    matching engine   Kafka → WebSocket
   Redis sessions     ledger          potency        (Redis ZSET)       live push
        │                │               │               │                ▲
        └────────────────┴───────────────┴───────────────┴────────────────┘
                                    Kafka (events)
```

### Design decisions

| Decision | Choice | Reason |
|---|---|---|
| Client auth | JWT (15 min) + refresh token in Redis | Stateless validation at the gateway; refresh token revocable on logout |
| Internal auth | mTLS (planned) | Zero-trust: no implicit trust from network position |
| Service data | One database per service | No shared schema, no cross-service joins, independent evolution |
| Wallet | Double-entry ledger, append-only | Every movement balances; no row is ever mutated or deleted |
| Concurrency | Optimistic locking (`version` column) | Prevents double-spend without holding row locks |
| Trades | Saga with compensating transactions | Distributed consistency without 2PC across services |
| Retries | Idempotency keys on all financial writes | A retried charge must never double-charge |
| Client push | WebSocket (fed by Kafka) | Devices can't consume Kafka; internal fan-out → WebSocket push |
| Event evolution | Expand-contract + schema compatibility | Add fields freely; never break a live consumer |
| Resilience | Bulkhead → Circuit Breaker → Retry → call → Timeout | Layered protection for every outbound dependency |
| Pagination | Cursor-based | O(1) at any depth; stable under concurrent inserts |

---

## Tech Stack

**Backend** — Java 21, Spring Boot 3.4, Spring Cloud Gateway, Spring Security, Spring Data JPA,
Spring Kafka, Resilience4j, Flyway

**Data** — PostgreSQL 16 (ACID: accounts, ledger, orders), Redis 7 (sessions, rate limits, order book),
Kafka (event backbone, KRaft mode)

**Testing** — JUnit 5, Testcontainers (real Postgres in integration tests, never mocked)

**Ops** — Docker multi-stage builds, Docker Compose, GitHub Actions, Actuator + Prometheus metrics

---

## Services

| Service | Port | Responsibility |
|---|---|---|
| api-gateway | 8080 | JWT validation, routing, per-tier rate limiting, `X-User-Id` injection |
| auth-service | 8081 | Registration, login, JWT issuance, refresh token lifecycle |
| wallet-service | 8082 | Double-entry ledger, fiat + crypto balances, transaction history |
| trade-service | 8083 | Buy/sell execution, saga orchestration, idempotency |
| order-service | 8084 | Order book and matching engine |
| notification-service | 8085 | Kafka consumer → WebSocket push to connected clients |

---

## Running Locally

**Prerequisites:** Java 21, Maven 3.9+, Docker with Compose v2

```bash
# 1. Start infrastructure (Postgres, Redis, Kafka, Kafka UI)
cd infrastructure
docker compose up -d

# 2. Build all modules
cd ..
mvn clean install

# 3. Run a service locally
mvn -pl services/auth-service spring-boot:run
```

**Everything in Docker** (builds images for all six services):
```bash
cd infrastructure
docker compose --profile services up --build
```

**Useful endpoints**
```
http://localhost:8080                  API Gateway
http://localhost:8090                  Kafka UI
http://localhost:8081/actuator/health  Service health
http://localhost:8081/swagger-ui.html  API docs
```

---

## Project Layout

```
trading-platform/
├── services/               six Spring Boot modules
├── infrastructure/
│   ├── docker-compose.yml  Postgres · Redis · Kafka · services
│   └── init-db/            per-service database creation
├── docs/
│   ├── adr/                architecture decision records
│   └── diagrams/           C4 model
└── pom.xml                 Maven parent (dependency management)
```

---

## Roadmap

**Phase 1 — MVP**
- [x] Mono-repo scaffolding, Docker Compose, CI
- [ ] Auth service (JWT + refresh tokens)
- [ ] Wallet service (double-entry ledger)
- [ ] Trade service (buy/sell BTC, saga)
- [ ] API Gateway (JWT filter, rate limiting)

**Phase 2 — Real-time & multi-asset**
- [ ] Live price feed (Binance WebSocket → Kafka → WebSocket push)
- [ ] Multiple assets, order book + matching engine
- [ ] Live charts (TimescaleDB)

**Phase 3 — Financial robustness**
- [ ] Event sourcing for the wallet
- [ ] Audit log (Cassandra, append-only)
- [ ] 2FA, email verification

**Phase 4 — Production polish**
- [ ] Multi-region deployment
- [ ] Prometheus + Grafana + distributed tracing
- [ ] Load test at 1,000 concurrent users
- [ ] ADRs for every major decision
