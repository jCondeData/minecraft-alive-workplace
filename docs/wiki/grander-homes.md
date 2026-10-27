# Grander homes

What a home is worth to the [classes](classes.md): every home has a grade, and three new houses give the Artisans, the
Burghers and the Nobles the homes their class asks for: the Artisan's House, the Burgher's Townhouse and the Noble's
Manor. Part of Classes and luxuries (1.8).

Roadmap items: 34.15, 34.15a, 34.15b, 34.16, B106

## What a player sees

A villager's home can be chosen for them: **Bed ▾** in the Village Hall's People table lists the village's beds (free
ones first, with the house and which bed, or how far and which way from the hall; then the taken ones, to swap) and a
click makes one theirs at once, the old bed freed (see `village-hall.md`, 40.4). The home a bed gives them, and its
grade, follow from the building it's in, as below.

Three new houses, all **sold by builders only** (not in the Blueprint Table): a builder sells the Artisan's House from
Apprentice on, the Townhouse once the village round the nearest hall has a household of Artisans or higher and the
Noble's Manor once it has a household of Burghers or higher, and each upgrade comes from the builder who finishes the
tier below:

- **Artisan's House** (a grade I home). A narrow house under a red tile gambrel roof (a barn-shaped roof, steep low
  down and shallow on top), its gable to the street with a loft door and a hoist beam with a lamp on it: a stone
  workroom below (a workbench, an anvil, a chest of stock, a ladder up) and an upper floor clad in pale weatherboards,
  jettied a block out over the street on brackets, with two beds. A brick chimney runs up the side.
- **Artisan's House II** (grade II). A stone workshop wing at the back, through a doorway in the back wall: a long
  bench, a loom, a barrel and a third bed in the corner, under its own lower red gambrel.
- **Burgher's Townhouse** (grade II). Three stories: a rusticated stone shop below with a shop window either side of
  the door and a stall board under each, a counter and shelves inside; two stories of warm sandstone stucco above
  between stone quoins and string courses, the first jettied over the street with a little iron balcony at each
  window, with the main bedroom (two beds) and a second bedroom (one bed); a loft in a slate mansard roof (steep sides
  with dormer windows, a flat top); a brick chimney up the side.
- **Townhouse II** (grade III). A walled courtyard garden behind, through a back door: brick walls with stone caps, a
  gate, flower beds in their own soil, an azalea, a bench and a lamp; and the loft fitted out as a third bedroom.
- **Townhouse III** (grade III). A stone corner tower, a block proud of the street front, four stories up to a study
  under a slate cap: shelves of books, a desk, a reading chair and windows every way, reached from the main bedroom
  and up ladders.
- **Noble's Manor** (a grade III home). A stone house of two stories standing back behind a graveled forecourt with
  clipped hedges and two lamps. Inside, a double-height hall with a chequered floor, a three-wide grand stair up to a
  gallery and a chandelier; the dining room (a long table, chairs down both sides) to the west and a parlor to the
  east; upstairs the main bedroom (two beds) and a second bedroom. Andesite quoins, dark oak string courses, a hipped
  slate roof with two dormers front and back and a tall chimney stack at each end.
- **Manor II** (grade III). A walled formal garden behind, through the back door: gravel walks round a fountain and
  across to the side walls, four parterres of clipped hedges with flowers inside them, lamps at the walks' corners and
  a gate at the back. **Its garden counts 3 beauty for the people who live there**, which is a Noble's beauty need on
  its own.
- **Manor III** (grade III). A gatehouse on the street (an arched passage with a porter's room over it, under a slate
  hip roof), the forecourt walled in, and a **ballroom wing** on the east: one tall room with a chequered floor, tall
  windows between pilasters, three chandeliers, a musicians' dais with a jukebox and note blocks, under a roof hipped
  like the house's, through a doorway from the parlor. **The Noble's Ball is held in the ballroom.**

**Every kind of home looks different** (26.11): the Starter Cottage is the timber-framed one under a spruce roof; the
Stone House a stone croft with crow-stepped gables (the gable walls rise above the slate roof in steps), a chimney
breast and a woodpile, rising a full story in stone at II and growing a crow-gabled wing at III; the Terrace a brick
row with a stone string course and cornice, a bay window and a hooded door for each home, the middle home's front
plastered white, a chimney stack over each party wall and dormers in a dark oak roof; then the three grander homes
above. Their beds, chests and rooms are where they were, so homes already built keep working.

The hall's "What next?" page names the households that want a grander home: "Dara and Tomas are Burghers and want a
grade II home", or "Bo could be an Artisan and wants a grade I home" when the home is all they lack to rise. With a
Steward, he proposes upgrading their house, or building an Artisan's House, a Townhouse or a Manor in a Homes zone.

In a village with a Noble household every other festival is a Noble's Ball ([classes](classes.md)): the guests gather
in a Manor III's ballroom, in a Manor I or II's hall when the village has no ballroom, and at the Village Hall when it
has no Manor.

## How it works

