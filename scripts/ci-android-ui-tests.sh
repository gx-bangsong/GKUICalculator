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
# Modern 19:9 aspect ratio (typical of current Android phones) at a size that keeps
# software rendering on the hosted VM fast enough for the UI test suite.
adb shell wm size 720x1520 || true
adb shell wm density 280 || true

# Wait for the fresh AVD to finish booting before touching it: installing APKs into a half-booted
# system server is a reliable way to provoke the ANR dialogs this script then has to fight, and
# the instrumented test used to race the boot on slow hosted VMs.
adb wait-for-device || true
booted=""
bootanim=""
for _ in $(seq 1 90); do
    booted=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    bootanim=$(adb shell getprop init.svc.bootanim 2>/dev/null | tr -d '\r')
    if [[ "$booted" == "1" && "$bootanim" != "running" ]]; then
        break
    fi
    sleep 2
done
echo "::notice title=Android boot state::sys.boot_completed=${booted:-unknown}, bootanim=${bootanim:-unknown}"

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
for attempt in 1 2 3; do
    adb shell input tap "$wait_x" "$wait_y" || true
    sleep 2
done
adb shell wm dismiss-keyguard || true
adb shell input keyevent KEYCODE_WAKEUP || true

# Separate package installation from instrumentation so System UI has a chance to recover from
# any ANR dialog caused by the install broadcasts before Espresso/ActivityScenario start.
install_log=build/install-debug-android-test.log
install_result=0
# Hosted AVDs occasionally drop the adb connection or abort a package scan mid-sync, which Gradle
# reports as the useless "Failed to install on any devices". Retry before failing the job, and
# dump the device list so a dead emulator is distinguishable from a rejected APK.
phase_start=$SECONDS
for install_attempt in 1 2 3; do
    if ./gradlew installDebug installDebugAndroidTest --stacktrace --console=plain >"$install_log" 2>&1; then
        install_result=0
        cat "$install_log"
        break
    fi
    install_result=$?
    echo "::warning title=APK install attempt $install_attempt failed::$(adb devices -l 2>&1 | tr '\n' ' ' | sed -e 's/%/%25/g' -e 's/\r/%0D/g')"
    adb reconnect offline >/dev/null 2>&1 || true
    adb wait-for-device || true
    sleep 15
done
if ((install_result != 0)); then
    cat "$install_log"
    install_diagnostics=$(grep -nEi 'Caused by|INSTALL_FAILED|InstallException|Failed to install|error:|Execution failed|What went wrong|offline|no devices|SIGNATURE|VERIFICATION|storage' "$install_log" | tail -n 30 || true)
    if [[ -z "$install_diagnostics" ]]; then
        install_diagnostics=$(tail -n 40 "$install_log")
    fi
    install_diagnostics+=$'\n--- adb devices ---\n'
    install_diagnostics+=$(adb devices -l 2>&1)
    install_diagnostics=$(printf '%s\n' "$install_diagnostics" | sed -e 's/%/%25/g' -e 's/\r/%0D/g' -e ':a' -e 'N' -e '$!ba' -e 's/\n/%0A/g')
    echo "::error title=Install UI test APKs failed::$install_diagnostics"
    exit "$install_result"
fi

install_seconds=$((SECONDS-phase_start))
echo "::notice title=Phase duration::APK install phase (gradle installDebug installDebugAndroidTest) took ${install_seconds}s"

# Dismiss a prompt that appeared during the install phase before starting instrumentation.
for attempt in 1 2 3 4 5; do
    adb shell input tap "$wait_x" "$wait_y" || true
    sleep 2
done
adb shell wm dismiss-keyguard || true
adb shell input keyevent KEYCODE_WAKEUP || true

# Retain early field-edit events until the full regression finishes, for the separate
# BMI selection investigation. This changes logging only, not focus or selection behavior.
adb logcat -G 16M || true

