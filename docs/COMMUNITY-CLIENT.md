# Community client — private shadow build

Opt-in is per installation. About -> Dashboard -> Community market data, or select the client in the local Dashboard tab. Credentials and the durable current retry batch live under the installation game directory, `gonuts-community`, with owner-only permissions. No secrets go through the shared hive; controls and nonsecret status use the existing private local IPC.

Supported observations: public follow trades, paid/was_paid notices with both parties, confirmed parked-listing receipts, authoritative auction-probe seller/price rows, and order-house rows with recognized owner fields. Unknown actors are omitted. Generic sold-multiple notices without both payment parties are not invented into payments.

Auction probes observe one page and exclude our seller, so they are always PARTIAL. Each exact item/stack group deduplicates seller-price rows and ends a partial scan. Whole order sweeps claim completeness only if the existing driver reached the end and every collected row has a valid public owner. Unknown region is omitted, not substituted with a coordinate-based estimate. Mixed queries never count as replaceable. Component lore/descriptors are not uploaded; canonical hashes identify variants.

Queries begin before existing live price probes. Cached community answers are compared against subsequent reads only if they arrived before the read and their TTL still holds. Own-seller rows are excluded from comparison. Shadow summaries contain item keys/prices/counts only, never rival names or credentials. No scan can be skipped in this implementation, including if the server turns shadow mode off.

Uploads contain at most 500 records / approximately 500 KB uncompressed, every ten seconds. One frozen retry batch is saved before transmission. Its key, creation time and observations remain unchanged; sentAt is refreshed per attempt. Unknown capabilities are not sent, 429 honors Retry-After, 5xx/network back off, nonretryable 4xx discard the batch. Local operation does not depend on service health.

Live acceptance: opt in Main/Alt; verify contributor enrollment and account masking with the VPS; inspect acceptance/rejection counts; confirm known follow/payment direction and orders; compare fresh local price reads with preexisting community answers; keep all scanning enabled. Public rollout, scan skipping and contribution enforcement remain separate decisions.

## First live test

Unpublished JAR SHA-256: `05188a4fcfa260e3c29b0df35144c306ce291c3cb018a245d6e49ca97677e2f3`.
Full build passed: 1,023 tests passed, one skipped. Web-panel JavaScript syntax passed.
Installed on Main and Alt, restarted cleanly, enrolled both, and enabled their private shadow contribution. At the latest check the server acknowledged 255 observations from Main and 283 from Alt with zero rejections. Both queried community markets and continued trading. This verifies enrollment/upload transport, not event deduplication or market accuracy; those need backend inspection and representative comparisons.

The live auction price-probe lore carries item and bare price but no seller identity. Those rows are intentionally not fabricated into seller-bearing asks. A separate price-only observation type was requested from the VPS. Public order sweeps also need a recognized owner before uploading an order. This first test does not prove complete auction/order coverage or justify reducing reads. No release was published.

## Price-only extension

The probe now contributes distinct `price_level` rows when a price is authoritative but no seller is shown. Levels carry no seller, quantity, scan id or coverage, and are sent only when capabilities advertise support. Comparisons label their evidence source; priceLevels remain historical and cannot increment the replaceable-read metric, even if a malformed server response says otherwise. Named asks remain separate.

## Coverage audit follow-up

Both clients read 9,000 order rows over 200 pages. This is the driver's page cap, not an empty book, and the driver correctly reports incomplete sweeps. The initial owner filter queued none, so the community ends were empty/partial. The client now recognizes decorated labels, Buyer/Player/Order by/Placed by fields and split label/name pairs. Coverage counters separate rows read, rows with identities, invalid rows and queued rows; no raw tooltip is logged.

Shard tags are accepted only from explicit server-provided numbered tab/sidebar labels, bound to the connection and world. Coordinate-derived RegionMap/StaffWatch labels and forward-domain names are not used. Unknown stays unknown. Page-derived observations may receive the observer shard; global follow/payment notices do not gain a claimed actor location. Cached queries are scoped to the current verified shard and discarded for comparison after a scope change. Contribution is restricted to an active DonutSMP connection.

