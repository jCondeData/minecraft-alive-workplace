# The Ranch

Milestone 49, item 49.9: the Rancher's workplace, rebuilt from scratch with the rebuild kit
(`tools/blueprints/REBUILD.md`): a blind brief (`tools/blueprints/briefs/ranch.md`), a blind design, and a critic who
scored old and new side by side (silhouette, depth, interior and detail 4/4/4/3 against 3/2/2/3 for Level I, 4/4/4/4
against 3/2/2/3 for Level II) and said SHIP (`tools/blueprints/briefs/ranch_critic.md`).
Roadmap items: 49.9, 49.10e

## 1. What a player sees

- **Ranch** (13 x 16 x 13; locked at first in the Blueprint Table, unlocked by a builder's level-up or found as a
  blueprint in village chests; the Steward builds one in a farms zone for a Rancher with no workplace, or for a village
  short of food): a **monitor horse barn**, the outline of a western ranch's barn seen from the road. A tall
  spruce-board center under a gray deepslate roof, with two low lean-to stall sheds against its sides, a row of high
  windows lighting the hay loft between the two roofs, and a little white cupola on the ridge. Over the big barn doors
  a **hay hood** carries the ridge out over the loft door, with a hoist beam and a lantern under it. A fieldstone flue
  climbs the back gable and smokes over the ridge.
  - In front stands the **ranch gate**: two dark posts and a crossbar with the ranch's brown banner and two lanterns,
    over the way into a fenced yard with a log horse trough and a stack of hay. There is no fence gate on the way in, so
    the Rancher walks from the road straight to his work.
  - Inside, the **stable aisle**: a packed-mud walk from the wicket door between the birch leaves of the big doors to
    the tack wall at the back, where the Rancher's **smoker** (his job block) stands between two **tack chests** (saddles,
    horse armor, carpets and feed). Either side, two **box stalls** bedded with hay, each gated off the aisle, with a
    barred window in the outer wall; lamps hang over the aisle and the stalls, and a potted cactus stands on a shelf by
    the smoker.
  - Up a ladder through a hatch, the **hay loft** over the aisle, bales stacked along both sides under the high
    windows, a lamp hung from the ridge.
- **Ranch II** (21 x 16 x 21): the barn as it was, and:
  - On the east, a **feed and bunk wing** under its own lower gable, entered by its own door: the feed barrel, a double
    chest, hay, the ranch hand's bed with a nightstand, a ledger desk with its chair and a red rug, a lamp on a chain.
    Firewood is stacked to the eave in the slot between the barn and the wing.
  - Behind, a big fenced **paddock** for the herd: a one-block **man-gap** on the west that a villager walks through
    and a horse can't, a double gate on the east to drive the herd in and out, a trough, a snubbing post for breaking
    in, a little slab-roofed run-in shelter with hay, and lamp posts on its corners.
- Every piece of furniture is a furniture spot, so a builder with the furniture mods builds the mods' pieces in their
  places (37.2); with the building mods its modded copy dresses the floors, chests and ladder (37.12).
- A Ranch standing in the older drawing keeps it until its owner presses **Refresh** on the hall's Repairs page
  (36.21). The smoker and the first chest stand in the same cells in the old and new drawings, and the cells of the
  old drawing's water cauldrons are left open, so a refreshed Ranch keeps its blocks where they were.
- **In its village's culture** (49.10e): a desert village builds both levels in coursed sandstone under mud brick, with
  a crenellated hay tower, a windcatcher, an arched gateway, viga ends, date palms and an adobe-pier corral; a savanna
  village under two tiers of golden thatch hips with no gable on acacia posts, over red granite and packed mud with
  clay pots on the peaks and a kraal for the yard (its thatch is lower: 13 x 14 x 13, then 21 x 14 x 21); a snowy
  village under one steep dark slate roof with dormers and snow on its ridge, pale calcite walls and a closed storm
  porch; a taiga village in dark spruce logs notched at the corners on mossy fieldstone, three stepped gables, a covered
  gallery on posts and a carved horse head on the ridge. The aisle, stalls, loft, smoker, chests, doors, Level II's
  bunk wing and paddock and every furniture spot are the same. Plains villages build the Ranch as drawn. The Blueprint
  Table lists the looks.

## 2. How it works

