# Automatic critical reports: proposed contract and coordinated rollout

Status: client implementation is local only, not published or installed. On 2026-10-15 UTC the mod agent verified the public capabilities endpoint advertising configured, unauthenticated schema 3 with both failure kinds and durable grouping. The VPS agent reports deployment and verification of the receiver; the mod agent has independently checked capabilities only, not its full server verification list.

## Scope

Automatic reporting is per-install, opt-in and off by default, including headless installs. Consent must explain that sanitized diagnostics are submitted in the background and appear on public GitHub issues. Manual bug reports keep their existing behavior.

Initial automatic triggers are DURABLE_WRITE_REJECTED and PERSISTENCE_WRITER_FAILED. Require previously healthy persistence during the current connection, a requested trading session, and an unresolved failure for at least 120 seconds. Recovery, manual stops, intentional Staff Watch/restart handling and disconnection suppress a pending incident. Slow sales, startup intelligence collection, market scans and normal order waits are not automatic-report triggers.

## Schema 3 proposal

Use the existing public POST /v1/bugs endpoint without Authorization. Retain all schema 2 fields, text restrictions, sanitized diagnosticLog rules and the 32768-byte body cap. Set schemaVersion to 3 and add exactly one object:

```json
"automatic": {
  "kind": "DURABLE_WRITE_REJECTED",
  "signature": "IMMUTABLE_POSITION_IDENTITY_CONFLICT",
  "fingerprint": "e70c285535b7bd9a9f74602f43cacb5eb0d247b2e07d82eddfc5bbd77f6f728a"
}
```

Valid signatures for DURABLE_WRITE_REJECTED: IMMUTABLE_POSITION_IDENTITY_CONFLICT, RETAIL_CHILD_ID_COLLISION, CHECKPOINT_REJECTED. The only valid signature for PERSISTENCE_WRITER_FAILED is PERSISTENCE_GENERIC_FAILURE. Reject unknown fields, enum values and mismatched pairs. Never accept a raw exception message as a signature.

Compute fingerprint as lowercase SHA-256 of these UTF-8 lines, with LF separators and no trailing LF:

```text
gonuts-auto-v1
<build>
<minecraftVersion>
<kind>
<signature>
```

The example fingerprint above uses build 0.1.0 and minecraftVersion 26.2. The receiver must recompute and validate the fingerprint rather than trusting the supplied value. Generic signatures are grouping hints, not proof of a shared root cause.

## Retry deduplication versus incident grouping

Idempotency-Key is a random UUID per report reservation, persisted with the exact sanitized request body before submission. Reuse both on every retry and after restart. Do not use the shared fingerprint as the idempotency key: different installations can send different diagnostic bodies for the same incident.

Retain the receiver's global replay semantics: same key and body returns the existing receipt; same key with different body is 409. Group schema 3 reports by validated fingerprint into one incident issue while that issue is open. Concurrent submissions and service restarts must not create duplicate issues. Return the existing issue link for grouped reports, with the existing response shape. Maintain durable report counts and bounded sanitized evidence; do not post a comment for every repeat. Anonymous reports cannot establish unique-user counts.

Keep manual schemas 1 and 2 unchanged. Preserve abuse limits, privacy filtering and interrupted-request reconciliation. The worker must independently triage reports; a client-supplied automatic failure kind must never bypass confidence, review, testing or release gates.

## Capabilities

Only after receiver acceptance tests pass, extend GET /v1/bugs/capabilities with schemaVersions including 3 and:

```json
"automaticReports": {
  "supported": true,
  "grouped": true,
  "schemaVersion": 3,
  "kinds": ["DURABLE_WRITE_REJECTED", "PERSISTENCE_WRITER_FAILED"]
}
```

Keep configured true only when filing is actually available, requiresAuthentication false, maxBodyBytes at least 32768, and existing schema 2 diagnosticLog capabilities. The mod must refuse automatic submissions when these capabilities are missing, unsupported or unavailable. Do not downgrade automatic incidents into manual reports.

## Local client implementation

The local code now contains detector, metadata journal, payload builder, capability gate, install-local consent, runtime wiring, sanitized evidence capture, an asynchronous sender and a durable outbox holding the exact sanitized payload plus retry key. Network and report-file operations run on a single daemon, not the Minecraft tick thread. A process lock prevents two clients using the same game directory from delivering simultaneously. Stale tick observations cannot authorize uploads.

The setting is Settings → Support → Send critical bug reports automatically, off by default. Headless users can explicitly set `"reports.auto_critical": 1` in their own game directory's doughbay-local.json. Consent is never inherited through shared tuning.json or a copied settings code. No consent has been enabled on Cody's clients.

Existing player stop ownership, server-restart holds, Staff Watch transfer holds, evasion and an open control panel suppress pending detection. After a request has already been attempted, an ambiguous receipt may be reconciled using the same frozen payload even if the fault recovered; opt-out still prevents retries. The excerpt is a bounded filtered sample of the current log, not the full log file. Cached excerpts are validated again before submission after restart. Raw messages and usernames are not copied into the request.

Local automated tests cover grace/recovery suppression, opt-out, unavailable capabilities, retry persistence, frozen request identity, five-attempt limits, corruption, forged/private cached evidence, duplicate/daily suppression and consent isolation. The full build and test suite must pass before release. A controlled GUI/headless end-to-end submission on the completed client is still outstanding; do not describe automatic reporting as live on mod installs yet.

Cap new reservations at three per install per rolling 24 hours. Suppress the same fingerprint for 24 hours after success or exhausted retries. Bound retries and honor Retry-After; 409 must not start a new-key loop. Mark success only on a validated 200/201 receipt with a trusted issue URL. Fail closed on corrupt local state. Recheck consent and failure eligibility before the first upload; an already attempted ambiguous request must retain its frozen body for safe reconciliation.

## Coordinated rollout: no mod-only release

1. Agree this contract between mod and VPS implementations; finish both locally without publishing the mod.
2. Verify the receiver in staging: old manual schemas, schema 3 validation, fingerprint golden vector, concurrent grouping, restart persistence, privacy and abuse limits. Test the client outbox/retries and recovery suppression against that receiver.
3. Deploy the backward-compatible VPS receiver first and verify its live capabilities and receipts. Close clearly marked test issues. If it fails, stop the rollout and keep the existing mod release.
4. With the user's go-ahead to publish, release the tested mod, source archive, matching checksums, release notes and Pages changelog together through the existing release workflow. Until that authorization, keep changes local.
5. Test an opted-in install with no token, including a controlled qualifying failure, recovery before the grace period, replay, and grouping from a second install. Confirm manual reports remain usable and headless clients do not require the GUI.

This is one coordinated rollout, with the compatible receiver deployed before the client is released. It does not require simultaneous deployment. If the receiver becomes unavailable afterward, the client's capability gate and bounded retries prevent incompatible automatic uploads. Disabling automaticReports.supported pauses automatic submission without removing manual reporting.
