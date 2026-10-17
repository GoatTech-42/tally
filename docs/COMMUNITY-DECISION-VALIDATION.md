# Private order-read decision validation

Status: Phase 1 only. No skipped reads, order-book replacements, public push, or release.

## What is compared

For each natural full order read, the peer snapshot is requested before the read starts. It must satisfy the existing exact-scope, same-owner, non-self, freshness, cap, paging and immutable-count checks. After the normal live read is recorded, the controller compares against the actual merged live OrderBook (including retained history), on its own thread and at one evaluation time. It never runs a second stateful bid desk.

1. **Order-backed purchases:** run the production orderCandidates selector with each book, keeping current policy, budget, inventory, market statistics, filters and hive allocation. Compare ordered executable/ranking inputs, including price, quantity, margin, score and ranking; explanation strings are excluded. Report unordered membership and rolling-history effects separately. Matching empty candidate lists supplies no purchase evidence.
2. **Bid placement, ranking and chasing:** production and comparison use the same bestBids helper, including fresh direct search quotes. Exact equality of the entire map proves the public-book dependency supplied to both bid-desk paths is unchanged. If it differs, the result is unverifiable; it does not claim a different final bid was executed or even selected.
3. **Delivery versus auction listing:** compare every plain item's price/maximum-remaining envelope, not just its highest bid. Equal envelopes guarantee identical best qualifying delivery prices for all position quantities and target/profit thresholds. Different envelopes are unverifiable, not an assertion of changed realized profit.

Parts-bearing orders are excluded by all current production order-book decision consumers, identically on both paths. The real live book is used rather than the sanitized upload projection, so excluded custom variants cannot silently disappear from the baseline.

Results are written under decisionComparison in each private order-shadow.json cycle and exposed in community.orderShadow status. They contain counters, closed reason codes and booleans, no player identities, positions, credentials or raw logs. A missing snapshot, unavailable policy/book, or exception is unverifiable. HTTP is never awaited on the controller thread. Persistence stays on the background executor.

## Legacy whole-book verdicts (diagnostic only for advisory use)

- equivalent: purchase outputs and both full dependency projections match exactly for this evaluation context.
- different: production purchase candidate outputs differ. This does not claim a different final executed purchase.
- unverifiable: a bid/delivery input changed or evaluation could not be completed. This is never counted as a pass.

The original exact-input acceptance proposal concerns replacing the whole live book. It is not the acceptance test for the historical-guidance role agreed with the user. Expected market churn is reported, not treated as a transport fault or grounds to reject background guidance. Own-order identity ambiguity blocks anonymous-book replacement but does not prevent suggesting an item to search: Your Orders remains the authority for ownership.

Before any reduced-read trading trial, validate nonempty guided markets, successful fresh targeted checks, handling of filled/missing orders and moved prices, current own-order/cap/guard checks, and actual execution-path behavior. Record reading time and trading outcomes separately; do not infer profit or time savings from a read-only baseline test. Current code grants no skipping/trading authority. A longer acceptance run and operator approval remain required before rollout.

## VPS handoff

No server changes are required. Continue auditing normal full-read markers and frozen snapshots. The new client adds decisionComparison results locally; uploads and protocol remain unchanged. Snapshot transport validation is separate and must never be counted as a pre-read decision comparison. Shadow, allowlist, enforcement and public rollout stay unchanged.

## Historical guidance and targeted checks (private, read-only)

The isolated comparison now also validates the intended background-data role. After the baseline full read, it refreshes Your Orders and selects at most three allowed plain-item markets from the peer snapshot and local follow-derived market history. Follow history supplies discovery only; it never marks an order filled, removes a row, or supplies a current executable price. Snapshot rows stay separate from the local book and own-order ledger.

Each selected market is searched live using the existing read-only ORDER_QUOTE path. A new-bid preview uses only that live quote, current price/spend caps and a fresh Your Orders read. A missing or filled order, failed/expired search, guard, stale own-order read, or already-owned market produces a fallback/review reason rather than using the historical bid. A quote verifies a price on the searched page, not deliverable quantity, ownership, or a final executable trade. Placement and delivery still require their actual live validation.

The cycle records decisionComparison.backgroundGuidance with selected/check counts, per-market historical versus live prices, closed outcomes, and preliminary unit prices. It explicitly says sharedPricesExecutable:false, decisionEquivalenceProven:false, actionsDispatched:false, skipped:false and actualSavedMs:0. Existing strict comparison diagnostics are retained separately and are not reclassified as passing because this advisory path works. Empty purchase lists remain no purchase evidence.

The full read is still performed as a baseline. No scan skipping, production order-book replacement, trading enablement, public rollout or server protocol change is introduced. The new checks measure whether background guidance leads to useful targeted live searches; they do not claim whole-book equality or monetary savings. Guard interruption during the extra checks retains the already-completed full-read result and labels advisory validation incomplete.

## Paper trade simulation v2

Each guided market now also gets a matching full-stack live auction probe. Quote evidence captures individual order remaining-quantity lower bounds, subtracting displayed delivered-count uncertainty; duplicate rows are never combined into fictitious capacity. Public auction rows have no seller identity. Auction evidence therefore records anonymous prices for exact-count plain unworn lots. Rows that overlap an audited own listing by item, quantity and displayed price band are conservatively excluded as possible own listings; unmatched rows do not prove another seller or complete hive exclusion. Reports explicitly retain auctionSellerKnown:false and ownAuctionExclusionComplete:false. These observations support paper economics only; no public seller-label parser or inferred ownership is required.

The paper buy-and-deliver calculation uses the live auction upper price bound and the highest currently observed order that can take the entire lot. It tests freshness, current own-page ownership, guards, retained unresolved exposure, purchase/outstanding caps and configured minimum profit/ROI. Cap diagnostics separately show resale ceiling, per-purchase cap, outstanding limit, committed spend and remaining room. No limits are raised to produce a positive result.

Observed GUI quantity is not reserved availability: auction rows may be ghosts and live orders can fill after the read. Every result retains exactConfirmationRequired:true, availabilityReserved:false and actionsDispatched:false. A paper_candidate_qualified is a candidate for actual execution-path preflight, not authority to buy or deliver. A retained hold can still show hypothetical economics but remains a blocking reason. ORDER_COMPARE_ONLY retains its full-read baseline. ORDER_GUIDANCE_ONLY performs fresh own-order reads, peer snapshot lookup and targeted checks without an Alt full sweep or full end marker. Both report actualSavedMs:0: an isolated diagnostic run does not demonstrate savings from a skipped production scan.

## Passive follow evidence

Live chat verified on October 16 supplies attributed listed, bought and ordered events with item, quantity and displayed total price. Payments provide amounts and parties but no item market; sold-multiple-items notices provide only an aggregate amount. Listings are not confirmed sales, purchases do not identify the seller, and order notices do not prove remaining capacity. Follow events can guide market discovery and historical prices without menu reads, but do not join an anonymous public auction row to a seller or prove present availability. Shared-journal counts may include repeated observations across clients and must not be called unique transactions without deduplication.

## Balance source for cap validation

The controller now uses SidebarBalance.best, the same selector used by Discord, before both its regular and isolated branches. A fresh scoreboard balance therefore works even when the market-feed balance is unknown; exact API values are retained only while consistent with the displayed scoreboard range. Demo feed balances are rejected. The isolated bank-scaled test reports waiting_balance until a positive real balance has been observed, so startup fallback caps cannot be presented as validated bank limits. Configured percentages, amounts and retained exposure are unchanged.
