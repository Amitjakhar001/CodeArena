# CodeArena — Git Strategy, GitHub Actions, GCP Deployment & Demo Guide
*The "how to ship" reference. Everything that's not application code: branching, CI/CD, infrastructure, and the actual recording playbook.*

---

## 1. Git Branching Strategy

### 1.1 Trunk-based with feature branches (chosen)

For a solo project filmed for video, we want **clean history that tells a story**. GitFlow with develop/release/hotfix branches is overkill. Pure trunk-based with no feature branches makes the video unwatchable (every commit is on main, no PR moments to teach).

The middle ground: **trunk-based with short-lived feature branches**, one branch per phase from the roadmap.

**Rules:**
1. `main` is always deployable. Branch protected. No direct pushes.
2. Each phase from the roadmap gets a `feature/<phase-name>` branch
3. Every feature branch ends in a PR to `main`
4. PRs require:
   - CI green
   - At least one review (set yourself as reviewer; for solo work, this trains the muscle)
   - Linear history (squash on merge OR rebase, no merge commits)
5. Tag releases on main: `v0.1.0`, `v0.2.0`, etc., one tag per phase milestone

**Branch naming convention:**
- `feature/<phase-name>` — e.g., `feature/auth-service`, `feature/execution-service`
- `fix/<short-description>` — e.g., `fix/grpc-timeout`, `fix/cgroup-detection`
- `chore/<short-description>` — e.g., `chore/upgrade-spring-boot`
- Never name a branch after a person or a date

### 1.2 Commit message convention

Conventional Commits, mandatory.

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:** `feat`, `fix`, `chore`, `docs`, `refactor`, `test`, `ci`, `build`

**Scopes:** `auth`, `app`, `execution`, `gateway`, `frontend`, `infra`, `proto`

**Examples:**
```
feat(auth): implement JWT issuance with refresh token rotation

Adds JJWT-based access token (15min) and opaque refresh token (30day)
backed by Mongo refresh_tokens collection with TTL index.

Closes #12
```

```
fix(execution): handle Judge0 timeout with retry on transient errors

Wraps the WebClient call in a retry decorator with exponential backoff,
limited to 3 attempts. Permanent failures (4xx) skip retry.
```

**Why this matters for the video:** When you scroll through `git log` on camera, viewers see a project that thinks like an engineer. Throwaway commits like "fixed stuff" are an instant credibility hit.

### 1.3 PR template

Create `.github/pull_request_template.md`:

```markdown
## What

<!-- One sentence: what does this PR do? -->

## Why

<!-- One paragraph: what problem does this solve? Reference the phase from the roadmap. -->

## How

<!-- Bullet points of the key implementation choices. -->

## Verification

- [ ] CI is green
- [ ] HTTP file or curl example added/updated if endpoints changed
- [ ] README updated if setup/run steps changed
- [ ] Manual smoke test passed (describe what you ran)

## Screenshots / Logs

<!-- For UI changes, include screenshots. For backend, include relevant log output. -->
```

---

## 2. Repository GitHub Setup

### 2.1 Branch protection on `main`

Settings → Branches → Add rule for `main`:

- ✅ Require pull request before merging
- ✅ Require approvals (1)
- ✅ Dismiss stale approvals on new commits
- ✅ Require status checks to pass
- ✅ Require branches to be up to date before merging
- ✅ Require linear history
- ✅ Include administrators (yes, even for solo — discipline matters)

### 2.2 Secrets needed

Settings → Secrets and variables → Actions:

| Secret | Purpose |
|---|---|
| `GCP_PROJECT_ID` | Your GCP project ID |
| `GCP_WIF_PROVIDER` | Workload Identity provider resource name |
| `GCP_WIF_SERVICE_ACCOUNT` | Service account email for OIDC |
| `JWT_SECRET` | Used by Auth Service + Gateway (at least 32 chars) |
| `MONGODB_AUTH_URI` | `mongodb://...` for the auth_db (prod) |
| `MONGODB_APP_URI` | `mongodb://...` for the app_db (prod) |
| `POSTGRES_EXECUTION_URL` | JDBC URL for execution_db (prod) |
| `POSTGRES_EXECUTION_USER` / `POSTGRES_EXECUTION_PASS` | Postgres creds |
| `JUDGE0_INTERNAL_URL` | Internal Nginx LB URL for Judge0 |
| `VM1_SSH_HOST` / `VM2_SSH_HOST` / `VM3_SSH_HOST` | VM IPs for deploy |

Most of these don't exist until Phase 8 (GCP deploy). Don't create them upfront; create as needed.

---

## 3. GitHub Actions Workflows

### 3.1 The reusable Java build workflow

`.github/workflows/_reusable-java-build.yml`:

