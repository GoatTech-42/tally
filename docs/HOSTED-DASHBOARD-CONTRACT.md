# GoNuts hosted dashboard: integration contract for the mod (protocol 1)

> **Retired:** the hosted dashboard is no longer running and the mod never
> shipped dashboard support. Installations do not contact the retired
> service. This document is kept as the design record.


Date: 2026-10-15. Receiver side implemented, deployed in test phase, and verified with a stand-in client; the
real mod has **no** dashboard support yet, so everything under "What the mod must do" is a client change.
No secrets in this file. Copy it into the mod repository's `docs/` when the client work starts.

## 0. Origins

| purpose | value |
|---|---|
| production browser origin | `https://gonuts.at` (the dashboard page is `https://gonuts.at/dashboard/`) |
| HTTP API base | `https://gonuts.at/dashboard` |
| mod socket | `wss://gonuts.at/dashboard/ws/client` |
| test environment | the same origin while `DASHBOARD_ALLOWED_DISCORD_IDS` restricts sign-in to the owner's Discord account; there is no second host. A client build can be tested against production safely: nothing it does affects other users |

All HTTP is JSON (`Content-Type: application/json`, UTF-8). Error bodies are `{"message": "...", "status": n}`.
Every route lives under `/dashboard`; nothing else on gonuts.at changed.

## 1. User flow (for reference)

1. The user opens `https://gonuts.at/dashboard/` and signs in with Discord (identify scope only).
2. They press **Link client**: the page shows a code like `K7PQ-M3ZD`, valid 5 minutes, one use.
3. In GoNuts the user opens the dashboard link screen and types the code.
4. The mod redeems it (section 2), stores the credential it gets, and connects (section 3).
5. The client appears on the dashboard; the user can rename, pause/resume, refresh, or unlink it.

A pairing code authorises the installation that redeems it. It does not prove ownership of a Minecraft account.

## 2. Pairing

### `POST /dashboard/api/pair/redeem` (no credential)

```json
{
  "code": "K7PQ-M3ZD",
  "protocols": [1],
  "name": "Laptop (main)",
  "build": "0.1.1-staffwatch+26.2-1792094657972",
  "hive": "",
  "capabilities": ["pause", "resume", "snapshot", "send_crash_report"]
}
```

| field | rule |
|---|---|
| `code` | as typed; the server upper-cases, drops the dash and spaces, and forgives `0`→`O`, `1`/`I`→`L` |
| `protocols` | versions the client speaks; must include `1` |
| `name` | ≤ 48 chars, shown on the dashboard; the user can rename it later. Suggest the machine name or account label, never a path |
| `build` | the mod version string, `[0-9A-Za-z.+_-]{0,60}` |
| `hive` | optional grouping label, `[0-9A-Za-z_-]{0,64}` (section 6) |
| `capabilities` | subset of `pause`, `resume`, `snapshot`, `send_crash_report`; the dashboard only offers what is listed |

Responses:

| status | meaning | client action |
|---|---|---|
| **201** | linked: `{"installationId": 12, "credential": "gni1_…", "name": "…", "protocol": 1, "wsUrl": "wss://gonuts.at/dashboard/ws/client", "checkUrl": "https://gonuts.at/dashboard/api/client/check", "heartbeatSeconds": 20, "telemetryMinIntervalMs": 1000, "maxMessageBytes": 32768}` | store `credential` (section 2.1) and connect |
| 400 | malformed code, no supported protocol, bad field | show the message; do not retry the same input |
| 404 | unknown code | the user mistyped or the code is someone else's; ask again |
| 409 | code already used | ask the user for a new code |
| 410 | code expired | ask the user for a new code |
| 429 | too many attempts from this address, or this code was tried too often (`Retry-After` seconds) | wait; do not loop |
| 503 | dashboard disabled | tell the user the dashboard is off |

Limits: 10 redemptions per minute per source address, 60 per minute receiver-wide, 5 attempts per code.
A code is consumed atomically: two simultaneous redemptions yield one 201 and one 409 (verified).

### 2.1 The installation credential

- `gni1_` + 43 url-safe base64 chars. The server stores only its SHA-256; it is shown exactly once, in the 201.
- One credential per installation. Store it in the installation's own local config with restricted permissions,
  next to the local panel PIN, **never** in `tuning.json` or any file a hive shares, never in logs, never in a URL.
- Send it only as `Authorization: Bearer gni1_…` on the socket upgrade and on `/api/client/check`.
- `POST /dashboard/api/client/check` with the bearer answers `200 {"ok":true,"installationId":12,"name":"…","protocol":1}`
  or `401` when revoked/unknown. Use it at startup to decide whether to connect at all.
