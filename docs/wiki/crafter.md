# Job chains (the retired Crafter)

Every job that crafts for the builders now works through whole recipe chains of its own kind: the Carpenter saws logs
into planks, cuts the planks into slabs, then makes the barrel or chair the builder was waiting for; the Mason fires
clay into bricks, lays them into blocks and cuts the stairs. What no job in the village makes stays unmade and is named
on the builder's shortage line. This replaced the Crafter, a separate job that made everything (37.14, retired by
37.14a, owner 2026-10-21: "each job should craft what the builder or other villagers need when they need it"). Part of
Furnished homes and modded storage (2.1), behind its expansion gate (`Expansions.M37`).

Roadmap items: 37.14, 37.14a, 37.15, 37.16, 37.16a, B396, B426, B696, B721, B760, B773, B778, B916, B920, B931, B1018, B1111, B1112, B1151, B1170, B1171, B1267, B1268, B1273, B1277, B1278, B1279, B1288, B1293, B1301, B1311

## 1. What a player sees

A builder short of three barrels with only oak logs in the chest: the village's Carpenter fetches the logs, and at the
bench the line above their head says "making 3 Barrels (step 1 of 3)", then step 2 (the slabs), then step 3; the barrels
go into the builder's chest and the build carries on. A Mason with a furnace by the stonecutter turns clay balls into
brick stairs the same way (fired, laid, cut, a coal burnt for every eight fired). The Librarian makes the bookish parts,
the Tinkerer the metal ones (raw iron fired into ingots, then bars or a lantern), the Toolsmith tools, the
Leatherworker leather goods (item frames, leads, leather armor), the Shepherd wool, carpets, beds and banners at his loom
(since 46.2), the Florist pots, dyes and moss carpet (since 46.3), the Cleric brewing stands, ender chests and
beacons at his brewing stand (since 46.4), the Beekeeper candles and honeycomb blocks at her Apiary (since 46.5), and,
with CobbleFurnies, the
Furnicrafter its furniture (the planks and sticks first, then the piece). A big order is made a bagful at a time until
there's enough. Which job makes what is each job's own list since 46.1 ([Each job's own list](job-lists.md)).

A job only crafts from what no open build needs. When two builds share a chest or the village Storehouse, the logs
stocked for one are never turned into planks for the other: the Carpenter uses only what's left over once every other
build's remaining list is counted, so neither build ends up waiting for a log that went into the other's planks (B696).
What the builder (and his helpers) already carry for the build counts toward it: with the stall's 21 logs in his bag,
the logs left in his chest are all spare, and the Carpenter saws them. Before B1151 the same 21 were held back in the
chest as well, so the Carpenter made nothing or stopped part-way, and the builder waited for the rest beside a chest of
logs.

What a job makes for a builder goes where that builder looks for materials: the chests by his Blueprint Table, or,
when there are none, the village Storehouse (else another village-mate's chests he takes from). A builder with no
chest anywhere he looks isn't crafted for, so nothing made for him is left lying by the crafter's bench (B721).
A builder who has a chest of his own still gets it there, never in the Storehouse, and two chestless builders each get
theirs.
"His Blueprint Table" is the table the build is fed from: a build is served once, through the builder who leads it. A
builder who is only helping on it (a Wonder's crew too) is passed over, and a Wonder's part gets its things in the
stockpile by the Foundation Stone, not in its lead's own house. Before, what was made for such a build was carried to
the helper's (or the Wonder lead's) own chests, where the build never takes from: it stayed on the list and was made
and carried there again and again. What a crafter delivers also comes off the builder's list at once, so it isn't made a
second time before the builder next counts his chests (B1171).
A job is only taken when what it makes fits in those chests: counting a slot the job's own ingredients empty (the last
two stone bricks in a slot leave room for the four slabs cut from them), and leaving out the room another crafter's
job already counts on, so two crafters never both count the same empty slot. With the chests by his table full, it
goes to another store the builder also takes from (another Storehouse in the village) that has room. With no room
anywhere, the crafter waits and looks again later, and the builder's line says so under "Short of": "No room to bring
Chest, Ladder: the chests he takes from are full", never "Nobody here makes" (B1111, B1112). Making some room in his
Storehouse is enough to get things made again. If they fill up while he carries the job to them, he keeps it in his bag and tries again every two
seconds (for up to two minutes, then takes it home), so nothing is dropped by a full Storehouse and nothing is made
twice (B760). He only takes it home if the chests by his own bench have room for it; with none there, or full ones,
he keeps carrying it and trying the Storehouse, so it never ends up on the ground by his bench (B773).
What he took home isn't lost to the builder: while a builder still waits for something that lies in the chests by
the crafter's own bench (and those aren't chests the builder takes from himself), the crafter carries it on to the
builder's chests or the Storehouse as soon as they have room, before he makes any more of it (B778).

When a job could make a part but a raw material is short, nothing is taken and the builder's shortage line says so,
under "Short of": "Can't be made yet: short of 4 Oak Logs to make 3 Barrels" (items named in the plural after a count above one, B426, for items that have a plural in the language file; every wood's logs and stems have one, "short of 2 Crimson Stems", and so do the countable things crafters run short of, such as sticks, nuggets, ingots, clay balls, feathers, diamonds, bricks, eggs and buckets, B920). When no job in the village makes a crafted
part at all (oak stairs in a village with a Mason but no Carpenter), the line names the job to hire: "Hire a
Carpenter to make Oak Stairs" (46.1); only what no job makes says "Nobody here makes".
A Mason's kiln counts its fuel as a raw material too: twelve clay balls and one coal for three brick stairs (a coal
for every eight fired) is "short of 1 Coal to make 3 Brick Stairs", and nothing is taken (B352).
Coal and charcoal count together: one coal and one charcoal are the two fuels twelve clay needs, and the Mason
takes and burns both (B396). The same goes for a Tinkerer's forge: six raw iron for iron bars and no coal is "short
of 1 Coal", not "Nobody here makes: Iron Bars", and the ore stays in the chest (B408).
The line names what the chest is short of, not some other way to make it: a Librarian with two leather where a
bookshelf's books need three says "short of 1 Leather to make 1 Bookshelf", not "4 Rabbit Hide". It goes further down
only when that uses something that's there (planks still to be sawn from the logs at hand read "short of 1 Oak Log")
(B409). Logs of every wood at hand count: an oak and a spruce log for 2 chests read "short of 2 Oak Logs", and
with a birch log too "short of 1 Oak Log" (B1311). A block packed from 9 of one thing names that thing: "short of 27 Wheat to make 3 Hay
Bales", and with 20 wheat the third bale "short of 7 Wheat" (B1305). Nothing is ever made of itself: with Create, a
stone a player mines (andesite, diorite, granite, tuff) is never "short of 3 Andesite to make 3 Andesite"; it is cut
from another form of it only when one is in the chest (B1308). The same holds between two recipes for one thing: a Tinkerer with five raw iron and a coal for iron bars says
"short of 1 Raw Iron", and with coal and no ore at all "short of 6 Raw Iron", never iron ore a player only gets with
Silk Touch; gold and copper read the same (B489). With Create (or Cobblemon) loaded it still names the raw metal a player
mines: never Create's Crushed Raw Iron, which only a crushing wheel makes, nor Cobblemon's plaques melted into nuggets
(B1267); and 4 chains short read "Raw Iron", never Create's Zinc (B1268). Of two ways that fit as well, the one from the
thing's own mod comes first (iron ingots, chains and bread by Minecraft's own recipes); of Create's two ways to its zinc,
the one short of the Raw Zinc a player mines, never the Crushed Raw Zinc (B1279). With nothing there that leads to it, an
ingredient is itself what's short when its only ways are another mod's (a sticky piston names the Slimeball, never Create's
Dough and Wheat Flour, B1277), pack several of one thing into one (a book or bookshelves with no leather at all name
Leather, not Rabbit Hide; with some hide there it still names the rest of the hide, B1278) or need an ore (a piston names
Redstone Dust, not Redstone Ore). Of an ingredient's options the base game's come first: with Chipped loaded a torch's
sticks go down to oak logs or bamboo, never Chipped's decorative planks that no crafting recipe makes (B1273). A thing
whose only recipes unpack a storage form counts as raw: with Carved Wood's log bundles a smoker names 4 of Minecraft's logs, never
Cobblemon's Apricorn Logs through Apricorn Wood (B1288). A stripped block is never raw (a player strips it from its
block), so 3 bamboo planks from nothing read "short of 18 Bamboo", never the Stripped Block of Bamboo (B1304). The line
never goes down through what the thing is being made for: 3 honey blocks read "short of 12 Honey Bottles", not honey
blocks unpacked into bottles and packed back (B1306), and blue wool names its dye and the String for white wool, never
black wool dyed from blue wool (B1303). With Carved Wood's defaults (as in the pack) Minecraft's bookshelf
is made of oak planks only, so Chipped's mosaic planks don't make one and the line names the oak logs (B1290's premise);
Minecraft's chest has no planks recipe there: 8 planks make that wood's Carved Wood chest, and a player turns any one
of those into a plain chest with Carved Wood's special chest_from_modded_chests recipe. The crafters read that special
recipe through a `recipe_stand_ins` data file (below), so the Carpenter makes a builder's plain chest from 8 oak planks
in two crafts (the Oak Chest of Carved Wood, then the chest), turns a carved chest of any wood that's there into one,
and a chest from nothing names the logs (B1302, B1286). A carved trapped chest is never used for it.

The Crafter is gone for new villagers: the Crafter's Workbench can't be crafted, isn't in the creative tab or any trade,
and no villager takes one. A Crafter on an existing world becomes jobless the first moment it's loaded (what it carried
drops at its feet) and soon takes another job. A Crafter's Workbench already placed stays a block. The Crafter's
Workshop (37.16) left with it (37.16a): the Blueprint Table no longer offers it, the Steward no longer plans it and
villages no longer grow it; workshops already built or grown stay as plain buildings.