```yaml
name: Reusable Java Build

on:
  workflow_call:
    inputs:
      service-name:
        required: true
        type: string
      push-image:
        required: false
        type: boolean
        default: false
    secrets:
      GCP_WIF_PROVIDER:
        required: false
      GCP_WIF_SERVICE_ACCOUNT:
        required: false
      GCP_PROJECT_ID:
        required: false

jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      id-token: write
    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven

      - name: Build and test
        run: mvn -pl ${{ inputs.service-name }} -am -B clean verify

      - name: Upload coverage report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: ${{ inputs.service-name }}-coverage
          path: ${{ inputs.service-name }}/target/site/jacoco/

      - name: Authenticate to GCP
        if: inputs.push-image == true
        uses: google-github-actions/auth@v2
        with:
          workload_identity_provider: ${{ secrets.GCP_WIF_PROVIDER }}
          service_account: ${{ secrets.GCP_WIF_SERVICE_ACCOUNT }}

      - name: Set up gcloud
        if: inputs.push-image == true
        uses: google-github-actions/setup-gcloud@v2

      - name: Configure Docker for Artifact Registry
        if: inputs.push-image == true
        run: gcloud auth configure-docker asia-south1-docker.pkg.dev --quiet

      - name: Build and push Docker image
        if: inputs.push-image == true
        run: |
          IMAGE=asia-south1-docker.pkg.dev/${{ secrets.GCP_PROJECT_ID }}/codearena/${{ inputs.service-name }}
          docker build -t $IMAGE:${{ github.sha }} -t $IMAGE:latest ./${{ inputs.service-name }}
          docker push $IMAGE:${{ github.sha }}
          docker push $IMAGE:latest
```

### 3.2 Per-service workflow (auth-service example)

`.github/workflows/auth-service.yml`:

```yaml
name: Auth Service

on:
  push:
    branches: [main]
    paths:
      - 'auth-service/**'
      - 'proto/**'
      - 'pom.xml'
      - '.github/workflows/auth-service.yml'
      - '.github/workflows/_reusable-java-build.yml'
  pull_request:
    branches: [main]
    paths:
      - 'auth-service/**'
      - 'proto/**'
      - 'pom.xml'

jobs:
  build:
    uses: ./.github/workflows/_reusable-java-build.yml
    with:
      service-name: auth-service
      push-image: ${{ github.ref == 'refs/heads/main' && github.event_name == 'push' }}
    secrets:
      GCP_WIF_PROVIDER: ${{ secrets.GCP_WIF_PROVIDER }}
      GCP_WIF_SERVICE_ACCOUNT: ${{ secrets.GCP_WIF_SERVICE_ACCOUNT }}
      GCP_PROJECT_ID: ${{ secrets.GCP_PROJECT_ID }}
```

Replicate for `app-service.yml`, `execution-service.yml`, `api-gateway.yml` — change `service-name` and the `paths`.

### 3.3 Frontend workflow

`.github/workflows/frontend.yml`:

```yaml
name: Frontend

on:
  push:
    branches: [main]
    paths: ['frontend/**', '.github/workflows/frontend.yml']
  pull_request:
    branches: [main]
    paths: ['frontend/**']

jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      id-token: write
    defaults:
      run:
        working-directory: frontend
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: frontend/package-lock.json

      - run: npm ci

      - run: npm run lint

      - run: npm run type-check

      - run: npm run build
        env:
          NEXT_PUBLIC_API_BASE: ${{ vars.NEXT_PUBLIC_API_BASE_PROD }}

      - name: Authenticate to GCP
        if: github.ref == 'refs/heads/main' && github.event_name == 'push'
        uses: google-github-actions/auth@v2
        with:
          workload_identity_provider: ${{ secrets.GCP_WIF_PROVIDER }}
          service_account: ${{ secrets.GCP_WIF_SERVICE_ACCOUNT }}

      - name: Configure Docker for Artifact Registry
        if: github.ref == 'refs/heads/main' && github.event_name == 'push'
        run: gcloud auth configure-docker asia-south1-docker.pkg.dev --quiet

      - name: Build and push image
        if: github.ref == 'refs/heads/main' && github.event_name == 'push'
        run: |
          IMAGE=asia-south1-docker.pkg.dev/${{ secrets.GCP_PROJECT_ID }}/codearena/frontend
          docker build --build-arg NEXT_PUBLIC_API_BASE=${{ vars.NEXT_PUBLIC_API_BASE_PROD }} \
                       -t $IMAGE:${{ github.sha }} -t $IMAGE:latest .
          docker push $IMAGE:${{ github.sha }}
          docker push $IMAGE:latest
```

### 3.4 Deployment workflow (manual trigger)

`.github/workflows/deploy-gcp.yml`:

```yaml
name: Deploy to GCP

on:
  workflow_dispatch:
    inputs:
      target:
        description: 'What to deploy'
        required: true
        default: 'all'
        type: choice
        options:
          - all
          - vm1-app-stack
          - vm2-judge0-stateful
          - vm3-judge0-stateless

jobs:
  deploy:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      id-token: write
    steps:
      - uses: actions/checkout@v4

      - uses: google-github-actions/auth@v2
        with:
          workload_identity_provider: ${{ secrets.GCP_WIF_PROVIDER }}
          service_account: ${{ secrets.GCP_WIF_SERVICE_ACCOUNT }}

      - uses: google-github-actions/setup-gcloud@v2

      - name: Deploy VM 1 (App Stack)
        if: inputs.target == 'all' || inputs.target == 'vm1-app-stack'
        run: |
          gcloud compute scp infra/docker/docker-compose.app.yml \
            codearena-vm1:~/docker-compose.yml \
            --zone=asia-south1-a --tunnel-through-iap
          gcloud compute ssh codearena-vm1 --zone=asia-south1-a --tunnel-through-iap --command "
            cd ~ && docker compose pull && docker compose up -d
          "

      - name: Deploy VM 2 (Judge0 Stateful)
        if: inputs.target == 'all' || inputs.target == 'vm2-judge0-stateful'
        run: |
          gcloud compute scp infra/docker/docker-compose.judge0-a.yml \
            codearena-vm2:~/docker-compose.yml \
            --zone=asia-south1-a --tunnel-through-iap
          gcloud compute ssh codearena-vm2 --zone=asia-south1-a --tunnel-through-iap --command "
            cd ~ && docker compose pull && docker compose up -d
          "

      - name: Deploy VM 3 (Judge0 Stateless)
        if: inputs.target == 'all' || inputs.target == 'vm3-judge0-stateless'
        run: |
          gcloud compute scp infra/docker/docker-compose.judge0-b.yml \
            codearena-vm3:~/docker-compose.yml \
            --zone=asia-south1-a --tunnel-through-iap
          gcloud compute ssh codearena-vm3 --zone=asia-south1-a --tunnel-through-iap --command "
            cd ~ && docker compose pull && docker compose up -d
          "

      - name: Health check
        if: always()
        run: |
          sleep 30
          curl -f https://${{ vars.PROD_DOMAIN }}/health || exit 1
```

