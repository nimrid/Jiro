#!/usr/bin/env bash
set -euo pipefail

echo "=== Verifying Mainnet IDL Contains No Mock Instructions ==="

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROGRAM_DIR="$ROOT_DIR/program"
IDL_PATH="$PROGRAM_DIR/target/idl/vault.json"

# Build with mainnet feature (and without default mock-swap)
echo "Building program with --no-default-features --features mainnet..."
(cd "$PROGRAM_DIR" && anchor build -- --no-default-features --features mainnet)

if [ ! -f "$IDL_PATH" ]; then
    echo "ERROR: IDL file not found at $IDL_PATH"
    exit 1
fi

# Check for any instruction starting with "mock"
MOCK_COUNT=$(grep -o '"name": "mock_[^"]*"' "$IDL_PATH" | wc -l || true)

if [ "$MOCK_COUNT" -gt 0 ]; then
    echo "SECURITY CHECK FAILED: Found $MOCK_COUNT mock_* instruction(s) in mainnet IDL!"
    grep -o '"name": "mock_[^"]*"' "$IDL_PATH"
    exit 1
fi

echo "SUCCESS: Mainnet IDL contains 0 mock instructions. Verified clean."
exit 0