A home is the finished building a villager's bed is in (`Homes`). Its **grade** is its blueprint's tier
(`<name>_2` is II, `<name>_3` III), unless a data file gives it more: `data/<ns>/homes/<name>.json` holds
`{"blueprint": "aliveworkplace:townhouse", "grade": 2}`. That blueprint, in any style, is that grade, and each upgrade
one more, up to III (`HomeGrades`). A file never lowers a grade, and a blueprint whose own tier is above III keeps it.
The mod ships two files: `aliveworkplace:townhouse` (grade II), so the Townhouse is II and its II and III are both III,
and `aliveworkplace:manor` (grade III, every tier). A file with a grade outside 1 to 3 is skipped with a warning naming
it.

**A home's garden** (34.16): a home file may add `"garden": {"tier": 2, "beauty": 3}`. From that tier on, the people
whose bed is in the building count that much beauty on top of the decorations near their bed (`ClassNeeds.beauty`,
what the classes' `beauty` need reads). `tier` defaults to 1; a negative beauty skips the file with a warning. The
Manor's file gives Manor II and III a garden of 3.

The grade is what the classes' `home` need reads (an Artisan needs grade I, a Burgher II, a Noble III), what the
moods read (a grade II home +5, III +10) and what the "homes" tip counts.

**Who wants a grander home** (`HomeWants`): a household (a couple counts as one, at the lower of their two homes)
whose class needs a higher grade than the home they sleep in; or a household one step below a class that has
everything that class needs except the home. Up to three of them get a tip on "What next?":
`advice.aliveworkplace.grand_home` ("... are Burghers and want a grade II home"), `advice.aliveworkplace.grand_home_one`,
`advice.aliveworkplace.grand_home_rise` and `advice.aliveworkplace.grand_home_rise_one`. With classes off there are no
such tips.

**Builders sell them.** A builder from Apprentice up sells the Artisan's House (10 emeralds); once the village round
the nearest hall has a household of Artisans or a higher class, every builder there sells the Townhouse (14
emeralds); once it has a household of Burghers or a higher class, every builder there sells the Manor (20
emeralds). The offers are added when the builder levels up or a player opens their trades, never twice. The Burghers'
traveling class trader on market days (34.7) can also bring the Townhouse or the Manor.

**The ball's venue** (`NobleBalls.venue`): the first finished Manor III (any style) within the hall's reach, at the
middle of its ballroom floor (`NobleBalls.BALLROOM`, 20, 1, 11 from the blueprint's origin, turned and mirrored with
the build); else the middle of a finished Manor I or II (also turned and mirrored with the build, B440: it was once only
turned, so a mirrored Manor's guests gathered about a Manor's width outside it); else the hall.

**The Steward** (27.6) has a new condition, `households_want_grade {grade, at_least}`: at least that many households
want a home of that grade or higher and sleep in a lower one; it counts the same households as the tips. Three new
rules use it:

- `aliveworkplace:homes_class` (a household wants grade II): upgrade the home of a household that wants a grander
  one, when its next tier is of a higher grade than they have (a new `for_class` field on the `upgrade` effect);
- `aliveworkplace:homes_class_artisans_house` (a household wants grade I): an Artisan's House in a Homes zone;
- `aliveworkplace:homes_class_townhouse` (two households want grade II, a Village or bigger): a Townhouse in a Homes
  zone;
- `aliveworkplace:homes_class_manor` (34.16; a household wants grade III, a Town or bigger): a Manor in a Homes zone.

## Switches

| Key | Default | What it does |
|---|---|---|
| `villageClasses` | on from 1.0 | With classes off nobody wants a grander home: no tips, the Steward's condition never holds and builders don't sell the Townhouse or the Manor. |

## Saved data

None new. A Steward proposal saved before this reads `for_class` as false. The ball's venue and a home's garden are
worked out from the finished buildings already saved.

## Items, blocks, jobs, commands

- Blueprints `aliveworkplace:artisans_house`, `aliveworkplace:artisans_house_2`, `aliveworkplace:townhouse`,
  `aliveworkplace:townhouse_2`, `aliveworkplace:townhouse_3`, `aliveworkplace:manor`, `aliveworkplace:manor_2`,
  `aliveworkplace:manor_3` (`blueprint.aliveworkplace.townhouse` and the others), drawn in `tools/blueprints/houses.py`.
  On the village map they are homes.
- Data files `aliveworkplace:townhouse` and `aliveworkplace:manor` in `homes/`.
- Steward rules `aliveworkplace:homes_class`, `aliveworkplace:homes_class_artisans_house`,
  `aliveworkplace:homes_class_townhouse`, `aliveworkplace:homes_class_manor`.

## Decisions

- 34.15: the Townhouse's II and III are both grade III (the roadmap's "each upgrade one more, up to III"); III adds
  the study tower, not a grade.
- 34.15a (the owner, 2026-10-12): the Artisan's House and the Townhouse, every tier, are sold by villagers only
  (`StarterBlueprints.SOLD_ONLY`): the Table neither lists them nor hands one out when asked; operators' commands with
  everything still see them. A blueprint already bought, a build site and a finished house in a save keep working,
  since the blueprints still load by id; the Steward still builds them for his village by his rules. A wandering
  trader's market-day blueprint may still be one (lane d, 2026-10-12).
