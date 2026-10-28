"""
The blueprint kit: Build (a structure being drawn, saved as a vanilla structure .nbt) and the building techniques that
make a build look built rather than boxy — see STYLE.md next to this file.

Conventions for every blueprint (the mod relies on these):
  * x = width, y = height, z = depth; the FRONT of the build is the z = 0 side (north).
  * y = 0 is the floor layer; it sits on top of the block the player clicks.
  * Every position inside the bounds is listed (air included) so builders clear the site.
  * A basement (ROADMAP 26.15) is drawn below y = 0, down to y = -basement (Build(..., basement=4)): `cellar` digs and
    walls it. The file holds it at the bottom (shifted up by `basement`); StarterBlueprints.Entry's basement moves it
    back under the ground. Outside its walls the ground under y = 0 is left out of the file (the world's as it is).
"""
import os
import zlib
import nbtlib
from nbtlib import Compound, List, Int, String, Double

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
MAIN_STRUCTURES = os.path.join(ROOT, "src/main/resources/data/aliveworkplace/structure")
TEST_STRUCTURES = os.path.join(ROOT, "src/gametest/resources/data/aliveworkplace_test/structure")
TEST_AREAS = os.path.join(ROOT, "src/gametest/resources/data/aliveworkplace_test/gametest/structure")
COMPAT_AREAS = os.path.join(ROOT, "src/compattest/resources/data/aliveworkplace_compat/gametest/structure")
DATA_VERSION = 3955  # Minecraft 1.21.1


class Build:
    def __init__(self, w, h, d, basement=0):
        self.w, self.h, self.d = w, h, d
        self.basement = basement
        self.cellars = []  # (x0, z0, x1, z1): the rooms dug under y = 0, emptied by fill_air
        self.blocks = {}

    def set(self, x, y, z, name, **props):
        assert 0 <= x < self.w and -self.basement <= y < self.h and 0 <= z < self.d, (x, y, z, name)
        if isinstance(name, Mix):
            name = name.at(x, y, z)
        if ":" not in name:
            name = "minecraft:" + name
        self.blocks[(x, y, z)] = (name, tuple(sorted((k, str(v).lower()) for k, v in props.items())))

    def fill(self, x0, y0, z0, x1, y1, z1, name, **props):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, name, **props)

    def ring(self, x0, z0, x1, z1, y, name, **props):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    self.set(x, y, z, name, **props)

    def get(self, x, y, z):
        """(id, properties) of the block at (x, y, z), or None if nothing is there yet."""
        return self.blocks.get((x, y, z))

    def villager(self, x, y, z, profession, villager_type, tags=()):
        """A villager standing on (x, y, z) when the structure is placed (as vanilla's village villagers are), with
        `profession` (an id such as "aliveworkplace:florist") and a little experience, so the job stays theirs
        until they take a workstation of it."""
        from nbtlib import Double
        self.entities = getattr(self, "entities", [])
        self.entities.append(Compound({
            "pos": List[Double]([Double(x + 0.5), Double(y), Double(z + 0.5)]),
            "blockPos": List[Int]([Int(x), Int(y), Int(z)]),
            "nbt": Compound({
                "id": String("minecraft:villager"),
                "VillagerData": Compound({"profession": String(profession), "level": Int(1), "type": String(villager_type)}),
                "Xp": Int(1),
                "PersistenceRequired": nbtlib.Byte(1),
                **({"Tags": List[String]([String(t) for t in tags])} if tags else {}),
            }),
        }))

    def set_nbt(self, x, y, z, nbt):
        """Block-entity data for the block at (x, y, z) (set the block first)."""
        self.nbt = getattr(self, "nbt", {})
        self.nbt[(x, y, z)] = nbt

    def grow(self, w, h, d, basement=None):
        """A bigger copy of this build (air left out), for an upgrade drawn on top of its base; `basement` to dig deeper."""
        b = Build(w, h, d, max(self.basement, basement or 0))
        for pos, block in self.blocks.items():
            if block[0] != "minecraft:air":
                b.blocks[pos] = block
        b.nbt = dict(getattr(self, "nbt", {}))
        b.cellars = list(self.cellars)
        b.spots = [dict(s) for s in getattr(self, "spots", [])]
        b.open_panes = dict(getattr(self, "open_panes", {}))  # B648: pane ends left free on purpose, with their reason
        return b

    def clear(self, x0, y0, z0, x1, y1, z1):
        """Takes blocks out again (they become air when the build is saved)."""
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.blocks.pop((x, y, z), None)

    def fill_air(self):
        seal_slots(self)  # roofs that run in under another's overhang meet at a wall, not a gap
        finish(self)  # stair corners and fence/pane/wall connections, as the game would work them out
        if seal_peeks(self):  # no quarter-block gaps into the attic where roofs meet at a corner
            finish(self)
        for x in range(self.w):
            for y in range(self.h):
                for z in range(self.d):
                    self.blocks.setdefault((x, y, z), ("minecraft:air", ()))
        for x0, z0, x1, z1 in self.cellars:
            for x in range(x0, x1 + 1):
                for y in range(-self.basement, 0):
                    for z in range(z0, z1 + 1):
                        self.blocks.setdefault((x, y, z), ("minecraft:air", ()))

    def door(self, x, y, z, name, facing, hinge="left"):
        self.set(x, y, z, name, facing=facing, half="lower", hinge=hinge, open=False, powered=False)
        self.set(x, y + 1, z, name, facing=facing, half="upper", hinge=hinge, open=False, powered=False)

    def bed(self, x, y, z, color, facing):
        """(x, y, z) is the foot; the head is one block towards `facing`."""
        dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[facing]
        self.set(x, y, z, f"{color}_bed", facing=facing, part="foot", occupied=False)
        self.set(x + dx, y, z + dz, f"{color}_bed", facing=facing, part="head", occupied=False)

    def furniture(self, kind, x, y, z, facing, cells=((0, 0, 0),), wood=None, colour=None):
        """Records a furniture spot (ROADMAP 37.2, furniture.py) for a piece this drawing just drew at (x, y, z): its kind
        (furniture.KINDS), the way a person using it faces, the cells it fills relative to (x, y, z), and its wood or
        colour. Chairs beside tables, tables and beds are found by themselves; record the rest."""
        self.spots = getattr(self, "spots", [])
        cells = [(x + dx, y + dy, z + dz) for dx, dy, dz in cells]
        # What each cell held when the piece was drawn: an upgrade that later builds over the piece drops its spot
        # (furniture.spots_of) instead of leaving a spot on blocks that are no longer there.
        drawn = [self.blocks.get(c, ("minecraft:air", ()))[0] for c in cells]
        self.spots.append({"kind": kind, "pos": [x, y, z], "facing": facing, "wood": wood, "colour": colour,
                           "cells": cells, "drawn": drawn})

    def to_nbt(self):
        entities = getattr(self, "entities", [])
        if self.basement and entities:  # 36.7: moved up with the blocks (StarterBlueprints.withBasement moves them back)
            entities = [moved_up(e, self.basement) for e in entities]
        palette, index, blocks = [], {}, []
        for (x, y, z), key in sorted(self.blocks.items()):
            if key[0] == "minecraft:structure_void":
                # "Leave the world as it is here": left out of the file, as the game's own structure block saves it.
                # Written in, it would be placed as a real block by processors that only skip air (village houses).
                continue
            if key not in index:
                index[key] = len(palette)
                name, props = key
                entry = Compound({"Name": String(name)})
                if props:
                    entry["Properties"] = Compound({k: String(v) for k, v in props})
                palette.append(entry)
            entry = Compound({"pos": List[Int]([Int(x), Int(y + self.basement), Int(z)]), "state": Int(index[key])})
            extra = getattr(self, "nbt", {}).get((x, y, z))
            if extra is not None:
                entry["nbt"] = extra
            blocks.append(entry)
        return Compound({
            "DataVersion": Int(DATA_VERSION),
            "size": List[Int]([Int(self.w), Int(self.h + self.basement), Int(self.d)]),
            "palette": List[Compound](palette),
            "blocks": List[Compound](blocks),
            "entities": List[Compound](entities),
        })

    def save(self, folder, name, checked=True):
        """Writes the build to folder/name.nbt; checked=False leaves the check to the caller (a Wonder's stage part is
        checked standing on the stages before it, by wonder_stages)."""
        os.makedirs(folder, exist_ok=True)
        path = os.path.join(folder, name + ".nbt")
        nbtlib.File(self.to_nbt()).save(path, gzipped=True)
        solid = sum(1 for n, _ in self.blocks.values() if n not in ("minecraft:air", "minecraft:structure_void"))
        under = f" (+{self.basement} below ground)" if self.basement else ""
        print(f"{name}: {self.w}x{self.h}x{self.d}{under}, {solid} blocks -> {os.path.relpath(path, ROOT)}")
        if checked:
            from check import check
            for problem in check(self):
                print(f"  CHECK {name}: {problem}")
            main = os.path.abspath(MAIN_STRUCTURES)
            if os.path.abspath(folder).startswith(main):  # our blueprints: their furniture spots (37.2)
                import furniture
                rel = os.path.relpath(os.path.join(os.path.abspath(folder), name), main)
                for problem in furniture.write(self, rel):
                    print(f"  CHECK {name}: {problem}")


