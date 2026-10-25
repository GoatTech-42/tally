# Native own-order rejection evidence

An incomplete Your Orders page remains a failed read. Observed owned rows are retained; no incomplete read renews freshness, permits a new bid, changes capacity, or resets ownership or credits. A repeated count of 27 does not identify the rejected contents or prove that the latest client introduced them.

The existing diagnostic context now carries six closed numeric fields from the last native own-order page. This uses the existing report/diagnostic channel and consent settings; it does not upload names, tooltip text, NBT, account identities, settings, or inventory contents.

| Field | Meaning |
| --- | --- |
| `own_read_reject_name_mask` | OR of fixed word flags in rejected tile names |
| `own_read_reject_lore_mask` | OR of the same fixed word flags in rejected tile tooltips |
| `own_read_reject_item_mask` | OR of fixed item categories below |
| `own_read_reject_parse_mask` | OR of structural flags below |
| `own_read_reject_shapes` | Number of distinct combinations, capped at 45 |
| `own_read_reject_at_ms` | Native page observation time; zero means not observed |

For name/lore masks, bit i corresponds to this immutable order (starting at bit 0):

`max, orders, buy, donut, slots, more, upgrade, unlock, locked, empty, new, create, place, no, found, available, here, page, requested, delivered, each, price, amount, quantity, order, limit, reached, click, not, per`.

Item flags: barrier=1, glass pane=2, glass block=4, paper=8, other=16, air=32, player head=64. These categories are evidence only and never classify a tile as safe or clickable.

Parse flags: blank name=1, empty tooltip=2, requested word=4, requested pattern=8, each word=16, price pattern=32, delivered word=64, delivered pattern=128, section-format character=256, nonbreaking space=512, unsupported exact quantity=1024, fixed Donut+ spelling=2048, fixed Donut++ spelling=4096. Patterns are evidence of syntax presence, not verified trade data. Aggregated flags can come from different tiles; they do not prove a single tile contains every flag.

Each newly observed own-order page replaces the previous summary, including an empty summary after a page with no rejected entries. The time and existing completeness/unparsed count must be considered together. Navigation/page evidence and native parsing remain authoritative. Mixed shapes require a bounded local capture to separate them.

The existing one-shot `!inspect-orders` local capture now also retains non-quantity content-grid tiles in its controls array with their slot and the same closed masks. Previously those tiles were omitted entirely, even when all 27 caused the read to fail. It still requires an explicit local inspection request, opens no menu, clicks nothing, writes only the bounded local snapshot, and never uploads that file itself. Preserve an already-pending send-command.txt file. Do not attach settings, accounts, or full logs to a report.

This repairs the reproduced evidence omission. It does not establish the root cause or recovery of the remaining 27-entry incident, and it does not loosen parsing, quantity, price, ownership or spending safeguards.
