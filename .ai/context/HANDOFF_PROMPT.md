# Handoff Prompt for the Next AI

Du bist die nächste primäre Entwicklungs-KI für das Futon-Mihon/Keiyoushi-Kompatibilitätsprojekt.

## Projektregeln

- Repository: `madebycli/Futon`
- Arbeitsbranch: `fix/mihon-uncaught-exception-interceptor`
- PR #1 gegen `devel`
- Niemals mergen oder `devel` direkt verändern, außer der Benutzer verlangt es ausdrücklich.
- Keine Signing-Secrets, Keys, Passwörter oder Tokens ausgeben.

## Finaler Runtime-Stand

Der Benutzer hat am 2026-09-01 den finalen Repository-Key-Gerätetest als erfolgreich bestätigt: "alles geht". Damit ist der bisher offene Knoten `POST_78D_REPOSITORY_KEY_DEVICE_VALIDATION` abgeschlossen.

Der zuletzt vollständig gerätetestete App-Tree ist weiterhin:

- Source/Test-Head: `78d128189277167cd2f0c84979c9f94139b9ff05`
- Tree: `2848e13c2b26566137a4a252a6f5c418fee8d012`
- Letzter Source-Fix darin: `809a8900f9f662b516b61eb7443cbf6c78021e6a`
- Signed workflow run: `33536918663`, Run #295, success
- Artifact: `9812861083`
- APK SHA-256: `4f0bdca5bc1bf29f37663485275dacedc25adf36d20ee58fb10cfe4cf1b6b745`
- Signing: `repository-release-key`

## Release-Vorbereitung

Der geplante echte GitHub-Release ist `9.8.3` mit `versionCode 90803`. Diese Nummer wurde gewählt, weil das erfolgreich getestete Repository-Key-APK bereits `90803` trägt. Ein `9.8.2` Release würde mit dem bestehenden tagbasierten Versionsschema `90802` erzeugen und wäre damit ein Android-VersionCode-Downgrade gegenüber dem Teststand.

Release-Vorbereitung umfasst:

- `gradle.properties`: `versionName=9.8.3`, `versionCode=90803`
- `CHANGELOG.md`: neuer Abschnitt 9.8.3
- `docs/releases/9.8.3.md`: Release-Notes
- `.github/workflows/release.yml`: gehärteter Tag-Workflow mit Versionsprüfung, Mihon-Regressionen, Release-Lint, optimiertem R8-Build, ABI-Gate, APK-Identitätsprüfung, Signaturprüfung, benanntem APK und SHA-256-Anhang

Der Release-Workflow publiziert erst nach einem späteren `v9.8.3` Tag. Der Benutzer hat in dieser Runde nur "release ready" verlangt, daher keinen Tag erstellen, keinen Release veröffentlichen und nicht mergen, solange das nicht ausdrücklich verlangt wird.

## Upstream-Referenzen

- Kototoro `devel`: zuletzt live geprüft `b2c20e84298bfcc806567d784c8cb6607b1c919f`
- Keiyoushi `extensions-lib/main`: zuletzt live geprüft `42255ee5fa96d9425697b7c143587483207308d1`, inklusive 1.6-Merge und ABI-Tracking
- Futons aktueller Source/HttpSource-Hostvertrag deckt die live geprüfte 1.6-API ab und bleibt absichtlich breiter für Legacy-/Fork-Kompatibilität.

## Historisch gelöste Root Causes

Nicht erneut als neue Fehler behandeln, solange aktuelle Device-Evidence keinen Rückfall beweist:

- fehlender Default-Client-Interceptor-Vertrag
- fehlende host-sichtbare Brotli Runtime
- `GeneratedSerializer.typeParametersSerializers()` `AbstractMethodError`
- obsolete Details/Chapter Route
- `SManga.getMemo` `NoSuchMethodError`
- `Source.getMangaUpdate` `NoSuchMethodError`
- MangaDex Continuity `0 manga` Familie
- repository-instanzlokaler Chapter-Snapshot-Verlust
- Verlust unabhängig gespeicherter Legacy-Felder beim Snapshot-Restore

## Nächster Schritt

1. Live Branch-/PR-/CI-Status lesen.
2. Sicherstellen, dass der Release-Prep-Commit vollständig grün ist.
3. PR #1 darf nach erfolgreichem Release-Prep-CI als Ready for Review markiert werden.
4. Erst auf ausdrücklichen Benutzerwunsch mergen.
5. Erst auf ausdrücklichen Benutzerwunsch `v9.8.3` taggen bzw. den dadurch ausgelösten öffentlichen GitHub Release starten.
