# Choosing trading items

Use **Items → Include/Exclude** to choose eligible trading items, and **Track** to collect their market information. Tracking alone is not a purchase instruction. Price evidence, margins, demand, account ownership, stock, spending and safety limits still determine whether a trade is possible.

## Focus new buying while keeping broader observations

In **Settings → Buying**, enable **Use trading focus** and set **Trading focus items** to the markets you want to prioritize for new purchases and bids. Use `=experience_bottle` for an exact item, with commas between items. Focus is off by default. An enabled empty focus buys nothing. Items exclusions and liquidation choices still take precedence.

Focus narrows new buying only. Broader market observation, price checks and management of existing stock and orders continue under the existing rules. It does not automatically cancel orders, sell stock, expand spending limits or make a market tradeable without valid quotes and demand.

Save your original configuration in **Presets** before changing it. You can save a narrower Focused preset and a broader Overnight preset, then switch manually. Turning focus off restores normal item selection. There is no automatic overnight schedule, learned profit ranking or shared universal best-items list; choose items using your own observed outcomes and acquisition/listing exposure. Zero sales without listing exposure does not establish poor demand. A short local trial does not establish a reliable profit improvement.

## Upgrading an existing setup

The old **Focus new purchases on these markets** setting is retired. Its selections are merged into the Items Include and Track lists on first load. Existing visible inclusions and tracked markets are retained, and explicit exclusions and liquidation choices still win. Exact Include tokens such as `=golden_apple` remain exact. A focus-only setup becomes an explicit include list rather than enabling every market. When the old watch list was not yet managed in Items, its configured markets are carried forward too.

The original settings file is backed up as `tuning.before-item-selection-migration.json`. After conversion the legacy field is removed, so later Items edits are not undone at the next start. Old saved presets and Settings codes convert through the same rules when applied; new exports do not contain the retired setting. Review the resulting Items selections after an upgrade, especially when the old hidden focus and visible Included list disagreed: the combined visible selection now takes effect.

Include/Exclude rules also affect existing-stock handling. Keep an item eligible while its stock is being managed; this change does not erase, reset or presume sold any inventory, position, delivery or pending action.

Pacing still controls **Maximum time per auction watch**. The default 45-second budget returns an unsuccessful watch to other work without interrupting a selected purchase during verification. Deep box sweeps get at least 150 seconds; 0 retains an unlimited time budget with the ordinary look-count limit.

To let multiple accounts use the same item pool, turn off **Split the markets between accounts** in Multi client settings. Each account still applies its own live prices, orders, inventory and trade gates, and cannot buy a recognized managed hive account's auction listings.
