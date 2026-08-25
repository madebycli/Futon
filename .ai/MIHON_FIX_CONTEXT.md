# Mihon / Keiyoushi Fix — AI Handoff Context

Last verified: 2026-08-25 18:24 CEST

## Mission

Continue the in-progress Mihon/Tachiyomi Keiyoushi extension compatibility fix in `madebycli/Futon` and produce a real installable signed optimized test APK for device testing.

**Work only on:** `fix/mihon-uncaught-exception-interceptor`

**Base branch:** `devel`

**Open PR:** #1, draft — `Fix Keiyoushi/Mihon default network interceptor compatibility`

Do not merge into `devel` until the test APK has been installed and real Keiyoushi extensions (especially Comix) have been verified by the user.

## Original failure

Opening a Keiyoushi/Mihon source in Futon could fail with:

```text
UncaughtExceptionInterceptor must be present in default client
```

The problem is in Futon's Mihon compatibility/network bridge, not specifically in Comix. Current Keiyoushi source code validates the expected Mihon interceptor classes on the default OkHttp application-interceptor chain (`client.interceptors()`). A network interceptor alone does not satisfy that contract.

## Reference implementation

Usagi has a similar base and can be used as a compatibility reference:

- https://github.com/UsagiApp/Usagi

Do not copy it blindly. In particular, placing `UncaughtExceptionInterceptor` only in `networkInterceptors()` is insufficient for the current Keiyoushi validation.

## Fix already implemented on this branch

Relevant files changed by the fix include:

- `app/src/main/kotlin/eu/kanade/tachiyomi/network/interceptor/UncaughtExceptionInterceptor.kt`
- `app/src/main/kotlin/eu/kanade/tachiyomi/network/interceptor/UserAgentInterceptor.kt`
- `app/src/main/kotlin/eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt`
- `app/src/main/kotlin/io/github/landwarderer/futon/mihon/compat/MihonNetworkHelper.kt`
- `app/src/test/kotlin/io/github/landwarderer/futon/mihon/compat/MihonNetworkHelperTest.kt`
- `.github/workflows/mihon-fix-test-build.yml`
- `.ci/mihon-fix-latest.json`

`MihonNetworkHelper.client` is rebuilt from Futon's manga client and deliberately installs the following **application interceptors first and in this order**:

1. `UncaughtExceptionInterceptor`
2. `UserAgentInterceptor`
3. `CloudflareInterceptor`

The exact class names matter because Keiyoushi checks them. Futon's existing class is spelled `CloudFlareInterceptor`, so the compatibility class is needed while preserving the existing Cloudflare behavior rather than merely faking a class name.

The Mihon client excludes Futon's `GZipInterceptor`, because that interceptor is incompatible with some Mihon extension requests, and avoids duplicate compatibility interceptors copied from the base client.

## Regression tests

Run the focused test suite with:

```bash
./gradlew testDebugUnitTest \
  --tests "io.github.landwarderer.futon.mihon.compat.MihonNetworkHelperTest" \
  --stacktrace \
  --no-build-cache \
  --no-configuration-cache
```

The tests verify at least:

- required interceptor names are exposed by the default client;
- required interceptors survive `client.newBuilder().build()`;
- the default User-Agent is added only when missing;
- a source-specific User-Agent is preserved;
- unchecked interceptor failures are wrapped as `IOException`.

### Verified test history

GitHub Actions run `32857638181` completed the focused `MihonNetworkHelperTest` suite successfully (`BUILD SUCCESSFUL`). The normal PR debug build also succeeded for the fix branch at that stage.

The latest recorded Mihon test workflow run is `32870847041`. It was **cancelled while the focused regression test step was running**, so it is not evidence of either a pass or a regression:

- `Verify Mihon compatibility`: cancelled
- `Run Mihon regression tests`: cancelled
- `Run release lint`: skipped
- `Build signed optimized test APK`: cancelled
- `Record test build status`: success

Always inspect the latest workflow run before relying on this snapshot.

## Current branch / CI snapshot

At the last verification:

- Branch HEAD: `cf6b443a54ba87979012609f6fcbb70f7fe24074`
- HEAD commit: `ci: record Mihon test build status [skip ci]`
- Source commit immediately before that status commit: `410ca1b11b73942912371b838c71911976553f6b`
- Draft PR #1 is open against `devel`
- `.ci/mihon-fix-latest.json` records run `32870847041`
- The workflow artifact `Futon-Mihon-Fix-Signed-Release` has **not** been verified to exist yet
- Therefore no APK may be claimed or linked yet

