# CodeArena — Implementation Roadmap
*Hour-by-hour breakdown of the 10-15 hour build. Reference this when filming each session — every hour has a clear deliverable, a checkpoint, and a "what to demonstrate on camera" note.*

---

## How to read this document

Each phase has:
- **Time budget** — realistic estimate, not aspirational
- **Git branch** — the branch you'll work on (see DevOps artifact for full strategy)
- **Deliverable** — what exists at the end that didn't exist at the start
- **Checkpoint** — a manual verification step before moving on (this is the "did it work?" question for the video)
- **Claude Code prompt hint** — what to ask Claude Code to do in this phase
- **What to film** — the key teaching moment for the video

**Phases are ordered by dependency, not by what's most fun.** Resist the urge to jump ahead to the frontend — if Auth doesn't work, nothing works.

---

## PHASE 0 — Project Setup & Architecture Walkthrough (~1 hour)

**Branch:** `main` (initial commit only)

**Deliverable:**
- Empty monorepo with the directory structure from the contracts artifact
- Parent `pom.xml` with shared dependency management
- `proto/` directory with the three proto files
- `.gitignore`, `README.md` skeleton, `Makefile` skeleton
- GitHub repo created, remote pushed
- Branch protection enabled on `main` (no direct pushes, PR required)

**Checkpoint:**
- `git clone` from a fresh machine, run `tree -L 2` — output matches the structure in contracts artifact
- `mvn validate` from the root succeeds (parent POM is valid)

**Claude Code prompt hint:**
> "I'm starting the CodeArena project. Read the architecture and contracts artifacts. Generate the monorepo skeleton: directory structure, parent pom.xml, proto files exactly as specified, .gitignore (Java + Node + IntelliJ), Makefile with placeholders for `make dev`, `make test`, `make seed`, `make teardown`, and a README with the topology diagram from the architecture artifact."

**What to film:**
- Open the architecture artifact, walk the viewer through the topology diagram (5 min)
- Show the proto files, explain why these specific RPCs (5 min)
- Run `tree`, show the empty skeleton, explain what each directory will hold (5 min)
- Push to GitHub, enable branch protection on camera — show this isn't a toy project (3 min)

---

## PHASE 1 — Auth Service (~1.5 hours)

**Branch:** `feature/auth-service`

**Deliverable:**
- Spring Boot 3.3 application with MongoDB
- REST endpoints: `/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/me`
- gRPC server exposing `AuthInternal.ValidateToken` and `AuthInternal.GetUser`
- BCrypt password hashing
- JWT generation (HS256, 15min access + 30day refresh)
- `RefreshToken` collection with TTL index
- `@ControllerAdvice` for RFC 7807 error responses
- Dockerfile

**Checkpoint:**
- `docker compose up auth-service mongodb` runs cleanly
- HTTP file `http/auth-flow.http` runs end-to-end:
  - Register → 201 with tokens
  - Login with same credentials → 200 with new tokens
  - Refresh → 200 with new access token
  - `/auth/me` with access token → 200 with user
  - Login with wrong password → 401
- gRPC test: a small standalone Java client calls `ValidateToken` with a real JWT, gets back valid=true

**Claude Code prompt hint:**
> "Implement the Auth Service per the contracts artifact §3.1 (REST endpoints), §2.2 (auth.proto gRPC), §4.1 (MongoDB schema), §5 (JWT spec), §6 (error responses). Use Spring Boot 3.3.5, Java 21, MongoDB driver, BCrypt, jjwt for JWT, net.devh:grpc-spring-boot-starter 3.1.0 for gRPC. Generate: pom.xml, application.yml (with profiles for local and docker), all controllers, services, repositories, entities, gRPC service implementation, exception handler, Dockerfile. Skip tests for now — we'll add them in Phase 7."

**What to film:**
- The auth flow diagram from architecture §5 — walk it slowly (5 min)
- Why we hash refresh tokens but not access tokens (3 min)
- The MongoDB TTL index — show it auto-deleting expired tokens (3 min)
- gRPC vs REST: hit the same logic via both, show the proto-generated client (5 min)
- Run the full http/auth-flow.http, narrate each request (10 min)

---

## PHASE 2 — Judge0 Setup & Manual Validation (~1.5 hours)

