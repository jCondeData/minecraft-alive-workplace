# Post Office: the brief

Family id(s): `post_office` (one level, never renamed; "Post Office" in `en_us.json`, both
`blueprint.aliveworkplace.post_office` and `village_piece.aliveworkplace.post_office`; `QaWorkplaceCopiesGameTests`
reads "Post Office").
Variants: village (`village/<style>_post_office` for the five village styles `plains`, `desert`, `savanna`, `snowy`,
`taiga`, saved by `generate.py` through `village.py`'s `staffed_house` with the job `aliveworkplace:postman`; grown in
every village, with or without Cobblemon; its own contract, see "The village variant"), modded (none to draw:
`modded_library.py` makes `structure/modded/post_office.nbt` from the vanilla drawing and the ship step reruns it),
looks (none: `BlueprintStyles` restyles the drawing automatically).

## What it is for
The **Postman's** workplace. A Postman (`ModVillagers.POSTMAN`) works at a **Mailbox** (`aliveworkplace:mailbox`) or
a Postal Desk; this building gives him a Mailbox as his workstation and a bed. From it he runs his round
(`mail/PostmanWork`): he collects parcels from players' Mailboxes within **64 blocks** of his workstation
(`PostOffice.ROUND`), carries each to its recipient's Mailbox if that is on the round too, and walks back to his
workstation to **hand in** the rest, which arrive at the next dawn (or at once by air mail with a Flying-type partner,
Cobblemon only). When he has no mail he runs the players' courier routes (delivery notes) between chests on his round.
The Post Office's Mailbox is also the village's **counter**: a player who right-clicks a Mailbox a postman works at
collects every parcel waiting for them (`MailboxBlock.useWithoutItem`, `Mail.collectAtDesk`), and the first player to
open a Mailbox placed by a structure owns it. A player makes a Postman by standing a villager next to the Mailbox,
holding paper and sneak-right-clicking him (`work/Stations`, within **4.5 blocks** of a free Mailbox; the questbook says
"Stand a villager right next to the Post Office's Mailbox"). A Mailbox is never taken by a jobless villager by itself
(`Stations.ONLY_BY_ITEM`), only by a villager already a Postman or by the paper.

It is in the Blueprint Table without Cobblemon (`StarterBlueprints.WORKPLACES`) and in every new village's starting set
(`blueprint_unlocks/starting.json`); the questbook's "Mail: a Post Office" (chapter 3) completes on the hidden
advancement `built/post_office`. The Steward builds it in a **civic** zone when a Postman has no workstation
(`steward_rules/workplace_post_office.json`, priority 62, cooldown 2 days, no Cobblemon needed), and his renewal of an
old Postman's house rebuilds it as this one (`steward_renewal/postman.json`). In world generation every vanilla village
style grows the village variant (pool weight 2, `VillageHouses.houses()`) with a Postman already in the job.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `post_office` | The Postman's Mailbox (his workstation and the players' counter), his bed | Table starting set (`starting.json`); Steward rule `workplace_post_office` (civic, Hamlet+); renewal `postman.json`; questbook |

**One level only.** No `post_office_2` may be drawn: a second level is a new feature and would change the counted
upgrades in `BuilderGameTests.starterUpgradesKeepMostOfTheirBase` (exactly 64).

## Must have
- **Job block: exactly one `aliveworkplace:mailbox`** in the file (`WorkplacesGameTests.workplaceBuilt_post_office`:
  "hasn't one Mailbox", and exactly one Mailbox built inside the box). **No second Mailbox anywhere, inside or out**
  (not one by the street or the door: it would be a second Postman job site, and the count is of the whole file).
  **No Postal Desk** (`aliveworkplace:postal_desk`: also the Postman's job site, and one a jobless villager takes by
  itself). **No other job block of any job**: no barrel, lectern, composter, smoker, loom, stonecutter, grindstone,
  cauldron, brewing stand, crafting/fletching/smithing/cartography table, bell or any of the mod's workstations
  (`workplaces.py` contract: "exactly one of the job's block ... no other vanilla job block"). A chest or a (chiseled)
  bookshelf is not a job block.
- **The Mailbox is reached on foot from the front.** A Postman waiting in front walks in and claims it as his job site
  (`workplaceBuilt_post_office`), and he walks back to it on every hand-in; a player must stand by it to collect
  parcels and to make a Postman with paper (with a villager standing right next to it, within 4.5 blocks). So: an open
  standing cell beside it (feet and head clear, floor under), reached from the door on foot, and room for a player and a
  villager by it. **Note:** the Mailbox is *not* an acquirable job site, so `QaBuildReachGameTests` does not check it;
  only the build test (and the player) will find it unreachable.
- **Beds and residents: at least one bed**, the Postman's (`workplaces.py`: "a bed for the worker"; every bed counts for
  the Steward, `StewardConditions.beds`). Its head is reachable on foot (`QaBuildReachGameTests`) and its foot has its
  head one block towards its facing (`BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds`). No bed right
  inside a doorway (`check.py`). More beds are free. No villager in the file.
