# CodeArena — API & gRPC Contracts
*The contract specification document. Paste this into Claude Code when implementing any service — it has the exact endpoint shapes, gRPC proto definitions, and DB schemas you'll be coding against.*

---

## 1. Repository Structure

Single monorepo. Same reasoning as Nexastore — proto sharing alone justifies it.

```
codearena/
├── .github/
│   └── workflows/
│       ├── _reusable-java-build.yml
│       ├── api-gateway.yml
│       ├── auth-service.yml
│       ├── app-service.yml
│       ├── execution-service.yml
│       ├── frontend.yml
│       ├── proto-publish.yml
│       └── deploy-gcp.yml
│
├── proto/
│   ├── auth.proto
│   ├── execution.proto
│   ├── common.proto
│   └── README.md
│
├── api-gateway/                     # Spring Cloud Gateway
├── auth-service/                    # Spring Boot 3.3 / Java 21 / MongoDB
├── app-service/                     # Spring Boot 3.3 / Java 21 / MongoDB
├── execution-service/               # Spring Boot 3.3 / Java 21 / Postgres
├── frontend/                        # Next.js 15 / TypeScript
│
├── infra/
│   ├── docker/
│   │   ├── docker-compose.app.yml      # VM 1 stack
│   │   ├── docker-compose.judge0-a.yml # VM 2 stack (stateful)
│   │   ├── docker-compose.judge0-b.yml # VM 3 stack (stateless)
│   │   ├── nginx-public.conf           # public ingress on VM 1
│   │   ├── nginx-internal.conf         # judge0 LB on VM 1
│   │   └── postgres-init.sql
│   ├── gcp/
│   │   ├── provision-vms.sh
│   │   └── teardown.sh
│   └── scripts/
│       ├── cgroup-v1-fix.sh
│       └── seed-problems.js
│
├── http/                            # IntelliJ HTTP Client files
│   ├── auth-flow.http
│   ├── submission-flow.http
│   ├── full-e2e-flow.http
│   └── http-client.env.json
│
├── docker-compose.local.yml         # Local dev — everything on one machine
├── Makefile                         # `make dev`, `make test`, `make seed`
└── README.md
```

---

## 2. gRPC Proto Definitions

### 2.1 `proto/common.proto`

```protobuf
syntax = "proto3";

package codearena.common.v1;

option java_multiple_files = true;
option java_package = "dev.codearena.proto.common.v1";

message UserContext {
  string user_id = 1;
  string username = 2;
  string role = 3;
}

message Empty {}

message Error {
  int32 code = 1;
  string message = 2;
  string correlation_id = 3;
}
```

### 2.2 `proto/auth.proto`

```protobuf
syntax = "proto3";

package codearena.auth.v1;

import "common.proto";

option java_multiple_files = true;
option java_package = "dev.codearena.proto.auth.v1";

service AuthInternal {
  // Called by App Service and Execution Service to validate tokens
  // when they need to verify a user beyond what the gateway already did.
  // Most calls go through gateway-trusted X-User-Id headers; this is
  // for cases where deeper validation is needed (e.g., admin actions).
  rpc ValidateToken(ValidateTokenRequest) returns (ValidateTokenResponse);
  
  // Lightweight user lookup for cross-service display name resolution.
  rpc GetUser(GetUserRequest) returns (GetUserResponse);
}

message ValidateTokenRequest {
  string token = 1;
}

message ValidateTokenResponse {
  bool valid = 1;
  codearena.common.v1.UserContext user = 2;
  int64 expires_at_epoch_seconds = 3;
}

message GetUserRequest {
  string user_id = 1;
}

message GetUserResponse {
  bool found = 1;
  codearena.common.v1.UserContext user = 2;
}
```

### 2.3 `proto/execution.proto`

