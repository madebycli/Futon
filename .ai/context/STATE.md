# Current Mihon/Keiyoushi Compatibility State

Last manually refreshed: 2026-08-27

## Repository state

- Repository: `madebycli/Futon`
- Working branch: `fix/mihon-uncaught-exception-interceptor`
- Base branch: `devel`
- Draft PR: #1, `Fix Keiyoushi/Mihon default network interceptor compatibility`
- Live-observed tested PR head before this context refresh: `9e5b7922bd2c71fbd8e3ac8c1dbe9eddf707660f`.
- Latest app/source-changing head in that tested tree: `85f19b491f2c4837e95c99e828fdb28f32d960c0`.
- PR #1 is open, `draft: true`, unmerged, base `devel` at `05f11b2e6d46993677eec4e7eb66fde2c76e5a4b`.
- App test version: `9.8.1-mihon-fix-test` (`90803`).
- Host baseline: `minSdk = 26`.
- The PR workflow built synthetic merge commit `fd2effcb2a90f2eae4047498f6734cebb9563682`. Its tree is `9324f0bf91078df59a7ccd922082b69806c37c10`, identical to tested PR head `9e5b792...`.

Always re-fetch live values before new work. Context/status commits use `[skip ci]` and may advance the branch while `9e5b792...` remains the currently tested PR source tree until another app/test source change is made.

## Current CI / APK validation for tested head 9e5b792

Tested PR head: `9e5b7922bd2c71fbd8e3ac8c1dbe9eddf707660f`
Latest app/source-changing commit in the tree: `85f19b491f2c4837e95c99e828fdb28f32d960c0`
Synthetic PR merge built by Actions: `fd2effcb2a90f2eae4047498f6734cebb9563682`
Tree: `9324f0bf91078df59a7ccd922082b69806c37c10`

- Debug workflow: run `33012858726`, `success`.
- Debug artifact: `Futon-Mihon-Fix-Debug-Native`, artifact id `9623782588`.
- Dedicated Mihon signed-test workflow: run `33012858721`, `success`.
- Focused Mihon regression tests: `success`.
- Release lint diagnostic: `success`.
- Optimized R8 release build: `success`.
- APK signature verification in CI: `success`.
- Signed artifact upload: `success`.
- Signed artifact: `Futon-Mihon-Fix-Signed-Release`, artifact id `9623914916`.
- Signed artifact ZIP SHA-256: `fb85ca060bc7cd7c94c2a65fed7601ade155c109dc5b7482baba218b89876438`.
- Signed APK SHA-256, verified against the artifact's bundled `.sha256` file and local `sha256sum`: `bcbe14a5d536703aa2f0c278f9668238d03845bc8a33974bc7891aca58fad25f`.
- Signing kind reported by the workflow: `temporary-test-key`. This APK cannot update an installation signed by a different key; uninstall/reinstall may be required.
- `BUILD-INFO.txt` records source commit `fd2eff...` because the workflow ran on `pull_request`; the built tree is identical to PR head `9e5b792...`.

Direct DEX inspection of the optimized APK confirmed the current workflow ABI/runtime gate symbols:

- `com/squareup/zstd/ZstdCompressor`
- `androidx/preference/PreferenceScreen`
- `eu/kanade/tachiyomi/source/ConfigurableSource`
- `eu/kanade/tachiyomi/source/Source`
- `eu/kanade/tachiyomi/source/model/RefreshContext`
- `eu/kanade/tachiyomi/source/online/HttpSource`
- `eu/kanade/tachiyomi/source/model/SManga`
- `eu/kanade/tachiyomi/source/model/SChapter`
- `eu/kanade/tachiyomi/source/model/Page`
- `getMangaUpdate`
- `fetchRelatedMangaList`
- `getChapterList`
- `getPageList`
- `fetchPageList`
- `CaptchaAutoResolveCoordinator`
- `TachiyomiApkClassLoaderPolicy`
- `org/brotli/dec/BrotliInputStream`

Do not treat symbols that are not part of the current host contract or current workflow gate as missing-runtime bugs without device evidence.

`.ci/mihon-fix-latest.json` records run `33012858721`, source `9e5b792...`, verification `success`, signed release `success`.

## Current upstream references

### Kototoro

