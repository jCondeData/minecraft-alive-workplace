# Wonders

A Wonder is a village's life's work: a huge build raised in four stages by every builder in the village at once, fed by
goods from every job, drawn up by the Master Architect and needing a second Legend. Each of the eight Wonders changes
what its village or its realm can do while it stands. Part of 2.0 (Wonders): the data files, requirements and caps, the
Wonders page, the Wonder Plan, the Foundation Stone with its stages, crews and goods, a finished Wonder's celebration,
upkeep and powers, and the first Wonders' designs (the Great
Cathedral, the Lighthouse, the Great Library, the Observatory, the Great Wall, the Hanging Gardens, the Colossus and, with
Cobblemon, the Stadium) are built, behind the 2.0 switch (see Known limits); the full plan is the design note,
docs/design/M35.md.

Roadmap items: 35.1, 35.2, 35.3, 35.4, 35.5, 35.7, 35.8, 35.9, 35.11, 35.13, 35.15, 35.17, 35.18a, 35.19, 35.21, 35.23

## What a player sees

**The Wonders page** (35.3): a Wonders tab on the Village Hall (a spare tab, so the tabs kept for add-ons stay free).
Each Wonder on the server's list is a card: its icon, name and power, then "Needs:" with each requirement ticked (✔) or
crossed (✘) with the village's progress ("Finished Chapel: 2 of 3", "Standing in for the Master Architect: …"), the cap
("Wonders: 0 of 1 for a Town") and what a click does. A Wonder going up here shows its stage and how far along it is
("Going up: stage 2 of 3, The Upper Story (33% along)"), a finished one its day.

**Draw up the plans**: with every line ticked and the caps clear, a click on the card pays 32 emeralds (the realm's
treasury if the village is in a realm and realms are on, else the hall's treasury, else the player's own emeralds; free
in creative when neither treasury has them) and gives a **Wonder Plan**, a rolled blueprint with a gold seal. Only the
hall's owner, their friends and operators may. Before that, a click says what's missing and takes nothing.

**The Wonder Plan** holds the whole Wonder's blueprint:

- held and placed, it shows the whole Wonder as see-through blocks, like any blueprint;
- right-click the ground to place it (its front faces you), sneak-right-click the ground to turn it, sneak-right-click
  the air to pick it up again or, not placed, open the style screen, where it can only be mirrored (each Wonder keeps
  its own blocks);
- it goes down only with its center within the hall's reach (64 blocks), every corner within 96 blocks of the hall, and
  not over a finished building or another Wonder; otherwise it says why ("It would stand over the finished Chapel");
- a Book and Quill in the other hand writes the whole Wonder's material list.

**The Foundation Stone** (35.4): right-click a builder of the village (their bench within 64 blocks of the hall) with
the placed plan. A **Foundation Stone**, a carved stone with a brass plaque, goes down just outside the Wonder's front
corner (the next free spot round it if that one's taken); everyone on the server reads "Owner has begun the Test Folly
in Testholm!", the chronicle gets "Owner laid the Foundation Stone of the Test Folly", and stage I starts as soon as
its goods are in (35.7, below; at once if the stockpile holds them already).
The chests within 8 blocks of the stone are the Wonder's stockpile. Refused, saying why, when the plan isn't placed,
the builder has no bench or works for another village or another player, the caps no longer allow it, or there's no
room for the stone.

- **Right-click the stone**: "The Test Folly of Testholm", "Stage 1 of 3, The Ground Storey: 34% built", then the
  stage's material list, each line "40 Stone Bricks, 12 still to bring" or "… all there", what's still to bring first.
- **Each stage starts when every part of the one before is done and its own goods are dedicated**, and the builder it
  was handed to takes it on (the owner reads "Stage 2 of 3 of the Test Folly is next: The Upper Storey. It begins once
  its goods are in").
- **Sneak-break the stone** (the owner or an operator) or `/workplace cancel` on a part to abandon the Wonder: what's
  built stays, the open parts end, the plan comes back (to the owner, else by the stone) and the chronicle says so. A
  plain break says to sneak; anyone else is refused. Builders never clear it and pistons can't push it; a finished
  Wonder's stone breaks like any block (it drops nothing).

**Crews** (35.5): every builder of the village (their bench within 64 blocks of the hall) with no build of their own
joins the Wonder's crew, up to 12 (the `wonderCrew` switch, and no more than the Wonder's file allows). The stone's
status says who leads and how many are on it ("Led by Odo, with a crew of 8 (at most 12)"), and so does the Wonder's
card on the hall's Wonders page. Click **[Call every builder]** under the stone's status (the owner or an operator,
within 8 blocks of the stone; `/workplace wonders call`) and the busy builders come too, each as they finish what
they're on: "Every builder is called". The lead is the Master Architect when he's one of the village's builders,
else the builder it was handed to; each next stage goes to the highest-level builder of the crew.

- Each member takes a **column** of its own (the part split at least 8 × 8: a 64-wide part has 8-block columns, the
  9-wide folly 1-block ones), the free one with work nearest to them, and works it in the stage's order: clearing from the top, the rest
  from the bottom. Nobody works more than two layers above the part's lowest unfinished layer (a block the lead gave up
  on, out of reach, doesn't hold the rest back; levelling the ground round it isn't layered), and no structure block
  goes up before the block the drawing puts under it.
