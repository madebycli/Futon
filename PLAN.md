# PLAN.md: Release-Signing und Samsung-Installationsfix für Futon

## Ziel

Futon soll als stabil signierte Release-APK verteilt werden können. Der private Release-Key wird auf einem NixOS-PC erzeugt und sicher als GitHub-Actions-Secret hinterlegt. Zusätzlich wird der Installationsfehler auf dem Samsung Galaxy S25 Ultra reproduzierbar untersucht und mit der kleinsten belegten Änderung behoben.

Die bestehende Mihon-/Keiyoushi-Kompatibilität, der Shared Chapter Snapshot Store, Cloudflare, ClassLoader, R8 und die CI-Prüfungen bleiben erhalten.

## Anforderungen

### Release-Signierung

- Ein NixOS-kompatibles Shell-Skript erzeugt lokal einen langlebigen Android-Release-Keystore.
- Das Skript prüft `keytool`, `openssl`, `base64` und `gh`.
- Der Keystore wird mit restriktiven Dateirechten erzeugt, niemals committed und niemals in Logs ausgegeben.
- Das Skript hinterlegt Keystore und Passwörter über `gh secret set` als verschlüsselte GitHub-Actions-Secrets.
- Es werden keine Workflow- oder App-Dateien für die reine Key-Erzeugung verändert.
- Die bestehenden Secret-Namen werden verwendet: `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` und `KEY_PASSWORD`.
- Die lokale Sicherung des Keystores muss außerhalb des Repositories verschlüsselt erfolgen.
- Die bestehende temporäre Testsignatur darf nicht als Release-Signatur weiterverwendet werden.

### Samsung-Installationsfehler

- GrapheneOS ist mit dem aktuellen APK erfolgreich.
- Eine andere temporär signierte App lässt sich auf dem Samsung Galaxy S25 Ultra installieren. Eine temporäre Signatur allein ist daher nicht als Root Cause bewiesen.
- Die aktuelle Futon-APK zeigt auf dem Samsung eine Google-Sicherheitswarnung. Nach Auswahl von „Trotzdem installieren“ wird die Installation trotzdem abgebrochen.
- APK, Manifest, Paketname, `versionCode`, `minSdk`, `targetSdk`, ZIP-Alignment und v1/v2/v3-Signatur werden mit einer funktionierenden Vergleichs-App gegenübergestellt.
- Der Samsung PackageInstaller- und Play-Protect-Fehler wird über Logcat und die exakte UI-Meldung isoliert.
- Eine Änderung an Manifest, Paketname, Build-Typ oder Signaturformat erfolgt nur bei konkreter Evidenz.
- Keine Exception wird verschluckt, um die Installation scheinbar erfolgreich wirken zu lassen.

### Projekt- und Sicherheits-Constraints

- Arbeitsbranch bleibt `fix/mihon-uncaught-exception-interceptor`.
- PR #1 bleibt offen, gegen `devel` gerichtet und Draft.
- `devel` wird nicht verändert und PR #1 wird nicht gemerged.
- Vor jedem Remote-Schreibvorgang wird der aktuelle Branch-Head live geprüft.
- Kein neuer Fix-Branch, kein Force-Push und keine fremden Änderungen überschreiben.
- Keine privaten Signing-Keys, Passwörter oder Tokens in Git, CI-Logs oder öffentliche Artifacts.
- Keine neue Gradle-Abhängigkeit und kein neues Gradle-Modul.

## Architektur

### Ansatz A, empfohlen: Lokaler Key-Generator plus bestehende CI-Signierung

**Tech-Stack:** POSIX-Shell, NixOS-Systempakete, `keytool`, `openssl`, `base64`, GitHub CLI `gh`, bestehender Gradle- und GitHub-Actions-Workflow.

**Datenfluss:**

`NixOS-PC -> lokaler Keystore -> base64-geschütztes GitHub-Secret -> bestehender Release-Workflow -> signierte APK -> Samsung-Installationsprüfung`

Der Samsung-Fix wird getrennt als Diagnosekette behandelt:

`Samsung-Fehler/Logcat -> APK- und Manifestvergleich -> kleinster belegter Fix -> signierter Rebuild -> erneuter Gerätetest`

**Vorteile:**

- Erfüllt den Wunsch nach einem reinen Key-Skript.
- Keine Änderung am funktionierenden Release-Workflow notwendig.
- Private Schlüssel bleiben unter eigener Kontrolle.
- Geringe Kosten, da keine zusätzliche Infrastruktur nötig ist.
- Der Samsung-Fix bleibt unabhängig von der Signaturdiagnose und wird nicht spekulativ.

**Nachteile:**

- Der Keystore muss lokal sicher gesichert werden.
- Ein Verlust des Keys verhindert spätere Updates derselben Paketidentität.
- Die Google-Sicherheitswarnung kann trotz stabiler Signatur bestehen bleiben.

**Komplexität:** niedrig bis mittel.

### Ansatz B: Vollständige Release-Key- und Paketpipeline im Repository

**Tech-Stack:** GitHub Actions, Gradle Signing Config, `apksigner`, optional `bundletool`, GitHub Secrets und zusätzliche Release-Metadaten.

**Datenfluss:**

`NixOS-PC -> Key -> GitHub Secrets -> neuer Release-Workflow -> APK/AAB-Validierung -> Artifact/Release -> Samsung-Test`

Dabei würden neben dem Key-Skript auch Workflow-, Gradle- und Release-Dateien angepasst werden. Für den Samsung-Fehler kämen zusätzliche Installations- und Paketprüfungen in CI hinzu.

**Vorteile:**

- Vollautomatische Release-Prüfung.
- Reproduzierbare Prüfung von Signatur, Alignment, Manifest und Artifact.
- Gut geeignet für spätere öffentliche Releases.

**Nachteile:**

- Größerer Änderungsumfang und mehr Angriffsfläche.
- Mehr CI-Komplexität und potenziell höhere Laufzeitkosten.
- Nicht nötig, solange der bestehende Workflow die Secrets bereits verarbeitet.
- Könnte funktionierende Mihon-/R8-/ClassLoader-Arbeit unnötig berühren.

**Komplexität:** mittel bis hoch.

**Ausführungsentscheidung:** Ansatz A ist für diese Ausführung festgelegt. Er erfüllt den aktuellen Scope mit minimalem Risiko. Ansatz B bleibt ein separates Release-Automatisierungsprojekt. Ein Samsung-Code- oder Manifest-Fix wird weiterhin nur nach konkreter PackageInstaller-/Play-Protect-Evidence vorgenommen.

## Dateistruktur

### Erste drei Dateien

1. `PLAN.md`
   - Dieser bestätigbare Umsetzungsplan, inklusive Scope, Sicherheitsregeln und offenen Samsung-Diagnosepunkten.

2. `scripts/create-release-signing-key.sh`
   - NixOS-taugliches Skript für Keystore-Erzeugung, sichere Eingaben, lokale Berechtigungen, GitHub-Repository-Prüfung und `gh secret set`.
   - Keine Ausgabe von Passwörtern oder privaten Schlüsselmaterialien.

3. `docs/release-signing.md`
   - Kurze Bedienungsanleitung für Key-Erzeugung, verschlüsselte Sicherung, GitHub-Secret-Namen, Rotation und Installationshinweise.

Der konkrete Samsung-Code- oder Build-Dateipfad ist noch nicht festgelegt. `UNKLAR:` Er wird erst nach der exakten PackageInstaller-/Play-Protect-Evidence bestimmt.

## Umsetzungsschritte

