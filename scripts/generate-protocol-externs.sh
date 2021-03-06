#!/usr/bin/env bash

set -e

. "$(dirname "${BASH_SOURCE[0]}")/config.sh"

pushd "$DEVTOOLS_ROOT"

python scripts/generate_protocol_externs.py -o "$PROTOCOL_EXTERNS_OUTPUT_FILE" "$BROWSER_PROTOCOL_JSON_FILE" "$V8_PROTOCOL_JSON_FILE"
echo "Generated fresh $PROTOCOL_EXTERNS_OUTPUT_FILE"

popd
