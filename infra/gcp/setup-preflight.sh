#!/usr/bin/env bash
# One-time GCP project preflight: enable the APIs Phase 2 needs.
#
# Prerequisites:
#   - gcloud installed and authenticated (`gcloud auth login`)
#   - GCP_PROJECT_ID set to the real project ID (NOT the display name)
#   - Billing linked to the project (in the Cloud Console)

set -euo pipefail

PROJECT_ID="${GCP_PROJECT_ID:?Set GCP_PROJECT_ID}"

echo "→ Active project: $PROJECT_ID"
gcloud config set project "$PROJECT_ID"

echo "→ Verifying billing is linked..."
BILLING_ENABLED=$(gcloud beta billing projects describe "$PROJECT_ID" \
  --format='value(billingEnabled)' 2>/dev/null || echo "false")
if [[ "$BILLING_ENABLED" != "True" && "$BILLING_ENABLED" != "true" ]]; then
  echo "✗ Billing is NOT enabled on $PROJECT_ID."
  echo "  Open https://console.cloud.google.com/billing and link a billing account."
  exit 1
fi
echo "✓ Billing enabled"

echo "→ Enabling required APIs (this can take 1–2 minutes)..."
gcloud services enable \
  compute.googleapis.com \
  iap.googleapis.com \
  iamcredentials.googleapis.com \
  --project="$PROJECT_ID"

echo ""
echo "✓ Preflight complete."
echo "  Next: bash infra/gcp/provision-vm2-early.sh"
