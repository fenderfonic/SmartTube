# adnada — personal Fire Stick player

Fork of SmartTube with Discover. Application ID: `dev.fenderfonic.smarttube`, label: **adnada**. Upstream version pinned in `.github/adnada-upstream-version`.

## Development APKs

The **Personal Android TV test APK** workflow builds an unsigned-for-release/test APK with an ephemeral CI debug signing key. Test APKs often require uninstall/reinstall and are **not eligible for in-app upgrades**. Never publish these as an update.

## Normal in-app updates

SmartTube's existing updater is enabled for adnada and reads **only**:

https://github.com/fenderfonic/SmartTube/releases/download/adnada-current/adnada-update.json

The **Publish adnada update** workflow builds a signed universal APK, validates package and signature, and uploads `adnada-universal.apk` plus `adnada-update.json` to the fixed `adnada-current` GitHub Release. The updater checks version codes and offers to install newer builds, preserving app data.

**One-time setup (Mac or Linux, no JDK required on Mac):**

```sh
bash <(curl -fsSL https://raw.githubusercontent.com/fenderfonic/SmartTube/master/scripts/bootstrap-adnada-signing.sh)
```

This creates `~/.config/adnada/adnada-release.p12` and `signing-pass.txt` with private permissions and configures the four GitHub Actions secrets using existing `gh` authentication. **Back up both private files securely.** Never commit, upload to a chat or regenerate the keystore after shipping. The bootstrap can be rerun to re-upload the existing identity.

Once secrets are configured, dispatch **Publish adnada update** on `master`:

```sh
gh workflow run adnada-publish.yml -R fenderfonic/SmartTube --ref master
```

The **first permanently signed release requires a clean uninstall of any prior CI debug build** because Android disallows upgrading across different signing identities. Every subsequent release with the same key can update **in place** via the app's own updater.

To install the initial signed release from the Mac (only after the workflow successfully publishes):

```sh
curl -fL -o /tmp/adnada-universal.apk https://github.com/fenderfonic/SmartTube/releases/download/adnada-current/adnada-universal.apk
adb connect 192.168.0.214:5555
adb -s 192.168.0.214:5555 uninstall dev.fenderfonic.smarttube
adb -s 192.168.0.214:5555 install /tmp/adnada-universal.apk
```

Thereafter open adnada normally. Check the update notification or use the built-in manual check.

## Upstream sync

**Check upstream SmartTube releases** runs daily via GitHub Actions, compares current pinned release against the latest upstream beta and creates a reviewable pull request if a 3-way patch applies. Merge conflicts fail the check: no silent overwrite, no auto-merge or auto-release. After CI and playback acceptance, merge the PR and run the publisher to make the update available in the app.

Source: https://github.com/yuliskov/SmartTube
