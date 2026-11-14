# SmartTube Personal

Independent Android TV build of SmartTube using the `stbeta` flavour.

- Android application ID: `dev.fenderfonic.smarttube`, allowing side-by-side installation.
- TV launcher name: **SmartTube Personal**.
- Upstream in-app update feed disabled. Install updates only from our own APK artifacts.
- No changes to playback or SponsorBlock functionality.

## Test build

The **Personal Android TV test APK** workflow generates a sideloadable universal APK artifact. On GitHub, open Actions, select a successful run, then download **smarttube-personal-test** from Artifacts. Unzip and sideload on a test Android TV.

This *test* APK is signed with an ephemeral Android debug key on the CI runner. Future runs may not update it in place. Do not rely on test signing for production use.

## Permanent signing

**One-time Mac setup (recommended).** The private signing keystore must live
outside Git and outside this chat. The reviewed helper creates it on the Mac,
uploads four encrypted repository secrets through an authenticated GitHub CLI,
and prints only the public certificate fingerprint.

Prerequisites: JDK (`keytool`) and GitHub CLI (`gh` authenticated as the
repository owner). On macOS, install `gh` with `brew install gh`, then
`gh auth login` if needed. Review the helper script before running it:

```sh
curl -fsSLo "$HOME/Downloads/setup-adnada-signing.sh" \
  https://raw.githubusercontent.com/fenderfonic/SmartTube/master/scripts/setup-adnada-signing.sh
less "$HOME/Downloads/setup-adnada-signing.sh"
bash "$HOME/Downloads/setup-adnada-signing.sh"
```

The script will interactively prompt for a new keystore password and **reuse
an existing key rather than replace it**. Private key:
`~/.config/adnada/adnada-release.p12`. Keep a protected offline backup
and a securely stored copy of the password. Losing the key prevents normal
upgrades to already-installed releases. The GitHub Actions secrets are:

- `SMARTTUBE_KEYSTORE_BASE64`: base64-encoded private keystore
- `SMARTTUBE_STORE_PASSWORD`: private keystore password
- `SMARTTUBE_KEY_ALIAS`: `adnada`
- `SMARTTUBE_KEY_PASSWORD`: same password (PKCS12)

Do not paste any secret into chat, source control, a PR or issue.

**Run a signed build:** GitHub Actions → **Personal Android TV signed
release** → **Run workflow**, and select `feat/discover-self-drive` to
build Discover (or `master` for the unchanged baseline). The workflow
exists on default branch `master` and accepts the selected branch as its
source. Download the resulting signed *universal APK* under Artifacts;
validate the selected branch and signer before sideloading.

**Existing test installation:** the first real Fire Stick APK is debug-signed
with a CI-runner key, not this permanent one. Android will reject an upgrade
signed by a different key (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Review
or record app settings before uninstalling the test APK. Uninstalling will
normally erase app-local preferences, sign-in and cached data. To make the
one-time switch, uninstall the old package only **after** the new signed APK
has been built and verified, then install the signed APK with ADB. Future
signed releases using the same key should be eligible for in-place upgrades
when Android version codes permit.

Do not assume `adb backup` can reliably preserve app data across a signing
identity change. The old working installation is left untouched by these
GitHub build instructions.
## Review and testing

First confirm CI succeeds, then test remote navigation, playback, ad behaviour, SponsorBlock, search, and optionally login and casting. Review every upstream dependency and submodule change before updating; an independent signature alone does not establish source safety. YouTube may disrupt third-party clients. Retain the MIT licence and upstream copyright notices.

Source: https://github.com/yuliskov/SmartTube
