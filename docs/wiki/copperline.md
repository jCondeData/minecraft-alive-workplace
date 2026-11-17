# Copperline, the new look

Every screen the mod opens is moving to one look, Copperline (`docs/design/copperline/`): deep slate-gray windows with a
copper plaque naming the screen, a bar under it with where you are, your coins and a close button, a rail of sections
down the left, and a line at the foot that says in plain words what the thing under the mouse does. The shared pieces
came first, so every screen already drawn in the Village Hall's look switched at once: the Village Hall, the Storehouse
board, the list menus (the Move Tutor, the shops, the job menus), the Blueprint Table, the Trainer screen, the status
cards and the Pokémon Trader. Each screen is then laid out like its own board (Milestones 44, 47 and 48). Part of
Milestone 47 (Copperline, the new look).

Roadmap items: 47.1, 44.1, 44.2, 44.3, 44.4, 48.1, 48.2, 48.3, 48.4

## What a player sees

- **The window.** A dark window with a thin copper line along its top. A copper **plaque** rises over the top edge with
  the screen's name (on the Village Hall, the open section's name).
- **The lintel.** The bar under the plaque: where you are on the right (in the Village Hall, the village and today's
  date; on a list menu, your money with a coin), and a small **close X** at its end that closes the screen like Esc.
- **The rail.** In the Village Hall, the ten sections run down the left as a rail: each one's icon (drawn for
  Copperline: a house for Overview, two villagers for People, a bed for Homes & Food, and so on) and its word. The
  open section is lit with a copper mark; the one under the mouse gets a lantern-yellow edge. On a small screen (320x180,
  GUI scale 3 on a 960x540 window) the rail turns into a two-column grid of icons, and the line at the foot says which
  section the mouse is on.
- **The whisper line.** The foot of the window explains whatever is under the mouse (a row's first line, a rail section
  and what it is for), or the screen's hint when nothing is, with **Esc close** on the right (just **Esc** on a small
  screen).
- **The parts.** Buttons are slate gray with a light top; under the mouse they get a lantern-yellow edge, pressed they
  sink, unusable ones are flat and gray. Only the one main or chosen button is copper. Lists sit in sunken wells; the row
  under the mouse turns lighter with a yellow edge, the chosen row gets the copper mark. Bars, tabs, icon cells and
  scroll bars follow the same colors.
- **Screens with item slots (44.1): the Mailbox, the Drop Box and the Shop Counter.** The same window, plaque, lintel
  and whisper line round their real slots. Every slot, your inventory's included, is a sunken well, lit with a
  lantern-yellow edge under the mouse, and each block of slots has a heading ("MAIL FOR YOU", "IN THE BOX", "PRICE
  LIST", "YOUR PACK"). The lintel counts what waits: "3 items waiting" in the mailbox, "4 stacks waiting for a porter"
  in the Drop Box, "2 columns for sale" on the counter. A panel on the right says what the screen is for: the mailbox's
  "To:" and "Letter:" fields, its parcel (3x3 slots) and the one copper **Send** button (gray until a name is written);
  the Drop Box's and the counter's "What happens". The counter names its two rows "Sells" and "Price" at the left. The
  foot line says what the slot under the mouse is for ("What one sale hands over: the item and how many").
- **The trading screen of the mod's own jobs (44.2).** The crafters (Tailor, Vintner, Printer, Jeweler and the rest),
  the Shopkeeper and the Ferryman without CobbleDollars, the retired jobs kept for old saves, and the sneak-click trades
  of every mod job open in Copperline; vanilla's professions keep vanilla's screen. The villager's name is on the
  plaque and the lintel counts the offers ("6 offers"). On the left, "OFFERS" is a sunken list of rows: the price, a
  second price, an arrow and the goods, the chosen row with the copper mark, sold-out rows dimmed with the red cross,
  rows cheaper for you with a green down arrow (the foot line says "Cheaper for you: 2 instead of 3."). Under "TRADE"
  the level's name and its bar (earned experience in green, what the trade in the wells adds in lantern yellow; a
  star at Master), then the two price wells, a copper arrow and the goods; "YOUR PACK" below. Clicking a row moves
  its price from the pack into the wells, as vanilla's button does; the mouse wheel and the scroll bar scroll a long
  list.