- Crews add hands, not speed: each builder keeps their own pace. Eight builders raise the folly in about 2,500 to
  2,900 ticks against about 12,150 for one alone (at one block a second): under a quarter of the time.
- The crew fetches from the chests round the stone and the village's stores, and passes materials to each other. In
  one trip each member takes for the rest of its column and a share of the stage, dealt round the crew like cards (each
  block's materials to half of the crew), plus a little of the stages after it (the decoration's lanterns and doors), so
  nobody walks down from a dome to the chests for every block.
- If the lead dies, its part goes to the highest-level member of its crew (else to the builder the next stage would get,
  else to the first to join); what it carried drops where it fell, as for any builder.

**What next?** names the Wonder closest to ready once the village is a Town with room for one: "The Great Cathedral can
be begun", or the first line it lacks ("The Great Cathedral is nearly in reach: Finished Chapel: 2 of 3").

**Goods and the dedication** (35.7): besides its blocks, each stage asks for goods from the village's jobs, the lines of
its file: the feast for the crew, their tools, what the rites need (candles, books, potions) and its treasures. A line
is in when the stockpile's chests and what was handed in at the stone hold its count together. A stage starts only
when every line is in; then, by day, it is **dedicated** (at night it waits for the morning): the bell rings at the
stone and at the village's bell, the goods are taken (exactly each line's count: what was handed in first, then from
the chests), the chronicle says "Stage 2, The Upper Storey, of the Test Folly was dedicated: the village gathered at
the stone, the bell rang and the goods were given", the owner reads "Stage 2 of 3 of the Test Folly is dedicated: The
Upper Storey begins", and the stage's parts go down for its lead. For the next 30 seconds the villagers off work
gather round the stone, the village's Bard plays a tune beside it ("playing at the dedication") and its guards stand
in a line three blocks out, on the side away from the Wonder ("standing in line at the dedication"), when it has them.

- **The stone's status** shows every line while the stage waits: "Before it begins, this stage needs its goods (in the
  chests within 8 blocks, or handed in here):", then "2 of 4 Flowers · Florist (no Florist in the village)", "0 of 2
  Potion of Healing · Cleric", and how to hand goods in; with every line in, "Every good is in: the stage is dedicated
  shortly" (by night "… in the morning"); once dedicated, "This stage's goods are dedicated".
