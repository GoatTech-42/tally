# GoNuts bug reports — receiver contract (final, verified 2026-10-14)

For the agent building the mod's bug-report transport, and for whoever runs the
VPS. The receiver is live. It validates every request fully and answers 503 on
a well-formed report until the GitHub token is installed (section 7); after
that each accepted report becomes an issue in `cody-raves/Gonuts` with the
label `bug-report`.

This matches the "VPS agent contract" section of `docs/BUG_REPORTS.md` in the
mod repository, and the counters and states come from the mod's
`BugReportDiagnostics` class, so the client only has to serialise what it
already collects.

---

## 1. Endpoint and authentication

```
POST https://gonuts.at/v1/bugs
Authorization: Bearer <this install's existing GoNuts proxy token>
Content-Type: application/json
Idempotency-Key: <optional, see section 4>
```

Same host as `apiBaseUrl`. The per-install proxy token the mod already holds
is the only credential; nothing is minted into the mod and no account linking
exists. The server records the reporter only as that token's name (for
example `java_build-2`). `http://147.189.172.152/v1/bugs` also answers for
clients still on the plain address, but use HTTPS.

## 2. Request body

```json
{
  "schemaVersion": 1,
  "build": "0.1.0",
  "minecraftVersion": "26.2",
  "description": "Automation bought an x1 listing at the x64 ceiling price.\nHappened twice on diamond blocks.",
  "expected": "Only x64 rows should match an x64 signal.",
  "diagnostics": {
    "state": "SCANNING",
    "sqlite_errors": 0,
    "uncompleted_desk_actions": 1,
    "disconnect_notices": 2,
    "evasion_notices": 0,
    "recovered_purchase_pauses": 0
  }
}
```

| Field | Rule |
|---|---|
| `schemaVersion` | required, the integer `1` |
| `build` | required, `[0-9A-Za-z.+_-]{1,40}` (the mod version string) |
| `minecraftVersion` | required, same pattern (`26.2`) |
| `description` | required, 10–1500 characters, at most 40 lines |
| `expected` | required, 1–1500 characters, at most 30 lines |
| `diagnostics` | required object holding exactly `state` plus the five counters below; no other keys |

Exactly these six top-level fields. The issue title is taken from the first
line of `description` (cut at 100 characters), so put the one-line summary
first.

### diagnostics.state — one of

```
SCANNING  BID_DESK  PREPARING_LIST  LISTING  MONITORING
COOLDOWN  PAUSED    STOPPED         IDLE     BUYING     UNKNOWN
```

