# adnada — SmartTube Personal fork project log

Status: **running successfully on the owner's Android-based Amazon Fire Stick**. **Owner approved the name `adnada` and the proposed teal lowercase-a/play-triangle logo direction on 13 November 2026.** Rebranding has not yet been implemented in the installed APK.

## Outcome: 13 November 2026

- Source: https://github.com/fenderfonic/SmartTube, an MIT-licensed fork of yuliskov/SmartTube.
- Initial build PR: https://github.com/fenderfonic/SmartTube/pull/1 (draft, branch `personal-apk-v1`, not yet merged).
- Personal beta application ID: `dev.fenderfonic.smarttube`, separate from the official client.
- Display name currently installed: **SmartTube Personal**.
- Removed original Firebase/Google Services build setup after a package-name mismatch on CI.
- Disabled upstream app's APK update feed for this personal flavour; all updates should come from reviewed personal builds.
- GitHub Actions test workflow https://github.com/fenderfonic/SmartTube/actions/runs/43415556857 completed successfully, including identity and APK signature checks.
- Test artifact: `smarttube-personal-test-2` (temporarily debug-signed, not a permanent upgrade key).
- User enabled Fire Stick ADB debugging, connected via LAN, used `adb install`, and **confirmed working playback and ad-free viewing** on the actual Fire Stick.

## Agreed boundaries

The Fire Stick is a **standalone player**, not an asmr casting target. No automatic TV discovery, casting, remote ADB controls or control service is needed.

The related private project `fenderfonic/asmr` may later provide same-device in-reader YouTube playback to iPhone Safari and desktop browsers, potentially via a private Linux playback sidecar. This is not code shared with the Android TV client and must be proven separately. It is **not another SmartTube fork**: use asmr's existing repository, with an optional isolated playback service if the proof of concept demands one. Do not create a new repo by default. Planning PR: https://github.com/fenderfonic/asmr/pull/65.

## Backlog for a later review

- [ ] Create and securely back up one permanent signing keystore locally; configure GitHub Actions secrets.
- [ ] Produce the first reproducible signed APK and validate future in-place upgrade behaviour.
- [ ] Review and merge initial build PR #1 with explicit acceptance.
- [x] Approve new name and original logo direction: **adnada** (ads: nada), lowercase, teal 'a' with play triangle. Owner approved on 13 November 2026. The image is a concept, not an Android launcher asset yet. Reject `hush` because an existing TV/video product uses that name.
- [ ] Implement approved brand in app name, banner, launcher icon and splash on a separate reviewed PR. Keep existing installed test APK untouched until signed release process is ready.
- [ ] Distinctive small TV-launcher icon, banner and splash; avoid copying YouTube trademark artwork. Keep original MIT licence and copyright notices, retaining attribution to upstream.
- [ ] Decide whether a low-maintenance upstream sync/review workflow is worthwhile.
- [ ] Review Android/FireOS compatibility on any additional devices before claiming support.

## Engineering principles

GitHub source of truth; prefer Codex and PR review for code. KISS, no code packages. Avoid security claims from fork status alone. Review upstream source changes, submodule hashes, dependencies and update behaviours. No live app/TV changes without the owner's go-ahead. A test artifact's ephemeral debug key is **not** a production signing identity. Do not commit signing keys or passwords.

## Experiment: Discover (13 November 2026)

- [Draft PR #2](https://github.com/fenderfonic/SmartTube/pull/2), stacked on PR #1, adds a native sidebar **Discover** section.
- It provides eight broad exploratory searches, Pivot for variety, explicit 👍/👎 topic feedback and local short-term prompt-repeat suppression. Watch history and clicks are not inferred as preferences. No new TV UI or player changes.
- First cut deliberately uses a curated query bank, not a remote LLM. Dynamic prompts via private Ubuntu + OpenRouter can be evaluated once the TV UX is accepted.
- [Android APK build #43490997667](https://github.com/fenderfonic/SmartTube/actions/runs/43490997667) **passed**; artifact `smarttube-personal-test-5` uploaded. This is CI acceptance only, not Fire Stick acceptance.
- **Do not blindly upgrade the working original test APK**: test builds are debug-signed with ephemeral CI keys. Future in-place upgrades require a permanent signing key; a reinstall may erase the app's local data.
- Owner acceptance pending: Discover sidebar placement, prompt -> YouTube search -> playback, back navigation, Pivot, feedback and reset behaviour.
