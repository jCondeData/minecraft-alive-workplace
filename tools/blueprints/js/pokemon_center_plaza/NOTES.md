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

## Round 3 (plaza)

The critic sent it back again (`briefs/cobblemon_50_12_critic.md`, "Pokémon Center ... round 2": Plaza II passed, Plaza I
scored 3 -> 4 silhouette, 3 -> 4 depth, 3 -> 3 interior, 3 -> 2 detail). Level I only; II grows from I unchanged in plan
and picks up I's changes, plus the window fix on its lane arcade. Renders: `build/rebuild/pokemon_center_plaza_r3_renders/`.
Its five notes, and what changed for each:

1. **I front and right, bare outside.** The loggia steps back a row (z 1..2) and z 0 becomes the street edge (`APRON`):
   steps up to the middle arch, a planter in soil either side of them (x 1..4 and x 8..11: flowering azaleas at the
   ends, poppies, daisies, cornflowers and tulips between), a lamp on a jungle post at each front corner (x 0, x 12), and
   a striped awning over each planter, sloping down from the loggia's frieze (quartz and tile stairs in turn). The
   right view picks up the corner lamp and planter, and every bay now has its pot of flowers (note 2).
2. **I right and back, the arches read as raw holes.** Every arcade bay is a window now (`bayWindow`): a cut-sandstone
   breast across the bay at y 1 in the arcade's plane (with the piers it makes one sill course round the house), a pot
   of flowers on its middle, and set a block back an arched light that follows the round head (three panes at y 2, the
   middle one at y 3) under a sandstone head; the keystone stays. Same on II's lane arcade (x 19), so its long side
   reads as three glazed arches between piers (note 5).
3. **I cutaway y <= 3, small pockets.** The office is gone: one waiting room from the door to the machine. Two benches
   against the front wall either side of the door face the desk, a low table before each, a fern and a lamp table at
   each bench's outer end, azaleas either side of the door, the runner to the desk, lamps hung over the tables; open
   floor (z 6) before the desk; the desk lost its east counter, so there is a way round each end. Behind it (z 8..10)
   one storage corner: two chests and a barrel, a cupboard and two bookcases with plants against the walls, azaleas
   either side of the back window (its middle kept clear for II's door), a white rug where the Nurse stands; red cloths
   on the side walls either side of the desk.
4. **I top and right, almost no eave.** A cornice ring of upside-down sandstone stairs (undercut outward) runs at y 6
   on the roof's outer line all round, over the red frieze; the tile hip now starts from a slab eave at y 7 on top of
   it (ridge y 9), so frieze, cornice and eave read as one moulded overhang on every side, and at the front corners the
   eave carries out over open ground. The plot (13 x 13, the arcade on its edge) leaves no cell for a deeper eave. The
   bell-cote rose a course with it (frieze y 10, rod y 13) to stay the hero, and its back is closed (its lantern hangs
   in a niche) so no gap opens into the roof.
5. **II right, the market hall's long side plain.** Its lane arcade's three bays get the same breast, pot and arched
   glass as I's (note 2). The rest of II is as the critic passed it.

Blocks: I 785 (round 2: 742; old 560), II 1530 (1479). The cornice ring (44), the breasts and pots (+3 a bay) and the
apron cost about 100; the walls' footing course at y 0 (hidden between the arcade's plinth and the floor, kept under the
door), the office furniture and the loggia's old front row paid back about 60. Stacks (upper bound): I 44, II 59.

## The contract (checked on the candidates with a script)

Healing Machine only at (6, 1, 7); PC (10, 1..2, 7); Nurse cell (6, 1, 8) free on a floor; camera cell (8, 2, 4) open;
chests at (2, 1, 9) and (10, 1, 10), a barrel at (2, 1, 10) (round 3); front door (6, 1, 3) under the middle arch of the
loggia (z 1 since round 3), up the steps at z 0. II: Shop Counter
(16, 1, 18), Pasture Block (3, 1..2, 22) (moved from round 1's (4, 1, 21)), the back door (6, 1, 11); II keeps 99.6 %
of I by block and state (only the back window's three cells become the door). Sizes: I 13 x 14 x 13, II 20 x 14 x 26 (round 3; 13 tall before).
Stacks (upper bound): I 44, II 58 (round 3). `rebuild.py --candidate`: no CHECK line; `holes.py`: 0.

Pins the ship step must move (on top of round 1's list): the declared sizes are now 13 x 13 x 13 and 20 x 13 x 26;
the Plaza II Pasture Block is (3, 1, 22); the lone-pane arch check re-aims at the keystone (6, 4, 0).

Round 3 adds for the ship step: the declared sizes 13 x 14 x 13 and 20 x 14 x 26; the lone-pane arch check's keystone
is now (6, 4, 1) (the loggia stepped back a row); II keeps 99.2 % of I (the back door's cells, the back bay's breast
middle and its pot).
