# /workplace import

```infobox
type: Command
Syntax: /workplace import
Who: operators
Reads: the world's aliveworkplace/import folder
Formats: .litematic, .schem and .nbt
categories: Commands, Builder
related: command-blueprints.md, command-blueprint.md, builder.md, game-rules.md
```

Turns build files on the server into blueprints everyone can use. Put `.litematic`, `.schem` or `.nbt` files in the
folder `aliveworkplace/import` inside the world's folder, then run `/workplace import`. Each file becomes a blueprint
named after the file: "Imported castle (21 × 18 × 30)".

## What can go wrong

The command tells you about every file it couldn't take, in plain words:

- blocks from mods that aren't installed become air, and you're told which;
- an old MCEdit `.schematic` has to be saved again as `.schem` or `.litematic` first;
- a damaged, empty or too-big file is skipped with the reason;
- an empty folder: "No blueprint files found. Put .litematic, .schem or .nbt files in" the folder.

## How it interacts

- Imported builds show up in the Blueprint Table for everyone and in [/workplace blueprints](command-blueprints.md),
  and they survive restarts.
- Players can bring in builds themselves with the Blueprint Table's **Upload a File** button, unless the
  `workplaceAllowUploads` [game rule](game-rules.md) is off. See [The Builder and blueprints](builder.md).
