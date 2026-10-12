#!/usr/bin/env python3
"""Turns the player guide (docs/guide/*.md) into a wiki-style site for the showcase site.

    python3 tools/playerguide/site.py --site build/showcase-site      # writes build/showcase-site/player-guide/

The nightly showcase (.github/workflows/showcase.yml, the page job) runs this after page.py, so the guide is published
with the showcase at https://jcondedata.github.io/minecraft-alive-workplace/player-guide/ and its pictures are that
night's stills. (It isn't /guide/: the Guide Book scene already owns that folder. tools/guide/ is the in-game Guide
Book's pictures, a different thing.)

The layout follows a Fandom wiki's (the Create Mod Wiki's main page was the model): a sidebar on every page, a main
page with an article counter, the About blurb, a portal box per subject and a News box, articles with a floating
infobox, a contents box, a "See also" box and their categories, a page per category and an "All pages" page.
docs/guide/README.md has the conventions a page uses (the ```infobox block, the index's ```portals block).

No dependencies and no external services: it reads only the small part of markdown the guide uses (headings,
paragraphs, lists, tables, pictures, links, bold and italics) and writes plain HTML with its CSS inline.
"""
import argparse
import html
import os
import re
import sys
import unicodedata

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
GUIDE = os.path.join(ROOT, "docs", "guide")
CHANGELOG = os.path.join(ROOT, "CHANGELOG.md")
FOLDER = "player-guide"
WIKI = "Alive Workplace Wiki"

# Infobox keys with a meaning of their own; every other "Key: value" line is a row of the infobox's table.
RESERVED = ("title", "image", "caption", "type", "categories", "related", "expansion")

# The pages left out of the published site because their expansion is still switched off ("expansion: M32" in the
# infobox; Expansions.java holds the switches). Links to them from published pages render as plain text, and a list
# item that starts with such a link (the index's lists) is left out. Site() fills it before reading the pages.
HIDDEN = set()