- Repository: `Kototoro-app/Kototoro`
- Branch: `devel`
- Live verified SHA: `f4f37a5b7290da05c10b9325912f2a37ebeff0f9`
- Previous recorded reference SHA: `dec0ef781644245f6937dc1cafc8ca84963fe08e`.
- Comparing `dec0ef...` to `f4f37...` shows 9 newer commits, but they do not modify the Mihon/Tachiyomi runtime, Source ABI, network helper, classloader policy, or extension-runtime files used as the compatibility reference here.
- Current `TachiyomiApkClassLoaderPolicy.kt` still uses explicit host-owned ABI packages plus child-first `$-CC` / `$DefaultImpls` bridge exceptions.
- Current `HttpSource.kt` continues the modern suspend API plus legacy `fetch*` compatibility pattern used as Futon's reference.
- Current Kototoro Cloudflare/captcha orchestration is newer than Futon's retained implementation. This is an unverified parity delta, not a confirmed current device root cause.

Current Kototoro files inspected in this round at `f4f37a5...`:

- `app/src/main/kotlin/eu/kanade/tachiyomi/source/Source.kt`, blob `1876ee1e16fedfc749b2f0a57a9e9a1533ea7058`.
- `app/src/main/kotlin/eu/kanade/tachiyomi/source/online/HttpSource.kt`, blob `73d06b35bc3c6b7427032401d078508715a04241`.
- `app/src/main/kotlin/org/skepsun/kototoro/mihon/compat/KotoNetworkHelper.kt`, blob `f98fffe205c63b1e1ee75028c87792b9c3ff38ee`.
- `app/src/main/kotlin/org/skepsun/kototoro/extensions/runtime/tachiyomi/TachiyomiApkClassLoaderPolicy.kt`, blob `77c7d23fb73d6a5764db1dba8a0068cfd5aa36df`.
- `app/src/main/kotlin/org/skepsun/kototoro/core/exceptions/resolve/CaptchaHandler.kt`, blob `ac15513dc32ee304057736bafb6e974072e3e07e`.
- `app/src/main/kotlin/org/skepsun/kototoro/core/exceptions/resolve/CaptchaAutoResolveCoordinator.kt`, blob `ca78c86da146117dae8af3fd7057d1f3ba47d2c7`.
- `app/src/main/kotlin/org/skepsun/kototoro/core/exceptions/resolve/CloudFlareSingleFlight.kt`, blob `1f664384597a218986369893e6de92eb04c1023a`.
- `app/src/main/kotlin/org/skepsun/kototoro/core/network/webview/WebViewExecutor.kt`, blob `66147b90d41a81bc3805e67303c02883041cd01f`.
- `app/src/main/kotlin/org/skepsun/kototoro/core/prefs/SourceSettings.kt`, blob `fa3fc4b3b12a996c2d65f941aa5b1ef50134fb13`.

### Keiyoushi extensions-lib

- Repository: `keiyoushi/extensions-lib`
- Branch: `main`
- Live verified SHA: `18a8e26be2320b48bdaa11840170479b62989e23`
- This is unchanged from the previous context round and remains the current host-contract reference observed here.

## Compatibility work implemented

### Default NetworkHelper / compression contract

- Required default application-interceptor order remains:
  1. `UncaughtExceptionInterceptor`
  2. `UserAgentInterceptor`
  3. `CloudflareInterceptor`
- Futon derives the Mihon client from `baseClient.newBuilder()`, preserving host proxy/TLS/DNS/cache/authenticator/dispatcher/connection/timeouts and rebuilding only interceptor lists.
- Incompatible host compression interceptors are removed from the modern default Mihon/Keiyoushi client.
- Legacy/fork-specific interceptor names `GZipInterceptor`, `IgnoreGzipInterceptor`, and `BrotliInterceptor` are filtered as well as the concrete OkHttp Brotli singleton.
- Compatible unrelated host interceptors are retained.
- `cloudflareClient` remains a legacy compatibility client with Brotli as a separate network interceptor; current Keiyoushi paths continue using the Brotli-free default client.
- Zstd is explicitly referenced in the static host graph so dynamically loaded extensions can resolve it.

### HttpSource modern + legacy compatibility

