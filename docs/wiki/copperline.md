# Copperline, the new look

Every screen the mod opens is moving to one look, Copperline (`docs/design/copperline/`): deep slate-gray windows with a
copper plaque naming the screen, a bar under it with where you are, your coins and a close button, a rail of sections
down the left, and a line at the foot that says in plain words what the thing under the mouse does. The shared pieces
came first, so every screen already drawn in the Village Hall's look switched at once: the Village Hall, the Storehouse
board, the list menus (the Move Tutor, the shops, the job menus), the Blueprint Table, the Trainer screen, the status
cards and the Pokémon Trader. Each screen is then laid out like its own board (Milestones 44, 47 and 48). Part of
Milestone 47 (Copperline, the new look).

Roadmap items: 47.1, 44.1, 48.1

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

## Decisions

- **Slots keep their places (44.1).** The Drop Box and the counter keep a vanilla chest's slot positions and their
  menus stay `ChestMenu`, so automation, inventory mods and every test that opens them see what they saw before; only
  the mailbox's slots moved (its menu is the mod's own), to put the inbox and pack where a chest has them and the parcel
  next to the address. The plaque on the counter says "Shop Counter"; its old title ("Goods on top, prices below") is
  now the foot line's hint.

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
  and the rest come in Milestones 44, 47 and 48. The Blueprint Table is (48.1, below).
- The Blueprint Table's material slots compare with what the player carries, not with the village's chests (the screen
  knows only the player's inventory). The board's coins, Walk through and favorite key are not in the table (it has none).
- The rail shows no counts yet (the parts can draw them; the Hall sends none).
- The Trainer screen, the status cards and the Pokémon Trader have the new window, colors, buttons and whisper line but
  no plaque yet (they draw their own titles).

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

Showcase scenes: `copperline` (the shell, the parts in every state, the boxes, and the 320x180 icon-grid rail), `mail`,
`counter` and `drop_box` (44.1: the three slot screens).

48.1: `CopperlineBlueprintsGameTests` (3: the table's three views at 427x240, 480x270 and 320x180 with labels one and
three times as long, inside the window and apart, every button, tab, style and row clickable; one copper button per
view and Store All, Store Held, Upload a File..., Open Folder and Back still there; the rows under the mouse, the empty
states' and the materials' words, the search), with `QaBlueprintTableGameTests`, `QaBlueprintTableJobs1111bGameTests`,
`TableSearchGameTests`, `TableLibraryGameTests`, `TableJobsGameTests`, `QaTableJobsGameTests` still passing. Scene:
`copperline_blueprints` (the book, a style from the rail, the Stored tab, both at 320x180, a search for nothing).
