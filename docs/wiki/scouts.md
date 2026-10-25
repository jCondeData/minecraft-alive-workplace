# Scouts

ROADMAP 32.12. Part of Milestone 32 (1.6), switched on for players in 1.0 with every other expansion
(`Expansions.M32`); GameTests and the showcase run with it on. A new kind of guard (see [Raids and threats](raids.md)) who finds
where a lair (see [Lairs and the Defense page](lairs.md)) really is, and brings home a map to it.

Roadmap items: 32.12, B368

## What a player sees

- **Making a scout.** Sneak-right-click a guard with a **spyglass**: one spyglass goes into their **off hand**, as a
  bow makes an archer and a shield a knight, and whatever they held there comes back to you (or drops at your feet).
  The action bar says "... is a Scout now", and **"Scout"** is over their head with their kills. Hand them a bow,
  shield or healing potion and they are that kind again; the spyglass comes back to you.
- **"Scout the lair"** on the hall's **Defense page** (the spyglass in the second row, right of the lair's slots, only
  while a lair stands). It says how many scouts the village has, or how to make one. A click sends the nearest free
  scout. Nobody goes **by night** ("Scouts go out by day"), with **no lair** or with **no scout**; one out already is
  named.
- **The trip.** The scout walks (or rides, if they're in the saddle of a cavalry horse) toward the lair, a stretch at a
  time over the ground, until they are **within 24 blocks** of it. There they stop, **raise the spyglass** (it moves
  into their hand, where a villager shows what they hold), look the camp over for 3 seconds with the spyglass's sound,
  lower it and **come home** to their grindstone. Over their head while out: "scouting the lair: Bandit camp", then
  "watching the lair through the spyglass", then "coming home with a scouting report". The Defense page shows the
  scout out and for how long.
- **Unseen.** A lair's band **never goes for a scout more than 8 blocks off**: they let them watch. Closer than that,
  a scout is fair game like anyone.
- **Home.** The chronicle and chat say the scout is back. The **Defense page** now gives the lair's **exact place**
  ("At 120, 64, -340", on the where slot and the scouting slot) with who found it and when, and on the scouting slot
  the **captain** and the **strength**. In the **chest by their grindstone** is a **Scouting Report**: a filled map
  like an explorer map, drawn between the village and the lair at the smallest scale that shows both (lined up with
  the game's own map squares when one holds both, else centered halfway between them, so both are always on it), with the
  village's mark and a **red X** on the lair, a dark red border color, called "Scouting Report: Bandit camp", its
  tooltip listing the **raiders**, the **captain**, the **strength** and who scouted it on which day. No room in the
  chest: it drops at the scout's feet.
- **Lost.** A scout **not home within a day** (24,000 ticks) is lost: gone, with a line in the chronicle and chat ("...
  went to scout the lair and never came back"). If the camp was broken up while they were out they come home with
  nothing ("the camp was gone").
- **Trailing raiders.** When a raid's raiders **flee at dawn**, a scout of the village trails them home: the lair's
  place becomes known at once (chat, chronicle: "... trailed the fleeing raiders back to their lair"), with no one
  sent and no map.

## How it works

- `guard/Guards.Kind.SCOUT`: a spyglass in the off hand (or in the main hand while raising it). `Guards.makesKind` and
  `Guards.hand` are what the sneak-right-click (`build/BuilderEvents`) does for every kind; a spyglass counts only
  while `Lairs.live()`.
- `guard/Scouts`: the missions, ticked on the server tick. `send` picks the free scout nearest the hall in the
  village's area. A mission goes OUT (legs of at most 16 blocks toward the lair, each on the ground by the heightmap,
  walked with `Walker.requestWalk`; stuck for 10 s, the next leg goes 6 blocks to one side), LOOK (60 ticks, the hands
  swapped) and BACK (to the post, else the hall), then `home`: the lair known, the report into the post's chests
  (`SupplyContainers`), the chronicle. `GuardPatrol` stands aside while a guard is out.
- **Unseen**: `mixin/ScoutUnseenMixin` on `LivingEntity.canAttack`, which every target goal and sensor asks, returns
  false for a mob with the lair tag (or the bandits') and a scout more than 8 blocks off.
- **Trailing**: `VillageRaids.end` calls `Scouts.trail` when a raid ends with raiders fleeing.
- **Known** is per village: the lair's position, the day, whether trailed, and who. A lair that moved (a new one) isn't
  known.

## Switches

The whole of it is behind Milestone 32's gate (`Lairs.live()`): before it opens a spyglass makes no kind and raiders
aren't trailed. The Defense page itself only opens then.

## Saved data

`aliveworkplace_scouts` per dimension: the missions under way (scout, hall, lair, start, phase, look end, seen, name)
and what each village knows (lair, day, trailed, by). Missing keys read as defaults; a world without the file has no
missions and knows nothing. A mission resumes after a restart where it stood.

## Items, blocks, jobs, commands

No new item: the Scouting Report is a vanilla filled map with a name, lore, a map color and two decorations.

## Decisions

- The Defense page already showed the captain and strength before scouting (32.3); scouting adds the exact place and
  a slot with all three rather than hiding what 32.3 shows.
- A lost scout is removed from the world even if still alive somewhere: lost means lost.

## Known limits

- The trip is walked a leg at a time; very rough ground (cliffs, wide water) can make it slow, and a scout stuck for a
  whole day is lost.

## Proof

`ScoutGameTests` (5): the spyglass makes a Scout and gives the shield back (not before M32); sent to a lair 60
blocks off, the scout gets within 24 blocks and is home within 2400 ticks with the report (its red X on the lair) and
the page's exact place; a bandit goes for a plain guard 10 blocks off but not a scout; a mission and what is known
survive a save and load, and nobody goes with no lair, by night or with no scout; fleeing raiders are trailed home.
`QaScoutGameTests` (3, QA lane D): a lair's bandit does go for a scout exactly 8 blocks off and one 5 blocks off; a
desert lair's husk (the lair's tag, not the bandits') goes for a plain guard 10 blocks off but not a scout as far.
Scene `scouts`.
