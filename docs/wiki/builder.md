# Builder

The flagship job: a villager who builds blueprints, block by block, from the chests by their bench.

Roadmap items: 23.1, 23.1a, 23.1b, 23.2, 23.3, 23.4, 23.8, 26.9, B120, 36.1, B173, B178, B185, B132, B197, B203, 36.15, B261, B279, B280, B290, B297, B298, B299, B340, B342, B379, B402, B403, B404, B393, B410, B394, B429, B434, B449, B486, B438, B515, B519, B487, B537, B538, B539, 36.20, 36.21, B642, B648, B636, B661, B662, B656, B792, B812, B822, B869, B880, B964, B965, B969, B973, B981, B1019, B1017, B1023, B1042, B1043, B1088, B1099, B1100, B1101, B1030, B1045, B1046, B874, B1105, B1113, B1119, B181, B872, B1117

## 1. What a player sees

You put a Builder's Bench down, and a villager without a job takes it and becomes a Builder. You give that villager a
Blueprint (right-click it), after placing the blueprint on the ground where the building should stand. The Builder
walks to the spot and builds: first clearing the ground, then the foundation, then walls and roof, then the small
things (doors, torches, flowers), then the landscaping. The materials come from chests and barrels near the bench.
Above the Builder's head you can see the progress, what it is short of, and where it takes things from. If the
chests run short, the Builder waits and tells you what is missing. Idle Builders walk over and help other Builders'
builds nearby, and later offer to fix holes in buildings they finished (a creeper's hole, a broken window): the
Builder asks first, "Repair it? [Yes] [Not now]" in chat and on the Village Hall's Repairs page, and repairs only what
you said yes to (36.15). What raiders and village fires broke is put back without asking. A finished build whose
blueprint was redrawn since it was built shows **Refresh to the latest look** on the same page (36.21): a click shows
what changes ("New: 3 decorations, 1 furniture piece · Taken away: 2 decorations"), a second hands it to the nearest
free Builder, who takes away what the old drawing left (floating or invisible pieces included; their items go to the
chests) and puts in the new pieces, as a repair. Chests and what's in them, beds villagers sleep in, job blocks and
blocks a player put there or changed stay. With no Builder free it waits until one is. Builders get
better with each building: they level up like villagers you trade with, and each level makes them faster.
A Builder who dies, or whom lightning turns into a witch (B928), drops his bag and the blueprint where he was, keeps
what was built and tells you where (`message.aliveworkplace.builder_died`, `message.aliveworkplace.builder_struck`).
Any other worker struck that way drops what is in their bag, a tool that is yours and their area's marker. A real bolt
keeps striking around it for a moment, so what a struck worker leaves is put down out of its reach (B952): on top of
their workstation (a Builder's Blueprint Table) when that is 4 or more blocks from the bolt, else on the nearest open
ground at least 4 blocks away, looked for within 8 blocks sideways and 8 under or over his feet (B964: so a builder deep
in a basement pit, or on top of a tower, within 3 blocks of his bench still has it put on the ground round the pit or
at the tower's foot); the message names that spot. The Table counts only while it still stands (B969: one broken or
blown up while he worked away from it is passed over, and everything goes on the ground nearest its old spot). Ground a
player can walk or climb to, open to the sky or joined to where he stood, comes before a nearer spot shut in a sealed
cave or room under him (B973: struck in a wheat field or on a stair roof; B1023: even a cave or room sealed moments before). Where there is none, it is dropped where he stood. On Peaceful lightning makes no witch and nothing is dropped.
At the Blueprint Table's library, the selected build is shown in 3D beside its materials: it turns slowly, can be
dragged round, and is scaled to fit, for imported and uploaded builds too. A row of style buttons under it (26.11)
recolours the picture and the materials, and Get Blueprint hands the build out in the chosen style.
Once a build is under way, a Book and Quill used on its Builder (or the Blueprint Table they work at, or an old Builder's
Bench) becomes the material list of the rest of it (B279; held in either hand, B394): every block from the stage it is in to the end that isn't standing yet, furniture and item
frames included, with what the bench's chests, the crew's bags and the village (a storehouse, other workers' spare
stock) hold ticked off. A sneak-right-click with an empty hand shows the status and then the whole list in chat. With
nothing left to place, no build, or a building being taken down, the Builder says so and the book stays as it was. Only a blank Book and Quill (no pages, or only empty ones) becomes the list: one with the player's own writing is never written over (B1042). From the main hand the player is told a blank one is needed; in the off hand it is left alone, so a plain right-click on the Builder still trades, and the Bench and Table answer as for an empty hand.
What that list is short of goes on the Storehouse's requests board straight away, while the Builder builds on with what
he has (B636): the Porter, Carpenter, Mason and Lumberjack fetch or make it during the build, not only once he reaches
the block that needs it and stands waiting. The shortfall is worked out again every 5 seconds and on the first tick
after a restart (it isn't saved), and is off the board once the chests hold enough. It is worked out again the moment the
crew takes anything from a chest too, so what is already in a bag is never asked for twice (the Lumberjack once stripped
every log in his chest for a Builder who had just collected the three he needed).
The Scan Tool marks whatever block it right-clicks as a corner, a chest, furnace, trapdoor or bench included: the click
marks it and the block stays shut and unflipped (B335), from the off hand too when the main hand is empty (B374).
A Scan Tool renamed in an anvil names the build exactly as typed, apostrophes and accents too: "Jesse's Café" is
called that in the saved line, on the blueprint, at the Blueprint Table and in every message (B458). Its id and file
stay plain (`scans/<player>/jesse_s_caf`); the name is kept by id in the world's `aliveworkplace_blueprint_names` data
(`blueprint/BlueprintNames`) and sent to players as they join. A second build of the same name is "Jesse's Café 2". A name is kept to 128 characters, cut between letters and never through an emoji, so players read the name the server keeps (B578, `QaFixEdges1027GameTests`). An
uploaded file such as `Zoë's Hut.litematic` keeps its name the same way; a plain `zoe_hut.nbt` is still "Zoe Hut", as
before. A name is plain text, as vanilla keeps chat, signs and anvil names: formatting codes and other section signs are
dropped and a line break becomes a space, so a file `§4§lServer§r Hall.nbt` is "Server Hall" in everyone's list, never
red and bold (B1046, `QaUploadNames1108BugTests`); a name with nothing left is called by its id. A name saved with codes before this fix loses them when the world loads
(`QaBuilderBugs1109bGameTests`: obfuscation, tabs and other control characters go too; accents and other scripts stay). Builds saved before this keep the names made from their ids. Shape Planner builds are named by the mod, never
typed, so they're unchanged.
A scan keeps the box's item frames, glow item frames, paintings and armor stands (B441), saved empty
(`BlueprintEntities.keepBuildable`): no framed item, no armor or held items on a stand, and nothing else that moves, so a
villager, a mob, a dropped item or a minecart with its load is never copied. Each keeps only what it is, the way it
faces (and the block it hangs on), a painting's picture and a stand's pose, arms, small size and base plate, from a scan,
an upload or a shipped build alike: an allow-list (B1101), so a creative world's stand saved with a scale attribute,
Glowing, Invisible, NoGravity or Silent, or an invisible or glowing frame, is put up as a survival player's would be
(`QaUploadStandLooks1109bBugTests`; `QaUploadFixesVerify1109GameTests`: a stand facing its file's way keeps a head
and arm pose, arms, small size and no base plate, and loses a shown name, silence, invulnerability, fire, locked slots,
tags, effects, health and scale; a marker stand isn't put up; a glow frame keeps its kind and facing, never fixed,
silent, named, invisible or glowing); an invisible stand from an uploaded file is put up visible, so two punches break it
(B1045, `QaUploadedStands1108BugTests`; `QaBuilderBugs1109bGameTests`: an invisible, invulnerable one, a scale 16 one
with no gravity and a marker one come out as plain, breakable stands, the marker left out). The builder puts them up last, as it does a
starter build's frames, each paid with its item from the chests; one with no item there is left out. Scans saved before
this have none and load as before.
Blocks with no item of their own are built from what a player would use (B793, B794): a cake with a candle on it costs
a cake and that candle; the middle pieces of cave vines, weeping vines, twisting vines, kelp and a big dripleaf's stem
each cost one glow berries, weeping vines, twisting vines, kelp or big dripleaf. What hangs from the block above it
(cave and weeping vines, stalactites) goes up last and top-down, so a vine three long is built three long. A big
dripleaf grows from the bottom as a player stacks one leaf on another: each stem goes down as a leaf and turns into
a stem when the next piece goes on top (B812), so a big dripleaf on two stems is built three tall. A piston
scanned pushed out is built retracted and pushed out by its own redstone, with its head (B795); one with no power in
the build stands retracted, never a pushed-out base with nothing in front.
A block a scan caught in the middle of a moment is built at rest, as a player's freshly placed one is (B1038): a
pressure plate someone stood on goes down up, a pressed button out, a tipped big dripleaf level, a sculk sensor (or a
calibrated one) that just heard a step listening, and a shrieker quiet; an observer, detector rail, tripwire, tripwire
hook, lightning rod or lectern caught pulsing goes down unpowered and a target block gives no signal. A plate stepped on
or a leaf tipped since still counts as built.
A melon's or pumpkin's grown stem costs one melon or pumpkin seeds and is planted as a fresh stem beside its fruit
(B865). A bubble column is the water a player poured on soul sand or magma: each of its blocks costs a water bucket
(the bucket comes back) and is poured once the soul sand or magma is down, and the bubbles rise (or sink) again, so a
scanned soul sand elevator works when it is built back (B861).
Powder snow costs a powder snow bucket, and the empty bucket comes back to the Builder as it does for a player
(B873), like the water and lava buckets he pours.
Glow lichen and sculk vein cost one item per face they cover (B792), as shears count them: a lichen on three faces
is listed as three glow lichen, so breaking the build gives back no more than was paid.
Water plants are built the way a player grows them: sugar cane goes up with the decoration, after the pool beside its
foot is poured (B816), so a cane three tall by the water stands three tall again. Sea pickles and living coral scanned
under water are built waterlogged (each costs a water bucket as well, which comes back empty), so pickles glow and
coral stays alive (B817); other waterlogged blocks, such as stairs and slabs, are still built dry. Tall seagrass costs a
seagrass and a bone meal, as a player plants seagrass and uses bone meal on it (B818), and is built both halves tall.

## 2. How it works

**One build is one "site".** A site (`BuildSite`) is the whole record of a building in progress. Everything the
Builder knows about it lives in that record, and the record is saved, so a Builder can stop at any tick (server
stopped, chunk unloaded, Builder asleep) and carry on exactly where it was. `BuilderWork` is written to be
restartable at any tick for this reason.

**Working hours.** A Builder works from early morning (1000) to dusk (11000), longer than vanilla villagers, then
idles and goes to bed at 12000. He stops building as soon as his schedule leaves work, puts his tool away and heads
for bed; he doesn't build on into the night (B839). In the morning he carries on where he stopped, also when the
world was saved and loaded at midnight (test `QaSavesAfterFixes1103eGameTests`).

**Stages, in order:** CLEAR (top-down), FOUNDATION, STRUCTURE (bottom-up), DECORATION, LANDSCAPE, DONE.
- CLEAR takes away what is in the way, but never breaks containers, its own bench or any other Builder's Bench (a
  neighbor's bench inside the build's box stays, and the build finishes round it, B132). Sand or gravel standing over
  the box (a dune, a gravel cap on a hill) falls into each layer as it is taken: at the end of CLEAR the builder waits
  for what is still coming down, then goes back over the list and digs out what fell in, up to 16 times a build (B837;
  saved as `clear_passes`, 0 in older saves).
- A spot filled again each time it is dug (sand dropped back, a cobblestone generator, a player putting a block back)
  no longer keeps the builder in CLEAR for ever: once he has dug one spot 16 times on a list it is deferred like any
  step that can't be done yet, and if it is still being refilled on the retry it is left, counted once in skipped, and
  the build goes on (B868; the spots left so are saved as `refilled`, none in older saves, so a later pass over CLEAR
  leaves them be). Ordinary terrain is still all dug out: a sand column standing over one spot (even one 24 high,
  more than 16 digs of it) is dug out with nothing skipped.
- Sand or gravel that falls into the rooms later (a prop under a pile broken mid-build) is dug out before the build is
  called done: once LANDSCAPE has run out, the builder goes back over CLEAR in a dig-out that takes only sand, gravel,
  concrete powder and the like standing where the drawing has air (never a block of the build, an anvil, a dragon egg or
  a player's block on a Steward's site), into his chests, then straight back to the end of the build (B869; saved as
  `dig_out`, false in older saves). Dig-outs share CLEAR's 16 passes, and a spot refilled each time it is dug is left
  as in CLEAR (B868), so the build always ends; what is still there after that is counted in skipped once and left for
  the repair round. Sand a player puts in a room mid-build is dug out with it (never lost); on a finished hut it stays.
- FOUNDATION fills under a build standing on uneven ground. How deep is the `workplaceFoundationDepth` rule.
- `workplaceFoundationDepth` and `workplaceLevelGround` changed with /gamerule take effect at once on a build under way
  (B1113): the foundation and the leveling still to come follow the new value, as after a restart (`QaBuilderRules1109BugTests`; QA's `QaBuilderFixesVerify1110GameTests`: the depth lowered to 0 while clearing or halfway through the foundation fills no more of the dip, the leveling switched on mid-STRUCTURE fills every pit, and a change made live holds through a restart).
- STRUCTURE goes bottom-up so every block has something to stand on. A block a player puts mid-build where the
  drawing has another is taken out into the builder's chests and the drawn block laid (a Steward's build keeps the
  player's block instead; tests `QaBuilderDisruptionGameTests`, `StewardSafetyGameTests`).
- DECORATION is what needs something to hang on or stand on (torches, doors, ladders, carpets...), placed after
  the structure is up.
- LANDSCAPE (leveling the ground round the build, dirt) never waits for materials.
- LANDSCAPE digs away ground above the floor and fills holes with dirt, but never above the land just beyond the
  leveled ring (B280): a build standing a block above the lawn round it leaves the lawn as it is, where it once got a
  raised band of dirt round it. A blueprint clicked onto a flower or tall grass stands on the ground under the plant.
- LANDSCAPE leaves the ground in and under a neighboring build alone (B838): a site under way or a finished
  building whose box the leveled ring reaches, its basement too, is neither dug nor filled, so two builds side by side
  don't dig the pad out from under each other (test `QaBuilderMidJob1102BugTests`; with a save and load just before
  the ring is leveled, `QaSavesAfterFixes1103eGameTests`).
- FOUNDATION and LANDSCAPE depend on the terrain, so a site loaded in the middle of those stages makes its list
  again from the terrain; steps already done are skipped.
- A step that cannot be done yet is put off once, then skipped (counted in `skipped`).
- A block over the world's build limit (y 320 in the Overworld) or under its bottom (y -64) can't be placed: a
  blueprint on a mountain top whose roof pokes over the limit, or a deep basement dug past the bottom. The builder
  skips such a block like any step that can't be done (counted in `skipped`), takes no material for it and never
  waits for one, so the rest is built and the build ends (B937; a door whose top half would be over the limit is
  skipped whole). The hand-over warns of it in yellow ("The top of Test Hut reaches over the world's build limit
  (y 320)..."). The held blueprint's supply report and its Material List book leave those blocks out too (B953).
  A blueprint laid wholly over the top ends at once with nothing used and the blueprint back in the chests, on
  ground as high as the world or on a lone block at y 319: no foundation goes under a block that will never be built,
  and no ground is leveled round a build of which nothing can stand (B965).
  Tests `QaBuildHeightLimit1104BugTests`, `BuildHeightLimitGameTests` (with a save and load mid-build), `B953GameTests`,
  `QaStruckAndTop1104GameTests`, `QaStruckAndTop1104BugTests`, `QaBuilderGaps1105GameTests` (a roof on y 319).
- "Done" is checked with `MaterialRules.matches`, which ignores block properties that depend on neighbors
  (a fence's connections, a stair's corner shape). Without that a finished wall could look unfinished forever.
- Nobody is built into a block: before a block goes down the builder checks there is room for it as it will
  really stand, shaped by its neighbors (a corner stair whose neighbor isn't up yet goes down straight, a quarter
  bigger), and a villager or animal that a new block's neighbor left inside a wall is moved to the nearest place to
  stand (B132, `B132GameTests`).

**Materials.** Each block costs an item (`MaterialRules`; some blocks you cannot hold in survival use their base
material: grass, podzol, mycelium, farmland and a dirt path cost dirt; infested stone, cobblestone, deepslate and stone
bricks (mossy, cracked and chiseled too) cost the same block uninfested, suspicious sand and gravel cost sand and gravel, and a petrified oak slab costs an oak
slab, each still built as drawn; B874, `QaScanItemless1103BugTests`; `QaBuilderBugs1109bGameTests`: a paid build of all of them
uses up just those base materials). Plants with no item a survival player can hold cost what grows them (B872,
`QaScanItemless1103BugTests`): a bamboo shoot costs one bamboo and is planted as a shoot (one that has grown into bamboo since
counts as built), and each piece of a chorus plant's stalk costs a chorus flower and is put down as drawn, from the end stone
up, so a stalk two tall under its flower asks three chorus flowers. A fixed set of blocks is never placed (bedrock, command
blocks, spawners, portals, barrier, fire, and frogspawn, which only frogs lay: a scanned pond is built without it; B1117,
`QaScanFrogspawn1109eBugTests`).
A Builder takes what it needs from the chests and barrels within `supplyRadius` of the bench (and 4 blocks up or
down), carries up to 27 stacks in its bag (`BuilderBag`), never more of a thing than the stretch of work ahead still
needs (B132: one block's worth too many on every trip left two Builders sharing a Storehouse each waiting for what the
other carried; B519: a helper at the chests puts back what its stretch no longer needs, such as the walls' last
planks once the crew is on the lanterns), and empties the bag at the chests when it finishes or
stops helping. A build placed mirrored (the mirror button, or a Steward's street mirroring its houses) keeps its double chests whole: each half swaps left and right with the mirror, whichever way the chest faces (B438). The mod's own facing blocks turn and mirror with the build as vanilla's do: a Storehouse built turned a quarter has its Storehouse block facing the way the building faces, and the same goes for the Village Hall, Mailbox, Shop Counter and Grave blocks (B448). One built facing the old way, in a turned build from before, still counts as built: it is never broken or repaired, so what it holds stays. Item frames, glow item frames, paintings and armor stands are put up turned and mirrored with the build too: each hangs on the wall the blueprint hangs it on and faces the way the turned, mirrored blueprint faces it, so a Guildhall placed mirrored and turned a quarter gets its item frame, a two-wide painting hangs where it was drawn, and an armor stand faces the way it was scanned (B439). Builders never copy what is inside containers, nor what a block holds in its look (B429, B434, B449): a scanned lava or powder snow cauldron is built as an empty cauldron (it asks only a cauldron), a ready composter at level 0, a full beehive or bee nest with no honey, a charged respawn anchor uncharged, a chiseled bookshelf or brewing stand showing no books or bottles, and a grave empty (never the dead villager it held). A fluid a block keeps in its data is never copied either (B486): a scanned Create fluid tank, spout, basin or item drain holding lava, honey, chocolate or a potion, or any mod's tank, is built empty, keeping its size and windows as drawn. Words a build carries keep only what a player could write by hand (B1044, B1047): a sign keeps its words, colors and glow, and a banner, chest or any mod's block keeps its name, but a click command (a sign that runs /give or /gamemode when clicked), a part the game works out (another player's position or inventory, a list of players, a score) and anything past 384 characters a line are dropped, whether the blueprint was uploaded, scanned from an operator's sign or came from a Create schematic (`BlockText`). A line is cleaned whenever the game would read it as styled text, even one a hand-written file starts with a comment or a `)]}'` prefix (B1062), and a line or name whose saved text runs past 1,024 characters (a style on every letter) keeps only its plain words, so a sign holds about what a player's can and a chunk full of signs from one file never grows too big to send (B1065). Crops are planted, never built ripe: wheat and other crops, stems, nether wart, a sweet berry bush and cocoa go in at age 0, a pitcher crop one block tall as a planted pod (B560), cave vines without glow berries; a lectern or jukebox shows no book or disc, a Mailbox shows no mail and gives no comparator signal (B536), a furnace, smoker or blast furnace is built cold (a campfire stays lit as drawn), and a bee nest or hive holds no bees (B493-B496). A crop that grew, a furnace lit or a Mailbox given mail since still counts as built. A water cauldron is still built full, as the shipped blueprints have it (water is free in vanilla). One filled by a player since still counts as built. The blueprint's tooltip and a Book and Quill list what
is still missing (the blueprint and the book in either hand, B365). A new build leaves out the chests inside its own box (they are what it puts up, its owner's), but a
repair counts them (`BuildPlan.supplyExclude`, B261): the building stands, so a Storehouse Shed being repaired, or a
shed the bench's chests stand in, hands the Builder what its chests hold.

**Whichever table (B290).** Beyond its bench's chests a Builder counts and takes the village's stock
(`BuilderWork.spareElsewhere`, `Village.stashes`): the overhead "Short of" line, the status's "Needs" and the Book and
Quill list all count it, as the fetching always did. A Storehouse is shared by `Village.sharesStorehouse`: the same side
as before (`Village.sameSide`), or, where one side is the village's own, the owner of the Village Hall whose village it
is in stands for it: an unowned Storehouse (a Steward's build) is the hall owner's, and an unhired Builder is on the
Storehouse owner's side when his build is theirs or the hall is. Before, a hired Builder never counted an unowned
Storehouse and an unhired one never counted a player's, so the Storehouse fed a Builder only while his table stood by it
(its chests were then his own), and a Builder's Workshop's table, once taken, split the village's stock. A sneak-click on
a Blueprint Table with an empty hand moves the Builder nearest the player (within 6 blocks, `BenchMoves`) there: the
old table's place is let go, the new one taken (`Stations.assign`), and the active and queued sites' bench follows. The
Village Hall's free job blocks page lists the village's Builders as takers of a free table, and the status says
`message.aliveworkplace.status.table`.

**A Storehouse with its own table (B297, B298, B299).** A Storehouse's chests are the Storehouse's, even when another
worker's bench stands nearer by them: `Village.stashes` lets the Storehouses take their chests first. Before, a Builder
at a Blueprint Table one block from the Storehouse's chests made them his, kept back for his own builds, and a Builder
at the Builder's Workshop's table counted none of them. A player's Storehouse in a village whose hall no player owns is
that village's store, so the village's own Builder on a village build (or none) counts it too. When the status says
"Needs", it also names the nearest Storehouse he doesn't count and why (`Village.unusedStorehouse`): more than the
village's 48 blocks from his table (the distance it gives is rounded up, so one just past the reach reads past it:
48.3 blocks says 49, B355), someone else's, or no chest round it he can read.

**Speed.** Exactly `workplaceBuildDelay` ticks per block (default 8; B515: it was one tick more) times a share by Builder level,
Novice to Master: 100%, 85%, 70%, 55%, 40%, rounded to whole ticks (at 30, a Novice places a block every 30 ticks and a
Master every 12), and never faster than a block a tick. Then the village's mood, traits, edicts and so on change it
(`work/Pace`). A bigger delay is never faster and a higher level never slower, up to the rule's largest value,
2147483647 (B822: over about 21 million it used to overflow and build every tick). The Fisherman waits the same rule's
delay 15 to 60 times for a bite (`FisherWork.biteTicks`, capped at the largest value), so a huge rule all but stops
him too (B832: past about 36 million it used to overflow and he caught a fish every second). `/gamerule` refuses values under 1.
A raised rule counts from the next block. A lowered one cuts the wait in hand at once to the new delay
(`BuilderLevels.rewait`, for blocks, scaffolding and a Fisherman's bite), so a Builder in the middle of a huge delay
builds again at the new speed without his work restarting (B831: he used to wait out the old delay). A restart of his
work never gives a free block: waking or a new stint keeps the wait in hand, and a fresh start (a reload, re-hired)
begins a whole wait at the rule (B845: under a huge rule he used to place one block straight away; `BuildDelayChangeGameTests`). XP: 1 per 5 blocks placed and 10 for finishing a build (`BuilderLevels`).

**Queue and crew.** A Builder takes up to 5 more blueprints on top of its current one (`MAX_QUEUE`). Up to 3 idle
Builders (`MAX_HELPERS`; Guilds can change this) help one site, each claiming steps ahead of the lead. A helper that dies drops only its bag; the steps it claimed go back to the crew and the lead finishes the build (B516). The owner
asked that time shrink with each Builder: tested at 2 builders = 50%, 4 = 29-35% of the time alone (ROADMAP 23.1a). A
helper keeps the step it claimed even after the lead has caught up with it, helpers pick only steps that still need work
(looking up to twice as far ahead), and a lead short of something a helper close by carries takes it from the helper
instead of walking to the chests (B181; QA's `QaCrewBusy1110bGameTests`, 6: a material only the helper carries used
exactly once, the helper never without a claim for long while open steps remain, the roof slabs passed over, a helper
killed holding a claim, a player breaking and blocking the helper's claims, and a restart mid-STRUCTURE, each finishing
from exactly the list).

**Who may order.** With `workplaceBuilderOwnership` on, a Builder takes orders only from the player who hired it,
their friends (`/workplace friend add|remove|list`) and operators. That includes orders from the Village Hall: the
Upgrades page and a house's new outside look go only to a Builder the clicking player may command (B880): his own
Builder is picked even when it is farther away or busy (the upgrade is queued behind its job), a Builder nobody hired
takes anyone's order, and an operator may send any. A refused order leaves the other player's Builder, its job and its
queue exactly as they were; a friend removed with `/workplace friend remove` is refused again
(`QaBuilderOwnership1104bGameTests`, 9).

**Upkeep and paths.** An idle Builder looks over buildings it (or its employer) finished anywhere in the village his
table stands in, or outside it within `maxSiteDistance` (36.20), every 1200 ticks, and fills only the holes with its usual materials; things players put there
are left alone.

**Asking before a repair (36.15).** With `repairAsk` on (the default) a Builder who finds blocks missing doesn't start:
the building goes on the list (`RepairAsks`) with the question for its owner, sent in chat to the owner (or, for a build
nobody hired the Builder for, the village's leader) as "Bram found 5 blocks missing from the Starter Cottage at x, y, z.
Repair it? [Yes] [Not now]". The Village Hall's **Repairs** tab (an anvil, one of the mod's spare pages) lists the
village's damaged builds, the questions waiting first, then repairs going on, agreed ones waiting for a free Builder,
paused ones and those left for now. A click on one opens it: **Yes, repair it** and **Not now** while it waits, **Pause**
and **Stop the repair** while a Builder repairs it, **Carry on** when paused. Yes sets the nearest free Builder who looks
after it to it at once; a Yes for a build already put right by hand says nothing is missing any more and takes it off the list. Not now leaves it as it is, and it is asked about again only once more goes missing (put right by hand meanwhile,
it comes off the list, and the next damage is asked about afresh); Pause
leaves it until Carry on. Pausing or stopping ends the repair there and then (what is put back stays, no blueprint is
handed out) and the Builder goes straight on to the next job in its queue. Handing a Builder a blueprint mid-repair
starts the new job at once instead of queueing it behind the repair; the repair is put aside, still agreed, and taken
up again when the Builder is free. Its owner, their friends, the village's leader and operators may answer; anyone
else is told it isn't theirs. Not asked: what threats took (32.16a: a gate a ram broke, a block a village fire burned),
repaired without asking as before, and a damaged Wonder's parts (35.8). `repairAsk` off: Builders repair by
themselves, as before 36.15. After a build, it can lay a dirt path to the village bell or hall (at most 96 blocks long). The path skips every 16x16x16 section where a player placed or broke blocks (the Steward's ledger, see
[Steward](steward.md)), even one built in after the path was planned, and paves the rest of the way. Ground a little lower than a full block (an old dirt path, a vanilla village's own paths round its bell, farmland, mud, soul sand) is walked over and left as it is, and a Steward's road crosses an old dirt path or mud the same way (B981; a road still never takes a field's farmland or soul sand, which aren't natural ground to it); furniture such as a lectern or a chest is never stood on, and neither is a big dripleaf, which tips over (B1030), or a magma block, which burns (B1105; workers never stand on one either, and a path goes round it or not at all), nor a lit
campfire, a cactus, a pointed dripstone or a lava cauldron (`QaBuilderBugs1109bGameTests`); nor does it lead into a fire, as on a ruined portal's netherrack, where no worker stands (B1119, `QaBuilderBugs1109bBugTests`), nor into a soul fire on soul sand or soul soil, a cobweb, a berry bush, a wither rose or powder snow: a route goes round a soul fire on its causeway and a Steward's road through the one gap in a band of fire (`QaBuilderFixesVerify1110GameTests`).

**Safe at height.** A Builder with a build (lead or helper) takes no fall damage: he stands on the build's own
upper rows (a palisade's stake tops, a tower's platform before its parapet) and can step, jump or be shoved off them.
Any other harm still hurts him, and a Builder with no build falls like any villager (B120).

**Table preview (26.9).** Selecting a library entry sends `table_request_details`; the server answers with the
materials and a `table_preview` packet (`TableServer.preview`): a palette of block-state ids and x, y, z, palette
index per cell. Only blocks a player could see are sent (air, invisible blocks, jigsaw and structure blocks are
dropped, and a cell whose six neighbors all occlude is left out). If more than `PREVIEW_CAP` (4096) cells remain, the
build is grouped into 2×2×2 cells (then 3, …), each showing its highest block, until it fits. The result is worked out
once per blueprint and kept (48 at most); unknown, sold-only, world-generation and closed-expansion ids get nothing.
The client (`TablePreview`) draws the cells with the block renderer into the screen with a scissor box.
**Placement preview requests (B340).** A held blueprint's ghost preview asks with `preview_request`
(`PreviewNetworking`); the same blueprint asked again within 100 ticks isn't sent again. Only real blueprints are
remembered for that guard, and each player's entries older than 100 ticks are dropped when they ask again, so made-up
ids from a modified client leave nothing behind.
**Basements in the preview (36.1).** A build with a basement comes out of the library with its cellar under y = 0
(`StarterBlueprints.withBasement`); the packet carries `below`, how many blocks lie under the ground (0 for most), and
cells are keyed with floor division, so at scale 2 or more the first cellar layer (y = -1) never folds into the ground
floor's cell. The client fits and centers the build by its whole height (`sizeY + below`), and for a build with a
basement draws a faint green ground plane at y = 0 after the blocks: the footprint and a little past it, with a
brighter rim, so the part under it reads as underground. The detail pane's size line then reads "17 × 12 × 11 · N
blocks, and 4 below ground" (`screen.aliveworkplace.table.size_below`, wrapped). A build without a basement sends the
same packet as before (plus `below` = 0) and draws as before.

**Table styles (26.11).** `table_details` also carries the styles the build takes (`TableServer.styles`: each of
`BlueprintStyles.all()` whose swaps change at least one of its blocks, via `BlueprintStyles.changes`; none for a Wonder,
or for a locked build). A style button asks `table_request_details` for the styled id
(`aliveworkplace:styled/<style>/<ns>/<path>`, the id the sneak-click style screen sets), and the server answers with
that style's materials and preview, each cached per styled id. `table_take` with a styled id gives `BlueprintItem.create`
of that id, the same `BLUEPRINT` component the style screen sets. A styled id whose style the build doesn't take, or a
locked build in a style, is refused (`TableServer.styleTaken`; tests in `TableGameTests`).

**Stall watch.** If a site with a Builder on shift makes no progress for 600 ticks (30 s), a log line starting
"Builder stalled" is written. The soak test counts these lines.

**Getting unstuck (23.2).** A Builder that can't walk to where it has to stand (no path at all, or a path it can't
follow) waits a moment, then hops onto that spot: it never digs or breaks its way out, so no block of the build and no
block of yours is ever broken getting it free. It gets out of a hole three deep, a well of water with sheer sides, an
island in a moat of lava, a ring of fences or a pen with its gate shut, a room with a wooden or an iron door, and the
top of its own build, in the tests within a few seconds (about 1.5 s for each of the harder traps), and goes on
building. The same holds when its chunk
unloads, or the server restarts, while it is trapped. Tests: `BuilderChaosGameTests` (five seeds, a new trap every 150
to 300 ticks through the starter cottage) and `QaBuilderStuckGameTests` (each harder trap three times through a build).

**Signs from a file (B1044, B1047, B1062, B1065, B1074, B1088, B1099, B1100).** A sign built from an uploaded or scanned file keeps its words and
their colors, but no click command, no score, selector or block-data part, and no line longer than 384 letters. Every
line the game would read as text is cleaned, also one that starts with a comment or Gson's `)]}'` prefix, so nothing
hidden that way runs when the sign is clicked. A line or name whose saved text runs past 1,024 characters keeps only
its plain words (a player's longest line, fully styled, stays under that), so a chunk full of such signs stays far
under what a client accepts. A line is measured by the letters it shows, counting a translation's argument once for each
`%s` that uses it as the game does, so a short line that repeats its words through nested translations (100,000 letters
from 333 characters) is cut to its first 384 letters too and no client freezes drawing it. A translation is measured by
the game's own text for its key (a vanilla key whose text uses its argument three times showed over a million letters
when only the key was counted). A key no language knows is measured by its fallback text, which is what the game shows
then, and a placeholder the game can't read (a %d, an argument number past the list) leaves the translation's text as
written, in the game and in the count. A keybind is measured by the language's text for its name, which a client shows
when no key is bound under that name (B1100). A line or name nested more than 256 levels deep (no player's text comes
close; a file can hold 30,000) keeps only its plain words, found without walking the nesting, so copying it can no
longer crash the server (B1099). Test: QA's
`QaSignTranslationRepeat1108bBugTests`, `QaSignLanguageKeys1109BugTests` and `QaUploads1109GameTests` (an uploaded line of exactly 384 letters, plain,
colored or through a repeated argument, stays whole; 386 is cut to 384), and `QaUploadTextEdges1109bGameTests` (3: a
fallback repeating its argument ten times, six deep, is cut to 384 letters and a short one shows as written; unreadable
placeholders neither stop the build nor grow the line, and a %% shows one percent sign; 384 letters through a red
translation stay whole and red, 385 are cut to 384). `QaUploadFixesVerify1109GameTests` (QA, through an upload and a real builder: 384 letters shown
through plain text, a vanilla key, a keybind to a vanilla key or an unknown key's fallback stay whole, 385 show at most
384; a mix of a translation, a keybind and words, a fallback repeating its argument seven deep, a %d, a %0$s and indexes
past an int copy without an error and show at most 384, and a jump keybind stays a keybind; a sign's back line and a
banner's name 30,000 arrays deep, a 2,500-deep chain of parts, and lines 250 and 260 deep are built showing their
words, also after a save and reload in the middle of the build).

