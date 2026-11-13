#!/usr/bin/env bash
set -euo pipefail

mkdir -p build/ui-screenshots
log_file=build/connected-debug-android-test.log

# Avoid Play Protect timing out while this unaccelerated CI emulator installs the debug APK.
adb shell settings put global verifier_verify_adb_installs 0 || true
adb shell settings put global package_verifier_enable 0 || true

if ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.android.calculator2.CalculatorToolUiTest#captureCalculatorScreenshot --stacktrace --console=plain >"$log_file" 2>&1; then
    test_result=0
else
    test_result=$?
fi
cat "$log_file"

if ((test_result != 0)); then
    diagnostics=$(grep -nEi 'error:|what went wrong|execution failed|caused by:|assertionerror|tests run:|FAILED|^failure:' "$log_file" | tail -n 60 || true)
    if [[ -z "$diagnostics" ]]; then
        diagnostics=$(tail -n 60 "$log_file")
    fi
    diagnostics=$(printf '%s\n' "$diagnostics" | sed -e 's/%/%25/g' -e 's/\r/%0D/g' -e ':a' -e 'N' -e '$!ba' -e 's/\n/%0A/g')
    echo "::error title=connectedDebugAndroidTest failed::$diagnostics"

    failure_summary=$(python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

results = Path("build/outputs/androidTest-results")
for report in sorted(results.rglob("*.xml")) if results.exists() else []:
    try:
        root = ET.parse(report).getroot()
    except ET.ParseError:
        continue
    for test in root.iter("testcase"):
        for failure in list(test.findall("failure")) + list(test.findall("error")):
            message = (failure.get("message") or "").strip()
            detail = " ".join("".join(failure.itertext()).split())
            summary = " | ".join(part for part in (message, detail) if part)
            label = ".".join(part for part in (test.get("classname"), test.get("name")) if part)
            print(f"{label}: {summary[:1800]}")
PY
)
    if [[ -n "$failure_summary" ]]; then
        failure_summary=$(printf '%s\n' "$failure_summary" | sed -e 's/%/%25/g' -e 's/\r/%0D/g' -e ':a' -e 'N' -e '$!ba' -e 's/\n/%0A/g')
        echo "::error title=Instrumented test failure details::$failure_summary"
    fi
fi

screenshot_dir=/sdcard/Android/data/com.android.calculator2.dev/files/ui-screenshots
adb pull "$screenshot_dir/calculator-default.png" build/ui-screenshots/ || true
adb pull "$screenshot_dir/calculator-units.png" build/ui-screenshots/ || true

emit_review_preview() {
    local image="$1"
    local label="$2"
    local preview="${image%.png}-review.jpg"
    local part=1
    if ! command -v sips >/dev/null || [[ ! -f "$image" ]]; then
        return
    fi
    if ! sips -s format jpeg -s formatOptions 60 -Z 720 "$image" --out "$preview" >/dev/null 2>&1; then
        return
    fi
    while IFS= read -r chunk; do
        echo "::notice title=$label part $part::$chunk"
        part=$((part + 1))
    done < <(base64 < "$preview" | tr -d '\n' | fold -w 8000)
}

# Keep original PNGs as artifacts; small JPEG previews are carried in check annotations for review.
emit_review_preview build/ui-screenshots/calculator-default.png "VM calculator screenshot"
emit_review_preview build/ui-screenshots/calculator-units.png "VM unit-converter screenshot"

exit "$test_result"
