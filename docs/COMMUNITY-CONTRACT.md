# GoNuts community market API: integration contract, draft v1 revision 2 (2026-10-16)

Base `https://gonuts.at/community/v1`. JSON UTF-8; uploads may be gzipped. Errors:
`{"message":"…","status":n,"code":"snake_case"}`. Times are epoch milliseconds. Not live yet.

## 0. Canonical forms

- **Item key** `itemKey`: `<namespace>:<id>|<stack>` for plain items, e.g. `minecraft:diamond|64`; for items with
  components `<namespace>:<id>|<stack>#<hash>` where `<hash>` is the first 16 hex characters of SHA-256 over the
  canonical component JSON, exactly as the mod already computes `item_key` for the order ingest. Pattern:
  `[a-z0-9_.-]+:[a-z0-9_./-]+\|[0-9]{1,3}(#[0-9a-f]{16})?`. Stack 1–64.
- **Account name** (seller, owner, actor, payer, recipient, own account): `\.?[A-Za-z0-9_]{2,20}` (Bedrock-style
  leading dot allowed). Stored as observed.
- **Region**: the shard identifier as the client reliably knows it (the server's own shard label from the tab
  list or scoreboard), lower-cased, `[a-z0-9-]{1,32}`. Unknown → omit the field; such rows go to region `unknown`
  and are served only in `mixed` answers.
- **Credential**: `gnc1_` + 43 url-safe base64 characters. Contributor id: `c_` + 12 url-safe characters.
- **Consent**: the request carries the version identifier `share-observations-v1`; the UI shows the sentence
  "Share market observations to use community market data." A new sentence means a new identifier.

## 1. Capabilities (public)

`GET /capabilities` →
```json
{"service":"community","version":1,"schemas":[1],
 "types":["ask","order","order_sweep_end","follow_trade","payment","sale"],
 "enabled":true,"shadow":true,"consent":"share-observations-v1",
 "limits":{"batchRecords":500,"batchBytes":524288,"batchesPer10s":1,"burst":3,"queriesPerMinute":60,"itemsPerQuery":50},
 "usability":{"ask":{"replaceScanSeconds":120,"historySeconds":900},"order":{"replaceScanSeconds":600,"historySeconds":3600},
   "sale":{"historySeconds":86400},"follow_trade":{"historySeconds":3600},"payment":{"historySeconds":3600}},
 "mergeWindowSeconds":{"follow_trade":5,"payment":5,"sale":2}}
```
`enabled:false` → every other route answers 503 `service_disabled`. Participate only when `enabled` and `1 ∈ schemas`.

## 2. Enrollment and account registration

`POST /enroll`, `Authorization: Bearer gni1_…` (dashboard installation credential; accepted on this route only):
```json
{"schema":1,"build":"0.1.1-…","consent":"share-observations-v1","observerAccounts":["Trader1"]}
```
| status | meaning |
|---|---|
| 201 `{"contributorId":"c_…","credential":"gnc1_…","shadow":true}` | enrolled; store the credential in the installation's own local file |
| 200 same shape | already enrolled: credential rotated, contributor id kept |
| 400 `bad_consent` / `bad_account` | wrong consent identifier / account name pattern |
| 401 `not_linked` | dashboard credential unknown or revoked: pair on the dashboard first |
| 403 `not_allowed` | test-phase allowlist |
| 429 | 10 enrollments per hour per address, `Retry-After` |

`PUT /me/accounts` (`Bearer gnc1_…`) `{"observerAccounts":["Trader1","Trader2"]}` → 204. Up to 8 names; the
list is what `own` marking (section 4) and source collapsing use. `POST /unenroll` → 204; rows deleted within the
hour. Dashboard unlink or account deletion suspends the contributor within 60 s.

All routes below take `Bearer gnc1_…`. Any other credential there → 401 `wrong_credential`.

## 3. Upload

`POST /observations`, headers `Idempotency-Key: [A-Za-z0-9_-]{8,64}` (one per batch, kept across retries),
optional `Content-Encoding: gzip`.
```json
{"schema":1,"sentAt":1792125000000,"region":"eu-1","records":[ … ]}
```
Up to 500 records. `sentAt` is the client clock at send; the client never adjusts any time. `region` applies to
every record unless a record has its own. Any unknown field anywhere → 400 `unexpected_field`, nothing stored.
A record failing its own rule is rejected individually (reported by index and code); the rest are stored.

