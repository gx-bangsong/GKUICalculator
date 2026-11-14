#!/usr/bin/env bash
# Publish one compact emulator preview through check annotations.
#
# GitHub caps the workflow-command annotations a single step may emit, so the base64 preview has
# to be emitted from a step of its own (see android-ui.yml). Emitting it from the instrumented
# test step truncated the image mid-stream and left every review staring at a half-decoded frame.
#
# usage: ci-publish-preview.sh <image> <label>
set -euo pipefail

image="${1:?usage: ci-publish-preview.sh <image> <label>}"
label="${2:?usage: ci-publish-preview.sh <image> <label>}"

if [[ ! -s "$image" ]]; then
    echo "::notice title=$label::no preview file at $image (see the calculator-ui-screenshots artifact)"
    exit 0
fi

# Keep the payload inside one step's annotation budget: 15000 bytes -> 20000 base64 chars ->
# 7 folded parts.
size=$(stat -c %s "$image" 2>/dev/null || stat -f %z "$image")
if ((size > 15000)); then
    echo "::notice title=$label::preview is $size bytes, too large to embed; see the calculator-ui-screenshots artifact"
    exit 0
fi

part=1
while IFS= read -r chunk; do
    echo "::notice title=$label base64 part $part::$chunk"
    part=$((part + 1))
done < <(base64 < "$image" | tr -d '\n' | fold -w 3000)
