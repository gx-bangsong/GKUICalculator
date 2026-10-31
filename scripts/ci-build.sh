#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 The GKUICalculator Project
# SPDX-License-Identifier: Apache-2.0

set -Eeuo pipefail

readonly APP_NAME="ExactCalculator"
readonly EXPECTED_APPLICATION_ID="com.android.calculator2"
readonly SIGNER_FILE="release-signer.sha256"

fail() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

normalize_fingerprint() {
    tr -d '[:space:]:' | tr '[:upper:]' '[:lower:]'
}

for variable in KEYSTORE_BASE64 KEYSTORE_PASS KEY_ALIAS KEY_PASS; do
    [[ -n "${!variable:-}" ]] || fail "Required secret is empty: $variable"
done

for command in aapt apksigner base64 find gh git jq sha256sum; do
    require_command "$command"
done

[[ -x ./gradlew ]] || fail "Run this script from the repository root"

repository_slug="${GITHUB_REPOSITORY:-}"
if [[ -z "$repository_slug" ]]; then
    remote_url="$(git config --get remote.origin.url || true)"
    remote_url="${remote_url%.git}"
    repository_slug="$(
        printf '%s' "$remote_url" |
            sed -E 's#^https://github\.com/##; s#^ssh://git@github\.com/##; s#^git@github\.com:##'
    )"
fi
[[ "$repository_slug" =~ ^[^/]+/[^/]+$ ]] ||
    fail "Cannot determine GitHub owner/repository for the source link"

commit_hash="$(git rev-parse HEAD)" || fail "Cannot determine the source commit"
server_url="${GITHUB_SERVER_URL:-https://github.com}"
source_url="${server_url}/${repository_slug}/tree/${commit_hash}"

temporary_dir="$(mktemp -d)"
cleanup() {
    rm -rf "$temporary_dir"
}
trap cleanup EXIT

keystore_path="${temporary_dir}/release.keystore"
if ! printf '%s' "$KEYSTORE_BASE64" | base64 --decode >"$keystore_path"; then
    fail "KEYSTORE_BASE64 is not valid base64"
fi
[[ -s "$keystore_path" ]] || fail "Decoded keystore is empty"
chmod 600 "$keystore_path"
export KEYSTORE_FILE="$keystore_path"
export CI=true

./gradlew --no-daemon assembleRelease

mapfile -t release_apks < <(
    find build/outputs/apk/release -maxdepth 1 -type f -name '*-release.apk' -print 2>/dev/null |
        sort
)
[[ ${#release_apks[@]} -eq 1 ]] ||
    fail "Expected exactly one signed *-release.apk, found ${#release_apks[@]}"
source_apk="${release_apks[0]}"

badging="$(aapt dump badging "$source_apk")" || fail "aapt could not inspect the APK"
package_line="$(printf '%s\n' "$badging" | grep -m1 '^package:')"
application_id="$(printf '%s\n' "$package_line" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
version_code="$(printf '%s\n' "$package_line" | sed -n "s/.* versionCode='\([^']*\)'.*/\1/p")"
version_name="$(printf '%s\n' "$package_line" | sed -n "s/.* versionName='\([^']*\)'.*/\1/p")"

[[ "$application_id" == "$EXPECTED_APPLICATION_ID" ]] ||
    fail "Unexpected applicationId: $application_id (expected $EXPECTED_APPLICATION_ID)"
[[ ! "$application_id" =~ (^|\.)(dev|debug)($|\.) ]] ||
    fail "Development applicationId found in release APK: $application_id"
if printf '%s\n' "$badging" | grep -q '^application-debuggable'; then
    fail "Release APK is debuggable"
fi
[[ "$version_code" =~ ^[1-9][0-9]*$ ]] || fail "Invalid versionCode in APK: $version_code"
[[ -n "$version_name" ]] || fail "APK has no versionName"
[[ "$version_name" =~ ^[0-9A-Za-z][0-9A-Za-z._-]*$ ]] ||
    fail "versionName is unsafe for an artifact filename: $version_name"

if ! signer_output="$(apksigner verify --verbose --print-certs "$source_apk" 2>&1)"; then
    printf '%s\n' "$signer_output" >&2
    fail "APK signature verification failed"