### 3.5 OIDC setup script

`infra/gcp/setup-oidc.sh` — run this once locally to wire GitHub → GCP:

```bash
#!/bin/bash
set -euo pipefail

PROJECT_ID="${GCP_PROJECT_ID:?Set GCP_PROJECT_ID}"
GITHUB_REPO="${GITHUB_REPO:?Set GITHUB_REPO (e.g., yourname/codearena)}"
POOL_ID="github-pool"
PROVIDER_ID="github-provider"
SA_NAME="codearena-deployer"

# Create Workload Identity Pool
gcloud iam workload-identity-pools create "$POOL_ID" \
  --project="$PROJECT_ID" \
  --location="global" \
  --display-name="GitHub Actions Pool" || true

# Create OIDC provider
gcloud iam workload-identity-pools providers create-oidc "$PROVIDER_ID" \
  --project="$PROJECT_ID" \
  --location="global" \
  --workload-identity-pool="$POOL_ID" \
  --display-name="GitHub OIDC Provider" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.actor=assertion.actor" \
  --attribute-condition="assertion.repository == '$GITHUB_REPO'" \
  --issuer-uri="https://token.actions.githubusercontent.com" || true

# Create service account
gcloud iam service-accounts create "$SA_NAME" \
  --project="$PROJECT_ID" \
  --display-name="CodeArena Deployer" || true

SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"

# Grant roles
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$SA_EMAIL" \
  --role="roles/artifactregistry.writer"

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$SA_EMAIL" \
  --role="roles/compute.instanceAdmin.v1"

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$SA_EMAIL" \
  --role="roles/iap.tunnelResourceAccessor"

# Allow GitHub to impersonate the SA
PROJECT_NUMBER=$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')
gcloud iam service-accounts add-iam-policy-binding "$SA_EMAIL" \
  --project="$PROJECT_ID" \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/$POOL_ID/attribute.repository/$GITHUB_REPO"

echo ""
echo "Add these to GitHub Secrets:"
echo "  GCP_PROJECT_ID = $PROJECT_ID"
echo "  GCP_WIF_PROVIDER = projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/$POOL_ID/providers/$PROVIDER_ID"
echo "  GCP_WIF_SERVICE_ACCOUNT = $SA_EMAIL"
```

---

## 4. GCP Provisioning

### 4.1 Pre-flight (one-time)

```bash
# Authenticate
gcloud auth login
gcloud config set project YOUR_PROJECT_ID

# Enable required APIs
gcloud services enable \
  compute.googleapis.com \
  artifactregistry.googleapis.com \
  iap.googleapis.com \
  iamcredentials.googleapis.com \
  secretmanager.googleapis.com

# Create Artifact Registry repo for Docker images
gcloud artifacts repositories create codearena \
  --repository-format=docker \
  --location=asia-south1 \
  --description="CodeArena Docker images"
```

### 4.2 VM provisioning script

`infra/gcp/provision-vms.sh`:

