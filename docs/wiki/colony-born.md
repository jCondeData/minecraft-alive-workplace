# A colony is born

ROADMAP 33.10 (part of 1.7, "From village to realm"; switched on in 1.0 with every other expansion). The third step of founding a colony:
the settlers [sent with the charter](colony-settlers.md) reach the spot, make camp, raise a Village Hall and a Travel
Post, and their builder starts the colony's first two buildings.

Roadmap items: 33.10, 33.14, B424

## What a player sees

When the journey is over, the settlers make camp on the nearest open ground within 48 blocks of the spot: the
**Colony Camp** (33.14, `camp/colony_camp`, 15 x 6 x 13: two covered wagons, a timber platform under a striped canvas
awning, a Blueprint Table with a lamp, supply crates round the chest, a campfire with log seats and a cooking pot,
three bedrolls and a flagpole with a white banner), turned to face the mother village. The colony's **Village Hall**
stands on the platform under the awning and the **Travel Post** at the front right by its lantern (template spots
(7, 1, 7) and (10, 0, 3), `ColonyArrival.HALL_IN_CAMP`/`POST_IN_CAMP`). Without the Colony Camp's template (a data pack
removed it) the settlers fall back on the Settler's Wagon's camp with the hall and post two blocks in front of it.
Every chest and barrel in either camp opens where it stands (B424): the Settler's Wagon's supply chest sits on its
wagon's open tailboard, past the wool cover so nothing on top keeps its lid shut (B590), and each Colony Camp wagon's
barrel at its tail, with two steps up to the wagon's bed.
Renders, as drawn and in the five styles: `docs/media/colony-camp-front.png`, `-back.png`.

- The hall carries the name the charter was given in an anvil (or a made-up one), belongs to the mother village's
  owner, and its chronicle opens with "Founded on day 41 by settlers from Thornholm: Dara and Tomas". The mother's
  chronicle says "Our settlers Dara and Tomas founded Newbrook, 612 blocks north-east".
- The post is on the travel network under the colony's name, already known to the owner and their friends.
- The settlers step out as they were: names, jobs, levels, trades and partners. The builder works at the camp's
  Blueprint Table; the others keep their trade until the colony has a workstation for it.
- The builder starts at once on the **Starter Cottage** and then the **Storehouse**, in the style most of the mother
  village's finished buildings use (Sandstone, say), one on either side of the camp where the ground is level and dry.
  Where it isn't, that blueprint waits in the camp's chest. The chest holds the supplies the settlers brought.
- Mother and colony are **sister villages**: a caravan route both ways that is always on, carries what the other
  waits for free (nothing for sale) and doesn't count toward the village's route limit. On the Routes tab the colony
  reads "Sister village: caravans both ways, free, outside the limit"; clicking it says the route stays open.