Follow uploads are called only by Follows.observeChat on incoming game messages, not file history, GUI refreshes or persisted listing state. Repeated feed notices from one client remain separate as the wire contract requires. Same-payer/recipient events are preserved as observed; they do not become profit or sale evidence.

Legacy ItemDescriptor.hash uses Java hashCode (up to eight hex digits), while the community contract requires a 16-digit canonical SHA-256 component identity. Component rows with only that legacy identity are held back rather than mapped to a plain market or invented hash. A canonical component serialization must be agreed before enabling those uploads.

## Local performance measurements

Each installation records a session summary in its own `gonuts-community/performance.json`. The file has no player identities, market names, raw text or credentials. Atomic snapshots run on the background worker every minute, even when community sharing is disabled, and retain at most 168 sessions from the last seven days. This is local-only; it is not sent to the community ingestion endpoint. Nonsecret summaries are available in the existing local Dashboard status under `community.performance`.

Measured baseline: connected milliseconds (offline intervals and game-thread gaps longer than five seconds excluded), auction price-probe milliseconds and completion/failure counts, public order-read milliseconds and completion/failure counts, pages and rows read. Probe/order timing starts after authorization and preflight and ends at the terminal event or cancellation. Failed reads still consume time and are included in reading duration. The terminal hook clears its timer, so cleanup cannot count it twice. Own inventory, personal order reconciliation and listing checks are deliberately outside this shared-market reading baseline. In-progress reads and the most recent unsaved minute may be absent after a crash.

Hypothetical candidates: the client must be enrolled/eligible, connected, have a verified numbered shard, and already hold a fresh same-shard answer marked replace_scan before starting the probe. Price-only history cannot qualify. Only successful probes whose observed price agrees within ten percent and whose shard did not change contribute hypotheticalCandidateMs. This is an opportunity indicator, not permission to skip a scan or evidence of achieved savings. The ten-percent comparison tolerance is reported explicitly and does not establish equivalent trading decisions. Order reads have no replacement estimator yet. Actual skipped reads and actual saved milliseconds remain zero in this shadow-only build.

Reporting: sum connected/read milliseconds and counts across sessions for each account, then combine raw totals (not averages of percentages). Baseline reading share = (probeReadMs + orderReadMs) / connectedMs; report it only after completed reads over a sufficiently long window. Hypothetical opportunity share = hypotheticalCandidateMs / connectedMs, clearly labelled as an estimate with the sample counts and price tolerance. Per-bot minutes/hour is 60 times that share. Two bots' combined minutes are bot-minutes, not elapsed wall-clock minutes. No projected profit claim follows from saved reading time.

Before public claims, run a long baseline and a comparable enabled period on the same build, item pool and settings, record the actual reads skipped, API latency/fallbacks and completed SOLD-position booked profit/sales, and compare reading seconds per connected hour. State the observation window and sample size. Current measurements cannot support a statement that the feature has reduced reading or increased profit because no reads are skipped yet.

## Anonymous order levels

The VPS legacy-data audit found no owner in 618,000 stored order rows, including their tail fields. Unknown-owner plain-item rows now use order_level when advertised by capabilities. Only the closed numeric fields itemKey, unitPrice, total, delivered, observedAt, depth, page and source are contributed; no raw tail, parts or descriptor is sent. Named rows retain the original order/sweep path. Component/part-bearing rows remain held back pending canonical identity support.

Depth counts identical rows within one page observation, with different delivery quantities and different pages kept separate. It does not sum appearances over a sequential sweep into simultaneous global inventory. Levels contain no owner, scope, sweep id or sequence. The original sweep's records count continues to count only named orders, so a zero-record partial sweep can coexist with successful order_level uploads. Local coverage status additionally exposes orderLevelsQueued and anonymousRowsQueued. The receiver query block remains history-only and is not used by client scan-replacement or price-comparison logic.