- **Anyone can hand goods in**: right-click the stone holding them (up to what the line lacks: "Handed in 4 Flowers for
  the Test Folly: 4 of 4 in"; a full line says "The Test Folly has all the Flowers it needs"), or click the line on a
  Storehouse's board.
- **The requests board**: each line still short is posted for the stone (a request for a block instead of a villager):
  "8 × Bread", "For the Test Folly, 30 blocks away", "Made by the Chef"; a click hands over what you have of it.
- **Porters** carry matching goods from the storehouse (and what a worker's job doesn't keep from their chests; tools
  and potions only from the job that makes them) to the stockpile before anything else ("carrying goods to the Test
  Folly").
- **Crafters** (chefs, carpenters, masons, tinkerers, dyers) make a short line as a stock order for the stockpile, from
  their own chests and the storehouses near (not what those keep for their orders), before the storehouse's own orders.
- **The hall's daily quest** asks for the line furthest behind: "Bring goods for the village's Wonder", as many as it
  lacks, handed in at the hall into the stockpile.
- **Cancelling** a Wonder hands the goods handed in for a stage not yet dedicated back to its owner (by the stone if
  they're away): "The 5 goods handed in for the Test Folly came back to you"; what lies in the stockpile's chests stays
  there, and a dedicated stage's goods were given.

**A finished Wonder** (35.8). When the last stage is done:

- **Every player on the server is told**, wherever they are: "Pinevale has finished the Test Folly!"
- **The village holds a festival that evening** (the next evening if this one's festival is already over): the usual
  feast, music and dancing, and at dusk a bigger firework show: three rockets every two seconds, large gold balls with
  trails and stars, two from round the square and one from the Wonder's own top (under Curfew's "stay in", none).
- **The chronicles record it**: the village's ("The Test Folly was finished, its Foundation Stone laid on day 3") and
  every village linked to it, its realm's villages or, without a realm, its caravan partners ("Pinevale finished the
  Test Folly").
- **The Village Map marks it with a gold banner** at its middle, with its name (its parts aren't marked one by one).
- **Its powers come on**: those its file lists. Two kinds are made for server owners' own Wonders: `mood` (everyone in
  the village, or with `"scope": "realm"` the whole realm, is that much happier, "proud of the Test Folly" among their
  reasons; `home_amount` for its own village) and `beauty` (points added to the village's beauty, as a fountain's). The
  hall's card lists each power ("✔ Everyone in the village is 4 happier").

**Keeping it up.** Once a day each finished Wonder counts how much of it still stands, every block of every stage (at
most 4,096 blocks a tick, and only while all of it is loaded). A block stands when it's there as drawn; properties
that depend on neighbours don't count, and copper that weathered or was waxed is still the copper it was, so a dome
turned green still stands. The stone and the hall's card say "Standing: 97% of it (counted on day 12). Its powers are
on."

- **Under `wonderStanding` (90%) it is damaged**: its powers stop (the card lists them "(stopped)"), the stone and the
  card say "Damaged: 84% of it stands (90% needed). Its powers have stopped until the builders repair it.", the
  chronicle says so and its owner is told.
- **The village's builders repair it before anything else**: an idle builder of the village puts back every missing
  block, a repair per part, from the chests by its Foundation Stone (nothing players put there is touched), even with
  `builderRepairs` off: "Odo is repairing the Test Folly: 87 blocks to put back, from the chests by its Foundation
  Stone." Each part they finish counts it again.
- **Back at 90%, the powers return**: "The Test Folly of Pinevale stands again (91%): its powers have returned.", and
  the chronicle says it was repaired.

| Wonder | Rank | Legend (stand-in) | Other requirements | Powers |
|---|---|---|---|---|
| Great Cathedral | Town | the Seer (a Master Cleric) | 3 finished Chapels | Undertakers revive anywhere in the village for free; blessed weddings; beauty |
| Lighthouse | Town | the Merchant Prince (3 caravan routes) | on the coast | caravans reach twice as far in half the time; ferry fares halved; raid warnings |
| Great Library | Town | the Old Sage (a Master Scholar) | 20 research levels | a second research tier |
| Great Wall | Town | the Golem Smith (4 iron golems and a Master Tinkerer) | a finished Gatehouse | raids gather outside it; golem sentries on the walk |
| Observatory | Town | the Seer (a Master Cleric) | none | the Sky Chart; the Coming days page |
| Hanging Gardens | Town | the Grand Chef (a Master Chef, 8 kinds of meal) | none | a quarter more harvest across the realm; faster births |
| Colossus | City | the Founder (City rank) | none | more mood across the realm; more and better travelers; beauty |
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
waterline, steps up to the door, mooring posts round its foot, an arm along the back sheltering a little harbor). Its
gallery at 38 stands on stone corbels behind an iron railing; above it a lantern room of glass round sea lanterns, a
ring of redstone lamps that an inverted daylight detector lights at night, and a copper cupola with a lightning-rod
vane. Inside, a spiral stair of spruce climbs past five rooms from the door to the lantern. At its foot: the keeper's
cottage (a stone ground floor, an upper story of white terracotta in a spruce frame, a slate roof; a bed upstairs, the
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
wing has two stories of tall windows between smooth stone pilasters; inside, bookshelf stacks out from the walls under
a dark oak gallery on both sides (two floors of bookshelf galleries, a ladder up to each), carrels under the windows,
reading tables with lanterns and low bookcases down the middle, and a coffered dark oak ceiling. The rotunda has three
rings of bookshelves with two galleries round them; at its center an Enchanting Table ringed by fifteen bookshelves,
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
Cleric while Legends are off). Its powers, the Sky Chart and the Coming days page, come with 35.18. The Sky Chart
covers about 6,144 blocks across (384 chunks, 12 regions; 35.18a): one locked map at 48 blocks a pixel, since a vanilla
map shows at most 2,048. Its search and the chart itself are built (`wonder/SkyChart`): one region of one structure kind
every 5 ticks while the server's tick has room, at most one kind an in-game hour, saved as it goes; the hall's map
button that hands it over comes with 35.18.

**The Great Wall** (35.15) is drawn: a defense 97 long, placed like the other walls (stand in the village and place it
facing out: its stairs are on your side), about 10,800 blocks. A curtain 7 thick and 12 high on a battered plinth of
deepslate bricks, faced with stone bricks to the field (mossy and cracked on the low courses, a buttress every four or
five blocks) and cobblestone to the village; on top a walk paved six wide behind a breastwork corbeled out over the
field, crenellated, with a low parapet on the village side carrying braziers (campfires on chiseled stone). Four towers
9 by 9 and 18 high under hipped spruce roofs, one at each end and two at the quarters, each with a door from the village,
a stair round its inside to a room at the walk (doors onto the walk, a guard's grindstone, and on the side facing the gate
an alcove 3 wide and 3 high open to the walk, where a sentry golem stands), a stair on up to a top room with windows on
three sides, arrow slits low down and a banner to the field. At the center the Great Gate, a gatehouse 17 wide and as high
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
while Legends are off). Its powers, the realm's mood, the travelers and its beauty, come with 35.22.

**The Stadium** (35.23, only with Cobblemon) is drawn: an oval arena 47 by 37, its bowl 16 high (20 to the gate's
banners and the floodlights, about 7,300 blocks), round a grass battle field 31 by 21 ruled in white (its edge, the
half-way line, a center circle; no logos) inside a smooth stone apron and a stone barrier. The stands step up two at a
time, five tiers along the long sides and three at the ends, with red and white carpet seats in wedges round the bowl;
in a village with a Village Banner the red and the white are built in its banner's two colors instead (the base and
the first other color of its pattern; the field's white lines stay white). A trainer's box is cut into each end, a
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
- `powers`: `[{"type": ..., numbers}]`. Each type is code registered by its id (35.8, `wonder/powers/`): `mood`
  (`scope` `village` or `realm`, `amount`, optional `home_amount`; each -50 to 50) and `beauty` (`points`, 0 to 50) work
  now; the other types our Wonders name are declared (their numbers kept as written) until their Wonder's item fills
  them in. An unknown type, or numbers its reader refuses, fail the file.
- `crew` (default 12, at most 32) and `enabled` (false: the file is skipped). `fabric:load_conditions` are honored,
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
stage and the days it was begun and finished.

**Stages as build sites** (35.4): each stage's parts become ordinary build sites, placed with the Wonder's turn and
mirror at their offsets, pointing back at the Wonder, with the Foundation Stone as their bench (so a builder fetches from
the chests round the stone and empties his bag there). Only stage I fills a foundation (later stages stand on the stage
below), only the last stage levels the ground round it, and no part hands a blueprint back. Once a second the Wonders
check their stage: when every part's site is done or gone, the next stage's sites are made for the Wonder's builder, or
the Wonder is finished. The check reads only saved data, so the order holds through a restart at any tick. While
`wonders` is off nothing moves on; it all waits, saved.

**Our Wonders' designs** (35.9 on) are drawn whole in `tools/blueprints/wonders.py`, one function each, stage by stage
on a staged build that refuses to draw over an earlier stage's block; `wonder_stages()` cuts each stage into parts and
`wonders.py` writes the Wonder's data file (`aliveworkplace:great_cathedral` is
`data/aliveworkplace/wonders/great_cathedral.json`, `aliveworkplace:lighthouse` is `.../lighthouse.json`, `aliveworkplace:great_library` is `.../great_library.json`, `aliveworkplace:observatory` is `.../observatory.json`, `aliveworkplace:great_wall` is `.../great_wall.json`, `aliveworkplace:hanging_gardens` is `.../hanging_gardens.json`, `aliveworkplace:colossus` is `.../colossus.json`) with those parts, so the two always agree. Each stage is checked
as it will stand, on the stages before it (nothing held up by nothing), and every stage leaves the site tidy, since the
village lives beside it for days.