| type | required | optional | rules |
|---|---|---|---|
| `ask` | `seller`, `itemKey`, `stack`, `unitPrice`, `observedAt`, `coverage` | `page`, `region` | `coverage` ∈ `complete` (the whole market page set for this item was read) / `partial`; `unitPrice` 1–1e12 |
| `order` | `owner`, `itemKey`, `unitPrice`, `total`, `delivered`, `observedAt`, `sweepId`, `sweepSeq` | `parts` (≤ 8 × ≤ 120 chars, no account names), `descriptor` (≤ 2 KiB), `page`, `region` | `sweepId` `[A-Za-z0-9_-]{4,32}` per sweep; `sweepSeq` 0-based within the sweep |
| `order_sweep_end` | `sweepId`, `pages`, `records`, `complete`, `observedAt` | `region` | one per sweep; `records` = number of `order` records sent for it; `complete:false` for interrupted scans |
| `follow_trade` | `actor`, `kind` ∈ `listed|ordered|bought`, `quantity`, `total`, `observedAt`, and `itemKey` or `itemName` | `region` | `itemName` only when no id resolves: ≤ 48 chars `[A-Za-z0-9' -]`; `quantity` 1–1e6; `total` 1–1e12 |
| `payment` | `payer`, `recipient`, `amount`, `observedAt` | `region` | `amount` 1–1e12; the notice's own words are not sent |
| `sale` | `account`, `itemKey`, `quantity`, `price`, `observedAt` | `region` | the contributing client's own confirmed sale; `account` must be one of its registered observer accounts, else `not_own_account` |

Refused in any string: UUIDs, coordinates, paths, URLs, tokens, anything not matching that field's pattern.
`observedAt` more than 10 min in the future or 24 h in the past **after server skew correction** → that record
`future_observation` / `stale_observation`.

Response 202:
```json
{"batchId":"b_…","accepted":412,"duplicates":80,"rejected":[{"index":17,"code":"bad_item_key"}],
 "credits":{"new":64,"confirmations":9,"freshness":5,"earned":78,"window6h":610},
 "eligible":true,"shadow":true,"skewMillis":-1430,"sweeps":{"s_01":"complete"},"degraded":false}
```
| repeat | answer |
|---|---|
| same key, same body | 200, original result, `"replay":true`, zero credits |
| same key, different body | 409 `idempotency_conflict`; nothing stored |
| new key, already-stored records | 202, counted as duplicates, credits per section 6 of the design |

Errors: 400 (nothing stored), 401, 403 `suspended`, 413 `too_large` / `too_many_records`, 429 with
`Retry-After`, 503 `service_disabled`. Retry: 5xx or network → backoff 10 s … 10 min, same key, raw times
unchanged; 429 → wait `Retry-After`, same key; other 4xx → drop the batch. `degraded:true` → only `sale`,
`order` and `order_sweep_end` were kept from this batch (service-wide hourly cap).

## 4. Query

