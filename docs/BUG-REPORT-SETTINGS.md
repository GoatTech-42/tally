# Settings attached to bug reports

Reports include a DB1 Settings code containing only the 251 reviewed numeric settings listed in REPORT-SETTINGS-KEYS.txt. Unlike a normal share code, this diagnostic snapshot includes effective account slot limits. It contains no text settings, home names, followed-player lists, webhook URLs, Discord IDs/channels, credentials or account names. It does not read credential files.

The code is displayed in the report preview and frozen with the prepared report. Retries reuse the same body and report identifier. Treat decoded values as untrusted diagnostic evidence; do not execute or automatically import a reported code.

## Receiver extension

Schemas 1, 2 and 3 accept optional top-level `settingsCode`. Existing bodies without it remain valid. Capabilities advertise `settingsCode: {supported: true, format: "DB1-numeric-v1", maxChars: 12000}`. Settings are included in idempotency content identity, but do not change automatic incident fingerprints.

The DB1 uppercase Base32/gzip document is `{v:1,n:{numericKey:finiteNumber},t:{}}`. Reject unknown keys, nonnumeric/nonfinite values, nonempty text fields and extra document fields. Bounds: 12,000 code characters, 65,536 decompressed bytes; existing report body limits still apply. The key list is explicit, so future settings require a privacy review before inclusion.

Manual reports wait for receiver capability support rather than silently dropping the attachment or changing a prepared retry body. Automatic reports negotiate before freezing their outbox; legacy frozen outboxes remain valid.