**Banners from a file (B1073).** A banner built from an uploaded or scanned file keeps its first 6 pattern layers, the
most a loom gives a banner; a file's extra layers are left off, so a chunk full of banners stays under what a client
accepts. Test: QA's `QaBannerChunkSize1108bBugTests` (48 banners of 5,000 layers from one upload) and
`BlockDataSizeGameTests.aBannerFromAFileKeepsALoomsLayersAndNoMore` (3 and 6 layers are kept whole), and
`QaUploads1109GameTests` (an uploaded banner of 7 layers keeps its first 6 in order, through a save and load).

**Heads from a file (B1077).** A mob or player head built from an uploaded or scanned file keeps what a player's head
could carry: the owner's name (up to 16 letters), their id and one signed skin (`textures`, up to 4,096 characters, as
a real player's skin is). A head whose file gives it more skin entries, or a bigger one, keeps only the name and id, and
the game looks the skin up by name as it does for a head a player names. Test: QA's `QaHeadChunkSize1108eBugTests` (48
zombie heads with four 60,000-character entries each),
`BlockDataSizeGameTests.aHeadFromAFileKeepsARealSkinButNotAHugeOne` and `QaUploads1109GameTests` (uploaded heads: a real
signed skin kept through a save and load, a 16-letter name kept and a 17-letter one not, two skins or a non-skin entry
dropped).

