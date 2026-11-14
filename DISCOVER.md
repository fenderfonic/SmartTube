# adnada Discover v2

Discover stays in SmartTube's native TV settings grid. Explore opens native YouTube search;
playback, accounts, subscriptions and normal navigation keep their existing behaviour.
Twelve directions per selection, Focus by subject/region, Pivot, and explicit thumbs all
run locally. No clicks, searches or watches change preference weights. No telemetry or
feedback upload. No YouTube OAuth or playlist changes.

## Small architecture

`curator/catalogue.json` contains 144 concrete subjects across 18 families. Six editorial
search lenses and six geographical search lenses yield **5,184 unique query directions**.
The standard-library Python compiler rotates a deterministic 216-entry page every six
London hours, with round-robin subject diversity, stable SHA-256 seeds and deduplication.
The default pool has no repeated exact query across eight consecutive slots (two days).
A finite catalogue eventually cycles; similar subjects can recur with different lenses.
Edit the catalogue to add real new subjects; it is deliberately easy to throw away.

The TV caches a validated schema-v2 pool, retaining the last good version if the host,
network or API fails. A reproducibly generated 216-entry offline pool ships in the APK.
Unknown schemas, oversized payloads, malformed fields and duplicate queries are rejected.
The host only serves `GET /pool.json`, never state, configuration, keys or directory lists.
Android permits only HTTP URLs with literal RFC1918 IPv4 addresses, exactly `/pool.json`,
no credentials/query/fragment, and no redirects. This avoids public endpoints and DNS
rebinding. Plain HTTP is suitable only for the owner's trusted private network.

Account preferences, Focus, sequence and recent queries have separate local namespaces,
using a SHA-256 digest of the selected SmartTube account's id/email/name; anonymous has
its own namespace. The shared pool and host URL are device-wide. Nothing identifying an
account is sent to the host. Renaming an account may start a new preference namespace.
Old v0 global feedback is deliberately not assigned to an arbitrary account.

Selection uses revision + local sequence as its seed, bounded explicit topic nudges and
a 96-query shown history. It first reserves three neutral/unfamiliar directions, then
favours the selected Focus, then fills from other subjects. Focus can combine a subject
and region; **Explore · clear Focus** removes both. **Focus · last subject** targets the
last explored query's topic. With exhausted/downvoted pools it relaxes suppression to
remain useful. Thumbs explicitly target the last subject named in the button. Reset
clears only this account's Discover preferences; host settings and SmartTube data remain.

## Ubuntu installation (manual, no deployment performed)

Requires Python **3.9+**, system zoneinfo/tzdata, systemd user services, and this checkout
at `~/adnada-discover/SmartTube` (adjust unit `WorkingDirectory` if placed elsewhere).
No pip packages, database, containers or new accounts.

```sh
cd ~/adnada-discover/SmartTube
python3 -m unittest discover -s curator/tests -v
install -d -m 700 ~/.config/adnada-discover ~/.local/state/adnada-discover
install -m 600 curator/config.example.json ~/.config/adnada-discover/config.json
# Replace this with the Ubuntu host's existing private LAN address.
printf 'DISCOVER_BIND=192.168.1.20\n' > ~/.config/adnada-discover/environment
chmod 600 ~/.config/adnada-discover/environment
python3 curator/curator.py --directory ~/.local/state/adnada-discover \
  --config ~/.config/adnada-discover/config.json
install -d ~/.config/systemd/user
cp curator/systemd/* ~/.config/systemd/user/
systemctl --user daemon-reload
systemctl --user enable --now adnada-curator.timer adnada-pool.service
systemctl --user list-timers adnada-curator.timer
journalctl --user -u adnada-curator.service -u adnada-pool.service
```

The timer runs at **00:00, 06:00, 12:00, 18:00 Europe/London**, including DST changes;
`Persistent=true` catches up once after host downtime. Generation holds a nonblocking
file lock to prevent overlapping reservations/writes. Both state and pool use fsync and
same-directory atomic replacement. A failed generation leaves the last-good pool intact.
User services must remain running while logged out; if the host does not already enable
user lingering, its administrator can enable it for this user with `loginctl enable-linger`.
No SSH, firewall, router or host configuration is changed by this PR. Bind only the existing
LAN address and restrict the existing host firewall to the Fire Stick/private subnet.
Do not forward the port or create public DNS/access. No unnecessary inbound port besides
the private pool port is needed. Stop with `systemctl --user disable --now
adnada-curator.timer adnada-pool.service`.