```bash
#!/bin/bash
set -euo pipefail

PROJECT_ID="${GCP_PROJECT_ID:?Set GCP_PROJECT_ID}"
REGION="asia-south1"
ZONE="asia-south1-a"
NETWORK="codearena-net"
SUBNET="codearena-subnet"

# ─── Network ────────────────────────────────────────────────────────
gcloud compute networks create "$NETWORK" \
  --subnet-mode=custom || true

gcloud compute networks subnets create "$SUBNET" \
  --network="$NETWORK" \
  --region="$REGION" \
  --range=10.0.0.0/24 || true

# ─── Firewall ───────────────────────────────────────────────────────
# Allow IAP for SSH (no public SSH ports)
gcloud compute firewall-rules create codearena-allow-iap \
  --network="$NETWORK" \
  --source-ranges=35.235.240.0/20 \
  --allow=tcp:22,tcp:80,tcp:443 || true

# Allow internal traffic between VMs
gcloud compute firewall-rules create codearena-allow-internal \
  --network="$NETWORK" \
  --source-ranges=10.0.0.0/24 \
  --allow=tcp,udp,icmp || true

# Allow public HTTP/HTTPS only on VM 1 (using target tags)
gcloud compute firewall-rules create codearena-allow-http \
  --network="$NETWORK" \
  --source-ranges=0.0.0.0/0 \
  --target-tags=http-server,https-server \
  --allow=tcp:80,tcp:443 || true

# ─── VM 1: App Stack (e2-standard-2, 8GB RAM) ───────────────────────
gcloud compute instances create codearena-vm1 \
  --zone="$ZONE" \
  --machine-type=e2-standard-2 \
  --network="$NETWORK" \
  --subnet="$SUBNET" \
  --image-family=ubuntu-2204-lts \
  --image-project=ubuntu-os-cloud \
  --boot-disk-size=30GB \
  --boot-disk-type=pd-balanced \
  --tags=http-server,https-server \
  --metadata=startup-script='#!/bin/bash
    apt-get update
    apt-get install -y docker.io docker-compose-plugin nginx certbot python3-certbot-nginx
    systemctl enable --now docker
    usermod -aG docker ubuntu'

# ─── VM 2: Judge0 Stateful Node (e2-medium, 4GB RAM) ────────────────
gcloud compute instances create codearena-vm2 \
  --zone="$ZONE" \
  --machine-type=e2-medium \
  --network="$NETWORK" \
  --subnet="$SUBNET" \
  --image-family=ubuntu-2204-lts \
  --image-project=ubuntu-os-cloud \
  --boot-disk-size=30GB \
  --boot-disk-type=pd-balanced \
  --metadata=startup-script='#!/bin/bash
    apt-get update
    apt-get install -y docker.io docker-compose-plugin
    systemctl enable --now docker
    usermod -aG docker ubuntu'

# ─── VM 3: Judge0 Stateless Node (e2-medium, 4GB RAM) ───────────────
gcloud compute instances create codearena-vm3 \
  --zone="$ZONE" \
  --machine-type=e2-medium \
  --network="$NETWORK" \
  --subnet="$SUBNET" \
  --image-family=ubuntu-2204-lts \
  --image-project=ubuntu-os-cloud \
  --boot-disk-size=30GB \
  --boot-disk-type=pd-balanced \
  --metadata=startup-script='#!/bin/bash
    apt-get update
    apt-get install -y docker.io docker-compose-plugin
    systemctl enable --now docker
    usermod -aG docker ubuntu'

echo "VMs created. Internal IPs:"
gcloud compute instances list --filter="name~codearena" --format="table(name,networkInterfaces[0].networkIP,status)"
```

### 4.3 Cgroup v1 fix script

`infra/scripts/cgroup-v1-fix.sh` — run on VM 2 and VM 3:

```bash
#!/bin/bash
set -euo pipefail

GRUB_FILE="/etc/default/grub"
NEW_PARAMS="systemd.unified_cgroup_hierarchy=0 systemd.legacy_systemd_cgroup_controller=1"

if grep -q "systemd.unified_cgroup_hierarchy=0" "$GRUB_FILE"; then
  echo "Cgroup v1 already configured."
  exit 0
fi

sudo cp "$GRUB_FILE" "${GRUB_FILE}.bak.$(date +%s)"
sudo sed -i "s/GRUB_CMDLINE_LINUX_DEFAULT=\"\(.*\)\"/GRUB_CMDLINE_LINUX_DEFAULT=\"\1 ${NEW_PARAMS}\"/" "$GRUB_FILE"

echo "Updated $GRUB_FILE:"
grep "GRUB_CMDLINE_LINUX_DEFAULT" "$GRUB_FILE"

sudo update-grub

echo ""
echo "GRUB updated. System needs reboot:"
echo "  sudo reboot"
echo ""
echo "After reboot, verify with:"
echo "  mount | grep cgroup | head -5"
echo "Should show v1 mounts like: cgroup on /sys/fs/cgroup/memory type cgroup"
```

**Run order:**
```bash
# On VM 2 and VM 3:
gcloud compute scp infra/scripts/cgroup-v1-fix.sh codearena-vm2:~ --zone=asia-south1-a --tunnel-through-iap
gcloud compute ssh codearena-vm2 --zone=asia-south1-a --tunnel-through-iap --command "bash ~/cgroup-v1-fix.sh && sudo reboot"
# Wait ~60s for reboot
gcloud compute ssh codearena-vm2 --zone=asia-south1-a --tunnel-through-iap --command "mount | grep cgroup | head -5"
# Repeat for VM 3
```

### 4.4 Docker Compose: VM 2 (Judge0 Stateful)

`infra/docker/docker-compose.judge0-a.yml`:

```yaml
services:
  judge0-db:
    image: postgres:13.0
    container_name: judge0-db
    environment:
      POSTGRES_USER: judge0
      POSTGRES_PASSWORD: ${JUDGE0_DB_PASSWORD}
      POSTGRES_DB: judge0
    volumes:
      - judge0-db-data:/var/lib/postgresql/data
    restart: unless-stopped
    ports:
      - "10.0.0.2:5432:5432"  # Bind to internal IP, accessible by VM 3

  judge0-redis:
    image: redis:6.0
    container_name: judge0-redis
    command: ["redis-server", "--requirepass", "${JUDGE0_REDIS_PASSWORD}"]
    volumes:
      - judge0-redis-data:/data
    restart: unless-stopped
    ports:
      - "10.0.0.2:6379:6379"  # Bind to internal IP

  judge0-server:
    image: judge0/judge0:1.13.1
    container_name: judge0-server
    environment:
      - REDIS_HOST=judge0-redis
      - REDIS_PASSWORD=${JUDGE0_REDIS_PASSWORD}
      - POSTGRES_HOST=judge0-db
      - POSTGRES_USER=judge0
      - POSTGRES_PASSWORD=${JUDGE0_DB_PASSWORD}
      - POSTGRES_DB=judge0
      - JUDGE0_TELEMETRY_ENABLE=false
    depends_on:
      - judge0-db
      - judge0-redis
    ports:
      - "2358:2358"
    privileged: true
    restart: unless-stopped

  judge0-workers:
    image: judge0/judge0:1.13.1
    container_name: judge0-workers
    command: ["./scripts/workers"]
    environment:
      - REDIS_HOST=judge0-redis
      - REDIS_PASSWORD=${JUDGE0_REDIS_PASSWORD}
      - POSTGRES_HOST=judge0-db
      - POSTGRES_USER=judge0
      - POSTGRES_PASSWORD=${JUDGE0_DB_PASSWORD}
      - POSTGRES_DB=judge0
    depends_on:
      - judge0-server
    privileged: true
    restart: unless-stopped

volumes:
  judge0-db-data:
  judge0-redis-data:
```

