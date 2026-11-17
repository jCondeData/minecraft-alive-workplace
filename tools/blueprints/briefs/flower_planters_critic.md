# Flower Planters (52.2): the critic's scorecard

Sheet: `flower_planters_sheet.png` (OLD and NEW from four sides and the top; NEW cutaways in the designer's renders). Eye-level shots: not filmed (a street piece; the triage sheet is `triage_52_2/flower_planters_sheet.png`).

| Score | OLD | NEW |
|---|---|---|
| Silhouette | 2 | 4 (a horseshoe stepping up in three tiers under a pergola: a place, not a wall) |
| Depth | 1 | 3 (stone-sided beds with caps, plinths under the posts, beams running proud of the posts) |
| Interior | 1 | 3 (a path between gate boxes into a little court and a bench, lanterns over it) |
| Detail | 2 | 4 (flowers in their own soil by tier: low at the gate, lilacs, peonies and roses at the back) |

Verdict: **SHIP** (+2, +2, +2, +2). Shipping needs `StarterBlueprints.FLOWER_PLANTERS` 7,3,3 -> 9,6,8, the family out of `owner_rules_baseline.json`, and its tests (it is in every village from the start): buildsFlowerPlanters in 6,000 ticks, flowerPlantersFromExactlyItsList, and the planters that wait for their lanterns finishing before tick 8,800.
