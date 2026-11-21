# Pokémon Center, Sunny Plaza look: design notes

Round 1 (the first drawing, the concepts and the contract): `../pokemon_center/NOTES.md`, "Sunny Plaza".

## Round 2 (plaza)

The critic sent it back (`briefs/cobblemon_50_12_critic.md`, "Pokémon Center ... round 1"; Plaza I scored 3 -> 3 on
silhouette, depth and interior and 3 -> 2 on detail, Plaza II 2 -> 4, 3 -> 3, 2 -> 3, 3 -> 3). Its five notes, and what
changed for each:

1. **I top and cutaway y <= 7, ragged roof.** The low hip of alternating bottom and top slabs (with whole bricks on the
   wall line and a ceiling showing through its steps) is now one hip in a new part, `parts/plaza_arcade.mjs`
   `tileHip`: a slab course, then a stair course, half a block up per ring, from one continuous slab eave at y 6 round
   all four sides (x 0..12, z 2..12) to a short brick ridge at y 8. No ring breaks or patches. The eave sits on the
   arcades' outer line, so the whole roof reads as one shape over one frieze.
2. **I back, left and right, flat walls with flush windows.** A shallow arcade (`tileArcade`) stands one block proud of
   the hall on the west (x 0), east (x 12) and back (z 12): a smooth plinth at y 0, cut-sandstone piers, a round head
   (upside-down stairs and a chiseled keystone) over each three-wide bay, and the red frieze at y 5. Each bay frames a
   window set a block back, with its sill stair standing in the bay. The back uses the front loggia's rhythm (piers at
   0, 4, 8, 12), so the arcade reads as carried round the house.
3. **I and II left, white walls beside sandstone.** One wall material: sandstone (rough walls, cut piers and dressings,
   a smooth socle, chiseled keys), with the red frieze as its single trim (round the arcades, the loggia front and
   sides, the bell-cote's top and the market's court front). No white concrete anywhere.
4. **II right, the market hall's long side a plain wall.** Its lane side gets the same arcade at x 19 (piers at
   z 12, 16, 20, 24, round heads over the three back windows, the frieze under the eave), so every long face of II has an
   arcade: glazed on the court, open on the lane.
5. **II cutaway y <= 3, the fountain alone on sand.** It now stands in a ring: a bench of three seats facing the water
   on each side, and a 2 x 2 soil bed sunk in the paving at each diagonal (a flowering azalea on the outer corner,
   poppies, alliums, cornflowers and daisies). The lawn bed with the Pasture Block moved to the court's south-west
   corner (x 1..5, z 20..24) to make room, with flowers in its corners.

Also asked:
- **Interior.** The flat ceiling is gone: the hall is open to the tile hip, and its three lamps hang on chains from it
  (the hall feels taller and the cutaway at y <= 7 shows the rooms). Lobby: two couches with a low lamp table before
  each (owner rule 2: lamps on tables or hung), the runner, azaleas by the door. The desk lost its west counter, so
  the way to the Nurse's side is a two-wide gap. The Nurse's office behind it: her lamp table and chair, two chests
  and two cupboards of remedies, two bookcases with a fern and a tulip, a white rug, a hung lamp.
- **Silhouette.** The bell-cote is now two deep and rises to a red-banded cap and tile gable at y 11 (lightning rod
  y 12), half again the ridge's height: the hero in every view.
- **Lean Level I.** 742 blocks (round 1: 649; the old one 560). The arcades cost about 130; the ceiling (63) and the
  hall wall's top course behind the frieze on three sides (about 25) came out to pay for them. Ship step: confirm
  `buildsPokemonCenterPlaza` still finishes inside 12,000 ticks (the default look's 762 was flagged the same way).

## The contract (checked on the candidates with a script)

Healing Machine only at (6, 1, 7); PC (10, 1..2, 7); Nurse cell (6, 1, 8) free on a floor; camera cell (8, 2, 4) open;
chests at (2, 1, 9) and (10, 1, 10); front door (6, 1, 3) under the middle arch of the z = 0 loggia. II: Shop Counter
(16, 1, 18), Pasture Block (3, 1..2, 22) (moved from round 1's (4, 1, 21)), the back door (6, 1, 11); II keeps 99.6 %
of I by block and state (only the back window's three cells become the door). Sizes: I 13 x 13 x 13, II 20 x 13 x 26.
Stacks (upper bound): I 33, II 54. `rebuild.py --candidate`: no CHECK line; `holes.py`: 0.

Pins the ship step must move (on top of round 1's list): the declared sizes are now 13 x 13 x 13 and 20 x 13 x 26;
the Plaza II Pasture Block is (3, 1, 22); the lone-pane arch check re-aims at the keystone (6, 4, 0).
