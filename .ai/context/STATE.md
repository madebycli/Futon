# Current Mihon/Keiyoushi Compatibility State

Last manually refreshed: 2026-09-01

## Repository safety and live state

- Repository: `madebycli/Futon`
- Working branch: `fix/mihon-uncaught-exception-interceptor`
- Base branch: `devel`
- Draft PR: #1, `Fix Keiyoushi/Mihon default network interceptor compatibility`
- PR #1 remains open, Draft, unmerged and based on `devel`.
- Never merge PR #1 or modify `devel` directly without explicit user approval.
- Never publish a release without explicit user approval.
- Never expose signing secrets, private keys, passwords or tokens.
- App test version: `9.8.1-mihon-fix-test` (`90803`).
- Host baseline: `minSdk = 26`.

Context and status commits use `[skip ci]` and may advance the branch after the last tested source tree. Always distinguish the current branch head from the last CI-verified source/test head.

## Current authoritative tested tree

The current CI-verified source/test head is:

`78d128189277167cd2f0c84979c9f94139b9ff05`

Its tree is:

`2848e13c2b26566137a4a252a6f5c418fee8d012`

The PR workflow built synthetic merge:

`6f690e43de883bc71897e5fa9c70cfc9c49d88eb`

The synthetic merge has the same exact tree `2848e13c2b26566137a4a252a6f5c418fee8d012`, so the verified PR build corresponds exactly to the feature source tree.

The last source fix inside this tested tree is:

`809a8900f9f662b516b61eb7443cbf6c78021e6a`, `fix(mihon): preserve legacy snapshot fields on restore`.

The final head `78d128...` adds the stricter regression fixture that keeps legacy and modern snapshot fields intentionally distinct.

## Current root cause and durable snapshot fix

The latest confirmed compatibility family was state loss across repository and process lifetimes.

Earlier, a repository-instance-local chapter cache could lose complete extension-provided `SChapter` metadata when a later request used another repository instance. That was first fixed with a shared process-local chapter snapshot store.

The newer problem boundary is app process restart and database restore. Futon can restore a saved Mihon-backed manga before the asynchronous extension scan has recreated the corresponding runtime source. Process-local snapshots alone cannot survive that lifecycle.

The current generic solution is:

- `MihonSnapshotPersistence` stores host-owned `SManga` and `SChapter` snapshots on disk.
- The file is `mihon-model-snapshots-v1.json` below `noBackupFilesDir` so stale extension state is not restored through Android backup.
- Schema version is `1`.
- Manga snapshots are bounded to 500 entries.
- Chapter snapshots are bounded to 1000 entries.
- Keys are source-aware and use `sourceId + exact URL`; URLs are not normalized.
- Access is synchronized and the bounded maps use access-order behavior.
- Extension implementation objects are never serialized. Only values copied into Futon's host-owned models are persisted.
- Writes use a temporary file, `fsync`, and atomic replacement when supported.
- A corrupt file becomes a cache miss and must never break startup.
- An unknown schema version is ignored safely.
- `AwaitingMihonMangaRepository` handles a Mihon source restored from Futon's database before the extension scan finishes.
- It waits for `MihonExtensionManager.awaitInitialLoad()` only when a restored Mihon source needs resolution, then delegates to the real `MihonMangaRepository`.
- `ExternalExtensionManagerRuntime` completes the initial-load readiness signal in `finally`, including empty and failure scan outcomes, so normal scan completion cannot leave the restored repository waiting forever.

### Restore fidelity bug found by the new test

The first persistence test run found a real fidelity issue rather than a false CI failure.

Host setters can mirror modern fields into legacy fields:

- setting `SManga.genres` can rewrite legacy `genre`
- setting `SChapter.number` can derive `chapter_number`
- setting `SChapter.scanlators` can rewrite legacy `scanlator`

Commit `809a890...` fixes restore order by applying modern values first and then reapplying the separately persisted legacy values. This preserves both ABI views exactly across restart.

The final regression fixture intentionally gives legacy and modern fields distinct values, so a future setter-order regression cannot hide behind equivalent fixture data.

## Final CI and APK verification for `78d128...`

### Debug build

- Workflow: Debug Build (PR)
- Run id: `33536918698`
- Run number: `148`
- Result: `success`
- Debug APK build: `success`
- Debug APK upload: `success`

