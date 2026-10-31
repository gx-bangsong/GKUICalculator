<!--
SPDX-FileCopyrightText: 2026 The GKUICalculator Project
SPDX-License-Identifier: Apache-2.0
-->

# Automated release workflow

Arena cannot push files under `.github/workflows/`. Copy the complete workflow below to
`.github/workflows/release.yml` manually.

> **Branch decision:** this repository currently has no `main` branch. The owner selected the
> current default branch, `lineage-23.2`, for releases. If the repository is later migrated to
> `main`, change the one branch entry below.

## Standalone workflow (recommended)

```yaml
# Human-readable workflow name shown in the Actions tab.
name: production release

# Run after release-branch changes and permit a manual retry/bootstrap run.
on:
  push:
    # The owner selected the repository's existing default branch.
    branches:
      - lineage-23.2
    # Documentation-only changes must not publish a new binary release.
    paths-ignore:
      - 'docs/**'
      - '**/*.md'
  # Allows the owner to rerun after adding release-signer.sha256.
  workflow_dispatch:

# gh release create needs permission to create tags, releases, and release assets.
permissions:
  contents: write

jobs:
  release:
    # A GitHub-hosted Linux runner supplies git, gh, jq, and sha256sum.
    runs-on: ubuntu-latest
    steps:
      # A complete history is mandatory for git rev-list --count HEAD versioning.
      - name: Check out complete source history
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      # Android Gradle Plugin 8.7 is configured for Java 17 in this repository.
      - name: Set up Java 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: gradle

      # Install sdkmanager and expose ANDROID_HOME for the following build-tools step.
      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      # aapt validates package/version/debuggable metadata; apksigner validates the certificate.
      - name: Install Android build tools
        shell: bash
        run: |
          sdkmanager 'build-tools;35.0.0'
          echo "$ANDROID_HOME/build-tools/35.0.0" >> "$GITHUB_PATH"

      # The repository script owns decoding, building, validation, naming, and metadata creation.
      - name: Build and validate the production APK
        id: build
        shell: bash
        env:
          # The temporary decoded keystore never enters the repository or release assets.
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
          # Gradle reads these three values through the release signing configuration.
          KEYSTORE_PASS: ${{ secrets.KEYSTORE_PASS }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASS: ${{ secrets.KEY_PASS }}
          # gh reads the previous release's build-info.json for the monotonic version check.
          GH_TOKEN: ${{ github.token }}
          # Missing production signing inputs are fatal in CI.
          CI: 'true'
        run: scripts/ci-build.sh

      # Build a release body from values extracted from the validated APK, not guessed values.
      - name: Prepare release notes
        shell: bash
        run: |
          cat > "$RUNNER_TEMP/release-notes.md" <<'NOTES'
          ## Production APK

          - applicationId: `${{ steps.build.outputs.application_id }}`
          - versionCode: `${{ steps.build.outputs.version_code }}`
          - versionName: `${{ steps.build.outputs.version_name }}`
          - signer SHA-256: `${{ steps.build.outputs.signer_sha256 }}`
          - APK SHA-256: `${{ steps.build.outputs.apk_sha256 }}`
          - source for this commit: ${{ steps.build.outputs.source_url }}

          This release is signed for production; it cannot be installed over previous `dev`/`debug` versions—please uninstall the old version before installing this one.
          NOTES

      # Publish the APK, checksum, and machine-readable build-info.json under the generated tag.
      - name: Create GitHub Release
        shell: bash
        env:
          # gh uses the job-scoped token granted contents: write above.
          GH_TOKEN: ${{ github.token }}
        run: |
          gh release create '${{ steps.build.outputs.tag }}' dist/* \
            --title '${{ steps.build.outputs.tag }}' \
            --notes-file "$RUNNER_TEMP/release-notes.md" \
            --generate-notes
```

The source URL in the release body and `build-info.json` points to the exact commit used for the
APK. Keep that link and the corresponding source available to satisfy source-distribution
obligations. The release command uploads all three deliverables from `dist/`: the APK, its
`.sha256` file, and `build-info.json`.

## Alternative: reusable workflow

Either use the standalone workflow above **or** make it reusable; do not enable both branch
triggers, or one commit may attempt to create the same tag twice.

To create `.github/workflows/reusable-release.yml`, copy the standalone workflow, remove its
`push` and `workflow_dispatch` triggers, and replace them with this fully declared reusable input:

```yaml
name: reusable production release
on:
  workflow_call:
    secrets:
      KEYSTORE_BASE64: {required: true}
      KEYSTORE_PASS: {required: true}
      KEY_ALIAS: {required: true}
      KEY_PASS: {required: true}
permissions:
  contents: write
# Copy the complete `jobs:` block from the standalone workflow here unchanged.
```

Then save the following **10-line caller** as `.github/workflows/release.yml`:

```yaml
name: production release
on:
  push: {branches: [lineage-23.2], paths-ignore: ['docs/**', '**/*.md']}
  workflow_dispatch:
permissions:
  contents: write
jobs:
  release:
    uses: ./.github/workflows/reusable-release.yml
    secrets: inherit
```

A local reusable workflow is resolved from the same commit as its caller. `secrets: inherit`
passes the four repository secrets declared by `workflow_call`; the reusable workflow still maps
them explicitly into `scripts/ci-build.sh` as shown in the standalone job.
