# The list screen

The menus that opened as a plain gray chest of buttons move, one by one, to a list drawn in the Village Hall's look:
the title and your money at the top, a row of tabs, a list of rows with a button on each, and a hint at the foot. The
Move Tutor's lessons were the first; the Pokémon Professor's hints, the Rancher's Daycare and the Daycare Keeper followed
(all Cobblemon), then the job menus: the Ball Smith's, Gem Grower's, Habitat Keeper's and Berry Breeder's, a traveler's
hire screen, a Legend guest's terms and the Research screen; then the builder tools and the shops: the Shape Planner, the
style picker, the Price Tag, and the Shop and the Ferry paid in CobbleDollars. Part of Milestone 42 (One look for every
screen).

Roadmap items: 42.1, 42.2, 42.3, 42.4, B1090

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

The job menus (42.3) follow, with their own rows:

- **The Ball Smith's orders** and **the Gem Grower's orders** (sneak-right-click with an empty hand): what they make or
  keep at the top right ("Makes any ball", "Keeps the picked beds"), then a row per ball or bed: "Making these" or "Not
  asked for", the level a ball needs (yellow) or what a bed is planted with, and **Pick**; a picked row is gold with
  **Unpick**.
- **The Habitat Keeper's lures**: her lure now at the top right, **Any snack** first, then a row per type and egg group
  with the berries that season it and **Choose** (her lure gold, "Chosen"); page arrows at the foot when there are more.
- **The Berry Breeder's book**: how many berries the village has found at the top right, her goal and the step she's on
  as the footer; **Clear** (her goal) and **Stop** (her plot) first, then a row per berry: "Found", "Not found yet" or
  "Her goal" (gold), the pair that makes it (yellow when both parents are found) and **Set goal**; page arrows.
- **A traveler at the inn** (right-click): your money at the top, their row (their level, how long they stay) and **Hire**
  with the price, red while you can't pay it; it follows your money while open.
- **A Legend guest's terms** (right-click): how long they stay at the top right, then four rows to read (who they are and
  their rarity, what they'd bring, how many of their wants are met, when they leave), every line in each row's tooltip.