- Revocation: the user presses **unlink** (or deletes their account). The socket closes with `4403 credential revoked`,
  later connects get `4401`, `check` gets 401. The mod then forgets the credential and shows "not linked";
  re-linking needs a new code. Nothing else changes: trading, the local panel and Discord are unaffected.
- Recovery: if the stored credential is lost (config wiped), the installation simply links again with a new code;
  the old entry stays on the dashboard as offline until the user unlinks it.
- The same credential redeemed on a second machine is not a second installation: the newest socket wins (4409
  to the older one). Each machine must pair separately.

## 3. The socket

`wss://gonuts.at/dashboard/ws/client`, upgrade headers: `Authorization: Bearer gni1_…`, no `Origin` header
(a request carrying `Origin` is treated as a browser and closed 4403). Text frames only, each one JSON object,
≤ 32768 bytes (larger: Jetty closes with 1009).

### 3.1 Handshake

First frame from the mod, within 10 s:

```json
{"type":"hello","protocols":[1],"build":"0.1.1-…","name":"Laptop (main)","hive":"","capabilities":["pause","resume","snapshot"]}
```

Reply:

```json
{"type":"welcome","protocol":1,"installationId":12,"heartbeatSeconds":20,"telemetryMinIntervalMs":1000,"maxMessageBytes":32768,"commands":["pause","resume","snapshot"]}
```

`commands` echoes the capabilities the relay will forward. Any other first frame: close 4400.
Close codes on the way in: `4401` credential unknown/revoked, `4403` browser origin, `1013` relay full (retry
with backoff), `4400` protocol error, `4426`-style refusal is expressed as `4400` with "no supported protocol".

### 3.2 Heartbeat and presence

- Send `{"type":"ping","at":<ms>}` every 20 s; the relay answers `{"type":"pong","at":<ms>}`.
- Any frame counts as life. Silence for 80 s: the relay closes `4408 no heartbeat` and marks the client offline.
- The relay's `last_seen` for the installation is updated from frames (persisted every 30 s).

### 3.3 Reconnect

Exponential backoff with jitter: 1 s, 2, 4, 8, 16, 32, capped at 60 s, each multiplied by a random factor in
[0.7, 1.3]; reset after a connection that lasted ≥ 60 s. Reconnect on any close except `4401`/`4403`
(credential gone: stop and forget the credential) and `4409` (replaced: stop; another process owns this
installation now). Never block trading on the socket: the relay is a viewer, the mod is the authority.

### 3.4 Telemetry (mod → relay)

Send on every meaningful change and at least every 15 s while connected, at most one frame per second (the relay
coalesces faster frames and forwards the newest once a second). Shape, every field optional, unknown fields
ignored, strings bounded (160 chars, 48 for names), numbers clamped:

```json
{
  "type": "telemetry",
  "at": 1792105000000,
  "state": "MONITORING",
  "detail": "watching 12 listings",
  "account": "display label of the account, as the HUD shows it",
  "listed": 7, "slots": 18,
  "bank": 12345678,
  "trades": 3,
  "profit": {"h1": 45000, "h24": 1250000, "source": "booked"},
  "sales": {"h1": 2, "h24": 31},
  "purchases": {"h1": 1, "h24": 28},
  "listings": {"h1": 2, "h24": 30},
  "reprices": {"h1": 0, "h24": 4},
  "build": "0.1.1-…",
  "update": {"available": false, "latest": ""},
  "report": {"prepared": false, "kind": ""},
  "controls": {"pause": true, "resume": false, "snapshot": true, "send_crash_report": false},
  "lastAction": "listed 1x diamond sword",
  "lastActionAt": 1792104970000
}
```

- `state`: one of `SCANNING BID_DESK PREPARING_LIST LISTING MONITORING COOLDOWN PAUSED STOPPED IDLE BUYING REPRICING RECOVERING EVADING UNKNOWN`; anything else is stored as `UNKNOWN`.
- `profit.source`: a short label for where the figure comes from (`booked`, `recorded`, `session`), shown next to it.
  Keep purchases, listings and reprices separate from completed sales; the dashboard shows them in separate rows.
- `report.prepared` + `kind`: a prepared automatic crash report waiting for an explicit send; the dashboard shows a
  notice and, if `send_crash_report` is a capability, a **Send it** button (which goes through the normal sanitized
  bug-report contract on the client; the relay never sees the report).
