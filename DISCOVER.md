# adnada Discover — v0 self-directed search shelf

**Only for the Fire Stick/Android TV fork.** No asmr dependency.

## What it does

- Adds a **Discover** section to SmartTube's existing sidebar. It opens a native settings-style grid of exploration prompts, without replacing any UI or player components.
- Eight choices per view, drawn from twenty broad subject families and 80 curated searches. Different subject families appear together, deliberately avoiding a one-topic feed.
- Select a prompt to open a normal SmartTube **YouTube search**. Videos are real YouTube search results; none are generated or suggested by a claimed LLM.
- **Pivot** creates a new spread of prompts. A bounded 48-item *local list of prompts shown* makes immediate repetition less likely.
- **👍 / 👎** modify only the most recently selected subject family (clamped weights from -3 to +3). A click or watch does **not** count as an endorsement.
- **Reset** deletes only the feature's own preferences. It does not modify the app's watch history, subscriptions or playlists.
- Everything works without accounts, API keys, background tasks or an Ubuntu box.
- New sidebar section is pinned once on an existing installation, and can then be removed using normal sidebar customisation.

## Constraints and honest limitations

This is a **practical first slice**, not yet LLM-assisted: the current search-prompt bank is editorially curated, with deterministic weight rules and randomized variety. It does **not** change how YouTube ranks results within a search, or stop Google's own account-level watch/search history from influencing other Google experiences. SmartTube's own search-history controls should be used if the owner wants to reduce local query retention.

No OpenRouter key goes into the public fork or Android APK. The user's ChatGPT subscription does not provide an API key. If dynamic LLM-generated query prompts later prove worthwhile, use an **opt-in private endpoint on the existing Linux host**, with the OpenRouter key kept in its server environment, and let the TV fetch only small, validated prompts. Treat that as a separate PR after real TV acceptance. The Fire Stick app must still work when Linux is unavailable.

## Test and acceptance

1. CI: build universal APK and verify Android app identity and signature.
2. On Fire Stick, confirm **Discover** appears on the existing sidebar; select a prompt and verify YouTube search/results/video playback are unchanged.
3. Return to Discover; **Pivot** should present fresh cross-domain choices. Try several times; verify repeat suppression.
4. Open a prompt, return, give 👍 then 👎. Verify preferences only change after feedback and don't lock into the same subject.
5. Reset Discover; check that unrelated subscriptions and viewing history are untouched.
6. Leave the rest of SmartTube, player UI and branding alone.

**Do not deploy or merge automatically.** This PR is a test build and requires the owner's TV acceptance.