- Nothing a screen did before is gone: every button, price, tooltip, sound, refusal and key works as before; only the
  look changed.

## How it works

- **The palette and the parts:** `CopperStyle` (client) holds the sixteen Copperline colors (Void, Deepslate 900 to 600,
  Slate Line, Tuff, Quartz, Copper and its light and dark, Lantern, Verdigris, Redstone, Lapis, Amethyst) and draws
  every part on `boards/Components.png`: buttons (neutral, copper, danger; normal, hover, pressed, off), rail entries,
  list rows (header, normal, hover, selected, locked), chips and tags, progress bars, toggles (off, on, locked), the
  slider, the number field, text fields (normal, focus, error with its error line), the scroll bar, item slots, the
  confirm dialog, the "What happens" and refusal boxes, the empty state, the tooltip and the toast.
- **The places:** `CopperLayout` (core, pure numbers) works out the window, the plaque, the lintel, the close X, the rail
  (a column, or at 320x180 a two-column icon grid), the stage and the whisper line for a screen size, and finds what is
  under a point. The screens draw from it and take their clicks with it, and the GameTests check it.
- **The switch:** `TableStyle`, which every screen in the Hall's look draws through, now draws with `CopperStyle`; its
  color names stayed (the screens use them) with Copperline's values. `ChoiceTableScreen` puts the plaque, the lintel
  (with the money on list menus) and the close X on every Hall-look menu, and the whisper line at its foot.
  `VillageHallMenuScreen` draws the Hall's rail from `CopperLayout`.
- **Slot screens (44.1):** `CopperSlots` (core, pure numbers) places the window, lintel, side panel and whisper line
  round a menu's slot frame, which stays where the menu puts its slots: the Drop Box and the counter are still vanilla
  chest menus (three rows; two rows) with a chest's slot places, now on their own menu types
  (`aliveworkplace:drop_box`, `aliveworkplace:shop_counter`) so the client can draw them; the mailbox's inbox and pack
  moved to a three-row chest's places and its parcel into the panel (`MailboxMenu`). `CopperSlotScreen` (client) draws
  the frame; `MailboxScreen`, `DropBoxScreen` and `ShopCounterScreen` add their panels and words. No new art: the
  wells, panel, buttons and glyphs are 47.1's.
- **Trades (44.2):** the mod's own jobs open vanilla's merchant menu on its own type, `aliveworkplace:trades`
  (`CopperTradeMenu`, core): `VillagerMixin` wraps the call that opens vanilla's trading screen, and
  `CopperTrades.open` does what vanilla's does (the same menu, offers and level packet) for any villager whose job is in
  the mod's namespace. The offers packet, the trade choice, the shop's stock refresh and the level-up are vanilla's and
  the mod's code as before. `CopperSlotScreen` learned a `shift` (the slot frame starts 100 pixels into vanilla's
  276-wide merchant menu, so the list fills that gutter) and `CopperTradeScreen` (client) draws the list, the level
  bar and the arrow from `CopperTrades`' boxes. No new art.
- **The art:** the nine-slices (window, plaque, panel, well and its focus and error states, tooltip, toast, the button and
  copper button in every state, danger) are drawn as letter grids on the palette by `tools/textures/art/copperline.py`;
  the 8-pixel status glyphs and the 16-pixel Hall icons start from the designer's sheets and are locked onto the palette
  there. They are GUI sprites under `textures/gui/sprites/copperline/`, each nine-slice with its `.mcmeta`.

## Switches

none

## Saved data

none (only the look changed)

## Items, blocks, jobs, commands

