# Inn Counter

```infobox
type: Block
Used by: Innkeeper
Recipe: none (it can't be crafted)
Found in creative tab: no
Today's block for the job: Shop Counter, picked with a bed
Mined with: an axe
Stack size: 64
categories: Blocks, Jobs
related: jobs-services.md, jobs.md
```

The Inn Counter is the Innkeeper's old workstation. You can't craft it any more, and it's not in the creative tab. Today the Innkeeper works at a **Shop Counter**: stand a villager within about 4 blocks of one and sneak-right-click them holding a bed.
The Inn Counter is kept so that a world that already has one keeps working, and an operator can still hand one out with
`/give @p aliveworkplace:inn_counter`.

## What it does

A villager without a job who stands near a Inn Counter takes it by themselves and becomes a **Innkeeper**: no item needed.
Each morning, while there's a free bed nearby, a traveler comes to stay. Right-click one to hire them (8 emeralds for an Apprentice, 16 for a Journeyman, 32 for an Expert).

Chests and barrels within **8 blocks** are the worker's supply chests: their tools and materials come out of them,
and their work goes into them. Right-click the Inn Counter to see how many supply chests it has. More on the job in
[Porters, post and services](jobs-services.md).

## How it fits in

- **Needs beds.** No free bed, no travelers.
- **Shared stock.** Workers whose workstations are within 48 blocks of each other share their chests, as long as they
  answer to the same people.
- **Breaking it.** Mine it with an axe; it drops itself, so you can move it. A worker who has leveled up keeps the job
  and takes a free block of their job nearby by themselves (a Inn Counter or a Shop Counter); one who hasn't worked yet may go
  back to having no job, as in vanilla.
