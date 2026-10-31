# Village protection

```infobox
type: Threat
image: https://jcondedata.github.io/minecraft-alive-workplace/hall_treasury/still-1.jpg
caption: The treasury and protection on the hall's screen
Who turns it on: the hall's owner (whoever placed it), or an operator
How: shift-click the hall's name icon
Area: the Village Hall's reach, at any height (64 blocks by default)
Who may build there: the owner, their friends and operators
Default: off on every hall until its owner turns it on
Setting: Village Protection (on)
categories: Defense, Village Hall
related: village-hall.md, command-friend.md, settings-threats.md, settings-reach-and-range.md
```

On a shared server, other players can be a bigger threat than zombies. **Village protection** lets the owner of a
[Village Hall](village-hall.md) lock the village against griefing, while everyone is still welcome to visit and trade.

## Turning it on

Whoever placed the hall owns it. Open the hall and **shift-click its name icon** (top left): "Oakvale is protected:
only you, your friends and operators can build, break or open things in it". Shift-click again to open it up. Every
hall starts unprotected. A hall from before this, or one placed by a machine, belongs to the first player who turns its
protection on.

## What it protects

Inside the hall's area, as far as its reach goes across the map and at any height, only the owner, their
[friends](command-friend.md) and operators may:

- break or place blocks, or empty or fill buckets (wherever the bucket's user stands: what counts is where the water
  or lava would go);
- open chests and other blocks with a screen, or mark them on a [Delivery Note](delivery-note.md) (a postman never
  carries a stranger's route out of the village);
- hurt the villagers, golems, animals, armor stands and item frames, by hand or with arrows, tridents and potions;
- have their own villagers work there: a blueprint, [Quarry Marker](quarry-marker.md) or
  [Field Marker](field-marker.md) whose area reaches into the village is refused, even when its corners were clicked
  outside it;
- open the hall's screen with a Village Ledger.

Anyone else is told whose village it is. **Still open to everyone:** walking in, opening doors and gates, pressing
buttons, ringing the bell, using a crafting table, trading with the villagers and opening their own mailbox.

## How it interacts

- **Friends.** Add someone with `/workplace friend add <name>` and they can build in all your protected villages
  (and give orders to your builders). See [/workplace friend](command-friend.md).
- **Its size** is the hall's reach: the [Village Hall Reach](settings-reach-and-range.md) setting.
- **Raids are another matter.** Protection is about players. It doesn't keep out [raids](raids.md) or
  [bandits](bandit-camps.md).
- **Turned off:** with [Village Protection](settings-threats.md) off in the settings, nobody can protect a village on
  that server ("Village protection is turned off on this server").
