# Wonders

A Wonder is a village's life's work: a huge build raised in four stages by every builder in the village at once, fed by
goods from every job, drawn up by the Master Architect and needing a second Legend. Each of the eight Wonders changes
what its village or its realm can do while it stands. Part of 2.0 (Wonders): the data files, requirements and caps, the
Wonders page, the Wonder Plan and the first Wonders' designs (the Great Cathedral, the Lighthouse, the Great Library, the Observatory, the Great Wall, the Hanging Gardens, the Colossus and, with Cobblemon, the Stadium) are built, behind the 2.0 switch (see
Known limits); the full plan is the design note, docs/design/M35.md.

Roadmap items: 35.1, 35.2, 35.3, 35.9, 35.11, 35.13, 35.15, 35.17, 35.19, 35.21, 35.23

## What a player sees

**The Wonders page** (35.3): a Wonders tab on the Village Hall (a spare tab, so the tabs kept for add-ons stay free).
Each Wonder on the server's list is a card: its icon, name and power, then "Needs:" with each requirement ticked (✔) or
crossed (✘) with the village's progress ("Finished Chapel: 2 of 3", "Standing in for the Master Architect: …"), the cap
("Wonders: 0 of 1 for a Town") and what a click does. A Wonder going up here shows its stage and how far along it is
("Going up: stage 2 of 3, The Upper Storey (33% along)"), a finished one its day.

**Draw up the plans**: with every line ticked and the caps clear, a click on the card pays 32 emeralds (the realm's
treasury if the village is in a realm and realms are on, else the hall's treasury, else the player's own emeralds; free
in creative when neither treasury has them) and gives a **Wonder Plan**, a rolled blueprint with a gold seal. Only the
hall's owner, their friends and operators may. Before that, a click says what's missing and takes nothing.

**The Wonder Plan** holds the whole Wonder's blueprint:

- held and placed, it shows the whole Wonder as see-through blocks, like any blueprint;
- right-click the ground to place it (its front faces you), sneak-right-click the ground to turn it, sneak-right-click
  the air to pick it up again or, not placed, open the style screen, where it can only be mirrored (each Wonder keeps
  its own blocks);
- it goes down only with its centre within the hall's reach (64 blocks), every corner within 96 blocks of the hall, and
  not over a finished building or another Wonder; otherwise it says why ("It would stand over the finished Chapel");
- a Book and Quill in the other hand writes the whole Wonder's material list.

Handing the placed plan to a builder (the Foundation Stone) comes with 35.4.

**What next?** names the Wonder closest to ready once the village is a Town with room for one: "The Great Cathedral can
be begun", or the first line it lacks ("The Great Cathedral is nearly in reach: Finished Chapel: 2 of 3").

Still to come (the design note): the Foundation Stone handed to a builder; a crew of up to 12 builders on 8 × 8 columns,
with scaffolding where they can't reach; goods from every job, dedicated at the stone stage by stage; and a finished
Wonder announced to the server, celebrated with a festival, and its powers on while at least 90% of it stands.

| Wonder | Rank | Legend (stand-in) | Other requirements | Powers |
|---|---|---|---|---|
| Great Cathedral | Town | the Seer (a Master Cleric) | 3 finished Chapels | Undertakers revive anywhere in the village for free; blessed weddings; beauty |
| Lighthouse | Town | the Merchant Prince (3 caravan routes) | on the coast | caravans reach twice as far in half the time; ferry fares halved; raid warnings |
| Great Library | Town | the Old Sage (a Master Scholar) | 20 research levels | a second research tier |
| Great Wall | Town | the Golem Smith (4 iron golems and a Master Tinkerer) | a finished Gatehouse | raids gather outside it; golem sentries on the walk |
| Observatory | Town | the Seer (a Master Cleric) | none | the Sky Chart; the Coming days page |
| Hanging Gardens | Town | the Grand Chef (a Master Chef, 8 kinds of meal) | none | a quarter more harvest across the realm; faster births |
| Colossus | City | the Founder (City rank) | none | more mood across the realm; more and better travellers; beauty |
| Stadium (with Cobblemon) | Town | the Pokémon Professor (25 pastured Pokémon of 10 types) | Cobblemon | Stadium Days with visiting trainers; hosts the Festival Cup |