### 4.5 Docker Compose: VM 3 (Judge0 Stateless)

`infra/docker/docker-compose.judge0-b.yml`:

```yaml
services:
  judge0-server:
    image: judge0/judge0:1.13.1
    container_name: judge0-server
    environment:
      - REDIS_HOST=10.0.0.2  # VM 2's internal IP
      - REDIS_PASSWORD=${JUDGE0_REDIS_PASSWORD}
      - POSTGRES_HOST=10.0.0.2
      - POSTGRES_USER=judge0
      - POSTGRES_PASSWORD=${JUDGE0_DB_PASSWORD}
      - POSTGRES_DB=judge0
      - JUDGE0_TELEMETRY_ENABLE=false
    ports:
      - "2358:2358"
    privileged: true
    restart: unless-stopped

  judge0-workers:
    image: judge0/judge0:1.13.1
    container_name: judge0-workers
    command: ["./scripts/workers"]
    environment:
      - REDIS_HOST=10.0.0.2
      - REDIS_PASSWORD=${JUDGE0_REDIS_PASSWORD}
      - POSTGRES_HOST=10.0.0.2
      - POSTGRES_USER=judge0
      - POSTGRES_PASSWORD=${JUDGE0_DB_PASSWORD}
      - POSTGRES_DB=judge0
    privileged: true
    restart: unless-stopped
```

**Note:** Replace `10.0.0.2` with VM 2's actual internal IP from `gcloud compute instances list`.

### 4.6 Internal Nginx config (on VM 1, load-balances Judge0)

`infra/docker/nginx-internal.conf`:

```nginx
upstream judge0_backend {
    least_conn;
    server 10.0.0.2:2358 max_fails=3 fail_timeout=30s;
    server 10.0.0.3:2358 max_fails=3 fail_timeout=30s;
    keepalive 32;
}

server {
    listen 8000;
    server_name judge0-internal.local;

    # Internal-only — no public exposure
    allow 10.0.0.0/24;
    deny all;

    location / {
        proxy_pass http://judge0_backend;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_connect_timeout 5s;
        proxy_send_timeout 30s;
        proxy_read_timeout 30s;
    }

    # Health check endpoint that reflects upstream health
    location /lb-health {
        access_log off;
        proxy_pass http://judge0_backend/about;
        proxy_connect_timeout 2s;
        proxy_read_timeout 5s;
    }
}
```

**Note on `least_conn`:** This is critical for Judge0. Round-robin would route a 5-second C++ compile to one node and a 50ms Python script to the other regardless of load. `least_conn` actually balances based on active work. Mention this on camera.

### 4.7 Public Nginx config (on VM 1, public ingress)

`infra/docker/nginx-public.conf`:

```nginx
server {
    listen 80;
    server_name codearena.example.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name codearena.example.com;

    ssl_certificate /etc/letsencrypt/live/codearena.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/codearena.example.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    # API routes → API Gateway container on VM 1
    location /api/ {
        proxy_pass http://api-gateway:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Frontend (Next.js) → frontend container on VM 1
    location / {
        proxy_pass http://frontend:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
    }
}
```

For demo purposes, you can skip Let's Encrypt and use a self-signed cert, OR just use HTTP at the VM's public IP. The video viewer doesn't care; the architecture does.

### 4.8 VM 1 master compose

`infra/docker/docker-compose.app.yml`:

