# Copperline, the new look

Every screen the mod opens is moving to one look, Copperline (`docs/design/copperline/`): deep slate-gray windows with a
copper plaque naming the screen, a bar under it with where you are, your coins and a close button, a rail of sections
down the left, and a line at the foot that says in plain words what the thing under the mouse does. The shared pieces
came first, so every screen already drawn in the Village Hall's look switched at once: the Village Hall, the Storehouse
board, the list menus (the Move Tutor, the shops, the job menus), the Blueprint Table, the Trainer screen, the status
cards and the Pokémon Trader. Each screen is then laid out like its own board (Milestones 44, 47 and 48). Part of
Milestone 47 (Copperline, the new look).

Roadmap items: 47.1

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
- **The art:** the nine-slices (window, plaque, panel, well and its focus and error states, tooltip, toast, the button and
  copper button in every state, danger) are drawn as letter grids on the palette by `tools/textures/art/copperline.py`;
  the 8-pixel status glyphs and the 16-pixel Hall icons start from the designer's sheets and are locked onto the palette
  there. They are GUI sprites under `textures/gui/sprites/copperline/`, each nine-slice with its `.mcmeta`.

## Switches

none

## Saved data

none (only the look changed)

## Items, blocks, jobs, commands

- No new items, blocks, jobs or commands.
- New text: `screen.aliveworkplace.copperline.key.esc`, `screen.aliveworkplace.copperline.key.close` (the whisper
  line's "Esc close"), `screen.aliveworkplace.copperline.where` (the village and the date on the Hall's lintel),
  `screen.aliveworkplace.copperline.rail_whisper` (a rail section and what it is for),
  `screen.aliveworkplace.copperline.what_happens`, and the parts screen's sample words under
  `screen.aliveworkplace.copperline.parts.title` and the keys after it (the showcase only).

## Decisions

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

- The screens are not yet laid out like their own boards: the Hall's Overview tiles, the Blueprint Book, the Trainer
  screen, the status card and the rest come in Milestones 44, 47 and 48. The Blueprint Table's vanilla buttons and tabs
  stay vanilla until 48.1.
- The rail shows no counts yet (the parts can draw them; the Hall sends none).
- The Trainer screen, the status cards and the Pokémon Trader have the new window, colors, buttons and whisper line but
  no plaque yet (they draw their own titles).

## Proof

`CopperlineShellGameTests.copperlineShellFitsAtGuiScaleTwoAndThree` (the shell at 427x240, 480x270 and 320x180: nothing
off the screen or overlapping, every rail entry clickable, the rail a grid only at 320x180),
`CopperlineShellGameTests.copperlineScreensButtonsWorkAtGuiScaleTwoAndThree` (the list menus' tabs and page arrows, the
Trainer's and the status card's buttons inside the window and clickable at both scales), with the screens' own tests
still passing: `StatusCardButtonsGameTests`, `TrainerScreenButtonsGameTests`, `QaTrainerScreen1111dGameTests`.

Showcase scene: `copperline` (the shell, the parts in every state, the boxes, and the 320x180 icon-grid rail).