- No new items, blocks, jobs or commands. Two new menu types (44.1), `aliveworkplace:drop_box` and
  `aliveworkplace:shop_counter`, for the Drop Box's and the counter's screens.
- New text (44.1): `screen.aliveworkplace.copperline.pack` ("YOUR PACK"), and the slot screens' headings, counts and
  foot lines under `screen.aliveworkplace.mailbox.inbox_header`, `screen.aliveworkplace.drop_box.contents` and
  `screen.aliveworkplace.shop_counter.prices` and the keys after them.
- New text: `screen.aliveworkplace.copperline.key.esc`, `screen.aliveworkplace.copperline.key.close` (the whisper
  line's "Esc close"), `screen.aliveworkplace.copperline.where` (the village and the date on the Hall's lintel),
  `screen.aliveworkplace.copperline.rail_whisper` (a rail section and what it is for),
  `screen.aliveworkplace.copperline.what_happens`, and the parts screen's sample words under
  `screen.aliveworkplace.copperline.parts.title` and the keys after it (the showcase only).

## The Blueprint Table (48.1)

The table is the board's **Blueprint Book** (`boards/Blueprints.png`), laid out by `ui/BlueprintBookLayout` (pure
arithmetic, checked by the GameTests): the plaque, the Library and Stored tabs on the lintel with the count text after
them, a **style rail** (the board's STYLE filter: as drawn and each style the selected build takes, the chosen one
copper-marked; the first entry reads "As drawn" beside a log, and its whisper names it in full, "Timber (as drawn)", so the
rail never cuts the chosen style; at 320x180 an icon grid), the search box and a one-line list (NAME and SIZE; locked builds with the lock
glyph in gray, "???" for rare ones), and on the right the 3D preview, the name with its tag chip (LOCKED or the style),
the size, jobs and beds, how to unlock it, and a row of **material slots**: a slot the player doesn't carry enough of has
the red well, and the line under the row says it with the warning glyph ("Not carried: Glass", or "You carry
all of it" with the good glyph). One row of buttons along the stage's foot; the first is the view's one copper button
(Get Blueprint, Take One Out, Upload), the others neutral. The game's own widgets still take the clicks; the screen draws
them in Copperline. The whisper line says what each button, row, style, slot and the preview do.

## The Trainer screen (48.2)

The Trainer screen is the board's **Trainer Battle** (`boards/Trainer.png`), laid out by `ui/TrainerLayout` (pure
arithmetic, checked by the GameTests): the plaque with the trainer's title, the trainer's rank and village on the lintel
by the close X; a **rail** with the trainer's portrait (the real villager model, following the mouse), their name and
title beside it, **Your record** and **Today** under it (today's line with its glyph: good, warning or info), and
Challenge, Trade and Close at its foot. The buttons are fitted by `StatusCard.buttonRow` as before (B1131: never past the
rail, wrapping onto more rows) and each row is then widened to fill the rail. **Challenge** is the screen's one copper
button; Trade and Close are neutral, and an unavailable button is flat and gray. The **stage** shows a refusal box at the
top when Challenge is off ("Can't challenge now" and the reason), "Challenge <name>" with the prize, the team as
**cards** (the real Pokémon model, the name and level; a level brought down to the cap in Lantern with the warning
glyph; "???" and "Not seen yet" until the player wins once), the team's hint line, **Your party** as striped rows (name,
level, a status glyph and word: Ready, Fainted, Over the cap) and the rules. When it doesn't fit (a big team, a small
screen) the stage scrolls with the wheel and shows a scroll bar. The team grid takes as many columns as fit at 79
pixels or more (room for a capped "Lv 100" whole, B1351) and evens them out over its rows (a team of six: three and three on a roomy screen, two by three at
320x180). The whisper line says what the thing under the mouse is or does (the trainer, the record, today, a card, a
party line, the rules, each button; a grayed Challenge gives its reason); with nothing under the mouse it keeps the
screen's hint.

