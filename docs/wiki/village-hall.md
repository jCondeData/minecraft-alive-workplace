# The Village Hall

The block that turns a group of villagers into a village the mod looks after: its people, food, beds, rank, calendar,
treasury and pages. Most other systems (edicts, guilds, Legends, classes, the Steward) hang off a hall.

Roadmap items: 22.5, 22.6, 30.4a, 26.12, B37, 36.5, 36.9, 36.9a

## What a player sees

You craft a Village Hall and put it in the middle of your village. Right-click it and a screen of its own opens, drawn
like the Blueprint Table's (not a chest): under the gold title a strip of icon buttons, the village's numbers (name
and rank, villagers, beds, food in store, guards, wellbeing, requests, buildings going up, quests) and the toolbar
(map, the Book of Edicts, the bell, mercenaries, the Chronicle, a festival, trade, "What next?", the bank). On the
left a list: the page tabs, then everyone who lives within 64 blocks, each as their workstation (stacked as high as
their level) with their name and job. The pane on the right reads out whatever is under the mouse, every line of it;
with nothing there it shows the village's numbers "at a glance". Click a villager and they glow for ten seconds so you
can find them, and stay in the pane (the wheel scrolls a long one; × or Esc puts them away). Click a jobless villager
to see the free workstations and give them one. Every page of the hall opens in the same screen.

**Reading without clicking (36.5).** A row or cell hovered stays read out while the mouse moves on into the pane
(it changes only when another row or cell is hovered, and lets go once the mouse leaves the panel, when the pane goes
back to the button last clicked), so the wheel there scrolls a long entry with nothing clicked; Shift and the wheel
over a row scroll that row's reading and leave the list where it is. A small arrow at the pane's foot shows while
there's more below, and a name too long for its two lines (a long chronicle entry) carries on at the top of the
reading. The footer says "Hover to read · scroll in the pane or with Shift · click to use". A click in the pane runs
nothing. The rule lives in `work/ReadingFocus` (no drawing); the Storehouse board shares the screen and the rule.

**Free job blocks (36.9).** A tab with a crafting table opens the "Free job blocks" page: every job block in the
village nobody works at, nearest the hall first. Each shows the block, the job it gives, the building it stands in
("In the Storehouse", or "Not in a building"), how far and which way from the hall, and how many villagers without a
job could take it. Click a block and its page lists those villagers; click one and they get the block: they claim it
and walk over to it. So an empty Storehouse or a lectern left in a finished home can be filled from the hall, without
leading anyone into the house. A villager who already works isn't listed; they are retrained at the block itself, for
a price (26.6, see [Villager jobs](villager-jobs.md)).

