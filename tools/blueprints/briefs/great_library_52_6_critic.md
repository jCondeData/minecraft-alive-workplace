# 52.6 critics (lane-d-1119-0708): the Great Library ("the Scroll Rotunda")

Sheet: `tools/blueprints/briefs/great_library_sheet.png` (OLD composed from its stage parts and offsets in
`data/aliveworkplace/wonders/great_library.json`, NEW from the candidate's stages; four sides, top, cutaways y 5/12/20,
stage I-III fronts). No eye-level shots. Each critic was a fresh subagent; the brief was checked on the candidate stages.

## Round 1: BACK
| Score | OLD | NEW |
|---|---|---|
| Silhouette | 3 | 4 |
| Depth | 3 | 3 |
| Interior | 3 | 3 |
| Detail | 3 | 3 |
Brief: nothing missed. Notes: 1. bare wing upper floors (cut12); 2. drum windows a thin strip hidden behind the wing
roofs, scattered cut copper; 3. back-gable pilasters stop flat under the eave; 4. flat wing walls, small windows;
5. portico columns without base or capital, no cornice under the pediment.

## Round 2 (b5f6554b7): SHIP
| Score | OLD | NEW |
|---|---|---|
| Silhouette | 3 | 4 |
| Depth | 2 | 3 |
| Interior | 3 | 3 |
| Detail | 2 | 3 |
Brief: nothing missed (41x34x41 from y 0; 14,967 blocks in 14,400-17,600; four stages that only add, nothing floats;
enchanting table at (20,3,22) with ~26 shelves in range; scholar's spot (21,3-4,18) clear with floor and sightline;
5 empty lecterns, 1 cartography table; bookshelf 2,028, copper 1,437, quartz 152, diorite+smooth stone 5,983, dark
oak planks 516, glass 400; 6 doors clear both sides; check.py 0 problems on every stage). Not verified without
GameTests: qaWonderStagesStand, exact enchanting power, mirrored/rotated placement.

Polish for a later pass (not needed to ship): 1. the drum narrows toward the roofs like a funnel and has glass patches
at random heights (front zoom): straight sides, one window band; 2. lone white quartz stubs on the pilasters at
mid-height (back zoom): join or remove; 3. ground-floor wing rooms (cut5) large and sparse: stacks or tables across
the middle; 4. blank pediment: a recessed field; 5. each corner garden is one oak on grass: vary them.

## For the ship step
Ship like the Great Wall (3982bf870) and Hanging Gardens (9d76e1d4e): `wonders.WONDERS["great_library"]["draw"]` from
`js/great_library/stages.py`, `save_wonder`, stage titles (I The Stacks, II The Galleries, III The Rotunda, IV The Dome
and the Portico; check they fit the existing lang keys), rewrite the old-look pins in QaWonderDesigns* /
WonderBlueprint to the new named parts (same kinds of check, decision in the Notes), a
`WonderDrawingsGameTests.aGreatLibraryOfThe<key>DrawingKeepsItsOldStages`, `rebuild_great_library` scene, guide,
wiki, CHANGELOG. Check the scholar scene's standing spot and qaWonderEnchantingPower in game.
