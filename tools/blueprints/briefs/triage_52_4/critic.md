# 52.4 Portals and shrines: the critic's triage

Milestone 52's rule: a small piece is rebuilt only when it looks weak next to the rebuilt village. A piece that holds
up is ticked with this sheet and gets no new drawing. The rebuilt village it is judged against: Well and Well 2, Well
House, Fountain, Lily Pond, Footbridge, the Daub House (`house_t1_b`), the 52.2 rebuilds (gazebo, fire pit, stag
statue and the rest) and 52.3's walls. Their shared bar: a stepped stone base, a two-tone roof with trim, lanterns on
posts at the corners, flowers in their own beds, and depth on every face.

How this was judged: each live `.nbt` was rendered (Lodestone, `front`, `back`, `left`, `right`, `top` and `low`
views). Each sheet `<piece>_sheet.png` in this folder shows the piece on the left ("OLD" = the live drawing) beside a
rebuilt village piece on the right ("NEW"): the rebuilt Gazebo beside both Nether Gates, the rebuilt Fountain beside
the Waystone Plaza, and the rebuilt Stag Statue beside the Mega Shrine. They are cropped, not to the same scale.
Scores use REBUILD.md's scorecard (1-5). For these pieces "Interior" means **Use**: the portal you walk into and the
netherworker's corner, the stone a traveler steps up to, the pedestal a trainer climbs to with the crowd in front.
Verdict: **HOLDS UP** means 3 or more on every score and no plainer than the rebuilt pieces; anything else is
**REBUILD**. A family's levels are judged together: if one level is REBUILD, the family is.

Two render caveats: the Lodestone pack draws polished blackstone in a warm brown, so the Nether Gate reads redder
here than in the game; and it leaves out modded blocks, so the Waystone Plaza's stone and the Mega Shrine's meteorite
blocks and pedestal are missing from the renders (the shrine's amethyst caps float). The scores below allow for what
the code places there (`waystones.py`, `decor.py`), not only for what the render shows.

| Piece | Sil. | Depth | Use | Detail | Verdict | Biggest weaknesses / evidence |
|---|---|---|---|---|---|---|
| nether_gate | 3 | 2 | 3 | 3 | **REBUILD** | Front: a clear gate shape, a pointed pediment with a gilded keystone over the obsidian frame, soul lanterns on the pillars, braziers at the front. But left/right: the whole arch is one block thick, a flat sheet on edge with no buttress or return. Top: the court is one dark blackstone plane, the same stone as the arch (one material from the ground up). The netherworker's cartography table, chest and barrel stand loose at the court's east edge (front/right) with no shelter, no work corner. Back: a blank wall. Beside the gazebo's two-tone roof and flower beds it's a dark slab with a doorway. |
| nether_gate_2 | 3 | 2 | 3 | 2 | (rebuilt with level 1) | Front/left: adds a gatehouse roof on four pillars, a storehouse on the east and a nether wart garden behind. Top/back: the roof is the same polished blackstone brick as the walls and court (STYLE: walls and roof never one material), a stepped slab lid with no eave, trim or finial, and the storehouse is a box under a flat slab lid. Back: the wart garden behind its nether brick fence is the best idea (keep it), but it's tucked behind a blank wall. Plainer than any rebuilt piece. |
| waystone_plaza | 2 | 2 | 3 | 2 | **REBUILD** | Front/left/low: a one-step dais on a paved square, four thin fence-post lamps at the corners and a single stair block on each side for a bench (the same armless bench the park bench was rebuilt for, 52.2). Top: a plain 7 x 7 square with its corners cut, no border, beds or planting beyond two potted poppies. In the game the waystone adds height in the middle, but the plaza round it is flat. Next to the rebuilt fountain's stepped basin, flower beds and lamp posts it looks unfinished. Five culture looks repeat the same plan. |
| mega_shrine | 2 | 3 | 3 | 2 | **REBUILD** | Front/back/left: a low square dais with stair moulding and four thin deepslate-wall posts; even with the meteorite caps and the pedestal in place, the outline is a box with four sticks. Nothing frames the pedestal or says "shrine" from 20 blocks. Low/top: three soul lanterns stand on the top step's floor (baselined owner-rule problems, 3: `soul_lantern@1,2,3` and two more), and the two potted alliums sit on the bare pad at the front corners. The steps (front) and stair moulding give it some depth. The rebuilt stag statue's planted, stepped base makes it look plain. |

**Count:** none hold up. All three families are rebuilt: nether gate (with level 2), waystone plaza (with its four
culture looks) and mega shrine.

## Notes for the rebuilds (most important first)

1. **The rules a test pins come first.** The nether gate's portal frame stays a vanilla-lightable obsidian frame (the
   builder lights it with `PortalShape`, and the screenshot harness lights it at (4, 2, 3) on the x axis); the plaza's
   waystone stays at (3, 2, 3) with exactly one stone per look and a 7 x 5 x 7 size; the shrine's pedestal stays at
   (3, 3, 3) on a chiseled meteorite block, with the trainer's spot (3, 3, 2) in front and its listed blocks present.
   The briefs list every one.
2. **Give the netherworker a place to work.** Nether Gate I's job block and storage are loose blocks on the court; a
   sheltered work corner beside the portal, within 8 blocks of its chests, reads as a workplace. Level II grows it.
3. **Two materials at least, and a roof that isn't the wall.** Both gates are one stone from ground to ridge. The
   rebuilt village's bar is a darker base, lighter walls and a contrasting, trimmed roof.
4. **Depth on every face.** The arch is one block thick, the plaza and the shrine are flat pads. Buttresses, returns,
   a stepped base, posts with caps, planting in its own beds.
5. **Keep the good ideas:** the gilded keystone and soul-fire braziers, Gate II's nether wart garden, the plaza's
   benches facing the stone (but real seats), and the shrine's stepped approach to the pedestal.

Renders used: the scratch render folder of this session (`<name>_<view>.png`, not committed). The sheets in this folder
are the record.
