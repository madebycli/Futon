# Current Mihon/Keiyoushi Compatibility State

Last manually refreshed: 2026-08-26

## Repository state

- Repository: `madebycli/Futon`
- Working branch: `fix/mihon-uncaught-exception-interceptor`
- Base branch: `devel`
- Draft PR: #1, `Fix Keiyoushi/Mihon default network interceptor compatibility`
- Live-observed branch head before the final context refresh: `3d7beb7500836e0495e48a658612a7f4ee0d21d1` (CI-status-only commit)
- Last meaningful app/source head: `157d94e249e2cc06b86b2088f9616802575efa5f`
- PR #1 is open, `draft: true`, unmerged, base `devel`.
- App test version: `9.8.1-mihon-fix-test` (`90802`)
- Host baseline: `minSdk = 26`.

Always re-fetch live values before new work. Context/status commits use `[skip ci]` and may advance the branch while `157d94e...` remains the meaningful app source until another app-code change is made.

## Final CI / APK validation for source 157d94e

Source SHA: `157d94e249e2cc06b86b2088f9616802575efa5f`

- Debug workflow: run `32968537762`, `success`.
- Debug artifact: `debug-apk`, artifact id `9606868918`.
- Dedicated Mihon workflow: run `32968537828`, `success`.
- Focused Mihon regression tests: `success`.
- Release lint diagnostic: `success`.
- Optimized R8 release build: `success`.
- APK signature verification (`apksigner verify --verbose --print-certs`): `success`.
- Signed artifact upload: `success`.
- Signed artifact: `Futon-Mihon-Fix-Signed-Release`, artifact id `9607335889`.
- Signed APK SHA-256: `d4de82bd6bb22d1bafb6f3860cf0e9ed6566aa2af0ad2b65a69d813172c038aa`.
- Artifact ZIP SHA-256: `0e7b509ca3bf04d62ffa274e0d47b3427f7f1478c6110a4994184cf25fcfc7fa`.
- Signing kind reported by the workflow: `temporary-test-key`. This APK cannot update an installation signed by a different key; uninstall/reinstall may be required.
- `BUILD-INFO.txt` records synthetic PR merge SHA `3890a58a607ed0c2fde692e2e3d65cca85e7e92a` because the workflow ran on `pull_request`; the Actions artifact metadata correctly identifies branch source head `157d94e...`.

Local extraction of the final optimized APK confirmed its recorded SHA-256 exactly. Direct DEX inspection of the optimized APK confirmed the packaged presence of all checked runtime/ABI symbols:

- `okhttp3/brotli/BrotliInterceptor`
- `okhttp3/zstd/Zstd`
- `eu/kanade/tachiyomi/source/model/SMangaUpdate`
- `getMangaUpdate`
- `io/github/landwarderer/futon/mihon/compat/MihonNetworkHelper`
- `eu/kanade/tachiyomi/source/online/HttpSource`
- `cloudflareClient`
- `overridesFetchWithoutRequestHelper`
- `newCachelessCallWithProgress`
- `typeParametersSerializers`
- `TachiyomiApkClassLoaderPolicy`

`.ci/mihon-fix-latest.json` now records run `32968537828`, source `157d94e...`, verification `success`, signed release `success`.

## Current upstream references

### Kototoro

- Repository: `Kototoro-app/Kototoro`
- Branch: `devel`
- Live verified SHA: `dec0ef781644245f6937dc1cafc8ca84963fe08e`
- The live-head commit is about Mihon-family backup source remapping, not extension runtime/network/classloader behavior.
- Current `KotoNetworkHelper.kt` blob remains `fc178e26d9d54fd9e2c4f5ee4ca4dbd529e00c88` at this SHA.
- Current `TachiyomiApkClassLoaderPolicy.kt` still uses explicit host-owned ABI packages plus child-first `$-CC` / `$DefaultImpls` bridge exceptions.
- Current `HttpSource.kt` continues the modern suspend API plus legacy `fetch*` override compatibility pattern used as Futon's reference.

### Keiyoushi extensions-lib

- Repository: `keiyoushi/extensions-lib`
- Branch: `main`
- Live verified SHA: `18a8e26be2320b48bdaa11840170479b62989e23`
- This remains the current host-contract reference observed in this round.

## Compatibility work implemented

### Default NetworkHelper / compression contract

- Required default application-interceptor order remains:
  1. `UncaughtExceptionInterceptor`
  2. `UserAgentInterceptor`
  3. `CloudflareInterceptor`
- Futon now derives the Mihon client from `baseClient.newBuilder()`, preserving host proxy/TLS/DNS/cache/authenticator/dispatcher/connection/timeouts and rebuilding only interceptor lists.
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
- `MihonModernHostContractTest` protects host-client configuration preservation, modern-vs-legacy Brotli separation, home URL, page-list legacy fetch dispatch, and chapter-list legacy override precedence.

### ClassLoader / ABI ownership

The old context entry saying this port was deferred is now **superseded**. The port is already implemented on the current branch.

- `ChildFirstPathClassLoader` now extends `DexClassLoader`.
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

## CI regressions encountered while finishing the modern host port

These are historical and resolved, retained so they are not rediscovered as new runtime bugs:

1. Source `76f56492ce07af04bc1f003ca7ff33e92b27ab1d` failed focused verification because the centralized compression filter stopped matching a legacy/fork test interceptor whose class name was `BrotliInterceptor`. The name-based compatibility filter was restored.
2. Source `86f6acd280fa79591249cebb9dce7a9649f78edd` compiled, but two JVM tests failed with the same root cause: `android.util.Log.d()` was called while constructing/filtering the helper, and plain JVM Android stubs throw `RuntimeException`. The nonessential construction-time skip logs were removed.
3. Final source `157d94e...` keeps the filtering behavior without those JVM-hostile construction logs and is green in both Debug and Signed workflows.

Do not interpret either red intermediate run as a current app/runtime failure.

## Historical on-device evidence

Status: historical, before the current final APK.

- Five installed Mihon extensions loaded successfully, with dozens of sources.
- The original `UncaughtExceptionInterceptor must be present in default client` failure was fixed.
- Missing `okhttp3.brotli.BrotliInterceptor` was observed and fixed.
- Comix Cloudflare WebView solve was proven on-device: changed `cf_clearance`, retry of original request, HTTP 200.
- Comix, MangaDot.net and Manga Ball then exposed `GeneratedSerializer.typeParametersSerializers()` `AbstractMethodError`; the root cause was the old `minSdk=23`/interface-desugaring ABI and Futon moved to API 26.
- Weeb Central exposed the obsolete details/chapter path; Futon added the combined `getMangaUpdate(...)` path.

There is still **no recorded real-device test of the final `157d94e...` APK**. CI and DEX evidence are strong but cannot overrule future device evidence.

## Next decisive validation

`POST_157D_DEVICE_VALIDATION` is the only remaining project-level runtime validation node.

Install the final signed APK and exercise:

- Comix: browse/popular, search, details, chapters, pages/images.
- MangaDot.net: browse/search/details/chapters/pages/images.
- Manga Ball: browse/search/details/chapters/pages/images.
- Weeb Central: especially details + chapters, then pages/images.
- MangaRead.org: the modern SManga/details/chapters/pages path.

If installation fails because another Futon build is installed, remember this final test APK uses a temporary signing key and cannot update a differently signed installation.

For any failure, capture logcat around the **first real exception** and group repeated lines by unique root cause. Device evidence has priority over all CI results.
