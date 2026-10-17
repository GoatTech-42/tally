# Hosted dashboard client: protocol 1

Public release integration: Discord sign-in and per-installation pairing, owner-authorized expiring controls, opt-in Swarm guidance, and passive per-client mascot activity. Legacy clients without optional fields remain supported.

## Pairing

Open https://gonuts.at/dashboard/ and sign in with Discord. Choose Link client.
Enter the fresh code in GoNuts About → Dashboard, or Dashboard in the local web panel for a headless client.
The local panel links its hosting client only. Pair each installation separately.
Local unlink stops the socket and removes the local credential; remove the offline installation on the website too.

## Client integration choices for the VPS agent

- Hive grouping uses a random `hive_` plus 32 lowercase hex characters, saved in shared `dashboard-hive-id.txt`. A file lock prevents simultaneous clients generating different groups. No path-derived identifier leaves the machine. The value is only a display group, not authorization.
- Credentials are in the game directory's `gonuts-dashboard/installation.json`, outside shared config. Files and the private directory use owner-only ACLs/POSIX permissions. No credential, code or response body is logged. Fixed HTTPS/WSS endpoints only; redirects are disabled and redeem response URLs must match the contract.
- Advertised capabilities: `pause`, `resume`, `snapshot`, `panel`, `swarm_enable`, `swarm_disable`. Swarm controls never start trading. Optional telemetry includes `mascotFace` and `swarm` consent/eligibility plus transport `sentAt`/`receivedAt` timestamps; timestamps do not prove useful data or a trade. `send_crash_report` is not advertised: the existing local alert describes a report ready to prepare, not necessarily a frozen prepared report. Unsupported commands are rejected.
- Session state, slots, bank and session trade count come from the same controller/sidebar figures as the HUD. Account labels obey the existing privacy toggle. Detail and lastAction are regenerated state descriptions; raw strings could contain player names, homes, paths or chat and are never forwarded.
- Rolling hour/day booked profit, completed sales and recorded purchases are read strictly read-only from REAL positions for the exact account, including negative profit. They are omitted if the ledger is unavailable.
- Listing/reprice counts come from confirmed successful LIST terminal events, deduplicated by sequence, with repricing kept separate. Existing positions do not preserve trustworthy prior action counts, so those fields are omitted until this client has observed a full hour/day. Observation resets on process restart or account change. The website should display unavailable for absent fields. No completed sales are substituted for listings/reprices and no partial interval is projected into an hourly number.
- Rate-limited telemetry, serialized outbound frames, 20-second heartbeat, 80-second receive timeout, jittered reconnect. Duplicate command ids reuse their prior result. Game-thread dispatch checks connection epoch and expiry again. Disconnect/revocation cancels pending work and never pauses trading.

## Live acceptance test together (before publication)

1. Install the unpublished test JAR on one selected client, retaining its current JAR as backup. Do not publish or update everyone yet.
2. Generate a new code and redeem it using About → Dashboard or the local web panel.
3. Verify the website identifies that installation and shows fresh telemetry. Check its booked hour/day profits against the same account's ledger.
4. Snapshot, pause and resume from the hosted page; verify ack/result outcomes and actual local controller state. Limits and unresolved recovery remain enforced.
5. Repeat an id in the VPS test harness: controller action happens once. Verify an expired command is rejected.
6. Interrupt the dashboard connection; trading continues and the client reconnects with backoff.
7. Unlink on the website; client forgets the credential and stays not linked. Pairing again requires a new code.
8. Pair the second installation separately: credentials remain separate and shared random hive id groups them under the same signed-in dashboard user.

Do not publish a release before this live pairing test is agreed complete with the user.

## One local panel for a hive

The Dashboard tab lists every hive account and accepts a separate website code for each. Actions target the selected account through an owner-only local shared inbox; installation credentials never enter that inbox or the shared ledger. Public link status is refreshed every two seconds and expires after fifteen. Requests expire after thirty seconds, are consumed before execution, and are bound to the target process instance so an offline client cannot replay them on startup. Both clients need the unpublished dashboard build for this test.

## Full panel relay

Advertises `panel` in redeem and hello. Relays page, state, data, change, act, png, icon and fixed bundled assets through the same LocalPanel.Backend instance. Mutations run on the client thread after checking socket ownership and the fifteen-second deadline. Eight outstanding requests maximum, dedupe per socket/request id, bounded text/binary/frame sizes, bounded form input, fixed asset allowlist, safe error replies and no request/body logging. Local PIN authentication stays unchanged; hosted ownership and CSRF belong to the VPS. No HTTP listener or client UI is added.
