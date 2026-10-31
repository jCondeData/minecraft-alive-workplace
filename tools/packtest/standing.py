#!/usr/bin/env python3
"""Do the finished builds in a saved world stand as drawn? (B690: the soak's skipped counts aren't holes.)

    python3 tools/packtest/standing.py <world folder> [structure ...]

Reads the world's finished builds (data/aliveworkplace_build_sites.dat), and for each compares its blueprint
(src/main/resources/data/aliveworkplace/structure/<id>.nbt, its modded copy or a later look, whichever fits best)
with the world's blocks, by block id (states differ by neighbours). A build with a basement (26.15) is drawn with its
floor at the template's y = 0 and built that many blocks down, so each build is tried from 0 to 8 blocks lower and
the best fit is taken. Prints one line per build with holes (air where the drawing has a block) or other blocks, and
a "Standing:" summary line. Informational: it always exits 0. No dependencies (a small NBT reader is below).
"""
import glob
import gzip
import io
import os
import struct
import sys
import zlib

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
STRUCTURES = os.path.join(ROOT, "src/main/resources/data/aliveworkplace/structure")
AIRS = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:structure_void"}


# ---- NBT --------------------------------------------------------------------------------------------------------------

def _read(f, tag):
    if tag == 1:
        return struct.unpack(">b", f.read(1))[0]
    if tag == 2:
        return struct.unpack(">h", f.read(2))[0]
    if tag == 3:
        return struct.unpack(">i", f.read(4))[0]
    if tag == 4:
        return struct.unpack(">q", f.read(8))[0]
    if tag == 5:
        return struct.unpack(">f", f.read(4))[0]
    if tag == 6:
        return struct.unpack(">d", f.read(8))[0]
    if tag == 7:
        n = struct.unpack(">i", f.read(4))[0]
        return f.read(n)
    if tag == 8:
        n = struct.unpack(">H", f.read(2))[0]
        return f.read(n).decode("utf-8", "replace")
    if tag == 9:
        inner = f.read(1)[0]
        n = struct.unpack(">i", f.read(4))[0]
        return [_read(f, inner) for _ in range(n)]
    if tag == 10:
        out = {}
        while True:
            t = f.read(1)[0]
            if t == 0:
                return out
            n = struct.unpack(">H", f.read(2))[0]
            name = f.read(n).decode("utf-8", "replace")
            out[name] = _read(f, t)
    if tag == 11:
        n = struct.unpack(">i", f.read(4))[0]
        return list(struct.unpack(f">{n}i", f.read(4 * n)))
    if tag == 12:
        n = struct.unpack(">i", f.read(4))[0]
        return list(struct.unpack(f">{n}q", f.read(8 * n)))
    raise ValueError(f"NBT tag {tag}")


def nbt(data):
    f = io.BytesIO(data)
    tag = f.read(1)[0]
    n = struct.unpack(">H", f.read(2))[0]
    f.read(n)
    return _read(f, tag)


def nbt_file(path):
    with open(path, "rb") as f:
        data = f.read()
    return nbt(gzip.decompress(data) if data[:2] == b"\x1f\x8b" else data)


# ---- the world --------------------------------------------------------------------------------------------------------

