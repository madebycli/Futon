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

## Finaler Stand dieser Runde, 2026-08-26

Letzter bedeutender App-/Source-Head:

`157d94e249e2cc06b86b2088f9616802575efa5f`

Danach folgen nur `[skip ci]` Status-/Kontextcommits, bis wieder App-Code geändert wird.

PR #1 wurde zuletzt live als offen, Draft, ungemergt gegen `devel` verifiziert.

### Finales CI

Für `157d94e...`:

- Debug Build: run `32968537762`, success
- Signed Mihon Test Build: run `32968537828`, success
- fokussierte Mihon Regressionen: success
- Release-Lint: success
- optimierter R8 Release-Build: success
- APK-Signaturprüfung: success
- signed artifact id: `9607335889`
- APK SHA-256: `d4de82bd6bb22d1bafb6f3860cf0e9ed6566aa2af0ad2b65a69d813172c038aa`
- Signing: `temporary-test-key`

Wichtig: Der temporäre Test-Key kann keine Installation überschreiben, die mit einem anderen Key signiert wurde. Bei Installationskonflikt kann Deinstallation der alten Testinstallation nötig sein.

Direkte DEX-Prüfung des optimierten APKs bestätigte u. a.:

- `BrotliInterceptor`
- Zstd
- `SMangaUpdate`
- `getMangaUpdate`
- `MihonNetworkHelper`
- `HttpSource`
- `cloudflareClient`
- Legacy-Fetch-Override-Dispatch
- cacheless/progress image call
- `typeParametersSerializers`
- `TachiyomiApkClassLoaderPolicy`

`.ci/mihon-fix-latest.json` ist auf run `32968537828` / source `157d94e...` / success aktualisiert.

## Aktuelle Referenzen

Kototoro:

- Repo: `Kototoro-app/Kototoro`
- Branch: `devel`
- zuletzt live: `dec0ef781644245f6937dc1cafc8ca84963fe08e`
- dieser Head-Commit betrifft Mihon-Fork-Backup-Remapping, nicht die Runtime-/Network-/ClassLoader-Flächen

Wichtige aktuelle Referenzdateien:

- `app/src/main/kotlin/org/skepsun/kototoro/mihon/compat/KotoNetworkHelper.kt`
- `app/src/main/kotlin/eu/kanade/tachiyomi/source/online/HttpSource.kt`
- `app/src/main/kotlin/org/skepsun/kototoro/extensions/runtime/tachiyomi/TachiyomiApkClassLoaderPolicy.kt`

Keiyoushi extensions-lib:

- `keiyoushi/extensions-lib`
- `main`
- zuletzt live `18a8e26be2320b48bdaa11840170479b62989e23`

## Was im aktuellen Futon bereits implementiert ist

### NetworkHelper

- `UncaughtExceptionInterceptor` zuerst
- `UserAgentInterceptor` zweitens
- `CloudflareInterceptor` drittens
- Mihon-Client wird aus `baseClient.newBuilder()` abgeleitet, dadurch bleiben Proxy/TLS/DNS/cache/authenticator/dispatcher/timeouts usw. erhalten
- nur Interceptor-Listen werden neu aufgebaut
- inkompatible Legacy-Compression-Interceptors werden entfernt
- kompatible Host-Interceptors bleiben erhalten
- konkrete und namensbasierte Brotli-Filterung für Fork-/Legacy-Kompatibilität
- moderner Default-Client ohne Brotli-Network-Interceptor
- Legacy-`cloudflareClient` separat mit Brotli
- Zstd statisch im Host-Graph verankert

### HttpSource

- moderner suspend-Pfad direkt über OkHttp
- Legacy-Rx-`fetch*` Overrides werden per Reflection erkannt und bei Bedarf bevorzugt
- `UnsupportedOperationException` Fallback für Custom-Legacy-Fetch
- `getHomeUrl() = baseUrl`
- Legacy-HttpSource bekommt Brotli über `cloudflareClient`
- Image-Requests mit Source-Tag, cacheless/progress Call und HTTP-Success-Check
- Regressionen in `MihonModernHostContractTest`