fi
signer_sha256="$(
    printf '%s\n' "$signer_output" |
        sed -n 's/^Signer #1 certificate SHA-256 digest: //p' |
        head -n 1 |
        normalize_fingerprint
)"
[[ "$signer_sha256" =~ ^[0-9a-f]{64}$ ]] ||
    fail "Could not read the signing certificate SHA-256 fingerprint"

if [[ ! -f "$SIGNER_FILE" ]]; then
    printf 'Detected signer SHA-256: %s\n' "$signer_sha256" >&2
    fail "$SIGNER_FILE is missing; verify the fingerprint, add it, and rerun"
fi
expected_signer="$(
    grep -Ev '^[[:space:]]*(#|$)' "$SIGNER_FILE" |
        head -n 1 |
        normalize_fingerprint || true
)"
[[ "$expected_signer" =~ ^[0-9a-f]{64}$ ]] ||
    fail "$SIGNER_FILE must contain one SHA-256 fingerprint"
[[ "$signer_sha256" == "$expected_signer" ]] ||
    fail "Signing certificate mismatch (actual $signer_sha256, expected $expected_signer)"

previous_release_json=""
previous_release_error="${temporary_dir}/previous-release-error.txt"
if ! previous_release_json="$(
    gh release view --repo "$repository_slug" --json tagName,assets 2>"$previous_release_error"
)"; then
    if grep -Eqi 'no releases found|release not found|HTTP 404|Not Found' \
        "$previous_release_error"; then
        printf 'No previous GitHub Release; skipping the versionCode comparison.\n'
    else
        cat "$previous_release_error" >&2
        fail "Could not query the previous GitHub Release"
    fi
fi

if [[ -n "$previous_release_json" ]] &&
    [[ "$(printf '%s' "$previous_release_json" | jq -r \
        '[.assets[].name] | index("build-info.json") != null')" == "true" ]]; then
    previous_tag="$(printf '%s' "$previous_release_json" | jq -r '.tagName')"
    previous_dir="${temporary_dir}/previous"
    mkdir -p "$previous_dir"
    gh release download "$previous_tag" --repo "$repository_slug" \
        --pattern build-info.json --dir "$previous_dir" --clobber
    previous_version_code="$(jq -er '.versionCode | numbers' \
        "${previous_dir}/build-info.json")" ||
        fail "Previous build-info.json has no numeric versionCode"
    [[ "$previous_version_code" =~ ^[0-9]+$ ]] ||
        fail "Previous versionCode is invalid: $previous_version_code"
    (( version_code > previous_version_code )) ||
        fail "versionCode $version_code must exceed previous release $previous_version_code"
elif [[ -n "$previous_release_json" ]]; then
    printf 'Previous release has no build-info.json; skipping first-run version comparison.\n'
fi

rm -rf dist
mkdir -p dist
artifact_name="${APP_NAME}-${version_name}.apk"
artifact_path="dist/${artifact_name}"
cp "$source_apk" "$artifact_path"
apk_sha256="$(sha256sum "$artifact_path" | awk '{print $1}')"
printf '%s  %s\n' "$apk_sha256" "$artifact_name" >"${artifact_path}.sha256"

jq -n \
    --arg applicationId "$application_id" \
    --argjson versionCode "$version_code" \
    --arg versionName "$version_name" \
    --arg signer_sha256 "$signer_sha256" \
    --arg apk_sha256 "$apk_sha256" \
    --arg commit "$commit_hash" \
    --arg source "$source_url" \
    '{
        applicationId: $applicationId,
        application_id: $applicationId,
        versionCode: $versionCode,
        version_code: $versionCode,
        versionName: $versionName,
        version_name: $versionName,
        signer_sha256: $signer_sha256,
        apk_sha256: $apk_sha256,
        commit: $commit,
        source_url: $source
    }' >dist/build-info.json

tag="v${version_name}"
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    {
        printf 'tag=%s\n' "$tag"
        printf 'application_id=%s\n' "$application_id"
        printf 'version_code=%s\n' "$version_code"
        printf 'version_name=%s\n' "$version_name"
        printf 'signer_sha256=%s\n' "$signer_sha256"
        printf 'apk_sha256=%s\n' "$apk_sha256"
        printf 'commit=%s\n' "$commit_hash"
        printf 'source_url=%s\n' "$source_url"
    } >>"$GITHUB_OUTPUT"
fi

printf 'Release artifact: %s\n' "$artifact_path"
printf 'Release tag: %s\n' "$tag"
printf 'Signer SHA-256: %s\n' "$signer_sha256"
