# The list screen

The menus that opened as a plain gray chest of buttons move, one by one, to a list drawn in the Village Hall's look:
the title and your money at the top, a row of tabs, a list of rows with a button on each, and a hint at the foot. The
Move Tutor's lessons were the first; the Pokémon Professor's hints, the Rancher's Daycare and the Daycare Keeper followed
(all Cobblemon). Part of Milestone 42 (One look for every screen).

Roadmap items: 42.1, 42.2

## What a player sees

Right-click a Move Tutor and the lessons open on a dark panel with a slate-blue edge: the tutor's title in gold at the
top left and your money ("1,250 CobbleDollars" or "40 emeralds") at the top right. Under it, one tab per Pokémon in your
party, the open one gold. Then the chosen Pokémon's lessons, one row each: the move, its type and kind (and "Egg move"),
its price (green when you can pay it, red when you can't) and a **Teach** button. Click Teach and the row turns gold and
the button says **Confirm**; click again to pay and teach. Lessons the tutor isn't experienced enough for are gray rows
with no button and the rank they need ("Expert tutors and up"). Pointing at a row, a tab or the money shows the same
tooltip the chest showed (power, accuracy, the price and what a click does). The footer says what to do, and the page
arrows sit at its right when there are more lessons than one page holds.

The Cobblemon menus that followed (42.2) look the same, with their own rows:

- **The Pokémon Professor** (right-click with an empty hand): the village Pokédex as a row (how many species, the
  latest ones in its tooltip); with Evolution Studies, today's evolution stone with its price and a **Buy** button (a
  gray "Sold today" once bought); then one row per party member, its nature beside its name and a **Note it** button
  that puts the hints in chat. Pointing at a Pokémon's row shows every hint, as the chest did. No party: one gray row
  says so.
- **The Rancher's Daycare**: a row for each of yours in the rancher's care (the level it'd come back at, the price and
  **Collect**), then one for each party member with **Leave** (then **Confirm**); a Pokémon that can't be left (the last
  one with you, the daycare full, already at the top level) is a gray row giving the reason.
- **The Daycare Keeper**: once you've left a pair, a row for each of the two, one for how well they get along (the chance
  of an egg at dawn as its second line), the eggs (**Collect**, the price of one egg, gray with "No eggs yet") and **Take
  back** (gray, "Collect the eggs first", while eggs wait). Then your party: **Pick**, then **Pair** on the partner (each
  row says how well it'd get along with the one picked), **Unpick** to change your mind; gray rows while you have a pair
  there or she's full.

In all three, pointing at your money shows the menu's own tooltip (what the job does, the price rules, the odds), and a
price you can't pay is red.

If your party changes while the screen is open (a Pokémon caught, one sent to the PC) or your money does, the tabs,
rows and prices follow at once, without a click.

## How it works

**Same menu, new look.** The server's menu is still a `ChoiceMenu`, six rows of nine buttons; a click is still the
chest click on that slot, so every button, price, sound and refusal works exactly as before (the menu's own code
decides; the screen only draws). `ChoiceMenu.openList` opens it on the list menu type `aliveworkplace:choice_list`,
whose client copy is a `ChoiceView` with the server's slots in the same order, drawn by `ChoiceListScreen`
(`ChoiceTableScreen` in list mode).

**What each icon is travels with it.** `ChoiceList` marks an icon in its custom data: a row (its second line, price,
button label, gray, chosen), a tab (open or not), a page arrow (next or previous) or the menu's own line (the money,
the footer hint and the column headings). The texts go as text components, so they keep their colors and are translated
in the player's own language. A shown button without a mark is a plain row (its name and first tooltip line, the whole
row a button), so a menu works on the list before it marks anything.

**Following changes.** `ChoiceMenu.everyTick` runs a check on the server every tick while the menu is open. The Move
Tutor compares the party and the money with what it last laid out and lays the page out again when either changed; a
lesson waiting for Confirm stays chosen unless its Pokémon is gone. The Professor and both daycares do the same
(`CobblemonTutors.shownFor`), keeping a Pokémon waiting for Confirm or a partner.

**Shared pieces.** `TableStyle`'s list-menu section (a text button, a tab, the row's columns and headings, a row, the
footer hint, the money line) is what the next menus (42.2 to 42.4) and the Trainer screen use.

## Switches

none

## Saved data