## 2. How it works

`CrafterWork` is the shared shift of every crafting job (the Carpenter's own, and `MasonWork`, `ScribeWork` for the
Librarian, `TinkererWork`, `ToolsmithWork`, `DyerWork`, `FurnicrafterWork`). With Milestone 37 open (`chains()`, gated by
`Expansions.isOpen("M37")`), each of those:

- plans with `Crafting.chain(level, kind, ...)`: its own recipes (the crafting table's; the stonecutter's, and the
  kiln's with a furnace by it, for the Mason; the blast furnace's for the Tinkerer; CobbleFurnies' Furni Crafter's files
  for the Furnicrafter), `Crafting.CHAIN_DEPTH` (3) steps down, a repeated recipe merged into one step;
- fills a slot that takes any of a tag (planks, logs) from any mix of it, as a crafting table does (B1293): one kind
  when one has enough, else what's there of them all, the best-stocked first, and only the rest made from further down;
  when the crafts' mix differs from one to the next, the recipe is several steps, each saying what one craft takes;
  when no one kind can be made in full, each kind is made as far as it goes and the slot takes the mix (B1301: a
  chest from one oak and one spruce log, 4 planks of each; `Crafting.makeMix`), taking only the makings needed;
- tries the whole count, then half, down to one, so a big order is made a bagful at a time (12 stacks of makings);
- when nothing fits, notes the chain's raw materials short (`Crafting.lacking`: of several recipes for one thing, the
  one closest to what's there, by `Crafting.shortRank`, never one that only unpacks a storage block, B489; on a tie the recipe from the item's own mod, by `Crafting.fromOwnMod`, tried first too, B1267; a plan for real gives up at once on what nothing in the chests leads to, `Crafting.reachable`, so the pack's many recipes don't use up the tick's planning before the line is said, B1268; or the Furnicrafter's own; the Mason's
  kiln and the Tinkerer's forge add their coal, from `Crafting.shortfall`'s fired steps (`CrafterWork.fuelShort`), and
  name it when the clay or ore is all there) for that
  build site and item in `JobShortages`; when it can make it, or another job has claimed it, notes it as covered;
- makes the plan's steps one after another at the workstation (each in its own time), saving the job on the villager
  after every phase change and step (`crafter_job`); fuel a plan takes that no step uses is burnt at the end; a plan's
  last change is its own step too (the Mason's glass fired from sand, the dyer's concrete hardened from the powder it
  mixed); a plan whose steps still don't add up to what it makes is made all at once as before;
- on its next shift after a restart or reload picks the saved job up where it was (the makings already made are in the
  saved bag; the claim on the request is taken again);
- lets the saved job go when its workstation has been gone for 600 ticks (`CrafterWork.benchlessTick`, every villager's
  AI step, B353): the claim released, `crafter_job` dropped and the bag emptied into the asker's chests (at the
  villager's feet when those are gone or full). A bench put back or moved within that time carries the job on. Another
  job's workstation doesn't count (B1009): a novice who loses his bench is made jobless by vanilla and may take a free
  lectern, so the saved job names the profession that took it (`profession`, a registry id; empty in older saves, which
  are stamped with his profession at the first look after loading, jobless too, B1018; his work on it stamps it
  again, so one who was jobless when saved and gets his bench back in time carries on) and is let go all the same once
  he's in another job that doesn't craft (a Scholar or Teacher): the logs go back and another Carpenter takes the
  request. Every crafting job picks up a saved job at its own workstation, so one who became a Librarian finishes the
  barrels and takes them to the builder himself.

