# Required private diagnostic history

The mandatory client stream is independent of Swarm sharing, dashboard pairing,
and public automatic bug filing. Every installation uploads typed, sanitized
diagnostic evidence to the existing GoNuts VPS. It does not enable trading or
participation in Swarm. The in-game About and report pages disclose collection;
the report page shows upload availability.

## What is captured

- Closed action and decision records across the mod's instrumented subsystems,
  including order plans, quantities, prices, refusal reasons and terminal causes.
- Controller context and allowlisted numeric settings observed every five seconds.
- Fault stages, closed exception categories, verified GoNuts stack frames, and
  process start/stop events. Repeated gate observations are sampled with omission
  counts; this is bounded diagnostic evidence, not an exhaustive raw log.

No chat, names, locations, commands, credentials, raw exception messages, raw
Minecraft logs or raw settings files are uploaded. Arbitrary strings fail closed.

## Durability and coverage

The client uses a separate private SQLite outbox at
`config/doughbay/diagnostics/outbox.db`, never the trading database. Its random
diagnostic capability is stored only there and sent only to the fixed HTTPS
diagnostics/report endpoints in `X-GoNuts-Diagnostics`; the receiver stores its
hash. It is never included in a report body, URL, public issue or log.

Each process starts a new random session. Unacknowledged events retain their old
session and sequence after restart. ACKs cannot delete another session or unsent
events. Capacity/expiry/permanent refusals declare explicit gaps. Network and
server limits use bounded retries on a background thread and never gate trading.
The in-memory ingress is bounded too; abnormal shutdown can lose its final queued
events, so complete coverage is not promised.

The local spool retains at most 32,000 pending events for up to 24 hours. A frame
contains at most 32 events and 16 gap ranges, within 64 KiB. The server retains up
to 24 hours, subject to its 100,000-event/64 MiB installation and 2 GiB global
limits. Low-priority observations are refused before critical evidence at the
installation limit. Expired or unrecorded history is never reconstructed.

## Reports and proactive investigation

Manual reports, added evidence and opted-in automatic reports attach the private
diagnostic capability independently of the older Swarm linkage. A best-effort
flush precedes filing. The receiver can select history across process sessions;
the worker reads that history and the report's numeric settings. A report retains
only its bounded selected excerpt under existing report retention, not the whole
24-hour timeline forever. GitHub-only fallback filing cannot attach this private
header. Missing linkage or gaps must be stated rather than treated as correctness.

The VPS groups closed fault/timeout/persistence signatures by installations,
sessions and build. Normal scans, cooldowns and ordinary price gate refusals are
not failures. Repeats on three occasions on one installation or on two
installations flag an investigation lead, always labelled as unproven. The
existing private admin API and worker can inspect aggregate counts without
exposing installation identities. No new worker or monitoring loop is required.

## Verification

Unit regressions cover restart retention, bounded queues, explicit gaps, invalid
and future ACKs, settings changes, expired server sessions, build collisions,
strict sanitization and fixed-destination report headers. The GUI timeout regression
fails before the closed reason-token repair and passes afterward.

An explicitly enabled live smoke test uses the actual client outbox and request
builder against the public receiver: restart, old-session upload, zero refusals,
replay deduplication, new-session upload, status and erase. It never files a public
issue or uses an account. Run only with `GONUTS_DIAGNOSTICS_LIVE_SMOKE=1`; the normal
suite skips network smoke tests.