`GET /market/{itemKey}?region=eu-1` or `GET /market?items=<up to 50 keys>&region=eu-1`. Omitting `region`
gives `regionScope:"mixed"`.
```json
{"itemKey":"minecraft:diamond|64","regionScope":"eu-1","generatedAt":1792125010000,"ttlSeconds":30,
 "asks":{"usability":"replace_scan","ageSeconds":12,"coverage":"complete","observers":3,"independentSources":2,"confidence":"medium",
         "spreadPercent":4,"liveRows":5,
         "rows":[{"seller":"SomeTrader","unitPrice":1200,"stack":64,"observers":3,"firstSeenAt":…,"lastSeenAt":…,"own":false},
                 {"seller":"Trader1","unitPrice":1210,"stack":64,"observers":1,"firstSeenAt":…,"lastSeenAt":…,"own":true,"ownIndex":1}],
         "lowest":1200,"note":"rows are seller-price rows, not listing or unit counts"},
 "orders":{"usability":"history","ageSeconds":700,"sweep":"complete","observers":2,"independentSources":2,"confidence":"medium",
           "rows":[{"owner":"BuyerX","unitPrice":1150,"remaining":64,"total":192,"observedAt":…,"own":false}]},
 "sales":{"usability":"history","ageSeconds":600,"independentSources":4,"confidence":"medium",
          "last24h":{"count":18,"medianUnitPrice":1180,"p25":1150,"p75":1210},"lastHour":{"count":2,"medianUnitPrice":1190}},
 "followActivity":{"usability":"history","ageSeconds":300,"lastHour":{"listed":4,"ordered":1,"bought":2,"medianListedUnitPrice":1210},
                   "recent":[{"actor":"RivalBot","kind":"listed","quantity":64,"total":77400,"at":…,"observers":2,"own":false}],
                   "mergeWindowSeconds":5},
 "payments":{"usability":"history","ageSeconds":120,"lastHour":{"count":9,"totalAmount":430000},
             "recent":[{"payer":"BuyerX","recipient":"RivalBot","amount":50000,"at":…,"observers":1,"own":false}],
             "note":"payments are not tied to this item and are not sales"}}
```
Rules the client must honour:
- Act only on `usability:"replace_scan"`; `history` is context; absent blocks mean no data, not zero stock.
- `asks` are asking prices; `sales` are confirmed own-receipt sales; `payments` are neither.
- `rows` and `liveRows` count seller-price rows. No field states a number of listings or units available.
- `own:true` rows belong to the caller's registered accounts; render them as `Account <ownIndex>` wherever
  privacy masking is on (tables, charts, tooltips, exports). The server does not substitute names.
- `independentSources` is the number for confidence; `observers` is raw contributor count.
- Cache for `ttlSeconds`; after that, ask again or scan.
- Not eligible, with enforcement on: 403 `contribute_first` with `{"credits":{"window6h":4,"needed":20},"graceUntil":…}`.
  In shadow mode data is returned and the same figures appear in `/me`.

## 5. Status

`GET /me` → `{"contributorId":"c_…","enrolledAt":…,"observerAccounts":["Trader1"],"eligible":true,"shadow":true,
"credits":{"window6h":610,"needed":20,"lastBatchAt":…},"graceUntil":null,"suspended":false,"limits":{…}}`.
`GET /health` (no credential) → `{"ok":true,"enabled":true,"shadow":true,"contributorsLastHour":2,"rows":{…},"aggregationLagSeconds":4}`.

## 6. Clock rule

Server authority only. The client sends raw `sentAt` and raw `observedAt` and never corrects either. The server
stores the original and a skew-corrected copy and uses the corrected copy for freshness, bucketing and order.
A retried batch (same key, same body) changes nothing whenever it arrives. `skewMillis` in the answer is
informational.

## 7. Compatibility

Additive within schema 1: new types and blocks appear in `capabilities` first; clients ignore unknown blocks and
send only listed types. A breaking change is schema 2, advertised beside 1 for at least one release.

## 8. Mod-side checklist

1. Opt-in switch with the consent sentence; status line (enrolled, eligible, last upload/query, shadow).
2. Enroll after dashboard pairing; register observer accounts and keep them current; `gnc1_` in the local file;
   unenroll on opt-out.
3. Sanitise: keep public identities; drop everything in the exclusion list; resolve items to keys, else `itemName`.
4. Batch every 10–30 s, fresh key per batch kept across retries, raw times, sweeps with `sweepId`/`sweepSeq` and
   an `order_sweep_end`; mark ask `coverage` honestly.
5. Query before discovery reads; honour `usability` only; cache for `ttlSeconds`; shadow mode scans anyway and
   logs the comparison; community data never changes limits, item rules, pacing or recovery.
6. Display: mask `own` rows as `Account <n>` when privacy masking is on.
7. Any error, 403 or `enabled:false` → local discovery exactly as today.

## 9. Revision 2.1 resolutions (2026-10-16, supersede earlier text where they differ)

### 9.1 Scans, rows and freshness

