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

## Who keeps it true

- **The lane that changes something a player sees updates its guide page in the same commit**, as for the wiki
  (`docs/agent/sessions.md`, the build loop).
- **The QA lane checks it** with the wiki: for every item it verifies, it reads the guide's page against what the tests
  proved.
- **`tools/modtest/guidecheck.py` fails the build** (`./gradlew build` runs it as `checkGuide`) when a page has a
  roadmap id, "ROADMAP", "GameTest" or a path under `src/`, links a page that doesn't exist or isn't linked from
  `index.md`, or shows a picture of a scene or still that doesn't exist. Run it yourself (under a second):
  `python3 tools/modtest/guidecheck.py`; its own tests: `python3 tools/modtest/test_guidecheck.py`.