none (the screen keeps nothing; the lessons, prices and tutor ranks are the Move Tutor's as before)

## Items, blocks, jobs, commands

- `aliveworkplace:choice_list`: the list screen's menu type.
- New text (42.2): the hints `screen.aliveworkplace.professor.list_hint`, `.column_name`, `.column_notes`,
  `.stone_second`, `.stone_sold_short`, `.button_buy`, `.button_note`; the daycare `screen.aliveworkplace.daycare.list_hint`,
  `.column_pokemon`, `.column_state`, `.in_party`, `.button_collect`, `.button_leave`; the keeper
  `screen.aliveworkplace.daycare_keeper.list_hint`, `.in_care`, `.each`, `.to_party`, `.picked_short`, `.with_short`,
  `.button_pick`, `.button_unpick`, `.button_pair`, `.button_take_back`.
- New text: `message.aliveworkplace.tutor.button_teach`, `message.aliveworkplace.tutor.button_confirm`,
  `message.aliveworkplace.tutor.needs_tier_short`, `message.aliveworkplace.tutor.list_hint`, the column headings
  `message.aliveworkplace.tutor.column_move`, `message.aliveworkplace.tutor.column_type` and
  `message.aliveworkplace.tutor.column_price`, and the default footer `screen.aliveworkplace.list.hint`.

## Decisions

- Option A, a list, for the menus that were a chest (owner, 2026-11-08, from `docs/design/M42/menu_a.png`).
- 42.1: one menu type for every list menu, and the row data as marks on the chest's own icons (not a new packet), so
  the clicks and the QA check that the server and client slots agree stay as they are, and a menu moves by opening with
  `ChoiceMenu.openList` and marking its icons.
- 42.1: moves a Pokémon already knows stay off the list (the mockup's gray "Already knows" row isn't shown): the
  tutor never offered them, and its tests require that. Gray rows are the lessons beyond the tutor's rank.
- 42.1: the type and kind sit in a column beside the name, as in the mockup, not on a second line.
- 42.2: the Professor's party stays rows, not tabs (each Pokémon is a thing to read and note, not a page to open); its
  info book's text is the money's tooltip, with a footer hint saying to point at a Pokémon.
- 42.2: the daycares' party rows keep the slots and two-click flow they had (Leave then Confirm; Pick then Pair), so a
  click does exactly what the chest's did; only the button's word says which click it is.
- 42.2: the boarders and the party share one list with no heading between them (the rows' buttons, Collect or Leave,
  tell them apart), as the list has no section headings yet.

## Known limits

- The Move Tutor and the Cobblemon menus use it so far; the other chest menus move in 42.3 and 42.4.
- A daycare's boarders' levels and the keeper's eggs don't change on screen while it's open (they follow the party and
  the money only); reopening shows them.
- The rows are fixed columns; a very narrow window (a big GUI scale on a small screen) cuts the texts with "…".

## Proof

`TutorListMenuCompatTests.tutorListTeachesAMove`, `TutorListMenuCompatTests.tutorListTooLittleMoney`,
`TutorListMenuCompatTests.tutorListAlreadyKnownAndGrayRows`, `TutorListMenuCompatTests.tutorListFollowsThePartyWhileOpen`
(Cobblemon), `ChoiceListGameTests.listMenuOpensOnItsOwnTypeAndClicksStayChestClicks`,
`ChoiceListGameTests.listMarksTravelWithTheIcon`, `ChoiceListGameTests.listMenuChecksEveryTickWhileOpen` and
`QaEveryMenuGameTests.everyServerMenuAndItsClientCopyAgreeSlotForSlot`; 42.2:
`CobblemonListMenusCompatTests.professorListHintsAndStone`, `CobblemonListMenusCompatTests.professorListWithoutAParty`,
`CobblemonListMenusCompatTests.daycareListLeaveAndCollect`,
`CobblemonListMenusCompatTests.daycareKeeperListPairEggsAndTakeBack` (Cobblemon).
QA's `QaTutorListEdges1108eCompatTests` (4, Cobblemon: the party reordered between Teach and Confirm keeps the lesson
on its Pokémon and teaches it once; money spent between them turns the price red and Confirm teaches and takes nothing;
money paid in turns a red price green with no click; a move learned elsewhere before Confirm is not sold).

Showcase scenes: `tutor`, `professor`, `daycare`, `daycare_keeper`