- An **ask scan** is one contributor's read of one market in one region, where a market is **one exact `itemKey` including its stack size** (`minecraft:diamond|64` and `minecraft:diamond|1` are two markets, two scans, two `ask_scan_end` records). A page read that shows several stack sizes at once is reported as one scan per stack size it covered completely, each with its own `scanId`; a stack size that was read completely and showed nothing is reported with `rows: 0`, which is what lets its old rows be marked absent.
  Every `ask` record carries `scanId` (`[A-Za-z0-9_-]{4,32}`); the scan ends with one record
  `{"type":"ask_scan_end","scanId":…,"itemKey":…,"rows":n,"complete":true|false,"startedAt":…,"endedAt":…}`.
  A scan is **received-complete** when the server holds exactly `rows` ask records for that `scanId` and
  `complete` is true. Scans without an end record are abandoned after 30 min without a record and count as partial.
- Each ask row keeps its own `firstSeenAt` and `lastSeenAt` and is served with its own `ageSeconds`. The block's
  `ageSeconds` is the age of the **newest received-complete scan of that market and region** (`endedAt`), not the
  newest row. `usability:"replace_scan"` requires that scan to be ≤ 120 s old; rows older than that scan are not
  served as live.
- **Absent rows.** A row that a received-complete scan of its market did not contain becomes `status:"unconfirmed"`
  and is excluded from `liveRows` and `lowest`; it is `gone` after a second received-complete scan (any source)
  without it, or 900 s after `lastSeenAt`, whichever first. A partial scan changes nothing about absence.
- Orders: the same with sweeps (9.6). Each order row has its own `observedAt`/`ageSeconds`; the block's age is the
  newest received-complete sweep covering that market's scope.

### 9.2 Own-account marking

- "Own" means any account registered (via `observerAccounts`) by **any installation of the same dashboard owner**
  as the caller, not only the calling installation.
- Every identity field gets its own marker: `sellerOwnIndex`, `ownerOwnIndex`, `actorOwnIndex`, `accountOwnIndex`,
  and for payments both `payerOwnIndex` and `recipientOwnIndex`. The field is absent when the name is not the
  caller's; an integer 1..n otherwise. Indexes are stable per owner: the order in which the owner's accounts were
  first registered, persisted, never reshuffled. There is no bare `own` flag.

### 9.3 Revocation guarantee

Two bounded mechanisms, both required: the dashboard pushes every unlink/deletion to the community service over
loopback at once, **and** the community service polls the dashboard's revocation cursor every 30 s. Guarantee: a
revoked installation is suspended within 60 s while the dashboard answers the poll. If the poll has failed for
10 consecutive minutes, the community service refuses data routes for everyone with 503 `revocation_check_unavailable`
until a poll succeeds (fail closed), so no window longer than 60 s of serving a revoked contributor exists while
the service is up, and none at all when it cannot verify.

### 9.4 Clock correction

- `sentAt` is the client clock **at this transmission attempt** and must be refreshed on every retry;
  `batchCreatedAt` (new, required) is the assembly time and never changes. Observation times stay raw.
- Per contributor the server keeps a skew estimate: the median of `receivedAt − sentAt` over the last 20 batches
  whose own `receivedAt − sentAt` was below 30 s (larger values are transfer delay, not skew, and are ignored).
  Observations are corrected by that estimate, never by the current batch alone. A batch delivered an hour late
  therefore carries observations that are an hour old after correction, exactly as they should be.
- A batch with no usable skew yet (first batches, all delayed) is accepted and its rows corrected by 0 but marked
  `clock:"unverified"` in storage and never used for `replace_scan` until the contributor has a skew estimate.

### 9.5 Event merging

- Two reports are the **same event** only if all identity fields are equal, **they come from different
  contributors**, and `|tServer₁ − tServer₂| ≤ mergeWindowSeconds` as a real timestamp distance (5 s for
  `follow_trade` and `payment`, 2 s for `sale`). Buckets are not used.
- Repeated identical notices from **one** contributor at different `observedAt` are kept as separate events
  (a real repeat); identical fields with identical `observedAt` from one contributor are a duplicate.
- Counts built from merged events (`followActivity.lastHour.*`, `payments.lastHour.count`) are served with
  `"estimate":true` and `mergeWindowSeconds`; `sales` counts from own receipts are exact per account.

### 9.6 Sweeps

- Every `order` record: `sweepId`, `sweepSeq` (0-based, **unique and contiguous** within the sweep), and the sweep's
  `scope`: `"full"` (the whole order house) or `{"items":[itemKeys]}` (complete for exactly those markets).
  First record of a sweep also carries `sweepStartedAt`.
