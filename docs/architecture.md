# CodeArena — Architecture Blueprint
*The "what we're building and why" document. Reference this during the Phase 0 whiteboard segment of the video, and paste it into Claude Code at the start of any implementation session so it has full architectural context.*

---

## 1. Project Identity

**Name:** CodeArena *(working name — replace with your final brand)*
**Domain:** Self-hosted code execution platform with multi-language support, custom test cases, submission history, and execution analytics.
**Tagline for the video:** *"What it actually looks like when you self-host code execution instead of leaning on a public API."*

**Why this project, why now:**
- Public code execution APIs (Piston, JDoodle) hit rate limits, cost money at scale, and don't let you customize sandboxing — every serious platform eventually self-hosts
- Judge0 is the open-source standard, battle-tested, used by HackerEarth-class platforms
- The interesting engineering isn't the UI — it's the **integration constraints**: cgroup v1 requirement, stateless worker pattern, async webhook handling, and horizontal scalability
- 10-15 hour scope forces honest decisions: what's worth building, what's worth cutting, what's worth showing-but-not-implementing

**Audience for the video series:** Intermediate developers who've built REST APIs and want to see microservices, gRPC, polyglot persistence, and self-hosted infrastructure done in a real (not toy) shape.

---

## 2. Bounded Contexts → Services

Four Spring Boot services + one Judge0 cluster. Each service earns its existence by owning data no other service owns and a workflow no other service runs.

