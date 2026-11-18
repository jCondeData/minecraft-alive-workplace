# 52.5 Story and legend sites: the critic's triage

Milestone 52's rule: a small piece is rebuilt only when it looks weak next to the rebuilt village. A piece that holds
up is ticked with this sheet and gets no new drawing. The rebuilt village it is judged against: Well, Fountain, the
Daub House (`house_t1_b`), the 52.2 rebuilds (gazebo, fire pit, stag statue, camp kitchen) and 52.3's walls (palisade,
palisade gate, wall tower). Their shared bar: a stepped stone base, a two-tone roof with trim, lanterns on posts,
flowers in their own beds, and depth on every face.

How this was judged: each live `.nbt` was rendered (Lodestone, `front`, `back`, `left`, `right`, `top` and `low`).
Each sheet `<piece>_sheet.png` in this folder shows the piece on the left ("OLD" = the live drawing) beside a rebuilt
village piece of the closest kind on the right ("NEW"): the Palisade Gate beside Bandit Fort I and II, the Wall Tower
beside Fort III, the Palisade beside the Raiders' Stockade, the Fire Pit beside the Bandit Camp and the Castaway Camp,
the Camp Kitchen beside the Caravan Wreck and the Traveller Camp, the Gazebo beside the Shift Bell, the Daub House
beside the Hermit Hut, the Stag Statue beside the Founder Statue, and the Well beside the five trail marks and the
Prisoner Cage. They are cropped, not to the same scale. Scores use REBUILD.md's scorecard (1-5); for these pieces
"Interior" means **Use**: what the player or the story does there. Verdict: **HOLDS UP** means 3 or more on every score
and no plainer than the rebuilt pieces; anything else is **REBUILD**. A family's levels are judged together.

Render caveats: every piece is vanilla blocks only, so nothing is missing from the renders. The camps, the wreck, the
stockade and the trail marks carry their own patch of ground in the template (dirt, coarse dirt, sand), which renders
as a square earth plate on the lawn; in the game the trail code cuts and fills the terrain round it
(`CaravanTrail.fits`), so their edges blend better than here. The scores allow for that. The bandit camp is an `.nbt`
(`camp/bandit_camp`, drawn in `tools/blueprints/bandits.py`), not drawn in code at placement.

