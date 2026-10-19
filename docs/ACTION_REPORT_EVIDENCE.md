# Action evidence in bug reports

The mod records structured action evidence while operations run, before a report is opened. Capture is shared by the in-game reporter, web reporter and existing opted-in automatic critical reports. Recording diagnostics does not grant permission to trade or submit reports.

## What is recorded

| Area | Evidence |
|---|---|
| Controller and persistence | State changes, durable-load/refusal status, accepted/durable generations and pending-checkpoint ages |
| Flips and order desk | Queued operation, refusal category, item, standing top bid, maximum bid and configured increment when the ceiling blocks a quote |
| Auction execution | Driver phase/phase age, watch age, operation outcome, quantity, price and ceiling; unclear prices, above-ceiling rows and ghost rows |
| Feed and Swarm | API/keyless selection, snapshot completion, market count, retries, HTTP outcome class and captured faults |
| Recovery | Resume refusal categories and existing stock, inventory, exposure and persistence context |
| Hive | Create/join/combine requests, offline-required refusals, queued-combine result and verified exception frames |
| Dashboard linking | Socket open/close/retry, known close codes, authorization refusal and captured faults |
| Evasion, Staff Watch and restart | Escape route requests, transfer/reconnect phases and listener failures, without staff names or destinations |
| Item tracking | Number selected in a track/untrack request and resulting effective tracked count |

`REQUEST` is intent, `PHASE` is progress, and `SUCCEEDED`, `ABORTED` and `EMERGENCY_STOPPED` are distinct driver terminal facts. A connection opening does not prove authentication or healthy trading. A queued Hive combination is not a completed import. Refusal categories identify gates, not proven root causes.

Action rows include a fixed subsystem, event, compiled code, fixed reason category, age, repeated-event duration/count, and applicable numeric values. Unknown values are `-1`. The market is a base Minecraft item identifier, never a seller, account, listing ID or component payload. For a rounded auction price rejected against a ceiling, `price` is the tested upper bound. For `TOP_BID_INCREMENT`, `price` is the configured per-item increment. For a quote ceiling refusal it is the standing per-item bid. Counts in a MARKET snapshot event mean valued markets; counts in item-track events mean selected/resulting items.

## Relevant evidence within a fixed size

Report text selects one fixed topic: Flips/orders, auction, recovery, Hive, dashboard, evasion or community. Unknown descriptions retain general capture. This is a priority policy, not a user-supplied log query. The original description is never used as a file path, regular expression or command.

Relevant structured log events are selected **before** the final attachment budget is applied. A Flip report prioritizes FLIPS/DESK/DRIVER evidence; a Hive report prioritizes HIVE evidence. The shared controller/inventory/persistence snapshot stays present, and some timeline space remains for other subsystems. The local historical scan remains bounded to the last 256 KiB of the current log; events outside that window or from before this instrumentation existed cannot be reconstructed.

The in-memory ring holds 48 recent entries and separately retains the latest observation and latest problem per subsystem. Repeated identical events coalesce into a count and duration. Structured disk logging emits at most one line per second per subsystem; it does not log every tick. Events suppressed by that disk rate limit can still be retained in memory. Historical action ages describe when they were logged; use their log timestamps for chronology.

The action section is at most 3 KiB, combined current context at most 7 KiB, and the complete diagnostic attachment remains at most 8 KiB/200 lines/500 characters per line. Older or lower-priority history is omitted to fit. Missing observations are unknown, not proof of health. These are bounded diagnostics, not an exhaustive replay.

## Delivery and privacy

Manual preview and frozen automatic bodies use the same evidence. Automatic outbox validation accepts only known context keys and values, compiled GoNuts frames, and strictly structured action/history lines. A disk reload preserves the frozen report; it does not recapture a different incident. The regression using real controller context failed against the old log-only outbox validator.

Receiver validation, stored/rendered issue text and the patch worker prompt must all preserve the reviewed fields. Isolated pipeline fixtures verify this without creating public test issues. Names, account/home identities, tokens, raw exception messages, command arguments, full settings and arbitrary logs are excluded. Existing submission consent and all trade safety gates remain authoritative.
