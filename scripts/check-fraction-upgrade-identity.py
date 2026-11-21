#!/usr/bin/env python3
"""Compare the exact accepted binary, never rebuild/re-sign the acceptance baseline."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import traceback


def describe_failure(kind, error, tb):
    message = "".join(traceback.format_exception(kind, error, tb))
    report = {"cleanupSha": os.environ.get("GITHUB_SHA"), "identityCheckError": message,
              "acceptedApkCandidates": [str(p) for p in Path("accepted-fraction-apk").rglob("*.apk")]}
    Path("build").mkdir(exist_ok=True)
    Path("build/fraction-upgrade-identity.json").write_text(json.dumps(report, indent=2) + "\n")
    print("::error title=APK identity check could not execute::" + message[:6000].replace("%", "%25").replace("\n", "%0A").replace("\r", "%0D"))
    sys.__excepthook__(kind, error, tb)


sys.excepthook = describe_failure


def one_apk(directory):
    files = list(Path(directory).rglob("ExactCalculator-debug.apk"))
    if len(files) != 1:
        raise RuntimeError(f"Expected one main APK in {directory}, got {len(files)}")
    return files[0]


sdk = Path(os.environ["ANDROID_HOME"])
tools = sorted((sdk / "build-tools").iterdir(), key=lambda p: [int(x) if x.isdigit() else 0 for x in p.name.split(".")])
tool = next(p for p in reversed(tools) if (p / "apksigner").is_file() and (p / "aapt").is_file())


def identity(apk):
    certs = subprocess.check_output([str(tool / "apksigner"), "verify", "--print-certs", str(apk)], text=True)
    digests = re.findall(r"certificate SHA-256 digest: (\S+)", certs)
    if not digests:
        raise RuntimeError("No signing certificate digest: " + str(apk))
    badging = subprocess.check_output([str(tool / "aapt"), "dump", "badging", str(apk)], text=True)
    match = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", badging, re.M)
    if not match:
        raise RuntimeError("Cannot parse APK identity: " + str(apk))
    return {"package": match[1], "versionCode": int(match[2]), "versionName": match[3],
            "certificateSha256": sorted(digests), "apkSha256": hashlib.sha256(apk.read_bytes()).hexdigest()}


old = identity(one_apk("accepted-fraction-apk"))
new = identity(one_apk("build/outputs/apk/debug"))
checks = {"samePackage": old["package"] == new["package"],
          "versionNotDowngraded": new["versionCode"] >= old["versionCode"],
          "sameSigningCertificates": old["certificateSha256"] == new["certificateSha256"]}
report = {"acceptedSha": "c5755d727d7cccc9e80274262ee9d7a5c2427e85", "cleanupSha": os.environ.get("GITHUB_SHA"),
          "acceptedApk": old, "cleanupApk": new, "checks": checks,
          "note": "Identity gate only. A successful adb install -r and data assertions are still required."}
Path("build").mkdir(exist_ok=True)
Path("build/fraction-upgrade-identity.json").write_text(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))
print("::notice title=Exact APK identity::" + json.dumps({"checks": checks, "accepted": old, "cleanup": new}))
if not all(checks.values()):
    print("::error title=In-place upgrade blocked::Accepted APK identity does not match cleanup APK. Do NOT uninstall or clear data as a workaround.")
    raise SystemExit(1)
print("::notice title=Upgrade identity::Exact accepted APK identity matches; still requires install -r/data-preservation test.")
