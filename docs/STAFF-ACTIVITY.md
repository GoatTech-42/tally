# Staff Watch activity history

The in-game Staff Watch tab, the web panel's Staff Watch page, and the Discord bot's Staff Watch page display passive detector observations from the hive. The web page includes the complete retained summaries and recent event list; the Discord image and in-game panel show bounded recent sections.

Data includes current player-list detections, spectator-mode observations, nearby/local evidence, first/last observation intervals, total detected time, recent events, and usual observed hour bands. Names remain attributed to the observing client. Mapped areas are estimates from the existing map, not authoritative shard identifiers.

## Observation rules

- No new game commands, autocomplete requests or additional server polling are introduced.
- The game thread publishes immutable detector facts. A daemon samples and persists them every five seconds.
- The separate `config/doughbay/staff-watch-activity.db` sidecar can be shared by clients already sharing that directory. Trading tables and caps are not modified.
- History is retained for 30 days. Observation gaps over 15 seconds, disconnects, module disablement, restarts and local-world boundaries do not accumulate detected time.
- Overlapping intervals across observers are merged before calculating total detection and monitoring duration. Counts are observation intervals, not confirmed player login sessions.
- A sighting disappearing does not establish that a moderator logged off. Hidden from TAB does not establish vanish. Stale current data is hidden while historical data remains explicitly historical.
- Usual hours require at least three covered dates and one hour of monitoring in an hour band. Percentages describe detections during observed coverage, not future availability. Sparse history stays marked insufficient.
- Times use the panel host's named local timezone. They do not infer a moderator's home timezone or account ownership.

## Reliability and access

Writes use bounded SQLite busy waits and transactional updates on the history daemon. Current observations expire after 15 seconds. History errors cannot stop trading or its persistence worker. The web route remains behind the existing PIN/session authentication; Discord retains its existing access controls and polling cadence.

The worker publishes cached immutable snapshots for UI readers; rendering performs no history-database IO. The sidecar has no API tokens, coordinates, chat logs or account-authentication material. It intentionally stores staff handles and the observing client's name for operator attribution.

## Validation and release status

Tests cover two observers, restarts, gaps, disabled detectors, stale observations, event idempotency, case-insensitive overlap merging, insufficient coverage, clock formatting, web authentication, JSON output, and headless Discord rendering. Preview assets under trader-fabric/build/staff-watch-preview use clearly marked demo data.

The actual in-game layout was verified with an isolated offline Minecraft client using marked sample data, including scrolling through current observations and history. The screenshot fixture is excluded from normal production builds. After installing, confirm that the sidecar records fresh observations from both clients and that all three panels expose the Staff Watch page. Existing escape and reconnect behavior is unchanged.