- `controls`: which commands make sense right now (e.g. `pause` false while already paused). Advisory for the UI.
- What is **not** sent: logs, chat, player names other than the account label, credentials, Java command lines,
  file paths. A `detail`/`lastAction`/`account` that contains a `C:\…`, `/home/…`, `/Users/…` path or anything that
  looks like a token (`gni1_`, `dbay_`, `Bearer `, a 40+ char base64 run) is refused with
  `{"type":"error","message":"… looks like a credential or a local path and was refused"}` and the frame is dropped.

### 3.5 Commands (relay → mod), acknowledgements (mod → relay)

```json
{"type":"command","id":"cmd_k3Jd9sPq2aBc","command":"pause","createdAt":1792105000000,"expiresAt":1792105030000}
```

The mod must:

1. Dedupe by `id`: a command id already seen is acknowledged again with its previous result and not executed twice.
2. Reply at once `{"type":"ack","id":"cmd_…"}` (received), then run it through the **same controller path the
   Discord panel and local panel use** (`HiveControl.START/STOP` for resume/pause), so pause ownership, limits
   and serialization are unchanged, and reply `{"type":"result","id":"cmd_…","status":"completed"|"rejected","reason":"…"}`.
   `reason` ≤ 160 chars, user-safe (e.g. "already paused", "manual stop is active").
3. Ignore a command whose `expiresAt` has passed (reply `rejected` with reason `expired`).
4. Push a telemetry frame right after the result so the dashboard shows the new state.

Commands in protocol 1: `pause` (= STOP in HiveControl terms: stop trading, keep running), `resume` (= START),
`snapshot` (send a telemetry frame now; always `completed`), `send_crash_report` (submit the prepared report via
the existing bug-report contract; `completed` with the issue number in `reason`, or `rejected`). No arguments in
protocol 1. Unknown command: `rejected`, reason `unsupported`.

Concurrency: a command arriving while Discord or the local panel is acting is handled exactly like a second
Discord press today: the controller serialises them and the later one gets the controller's answer ("already
paused", etc.) as its `rejected`/`completed` reason. The dashboard never bypasses the controller.

Nothing is queued for an offline mod; the relay answers the browser "offline" and sends nothing later. Nothing is
replayed after a reconnect. A command without a result after 30 s is reported "expired" to the browser; the mod
should still not execute it after that point.

## 4. Outcomes the dashboard shows (relay → browser)

