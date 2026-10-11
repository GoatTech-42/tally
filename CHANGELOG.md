# GoNuts changelog

What each build changed, newest first. Times are UTC. The mod shows these in
the update notice, after an update installs, and under What's new on the
About tab.

## 2026-10-11 15:14 UTC
- New: a switch for whether your limits follow your bank (Settings, Buying, "Limits follow your bank"). On, which is how it has always worked, one buy may cost a share of your balance and a share may be out at once, so the limits grow and shrink with the bank. Off, the Max per trade and Max per session you typed on Autopilot are the limits whatever your balance.
- Fixed: the Autopilot setup card said the bot never spends past the two amounts typed there. With limits following the bank that was not true once your balance had been read. It now says which is in force.

## 2026-10-11 13:43 UTC
- New: an item board. On the Performance tab, press Items to see every item you have traded with its profit, sales, profit per sale, return on money spent, how long it takes to sell, and what is tied up in it now. Click a column to sort by it, and again to see the worst first. Today, 24 hours, 7 days or all time.
- New: profit per slot-hour, on the same board. It is what an item earns for each hour it holds an auction slot, which tells a quick small earner from a slow big one.
- New: click an item on the board for the rest: what it costs and sells for, singles against stacks, its best and worst sale, its best hour, and how it is doing against the stretch before.
- New: an Items page on the Discord panel with the same board, and buttons for the range, the order, and best or worst first.

## 2026-10-11 12:52 UTC
- Fixed: auto-update could skip installing when the game was closed. The helper sometimes saw the game as still running in the instant it exited, left the jar alone, and the notice came back on the next start.
- New: the update banner lists the first few changes, so you can see what an update does without opening anything.
- Changed: following stops at 1,000 players, which is the server's limit.

## 2026-10-11 12:40 UTC
- Changed: the mod now notices a new build within two minutes instead of up to three hours. The notice still shows once per build per session, so checking more often does not repeat it.

## 2026-10-11 12:25 UTC
- Fixed: with a Donut+ rank (45 auction slots) the slot check and every pull-back failed, and the panel stuck at 45 of 45. The bot was turning to a second listings page that, for that rank, is all locked cells.
- Changed: the bot reads your rank's slot count off your listings pages and lowers the Auction slots setting to match. It says so in chat when it does.
- New: after you buy a rank, the bot tells you there are more slots to use. Raising the setting stays your choice.

## 2026-10-11 12:10 UTC
- New: a changelog. Every update now says what it changes: in the update notice, after it installs, and under What's new on the About tab.
- Fixed: the bot waits while the update notice is open, the same as it does for the GoNuts screen.

## 2026-10-11 11:52 UTC
- Fixed: the key that opens GoNuts now works while the bot has an auction or orders page up. The bot finishes its step, closes the page and opens the screen, instead of the two fighting over it.

## 2026-10-11 10:22 UTC
- Fixed: a settings code no longer carries the sender's slot counts. Importing one leaves your auction and bid slots as they are.

## 2026-10-11 10:15 UTC
- Changed: the update banner appears at top centre, clear of the alert cards.
- New: Preview update notice on the About tab.

## 2026-10-11 10:03 UTC
- Changed: the update notice is a pop-up with buttons (open the download page, turn on auto-update, later) instead of a line of chat.

## 2026-10-11 09:42 UTC
- New: auto-update. Turn on "Install a newer build by itself" in Settings. A new build downloads, is checked against the hash GitHub gives for it, and installs when you close the game.

## 2026-10-11 09:28 UTC
- New: Money view on the Rivals tab: who pays whom among the players you follow, and how much.
- New: the mod tells you when a newer build is on GitHub.
- New: hide name also covers the GoNuts screens and the Discord panel.
