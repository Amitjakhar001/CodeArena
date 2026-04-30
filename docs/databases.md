# CodeArena — Databases Setup & Service Interaction

The practical guide to our application databases — what they are, where they run, how to set them up, and how each Spring Boot service interacts with them.

**Scope:** This artifact covers the **two application databases we own**: MongoDB (for Auth + App services) and Postgres (for Execution service). It briefly mentions Judge0's internal databases at the end so you know they exist, but we don't manage them.

**For full schema definitions** (every collection, every column, every index), see the **contracts artifact §4**. This artifact references those schemas — it doesn't duplicate them.

---

## 1. The Real Database Topology

You may have been thinking "we have two databases." Let's be precise about the count:

### What we own and design

| Database technology | Container | Logical databases inside | Owner service |
|---|---|---|---|
| MongoDB 7 | `mongodb` | `auth_db` | Auth Service |
| MongoDB 7 | `mongodb` (same container) | `app_db` | App Service |
| Postgres 16 | `execution-postgres` | `execution_db` | Execution Service |

So: **one Mongo container** hosting **two logical databases inside it**, plus **one Postgres container** hosting **one database**. Three logical databases, two physical containers.

### The "one container, two databases" nuance

A single MongoDB instance can host many independent databases internally. They're isolated from each other — separate collections, separate indexes, separate access controls. This is exactly like how a single Postgres server can host multiple schemas, or a single MySQL server can host multiple databases.

We use this to save resources. Instead of running two Mongo processes (each with its own memory overhead, its own port, its own backup considerations), we run one process serving both `auth_db` and `app_db`. Auth Service connects with the URI `mongodb://mongodb:27017/auth_db`. App Service connects with `mongodb://mongodb:27017/app_db`. They're as isolated as if they were separate servers, just sharing a process.

For our scope this is the right call. If we ever needed stronger isolation — say, if `app_db`'s load was hurting `auth_db`'s latency — we'd split them into two containers. We don't need that now.

### What's also there but we don't manage (Judge0's internal stuff)

For completeness, here's what runs alongside on the GCP Judge0 VMs but is **not our concern**:

- **Judge0's internal Postgres** — stores Judge0's submission tracking and language config. Different database, different container, different lifecycle. Don't touch.
- **Judge0's internal Redis** — Judge0's job queue (Resque). The shared queue between Judge0 nodes lives here.
- **Gateway's Redis** — small Redis instance used only by Spring Cloud Gateway for rate limiting. Not a database in the traditional sense; it's an in-memory counter store.

These three are managed by the systems that need them. We just point them in the right direction via environment variables. The rest of this artifact ignores them.

---

## 2. MongoDB — What It Is And Why For Auth + App

### Quick definition

MongoDB is a document database. Instead of tables with rows and columns, it stores data as flexible JSON-like documents in collections. Every document in a collection can have a different shape. There's no schema enforced at the database level (though we can validate at the application level).

### Why MongoDB for Auth and App

**Auth Service data is naturally document-shaped.**
- A user document has email, username, password hash, role, timestamps
- A refresh token document has token hash, user reference, expiry
- These don't have complex relationships and don't need joins. They're standalone documents queried by ID or by indexed fields like email.

**App Service data benefits from flexibility.**
- A submission has variable metadata depending on whether it's a problem submission or playground submission
- User stats are denormalized — preaggregated counts per language stored as a nested object
- Problems have variable-shaped sample test cases as embedded arrays

If we forced this into a relational schema, we'd either need lots of nullable columns (for the variability) or many auxiliary tables (for the embedded arrays). Mongo handles both naturally.

### What lives where (cross-reference contracts §4.1 and §4.2)

**`auth_db` collections (owned by Auth Service):**
- `users` — registered user accounts
- `refresh_tokens` — long-lived refresh tokens (with TTL index for auto-cleanup)

**`app_db` collections (owned by App Service):**
- `problems` — coding problems users can solve
- `submissions` — every code submission, linked to a user and optionally a problem
- `user_stats` — denormalized stats per user

**For exact field definitions, indexes, and sample documents, see contracts artifact §4.1 and §4.2.** This artifact doesn't duplicate those.

### Common Mongo operations you'll use during dev