### Mihon signed optimized build

- Workflow: `Mihon Fix Signed Test Build`
- Run id: `33536918663`
- Run number: `295`
- Result: `success`
- Focused Mihon regression suite: `49/49`, success
- Release lint: success
- Optimized R8 release build: success
- Optimized Mihon runtime ABI gate: success
- APK signature verification: success
- Artifact upload: success

Artifact:

- Name: `Futon-Mihon-Fix-Signed-Release`
- Artifact id: `9812861083`
- Artifact digest: `sha256:e174ceb153e80c28ee70b9a883a174a12a074c76513c2d066c5b3c65eac9e367`
- APK: `Futon-9.8.1-mihon-fix-test-signed-release.apk`
- APK SHA-256: `4f0bdca5bc1bf29f37663485275dacedc25adf36d20ee58fb10cfe4cf1b6b745`
- The artifact-bundled `.sha256` matches the locally calculated APK hash exactly.
- `BUILD-INFO.txt` records source commit `6f690e43de883bc71897e5fa9c70cfc9c49d88eb` and `Signing: repository-release-key`.
- The synthetic merge and feature head have the same tree, so this artifact verifies the `78d128...` feature tree.

Important signing update: the repository release signing key is now available to the workflow and was used for this final optimized APK. This supersedes the older temporary-test-key artifact state. Do not expose or print any signing secret material.

`.ci/mihon-fix-latest.json` now records this final run and artifact.

## Persistence regression coverage

`MihonSnapshotPersistenceTest` is part of the focused CI filter and covers:

- manga and chapter metadata surviving construction of a new persistence store, modeling process restart
- legacy and modern fields remaining independently faithful after restore
- source isolation for identical URLs under different source IDs
- corrupt snapshot file fallback and subsequent recovery
- unsupported schema version fallback

The existing `MihonMangaRepositoryTest` continues to cover repository-instance changes, source isolation and defensive snapshot behavior at the repository boundary.

## Current upstream references, live checked 2026-09-01

### Kototoro

- Repository: `Kototoro-app/Kototoro`
- Branch: `devel`
- Current live head: `b2c20e84298bfcc806567d784c8cb6607b1c919f`
- Previous recorded head: `19cbb0790744eb28e5accead7e9514d976b02f3d`
- The current head is 32 commits ahead of the previous recorded head.
- The exact `TachiyomiApkClassLoaderPolicy.kt` and `KotoNetworkHelper.kt` reference paths do not appear in that compare delta.
- The broader delta contains unrelated and adjacent app work, including favourites, setup/wizard and Tsundoku integration. Do not claim all Mihon-adjacent behavior is unchanged without checking the exact path relevant to a future failure.
- Kototoro remains the preferred reference implementation for a demonstrated compatibility failure.

### Keiyoushi extensions-lib / TachiyomiX 1.6

- Repository: `keiyoushi/extensions-lib`
- Branch: `main`
- Current live head: `42255ee5fa96d9425697b7c143587483207308d1`
- Previous recorded head: `18a8e26be2320b48bdaa11840170479b62989e23`
- Current head merged the 1.6 line and now tracks ABI changes explicitly.

The current 1.6 changelog includes the modern suspend contract such as:

- `Source.getPopularManga`
- `Source.getLatestUpdates`
- `Source.getSearchManga`
- `Source.getMangaUpdate`
- `Source.getPageList`
- `Source.getFilterList`
- `HttpSource.getHomeUrl`
- `HttpSource.getImageUrl`
- `SManga.memo`
- `SChapter.memo`
- standardized `HttpException`
- `Call.awaitSuccess`

It deprecates older Rx request APIs and `NetworkHelper.cloudflareClient`, and removes `HttpSource.fetchImage`.

Source-level audit against Futon `78d128...` found no missing current 1.6 `Source` or `HttpSource` ABI entry. Futon's host remains intentionally broader because it also preserves legacy and fork compatibility paths. The green optimized ABI gate plus this source review are strong compatibility evidence, but real device behavior still outranks them.

## Implemented compatibility that remains active