- `HttpSource.client` retains legacy Brotli behavior through `NetworkHelper.cloudflareClient`; newer KeiSource implementations may override to the modern default client.
- Suspend APIs execute direct OkHttp request/parser paths for modern sources.
- Reflection-based compatibility detects custom legacy `fetch*` overrides and dispatches to them when the corresponding request helper was not/should not be used.
- `UnsupportedOperationException` fallback preserves legacy custom fetch behavior.
- `getHomeUrl()` returns `baseUrl`.
- Image requests use source tagging and cacheless/progress-aware network calls with HTTP success checking.
- Modern request ABI includes `RefreshContext` and current request tagging/source context behavior adapted from Kototoro.
- `MihonModernHostContractTest` and the expanded compatibility suite protect modern and legacy contracts.

### Post-157d host-ABI and repository alignment already present

The context previously stopped too early at `157d94e...`. The tested `9e5b792...` tree contains additional compatibility work, including:

- `e617a5d5f4c356d43d0830643fdbad52e896b95b`, `fix(mihon): align HttpSource request ABI with Kototoro`, including `RefreshContext`, source request tagging and modern HttpSource APIs.
- `fd81be4797bbcdf3752ed06ba514b0cfda2156c0`, `fix(mihon): adapt mapper to modern genres property`.
- `d5c344c126c8cecd7716c77c0871418b6c6ec522`, `fix(mihon): preserve protobuf extension lib version`.
- `87bbda494c48355f9c98754b82143533f292b777`, `fix(mihon): preserve declared source browser origin`.
- `4f254b2b838338a6bdd0ca1a4bac83fb4785b4a2`, `fix(mihon): share source preference namespace`, aligning Mihon source preferences to `source_<mihonId>`.
- `00a488a1b25dba7dc5f18b42e30660fb4ad768db`, `fix(mihon): isolate extension repository failures`, adapted from Kototoro so one repository failure does not fail all repositories.
- `85f19b491f2c4837e95c99e828fdb28f32d960c0`, `fix(mihon): align extension repo fallback parsing`, the latest app/source-changing commit in the tested tree.
- Later commits through `9e5b792...` expand regression and optimized-APK ABI verification without another app/source code change after `85f19b...`.

### ClassLoader / ABI ownership

The old context entry saying this port was deferred is **superseded**. The port is implemented on the current branch.

- `ChildFirstPathClassLoader` extends `DexClassLoader`.
- It consults `ChildFirstClassLoaderPolicy`, which delegates to the shared `TachiyomiApkClassLoaderPolicy`.
- Host-owned namespaces include Java/Kotlin/Android, coroutines, JSON/Jsoup, OkHttp/Okio, Rx, Tachiyomi source/network/util ABI, Injekt, IReader, Ktor and Fleeksoft.
- Generated `$-CC` and `$DefaultImpls` bridge classes remain child-first.
- Regression coverage exists in `TachiyomiApkClassLoaderPolicyTest`.
- Futon files retain attribution noting they were ported/adapted from Kototoro, Apache-2.0.

### Previously implemented and retained

- `SManga.memo`.
- `SMangaUpdate`.
- `Source.getMangaUpdate(...)` with legacy details/chapter fallback.
- `MihonMangaRepository` combined update path.
- `minSdk = 26` to avoid host/extension default-method serializer ABI mismatch.
- Official OkHttp Brotli and Zstd host runtime.
- Chromium/WebView Cloudflare solve, changed-clearance detection, retry of the original source request.
- Request/source context propagation and current image/request compatibility support.
- External extension runtime/metadata/local APK support and the current Tachiyomi ecosystem classifier/runtime structure present on the branch.

## Historical root causes and device evidence

These entries are retained on purpose. Do not rediscover them as new bugs unless a current device log proves a regression.

1. `UncaughtExceptionInterceptor must be present in default client`
   - Status: `resolved`.
   - Root cause: Mihon extensions expected the default application-interceptor contract and order.
   - Fix: Mihon-compatible default client with required interceptor order.
2. Missing `okhttp3.brotli.BrotliInterceptor`
   - Status: `resolved`.
   - Root cause: dynamically loaded extensions required a host-visible Brotli runtime class.
   - Fix: host Brotli runtime plus modern/legacy compression separation.
3. `GeneratedSerializer.typeParametersSerializers()` `AbstractMethodError`
   - Status: `resolved`.
   - Observed on Comix, MangaDot.net and Manga Ball after Cloudflare itself had succeeded.
   - Root cause: old `minSdk=23` interface-desugaring ABI mismatch across dynamically loaded extension serializers.
   - Fix: `minSdk = 26` and aligned host runtime ABI.