```javascript
// Connect via mongosh from inside the container
docker exec -it mongodb mongosh

// Switch to the auth database
use auth_db

// List collections
show collections

// Find all users
db.users.find().pretty()

// Find a specific user by email
db.users.findOne({ email: "alice@example.com" })

// Count submissions for a user (in app_db)
use app_db
db.submissions.countDocuments({ userId: ObjectId("...") })

// Drop everything (development only — careful!)
db.dropDatabase()
```

---

## 3. Postgres — What It Is And Why For Execution

### Quick definition

Postgres is a relational database. Strict schemas, rows and columns, every field typed. It's been around forever, has the best query planner in open source, and supports advanced features like JSON columns, partial indexes, and full-text search.

### Why Postgres for Execution Service

The Execution Service stores the technical record of every code execution. Every field is required, every field has a known type:

- `judge0_token` — exactly 64 characters, never null
- `language_id` — integer, must reference a row in `execution_languages`
- `time_ms`, `memory_kb` — integers, set by Judge0
- `status_id`, `status_description` — Judge0's verdict

This is **strict, relational data**. We're going to query it with SQL aggregations like:

```sql
-- Average execution time per language for a user, last 30 days
SELECT 
  language_id,
  AVG(time_ms) as avg_ms,
  COUNT(*) as count
FROM executions
WHERE user_id = $1
  AND created_at > NOW() - INTERVAL '30 days'
  AND status_description = 'Accepted'
GROUP BY language_id;
```

That kind of query is Postgres's home turf. Doing the equivalent in Mongo with aggregation pipelines works, but it's clunky and slower for this shape of data.

### The schema (cross-reference contracts §4.3)

`execution_db` has two tables:
- `execution_languages` — cached metadata about supported Judge0 languages
- `executions` — the row-per-execution table where the actual execution data lives

**For full DDL with columns, indexes, and the foreign key relationship, see contracts artifact §4.3.** Again, not duplicating here.

### Common Postgres operations you'll use during dev

```bash
# Connect via psql from inside the container
docker exec -it execution-postgres psql -U execution -d execution_db

# List tables
\dt

# Describe the executions table
\d executions

# Sample data
SELECT id, judge0_token, language_id, status_description, time_ms, memory_kb, created_at
FROM executions
ORDER BY created_at DESC
LIMIT 10;

# Drop everything and start fresh (dev only)
DROP TABLE executions, execution_languages CASCADE;
# Then restart the container — the init.sql will recreate
```

---

## 4. The Four Spring Boot Services — Database Interaction Map

Here's exactly which service owns which database, and which services are stateless.

### Service ownership table