No new in-game reads are introduced and unknown shard labels remain omitted. This type solves anonymous numeric order contribution; it does not establish actionable depth, own-order exclusion, sweep completeness or exact-region evidence.

## Expanded order-level coverage

The client quantity ceiling now matches the receiver's 1e12 total/delivered contract (delivered still cannot exceed total). Visible vanilla enchantment, potion-effect, attribute and smithing-template parts are sent in their original order, up to 12 lines, with the base item/stack key. The receiver explicitly includes parts in anonymous level identity; the legacy Java component hash is not sent or pretended to be canonical SHA-256. Local depth grouping also includes parts, so different variants are never combined with each other or plain items.

Raw custom lore, names, unknown metadata and descriptors are not uploaded. A closed allowlist preserves supported public vanilla fields without mutating unknown text into a different market identity. Rows with unknown parts, too many lines, or an opaque component hash without visible parts remain held back. This expands enchanted equipment/book coverage without claiming every possible lore-bearing item is supported. Levels remain history-only; no scan skipping or trading-policy change.

## Network market scope (section 14)

Market scope is net-donut only while the client has a verified DonutSMP connection and deployed capabilities advertise networkScopes including net-donut, regionField=region, and regionQueryParameter=region. It is independent of physical shard detection. Each queued observation captures its scope at observation time; the batch packer never mixes unscoped and scoped records, sends region only at batch level, and preserves the frozen pending body's scope across retries/restarts. Older unscoped records are not reclassified. Queries use region=net-donut and cached answers must match the current market scope.

Physical observerShard is local status only and is never transmitted in the community protocol. Network-scoped observations do not locate a player or staff member within a shard. Performance candidate comparisons use the network market scope, but price-only and order-level history still cannot replace a scan. No scan-skipping, enforcement or public rollout change.

## Read-reduction Phase 1 preparation (not installed)

Prepared capped and levels marker fields only. Capped requires a successful full-read intent, reaching the configured page limit with a next-page control still present, and no navigation failure/end-of-book evidence. The wire levels count is the number of emitted order_level records after per-page grouping, as defined in the supplied revision-2 proposal. Distinct identities (itemKey, parts, price, total) may be fewer than emitted records across sequential pages; the snapshot count tolerance needs section-15 clarification. The legacy complete flag remains conservative (known-owner completeness) and is never true for a capped read. The naturally exhausted anonymous-read case needs an explicit section-15 semantic agreement rather than changing legacy expiry behavior. No trading scheduler, order-book retirement or read-skipping behavior is changed.

Pending exact section-15 JSON and read membership/pagination contract: marker response, snapshot response and row shape, relationship between marker levels and distinct snapshot rows, and whether complete denotes physical scan completion independently of named-order completeness. No snapshot consumer or wouldSkip comparison is active yet.

Phase 1 must retain all live reads and use an isolated comparison snapshot, not mutate the local trading order book. A capped snapshot cannot prove unseen orders gone. Anonymous equal-price/total rows do not reliably distinguish the bot's own order from a rival; ambiguous own-order subtraction must block eligibility. Market-level top bid agreement alone cannot establish equivalent trading decisions. Marker age and per-row observation age must both be checked. Actual reading savings remain zero until a separately approved trial.

## Section 15 Phase 1 collection (supersedes the preparation notes)

The exact contract has been received and copied into COMMUNITY-CONTRACT.md. Anonymous wire complete means physical page exhaustion, independent of named-order ownership, while the local OrderBook's existing completeness/retention behavior is unchanged. Driver flags track this wire fact separately. Capped means reaching the full-read page limit with another page available and no navigation failure. levels counts emitted records, while the server marker rows and snapshot rowsTotal count distinct frozen identities; no five-percent comparison to emitted levels is performed.

At each normal full-read start, opt-in clients fetch the same-network marker and, if sameOwner=true/self=false and sufficiently fresh/covered, every immutable snapshot page. This runs on a dedicated background executor with two-second requests, at most 25 pages/25,000 rows/8 MiB, no redirects, and existing contributor credentials only. Each page must keep the read identity, coverage fields and total unchanged; pagination, exact row count, duplicates, quantities, parts, scope and timestamps are validated. A missing/foreign/self/stale/malformed/oversized snapshot records a reason and does not affect the live scan. Physical shard is not part of these requests.

