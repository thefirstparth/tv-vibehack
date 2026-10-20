#!/bin/bash
# Copy every stored day from the insights site (Vercel Blob, private) to backup/insights/ on this computer
# (gitignored): the per-arrival archive (days/), the month files (months/) and status.json. Files already here are
# skipped, so it only fetches what is new. Runs weekly from launchd (tools/insights-backup.plist); safe to run by hand.
#
# Needs the Vercel CLI login on this computer and the project link in insights/ (npx vercel link).
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=backup/insights
mkdir -p "$OUT"
cd insights
# the store's read-write token, from the project's environment (written to a file only for the run, then removed)
TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT
npx --yes vercel env pull "$TMP" --environment=production --yes >/dev/null 2>&1
export BLOB_READ_WRITE_TOKEN=$(grep '^BLOB_READ_WRITE_TOKEN=' "$TMP" | cut -d= -f2- | tr -d '"')
[ -n "$BLOB_READ_WRITE_TOKEN" ] || { echo "no Blob token: is this computer logged in to Vercel?"; exit 1; }
node --input-type=module - "../$OUT" <<'EOF'
import { list, get } from "@vercel/blob";
import fs from "node:fs";
import path from "node:path";
const out = process.argv[2];
let cursor, got = 0, had = 0;
do {
  const page = await list({ cursor, limit: 1000 });
  for (const b of page.blobs) {
    const file = path.join(out, b.pathname);
    // month files and status change; days never do
    if (fs.existsSync(file) && b.pathname.startsWith("days/")) { had++; continue; }
    const r = await get(b.pathname, { access: "private", useCache: false });
    if (!r?.stream) continue;
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, Buffer.from(await new Response(r.stream).arrayBuffer()));
    got++;
  }
  cursor = page.hasMore ? page.cursor : undefined;
} while (cursor);
console.log(`${new Date().toISOString()} backup: ${got} fetched, ${had} already here`);
EOF
