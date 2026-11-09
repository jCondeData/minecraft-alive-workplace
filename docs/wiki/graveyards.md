# Graveyards: graves laid to rest

Villagers' graves no longer clutter the village. Every grave used to stay where the villager fell: in the street, a
field, on a doorstep. Now the village's Undertaker takes each one to the village's **Graveyard** and lays it in a row
there. The villager can still be brought back from the Graveyard. A village without a Graveyard gets a small plot near
its hall instead (owner's decision, 2026-10-19).

Roadmap items: B222, B788, B1081, B1082

<!-- Lanes: add each id to the line above in the commit that builds it, and put the new config keys, saved fields,
     tests and scenes in backticks so wikicheck.py checks them. -->

## What a player sees

A villager dies in the street and leaves a grave there, as before. While the Undertaker has nothing to revive anyone
with (no golden apple, healing potion or totem in the chest), they walk to the grave, work at it for two seconds, and the
grave moves to the next free slot in the Graveyard, with a puff of soul particles at both ends. Then the Undertaker walks
over to it. Their status reads "laying a grave to rest in the graveyard" (`message.aliveworkplace.undertaker.state.moving`).

The Graveyard build (`aliveworkplace:graveyard` and its upgrade) fills row by row: first the row in front of the
mortuary, then the row along the gate wall, then the gaps along the walls and the path. A village with a Village Hall
and no Graveyard gets a **plot near the hall**: three rows of four graves, a grave every other block, on open ground
6 to 20 blocks from the hall.

When a golden apple (or a healing potion or totem) is in the chest, the Undertaker brings the villager back from the
Graveyard as from anywhere: same name, job, level and trades. Their partner, widowed by the death, marries them again
(a second wedding, as the "Bring back my love" request has it) if still alone; one who has married someone else
since, is courting someone else, or mourns someone else, stays with them, and the one brought back is single, free
to court again (B1082). A couple who were only courting when one died court again. Each death and revival goes the
same way, so a villager brought back twice is married to the same widow(er) again both times.

Graves that old saves left scattered round the village are moved the same way, one at a time.

A villager a zombie turns into a zombie villager leaves no grave: cure them the vanilla way instead. Cured, they are
the same villager to the mod too: their class, family name, friendship with players, partner (still married, the
partner never widowed by the bite), parents, gift, schooling, retirement, job counts, the day they grew up, Legend
place and the rest come back with them, even when the zombie villager was saved and loaded in between, and a couple
who were both turned are still married when both are cured. Only the work they held (a builder's site, a field, a
quarry, the guild they led) is let go when they're bitten, as a death lets it go. A zombie villager that is
killed instead of cured leaves their partner widowed then (B1081).

## How it works

- The Undertaker (`UndertakerWork`) looks for graves every two seconds, as before. Reviving comes first. When it can't
  revive (nothing in the chest), it takes the nearest grave that isn't resting in the village's Graveyard (or hall
  plot) and moves it. A grave that lies in another village (inside that village's hall circle) is that village's and
  is never moved to this one, even within the Undertaker's 32 blocks; a grave in no village (out in the wilds) still is
  (B779). Where two villages' circles overlap, a grave belongs to the village whose hall is nearer across.
- The resting places (`Graveyards.resting`) are worked out each time from the finished Graveyard builds the village
  remembers (within the hall's reach) and their placement, rotation included. Nothing new is saved. A Graveyard whose
  middle lies in another village (inside that village's hall circle) is that village's: this village never lays a grave
  there nor counts the graves in it as its own, however near it stands. One in no village (its hall gone) is still
  used (B788).
- Moving copies the grave's block entity as it is (the villager's saved data, the epitaph, the day they died). The new
  grave is laid before the old one is cleared, so a save at any moment keeps exactly one grave.
- A villager or zombie villager that vanilla converts (`Mob.convertTo`) is a new entity with a new UUID. The platform
  layer carries every attachment the mod saved on the old one over to the new one; for a villager a zombie killed,
  after the death hooks have let go of their work. `Couples` points the partner at the new UUID (B1081).
- After a revival (`Graves.revive`, after the request's own remarriage), `Couples.onRevived` makes both sides agree:
  a partner still widowed by this villager and alone marries them again through `RequestErrands.remarry` (the village's
  wedding at the hall of the partner's village, else just married again), a courting one courts them again; a partner
  with someone else, or mourning someone else, keeps that, and the revived villager's saved partner is cleared. A
  partner not loaded was never widowed and still names them, so nothing changes (B1082).
- Graves already in a Graveyard's walls, or on the hall plot, stay put. Graves in the Graveyard are tended wherever it
  lies in the village, even beyond the Undertaker's usual 32 blocks.

## Switches

| Key | Default | Expansion | What it does |
|---|---|---|---|
| `gravesToGraveyard` | on |  | Undertakers move graves to the Graveyard or the hall plot. Off: graves stay where villagers fall. |

## Saved data

Nothing new. A moved grave keeps its block entity (`villager`, `name`, `profession`, `level`, `died`).

## Items, blocks, jobs, commands

- `aliveworkplace:graveyard`: the build the graves go to.
- `aliveworkplace:grave`: the grave block, unchanged.
- `aliveworkplace:undertaker`: the job that moves them.

## Decisions

- The grave moves as soon as the Undertaker has worked at it, then the Undertaker walks to where it was laid. A carried
  grave would have needed new saved data on the villager and could be lost if the Undertaker died on the way.
- Reviving comes before moving: a grave the Undertaker can empty now isn't moved first.
- The hall plot is found from the ground each time (the first open spot, nearest the hall, where 12 slots fit), so
  graves already laid there keep it.

## Known limits

- A full Graveyard (32 slots) leaves the next graves where they fell until one is emptied.
- The hall plot needs open ground under the sky within 20 blocks of the hall.
- The switch is off in GameTests, so other tests' graves stay put; `B222GameTests` turns it on.

## Proof

- `B222GameTests.strayGraveGoesToTheGraveyardAndIsRevivedThere`: the street grave ends up in the Graveyard's first row
  slot with everything kept, and a golden apple brings Mira back from there with her name, job and level.
- `B222GameTests.reloadMidMoveKeepsTheGrave`: the Undertaker saved and loaded mid-move carries on; one grave, saved and
  loaded with everything in it.
- `B222GameTests.switchOffLeavesGravesWhereTheyFall` and `B222GameTests.noGraveyardMeansAPlotNearTheHall`.
- QA's `QaVillagerFixes1104c2GameTests.qaWithAnAppleInTheChestAStreetGraveIsRevivedNotMoved`: with a golden apple
  already in the chest, the street grave is revived where it lies (name kept, apple used) and never laid in the Graveyard.
- B779: QA's `QaVillageReachGraves1101BugTests.aGraveInTheNextVillageIsNotLaidInThisVillagesGraveyard` (the next
  village's grave stays out of this Graveyard, this village's own is laid there);
  `B779B780GameTests.aGraveInNoVillageIsStillLaidInTheGraveyard` and
  `B779B780GameTests.aGraveIsMovedOnceTheNextVillagesHallIsGone`; QA's
  `QaVillageFixesVerify1102GameTests.qaWhereVillagesOverlapAGraveGoesToTheNearerHallsGraveyard` (where two villages
  overlap, the grave nearer this hall is laid in its Graveyard, the one nearer the other hall stays).
- B788: `B788GameTests.aNeighborsGraveyardIsNotThisVillages` (the next village's Graveyard, within this hall's reach,
  isn't this village's), `B788GameTests.eachVillageKeepsItsOwnGraveyard`,
  `B788GameTests.aGraveyardWhoseHallIsGoneIsTheNearVillages` and
  `B788GameTests.anUndertakerNeverLaysAGraveInTheNeighborsGraveyard`.
- QA (B788's edges): `QaGraveyardRim1102GameTests`: a Graveyard whose middle lies exactly on this village's rim is
  this village's only; one block further out, inside the next village's circle, it is the next village's only; where
  two circles overlap, each Graveyard goes to the hall nearer its middle.
- B1081: QA's `QaCuredVillager1109eBugTests.aCuredVillagerKeepsHisClassFamilyNameAndFriends` (a Burgher of family 7
  with 500 friendship points, turned and cured, keeps all three) and `QaCuredVillager1109eBugTests.aCuredWifeIsStillMarried`
  (cured Ada and Bea still married to each other, Bea never widowed); the controls in `QaCuredVillager1109eGameTests`.
- B1082: QA's `QaRevivedPartner1109eBugTests.aRevivedWifeAndHerWidowerAgreeWhetherTheyAreMarried` (revived Ada and
  her widow Bea married to each other again) and `QaRevivedPartner1109eBugTests.aRevivedWifeWhoseWidowerRemarriedIsNotStillHisWife`
  (Bea married Carl meanwhile: revived Ada is single).
- B1081 and B1082's edges: QA's `QaConvertRevive1109cGameTests`: a cured villager keeps a dozen kinds of record
  (parents, gift, schooling, retirement, job counts, adult since, widowhood, class, friendship), and so does the zombie
  villager between; a zombie villager saved and loaded before the cure still comes back married; a couple both turned
  and both cured are still married; a zombie spouse killed leaves the other widowed, mourning them and free to court; a
  stale claim on someone remarried leaves the new couple alone; a revived wife whose widow is alone is married again
  with nobody mourning; one whose widow remarried, or courts someone else, is single and may court, the new couple
  untouched; a courting couple court again; an unmarried villager comes back with no partner; revived twice, married
  again both times, and a spent grave brings nobody back.
- Showcase scene: `undertaker_graveyard`.
