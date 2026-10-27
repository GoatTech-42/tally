# GUI tour

Press **B** to open GoNuts. Five aligned workspaces organize navigation: **Trading**, **Markets**, **Accounts**, **Safety** and **Settings**. Each workspace shows its own pages. The in-game layout fits the window independently of Minecraft GUI scale, including compact windows. Observe mode does not execute trades; changing a setting remains an explicit action.

A new install opens **Start here**. Reopen it with **Settings → Help & setup**, then **Set up trading** for **Choose limits → Review & Start**. Opening setup or a tutorial never starts trading.

**Trading → Auction** and **Trading → Orders** provide direct controls. **Settings** opens four Simple groups: Order management, Trading style, Action pace and Order batch ceiling. Bundled changes show their exact before/after values and require Apply. **Custom — preserved** keeps unmatched values. Switching views changes no saved settings. **Advanced view** exposes all individual controls in searchable categories. See the [settings handbook](guide/settings.html).

**Settings → Advanced view → Tutorials** contains the updated optional lessons. **Accounts → Swarm / Hive setup** holds connections; the hosted Swarm and dashboard services are retired, so new installations cannot be linked; **Safety → Evasion / Staff Watch / Region Map** holds protection controls.

The Dashboard shows realized profit, active listings, a profit
chart, the top opportunities, and recent activity. The ledger always shows real
records, even when market opportunities use explicitly labeled demo data.

The rounded cream-and-teal robot has an upright antenna and independently
animated eyes, eyelids and mouth: reading scans, rolling dollar reels, and a
bored idle with glances and yawns. It appears once on the Dashboard.
**Settings → Advanced view → Display & alerts → Reduced motion** keeps its expression still and
removes HUD movement. Alerts have a visible lifetime bar and avoid other GoNuts
HUD panels; the key legend and nearby-player panel use the same visual theme.
The HUD separates state, slot usage, committed money, and current action.
Status and alert text wrap, with card height measured from the full text.
The capacity grid fills the card width, using larger, evenly spaced slots.
**Preview HUD** on the Dashboard displays labeled sample values for 30 seconds
and cycles through reading, sale, idle and working animations.

> Screenshots are captured in the mod's built-in demo mode (sample data, never a
> live account) and will be added per tab below.

---

## Opportunities

A ranked set of opportunity cards showing resale flips the engine currently likes, each
with its expected margin, ROI, and estimated hold time, all backed by completed
sales. Click one for a detail view with a 12-hour completed-sale chart and the
price bands behind the call. In Observe mode this is a shortlist to read, not a
buy button.

## Markets

The live scan roster: every market being watched right now, marked as pinned,
auto-selected, or held. Each row shows the price bands, liquidity (sales per
hour), and confidence, so you can see at a glance which markets are trustworthy
and which are thin.

## Search

Look up any item, even one that is not in the current scan set. Type a name and
GoNuts pulls its market: the price bands, the chart, and how fresh the data is.
Useful for checking a hunch before you pin something.

## Items

A creative-style item grid for editing the include and exclude lists. Click
items to pin markets you always want scanned, or to deny ones you never want
touched. This is how you shape what the bot pays attention to.

## Performance

How trading actually went, in game: realized profit, win rate, median ROI and
hold, best and worst flips, per-market profit and loss, and an equity-over-time
curve. The same performance picture the CLI `report` command prints, without
leaving Minecraft.

## Rivals

The competition, reconstructed from the sales feed: who else is flipping, the
hours they are active, what they trade, and how they price. It also surfaces the
**underdog's shadow markets**, markets a rival has proven profitable that you
can quietly shadow.

## Orders

The order house, a buy-order book no public API exposes. This tab shows your
standing bids, what has been collected and delivered, and the depth of the book,
so you can trade the demand side as well as the listings.

## Trade (formerly AutoPilot)

AutoPilot is still available under **Trading → Trade**. The Dashboard shortcut is
**Manage trading**. Older walkthroughs may say AutoPilot or Automation.

For guided setup, choose **Settings → Help & setup → Set up trading**, set your limits in **Choose limits**,
then review readiness in **Review & Start**. Starting is a separate, deliberate
choice. Existing item choices, protections and recovery checks still apply.

On **Trade**, **Continuous** shows readiness and session controls. If live trading
is off, review **Max per trade** and **Max per session**, select **Enable live
trading**, and click again to confirm. Then review readiness before **Start**.
An unresolved recovery or exposure blocker must be investigated, not bypassed.

The page selector cycles through four views:

- **Continuous:** the live buy, relist, and track engine, with the session
  spend and trade caps, plus start and stop. Off by default and gated behind the
  full execution allowlist.
- **Tools:** setup and proving: the API-key card, and a one-shot test buy and
  sell for validating the live GUI format.
- **Discord:** the command-centre setup: bot token, channel, and operator.
- **Web:** local web-panel setup.

From Continuous, choose **Tools: API key / test**, then **Discord command
centre**, then **Web panel**, then **Back to Continuous**. Linking Swarm and
configuring Discord are separate from starting a local trading session.

See [Execution and safety](execution-and-safety.md) for exactly what has to be
true before a real trade can happen.

## Activity

The most recent 80 alerts from this game session, including their complete text
and timestamps. Popups can expire without losing the event here.

## Diagnostics

The health panel: feed freshness, request-budget usage, the liveness verdict,
window state, and which gates are open or closed. This is where you confirm the
bot is actually turning over rather than quietly stuck.

## Logs

The recent log lines, scrollable, right inside the panel, so you can watch what
GoNuts is doing without alt-tabbing to a log file.

## Payroll

Hive profit-sharing. Define rules that pay a share of profit out to accounts,
and see the payment history. For groups running several accounts together.

## Settings

Every tuning knob, grouped by feature: Adaptive market, Buying, Selling, Tiers,
Pacing, Rivals, Evasion, Orders, Boxes, Consignment, Stash, Slots, Multi client,
HUD, Session, and more. Each setting has a toggle or a value, with plus, minus,
reset, and preset groups. Changes take effect live.

## About

Version, credits, and links.

---

Back to the [README](../README.md).


## Money and Orders account views

In local, hosted and Discord panels, choose one account for its records or All accounts for the combined Hive. Order rows are account-labelled. Money retains its existing sales, profit and auction summary, adding unfilled orders, committed order funds, delivered stock awaiting collection and observed full-order completions in the last hour/day. Missing or stale reports are unknown rather than borrowed from the host. Completion history begins with this update and does not reconstruct offline activity. Total auction capacity sums each member's own reported limit: three 90-slot members total 270.

The reporting selector does not redirect settings writes. Open the intended client's own panel to edit its settings.
