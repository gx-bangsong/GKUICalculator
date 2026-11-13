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
from html.parser import HTMLParser
from pathlib import Path
import re
import xml.etree.ElementTree as ET

build = Path("build")
xml_reports = sorted(
    path for path in build.rglob("*.xml")
    if any(token in str(path).lower() for token in ("androidtest", "test-results", "connected"))
)
found_failure = False
for report in xml_reports:
    try:
        root = ET.parse(report).getroot()
    except ET.ParseError:
        continue
    for test in root.iter("testcase"):
        for failure in list(test.findall("failure")) + list(test.findall("error")):
            found_failure = True
            message = (failure.get("message") or "").strip()
            detail = " ".join("".join(failure.itertext()).split())
            summary = " | ".join(part for part in (message, detail) if part)
            label = ".".join(part for part in (test.get("classname"), test.get("name")) if part)
            print(f"{report.name} {label}: {summary[:1800]}")

if not found_failure:
    class VisibleText(HTMLParser):
        def __init__(self):
            super().__init__()
            self.parts = []
            self.hidden = 0
        def handle_starttag(self, tag, attrs):
            if tag in ("script", "style"):
                self.hidden += 1
        def handle_endtag(self, tag):
            if tag in ("script", "style") and self.hidden:
                self.hidden -= 1
        def handle_data(self, data):
            if not self.hidden:
                text = " ".join(data.split())
                if text:
                    self.parts.append(text)

    reports = Path("build/reports/androidTests/connected/debug")
    for report in sorted(reports.rglob("*.html")) if reports.exists() else []:
        parser = VisibleText()
        try:
            parser.feed(report.read_text(errors="replace"))
        except OSError:
            continue
        text = " ".join(parser.parts)
        if "captureCalculatorScreenshot" in text:
            start = text.index("captureCalculatorScreenshot")
            print(f"{report.name}: {text[max(0, start - 300):start + 2200]}")
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

# Capture the device display even if an Espresso assertion failed before its in-app PNG save.
if [[ ! -s build/ui-screenshots/calculator-default.png ]]; then
    adb exec-out screencap -p > build/ui-screenshots/calculator-default.png 2>/dev/null || true
    if [[ ! -s build/ui-screenshots/calculator-default.png ]]; then
        rm -f build/ui-screenshots/calculator-default.png
    fi
fi

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