## 3. Switches

Config file keys (`WorkplaceConfig`):

| Key | Default | What it does |
|---|---|---|
| `supplyRadius` | 8 | Chests and barrels this close to a workstation are that villager's supply chests |
| `maxSiteDistance` | 128 | How far from its bench a Builder takes a build outside the village (anywhere in the village is in reach; also how far upkeep looks) |
| `builderPaths` | true | Builders lay a dirt path from each finished building to the bell or hall; switched off, a builder also drops a path he had planned and lays no more of it |
| `builderRepairs` | true | Idle Builders repair buildings they finished when blocks go missing |
| `repairAsk` | true | Builders ask before a repair (Repair it? Yes / Not now) and repair only what was agreed; off: they repair by themselves. Threat damage is never asked about |

Game rules (per world, `/gamerule`):

| Rule | Default | What it does |
|---|---|---|
| `workplaceFreeMaterials` | false | Builders place blocks without needing materials |
| `workplaceBuildDelay` | 8 (min 1) | Ticks per block for a level 1 Builder |
| `workplaceFoundationDepth` | 12 (min 0) | How deep to fill under a build on uneven ground; 0: no foundations |
| `workplaceBuildersHelp` | true | Idle Builders help builds near their bench |
| `workplaceBuilderOwnership` | true | Only the owner, friends and operators give orders |
| `workplaceKeepWorkLoaded` | true | Builds keep running while the ordering player is online but far away |
| `workplaceLevelGround` | 2 (0 to 8) | Blocks around a finished build that get leveled; 0: leave the ground |
| `workplaceAllowUploads` | true | Anyone may upload blueprint files at a Blueprint Table (operators always can) |

