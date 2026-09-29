# How our builds are drawn

Every blueprint we ship is drawn in Python (`starter.py`, `village.py`) with the kit in `kit.py`. These are the rules
that make a build look *built* instead of boxy. Check a new or changed build with a render (below) before committing it.

## The seven moves

1. **A plinth.** Walls stand on a ring of stone at y 0 (`plinth`, textured with a `Mix` such as `STONE_MIX`). A flared
   base of stairs (`skirt`) roots towers.
2. **A frame.** Logs at the corners and every 3–4 blocks along a wall (`posts`), a log beam round the top of each storey
   (`beam_ring`). Dark frame, light infill: spruce logs with birch planks or white terracotta.
3. **Depth on the walls.** Nothing sits flat: windows get open trapdoor shutters, a sill of upside-down stairs or a flower
   box (a top-half trapdoor with potted flowers) — all one block out from the wall (`window`). Doors get a lintel and a step.
4. **An overhanging roof.** Roofs reach one block past the walls on every side (`gable_roof`, `hip_roof`); gable ends are
   filled with the infill, with a post and a little window. The eaves hang level with the top beam so the roof sits down
   on the walls. `steep=True` doubles the pitch for towers and spires.
5. **Varied outline.** Upgrades add wings, jetties (an upper storey a block out over the lower one, on stair brackets),
   lower roofs against higher walls, a chimney up a gable (with a campfire for smoke).
6. **Texture.** Stone is a weighted mix (`Mix`): cobblestone with stone and andesite, stone bricks with cracked ones.
   Keep to blocks a miner or lumberjack brings: a mix costs the player a few kinds of block, not rare ones.
7. **Small life.** Lanterns (hanging from beams, standing on flower boxes or fence posts), barrels and hay by the door, a
   bench, flowers, a lamp post at a corner.

## Rules the mod needs (the tests check most of them)

- Front is z = 0 (north); y = 0 sits on the clicked block. Put the door on the front with a stair step at y 0 before it.
- A starter build must fit the test area built from (9, 2, 9): at most 13 x 16 x 13. Its materials must fit 4 barrels.
- An upgrade (`<name>_2`) has the same origin and front and keeps at least 60% of its base's blocks: draw it from the
  same helper as the base (e.g. `cottage_ground_floor`) or `grow()` the base and add to it.
- Don't hang anything (lanterns, bells) from a block an upgrade replaces: when the builder takes that block down, what
  hangs from it falls and the builder has to wait for a new one.
- Builders can't stand on roofs (stairs and slabs aren't solid underfoot) and reach about four blocks: anything high
  over a roof (a belfry, a finial) needs a floor or a wall top within reach below it, or it never gets built. Give tall
  rooms a loft floor.
- Plants on the ground (flowers, saplings) need grass or dirt under them, which a build site may not have: use potted
  plants, or a flower bed with its own soil (`flower_bed`).
- Builders never break containers, and anything with a block entity except signs, banners, skulls, beds, campfires and
  bells. Don't put a chest where an upgrade needs something else.
- Village houses keep exactly one job block (a vanilla one — a barrel, a lectern — would give the villager the wrong
  job), the street jigsaw at the front and the villager-spawn jigsaw in the floor.
- `finish()` (run by `fill_air`) works out stair corners and fence/pane/wall connections, so draw stairs straight and
  fences unconnected and let it join them up.
- Original designs only: no logos, characters or copies of other people's builds (the Healing Center's sign is a heart,
  the Supply Shop's a shopping bag).

## Seeing a build

The fastest check is a render of the .nbt with [Lodestone](https://www.npmjs.com/package/@mattzh72/lodestone) in
headless Chromium (a few seconds a build); the real game is `SCENE=gallery tools/screenshots/run.sh` (every starter
blueprint built in the client, front and back). Look at a build from the front and the back, low down as a player
would: if a wall is a flat plane of one block, give it a frame, a window with shutters or a change of material.
