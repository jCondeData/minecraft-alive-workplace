# House looks

Every house a village grows wears its village style's outside (plains, desert, savanna, snowy or taiga). The Village
Hall's **House looks** page lets the village's leader give one house another style's outside: the village's builder
rebuilds the outside, and the room, its job block and its chests stay. It belongs to the Village Hall.

Roadmap items: 23.10a, 23.10

## 1. What a player sees

- On the Village Hall, the **Builds** button reads "Click: choose how the village's houses look" and opens the
  **House looks** page, titled "House looks in <village>". Its header says "Pick a house, then the outside it should
  have." and "The village's builder rebuilds the outside; the room, its job block and its chests stay." A last line
  names who may choose: "Only <leader> and their friends can choose", or, in a village nobody leads, "No leader yet:
  whoever switches on the hall's protection leads the village".
- Under the header is a button for each village house near the hall (the ones the village grew by itself), nearest
  first. Each shows the house's name, "Outside: <style>", "Built as a <style> house" when the outside is no longer the
  style it was built in, where it stands ("At x, y, z"), and, while a rebuild goes on, "Being rebuilt with the <style>
  outside: N%", plus "Waiting for <materials>" when the builder lacks some. With no houses the page says "No village
  houses near the hall".
- Click a house and its view shows five buttons, one per style, each "<Style> outside". The current one is marked "Its
  outside now" and the style it was built in "The style it was built in". Click one and the village's builder starts.
  The result shows above the hotbar:
  - "<Builder> is rebuilding the <house> with the <style> outside."
  - "<Builder> will rebuild the <house> with the <style> outside after their current builds." (the builder was busy)
  - "The <house> already has the <style> outside." (nothing starts)
  - "The <house> is already being rebuilt with the <style> outside." (nothing starts)
  - "Only the village's leader, <leader>, and their friends can choose how its houses look."
  - "This village has no leader yet: switch on the hall's protection to lead it."
  - "No builder of this village can reach the <house>: give one a bench within 128 blocks of it."
  - "No builder of yours can rebuild the <house>: the village's builders work for <owner>. Hire one of your own, or ask
    them to run /workplace friend add <you>."
  - "That house isn't there any more."
- The rebuild is an ordinary build site, so it shows in the hall's count of buildings going up. Its blueprint is named
  "<House>: <Style> outside".
- The village's Chronicle (the Plans kind) keeps "<player> had the <house> rebuilt with the <style> outside".

## 2. How it works

- **Who may choose.** The hall's owner (the leader), the owner's friends who may direct (`Friends.mayDirect`) and
  operators. A hall nobody owns lets an operator through and refuses everyone else with "no leader yet". An unknown
  style (a forged click) is refused as "That house isn't there any more."
- **The builder.** The village's builders are the Builders within the hall's radius that have a bench. The nearest
  idle one whose bench reaches the house (128 blocks) takes it; if none is idle, the one with the shortest queue
  (at most 5 waiting builds) gets it queued. If the only builders who reach it belong to another player and the game
  rule below is on, the chooser is told "No builder of yours..." rather than "No builder of this village...".
- **What is built.** An "outside" blueprint of the house (`aliveworkplace:outside/<style>/<house>`) is built over
  the house like an upgrade: the old outside's pieces come out, the new ones go in, and the room, bed, job block and
  chests stay. The ground round it is not leveled, so the street and gardens stay as they are.
- **Changing your mind.** Choosing another style while a rebuild runs calls the first off and starts the new one; the
  house ends with the last pick, its room kept. Choosing the style that is already being built is refused
  ("already being rebuilt"). Choosing the outside the house already has is refused ("already has"), though the choice is
  still remembered.
- **Remembered.** The hall keeps a list of the choices (the house's spot, its turn, its house, the style it was built
  in and the look chosen), so a house shows on the page even while it stands half of one outside and half of the other
  and can't be recognized by its blocks.

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| `workplaceBuilderOwnership` | true | A builder takes orders only from who hired it, their friends and operators; with it on, another player's hired Builder can't be sent to rebuild a house by someone else. Off: any village builder takes it. |

## 4. Saved data

The Village Hall's block entity saves `piece_looks`, a list of choices (`origin`, `rotation`, `house`, `style`,
`look`). It is written only when the list is not empty; an older save without it reads as no choices. A damaged entry
is skipped and the rest of the list loads (`QaHallLenientLoad1102GameTests`). The rebuild itself is an
ordinary build site, saved with the builder's other sites.

## 5. Items, blocks, jobs, commands

No new items, blocks or commands. The page is the Village Hall's Builds button; the work is the Builder's.

## 6. Decisions

- **Owner, 2026-10-04 (23.10a):** keep one shared outside per village style, but let the village leader override it
  (choose a different look for a piece). So there is one outside per style and house, and a leader's choice only
  swaps which one a house wears.
- The room and its chests are never touched, so a choice is safe to make on a working house.
- **23.10 (owner, 2026-10-04, process work):** every shipped build was reviewed in a gallery package per build family
  (houses, workshops, defenses, decorations, village pieces in five styles), each shown front and back, and vetoed
  builds were redrawn. The five outsides a leader can choose from are those reviewed style variants.

## 7. Known limits

- Only houses the village grew by itself (and houses with a remembered choice) are listed ("Houses the village grew
  by itself show here").
- The page has room for the houses that fit its rows; a village with more houses shows the nearest ones.
- The rebuild needs the materials like any build and waits ("Waiting for ...") until the store has them.
- A builder needs a bench within 128 blocks of the house (exactly 128 reaches, 129 doesn't), and takes at most 5
  waiting builds; a 5th waiting build is still queued, a 6th is refused.
- Open bugs: a torn-down house whose look was ever chosen gets a bare outside on its empty lot (B1050); a look waiting
  as the builder's 5th queued build can't be changed, and a full queue is told as the out-of-reach line (B1051).

## 8. Proof

GameTests: `PieceLooksGameTests` (`ownerGivesAHouseANewOutside`, `onlyTheLeaderChoosesALook`,
`aRebuildWaitsForMaterials`, `aRebuildSurvivesSaveAndReload`, `everyLookSentenceReads`,
`pickingTheOutsideItHasStartsNothing`, `withNoBuilderAPickIsExplained`, `aLookIsNotHandedToAnotherPlayersHiredBuilder`,
`anUnknownStyleIsRefused`, `changingTheLookMidRebuildEndsWithTheLastPick`), and `QaBuilderOwnership1104bGameTests`
(`aLookGoesToThePlayersOwnFartherBuilderNotANearerForeignOne`, `aRefusedLookLeavesAnotherPlayersRebuildRunning`,
`withOwnershipOffAnotherPlayersBuilderTakesALook`), and `QaHouseLooks1108cGameTests` (a house torn down after the
listing, the 128/129-block reach, a queue of 4 and of 5 waiting builds, every chest item, the job block and the bed kept
through a finished rebuild, who may choose in an unowned and an owned hall).

Showcase scenes: `piece_look` (the Builds button, the Guard House's five outsides, and the desert one rebuilt).