4. Weeb Central obsolete details/chapter path
   - Status: `resolved`.
   - Root cause: host repository path did not use the current combined source API.
   - Fix: `SMangaUpdate`, `Source.getMangaUpdate(...)`, legacy fallback and combined repository path.
5. `SManga.getMemo` `NoSuchMethodError`
   - Status: `resolved`.
   - Root cause: missing modern SManga ABI field/accessor in host contract.
   - Fix: `SManga.memo` support and regression coverage.
6. `Source.getMangaUpdate` `NoSuchMethodError`
   - Status: `resolved`.
   - Root cause: modern Source API contract missing from an earlier host build.
   - Fix: current `Source.getMangaUpdate(...)` ABI plus legacy fallback.
7. Mihon 1.6 MangaDex `0 manga` after legacy fallback succeeded for most cases
   - Status: `resolved` / `superseded` by the combined current repository path and later compatibility work.
   - Root cause family: duplicate/incorrect source continuity across old and modern repository handling.
8. Historical Cloudflare WebView solve on Comix
   - Status: `proven_historical`.
   - Evidence: changed `cf_clearance`, retry of original request, HTTP 200.
   - This proves the retained historical Futon Cloudflare mechanism worked on that older device round, but does not prove parity with Kototoro's newer orchestration.

Historical CI-only regressions retained:

- Source `76f56492ce07af04bc1f003ca7ff33e92b27ab1d`: focused verification failed because centralized compression filtering stopped matching a legacy/fork test interceptor named `BrotliInterceptor`. Status `resolved` by restoring the name-based filter.
- Source `86f6acd280fa79591249cebb9dce7a9649f78edd`: two JVM tests failed from one root cause, construction-time `android.util.Log.d()` calls throw in plain JVM Android stubs. Status `resolved` by removing nonessential construction logs.

## Current unverified Kototoro parity delta

### Cloudflare/captcha orchestration

Status: `open_unverified_parity_delta`, **not a confirmed device root cause**.

Futon's current `CaptchaAutoResolveCoordinator` is an older, simpler port with per-host mutex/cooldown, WebView solve, original-request probe and retry. Current Kototoro at `f4f37a5...` has a richer generic orchestration:

- `CloudFlareSingleFlight` and resolver-state tracking.
- Automatic versus manual strategy selection.
- Recent-success retry window.
- Explicit manual fallback flow.
- Original-request probing.
- Foreground-aware resolver behavior.
- Per-source automatic-captcha opt-out in `SourceSettings`.
- Newer `KotoNetworkHelper` Cloudflare strategy and solve coordination.

Decision for this round: do **not** port this solely because it is newer. There is no current device failure for tested tree `9e5b792...` proving Futon's retained path fails. If current device evidence shows a Cloudflare/captcha/network failure and Kototoro succeeds on the equivalent path, port the relevant current Kototoro orchestration as literally as practical with attribution and regression tests instead of adding another Futon-only workaround.

## Current device evidence status

There is **no recorded real-device test of the current `9e5b792...` tested tree / `85f19b...` app-source state** in the supplied evidence for this round.

The previous `POST_157D_DEVICE_VALIDATION` node is therefore superseded by the newer tested tree. CI, optimized build and DEX evidence are green, but they cannot overrule future device evidence.

## Next decisive validation

`POST_9E5_DEVICE_VALIDATION` is the current project-level runtime validation node.

Install the current signed APK and exercise:

- Comix: browse/popular, search, details, chapters, pages/images.
- MangaDot.net: browse/search/details/chapters/pages/images.
- Manga Ball: browse/search/details/chapters/pages/images.
- Weeb Central: especially details + chapters, then pages/images.
- MangaRead.org: modern SManga/details/chapters/pages path.

If installation fails because another Futon build is installed, remember this test APK uses a temporary signing key and cannot update a differently signed installation.

For any failure, capture logcat around the **first real exception** and group repeated lines by unique root cause. Device evidence has priority over all CI results. For ABI/ClassLoader-family failures, compare `TachiyomiApkClassLoaderPolicy` with current Kototoro first. For Cloudflare/captcha failures, compare the current Kototoro SingleFlight/resolver-state/strategy path before inventing another local workaround.
