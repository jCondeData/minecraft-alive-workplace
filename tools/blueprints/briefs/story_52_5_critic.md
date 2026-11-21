# Story and Legend pieces (52.5): the critic's scorecards

Sheets (made by the critic with `shoot.mjs` and `sheet.py`: OLD = the live `.nbt`, NEW = the candidate; front, back,
left, right, top, low): `founder_statue_sheet.png`, `shift_bell_sheet.png`, `prisoner_cage_sheet.png`. Eye-level shots:
not filmed. No cutaways: none of the three has rooms. For these non-buildings "Interior" scores **Use**: what the player
or the story does there.

## founder_statue, round 1

OLD `legend/founder_statue.nbt` 7 x 8 x 7, 113 blocks. NEW `build/rebuild/founder_statue/founder_statue.nbt` 7 x 10 x 7,
127 blocks.

| Score | OLD | NEW | Evidence |
|---|---|---|---|
| Silhouette | 2 | 4 | front/low: OLD's figure is a stone cross on a step (it reads as a grave); NEW reads in outline as a man in a wide hat holding a staff with a lantern, and each side is different (right: the green cloak and the staff arm). The single-block head under a 3 x 3 brim is a little small. |
| Depth | 2 | 4 | back/left: OLD is a flat stepped plinth under a flat figure; NEW has stair steps on all four sides, stair feet, sloped shoulders and a hat brim that overhangs the head. |
| Use | 2 | 4 | front: OLD's lanterns and copper plaque frame a grave, not a Founder; NEW is a person a player can walk up to on the front steps, with the plaque at its foot and three lanterns. |
| Detail | 2 | 3 | right/back: the patina is used as clothes (teal trousers, coat, weathered green cloak, green hat) and azaleas sit on moss at the back corners; but low/front: the mossy cobblestone column under the left arm, (1, 1..3, 3), reads as a dark stain, not as anything the figure leans on. |

Checked against the brief: 7 x 7 footprint, both sides odd, height 10 (in the 4 to 10 the old statues need); middle
column (3, y, 3) is the head at y 7 and the hat crown at y 8, inside the top three layers; no POI block, no container;
the `waxed_cut_copper` marker is still there (the plaque, (3, 1, 1)); the toes (stairs facing south) and the cloak at
z = 4 put the face on z = 0; 127 blocks, near the old 113.

**Missed:** `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural` (named in the brief): the new
`minecraft:waxed_oxidized_copper_trapdoor` (the hat brim, 8 of them) ends in " Trapdoor" and the lang file has no
`plural.aliveworkplace.minecraft.waxed_oxidized_copper_trapdoor`, so the test would fail once it ships.

Scores: NEW beats OLD on all four, loses on none. **Verdict: BACK** (only for the brief miss).
1. Material list (lang, not the drawing): add `"plural.aliveworkplace.minecraft.waxed_oxidized_copper_trapdoor":
   "Waxed Oxidized Copper Trapdoors"` to `en_us.json` in the same commit, or draw the brim with a block that already has
   a plural. With that line in, it passes without another look.
2. low/front: replace the mossy cobblestone column under the left arm with the arm itself (copper hanging to the hip)
   or remove it; today it reads as a stain beside the leg. (Optional: does not hold the ship.)

## shift_bell, round 1

OLD `story/shift_bell.nbt` 3 x 6 x 3, 20 blocks. NEW `build/rebuild/shift_bell/shift_bell.nbt` 3 x 6 x 3, 38 blocks.
**The renderer skips block entities: the bell itself shows in neither column** (both have it at (1, 3, 1), ceiling).

| Score | OLD | NEW | Evidence |
|---|---|---|---|
| Silhouette | 2 | 3 | front/left: OLD is two posts and a beam, a gate from front and back and a single plank from the sides; NEW is a small tower with a hipped copper roof and a finial, the same from all four sides, but the open layer at y = 1 (only an anvil and four lanterns under a solid deck) makes it read as a box on a stool. |
| Depth | 2 | 4 | front/back: OLD is flat posts on stair feet; NEW has a flared stair base, a deck of upside-down stairs, wall posts set in, and roof eaves of stairs. |
| Use | 3 | 3 | front: OLD's bell hangs in the open, rung by hand from the front or back, hidden from the sides by its posts, unlit; NEW's belfry is open on all four sides and lit by four lanterns, but the deck under the bell (top of y = 2) stands in the line from a player's eye on the ground, so ringing it by hand within reach is doubtful (not tested). |
| Detail | 2 | 4 | front/top: NEW tells the story: an anvil for the smith who asked, a gold block under the bell for the village's 128 gold, hanging lanterns, a deepslate and copper palette held throughout; OLD has none. |

