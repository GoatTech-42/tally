# Execution and safety

GoNuts is fail-closed by design. Live commands and clicks are disabled by
default, and the driver refuses to act on anything it cannot verify. This page
explains the modes and the gates between them.

## The modes

Press **B** to open GoNuts. Open **Trade** (formerly **AutoPilot** or
**Automation**) for current readiness and session controls. **Set up trading**
provides the guided **Choose limits → Review & Start** flow. The execution
capabilities below describe the safety boundaries; they are not a list of current
navigation tabs:

- **Observe (default).** Opportunities and demo data are view-only. Nothing can
  trade. Opening an opportunity's detail only navigates; it never sends a buy.
- **Preflight (one shot, zero clicks).** Uses only the independent
  `preflightEnabled` / `preflightServers` gate. It rebinds the candidate to the
  latest scan, sends one `/ah search`, waits for a stable auction GUI, verifies
  the exact id, stack, seller, and authoritative total price, captures the
  screen and lore evidence, and stops before the first slot click.
- **Single (one complete trade).** Uses the full live and continuous gates. It
  selects one fresh, completed-sales-backed candidate, buys it, prepares the
  exact resale stack, relists it, then monitors until a trusted sale notice and
  a newer active-listing snapshot reconcile the listing as gone. It stops after
  that one fully reconciled trade.
- **Continuous.** Same gates and lifecycle, then chooses another candidate only
  after the previous sale is reconciled and its capital released, always inside
  every configured session limit.

**End** is the default emergency-stop key, including during an active preflight.
**B** opens the interface. If you changed bindings, check Minecraft Controls.

## What a real trade requires

An automated buy needs an exact item id, exact stack size, exact seller, and one
unambiguous authoritative `Price`, `Cost`, or `Total` lore field in a non-player
GUI slot. GoNuts refuses incidental per-unit or fee amounts, ambiguous listings,
and ambiguous confirmation controls, and marks a purchase complete only after
the expected inventory quantity change.

Continuous automation is gated behind **all** of these at once:

- `authorizedExecutionEnabled: true` and an exact `authorizedServers` match;
- `continuousAutomationEnabled: true` and an exact `continuousAutomationServers` match;
- an exact `notificationServers` match (a trusted sale notice is required before
  a position can be reconciled and capital released);
- `auctionFeesConfirmed: true` with valid `listingFeeFlat`, `listingFeePercent`,
  and `saleTaxPercent` (a confirmed zero is allowed);
- positive `continuousMaxPurchasePrice` and `continuousMaxSessionSpend` caps.

The defaults are disabled, empty, unconfirmed, and zero, so an older, partial,
malformed, or freshly generated config cannot start live automation. A present
field with the wrong type, a non-finite number, or an out-of-range value locks
the matching policy rather than silently substituting a weaker value.

## Reconciliation, not optimism

A trusted sale action-bar event is only a **notice**. Capital stays committed
until two newer, complete, stable, zero-error full-book scans agree the exact
bound listing disappeared. Only then is the sale reconciled and another
candidate eligible. The server reporting a gross sale amount does not by itself
prove fees, cost basis, net profit, or that the sale belongs to a tracked
position.

## Crash recovery

### If Trade says “Trade interrupted — check before continuing”

This means the bot could not verify how an interrupted trade finished. It has
paused new purchases to avoid buying twice. Open **B → Trade** and follow the
next step shown at the top of the session card:

1. **Item verified in inventory:** choose **List the bought item, then continue**.
   Normal listing checks and your existing limits still apply.
2. **Item not verified:** check your inventory and your own auction listings.
   Open **Show recovery steps** for a readable checklist. Only if the item is in
   neither place, choose **1: I checked both places** in that dialog.
   If you find it, keep it; report the problem if the screen
   does not recognize it. Do not confirm that it is missing.
3. **Checking listings:** close the panel with **Esc** so the bot can perform its
   fresh checks, then reopen **Trade**. A waiting button is disabled on purpose.
