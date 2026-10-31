# The Sickness: the Gray Cough

A story arc in which a new illness, the Gray Cough, goes round a village, and the village's healer needs three far
herbs to brew the cure. Part of the 1.5 expansion; the herbs and the Three-Herb Tonic are on [their own page](far-herbs.md).

Roadmap items: 31.18, B135, B343

## What a player sees

- **The Gray Cough comes.** Three villagers fall ill at once: they puff gray smoke, cough with a low, rough sneeze and
  walk slowly. The chat, the hall's chronicle and the villagers' chatter say so, and the hall's villager list shows
  "The Gray Cough" under each who has it.
- **It doesn't pass by itself.** The nurse's honey, milk or healing potion, and the Physician's Remedy (from their
  chest, or from a player's hand), only take the slowness away for a day ("eased ... gray cough for a day" over their
  head); the cough stays. Each night every coughing villager may pass it to
  someone who works or sleeps near them, but never past half the village.
- **Talk to the healer** (right-click them): the village's Nurse, or with no Nurse its [Physician](plague.md). Then
  Doctor Wren isn't sent for. A village with neither gets **Doctor Wren**, a traveling physician in a cleric's robe, who
  comes to the hall the next morning and stays until the story is over.
- **Three far cures.** The healer names the three far herbs whose biomes are nearest the village and gives you a map
  to a patch of each. Bring back 3 of each and right-click the healer; you can hand them in a herb at a time, and
  friends can share the gathering.
- **The remedy.** The healer brews the Three-Herb Tonic for half a day at their brewing stand (Doctor Wren by the
  hall), with "Brewing the cure · 40%" over their head, then walks round with the tonic in hand and gives it to every
  coughing villager, with hearts over each.
- **The ending**: everyone is well, a thanksgiving festival the next evening, +150 standing in the village and the
  honor **Healer of** the village for every gatherer, and +100 friendship with each villager who was ill.
- **Without the cure**: after 15 days the cough runs its course. Everyone is well within 3 days, but the village's
  mood drops 15 for 5 days and the chronicle says it was a hard time. Nobody dies of it.

## How it works

- **The trigger** (`data/aliveworkplace/arcs/the_sickness.json`, an arc marked `threat`): a village of Village rank or
  more with 12 villagers or more, with illness on, at 6% a day. The conditions are `rank_at_least`, `villagers` and the
  new `villager_sickness` (illness switched on).
- **The illness** is a kind of illness (`grey_cough`, saved beside the day the villager fell ill), like a coven's hex.
  While a Sickness story runs in the village, the hall's round keeps the slowness on (unless eased) and the cough
  going; it never passes with time. Once no Sickness story runs there (it ended, or was switched off), the cough lifts
  at the next round. Hardy villagers never catch it, and an ill villager doesn't catch it on top.
- **The night's pass-on**: once a night (from dusk), each coughing villager, at 40%, passes it to one well, grown
  villager who worked or slept within 8 blocks of where they work, sleep or stand (their bed and workstation; where
  they stand if they have neither). Never past half the village's grown villagers, Doctor Wren not counted. It stops
  once the tonic is brewed.
- **The healer** is the village's Nurse nearest the hall; with no Nurse, its Physician nearest the hall (32.18, while
  `physicians` is on: B135). With neither, Doctor Wren is sent for: he comes to the hall at the next day's start, a
  villager in a cleric's robe kept by the hall, and leaves in a puff when the story ends. With a Nurse or Physician he
  never comes. If the healer is lost while players are near, another Nurse or Physician (or Doctor Wren) takes over.
- **A Physician as healer** brews the tonic at their own brewing stand; their house-to-house rounds wait while they
  brew and take it round. A Remedy in their hand when they take up the tonic goes back to the chest by the stand (or
  is dropped there). Before the brew they go on visiting the ill as usual, and their Remedy only eases a cough for a day.
- **The Remedy and the cough**: a Remedy, the Physician's or a player's, only eases the Gray Cough for a day, like a
  nurse's remedy ("... gray cough is eased for a day"), and doesn't keep the villager well; an eased cough is off the
  Physician's round until the day passes. A villager a Remedy kept well (10 days after one) doesn't catch the cough.
- **The herbs**: when the healer is first spoken to, each of the eight herbs' biome tags is looked up once from the
  hall, within 3000 blocks; the three found nearest are the cure, and a herb not found is passed over for the next.
  Each gets a `herb_patch` at the spot found (planted when a player comes near), and every player near the hall gets a
  map to each (anyone who talks to the healer later gets them too, once). If no herb is found at all, the healer makes
  do and the brew begins at once.
- **The hand-in**: a right-click on the healer takes 3 of each wanted herb the player carries; they say what they
  still need. Everyone who hands herbs in is a gatherer. A Gift, a tonic, a Physician's Remedy or a Guild Charter in
  hand (or in the off hand, the main one empty) is given as usual instead of talking to the healer (B343); anything
  else in hand, a sword or a map, still talks to them.
