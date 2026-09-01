# Handoff Prompt for the Next AI

Du bist die nächste primäre Entwicklungs-KI für das Futon-Mihon/Keiyoushi-Kompatibilitätsprojekt.

## Sicherheitsregeln

- Repository: `madebycli/Futon`
- Arbeitsbranch: `fix/mihon-uncaught-exception-interceptor`
- Base: `devel`
- Draft-PR: #1
- Niemals mergen oder `devel` direkt verändern, außer der Benutzer verlangt es ausdrücklich.
- Keine Releases veröffentlichen, außer der Benutzer verlangt es ausdrücklich.
- Keine Signing-Secrets, Keys, Passwörter oder Tokens ausgeben.
- Nie behaupten, ein Device-Problem sei gelöst, solange dieses exakte APK nicht auf einem Gerät validiert wurde.

## Zwingender Start jeder Runde

Lies zuerst vollständig:

1. `.ai/context/README.md`
2. `.ai/context/STATE.md`
3. `.ai/context/graph.yaml`
4. `.ci/mihon-fix-latest.json`
5. diese Datei

Danach live neu holen:

- Branch-Head von `fix/mihon-uncaught-exception-interceptor`
- PR #1 Status, Head und Base
- relevante GitHub Actions Runs
- aktuellen `devel`-Head von `Kototoro-app/Kototoro`
- aktuellen `main`-Head von `keiyoushi/extensions-lib`, wenn ABI/Source-Vertrag relevant ist

Gespeicherte SHAs niemals ungeprüft als aktuell annehmen. Kontextcommits mit `[skip ci]` können den Branch-Head nach dem letzten getesteten Source-Head weiterbewegen.

## Aktueller verifizierter Source-Stand

Finaler CI-verifizierter Source/Test-Head:

`78d128189277167cd2f0c84979c9f94139b9ff05`

Tree:

`2848e13c2b26566137a4a252a6f5c418fee8d012`

Synthetischer PR-Merge aus dem finalen Build:

`6f690e43de883bc71897e5fa9c70cfc9c49d88eb`

Der Merge hat exakt denselben Tree `2848e13c...`. Der Build entspricht deshalb dem Feature-Tree.

Letzter Source-Fix:

`809a8900f9f662b516b61eb7443cbf6c78021e6a`, `fix(mihon): preserve legacy snapshot fields on restore`.

`78d128...` verschärft anschließend nur noch die Regression-Fixture, damit moderne und Legacy-Felder absichtlich verschieden bleiben.

## Aktueller Fix: dauerhafte Mihon-Snapshots über App-Neustart

Der frühere shared chapter snapshot fix löste Repository A -> Repository B innerhalb eines Prozesses. Der aktuelle Fix erweitert das auf App-Prozess-Neustart und DB-Restore.

Wichtige Komponenten:

- `mihon/state/MihonSnapshotPersistence.kt`
- `core/parser/AwaitingMihonMangaRepository.kt`
- `core/parser/MangaRepository.kt`
- `mihon/MihonExtensionManager.kt`
- `mihon/extensions/runtime/ExternalExtensionManagerFacade.kt`
- `mihon/extensions/runtime/ExternalExtensionManagerRuntime.kt`
- `mihon/MihonMangaRepository.kt`
- `mihon/state/MihonSnapshotPersistenceTest.kt`

Persistenzregeln:

- Datei `mihon-model-snapshots-v1.json` unter `noBackupFilesDir`
- Schema 1
- maximal 500 Manga-Snapshots und 1000 Chapter-Snapshots
- Key `sourceId + exact URL`
- keine URL-Normalisierung
- keine Extension-Implementierungsklassen serialisieren, nur Futon-hosted `SManga`/`SChapter` Werte
- synchronisiert
- temp write + `fsync` + atomic replace wenn verfügbar
- korrupte Datei darf Startup nicht brechen
- unbekannte Schema-Version wird ignoriert

`AwaitingMihonMangaRepository` wird nur für Mihon-Quellen verwendet, die aus Futons DB wiederhergestellt wurden, bevor der initiale Extension-Scan fertig ist. `awaitInitialLoad()` wird im Runtime-Pfad auch bei leeren oder fehlerhaften Scan-Ergebnissen im `finally` freigegeben.

### Wichtigster neuer Testfund

Der neue Restart-Test fand einen echten Restore-Fidelity-Bug:

- `genres` kann `genre` überschreiben
- `number` kann `chapter_number` ableiten
- `scanlators` kann `scanlator` überschreiben

Commit `809a890...` stellt deshalb erst die modernen Werte her und setzt danach die separat persistierten Legacy-Werte erneut. Dadurch bleiben beide ABI-Sichten unabhängig korrekt.

## Finales CI

### Mihon Fix Signed Test Build

- Run id: `33536918663`
- Run number: `295`
- Ergebnis: success
- fokussierte Mihon Tests: 49/49 success
- Release Lint: success
- optimierter R8 Release Build: success
- optimierter Mihon ABI Gate: success
- APK Signaturprüfung: success
- Artifact Upload: success

Artifact:

