# Focusing new purchases

Settings → Buying includes **Focus new purchases on these markets**. Use a comma-separated list of item names or IDs; `=golden_apple` matches only golden apples, while `golden_apple` also matches enchanted golden apples. Blank preserves ordinary market selection.

The focus is a buy-only filter. Items must still pass the normal allow/deny lists, fresh price evidence, margins, demand, account ownership and spending limits. It never raises a buying ceiling or promises a profitable sale.

Auction buys, rival-following buys and new buy orders use the focus. Queued bids are checked again before sending. Unfilled orders outside the focus are cancelled through the normal order workflow; their delivered stock is still collected and sold. Existing inventory and listings remain eligible for normal listing, repricing and sale reconciliation. Owned stock also retains live price checks.

A focus can be chosen from the Items board and ledger using repeated profitable sales, recent earnings, sale speed and money tied up. Keep old prices out of the list: live market evidence still sets the actual bid and sale targets. A few high-profit sales are weaker evidence than a long record of profitable turnover. Old inferred sales remain subject to the limitations documented in the accounting patch.

This setting is a curated list, not an automatic learner. Review its markets as conditions change. The buy-order planner ranks eligible markets by expected dollars per resale lot with diminishing weight on current trading volume, rather than by percentage margin alone. Slot and per-market limits bound exposure; existing filled batches can take time to sell after the focus changes.
