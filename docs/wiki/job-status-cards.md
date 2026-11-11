# Job status cards

A sneak-right-click with an empty hand on a Builder, Miner, Fisher, Farmer, Lumberjack, Orchard keeper or Postman opens
a small card in the Village Hall's look instead of putting their status lines in chat: their name, job and level, what
they're doing, their work in numbers, what they need, and buttons for the whole status in chat and their page in the
Village Hall. It is the same card as the Nurse's and the Fossil Scientist's ([Status cards](status-cards.md)). Part of
Milestone 42 (One look for every screen).

Roadmap items: 42.5, B1096, B1103

## What a player sees

Sneak-right-click a Builder with an empty hand. The card opens: the name in gold, "Builder · Apprentice" on the right,
and under it a gray line saying what they're doing, in the Hall's own words ("Building the Stone House · Structure ·
working", or what the chat said for an idle builder: "Mason has no building job. Hand them a placed blueprint." or
"Mason has lost their Blueprint Table."). Under **Their work**:

- **Builder**: the build (a blueprint icon) with "42%" and a gold bar; "Skipped" when blocks were skipped; "Next up"
  with the builds queued after it.
- **Miner**: "Quarry 5 × 5 × 12" with its progress and a bar, "Blocks mined", "Skipped" when there are any (an idle
  miner's line is the chat's "has no quarry. Hand them a marked Quarry Marker.").
- **Fisher**: "Fish caught".
- **Farmer**: "Field: 9 × 9", "Crops harvested".
- **Lumberjack**: "Tree farm: 9 × 9", "Trees felled", "Saplings planted".
- **Orchard keeper**: "Orchard: 9 × 9", "Fruit picked", "Saplings planted".
- **Postman**: "Parcels delivered", "Courier routes".

Then for everyone "Experience: 40 / 70 XP" with a green bar to the next level ("Top level" for a Master), and what they
need: a line per request as the Village Hall lists it, with its icon ("Needs Oak Planks: ×24" in orange; a miner's
pickaxe or ladders), three at most, then "and 5 more"; or "Needs: Nothing" in green. A tool or seeds read as part of
the sentence: "Needs a pickaxe", "Needs an axe", "Needs a fishing rod", "Needs seeds" (B1096).

