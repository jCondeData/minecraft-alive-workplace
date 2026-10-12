# Siege and disaster settings

```infobox
type: Setting
expansion: M32
Sieges: on
Siege Damage: on
Firewardens: on
Well Keepers: on
Physicians: on
In the config file: one switch per disaster and per raider culture
categories: Settings, Defense, Disasters
related: settings.md, settings-threats.md, sieges.md, disasters.md
```

These settings decide how hard a walled village is pressed, and whether the jobs that answer fire, drought and plague
are there. See [Settings](settings.md) for where to change them. The [Threat settings](settings-threats.md) still
decide whether raiders come at all.

## Sieges

Normally **on**: a raid by raiders who ram gates or climb walls, on a village with a finished wall or gate build, is a
[siege](sieges.md). The gates shut, the portcullis drops and the rams go for the gate. Off: every raid is a plain one,
with no shut gates, rams, ladders or sand ramps.

## Siege Damage

Normally **on**: in a siege, rams break the gates of finished walls and gates, and your builders put them back. Off:
gates hold however hard they're hit, and a [village fire](fire.md) burns nothing at all. The `mobGriefing` game rule
off does the same, and also keeps ladders off your walls and pirates' arrows from lighting anything.

## Firewardens, Well Keepers, Physicians

Each normally **on**: villagers can take the [Firewarden](firewarden.md) (a water bucket at a grindstone), the
[Well Keeper](well-keeper.md) (a shovel at a cauldron) or the [Physician](physician.md) (a glistering melon slice at a
brewing stand) job. Off: the workstation no longer offers the job, and those already hired stand idle.

## In the config file only

- **`disasters`**: one line per disaster, `village_fire`, `drought` and `plague`, each `true`. Set one to `false` and
  that [disaster](disasters.md) never comes; one under way ends at once.
- **`raiderCultures`**: one line per kind of raider, each `true`: `monsters`, `bandits`, `pillager_warband`,
  `desert_raiders`, `drowned_pirates`, `piglin_incursion` and `witch_coven`. Set one to `false` and those raiders
  make no camp, and a camp of theirs that stands sends no raids.

## How it fits in

- The [Threat Level](threat-level.md) sits over all of these: at 0 nothing comes; at 1 there are no sieges, hexes or
  plague.
- A village set **At peace** on its [Defense page](defense-page.md) gets no raids, camps or disasters, whatever these
  say.
