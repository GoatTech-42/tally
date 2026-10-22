# Staff Watch signal audit — 2026-10-22

The owner reports a sustained loss of alerts across three clients after earlier
frequent alerts, with possible timing around Hive membership or Swarm staff
sharing. Treat this as an unresolved incident; enabled settings and active
packet reception alone do not establish correct detection.

## Observed evidence

- All three current clients have Staff Watch, Staff List, Anti Vanish and region
  alerts enabled. Current detector counters are advancing.
- The current shared database has seven dedicated watcher-alert rows dated
  October 14–15 UTC. This is **not** a complete detection history: the previous
  investigation found successful region transfers on October 17 that bypassed
  the notification path. PR152 and PR160 repaired those paths; do not redo them.
- The shared activity database records game-mode observations on October 17–18,
  then ordinary presence observations only from October 19. Shared rows are not
  independent observations across three bots and do not count unique people.
- Retained October 19–21 detector logs show all observed game-mode update packets
  also carrying ADD_PLAYER, zero accepted mode changes, and zero recognized
  staff spectator entries in the available staff-specific counters. No matching
  Staff List/Anti Vanish listener-error markers were found in those archives.
- Swarm sharing was added on October 17. Its reporting calls did not introduce
  the ADD_PLAYER exclusion: that exclusion existed in the October 13 detector.
  Global player-list state does not establish that staff are on the local shard
  or watching this player. Preserve that distinction.

## Reproduced defects and bounded repair

The live shared activity database contains two fresh observers; bot three has
one fresh observer in `joined-profiles/<account>/staff-watch-activity.db`.
`StaffWatchReport.start` used the private settings directory for its database,
although added Hive members deliberately keep their settings separate. The
joined-profile location regression fails against that behavior. History now
uses the validated shared Hive directory, while solo storage is unchanged.
Existing profile-local history is retained without an automatic historical
merge. This affects aggregation and does not disable the local detector.

`StaffInboundActionsTest` exercises the actual Anti Vanish packet handler using
Minecraft packet objects without connecting a game or sending notifications.

Before repair, three tests failed:

1. UPDATE_LISTED plus UPDATE_GAME_MODE queued visibility only, losing mode data.
2. Even a visible entry with both flags lost its mode data.
3. A game-mode update inside a bundle queued nothing.

The handler now examines both action flags and bundle children. It does not
cancel an entire bundle when one child is an owned completion reply. Regression
coverage also preserves the existing exclusion of initial full-list additions.
The separate Staff List detector already handled mode flags and bundles; these
Anti Vanish defects therefore do **not** establish loss of every alert route or
explain the entire live incident.

## Collecting the missing evidence

Anonymous Anti Vanish counters are appended to the existing minute health line:

- `avEnabled`: whether the Anti Vanish module is actually enabled at runtime.
- `avBundles`, `avModePackets`, `avInitialModePacketsIgnored`, `avModesQueued`:
  where mode updates reached the input path and whether they were queued.
- `avProbeAttempts`, `avProbeReplies`, `avProbeNames`: attempted sends and owned
  replies, including replies later discarded at a region boundary. An attempted
  send is not proof of delivery or a successful reply.
- `avProbeKnownStaff`, `avProbeHiddenCandidates`: cumulative entry counts from
  processed replies, before the existing one-to-three-candidate alert gate.
- `avAlertRequests`: calls requested by Anti Vanish; not proof that a response
  was accepted, completed, or delivered as a notification.

These are cumulative counters since module construction, not unique people or
CPU measurements. They contain no names, UUIDs, positions or packet payloads and
do not add probes, timers, collectors or server requests.

## Still unproven

We have not demonstrated that Swarm sharing or Hive membership caused the gap,
that the server changed its packets, or that a real local spectator event was
missed. The fixed staff roster and server visibility are additional limitations.
Do not turn global presence into local-watcher proof to manufacture alerts.
Use a normal post-update minute sample to locate the missing stage before
claiming the whole incident fixed. Keep the live build and artifact publication
status separate from tested source changes.
