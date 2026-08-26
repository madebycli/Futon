# Current Mihon/Keiyoushi Compatibility State

Last manually refreshed: 2026-08-26

## Repository state

- Repository: `madebycli/Futon`
- Working branch: `fix/mihon-uncaught-exception-interceptor`
- Base branch: `devel`
- Draft PR: #1 — `Fix Keiyoushi/Mihon default network interceptor compatibility`
- Current branch head at this snapshot: `18231440e71c007cfe32e95373d8722658f08882`
- Last meaningful source-change head: `d5e10a1d6b7dd69b45c4e7d953fa2f14f3e7ec32`
- `18231440...` is a GitHub Actions status-record commit whose parent is `d5e10a1...`.
- App version under test: Futon `9.8.1`
- Debug application id: `io.github.landwarderer.futon.debug`
- Host baseline now matches current Mihon/Keiyoushi: `minSdk = 26`.

Always re-fetch these values before work because the branch may have advanced.

## Latest CI state

`.ci/mihon-fix-latest.json` currently records:

- tested source SHA: `d5e10a1d6b7dd69b45c4e7d953fa2f14f3e7ec32`
- dedicated run: `32948732345`
- focused Mihon verification: `success`
- signed optimized release test build: `success`

The full debug build for the same source head also succeeded in workflow run `32948738540`.

## Latest device evidence received before d5e10a1

The user's device logs established the following sequence:

1. Extensions could be discovered and instantiated: five installed Mihon extensions loaded successfully, with dozens of sources available.
2. The original Keiyoushi default-client failure (`UncaughtExceptionInterceptor must be present in default client`) was fixed.
3. A second failure exposed a missing host runtime class: `okhttp3.brotli.BrotliInterceptor`. Futon now packages the official OkHttp Brotli module.
4. Zstd host runtime support was also aligned with the modern Mihon/Keiyoushi environment.
5. The Usagi/Kototoro-style Cloudflare WebView solve was verified on-device: a changed `cf_clearance` was obtained, the original Comix request was retried, and HTTP 200 was returned. This means the current Cloudflare solve path is proven on the user's device.
6. After Cloudflare succeeded, Comix, MangaDot.net, and Manga Ball exposed a repeated `GeneratedSerializer.typeParametersSerializers()` `AbstractMethodError`.
7. Root cause: Futon still used `minSdk = 23`, allowing Android interface desugaring to produce a host ABI incompatible with dynamically loaded serializer implementations. Current Mihon and Keiyoushi both use API 26. Futon was raised to `minSdk = 26` and a regression gate was added.
8. Weeb Central exposed `UnsupportedOperationException` because current Keiyoushi sources use the extensions-lib/TachiyomiX 1.6 combined `getMangaUpdate(...)` path while Futon still called legacy details/chapter paths. Futon now exposes `SMangaUpdate` and `Source.getMangaUpdate(...)`, with a legacy fallback, and `MihonMangaRepository` uses the combined API.
9. `SManga.memo` compatibility was added for newer extension ABI expectations.

No post-`d5e10a1` on-device result is recorded yet in this context. The next device log must be treated as the new truth and this section updated immediately.

## Compatibility work already implemented

### Network host contract

- Mihon-compatible `UncaughtExceptionInterceptor`.
- Mihon-compatible `UserAgentInterceptor`.
- Mihon-compatible `CloudflareInterceptor` delegating to Futon's host behavior.
- Required default-interceptor ordering preserved.
- Legacy/incompatible compression network interceptors filtered (`IgnoreGzipInterceptor`, Brotli network interceptor where forbidden by KeiSource).
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

### CI

- Focused Mihon compatibility test workflow.
- Full Debug APK build.
- Diagnostic release lint does not block the focused compatibility artifact because unrelated existing lint debt is out of scope.
- Optimized signed test APK build and signature verification.
- Status JSON records the exact source SHA tested.

## Current Kototoro reference

- Repository: `Kototoro-app/Kototoro`
- Reference branch: `devel`
- Current reference SHA at this snapshot: `e036c5940af6b849c055ab46d73c0ec4896276f7`
- Version at that head: v2.0.3
- License: Apache-2.0

Important reference paths:

- `docs/reference/mihon-integration.md`
- `app/src/main/kotlin/org/skepsun/kototoro/mihon/compat/KotoNetworkHelper.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/mihon/util/ChildFirstPathClassLoader.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/extensions/runtime/tachiyomi/TachiyomiApkClassLoaderPolicy.kt`
- `app/src/main/kotlin/eu/kanade/tachiyomi/source/Source.kt`
- Kototoro Mihon loader/manager/repository/filter/converter files listed by its integration document.

## Important comparison discovered now

Kototoro's class-loader design is stronger than Futon's current generic child-first loader for ABI ownership:

- Kototoro explicitly delegates host-owned runtime/API namespaces to the parent (`kotlinx.coroutines`, Android, OkHttp, Okio, Rx, `eu.kanade.tachiyomi.source.*`, network/util APIs, Injekt, etc.).
- It intentionally allows generated `$-CC` and `$DefaultImpls` bridge classes to stay child-first because those bridge artifacts may travel inside extension APKs.
- Futon's current `ChildFirstPathClassLoader` is more generic and does not encode the same explicit Tachiyomi ABI ownership policy.

Therefore, if new device results show further `AbstractMethodError`, `NoSuchMethodError`, `IncompatibleClassChangeError`, `ClassCastException`, duplicate API-class identity problems, or class-loader-specific failures, compare and strongly consider porting Kototoro's class-loader policy word-for-word before creating new ad-hoc exceptions.

## Next device test matrix

Use the newest APK built from or after `d5e10a1` and exercise at least:

- Comix: browse/popular, search, manga details, chapters, pages/images.
- MangaDot.net: browse/search/details/chapters/pages.
- Manga Ball: browse/search/details/chapters/pages.
- Weeb Central: especially manga details + chapter loading, then pages.
- MangaRead.org: repeat the path that previously exercised modern SManga behavior.

For every failure, capture full logcat around the first exception and classify by unique root cause rather than counting all warning/error lines.

## Unresolved / next-action node

`POST_D5_DEVICE_VALIDATION` is unresolved. The next AI must not declare the project fixed until a real post-`d5e10a1` device run confirms the critical source matrix or identifies the next host incompatibility.
