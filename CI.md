# CI/CD Setup Guide

This document describes the automated build and release process for Futon.

## Active Mihon / Keiyoushi Test Workflow

The branch `fix/mihon-uncaught-exception-interceptor` contains a temporary workflow dedicated to validating the current Mihon/Tachiyomi Keiyoushi compatibility fix:

`/.github/workflows/mihon-fix-test-build.yml`

For the complete current handoff state read `.ai/MIHON_FIX_CONTEXT.md`. For a ready-to-paste continuation prompt for another coding AI, read `.ai/MIHON_FIX_HANDOFF_PROMPT.md`.

### Hard compatibility gate

The Mihon-specific regression test is:

```bash
./gradlew testDebugUnitTest \
  --tests "io.github.landwarderer.futon.mihon.compat.MihonNetworkHelperTest" \
  --stacktrace \
  --no-build-cache \
  --no-configuration-cache
```

It covers the application-interceptor contract expected by current Keiyoushi sources, derived OkHttp clients, User-Agent preservation/defaulting, and unchecked-exception conversion.

Do not weaken or make this focused regression test non-blocking merely to obtain an APK.

### Current verified CI state (2026-08-25)

A previous completed run, `32857638181`, passed the focused Mihon regression suite. Project-wide `lintRelease` then failed with unrelated pre-existing lint debt (`20 errors, 406 warnings`), which prevented the signed APK job.

The latest recorded run at the time this file was updated is `32870847041`. It was cancelled while the focused regression-test step was running:

- `Verify Mihon compatibility`: cancelled
- `Run Mihon regression tests`: cancelled
- `Run release lint`: skipped
- `Build signed optimized test APK`: cancelled
- `Record test build status`: success

That cancellation is not a compatibility-test pass or failure. Re-check the latest Actions run before acting because a newer documentation/status commit may have triggered another run.

At this snapshot the branch HEAD before the documentation refresh was `cf6b443a54ba87979012609f6fcbb70f7fe24074`, a bot status commit. `.ci/mihon-fix-latest.json` recorded:

```json
{
  "run_id": "32870847041",
  "source_sha": "cf48994fefcce3c29f446b850a2f77b5605f2963",
  "verify": "cancelled",
  "signed_release": "cancelled"
}
```

The report job can add a bot status commit after the source SHA. Inspect branch HEAD, its parent, the JSON and the actual workflow run together.

### Temporary workflow policy

The current workflow still invokes full project-wide `lintRelease` as a hard gate. For this temporary Mihon test lane, the intended policy is:

- focused `MihonNetworkHelperTest`: **hard gate**;
- optimized release compilation: **hard gate**;
- APK signature verification: **hard gate**;
- artifact upload: **hard gate**;
- legacy project-wide `lintRelease`: **diagnostic/non-blocking**, while retaining its report/artifact where practical.

Do not fix hundreds of unrelated legacy lint findings solely to unblock this scoped compatibility APK. If lint reveals a problem actually introduced by the Mihon patch, fix that regression.

The workflow also writes `.ci/mihon-fix-latest.json`. Status-only commits should use `[skip ci]`, and the next agent must verify that the status-report mechanism does not accidentally supersede/cancel the real build run.

### Signed optimized APK

After verification succeeds, the workflow is intended to build:

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

If normal repository signing secrets are available, they are used. Otherwise the temporary workflow generates a 4096-bit RSA test key valid for 30 days. A temporary-key APK cannot update an installation signed with another key, so uninstall/reinstall may be required.

Never expose signing secrets, keystore material, generated passwords or private keys.

### Artifact truth rule

Never tell the user the APK exists merely because the code or workflow looks correct. Before reporting success, verify all of the following against GitHub Actions:

1. final focused Mihon tests passed for the build source commit;
2. `assembleRelease` succeeded;
3. `apksigner verify` succeeded;
4. artifact `Futon-Mihon-Fix-Signed-Release` exists;
5. the artifact is downloadable and contains the expected APK/checksum/build info.

Only then provide the real artifact/run information to the user.

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

To enable normal release signing, configure the following secrets in the repository:

- `KEYSTORE_FILE`: Base64-encoded keystore
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

For local release builds the corresponding environment variables are read by Gradle.

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

### Release Build
```bash
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

### Nightly Build
```bash
./gradlew assembleNightly
# Output: app/build/outputs/apk/nightly/app-nightly.apk
```

## Monitoring Builds

- Release builds: GitHub Releases
- Nightly builds: GitHub Releases (pre-release)
- PR builds: Actions → Debug Build → Artifacts
- Mihon fix: Actions → Mihon Fix Signed Test Build, plus `.ci/mihon-fix-latest.json`

## Troubleshooting

### SDK location not found
Ensure Android SDK is configured. GitHub workflows use `android-actions/setup-android@v3`.

### Signing fails
Verify repository signing secrets/keystore encoding for production-key builds. The temporary Mihon workflow may fall back to a short-lived test key when secrets are unavailable.

### Mihon signed job is skipped
Inspect `Verify Mihon compatibility` first. A failing focused Mihon regression test is a real blocker. Legacy project-wide lint should be diagnostic for the temporary test lane, not mistaken for failure of the focused compatibility suite.

### Workflow gets cancelled
Check whether another push superseded the run via the workflow concurrency group. In particular, inspect status/documentation commits and the `Record test build status` job rather than assuming cancellation means a code failure.

## Certificate Fingerprint

The normal release keystore SHA-256 fingerprint documented by the project is:

```text
EF:48:B2:2E:F2:C5:40:45:53:1F:6E:76:00:C2:7E:C3:D0:3B:71:22:1E:0B:05:FF:B6:8E:33:57:CF:8E:4D:40
```

This matches the built-in app validator check in `AppValidator.kt`. Temporary test-key builds are explicitly test-only and may not satisfy assumptions tied to the production signature.
