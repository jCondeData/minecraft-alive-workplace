# More decorations

Fourteen new small builds that aren't houses, to sit beside the Well, Street Lamp and Fountain: a player can dress a
village with more than lamps and benches (the owner, 2026-10-16: "there should be a wider variety of builds that arent
houses, but decorations similar to fountain or lamp").

Roadmap items: 26.14, 36.4

## 1. What a player sees

| Build | Size (w x h x d) | Beauty | What it is |
|---|---|---|---|
| Market Cross | 5 x 8 x 5 | 2 | a stepped stone plinth, a shaft of stone brick wall to a chiseled head, dark oak arms with a lantern hanging from each, one on top |
| Stag Statue | 7 x 8 x 5 | 2 | a stag of polished andesite with spreading antlers on a stone brick plinth, a bush and a fern at its feet |
| Flower Planters | 7 x 3 x 3 | 1 | a two-tier brick planter of flowers in their own soil between two lanterns |
| Signpost | 3 x 5 x 3 | 1 | a spruce post on a cobbled foot, four fingerboards at two heights that name what lies each way (36.4), a lantern on top |
| Stone Archway | 7 x 7 x 3 | 2 | mossy stone brick piers, an arch of stairs to a chiseled keystone, ivy, a hanging lantern, lilies at its feet |
| Lily Pond | 9 x 3 x 7 | 2 | an oval of water in a low mossy kerb with lily pads, a grassy bank of ferns and flowers, a stone seat |
| Hay Cart | 6 x 4 x 5 | 1 | a spruce cart on dark oak wheels loaded with bales, its shafts down, a lantern on the corner |
| Notice Board | 5 x 5 x 3 | 1 | a spruce board between dark oak posts under a little gabled roof, notices pinned to it, lanterns either side |
| Lantern Pergola | 5 x 6 x 7 | 2 | a pergola walk of spruce posts and slats grown over with azalea, lanterns on chains, benches either side |
| Fire Pit | 7 x 3 x 7 | 2 | a campfire in a ring of cobble slabs, log seats on three sides, a log pile and a lamp |
| Sundial | 7 x 3 x 7 | 2 | a round smooth-stone plaza with chiseled hour stones, a dial of slabs and its gnomon, two lamp posts |
| Topiary Garden | 9 x 5 x 9 | 3 | a clipped hedge round gravel walks, four shapes (cones of spruce, a ball and a cube of oak) and an azalea urn |
| Market Corner | 5 x 5 x 5 | 1 | crates, wool sacks, hay, pumpkins and melons under a striped awning on dark oak posts |
| Footbridge | 7 x 4 x 7 | 2 | a spruce bridge with rails over a stone-kerbed channel of water with lily pads, a lantern at each end |

All are in the Blueprint Table with the other decorations. The Flower Planters are unlocked from the start; the rest
start locked ("A market-day trader"): a traveling trader on market day may sell any of them, and a builder's level-up
unlocks locked builds too.

## 2. How it works

- Drawn in `tools/blueprints/decor.py` (`MORE_DECORATIONS`), saved by `generate.py`, listed in
  `StarterBlueprints.MORE_DECORATIONS`, which `StarterBlueprints.DECORATIONS` takes in. Through that list each one is in
  the Blueprint Table, the creative tab, the market-day traders' pool, and never rebuilt by the Grand Rebuild.
- Beauty: `hall.Decorations` gives each its points (above); a finished one within a Village Hall's reach adds them.
- The village map marks the larger ones as decorations; the Signpost, Flower Planters, Hay Cart, Notice Board and Market
  Corner are too small to mark, like the Street Lamp.
- None holds a block a villager takes a job or a meeting from (no barrel, composter, cauldron, lectern, smoker, loom,
  bell...): a decoration never hands out a job. Plants stand in their own soil; water and lily pads go in after the kerbs.
- Each has a hidden `aliveworkplace:built/<name>` advancement (written by `tools/questbook/generate.py`) for quests.
- **The Signpost's boards (36.4, `city/Signposts`).** When a builder finishes a Signpost in any style
  (`Builders` calls `Signposts.finished` after recording it), each wall sign within its few blocks is written and
  waxed, as the caravan road milestones are (`CaravanRoads.signText`). The board facing a way names the nearest other
  village whose hall lies within 45° of that way and 1,000 blocks ("To", its name, "N blocks", from the caravans' list
  of villages, by the live name when its hall is loaded), else the nearest finished build that way (its display name
  and distance; not another signpost), else the village's own name and "Welcome". The hall's round, once a day,
  writes the boards of its village's finished signposts again where the words changed (a village renamed, a new
  neighbour, a build pulled down); unchanged boards aren't touched.

## 3. Switches

None. `blueprintUnlocks` decides, as for every build, whether they start locked.

## 4. Saved data

None new. The ids are new, so no save changes. A Signpost's words live in its signs, saved with the world as any
sign's are; one finished before 36.4 gets them at its village's next dawn.

## 5. Items, blocks, jobs, commands

Blueprints `aliveworkplace:market_cross`, `aliveworkplace:stag_statue`, `aliveworkplace:flower_planters`,
`aliveworkplace:signpost`, `aliveworkplace:stone_archway`, `aliveworkplace:lily_pond`, `aliveworkplace:hay_cart`,
`aliveworkplace:notice_board`, `aliveworkplace:lantern_pergola`, `aliveworkplace:fire_pit`, `aliveworkplace:sundial`,
`aliveworkplace:topiary_garden`, `aliveworkplace:market_corner`, `aliveworkplace:footbridge` (names under
`blueprint.aliveworkplace.<name>`).

## 6. Decisions

- Fourteen, each a different kind of thing (a monument, a statue, flowers, a sign, an arch, water, a cart, a board, a
  walk, a fire, a dial, a garden, a market stack, a bridge), in the village's materials: stone bricks and cobble, spruce
  and dark oak, lanterns, flowers in their own soil. No barrel in the Market Corner (a fisherman's job block): its
  crates are planks with trapdoor lids, its sacks wool.
- Only the Flower Planters start unlocked: the starting set stays at 20 (26.10 asks for about 15 to 20), and a cheap
  pretty one lets a new village start decorating at once.
- Beauty 1 for the smallest, 2 for most, 3 for the Topiary Garden (as much as the Fountain).

## 7. Known limits

- The Signpost's boards are waxed, so a player can't write on them, and a sign put back on the post is written again at dawn. A village
  not yet on the caravans' list (its hall has never had a round) isn't named until it has.
- No upgrades: each is one tier.

## 8. Proof

GameTests: `MoreDecorationsGameTests` (each built by a builder from its plan's materials and finished; no villager POI
block in any; each a decoration with beauty and a name; the Flower Planters start unlocked, the rest come from market
day), `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`, `SignpostGameTests` (36.4: four
waxed boards naming the village east, a village west and a Well south, kept through a save and load; a renamed
village renamed at the next dawn; a Dark Oak signpost gets words too).

Showcase scenes: `decor` (every decoration placed side by side), `signpost` (a signpost at a crossroads between two
villages, each board up close).