| Service | Owns | Reads only | Doesn't touch any DB |
|---|---|---|---|
| API Gateway | — | — | ✓ (uses Redis for rate limit, but doesn't own application data) |
| Auth Service | `auth_db` (Mongo) | — | — |
| App Service | `app_db` (Mongo) | — | — |
| Execution Service | `execution_db` (Postgres) | — | — |

### The strict rule: no service touches another service's database

This is important enough that it's worth stating in bold: **a service can only read from or write to its own database.** If App Service needs data that Execution Service owns, it asks via gRPC. It does NOT connect to `execution_db` directly.

Why this rule matters:

1. **Encapsulation.** Each service controls its own schema. You can change the shape of `executions` without breaking App Service, because App only sees the shape Execution exposes via its gRPC contract.

2. **Independent deployment.** If two services share a database, you can never deploy one without considering the other. Schema changes become coordinated releases.

3. **Honest microservices.** "Microservices that share a database" is a famous anti-pattern called the *distributed monolith*. You get all the operational complexity of microservices with none of the benefits.

This rule is enforced by network configuration too — services don't have credentials to each other's databases.

### Detailed interaction flow for each service

#### API Gateway

- **Owns no application data.**
- Connects to a small Redis instance only for rate limiting (counts requests per user per minute).
- Stateless otherwise. Could be scaled to 10 instances and they'd all behave identically.
- Validates JWTs locally using a shared secret — no database hit, no Auth Service call. This is the "edge validation" pattern.

#### Auth Service

- **Owns `auth_db` on Mongo.**
- Writes to `users` collection on registration. Never updates passwords directly — only via a "change password" flow that re-hashes.
- Writes to `refresh_tokens` on login (one document per refresh token, hashed before storing).
- Reads from `users` on login (for password verification).
- Reads from `refresh_tokens` on refresh (validate token still active, not revoked).
- Has the shortest-running queries in the system — auth lookups are fast and indexed.

#### App Service

- **Owns `app_db` on Mongo.**
- Reads from `problems` to serve the problem listing and detail pages.
- Writes to `submissions` when a user submits code (creates a new document).
- Updates `submissions` when polling reveals execution completed (status changes from PENDING to ACCEPTED/etc.).
- Writes to `user_stats` (denormalized) when a submission completes — increments counts, recomputes averages.
- Calls Execution Service via gRPC — never reads `execution_db` directly.
- Calls Auth Service via gRPC for username lookups when needed (rare; mostly the gateway-forwarded `X-User-Id` is enough).

#### Execution Service

- **Owns `execution_db` on Postgres.**
- Writes to `executions` when a submission arrives (creates a row with status PENDING).
- Updates `executions` when Judge0's webhook fires (sets status, stdout, time_ms, etc.).
- Reads from `executions` when App Service asks for results via gRPC.
- Reads from `execution_languages` to validate language IDs and serve the language list.
- Has a recovery poller that finds executions stuck in PENDING > 30s and asks Judge0 directly (in case the webhook was lost).
- Calls Judge0 via REST — never reads Judge0's internal database.

### The full picture, end to end, for a single submission

This is the lifecycle of one code submission, showing every database touch:

```
1. Browser POSTs to /api/v1/submissions with JWT
2. Gateway validates JWT (no DB hit)
3. Gateway forwards to App Service with X-User-Id header
4. App Service validates input
5. App Service writes to app_db.submissions: { status: PENDING, ... }   ← Mongo write
6. App Service calls Execution.SubmitCode() via gRPC
7. Execution Service writes to execution_db.executions: { status: PENDING, ... }   ← Postgres write
8. Execution Service calls Judge0 with callback_url set
9. Execution Service returns token to App Service via gRPC
10. App Service updates app_db.submissions with executionToken   ← Mongo update
11. App Service returns 202 to browser

[time passes — 1-5 seconds — Judge0 runs the code]

12. Judge0 finishes, POSTs result to Execution Service's webhook
13. Execution Service updates execution_db.executions: { status: ACCEPTED, time_ms: 145, ... }   ← Postgres update

[browser is polling]

14. Browser GETs /api/v1/submissions/{id}
15. Gateway → App Service
16. App Service reads app_db.submissions   ← Mongo read
17. App Service calls Execution.GetExecutionResult() via gRPC
18. Execution Service reads execution_db.executions   ← Postgres read
19. Execution Service returns result via gRPC
20. App Service updates app_db.submissions.latestStatus   ← Mongo update
21. App Service updates app_db.user_stats (if newly completed)   ← Mongo update
22. App Service returns full submission to browser
```

Every database touch is by the owning service. App never reads from `execution_db`. Execution never reads from `app_db`. Communication crosses service boundaries via gRPC.

---

## 5. Local Development Setup (Fedora)

This is what runs on your Fedora laptop during the build phase. Everything via Docker — no native installation of Mongo or Postgres needed.

### The two database containers

Add these to your local `docker-compose.local.yml`:

```yaml
services:
  mongodb:
    image: mongo:7
    container_name: codearena-mongodb
    ports:
      - "27017:27017"   # Exposed locally so you can connect with Compass
    volumes:
      - mongodb-data:/data/db
    environment:
      # No auth in local dev — keeps onboarding simple
      # Production setup adds MONGO_INITDB_ROOT_USERNAME/PASSWORD
      MONGO_INITDB_DATABASE: admin
    restart: unless-stopped
    healthcheck:
      test: echo 'db.runCommand("ping").ok' | mongosh localhost:27017/admin --quiet
      interval: 10s
      timeout: 5s
      retries: 5

  execution-postgres:
    image: postgres:16
    container_name: codearena-execution-postgres
    ports:
      - "5432:5432"   # Exposed locally so you can connect with DBeaver
    volumes:
      - execution-postgres-data:/var/lib/postgresql/data
      - ./infra/docker/postgres-init.sql:/docker-entrypoint-initdb.d/init.sql:ro
    environment:
      POSTGRES_USER: execution
      POSTGRES_PASSWORD: dev-password-change-in-prod
      POSTGRES_DB: execution_db
    restart: unless-stopped
    healthcheck:
      test: pg_isready -U execution -d execution_db
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  mongodb-data:
  execution-postgres-data:
```

### Where the data physically lives on your Fedora laptop

When Docker Compose starts these containers, it creates **named volumes**:
- `codearena_mongodb-data`
- `codearena_execution-postgres-data`

These are managed by Docker. The actual files live under `/var/lib/docker/volumes/` on your Fedora machine. You don't normally need to look in there — Docker manages the lifecycle.

What matters operationally:

- **Volumes survive `docker compose down`.** Your data is safe.
- **Volumes are deleted by `docker compose down -v`.** That `-v` flag is a "blow it all away" switch. Useful for resetting, dangerous if accidental.
- **Volumes are deleted by `docker volume prune`.** Same warning.
- **Volumes are NOT deleted by `docker compose restart` or container restarts.**

### Starting it all up

```bash
# Start just the databases (useful when developing)
docker compose -f docker-compose.local.yml up -d mongodb execution-postgres

# Check they're healthy
docker compose -f docker-compose.local.yml ps
# Look for "healthy" in the STATUS column

# View logs
docker compose -f docker-compose.local.yml logs -f mongodb
docker compose -f docker-compose.local.yml logs -f execution-postgres

# Stop everything (data preserved)
docker compose -f docker-compose.local.yml down

# Stop everything AND wipe data (start completely fresh)
docker compose -f docker-compose.local.yml down -v
```

### Connecting from a GUI tool (highly recommended during dev)

You'll thank yourself later for setting these up.

**MongoDB Compass** (free from MongoDB):
- Connection string: `mongodb://localhost:27017`
- You'll see both `auth_db` and `app_db` once your services have written something
- Use it to inspect documents, manually edit data for testing, run queries

**DBeaver** (free, multi-database tool):
- Connection: PostgreSQL, host `localhost`, port `5432`, database `execution_db`, user `execution`, password from your env
- Browse tables, run SQL, export results
- DBeaver also speaks Mongo if you don't want a separate Compass install

### A note on Fedora specifically

These Docker images run identically on Fedora and Ubuntu. There's no Fedora-specific config needed.

The one thing to be aware of: if you set SELinux to permissive earlier (which we did to make Judge0 work), Mongo and Postgres also benefit from that — but they'd actually run fine in enforcing mode too. The SELinux fix isn't for them; it was for Judge0's privileged containers. Mongo and Postgres don't need privileged mode and play nicely with SELinux.

### Seeding sample data during dev

For the App database, you'll want some sample problems to test against:

```bash
# After mongodb container is up
docker exec -i codearena-mongodb mongosh app_db < infra/scripts/seed-problems.js
```

The seed script (defined in the contracts artifact and roadmap) inserts ~10 sample problems so the frontend has something to render.

For the Execution database, the `postgres-init.sql` mounted into the container automatically creates the schema and seeds the language list (Python, Java, C++, etc.) on first startup. No manual seeding needed.

For Auth, no seeding — you just register accounts through the UI as you would normally.

### Common dev workflow recipes

**"I want to reset the App database but keep my user accounts"**
```bash
docker exec -i codearena-mongodb mongosh
> use app_db
> db.dropDatabase()
> exit
docker exec -i codearena-mongodb mongosh app_db < infra/scripts/seed-problems.js
```

**"I broke the Postgres schema while experimenting"**
```bash
# Nuclear option — re-run init.sql
docker compose -f docker-compose.local.yml down -v
docker compose -f docker-compose.local.yml up -d execution-postgres
# init.sql runs automatically on first startup of a fresh volume
```

**"I want to see what Spring Data is actually writing"**
- For Mongo: enable query logging in your Spring Boot `application.yml`:
  ```yaml
  logging.level.org.springframework.data.mongodb.core.MongoTemplate: DEBUG
  ```
- For Postgres: same idea, with JPA:
  ```yaml
  logging.level.org.hibernate.SQL: DEBUG
  logging.level.org.hibernate.orm.jdbc.bind: TRACE
  ```

---

## 6. Spring Boot Connection Configuration

Each service has its own `application.yml` with profiles for `local` and `gcp`. The connection strings differ between profiles, but the application code doesn't change.

### Auth Service (`auth-service/src/main/resources/application.yml`)

```yaml
spring:
  application:
    name: auth-service
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://localhost:27017/auth_db}

server:
  port: 8080

grpc:
  server:
    port: 9090

jwt:
  secret: ${JWT_SECRET:dev-secret-at-least-32-chars-long-please}
  access-token-ttl-minutes: 15
  refresh-token-ttl-days: 30

---
spring.config.activate.on-profile: docker

spring.data.mongodb.uri: mongodb://mongodb:27017/auth_db
# In docker-compose, the hostname "mongodb" resolves to the mongodb container
```

### App Service (`app-service/src/main/resources/application.yml`)

```yaml
spring:
  application:
    name: app-service
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://localhost:27017/app_db}

server:
  port: 8080

grpc:
  client:
    auth-service:
      address: 'static://localhost:9090'
      negotiationType: PLAINTEXT
    execution-service:
      address: 'static://localhost:9091'   # Note: different port for local
      negotiationType: PLAINTEXT

---
spring.config.activate.on-profile: docker

spring.data.mongodb.uri: mongodb://mongodb:27017/app_db

grpc.client.auth-service.address: 'static://auth-service:9090'
grpc.client.execution-service.address: 'static://execution-service:9090'
```

### Execution Service (`execution-service/src/main/resources/application.yml`)

```yaml
spring:
  application:
    name: execution-service
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/execution_db}
    username: ${DB_USER:execution}
    password: ${DB_PASS:dev-password-change-in-prod}
  jpa:
    hibernate:
      ddl-auto: validate   # Schema is managed by init.sql, not Hibernate
    properties:
      hibernate.dialect: org.hibernate.dialect.PostgreSQLDialect

server:
  port: 8081   # Different from app-service for local dev

grpc:
  server:
    port: 9091   # Different from auth-service for local dev

judge0:
  base-url: ${JUDGE0_BASE_URL:http://localhost:2358}
  webhook-base-url: ${WEBHOOK_BASE_URL:http://localhost:8081}

---
spring.config.activate.on-profile: docker

spring.datasource.url: jdbc:postgresql://execution-postgres:5432/execution_db
judge0.base-url: http://nginx-internal:8000
judge0.webhook-base-url: http://execution-service:8080
```

### Why the port differences in local mode

When running multiple Spring Boot services on your laptop without Docker, each one needs a unique port. So:

- Auth Service: HTTP 8080, gRPC 9090
- App Service: HTTP 8080 (conflicts! see below)
- Execution Service: HTTP 8081, gRPC 9091

In practice you can't run two services on port 8080 simultaneously without containers. Three options:
1. Run everything in Docker Compose locally — each container has its own network namespace, all use 8080 internally, only Gateway is exposed
2. Manually configure each service to use a unique port locally (App on 8080, Execution on 8081, Auth on 8082, Gateway on 8090)
3. Use Docker Compose for everything but run the one service you're actively developing natively

I recommend **option 3** — Docker Compose runs Mongo, Postgres, Judge0, and the services you're not actively editing. You natively run the one service you're tweaking. Spring Boot DevTools handles hot-reload. This is the fastest dev loop.

---

## 7. GCP VM 1 Deployment (When We're Ready)

You confirmed Path 1 — local for dev, deploy to GCP for the final demo. This section explains how the same setup translates to VM 1.

### The deployment structure

When you deploy to GCP VM 1, you copy the same `docker-compose.app.yml` (defined in the devops artifact §4.8) up to the VM. The compose file references the same Mongo and Postgres images. The only differences are:

1. **Storage location.** Instead of Docker volumes on your laptop, the volumes live on VM 1's persistent disk. Docker volumes work the same way; they just sit on a different physical disk.

2. **Network exposure.** On your laptop, ports 27017 and 5432 are bound to `localhost` so Compass and DBeaver can connect. On GCP, those ports are NOT exposed publicly — only the internal Docker network sees them. Spring Boot containers reach them by service name (`mongodb:27017`, `execution-postgres:5432`).

3. **Credentials.** The Postgres password lives in a GCP Secret Manager secret on the VM, mounted into the container as an env var. The placeholder `dev-password-change-in-prod` from local becomes a real secret in production.

### Persistent disk basics

Each GCP VM has a boot disk (where the OS lives) and can have additional persistent disks. For our scope, the boot disk is enough — Docker volumes live there.

The persistent disk:
- **Survives VM stops and restarts.** Stop the VM, start it back up, your databases are still there.
- **Does NOT survive VM deletion** unless you snapshot it first.
- **Can be snapshotted** for backups: `gcloud compute disks snapshot codearena-vm1 --zone=asia-south1-a`

For the demo, after recording, the teardown checklist deletes the VM (and with it the disk). If you wanted to preserve data, you'd snapshot first. We don't bother for a demo.

### What changes in the docker-compose for GCP

The full GCP-side compose is in the devops artifact §4.8. The Mongo and Postgres services in that file look like:

```yaml
mongodb:
  image: mongo:7
  container_name: codearena-mongodb
  volumes:
    - mongodb-data:/data/db
  # No "ports" section — internal access only
  restart: unless-stopped

execution-postgres:
  image: postgres:16
  container_name: codearena-execution-postgres
  volumes:
    - execution-postgres-data:/var/lib/postgresql/data
    - ./postgres-init.sql:/docker-entrypoint-initdb.d/init.sql:ro
  environment:
    POSTGRES_USER: execution
    POSTGRES_PASSWORD: ${POSTGRES_EXECUTION_PASS}   # From GCP Secret Manager
    POSTGRES_DB: execution_db
  # No "ports" section — internal access only
  restart: unless-stopped

volumes:
  mongodb-data:
  execution-postgres-data:
```

Notice the differences from local:
- No `ports:` section — the databases aren't reachable from outside the VM, only from other containers on the same Docker network
- Postgres password from environment variable, sourced from a real secret
- No healthcheck shown here for brevity (the actual file in devops §4.8 has them)

### Inspecting databases on the GCP VM

Since the ports aren't exposed publicly, you can't connect Compass or DBeaver from your laptop directly. Two options:

**Option 1 — IAP tunnel for inspection:**
```bash
# Open a tunnel from your laptop to VM 1's Mongo port
gcloud compute start-iap-tunnel codearena-vm1 27017 \
  --local-host-port=localhost:27017 \
  --zone=asia-south1-a

# In another terminal, Compass connects to localhost:27017 as if it were local
```

This works but feels heavyweight for occasional inspection.

**Option 2 — exec into the container directly:**
```bash
# SSH into VM 1
gcloud compute ssh codearena-vm1 --zone=asia-south1-a --tunnel-through-iap

# Then on the VM:
docker exec -it codearena-mongodb mongosh
docker exec -it codearena-execution-postgres psql -U execution -d execution_db
```

For demo-grade inspection, Option 2 is enough. For sustained use, Option 1 is friendlier.

### What we're NOT doing — and why mentioning it on camera matters

For a real production system, you would not run Mongo and Postgres in Docker containers on a single VM. You'd use:

- **Cloud SQL for Postgres** — managed Postgres with automatic backups, automatic minor version upgrades, point-in-time recovery, high-availability replicas
- **MongoDB Atlas on GCP** — managed Mongo with the same managed-service benefits

We don't use these because:
1. They cost money even idle (~₹3000-5000/month minimum)
2. The demo gets torn down after recording
3. The architectural patterns we're teaching (gRPC, microservices, Judge0 multi-node) are independent of where the database lives

When viewers ask on camera: *"In production we'd use Cloud SQL and MongoDB Atlas. The application code wouldn't change — only the connection strings. Self-hosted Docker volumes are the right choice for a tear-down demo, not a real product."*

That's a senior-engineer answer. Acknowledges the limitation, explains the reasoning, points to the production upgrade path.

---

## 8. Common Pitfalls & Recovery

### "I deleted my volume and lost data"

Recovery: there isn't any. Docker volumes don't have built-in undelete. For dev data this is fine — re-seed and move on. This is why backups exist in production.

For a demo project, you can mitigate by occasionally exporting:

```bash
# Mongo dump
docker exec codearena-mongodb mongodump --out=/tmp/dump --db=app_db
docker cp codearena-mongodb:/tmp/dump ./mongo-backups/

# Postgres dump
docker exec codearena-execution-postgres pg_dump -U execution execution_db > ./postgres-backup.sql
```

But honestly — for our scope, just don't run `docker compose down -v` carelessly.

### "Mongo is rejecting connections / connection refused"

Diagnostic order:
1. Is the container running? `docker ps | grep mongo`
2. Is it healthy? `docker compose ps` — look for "healthy" status
3. Are you using the right hostname? `localhost` from the host, `mongodb` from inside another container
4. Are you using the right port? 27017
5. Network mode mismatch? Make sure your Spring Boot container is on the same Docker network

### "Postgres complains about init.sql syntax"

The `init.sql` only runs **on first startup of an empty volume**. If you've started Postgres once before, the second startup ignores `init.sql`. To force re-run:

```bash
docker compose down -v   # Delete volume
docker compose up -d execution-postgres   # Fresh start, init.sql runs
```

This catches a lot of people. Edits to `init.sql` after first startup are silently ignored — you must wipe the volume to apply them.

### "How do I see what's in my database right now?"

Quickest paths:

```bash
# Mongo — count documents in each collection
docker exec codearena-mongodb mongosh --quiet --eval '
  db.getSiblingDB("auth_db").users.countDocuments();
  db.getSiblingDB("app_db").submissions.countDocuments();
  db.getSiblingDB("app_db").problems.countDocuments();
'

# Postgres — count rows
docker exec codearena-execution-postgres psql -U execution -d execution_db -c '
  SELECT COUNT(*) FROM executions;
  SELECT COUNT(*) FROM execution_languages;
'
```

For deep inspection, use Compass and DBeaver as described earlier.

### "Spring Boot can't connect to my database"

99% of the time, one of these:

1. **Wrong host.** From outside Docker, use `localhost`. From inside another Docker container on the same compose network, use the service name (`mongodb`, `execution-postgres`).
2. **Wrong port.** Mongo is 27017, Postgres is 5432. Don't typo these.
3. **Database container not started yet.** Spring Boot starts faster than you think. Add `depends_on` with healthchecks in compose so Spring waits for the database to be ready.
4. **Credentials mismatch.** Check that `application.yml`'s default password matches what's in the docker-compose env vars.

Read the full Spring Boot exception. Spring Boot's Mongo and Postgres connection failures are usually clearly labeled — "Connection refused" means the host or port is wrong; "authentication failed" means credentials.

---

## 9. Quick Reference Card

| Task | Command |
|---|---|
| Start local databases only | `docker compose up -d mongodb execution-postgres` |
| Stop databases (keep data) | `docker compose down` |
| Stop and wipe everything | `docker compose down -v` |
| Connect to Mongo CLI | `docker exec -it codearena-mongodb mongosh` |
| Connect to Postgres CLI | `docker exec -it codearena-execution-postgres psql -U execution -d execution_db` |
| Seed sample problems | `docker exec -i codearena-mongodb mongosh app_db < infra/scripts/seed-problems.js` |
| View Mongo data via GUI | Compass → `mongodb://localhost:27017` |
| View Postgres data via GUI | DBeaver → `localhost:5432`, db `execution_db`, user `execution` |
| Tail database logs | `docker compose logs -f mongodb execution-postgres` |
| Mongo backup | `docker exec codearena-mongodb mongodump --out=/tmp/dump` |
| Postgres backup | `docker exec codearena-execution-postgres pg_dump -U execution execution_db > backup.sql` |

---

## 10. Reference

This is the databases artifact. See also:
- **Architecture artifact** (`judge0-architecture`) — the "what and why" overview
- **Contracts artifact** (`judge0-contracts`) §4 — full schema definitions for all collections and tables
- **Roadmap artifact** (`judge0-roadmap`) Phase 1, 3, 4 — when each database gets used
- **DevOps artifact** (`judge0-devops`) §4.8 — full GCP-side docker-compose
- **Concepts artifact** (`judge0-concepts`) Part 1, 2 — relational vs document databases, polyglot persistence