CSS = """
:root{--bg:#eef0ea;--card:#fffefb;--text:#26231e;--muted:#6b6257;--accent:#3f6f2c;--accent2:#7a5a1e;--line:#d9d3c4;
--head:#2f4a24;--headtext:#f3f6ee;--box:#f4f1e8;--boxhead:#4f7a3a;--code:#efe9dc;--side:#f7f5ef}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--bg:#161714;--card:#22231f;--text:#ece6da;
--muted:#a89f90;--accent:#9cc77f;--accent2:#e0b860;--line:#3a3a33;--head:#1f2b1a;--headtext:#e9f0e2;--box:#2a2b26;
--boxhead:#466b34;--code:#302c27;--side:#1c1d19}}
:root[data-theme="dark"]{--bg:#161714;--card:#22231f;--text:#ece6da;--muted:#a89f90;--accent:#9cc77f;--accent2:#e0b860;
--line:#3a3a33;--head:#1f2b1a;--headtext:#e9f0e2;--box:#2a2b26;--boxhead:#466b34;--code:#302c27;--side:#1c1d19}
*{box-sizing:border-box}
html{-webkit-text-size-adjust:100%}
body{margin:0;background:var(--bg);color:var(--text);font:16px/1.6 system-ui,-apple-system,"Segoe UI",sans-serif}
a{color:var(--accent)}
code{background:var(--code);padding:1px 5px;border-radius:4px;font-size:14px;overflow-wrap:anywhere}
.top{position:sticky;top:0;z-index:5;display:flex;align-items:center;gap:12px;background:var(--head);
color:var(--headtext);padding:10px 16px;box-shadow:0 1px 3px rgba(0,0,0,.25)}
.top a.brand{color:var(--headtext);font-weight:700;text-decoration:none;font-size:18px}
.top .tag{opacity:.75;font-size:14px}
.top label{display:none;cursor:pointer;border:1px solid rgba(255,255,255,.35);border-radius:6px;padding:2px 10px;
font-size:14px}
#menu{display:none}
.layout{display:grid;grid-template-columns:230px minmax(0,1fr);max-width:1240px;margin:0 auto;gap:20px;padding:16px}
nav.side{background:var(--side);border:1px solid var(--line);border-radius:8px;padding:12px;font-size:14px;
align-self:start;position:sticky;top:64px;max-height:calc(100vh - 80px);overflow-y:auto}
nav.side h4{margin:12px 0 4px;font-size:12px;letter-spacing:.06em;text-transform:uppercase;color:var(--muted)}
nav.side h4:first-child{margin-top:0}
nav.side ul{list-style:none;margin:0;padding:0}
nav.side li{margin:2px 0}
nav.side a{text-decoration:none}
nav.side a:hover{text-decoration:underline}
nav.side details{margin:2px 0}
nav.side summary{cursor:pointer;color:var(--text)}
nav.side details ul{padding-left:14px;border-left:2px solid var(--line);margin:2px 0 6px 4px}
nav.side .here{font-weight:700}
main{background:var(--card);border:1px solid var(--line);border-radius:8px;padding:8px 24px 32px;min-width:0}
h1{font-size:30px;line-height:1.2;margin:18px 0 6px;font-weight:600}
h2{font-size:22px;margin:28px 0 8px;padding-bottom:4px;border-bottom:1px solid var(--line);font-weight:600;clear:none}
h3{font-size:18px;margin:20px 0 4px}
.crumbs{font-size:13px;color:var(--muted);margin-top:12px}
figure{margin:14px 0}
figure img{width:100%;max-width:640px;height:auto;border-radius:6px;display:block;background:#000;aspect-ratio:16/9;
object-fit:cover}
figcaption{color:var(--muted);font-size:13px;padding-top:4px}
.table{overflow-x:auto;margin:12px 0}
table{border-collapse:collapse;width:100%;font-size:15px}
th,td{border:1px solid var(--line);padding:6px 8px;text-align:left;vertical-align:top}
th{background:var(--box);font-weight:600}
aside.infobox{float:right;width:300px;margin:6px 0 16px 20px;background:var(--box);border:1px solid var(--line);
border-radius:8px;overflow:hidden;font-size:14px}
aside.infobox .ib-title{background:var(--boxhead);color:#fff;text-align:center;font-weight:700;padding:8px 10px;
font-size:16px}
aside.infobox .ib-type{text-align:center;font-size:12px;letter-spacing:.06em;text-transform:uppercase;color:var(--muted);
padding:6px 8px 0}
aside.infobox figure{margin:8px}
aside.infobox figure img{aspect-ratio:16/10}
aside.infobox table{font-size:14px}
aside.infobox th,aside.infobox td{border:0;border-top:1px solid var(--line);background:none}
aside.infobox th{width:42%;color:var(--muted)}
.toc{display:inline-block;background:var(--box);border:1px solid var(--line);border-radius:8px;padding:8px 16px 8px 8px;
margin:8px 0;font-size:14px}
.toc b{display:block;padding-left:8px}
.toc ol{margin:4px 0;padding-left:28px}
.box{background:var(--box);border:1px solid var(--line);border-radius:8px;margin:16px 0;overflow:hidden;clear:both}
.box>h2,.box>.bh{margin:0;background:var(--boxhead);color:#fff;font-size:17px;padding:7px 12px;border:0}
.box>.bb{padding:4px 14px 12px}
.box ul{padding-left:20px;margin:6px 0}
.cats{clear:both;margin-top:24px;font-size:14px;border:1px solid var(--line);background:var(--box);border-radius:6px;
padding:6px 10px}
.cats a{margin-right:4px}
.banner{text-align:center;padding:14px 8px 4px}
.banner h1{margin:6px 0 2px}
.banner .count{color:var(--muted);font-size:15px}
.banner .count b{color:var(--accent2);font-size:18px}
.mainpage{display:grid;grid-template-columns:minmax(0,2fr) minmax(0,1fr);gap:18px}
.mainpage .box{margin:0 0 18px}
.portals{display:grid;grid-template-columns:repeat(auto-fill,minmax(210px,1fr));gap:12px;padding:12px}
.portal{background:var(--card);border:1px solid var(--line);border-radius:8px;overflow:hidden}
.portal img{width:100%;aspect-ratio:16/9;object-fit:cover;display:block;background:#000}
.portal h3{margin:0;padding:6px 10px 2px;font-size:16px}
.portal h3 a{text-decoration:none}
.portal ul{margin:0 0 8px;padding:0 10px 0 26px;font-size:14px}
.news li{margin:4px 0}
.news .ver{font-weight:600;margin-top:8px;color:var(--muted);font-size:13px}
.catlist{list-style:none;padding:0}
.catlist li{display:flex;gap:12px;align-items:flex-start;padding:10px 0;border-bottom:1px solid var(--line)}
.catlist img{width:140px;aspect-ratio:16/9;object-fit:cover;border-radius:6px;background:#000;flex:none}
.catlist p{margin:2px 0 0;color:var(--muted);font-size:14px}
.seealso ul{columns:2;column-gap:24px}
@media (max-width:900px){
 .top label{display:inline-block;margin-left:auto}
 .top .tag{display:none}
 .layout{grid-template-columns:minmax(0,1fr);padding:10px}
 nav.side{display:none;position:static;max-height:none}
 #menu:checked~.layout nav.side{display:block}
 .mainpage{grid-template-columns:minmax(0,1fr)}
}
@media (max-width:640px){
 main{padding:4px 14px 24px}
 aside.infobox{float:none;width:auto;margin:8px 0 16px}
 h1{font-size:25px}
 .seealso ul{columns:1}
 .catlist img{width:96px}
}
"""


