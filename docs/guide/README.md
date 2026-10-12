# The player guide (for the people who keep it)

`docs/guide/` is the guide the owner sends his friends: plain pages about what a player does and sees. It is published
every night with the showcase, at https://jcondedata.github.io/minecraft-alive-workplace/player-guide/ (start page
`index.md`; `tools/playerguide/site.py` turns the pages into the site's HTML in the showcase workflow's page job). Not
at `/guide/`: the Guide Book's showcase scene already owns that folder.

`docs/wiki/` stays the builders' and QA's reference. This guide is the opposite: never how it works inside.

## Rules for a page

- Write for a friend who plays Minecraft and has never seen GitHub: what they craft, click, see and get. Short
  paragraphs, warm, second person.
- Never: roadmap ids, lanes, tests, scenes, saved data, file formats, class or field names, "known limits", version
  plans or anything coming later. Settings in plain words with the settings screen's names (game rule names and
  commands are fine: players type them).
- Only what is switched on in the release. An expansion's content gets its page (or its lines) in the release that
  turns it on, in the same commit as the switch.
- Pictures come from the nightly showcase, so they stay fresh: `![caption](https://jcondedata.github.io/minecraft-alive-workplace/<scene>/still-<n>.jpg)`
  on a line of its own (`anim.gif` for the scene's GIF). Pick stills that show only switched-on content.
- Every page starts with a `# ` title and is linked from `index.md`.

## The wiki layout

The site looks like a Fandom wiki: a sidebar on every page, a main page, an infobox on every article, categories,
"See also" boxes and an "All pages" page. `site.py` builds all of it from two kinds of block in the pages:

- **The infobox.** Right under the title, a fenced block `` ```infobox `` of `Key: value` lines. `type` (Job, Block,
  Disaster...), `image` (a showcase still, as above), `caption`, `categories` (comma-separated) and `related` (other
  pages, as `builder.md, jobs.md`) have a meaning; every other line is a row of the box, in order, such as
  `Recipe: blue dye on top, a cartography table in the middle, planks around it` or `Workstation: Blueprint Table`. A
  row's value may hold links and **bold**. Only facts the page itself says. The infobox's picture isn't shown again in
  the text. Every page but `index.md` needs one with `categories`.
- **Categories** make the sidebar's subjects, a page per category and the "See also" box (the `related` pages first,
  then the others in the same categories). Reuse the existing ones (Basics, Builder, Jobs, Village Hall, Quests,
  Festivals, Trade, Defense, Disasters, Pokémon, Settings) before adding one.
- **Portals.** `index.md` has a fenced `` ```portals `` block, one `Category | picture` line per portal box on the main
  page, in order. The main page also shows the article counter, the About box (the index's text before its first
  heading), the News box (the newest released CHANGELOG notes, leaving out switched-off expansions and anything behind
  the scenes) and the index's own lists under "Every article".

Check the site after changing the layout: `python3 tools/playerguide/site.py --site /tmp/site` and open
`/tmp/site/player-guide/index.html`; it must also read well at phone width.

## Who keeps it true

- **The lane that changes something a player sees updates its guide page in the same commit**, as for the wiki
  (`docs/agent/sessions.md`, the build loop).
- **The QA lane checks it** with the wiki: for every item it verifies, it reads the guide's page against what the tests
  proved.
- **`tools/modtest/guidecheck.py` fails the build** (`./gradlew build` runs it as `checkGuide`) when a page has a
  roadmap id, "ROADMAP", "GameTest" or a path under `src/`, links a page that doesn't exist or isn't linked from
  `index.md`, shows a picture of a scene or still that doesn't exist, lacks an infobox with categories, names a
  `related` page that doesn't exist, or has a portal no page is in. Run it yourself (under a second):
  `python3 tools/modtest/guidecheck.py`; its own tests: `python3 tools/modtest/test_guidecheck.py`.
