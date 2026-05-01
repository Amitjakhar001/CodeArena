#!/usr/bin/env bash
# Phase 2 (Path A): provision a single GCP VM (VM 2) ready to run Judge0.
#
# What it creates (all idempotent — safe to re-run):
#   - VPC network    `codearena-net`
#   - Subnet         `codearena-subnet` (10.0.0.0/24, asia-south1)
#   - Cloud Router   `codearena-router`           — needed by Cloud NAT
#   - Cloud NAT      `codearena-nat`              — outbound internet for the VM
#                                                   (apt mirrors, Docker Hub, …)
#                                                   since VMs have --no-address.
#   - Firewall       `codearena-allow-iap`        — IAP CIDR → tcp:22, tcp:2358
#   - Firewall       `codearena-allow-internal`   — VPC-internal traffic
#   - VM instance    `codearena-vm2` (e2-medium, Ubuntu 22.04 LTS, 30 GB pd-balanced)
#                    Startup script installs Docker CE from Docker's official apt
#                    repo, applies the cgroup v1 fix via a GRUB drop-in file,
#                    reboots once. No external IP — IAP tunnel only.
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
ROUTER="codearena-router"
NAT="codearena-nat"
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

# ─── Cloud Router + Cloud NAT ────────────────────────────────────────
# Without these, a VM created with --no-address has no path to the public
# internet, so apt-get update / Docker Hub pulls fail silently or partially.
# Cloud NAT gives the VM outbound-only access through Google's NAT gateway;
# inbound is still IAP-only.
if gcloud compute routers describe "$ROUTER" \
     --region="$REGION" --project="$PROJECT_ID" >/dev/null 2>&1; then
  echo "✓ Cloud Router $ROUTER already exists"
else
  echo "→ Creating Cloud Router $ROUTER..."
  gcloud compute routers create "$ROUTER" \
    --project="$PROJECT_ID" \
    --network="$NETWORK" \
    --region="$REGION"
fi

if gcloud compute routers nats describe "$NAT" \
     --router="$ROUTER" --router-region="$REGION" \
     --project="$PROJECT_ID" >/dev/null 2>&1; then
  echo "✓ Cloud NAT $NAT already exists"
else
  echo "→ Creating Cloud NAT $NAT..."
  gcloud compute routers nats create "$NAT" \
    --project="$PROJECT_ID" \
    --router="$ROUTER" \
    --router-region="$REGION" \
    --auto-allocate-nat-external-ips \
    --nat-all-subnet-ip-ranges
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

# 1. Docker — install Docker CE from Docker's official apt repo.
#    Ubuntu's docker.io was unreliable on a fresh VM (the package list
#    wasn't always populated when startup-script ran), and ships Compose v1
#    by default. Docker CE gives us docker-compose-plugin (Compose v2) which
#    is what every compose file in this repo expects.
export DEBIAN_FRONTEND=noninteractive

# Wait for any background apt operations (cloud-init, unattended-upgrades)
# to finish so we don't deadlock on the dpkg lock.
for i in $(seq 1 60); do
  if ! pgrep -x apt-get >/dev/null && ! pgrep -x dpkg >/dev/null; then
    break
  fi
  sleep 2
done

apt-get update
apt-get install -y ca-certificates curl gnupg lsb-release

install -m 0755 -d /etc/apt/keyrings
if [ ! -f /etc/apt/keyrings/docker.gpg ]; then
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
    | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  chmod a+r /etc/apt/keyrings/docker.gpg
fi

CODENAME=$(. /etc/os-release && echo "$VERSION_CODENAME")
ARCH=$(dpkg --print-architecture)
echo "deb [arch=${ARCH} signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu ${CODENAME} stable" \
  > /etc/apt/sources.list.d/docker.list

apt-get update
apt-get install -y \
  docker-ce \
  docker-ce-cli \
  containerd.io \
  docker-buildx-plugin \
  docker-compose-plugin

systemctl enable --now docker
usermod -aG docker ubuntu || true

# 2. Cgroup v1 — Judge0's isolate engine requires the v1 hierarchy.
#    Drop a file into /etc/default/grub.d/ instead of editing the main grub
#    config; the drop-in is sourced after /etc/default/grub by update-grub
#    and uses GRUB_CMDLINE_LINUX (applies to all kernel entries, including
#    recovery), which is the right knob for boot-time kernel parameters.
mkdir -p /etc/default/grub.d
cat > /etc/default/grub.d/99-cgroup.cfg <<'GRUB_EOF'
# Force cgroup v1 hierarchy for Judge0's isolate sandbox.
GRUB_CMDLINE_LINUX="$GRUB_CMDLINE_LINUX systemd.unified_cgroup_hierarchy=0 systemd.legacy_systemd_cgroup_controller=1"
GRUB_EOF
update-grub

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
  1. Installs Docker CE from Docker's official apt repo (~60 s)
  2. Applies the cgroup v1 fix via /etc/default/grub.d/99-cgroup.cfg
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
