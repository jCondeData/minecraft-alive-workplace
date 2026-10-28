# Scaffolding

Builders put up vanilla scaffolding to reach blocks with nowhere to stand near them (a raised arm, a spire's tip, a
dome's crown) and take it down again afterwards. Part of Wonders (2.0), behind its expansion gate (`Expansions.M35`), which
1.0 opens with every other expansion.

Roadmap items: 35.6, 35.6a

## 1. What a player sees

A builder that comes back to a block it couldn't reach (the top of a tall pillar, the lantern on a dome) walks to a
spot beside it, puts a block of scaffolding under its feet, steps up on it, and does it again until the block is in
reach; then it works from the top, standing on the scaffolding at that height. The column is one unbroken stack
from the ground beside the wall, and it grows as the part rises: the builder never stands on the unfinished pillar or
on the cap above it. When that stage of the build is done it climbs back up and takes the scaffolding
down from the top, a block at a time, and the scaffolding goes back into its chests. With no scaffolding in the chests
it's asked for on the requests board, where a carpenter can make it from bamboo and string, but the build doesn't wait:
that block is skipped as before. Cancel the build with scaffolding up and it all comes down at once, into the chests.

## 2. How it works

**When.** A step the builder can't reach is put off once, as always: what gets built meanwhile often gives it
somewhere to stand. When the step comes round again at the end of its stage and the builder still finds nowhere to
stand within reach (no floor in reach, not even a spot the drawing fills later), it would be skipped. Instead it plans
a column of scaffolding. Never in the landscaping stage, and only the lead builder does it (helpers leave such blocks
to the lead).

**Sooner, with scaffolding on hand (35.6a).** Where the builder used to hop onto a spot the drawing still fills (the
top of the pillar it is building, a gap in it) or up onto something it can't walk to (the cap over the pillar), it
puts up scaffolding instead, from the first go, as long as its bag and chests hold enough for the column (or free
materials are on). With too little on hand it hops as it always did, so builds without scaffolding behave as before.

**Where.** Every place to stand from which the block is in reach (eye within 5 blocks of it) is tried: one whose feet
and head are air and where the drawing leaves air or nothing. Under it a column runs down through air (again only
where the drawing leaves air or nothing) to the ground, a floor, a wall top or a column already standing, at most 32
blocks. The one needing the fewest new scaffolds wins (then the one nearest the builder), so a column already up is
built on rather than a new one started.

**Putting it up.** The builder fetches as many scaffolds as the column needs, up to 16 at a time so the column can
grow without a trip per block, from its chests (or the village's, as
with any material), walks to the column's foot (hopping there if it can't get there), and puts each scaffold under its
feet, stepping up on it, at the build speed (`workplaceBuildDelay`). Every scaffold is recorded in the build site.

**Working from it.** The top of a scaffolding column counts as somewhere to stand. A builder going to stand on one of
its site's columns climbs it (about 0.3 blocks a tick); one going anywhere else climbs down first rather than step
off the top, and at the foot steps out of the column onto the free spot beside it nearest where it's going (from
inside a scaffold its path out used to fail, and it stood there five seconds and then hopped). When its shift ends
(night, a panic) it is put down at the column's foot.

**Taking it down.** When a stage's list is finished (its put-off steps included), before the next stage starts, the
builder takes the site's scaffolding down: the top of the column it is on, or of the nearest, from where it stands if
in reach, else climbing up to it. Each scaffold goes into its bag, and so back into the chests on its next trip or when
the build is finished. Scaffolding someone else broke is simply struck off the list.

**When the build ends any other way.** A site removed with scaffolds still recorded (canceled, its builder died, a
Wonder's part called off) has them all taken down at once, into the supply chests by its bench (else dropped there);
its builder, if on a column, is first put down at the foot.

**Asking for it.** When the column needs more scaffolding than the builder's bag, its chests and the village's spare
hold, the shortfall is added to the site's missing list (the Storehouse's requests board and the Village Hall show it
while the builder carries on, not only while it waits for other materials) and the step goes on as it always did:
skipped and counted. The request goes away once the builder finds enough in its chests. The build never waits on scaffolding. A carpenter makes it by the vanilla
recipe (6 bamboo and a string make 6); builders sell it too.

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| `builderScaffolding` | on from 1.0 | Builders put up scaffolding to reach what has nowhere to stand near it. Off: those blocks are skipped as before (scaffolds already up still come down into the chests) |

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
- 35.6a (the owner, 2026-10-13: in the showcase no scaffolding showed, the builder stood on the cap and the pillar had a
  gap): the cause was the builder's fallback hop, which ignored the drawing, so it stood on top of the unfinished
  pillar (leaving the cells its body filled as a gap, built later) and on the cap, and only needed two scaffolds at the
  very end. With scaffolding on hand it now scaffolds instead of hopping onto the build; without, it hops as before
  (so builds with no scaffolding in the chests are unchanged).
