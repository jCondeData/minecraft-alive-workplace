# Peaceful settings and the threat level

How much trouble comes to villages, server-wide and village by village: the `threatLevel` setting, Peaceful difficulty,
the Defense page's **At peace**, and the operators' `/workplace threat` command. Part of Milestone 32 (1.6); the
threat level and the command work with today's raids and bandit camps too.

Roadmap items: 32.21, B134, B383

## What a player sees

- **The threat level** (Threat Level on the settings screen, 0 to 3, normally 2). At 0 nothing comes: no raids, no
  raiders' camps, no sieges, no disasters. At 1 everything comes half as often, raiders walk in (no rams, ladders or sand
  ramps, so no sieges), witches never hex, and there is no plague. At 3 everything comes half again as often and a
  quarter more raiders come each time, from a raiders' camp too: on hard every camp's band is a quarter bigger.
- **Peaceful difficulty**: nothing comes, whatever the threat level says.
- **At peace** (the Defense page, top right, a white tulip when on): the hall's owner sets the village at peace and
  no raids, raiders' camps or disasters come there. A camp already standing leaves at the next dawn ("The raiders of the
  Bandit camp have packed up and gone: Oakvale is left in peace."). The guards icon says "At peace: no raiders, camps or
  disasters come", and "The Bandit camp leaves at the next dawn" while one still stands. At threat level 0 or on
  Peaceful it says that instead ("Threats are off on this server", "The world is on Peaceful").
- **No new bounties** in a quiet village (At peace, threat level 0 or Peaceful): no captain is posted on its Bounty
  Board; ones already up stay until they are paid or run out (B134, [bounties](bounties.md)).
- **`/workplace threat`** (operators): starts a raid by any culture, sets a lair down, starts a disaster, ends what is
  under way or clears the village, at the nearest Village Hall, for events and testing.

## How it works

