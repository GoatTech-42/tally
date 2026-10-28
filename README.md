<div align="center">

<img src="docs/assets/tally-wordmark.png" alt="Tally" width="440">

### Trade the DonutSMP auction house on autopilot

![License](https://img.shields.io/badge/license-MIT-blue)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-brightgreen)
![Mod](https://img.shields.io/badge/mod-Fabric-orange)
![Java](https://img.shields.io/badge/Java-25-red)
![Execution](https://img.shields.io/badge/live%20execution-off%20by%20default-lightgrey)

</div>

**Tally is a community-maintained fork of [GoNuts](https://github.com/cody-raves/Gonuts).**
The upstream project ended public maintenance and support after seven weeks of
development; see [RETIREMENT.md](RETIREMENT.md) for the upstream record. Its hosted
services (Swarm, hosted dashboard, bug reports, diagnostic upload) are retired and
stay retired here. Local trading, market history, settings, ownership records and
recovery safeguards are unaffected.

Tally learns real prices from completed sales, hunts flips on both sides of the
market, and can run the whole play from bidding to reselling. Safety gated, and
off by default.

**Download:** no Tally release is published yet. The first release will land on the
[releases page](https://github.com/GoatTech-42/tally/releases); building from source
works today (see Quick start). The in-game updater watches this repository's releases
only. [Upstream GoNuts releases](https://github.com/cody-raves/Gonuts/releases/latest)
remain available, with public support ended.

## Retired hosted services

No diagnostics or reports are uploaded to the retired hosted services: the retired Swarm, hosted dashboard, bug receiver and
diagnostic upload services are walled off, and a saved config that still names the
retired host is normalized to the direct DonutSMP API instead. Everything Tally does
runs locally, against your own self-hosted hive server if you run one, or through the
public DonutSMP API.

## Where is AutoPilot?

Open Tally with **B**, then choose **Trade** (the current name for AutoPilot).
The Dashboard shortcut is **Manage trading**. **Set up trading** opens
**Choose limits → Review & Start**. See **Tutorials → Find AutoPilot / Trade**
in game. Opening a tutorial never starts trading.

## Why Tally

Most auction tooling reads the current asking prices and guesses. Tally does the
opposite: it collects every completed sale, builds its own long-term price
history, and values items from what things **actually sold for**, not what
someone is hoping to get. Then it puts that edge to work, and keeps adapting as
the market moves.

## What it does

|  |  |
|---|---|
| **Prices from real sales** | Recency-weighted percentiles, outlier filtering, a conservative quick-sale value, and manipulation checks that lower confidence instead of trusting a rigged market. |
| **Works both sides** | Ranks resale flips across dozens of markets, and reads and bids the **order house**, a book no public API exposes. |
| **Adapts live** | Benches markets that stop paying (on a rolling window, so they cycle back when they recover) and leans into the hours each market actually fills. |
| **Executes safely** | Buys exact-match listings and relists at a conservative target, booking a sale only after independent evidence reconciles it. Spend caps, dry run, emergency stop. Opt-in, off by default. |
| **Paper trades** | A leak-free simulated bankroll for backtesting before any real coin is at risk. |
| **Watches rivals** | Reconstructs who else is flipping, when they are active, and how they price. |
| **Scales to a clan** | One self-hosted server on a single Donut key, with per-member keys, so a whole hive trades on one budget and a shared, sharpening price history. |
| **Runs from Discord** | One live embed of the trader's state, with buttons to start, stop, and tune it. |

## See it in action

A full in-game control panel: opportunities, live markets, the order house,
rival intel, performance, and every setting, all in one screen you open with a
keypress.

## Documentation

New here? Start with **Getting started**, then skim the **GUI tour** to see every
tab.

| Guide | What's inside |
|---|---|
| [Getting started](docs/getting-started.md) | Build, install the mod, and the first steps in order |
| [GUI tour](docs/gui-tour.md) | Every tab: what it shows, and why it is there |
| [How it works](docs/how-it-works.md) | The valuation and decision pipeline |
| [Configuration](docs/configuration.md) | API key, `config.json`, and the server allowlists |
| [Execution and safety](docs/execution-and-safety.md) | The trading modes and every permission gate |
| [Hive and clan server](docs/server.md) | One Donut key, many members |
| [Discord control panel](docs/discord.md) | Run the trader from a Discord channel |

## Quick start

Requires **JDK 21** (Minecraft 1.21.11). The 26.x line needs JDK 25 and lives on its own branch. From the repo root:

```bash
./gradlew build
java -jar trader-cli/build/libs/doughbay.jar doctor
```

`doctor` validates the data pipeline against the live API without writing
anything. The Fabric mod jar lands in `trader-fabric/build/libs/`; drop it in
your `mods/` folder. Full walkthrough in [Getting started](docs/getting-started.md).

## Safety and honesty

Tally starts in **Observe** mode and cannot place a real trade until execution
is explicitly enabled and the current server is on an exact-address allowlist.
It aborts on any ambiguity rather than guessing, and it never conceals that it
is automated.

**DonutSMP's rules ban scripts, macros, and auto-clickers**, and enforcement can
reach every account linked to a player. The analysis, pricing, and paper-trading
features carry no such risk; running live execution is a choice to take a real
ban risk, made by the account owner. Tally is an independent project, not
affiliated with or endorsed by DonutSMP.

## Upstream and license

Tally continues [GoNuts](https://github.com/cody-raves/Gonuts) by cody-raves,
released under the [MIT](LICENSE) license, which Tally keeps. The web panel is
set in [Monocraft](https://github.com/IdreesInc/Monocraft) by Idrees Hassan,
bundled under the SIL Open Font License 1.1; the font and its licence are in
`trader-fabric/src/main/resources/doughbay/web/`.

### Interface and account views

Open **B → Settings** for four Simple control groups with a before/after preview.
**Advanced view** keeps individual settings grouped by task; switching views never
changes saved values. Open **Help & setup** to revisit setup and tutorials. The
Money and Orders panel account selector scopes figures and rows to one account; All
accounts combines the Hive. Order funds and delivered stock are separate from
auction cost and asking revenue. Missing or stale order reports remain unknown.