- **The brew** takes half a day (12,000 ticks of the day's time, so sleeping through the night counts) from the hand-in.
  The healer stays by their workstation (Doctor Wren by the hall) and their nurse's or Physician's work waits; a right-click tells how
  far along it is.
- **The cure round**: the healer holds the tonic and walks to the nearest coughing villager, gives it (they are well at
  once), and on to the next; anyone they can't reach in 400 ticks they step over to. When nobody in the village coughs,
  the story ends.
- **Running its course**: 15 days after the story began, without the cure, the cough runs its course: each coughing
  villager is well 1 to 3 days later, the village's mood is 15 lower for 5 days, and the story ends as failed.
- Every step is kept in the arc's saved state (its flags and roles), so the story carries on after a restart.

## Switches

| Key | Default | What it does |
|---|---|---|
| `villagerSickness` | on | Off: the story never starts, and a running one ends quietly with everyone well. |
| `physicians` | on | Off: a Physician doesn't count as the healer, so a village without a Nurse sends for Doctor Wren. |
| `storyArcs` | on | Off: no story arcs at all. |
| `disabledArcs` | empty | Add `the_sickness` to keep just this story from starting. |

## Saved data

- On a villager: the illness kind `ill_kind` is `grey_cough` for the Gray Cough (absent: an ordinary illness, as in
  older saves), and `ill_eased_until`, the game time a remedy eases it until (absent: not eased).
- In the arc's saved state (`ArcState`): the flags `healer_told`, `herbs_given`, `brewed`, `cured`, `spread_night`;
  `ill.<villager>` for each who caught it, `given.<herb>` for each herb handed in, `maps.<player>` for each player given
  the maps; and numbered flags `wren_due.<day>`, `brew_from.<day time>` and `course.<day>`. The healer is the arc's
  `healer` role, Doctor Wren its `wren` mob, the herb patches its `herb_<herb>` places. Older saves have no such arc.

## Items, blocks, jobs, commands

- The story: `aliveworkplace:the_sickness` (start it with `/workplace story start the_sickness` near a hall).
- Doctor Wren is a vanilla cleric villager named `entity.aliveworkplace.doctor_wren`.
- Uses the far herbs and `aliveworkplace:three_herb_tonic` (31.17); the condition `villager_sickness`.
- The arc effect `{"type": "sickness", "step": "outbreak" | "herbs" | "brew" | "ending"}`.

## Decisions

- The Gray Cough is a kind of illness beside the day it began, the same saved field as 32.11's hex, so old saves are
  unchanged and only one field holds what kind of illness it is (31.18).
- "Coughing villagers pass it on" counts a villager whose bed or workstation is within 8 blocks of the cougher's bed,
  workstation or place; villagers with neither count where they stand (31.18, lane c).
- The pass-on stops once the tonic is ready, so the round can end (31.18, lane c).
- The half-village cap and the people it can pass to count grown villagers only, since only grown villagers have the
  hall's illness round (31.18, lane c).
- The healer steps over to a villager they can't walk to within 400 ticks, so a walled-in villager can't hold the
  story up (31.18, lane c).
- A remedy's ease keeps the illness's slower work pace; only the slowness effect goes (31.18, lane c).
- A Nurse comes before a Physician as the healer, since the spec names "its Nurse, or the Physician"; the Physician is
  counted only while `physicians` is on and the disasters expansion is open, as for the rest of their work (B135).
- The Remedy's ten days well keep a villager from catching the Gray Cough, as they keep off the plague and hexes; on a
  coughing villager it only eases, as 31.18 says, and gives no ten days (B135).
- The arc needs illness on through a condition, `villager_sickness`, rather than code in the arc engine (31.18, lane c).

## Known limits

- An arc marked `threat` should not start in a village set At peace or at threat level 0 (31.4), but today it
  still does (B699; failing tests on the tests/threat-arcs-peace-1031 branch).
- Friendship at the end goes only to the ill villagers who are loaded then (in practice, the village the players are
  in).
- Doctor Wren trades as a cleric if someone sneaks and right-clicks him.
- Not tested in a real game yet beyond the GameTests and the showcase scene.

## Proof

- `SicknessStoryGameTests`: `greyCoughPassesOnUpToHalfTheVillage` (40% a night, never past half, never to one who
  wasn't near), `aRemedyOnlyEasesTheGreyCough`, `theNurseIsTheHealer`, `doctorWrenComesWithNoNurse`,
  `theHealerNamesTheNearestHerbs` (a stubbed locator; a herb not found passed over; one lookup each; maps; the hand-in a
  herb at a time), `theBrewAndTheCureRound` (40% status, the round, the festival, +150, Healer, +100 friendship),
  `withoutTheCureTheCoughRunsItsCourse`, `sicknessSwitchedOff`.
- `SicknessPhysicianGameTests` (B135): `thePhysicianIsTheHealer` (no Wren; the herbs, the brew at their stand, their
  rounds waiting, the Remedy in hand stowed, the round), `aPhysiciansRemedyOnlyEasesTheGreyCough` (their visit and a
  player's hand; a kept-well villager doesn't catch it; a Nurse comes first), `physiciansSwitchedOffWrenComes`,
  `thePhysicianHealerSurvivesAReload` (two save-and-reloads mid-arc).
- QA (`QaSicknessGameTests`, 14 tests): the trigger at Village rank and 12 villagers (not 11, not a Hamlet, not with
  illness off), the pass-on at 8 blocks and not 9 and by bed and workstation, the cap in a village of 13, the cough
  and an old-style illness through a save, the hand-in taking only 3 of each named herb, the tonic only for the
  coughing, every gatherer thanked and no bystander, the Nurse killed or the stand broken mid-brew, Doctor Wren
  brewing by the hall and leaving, every sentence a player reads, and the herb biome tags open to datapacks.
- Showcase scenes: `sickness` (the coughing village, a herb patch, the brew, the cure going round).
