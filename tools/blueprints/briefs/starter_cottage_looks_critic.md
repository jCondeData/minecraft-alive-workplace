# Starter Cottage biome looks: the critic's scorecards (49.10a, round 1, a fresh critic subagent, 2026-11-18)

OLD = the live plains Starter Cottage (Version B); NEW = each look's candidate (`build/rebuild/starter_cottage_<culture>/`).
A look must be the same building (rooms, beds, job blocks, containers, furniture spots) and read as its climate.
Checked: furniture spots match starter_cottage_b at every level (desert and snowy hang their lamps one lower, allowed);
beds, chests, barrel, furnace, crafting table unchanged; holes.py clean on all 12. OLD scores 4/4/4/4 throughout.

| Look | Silhouette | Depth | Interior | Detail | Reads as its culture | Verdict |
|---|---|---|---|---|---|---|
| Desert | 3 | 4 | 4 | 3 | yes | BACK |
| Snowy | 4 | 4 | 4 | 3 | no (can't be told from taiga) | BACK |
| Savanna | 3 | 4 | 4 | 4 | Level I yes, II-III no (a fort tower) | BACK |
| Taiga | 4 | 4 | 4 | 3 | yes | BACK (narrow) |

## Desert notes
1. L1, L3 front/back/left: corner posts of uneven height stick up through the parapet (reads as a ruin): one even parapet height, or one deliberate tower.
2. L1 back/right, L3 back: the trapdoor roof beams sit at random heights: one evenly spaced row just under the parapet.
3. L1 front: the arcade hides the door: frame it in chiseled/cut sandstone with a short awning.
4. Every level, sides: bring back planting (cactus, dead bush, flowers in planters by door and windows).

## Snowy notes
1. Every view: a snowy identity of its own: snow layers on slopes and eaves, a drift skirt, white/stripped-spruce trim and pale stone, so it can't be mistaken for taiga.
2. L1 left/right: break the plain roof planes (a dormer or chimney cap), shutters or flower boxes on the side walls.
3. L3 front: keep the entry porch (the best idea in the set); dress it (lantern, boot bench, firewood).

## Savanna notes
1. L2, L3 front/back/right: the crenellated flat-topped upper floor reads as a castle keep: carry Level I's thatched hip over the second storey so every level shares one roof language.
2. L2 right: the flat terracotta upper wall needs acacia posts, shutters or a balcony.

## Taiga notes
1. L1, L3 left/right: almost all roof slope: a dormer, or flower boxes and shutters on the low wall.
2. L1 right: tidy the trapdoor/stair clutter at the wall's foot into one log pile or one bench.

## Not settled (for the lane)
- The brief's Level I material list (spruce_log, cobblestone, oak_door, glass_pane, chest, a spruce stair): desert and savanna Level I have no spruce_log and no cobblestone; probably moot if those tests read only the plains id. Not tested.
- The whole-chimney rule (`chimneysStayWholeInEveryStyle`), same-cell-across-levels and under-80 %-placed rules: not tested on the looks. No GameTests run.

Sheets (not committed, build/): `build/rebuild/looks_sheets/{culture}_{1,2,3}_exterior.png`, `{culture}_cutaways.png`.

# Round 2 (a fresh critic subagent, 2026-11-18 20:37Z): all four SHIP

| Look | Silhouette | Depth | Interior | Detail | Reads as its culture | Verdict |
|---|---|---|---|---|---|---|
| Desert | 4 | 4 | 4 | 4 | yes | SHIP |
| Snowy | 4 | 4 | 4 | 4 | yes, now clearly apart from taiga (white and pale against dark spruce) | SHIP |
| Savanna | 4 | 4 | 4 | 4 | yes, every level (the keep is gone; the thatched hip carries over the upper storey) | SHIP |
| Taiga | 4 | 4 | 4 | 4 | yes | SHIP (narrow) |

Same building checked against starter_cottage_b at every level (beds, chests, barrel, furnace, crafting table, 16/29/36
furniture spots in the same cells; lamp chains and bed wood differ). Optional polish the critic left: desert, a visible
chiseled door surround and awning on Level I (the designer says the frame is there; the critic couldn't see it in the
front render), more planting; snowy, snow layers on the lower roof courses and something on Level I's right wall; taiga,
shutters or a box on the right low wall. Still not tested: the brief's Level I material list on desert and savanna, the
whole-chimney rule, same cell across levels, under 80 % placed; no GameTests. Sheets (look vs the live plains cottage,
Levels I and III): `briefs/starter_cottage_looks/<culture>_<level>_vs_old.png`.