def slug(text):
    """A plain ASCII id for a heading or a category ("Pokémon" -> "pokemon")."""
    s = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", "-", s).strip("-") or "x"


def inline(text):
    """Escapes and turns links, bold, italics and code into HTML; links to x.md go to x.html."""
    parts = re.split(r"(`[^`]+`)", text)
    out = []
    for part in parts:
        if part.startswith("`") and part.endswith("`") and len(part) > 1:
            out.append(f"<code>{html.escape(part[1:-1])}</code>")
            continue
        s = html.escape(part, quote=False)

        def link(m):
            href = m.group(2)
            if href.split("#")[0] in HIDDEN:
                return m.group(1)
            if href.endswith(".md") or ".md#" in href:
                href = href.replace(".md", ".html")
            return f'<a href="{html.escape(href)}">{m.group(1)}</a>'

        s = re.sub(r"\[([^\]]+)\]\(([^)\s]+)\)", link, s)
        s = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", s)
        s = re.sub(r"(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])", r"<em>\1</em>", s)
        out.append(s)
    return "".join(out)


def plain(text):
    """Markdown inline text without its marks (for summaries and titles)."""
    text = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", text)
    return re.sub(r"[*`]", "", text)


def blocks(md):
    """Takes the ```infobox and ```portals blocks out of a page. Returns (markdown left, infobox, portals).

    An infobox is "key: value" lines (RESERVED keys, then rows in order); portals are "Category | picture" lines."""
    info = {"rows": []}
    portals = []
    out, lines, i = [], md.splitlines(), 0
    while i < len(lines):
        m = re.match(r"^```(infobox|portals)\s*$", lines[i])
        if not m:
            out.append(lines[i])
            i += 1
            continue
        kind = m.group(1)
        i += 1
        while i < len(lines) and not lines[i].startswith("```"):
            line = lines[i].strip()
            i += 1
            if not line:
                continue
            if kind == "portals":
                name, _, image = (p.strip() for p in line.lstrip("- ").partition("|"))
                portals.append((name, image))
                continue
            key, _, value = (p.strip() for p in line.partition(":"))
            if key.lower() in RESERVED:
                info[key.lower()] = value
            else:
                info["rows"].append((key, value))
        i += 1
    return "\n".join(out), info, portals


def csv(value):
    return [v.strip() for v in (value or "").split(",") if v.strip()]


