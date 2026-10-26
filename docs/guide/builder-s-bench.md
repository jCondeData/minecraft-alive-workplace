# Builder's Bench

```infobox
type: Block
Used by: Builder
Recipe: none (it can't be crafted)
Found in creative tab: no
Today's block for the job: Blueprint Table
Mined with: an axe
Stack size: 64
categories: Blocks, Builder, Jobs
related: builder.md, jobs.md
```

The Builder's Bench is the Builder's old workstation. You can't craft it any more, and it's not in the creative tab. Today the Builder works at a **[Blueprint Table](blueprint-table.md)** instead, which a jobless villager takes by themselves.
The Builder's Bench is kept so that a world that already has one keeps working, and an operator can still hand one out with
`/give @p aliveworkplace:builders_bench`.

## What it does

A villager without a job who stands near a Builder's Bench takes it by themselves and becomes a **Builder**: no item needed.
They build the blueprints you hand them, block by block, from the chests round the bench, exactly as at a Blueprint Table. Builds go up anywhere in its village, or within 128 blocks of it outside the village.

Chests and barrels within **8 blocks** are the worker's supply chests: their tools and materials come out of them,
and their work goes into them. Right-click the Builder's Bench to see how many supply chests it has. More on the job in
[The Builder and blueprints](builder.md).

## How it fits in

- **Fed by** the chests within 8 blocks, the village's other chests within 48 blocks and the storehouse. A [Carpenter](jobs-crafts.md) nearby crafts the stairs, doors and panes the builder is waiting for.
- **Safe while they work.** Builders clearing a site never break their own bench, and a miner's quarry never digs one up.
- **Getting blueprints.** The bench has no library: blueprints come from a [Blueprint Table](blueprint-table.md) or from the builder's own trades.
- **Shared stock.** Workers whose workstations are within 48 blocks of each other share their chests, as long as they
  answer to the same people.
- **Breaking it.** Mine it with an axe; it drops itself, so you can move it. A worker who has leveled up keeps the job
  and takes a free block of their job nearby by themselves (a Builder's Bench or a Blueprint Table); one who hasn't worked yet may go
  back to having no job, as in vanilla.