- `order_sweep_end`: `sweepId`, `scope`, `pages`, `records`, `complete`, `startedAt`, `endedAt`. **Received-complete**
  = `complete:true` and exactly `records` order rows received with contiguous `sweepSeq` 0..records−1, checked
  **before** market deduplication (duplicates within the sweep still count toward `records`).
- Only a received-complete sweep expires orders, and only inside its `scope` and region: rows last seen by anyone
  more than 60 min before `endedAt` and absent from the sweep → `gone`. Partial scope expires only its items.
- Empty sweeps: `records:0`, `pages ≥ 1`, `complete:true` is valid; it marks the scope's rows `unconfirmed`, and a
  second independent received-complete sweep (or 60 min) makes them `gone`.
- Long scans: a sweep stays open while records keep arriving; it is abandoned (treated as partial, never
  expiring) after 30 min with no record and no end record. There is no fixed 10-minute limit.
- Retries re-deliver the same `sweepSeq` values under the same idempotency key and change nothing; a different
  batch repeating a `sweepSeq` is a duplicate row and does not break contiguity.

## 10. Status (2026-10-16 07:40 UTC)

**Implemented and deployed on the VPS in shadow mode, private.** `https://gonuts.at/community/v1/capabilities`
answers; enrollment is limited to the owner's two dashboard installations (ids 4 and 5) until the client side
exists. Verified live with stand-in contributors against every rule in sections 0–9 (see `README-COMMUNITY.md`).
Two details settled during verification and binding for the client:

- **Idempotency ignores `sentAt`.** The batch identity is the body with `sentAt` normalised, so a retry that
  refreshes `sentAt` (as 9.4 requires) is a replay (200), while any other change under the same key is 409.
- **Scan and sweep bookkeeping is idempotent per row.** Re-sending the same ask or order rows under a new key
  does not inflate `rows`/`records` counts; a scan or sweep is complete when the distinct rows received match.

Not enabled: scan skipping (client decision, shadow keeps scanning), contribution enforcement (`COMMUNITY_SHADOW`
stays true), public enrollment (allowlist in force), the rival pseudonym field (design section 5).

## 11. Wire clarifications (2026-10-16, answers to the mod agent)

1. **Ask-scan completeness counts distinct rows, not listings.** Row identity is
   `(region, seller, itemKey, stack, unitPrice)`. `rows` in `ask_scan_end` is the number of distinct such rows the
   scan contained; two identical listings by one seller at one price are one row (the client counts them once and
   sends the row once; a repeated row in the same scan is counted once by the server anyway). A scan is
   received-complete when the server holds exactly `rows` distinct rows for that `scanId` and `complete` is true.
2. **A scan covers one exact `itemKey`, stack size included.** Section 9.1 now says so; the earlier "all stack
   sizes it shows" wording is withdrawn. The market query is per exact `itemKey` as well, so scan scope and
   query scope match one to one.

## 12. `price_level`: a price seen without a seller (added 2026-10-16 on the mod agent's request)

Auction price-probe tooltips often show a price and no seller. Those are **levels**, not listings: send them as
their own type and never as an `ask` with an invented seller.

```json
{"type":"price_level","itemKey":"minecraft:diamond|64","stack":64,"unitPrice":1195,"observedAt":1792130000000,"source":"probe"}
```

| field | rule |
|---|---|
| `itemKey`, `stack`, `unitPrice`, `observedAt` | as for `ask` |
| `source` | optional, `probe` (default: a tooltip or price check) or `page` (a page row whose seller was not visible) |
| forbidden | `seller`, `quantity`, `coverage`, `scanId`: a level has none of these; their presence is 400 `unexpected_field` |

Server behaviour: identity `(region, itemKey, stack, unitPrice)`; first/last seen and observers per level;
credits like asks (new level 2, independent confirmation 1, freshness update 1); retention 15 min of history,
rows dropped an hour after last seen. Levels take no part in scans and never mark anything absent.

