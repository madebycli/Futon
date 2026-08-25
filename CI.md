# CI/CD Setup Guide

This document describes the automated build and release process for Futon.

## Active Mihon / Keiyoushi Test Workflow

The branch `fix/mihon-uncaught-exception-interceptor` contains a temporary workflow dedicated to validating the current Mihon/Tachiyomi Keiyoushi compatibility fix:

`/.github/workflows/mihon-fix-test-build.yml`

For the complete handoff state and implementation details, read `.ai/MIHON_FIX_CONTEXT.md` before changing this workflow or the Mihon network bridge.

### What it verifies

The hard compatibility test is:

```bash
./gradlew testDebugUnitTest \
  --tests "io.github.landwarderer.futon.mihon.compat.MihonNetworkHelperTest" \
  --stacktrace \
  --no-build-cache \
  --no-configuration-cache
```

The test covers the application-interceptor contract expected by current Keiyoushi sources, derived OkHttp clients, User-Agent preservation/defaulting, and unchecked-exception conversion.

### Current known state

In GitHub Actions run `32857638181`, the focused Mihon regression tests completed successfully. The subsequent project-wide `lintRelease` task failed with existing lint debt (`20 errors, 406 warnings`), so the signed APK job was skipped. The first reported lint error was an unrelated `SuspiciousIndentation` finding in `app/src/main/kotlin/io/github/landwarderer/futon/explore/data/MangaSourcesRepository.kt` around line 387.

This means the scoped Mihon tests passed; it does **not** mean the Mihon compatibility test failed. Before acting on this snapshot, inspect the latest branch HEAD, `.ci/mihon-fix-latest.json`, and latest Actions run because CI may have advanced.

For this temporary test lane, avoid broad unrelated cleanup solely to satisfy legacy project-wide lint. Prefer keeping the focused regression tests as a hard gate and making legacy lint diagnostic/non-blocking or otherwise limiting the gate to regressions introduced by the fix.

### Signed optimized APK

After verification passes, the workflow builds:

```bash
./gradlew assembleRelease \
  -PversionName=9.8.1-mihon-fix-test \
  -PversionCode=90802 \
  --stacktrace \
  --no-build-cache \
  --no-configuration-cache
```

It verifies the resulting APK using `apksigner`, then uploads artifact `Futon-Mihon-Fix-Signed-Release` containing:

- `Futon-9.8.1-mihon-fix-test-signed-release.apk`
- its `.sha256` checksum
- `BUILD-INFO.txt`

If the normal repository signing secrets are available, they are used. Otherwise this temporary workflow generates a 4096-bit RSA test key valid for 30 days. A temporary-key APK cannot update an installation signed with another key, so uninstall/reinstall may be required. Never expose signing secrets or keystore material.

`.ci/mihon-fix-latest.json` is the machine-readable last-run status written by this workflow.

## Automated Workflows

The project uses GitHub Actions for continuous integration and automated releases:

### 1. Release Workflow (release.yml)
Automatically builds and publishes signed release APKs to GitHub Releases.

**Trigger:** Push a git tag with format `v*` (e.g., `v9.4.2`)
```bash
git tag v9.4.2
git push origin v9.4.2
```

**Output:** Signed release APK published to GitHub Releases

### 2. Nightly Workflow (nightly.yml)
Builds and publishes nightly APKs on a weekly schedule.

**Trigger:** Every Sunday at 2:00 UTC (or manual trigger via `workflow_dispatch`)
**Smart Skip:** Automatically skips the build if there are no new commits since the last nightly release

**Output:** Pre-release APK tagged as `N{YYYYMMDD}` (e.g., `N20251208`)

### 3. Debug Workflow (debug.yml)
Builds debug APK on pull requests for validation.

**Trigger:** On every pull request to `main` or `devel` branches
**Output:** Debug APK available as workflow artifact (7-day retention)

## Required GitHub Secrets

To enable automated signing, configure the following secrets in your GitHub repository settings:

### Setup Instructions

1. **Get the Keystore File (base64-encoded)**
   - If you have an existing keystore:
     ```bash
     base64 -w 0 your-keystore.jks
     ```
   - Copy the output

2. **Create GitHub Secrets**
   Navigate to: **Settings → Secrets and variables → Actions → New repository secret**

   Create these secrets:
   - **KEYSTORE_FILE**: Base64-encoded keystore file (entire output from step 1)
   - **KEYSTORE_PASSWORD**: Password for the keystore
   - **KEY_ALIAS**: Alias of the signing key (default: `futon-key`)
   - **KEY_PASSWORD**: Password for the signing key

### Example for Fresh Setup

A new keystore was generated with:
```
Key Alias: futon-key
Keystore Password: [from setup]
Key Password: [from setup]
SHA-256 Fingerprint: EF:48:B2:2E:F2:C5:40:45:53:1F:6E:76:00:C2:7E:C3:D0:3B:71:22:1E:0B:05:FF:B6:8E:33:57:CF:8E:4D:40
```

## Local Development Setup

The `build.gradle` is configured to support both local development and CI environments:

### For CI Environments
Environment variables are read automatically:
- `KEYSTORE_FILE`: Path to keystore (or set via secrets)
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

### For Local Development
If environment variables are not set, the build system will prompt for credentials interactively.

To set up locally with a keystore:
```bash
export KEYSTORE_FILE=/path/to/keystore.jks
export KEYSTORE_PASSWORD=your-password
export KEY_ALIAS=futon-key
export KEY_PASSWORD=key-password

./gradlew assembleRelease
```

## Building Variants

### Debug Build
```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Release Build (requires signing setup)
```bash
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

### Nightly Build (requires signing setup)
```bash
./gradlew assembleNightly
# Output: app/build/outputs/apk/nightly/app-nightly.apk
# Version: N{YYYYMMDD} (auto-generated from current date)
```

## Monitoring Builds

- **Release builds**: Check GitHub Releases
- **Nightly builds**: Check GitHub Releases (marked as pre-release)
- **PR builds**: Check "Actions" tab → "Debug Build" → Artifacts section
- **Mihon fix build**: Check "Actions" → "Mihon Fix Signed Test Build" and `.ci/mihon-fix-latest.json` on the fix branch

## Troubleshooting

### Build fails with "SDK location not found"
Ensure Android SDK is properly set up. The workflows use `android-actions/setup-android@v3` which handles this automatically.

### Signing fails with "keystore corrupted or password incorrect"
- Verify the base64 encoding of the keystore is correct
- Ensure all password secrets are set correctly
- Test locally: `keytool -list -v -keystore keystore.jks -storepass <password>`

### Nightly build is skipped unexpectedly
The workflow checks for commits since the last nightly release. If no commits exist, the build is skipped. Force a build with the "workflow_dispatch" trigger.

### Mihon test APK job is skipped
Inspect the `Verify Mihon compatibility` job first. The focused Mihon regression test and project-wide release lint are separate signals. At the documented handoff state, the regression test passed but legacy project-wide lint failed and prevented the signed job from running.

## Certificate Fingerprints

Current release keystore SHA-256 fingerprint:
```
EF:48:B2:2E:F2:C5:40:45:53:1F:6E:76:00:C2:7E:C3:D0:3B:71:22:1E:0B:05:FF:B6:8E:33:57:CF:8E:4D:40
```

This matches the built-in app validator check in `AppValidator.kt`. All release builds intended to behave as normal release installations must use a keystore with this fingerprint for proper app signature validation. Temporary Mihon test builds signed with the fallback test key are explicitly test-only and may not pass assumptions tied to the production signature.
