#!/usr/bin/env bash
set -euo pipefail
mkdir -p build/ui-screenshots
adb shell wm dismiss-keyguard
status=0
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.android.calculator2.ExternalTextConversionProbeTest --console=plain > build/connected-debug-android-test.log 2>&1 || status=$?
adb exec-out run-as com.android.calculator2.dev cat files/external-text-probe.json > build/ui-screenshots/external-text-probe.json || true
adb pull /sdcard/external-probe-native.png build/ui-screenshots/external-probe-native.png || true
adb pull /sdcard/external-probe-webview.png build/ui-screenshots/external-probe-webview.png || true
python3 - <<'PY'
import json,pathlib,re
p=pathlib.Path('build/ui-screenshots/external-text-probe.json')
if not p.exists() or not p.stat().st_size:
    text=pathlib.Path('build/connected-debug-android-test.log').read_text(errors='replace')[-7000:]
else:
    d=json.loads(p.read_text())
    for key in ('native','webview'):
        if key in d and 'menuHierarchy' in d[key]:
            d[key]['visibleLabels']=re.findall(r'text="([^"]+)"',d[key].pop('menuHierarchy'))
    text=json.dumps(d,ensure_ascii=False,indent=2)[:14000]
text=text.replace('%','%25').replace('\r','%0D').replace('\n','%0A')
print('::notice title=External text conversion observation (not integration PASS)::'+text)
PY
exit "$status"
