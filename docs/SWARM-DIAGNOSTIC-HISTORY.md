# Swarm diagnostic history (v1 implementation, staged)

This feature is staged for client/backend compatibility testing. It is not active in the public PR245 client or in the running local profit trial.

Linked clients that have Swarm sharing enabled send a rolling diagnostic history through the existing authenticated dashboard connection. There is no separate diagnostics switch. Trading may be paused without disabling participation; disabling Swarm or unlinking stops new history uploads. Unlinked clients continue to file ordinary bug reports without history linkage.

The history is pseudonymous: the server derives a stable Swarm client ID per installation, with a separate random session ID on each process/session boundary. It does not contain usernames, chat, home names, paths, credentials, webhook URLs, Discord identities, raw settings files, pending request bodies or raw Minecraft logs. Existing dashboard account-label behavior is separate from this new history projection.

## Evidence and settings

Capture uses only verified `ctx` controller/fault fields and regenerated `act` action fields. Context is at most 16 lines / 4096 UTF-8 bytes. Known numeric report settings are captured with the event as a validated DB1 code (at most 12000 characters); text/list settings and secrets are excluded. The backend deduplicates these into settings versions, independently of hourly performance-analysis samples. These events must never inflate the hourly analysis denominators.

Settings/fault changes are checked at most once per five seconds; ordinary state/action changes are sampled no faster than once per 30 seconds, with an unchanged-state heartbeat after a minute. This leaves room below the server's rolling daily cap for settings/fault evidence. An observed settings change happened between observations, not necessarily exactly at the later observation. Worker analysis compares the versions around a failure and can test them, but chronology is not proof of causality. The report preview's `captured_at_ms` is the evidence-capture time, not necessarily the time the user experienced the incident.

## Wire and identity

Hello capability: `diagnostic_history`. Welcome: `diagnosticHistorySchema:1`, `swarmClientId:sc_<32 lowercase hex>`. Once the authenticated installation/account identity is known, the server sends `diagnostic_report_token` with `token`, `expiresAt` and `swarmClientId`. The opaque token is private, expires after 24 hours, is revoked on unlink, and must never appear in public issue bodies, page markup, logs or diagnostic events.

An independent frame on the same socket is `{type:diagnostic_history,version:1,sessionId:<lowercase UUID>,events:[{sequence,observedAt,context,settingsCode}]}`. Maximum serialized frame 24KiB, maximum eight events, increasing positive sequences. The existing telemetry frame remains unchanged in size and function. Server eligibility uses authenticated linkage and explicit `swarm.enabled`, not transient trading activity or a cold ranks cache.

The server acknowledges final events with `{type:diagnostic_history_ack,sessionId,throughSequence}`. Permanent refusal must be counted separately from storage; storage errors, unknown eligibility and rate limits must remain retryable. The client rejects ACKs for another session or beyond a sequence it actually sent. The in-memory outbox holds at most 256 events and drops expired/oldest entries; the server records sequence gaps. Unsent data may be lost on process shutdown. Uploaded history survives closing the game. This is bounded diagnostic evidence, not guaranteed complete logging.

## Report linkage and retention

When the receiver advertises `diagnosticHistory:{supported:true,version:1,header:X-GoNuts-Swarm-Report}`, a prepared report can send its private capability in that HTTPS header. The token is frozen to the reviewed evidence, never substituted from a different current account. JSON and idempotency keys stay unchanged. Manual reports, existing-issue evidence and opted-in automatic reports use the same optional path. Missing/expired/unavailable history never prevents ordinary reporting. A restarted process may not retain an unsent report's optional in-memory association.

The receiver validates the token against the installation/account; a plain Swarm ID is not authorization. The worker and allowlisted admin can retrieve only the associated private evidence. Arbitrary strings in a report or uploaded evidence are untrusted data, never instructions to the worker.

The rolling timeline and unreferenced diagnostic settings expire after 24 hours. A filed report may keep a bounded selected sanitized excerpt and relevant settings under the existing report retention policy; it must not freeze an entire 24-hour timeline indefinitely. Worker/public responses use only the minimal sanitized findings, without private timelines, IDs or tokens. Gaps, capture time versus incident time, and clock-skew corrections must be labelled.

## Validation boundary

No real public issue is created for tests. Verify strict input sanitization, malformed/forged/expired tokens, cross-client/account isolation, ACK replay/future ACKs, retry, bounded bytes/queue, settings changes, TTL and existing report compatibility using isolated fixtures and the backend's disposable-schema harness. The live profit trial is not restarted or upgraded for this feature.
