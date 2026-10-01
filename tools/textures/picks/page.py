#!/usr/bin/env python3
"""The owner's picker page for the item icons (ROADMAP 21.1b): every item's versions side by side, at the size the game
shows them and enlarged, one pick and a note per item. Picks are saved in the published page's own store (the `db`
capability, collection `picks`, one document per item: {"pick": "v3", "note": "...", "round": 1}), where the chat
reads them back.

    python3 tools/textures/picks/sheets.py; python3 tools/textures/picks/papers.py; python3 tools/textures/picks/tools.py
    python3 tools/textures/picks/page.py          # -> build/texture-picks/index.html (publish it as an Artifact)

Version 1 of each item is its icon in the game today (textures/item/<name>.png); the others come from the recipes in
this folder (build/texture-picks/<item>/v<n>.png and captions.json).
"""
import base64
import html
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
PICKS = ROOT / "build" / "texture-picks"
CURRENT = ROOT / "src/main/resources/assets/aliveworkplace/textures/item"
ROUND = 1

# (group, [(item, name, what it does, the current icon in a few words)])
ITEMS = [
    ("Building", [
        ("blueprint", "Blueprint", "Hand it to a builder and they build it. Also the mod's icon.", "Blue sheet with a house sketch"),
        ("blank_blueprint", "Blank Blueprint", "Empty blueprint paper: copies and scans are made from it.", "Plain blue sheet"),
        ("scan_tool", "Scan Tool", "Click two corners of your own build to save it as a blueprint.", "Blue wand with a violet crystal"),
        ("shape_planner", "Shape Planner", "Plan walls, towers and domes that become a blueprint.", "Sheet with a dome and a brass compass"),
    ]),
    ("Village and guards", [
        ("village_ledger", "Village Ledger", "Opens your Village Hall's screens from anywhere.", "Green ledger with leather corners"),
        ("patrol_map", "Patrol Map", "Mark up to eight spots for a guard's patrol.", "Parchment map, red route, blue flags"),
        ("rally_banner", "Rally Banner", "Enlist guards to follow you and fight beside you.", "Red war banner with a gold crown"),
    ]),
    ("Workers", [
        ("quarry_marker", "Quarry Marker", "Mark a quarry's corners and give it to a miner.", "Stake with a red and white flag"),
        ("field_marker", "Field Marker", "Mark a field, tree farm or orchard for its worker.", "Stake with a green flag"),
        ("delivery_note", "Delivery Note", "A courier route for a postman, from one chest to another.", "Letter sealed with red wax"),
        ("price_tag", "Price Tag", "Sets a price on your Shop Counter.", "White tag on a red string, gold coin"),
        ("travel_ticket", "Travel Ticket", "Bought from a ferryman: takes you to another travel post.", "Gold card ticket with a cream stub"),
    ]),
    ("Starting out", [
        ("settlers_wagon", "Settler's Wagon", "Place it on open ground: two settlers make camp there.", "Covered wagon, three-quarter view"),
    ]),
]


def data_uri(path):
    return "data:image/png;base64," + base64.b64encode(path.read_bytes()).decode()


def versions(item, current):
    out = [{"id": "v1", "caption": current, "current": True, "src": data_uri(CURRENT / f"{item}.png")}]
    captions = json.loads((PICKS / item / "captions.json").read_text())
    for v in sorted(captions):
        out.append({"id": v, "caption": captions[v], "current": False, "src": data_uri(PICKS / item / f"{v}.png")})
    return out


def card(item, name, what, current):
    tiles = []
    for v in versions(item, current):
        label = "Today" if v["current"] else f"Version {v['id'][1:]}"
        tiles.append(f"""
        <button type="button" class="tile" id="{item}-{v['id']}" data-item="{item}" data-version="{v['id']}" aria-pressed="false">
          <span class="big"><img src="{v['src']}" alt="{html.escape(name)}, {html.escape(v['caption'])}" width="16" height="16"></span>
          <span class="row">
            <span class="slot" aria-hidden="true"><img src="{v['src']}" alt="" width="16" height="16"></span>
            <span class="label"><span class="vname">{label}</span><span class="cap">{html.escape(v['caption'])}</span></span>
          </span>
          <span class="check" aria-hidden="true">Picked</span>
        </button>""")
    return f"""
    <article class="item" id="item-{item}" data-item="{item}">
      <header class="item-head">
        <h3>{html.escape(name)}</h3>
        <p>{html.escape(what)}</p>
      </header>
      <div class="tiles" role="group" aria-label="{html.escape(name)} versions">{''.join(tiles)}
      </div>
      <label class="note-label" for="note-{item}">Guidance for this item <span>(optional)</span></label>
      <textarea class="note" id="note-{item}" data-item="{item}" rows="2" placeholder="What you like, what to change, ideas for another round"></textarea>
      <p class="saved" id="saved-{item}" aria-live="polite"></p>
    </article>"""


def page():
    groups = []
    for group, items in ITEMS:
        cards = "".join(card(*i) for i in items)
        groups.append(f'<section class="group"><h2>{html.escape(group)}</h2>{cards}</section>')
    names = {i[0]: i[1] for _, items in ITEMS for i in items}
    template = (Path(__file__).parent / "page_template.html").read_text()
    return (template.replace("%%GROUPS%%", "".join(groups))
            .replace("%%NAMES%%", json.dumps(names))
            .replace("%%ROUND%%", str(ROUND))
            .replace("%%COUNT%%", str(len(names))))


if __name__ == "__main__":
    out = PICKS / "index.html"
    out.write_text(page())
    print(out.relative_to(ROOT), f"{out.stat().st_size // 1024} KB")
