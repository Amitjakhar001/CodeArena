#!/usr/bin/env bash
# Push the Judge0 docker-compose stack to VM 2 and start it.
# Generates random passwords for Postgres and Redis on first run.

set -euo pipefail

PROJECT_ID="${GCP_PROJECT_ID:?Set GCP_PROJECT_ID}"
ZONE="${GCP_ZONE:-asia-south1-a}"
VM_NAME="codearena-vm2"
COMPOSE_FILE="infra/docker/docker-compose.judge0.yml"
ENV_FILE="infra/docker/.env.judge0"

if [ ! -f "$COMPOSE_FILE" ]; then
  echo "✗ $COMPOSE_FILE not found — run from the repo root."
  exit 1
fi

# Generate passwords on first deploy. The file is gitignored.
if [ ! -f "$ENV_FILE" ]; then
  echo "→ Generating random passwords in $ENV_FILE..."
  cat > "$ENV_FILE" <<EOF
JUDGE0_DB_PASSWORD=$(openssl rand -hex 24)
JUDGE0_REDIS_PASSWORD=$(openssl rand -hex 24)
EOF
  chmod 600 "$ENV_FILE"
  echo "✓ Wrote $ENV_FILE (gitignored, 0600)"
fi

echo "→ Copying compose + env file to $VM_NAME..."
gcloud compute scp \
  "$COMPOSE_FILE" \
  "$ENV_FILE" \
  "$VM_NAME":~/ \
  --zone="$ZONE" \
  --tunnel-through-iap \
  --project="$PROJECT_ID"

echo "→ Starting Judge0 stack on $VM_NAME..."
gcloud compute ssh "$VM_NAME" \
  --zone="$ZONE" \
  --tunnel-through-iap \
  --project="$PROJECT_ID" \
  --command='set -e
    cd ~
    mv -f .env.judge0 .env
    sudo docker compose -f docker-compose.judge0.yml pull
    sudo docker compose -f docker-compose.judge0.yml up -d
    sleep 5
    sudo docker compose -f docker-compose.judge0.yml ps'

cat <<NEXT

────────────────────────────────────────────────────────────────────
✓ Judge0 stack started on $VM_NAME.

To talk to it from your laptop, open the IAP tunnel:

    make judge0-tunnel

Then in another terminal:

    curl http://localhost:2358/about

Or run the full smoke test in IntelliJ HTTP Client:
    http/judge0-direct.http   (with env = "local")
────────────────────────────────────────────────────────────────────
NEXT
