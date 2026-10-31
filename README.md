<!--
SPDX-FileCopyrightText: 2026 The GKUICalculator Project
SPDX-License-Identifier: Apache-2.0
-->

# GKUICalculator

> [!CAUTION]
> **Switching from a `dev`/`debug` version to a `release` version requires an uninstallation and
> fresh installation, which will result in data loss.** The packages have different application
> IDs and Android cannot treat them as an in-place upgrade.

GKUICalculator is based on LineageOS ExactCalculator and adds localized conversion and everyday
calculation tools. The AOSP/LineageOS build continues to use `Android.bp`; a standalone Gradle
build is also available.

## Build and Release

### Package and signature behavior

The production Gradle variant has application ID `com.android.calculator2`. The development build
keeps the existing `com.android.calculator2.dev` ID and Android debug signing, so
`./gradlew assembleDebug` remains convenient and can coexist with a production installation.

Every production release must use the same long-lived certificate. Releases signed by that fixed
certificate can upgrade one another without losing app data. Matching the LineageOS built-in
package name does **not** guarantee an upgrade over a ROM copy: Android also requires the ROM APK
and downloaded APK to have the same signing certificate.

### 1. Generate and protect the production keystore

Run this once on a trusted offline/admin machine. Choose strong, unique passwords and retain
multiple encrypted backups; losing this key prevents future in-place upgrades.

```bash
keytool -genkeypair -v \
  -keystore gkuicalculator-release.jks \
  -storetype PKCS12 \
  -alias gkuicalculator \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Never commit the keystore. `*.jks` and `*.keystore` are ignored by Git.

### 2. Configure GitHub Actions secrets

In **Repository settings → Secrets and variables → Actions**, add exactly these four repository
secrets:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | The complete keystore encoded as one base64 line |
| `KEYSTORE_PASS` | Keystore password |
| `KEY_ALIAS` | Key alias (for example, `gkuicalculator`) |
| `KEY_PASS` | Private-key password |

Create the base64 value on GNU/Linux with:

```bash
base64 -w 0 gkuicalculator-release.jks
```

On macOS or other systems without `-w`, use:

```bash
base64 < gkuicalculator-release.jks | tr -d '\n'
```

Follow [`docs/WORKFLOW.md`](docs/WORKFLOW.md) and manually copy its workflow into
`.github/workflows/release.yml`. The repository owner selected `lineage-23.2`, the current default
branch, as the release trigger because this repository does not currently have a `main` branch.

### 3. Pin the expected signing certificate

`release-signer.sha256` is a public certificate fingerprint—not a private key. It prevents a
changed/replaced Actions keystore from silently publishing an APK under an unexpected identity.
The file is intentionally not generated automatically.

On the first workflow run, `scripts/ci-build.sh` builds and verifies the APK, prints:

```text
Detected signer SHA-256: <64 lowercase hexadecimal characters>
```

and then fails because `release-signer.sha256` is absent. Independently verify the fingerprint
against the protected keystore, then add the exact 64-character value:

```bash
keytool -list -v \
  -keystore gkuicalculator-release.jks \
  -alias gkuicalculator

printf '%s\n' '<verified-sha256-without-colons>' > release-signer.sha256
git add release-signer.sha256
git commit -m 'build: pin production signing certificate'
```

The next run succeeds only when `apksigner verify --print-certs` reports the same fingerprint.
Changing production keys therefore requires an explicit reviewed change to this file and will
break in-place upgrades from releases signed by the old key.

### 4. Versioning

The Gradle build derives `versionCode` from:

```bash
git rev-list --count HEAD
```

The release workflow checks out full history (`fetch-depth: 0`). On the non-rebased release branch,
the commit count increases monotonically without writing version files or commits back to the
repository. If Git is unavailable in a local checkout, the fallback is `1`; CI fails instead of
using that fallback.

By default, `versionName` is `<UTC date yyyyMMdd>.<versionCode>`, for example
`20261028.1234`. Set the `VERSION_NAME` environment variable to override it for a deliberate
manual build. The build never edits tracked source files, preventing release-trigger loops.

### 5. Local builds

Development is unchanged:

```bash
./gradlew assembleDebug
```

A local release build with no signing variables emits a warning and creates an **unsigned** APK;
it never falls back to the debug certificate:

```bash
./gradlew assembleRelease
```

To produce a locally signed release, export all four Gradle signing inputs:

```bash
export KEYSTORE_FILE="$PWD/gkuicalculator-release.jks"
export KEYSTORE_PASS='...'
export KEY_ALIAS='gkuicalculator'
export KEY_PASS='...'
./gradlew assembleRelease
```

`scripts/ci-build.sh` is stricter than direct local Gradle use: it requires the four GitHub-style
secret values, validates package/debuggable/signature/version metadata, compares the prior
release's `build-info.json`, and writes only ignored deliverables under `dist/`.

### Published files

A successful automated release contains:

- `ExactCalculator-<versionName>.apk`
- `ExactCalculator-<versionName>.apk.sha256`
- `build-info.json`

`build-info.json` records `applicationId`, `versionCode`, `versionName`, signer and APK SHA-256
hashes, the exact commit, and its source-code URL for GKUI-Pack and independent verification.