Query block `priceLevels` (beside `asks`, never merged into it):
```json
"priceLevels":{"usability":"history","ageSeconds":40,"observers":2,"independentSources":1,"confidence":"medium","lowest":1195,
  "rows":[{"stack":64,"unitPrice":1195,"firstSeenAt":…,"lastSeenAt":…,"ageSeconds":40,"source":"probe","observers":2}],
  "note":"prices seen without a seller; levels, not listings or quantities"}
```
`usability` is `history` at most (a level can never replace a discovery scan: it proves a price was shown, not
that a listing exists), `expired` beyond 15 min. No field states a listing count or available quantity.
Where the seller **is** visible, keep sending an `ask`; the two blocks stay apart so a client can weight them.


## Section 13 deployment handoff (reported by VPS agent)

The deployed capabilities advertise order_level. Required: itemKey, unitPrice, total, delivered, observedAt. Optional: depth (default 1), parts, descriptor, page, source (page default or probe), region. Owner, sweepId, sweepSeq and scope are forbidden. Delivered must not exceed total. Server identity: region, item, parts, price and total; newest observation updates delivered and depth. Queries return an orderLevels block, highest bid first, history-only, with remaining, depth and remainingSeen. Retention 24 hours. This is the pasted deployment handoff, not a substitute for the receiver's complete field-bound specification. The client currently omits optional parts and descriptor and continues to hold component variants back.


## Section 14 accepted deployment handoff

Capabilities advertise networkScopes:[net-donut], regionField:region, regionQueryParameter:region. Send region=net-donut at batch level only, from observations made on a verified DonutSMP connection and only when advertised. Queries use ?region=net-donut; absent scope remains mixed. Existing unknown rows are unchanged. Physical shard identity is separate and no community wire field accepts it. Scope is accepted for shadow testing only; scan replacement, enforcement and public rollout remain off.


I'll answer the four points precisely and, for the second one, make snapshots immutable server-side (materialized at the sweep end) so the answer is a guarantee rather than a caveat. Then I'll rewrite section 15 with exact JSON.

Wiring the caller id into the routes, retention for the frozen snapshots, the self-check, and the rewritten section 15, all in parallel.

Section 15 is rewritten with exact JSON, and the server now matches it. Two things changed on the server to make the answers guarantees rather than caveats: snapshots are frozen the moment the end record arrives, and the marker carries `self` plus a server-side distinct `rows` count. Both are deployed, self-checked against the live database, and uploads continued with zero rejections. Both bots are untouched.

**The four answers, short form:**

- **Other hive client's read:** `sameOwner:true` and `self:false` in the marker. `self:true` is the caller's own read. Contributor ids are never exposed.
- **Unchanged across pages:** yes, by construction. The snapshot is frozen into its own table when the end record is processed. Later observations change the live levels, not the snapshot. Every page carries the same `readId` and `rowsTotal`.
- **`levels` versus `rows`:** `levels` is the client's count of emitted records, repeats included, echoed back untouched. `rows` is the server's count of distinct identities it froze, and it is what the full-read threshold and `rowsTotal` use. The self-check sent 1300 records with 1200 identities and got `levels:1300, rows:1202`.
- **`complete`:** physical page exhaustion only. It says nothing about ownership or record types. A capped read is never complete; if both flags arrive, the server records `capped`.

Here is section 15 verbatim for the mod agent (also at [COMMUNITY-CONTRACT.md:375](C:\DoughBay\community\COMMUNITY-CONTRACT.md:375)):

