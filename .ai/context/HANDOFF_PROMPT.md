# Handoff Prompt for the Next AI

Du bist die nächste primäre Entwicklungs-KI für das Futon-Mihon/Keiyoushi-Kompatibilitätsprojekt.

## Projekt

- Futon-Fork: `https://github.com/madebycli/Futon`
- Arbeitsbranch: `fix/mihon-uncaught-exception-interceptor`
- Draft-PR: #1 gegen `devel`
- Referenzimplementierung: `https://github.com/Kototoro-app/Kototoro`, Branch `devel`
- Aktueller Kototoro-Snapshot beim letzten Handoff: `e036c5940af6b849c055ab46d73c0ec4896276f7` (v2.0.3)

## Zwingender Start

Bevor du Code änderst:

1. Lies vollständig:
   - `.ai/context/README.md`
   - `.ai/context/STATE.md`
   - `.ai/context/graph.yaml`
   - `.ci/mihon-fix-latest.json`
2. Hole den AKTUELLEN Branch-Head, PR-Status und CI-Status von GitHub. Verlasse dich nicht auf die im Handoff gespeicherten SHAs.
3. Hole den AKTUELLEN `devel`-Head von `Kototoro-app/Kototoro`. Wenn sich der SHA geändert hat, aktualisiere `STATE.md` und `graph.yaml` sofort.
4. Nimm mein neuestes Testergebnis / Log / die von mir genannte APK-Version entgegen und behandle es als höchste Laufzeit-Evidenz.
5. Zähle nicht jede rote Log-Zeile als eigenen Fehler. Gruppiere nach eindeutiger Root Cause und beginne immer mit der ersten echten Exception im relevanten Source-Pfad.

## Deine Hauptaufgabe

Prüfe anhand meines jeweils neuesten Gerätetests, ob Futons aktuelle Mihon/Keiyoushi-Implementierung wirklich funktioniert. Vergleiche jeden noch fehlerhaften Teil mit Kototoro und entscheide anhand von Code + Tests + Gerätelog, ob Kototoros Implementierung robuster ist.

Vergleiche insbesondere:

- Extension Discovery / Loader / Version Gate
- ChildFirstPathClassLoader
- Parent-vs-Child ABI Ownership
- `TachiyomiApkClassLoaderPolicy`
- Injekt Bridge
- NetworkHelper
- Required Mihon/Keiyoushi Interceptors und Reihenfolge
- Brotli / Zstd / Compression-Verhalten
- Cloudflare / WebView / Clearance / Retry
- Source API 1.4 / 1.5 / 1.6 Kompatibilität
- `SManga`, `SManga.memo`, `SMangaUpdate`
- `getMangaUpdate(...)`
- Legacy-Fallbacks
- Filter Mapping
- Repository Adapter
- Details / Chapters / Pages / Images
- Request Headers / Referer / User-Agent / Cookies
- dynamische Serializer-/Kotlin-/Coroutine-/OkHttp-ABI
- APK/Dex Class Loading

## Kototoro ist die bevorzugte Referenz

Kototoro gilt in diesem Projekt als die primäre Referenzimplementierung für Mihon/Tachiyomi-Kompatibilität, weil es dieselbe App-/Kotatsu-Linie nutzt und eine deutlich vollständigere externe Extension-Laufzeit besitzt.

WENN FUTONS EIGENE IMPLEMENTIERUNG WIEDER SCHEITERT UND KOTOTORO DENSELBEN PFAD FUNKTIONIEREND IMPLEMENTIERT:

**Dann portiere den relevanten Kototoro-Code so wortgetreu / code-für-code wie praktisch möglich.**

Das bedeutet:

- Keine neue kreative Neuimplementierung, wenn Kototoro bereits einen bewährten Pfad hat.
- Logik, Reihenfolge, Kontrollfluss, ABI-Signaturen, ClassLoader-Policy, Interceptor-Reihenfolge, Retry-Verhalten und Edge-Case-Behandlung möglichst exakt übernehmen.
- Nur zwingend notwendige Änderungen für Futon vornehmen:
  - Package-Namen
  - Imports
  - Dependency-Injection-Grenzen
  - Futon-Modellnamen
  - bereits vorhandene Host-Komponenten
  - UI-/Repository-Integrationspunkte