Checked against the brief: bell at (1, 3, 1), ceiling, with (1, 4, 1) waxed weathered cut copper above it; 4 blocks
below the bell is the natural ground; inside 3 x 6 x 3, nothing below y = 0; hanging lanterns hang from deepslate
bricks, the posts and the lightning rod stand on blocks; the plural-checked materials (polished deepslate wall,
lantern) have plurals; no furniture spots. No requirement missed.

Scores: NEW beats OLD by one or more on silhouette, depth and detail; ties on use. **Verdict: SHIP.**
Advice for a later pass (not holding the ship): front: if hand-ringing matters, drop the deck to slabs or open its
middle edge so the bell's lower half can be clicked from the ground.

## prisoner_cage, round 1

OLD `legend/prisoner_cage.nbt` 5 x 5 x 5, 85 blocks. NEW `build/rebuild/prisoner_cage/legend/prisoner_cage.nbt`
9 x 11 x 9, 307 blocks. Render note: the renderer sets template y = 0 on top of the grass plate, so NEW's ground layer
(coarse dirt, mud) shows as a raised rim; in the game it replaces the ground and lies flush.

| Score | OLD | NEW | Evidence |
|---|---|---|---|
| Silhouette | 2 | 4 | front/left: OLD is a log-framed box; NEW is a cage wagon (wheels, gabled roof) beside a tent and a tall pole with a black banner, a different shape from every side; front/low: the white tent reads as a stepped snow mound with no ridge or opening seen from that side. |
| Depth | 2 | 4 | left/back: OLD has logs proud of the bars and a slab roof in a log rim; NEW has stair eaves on an overhanging roof, wheels proud of the bed with hubs, and a stepped tent. |
| Use | 3 | 4 | left: the prisoner stands on the wagon floor at (2, 3, 3) behind bars on all four sides, seen from the ground and reached from it (bars at y 3 to 4), and the captors' camp says why he is there; OLD showed him just as clearly but alone. |
| Detail | 2 | 4 | right/back: wheel hubs, fenced campfire, barrels in the tent, hay, a lantern pole with a black banner, straw-yellow carpet in the cage; OLD has a hay bale and a cauldron. |

Checked against the brief: inside 9 x 9 x 18; the moved prisoner's spot (2, 3, 3) is air with air above and dark oak
below; all 16 iron bars (plain `minecraft:iron_bars`) are within 2.5 blocks of (2.5, 3, 3.5), none further out in the
test's box; every bar end meets a block (pane-end check: none); the cage is closed (roof at y = 5); hanging lanterns
hang from blocks; plurals present for every countable material; `furniture.json` has no spots. The ship step must change
`LegendSites.site("outpost")`'s `new BlockPos(2, 1, 2)` to `new BlockPos(2, 3, 3)` (the brief allows the move). No
requirement missed.

Scores: NEW beats OLD on all four, loses on none. **Verdict: SHIP.**
Advice for a later pass (not holding the ship): front/low: give the tent a ridge and an opening on its long side so it
reads as a tent, not a white mound; the two barrels are villager job sites (fisherman), harmless at an outpost but a
chest would avoid it.

## castaway_camp, round 1

Sheet `castaway_camp_sheet.png`: front, back, left, right, top, low, plus two NEW cutaways (y <= 4 from the top, y <= 2
from the front; the OLD column repeats its top and front, it has no shelter). OLD `legend/castaway_camp.nbt` 9 x 4 x 7,
86 blocks. NEW `build/rebuild/castaway_camp/legend/castaway_camp.nbt` 10 x 15 x 10, 295 blocks. Eye-level shots: not
filmed. "Interior" scores **Use**.

| Score | OLD | NEW | Evidence |
|---|---|---|---|
| Silhouette | 2 | 4 | front/back/left/right: OLD is a low sand-and-slab patch with a small flag, nothing to see from 20 blocks; NEW is a mast with a yard and a red pennant beside an upturned hull, recognisable from every side. The sail (back) is a stepped wedge of wool, not a cloth. |
| Depth | 2 | 4 | left/right: OLD's slab canopy is one flat layer; NEW's hull has stair eaves stepping down both sides, a keel line, end posts proud of the planking and a sunken fire ring. |
| Use | 2 | 4 | cutaway y <= 2: the castaway's spot (4, 1, 4) stands open beside a sunken fire ring, reachable and with the east side open to walk out; under the hull (2 clear) a chest, a barrel with a lantern, a hay bed and a water cauldron make a survivor's shelter. OLD had the fire and a barrel but no shelter worth the name. |
| Detail | 2 | 3 | cutaway y <= 2/back: coral fans and sea pickles on the sand, salvaged chest and barrel, mossy fire stones tell the shipwreck; but four woods at once (spruce, dark oak, oak, birch posts) and the stepped sail read as noise. |

