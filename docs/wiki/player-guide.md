# The player guide

A second, separate site for players: plain pages a friend can read with no GitHub knowledge, published with the
showcase. This wiki stays the builders' and QA's reference (Safety net).

Roadmap items: 22.10

## 1. What a player sees

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

## 3. Switches

none

## 4. Saved data

none

## 5. Items, blocks, jobs, commands

none

## 6. Decisions

- The guide lives under `/player-guide/`, not `/guide/`, because the Guide Book scene already owns that folder on the
  showcase site (22.10, lane d; noted in ROADMAP Notes).
- Its code is in `tools/playerguide/`, because `tools/guide/` is the in-game Guide Book's picture tool (22.10).

## 7. Known limits

The guide only goes live with the next full nightly showcase run. When an expansion is switched on, its page has to
be added by the lane that ships it.

## 8. Proof

`tools/modtest/test_guidecheck.py` (14 checks) and the Gradle task checkGuide, which `build` runs.