- Scaffolding goes only where the drawing leaves air or nothing, and comes down at the end of each stage, so it never
  stands where a block goes (ROADMAP 35.6).
- A builder never steps off a column's top: it climbs down, and is put at the foot when its shift ends (a fall from a
  tall column would hurt it).
- Without scaffolding the build asks for it but never waits for it: the step is skipped as before (coordinator,
  2026-10-12: builds stuck waiting for one scaffold broke 11 tests; builders never need babysitting, M23).

## 7. Known limits

- A part walled in on every side by the drawing (no air beside it within reach) still can't be reached and is skipped.
- Only the lead builder scaffolds; helpers leave those blocks to it.
- Not in the landscaping stage.
- With `workplaceFreeMaterials` on, scaffolding is put up and taken down for nothing (none taken from or added to the
  chests).
- A carpenter crafts for a build that is short of materials, whether the builder waits or is still building (B636).
  Whether it makes scaffolding for a build that already skipped its step without it isn't tested; put scaffolding in
  the chests to have such steps built.
- Not tested here: a carpenter crafting it end to end (the recipe and the missing list are tested separately).

## 8. Proof

GameTests: `ScaffoldingGameTests` (12): `ScaffoldingGameTests.builderClimbsAContinuousColumnBesideThePillar` (35.6a:
every tick the scaffolding is one unbroken column from the ground, the builder never stands on the pillar or cap, the
pillar never has a gap, and the builder is up the scaffolding at least 7 blocks high),
`ScaffoldingGameTests.reloadWithTheBuilderUpTheScaffolding` (saved and loaded with the builder high up the column),
`ScaffoldingGameTests.builderScaffoldsUpAPillarAndItsOverhangingCap` (the 14-high
pillar with a 5 x 5 cap, nothing skipped, no scaffolding left, the chest as full as before),
`ScaffoldingGameTests.cancellingTakesTheScaffoldingDown`, `ScaffoldingGameTests.reloadWithScaffoldsUpFinishesTheBuild`,
`ScaffoldingGameTests.oldSitesLoadWithNoScaffolds`, `ScaffoldingGameTests.noScaffoldingInTheChestsIsAskedFor`,
`ScaffoldingGameTests.carpentersMakeScaffoldingFromBambooAndString`, `ScaffoldingGameTests.switchedOffNothingIsPutUp`,
`ScaffoldingGameTests.follysDomeIsFinishedWithNoSkippedSteps` (the test folly's dome and finial, nothing skipped), and QA's `ScaffoldingGameTests.theBuilderDyingWithScaffoldsUpTakesThemAllDown` (killed with scaffolds up: all down,
none lost between the chest and his dropped bag) and `ScaffoldingGameTests.scaffoldingAPlayerBreaksIsStruckOffAndTheBuildFinishes`
(the column's foot broken by someone else: struck off, the build finishes). QA's `QaScaffoldingGameTests` (6):
too little scaffolding (none lost, none left up, no waiting), free materials (scaffolded with nothing skipped, no
scaffolding item created), never on a drawn block and never carried past its stage, the chest broken then the build
canceled (all 64 on the ground), the chest emptied mid-column (none lost), and a two-builder crew (finished, nobody
hurt, the chest as full as before). QA's `QaBuilderFixes1026GameTests.scaffoldingSwitchedOffWithScaffoldsUpStillTakesThemAllDown`
(switched off with scaffolds up: all down, all 64 back in the chest) and
`QaBuilderFixes1026GameTests.cancelWithTheChestFullDropsTheScaffoldingAndLosesNone` (canceled with the chest full: none
left standing, none lost between the chest, the bag and the ground).

Showcase scenes: `scaffolding` (a builder scaffolding up the pillar and taking it down, filmed from about 7 blocks
away at the builder's height, with stills of it up the scaffolding and taking it down).