After the unchanged live full read, the client records missing/extra level identities, changed delivery/depth, and highest bid differences on managed plain markets. Unknown lore held back from community serialization is counted as unsupportedLiveRows, not silently treated as complete community coverage. Only summaries are persisted in the private per-installation gonuts-community/order-shadow.json (last 30 cycles, at most 50 detailed bid differences each, plus total difference count); the latest summary appears under community.orderShadow in local status. No player names or credentials are stored in these summaries.

This is measurement collection, NOT a completed skip policy or a trading-decision simulation. tradingDecisionComparison=not_simulated, wouldSkip=false, skipped=false and actualSavedMs=0 are explicit. snapshotDataEligible indicates preliminary data checks only. Unknown own-order state and nonempty anonymous own-order matches block eligibility; no heuristic subtraction is presented as proven ownership. The trading decision engine and the large-order/consecutive-skip trial policy still need a separate evaluation before Phase 2. No snapshots enter the trading OrderBook, no scheduling changes are made, and all price/quick/discovery reads remain unchanged. Never approve Phase 2 using these row/bid comparisons alone.

## Separate post-read transport validation

The client now performs bounded read-only snapshot validation at startup and 90 seconds after each successful natural full read, retrying at most ten times at one-minute intervals when no suitable other-hive marker exists. Both marker and snapshot requests carry purpose=validation. Self reads and foreign-owner reads are never used. Successful validation is persisted separately in gonuts-community/snapshot-validation.json and exposed as community.orderSnapshotValidation, retaining the latest attempt and last success.

Validation fetches all pages under the same scope/read/count/quantity/metadata checks as Phase 1, but may inspect a snapshot retained for up to 24 hours. This tests transport/schema/pagination, not its fitness to replace a read. Pre-read eligibility keeps its separate 15-minute freshness rule. Validation records explicitly say phase=post_read_validation, validationOnly=true, candidateBeforeLiveRead=false, skipFreshnessEvaluated=false, wouldSkip=false, skipped=false and actualSavedMs=0. Nothing enters the local order book and no extra game reads or trading commands are issued. Normal live-read comparisons remain separate and retain their original decision time.


## Page-streamed order uploads (private Phase 1)

Each successfully parsed order-house page now queues its sanitized named orders and anonymous order levels immediately, using the existing background uploader. Page navigation retries do not emit the same page again. Per-page depth and variant filtering are unchanged. The controller no longer re-uploads the whole read at completion.

A local acknowledgement barrier counts every queued stream record. The end marker is released only after the read has ended and every containing HTTP batch has returned an unrejected, non-degraded success whose accepted-plus-duplicate count matches the request. A retry/replay can satisfy the same pending batch once. 429, 5xx and timeouts keep the existing stable pending request and do not release the marker. Unsupported types, queue overflow, abort, scope changes or permanently refused records invalidate the stream; its historical levels remain usable, but no full-read marker is advertised from it.

The barrier metadata is local only, absent from wire JSON. If a process restarts during a read, its unfinished barrier cannot manufacture an end record. A persisted end-marker batch can only have been created after all preceding stream records were acknowledged. Unrelated queued follow/payment events still use the same bounded FIFO, 500-record cap, 10-second upload cadence and rate-limit handling. No server limits or protocol fields changed.

Local community.orderCoverage.streamUpload reports recordsQueued, recordsAccepted, recordsPending, endMarkerQueued and uploadState. endMarkerQueued means queued for upload, not proof the server accepted it; the VPS must verify marker acceptance and frozen row counts. Streaming should reduce post-read lag; it does not guarantee a peer snapshot exists ten seconds after a fast full read, nor does it establish decision equivalence. All reads, shadow mode and public rollout gates stay unchanged.
