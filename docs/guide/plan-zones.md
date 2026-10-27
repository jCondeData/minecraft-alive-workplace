# Zones and plots

```infobox
type: Mechanic
expansion: M27
image: https://jcondedata.github.io/minecraft-alive-workplace/city_plan_ground/still-1.jpg
caption: Zone edges and a street, shown on the ground while you hold the plan
Zones per plan: up to 16
Kinds: Homes, Workshops, Farms, Market, Civic, Gardens, Defenses, Keep Clear
Each zone has: a name, a kind, a building style and a Renew switch
A plot needs: a road (or the hall) in front, a 2-block margin inside the zone, ground within 4 blocks of level
categories: Steward, Builder, Village Hall
related: city-plan.md, steward.md, stewards-desk.md, old-house-renewal.md
```

You paint zones on the [City Plan](city-plan.md); the [Steward](steward.md) builds each kind of building only in its own
kind of zone, in that zone's style. That's how you decide that the homes go by the river, the forge by the road and the
gardens in the middle, without placing a single block.

## The kinds

| Zone | Color | What goes there |
|---|---|---|
| **Homes** | white | cottages, stone houses, terraces, the inn, home upgrades |
| **Workshops** | orange | each job's workplace: the Smithy, the Mason's Yard, the Map Room... |
| **Farms** | lime | the Berry Farm, the Ranch, the Farmstead, the Fisher's Hut |
| **Market** | yellow | the storehouse, market stalls, the Market Square |
| **Civic** | blue | the school, the library, the clinic, the chapel, the graveyard |
| **Gardens** | light blue | wells, benches, fountains, gazebos, the bandstand |
| **Defenses** | black | lookout towers, barracks |
| **Keep Clear** | red | nothing, ever (roads may cross) |

Each zone also has a **style** (Stonework, Sandstone, Dark Oak, Cherry, with Cobblemon Apricorn, or As drawn), which
every building and [road](roads.md) that starts in it uses, and a **Renew** switch for
[old houses](old-house-renewal.md).

## How he picks a plot

For each building he wants, the Steward looks for a spot in a zone of the right kind, nearest the hall first so the
village grows compact, trying the blueprint's four turns and its mirror image. A spot is good only if:

- its **front faces a road** on the plan (or the hall when there's no road);
- the building plus **2 blocks all round** lies inside the zone, and off the roads;
- the ground under it is **within 4 blocks of level** (the builder levels the rest), with at most a tenth of it over
  water and none over lava;
- nothing stands there but **natural ground, plants and trees**, and it's 2 blocks clear of every other build site and
  finished building;
- a builder's [Blueprint Table](blueprint-table.md) stands in the village (then any spot in the zones will do), or the
  spot is within the **Build Distance** of one.

He never puts the same building the same way round within 24 blocks of itself, center to center (turned still counts), so a street isn't one house repeated:
he mirrors it, or picks the next building on his list. On his desk, **Another spot** asks for the next one that fits
and **Another style** for the same building in the next style that suits the spot.

## How it fits with the rest

- **Feeds**: [the Steward's rules](steward-rules.md) say what to build; the zones say where.
- **Keep Clear** keeps your own builds, farms and views safe for good. A place where a player placed or broke blocks is
  also left alone unless the owner approves a build there by hand.
- **No room?** With no plot that fits (zones too small, too steep, no Blueprint Table near), nothing is proposed for
  that building. Paint a bigger zone or put a Blueprint Table closer.
- Every finished building counts toward the village's [rank](ranks.md) as always.