- Zugehörige Kototoro-Tests ebenfalls portieren oder äquivalente Regressionstests hinzufügen.
- Nicht einfach nur einen einzelnen Fehlernamen filtern, wenn Kototoro eine generische Policy dafür besitzt.
- Bei ClassLoader-/ABI-Fehlern zuerst Kototoros `TachiyomiApkClassLoaderPolicy` untersuchen und bevorzugt vollständig übernehmen statt immer neue Sonderfälle zu ergänzen.
- Kototoro ist Apache-2.0 und Futon GPL-3.0: notwendige Copyright-/NOTICE-/Attributionshinweise erhalten, geänderte übernommene Dateien entsprechend kennzeichnen. Keine Notices entfernen.

## Bereits erledigte Arbeit

Die aktuelle Branch enthält bereits u. a.:

- Mihon-kompatiblen `UncaughtExceptionInterceptor`
- `UserAgentInterceptor`
- `CloudflareInterceptor`
- Filterung inkompatibler Legacy-Compression-Network-Interceptors
- offiziellen OkHttp-Brotli-Host-Runtime-Support
- Zstd-Host-Runtime-Support
- Usagi/Kototoro-artigen Cloudflare-WebView-Solve + Retry des Originalrequests
- `SManga.memo`
- `SMangaUpdate`
- `Source.getMangaUpdate(...)`
- Legacy-Fallback für ältere Extensions
- `MihonMangaRepository` auf kombinierten 1.6-Update-Pfad
- `minSdk = 26`, passend zu aktuellem Mihon + Keiyoushi, um dynamische Default-Method-/Serializer-ABI-Probleme zu vermeiden
- fokussierte Mihon Regression Tests
- Debug APK CI
- optimierten signierten Test-APK-Workflow

Beim letzten gespeicherten Stand war der bedeutende Source-Head:
`d5e10a1d6b7dd69b45c4e7d953fa2f14f3e7ec32`

Danach wurde durch GitHub Actions ein Status-Commit erzeugt. Re-fetch den echten aktuellen Head.

## Letzte bekannte Testergebnisse

Vor `d5e10a1` wurde auf dem Gerät bestätigt:

- Extensions laden grundsätzlich erfolgreich.
- Der alte `UncaughtExceptionInterceptor`-Fehler ist verschwunden.
- Der fehlende `okhttp3.brotli.BrotliInterceptor` wurde identifiziert und behoben.
- Comix Cloudflare WebView löst die Challenge erfolgreich.
- `cf_clearance` ändert sich.
- Der Originalrequest wird danach wiederholt.
- Comix liefert danach HTTP 200.
- Danach trat bei Comix, MangaDot.net und Manga Ball derselbe `GeneratedSerializer.typeParametersSerializers()` `AbstractMethodError` auf.
- Root Cause wurde als Host-minSdk/Android-Interface-Desugaring-ABI identifiziert; Futon wurde auf API 26 angehoben.
- Weeb Central lief in den alten Details-/Chapter-Pfad und warf `UnsupportedOperationException`; Futon wurde auf `getMangaUpdate(...)` umgestellt.

Die fokussierten Tests und der signierte optimierte Test-Build für `d5e10a1` sind laut `.ci/mihon-fix-latest.json` erfolgreich.

**Ein neuer realer Gerätetest nach `d5e10a1` ist im Kontext noch nicht als abgeschlossen dokumentiert.** Mein nächster Log/Test kann also neue Wahrheit liefern.

## Besonders wichtiger Kototoro-Unterschied

Kototoro hat eine explizite Parent-ABI-ClassLoader-Policy:

- Host-/Parent-owned u. a. `kotlinx.coroutines`, Android/AndroidX, OkHttp, Okio, Rx, `eu.kanade.tachiyomi.source.*`, `eu.kanade.tachiyomi.network.*`, `eu.kanade.tachiyomi.util.*`, Injekt usw.
- Generierte `$-CC` / `$DefaultImpls` Bridge-Klassen bleiben bewusst child-first, weil sie mit Extension-APKs ausgeliefert werden können.

Futons derzeitiger ChildFirstPathClassLoader ist generischer und besitzt diese komplette Tachiyomi-ABI-Ownership-Policy noch nicht.

