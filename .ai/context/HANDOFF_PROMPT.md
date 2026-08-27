# Handoff Prompt for the Next AI

Du bist die nächste primäre Entwicklungs-KI für das Futon-Mihon/Keiyoushi-Kompatibilitätsprojekt.

## Projekt und Sicherheitsregeln

- Futon-Fork: `madebycli/Futon`
- Arbeitsbranch: `fix/mihon-uncaught-exception-interceptor`
- Draft-PR: #1 gegen `devel`
- Niemals mergen oder `devel` direkt verändern, außer der Benutzer verlangt es ausdrücklich.
- Keine Releases veröffentlichen, außer der Benutzer verlangt es ausdrücklich.
- Keine Signing-Secrets, Keys, Passwörter oder Tokens ausgeben.

## Zwingender Start jeder Runde

Lies zuerst vollständig:

1. `.ai/context/README.md`
2. `.ai/context/STATE.md`
3. `.ai/context/graph.yaml`
4. `.ci/mihon-fix-latest.json`
5. diese Datei

Danach live neu holen:

- Branch-Head von `fix/mihon-uncaught-exception-interceptor`
- PR #1 Status/Head/Base
- relevante GitHub-Actions-Runs
- aktuellen `devel`-Head von `Kototoro-app/Kototoro`
- bei Vertragsfragen aktuellen `keiyoushi/extensions-lib` Head

Gespeicherte SHAs niemals ungeprüft als aktuell annehmen.

## Verifizierter Stand, 2026-08-27

Tested PR head vor den nachfolgenden `[skip ci]` Kontextcommits:

`9e5b7922bd2c71fbd8e3ac8c1dbe9eddf707660f`

Letzter App-/Source-Code-Commit in diesem getesteten Tree:

`85f19b491f2c4837e95c99e828fdb28f32d960c0`

PR #1 wurde live als offen, Draft, ungemergt gegen `devel` verifiziert. Base SHA war `05f11b2e6d46993677eec4e7eb66fde2c76e5a4b`.

Der `pull_request` Workflow baute synthetischen Merge `fd2effcb2a90f2eae4047498f6734cebb9563682`. Dessen Tree `9324f0bf91078df59a7ccd922082b69806c37c10` ist identisch mit dem Tree des getesteten PR-Heads `9e5b792...`.

### Aktuelles CI

Für tested head `9e5b792...`:

- Debug Build: run `33012858726`, success
- Debug artifact id: `9623782588`
- Signed Mihon Test Build: run `33012858721`, success
- fokussierte Mihon Regressionen: success
- Release-Lint: success
- optimierter R8 Release-Build: success
- APK-Signaturprüfung: success
- signed artifact id: `9623914916`
- signed APK SHA-256: `bcbe14a51f5394abc00ac0b2686cc05e2171f57e74d649c58f2e9f2bf9966318`
- artifact ZIP SHA-256: `fb85ca060bc7cd7c94c2a65fed7601ade155c109dc5b7482baba218b89876438`
- Signing: `temporary-test-key`
- App-Version: `9.8.1-mihon-fix-test`, versionCode `90803`

Wichtig: Der temporäre Test-Key kann keine Installation überschreiben, die mit einem anderen Key signiert wurde. Bei Installationskonflikt kann eine Deinstallation der alten Testinstallation nötig sein.

Direkte DEX-Prüfung des optimierten APKs bestätigte den aktuellen Workflow-Gate, unter anderem:

- Zstd Runtime
- `PreferenceScreen`
- `ConfigurableSource`
- `Source`
- `RefreshContext`
- `HttpSource`
- `SManga`
- `SChapter`
- `Page`
- `getMangaUpdate`
- `fetchRelatedMangaList`
- `getChapterList`
- `getPageList`
- `fetchPageList`
- `CaptchaAutoResolveCoordinator`
- `TachiyomiApkClassLoaderPolicy`
- Brotli Runtime

`.ci/mihon-fix-latest.json` zeigt auf run `33012858721` / source `9e5b792...` / success.

## Aktuelle Referenzen

Kototoro:

