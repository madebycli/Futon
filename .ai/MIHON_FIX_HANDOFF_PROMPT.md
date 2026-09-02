# Ready-to-paste AI continuation prompt

You are continuing active work on the Android/Kotlin repository `madebycli/Futon`.

Repository: `https://github.com/madebycli/Futon`

Work only on branch:

`fix/mihon-uncaught-exception-interceptor`

Base branch:

`devel`

Open draft PR:

`#1 Fix Keiyoushi/Mihon default network interceptor compatibility`

Do **not** merge into `devel` until the user has installed a real test APK and verified actual Keiyoushi extensions, especially Comix.

## Goal

Finish and validate the current Mihon/Tachiyomi Keiyoushi compatibility fix, then produce a real signed optimized test APK as a GitHub Actions artifact for the user.

The original runtime error was:

```text
UncaughtExceptionInterceptor must be present in default client
```

The compatibility work already adds Mihon-compatible application interceptors and tests. Do not restart the implementation from scratch unless the tests prove the current approach is wrong.

## First actions — mandatory

Before editing code:

1. Read `AGENTS.md`.
2. Read `.github/copilot-instructions.md`.
3. Read `.ai/MIHON_FIX_CONTEXT.md` completely.
4. Read `CI.md`.
5. Inspect current branch HEAD and its parent.
6. Compare `fix/mihon-uncaught-exception-interceptor` against `devel`.
7. Inspect draft PR #1 and its current diff.
8. Read `.ci/mihon-fix-latest.json`.
9. Read `.github/workflows/mihon-fix-test-build.yml` at current HEAD.
10. Inspect the latest GitHub Actions run/jobs/logs for `Mihon Fix Signed Test Build`.

Repository/Actions state is authoritative. The static snapshot below may already be stale by the time you run.

## Last verified snapshot

At 2026-08-25 18:24 CEST, before the handoff-documentation commits:

- branch HEAD was `cf6b443a54ba87979012609f6fcbb70f7fe24074`;
- that HEAD was a bot status commit: `ci: record Mihon test build status [skip ci]`;
- draft PR #1 was open against `devel`;
- `.ci/mihon-fix-latest.json` recorded workflow run `32870847041`;
- run `32870847041` was cancelled while `Run Mihon regression tests` was still running;
- release lint was skipped in that cancelled run;
- the signed release job was cancelled;
- no signed APK artifact had been verified to exist.

A previous completed run, `32857638181`, **did pass** the focused `MihonNetworkHelperTest` suite, but the signed job was blocked afterward by unrelated project-wide `lintRelease` debt (`20 errors, 406 warnings`).

Do not interpret the later cancellation as a compatibility failure. Do not interpret the earlier legacy lint failure as a focused Mihon-test failure.

## Current implementation that must be preserved unless proven wrong

Relevant files include:

- `app/src/main/kotlin/eu/kanade/tachiyomi/network/interceptor/UncaughtExceptionInterceptor.kt`
- `app/src/main/kotlin/eu/kanade/tachiyomi/network/interceptor/UserAgentInterceptor.kt`
- `app/src/main/kotlin/eu/kanade/tachiyomi/network/interceptor/CloudflareInterceptor.kt`
- `app/src/main/kotlin/io/github/landwarderer/futon/mihon/compat/MihonNetworkHelper.kt`
- `app/src/test/kotlin/io/github/landwarderer/futon/mihon/compat/MihonNetworkHelperTest.kt`

`MihonNetworkHelper.client` must expose the Mihon/Keiyoushi compatibility classes in `client.interceptors()` as application interceptors. The intended leading order is:

1. `UncaughtExceptionInterceptor`
2. `UserAgentInterceptor`
3. `CloudflareInterceptor`

The exact class names/packages matter to current Keiyoushi compatibility checks.

Preserve these behaviors:

- `UncaughtExceptionInterceptor` remains in the application-interceptor chain, not only `networkInterceptors()`.
- source-specific User-Agent headers are not overwritten.
- default User-Agent is supplied when absent.
- derived clients from `client.newBuilder()` retain the compatibility interceptors.
- unchecked extension/interceptor exceptions are surfaced as normal `IOException` failures rather than app-crashing unchecked exceptions.
- Futon's real Cloudflare behavior remains functional; do not use a permanent no-op solely to satisfy a class-name check.
- Futon's problematic `GZipInterceptor` remains excluded from the Mihon client if that is still required by the current implementation/tests.

