#!/usr/bin/env bash
# Phase 2 (Path A): provision a single GCP VM (VM 2) ready to run Judge0.
#
# What it creates (all idempotent — safe to re-run):
#   - VPC network    `codearena-net`
#   - Subnet         `codearena-subnet` (10.0.0.0/24, asia-south1)
#   - Firewall       `codearena-allow-iap`        — IAP CIDR → tcp:22, tcp:2358
#   - Firewall       `codearena-allow-internal`   — VPC-internal traffic
#   - VM instance    `codearena-vm2` (e2-medium, Ubuntu 22.04 LTS, 30 GB pd-balanced)
#                    Startup script installs Docker + applies the cgroup v1 GRUB
#                    edit + reboots once. No external IP — IAP tunnel only.
#
# The full 3-VM topology lives in Phase 8. This script is the slimmed-down
# version per docs/roadmap.md Phase 2 Path A.
#
# Prerequisites:
#   - infra/gcp/setup-preflight.sh has been run successfully

set -euo pipefail

PROJECT_ID="${GCP_PROJECT_ID:?Set GCP_PROJECT_ID}"
REGION="${GCP_REGION:-asia-south1}"
ZONE="${GCP_ZONE:-asia-south1-a}"
NETWORK="codearena-net"
SUBNET="codearena-subnet"
VM_NAME="codearena-vm2"
MACHINE_TYPE="e2-medium"

echo "→ Project: $PROJECT_ID"
echo "→ Region:  $REGION (zone $ZONE)"
echo "→ VM:      $VM_NAME ($MACHINE_TYPE)"
echo ""

# ─── Network ─────────────────────────────────────────────────────────
if gcloud compute networks describe "$NETWORK" --project="$PROJECT_ID" >/dev/null 2>&1; then
  echo "✓ Network $NETWORK already exists"
else
  echo "→ Creating VPC $NETWORK..."
  gcloud compute networks create "$NETWORK" \
    --project="$PROJECT_ID" \
    --subnet-mode=custom
fi

if gcloud compute networks subnets describe "$SUBNET" \
     --region="$REGION" --project="$PROJECT_ID" >/dev/null 2>&1; then
  echo "✓ Subnet $SUBNET already exists"
else
  echo "→ Creating subnet $SUBNET..."
  gcloud compute networks subnets create "$SUBNET" \
    --project="$PROJECT_ID" \
    --network="$NETWORK" \
    --region="$REGION" \
    --range=10.0.0.0/24
fi

# ─── Firewall ────────────────────────────────────────────────────────
# IAP tunnel reaches our VMs from this fixed CIDR.
if gcloud compute firewall-rules describe codearena-allow-iap \
     --project="$PROJECT_ID" >/dev/null 2>&1; then
  echo "✓ Firewall codearena-allow-iap already exists"
else
  echo "→ Creating firewall codearena-allow-iap (tcp:22, tcp:2358 from IAP)..."
  gcloud compute firewall-rules create codearena-allow-iap \
    --project="$PROJECT_ID" \
    --network="$NETWORK" \
    --source-ranges=35.235.240.0/20 \
    --allow=tcp:22,tcp:2358
fi

# Internal traffic — used in Phase 8 when VM 3 talks to VM 2's shared state.
if gcloud compute firewall-rules describe codearena-allow-internal \
     --project="$PROJECT_ID" >/dev/null 2>&1; then
  echo "✓ Firewall codearena-allow-internal already exists"
else
  echo "→ Creating firewall codearena-allow-internal..."
  gcloud compute firewall-rules create codearena-allow-internal \
    --project="$PROJECT_ID" \
    --network="$NETWORK" \
    --source-ranges=10.0.0.0/24 \
    --allow=tcp,udp,icmp
fi

# ─── Startup script (runs as root on first boot) ────────────────────
read -r -d '' STARTUP_SCRIPT <<'EOF' || true
#!/bin/bash
set -e

MARKER=/var/lib/codearena-bootstrapped
if [ -f "$MARKER" ]; then
  echo "Already bootstrapped on prior boot."
  exit 0
fi

# 1. Docker
apt-get update
apt-get install -y docker.io docker-compose-plugin
systemctl enable --now docker
usermod -aG docker ubuntu || true

# 2. Cgroup v1 GRUB edit (Judge0's isolate engine requires v1)
GRUB=/etc/default/grub
PARAMS='systemd.unified_cgroup_hierarchy=0 systemd.legacy_systemd_cgroup_controller=1'
if ! grep -q 'systemd.unified_cgroup_hierarchy=0' "$GRUB"; then
  cp "$GRUB" "${GRUB}.bak.$(date +%s)"
  sed -i "s|GRUB_CMDLINE_LINUX_DEFAULT=\"\(.*\)\"|GRUB_CMDLINE_LINUX_DEFAULT=\"\1 ${PARAMS}\"|" "$GRUB"
  update-grub
fi

touch "$MARKER"
echo "Bootstrap complete; rebooting into cgroup v1 in 60s..."
shutdown -r +1
EOF

# ─── VM ──────────────────────────────────────────────────────────────
if gcloud compute instances describe "$VM_NAME" \
     --zone="$ZONE" --project="$PROJECT_ID" >/dev/null 2>&1; then
  echo "✓ VM $VM_NAME already exists — skipping creation."
else
  echo "→ Creating VM $VM_NAME..."
  gcloud compute instances create "$VM_NAME" \
    --project="$PROJECT_ID" \
    --zone="$ZONE" \
    --machine-type="$MACHINE_TYPE" \
    --network="$NETWORK" \
    --subnet="$SUBNET" \
    --no-address \
    --image-family=ubuntu-2204-lts \
    --image-project=ubuntu-os-cloud \
    --boot-disk-size=30GB \
    --boot-disk-type=pd-balanced \
    --metadata=startup-script="$STARTUP_SCRIPT"
fi

cat <<NEXT

────────────────────────────────────────────────────────────────────
✓ VM is being provisioned.

The startup script:
  1. Installs Docker  (~30 s)
  2. Applies the cgroup v1 GRUB edit
  3. Reboots in 60 s

Wait ~3 minutes total, then verify cgroup v1 took effect:

  gcloud compute ssh $VM_NAME --zone=$ZONE --tunnel-through-iap \\
    --command='mount | grep "type cgroup " | head -3'

Expected: lines like  cgroup on /sys/fs/cgroup/memory type cgroup ...
If you see  cgroup2 on /sys/fs/cgroup type cgroup2  the reboot has not
finished yet — wait another minute and retry.

Once cgroup v1 is confirmed:
  make judge0-deploy GCP_PROJECT_ID=$PROJECT_ID
────────────────────────────────────────────────────────────────────
NEXT
