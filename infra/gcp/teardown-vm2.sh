#!/usr/bin/env bash
# Phase 2 cleanup. Deletes VM 2 and the network resources created by
# provision-vm2-early.sh. Use between dev sessions to stop billing.
#
# This is destructive — Judge0 data on VM 2 is wiped. Re-running
# provision-vm2-early.sh + deploy-judge0-vm2.sh restores a fresh stack.

set -euo pipefail

PROJECT_ID="${GCP_PROJECT_ID:?Set GCP_PROJECT_ID}"
ZONE="${GCP_ZONE:-asia-south1-a}"
REGION="${GCP_REGION:-asia-south1}"
VM_NAME="codearena-vm2"

read -r -p "This deletes $VM_NAME and the VPC. Type the project ID ($PROJECT_ID) to confirm: " confirm
if [[ "$confirm" != "$PROJECT_ID" ]]; then
  echo "Aborted."
  exit 1
fi

echo "→ Deleting VM..."
gcloud compute instances delete "$VM_NAME" \
  --zone="$ZONE" --project="$PROJECT_ID" --quiet || true

# Cloud NAT must be deleted before its router, and the router before the
# subnet/network it lives on. Each step is best-effort idempotent.
if gcloud compute routers nats describe codearena-nat \
     --router=codearena-router --router-region="$REGION" \
     --project="$PROJECT_ID" >/dev/null 2>&1; then
  echo "→ Deleting Cloud NAT..."
  gcloud compute routers nats delete codearena-nat \
    --router=codearena-router --router-region="$REGION" \
    --project="$PROJECT_ID" --quiet || true
else
  echo "✓ Cloud NAT already absent"
fi

if gcloud compute routers describe codearena-router \
     --region="$REGION" --project="$PROJECT_ID" >/dev/null 2>&1; then
  echo "→ Deleting Cloud Router..."
  gcloud compute routers delete codearena-router \
    --region="$REGION" --project="$PROJECT_ID" --quiet || true
else
  echo "✓ Cloud Router already absent"
fi

echo "→ Deleting firewall rules..."
gcloud compute firewall-rules delete \
  codearena-allow-iap codearena-allow-internal \
  --project="$PROJECT_ID" --quiet || true

echo "→ Deleting subnet + network..."
gcloud compute networks subnets delete codearena-subnet \
  --region="$REGION" --project="$PROJECT_ID" --quiet || true
gcloud compute networks delete codearena-net \
  --project="$PROJECT_ID" --quiet || true

echo ""
echo "✓ Teardown complete. Verify in the Cloud Console:"
echo "  https://console.cloud.google.com/compute/instances?project=$PROJECT_ID"
