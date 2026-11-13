#!/usr/bin/env bash
set -euo pipefail

mkdir -p build/ui-screenshots
rm -f build/ui-screenshots/*.png
log_file=build/connected-debug-android-test.log
screenshot_dir=/sdcard

# Avoid Play Protect timing out while the fresh CI emulator installs the debug APK.
adb shell settings put global verifier_verify_adb_installs 0 || true
adb shell settings put global package_verifier_enable 0 || true
adb shell rm -f "$screenshot_dir/calculator-default.png" "$screenshot_dir/calculator-units.png" || true
# Keep the Pixel 2 logical size while reducing software-rendered pixels on the hosted VM.
adb shell wm size 720x1280 || true
adb shell wm density 280 || true

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

# Separate package installation from instrumentation so System UI has a chance to recover from
# any ANR dialog caused by the install broadcasts before Espresso/ActivityScenario start.
install_log=build/install-debug-android-test.log
if ./gradlew installDebug installDebugAndroidTest --stacktrace --console=plain >"$install_log" 2>&1; then
    cat "$install_log"
else
    install_result=$?
    cat "$install_log"
    install_diagnostics=$(tail -n 40 "$install_log" | sed -e 's/%/%25/g' -e 's/\r/%0D/g' -e ':a' -e 'N' -e '$!ba' -e 's/\n/%0A/g')
    echo "::error title=Install UI test APKs failed::$install_diagnostics"
    exit "$install_result"
fi

# Dismiss a prompt that appeared during the install phase before starting instrumentation.
for attempt in 1 2 3 4 5; do
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

if ((test_result == 0)); then
    test_summary=$(grep -E 'Tests [0-9]+/[0-9]+ completed|BUILD SUCCESSFUL' "$log_file" | tail -n 4 || true)
    if [[ -n "$test_summary" ]]; then
        test_summary=$(printf '%s\n' "$test_summary" | sed -e 's/%/%25/g' -e 's/\r/%0D/g' -e ':a' -e 'N' -e '$!ba' -e 's/\n/%0A/g')
        echo "::notice title=Android UI test result::$test_summary"
    else
        echo "::warning title=Android UI test result::Gradle reported success without a visible test-count summary."
    fi
    recovery_summary=$(adb logcat -d -v brief 2>/dev/null | grep -F 'CalculatorToolUiTest' | tail -n 12 || true)
    if [[ -n "$recovery_summary" ]]; then
        recovery_summary=${recovery_summary:0:1400}
        recovery_summary=$(printf '%s\n' "$recovery_summary" | sed -e 's/%/%25/g' -e 's/\r/%0D/g' -e ':a' -e 'N' -e '$!ba' -e 's/\n/%0A/g')
        echo "::notice title=Emulator focus recovery::$recovery_summary"
    fi
fi

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
    instrumentation_summary=$(
        find build/outputs/androidTest-results/connected/debug -type f \( -name 'utp.*.log' -o -name 'test-results.log' \) -print0 2>/dev/null |
            xargs -0 -r grep -nEi 'crash|exception|failure|failed|fatal|error|unresponsive|abort|instrumentation' 2>/dev/null |
            tail -n 50 || true
    )
    if [[ -n "$instrumentation_summary" ]]; then
        failure_summary+=$'\n--- Android test runner diagnostics ---\n'
        failure_summary+="${instrumentation_summary:0:1000}"
    fi
    logcat_summary=$(adb logcat -d -v brief 2>/dev/null | grep -nEi -B 4 -A 12 'CalculatorToolUiTest|TestRunner|AndroidJUnitRunner|AssertionError|NoMatchingViewException|PerformException|FATAL EXCEPTION|AndroidRuntime|Fatal signal|am_crash|am_anr|Process .*died|OutOfMemory|lowmemory|SIGSEGV|SIGABRT|INSTRUMENTATION_RESULT|instrumentation.*fail|ANR|not responding|Input dispatching timed out|com\.android\.systemui|system_server' | tail -n 90 || true)
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

adb pull "$screenshot_dir/calculator-default.png" build/ui-screenshots/ || true
adb pull "$screenshot_dir/calculator-units.png" build/ui-screenshots/ || true

default_screenshot_is_from_test=false
if [[ -s build/ui-screenshots/calculator-default.png ]]; then
    default_screenshot_is_from_test=true
    echo "::notice title=Calculator screenshot source::The screenshot test saved calculator-default.png."
else
    echo "::warning title=Calculator screenshot source::No test-generated default screenshot was saved; capturing the emulator display instead."
fi

# Capture the device display even if an Espresso assertion failed before its in-app PNG save.
if [[ ! -s build/ui-screenshots/calculator-default.png ]]; then
    adb exec-out screencap -p > build/ui-screenshots/calculator-default.png 2>/dev/null || true
    if [[ ! -s build/ui-screenshots/calculator-default.png ]]; then
        rm -f build/ui-screenshots/calculator-default.png
    fi
fi

if ((test_result == 0)) && [[ "$default_screenshot_is_from_test" != true ]]; then
    test_result=1
    echo "::error title=Missing test-generated screenshot::The selected screenshot test passed without saving calculator-default.png."
fi

emit_review_preview() {
    local image="$1"
    local label="$2"
    local preview="${image%.png}-review.jpg"
    local part=1
    if [[ ! -f "$image" ]]; then
        return
    fi
    if command -v sips >/dev/null; then
        sips -s format jpeg -s formatOptions 60 -Z 720 "$image" --out "$preview" >/dev/null 2>&1 || return
    elif command -v magick >/dev/null; then
        magick "$image" -resize 720x -quality 60 "$preview" >/dev/null 2>&1 || return
    elif command -v convert >/dev/null; then
        convert "$image" -resize 720x -quality 60 "$preview" >/dev/null 2>&1 || return
    elif python3 -c 'from PIL import Image' >/dev/null 2>&1; then
        python3 - "$image" "$preview" <<'PY'
from PIL import Image
import sys
with Image.open(sys.argv[1]) as image:
    image.thumbnail((720, 720))
    image.convert("RGB").save(sys.argv[2], "JPEG", quality=60)
PY
    else
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
