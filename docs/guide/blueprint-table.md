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

Right-click the table to browse every blueprint on the server: its size, the jobs and beds it brings, exactly what it
needs, and a small 3D picture of the build itself, so you can see it before you pick it. A build with a basement shows its cellar too,
under a faint green line where the ground will be, and its size says how many blocks it goes below ground.

![The selected build in 3D](https://jcondedata.github.io/minecraft-alive-workplace/table_preview/still-1.jpg)

![The starter cottage in Sandstone, picked from the style row](https://jcondedata.github.io/minecraft-alive-workplace/table_preview/still-3.jpg)

- **Search.** Type in the box over the list and it narrows as you type: by a build's name ("inn"), its id
  ("fishers_hut") or a job it brings ("fisherman", or "smith" for every forge), in capitals or not. A build's levels
  stay under it (search "inn" and Inn II and Inn III come with the Inn), the header says how many match, and if nothing
  does, the list says so. If the build you had picked isn't in the results, the first one that is gets picked instead.
  Empty the box to see everything again.

![Searching the library for "smith"](https://jcondedata.github.io/minecraft-alive-workplace/table/still-2.jpg)

- **Jobs and beds.** Under the size, the build says what work it brings: "Jobs: Fisherman (1), Butcher/Chef/Rancher
  (3)". Each entry is one kind of job block; a slash means the jobs share that block (any one of them can work there),
  and the number is how many of that block the build has. "Beds: 2" follows when it has beds. A build with no job block
  and no bed shows neither, and builds you upload, scan or import get the lines too.
- **The picture** turns slowly by itself; drag it with the mouse to look round it or tilt it. Big builds shrink to
  fit, and builds you uploaded or scanned get a picture too, drawn from their own blocks. Your game draws it, so it
  costs the server nothing.
- **Styles**: the row of buttons under the picture builds the selected build in another style: as drawn,
  **Stonework**, **Sandstone**, **Dark Oak**, **Cherry**, **Grand** or, with Cobblemon, **Apricorn**. Only the styles
  that change something in that build show, and the chosen one is framed in gold. Click one and the picture and the
  material list change to it. Locked and "???" builds have no style row.
- **All materials.** The materials under the picture show the biggest first; scroll over them with the mouse wheel,
  or click **+N** or **All materials** for the whole list, every material with its count and name, biggest first,
  in the style you picked. Esc or **Back** closes it.
- **Get Blueprint** gives you a copy for a [Blank Blueprint](blank-blueprint.md), already set to the style you chose.
- **Upload a File** lists the build files in your `blueprints` folder (`.litematic` or `.schem`) and puts the one you
  pick in the library for everyone. Unless the server allows it with the `workplaceAllowUploads` game rule, only
  operators can upload.
- Builds you save with a [Scan Tool](scan-tool.md) show up here for everyone too.

## Storing your blueprints

The table is a shelf for the blueprints you bought, too, so they never have to sit in a chest. Click **Stored** at the
top of the table's screen: it lists every blueprint stored in this table with its picture and how many there are
("2 stored"), and the header counts them all ("4 of 200 stored").

- **Store Held** puts the blueprint in your hand on the shelf; **Store All** puts away every blueprint you carry
  (Blank Blueprints stay with you). Wonder Plans can be stored too.
- **Take One Out** hands you one of the blueprint you picked, just as it went in, and the count goes down. With a full
  inventory it stays in the table and the table says so.
- The blueprints belong to the table, not to you: anyone at it can take one out. They stay through restarts, a table
  holds up to 200, and breaking the table drops them all on the ground.

![A stored Starter Cottage taken back out](https://jcondedata.github.io/minecraft-alive-workplace/table_library/still-3.jpg)

## A collection that grows

Each village's table is a collection. A new village starts with the basics, about eighteen builds: the Starter
Cottage, the first Stone House and Terrace, and a first workplace for each everyday job (the Farmstead, the
Storehouse, the Smithy, the Mason's Yard, the Kitchen and the like). Those show in colour, and Get Blueprint works as
always.

Everything else starts **locked**. A locked build shows greyed out, with a line saying how to get it, such as "Sold by
a Journeyman Builder", "A market-day trader", "A questbook reward" or "Found in chests". You can still look round
its picture, dimmed, and see what it needs, but Get Blueprint stays off. A few rare builds (the grander homes only a
builder sells, the Wonders and some special builds) show as a dark **???** with no name, picture or materials, like a
hidden achievement, until your village finds them. Unlocked builds come first in the list.

Ways to unlock a build for the whole village:

- **Buy its blueprint** from a builder, from another villager who sells one, or from a market-day trader. You keep the
  blueprint, and the table has it from then on.
- **Level up your builders.** Each time one of the village's builders reaches a new level, a few more builds unlock.
- **Questbook rewards.** With FTB Quests, some quests in The Builder chapter unlock the next tier of a home.
- **Use a blueprint on the table.** Right-click the table holding any blueprint, one found in a chest, a friend's or a
  bought one, and its build joins the library. Village storehouses, inns and kitchens, and the chests of pirates,
  bandits and other raiders, hold a blueprint now and then. A Wonder Plan used on the table shows its Wonder there
  too, though a Wonder is still raised from its plan.

Each unlock says so in chat: "The village's library has a new blueprint: Smithy II". Builds you upload, scan or import
are always unlocked, and a table far from any Village Hall shares one collection for the whole server. A village that
was already standing before this came in keeps everything it has built or is building. A server owner can turn it all
off with **Blueprint Unlocks** in the [Settings](settings.md): then every build is in the table, as before.

## How it fits in

- **Fed by** the chests round the table, the village's shared chests within 48 blocks and the
  [Storehouse](storehouse.md). A [Carpenter](jobs-crafts.md) crafts what the builder is waiting for, and the
  Storehouse's board lists every block they're short of.
- **Crews.** Up to three builders with nothing to do join a build near their table and finish it sooner.
- **Safe while they work.** Builders clearing a site never break the table or your chests, and a miner's quarry never
  digs one up.
- **Breaking it.** Mine it with an axe; it drops itself, and the blueprints stored in it drop beside it. A builder who has leveled up keeps the job and takes a free
  table nearby by themselves; one who hasn't worked yet may go back to having no job, as in vanilla.

More in [The Builder and blueprints](builder.md).
