# Choosing trading items

Use **Items → Include/Exclude** to choose eligible trading items, and **Track** to collect their market information. Tracking alone is not a purchase instruction. New buys, orders and targeted price lookups use the same Include/Exclude rules; there is no separate purchase-focus list in Settings. Price evidence, margins, demand, account ownership, stock, spending and safety limits still determine whether a trade is possible.

## Upgrading an existing setup

The old **Focus new purchases on these markets** setting is retired. Its selections are merged into the Items Include and Track lists on first load. Existing visible inclusions and tracked markets are retained, and explicit exclusions and liquidation choices still win. Exact Include tokens such as `=golden_apple` remain exact. A focus-only setup becomes an explicit include list rather than enabling every market. When the old watch list was not yet managed in Items, its configured markets are carried forward too.

The original settings file is backed up as `tuning.before-item-selection-migration.json`. After conversion the legacy field is removed, so later Items edits are not undone at the next start. Old saved presets and Settings codes convert through the same rules when applied; new exports do not contain the retired setting. Review the resulting Items selections after an upgrade, especially when the old hidden focus and visible Included list disagreed: the combined visible selection now takes effect.

Include/Exclude rules also affect existing-stock handling. Keep an item eligible while its stock is being managed; this change does not erase, reset or presume sold any inventory, position, delivery or pending action.

Pacing still controls **Maximum time per auction watch**. The default 45-second budget returns an unsuccessful watch to other work without interrupting a selected purchase during verification. Deep box sweeps get at least 150 seconds; 0 retains an unlimited time budget with the ordinary look-count limit.

To let multiple accounts use the same item pool, turn off **Split the markets between accounts** in Multi client settings. Each account still applies its own live prices, orders, inventory and trade gates, and cannot buy a recognized managed hive account's auction listings.