```yaml
services:
  gateway-redis:
    image: redis:7-alpine
    container_name: gateway-redis
    restart: unless-stopped

  auth-mongodb:
    image: mongo:7
    container_name: auth-mongodb
    environment:
      MONGO_INITDB_DATABASE: auth_db
    volumes:
      - auth-mongodb-data:/data/db
    restart: unless-stopped

  app-mongodb:
    image: mongo:7
    container_name: app-mongodb
    environment:
      MONGO_INITDB_DATABASE: app_db
    volumes:
      - app-mongodb-data:/data/db
    restart: unless-stopped

  execution-postgres:
    image: postgres:16
    container_name: execution-postgres
    environment:
      POSTGRES_USER: execution
      POSTGRES_PASSWORD: ${POSTGRES_EXECUTION_PASS}
      POSTGRES_DB: execution_db
    volumes:
      - execution-postgres-data:/var/lib/postgresql/data
      - ./postgres-init.sql:/docker-entrypoint-initdb.d/init.sql:ro
    restart: unless-stopped

  auth-service:
    image: asia-south1-docker.pkg.dev/${GCP_PROJECT_ID}/codearena/auth-service:latest
    container_name: auth-service
    environment:
      SPRING_PROFILES_ACTIVE: docker
      MONGODB_URI: mongodb://auth-mongodb:27017/auth_db
      JWT_SECRET: ${JWT_SECRET}
    depends_on:
      - auth-mongodb
    restart: unless-stopped

  execution-service:
    image: asia-south1-docker.pkg.dev/${GCP_PROJECT_ID}/codearena/execution-service:latest
    container_name: execution-service
    environment:
      SPRING_PROFILES_ACTIVE: docker
      DB_URL: jdbc:postgresql://execution-postgres:5432/execution_db
      DB_USER: execution
      DB_PASS: ${POSTGRES_EXECUTION_PASS}
      JUDGE0_BASE_URL: http://nginx-internal:8000
      WEBHOOK_BASE_URL: http://execution-service:8080
    depends_on:
      - execution-postgres
    restart: unless-stopped

  app-service:
    image: asia-south1-docker.pkg.dev/${GCP_PROJECT_ID}/codearena/app-service:latest
    container_name: app-service
    environment:
      SPRING_PROFILES_ACTIVE: docker
      MONGODB_URI: mongodb://app-mongodb:27017/app_db
      AUTH_SERVICE_GRPC: auth-service:9090
      EXECUTION_SERVICE_GRPC: execution-service:9090
    depends_on:
      - app-mongodb
      - auth-service
      - execution-service
    restart: unless-stopped

  api-gateway:
    image: asia-south1-docker.pkg.dev/${GCP_PROJECT_ID}/codearena/api-gateway:latest
    container_name: api-gateway
    environment:
      SPRING_PROFILES_ACTIVE: docker
      JWT_SECRET: ${JWT_SECRET}
      AUTH_SERVICE_URL: http://auth-service:8080
      APP_SERVICE_URL: http://app-service:8080
      RATE_LIMIT_REDIS_HOST: gateway-redis
    depends_on:
      - auth-service
      - app-service
    restart: unless-stopped

  frontend:
    image: asia-south1-docker.pkg.dev/${GCP_PROJECT_ID}/codearena/frontend:latest
    container_name: frontend
    restart: unless-stopped

  nginx-internal:
    image: nginx:alpine
    container_name: nginx-internal
    volumes:
      - ./nginx-internal.conf:/etc/nginx/conf.d/default.conf:ro
    ports:
      - "8000:8000"
    restart: unless-stopped

  nginx-public:
    image: nginx:alpine
    container_name: nginx-public
    volumes:
      - ./nginx-public.conf:/etc/nginx/conf.d/default.conf:ro
      - /etc/letsencrypt:/etc/letsencrypt:ro
    ports:
      - "80:80"
      - "443:443"
    depends_on:
      - frontend
      - api-gateway
    restart: unless-stopped

volumes:
  auth-mongodb-data:
  app-mongodb-data:
  execution-postgres-data:
```

---

## 5. The Demo Recording Playbook

### 5.1 Pre-recording checklist

The night before:
- [ ] Full deploy is green; URL loads in browser
- [ ] Test the kill-VM-3 demo and recover (rehearse it 2-3 times)
- [ ] Have starter code snippets ready (Python hello world, Java hello world, intentional runtime error, intentional compile error)
- [ ] Browser bookmarks: frontend, GCP console (3 VMs visible), Artifact Registry, GitHub Actions
- [ ] Terminal windows pre-arranged: tmux session with SSH to VM 3, log tail on VM 1
- [ ] Recording software tested with audio + screen capture
- [ ] Background apps closed, notifications disabled
- [ ] DB seeded with 1-2 prior submissions so history isn't empty

### 5.2 The demo script (10-15 min)

**Segment 1 — Architecture intro (2 min)**
- Open the topology diagram (from architecture artifact §3)
- Walk through it left-to-right: frontend → gateway → services → Judge0 cluster
- Name each communication arrow: REST, gRPC, REST+webhook
- One sentence on each service's responsibility

**Segment 2 — Live system tour (3 min)**
- Open the live URL
- Click Register, create demo account `demo@codearena.dev`
- Land on playground, write `print("Hello, CodeArena!")` in Python, Run
- Watch status badge change PENDING → ACCEPTED, output appear
- Switch language to Java, paste equivalent, Run
- Switch to C++, intentionally write `prit` instead of `print`, Run → COMPILATION_ERROR with compile output
- Fix it, run → ACCEPTED
- Click History → see all four submissions
- Click one → see detail page with source code in read-only Monaco
- Click Stats → see counts and avg times by language

**Segment 3 — The horizontal scaling moment (2 min)** *(this is the money shot)*
- Split-screen: GCP console (3 VMs visible) on left, terminal SSH'd to VM 3 + frontend on right
- "Watch what happens when I take down a Judge0 node"
- On VM 3: `cd ~ && docker compose stop`
- Show GCP console — VM 3 still UP but its Judge0 containers gone
- Submit code from frontend → executes successfully via VM 2
- "The Nginx LB on VM 1 marked VM 3 as unhealthy and routed exclusively to VM 2"
- On VM 3: `docker compose up -d`
- Wait ~10s, submit again, show Nginx access log proving requests now hit both
- "This is what stateless workers + shared state gets you — true horizontal scaling, not just multiple boxes."

**Segment 4 — Code highlights (3 min)**
- Quick scroll through `proto/execution.proto` — "this is the contract App Service codes against"
- Open `api-gateway/.../JwtFilter.java` — "JWT validated once, X-User-Id propagated as trusted context"
- Open `execution-service/.../Judge0WebhookController.java` — "webhook is the happy path; the recovery poller is the safety net"
- Open `frontend/lib/hooks/usePollSubmission.ts` — "polling beats WebSockets when execution is 1-5s"