The buttons open the hall's pages: Quests (a journal), the Chronicle (what happened, day by day), trade routes
(caravans to other halls), "What next?" (advice, or the Steward's desk when there is a Steward), the village map, the
Book of Edicts, the calendar, Legends, the Festival Cup and Classes. The bell calls everyone home. A cake calls a
festival. A named Name Tag used on the hall names the village (the tag isn't used up); otherwise it gets a made-up
name such as Thornholm.

## How it works

**The village is a circle.** Everyone and everything within `villageHallRadius` blocks of the hall (64 by default, 32
up and down) belongs to it. Once every 600 ticks the hall counts its people, beds, food, guards and light; that count
feeds everything else.

**Needs set the pace.** Every grown villager eats once a day from the store (chests by the kitchens first, then by
the Storehouses), wants a bed of their own, one guard for every ten villagers and light by the beds. How well that is
met is the village's wellbeing, 0 to 100%. Work goes up to 25% faster in a well-kept village, at the usual pace at
50%, and up to 20% slower in a badly kept one. Villages without a hall work at the usual pace.

**Ranks.** Hamlet, then Village (10 villagers and 5 finished buildings), Town (20, 12, and 3 research levels), City
(35, 25 and 7). A rank up means fireworks, a chronicle line, more caravans, more market traders, quests that pay a
quarter more, and room for ten more villagers. Roads never count as buildings and a whole wall counts as one.

**Growth.** At most once a day, with a free bed, 16 meals in the store and wellbeing of 50% or more, the two
villagers nearest the free bed have a baby (the family eats 8 meals), up to `villageGrowthCap`. The baby takes that
bed at once (B206), so a second birth, an inn's traveller or vanilla breeding can't count the same bed free: a village
with N free beds takes in at most N newcomers. With no bed free the Beds button says **The village is full: nobody new
moves in until a bed is free**.

**Wagon beds are no homes** (B206, B207, `camp/CampBeds`). The bedrolls of a Settler's Wagon's camp or a Colony Camp
are listed when the camp is placed (`aliveworkplace_camp_beds`, per dimension); an older camp's are known by their
look (a brown or green bed with a campfire and a hay bale within 10 blocks) and listed once seen. At each round the
hall moves out anyone homed in one (they wake and forget it, then find a bed in a house) and holds every wagon bed for
nobody, so it is never claimed again. The census leaves them out of Beds and Free, and the Beds button says how many
it left out ("Not counted: 2 wagon beds"). Before a hall stands, the settlers sleep in the wagon as before.

**Treasury.** Each morning every worker puts by `treasuryPerWorker` hundredths of an emerald (20: a fifth of an
emerald), half in a badly kept village, half again in a well kept one, a quarter more per rank. Whoever opens the hall
collects it. It holds up to a stack of emeralds a rank and counts at most 3 missed days.

**Calendar.** Four seasons of `seasonDays` days each (16), the same for the whole world, counted from the overworld's
day. Each season has a festival on its middle day. Festivals also come every 8 days where at least 6 villagers live.

**Protection.** The hall's owner (whoever placed it) can switch protection on by shift-clicking the hall's name
icon. Then only the owner, their friends and operators may break or place blocks, open chests or hurt villagers in the
hall's area. Anyone may still walk in, open doors, trade and ring the bell.

**Page tabs (22.5).** The third row holds 9 tabs. Any feature adds a page with one registration call
(`docs/agent/layout.md`); the mod's own later pages (Classes, Wonders, Free job blocks) give way to pages added by
others when the row is full, newest first.

**Free job blocks (36.9).** The page is read when it's drawn: the village's points of interest that give a job and
have room (the same list a jobless villager's own page shows), each matched to the building whose outline holds it
(finished builds, and builds going up). Who could take a block: a grown villager in the village without a job, not a
nitwit or a Legend. One who has never worked takes any block; one who still has an old job's trades but lost the block
is offered only a block of that same job, because changing job is paid retraining (26.6). A job above a villager's
class (34.8) is greyed with the class it needs. The pick is checked again on the click: if someone took the block
meanwhile, or the villager found work, nothing changes, the action bar says why and the list comes back. The page
shows up to 36 blocks; past that, the last slot says how many more there are further away.

## Switches

| Key | Default | What it does |
|---|---|---|
| `villageHallRadius` | 64 (16 to 160) | How far from a hall its village reaches |
| `villageGrowthCap` | 40 (0 to 500) | The village stops having babies at this many villagers; 0: no growth |
| `villageTreasury` | on | Villages put by takings every morning |
| `treasuryPerWorker` | 20 (0 to 500) | Hundredths of an emerald a worker a day |
| `villageProtection` | on | Owners may protect their village; off: nobody can, on the whole server |
| `villageQuests` | on | Halls post quests; off: no new ones, open ones can still be finished |
| `festivals` | on | Regular festivals; off: only ones a player calls with a cake |
| `marketDays` | on | A weekly market in a village with a Market Square |
| `seasonDays` | 16 (1 to 120) | Days in each of the four seasons |
| `villagerNames` | on | Villagers in a hall's village get names |
| `villagerMoods` | on | Moods that change how fast villagers work |
| `villagerSickness` | on | Villagers fall ill now and then |

## Saved data

On the hall block itself (`VillageHallBlockEntity`). A field missing from an older save reads as zero, false or
empty unless a default is given here:

- Identity: `CustomName` (none: a made-up name), `owner` and `ownerName` (none), `protected` (false), `rank`.
- People: `births`, `lastBirth`, `chronicle` (at most 100 entries, the oldest forgotten).
- Money and days: `treasury`, `treasuryTotal`, `lastTaxDay` (-1: never), `lastQuestDay` (-1), `lastMarketDay` (-1),
  `lastRaidDay` (-100), `festivalDay` (-1), `feastDay` (-1), `festivalCalled` (-100), `festivalMissed` (-1).
- Pages: `quests` and `questsDone`, `research`, `edicts`, `reforms`, `guilds`, `plan` (the City Plan), `steward`
  and `steward_desk`, `services`, `piece_looks`, `bannerBase` and `bannerPatterns` (the village's colors).

The calendar keeps the last day it fired for with the overworld. Friends are saved in `aliveworkplace_friends`.
Not saved: the census (counted again every 600 ticks) and the screen's layout. The Free job blocks page saves
nothing of its own: a block given from it is the villager's job like any other (their job-site memory and the block's
point of interest, saved by Minecraft).

## Items, blocks, jobs, commands

- Block: Village Hall (`aliveworkplace:village_hall`, `block.aliveworkplace.village_hall`). Recipe: gold, a book and
  gold over planks, with an emerald in the middle.
- Item: Village Ledger (`aliveworkplace:village_ledger`): right-click a hall to bind it, then open the hall's screen
  from anywhere nearby; sneak-use opens the Book of Edicts.
- Jobs at the hall: the Steward (see [Steward](steward.md)).
- Commands: `/workplace quests`, `/workplace friend add|remove|list`.

## Decisions

- 30.4a (owner, 2026-10-05): the hall's screen is a custom UI like vanilla's workstation screens, not a chest of
  icons. The server lays out the buttons and decides every click; the client only draws.
- 26.12 (owner, 2026-10-16: "i like the look of the blueprint table"): the hall and the Storehouse board are drawn in
  the Table's style by one client screen (`ChoiceTableScreen`, the look shared through `TableStyle`). The server's
  menu, buttons and clicks did not change: the top row and the toolbar row are the strip, the rows below are the list,
  and a click is the chest click it always was (left, right, shift). Tooltips became the pane on the right, so long ones
  no longer cover the screen.
- 22.5: page tabs instead of more buttons, because five expansions each needed a page and the screen had no free
  slot. Every button that was there before stayed where players knew it.
- 22.6: one calendar for the whole world, so the Festival Cup, harvest season and stories agree on the date. The
  seasons grew from 8 to 16 days (owner, 2026-10-04); a config written before that reads 8 as "the old default".
- 36.9 (owner, 2026-10-18): filling empty job blocks moved to the hall, block first, because retraining at a block
  meant walking a villager into a finished home. Paid retraining of a working villager (26.6) stays at the block.
  The page is a spare tab (`HallPages.SPARES` went from 2 to 3), so the six tabs kept for other mods stay free. The
  tab is a vanilla crafting table, no new art.
- B37: the name icon's tooltip only offers "protect the village" to the player who may do it.
- Villages keep working with no player near (23.6, owner's call): see `keepVillagesWorking` on
  [Config switches](config-switches.md).

## Known limits

- Halls are meant to stand apart. Where two halls' circles overlap, the City Plan and the Steward keep to the nearer
  hall's side; how the head count splits shared villagers was not checked for this page.
- The hall's numbers are up to 600 ticks (30 seconds) old.
- Nothing in the mod changes with the seasons except festivals, the Festival Cup and the Harvest Idol.
- A hall placed by a machine has no owner until a player turns its protection on.
- Free job blocks: a villager given a block walks there with the usual brain; at night or in a festival their
  schedule may take them elsewhere first. Blocks past the 36th aren't clickable until nearer ones fill. Only grown
  villagers in the hall's circle are offered.

## Proof

GameTests: `VillageHallGameTests` (23 tests: the census, the name tag, eating from the store, pace, growth),
`WagonBedsGameTests` (B206, B207: settlers leave the wagon once the hall stands, an older camp's bedroll is mended,
two free beds take in two newcomers and the hall says it's full, the inn takes a traveller only into a free house bed,
the wagon-bed list and its save),
`HallUiGameTests` (the hall and the Storehouse board on their own screens), `HallPagesGameTests`, `QaHallPagesSeasonsGameTests`, `HallSpecGameTests`, `ProtectionGameTests`,
`ProtectionSpecGameTests`, `HallProtectHintGameTests`, `ReadingFocusGameTests` (36.5: hovered, held on the way into
the pane, let go outside the panel for the pinned button; unpinning and page changes; the wheel and Shift),
`FreeJobsPageGameTests` (36.9: the listing with job, building and distance, `aPickTakesTheFreeBlock`, a block claimed
meanwhile refused, a villager who found work meanwhile left alone, `aGivenBlockSurvivesAReload`).

Showcase scenes: `hall` (the screen, a villager read out and kept in the pane, the calendar, GUI scales 3 and 4), `hall_pages` (chronicle and trade routes), `hall_quests`,
`hall_treasury` (treasury, protection and the ledger), `hall_reading` (a long chronicle entry read and scrolled to its end
from the pane without a click, then the Storehouse board), `hall_jobs` (the Free job blocks page, the Storehouse's
takers, Dara picked as its Porter).