## The status card, tooltips and toasts (48.3)

Every status card (the job cards, the Nurse's, the Fossil Scientist's) is the board's **pinned card**
(`boards/VillagerCard.png`), laid out by `ui/StatusCardLayout` (pure arithmetic, checked by the GameTests): a copper edge
along the top; the villager's face (the real model, framed on the head) in a well, the name beside it with the job and
level under it, and the **close X** at the right (Close is the X now, not a button); the status line(s); the heading over
the labeled lines, their values and bars in Copperline's colors (green Verdigris, red Redstone, orange Copper, gold
Lantern); a divider; the reason the first grayed-out button can't be used, in a Redstone-edged box; the buttons (the
first usable one is the card's one copper button, the rest neutral, a grayed-out one flat); and the hint at the foot.
The foot is laid out from the bottom up, so on a short screen the lines give up their room (whole rows only) and the
buttons never leave the card.

**Tooltips.** An item line under the mouse shows a Copperline tooltip **beside the card** at that row (the item, the
line, the value with its status glyph), so it never covers the row it describes; a grayed-out button shows its reason
with the bad glyph and "Not yet".

**Toasts.** `CopperToast` draws the board's toast (the Lantern edge, a section icon, a title and a line). A builder
finishing (or taking down) a build sends one to the player it's for, "Stone House / Mira finished it.", through
`CardToasts` (a client without the mod gets only the chat line, which still goes).

## The City Plan and the Colony Charter (44.3)

**The City Plan.** The plaque says "City Plan of Thornholm"; the lintel says which tool is in hand ("Tool: Brush") or
"View only" for a player who may only look. The rail, under "TOOLS", holds Brush, Area, Erase, Road and Wall (each with
its item: a brush, a map, a sponge, a dirt path, a stone brick wall), the chosen one copper-marked, and Undo below a rule
with the steps left as its count; at 320x180 (GUI scale 3) the rail is the two-column icon grid. The map keeps its look:
the land, the grid, each zone tinted and edged in its kind's color, Keep Clear hatched, roads, the wall line, the
banners and the zone names, now in a Void frame with "N ^" in its corner. Under the map, the road width (Lane, Street,
Avenue) or the wall's Closed/Open and the next road's style, as before. On the right, "ZONES 3/16" with **+ New**, the
one copper button; the zones in a sunken list (the kind's swatch, the name, the cells; the chosen one copper-marked);
the chosen zone's name in a well, its style (with the style's icon), kind, Renew and a red Delete; and "LEGEND" when
there's room (not at 320x180, where the zone swatches and the foot line do its work). The foot line says what the tool,
button or row under the mouse does ("Drag a rectangle to fill it with the chosen zone."), on the map the zone under the
mouse ("Homes: Homes, As drawn") or what to do next; the old hover tooltips moved there.