- **One question decides "nothing comes"**: `Threats.quiet(level, hall)` is true at threat level 0, on Peaceful, or
  when the hall is At peace. In a quiet village:
  - the raid chance, the lair chance and every disaster's chance are 0 (`Threats.scale`), and so is the fire's
    (lightning, chimney sparks, burning arrows);
  - the hall's raid round takes every attack off the threat clock (no warning of it, and braced gates go back up the
    next morning) and starts none, the Seer's foretold raid included;
  - no lair makes camp; one standing leaves at the first dawn after the village became quiet (`Lairs.leave`: its band and
    captain go in a puff, a portal goes out, a ship sails off, a coven's hex lifts; unlike a lair broken up it leaves no
    rest days and nobody is credited);
  - on Peaceful the game takes a lair's captain away at once; the hall's round, finding him gone without dying in a
    quiet village, sends the lair away in peace then and there rather than breaking it up (a captain who fell was
    told by `Lairs.onDeath` at once, so a player's kill just before still breaks the camp up; off a quiet village a
    captain gone still breaks it up, B364);
  - a disaster under way ends at once, as when it is switched off; covens hex nobody;
  - no bounty is posted (`Bounties.mayPost` and the hall's bounty round ask `Threats.quiet`); open ones stay (B134).
  - no story arc marked `threat` starts (the Sickness's Grey Cough, the Bandit King): `Arcs.tryStart` and
    `Arcs.onEvent` pass it by, while other stories still start; it isn't used up, so it can start again once the village
    is no longer quiet. An operator's `/workplace story start` still starts it (B699).
  A raid already under way when the village turns quiet runs its course.
- **The threat level multiplies the chances** (`Threats.often`): 0, ×0.5, ×1, ×1.5, on top of each system's own
  factors (Curfew, research, a lair near). Hard multiplies the raid's size by 1.25, rounded up, after the usual cap of 16
  (so up to 20), then a lair's band at home caps it as before. So that a lair's raid is a quarter bigger too, on hard
  every lair culture's band is: a lair makes camp with its culture's `strength` times 1.25, rounded up, and grows to
  its `max` times 1.25 (`Lairs.most`; the Defense page's "up to" says so). A lair keeps the strength it has when the
  level changes and grows to the new most from the next dawn (B383).
- **Gentle (1)** leaves the tactics `ram_gates`, `ladders`, `sand_ramps` and `hex` out of every culture
  (`Threats.GENTLE_SKIPS`), so no siege is laid (`Sieges.lays` is false and raiders gather where plain raiders do), and a
  plague never rolls (one under way ends). The piglins still plunder.
- **At peace is set like protection**: the hall's owner or an operator; a hall nobody owns becomes the clicker's. The
  chronicle notes who set it and who lifted it. The setting is on the hall, so it survives restarts.
- **The dawn**: "next dawn" is the day turning over (`Chronicle.day`). At peace keeps the day it was set; for level 0
  and Peaceful the hall's round remembers the first day it saw the village quiet (in memory).
- **The command is an order**: it ignores the switches, the threat level and At peace (a lair set down by a quiet
  village still leaves at the next dawn). `start` refuses while a raid is under way, `lair` while a lair stands and for a
  culture without one, `disaster` while one is under way. `end` calls the raid off (the raiders go, the band's own back
  to their lair, the gates open; nothing goes into the past attacks) and ends the disaster. `clear` does that, sends the
  lair away and empties the threat clock.

## Switches

| Key | Default | What it does |
|---|---|---|
| `threatLevel` | 2 (0 to 3) | 0 none at all; 1 gentle (half as often, no sieges, hexes or plague); 2 normal; 3 hard (half again as often, a quarter more raiders). |
| `villageRaids` | on | The monsters' raids. |
| `banditCamps` | on | The bandits' camps and raids. |
| `raiderCultures` | every culture on | One switch per culture. |
| `sieges` | on | Raids by cultures that ram or climb are sieges. |
| `siegeDamage` | on | Rams break gate blocks. |
| `disasters` | every disaster on | One switch per disaster. |

## Saved data

- On the Village Hall: `atPeace` (false when missing: a hall saved before is not at peace) and `peaceSince`, the day it
  was set (-1 when missing or not at peace). Both are written only while the village is at peace.
- The day a village was first seen quiet at level 0 or on Peaceful is kept in memory only: after a restart the lair
  leaves a day later at most.

## Items, blocks, jobs, commands

- The Defense page's At peace slot (slot 8, a white tulip when on, gray dye when off).
- `/workplace threat start <culture>`, `/workplace threat lair <culture>`, `/workplace threat disaster <id>`,
  `/workplace threat end`, `/workplace threat clear` (operators; ours by their path, such as `bandits`, or a whole id
  such as `aliveworkplace:pillager_warband`).

## Decisions

- At peace follows the protection toggle's rule (owner or operator; an unowned hall becomes the clicker's), as the
  roadmap says "like protection" (32.21).
- Level 0 and Peaceful send a standing lair away at dawn too, not only At peace: "nothing comes" reads as nothing
  staying either (32.21, lane b).
- A disaster under way ends when the village turns quiet, as one switched off does (32.15's rule), and a raid already
  under way runs on (32.21, lane b).
- `threatLevel` isn't behind the expansion gate: it changes today's raids and camps too. The At peace slot is on the
  Defense page, which opens with Milestone 32 (32.21, lane b).
- A lair leaving in peace isn't "broken up": no rest days, no rewards, no bounty (32.21, lane b).
- Hard grows the lair's band, not the raid's cap: a raid of the band at home stays the rule, and the extra raiders are
  real ones at the lair, who can die and whose losses count (B383, lane a).

## Known limits

- At peace can only be set from the Defense page, so players see it once Milestone 32 opens; the threat level and
  Peaceful work now.
- Bandit King arcs and other story threats don't read the threat level.
- At threat level 3 the fire, drought and plague chances are capped at 100% a day.

## Proof

- `ThreatLevelGameTests`: `ThreatLevelGameTests.theThreatLevelIsTwoUnlessTheFileSays`,
  `ThreatLevelGameTests.atThreatLevelZeroNothingStarts`, `ThreatLevelGameTests.atThreatLevelOneThereIsNoSiegeHexOrPlague`,
  `ThreatLevelGameTests.atThreatLevelTwoEverythingIsAsBefore`, `ThreatLevelGameTests.atThreatLevelThreeMoreComeMoreOften`,
  `ThreatLevelGameTests.atThreatLevelThreeEveryLairIsAQuarterBigger` (every lair culture at levels 1 to 3, the growth
  cap, a warband's raid),
  `ThreatLevelGameTests.onPeacefulNothingComes`, `ThreatLevelGameTests.villageRaidsOffMeansNoMonsters`,
  `ThreatLevelGameTests.banditCampsOffMeansNoBandits`, `ThreatLevelGameTests.aCultureSwitchedOffNeverComes`,
  `ThreatLevelGameTests.siegesOffMeansNoSiege`, `ThreatLevelGameTests.siegeDamageOffMeansGatesHold`,
  `ThreatLevelGameTests.aDisasterSwitchedOffNeverStarts`, `ThreatLevelGameTests.atPeaceTheLairLeavesAtDawnAndNoneComes`,
  `ThreatLevelGameTests.atPeaceNoRaidComes`, `ThreatLevelGameTests.atPeaceIsSavedWithTheHall`,
  `ThreatLevelGameTests.theCommandStartsAndEndsEachRaidAndDisaster`, `ThreatLevelGameTests.theCommandClearsTheVillage`,
  `ThreatLevelGameTests.everyNewSentenceHasWords`.
- `QaQuietLairGameTests.aLairStandingWhenTheThreatLevelGoesToZeroLeavesByTheNextDawn`: a bandit camp standing when the
  threat level goes to 0 has packed up and left in peace by the next dawn (not counted as broken up), and no lair comes
  on the four days after.
- `QaPeacefulLairGameTests.aLairStandingWhenTheWorldGoesPeacefulLeavesInPeaceByTheNextDawn`,
  `QaPeacefulLairGameTests.aChiefAPlayerKillsJustBeforePeacefulStillFell`,
  `QaPeacefulLairGameTests.aChiefGoneOutsidePeacefulStillBreaksTheCampUp` (B364): on Peaceful a standing camp leaves in
  peace (also after a save and load), a player's kill just before still counts as the chief falling, and a chief gone
  off Peaceful still breaks the camp up.
- Showcase scenes: `at_peace`.