Upper case, exact. Send `UNKNOWN` when no state line was found in the sample
(the client's current "Unknown").

### diagnostics counters — all five required, integers 0 to 1 000 000 000

| key | client label today |
|---|---|
| `sqlite_errors` | SQLite errors |
| `uncompleted_desk_actions` | Uncompleted desk actions |
| `disconnect_notices` | Disconnect notices |
| `evasion_notices` | Evasion notices |
| `recovered_purchase_pauses` | Recovered purchase pauses |

Adding a counter later is a receiver change first, then a client change; until
then an unknown key is a 400.

### Refused

- any other top-level field or diagnostics key → 400 naming the key
- a state outside the list, a missing counter, a negative, fractional or
  non-numeric counter → 400
- text that looks like a pasted log: three or more lines matching
  `[hh:mm:ss]`, an ISO timestamp, `at pkg.Class(`, `Caused by:`,
  `Exception in thread`, `[main/`, `[Render thread/` → 400 "looks like a raw log"
- body over **16 KiB (16384 bytes)** → 413
- `Content-Type` other than `application/json` → 415
- missing or revoked token → 401; non-POST → 405

Control characters are stripped on arrival and the text is sanitised again
server-side, but whatever remains is written into a public issue verbatim.
The 400 message names the field and rule; it never echoes report content.

## 3. Responses

| Status | Body | Meaning |
|---|---|---|
| **201** | `{"reportId":"br_…","issueNumber":123,"issueUrl":"https://github.com/cody-raves/Gonuts/issues/123","duplicate":false}` | filed; show `issueUrl` to the user |
| **200** | same shape, `"duplicate":true` | a retry (same `Idempotency-Key` from this install) or the same text filed within 24 h; the existing issue comes back |
| 400 | `{"message":"<field and rule>","reason":"Bad Request","status":400}` | fix the form |
| 401 / 405 / 413 / 415 | same `{message, reason, status}` shape | see above |
| 429 | `Retry-After: <seconds>` | over quota, section 4 |
| 502 | | GitHub refused or was unreachable; nothing stored, retry later with the same `Idempotency-Key` |
| 503 | | report accepted by validation but the receiver has no GitHub token yet; tell the user to try later |

Every error body is `{"message","reason","status"}`.

## 4. Idempotency and quotas

- `Idempotency-Key` header, optional but recommended: **8 to 64 characters
  of `[A-Za-z0-9_-]`**, a UUID is ideal. Generate one per report when the
  user presses send and reuse it for every retry of that report. The key is
  scoped to the install's token; a replay returns 200 with the earlier
  `issueUrl`. A malformed key is a 400.
- Without a key, an identical report (same install, build, Minecraft
  version, description, expected) within 24 h is also answered with the
  existing issue.
- **5 reports per hour per token** and **10 per hour per address**. The
  address counter lives in memory only and is never written anywhere.
- Every token is per install and revocable in a second.
- No client-chosen repository, labels, assignees or anything else: the
  destination and the `bug-report` label are fixed on the server.
- All user text is placed inside a code fence longer than any backtick run it
  contains, so GitHub renders it as plain text: no @mentions, no links, no
  HTML, no markdown. Counters and state are validated identifiers and
  integers rendered as a table. Nothing in a report is interpreted or
  executed; the receiver only calls GitHub's create-issue API.

## 5. Privacy and logging (verified)

- Caddy has `log_skip` for `/v1/bugs*`: no request line, no address.
- The proxy logs one line per filed report: issue number, token name, build.
  Never the body, never the Authorization header.
- Postgres keeps per report: id, time, token name, build, first line of the
  description (for the digest), a SHA-256 fingerprint, the idempotency key,
  issue number and link. No description, no diagnostics, no address.
- The GitHub issue is the only copy of the report text.

## 6. Daily digest

Shortly after 09:00 UTC the receiver writes the previous day's digest to
`C:\DoughBay\proxy\digests\bug-digest-YYYY-MM-DD.md` (one line per report,
grouped by build, with issue links). Admin tokens (`BUG_ADMIN_TOKENS`) can
fetch it:

```
GET https://gonuts.at/v1/bugs/digest            # yesterday
GET https://gonuts.at/v1/bugs/digest?day=2026-10-13
Authorization: Bearer <admin token>
```

The repository's own `daily-bug-digest.yml` workflow (16:00 UTC) is
independent and keeps working; both read the same `bug-report` issues.

## 7. Deployment (VPS side, once)

1. On GitHub, create a **fine-grained personal access token**: repository
   access only `cody-raves/Gonuts`, permission **Issues: Read and write**
   (Metadata: read is added automatically). Set an expiry and note it.
2. On the VPS, in `C:\DoughBay\proxy\proxy.env`:
   ```
   GITHUB_TOKEN=github_pat_…
   BUG_REPORT_REPO=cody-raves/Gonuts
   ```
   then restart the proxy (kill the `doughbay-proxy.jar` java process and
   `Start-ScheduledTask DoughBay-Proxy`). The log line
   `bug reports: filing to cody-raves/Gonuts` confirms it.
3. The `bug-report` label is created on first use if it does not exist.
4. The token lives only in that file on the VPS. Clients never see it; the
   receiver never returns it; it appears in no log.

## 8. Patch automation (constraints, not built here)

Nothing in this receiver runs code, and nothing should. If patch automation
is added later it belongs in GitHub Actions on the repository, triggered by
the `bug-report` label, and may only open a **draft pull request** whose
checks have passed. No workflow may publish a release or treat report text
as a command. Every issue body is untrusted input, exactly as here.

## 9. Quick check

```bash
# 503 until the GitHub token is installed, then 201 with issueUrl
curl -s -X POST https://gonuts.at/v1/bugs \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"schemaVersion":1,"build":"0.1.0","minecraftVersion":"26.2","description":"Receiver smoke test, please close.","expected":"An issue with the bug-report label.","diagnostics":{"state":"IDLE","sqlite_errors":0,"uncompleted_desk_actions":0,"disconnect_notices":0,"evasion_notices":0,"recovered_purchase_pauses":0}}'
```