- **The Research screen** (sneak-right-click a Scholar, or a Legend with a tree): what's being researched at the top
  right, the tabs (the scholars', each Legend's tree, the realm's in a capital), then a row per topic: its level, the next
  level's price ("Fully researched" once done) and **Research**; gold "Under way" while it's worked on, gray with no button
  while it's locked or done (the reason in its tooltip, and a click still says it). The Great Library's row and topics come
  after the scholars' own.

The builder tools and the shops (42.4) follow:

- **The Shape Planner** (right-click it): how many blocks the shape takes at the top right, then a **Shape** row with the
  seven shapes as small icon cells (the chosen one gold) and a **Block** row with the building blocks you carry as icon
  cells (gray "Carry some building blocks" when you have none). Then a row for the width, the height and the depth, each
  with **-** and **+** in it (shift-click for 5), the solid or hollow row (click it to switch), the size, and the **Draw**
  row with its gold **Draw** button. Pointing at a cell shows its name, as the chest did.
- **The style picker** (sneak-right-click the air with a blueprint): the blueprint's name at the top right, **Mirror**
  first (gold **Flip back** while it's mirrored), then a row per style with its icon and **Choose**; the style it's built
  in is gold, "Chosen". A Wonder Plan has one row saying a Wonder keeps its own blocks.
- **The Price Tag** (right-click it): the price at the top right ("No price yet"), the price's row, then one row per size
  of step (1,000, 100, 10 and 1) with its **-1000** and **+1000** (and so on) in it, and **Done**.
- **The Shop** (right-click a shopkeeper with an empty hand, with CobbleDollars): your money at the top, a row per lot in
  stock with how many are in stock, the price (green when you can pay it, red when not) and **Buy**; Buy turns the row
  gold with **Confirm**, and Confirm pays. "Nothing in stock right now" at the foot when the chests are empty.
- **The Ferry** (right-click a ferryman with an empty hand, with CobbleDollars): your money at the top, a row per travel
  post you know with its fare and **Buy**, then **Confirm**.

Both shops follow your money while they're open: money paid in turns a red price green with no click.

In all of them, pointing at the top right line shows the menu's own tooltip (what the job does, the price rules, the odds), and a
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

**Row order.** Rows are listed in slot order, unless a row carries an order (`ChoiceList.order(icon, n)`, 42.3): lower
first, slot order among equals. So a menu keeps its slots (and the tests and clicks that know them) while "Any snack",
"Clear" and "Stop" come first and the Great Library's rows after the scholars' topics.

**Shared pieces.** `TableStyle`'s list-menu section (a text button, a tab, the row's columns and headings, a row, the
footer hint, the money line) is what the next menus (42.2 to 42.4) and the Trainer screen use.

**Cells in a row (42.4).** A row may hold small cells (`ChoiceList.cells(row, slots...)`): other buttons drawn inside it
at its right, in place of its one button, each still its own slot and its own chest click. A cell's icon is marked
`ChoiceList.cell(icon, label, on)`: with a label it's a small text button (the Planner's **-** and **+**, the Price Tag's
steps, **Draw**), without one the icon itself in a framed cell (the Planner's shapes and blocks), gold when on. A cell is
never a row of its own. A row with no second line and no price has the room up to its button for its name.

## Switches

none

## Saved data

none (the screen keeps nothing; the lessons, prices and tutor ranks are the Move Tutor's as before)

## Items, blocks, jobs, commands

- `aliveworkplace:choice_list`: the list screen's menu type.
- New text (42.4): the planner's `screen.aliveworkplace.shapes.shape_row`, `.block_row`, `.block_none`, `.button_draw`,
  `.list_hint`; the styles' `screen.aliveworkplace.styles.button_choose`, `.button_mirror`, `.button_unmirror`,
  `.list_hint`; the tag's `screen.aliveworkplace.price_tag.steps`, `.list_hint`; the shop's
  `message.aliveworkplace.shop.menu.button_buy`, `.button_confirm`, `.list_hint`, `.column_goods`, `.column_stock`,
  `.column_price`; the ferry's `message.aliveworkplace.travel.list_hint`, `.column_destination`, `.column_fare`.
- New text (42.3): `screen.aliveworkplace.orders.column_order`, `.button_pick`, `.button_unpick`; the smith's
  `screen.aliveworkplace.ball_smith.mode_any`, `.mode_only`, `.list_hint`, `.column_ball`, `.column_level`; the grower's
  `screen.aliveworkplace.gem_grower.mode_every`, `.mode_only`, `.list_hint`, `.column_bed`, `.column_planted`; the lures'
  `message.aliveworkplace.habitat_keeper.lures.any_second`, `.button_choose`, `.button_chosen`, `.about`, `.list_hint`,
  `.column_lure`, `.column_berries`; the book's `message.aliveworkplace.berry_breeder.book.found_short`, `.lit_short`,
  `.unfound`, `.wild_short`, `.button_set`, `.button_goal`, `.button_clear`, `.button_stop`, `.column_berry`,
  `.column_book`, `.column_from`; `screen.aliveworkplace.inn.joins`, `.button_hire`; `screen.aliveworkplace.legend_terms.met`;
  the research's `screen.aliveworkplace.research.nothing_short`, `.list_hint`, `.column_topic`, `.column_level`,
  `.column_next`, `.button_research`, `.button_under_way`.
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

- 42.3: the Research screen is a list, not a grid of framed cells: the topics already sit in their tree's order (what
  a topic needs comes before it), and a row has room for the level, the price and the button that a cell didn't; what's
  locked is a gray row with the reason in its tooltip.
- 42.3: the job menus' top right line says what the job does now (what she makes, her lure, what's researched, the found
  count, the days left) where no money is spent; the inn's shows your money.
- 42.3: a Legend guest's terms stay four rows to read (no buttons, as the chest had none); each row's tooltip keeps every
  line (the ticks and crosses of the wants).

- 42.4: the Shape Planner keeps its shapes and blocks as icon cells inside two rows ("Shape", "Block") rather than a row
  each, as the item allowed: seven shapes and up to nine blocks would be sixteen rows for two choices. Each size is one
  row with its - and + as cells, the Price Tag's steps one row per size of step (- and + side by side), so every button
  keeps its slot and click.