The machine-readable status currently records:

```json
{
  "run_id": "32870847041",
  "source_sha": "cf48994fefcce3c29f446b850a2f77b5605f2963",
  "verify": "cancelled",
  "signed_release": "cancelled"
}
```

The workflow's report job can create a bot status commit after the source SHA, so compare the branch HEAD, its parent, the status JSON and the actual Actions run rather than assuming they are the same commit.

## Current workflow blocker / next action

The current `.github/workflows/mihon-fix-test-build.yml` still executes full project-wide `lintRelease` as a **hard gate** after the focused Mihon regression test.

An earlier completed run showed the focused Mihon tests passing while project-wide lint failed with unrelated legacy debt (`20 errors, 406 warnings`; first observed error was `SuspiciousIndentation` in `MangaSourcesRepository.kt`). Do not broaden this task into repairing hundreds of unrelated lint findings just to create the temporary test APK.

The next AI should:

1. Re-read the current workflow at HEAD and inspect the latest Actions jobs/logs.
2. Keep the focused Mihon regression test as a hard gate.
3. Make legacy project-wide `lintRelease` diagnostic/non-blocking for this temporary Mihon test workflow, while still uploading/reporting lint diagnostics if useful.
4. Ensure the status-report mechanism does not accidentally prevent or continuously supersede the real build run. Preserve `[skip ci]` on status-only commits and verify actual Actions behavior instead of assuming it.
5. Push only to `fix/mihon-uncaught-exception-interceptor` and allow a fresh workflow run to complete.
6. If the focused regression test fails, fix the Mihon compatibility code before attempting the APK build.
7. If verification passes, let the signed optimized release job build, verify, and upload the artifact.

## Signed optimized test build

Workflow:

`/.github/workflows/mihon-fix-test-build.yml`

Intended artifact:

`Futon-Mihon-Fix-Signed-Release`

Intended APK name:

`Futon-9.8.1-mihon-fix-test-signed-release.apk`

The workflow is intended to build `assembleRelease` with the project's optimized release configuration, verify the APK with `apksigner`, and upload the APK plus SHA-256/build information.

Signing behavior in the current workflow:

- If repository signing secrets are available, use the repository release key.
- Otherwise generate a temporary 4096-bit RSA test key valid for 30 days and record `temporary-test-key` in build info.
- A temporary-key APK cannot update an installation signed with a different key; uninstall/reinstall may be required for testing.

Never expose keystore material, signing passwords, generated passwords, private keys or GitHub secrets in logs, commits, docs or chat.

## Definition of done

1. Focused Mihon regression tests pass on the final source commit.
2. The optimized release APK builds successfully.
3. `apksigner verify --verbose --print-certs` succeeds.
4. GitHub Actions artifact `Futon-Mihon-Fix-Signed-Release` exists and can be downloaded.
5. Record/report the exact workflow run ID, source commit, artifact name, signing kind, APK SHA-256 and install caveat.
6. User installs it and tests real Keiyoushi extensions, especially Comix: browse/popular/search, manga details, chapters and image/page loading.
7. Only after successful device testing should merging into `devel` be considered.

## Working rules for the next AI

- First read `AGENTS.md`, `.github/copilot-instructions.md`, `CI.md`, this file, and `.ai/MIHON_FIX_HANDOFF_PROMPT.md`.
- Then inspect the current branch HEAD, diff vs `devel`, PR #1, `.ci/mihon-fix-latest.json`, the workflow file and latest Actions jobs/logs.
- Treat repository state and Actions results as authoritative over any static snapshot in docs.
- Preserve the exact Mihon package/class names required by extensions.
- Preserve source-specific User-Agent headers.
- Preserve Futon's Cloudflare handling; do not replace it with a permanent no-op just to satisfy class-name checks.
- Keep `UncaughtExceptionInterceptor` in the application interceptor list, ahead of later application interceptors.
- Do not merge or push unrelated cleanup to `devel`.
- Do not hide failing focused tests by weakening the Mihon-specific test gate.
- Never claim an APK exists until the workflow artifact actually exists.