| Service | Stack | Why it's separate | Owned data |
|---|---|---|---|
| **API Gateway** | Spring Cloud Gateway (Spring Boot 3.3) | Edge concerns isolated from business logic — JWT validation, rate limiting, routing | None (stateless) |
| **Auth Service** | Spring Boot 3.3 / Java 21 | Different security posture (handles credentials), different scaling profile (login bursts vs steady execution traffic), PII isolation | `users`, `refresh_tokens` (MongoDB) |
| **App Service** | Spring Boot 3.3 / Java 21 | User-facing submission orchestration, history, stats — the "business logic" tier | `submissions`, `problems`, `user_stats` (MongoDB) |
| **Execution Service** | Spring Boot 3.3 / Java 21 | Wraps Judge0 entirely — strict schemas, relational execution metadata, isolated failure domain | `executions`, `execution_languages` (Postgres) |
| **Judge0 cluster** | Judge0 official Docker images | Sandboxed code execution — third-party, treated as external dependency | Internal Postgres + Redis (don't touch) |

### 2.1 Why this split, defended

**Why Auth is separate from App** — three reasons in order of importance:
1. **Security blast radius.** A vulnerability in submission logic shouldn't leak password hashes. Process isolation matters.
2. **Different DB shape.** Auth needs strict schema with unique indexes on email; App needs flexible submission documents.
3. **Independent scaling.** Login spikes during tutorial promotions don't correlate with submission spikes during contests.

**Why Execution is separate from App** — this is the most important split:
1. **Different DB needs.** Execution metadata is highly relational (executions → test_cases → results) with strict schemas — Postgres territory. App data is document-shaped (submissions with variable metadata) — Mongo territory. Two databases owned by two services is honest polyglot persistence.
2. **Different failure semantics.** If Judge0 hangs, the Execution Service's thread pool fills up. Isolating this means the App Service stays responsive — users can still browse history while new executions are stalled.
3. **Different deployment cadence.** Tweaking Judge0 timeouts or language configs shouldn't require redeploying the user-facing app.

**Why we did NOT split further:**
- ❌ **No separate Problem Service** — problems are read-mostly reference data, fine to live in App Service. Splitting would be ceremony.
- ❌ **No separate Stats/Analytics Service** — stats are computed from the same `submissions` collection App owns. Don't fragment ownership.
- ❌ **No separate Notification Service** — we're not sending emails in this scope. If we were, this would earn a service.

---

## 3. The Topology

```
                    ┌──────────────────────────────────┐
                    │   Next.js 15 Frontend            │
                    │   App Router · Monaco Editor     │
                    │   Served as static via Nginx     │
                    └──────────────┬───────────────────┘
                                   │ HTTPS REST + JWT (Bearer header)
                    ┌──────────────▼───────────────────┐
                    │   API Gateway (Spring Cloud GW)  │  ← VM 1
                    │   JWT validation at edge         │
                    │   Rate limit (Redis-backed)      │
                    │   Correlation ID injection       │
                    └──┬───────────────┬───────────────┘
                       │ REST          │ REST + X-User-Id header
           ┌───────────▼──┐    ┌───────▼────────────────┐
           │ Auth Service │    │ App Service            │  ← VM 1
           │ Spring Boot  │    │ Spring Boot            │
           │ MongoDB      │    │ MongoDB                │
           │ (auth_db)    │    │ (app_db)               │
           └──────▲───────┘    └──────┬─────────────────┘
                  │                   │ gRPC
                  │ gRPC              │ (SubmitCode,
                  │ (ValidateToken)   │  GetExecutionResult)
                  └────────┬──────────┘
                           │
                  ┌────────▼─────────────────┐
                  │ Execution Service        │  ← VM 1
                  │ Spring Boot              │
                  │ Postgres (execution_db)  │
                  └────────┬─────────────────┘
                           │ REST (submit + callback URL)
                  ┌────────▼─────────────────┐
                  │ Internal Nginx LB        │  ← VM 1
                  │ least_conn algorithm     │
                  │ /about health checks     │
                  └────┬──────────────┬──────┘
                       │              │
           ┌───────────▼──┐    ┌──────▼───────┐
           │ Judge0 Node A│    │ Judge0 Node B│
           │ API + Worker │    │ API + Worker │
           │ Redis (shared)◄───┤ (points to A)│
           │ Postgres(shr)│    │              │
           │   ← VM 2     │    │   ← VM 3     │
           └──────┬───────┘    └──────┬───────┘
                  │                    │
                  └────────┬───────────┘
                           │ Webhook POST (execution complete)
                           ▼
                  Execution Service /webhook/judge0
```

### 3.1 Communication rules — memorize these

| Pattern | When | Example |
|---|---|---|
| **REST** | External boundary (browser ↔ gateway, gateway ↔ services) — anywhere debuggability and HTTP-native tooling matter | Browser → `POST /api/v1/submissions` |
| **gRPC** | Internal synchronous calls between Spring Boot services where a typed contract + speed matter | App Service calling `Execution.SubmitCode()` |
| **REST (third-party)** | Calls to Judge0 — non-negotiable, Judge0 only speaks REST | Execution Service → `POST http://judge0-lb/submissions` |
| **Webhook (REST callback)** | Async return path from Judge0 | Judge0 → `POST /webhook/judge0` on Execution Service |

**Anti-patterns we explicitly avoid and will call out on camera:**
- ❌ Frontend calling services directly (everything goes through the Gateway)
- ❌ Services calling each other via REST when gRPC is more appropriate
- ❌ Services validating JWTs themselves after the gateway already did
- ❌ Cross-database queries ("just join App's submissions with Execution's executions") — always go through gRPC
- ❌ Polling Judge0 for results when webhook callbacks exist
- ❌ Storing source code in Postgres `executions` table (it lives in Mongo `submissions` — Postgres has only the execution-side view)

---

## 4. Data Ownership and Schema Strategy

### 4.1 The polyglot persistence justification (defend on camera)

> "Submissions are user-facing artifacts with flexible metadata — title, problem reference, source code, language choice, user notes. They're append-mostly and read in document-shaped queries (give me this user's last 50 submissions). MongoDB fits.
>
> Executions are the technical record of what Judge0 did — strict schema, every field required, every field typed. They're queried relationally (give me avg execution time per language for this user). Postgres fits.
>
> One submission has 0..N executions (a user can re-run the same submission). The relationship is owned at the service boundary via gRPC, not at the DB layer via foreign keys."

That's a 30-second answer to "why two databases?" and it's honest.

### 4.2 MongoDB Collections (Auth Service — `auth_db`)

```javascript
// users
{
  _id: ObjectId,
  email: String,           // unique index
  username: String,        // unique index
  passwordHash: String,    // BCrypt
  role: String,            // "USER" | "ADMIN"
  createdAt: ISODate,
  updatedAt: ISODate,
  lastLoginAt: ISODate
}

// refresh_tokens
{
  _id: ObjectId,
  userId: ObjectId,        // index
  tokenHash: String,       // SHA-256 of the actual token
  expiresAt: ISODate,      // TTL index
  createdAt: ISODate,
  revokedAt: ISODate       // null if active
}
```

**Indexes:**
- `users.email` unique
- `users.username` unique
- `refresh_tokens.userId`
- `refresh_tokens.expiresAt` TTL (auto-delete expired)

### 4.3 MongoDB Collections (App Service — `app_db`)

```javascript
// problems  (seed data, ~10-15 problems for demo)
{
  _id: ObjectId,
  slug: String,            // unique, e.g. "two-sum"
  title: String,
  description: String,     // markdown
  difficulty: String,      // "EASY" | "MEDIUM" | "HARD"
  sampleTestCases: [
    { input: String, expectedOutput: String, isHidden: Boolean }
  ],
  supportedLanguages: [Number],  // Judge0 language IDs
  createdAt: ISODate
}

// submissions
{
  _id: ObjectId,
  userId: ObjectId,        // index
  problemId: ObjectId,     // index (nullable — for "playground" submissions)
  languageId: Number,      // Judge0 language ID
  sourceCode: String,
  customStdin: String,     // nullable, for playground mode
  executionTokens: [String],  // Judge0 tokens for the executions this submission triggered
  latestStatus: String,    // "PENDING" | "ACCEPTED" | "WRONG_ANSWER" | etc.
  createdAt: ISODate,
  updatedAt: ISODate
}

// user_stats  (denormalized, updated on submission completion)
{
  _id: ObjectId,
  userId: ObjectId,        // unique index
  totalSubmissions: Number,
  acceptedSubmissions: Number,
  byLanguage: {
    "71": { count: Number, accepted: Number, avgTimeMs: Number },  // Python
    "62": { count: Number, accepted: Number, avgTimeMs: Number },  // Java
    // ... keyed by Judge0 language ID
  },
  lastSubmissionAt: ISODate
}
```

**Indexes:**
- `submissions.userId`
- `submissions.userId + createdAt` (compound, for paginated history)
- `submissions.problemId`
- `user_stats.userId` unique

### 4.4 Postgres Schema (Execution Service — `execution_db`)

```sql
CREATE TABLE execution_languages (
  id              INTEGER PRIMARY KEY,        -- Judge0 language ID
  name            VARCHAR(100) NOT NULL,
  is_active       BOOLEAN NOT NULL DEFAULT TRUE,
  cached_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE executions (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  judge0_token        VARCHAR(64) UNIQUE NOT NULL,    -- Judge0's submission token
  user_id             VARCHAR(64) NOT NULL,           -- Mongo ObjectId as string
  submission_id       VARCHAR(64),                    -- Mongo ObjectId as string, nullable
  language_id         INTEGER NOT NULL REFERENCES execution_languages(id),
  source_code_hash    CHAR(64) NOT NULL,              -- SHA-256, source itself stays in Mongo
  stdin               TEXT,
  expected_output     TEXT,
  cpu_time_limit      NUMERIC(5,2) NOT NULL DEFAULT 5.0,    -- seconds
  memory_limit        INTEGER NOT NULL DEFAULT 128000,      -- KB
  status_id           INTEGER,                        -- Judge0 status ID
  status_description  VARCHAR(50),                    -- "Accepted", "Wrong Answer", etc.
  stdout              TEXT,
  stderr              TEXT,
  compile_output      TEXT,
  time_ms             INTEGER,                        -- actual execution time
  memory_kb           INTEGER,                        -- actual memory used
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  completed_at        TIMESTAMPTZ
);

CREATE INDEX idx_executions_user_id ON executions(user_id);
CREATE INDEX idx_executions_user_created ON executions(user_id, created_at DESC);
CREATE INDEX idx_executions_submission ON executions(submission_id);
CREATE INDEX idx_executions_status ON executions(status_id);
```

**Why source code is hashed in Postgres but stored in Mongo:**
- Source code lives once, in the user-facing `submissions` collection (Mongo)
- Postgres stores only the hash for integrity verification
- This avoids 2x storage and avoids the ambiguity of "which copy is canonical?"

---

## 5. The Auth Flow End-to-End

This is the flow you'll explain on camera. Memorize the sequence.

### 5.1 Registration

```
1. Frontend → Gateway: POST /api/v1/auth/register { email, username, password }
2. Gateway → Auth Service: POST /auth/register (REST passthrough, no JWT yet)
3. Auth Service:
   - Validates input
   - BCrypt-hashes password (cost 12)
   - Inserts into users collection
   - Generates access JWT (15 min) + refresh token (30 days)
   - Stores refresh token hash in refresh_tokens collection
4. Auth Service → Gateway → Frontend: { accessToken, refreshToken, user }
5. Frontend: stores accessToken in memory, refreshToken in httpOnly cookie
```

### 5.2 Authenticated Request (e.g., submit code)

```
1. Frontend → Gateway: POST /api/v1/submissions
   Header: Authorization: Bearer <accessToken>
2. Gateway: validates JWT signature locally (HMAC-SHA256, shared secret)
   - If invalid → 401 immediately
   - If valid → extract userId, inject as X-User-Id header
3. Gateway → App Service: POST /submissions
   Header: X-User-Id: <userId>  (gateway-trusted, never from client)
4. App Service: trusts X-User-Id, processes request
5. App Service → Execution Service: gRPC SubmitCode(request)
   gRPC metadata: user-id=<userId>
6. Execution Service: persists pending execution row, calls Judge0 with callback_url
7. Execution Service → App Service: returns ExecutionToken via gRPC
8. App Service: persists submission with executionTokens array, returns 202 to Frontend
```

### 5.3 The webhook return path

```
1. Judge0 finishes execution
2. Judge0 → Execution Service: PUT /webhook/judge0/<token>
   Body: { stdout, stderr, status, time, memory, ... }
3. Execution Service: 
   - Updates executions row (status, stdout, time_ms, memory_kb)
   - Calls App Service via gRPC: NotifyExecutionCompleted(token, status)
4. App Service:
   - Updates submissions.latestStatus
   - Updates user_stats (denormalized)
5. Frontend: polls GET /api/v1/submissions/<id> every 1s until status != PENDING
   (we are deliberately not using WebSockets — see §7.2)
```

---

## 6. Judge0 Integration — The Constraints That Will Bite You

### 6.1 Cgroup v1 — non-negotiable

Judge0's isolation engine (`isolate`) depends on **Linux cgroup v1**. Modern Ubuntu (22.04+, 24.04) defaults to **cgroup v2 unified hierarchy**. If you skip the GRUB modification, Judge0 will throw:

```
status 13 (Internal Error)
or
No such file or directory @ rb_sysopen — /sys/fs/cgroup/memory/...
```

You'll waste hours. Don't skip this.

**The fix (must run on VM 2 and VM 3 — the Judge0 hosts):**

```bash
sudo sed -i 's/GRUB_CMDLINE_LINUX_DEFAULT="\(.*\)"/GRUB_CMDLINE_LINUX_DEFAULT="\1 systemd.unified_cgroup_hierarchy=0 systemd.legacy_systemd_cgroup_controller=1"/' /etc/default/grub
sudo update-grub
sudo reboot
```

After reboot, verify:
```bash
mount | grep cgroup | head -5
# Should show v1-style mounts: cgroup on /sys/fs/cgroup/memory type cgroup
```

This goes in the deployment artifact too, but it's flagged here because it's the #1 architectural constraint.

### 6.2 Multi-node Judge0 — the stateless worker pattern

Judge0's standard Docker Compose runs API + Worker + Redis + Postgres all together. **Naively running this stack on both VM 2 and VM 3 creates split-brain** — VM 2's worker only sees VM 2's queue, VM 3's worker only sees VM 3's queue, and the load balancer routes blindly.

**The correct multi-node pattern:**

- **VM 2 (stateful):** runs Redis + Postgres + Judge0 API + Judge0 Worker. The Redis and Postgres here are the **shared state** for the cluster.
- **VM 3 (stateless):** runs only Judge0 API + Judge0 Worker, with config pointing to VM 2's Redis and Postgres.

When the internal Nginx routes a submission to VM 3's API, VM 3 inserts the job into VM 2's shared Redis queue. VM 3's worker (or VM 2's worker, whichever picks up first) processes it. The result lands in VM 2's shared Postgres. Either node can serve the result on subsequent reads.