Every Wonder also needs the Master Architect (stand-in: a Master builder).

**The Great Cathedral** (35.9) is drawn: a Gothic cathedral on a cross plan, 39 blocks across the transepts, 55 long,
its two west towers 44 high under stone spires reaching 52; about 14,250 blocks. A deep west door under three stepped
pointed arches, a rose window of white, light blue, yellow and red glass over it; a nave of five bays with flying
buttresses over the aisles and tall lancet windows; transepts with gable windows over their doors; a choir ending in a
rounded apse; open belfries with three bells. Inside: pews either side of a red runner, banners and lanterns on the
polished andesite piers, an altar of gold and candles, three side chapels with lecterns, and a sacristy with a brewing
stand. It goes up in four stages, each leaving the site tidy: I *The Foundations and the West Front*, II *The Nave*,
III *The Transepts and the Choir*, IV *The Towers and the Rose Window*. Its goods: I 128 bread (Chef), 8 iron pickaxes
(Toolsmith), 16 gold ingots (Armorer); II 64 white candles (Dyer), 32 honeycomb (Beekeeper), 64 white wool (Shepherd);
III 16 books (Librarian), 12 potions of healing (Cleric), 64 small flowers (Florist); IV 32 chiseled stone bricks
(Mason), 16 cakes (Chef), 64 bone meal (Composter). It can't be built in game until the rest of Wonders is.

