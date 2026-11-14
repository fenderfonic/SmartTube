#!/usr/bin/env bash
# Bootstrap permanent adnada signing on macOS/Linux using OpenSSL, without Java.
set -euo pipefail
umask 077
command -v openssl >/dev/null || { echo "OpenSSL is required"; exit 1; }
command -v gh >/dev/null || { echo "GitHub CLI is required"; exit 1; }
gh auth status >/dev/null
repo="fenderfonic/SmartTube"
dir="$HOME/.config/adnada"
keystore="$dir/adnada-release.p12"
passfile="$dir/signing-pass.txt"
mkdir -p "$dir"
if [[ ! -f "$keystore" ]]; then
  if [[ -e "$passfile" ]]; then
    echo "Password exists but keystore is missing. Restore your backup rather than generating a different signing identity." >&2
    exit 1
  fi
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  password="$(openssl rand -hex 24)"
  openssl req -new -newkey rsa:3072 -nodes -x509 -days 36500 \
    -subj "/CN=adnada personal signer/O=adnada" \
    -keyout "$tmp/key.pem" -out "$tmp/cert.pem" 2>/dev/null
  openssl pkcs12 -export -inkey "$tmp/key.pem" -in "$tmp/cert.pem" \
    -name adnada -out "$keystore" -passout "pass:$password"
  printf '%s' "$password" > "$passfile"
  echo "Generated one permanent signing identity."
else
  [[ -s "$passfile" ]] || { echo "Signing password missing: $passfile" >&2; exit 1; }
  echo "Reusing existing signing identity."
fi
python3 -c 'import base64,sys;sys.stdout.write(base64.b64encode(open(sys.argv[1],"rb").read()).decode())' "$keystore" | gh secret set SMARTTUBE_KEYSTORE_BASE64 -R "$repo"
gh secret set SMARTTUBE_STORE_PASSWORD -R "$repo" < "$passfile"
printf '%s' adnada | gh secret set SMARTTUBE_KEY_ALIAS -R "$repo"
gh secret set SMARTTUBE_KEY_PASSWORD -R "$repo" < "$passfile"
printf '\nGitHub signing secrets configured for %s.\n' "$repo"
printf 'Back up BOTH files offline: %s and %s\n' "$keystore" "$passfile"
echo "Keep them private. Never commit or paste their contents."
