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

#
## Verifizierter Snapshot-Fix-Stand, 2026-08-28

Der Shared Chapter Snapshot Fix ist in `88006baa10d45dfb1a28c7721a74ce85876e0c45` enthalten. Der getestete Source-/Test-Head ist `22032fac0dc413c2cfa3af5d9bbd196d82f7fc93`.

Die bestätigte Root Cause war ein repository-instanzlokaler Chapter-Snapshot-Cache. Der generische process-lokale Store liegt in `mihon/state/MihonChapterSnapshotStore.kt`, nutzt exakt `sourceId + chapterUrl`, speichert höchstens 500 Einträge, synchronisiert jeden Zugriff über einen privaten Lock und gibt defensive `SChapter`-Kopien auf Ein- und Ausgabe zurück. Die vollständige Kopierlogik liegt gemeinsam in `mihon/model/MihonModelSnapshots.kt`. `mangaSnapshots` bleibt repository-lokal.

`MihonMangaRepositoryTest` beweist den öffentlichen Repository-Lebenszyklus Repository A -> Repository B, Source-Isolation bei identischer URL und defensive Kopien. Der Workflow filtert diese Klasse explizit.

### CI und APK

- Workflow-Run: `33159391334` / Run `277`, vollständig erfolgreich.
- Artifact: `Futon-Mihon-Fix-Signed-Release`, id `9681476261`.
- APK: `Futon-9.8.1-mihon-fix-test-signed-release.apk`.
- APK SHA-256: `acaa9a48a62391f8ad667a4801394cae13d6c41c44e971c69f9d3cacc3ee04ee`.
- Signing: `temporary-test-key`; CI-Signaturprüfung erfolgreich, aber nicht als Update über eine anders signierte Installation verwendbar.
- Reale Gerätevalidierung des aktuellen APKs ist noch ausstehend: `POST_22032_DEVICE_VALIDATION`.

### Feste Upstream-Sync-Prozedur

Bei jedem späteren Kototoro/Mihon/Keiyoushi-Update exakt diese Reihenfolge einhalten:

1. Futon Feature-Head live lesen.
2. Futon `devel` live lesen.
3. Kototoro `devel` live lesen.
4. `keiyoushi/extensions-lib/main` live lesen.
5. Nur die entsprechenden Mihon/Tachiyomi-Grenzen vergleichen.
6. API-Additions, API-Removals und Verhaltensänderungen getrennt erfassen.
7. Den kleinsten semantisch notwendigen Delta portieren.
8. Keine komplette Upstream-Datei blind ersetzen.
9. Unit-/Contract-Tests ausführen.
10. Den optimierten R8-APK prüfen.
11. Die Kontextdateien mit den tatsächlich geprüften SHAs aktualisieren.

Die logischen Futon-Grenzen bleiben `mihon/compat/**` für Host-ABI/Request Context/dynamische Extension-Kompatibilität, `mihon/model/**` für Konvertierung/Snapshots, `mihon/state/**` für process-lokalen Integrationszustand, die bestehenden Network-/Cloudflare-Bereiche sowie die bestehenden Extension Loader-/Manager-Bereiche. Kein neues Gradle-Modul anlegen.

## Aktuelles CI

Für tested head `9e5b792...`:

- Debug Build: run `33012858726`, success
- Debug artifact id: `9623782588`
- Signed Mihon Test Build: run `33012858721`, success
- fokussierte Mihon Regressionen: success
- Release-Lint: success
- optimierter R8 Release-Build: success
- APK-Signaturprüfung: success
- signed artifact id: `9623914916`
- signed artifact ZIP SHA-256: `fb85ca060bc7cd7c94c2a65fed7601ade155c109dc5b7482baba218b89876438`
- signed APK SHA-256: `bcbe14a5d536703aa2f0c278f9668238d03845bc8a33974bc7891aca58fad25f`
- APK-Hash wurde gegen die im Artifact enthaltene `.sha256` und lokalen `sha256sum` geprüft
- Signing: `temporary-test-key`
- App-Version: `9.8.1-mihon-fix-test`, versionCode `90803`

Wichtig: Der temporäre Test-Key kann keine Installation überschreiben, die mit einem anderen Key signiert wurde. Bei Installationskonflikt kann eine Deinstallation der alten Testinstallation nötig sein.

Direkte DEX-Prüfung des optimierten APKs bestätigte den aktuellen Workflow-Gate, unter anderem Zstd/Brotli, `PreferenceScreen`, `ConfigurableSource`, `Source`, `RefreshContext`, `HttpSource`, `SManga`, `SChapter`, `Page`, `getMangaUpdate`, `fetchRelatedMangaList`, Chapter/Page APIs, `CaptchaAutoResolveCoordinator` und `TachiyomiApkClassLoaderPolicy`.