| Piece | Sil. | Depth | Use | Detail | Verdict | Biggest weaknesses / evidence |
|---|---|---|---|---|---|---|
| bandit_fort | 2 | 2 | 3 | 2 | **REBUILD** | Low/front: a 29 x 29 ring of one dark log wall on a cobble strip, the same height all round, with a gap and a fence-rail lintel for the gate: no gatehouse, no corner towers, nothing breaks the outline. Top: the yard is one coarse dirt plane with tents and a fire set loose on it. Beside the rebuilt Palisade Gate's gabled gatehouse it's a box. |
| bandit_fort_2 | 2 | 2 | 3 | 2 | (rebuilt with level 1) | Front/back: adds two small hip-roofed watch huts on the front corners and a stable along the back wall, but the wall itself is unchanged (one log, one height, no walk or hoarding on the outside, left/right), and the back corners stay bare. |
| bandit_fort_3 | 4 | 3 | 4 | 3 | (rebuilt with level 1) | Front/back: the King's keep is the family's best part, a two-storey hall with a steep gable roof, a cobble chimney and a frame proud of its infill; it gives the fort a real silhouette. Left/right: the curtain wall round it is still the flat log ring of level 1. Keep the keep's idea. |
| raiders_stockade | 2 | 2 | 3 | 2 | **REBUILD** | Front/back/left: the same one-log wall as the fort, a rectangle of even height on a cobble strip with a doorway in it; the gate is a plain gap with a slab lintel and two lanterns. Top: the tent, fire and cage stand on bare coarse dirt. Next to the rebuilt Palisade's layered posts, walk and stone footing it reads as an unfinished first pass. |
| bandit_camp | 2 | 2 | 3 | 2 | **REBUILD** | Front/right: a red wool block tent (a stepped pyramid of full blocks), a gray wool tent laid as flat slabs, a log pile and crates on a ragged dirt pad; from 20 blocks it's a low pile with one red lump. Top: no layout, no fence or watch post, a single torch-post lantern. The rebuilt Fire Pit beside it has a stepped stone ring, posts and seats. |
| caravan_wreck | 2 | 2 | 3 | 3 | **REBUILD** | Left/front: the canvas is a flat white wool box sitting on a heap of planks and trapdoors; the wheels (stripped dark oak hubs) don't read as wheels from any view, so it reads as a pile, not an overturned wagon. Front/back: the spilled cargo (barrels, pumpkin, hay, composter crates) is a good story, keep it. Top: holes in the pad show through. |
| trail_1..5 | 2 | 1 | 3 | 2 | **REBUILD** | Front/top (montage of all five): each is a 3 x 3 dirt plate with one prop: a dead bush and a lantern on the ground (1), a few planks (2), a banner on a fence post (3, the only one seen from afar), two slabs (4), a composter and tripwire hooks (5). The player is meant to follow them across 120+ blocks; only mark 3 would catch the eye. Cheap to redo, and they carry the story. |
| shift_bell | 2 | 2 | 3 | 2 | **REBUILD** | Front/left: two dark log posts and a slab lintel with the bell hanging between, on a few stone stairs: it reads as a gallows frame, one wood from foot to top, no roof or cap. A village reward (the reform story's ending) that stands beside the hall, so it is seen every day next to the rebuilt pieces. |
| castaway_camp | 2 | 2 | 3 | 3 | **REBUILD** | Front/left: a lean-to of slabs on fences, a raft of slabs and a white wool flag on a sand-and-gravel patch; all of it under two blocks high except the flag. Top: the patch is a hard-edged rectangle. The story props (raft, signal flag, fire, barrel) are right, the shape is flat. |
| traveller_camp | 2 | 2 | 3 | 3 | **REBUILD** | Front/back: a flat white wool sheet on four fence posts over a bed, a campfire, a lantern post and a barrel on a dirt plate; the awning has no slope or tie-downs, so from the side (left) it's a plank. Beside the rebuilt Camp Kitchen's pavilion it looks unfinished. |
| hermit_hut | 4 | 3 | 4 | 4 | **HOLDS UP** | Front/right: a clear cottage shape with a cobble chimney up the gable, log posts proud of the plank-and-cobble walls, a fenced flower garden with a lantern post, and a pond with lily pads behind (top). Its palette (bookshelves, lectern, bed, cauldron, campfire, berry bushes) furnishes the sage's life. Weakest face: the left gable is a flat plank triangle and the roof has no trim, so it's humbler than the Daub House, but that suits a hermit and it meets the bar everywhere else. |
| founder_statue | 3 | 3 | 3 | 2 | **REBUILD** | Front/top: a good stepped base (stone brick on a polished andesite plinth, chiseled panels, a copper plaque, lanterns at two corners). But front/left: the figure is a column with two stubs that reads as a grave cross, not a person, and the whole piece is one gray stone with no planting. Beside the rebuilt Stag Statue (a recognisable figure on a planted, stepped base) it looks like a memorial stone. |
| prisoner_cage | 2 | 3 | 3 | 3 | **REBUILD** | Front/back/left: a 5 x 5 box: cobble base, dark log corner posts, iron bars between, a flat slab lid with a lantern on one corner. Posts stand proud of the bars (some depth), but the outline is a cube from every side and top. Seen at an outpost, it is the place the player frees a Legend from. |

**Count:** one holds up (the Hermit Hut, ticked with its sheet). Rebuilt: the Bandit Fort family (all three levels,
keeping the keep's idea), the Raiders' Stockade, the Bandit Camp, the Caravan Wreck, the five trail marks, the Shift
Bell, the Castaway Camp, the Traveller Camp, the Founder Statue and the Prisoner Cage.

## Notes for the rebuilds (most important first)

1. **The rules a test or code pins come first** (the briefs must list each):
   - **Bandit Fort** (`guard/BanditFort.java`, `BanditKingStoryGameTests`, `QaBanditKing1028GameTests`): all three
     levels 29 x 18 x 29, each standing on the one before; front z 0. The treasure chest at (18, 1, 24) with loot
     `chests/bandit_king`; the postern a door at (14, 1, 28) that opens; the yard's middle (14, 1, 14), the keep's door
     (14, 1, 15), the throne spot (14, 1, 21), the yard men at (12/16, 1, 8) and (11/17, 1, 14), and the watchtower
     platforms at (2, 6, 2) and (26, 6, 2) (level 2 must give a pillager a floor there). Men keep within 17 of the yard.
   - **Caravan Wreck** (`CaravanTrailGameTests.theWreckLiesOnUnevenGround`): the test checks block ids at fixed spots:
     spruce planks at (6, 2, 5) "the wagon's floor", white wool at (2, 1, 5) "the canvas", stripped dark oak log at
     (7, 4, 3) and (10, 1, 7) "the wheels", a lantern at (9, 1, 10), a composter at (2, 1, 1). A rebuild that moves
     them changes this test with it (same meaning, new spots), never deletes the checks.
   - **Raiders' Stockade** (same class, `theStockadeLiesOnUnevenGround`): dark oak log palisade at (0, 2, 6), a dark
     oak fence gate at (8, 1, 0), a campfire at (9, 1, 8), a tent ridge of stripped dark oak at (4, 3, 12), the cage's
     iron bars at (13, 1, 10), its lever at (15, 2, 9) and an iron door at `CAGE_DOOR` (14, 1, 10); the loot chest at
     `LOOT_CHEST` (7, 1, 14).
   - **Trail marks** (`theFiveMarksLieOnUnevenGround`): each 3 x 3 footprint, no earth over it; the checked props:
     a lantern at (0, 1, 2) (1), a barrel at (1, 1, 1) (2), a white banner at (1, 3, 1) on a spruce fence at (1, 1, 1)
     (3), a dirt path at (0, 0, 1) (4), a composter at (1, 1, 1) and a tripwire hook at (0, 1, 1) (5). A player counts
     a mark within 6 blocks, so the footprint can grow a little if the test's spacing (6 apart) is kept.
   - **Shift Bell** (`ReformStories.placeBell`): the code only places it on a free 3 x 3 x 6 box of natural ground and
     records the bell at (1, 3, 1). A bigger bell house needs that code changed with it.
   - **Legend camps** (`LegendSites.site`, `LegendSitesGameTests`): the Legend stands at (4, 1, 5) in the Traveller
     Camp, (2, 1, 2) in the Prisoner Cage and (4, 1, 4) in the Castaway Camp; each camp has **exactly one barrel**, with
     a map frame set on its top (facing up); the cage has iron bars within 3 blocks of the prisoner, and breaking one
     frees him.
   - **Founder Statue** (`Founder.stands`): built by the builders from a blueprint; it "stands" while one of the top
     three blocks of the footprint's middle column is solid, so the figure's head must be in the middle column.
   - **Bandit Camp** (`threat/Lairs.java`, `B209GameTests`): `camp/bandit_camp` id kept; its chests get the culture's
     loot, and cleanup walks the template's own blocks, so any layout works.
2. **Break the wall's outline.** The fort and the stockade are one log, one height, all round. A gatehouse, corner
   towers, a walk with hoarding, and a stone footing that steps are the rebuilt palisade's bar.
3. **Shapes you can read from 20 blocks.** The wagon must read as a wagon (round wheels, a hooped canvas), the tents
   and awnings need slope and guy ropes, the trail marks one tall eye-catcher each, the founder a person.
4. **Two materials at least, and planting.** The statue and the bell are one material from foot to top; the camps are
   plates of dirt with hard edges. Ragged edges, grass and flowers in beds, a darker base.
5. **Keep the good ideas:** Fort III's keep, the wreck's spilled cargo, the castaway's raft and signal flag, the
   statue's stepped base and copper plaque, the stockade's cage with its lever.

Renders used: the scratch render folder of this session (`<name>_<view>.png`, not committed). The sheets in this folder
are the record (downscaled to 80% and reduced to 256 colors to keep each under 350 KB).
