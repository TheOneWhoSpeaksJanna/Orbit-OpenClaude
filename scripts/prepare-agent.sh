#!/usr/bin/env bash
# prepare-agent.sh - Pre-download and bundle an agent for APK inclusion
# Usage: ./scripts/prepare-agent.sh <flavor>
# Flavors: opencode, openclaude, claudecode, codex
#
# This downloads the npm package, installs production dependencies,
# and creates a compressed archive in the flavor's assets directory.
# The archive is included in the APK via Android's asset merging.
#
# Requires: npm, tar, gzip

set -euo pipefail

FLAVOR="${1:?Usage: $0 <flavor>}"

declare -A AGENT_PACKAGES
declare -A AGENT_COMMANDS
declare -A AGENT_DIRS

AGENT_PACKAGES[opencode]="@opencode-ai/cli"
AGENT_COMMANDS[opencode]="lildax"

AGENT_PACKAGES[openclaude]="@gitlawb/openclaude"
AGENT_COMMANDS[openclaude]="openclaude"

AGENT_PACKAGES[claudecode]="@anthropic-ai/claude-code"
AGENT_COMMANDS[claudecode]="claude"

AGENT_PACKAGES[codex]="@openai/codex"
AGENT_COMMANDS[codex]="codex"

PACKAGE="${AGENT_PACKAGES[$FLAVOR]:-}"
COMMAND="${AGENT_COMMANDS[$FLAVOR]:-}"

if [[ -z "$PACKAGE" ]]; then
  echo "ERROR: Unknown flavor '$FLAVOR'. Valid: opencode, openclaude, claudecode, codex"
  exit 1
fi

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS_DIR="${PROJECT_DIR}/app/src/${FLAVOR}/assets"
WORK_DIR="${PROJECT_DIR}/build/prepared-agents/${FLAVOR}"
AGENT_DIR="${WORK_DIR}/agent"

echo "==> Preparing agent for flavor: $FLAVOR"
echo "    Package: $PACKAGE"
echo "    Command: $COMMAND"
echo "    Assets:  $ASSETS_DIR"

mkdir -p "$ASSETS_DIR" "$WORK_DIR"
rm -rf "$AGENT_DIR"
mkdir -p "$AGENT_DIR"

cd "$WORK_DIR"

# Download the npm package tarball
echo "==> Downloading $PACKAGE..."
TARBALL_FILE="${FLAVOR}.tgz"
npm pack "$PACKAGE" --pack-destination "$WORK_DIR" 2>/dev/null || {
  echo "ERROR: Failed to download $PACKAGE. Is npm logged in / package accessible?"
  exit 1
}

# Find the generated tarball (npm pack creates PACKAGE-VERSION.tgz or NAME.tgz)
GENERATED_TAR=""
for f in *.tgz; do
  if [ -f "$f" ]; then
    GENERATED_TAR="$f"
    break
  fi
done

if [[ -z "$GENERATED_TAR" ]]; then
  echo "ERROR: No tarball was created by npm pack"
  exit 1
fi

echo "==> Extracting $GENERATED_TAR..."
tar -xzf "$GENERATED_TAR" -C "$AGENT_DIR"
rm "$GENERATED_TAR"

# npm pack extracts to "package/" inside the target dir
if [ -d "${AGENT_DIR}/package" ]; then
  # Move contents up
  cd "$AGENT_DIR"
  mv package/* . 2>/dev/null || true
  mv package/.* . 2>/dev/null || true
  rmdir package 2>/dev/null || true
  cd "$WORK_DIR"
fi

# Install production dependencies
echo "==> Installing production dependencies..."
cd "$AGENT_DIR"
npm install --production 2>&1 | tail -10 || echo "    (npm install completed with warnings)"
cd "$WORK_DIR"

# Remove non-essential files to reduce size
echo "==> Cleaning up..."
find "$AGENT_DIR" -name "*.md" -not -name "README.md" -delete 2>/dev/null || true
find "$AGENT_DIR" -name "LICENSE*" -delete 2>/dev/null || true
find "$AGENT_DIR" -name "*.map" -delete 2>/dev/null || true
find "$AGENT_DIR" -name ".npmignore" -delete 2>/dev/null || true
find "$AGENT_DIR" -name ".gitignore" -delete 2>/dev/null || true
find "$AGENT_DIR" -name "CHANGELOG*" -delete 2>/dev/null || true
find "$AGENT_DIR" -name "test" -type d -exec rm -rf {} + 2>/dev/null || true
find "$AGENT_DIR" -name "tests" -type d -exec rm -rf {} + 2>/dev/null || true
find "$AGENT_DIR" -name "__tests__" -type d -exec rm -rf {} + 2>/dev/null || true

# Create the compressed archive
echo "==> Creating agent archive..."
ARCHIVE_PATH="${ASSETS_DIR}/agent.tar.gz"
tar -czf "$ARCHIVE_PATH" -C "$AGENT_DIR" .

ARCHIVE_SIZE=$(du -h "$ARCHIVE_PATH" | cut -f1)
echo "==> Done! Agent archive created:"
echo "    Path: $ARCHIVE_PATH"
echo "    Size: $ARCHIVE_SIZE"

# Create metadata file for runtime use
cat > "${ASSETS_DIR}/agent.meta" << EOF
package=$PACKAGE
command=$COMMAND
flavor=$FLAVOR
size=$(stat -c%s "$ARCHIVE_PATH" 2>/dev/null || stat -f%z "$ARCHIVE_PATH" 2>/dev/null)
built=$(date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u +%Y-%m-%dT%H:%M:%SZ)
EOF

echo "    Meta:  ${ASSETS_DIR}/agent.meta"
echo ""
echo "To rebuild, run: rm -f $ARCHIVE_PATH && $0 $FLAVOR"