class World:
    def __init__(self, folder):
        self.folder = folder
        self.regions = {}
        self.chunks = {}

    def _chunk(self, cx, cz):
        key = (cx, cz)
        if key in self.chunks:
            return self.chunks[key]
        rkey = (cx >> 5, cz >> 5)
        if rkey not in self.regions:
            path = os.path.join(self.folder, "region", f"r.{rkey[0]}.{rkey[1]}.mca")
            self.regions[rkey] = open(path, "rb").read() if os.path.exists(path) else None
        data, sections = self.regions[rkey], None
        if data:
            i = 4 * ((cx & 31) + (cz & 31) * 32)
            offset = int.from_bytes(data[i:i + 3], "big") * 4096
            if offset:
                length = int.from_bytes(data[offset:offset + 4], "big")
                raw = data[offset + 5:offset + 4 + length]
                body = zlib.decompress(raw) if data[offset + 4] == 2 else gzip.decompress(raw)
                sections = {s["Y"]: s.get("block_states") for s in nbt(body).get("sections", [])}
        self.chunks[key] = sections
        return sections

    def block(self, x, y, z):
        sections = self._chunk(x >> 4, z >> 4)
        if sections is None:
            return None
        states = sections.get(y >> 4)
        if not states:
            return "minecraft:air"
        palette = states["palette"]
        if len(palette) == 1 or "data" not in states:
            return palette[0]["Name"]
        bits = max(4, (len(palette) - 1).bit_length())
        per = 64 // bits
        index = ((y & 15) * 16 + (z & 15)) * 16 + (x & 15)
        word = states["data"][index // per] & ((1 << 64) - 1)
        return palette[(word >> ((index % per) * bits)) & ((1 << bits) - 1)]["Name"]


# ---- the builds -------------------------------------------------------------------------------------------------------

def transform(x, z, size, rotation, mirror):
    """StructureTemplate.transform with the pivot at 0, as the mod places a build."""
    if mirror == "left_right":
        z = -z
    elif mirror == "front_back":
        x = -x
    if rotation == "clockwise_90":
        return -z, x
    if rotation == "clockwise_180":
        return -x, -z
    if rotation == "counterclockwise_90":
        return z, -x
    return x, z


def compare(world, template, origin, rotation, mirror, sink):
    palette = template["palette"]
    ox, oy, oz = origin
    total = holes = other = 0
    examples = []
    for b in template["blocks"]:
        name = palette[b["state"]]["Name"]
        if name in AIRS:
            continue
        x, y, z = b["pos"]
        dx, dz = transform(x, z, template["size"], rotation, mirror)
        found = world.block(ox + dx, oy + y - sink, oz + dz)
        total += 1
        if found in AIRS:
            holes += 1
            if len(examples) < 3:
                examples.append(f"{name} at {ox + dx}, {oy + y - sink}, {oz + dz}")
        elif found != name:
            other += 1
    return total, holes, other, examples


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    folder, wanted = sys.argv[1], set(sys.argv[2:])
    data = nbt_file(os.path.join(folder, "data", "aliveworkplace_build_sites.dat"))
    finished = data.get("data", data).get("finished", [])
    world = World(folder)
    standing = 0
    checked = 0
    for entry in finished:
        sid = entry["structure"].split(":", 1)[1]
        if wanted and sid not in wanted:
            continue
        files = sorted(set(glob.glob(os.path.join(STRUCTURES, sid + ".nbt")) + glob.glob(os.path.join(STRUCTURES, sid + "_[0-9].nbt"))
                           + glob.glob(os.path.join(STRUCTURES, "modded", sid + ".nbt")) + glob.glob(os.path.join(STRUCTURES, "modded", sid + "_[0-9].nbt"))))
        if not files:
            print(f"  {sid}: no blueprint file to compare with")
            continue
        p = entry["placement"]
        origin = tuple(p["origin"])
        best = None
        for path in files:
            template = nbt_file(path)
            for sink in range(0, 9):
                total, holes, other, examples = compare(world, template, origin, p.get("rotation", "none"), p.get("mirror", "none"), sink)
                score = (holes + other, sink)
                if best is None or score < best[0]:
                    best = (score, os.path.relpath(path, STRUCTURES), sink, total, holes, other, examples)
        _, name, sink, total, holes, other, examples = best
        checked += 1
        if holes == 0 and other == 0:
            standing += 1
        else:
            print(f"  {sid} at {origin[0]}, {origin[1]}, {origin[2]} ({name}{', ' + str(sink) + ' down' if sink else ''}): "
                  f"{holes} holes and {other} other blocks of {total}" + (f"; e.g. {'; '.join(examples)}" if examples else ""))
    print(f"Standing: {standing} of {checked} finished builds stand as drawn")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:  # informational: never fails the run that called it
        print(f"Standing: not checked ({e})")
