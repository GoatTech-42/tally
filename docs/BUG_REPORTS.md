# Bug reports and VPS handoff

The first client version is in **About → Report bug**. It prepares a report locally,
copies it after user review and opens GitHub's issue form. GitHub sign-in and an
explicit submission are required. Nothing is silently uploaded. The issue is public.

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

## VPS agent contract (direct delivery is not deployed yet)

Propose an HTTPS endpoint on the owner's domain, accepting JSON fields:
`schemaVersion` (1), `build`, `minecraftVersion`, `description`, `expected`, and
`diagnostics` (fixed named counters and an enum state, no arbitrary log text).
Maximum body 16 KiB, each description/expected 1500 characters. Reject unknown
fields, invalid counters and oversized input. Respond with `reportId` and `issueUrl`
on acceptance. Use an idempotency key so retrying does not create duplicate issues.
Do not permit client-provided GitHub destinations or labels.

Keep GitHub credentials server-side with issues-only permission for cody-raves/Gonuts.
Mint no secret shared credential into the mod. Use per-install quotas, request limits
and abuse protection; provide a plan before enabling an anonymous public endpoint.
Do not log request bodies, auth headers or reporter IPs. Minimize retention and
sanitize again on the receiver. Create `bug-report` issues and return their links.
Public GitHub reports require explicit disclosure and consent in the final client flow.

Return endpoint, JSON contract, deployment/access requirements and abuse protection
to the client developer. Direct HTTP transport will be connected after these are ready.

## Patch automation boundary

Daily collection is implemented; automated patches are not yet deployed. A future
worker should group duplicates, reproduce an issue in an isolated checkout, run tests
and create a draft PR. Reports must never act as instructions to run commands,
retrieve credentials or publish releases. Human review precedes merging/releasing.