Wenn der neue Test irgendeinen dieser Fehler zeigt:

- `AbstractMethodError`
- `NoSuchMethodError`
- `IncompatibleClassChangeError`
- `ClassCastException`
- `VerifyError`
- Host-/Extension-Doppelklassen
- ClassLoader-bedingte `ClassNotFoundException`

... dann untersuche zuerst Kototoros ClassLoader-Policy. Wenn sie den Fall sauber löst, portiere sie bevorzugt vollständig statt eines weiteren Einzelpatches.

## Testverfahren

Für jeden Fix:

1. Root Cause aus dem neuesten Log isolieren.
2. Futon-Codepfad bestimmen.
3. Kototoro-Äquivalent am aktuellen Kototoro-SHA bestimmen.
4. Aktuellen Keiyoushi/Mihon-Vertrag prüfen.
5. Entscheidung dokumentieren: Futon behalten, Teil portieren oder Kototoro-Pfad vollständig portieren.
6. Regressionstest hinzufügen, der genau diesen Fehler gefangen hätte, sofern technisch möglich.
7. Fokussierte Mihon-Tests ausführen.
8. Komplette Debug APK bauen.
9. Bei Runtime/ClassLoader-Problemen die gebaute APK/Dex auf die benötigten Klassen/Methoden prüfen.
10. Signierten optimierten Testbuild verifizieren, wenn Workflow verfügbar.
11. Mir eine direkt installierbare APK geben, nicht nur einen GitHub-Actions-ZIP-Link.
12. Mich gezielt testen lassen:
    - Comix
    - MangaDot.net
    - Manga Ball
    - Weeb Central
    - MangaRead.org
    - jeweils Browse/Search/Details/Chapters/Pages/Images soweit unterstützt.
13. Mein neues Testergebnis wieder in `STATE.md` und `graph.yaml` eintragen.

## Kontextgraph MUSS gepflegt werden

Nach jeder Arbeitsrunde musst du `.ai/context/graph.yaml` und `.ai/context/STATE.md` aktualisieren.

Mindestens aktualisieren:

- aktueller Futon-Branch-Head
- letzter bedeutender Source-Head
- aktueller Kototoro-Referenz-SHA
- neue Device-Evidence
- neue Root Cause
- neuer Fix
- Teststatus
- CI-Run-IDs
- APK-Status
- welche Kototoro-Datei/Version als Referenz verwendet wurde
- offene nächste Test-/Fehlerknoten

Entferne historische Ursachen nicht einfach. Markiere sie als `resolved`, `historical` oder `superseded`, damit die nächste KI versteht, warum die aktuelle Architektur so aussieht.

## Git-Sicherheitsregeln

- Nur auf `fix/mihon-uncaught-exception-interceptor` arbeiten.
- `devel` nicht direkt verändern.
- PR #1 Draft lassen.
- Nicht mergen, außer ich verlange es ausdrücklich.
- Keine Releases erstellen, außer ich verlange es ausdrücklich.
- Keine Secrets/Signing-Keys/Passwörter ausgeben.
- Während ein relevanter CI-Lauf aktiv ist nicht unnötig neue Source-Commits erzeugen, wenn dadurch der Lauf abgebrochen würde.

## Definition von „besser“

Eine Kototoro-Implementierung ist nur dann „besser“ für Futon, wenn mindestens einer dieser Punkte anhand von Code/Test/Device-Evidence gilt:

- sie erfüllt einen aktuelleren Mihon/Keiyoushi-ABI-Vertrag,
- sie verhindert eine bereits beobachtete Runtime-Exception generisch,
- sie besitzt eine robustere ABI-/ClassLoader-Grenze,
- sie erhält Host-Netzwerkfunktionen ohne Keiyoushi-Verträge zu brechen,
- sie hat Regressionstests für den problematischen Pfad,
- sie funktioniert auf meinem Gerät, wo Futons Implementierung scheitert,
- oder sie deckt mehr Extension-Versionen ab ohne bestehende Quellen zu brechen.

Nicht nur anhand von Codegröße oder Stil entscheiden.

Beginne jetzt damit, meinen neuesten Test/Log entgegenzunehmen, den Kontext zu aktualisieren und die aktuelle Futon-Implementierung gegen den aktuellen Kototoro-Stand zu prüfen.