```protobuf
syntax = "proto3";

package codearena.execution.v1;

import "common.proto";

option java_multiple_files = true;
option java_package = "dev.codearena.proto.execution.v1";

service ExecutionService {
  // Submit code for execution. Returns immediately with a token;
  // the actual execution happens async and result is fetched later
  // (or pushed via NotifyExecutionCompleted callback to App Service).
  rpc SubmitCode(SubmitCodeRequest) returns (SubmitCodeResponse);
  
  // Poll for execution result by token.
  rpc GetExecutionResult(GetExecutionResultRequest) returns (GetExecutionResultResponse);
  
  // Batch fetch results for history view (App Service uses this to hydrate
  // a list of submissions with their execution data).
  rpc GetExecutionResultsBatch(GetExecutionResultsBatchRequest) returns (GetExecutionResultsBatchResponse);
  
  // List supported languages (cached from Judge0).
  rpc ListLanguages(codearena.common.v1.Empty) returns (ListLanguagesResponse);
}

message SubmitCodeRequest {
  string user_id = 1;
  string submission_id = 2;        // Mongo ObjectId from App Service, optional
  int32 language_id = 3;
  string source_code = 4;
  string stdin = 5;
  string expected_output = 6;       // optional, used for ACCEPTED/WRONG_ANSWER comparison
  double cpu_time_limit_seconds = 7;
  int32 memory_limit_kb = 8;
}

message SubmitCodeResponse {
  string execution_token = 1;       // Judge0's token, opaque to App Service
  string status = 2;                // "QUEUED" on submission
}

message GetExecutionResultRequest {
  string execution_token = 1;
  string requesting_user_id = 2;    // for ownership check
}

message GetExecutionResultResponse {
  bool found = 1;
  ExecutionResult result = 2;
}

message GetExecutionResultsBatchRequest {
  repeated string execution_tokens = 1;
  string requesting_user_id = 2;
}

message GetExecutionResultsBatchResponse {
  repeated ExecutionResult results = 1;
}

message ExecutionResult {
  string execution_token = 1;
  string status = 2;                // "PENDING" | "ACCEPTED" | "WRONG_ANSWER" | "TIME_LIMIT_EXCEEDED" | "COMPILATION_ERROR" | "RUNTIME_ERROR"
  string status_description = 3;
  string stdout = 4;
  string stderr = 5;
  string compile_output = 6;
  int32 time_ms = 7;
  int32 memory_kb = 8;
  int64 created_at_epoch_seconds = 9;
  int64 completed_at_epoch_seconds = 10;
}

message ListLanguagesResponse {
  repeated Language languages = 1;
}

message Language {
  int32 id = 1;
  string name = 2;
  bool is_active = 3;
}
```

### 2.4 Why these RPCs, and why no others

**`AuthInternal.ValidateToken`** — used by services for the rare case where they need to deeply validate a token (e.g., admin endpoint requiring fresh validation). **Most service calls bypass this** because the gateway already validated and forwarded `X-User-Id`. Including it shows the pattern without forcing every call through it.

**`AuthInternal.GetUser`** — used when App Service needs to display a username (e.g., on a leaderboard) for a user_id it doesn't have cached. Demonstrates the cross-service lookup pattern.

**`ExecutionService.SubmitCode`** — the headline gRPC call. This is the one that earns the gRPC choice — strongly typed, low-latency, the call you make every time a user submits code.

**`ExecutionService.GetExecutionResult`** — for polling. App Service calls this when the frontend asks "what's the status of submission X?"

**`ExecutionService.GetExecutionResultsBatch`** — for history pages. App Service has 50 submissions, needs execution data for all of them. Batching avoids N+1.

**`ExecutionService.ListLanguages`** — populates the language dropdown on the frontend.

**No `NotifyExecutionCompleted`** — App Service polls. We considered a reverse gRPC call (Execution → App) when execution completes, but it adds bidirectional dependency complexity for marginal benefit. If polling becomes a bottleneck, this is where it'd be added.

---

## 3. REST API Endpoints

All endpoints are exposed through the API Gateway at `https://<host>/api/v1/...`.

### 3.1 Auth Service Endpoints (via Gateway)

```
POST   /api/v1/auth/register
       Body: { email, username, password }
       Response: 201 { user, accessToken, refreshToken }

POST   /api/v1/auth/login
       Body: { emailOrUsername, password }
       Response: 200 { user, accessToken, refreshToken }

POST   /api/v1/auth/refresh
       Body: { refreshToken }
       Response: 200 { accessToken }

POST   /api/v1/auth/logout
       Header: Authorization: Bearer <token>
       Body: { refreshToken }
       Response: 204

GET    /api/v1/auth/me
       Header: Authorization: Bearer <token>
       Response: 200 { user }
```

### 3.2 App Service Endpoints (via Gateway)

