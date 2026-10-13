# Threat Level

```infobox
type: Threat
image: https://jcondedata.github.io/minecraft-alive-workplace/config/still-1.jpg
caption: The settings screen
Setting: Threat Level
Range: 0 to 3
Default: 2 (normal)
0: nothing comes at all
1: gentle, half as often
3: hard, half again as often and a quarter more raiders
Peaceful: nothing comes, whatever the setting
categories: Defense, Settings
related: raids.md, bandit-camps.md, settings-threats.md, defence.md
```

The **Threat Level** is one dial for how much trouble finds your villages. It works on top of each threat's own on/off
switch, so you can keep raids on but make them rarer, or turn everything up for a harder game.

## The four levels

| Level | What it means |
|---|---|
| 0, none | no [raids](raids.md), no [bandit camps](bandit-camps.md) and no new [bounties](bounty-board.md), ever |
| 1, gentle | raids and camps come half as often |
| 2, normal | the usual (the default) |
| 3, hard | raids and camps come half again as often, and every raid brings a quarter more raiders |

On **Peaceful** difficulty nothing comes, whatever the Threat Level says. The hall's guards icon tells you when
nothing will come to your village.

## Changing it

On the settings screen it's the **Threat Level** slider; in the server's settings file it's `threatLevel`. See
[Threat settings](settings-threats.md) and [Settings](settings.md).

## How it interacts

- **Affects:** how likely a raid is each night and how many raiders come; how likely bandits are to make camp. The
  village's own size, rank and guards still count as usual.
- **Together with the switches:** [Monster Raids](settings-threats.md) and [Bandit Camps](settings-threats.md) off
  still win: a threat that's switched off never comes, however high the level.
- **Not affected:** an operator's [/workplace threat](command-threat.md) order. It starts a raid or a camp whatever the
  level says.
