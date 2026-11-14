# adnada: curated channels — small home experiment

Recorded 14 November 2026. **No production deployment and no Android code changes in this note.**

## Decision

Keep adnada a **standalone Fire Stick player**. Keep asmr a separate reader. Use an optional, owner-controlled curator on the existing Ubuntu box to prepare YouTube selections. Prefer YouTube playlists, which SmartTube already displays and can pin in its sidebar, before writing a custom media-feed API.

Do **not** build an agent framework or put OpenRouter credentials in the APK. The curator can be a simple on-demand / scheduled Python process, using a small LLM via OpenRouter only to propose search directions or classify search results. Real videos come from YouTube; validate their actual IDs and metadata. Its state is local, explicit and reversible.

## Channels

- **Discover:** broad exploratory videos deliberately chosen across unfamiliar subjects, not linked to the most recent click. Learn from explicit 👍/👎 and a **Pivot** request. Diversify topics and creators; impose recency/repetition limits. Clicks and watch time are **not** positive signals. The current app-side Discover v0 in PR #2 is an offline, search-prompt prototype, not the final playlist curator.
- **Otto:** long, slow, calming nature/landscape videos with gentle music, minimal animals, no close animal shots or barking, no loud narration/jumps, avoid snowy footsteps or people walking on snow. A dog-safe preference profile: one failure can exclude a video, and Otto's likes/dislikes must never affect Discover. Curated video metadata can suggest suitability but **cannot guarantee the actual footage/sound**; owner feedback and manual acceptance matter.
- Extra lists only when there is a real use case. No default algorithm/Home feed dependency.

## Minimal path

1. **Prove native playlists:** create one unlisted **Otto** playlist and one **Discover** playlist, open/pin them in the current SmartTube/Fire Stick UI. Confirm they refresh when changed and preserve normal playback.
2. **Make the curator write them:** on Ubuntu, read public YouTube search/video metadata using the owner's YouTube Data API configuration. Use a separately authorised YouTube account and limited OAuth to manage playlists; keep refresh tokens local and backed up. The current public API key alone cannot write playlists. Quota limits apply. Avoid maintaining the same content twice, and use careful replace/reorder logic.
3. **Optional small model:** send bounded search terms and candidate metadata, not watch history or account tokens, to OpenRouter. Generate search terms and curate variety, but keep deterministic dedupe, exclusions, and explicit feedback. Server API secrets stay on Ubuntu, not in repo/TV/ChatGPT.
4. **Controls:** first prove that normal playlist viewing is useful. If TV-driven **Pivot** and 👍/👎 are worth the extra code, add just those actions to the existing ADNADA menu to call a private Ubuntu endpoint; otherwise an owner-only local web page is sufficient. Don't confuse YouTube's own thumbs with curator feedback.
5. **Fallback:** if the home service is down, cached/previously published playlists continue to work. If playlist OAuth/quota/platform limits prove onerous, switch to a tiny authenticated read-only manifest of selected YouTube IDs, consumed using existing SmartTube card grids, **only then** justify writing custom app/network code.

## Privacy, reliability, and constraints

- Playback remains YouTube-based through the existing SmartTube player. The curator does **not** download/transcode/relay video. Video URLs are normal YouTube IDs.
- Curating search candidates cannot fully isolate Google account watch/search history or change how YouTube ranks results. Prefer starting in curated playlists, disabling autoplay/suggestions as desired, and keeping any service queries independent of the viewing account.
- Playlist write operations need Google OAuth. A read-only YouTube API key is insufficient. OAuth scopes and changes should be human-approved. Use a dedicated curator account if convenient and avoid submitting private watching data.
- Do not run the curator on asmr's current server, modify live databases, or expose new services to the internet by default. Keep it independent and opt-in.
- Use existing GitHub/Codex workflow; a disposable Ubuntu project is fine, but **do not create another repository or source package until we prove the native-playlist option**.
- User accepts a **clean uninstall and reinstall** of the original debug-signed Fire Stick test version. No need to change Android application ID to preserve the old installation. Establish permanent signing key before the next build. A fresh Android installation may lose settings, which the user explicitly accepts.

## First acceptance test

- Pin both curated playlists in adnada's unmodified interface.
- Play several Otto videos on the actual TV; assess calming pace, animals, sudden sounds, snow footsteps and advertisements.
- Confirm Discover surfaces worthwhile and genuinely varied material, and that updating a playlist changes its contents on the TV.
- Confirm the curator does not inadvertently optimise for repeated attention or drag one accidental topic into future lists.
- Determine whether true TV-remote Pivot/feedback justifies the **only** new client code. Keep PR #2 isolated pending this decision.

No claim yet that autoplay-free, fully ad-free, dog-calming playback is guaranteed by these filters.
