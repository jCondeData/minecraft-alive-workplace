# /workplace friend

```infobox
type: Command
Syntax: /workplace friend add <player>, /workplace friend remove <player>, /workplace friend list
Who: everyone
Friends may: give orders to your builders, cancel your builds and build in your protected villages
categories: Commands, Village Hall
related: village-protection.md, command-cancel.md, game-rules.md, builder.md
```

Your crew list. Add a friend and they can work with your villagers as if they were yours.

- `/workplace friend add Steve`: "Steve can now give orders to your builders and cancel your builds."
- `/workplace friend remove Steve`: "Steve can no longer give orders to your builders."
- `/workplace friend list`: everyone on your list, or a reminder of how to add someone.

## What a friend may do

- give orders to your builders and other workers, and cancel your builds and quarries
  ([/workplace cancel](command-cancel.md));
- build, break and open things in your [protected villages](village-protection.md).

It goes one way: adding Steve lets him work with your things, not you with his.

## How it interacts

- **Builder ownership.** With the `workplaceBuilderOwnership` [game rule](game-rules.md) on (the default), a builder
  only takes orders from the player who hired them, their friends and operators. Off, anyone can, and the list matters
  only for protection.
- **Protection.** It's the same list a [Village Hall](village-hall.md)'s owner uses for
  [village protection](village-protection.md).
