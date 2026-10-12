# /workplace blueprints

```infobox
type: Command
Syntax: /workplace blueprints
Who: operators
Shows: every blueprint on the server, by its id
categories: Commands, Builder
related: command-blueprint.md, command-import.md, builder.md, game-rules.md
```

Lists every blueprint the server knows, in chat: how many there are ("... blueprints available:"), then one id a line, such as
`aliveworkplace:well` or `aliveworkplace:stone_house_2`. Imported and scanned builds are in the list too.

## When to use it

It's the operator's catalog: look up the exact id of a build, then hand yourself a copy with
[/workplace blueprint](command-blueprint.md). Players don't need it: the Blueprint Table shows the same library with
pictures and material lists (see [The Builder and blueprints](builder.md)).

## How it interacts

- **Fed by** the mod's own blueprints, files brought in with [/workplace import](command-import.md), uploads at the
  Blueprint Table and builds made with the Scan Tool or the Shape Planner.
- Needs operator rights (permission level 2).