- **Storage: none required** (no Postman code reads a chest at his workstation; his courier routes use chests the player
  names). Any chest is drawn empty (builders never fill containers), is reachable on foot (`QaBuildReachGameTests`), and
  stands against a wall, a solid block, a bed or another chest (`QaFurnishedRooms1031GameTests`, if the furniture spots
  make it a furnished build). **No barrel** (a Fisherman's job block).
- **Furniture spots** (`blueprint_furniture/post_office.json`, written by `rebuild.py`): no kind is required by any test
  for this id (`post_office` is not in `FurnishedWorkplacesGameTests.workplaces()`). No spot's cells may cover the
  Mailbox, a container or a bed that isn't the spot's own (`FurnishedWorkplacesGameTests.noSpotCoversAJobBlockABedOrAContainer`):
  a counter or desk spot stops beside the Mailbox, never over it. Each spot's blocks are exactly the template's
  (`FurnitureSpotGameTests.everyShippedSpotMatchesItsTemplate`); nothing floats
  (`FloatingFurnishingsGameTests.noFurnishedBlueprintHasAFloatingPiece`); every chair faces its table, turned and
  mirrored too (`QaChairFacingGameTests`). Any spot kind other than bed, chair and table makes it a "furnished" build
  for `QaFurnishedRooms1031GameTests` (indoor chests against something, no standing lantern on a room's floor).
- **A way in on the z = 0 side** on the ground floor that a villager 1.95 tall walks through
  (`QaDoorwayClearanceGameTests`; no carpet just inside a 2-tall doorway, B170). Any upper floor or loft holding the bed,
  a chest or the Mailbox is reached by ladder or stairs from inside (B149, `check.py`, `QaBuildReachGameTests`).
- **No trapdoor in the front walk row**: no `*_trapdoor` at z = 1, y = 0 or 1
  (`VillageGameTests.noVillageHouseHasATrapdoorInItsFrontWalkRow` reads `structure/post_office.nbt`).
- **No calcite, jigsaw or structure void, and no entity** in the file (`workplaceBuilt_post_office`: "still has calcite,
  a jigsaw or structure voids", "comes with a villager").
- Every block is one a builder can place (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).

## Fits
- **Plot: at most 18 wide x 18 tall x 20 deep.** `WorkplacesGameTests.workplaceBuilt_post_office` builds it in the
  30 x 20 x 30 `huge_area` at helper (12, 2, 10) (x 12..29, y 2..19, z 10..29); the builder's bench (3, 2, 3) and stock
  chests (3, 2, 6/8/10) stand west of it. y = 0 sits on the clicked block; the front is the z = 0 side.
- **Material budget: at most 81 stacks** (three chests of 27, each item rounded up to whole stacks). The old drawing is
  455 placed blocks.
- **Build time:** the whole build, then the Postman walking in and claiming the Mailbox, within **12,000 ticks** (build
  delay 2, builders' help off).
- **Declared size:** `StarterBlueprints.POST_OFFICE` is `workplace("post_office", 9, 17, 10)` today; if the size changes
  the ship step changes that line (sizes must match); `modded_library.py` reads the sizes from that file.
- **Front and way in:** the z = 0 side faces the player who places it and the road of a Steward civic plot. The waiting
  Postman stands at helper (18, 2, 7) = template **(6, 0, -3)**, three blocks before the front edge, and must walk in by
  the front.
- **Neighbours:** a workplace in a Steward civic zone; the Steward keeps 2 blocks clear round its plot (`Plots.CLEAR`,
  `Plots.MARGIN`) and wants the ground within 4 blocks of level (`Plots.MAX_STEP`). A renewal takes it only where it
  fits the old house's footprint plus 3 a side (`Renewals.MARGIN`), so a smaller footprint renews more old houses (free).

## The village variant (`village/<style>_post_office`, five styles)
The shared village-house contract (`village.py` `village_house`, `village_looks.py` header, `world/VillagePieces`);
the designer draws only the look (`village_looks.py` `DESIGNS["post_office"]`, today 9 x 17 x 10):
- **Street joint:** a `minecraft:jigsaw` at **(4, 1, 0)**, `north_up`, `minecraft:building_entrance`, final state
  structure_void; the whole front row z = 0 at y = 0 places nothing
  (`VillageGameTests.everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`).
- **Front door at (4, 1, 2)** facing south, a step at (4, 0, 1); no trapdoor at z = 1, y 0-1. The west wall at x = 1.
- **The room:** x 2-6, z 3-7, up to y 4 (`VillagePieces` room box: a house is named by its room; a hall look change
  rebuilds everything outside it), bounded by walls at x 1, x 7, z 2, z 8.
- **Bed: foot (6, 1, 6), head (6, 1, 7), facing south.**
- **Fit-out** (`village.py` `post_office`, moved in by one): the Mailbox at **(2, 1, 7)** facing east, a chiseled
  bookshelf at (2, 1, 6), a bookshelf at (2, 1, 4). **Exactly one Mailbox and exactly one job block of any job in the
  whole template** (`VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`: "should have one Mailbox", "has N job
  blocks").
- **The villager:** one entity, already a **Postman**, at **(5, 1, 5)**, no worker-pool spawn jigsaw (same test:
  "should come with a aliveworkplace:postman"); keep (5, 1, 5) and (5, 2, 5) air with floor under it.
- A ladder to every furnished upper floor (`village_looks.py` header).
- In every vanilla village style's pool, by id, without Cobblemon (`everyVillageTypeCanGrowTheOtherHouses` pins
  `VillageHouses.houseNames()`, `post_office` third), and in Repurposed Structures' villages
  (`VillageCompatTests.repurposedStructuresVillagesGetOurHouses`). No test pins its outer size.

## Rules tests check
Pinned coordinates (the ship step moves them to the new drawing, keeping what each checks, dropping none):
- `WorkplacesGameTests.workplaceBuilt_post_office` (the `builds()` generator; `HOUSE` (12, 2, 10), `WORKER` (18, 2, 7),
  `BENCH`, `CHESTS`): no entities, calcite, jigsaws or voids; exactly one Mailbox in the file and in the built box;
  built from its supply report (at most 81 stacks); the worker, a Postman with no job site, let go after the build, must
  hold the one Mailbox as his `JOB_SITE`, still a Postman. It must reach DONE within the test's **12,000 ticks**: at most
  about **900 placed blocks** (a 1,410-block School timed out there; about 900 passed), and **no roof, overhang, barge
  or eave over another roof** (that School's builder got stuck on a barge stair above a lower roof).
- `VillageGameTests.everyVillageTypeCanGrowTheOtherHouses`, `everyVillageTemplatePlacedLikeAVillageKeepsTheGroundInFrontOfItsDoor`,
  `noVillageHouseHasATrapdoorInItsFrontWalkRow` (the village variant's cells above; the buildable's front walk row).
- In code, not a test: `village.py` fit-out cells and the Postman at (5, 1, 5); `VillagePieces` room box and bed-head
  reference; `Stations.REACH` 4.5 (paper makes a Postman by the Mailbox); `PostOffice.ROUND` 64 (the round is measured
  from the Mailbox).

By id only: `WorkplacesGameTests.workplaceRule_post_office_postman` (civic, Hamlet, no Cobblemon);
`QaWorkplaceCopiesGameTests` (the name "Post Office", in the Table without Cobblemon);
`QaQuestbookClaims1116eGameTests.qaEveryBuildAQuestSaysToPickIsOnANewVillagesTable` and
`QaBlueprintLibraryVerify1108bGameTests.aNewVillageStartsWithTheWikisSetAndTheRestLockedOrRare` (starting set);
`ModdedLibraryGameTests`; `QaCarvedLibraryCompatTests.everyModdedCopyKeepsItsJobSites` (the Mailbox at the same cell in
the modded copy: regenerate it); `QaBuiltAdvancements1116bGameTests` (`built/post_office` exists).

Library-wide: `B648GameTests` / `QaWindowsAndWards1028BugTests` (no pane or bar run ending against air),
`BlueprintsStayGameTests`, `QaDoorwayClearanceGameTests`, `QaChairFacingGameTests`, `QaBuildReachGameTests`,
`QaMirroredBuildsGameTests`, `QaTurnedFramesGameTests`, `QaLaneC1101GameTests.qaEveryCountableBlueprintMaterialHasAPlural`,
`QaMaterialListGameTests.everyLibraryBlueprintsListMatchesItsPlanAndFitsABook`,
`StyleGameTests.chimneysStayWholeInEveryStyle` (only if a campfire stands on a solid stack).

The owner's rules (`check.py`: posts at fence and wall corners, lamps on tables or posts or hung, crockery only by a
stove or on a table with a chair, no bed right inside a door; `open_panes`), STYLE.md's checks and `holes.py`:
`rebuild.py --candidate` prints no CHECK line and `holes.py` passes. The old drawing is in
`owner_rules_baseline.json` (`post_office`, `modded/post_office`: one owner-rule line each); the new one must need no
baseline entry, and the ship step removes them. Saves: there is **no `blueprint_history/post_office/` yet** (only the
village variants' `blueprint_history/village/<style>_post_office/`); the ship step must save the old drawings there
(`history.py`) and regenerate the village and modded variants, `blueprint_furniture/post_office.json` and
`village/<style>_post_office.json`.

## Free
Everything not listed above: shape, plan, roof, materials, colors, storeys, rooms, where the Mailbox, bed and any chest
stand (within the reaches above), extra beds and chests, a counter or none, lighting, and the village variant's look
outside its room.