**This is what makes the demo work:** killing VM 3 during a live submission stream still results in successful executions because VM 2 picks up the slack from the shared queue.

### 6.3 Judge0's webhook (`callback_url`) — use it, don't poll

Judge0 supports a `callback_url` field in submission requests. When the execution completes, Judge0 POSTs the full result to that URL. **Use this.** Polling Judge0 every second per submission will saturate the API container under any load.

```json
{
  "source_code": "...",
  "language_id": 71,
  "stdin": "...",
  "expected_output": "...",
  "callback_url": "http://execution-service:8080/webhook/judge0"
}
```

The webhook URL must be reachable from Judge0 nodes. In our topology, all 3 VMs are in the same GCP VPC, so internal IPs work. Document this in the deployment artifact.

### 6.4 Language IDs — the lookup table

Judge0 assigns numeric IDs to languages. Common ones:

| Language | ID | Notes |
|---|---|---|
| Python 3.8.1 | 71 | Default Python |
| Java (OpenJDK 13) | 62 | |
| C++ (GCC 9.2.0) | 54 | |
| JavaScript (Node 12) | 63 | |
| Go (1.13.5) | 60 | |
| Rust (1.40.0) | 73 | |
| C (GCC 9.2.0) | 50 | |

The Execution Service caches these in `execution_languages` on startup. The frontend fetches the active list from the App Service (which proxies to Execution Service via gRPC) so the language dropdown always reflects what Judge0 actually supports.