```
GET    /api/v1/problems
       Response: 200 [{ id, slug, title, difficulty, supportedLanguages }]

GET    /api/v1/problems/{slug}
       Response: 200 { id, slug, title, description, difficulty, sampleTestCases, supportedLanguages }

GET    /api/v1/languages
       Response: 200 [{ id, name }]
       (proxied to Execution Service via gRPC)

POST   /api/v1/submissions
       Header: Authorization: Bearer <token>
       Body: {
         problemId: string | null,
         languageId: number,
         sourceCode: string,
         customStdin: string | null
       }
       Response: 202 { submissionId, status: "PENDING" }

GET    /api/v1/submissions/{id}
       Header: Authorization: Bearer <token>
       Response: 200 {
         id, problemId, languageId, sourceCode, customStdin,
         latestStatus, executions: [ExecutionResult],
         createdAt, updatedAt
       }

GET    /api/v1/submissions
       Header: Authorization: Bearer <token>
       Query: ?page=0&size=20&problemId=...
       Response: 200 { content: [Submission], page, size, total }

GET    /api/v1/users/me/stats
       Header: Authorization: Bearer <token>
       Response: 200 {
         totalSubmissions, acceptedSubmissions,
         byLanguage: { "71": { count, accepted, avgTimeMs }, ... }
       }
```

### 3.3 Execution Service Internal Endpoints (NOT exposed via Gateway)

```
PUT    /webhook/judge0/{token}
       (Called by Judge0 nodes — not authenticated, but firewall-restricted to VPC internal IPs)
       Body: { stdout, stderr, status_id, time, memory, ... }  (Judge0's payload)
       Response: 204

GET    /actuator/health
GET    /actuator/metrics
```

### 3.4 Public Health Check (via Gateway)

```
GET    /health
       Response: 200 { status: "UP", services: { auth, app, execution, judge0 } }
```

This is what GitHub Actions hits to verify deployment health.

---

## 4. Database Schemas — Final Form

### 4.1 MongoDB — `auth_db`

```javascript
// Collection: users
db.users.createIndex({ email: 1 }, { unique: true });
db.users.createIndex({ username: 1 }, { unique: true });

// Sample document
{
  _id: ObjectId("..."),
  email: "alice@example.com",
  username: "alice",
  passwordHash: "$2a$12$...",
  role: "USER",
  createdAt: ISODate("2026-01-15T10:00:00Z"),
  updatedAt: ISODate("2026-01-15T10:00:00Z"),
  lastLoginAt: ISODate("2026-01-15T10:00:00Z")
}

// Collection: refresh_tokens
db.refresh_tokens.createIndex({ userId: 1 });
db.refresh_tokens.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 });

{
  _id: ObjectId("..."),
  userId: ObjectId("..."),
  tokenHash: "sha256-hex",
  expiresAt: ISODate("2026-02-15T10:00:00Z"),
  createdAt: ISODate("2026-01-15T10:00:00Z"),
  revokedAt: null
}
```

### 4.2 MongoDB — `app_db`

```javascript
// Collection: problems
db.problems.createIndex({ slug: 1 }, { unique: true });

{
  _id: ObjectId("..."),
  slug: "two-sum",
  title: "Two Sum",
  description: "Given an array of integers...",
  difficulty: "EASY",
  sampleTestCases: [
    { input: "[2,7,11,15]\n9", expectedOutput: "[0,1]", isHidden: false }
  ],
  supportedLanguages: [50, 54, 62, 63, 71, 73],
  createdAt: ISODate("2026-01-01T00:00:00Z")
}

// Collection: submissions
db.submissions.createIndex({ userId: 1, createdAt: -1 });
db.submissions.createIndex({ problemId: 1 });

{
  _id: ObjectId("..."),
  userId: ObjectId("..."),
  problemId: ObjectId("..."),  // null for playground submissions
  languageId: 71,
  sourceCode: "def two_sum(nums, target):\n    ...",
  customStdin: null,
  executionTokens: ["judge0-token-uuid"],
  latestStatus: "ACCEPTED",
  createdAt: ISODate("2026-01-15T10:00:00Z"),
  updatedAt: ISODate("2026-01-15T10:00:03Z")
}

// Collection: user_stats
db.user_stats.createIndex({ userId: 1 }, { unique: true });

{
  _id: ObjectId("..."),
  userId: ObjectId("..."),
  totalSubmissions: 42,
  acceptedSubmissions: 28,
  byLanguage: {
    "71": { count: 30, accepted: 22, avgTimeMs: 145 },
    "62": { count: 12, accepted: 6, avgTimeMs: 320 }
  },
  lastSubmissionAt: ISODate("2026-01-15T10:00:00Z")
}
```