**Powers and upkeep** (35.8): the rest of the mod asks `Wonders.power(level, hall, type)` (the first) or
`Wonders.powers(level, hall, type)` (every Wonder that grants it, its own village's first, then its realm's): a lookup
in a cache worked out again whenever a Wonder is finished, damaged, repaired, forgotten or loaded, and when the files
are read again. It answers only for finished Wonders that aren't damaged, and nothing while `wonders` is off. A realm
power reaches the village's realm (or its caravan partners). A Wonder's item adds its type with
`WonderPowers.register(type, reader)` and reads the numbers where the power acts. The upkeep count goes one dimension
at a time, a Wonder at a time, 4,096 blocks a tick, and starts over if part of it unloads; it isn't saved, so a
restart only begins that day's count again. Repairs are ordinary repair build sites, one per part with holes, pointing
back at the Wonder with its stone as their bench; the first goes to the builder that looked, the rest in its queue.

## Switches

All behind the 2.0 switch: until 2.0 is finished they are off and hidden, whatever the config file says.

| Switch | Default | Off means |
|---|---|---|
| `wonders` | on | no new Wonders; finished ones keep their blocks, everything stays saved |
| `wonderLegends` | on | each Wonder's Legends are replaced by their stand-ins |
| `wonderServerLimit` | 0 (0 to 64) | how many villages may each build the same kind; 0 is no limit |
| `wonderCaps` | 0, 0, 1, 2 | file only: Wonders per village by rank (each 0 to 8; a short list is filled from the defaults) |
| `disabledWonders` | empty | file only: Wonder ids that can't be started (`great_cathedral` or `pack:id`) |
| `wonderCrew` | 12 (1 to 32) | the most builders on one Wonder's crew, its lead included |
| `wonderStanding` | 90 (50 to 100) | the share (%) of a finished Wonder that must stand for its powers; under it, it's damaged and repaired |