class StagedBuild(Build):
    """A Wonder (ROADMAP 35.9, docs/design/M35.md), drawn whole and stage by stage: call begin(n) before drawing stage n.
    A stage only adds blocks: drawing over a block of an earlier stage is an error (the village lives beside a stage for
    days, and a builder would have to break what the stage before left finished). stage_builds() gives the Wonder as it
    stands after each stage, for wonder_stages()."""

    def __init__(self, w, h, d):
        super().__init__(w, h, d)
        self.stage = 0
        self.stage_of = {}
        self._frozen = {}

    def begin(self, n):
        self._close()
        self.stage = n

    def _close(self):
        changed = [p for p, block in self._frozen.items() if self.blocks.get(p) != block]
        assert not changed, f"stage {self.stage} draws over earlier stages at {sorted(changed)[:5]}"
        for p in self.blocks:
            self.stage_of.setdefault(p, self.stage)
        self._frozen = dict(self.blocks)

    def stage_builds(self):
        """The Wonder as it stands after each stage (each a Build of the whole size), its stair corners and connections
        worked out once on the whole, so no later stage changes an earlier block."""
        self._close()
        finish(self)
        out = []
        for n in sorted(set(self.stage_of.values())):
            s = _Finished(self.w, self.h, self.d)
            s.blocks = {p: v for p, v in self.blocks.items() if self.stage_of[p] <= n}
            s.nbt = {p: v for p, v in getattr(self, "nbt", {}).items() if p in s.blocks}
            out.append(s)
        return out


class _Finished(Build):
    """A stage of a StagedBuild: its connections are the whole Wonder's, so fill_air only adds the air."""

    def fill_air(self):
        for x in range(self.w):
            for y in range(self.h):
                for z in range(self.d):
                    self.blocks.setdefault((x, y, z), ("minecraft:air", ()))


# --- directions --------------------------------------------------------------------------------
DIRS = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
OPP = {"north": "south", "south": "north", "west": "east", "east": "west"}
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}
CCW = {v: k for k, v in CW.items()}


def step(x, z, facing, n=1):
    dx, dz = DIRS[facing]
    return x + dx * n, z + dz * n


# --- materials -------------------------------------------------------------------------------------
class Family:
    """A block family: its full block, stairs, slab, wall/fence (what exists)."""

    def __init__(self, full, stairs, slab, wall=None, fence=None, trapdoor=None):
        self.full, self.stairs, self.slab, self.wall, self.fence, self.trapdoor = full, stairs, slab, wall, fence, trapdoor


def wood(name, log=None):
    return Family(f"{name}_planks", f"{name}_stairs", f"{name}_slab", fence=f"{name}_fence", trapdoor=f"{name}_trapdoor")


OAK, SPRUCE, BIRCH, DARK_OAK, ACACIA, JUNGLE, MANGROVE, CHERRY = (wood(n) for n in
                                                                   ("oak", "spruce", "birch", "dark_oak", "acacia", "jungle", "mangrove", "cherry"))
COBBLE = Family("cobblestone", "cobblestone_stairs", "cobblestone_slab", wall="cobblestone_wall")
MOSSY_COBBLE = Family("mossy_cobblestone", "mossy_cobblestone_stairs", "mossy_cobblestone_slab", wall="mossy_cobblestone_wall")
STONE = Family("stone", "stone_stairs", "stone_slab")
STONE_BRICK = Family("stone_bricks", "stone_brick_stairs", "stone_brick_slab", wall="stone_brick_wall")
ANDESITE = Family("polished_andesite", "polished_andesite_stairs", "polished_andesite_slab")
BRICK = Family("bricks", "brick_stairs", "brick_slab", wall="brick_wall")
DEEPSLATE_TILE = Family("deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab", wall="deepslate_tile_wall")
DEEPSLATE_BRICK = Family("deepslate_bricks", "deepslate_brick_stairs", "deepslate_brick_slab", wall="deepslate_brick_wall")
SANDSTONE = Family("sandstone", "sandstone_stairs", "sandstone_slab", wall="sandstone_wall")
SMOOTH_SANDSTONE = Family("smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab")
CUT_SANDSTONE = Family("cut_sandstone", "sandstone_stairs", "cut_sandstone_slab")
MUD_BRICK = Family("mud_bricks", "mud_brick_stairs", "mud_brick_slab", wall="mud_brick_wall")
QUARTZ = Family("quartz_block", "quartz_stairs", "quartz_slab")
SMOOTH_QUARTZ = Family("smooth_quartz", "smooth_quartz_stairs", "smooth_quartz_slab")
RED_NETHER_BRICK = Family("red_nether_bricks", "red_nether_brick_stairs", "red_nether_brick_slab", wall="red_nether_brick_wall")
PRISMARINE = Family("prismarine_bricks", "prismarine_brick_stairs", "prismarine_brick_slab")
GRANITE = Family("polished_granite", "polished_granite_stairs", "polished_granite_slab")
DIORITE = Family("polished_diorite", "polished_diorite_stairs", "polished_diorite_slab")
CUT_COPPER = Family("cut_copper", "cut_copper_stairs", "cut_copper_slab")


