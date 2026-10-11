"""
The blueprint styles (data/aliveworkplace/blueprint_styles/*.json) applied to a drawn Build, for renders: the same
block swaps BlueprintStyles makes in the game (the first rule whose result is a real block wins; a block no rule turns
into a real one stays). Python can't ask the game which blocks are real, so NOT_REAL lists the results we know are not
blocks on 1.21.1.

    tools/blueprints/render/preview.sh front "styled(townhouse(), 'cherry')"
"""
import json
import os
import re

from kit import ROOT, Build

STYLES = os.path.join(ROOT, "src/main/resources/data/aliveworkplace/blueprint_styles")
NOT_REAL = {"minecraft:copper_lantern", "minecraft:cut_sandstone_stairs", "minecraft:dark_oak_wall"}
# Blocks that stand on an axis (pillars): the game gives one a source block lacked its default, axis=y, so a stone
# chimney turned deepslate stays a column. Without it the renderer has no model for the block and draws nothing
# (ROADMAP B106: dark_oak chimneys looked like floating tops).
PILLARS = re.compile(r"minecraft:(.+_(log|wood|stem|hyphae)|deepslate|basalt|polished_basalt|quartz_pillar|purpur_pillar"
                     r"|bone_block|hay_block|muddy_mangrove_roots|ochre_froglight|verdant_froglight|pearlescent_froglight)")


def _rules(style):
    with open(os.path.join(STYLES, style + ".json")) as f:
        return [(re.compile(r["from"]), r["to"]) for r in json.load(f)["replace"]]


def _swap(rules, block):
    for pattern, to in rules:
        m = pattern.fullmatch(block)
        if m:
            out = re.sub(r"\$(\d)", lambda g: m.group(int(g.group(1))) or "", to)
            if out not in NOT_REAL:
                return out
    return block


def styled(b, style):
    """A copy of Build {@code b} in {@code style} (cherry, dark_oak, grand, sandstone, stonework, apricorn)."""
    rules = _rules(style)
    out = Build(b.w, b.h, b.d)
    for pos, (name, props) in b.blocks.items():
        to = _swap(rules, name)
        if to != name and PILLARS.fullmatch(to) and not any(k == "axis" for k, _ in props):
            props = tuple(sorted(props + (("axis", "y"),)))
        out.blocks[pos] = (to, props)
    out.nbt = dict(getattr(b, "nbt", {}))
    return out
