#!/usr/bin/env bash
# Publish one compact emulator preview through check annotations.
#
# GitHub caps the workflow-command annotations a single step may emit, so the base64 preview has
# to be emitted from a step of its own (see android-ui.yml). Emitting it from the instrumented
# test step truncated the image mid-stream and left every review staring at a half-decoded frame.
#
# usage: ci-publish-preview.sh <image> <label> [skip-parts]
#
# A third argument skips the first N base64 parts so one image can be published across several
# steps when it needs more parts than a single step may carry.
set -euo pipefail

image="${1:?usage: ci-publish-preview.sh <image> <label> [skip-parts]}"
label="${2:?usage: ci-publish-preview.sh <image> <label> [skip-parts]}"
skip="${3:-0}"

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
# JPEG twin often does not exist and the raw PNG is the only frame to embed. Workflow-command
# annotation messages are cut at 4096 characters, so fold just under that; a 70 KB frame then
# needs ~24 parts, published across three steps via the skip-parts argument.
size=$(stat -c %s "$image" 2>/dev/null || stat -f %z "$image")
if ((size > 90000)); then
    echo "::notice title=$label::preview is $size bytes, too large to embed; see the calculator-ui-screenshots artifact"
    exit 0
fi

part=1
while IFS= read -r chunk; do
    if ((part > skip)); then
        echo "::notice title=$label base64 part $part::$chunk"
    fi
    part=$((part + 1))
done < <(base64 < "$image" | tr -d '\n' | fold -w 4000)
