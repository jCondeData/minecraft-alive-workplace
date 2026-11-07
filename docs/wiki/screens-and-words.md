# Screens and words

The mod's own screens (the Village Hall, the Blueprint Table, the Storehouse's requests board, the mailbox, the shop
and the research screen) should be easy to read at any GUI scale, and every sentence a player reads should use one
name for each job, block and item, in American English. Part of Milestone 24 (Everything looks finished).

Roadmap items: 24.4, 24.5

## What a player sees

Each screen fits its window at GUI scales 2 to 4, with no title or line running past the edge of its panel. The
Village Hall and the Storehouse board are drawn in the Blueprint Table's style: a gold title, a list on the left and a
pane on the right that reads out whatever is under the mouse, scrolled when it is long. The mailbox is a vanilla-style
container drawn in vanilla's container colors. The shop and the research screen are six-row chest screens of buttons.

The words match: a job is called what the README's job table ("All the jobs at a glance") calls it, a block or item of
ours is written with the same capitals everywhere ("the Travel Post", never "the travel post"), and spelling is
American ("color", "gray", "defense", "canceled", "Jeweler").

## How it works

**One place for words.** Every sentence a player reads comes from `src/main/resources/assets/aliveworkplace/lang/en_us.json`
by its key, never typed into the code (CLAUDE.md, "Rules").

**The screens.** `VillageHallMenuScreen` and `StorehouseScreen` are both built on `ChoiceTableScreen`, which draws a
server's button menu in the Blueprint Table's style; `BlueprintTableScreen` and `MailboxScreen` are screens of their
own (all in `src/client/java/io/github/jcondedata/aliveworkplace/client/`). The shop (`Shops`) and the research screen
(`ResearchScreen`) open a `ChoiceMenu`, a six-row chest whose slots are buttons.

## How it's checked

- **`tools/modtest/langcheck.py`** reads `en_us.json` and the Java sources and reports: keys the code uses that the
  file doesn't have, calls whose number of values differs from the sentence's blanks (`%s`), a title and its detail
  line with different blanks, sentences mixing `%s` with `%1$s`, a block or item name written in another case, a job
  called by another name (its `JOB_NAMES` list: "Fisher" for Fisherman, "Gravedigger" for Undertaker, "Pokemon" for
  Pokémon...), and British spellings. It fails on the first, second, name and spelling kinds. The nightly run
  (`.github/workflows/nightly.yml`) runs it in full.
- **`tools/modtest/spelling.py`** holds the British word list and its allowlist. It checks the values in
  `en_us.json` (never the keys), the questbook, `docs/guide/` and `docs/wiki/`, skipping code spans, ids and class
  names; `spelling.py --fix` rewrites what it finds. Every build runs `langcheck.py --spelling` (the Gradle task checkSpelling).
- **The screenshot harness** (`src/devclient/java/io/github/jcondedata/aliveworkplace/devclient/ScreenshotHarness.java`)
  measures the title of every chest-style screen it photographs and fails the scene when the title is
  wider than its panel. The `hall` scene also photographs the hall at GUI scale 4 (`09_hall_scale4`), `hall_reading`
  runs at scale 4 and photographs the Storehouse board at scale 3, and `city_plan` photographs scales 2 and 4.

## Switches

none

## Saved data

none

## Items, blocks, jobs, commands

none

## Decisions

- 24.5: the README's job table is the reference for every job's name; `langcheck.py` keeps a short list of the
  other names to refuse.
- American English for every player-read sentence (owner, 2026-10-26, ROADMAP 36.19); keys and ids are never renamed
  to match, so old saves keep working (`jewellers_workshop` stays).
- B41: a title that starts in the right half of its panel (the player inventory's "Crafting") keeps vanilla's
  8-pixel inset in the title check.

## Known limits

- `langcheck.py` reads only literal keys; a sentence whose key or values are built at run time is checked by a
  GameTest that renders it, not by the script.
- The title check measures chest-style screens only; the other screens are judged from their screenshots.
- A title and detail pair with different blanks is reported but doesn't fail the check; pairs checked by hand are
  listed in `CHECKED_PAIRS` with their reason.

## Proof

Checked on 2026-10-04 for both items: `langcheck.py` clean, and showcase run 55 with every screen scene passing.

Showcase scenes: `hall`, `hall_reading`, `table`, `porter`, `mail`, `shop`, `counter`, `scholar`, `city_plan`