def render(md):
    """The markdown the guide uses, as HTML. Returns (title, body, headings [(id, text)], first paragraph)."""
    lines = md.splitlines()
    out, title, heads, first, i = [], "", [], "", 0
    while i < len(lines):
        line = lines[i]
        if not line.strip():
            i += 1
            continue
        m = re.match(r"^(#{1,3}) (.*)", line)
        if m:
            level, text = len(m.group(1)), m.group(2).strip()
            if level == 1 and not title:
                title = text
                i += 1
                continue
            if level == 2:
                hid = slug(plain(text))
                heads.append((hid, plain(text)))
                out.append(f'<h2 id="{hid}">{inline(text)}</h2>')
            else:
                out.append(f"<h{level}>{inline(text)}</h{level}>")
            i += 1
            continue
        m = re.match(r"^!\[([^\]]*)\]\(([^)\s]+)\)\s*$", line)
        if m:
            alt = html.escape(m.group(1))
            out.append(f'<figure><img src="{html.escape(m.group(2))}" alt="{alt}" loading="lazy">'
                       f"<figcaption>{alt}</figcaption></figure>")
            i += 1
            continue
        if line.lstrip().startswith("|"):
            rows = []
            while i < len(lines) and lines[i].lstrip().startswith("|"):
                cells = [c.strip() for c in lines[i].strip().strip("|").split("|")]
                if not all(re.fullmatch(r":?-{3,}:?", c) for c in cells):
                    rows.append(cells)
                i += 1
            head = "".join(f"<th>{inline(c)}</th>" for c in rows[0])
            body = "".join("<tr>" + "".join(f"<td>{inline(c)}</td>" for c in r) + "</tr>" for r in rows[1:])
            out.append(f'<div class="table"><table><thead><tr>{head}</tr></thead><tbody>{body}</tbody></table></div>')
            continue
        m = re.match(r"^(\s*)([-*]|\d+\.) (.*)", line)
        if m:
            tag = "ol" if m.group(2)[0].isdigit() else "ul"
            items = []
            while i < len(lines):
                m = re.match(r"^\s*([-*]|\d+\.) (.*)", lines[i])
                if m:
                    items.append(m.group(2))
                elif lines[i].startswith("  ") and lines[i].strip() and items:
                    items[-1] += " " + lines[i].strip()
                else:
                    break
                i += 1
            items = [it for it in items if not hidden_item(it)]
            if not items:
                continue
            out.append(f"<{tag}>" + "".join(f"<li>{inline(it)}</li>" for it in items) + f"</{tag}>")
            continue
        para = []
        while i < len(lines) and lines[i].strip() and not re.match(r"^(#{1,3} |!\[|\s*\||\s*([-*]|\d+\.) )", lines[i]):
            para.append(lines[i].strip())
            i += 1
        text = " ".join(para)
        if not first:
            first = plain(text)
        out.append(f"<p>{inline(text)}</p>")
    # A section left empty by gated pages (only links to them) goes too: a "###" with nothing under it before the next
    # heading, and a "##" whose subsections all went (looked at from the end, so an emptied "###" empties its "##").
    kept = []
    for chunk in reversed(out):
        level = int(chunk[2]) if re.match(r"^<h[23][ >]", chunk) else 0
        after = kept[-1] if kept else None
        after_level = int(after[2]) if after and re.match(r"^<h[23][ >]", after) else 0
        if level and (after is None or (after_level and after_level <= level)):
            if level == 2:
                hid = re.match(r'<h2 id="([^"]*)"', chunk).group(1)
                heads = [h for h in heads if h[0] != hid]
            continue
        kept.append(chunk)
    kept.reverse()
    return title, "\n".join(kept), heads, first


def hidden_item(item):
    """A list item that starts with a link to a gated page (the index's "- [Drought](drought.md): ..." lines)."""
    m = re.match(r"^\s*\[[^\]]+\]\(([^)\s#]+)", item)
    return bool(m and m.group(1) in HIDDEN)


def expansions(info):
    """The milestones an infobox's "expansion:" names ("M32", or "M32, M34"), as numbers ("32")."""
    return [re.sub(r"^M", "", e, flags=re.I) for e in csv(info.get("expansion"))]