> ## 15. Level reads: marker, snapshot and the skip decision
>
> Supports rule A of the scan-reduction proposal (own hive only): a bot may skip its own full order read when the other bot of its hive produced a fresh full read, by **replacing** its local order view with that read's frozen snapshot. The server enables nothing; the client decides, first in shadow (Phase 1: every live read kept, snapshots compared separately; no skipping, no local order-book replacement).
>
> ### 15.1 `order_sweep_end` additions
>
> ```json
> {"type":"order_sweep_end","sweepId":"o_2bf7bb9426414732a835956c","scope":"full","pages":200,"records":0,
>  "levels":7312,"capped":true,"complete":false,"startedAt":1792183200000,"endedAt":1792183365000,"observedAt":1792183365000}
> ```
>
> | field | meaning |
> |---|---|
> | `levels` | optional, 0–1 000 000. **Emitted records**: how many `order_level` records the client sent for this read, repeats included. The server makes no decision on it; it is echoed in the marker so the client can compare. |
> | `capped` | optional boolean. The page cap stopped the read before the end of the list. |
> | `complete` | **Physical page exhaustion only**: true when the reader reached the end of the list (a short or empty last page). It says nothing about ownership, `order` records or `order_level` records. A read that is `capped` is never complete; if both are sent, the server records `capped`. |
> | `records` | unchanged: the number of named `order` records, 0 when none; the contiguity rule applies to those only |
>
> Resulting status: `complete` (end reached), `capped` (page cap hit), `partial` (neither: interrupted). When `pages ≥ 100` the server **freezes the read's snapshot** at this moment: the distinct level identities this reader observed between `startedAt` and `endedAt` + 60 s, with the `delivered` and `depth` it saw, and stores their count as `rows`. A **full level read** is a `complete` or `capped` read with `pages ≥ 100` and `rows ≥ 1000` (the server's distinct count, not the client's `levels`). Sending the same end record again rebuilds the snapshot; nothing else ever changes it.
>
> ### 15.2 Marker: `GET /orders/last-read?region=net-donut`
>
> Credential required; counts as a query. `region` is required (there is no mixed marker).
>
> ```json
> {"regionScope":"net-donut","generatedAt":1792183500000,
>  "read":{"readId":"o_2bf7bb9426414732a835956c","startedAt":1792183200000,"endedAt":1792183365000,"ageSeconds":135,
>          "pages":200,"levels":7312,"rows":7229,"status":"capped","capped":true,"self":false,"sameOwner":true}}
> ```
>
> No full level read in the scope yet:
> ```json
> {"regionScope":"net-donut","generatedAt":1792183500000,"read":null}
> ```
>
> | field | meaning |
> |---|---|
> | `readId` | the `sweepId` the reader chose for that read; the key for `/orders/snapshot` |
> | `levels` | the reader's own count of emitted records (15.1) |
> | `rows` | distinct level identities the server froze for this read; equals the snapshot's `rowsTotal` |
> | `status`, `capped` | as in 15.1 |
> | `self` | true when the caller made this read itself |
> | `sameOwner` | true when the reader belongs to the caller's own hive (same dashboard owner) |
>
> **Identifying the other hive client's read:** `sameOwner:true` and `self:false`. `self:true` is the caller's own read (nothing to skip); `sameOwner:false` is another owner's read (not used by rule A). Contributor ids are never exposed. The same object appears as `orderLevels.lastFullRead` in an exact-scope market query.
>
> ### 15.3 Snapshot: `GET /orders/snapshot?region=net-donut&read=<readId>&page=1`
>
> Credential required; each page counts as a query. Pages of 1000 rows ordered by item key, then bid (highest first), then level id. `page` is 1-based, up to 1000; `next` is the next page number or `null` on the last page.
>
> ```json
> {"regionScope":"net-donut","generatedAt":1792183500000,
>  "read":{"readId":"o_2bf7bb9426414732a835956c","startedAt":1792183200000,"endedAt":1792183365000,"ageSeconds":135,
>          "pages":200,"levels":7312,"rows":7229,"status":"capped","capped":true,"self":false,"sameOwner":true},
>  "page":1,"pageSize":1000,"rowsTotal":7229,
>  "rows":[{"itemKey":"minecraft:acacia_boat|1","unitPrice":1,"total":2100000000,"delivered":0,"remaining":2100000000,"depth":1,"observedAt":1792183301000,"source":"page"},
>          {"itemKey":"minecraft:amethyst_block|64","unitPrice":1250,"total":640,"delivered":128,"remaining":512,"depth":2,"observedAt":1792183290000,"source":"page"},
>          {"itemKey":"minecraft:netherite_helmet|1","parts":"Protection IV;Unbreaking III;Mending","unitPrice":15000,"total":7,"delivered":0,"remaining":7,"depth":1,"observedAt":1792183310000,"source":"page"}],
>  "next":2,
>  "note":"the distinct levels one full read observed, frozen at its end record; replace the local order view with all pages, never merge into stale data; own orders are not identifiable here"}
> ```
> `parts` is omitted when empty. Last page: `"next":null`. Unknown id, or a read that is not a full level read of that scope: 404 `{"status":404,"code":"unknown_read","message":"…"}`.
>
> **Unchanged across pages:** yes, by construction. The snapshot is frozen when the end record is processed (15.1), and no later observation, by that reader or anyone else, alters its membership, order, `delivered` or `depth`. Every page carries the same `read.readId` and `rowsTotal`; a client that sees either change between pages has crossed a re-sent end record and must start over. The snapshot lives as long as its read record (24 h), then both go together.
>
> ### 15.4 Client rules for a skip (rule A, own hive only; not before Phase 1 passes)
> 1. **Marker first.** Skip is possible only if `read` is present, `sameOwner:true`, `self:false`, `ageSeconds ≤ 900`, `pages ≥ 100`, `rows ≥ 1000`. A `capped` read is acceptable only if the bot's own reads are capped at the same page count or fewer; a `complete` read is always acceptable.
> 2. **Replace, never merge.** Fetch every page until `next` is `null`, 2 s per page, same `readId` and `rowsTotal` on every page, and the number of rows received equal to `rowsTotal`; then replace the local order view in one step. Any failure discards everything and falls back to a live read.
> 3. **Own orders.** Anonymous levels cannot identify the bot's own orders. The bot keeps its own order list from its own UI, subtracts its own price/total rows, and keeps every live own-order check before placing, repricing or cancelling.
> 4. **Managed markets.** Every market the bot manages must appear in the snapshot with at least one row; the snapshot, not the 100-row market block, is the ladder. A managed market absent from the snapshot forces a live read that cycle. If the market block is consulted instead, `truncated:true`, a missing `orderLevels` block or `expired` forces a live read too.
> 5. **Caps.** Never more than two consecutive skips; never before an order above the bot's large-order threshold; never after any non-200 answer from the service in the last 5 minutes.
> 6. **Scope.** Only `net-donut`; `unknown` rows and mixed queries never feed a skip.
>
> ### 15.5 Phase 1 (shadow) before any trial
> Per read cycle the client records `wouldSkip` with the marker used (or the reason for no skip), performs its normal live read, then compares the frozen snapshot with the live read separately: levels missing from the snapshot, levels the live read did not find, highest-bid differences on managed markets, and whether any trading decision would have differed. No skipping and no local order-book replacement in Phase 1. A limited Alt-only trial follows only after those comparisons pass the mod agent's criteria. Enforcement and public rollout stay off throughout.

