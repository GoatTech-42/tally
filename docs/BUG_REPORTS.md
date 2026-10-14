# Bug reports and VPS handoff

The client now sends reviewed reports directly to **https://gonuts.at/v1/bugs**
using the configured gonuts.at install token. It never reuses an official DonutSMP
key or a token configured for another server. Legacy numeric-address/custom-proxy
setups should use the GitHub fallback or configure the canonical gonuts.at origin
with their existing valid GoNuts install token. Transport always uses HTTPS,
never follows redirects, bounds reply sizes and times out failed requests.

In **About → Report bug**, enter a description and expected behavior, choose
**Prepare**, scroll through the filtered preview, confirm **I reviewed this public
report**, then **Send reviewed**. Authentication-free GitHub submission remains a
fallback (GitHub sign-in required). No report is sent in the background.

Success and duplicate replies offer **Open issue**. Retrying an unchanged report
uses the same idempotency key. A 429 holds the send button for Retry-After; failed
GitHub/receiver availability holds it briefly. There is no automatic retry loop.
Report preparation failure disables sending and never falls back to raw logs.
A 503 means reporting is temporarily unavailable, not that the report was filed.
The VPS operator must install its GitHub Issues token before filing can succeed.

**Feature request** opens a separate GitHub form, without diagnostics; requests
are considered, not guaranteed. It is not a bug-report receiver submission.

Raw logs are never attached. A bounded 256 KiB tail of the current log is reduced to
fixed event counters and a recognized automation state. This is a sample, not a
time window. No event messages, player names, chat, paths or exception messages
are copied. Config files, databases, environment variables and process arguments
are not read. User-written fields are filtered for credentials, addresses, email,
UUIDs, paths and opaque long identifiers. User text still requires review: arbitrary
names and personal facts cannot reliably be recognized by a pattern filter.
If collection fails, report submission is disabled; there is no raw-log fallback.

The GitHub daily digest runs at 16:00 UTC (09:00 PDT / 08:00 PST), linking new
`bug-report` issues from the preceding 24 hours. No report text is executed or sent
to a patch agent. GitHub schedules are best-effort; manually rerunning the workflow
updates that date's existing digest. Empty days produce only a workflow summary.

## VPS receiver contract

The verified receiver schema is in [BUG_REPORTS_RECEIVER_CONTRACT.md](BUG_REPORTS_RECEIVER_CONTRACT.md).
Exactly six top-level fields are sent; diagnostics contain the fixed enum state
and five integer counters. UNKNOWN is uppercase. The mod build timestamp is
appended to its version when it fits the receiver's 40-character build limit,
so reports distinguish releases sharing the same mod version and JAR filename.

This schema rejects attachments and raw-log-shaped description text. Sanitized
log attachments require a receiver schema extension first; they are not sent by
this client. The receiver's GitHub token stays on the VPS and is never in the JAR.

## Patch automation boundary

Daily collection is implemented; automated patches are not yet deployed. A future
worker should group duplicates, reproduce an issue in an isolated checkout, run tests
and create a draft PR. Reports must never act as instructions to run commands,
retrieve credentials or publish releases. Human review precedes merging/releasing.
