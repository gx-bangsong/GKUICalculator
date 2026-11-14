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

# Prefer the compact JPEG twin, but fall back to the raw screenshot when the runner had no
# image converter to produce the twin.
if [[ "$image" == *-review.jpg && ! -s "$image" ]]; then
    candidate="${image%-review.jpg}.png"
    if [[ -s "$candidate" ]]; then
        image="$candidate"
    fi
fi

if [[ ! -s "$image" ]]; then
    echo "::notice title=$label::no preview at $image; dir: $(ls -la "$(dirname "$image")" 2>&1 | tr '\n' ' ' | sed -e 's/%/%25/g')"
    exit 0
fi

# The hosted runner has no image converter at all (no magick/convert/ffmpeg/sips), so the compact
# JPEG twin often does not exist and the raw PNG is the only frame to embed. Keep the payload
# inside one step's annotation budget: 90000 bytes -> ~120000 base64 chars -> 9 folded parts of
# 12000 chars (each well under the 64 KiB annotation message limit).
size=$(stat -c %s "$image" 2>/dev/null || stat -f %z "$image")
if ((size > 90000)); then
    echo "::notice title=$label::preview is $size bytes, too large to embed; see the calculator-ui-screenshots artifact"
    exit 0
fi

part=1
while IFS= read -r chunk; do
    echo "::notice title=$label base64 part $part::$chunk"
    part=$((part + 1))
done < <(base64 < "$image" | tr -d '\n' | fold -w 12000)