**Branch:** `feature/judge0-local`

**This phase doesn't write Spring Boot code.** It's pure infrastructure validation. Skipping it means you'll discover Judge0 problems while debugging Spring Boot, which doubles debugging time.

### Important: where does Judge0 actually run during dev?

**Decision point — pick one path:**

**Path A (recommended for Fedora dev hosts): Judge0 runs only on GCP, accessed via IAP tunnel during local dev.**
- Pro: no GRUB edit on your daily-driver Fedora machine, no reboot dance, dev environment matches prod exactly
- Pro: forces the SSH-tunnel-to-cloud-resource pattern, which is itself a senior-engineer skill worth filming
- Con: requires GCP VM 2 provisioned earlier than Phase 8 (do a slimmed-down provision now, expand later)
- Con: any local dev requires the tunnel to be active

**Path B: Judge0 runs locally on your Fedora host.**
- Pro: no cloud dependency during dev
- Con: requires the cgroup v1 GRUB edit on your dev machine + SELinux permissive (see devops artifact §7)
- Con: rebooting your daily driver into cgroup v1 mode for one project is annoying

**Path C: Judge0 runs locally inside a Linux VM (VirtualBox/UTM/multipass) with cgroup v1 + SELinux permissive.**
- Pro: keeps your Fedora host untouched
- Con: extra VM layer eats RAM, adds friction
- Con: nested virtualization can be flaky

**My recommendation: Path A.** Provision VM 2 from Phase 8 *now* (just one VM, ~₹50/day), keep it running through Phases 2-7, expand to the full 3-VM topology in Phase 8. This is exactly what real teams do — they don't run heavy infrastructure on laptops.

**Deliverable:**

For Path A (recommended):
- Slimmed-down VM 2 provisioned on GCP (Ubuntu 22.04, e2-medium) with Judge0 running
- Cgroup v1 fix applied on VM 2 (per devops artifact §4.3)
- IAP tunnel command documented in `README.md` for local dev access
- HTTP file `http/judge0-direct.http` with manual Judge0 API calls (targets `http://localhost:2358` after tunnel is open)
- A documented list of language IDs your platform will support

For Path B/C:
- Local Docker Compose for Judge0 (single-node) at `infra/docker/docker-compose.judge0-local.yml`
- Host machine (or VM) configured with cgroup v1 + SELinux permissive per devops artifact §7
- HTTP file as above

**Checkpoint:**

For Path A:
- `gcloud compute start-iap-tunnel codearena-vm2 2358 --local-host-port=localhost:2358 --zone=asia-south1-a` opens tunnel
- `GET http://localhost:2358/about` returns Judge0 metadata
- `POST http://localhost:2358/submissions?wait=true` with Python hello-world returns stdout
- `POST` without `wait=true`, then `GET /submissions/{token}` returns the same result asynchronously

For Path B/C:
- Same checkpoints, but Judge0 runs on `localhost:2358` directly via Docker.

**Claude Code prompt hint (Path A — recommended):**
> "We're using Path A from Phase 2: Judge0 will run on a slimmed-down GCP VM 2 (Ubuntu 22.04, e2-medium, asia-south1-a), accessed locally via IAP tunnel. Generate: (1) a `infra/gcp/provision-vm2-early.sh` script that creates only VM 2 with the network/firewall/cgroup-v1-fix done in startup-script, idempotent so re-running is safe; (2) `infra/docker/docker-compose.judge0.yml` for VM 2 (Judge0 server + workers + its internal Postgres + its internal Redis, all colocated since this is single-node for now — we'll split into stateful/stateless in Phase 8); (3) `http/judge0-direct.http` with example calls assuming `localhost:2358` (IAP tunnel will be open); (4) a `Makefile` target `make judge0-tunnel` that opens the IAP tunnel, and `make judge0-deploy` that scps the compose file and runs it on VM 2. Don't include callback_url examples yet — those need a webhook receiver."