def gated_pages(off=None):
    """The guide's pages whose expansion is still switched off: they aren't published until its switch flips."""
    off = off_milestones() if off is None else off
    out = set()
    for name in os.listdir(GUIDE):
        if name.endswith(".md") and name != "README.md":
            _, info, _ = blocks(open(os.path.join(GUIDE, name), encoding="utf-8").read())
            if set(expansions(info)) & set(off):
                out.add(name)
    return out


class Page:
    def __init__(self, name):
        self.name = name
        self.href = name[:-3] + ".html"
        raw = open(os.path.join(GUIDE, name), encoding="utf-8").read()
        md, self.info, self.portals = blocks(raw)
        self.md = md
        self.title, self.body, self.heads, self.first = render(md)
        self.categories = csv(self.info.get("categories"))
        self.related = csv(self.info.get("related"))
        self.image = self.info.get("image") or next(iter(re.findall(r"^!\[[^\]]*\]\(([^)\s]+)\)", md, re.M)), "")


def page_order():
    """The published pages in the order index.md links them, index first (gated pages left out)."""
    index = open(os.path.join(GUIDE, "index.md"), encoding="utf-8").read()
    order = ["index.md"]
    for target in re.findall(r"\]\(([\w-]+\.md)\)", index):
        if target not in order and target not in HIDDEN and os.path.exists(os.path.join(GUIDE, target)):
            order.append(target)
    for name in sorted(os.listdir(GUIDE)):
        if name.endswith(".md") and name != "README.md" and name not in order and name not in HIDDEN:
            order.append(name)
    return order


# CHANGELOG lines that aren't news for a player: expansions still switched off, and things behind the scenes.
NOT_NEWS = re.compile(r"off until|switched off|still off|nothing to start|behind the|part of \d|\bfor [12]\.\d\b|"
                      r"\b[12]\.\d\b(?!\.\d)|wonder|datapack|data/|render|\btests?\b|showcase|wiki|scene|\bci\b|"
                      r"the player guide|engine|\blog\b|look-up|costs the server", re.I)
# A note whose text says it belongs to an expansion (still switched off in the release) isn't news either.
EXPANSION_TEXT = re.compile(r"\bstory\b|story arc|off until|switched off|nothing to start|behind the|"
                            r"\b(for|part of|in|with) [12]\.\d\b(?!\.\d)|realm|\blairs?\b|reputation|\belders?\b", re.I)
AMERICAN = {"Defence": "Defense", "defence": "defense", "colour": "color", "Colour": "Color", "Jeweller": "Jeweler",
            "Grey": "Gray", "grey": "gray", "armour": "armor", "Armour": "Armor", "favour": "favor", "honour": "honor",
            "neighbour": "neighbor", "centre": "center", "traveller": "traveler", "Traveller": "Traveler", "Centre": "Center", "storey": "story"}


def off_milestones():
    """The milestones of the expansions still switched off in the release (Expansions.java: M31 = false and so on)."""
    found = []
    for folder, _, files in os.walk(os.path.join(ROOT, "src", "main", "java")):
        if "Expansions.java" in files:
            text = open(os.path.join(folder, "Expansions.java"), encoding="utf-8").read()
            found += re.findall(r"boolean M(\d+) = false", text)
    return set(found)


