# Steward

A seasoned Builder who runs a village from its City Plan: he decides what the village needs next, finds the plot and
has the builders build it. Part of 1.1 (Villages that build themselves): switched on in 1.0 with every other expansion.

Roadmap items: 27.1, 27.1a, 27.2, 27.3, 27.5, 27.6, 27.7, 27.8, 27.9, 27.10, 27.11, 27.12, 27.19, 27.20, 27.21, 27.22, B328, B468, B469, B471, B472, B503, 36.20

## What a player sees

**The City Plan.** Craft one (a Map, a Blank Blueprint and a Heart of the Sea) and right-click a Village Hall to bind
it. Right-click the air to open the plan: the village map under a grid of 32 by 32 cells. You paint up to 16 zones,
each with a kind, a name, a building style and a "renew old houses" switch. Holding the plan shows the zone edges,
roads and wall line on the ground around you as colored dust that only you see.
A zone's name is 1 to 32 letters and its style is one the plan lists (or as drawn); the server refuses anything
else a changed game sends. A zone saved earlier with a style that no longer exists keeps its name and cells and is
built as drawn until you pick a style again.

| Zone | What goes there |
|---|---|
| Homes | cottages, stone houses, terraces, inns, home upgrades |
| Workshops | the workplace of each job (Smithy, Mason's Yard, Map Room...) |
| Farms | Berry Farm, Ranch, Farmstead, Fisher's Hut |
| Market | storehouses, market stalls, the Market Square |
| Civic | school, library, clinic, chapel, graveyard |
| Gardens | wells, benches, fountains, gazebos |
| Defenses | lookout towers, barracks |
| Keep Clear | nothing is ever built there (roads may cross) |

**The Steward.** Sneak-right-click a Builder of Journeyman level or higher, standing by the hall, with that hall's
City Plan. He becomes the Steward, one per hall, in a clerk's long coat. Each morning he walks his rounds with the
plan in his hands (his open builds, the zones, the storehouse), then plans at the hall until evening. The line over
his head says what and why: "Planning a Stone House: 3 villagers have no bed". A count of one reads in the singular:
"1 villager has no bed", "1 job here has no workplace of its trade" (B377), "The store is 1 meal short", "1 household
wants a grander home", "1 topic is open", and on the City Plan "1 cell is nearer another village" (B469, B471, B472).

**His desk.** The hall's "What next?" page becomes his desk. Three modes: Ask me first, Run the village, Rest. Up to
9 proposals, each saying what, why, where and what it needs, with Approve, Decline, Show me, Another spot and Another
style. In Run the village he approves them himself and tells you in one line a morning. He also gives jobless
villagers jobs and picks the scholars' next research. The hall's owner, their friends and operators may answer,
at the hall or from a bound Village Ledger; Show me outlines the spot for 30 seconds. A proposal starts one build
however often it is approved, and nothing starts once the Steward or the hall is gone. His mode and open proposals
are kept over a restart.

## How it works

**Rules, as data.** What the Steward wants is a set of rule files (`data/aliveworkplace/steward_rules/`, one rule per file).
Each has conditions that must all hold ("one bed short", "food short", "no guard"), one thing to do (build, upgrade,
assign jobs, research, or a tip only a player can act on), a priority, a reason, a rest period, and the lowest rank it
applies at. Each morning he ranks the rules that hold into at most 8 wishes. The conditions read the same numbers as
the hall's "What next?" tips, so the Steward and the tips never disagree. `/workplace steward explain` shows every
rule with each condition's number and whether it held.

**Finding a plot.** For a build wish he looks for a spot in the right zone, nearest the hall first, trying the
blueprint's four turns and its mirror image. A spot is good only if the front faces the nearest road (or the hall), the
footprint and a 2-block margin lie inside the zone and off the roads, the ground varies by at most 4 blocks, nothing
stands there but natural ground, trees and the plants of the `steward_clearable` tag (a carved pumpkin is not one), and a builder's bench stands in the village (anywhere in it then reaches, 36.20) or is within `maxSiteDistance` of the spot. At most a tenth of
the footprint may be over water and none of it over lava, it stays 2 blocks clear of every build site and finished
building, and the same blueprint with the same mirroring is never put within 24 blocks of another, center to center (exactly 24 counts
as within; a turned twin counts, a mirrored one doesn't).

**Open builds.** His level sets how many of his builds may be open at once (1, 2, 2, 3, 4 from Novice to Master),
never more than the village's rank allows (Hamlet 1 to City 4) nor more than `stewardMaxOpenBuilds`. An approved
proposal is an ordinary build site for the nearest builder, owned by the hall's owner.

**Jobs and research.** Each morning every grown jobless villager gets a free workstation for the village's biggest
gap: a builder while there is none, then a farmer while food is short, guards, a porter, a scholar, then the nearest
free block. When a scholar has nothing to research he picks the next topic by what the village lacks.

**Safe by design (27.19).** Everything he builds passes one check: only inside his own village, never in Keep Clear,
never in a protected village of another owner, never through a place where a player placed or broke blocks unless the
owner approved that one by hand (Keep Clear refuses even that). A road the owner drew on the City Plan counts as
approved by hand; a lane the Steward lays from a finished house's door and his half of a road to another village do
not, so a stretch of them that would run through a player's section waits instead (B520). A player placing or breaking a block inside a hall's
area marks its 16×16×16 section, and only that one. His sites clear only natural blocks. His waiting builds (not a player's own) share one
shopping list, the 8 most needed added up over all of them, on the desk, the Storehouse's requests board and in the
caravans' wants ("30 Glass, 12 Oak Logs, 1 Torch"); the owner is told it once a day. While two of his builds have waited a whole day (one, or two for
less, isn't enough) he proposes nothing new and the desk says so.

**Old houses (27.20, 27.21).** In zones with "renew old houses" on he finds the houses no builder built (by their
beds and job blocks), never one with a chest, never one a player changed, never one inside a finished build. A house
counts when 85% or more of its blocks are village blocks (mossy, cracked and cobwebbed ones too) and it is at most
2000 blocks and 20×16×20; beds, bells, signs, banners, campfires and job blocks may stay in it, any other container
(a furnace, a hopper) rules it out. Trees that grew against a house are not part of it: a trunk by the wall and the
leaves round the house's posts are left standing, and the house measures as its own blocks. The desk says "Old houses: 2, 1 can be renewed". One at a time, at most one every 2 days,
he proposes to renew one in the zone's style: builders take the old house down (its blocks go to the store) and
build the new one on the plot, the first building on the list that fits the old footprint plus 3, its front where
the old door was. A renewal list file that is broken, or a building it names that doesn't exist, is skipped. The sleepers get the new beds and the worker keeps the job. Two of them coming home
through the one front door together no longer jam on the doorstep: the one further from the door steps back a moment
and follows the other in, and the jam never counts towards vanilla giving a bed up (B255; this holds at any door, for
any villagers walking to bed). A jobless villager walking to a workstation it picked before dusk gives that walk up at
bedtime and goes to bed (B258).

## Switches

| Key | Default | What it does |
|---|---|---|
| `steward` | on from 1.0 | Off: no new Stewards, and those appointed stand idle (the plan still paints) |
| `stewardMaxOpenBuilds` | 4 (1 to 8) | The most builds a Steward may have open at once |
| `stewardSelfRun` | on from 1.0 | Off: "Run the village" asks first, like every other mode |
| `stewardRenewal` | on from 1.0 | Off: old houses are listed, never renewed |
| `maxSiteDistance` | 128 (16 to 256) | How far from a builder's bench outside the village a plot may be (a bench in the village reaches all of it) |
| `villageHallRadius` | 64 (16 to 160) | The plan covers this far; a cell is 4 by 4 blocks at 64 |

Roads and walls have their own switches: see [Roads and walls](roads-and-walls.md).

## Saved data

- On the hall: `plan` (zones, roads, the wall line and the Steward's mode; an older hall loads with an empty plan),
  `steward` (today's wishes and the day they were ranked; empty), `steward_desk` (proposals, declines and open
  builds; empty), the renewal in progress, and `stewardPlaceChecked`.
- On the Steward: the attachment `steward_round_day` (the day of his last finished round).
- Per dimension: `aliveworkplace_player_built`, the ledger of where players placed or broke blocks inside a hall's
  area. Older worlds start with an empty ledger.
- Not saved: the old-house survey (done again after a day or when the zones change) and the plot searches.

## Items, blocks, jobs, commands

- Item: City Plan (`aliveworkplace:city_plan`, `item.aliveworkplace.city_plan`).
- Job: Steward (`aliveworkplace:steward`, `entity.minecraft.villager.steward`), at the Village Hall.
- Zone kinds (`data/aliveworkplace/city_zones/`): `aliveworkplace:homes`, `aliveworkplace:workshops`,
  `aliveworkplace:farms`, `aliveworkplace:market`, `aliveworkplace:civic`, `aliveworkplace:gardens`,
  `aliveworkplace:defences`, `aliveworkplace:keep_clear`.
- Command: `/workplace steward explain`. Test and QA only: `/workplace city` (the city soak).

## Decisions

- 27.1a (owner, 2026-10-05): only a villager who has been a Builder for a while may be Steward. Decided as a Builder
  at Journeyman (level 3) or higher: about 350 blocks of steady work, and no new saved field. He starts as a Novice
  Steward. Stewards appointed before the rule keep their job.
- 27.1a (owner, 2026-10-05): the City Plan's recipe is harder (a Heart of the Sea added), so a city is something a
  player works toward.
- Design note `docs/design/M27.md`: a Steward is appointed with the plan; nobody takes the hall by himself. A new
  Steward starts in Ask me first.
- 27.19: the Steward never builds over what a player built; the ledger of player-built places is what he checks.
- B520 (bug lane, 2026-10-27): only roads the owner drew by hand may cross the ledger; the Steward's own lanes and
  caravan halves are held back by it like his other builds. A stretch held back stays unbuilt until the section is
  forgotten or the plan changes.
- 27.6: rules are data, one file each; a file with an unknown condition or a bad field is skipped with a warning
  that names the file and the field.

## Known limits

- One Steward per hall, and only a Builder at Journeyman or higher qualifies.
- Breaking the hall ends the job.
- He needs builders: he plans, they build. With no builder's bench within reach of a plot, no plot is found.
- Player-built places are known only from 1.1 on; what players built before is not in the ledger.
- A proposal nobody answers lapses after 3 days; a declined one stays away 3 days. The line names it
  in quotes: Declined: "Give 1 villager a job" won't be proposed again for 3 days.

## Proof

GameTests: `StewardGameTests` (12), `StewardRulesGameTests` (19), `StewardHomesRulesGameTests` (12),
`StewardCivicRulesGameTests` (20), `StewardDeskGameTests` (7), `StewardJobsGameTests` (11),
`StewardSafetyGameTests` (8), `QaLaneLedgerBugTests` (1, B520: his own lane waits at a player's section), `QaLaneLedger1031GameTests` (6, B520: a lane with only its tip in a player's section waits and one whose tip is a block short of it opens, the owner drawing the same line by hand builds through it, the lane still waits after save and reload, a caravan half waits too, even where it runs nearer the other village), `StewardRoundsQaGameTests` (2), `CityPlanGameTests` (7), `CityPlanScreenGameTests` (3),
`OldHousesGameTests` (7), `RenewalGameTests` (9), `DoorJamGameTests` (1), `AtticBedGameTests` (1), `CountWordsGameTests`
(2, B377: the workplace reason and the Storehouse stock line with one and with two), `QaStewardCivicBoundariesGameTests` (3, 27.12: a death 29 days back counts for the Graveyard and 30 days back not, 7
villagers are too few; a raid 6 days ago is within 7 and 7 days ago not; 2 children get no Schoolhouse), `QaStewardHomesBoundariesGameTests` (2, 27.10: no Inn for a Town 3 beds short or a Village 4 short;
the Storehouse grows at 22 of 27 slots, not 21, on to III and no further). `QaStewardDeskJobsGameTests` (16, 27.8 and 27.9: a decline and an unanswered proposal both end at exactly 3 days,
9 proposals and never a 10th, strangers answer nothing while a friend of the owner can, Approve all, Rest and
`stewardSelfRun` off, one morning line, guards only while short, a baby gets no job, the research order against the spec).
`QaCityPlanZoneChecks1031GameTests` (3, B468 and B503: every listed style goes on a new zone and an edited one; a
refused 33-letter name or made-up style leaves the zone, its undo step and its saved copy as they were, each refusal
read as the bad_zone sentence; a gone style stays only with the zone that had it), `QaCityPlanPaintGameTests` (7, 27.2 and 27.3: a dragged rectangle is one stroke and one undo, a deleted zone comes back
with its cells, strokes that change nothing take no undo step, a stranger's eraser and side panel are refused, the
first painter's claim is saved, a block's zone comes from the nearest hall, an 8x8 cell at radius 128),
`QaStewardRulesDataGameTests` (2, 27.6), `QaStewardPlotFitGameTests` (2, 27.7: the front faces the nearer road;
clearable plants), `QaStewardDeskModesGameTests` (10, 27.8: mode buttons, operator and friend approvals, approving
twice, Steward or hall gone, Run's morning line, save and reload, the desk's sentences, the Ledger, Show me's 30
seconds), `QaWorkplaceCopiesGameTests` (4, 27.11), `QaSteward1031GameTests` (10, 27.1a and 27.19: one XP short of Journeyman refused and the next XP let in, Expert and Master
qualify, a Steward moves halls, the recipe in any slots and with a lookalike, the half-way line to another hall, a stranger's
protected village by hand, negative ledger sections saved, exactly 8 kinds listed, only his builds pause or are told), `QaStewardSafetyGameTests` (10, 27.19: a broken block marks its section,
nothing outside a hall's area, one section only, per dimension, Keep Clear over the owner's approval, the 8 most
needed, caravans, once a day, the pause's day from both sides, the sentences), `QaShoppingPlurals1031GameTests` (2, B698: 1 Torch then 2 Torches on the daily message, desk and board, and plurals that aren't the name plus an s, 40 Hay Bales, 3 Bowls of Rabbit Stew, 1000 Lanterns), `QaOldHousesGameTests` (12, 27.20: 85%
from both sides, abandoned blocks, allowed block entities, containers, 2000 blocks and 20×16×20 from both sides, a
finished build, placed leaves, the ledger, the desk line) and `QaRenewalGameTests` (9, 27.21: the switch, two days
from both sides, a Fletcher's Lodge facing the old door, the sentences, a tight zone, broken lists, a building too big
passed over, a zone not renewed), `QaStewardSameRangeGameTests` (1, B470: Wells 25 and 24.04 blocks apart are not within
24) and `QaBuilderFixes1027bGameTests` (B470's two: 24 blocks off refused on all four sides and 25 not, diagonals 23.35
and 23.77 refused and 24.04 not, a turned twin still counts and a mirrored one doesn't; the even Market Stall and the
Hay Cart turned against an unturned one, half a block off on both axes, measured the same way).

Showcase scenes: `steward` (the morning rounds), `steward_rules`, `steward_desk`, `steward_safety`, `steward_jobs`,
`steward_homes`, `steward_civic`, `old_houses`, `renewal`, `city_plan`, `city_plan_ground`, `city_timelapse` (a
village from a plan, 6 days in Run the village).
