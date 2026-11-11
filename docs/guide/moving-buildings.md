# Moving buildings

```infobox
type: Feature
image: https://jcondedata.github.io/minecraft-alive-workplace/mover/still-2.jpg
caption: A Mover half way through taking a cottage down
Where: the Village Hall's Move page (the piston tab)
Who does it: your village's Mover
Can be moved: your village's own finished buildings
Never moved: the Village Hall, a Wonder, a building still being built or upgraded, a building in someone else's protected village
Setting: Move buildings (on)
categories: Village Hall
related: mover.md, village-hall.md
```

Built a cottage in the wrong place? Your village can pick it up and put it down somewhere else, with everything inside
it. The heavy lifting is done by the [Mover](mover.md); this page is about choosing what moves and where.

## Picking a building and a spot

Open the Village Hall and click the **Move** tab (a piston). The page walks you through three steps, shown across
its top:

1. **Pick a building.** The list shows the village's finished buildings and where each stands from the Hall.
2. **Pick a new spot.** A map of your village opens: the building where it stands now in yellow, the Hall's bell, the
   roads and the other buildings, named. Click the map where it should go. The new spot shows green when it fits and red
   when it doesn't, with the reason beside the map, and a glowing outline marks it in the world too. Scroll over the
   map to zoom out to the whole village. **Rotate** turns
   it; the gold edge is its front.
3. **Confirm.** Once the spot fits, **Confirm the move** lights up. The page tells you what the Mover will do and
   about how long it takes. **Cancel** leaves the building where it is.

A spot is refused, with the reason on the page, when the building would:

- overlap another building, the spot another building is already being moved to, or the Village Hall;
- sit on a road or under water;
- stick out of the village, or stand on ground that rises more than 3 blocks across it;
- cover a Mover's Crate. A Mover's Crate never travels with a building, so pick another spot or move the crate first.

## What can't be moved

Some buildings stay put, and the Move page tells you why when you click them:

- **The Village Hall** stays where it stands: the village is measured from it.
- **A Wonder** is built to stay where it stands.
- **A building the builders are still working on**, such as one being upgraded or repaired, or one a new build next
  door reaches into. Move it once they're done.
- **A building outside your village**, or one in **someone else's protected village**. Only the village's own buildings
  can be moved, and only by the people allowed to change things there (the owner, their friends and server operators).

The page checks again when you click **Confirm**. If something changed after you picked the building (builders
started on it, someone else's protection now covers it, or moving was switched off), Confirm is refused with the
reason and nothing moves.

## Turning it off

Server owners can switch moving off with the **Move buildings** setting. With it off the Move page refuses every
building; a move that was already confirmed still finishes, so nothing is left half built. To stop the Movers
themselves, switch off **Movers** instead.
