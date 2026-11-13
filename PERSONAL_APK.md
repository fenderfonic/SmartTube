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

Generate and securely back up an Android signing keystore on your own machine. Do not upload it in chat or commit it.

On a Mac with JDK:

```sh
keytool -genkeypair -keystore smarttube-personal.jks -alias smarttube-personal -keyalg RSA -keysize 3072 -validity 10000
base64 -i smarttube-personal.jks | tr -d '\n' | pbcopy
```

Under repository Settings > Secrets and variables > Actions, configure:

- `SMARTTUBE_KEYSTORE_BASE64` (base64-encoded keystore)
- `SMARTTUBE_STORE_PASSWORD`
- `SMARTTUBE_KEY_ALIAS` (e.g. smarttube-personal)
- `SMARTTUBE_KEY_PASSWORD`

Then manually run **Personal Android TV signed release**. Preserve this signing key for every upgrade. The resulting APK is available under the workflow's Artifacts and is not publicly published as a release.

## Review and testing

First confirm CI succeeds, then test remote navigation, playback, ad behaviour, SponsorBlock, search, and optionally login and casting. Review every upstream dependency and submodule change before updating; an independent signature alone does not establish source safety. YouTube may disrupt third-party clients. Retain the MIT licence and upstream copyright notices.

Source: https://github.com/yuliskov/SmartTube