`builderScaffolding` (35.6) is on the Builder's pages.

## Saved data

A new saved file per dimension, `aliveworkplace_wonders`, with a list of `sites`: each with its `id`, `wonder`, `hall`,
`owner` and `ownerName`, `stage` (default 1), `begun` and `finished` (absent until it is). A world saved before 35.2 has
no such file and loads unchanged. 35.4 adds, all optional: `placement`, `stone`, `parts` (the current stage's build
sites) and `builder` on a site; 35.5 adds `called` (default false: every builder called to the crew); 35.7 adds
`dedicated` (default false: whether the current stage's goods were dedicated) and `delivered` (default empty: the goods
handed in at the stone for it, each its line, item, potion and count). A site saved before 35.7 with its stage's parts
down counts as started, so a Wonder going up keeps going. 35.8 adds `standing` (default 100: the share standing at the
last count), `counted` (default 0: that count's day), `damaged` (default false) and `festival` (default 0: the day of
its finishing festival); a Wonder saved before 35.8 reads as standing whole, its powers on. A crew member's
job is the ordinary saved builder job (as a helper), so a crew comes back together after a restart; the columns are
worked out again; `wonder` and `no_foundation` on a build site (written only when set, so an ordinary
build saves exactly as before).

## Items, blocks, jobs, commands

- `/workplace wonders call`: the stone's [Call every builder], for the owner or an operator within 8 blocks of it.