**Server verification, in-process against the live database:** a capped end record with 1300 emitted records became a `capped` full read with `rows:1202`; the reader's marker shows `self:true`, the other owner's shows `self:false, sameOwner:false`; the snapshot paged 1000 then 202 rows with `next` ending in `null`; a newer observation moved the live level to delivered 9 while the snapshot row stayed at delivered 0, depth 1; an unknown read id returned nothing; both routes answer 401 without a credential. All transient rows were removed. One note for the mod agent: today's clients still send `records:0, complete:false` and no `levels`, so no read counts as full until the prepared marker changes are installed.
## Completed-sale bootstrap (deployed 2026-10-18)

`GET /sales/history?region=net-donut&items=k1,k2&limit=1000[&after=cursor]` uses existing credentials, eligibility and query limits. Items are exact plain commodity keys (at most 50). The default window is 24 hours. Omit `since` on every page, or resend its original explicit value unchanged. The signed cursor binds the caller, region, window and keys to a frozen snapshot, expires after ten minutes, and returns `next:null` at EOF.

Schema 1, `kind:completed_sales`, includes `regionScope`, `generatedAt`, `asOf`, `windowStart`, `rows`, `next` and optional `capped:true`. Each row has opaque `id`, exact `itemKey`, `stack`, actual `quantity`, `total` proceeds, integral `unitPrice`, original `observedAt`, and provenance `{kind:seller_receipt, independentSources:n, ownAccount:boolean}`. Stack is min(quantity,64), not a replacement for quantity. No account identities are returned. Pages are bounded at 256 KiB; snapshots at 50,000 rows. Caller/filter mismatch returns 400; expiry 410.

The client accepts only plain supported commodities, skips own-account and multi-stack receipts rather than inventing stack samples, requires distinct sample coverage and a recent observation, and tags imported historical observations separately. It rejects capped/inconsistent snapshots and bounds collection to 50,000 rows/64 pages. No positions, ownership, booked profit or live order-book state are imported. Missing shared history never disables local collection or bypasses native live trade checks.