class Mix:
    """Weighted blocks picked by position (the same spot always gets the same block): texture for stone and ground.
    Only plain blocks (no properties) — use it for full blocks."""

    def __init__(self, *weighted, seed=0):
        self.choices = [(w, n) for w, n in weighted]
        self.total = sum(w for w, _ in self.choices)
        self.seed = seed

    def at(self, x, y, z):
        h = zlib.crc32(f"{x},{y},{z},{self.seed}".encode()) % self.total
        for w, n in self.choices:
            if h < w:
                return n
            h -= w
        return self.choices[-1][1]


# Easy-to-gather mixes (a miner brings all of these)
STONE_MIX = Mix((6, "cobblestone"), (3, "stone"), (2, "andesite"), seed=1)
FOUNDATION_MIX = Mix((5, "stone_bricks"), (3, "cobblestone"), (2, "andesite"), seed=2)
BRICK_WALL_MIX = Mix((8, "stone_bricks"), (2, "cracked_stone_bricks"), seed=3)
MOSSY_BRICK_MIX = Mix((7, "stone_bricks"), (2, "mossy_stone_bricks"), (1, "cracked_stone_bricks"), seed=4)
DEEPSLATE_MIX = Mix((6, "deepslate_bricks"), (3, "deepslate_tiles"), (1, "cracked_deepslate_bricks"), seed=5)


def resolve(name, x, y, z):
    return name.at(x, y, z) if isinstance(name, Mix) else name


# --- basic pieces ----------------------------------------------------------------------------------
def stairs(b, x, y, z, fam, facing, top=False):
    """A stair of {@code fam} (a Family or a stairs id) rising towards {@code facing}; top=True hangs it upside down."""
    name = fam.stairs if isinstance(fam, Family) else fam
    b.set(x, y, z, name, facing=facing, half="top" if top else "bottom", shape="straight", waterlogged=False)


def chair(b, x, y, z, fam, toward):
    """A stair as a chair whose sitter faces {@code toward} (where the table is): a stair's facing is the side its
    backrest is on, so the stair faces the other way (B148: chairs drawn with stairs(..., toward) sat with their backs
    to the table). check.py fails any chair beside a table that doesn't face it."""
    stairs(b, x, y, z, fam, OPP[toward])


def slab(b, x, y, z, fam, top=False, double=False):
    name = fam.slab if isinstance(fam, Family) else fam
    b.set(x, y, z, name, type="double" if double else ("top" if top else "bottom"), waterlogged=False)


def log(b, x, y, z, name, axis="y"):
    b.set(x, y, z, name, axis=axis)


def box(b, x0, y0, z0, x1, y1, z1, name, **props):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                b.set(x, y, z, resolve(name, x, y, z), **props)