- 42.4: the Shop and the Ferry follow the player's money while open (as the inn's hire screen), so a price turns green
  or red with no click.

## Known limits

- Every menu that opened as a plain chest uses it now (42.1 to 42.4).
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
QA's `QaCobblemonListEdges1109eCompatTests` (2, Cobblemon, 42.2: the party reordered while the Daycare's Leave waits
for Confirm keeps it on its Pokémon, a click on the row that moved into its place leaves nobody, and Confirm leaves
exactly that one; the Keeper's Pick stays on its Pokémon when another takes its place, and Pair leaves the two chosen).

42.3: `JobListMenusGameTests.gemGrowerOrdersAsAList`, `JobListMenusGameTests.innHireAsAList`,
`JobListMenusGameTests.legendTermsAsAList`, `JobListMenusGameTests.researchAsAList`;
`JobListMenusCompatTests.smithOrdersAsAList`, `JobListMenusCompatTests.berryBookAsAList`,
`JobListMenusCompatTests.habitatLuresAsAList` (Cobblemon).
QA's `QaJobListEdges1109eGameTests` (3, 42.3: one emerald short of a traveler's price Hire is red and a click takes
nothing, one more emerald turns it green while open and hires for all of it; Swift Hands at its top reads "Level 3 of 3",
"Fully researched" in green, gray with no button, and a click starts nothing; research paid for and half done reads
"Researching ...: 50%" and Under way after the hall is saved and loaded, and another topic is refused as busy).
QA's `QaJobLists1109cGameTests` (9, 42.3 without Cobblemon: the hire price turns red when money leaves while open and
Hire then takes nothing; exactly the price hires; a Gifted traveler's doubled price; a second player can't hire a
traveler already hired and their screen closes; a second Research click on a topic under way restarts nothing; Under
way survives a save; the Great Library's rows; a Legend tree's tab; every list line's text in American English).

42.4: `BuilderToolListsGameTests.theShapePlannerIsAList`, `BuilderToolListsGameTests.theStylePickerIsAList`,
`BuilderToolListsGameTests.thePriceTagIsAList`; `ShopListMenusCompatTests.theShopIsAList`,
`ShopListMenusCompatTests.theFerryIsAList` (CobbleDollars).
QA (lane B, 2026-11-09, 42.4): `QaBuilderToolLists1109bGameTests` (9): every builder tool sentence in American English
and the Price Tag's steps read "Steps of 1,000 / 100 / 10 / 1"; shape sizes stop at 1 and at 32; with no blocks the
Shape Planner says "no blocks" and refuses to draw; forged clicks (slots out of range, button-only rows, swap, throw
and clone clicks) change nothing; each of the 7 shapes is picked by its cell and an arch has no solid/hollow row; the
shape, style and price survive a save and reach the blueprint or the tag; the Style picker's first and last rows; the
screens stop answering once the item leaves the hand. Mutants: 6 of 6 caught.
QA (lane B, 2026-11-09 later, 42.4): 7 more in `QaBuilderToolLists1109bGameTests`: a 3x3x3 box counts 26 blocks
hollow and 27 solid, at the top and in the Draw sentence, and takes one Blank Blueprint (none in creative); the Block row
offers only building blocks carried, each once, at most 9 cells; the Shape Planner and the Price Tag work from the off
hand; choosing a style keeps the mirror, choosing the chosen one changes nothing and only one row is gold; a Wonder's
plans show one row and Mirror; the Price Tag stops at 1,000,000,000 and 1 then -1 reads "No price yet". Mutants: 3 of 3
caught.

Showcase scenes: `shapes`, `style_menu`, `shop`, `ferry_menu` (42.4, and the Price Tag in `counter`), `tutor`, `professor`, `daycare`, `daycare_keeper`, `smith_orders`, `gem_orders`, `habitat_lures` (and the
list in `scholar`'s research still, `innkeeper`'s hire still, `berry_breeder`'s book still and `legend_guest`'s terms)