**The Lighthouse** (35.11) is drawn: a coastal landmark 25 by 33 and 46 high, about 5,000 blocks, its back half in the
sea. A round tower banded in white and red terracotta tapers from 13 across to 9 on a stepped stone mole (mossy at the
waterline, steps up to the door, mooring posts round its foot, an arm along the back sheltering a little harbour). Its
gallery at 38 stands on stone corbels behind an iron railing; above it a lantern room of glass round sea lanterns, a
ring of redstone lamps that an inverted daylight detector lights at night, and a copper cupola with a lightning-rod
vane. Inside, a spiral stair of spruce climbs past five rooms from the door to the lantern. At its foot: the keeper's
cottage (a stone ground floor, an upper storey of white terracotta in a spruce frame, a slate roof; a bed upstairs, the
fisherman's barrel by the stove), a spruce boathouse half over the water, a stone quay with mooring posts, and a
timber jetty out over the sea to a ferry stop, the Travel Post under a little roof. Its stages: I *The Mole and the
Quay*, II *The Tower*, III *The Gallery*, IV *The Lantern and the Keeper's House*. Its goods: I 64 cooked cod
(Fisherman), 8 oak boats (Carpenter), 64 coal (Miner); II 16 chains (Tinkerer), 32 iron ingots (Armorer), 32 white
wool (Shepherd); III 4 explorer's maps (Cartographer), 32 spectral arrows (Fletcher), 32 leather (Butcher); IV 64
glowstone dust (Netherworker), 32 redstone (Miner), 16 pumpkin pies (Chef). Its powers come with 35.12.

**The Great Library** (35.13) is drawn: a domed library on a cross plan, 41 by 41 and 34 high, about 15,000 blocks, on a
square terrace of smooth stone with a garden in each corner (azalea hedges, flowers, a bench and a lamp) behind a
diorite balustrade. A grand stair of three quartz steps climbs to a portico of six quartz columns (a wider bay before
the door) under a low pediment with a round window. Behind it the rotunda, 19 across, rises above four wings of reading
halls under deepslate-tile roofs: a drum of polished diorite with clerestory windows and a cornice, a shallow copper
dome with eight ribs (it weathers green), coffered inside in smooth stone and diorite, and a glass lantern on top. Each
wing has two storeys of tall windows between smooth stone pilasters; inside, bookshelf stacks out from the walls under
a dark oak gallery on both sides (two floors of bookshelf galleries, a ladder up to each), carrels under the windows,
reading tables with lanterns and low bookcases down the middle, and a coffered dark oak ceiling. The rotunda has three
rings of bookshelves with two galleries round them; at its centre an Enchanting Table ringed by fifteen bookshelves,
open towards the door, with the librarian's lectern before it and four reading desks round it. A lectern for a scholar
stands in each wing; the west wing's end is the map room, a carpet map of seas and islands round a cartography table.
Its stages: I *The Stacks*, II *The Galleries*, III *The Rotunda*, IV *The Dome and the Portico*. Its goods: I 128
sugar cane (Farmer), 64 leather (Butcher), 64 spruce logs (Lumberjack); II 64 books (Librarian), 64 black dye (Dyer),
16 lanterns (Tinkerer); III 8 explorer's maps (Cartographer), 32 lapis lazuli (Sifter), 32 copper ingots (Armorer); IV
64 nether quartz (Netherworker), 32 cookies (Chef), 16 bookshelves (Librarian). It needs a Town, 20 research levels and
the Old Sage (or a Master Scholar while Legends are off). Its power, a second research tier, comes with 35.14.

**The Observatory** (35.17) is drawn: a round tower 15 across and 30 high under a copper dome, on a raised terrace 31
by 31 (42 high to the dome's vane, about 8,400 blocks) of deepslate bricks behind a polished blackstone balustrade, a
wide stair up its front. The tower is deepslate bricks and tiles with polished blackstone bands at its floors, eight
blackstone pilasters up its height and tall windows; inside, a dark oak spiral stair climbs past the Seer's study
(bookshelves and a lectern), a store of chests and barrels and the instrument room (a cartography table, a lectern)
to the top, where a walk behind a blackstone parapet runs round the dome: the viewing platform. The dome, of cut copper
on a blackstone drum (it weathers green), has a slit of tinted glass three wide from its foot to its crown on the
courtyard side and a door onto the walk at the back; the great telescope, a cut copper barrel 12 long on a blackstone
mount, leans up and out of the slit over the courtyard. Two low wings under hipped deepslate-tile roofs flank the
tower: the star-chart room (a cartography table on a carpet chart of the night sky, a lectern, chart shelves) and the
Seer's lodging (a bed, a brewing stand, a barrel). The courtyard is paved in deepslate tiles ruled by blackstone, with
amethyst set in it: a compass star round the sundial (a round stone face and its gnomon), studs along the path and a
ring round the tower's foot. Opposite the sundial stands the orrery: a gold sun on a chain under a blackstone frame,
with five planets (iron, copper, lapis, redstone and raw gold) hung on chains round it. Its stages: I *The Terrace and
the Courtyard*, II *The Tower*, III *The Dome*, IV *The Great Telescope and the Orrery*. Its goods: I 32 lapis lazuli
(Sifter), 64 cooked chicken (Chef), 8 explorer's maps (Cartographer); II 64 copper ingots (Armorer), 4 spyglasses
(Toolsmith), 32 amethyst shards (Miner); III 16 books (Librarian), 8 tinted glass (Carpenter), 32 feathers (Butcher);
IV 32 chains (Tinkerer), 9 gold ingots (Armorer), 32 pumpkin pies (Chef). It needs a Town and the Seer (or a Master
Cleric while Legends are off). Its powers, the Sky Chart and the Coming days page, come with 35.18.

**The Great Wall** (35.15) is drawn: a defence 97 long, placed like the other walls (stand in the village and place it
facing out: its stairs are on your side), about 10,800 blocks. A curtain 7 thick and 12 high on a battered plinth of
deepslate bricks, faced with stone bricks to the field (mossy and cracked on the low courses, a buttress every four or
five blocks) and cobblestone to the village; on top a walk paved six wide behind a breastwork corbelled out over the
field, crenellated, with a low parapet on the village side carrying braziers (campfires on chiseled stone). Four towers
9 by 9 and 18 high under hipped spruce roofs, one at each end and two at the quarters, each with a door from the village,
a stair round its inside to a room at the walk (doors onto the walk, a guard's grindstone, and on the side facing the gate
an alcove 3 wide and 3 high open to the walk, where a sentry golem stands), a stair on up to a top room with windows on
three sides, arrow slits low down and a banner to the field. At the centre the Great Gate, a gatehouse 17 wide and as high
as the towers under a long hipped roof: a vaulted gateway 5 wide with spruce fence gates two high across it under a
portcullis of iron bars, which runs up a slot into the hall and hangs from a windlass; a guardroom either side with its
own stair, a hall at the walk, a top room opening onto a crenellated bretèche over the gate, buttresses either side of
the gateway, braziers at both its mouths and banners beside them. Two stairs of ten steps climb the village side to
landings by the gatehouse. Its stages: I *The Great Gate*, II *The West Run*, III *The East Run*, IV *The Walk and the
Towers' Crowns* (the walk, breastwork and braziers, the towers' top rooms and roofs). Its goods: I 8 iron swords
(Weaponsmith), 128 arrows (Fletcher), 128 bread (Chef); II 8 iron shovels (Toolsmith), 12 potions of strength (Cleric),
64 baked potatoes (Chef); III 8 crossbows (Fletcher), 8 iron chestplates (Armorer), 8 shields (Carpenter); IV 16 iron
blocks (Armorer), 4 pumpkins (Farmer), 16 campfires (Carpenter). It needs a Town, a finished Gatehouse and the Golem
Smith (or 4 iron golems and a Master Tinkerer while Legends are off). Its powers, the raid barrier and the sentries,
come with 35.16.

**The Hanging Gardens** (35.19) are drawn: a stepped garden mountain on a 39 by 39 foot, 31 high to its treetops, about
16,200 blocks. Four terraces 6 high, each 4 in from the one below (39, 31, 23 and 15 across), each faced all round with an
arcade of 3-wide arches on cut sandstone columns with chiseled capitals, under a terracotta band and a cut sandstone
coping; behind each arcade a gallery runs round the terrace under its garden, lit by glow berries hanging in the arch
heads and lanterns. Inside the first terrace are the Cistern Vaults: a hall 29 by 29 of 36 columns round a pool with a
chain pump, a doorway from the gallery on each side. Each ledge is a garden on the gallery's roof: a hedge of azalea and
oak leaves on the coping, fields of wheat, carrots and beetroots between water channels, sweet berry beds, glow berry
arbors, flower beds with azaleas, beehives, and on the corners oaks, flowering azalea trees and a farm corner with a
composter and a chest on each level (four village farms). Vines hang down the columns and over the arches. A ramp
spirals up, a stair with a balustrade on each side in turn: along the front from the ground, up the second terrace's east
face, the third's back and the crown's west face. On the crown a cistern with a fountain pours down a channel to the east
edge: the falls drop from a basin on each ledge to the next and into a pool with lily pads at the foot, past a blind,
mossy stretch of arcade. Its stages: I *The First Terrace and the Cistern Vaults*, II *The Second Terrace*, III *The
Third Terrace*, IV *The Crown Garden and the Falls* (the crown, its garden and cistern, the last ramp and the water,
poured from buckets). Its goods: I 128 bone meal (Composter), 128 wheat seeds (Farmer), 64 clay balls (Sifter); II 64
sweet berries and 32 glow berries (Orchard Keeper), 64 small flowers (Florist), 16 saplings (Lumberjack); III 32 honey
bottles (Beekeeper), 64 carrots and 64 potatoes (Farmer), 32 cocoa beans (Orchard Keeper); IV 16 cakes (Chef), 32 eggs
(Butcher), 32 raw cod (Fisherman). It needs a Town and the Grand Chef (or a Master Chef and 8 kinds of meal in the store
while Legends are off). Its powers, the bigger harvests and the faster births, come with 35.20.

**The Colossus** (35.21) is drawn: the Founder's statue, 47 high on a 29 by 29 site, about 9,000 blocks, standing over the
village's main road. It is a villager drawn a pixel to a block from the game's own model, so it reads as one from far
off: the tall head (9 across, 10 high), the long nose hanging past the chin, a dark unibrow over white eyes with emerald
pupils, and a robe of polished diorite trimmed with smooth stone at the hem, the belt and down the front, flaring at the
hem over two feet. Its left arm folds across the chest, the hand resting on it; its right arm rises clear of the head and
holds up the lantern, a cage of glass panes round glowstone under a copper cap, lit day and night. A cloak of cut copper
(about 370 blocks with the lantern's copper) hangs from the shoulders to the pedestal in three pleats, and weathers green
over the weeks. The plinth is 13 high in three steps of stone bricks (mossy and cracked low down) on a skirt of deepslate
brick: polished andesite pilasters and a cornice, a terrace with a balustrade and nine braziers round it, and a smooth
stone pedestal. The road passes front to back through an arch 5 wide and 6 high, with lantern niches down the passage
and lamp posts at both mouths; over the front arch is the Founder's plaque, a row of five dark oak signs on a dark oak
panel (35.22 writes them). The arm and the head need scaffolding (35.6). Its stages: I *The Plinth and the Road Arch*,
II *The Robes*, III *The Arms and the Cloak*, IV *The Head and the Lantern*. Its goods: I 128 baked potatoes (Chef), 64
coal (Miner), 8 iron pickaxes (Toolsmith); II 64 copper ingots (Armorer), 64 white wool (Shepherd), 32 leather
(Butcher); III 32 chains (Tinkerer), 32 stripped dark oak logs (Lumberjack), 16 potions of strength (Cleric); IV 32
glowstone dust (Netherworker), 64 small flowers (Florist), 16 cakes (Chef). It needs a City and the Founder (or City rank
while Legends are off). Its powers, the realm's mood, the travellers and its beauty, come with 35.22.

**The Stadium** (35.23, only with Cobblemon) is drawn: an oval arena 47 by 37, its bowl 16 high (20 to the gate's
banners and the floodlights, about 7,300 blocks), round a grass battle field 31 by 21 ruled in white (its edge, the
half-way line, a centre circle; no logos) inside a smooth stone apron and a stone barrier. The stands step up two at a
time, five tiers along the long sides and three at the ends, with red and white carpet seats in wedges round the bowl;
in a village with a Village Banner the red and the white are built in its banner's two colours instead (the base and
the first other colour of its pattern; the field's white lines stay white). A trainer's box is cut into each end, a
stone room under a spruce roof with a Training Post facing the field, and the professor's box stands raised in the
middle of the south stand, a lectern behind its rail. The west end is the covered stand, under a lean-to canopy of
spruce slabs on four spruce columns; over the east end stands the scoreboard, black terracotta in a stone frame under
a red header, showing 2 - 1. Outside, the back wall is an arcade into a concourse that runs round under the top tier,
with pilasters, two bands of red and white terracotta and a parapet with lanterns. The gateway on the north side is a
tunnel through the stand under two piers and a lintel banded red and white, with standing banners on the piers and
banners hung over the stand. Four floodlight towers stand at the corners, each an iron head with eight redstone lamps
wired round a daylight detector that lights them at night. In the corners outside are the nurse's booth (a striped
stall with a Healing Machine and a brewing stand) and seven market pitches, three with stalls on them. Its stages: I
*The Field*, II *The Lower Stands*, III *The Upper Stands and the Covered Stand*, IV *The Gate and the Floodlights*.
Its goods: I 128 bone meal (Composter), 64 small flowers (Florist), 32 Poké Balls (Ball Smith); II 64 red wool (Dyer)
and 64 white wool (Shepherd), 16 Poké Snacks (Chef), 16 Great Balls (Ball Smith); III 16 banners (Carpenter), 64
cooked beef (Chef), 32 berries (Orchard Keeper); IV 8 daylight detectors (Tinkerer), 16 Ultra Balls (Ball Smith), 32
pumpkin pies (Chef). It needs a Town and the Pokémon Professor (or 25 pastured Pokémon of 10 types while Legends are
off). Without Cobblemon its file isn't loaded, so it isn't on the Wonders page. M28's Arena is a smaller ring of its
own, not a Stadium, so this one keeps the name Stadium. Its powers, Stadium Days and hosting the Festival Cup, come with
35.24.

## How it works

**A Wonder is a data file** (35.2), `data/<namespace>/wonders/<id>.json`, read whenever data packs load, so a server
owner can add their own Wonder with a data pack and it works like ours. The file holds:

- `name` and `description`: lang keys. `icon`: any item.
- `requirements`: a list of conditions (below).
- `stages`: each a `name`, its blueprint `parts` (a structure id and its `offset` from the Wonder's corner), and its
  `goods`, each `{"item"` or `"tag", "count", "job"}`, with an optional `potion` to name a kind of potion.
- `powers`: `[{"type": ..., numbers}]`. The types are listed in the design note; each Wonder's item makes its own work.
- `crew` (default 12, at most 32) and `enabled` (false: the file is skipped). `fabric:load_conditions` are honoured,
  so a Wonder can need a mod (the Stadium will need Cobblemon).

A file with a mistake (an unknown condition or power type, a missing field, a count of 0, an unknown item, job or
potion, a stage without parts) is skipped and the log says which file and why; the other files still load.

**Requirements** use the same condition toolbox as Legends (`rank_at_least`, `research_levels`, `finished`,
`villagers`, `job_level`, `caravan_routes`, `iron_golems`, `meal_kinds`, `pastured_pokemon`, …), plus two types new
in 35.2:

- `coastal`: an ocean or beach within 48 blocks of the hall. It is the same coast check the drowned pirates use, looked
  at once a day per hall, so both agree on what a coast is.
- `mod_loaded` (`mod`): that mod is installed.

`legend` gained an optional `stand_in`, a list of conditions: while Legends are off, `wonderLegends` is off, or that
Legend's file isn't loaded, the stand-in list must hold instead (all of it). A `legend` without one reads as before.
`pastured_pokemon` counts through the Cobblemon census; without Cobblemon it is always false.

**Every Wonder needs the Master Architect** to draw up its plans. The mod adds this line to every Wonder itself (with a
Master builder as the stand-in), so no file can leave it out.

**The caps** (checked before the requirements):

- a village may hold `wonderCaps` Wonders by rank: Hamlet 0, Village 0, Town 1, City 2 (finished or going up);
- one of each kind per village;
- one Wonder going up per village at a time;
- `wonderServerLimit` villages on the server may each build the same kind (0: no limit);
- a Wonder in `disabledWonders` can't be started.

Each refusal says why ("Wonders: 1 of 1 for a Town.", "This village already has the Great Cathedral.").

**A Wonder's blueprints** (35.3): each stage's parts are structure files, `structure/wonder/<id>/stage_<n>_<part>.nbt`,
each at most 48 a side and only what that stage adds to the ones before (stage I also clears the site). The whole
Wonder, `<namespace>:wonder/<id>`, is those parts laid at their offsets, merged once and kept until data packs load
again; it is what the plan shows. Nothing under `wonder/` is ever on the Blueprint Table. A Wonder whose part is
missing can't have its plans drawn up (the log names the part). For pack makers drawing their own: the blueprint kit's
`wonder_stages()` (`tools/blueprints/kit.py`) takes the Wonder drawn one stage at a time and writes the parts and their
offsets; `tools/blueprints/wonders.py` draws the test folly with it.

Each Wonder in progress or finished is recorded per dimension, with its village's hall, the player who started it, its
stage and the days it was begun and finished. The Wonders page (35.3) and the Foundation Stone (35.4) will use these
checks; stage parts are ordinary builds of the builder, using the Foundation Stone as their bench.

**Our Wonders' designs** (35.9 on) are drawn whole in `tools/blueprints/wonders.py`, one function each, stage by stage
on a staged build that refuses to draw over an earlier stage's block; `wonder_stages()` cuts each stage into parts and
`wonders.py` writes the Wonder's data file (`aliveworkplace:great_cathedral` is
`data/aliveworkplace/wonders/great_cathedral.json`, `aliveworkplace:lighthouse` is `.../lighthouse.json`, `aliveworkplace:great_library` is `.../great_library.json`, `aliveworkplace:observatory` is `.../observatory.json`, `aliveworkplace:great_wall` is `.../great_wall.json`, `aliveworkplace:hanging_gardens` is `.../hanging_gardens.json`, `aliveworkplace:colossus` is `.../colossus.json`) with those parts, so the two always agree. Each stage is checked
as it will stand, on the stages before it (nothing held up by nothing), and every stage leaves the site tidy, since the
village lives beside it for days.

## Switches

All behind the 2.0 switch: until 2.0 is finished they are off and hidden, whatever the config file says.

| Switch | Default | Off means |
|---|---|---|
| `wonders` | on | no new Wonders; finished ones keep their blocks, everything stays saved |
| `wonderLegends` | on | each Wonder's Legends are replaced by their stand-ins |
| `wonderServerLimit` | 0 (0 to 64) | how many villages may each build the same kind; 0 is no limit |
| `wonderCaps` | 0, 0, 1, 2 | file only: Wonders per village by rank (each 0 to 8; a short list is filled from the defaults) |
| `disabledWonders` | empty | file only: Wonder ids that can't be started (`great_cathedral` or `pack:id`) |

Later items add three more: the most builders on one Wonder, builders' scaffolding, and how much of a Wonder must stand
for its powers.

## Saved data

A new saved file per dimension, `aliveworkplace_wonders`, with a list of `sites`: each with its `id`, `wonder`, `hall`,
`owner` and `ownerName`, `stage` (default 1), `begun` and `finished` (absent until it is). A world saved before 35.2 has
no such file and loads unchanged. Planned (35.4 and later): the placement, the stone and the parts' build sites, and two
optional fields on a build site.

## Items, blocks, jobs, commands

- `/workplace wonders`: every Wonder on the server, one line each: its name, village, owner and stage ("stage 2 of 3,
  …" or "finished on day 12"). While `wonders` is off it says "Wonders are off."

- **Wonder Plan** (`aliveworkplace:wonder_plan`): drawn up on the Wonders page; not crafted, not in the creative tab.

Planned: the Foundation Stone (a block).

## Decisions

- The Great Cathedral's stages are cut by `wonder_stages()` on a 48-block grid (35.9): nine parts, two of stage I only
  clearing the air up to the spires' tips (y 48 to 52), and the spires' last few blocks a small part of stage IV.
- A stage's round rose opening and the nave's open east end wait for the next stage (35.9): the rose's ring is built
  with the nave (II) and its glass with the towers (IV), as the roadmap splits them; nothing hangs in the air between
  stages.
- The Lighthouse is about 5,000 blocks, not the 7,500 first planned (35.11): a tower 13 to 9 across with the cottage,
  boathouse, quay and jetty fills 25 by 33 by 46 that way, and only solid masonry would reach 7,500. Its base is solid
  round the stair and its wall three thick low down, as a sea tower's is.
- The Lighthouse's sea is the land behind its quay (35.11): the front (z 0) is the land side, where the village is; the
  mole, the boathouse's open end and the jetty stand in the water from 11 back. The jetty's deck is top slabs on log
  posts, so it gets no foundation, as the Fisher's Hut's; the mole's bottom course is filled down to the sea bed.
- The Lighthouse's lamps are redstone lamps under redstone dust that an inverted daylight detector on the lantern's roof
  powers (35.11): lit at night, dark by day, with no clock. The lamps' lit state is not checked as damage.
- The Great Library's walls are two leaves (35.13), diorite outside and smooth stone inside, with deep windows, and it
  stands on a solid terrace three high.
- The Great Library's sixteenth bookshelf place round the Enchanting Table is left open towards the door (35.13), so the
  librarian can walk in; fifteen shelves still give the table its full power.
- One Legend can serve two Wonders (the Seer for the Cathedral and the Observatory): a requirement only checks that the
  Legend lives in the village.
- The Master Architect requirement is added to every Wonder by the mod, so a server owner's own Wonder needs it too.
- Builders' scaffolding comes with 2.0, for every build (an owner question in the design note).

## Known limits

- No Wonder can be started in game yet: the plan can be drawn and placed, but there's no Foundation Stone to hand it
  over (35.4). The Great Cathedral's, the Lighthouse's, the Great Library's, the Observatory's, the Great Wall's, the
  Hanging Gardens' and the Colossus' files ship (35.9, 35.11, 35.13, 35.17, 35.15, 35.19, 35.21), so they are on the
  Wonders page behind the 2.0 switch; their powers come with 35.10, 35.12, 35.14, 35.18, 35.16, 35.20 and 35.22, and the
  Stadium's file with 35.23. Everything is behind the 2.0 switch.
- A Wonder going up shows "how far along" by its stages done; the parts' own progress joins with 35.4.
- Drawing up the plans doesn't reserve the Wonder: two plans of one Wonder can be drawn; the caps hold when it's begun.
- A stage's `parts` are checked to exist when the plans are drawn up (the log names a missing one), not when loaded.
- Powers are read and checked for their type only; what each does comes with its Wonder.

## Proof

The design note, docs/design/M35.md. GameTests in `WonderDataGameTests`: the test folly's file loads (gametest
resources only), broken files are skipped with their reasons, a Wonder from a data pack on disk loads beside ours,
every requirement type holds and fails, `legend` falls back to its stand-in, the caps (a Town with one can't start a
second and a City can, the same kind twice, one going up, `wonderServerLimit 1`, disabled, off), `/workplace wonders`,
the switches and the gate, and the saved sites.

GameTests in `WonderPlanGameTests` (35.3): the folly's three parts merge into the tower as drawn (compared block by
block with the drawing), no part sets air over an earlier stage's block, parts are at most 48 a side, the whole
blueprint is cached and never on the Blueprint Table; the page's card ticks every line and offers the plans, one unmet
line (no Master builder) shows crossed, the cap line, and "What next?" names the folly; Draw up the plans takes the
player's 32 emeralds or the treasury's, refuses without the money, over the cap and for a stranger, taking nothing;
the plan is refused outside the village and over a finished building and goes down elsewhere in all four turns.