The buttons: **In chat** (gold) puts the old status lines in chat, word for word (the builder's whole material list,
the coordinates, the routes, the pace and the clickable **[Stop]**/**[Cancel]** link); **Hall page** opens the Village
Hall on their own page (the worker's page with Find and Change job, or a jobless villager's free workstations) with
Back to the people list; **Close**. With no Village Hall near them, Hall page is gray and "There's no Village Hall near
Mason." shows under the row; in a protected village that keeps you out it's gray with the village's "only they and
their friends" line. The buttons always stay inside the card (B1103): with room to spare they have the Hall's roomy
width; when they don't fit that way (a narrow card, or longer labels in another language) they shrink to what their
labels need and share the room left out evenly to the card's edge, then wrap onto another row, and a label wider than
the card is cut to it. The hint at the foot: "In chat: every detail, with the Stop link."

The same conditions as before open it: a Fisher only when hired, a Farmer, Lumberjack or Orchard keeper only with a
field, tree farm or orchard; anything in hand still does what it did (a blueprint, a marker, a note, a rod), and a
plain right-click still trades.

## How it works

`card/JobCards` builds each job's `StatusCard` on the server and opens it with `StatusCardMenu.open` (43.3's shared
card, `aliveworkplace:status_card`, drawn by the client's `StatusCardScreen`); `BuilderEvents` calls
`JobCards.open(player, villager, kind)` where it called each job's status (`Builders.sendStatus`, `Miners.sendStatus`, ...) before. What they're doing is
`VillageHallScreen.doing` (the Hall's people list: the builder's synced status, the quarry's state, the job's
`WorkerStatus` line, else their activity); what they need is `Requests.of` (the Hall's requests). For a builder,
`Builders.refreshMissing` counts what's short afresh first, as the chat status did (the same code, moved out of
`Builders.statusText`). In chat calls the job's own `Fishers.sendStatus` and the like, unchanged. Hall page finds the hall with `VillageHalls.nearest`,
checks `VillageProtection.keeper`, and opens `VillageHallScreen.openWorker` (valid while the hall stands, like the
Ledger's remote screen). A button pressed after the villager died or unloaded, or from more than 24 blocks away, does
nothing; the card's menu is no longer valid then, so it closes on the next tick.

## Switches

none

## Saved data

none (the card reads what the jobs already save)

## Items, blocks, jobs, commands

- New text: `screen.aliveworkplace.card.job.*` (heading, percent, skipped, next, quarry, mined, fish, field, tree_farm,
  orchard, size, harvested, felled, planted, picked, delivered, routes, experience, xp, top_level, needs,
  needs_nothing, need, need_count, need_more, hall, chat, no_hall, hint).

## Decisions

- 42.5: built on 43.3's card (`StatusCard`, `StatusCardMenu`, `StatusCardScreen`, `TableStyle`'s small-card pieces), not
  a second one.
- 42.5: the chat lines stay, behind the card's **In chat** button, word for word: the card is only 200 pixels wide and
  can't hold the coordinates, the routes, the builder's whole list or the clickable Stop/Cancel link, so nothing the
  lines said is lost. In chat is the first button, as 43.3 decided (a card's first button does what the click did), so
  the click tests that went through the old click press it (`QaStageSixGameTests` through its helper,
  `QaWonderPartLines1031GameTests.aPartWaitingBehindAnotherBuildIsNamedByItsWonder`), every assertion unchanged. The card always opens on the server's side, so there's
  no other fallback.
- 42.5: "what they're doing" and "what they need" are the Hall's own (`doing`, `Requests.of`), so the card and the Hall
  never disagree.
- B1096: the Hall's request names stand alone in its lists ("Miner: A pickaxe"), so the card's "Needs %s" and the Hall
  page's "Waiting for %s" wrap them through `Requests.inSentence`, which turns a capitalized tool or seeds name into its
  `.in_sentence` form ("a pickaxe"); every other name (an item's, "a remedy …") is used as it is.
- 42.5: Hall page opens the hall from where the player stands (like the Village Ledger), but only for the hall nearest
  the villager and only when the village's protection lets the player in.

## Known limits

- The card is a snapshot: it shows the work as it was when it opened.

## Proof

`JobCardGameTests.builderCardShowsTheSiteAndWhatIsShort`, `JobCardGameTests.builderCardWithoutABenchOrAHall`,
`JobCardGameTests.minerCardShowsTheQuarry`, `JobCardGameTests.fisherCardShowsTheCatch`,
`JobCardGameTests.farmerCardShowsTheField`, `JobCardGameTests.lumberjackCardShowsTheTreeFarm`,
`JobCardGameTests.orchardCardShowsTheOrchard`, `JobCardGameTests.postmanCardShowsTheRoutes`,
`JobCardGameTests.cardClosesWhenTheVillagerDies`, `JobCardGameTests.cardAfterASaveAndReload`, and the click tests of
`QaStageSixGameTests.emptyHandsStillShowTheStatusLine` and
`QaWonderPartLines1031GameTests.aPartWaitingBehindAnotherBuildIsNamedByItsWonder` (now through In chat); B1096's need lines in
`QaJobCard1109cBugTests.qaAMinersPickaxeNeedReadsAsASentence` and `QaJobCard1109cBugTests.otherJobsNeedsReadAsSentences`.

QA (2026-11-09): `QaJobCard1109cGameTests` (only a sneak with an empty hand opens it, an off-hand torch still counts; no
card for a Librarian, a jobless villager, or a Farmer, Fisherman, Lumberjack or Orchard keeper with nothing to show; every
level's tag and experience line; a miner's pickaxe and ladders under Needs; an idle Postman; Hall page in a protected
village for a stranger and for the owner; the card closing when the player walks away or the villager dies; fresh
numbers on reopening).
QA (lane B, 2026-11-09): `QaBuilderCardNeeds1109bGameTests` (3), a builder on the stone house with his chests stocked
from its own materials: everything there reads "Needs: Nothing" in green with no "and N more"; exactly three kinds short
gives three orange Needs lines, each one of those kinds with its count, and no "and" line; four short gives three lines
and "and 1 more". Hand mutant killed: "and N more" from three needs on. The `status_card` scene now opens the card a
step after its staging (a new hall's point of interest is added after the tick that placed it, so the card opened in
that tick had Hall page grayed).

Showcase scenes: `status_card`
