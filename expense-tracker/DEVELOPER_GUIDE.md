# SplitEase — Developer Guide

> Complete explanation of how every layer works, how to run it, and how to test it.

---

## Table of Contents

1. [How to Run](#1-how-to-run)
2. [Project Architecture](#2-project-architecture)
3. [Authentication Flow](#3-authentication-flow)
4. [Data Model (Entity Relationships)](#4-data-model-entity-relationships)
5. [Split Types Explained](#5-split-types-explained)
6. [Settlement Algorithm Deep Dive](#6-settlement-algorithm-deep-dive)
7. [Analytics Endpoints](#7-analytics-endpoints)
8. [Real-Time Updates (WebSocket + Kafka)](#8-real-time-updates-websocket--kafka)
9. [Redis Caching](#9-redis-caching)
10. [How to Use the Postman Collection](#10-how-to-use-the-postman-collection)
11. [API Quick Reference](#11-api-quick-reference)
12. [Common Errors & Fixes](#12-common-errors--fixes)
13. [Tech Stack Decision Log](#13-tech-stack-decision-log)

---

## 1. How to Run

### Fastest path (no Docker, no external services)

```bash
cd expense-tracker
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

| URL | Purpose |
|-----|---------|
| http://localhost:8080 | Web UI |
| http://localhost:8080/login | Login page |
| http://localhost:8080/h2-console | In-memory DB browser |
| http://localhost:8080/swagger-ui.html | Interactive API docs |
| http://localhost:8080/actuator/health | Health check |

**Demo login** (auto-seeded):
```
Email:    alice@demo.com
Password: password
```

### With full infrastructure (PostgreSQL + Redis + Kafka)

1. Create `docker-compose.yml` (see README.md for content)
2. `docker-compose up -d`
3. `mvn spring-boot:run -Dspring-boot.run.profiles=prod`

---

## 2. Project Architecture

```
Browser / Postman
        │
        ▼
┌────────────────────────────────────────────────────┐
│                  Spring Boot App                   │
│                                                    │
│  ┌─────────────┐   ┌──────────────────────────┐   │
│  │ Thymeleaf   │   │ REST Controllers          │   │
│  │ Web Pages   │   │ /api/auth, /api/groups,   │   │
│  │ (HTML+JS)   │   │ /api/expenses,            │   │
│  └──────┬──────┘   │ /api/settlements,         │   │
│         │          │ /api/analytics            │   │
│         └──────────┴─────────┬────────────────┘   │
│                               │                    │
│                    ┌──────────▼──────────┐         │
│                    │  Service Layer      │         │
│                    │  (Business Logic)   │         │
│                    └──────────┬──────────┘         │
│                               │                    │
│          ┌────────────────────┼──────────────┐     │
│          │                    │              │     │
│   ┌──────▼──────┐  ┌─────────▼──────┐  ┌───▼──┐  │
│   │   JPA Repos │  │ Kafka Producer │  │Redis │  │
│   │ (PostgreSQL)│  │   + Consumer   │  │Cache │  │
│   └─────────────┘  └────────────────┘  └──────┘  │
└────────────────────────────────────────────────────┘
        │                   │
        ▼                   ▼
  PostgreSQL / H2         Kafka Topics
                              │
                         ┌────▼────┐
                         │WebSocket│ (→ Browser live updates)
                         └─────────┘
```

### Layer responsibilities

| Layer | Package | Responsibility |
|-------|---------|---------------|
| **Models** | `model/` | JPA entities — maps to DB tables |
| **Repositories** | `repository/` | SQL queries via Spring Data JPA |
| **Services** | `service/` | All business logic — no HTTP here |
| **Controllers** | `controller/` | HTTP I/O only — delegates to services |
| **Security** | `security/` | JWT parsing, user loading |
| **Config** | `config/` | Spring beans, wiring, exception handler |
| **Kafka** | `kafka/` | Event publishing and consuming |
| **Templates** | `resources/templates/` | Server-rendered HTML (Thymeleaf) |
| **Static** | `resources/static/` | CSS + JS served as-is |

---

## 3. Authentication Flow

### How it works (step by step)

```
Client                          Server
  │                               │
  │  POST /api/auth/register      │
  │  {name, email, password}      │
  │──────────────────────────────►│
  │                               │  1. Validate input
  │                               │  2. BCrypt hash the password
  │                               │  3. Save User to DB
  │                               │  4. Generate access token (24h JWT)
  │                               │  5. Generate refresh token (7d JWT)
  │                               │  6. Save refresh token on User record
  │◄──────────────────────────────│
  │  {accessToken, refreshToken}  │
  │                               │
  │  GET /api/groups              │
  │  Authorization: Bearer <jwt>  │
  │──────────────────────────────►│
  │                               │  JwtAuthFilter runs:
  │                               │  1. Extract token from header or cookie
  │                               │  2. Validate signature + expiry
  │                               │  3. Load UserDetails from DB
  │                               │  4. Set SecurityContext
  │◄──────────────────────────────│
  │  [group list]                 │
```

### JWT structure

```
Header:  { "alg": "HS256" }
Payload: { "sub": "alice@demo.com", "iat": 1720000000, "exp": 1720086400 }
Signature: HMAC-SHA256(base64(header) + "." + base64(payload), secret)
```

- The **secret** is 64 hex chars (`app.jwt.secret` in config)
- Token stored in `localStorage` by the JS frontend
- Also set as a cookie (`jwt=...`) so Thymeleaf server-side pages work

### Key files

| File | What it does |
|------|-------------|
| `JwtUtil.java` | Generates + validates tokens using jjwt library |
| `JwtAuthFilter.java` | Runs on every request, extracts + validates token |
| `UserDetailsServiceImpl.java` | Loads User from DB by email for Spring Security |
| `SecurityConfig.java` | Defines which paths are public vs. protected |
| `AuthService.java` | Register / Login / Refresh logic |
| `AuthController.java` | REST endpoints for auth |

---

## 4. Data Model (Entity Relationships)

```
User (users)
 ├─── id, name, email, password (bcrypt), phone, currency
 └─── refreshToken (stored for rotation)

Group (expense_groups)
 ├─── id, name, description, defaultCurrency
 ├─── createdBy → User (FK)
 └─── active (soft delete flag)

GroupMember (group_members)
 ├─── group → Group (FK)
 ├─── user  → User (FK)
 └─── role  (ADMIN | MEMBER)

 Unique constraint: (group_id, user_id)

Expense (expenses)
 ├─── id, description, amount, currency
 ├─── splitType  (EQUAL | EXACT | PERCENTAGE)
 ├─── category   (FOOD | TRANSPORT | ... | OTHER)
 ├─── expenseDate, notes, receiptUrl
 ├─── group  → Group (FK)
 ├─── paidBy → User (FK)  ← who actually paid the bill
 └─── settled (boolean flag)

ExpenseSplit (expense_splits)
 ├─── expense → Expense (FK)
 ├─── user    → User (FK)
 ├─── amount  (their share of the expense)
 ├─── percentage (only for PERCENTAGE split)
 └─── paid (boolean — has this person's share been settled?)

 Unique constraint: (expense_id, user_id)

Settlement (settlements)
 ├─── id, amount, currency
 ├─── group    → Group (FK)
 ├─── payer    → User (FK)   ← who sent money
 ├─── receiver → User (FK)   ← who received money
 ├─── status   (PENDING | COMPLETED | CANCELLED)
 ├─── paymentReference (UPI ID, transaction ID, etc.)
 └─── settledAt (timestamp)
```

### DB indexes created automatically

```sql
-- Hot query paths are indexed for performance
CREATE INDEX idx_expense_group   ON expenses(group_id);
CREATE INDEX idx_expense_date    ON expenses(expense_date);
CREATE INDEX idx_expense_paid_by ON expenses(paid_by);
CREATE UNIQUE INDEX idx_user_email ON users(email);
```

---

## 5. Split Types Explained

### EQUAL split

Everyone pays the same amount.

```
Expense: $300 dinner, paid by Alice
Participants: Alice, Bob, Carol
Each owes: $300 / 3 = $100

ExpenseSplit rows:
  Alice → $100 (marked paid=true since she paid)
  Bob   → $100 (paid=false — owes Alice)
  Carol → $100 (paid=false — owes Alice)
```

**Request body:**
```json
{
  "splitType": "EQUAL",
  "participantIds": [1, 2, 3]
}
```

### EXACT split

You manually specify what each person owes.

```
Expense: $180 restaurant bill
  Alice ordered the steak  → owes $80
  Bob   ordered the salmon → owes $60
  Carol ordered the pasta  → owes $40
  Total: $80 + $60 + $40 = $180 ✓
```

**Request body:**
```json
{
  "splitType": "EXACT",
  "splitDetails": {
    "1": 80.00,
    "2": 60.00,
    "3": 40.00
  }
}
```

Validation: `splitDetails` values must sum exactly to `amount`.

### PERCENTAGE split

Useful when shares are proportional but not equal.

```
Expense: $120 fuel
  Alice drove 50% of the time → owes $60
  Bob drove 30%               → owes $36
  Carol drove 20%             → owes $24
  Total percentages: 100% ✓
```

**Request body:**
```json
{
  "splitType": "PERCENTAGE",
  "splitDetails": {
    "1": 50,
    "2": 30,
    "3": 20
  }
}
```

Validation: percentages must sum to 100.

---

## 6. Settlement Algorithm Deep Dive

### Problem

After many expenses, everyone has a complex web of debts. You want to **minimize the number of transactions** needed to fully settle the group.

### Step 1 — Calculate Net Balances

For each unsettled expense:
- **Payer** gets `+amount` (they're owed money)
- **Each participant** gets `-their_split_amount` (they owe money)

Then apply completed settlements:
- **Payer of settlement** gets `+amount` (they paid off debt)
- **Receiver of settlement** gets `-amount` (they were paid back)

```
Expenses (simplified):
  Hotel $300 — Alice paid, equal split
    Alice: +300, then -100 = net +200
    Bob:   -100
    Carol: -100

  Dinner $180 — Bob paid, exact split (Alice $80, Bob $60, Carol $40)
    Bob:   +180, then -60 = net +120
    Alice: -80
    Carol: -40

  Fuel $120 — Carol paid, percentage (50/30/20)
    Carol: +120, then -24 = net +96
    Alice: -60
    Bob:   -36

Net balances:
  Alice: +200 - 80 - 60 = +60  (is owed $60)
  Bob:   -100 + 120 - 36 = -16 (owes $16)
  Carol: -100 - 40 + 96 = -44  (owes $44)
```

### Step 2 — Minimize Cash Flow Algorithm

```java
// Uses two max-heaps (largest values first)
PriorityQueue<long[]> creditors; // people with positive balance
PriorityQueue<long[]> debtors;   // people with negative balance

while (!creditors.isEmpty() && !debtors.isEmpty()) {
    creditor = creditors.poll();  // biggest creditor
    debtor   = debtors.poll();    // biggest debtor

    transfer = min(creditor.balance, debtor.balance);

    // Create: debtor pays creditor `transfer` amount
    suggestions.add(new Settlement(debtor, creditor, transfer));

    // If debtor still owes → put back
    if (debtor.balance > transfer) debtors.offer(debtor with balance - transfer);

    // If creditor still owed → put back
    if (creditor.balance > transfer) creditors.offer(creditor with balance - transfer);
}
```

### Step 3 — Result for the example above

```
Naive approach would be 3 transactions (everyone to everyone).

Algorithm output:
  Bob   → Alice: $16  (Bob's full debt cleared)
  Carol → Alice: $44  (Carol's full debt cleared)

Total: 2 transactions ✓ (minimum possible)
```

The algorithm guarantees the minimum number of transactions. Works correctly with any number of users and complex expense histories.

---

## 7. Analytics Endpoints

All analytics endpoints query the DB and return `DataPoint` objects:
```json
{ "label": "JULY", "value": 879.60 }
```

| Endpoint | Aggregation | Chart type |
|----------|-------------|-----------|
| `/daily?from=&to=` | `GROUP BY expense_date` | Line chart |
| `/weekly?year=` | `GROUP BY WEEK(expense_date)` | Bar chart |
| `/monthly?year=` | `GROUP BY MONTH(expense_date)` | Bar chart |
| `/categories` | `GROUP BY category` | Doughnut chart |
| `/summary` | Three separate queries | Stat cards |

The frontend (`analytics.html`) receives these as Thymeleaf model attributes and injects them directly into Chart.js datasets:

```javascript
// Server injects this via Thymeleaf
const monthlyData = /*[[${monthlyData}]]*/ [];

new Chart(canvas, {
    type: 'bar',
    data: {
        labels: monthlyData.map(d => d.label),
        datasets: [{ data: monthlyData.map(d => d.value) }]
    }
});
```

---

## 8. Real-Time Updates (WebSocket + Kafka)

### How the pipeline works

```
User adds expense
       │
       ▼
ExpenseController
       │  calls
       ▼
ExpenseService.createExpense()
       │  (in prod) publishes event to
       ▼
Kafka topic: "expense.created"
       │
       ▼
ExpenseEventConsumer (Kafka listener)
       │  broadcasts via
       ▼
SimpMessagingTemplate
       │
       ▼
WebSocket topic: /topic/group/{id}/expenses
       │
       ▼
Browser (subscribed via STOMP/SockJS)
       │
       ▼
loadExpenses() called → UI refreshes without page reload
```

### Kafka topics

| Topic | Published when | Consumed by |
|-------|---------------|-------------|
| `expense.created` | New expense added | UI refresh via WS |
| `expense.settled` | Expense marked settled | UI refresh via WS |
| `settlement.completed` | Payment recorded | UI refresh via WS |
| `group.activity` | Member added/removed | Activity feed |
| `notification.push` | (future) | Email / push service |

### WebSocket subscription in the browser

```javascript
const client = new StompJs.Client({
    webSocketFactory: () => new SockJS('/ws')
});

client.onConnect = () => {
    // Subscribe to group-specific channel
    client.subscribe(`/topic/group/${groupId}/expenses`, (message) => {
        const event = JSON.parse(message.body);
        loadExpenses(groupId); // refresh the expense list
    });
};

client.activate();
```

> In **dev mode**, Kafka is disabled. Changes are reflected after a page refresh.

---

## 9. Redis Caching

Redis is used to cache expensive computed results so they don't hit the DB on every request.

### Cache names and TTLs

| Cache name | What's cached | TTL |
|------------|--------------|-----|
| `groupBalances` | Net balance map per group | 5 min |
| `analytics` | Chart data points | 10 min |
| `userGroups` | List of groups per user | 15 min |

### How caching works

```java
// Example from SettlementService (cache enabled in prod)
@Cacheable(value = "groupBalances", key = "#groupId")
public GroupBalanceResponse getGroupBalances(Long groupId) {
    // Only runs if not in cache
    // Result is stored in Redis with TTL
}

// When a new expense or settlement is recorded:
@CacheEvict(value = "groupBalances", key = "#groupId")
public void recordSettlement(...) {
    // After recording, the cached balance is invalidated
    // Next call to getGroupBalances will recompute and re-cache
}
```

> In **dev mode**, Redis is disabled. Every call hits the H2 database directly.

---

## 10. How to Use the Postman Collection

### Import steps

1. Open Postman
2. Click **Import** (top left)
3. Import both files:
   - `SplitEase.postman_collection.json`
   - `SplitEase.postman_environment.json`
4. In the top-right dropdown, select **"SplitEase Local"**
5. Make sure the app is running on `localhost:8080`

### Run order (important)

The collection uses **collection variables** that are set automatically by test scripts. Run in this order:

```
01 - Auth
  → Register Alice      ← sets alice_id, access_token, refresh_token
  → Register Bob        ← sets bob_id
  → Register Carol      ← sets carol_id
  → Login as Alice      ← refreshes access_token

02 - Groups
  → Create Group        ← sets group_id

03 - Expenses
  → Add Hotel Expense   ← sets expense_id_1
  → Add Dinner Expense  ← sets expense_id_2
  → Add Fuel Expense    ← sets expense_id_3
  → (add more expenses)

04 - Settlements
  → Get Balances        ← shows who owes what + suggestions
  → Record Settlement   ← sets settlement_id
  → Cancel Settlement

05 - Analytics
  → (any order, uses group_id)

06 - Edge Cases
  → (tests error handling)
```

### Run as a Collection (automated)

1. Click the **"..."** next to the collection name → **Run collection**
2. All tests run in sequence
3. Green = pass, Red = fail

### Reading the balance response

```json
{
  "groupId": 1,
  "groupName": "Vegas Road Trip",
  "balances": [
    { "userId": 1, "userName": "Alice Johnson", "netBalance": 60.00 },
    { "userId": 2, "userName": "Bob Smith",     "netBalance": -16.00 },
    { "userId": 3, "userName": "Carol White",   "netBalance": -44.00 }
  ],
  "suggestions": [
    { "fromUserName": "Bob",   "toUserName": "Alice", "amount": 16.00 },
    { "fromUserName": "Carol", "toUserName": "Alice", "amount": 44.00 }
  ]
}
```

- `netBalance > 0` → this person is **owed** money
- `netBalance < 0` → this person **owes** money
- `suggestions` → the minimum set of payments to clear all debts

---

## 11. API Quick Reference

All protected routes require: `Authorization: Bearer <access_token>`

### Auth

```
POST /api/auth/register          Body: {name, email, password, phone?}
POST /api/auth/login             Body: {email, password}
POST /api/auth/refresh           Query: ?refreshToken=<token>
```

### Groups

```
GET    /api/groups                     List my groups
POST   /api/groups                     Create group
GET    /api/groups/{id}                Get group
GET    /api/groups/{id}/members        List members
POST   /api/groups/{id}/members        Add member  Query: ?email=...
DELETE /api/groups/{id}/members/{uid}  Remove member
DELETE /api/groups/{id}                Soft-delete group
```

### Expenses

```
POST   /api/expenses                        Create expense
GET    /api/expenses/group/{id}             All expenses in group
GET    /api/expenses/group/{id}/unsettled   Unsettled only
GET    /api/expenses/{id}                   Single expense
DELETE /api/expenses/{id}                   Delete (payer only)
```

### Settlements

```
GET   /api/settlements/group/{id}/balances  Net balances + suggestions
POST  /api/settlements                      Record payment
GET   /api/settlements/group/{id}           Settlement history
PATCH /api/settlements/{id}/cancel          Cancel settlement
```

### Analytics

```
GET /api/analytics/group/{id}/summary               Today/week/month totals
GET /api/analytics/group/{id}/daily?from=&to=       Daily breakdown
GET /api/analytics/group/{id}/weekly?year=          Weekly breakdown
GET /api/analytics/group/{id}/monthly?year=         Monthly breakdown
GET /api/analytics/group/{id}/categories            By category
```

---

## 12. Common Errors & Fixes

| Error | Status | Cause | Fix |
|-------|--------|-------|-----|
| `Email already registered` | 400 | Duplicate email on register | Use a different email |
| `Invalid email or password` | 401 | Wrong credentials | Check email/password |
| `User is not a member of this group` | 403 | Accessing a group you're not in | Join the group first |
| `Only group admins can perform this action` | 403 | Non-admin trying admin action | Login as group creator |
| `Exact splits must sum to the total expense amount` | 400 | Math doesn't add up | Make sure splitDetails values sum to amount |
| `Percentages must sum to 100` | 400 | Percentage split invalid | Make sure percentages sum to exactly 100 |
| `Group not found: 999` | 400 | Wrong group ID | Check the group_id variable in Postman |
| `403 Forbidden` on all requests | 403 | No JWT token | Run Login first to get token |
| H2 console shows empty tables | — | Wrong profile | Make sure `dev` profile is active |
| Port 8080 already in use | — | Another process on 8080 | `kill -9 $(lsof -ti:8080)` or change `server.port` |

---

## 13. Tech Stack Decision Log

| Decision | Why |
|----------|-----|
| **Spring Boot 3 / Java 17** | LTS, virtual threads ready, latest Spring features |
| **H2 for dev** | Zero-config dev startup — no Docker needed |
| **PostgreSQL for prod** | ACID, JSONB support, excellent indexing |
| **JWT (stateless)** | Scales horizontally — no session affinity needed |
| **Redis** | Sub-millisecond cache for balance calculations which run on every page load |
| **Kafka** | Decouples the expense write path from notification/analytics — if email service is down, expense still saves |
| **WebSocket (STOMP)** | Real-time group updates without polling — Kafka consumer bridges events to WS |
| **Thymeleaf** | Server-side rendering — SEO friendly, works without JS enabled, easy Spring integration |
| **Chart.js** | Lightweight, no build step required, excellent docs |
| **BCrypt** | Adaptive cost factor — resists brute force even as hardware improves |
| **Minimize Cash Flow** | O(n log n) greedy algorithm — same one Splitwise uses — provably optimal transaction count |
| **MapStruct** | Compile-time DTO mapping — no reflection overhead at runtime |
| **SpringDoc (Swagger)** | Auto-generated API docs from annotations — always in sync with code |