- Drawn in JavaScript with the rebuild kit: `tools/blueprints/js/ranch/` (`plan.mjs`: three concepts, story, palette,
  masses, rooms, openings, every furniture footprint, the walking line; `recipe.mjs`: the passes for both levels), on
  the shared parts library `tools/blueprints/js/parts/` (the ranch gate, the horse trough, the stall fronts, the
  cupola, the hay hood and the paddock fence). `build.sh` builds both levels as candidates; `LIVE=1 build.sh` saves the
  live drawings through `tools/blueprints/rebuild.py`, which runs check.py's checks with the owner's rules, `finish()`
  and writes `blueprint_furniture/<id>.json`.
- The old Python drawing is no longer saved by `generate.py` (the family is in its `REBUILT` set); `history.py` ships
  the old drawings in `blueprint_history/ranch*/` (and `modded/ranch*/`) for refreshes.
- `StarterBlueprints.RANCH` and `RANCH_2` carry the new sizes. Modded copies: `modded_library.py ranch ranch_2`. The
  Table's styles (`BlueprintStyles`) restyle the drawing at play time. The village "Ranch House" pieces are another
  family and keep their own looks.
- The Rancher's chests are within 8 blocks of his smoker (`SupplyContainers.find`), so he finds his saddles and feed.
- Level II is Level I with the wing and the paddock added: 822 of Level I's 824 blocks stay as they are, so the upgrade
  builds only what is new.
- The culture looks (49.10e) are drawn in `tools/blueprints/js/ranch_<culture>/` from the brief and the shipped plan
  (`look_spots.py` says SAME BUILDING: every furniture spot, the smoker, chests, bed and doors in the same cells at both
  levels); `SHIP=1 build.sh` saves them as `structure/looks/<culture>/ranch[_2]` with their furniture spots.
  `StarterBlueprints.RANCH_CULTURES` lists the cultures that have a look (`BlueprintLooks` reads it; take a culture out
  to hold its look back), `ranchLook(culture)` each look's two levels (the savanna look 14 tall) and `RANCH_LOOKS` all
  of them. Critic: `tools/blueprints/briefs/ranch_looks_critic.md` (desert in round 2, the other three in round 3).
  Scene: `rebuild_ranch_looks`.

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| (none of its own) | | `moddedLibrary` and the furniture switches apply as for every workplace. |

## 4. Saved data

Nothing new. The ids never change (`aliveworkplace:ranch`, `ranch_2`); standing ranches and sites mid-build keep their
drawing until a refresh.

## 5. Items, blocks, jobs, commands

- Blueprints: Ranch and Ranch II. Job block: one smoker (the Rancher's, which the Steward can hand him).

## 6. Decisions

- The critic's notes: the palette is narrowed (the fieldstone is cobblestone and mossy cobblestone only, no andesite or
  stone; the birch block over the wicket door is a dark oak lintel), and the potted cactus on the aisle shelf is a
  plant, not a piece of crockery, so it is no furniture spot.
- The wing's lamp hangs a block lower on a longer chain, so the bunk's nightstand is lit (the furniture check's light
  rule).
- The old drawing reached its smoker only through a closed fence gate, which villagers don't open; the new way in is
  the ranch gate's open crossbar and a wooden door.
- The footprint stays 13 x 13, so the Steward's renewal of an old Rancher's house fits it wherever the old one did.

## 7. Known limits

- The showcase scene `rebuild_ranch` is filmed on GitHub, not in the session that shipped it; the critic asked for the
  stable aisle and the II wing at eye level before the owner judges. `rebuild_ranch_looks` is filmed there too.

## 8. Proof

`BuilderGameTests.buildsRanch` (Level I built whole from its plan's materials), `starterUpgradesKeepMostOfTheirBase`
and `starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`, the `workplace_ranch` case of `WorkplacesGameTests`,
`ModdedLibraryGameTests`, `QaBuildReachGameTests`, `QaFurnishedRooms1031GameTests`, `FurnitureSpotGameTests`,
`QaRefreshHistory1028GameTests`. Scene `rebuild_ranch`. `RanchLooksGameTests` (culture pick, fallbacks, base/upgrade/lock
and map kind per look, sizes, beds, chests, job blocks and every furniture spot of the drawing, Level I of each look
built to DONE, a desert site saved mid-build reloads as desert). Scene `rebuild_ranch_looks`.
