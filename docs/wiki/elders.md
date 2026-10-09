# Elders and the Evergreen Charm

ROADMAP 34.19a (the charm and the passing), on the part of 34.19 it needs. Part of 1.8 (Classes and luxuries): off for
players until 1.8 is finished. The elder look (34.18), the slower walk, the "quiet old age" mood and the elders' chatter
(34.19), retirement (34.20) and the seeding of everyone's age (34.22) are not built yet; this page grows with them.

## What a player sees

A villager who grew up in a village with a Village Hall counts their days. After 120 grown days they are an **elder**:
their card on the hall's list says "Elder · grown 131 days". Forty elder days later their time comes: they pass away in
their sleep (or, with no bed, once night has fallen) and leave a grave. The village is told in chat, the chronicle
notes it ("Bram passed away in their sleep, an elder"), and an Undertaker can bring them back for another 40 days.
From ten days before, the card warns: "Their time comes in 5 days (an Evergreen Charm keeps them)", then "Their time
comes tonight".

The **Evergreen Charm** keeps a good villager for good. It is a gold-and-green leaf pendant, crafted from a totem of
undying, a golden apple, 2 emeralds and a heart of the sea (any arrangement). Sneak-right-click an elder with it:

- an elder with a **good trait** takes it and becomes **Ageless**: totem sparkles, a cheer, "Bram (a Master of their
  trade) takes the Evergreen Charm and will never leave us." The charm is used up (not in creative), the chronicle says
  "Bram will never leave us: Jesse gave them an Evergreen Charm (a Master of their trade)", and their card wears a gold
  leaf badge, "❦ Ageless: will never leave us". They stay an elder in every other way.
- anyone else refuses, with the reason over the hotbar, and the player keeps the charm:
  - "Fenn is not an elder yet (grown 30 of 120 days): the Evergreen Charm is for elders." (or "is not an elder" for a
    child or someone whose age nobody knows);
  - "Dara won't take it. The charm is for an elder who is a Master of their trade, Gifted, a Legend, or happy (mood 80
    or more) for 20 days running. Happy days so far: 19.";
  - "Bram is already ageless.";
  - "Evergreen Charms are switched off on this server." / "Elders don't pass away on this server: nobody needs an
    Evergreen Charm."

## How it works

- **Age.** `adult_since` is the `Chronicle.day` a villager grew up. It is written when a child grows up in a hall's
  round (`Families.round`). Whether someone is an elder is never saved: it is `today - adult_since >= villagerElderDays`
  (`people/LifeStages`). A villager with no `adult_since` (everyone in a world from before 1.8, until 34.22 seeds them)
  is never an elder and never passes.
- **Good traits**, the first that holds: a Legend; Gifted (29.x); Master level (5) in a real trade (not jobless, not a
  nitwit); or 20 **happy days**. Happy days are counted in the hall's round, once a day per villager: a mood of 80 or
  more adds one, a lower mood starts again from 0. Days nobody counted (the village not loaded, moods off) neither add
  nor break the run.
- **Passing.** The hall's round (every 30 seconds) checks each grown villager: an elder with 40 or more elder days who
  is not ageless passes if asleep, or if it is night (13000 to 23000 on the day clock). They die as any villager does
  (damage type `aliveworkplace:old_age`), so their work is handed back, a Legend's slot is freed, and the usual grave is
  left; an elder always gets a grave, even with no job and no name.
- **Brought back.** An elder whose time had come and whom an Undertaker revives gets `passed_day` = that day and 40 more
  days from it (otherwise they would pass again the same night and waste the golden apple). A charm clears it.
- **Ageless** is one saved flag. It only stops the passing.

## Switches

All four belong to 1.8 (`Expansions.M34`): off for players, whatever the file says, until 1.8 is finished.

- `villagerAges` (true): villagers become elders. Off: nobody is an elder, so nobody passes; `adult_since` is kept.
- `villagerElderDays` (120, 20 to 1000): grown days before a villager is an elder.
- `elderPassing` (true; owner, 2026-10-06): elders pass after 40 elder days. Off: nobody dies of old age, and a charm is
  refused as not needed.
- `agelessElders` (true): Evergreen Charms work. Off: charms are refused and kept; elders already ageless stay ageless.

## Saved data

On the villager (attachments; every one optional, so older saves load unchanged):

- `adult_since` (absent: age unknown, never an elder);
- `ageless` (absent: false);
- `happy_streak`: {`days` 0, `day` 0}, the happy days running and the last day counted (absent: none);
- `passed_day` (absent: never brought back from old age).

The chronicle has a new kind, `LIFE` (a clock); an older version reads an unknown kind as `FOUNDED`.

## Items, blocks, jobs, commands

- **Evergreen Charm** (`aliveworkplace:evergreen_charm`): stacks to 1, rare. Recipe `evergreen_charm` (shapeless),
  unlocked by holding a totem of undying or a heart of the sea. Texture drawn in `tools/textures/art/items.py`
  (`evergreen_charm`). In the creative tab after the wines.
- Damage type `aliveworkplace:old_age` (death message "%s passed away in their sleep, an elder").

## Decisions

- 34.19a (owner, 2026-10-06): an elder with good traits can be made eternal with a crafted item; the recipe is ours to
  tune in play.
- 34.19a: with `agelessElders` off, elders already ageless stay so. Turning a switch off should never cost a player a
  villager they paid a totem and a heart of the sea for.
- 34.19a: with `elderPassing` off the charm is refused and kept ("nobody needs one"), not silently used up.
- 34.19a: "a Mood of 80 or more for the last 20 days" is 20 counted days running; days the village wasn't loaded
  don't break the run.
- 34.19a: a revived elder gets 40 more days (see above); this also gives a second chance to hand them a charm.
- 34.19a: an elder leaves a grave even without a job or a name (a retired elder, 34.20, will have no job).
- 34.19a: the hall's card warns from 10 days before, so the player knows when a charm is needed.
- 34.19a: the charm is a sneak-right-click on any villager; it never opens trades and never changes a job.

## Known limits

- Until 34.22 seeds ages, only children who grow up from now on ever become elders.
- A Master who retires (34.20) and loses their level would stop counting as a Master; 34.20 must keep the old level
  or its own flag for this check.
- The happy-day count needs a Village Hall and `villagerMoods` on; without them only Masters, Gifted and Legends qualify.
- An elder in a village whose hall is never loaded at night doesn't pass until it is.

## Proof

- GameTests: `AgelessElderGameTests`: the recipe, tooltip and config (`theCharmIsCraftedAndExplainsItself`); a Master
  through the sneak-right-click, then a Gifted villager, a Legend and a 20-happy-day elder, the charm used up, the gold
  badge, the chronicle, a second charm refused, creative keeps it (`anElderWithAGoodTraitTakesTheCharm`); every refusal
  with its sentence and the charm kept (`anyoneElseRefusesAndTheCharmIsKept`); happy days counted from a real mood and
  reset by a hungry day (`happyDaysAreCountedFromTheMood`); day by day to 45 elder days, the ageless elder alive, the
  others gone on the 40th with graves, a reload, an older save (`anAgelessElderOutlivesFortyElderDaysAndAReload`); the
  passing in the hall's own round with a grave and the chronicle, the revival's 40 more days
  (`anElderPassesInTheirSleepAndLeavesAGrave`); each switch off, and a child grown up getting today
  (`theConfigSwitches`).
- Showcase scene: `ageless_elder` (a refusal, the charm taken, the night after 45 elder days, the badge).
