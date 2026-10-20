# Crafter

```infobox
type: Job
expansion: M37
image: https://jcondedata.github.io/minecraft-alive-workplace/crafter_chain/still-2.jpg
caption: A crafter working through a chain at the Crafter's Workbench
Workstation: Crafter's Workbench
Pick it with: nothing: a jobless villager takes a free workbench by themselves
Works for: the builders nearby first, then the Storehouse's stock orders, then any other worker waiting for something
Needs: raw materials (logs are enough for anything wooden)
Makes: anything a crafting table or a stonecutter makes, a whole chain at a time: logs into planks into slabs into a barrel
Build: the Crafter's Workshop
categories: Jobs, Builder
related: carpenter.md, mason.md, builder.md, porter.md, jobs-crafts.md
```

The Crafter makes whatever the village's workers are short of, and doesn't stop at one step: give the village logs and
they saw them into planks, cut the planks into slabs, then make the barrel or chair the builder was waiting for.

## Getting a crafter

Craft a **Crafter's Workbench** (two stonecutters and a crafting table over two planks and a chest) and place it in
the village. A villager with no job takes it by themselves. Or build the **Crafter's Workshop** from the Blueprint Table:
the workbench is inside it.

## What they do

They look at what the workers nearby are waiting for: first the builders' missing materials, then what you ordered on
the Storehouse's board, then anything else a worker is waiting for, like a miner's ladders. For each one they work out
the whole chain from what the village has, using the game's own recipes (so other mods' recipes count too), fetch the
makings from the builder's chests and the Storehouse, and make it one step after another at the workbench. What's made
goes straight into the chests of whoever asked. A big order is made a bagful at a time until there's enough.

They check everything before they take anything. If something can't be made, nothing is cut up for nothing: the line
over their head says what's short, like "short of 2 Oak Log to make 2 Barrel". Put the logs in the Storehouse and
they're on it.

They never take from a chest outside the village, and from another builder's chests only what that builder's own
builds can spare.

Over their head: how many things they've made, and what they're fetching, making ("making 24 Oak Planks (step 1 of 3)")
or carrying.

![The Crafter's chain](https://jcondedata.github.io/minecraft-alive-workplace/crafter_chain/still-3.jpg)

## How the crafter fits in

- **Fed by:** the [Lumberjack](lumberjack.md)'s logs and the [Miner](miner.md)'s stone, from the Storehouse.
- **Works beside:** the [Carpenter](carpenter.md) and the [Mason](mason.md): they share the work, and never make the
  same thing twice.
- **Furniture mods:** with a furniture mod's own crafting bench standing in the workshop, the Crafter uses its
  recipes too.

## Switching it off

The **Crafters** setting (on by default once Furnished Homes is out) turns the job off: no villager takes a workbench,
and crafters already hired stand idle.

See also [Crafters and smiths](jobs-crafts.md).