1. Den aktuellen Feature-Branch-Head und PR-Zustand live prüfen. `devel` bleibt unverändert.
2. Die bestehenden Kontextdateien und den aktuellen Release-Workflow lesen. Prüfen, ob die Secret-Namen bereits exakt verwendet werden.
3. `scripts/create-release-signing-key.sh` erstellen.
4. Das Skript statisch prüfen: `shellcheck`, sichere Dateirechte, keine Secret-Ausgabe, Repository-Prüfung über `gh repo view` und keine ungewollten Pushes.
5. Das Skript statisch prüfen und den Hilfe-/Fehlerpfad ohne echte Secrets ausführen. Die echte Key-Erzeugung erfolgt später auf dem NixOS-PC des Masters.
6. Den Samsung-Fehler mit der neuesten APK reproduzieren und die exakte Warnung dokumentieren.
7. PackageInstaller- und Play-Protect-Logs sammeln, zum Beispiel mit Filtern für `PackageInstaller`, `PackageManager`, `INSTALL_FAILED`, `verifier` und `Play Protect`.
8. Die Futon-APK technisch prüfen: `apksigner verify --verbose --print-certs`, Alignment, Manifest, Paketname, Version, SDK-Werte, Split-APK-Status und Signatur-Schemata.
9. Die funktionierende temporär signierte Vergleichs-App unter denselben Kriterien prüfen. Die temporäre Signatur nicht pauschal als Ursache behandeln.
10. Nur die konkrete Ursache beheben. Mögliche Ursachen werden getrennt bewertet: inkompatible Paketversion, Signaturkonflikt, malformed APK, Installer-/Verifier-Regel, Manifest-/SDK-Konfiguration oder beschädigter Download.
11. Einen Release-Build mit dem stabilen Key vorbereiten, sobald der Master die Secrets auf seinem NixOS-PC erzeugt und hinterlegt hat.
12. Samsung und GrapheneOS mit demselben Artifact testen. Bei bestehender alter Futon-Installation den Signaturwechsel als erwarteten Neuinstallationsfall dokumentieren.
13. Relevante Mihon-Tests, den bestehenden R8-/ABI-Gate und den finalen signierten Build ausführen.
14. Kontextdateien und PR-Beschreibung mit realen Ergebnissen ergänzen. Device-Evidence bleibt offen, falls kein reproduzierbarer Samsung-Test mit finalem Release-Key vorliegt.

## Skalierbarkeit, Sicherheit und Kosten

- Ein stabiler Keystore ermöglicht signierte Updates ohne Änderungen an der Paketidentität.
- Der Key bleibt ein einzelnes, geschütztes Geheimnis. Backup und Recovery müssen außerhalb von GitHub erfolgen.
- GitHub Secrets reduzieren das Risiko einer Veröffentlichung im Repository, ersetzen aber kein verschlüsseltes Offline-Backup.
- Das Skript ist idempotent: Es überschreibt keinen vorhandenen Keystore ohne ausdrückliche Bestätigung.
- Es werden keine neuen Server, Datenbanken oder kostenpflichtigen Dienste benötigt.
- CI nutzt den bestehenden Workflow und die bestehenden Artifact-Aufbewahrungsregeln.
- Der Samsung-Vergleich wird reproduzierbar dokumentiert, damit spätere Geräteprobleme nicht durch Vermutungen behandelt werden.

## Offene Fragen / Unklarheiten

- `UNKLAR:` Der exakte Wortlaut der Samsung-Warnung und der anschließende Installer-Fehlercode fehlen noch.
- `UNKLAR:` Die genaue Android- und One-UI-Version des Galaxy S25 Ultra ist noch nicht dokumentiert.
- `UNKLAR:` Der Installationsweg ist noch nicht festgehalten, zum Beispiel Samsung Internet, Dateien-App, Chrome, ADB oder Artifact-ZIP.
- `UNKLAR:` Es ist noch unbekannt, ob eine ältere Futon-Version mit anderer Signatur oder gleichem Paketnamen installiert war.
- Die Ausführung unterstützt beides: lokale Erzeugung ohne Upload und einen ausdrücklich mit `--upload` bestätigten Upload über `gh`.
- `UNKLAR:` Es ist noch nicht bewiesen, ob der stabile Release-Key den Samsung-Fehler behebt. Die Vergleichs-App zeigt, dass eine temporäre Signatur allein keine ausreichende Erklärung ist.
