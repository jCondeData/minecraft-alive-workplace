# Inn (51.2, flagship: A and B): the critics' scorecards

Brief: `inn.md`. Designs, each drawn blind by its own designer: `tools/blueprints/js/inn_a/` ("the Jetty and the
Stair Tower": a stone taproom with the guest floor jettied over the street, two three-bed chambers under one tall
gable; II adds a stable yard with a gallery and covered gate, III an ale cellar under the bar; 13x16x13, II and III
27x16x13) and `tools/blueprints/js/inn_b/` ("the Arcade Inn": a tall white timber gable to the street with the guest
floor pushed out over a covered arcade; II adds a coach gate, a stable gable with hayloft and a walled yard, III a
vaulted beer cellar; 13x16x13, II and III 26x16x19). Sheets (OLD left, NEW right, five views per level and
cutaways): `inn_a_sheet.png`, `inn_b_sheet.png`; A and B side by side: `inn_pick.png`. Eye-level shots: not filmed
yet. Every round's critic was a fresh subagent. Candidates: `build/rebuild/inn_{a,b}/`.

## Round 1: both BACK

| Score | OLD (A's critic) | A | OLD (B's critic) | B |
|---|---|---|---|---|
| Silhouette | 3 | 3 | 3 | 4 |
| Depth | 3 | 3 | 3 | 4 |
| Interior | 3 | 3 | 3 | 3 |
| Detail | 3 | 3 | 3 | 3 |

A's notes (applied): finish the back gable; a framed back; whole plaster panels; framed guest rooms; a railing round
the II terrace. B's notes (applied): gravel over a void in Inn III (a CHECK line: a brief miss); framed guest rooms
with a nightstand per bed; a hollow glass attic; flat back wall and unbroken roof planes; the wing eave touching the
main roof.

## Round 2: A BACK, B SHIP

| Score | OLD (A's critic) | A | OLD (B's critic) | B |
|---|---|---|---|---|
| Silhouette | 3 | 4 | 3 | 4 |
| Depth | 3 | 3 | 3 | 4 |
| Interior | 3 | 4 | 3 | 4 |
| Detail | 3 | 3 | 3 | 4 |

B's optional polish for the ship step: furnish the cellar more; break up the gravel yard (a well, trough or cart path);
sills or a buttress line on the flat stone back. A's notes (applied): windows on the back and east walls; glazed
guest-floor windows; furnish the II wing; the chimney through the jetty; a bare cellar.

## Round 3: A BACK (its second time back, so it goes to the owner as it stands)

| Score | OLD | A |
|---|---|---|
| Silhouette | 3 | 4 |
| Depth | 3 | 4 |
| Interior | 3 | 3 |
| Detail | 3 | 3 |

A wins two scores (silhouette, depth), loses none; three wins are needed. Its remaining notes: the link between the
main block and the II annex has no roof; a raised grass lump at the front-left corner; plain 2-wide white panels in the
gables and upper storey; a flat stone rear ground floor; a sparse taproom. If the owner picks A, the ship step does
these first.

## Brief checks (both, every round, on the candidate files)

One Shop Counter facing west with two clear blocks behind it, air in front, dark oak planks either side; six bed
heads, all at the counter's y + 5; the front door at (6,1,2); one standing red banner on a fence, rotation 8; no
campfire east of x 11; drink chests within reach; Inn I 13x16x13 at about 58-67 stacks (limit 108); II keeps 99% of
I; III above ground is II plus one hatch, over a 4-deep cellar with a ladder; every required furniture kind (A 48,
B 57 spots); check.py and holes.py clean. Ship step: `StarterBlueprints.INN_2`/`INN_3`'s declared sizes change (A:
27x16x13; B: 26x16x19). Not tested: the GameTests (pathing, build time, the furnished builds).