def walls(b, x0, z0, x1, z1, y0, y1, name, **props):
    """A ring of walls from y0 to y1 around the rectangle (x0, z0)-(x1, z1)."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    b.set(x, y, z, resolve(name, x, y, z), **props)


def posts(b, points, y0, y1, name):
    for x, z in points:
        for y in range(y0, y1 + 1):
            b.set(x, y, z, name, axis="y")


def beam_ring(b, x0, z0, x1, z1, y, name):
    """Horizontal logs round the rectangle (a top plate or a floor beam), laid along each wall."""
    for x in range(x0, x1 + 1):
        b.set(x, y, z0, name, axis="x")
        b.set(x, y, z1, name, axis="x")
    for z in range(z0 + 1, z1):
        b.set(x0, y, z, name, axis="z")
        b.set(x1, y, z, name, axis="z")


def plinth(b, x0, z0, x1, z1, name=STONE_MIX, floor=None, y=0):
    """The stone base the walls stand on (y 0: the layer just above the ground), with a floor inside."""
    walls(b, x0, z0, x1, z1, y, y, name)
    if floor:
        box(b, x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, floor)


def skirt(b, x0, z0, x1, z1, fam, y=0):
    """Stairs flaring out round the foot of the walls (x0..x1, z0..z1 is the wall rectangle): the build looks rooted."""
    for x in range(x0, x1 + 1):
        stairs(b, x, y, z0 - 1, fam, "south")
        stairs(b, x, y, z1 + 1, fam, "north")
    for z in range(z0, z1 + 1):
        stairs(b, x0 - 1, y, z, fam, "east")
        stairs(b, x1 + 1, y, z, fam, "west")


def pane(b, x, y, z, name="glass_pane"):
    b.set(x, y, z, name, north=False, south=False, east=False, west=False, waterlogged=False)


def fence(b, x, y, z, name):
    b.set(x, y, z, name, north=False, south=False, east=False, west=False, waterlogged=False)


def wall_block(b, x, y, z, name):
    b.set(x, y, z, name, north="none", south="none", east="none", west="none", up=True, waterlogged=False)


def lantern(b, x, y, z, hanging=False, soul=False):
    b.set(x, y, z, "soul_lantern" if soul else "lantern", hanging=hanging, waterlogged=False)


def trapdoor(b, x, y, z, name, facing, half="bottom", open_=False):
    b.set(x, y, z, name, facing=facing, half=half, open=open_, powered=False, waterlogged=False)


def door(b, x, y, z, name, facing, hinge="left"):
    b.set(x, y, z, name, facing=facing, half="lower", hinge=hinge, open=False, powered=False)
    b.set(x, y + 1, z, name, facing=facing, half="upper", hinge=hinge, open=False, powered=False)


# --- windows -----------------------------------------------------------------------------------------
def window(b, x, y, z, out, width=1, height=1, glass="glass_pane", shutters=None, sill=None, lintel=None, flowers=None):
    """A window in the wall at (x, y, z), {@code width} wide along the wall and {@code height} tall, looking {@code out}
    (the side the street is on). Outside it: open trapdoor shutters either side, a sill of upside-down stairs (or a
    flower box of trapdoors with potted flowers on it) under it, a lintel of upside-down stairs over it."""
    along = CW[out]  # the wall runs this way
    cells = [step(x, z, along, i) for i in range(width)]
    for cx, cz in cells:
        for dy in range(height):
            pane(b, cx, y + dy, cz, glass)
    if shutters:
        for (cx, cz), side in ((cells[0], OPP[along]), (cells[-1], along)):
            sx, sz = step(cx, cz, side)
            ox, oz = step(sx, sz, out)
            for dy in range(height):
                # an open trapdoor facing out lies flat against the wall beside the window
                trapdoor(b, ox, y + dy, oz, shutters, out, open_=True)
    for i, (cx, cz) in enumerate(cells):
        ox, oz = step(cx, cz, out)
        if flowers:
            trapdoor(b, ox, y - 1, oz, flowers[0], OPP[out], half="top")
            plant = flowers[1][i % len(flowers[1])]
            if plant:
                b.set(ox, y, oz, plant)
        elif sill:
            stairs(b, ox, y - 1, oz, sill, OPP[out], top=True)
        if lintel:
            stairs(b, ox, y + height, oz, lintel, OPP[out], top=True)


def glass_column(b, x, y0, y1, z, glass="glass_pane"):
    for y in range(y0, y1 + 1):
        pane(b, x, y, z, glass)


# --- roofs ---------------------------------------------------------------------------------------------
def _swap(axis, u, v):
    """(u along the ridge, v across it) -> (x, z)."""
    return (u, v) if axis == "x" else (v, u)


def gable_roof(b, x0, x1, z0, z1, y, fam, axis="x", gable=None, gable_at=None, ridge=None, steep=False, eave_trim=None,
               fill_under=None, closed=False):
    """A gable roof over the rectangle (x0, z0)-(x1, z1) (overhang included), its lowest row at {@code y}.
    axis: which way the ridge runs. The two slopes are stairs of {@code fam}; the top is a ridge of {@code ridge}
    (a Family: its slabs on an odd span, else a cap of stairs), or the slab of {@code fam}.
    gable: the block for the triangles closing the ends, set in at {@code gable_at} (the two positions along the ridge
    where the end walls are; default one in from each end, under the overhang). steep: two blocks up per step in (a
    stair over a full block), for a tall medieval roof. eave_trim: upside-down stairs under the lowest row (a family).
    fill_under: fill the roof's inside down to its lowest row (a solid attic floor look) with this block.
    closed: on an odd span, a full block under the ridge slab, so the ceiling seen from inside has no open block at the
    top where the two stair runs meet (B639).
    Returns the y of the ridge."""
    if axis == "x":
        u0, u1, v0, v1 = x0, x1, z0, z1
        up_face, down_face = "south", "north"
    else:
        u0, u1, v0, v1 = z0, z1, x0, x1
        up_face, down_face = "east", "west"
    if gable_at is None:
        gable_at = (u0 + 1, u1 - 1)
    rise = 2 if steep else 1
    i = 0
    top_y = y
    while v0 + i <= v1 - i:
        yy = y + i * rise
        lo, hi = v0 + i, v1 - i
        for u in range(u0, u1 + 1):
            if lo == hi:
                x, z = _swap(axis, u, lo)
                r = ridge or fam
                slab(b, x, yy, z, r)
                if closed and not steep:
                    b.set(x, yy - 1, z, fam.full)
                if steep:
                    x, z = _swap(axis, u, lo)
                    b.set(x, yy - 1, z, (ridge or fam).full)
            else:
                for v, face in ((lo, up_face), (hi, down_face)):
                    x, z = _swap(axis, u, v)
                    stairs(b, x, yy, z, fam, face)
                    if steep and i > 0:
                        b.set(x, yy - 1, z, fam.full)
        # the gable triangles under this row
        if gable:
            for u in gable_at:
                for v in range(lo + 1, hi):
                    for dy in range(rise if i > 0 else 1):
                        x, z = _swap(axis, u, v)
                        b.set(x, yy - dy, z, resolve(gable, x, yy - dy, z))
        if fill_under:
            for u in range(gable_at[0] + 1, gable_at[1]):
                for v in range(lo + 1, hi):
                    x, z = _swap(axis, u, v)
                    b.set(x, yy, z, fill_under)
        top_y = yy
        if lo == hi or lo + 1 == hi:
            if lo + 1 == hi and ridge is not None:
                # an even span ends with two stairs back to back: cap the ridge with slabs
                for u in range(u0, u1 + 1):
                    for v in (lo, hi):
                        x, z = _swap(axis, u, v)
                        slab(b, x, yy + 1, z, ridge)
                top_y = yy + 1
            break
        i += 1
    if eave_trim:
        for u in range(u0, u1 + 1):
            for v, face in ((v0, up_face), (v1, down_face)):
                x, z = _swap(axis, u, v)
                if b.get(x, y - 1, z) is None:
                    stairs(b, x, y - 1, z, eave_trim, face, top=True)
    return top_y


def hip_roof(b, x0, x1, z0, z1, y, fam, cap=None, steep=False, rings=None):
    """A hipped roof (sloping on all four sides) over (x0, z0)-(x1, z1), lowest ring at y; {@code rings}: stop after that
    many rings (for a skylight or a cupola on top). Returns the top y."""
    i = 0
    rise = 2 if steep else 1
    while x0 + i <= x1 - i and z0 + i <= z1 - i and (rings is None or i < rings):
        yy = y + i * rise
        a0, a1, c0, c1 = x0 + i, x1 - i, z0 + i, z1 - i
        if a0 == a1 or c0 == c1:
            for x in range(a0, a1 + 1):
                for z in range(c0, c1 + 1):
                    if steep and i > 0:
                        b.set(x, yy - 1, z, (cap or fam).full)
                    slab(b, x, yy, z, cap or fam)
            return yy
        for x in range(a0, a1 + 1):
            stairs(b, x, yy, c0, fam, "south")
            stairs(b, x, yy, c1, fam, "north")
            if steep and i > 0:
                b.set(x, yy - 1, c0, fam.full)
                b.set(x, yy - 1, c1, fam.full)
        for z in range(c0 + 1, c1):
            stairs(b, a0, yy, z, fam, "east")
            stairs(b, a1, yy, z, fam, "west")
            if steep and i > 0:
                b.set(a0, yy - 1, z, fam.full)
                b.set(a1, yy - 1, z, fam.full)
        if a0 + 1 == a1 or c0 + 1 == c1:
            for x in range(a0, a1 + 1):
                for z in range(c0, c1 + 1):
                    slab(b, x, yy + 1, z, cap or fam)
            return yy + 1
        i += 1
    return y + i * rise


def roofs(b, *draws):
    """Roofs that run into each other (a cross gable, a lower wing): each of {@code draws} draws one roof on a Build.
    Where two cover the same column the one reaching higher wins (ties: the later one) and the other's blocks there are
    left out, so a lower ridge dies into the higher slope, the valleys meet, and nothing pokes through or hangs inside."""
    layers = []
    for draw in draws:
        t = Build(b.w, b.h, b.d)
        draw(t)
        tops = {}
        for (x, y, z) in t.blocks:
            tops[(x, z)] = max(y, tops.get((x, z), -1))
        layers.append((t.blocks, tops))
    winner = {}
    for i, (_, tops) in enumerate(layers):
        for col, top in tops.items():
            if col not in winner or top >= layers[winner[col]][1][col]:
                winner[col] = i
    for i, (blocks, _) in enumerate(layers):
        for (x, y, z), block in blocks.items():
            if winner[(x, z)] == i:
                b.blocks[(x, y, z)] = block


SLOT_SKIP = ("_stairs", "_slab", "door", "lantern", "ladder", "torch", "_sign", "banner", "button", "lever", "carpet",
             "potted_", "flower_pot", "bed", "chest", "barrel", "campfire", "bell", "chain", "scaffolding", "leaves")


def seal_slots(b):
    """Closes the slot where a lower roof runs in under a higher one's overhang (a wing under the main roof's eave or
    gable verge, or the main roof under a taller wing's): the wall behind is carried out to the overhang's edge, block
    for block (its infill, posts and windows), from the lower roof up to the overhang, so the two roofs meet at a wall
    instead of leaving a dark gap of open blocks between them (holes.py, "meet"). Run by fill_air."""
    from holes import scan
    keep_open = getattr(b, "open_slots", set())  # columns (x, z) left open on purpose (holes.ALLOW says why)
    for _ in range(3):  # an overhang two deep takes two passes
        cells = {p: blk[0] for p, blk in b.blocks.items()}
        slots = scan((b.w, b.h, b.d), cells, only_slots=True)
        if not slots:
            return
        filling = {(x, z): (down, up) for (x, _, z), _, down, up in slots if (x, z) not in keep_open}

        def walled(x, yy, z):
            """Whether (x, yy, z) holds a block, or will once this pass fills its slot."""
            if b.blocks.get((x, yy, z), ("minecraft:air",))[0] not in ("minecraft:air", "minecraft:cave_air"):
                return True
            span = filling.get((x, z))
            return span is not None and span[0] < yy < span[1]
        for (x, y, z), (dx, dz), down, up in slots:
            if (x, z) in keep_open:
                continue
            column = range(down + 1, up)
            if any(b.blocks.get((x, yy, z), ("minecraft:air",))[0] not in ("minecraft:air", "minecraft:cave_air")
                   for yy in column):
                continue  # filled already this pass (another cell of the same column)
            wall = [b.blocks.get((x + dx, yy, z + dz)) for yy in column]
            usable = [w for w in wall if w and w[0] != "minecraft:air" and not any(t in w[0] for t in SLOT_SKIP)]
            if not usable:
                continue
            plain = [w for w in usable if not any(t in w[0] for t in ("_log", "pane", "glass", "_wood", "_bars"))]
            # The commonest block of the wall; a tie goes to the lowest (dict.fromkeys keeps the wall's order: a set's
            # order depends on PYTHONHASHSEED, so ties once came out differently run to run, e.g. ranch_2's gable)
            fallback = max(dict.fromkeys(plain or usable), key=(plain or usable).count)
            for yy, w in zip(column, wall):
                # A window's glass is carried out only between blocks: a pane or bars with air beside it in the slot
                # joins the window behind it and nothing else, a thin strip ending in the air (B648), so the wall's
                # plain block closes the slot there instead
                loose = w is not None and any(t in w[0] for t in ("pane", "_bars")) \
                    and not (walled(x + dz, yy, z + dx) and walled(x - dz, yy, z - dx))
                b.blocks[(x, yy, z)] = w if w in usable and not loose else fallback


def full_of(block_id):
    """The full block of the family a stair or slab id belongs to (deepslate_tile_stairs -> deepslate_tiles): the kit's
    families first, then the vanilla naming rules."""
    name = block_id.split(":")[-1]
    ns = block_id.split(":")[0] if ":" in block_id else "minecraft"
    for fam in [v for v in globals().values() if isinstance(v, Family)]:
        if name in (fam.stairs, fam.slab) and isinstance(fam.full, str):
            return fam.full if ":" in fam.full else f"{ns}:{fam.full}"
    base = name[:-len("_stairs")] if name.endswith("_stairs") else name[:-len("_slab")] if name.endswith("_slab") else name
    if base in ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "crimson", "warped",
                "bamboo"):
        return f"{ns}:{base}_planks"
    for end in ("brick", "tile"):
        if base.endswith(end):
            return f"{ns}:{base}s"
    if base in ("quartz", "purpur"):
        return f"{ns}:{base}_block"
    return f"{ns}:{base}"


def seal_peeks(b):
    """Fills the cells holes.py calls "peek": shut in under a roof, and seen from outside past the open top half of a
    roof stair beside them, where two roofs meet at a corner or a valley (a wing's roof running into the main one's
    end, a porch's into the eaves). The game shapes a stair from its neighbours (a stair in the middle of a straight
    run stays straight even with a cross roof's stair in front of it), so the corner can't close the gap by its shape:
    a full block of the roof over the cell closes it, as a builder packs the corner of a roof. Run by fill_air after
    finish (it needs the stairs' shapes). Returns whether it filled any."""
    from holes import peeks
    cells = {p: blk[0] for p, blk in b.blocks.items()}
    states = {p: dict(blk[1]) for p, blk in b.blocks.items()}
    filled = False
    for q, cover in peeks((b.w, b.h, b.d), cells, states):
        if q in b.blocks and b.blocks[q][0] == "minecraft:structure_void":
            continue  # left as the world has it, on purpose
        b.blocks[q] = (full_of(cover), ())
        filled = True
    return filled


def chimney(b, x, z, y0, y1, name=STONE_MIX, smoke=True):
    """A stone column from y0 to y1 with a campfire (smoking) or a slab on top."""
    for y in range(y0, y1 + 1):
        b.set(x, y, z, resolve(name, x, y, z))
    if smoke:
        b.set(x, y1 + 1, z, "campfire", facing="north", lit=True, signal_fire=False, waterlogged=False)


def moved_up(entity, dy):
    """A copy of a build's entity entry (pos, blockPos, nbt) {@code dy} higher, for a file that holds a basement under it."""
    out = Compound(dict(entity))
    out["pos"] = List[Double]([Double(float(v) + (dy if i == 1 else 0)) for i, v in enumerate(entity["pos"])])
    out["blockPos"] = List[Int]([Int(int(v) + (dy if i == 1 else 0)) for i, v in enumerate(entity["blockPos"])])
    nbt = Compound(dict(entity["nbt"]))
    if "TileY" in nbt:
        nbt["TileY"] = Int(int(nbt["TileY"]) + dy)
    out["nbt"] = nbt
    return out


def cellar(b, x0, z0, x1, z1, wall=MOSSY_BRICK_MIX, floor="stone_bricks", ladder=None, depth=None, beam=None,
           hatch="spruce_trapdoor"):
    """A room dug under the build (ROADMAP 26.15; the Build needs basement >= depth): a floor at y = -depth and walls
    x0..x1, z0..z1 up to y = -1, under the ground floor's own floor (its ceiling), the inside emptied (the builder digs it
    out). ladder=(x, z, facing): a ladder up from the cellar's floor to just under the ground floor's floor, against a
    wall on its back (facing is the way it faces, away from that wall), under a closed hatch in that floor (a trapdoor
    flush with it, facing as the ladder: open, the game lets a player climb on through it). With the ladder
    through an open hole and a block up above the floor, villagers walking by climbed it onto the window sill beside it
    and stuck there (B158: a Chef never got to the Kitchen's smoker); a closed hatch is floor to them. hatch=None: the old
    ladder through the floor to a block above it, for a ladder in a nook nobody walks into (one you climb out of over
    the furniture in front of it, which a hatch's top rung is too low for). beam: a log course
    under the ceiling round the walls (the top of the walls in logs, axis along each wall)."""
    depth = depth or b.basement
    assert 0 < depth <= b.basement, (depth, b.basement)
    box(b, x0, -depth, z0, x1, -depth, z1, floor)
    for y in range(-depth + 1, 0):
        b.ring(x0, z0, x1, z1, y, wall)
    if beam:
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                log(b, x, -1, z, beam, axis="x")
        for z in range(z0 + 1, z1):
            for x in (x0, x1):
                log(b, x, -1, z, beam, axis="z")
    b.cellars.append((x0 + 1, z0 + 1, x1 - 1, z1 - 1))
    if ladder:
        x, z, facing = ladder
        for y in range(-depth + 1, 0 if hatch else 2):
            b.set(x, y, z, "ladder", facing=facing, waterlogged=False)
        if hatch:
            trapdoor(b, x, 0, z, hatch, facing, half="top")


def cellar_stairs(b, x, z, facing, fam, depth=None, width=1):
    """A flight down into a cellar: the top step in the ground floor's floor (y = 0) at (x, z), then one a block lower
    each block `facing` (the way a player walks down), the last on the cellar's floor; the floor over the steps below
    the top one is opened for the head. width: steps side by side (to the right of `facing`). The run must lie inside
    the cellar's walls."""
    depth = depth or b.basement
    dx, dz = DIRS[facing]
    rx, rz = DIRS[CW[facing]]
    for i in range(depth):
        for j in range(width):
            px, pz = x + dx * i + rx * j, z + dz * i + rz * j
            stairs(b, px, -i, pz, fam, OPP[facing])
            if i >= 1:
                b.set(px, 0, pz, "air")


def lamp_post(b, x, y, z, post="spruce_fence", height=2, hanging=False):
    for dy in range(height):
        fence(b, x, y + dy, z, post)
    lantern(b, x, y + height, z)


def flower_bed(b, x0, z0, x1, z1, y, plants, soil="grass_block", edge=None):
    """A bed of flowers set into the plinth level: soil at y, plants on top (edge: trapdoors round it)."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            b.set(x, y, z, soil, **({"snowy": False} if soil == "grass_block" else {}))
            p = plants[zlib.crc32(f"{x},{z}".encode()) % len(plants)]
            if p:
                b.set(x, y + 1, z, p)


# --- finishing: what the game works out from the neighbours -------------------------------------------------
FULL_HINTS = ("planks", "_log", "_wood", "bricks", "cobblestone", "concrete", "terracotta", "_wool", "stone", "deepslate",
              "sandstone", "tiles", "quartz_block", "smooth_quartz", "andesite", "diorite", "granite", "_block", "calcite",
              "tuff", "mud", "prismarine", "glass", "bookshelf", "barrel", "crafting_table", "furnace", "smoker", "dirt",
              "grass_block", "podzol", "basalt", "blackstone", "copper", "ice", "clay", "melon", "pumpkin", "loom", "target",
              "cartography_table", "fletching_table", "smithing_table", "log")
NOT_FULL = ("stairs", "slab", "fence", "pane", "_wall", "door", "torch", "lantern", "carpet", "_bed", "chest", "button",
            "pressure_plate", "sign", "banner", "rail", "ladder", "vine", "flower", "sapling", "potted", "flower_pot",
            "campfire", "bars", "chain", "candle", "bell", "skull", "head", "anvil", "scaffolding", "cauldron", "composter",
            "grindstone", "lectern", "stonecutter", "brewing", "enchanting", "bush", "berry", "kelp", "air", "void",
            "jigsaw", "_rod", "path", "farmland", "cake", "dripleaf", "azalea", "petals", "hopper", "cactus", "amethyst_cluster")


def name_of(block):
    return block[0].split(":")[-1] if block else "air"


def is_full(block):
    """Whether the block is a full cube (what fences, panes and walls join onto): a good guess from its id."""
    n = name_of(block)
    if block and not block[0].startswith("minecraft:"):
        return False
    return not any(h in n for h in NOT_FULL) and any(h in n for h in FULL_HINTS)


def props_of(block):
    return dict(block[1]) if block else {}


def _stair_shape(b, x, y, z, block):
    p = props_of(block)
    facing, half = p["facing"], p["half"]

    def stair_at(dirn):
        dx, dz = DIRS[dirn]
        other = b.get(x + dx, y, z + dz)
        if other and name_of(other).endswith("_stairs") and props_of(other).get("half") == half:
            return props_of(other)
        return None

    def can_take(dirn):
        o = stair_at(dirn)
        return not (o and o["facing"] == facing)

    behind = stair_at(facing)
    if behind and behind["facing"] not in (facing, OPP[facing]) and can_take(OPP[behind["facing"]]):
        return "outer_left" if behind["facing"] == CCW[facing] else "outer_right"
    front = stair_at(OPP[facing])
    if front and front["facing"] not in (facing, OPP[facing]) and can_take(front["facing"]):
        return "inner_left" if front["facing"] == CCW[facing] else "inner_right"
    return "straight"


def _connects(b, x, y, z, dirn, kind):
    dx, dz = DIRS[dirn]
    other = b.get(x + dx, y, z + dz)
    if not other:
        return False
    n = name_of(other)
    if kind == "fence":
        wood_fence = "nether_brick" not in name_of(b.get(x, y, z))
        if n.endswith("_fence"):
            return ("nether_brick" in n) != wood_fence
        if n.endswith("_fence_gate"):
            return props_of(other).get("facing") in (CW[dirn], CCW[dirn])
        return is_full(other)
    if kind == "pane":
        return n.endswith("glass_pane") or n == "iron_bars" or n.endswith("_wall") or is_full(other)
    if kind == "wall":
        if n.endswith("_wall") or n.endswith("glass_pane") or n == "iron_bars":
            return True
        if n.endswith("_fence_gate"):
            return props_of(other).get("facing") in (CW[dirn], CCW[dirn])
        return is_full(other)
    return False


def finish(b):
    """Works out what the game would from the neighbours: stair corners, and which way fences, panes, bars and walls
    join up. The game does this again as a builder places blocks; here it makes the saved blueprint (and previews)
    look right."""
    updates = {}
    for (x, y, z), block in list(b.blocks.items()):
        n = name_of(block)
        p = props_of(block)
        if n.endswith("_stairs"):
            p["shape"] = _stair_shape(b, x, y, z, block)
        elif n.endswith("_fence") or n.endswith("glass_pane") or n == "iron_bars":
            kind = "fence" if n.endswith("_fence") else "pane"
            for d in DIRS:
                p[d] = _connects(b, x, y, z, d, kind)
        elif n.endswith("_wall") and "wall_" not in n and n not in ("wall_torch",):
            sides = {d: _connects(b, x, y, z, d, "wall") for d in DIRS}
            above = b.get(x, y + 1, z)
            tall = above is not None and (is_full(above) or name_of(above).endswith("_wall"))
            for d, c in sides.items():
                p[d] = ("tall" if tall else "low") if c else "none"
            straight = (sides["north"] and sides["south"] and not sides["east"] and not sides["west"]) or \
                       (sides["east"] and sides["west"] and not sides["north"] and not sides["south"])
            p["up"] = not straight or (above is not None and name_of(above) not in ("air",) and not tall)
        else:
            continue
        updates[(x, y, z)] = (block[0], tuple(sorted((k, str(v).lower()) for k, v in p.items())))
    b.blocks.update(updates)
    settle_paths(b)


def settle_paths(b):
    """A dirt path or farmland under a block the game counts as solid (a fence post, a wall, a tent's wool, a bed) is
    turned to dirt by the game as soon as that block is placed (B539), so the blueprint draws what will stay: grass
    where grass is beside it, coarse dirt on a trodden path, dirt under farmland's crops."""
    from check import turns_path
    for (x, y, z), block in list(b.blocks.items()):
        n = name_of(block)
        if n not in ("dirt_path", "farmland") or not turns_path(b.get(x, y + 1, z)):
            continue
        if n == "farmland":
            b.set(x, y, z, "dirt")
        elif any(b.get(x + dx, y, z + dz) and name_of(b.get(x + dx, y, z + dz)) == "grass_block"
                 for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            b.set(x, y, z, "grass_block", snowy=False)
        else:
            b.set(x, y, z, "coarse_dirt")


# --- Wonders (ROADMAP 35.3) ----------------------------------------------------------------------------------------
WONDER_PART = 48  # a Wonder's part is at most this many blocks a side
DEVCLIENT_STRUCTURES = os.path.join(ROOT, "src/devclient/resources/data/aliveworkplace_test/structure")


def wonder_stages(namespace, wonder, stages, folders, part=WONDER_PART):
    """Saves a Wonder drawn one stage at a time. `stages` holds one Build per stage, each the whole Wonder as it stands
    once that stage is done (stage 2 is stage 1 with more drawn on it), all the same size. Stage 1 is saved whole, air
    included, so the builders clear the site; every later stage only as what it adds to the stages before it (a block
    where there was air, or a different block), never air where an earlier stage put a block (that's an error). Each
    stage is cut into parts of at most `part` a side, trimmed to their blocks, saved as
    `<folder>/wonder/<wonder>/stage_<n>_<i>.nbt` in every folder of `folders`. Returns the Wonder file's "stages" parts:
    [[{"part": "<namespace>:wonder/<wonder>/stage_<n>_<i>", "offset": [x, y, z]}, ...], ...]."""
    sizes = {(s.w, s.h, s.d) for s in stages}
    assert len(sizes) == 1, f"{wonder}: every stage must be drawn at the same size, got {sizes}"
    before = {}
    out = []
    for n, stage in enumerate(stages, start=1):
        stage.fill_air()
        added = {}
        for pos, block in stage.blocks.items():
            had = before.get(pos)
            if block[0] == "minecraft:air":
                if had is not None and had[0] != "minecraft:air":
                    raise ValueError(f"{wonder}: stage {n} takes away the block stage {n - 1} put at {pos}")
                if n == 1:
                    added[pos] = block
            elif had != block:
                added[pos] = block
        for pos, block in before.items():
            if block[0] != "minecraft:air" and stage.blocks.get(pos, ("minecraft:air", ()))[0] == "minecraft:air":
                raise ValueError(f"{wonder}: stage {n} takes away the block an earlier stage put at {pos}")
        cells = {}
        for (x, y, z), block in added.items():
            cells.setdefault((x // part, y // part, z // part), {})[(x, y, z)] = block
        parts = []
        for i, cell in enumerate(sorted(cells, key=lambda c: (c[1], c[2], c[0]))):
            blocks = cells[cell]
            lo = [min(p[k] for p in blocks) for k in range(3)]
            hi = [max(p[k] for p in blocks) for k in range(3)]
            b = Build(hi[0] - lo[0] + 1, hi[1] - lo[1] + 1, hi[2] - lo[2] + 1)
            for (x, y, z), block in blocks.items():
                b.blocks[(x - lo[0], y - lo[1], z - lo[2])] = block
            nbt = getattr(stage, "nbt", {})
            b.nbt = {(x - lo[0], y - lo[1], z - lo[2]): v for (x, y, z), v in nbt.items() if (x, y, z) in blocks}
            name = f"stage_{n}_{i}"
            for folder in folders:
                path = os.path.join(folder, "wonder", wonder)
                os.makedirs(path, exist_ok=True)
                nbtlib.File(b.to_nbt()).save(os.path.join(path, name + ".nbt"), gzipped=True)
            solid = sum(1 for nm, _ in blocks.values() if nm != "minecraft:air")
            print(f"{wonder} {name}: {b.w}x{b.h}x{b.d} at {lo}, {solid} blocks")
            parts.append({"part": f"{namespace}:wonder/{wonder}/{name}", "offset": lo})
        out.append(parts)
        before = dict(stage.blocks)
    return out


# --- Furnished rooms (ROADMAP 37.4): vanilla pieces that read as furniture, each recorded as a furniture spot ---------
# Every helper draws the piece in vanilla blocks and records it with Build.furniture, so a server with a furniture mod
# builds that mod's piece there (37.3's tables) and one without builds these blocks. `facing` is the way a person
# using the piece faces: into the room for anything standing against a wall. Each refuses a cell already drawn, so a
# piece never lands on a bed, a job block or a chest by accident.

def _free(b, *cells):
    for c in cells:
        got = b.blocks.get(c)
        assert got is None or got[0] == "minecraft:air", f"furniture over {got} at {c}"


def nightstand(b, x, y, z, facing, wood):
    """A bedside table: an upside-down slab beside the bed's head."""
    _free(b, (x, y, z))
    b.set(x, y, z, f"{wood}_slab", type="top", waterlogged=False)
    b.furniture("nightstand", x, y, z, facing, wood=wood)


def floor_lamp(b, x, y, z, wood, facing="north"):
    """A standing lamp: a lantern on a fence post."""
    _free(b, (x, y, z), (x, y + 1, z))
    b.set(x, y, z, f"{wood}_fence")
    lantern(b, x, y + 1, z)
    b.furniture("lamp", x, y, z, facing, cells=((0, 0, 0), (0, 1, 0)), wood=wood)


def couch(b, x, y, z, facing, wood, colour, length=2, arms=True):
    """A couch of `length` seats along the wall behind it, the sitters facing `facing`: stair seats with their backs to
    the wall and, with `arms`, an open trapdoor at each end. Each seat is a couch spot (an arm goes with its end seat)."""
    along = CW[facing]
    ax, az = DIRS[along]
    seats = [(x + ax * i, z + az * i) for i in range(length)]
    for i, (sx, sz) in enumerate(seats):
        _free(b, (sx, y, sz))
        stairs(b, sx, y, sz, wood_family(wood), OPP[facing])
        cells = [(0, 0, 0)]
        if arms and i in (0, length - 1):
            for end, d in ((0, -1), (length - 1, 1)):
                if i == end:
                    arm = (sx + ax * d, sz + az * d)
                    _free(b, (arm[0], y, arm[1]))
                    trapdoor(b, arm[0], y, arm[1], f"{wood}_trapdoor", along if d < 0 else OPP[along], open_=True)
                    cells.append((arm[0] - sx, 0, arm[1] - sz))
        b.furniture("couch", sx, y, sz, facing, cells=cells, wood=wood, colour=colour)


def wood_family(wood):
    return wood if isinstance(wood, Family) else globals().get(wood.upper()) or Family(f"{wood}_planks", f"{wood}_stairs", f"{wood}_slab")


def shelf(b, x, y, z, facing, wood, crockery="flower_pot"):
    """A wall shelf at head height: an upside-down slab on the wall, crockery (a flower pot, the vanilla cup) on it."""
    _free(b, (x, y, z))
    b.set(x, y, z, f"{wood}_slab", type="top", waterlogged=False)
    b.furniture("shelf", x, y, z, facing, wood=wood)
    if crockery:
        _free(b, (x, y + 1, z))
        b.set(x, y + 1, z, crockery)
        b.furniture("crockery", x, y + 1, z, facing, wood=wood, colour="white")


def picture(b, x, y, z, facing, colour, wood):
    """A picture on the wall behind (x, y, z): a wall banner, the plain cloth of the vanilla build."""
    _free(b, (x, y, z))
    b.set(x, y, z, f"{colour}_wall_banner", facing=facing)
    b.furniture("picture", x, y, z, facing, wood=wood, colour=colour)


def blinds(b, x, y, z, facing, wood):
    """Blinds rolled up at the top of a window, inside: a closed upper trapdoor against the window head. `facing`: into
    the room."""
    _free(b, (x, y, z))
    trapdoor(b, x, y, z, f"{wood}_trapdoor", facing, half="top")
    b.furniture("blinds", x, y, z, facing, wood=wood)


def clock(b, x, y, z, facing, wood):
    """A grandfather clock against a wall: a stripped log case under a trapdoor hood."""
    _free(b, (x, y, z), (x, y + 1, z))
    log(b, x, y, z, f"stripped_{wood}_log")
    trapdoor(b, x, y + 1, z, f"{wood}_trapdoor", facing, half="bottom")
    b.furniture("clock", x, y, z, facing, cells=((0, 0, 0), (0, 1, 0)), wood=wood)


# --- Furnished workplaces (ROADMAP 37.5): desks, benches, counters and decorations in the workplaces ------------------
# The same rule as the homes: every piece is vanilla blocks recorded as a furniture spot. A job block, a bed or a
# container is never part of a spot (FurnishedWorkplacesGameTests checks every shipped spot), so a furniture mod's piece
# can never stand where a job reads its block or its chest.
NOT_FURNITURE = ("chest", "barrel", "_shulker_box", "lectern", "_bed", "smoker", "furnace", "brewing_stand", "composter",
                 "cartography_table", "crafting_table", "loom", "fletching_table", "smithing_table", "stonecutter",
                 "grindstone", "cauldron", "anvil", "bell", "hopper", "dispenser", "dropper", "aliveworkplace:",
                 "cobblemon:")


def _job_free(b, cells):
    for c in cells:
        got = b.blocks.get(c)
        assert got is not None and got[0] != "minecraft:air", f"furniture spot on nothing at {c}"
        assert not any(n in got[0] for n in NOT_FURNITURE), f"furniture spot over a job block, bed or container {got} at {c}"


def furnish(b, kind, x, y, z, facing, cells=((0, 0, 0),), wood=None, colour=None):
    """Records a piece already drawn at (x, y, z) (a counter of planks under a slab, a row of stair benches) as a
    furniture spot, refusing one that would cover a job block, a bed or a container."""
    _job_free(b, [(x + dx, y + dy, z + dz) for dx, dy, dz in cells])
    b.furniture(kind, x, y, z, facing, cells=cells, wood=wood, colour=colour)


def desk(b, x, y, z, facing, wood):
    """A writing desk: an upside-down slab, the person at it facing `facing`."""
    _free(b, (x, y, z))
    b.set(x, y, z, f"{wood}_slab", type="top", waterlogged=False)
    b.furniture("desk", x, y, z, facing, wood=wood)


def bench_seat(b, x, y, z, facing, wood):
    """One seat of a bench: a stair whose sitter faces `facing` (its back the other way)."""
    _free(b, (x, y, z))
    stairs(b, x, y, z, wood_family(wood), OPP[facing])
    b.furniture("bench", x, y, z, facing, wood=wood)


def trellis(b, x, y, z, facing, wood, height=2):
    """A trellis on a wall: open trapdoors flat against the wall behind, `height` high, a lattice for climbers.
    `facing`: away from the wall."""
    cells = [(x, y + i, z) for i in range(height)]
    _free(b, *cells)
    for c in cells:
        trapdoor(b, *c, f"{wood}_trapdoor", facing, open_=True)
    b.furniture("trellis", x, y, z, facing, cells=tuple((0, i, 0) for i in range(height)), wood=wood)


def hanging_pot(b, x, y, z, plant="potted_fern", wood="oak"):
    """A hanging pot: a chain from the beam above (x, y + 1, z) holding a potted plant under it at y - 1. The spot is the
    chain's cell (a modded hanging pot hangs from the beam there)."""
    _free(b, (x, y, z), (x, y - 1, z))
    b.set(x, y, z, "chain", axis="y", waterlogged=False)
    b.set(x, y - 1, z, plant)
    b.furniture("hanging_pot", x, y, z, "north", cells=((0, 0, 0), (0, -1, 0)), wood=wood)