## 4. Saved data

Per dimension, in the world's saved data `aliveworkplace_build_sites` (`BuildSiteManager`; at most 2000 finished
sites are remembered). Each site (`BuildSite.save`) holds:

- `id`, `owner`, `owner_name`, `structure`, `placement`: which blueprint, for whom, where and turned how.
- `stage` (default CLEAR), `cursor` (0), `retrying`, `deferred` (list), `skipped` (0), `placed` (0).
- `shown_progress`: the most progress shown so far, so the bar never goes backwards after a restart. Saves from
  0.138.0 and earlier lack it; it is worked out from the cursor (B64).
- `builder` (the Builder's id; none by default), `queued` (false), `deconstruct` (false), `repair` (false),
  `refresh_from` (36.21: the older drawing a refresh brings the build on from, its key in `blueprint_history`; absent:
  not a refresh, as in every older save).
- `no_level_ground` (absent means leveling is on), `bench` (the lead's bench; absent in pre-0.6 saves).
- `steward`, `hall` (a Steward's build from a Village Hall; absent for a player's own), `waiting_since` (absent:
  not waiting) and `player_blocks` (0): used by Stewards (27.19).
- `rechecks` (0): how often the build went back for blocks broken mid-build; `left_out` (empty): the structure and
  decoration blocks given up for good, which that look leaves be (B508; older saves have neither), but for a door or
  bed whose other half's spot is free again (B47).
- `clear_passes` (0): CLEAR's passes for what fell in, dig-outs included (B837); `dig_out` (absent: false): the build is
  back in CLEAR only to dig out sand fallen into its rooms (B869).

On the villager (attachments in `registry/ModAttachments`):
- `builder_job` (`BuilderJob`): `site` and `helper` (false by default).
- `builder_bag`: the 27-slot bag, empty by default. A stack of an item no loaded mod has any more (a mod taken out of
  the pack) is dropped with a warning in the log and the rest of the bag loads, as a chest keeps its other slots; the
  same goes for a Hauler Golem's load, a postman's courier routes (a route keeps its ends and its other filter items)
  and a worker's machine trip, which keeps its machine and stage even when every stack it carried is gone (B791,
  `RemovedModItemsGameTests`, `QaRemovedModItems1102BugTests`; QA `QaVillageVerify1102GameTests`: gone stacks first,
  in the middle and last drop out with the others kept in order, a bag of only gone stacks loads empty and the
  villager's other saved data stays, a named, damaged tool keeps its components, a bag saved again is the old save
  minus the gone stacks, a golem load with several gone keys or only gone keys).
- `builder_employer`: who the Builder works for (fields not listed here).

Per finished building (36.15): `repair_ask` (`asked`, `yes`, `not_now` or `paused`; absent: never asked, as in every
save from before 36.15) and `repair_missing` (the blocks missing when it was last looked at). It is cleared when the
repair finishes, when nothing is missing any more, and when the building is taken down or built again on its spot.
`refresh_asked` (36.21, true while a refresh waits for a free Builder; absent: false) is kept with it the same way.

Friends are saved in `aliveworkplace_friends`. Not saved, worked out again: the plan, status, missing list, helpers'
claims and trip counts.

## 5. Items, blocks, jobs, commands

- Block: Builder's Bench (`aliveworkplace:builders_bench`), the job block. Profession: Builder (`entity.minecraft.villager.builder`).
- Items: Blueprint (`aliveworkplace:blueprint`; "Blueprint: %s" when named), Blank Blueprint (`aliveworkplace:blank_blueprint`, bought from
  villagers for emeralds), Builder's Tea (`aliveworkplace:builders_tea`). The Blueprint Table makes and uploads blueprints.
- Hand over: right-click the Builder with a Blueprint. Sneak-right-click with an empty hand: status. A placed Blueprint sneak-handed over its finished building: take it down (a busy Builder queues it and says so: "Builder will take down Test Hut after Cottage (number 1 in line). It needs no materials.", B514). Water (or lava) in it is scooped up first, while its bank still holds it, with empty buckets from the Builder's bag or the bench's chests (the ones its pouring left): nothing runs out over the ground and the buckets come back full, one per bucket it cost. Short of empty buckets he still takes the liquid away but fills only the buckets he has; a bucket is never made from nothing (B961). A water bucket left over from the build (a source that filled itself before he poured it) stays as it was. Food a player is cooking on one of the build's campfires comes back too, into his bag or by the bench when it's full (B1043). A Blueprint, a Book and Quill or a
  Wonder Plan in the off hand works too when the main hand is empty.
- Commands: `/workplace blueprints`, `/workplace blueprint`, `/workplace import`, `/workplace sites`,
  `/workplace cancel <site>` (the blueprint stays placed; hand it back to carry on; a canceled take-down says "Canceled the
  take-down of Test Hut." and its blueprint, handed back plainly or sneaking, carries on taking it down, its tooltip
  reading "Give it to a Builder villager to carry on taking the building down"; moved to a new spot it's a plain
  blueprint again, B526), `/workplace friend add|remove|list`,
  `/workplace strip`, `/workplace repair yes|notnow <x> <y> <z>` (what the chat's [Yes] and [Not now] run, 36.15).
  Test and QA only: `/workplace soak`, `/workplace benchmark`.

## 6. Decisions

- Builder helpers must scale: about half the time with two builders, and so on (owner, 2026-10-04, ROADMAP 23.1a).
- Builder speed is fine: about 3.5 in-game days for the 22 starter builds (owner, 2026-10-04, 23.1a).
- Helpers only play a toss sound, no flying item, and a cap limits how many sounds play at once (owner, 2026-10-05,
  23.1b).
- "What do you need?" must be readable at a glance, from the site or the Builder (23.3, approved 2026-10-04).
- Placing must feel good: turn, mirror, ghost preview, move, cancel with materials returned (23.8, approved
  2026-10-04).
- A builder's steps need exactly the build's material list, nothing more (B22), and it fetches from its own site's
  bench chests and keeps its bench, never swapping to another builder's (B22, B28); no build breaks a bench, and a
  builder employed at a bench put down by a command holds it at once (B132).
- Progress never drops after a restart (B46, B64).
- Unfinished blocks are never claimed done: `MaterialRules.matches` ignores neighbor-dependent properties.
  Nor is a hole: before a build is called done (once LANDSCAPE has run out), the Builder looks over every structure
  and decoration block once. If one laid earlier is gone and its spot is open (a player, a mob or an explosion broke
  it mid-build; the same test the repair round uses, crops aside), the Builder goes back to the first such block and
  over the lists again, passing what still stands, and lays what's missing. It goes back at most twice a build; a
  block broken again after that is counted in skipped and left for the repair round. Blocks already given up
  (deferred, then skipped) are not gone back for (B508, `aPlankBrokenMidBuildIsBackWhenTheBuildEnds`), but for one
  kind: a door, bed or other two-block block given up because something stood in its other half's spot (a player's
  block in a door's upper cell each time the Builder came to it). If that spot is free at the last look and the block
  can stand, it is taken out of `left_out` and of skipped, and the Builder goes back and sets it, so a house is not
  called done without its door; a block that stays in the way keeps it out, counted in skipped once (B47,
  `B47GameTests`).
- An upgrade is a whole new blueprint on the same spot. Where its new room's roof runs in under the old roof's edge,
  the wall is carried up to that edge, so the roofs meet with no open blocks between them; `tools/blueprints/holes.py`
  checks every blueprint for roof holes, gaps between roofs and stray empty blocks (owner's report, 2026-10-14).
- Every doorway a build leaves lets a villager through: a villager is 1.95 tall, so under a lintel two blocks up nothing
  in the doorway or just inside or outside it may raise the floor (a carpet, a slab, a stair or seat, a closed
  trapdoor). Carpets start a block in from the door, seats stand aside, and where a stair must start right inside (the
  Manor's) the door has a fanlight (an open trapdoor flat on the outside face) in place of a lintel: a stair's back half
  is a full block up, and a villager climbing onto it still has its back in the doorway. A bed counts too: it is 9/16
  high, under a villager's step, so its path runs over a bed like a slab, and no bed stands right inside a door (the
  Carpenter's Workshop and the builder's and carpenter's village looks). `tools/blueprints/check.py` refuses every
  blueprint that breaks it (B170, B173, B178). A whole block counts as well: a door opens onto a floor at its own
  level, never onto a window's old sill, a dais or a raised floor (that leaves a villager 1.00), and a wooden door
  never opens onto a wall (B487: the Healing Center III's garden door, the Mountain Lodge II's back door, the Royal
  Hall's vault door, now a block up level with the dais, and Royal Hall II's east door). Tents are two high only under their ridge: nothing stands in a tent's
  mouth there, and no carpet lies under the low sides (the War Camp's tents, B178).
- Not only in doorways: anywhere a villager walks, a step (a carpet, a shut trapdoor, a bottom slab or stair, a bed)
  leaves it its 1.95 under whatever is over it. The game lets a villager walk where the two cells its body is in are
  open, so a carpet under a ceiling two blocks up (1.94 of room) or a seat under a shut trapdoor is a place it walks
  to and can't get onto. `tools/blueprints/check.py` flags every such step a player can get to (B197).
- Nothing a build leaves is broken or changed by the game itself once placed: every bed has its head one block
  towards its facing (the Observatory's, B537); a standing lantern, candle or torch stands on a block whose top holds
  its middle, never a lectern, a chest or a bottom slab (the Great Library's lantern on a lectern, B538); and no dirt
  path or farmland lies under a block the game counts as solid (a fence post, a wall, a tent's wool, a bed), since the
  game turns it to dirt (the Hanging Gardens' paths, B539). The blueprint kit draws grass or coarse dirt there by
  itself, `tools/blueprints/check.py` flags all three, and `BlueprintsStayGameTests` reads every shipped blueprint for
  them with the game's own rules.
- Every window's glass meets a block at both ends: a glass pane or iron bars joined on one side only, with air on the
  other, stops half way across its block, a thin strip with a gap beside it (the Terrace's bay windows, B640). So
  windows stand between jambs (piers, posts, the wall), a cage's bars meet a stone post by its way in, and glass cases
  close round their corners; where a lower roof meets a wall, a window's glass is carried out to the eaves only with a
  block on both sides of it. `tools/blueprints/check.py` flags any pane or bars run that ends in the air (a lamp's arm,
  meant to end over its lantern, is listed with its reason in the build's `open_panes`), and `B648GameTests` reads
  every shipped structure, modded copies, village houses and Wonder parts included, for them (B648). Glass joined to
  nothing with air beside it is a lone thin post and is flagged too (between two stairs whose backs face away, or
  standing out from a wall: the Sunny Plaza's arches and the Lighthouse's lower windows, B656);
  `QaWindowsAndWards1028BugTests` joins every shipped pane as the game joins it, Wonders judged whole.
- No villager is left standing inside a block in a growing village. A baby fits in nooks a grown villager doesn't
  (under a roof's stair being laid, beside a lantern on a fence post), and when it grows up vanilla finds no room for
  the bigger body there; a villager standing still in a doorway can have the door shut on it by a neighbor walking
  through. Twice a second every villager looks whether its body is inside a block; found there twice in a row, it opens
  the wooden door it is shut in or else steps (with a puff) to the nearest spot within four blocks with a floor and
  room to stand, never over lava. The same goes for a fence gate shut on a villager, a block put down where one
  stands, and a villager loaded inside a block. A villager only pressed against walls, or standing on a carpet under a
  hanging lantern, is never moved (B185).
- A villager walks in by a shut wooden door every time: it opens the door and carries on to where it was going. Vanilla
  works a villager's path out again whenever a block on it changes shape (a door opening or shutting), and aimed the
  new path at the last spot the villager had worked any path out for, which was often a bed, bell or workstation it had
  only looked at; the villager then turned round, shut the door behind it and walked off round the building. A path
  worked out again now goes where the villager is walking (B203).

- Builders ask before repairing a house, and a repair can be stopped, paused or put aside for another job (owner,
  2026-10-19, 36.15). Defense repairs after a threat (32.16a) stay automatic, as the owner hadn't objected to them: a
  wall or gate a siege broke goes back without a question.

## 7. Known limits

- **How far a build may be (36.20, the owner's "village-wide").** Anywhere in the village the builder's table stands in
  is in reach, however big the village: within the Village Hall's radius (`villageHallRadius`) or in a cell painted into
  one of the City Plan's zones, the table itself standing in either (a table in a zone painted in the plan's corner,
  past the radius, reaches the whole village too; B550, B557; QA's `QaReachZoneCornerGameTests` and
  `QaVillageReachBugTests`). Outside the village, a build's middle must be within `maxSiteDistance` (128) of the
  table, measured across the ground (B668: a tall build's height never counts; `ReachAcrossGameTests`, QA's
  `QaFarReach1028bBugTests`). A spot past it is refused with its distance rounded up, so one just past the reach reads past it, and naming the
  village when the table stands in one: "That spot is outside Mapleridge and 140 blocks from the builder's Blueprint
  Table — keep it within 128." (`assign.too_far_village`; with no village round the table, `assign.too_far`: "That spot
  is 140 blocks from the builder's Blueprint Table — keep it within 128."; B378). Farmers', Miners' and other area jobs'
  spots follow the same rule, and their refusal names the worker's own block instead of the table ("That field is 129
  blocks from the Composter the farmer works at — keep it within 128."; B453). A far site in chunks nobody has loaded waits until someone does:
  nothing loads chunks for it. A world from before keeps its sites, and a config file holding the old default 48 moves to
  128 (format 5); any other number stays.
- Blocks on the never-placed list (spawners, portals, bedrock, command blocks...) are skipped, not substituted.
- Steps that cannot be done after being put off once are skipped, so a build can finish with a few blocks missing
  (the `skipped` count).
- Upkeep looks only every 1200 ticks and only at buildings within range of the bench, so a damaged build is asked
  about at a Builder's next round, not the moment it breaks.
- The question in chat goes only to a player who is online when the Builder asks; otherwise it waits on the Repairs page.
- A refresh (36.21) knows only the older drawings the mod ships (`data/aliveworkplace/blueprint_history/`, written by
  `tools/blueprints/history.py`: every earlier version of the blueprints changed since 1.0, and every version of the
  Stone House line), and always the drawing of the latest release (its `v*` tag), so a build players put up from the
  release before a redraw is always known (B661/B662: the Clinic before its real ward beds, B639, was missing, so an old
  Clinic with slab-and-carpet cots was offered no refresh; `history.py --check` lists any release drawing missing). A
  build of an older drawing not shipped, an imported or uploaded build, and a modded-furniture
  variant drawn only in the `modded/` files show no button. A furnished build (37.3) is matched as it was furnished:
  its saved pieces stand where the spots file of its day put them, so they are the Builder's and come away; today's
  spots keep a piece only where one of the same kind already stands, and the others are furnished as a new build is
  (B642). A furnished build whose spots file has gained spots since it was furnished (the Terraces before B281) is
  offered a refresh of today's drawing itself, furnished anew where it lacked a piece (B641). The refreshed build's furniture is saved by today's spots. A bed a villager has claimed stays where the old drawing put it, so a new
  piece whose place it takes (a chest, the new bed beside it) isn't put in. The Repairs page lists refreshes after the repairs, so with
  more builds than fit, the last ones are counted under "more" rather than shown.
- A site in unloaded chunks, queued, or without a Builder is not watched for stalls.
- The table preview leaves out fluids (water and lava have no block model) and draws blocks at full brightness.
- Not tested here: the pack-server soak result of the last run (see the soak scene's pass or fail on the showcase).

## 8. Proof

`B928GameTests` (3: a builder struck into a witch mid-job, then a save and load of the build sites: the site stays
closed, one blueprint on the ground and no material lost; on Peaceful he keeps his bag, his site and his blueprint; a
struck worker drops their bag, the owner's axe and the tree farm's marker, and a block a builder only holds for the look
isn't made). QA's `QaBuilderConverted1104GameTests` (1: a zombie's bite on Hard drops the bag and one blueprint, nothing
doubled) and `QaBuilderConverted1104BugTests` (1: struck by lightning mid-job, nothing lost and the build left).
QA's `QaStruckAndTop1104GameTests` (9: real bolts on a builder by lava with his bench covered, on two builders at once and
twice on one spot leave every bag and blueprint on safe ground; a real bolt, a save and load and a second builder finish
the hut with nothing lost; a hut whose floor is the world's bottom layer is built whole with no warning; the held
blueprint's lists exactly at the top and the bottom ask for everything; a hut sunk under the bottom saved and loaded
mid-build ends with nothing lost; a blueprint wholly over the top uses nothing and comes back).
`B952GameTests` (3: one and two real bolts on a builder mid-job leave one blueprint and his whole bag; a real bolt on any
other worker leaves their bag and the owner's axe).
`QaStruckAndTop1104BugTests` (QA, B964: a real bolt on a builder at the bottom of a 4-deep pit with his bench on the rim,
and on top of a 5-high tower over his bench, leaves his whole bag and one blueprint).
`QaBuilderGaps1105GameTests` (QA, 10: a Smithy's cellar dug beside waterlogged stairs, seagrass and kelp, a basement
beside a bubble column and a kelp column, water where the basement's own walls and floor go, and a save and load mid-dig
beside waterlogged earth all end dry and as drawn; a real bolt with the bench 4 blocks off puts the bag on the bench, 3
blocks off on the ground, atop a tower at its foot and in a pond on the dry bank, each at the spot the message names; on
Peaceful a bolt makes no witch and drops nothing; a hut whose roof is the world's top layer is built whole).
`QaBuilderGaps1105BugTests` (QA, B969, 2: a builder struck with his bench broken a tick before, or blown up with a crater
under it, gets his bag on solid ground at the spot the message names).
`QaBuilderGaps1105bGameTests` (QA, 2 here: a builder struck on grass over a sealed cave gets his bag on the surface; on a
bridge over a sealed flat-roofed room, on its roof under the sky). `QaBuilderGaps1105bBugTests` (QA, B973, 2 here: struck
in a wheat field over a sealed cave, or on a bridge over a sealed stair-roofed room, the bag lies where a player can
walk to it, not inside). `BuilderStruckStaleLightGameTests` (B1023, 1: struck in a wheat field the tick the sealed cave
under it was made, before the light has caught up, the bag still lies on the surface).
`QaBuilderFixes1107dGameTests` (QA, B969, B973, B1002, B1019, 12: a bench swapped for a carpet, a bottom slab or stone,
or broken on a pillar in a lava lake, leaves the bag on solid ground at the spot the message names, none burnt; struck
over a room shut by a door or by fence gates, or on a causeway over lava, the bag lies where a player can get to; with
the unlocks switch off an ordinary build is still handed out and no Wonder is, plain, styled or in creative, the blank
kept; a lead takes from a crewmate only what the row still needs, each material on its own, nothing left on the ground,
and finishes once a short crewmate gets more).
`QaLaneC1102eGameTests` (QA, B832): six real fishermen catch nothing in 1500 ticks at workplaceBuildDelay 35791395 (the
first delay whose 60-step bite passes the int range) and at 2147483647, and the same pond at the default 8 still catches.
`B636GameTests`: a hut short only of its torch has the torch on the Storehouse board while the walls go up (not
waiting); a click there hands one over and it's off the board; after a save and reload it's listed again while he builds
on, and a torch put in the chest takes it off before the hut is done.
`RefreshGameTests` (36.21, through the Village Hall's Repairs page): a Stone House built from its first shipped drawing
refreshed to the current one (every leftover gone or turned into the new piece, every new piece there, no button
after); a small build's refresh keeping a chest and its diamonds, a bed and its sleeper's claim, a composter and the
owner's own blocks, the leftovers' items in the store; no free Builder (it waits, the first one free takes it up);
short of planks (the leftovers away, then the Builder waits and says what's missing, as for a repair); a build of the
latest drawing (no button); saved and reloaded mid-way (it finishes).
`RefreshMatchesFreshBuildGameTests` (B642, through the same page): a Stone House built from each shipped older drawing,
plain, turned, mirrored, and furnished by the spots file before B260 (saved and loaded back as an old save has it), ends
equal to a fresh build of the current drawing block for block (chests and job blocks aside); a Builder working a
furnished, turned and mirrored one through leaves no second refresh. `RefreshFurnishedCompatTests` (B642, with
Handcrafted): the same with Handcrafted's table and chairs, and as many tables as a fresh build.
`QaRefreshOldHouseGameTests` (36.21, the tester's, through the same page): the preview's exact counts by kind and every
sentence of the feature; the nearer of two free Builders gets it; two hall screens pressing the button start one
refresh; a player who doesn't own the build can't start one; every leftover's item reaches the store exactly once (also
across a save and reload mid-way, and none for a leftover a player broke first); a player's block where a new piece
goes stays; with `builderRepairs` off a waiting refresh is still taken up; a Builder dying mid-way leaves the build on
the page for another to finish.
`QaRefreshFurnishedGameTests` (B642, the tester's, through the same page): a Starter Cottage, an Inn and a Townhouse,
each furnished by the spots file of its older drawing's day and turned or mirrored, end equal to a fresh build of the
current drawing with nothing floating; a Builder refreshing a furnished, turned Stone House of before B260 across a save
and reload keeps a player's block on an old piece's spot and one where a new piece goes, leaves nothing floating and
no second refresh on the page.
`QaClinicTerrace1028BugTests` (B661/B662, the tester's): a Clinic built from 0.151.0's drawing (one bed and three
slab-and-carpet cots) is offered the refresh to real ward beds from the drawings the mod ships.

Compat tests (QA, lane E, from "Builders never copy what is inside containers"): `QaPastureCopyCompatTests` (a scanned
Cobblemon Pasture Block with a Machop let out in it is built holding no Pokémon and lets out no second Machop),
`QaCreateItemsHeldCompatTests` (a scanned Create depot, deployer and basin each holding a diamond are built empty),
`QaCreateFluidsCompatTests`, `QaCreateDrainFluidsCompatTests` and `QaCreateSchematicFluidsCompatTests` (B486: a Create
fluid tank, spout, basin and item drain each holding a bucket of lava, scanned or uploaded as a schematic, are built from
exactly their material list holding no lava). `BuiltEmptyGameTests.aFluidHeldInBlockDataIsNotCopied` (B486, without
Create: Create's, Forge's and Fabric's saved fluids are dropped, the tank's size and capacity kept).

GameTests (`src/gametest/java/.../test/`): `BlueprintsStayGameTests.everyBlueprintKeepsItsLampsPathsAndBeds` (B537-B539: in every shipped blueprint no standing lantern, candle or torch is on a top that can't hold it, no dirt path or farmland under a solid block, no half bed; with QaWonderDesignsGameTests' qaWonderStagesStand and qaWonderBedsWhole now run for the Great Library, the Hanging Gardens and the Observatory too), `QaBuildReachGameTests.everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo` (QA, from B149: in every build the Table lists but the modded looks, Wonder stages and the two camps of B424, every chest, barrel, bed and job block is within reach of a spot walked to from the ground outside), `QaStageFourBugTests.scanToolMarksACornerOnAChestOrJobBlock` (B335, a Scan Tool click on a chest, barrel, crafting table, furnace, trapdoor or Builder's Bench marks the corner), `BuilderGameTests` (108 tests), `BuilderCrewGameTests` (3), `PathLedgerGameTests` (2, B575: the path to the bell keeps out of a section a player built in, planned or laid), `QaBuilderPathsOldPathGameTests` (5, B981: a new path crosses a row of old dirt path and reaches a bell standing in a square of dirt path; routes and a Steward's road cross dirt path, farmland, mud and soul sand; a lectern, enchanting table, chest, fence or cauldron is never ground), QA's `QaBuilderFixes1107bGameTests` (2 here, B981: a finished hut's path crosses bands of farmland, mud and soul sand right across the way, turns none of them into path and reaches the bell; a Steward's road crosses mud and old dirt path but lays none over farmland or soul sand), `QaBuilderPaths1105GameTests` (7, QA: the ground kinds, a pond in the way, the bench fallback, a nearer hall, plants on the path, a planned path through a restart), `QaLedgerFramesHaulGameTests` (QA: the same with the section marked by a player really breaking or placing a block beside a Village Hall, the path's goal; and every blueprint of the mod that hangs a frame or painting, in all four turns plain and mirrored, hangs each one on its wall facing the right way, B439),
QA's `QaWalkerGroundGameTests` and `QaWalkerGround1108GameTests` (B1017: a worker's spot and a builder's path take the same 56 grounds for ground: every snow depth, slabs, stairs and trapdoors both ways up, mud, soul sand and soil, dirt path and farmland; not a lectern, chest, enchanting table, honey block, powder snow, carpet, composter, cauldron or hopper; and `QaWalkerGround1108BugTests.workersAndPathsAgreeOnADripleafAndAnEndPortalFrame` (B1030: a big dripleaf in every tilt is ground for neither, an end portal frame for both; the two rules are now one, `Walker.isGround`). `QaWalkerGround1108BugTests.aMagmaBlockIsGroundForNeitherWorkersNorPaths` (B1105: a magma block's sturdy top is ground for neither; stone in its place is).
`BuilderChaosGameTests` (5 seeds, stuck recovery), `QaBuilderStuckGameTests` (6, 23.2: a 3-deep hole, an iron-door room, a deep well of water, a lava moat, a fence pen with a shut gate, and the hole through a restart, each three times: out within 600 ticks, nothing placed or of the trap broken, the build whole), `B185GameTests` (6, villagers let out of a block), `QaBuilderBugs1031GameTests` (QA, 8: B185's let-out for a stone put down on a villager, a fence gate shut on one, one loaded inside a block, lava pits round it, a villager on a carpet never moved; B124's level line read through a sneak-click for a Miner and a Builder, and every registered job at every level), `BuildSiteSaveGameTests` (2), `BuilderBenchGameTests` (2),
`BuildSiteStepsGameTests` (2), `BuildReserveGameTests` (5), `StallWatchGameTests` (7), `BuilderKeepsBenchGameTests`, `B132BenchGameTests` (2, a neighbor's bench outlasts a build;
a bench put down and employed at in one command is held),
`TerrainStallGameTests`, `BuildersShareSpareGameTests`, `PokemonBuildsGameTests`, `QaJobBuildsGameTests` and `QaJobBuildsCompatTests` (28.13: each job's Journeyman sells their build and a Novice or an Apprentice doesn't; in all ten builds the job block takes a villager sneak-clicked with the job's item), `QaStageFiveGameTests.madeUpPreviewIdsAreNotRemembered` (B340: 5000 made-up preview ids remembered as 0, a real one
answered, its repeat at once ignored and answered after 100 ticks), `TablePreviewGameTests` (9, the table's
3D preview data; 36.1: Starter Cottage IV's preview has cells below the ground and its fitted height counts them, and
at scale 2 no cellar block shares a cell with a ground-floor block; B798: a banner, wall banner, decorated pot, sign and
head alone in a blueprint each get a preview cell, a barrier none, and a scanned lone banner has its cell on the Table), `DoorwayHeadroomGameTests` (7, B173: a villager
walks in by the Healing Center's front door onto its carpet, and by the Manor's front door onto the stair right inside:
`DoorwayHeadroomGameTests.aVillagerWalksInByTheHealingCentersFrontDoor`,
`DoorwayHeadroomGameTests.aVillagerWalksInByTheManorsFrontDoorOntoItsStair`; B178: three villagers walk from the War
Camp's yard up to the beds of two canvas tents and into the Warlord's tent up to its barrel,
`DoorwayHeadroomGameTests.villagersWalkIntoTheWarCampsTents`; B197: a villager in the Starter Cottage sits down on the
chair under the front window, where the blinds were,
`DoorwayHeadroomGameTests.aVillagerSitsDownOnTheStarterCottagesChairUnderTheWindow`; B203: with the front doors shut
as built, a villager opens them and walks in while it keeps looking at a spot outside, as villagers look at beds and
bells, `DoorwayHeadroomGameTests.aVillagerOpensTheHealingCentersShutFrontDoorAndWalksIn`,
`DoorwayHeadroomGameTests.aVillagerOpensTheManorsShutFrontDoorAndClimbsItsStair`, and a jobless villager opens the
Healing Center's door and takes its brewing stand as a cleric,
`DoorwayHeadroomGameTests.aJoblessVillagerOpensTheHealingCentersShutDoorAndTakesItsBrewingStand`).

`B261GameTests` (8, B261: a Storehouse Shed missing its hay bale and bottom ladder is repaired from its own chest,
`aStorehouseShedIsRepairedFromItsOwnChests`; a shed repaired from the chest, barrel, double chest (its second half),
trapped chest or chest one block above the bench inside it, and after a reload; a new build still leaves its own box's
chests out). `RepairAskGameTests` (9, 36.15: the ask with nothing started and the page's Yes and Not now,
`theBuilderAsksBeforeRepairing`; Yes on the hall's page repairs it, `yesOnTheHallPageRepairsIt`; Not now from the chat
command and asked again once more goes missing, a stranger refused, `notNowLeavesItUntilMoreGoesMissing`; Stop and
Pause mid-repair with the builder going straight to its queued job, `aRepairGoingOnStopsAndPausesFromTheHall`; a new
blueprint puts the repair aside, `aNewJobPutsTheRepairAside`; the switch off, `withTheSwitchOffTheyRepairByThemselves`;
a threat's damage not asked about, `whatAThreatTookIsRepairedWithoutAsking`; save and reload,
`answersSurviveASaveAndReload`; the text, `repairAskWordsAreWritten`).
`BuilderSiteListGameTests` (8, B279 and B394's off-hand book on the bench: a Book and Quill on the builder mid-build is the site's remaining steps less the
chest, `aBookAndQuillOnTheBuilderWritesWhatIsLeft`; the bag and a storehouse come off, `theBagAndTheVillageStoreComeOff`;
every stage and after a save and reload, `everyStageAndAfterAReload`; a finished and an idle builder say so,
`aFinishedOrIdleBuilderSaysSo`; the empty-hand chat list, `anEmptyHandShowsTheWholeListInChat`; the bench,
`aBookAndQuillOnTheBenchWritesTheList`; taking down and the book's words, `takingDownNeedsNothing`).
`QaOffHandBookGameTests` (2, QA on B394: an off-hand Book and Quill doesn't stop the Postal Desk handing out waiting
parcels, `anOffHandBookDoesNotStopThePostalDeskHandingOutParcels`; on a bench with no build it says so and is kept,
`anOffHandBookOnABenchWithNoBuildSaysSoAndIsKept`).
`QaBuildReserve1104bGameTests` (7, QA of B84's build reserve at its edges: only what lies beyond a build's shortfall is ever free, `onlyWhatIsBeyondTheShortfallIsFree`; the bench's own chests lower the hold item for item, `benchChestsLowerTheShareItemForItem`, but a shared store never counts as one, `theStoreNeverCountsAsTheBenchsOwnChest`; two benches add up, `twoBenchesSharingOneStoreKeepBackTheSum`; a builder's bag counts once across his queued builds, `oneBuildersBagCountsOnceForHisQueuedBuilds`, and nobody else's counts, `anotherVillagersBagDoesntCount`; holding back one item never holds back another, `oneKeptBackItemNeverHoldsBackAnother`).
`QaBenchMovesCellars1104bGameTests` (6, QA of B290 and 36.7: a Builder moved to another table mid-structure finishes from the new table's chests and never touches the old one's again, every material accounted for (`aMoveMidStructureLeavesTheOldTablesChestUntouched`); he carries on when the old table is broken after the move (`theOldTableBrokenAfterTheMoveDoesntStopTheBuild`); moved there and back, his build and his queued builds return to the first table (`aMoveThereAndBackLeavesEverythingAtTheFirstTable`); digging an old build's cellar leaves every container above ground with exactly what it held (`aCellarDigLeavesTheContainersAboveGroundAndTheirContents`) and a player's chest in the half-dug cellar with its contents (`aChestInTheHalfDugCellarSurvivesTheDig`); builds with no basement are never offered "Dig the cellar" (`aBuildWithNoBasementIsNeverOfferedACellar`)).
`B290GameTests` (6, B290: a hired Builder at a workshop table 34 blocks from the village's unowned Storehouse, in a
village whose hall his employer owns, counts its stone overhead, in the list and the status and builds from it,
`aHiredBuilderAtAWorkshopTableBuildsFromTheVillageStorehouse`; a player's Storehouse feeds the village's own Builder on
that player's build, `aPlayersStorehouseFeedsTheVillagesBuilderOnTheirBuild`; a stranger's stays theirs,
`aStrangersStorehouseStaysTheirs`; a sneak-click moves the Builder and his build to another table, both standing, after
a reload too, `aSneakClickMovesTheBuilderToAnotherTable`; nobody near moves nobody, `aSneakClickWithNoBuilderNearMovesNobody`;
the hall offers a free table to a Builder, `theHallOffersAFreeTableToTheVillagesBuilder`).
`B297GameTests` (6, B297-B299: the owner's layout, a player's Storehouse with its own table one block from its chests
and a workshop table 31 blocks off: a Builder at each counts and takes it, `bothBuildersCountAndTakeTheStorehouseWithItsOwnTable`;
alone at the workshop table, the Storehouse saved and loaded, `aBuilderAtTheWorkshopTableTakesFromAStorehouseWithAFreeTable`;
the village's own Builder and the player's Storehouse, `theVillagesOwnBuilderCountsThePlayersStorehouse`; the status's
reasons, `theStatusSaysAStorehouseIsTooFar`, `theStatusSaysAStrangersStorehouseIsntHis`, `theStatusSaysAStorehouseHasNoChests`).
`QaBuilderStockGameTests` (9, QA from B226/B290/B297's specs: stock split between the table's chest and the Storehouse
builds the hut, `theStockSplitBetweenTheTableAndTheStorehouseBuildsTheHut`; a Storehouse emptied after it was counted
holds the Builder until filled again, `aStorehouseEmptiedAfterItWasCountedHoldsTheBuilderUntilItIsFilledAgain`; the same
with the Builder saved and loaded while waiting, `aBuilderWaitingOnTheStorehouseFinishesAfterAReload`; a Storehouse
right at the village's reach is counted, `aStorehouseRightAtTheVillagesReachIsCounted`; `villageRadius` 0 shares
nothing, `withNoVillageSharingTheStorehouseIsNotTaken`; a Storehouse broken mid-job stops feeding him until placed back,
`aStorehouseBrokenMidJobStopsFeedingTheBuilderUntilPlacedBack`; barrels do as chests, `aStorehouseOfBarrelsFeedsTheBuilder`;
a Storehouse with its Porter counts once, `aStorehouseWithItsPorterIsCountedOnce`; a built Terminal Nook (37.17) feeds
the village's Builder, `aBuiltTerminalNookFeedsTheVillagesBuilder`).
`QaBuilderStockBugTests` (1, B355: a Storehouse just past the reach, 20.4 blocks off in a 20-block village, is too far and
the status says so with numbers that agree, the distance rounded up to 21, `aStorehouseJustPastTheReachIsTooFarAndTheNumbersSaySo`).
`QaBuilderLimitsBugTests` (1, B378: a spot 48.5 blocks from the table is refused with a number past the 48-block reach,
`aSpotJustPastTheReachIsRefusedWithANumberPastTheReach`).
`QaChairFacingGameTests` (4, QA from B148: in every listed build each stair chair beside a table opens toward it, 251
chairs, and still does turned four ways and mirrored, 2008; a Manor chair turned round is caught).
`QaMirroredBuildsGameTests` (2, QA from B438: every starter build's doors, beds and double chests stay whole in all four
turns, plain and mirrored; a builder builds the mirrored Tinker's Workshop with its double chest joined) and
`QaTurnedFramesGameTests` (2, QA from B439: every starter build's item frames and paintings hang on their wall in all four turns, plain and mirrored; a builder finishes the Guildhall turned a quarter and mirrored with its item frame), `B439GameTests` (1: an item frame on a wall and on a floor, a glow item frame, a one- and a two-wide painting and an armor stand each stand where the blueprint puts them and face its way in every turn and all three mirror settings, also after a save and load), `QaTurnedModBlocksGameTests` (2, QA from B448: every facing block in every starter build faces its blueprint facing turned with the build, in all four turns, plain and mirrored; a builder builds the Storehouse turned a quarter with its Storehouse block turned), `B448GameTests` (3: every block of ours with a facing turns and mirrors like vanilla's and keeps its other state; an old-facing Storehouse block in a finished turned build, and in one going up, stays built through a restart, keeping its orders, nothing skipped), `MirroredChestGameTests` (2, B438: a double chest facing any way stays joined in every turn and both mirrors, plain or
trapped, and joins when placed).
`QaDoorwayClearanceGameTests` (5, QA from B173: a villager slid through a doorway with a carpet under the lintel is
caught, and so are the Village Hall's and Supply Shop's with one moved there; villagers walk in by the shut front doors of
the Supply Shop, turned too, and the Village Hall; the sweep over every build's doors is B487's test on tests/qa-doorways-1025).
`QaDoorwayBugGameTests` (5, QA from B173, fixed by B487: the sweep over every listed build's doors, and walks in by the
Healing Center III's garden door, Royal Hall II's east door and the lodge look II's front door, and out of the Royal
Hall's vault) and `QaBuilderFixes1027bGameTests` (B487's four: a villager walks in by the Healing Center IV's garden
door turned a quarter, out by the lodge look II's front door turned the other way, out by Royal Hall II's east door,
and into the Royal Hall's vault from the dais with the hall turned half round; all four fail on the builds before the
fix).
`QaWallTowerGameTests` (7, QA from B283: hall rounds open nothing on a Palisade Tower half way up, two towers decorating
or a finished one; a tower saved and loaded mid-decoration stays one tower and is finished where it stands; its ladders
balance when its builder is killed holding three and every second of a real build).
`QaBuilders1028GameTests` (QA: B197, check.py's headroom numbers are the game's own (a villager's 1.95, a carpet's
1/16, a bed's 9/16, a top slab's and a shut hatch's undersides, a hanging lantern's); B648, every shipped structure's
glass joined as the game joins it, not as the kit stored it, ends at a jamb (Wonder stage parts aside); B229, a
Miner with no chests saved and loaded twice with finds in the bag, and one beside its own hall, puts every block dug
in the Storehouse; B166, a saved bed spot loads only with a bed villagers sleep in).
`QaScanNameBugTests.aScanNamedWithAnApostropheAndAnAccentKeepsItsName` (B458, QA: a Scan Tool named "Jesse's Café" in an anvil saves a build called that), `BlueprintNamesGameTests` (3, B458: two scans of one accented name are "…" and "… 2" on the blueprint, the saved line and after the saved names are written and read back; an unnamed scan is still "<Player>s Build"; an upload `Zoë's Hut.litematic` is "Zoë's Hut" and a plain `zoe_hut` file "Zoe Hut"). QA's `QaBuilderFixes1027GameTests` (7 of its 9 here, scanned with the Scan Tool's clicks and built by a builder turned a quarter and mirrored, a restart mid-build: a room's east-facing double chest and west-facing trapped double chest stay joined and are built empty, B438; its Storehouse, Mailbox, Shop Counter and Grave face their drawn facing mirrored and turned, nothing skipped, and only those five data blocks, with the Village Hall, count as built facing any way (a wrong-facing furnace or stairs doesn't), B448; a room's glow item frame, item frame, painting and armor stand are on its material list without what they held, and built again empty, once each, facing the mirrored, turned way and paid from the chest, B441; a Scan Tool named "日本の家" or "cozy den" names its build exactly that, also after the saved names are read back, B458). `QaBuilderVerifyScan1026bBugTests.aScannedMailboxWithMailIsBuiltEmptyAndShowsNoMail` (B536, QA: a Mailbox scanned holding mail is built with has_mail false). `QaBlueprintLootGameTests` (1, QA: a scanned build of a lectern with a book, a jukebox with a disc, a chiseled bookshelf
with a book, a decorated pot of diamonds, a campfire cooking beef, a sign and a named chest of gold, built by a Builder:
every block comes out empty, the sign keeps its words and the chest its name,
`aBuildOfBlocksHoldingItemsComesOutEmptyWithItsWordsAndNames`).
QA's `QaSignCommands1108BugTests` (4, B1044, B1047: a sign from an uploaded file keeps its words but runs no command when clicked, shows no player's position worked out with operator permission and holds no line longer than 384 characters; a scanned operator's command sign is built without its command). `QaSignCommandsLenient1108eBugTests` (2, B1062: a command hidden behind a comment or a `)]}'` prefix runs nothing) and `QaSignChunkSize1108eBugTests` (1, B1065: 48 rainbow signs from one file make a chunk packet under the 8 MiB a client accepts). `QaSignLenientForms1108bGameTests` (1, B1062: one sign in loose JSON, keys without quotes, values in single quotes and a comment between keys, runs neither command, and a translation's selector argument and a later nbt part show no player's name or position). `QaPotSherds1108bGameTests` (1: a decorated pot whose file names four diamond blocks as its sides, which a pot loaded with that data shatters into, is built as a plain pot and shatters into its 4 bricks).
QA's `QaJournalBook1108bBugTests` (1, B1042: an off-hand Book and Quill with the player's notes is left as it was by a click on the Builder) and `BuilderWrittenBookGameTests` (2: from the main hand the notes are kept and `message.aliveworkplace.site_list.blank_book_needed` is said; a book with only an empty page still becomes the list). QA's `QaBookCampfireVerify1108GameTests` (11, verifying B1042 and B1043: no pages or one page of a space is blank, one letter or writing only on the third page is kept byte for byte with the line, from the main hand on the Builder, Bench and Table; from the off hand the Builder and Bench leave it alone and the Table opens; a blueprint with the book in either hand and a Wonder Plan refuse it and write a blank one; four foods on a lit campfire, a soul campfire, an unlit one, a full bag and chest (the food lies by the bench) and a save and reload mid take-down each bring every food back exactly once). `QaBuilderClicksGameTests` (8, QA from B279/B335/B337/B342/B344's specs: a Book and Quill on the Builder's Blueprint
Table writes the exact list less the table's chest, `aBookAndQuillOnTheBuildersTableWritesTheListLessItsChest`; a table
with no build or no Builder keeps the book, `aTableWithNoBuildOrNoBuilderKeepsTheBook`; the book from the off hand,
`aBookAndQuillFromTheOffHandOnTheBuilder`; the list after a save and reload mid-structure, book and chat,
`theListAfterAReloadMidStructure`; two books with a full inventory, `aStackOfBooksWithAFullInventoryKeepsTheList`; a
placed blueprint from the off hand, `aPlacedBlueprintFromTheOffHand`; Scan Tool corners on a chest then a barrel, neither
opened, `scanToolCornersOnAChestThenABarrel`; the Storehouse board asks for nothing more after a hand-over,
`theBoardOpenedAgainAfterAHandOverAsksForNothingMore`).
`QaBuildsGameTests` (9, QA from B280/B300's specs and the builder's invariants: a cottage clicked onto a flower on a bump
leaves the lawn level, `aCottageClickedOntoAFlowerOnABumpLeavesTheLawnLevel`; a stall on tall grass leaves the lower
terrace beside it low, `aStallClickedOntoTallGrassLeavesTheLowerTerraceBesideItLow`; pots whose support breaks mid-build
are set down, `potsWhoseSupportIsBrokenMidBuildAreSetDown`, also across a reload, `aPotUnderminedThenAReloadMidDecorationIsStillSetDown`;
a reload mid-foundation or mid-landscape loses and makes nothing, `aReloadMidFoundationLosesAndMakesNothing`,
`aReloadMidLandscapeLosesAndMakesNothing`; the lead takes over a block two crew members hold,
`theLeadTakesABlockOverFromTwoMembersHoldingItAtOnce`, while a member on another column keeps its block and column,
`aMemberOnAnotherColumnKeepsItWhenTheLeadTakesAHoleOver`; `workplaceLevelGround` 0 leaves the ground alone,
`levelGroundZeroLeavesTheGroundAlone`).
`QaBuilderSwitchesGameTests` (4, QA of the builder's switches: `workplaceBuilderOwnership` off lets a stranger give a
hired Builder a blueprint, `ownershipOffLetsAStrangerGiveTheBuilderABlueprint`; `workplaceLevelGround` 8 levels 8 blocks
out and no further, `levelGroundEightLevelsEightBlocksOutAndNoFurther`; `workplaceFoundationDepth` 0 puts nothing in a
dip and uses no extra cobblestone, `foundationDepthZeroPutsNothingIntoTheDip`; the Blueprint Table hides the Mega Shrine
and Train Station while their expansions are closed, `theTableHidesTheM38AndM39BuildsWhileTheirExpansionsAreClosed`).
`QaBuilderLimitsGameTests` (11, QA of the builder's limits from the player guide, each from both sides through the
player's clicks: a Book and Quill on a Blueprint Table nobody works at opens the table, from either hand; five blueprints
line up as numbers 1 to 5 and the sixth is refused with "already has 5 blueprints lined up" and kept; the line keeps its
order over a save and each `/workplace cancel` takes up the next and hands the blueprint back still placed; a spot 48
blocks from the table is taken and 49 refused; chests 8 blocks off (and 4 up) count and 9 off don't; 4 under the table count and 5 under or 5 up don't; four idle builders
make a crew of three helpers, never four; a hired builder killed with a build and two in line drops all three blueprints
where he fell and his owner reads builder_died with the build's name and the spot; a hut placed overlapping the one under
way, or in the Nether, is refused with its sentence and kept). `QaScanEdgesGameTests` (4, QA: a Scan Tool box with nothing
built in it saves nothing and keeps the Blank; with no Blank in survival nothing is saved and the player is told; in
creative no Blank is needed nor used; in survival the save takes the Blank from any slot, the first included). `QaLevelUpWordsGameTests` (1, QA: each level-up from
Apprentice to Master tells the owner the level_up sentence with the new level's speed, 18%, 43%, 82% and 150%). `QaNeverPlacedGameTests` (1: a blueprint with a spawner, bedrock and budding
amethyst between cobblestone asks only for the cobblestone and is finished with none of the three placed). `QaBlueprintHandlingGameTests` (3: place, four quarter turns back to the
start, pick up, the leveling switch off/on/off kept through placing, turning and the hand-over; a building taken down
with the chest full loses nothing; an upgrade lines up over its own finished building, turned or not, never when
sneaking and never over another kind of building). QA's `QaTakeDown1105GameTests` (9, B961: the Lily Pond taken down across a save and reload, with no empty bucket, with four, with some in the bag and some in a chest, turned and mirrored, and beside a player's own pool he leaves alone; the Fountain, the Well and the Gem Grotto's lava: none of it left, every item back, the buckets neither made nor lost). `QaTakeDownGameTests` (8: a camp, a turned and mirrored hut and a hut with its table broken come down losing no block; a plain hand-over leaves the building standing; a stranger can't have it taken down; a take-down handed to a busy Builder waits its turn; one canceled halfway carries on when its blueprint is handed back as it came; the placed blueprint's tooltip tells how) and `QaTakeDownBugTests` (2: a canceled take-down's blueprint comes back still placed, B513; a queued take-down is announced as one, with no materials sentence, B514). QA's `QaTakeDownCampfire1108bGameTests` (1, B1043: a beef and a salmon cooking on a build's campfire come back with its plank and campfire when it is taken down). `TakeDownCancelGameTests` (2, B526: a take-down canceled halfway says it's a take-down, its blueprint goes through a save and comes back to a plain hand-back carrying on the take-down; the take-down mark is kept at its spot and through a save, dropped when moved, and an old blueprint reads as plain) and QA's `QaBuilderFixes1026BugTests` (1: handed back as the cancel line says, the take-down carries on). QA's `QaBuilderFixes1026GameTests` (5: the lead killed while two helpers work ends the build once, one blueprint on the ground, builder_died with the hut and the spot, the helpers stop and nothing is doubled or lost; a helper killed then a save and load still finishes the hut whole, no blueprint dropped, no builder_died; a queued take-down and a queued build each say their own exact line; and two scaffolding cases, on its page). `QaShapePlannerBugTests` (2, B379: drawing without a Blank Blueprint and a failed save each speak of the shape, never of a scan). `QaShapePlannerGameTests` (6, the Shape Planner's screen clicked as the client
does: each side 1 to 32, no Blank Blueprint or no block draws nothing and takes nothing, two draws give the same build
for two blanks, a hollow and a solid box of one size are two builds, a blank in the first slot is used and a full bag
gets the blueprint at the player's feet, a builder builds a drawn box from the chest). `ScanRebuildPlantsGameTests` (6, B793-B795, scanned with real clicks and built again: a cake with a red candle is
listed as a cake and a red candle and built from them, and with no candle the builder waits for one and builds no bare
cake; cave and weeping vines three long under a ceiling, and twisting vines three tall, are listed one item a block and
built that long with nothing skipped; a pushed-out sticky piston is built with its head, and without its redstone it
stands retracted). `QaScanRebuild1102dBugTests` (3, QA, B816-B818: sugar cane three tall on sand by a pool is built
three tall; three sea pickles at the bottom of a pool are built as three, waterlogged; tall seagrass in a pool is built
again, both halves, with nothing skipped). `QaScanRebuild1102fGameTests` (10, QA, B816-B818: cane one and three tall on dirt, grass, sand and red sand by one channel, cane with water only a diagonal away skipped and never floating, cane by the world's own water, a chest short of cane waits then builds it, a save and reload mid-decoration; one and four pickles glow under water, dry pickles stay dry, living coral stays alive; tall seagrass costs one seagrass and one bone meal a plant, seagrass one deep is plain seagrass). `QaBuilderLeftovers1102GameTests` (9, QA, B820: a Builder's leftovers fill the part stacks of his own chest, go to the Storehouse when every slot holds another item or the chest is brim-full, the one that doesn't fit goes on, after a save and reload too, and at the end of a job; a Hauler Golem leaves named logs and an enchanted, damaged axe untouched, also after a reload). `QaBuilderTerrain1102GameTests` (6, QA: a box full of sand dug out, a pond by the doorway leaves no water in the hut, lava pools with fire spread on burn nothing and kill no one, a crack under the floor is filled to the bottom and the build ends with no dirt in the chest, an oak through the footprint leaves no log or leaf, snow, grass and flowers are cleared and never asked for; every material adds up). `B868GameTests` (4, B868: sand put back the tick after each dig ends CLEAR with the spot counted once in skipped and the hut finished, also through a save and reload mid-CLEAR; sand put back only three times is dug out with nothing skipped; the `refilled` spots survive a save and an older site loads with none). `QaBuilderRestartClear1103bBugTests` (3, QA and B869: sand fallen into the room mid-STRUCTURE is dug out before the hut is done, also with a save and load in the middle of that dig-out, the hut as drawn and every material kept; sand in the room put back each time it is dug in the dig-out, for ever, still lets the hut finish, counted in skipped). `QaBuilderDigOut1103GameTests` (7, QA, B869: gravel and concrete powder in the room dug out into his chests, sand a player puts in the room mid-build dug out and never lost, an anvil in the room never dug and the hut still finishes, sand on the lawn and the roof outside the drawing's air left, a wall broken and sand in the room both put right in either order, the dig-out only ever in CLEAR, ending in LANDSCAPE and taking nothing built). `QaRefilledSpots1103eGameTests` (5, QA, B868: a sand column 24 high over a dirt-filled box, more than 16 digs of one spot but ordinary, all dug out with nothing skipped or lost; a spot refilled for ever saved and loaded at its 16th dig and in the middle of its retry, two spots refilled at once, and two helpers on the site: CLEAR ends with each spot counted once and the hut finished). `QaClearPassesSaved1103eGameTests` (2, QA, B837's `clear_passes`: the count of CLEAR passes survives a save and an older site loads with none; on a loaded site one pass short of the 16 sand in a cleared spot sends the builder back over CLEAR, and at 16 it is counted as left, so a reload never gives a site its passes back). `QaBuilderTerrain1102BugTests` (2, QA, B837: a sand dune over the whole site and a gravel cap over dirt, reloaded mid-CLEAR, are all dug out of the hut, nothing left falling, every material adds up). `QaBuilderMidJob1102GameTests` (6, QA: a builder killed mid-structure is followed at his bench with nothing doubled, the bench mined and put straight back, a player's block in the room neither holds the build nor is lost, two reloads three ticks apart, a builder asleep builds nothing and finishes in the morning, two touching huts on flat ground). `QaBuilderMindChange1102GameTests` (7, QA: a player who changes their mind mid-job: a build canceled mid-structure and moved is built at the new spot with the half left as it stood and nothing lost; a second blueprint mid-build is lined up and built after the first; chests emptied mid-structure make the builder wait and say "is waiting for materials", then he finishes when they're refilled; wall planks mined during decoration are laid again; a Scan Tool scan of the half-built hut holds exactly what stood and the build carries on; a canceled build stays canceled over a save and load, its bag back in the chests; a build canceled mid-decoration and handed straight back carries on with nothing doubled). `QaBuildDelay1102gGameTests` (6, QA, B832 and B834: six fishermen at `workplaceBuildDelay` 2147483647 and at 35,791,395, Masters included, catch nothing in 1,500 ticks while at 1 they still fish; a Miner's finds go to his hall's Storehouse, not a nearer kitchen, across reloads and with two kitchens, and into the kitchen's chest only when the Storehouse is full). `QaBuilderFixes1103GameTests` (11, QA, verifying B837-B839: red sand under a concrete powder dune, a sand column taller than the box on a dirt lid, and a dune reloaded every 200 ticks of CLEAR are all dug out with every block kept; sand a player puts in the finished hut is left alone; leveling leaves the pad of a finished neighbor, of a turned neighbor site still in CLEAR and of a neighbor touching only at a corner, while a plain hill in the ring is still leveled; a Builder stops within 200 ticks of dusk, stays still after a reload at night and finishes in the morning, and keeps building at midday). `QaBuilders1102bBugTests` (5, QA: a glow lichen scanned on three faces is listed as three lichen and
built on no more faces than were paid, B792; a candle cake, three-long hanging vines, a pushed-out piston and a lone
banner's preview). `QaScanRebuild1102cGameTests` (9, the tester's, B793-B795: a lit cake with a red candle is paid
once from the chest and built; kelp three tall in a glass tank, a stalactite three long, a spore blossom, hanging roots
and a hanging sign under a ceiling are built again; a pushed-out sticky piston built turned a quarter faces south with
its head; a lever-pushed piston is never left stuck (lever on with the head, or off and retracted); cave vines whose
site is saved and loaded every 7 ticks still end three long). A big dripleaf's stem isn't built yet (B812).
`QaBuilderFixes1103cGameTests` (12, QA, B868, B873, B879: a CLEAR spot refilled 16 or 20 times and then left is dug on
the retry with nothing skipped; a spot given up for being refilled stays given up through a save and a later CLEAR
pass, counted once in skipped; two refilled spots give skipped 2 and the build finishes; water and powder snow paid
from a chest or the bag give one empty bucket back per pour, and stone leaves nothing extra; a Yes after one of two
holes was mended is still agreed and repaired; a wall mended by hand leaves the list after Yes or Not now, and new
damage is asked about afresh; damage done before the Yes is repaired; the Repairs page's Yes on a mended wall promises
no repair). `QaBuilderSurvivors1103cGameTests` (4, QA, B865, B831: a fresh melon or pumpkin stem counts as the grown
stem of its own kind only; scanned grown stems are planted once, one seed each, and never dug or replanted; a stem
already growing where the grown stem is drawn is left standing and costs nothing; a scaffold wait with one tick left
ends at once when the delay is lowered to zero).
`QaScanRebuild1102dGameTests` (6, the tester's: a lily pad on a pool, a scaffolding tower that is part of the scan,
four pink petals, three turtle eggs, a full water cauldron, and farmland with wheat beside a dirt path, each scanned and
built again whole with nothing skipped and asked for as a player places them).
`QaScanRoundTripGameTests` (1, from
the Scan Tool's guide page: a hand-built room with two double chests lying either way, a door, a bed, a wall torch, a
hanging lantern, an upside-down stair and a ladder, scanned with real clicks and built again a quarter turned, comes out
block for block turned, the chests still double, `aScannedRoomIsBuiltAgainTurnedWithEveryPieceWhole`). `QaScanPotGameTests`
(1: a decorated pot of four pottery sherds on a stone, scanned with real clicks and built from a chest holding one plain pot,
shatters into no sherd when broken with a pickaxe, while the scanned pot itself gives its four,
`aScannedSherdPotGivesNoFreeSherds`). `ScanEntitiesGameTests` (2, B441: a room scanned
with real clicks holding a framed diamond, a painting, an armor stand in a helmet, a cow, a villager, a dropped diamond
and a loaded chest minecart keeps only the frame, painting and stand, empty; built again a quarter turned from a chest it
gets an empty frame facing the turned way and the painting, both paid, and no stand with none in the chest; with one, a
bare stand, the helmet left in the chest) and `QaScanFramesGameTests` (1, QA from B441: a scanned room's item frame and
armor stand are built again).

Showcase scenes (`tools/showcase/scenes.py`): `repair_ask` (36.15: the question in chat, the Repairs page, Yes),
`refresh_old_house` (36.21: a Stone House of its first drawing, the Refresh button with what changes, the house after), `builders` (three builders, three starter builds), `soak` (10 builders,
every starter build), `preview` (ghost preview and status), `placing` (turn, mirror, cancel, slope), `missing` (the
missing-materials tooltip and list), `table`, `table_preview` (the library's 3D preview), `table_basement` (Starter Cottage IV and the Storehouse with their cellars), `shapes`, `scan`, `style_menu`, `crew` (four builders on one build),
`pace` (speed cap), `partners_engine`, `builder_list` (B279: the list of a build under way, as a book and in chat),
`builder_table_move` (B290: the Storehouse counted at a workshop table, and a sneak-click moving the builder).
