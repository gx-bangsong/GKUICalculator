#!/usr/bin/env bash
set -euo pipefail

mkdir -p build/ui-screenshots
log_file=build/connected-debug-android-test.log

# Avoid Play Protect timing out while this unaccelerated CI emulator installs the debug APK.
adb shell settings put global verifier_verify_adb_installs 0 || true
adb shell settings put global package_verifier_enable 0 || true

# Keep the headless AVD awake and unlocked. Fresh emulator boots occasionally leave the
# System UI "isn't responding" prompt over the launcher, which prevents Espresso from getting
# window focus. Choose Wait if that dialog is present, then give System UI time to recover.
adb shell svc power stayon true || true
adb shell settings put system screen_off_timeout 600000 || true
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true
adb shell input keyevent 82 || true
display_size=$(adb shell wm size 2>/dev/null | tr -d '\r' | sed -n 's/.*size: \([0-9][0-9]*x[0-9][0-9]*\).*/\1/p' | tail -n 1)
wait_x=540
wait_y=1094
if [[ "$display_size" =~ ^([0-9]+)x([0-9]+)$ ]]; then
    wait_x=$((${BASH_REMATCH[1]} / 2))
    wait_y=$((${BASH_REMATCH[2]} * 57 / 100))
else
    echo "::warning title=Android emulator wakeup::Could not parse the display size '$display_size'; using Pixel 2 Wait coordinates."
fi
echo "::notice title=Android emulator wakeup::Display ${display_size:-unknown}; tapping the system dialog Wait position at $wait_x,$wait_y if present."
for attempt in 1 2 3; do
    adb shell input tap "$wait_x" "$wait_y" || true
    sleep 2
done
adb shell wm dismiss-keyguard || true
adb shell input keyevent KEYCODE_WAKEUP || true

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

roots = [
    Path("build/outputs/androidTest-results"),
    Path("build/reports/androidTests/connected/debug"),
]
report_files = sorted(
    path for root in roots if root.exists() for path in root.rglob("*")
    if path.is_file() and path.suffix.lower() in (".xml", ".html", ".txt", ".log")
)
details = []
for report in report_files:
    if report.suffix.lower() != ".xml":
        continue
    try:
        root = ET.parse(report).getroot()
    except (ET.ParseError, OSError):
        continue
    for test in root.iter():
        if test.tag.rsplit("}", 1)[-1] != "testcase":
            continue
        for failure in test:
            if failure.tag.rsplit("}", 1)[-1] not in ("failure", "error"):
                continue
            message = (failure.get("message") or "").strip()
            trace = " ".join("".join(failure.itertext()).split())
            label = ".".join(part for part in (test.get("classname"), test.get("name")) if part)
            details.append(f"XML failure {label}: {' | '.join(x for x in (message, trace) if x)}")

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

if not details:
    for report in report_files:
        if report.suffix.lower() != ".html":
            continue
        try:
            parser = VisibleText()
            parser.feed(report.read_text(errors="replace"))
        except OSError:
            continue
        text = " ".join(parser.parts)
        match = re.search(
            r"captureCalculatorScreenshot|AssertionError|NoMatchingViewException|"
            r"PerformException|FATAL EXCEPTION|FAILED|exception",
            text, re.IGNORECASE
        )
        if match:
            start = max(0, match.start() - 500)
            details.append(f"HTML report {report}: {text[start:start + 5000]}")
            break

if details:
    print("\n".join(details)[:2400])
else:
    print("No failure details were present in the Android test XML/HTML reports.")
print("Report files: " + (", ".join(str(path) for path in report_files[:12]) or "none found"))
PY
)
    logcat_summary=$(adb logcat -d -v brief 2>/dev/null | grep -nEi -B 4 -A 12 'CalculatorToolUiTest|TestRunner|AndroidJUnitRunner|AssertionError|NoMatchingViewException|PerformException|FATAL EXCEPTION|INSTRUMENTATION_RESULT|instrumentation.*fail' | tail -n 90 || true)
    if [[ -n "$logcat_summary" ]]; then
        if [[ ${#failure_summary} -gt 1600 ]]; then
            failure_summary="${failure_summary:0:1600} [report output truncated]"
        fi
        failure_summary+=$'\n--- Relevant emulator logcat ---\n'
        failure_summary+="${logcat_summary:0:1600}"
    fi
    if [[ ${#failure_summary} -gt 3500 ]]; then
        failure_summary="${failure_summary:0:3500} [diagnostics truncated]"
    fi
    failure_summary=$(printf '%s\n' "$failure_summary" | sed -e 's/%/%25/g' -e 's/\r/%0D/g' -e ':a' -e 'N' -e '$!ba' -e 's/\n/%0A/g')
    echo "::error title=Instrumented test failure details::$failure_summary"
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
    done < <(base64 < "$preview" | tr -d '\n' | fold -w 3000)
}

# Keep original PNGs as artifacts; small JPEG previews are carried in check annotations for review.
emit_review_preview build/ui-screenshots/calculator-default.png "VM calculator screenshot"
emit_review_preview build/ui-screenshots/calculator-units.png "VM unit-converter screenshot"

exit "$test_result"
