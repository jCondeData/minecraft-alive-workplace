# Palisade Gate: the brief

Family id(s): `palisade_gate` (never renamed)   Variants: village (none drawn), modded (none), looks (none)

One tier only: there is no `palisade_gate_2` (`StarterBlueprints.DEFENCES` lists `PALISADE`, `PALISADE_2`,
`PALISADE_GATE`, `PALISADE_TOWER`, ...; no upgrade names it). The code still follows any later tier or style back to
this id (`Sieges.isGated`, `isDefence`), so one could be added later without code changes.

## What it is for
The way through a village's wooden wall. It is the `gate` piece of the **Palisade wall kit**
(`data/aliveworkplace/wall_kits/palisade.json`: segment `palisade`, corner tower `palisade_tower`, gate
`palisade_gate`, Hamlet to Village). When the Steward's approved wall line is crossed by one of the plan's roads,
`city/Walls.layout` puts a Palisade Gate there in place of a segment, and an ordinary Builder builds it. A player can
also place it by hand from its blueprint (creative tab and Blueprint Table, with the other defences). Nobody lives or
works in it. What it does in the game:
- **Night:** in a village with a Village Hall and at least one guard, the hall's round (`guard/Gates.round`) shuts its
  fence gates at dusk (day time 12,500) and opens them before dawn (23,500); with no guard, it opens what a siege or
  the last guard shut. Players can still open them by hand.
- **Siege** (`threat/Sieges`): a finished Palisade Gate makes the village "walled", so a raid by a ram culture is a
  siege. Its gates shut at once; the gate build nearest the raiders is the **breach**; rams strike its gate blocks
  (fence gate 60 hit points, 120 with Ramparts), the middle way first; when one goes, the raiders walk in through the
  gap. Only gate blocks ever break; a builder puts them back afterwards (defences mended first).
- **Battle stations** (`guard/BattleStations`): knights hold a spot 3 blocks inside the breach gate, medics 6 blocks
  inside; archers may stand on any walkway spot of the build (optional, see Free).
- Counts as part of the wall: a whole wall is one building for the village's rank (`Walls.countsAsBuildings`);
  marked as a defence on the village map (`VillageMaps`, by id); never rebuilt by the Grand Rebuild; a hidden
  `aliveworkplace:built/palisade_gate` advancement.

## Levels
| Level | Id | What it adds (function, not looks) | Upgrade cost / unlock, if the code reads it |
|---|---|---|---|
| I (only) | `palisade_gate` | The wall's gateway: three fence gates the guards shut at night and rams break in a siege; makes the village walled | Creative tab and Blueprint Table with `StarterBlueprints.DEFENCES` (unlock rules by id, not by the drawing); placed by `Walls` at every road crossing of a Palisade wall |

## Must have (each level)
- **Gate blocks: exactly three, all `minecraft:spruce_fence_gate`, at (2, 0, 1), (3, 0, 1) and (4, 0, 1)**, and no
  other gate block anywhere in the build.
  - `guard/Gates.shut` opens and shuts **every block of the blueprint that is a `FenceGateBlock`**, at its blueprint
    position transformed by the placement's rotation and mirror. So a decorative fence gate anywhere else in the build
    would also swing at dusk and dawn.
  - `threat/Sieges` counts as "gate blocks" every fence gate, door (lower half) and iron bar of the blueprint, and
    drops a portcullis under every drawn iron bar. `SiegeGameTests.aRamBreaksThePalisadeGate` asserts the siege's list
    is **exactly 3 fence gates of 60** and the breach is **the middle one, (3, 0, 1)**; so **no door, no iron bars,
    no other fence gate** in the build.
  - The block must be **spruce** fence gate: `BattleStationGameTests.buildersMendTheGateBeforeAHouse` asserts the
    breach `is(Blocks.SPRUCE_FENCE_GATE)` and stocks the builder's chest with spruce fence gates for the repair.
  - Each gate must close its own gateway cell when shut (the shut gate across the way in, so its panel runs along x
    and the way through runs along z). Drawn shut (`open=false`) is the safe state: the guards and sieges set OPEN
    themselves. `QaThreatRepairGameTests` checks a repaired gate comes back with the drawn facing.
