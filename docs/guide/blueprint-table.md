# Blueprint Table

```infobox
type: Block
image: https://jcondedata.github.io/minecraft-alive-workplace/table/still-1.jpg
caption: The Blueprint Table's library
Recipe: blue dye on top, a cartography table in the middle with planks on each side, and planks in the two bottom corners
Used by: [Builder](builder.md)
Takes a jobless villager: yes, by itself
Supply chests: within 8 blocks
Build Distance: builds within 48 blocks
Mined with: an axe
Stack size: 64
Found in creative tab: yes
categories: Blocks, Builder
related: builder.md, blueprint.md, blank-blueprint.md, scan-tool.md
```

The Blueprint Table is where building starts: it's the Builder's workstation and the server's library of blueprints in
one block.

## Getting one

Craft it from **blue dye** on top, a **cartography table** in the middle with **planks** on each side, and planks in
the two bottom corners. You'll also find it in the mod's creative tab.

## The Builder

Put a Blueprint Table down near a villager without a job and they take it by themselves: they're now a **Builder**.
Chests and barrels within 8 blocks of the table are their supply chests. Hand them a [Blueprint](blueprint.md) and they
build it, block by block, as long as it stands within 48 blocks of the table (a server owner can change that as
**Build Distance** in the [Settings](settings.md)). A blueprint you hold tells you when there's no table close enough.

## The library

Right-click the table to browse every blueprint on the server: its size, exactly what it needs, and a small 3D
picture of the build itself, so you can see it before you pick it.

![The selected build in 3D](https://jcondedata.github.io/minecraft-alive-workplace/table_preview/still-1.jpg)

- **The picture** turns slowly by itself; drag it with the mouse to look round it or tilt it. Big builds shrink to
  fit, and builds you uploaded or scanned get a picture too, drawn from their own blocks. Your game draws it, so it
  costs the server nothing.
- **Get Blueprint** gives you a copy for a [Blank Blueprint](blank-blueprint.md).
- **Upload a File** lists the build files in your `blueprints` folder (`.litematic` or `.schem`) and puts the one you
  pick in the library for everyone. Unless the server allows it with the `workplaceAllowUploads` game rule, only
  operators can upload.
- Builds you save with a [Scan Tool](scan-tool.md) show up here for everyone too.

## How it fits in

- **Fed by** the chests round the table, the village's shared chests within 48 blocks and the
  [Storehouse](storehouse.md). A [Carpenter](jobs-crafts.md) crafts what the builder is waiting for, and the
  Storehouse's board lists every block they're short of.
- **Crews.** Up to three builders with nothing to do join a build near their table and finish it sooner.
- **Safe while they work.** Builders clearing a site never break the table or your chests, and a miner's quarry never
  digs one up.
- **Breaking it.** Mine it with an axe; it drops itself. A builder who has leveled up keeps the job and takes a free
  table nearby by themselves; one who hasn't worked yet may go back to having no job, as in vanilla.

More in [The Builder and blueprints](builder.md).
