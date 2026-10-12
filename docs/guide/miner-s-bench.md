# Miner's Bench

```infobox
type: Block
Used by: Miner
Recipe: none (it can't be crafted)
Found in creative tab: no
Today's block for the job: blast furnace, picked with a pickaxe
Mined with: an axe
Stack size: 64
categories: Blocks, Jobs
related: jobs-land.md, jobs.md
```

The Miner's Bench is the Miner's old workstation. You can't craft it any more, and it's not in the creative tab. Today the Miner works at a **blast furnace**: stand a villager within about 4 blocks of one and sneak-right-click them holding a pickaxe.
The Miner's Bench is kept so that a world that already has one keeps working, and an operator can still hand one out with
`/give @p aliveworkplace:miners_bench`.

## What it does

A villager without a job who stands near a Miner's Bench takes it by themselves and becomes a **Miner**: no item needed.
Hand them a [Quarry Marker](quarry-marker.md) and they dig the marked area out, dropping what they dig in the chests round the bench.

Chests and barrels within **8 blocks** are the worker's supply chests: their tools and materials come out of them,
and their work goes into them. Right-click the Miner's Bench to see how many supply chests it has. More on the job in
[Jobs on the land](jobs-land.md).

## How it fits in

- **Fed by** pickaxes and torches in the chests within 8 blocks; a [Toolsmith](jobs-crafts.md) makes a new pickaxe when theirs wears out.
- **Feeds** the builders (stone and ore go into the shared stock) and, through a [Porter](storehouse.md), the storehouse.
- **Safe from the quarry.** A quarry never digs up the miner's own bench, and the marker refuses a quarry laid over it.
- **Shared stock.** Workers whose workstations are within 48 blocks of each other share their chests, as long as they
  answer to the same people.
- **Breaking it.** Mine it with an axe; it drops itself, so you can move it. A worker who has leveled up keeps the job
  and takes a free block of their job nearby by themselves (a Miner's Bench or a blast furnace); one who hasn't worked yet may go
  back to having no job, as in vanilla.
