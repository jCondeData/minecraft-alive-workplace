# The settlers set out

ROADMAP 33.9 (part of 1.7, "From village to realm"; off until 1.7 is released). The second step of founding a
colony: the owner takes the [Colony Charter](colony-charter.md), its spot chosen, back to the hall, and the village
sends two settlers with supplies. What happens when they arrive (33.10) gets its own page.

Roadmap items: 33.9

## What a player sees

**Sending them.** Right-click the village's hall with its charter. The charter is used up and chat says who goes:
"Dara and Tomas will set out for Newbrook. Their supplies are packed: they leave in the morning." If the Storehouses
are short it lists what is missing: "The Storehouses still miss 24 planks, 1 bed: bring it, or they leave in 3 days
with what there is."

**Who volunteers.** Two settlers:

- the colony's builder: the lowest-levelled builder if the village has two or more, otherwise a jobless grown-up
  (who becomes the builder when the colony is founded);
- a second settler: a jobless villager, otherwise the lowest-levelled worker of a job the village has three or more
  of.

Never a guard, the only worker of a job, a Legend or a child. A married volunteer brings their partner, so a
couple makes three or four settlers; someone whose partner can't be spared (a guard, the only farmer) doesn't
volunteer. If the village can't spare two, the hall says so and the charter stays in your hand.

**Supplies.** 64 logs, 64 planks, 64 cobblestone, 32 bread, 16 torches, 12 glass panes and 3 beds (any wood, any
bed colour) come out of the chests by the village's Storehouses. What is missing:

- goes on the village's wants, so partner villages' caravans bring it;
- shows on every Storehouse's requests board in a settler's name ("24 planks"); a click hands yours over, into the
  Storehouse's chests, where the settlers take it within a second.

After 3 days they stop waiting and leave with what there is.

**The Colonies tab** shows the order under "On the way: Newbrook": where the spot is, then "Getting ready for
Newbrook", "Ready for Newbrook: they leave in the morning", "Setting out for Newbrook" or "On the road to Newbrook,
there in 2 minutes"; who goes; what is packed; what they wait for. Until they leave, a **Call the colony off**
button stands beside it (the owner and friends only): the settlers stay, every supply goes back into the
Storehouses and the charter, spot and name and all, comes back to you.

**Leaving.** The morning after the supplies are complete (or the 3 days are up), the settlers walk to the hall;
when all are there (15 seconds at most) the bell rings and chat says "The bell rings: Dara and Tomas set out from
Thornholm for Newbrook." They walk out toward the spot. Once nobody can see them (no player within 48 blocks, or
40 blocks from the hall), or after 30 seconds, they are on the road: gone from the world and kept in the order.
The journey takes 3 minutes plus a tick for every block to the spot (612 blocks: about 3 and a half minutes).

## How it works

- `colony/Settlers` holds all of it; `VillageHallBlock` hands a right-click with the charter to `Settlers.send`.
- The order (`RealmData.Order` in `aliveworkplace_realms`) is the only state. `Settlers.tick` runs every server
  tick and moves each order on from what is saved, so a save and reload at any tick goes on where it was:
  `gathering` (checked once a second) → `muster` → `leaving` → `on_road`. An order whose hall isn't in loaded,
  ticking chunks waits.
- While gathering, the volunteers are ordinary villagers, remembered by id. On the road each is saved the way a
  grave saves a villager (name, job, level, trades, partner), gives up bed and workstation in the
  mother village and is removed.
- Supplies are kept in the order as item and count. Wants: `Caravans.wants` adds `Settlers.wants`. Board:
  `StorehouseBoard` adds `Settlers.requests`.
- The walk uses the villagers' own brains (a walk target every half second), first to the hall, then 48 blocks out
  on the line to the spot.

## Switches

| Key | Default | What it does |
|---|---|---|
| `colonies` | on from 1.7 (off until then) | Off: the hall refuses the charter ("Colonies are switched off on this server."). An order already given goes on. |

## Saved data

- `colonies.orders` in `aliveworkplace_realms` gains, each with an empty default so older files load unchanged:
  `ordered` (game time), `volunteers` (villager ids while they are still in the village), `builder` (which of them),
  `supplies` (item to count) and `settlers` (the saved villagers once on the road). `state` now also takes `muster`
  and `leaving`; `leaves` is the gathering's time, then the bell's; `arrives` is set when they take to the road;
  `cost` is 3,200 (the charter's 32 emeralds, for 33.10's refund).

## Items, blocks, jobs, commands

- No new items or blocks. The Colonies tab gets the call-off button (slot beside the order).

## Decisions

- Right-clicking the hall uses the charter up; calling off gives it back instead of a refund, so the spot and the
  name aren't lost.
- A builder in the middle of a build doesn't volunteer (his site would be left half-built); the next builder or a
  jobless grown-up goes instead.
- A volunteer whose married partner can't go (guard, Legend, only worker of a job, not in the village) doesn't
  volunteer: couples aren't split.
- "Three or more of a job" counts the village before anyone is chosen.
- Logs, planks and beds are asked for on the wants as oak logs, oak planks and white beds (a caravan carries one
  item), but any kind is taken from the Storehouses and on the board.
- Calling off is possible while the settlers get ready and while they gather, not once the bell has rung.
- If the hall is broken before the bell, the order is called off by itself and the charter drops there. If every
  volunteer has died by the gathering, the same.

## Known limits

- Until 33.10 lands nothing happens when the journey ends: the order stays "On the road to Newbrook, arriving" and
  the village can't send another colony.
- A volunteer who dies or is taken away while the village gets ready isn't replaced: the others go without them.
- Supplies with extra data (a renamed bed, say) aren't taken: only plain items count, as elsewhere in the
  Storehouse's stock.
- Seen in the real client only through the showcase scene, which GitHub films; it was not filmed locally.
- The "never a Legend" rule has no GameTest of its own.

## Proof

- `ColonySettlersGameTests` (8): `theRightVillagersVolunteer` (never the guard, the guard's husband, the only
  builder, the only farmer or the child; the wife comes too), `suppliesAreTakenAndTheMissingBecomeWants` (wants,
  the tab, the Storehouse board, the morning), `theSettlersLeaveAndAreSaved` (the walk to the hall, on the road,
  the journey time, no call-off and no second order), `theSettlersWalkToTheHallAndOutTowardTheSpot` (on their own
  feet, the owner watching), `callingItOffPutsEveryoneAndEverythingBack`,
  `theOrderSurvivesSaveAndReload`, `sendingIsRefusedWithoutASpotAnOwnerOrTheSwitch`,
  `afterThreeDaysTheyLeaveWithWhatThereIs`.

Showcase scenes: `colony_departure`