On the TV, open **Discover → Pool · … · private host settings → Set private host URL**
and enter `http://192.168.1.20:8766/pool.json`. Initial rows remain usable while a background
fetch runs. Pivot uses the newly cached pool after completion. Automatic attempts are
limited to once per 15 minutes while visiting Discover; manual refresh is available.
Disable host retains the cache. Deleting/corrupting the cache returns to the bundled pool.
No service needs to accept writes from the TV.

## Optional OpenRouter novelty: disabled by default

The normal rotation makes **zero API calls**. A single optional request can run only when
unused candidates drop below `replenish_below` (default 100). It can add up to 128 retained
novelty entries. There is no model request from the Fire Stick, including Focus, Pivot or
feedback. No account/preferences/history are included in the novelty prompt.

To opt in, set `daily_calls` to **1**, choose an explicit text model ID in the private
config and add `OPENROUTER_API_KEY=...` to the private mode-600 environment file. No default
model is selected and no real API call was made during development. Keep secrets outside
the checkout; never put a key in the URL, APK, JSON pool or git.

Controls:

- Daily calls are constrained to 0 or 1 per **London calendar day**, across restarts.
- The attempt and **$0.01 reservation** are atomically persisted before network I/O.
  Timeouts, malformed responses and errors consume the daily attempt; there are no retries.
- Lifetime reserved spend defaults to **$0.25**, with an absolute configuration ceiling of
  **$1**. Reservations are never refunded, even for unknown usage, to fail closed.
- Each call caps output at **512 tokens**, uses a small fixed prompt, and restricts provider
  prices to **$0.50/M input**, **$1/M output**, **$0 per request**, with fallbacks disabled
  and required parameter support. The reservation is conservative for this fixed prompt.
- Actual returned prompt/completion token counts and dollar cost are logged and persisted
  in `state.json`, even when content validation fails. Missing usage remains unknown and
  keeps the full reservation. Never delete/reset state to work around a budget limit.
- A separate provider-side key credit limit is useful if already available; the local cap
  controls this script, not other programs using that key.

Provider price ceilings follow [OpenRouter's provider routing documentation](https://openrouter.ai/docs/guides/routing/provider-selection).
The replayable deterministic baseline is guaranteed; optional model text is not guaranteed
reproducible by the provider, even with seed/temperature zero. Persisted novelty is an input
to subsequent deterministic compilation. Keep `state.json` and the catalogue for replay.

```sh
# Reproduce the bundled baseline (no model or real clock involved):
python3 curator/curator.py --directory /tmp/discover-replay \
  --at 2026-11-14T00:00:00+00:00 --seed adnada-v2
cmp /tmp/discover-replay/pool.json common/src/main/assets/discover-pool-v2.json
```

## Quality boundaries and acceptance

Queries favour specific crafts, field research, museums, overlooked histories and
connections across disciplines. Geographical labels are search lenses, not claims of
origin. The engine diversifies search directions, not YouTube's ranked results; it cannot
guarantee diverse creators or remove clickbait from Google's results without a larger
video-selection system. It never optimises watch time or engagement. Search history may
still be retained by SmartTube/YouTube under their existing settings.

CI runs curator tests, Android selection/cache tests, builds the universal debug APK,
and checks package `dev.fenderfonic.smarttube` and APK signature. Debug CI keys can differ
from the installed key: an APK artifact is for review and manual testing, not permission
to uninstall or replace a working installation. The separate signed-release workflow is
unchanged. No merge, uninstall or Fire Stick deployment is performed.

Owner acceptance after choosing an appropriate signed build:

1. Discover → Explore → native search → normal video → back navigation.
2. Repeated Pivot: twelve varied directions, no immediate exact query repeats.
3. Focus subject, region, then combined; confirm unfamiliar rows remain; clear Focus.
4. Explicit thumbs changes only the selected account; ordinary searches/watches do not.
5. Switch accounts and anonymous: independent feedback/Focus/recent history; switch back.
6. Configure host, fetch a new revision, stop host, restart app: last-good remains usable.
7. Invalid/oversized/schema-v3 host JSON leaves last-good; clean install offline uses bundle.
8. Reset discovery without changing subscriptions/history/accounts/player behaviour.