`WonderBlueprintGameTests` checks every one of our Wonders' designs (35.9 on): its file loads with the mod's own
loader, every part loads and is at most 48 a side, its name, description and stage names have their lines, the parts
aren't in the Blueprint Table, no part sets air over an earlier stage's block, the block count is within 10% of the
roadmap's (14,000 for the Cathedral, 5,000 for the Lighthouse, 16,000 for the Great Library, 9,000 for the Observatory, 11,000 for the Great Wall, 15,000 for the Hanging Gardens, 9,000 for the Colossus), and it uses no retired job block, no calcite and nothing a player can't get (still water counts: a builder pours it from a bucket).
The Stadium's file and its Healing Machine need Cobblemon, so its same checks run in the compat tests
(`StadiumCompatTests`, 7,300 blocks), with its requirements and goods as listed, its nurse's booth, Training Posts and
floodlights, and its stages built in a village's banner colours (blue and red for a blue banner with a red stripe, the
field's lines still white; red and white for a village without one). `StadiumGameTests`, without Cobblemon: its file
is skipped quietly (not on the page, no mistake logged), and the colours style and how a banner picks it.

Showcase scenes: wonders_page (group Wonders: the folly's card, then the placed plan's see-through tower) and
wonder_gallery (each of our Wonders placed whole, from the front and from behind; the Lighthouse with its sea flooded
in, so the mole and the jetty stand in the water; the Great Wall's front is its village side; the Hanging Gardens' falls are on their east side; the Colossus faces its front, down the road through its arch; the Stadium needs Cobblemon, so the scene is filmed with it; its card shows four of the stills, the rest are in its gallery).
