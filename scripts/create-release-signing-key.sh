#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'USAGE'
Usage: create-release-signing-key.sh [options]

Creates a local Android release keystore. The keystore is never committed.

Options:
  --keystore PATH   Output keystore path.
  --repo OWNER/REPO GitHub repository for secret upload.
  --upload          Upload the keystore and signing values with gh.
  -h, --help        Show this help.

Without --upload, the script only creates and verifies the local keystore.
USAGE
}

die() {
    printf 'Error: %s\n' "$1" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "Required command not found: $1"
}

repo_name=""
keystore_path=""
upload=false

while (($# > 0)); do
    case "$1" in
        --keystore)
            (($# >= 2)) || die "--keystore requires a path"
            keystore_path="$2"
            shift 2
            ;;
        --repo)
            (($# >= 2)) || die "--repo requires OWNER/REPO"
            repo_name="$2"
            shift 2
            ;;
        --upload)
            upload=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            die "Unknown option: $1"
            ;;
    esac
done

require_command keytool
require_command openssl
require_command base64

if [[ "$upload" == true ]]; then
    require_command gh
    [[ -n "$repo_name" ]] || repo_name="$(gh repo view --json nameWithOwner --jq '.nameWithOwner')"
    [[ -n "$repo_name" ]] || die "Could not determine the GitHub repository"
    gh repo view "$repo_name" --json nameWithOwner --jq '.nameWithOwner' >/dev/null
fi

[[ -t 0 && -t 1 ]] || die "Interactive terminal input is required for password entry"

user_home="${HOME:?HOME must be set}"
data_root="${XDG_DATA_HOME:-"$user_home/.local/share"}"
keystore_path="${keystore_path:-"$data_root/futon/futon-release.jks"}"

if [[ -e "$keystore_path" ]]; then
    die "Refusing to overwrite existing keystore: $keystore_path"
fi

mkdir -p "$(dirname "$keystore_path")"
umask 077

read -r -p "Key alias [futon-release]: " alias_input
key_alias="${alias_input:-futon-release}"
[[ -n "$key_alias" ]] || die "Key alias must not be empty"

read -r -s -p "Keystore password: " store_password
printf '\n'
[[ -n "$store_password" ]] || die "Keystore password must not be empty"

read -r -s -p "Confirm keystore password: " store_password_confirm
printf '\n'
[[ "$store_password" == "$store_password_confirm" ]] || die "Keystore passwords do not match"

read -r -s -p "Key password (empty = keystore password): " key_password
printf '\n'
if [[ -z "$key_password" ]]; then
    key_password="$store_password"
fi

trap 'unset store_password store_password_confirm key_password keystore_b64' EXIT

keytool -genkeypair \
    -keystore "$keystore_path" \
    -storetype JKS \
    -storepass "$store_password" \
    -keypass "$key_password" \
    -alias "$key_alias" \
    -keyalg RSA \
    -keysize 4096 \
    -sigalg SHA256withRSA \
    -validity 10000 \
    -dname "CN=Futon Release,O=Futon,C=DE" \
    -noprompt >/dev/null

test -s "$keystore_path"
certificate_fingerprint="$(
    keytool -list -v \
        -keystore "$keystore_path" \
        -storetype JKS \
        -storepass "$store_password" |
        awk -F': ' '/^[[:space:]]*SHA256:/{print $2; exit}'
)"
keystore_sha256="$(openssl dgst -sha256 "$keystore_path" | awk '{print $2}')"

if [[ "$upload" == true ]]; then
    read -r -p "Upload four signing secrets to $repo_name? [y/N] " upload_confirmation
    [[ "$upload_confirmation" =~ ^[Yy]$ ]] || die "Upload cancelled; local keystore was kept"

    keystore_b64="$(base64 "$keystore_path" | tr -d '\n')"
    printf '%s' "$keystore_b64" | gh secret set KEYSTORE_FILE --repo "$repo_name"
    printf '%s' "$store_password" | gh secret set KEYSTORE_PASSWORD --repo "$repo_name"
    printf '%s' "$key_alias" | gh secret set KEY_ALIAS --repo "$repo_name"
    printf '%s' "$key_password" | gh secret set KEY_PASSWORD --repo "$repo_name"
fi

printf 'Created release keystore: %s\n' "$keystore_path"
printf 'Key alias: %s\n' "$key_alias"
printf 'Certificate SHA-256: %s\n' "${certificate_fingerprint:-unavailable}"
printf 'Keystore file SHA-256: %s\n' "$keystore_sha256"
if [[ "$upload" == true ]]; then
    printf 'GitHub Actions secrets uploaded to: %s\n' "$repo_name"
else
    printf 'GitHub upload: skipped, rerun with --upload when ready\n'
fi
