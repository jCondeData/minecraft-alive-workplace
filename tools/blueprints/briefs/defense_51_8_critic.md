# 51.8 critics: Barracks, Guard House, Fire Station, Gatehouse, Lookout Tower (lane-c-1118-2153)

Each family: blind brief (`briefs/<family>.md`), blind design (`tools/blueprints/js/<family>/`), and a fresh critic who
saw the old-vs-new sheet (`briefs/<id>_sheet.png`) and the new cutaways. Old cutaways were not rendered and no
eye-level shots were filmed, so the old interiors were scored from exterior views and block counts.

| Family | Silhouette | Depth | Interior | Detail | Verdict |
|---|---|---|---|---|---|
| Barracks ('the Gate Ward', 3 levels) | 2 -> 4 | 2 -> 3 | 2 -> 4 | 2 -> 3 | SHIP |
| Guard House ('the Gate Watch') | 3 -> 4 | 3 -> 4 | 2 -> 4 | 2 -> 4 | SHIP |
| Fire Station ('the Hose Tower') | 3 -> 4 | 3 -> 4 | 2 -> 3 | 3 -> 3 | SHIP |
| Gatehouse I | 2 -> 3 | 2 -> 4 | 1 -> 3 | 2 -> 3 | SHIP |
| Gatehouse II | 2 -> 4 | 2 -> 4 | 1 -> 3 | 2 -> 3 | SHIP |
| Lookout Tower ('the Hoarded Watchtower', 4 levels) | 3 -> 4 | 2 -> 4 | 2 -> 3 | 3 -> 4 | SHIP |

No critic found a brief requirement missed.

## Polish notes (none block the ship)

- Barracks: lone 2-block white-concrete patches in the gables (front/back, low); Level I's right side is weak (plain
  tower side over the half-height yard wall); a string course where the stone meets the dark-wood top storey.
- Guard House: 940 blocks against the brief's "about 900" (run `workplaceBuilt_guard_house` before landing; trim the
  side tie beams first); the cross gable's barge stairs must not hang over the main roof (the School's builder hang);
  the village variant in `village_looks.py` was not redrawn.
- Fire Station: the timber loft is bare (cut7_top); the stepped brick edge of the front gable looks ragged; the
  lightning-rod downpipe reads as a thin red line on the left wall; the hall's side walls are plain.
- Gatehouse: the open slot between the towers at y 11-15 makes the front read as one split block; the ground
  guardrooms hold only a ladder and a lantern; 16 tall stands 10 over the stone wall segment (owner to judge in a
  wall-line shot). Declared heights go from 9 to 16.
- Lookout Tower: gallery stations have 2 cells of headroom under the eave (check `BattleStations.view`); Level I
  grows to 430 blocks (the 9,000-tick `buildsLookoutTower`); the tower shaft is a bare ladder; the crenellated
  guardhouse beside a hip-roofed tower speaks two languages.
