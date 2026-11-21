# 52.2 Street pieces: the critic's triage

Milestone 52's rule: a small piece is rebuilt only when it looks weak next to the rebuilt village. A piece that holds
up is ticked with this sheet and gets no new drawing. The rebuilt village it is judged against: Well and Well 2,
Fountain, Lily Pond, Footbridge, Well House, and the Daub House (`house_t1_b`). Their shared bar: a stepped stone base,
a two-tone roof with trim, lanterns on posts at the corners, flowers in their own beds, and depth on every face.

How this was judged: each live `.nbt` was rendered (Lodestone, `front`, `left` and `top` views, plus `low` and `back`
for the gazebo, camp kitchen, hay cart and stag). Each sheet `<piece>_sheet.png` in this folder shows the piece on the
left ("OLD" = the live drawing) beside a rebuilt village piece on the right ("NEW"). Both sides are cropped to fill
the frame, so they aren't to the same scale. Scores use REBUILD.md's scorecard (1-5). For pieces that aren't
buildings, "Interior" means the use and walking line (can you sit, walk through, or read it). Verdict: **HOLDS UP**
means 3 or more on every score and no plainer than the rebuilt pieces; anything else is **REBUILD**.

| Piece | Sil. | Depth | Use | Detail | Verdict | Biggest weaknesses / evidence |
|---|---|---|---|---|---|---|
| street_lamp | 4 | 3 | 3 | 4 | **HOLDS UP** | Front: a stair-skirted stone foot, chiseled collar, crossarm with two lanterns on chains. Reads as a lamp from every side. |
| signpost | 3 | 3 | 3 | 3 | **HOLDS UP** | Front/left: log post with signs on four faces, cobble stair skirt, a pot each side and a lantern cap. Modest but complete for its job. |
| notice_board | 4 | 4 | 3 | 4 | **HOLDS UP** | Front: a framed board under a stair-eaved roof with a 1-block overhang, lanterns at both posts, cobble foot and pots. As much depth as the rebuilt well. |
| park_bench | 2 | 2 | 3 | 2 | **REBUILD** | Front: three bare stairs with no arms, standing on grass with no paving. Left: the "hedges" are single leaf cubes on bare ground. A fence-post lamp stuck behind. A flat strip next to the well's stepped base. |
| sundial | 2 | 2 | 3 | 2 | **REBUILD** | Front/left: a one-step smooth-stone disc with a 2-block stair stub for a gnomon, which doesn't read as a sundial. Top: no hour marks. Its two lamp posts stand loose on the grass off the paving, asymmetric. Next to the fountain it looks unfinished. |
| hay_cart | 3 | 3 | 3 | 3 | **HOLDS UP** | Low/back: a two-wheeled cart (a trapdoor wheel on each side) with a slab bed, fence rails, heaped hay and fence-gate shafts. It reads as a cart from every side. A small prop that's fine as it is. |
| flower_planters | 2 | 1 | 3 | 2 | **REBUILD** | Front: a flat 7-wide, 2-high brick wall with no cap, sill or trim, all one material. Top: a U open at one end. Brick is off the rebuilt village's stone, spruce and birch palette. The weakest face of the set. |
| lantern_pergola | 3 | 3 | 4 | 4 | **HOLDS UP** | Front: spruce posts on a stone and andesite floor with benches, a chained lantern, potted azaleas, and an open slatted top with azalea growing over it (top view). Boxy outline, but rich and usable. |
| gazebo | 3 | 3 | 4 | 3 | **REBUILD** | Front/back: dark-oak log posts under a dark-oak stair pyramid, so walls and roof are one wood (STYLE: never). The plain stepped pyramid has no trim, soffit or finial. Beside the Well House's trimmed two-tone roof it reads as a brown block. Inside (low view) is good: benches, lantern, pots. Keep that. |
| bandstand | 5 | 4 | 4 | 4 | **HOLDS UP** | Front/left: an octagonal stepped stone base with stairs on several sides, birch columns, railings, a flared dark-prismarine roof, a checker floor with note blocks and lanterns. The strongest piece of the set, at least the match of the well house. |
| topiary_garden | 2 | 2 | 3 | 2 | **REBUILD** | Front/left: the grass blocks at y 0 make a raised turf slab with dirt sides showing. The "topiary" is shapeless leaf blobs and leaf walls, with no clipped forms (balls, cones, arches). Top: cross paths are fine, but the edge has no border. The rebuilt Lily Pond's stone edging beats it. |
| stag_statue | 2 | 3 | 3 | 3 | **REBUILD** | Front/low: the stag reads as a table-legged animal with a hammer on a long neck. The antlers are a flat T. From the back, more like a llama. Its shape is the whole point of a statue, and only the left view comes close. The stepped plinth with chiseled panels and potted ferns is good: keep its idea. |
| stone_archway | 4 | 4 | 3 | 4 | **HOLDS UP** | Front/left: a stone-brick arch with merlons, a chiseled keystone, mossy and cracked bricks clustered low, vines on the sides, a hanging lantern and flowers at the foot. Ruined but deliberate, and you can walk through it. |
| fire_pit | 2 | 2 | 4 | 2 | **REBUILD** | Front/left: coarse dirt, gravel and cobble placed at y 0 make a lumpy raised patch, single-block confetti (STYLE: weathering in clusters). The campfire sits flush and hard to see. The log seating on three sides (top) works: keep the layout. |
| camp_kitchen | 3 | 3 | 4 | 3 | **REBUILD** | Left/back: each gable is a blank 11-wide plank panel with no studs, window or king post (STYLE: no flat panel wider than about 6), and the roof has no trim or soffit. Front/low: the open kitchen, flower boxes and lamp posts are good. Plainer than the Well House and Daub House it would stand beside. |
| camp_kitchen_2 | 4 | 3 | 4 | 3 | (rebuilt with level 1) | Front/top: the stone-chimney wing and fenced garden plot give real massing. It inherits level 1's blank gables and untrimmed roof, and must keep 60% of level 1's blocks, so it's redrawn with it. Keep the wing, chimney and garden plan. |