**Segment 5 — CI/CD moment (2 min)**
- Local: change `app-service`'s health response message
- Commit with a real message, push to feature branch
- Open GitHub: PR auto-opened, CI running
- Show CI passing, merge to main
- Show main pipeline pushing image to Artifact Registry
- Run `deploy-gcp` workflow manually
- Show the change live on the deployed system

**Segment 6 — Wrap (1 min)**
- "What I deliberately cut for this scope: Kafka, separate notification service, full LGTM stack, mTLS"
- "What I'd add for production: managed databases, Redis Sentinel for Judge0's Redis, circuit breakers via Resilience4j, structured log shipping to Loki"
- Link to repo
- "Subscribe for the next one"

### 5.3 The teardown checklist (do immediately after recording)

```bash
# Stop and delete VMs
gcloud compute instances delete codearena-vm1 codearena-vm2 codearena-vm3 --zone=asia-south1-a --quiet

# Delete persistent disks (if not deleted with VMs)
gcloud compute disks list --filter="name~codearena" --format="value(name,zone)" | while read name zone; do
  gcloud compute disks delete "$name" --zone="$zone" --quiet
done

# Delete static IPs if reserved
gcloud compute addresses list --filter="name~codearena" --format="value(name,region)" | while read name region; do
  gcloud compute addresses delete "$name" --region="$region" --quiet
done

# Optional: delete Artifact Registry to stop storage charges
# gcloud artifacts repositories delete codearena --location=asia-south1 --quiet

# Delete firewall rules
gcloud compute firewall-rules delete codearena-allow-iap codearena-allow-internal codearena-allow-http --quiet

# Delete network and subnet
gcloud compute networks subnets delete codearena-subnet --region=asia-south1 --quiet
gcloud compute networks delete codearena-net --quiet

# Set a billing alert as a safety net
gcloud alpha billing budgets create \
  --billing-account=YOUR_BILLING_ID \
  --display-name="codearena-safety-net" \
  --budget-amount=5USD \
  --threshold-rule=percent=0.5 \
  --threshold-rule=percent=1.0
```

**Verify in the GCP console:** Compute Engine → VM instances should show empty. Billing → Reports next day should show no compute consumption.

---

## 6. README Template

The README is the project's first impression. Fill it in fully before recording the demo.

````markdown
# CodeArena

A self-hosted code execution platform with multi-language support, custom test cases, and real-time execution feedback. Built as a microservices architecture demonstrating gRPC, polyglot persistence, and horizontal scaling of code execution workers via Judge0.

![Architecture](docs/architecture.png)

## Stack

**Backend:** Spring Boot 3.3 · Java 21 · Spring Cloud Gateway · gRPC (net.devh) · MongoDB · PostgreSQL · Redis
**Frontend:** Next.js 15 · TypeScript · Tailwind CSS 4 · Monaco Editor
**Infrastructure:** Judge0 1.13 · Docker · Nginx · Google Cloud Platform · GitHub Actions (OIDC)

## Architecture Highlights

- **4 microservices** with clean ownership boundaries
- **gRPC for internal sync calls**, REST for the edge, REST+webhook for Judge0 integration
- **Polyglot persistence** — MongoDB for user-facing data, PostgreSQL for execution metadata
- **Stateless Judge0 workers** with shared Redis + Postgres for true horizontal scaling
- **JWT auth at the edge**, X-User-Id trusted context propagated inward
- **Cgroup v1 fix** documented and automated for modern Linux hosts

## Quick Start

```bash
git clone https://github.com/yourname/codearena.git
cd codearena
make dev      # Brings up the entire stack via Docker Compose
make seed     # Seeds sample problems
open http://localhost:3000
```

## Deployment

See [`infra/gcp/`](infra/gcp/) for GCP provisioning scripts and [`/.github/workflows/`](/.github/workflows/) for CI/CD pipelines.

## Documentation

- [Architecture Blueprint](docs/architecture.md)
- [API & gRPC Contracts](docs/contracts.md)
- [DevOps Guide](docs/devops.md)

## License

MIT
````

---

## 7. Appendix — Local Dev on Fedora

The artifacts above target Ubuntu 22.04 on the GCP VMs because that's the deployment OS. If your **local development machine** runs Fedora (39/40/41), use the notes below. The deployment instructions stay unchanged — only your local setup differs.

### 7.1 Docker installation on Fedora

```bash
# Remove Podman if present (it conflicts with Docker for privileged workloads)
sudo dnf remove -y podman buildah || true

# Install Docker CE from Docker's official repo
sudo dnf -y install dnf-plugins-core
sudo dnf config-manager --add-repo https://download.docker.com/linux/fedora/docker-ce.repo
sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod -aG docker $USER

# Log out and back in (or reboot) for the group change
newgrp docker  # or just log out/in
docker run --rm hello-world  # verify
```

**Don't use Podman for this project.** Podman's rootless model and SELinux integration interact badly with Judge0's `--privileged` containers and cgroup bind-mounts.

### 7.2 SELinux — the gotcha that will silently break Judge0

Fedora ships with SELinux in enforcing mode by default. Judge0 uses `--privileged` containers and mounts kernel cgroup paths; SELinux blocks some of these silently, producing weird `Internal Error` results on submissions that look nothing like permission issues.