- Repo: `Kototoro-app/Kototoro`
- Branch: `devel`
- zuletzt live verifiziert: `f4f37a5b7290da05c10b9325912f2a37ebeff0f9`
- vorheriger Kontext-SHA: `dec0ef781644245f6937dc1cafc8ca84963fe08e`
- zwischen `dec0ef...` und `f4f37...` liegen 9 Commits, aber keine Änderungen an den hier verwendeten Mihon/Tachiyomi-Runtime-, Source-ABI-, NetworkHelper-, ClassLoader- oder Extension-Runtime-Referenzdateien

Wichtige aktuelle Referenzdateien:

- `app/src/main/kotlin/eu/kanade/tachiyomi/source/Source.kt`
- `app/src/main/kotlin/eu/kanade/tachiyomi/source/online/HttpSource.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/mihon/compat/KotoNetworkHelper.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/extensions/runtime/tachiyomi/TachiyomiApkClassLoaderPolicy.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/core/exceptions/resolve/CaptchaHandler.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/core/exceptions/resolve/CaptchaAutoResolveCoordinator.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/core/exceptions/resolve/CloudFlareSingleFlight.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/core/network/webview/WebViewExecutor.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/core/prefs/SourceSettings.kt`

Keiyoushi extensions-lib:

- `keiyoushi/extensions-lib`
- `main`
- zuletzt live `18a8e26be2320b48bdaa11840170479b62989e23`

## Was im aktuellen Futon bereits implementiert ist

### NetworkHelper / Compression

- `UncaughtExceptionInterceptor` zuerst
- `UserAgentInterceptor` zweitens
- `CloudflareInterceptor` drittens
- Mihon-Client aus `baseClient.newBuilder()`, dadurch bleiben Proxy/TLS/DNS/cache/authenticator/dispatcher/timeouts erhalten
- nur Interceptor-Listen werden neu aufgebaut
- inkompatible Legacy-Compression-Interceptors werden entfernt
- kompatible Host-Interceptors bleiben erhalten
- konkrete und namensbasierte Brotli-Filterung
- moderner Default-Client ohne Brotli-Network-Interceptor
- Legacy-`cloudflareClient` separat mit Brotli
- Zstd statisch im Host-Graph verankert

### HttpSource / Source ABI

- moderner suspend-Pfad direkt über OkHttp
- Legacy-Rx-`fetch*` Overrides werden per Reflection erkannt und bei Bedarf bevorzugt
- `UnsupportedOperationException` Fallback für Custom-Legacy-Fetch
- `getHomeUrl() = baseUrl`
- Legacy-HttpSource bekommt Brotli über `cloudflareClient`
- Image-Requests mit Source-Tag, cacheless/progress Call und HTTP-Success-Check
- `RefreshContext`
- modernes Request-ABI und `SourceRequestContext` Tagging
- `SManga.memo`
- `SMangaUpdate`
- `Source.getMangaUpdate(...)`
- Legacy Details/Chapter Fallback
- kombinierter `MihonMangaRepository` 1.6 Update-Pfad

### Post-157d bereits hinzugekommen

Der alte Kontext, der `157d94e...` als letzten Source-Stand behandelte, war veraltet. Im getesteten `9e5b792...` Tree sind unter anderem bereits enthalten:

- `e617a5d...`: HttpSource Request ABI + `RefreshContext` an Kototoro angeglichen
- `fd81be4...`: moderner `genres` Mapper
- `d5c344c...`: deklarierte protobuf extension-lib Version erhalten
- `87bbda4...`: deklarierte Source-Browser-Origin erhalten
- `4f254b2...`: gemeinsamer Mihon Source-Preference-Namespace `source_<mihonId>`
- `00a488a...`: einzelne Extension-Repo-Fehler isolieren, nach Kototoro-Muster
- `85f19b4...`: Extension-Repo-Fallback-Parsing an aktuelle Versionsformen anpassen
- spätere Commits bis `9e5b792...`: zusätzliche Host-ABI- und optimierte APK-Regressionen

### ClassLoader

Kototoros generische ABI-Policy ist bereits portiert:

- Futon `ChildFirstPathClassLoader` nutzt `DexClassLoader`
- Host-/Parent-owned: Java/Kotlin/Android, Coroutines, JSON/Jsoup, OkHttp/Okio, Rx, Tachiyomi source/network/util ABI, Injekt, IReader, Ktor, Fleeksoft
- `$-CC` und `$DefaultImpls` bleiben child-first
- Regressionstest: `TachiyomiApkClassLoaderPolicyTest`