**Claude Code prompt hint (Path B — Fedora host):**
> "We're using Path B from Phase 2: Judge0 runs on the local Fedora host. Pre-flight has already been done (SELinux permissive, cgroup v1 GRUB edit, reboot). Generate: (1) `infra/docker/docker-compose.judge0-local.yml` running Judge0 server + workers + Postgres + Redis on the host, exposed at localhost:2358; (2) `http/judge0-direct.http` with example calls; (3) a `Makefile` target `make judge0-local-up`. Verify `getenforce` returns Permissive and `mount | grep cgroup | head -1` shows v1 mounts before bringing up the stack."

**What to film:**

For Path A:
- **The "develop against cloud resources via IAP" pattern** — this is genuinely high-value content most YouTube tutorials skip. Show the tunnel command, show the `http://localhost:2358` calls actually hitting GCP. (5 min)
- **The cgroup v1 explanation** — show `/etc/default/grub` on VM 2 via SSH, explain why modern Linux defaults to v2, why Judge0 needs v1. This is one of the most valuable 5 minutes in the series. (5 min)
- Walk through `judge0.conf` — every meaningful setting (3 min)
- Hit Judge0 via the tunnel, show the queued → completed lifecycle (3 min)
- Show the Judge0 dashboard at `:2358` (it has a built-in UI for monitoring submissions) (2 min)