**Symptoms:**
- Judge0 worker starts cleanly but every submission returns status `Internal Error`
- `docker logs judge0-workers` shows `permission denied` on `/sys/fs/cgroup/...`
- `sudo dmesg | grep -i avc` shows SELinux denial messages

**The fix — set SELinux to permissive (logs violations without enforcing):**

```bash
# Verify current mode
getenforce  # likely "Enforcing"

# Switch to permissive — temporary (resets at reboot)
sudo setenforce 0

# Persist across reboots
sudo sed -i 's/^SELINUX=enforcing/SELINUX=permissive/' /etc/selinux/config

# Verify after reboot
getenforce  # should be "Permissive"
```

**Don't disable SELinux entirely (`SELINUX=disabled`).** Permissive mode logs violations so you can debug; disabled mode prevents the kernel module from loading at all and complicates re-enabling later.

This is standard practice for container-heavy dev workflows on Fedora. SELinux on the GCP Ubuntu VMs is irrelevant — Ubuntu uses AppArmor, not SELinux, and the artifact deployment scripts don't trigger AppArmor issues.

### 7.3 Cgroup v1 on Fedora — the GRUB path differs

The cgroup v1 requirement applies to Fedora too if you intend to run Judge0 directly on the host. Fedora moved to unified cgroup v2 even earlier than Ubuntu (Fedora 31, 2019), so all current Fedora versions default to v2.

**The kernel parameters are identical to the artifact's Ubuntu version**, but Fedora's grub regeneration command differs:

```bash
# Backup and edit
sudo cp /etc/default/grub /etc/default/grub.bak
sudo sed -i 's/GRUB_CMDLINE_LINUX="\(.*\)"/GRUB_CMDLINE_LINUX="\1 systemd.unified_cgroup_hierarchy=0 systemd.legacy_systemd_cgroup_controller=1"/' /etc/default/grub

# Regenerate GRUB config — note the difference:
# Ubuntu: sudo update-grub
# Fedora BIOS: sudo grub2-mkconfig -o /boot/grub2/grub.cfg
# Fedora UEFI: sudo grub2-mkconfig -o /boot/efi/EFI/fedora/grub.cfg

# Detect which one your system uses
[ -d /sys/firmware/efi ] && echo "UEFI" || echo "BIOS"

# Then run the appropriate command
sudo grub2-mkconfig -o /boot/efi/EFI/fedora/grub.cfg  # if UEFI
# or
sudo grub2-mkconfig -o /boot/grub2/grub.cfg          # if BIOS

sudo reboot

# Verify after reboot
mount | grep cgroup | head -5
# Should show v1-style mounts: cgroup on /sys/fs/cgroup/memory type cgroup
```

**Note on the sed pattern:** Fedora's GRUB uses `GRUB_CMDLINE_LINUX` (not `GRUB_CMDLINE_LINUX_DEFAULT` like Ubuntu). The script above accounts for that.

**However:** Since you're running Judge0 inside Docker containers and Docker Desktop / Docker CE on Linux uses the host kernel directly, the cgroup setting is on the **host**, not the container. So yes, the GRUB fix applies to your Fedora dev box too if you want Judge0 to work locally.

**Pragmatic alternative for local dev:** if you don't want to reboot your daily-driver Fedora machine into cgroup v1 mode, run Judge0 only on the GCP VMs (which are Ubuntu and have the fix applied via the deploy scripts) and develop the Spring Boot services against either:
- A remote tunnel to the GCP Judge0 cluster, or
- Mock execution responses during local Spring Boot development

For most people, this is the right tradeoff — keep your Fedora host on cgroup v2 (modern default, plays nicely with everything else), test Judge0 against the deployed cluster.

### 7.4 Firewalld

Fedora uses `firewalld`, not `ufw`. Local dev usually means no host firewall is needed (you're behind a router), but if firewalld is enabled and Docker can't reach containers:

```bash
sudo firewall-cmd --permanent --zone=trusted --add-interface=docker0
sudo firewall-cmd --reload
```

### 7.5 What does NOT change on Fedora

- All Spring Boot service code, Maven configs, Dockerfiles
- The Next.js frontend
- The `docker-compose.local.yml` and all the per-VM compose files
- The proto files and gRPC setup
- All GitHub Actions workflows (they run on `ubuntu-latest`, not your local OS)
- The MongoDB, Postgres, and Redis container configurations
- The cgroup *parameters* (only the regenerate-grub command differs)

Everything inside Docker is OS-agnostic. The Fedora-specific notes above are purely about your **host machine** during local dev.

### 7.6 What to mention on camera if you film on Fedora

Two honest sentences:

> "I'm developing on Fedora locally, deploying to Ubuntu 22.04 on GCP. The Spring Boot containers, the gRPC integration, the Judge0 stack — all of it is Docker-based, so the host OS doesn't change anything about the application. The only Fedora-specific notes are SELinux permissive mode and a slightly different `grub2-mkconfig` path, both documented in the repo's `docs/local-dev-fedora.md`."

That's it. Don't make the host OS a teaching moment — it isn't one. The architecture is what matters.

---

## 8. Reference

This is artifact 5 of 5. See:
- **Artifact 1:** `judge0-architecture` — the "what and why"
- **Artifact 2:** `judge0-contracts` — gRPC, REST, schemas
- **Artifact 3:** `judge0-roadmap` — hour-by-hour plan
- **Artifact 4:** `judge0-frontend` — Next.js 15 spec