- 34.15: the tip names a household whose own class wants more than its home, and also one a step below that lacks
  only the home, since a household can't rise into a class whose home need fails (lane-d, 2026-10-11).
- 34.15: a couple's home grade is the lower of their two beds' buildings, as a class need holds for a household only
  when it holds for both.
- 34.16: every tier of the Manor is grade III (the Noble's need); II and III add the garden's beauty and the ballroom.
- 34.16: the Manor is sold by builders once the village has Burghers (or higher); its Steward rule needs a Town, the
  Noble's own rank need.
- 34.15b (the owner, 2026-10-13): the Manor, every tier, is sold by villagers only like the other two
  (`StarterBlueprints.SOLD_ONLY`): the Table neither lists it nor hands one out when asked. A Manor blueprint already
  bought, a Manor site in progress and a finished Manor keep working (they load by id), its upgrades still come from
  the builder who finished it, and the Steward still builds it by `homes_class_manor` (lane b, 2026-10-13).
- 34.16: the garden's beauty counts only for the people whose bed is in the Manor (a home file's `garden`), not for the
  whole village like a decoration, as the roadmap says.
- 34.16: with no ballroom the ball gathers in a Manor I or II's hall (its middle), as 34.7 did, before the Village Hall.
- 34.16: the Manor is 18 tall so every tier fits the 30 x 20 x 30 test area a builder builds it in.
- Renders of every tier in the drawn look and the five styles (stonework, sandstone, dark oak, cherry, grand):
  [front](../media/grand-houses-front.png), [back](../media/grand-houses-back.png); the Manor (I-III, as drawn and in
  the five styles): [front](../media/manor-front.png), [back](../media/manor-back.png), [above](../media/manor-top.png).
  Renders of a style use
  `tools/blueprints/styles.py`, which applies the style files' swaps to a drawing.
- B106: a style that turns a block into one standing on an axis (stone into deepslate in dark oak) gives it the
  game's default, upright, in renders too; before, the dark oak renders showed the chimneys as floating tops. In game
  they were always whole; `StyleGameTests.chimneysStayWholeInEveryStyle` checks every chimney in every style.

## Known limits

- The garden's beauty is counted for the Manor's people only from the home file; the garden blocks themselves aren't
  checked, so a Manor II whose garden was dug up still counts it while the build is on record.
- The tip and the trades read classes, so they wait for 1.8's classes to be on in a world.
- A data pack's class name without a `.one` or `.plural` lang key shows its plain name in the tip.

## Proof

- `HomeGradeGameTests.aBedInTheTownhouseCountsIIAndInItsUpgradeIII`, `HomeGradeGameTests.aDataFileOverridesATier`,
  `HomeGradeGameTests.householdsWhoOutgrowTheirHomeAskForAGranderOne`, `HomeGradeGameTests.theHomesClassRulesLoad`,
  `HomeGradeGameTests.buildersSellTheGranderHomes`.
- `ManorGameTests.theManorIsGradeIIIAndItsGardenCountsThreeBeauty`, `ManorGameTests.aHomeFileCanGiveItsGardenBeauty`,
  `ManorGameTests.buildersSellTheManorOnceTheVillageHasBurghers`, `ManorGameTests.theBallGathersInTheBallroomWhenThereIsOne`,
  `ManorGameTests.theManorIsSoldNotInTheTable`.
- B440: `QaMirroredManorBallGameTests.theBallGathersInsideAManorPlacedAnyWay` (every Manor tier, every turn, mirrored
  and not: the venue is inside the Manor), failing before the fix.
- `BuilderGameTests.buildsArtisansHouse`, `BuilderGameTests.buildsArtisansHouseII`, `BuilderGameTests.buildsTownhouse`,
  `BuilderGameTests.buildsTownhouseII`, `BuilderGameTests.buildsTownhouseIII`, `BuilderGameTests.buildsManor`,
  `BuilderGameTests.buildsManorII`, `BuilderGameTests.buildsManorIII` (in the huge area): a builder builds each tier from bare
  ground with exactly the materials it needs.
- QA (2026-10-26): `QaGranderHomesGameTests.badHomeFilesAreSkippedAndTheGoodOneStillLoads`,
  `QaGranderHomesGameTests.upgradesAddOneUpToIIIAndATierAboveIIIKeepsIt`,
  `QaGranderHomesGameTests.aCoupleWantsAGranderHomeWhileEitherBedIsInALowerOne`,
  `QaGranderHomesGameTests.noMoreThanThreeHouseholdsGetATip`, `QaGranderHomesGameTests.theTipReadsRightForEveryClassAndCount`,
  `QaGranderHomesGameTests.openingABuildersTradesAddsTheGranderHomesOnce`,
  `QaGranderHomesGameTests.onlyTheNearestHallsVillageBringsTheTownhouse`.
- Showcase scenes: `grand_houses`.
