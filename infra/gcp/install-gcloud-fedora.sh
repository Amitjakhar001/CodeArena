#!/usr/bin/env bash
# One-time installer for the Google Cloud CLI on Fedora.
# Run this on your Fedora workstation, not on a VM.
#
# After install:
#   gcloud auth login          # browser-based auth
#   gcloud projects list       # find your real PROJECT_ID
#   export GCP_PROJECT_ID=...  # use that ID for the other scripts

set -euo pipefail

if command -v gcloud >/dev/null 2>&1; then
  echo "✓ gcloud already installed: $(gcloud --version | head -1)"
  exit 0
fi

echo "→ Adding Google Cloud CLI dnf repo..."
sudo tee /etc/yum.repos.d/google-cloud-sdk.repo >/dev/null <<'EOF'
[google-cloud-cli]
name=Google Cloud CLI
baseurl=https://packages.cloud.google.com/yum/repos/cloud-sdk-el9-x86_64
enabled=1
gpgcheck=1
repo_gpgcheck=0
gpgkey=https://packages.cloud.google.com/yum/doc/rpm-package-key.gpg
EOF

echo "→ Installing google-cloud-cli..."
sudo dnf install -y google-cloud-cli

echo ""
echo "✓ Installed: $(gcloud --version | head -1)"
echo ""
echo "Next steps:"
echo "  1. gcloud auth login"
echo "  2. gcloud projects list           # confirm your project ID"
echo "  3. export GCP_PROJECT_ID=<id>     # the actual ID, not the display name"
echo "  4. bash infra/gcp/setup-preflight.sh"
