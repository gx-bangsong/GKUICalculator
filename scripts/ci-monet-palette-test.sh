#!/usr/bin/env bash
# Disposable emulator only. Set two actual SystemUI Monet seeds, never app-local overrides.
set -euo pipefail
mkdir -p build/monet-evidence
adb install -r build/outputs/apk/debug/ExactCalculator-debug.apk
adb install -r build/outputs/apk/androidTest/debug/ExactCalculator-debug-androidTest.apk
api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
for seed in blue red; do
  if [ "$api" -lt 31 ] && [ "$seed" = red ]; then continue; fi
  case "$seed" in blue) hex=0000FF;; red) hex=FF0000;; esac
  if [ "$api" -ge 31 ]; then
    adb shell settings put secure theme_customization_overlay_packages "'{\"android.theme.customization.system_palette\":\"$hex\",\"android.theme.customization.accent_color\":\"$hex\",\"android.theme.customization.color_source\":\"preset\",\"android.theme.customization.theme_style\":\"TONAL_SPOT\"}'"
  fi
  for mode in light dark; do
    if [ "$mode" = dark ]; then night=yes; dark=true; else night=no; dark=false; fi
    adb shell cmd uimode night "$night"
    # Allow SystemUI to regenerate/install overlays before launching a fresh Activity.
    sleep 8
    label="$seed-$mode"
    adb shell settings get secure theme_customization_overlay_packages > "build/monet-evidence/$label-system-setting.txt"
    adb shell cmd overlay list > "build/monet-evidence/$label-overlays.txt"
    adb shell am instrument -w -r -e class com.android.calculator2.MonetPaletteTest \
      -e palette_label "$label" -e expected_dark "$dark" \
      com.android.calculator2.dev.test/androidx.test.runner.AndroidJUnitRunner \
      | tee "build/monet-evidence/$label-test.txt"
    # Surface real instrumentation failures even when hosted log downloads are unavailable.
    python3 - "$label" <<'PYLOG'
import sys
from pathlib import Path
label=sys.argv[1]
text=Path(f'build/monet-evidence/{label}-test.txt').read_text()[-14000:]
print('::notice title=Monet instrumentation '+label+'::'+text.replace('%','%25').replace('\n','%0A').replace('\r','%0D'))
PYLOG
    adb exec-out run-as com.android.calculator2.dev tar -C files/monet-evidence -cf - . | tar -xf - -C build/monet-evidence
    grep -q 'OK (1 test)' "build/monet-evidence/$label-test.txt"
  done
done
python3 - <<'PY'
import json
import sys
import traceback
from pathlib import Path

def failure(kind, error, tb):
    message=''.join(traceback.format_exception(kind,error,tb))
    print('::error title=Monet palette comparison failed::'+message.replace('%','%25').replace('\n','%0A').replace('\r','%0D'))
    sys.__excepthook__(kind,error,tb)
sys.excepthook=failure
p=Path('build/monet-evidence')
for mode in ('light','dark'):
    blue=json.loads((p/f'blue-{mode}.json').read_text())
    if blue['api']<31: continue
    red=json.loads((p/f'red-{mode}.json').read_text())
    for role in ('systemAccent','equals','chip'):
        assert blue[role]!=red[role], (mode,role,'two system palettes did not actually change',blue,red)
    print('::notice title=Real Monet comparison::'+mode+' distinct system/equals/chip colors verified')
PY
