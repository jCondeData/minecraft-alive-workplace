# Marching on the lair

ROADMAP 32.13. Part of Milestone 32 (1.6), so it is **off for players until the milestone is finished**
(`Expansions.M32`); GameTests and the showcase run with it on. How a player takes the fight to a lair (see
[Lairs and the Defense page](lairs.md)) with the village's guards (see [Raids and threats](raids.md)) and hired swords.

Roadmap items: 32.13, B117

## What a player sees

- **"Muster a war party"** on the hall's **Defense page** (the white banner in the third row). It says how many guards
  would march and how many keep the walls. A click takes **three in four** of the village's guards, nearest the hall
  first, **at most 12** (a scout out and hired swords don't count), enlists them on the **Rally Banner** in your
  inventory and **raises it**; they gather by the hall and follow you, as a raised Rally Banner always makes them. With
  no Rally Banner the chat says "Craft a Rally Banner first: the war party gathers under it"; with one guard there is
  no one to spare.
- **"Hire the Siege-breakers"** (the crossbow beside it): five hired swords in iron, **two with crossbows, two knights**
  (shields in the village's colors) **and a medic** with healing potions, for **30 emeralds, 3 less a level of
  Commerce** (or CobbleDollars). They are named "Siege-breaker", follow the player who hired them (and go on your Rally
  Banner while it has room), and stay **until the second dawn**, or **the dawn after the lair falls** if that comes
  first. One band of hired swords at a time, as with the hall's mercenaries.
- **At the lair** the band **turns out** to meet the war party: six at a time from the tents, more as they fall,
  until all its strength is spent. At a **City**, the captain leaves his fire and fights **at the front**.
- **Kills at a lair** earn a guard **double experience** (Masters are made here).
- **When the lair breaks** with a war party at it (or guards who killed there): its loot chest is yours as always; the
  chronicle says who led the war party and names **every guard who killed there**; the village has **10 days' peace**
  instead of 5; and every villager is **+10 happier for two days**: "the warband is broken". Chat says so too.

## How it works

`guard/WarParties`. The muster sorts the village's guards by distance to the hall, takes `size * 3 / 4` (at most
`RallyBannerItem.MAX_GUARDS`), writes them to the banner's `rally` component with `raised`, and asks `Escorts` to scan
at once. Siege-breakers are mercenaries (`MERCENARY_UNTIL`, so `Mercenaries.tick` sends them away) with two entity
tags: `aliveworkplace_siege_breaker` and `aliveworkplace_hired_by.<uuid>`; `Escorts.leader` falls back to the hirer for
them. Every second, for each loaded lair, the guards marching (on a raised banner, or siege-breakers) within 48 blocks
and their leaders are the war party; `Lairs.turnOut` tops the band up to six (never past its strength at home) and sets
those without a target on the nearest of them, and at a City frees the captain. A lair mob killed by a guard within 48
blocks of its lair pays the kill's XP again and puts the guard's name on the list for that village. `Lairs.onBroken`
gives the rewards: `Lairs.rest(hall, 10)`, the mood until two days on, the chronicle line, and the siege-breakers'
stay cut to the next dawn.
Every villager made a guard on the spot (the hall's mercenaries, the Siege-breakers, `BanditKingStory.ally`, the reform's
old soldier) gets a fresh brain as it is made, as `Relief` does, so its brain has the guard's CORE package with
`GuardCombat` and it fights at once instead of after a reload (B117).

## Switches

All of it is behind Milestone 32's gate (`Lairs.live()`): before it opens the Defense page doesn't open, a muster does
nothing, kills at a lair pay once, and a broken lair gives the usual 5 days.

## Saved data

Enlistment lives on the Rally Banner (its `rally` component, as before). A siege-breaker's stay and hirer are on the
villager (the `MERCENARY_UNTIL` attachment and entity tags). `aliveworkplace_war_parties` per dimension: the names of
the guards who killed at each village's lair, and until when each village is glad. Missing keys read as empty; a
world without the file has neither.

## Items, blocks, jobs, commands

Nothing new: the Rally Banner, guards and mercenaries are the existing ones.

## Decisions

- "Three in four" rounds down: two guards send one, one guard sends none (the walls are never left empty).
- The muster replaces whoever was on the banner with the chosen guards, keeping siege-breakers while there is room.
- Siege-breakers join the banner only while it has room (12): a full war party leaves them following the player
  through their own tag, which works the same.
- "The war party there" is the guards marching within 48 blocks of the lair, or any guard who killed there: a party
  that lost every guard still earns its rewards.

## Known limits

- The band's targets are set when it turns out; brain-driven mobs (piglins) may pick their own.
- In a City the captain chases the war party freely; if it retreats he may follow it a long way.

## Proof

`WarPartyGameTests` (4): the muster enlists three of four guards on the banner and raises it, twelve of twenty, says
to craft a banner without one, and does nothing with M32 closed; the Siege-breakers are two crossbows, two knights and
a medic for 30 emeralds, stay to the second dawn (kept through a save and load of one) and leave at the next dawn when
the lair falls; a lair broken with guards on a raised banner gives 10 days' rest, extra XP for kills there (none with
M32 closed), a chronicle line naming the player and both guards, and the mood (through a save and load); the band
turns out six at a time and goes for the war party. Scene `war_party`.