Bei neuen `AbstractMethodError`, `NoSuchMethodError`, `IncompatibleClassChangeError`, `ClassCastException`, `VerifyError` oder ClassLoader-`ClassNotFoundException` zuerst prüfen, ob die bereits portierte Futon-Policy vom aktuellen Kototoro abweicht. Keine neuen Ad-hoc-Ausnahmen hinzufügen, solange die generische Policy den Fall erklären kann.

## Historische Root Causes, nicht als neue Bugs zählen

Alle Details stehen in `STATE.md` und `graph.yaml`. Wesentliche Familien:

- fehlender `UncaughtExceptionInterceptor` im Default-Client, resolved
- fehlender host-sichtbarer Brotli Runtime-Typ, resolved
- `GeneratedSerializer.typeParametersSerializers()` `AbstractMethodError` durch alte minSdk/interface-desugaring ABI, resolved durch `minSdk = 26`
- obsolete Details/Chapter Repository-Route, resolved
- `SManga.getMemo` `NoSuchMethodError`, resolved
- `Source.getMangaUpdate` `NoSuchMethodError`, resolved
- Mihon 1.6 MangaDex `0 manga` Continuity-Fall, resolved/superseded durch aktuellen kombinierten Repository-Pfad
- historischer Comix Cloudflare-Solve mit geändertem `cf_clearance` und HTTP 200 nach Retry, proven historical

## Offene, aber noch unbewiesene Kototoro-Paritätsdifferenz

Aktuelles Kototoro hat eine neuere Cloudflare/Captcha-Orchestrierung als Futon:

- `CloudFlareSingleFlight`
- Resolver-State
- Auto/Manual Strategy
- Recent-Success Retry Window
- expliziter manueller Fallback
- Originalrequest Probe
- Foreground-aware Resolver
- per-Source Auto-Captcha Opt-out
- neueres `KotoNetworkHelper` Cloudflare Strategy/Solve Coordination

Futon hat eine ältere, einfachere Portierung mit per-Host Mutex/Cooldown, WebView Solve, Originalrequest Probe und Retry.

Wichtig: Das ist aktuell **kein bestätigter Root Cause**. Ohne aktuellen Gerätetest des `9e5b792...` Trees nicht allein wegen neuerer Kototoro-Architektur portieren. Wenn ein aktuelles Gerätelog genau diesen Pfad als Fehler zeigt und Kototoro ihn funktionierend löst, dann die relevante aktuelle Kototoro-Orchestrierung code-nah mit Attribution und Tests portieren statt einen weiteren Futon-Workaround zu erfinden.

## Höchste Priorität: echter Gerätetest des aktuellen APKs

Es gibt im derzeitigen Kontext noch keinen aufgezeichneten realen Device-Test des getesteten `9e5b792...` Trees / `85f19b...` App-Source-Stands. CI/DEX ist grün, aber Device-Evidence hat Vorrang.

Aktueller offener Runtime-Knoten: `POST_9E5_DEVICE_VALIDATION`.

Teste mindestens:

- Comix
- MangaDot.net
- Manga Ball
- Weeb Central
- MangaRead.org

jeweils soweit möglich:

- Browse/Popular
- Search
- Details
- Chapters
- Pages/Images

Für jeden Fehler:

1. erste echte Exception im relevanten Source-Pfad isolieren
2. wiederholte rote Zeilen nach einer eindeutigen Root Cause zusammenfassen
3. Futon-Pfad bestimmen
4. aktuellen Kototoro-Pfad am live SHA bestimmen
5. aktuellen Keiyoushi/Mihon-Vertrag prüfen
6. Kototoro-Lösung bevorzugt code-nah portieren, falls sie den Fall generisch löst
7. Regressionstest hinzufügen
8. fokussierte Tests + Debug APK + Signed APK validieren
9. bei Runtime/ClassLoader-Problemen APK/DEX prüfen
10. `.ai/context/STATE.md` und `.ai/context/graph.yaml` aktualisieren
11. dem Benutzer wieder eine direkt installierbare APK geben

## Evidenzpriorität

1. neuestes reales Gerätelog / reproduzierbares Device-Verhalten
2. aktueller Kototoro-/Keiyoushi-/Mihon-Code am exakten SHA
3. Regressionstests + vollständige APK Builds + DEX-Prüfung
4. Dokumentation
5. Annahmen

Ein grüner Test darf niemals eine reale Device-Exception wegbeweisen.
