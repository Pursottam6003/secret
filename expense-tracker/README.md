# SplitEase — Expense Tracker

A full-stack expense splitting application (Splitwise-style) built with Spring Boot 3 + Thymeleaf.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.2, Java 17 |
| Database | PostgreSQL (prod) / H2 (dev) |
| Cache | Redis (Spring Cache + Lettuce) |
| Messaging | Apache Kafka |
| Real-time | WebSocket (STOMP via SockJS) |
| Security | Spring Security + JWT (jjwt 0.12) |
| ORM | Spring Data JPA / Hibernate |
| Frontend | Thymeleaf + Bootstrap 5 + Chart.js |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Build | Maven |

---

## Quick Start (Dev — no external infra needed)

```bash
cd expense-tracker
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

- App runs at: http://localhost:8080
- H2 Console:  http://localhost:8080/h2-console
- Swagger UI:  http://localhost:8080/swagger-ui.html

**Demo credentials** (auto-seeded in dev):
```
alice@demo.com / password
bob@demo.com   / password
carol@demo.com / password
```

---

## Running with Full Stack (PostgreSQL + Redis + Kafka)

### Prerequisites
- Docker Desktop

```bash
# Start external services
docker-compose up -d

# Run app with prod profile
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

### docker-compose.yml (create this)

```yaml
version: '3.9'
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: expensedb
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  kafka:
    image: confluentinc/cp-kafka:7.6.0
    depends_on: [zookeeper]
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    ports:
      - "9092:9092"
```

---

## API Reference

### Auth
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login, get JWT |
| POST | `/api/auth/refresh?refreshToken=...` | Refresh access token |

### Groups
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/groups` | List my groups |
| POST | `/api/groups` | Create group |
| GET | `/api/groups/{id}` | Get group detail |
| GET | `/api/groups/{id}/members` | List members |
| POST | `/api/groups/{id}/members?email=...` | Add member by email |
| DELETE | `/api/groups/{id}/members/{userId}` | Remove member |
| DELETE | `/api/groups/{id}` | Soft-delete group |

### Expenses
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/expenses` | Create expense (EQUAL/EXACT/PERCENTAGE) |
| GET | `/api/expenses/group/{id}` | All group expenses |
| GET | `/api/expenses/group/{id}/unsettled` | Only unsettled |
| GET | `/api/expenses/{id}` | Single expense |
| DELETE | `/api/expenses/{id}` | Delete expense (payer only) |

### Settlements
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/settlements/group/{id}/balances` | Net balances + optimal plan |
| POST | `/api/settlements` | Record a payment |
| GET | `/api/settlements/group/{id}` | Settlement history |
| PATCH | `/api/settlements/{id}/cancel` | Cancel settlement |

### Analytics
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/analytics/group/{id}/daily?from=&to=` | Daily totals |
| GET | `/api/analytics/group/{id}/weekly?year=` | Weekly totals |
| GET | `/api/analytics/group/{id}/monthly?year=` | Monthly totals |
| GET | `/api/analytics/group/{id}/categories` | Category breakdown |
| GET | `/api/analytics/group/{id}/summary` | Today/week/month summary |

---

## Settlement Algorithm

The **Minimize Cash Flow** algorithm (same approach as Splitwise):

1. Calculate each member's **net balance**:
   - `+amount` for every expense they paid
   - `-split_amount` for every expense they owe
   - Completed settlements adjust balances accordingly

2. Separate members into **creditors** (owed money) and **debtors** (owe money)

3. Use a **greedy max-heap approach**:
   - Match largest creditor with largest debtor each iteration
   - Transfer `min(creditor balance, debtor balance)`
   - Push remainders back into heaps

This produces the **minimum number of transactions** to fully settle the group.
Time complexity: O(n log n)

---

## AI Integration (Roadmap)

The codebase is prepared for AI features. Add these to complete:

### AI Expense Bot
```
Dependency: spring-ai-openai-spring-boot-starter
```
- Parse natural language: "I paid $45 for pizza last night"
- Auto-categorize expenses using GPT
- Voice input via Web Speech API → text → AI parser

### AI Insights
- Spending anomaly detection (compare vs personal average)
- Budget recommendations per category
- "Who tends to overspend" group insights
- Predictive monthly spend forecast

### Required additions:
```yaml
# application.yml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
```

```java
// AiInsightService.java
@Service
public class AiInsightService {
    private final ChatClient chatClient;
    // Inject expense history → generate insights via GPT
}
```

---

## Improvement Roadmap

### Performance
- [ ] **Redis caching** — balance calculations, user groups (already wired, needs Redis running)
- [ ] **Kafka** — async notifications, audit log, balance recalculation events
- [ ] **DB indexing** — already added on hot query columns
- [ ] **Pagination** — add `Pageable` to expense list endpoints for large groups
- [ ] **Connection pooling** — HikariCP configured (prod profile)

### Features
- [ ] **Multi-currency** with live FX rates (ExchangeRate API / Open Exchange Rates)
- [ ] **Email notifications** (Spring Mail + Kafka consumer)
- [ ] **Receipt scanning** (AWS Textract / Google Vision API)
- [ ] **Recurring expenses** (Spring `@Scheduled`)
- [ ] **Export to CSV/PDF** (Apache POI / iText)
- [ ] **OAuth2 login** (Google, GitHub) — add `spring-boot-starter-oauth2-client`
- [ ] **Mobile API** — the REST layer already works, add CORS config
- [ ] **Push notifications** (Firebase FCM)

### Architecture
- [ ] **Event Sourcing** — store all expense mutations as events in Kafka
- [ ] **CQRS** — separate read/write models for analytics
- [ ] **Rate limiting** (Bucket4j + Redis)
- [ ] **Distributed tracing** (Micrometer + Zipkin)
- [ ] **API versioning** (prefix `/api/v1/`)

### DevOps
- [ ] **Dockerfile** + **docker-compose**
- [ ] **GitHub Actions CI/CD**
- [ ] **Kubernetes Helm chart**
- [ ] **Prometheus + Grafana** dashboards (actuator metrics already exposed)

---

## Project Structure

```
expense-tracker/
├── src/main/java/com/expensetracker/
│   ├── config/                  # Security, Redis, Kafka, WebSocket, Exception handler
│   ├── controller/              # REST + Web (Thymeleaf) controllers
│   ├── dto/
│   │   ├── request/             # RegisterRequest, LoginRequest, CreateExpenseRequest ...
│   │   └── response/            # AuthResponse, GroupBalanceResponse
│   ├── kafka/                   # Event producer + consumer
│   ├── model/                   # JPA entities (User, Group, Expense, ExpenseSplit, Settlement)
│   │   └── enums/               # SplitType, SettlementStatus, ExpenseCategory
│   ├── repository/              # Spring Data JPA repositories
│   ├── security/                # JwtUtil, JwtAuthFilter, UserDetailsServiceImpl
│   └── service/                 # AuthService, GroupService, ExpenseService,
│                                #   SettlementService, AnalyticsService
└── src/main/resources/
    ├── application.yml          # Multi-profile config (dev/prod)
    ├── static/
    │   ├── css/app.css
    │   └── js/app.js
    └── templates/
        ├── fragments/layout.html
        ├── auth/login.html
        ├── auth/register.html
        ├── dashboard.html
        └── group/
            ├── detail.html      # Expenses + members + balances
            ├── analytics.html   # Chart.js charts (monthly/weekly/category)
            └── settlements.html # Settle-up + history
```