The builder's status (`BuilderStatusSync.supplyLines`) adds `JobShortages.line` under "Short of" while it waits:
first up to two chains short of a raw material, else the crafted parts (something a crafting table, stonecutter,
furnace or furniture bench makes) that no job has looked at in the last 600 ticks, once the site has waited that long.
The cook, the luxury makers, the Weaponsmith and the Fletcher keep their old one-plan way. Their status lines name what they make in the plural after a count above one: "making 8 Spectral Arrows" (B916; bows, crossbows, shears and the stone and iron tools and swords have plurals too), and so do the cook's pies, cookies, cakes and stews ("cooking 2 Pumpkin Pies"). His Smoker dishes too: "cooking 4 Cooked Porkchops" and "cooking 8 Bowls of Beetroot Soup" (B931); the meats and fish that read as a mass ("4 Cooked Chicken", "8 Steak") keep their one form.

The retired Crafter: `Crafters.tick` (every villager's AI step, from `VillagerMixin`) finds a villager whose profession is
`aliveworkplace:crafter` and makes it jobless: the workbench's POI ticket let go, the `crafter_job` dropped (and its claim
released), the bag's contents dropped where it stands, trades, level and experience cleared, its brain rebuilt. The
profession can't acquire a workbench any more, and the workbench left the `acquirable_job_site` tag.

**Special recipes as data (B1302).** A mod's special crafting recipe is code with no ingredients to read, so the
planner skips it. A file in `data/<namespace>/recipe_stand_ins/` stands in for one:
`{"serializer": "carved_wood:chest_from_modded_chests", "from": ["#c:chests/wooden"], "except": ["#c:chests/trapped",
"minecraft:chest"], "result": "minecraft:chest"}` adds an in-memory one-slot recipe (any one of `from`, not `except`,
makes the result) to every kind that uses the crafting table's recipes (`RecipeStandIns`, joined in `Crafting.byResult`).
It counts only while a recipe of that serializer id is loaded (the mod installed and its config switch on), and of
`from` only what a crafting recipe makes is kept when anything is (Carved Wood's Pale Oak Chest without pale oak in the
game was what a chest from nothing named). By id only, never the other mod's classes; a datapack can add more. A file
with a typo in an id (a capital letter or a space, as in `"#c:Chests/Wooden"`) is skipped with a "Skipping recipe
stand-in" warning naming it, and the server starts as usual (B1312).

## 3. Switches

| Key | Default | What it does |
|---|---|---|
| (none of its own) | | The chain way follows Milestone 37's gate: before 2.1 every job crafts as it did before. The `crafters` switch (37.14) is gone with the Crafter; an old config file that has it loads fine and the key is ignored. |

## 4. Saved data

