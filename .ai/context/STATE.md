# Current Mihon/Keiyoushi Compatibility State

Last manually refreshed: 2026-08-26

## Repository state

- Repository: `madebycli/Futon`
- Working branch: `fix/mihon-uncaught-exception-interceptor`
- Base branch: `devel`
- Draft PR: #1 — `Fix Keiyoushi/Mihon default network interceptor compatibility`
- Live-observed branch head before this context refresh: `6d265a6d47fd08da332234335b559b3fc0c9aae1`
- Last meaningful source-change head: `d5e10a1d6b7dd69b45c4e7d953fa2f14f3e7ec32`
- `6d265a6...` is context-only and descends from the prior CI-status/context commits; the meaningful app source remains `d5e10a1...`.
- App version under test: Futon `9.8.1`
- Debug application id: `io.github.landwarderer.futon.debug`
- Host baseline now matches current Mihon/Keiyoushi: `minSdk = 26`.

Always re-fetch these values before work because context-refresh commits advance the branch head.

## Live verification, 2026-08-26

- PR #1 is still open and `draft: true`, base `devel`, head branch `fix/mihon-uncaught-exception-interceptor`.
- Futon live head observed before this context refresh: `6d265a6d47fd08da332234335b559b3fc0c9aae1`.
- Current Kototoro `devel` head remains `e036c5940af6b849c055ab46d73c0ec4896276f7` (v2.0.3).
- No post-`d5e10a1` device result has been supplied yet, so `POST_D5_DEVICE_VALIDATION` remains the highest-priority unresolved node.
- Current Kototoro Mihon `ChildFirstPathClassLoader` delegates parent-owned ABI classes through `ChildFirstClassLoaderPolicy`, which is now only a legacy alias for the shared `TachiyomiApkClassLoaderPolicy`.
- The shared Kototoro policy explicitly parent-loads Java/Kotlin/Android, coroutines, OkHttp/Okio, Rx, Tachiyomi source/network/util APIs, Injekt and related host ABI namespaces, while `$-CC` and `$DefaultImpls` bridge artifacts remain child-first.
- Futon's current `ChildFirstPathClassLoader` still uses generic system -> child -> parent loading and does not encode this ABI ownership policy.
- Decision for this round: do not port the policy without fresh post-`d5e10a1` device evidence. If the next log contains `AbstractMethodError`, `NoSuchMethodError`, `IncompatibleClassChangeError`, `ClassCastException`, `VerifyError`, duplicate host/extension ABI classes, or classloader-specific `ClassNotFoundException`, port Kototoro's shared policy and Mihon loader behavior preferentially instead of adding one-off exceptions.

## Latest CI state

`.ci/mihon-fix-latest.json` records:

- tested source SHA: `d5e10a1d6b7dd69b45c4e7d953fa2f14f3e7ec32`
- dedicated run: `32948732345`
- focused Mihon verification: `success`
- signed optimized release test build: `success`

The full debug build for the same source head succeeded in workflow run `32948738540`. Live Actions history still shows successful Debug Build and Mihon signed-test runs for `d5e10a1...`; context-only commits intentionally use `[skip ci]`.

## Historical device evidence before d5e10a1

Status: `historical`, retained for root-cause history.