### 4.3 Postgres — `execution_db` (full DDL)

```sql
-- This file is mounted into the Postgres container as /docker-entrypoint-initdb.d/init.sql

CREATE TABLE IF NOT EXISTS execution_languages (
  id              INTEGER PRIMARY KEY,
  name            VARCHAR(100) NOT NULL,
  is_active       BOOLEAN NOT NULL DEFAULT TRUE,
  cached_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS executions (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  judge0_token        VARCHAR(64) UNIQUE NOT NULL,
  user_id             VARCHAR(64) NOT NULL,
  submission_id       VARCHAR(64),
  language_id         INTEGER NOT NULL REFERENCES execution_languages(id),
  source_code_hash    CHAR(64) NOT NULL,
  stdin               TEXT,
  expected_output     TEXT,
  cpu_time_limit      NUMERIC(5,2) NOT NULL DEFAULT 5.0,
  memory_limit        INTEGER NOT NULL DEFAULT 128000,
  status_id           INTEGER,
  status_description  VARCHAR(50),
  stdout              TEXT,
  stderr              TEXT,
  compile_output      TEXT,
  time_ms             INTEGER,
  memory_kb           INTEGER,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  completed_at        TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_executions_user_id ON executions(user_id);
CREATE INDEX IF NOT EXISTS idx_executions_user_created ON executions(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_executions_submission ON executions(submission_id);
CREATE INDEX IF NOT EXISTS idx_executions_status ON executions(status_id);
CREATE INDEX IF NOT EXISTS idx_executions_pending ON executions(created_at)
  WHERE completed_at IS NULL;  -- Partial index for the recovery poller

-- Seed common Judge0 languages (the Execution Service will refresh from Judge0 on startup)
INSERT INTO execution_languages (id, name) VALUES
  (50, 'C (GCC 9.2.0)'),
  (54, 'C++ (GCC 9.2.0)'),
  (62, 'Java (OpenJDK 13.0.1)'),
  (63, 'JavaScript (Node.js 12.14.0)'),
  (71, 'Python (3.8.1)'),
  (73, 'Rust (1.40.0)'),
  (60, 'Go (1.13.5)')
ON CONFLICT (id) DO NOTHING;
```

---

## 5. JWT Specification

**Algorithm:** HS256 (shared secret between Auth Service and API Gateway)
**Access token TTL:** 15 minutes
**Refresh token TTL:** 30 days

**Access token payload:**
```json
{
  "sub": "userId-as-mongo-objectid-string",
  "username": "alice",
  "role": "USER",
  "iat": 1737000000,
  "exp": 1737000900,
  "iss": "codearena-auth"
}
```

**Refresh token:** opaque random string (32 bytes, base64url), stored hashed in `refresh_tokens` collection. The token itself is never stored — only its SHA-256 hash for lookup.

**Where the secret lives:**
- Local dev: `application.yml` with `jwt.secret: ${JWT_SECRET:dev-secret-change-me}`
- GCP: Secret Manager, mounted into containers as env var `JWT_SECRET`
- GitHub Actions: GitHub Secrets, never committed

---

## 6. Standard Error Response