- `crafter_job` on the villager (since 37.14, now on every crafting job's villager): the job in hand (who it's for, the
  chain's steps, what it takes, the phase and the next step). None: no job in hand, as on every older save. A job
  that names an item no loaded mod has any more (its target, a step's makings or what it takes; a mod taken out of
  the pack) doesn't load and the villager has no job in hand; the bag still loads whole, and with no job its makings
  go back into the chest by the workstation (`QaCrafterJobRemovedMod1102GameTests`,
  `QaAttachmentRemovedModId1102GameTests`).
- The bag (`builder_bag`) and items made (`items_crafted`) are the ones every crafting job already saves.
- Nothing new is saved: the shortage notes are kept in memory and renewed every look.

## 5. Items, blocks, jobs, commands

- Block: `aliveworkplace:crafters_workbench` stays registered (no recipe, not in the creative tab); job
  `aliveworkplace:crafter` stays registered so old saves load; nobody takes it.
- Blueprint `aliveworkplace:crafters_workshop` and the village pieces `aliveworkplace:village/<style>_crafters_workshop`
  still load by id; not offered, planned or grown.
- Data: `data/<namespace>/crafter_benches/*.json` still read (CobbleFurnies' Furni Crafter's recipe type), used by
  `Crafting.chain(level, target, count, usable, types)`.
- Text: `message.aliveworkplace.job_chain.*` (the step, the lack line, "Can't be made yet", "Nobody here makes").

## 6. Decisions

- 37.14 (owner, 2026-10-20): a new job that makes what others need, through whole chains.
- 37.14a (owner, 2026-10-21): the Crafter is retired and its way of working moved into the jobs that already craft for
  builders, each for its own kind; what no job covers stays unmade and is said on the builder's line.
- 37.14a: the chain way is behind Milestone 37's gate like the rest of 2.1, so the live server's jobs don't change
  before the release; the Crafter's retirement isn't gated (the Crafter never shipped outside the gate; a stray one on a
  test world is made jobless whatever the gate says).
- 37.14a: the job is saved in the attachment the Crafter used (`crafter_job`), so no new saved field; a villager
  without it has no job in hand.
- 37.14a: "nobody here makes" waits 600 ticks after the builder starts waiting (the jobs look every 5 seconds), and
  only names crafted parts, so a missing log is left to the "Short of" line above.
- 37.14a: the other-workers requests the Crafter answered (a miner's ladders) aren't moved into the jobs; a question
  for the owner is in the ROADMAP notes.
- 37.16a: the Crafter's Workshop rule (`workplace_crafters_workshop`) is deleted; its condition
  `builders_short_of_parts` stays (a pack's rule may name it) and never holds, saying why.

- B778 (lane-a-1101-1823): what a job took home after the long wait (B773) is carried on to the builder once his
  chests or the Storehouse have room, as a job with nothing to make (`CrafterWork.carryOn`: up to what he still
  misses, from the chests by the crafter's own bench that the builder doesn't already take from). Chosen over the two
  other ways: carrying it for good (B773's no-room branch) would keep the crafter from all other work for as long as
  the Storehouse stays full, and letting builders take from a crafter's chests (`Village.takesPart`) would change
  every place that list is used (the job board, the hauler golems, enchanting, workplace rules) and let builders take
  the crafter's own makings. Anything of the asked item in those chests is carried on, whoever put it there: they are
  the crafter's, and nothing else takes from them.
- B1170 (lane-a-1111-0322): a crafting job also counts the makings in the chests by his own workstation for a
  builder's request (`CrafterWork.ownMakings`, pooled with the builder's chests and the village stores, taken like a
  stash's), so a Mason's stone or a Librarian's books aren't left lying while the builder waits "short of" them. Chosen
  over adding those jobs to `Village.takesPart` (as the Cleric, B1144, and the Glassblower, B1156, were), which would
  also keep their chunks loaded, send their wants to the job board, let builders and B721's deliveries use their chests
  and change the Tinkerer's golem mending. Chests already counted (the builder's, a village-mate's such as the
  Glassblower's, another builder's) aren't counted twice; a builder on another side (`Village.sharesWith`) gets none.
  The Carpenter stays out, for B778's reasons above: what he took home is carried on, not made into anything else.
  "By his own workstation" means within 8 blocks of it: a chest 8 blocks away counts, one 9 blocks away is left
  alone, and another crafter's chest is never raided (QaCrafterOwnChest1111cGameTests, 18, with the short lines' exact
  numbers and reloads mid-make and mid-carry).
- B1171 (lane-a-1111-0322): a crafter goes by the bench that feeds the build (`CrafterWork.supplyBench`:
  `Builders.siteBench` for its lead, as `BuilderWork` fetches and counts), not by the table of whichever builder it
  looked at, and skips helpers (`Builders.isHelping`), so each build is served once, where it takes from. Chosen over
  serving a helper at the lead's bench too: his claim ("his uuid|item") would let two crafters make the same request.
  A delivery lowers `BuildSite.missing` at once (`CrafterWork.offTheList`, as `Requests.lower` does for a player's
  hand-over, B344); the builder's next count (every 100 ticks) replaces it. A crafter near a helper's table but more
  than a village's reach from the lead's no longer serves that build.

## 7. Known limits

- What's short is found along the first way down the recipes: with oak and spruce logs both short it names oak.
- A job only looks at the parts of its own kind, so a part two jobs could make in turns (a lantern from a Tinkerer's
  ingots and a Carpenter's torch) is made only if one job's own recipes go all the way down.
- The showcase scene is filmed on GitHub, not in this session.

## 8. Proof

- `CarpenterOwnChestB1151GameTests` (the `carpenter` scene's village: a Market Stall, the builder's own chests, a
  Carpenter at a crafting table): all the stall's woodwork made once from logs and the stall built, the same reloaded
  mid-delivery, the logs a builder carries not held back in his chest too (B1151), and the stall's own logs never sawn
  while the line names the spruce logs short.
- `JobChainGameTests.theCarpenterMakesBarrelsFromLogsInThreeSteps`: the Carpenter's three-step chain, its "step 2 of 3"
  line, and the build finishing.
- `JobChainGameTests.theMasonFiresLaysAndCutsBrickStairsFromClay`: a Mason's kiln chain, the fuel burnt.
- `JobChainGameTests.aShortRawMaterialIsNamedOnTheBuildersLineAndNothingIsTaken`: a raw material short, said on the
  builder's line, nothing taken.
- `QaLaneD1104GameTests.qaB426TheRealLackReadsOneAndTwoLogsRight` (QA, B426): from the real lack, "short of 1 Oak Log
  to make 4 Oak Planks", "short of 2 Oak Logs to make 8 Oak Planks" and "short of 2 Oak Logs to make 1 Barrel"
  (and since B920's fix two jungle, cherry, crimson or warped logs read in the plural too:
  `QaLaneD1104GameTests.qaB426EveryWoodsLogsAreNamedInThePlural`).
- `QaLaneC1104eGameTests` (QA, B878/B920/B931): 1 Stick, Egg or Cherry Log reads in the singular and 0, 2, 8 and 64
  in the plural; "short of 2 Sticks, 1 Copper Ingot to make 1 Iron Pickaxe" and "short of 1 Crimson Stem to make 4
  Crimson Planks"; every `plural.aliveworkplace.*` key in the language file names a real item or creature, isn't empty
  and differs from the one-form (but for names already plural, "Fine Clothes", "Drowned"); every countable input and
  output of the mod's luxury recipes has a plural.
- `QaCrafterFurnitureBugTests.b352AMasonShortOfCoalSaysSoAndTakesNothing`: the kiln a coal short names the coal, not
  the bricks, and takes nothing (B352).
- `QaJobChainBugGameTests.aTinkererWithOreButNoCoalNamesTheCoalAndTakesNothing`: the forge with the raw iron but no
  coal names the coal and takes nothing (B408).
- `QaTinkererShortGameTests` (5, QA from B408): the line reads exactly "Can't be made yet: short of 1 Coal to make 3
  Iron Bars" the whole minute and never "Nobody here makes"; coal and no ore names the iron; neither names both; six raw
  iron on one charcoal make the bars; with no Tinkerer it is "Nobody here makes: Iron Bars" and the ore stays.
- `QaTinkererShortBugGameTests` (4, B489): five raw iron and a coal read "short of 1 Raw Iron to make 3 Iron Bars",
  coal alone "short of 6 Raw Iron", one raw gold for pressure plates "short of 5 Raw Gold", all taking nothing; read
  off the forge's recipes, five raw copper for a copper block lack 4 raw copper, nine nuggets from nothing 1 raw iron,
  and a raw iron block is unpacked rather than named.
- `QaCreateForge1115eBugCompatTests` (2, B1267, the compat suite with Create): read off the forge's recipes, 16 iron
  bars from nothing lack 6 Raw Iron (from 5 raw iron, 1), 9 nuggets 1 raw iron, a copper block 9 raw copper, a gold
  pressure plate 2 raw gold, a lantern names the raw iron; the builder's line reads exactly "Can't be made yet: short of
  6 Raw Iron to make 16 Iron Bars" and the Tinkerer takes nothing.
- `QaCreateChain1115eBugCompatTests` (B1268, with Create): 4 chains short with a coal and no iron name the Raw Iron,
  never Zinc or a Crushed ore, and nothing is taken.
- `QaCreateRivals1115e2CompatTests` (4, B1267/B1268's fix with Create): bread at the kitchen names wheat (never Wheat
  Flour), a book, an item frame and chains at the table or forge name no Create item; Crushed Raw Iron or zinc ingots
  and nuggets the chests hold are used (nothing short, and the forge has a plan), and from nothing there is no plan;
  3 zinc ingots from 1 raw zinc lack 2 Raw Zinc.
- `ShortLineGatheredGameTests` (5, B1278, B1277): with no leather a book and an item frame lack 1 Leather and a Librarian's
  three bookshelves read "short of 9 Leather"; with 2 rabbit hides a book lacks the 2 more hides; leather asked for itself
  still lacks 4 rabbit hide; a piston or sticky piston from nothing names Redstone Dust and no ore.
- `QaCreateRivals1115cBugCompatTests` (4, B1277/B1278 with Create): every tinkering item from nothing names something and
  no Create or Cobblemon item; a sticky piston's line names the Slimeball, never Wheat Flour or Dough; a book, an item frame
  and bookshelves with no leather name Leather.
- `QaCreateZinc1115e2BugCompatTests` (2, B1279 with Create): 3 zinc ingots from nothing lack 3 Raw Zinc and a zinc block 9;
  a builder short of 2 Blocks of Zinc reads Raw Zinc, never Crushed, and the Tinkerer takes nothing.
- `MixedTagSlotGameTests` (5, B1293): a chest from 4 oak and 4 spruce planks and a smoker from four woods' logs are
  planned taking exactly those, every step's makings in hand when it comes; 3 crafting tables from 6 oak and 6 spruce
  planks use every plank; one oak and one spruce log lack 2 logs of a smoker's 4, 3 oak and 2 spruce planks 3 of a
  chest's 8; a Carpenter makes the 3 tables from a chest of mixed planks, the row is built and no plank is left.
- `QaTagMix1115hGameTests` (9, the QA lane, past B1293): a chest from 3 oak, 3 spruce and 2 birch planks takes exactly
  those 8 (never the cobblestone, stone or sticks by them), from crimson, warped and cherry planks too, and a crafting
  table from one plank of each of four woods; 8 oak by 3 spruce and 2 birch take the oak alone; 7 planks of three woods
  are no plan and lack 1 plank of one wood, 8 are a chest; 16 mixed planks are two chests and lack 8 for a third. A
  builder short of a chest with 3 oak, 3 spruce and 2 birch planks, 2 oak logs, cobblestone and sticks in his chest gets
  it from the Carpenter, every plank used and the logs, cobblestone and sticks untouched; one plank short, nothing is
  taken and his line reads "Can't be made yet: short of 1 … Planks to make 1 Chest", naming oak, spruce or birch
  (a wood that's there, never 5 of one), then one
  more birch plank and it's made; the Carpenter saved and loaded back mid-craft still makes it once, no plank lost.
- `QaLogsSlot1115e3BugGameTests` (2, B1293): four logs of four woods, or 2 stripped oak logs and 2 dark oak wood, make a
  smoker with nothing short; a chest from 4 oak and 4 spruce planks is planned. `QaLogsSlot1115e3GameTests` (3): a
  smoker from 8 cobblestone, or a furnace, lacks exactly 4 logs of one base-game wood; with 2 oak logs the other 2.
- `QaMixedTagSlot1115cGameTests` (8, QA past B1293): a chest from 3 oak, 3 spruce and 2 birch planks takes exactly those,
  one fewer reads "short of 1 … Planks to make 1 Chest"; a wooden pickaxe's head and sticks share 5 mixed planks (4
  are 1 short); an oak log tops up 2 oak and 2 spruce planks into a chest; oak stairs never take spruce planks (3 oak
  and 6 spruce lack 3 Oak Planks); 10 chests use 80 planks of four woods; a Carpenter makes 3 tables from 5 oak, 4
  spruce and 3 birch planks and leaves the chest's logs and cobblestone alone; one short of oak planks for oak stairs
  names them and takes no spruce; `aChestFromTwoWoodsLogs` (B1301): one oak and one spruce log are a chest, nothing short.
- `MixedLogsSlotGameTests` (3, B1301): 2 chests from 2 oak, 1 spruce and 1 birch log take all four logs; a chest from
  three woods' logs takes two, and 3 oak planks are topped up from two logs; one log alone is still short; a Carpenter
  makes the builder's row of 3 chests from 3 oak, 3 spruce and 1 birch log, leaving one log and no plank.
- `QaCrafterLogs1116cGameTests` (6, QA past B1301): one log each of oak, spruce, birch and jungle are exactly 2 chests;
  a crimson stem with a stripped birch log, or a bamboo block with an oak log and 2 spruce planks, make a chest; logs top
  up loose planks of other woods and no plank beyond the 8 is used; an oak and a spruce log are never 2 chests; a barrel
  and a wooden pickaxe from mixed logs; a Carpenter makes a row of 3 chests from planks and two woods' logs across a save
  and load, and short of logs loses no wood and names logs.
- `QaStorageBlockLine1115bBugTests` (3, B1305, QA): hay bales, a slime block and a dried kelp block from nothing lack
  the 27 wheat, 9 slimeballs and 9 dried kelp; a builder with 20 wheat and a Carpenter gets 2 bales and reads "short of
  7 Wheat to make 1 Hay Bale"; from an empty chest "short of 27 Wheat to make 3 Hay Bales".
- `QaMinedStoneSelf1115bBugCompatTests` (2, B1308, QA, with Create): no mined stone's line names the stone itself; a
  builder short of andesite with a Mason never reads "Andesite to make".
- `QaCrafterLogs1116cBugGameTests` (1, B1311, QA): truly short, an oak and a spruce log for 2 chests lack 2 logs and
  read "short of 2 … Logs to make 2 Chests"; with a birch log too, 1 log.
- `QaMixedLogs1116bGameTests` (7, B1301, QA): a chest from a crimson and a warped stem, from oak wood and a stripped
  spruce log, from an oak log and 2 bamboo blocks (2 planks each; one block is short); two chests from one log of each
  of four woods (a third is short); an oak log and 3 spruce planks lack exactly one log or plank, with 4 they are a chest;
  a barrel (planks and slabs) from three woods' logs; the Carpenter's job from four woods' logs saved and loaded mid-way
  builds the row of 3 chests, one log left, no plank.
- `ChippedShortLineCompatTests` (3, B1273 with Chipped): a lantern, torch, sticks, piston or chest from nothing names no
  Chipped item; a builder short of a lantern reads the Raw Iron and no Chipped planks, and nothing is taken; with Carved
  Wood a bookshelf from 6 mosaic planks and 3 books lacks 2 Oak Logs, from oak planks nothing, and a smoker from a
  furnace lacks 4 of one of Minecraft's logs (B1288, B1290); a chest from nothing names logs too (B1286).
- `RecipeStandInsCompatTests` (4, B1302, B1286 with Carved Wood): the shipped stand-in is read and is the one way to a
  plain chest; a chest is planned from 8 oak planks as Carved Wood's Oak Chest then the chest (7 planks: none); two
  carved spruce chests make two in one step, a carved or vanilla trapped chest none; a chest from nothing lacks logs.
- `QaRecipeStandInFiles1116eGameTests` (4, base game, a datapack's files through the real loader): a stand-in counts
  only while its serializer's recipe is loaded and is gone after a reload without it; without Carved Wood the shipped
  chest file adds nothing; `except` taking out all, a result from itself, and from only uncrafted things (both kept) or
  a tag of logs (only the crafted woods kept); broken files (no serializer or result, empty `from`, a bad result id,
  not JSON, an array) are skipped and the rest load. A typo in a `from` or `except` entry skips that file with the
  warning, and the server starts (B1312: `QaRecipeStandInTypos1116eBugTests`, 3).
- `QaCarvedChest1115eCompatTests` (1) and `QaCarvedChest1115eBugCompatTests` (2, B1302): a one-block blueprint with a
  Carpenter hired gets its plain barrel from 3 spruce logs, and its plain chest from 8 oak planks or from a carved oak
  chest in the builder's chest.
- `QaCarvedChest1116cCompatTests` (7, QA past B1302 and B1286 with Carved Wood): the block built is exactly Minecraft's
  chest, from 2 oak logs, from 1 oak and 2 spruce logs (the spruce used, the oak left), or from a carved spruce chest
  (the 8 planks kept); trapped chests (Minecraft's and Carved Wood's) are never used and the line names 2 logs, never Pale
  Oak; planks taken away mid-job and given back make the chest losing nothing; a Carpenter saved and loaded mid-job.
- `QaCarvedChest1116bCompatTests` (4, B1302, QA, with Carved Wood): a plain chest from 2 oak logs and a Carpenter is
  built (planks, Oak Chest, chest; 1 log: none); 16 oak planks are two chests, 15 one; a carved oak chest and 8 spruce
  planks are two; a carved trapped oak chest is never used and the line reads "short of 2 Oak Logs to make 1 Chest"; a
  blueprint asking for the carved Oak Chest is never built with a plain chest.
- `QaShortageGathers1115fBugCompatTests` (2, B1288): a smoker from nothing or from a furnace names 4 of Minecraft's logs,
  and a builder short of a smoker reads Minecraft's logs, never Apricorn Logs.
- `QaShortLineSweep1115e3CompatTests` (1, B489/B1267/B1277/B1279 with the whole compat pack): every item a job makes
  (about 3,200), read from an empty chest the way that job's shortage line reads it, names no ore (`c:ores`) and no
  Create crushed raw metal. Kills a mutant dropping the ore rule and one dropping B1279's raw-metal rank.
- `QaLogsSlot1115e3GameTests` (3): a smoker from 8 cobblestone, or from a furnace, lacks exactly 4 logs of one of the
  base game's woods (never 8 through wood); with 2 oak logs there the other 2, with 1 birch log 3 more. Mixed woods in one
  slot are B1293's.
- `QaShortageGathers1115fGameTests` (8, QA past B1273/B1277/B1278/B1279): a book from 3 paper lacks 1 Leather, with 1
  rabbit hide the 3 more hides, with 4 hides or 1 leather nothing; three bookshelves lack 9 Leather, 8 with one leather,
  35 Rabbit Hides with one hide; two item frames, a leather cap and a book and quill name Leather; a sticky piston and a
  lead name the Slimeball (never a Slime Block), and a slimeball or slime block there is used; a block of iron, copper or
  gold names 9 of its raw metal and no redstone or metal thing names an ore; a smoker names its 4 logs and a campfire its
  3; a Librarian's builder reads exactly "short of 35 Rabbit Hides to make 3 Bookshelves" and a Tinkerer's "short of 3
  Slimeballs to make 3 Sticky Pistons", the same after the villagers are saved and loaded, and nothing is taken.
- `QaShortageGathers1115fCompatTests` (8, the same with the pack): with Create, Minecraft's metal blocks, ingots and a
  bucket name the raw metal, never Crushed; zinc lacks 9 Raw Zinc (8 with one there), 9 nuggets 1, and crushed raw zinc
  there is used; a sticky piston, a lead, a magma cream and a slime block name the Slimeball, never Dough or Wheat Flour,
  while dough and a lime dye there may be used; leather and hide read as in the plain game; sticks, ladders, a crafting
  table, a barrel, a bowl, a sign, an item frame, a painting, a torch, a wooden pickaxe, a tripwire hook and a lever name
  no Chipped, Create or Cobblemon item nor an ore, and Chipped's planks or an oak log there make the sticks; every
  Minecraft block a Minecraft crafting recipe makes (579), asked one a tick, names only Minecraft's things (the
  smoker too, since B1288's fix); a builder short of a sticky piston reads exactly "short of 1 Slimeball to make 1 Sticky Piston", and of
  a copper block "short of 9 Raw Copper", never Crushed.
- `QaUnpackedLogs1115bCompatTests` (2, QA past B1288 with the pack): a smoker from a furnace and 3 oak logs lacks 1 of
  Minecraft's logs, with 4 nothing, with 4 Apricorn Logs (they are in `#logs`) nothing, and with 4 of Carved Wood's oak
  log bundles nothing (4 logs pack into 4 bundles, each unpacks into 1 log); a campfire from 3 sticks and a coal lacks 3
  of Minecraft's logs; a builder with a furnace and 4 bundles in his chest and a Carpenter at his bench gets his smoker,
  with no shortage line on the way.
- `QaStoneLine1115bGameTests` (2, QA): polished andesite, diorite and granite, an andesite slab, polished andesite stairs
  and a diorite wall from nothing, by the stonecutter's plan, name the mined stone and never Nether Quartz; a builder
  short of 3 Polished Andesite with a Mason reads a line naming Andesite (never Quartz or Cobblestone), and nothing is made.
- `QaDyedWoolLine1115bBugTests` (2, B1303, QA): every dyed wool from nothing names no dyed wool, and 2 blue wool with
  2 blue dye there lack only the wool's makings; a builder short of 2 Blue Wool with 2 Blue Dye and a Shepherd reads a line
  naming White Wool or String, never Blue Wool, Black Dye or Lapis, and the dye isn't taken.
- `QaBambooLine1115bBugTests` (2, B1304, QA): bamboo planks, slabs, stairs, mosaic and fences from nothing name the Bamboo
  (or a Block of Bamboo), never the Stripped Block of Bamboo; 9 bamboo or a block of bamboo make 2 planks; a builder short
  of 3 Bamboo Planks reads a line naming Bamboo and nothing is made.
- `QaHoneyBlockLine1115bBugTests` (2, B1306, QA): 3 honey blocks from nothing lack exactly 12 Honey Bottles, from 12
  bottles nothing; a builder short of 3 honey blocks reads exactly "short of 12 Honey Bottles to make 3 Honey Blocks".
- `QaShortPlurals1115bBugTests` (3, B1307, QA): the 24 countable things (snowballs, torchflowers, the chiseled blocks'
  slabs, weathered copper doors and trapdoors, disc fragments, goat horns...) read their plural after 4; "short of 12
  Snowballs to make 3 Snow Blocks", "short of 3 Vines to make 3 Mossy Cobblestone", also through a builder's line.
- `QaShortageLineVerify1116bGameTests` (10, QA past B1303/B1304/B1306/B1307): each of the 16 colors' wool and carpet from
  nothing names String or White Wool and no dyed wool or carpet; each dyed wool with its own dye there lacks only String or
  White Wool, and with white wool (or 8 string) and its dye nothing; each carpet from its own wool nothing; every wood's
  planks from nothing name exactly its log or stem (1 for 4, 2 for 8), never the stripped one, and its stripped log, wood
  or log there makes them; 3 bamboo planks lack exactly 18 Bamboo, and a Stripped Block of Bamboo or a Block of Bamboo there
  makes 2; a honey block from nothing lacks 4 Honey Bottles, 2 from 5 bottles 3, and 4 bottles from a honey block and
  glass bottles nothing; an iron block from nothing never names the block or nuggets, and 9 ingots and a block turn into
  each other; B1307's things read singular at 1 and plural above ("short of 1 Snowball to make 1 Snow Block", "short of 2
  Torchflowers to make 2 Orange Dye"); everything a dyed wool, carpet or dye line names more than one of reads its plural
  (bar String, Lapis Lazuli, Bone Meal, Bamboo, Cocoa Beans, dye and wool); a builder short of 3 Blue Carpets with a
  Shepherd reads a line naming String or White Wool and no other color, one short of 3 Mangrove Planks with a Carpenter
  reads exactly "short of 1 Mangrove Log to make 3 Mangrove Planks", and a builder with 2 Stripped Blocks of Bamboo gets
  his 3 bamboo planks from the Carpenter.
- Open bug B1305 (its 3 failing tests, QaStorageBlockLine1115bBugTests, are on the tests/storage-block-line-1115b
  branch): a builder 7 wheat short of his last hay bale, or with no wheat at all, gets no shortage line; a hay bale, a
  slime block and a dried kelp block from nothing lack nothing.
- Open bug B1308 (its 2 failing compat tests, QaMinedStoneSelf1115bBugCompatTests, are on the
  tests/mined-stone-self-1115b branch): with Create a builder short of 3 Andesite and a Mason reads "short of 3 Andesite
  to make 3 Andesite" (Create's stonecutting recipes make the mined stones from a tag that holds them).
- Open bug B1311 (its failing test, QaCrafterLogs1116cBugGameTests, is on the tests/crafter-logs-1116c branch): with
  one oak and one spruce log for 2 chests the line reads "short of 3 Oak Logs", counting only the first wood (2 is right).
- `QaJobChainBugGameTests.aLibrarianOneLeatherShortOfTheLastShelfTakesNothingAndNamesIt`: two leather for the last
  bookshelf's three books names "1 Leather", not rabbit hide, and takes nothing (B409).
- `QaJobChainFixesBugTests.aMasonWithOneCoalAndOneCharcoalMakesTheStairs`: one coal and one charcoal make the
  stairs (B396).
- `MasonMixedFuelGameTests.aMasonWithTwoCharcoalMakesTheStairs`, `aMasonWithOneCoalForTwelveClayIsShortOfOneCoal`,
  `aMixedFuelJobReloadedMidWayFinishes`: charcoal alone, a real shortage counted from both kinds, and a mixed-fuel job
  saved and loaded mid-way (B396).
- `QaMixedFuelGameTests` (QA): a mix takes only the fuel it needs, coal first (one coal and five charcoal for twelve
  clay leave four charcoal), a spare coal stays, no fuel at all reads "short of 2 Coal", glass fired on charcoal (from sand
  or crushed cobblestone), glass with no fuel names "1 Coal", and a Tinkerer's forge burning two coal and a charcoal for
  three cauldrons (B396).
- `QaCrafterFurnitureBugTests.b353TheCarpentersBenchBrokenMidChainLosesNothing`: the bench broken at step 2 and not
  put back: the job let go, the wood back in the builder's chest (B353).
- `QaJobChainLetGoGameTests.aBenchBrokenBeforeAnythingWasCarriedLetsTheJobGoCleanly`: the bench broken while the
  Carpenter is still fetching, empty-handed: let go after half a minute, nothing taken, and the bench put back finishes
  the build (B353).
- `QaCrafterNewJob1107BugTests.qaANewCarpenterWhoTakesAnotherJobLetsTheRequestAndTheWoodGo`: a 0-XP Carpenter carrying
  the builder's logs loses his bench and may take a free lectern: the job and its claim let go, the bag emptied (B1009).
- `QaBagNewJob1107cGameTests` (5, B1007-B1009 QA): a novice Weaponsmith with your sword made a Librarian (after a
  reload) or a Scholar the moment vanilla takes his job: one sword and the 10 iron in a chest or on the ground; a novice
  Carpenter with the builder's logs made a Scholar after a reload: the job let go, a second Carpenter takes the request
  before the claim would run out, the barrels made once; made a Librarian: the barrels still made once, no log left in a
  bag; a job from an older save (no `profession`) with no lectern near: still let go, nothing lost.
- `QaBagNewJob1107cBugTests.qaACarpenterFromAnOldSaveWhoBecomesAScholarFreesTheRequest1107c`: the same with the job
  loaded from a save made before B1009 (no profession in it): he becomes a Scholar and still lets the request and the
  logs go (B1018).
- `QaOldSaveBench1107eGameTests` (3, B1018 and B353 QA): a novice Carpenter whose job was loaded from a save made
  before B1009 finishes the builder's three barrels himself when his bench stands (the job stamped
  `aliveworkplace:carpenter`); one saved while jobless (the bench broken first, so the job is stamped `minecraft:none`)
  whose bench is put back 560 ticks after the load carries on with the job and makes the barrels once, as from a newer
  save: his work on the job stamps it again with the job he holds then.
- `QaOldSaveCrafter1107cGameTests` (8, QA, B1018): an old save's job on a jobless novice, a novice, an experienced Carpenter
  with his bench, and after another reload carries on when the bench is put back (early, late, or moved); a new save's
  job the same, as controls.
- `JobChainGameTests.whatNoJobMakesIsNamedOnTheBuildersLine`: "Nobody here makes".
- `JobChainGameTests.aChainHalfMadeCarriesOnAfterAReload`: save and reload mid-chain.
- `QaStewardMaterials1031GameTests.aCarpenterNeverCraftsAwayLogsAnotherOpenBuildNeeds`: two builds sharing a chest,
  the logs there all B's: the Carpenter leaves them, and B finishes (B696);
  `aCarpenterStillCraftsFromALogNoOpenBuildNeeds`: one log more than B needs is crafted into A's planks, and both
  finish.
- `QaCarpenterReserve1031GameTests.aCarpenterLeavesTheStorehouseLogsAnotherBuildNeeds`: the same with B's logs in the
  village Storehouse, out of both benches' reach: the Carpenter leaves them and B finishes (B696);
  `aCarpenterStillCraftsTheStorehousesSpareLog`: one log more there is crafted and brought to A's chest, and both finish.
- `QaCarpenterChestlessBug1031Tests.aCarpentersPlanksForABuilderWithNoChestReachHim`: the same spare log for a builder
  with no chest by his table: the planks go into the Storehouse, never onto the ground, and his row is built (B721).
- `QaCarpenterStorehouse1101GameTests`: the same case counted at every tick, a log as 4 planks, with nothing lost or
  made twice (`aChestlessBuildersPlanksAddUpExactlyThroughTheStorehouse`); a builder with his own chest gets them in it,
  never in the Storehouse (`aBuilderWithHisOwnChestStillGetsThePlanksInIt`); two chestless builders both build
  (`twoChestlessBuildersBothGetTheirPlanks`); the Storehouse chest broken mid-delivery loses no plank
  (`theStorehouseChestBrokenMidDeliveryLosesNoPlanks`); no Storehouse and only the Carpenter's own chest leaves nothing
  lying (`noStorehouseAndNoChestMeansNoPlanksLeftLying`); a save and reload on the way still delivers to the Storehouse,
  once (`reloadedOnTheWayToTheStorehouseDeliversOnce`) (B721).
- `QaCarpenterFullStorehouseBug1101Tests.aFullStorehouseNeverLeavesThePlanksOnTheGround`: with the Storehouse full, no
  log is crafted and no plank lies on the ground (B760); `CarpenterFullStorehouseB760GameTests`:
  `aFullStorehouseThatFreesUpGetsThePlanksThen` (one log used once there's room, the row built) and
  `aStorehouseFilledOnTheWayIsWaitedFor` (held in the bag, put in once room comes back).
- `QaStalledCrafting1109bBugTests`: a builder by a full Storehouse gets his stone brick slabs cut (the last bricks
  free their slot) and the line names what has no room ("No room to bring Chest, Ladder, Spruce Door"), never "Nobody
  here makes" (B1111, B1112); `QaStalledCrafting1109bGameTests`: with room the Mason and the Carpenter make the slabs,
  chest, ladder and spruce door, also after a save and reload; `CraftersFullStoreGameTests`: the freed slot counts once
  (not twice for two crafters, not for part of a stack), the full line's text, and slabs cut into a second Storehouse
  with room when the one by the table is full. QA's `QaBuilderFixesVerify1110GameTests` (5 here): a full Storehouse
  holding exactly one batch's ingredients still gets a chest, ladder and birch door (Carpenter), a blue bed and a green
  banner (Shepherd) and stone brick slabs from 2 stone (Mason); with no free slot the line reads exactly "No room to
  bring Stone Brick Slab: the chests he takes from are full" (and for a chest), nothing is made or dropped, and one slot
  emptied gets them made and built.
- `QaVillageEconomySaves1101BugTests.aStorehouseThatStaysFullAfterHeSetOffLeavesNoPlanksLying`: a Storehouse that fills
  while he carries the planks and stays full never leaves them on the ground, 3200 ticks on (B773).
- `QaCarpenterFullStore1101cBugTests.planksTakenHomeStillReachTheBuilderWhoAsked`: planks taken home after the long
  wait reach the chestless builder once a Storehouse slot frees, none on the ground and the wood adding up (B778);
  `QaCarpenterHomePlanks1101GameTests.reloadedWhileCarryingHomePlanksOnStillFeedsTheBuilder`: the same with a save and
  reload of the Carpenter on his way, the row built and no job left saved.
- `CraftersDeliverOnceB1171GameTests` (written while Gradle was down, first run on CI):
  `stairsAndSlabsAreMadeOnceAndReachTheBuilder` (4 oak stairs and 4 stone brick slabs with a Carpenter, a Mason and a
  Porter and makings for many more: made once, built, no second batch in the 500 ticks after),
  `nothingIsMadeIntoAHelpersHouseTheBuildNeverTakesFrom` (another player's builder helping, planks in his own chest:
  nothing made, his planks untouched), `reloadedOnTheWayTheStairsStillArriveOnce` (the Carpenter saved and loaded as he
  sets off with the stairs) (B1171).
- `JobChainGameTests.anOldSavesCrafterLoadsJobless`: an old save's Crafter loads clean and jobless.
- `JobChainGameTests.theCarpenterMakesTheSlabsTheBuilderNeeds`, `theCarpenterFetchesTheOneLogItNeeds`,
  `oneCarpenterServesTwoBuilders`: one step, exactly one log, two askers.
- `JobChainGameTests.theLackLineNamesEveryShortThingWithACommaBetween`, `beingShortOfJustOneThingIsStillNamed`: the
  lack line.
- `JobChainGameTests.aBenchsRecipesOnlyWhileTheBenchIsThere`, `theCobbleFurniesBenchIsIgnoredWithoutCobbleFurnies`: the
  bench files.
- `CrafterWorkshopGameTests.theWorkshopIsNoLongerOfferedButStillLoads`, `theStewardNoLongerPlansAWorkshop`,
  `villagesNoLongerGrowTheWorkshop`, `aSiteAtTheVillagesEdgeCountsOneBeyondItDoesnt`: 37.16a.
- `CrafterFurnitureCompatTests.theCarpenterMakesHandcraftedChairsFromLogsForTheBuilder`: a chair from logs through the
  whole chain, step by step (compat suite).
- `FurnicrafterCompatTests.theFurnicrafterMakesAWaitingBuildersPieceFromLogs`: a Furnicrafter piece for a waiting
  builder, planks first (compat suite).
- `CrafterFurnitureCompatTests.theFurniCraftersRecipesOnlyWhileItStandsInTheWorkshop`,
  `aBenchFileForAModThatIsntInstalledIsIgnored`: the bench files with the pack (compat suite).

Showcase scenes: `job_chains`, `crafter_furniture`
