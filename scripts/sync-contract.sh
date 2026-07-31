#!/usr/bin/env bash
#
# Refresh the vendored folicular API contract from an upstream checkout.
#
#   ./scripts/sync-contract.sh                  # defaults to ~/Projects/folicular
#   ./scripts/sync-contract.sh /path/to/folicular
#
# See contract/README.md. Upstream owns the contract; this directory is only
# ever a snapshot of it.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
upstream="${1:-$HOME/Projects/folicular}"

spec="$upstream/openapi/openapi.yaml"
fixtures="$upstream/conformance"

if [ ! -f "$spec" ]; then
    echo "no spec at $spec" >&2
    echo "pass the folicular checkout as the first argument" >&2
    exit 1
fi

if [ ! -d "$fixtures" ]; then
    echo "no conformance fixtures at $fixtures" >&2
    exit 1
fi

cp "$spec" "$repo_root/contract/openapi.yaml"

rm -f "$repo_root"/contract/conformance/*.json
cp "$fixtures"/*.json "$repo_root/contract/conformance/"

echo "synced contract from $upstream"
git -C "$repo_root" status --short contract/
echo
echo "rebuild to regenerate the DTOs, then: ./gradlew testDebugUnitTest"
