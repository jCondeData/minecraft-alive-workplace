# Mover

```infobox
type: Job
image: https://jcondedata.github.io/minecraft-alive-workplace/mover/still-2.jpg
caption: A Mover half way through taking a cottage down
Workstation: Mover's Crate
Recipe: planks round a barrel, a lead on top, two iron ingots under it
Pick it: a villager without a job takes a Mover's Crate by themselves
Moves: the building picked on the Village Hall's Move page
Nothing lost: every block, chest contents and decorations travel in his crates
Crew: two Movers share a move
categories: Jobs, Village Hall
related: moving-buildings.md, village-hall.md, builder.md
```

The Mover moves a finished building to a new spot in its village, with nothing lost on the way.

## Getting a Mover

Craft a **Mover's Crate** (planks round a barrel, a lead on top and two iron ingots under it) and put it in your
village. A villager without a job takes it by themselves, as a builder takes a Blueprint Table.

## Moving a building

Pick the building and its new spot on the Village Hall's **Move** page, turn it if you like, and Confirm (what can
be moved, and where: [Moving buildings](moving-buildings.md)). The Mover:

- takes it down from the top, one block at a time, into his crates. Chests, barrels, shulker boxes and modded storage
  (Sophisticated Storage, furniture cabinets and cupboards) are packed with what's in them, each in its own crate;
  signs keep their words, banners their patterns; item frames (one hanging on an outside wall too), armor stands and
  furniture go too. Nothing is dropped and nothing is doubled;
- puts it up again at the new spot from the ground up (the foundation, the walls and floors, then doors, beds, torches
  and decorations), turned the way you picked;
- leaves the old spot cleared. Paths, fences and gardens round it stay where they are.

Two Movers in the village work the same move together, twice as fast. A save and reload mid-move is fine: he carries
on where he was.

He packs everything into the crates at his Mover's Crate, so keep it standing: if it's broken he can't take
anything more down, and he waits ("Waiting for his Mover's Crate") until he has one again. A Mover's Crate inside the
building he moves stays where it stands: the building goes up at its new spot without it, and you can move the crate
yourself afterward.

**The people who live there move too.** Villagers whose bed or workstation is in the building keep them. While it's
away they sleep in a free bed at the village's inn (or the free bed nearest the Village Hall), and workers keep their
jobs. The moment the Mover puts a bed or workstation up at the new spot, it's theirs again, held so nobody else takes
it. The Hall's People page, couples and job cards follow them, and a Builder whose Builder's Bench is in the house keeps
it, with his queued builds. A resident who is far away when the move starts gets the moved bed or workstation too
when they come back.

**Everything arrives as it was.** What's in a chest, on a frame or on an armor stand when he packs it is what comes
out at the new spot: an item you took out after confirming the move stays yours, and one you put in moves too.
Furniture from CobbleFurnies, Handcrafted and Lucky's Cozy Home moves like any block; if a furniture mod is removed in
the middle of a move, its pieces are left out and the rest of the building still goes up.

If a block of the building is broken before he gets to it, whoever broke it keeps it, and it isn't put up again.

**Calling it off.** Call off on the Move page stops the move, even half way: what he put up at the new spot comes down
again, and everything goes back up where it was, chests and all. The building's row opens with Call off at every stage
of the move. Once he has started, you can't send it to a different spot: call the move off first, then pick again.

**Turning it off.** The server's config switch `movers` turns the job off: no new Movers, those hired stand idle, and
confirmed moves wait.