---

## 7. Cross-Cutting Concerns

### 7.1 Rate limiting

Applied at the **API Gateway** using Spring Cloud Gateway's `RequestRateLimiter` filter, backed by Redis (a small Redis instance on VM 1, separate from Judge0's Redis on VM 2).

Limits:
- **Submissions endpoint:** 10/minute per authenticated user
- **Auth endpoints:** 5/minute per IP (for login/register)
- **Read endpoints:** 60/minute per user

Why this matters: code execution is expensive. Without rate limiting, one rogue script can fill the Judge0 queue.

### 7.2 Why we're not using WebSockets (defend on camera)

> "Code execution typically completes in 1-5 seconds. Polling `GET /submissions/{id}` every second for ~5 polls is trivial code, zero infrastructure overhead, and indistinguishable from real-time at human latency. WebSockets earn their place when you need sub-100ms updates over long-lived sessions — e.g., chat, live cursors. They're overkill for 'did my code finish?'.
>
> If we ever add real-time features like live contest leaderboards, we'd add WebSockets then. Right now, polling is the right tool. Adding WebSockets prematurely would mean managing connection state, heartbeats, reconnection logic, and a stickiness story for the load balancer — none of which serves the user better than polling does."

That's the answer. Stick to it.

### 7.3 Correlation IDs