### ClassLoader

Der alte Handoff-Hinweis „ClassLoader-Port deferred“ ist **veraltet und superseded**.

Aktuell ist Kototoros generische ABI-Policy bereits portiert:

- Futon `ChildFirstPathClassLoader` nutzt `DexClassLoader`
- Host-/Parent-owned: Java/Kotlin/Android, Coroutines, JSON/Jsoup, OkHttp/Okio, Rx, Tachiyomi source/network/util ABI, Injekt, IReader, Ktor, Fleeksoft
- `$-CC` und `$DefaultImpls` bleiben child-first
- Regressionstest: `TachiyomiApkClassLoaderPolicyTest`

Bei neuen `AbstractMethodError`, `NoSuchMethodError`, `IncompatibleClassChangeError`, `ClassCastException`, `VerifyError` oder ClassLoader-`ClassNotFoundException` zuerst prüfen, ob die **bereits portierte** Policy vom aktuellen Kototoro abweicht. Keine neuen Ad-hoc-Ausnahmen hinzufügen, solange die generische Policy den Fall erklären kann.

### Bereits vorher erledigt und weiterhin relevant

- Brotli/Zstd Host-Runtime
- Cloudflare WebView/Chromium Solve, Clearance-Änderung, Retry Originalrequest
- `SManga.memo`
- `SMangaUpdate`
- `Source.getMangaUpdate(...)`
- Legacy Details/Chapter Fallback
- kombinierter `MihonMangaRepository` 1.6 Update-Pfad
- `minSdk = 26`
- Request-/Source-Context
- externe APK/Metadata/Tachiyomi-Ecosystem-Runtime-Flächen

## Historische Zwischenfehler der letzten Port-Runde

Nicht als neue Runtime-Bugs zählen:

1. `76f56492...`: Regressionstest fand, dass namensbasierter Legacy-`BrotliInterceptor` Filter beim Refactor verloren ging. Behoben.
2. `86f6acd...`: Zwei JVM-Tests scheiterten aus **einer** Root Cause, `android.util.Log.d()` während Helper-Konstruktion trifft Plain-JVM-Android-Stubs und wirft `RuntimeException`. Nicht notwendige Konstruktionslogs entfernt.
3. `157d94e...`: final grün.

## Höchste Priorität: echter Gerätetest des finalen APKs

Es gibt noch keinen aufgezeichneten realen Device-Test des finalen `157d94e...` APKs. CI/DEX ist grün, aber Device-Evidence hat Vorrang.

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
2. wiederholte rote Zeilen nach Root Cause zusammenfassen
3. Futon-Pfad bestimmen
4. aktuellen Kototoro-Pfad am live SHA bestimmen
5. aktuellen Keiyoushi/Mihon-Vertrag prüfen
6. Kototoro-Lösung bevorzugt code-nah portieren, falls sie den Fall generisch löst
7. Regressionstest hinzufügen
8. fokussierte Tests + Debug APK + Signed APK validieren
9. bei Runtime/ClassLoader-Problemen APK/DEX prüfen
10. `.ai/context/STATE.md` und `.ai/context/graph.yaml` aktualisieren
11. dem Benutzer wieder eine direkt installierbare APK geben

Aktueller offener Runtime-Knoten: `POST_157D_DEVICE_VALIDATION`.

## Evidenzpriorität

1. neuestes reales Gerätelog / reproduzierbares Device-Verhalten
2. aktueller Kototoro-/Keiyoushi-/Mihon-Code am exakten SHA
3. Regressionstests + vollständige APK Builds + DEX-Prüfung
4. Dokumentation
5. Annahmen

Ein grüner Test darf niemals eine reale Device-Exception „wegbeweisen“.