**The Colony Charter.** The plaque says "Colony Charter of Thornholm", the lintel "Ring: 256 to 1024 blocks out". The
map (unchanged: the land on parchment, banners, the ring, the circles round other halls, the red cross) fills the stage's
left; on the right "THE SPOT" says where the colony goes with a glyph and its color (blue "No spot chosen yet.", green
the spot, red the server's refusal), "THE RING" explains the ring, and **Done** is the copper button. Pointing at the
map shows a Copperline tooltip (how far and which way, with a green "A colony can go here" or a red reason).

How: `ui/CityPlanLayout` (core, pure numbers) places both screens' parts from `CopperLayout`'s shell, and
`CopperlineCityPlanGameTests` checks them at 427x240, 480x270 and 320x180 with the English labels and three times as
long. The screens keep their widgets for the clicks (drawn in Copperline), so every button, tool, key (Enter, Esc,
Ctrl+Z), drag and double-click works as before. The City Plan's scene now takes its third still at GUI scale 3.

## The Guide Book and the Settings screen (44.4)

**The Guide Book.** The plaque says "Guide Book"; the lintel the chapter and the page ("Builders · Page 5 of 36"), or
"35 pages in 7 chapters" on the contents. The rail, under "CHAPTERS", holds Contents (the book) above a rule, then
the chapters by short name with their icons and page counts (Start, Builders, Village, Gathering, Making, Safety,
Pokémon), the open one copper-marked. The stage keeps the pictures and pages: the page's title, its screenshot in a
Void frame (16:9, as big as the words under it leave room for) and its words; Back and Next (the copper button) along
the foot with "5 / 36" between them. The contents page has the intro and the chapters, each with the page it starts on.
At 320x180 the rail is the icon grid and the arrows ("<" ">") sit beside the title. The foot line says what the
chapter, arrow or close X does.

**The Settings screen** (Mod Menu, like `boards/Settings.png`). The plaque says "Alive Workplace Settings", the lintel
the open section and its size ("Village · 29 settings"). The rail, under "SETTINGS FOR", holds the sections (Work,
Jobs, Builders, Village, Villagers, Defense, Realms, Pokémon) with their counts. The stage lists the open section's
settings: "DISTANCES AND NUMBERS" then "SWITCHES", each row its name and its control on the right: a toggle with On
(Verdigris) or Off, [-] value [+] for a number of 20 steps or fewer, a slider and its value well for a bigger one; at
the foot "Reset this page" and **Done**, the copper button. Pointing at a name shows its tooltip in a Copperline
tooltip; the foot line says how to change it, and by default that a server keeps its own file.

How: `ui/GuideLayout` and `ui/SettingsLayout` (core, pure numbers) place both screens' parts from `CopperLayout`'s
shell; `guide/GuideBook.Reader` and `ui/SettingsEditor` (core) do what a click does, so
`CopperlineGuideSettingsGameTests` opens every page and changes settings the way a player's clicks do, and checks the
layouts at 427x240, 480x270 and 320x180. Every setting has its section (`SettingsLayout`'s list; the test fails for a
new one left out). Saving is as before: closing the screen writes the file and applies it.

## The Chronicle, the list menus and the Pokémon Trader (48.4)

**The Chronicle** (the Village Hall's Calendar, Chronicle) is the board's **Chronicle** (`boards/Chronicle.png`), laid
out by `ui/ChronicleLayout` (pure arithmetic, checked by the GameTests) beside the Hall's section rail: the plaque reads
"Chronicle" and the lintel "Oakhaven · 128 entries since day 1" (on a small screen "128 entries"). On the left a **Show**
column: All, People, Building, Trade, Festivals, Defense and Stories, each with its Copperline icon and how many entries it
holds, the open one copper-marked; a click lists only that kind (every entry kind is in exactly one group:
`Chronicle.Group`), and Back is at the column's foot. At 320x180 the column is icons with their counts and the whisper line
names them. On the right the entries, newest first, under **day headers** ("DAY 47 · AUTUMN 5" over a rule, the season
and its day worked out with the server's season length); each entry is a chevron, its icon and its text on one line, and
a click **opens** it to its whole text (a copper-edged box) and closes it again. The mouse wheel scrolls the list. The
whisper line says when an entry was written and that a click reads all of it, what a filter shows, or the page's hint.

**The list menus** (ROADMAP 42, `ChoiceTableScreen` in list mode: the Move Tutor, the daycares and their keeper, the
Professor, the Innkeeper, the Legend guest's terms, Research, the Smith's orders, the builder's tool pickers, the shops
and the Ferry) put their **tabs down a rail** at the left, a two-column grid of icon cells (a Pokémon of your party, a
research tree's book), the open one copper-marked; the whisper line names the tab under the mouse and what it is ("The
scholars' research: Worked by the village's scholars at their desks"), and its tooltip shows as before. The list sits on
the stage beside it, laid out by `ui/ListMenuLayout`. A menu without tabs has no rail and its list fills the stage. The page arrows stay at the whisper
line's right. Every row, button, price, tooltip and click is as before.

**The Pokémon Trader** has the shell round its offers and party: the trader's name on the plaque, "3 offers today" on the
lintel beside the trader's book (moved there from the corner, so the close X has room), the close X, and a whisper line
that names the offer under the mouse and its price ("Pikachu · For: any Electric, Lv. 20+. Click to pick it.") or the
Pokémon or book under it.

## Decisions

- **The Settings screen saves on closing, as before (44.4).** The board's Save button and its red "fix this field"
  state are left out: no setting is typed in (they're toggles, [-] [+] and sliders, all kept in range), so there's never a
  value to refuse, and Done saves. Sections are the mod's own grouping of its 158 settings; the board's "Screen"
  section has no settings to hold.
- **Slots keep their places (44.1).** The Drop Box and the counter keep a vanilla chest's slot positions and their
  menus stay `ChestMenu`, so automation, inventory mods and every test that opens them see what they saw before; only
  the mailbox's slots moved (its menu is the mod's own), to put the inbox and pack where a chest has them and the parcel
  next to the address. The plaque on the counter says "Shop Counter"; its old title ("Goods on top, prices below") is
  now the foot line's hint.

- **The team's Pokémon sit in wells (48.2).** Each team card puts its Pokémon's model in a sunken well as tall as the
  card (24 pixels), the same well a "???" card has, so the creature (the board's subject) reads first; the name and level
  get a few pixels less and trim with "..." on narrow cards (the whisper line gives them in full).
- **The list menus' rail is icons (48.4).** A worded rail took a quarter of the window and cut the list's columns
  ("Level 0 ..." for "Level 0 of 3" on Research); the icon grid takes 50 pixels, so every column reads as it did under
  the old row of tabs, and the whisper line and tooltip give the tab's name.
- **Old names, new colors.** `TableStyle` keeps its color names (`TITLE`, `NAME`, `SUBLINE`...) so the eight screens on it
  switched without each being rewritten; `TITLE` is now Copper Light, text Quartz, second lines Tuff.
- **The village's name and date moved from the rail to the lintel** ("where you are"), so the rail is the sections only
  and the 320x180 grid has room; on a small screen the lintel shows just the date.
- **The icons keep a few material colors.** The glyphs and the nine-slices use only the sixteen Copperline colors; the
  Hall icons also use the designer sheet's handful of wood, wheat, grass and skin shades (named in the recipe), since a
  bed or a map in only slate and copper would not read.
- **Disabled text** is Tuff at 70% over Deepslate 900 (about 4:1, as the README asks), the one color not in the table.
- The close X is new on the Hall-look menus and the Blueprint Table; it closes as Esc does, with the button click.

## Known limits

- The screens are not yet laid out like their own boards: the Hall's Overview tiles, the Trainer screen, the status card
  and the rest come in Milestones 44, 47 and 48. The Blueprint Table (48.1), the Trainer screen (48.2), the status card (48.3) and the Chronicle, list menus and Pokémon Trader (48.4) are (above).
- The Blueprint Table's material slots compare with what the player carries, not with the village's chests (the screen
  knows only the player's inventory). The board's coins, Walk through and favorite key are not in the table (it has none).
- The rail shows no counts yet (the parts can draw them; the Hall sends none).
- The Trainer screen's cards show no types or moves (the board's), and its rail no stars or last battle: the server
  doesn't send them. Challenge has no crossed-swords glyph (no such glyph yet).
- The Chronicle (48.4) has no search box or sort menu (the board's): the page shows a hall's newest entries (up to 36 at
  a time, of the 100 a hall keeps) and the filters already narrow it; nor the time of day or the people and places chips
  of an entry, which the chronicle doesn't keep. A click on a name doesn't jump to that villager.

## Proof

`CopperlineShellGameTests.copperlineShellFitsAtGuiScaleTwoAndThree` (the shell at 427x240, 480x270 and 320x180: nothing
off the screen or overlapping, every rail entry clickable, the rail a grid only at 320x180),
`CopperlineShellGameTests.copperlineScreensButtonsWorkAtGuiScaleTwoAndThree` (the list menus' tabs and page arrows, the
Trainer's and the status card's buttons inside the window and clickable at both scales), with the screens' own tests
still passing: `StatusCardButtonsGameTests`, `TrainerScreenButtonsGameTests`, `QaTrainerScreen1111dGameTests`.

`CopperlineSlotScreensGameTests.mailboxSlotsFitCopperlineAndShiftClicksStillWork`,
`CopperlineSlotScreensGameTests.dropBoxSlotsFitCopperlineAndShiftClicksStillWork`,
`CopperlineSlotScreensGameTests.shopCounterPricesSetOnItsScreenStillSell` (44.1: each opened by using the block; every
slot inside the window and off the lintel, panel and foot line, none overlapping, the window inside 427x240;
shift-clicks both ways; the inbox refuses items; a price set on the counter's screen sells on the trade screen and is
logged).

`CopperlineTradesGameTests` (44.2: a Tailor's sneak-click opens `aliveworkplace:trades` and every one of her offers
trades through the packets a client sends; a Farmer keeps `minecraft:merchant`; every mod job with offers opens in
Copperline; a shopkeeper's offers are rebuilt from the stock on each opening and a sale is paid into the chest; the
level, experience and bar reach the client, the bar fills as vanilla's, a trade levels the villager up and the next
opening says so; every slot clear of the list, the level row, the arrow, the lintel and the foot line), with
`QaEveryMenuGameTests` pairing the new menu type and the shop tests (`QaClicksShops1101GameTests`, `ShopStockGameTests`,
`QaShopFerry1031GameTests` and the rest) still passing.

QA (lane c, 2026-11-17): `QaCopperTrades1117cGameTests` (12, from the spec, through the packets a client sends: too
few emeralds trade nothing and come back on closing; a sold-out offer is shown sold out and refused; a shift-click on
the goods repeats the trade; Hero of the Village prices reach the screen and buy; a second player is refused and a
killed villager closes the screen with the emeralds kept; a mod job with no offers and a baby open no screen, the
wandering trader and a Librarian keep vanilla's; the Ferryman sells a ticket in Copperline without CobbleDollars; a
shop lot emptied while the screen is open is refused, shown sold out and the buyer keeps the price; a job change
switches the screen both ways; a Tailor saved and loaded keeps his offers and trades; a Master trades with no level
bar; the new sentences) and `QaCopperCityPlan1117cGameTests` (4: the City Plan's and the Charter's parts inside the
window and apart at a dozen screen sizes, from GUI scale 1 at 1920x1080 to both sides of the compact switch, the map
never smaller on a bigger screen; the zone list's row picking with no zones, scrolled, at 16 zones and outside the
list; the new sentences).

Showcase scenes: `copperline` (the shell, the parts in every state, the boxes, and the 320x180 icon-grid rail), `mail`,
`counter` and `drop_box` (44.1: the three slot screens), `crafter_trades` (44.2: a Journeyman Tailor's trades, then an
offer chosen).

48.1: `CopperlineBlueprintsGameTests` (3: the table's three views at 427x240, 480x270 and 320x180 with labels one and
three times as long, inside the window and apart, every button, tab, style and row clickable; one copper button per
view and Store All, Store Held, Upload a File..., Open Folder and Back still there; the rows under the mouse, the empty
states' and the materials' words, the search), with `QaBlueprintTableGameTests`, `QaBlueprintTableJobs1111bGameTests`,
`TableSearchGameTests`, `TableLibraryGameTests`, `TableJobsGameTests`, `QaTableJobsGameTests` still passing. Scene:
`copperline_blueprints` (the book, a style from the rail, the Stored tab, both at 320x180, a search for nothing).

48.2: `CopperlineTrainerGameTests` (3: the Trainer screen at 427x240, 480x270, 640x360 and 320x180 with labels one,
three and five times as long: the rail, portrait, name, record and stage inside the window and apart, clear of the
whisper line and the close X; Challenge, Trade and Close inside the rail, in order, apart, clickable and as wide as
they need; teams of one to eight cards inside the stage and apart in even rows; Challenge the one copper button; the
new sentences in the lang file), with `TrainerScreenGameTests`, `TrainerScreenButtonsGameTests`,
`QaTrainerScreen1111dGameTests` still passing. Scene: `copperline_trainer` (with Cobblemon: the mouse on Challenge and
on Trade, then at 320x180).

48.4: `CopperlineChronicleGameTests` (3: the Chronicle's column, filters, Back and list at 427x240, 480x270, 640x360 and
320x180 with group words one and three times as long, the rows apart and an open entry taller, scrolling only when
needed; a real hall's Chronicle page: newest first with dates, every kind in exactly one group, the filters' counts, a
filter's click showing only its group, an empty group's line, All bringing every entry back, Back still first; the list
menus' rail with no, 1, 6 and 12 tabs, the list beside it and the page arrows in the whisper line; the Trader's book on
the lintel clear of the plaque and the close X; the new sentences), with `CopperlineShellGameTests`,
`PokemonTradeLayoutGameTests`, `QaTradeRows1109dGameTests`, the hall page tests and the list menu tests still passing.
Scene: `copperline_chronicle` (the Chronicle with the raid's entry open, Show: Defense, at 320x180, then the Research
list menu with its tab on the rail).

48.3: `CopperlineStatusCardGameTests` (3: a builder's card, an empty card and a thirty-line card at 427x240, 480x270,
640x360 and 320x180 with labels one, three and five times as long: the card inside the screen, portrait, name and close
X apart, buttons inside the card, in order, apart, under the reason box and the divider and above the hint; a builder's
card whole at 240 tall; one copper button, never Close or a grayed-out one; the toast's words and packet), with
`StatusCardButtonsGameTests`, `QaStatusCardRows1111cGameTests`, `JobCardGameTests`, `NurseCardGameTests` and
`CopperlineShellGameTests` still passing (23 of 23). Scene: `copperline_status_card` (a line's tooltip, a grayed-out
button's reason, the toast, then at 320x180).

44.4: `CopperlineGuideSettingsGameTests` (6: the Guide Book at 427x240, 480x270 and 320x180 with labels one and three
times as long, every page's picture and words on the stage; every page opened with Next, Back, the rail and the
contents' rows, with and without Cobblemon and the City Plan; every setting in a section; the Settings screen at the
three sizes with every setting's name fitting beside its control; a switch, a small number and a slider changed by
clicks, a page reset, and the file read back; the new sentences), with `GuideGameTests` and `ConfigGameTests` still
passing. Scenes: `guide` (every page, a chapter from the rail, then at 320x180) and `config` (Work's numbers, Village's
switches, Festivals turned off and saved, then at 320x180).

QA, lane E, 2026-11-17: `QaCityPlanCopper1117eGameTests` (44.3, 3: the City Plan and the Charter on both sides of the
compact boundary, a 1920x1080 and an ultrawide screen and 320x180, the map a square of whole cells and nothing
overlapping; a full plan's 16 zones each picked by a click on its row at some scroll, no click on an empty row, the
strip under the last drawn row, the scroll bar or under the list picks a zone; the tool and width lines read with
their real arguments in American English) and `QaBlueprintBook1117eGameTests` (48.1, 5: the most-styled build's style
rail fits at every GUI scale; a style the build lacks, a deleted build and a press from 20 blocks away hand nothing out
and keep the blank; a shelved blueprint of a deleted build is listed, survives a save and reload and comes back out;
every listed build's materials name real items). With Create: `QaTableCreate1117eCompatTests` (every Create build's
materials, preview and styles, and Get Blueprint for each).