- `/workplace wonders`: every Wonder on the server, one line each: its name, village, owner and stage ("stage 2 of 3,
  …", "finished on day 12" or "finished on day 12, damaged (84% stands)"). While `wonders` is off it says "Wonders are off."

- **Wonder Plan** (`aliveworkplace:wonder_plan`): drawn up on the Wonders page; not crafted, not in the creative tab.

- **Foundation Stone** (`aliveworkplace:foundation_stone`): laid by a Wonder; not crafted, not in the creative tab,
  drops nothing.

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
- Stage I waits for its goods too (35.7): the design note's "the goods are taken and the stage begins" holds for every
  stage. `WonderStages.start` (commands and tests) still starts a stage at once.
- The dedication's state is the moment the goods are taken (35.7): the stage begins then, and the gathering, the Bard
  and the guards' line follow for 30 seconds without being saved, so a restart only cuts the gathering short and a
  stage never waits on villagers walking.
- The bell is heard, not rung (35.7): a rung bell sends every villager into hiding and the guards to a rally.
- Goods handed in are kept by the stone apart from the chests (35.7), so a cancel can give them back; they go to the
  Wonder's owner, whoever brought them.
- "The shortest line" for the daily quest is the one with the least of it in for its size (35.7); potion lines are left
  out of it (a quest's item can't name a potion's kind), and crafters fill only lines that name one plain item.

- A damaged Wonder is repaired whatever `builderRepairs` says (35.8), as what threats took is: its powers wait on
  it. Below the line, the builders' ordinary upkeep still mends a finished part's holes as for any building.
- A block put back as something else (a player's dirt in a wall) doesn't stand, and the builders never take a player's
  block out (35.8): the owner puts it right. A Wonder in the village's colours (the Stadium's seats) also stands in the
  colours it was drawn in, so a new banner doesn't damage it.
- The Village Map's gold banner is a yellow one (35.8): a map's banners come only in the sixteen dye colours.

## Known limits

- The Great Cathedral's, the Lighthouse's, the Great Library's, the Observatory's, the Great Wall's, the Hanging
  Gardens' and the Colossus' files ship (35.9, 35.11, 35.13, 35.17, 35.15, 35.19, 35.21), so they are on the Wonders
  page behind the 2.0 switch; their powers come with 35.10, 35.12, 35.14, 35.18, 35.16, 35.20 and 35.22, and the
  Stadium's file with 35.23. Everything is behind the 2.0 switch.
- A lone builder can't reach blocks far over anywhere he can stand: the folly's finial lantern, five blocks over the
  inside of its closed dome, is skipped until scaffolding (35.6).
- Drawing up the plans doesn't reserve the Wonder: two plans of one Wonder can be drawn; the caps hold when it's begun.
- A stage's `parts` are checked to exist when the plans are drawn up (the log names a missing one), not when loaded.
- Only `mood` and `beauty` act yet; the other power types are declared, their numbers kept and `Wonders.power` already
  answering for them, until their Wonder's powers item (35.10 on) reads them.
- Goods handed in come back as the plain item (35.7): a renamed flower comes back unnamed. A tag line's name ("Flowers",
  "Saplings", "Banners", "Berries") comes from the language file; a server owner's own tag shows as `#namespace:path`.
- The stone's status is its chat text, as in 35.4: there is no separate screen.

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

GameTests in `WonderStageGameTests` and `WonderSiteGameTests` (35.4): the parts at their offsets in all four turns and
mirrored, stage II never before stage I; handing the plan over lays the stone at the front corner (turned and mirrored
too), begins the Wonder in the chronicle and queues stage I with the stone as bench; refused unplaced, with Wonders off,
to a builder without a bench, of another village, and for a second of the same kind; the stone's status with empty and
full chests; the stone refused to a stranger and to a plain break, the owner's sneak-break abandoning it with the plan
back; one real builder raising the folly from the chests round the stone in each of the four turns, plain and
mirrored (8 tests), no stage II block during stage I and stage I complete when stage II begins, ending as drawn (but
the finial lantern, 35.6); a save and reload mid-stage II of the Wonders, every build site and the builder, finished as
drawn; old saves unchanged. Showcase scene `wonder_site`: the plan placed, the stone going down, stage I under way and
the stone's status.

GameTests in `WonderCrewGameTests` (35.5): eight builders raise the folly with no two on one column on any tick, no
structure block up with the drawing's block under it missing, all eight on the crew at once, and done in at most a
quarter of the time one builder alone takes on the same folly (measured in the same test, first); a save and reload mid-stage II with every
member, the crew back together and the folly finished as drawn; `wonderCrew 3` holds the crew to 3, nobody joins with
Wonders off, and a member lost mid-job is replaced; a builder with its own build queued joins only once every builder is
called, a stranger can't call, the stone names the crew and offers the call, the highest-level builder leads; the
lead killed mid-part hands the part to a living member, who leads it, and the crew finishes the folly as drawn.
Showcase scene `wonder_crew` (group Wonders): ten builders on the folly.

GameTests in `WonderGoodsGameTests` (35.7): the folly's stage II (three lines) waits through one line, two, a line one
short and the wrong potion, each line posted for the stone, then is dedicated taking exactly its goods (the spares and
the water bottle stay), in the chronicle, its posts down, never twice; a porter carries the eight bread stage I lacks
(and no more) from the storehouse to the stockpile; a chef cooks them from the wheat by his stove as a stock order; a
click on a Storehouse's board hands a line over (only what it lacks) and names the Wonder and the Chef; a stranger hands
flowers in at the stone (dirt is refused, a full line takes no more) and the owner's sneak-break gives the goods back
while the chest's stay; at night the stage waits and the stone says it's for the morning; the dedication puts the guard
in line, the Bard by the stone and brings a villager off work, and the stone broken in the middle ends it with nothing
given back; the goods and `dedicated` survive a save and load, a 35.5 site reads with the defaults; with Wonders off
nothing is handed in, posted or dedicated; the stone shows every line with "no Florist in the village" and every
sentence has its text; the hall's quest asks for the line furthest behind. The 35.4 and 35.5 tests put each stage's
goods in the stockpile (`WonderGoodsKit`). Showcase scene `wonder_goods`: the stone's lines half filled, the porter
bringing the books, then the dedication.

`WonderBlueprintGameTests` checks every one of our Wonders' designs (35.9 on): its file loads with the mod's own
loader, every part loads and is at most 48 a side, its name, description and stage names have their lines, the parts
aren't in the Blueprint Table, no part sets air over an earlier stage's block, the block count is within 10% of the
roadmap's (14,000 for the Cathedral, 5,000 for the Lighthouse, 16,000 for the Great Library, 9,000 for the Observatory, 11,000 for the Great Wall, 15,000 for the Hanging Gardens, 9,000 for the Colossus), and it uses no retired job block, no calcite and nothing a player can't get (still water counts: a builder pours it from a bucket).
The Stadium's file and its Healing Machine need Cobblemon, so its same checks run in the compat tests
(`StadiumCompatTests`, 7,300 blocks), with its requirements and goods as listed, its nurse's booth, Training Posts and
floodlights, and its stages built in a village's banner colors (blue and red for a blue banner with a red stripe, the
field's lines still white; red and white for a village without one). `StadiumGameTests`, without Cobblemon: its file
is skipped quietly (not on the page, no mistake logged), and the colors style and how a banner picks it.

Showcase scenes: wonders_page (group Wonders: the folly's card, then the placed plan's see-through tower) and
wonder_gallery (each of our Wonders placed whole, from the front and from behind; the Lighthouse with its sea flooded
in, so the mole and the jetty stand in the water; the Great Wall's front is its village side; the Hanging Gardens' falls are on their east side; the Colossus faces its front, down the road through its arch; the Stadium needs Cobblemon, so the scene is filmed with it; its card shows four of the stills, the rest are in its gallery).

GameTests in `WonderFinishedGameTests` (35.8): the folly raised through its plan has its test mood (+4, "proud of the
Test Folly" among a villager's reasons) and beauty (+2) on; 15% of it broken, the next day's count turns them off, the
stone, the hall's card, the chronicle and the owner say "damaged", the village's builder repairs it with
`builderRepairs` off and the powers come back; every player on the server is told (one 5,000 blocks away too), the
festival is that evening with the Wonder's bigger show (rockets from its top), the village's and its caravan partner's
chronicles record it, the Village Map has its gold banner and no part's; finished Wonders and their upkeep survive a
save and reload (the powers from the loaded copy), old saves read as standing whole; `wonderStanding` 50 against 90,
weathered and waxed copper standing as drawn, Wonders off; every power type registered by id, bad numbers refused, a
type registered later read from a file. Showcase scene `wonder_finished` (group Wonders): the folly's time-lapse, a
crew of ten, ending in its festival's fireworks.
