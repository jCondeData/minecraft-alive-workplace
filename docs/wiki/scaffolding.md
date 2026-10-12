# Scaffolding

Builders put up vanilla scaffolding to reach blocks with nowhere to stand near them (a raised arm, a spire's tip, a
dome's crown) and take it down again afterwards. Part of Wonders (2.0), behind its expansion gate.

Roadmap items: 35.6

## 1. What a player sees

A builder that comes back to a block it couldn't reach (the top of a tall pillar, the lantern on a dome) walks to a
spot beside it, puts a block of scaffolding under its feet, steps up on it, and does it again until the block is in
reach; then it works from the top. When that stage of the build is done it climbs back up and takes the scaffolding
down from the top, a block at a time, and the scaffolding goes back into its chests. With no scaffolding in the chests
the build waits for it like any material, and it shows on the requests board, where a carpenter can make it from
bamboo and string. Cancel the build with scaffolding up and it all comes down at once, into the chests.

## 2. How it works

**When.** A step the builder can't reach is put off once, as always: what gets built meanwhile often gives it
somewhere to stand. When the step comes round again at the end of its stage and the builder still finds nowhere to
stand within reach (no floor in reach, not even a spot the drawing fills later), it would be skipped. Instead it plans
a column of scaffolding. Never in the landscaping stage, and only the lead builder does it (helpers leave such blocks
to the lead).

**Where.** Every place to stand from which the block is in reach (eye within 5 blocks of it) is tried: one whose feet
and head are air and where the drawing leaves air or nothing. Under it a column runs down through air (again only
where the drawing leaves air or nothing) to the ground, a floor, a wall top or a column already standing, at most 32
blocks. The one needing the fewest new scaffolds wins (then the one nearest the builder), so a column already up is
built on rather than a new one started.

**Putting it up.** The builder fetches as many scaffolds as the column needs from its chests (or the village's, as
with any material), walks to the column's foot (hopping there if it can't get there), and puts each scaffold under its
feet, stepping up on it, at the build speed (`workplaceBuildDelay`). Every scaffold is recorded in the build site.

**Working from it.** The top of a scaffolding column counts as somewhere to stand. A builder going to stand on one of
its site's columns climbs it (about 0.3 blocks a tick); one going anywhere else climbs down first rather than step
off the top. When its shift ends (night, a panic) it is put down at the column's foot.

**Taking it down.** When a stage's list is finished (its put-off steps included), before the next stage starts, the
builder takes the site's scaffolding down: the top of the column it is on, or of the nearest, from where it stands if
in reach, else climbing up to it. Each scaffold goes into its bag, and so back into the chests on its next trip or when
the build is finished. Scaffolding someone else broke is simply struck off the list.

**When the build ends any other way.** A site removed with scaffolds still recorded (cancelled, its builder died, a
Wonder's part called off) has them all taken down at once, into the supply chests by its bench (else dropped there);
its builder, if on a column, is first put down at the foot.

**Asking for it.** While a builder waits for scaffolding, the shortfall (what the column needs beyond its bag and the
chests) is added to the site's missing list: the Storehouse's requests board shows it, and a carpenter who sees a
builder waiting for it makes it by the vanilla recipe (6 bamboo and a string make 6). Builders sell scaffolding too.

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| `builderScaffolding` | on (2.0) | Builders put up scaffolding to reach what has nowhere to stand near it. Off: those blocks are skipped as before |

## 4. Saved data

On each build site (`BuildSite.save`): `scaffolds`, the positions of the scaffolding its builders have put up and not
yet taken down (a list of block positions). Absent, as in every save from before 35.6: none.

Not saved: the column a builder is putting up. After a restart it looks again, finds the half-built column and builds
on it, or climbs it.

## 5. Items, blocks, jobs, commands

Vanilla scaffolding (`minecraft:scaffolding`). No new items, blocks or commands. Config text:
`aliveworkplace.config.builderScaffolding`.

## 6. Decisions

- Scaffolding only for a step that would otherwise be skipped, on its second go (35.6, lane a): putting it up on the
  first go made every starter build wait for scaffolding for blocks it reached later anyway.
- Scaffolding goes only where the drawing leaves air or nothing, and comes down at the end of each stage, so it never
  stands where a block goes (ROADMAP 35.6).
- A builder never steps off a column's top: it climbs down, and is put at the foot when its shift ends (a fall from a
  tall column would hurt it).
- Without scaffolding the build waits and asks for it, like any material (ROADMAP 35.6), rather than skip.

## 7. Known limits

- A part walled in on every side by the drawing (no air beside it within reach) still can't be reached and is skipped.
- Only the lead builder scaffolds; helpers leave those blocks to it.
- Not in the landscaping stage.
- With `workplaceFreeMaterials` on, scaffolding is put up and taken down for nothing (none taken from or added to the
  chests).
- Not tested here: a carpenter crafting it for a waiting builder end to end (the recipe and the missing list are
  tested separately).

## 8. Proof

GameTests: `ScaffoldingGameTests` (8): `ScaffoldingGameTests.builderScaffoldsUpAPillarAndItsOverhangingCap` (the 14-high
pillar with a 5 x 5 cap, nothing skipped, no scaffolding left, the chest as full as before),
`ScaffoldingGameTests.cancellingTakesTheScaffoldingDown`, `ScaffoldingGameTests.reloadWithScaffoldsUpFinishesTheBuild`,
`ScaffoldingGameTests.oldSitesLoadWithNoScaffolds`, `ScaffoldingGameTests.noScaffoldingInTheChestsIsAskedFor`,
`ScaffoldingGameTests.carpentersMakeScaffoldingFromBambooAndString`, `ScaffoldingGameTests.switchedOffNothingIsPutUp`,
`ScaffoldingGameTests.follysDomeIsFinishedWithNoSkippedSteps` (the test folly's dome and finial, nothing skipped).

Showcase scenes: `scaffolding` (a builder scaffolding up the pillar and taking it down).