Every request gets a correlation ID at the gateway (`X-Correlation-Id` header, UUID v4). It propagates through:
- HTTP headers (gateway → services)
- gRPC metadata (App → Execution)
- Webhook callbacks (Execution → Judge0 → back to Execution, via the webhook path itself encoding the token)
- Log MDC (every log line includes correlationId)

When debugging, you grep all 4 services for one correlation ID and reconstruct the full request lifecycle.

### 7.4 Error response standard

All services return RFC 7807 Problem+JSON for errors:

```json
{
  "type": "https://codearena.dev/errors/validation",
  "title": "Validation Failed",
  "status": 400,
  "detail": "languageId 999 is not supported",
  "instance": "/api/v1/submissions",
  "correlationId": "uuid",
  "errors": [
    { "field": "languageId", "message": "must be a supported language" }
  ]
}
```

HTTP status conventions:
- `400` — client validation error
- `401` — missing/invalid auth
- `403` — authenticated but not authorized (trying to view another user's submission)
- `404` — resource doesn't exist
- `409` — conflict (duplicate submission token)
- `422` — semantically invalid (unsupported language)
- `429` — rate limited
- `500` — unexpected server error
- `503` — Judge0 cluster unavailable

### 7.5 Observability (lightweight for this scope)

We're **not** deploying the full LGTM stack from Nexastore. For 10-15 hours, that's ceremony. Instead:

- **Spring Boot Actuator** enabled on every service with `/actuator/health` and `/actuator/metrics`
- **Structured JSON logging** via Logback's logstash encoder
- **Docker logs aggregated** via `docker compose logs -f` during demo (mention "in production we'd ship to Loki/Grafana")
- **Health check endpoints** consumed by Nginx and by GitHub Actions deployment verification

That's enough for demo and honest about what's cut.

---

## 8. What We're Deliberately NOT Building (and why)

| Cut | Reason |
|---|---|
| **Kafka** | Judge0 is already a queue (its internal Redis/Resque). With gRPC for sync calls and webhooks for async return, no second consumer needs the event. Adding Kafka would be queueing a queue. |
| **Outbox Pattern** | We don't have a "publish event after DB commit" requirement. Webhook from Judge0 is the only async signal, and it's idempotent by token. |
| **Schema Registry / Avro** | No Kafka, no need. gRPC proto files give us strict typing where it matters. |
| **WebSockets / SSE** | See §7.2. Polling fits 1-5s execution times. |
| **Separate Notification Service** | We're not sending emails. If we add them, this is the seam where it splits. |
| **Multi-cloud** | Nexastore covers that story. Adding it here is repetition without learning value. |
| **Service Mesh (Istio/Linkerd)** | 4 services don't justify mesh complexity. mTLS at this scale is overengineering. |
| **Distributed tracing (Tempo/Jaeger)** | Correlation IDs in logs serve the same debugging purpose at this scale. Mention as "production upgrade" on camera. |
| **Database migrations framework (Flyway/Liquibase)** | One Postgres schema, ~30 lines of DDL. We use a single `init.sql` mounted as a Docker volume. Mention Flyway as production-track. |
| **Refresh token rotation** | Simple JWT + long-lived refresh token is fine for demo. Rotation is a v2 concern. |
| **Email verification** | Demo accounts only. v2 concern. |
| **Admin panel** | The Judge0 dashboard at `:2358` is the admin panel for now. |
| **Docker image scanning / SBOM** | Production concern. Out of scope. |

Each cut is **principled**, not lazy. Each one has a defensible answer when asked on camera.

---

## 9. Senior-Engineer Smell Test

If a senior engineer reviewed this architecture, here's what they'd nod at and what they'd push back on:

**They'd nod at:**
- ✅ Polyglot persistence with clean service ownership (no cross-DB queries)
- ✅ gRPC for typed internal calls, REST for the edge — appropriate tool for each layer
- ✅ Stateless Judge0 workers with shared state — the only correct multi-node pattern
- ✅ Auth at the edge, trusted context propagated inward (gateway is the trust boundary)
- ✅ Webhook over polling for async return path
- ✅ Cgroup v1 acknowledgment up-front — shows operational awareness
- ✅ Honest cuts with documented reasoning

**They'd push back on (and your defense):**
- ❓ "Why no circuit breaker on the App → Execution gRPC call?"
  → "Resilience4j is the next thing I'd add. For demo scope, we accept that an Execution Service outage cascades to App. In v2, circuit breaker with fallback to 'Execution temporarily unavailable, your submission is saved and will be processed when service recovers.'"

- ❓ "VM 2 is a single point of failure for the Judge0 cluster (it owns Redis + Postgres)."
  → "Correct, deliberate. For demo, simpler than HA Postgres + Redis Sentinel. In production, Redis would move to Memorystore (managed), Postgres to Cloud SQL (managed), and Judge0 nodes would all be stateless."

- ❓ "What happens if the webhook from Judge0 is lost?"
  → "Each pending execution has a `created_at` timestamp. A scheduled job in Execution Service polls Judge0 for any execution still PENDING after 30 seconds. This is the safety net. Webhook is the happy path; polling is the recovery path."

- ❓ "Source code stored in Mongo could be huge. Is there a size limit?"
  → "Yes, we cap at 64KB at the gateway level (rejected with 413 if exceeded). Reasonable for code, prevents abuse."

---

## 10. Reference: The Five Artifacts

This is artifact 1 of 5. The others:

- **Artifact 2:** `judge0-contracts` — gRPC proto files, REST endpoints, full schema DDLs
- **Artifact 3:** `judge0-roadmap` — hour-by-hour implementation roadmap (10-15 hours)
- **Artifact 4:** `judge0-frontend` — Next.js 15 frontend specification with components and API client
- **Artifact 5:** `judge0-devops` — Git strategy, GitHub Actions workflows, GCP deployment, demo guide

When pasted into Claude Code, each artifact is self-contained. Cross-references by name, never by chat history.