`.ci/mihon-fix-latest.json` zeigt auf run `33012858721` / source `9e5b792...` / success.

## Aktuelle Referenzen

Kototoro:

- Repo: `Kototoro-app/Kototoro`
- Branch: `devel`
- zuletzt live verifiziert: `f4f37a5b7290da05c10b9325912f2a37ebeff0f9`
- vorheriger Kontext-SHA: `dec0ef781644245f6937dc1cafc8ca84963fe08e`
- zwischen `dec0ef...` und `f4f37...` liegen 9 Commits, aber keine Änderungen an den hier verwendeten Mihon/Tachiyomi-Runtime-, Source-ABI-, NetworkHelper-, ClassLoader- oder Extension-Runtime-Referenzdateien

Wichtige aktuelle Kototoro-Referenzdateien:

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

## Aktuell implementierte Kompatibilität

- Mihon Default-Client-Interceptor-Reihenfolge und vollständige Host-OkHttp-Konfiguration.
- Moderne und Legacy Brotli/Zstd-Kompatibilität.
- `HttpSource` modern suspend plus Legacy-Rx-`fetch*` Fallback.
- `RefreshContext`, Request-/Source-Context und aktuelle Source-Request-ABI.
- `SManga.memo`, `SMangaUpdate`, `Source.getMangaUpdate(...)`, Legacy Details/Chapters und kombinierter Repository-Pfad.
- `minSdk = 26` gegen den dynamischen Serializer/default-method ABI-Konflikt.
- Kototoro-artige `TachiyomiApkClassLoaderPolicy`, Parent-owned Host-ABI, child-first `$-CC` / `$DefaultImpls`.
- Source-Browser-Origin, gemeinsamer Mihon Preference-Namespace, protobuf extension-lib Version, isolierte Extension-Repo-Fehler und robustes Fallback-Version-Parsing.
- Historischer Futon Cloudflare WebView-Solve mit Clearance-Erkennung und Originalrequest-Retry.

Der alte Kontext, der `157d94e...` als letzten Source-Stand behandelte, ist superseded. Die zusätzlichen App-Fixes bis `85f19b...` und Tests bis `9e5b792...` sind bereits Teil des aktuellen getesteten Trees.

## Historische Root Causes

Nicht als neue Bugs zählen, solange ein aktuelles Gerätelog keinen Rückfall beweist:

- fehlender `UncaughtExceptionInterceptor` im Default-Client, resolved
- fehlender host-sichtbarer Brotli Runtime-Typ, resolved
- `GeneratedSerializer.typeParametersSerializers()` `AbstractMethodError` durch alte minSdk/interface-desugaring ABI, resolved durch `minSdk = 26`
- obsolete Details/Chapter Repository-Route, resolved
- `SManga.getMemo` `NoSuchMethodError`, resolved
- `Source.getMangaUpdate` `NoSuchMethodError`, resolved
- Mihon 1.6 MangaDex `0 manga` Continuity-Fall, resolved/superseded durch aktuellen kombinierten Repository-Pfad
- historischer Comix Cloudflare-Solve mit geändertem `cf_clearance` und HTTP 200 nach Retry, proven historical

Alle Details und frühere CI-Regressionen stehen in `STATE.md` und `graph.yaml`.

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

Das ist aktuell **kein bestätigter Root Cause**. Ohne aktuellen Gerätetest des `9e5b792...` Trees nicht allein wegen neuerer Kototoro-Architektur portieren. Wenn ein aktuelles Gerätelog genau diesen Pfad als Fehler zeigt und Kototoro ihn funktionierend löst, die relevante aktuelle Kototoro-Orchestrierung code-nah mit Attribution und Tests portieren statt einen weiteren Futon-Workaround zu erfinden.

## Höchste Priorität: echter Gerätetest des aktuellen APKs

Es gibt im derzeitigen Kontext noch keinen aufgezeichneten realen Device-Test des getesteten `9e5b792...` Trees / `85f19b...` App-Source-Stands. CI/DEX ist grün, aber Device-Evidence hat Vorrang.

Aktueller offener Runtime-Knoten: `POST_9E5_DEVICE_VALIDATION`.

Teste mindestens Comix, MangaDot.net, Manga Ball, Weeb Central und MangaRead.org jeweils soweit möglich über Browse/Popular, Search, Details, Chapters und Pages/Images.

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
