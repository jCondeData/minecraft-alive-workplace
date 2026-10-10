# Steward

A seasoned Builder who runs a village from its City Plan: he decides what the village needs next, finds the plot and
has the builders build it. Part of 1.1 (Villages that build themselves): off until that expansion is finished.

Roadmap items: 27.1, 27.1a, 27.2, 27.3, 27.5, 27.6, 27.8, 27.9, 27.10, 27.11, 27.12, 27.19, 27.20, 27.21, 27.22

## What a player sees

**The City Plan.** Craft one (a Map, a Blank Blueprint and a Heart of the Sea) and right-click a Village Hall to bind
it. Right-click the air to open the plan: the village map under a grid of 32 by 32 cells. You paint up to 16 zones,
each with a kind, a name, a building style and a "renew old houses" switch. Holding the plan shows the zone edges,
roads and wall line on the ground around you as coloured dust that only you see.

| Zone | What goes there |
|---|---|
| Homes | cottages, stone houses, terraces, inns, home upgrades |
| Workshops | the workplace of each job (Smithy, Mason's Yard, Map Room...) |
| Farms | Berry Farm, Ranch, Farmstead, Fisher's Hut |
| Market | storehouses, market stalls, the Market Square |
| Civic | school, library, clinic, chapel, graveyard |
| Gardens | wells, benches, fountains, gazebos |
| Defences | lookout towers, barracks |
| Keep Clear | nothing is ever built there (roads may cross) |

**The Steward.** Sneak-right-click a Builder of Journeyman level or higher, standing by the hall, with that hall's
City Plan. He becomes the Steward, one per hall, in a clerk's long coat. Each morning he walks his rounds with the
plan in his hands (his open builds, the zones, the storehouse), then plans at the hall until evening. The line over
his head says what and why: "Planning a Stone House: 3 villagers have no bed".

**His desk.** The hall's "What next?" page becomes his desk. Three modes: Ask me first, Run the village, Rest. Up to
9 proposals, each saying what, why, where and what it needs, with Approve, Decline, Show me, Another spot and Another
style. In Run the village he approves them himself and tells you in one line a morning. He also gives jobless
villagers jobs and picks the scholars' next research.

## How it works

**Rules, as data.** What the Steward wants is a set of rule files (`data/aliveworkplace/steward_rules/`, one rule per file).
Each has conditions that must all hold ("one bed short", "food short", "no guard"), one thing to do (build, upgrade,
assign jobs, research, or a tip only a player can act on), a priority, a reason, a rest period, and the lowest rank it
applies at. Each morning he ranks the rules that hold into at most 8 wishes. The conditions read the same numbers as
the hall's "What next?" tips, so the Steward and the tips never disagree. `/workplace steward explain` shows every
rule with each condition's number and whether it held.

**Finding a plot.** For a build wish he looks for a spot in the right zone, nearest the hall first, trying the
blueprint's four turns and its mirror image. A spot is good only if the front faces a road (or the hall), the
footprint and a 2-block margin lie inside the zone and off the roads, the ground varies by at most 4 blocks, nothing
stands there but natural ground and trees, and a builder's bench is within `maxSiteDistance`.

**Open builds.** His level sets how many of his builds may be open at once (1, 2, 2, 3, 4 from Novice to Master),
never more than the village's rank allows (Hamlet 1 to City 4) nor more than `stewardMaxOpenBuilds`. An approved
proposal is an ordinary build site for the nearest builder, owned by the hall's owner.

**Jobs and research.** Each morning every grown jobless villager gets a free workstation for the village's biggest
gap: a builder while there is none, then a farmer while food is short, guards, a porter, a scholar, then the nearest
free block. When a scholar has nothing to research he picks the next topic by what the village lacks.

**Safe by design (27.19).** Everything he builds passes one check: only inside his own village, never in Keep Clear,
never in a protected village of another owner, never through a place where a player placed or broke blocks unless the
owner approved that one by hand. His sites clear only natural blocks. While two of his builds have waited a whole day
for materials he proposes nothing new and tells the owner the shopping list instead.

**Old houses (27.20, 27.21).** In zones with "renew old houses" on he finds the houses no builder built (by their
beds and job blocks), never one with a chest, never one a player changed. One at a time, at most one every 2 days,
he proposes to renew one in the zone's style: builders take the old house down (its blocks go to the store) and
build the new one on the plot. The sleepers get the new beds and the worker keeps the job.

## Switches

| Key | Default | What it does |
|---|---|---|
| `steward` | on from 1.1 | Off: no new Stewards, and those appointed stand idle (the plan still paints) |
| `stewardMaxOpenBuilds` | 4 (1 to 8) | The most builds a Steward may have open at once |
| `stewardSelfRun` | on from 1.1 | Off: "Run the village" asks first, like every other mode |
| `stewardRenewal` | on from 1.1 | Off: old houses are listed, never renewed |
| `maxSiteDistance` | 48 (16 to 256) | How far from a builder's bench a plot may be |
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
- 27.6: rules are data, one file each; a file with an unknown condition or a bad field is skipped with a warning
  that names the file and the field.

## Known limits

- One Steward per hall, and only a Builder at Journeyman or higher qualifies.
- Breaking the hall ends the job.
- He needs builders: he plans, they build. With no builder's bench within reach of a plot, no plot is found.
- Player-built places are known only from 1.1 on; what players built before is not in the ledger.
- A proposal nobody answers lapses after 3 days; a declined one stays away 3 days.

## Proof

GameTests: `StewardGameTests` (12), `StewardRulesGameTests` (19), `StewardHomesRulesGameTests` (12),
`StewardCivicRulesGameTests` (20), `StewardDeskGameTests` (7), `StewardJobsGameTests` (10),
`StewardSafetyGameTests` (7), `StewardRoundsQaGameTests` (2), `CityPlanGameTests` (7), `CityPlanScreenGameTests` (3),
`OldHousesGameTests` (7), `RenewalGameTests` (9).

Showcase scenes: `steward` (the morning rounds), `steward_rules`, `steward_desk`, `steward_safety`, `steward_jobs`,
`steward_homes`, `steward_civic`, `old_houses`, `renewal`, `city_plan`, `city_plan_ground`, `city_timelapse` (a
village from a plan, 6 days in Run the village).