- The owner gets a letter ("We've arrived at Newbrook, 612 blocks north-east of Thornholm, and made camp by the new
  hall and Travel Post. Tomas is building the Starter Cottage (Sandstone) and Storehouse Shed (Sandstone)...") and,
  if online, a chat line.

**No room.** If there's no open ground within 48 blocks (all water, a cliff), nothing is built. The Colonies tab says
"No room for Newbrook: the settlers are coming home", and when the mother village is next loaded the settlers are
back by its hall, the supplies in its Storehouses and the charter's 32 emeralds in its treasury; its chronicle notes
it and the owner hears it in chat. No colony is counted, so no wait before the next one.

## How it works

- `colony/ColonyArrival`, called by `Settlers.tick` for orders `on_road`, `arriving` and `returning`.
- When `arrives` comes, the order turns `arriving` and a chunk ticket (radius 6, like `KeepLoaded`'s) loads the
  spot's chunks for a minute. It is asked again every second while arriving, since tickets aren't saved.
- Once the spot is in ticking chunks (or after 30 seconds, with what has loaded) the settlers look for the camp: every
  third block within 48 of the spot, nearest first. A place counts if the ground there isn't water, the camp's box is
  as open and as level as the Settler's Wagon asks (`SettlersWagonItem.roomFor`), nothing wet is in or under it, and
  (with the old camp only) the hall's and post's spots have ground within a block. The ground is found from the height map, past trees.
- The mother's hall chunk is loaded once (one chunk) to read its owner, name and chronicle.
- Builds: the cottage tries one side first, the storehouse the other, each 2, 4 then 7 blocks from the camp, its front
  in line with the camp's. A footprint is level enough when at most 15% of its columns are more than 2 blocks above or
  below where it stands, and none is water. The style is the first of `Founder.styles` (the mother's finished builds,
  most used first) the blueprint comes in.
- The builder is employed and the builds started one tick later (the table becomes a workstation then). Each
  blueprint started comes out of the chest; one that couldn't be placed stays in it.
- The letter goes to the owner's mailbox if they have one, otherwise into their hands if online, otherwise it waits
  at any Postal Desk.
- A colony without a Storehouse yet unloads its sister's caravans into the chests by its hall (the camp's chest).

## Switches

| Key | Default | What it does |
|---|---|---|
| `colonies` | on from 1.0 | Off: no charters or tab. An order already on the road still arrives (or comes home), so nobody is lost. |

## Saved data

- `colonies.orders` in `aliveworkplace_realms`: `state` also takes `arriving` (then `arrives` is when they got there)
  and `returning`. Older files have neither.
- `colonies.founded` gets the colony (mother, hall, day), and the mother's count and last colony day go up.
- `sisters` in `aliveworkplace_caravans` (each dimension): pairs of halls `a` and `b`, both ways. Empty in older saves.
- The hall, post, camp, villagers and build sites are saved as any other.

## Items, blocks, jobs, commands

- No new items or blocks. The camp is `aliveworkplace:camp/settlers_camp`; the hall `aliveworkplace:village_hall`,
  the post `aliveworkplace:travel_post`.

## Decisions

- The camp faces the mother village, so the hall's front looks home (33.10, the lane's choice).
- The chest's loot table (which would add a second hall and other blueprints) is emptied: the settlers bring their
  own supplies.
- The cost is refunded to the mother's treasury, all of it, even the part the player paid (the order keeps only the
  total).
- Sister routes carry no goods for sale, so "free" holds both ways; turning one off isn't possible.
- The arrival happens with `colonies` off, as 33.8's design note says: an order on the road arrives anyway.

## Known limits

- If the server stops in the one tick between the camp and the builder's start, the builder has no table or builds
  until he takes the table by himself, and both blueprints wait in the chest (not tested).
- Seen in the real client only through the showcase scene, which GitHub films; it was not filmed locally.
- The arrival GameTests run in a 44-block area (`colony_area`): the camp with a build either side is 35 blocks
  across, too wide for `huge_area`.

## Proof

- `ColonyArrivalGameTests` (3): `settlersArriveAndFoundTheColony` (camp, hall named and owned, post known to the owner
  and a friend, Tomas still a builder and Dara still a farmer, two Sandstone sites either side, the sister route
  outside the cap and on the Routes tab, a free caravan, both chronicles, the letter),
  `withNoRoomEveryoneAndEverythingComesHome` (a lake: settlers, supplies and the 32 emeralds back home),
  `anArrivalSurvivesAReloadAndTheSwitch` (mid-arrival reload, with colonies off).
- `QaCampReachGameTests` (3, B424): `theCampsChestOfSuppliesCanBeOpened` (the game's own ray pick from every spot a
  player fits within 5 blocks), `everyChestAndBarrelInTheColonyCampCanBeOpened`, `theSettlersCampTurnedRoundStillOpens`;
  `QaBuildReachGameTests` walks to both camps' containers too.
- `QaBuilderFixes1027cGameTests` (3, B424, B590): `theCampChestOpensForAPlayerAndHoldsTheSupplies` (a player's click
  opens the chest's menu with the Village Hall and two blueprints; nothing on the chest blocks its lid),
  `theColonyCampTurnedClockwiseStillOpens`, `theColonyCampTurnedCounterClockwiseStillOpens` (every chest and barrel of
  the Colony camp turned a quarter either way).

Showcase scenes: `colony`
