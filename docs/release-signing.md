# Futon release signing

The release workflow already accepts a stable signing key through these GitHub Actions secrets:

- `KEYSTORE_FILE`: base64 encoded JKS keystore
- `KEYSTORE_PASSWORD`: keystore password
- `KEY_ALIAS`: signing alias
- `KEY_PASSWORD`: private-key password

When all four secrets are present, `Mihon Fix Signed Test Build` uses the repository release key. If one is missing, the workflow deliberately falls back to a short-lived temporary test key.

## Generate the key on NixOS

Use a local Nix shell with Java, OpenSSL, coreutils, and the GitHub CLI:

```sh
nix shell nixpkgs#jdk17 nixpkgs#openssl nixpkgs#coreutils nixpkgs#gh
```

From a Futon checkout, run:

```sh
chmod +x scripts/create-release-signing-key.sh
./scripts/create-release-signing-key.sh --repo madebycli/Futon --upload
```

The script asks for the alias and passwords without printing them. It refuses to overwrite an existing keystore, writes it with restrictive permissions, verifies the certificate, and uploads only encrypted GitHub Actions secret values through `gh secret set`.

For local creation without uploading yet:

```sh
./scripts/create-release-signing-key.sh --keystore "$HOME/.local/share/futon/futon-release.jks"
```

The private keystore must never be committed. Keep at least one encrypted offline backup. Losing the key prevents future APK updates for the same package identity. Do not rotate it casually.

## Verify the repository configuration

```sh
gh secret list --repo madebycli/Futon
```

The values themselves must not be printed or copied into issues, logs, artifacts, or chat. A later `Mihon Fix Signed Test Build` run should report `Signing: repository-release-key` in `BUILD-INFO.txt`.

## Samsung installation note

The current CI artifact is signed with a temporary key until the four repository secrets are set. A stable key prevents the normal signature change between builds, but it does not by itself prove that a Samsung Google security warning is fixed.

If a temporary Futon APK is already installed, the first stable-key APK may require one clean uninstall and reinstall because Android rejects an update signed by a different key. Back up application data first. After that one transition, later stable-key builds can update normally.

If Samsung still blocks the clean install, capture the exact warning and the Package Installer result. Useful diagnostics are:

```sh
adb logcat -b all -d | grep -Ei 'PackageInstaller|PackageManager|INSTALL_FAILED|verifier|Play Protect'
```

Do not treat the temporary signature as the sole cause without this evidence. The existing workflow already checks optimized APK signing, and the Samsung-specific failure still needs the exact device-side installer code before an app or manifest change is justified.