def news(limit=8, releases=12):
    """What's new: the bold titles of the latest released versions' Added and Changed notes (a fix in an expansion's content rarely says so), in player words."""
    if not os.path.exists(CHANGELOG):
        return []
    text = open(CHANGELOG, encoding="utf-8").read()
    off = off_milestones()
    out = []
    sections = re.split(r"^## ", text, flags=re.M)[1:]
    for sec in sections:
        head, _, body = sec.partition("\n")
        m = re.match(r"(\d+\.\d+\.\d+)\s*[—-]\s*(\S+)", head)
        if not m:
            continue
        items, part = [], ""
        for line in body.splitlines():
            if line.startswith("### "):
                part = line[4:].strip()
                continue
            if part not in ("Added", "Changed"):
                continue
            b = re.match(r"^- \*\*(.+?)\*\*\.?\s*(\(([^)]*)\))?", line)
            if not b:
                if line.startswith("- "):
                    items.append(None)  # a note without a title ends the one before
                elif items and items[-1] and line.startswith("  "):
                    items[-1][2].append(line.strip())
                continue
            title = b.group(1).strip().rstrip(".")
            ids = re.findall(r"(?<![\w.])(\d{1,2})\.\d{1,2}[a-z]?(?![\w]|\.\d)", b.group(3) or "")
            if NOT_NEWS.search(title + " " + (b.group(3) or "")) or "`" in title or off & set(ids):
                continue
            title = re.sub(r"\s*\([^)]*\)", "", title)
            for british, american in AMERICAN.items():
                title = title.replace(british, american)
            if re.search(r"(?<![\w.])\d{1,2}\.\d{1,2}[a-z]?(?!\w|\.\d)|\bB\d{1,3}\b", title):
                continue
            items.append((part, title, [line]))
        items = [(part, title) for part, title, text in filter(None, items)
                 if not EXPANSION_TEXT.search(" ".join(text))]
        if items:
            out.append((m.group(1), m.group(2), items))
        if len(out) >= releases or sum(len(x[2]) for x in out) >= limit:
            break
    trimmed, left = [], limit
    for ver, date, items in out:
        if left <= 0:
            break
        trimmed.append((ver, date, items[:left]))
        left -= len(items[:left])
    return trimmed