**Count:** 7 hold up (street lamp, signpost, notice board, hay cart, lantern pergola, bandstand, stone archway).
8 are rebuilt (park bench, sundial, flower planters, gazebo, topiary garden, stag statue, fire pit, and the camp
kitchen family with its level 2).

## Notes for the rebuilds (most important first)

1. **Flower planters, sundial, park bench, topiary garden** are the clearest gaps. They are flat on the grass, where
   every rebuilt piece stands on a stepped stone base with a lantern post at the corner and flowers in their own beds.
   Their briefs should say only what each must do. The designer will find that bar in STYLE.md.
2. **Ground at y 0:** the fire pit and topiary garden put soil blocks (grass, coarse dirt, gravel) at y 0, which sits
   one block above the ground and shows dirt sides. That reads as a lump in every front view. Paving or a stone edge
   at y 0 is what the rebuilt Lily Pond does.
3. **Gazebo:** the inside works. The outside breaks the one-wood rule and has no roof trim. Being the park's
   centerpiece, it should not look plainer than the Well House.
4. **Stag statue:** the shape fails from three of four sides. A statue has to read in outline first.
5. **Camp kitchen:** the layout and level 2's massing are sound. The weakness is the blank gables and bare roof. It is a
   Pokémon job house (`VillageHouses.POKEMON_JOB_HOUSES`, `StarterBlueprints.CAMP_KITCHEN`, read by
   `VillageGameTests` and `PokemonBuildsGameTests`), so its brief must keep what that code and those tests need
   (job block, furniture spots, the 13 x 10 x 11 size, the upgrade's 60% rule).

Renders used: `build/rebuild/triage/out/` (full frames) and `build/rebuild/triage/crop/` (cropped), not committed.