1. Extensions could be discovered and instantiated: five installed Mihon extensions loaded successfully, with dozens of sources available.
2. The original Keiyoushi default-client failure (`UncaughtExceptionInterceptor must be present in default client`) was fixed.
3. A second failure exposed a missing host runtime class: `okhttp3.brotli.BrotliInterceptor`. Futon now packages the official OkHttp Brotli module.
4. Zstd host runtime support was also aligned with the modern Mihon/Keiyoushi environment.
5. The Usagi/Kototoro-style Cloudflare WebView solve was verified on-device: a changed `cf_clearance` was obtained, the original Comix request was retried, and HTTP 200 was returned. The current Cloudflare solve path is therefore device-proven.
6. After Cloudflare succeeded, Comix, MangaDot.net, and Manga Ball exposed repeated `GeneratedSerializer.typeParametersSerializers()` `AbstractMethodError` failures.
7. Root cause was Futon's old `minSdk = 23`, which allowed Android interface desugaring to create a host ABI incompatible with dynamically loaded serializer implementations. Current Mihon and Keiyoushi use API 26. Futon was raised to `minSdk = 26` and a regression gate was added.
8. Weeb Central exposed `UnsupportedOperationException` because current Keiyoushi sources use the extensions-lib/TachiyomiX 1.6 combined `getMangaUpdate(...)` path while Futon still called legacy details/chapter paths. Futon now exposes `SMangaUpdate` and `Source.getMangaUpdate(...)`, with legacy fallback, and `MihonMangaRepository` uses the combined API.
9. `SManga.memo` compatibility was added for newer extension ABI expectations.

No post-`d5e10a1` on-device result is recorded yet. A green unit/CI result must not override a future real device failure.

## Compatibility work already implemented

### Network host contract

- Mihon-compatible `UncaughtExceptionInterceptor`.
- Mihon-compatible `UserAgentInterceptor`.
- Mihon-compatible `CloudflareInterceptor` delegating to Futon's host behavior.
- Required default-interceptor ordering preserved.
- Legacy/incompatible compression network interceptors filtered.
- Unrelated compatible host interceptors preserved.
- Official OkHttp Brotli runtime added to the host.
- OkHttp Zstd runtime aligned.

### Cloudflare

- Chromium/WebView owns challenge networking rather than proxying WebView subrequests through a second OkHttp path.
- Host waits for changed clearance state and retries the original source request.
- This path is proven on the user's device for Comix with HTTP 200 after solve.

### Source ABI

- `SManga.memo` compatibility.
- `SMangaUpdate` host model.
- `Source.getMangaUpdate(manga, chapters, fetchDetails, fetchChapters)` API.
- Default fallback keeps older extensions working through old details/chapter methods.
- `MihonMangaRepository` requests combined details + chapter updates for modern sources.

### Android ABI baseline

- `minSdk` raised from 23 to 26 to match modern Mihon/Keiyoushi dynamic-extension ABI assumptions.
- Regression coverage protects the API-26 baseline.

## Current Kototoro reference

- Repository: `Kototoro-app/Kototoro`
- Reference branch: `devel`
- Live verified SHA: `e036c5940af6b849c055ab46d73c0ec4896276f7`
- Version: v2.0.3
- License: Apache-2.0

Important reference paths:

- `docs/reference/mihon-integration.md`
- `app/src/main/kotlin/org/skepsun/kototoro/mihon/compat/KotoNetworkHelper.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/mihon/util/ChildFirstPathClassLoader.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/mihon/util/ChildFirstClassLoaderPolicy.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/extensions/runtime/tachiyomi/TachiyomiApkClassLoaderPolicy.kt`
- `app/src/main/kotlin/eu/kanade/tachiyomi/source/Source.kt`
- Kototoro Mihon loader/manager/repository/filter/converter files listed by its integration document.

## Next device test matrix

Use the newest APK built from or after `d5e10a1` and exercise at least:

- Comix: browse/popular, search, manga details, chapters, pages/images.
- MangaDot.net: browse/search/details/chapters/pages.
- Manga Ball: browse/search/details/chapters/pages.
- Weeb Central: especially manga details + chapter loading, then pages.
- MangaRead.org: repeat the path that previously exercised modern SManga behavior.

For every failure, capture full logcat around the first exception and classify by unique root cause rather than counting all warning/error lines.

## Unresolved / next-action node

`POST_D5_DEVICE_VALIDATION` is unresolved. Do not declare the project fixed until a real post-`d5e10a1` device run confirms the critical source matrix or identifies the next host incompatibility.
