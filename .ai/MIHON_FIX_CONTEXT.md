# Mihon / Keiyoushi Fix — AI Handoff Context

Last verified: 2026-08-25

## Mission

Continue the in-progress Mihon/Tachiyomi Keiyoushi extension compatibility fix in `madebycli/Futon` and produce a real installable signed optimized test APK for device testing.

**Work only on:** `fix/mihon-uncaught-exception-interceptor`

**Base branch:** `devel`

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

The branch currently adds/changes these relevant files:

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

The Mihon client also excludes Futon's `GZipInterceptor`, because that interceptor had already been documented as problematic for Mihon extension requests, and avoids duplicate compatibility interceptors copied from the base client.

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

### Last verified result

The focused Mihon regression tests **passed** in GitHub Actions run `32857638181` (`BUILD SUCCESSFUL`).

The normal PR debug build also succeeded for the fix branch.

## Current CI blocker — important

The signed test APK has **not been produced yet** because the `verify` job currently runs the entire project-wide `lintRelease` as a hard gate after the scoped Mihon tests.

In run `32857638181`:

- Mihon regression tests: **success**
- `lintRelease`: **failure**
- signed optimized APK job: **skipped**

Lint reported **20 errors and 406 warnings** across the existing project. The first reported error was unrelated to this Mihon patch:

```text
app/src/main/kotlin/io/github/landwarderer/futon/explore/data/MangaSourcesRepository.kt:387
SuspiciousIndentation
```

The relevant line was `result.addAll(MangaParserSource.entries)` after the declaration around line 386.

Do not assume this unrelated legacy lint debt means the Mihon regression fix failed. The focused tests passed.

### Recommended immediate next action

Inspect the **latest** workflow and Actions run before changing anything, because the status may have moved since this file was written. If the state is unchanged, make project-wide legacy lint diagnostic/non-blocking for this temporary test workflow (or otherwise gate only on regressions introduced by this change) while keeping the focused Mihon tests as a hard gate. Do not broaden this compatibility task into fixing hundreds of unrelated warnings merely to obtain a test APK unless there is a concrete reason.

Then push the workflow adjustment on the same fix branch and let the signed optimized test job run.

## Signed optimized test build

Workflow:

`/.github/workflows/mihon-fix-test-build.yml`

Intended artifact:

`Futon-Mihon-Fix-Signed-Release`

Intended APK name:

`Futon-9.8.1-mihon-fix-test-signed-release.apk`

The workflow builds `assembleRelease` with R8/minification/resource shrinking from the project's release configuration, verifies the APK with `apksigner`, and uploads a SHA-256 file plus `BUILD-INFO.txt`.

Signing behavior:

- If repository signing secrets are available, use the repository release key.
- Otherwise the workflow generates a temporary 4096-bit RSA test key valid for 30 days and records `temporary-test-key` in build info.
- An APK signed with the temporary key cannot update an installation signed with a different key; uninstall/reinstall may be required for testing.

Never expose keystore material, signing passwords, or GitHub secrets in logs, commits, docs, or chat.

## Machine-readable status

`.ci/mihon-fix-latest.json` records the latest workflow run/result. At the time of this handoff it recorded:

```json
{
  "run_id": "32857638181",
  "source_sha": "ef1f219de44db9c9a3ca5401708c2612d7ee4014",
  "verify": "failure",
  "signed_release": "skipped"
}
```

The branch may have an additional bot status commit after that source SHA. Always inspect current HEAD and current Actions runs rather than treating this snapshot as immutable.

## Definition of done for this task

1. Focused Mihon regression tests pass.
2. The optimized release APK builds successfully.
3. `apksigner verify --verbose --print-certs` succeeds.
4. The GitHub Actions artifact exists and can be downloaded.
5. Report the exact run, source commit, artifact name, signing kind, SHA-256 and any install caveat to the user.
6. User installs it and tests real Keiyoushi extensions, especially Comix: browse/popular/search, manga details, chapters and image/page loading.
7. Only after successful device testing should merging into `devel` be considered.

## Working rules for the next AI

- First read `AGENTS.md`, `.github/copilot-instructions.md`, `CI.md`, this file, the current diff vs `devel`, the current branch HEAD, and the latest Actions logs.
- Preserve the exact Mihon package/class names required by extensions.
- Preserve source-specific User-Agent headers.
- Preserve Futon's Cloudflare handling; do not replace it with a permanent no-op just to satisfy class-name checks.
- Keep `UncaughtExceptionInterceptor` in the application interceptor list, ahead of later application interceptors.
- Do not merge or push unrelated cleanup to `devel`.
- Never claim an APK exists until the workflow artifact actually exists.
