# Grander homes

What a home is worth to the [classes](classes.md): every home has a grade, and two new houses give the Artisans and
the Burghers the homes their class asks for, the Artisan's House and the Burgher's Townhouse. Part of Classes and
luxuries (1.8).

Roadmap items: 34.15

## What a player sees

Two new houses in the Blueprint Table, sold by builders too:

- **Artisan's House** (a grade I home). A narrow house with its gable to the street: a stone workroom below (a
  workbench, an anvil, a chest of stock, a ladder up) and an upper floor in a spruce frame with birch infill, jettied a
  block out over the street on stair brackets, with two beds under a dark oak roof. A stone chimney runs up the side.
- **Artisan's House II** (grade II). A stone workshop wing at the back, through a doorway in the back wall: a long
  bench, a loom, a barrel and a third bed in the corner, under its own lower roof.
- **Burgher's Townhouse** (grade II). Three storeys with a slate gable to the street: a brick shop below with a shop
  window either side of the door and a stall board under each, a counter and shelves inside; two storeys of dark oak
  and white plaster above, the first jettied over the street, with the main bedroom (two beds) and a second bedroom
  (one bed); a loft under the roof; a brick chimney up the side.
- **Townhouse II** (grade III). A walled courtyard garden behind, through a back door: brick walls with stone caps, a
  gate, flower beds in their own soil, an azalea, a bench and a lamp; and the loft fitted out as a third bedroom.
- **Townhouse III** (grade III). A stone corner tower, a block proud of the street front, four storeys up to a study
  under a slate cap: shelves of books, a desk, a reading chair and windows every way, reached from the main bedroom
  and up ladders.

The hall's "What next?" page names the households that want a grander home: "Dara and Tomas are Burghers and want a
grade II home", or "Bo could be an Artisan and wants a grade I home" when the home is all they lack to rise. With a
Steward, he proposes upgrading their house, or building an Artisan's House or a Townhouse in a Homes zone.

## How it works

A home is the finished building a villager's bed is in (`Homes`). Its **grade** is its blueprint's tier
(`<name>_2` is II, `<name>_3` III), unless a data file gives it more: `data/<ns>/homes/<name>.json` holds
`{"blueprint": "aliveworkplace:townhouse", "grade": 2}`. That blueprint, in any style, is that grade, and each upgrade
one more, up to III (`HomeGrades`). A file never lowers a grade, and a blueprint whose own tier is above III keeps it.
The mod ships one file, `aliveworkplace:townhouse` (grade II), so the Townhouse is II and its II and III are both III.
A file with a grade outside 1 to 3 is skipped with a warning naming it.

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
emeralds). The offers are added when the builder levels up or a player opens their trades, never twice. The Burghers'
traveling class trader on market days (34.7) can also bring the Townhouse.

**The Steward** (27.6) has a new condition, `households_want_grade {grade, at_least}`: at least that many households
want a home of that grade or higher and sleep in a lower one; it counts the same households as the tips. Three new
rules use it:

- `aliveworkplace:homes_class` (a household wants grade II): upgrade the home of a household that wants a grander
  one, when its next tier is of a higher grade than they have (a new `for_class` field on the `upgrade` effect);
- `aliveworkplace:homes_class_artisans_house` (a household wants grade I): an Artisan's House in a Homes zone;
- `aliveworkplace:homes_class_townhouse` (two households want grade II, a Village or bigger): a Townhouse in a Homes
  zone.

## Switches

| Key | Default | What it does |
|---|---|---|
| `villageClasses` | on from 1.8 | With classes off nobody wants a grander home: no tips, the Steward's condition never holds and builders don't sell the Townhouse. |

## Saved data

None new. A Steward proposal saved before this reads `for_class` as false.

## Items, blocks, jobs, commands

- Blueprints `aliveworkplace:artisans_house`, `aliveworkplace:artisans_house_2`, `aliveworkplace:townhouse`,
  `aliveworkplace:townhouse_2`, `aliveworkplace:townhouse_3` (`blueprint.aliveworkplace.townhouse` and the others),
  drawn in `tools/blueprints/houses.py`. On the village map they are homes.
- Data file `aliveworkplace:townhouse` in `homes/`.
- Steward rules `aliveworkplace:homes_class`, `aliveworkplace:homes_class_artisans_house`,
  `aliveworkplace:homes_class_townhouse`.

## Decisions

- 34.15: the Townhouse's II and III are both grade III (the roadmap's "each upgrade one more, up to III"); III adds
  the study tower, not a grade.
- 34.15: both houses are in the Blueprint Table for everyone, like the luxury workshops (34.13), as well as sold by
  builders; the trades are a second way to get them, not a lock (lane-d, 2026-10-11).
- 34.15: the tip names a household whose own class wants more than its home, and also one a step below that lacks
  only the home, since a household can't rise into a class whose home need fails (lane-d, 2026-10-11).
- 34.15: a couple's home grade is the lower of their two beds' buildings, as a class need holds for a household only
  when it holds for both.
- 34.15: the Manor (grade III) gets its own Steward rule with 34.16; until then grade III is reached by upgrading.
- Renders of every tier in the drawn look and the five styles (stonework, sandstone, dark oak, cherry, grand):
  [front](../media/grand-houses-front.png), [back](../media/grand-houses-back.png). Renders of a style use
  `tools/blueprints/styles.py`, which applies the style files' swaps to a drawing.

## Known limits

- No Steward rule builds a grade III home yet (the Manor, 34.16); the Steward upgrades a Townhouse I to II instead.
- The tip and the trades read classes, so they wait for 1.8's classes to be on in a world.
- A data pack's class name without a `.one` or `.plural` lang key shows its plain name in the tip.

## Proof

- `HomeGradeGameTests.aBedInTheTownhouseCountsIIAndInItsUpgradeIII`, `HomeGradeGameTests.aDataFileOverridesATier`,
  `HomeGradeGameTests.householdsWhoOutgrowTheirHomeAskForAGranderOne`, `HomeGradeGameTests.theHomesClassRulesLoad`,
  `HomeGradeGameTests.buildersSellTheGranderHomes`.
- `BuilderGameTests.buildsArtisansHouse`, `BuilderGameTests.buildsArtisansHouseII`, `BuilderGameTests.buildsTownhouse`,
  `BuilderGameTests.buildsTownhouseII`, `BuilderGameTests.buildsTownhouseIII`: a builder builds each tier from bare
  ground with exactly the materials it needs.
- Showcase scenes: `grand_houses`.