# Run the whole instrumented class, not just the screenshot method: the geometry and mode-pill
# guards (pillShowsEveryToolWithoutScrolling, scientificPadDoesNotMoveDisplay, ...) only protect
# the UI if they actually execute on the emulator.
phase_start=$SECONDS
if ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.android.calculator2.WindowConfigurationTest,com.android.calculator2.tools.ToolRolesAndCurrencyTest,com.android.calculator2.CalculatorFormulaCursorTest,com.android.calculator2.CalculatorToolUiTest,com.android.calculator2.tools.ToolInputCursorTest,com.android.calculator2.tools.UnitInputCursorTest --stacktrace --console=plain --info >"$log_file" 2>&1; then
    test_result=0
else
    test_result=$?
fi
cat "$log_file"
if ((test_result != 0)); then
    adb logcat -d -v threadtime FieldToolMode:D '*:S' > build/field-cursor-diagnostic.log || true
    # Report the actual selection received with the first digit replacing BMI's default.
    # Keep these separate from the display-row failure/validation annotations.
    bmi_trace=$(grep "onDigit:.*value='175'" build/field-cursor-diagnostic.log | head -n 12 || true)
    if [[ -n "$bmi_trace" ]]; then
        bmi_trace=${bmi_trace//%/%25}
        bmi_trace=${bmi_trace//$'\r'/%0D}
        bmi_trace=${bmi_trace//$'\n'/%0A}
        echo "::notice title=BMI default replacement trace::$bmi_trace"
    fi
fi
connected_seconds=$((SECONDS-phase_start))
echo "::notice title=Phase duration::connectedDebugAndroidTest phase (build + install + 51 tests) took ${connected_seconds}s"

# Per-test wall times from the instrumentation XML, so slow tests are visible in the check
# annotations instead of hidden inside one monolithic step log.
per_test=$(python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

rows = []
for xml in Path("build/outputs/androidTest-results").rglob("TEST-*.xml"):
    try:
        root = ET.parse(xml).getroot()
    except (ET.ParseError, OSError):
        continue
    for case in root.iter("testcase"):
        try:
            rows.append((float(case.get("time", "0")), case.get("name", "?")))
        except ValueError:
            continue
rows.sort(reverse=True)
text = ", ".join(f"{name}={seconds:.1f}s" for seconds, name in rows)
print(text[:1800].replace("%", "%25").replace("\n", " "))
PY
)
if [[ -n "$per_test" ]]; then
    echo "::notice title=Per-test durations::$per_test"
fi

# Parse TEST-*.xml files and report each test's classname, name, and status.
# This verifies that both required test classes and their expected methods
# actually executed — not just that Gradle reported success.
test_details=$(python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

results_dir = Path("build/outputs/androidTest-results")
if not results_dir.exists():
    print("WARNING: No androidTest-results directory found.")
    raise SystemExit(0)

rows = []
for xml in sorted(results_dir.rglob("TEST-*.xml")):
    try:
        root = ET.parse(xml).getroot()
    except (ET.ParseError, OSError) as e:
        print(f"WARNING: Could not parse {xml}: {e}")
        continue
    for case in root.iter("testcase"):
        classname = case.get("classname", "?")
        name = case.get("name", "?")
        # Determine status from child elements
        failure = case.find("failure")
        error = case.find("error")
        skipped = case.find("skipped")
        if failure is not None:
            status = "FAIL"
            msg = failure.get("message", "")[:120]
        elif error is not None:
            status = "ERROR"
            msg = error.get("message", "")[:120]
        elif skipped is not None:
            status = "SKIP"
            msg = skipped.get("message", "")[:120]
        else:
            status = "PASS"
            msg = ""
        rows.append((classname, name, status, msg))

if not rows:
    print("WARNING: No testcases found in any TEST-*.xml files.")
else:
    for classname, name, status, msg in rows:
        suffix = f" | {msg}" if msg else ""
        print(f"TEST {classname}#{name} => {status}{suffix}")
PY
)
if [[ -n "$test_details" ]]; then
    # An unescaped multi-line annotation keeps only its first line, so every test but the first
    # was invisible outside the runner. '%' must be escaped before the newline escape, otherwise
    # the '%0A' just inserted would itself be escaped.
    escaped_details="${test_details//%/%25}"
    escaped_details="${escaped_details//$'\n'/%0A}"
    echo "::notice title=Per-test execution details::$escaped_details"
fi

# Window functional results stay separate from screenshot/evidence status.
python3 - <<'PYWINDOW'
from pathlib import Path
import xml.etree.ElementTree as ET
rows=[]
for p in Path("build/outputs/androidTest-results").rglob("TEST-*.xml"):
    root=ET.parse(p).getroot()
    for case in root.iter("testcase"):
        if case.get("classname", "").endswith("WindowConfigurationTest"):
            status="FAIL" if case.find("failure") is not None or case.find("error") is not None else "SKIP" if case.find("skipped") is not None else "PASS"
            rows.append(case.get("name", "?")+"="+status)
text="; ".join(rows).replace("%","%25")
print("::warning title=Window functional results::"+text)
PYWINDOW
adb logcat -d -v brief WindowEvidence:I '*:S' > build/ui-screenshots/window-evidence.log || true
window_evidence=$(grep -E 'CAPTURE_(OK|FAIL)|configDp=' build/ui-screenshots/window-evidence.log | tail -n 30 | tr '\n' ' ' || true)
window_evidence=${window_evidence//%/%25}
echo "::warning title=Window evidence status::${window_evidence:0:3800}"


# ---- Required-method validation -------------------------------------------
# Definitive list of required test methods based on the current source code:
#   15 for CalculatorToolUiTest + 6 BMI cursor + 5 unit cursor/read-only + 7 formula + 7 tool roles/currency = 51 total.
# Any missing, absent, or skipped required method triggers a validation failure.
validation_failed=0
summary_lines=()
annotation_lines=()

set +e
validation_output=$(python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

results_dir = Path("build/outputs/androidTest-results")
if not results_dir.exists():
    print("ERROR: No androidTest-results directory found.")
    raise SystemExit(1)

# Required methods: 15 CalculatorToolUiTest + 6 ToolInputCursorTest + 5 UnitInputCursorTest + 7 CalculatorFormulaCursorTest + 7 ToolRolesAndCurrencyTest = 51
required = {
    "com.android.calculator2.WindowConfigurationTest": [
        "bmiSurvivesActualRotation", "dateSurvivesActualRotation",
        "relationshipSurvivesActualRotation", "programmerSurvivesActualRotation",
        "taxSurvivesActualRotation", "mortgageSurvivesActualRotation",
        "unitSurvivesActualRotation", "currencySurvivesActualRotation",
        "landscapeScientificKeyBounds",
        "landscapeHistoryExpandCollapseKeepsKeyboardRegion",
        "historyHandleAnchorsToDisplayAcrossPanelModes",
        "historyHandleRespondsToActualDragAcrossPanelModes",
    ],
    "com.android.calculator2.tools.ToolRolesAndCurrencyTest": [
        "sharedOutputsAreReadOnlyAcrossTools",
        "relationshipAndCalculatorRestoreInputRoles",
        "programmerPanelHasOnlyTwoValuesAndStatus",
        "programmerLongBinaryOutputIsCompleteAndCopyable",
        "currencyTouchInsertionUpdatesBothControlledResults",
        "currencyArithmeticSymbolsRespectCaret",
        "currencyDeleteRangeDecimalClearAndSwap",
        "currencyRateCallbackPreservesSelection",
    ],
    "com.android.calculator2.CalculatorFormulaCursorTest": [
        "realTapMiddleInsertionEvaluatesModel",
        "consecutiveInsertionKeepsCaretAcrossGrouping",
        "middleBackspaceEditsModel",
        "selectedRangeIsReplacedInModel",
        "ordinaryResultIsReadOnlyAndCopyable",
        "formulaEditingSurvivesUnitRoundTrip",
        "mappingKeepsFunctionTokensAtomic",
        "middleArithmeticSymbolsRespectCaret",
        "semanticSymbolBoundariesAndRejection",
        "scientificFunctionTouchInsertionEvaluates",
        "scientificSuffixTouchInsertionEvaluates",
        "scientificModelFunctionsAndConstantsEvaluate",
        "modelParenthesesAndLocalOperatorContext",
        "invalidSymbolTouchPreservesSelection",
        "smartParenthesesTouchInsertionEvaluates",
        "selectedOperatorReplacementEvaluates",
    ],
    "com.android.calculator2.CalculatorToolUiTest": [
        "firstRunUsesStandardKeypad",
        "scientificSegmentTogglesPad",
        "scientificPadDoesNotMoveDisplay",
        "convertSegmentOpensConverter",
        "everyToolHasAModeSegment",
        "pillShowsEveryToolWithoutScrolling",
        "firstKinshipPressDoesNotStallThePad",
        "percentModeOptionSwitchesAdditiveSemantics",
        "unitCategoriesRemainInlinePills",
        "captureCalculatorScreenshot",
        "captureUnitConverterScreenshot",
        "pendingCalculatorResultDoesNotReplaceMortgageResult",
        "uppercaseNumbersIncludePrimaryMortgagePayment",
        "unitConverterAcceptsArithmetic",
        "currencyConverterUsesValueColumnForUppercaseAndAcceptsArithmetic",
    ],
    "com.android.calculator2.tools.UnitInputCursorTest": [
        "unitMiddleInsertionUpdatesConversion",
        "unitArithmeticSymbolsRespectCaret",
        "unitMiddleDeleteAndRangeReplacement",
        "unitResultsAreReadOnlyAndSelectable",
        "unitSharedDisplayRowsAreHidden",
        "unitSharedRowsRestoreAcrossModes",
    ],
    "com.android.calculator2.tools.ToolInputCursorTest": [
        "testMiddleInsertionAfterFocusLoss",
        "testDeleteAtPosition0DoesNothing",
        "testConsecutiveCursorRepositioning",
        "testSwitchingBetweenFields",
        "testEnterAfterClear",
        "testCursorAtEndAppendsDigit",
    ],
}

# Collect all executed testcases from TEST-*.xml files
executed = {}
for xml in sorted(results_dir.rglob("TEST-*.xml")):
    try:
        root = ET.parse(xml).getroot()
    except (ET.ParseError, OSError) as e:
        print(f"WARNING: Could not parse {xml}: {e}")
        continue
    for case in root.iter("testcase"):
        classname = case.get("classname", "?")
        name = case.get("name", "?")
        failure = case.find("failure")
        error = case.find("error")
        skipped = case.find("skipped")
        if failure is not None:
            status = "FAIL"
        elif error is not None:
            status = "ERROR"
        elif skipped is not None:
            status = "SKIP"
        else:
            status = "PASS"
        executed[(classname, name)] = status

missing = []
failed = []
skipped = []

for classname, names in required.items():
    for name in names:
        key = (classname, name)
        if key not in executed:
            missing.append(f"{classname}#{name}")
        elif executed[key] == "FAIL":
            failed.append(f"{classname}#{name}")
        elif executed[key] == "SKIP":
            skipped.append(f"{classname}#{name}")

if missing:
    print("MISSING required tests (not executed):")
    for m in missing:
        print(f"  - {m}")
if failed:
    print("FAILED required tests:")
    for f in failed:
        print(f"  - {f}")
if skipped:
    print("SKIPPED required tests:")
    for s in skipped:
        print(f"  - {s}")

total_required = sum(len(v) for v in required.values())
total_executed = len(executed)
total_passed = sum(1 for s in executed.values() if s == "PASS")
print(f"Required: {total_required}, Executed: {total_executed}, Passed: {total_passed}")

if missing or failed or skipped:
    raise SystemExit(1)
PY
)
validation_failed=$?
set -e

echo "$validation_output"

if ((validation_failed != 0)); then
    echo "::error title=Required test validation failed::Some required test methods were missing, failed, or skipped."
fi

# Write results table to GITHUB_STEP_SUMMARY
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    {
        echo "# Android UI Test Results"
        echo ""
        echo "| Class | Method | Status |"
        echo "|-------|--------|--------|"
        set +e
        summary_output=$(python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

results_dir = Path("build/outputs/androidTest-results")
if not results_dir.exists():
    print("| N/A | N/A | No results |")
    raise SystemExit(0)

rows = []
for xml in sorted(results_dir.rglob("TEST-*.xml")):
    try:
        root = ET.parse(xml).getroot()
    except (ET.ParseError, OSError):
        continue
    for case in root.iter("testcase"):
        classname = case.get("classname", "?")
        name = case.get("name", "?")
        failure = case.find("failure")
        error = case.find("error")
        skipped = case.find("skipped")
        if failure is not None:
            status = "FAIL"
        elif error is not None:
            status = "ERROR"
        elif skipped is not None:
            status = "SKIP"
        else:
            status = "PASS"
        rows.append((classname, name, status))

for classname, name, status in sorted(rows):
        short = classname.rsplit(".", 1)[-1]
        print(f"| {short} | {name} | {status} |")
PY
)
        summary_exit=$?
        set -e
        echo "$summary_output"
        if ((summary_exit != 0)); then
            echo "| N/A | N/A | Summary generation failed |"
        fi
    } >> "${GITHUB_STEP_SUMMARY}"
fi

# Output escaped annotations for each failed/missing item (always)
set +e
annotation_output=$(python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

results_dir = Path("build/outputs/androidTest-results")
required = {
    "com.android.calculator2.WindowConfigurationTest": [
        "bmiSurvivesActualRotation", "dateSurvivesActualRotation",
        "relationshipSurvivesActualRotation", "programmerSurvivesActualRotation",
        "taxSurvivesActualRotation", "mortgageSurvivesActualRotation",
        "unitSurvivesActualRotation", "currencySurvivesActualRotation",
        "landscapeScientificKeyBounds",
        "landscapeHistoryExpandCollapseKeepsKeyboardRegion",
        "historyHandleAnchorsToDisplayAcrossPanelModes",
        "historyHandleRespondsToActualDragAcrossPanelModes",
    ],
    "com.android.calculator2.tools.ToolRolesAndCurrencyTest": [
        "sharedOutputsAreReadOnlyAcrossTools",
        "relationshipAndCalculatorRestoreInputRoles",
        "programmerPanelHasOnlyTwoValuesAndStatus",
        "programmerLongBinaryOutputIsCompleteAndCopyable",
        "currencyTouchInsertionUpdatesBothControlledResults",
        "currencyArithmeticSymbolsRespectCaret",
        "currencyDeleteRangeDecimalClearAndSwap",
        "currencyRateCallbackPreservesSelection",
    ],
    "com.android.calculator2.CalculatorFormulaCursorTest": [
        "realTapMiddleInsertionEvaluatesModel",
        "consecutiveInsertionKeepsCaretAcrossGrouping",
        "middleBackspaceEditsModel",
        "selectedRangeIsReplacedInModel",
        "ordinaryResultIsReadOnlyAndCopyable",
        "formulaEditingSurvivesUnitRoundTrip",
        "mappingKeepsFunctionTokensAtomic",
        "middleArithmeticSymbolsRespectCaret",
        "semanticSymbolBoundariesAndRejection",
        "scientificFunctionTouchInsertionEvaluates",
        "scientificSuffixTouchInsertionEvaluates",
        "scientificModelFunctionsAndConstantsEvaluate",
        "modelParenthesesAndLocalOperatorContext",
        "invalidSymbolTouchPreservesSelection",
        "smartParenthesesTouchInsertionEvaluates",
        "selectedOperatorReplacementEvaluates",
    ],
    "com.android.calculator2.CalculatorToolUiTest": [
        "firstRunUsesStandardKeypad",
        "scientificSegmentTogglesPad",
        "scientificPadDoesNotMoveDisplay",
        "convertSegmentOpensConverter",
        "everyToolHasAModeSegment",
        "pillShowsEveryToolWithoutScrolling",
        "firstKinshipPressDoesNotStallThePad",
        "percentModeOptionSwitchesAdditiveSemantics",
        "unitCategoriesRemainInlinePills",
        "captureCalculatorScreenshot",
        "captureUnitConverterScreenshot",
        "pendingCalculatorResultDoesNotReplaceMortgageResult",
        "uppercaseNumbersIncludePrimaryMortgagePayment",
        "unitConverterAcceptsArithmetic",
        "currencyConverterUsesValueColumnForUppercaseAndAcceptsArithmetic",
    ],
    "com.android.calculator2.tools.UnitInputCursorTest": [
        "unitMiddleInsertionUpdatesConversion",
        "unitArithmeticSymbolsRespectCaret",
        "unitMiddleDeleteAndRangeReplacement",
        "unitResultsAreReadOnlyAndSelectable",
        "unitSharedDisplayRowsAreHidden",
        "unitSharedRowsRestoreAcrossModes",
    ],
    "com.android.calculator2.tools.ToolInputCursorTest": [
        "testMiddleInsertionAfterFocusLoss",
        "testDeleteAtPosition0DoesNothing",
        "testConsecutiveCursorRepositioning",
        "testSwitchingBetweenFields",
        "testEnterAfterClear",
        "testCursorAtEndAppendsDigit",
    ],
}

executed = {}
for xml in sorted(results_dir.rglob("TEST-*.xml")):
    try:
        root = ET.parse(xml).getroot()
    except (ET.ParseError, OSError):
        continue
    for case in root.iter("testcase"):
        classname = case.get("classname", "?")
        name = case.get("name", "?")
        failure = case.find("failure")
        error = case.find("error")
        skipped = case.find("skipped")
        if failure is not None:
            status = "FAIL"
            msg = (failure.get("message") or "")[:200]
        elif error is not None:
            status = "ERROR"
            msg = (error.get("message") or "")[:200]
        elif skipped is not None:
            status = "SKIP"
            msg = (skipped.get("message") or "")[:200]
        else:
            status = "PASS"
            msg = ""
        executed[(classname, name)] = (status, msg)

for classname, names in required.items():
    for name in names:
        key = (classname, name)
        if key not in executed:
            short = classname.rsplit(".", 1)[-1]
            print(f"::error title=Missing test::{short}%23{name}%20not%20executed")
        else:
            status, msg = executed[key]
            if status != "PASS":
                short = classname.rsplit(".", 1)[-1]
                safe_msg = msg.replace(" ", "%20").replace("\n", "%0A").replace("|", "%7C")
                print(f"::error title={status} test::{short}%23{name}%20{safe_msg}")
PY
)
annotation_exit=$?
set -e

echo "$annotation_output"
if ((annotation_exit != 0)); then
    echo "::error title=Annotation generation failed::Python annotation script exited with code ${annotation_exit}"
fi
# ---- End required-method validation ---------------------------------------

if ((test_result == 0)); then
    test_summary=$(grep -E 'Tests [0-9]+/[0-9]+ completed|BUILD SUCCESSFUL' "$log_file" | tail -n 4 || true)
    if [[ -n "$test_summary" ]]; then
        test_summary=$(printf '%s\n' "$test_summary" | sed -e 's/%/%25/g' -e 's/\r/%0D/g' -e ':a' -e 'N' -e '$!ba' -e 's/\n/%0A/g')
        echo "::notice title=Android UI test result::$test_summary"
    else
        echo "::warning title=Android UI test result::Gradle reported success without a visible test-count summary."
    fi
    recovery_summary=$(adb logcat -d -v brief 2>/dev/null | grep -F -e 'CalculatorToolUiTest' -e 'ToolInputCursorTest' | tail -n 12 || true)
    if [[ -n "$recovery_summary" ]]; then
        recovery_summary=${recovery_summary:0:1400}
        recovery_summary=$(printf '%s\n' "$recovery_summary" | sed -e 's/%/%25/g' -e 's/\r/%0D/g' -e ':a' -e 'N' -e '$!ba' -e 's/\n/%0A/g')
        echo "::notice title=Emulator focus recovery::$recovery_summary"
    fi
fi

if ((test_result != 0)); then
    app_crash=$(adb logcat -d -v brief AndroidRuntime:E '*:S' 2>/dev/null | head -n 65 || true)
    if [[ -n "$app_crash" ]]; then
        app_crash=${app_crash//%/%25}
        app_crash=${app_crash//$'\n'/%0A}
        echo "::warning title=Application runtime failure::$app_crash"
    fi
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
    print("\n".join(detail[:1800] for detail in details)[:12000])
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
        if [[ ${#failure_summary} -gt 12000 ]]; then
            failure_summary="${failure_summary:0:12000} [report output truncated]"
        fi
        failure_summary+=$'\n--- Relevant emulator logcat ---\n'
        failure_summary+="${logcat_summary:0:1100}"
    fi
    if [[ ${#failure_summary} -gt 15000 ]]; then
        failure_summary="${failure_summary:0:15000} [diagnostics truncated]"
    fi
    failure_summary=$(printf '%s\n' "$failure_summary" | sed -e 's/%/%25/g' -e 's/\r/%0D/g' -e ':a' -e 'N' -e '$!ba' -e 's/\n/%0A/g')
    echo "::warning title=Instrumented test failure details::$failure_summary"
fi

adb pull "$screenshot_dir/calculator-default.png" build/ui-screenshots/ || true
adb pull "$screenshot_dir/calculator-units.png" build/ui-screenshots/ || true
# Preserve actual-rotation evidence even when a mode-restoration assertion fails.
while IFS= read -r image; do
    image=${image//$'\r'/}
    [[ "$image" == /sdcard/window-*.png ]] || continue
    adb pull "$image" build/ui-screenshots/ || true
done < <(adb shell 'ls /sdcard/window-*.png' 2>/dev/null || true)


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

# Compact JPEG twins of the screenshots, for the dedicated preview-publishing steps in
# android-ui.yml. The base64 is NOT emitted here: GitHub caps workflow-command annotations per
# step, and emitting the image from this (annotation-heavy) step truncated it mid-stream — every
# review so far has been reading a half-decoded frame.
make_review_preview() {
    local image="$1"
    local preview="${image%.png}-review.jpg"
    rm -f "$preview"
    [[ -s "$image" ]] || return 0
    if command -v magick >/dev/null 2>&1; then
        magick "$image" -resize 360x -quality 38 "$preview" >/dev/null 2>&1 || true
    fi
    if [[ ! -s "$preview" ]] && command -v convert >/dev/null 2>&1; then
        convert "$image" -resize 360x -quality 38 "$preview" >/dev/null 2>&1 || true
    fi
    if [[ ! -s "$preview" ]] && command -v ffmpeg >/dev/null 2>&1; then
        ffmpeg -v error -y -i "$image" -vf scale=360:-2 -q:v 8 "$preview" >/dev/null 2>&1 || true
    fi
    if [[ ! -s "$preview" ]] && command -v sips >/dev/null 2>&1; then
        sips -s format jpeg -s formatOptions 38 -Z 360 "$image" --out "$preview" >/dev/null 2>&1 || true
    fi
    # Stay silent when no converter exists: the publish steps fall back to the raw PNG, and
    # every annotation here spends part of the check run's 50-annotation budget.
}

make_review_preview build/ui-screenshots/calculator-default.png
make_review_preview build/ui-screenshots/calculator-units.png

# Aggregate final exit code: fail if tests failed OR validation failed
final_result=$test_result
if ((validation_failed != 0)); then
    final_result=1
fi
exit "$final_result"
