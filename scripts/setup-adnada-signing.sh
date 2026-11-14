#!/usr/bin/env bash
# One-time local signing setup for the owner's personal SmartTube fork.
# Key creation stays on this Mac; only encrypted GitHub Actions secrets are uploaded.
set -euo pipefail
umask 077

REPO="fenderfonic/SmartTube"
ALIAS="adnada"
KEY_DIR="$HOME/.config/adnada"
KEYSTORE="$KEY_DIR/adnada-release.p12"

for program in keytool gh base64 tr; do
  if ! command -v "$program" >/dev/null 2>&1; then
    printf 'Missing %s. Install Java 17 and GitHub CLI (brew install gh) first.\n' "$program" >&2
    exit 1
  fi
done

if ! gh auth status >/dev/null 2>&1; then
  echo "GitHub CLI is not logged in. Run: gh auth login" >&2
  exit 1
fi
if ! gh repo view "$REPO" >/dev/null 2>&1; then
  echo "Cannot access $REPO through gh. Check the authenticated GitHub account." >&2
  exit 1
fi

mkdir -p "$KEY_DIR"
chmod 700 "$KEY_DIR"
if [[ -L "$KEYSTORE" ]]; then
  echo "Refusing symlinked keystore file: $KEYSTORE" >&2
  exit 1
fi

printf 'Private signing key: %s\n' "$KEYSTORE"
if [[ -f "$KEYSTORE" ]]; then
  printf 'Existing key found. It will NOT be overwritten.\n'
else
  printf 'Creating a new permanent key. Keep its password and an encrypted backup.\n'
fi

read -r -s -p "Enter the keystore password (12+ characters): " signing_password
printf '\n'
if [[ "${#signing_password}" -lt 12 ]]; then
  echo "Password must be at least 12 characters." >&2
  exit 1
fi
trap 'unset signing_password || true' EXIT

if [[ ! -f "$KEYSTORE" ]]; then
  read -r -s -p "Repeat the password: " confirmation
  printf '\n'
  if [[ "$signing_password" != "$confirmation" ]]; then
    echo "Passwords do not match. Nothing generated." >&2
    exit 1
  fi
  unset confirmation

  # Do not pass passwords as process arguments or print them.
  ADNADA_SIGNING_PASSWORD="$signing_password" keytool -genkeypair \
    -alias "$ALIAS" \
    -keystore "$KEYSTORE" \
    -storetype PKCS12 \
    -keyalg RSA -keysize 3072 -validity 10000 \
    -dname "CN=adnada personal, O=Personal, C=GB" \
    -storepass:env ADNADA_SIGNING_PASSWORD \
    -keypass:env ADNADA_SIGNING_PASSWORD
  chmod 600 "$KEYSTORE"
fi

if ! ADNADA_SIGNING_PASSWORD="$signing_password" keytool -list \
  -keystore "$KEYSTORE" -storetype PKCS12 -alias "$ALIAS" \
  -storepass:env ADNADA_SIGNING_PASSWORD >/dev/null; then
  echo "Could not verify the existing keystore and alias. Check the password." >&2
  exit 1
fi

printf 'Uploading the four encrypted GitHub Actions secrets to %s...\n' "$REPO"
base64 < "$KEYSTORE" | tr -d '\n' | gh secret set SMARTTUBE_KEYSTORE_BASE64 -R "$REPO"
printf '%s' "$signing_password" | gh secret set SMARTTUBE_STORE_PASSWORD -R "$REPO"
printf '%s' "$ALIAS" | gh secret set SMARTTUBE_KEY_ALIAS -R "$REPO"
printf '%s' "$signing_password" | gh secret set SMARTTUBE_KEY_PASSWORD -R "$REPO"

printf '\nSigning certificate (public fingerprint only):\n'
ADNADA_SIGNING_PASSWORD="$signing_password" keytool -list -v \
  -keystore "$KEYSTORE" -storetype PKCS12 -alias "$ALIAS" \
  -storepass:env ADNADA_SIGNING_PASSWORD | grep 'SHA256:'

printf '\nSecret names now configured:\n'
gh secret list -R "$REPO" | grep 'SMARTTUBE_' || true
printf '\nDone. Back up %s safely, and retain its password.\n' "$KEYSTORE"
echo 'Do NOT delete this keystore or regenerate it for future upgrades.'
echo 'No APK was installed and no Fire Stick data was changed.'
