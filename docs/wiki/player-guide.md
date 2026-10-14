# The player guide

A second, separate site for players: plain pages a friend can read with no GitHub knowledge, published with the
showcase. This wiki stays the builders' and QA's reference (Safety net).

Roadmap items: 22.10, 26.8

## 1. What a player sees

**The questbook (26.8).** On a server with FTB Quests, the quest book gains our chapters, start to finish: Getting
Started (Cobblemon's starter, first catches, Poké Balls, RCT's first trainers), the Builder and the Blueprint Table,
Hiring and Retraining, the Village Hall and its Ranks, Pokémon in the Village, and one chapter per expansion (1.1 the
Steward to 2.0 Wonders). Tasks are item, advancement, kill and checkmark tasks; rewards are emeralds and experience.
The guide's `questbook.md` explains it.

A website, linked from the showcase page, at https://jcondedata.github.io/minecraft-alive-workplace/player-guide/.
It has an index and 14 pages: getting started, giving jobs, the Builder and blueprints, jobs on the land, crafters
and smiths, porters, post and services, the Village Hall, village life, quests, festivals and markets, trade and
caravans, defense, Pokémon partners, and the settings a server owner can change. Each page uses pictures from the
nightly showcase.

## 2. How it works

The pages are markdown in `docs/guide/`. `tools/playerguide/site.py` turns them into HTML in the showcase
workflow's page job, so the guide goes up with every full nightly showcase run on `main`. Only features that are
switched on in the 1.0 release are described; expansion content that is still off is left out until its expansion
ships (the Steward, legends, edicts, guilds, friendship, gifts, the Cup, the economy, classes and sieges).

`tools/modtest/guidecheck.py` (the Gradle task checkGuide, part of `build`) fails the build when a guide page names a
roadmap or bug id, "ROADMAP", "GameTest" or a source path, has a broken link, is missing from the index, or shows a
scene picture that doesn't exist.

The questbook's chapters are written by `tools/questbook/generate.py` from `tools/questbook/chapters.py` into
`src/main/resources/aliveworkplace/questbook/` (FTB Quests SNBT, with a `manifest.txt` of each file's milestone and
mods). FTB Quests reads chapters only from `config/ftbquests/quests/chapters/` (no data-pack route in its 1.21.1
source), so `compat/ftbquests/FtbQuestsBook`, started by `Compat.init` when `ftbquests` is loaded, copies on server
start (before FTB Quests loads the folder on server started) each chapter that isn't installed yet, and records it in
`config/aliveworkplace/questbook.txt` (the version marker). Data only: no FTB Quests classes.

## 3. Switches

none (an expansion's chapter waits for its milestone switch in `Expansions`, a Pokémon chapter for Cobblemon)

## 4. Saved data

none

## 5. Items, blocks, jobs, commands

none

## 6. Decisions

- The guide lives under `/player-guide/`, not `/guide/`, because the Guide Book scene already owns that folder on the
  showcase site (22.10, lane d; noted in ROADMAP Notes).
- Its code is in `tools/playerguide/`, because `tools/guide/` is the in-game Guide Book's picture tool (22.10).
- The questbook never overwrites (26.8): a chapter file already there is kept, and one in the marker is never copied
  again, so the owner's edits and deletions stand. Our chapters sit in FTB Quests' default group at order 100 and up,
  after the pack's own, so we never edit the shared `chapter_groups.snbt`. Quest text is in the chapter files (FTB
  Quests' legacy fields, which it moves into its own `lang/en_us.snbt` on its first save) rather than in the shared
  lang file. Ids are hashes of chapter and quest keys, stable across versions.

## 7. Known limits

The guide only goes live with the next full nightly showcase run. When an expansion is switched on, its page has to
be added by the lane that ships it.

The questbook has not been seen in FTB Quests' screen yet: FTB Quests isn't in the compat-test pack or in the pack test.
Many quests are checkmarks, because villages' events can't be detected by FTB Quests without its classes.

## 8. Proof

`tools/modtest/test_guidecheck.py` (14 checks) and the Gradle task checkGuide, which `build` runs.

The questbook: `QuestbookCompatTests` (every item, entity, block and advancement id in the chapters exists, ids valid
and unique, dependencies ours, emeralds-only rewards) and `QuestbookInstallGameTests` (copies once, never overwrites,
waits for expansions and mods). `python3 tools/questbook/generate.py --check` says whether the chapters are up to date.
