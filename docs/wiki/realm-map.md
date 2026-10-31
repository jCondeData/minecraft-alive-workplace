# The Realm Map

ROADMAP 33.21 (part of 1.7, "From village to realm"; switched on in 1.0 with every other expansion). At any member's Village Hall the
Realm tab draws the whole realm on an empty map: a vanilla map you can hold, copy or hang in an item frame.

Roadmap items: 33.21

## What a player sees

On the hall's Trade page, the **Realm** tab (see [Founding a realm](realms.md)) has **Draw the Realm Map** (a filled
map) in its last slot, at the capital and at every member (a village outside a realm has no button). With an empty map in your inventory (none in creative), a
click turns it into **Map of the County of Thornholm**:

- a **banner for every member** in the realm's color (the base color of its arms; white without arms), named after
  the village, the capital's as **Thornholm (capital)**;
- **colonies** a member founded that are not in the realm in lime, **allies** (alliances, see
  [Trade pacts and alliances](pacts.md)) in light blue and **rivals** (the other side of a feud, see
  [Feuds and war](feuds.md)) in red, each named;
- every member's **caravan routes** as dotted lines of dark ink between the two halls;
- a **colony whose settlers are on the road** as a red cross on the spot they are heading for;
- the land the server has loaded drawn as on a vanilla map, and the rest as **plain parchment**.

The map's tooltip has the legend, as the [Village Map's](village-hall.md) does: the color and count of each kind of
banner, the routes, the crosses and what parchment means.

## How it works

`realm/RealmMaps.java` builds a locked map (like a cartography table's copy). Its center is the middle of the members'
halls, and its scale the smallest of vanilla's 2 to 4 (512, 1,024 or 2,048 blocks a side) at which every member is at
least 24 blocks inside the edge, so each banner is drawn whole; a realm wider than 2,048 blocks stays at scale 4 and its
farthest members fall off the edge. The land is drawn by the same code as the Colony Charter's map
(`VillageMaps.colors`, one column every 4, 8 or 16 blocks); a column in a chunk that isn't loaded stays blank, so
nothing is loaded for the map. Banners are vanilla map banners (`MapBanner`); the cross is vanilla's red X
(`MapDecorationTypes.RED_X`), kept on the item. A route's dots are one pixel every 3, starting 3 pixels from each hall
where its banner sits. Only the hall's own dimension is drawn.

## Switches

| Switch | Default | What it does |
|---|---|---|
| `realmMap` | on (with 1.7) | The Realm tab has the Draw the Realm Map button. Off: no button, nothing drawn; maps already drawn stay. |

`realms` off hides the whole Realm tab, the map button with it.

## Saved data

None new in the realm records. Each drawn map is an ordinary vanilla map (`data/map_<id>.dat`: its land, scale, center
and banners); the cross is on the item (`minecraft:map_decorations`).

## Items, blocks, jobs, commands

None new: vanilla `minecraft:filled_map`, named "Map of ..." with the legend as lore.

## Decisions

- "Every member in the realm's color" holds for colonies that are members too (colonies join their mother's realm by
  themselves), so the lime banner is for a member's colony that has left the realm or never joined.
- The cross sits on the colony's chosen spot, where the settlers are going, rather than on a guessed point of the walk.
- Rivals are found from the capital's feuds, which cover the whole realm's side.

## Known limits

- The land is a snapshot: drawn once, like any locked map; draw a new one to see the realm as it is now.
- Members in another dimension are left off a map drawn in this one.

## Proof

`RealmMapGameTests`: `realmMapShowsTheRealm` (the tab's button at a member, the empty map used, every banner's place,
color and name with the capital's, the ally, rival and colony, a far member, the route's dots all between its two
halls, the colony's cross, the legend, parchment wherever no chunk is loaded, save and reload of the map and the item),
`realmMapScalesToFitEveryMember` (scale 3 for members 900 blocks apart, both on the map; scales 2, 3, 4 and the cap),
`realmMapSwitchOff` (no button, nothing drawn, the empty map kept; nothing outside a realm). Showcase scene `realm_map`.
QA: `QaRealmMapBoardGameTests.qaRealmMapRefusesOutsidersAndNoMap` (no empty map: "Bring an empty map to draw the realm
on." and nothing given; a village of the same owner outside the realm: no button, "There's no realm map to draw here.",
the empty map kept; at a member's hall it's drawn).