- Required Mihon default client application-interceptor order: `UncaughtExceptionInterceptor`, `UserAgentInterceptor`, `CloudflareInterceptor`.
- Mihon client derives from the complete host OkHttp configuration while rebuilding only interceptor lists.
- Modern and legacy Brotli/Zstd runtime compatibility.
- Modern suspend `Source` / `HttpSource` paths plus legacy Rx `fetch*` fallback.
- `SManga.memo`, `SChapter.memo`, `SMangaUpdate`, `Source.getMangaUpdate(...)` and combined repository update path.
- `RefreshContext` and source/request context compatibility retained for already-published fork extensions.
- `minSdk = 26` to avoid the historical serializer/default-method desugaring ABI mismatch.
- Kototoro-style Tachiyomi APK classloader ownership policy with host-owned ABI namespaces and child-first `$-CC` / `$DefaultImpls` bridges.
- Source browser-origin preservation.
- Shared Mihon source preference namespace.
- Protobuf extension-lib metadata preservation.
- Extension repository failure isolation and fallback version parsing.
- Chromium/WebView Cloudflare solve with clearance change detection and retry of the original source request.
- Shared process-local chapter snapshot protection plus the newer durable restart persistence layer.

## Historical resolved root causes

Do not rediscover these as new bugs unless a current device log proves a regression:

1. `UncaughtExceptionInterceptor must be present in default client`, resolved by the Mihon-compatible default client contract.
2. Missing host-visible `okhttp3.brotli.BrotliInterceptor`, resolved by host runtime support.
3. `GeneratedSerializer.typeParametersSerializers()` `AbstractMethodError`, resolved by `minSdk = 26` and aligned host ABI.
4. Obsolete details/chapter repository path, resolved by modern combined update handling plus legacy fallback.
5. `SManga.getMemo` `NoSuchMethodError`, resolved.
6. `Source.getMangaUpdate` `NoSuchMethodError`, resolved.
7. Mihon 1.6 MangaDex `0 manga` continuity family, resolved/superseded by the current repository path.
8. Repository-instance-local chapter snapshot loss, resolved by the shared snapshot store.
9. Disk restore fidelity loss caused by model setters mutating legacy fields, resolved by restore ordering in `809a890...` and strict regression coverage.

Historical Comix evidence proved Futon's retained WebView Cloudflare path could change `cf_clearance`, retry the original request and return HTTP 200. This does not prove every current Cloudflare flow.

## Open unverified Kototoro parity delta

Kototoro has a richer Cloudflare/captcha orchestration than Futon's retained implementation, including SingleFlight/resolver-state handling, automatic/manual strategy selection, recent-success behavior, explicit manual fallback and per-source automatic captcha control.

Status: unverified parity delta, not a confirmed current root cause.

Do not port it only because it is newer. If a current device failure on the final APK points at this path and Kototoro succeeds on the equivalent case, inspect the current exact Kototoro files at the live head and port the smallest proven semantic delta with attribution and regression coverage.

## Samsung installation evidence and next decisive validation

Historical Samsung Galaxy S25 Ultra evidence showed Google Play Protect blocking an earlier optimized Futon APK as an unknown developer. `Trotzdem installieren` did not complete.

That historical APK used the temporary test signing path. The final `78d128...` optimized APK is now signed with `repository-release-key`, so the next decisive installation test must use this exact artifact before drawing further conclusions about Samsung or Play Protect.

Current open runtime node: `POST_78D_REPOSITORY_KEY_DEVICE_VALIDATION`.

Test sequence:

1. Install the final repository-key APK on the Samsung device.
2. If an older Futon signed with another key is installed and Android reports a signature/update conflict, uninstall that old test build once and perform a clean install.
3. Record whether Play Protect still blocks the clean repository-key installation.
4. If a clean repository-key install is still blocked, capture the exact Play Protect/installer message or code. Android Developer Verification or Play distribution may then be required for a normal user-facing distribution path.
5. Once installed, validate Mihon source flows on the same exact APK.

Required source coverage where supported:

- Comix
- MangaDot.net
- Manga Ball
- Weeb Central
- MangaRead.org

Required paths:

- browse/popular
- search
- details
- chapters
- pages/images
- app restart followed by reopening previously loaded Mihon manga/chapter data

For any runtime failure, capture logcat around the first real exception and group repeated red lines by unique root cause. Current real device evidence remains higher priority than all CI and source-level audits.
