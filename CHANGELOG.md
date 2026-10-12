# GoNuts changelog

What each build changed, newest first. Times are UTC. The mod shows these in
the update notice, after an update installs, and under What's new on the
About tab.

## 2026-10-12 16:37 UTC
- Fixed: the bot freezing with "Multiple distinct exact sale notices are ambiguous" until someone pressed Resume. It happened when two identical stacks sold to different buyers moments apart, which is normal on a busy book, and it stopped one trader three times in thirteen hours. The second sale is now given to another listing of the same item, or the auction page is recounted if there is none, and the session keeps running.

## 2026-10-12 15:31 UTC
- Removed: the row of slot squares at the top of the web panel. The Auction slots tile already gives the count, and the row took a lot of room to say the same thing.

## 2026-10-12 15:25 UTC
- Changed: the web panel is now set in a pixel font, so it reads like the screen in game. The font is Monocraft by Idrees Hassan, bundled with the mod under the SIL Open Font License; nothing is fetched from the internet.

## 2026-10-12 15:13 UTC
- Changed: the web panel now looks like the GoNuts screen in game. Same dark panels, edges, gold titles and button states, with the GoNuts logo on the page and on the PIN screen.
- New on the web panel: the HUD across the top (state, auction slots, bank, trades, account, what the bot is doing now, and the capacity squares), and the pages down the side as buttons in place of a dropdown. On a phone the pages become a row you swipe.
- The web panel is under Autopilot on the GoNuts screen: click the mode button through to Web panel.

## 2026-10-12 14:50 UTC
- Fixed: the session pausing over and over on "Recovered purchase is next; listing it". When several listings of one item left your auction page and one stack of it was in your inventory, every one of them was queued as a purchase waiting to be listed. Only one stack was really there, so the bot paused on each of the others in turn. One stack in the inventory now stands for one listing.
- Fixed: sales still being booked as "returned to you, not sold". Many sales are announced only as "You earned $89.8K from auction", with no item named. Those amounts are now added up between slot checks and used as proof that missing listings sold, so the decision no longer rests on your balance alone. In one 75 minute log, 34 listings were booked as returns while these messages reported more than enough earned to cover them at each check.

## 2026-10-12 12:55 UTC
- Fixed: the bot saying your auction slots are full when they are not. A listing that leaves your auction page is kept on the books if the same stack is sitting in your inventory, on the idea that it was pulled back. One stack in the inventory was excusing every missing listing of that item, so nine sold stacks of emeralds stayed counted as listed because a tenth had just been collected from an order: the panel read 45 of 45 with 35 on the page, and nothing more was listed into the empty slots. One stack now stands for one listing; the rest are settled as sold or returned at the next slot check.

## 2026-10-12 12:48 UTC
- Fixed: sales booked as "returned to you, not sold". When a listing leaves your auction page with no sale message, the bot checks your balance to decide whether it sold. That check did not count money locked into the bot's own buy orders, so after a large order the balance looked short and real sales were recorded as returns: 13 in half an hour in one log, right after a 14.8M order. Your money was never affected, but the profit shown was too low and the bot believed it still held stock it had sold. Money put into an order and money refunded by a cancelled one are now both counted.

## 2026-10-12 11:11 UTC
- New: a web panel. The same pages and buttons as the Discord panel (money, listings, items, orders, rivals, settings, Start and Stop) in a browser, with no Discord and no bot token. Open the GoNuts screen, go to Automation and click through to Web panel, and set a PIN of 4 to 8 digits: that turns it on. The card then shows the address to open.
- New: use it from your phone. Turn on "Phones on your Wi-Fi" on the same card and the panel also answers devices on your home network, at the second address the card shows. It never answers anything from outside your network.
- Safety: nothing is shown or done without the PIN. Five wrong PINs lock the panel for a minute, and each lock after that is twice as long, up to 15 minutes. Only a hash of the PIN is saved. The PIN and the phone switch can only be changed in game, never from the web page itself, and neither travels in a settings code.

## 2026-10-11 19:42 UTC
- Fixed: orders that failed to place on a busy server. When Your Orders was slow the bot clicked it a second time after 8 seconds, and the second page then arrived in the middle of the order form and wiped it. Of 32 placements, the 8 that asked once all worked and 10 of the 24 that asked twice failed, about 40 seconds each. The second click now waits for as long as the page has lately been taking, and an order whose form is replaced by a late page starts again from that page at once.
- Changed: after placing an order the bot tries to place the next one from the same page, without waiting for Your Orders again. If the server does not answer on that page within 3 seconds it goes back to asking for a new one.
- Removed: the wait added in the last build for a page the server was thought to send back after an order. It does not send one.

## 2026-10-11 19:27 UTC
- Changed: a step that ends on Your Orders (reading or cancelling) leaves the page open for the next step to start from, which saves one slow page load each time. There is a switch for it under Orders in Settings (Stay in the order house between steps, on by default).
- Fixed: a sell command the server ignores is retried at once after closing the player inventory, the same cure already used for the auction and order commands. Before, the bot waited out five tries with longer and longer holds, which cost four and a half minutes each time the server thought a menu was still open.
- Collecting is unchanged: one order at a time, with the stock listed before the next, so nothing piles up in the inventory.

## 2026-10-11 18:34 UTC
- Changed: the bot now collects from an order only when a whole stack is waiting or the order is completely filled. It used to make a trip for a quarter of a stack, or for whatever was there when an order went quiet, which is where listings of 3, 14 or 53 of something came from. A part stack is only taken when the order itself is being pulled.
- Changed: a small delivery no longer sends the bot to Your Orders at once. It goes when an order completes or a stack has built up, which saves a slow page load each time on a busy server.
- New: the switch for this is in Settings under Orders, "Collect whole stacks only". It is on by default.

## 2026-10-11 17:37 UTC
- New: when the order house stops answering, the bot no longer stands waiting at it. After three failed tries it works the auction side (listing what it holds, repricing, watching for deals) and tries Your Orders again every few minutes until it comes back.

## 2026-10-11 17:28 UTC
- Fixed: the wait for the Your Orders page now follows how slow the page really is. The build before this one went back to a 20 second wait after every page, which on a busy server (the page was taking about a minute) made it give up too early over and over. It now waits a bit over twice as long as the last page took, up to 90 seconds, and eases back down as the server speeds up.

## 2026-10-11 17:14 UTC
- Fixed: three-minute stalls when the Your Orders page does not open. The bot waited a fixed 90 seconds before asking again and 90 more before giving up, with listing stopped the whole time. It now waits 20 seconds, and only waits longer (45, then 90) if the page keeps failing. On a busy server this was costing several minutes of every hour.

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
