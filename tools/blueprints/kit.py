"""
The blueprint kit: Build (a structure being drawn, saved as a vanilla structure .nbt) and the building techniques that
make a build look built rather than boxy — see STYLE.md next to this file.

Conventions for every blueprint (the mod relies on these):
  * x = width, y = height, z = depth; the FRONT of the build is the z = 0 side (north).
  * y = 0 is the floor layer; it sits on top of the block the player clicks.
  * Every position inside the bounds is listed (air included) so builders clear the site.
"""
import os
import zlib
import nbtlib
from nbtlib import Compound, List, Int, String

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
MAIN_STRUCTURES = os.path.join(ROOT, "src/main/resources/data/aliveworkplace/structure")
TEST_STRUCTURES = os.path.join(ROOT, "src/gametest/resources/data/aliveworkplace_test/structure")
TEST_AREAS = os.path.join(ROOT, "src/gametest/resources/data/aliveworkplace_test/gametest/structure")
COMPAT_AREAS = os.path.join(ROOT, "src/compattest/resources/data/aliveworkplace_compat/gametest/structure")
DATA_VERSION = 3955  # Minecraft 1.21.1


class Build:
    def __init__(self, w, h, d):
        self.w, self.h, self.d = w, h, d
        self.blocks = {}

    def set(self, x, y, z, name, **props):
        assert 0 <= x < self.w and 0 <= y < self.h and 0 <= z < self.d, (x, y, z, name)
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

    def set_nbt(self, x, y, z, nbt):
        """Block-entity data for the block at (x, y, z) (set the block first)."""
        self.nbt = getattr(self, "nbt", {})
        self.nbt[(x, y, z)] = nbt

    def grow(self, w, h, d):
        """A bigger copy of this build (air left out), for an upgrade drawn on top of its base."""
        b = Build(w, h, d)
        for pos, block in self.blocks.items():
            if block[0] != "minecraft:air":
                b.blocks[pos] = block
        b.nbt = dict(getattr(self, "nbt", {}))
        return b

    def clear(self, x0, y0, z0, x1, y1, z1):
        """Takes blocks out again (they become air when the build is saved)."""
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.blocks.pop((x, y, z), None)

    def fill_air(self):
        finish(self)  # stair corners and fence/pane/wall connections, as the game would work them out
        for x in range(self.w):
            for y in range(self.h):
                for z in range(self.d):
                    self.blocks.setdefault((x, y, z), ("minecraft:air", ()))

    def door(self, x, y, z, name, facing, hinge="left"):
        self.set(x, y, z, name, facing=facing, half="lower", hinge=hinge, open=False, powered=False)
        self.set(x, y + 1, z, name, facing=facing, half="upper", hinge=hinge, open=False, powered=False)

    def bed(self, x, y, z, color, facing):
        """(x, y, z) is the foot; the head is one block towards `facing`."""
        dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[facing]
        self.set(x, y, z, f"{color}_bed", facing=facing, part="foot", occupied=False)
        self.set(x + dx, y, z + dz, f"{color}_bed", facing=facing, part="head", occupied=False)

    def to_nbt(self):
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
            entry = Compound({"pos": List[Int]([Int(x), Int(y), Int(z)]), "state": Int(index[key])})
            extra = getattr(self, "nbt", {}).get((x, y, z))
            if extra is not None:
                entry["nbt"] = extra
            blocks.append(entry)
        return Compound({
            "DataVersion": Int(DATA_VERSION),
            "size": List[Int]([Int(self.w), Int(self.h), Int(self.d)]),
            "palette": List[Compound](palette),
            "blocks": List[Compound](blocks),
            "entities": List[Compound]([]),
        })

    def save(self, folder, name):
        os.makedirs(folder, exist_ok=True)
        path = os.path.join(folder, name + ".nbt")
        nbtlib.File(self.to_nbt()).save(path, gzipped=True)
        solid = sum(1 for n, _ in self.blocks.values() if n != "minecraft:air")
        print(f"{name}: {self.w}x{self.h}x{self.d}, {solid} blocks -> {os.path.relpath(path, ROOT)}")
        from check import check
        for problem in check(self):
            print(f"  CHECK {name}: {problem}")



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
               fill_under=None):
    """A gable roof over the rectangle (x0, z0)-(x1, z1) (overhang included), its lowest row at {@code y}.
    axis: which way the ridge runs. The two slopes are stairs of {@code fam}; the top is a ridge of {@code ridge}
    (a Family: its slabs on an odd span, else a cap of stairs), or the slab of {@code fam}.
    gable: the block for the triangles closing the ends, set in at {@code gable_at} (the two positions along the ridge
    where the end walls are; default one in from each end, under the overhang). steep: two blocks up per step in (a
    stair over a full block), for a tall medieval roof. eave_trim: upside-down stairs under the lowest row (a family).
    fill_under: fill the roof's inside down to its lowest row (a solid attic floor look) with this block.
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


def chimney(b, x, z, y0, y1, name=STONE_MIX, smoke=True):
    """A stone column from y0 to y1 with a campfire (smoking) or a slab on top."""
    for y in range(y0, y1 + 1):
        b.set(x, y, z, resolve(name, x, y, z))
    if smoke:
        b.set(x, y1 + 1, z, "campfire", facing="north", lit=True, signal_fire=False, waterlogged=False)


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