- **A way through over the gates:** above each gate at least (x, 1, 1) open (no block that stops a mob), so once a
  gate breaks a pillager (1.95 tall) walks through; and the cells in front of and behind the gateway, at z = 0 and
  outside the back, walkable. Tests need a pillager through within 600 ticks of the siege and the ram within 3.5
  blocks of the middle gate (see Rules). Anything solid hung over the gateway higher up is never broken (only gate
  blocks break), so a ravager (2.2 tall) is held up by anything lower than y = 3 over it.
- **Sealed everywhere else:** with the gates shut, no raider may get from the outside (z = 1 side) to the inside (z = 0
  side) through or round the build. In the test yards the build fills a 7-wide gap in a wall 4 blocks high (y 0..3)
  and the yard wall meets its two end columns: every column of the back row except the gateway must stop a mob at
  y 0 and y 1 at least (nothing it can step or jump onto and over), and there must be nothing climbable from outside
  (no ladder or step it could use). `SiegeGameTests.gatesHoldWithMobGriefingOff` asserts no raider gets in.
- **More than 10 other blocks** besides the gates (`SiegeGameTests.onlyTheGateBreaksAndABuilderPutsItBack`,
  `QaThreatRepairGameTests.qaARamLeftAtWorkBreaksNothingButGateBlocks`: "rest.size() > 10").
- Job blocks: none. Beds and residents: none. Storage: none read (builders never break containers, and nothing reads
  one here). Furniture: none (there is no `blueprint_furniture/palisade_gate.json`, and none is needed).
- Anything else the code reads: nothing (no bell, sign or lectern).

## Fits
- **Plot: 7 wide (x), exactly; 2 deep (z); at most 6 tall today** (`StarterBlueprints.PALISADE_GATE` is
  `7, 6, 2`). y = 0 sits on the clicked block.
  - The width 7 is pinned by tests and the showcase, not only by the declared size:
    `QaSiegeLadderBreach1103dGameTests` (and the `QaSiegeConverts1109d`, `QaLadderOver1109d`,
    `QaLadderOverShutGate1109dBug`, `QaLadderOverSaves1110d` tests that share its yard) leave a hole of exactly 7
    (x 19..25) in the yard wall for it; `SiegeGameTests` puts "a player's" fence gate right beside it at x 18 (a build
    9 wide would cover it); `devclient/JobScenes` (scenes `siege_gate`, `warband`) lays palisades 7 to either side of it.
  - The depth 2 puts the gate row (z = 1) on the build's outside face, which every yard test relies on: they put the
    build's last row in the yard's front wall and the raiders outside it. A deeper build moves the gates inside the
    wall line; don't.
  - Height is free up to the area tests use (the `huge_area` yards; the builder test in `big_area`, 18 tall). If the
    new size differs from `7, 6, 2` the ship step changes the `Vec3i` to match
    (`BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`).
- **Front: z = 0 faces the village (the inside); z = 1 (the back) faces out.** `Walls.add` turns every piece so its
  z = size - 1 side faces away from the hall; `Sieges.pickBreach` takes the blueprint's back as the outside.
