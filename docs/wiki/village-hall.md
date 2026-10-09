# The Village Hall

The block that turns a group of villagers into a village the mod looks after: its people, food, beds, rank, calendar,
treasury and pages. Most other systems (edicts, guilds, Legends, classes, the Steward) hang off a hall.

Roadmap items: 22.5, 22.6, 30.4a, B37

## What a player sees

You craft a Village Hall and put it in the middle of your village. Right-click it and a screen of its own opens (drawn
like vanilla's workstation screens, not a chest): a top row with the village's numbers (name and rank, villagers, beds,
food in store, guards, wellbeing, requests, buildings going up), a row of page tabs, and below that everyone who lives
within 64 blocks. Each worker is shown as their workstation, stacked as high as their level, with what they are doing
and waiting for. Click one and they glow for ten seconds so you can find them. Click a jobless villager to see the free
workstations and give them one.

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
villagers nearest the free bed have a baby (the family eats 8 meals), up to `villageGrowthCap`.

**Treasury.** Each morning every worker puts by `treasuryPerWorker` hundredths of an emerald (20: a fifth of an
emerald), half in a badly kept village, half again in a well kept one, a quarter more per rank. Whoever opens the hall
collects it. It holds up to a stack of emeralds a rank and counts at most 3 missed days.

**Calendar.** Four seasons of `seasonDays` days each (16), the same for the whole world, counted from the overworld's
day. Each season has a festival on its middle day. Festivals also come every 8 days where at least 6 villagers live.

**Protection.** The hall's owner (whoever placed it) can switch protection on by shift-clicking the hall's name
icon. Then only the owner, their friends and operators may break or place blocks, open chests or hurt villagers in the
hall's area. Anyone may still walk in, open doors, trade and ring the bell.

**Page tabs (22.5).** The third row holds 9 tabs. Any feature adds a page with one registration call
(`docs/agent/layout.md`); the mod's own later pages give way to pages added by others when the row is full.

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
  and `steward_desk`, `services`, `piece_looks`, `bannerBase` and `bannerPatterns` (the village's colours).

The calendar keeps the last day it fired for with the overworld. Friends are saved in `aliveworkplace_friends`.
Not saved: the census (counted again every 600 ticks) and the screen's layout.

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
- 22.5: page tabs instead of more buttons, because five expansions each needed a page and the screen had no free
  slot. Every button that was there before stayed where players knew it.
- 22.6: one calendar for the whole world, so the Festival Cup, harvest season and stories agree on the date. The
  seasons grew from 8 to 16 days (owner, 2026-10-04); a config written before that reads 8 as "the old default".
- B37: the name icon's tooltip only offers "protect the village" to the player who may do it.
- Villages keep working with no player near (23.6, owner's call): see `keepVillagesWorking` on
  [Config switches](config-switches.md).

## Known limits

- Halls are meant to stand apart. Where two halls' circles overlap, the City Plan and the Steward keep to the nearer
  hall's side; how the head count splits shared villagers was not checked for this page.
- The hall's numbers are up to 600 ticks (30 seconds) old.
- Nothing in the mod changes with the seasons except festivals, the Festival Cup and the Harvest Idol.
- A hall placed by a machine has no owner until a player turns its protection on.

## Proof

GameTests: `VillageHallGameTests` (23 tests: the census, the name tag, eating from the store, pace, growth),
`HallUiGameTests`, `HallPagesGameTests`, `QaHallPagesSeasonsGameTests`, `HallSpecGameTests`, `ProtectionGameTests`,
`ProtectionSpecGameTests`, `HallProtectHintGameTests`.

Showcase scenes: `hall` (the screen and calendar), `hall_pages` (chronicle and trade routes), `hall_quests`,
`hall_treasury` (treasury, protection and the ledger).