- `Futon-Mihon-Fix-Signed-Release`
- Artifact id `9812861083`
- Artifact digest `sha256:e174ceb153e80c28ee70b9a883a174a12a074c76513c2d066c5b3c65eac9e367`
- APK `Futon-9.8.1-mihon-fix-test-signed-release.apk`
- APK SHA-256 `4f0bdca5bc1bf29f37663485275dacedc25adf36d20ee58fb10cfe4cf1b6b745`
- Artifact `.sha256` und lokal berechneter APK-Hash stimmen exakt überein
- `BUILD-INFO.txt`: `Signing: repository-release-key`

Wichtig: Der finale optimierte Build nutzt jetzt den Repository Release Key. Der frühere Status `temporary-test-key` ist für dieses finale APK superseded. Niemals Secret-Material ausgeben.

### Debug Build

- Run id `33536918698`
- Run number `148`
- Ergebnis success
- Debug APK Build und Upload success

## Aktuelle Upstream-Referenzen, live geprüft 2026-09-01

### Kototoro

- `Kototoro-app/Kototoro`
- Branch `devel`
- aktueller Head `b2c20e84298bfcc806567d784c8cb6607b1c919f`
- vorher gespeichert `19cbb0790744eb28e5accead7e9514d976b02f3d`
- Delta: 32 Commits
- die exakten Pfade `TachiyomiApkClassLoaderPolicy.kt` und `KotoNetworkHelper.kt` erscheinen nicht im Delta
- der restliche Delta enthält unter anderem Favourites-, Wizard- und Tsundoku-Arbeit, also bei einem neuen Fehler immer den tatsächlich betroffenen exakten Pfad prüfen

### Keiyoushi extensions-lib / TachiyomiX 1.6

- `keiyoushi/extensions-lib`
- Branch `main`
- aktueller Head `42255ee5fa96d9425697b7c143587483207308d1`
- vorher `18a8e26be2320b48bdaa11840170479b62989e23`
- 1.6 wurde in `main` gemergt und ABI-Tracking hinzugefügt

Der aktuelle 1.6 `Source`/`HttpSource` Vertrag wurde gegen Futon `78d128...` geprüft. Es wurde kein fehlender aktueller `Source`- oder `HttpSource`-ABI-Einstieg gefunden. Futon hostet zusätzlich Legacy/Fork-Kompatibilität. Das ist Source-Level-Evidenz plus grüner APK-ABI-Gate, kein Ersatz für Device-Tests.

## Historische Root Causes, nicht neu entdecken ohne aktuelle Evidence

- fehlender `UncaughtExceptionInterceptor` im Default Client, resolved
- fehlender host-sichtbarer Brotli Runtime-Typ, resolved
- `GeneratedSerializer.typeParametersSerializers()` `AbstractMethodError`, resolved durch `minSdk = 26`
- obsolete Details/Chapter Route, resolved
- `SManga.getMemo` `NoSuchMethodError`, resolved
- `Source.getMangaUpdate` `NoSuchMethodError`, resolved
- MangaDex 1.6 Continuity `0 manga`, resolved/superseded
- repository-instanzlokaler Chapter Snapshot Verlust, resolved
- Restore Setter Fidelity, resolved durch `809a890...`

Historischer Comix-Test bewies, dass Futons WebView Cloudflare Flow `cf_clearance` ändern und den Originalrequest erfolgreich bis HTTP 200 wiederholen konnte. Das beweist nicht jeden aktuellen Cloudflare-Fall.

## Offene Kototoro-Paritätsdifferenz

Kototoro hat weiterhin eine reichere Cloudflare/Captcha-Orchestrierung mit SingleFlight, Resolver State, Auto/Manual Strategy, Recent-Success-Verhalten, explizitem Manual Fallback und per-Source Auto-Captcha-Steuerung.

Das ist kein bestätigter aktueller Root Cause. Nur portieren, wenn aktuelle Device-Evidence des finalen APKs den Pfad als Fehler zeigt und der aktuelle Kototoro-Pfad den gleichen Fall generisch löst.

## Nächster entscheidender Schritt

Aktueller offener Runtime-Knoten:

`POST_78D_REPOSITORY_KEY_DEVICE_VALIDATION`

Das exakte finale APK mit Repository Release Key auf dem Samsung Galaxy S25 Ultra testen.

1. Wenn eine anders signierte alte Futon-Testinstallation ein Update verhindert, diese einmal deinstallieren und sauber installieren.
2. Exakt dokumentieren, ob Play Protect das Repository-Key-APK weiterhin blockiert.
3. Wenn ein Clean Install weiter blockiert wird, genaue Play-Protect-/Installer-Meldung oder Code erfassen. Dann können Android Developer Verification oder Play Distribution der nächste Distribution-Schritt sein.
4. Nach erfolgreicher Installation Comix, MangaDot.net, Manga Ball, Weeb Central und MangaRead.org über Browse/Search/Details/Chapters/Pages testen.
5. Zusätzlich einen echten App-Neustart testen und zuvor geladene Mihon-Manga/Chapter erneut öffnen, damit die neue Persistenz auf Hardware validiert wird.

Bei Fehlern immer die erste echte Exception isolieren und wiederholte Logzeilen nach Root Cause gruppieren. Device-Evidence hat Vorrang vor CI.
