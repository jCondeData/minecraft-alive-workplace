# Royal Hall (51.9): the critic's scorecard

Sheets: `royal_hall_sheet.png` (Level I) and `royal_hall_2_sheet.png` (Level II), OLD left, NEW right: front, back,
left, right, top, and cutaways of NEW at y 5 (and y 10 for II). Eye-level shots: not filmed yet (`SCENE=rebuild_royal_hall`
films them on GitHub after the push).

**Process note (lane-c-1119-0053, 51.9's run):** this run had no way to start fresh subagents, so the brief, the
design and this scorecard were written by one agent, in that order. The designer did not open the old renders, the
old `.nbt`s or `blueprint_history/` before its own drawing was done, but it had read the old drawing's text description
while writing the brief, and the critic is not independent of the designer. The owner should treat this verdict as
weaker than a blind critic's and judge the sheets himself.

## Scores

| Score | OLD | NEW | Evidence |
|---|---|---|---|
| Silhouette | 3 | 4 | Front and top rows: OLD is one long gabled hall with a porch, a clear but ordinary church-like shape; NEW reads from any side by its two copper-capped gate towers, the ridge louver and (II, back row) the belfry, though from the left and right the hall's hip roof is a plain pyramid. |
| Depth | 3 | 4 | Front and left rows: OLD has buttresses and a porch on the front and long sides; NEW has corbelled tower rings, stone quoins and bands on every corner, sills under every window, the balcony over a recessed arcade, the throne bay pushed out of the back wall and battlemented wings, while the hall's long walls are flatter between their two buttresses. |
| Interior | 3 | 4 | Cutaway rows (NEW only; OLD judged from its top view and description): NEW has a hall with two feast tables and chandeliers under tie beams, a runner to the dais, the throne bay, a vault of four chests and a steward's room off the dais, and in II a treasury with a counting table and a council chamber upstairs; the hall itself has no hearth. |
| Detail | 3 | 3 | Front and back rows: NEW's palette holds (brick, stone dressings, dark oak, copper caps, one glass), flowers grow in their own soil, lamps stand on posts or hang; but the patchy stone mix on the quoins and the hall's front eave showing as a dark flap over the balcony are untidy, about level with OLD's banners and rose window. |

## Brief check
Every requirement met: four chests in the vault (I), twelve in II (the vault's four and the
treasury's eight); the vault door (3,2,17) beside the dais, walkable both ways (`b487RoyalHallVault`,
`qaB487VaultInTurned` pass); II's east door (25,1,14) walkable in and out (`qaB173RoyalHallEast`,
`qaB487RoyalHallEastOut` pass); both levels on the same origin and front, II keeping Level I whole; inside the plot
(19 x 25 x 25 and 27 x 25 x 25); no CHECK line, `holes.py` clean (the open entrance arcade is in ALLOW).

## Verdict: SHIP
NEW beats OLD by one point on silhouette, depth and interior and ties on detail; it loses on none.

Notes for a later pass (not blocking): 1. front view: the hall's front eave over the balcony reads as a dark flap
between the towers; 2. left/right views: give the hall's hip roof a ridge or dormers so it is less of a pyramid;
3. cutaway y5: the hall has no hearth.