class Site:
    def __init__(self, off=None):
        """off: the switched-off milestones ({"32", ...}); by default Expansions.java's."""
        HIDDEN.clear()
        HIDDEN.update(gated_pages(off))
        self.hidden = set(HIDDEN)
        self.order = page_order()
        self.pages = {n: Page(n) for n in self.order}
        self.index = self.pages["index.md"]
        self.articles = [self.pages[n] for n in self.order if n != "index.md"]
        self.cats = {}
        for p in self.articles:
            for c in p.categories:
                self.cats.setdefault(c, []).append(p)
        portal_names = [name for name, _ in self.index.portals]
        self.cat_order = [c for c in portal_names if c in self.cats] + sorted(c for c in self.cats if c not in portal_names)

    @staticmethod
    def cat_href(name):
        return f"category-{slug(name)}.html"

    def sidebar(self, current=None):
        here = current.categories if current else []
        def a(href, text, on=False):
            cls = ' class="here"' if on else ""
            return f'<a href="{href}"{cls}>{html.escape(text)}</a>'
        parts = ['<h4>Explore</h4><ul>',
                 f'<li>{a("index.html", "Main page", current is self.index)}</li>',
                 f'<li>{a("all-pages.html", "All pages")} ({len(self.articles)})</li>',
                 f'<li>{a("categories.html", "Categories")} ({len(self.cats)})</li></ul>',
                 "<h4>Subjects</h4>"]
        for c in self.cat_order:
            items = "".join(f'<li>{a(p.href, p.title, p is current)}</li>' for p in self.cats[c])
            parts.append(f'<details{" open" if c in here else ""}><summary>{html.escape(c)}</summary>'
                         f'<ul><li>{a(self.cat_href(c), "All in " + c)}</li>{items}</ul></details>')
        return '<nav class="side" aria-label="Wiki">' + "".join(parts) + "</nav>"

    def doc(self, title, body, current=None):
        return (f'<!doctype html><html lang="en"><head><meta charset="utf-8">'
                f'<meta name="viewport" content="width=device-width,initial-scale=1">'
                f"<title>{html.escape(title)} · {WIKI}</title><style>{CSS}</style></head><body>"
                f'<input type="checkbox" id="menu"><header class="top"><a class="brand" href="index.html">{WIKI}</a>'
                f'<span class="tag">the player guide</span><label for="menu">Menu</label></header>'
                f'<div class="layout">{self.sidebar(current)}<main>{body}</main></div></body></html>')

    def infobox(self, p):
        info = p.info
        if not (info["rows"] or info.get("image") or info.get("type")):
            return ""
        title = info.get("title") or p.title
        out = [f'<aside class="infobox"><div class="ib-title">{html.escape(title)}</div>']
        if info.get("type"):
            out.append(f'<div class="ib-type">{html.escape(info["type"])}</div>')
        if p.image:
            cap = info.get("caption", "")
            out.append(f'<figure><img src="{html.escape(p.image)}" alt="{html.escape(cap or title)}">'
                       + (f"<figcaption>{inline(cap)}</figcaption>" if cap else "") + "</figure>")
        if info["rows"]:
            out.append("<table>" + "".join(f"<tr><th>{inline(k)}</th><td>{inline(v)}</td></tr>"
                                           for k, v in info["rows"]) + "</table>")
        out.append("</aside>")
        return "".join(out)

    def see_also(self, p):
        seen, links = {p.name}, []
        for r in p.related:
            if r in self.pages and r not in seen:
                seen.add(r)
                links.append(self.pages[r])
        for c in p.categories:
            for q in self.cats.get(c, []):
                if q.name not in seen:
                    seen.add(q.name)
                    links.append(q)
        if not links:
            return ""
        items = "".join(f'<li><a href="{q.href}">{html.escape(q.title)}</a></li>' for q in links[:10])
        return f'<div class="box seealso"><div class="bh">See also</div><div class="bb"><ul>{items}</ul></div></div>'

    def cat_bar(self, p):
        if not p.categories:
            return ""
        links = " · ".join(f'<a href="{self.cat_href(c)}">{html.escape(c)}</a>' for c in p.categories)
        return f'<div class="cats"><b>Categories:</b> {links}</div>'

    def article(self, p):
        body = p.body
        if p.image and p.info.get("image"):
            # The infobox shows this picture; don't show it twice.
            body = re.sub(r'<figure><img src="' + re.escape(html.escape(p.image)) + r'"[^>]*><figcaption>.*?</figcaption></figure>',
                          "", body, count=1)
        toc = ""
        if len(p.heads) >= 3:
            toc = ('<nav class="toc"><b>Contents</b><ol>'
                   + "".join(f'<li><a href="#{hid}">{html.escape(t)}</a></li>' for hid, t in p.heads) + "</ol></nav>")
        # The contents box goes after the article's first paragraph, as on a wiki.
        if toc and "</p>" in body:
            body = body.replace("</p>", "</p>" + toc, 1)
        else:
            body = toc + body
        crumbs = ""
        if p.categories:
            crumbs = (f'<div class="crumbs"><a href="index.html">Main page</a> › '
                      f'<a href="{self.cat_href(p.categories[0])}">{html.escape(p.categories[0])}</a></div>')
        html_body = (f"{crumbs}<h1>{inline(p.title)}</h1>{self.infobox(p)}{body}"
                     f"{self.see_also(p)}{self.cat_bar(p)}")
        return self.doc(p.title, html_body, p)

    def portal(self, name, image):
        articles = self.cats.get(name, [])
        image = image or next((p.image for p in articles if p.image), "")
        items = []
        if len(articles) <= 2:
            for p in articles:
                items.append(f'<li><a href="{p.href}">{html.escape(p.title)}</a></li>')
                items += [f'<li><a href="{p.href}#{hid}">{html.escape(t)}</a></li>' for hid, t in p.heads[:3]]
        else:
            items = [f'<li><a href="{p.href}">{html.escape(p.title)}</a></li>' for p in articles]
        img = f'<a href="{self.cat_href(name)}"><img src="{html.escape(image)}" alt="{html.escape(name)}" loading="lazy"></a>' if image else ""
        return (f'<div class="portal">{img}<h3><a href="{self.cat_href(name)}">{html.escape(name)}</a></h3>'
                f'<ul>{"".join(items)}</ul></div>')

    def main_page(self):
        p = self.index
        # About: what comes before the index's first heading; the rest of the index lists every page.
        about, _, rest = p.body.partition("<h2")
        rest = "<h2" + rest if rest else ""
        portals = "".join(self.portal(n, img) for n, img in p.portals if n in self.cats)
        news_items = []
        for ver, date, items in news():
            news_items.append(f'<div class="ver">Version {html.escape(ver)} · {html.escape(date)}</div><ul>'
                              + "".join(f"<li><b>{html.escape(part)}:</b> {inline(t)}</li>" for part, t in items)
                              + "</ul>")
        news_html = "".join(news_items) or "<p>Nothing new yet.</p>"
        body = (f'<div class="banner"><h1>{inline(p.title)}</h1><div class="count">'
                f'<b>{len(self.articles)}</b> articles in <b>{len(self.cats)}</b> categories, written for players</div></div>'
                f'<div class="mainpage"><div>'
                f'<div class="box"><div class="bh">About</div><div class="bb">{about}</div></div>'
                f'<div class="box"><div class="bh">Portals</div><div class="portals">{portals}</div></div>'
                f'<div class="box"><div class="bh">Every article</div><div class="bb">{rest}</div></div>'
                f'</div><div>'
                f'<div class="box news"><div class="bh">News: what\'s new</div><div class="bb">{news_html}'
                f'<p><a href="https://github.com/jCondeData/minecraft-alive-workplace/releases">Every release</a></p></div></div>'
                f'<div class="box"><div class="bh">Subjects</div><div class="bb"><ul>'
                + "".join(f'<li><a href="{self.cat_href(c)}">{html.escape(c)}</a> ({len(self.cats[c])})</li>'
                          for c in self.cat_order)
                + "</ul></div></div></div></div>")
        return self.doc(p.title, body, p)

    def listing(self, pages):
        out = []
        for p in pages:
            img = f'<img src="{html.escape(p.image)}" alt="" loading="lazy">' if p.image else ""
            summary = p.first if len(p.first) < 220 else p.first[:217].rsplit(" ", 1)[0] + "…"
            out.append(f'<li>{img}<div><a href="{p.href}"><b>{html.escape(p.title)}</b></a>'
                       f"<p>{html.escape(summary)}</p></div></li>")
        return '<ul class="catlist">' + "".join(out) + "</ul>"

    def category(self, name):
        pages = self.cats[name]
        body = (f'<div class="crumbs"><a href="index.html">Main page</a> › <a href="categories.html">Categories</a></div>'
                f"<h1>Category: {html.escape(name)}</h1>"
                f"<p>{len(pages)} article{'s' if len(pages) != 1 else ''} in this category.</p>{self.listing(pages)}")
        return self.doc(f"Category: {name}", body)

    def categories(self):
        items = "".join(f'<li><a href="{self.cat_href(c)}"><b>{html.escape(c)}</b></a>'
                        f'<p>{", ".join(html.escape(p.title) for p in self.cats[c])}</p></li>' for c in self.cat_order)
        body = f'<h1>Categories</h1><ul class="catlist">{items}</ul>'
        return self.doc("Categories", body)

    def all_pages(self):
        pages = sorted(self.articles, key=lambda p: p.title.lower())
        return self.doc("All pages", f"<h1>All pages</h1>{self.listing(pages)}")

    def write(self, site):
        out_dir = os.path.join(site, FOLDER)
        os.makedirs(out_dir, exist_ok=True)
        files = {"index.html": self.main_page(), "categories.html": self.categories(), "all-pages.html": self.all_pages()}
        for p in self.articles:
            files[p.href] = self.article(p)
        for c in self.cats:
            files[self.cat_href(c)] = self.category(c)
        for name, text in files.items():
            with open(os.path.join(out_dir, name), "w", encoding="utf-8") as f:
                f.write(text)
        return len(files)


def build(site):
    return Site().write(site)


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--site", required=True, help="the showcase site folder; the guide goes in its player-guide/")
    args = ap.parse_args()
    s = Site()
    n = s.write(args.site)
    print(f"guide: {n} pages ({len(s.articles)} articles, {len(s.cats)} categories) in {os.path.join(args.site, FOLDER)}"
          f"; {len(s.hidden)} pages wait for their expansion's switch")
    return 0


if __name__ == "__main__":
    sys.exit(main())