Usagi can be used as a reference because it has a similar base:

`https://github.com/UsagiApp/Usagi`

Do not copy Usagi blindly. In particular, a compatibility interceptor placed only in the OkHttp network-interceptor list does not satisfy the current Keiyoushi application-interceptor check.

## Required focused test

Keep this as a hard gate:

```bash
./gradlew testDebugUnitTest \
  --tests "io.github.landwarderer.futon.mihon.compat.MihonNetworkHelperTest" \
  --stacktrace \
  --no-build-cache \
  --no-configuration-cache
```

If this fails on the final source commit, fix the Mihon code/tests before producing an APK. Do not make this test non-blocking.

## Immediate CI work

The temporary workflow is:

`.github/workflows/mihon-fix-test-build.yml`

At the last verified source state it still ran full project-wide `lintRelease` as a hard gate. That is not appropriate for this scoped temporary test lane because the project already has unrelated legacy lint debt.

Update the temporary workflow so that:

- the focused Mihon regression test is a hard gate;
- optimized `assembleRelease` is a hard gate;
- APK signature verification is a hard gate;
- artifact upload is a hard gate;
- project-wide `lintRelease` remains visible/diagnostic but is non-blocking, unless it identifies a regression actually introduced by this patch.

Retain or upload lint diagnostics if practical.

Also inspect the `Record test build status` mechanism. It writes `.ci/mihon-fix-latest.json` and can create status-only bot commits. Ensure these commits use `[skip ci]` and verify that workflow concurrency/status commits do not repeatedly supersede or cancel the real source build. Do not guess — inspect actual Actions behavior/logs.

## Signed optimized test APK requirements

Target artifact name:

`Futon-Mihon-Fix-Signed-Release`

Target APK name:

`Futon-9.8.1-mihon-fix-test-signed-release.apk`

Target build command:

```bash
./gradlew assembleRelease \
  -PversionName=9.8.1-mihon-fix-test \
  -PversionCode=90802 \
  --stacktrace \
  --no-build-cache \
  --no-configuration-cache
```

Use the project's release optimization configuration. Verify the result using `apksigner verify --verbose --print-certs`.

Signing rules:

- if repository signing secrets are available, use the repository release key;
- otherwise a temporary short-lived test key is acceptable for this user-requested test APK;
- if a temporary key is used, clearly report that the APK may require uninstall/reinstall because it cannot update an installation signed with another key;
- never expose secrets, passwords, keystore contents, private keys or generated credentials in logs, commits, docs or chat.

## Definition of done

Do not stop at "the workflow should work". Continue until one of these is true:

### Success

1. Focused Mihon regression tests pass on the final source commit.
2. Optimized release build succeeds.
3. APK signature verification succeeds.
4. GitHub Actions artifact `Futon-Mihon-Fix-Signed-Release` actually exists.
5. Artifact is downloadable and contains the expected APK plus checksum/build info.
6. Report to the user:
   - exact workflow run ID;
   - exact source commit SHA;
   - artifact name;
   - APK filename;
   - signing kind (`repository-release-key` or `temporary-test-key`);
   - SHA-256;
   - installation caveat if applicable;
   - real GitHub artifact/run link or actual downloadable file reference.
7. Ask the user to test Comix and other Keiyoushi sources for browse/popular/search, details, chapters and image/page loading.
8. Keep PR #1 unmerged until the user confirms device testing.

### Blocked

If the build cannot succeed, report the exact failing job/step/log reason and fix it if it is within the scope of this branch. Do not claim an APK exists when it does not.

## Scope discipline

- Work only on `fix/mihon-uncaught-exception-interceptor`.
- Do not modify/merge `devel`.
- Avoid unrelated cleanup/refactors.
- Do not repair hundreds of pre-existing lint warnings merely to make the temporary APK lane green.
- Preserve compatibility class names and behavior.
- Keep documentation/context files updated when the real state changes.
- If you change workflow behavior, update `.ai/MIHON_FIX_CONTEXT.md`, `CI.md` and `.ci/mihon-fix-latest.json` semantics/docs as appropriate.
- Never fabricate test results, artifacts, links or signatures.

Proceed autonomously: inspect, patch, test, iterate, build, verify and only then report the real APK artifact.
