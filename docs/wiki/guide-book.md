# The Guide Book

An in-game book that teaches the mod with pictures: every page is a real screenshot of the game with a title and a few
short steps. Every player is given one the first time they join. Part of 1.0 (Milestone 26).

Roadmap items: 26.2a, B18, B29

## What a player sees

- **Getting it.** The first time a player joins, they are handed a Guide Book (no toast; the advancement is hidden).
  Players who were already on a server get it on their next join. With a full inventory it lands at their feet.
  Dying doesn't give a second one. A lost book is crafted shapeless from a book and wheat. The creative tab lists it.
  Its tooltip says "Right-click to read how villagers' jobs work". It stacks to 1.
- **Reading.** Right-click it (with a page-turn sound) and a book opens. Page 0 is the contents: the intro and the seven
  chapters, each a link. Then one page per topic: the picture on top, the page's title, and the steps under it (at most
  six lines). Turn pages with the arrow buttons, the Right/Left arrow keys, Page Down/Page Up, D/A or the mouse wheel;
  Home or Backspace and the Contents button go back to the contents. The book opens where it was left (for this game
  session).
- **Chapters (35 pages, 36 counting the City Plan):** Getting started (welcome, camp, jobs, status); Builders (table,
  place, stock, build, styles, library, shapes); Your village (hall, people, requests, storehouse, villages, city plan);
  Gathering jobs (miner, lumberjack, farmer, orchard, fisher, beekeeper); Making, carrying and trading (carpenter, chef,
  mail, shop, ferry); Keeping the village safe (guard, training, nurse); Pokémon jobs (trainer, tutor, ball smith,
  trader, fossil).
- **Only with the right mods.** The Pokémon chapter shows only when Cobblemon is installed. The City Plan page shows
  only when 1.1's expansion (the Steward) is on.

## How it works

- `guide/GuideBookItem` (core) is the item: on the client its `use` runs `GuideBookItem.open` and plays the sound; on the
  server it only counts the use, so the book is never used up. `AliveWorkplaceClient` sets `open` to open the screen
  (a dedicated server has no screens).
- `client/guide/GuidePages` lists the pages in order (`Page(id, chapter)`), the chapter order, and `shown()`, which
  drops the Pokémon chapter without Cobblemon and `city_plan` without the 1.1 expansion. `client/guide/GuideScreen`
  draws the screen.
- Words are in `en_us.json`: `guide.aliveworkplace.chapter.<chapter>`, `.page.<id>.title` and `.page.<id>.text`, plus
  `contents`, `chapters`, `intro` and the item's name and tooltip. The screen splits a page's text to its width and cuts
  it at the line limit, so a page's words must fit.
- Pictures: `tools/guide/pages.py` says, for each page, which showcase scene and still to use and the crop;
  `tools/guide/build.py` fetches the stills from the published showcase (or `--local DIR`), crops and scales them to
  384 x 216 with a small palette, and writes `textures/gui/guide/<page>.png`. So the pictures are game screenshots, never
  drawn.
- The gift: advancement `aliveworkplace:guide_book` (trigger `minecraft:tick`, no display) rewards the loot table
  `aliveworkplace:gameplay/guide_book`, one book. An advancement is given once, which is why a rejoin or a death
  doesn't repeat it. The recipe is `data/aliveworkplace/recipe/guide_book.json`; it has a recipe-book unlock.

## Switches

None. The only conditions are the installed mods (Cobblemon) and the City Plan expansion.

## Saved data

None of our own: the gift is the vanilla advancement `aliveworkplace:guide_book` in each player's advancements. The
open page is kept only in the client for the game session (the book opens at the contents after a restart).

## Items, blocks, jobs, commands

- Item: Guide Book (`aliveworkplace:guide_book`). No commands.

## Decisions

- 26.2a (owner, 2026-10-02): pictures are in-game screenshots from the showcase scenes, so nobody has to draw them.
- 26.2a: one book for everyone, given once by a hidden advancement; a lost one is crafted from a book and wheat.
- B18, B29: the Beekeepers page's claim that a flower at a composter makes a Florist is held true by tests for every
  `#minecraft:flowers` item, rather than by narrowing the words.

## Known limits

- The screen has no search; it is read page by page or by chapter.
- Pictures are only as current as the stills they were cut from; `tools/guide/build.py` must be run again to refresh them.
- The text is English only here (`en_us.json`).

## Proof

- `GuideGameTests`: a new player gets exactly one book and a second award adds none; the join tick gives it once even
  after a rejoin; a full inventory leaves it at the player's feet; dying gives no second book; a book and wheat make it
  and match no other recipe; using it on the server keeps it; every page has its picture and words; our recipes appear
  in the recipe book once the player holds an ingredient.
- `QaGuideClaimsGameTests`: the pages' job claims hold (every orchard item makes an Orchard Keeper; raw meat and fish
  at a smoker make a Chef). `QaGuideFloristGameTests`: any flower at a composter makes a Florist (B18, then B29's
  `QaB18FlowersGameTests` for all `#minecraft:flowers`).
- Showcase scene `guide` ("The Guide Book": a new player is given the book and every page opens with its picture; also
  checks the words fit). Listed in `tools/showcase/scenes.py`.