Checked against the brief: 10 x 10 footprint, 15 tall (inside 10 x 10 x 19); spot (4, 1, 4) unchanged: (4, 0, 4) sand,
(4, 1, 4) and (4, 2, 4) air, nothing above; three of four sides open at the feet (a fire-ring slab on the west), so a
player can stand beside and click the castaway and the walk out is open; y = 0 has 91 cells drawn (sand, gravel, the
campfire), 9 left to the world's ground, no air and nothing below y = 0; no entities; the lantern on the barrel and
the hanging one pass `check.py`'s lamp rule. Plurals: every countable material (logs, fences, slabs, red banner,
chest, barrel, lantern) has its `plural.aliveworkplace.minecraft.*` line. Nothing missed.

Scores: NEW beats OLD on all four, loses on none. **Verdict: SHIP.**
Advice for a later pass (not holding the ship): back: turn the stepped wool wedge into a sail hung from the yard (a
flat sheet, one or two blocks thick, with a torn edge); left/top: pick two woods for the hull and posts instead of four.

## traveller_camp, round 1

Sheet `traveller_camp_sheet.png`: front, back, left, right, top, low, plus a NEW cutaway (y <= 3 from the top; the OLD
column repeats its top). OLD `legend/traveller_camp.nbt` 7 x 4 x 7, 62 blocks. NEW
`build/rebuild/traveller_camp/legend/traveller_camp.nbt` 9 x 11 x 9, 206 blocks. The map frame is in the file but
entities don't render. Eye-level shots: not filmed. "Interior" scores **Use**.

| Score | OLD | NEW | Evidence |
|---|---|---|---|
| Silhouette | 2 | 4 | front/low: OLD is a dirt patch under a flat wool awning on two posts; NEW is a banded tepee with its poles crossing out of the smoke hole, a shape you'd know in outline from any side. Every side shows the same cone, so the camp beside it (back/right) does the work of telling sides apart. |
| Depth | 2 | 3 | back/left: the cone steps in with stair skirts and a red band at the base and the crown, and the door is a recess with open trapdoor flaps; but each tier of the cone is a flat smooth face with no seams or lap lines. |
| Use | 3 | 4 | cutaway y <= 3: inside the tepee a bed on a red rug, a chest, a pot and a lantern hung on a chain; outside, the spot (4, 1, 5) stands at the door between the map barrel and a fire ring with seats and a log bench; OLD had the same bed, barrel and fire, all in the open. |
| Detail | 2 | 3 | back/top: a lodestone and slab cairn with mossy stone (a wayfarer's marker), a lantern on a fence post, the fire ring in gravel; but smooth sandstone reads as stone, not hide or canvas, and the grass-block rim of the ground layer (top) will be a green square in a desert, snow or badlands portal. |

Checked against the brief: 9 x 9 footprint, 11 tall (<= 28); spot (4, 1, 5) unchanged: (4, 0, 5) coarse dirt (solid),
(4, 1, 5) and (4, 2, 5) air, nothing above; east and west open at the feet, so it is reachable and the walk out is
open; y = 0 has all 81 cells drawn, none air, nothing below y = 0; exactly one barrel, (2, 1, 5), facing up; exactly
one entity, a `minecraft:item_frame` at pos (2.5, 2.03125, 5.5), `TileX/Y/Z` 2, 2, 5 (the cell above the barrel,
`TileY` 2), `Facing` 1, holding `minecraft:map` count 1; the furniture spots (a lamp at (4, 3..5, 2), a bed at
(3..4, 1, 1)) match the template's blocks.

**Missed:** `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`: the new `minecraft:andesite_slab` (the
cairn, (1, 1, 0)) ends in " Slab" and `en_us.json` has no `plural.aliveworkplace.minecraft.andesite_slab`, so the
test would fail once it ships. Every other countable material has its line.

Scores: NEW beats OLD on all four, loses on none. **Verdict: BACK** (only for the brief miss).
1. Material list (lang, not the drawing): add `"plural.aliveworkplace.minecraft.andesite_slab": "Andesite Slabs"` to
   `en_us.json` in the same commit, or make the cairn's slab a cobblestone slab (already in the camp, already has a
   plural). With either, it passes without another look.
2. top: draw the outer ring of y = 0 that is grass block as coarse dirt or leave those cells out (the world's ground
   stays), so the camp doesn't paint a green square in a desert or snowy portal. (Optional: does not hold the ship.)
3. back/left: give the cone's smooth faces a seam (a column of stairs or a different shade where the hide overlaps)
   so it reads as hide, not stone. (Optional.)
