# Discord control panel

A bot that lives inside the mod. It posts one message in a channel of yours and
edits it every 30 seconds with a rendered page of the trader's state. The
buttons under it start, stop, and tune the trader, and only one Discord user,
the operator, can press them.

There is a two-minute video of everything below on
[the site](https://cody-raves.github.io/Gonuts/#discord).

## Setup, step by step

You do this once. It takes about five minutes and needs no programming.

### 1. What you need

- A Discord account. The free one is fine.
- A Discord server you own, or one where you are allowed to add bots. If you
  have none, press the **+** at the bottom of the server list on the left of
  Discord, choose **Create My Own**, and give it any name.
- GoNuts already installed and trading. See [Getting started](getting-started.md).

### 2. Create the bot and copy its token

1. Open <https://discord.com/developers/applications> in a browser and log in.
2. Press **New Application** (top right). Type any name, for example
   `My GoNuts`, tick the box, and press **Create**.
3. In the menu on the left, click **Bot**.
4. Press **Reset Token**, confirm, then press **Copy**. Paste it into a note for
   a minute. Discord shows it once; if you lose it, press Reset Token again.

The token is the bot's password. Never post it, never share it, and do not put
it in a screenshot. Anyone who has it can control your bot. If it gets out,
press **Reset Token** and paste the new one into GoNuts.

You do not need to switch on any of the "Privileged Gateway Intents" on that
page. Leave them off.

### 3. Invite the bot to your server

1. Still on the developer page, click **OAuth2** in the left menu, then
   **URL Generator**.
2. In the first list (Scopes) tick **bot**. A second list (Bot Permissions)
   appears under it.
3. In the second list tick exactly three: **View Channels**, **Send Messages**
   and **Attach Files**.
4. Copy the link at the bottom of the page and open it in a new tab.
5. Pick your server from the dropdown and press **Authorize**.

The bot now shows up in your server's member list, offline. That is normal: it
comes online when GoNuts connects it.

(For the curious: those three permissions add up to the number `35840`.)

### 4. Copy two ids

GoNuts needs to know which channel to post in and who is allowed to press the
buttons. Discord hides these numbers until you turn on Developer Mode.

1. In Discord, click the gear next to your name (bottom left) to open **User
   Settings**.
2. Click **Advanced**, near the bottom of the list, and switch **Developer
   Mode** on. Close the settings.
3. Right-click the channel where the panel should live and choose **Copy
   Channel ID**. Paste it into your note.
4. Right-click your own name (in the member list or on a message of yours) and
   choose **Copy User ID**. Paste that into your note too.

Both are long numbers, about 18 digits.

### 5. Paste them into GoNuts

1. In game, press **B** to open GoNuts.
2. Click **Autopilot** in the list on the left.
3. Click the button at the top right, labelled **Tools: API key / test**. Its
   label changes to **Discord command centre**. Click it again.
4. Paste the token into **Bot token** (it shows as stars), the first number into
   **Channel id**, and the second into **Your Discord user id**.
5. Press **Save**. It answers: "Saved. Connecting; the panel appears in the
   channel within a minute."

The two other boxes on that card are optional. **Server (guild) id** can stay
empty. **Activity feed channel id** is for a second channel that gets a line
for every sale and purchase as it happens; leave it empty to start with.

### 6. Use the panel

Within a minute one message appears in your channel, with a picture of the
trader's state and controls under it.

- The **dropdown** switches pages: Money, Status, Listings, Items, Opps,
  Orders, Flips, Rivals, Evasion, Payroll and Tune.
- **Start** and **Stop** do what the buttons in game do. **Search** looks up a
  market.
- Only the user id you pasted can press them. Anyone else is told the panel is
  not theirs.

## If nothing shows up

- **Wait a full minute.** The first message can take up to 60 seconds.
- **The bot has to see the channel.** In a private channel, open the channel's
  settings, **Permissions**, and add the bot with View Channel, Send Messages
  and Attach Files.
- **The ids are numbers.** If GoNuts says "Ids are numbers", you pasted a name
  or a link. Copy the id again with Developer Mode on.
- **The token is long.** A token with a character missing connects to nothing.
  Reset it on the developer page, copy it again, and Save.
- **Look at the game log.** Everything the bot does is logged with the words
  `DoughBay Discord`, and the token itself is never written there.

## Running with no game window

The setup card is on the in-game screen. If you run the game with no window,
set the panel up once with a normal window first. The settings are kept in
`config/doughbay/discord.json` and are picked up the same way afterwards.

## Pages

Money, Listings, Opps, Orders, Flips, Rivals (with the underdog's shadow
markets), Payroll, Tune, and a market Search form. **Tune** uses a dropdown for
the settings group and one for the setting, with Toggle, Set value, minus, plus,
and Reset buttons, plus a Presets group. **Flips** shows your own price list:
pick an item, then Sell price, Buy price, Add item or Remove.

Everything the panel changes is logged as `DoughBay Discord`. The settings live in
`config/doughbay/discord.json`, and the token is never written to the log.

## Account scope and occupied slots

With one active account, the default page filters ledger entries to that
account. Saved positions belonging to an older account do not appear in its
Listings rows or Money totals. With multiple accounts, the default remains
the combined hive view; selecting an account filters to that owner.

Money and Listings use the same occupied-slot count. For the local account,
the latest server audit, adjusted for subsequent listings and sales, takes
priority over the session's tracked count. Other accounts and the combined
hive use their scoped ledger counts. Costs and asking prices describe tracked
ledger entries, which can lag a server audit.