All services return RFC 7807 Problem+JSON. Implemented as a `@ControllerAdvice` in each Spring Boot service.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ProblemDetail> handleValidation(ValidationException ex, HttpServletRequest req) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    pd.setType(URI.create("https://codearena.dev/errors/validation"));
    pd.setTitle("Validation Failed");
    pd.setInstance(URI.create(req.getRequestURI()));
    pd.setProperty("correlationId", MDC.get("correlationId"));
    pd.setProperty("errors", ex.getFieldErrors());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
  }

  // ... handlers for ResourceNotFoundException, AuthenticationException,
  //     RateLimitExceededException, ExecutionServiceUnavailableException, etc.
}
```

The exact list of custom exceptions is service-specific. Define them in a `dev.codearena.{service}.exception` package.

---

## 7. Maven Parent POM Strategy

All four Spring Boot services share a parent POM at the repo root:

```xml
<!-- pom.xml at repo root -->
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>dev.codearena</groupId>
  <artifactId>codearena-parent</artifactId>
  <version>1.0.0</version>
  <packaging>pom</packaging>

  <modules>
    <module>api-gateway</module>
    <module>auth-service</module>
    <module>app-service</module>
    <module>execution-service</module>
  </modules>

  <properties>
    <java.version>21</java.version>
    <spring-boot.version>3.3.5</spring-boot.version>
    <spring-cloud.version>2023.0.3</spring-cloud.version>
    <grpc.version>1.65.1</grpc.version>
    <protobuf.version>3.25.3</protobuf.version>
    <protoc-gen-grpc-java.version>1.65.1</protoc-gen-grpc-java.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-dependencies</artifactId>
        <version>${spring-boot.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
      <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-dependencies</artifactId>
        <version>${spring-cloud.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
</project>
```

**Why a parent POM:** version bumps happen in one place, all four services stay aligned. Avoids "auth-service uses Spring Boot 3.3.4 and execution-service uses 3.3.5" drift.

---

## 8. gRPC Stub Generation

Use `protobuf-maven-plugin` in each service that consumes gRPC:

```xml
<build>
  <extensions>
    <extension>
      <groupId>kr.motd.maven</groupId>
      <artifactId>os-maven-plugin</artifactId>
      <version>1.7.1</version>
    </extension>
  </extensions>
  <plugins>
    <plugin>
      <groupId>org.xolstice.maven.plugins</groupId>
      <artifactId>protobuf-maven-plugin</artifactId>
      <version>0.6.1</version>
      <configuration>
        <protocArtifact>com.google.protobuf:protoc:${protobuf.version}:exe:${os.detected.classifier}</protocArtifact>
        <pluginId>grpc-java</pluginId>
        <pluginArtifact>io.grpc:protoc-gen-grpc-java:${protoc-gen-grpc-java.version}:exe:${os.detected.classifier}</pluginArtifact>
        <protoSourceRoot>${project.basedir}/../proto</protoSourceRoot>
      </configuration>
      <executions>
        <execution>
          <goals>
            <goal>compile</goal>
            <goal>compile-custom</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

**The proto files live at the monorepo root** (`/proto/*.proto`) and each service references them via `protoSourceRoot`. No copy-paste, single source of truth.

---

## 9. gRPC Server / Client Setup (Spring Boot)

Use `net.devh:grpc-spring-boot-starter` — the de facto Spring Boot gRPC integration:

```xml
<dependency>
  <groupId>net.devh</groupId>
  <artifactId>grpc-spring-boot-starter</artifactId>
  <version>3.1.0.RELEASE</version>
</dependency>
```

**Server side (Auth Service exposing AuthInternal):**

```java
@GrpcService
public class AuthInternalGrpcService extends AuthInternalGrpc.AuthInternalImplBase {

  @Override
  public void validateToken(ValidateTokenRequest req, StreamObserver<ValidateTokenResponse> resp) {
    // ... validate, build response, resp.onNext(...), resp.onCompleted()
  }
}
```

**Client side (App Service calling Execution):**

```yaml
# application.yml
grpc:
  client:
    execution-service:
      address: 'static://execution-service:9090'
      negotiationType: PLAINTEXT  # mTLS in production; out of scope here
```

```java
@Service
public class ExecutionClient {
  
  @GrpcClient("execution-service")
  private ExecutionServiceGrpc.ExecutionServiceBlockingStub stub;
  
  public SubmitCodeResponse submit(SubmitCodeRequest req) {
    return stub.submitCode(req);
  }
}
```

**Ports:**
- Each service runs HTTP on port 8080 (internal Docker network) and gRPC on port 9090.
- Public exposure: only the Gateway's HTTP port, behind Nginx.

---

## 10. Reference

This is artifact 2 of 5. See:
- **Artifact 1:** `judge0-architecture` — the "what and why"
- **Artifact 3:** `judge0-roadmap` — hour-by-hour implementation plan
- **Artifact 4:** `judge0-frontend` — Next.js 15 spec
- **Artifact 5:** `judge0-devops` — Git, GitHub Actions, GCP, demo
