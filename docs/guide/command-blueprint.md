# /workplace blueprint

```infobox
type: Command
Syntax: /workplace blueprint <id>
Who: operators
Gives: a Blueprint of that build, into your inventory
Example: /workplace blueprint aliveworkplace:fountain
categories: Commands, Builder
related: command-blueprints.md, builder.md, command-import.md, well-build.md
```

Gives you a ready-to-place **Blueprint** of any build on the server: "Gave you a blueprint for Fountain". Tab
completes the id; [/workplace blueprints](command-blueprints.md) lists them all.

## When to use it

For setting up a server, running an event or replacing a lost blueprint without a trip to the Blueprint Table. It gives
the blueprint for free and skips everything the Table asks for, so it's for operators only. An unknown id gets a
message saying so.

## How it interacts

- The blueprint works like any other: place it, and a builder within [Build Distance](settings-reach-and-range.md) of
  their table builds it from the chests (or for free with the `workplaceFreeMaterials` [game rule](game-rules.md)).
- Works for upgrades too (`aliveworkplace:well_2`), for blueprints a builder would otherwise sell, and for builds
  brought in with [/workplace import](command-import.md).
