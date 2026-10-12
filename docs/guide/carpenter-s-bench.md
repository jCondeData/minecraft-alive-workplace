# Carpenter's Bench

```infobox
type: Block
Used by: Carpenter
Recipe: none (it can't be crafted)
Found in creative tab: no
Today's block for the job: crafting table, picked with planks
Mined with: an axe
Stack size: 64
categories: Blocks, Jobs
related: jobs-crafts.md, jobs.md
```

The Carpenter's Bench is the Carpenter's old workstation. You can't craft it any more, and it's not in the creative tab. Today the Carpenter works at a **crafting table**: stand a villager within about 4 blocks of one and sneak-right-click them holding planks.
The Carpenter's Bench is kept so that a world that already has one keeps working, and an operator can still hand one out with
`/give @p aliveworkplace:carpenters_bench`.

## What it does

A villager without a job who stands near a Carpenter's Bench takes it by themselves and becomes a **Carpenter**: no item needed.
When a builder nearby is waiting for something that can be crafted (stairs, slabs, doors, fences, torches, glass panes), the carpenter makes it here from what the village has and carries it to the builder's chest.

Chests and barrels within **8 blocks** are the worker's supply chests: their tools and materials come out of them,
and their work goes into them. Right-click the Carpenter's Bench to see how many supply chests it has. More on the job in
[Crafters and smiths](jobs-crafts.md).

## How it fits in

- **Fed by** the village's shared chests and the storehouse: give a builder only logs and the carpenter makes the rest.
- **Stock orders.** The carpenter also keeps the items ordered on a [Storehouse](storehouse.md)'s board in store.
- **Shared stock.** Workers whose workstations are within 48 blocks of each other share their chests, as long as they
  answer to the same people.
- **Breaking it.** Mine it with an axe; it drops itself, so you can move it. A worker who has leveled up keeps the job
  and takes a free block of their job nearby by themselves (a Carpenter's Bench or a crafting table); one who hasn't worked yet may go
  back to having no job, as in vanilla.
