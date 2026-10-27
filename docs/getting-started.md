# Getting started

## Build

The Minecraft 26.2 build needs **JDK 25**. The headless modules target Java 21
and `trader-fabric` targets Java 25. From the repo root:

```bash
./gradlew build
```

Artifacts:

- **CLI jar** at `trader-cli/build/libs/doughbay.jar` (headless: collect, scan, paper).
- **Fabric mod jar** under `trader-fabric/build/libs/`. Drop it in your `mods/` folder.

All headless, engine, and Fabric policy tests run in the normal build and must
stay green.

## In-game quick start

Join the supported server and press **B**. New installs open **Start here**. Reopen it through **Settings → Help & setup**, then choose **Set up trading**. Choose maximum per trade and session spend, review readiness and deliberately start. **Trading → Trade**, **Trading → Auction** and **Trading → Orders** hold trading controls. **Settings** opens Simple view; **Advanced view** exposes every individual setting. Switching views changes no saved values. Read the [settings guide](guide/settings.html).

Keyless mode needs no API key. Shared completed-sale history came from the hosted Swarm service, which is retired; keyless installs use local collection. Live prices, inventory, caps and safety gates still decide every trade; no immediate purchase or profit is promised.

The following API-key and CLI workflow is optional.

## Get an API key

GoNuts reads completed sales through the public Donut API. You need a Donut API
key. The easiest route is to enter it in game (see
[Configuration](configuration.md)); for the CLI, set it in the environment:

```bash
export DONUT_API_KEY=<your key>
```

**Never commit your key.** `.gitignore` already excludes local config and
databases, and the client redacts the key from every log and error.

## First steps, in order

Do not trust any number until the data behind it is proven. Work through these
gates in order; do not skip ahead because earlier output looked reasonable.

**1. The pipeline is sound.** Run `doctor` (about 4 API requests, writes
nothing). It checks auth, that responses parse into records, that
timestamps, sellers, and prices are plausible, that transaction hashes are
stable so de-duplication works, and that listings parse. Any `FAIL` means the
data is wrong; fix it before collecting for hours on bad assumptions.

```bash
java -jar doughbay.jar doctor
```

**2. History accumulates cleanly.** Run `collect --loop` for a few hours, then
re-run `doctor`. Counts should rise, duplicates should dominate re-fetches, and
parse failures should stay near zero. The engine needs 20 or more sales in a
market before it will value it at all.

```bash
java -jar doughbay.jar collect --pages 10
java -jar doughbay.jar collect --loop
```

**3. Valuations look sane.** Run `scan` and check the numbers against what you
know from playing. Are fair values roughly what these items really trade for? A
tool that prices ender pearls wrong is not ready to be believed about anything.

```bash
java -jar doughbay.jar scan
```

**4. Paper trade, judged over time.** Only then run `paper`, and let it run long
enough to compare predicted hold times and sale probabilities against what
actually happened. Expect the first config to be wrong; tune and re-run. `paper`
prints signals as they fire, periodic metrics (realized profit, win rate, median
ROI and hold, drawdown), and a simulated-equity chart. Positions and balance
history persist to `data/doughbay.db`.

```bash
java -jar doughbay.jar paper --balance 2000000
```

Only after paper trading is consistently profitable over many trades, and the
live auction GUI has been verified, should you consider enabling live execution.
See [Execution and safety](execution-and-safety.md).

## Where to next

- [GUI tour](gui-tour.md) to see the in-game interface.
- [How it works](how-it-works.md) for the valuation and decision pipeline.
- [Configuration](configuration.md) for the key, `config.json`, and allowlists.