`accepted` (relay took it) → `delivered` (written to the mod's socket) → `received` (mod acked) → `completed` or
`rejected (reason)`; or `expired` (no result in 30 s); or `unknown` (socket lost after delivery); or `offline`
(target not connected; nothing queued). The dashboard never shows success before `completed`.

## 5. Limits

| what | limit |
|---|---|
| frame size | mod 32 KiB, browser 4 KiB |
| telemetry | ≥ 1 s apart (coalesced), ≤ 160-char strings, counts ≤ 1e6, money clamped to ±1e12 |
| heartbeat | 20 s; offline after 80 s of silence |
| command expiry | 30 s |
| commands | 60 per minute per user |
| sockets | one per installation (newest wins), 5 browser tabs per user, 2000 total |
| pairing | 10/min per address, 60/min global, 5 tries per code, code lifetime 5 min, one live code per user |
| HTTP | 120 requests/min per address on the dashboard routes |

## 6. Ownership and hives

- Every browser command names explicit `targets` (installation ids). The relay checks each one belongs to the
  signed-in user and is not revoked; one bad target fails the whole command with an error, nothing is sent.
- An installation belongs to exactly the user whose code it redeemed. A second user cannot see it, rename it,
  unlink it or command it (verified). A code never reassigns an installation; relink = new installation entry.
- `hive` is a display label only. Installations of the **same user** with the same label are grouped on the page
  with "Pause all"/"Resume all" buttons that list their exact targets before sending one command per target.
  The label grants nothing: a different user with the same label sees only their own clients.
- **Open question for the mod agent**: what to send as `hive`. Suggestion: a stable, non-identifying value such
  as the first 12 hex chars of SHA-256 over the shared config directory's absolute path plus the hive name, so
  clients sharing a config directory land in one group and the path itself never leaves the machine. Confirm or
  propose otherwise before implementing; the relay accepts any `[0-9A-Za-z_-]{0,64}`.

## 7. Privacy and retention (relay side)

- Stored per installation: a name, the hashed credential, build, protocol, hive label, capabilities, last-seen
  time, and the last telemetry frame (so an offline client still shows stale figures, clearly labelled).
- Stored per command: id, installation, user, type, times, final state and reason; deleted after 7 days.
- Sessions: hashed token, 30 days; pairing codes: deleted an hour after expiry.
- Deleted on unlink: the credential (revoked) and the snapshot; the row itself after 7 days.
  Deleted on account deletion: everything of that user, at once (verified).
- Logs: one line per sign-in/link/unlink/deletion by numeric id; never bodies, codes, credentials, cookies.
  Caddy does not access-log `/dashboard*`; access entries never go to stderr.

## 8. Test vectors

- Code normalisation: `"k7pq m3zd"` → `K7PQ-M3ZD`; `"1010-1010"` → `LOLO-LOLO`; `"ABC"` → invalid.
- A valid hello with `protocols:[0,1,2]` negotiates 1; `[2]` is refused ("no supported protocol version; this relay speaks 1").
- Telemetry with `bank: 99999999999999999` is stored as 1000000000000; `listed: -5` as 0; `state: "DRUNK"` as `UNKNOWN`.
- Telemetry whose `detail` is `token gni1_abcdefghijklmnopqrstuvwxyz0123456789ABCDEF` is refused.
- A `result` sent twice for one id produces one `completed` update (verified live).
- A mod that acks but never results: `delivered → received → expired` after 30 s (verified live).
- A mod that disconnects with a pending command: `delivered → unknown`, plus a presence frame `online:false` (verified live).
- An oversized 36 KB frame: close 1009 (verified live).

## 9. What the mod must do (summary of client changes)

1. A screen under About → **Dashboard**: Link (type code → redeem → store credential), status (linked as "name",
   online/offline), Unlink (forget credential locally; the user also unlinks on the site).
2. A background client: connect with the credential, hello, heartbeat, reconnect with backoff, send telemetry from
   the same figures the HUD/web panel already compute (`panelState()` fields plus the ledger's 1 h/24 h profit and
   the completed-sale, purchase, listing and reprice counts), run commands through the existing controller, ack
   and result with dedupe by id, never block or pause trading on relay state.
3. Capabilities honest to the build: list `send_crash_report` only if the client can submit a prepared report.
4. Store the credential per installation, outside any shared file. Never log it.
5. Nothing new is sent that the Discord panel or local panel do not already show, except the aggregate counts above.

Until the mod has this, the dashboard shows a signed-in user "No clients linked yet" and nothing else.

## 10. The hive's shared database and the market API: what the dashboard does and does not touch

- **The relay never reads a client's database.** The hive's shared ledger (the SQLite file the accounts of a hive
  share) stays on the user's machine. Each installation reports its own telemetry over its own socket; the
  dashboard only groups what it is sent. Nothing on the VPS opens, syncs or mirrors that database.
- **Per-account figures, please.** Two clients of one hive both have the shared ledger, so if each computed
  "profit 24 h" over the whole ledger the dashboard would show the same hive-wide number twice. Send figures
  scoped to the reporting account (`account`, `listed`, `slots`, `trades`, `sales`, `purchases`, `listings`,
  `reprices`, `profit`). If a figure is only available hive-wide, send it from **one** client (the elected bot
  host that already posts the Discord feed) with `profit.source: "hive"`; the page shows a `hive` label on it
  and does not add it up across cards.
- **The market API is unrelated.** `https://gonuts.at/v1/*` (markets, orders, bug reports, downloads) keeps its
  own access rules and tokens. The dashboard credential (`gni1_…`) is accepted only on `/dashboard/*`; a market
  token is not accepted on `/dashboard/*`. Pairing needs neither a market token nor anything issued by hand.
- **The self-hosted hive server is unrelated too.** A user running their own hive server keeps doing so; the
  dashboard does not talk to it. If the mod agent later wants hive-server data on the page, it goes through
  the client's telemetry like everything else, never through a second connection from the VPS.

## 11. The full panel, relayed (capability `panel`) — added 2026-10-16

The hosted dashboard can show a linked installation's **own local web panel**, every page and button of it, at
`https://gonuts.at/dashboard/panel/<installationId>/`. The relay does not reimplement the panel: the mod answers
the same seven things its `LocalPanel.Backend` already answers on the home network, and the dashboard proxies
them under that prefix after checking ownership, same-origin + CSRF on the POSTs, the panel's own `X-GoNuts: 1`
header, and size bounds. The page the user sees is the mod's own `Pages.APP`, with its absolute paths rewritten
to relative ones and the dashboard's CSRF token injected into its `fetch` calls. Verified live 2026-10-16 with
the stand-in client; the real mod needs the client side below.

### 11.1 What the mod must do

1. Add `"panel"` to `capabilities` in the hello (and in the redeem request).
2. Handle frames of type `panel` and answer each with one `panel_result` carrying the same `id`:

```json
{"type":"panel","id":"pr_…","op":"state","expiresAt":1792120000000}
{"type":"panel","id":"pr_…","op":"data","name":"settings","query":{"id":"…"},"expiresAt":…}
{"type":"panel","id":"pr_…","op":"change","name":"setting","form":{"key":"slots.max","value":"45"},"expiresAt":…}
{"type":"panel","id":"pr_…","op":"act","act":{"id":"ctl:start","value":""},"expiresAt":…}
{"type":"panel","id":"pr_…","op":"act","act":{"id":"tune:set","text":"45"},"expiresAt":…}
{"type":"panel","id":"pr_…","op":"png","expiresAt":…}
{"type":"panel","id":"pr_…","op":"icon","item":"minecraft:diamond","expiresAt":…}
{"type":"panel","id":"pr_…","op":"asset","asset":"logo","expiresAt":…}
{"type":"panel","id":"pr_…","op":"page","expiresAt":…}
```

| op | call on the client | answer |
|---|---|---|
| `page` | the panel HTML (`Pages.APP`, exactly what `GET /` serves after the PIN) | `200 text/html; charset=utf-8`, body = the HTML |
| `state` | `backend.state()` | `200 application/json`, body = the JSON |
| `data` | `backend.data(name, query)` | `200 application/json`, or `404` when it returns null |
| `change` | `backend.change(name, form)` | `200 application/json`, or `404` when null |
| `act` | `act.text` present → `backend.text(id, text)`, else `backend.press(id, value)` | `204`, empty body |
| `png` | `backend.png()` | `200 image/png`, `encoding:"base64"` |
| `icon` | `backend.icon(item)` | `200 image/png` base64, or `404` |
| `asset` | `logo` → the bundled logo PNG; `font` → the bundled Monocraft TTF | `200 image/png` / `200 font/ttf`, base64 |

```json
{"type":"panel_result","id":"pr_…","status":200,"contentType":"application/json; charset=utf-8","body":"{…}"}
{"type":"panel_result","id":"pr_…","status":200,"contentType":"image/png","encoding":"base64","body":"iVBORw0…"}
{"type":"panel_result","id":"pr_…","status":404,"contentType":"text/plain; charset=utf-8","body":"Not found"}
```

3. Run `act` and `change` exactly as a local-panel press: same thread hand-off (`onClient`), same controller, same
   safety checks and pause ownership. The relay never forwards an `act` whose id does not match
   `[a-z]{1,16}:[A-Za-z0-9_.-]{1,48}`, a `data`/`change` name outside `[a-z]{1,24}`, or an icon id outside the
   panel's own pattern, so what arrives is what the local panel would have accepted.
4. Answer within 15 s (`expiresAt`); after that the relay has already told the browser "the client did not answer".
   Ignore a `panel` frame whose `expiresAt` has passed.
5. Bounds: a `panel_result` frame may be up to 768 KiB; text bodies ≤ 256 KiB, binary bodies ≤ 512 KiB decoded
   (downscale `png` if needed; the local panel's image is usually far smaller). At most 8 panel requests are in
   flight per installation; the relay refuses the ninth with 429 without sending it.
6. The page's own privacy filter already applies (`DiscordPrivacy.payload` on state); nothing extra is needed.

### 11.2 What the dashboard does with it

| browser route (under `/dashboard/panel/<id>/`) | op | auth |
|---|---|---|
| `GET /` | `page` (cached 10 min per installation + build; a stale copy is served while the client is away) | session + ownership |
| `GET state`, `GET api/<name>?…`, `GET panel.png`, `GET item/<item>.png`, `GET logo.png`, `GET font.ttf` | `state`, `data`, `png`, `icon`, `asset` (icons/assets cached) | session + ownership |
| `POST act`, `POST api/<name>` (form-urlencoded) | `act`, `change` | session + ownership + same-origin + CSRF + `X-GoNuts: 1`; recorded in the command audit as `panel:act:<id>` / `panel:change:<name>` |

Offline client → 503 with "the client is not connected" (the page shows its own "The game is not answering"
line); no answer in 15 s → 504; client without the capability → 501; another user's installation → 404; no
session → 401. Forms are capped at 64 KiB like the local panel; 120 requests/min per address apply as elsewhere.
"Lock" on the page returns to the dashboard; the dashboard session is the only sign-in, the local PIN is not used.