- **How it joins the wall** (`city/Walls`):
  - Its z = 0 face sits on the wall line, the build reaching outward from it, like the segments and towers.
  - It is centred on the road crossing (`start = d - gateW / 2`), so the gateway in the middle of its width is where
    the road runs. A road crossing nearer a corner than the gate fits is skipped; two crossings within one gate's
    width share one gate.
  - Palisade segments either side stand flush against its two end columns (x = 0 and x = 6); nothing overlaps a gate.
  - Each piece sits at its own ground height (`Walls.groundY` at the footprint's middle), so its neighbours may stand
    a block or more higher or lower. No code joins walkways from piece to piece: walk heights are free.
  - Its footprint is the gate's width by its depth; the wall's tests only check that the gate's box is centred within
    4 blocks of the street and of the wall line (`WallGameTests`, `QaWallGameTests`).

## Rules tests check
Yards: `SiegeGameTests.yard` (used by SiegeGameTests, SiegeGatewayGameTests, QaSiegeOpenGate1105d, QaSiegeRegate1105d,
QaSiegeGatewaySaves1107e, QaThreatRepairGameTests) walls a yard 4 high with the build's last row in its front wall,
origin x = 14 - width / 2, and a villager shut in a stone cell inside; the raiders come from outside (the back).
- `SiegeGameTests.aRamBreaksThePalisadeGate`: fence gates at (2, 0, 1), (3, 0, 1), (4, 0, 1); the breach is (3, 0, 1);
  the siege's list is exactly 3 fence gates of 60 hit points; the player's own fence gate beside the build is not on
  it; the middle gate cracks first, then goes, drops nothing and is listed for the builders; a pillager gets through.
- `SiegeGameTests.gatesHoldWithMobGriefingOff`: the ram is within 3.5 blocks of the middle gate by tick 300; every gate
  keeps 60 hit points; no raider gets in; the villager lives (the shut build must be sealed).
- `SiegeGameTests.theSwitchesTurnSiegesAndTheirDamageOff`: the siege shuts all three gates; the ram is within 3.5 of
  the middle one.
- `SiegeGameTests.aSiegeCarriesOnAfterARestart`: the siege's gate list after a load starts at (3, 0, 1) with lanes
  0, 1, 2 (the three gates side by side on one floor, the middle one first).
- `SiegeGameTests.onlyTheGateBreaksAndABuilderPutsItBack`, `QaThreatRepairGameTests` (the ram, repair and
  materials tests): more than 10 non-gate blocks, at least 3 gates; only gate blocks ever break; a builder puts the
  broken gate back with its drawn facing, from a chest holding that gate's item.
- `SiegeGameTests.aGateThePlayerBreaksIsNotTheRams`: gates at (3, 0, 1) and (2, 0, 1); a player breaking the middle
  one opens the way.
- `SiegeGameTests.onlyAWalledVillageIsBesieged`: the gate at (3, 0, 1) of an unfinished build is never touched.
- `SiegeGatewayGameTests` (B972): the middle gate at (3, 0, 1); cobblestone put in its gap counts as walled up, cleared
  it counts open and the waiting pillager gets through within 35 seconds.
- `QaSiegeOpenGate1105dGameTests`, `QaSiegeRegate1105dGameTests`, `QaSiegeGatewaySaves1107eGameTests`: the same yard;
  a pillager through the middle gateway within the time.
- `QaZoglinRam1109dGameTests`: a fence gate at (3, 0, 1); a hoglin standing at (3.5, 0, 3.5) (outside the back) is in
  reach of it.
- `QaSiegeGameTests` (rams, axes, bows, husks at the Palisade Gate): blows counted against its fence gates.
- `QaSiegeLadderBreach1103dGameTests` and the ladder tests sharing its yard: the build fills a 7-wide gap (x 19..25)
  in a 4-high wall, its back row in the wall; shut, nobody gets in but by the Stone Wall's ladder; the gate broken, a
  follower walks in through the gateway.
- `BattleStationGameTests.knightsHoldTheGateAndMedicsStandBehind` and
  `QaBattleStationGameTests.knightsHoldTheInsideOfAGateThatFacesAnotherWay` (the build turned a quarter): the knight's
  place is 3 blocks inside the breach, the medic's 6, in line with it, and both walk there.
- `BattleStationGameTests.buildersMendTheGateBeforeAHouse`: the breach is a `spruce_fence_gate`; the builder repairs
  the gate first from a chest of oak planks and spruce fence gates.
- `BuilderGameTests.buildsPalisadeGate` (`big_area`, origin (9, 2, 9), 12,000 ticks): a builder stocked with exactly
  the plan's materials finishes it with every block as drawn.
- `BuilderGameTests.starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable`: the size matches `StarterBlueprints`.
- `WallGameTests` / `QaWallGameTests`: the Palisade kit's gate is `aliveworkplace:palisade_gate`; one gate per road
  crossing, its box centred within 4 of the street and the wall line.
- The owner's rules (`check.py`) and STYLE.md's checks.

## Free
Everything not listed above: how it looks, what it is made of (except the three spruce fence gates), its height,
lights, a walkway or archers' spots on top (optional), and how it meets its neighbours above the sealed lower rows.