4. **Checks ready:** open **Review final check**, then confirm **2: Confirm neither place** only if
   that remains true. Wait while the confirmation is saved.
5. Once the screen says it is ready, choose **Resume trading**. Existing spending
   limits remain in place; recovery confirmation does not itself start trading.

If checks keep waiting, the records cannot identify a single interrupted trade,
or a button refuses your action, send a bug report from that screen. Include the
displayed **Last action** message. Do not force-reset or remove recovery records.
An update can prevent a previously fixed cause, but does not automatically erase
an existing unresolved trade.

Real-automation checkpoints are written by a dedicated SQLite worker, never on
Minecraft's render thread. Session open, pre-command intent, verified position,
caps, and reconciled closure each get a durable acknowledgement at their safety
boundary. After a crash or restart, any open session or uncertain exposure loads
before Start can unlock and appears as a non-resumable PAUSED session. Clearing
recovered exposure takes two separate confirmations around two newer complete
scans plus an immutable local audit record; caps are kept until the session is
deliberately resumed or ended.

## Restart queue bypass

In **Settings → Session**, **Restart queue bypass** is enabled by default.
An explicitly saved off setting stays off. When the server confirms that this
client is in its restart waiting room, GoNuts waits for a safe trading checkpoint,
disconnects, and connects directly through a different regional address. The
default list is `east.donutsmp.net`, `west.donutsmp.net`, `asia.donutsmp.net`,
`oceania.donutsmp.net`, `eu-central.donutsmp.net`, and `eu-west.donutsmp.net`.
The current connection address is skipped. **Addresses to come back in through**
can specify a custom order of up to six addresses on the same network.

Failed joins and regions that return the client to the waiting room move to the
next address after a retry delay. A working region is kept; GoNuts does not
bounce back to the main address. A fresh balance sidebar and a stable game world
must be verified before the restart-paused session can resume. Manual stops,
staff holds, ownership checks and unresolved-trade recovery remain authoritative.
Trade records and spending limits are retained. Leaving the waiting room can
lose the previous world position, as the server warns. This is recovery from a
confirmed restart, not an instruction to leave during every restart warning.

## Auction confirmation delays

If a sell confirmation, listing receipt or server refusal arrives while a sell
retry is queued, GoNuts cancels that retry and processes the response. It does
not close the delayed confirmation or send another sell command over it.
Receipt-only listings still require the normal inventory verification; a receipt
alone does not clear a trade. A changed hand still blocks a queued sale.

If the server never opens a confirmation or reports a sale, the attempt stays
bounded and the stock stays subject to recovery checks. Bug reports distinguish
confirmation timeouts, commands that could not leave the queue, changed stacks,
unavailable worlds and server refusals without copying private message text.

## GUI captures

On the first listing GUI and first confirmation GUI of each session, GoNuts
captures a screenshot and a paired slot and tooltip dump to
`.minecraft/doughbay-captures/`. The dump includes the GUI title, slot numbers,
item id, stack count, every tooltip line, and every parsed price. If the server
changes its lore format, the driver aborts without clicking, and the capture is
the exact fixture needed to update the parser.

## What it will not do

GoNuts does not bypass anti-cheat, conceal that it is automated, guess at
ambiguous controls, or weaken the emergency stop. It does not yet fingerprint
enchantments or arbitrary components from GUI stacks, so it executes only when a
listing is uniquely verifiable by id, stack size, lore price, and optional
seller. Any unknown lore format, duplicate candidate, unclear confirmation,
timeout, disconnect, or unexpected inventory result aborts the operation.

## The rules

**DonutSMP bans scripts, macros, and auto-clickers**, and enforcement can reach
every account linked to a player. Analysis, pricing, and paper trading carry no
such risk. Running live execution is a real ban risk that the account owner
chooses to take. GoNuts is not affiliated with DonutSMP.