For Path B (if you went that way):
- Same as above but show the GRUB edit + reboot on your local Fedora dev machine instead (riskier filming — make sure you've rehearsed the recovery if reboot fails)

---

## PHASE 3 — Execution Service Core (~2 hours)

**Branch:** `feature/execution-service`

**Deliverable:**
- Spring Boot 3.3 with Postgres
- gRPC server exposing all four `ExecutionService` RPCs
- HTTP webhook endpoint `PUT /webhook/judge0/{token}`
- Judge0 client (RestTemplate or WebClient) wrapping the Judge0 REST API
- Language sync job that pulls from Judge0 on startup and refreshes daily
- Recovery poller (scheduled every 30s) that polls Judge0 for any execution still PENDING > 30s
- Postgres schema initialized via `init.sql` mounted in the container
- Dockerfile

**Checkpoint:**
- `docker compose up execution-service postgres judge0-server judge0-workers judge0-db judge0-redis` runs cleanly
- gRPC test: standalone Java client calls `SubmitCode` with a Python hello-world, gets back a token
- Wait 1-2s, call `GetExecutionResult(token)` — returns ACCEPTED with stdout
- Webhook test: submit with `callback_url` set, watch logs show webhook received and DB updated
- Recovery test: submit, kill the webhook listener temporarily, restart, recovery poller catches up

**Claude Code prompt hint:**
> "Implement the Execution Service per the contracts artifact §2.3 (execution.proto gRPC), §3.3 (webhook endpoint), §4.3 (Postgres schema). Spring Boot 3.3.5, Java 21, Spring Data JPA with Hibernate, Postgres driver, net.devh:grpc-spring-boot-starter, WebClient for Judge0 calls. Generate: pom.xml, application.yml with profiles, JPA entities, repositories, services (Judge0Client, ExecutionService, LanguageSyncService, RecoveryPollerService), gRPC implementation, webhook controller, Postgres init.sql at /infra/docker/postgres-init.sql, Dockerfile. Source code is hashed (SHA-256) before storing in Postgres — full source stays in Mongo (App Service)."

**What to film:**
- The Judge0 client code — explain timeout config, retry policy (5 min)
- The webhook handler — explain idempotency by token (5 min)
- The recovery poller — explain "webhook is happy path, polling is recovery" pattern (5 min)
- gRPC end-to-end: submit via gRPC, watch DB update via Postgres CLI, see webhook log line (10 min)
- **The senior engineer moment:** explain why `source_code_hash` instead of full source in Postgres (3 min)

---

## PHASE 4 — App Service (~2 hours)

**Branch:** `feature/app-service`

**Deliverable:**
- Spring Boot 3.3 with MongoDB
- REST endpoints per contracts §3.2
- gRPC client to Execution Service
- gRPC client to Auth Service (for `GetUser` lookups)
- Submission lifecycle: receive submission → call Execution.SubmitCode → store with token → return 202
- Status polling endpoint that fetches latest from Execution via gRPC and updates Mongo
- Stats aggregation: when a submission completes, denormalized update to `user_stats`
- Problem seed script at `infra/scripts/seed-problems.js` with ~10 sample problems
- Dockerfile

**Checkpoint:**
- `docker compose up app-service execution-service auth-service mongodb postgres judge0-stack` runs cleanly
- HTTP file `http/submission-flow.http` runs end-to-end:
  - Login (gets JWT)
  - List problems → 200 with seeded data
  - Submit code → 202 with submissionId
  - Poll submission → eventually returns ACCEPTED
  - List submissions → includes the new one
  - Get stats → reflects the submission
- Log inspection shows the gRPC calls App → Execution and App → Auth

**Claude Code prompt hint:**
> "Implement the App Service per the contracts artifact §3.2 (REST endpoints), §4.2 (MongoDB schema), and using the gRPC clients defined by execution.proto and auth.proto. Spring Boot 3.3.5, Java 21, Spring Data MongoDB, net.devh:grpc-spring-boot-starter, Spring Validation. The flow: POST /submissions persists submission with status=PENDING, calls Execution.SubmitCode via gRPC, stores returned token, returns 202. GET /submissions/{id} fetches submission from Mongo, calls Execution.GetExecutionResult via gRPC for fresh data, updates Mongo if status changed, updates user_stats if newly completed. Generate: pom.xml, application.yml, controllers, services, repositories, MongoDB documents, gRPC client wrappers, Dockerfile, and seed-problems.js script for ~10 sample problems."

**What to film:**
- The submission flow end-to-end with all logs visible — this is the centerpiece demo (10 min)
- gRPC vs REST in the network: show that the App→Execution call doesn't go through the gateway (5 min)
- The denormalized `user_stats` decision — explain when denormalization is right (5 min)
- The X-User-Id trust boundary — show that App Service never validates JWT itself (5 min)

---

## PHASE 5 — API Gateway (~1 hour)

**Branch:** `feature/api-gateway`

**Deliverable:**
- Spring Cloud Gateway application
- JWT validation filter (HS256, shared secret with Auth Service)
- X-User-Id header injection on validated requests
- Routing rules: `/api/v1/auth/**` → Auth Service, `/api/v1/**` → App Service
- Public bypass for `/api/v1/auth/register`, `/api/v1/auth/login`, `/api/v1/auth/refresh`
- Redis-backed rate limiter (Spring Cloud Gateway's `RequestRateLimiter`)
- Correlation ID injection (`X-Correlation-Id`)
- Health check at `/health` that aggregates downstream service health
- Dockerfile

**Checkpoint:**
- `docker compose up api-gateway auth-service app-service execution-service mongodb postgres judge0-stack redis-gateway` runs cleanly
- All HTTP files now route through `http://localhost:8080/api/v1/...` (the gateway port)
- Hitting an authenticated endpoint without JWT → 401 from gateway, never reaches App Service
- Hitting register endpoint without JWT → succeeds (public bypass works)
- Spamming login → eventually returns 429 (rate limit works)
- Killing App Service, hitting `/health` → returns degraded status with details

**Claude Code prompt hint:**
> "Implement the API Gateway with Spring Cloud Gateway 2023.0.3, Spring Boot 3.3.5, Java 21. Configuration via application.yml (no Java DSL). Routes: /api/v1/auth/** to auth-service:8080, /api/v1/** to app-service:8080. Custom GlobalFilter for JWT validation: extract Bearer token, validate HS256 signature with shared secret, extract userId from claims, add as X-User-Id header to downstream request. Public bypass list for /auth/register, /auth/login, /auth/refresh, /actuator/health. Redis-backed RequestRateLimiter with two policies: 5/min per IP for auth endpoints, 60/min per user for the rest. Custom GlobalFilter for X-Correlation-Id (generate UUID if absent, propagate to MDC and downstream). Generate: pom.xml, application.yml, the two filters, Dockerfile. The Redis instance for rate limiting is separate from Judge0's Redis — name it 'gateway-redis' in compose."

**What to film:**
- Why Spring Cloud Gateway (reactive) is the right choice for this layer (3 min)
- The trust boundary explanation — JWT validated once, X-User-Id trusted thereafter (5 min)
- Rate limiting demo: spam login until 429 (3 min)
- Show the network: a request from frontend → gateway → service, with all headers visible (5 min)

---

## PHASE 6 — Frontend (Next.js 15) (~2.5 hours)

**Branch:** `feature/frontend`

**See the dedicated `judge0-frontend` artifact for full component spec.** This phase implements it.

**Deliverable:**
- Next.js 15 app with App Router, TypeScript, Tailwind CSS
- Pages:
  - `/` — landing (problem list)
  - `/login`, `/register` — auth pages
  - `/problems/[slug]` — problem detail with code editor
  - `/playground` — free-form code execution (no problem)
  - `/submissions` — history list
  - `/submissions/[id]` — single submission detail with execution output
  - `/me/stats` — user stats dashboard
- Components:
  - `<CodeEditor>` — Monaco-based, language-aware
  - `<LanguageSelector>` — dropdown populated from `/api/v1/languages`
  - `<ExecutionOutput>` — stdout/stderr/compile output panels
  - `<SubmissionStatusBadge>` — colored badge per status
  - `<StdinInput>` — collapsible custom stdin
- API client (`lib/api.ts`) wrapping all backend calls
- Auth context with JWT in memory + refresh token in httpOnly cookie
- Protected routes via middleware
- Dockerfile (multi-stage build)

**Checkpoint:**
- `npm run dev` runs the frontend at `localhost:3000`
- Full user journey works through the UI:
  - Register → redirected to playground
  - Write Python code, hit Run → see ACCEPTED output
  - Switch to Java, write equivalent code, run → see ACCEPTED
  - Go to history, see both submissions
  - Click a submission, see full detail
  - Go to stats, see counts and avg times by language
- Polling works: status changes from PENDING to ACCEPTED visibly
- Wrong code (`print(1/0)`) shows RUNTIME_ERROR with stderr
- Bad syntax (`prit("hi")`) shows COMPILATION_ERROR with compile_output

**Claude Code prompt hint:**
> "Implement the Next.js 15 frontend per the judge0-frontend artifact. Use App Router, TypeScript strict mode, Tailwind CSS 4, @monaco-editor/react for the code editor, lucide-react for icons, no other UI libraries. State management: React Context for auth (no Redux/Zustand). API client at lib/api.ts using fetch with automatic refresh-token retry on 401. Polling: custom hook usePollSubmission(id) that polls every 1s while status is PENDING. Generate every page, every component, the API client, the auth context, middleware for protected routes, Dockerfile (multi-stage: build stage with npm ci + npm run build, runtime stage with nginx serving the static export — wait, use Next.js standalone output instead since we have API routes). Tailwind config, package.json with exact dep versions."

**What to film:**
- Why Monaco (the editor that powers VS Code) (3 min)
- The polling hook — show the network tab, see requests every 1s, stop when status changes (5 min)
- The auth context — show JWT in memory, refresh token in cookie, the silent refresh flow (5 min)
- Full demo: register → playground → submit Python → ACCEPTED → switch to Java → submit → ACCEPTED → check stats (10 min)

---

## PHASE 7 — Local End-to-End Validation (~0.5 hour)

**Branch:** `feature/e2e-local`

**Deliverable:**
- Master `docker-compose.local.yml` at repo root that brings up everything in correct dependency order
- `Makefile` targets: `make dev` (starts everything), `make logs`, `make seed`, `make teardown`
- `http/full-e2e-flow.http` — single file that runs every endpoint in sequence
- README updated with "Quick Start" section

**Checkpoint:**
- Fresh clone on a new machine: `make dev` brings up the entire stack
- `make seed` populates problems
- `http/full-e2e-flow.http` runs without errors
- Frontend at `:3000` is fully functional

**Claude Code prompt hint:**
> "Generate the master docker-compose.local.yml at the repo root that orchestrates: gateway-redis, auth-mongodb, app-mongodb, execution-postgres, judge0-redis, judge0-postgres, judge0-server, judge0-workers, auth-service, app-service, execution-service, api-gateway, frontend. Use depends_on with healthchecks (every service has /actuator/health on Spring services, the others use their native healthchecks). Networks: one bridge network 'codearena-net'. Volumes for the three databases. Generate the complete Makefile with targets: dev, logs, seed, test, teardown, clean. Update README with the Quick Start section."

**What to film:**
- This is the "victory lap" segment. Run `make dev` from a fresh clone, watch everything come up, hit the frontend, do a full submission. (10 min)
- This is also where you record the **architecture video intro shot** — overlay the topology diagram on the running stack.

---

## PHASE 8 — GCP Deployment (~2 hours)

**Branch:** `feature/gcp-deploy`

**See the dedicated `judge0-devops` artifact for the full deployment guide.** This phase executes it.

**Deliverable:**
- 3 GCP VMs provisioned (VM 1: app, VM 2: judge0 stateful, VM 3: judge0 stateless)
- Cgroup v1 fix applied and verified on VM 2 and VM 3
- Docker Compose deployed on each VM
- Internal Nginx on VM 1 load-balancing between VM 2 and VM 3 with `least_conn`
- Public Nginx on VM 1 with TLS via Let's Encrypt (or self-signed for demo)
- Firewall rules: VM 1 public on 80/443, VM 2 + VM 3 only reachable from VM 1
- Domain pointed at VM 1 (or use VM 1's public IP for demo)

**Checkpoint:**
- `https://<your-domain-or-ip>` loads the frontend
- Full user journey works through the deployed system
- SSH into VM 3, `docker compose stop`, submit code from frontend → still works (VM 2 picks it up)
- Restart VM 3, watch Nginx pick it back up via health check

**Claude Code prompt hint:**
> "Generate the GCP deployment scripts per the judge0-devops artifact: provision-vms.sh (gcloud commands for 3 VMs in asia-south1, e2-standard-2 for VM 1 and e2-medium for VM 2/3, Ubuntu 22.04 LTS), cgroup-v1-fix.sh (the GRUB modification), the three docker-compose files for the three VMs, nginx-public.conf (TLS termination, reverse proxy to Spring Cloud Gateway, static frontend serving), nginx-internal.conf (least_conn LB to Judge0 nodes with /about health check). Provide the exact firewall rules as gcloud commands."

**What to film:**
- **The cgroup fix on the actual VMs** — this is the most valuable infrastructure moment in the series (10 min)
- The 3-VM topology in the GCP console (5 min)
- Internal vs public Nginx — show both configs side by side (5 min)
- **The kill-VM-3 demo** — ssh in, stop Judge0, submit code, watch it succeed via VM 2, restart (5 min)

---

## PHASE 9 — GitHub Actions CI/CD (~1.5 hours)

**Branch:** `feature/cicd`

**See the dedicated `judge0-devops` artifact for full workflow specs.** This phase implements them.

**Deliverable:**
- Reusable workflow `_reusable-java-build.yml` (build, test, build Docker image, push to GCP Artifact Registry)
- Per-service workflows that use the reusable one
- Frontend workflow (build, test, build Docker image, push)
- `deploy-gcp.yml` workflow (manual trigger, SSHs to VMs and runs `docker compose pull && up -d`)
- GCP service account + Workload Identity Federation configured for OIDC auth from GitHub
- Branch protection on `main`: requires CI green + PR review

**Checkpoint:**
- Push to a feature branch → CI runs, builds, tests
- Open PR to `main` → CI runs again, status checks visible
- Merge to `main` → CI runs, images pushed to Artifact Registry
- Manual trigger of `deploy-gcp` → SSHs to VMs, pulls new images, restarts services
- `https://<domain>` reflects the deployed change

**Claude Code prompt hint:**
> "Generate the GitHub Actions workflows per the judge0-devops artifact §5. Reusable Java build workflow that takes service-name as input and runs: setup-java action with Java 21 (Temurin), Maven cache, mvn -pl <service-name> -am clean verify, build Docker image, push to asia-south1-docker.pkg.dev/<project-id>/codearena/<service-name>:${{ github.sha }} via OIDC auth (use google-github-actions/auth). Per-service workflows triggered on push paths. Frontend workflow with Node 20, npm ci, npm run build, npm test, Docker build/push. deploy-gcp workflow with workflow_dispatch input for environment, SSHs to VMs using GCP IAP tunnel, runs docker compose pull and up -d. Include all the gcloud commands needed for the OIDC setup as a separate setup-oidc.sh script."

**What to film:**
- OIDC vs long-lived service account keys — explain why OIDC is the modern answer (5 min)
- The reusable workflow pattern — DRY in CI (3 min)
- Push-to-deploy demo: change a string in App Service, commit, push, watch CI green, manual deploy, see the change live (10 min)

---

## PHASE 10 — Demo Recording & Final Polish (~1 hour)

**Branch:** `main` (no new code, just rehearsal)

**Deliverable:**
- A clean recording of the full demo, ~10-15 min
- README finalized with: architecture diagram, tech stack badges, Quick Start, deployment instructions, screenshots/GIFs
- LinkedIn / portfolio post draft

**Demo script (rehearse this):**

1. **Architecture intro** (2 min) — overlay topology diagram, narrate the four services and Judge0 cluster
2. **Live system tour** (3 min) — register account, submit Python code, see ACCEPTED, switch to Java, submit, see history, see stats
3. **The horizontal scaling moment** (2 min) — split-screen: GCP console showing 3 VMs, terminal SSH'd into VM 3, frontend ready to submit. Stop VM 3's Judge0, submit code, watch it succeed via VM 2. Restart VM 3.
4. **Code highlights** (3 min) — show the gRPC proto file, show the Spring Cloud Gateway JWT filter, show the Judge0 webhook handler
5. **CI/CD moment** (2 min) — make a small change, push, watch CI green, deploy
6. **Wrap** (1 min) — what was deliberately cut and why, what you'd add for production, link to repo

**Teardown checklist (CRITICAL — do this immediately after recording):**

- [ ] Snapshot VM 1's databases for archival (one-time storage cost is negligible)
- [ ] Stop all 3 VMs
- [ ] Delete all 3 VMs
- [ ] Delete persistent disks
- [ ] Delete Artifact Registry repo (or keep — small storage cost)
- [ ] Delete static IP if reserved
- [ ] Delete DNS records
- [ ] Verify GCP billing dashboard shows no running compute
- [ ] Set a billing alert at $5 for the next month as a safety net

**What to film:** Just the demo itself. Phase 10 isn't really a phase — it's the recording session.

---

## Total Time Audit

| Phase | Budget | Cumulative |
|---|---|---|
| 0. Setup | 1.0h | 1.0h |
| 1. Auth Service | 1.5h | 2.5h |
| 2. Judge0 Local | 1.5h | 4.0h |
| 3. Execution Service | 2.0h | 6.0h |
| 4. App Service | 2.0h | 8.0h |
| 5. API Gateway | 1.0h | 9.0h |
| 6. Frontend | 2.5h | 11.5h |
| 7. E2E Local | 0.5h | 12.0h |
| 8. GCP Deploy | 2.0h | 14.0h |
| 9. CI/CD | 1.5h | 15.5h |
| 10. Demo | 1.0h | 16.5h |

**16.5h total budget** — slightly over the 10-15h target, with realistic buffer. Phases 7 and 10 have the most slack and can be compressed if you're behind.

**If you're falling behind by Phase 5, cut these in this order:**
1. **CI/CD (Phase 9)** — most painful cut, but the project still works without it. Mention it as "future work" on camera.
2. **Recovery poller in Execution Service** — keep webhook only, document the gap.
3. **Stats dashboard page in frontend** — keep stats endpoint, just don't render it.
4. **Refresh token flow** — use long-lived access tokens for demo only.

Do NOT cut: Auth, the core submission flow, Judge0 multi-node, the cgroup fix, the GCP deployment.

---

## Branching Strategy (Quick Reference)

Full strategy in the DevOps artifact. Quick version:

- `main` — protected, only updated via PR
- `develop` — integration branch (optional for solo work; you can skip and PR feature branches directly to main)
- `feature/<phase-name>` — one branch per phase
- Each PR must: pass CI, have a meaningful description, reference the phase

**Why this matters for the video:** Showing meaningful Git history with descriptive commits and PRs is itself a teaching moment. Don't squash everything into one commit.

---

## Reference

This is artifact 3 of 5. See:
- **Artifact 1:** `judge0-architecture` — the "what and why"
- **Artifact 2:** `judge0-contracts` — gRPC protos, REST endpoints, schemas
- **Artifact 4:** `judge0-frontend` — Next.js 15 spec
- **Artifact 5:** `judge0-devops` — Git, GitHub Actions, GCP, demo
