#!/usr/bin/env bash
# Only the fraction window diagnostic. Collect while the emulator is still alive.
set -uo pipefail
mkdir -p build/fraction-evidence
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class="${FRACTION_TESTS:-com.android.calculator2.WindowConfigurationTest#fractionConstrainedHeightMeasurements,com.android.calculator2.ExactFractionUiTest}" --stacktrace --console=plain
result=$?
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
for p in Path('build/outputs/androidTest-results').rglob('TEST-*.xml'):
    root=ET.parse(p).getroot()
    for c in root.iter('testcase'):
        errors=c.findall('failure')+c.findall('error')
        message=c.get('name','?')+('='+'FAIL' if errors else '=PASS')
        if errors:message+=' '+(errors[0].text or '')[:5000]
        print('::notice title=Fraction test result::'+message.replace('%','%25').replace('\n','%0A').replace('\r','%0D'))
PY
timeout 20s adb pull /sdcard/Android/data/com.android.calculator2.dev/files/fraction-evidence/ build/fraction-evidence/ || true
for image in $(timeout 10s adb shell 'ls /sdcard/window-fraction-*.png /sdcard/window-split-entry-failed.png /sdcard/window-bmi-*.png' 2>/dev/null | tr -d '\r'); do timeout 15s adb pull "$image" build/fraction-evidence/ || true; done
timeout 15s adb logcat -d -v brief FractionWindow:I SplitPhase:I WindowEvidence:I '*:S' > build/fraction-evidence/measurements.log || true
python3 - <<'PYMEASURE'
from pathlib import Path
text=Path('build/fraction-evidence/measurements.log').read_text(errors='replace')
# GitHub caps notices per step: one grouped message, not one notice per ancestor.
lines=[line for line in text.splitlines() if 'FRACTION' in line]
if not lines:lines=text.splitlines()[-20:]
print('::notice title=Measured fraction geometry::'+'\n'.join(lines).replace('%','%25').replace('\n','%0A').replace('\r','%0D'))
PYMEASURE
exit "$result"
