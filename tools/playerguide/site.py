#!/usr/bin/env python3
"""Turns the player guide (docs/guide/*.md) into plain web pages for the showcase site.

    python3 tools/playerguide/site.py --site build/showcase-site      # writes build/showcase-site/player-guide/

The nightly showcase (.github/workflows/showcase.yml, the page job) runs this after page.py, so the guide is published
with the showcase at https://jcondedata.github.io/minecraft-alive-workplace/player-guide/ and its pictures are that
night's stills. (It isn't /guide/: the Guide Book scene already owns that folder. tools/guide/ is the in-game Guide
Book's pictures, a different thing.) No dependencies: it reads only the
small part of markdown the guide uses (headings, paragraphs, lists, tables, pictures, links, bold and italics).
"""
import argparse
import html
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
GUIDE = os.path.join(ROOT, "docs", "guide")
FOLDER = "player-guide"

CSS = """
:root{--bg:#f6f3ec;--card:#fffdf8;--text:#2b2620;--muted:#6b6257;--accent:#4f7a3a;--line:#e3dccd;--code:#efe9dc}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--bg:#1d1b18;--card:#262320;--text:#ece6da;
--muted:#a89f90;--accent:#9cc77f;--line:#3a352e;--code:#302c27}}
:root[data-theme="dark"]{--bg:#1d1b18;--card:#262320;--text:#ece6da;--muted:#a89f90;--accent:#9cc77f;--line:#3a352e;--code:#302c27}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--text);font:17px/1.6 system-ui,-apple-system,"Segoe UI",sans-serif}
header{background:var(--card);border-bottom:1px solid var(--line);padding:12px 16px}
header a{color:var(--accent);font-weight:600;text-decoration:none}
main{max-width:760px;margin:0 auto;padding:8px 16px 48px}
h1{font-size:30px;line-height:1.2;margin:24px 0 8px}
h2{font-size:22px;margin:32px 0 6px;padding-top:8px;border-top:1px solid var(--line)}
h3{font-size:18px;margin:22px 0 4px}
a{color:var(--accent)}
code{background:var(--code);padding:1px 5px;border-radius:4px;font-size:15px}
figure{margin:16px 0}
figure img{width:100%;height:auto;border-radius:10px;display:block;background:#000;aspect-ratio:16/9;object-fit:cover}
figcaption{color:var(--muted);font-size:14px;padding-top:4px}
.table{overflow-x:auto;margin:12px 0}
table{border-collapse:collapse;width:100%;font-size:15px}
th,td{border-bottom:1px solid var(--line);padding:6px 8px;text-align:left;vertical-align:top}
th{color:var(--muted);font-weight:600}
nav.pages{margin-top:40px;padding-top:16px;border-top:1px solid var(--line);font-size:15px}
nav.pages a{display:inline-block;margin:0 14px 6px 0}
"""


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
            if href.endswith(".md") or ".md#" in href:
                href = href.replace(".md", ".html")
            return f'<a href="{html.escape(href)}">{m.group(1)}</a>'

        s = re.sub(r"\[([^\]]+)\]\(([^)\s]+)\)", link, s)
        s = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", s)
        s = re.sub(r"(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])", r"<em>\1</em>", s)
        out.append(s)
    return "".join(out)


def render(md):
    """The markdown the guide uses, as HTML. Returns (title, body)."""
    lines = md.splitlines()
    out, title, i = [], "", 0
    while i < len(lines):
        line = lines[i]
        if not line.strip():
            i += 1
            continue
        m = re.match(r"^(#{1,3}) (.*)", line)
        if m:
            level = len(m.group(1))
            if level == 1 and not title:
                title = m.group(2).strip()
            out.append(f"<h{level}>{inline(m.group(2).strip())}</h{level}>")
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
            out.append(f"<{tag}>" + "".join(f"<li>{inline(it)}</li>" for it in items) + f"</{tag}>")
            continue
        para = []
        while i < len(lines) and lines[i].strip() and not re.match(r"^(#{1,3} |!\[|\s*\||\s*([-*]|\d+\.) )", lines[i]):
            para.append(lines[i].strip())
            i += 1
        out.append(f"<p>{inline(' '.join(para))}</p>")
    return title, "\n".join(out)


def page_order():
    """The pages in the order index.md links them, index first."""
    index = open(os.path.join(GUIDE, "index.md"), encoding="utf-8").read()
    order = ["index.md"]
    for target in re.findall(r"\]\(([\w-]+\.md)\)", index):
        if target not in order and os.path.exists(os.path.join(GUIDE, target)):
            order.append(target)
    for name in sorted(os.listdir(GUIDE)):
        if name.endswith(".md") and name != "README.md" and name not in order:
            order.append(name)
    return order


def build(site):
    out_dir = os.path.join(site, FOLDER)
    os.makedirs(out_dir, exist_ok=True)
    order = page_order()
    titles = {}
    bodies = {}
    for name in order:
        titles[name], bodies[name] = render(open(os.path.join(GUIDE, name), encoding="utf-8").read())
    nav = "".join(f'<a href="{n[:-3]}.html">{html.escape(titles[n] if n != "index.md" else "Start")}</a>' for n in order)
    for name in order:
        doc = (f'<!doctype html><html lang="en"><head><meta charset="utf-8">'
               f'<meta name="viewport" content="width=device-width,initial-scale=1">'
               f"<title>{html.escape(titles[name])} · Alive Workplace</title><style>{CSS}</style></head><body>"
               f'<header><a href="index.html">Alive Workplace · Player guide</a></header>'
               f'<main>{bodies[name]}<nav class="pages">{nav}</nav></main></body></html>')
        with open(os.path.join(out_dir, name[:-3] + ".html"), "w", encoding="utf-8") as f:
            f.write(doc)
    return len(order)


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--site", required=True, help="the showcase site folder; the guide goes in its player-guide/")
    args = ap.parse_args()
    n = build(args.site)
    print(f"guide: {n} pages in {os.path.join(args.site, FOLDER)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
