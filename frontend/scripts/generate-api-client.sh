#!/usr/bin/env bash
# Regenerates the typed API client from the backend's OpenAPI document.
# CI fails when the committed client differs from the spec, so the contract
# cannot drift silently.
set -euo pipefail

SPEC_URL="${OPSFLOW_API_DOCS:-http://localhost:8080/v3/api-docs}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

curl -sSf "$SPEC_URL" -o "$HERE/openapi/openapi.json"

rm -rf "$HERE/src/app/core/api/generated"
npx --yes @openapitools/openapi-generator-cli generate \
  -i "$HERE/openapi/openapi.json" \
  -g typescript-angular \
  -o "$HERE/src/app/core/api/generated" \
  --additional-properties=ngVersion=22.1.7,providedInRoot=true,withInterfaces=true,fileNaming=kebab-case,enumPropertyNaming=UPPERCASE,supportsES6=true,useSingleRequestParameter=false

npx prettier --write "$HERE/src/app/core/api/generated/**/*.ts" >/dev/null
