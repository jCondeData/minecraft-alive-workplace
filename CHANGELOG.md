# Changelog

Everything that changed in Alive Workplace, newest first. Each version has up to four parts:

- **Added**: new jobs, blocks, builds, screens and settings.
- **Changed**: things that now work differently. Read this part before updating a world you care about.
- **Fixed**: bugs that are gone.
- **Dev** (or **Tests**): checks and tools behind the scenes. Nothing changes in game; players can skip it.

Versions are written `X.Y.Z`; the jar (and Mod Menu) adds the Minecraft version, as in `1.0.0+1.21.1`. The mod is made
never to break a saved world: updating keeps your builds, your villagers' jobs and your settings. Versions before
1.0.0 were pre-releases; their notes are kept below as the mod's history.

Found a bug or have an idea? Open an issue: https://github.com/jCondeData/minecraft-alive-workplace/issues (the form
asks for the steps, `latest.log` and any crash report).

## Unreleased

### Fixed
- Builders (with Create): a scanned or uploaded Create fluid tank, spout or item drain is now built empty, as the blocks are placed fresh; before, each copy came out holding the scanned block's fluid (a bucket of lava, honey, chocolate or a potion) for free. Other mods' tanks are built empty too.
- Quests: a step that asks you to defeat several of a creature now reads in the plural ("Defeat 2 Zombies", "Defeat 6 Vindicators") in the quest journal, the tracking bar and `/workplace quests`; before, it read "Defeat 2 Zombie". A modded creature with no plural reads "Defeat Grub ×2". `/workplace quests` now says "That quest isn't open anymore." in American English.
- Festival Cup: [Watch] on a player bout (the Cup page, the Arena's notice board or `/workplace cup watch`) now puts you in Cobblemon's spectator view of the battle; before, it always answered "You can't watch that bout right now."
- Festival Cup: the Grand Cup's Species and Item Clause now hold for every team that goes. Your second Pokémon of one species, or a second one holding the same item, stays home and the Cup page says which clause keeps it there; a delegate's or host trainer's Expert or Master team no longer has two Pokémon holding the same item.
- Festival Cup: the Workers' Cup banner now flies a gold cup on brown, its theme's colors; before, it flew gold on red, the same as the Grand Cup's.
- Waystones: a village whose Waystone Plaza is gone (demolished or forgotten) now leaves the waystone network at its hall's next round, even when another village's plaza stands nearby; before, a neighbor's plaza kept it on every hall's Map for good as "its waystone is broken".
- Blueprint Table: after a job's config switch changes in a running game (physicians, well keepers, firewardens, jewelers and the like, or disasters and village fires turned on or off), the Table's list, its job search and a build's Jobs line all follow the new setting at once; before, the list kept the old jobs until a restart and could disagree with the Jobs line under the build.
- Cobblemon: the Chef no longer churns EXP Candies or breaks them down. He cooks EXP Candies XS from sculk and honeycomb and keeps 16 in the chests by his stove, instead of cooking the store's XS into bigger candies and splitting them back again, and he never breaks a player's EXP Candy XL into smaller candies. A recipe that also wants other makings still uses it: four Kelpsy Berries, four Honeycombs and an XL make three Mighty Candies.
- Spruce style: with Carved Wood installed, a builder now puts up the two spruce lanterns on the flower boxes by a cottage's door; before, he set them before the flower boxes they stand on, couldn't, and finished the cottage without them.
- Noble's Ball: in a village whose Manor I or II was placed mirrored (the style screen's mirror button, or a Steward's mirrored street), the guests now gather in the Manor's hall; before, they gathered on open ground about a Manor's width outside it.
- Sieges: dead bushes, grass, ferns or snow at a wall's foot no longer stop the raiders. The desert husks' sand ramp buries them and rises to the top, and the warband climber's ladder goes up through them. The buried plants are gone after the raid (nothing drops); your flowers, saplings and blocks at a wall's foot still turn ladders and ramps away.
- Class chatter: villagers now talk of wanting wine or new clothes when their class asks for them and they haven't had any lately, of the Gazette the week they read it, and of the tavern near their home (three lines each); before, only a household's rise and fall had lines.
- Village-wide reach: a worker whose block stands in a City Plan zone painted in the plan's corner, past the Village Hall's radius, now reaches the whole village, as everywhere else in it; before, a field or site across the village was refused as if the worker were in no village.
- Mourning: a villager who dies inside the village but far from the hall up a hill is now mourned like any other (her family grieves, players in the village are told, the chronicle gives her job and age, the family book dates her death); before, only deaths within the hall's radius in a straight line counted. Players are told when they stand anywhere in the village.
- Builders now plant a scanned ripe pitcher crop as a young one-block crop, the way you plant a pitcher pod; before, it came out full grown and broke into a free pitcher plant.
- The Village Hall's Classes tab and page now count only the households inside the village's circle; villagers out in the corners beyond it were counted too.
- The Village Hall's What next? tip for a plain diet said "Keep 3 kinds of meal in the store", the varied diet's number; it now says 2.

## 0.150.0 — 2026-10-26

### Added
- Refresh an old house to its latest look: the Village Hall's Repairs page shows **Refresh to the latest look** on every finished build whose blueprint has been redrawn since it was built (the Stone House's fixed decorations, say). A click shows what changes ("New: 12 decorations, 3 furniture pieces"), a second sends the nearest free Builder, who takes away only what the old drawing left (floating or invisible pieces too; their items go to the chests) and puts in the new pieces. Chests and their contents, beds villagers sleep in, job blocks and your own blocks stay. No Builder free: it waits for one. Short of materials: the Builder asks as for a repair.
- Work anywhere in the village, and far outside it: builders, miners, farmers, lumberjacks, orchard keepers and berry breeders take a build, quarry, field, tree farm, orchard or berry plot anywhere in the village their block stands in (the Village Hall's reach and the City Plan's zones), however big, and outside the village up to 128 blocks from their block (was 48, 64 for quarries). Each reach is a setting (Build, Quarry, Field, Tree Farm, Orchard and Berry Plot Distance, 16 to 256); a spot too far says so: "That spot is outside Mapleridge and 140 blocks from the Miner's Bench the miner works at — keep it within 128." Upkeep and the Steward's plans reach the whole village too.
- Village Hall: Bed ▾ in the People table opens the village's beds right under the villager's row. Free beds come first with where they are (the house and which bed, or "free · 14 blocks east" from the hall), then the beds others sleep in ("swap with Jem"), and "Short of beds? Plan a house ›" when none is free. A click makes the bed theirs at once (the old one freed, a swap moves both), the table and census show it straight away, and they sleep there that night.
- Waystones: caravans and settlers travel by waystone between two villages that both have one: they walk to their stone and step out at the other's (with the mod's own sound and particles), a 30-second trip; if a stone is missing or broken they walk the road as before.
- Waystones: the Village Hall's Map now lists your villages that have a waystone, each with its distance and direction, and whether you have discovered its stone, not yet, or it is broken.
- Waystones: with the Waystones mod installed, the Steward proposes a Waystone Plaza by the Village Hall, in the village's own style (plains, desert, savanna, snowy or taiga); its waystone is named after the village, renamed with it, and put back by the builder if broken. Config `villageWaystones` (on).
- Village Hall: the hall's screen has a sidebar: the village's name and today's date over ten labeled buttons (Overview, People, Homes & Food, Buildings, Defense, Quests, Trade, Calendar, Laws & Realm, Map). The section you pick fills the right side, an empty one says so plainly, the Overview shows the village at a glance with what it needs next, and the hall opens on the section you used last. Every page and button of the old screen is still there, under its section (40.1).
- Village Hall: the People section is now a table of everyone in the village: Name, Job, Level and Mood, with "8 · 6 working · 2 jobless" on top and a scroll for big villages. Mood is a word (Happy, Content, Unhappy, or "No bed" in red when that's why). Click a row to open that villager's details right under it (traits, what they're doing, what they need, their mood and why, their class and how to rise), with Job ▾ (change or give them a job) and Bed ▾ (the village's beds); click again to close it. Children and builders are listed too, marked as such (40.2).
- Village Hall: Job ▾ in the People table opens a list of every job right under the villager's row. Jobs with a free work block in the village say how many ("Mason · 1 free Stonecutter"), the others are grayed with the block to place ("place a Lectern first"), and the Builder and "No job" are there too. The price by their level is shown on top before you pay; click a job once to see the offer, again to pay. Their trades and level are lost, a busy builder hands his build back, a jobless villager gets a free block for nothing, children and nitwits can't, and game rule `workplaceRetraining` off hides the button (40.3).
### Changed
- Text: every sentence a player reads is now in American English: Smith's Draught is now Smith's Draft, the den-helpers and bean-farm settings say Defense and color, and the questbook, guide and wiki say gray, armor, Jeweler, canceled, traveler, leveled, curb and story (a floor). Item and block ids, saved data and lang keys are unchanged, so worlds and data packs keep working.

### Fixed
- City Plan: the server now refuses a zone whose name is blank, over 32 letters or formatted, or whose style isn't one the plan lists (a modified game could save anything); a zone saved earlier with a style that no longer exists is built as drawn and can still be renamed.
- Colony Charter: a spot just past the ring is refused with a number on the refused side ("at most 1024 blocks ... (this is 1025)", "at least 256 blocks ... (this is 255)"), never the edge itself; the cap reads "has founded the 1 colony a village may" with `coloniesPerVillage` 1, and at 0 says the server lets no village found colonies (B447).
- Create: a worker whose machine is broken while they walk to it turns back with what they carry and shows "the Millstone is gone: bringing the load back to the store", and a Mechanic says "the Millstone is gone"; before, they said "working the Air", "bringing what the Air made to the store" and "seeing to the Air" (B506).
- Wonders: `/workplace cancel` now ends a Wonder whose stage is waiting for its goods, giving back the plan and the goods handed in for that stage. The Foundation Stone's screen has an **[Abandon this Wonder]** link that puts the command with the Wonder's id in the chat (before, only a part's id worked, and a stage waiting for its goods has no parts yet, so the answer was "No build with that id.").
- Camp Cook (Cobblemon): she stops at the 16 Poké Snacks (and every other dish's count) her menu keeps. A dish out of the pot carries what it was cooked with and only plain ones were counted, so from 15 she cooked on to 18 until the makings ran out (B465).
- Gem Grower (Cobblemon 1.8): she ends on 8 Blank TMs at most. Cobblemon's recipe makes 2, so from 7 she crafted once more and held 9; now she crafts only while one more craft still fits under the 8 (B490).
- Classes: switching classes on in an old village now seeds, counts and tells only the villagers and players inside the village's circle (the hall's radius), as the census does; before, a villager or player in the square's corners, outside the village, was given a class by that hall, counted in its "found their places" line, and sent the chat line (B548).
- Villages: couples, the Book of Families, mourning and the dawn class check now go by the village's circle (the hall's radius), as the census does; before, a villager in the corners of the square round the hall, outside the village, could be paired as its couple, counted as courting, written into its family book, grieve its deaths and be counted at its dawns (B549).
- Villagers: a Mechanic, Conductor or Furnicrafter in a world Create (or CobbleFurnies) was taken off now loads as a jobless Novice who doesn't trade, keeping her name and clothes; before, she kept her old job's trades and level (B542).
- Wonders: the Observatory's purple bed in the Seer's lodging is a whole bed now, its head against the south wall (its halves were swapped, so no one could sleep in it) (B537). The Great Library's map room lantern no longer stands on the lectern, where it popped off as soon as a book went on; it stands on the reading table beside it (B538). The Hanging Gardens' paths no longer run under fence posts and the ramps' sandstone walls, which the game turned to dirt patches; those cells are grass or coarse dirt now (B539).
- Builds: no lantern, candle or torch stands where the game drops it, and no dirt path lies under a block that turns it to dirt. The Map Room's two lanterns are off its chests (on the cartography table and a bookshelf), the Smithy II's lantern is on its coal bin, the coven hut's candles on a bookshelf beside the loot chest, the prisoner cage's lantern on a corner log of its lid, and the plains, savanna, snowy and taiga Trade Halls' roof lantern hangs under its little roof. The paths under tents, beds, fences and crates in the bandit camps and forts, the war camp, the raiders' stockade, the caravan wreck, the hermit's hut, the traveler's camp and the Farmsteads are coarse dirt or grass. The modded-library copies of the Healing Center III and IV now carry B487's door fix too (their old sill stair over the path was left in them). The blueprint check catches all three kinds of mistake from now on (B537, B538, B539).
- Builds: villagers now get through every door. The Healing Center III and IV's garden door and the Pokémon Center Mountain Lodge II's back door lost the old window sill in front of them, Royal Hall II's east door opens onto a way along the wall instead of the dais's end, and the Royal Hall's vault door sits a block up, level with the dais, with a step down into the vault (B487).
- Scaffolding: with none in the chests, a builder's shortfall now shows on the Storehouse's requests board and the Village Hall while the build carries on (before, it never showed); it goes away once there's enough in the chests (B499).
- Hauler Golems: a golem whose only Storehouse is walled in (or across water) no longer stands holding its load for good; it puts the goods back into the nearest Drop Box and is free to haul again (B463).
- Village Hall: in the People table, scrolling down an open villager's row to reach its Job ▾ or Bed ▾ list no longer blanks the row out once its name scrolls off the top; the details and lists stay in view and can be clicked (B546).
- Village Hall: a villager's life story can be opened from the People table again, with a new Story button on their open row (it was only reachable by the old list's shift-click). The open row no longer shows the old list's click hints, and the count says "1 child", not "1 children" (B547).
- Steward plots: the same blueprint is now kept 24 blocks apart as promised, measured centre to centre; a finished Well 25 blocks off no longer refuses the spot (B470).
- Walls: with no wall line drawn, the Steward's own line now runs 4 blocks outside the zones on every side; the east and south sides were 5 out (B510).
- Visible caravans: a carter or pack llama killed by /kill or the void no longer drops leather, a chest or anything else (B431); the party is only a sight and the goods still arrive.
- Caravan trade: a village whose treasury is full is no longer paid emeralds that vanish (B430). The buying village buys only what the seller's treasury can still take and the rest goes home ("Thornholm's treasury is full and can't take our money: their caravan took 16 × Oak Log home"); a sale booked later into a treasury that filled up meanwhile sends what it can't take back to the buyer.
- Scan Tool: a build saved with the tool renamed in an anvil keeps the name exactly as typed, apostrophes and accents too: "Jesse's Café" is no longer "Jesse S Caf" in the saved line, on the blueprint or at the Blueprint Table (B458). Uploaded files keep theirs the same way ("Zoë's Hut.litematic"). File names and ids are unchanged; builds saved before keep their old names.
- Builder: a Mailbox scanned while it held mail is now built showing no mail and giving no comparator signal, like the empty mailbox it is (B536); it used to look and signal full with nothing in it.
- Village Hall: Job ▾ now counts free work blocks in the plural, "Mason · 2 free Stonecutters", "Librarian · 2 free Lecterns" (B535): every vanilla and mod job block, and the Cobblemon and Create ones, has its plural.
- Builder: a take-down canceled with /workplace cancel now says "Canceled the take-down of …", and its blueprint, handed back plainly as the line says, carries on taking the building down instead of starting to build it again over the half-taken-down one (B526). Its tooltip says so; moved to a new spot it's a plain blueprint again.
- A hopper under a Mailbox no longer pulls the owner's mail out, and a hopper, dropper or Create funnel above or beside it no longer pushes items into the inbox (B525), so nobody can empty or fill someone's mailbox with automation. Mail still arrives through the post and the owner, friends and operators still take it out on the mailbox's screen; mail already in a mailbox stays.
- Builders now place a block every `workplaceBuildDelay` ticks times their level's share, as the guide says (B515): each block used to take one tick more, so at the default delay of 8 a Novice took 9 ticks a block and a Master 4 instead of 3, and a Master built only about twice as fast as a Novice, not two and a half times.
- A helping Builder now puts back the materials its next stretch of work no longer needs when it goes to the chests (B519): near the end of a crew build, a helper fetching the lanterns kept the walls' last planks and cobblestone in its bag, so it carried more than the build still needed.
- Raid helpers now help in a real raid (B476): when Raid Dens counts the player as joining the raid, their hired trainers find that raid and their Pokémon wear its boss down to a quarter of its health, and when the raid is lost they head home right away instead of staying hired for the rest of the day.
- Festival Cup: Sign up no longer puts a player's name down before sign-up opens (B491); it says "Sign-up for this Cup isn't open yet: it opens after the festival on day N", as the Cup card does.
- A Berry Wine bought from a Vintner is now pressed the day it's sold (B443): it shows "Pressed on day N" and waits its 3 days before a Journeyman makes it Vintage Wine, instead of counting as old at once.
- Builders no longer hand out a scanned block's harvest or look for free (B493-B496): a ripe field is planted at age 0 (wheat and other crops, stems, nether wart, sweet berry bushes, cocoa) and cave vines without berries; a lectern or jukebox built empty no longer shows a book or disc, also after a reload mid-build; a furnace, smoker or blast furnace scanned burning is built cold (campfires stay lit as drawn); and a bee nest or hive is built with no bees inside. Shipped farm blueprints now come up as young crops that grow. A crop that grew or a furnace lit since still counts as built.
- Text: names that start with a vowel now get "an": the blast furnace's tooltip says "takes it as an Armorer", the hall says "Bram is now an Armorer", and the same for retraining, guards ("an Archer"), inn travelers ("an Apprentice"), the Fossil Scientist ("reviving an Omanyte"), the Daycare Keeper ("an Eevee Egg"), the Pokémon Trader ("Comes in an Ultra Ball") and the raid helpers' cheer (B505).
- Text: more lines say "an" before a vowel: the Pokémon Trader's "Not an Ice type", the Professor's "You bought an Ice Stone", the Golem Smith's "an Iron Golem", and every line that names a building ("An Inn in the village", "Have the builder put up an Apiary Garden", the Steward's "build an Inn" and "as an Inn", a wedding "in an Abbey", the disaster tips and "An Armorer answers it", the Mechanic's "putting back an Iron Door") (B523).
- Text: the Steward's desk now reads 'Declined: "Give 1 villager a job" won't be proposed again for 3 days'; before, it said "Declined: the Give 1 villager a job won't be proposed" for jobs and research proposals (B475).
- Text: the Festival Cup page says "with 1 bye for the top seed" and a full Town "can hold 1 Mythic Legend"; before, "1 byes" and "1 Mythic Legends" (B492, B504).
- Text: two config tooltips are now in American spelling: the hall's "Defense page" and "a color" (B507, B518).
- Builder: a take-down canceled with `/workplace cancel` now gives its blueprint back still placed over the building, as the cancel message says, so handing it back carries on; before, it came back unplaced and had to be placed again (B513).
- Builder: a take-down sneak-handed to a busy Builder is now announced as one: "Builder will take down Test Hut after … It needs no materials."; before, it said "will build" and asked you to stock materials for it (B514).
- Builder: a block the builder had already laid that is broken mid-build (by a player, a mob or an explosion) is now put back before the build is called finished; before, the build ended at 100% with the hole still there and, with Ask Before Repairs on, asked you whether to repair a building it had just reported done. If the block keeps getting broken, the builder goes back for it twice and then counts it as skipped (B508).

- Builder crews: when a helper Builder dies mid-build, the lead and the rest of the crew now finish the build, the steps the helper had taken on included, and only its bag drops; before, the lead's whole build was canceled half-built, a copy of the blueprint dropped where the helper fell and you were told your builder died (B516).
- Village Hall: a hall whose quest board holds a quest of a kind this version doesn't know (a server rolled back to an older jar after a newer one added a kind) now loads with everything else it keeps, leaving out only that quest; before, the whole hall came back empty: treasury, rank, research, families and chronicle (B509).
- Walls: when a wall piece's builder dies (killed in a raid, say), the half-built piece now stays open and the next free builder finishes it where it stands, also after a restart; before, the site was forgotten, the next round laid a second tower on the first one's roof and neither ever finished (B488).
- Jobs: a farmer, miner, lumberjack, orchard keeper or berry breeder handed a Field Marker or Quarry Marker for a spot too far away now names the block they work at: "That field is 63 blocks from the Composter the farmer works at — keep it within 48."; before, every one of them said "from the builder's Blueprint Table", a table none of them has (B453).
- Builder: when a Tinkerer can't make a part yet, the shortage line now names the raw metal the chest is short of: five raw iron and a coal for iron bars read "short of 1 Raw Iron", and coal with no ore "short of 6 Raw Iron"; before, both said "6 Deepslate Iron Ore", an ore you only get with Silk Touch. Gold and copper read the same way, and any job with two recipes for one thing names the one that uses what's in the chest (B489).
- Builder: a build placed mirrored and turned a quarter now gets its item frames; before, the frame faced away from its wall and was left out without a word, as in all three Guildhalls (B439). Paintings two blocks wide now hang where the blueprint hangs them in every placement (they could be left out even unturned), and an armour stand faces the way it was scanned instead of always south. Frames and paintings already hanging stay as they are.
- Builder: the mod's own blocks now turn with a build placed turned or mirrored; before, the Storehouse, Village Hall, Mailbox, Shop Counter and Grave blocks kept the way they faced in the blueprint, so in a Storehouse turned a quarter the Storehouse block faced north instead of the way the building faces (B448). A turned build finished, or started, before this update keeps its blocks as they stand: the builder counts one facing the old way as built and never breaks it, so the hall's records, the mail, the shop's list and a grave's items stay put.
- Builder: a build placed mirrored (with the mirror button, or a Steward's street mirroring its houses) now gets its double chests as double chests; before, a chest facing east or west came out as two single chests, as in the mirrored Tinker's Workshop, Winery and Nether Gate II, and the ghost preview and repairs showed it that way too (B438).
- Disasters: a worker held back by a disaster now reads "held back by a disaster in the village" in his status line and the hall's list, no longer with a capital A mid-sentence (B497).
- Mega Shrine (with Mega Showdown): a villager busy at his job near the shrine, such as a farmer at his fields, no longer gets the show's +3 mood and "watched a Mega Evolution at the shrine" though he never came to watch; only villagers off work who gather for the show get it (B420).
- Mega Shrine (with Mega Showdown): a shrine whose pedestal was broken no longer holds the festival show at the empty spot (no Mega Evolution, no chat line, no mood for the crowd); put the pedestal back and the next festival's show is held again (B421).
- Mega Shrine (with Mega Showdown): restarting the server during the festival show no longer holds the show a second time (the trainer Mega Evolving again, the chat line again, and villagers who came late getting the +3 mood); each village holds one show per festival day, restart or not, and the next festival holds its own (B501).

## 0.149.1 — 2026-10-25

### Fixed

- A shop's takings while its owner was offline are no longer lost when CobbleDollars is gone from the server by the time they join (taken out of the pack, or broken by an update): they're paid in emeralds at the server's rate (1,234 CobbleDollars: 12 emeralds, "your shops took 12 emeralds"; it said "took 0 emeralds" and paid nothing), and what doesn't make a whole emerald stays owed for a later join, in CobbleDollars once they're back. A fortune is paid an inventory of emeralds at a time (B416).
- With Create, the station no longer puts the village train together while its builder is still finishing the train's site: the blocks went into the train under the builder, who then stood stuck at the station, and the chronicle never said who built it. The train is put together once the site is done, after the chronicle's "built the village's train" line (B483).
- Builders no longer hand out what a scanned block held (B429, B434, B449): a lava cauldron is built as an empty cauldron (no free lava bucket), a ready composter empty, a full beehive without honey, a chiseled bookshelf no longer shows books it doesn't hold, and a grave built from a scan is empty instead of holding the dead villager again. Water cauldrons in the village's own buildings still come full.
- More lines count one in the singular (kin of B413): the titles over workers' heads ("Netherworker · 1 expedition", "Teacher · 1 pupil taught", "Lumberjack · 1 tree" and eleven more) and their counting state lines (B444); "1 villager came home", a golem "carrying 1 item", mail tracking "1 item", the coven's and the Gray Cough's "1 villager", the bank's "Your account: 1 emerald" (B445); a Wonder's "in 1 village on this server" (B461); the Habitat Keeper's "1 lure spot kept, 1 Saccharine log known", and CobbleDollar sums over a thousand read "150,000" (B466); the City Plan's "1 cell" and "(1 zone)" (B469); the Steward's "1 meal short", "1 household wants a grander home", "1 topic is open", "1 guard short", "Last raid: 1 day ago" and their kin (B471, B472); the Cup's "on 1 day", and the crafters' shortage lines name items in the plural after a count ("short of 4 Oak Logs to make 3 Barrels", B426).
- The Village Hall card no longer says "Bram would like to retire: the village needs a jobless villager to take over" while a jobless grown villager is there and Bram waits for another reason (he holds a building site, quarry, daycare or parcels, or another villager retired under 3 days ago): the hall checks the line every round (B442).
- The Settler's Wagon camp's chest of supplies can be opened where it stands: it moved to the wagon's open tail, with two steps up to it (it was wedged between the hay and the pumpkin under the cover, so the wagon had to be broken to unpack). The Colony Camp's wagon barrels moved to their tails the same way (B424).
- A found Blueprint (or Wonder Plan) in the off hand, the main hand empty, now unlocks its build when right-clicked on the Blueprint Table and says "The village's library has a new blueprint", the same as from the main hand; before, the Table just opened (B419).
- A paid raid helper stays hired when its chunk unloads for a while (the player logs off by the den, or the server restarts): the hiring used to vanish silently, the money gone and the helper neither following nor walking home once it loaded again. A hiring now ends only on a won or lost raid, the raid gone, a day passing or the switch turned off, and helpers away when it ends are still sent home (B415).
- The Defence page's **Hire a raid helper** button now shows the price in the money the click takes: "500 CobbleDollars a helper" on a server with CobbleDollars (it said "5 emeralds a helper", then asked for 500 CobbleDollars), "5 emeralds a helper" without (B414).
- A hand-edited server config with one value of the wrong kind (`"festivals": 0`, `"threatLevel": "high"`) no longer resets every setting: that value takes its default and the rest are kept; a file that isn't JSON at all is kept as `aliveworkplace.json.bad` before the defaults are written (B411).
- A Librarian one leather short of a builder's last bookshelf now says "Can't be made yet: short of 1 Leather to make 1 Bookshelf" instead of "short of 4 Rabbit Hide": the shortage line names what the chest holds too few of, and only names what it's made from when that's at hand (logs for planks) (B409).
- A server whose `config/aliveworkplace.json` holds an empty format number (`"configVersion": null`) or an empty entry in `"expansionsOpen"` (`[null]`) now starts: the empty value is read as a missing one and the rest of the file is kept, instead of the game crashing at start-up. Any other file the mod can't make sense of gives the defaults rather than stopping the server (B457).
- A Tinkerer with the raw iron for a build's iron bars but no coal now tells the builder it's short of coal ("Can't be made yet: short of 1 Coal...") instead of "Nobody here makes: Iron Bars", and leaves the ore in the chest (B408).
- A Hauler Golem whose nearest Storehouse it can't get to (walled in, across water) no longer stands at the end of its path with its load forever: after about 10 seconds without getting closer it takes the load to the next nearest Storehouse, and tries the first one again a couple of minutes later (B450); an empty one that can't get to a Drop Box or a worker's chest likewise fetches from the next one instead (B456).
- A grown child now takes up a parent's trade for every job, not only the Shopkeeper: a Scholar's child takes a free lectern as a Scholar (likewise Teachers, Tinkerers, Chefs, Nurses, Innkeepers and the other jobs that share a vanilla block), and a child whose class is too low for the trade waits (B397).
- Village fires: a fire on a build's roof (lightning, a chimney spark) is named after the build in chat and the chronicle ("Fire in the Starter Cottage"), not "Fire in the village" (B390); a Firewarden with no name yet reads "the Firewarden put it out", not "Firewarden the Firewarden put it out" (B391); and a fire burning when the server restarts is still put out, as the village's fires are now saved (B392).
- Counts of one now read in the singular across the mod: "1 emerald" in every price, purse, treasury and reward (and "+1 emerald" for beating a Novice trainer), "Posted 1 item", "Burgher: 1 pays" on the tax split, "1 block of Stone Bricks" for a one-block Shape Planner box and on the Blueprint Table ("1 material · 64 items"), and every countdown and counter that reaches 1: "Wait 1 day", "Back to work in 1 day", "About 1 hour left", "1 day and 1 hour left" on realm invites, pacts and feuds, the colony wait, an elder's last days, a quest's days left, the fossil revival's minutes, the Nurse's seconds, "1 battle", "1 trade", "1 sale" and "at 1 heart" (B398, B406, B410, B413; the builder's "1 item of 1 kind" status, B393, was already fixed by B402).
- A Book and Quill held in the off hand (main hand empty) and used on a builder's Blueprint Table or Builder's Bench now writes the list of his build, as from the main hand, instead of opening the Table (B394).
- A Mason's kiln (and a Tinkerer's forge) now burns coal and charcoal together: twelve clay with one coal and one charcoal in the chest makes the three brick stairs, instead of "short of 1 Coal" forever, and a real shortage counts both kinds (B396).
- A request or quest to bring many of a thing names them in the plural: "4 Baked Potatoes", "8 Cookies", "32 Torches", "Bring 24 Clocks" (B407).
- A guard on watch by a raiders' camp (not on a raised Rally Banner) who kills one of its band no longer turns the camp's fall into a war party's win: break it without a war party and the village gets the usual 5 days' peace, not 10 and the "warband is broken" cheer; a war party that lost its guards there still earns them (B418).
- The war party's muster line on the Defense page and in chat reads "1 keeps the walls" when one guard stays behind, not "1 keep the walls" (B387).
- A Carpenter can take the crafting table again in Spruce-style buildings and in the Tom's Simple Storage Storage Hall and Warehouse: the Spruce style no longer swaps it for Carved Wood's spruce crafting table (no job site), and the Tom's copies keep the crafting table and put their crafting terminal in the counter beside it (B388).
- A builder's "Dig the cellar" trade goes once the cellar is there, dug by them or by hand (also from builders saved with it), and one bought before starts nothing: given to a builder, it no longer starts a whole new build over the finished one (B386).
- A relief band of one rider is told in the singular: "1 guard away helping Ashford" on the sender's hall, "1 guard rode to help Ashford in a raid" and "1 guard from Ashford rode in to help" in the chronicles, "1 guard from Ashford rides in to help!" in chat, "1 rider" in the realm treasury's ledger and in an ally's riders news (B425).
- The weekly leaderboard's Biggest realm reads "1 village, 1 villager" for a realm of one villager, not "1 villagers" (B422).
- "A cat by the door": a cat or tamed wolf exactly 10 blocks from the giver's bed now counts, as the request says ("within 10 blocks"); the distance is measured along the floor from the bed, not from half a block above it (B423).

## 0.149.0 — 2026-10-24

### Fixed

- The game rule `workplaceRetraining`'s description names both things it switches: retraining to any job at the Village Hall and as a Builder at a Blueprint Table (B384).
- At threat level 3 (hard) a raiders' camp, ship or portal sends a quarter more raiders too: every lair's band is a quarter bigger on hard when it makes camp and grows to a quarter more, so a Town's pirates send 10, not 8 (B383).
- Desert raiders still ramp over a wall when sand already sits at its outside foot, a player's or the desert's own: that sand counts as the ramp's first blocks and is built on, and it stays when the siege's own sand is taken away (B382).
- The morning siege report counts one raider in the singular: "The siege: 1 raider came and ...", not "1 raiders came" (B381).
- Builder and Storehouse lines count one in the singular: the board's "For the Builder, 1 block away", the chat list's "Still to bring for the Test Hut: 1 item of 1 kind" (and "5 items of 1 kind"), the blueprint tooltip's "short of 1 item", the board's "Room for 1 more item", a repair's "(1 block missing)" and the porter's "1 item carried" (B402, B403).
- The builder's written book reads "Still to bring: 5 items of 1 kind." when one kind of item is short, now held by a test through the Book and Quill on the builder (B404).
- When a villager dies only their partner and real family grieve: strangers whose mothers or fathers share a name (every unnamed parent is a "Farmer" with villager names off) no longer mourn each other and work slower (B380).
- Drawing with the Shape Planner without a Blank Blueprint says "Drawing a shape takes a Blank Blueprint (paper and blue dye)", not the Scan Tool's "Saving a scan takes...", and a shape that can't be saved says so for the shape (B379).
- A full Village refusing a guild charter counts its one guild in the singular: "Elderford is a Village and may have 1 guild; it has them all.", not "1 guilds" (B376).
- The Storehouse board and the Steward count one in the singular: "Storehouse: 1 chest, ..." and "1 job here has no workplace of its trade", not "1 chests" and "1 jobs here have" (B377).
- The Storehouse board's stock line counts one item and one empty slot in the singular too: "1 item", "1 empty slot", not "1 items" and "1 empty slots" (B395).
- A spot just past a worker's reach is refused with its distance rounded up, so the numbers agree: 48.5 blocks from a Builder's Blueprint Table says "That spot is 49 blocks ... keep it within 48.", not "48 blocks" (B378; Farmers', Miners' and other area jobs' spots too).
- The Storehouse board shows the real room behind a Sophisticated Storage controller once something is stored: with a stack-upgraded barrel and a plain one linked, 100 cobblestone made it say "Room for 22164 more items" where 8540 fit; it now says 8540. Goods a porter puts in through a controller no longer vanish when they fill the barrel holding the same item: the rest goes into the next barrel (B371).
- A Scouting Report map always shows both the village and the lair, with the red X on the lair: near a line where the game's map squares meet, one of them could fall off the map's edge (B368).
- A villager whose bed is broken during the day stops counting as housed at once, instead of keeping his home until bedtime: the village's housed count, wellbeing and lit beds drop and the hall's villager page reads "No bed of their own" as soon as the bed is gone (B362).
- A caravan saved while it waited at its station for the village's Create train, or rode it, now takes the timed trip and arrives when trains stop running (Create taken out of the pack, or `villageTrains` switched off), instead of staying on the road for good; goods that rode in the train's chest arrive too: out of the chest while Create is there, from the caravan's own record when Create is gone (B359).
- A raiders' camp standing when the world is set to Peaceful now packs up and leaves the village in peace, instead of being "broken up" as if its captain had fallen (no camp-broken message, no chronicle line about a fall, no rest days); a captain a player killed just before still counts as fallen (B364).
- When a Piglin Incursion's Warlord falls on a server with a mob blocklist (Cobbleverse's MobsBeGone), his band at the outpost and his raiders now turn into zombified piglins and zoglins and stay, still raiders, instead of vanishing; with `forceThreatSpawns` off they are left out and the log says so once (B356).
- A villager beside his own bed near the edge of a village no longer makes it a bed short: the hall counted its villagers in a square and its beds in a circle, so with the default 64-block reach a villager and his bed about 45 blocks off diagonally were half in. Both are now counted in the same circle (64 across, 32 up and down), for the hall's numbers, the "beds" tip, the Steward's homes rules and the village's wellbeing (B361).
- The builder's status no longer calls a Storehouse too far with numbers that say it isn't: one 20.4 blocks from his table in a 20-block village now reads "it is 21 blocks from his table, and a village reaches 20" instead of "20 blocks" (the distance is rounded up) (B355).
- A Carpenter (or any job making a chain for a builder) whose bench is broken mid-chain and not put back within 30 seconds now lets the job go and puts what it carried (the planks sawn from the builder's logs) in the builder's chest, instead of keeping it in the bag forever while the build waits; a bench put back or moved in time still carries on (B353).
- A Mason with a furnace who is a coal short of firing the clay for brick stairs (12 clay, 1 coal for 3 stairs) now says "short of 1 Coal to make 3 Brick Stairs" on the builder's line instead of asking for bricks, and still takes nothing (B352).
- With CobbleDollars, a shop's own screen counts the price items in your off hand: 2 diamonds in the bag and 1 in the off hand now buy a 3-diamond lot instead of "That costs 3x Diamond", and the payment takes the off hand's diamond too (B350).
- Threats stay out of Nether portals in every case: a raider riding a mount (a desert raid's baby husk on its chicken) no longer rides through with it, and the Bandit King and his fort's men, bounty bands, rogues and story-arc mobs keep out too, while plain mobs still use portals (B375).
- Counted lines read right for one: "1 settler" and "in 1 day" on the hall's Colonies tab, "1 villager has no bed" (and "no work") in the What next? tips and the Steward's reasons, "Still to bring: 1 item of 1 kind" in material lists, "fetching what 1 Amethyst Ring takes" above the luxury makers, crafters, chefs and smiths, and "1 block" on the Storehouse board's linked lines.
- Items held in the off hand with the main hand empty now work where they didn't: the Storehouse board's stock orders
  page offers to order the off hand's item (B351); a Colony Charter sends the settlers from the off hand, and a
  sneak-click on the hall with it sends them too instead of opening the colonies map (B357); a City Plan, a Village
  Ledger and a named Name Tag bind to or name the Village Hall from the off hand instead of opening its page (B360); a
  placed Blueprint in the off hand writes its material list into the Book and Quill in the main hand instead of the
  book's editor opening (B365); and the Scan Tool marks a chest from the off hand instead of opening it (B374).
- A retired elder sneak-right-clicked with a job's item (lapis by a free lectern) no longer says he took the job
  while staying jobless, and no longer leaves that workstation taken by nobody: he says he has retired, and the block
  stays free for another villager (B346).
- A renamed elder (a Name Tag) is still known as his children's parent at retirement, so his own grown child takes over
  rather than a stranger: children are matched by their parents' ids, by name only in records older than the ids (B347).
- A piglin outpost's band no longer wanders into the outpost's own Nether portal and vanishes to the Nether, and
  piglin raiders stepping out of it no longer walk straight back in (B373). No lair band, bandit camp or raider goes
  through a portal any more, so a lair keeps its whole band until the village beats it.
- A hopper under a Shop Counter no longer pulls its price list out, and a hopper or dropper above or beside it no
  longer pushes items in as new prices (B349), so nobody can empty or change someone's shop with automation. The
  owner, friends and operators still set prices on the counter's screen; existing shops keep their prices.
- A Shopkeeper's trade screen sells only what the shop's chests hold at the moment of the trade (B348). Before, a
  buyer who had the trades open when the owner emptied the chest still got the goods for his emeralds, made from
  nothing; now the goods don't appear, the emeralds stay yours and the offer shows as sold out.
- A sneak-right-click with a personal request's letter, reply or heirloom hands it over (B354). Before, the letter
  (paper) was taken as a job pick ("needs a lectern nearby") and stayed in your hand.
- A found Legend at their camp takes what you hold in the off hand when your main hand is empty (B345): the shipwreck
  castaway eats a cooked meal from it and the Old Sage takes his riddle's answer. Before, only the main hand counted.
- The Storehouse board lowers a request as soon as you hand something over (B344). Before, it kept asking for the
  full amount until the worker looked again, so a quick second click handed over extra (into his chest).
- The Gray Cough's healer takes a Gift, a tonic, a Physician's Remedy or a Guild Charter while the story runs (B343).
  Before, any right-click on them talked about the cough and the item in hand was never used.
- Villagers walk round a lantern from Carved Wood (or another mod's small block that lets paths through it) instead of
  trying to walk through it (B339). In a furnished spruce Library a villager without a job sometimes climbed onto a
  desk on its way to the lectern and stood stuck on the desk's edge for the rest of the day, never becoming the
  Librarian.
- A modified client asking for previews of endless made-up blueprints no longer makes the server remember each one
  until the player leaves (B340). Only real blueprints are remembered, and only for the five seconds a repeated request
  is ignored; previews work as before.
- A Guild Charter or an Evergreen Charm held in the off hand, with the main hand empty, now works when
  sneak-right-clicked on a Master (B341), as from the main hand; before, the click only showed the builder's status
  line (or another villager's own page) and the charter was kept. The same goes for Builder's Tea and the other tonics,
  the Physician's Remedy, a Gift and a request's letter or heirloom on a plain right-click, which only opened the trades.
- The other items a villager takes on a click work from the off hand too, with the main hand empty (B342): a City Plan
  (the Steward), a job's item (a new job at the block they stand by), the hiring items (lapis, a compass, flint, shears
  and the rest), a Blueprint, a Book and Quill, a Wonder Plan, a marker, a Delivery Note, a fishing rod and a guard's
  gear (but not a shield, which stays in the off hand). Before, the click showed the builder's status line or opened the
  trades. Both hands empty still shows the status.

## 0.148.1 — 2026-10-23

### Fixed

- A Captain's Insignia held in the off hand now pays when right-clicked on the Bounty Board or the Village Hall
  (B338), as from the main hand; before, the click only opened the board or the hall.
- A Book and Quill on the Blueprint Table a builder works at now writes the material list of their build, as it does on
  the builder (B337), or says no build is under way from it; before, it only opened the table. A table no builder
  works at still opens as usual.
- A shop owner who sneak-right-clicks their Shop Counter with something in hand (emeralds, the goods to stock) now gets
  the sales log as with empty hands (B336); before, nothing happened.
- Right-clicking a chest, barrel, crafting table, furnace, trapdoor or Builder's Bench with the Scan Tool marks it as
  a corner again instead of opening, flipping or answering it (B335), so a build whose corner is one of those can be
  scanned the way the tool says: one corner block, then the opposite corner.
- Right-clicking a chest or barrel with a Delivery Note marks it again instead of opening it (B333), so a courier
  route can be set up the way the note says: the container to take from, then the one to bring to. A note that
  already carries 9 kinds now says so when you hold a 10th, instead of "Stop carrying" a kind it never had (B334).
- A desert raider who climbed a sand ramp no longer gets stuck on a low step just inside the wall (B330). Coming
  down onto a slab or a stair's lower step, it was walked back toward the ramp's top again and again; now it is let
  go there to fight like one that lands on the ground.
- A desert raider who climbed a sand ramp no longer stands for good on raised ground inside the wall (B331). Behind
  a wall with a terrace, a raised floor or a thick walkway inside, it was pushed again and again toward the spot it
  already stood on; now it drops in where the ground is low enough and is let go there to fight.
- A Golem Smith's Hauler or Farmhand Golem no longer stands stuck among villagers who stand still, such as shop
  villagers with no AI (B329). After a fight it could end up wedged between two of them, held back by their push for
  good. Now a golem that gets no farther for 2 seconds squeezes past them and goes on with its job.
- A Wonder crew no longer stands idle near the top of a big build (B326): when a member held a column and never laid
  its block (most likely the folly dome's centre tile), the lead waited on it with no end while going back over the blocks
  it had put off. Once the crew has stood still for 10 seconds, the lead now takes that column over and lays the block
  itself.
- Standing next to a conscript in a raid no longer swaps his militia sword for the wares he'd show you (B324, B327):
  vanilla's trade display put one of his wares (a pumpkin pie, say) in his hand, and he fought on without the sword.
- A conscript whose hand is emptied in the middle of a fight under Conscription takes the militia's stone sword up
  again at once (B324): the sword was handed out only as each fight began, so a villager whose hand was emptied mid-fight
  fought on bare-handed until that raider was dead.
- A Pidgey taking the air mail up no longer risks being sent back to your PC at the top of its climb (B89): the flight
  stopped as little as half a block under the top of what its pasture lets it roam, and a tick of the Pidgey's own flying
  there could carry it out, which Cobblemon answers by recalling it. It now stays 2 blocks under.

## 0.148.0 — 2026-10-22

### Added

- Three storage builds at the Blueprint Table, each a Storehouse: the Storage Hall, the Terminal Nook and the Warehouse. With Tom's Simple Storage they are built of its blocks, every chest on one network with terminals to search it; without it, of plain chests and barrels (2.1, behind its expansion switch; 37.17).
- With Create, six more working builds at the Blueprint Table: the Brass Foundry (a mixer over a basin and a blaze burner), the Sawmill (two saws on a belt), the Crushing Mill (a pair of crushing wheels), the Greenhouse (a glass house with farmland beds, a belt and a harvester-and-plough cart), the Clock Tower (a cuckoo clock turned by shafts up the tower) and the Pump House (a pump filling a fluid tank), each turned by a water wheel as soon as it's built.

### Fixed

- Settlers leaving for a colony no longer miss the bell because of a free workstation: a jobless volunteer who had his eye on one could stand by it the whole gathering (the walk to it kept winning over the walk to the hall), and the bell rang without him. Called to the hall, a settler now lets that workstation go, and it is free again for the village.
- A Pokémon Trader no longer walks off while you're trading with him: with his trade screen open he stops his rounds and stays facing you, like a vanilla villager at his trade screen. Before, he could stroll out of reach mid-trade and the screen shut before your second click (B312).
- The Colossus's lantern is lit again: a builder or villager boxed in inside the ring of panes where its glowstone goes, with nowhere near to step to, is now moved to a free spot further off, so the glowstone is laid instead of given up with everything over it, which left the lantern hollow (B308).
- A Storehouse with two Tom's Simple Storage inventory connectors on one cable network now counts each chest once: before, each connector was read as the whole network, so every item there was counted twice (and a second connector touching one of the network's chests counted that chest twice too). It now reads the network through one connector (B305).
- Villagers now sleep in the Terrace's beds. Its two homes each have their bedroom up a ladder: a villager without a bed now takes a free one there (before, villagers only claimed beds they could walk to, so the Terrace's stood empty while villagers stood about its rooms all night, on a chest even), and a villager coming home by the other home's door climbs his own home's ladder instead of the nearest one, where he couldn't reach his bed and gave it up. This holds for any bed up a ladder, furniture mods' beds included (B304).
- The Village Hall's Free job blocks page now lets you choose any job a shared block offers: a composter says "Composter: 4 jobs" and lists Farmer, Orchard Keeper, Florist and Composter; click it, pick the job, then who takes it, and they become that job (before, every composter or lectern was only ever offered as its first job). Blocks of one kind in one building are now one line with a count ("Fisherman ×4", "In the Storehouse Shed") instead of a pile of identical lines (B303).
- The Village Hall no longer asks you to "Clear out 8 monsters" in a world where none can come (Peaceful, or a pack that removes them, as Cobbleverse's MobsBeGone does). With Cobblemon the quest becomes "Defeat 8 wild Pokémon" and the wild Pokémon you battle or catch near the hall count; bandits and raiders count everywhere. With neither, the hall asks for bread instead, reform steps too, and a stuck quest already up is swapped for one you can finish (B301).
- Builders no longer leave furnishings floating: a flower pot, candle or lantern that stood on a vanilla table is set down on the furniture mod's lower table that replaces it, a potted flower on a hillside gets a footing of dirt under it, and when a builder finishes a build, anything left resting on nothing is set down on the block under it (or dropped as an item if it can't stay) (B300).
- A CobbleFurnies Furni Crafter placed before the update that added the Furnicrafter now shows on the Village Hall's Free job blocks page and can be given to a jobless villager: when its chunk loads it becomes a job block, as do Create's Toolboxes and Train Stations and Cobblemon's Campfire Pots and Pastures placed before they were ones (B302).
- A builder at the Builder's Workshop's table now counts and takes the Storehouse's chests when the Storehouse has a Blueprint Table of its own beside them (with a builder at it, those chests counted as that builder's), and the village's own builder counts a Storehouse you placed. When his status says he needs something, it also says why a Storehouse nearby isn't counted: too far, not his village's, or no chest he can read (B297, B298, B299).
- A villager with a heart event to tell you now starts it even if they've just said hello: a line of small talk over their head used to count as work, so they waited it out for five seconds and often wandered out of range and never told it (B309).
- A colony settler who has a job now leaves his workstation when the muster calls him to the hall, instead of working on until the bell rings without him (B295).

## 0.147.0 — 2026-10-21

### Added
- **Change any villager's job from the Village Hall** (36.18). Right-click a working villager in the hall's residents
  list and press **Change job**: pick any free job block in the village (or the Builder, at a free Blueprint Table), see the
  price (5 to 50 emeralds by their level, as at the Blueprint Table) and click again to pay. The new job and its block
  are theirs at once, with no breaking and carrying job blocks; they walk over, and one who can't get there within
  about a minute is moved beside the block. Their old block is left free; a builder hands back their build. Children
  and nitwits can't, and the game rule `workplaceRetraining` off hides the button.
- **Every crafting job works through whole recipe chains** (2.1, 37.14a, 37.16a). The Carpenter saws logs into planks,
  cuts slabs and makes the barrel or chair a builder waits for; the Mason fires clay into bricks, lays and cuts them; the
  Librarian, Tinkerer, Toolsmith, dyer and Furnicrafter do the same for their own kind, step by step ("step 2 of 3"),
  a bagful at a time, carrying on after a restart. The builder's line says what can't be made ("Can't be made yet:
  short of 4 Oak Log to make 3 Barrel", "Nobody here makes: Oak Stairs"). This replaces the Crafter, which is retired:
  no Crafter's Workbench recipe, no Crafter's Workshop in the Blueprint Table, the Steward's plans or new villages, and a
  Crafter on an existing world becomes jobless.
- **A job block with several jobs gives one at random** (36.17). A villager without a job who takes a composter, a
  lectern, a Shop Counter or any block several jobs share by themselves now becomes one of its jobs, not always the
  vanilla one: a composter can make a Farmer, an Orchard Keeper, a Florist or a Composter. The jobs the village has
  fewest of are the likeliest, and the job taken stays. Jobs picked with their item, from the hall or by the Steward
  are unchanged, and the Trainer Leader is never drawn. Random Station Jobs in the settings turns it off (the vanilla
  job every time, as before).

- The Furnicrafter (Cobblemon depth, 2.2, with CobbleFurnies): a villager without a job by CobbleFurnies' Furni
  Crafter takes it and makes CobbleFurnies furniture from the store's materials: the chairs, tables and lamps a builder
  nearby is waiting for (a furnished home's pieces) and the pieces the Storehouse orders, with sticks cut from planks
  first when they're short. A red bobble beanie, a walnut apron with a Poké Ball on it and a tape measure; trades in
  planks, wool and furniture. Without CobbleFurnies nothing changes. Switch: `furnicrafters`.
- Fixed: a Sophisticated Storage link tied to a controller with the Storage Tool, standing away from the controller's network, no longer has the storage behind it counted twice at the Storehouse (B262).
- Fixed: a Wonder's crew no longer stands still for minutes when many blocks wait over holes it can't fill: once the
  crew has stood still for 10 seconds the lead gives them all up together, not one every 10 seconds (the Hanging
  Gardens' time-lapse ran out of time over one terrace's holes) (B270).
- Fixed: a Builder working from another Blueprint Table (a Builder's Workshop's) now counts and takes what the village
  Storehouse holds, in the line over his head, in the Book and Quill list, in his status and when fetching. A Storehouse
  nobody placed (put up for the Steward) in a village whose hall you own is yours to your Builders, and your Storehouse
  feeds the village's own Builder on your builds; before, the Storehouse counted only while his table stood by it. New:
  sneak-right-click a Blueprint Table with an empty hand next to a Builder to move him there without breaking the old one
  (or pick him for a free table on the Village Hall's free job blocks page); his status says which table he works at (B290).

### Fixed
- **A worker's partner show goes to the Pokémon pastured by their workstation** (B277). With two pastures close
  together, a neighbour's Pokémon that wandered a step nearer (a florist's Oddish by the compost bin) used to take the
  show, and the worker's own partner just stood by.

## 0.146.1 — 2026-10-20

### Fixed

- The dyer mixing concrete powder from sand, gravel and dye now makes it step by step like the other chains (the powder,
  then the hardening), so a world saved mid-job no longer loses the spare powder a mix makes.
- **A broken bandit camp's ground comes back at the right height everywhere** (B288). Where grass had crept over the
  dirt a camp filled its floor with while it stood, that patch stayed a block too high once the camp came down; now it
  goes back to the land as it was too.
- **No more palisade tower stacked on another** (B283). While a Steward's corner tower was still being finished, its
  roof counted as the ground there, so a second tower was started on top of it and stood half-built for good. A wall
  piece now never starts on the spot of one still being built.
- Both homes of the Terrace (and all three of Terrace II) get the furniture mods' table and chair downstairs: the east
  home kept spruce stand-ins because its table stands against the party wall. Tables against a wall in the village
  kitchens, the Crafter's Workshop, the School and the Winery are swapped too. Every furnished room now has its own
  light: a lantern in each Terrace kitchen and in the Townhouse's sitting room and loft (B281).
- **No more raised band of dirt round a finished build** (B280). A build standing a block above the ground round it
  (on a bump, or with its blueprint clicked onto a flower) had the strip round it filled up to its floor: a jagged
  band of dirt on the grass, with holes where flowers stood. Leveling now only fills holes up to the land round the
  build, and a blueprint clicked onto a flower or tall grass stands on the ground under it.
- You can now see everything a build still needs after you hand the blueprint over. Right-click the Builder (or their
  Builder's Bench) with a Book and Quill to get the list for the rest of the build, with what the chests, the
  Builder's bag and the village storehouse already hold ticked off. Sneak-right-click the Builder with an empty hand
  to see the whole list in chat. Before, the only list was the 3-4 lines over the Builder's head (B279).
- A guest Legend who settles in a village has their powers at once: a Seer made the village's at dawn foretold at
  that dawn, where before their powers could wait up to ten seconds and the dawn's foretelling was missed (B269).
- Builders take from the chests only what their work still needs. Each trip took one block too many, so two Builders
  building from the same Storehouse could each end up waiting for the one log or slab the other was carrying, and
  neither build finished (the city time-lapse's last palisade pieces, B132).
- Builders no longer lose their bench, or the chests by it, to a neighbour's build. A build whose box took in another
  Builder's Bench cleared it; its Builder then took the nearest free bench, another Builder's, and his own build
  waited forever for materials that sat in the chests by his old bench. Builds now leave every Builder's Bench
  standing (B132).
- Furnished homes and workplaces read as rooms: no furniture in a doorway's way, every bed's head against a wall,
  lanterns hung or on a table instead of the floor, chests against a wall or at a bed's foot. A modded piece goes only
  where it can stand (no Beautify picture frame or Handcrafted shelf floating mid-wall) and a two-block-tall piece gets
  its top half. The Stone House was refurnished: a clear way in from the door, and its attic and upper floor are
  proper bedrooms (B260).
- A Builder repairing a building now takes what it needs from the chests inside that building. Repairing a Storehouse
  Shed, it waited for a Hay Bale and an Oak Ladder that were in the shed's own chests by its bench, because chests
  inside the build were left out, as they are for a new build (B261).
- A villager without a job who had picked a workstation just before dusk no longer keeps walking to it at bedtime.
  From a Stone House attic it used to stand by its bed all night and then lose the bed; now it goes to bed and picks a
  workstation again by day (B258).
- Two villagers coming home through one door together no longer get stuck on the doorstep, where one could lose
  their bed for the night (seen at a renewed Stone House). The one further from the door now steps back a moment and
  goes in after the other (B255).
- Wonders: a crew no longer lays a block in the air over one the lead had to give up (someone stood in it, or there
  was nowhere to reach it from), as once on the folly's dome. A crew member now lays the given-up block once it's
  clear, and the wall goes on from there; if nobody can, the block over it is left too, never floating (B254).

- Storehouse: a Sophisticated Storage link that isn't linked with the Storage Tool (one a builder built, or a player
  placed without the tool) no longer hides the storage behind it: that barrel stays a store of its own, so villagers
  see what's in it. A link linked to the controller still counts what's behind it through the controller (B257).

- Sieges: an archer on a battle station whose raider goes out of sight (under the walkway, hacking at the gate) turns
  to the nearest raider he can see and reach, instead of watching for the hidden one for up to a minute (B252).
- Sieges: archers take the battle station with a view of the ground in front of the gate, in a gap of the
  battlements rather than right behind a merlon, and stay in the middle of it, so they shoot at the raiders coming at
  the gate instead of standing over the siege without seeing anyone (B252).

- Create: a farmer, armorer, chef or miner who stops machine work in the middle of a trip (their workstation broken
  while still a Novice, or machine work switched off in the config) no longer keeps the wheat, iron or ore they
  carried for good: it goes back into the village's store, or drops at their feet when the store is full (B244).
- Create: villagers and the Mechanic only work the village's own machines and chests, those inside a building the
  village's builders finished. A Mechanic hired at a player's own toolbox, or a farmer, armorer, chef or miner whose
  workstation stands by a player's chests, no longer feeds the player's iron, wheat or ore into the player's press or
  millstone, nor carries the player's automation output away (B243).
- Workers: a villager whose walk the game dropped for a moment (a stroll ending as the job's walk began) no longer
  stands still for a second before setting off, when an old path of theirs had failed; this sometimes made an elder
  look as quick as a younger villager walking beside him (B242).
- Sieges: a raider that walked back out through the open gate after going in no longer stands outside the walls for
  the rest of the siege; it is sent in again (B175).
- Storage: a Sophisticated barrel or chest a builder adds to a storage controller's network, touching only another
  linked barrel, is now linked by the controller, so the Storehouse sees what is in it (B238).
- Create: Mechanics and machine workers no longer carry a player's own input out of a Create machine to the village
  store (gold ingots waiting on a press's depot, bones in a millstone, a brass mix in a basin); they only take what
  the machine made, and don't feed a machine that is still busy with a player's batch.
- Create: a farmer, armorer, chef or miner killed on the way to a machine now drops the input they were carrying
  instead of losing it.
- Create: workers no longer use Create's work blocks as supply chests: a train station's schedule slot, a saw, an
  ejector, a brass tunnel, a portable storage interface, a packager, a frogport or postbox, a stock ticker or a
  creative crate (a farmer filled them with wheat, and a builder could take endlessly from a creative crate).
- Create: an item vault built of several blocks counts as one store: its items were counted once per block (a
  three-block vault with 10 diamonds showed 30), so workers and builders thought the store held more than it did.
- Create: when the store by a machine has room for only part of what the machine made, the rest now stays in the
  machine for next time instead of being dropped on the ground, where it could despawn.

## 0.146.0 — 2026-10-19

### Added
- **Villagers live much longer, and a death matters** (36.16). Every life is ten times longer: an elder after 1200
  grown days (Days Until Elder, now up to 10000), passing 400 elder days later; your world keeps each villager's age and
  counts it against the new span, and a settings file still on the old default 120 moves to 1200. When a villager of a
  village with a hall dies, the players near are told who it was, their job and their years ("Mira, the village's
  Farmer, has died aged 31. The village mourns."), the chronicle says the same, their family grieve for three days and
  their partner for five (a lower mood and 10% slower work), and the Undertaker leads a funeral at the grave first.
  Village Mourning in the settings turns it off.
- **Upgrade a home from the Village Hall** (36.14). A new Upgrades tab (the bricks) lists the village's finished builds
  that have a next level, nearest first: the level and the next, where, what it costs in blocks, and whether the space
  is free. Click one and the village's Builder builds the next level over the old one. Natural ground, trees and water
  in the way are cleared as part of the job; a wall, fence, path or another build blocks it, and the row says what's in
  the way and where. Residents keep their beds: a Builder no longer breaks a bed somebody sleeps in while clearing for
  any upgrade.
- **A Quarry that grows back** (36.12). A new blueprint in the Blueprint Table: a fenced pit with steps cut down its side,
  a hoist and the Miner's shed with its blast furnace. The Miner who works at that furnace digs the pit by themselves (no
  Quarry Marker), and what they dig grows back slowly from the bottom up: mostly stone, some coal and copper, now and
  then iron, redstone and gold, rarely a diamond, deepslate in the bottom layers. Dug to the bottom, they wait at the
  shed for a layer to grow back; a pit dug right out takes about a week to fill again. Nothing outside the pit changes
  and a block you put in it stays. The Quarry Marker works as before. Settings: Quarry Build (on), Quarry Regrowth (100
  blocks a day).
- **The Builder asks before repairing a house** (36.15). A builder who finds blocks missing from a build asks first:
  "Repair it? [Yes] [Not now]" in chat, and on the Village Hall's new Repairs tab, and repairs only what you said yes to.
  A repair under way can be paused or stopped there (the builder goes straight on to their next job), and a blueprint
  handed over mid-repair comes first. What raiders and village fires broke is still put back without asking. The
  setting Ask Before Repairs, off, brings back repairs without asking.
- **The Blueprint Table keeps your blueprints** (36.13). A Stored tab on the table lists the blueprints you put away in
  it, with how many of each: Store Held or Store All puts them on its shelf, Take One Out hands one back. They belong to
  the table (up to 200), stay through restarts and drop when the table is broken.
- **Miners make their own pickaxe** (36.11). A Miner whose pickaxe wore out, or who has none, makes a new one at their blast furnace from what they mined: three cobblestone and two sticks (or two planks), iron from Journeyman and diamond from Expert. With nothing to make it from they wait, and the Storehouse board asks for what's missing. Setting: Miners Make Pickaxes (on).
- **Search the Blueprint Table** (36.10). A search box over the library narrows its 150-odd builds as you type, by
  name ("inn"), id ("fishers_hut") or a job a build brings ("fisherman", "smith"), in capitals or not. A build's levels
  stay under it, the header counts the matches, and a search that finds nothing says so; an empty box lists everything.
- **The Conductor** (39.7d, with Create). A villager without a job by a Create Train Station becomes the village's
  Conductor (maroon cap and tunic, brass buttons, a ticket punch): they take the seat of the village's train instead of a
  jobless villager, call each stop as it comes in ("Now arriving at Ashford! All change, mind the gap."), and trade
  rails, Create track, train schedules, signals, railway casing and train controls. Without a Conductor a jobless
  villager still drives. Behind the Village trains setting.
- **Any mod's spruce chair will do** (37.13; 2.1, behind its gate). A furnished build no longer insists on one
  furniture mod's piece: bring CobbleFurnies' or Cozy Home's spruce chair where the build asked for Handcrafted's and
  the builder builds yours, in the same place and facing the same way (the first mod in the furniture mods list when the
  chest holds several). The material list book shows the piece once, "Spruce Chair (any furniture mod)", with each
  mod's piece on hover. Chairs, tables, couches, desks, counters, cupboards, nightstands, drawers and lamps; pieces
  already built stay, and a piece no installed mod makes is the plain one as before.
### Changed
- **Cobblecuisine dishes are asked for at the Village Hall** (38.8a). The Chef no longer cooks Cobblecuisine's dishes on
  his own: open the hall, click the minecart and pick the new **Dishes** tab, then click a dish to ask for 4 (shift: 16,
  right-click: take the request down). The dishes are locked until the village has a Chef, and a dish above his level
  says which level he needs. He cooks the requests from the store's makings and brings them to the Storehouse. Camp
  curries, Poké snacks, Campfire Pot candies and vanilla food are cooked as before; dishes he already made stay put.
### Fixed
- **Raiders no longer spawn stuck on their war camp's fences** (B130). A raid that gathered in or next to its own camp
  put each raider on top of whatever stood highest where it was drawn, so pillagers stood on the camp's fences and the
  ravager on a tent, and never set off (the ram never reached the Palisade Gate). Raiders now gather only on ground a
  mob can stand and walk from, no higher than a step over the gathering point; another spot is drawn, else the nearest.
- **Villagers no longer get stuck under flower boxes** (B156). The game's path finding counts every trapdoor as open,
  so a villager walking along a house front could walk into the gap under a window's flower box (a closed trapdoor at
  head height), bump its head and stand there for the rest of the day; a Printer walking up to the Print Shop sometimes
  never reached its cartography table. Villagers now walk round closed trapdoors they can't fit under, in our builds
  and yours; closed trapdoors used as a floor are walked on as before.
- **A Firewarden no longer stops a step short of a fire on a high roof** (B230). For a fire on a roof's ridge they now
  walk onto the very spot they throw the bucket up from; before, they could stop one block away, just out of throwing
  range, and stand there watching it burn.
- **A Create schematic named like a Windows device no longer breaks the Blueprint Table.** A schematic uploaded to
  Create's table as `con.nbt` (or `aux`, `nul`, `com1`...) made the game refuse to save its blueprint with an error
  nobody caught, so the Blueprint Table failed every time it was opened. The table now says once that it couldn't
  import that schematic and opens as usual.
- **No more client crash near a turning Windmill with shaders on** (B231). With shaders, Create draws spinning
  contraptions the slow way, and in packs where Sodium and Continuity write a block one corner at a time (Cobbleverse)
  the Windmill's sails crashed the game ("ShadedBlockSbbBuilder only supports putBulkData!"). Each corner now goes into
  Create's buffer as it would whole, so the sails draw.
- **Graves no longer clutter the village** (B222). The Undertaker walks to each grave lying in a street, a field or on
  a doorstep and lays it in the next free row of the village's Graveyard; a village with no Graveyard gets a small plot
  of three rows near the hall. Everything in the grave is kept (name, job, level, trades, partner) and the villager can
  be brought back from there. Graves already scattered in your world are moved too. Setting: `gravesToGraveyard` (on).
- **Look at a villager to see its job and level** (B221). Point your crosshair at a working villager up close and a
  plate over its head reads "Fisherman, Journeyman" ("Dara — Fisherman, Journeyman" for one with a name). Every job
  shows, ours and vanilla's; jobless villagers and nitwits show nothing new, and no villager's name is changed.
- **A villager handed the item for their job gets straight to it** (B220). A Fisherman given a rod late in the work
  day stood about for a second before fishing; now the work starts within a tick, as it does for a Builder given a
  blueprint. Handed one outside their work hours, a villager now says when they start ("in about 8 minutes") instead
  of standing there with no word; at night they still sleep first.
- **A Miner stores everything they dig** (B224). Stone, cobblestone, deepslate, andesite, diorite, granite, tuff,
  gravel and dirt go into the chests by the blast furnace along with the ores. When those chests are full (or there
  are none), the rest goes to the village Storehouse instead of being dropped on the ground to vanish, and ores always
  get the room first: a stack of stone moves on to the Storehouse to make way for them.
- **A Builder with no chests by its bench builds from the village Storehouse again** (B226). The Storehouse only
  counted while its Porter was in reach, so with the Porter asleep at home far off (or in a chunk that wasn't loaded)
  the Builder said "No chests by its bench" and waited, though everything was in the Storehouse's chests. Now the
  Storehouse counts by its block, Porter or not.
- **A broken bandit camp gives the land back** (B225). When a camp came down it left its levelled pad and its patch of
  trampled earth and gravel for good, so the land round a village got scarred as camps came and went. Now a camp
  remembers the ground it was set on, and once it is down that ground comes back: the hill it cut into, the hollow it
  filled, the grass. Camps made before this update have their pad blended into the land round them instead (grass on
  top, no trampled earth or gravel, no sharp steps). With Camp Ruins on, the tents stay but the open ground round them
  goes back. Anything a player built there stays.
- The Blueprint Table no longer errors on a Create Schematic whose file name holds a hidden control character: it refuses it by name, like other bad names.
- **The Firewarden puts out a fire on a high roof** (B218). A fire on the ridge of a pitched roof, such as the Starter
  Cottage's, had nowhere within a bucket's throw to stand, so the Firewarden waited by the grindstone while it spread
  down the roof. Now they run to the open ground below it and throw their buckets up onto the roof (up to 12 blocks).
- **The whole crowd sees the Mega Shrine's show** (B215). On a festival day the trainer at the shrine's pedestal now
  waits for the villagers off work to gather before Mega Evolving their ace (at most half a minute), and everyone who
  came cheers (+3 mood), even those at the back of the crowd. Before, the show could go off as the first villagers
  arrived, and only those already near the pedestal got the mood.
- **Raiders no longer appear frozen at the edge of the loaded world** (B176). A raid gathering near the edge of the
  ground where mobs move put some of its raiders just past it, where they stood still and the raid lost count of
  them. When a raid gathers on live ground every raider now arrives on it, and a siege moves its gathering point beyond the walls only onto it.
- **Caravans no longer get lost when a Village Hall is taken away** (B212). Break the hall a caravan is heading for and
  everything it carried now turns round and comes back to its own village's Storehouse, not just the goods it meant to
  sell. A caravan whose own village's hall is gone too is dropped, so a new hall set down on the same spot no longer
  receives old goods out of nowhere.
- **A train caravan no longer waits forever at the station for a village that's gone** (B214, with Create). Break the
  hall a caravan is waiting at the station to ride the village train to, or whose train is carrying its goods, and the
  goods now come out of the train's chest once and the caravan walks them home; break the sending village's own hall
  and its caravan waiting at the station walks on to where it was going.
- **No more villagers than beds, and nobody lives in the settlers' wagon** (B206, B207). The bedrolls by a Settler's
  Wagon or a Colony Camp are for the road: once the Village Hall stands, a settler who kept one as their home gets up
  and finds a bed in the village's houses, and nobody claims one again. The hall doesn't count them among its beds
  (its Beds button says how many it left out). A baby or an inn's traveller now moves in only on a free bed in a house
  and takes it at once, so one free bed can't be counted twice (a birth, a traveller and vanilla's own breeding all
  used the same free bed before); a village with every bed taken takes nobody in, and the hall says "The village is
  full". Existing worlds are mended at each hall's next round, older camps known by their bedrolls, campfire and hay.
- **Village golems, horses and pups on modpacks that block animals** (B211). Cobbleverse's MobsBeGone also blocks iron
  golems, wolves, horses and llamas, so the Golem Smith's and Iron Pact's golems, relief riders' horses, the
  Beastmaster's foals and wolf pups and caravan pack llamas never appeared. They are now put in the world anyway (new
  setting Force Village Spawns, on), or left out with one clear line in the server log; a Golem Smith keeps his iron
  when his golem is refused. A story arc's foes are forced like raiders now too.
- **A broken bandit camp no longer stands for good** (B209). Kill a camp's chief and the camp is gone from the village
  at once (the hall, the Defense page and the scouts' reports forget it), and a couple of seconds later its tents, fire
  and chests come down, top first; the loot in its chests spills out on the ground, and anything a player built or
  changed there stays. Camps broken up before this update are found and taken down the same way when the village's
  hall next checks for threats. A camp that leaves a village in peace packs up the same way. The new **Camp Ruins**
  setting (off) leaves a broken camp standing as ruins instead, its fire out.
- **Bandit camps and night raids have their mobs again in modpacks that block vanilla mobs (B208).** Cobbleverse ships
  MobsBeGone, which stops pillagers, vindicators, zombies and most other vanilla mobs from spawning, so camps stood
  empty and were "broken up" at once and night raids never came. Camps, raids, the Bandit King's fort and bounty bands
  now put their mobs in the world anyway (new setting **Force Raider Spawns**, on), the server log names any mob another
  mod refused, a camp is never made without its chief, and on Peaceful players near are told why nothing comes.
- **Settlers really walk out toward their colony (B182).** When the bell rang, settlers headed for a point 48 blocks
  out; if that ground wasn't loaded yet they could only shuffle a few random steps and often stood by the hall. They now
  walk to the farthest loaded point on the way, which moves out as the land ahead loads.
- **Villagers walk in by a shut door every time (B203).** A villager opening a shut wooden door on its way in (to its
  job, its bed, a workplace) sometimes turned round as the door swung open, shut it behind itself and wandered off
  round the building: the game worked its path out again for the opened door and aimed it at some bed or bell the
  villager had only looked at. The new path now goes where the villager was walking.
- **A job block a villager was walking to is let go when they're given another (B201).** A jobless villager on the
  way to a job block they picked on their own and given a different one, from the Village Hall or by sneak-right-
  clicking them with a job's item, kept the first one held for nobody: it dropped off the Free job blocks list and no
  other villager could take it.
- A Jeweller now cuts Abomasite for a Master trainer whose team has an Abomasnow: it was the one Mega Stone
  missing from Mega Showdown's stone list, so he waited for makings forever (B174).
### Changed
- **Raid helpers replace Den Wardens and the village's den challenges** (38.1a, 38.3a, 38.4a, the owner's change). The
  village no longer raids dens alone and no guard stands watch at them. On the hall's Defence page, next to a glowing
  den, hire up to 3 of the village's trainers (5 emeralds each, set by `denHelperPrice`): they follow you, each gives
  you a raid cheer item once when you join the raid there, their Pokémon wear the boss down (never below a quarter),
  a win goes in the chronicle, and they walk home after the raid or a day. Old worlds and config files load as before.
  The scene `den_helpers` shows it.

## 0.145.0 — 2026-10-18

### Added
- **Fill empty job blocks from the Village Hall** (36.9). The hall's new Free job blocks tab lists every job block in
  the village nobody works at (its job, the building it's in, how far away) and the villagers without a job who could
  take each; pick one and they claim the block and walk over. A block taken meanwhile is refused cleanly. Paid
  retraining of a working villager at the block stays as it was. The scene `hall_jobs` shows it.
- **The Chef cooks every Campfire Pot recipe** (2.2, with Cobblemon): the EXP and IV/EV candies, medicines, sweets and
  any pot recipe a mod or data pack adds, found by recipe type, only from makings in the store, 16 kept by his stove.
- **Real Create trains between villages.** With Create, two villages joined by rail now put their own Create train
  together at a station (a bogey, a carriage with the caravan's chest, controls at both ends and a jobless villager in
  the seat as driver) and Create runs it on a schedule between the two stations. Caravans wait at the station, ride the
  train and are unloaded when it stands at the other village's station. The scene `village_train` shows it moving.
- **Village trains climb, turn and carry the goods** (39.7b, with Create). The track along a caravan road is now one
  line Create's railway accepts: sloped track where the road steps up or down and Create curves round its turns, so both
  villages' stations join one network. A builder builds the train from its blueprint out of the bench's chests (train
  casing, two train controls, a seat and a chest) before the station puts it on the rails, and the caravan's goods ride
  in the carriage's chest: loaded at one station, unloaded at the other. A train broken up on the way keeps them in
  its chest, never paid out twice.
- **Village trains reach every station and every road** (39.7c, with Create). A Train Station built away from the
  caravan road gets a curved spur from its own track to the road's, joining it towards the other village, so the train
  runs out of it and back in. Roads that dip a block and climb back out, step up right on a turn, or run diagonally in
  zig-zags now get unbroken track (sloped curves, a climbing turn, one long S-bend), so no road leaves a gap. The
  village's train is now a drawn carriage: a bogey, train casing and the chest below, controls at both ends and the
  driver's seat on the deck, spruce posts and a slab canopy with a lantern over it (the builder also needs three spruce
  fences, four spruce slabs and a lantern).
- **Two build libraries, vanilla and modded** (2.1, behind its gate). Every build as it ships stays the vanilla
  library. With Rechiseled, Supplementaries, Carved Wood, Chipped and Handcrafted installed, all 130 homes and workshops
  are built from the modded library instead: daub walls with braces, herringbone floors, Carved Wood chests and shelves
  in the house's wood, crockery on the tables and books on the shelves, with the furniture mods' chairs and beds as
  before. A missing mod gives the vanilla build; a building keeps the library it was planned with. New setting
  **Modded Build Library** (`moddedLibrary`, on).
- **Cellars for eight Level IIs, and for old builds.** The Inn, Winery, Farmstead, Guildhall, Barracks, Research Lab,
  Jeweler's Workshop and Artisan's House get a Level III: their Level II with a furnished, lit cellar dug under it (an ale
  and wine cellar, a barrel vault, a root cellar, a strongroom, an armoury, a lower lab, a vault, a storeroom), down a
  ladder under a hatch, everything above kept. A Smithy, Storehouse or Kitchen finished before it had its cellar is
  offered **Dig the cellar** by the village's builders (and the Village Hall's advice): its builder digs and fits out
  only the cellar.
- **The Blueprint Table says what a build brings** (36.6, 36.6a). Under a build's size: "Jobs: Fisherman (1),
  Butcher/Chef/Rancher (3)" (a slash means jobs that share a job block, the number how many of that block the build
  has) and "Beds: N" when it has beds. Uploaded and imported builds get the lines too, from their own blocks.
- **Furnished homes and modded storage (2.1), begun.** The plan (`docs/design/M37.md`) and its three settings,
  `furnishedBuilds`, `furnitureMods` and `moddedStorageNetworks`, which stay off until 2.1 is finished (37.1), and a furniture spots file beside each of 223 blueprints marking
  where its chairs, tables and beds are, for the furniture mods to fill in later (37.2). Behind the same gate, a build started with Handcrafted, Cozy Home,
  CobbleFurnies or Beautify installed gets their chairs, tables and beds in those places, chosen once and kept (37.3).
- **Furnished homes (2.1, behind its gate).** The Starter Cottage, Stone House, Townhouse, Manor, Artisan's House and the
  Inn get furnished rooms at every level: a dining corner, a bedroom with a nightstand and a lamp, a couch, a shelf of
  cups, a clock, pictures and blinds, in plain vanilla blocks that the pack's furniture mods (Handcrafted, Beautify,
  CobbleFurnies, Cozy Home) turn into their own pieces. A home planned beside a pasture with a Pokémon in it gets a
  Trainer's room: CobbleFurnies' Poké Ball chair and desk in place of its first chair and the table by it, even with
  Handcrafted first (37.4).
- **Furnished workplaces (2.1, behind its gate).** The Supply Shop, Library, Healing Center, Schoolhouse, Village Hall,
  Kitchen and Print Shop get furniture at every level: counters, a shelf of cups, a picture, hanging pots and rose
  trellises in the shop, reading desks in the Library, benches in the schoolroom, the Village Hall and the Healing
  Center's waiting room, the teacher's, nurse's and printer's desks, the council table, crockery on the Kitchen's worktop. Their
  job blocks, beds and chests are never swapped; with the pack's mods they become Handcrafted counters, desks and
  benches and Beautify trellises, hanging pots and picture frames, and the Healing Center's and Schoolhouse's desks are
  CobbleFurnies' Poké Ball desks. A furniture mod's desk, counter or cabinet is never taken for a job's chest (37.5).
- **Villagers use the furniture (2.1, behind its gate).** At supper and in the evening villagers sit on the furniture
  mods' chairs, stools, benches, couches and sofas (Handcrafted, Cozy Home, CobbleFurnies), one villager to a seat, and
  get up for work, danger or bed; they claim and sleep in Handcrafted's fancy beds, whose house counts as their home,
  and a home with a seat in it is worth 1 beauty to its people (37.6).
- **Sophisticated Storage as village storage (2.1, behind its gate).** Its chests, barrels and limited barrels of every
  tier are tested as supply chests, porters' chests and Storehouse chests, and builders leave them standing with their
  contents. Where a stack upgrade or a limited barrel lets a slot hold more than a stack, the Storehouse board shows the
  real room ("Room for 6912 more items") and porters keep filling it while a slot has room, not only while one is empty (37.8).
- **Tom's Simple Storage as the Storehouse's store (2.1, behind its gate).** An inventory connector by the Storehouse
  is one store for every chest, barrel and filing cabinet it joins (touching it, or on its cables through a cable
  connector), so each item counts once; porters fill it and builders take from it through the connector, and a chest
  unplugged from the network is its own store again. The board says "Linked through an inventory connector: 3 blocks",
  and a storage terminal on the connector shows the village's goods in Tom's own screen (37.10).
  A limited barrel holding more than a stack of something now counts, gives and takes all of it, not one stack.
- **Create integration (2.3), the plan.** The design note (`docs/design/M39.md`): the Mechanic and their Toolbox,
  villagers working Create's millstone, press, mixer and crushing wheels, the Watermill, Windmill and Workshop,
  contraptions and Create schematics at the Blueprint Table, and trains later; its gate stays shut until 2.3 is
  finished. The owner's Create port now loads in the compat tests, which check every Create id the plan uses (39.1).
- **The Mechanic (2.3, being built).** With Create installed, a jobless villager by a Create Toolbox (any colour)
  becomes the village's Mechanic, in a grey work apron, brass goggles on the brow and a wrench at the belt. They put
  back shafts, gearboxes, cogwheels and belts missing from finished builds near the toolbox, from the build's
  blueprint and with the parts in the chest by the toolbox, and walk a round of the village's millstones: what's
  milled goes to the chest, and a turning, empty millstone gets wheat. Create's machines, belts and toolboxes are no
  longer taken for supply chests. Setting `mechanics` (behind the 2.3 gate); no Mechanic without Create (39.2).
- **Villagers work Create's machines (2.3, being built).** With Create installed, the farmer carries wheat from the
  store to a turning millstone and brings back flour, the armorer presses ingots into sheets on the depot under a
  mechanical press, the chef mixes dough in the basin under a mechanical mixer, and the miner drops raw ore between
  crushing wheels and gathers the crushed ore. Only while the machine turns and the store has room; what each machine
  makes comes from Create's own recipes, and each machine is a data file (`machine_work`) a datapack can change. The
  Mechanic's round covers the new machines too. Setting `machineWork` (behind the 2.3 gate) (39.3).
- **Create-powered builds.** With Create installed, the Blueprint Table offers the Watermill (a water wheel turned by
  water falling from a header tank, driving a millstone), the Windmill (a stone tower with a windmill bearing and 24
  sails over a millstone; the builder starts it when it's done) and the Workshop (a water wheel driving a belt to a
  depot under a mechanical press): workplaces for the farmer's millstone and the armorer's press, turning as soon as
  they're built. Setting `createBuilds` (behind the 2.3 gate) (39.4).
- **Builders build contraptions (2.3, being built).** With Create installed, a blueprint with Create's moving parts in
  it (one of ours or a machine you saved yourself) goes up in an order that keeps it whole: what holds it up, then
  shafts and gearboxes, then cogwheels, then belts, each part facing and turning as drawn and a belt finding its
  pulleys in the new place. A part broken before the build is done is put back first, and once it's finished the
  owner is told whether every part turns, or which don't and where ("1 part doesn't turn: Shaft at 12 64 -30"). The
  parts list is a data file (`kinetic_parts`). Setting `createBuilds` (behind the 2.3 gate) (39.5).
- **Create schematics at the Blueprint Table (2.3, being built).** With Create installed, the schematics players
  upload with Create's Schematic Table (`schematics/uploaded/<player>/`) are at the Blueprint Table the next time it
  opens, as blueprints builders can build, and a written Schematic right-clicked on the table imports its own file
  there and then. Blocks of mods the server doesn't have are counted and named, an upload under the same name replaces
  its blueprint, and a Schematic whose file is gone says so; names that point outside the uploads are refused.
  Setting `createSchematics` (behind the 2.3 gate) (39.6).
- **Trains between villages (2.3, being built).** With Create installed, the Blueprint Table offers a Train Station (a
  Create station beside a run of track, a platform and a timber shelter). When two villages with a trade route both
  have a station and the road between them is built, their builders lay Create track down the middle of the road, and
  from then on their caravans take the train: a third of the trip, never lost on the way, with steam and a bell at the
  stations ("A train from Thornholm came in with 32 × Oak Log"). A station broken while a train is on its way leaves
  the caravan to walk the rest, goods and all; without stations caravans walk as before. Setting `villageTrains`
  (behind the 2.3 gate) (39.7).
- **A Sophisticated controller as the Storehouse's store (2.1, behind its gate).** A Sophisticated Storage controller
  in the Storehouse's area makes the barrels and chests it links one store: counted once, porters put deliveries in
  and builders take materials through it, and the board says "Linked through a storage controller: 3 blocks". Break
  the controller and they are separate stores again. A "Sophisticated" style builds the Storehouse with the mod's
  chests and barrels and a controller on top of the Storehouse block.
- **Carved Wood and Rechiseled in the styles (2.1, behind its gate).** A new **Spruce** style builds any blueprint in
  spruce, and with Carved Wood its chests, bookshelves, lecterns, crafting tables, ladders, lanterns and beehives are
  Carved Wood's spruce ones: a Library in Spruce has spruce shelves, and its Librarian still takes the spruce lectern
  (Carved Wood registers it as the same job site). With Rechiseled, the **Grand** style's floors are patterned dark oak
  flooring and its wall panels andesite squares. Without the mods both look as before; Carved Wood chests and barrels
  are supply chests like any chest.
- **Cobblemon depth (2.2), the plan.** The design note for raid dens, more Mega trainers and Mega Stones, and
  Cobblecuisine dishes and bean farms (docs/design/M38.md), its expansion gate (off until 2.2), and Cobblemon Raid Dens
  and Cobblecuisine in the compat tests, with a test that every id the expansion reads still exists.
- **The village knows its raid dens (2.2).** With Cobblemon Raid Dens, the dens within 160 blocks of a Village Hall
  show on its Defense page ("Raid den, tier 3, Fire, active") and on the village map, found as their chunks load.
- **Den Wardens (2.2).** Post a guard to a raid den from the Defense page: they stand watch by day, the chronicle
  says when the den glows again, and a player who joins a raid there gets a cheer. Switch: `denWardens`.
- **Village trainers challenge the raid dens (2.2).** Every 3 days a village's Trainer Leader or Master takes on an
  active den nearby (up to tier 3, never one a player is at; tier 4 and up are for players). A win brings that
  tier's raid loot home to the village store and the den goes dark until it resets; a loss sends the trainer home
  tired for a day. Players nearby are told, so they can go and watch. Switches: `denChallenges`, `denChallengeDays`.
- **More village trainers Mega Evolve (2.2).** With Mega Showdown, a City's Trainer Leaders, the Festival Cup's
  finalists and the Stadium Day's visiting trainers carry a Mega Stone like Masters do, and Mega Evolve in battle
  against you. Every village trainer who can wears a Mega Bracelet (a gold band with the rainbow Key Stone) on their
  wrist, so you can tell before you challenge them. Switch: `villageMegas`.
- **Mega Stones and Key Stones from the village (2.2).** With Mega Showdown, a Journeyman Jeweler cuts Key Stone Ore
  into a Key Stone, and a Mega Crystal (or 4 Mega Meteoroid ores, for a Master) into the Mega Stone of a Pokémon your
  village's own trainers use, each with 2 gold ingots and 4 emeralds. Miners take the Key Stones, Mega Stones and ores
  they dig to the village store for the Jeweler. Beat the Trainer Leader of every village of a realm and the capital's
  hall gives you a Mega Bracelet, once per realm. Switch: `villageMegas`.
- **The Mega Shrine (2.2).** With Mega Showdown, the Blueprint Table has a new decoration: Mega Showdown's pedestal on a
  dais of Mega meteorite bricks, with amethyst-crowned posts and soul lanterns. On festival days a village with a
  finished shrine holds a show there first: a village trainer climbs to the pedestal and Mega Evolves their ace, and
  the villagers who gather to watch get +3 mood. Switch: `villageMegas`.
- **Cobblecuisine on the menus (2.2).** With Cobblecuisine, the Chef cooks its dishes from their own recipes with the
  makings from the store: salads and sandwiches from Novice, the drinks from Apprentice, curries from Journeyman, cakes
  from Expert, keeping 16 of each. Each dish counts as its kind of food (a salad, a sandwich, a curry...), so villagers
  eating them have a more varied diet; the Camp Cook cooks three of its curries, and the Innkeeper serves each guest a
  Bouncy Brionne Soda, Teddiursa Iced Coffee or Fruit Punch. Switch: `cuisineDishes`.
- **Bean farms (2.2).** With Cobblecuisine, farmers sow its bean seeds on their fields when the store holds fewer than
  16 of a bean colour a dish on the menu needs, cut the beans only when fully grown, and take them to the store like
  any crop. With enough beans of every colour, or no bean dish on the menu, they plant none. Switch: `beanFarms`.
- **Treats for partners and dish luxuries (2.2).** With Cobblecuisine, partner Pokémon helping villagers at work eat
  a Poké Puff or a shake from the village store once a day as their treat (more friendship, with hearts), and two
  dishes become luxuries: a Deluxe Shake for Burghers every 4 days and the Clodsire Eclair for Nobles every 8. Without
  Cobblecuisine they aren't wanted. Switch: `cuisineDishes`.
- **Basements.** Eight homes and workshops get a Level 4 that digs a furnished, lit basement under the finished Level
  III, down a ladder or a stone stair from inside: a root cellar under the Starter Cottage, wine cellars under the Stone
  House and the Noble's Manor, a cellar of casks and coal under the Burgher's Townhouse, a stockroom under the Supply
  Shop, an archive under the Library, a herb store under the Healing Center and an armoury with a lock-up under the
  Lookout Tower. The builder digs the room out of the ground himself and leaves the house above as it was.
- **Cellars from Level 1, and a Butcher's Shop.** A new Butcher's Shop (a striped awning, meat in the windows, the
  smoker in a brick chimney breast, a pen behind) with an ice-lined freezer under it; the Smithy now has a coal store
  under the forge, the Kitchen a cold larder and the Storehouse a vault of chests. Ones already standing keep as they
  are; a new one digs its cellar.
- **Fourteen new decorations.** Small builds that aren't houses, in the Blueprint Table beside the Fountain and the
  Street Lamp: a Market Cross, a Stag Statue, Flower Planters, a Signpost, a Stone Archway, a Lily Pond, a Hay Cart, a
  Notice Board, a Lantern Pergola, a Fire Pit, a Sundial, a Topiary Garden, a Market Corner and a Footbridge. Each adds
  1 to 3 to a village's beauty. The Flower Planters are unlocked from the start; market-day traders sell the rest.
- **Every workplace has its own building.** The twelve workplaces the Steward asks for no longer share one house: the
  Builder's Workshop is a saltbox studio with a crane yard, the Carpenter's an open saw shed with log piles, the Kitchen
  a cookhouse with a domed bread oven and herb beds, the Post Office a jettied townhouse with a dovecote, the Guard House
  a battlemented stone blockhouse with a watch hut, the Clinic a white pavilion and ward with cross windows, the Ferry
  House a gambrel boathouse with a landing stage, and with Cobblemon the Trainer's House a veranda dojo with a sparring
  ring, the Leader's Hall a columned portico, the Ball Workshop a round drying tower under a copper cone, the Trade Hall
  a market hall on arches and the School a brick U round a courtyard. Ones already standing stay as they are.
- **Pick a style at the Blueprint Table.** A row of style buttons under the 3D picture (as drawn, Stonework,
  Sandstone, Dark Oak, Cherry, Grand, and Apricorn with Cobblemon; only those that change the selected build) recolours
  the picture and the material list, and Get Blueprint hands the blueprint out already in that style. Locked and "???"
  builds have no style row. Sneak-clicking the air with a blueprint still works too.
- **The Blueprint Table is a collection.** Each village starts with the basics (18 builds: the Starter Cottage, the
  first Stone House and Terrace, and a first workplace for each everyday job); the rest show greyed out with how to get
  them, and rare builds show as a dark "???". Buying a blueprint from a builder or a market-day trader, a builder's
  level-up, a questbook reward or a blueprint found in a chest and used on the Table adds it to the village's library,
  with a line in chat. Existing villages keep everything they have built or are building, and uploads and scans are
  always unlocked. New operator command `/workplace unlock <player> <blueprint>`; server switch **Blueprint Unlocks**
  (on; off puts every build back in the Table).
- **A Village Hall to build.** The Blueprint Table has a Village Hall build from the start: a timber hall with the
  Village Hall block inside, the village bell under its porch, a notice board, benches, banners and lanterns. Finished,
  it works just like a hall you put down, and it's yours. Hall II adds a meeting room, Hall III a stone tower with a
  clock and a great bell; it comes in every build style. The questbook's "Build the Village Hall" follows the Starter
  Cottage and says why it matters.
- **Every material at a glance.** The Blueprint Table's materials scroll with the mouse wheel, and **+N** or the new
  **All materials** button opens the whole list (icon, count and name, biggest first; Esc or Back closes it). Holding
  Shift over a blueprint packs its materials several to a line, so a big build shows them all in about 20 lines, and
  the "still missing" list does the same.
- **The questbook is one story, by village rank.** Six chapters replace the old ones per topic and per expansion:
  Founding, The Hamlet Grows, A Village, A Town, A City and Wonders. Each has one line of quests in order, each after
  the last and saying exactly what to do next, with optional side quests off it. Cobblemon's quests are side quests
  that appear only with Cobblemon, and an expansion's quests appear only once it's switched on. More quests tick
  themselves off: a rank reached, the first caravan, festival and hall quest, a raid survived, a Steward appointed, a
  colony founded and a Wonder finished (hidden advancements, no toasts). The home and hall quests still unlock the
  next level in the Blueprint Table, and Village Hall II and III are quests of A Village. The old chapters are removed from the server
  on the next start, unless Keep Questbook Edits is on.
- **The questbook says what to build.** The Builder chapter starts with "Build a Starter Cottage" (what it's for and
  how, step by step), then the homes' upgrades I to II to III; the Jobs chapter has a quest per job naming the building
  for it, and the item to use when the job has no table of its own. Build quests tick off by themselves when a builder
  finishes that building for you (hidden advancements per blueprint; imports and Wonder parts give none).
- **See a build before you pick it** (26.9). The Blueprint Table's library shows the selected build in 3D beside its
  materials: it turns slowly, you can drag it round, and big builds shrink to fit. Uploaded and scanned builds get one
  too, drawn from their own blocks; the server sends only the blocks you could see, once per build.
- **Retrain a villager who already has a job** (26.6). In Cobblemon villages nearly everyone works, so nobody was left
  to become a Builder: now sneak-right-click any grown villager with a job by a free Blueprint Table, holding a Blueprint
  or a Blank Blueprint. You're shown the price first (5 emeralds for a Novice up to 50 for a Master, in CobbleDollars
  with the Cobbleverse pack); a second click within 5 seconds pays, and they start again as a Novice Builder, their old
  trades gone. A builder given another job now hands their build back first, its blueprint still placed for another
  builder. Game rule `workplaceRetraining` (on) and config `retrainPrices`.
- **A questbook for FTB Quests** (26.8). On a server with FTB Quests, the quest book gains Alive Workplace's chapters,
  start to finish: Getting Started (your starter, apricorns, Poké Balls, first catch and battle, and with Radical
  Cobblemon Trainers your first trainers and gym leader), The Builder and the Blueprint Table, Hiring and Retraining,
  The Village Hall and its Ranks, and Pokémon in the Village, 138 quests in all with the expansions' chapters (1.1
  to 2.0), which come in by themselves when each expansion is switched on. Rewards are emeralds and experience. The
  chapters are added once, on the first start with FTB Quests: a server owner's edits and deletions are kept. The
  guide's new page, The questbook, lists them.

### Changed
- **The Jeweler cuts Mega Stones only from what you put in the Storehouse.** Mega Stones and Key Stones now take their
  Mega Crystal (or ore), gold and emeralds from the village store alone, never from the Jeweler's own chest or other
  workers' chests. Short of something, the Jeweler waits and says what to add: "needs 4 × Emerald in the Storehouse".
- **The Signpost's boards say something.** A finished Signpost names the nearest village each way and how far (or
  the nearest building, or "Welcome"), and keeps its boards up to date at dawn.
- **Read a long entry in the Village Hall without clicking it.** Move the mouse from a row into the reading pane and
  it keeps reading that row, so the wheel scrolls it there; Shift and the wheel over a row scroll it too. An arrow
  shows when there's more below, and a long chronicle entry is shown whole. The Storehouse board works the same.
- **The Blueprint Table's preview shows basements.** A build with a cellar is drawn whole in the 3D preview, cellar and
  all, with a faint line where the ground will be; its size line says how far it goes below ground.
- **Eight more village quests complete by themselves.** The questbook no longer asks you to click a checkmark for a
  baby born in your village, 3 and 7 levels of research, proclaiming an edict, your first Burgher and Noble
  households, founding a realm or collecting the takings: each quest ticks off when it happens. A village that got
  there before this update ticks them off at its next dawn.
- **"Take the fight to them" asks for what its story tells.** The questbook quest now completes when you and your war
  party break a raiders' camp, not for any 10 pillagers killed anywhere; everyone leading guards in the party gets it.
- **The Village Hall and the Storehouse board look like the Blueprint Table.** Both screens now use the Table's dark
  panel, gold title, list on the left and reading pane on the right. In the hall, the village's numbers and the toolbar
  sit as icon buttons under the title, and the page tabs and everyone who lives there are a list with each villager's
  name and job. The Storehouse board lists the requests with what, how many and for whom, and no longer looks like a
  locked chest. Point at anything and the pane tells you all about it (long tooltips no longer cover the screen); with
  the mouse elsewhere it shows the village's numbers or the store at a glance. Click a villager to keep them in the
  pane and scroll. Every button and click works as before.
- **Fewer fishermen.** Storage barrels in builds made most villagers fishermen: now a village takes one fisherman, plus
  one more for every ten villagers. After that, jobless villagers pass free barrels by and take another workstation. A
  fisherman a village already has keeps his job. Settings: Fisherman Cap (on), Fishermen per Village (1) and Villagers
  per Extra Fisherman (10).
- **No two kinds of home look alike any more.** The Stone House is now a stone croft with crow-stepped gables, a
  chimney breast and a woodpile, and grows a second storey in stone instead of timber; the Terrace is a brick row with
  a bay window and a hooded door for each home; the Artisan's House has a red tile gambrel roof, weatherboards and a
  hoist beam over a loft door; the Townhouse is warm stucco under a slate mansard with little iron balconies; the
  Manor's roofs are hipped. The Starter Cottage stays the timber-framed one. Beds, chests and rooms are where they
  were: homes already built keep their villagers, and an upgrade builds the new look onto them.
- **Every job's village house looks different.** The 32 kinds of house our villages grow (one per job) were one
  timber house per village style with a different room inside; now each job has its own building, in its village's
  own materials: a Guard House with a battlement and watch hut, a Kitchen with a domed bread oven, a Mortuary chapel
  with a small graveyard, a Tinker's Shop with a glass monitor roof and a brick smokestack, a Print Shop with
  crow-stepped gables, a Gem Grotto mound sprouting amethyst, an A-frame Camp Kitchen, a hay-thatched Berry Nursery
  and more (each one is in the wiki's Workplace looks page). The Weaver's Cottage is now a stone workroom with an
  open drying loft hung with dyed cloth, and the Inn has its guest storey jettied over the street under twin gables.
  Houses already standing keep their old look; the jobs, beds and villagers inside are unchanged.

### Fixed
- **The Mega trainers and the Mega Shrine show again (B191).** The Mega trainers scene asked whether its City's Leader
  could Mega Evolve before the game had filed the new Village Hall, and let the hall's first count make the test
  village a Hamlet just before the battle, so the Leader had no Mega Stone and no bracelet; it now asks once the hall
  stands and keeps the village a City. The Mega Shrine scene called the show by hand instead of holding a festival:
  now the village's own festival day comes, the hall plans it, and the festival holds the show, as in a real world (a
  new compat test proves that path).
- **A raid den by a Village Hall is found in a real world.** Dens were looked for only as their chunk loaded, so a hall
  set down by a den already in view, or a den set down near a hall, stayed "No raid den known": no warden could be
  posted and the Leader found no den. Now a crystal set down in a loaded chunk is seen at once, and a new hall looks
  through the loaded land within 160 blocks for the dens already there (B190).
- **Tom's storage terminal opens beside the Storehouse.** The showcase placed the terminal backwards (facing away
  from the inventory connector) and clicked it from the overview camera, ten blocks away, so Tom's mod said "Terminal
  out of range". The mod was never at fault: a terminal placed against the connector, as a player places it, opens
  Tom's screen with the village's goods, now proven by a compat test (B192).
- **Holding a Create Schematic after using it on the Blueprint Table no longer crashes the game.** A written Schematic
  without its size (made by a command or another mod) crashed Create's own schematic code when held ("getBounds() is
  null"); the table now fills in what Create's Schematic Table writes, its size read from the file, and still lists
  and imports it (B189).
- **Raiders beside or behind a besieged village's walls go round to the open gateway.** Sent straight for the way in,
  a raider a fight had taken beside the walls could be led to the outside spot nearest the yard, behind the back wall,
  and stand there for the rest of the siege with the gate broken open; now it goes round to the front of the breach
  first, and in from there (B175).
- **A villager whose path failed once walks again.** A walk asked for after a failed path waited for a retry that
  never came when the job asking kept no count (a successor following the elder, a retired mentor going to the hall):
  the villager stood still from then on. It is now tried again a second later; the elders' showcase, where young Tom
  stopped after two blocks and Bram looked the faster, walks them side by side again (B187).
- **A Signpost no longer names the same place on two boards, or its own village.** A village standing exactly on a
  diagonal from the post was written on both boards beside it, hiding the village one of them should have named, and a
  village hall placed only a moment before could be taken for a neighbour. Each place now goes on one board only, and
  the post's own village is always recognised (B186).
- **Villagers are never left stuck inside a block.** In a growing village a baby could play in a nook too small for a
  grown villager (under a roof's stair, beside a lantern on a fence) and grow up inside the block, and a villager
  standing in a doorway could have the door shut on it. Such a villager now opens the door it is shut in, or steps out
  to the nearest free spot, within a second (B185).
- **The Trainer Leader gets to the Leader's Hall's Training Post.** The red carpet started right inside the double
  door, under its low lintel, and a villager stepping up onto it there hit its head, so a leader coming in by the middle
  of the door stood in the doorway for good. The carpet now starts a step further in. A Leader's Hall already built
  keeps its carpet: take up the first piece by the door if your leader is stuck there (B170).
- **Villagers no longer get stuck in doorways.** A villager is taller than a player, and a carpet, a seat or a stair
  step right inside a door two blocks high made it hit its head on the lintel and stand in the doorway for good. Every
  blueprint is now checked for it, and these were fixed: the Healing Center (front and ward doors), Supply Shop (its
  rug, and the shopkeeper's seat moved aside of the back door), Village Hall and its meeting room (one bench seat and
  the chair at the doorway's end of the council table taken out), Town Hall, Manor (a fanlight over the front door in place
  of its lintel, since the grand stair starts right inside; the parlour rug), Stone House III, Royal Hall (and the bench seats inside its new doors), Guildhall, Chapel, Pokemon
  Center (every look), Leader's Hall village looks, Tailor's Shop, Jeweler's Workshop II, the Bandit King's throne
  room, the pirate ship's cabin, the Observatory's chart room and the Hanging Gardens' arbors. Builds already standing
  keep theirs: lift the piece of carpet (or the seat) just inside a door where a villager stands stuck (B173).
- **Villagers get into the War Camp's tents, and past the workshop beds.** A lamp pole stood in the middle of each
  canvas tent's mouth, the only place a tent is two blocks high, so nobody reached the bed inside; the Warlord's tent
  had red carpet under its low sides, where a villager stood at the carpet's edge for good. The poles now stand beside
  the mouths and the carpet is a runner down the middle. A villager also walks over a bed (it is lower than its step),
  so a bed right inside a door trapped it under the lintel: the Carpenter's Workshop's bed turns its head to the wall,
  and the builder's and carpenter's village workshops have their side door a block or two along. Camps and workshops
  already standing keep theirs (B178).
- **No more villagers stuck on a step under a low ceiling, away from doors too.** A villager stepping onto a carpet,
  slab, stair or bed with less than its own height over it stood there with its head in the ceiling. The Starter
  Cottage's chair by the front window had the blinds right over it (the blinds there are gone), the upstairs rug lay
  under the tie beam (it now lies by the bed), the Pokéball workshop's drying loft had apricorns under the spire's low
  foot (they dry under the spire now), the Lodge-look Pokémon Center's flower boxes stood on the step round its walls,
  and a few moss patches in the Hanging Gardens lay under ledges. Builds already standing keep theirs (B197).
- **Chefs take the Kitchen's smoker, however it's decorated.** A villager walking to a workstation built in under
  solid blocks (the smoker in the Kitchen's brick oven) climbed onto the nearest chest or worktop instead, a step too far
  to take it, and stood there; it now walks to the floor in front of the workstation and takes it (B168).
- **Villagers sleep in furnished homes' beds.** A furnished home's fancy beds are now checked to be beds villagers
  claim as their home and sleep in (Handcrafted's are), and a furniture mod's bed that isn't one leaves the vanilla
  bed in its place, so every bed of a furnished home is a villager's bed (B166).
- **"Your first catch" completes on Cobblemon 1.8 too.** The quest waited for Cobblemon's `first_catch` advancement,
  which Cobblemon 1.8 moved to `catching/first_catch`, so it never ticked off there; it now uses the mod's own
  advancement on Cobblemon's catch trigger, which works on 1.7 and 1.8 (B164).
- **Every setting's name fits its button.** On the settings screen "Villagers per Extra Fisherman" ran past its
  button; it is now **Extra Fisherman Every**, and "Gifted Villagers (1 in ...)" is **Gifted Villagers, 1 in** so it
  fits at 1000 too. A test now measures every setting's name at its widest value (B161).
- **Cellar ladders no longer trap villagers.** A cellar's ladder came up through an open hole in the floor and a block
  above it, and villagers walking past climbed it onto the window sill beside it and got stuck (a Chef never reached
  the Kitchen's smoker). The ladder now ends under a trapdoor hatch in the floor: open it to climb down or up (B158).
- **A Great Wall sentry climbing back up gets all the way to its post.** One that went up the wall's face landed a
  block or two off its post and stood there, with no path for a golem along the narrow walk; it now takes the last
  steps straight over (B144).
- **Two builders on one house really take about half the time again.** Near the end, the lead took what it was short of
  from its helper and left the rest of the build's last things (a lantern) in the chests, then walked back for them
  alone at the very end while the helper stood about. It now picks those up while the helper is still busy (B155).
- **A Wonder's parts no longer count as buildings.** Each finished part used to write "Odo finished the Stage 1 0" in
  the village's chronicle, join the village's list of buildings, and get a lane or dirt path to its "door". Now only
  the Wonder's own lines tell its story, and a new Wonder still can't be drawn over a finished one (B143).
- **Every seat in the Stadium can be walked to.** Its aisles climbed two blocks a tier on a single stair, so nobody
  could get past the second row; now each aisle is a proper flight, two stairs to a tier, and the rows round the
  curved corners join up so you can walk right round every tier. The blueprint check now also looks at open-air
  stands and terraces, not just upper floors under a roof (B154).
- **Fishermen take their cooked fish out of the furnace.** The cooked fish used to sit in the furnace until his next
  catch came home, and all night once his shift ended; now he comes back for them as soon as they are done (also after
  a reload mid-cook), and puts them in his barrel like his catch (B147).
- **A villager called to the bucket brigade gets their tool back.** The water bucket used to replace whatever they
  held for good (a fisherman's rod, an axe); the same tool is back in their hand once the fire is out, even if the world
  was saved in between (B152).
- **The Village Hall's food count matches growth.** "Food in store" now counts only meals, the same number a baby
  waits for, so rotten flesh, spider eyes and golden food in the store no longer make it look full (B153).
- **A ram no longer stalls behind a Gatehouse's portcullis.** After breaking the middle bars, a ravager sometimes stood
  just out of reach of the fence gate behind them and never struck it, so the raiders waited outside all night.
- **Chairs face their tables, and every upstairs can be reached.** Stair chairs in the Inn, Library, Terrace,
  Farmstead, Artisan's House, Townhouse, Map Room, Royal Hall II and the village kitchens sat with their backs to the
  table; they now face it. The Storehouse's loft (all three tiers) gets a ladder up through a hatch. Other ways up that
  were missing or a block short are fixed too: the Artisan's House ladder now reaches through its hatch, the Tinker's
  Workshop II ladder moves to where the loft has headroom, a board joins the Farmstead II ladder to its loft, the
  Guildhall stair, the Well House steps and the Coven Hut's deck stairs get their last step, the Manor's newel posts no
  longer block the bedroom doorways, the Pirate Ship has a boarding ladder at the bow, the Observatory's tower door and
  wing doorways lose the plinth step that made a player's head hit the lintel, and the Stadium's professor's box gets a
  ladder from the players' tunnel. The blueprint checks now fail on a chair facing away from its table and on a
  furnished upper floor with no stairs or ladder up to it.
- **Every questbook quest now says plainly what to do.** All 107 quests were rewritten: each opens with its goal, gives
  the steps by the real block, item or hall button ("hover over the bread in the hall's top row"), says why it matters,
  and ends with how it completes ("completes by itself when the builder finishes it", or "Click the checkmark above
  once..."). "Food in the store" now explains which chests are the food store and what counts as a meal. A few wrong
  details are fixed on the way: the Poké Ball recipe, the Cup command (`/workplace cup ready`) and what Nobles need.
- **Picking another build with the full materials list open no longer crashes the game.** The list shows "Loading" for a moment instead.
- **The campfire goes on top of a tall chimney.** On houses whose chimney rises well above the upstairs floor (Starter
  Cottage II and III, the Farmstead, the Fisher's Hut, the Smithy, the Noble's Manor and others) the builder had nowhere to
  stand to reach its top, so he skipped the campfire, then came back for it on every repair round: out of the chest, over
  to the chimney, "repaired", and back in the chest, over and over. Now he climbs onto the roof beside the chimney to put
  it on, and a block a repair really can't reach is tried once and then left alone.
- **A farm stays the farmer's.** Villagers no longer trample farmland into dirt; a farmer works a trampled patch of his
  field back into farmland and sows it, even without a hoe; and builders no longer treat cut or trampled crops of a
  finished farm as damage to repair with wheat seeds (the first build still plants the field).
- **A fisherman's rod lasts as long as yours.** Standing by a hired fisherman (just after hiring him, or in the evening)
  made vanilla's trade display empty his hand, and the rod was gone after a catch or so. Now the rod stays in his hand
  and wears one point a catch, and it takes its enchantments as a player's does: Unbreaking wears it slower, Mending
  repairs it with each catch's experience, Luck of the Sea brings better catches and Lure shorter waits for a bite.
- **Six builds from the owner's playtest look right** (2026-10-16). Starter Cottage II's upstairs back windows sit in
  their bays over the front ones instead of cutting into the corner post; the Storehouse's west window is centred like
  the east one; the Terrace loses the bench that hung in the air beside its east door; the Weaver's Cottage's wool
  bales sit on the ground behind its west window, no longer over its sill and shutter; the Smithy's lean-to lantern
  stands on a post instead of on the ground; and the Apiary Garden's fence stands on the garden's grass, a block above
  the flower beds, instead of level with them.
- **No more gaps where roofs meet at a corner.** Where a wing's or a porch's roof runs into the main roof (the Village
  Hall, Storehouse, Smithy, Barracks, Ranch, Research Lab, Supply Shop, Healing Center, Townhouse, Physician's House and Fletcher's Lodge), the stairs left a little
  hole into the attic at each step of the valley; the corner is now packed with a roof block. Village Hall III's tower
  loses the stray stone wall that stood out a block from its west side (jutting past its front and back and walling up
  the belfry's west arch), and its cornice now runs round all four sides.
- **The Village Hall build is framed properly.** Its logs now stand as posts at the corners and at even bays with one
  beam round the top, and every window is the same 2 x 2 at one height, in a bay, with a flower box or a sill; the
  gables, the meeting room (Hall II, now entered from the hall) and the stray lamp post are tidied to match.
- JEI, REI and EMI no longer list the 26 retired workstations (the Kitchen Stove, Postal Desk and the other old benches that
  can't be crafted and are kept only so old worlds load); the jobs' real workstations are unchanged.
- **Questbook updates reach the server.** FTB Quests rewrites every chapter it saves (and moves its text to its lang
  file), so every one of our chapters looked edited and kept its old layout and quests for good. Now a new version of
  a chapter replaces the old one on start, and the old text of our quests is taken out of FTB Quests' lang files (the
  pack's own entries stay). A chapter you deleted stays deleted; the new `questbookKeepEdits` switch keeps every
  chapter already there as it is.
- **Roofs that meet, and no stray gaps in builds.** Where an upgrade's new room runs its roof in under the old roof's
  edge (the Starter Cottage III's kitchen, the Inn II, the Library II and III, the Manor II and III, the Supply Shop II
  and III, the Pokémon Center, the Schoolhouse, the Winery and others), the wall now carries on up to the old roof, so the two roofs meet instead of leaving a
  row of open blocks between them. The Graveyard II's lych-gate beam runs over its back fence posts (the gap above them
  is gone), the Manor's inside walls carry on through the upper floor, the Inn's stacked barrel has one under it, the
  Chapel's string course reaches the tower's corner, and the Fisher's Hut boat shed's back gable is shut. Builds
  already standing keep their blocks; build or upgrade again to get the new ones. (Dev: `tools/blueprints/holes.py`
  finds roof holes, gaps between roofs and stray empty blocks in every blueprint: 512 before, 16 left, all on purpose.)
- **Questbook chapters you haven't edited now update** to the mod's new version (they used to keep the old layout
  forever). Chapters you edited are kept (delete one to get a fresh copy), and unedited ones you deleted stay deleted.
- **Small things in the Wonders' looks** (B141). The Stadium's scoreboard reads 2 - 1 from outside the bowl too (from
  behind it read "1 - S"). A festival, a Cup's final and a won siege still make you a Hero of the Village, but no longer
  send its pale-green swirls up past your eyes, where they floated in the sky over the fireworks. A button's tooltip on
  a menu screen (the research screen, the lessons...) now opens under its row with its long lines wrapped, so the
  Great Library's Scholarship tooltip no longer covers the second-tier row, and it no longer ends with "A villager's
  workstation: hold Shift for its jobs" (its picture is a lectern). The found-goods showcase now films the Foundation
  Stone above the chat, so the Pathfinder's line no longer shows through the stone's status.
- **Villagers no longer end up stuck in a wall a builder has just put up** (B132). A builder checked for room for a
  stair as drawn in the blueprint, but a corner stair whose neighbour isn't built yet goes down straight, a quarter
  bigger, sometimes onto a porter walking past. It now checks the stair it really puts down, and anyone a new block
  leaves inside a wall is moved out to the nearest place to stand.
- **A builder finishing a part of a Wonder now says so properly** (B138). The chat used to read "Odo finished
  building Stage 1 0! The blueprint is back in the supply chest." (no blueprint comes back for a Wonder's part); now it
  says "Odo finished stage 1 of 4 of the Colossus: The Plinth and the Road Arch." or, while other parts of the stage are still
  going up, how many of them stand. The Wonder's very last part leaves the telling to the Wonder's own announcement.
- **Legends take the village's own luxuries** (B136). A Legend who likes wine now drinks the Vintner's Cider, Berry
  Wine and Vintage Wine; one who likes jewels takes the Jeweler's rings, brooches and circlets; books, the Printer's
  Village Gazette and Illuminated Book; clothes, the Tailor's Work Clothes, Fine Clothes and Noble Robes. The honey bottles,
  gems, books and leather armor they took before still count.
- **A village at peace posts no more bounties** (B134). A village set At peace on its Defense page, or every village
  at threat level 0 or on Peaceful, no longer puts new captains on its Bounty Board, and the Bounties tab says why;
  bounties already up stay until they're paid or run out.
- **A Wonder's crew no longer stops for good partway up** (B139). When the lead had to give up on one block (a
  crewmate stood on it the whole time, as on top of the Colossus's half-built robe), the wall above it waited for that
  block forever and the whole crew stood idle; now the wall goes on over the gap, also after a restart. And a block that
  changes by itself once laid, like farmland drying out before its water comes, is no longer broken and laid again
  over and over (the Hanging Gardens never got past their first terrace, and it used up the chests' stone): the crew
  leaves it, and a crop to go on dried-out farmland is planted after the builder tills it again.
- The settings screen's **Rogue Bounties** and **Builder Scaffolding** switches (renamed from Rogue Pokémon Bounties
  and Builders Use Scaffolding) now fit their buttons when turned off (B131).
- **The Seer now warns of raids two days ahead**, as promised (B133). A Seer living in the village used to foretell
  only the coming night; now, as soon as raiders are on their way, everyone in the village (and the hall's owner,
  wherever they are) hears "A vision of the north road: ... will strike Oakvale in 2 days", the chronicle notes it and
  the Defense page shows it. On the day the gates shut from noon and the villagers go in before dark, just as with a
  guard on a Lookout Tower, and the raiders come from the side the Seer named.
- **The Sickness now turns to your own Physician** (B135). A village with a Physician and no Nurse used to send for
  Doctor Wren anyway; now the Physician is the healer: talk to them about the Gray Cough, bring them the herbs, and they
  brew the Three-Herb Tonic at their brewing stand and take it round, with no traveling doctor sent for. A Remedy, from
  them or from your hand, only eases the cough for a day, as the story says, and a villager a Remedy is keeping well
  doesn't catch it. With **Physicians** off, Doctor Wren comes as before.

### Dev
- **The pack test builds furnished homes** (37.11). `FURNISHED=true tools/packtest/run.sh` boots Cobbleverse with
  `furnitureMods` set to Cozy Home first and has two builders build the Starter Cottage and the Inn; it passes only when
  both finish with nothing skipped, every block and furniture piece in place and Cozy Home pieces in each. The nightly
  run does it too. The wiki's "Furniture and storage mods" page gains a table of each mod, and the player guide a page.

## 0.144.0 — 2026-10-13

### Added
- **The Observatory's powers** (35.18, part of Wonders, for 2.0): while it stands, the hall searches the land about
  6,144 blocks across round the village, one kind of structure an in-game hour (and again each week), and the hall's
  map button also draws the Sky Chart on a second empty map: a marker on every village, outpost, temple, witch hut,
  igloo, monument, mansion, shipwreck, ruined portal, trail ruin, ancient city, trial chamber and stronghold found,
  and on the bandit camps, the nearest named in its tooltip. The hall's calendar shows the Coming days: the next
  festival and market day, weddings due, caravans and when they arrive, a traveller expected at the inn, the full
  moon, the Seer's foretold guests, bandit camps and the night's raid risk. A damaged Observatory pauses both. Scene
  `wonder_observatory`: the time-lapse, then the Sky Chart in hand and the Coming days.
- **The Great Wall's powers** (35.16, part of Wonders, for 2.0): while it stands, no raid gathers behind it. Night
  raids, bandits and the other raiders gather outside the stretch of the compass the Wall covers from the hall, plus
  10 degrees either side, and a siege can't put ladders against it or ram its Great Gate. Four Wall Sentries come with
  it and stand in the towers' alcoves. They fight anything within 16 blocks of the wall, go back to their posts after,
  and step out onto the walk to watch raiders further off. One pushed off climbs back up. A lost one is replaced the
  next morning by the Golem Smith (or a Tinkerer) from 4 iron blocks and a pumpkin in the stockpile. The Great Gate
  shuts at night. A damaged Wall turns both powers off. Scene `wonder_great_wall`: the time-lapse, then a night raid
  gathering on the open side while the sentries watch.
- **The Great Library's powers** (35.14, part of Wonders, for 2.0): while it stands, the village's research opens a
  second tier, a row of its own on the scholar's screen ("Needs the Great Library" until then): Swift Hands IV-V,
  Scholarship (scholars 25% faster a level, research a quarter cheaper at II), Engineering (builders' bags 9 more stacks
  a level, Wonder crews 2 more builders a level), Cartography (explorers twice as far, maps to trial chambers and
  ancient cities), Trade Law (caravans 2 more stacks a level, the treasury 10% more a level) and Pharmacy (the ill well
  in a day, each Nurse curing one a day without a remedy). Each level costs 24 paper, 6 books and 8 emeralds times its
  level and twice the points; work still stops at twice the pace. A damaged Library keeps the levels and pauses their
  bonuses. Its time-lapse, ending in the rotunda on the scholar's screen, is on the showcase page.
- **The Stadium's powers** (35.24, part of Wonders, for 2.0, with Cobblemon): while it stands, every second afternoon
  is a Stadium Day: two to four visiting trainers from the realm's villages (or travelling Journeyman to Master
  trainers) stand in its boxes until dusk, and players challenge them for prize money, once a day each; the village's
  trainers fight them in bouts settled by rank and team, with the results in chat and the chronicle; the villagers off
  work fill the stands and are 5 happier. The Festival Cup of the villages taking part is held at the nearest Stadium,
  which serves as its Arena. A damaged Stadium holds none of it. Its time-lapse and a Stadium Day are on the showcase
  page.
- **The Colossus's powers** (35.22, part of Wonders, for 2.0): while it stands, every villager of its village is 12
  happier and every villager of its realm (or its caravan partners) 8, with "the Colossus" among their reasons;
  travelers come to its village's inns twice as often (two a morning, room for twice as many) and are twice as likely
  to be Experts; it adds 8 beauty. Once finished, the plaque over its road arch is written in glowing letters with the
  Founder's name (the hall owner's without one), the village's name and its founding day from the chronicle. A damaged
  Colossus turns its powers off. Its time-lapse, scaffolding and all, is on the showcase page. Realm powers now reach a
  village with a caravan route either way, not only one the Wonder's village sends caravans to.
- **The Lighthouse's powers** (35.12, part of Wonders, for 2.0): while it stands, caravans from its village reach
  villages up to 4,096 blocks away and arrive in half the time; ferry fares to and from the village's Travel Posts are
  halved, and the jetty's post joins the network as "<Village> Lighthouse"; the day before drowned pirates raid, the
  keeper warns the village ("Sails off the west shore..."), the chronicle notes it and at dusk the guards gather on the
  shore. A damaged Lighthouse turns all four off. Its time-lapse by the sea is on the showcase page.
- **The Hanging Gardens' powers** (35.20, part of Wonders, for 2.0): while they stand, every farmer and orchard keeper
  in the village and across its realm (with realms off, the villages whose caravans trade with it) brings in a quarter
  more, one more of each crop for every four picked; a baby needs 12 meals in the store instead of 16, and up to two
  are born a day. Its four terraces are village farms that farmers take on by themselves (the crown now has a field by
  its farm corner too). A damaged Garden turns both off. The showcase films its time-lapse, ending the next morning
  with the falls running and farmers on the terraces.
- **The Great Cathedral's powers** (35.10, part of Wonders, for 2.0): while it stands, the village's Undertakers need no
  golden apple, potion or totem, look for graves across the whole village, and anyone still in a grave at dawn is back
  by morning. Weddings are held at its altar and blessed: everyone in the village is 15 happier for 3 days, the couple's
  first baby comes the next day there's a free bed without waiting for the store's meals, and their children are twice
  as likely to grow up Gifted. Its bells ring for weddings, festivals and raids, and it adds 8 to the village's beauty.
  The showcase films its time-lapse, ending at dusk with a wedding at the altar.
- **Wonder goods you explore for** (35.7a, 35.7b, the owner's change to 35.7, part of Wonders, for 2.0): a Wonder's
  stages no longer ask for stacks of farm produce. Each asks for two or three hard things found by exploring, one of
  each (a Heart of the Sea, an Echo Shard, a Heavy Core, a Nether Star, an Elytra...), and two of an upgraded workshop's
  best goods (Vintage Wine, Gold Circlets, Illuminated Books, Noble Robes). The stone says where each is found; a rare
  worker in the village (a Legend like the Pathfinder, or a Master Netherworker, Sifter or Fisherman) searches for it in
  play time and brings it, told in chat and the chronicle. A Wonder now takes a typical group about 44 hours of play
  (15 evenings); a Wonder saved mid-stage puts the goods handed in for its old lines into its stockpile.
- **A finished Wonder: its celebration, upkeep and powers** (35.8, part of Wonders, for 2.0): when a Wonder's last
  stage is done every player on the server is told, the village holds a festival that evening with a bigger firework
  show (gold rockets from the Wonder's own top), the chronicles of the village and of its realm's villages (or its
  caravan partners) record it, and the Village Map marks it with a gold banner. Its powers come on: `mood` and `beauty`
  work now, for server owners' own Wonders too. Once a day it counts how much of it stands (weathered copper still
  counts); under the new `wonderStanding` setting (90%) its powers stop, the stone and the hall say "damaged", and the
  village's builders repair it before anything else; back at 90%, the powers return.
- **A Wonder's goods and its dedication** (35.7, part of Wonders, for 2.0): each stage of a Wonder now waits for its
  goods (the crew's feast, their tools, the rites' candles, books and potions, its treasures), each line naming the job
  that makes it. The Foundation Stone shows every line ("2 of 4 Flowers · Florist", "no Florist in the village"); the
  lines go on the Storehouse's requests board (a request for a block, not a villager), porters carry them to the
  stockpile first, crafters make what's short, and the hall's daily quest asks for the line furthest behind. Anyone can
  hand goods in at the stone or on the board. With every line in, by day, the stage is dedicated: the bell rings, the
  village gathers at the stone, the Bard plays and the guards stand in line, the goods are taken and the chronicle says
  so. Abandoning the Wonder hands back the goods handed in for a stage not yet dedicated.
- **Builders use scaffolding** (35.6, part of Wonders, for 2.0): a block with nowhere to stand near it (a raised arm,
  a spire's tip, a dome's crown) is no longer skipped. The builder puts up a column of scaffolding from its chests
  beside it, climbs it and works from the top, then takes it all down when that stage is done and the scaffolding goes
  back into the chests. With none in the chests it's asked for on the requests board (a carpenter makes it from bamboo
  and string) and the block is skipped as before: a build never waits on scaffolding. Cancelling a build takes its scaffolding down at once. Switch: `builderScaffolding`.
- **A player-guide article for everything you can meet** (22.10a part 3): about 190 new wiki pages, one per job, block,
  item, build, threat, setting theme, game rule, command and village-life system, each with an infobox and a section
  on how it ties into the rest (what feeds it, what it affects, what stops it), all linked from the main page's A to Z
  lists. Pages for every expansion (story arcs, edicts, classes, Wonders, Legends, guilds, the Steward, the Festival Cup, disasters, realms, luxuries and friendships) are written too and appear on
  the published guide the night their expansion switches on.
- **The Colossus' design, for 2.0** (35.21, nothing to start in game until Wonders is finished): the seventh Wonder's
  build, the Founder's statue 47 high (about 9,000 blocks): a robed villager in polished diorite and smooth stone, big
  nose, unibrow and all, one arm folded across the chest and the other holding up a lantern of glass round glowstone,
  under a cut copper cloak that weathers green. It stands on a stepped stone plinth ringed with braziers, over an arch 5
  wide and 6 high that the main road runs through, with the Founder's plaque (a row of signs) over it. It rises in four
  stages (the Plinth and the Road Arch, the Robes, the Arms and the Cloak, the Head and the Lantern), each with its goods.
- **The player guide looks like a wiki**: a sidebar on every page, a main page with an article counter, an About box,
  a portal box with a picture for each subject (Jobs, the Builder, the Village Hall, Pokémon, festivals, quests, trade,
  defense, disasters, settings), a News box of what's new, and on every article an infobox (picture, recipes,
  workstation, numbers), a contents box, "See also" links and its categories, with a page per category and an "All
  pages" list. It reads well on a phone too.
- **The Stadium's design, for 2.0** (35.23, with Cobblemon, behind the 2.0 switch): an oval arena of stone round a grass
  battle field, with stands of red and white seats (in the village's own banner colours if it has a Village Banner),
  two trainer boxes with Training Posts, a professor's box, a covered stand, a gateway arch with banners, a
  scoreboard, four floodlight towers that light up at night, a nurse's booth with a Healing Machine and market pitches,
  in four stages. Without Cobblemon it isn't on the Wonders page.
- **A bigger Stadium you can walk through** (35.23a, with Cobblemon, behind the 2.0 switch): 53 by 43 now, seven tiers
  of seats along the sides and five at the ends, with aisles of stairs to climb them, buttresses, banners and flower beds
  on its facade and banners round its rim. Under the stands, all lit: a concourse right round, stairs up from it into
  the front rows, two food kiosks, a first-aid room by the nurse's booth, a trophy room, the referees' room, the
  groundsman's store, and the players' tunnel out to the pitch under the professor's box, with the home and away
  dressing rooms off it and a dugout either side of its mouth.
- **Realm edicts, for 1.7** (33.22, off until 1.7 is finished): the capital's Book of Edicts gets a row of realm slots
  (1 for a Barony or County, 2 for a Duchy, 3 for a Kingdom, one more with the Royal Hall II) and six realm edicts, in
  force in every member and paid from the realm treasury: the Royal Highway (faster, fuller caravans between members),
  the Common Market (one board at the members' average price, no spread for the realm's people), the Levy of Arms
  (relief from every member in bands twice as big), the Realm Festival (one festival day for the realm, three days of
  good mood), the Crown's Peace (no feuds on members, no bandit camps near them) and the Royal Charter (colonies from
  Town rank, half the wait). Each has its reform: a quest at the capital and a count across the realm, after which the
  boost stays and the cost goes for good.
- **The Hanging Gardens' design, for 2.0** (35.19, nothing to start in game until Wonders is finished): the sixth
  Wonder's build, a garden mountain of four sandstone terraces (39 across at the foot, about 16,200 blocks), each faced
  with arcades hung with vines and glow berries over a gallery, its ledges planted with fields between water channels,
  berry beds, glow berry arbors, flowers, beehives, oaks and flowering azaleas, and a composter and a chest on each level.
  The Cistern Vaults fill the first terrace; a ramp spirals to the crown, where a cistern pours a waterfall down the east
  side from basin to basin into a pool. It rises in four stages (the First Terrace and the Cistern Vaults, the Second
  Terrace, the Third Terrace, the Crown Garden and the Falls), each with its goods.
- **The Great Wall's design, for 2.0** (35.15, nothing to start in game until Wonders is finished): the fifth Wonder's
  build, a stone wall 97 long, 7 thick and 12 high on a battered deepslate plinth (about 10,800 blocks), placed like the
  other walls with its stairs on the village side: a crenellated walk with braziers, four towers under hipped spruce roofs
  (stairs inside, a guard's grindstone, an alcove on the walk for a sentry golem) and at the centre the Great Gate, a
  gatehouse with an iron-bar portcullis over a gate of fence gates and a bretèche over it. It rises in four stages (the
  Great Gate, the West Run, the East Run, the Walk and the Towers' Crowns), each with its goods.
- **Legends of the realm, for 1.7** (33.23): the Founder now leads colonies. The village he lives in may found them from
  Town rank, sends four settlers instead of two and waits half as long between them; its colonies are "founded with the
  Founder's blessing" in both chronicles and his statue stands beside each colony's camp. The Merchant Prince's board
  shows the prices of every village within caravan range, his village's caravans sell where they are paid most, and
  its board's spread is halved (105% instead of 110%).
- **Crews: every builder on one Wonder** (35.5, 2.0, still switched off): every builder of the village with no build of
  their own joins the Wonder's crew, up to 12 (the new `wonderCrew` setting). **[Call every builder]** under the
  Foundation Stone's status brings the busy ones too, as they finish what they're on. Each takes a column of its own
  and works it from the bottom (clearing, from the top), never more than two layers above the lowest unfinished one, so
  nothing goes up with nothing under it. Each fetches its share of a stage's materials in one trip, so eight builders
  raise the test Wonder in under a quarter of one builder's time. The
  stone and the hall's Wonders page name the lead (the Master Architect when there is one) and the crew.
- **The Foundation Stone** (35.4, 2.0, still switched off): right-click a builder of the village with a placed Wonder
  Plan and a **Foundation Stone** (carved stone, brass plaque) goes down just outside the Wonder's front corner; the
  start is announced to the whole server and written in the chronicle. The chests within 8 blocks of the stone are the
  Wonder's stockpile, and its stages go up one after another as ordinary builds (only stage I fills a foundation, only
  the last levels the ground), stage II never before stage I is done, through any restart. Right-click the stone for the
  stage, how far along it is and what the chests are short of. Sneak-breaking it (the owner or an operator) or
  `/workplace cancel` abandons the Wonder: what's built stays and the plan comes back. The README has a Wonders section.

### Fixed
- A siege's ram no longer plugs the breach it just made: once the way through is open, the rams stand aside, out of
  the gateway, while their band is still outside, and widen it after. A ravager at the next bar of a Gatehouse's
  portcullis stood right in front of the one-block way it had opened, and the raiders behind it waited until the next
  bar went too (B126).
- An Expert Habitat Keeper now founds the village's Habitat Block in a Habitat Garden a Builder built: she
  only looked for a moss block, but the garden's centre stone is mossy cobblestone, so she never did. The block now
  looks like the stone it replaces (B88, Cobblemon 1.8).
- Smoother first day on big packs (B103): the first child of a Cradle to grow up no longer holds up a tick by up to a
  quarter of a second (the code it needs is loaded in the background while the server starts), and the nearest Village
  Hall, and the building a villager's bed is in, are looked up once a tick instead of once for each thing that speeds a
  worker up or slows them down. The season
  test's first day went from 4 ticks with over 50 ms of our code to 1, and from 16.7% to 14.7% of the tick.
- A worker's status names their own job: a Miner's level line reads "Apprentice Miner · 10/70 XP…" instead of
  calling every worker a builder (B124).
- Builders no longer die from falls while on a job (on a roof, a tower or a Wonder's dome), and a Wonder's part whose
  builder is lost goes on to another builder of its crew instead of counting as built with blocks missing (35.5).
- A builder levelling the ground round a finished build with no dirt in its chests leaves the holes at once instead of
  standing there a tick for each one (about 20 seconds round a Wonder's last stage).
- Under the Common Market the realm's own people buy at the board's price and strangers still pay the 10 percent, and
  the board now says so: the Prices tab tells each player their own price, and "You can't pay …" names the price that
  buyer would have paid. Long tooltips in the Village Hall and the Book of Edicts (the Royal Highway's) are wrapped to
  fit on screen (33.22a).
- The test folly's top no longer gets a band of dirt round it under the dome, where the stone cornice is: a Wonder's
  stage high up (a dome on a tower) no longer "levels the ground" round its own bottom in mid-air. The ground is
  levelled with the last stage built on the Wonder's floor; the Colossus, the Great Cathedral, the Great Wall and the
  Observatory had the same fault (35.8a).

### Changed
- **The Great Wall's towers stand on solid, decorated bases** (35.15a, part of Wonders, for 2.0; the owner's change):
  each tower is solid stone up to the walk, on a battered deepslate base with buttresses at its corners, a ledge round
  it at the walk with lanterns on its corners, banners on both sides, a lit niche toward the village, and a lantern on
  its roof's peak. The rest of the wall is more decorated too: banners in every bay on the field side with arrow slits
  above them, taller merlons over the buttresses, vines low down, lanterns along the walk and on the village side, and
  the gatehouse gets the towers' buttresses and ledge. You now reach the towers from the walk. About 12,300 blocks.
- **Builders really climb their scaffolding** (35.6a, part of Wonders, for 2.0; the owner's change): a builder working
  high on a pillar, a tower or a dome puts up an unbroken column of vanilla scaffolding beside it, climbs it and works
  from it at that height, instead of hopping onto the unfinished pillar (which left a gap in it) or onto the cap above.
  It takes enough scaffolding for the column in one trip, steps out of the column's foot when it walks back to the
  chests, and takes it all down when that part is done. With no scaffolding in the chests nothing changes.
- **The Observatory's Sky Chart covers about 6,144 blocks** (35.18a, part of Wonders, for 2.0; the owner's change from
  1,024): 384 chunks and 12 regions across, every village, temple, monument, mansion and the rest on one locked map at
  48 blocks a pixel (a vanilla map stops at 2,048). The Observatory searches that far without slowing the server: one
  patch of land at a time, only when the tick has room, at most one kind of place an hour, and it carries on where it
  was after a restart. The hall's button that hands the chart over comes with 35.18.
- **Threats never destroy your builders' work** (32.16a, part of 1.6): only the gate blocks of walls and gates break
  under the rams, and nothing else a raider does (a creeper's blast, a ravager, a vindicator at a house door) breaks a
  block the builders placed or a build still under way. Whatever rams and village fires take, the builders always put
  back, even with Builder Repairs off, and a restart forgets none of it.
- **The Noble's Manor is sold by builders only too** (34.15b): it's no longer in the Blueprint Table. A builder sells it
  once the village has a household of Burghers or higher, and its upgrades come from the builder who finishes it; Manor
  blueprints already bought, sites in progress and finished Manors keep working.
- **The Artisan's House and the Burgher's Townhouse are sold by builders only** (34.15a): they're no longer in the
  Blueprint Table. A builder sells the Artisan's House from Apprentice on and the Townhouse once the village has
  Artisans; blueprints already bought and houses already built keep working.
- **American spelling** (22.10a, part 1): the player guide, the wiki and every in-game text now say defense, color,
  armor, gray, honor, favor, neighbor, center, traveler, Jeweler and the like; ids, config keys and saves are unchanged.

### Fixed
- Wall Sentries hold their leash when crowded (B123): zombies pressing on a sentry walking back to its post could
  shove it half a block past the 3 blocks it may step off, off the wall's edge. It now can't be pushed farther out.
- Builders no longer get hurt falling off their own build (B120): a Builder at work stands on the build's upper rows
  (a palisade's stake tops, a tower's platform) and could walk or be shoved off them, and one died building a
  Palisade Tower. A Builder with a build now takes no fall damage; everything else still hurts him.
- Village walls: a finished Palisade Tower no longer looks unbuilt to the Steward (its roof counted as the ground), so
  builders no longer stack a second tower on top of the first (B94).
- The Blueprint Table no longer offers the builds of expansions that aren't out yet (B119): the Arena and the Pokémon
  job builds (Camp Kitchen, Berry Nursery, Habitat Garden, Gem Grotto, Daycare), the Guildhall, the Fire Station, Well
  House and Physician's House, and the luxury workshops wait for their expansion, so you can't build a workplace whose
  job can't start yet. A blueprint already taken and a build already started keep working.

## 0.143.0 — 2026-10-11

### Added
- **The Realm Map, for 1.7** (33.21, off until 1.7 is finished): the Realm tab of any member's hall draws the realm on
  an empty map, scaled so every member fits: a banner for each member in the realm's colour (the capital's named
  "Thornholm (capital)"), colonies outside the realm, allies and rivals in their own colours, caravan routes as dotted
  lines, a colony on the road as a red cross, unloaded land as plain parchment, and the legend on its tooltip. Setting
  `realmMap`.
- **Feuds, and war where PvP is on, for 1.7** (33.20, off until 1.7 is finished): on the Pacts tab an owner can declare
  a feud on another player's village (or its whole realm). A letter tells them; pacts and caravans between the two end;
  each side's price board pays the rival's people 25% less and charges them 25% more; villagers grumble about the
  rivals and the chronicles note it. Either side offers peace by letter and the other accepts on its tab; a feud
  without a war fades after 7 days. On a server with PvP on, the side that declared can raise a feud that has stood a
  day to war, from the next dawn for 3 days or until peace: each side's guards fight the other side's owners and their
  friends inside their own village, and those players may fight back against the guards, but blocks, chests, other
  villagers, animals and treasuries stay protected, and relief riders never come to a war. Both chronicles record the
  guards each side lost; no treasury changes hands. With PvP off the war button says why it can't be pressed. Settings
  `feuds` and `feudWars`.
- **The weekly leaderboard, for 1.7** (33.19, off until 1.7 is finished): every Sunday at 19:00 server time everyone
  online hears how the villages did: the Biggest, the Richest (emeralds earned that week), the Happiest (the week's
  wellbeing), Most Legends and the Greatest realm, the top three of each with owner and number. Winners get a chronicle
  line and a ribbon on their hall's name icon for a week; owners can leave a village off on the Trade page;
  `/workplace leaderboard` shows the standings so far; a week missed while the server was down is read out at the next
  start. Settings `weeklyLeaderboard`, `leaderboardDay` and `leaderboardHour`.
- **The Observatory's design, for 2.0** (35.17, nothing to start in game until Wonders is finished): the fourth
  Wonder's building, a round deepslate tower 15 across and 30 high under a copper dome on a raised terrace 31 by 31
  (about 8,400 blocks): a slit of tinted glass in the dome with a great copper telescope leaning out of it, a viewing
  walk round the dome reached by a spiral stair, a star-chart room and the Seer's lodging in two low wings, and a
  courtyard paved with amethyst round a sundial and an orrery of a gold sun and five planets on chains. It rises in
  four tidy stages (the Terrace and the Courtyard, the Tower, the Dome, the Great Telescope and the Orrery), each with
  its goods.
- **The Great Library's design, for 2.0** (35.13, nothing to start in game until Wonders is finished): the third
  Wonder's building, a domed library on a cross plan (41 by 41, 34 high, about 15,000 blocks) on a terrace with four
  corner gardens: a portico of six quartz columns under a low pediment over a grand stair, a rotunda under a ribbed
  copper dome with a glass lantern, three rings of bookshelves and two galleries round an Enchanting Table ringed by
  fifteen bookshelves, and four wings of reading halls with two floors of bookshelf galleries, carrels, reading tables, a
  lectern each and a map room with a cartography table. It rises in four tidy stages (the Stacks, the Galleries, the
  Rotunda, the Dome and the Portico), each with its goods.
- **The Lighthouse's design, for 2.0** (35.11, nothing to start in game until Wonders is finished): the second Wonder's
  building, a tower banded in white and red on a stepped stone mole at the water's edge (25 by 33, 46 high, about 5,000
  blocks), with a gallery on stone corbels, a lantern room of glass round sea lanterns, a ring of lamps an inverted
  daylight detector lights at night, a copper cupola with a lightning-rod vane, and at its foot the keeper's cottage, a
  boathouse, a quay with mooring posts and a jetty over the sea to a ferry stop with a Travel Post. It rises in four
  tidy stages (the Mole and the Quay, the Tower, the Gallery, the Lantern and the Keeper's House), each with its goods.
- **The Great Cathedral's design, for 2.0** (35.9, nothing to start in game until Wonders is finished): the first
  Wonder's building, a Gothic cathedral on a cross plan (39 across the transepts, 55 long, two west towers under stone
  spires reaching 52, about 14,250 blocks) with a rose window over a deep west door, flying buttresses, a rounded apse,
  open belfries with three bells, pews, an altar of gold and candles, three side chapels and a sacristy. It rises in
  four tidy stages (the Foundations and the West Front, the Nave, the Transepts and the Choir, the Towers and the Rose
  Window), each with its goods from the village's jobs, in its Wonder data file.
- **Trade pacts and alliances, for 1.7** (33.18, off until 1.7 is finished): the Trade page's new Pacts tab offers
  another player's village within caravan range a trade pact or an alliance, by letter; they accept or decline on
  their Pacts tab within 3 days. Caravan routes to another owner's village now need a pact (routes from before 1.7
  become pacts), and pact caravans are paid for what they bring unless the sender turns on gifts. Allies send relief
  riders to each other's raids within 1,024 blocks. A pact ends at once, an alliance after a day's notice. Settings
  `tradePacts` and `alliances`.
- **The realm's research topics, for 1.7** (33.16, off until 1.7 is finished): the capital's Realm tab now has the
  realm's eight topics in place of the six interim ones: Royal Roads (caravans between the realm's villages 25% sooner
  a level), Common Coin (the board's spread 3 points narrower a level), Royal Post (parcels between the realm's
  mailboxes also at noon), Ferry Charter (half-price Travel Tickets between the realm's posts for members' owners and
  friends), Levies (bigger relief bands, for 33.17), Charters (colonies a quarter cheaper a level, settlers bring 16
  more of each supply), Concord (wellbeing 5% higher a level) and Granaries (a hungry member is sent 32 meals at dawn
  from the fullest store). Each level costs 16 paper, 8 emeralds from the realm treasury and, after the first, 2 books.
  A realm that had chosen an interim topic simply chooses again.
- **Relief in a raid, for 1.7** (33.17, off until 1.7 is finished): when a realm village is raided (a night raid,
  bandits, a siege or a pillager raid), the capital and every member within 1,024 blocks with 2 guards or more send
  riders: one per 3 guards (1 to 3 a village, 6 at most). After a ride of at least 30 seconds they come on horseback to
  the village's edge from their home's side (to the capital's Royal Hall when the capital is raided), named
  "Ashford's guard", their shields in the realm's arms, fight as guards and ride off a minute after the raid (at dawn
  at the latest). Each rider costs the realm treasury an emerald; with it empty only the capital's band comes. The
  sender's hall says "2 guards away helping Ashford" and both chronicles note it. Setting `realmRelief`.
- **Shared research, for 1.7** (33.15, off until 1.7 is finished): a realm's villages share what their scholars know.
  A member uses the capital's level of each research topic where it is higher (its screen says "From the capital"),
  without it counting toward the member's rank, and its idle scholars help the capital at half pace. The capital's
  research screen gets a Realm tab with realm topics that the capital's scholars research, paid from the realm
  treasury, and that help every member village. Packs add their own in `data/<ns>/realm_research/`.
- **The eight reforms as stories** (31.22, part of 1.5): with story arcs on, each edict's reform is told as a short
  story. A villager of the fitting trade asks for it, the three steps follow as its quests, and the ending leaves
  something lasting: the Shift Bell beside the hall ringing the work day in and out, a harvest supper, a midwife, an old
  soldier in iron, a grand festival, the Fair Ledger, two Street Lamp blueprints and a lamplit night, or a drill day at
  the Training Dummies. The edict is reformed for that village as before.
- **Peaceful settings and the threat level** (32.21): a new **Threat Level** setting, 0 to 3 (normally 2). At 0 no
  raids, raiders' camps, sieges or disasters come at all; at 1 they come half as often, raiders walk in (no rams,
  ladders or sand ramps), witches never hex and there is no plague; at 3 they come half again as often with a quarter
  more raiders. On Peaceful nothing comes. For 1.6, the hall's owner can set a village **At peace** on its Defence
  page: nothing comes to it, a camp already standing leaves at the next dawn, and the guards icon says so. Operators
  get `/workplace threat start <culture> | lair <culture> | disaster <id> | end | clear` at the nearest hall, and the
  README has a Threats section.
- **The Wonders page and the Wonder Plan** (35.3, 2.0, still switched off): a Wonders tab on the Village Hall lists every
  Wonder as a card (its icon, name and power), each requirement ticked or crossed with what's missing, the cap
  ("Wonders: 0 of 1 for a Town"), and a Wonder going up with its stage. With every line ticked, a click draws up the
  plans for 32 emeralds (from the realm's treasury, else the hall's, else the player's; owner and friends only) and
  gives a **Wonder Plan**: the whole Wonder as see-through blocks, turned with sneak-right-click and mirrored on the
  style screen, placed only with its centre in the village, all of it within 96 blocks of the hall and over no finished
  building or other Wonder (it says why otherwise). "What next?" names the Wonder closest to ready once the village is
  a Town. Builders draw Wonders stage by stage with the kit's new `wonder_stages()`.
- **Wonders as data** (35.2, 2.0, still switched off): a Wonder is one file, `data/<namespace>/wonders/<id>.json` (its
  name, icon, requirements, stages with their parts and goods, powers and crew), so a server owner's own Wonder works
  like ours; a file with a mistake is logged with its path and the reason and skipped. Requirements use the Legends'
  condition toolbox, with two new types, `coastal` and `mod_loaded`, and a `stand_in` for `legend`; every Wonder also
  needs the Master Architect (stand-in: a Master builder). The caps: `wonderCaps` by rank (Town 1, City 2), one of each
  kind per village, one going up at a time, `wonderServerLimit`, `disabledWonders`, and the `wonders` and
  `wonderLegends` switches. `/workplace wonders` lists every Wonder on the server with its village, owner and stage.
- **The Colony Camp and the Royal Hall, for 1.7** (33.14, off until 1.7 is finished): a new colony now starts from
  the Colony Camp, two covered wagons round its Village Hall under a striped awning, with the Blueprint Table, supply
  crates, a campfire with a cooking pot, three bedrolls, the Travel Post and a flagpole. A realm's capital draws the
  Royal Hall on its Realm tab for a Blank Blueprint: a stone great hall with a dais and throne, banners, a gallery and
  the realm's vault, and its upgrade with a keep tower and a treasury wing. Finished within 32 blocks of the capital's
  hall it doubles what the realm treasury holds (the Royal Hall II: three times). It shows on the village map as a hall.
- **The Professor's Thesis, for 1.5** (31.21, Cobblemon, off until 1.5 is finished): in a Town with the Pokémon
  Professor or an Expert Scholar ("Professor Alder"), 6% a day, the professor writes a thesis on one of six topics
  (Life in the Cold, Fire and Stone, Creatures of the Night, The Forest's Web, Sea and Shore, Living Fossils). Catch
  three Pokémon by type, biome, time of day or height (or have a Fossil Scientist revive one), show the professor one
  of them evolved or a Pokémon at friendship 200, then beat the Trainer Leader of a nearby village, or the rival
  scholar Thessaly who comes to the hall. The thesis is published as a book listing your catches with you as
  co-author: 3 Rare Candies, an Ability Capsule, +150 standing, the honour Co-author and a free research level.
- **The Lost Caravan, for 1.5** (31.20, off until 1.5 is finished): one caravan in 20 on a road over 300 blocks goes
  missing (`lostCaravanChance`). Both halls hear it's overdue and the sending hall hands out a map: find the wreck,
  follow the five marks to the raiders' stockade, and open the cage. The carter (a villager with one of the village
  names) follows whoever freed him, catching up from 32 blocks and waiting further than 64, and joins the village he
  reaches at Journeyman: +150 standing in both villages, +100 friendship with him and the honour Wayfinder. The
  stockade's chest holds the lost goods; bring them to the receiving hall for 8 emeralds and +50 standing there. After 7
  days without a rescue he finds his own way home with nothing, and the goods are lost.
- **Defence upgrades** (32.20): **Palisade II** (a fighting step, and a ditch of pointed dripstone stakes in front),
  **Stone Wall II** (timber hoardings: a roofed walkway with murder holes), **Wall Tower II** (a roofed top with an
  archers' gallery, two stations a side) and **Gatehouse II** (a barbican: a second gate and portcullis, murder holes
  over the gateway). In a siege both portcullises drop and the barbican's gate is broken first; the guards shut both
  gates at night.
- **Houses for the answering jobs** (32.19): the Blueprint Table has a **Fire Station** (a stone ground floor with the
  Firewarden's grindstone, a water basin and buckets on racks, under a timber watch loft), a **Well House** (a roofed
  well down to water with the Well Keeper's cauldron, a trough and benches) and a **Physician's House** (the brewing
  stand, a herb garden and four ward beds behind screens). Each has its job's block and no other. What next? suggests
  each when the village lacks its job and its disaster can come there ("Drought can come here: build a Well House").
- **Plague and the Physician** (32.18, behind the 1.6 expansion): a Town or bigger may suffer a plague (3% a day, twice
  that with more villagers than beds; a coven's hexed or a traveller from far away can bring it too). While it lasts,
  each hall round a villager within 3 blocks of an ill one catches it 1 in 5 (less with Medicine research); the ill
  don't get well by themselves, the Nurse can't cure them, and after two days they stay in bed. It ends when no one has
  been ill for a day. The new **Physician** (brewing stand + glistering melon slice) foretells the outbreak at its first
  case, goes house to house, sends the ill home (then they pass it on only in their own house) and cures them with the
  new **Remedy** (a glass bottle, a glistering melon slice, a honey bottle and a fermented spider eye; they brew it from
  those makings in their chest too): it cures any illness, hexes included, and keeps a villager well for 10 days.
  Right-click a villager with one to give it; drink one to lose poison, wither, hunger and nausea. The hall's list marks
  the plague's ill. Config `physicians`.
- **Drought and the Well Keeper** (32.17, behind the 1.6 expansion): after five days without rain a village may fall
  into a drought (5% a day; desert, savanna and badlands villages 15%, twice that with a desert raiders' lair near),
  lasting 3 to 5 days until rain ends it. Crops, stems and berry bushes grow at a quarter pace, farmland dries, water
  cauldrons lose a level a day and villagers are unhappy ("The wells are low") unless the village has a finished Well
  or Fountain. The new **Well Keeper** (cauldron + a shovel) carries water from the nearest well, river or pond to the
  fields, each bucket wetting 9 farmland whose crops grow at full pace that day; between droughts they keep the
  cauldrons full and dry farmland wet, and they foretell the next drought a day ahead. Water-type partners speed them.
  Config `wellKeepers`.
- **Fire and the Firewarden** (32.16, behind the 1.6 expansion): wooden villages can burn, from lightning in a
  thunderstorm (1 in 3), a chimney spark on a dry day (2%) or a raider's burning arrow (1 in 4, with `mobGriefing`).
  The fire spreads by the game's own rules but only through the builders' blocks, never a player's, and the builders
  put back what burned. The new **Firewarden** (grindstone + water bucket) runs to the fire with two buckets and
  douses it in steam, refilling at water near the grindstone; from Journeyman they ring the bell for a bucket brigade,
  and with Cobblemon pastured Water types douse fires at range. The chronicle says what burned and who put it out.
  Config `firewardens`.
- **The disaster engine** (32.15): datapacks can describe disasters (`data/<ns>/disasters/<id>.json`) that strike a
  village one at a time, with 7 days' rest after one: crops grow slower, farmland dries, cauldrons drain, villagers fall
  ill and pass it on, are unhappy, fires start or a job slows. A village with the right job or a lookout is told a day
  ahead; it ends after its days or with rain, shows on the wellbeing icon, in What next? and the chronicle, and each
  can be switched off (`disasters`). No disaster ships yet.
- **A day's warning** (32.14): a guard posted within 24 blocks of a finished Lookout Tower or Wall Tower sees a raid
  coming the evening before ("Smoke on the north road: ... will strike tomorrow night"); a Scout there also counts
  them. Players in the village and the hall's owner are told, the chronicle, guards icon and Defence page show it; on
  the day the gates shut and the portcullis drops from noon, and at dusk the guards take battle stations and the
  villagers go in. Without a lookout, What next? says to build one.
- **The realm treasury, for 1.7** (33.13, off until 1.7 is finished): every member of a realm pays a levy of its
  daily takings into the realm treasury before the rest goes into its own (10% by default; the capital's owner picks
  0, 5, 10 or 20% on the Realm tab). The treasury holds up to 256 emeralds for a Barony, 512 for a County, 1,024 for a
  Duchy and 2,048 for a Kingdom. Anyone can give to it (click its icon with emeralds: one; shift-click: the stack; or
  CobbleDollars with the pack); only the capital's owner, their friends and operators take from it (right-click). Its
  tooltip lists the last 20 movements with the day and the reason. Realm research, relief riders and edicts will pay
  from it.
- **The Lost Caravan's trail** (31.19, for the Lost Caravan story): the settlers' covered wagon wrecked on its side, five marks of a trail (a dropped lantern, a smashed barrel, a torn white banner, cart ruts, an arrow-studded crate) and the raiders' stockade with its tent, fire, loot chest and iron-barred cage, each lying on the land as it is and never over anything a player built. A new quest objective, follow the tracks: reach the marks in order, and the action bar says where they go next ("The tracks go on to the north-east").
- **The Sickness: the Grey Cough** (31.18): a new story arc in villages of Village rank with 12 villagers or more (illness on). Three villagers fall ill with the Grey Cough, which no nurse's remedy cures (it only eases the slowness for a day) and which passes on at night to those who work or sleep nearby, never past half the village. Talk to the village's Nurse, or to Doctor Wren, a travelling physician who comes when there's none: they name the three far herbs nearest the village and give you a map to each. Bring 3 of each and they brew the Three-Herb Tonic over half a day, then take it round. Everyone well brings a thanksgiving festival, +150 standing, the honour Healer of the village and +100 friendship with each who was ill; after 15 days without the cure it runs its course and the village is low for 5 days.
- **The far herbs** (31.17): eight rare plants drawn like vanilla's flowers (Frostbloom, Sunthistle, Mirewort, Peakmoss, Jungle Orchid, Sea Lavender, the glowing Glowcap and Petal Balm), each growing on its own ground in its own biomes (biome and ground tags datapacks can add to). They never generate or spread, so no world changes: story arcs plant them with the new `herb_patch` effect, 5 to 9 on fit ground. Also the Three-Herb Tonic, a bottle of green tonic a healer will make (31.18).
- **The bounty board and bandit captains** (31.13): from Village rank a village posts bounties on named bandit
  captains (two open at most, one every 3 days, 7 days each), shown as posters on the new Bounty Board and in the
  journal's Bounties tab. Taking one gives a Wanted Poster and a map to his hideout, a bandit camp with his banner built
  when you come near; each captain has a quirk (Ironhide, Fleetfoot, Crossbow Ace, Beast Rider, Night Stalker, Warlord,
  Hexer). His Captain's Insignia pays 12 emeralds times the rank factor, once; a lair's captain is paid to his killer.
  Config `bountyBoard`.
- **Rogue Pokémon bounties** (31.14, with Cobblemon): half the bounties are on a rogue, a wild Pokémon half again as
  big (an Alpha on Cobblemon 1.8) roaming 150 to 250 blocks out, its species from the biome there (Ursaring in a
  forest, Mamoswine in the snow, Kingler on a beach and 21 more) and its level 35, 50 or 65 by the village's rank (with
  Radical Cobblemon Trainers, your level cap plus 5). Named "Rogue Ursaring", it glows for whoever took its bounty
  within 32 blocks; beat it or catch it (and keep it) for 16 emeralds times the rank factor, once. Bounty rewards are
  now paid in CobbleDollars where that mod keeps the money. Config `rogueBounties`.
- **The Bandit King's fort and the King** (31.15): three new builds that rise a stage a dawn 110 to 150 blocks from a
  village, each on the one before: a palisade of sharpened logs round a yard with a fire and tents; two watchtowers
  and a stable; the King's keep with his war banner over the door, a throne, a treasure chest and a postern at the back.
  The King ("Grimwald, the Bandit King") wears a golden crown and iron, swings a diamond axe, has 150 health and a boss
  bar, and at half health blows a goat horn that calls four bandits out of the keep, once; his men keep to the fort.
  His chest always holds the King's Crown (Protection III, Unbreaking III) with emeralds, gold, diamonds and an
  enchanted sword or crossbow. The story that brings him comes with 31.16.
- **The Bandit King's story** (31.16): a Town or bigger that has broken up two bandit camps may face him (8% a day,
  with `banditCamps` on). Three camps go up at once 80 to 104 blocks out, their chiefs "Captain of the Bandit King",
  each flying his war banner (black, a red skull): take all three. The next dawn his fort rises a stage a day; win
  allies before the keep stands: a quest done at a neighbouring village brings 3 of its guards, 24 emeralds at the hall
  6 mercenaries, and 16 bread for Pell the deserter (by the first camp's ashes) opens the keep's postern and wins him
  for the village. At the storm, raise a Rally Banner near the fort and your guards and allies follow; his raiders come
  every second night until he falls. Kill him for his treasure, a festival, +150 reputation to everyone who fought,
  the honour Kingslayer and 10 days without bandit camps; if he still stands after 10 days he takes a third of the
  treasury, leaves his fort a ruin, and moods drop 10 for 3 days. The village now counts the bandit camps it broke up.
- **What friendship and titles are worth** (31.12): friends sell for less (5% at 4 hearts up to 30%, plus 5, 10 or
  15% for a Friend, Hero or Lord, 40% at most); at a festival every villager with 6 hearts or more brings you their
  family's gift, and Heroes and Lords are Heroes of the Village II; the first baby of a couple who love you (8 hearts)
  is named after you; at 10 hearts a villager sends you a weekly parcel with a letter; Legends visit only villages
  where someone is a Friend (Rare) or a Hero (Legendary); a Lord may set edicts and a Hero may propose one to the owner.
- **Classes switch on by themselves** (34.22, 1.8): every village with a hall from an older save finds its places, 16
  villagers every 30 seconds: each household gets the highest class it already qualifies for (luxuries aside, at most
  Burgher), children their parents', grown-ups an age spread over the last 100 days and a family name. The chronicle
  and the players there hear it once ("The people of Thornholm found their places: 14 peasants, 6 artisans and 2
  burghers"), then a week of settling in (`classGraceDays`, 7): nobody falls a class, luxuries aren't needed yet,
  nobody retires, and the Classes page says "Settling in: 5 days left" with what each class will need. Nobody loses a
  job, no rank or takings drop, and build sites, quarries, daycare Pokémon, fossils and parcels are untouched.
  Operators: `/workplace classes status` and `/workplace classes reseed`.
- **Family trees** (34.21, 1.8): every villager in a village with a hall has a family name, founded or taken from a
  parent, shown on the hall as "Dara (Ashby)" (the name over their head never changes). Each hall keeps the Book of
  Families: up to 400 people who lived there, the longest dead forgotten first. A villager's page has a Family button:
  grandparents, parents, partner, brothers and sisters, children and grandchildren, the dead marked. Births in the
  chronicle name the grandparents, and a family's third generation is noted; the Printer prints any family's tree as a
  book for 2 emeralds. Babies now remember their parents by id too. Config `familyNames`.
- **Retirement and apprentices** (34.20, 1.8): an elder with a job retires once a jobless villager can take over (their
  own child first), at most one a village every 3 days and never while they hold a site, a quarry, Pokémon, fossils or
  parcels. The successor spends 2 days "learning from Bram", then takes the workstation, the job one level below and its
  fields, routes and orders. Retired elders keep their trades, never work again, and mentor Novices and Apprentices of
  their old trade, who learn 50% faster ("taught by Bram"). Chronicle entries; config `elderRetirement`.
- **Marching on the lair, for 1.6** (32.13). On the hall's Defence page, **Muster a war party** puts three in four of
  the village's guards (up to 12) on the Rally Banner in your inventory and raises it; they gather by the hall. **Hire
  the Siege-breakers**: two crossbows, two knights and a medic for 30 emeralds (3 less a level of Commerce), who stay
  until the lair falls or two dawns pass. At the lair the band turns out six at a time; at a City the captain fights at
  the front. Guards earn double experience for kills at a lair, and a lair broken by a war party gives 10 days' peace,
  a chronicle line naming you and every guard who killed there, and +10 mood for two days ("the warband is broken").
- **Scouts, for 1.6** (32.12). Sneak-right-click a guard with a **spyglass** and they're a **Scout** (you get back what
  they held). From the hall's Defence page, **Scout the lair** sends one out by day: they walk to within 24 blocks of
  the lair, raise the spyglass and come home; the band ignores a scout more than 8 blocks off. Back home the page shows
  the lair's **exact place**, strength and captain, and a **Scouting Report** waits in the chest by their grindstone:
  a map with a red X on the lair. A scout not back within a day is lost. Scouts also trail raiders who flee at dawn.
- **Witch covens, for 1.6** (32.11). A swamp village of Village rank or more may get a **Coven Hut** beyond its
  edge: a hut on mangrove stilts over a pond, brewing stands, hanging lanterns, a herb garden, a black cat and the
  coven's cauldron over a fire. Every night it stands the coven **hexes** one to three villagers (a swirl of witch
  particles): they fall ill with something **no nurse's remedy cures** and that doesn't pass by itself. In a raid the
  witches **stand 12 to 20 blocks outside the walls and throw potions over them** (slowness, weakness, poison at
  villagers, harming at guards) and heal each other. Their **Coven Mother** blinks away after three hits in a row.
  **Kill her or break her cauldron** and every hex lifts at once.
- **The player guide** (22.10): a website for players, the link to send friends
  (https://jcondedata.github.io/minecraft-alive-workplace/player-guide/): getting started, every job and how to set it
  up, the Builder and blueprints, the Village Hall, village life, quests, festivals and markets, trade and caravans,
  threats and defence, Pokémon and villagers, and the settings a server owner can change, each with the nightly
  showcase's pictures. It is rebuilt every night with the showcase.
- **Class outfits, drawn** (34.17, the owner's picks in 34.17a): what Artisans, Burghers and Nobles wear over their
  job's outfit, and an elder's face: Artisans a blue neckerchief and a belt pouch, Burghers the buttoned edge of a dark
  frock coat with a pocket square, Nobles an ermine-trimmed mantle with a chain of office, elders grey brows and a
  short beard. The job's hat, apron and level badge always stay in view, and Peasants keep their outfits. They appear
  in game with 34.18's render layer.
- **Dressed by class, in game** (34.18): Artisans, Burghers and Nobles now wear their class over their job's outfit,
  children too, and elders have grey brows and a beard; it changes as soon as a household rises or falls or a villager
  becomes an elder. Zombie villagers never wear it. `classOutfits` in the settings turns it off.
- The benchmark times our own code each tick (25.3, testers, benchmark servers only): with
  `-Daliveworkplace.benchmark=true` the log gets our code's p50, p99 and worst tick each minute and a line for each
  tick with over 50 ms of ours, and `perf.py --ticks server.log` sums them up. Normal servers are unchanged.

### Fixed
- Big villages: a builder fetching from a store with many builds queued at it no longer recounts every one of them
  block by block in one tick; the recounts are spread over a few ticks (25.4). Testers: the hour-long soak fails only
  when the heap grows, not when it falls.
- Hired swords fight at once (B117): mercenaries hired at the hall, the Siege-breakers, the Bandit King's allies and
  the old soldier who settles after a reform could stand idle beside a monster until the world was reloaded. They now
  take up a guard's ways the moment they arrive.
- Big building villages run smoother: builders in one world place or break at most 12 blocks a tick between them (a
  builder whose turn falls past that waits one tick). The benchmark's timer now tells a long tick of ours from a
  garbage collection pause or a busy machine (the tick's GC pauses and time off the CPU in each long tick's line).
- Fewer hitches the first time a job runs on a big pack: the mod's text joins are now built in as plain code instead of
  being put together by Java the first time each one runs, which held up a tick by up to a tenth of a second (B103).
- Villages with many lumberjacks and builders run smoother: a lumberjack in a wood full of log pillars or log cabins
  now looks a few trunks over at a time instead of all of them in one go (it may take a moment longer to pick its
  tree there), a tree farm's saplings are found without going over every block of the farm, and a builder waiting for
  materials no longer recounts every other build of the village block by block each time.
- Updating no longer leaves a finished expansion switched off (34.23): a config file written by an earlier version held
  `false` for the switches of expansions that weren't out yet, and the new version obeyed it, so a server updating to
  1.8 would have kept classes, ages and family names off. Such switches now take their new default; a switch turned off
  after the update stays off. Found by the update rehearsal (`tools/packtest/rehearsal.sh`, a 0.142.0 world opened with
  1.8 on the Cobbleverse pack).
- A crafter's first look at the recipes no longer stalls a big modpack's server for a third of a second (B101): the
  crafters' recipes are sorted and their ingredients worked out once when the server starts and after each `/reload`,
  and the crafters' planning has a limit per plan and per tick (a crafter past it looks again a few seconds later).
### Added
- **A colony is born, for 1.7** (33.10): at the end of their journey the settlers make camp on open ground near the
  spot, facing home, with the colony's Village Hall (named from the charter, owned by the mother village's owner) and
  a Travel Post already known to the owner and their friends. They step out as they were; the builder takes the
  camp's Blueprint Table and starts the Starter Cottage and Storehouse in the mother village's style, one on either
  side of the camp. Mother and colony become sister villages, with a free caravan route both ways outside the route
  limit; both chronicles note the founding and the owner gets a letter. With no room near the spot (all water, a
  cliff) the settlers come home, the supplies go back and the charter's cost goes back into the treasury.
- **Fewer stalls on a new world's first day on big packs** (B103): the mod's classes are now loaded in the background
  while the server starts, instead of in the middle of a tick the first time a hall round, a worker's check of the
  village's chests or a cradle at night needs them; a worker's chest search no longer waits for a chunk.
- **Elders walk slower again, and nimble villagers faster** (B99, B95): when the game took a job's walk over at its
  own pace (for a moment after a path failed), an elder walked the rest of the way at full speed and a nimble villager
  lost the faster walk. A worker now always walks at their own pace.
- **Dark oak chimneys in the renders rise whole again** (B106): in the review renders of the grand homes and the Manor
  in the dark oak style, the chimneys' stone turned to deepslate with no direction, which the renderer drew as
  nothing, so only the smoking tops showed. Styled renders now give such blocks the game's default (upright); in game
  the chimneys were always whole, and a new test checks every chimney stays solid in all five styles.
### Added
- **Piglin incursions, for 1.6** (32.10). A Town with a lit Nether portal may get a **Blackstone Outpost** beyond its
  edge: a lit portal on a blackstone dais, a basalt pillar flying the piglin banner, a hoglin pen and a hoard of gold.
  By night piglins, crossbowmen, brutes and hoglin rams **step out of its portal**; they never zombify while raiding
  and go for villagers and guards. A piglin passing a village chest **takes its gold** (a stack at most), drops it if
  killed and carries it home at dawn; the siege report counts what was lost. When their gold-armoured **Warlord** falls
  the portal goes out and the band left behind turns zombified.
- **Desert raiders, for 1.6** (32.9). By a desert or badlands Town, husks open a half-buried **Tomb Camp** with an
  obelisk under their **Pharaoh**: while he lives, a husk that falls near him gets up again once, in a burst of sand.
  They pile **sand ramps** against the walls where pillagers set ladders (gone after the siege), stay until noon, and
  the Pharaoh's sarcophagus holds gold, emeralds, a golden apple and now and then the dune armour trim.
- **Six more personal requests, for 1.5** (31.10). A friend with 3 hearts may now also ask you to carry **a letter to
  family** in another village (a sealed letter; bring their relative's reply back, and if you lose it they write
  another), to **bring back their love** from the grave (an Undertaker revives them, and the two marry again), for **a
  cat by the door** (a cat or a tamed wolf by their bed at dawn), to see their child **off to school**, for **a chapel
  wedding** (the couple wait up to 5 days for a Chapel, then marry there with a feast for the whole village) and for
  **the lost heirloom** (you get a map; the heirloom turns up in a chest at a ruined portal, shipwreck, trail ruins or
  mineshaft when you get there, and its story goes in the chronicle when it's home).
### Added
- **Pillager warbands, for 1.6** (32.7): by a Town or City in the plains, forests, taiga, snowy lands or savanna, a
  pillager warband may raise a War Camp (a ring of sharpened logs, three tents, the Warlord's tent and its chest, a
  ravager pen, a siege yard, the ominous banner on a pole), three times as likely as bandits. Its named Warlord wears
  iron and the ominous banner; its raids are sieges with ladders and ravager rams (one at a Town, two and an evoker at
  a City), and at a City the Warlord leads them himself: bring him down at the walls and his camp breaks.
- **Drowned pirates, for 1.6** (32.8): by a Town or City on the coast, Captain Silas Brine may anchor his Pirate Ship
  (a two-masted dark oak sloop with black sails, a crow's nest, his cabin and chest at the stern, the Jolly Roger) 40 to
  70 blocks out, bow to the village. On raid nights his drowned crew and skeleton gunners with Flame bows wade ashore
  at the beach nearest the ship, so a sea wall holds them; at dawn they go back into the sea. Bring the captain down
  and the ship burns and sinks in ten seconds (only its own blocks), leaving its chest of gold, emeralds, a trident and
  now and then a heart of the sea on a log raft.

- **Grander homes (1.8).** Two new houses in the Blueprint Table: the **Artisan's House** (a narrow timber house with a
  stone workroom below and two beds above; its II adds a workshop wing and a third bed) and the **Burgher's
  Townhouse** (three storeys of brick and timber with a shop window and two bedrooms; its II a walled courtyard garden
  and a third bedroom; its III a corner tower with a study). Every home now has a grade: its tier, or more if a
  `homes/` data file says so (the Townhouse is grade II, its upgrades III). Builders sell the Artisan's House from
  Apprentice and the Townhouse once the village has Artisans. "What next?" names the households that want a grander
  home ("Dara and Tomas are Burghers and want a grade II home"), and the Steward upgrades their house or builds an
  Artisan's House or a Townhouse for them.
- **The Noble's Manor (1.8).** A grade III home in the Blueprint Table: a stone house of two storeys behind a
  gravelled forecourt, round a chequered hall with a grand stair up to a gallery, a dining room and a parlour, two
  bedrooms, under a slate roof with dormers. Its II adds a walled formal garden (clipped hedges, a fountain, gravel
  walks) that counts 3 beauty for the people who live there; its III a gatehouse on the street and a ballroom wing,
  where the Noble's Ball is now held. Builders sell it once the village has a Burgher household, and the Steward
  builds one for a household that wants a grade III home.

### Fixed

- When a siege begins, archers get their battle stations before they fight: none closes in on a raider or shoots
  from the ground first while his station is still being handed out, in any part of the tick the siege begins
  (32.6, B105).
- The settings screen no longer cuts off the elder setting: it now reads "Days Until Elder" (B102).
- Villagers getting up from the Arena's stands after a Festival Cup step off the bench into the aisle (or onto it),
  instead of standing inside the stair (B94).

## 0.142.0 — 2026-10-10

### Added
- **The Print Shop and the Jeweller's Workshop, for 1.8** (34.14): two more blueprints in the Blueprint Table, each
  with a II. The **Print Shop** is a brick shop in a timber frame: the cartography table is the press, standing under a
  skylight, with a counter for buyers and shelves of paper and ink (no lectern: a lectern would make a librarian).
  **Print Shop II** adds a wing behind with a bindery and a reading room lined with bookshelves. The **Jeweller's
  Workshop** is a small stone shop with an iron door (a button opens it): the stonecutter at a bench under a lantern,
  an amethyst cluster in a glass case, and a plank door at the side for the Jeweller, since villagers can't open iron
  doors. **Jeweller's Workshop II** adds a strong room: a vault of ten chests behind iron bars. A Printer or Jeweller
  who reaches Journeyman sells their building's blueprint for 12 emeralds, the Steward builds one for a Printer or
  Jeweller with no workstation, and (once 1.8 is on) villages of all five kinds grow a small Print Shop and Jeweller's
  Workshop with a Printer and a Jeweller already at work.
- **The Winery and the Tailor's Shop, for 1.8** (34.13): two new blueprints in the Blueprint Table, each with a II.
  The **Winery** is a stone press house over a half-sunk cellar: earth banked up the cellar's sides, its door under
  the landing of the outside stair, the cauldron vat by the press room's door, racks of casks and a cellar of chests
  (no barrels: a barrel would make a fisherman). **Winery II** adds a tasting porch and two terraces of sweet berry rows
  down to a pergola of glow berries. The **Tailor's Shop** is a timber shop with a bay window of dress forms, the
  loom, bolts of coloured wool and a fitting room behind; **Tailor's Shop II** adds a cutting-room storey and a drying
  loft for dyed wool. A Vintner or Tailor who reaches Journeyman sells their building's blueprint for 12 emeralds, the
  Steward builds one for a Vintner or Tailor with no workstation, and (once 1.8 is on) villages of all five kinds grow
  a small Winery and Tailor's Shop with a Vintner and a Tailor already at work.
- **Reputation and titles** (31.11, part of 1.5, off until it's released): every player has a standing in every
  village. It is earned by a daily quest (+10), a bounty (+40), a villager's own request (+25 to every helper), a
  story chapter (+50 to everyone who helped) and a story's ending (what its file says), breaking up a bandit camp or
  a lair (+40 to whoever brought its chief down), fighting in a raid (+20 at your third raider), coming to a festival
  (+5) and gifts (+2, at most +10 a day per village); it is lost by hitting a villager (-10) and by killing one
  (-150), an iron golem (-100) or a guard (-200). **Friend** at 50, **Hero** at 300, **Lord** at 1000 in a Town or
  bigger. A new title is told to the whole server in gold with a fanfare ("Jesse is now a Hero of Thornholm!") and
  written in the chronicle; losing one is told only to you. Your title in the village you stand in (else your best
  anywhere) goes before what you say in chat: `[Hero of Thornholm]`. The hall's name tag tooltip shows your standing
  and the three best regarded, and `/workplace standing` lists yours everywhere. Stories can give **honours**
  (Kingslayer, Healer of the village, Wayfinder, Co-author), listed with your standing. For data packs: the quest
  rewards `reputation` (`points`, `who`) and `honour` (`id`, `who`). Settings `reputation` and `titlesInChat`.
- **Personal requests** (31.9, part of 1.5, off until it's released): a villager with 3 hearts or more may walk up
  and ask you for help. Each morning a village rolls a one-in-four chance that someone asks; they come over, ask over
  their head, and your chat gets the request with **[I'll help]** and **[Not now]** (`/workplace quest accept|decline`).
  Six requests: **Next level before the festival** (6 emeralds), **A home of my own** (a bed in a finished tier II
  house, 8), **A taste of home** (a dish by where their people come from: a pumpkin pie on the plains, 16 sweet
  berries in the taiga..., 4), **The tools of my trade** (a diamond pickaxe for the Miner, a fishing rod with Luck of
  the Sea for the Fisherman..., into the chest by their workstation, 5), and with Cobblemon **Help me train** (beat a
  Trainer on three different days) and **A Pokémon friend** (a Pokémon that helps their job, pastured within 16 blocks
  of their workstation at dawn). An open request shows in the quest journal's new **Personal** tab, on their life-story
  page and on the hall's tooltip ("Dara wants to make Journeyman before the next festival"). Other friends with 3
  hearts can join; everyone who helped gets the reward, a heart and a half of friendship, and the chronicle a line. A
  missed deadline costs 30 friendship, leaves them glum for a day and they ask nobody for 3 days. New quest-file
  conditions for data packs: `hearts_at_least`, `job`, `job_family`, `level_below`, `villager_type`,
  `home_tier_below`, `no_bed`. Setting `personalRequests`.
- **Heart events: the full set, and keepsakes** (31.8, part of 1.5, off until it's released): villagers now have
  something to tell at every second heart. At 4 hearts, **My work**, one story per job family (the first wall a
  builder raised, the day a miner's lamp went out, the fisherman's fish that got away; someone without a trade asks
  whether the free Loom near the hall might be for them). At 6, **What keeps me up at night**, from their life now:
  hunger, no bed of their own, raids, illness in the house, loneliness, or worry about you. At 8, **The people I
  love**: their partner and the wedding day, who they're courting, their children by name, the partner they lost,
  their parents, or the whole village. At 10, **What I dream of**: to be a Master, to see the Nether or the sea, a
  finer house, the village a City, a festival in their honour, or nothing more at all. After the 10-heart story they
  give you a **keepsake**, once: "Dara's Lucky Pick" (an iron pickaxe with Fortune), "Dara's Grandmother's Seeds",
  "Dara's Secret Recipe" (a book with her pumpkin pie, and two pies) and nine more, one per job family.
- **Founding a realm, for 1.7** (33.11, off until 1.7 is finished): a Town's hall gets a **Realm** tab on its Trade
  page. Found a realm and the village is the capital of "the Barony of Thornholm"; one click adds any of your own (or
  a friend's) villages within 2,048 blocks, and colonies join their mother's realm by themselves. Three villages with
  a Town capital make a County, five with a City a Duchy, eight with two Cities a Kingdom: each rank up sends
  fireworks over the capital, a chat line to every member's owner and a new title. The tab lists the members (the
  capital crowned; rank, villagers, wellbeing, distance, owner), takes a banner for the realm's arms and a named Name
  Tag for its name; members can leave and the capital can let one go. A broken capital passes the crown to the
  biggest Town left, or the realm ends. The hall's name icon names the realm and "What next?" suggests one. New
  settings `realms` and `realmMaxMembers` (12).
- **Other players' villages, and choosing the capital, for 1.7** (33.12, off until 1.7 is finished): the capital's
  Realm tab invites another player's village: a letter goes to its owner (their mailbox, their hands, or the Postal
  Desk), and its own Realm tab offers Join or Decline for 3 days. Any member that's a Town can be put forward as
  capital: with one owner it moves at once, otherwise the members' owners vote on the tab for a day (not voting counts
  for the current capital; a tie keeps it). The realm moves with all it has, with fireworks over the new capital and a
  line in every member's chronicle.
- **The settlers set out, for 1.7** (33.9, off until 1.7 is finished): right-click the hall with the Colony Charter,
  its spot chosen, and two settlers volunteer (a builder and one more; never a guard, the only worker of a job, a
  Legend or a child; a married volunteer brings their partner). 64 logs, 64 planks, 64 cobblestone, 32 bread, 16
  torches, 12 glass panes and 3 beds come out of the Storehouses; what is missing goes on the village's wants and the
  Storehouse board, and after 3 days they leave with what there is. The next morning they gather at the hall, the
  bell rings and they walk out toward the spot; out of sight, they are on the road for 3 minutes plus a tick a
  block. The Colonies tab shows the order ("On the road to Newbrook, there in 2 minutes") and can call it off until
  they leave, which puts everyone and everything back.
- **The Colony Charter, for 1.7** (33.8, off until 1.7 is finished): a City's hall gets a **Colonies** tab on its Trade
  page that sells the owner a Colony Charter for 32 emeralds (the treasury pays what it has, you pay the rest).
  Right-click the air with it for its map: the 2,048 blocks round the village, drawn like a vanilla map where the
  server has the land loaded and as parchment elsewhere, a banner for every village with a hall, and the ring a colony
  may go in (256 to 1,024 blocks from the hall, 128 clear of other halls). Click a spot, or right-click the ground
  where you stand: a red cross marks it and the tooltip says "612 blocks north-east of Thornholm". Rename the charter
  in an anvil to name the colony. One colony on the road at a time, 7 days between colonies, 3 a village. "What next?"
  suggests a colony once a village is a City. New settings `colonies`, `colonyCooldownDays`, `coloniesPerVillage`, and
  `colonyRank` in the file. The settlers themselves come with 33.9.
- **Caravans you can see, for 1.7** (33.7, off until 1.7 is finished): when a caravan leaves or arrives in a village
  with a player within 96 blocks, a carter in the porter's outfit ("Thornholm's caravan") leads two llamas with chests
  and carpets in the village's colour (its Village Banner's, or one picked from where its hall stands) from the
  Storehouse to the edge of the village, or in from the edge to the Storehouse, where the llamas stand while the goods
  are unloaded, and back out. They are only a sight: the goods travel the saved way, and nobody can trade with, ride,
  feed, hurt or rob them. One party per village at a time, gone after two minutes, removed after a restart.
  `visibleCaravans` turns them off.
- **Sieges, part 3: battle stations and the morning after, for 1.6** (32.6, off until 1.6 is finished): when a siege
  begins, archers go up to the tops of Wall Towers and Lookout Towers and the walkways of gates and walls, knights
  hold the inside of the gate the rams go for and medics stand behind them. From a station an archer shoots 24 blocks
  (16 on the ground) and hits a quarter harder at foes 3 or more blocks below. The next dawn the chronicle has a
  siege report with the hero of the night, players who killed a raider in a siege that was fought off are Heroes of
  the Village for a day, villagers are 10 happier for a day ("we held against the siege"), and for 2 days builders
  mend walls and gates before houses. New research **Ramparts** (after Fortification I): gates have twice the hit
  points and archers on stations shoot 28 blocks.
- **Elders, for 1.8** (34.19, off until 1.8 is finished): after 120 grown days a villager is an elder. The chronicle
  notes the day ("Bram is an elder now"), they walk 15% slower on their work's errands, their mood has "a quiet old
  age" (+5) while they are fed and have a bed, and off work they have four lines of their own ("In my day this was all
  fields."). The elder look comes later, with the class outfits (34.18). `villagerAges` turns elders off.
- **Lairs for every raider culture, for 1.6** (32.3, off until 1.6 is finished): the bandit camp's chief now has a
  name ("Chief Harl Ashgrave", one of twenty) over his head and in every message and chronicle line, and the camp has a
  strength (6, one more a day, 10 at most): a raid takes its raiders from it, those alive at dawn rejoin, the dead are
  gone, so a camp that lost a costly night is weak. Any datapack culture with a `lair` makes camp the same way.
- **Sieges: rams and gates, for 1.6** (32.4, off until 1.6 is finished): a raid by raiders who ram gates on a village
  with a finished wall or gate is a siege. The gates shut at once, whatever the hour, and the Gatehouse's portcullis
  drops; both open again at the first dawn after. A ravager ram (or raiders with axes) pounds the gate nearest the
  raiders: every blow is heard, the cracks grow, and the block breaks without dropping anything (a fence gate has 60
  hit points, a door 80, iron bars 150: about 10 seconds through a Palisade Gate, about a minute through a Gatehouse).
  Only the gates of finished builds break, never a gate you placed; builders put them back. The Defence page shows the
  gates, the portcullis and what is broken. New settings `sieges` and `siegeDamage`; the mobGriefing game rule off also
  keeps gates standing.
- **Sieges: ladders over the walls, for 1.6** (32.5, off until 1.6 is finished): raiders who carry ladders (the
  climbers of a culture with the tactic `ladders`) and find a wall in their way set a ladder up its outer face, a rung
  every half second, up to 10 high; they climb over and drop inside, and the other raiders follow up the same ladder
  while the gate is shut. The chat says "Ladders at the walls of Oakbrook! A guard on the wall can throw them down." A
  guard on the walkway within 2 blocks of a ladder's top does: the whole ladder goes and whoever is on it falls.
  Ladders go on any wall, a player's too, but only into air; every one is taken away when the raid is over, also after
  a restart, and nothing else is changed. A siege now gathers beyond the outermost finished wall on its side, so no
  raider appears inside the walls. With mobGriefing off no ladder is set; `sieges` off turns it all off.
- **The Defence page, for 1.6** (32.3): the guards icon on the Village Hall opens it: the camp, its captain, its
  strength, roughly where it lies, the days it has stood, and the last three attacks and how they ended.
- **The price board** (33.4, part of 1.7, off until it's released): the minecart on the Village Hall opens a **Trade**
  page with tabs. **Routes** is the trade routes page as it was; **Prices** lists every trade good with what the
  village pays for a bundle and what it sells one for, an arrow for how the price moved since yesterday, a gold star
  on what the village is known for and a red mark on what it's short of. A good's tooltip names the village on your
  trade routes where it's dearer and the one where it's cheaper. The hall's name icon says "Known for: Timber, Wool.
  Short of: Bread", villagers talk about what sells well, what there's too much of and what's dear, and the Village
  Ledger reaches the page from afar.
- **Trading at the board** (33.5, part of 1.7, off until it's released): on the Prices tab, click a good to **sell**
  the village a bundle you carry, or to **buy** one if you don't (right-click always buys; shift-click does as many
  bundles as will go). The village pays from its treasury and keeps what it earns there (in CobbleDollars with the
  pack; with emeralds a trade settles in whole emeralds, and the odd cents stay with the village). The goods go into
  and come out of its Storehouses' chests, which keep 16 of every item back, as they do for caravans. It sells at 10%
  over what it pays, and each bundle moves that day's price 2% (sold to it: down; bought from it: up). It never pays
  more than its treasury holds or takes what its chests have no room for, and the good's tooltip says which stopped
  it. Without a Storehouse the tab says the market needs one. The treasury is on the Trade page too (a gold nugget:
  what it holds, its cap, click to collect). The board buys potions only if they heal, and no worn tools.
- **Caravans that trade** (33.6, part of 1.7, off until it's released): a caravan still carries what the other
  village is waiting for, free. Now it also takes up to 2 stacks of goods its own village is known for, to villages
  that are short of them or pay at least 10% more. When it arrives, that village's treasury pays yours at its board's
  price, both boards move 2% a bundle (down there, up at home), and both chronicles say so ("Sold 32 Timber to
  Ashford for 2.57 emeralds"). What it can't pay for comes home in a caravan of its own, back into your Storehouse;
  what its Storehouse has no room for waits on the road. On the Routes tab every village shows what a caravan sells
  there and what it earns ("Timber ×2 for 2.57 emeralds").
- **Strangers can trade in a protected village** (33.5): right-clicking the hall of a village that keeps you out now
  opens its price board, and nothing else of the hall.

### Changed
- **Collecting a village's treasury is now for its owner, their friends and operators, in every village** (33.5, with
  1.7's `villageEconomy`; until then nothing changes). Before, anyone who could open the hall of an open village
  could collect; now that strangers' trades draw on the treasury, they can't. A hall nobody owns stays open to all.
- **Raids run on a new threat engine** (32.2), the base of 1.6's threats. Monster and bandit raids look and count as
  before, but who raids is now data (`data/aliveworkplace/raider_cultures/monsters.json` and `bandits.json`; a datapack
  can add cultures of its own, with their own mobs, gear and lands). Two things work differently: a raid under way now
  **survives a server restart** (it used to be forgotten, and never ended properly), and a raid is now **decided at
  dusk for the next night**, so the first night after updating is quiet. Breaking up a bandit camp before the bandits'
  hour calls their attack off. Guards now fight anything a raid brings, hoglins included. New setting `raiderCultures`
  (in the file only): every culture and whether it may come; `villageRaids` and `banditCamps` work as before.
- Bandit camps saved by older versions load as they were (their file keeps its name); nothing changes in play until
  1.6 is finished.

### Fixed
- **Elders and the nimble keep their own pace** (B95): now and then a worker on the way to a job walked at the plain
  villager speed instead (an elder at full speed, a nimble villager no faster than the rest). They hold their pace now.
- **The Pathfinder now waits for an Expert explorer** (B96): the Legend's file asked for a Journeyman Cartographer
  (level 3) where its design says Expert (level 4). A Pathfinder who has already come stays.
- **A workstation's tooltip no longer names a job that is switched off** (B97): with `vintners` off the cauldron still
  offered the Vintner. The same went for the Gem Grower (`gemGrowers`) at the stonecutter and, with Cobblemon, the
  Berry Breeder, Camp Cook, Habitat Keeper and Daycare Keeper behind their switches. Now only the jobs you can
  actually give are listed.
- The hearts shown when you look at a villager no longer make the server load terrain for a player standing where
  none is loaded (fake players from other mods, B93); the mod's own test run is back from about two hours to minutes.
- Builders no longer stand inside a closed door (B92): a builder in a doorway when a neighbour shuts the door on him,
  or caught in any block that changed around him, walks out of it within a second or two; and a door's top half or a
  bed's head is no longer put down on a villager, pet or player standing there (they are asked or nudged aside first,
  as for any other block).
- **The hearts look-up no longer loads chunks** (B93): a player standing where nothing is loaded made the server read
  that chunk from disk every half second, which slowed every tick (and the test suite from 13 to about 80 minutes).
  Out there, no hearts are shown.

## 0.141.0 — 2026-10-09

### Added
- **The Evergreen Charm, for 1.8** (34.19a, off until 1.8 is finished): a gold-and-green leaf pendant crafted from a
  totem of undying, a golden apple, 2 emeralds and a heart of the sea. Sneak-right-click an elder with it and they
  become Ageless: they never pass away, the chronicle says "Bram will never leave us" and their card on the hall's list
  wears a gold leaf badge. Only an elder who is a Master of their trade, Gifted, a Legend, or has been happy (mood 80 or
  more) for 20 days running takes it; anyone else refuses with the reason and you keep the charm. With it comes the
  ground it stands on: villagers who grow up count their days, are elders after 120 (`villagerElderDays`), and pass away
  in their sleep after 40 elder days, leaving a grave an Undertaker can bring them back from; the hall's list warns ten
  days ahead. Config `villagerAges`, `elderPassing`, `agelessElders`.
- **A trade screen of the Pokémon Trader's own** (28.23): the day's offers as cards (the Pokémon, its level, ball and
  shiny mark, and what it costs) beside your party, drawn like the Village Hall's screen, with a pressed-in button and
  "click again" line before a trade and a plain "no offers today" state.
- **The eight Festival Cup themes** (28.22): Blossom, Little, Sun (doubles), Workers', Harvest, Lantern (dusk to
  dawn), Frost and Grand, in that order, each with its own rules, fair wares, feast dish, firework colours and disc.
  Villager trainers' teams and players' eligibility follow the theme. For the Workers' Cup, every pastured Pokémon now
  counts the days it helped a villager at work (once a day); it needs 3.
- **The Printer, for 1.8** (34.11, off until 1.8 is finished): sneak-right-click a villager by a cartography table with
  an ink sac. They print books (Novice: 3 paper and a leather make 2; kept in stock while the village has a Scholar,
  whose research now takes books from the store and the Printers' chests first), the Village Gazette (Novice: 3 paper
  and an ink sac make 2) and the Illuminated Book (Journeyman: a book, 2 gold nuggets, a lapis lazuli, a glow ink
  sac). The Gazette reads like a written book, printed from the hall that day: the newest chronicle entries, the open
  quests and what they pay, the next festival and market day, the week's births, weddings and households that rose;
  the Illuminated Book is the whole chronicle. A new hall quest, Spread the news, pays 6 emeralds for carrying this
  week's Gazette to the hall of a village on a caravan route. Trades (today's Gazette for 1 emerald, the Illuminated
  Book for 8), Psychic and Normal partners, an outfit (ink-stained apron, green eyeshade, sleeve garters). Config
  `printers`.
- **The Tailor, for 1.8** (34.10, off until 1.8 is finished): sneak-right-click a villager by a loom with string (shears
  bring the Shepherd back). They sew Work Clothes (Novice: 3 wool of any colours, 2 leather, a string), Fine Clothes
  (Apprentice: 4 dyed wool but white, a string, 2 gold nuggets) and Noble Robes (Journeyman: 5 wool of one rich colour,
  a rabbit hide, a gold ingot) for the village store: the clothes the Artisans, Burghers and Nobles need. Trades, Bug
  and Normal partners, an outfit, config `tailors`. A loom's tooltip now names its jobs, and shears make a jobless
  villager by a loom its Shepherd.
- **The Jeweller, for 1.8** (34.12, off until 1.8 is finished): sneak-right-click a villager by a stonecutter with a
  gold nugget (a clay ball brings the Mason back, an amethyst shard still picks the Gem Grower). They make the Amethyst
  Ring (Novice: 2 amethyst shards, 2 copper ingots), the Emerald Brooch (Apprentice: an emerald, 3 gold nuggets) and
  the Gold Circlet (Journeyman: 2 gold ingots, an emerald, an amethyst shard) for the village store: the jewellery the
  Burghers and Nobles look for. A Burgher's job; trades, Rock, Steel and Fairy partners, an outfit with a loupe, config
  `jewellers`.
- **The trade goods engine for 1.7** (33.2, nothing to see until the price board): trade goods are data files
  (`data/<namespace>/trade_goods/<good>.json`). Once a day, in its hall's round, each village works out up to 3 goods
  it's known for and 3 it's short of (from its workers' jobs, its biome, its Storehouses and what its workers wait for)
  and a price for every good that moves a third of the way toward supply and demand each dawn, between half and twice
  the base. A beaten raid (night, bandit or vanilla) raises demand for arms for a few days. Kept with the village's
  caravan entry, so old saves load unchanged. Config `villageEconomy` (off until 1.7).
- **The 28 trade goods for 1.7** (33.3, nothing to see until the price board): Grain, Bread, Roots, Fish, Meat, Fine
  Meals, Fruit, Honey and Bone Meal; Timber, Stone, Glass, Bricks and Clay, Coal, Iron and Gold; Wool, Leather, Dyes and
  Flowers, Paper, Tools, Arms and Armour, Arrows, Remedies and Nether Goods; and, only with Cobblemon, Apricorns, Berries
  and Poké Balls. Each has its makers and their biomes, the biomes and jobs that want it, and a bundle priced like
  vanilla's villager trades where there is one (20 wheat, 6 bread, 18 wool, 24 paper, 4 iron ingots for an emerald).
  Raids raise demand for arms, arrows and remedies for 3 days, and remedies while villagers are ill.
- **The Vintner, for 1.8** (34.9, off until 1.8 is finished): sneak-right-click a villager by a cauldron with sweet
  berries, glow berries or an apple. They press Cider (Novice), Berry Wine (Apprentice) and Vintage Wine from Berry Wine
  three days old (Journeyman) for the village store, with purple splashes at the vat; the three drinks are drinkable
  (the bottle comes back), are the households' wine luxuries and are served at the Noble's Ball. Trades, Grass, Bug and
  Fairy partners, an outfit, config `vintners`.
- **Heart events and life stories** (31.7; switched on with the rest of 1.5): at 2, 4, 6, 8 and 10 hearts a villager
  has something to tell you. When you're within 8 blocks and they're off work they walk up, face you and tell it in
  three to five lines over their head, one every 3 seconds, each also in your chat in grey; walk away halfway and they
  start again next time. Told once per player, it adds 20 friendship, writes a line in the chronicle ("Dara told Jesse
  about growing up in Thornholm") and becomes part of their **life story**: shift-click someone on the hall's list for
  their page (your hearts, name day, family, partner and the story so far). The first set, at 2 hearts, is **Where I
  come from**: born here (names both parents), came as a traveller (the inn and the day they were hired), here before
  the hall, and back from the grave. Events are data (`data/<ns>/heart_events/<id>.json`: hearts, conditions on the
  villager's facts, lines, the chronicle line); a broken file is skipped with a warning. Config `heartEvents` (true).
  Villagers revived, widowed or hired from now on remember it (`revived`, `late_partner`, `hired_day`).
- **Gifts** (31.6; switched on with the rest of 1.5): two new items, **Gift Wrap** (paper, string and any dye make 4)
  and the **Gift** (Gift Wrap and any one item on the crafting grid; the tooltip says "From Jesse", not what's inside).
  Right-click a named villager with a Gift: they unwrap it, say how they like it over their head and in your chat, and
  your friendship changes (loved +80, liked +45, neutral +20, disliked −20, hated −40); the item goes into their
  chests. One gift a day and two a week per villager; on their **name day** (every 28 days, shown in the hall's list)
  a gift counts three times. A Bottle o' Enchanting is liked and gives a worker 15 XP. Villagers without a name shake
  their head; with `friendship` off a Gift is handed back unopened. Tastes are data
  (`data/<namespace>/villager_tastes/<id>.json`: for everyone, a job, a family of jobs or a trait; the most specific
  file that names an item wins), with a first set for everyone, twelve job families and the eight traits.
- **Friendship with villagers** (31.5; switched on with the rest of 1.5): every named villager in a village with a hall keeps ten hearts for
  each player. Trading with them (+5), finishing a hall quest they posted (+40), handing in what they wait for (+10),
  coming to their wedding (+30) or a festival with them (+10) count once a day each; killing a monster that just hurt
  them (+15) counts every time; hitting them costs 50 (once a minute). Look at a named villager within 6 blocks and
  the action bar shows "Dara ♥♥♥♡♡♡♡♡♡♡"; hearts puff over them when they go up; the hall's list shows your hearts
  and their two best friends. Quest files can reward `friendship`. Setting `friendship` (on); hearts never fade.
- **The story arc engine for 1.5** (31.4; the stories themselves come in later updates): a village can live through
  a story told in chapters over several days. Each chapter is announced in chat and written in the chronicle, puts up
  its quests (in the journal's new **Story** tab, chapters done ticked), can build something out in the wild once a
  player comes near, bring in named foes with a boss bar, and has the villagers talking about it; a chapter whose time
  runs out ends the story badly. Stories are data (`data/<namespace>/arcs/<id>.json`), survive a restart at any
  moment, and come with settings `storyArcs`, `arcCooldownDays` (8), `arcsAtOnce` (3) and `disabledArcs`. Operators
  get `/workplace story start <arc>`, `next` and `stop`. A new quest goal, **talk** to a villager the story names.
- **Champions** (28.21): the Festival Cup's champion's village holds the Cup until the next champion is crowned there.
  It's named on its hall's name tag, in other halls' trade-route lists and on its Village Map ("Holders of the
  Thornholm Cup"). The Cup banner, a trophy pattern of our own (`aliveworkplace:cup`) in the theme's colours, flies on
  its Arena III's champion's pole or on top of its Village Hall, and comes down when the title passes. Its villagers
  are proud of it (+5 mood), the winning Leader gets two more win bonuses of trainer XP, and the defending champion is
  seeded first at the next Cup.
- **Players in the Cup** (28.20): with Cobblemon, a signed-up player's bout is called in chat with a bell and a
  clickable [I'm ready]; click it inside the Arena within two minutes or lose by walkover. Then it's a real battle
  (against a villager, the Leader or their delegate as the trainer; against a player, Cobblemon's own PvP battle) in
  the theme's format and level and with its rules, with healed copies so nobody gains experience or keeps damage. Only
  the party's first eligible Pokémon go, and the player is told who stayed home and why. Fleeing loses. [Watch] on the
  Cup page and the Arena's notice board shows a running bout from the stands. Each win pays 100,000 (500,000 more for
  the final, half again at a City host; emeralds without CobbleDollars).
- **Cup day** (28.19): on a Festival Cup's day the far villages' entrants walk in from their village's side as delegates
  ("Dara of Eastholm", in a Trainer Leader's outfit; real Leaders left at home are away at the Cup and take no
  challenges), the market's traders hold a fair on the Arena's fair lane selling the theme's wares, from noon the
  villagers sit in the stands, eat the feast (the theme's dish first) and cheer their village's wins while the bard plays
  the theme's disc; after the final, fireworks in the theme's colours, Hero of the Village and the festival's mood, and
  the Cup goes into every circuit village's chronicle (gold ingot). Bouts left at the end time are settled as
  exhibitions; a host that isn't loaded puts its Cup off (8 days at most); no Arena or `festivalCup` off: a plain festival.
- **Higher jobs need higher classes, for 1.8** (34.8, off until 1.8 is finished): in a village with a hall, the
  Tinkerer, Chef, Netherworker, Nurse, Teacher, Shopkeeper, Innkeeper and the Cobblemon trades need an Artisan, and the
  Scholar, Undertaker and Trainer Leader a Burgher, when the job is taken (its item, the hall's free workstations, now
  greyed with the class, a grown child, the Steward). Hired travellers arrive as Peasants, Artisans or Burghers by
  level. Nobody is fired: a worker below their job's class keeps it and the hall marks them.
- **What each class gives, for 1.8** (34.7, off until 1.8 is finished): taxes by class (Peasant ×1, Artisan ×1.5,
  Burgher ×2.5, Noble ×4, +10% a want; the hall's name tag shows the split), Artisan crafters 10% faster, Burgher
  scholars 15% faster, a caravan route and a market trader more with 3 Burgher households, +3% wellbeing a Noble
  household (9% at most), and a **Noble's Ball** in place of every other festival (wine, the store's best food, gold
  fireworks, +15 mood for 3 days, Hero of the Village for the night).
- **Classes at the Village Hall for 1.8** (34.6, off until 1.8 is finished): the hall gets a Classes tab (how many
  households of each class) and page (each class's needs and wants with how many households have them, what the class
  gives, and who is closest to rising with what they lack); the people list shows each villager's class and household
  ("Burgher · married to Tomas") with the needs of their class and the next one ticked; the food icon lists the
  luxuries in store; and "What next?" gives up to three class tips, most households first.
- **A household's rise and fall is felt for 1.8** (part of 34.6, off until 1.8 is finished): a household that
  rises gets golden sparkles and a chime at their door, a chat line to players within 32 blocks, a line in the hall's
  Chronicle and "rose in the world" (+10 mood for 2 days); one that falls gets a Chronicle line and "came down in the
  world" (-10 for 2 days). Moods add "has what their class needs" (+5) or -5 for each need lacking (at most -15), and
  villagers talk of their rise or fall (three lines each).
- **The luxury workshop engine for 1.8** (34.5, nothing to see until the four new makers arrive): luxury recipes are
  data files (`data/<namespace>/luxury_recipes/<id>.json`: the job, the level, the makings by item or tag, what comes
  out and how long it takes). A maker fills the store's stock orders first, then makes whatever the village store has
  fewer than 8 of, from their own chests, the store and the village's stashes (crafting-table makings up to two steps
  down), asks on the requests board for what's missing, and gets faster with Craftsmanship. Goods that age carry the
  day they were made, and the tooltip says "Pressed on day 42 · vintage in 2 days".
- **Luxuries from the village store for 1.8** (34.4, off until 1.8 is finished): luxuries are data files
  (`data/<ns>/luxuries/`: an item or a tag, and how many days apart a household wants one). At dawn each household
  takes one of every luxury its class and the class above ask for that's due, from the village store (the kitchens'
  chests, then the Storehouses'), and remembers the day; a couple takes one between them, and an empty store leaves
  the need unmet that day. Porters carry luxuries from the makers' chests to the storehouse. The luxuries themselves
  come with their jobs later in 1.8, so nothing changes in game yet.
- **Two more looks for the Pokémon Center** (28.7a, with Cobblemon): a timber **Mountain Lodge** under a steep red
  gable with a porch (II adds a two-storey wing and a pen with a Pasture Block) and a sandstone **Sunny Plaza** with a
  striped awning and a red-capped drum (II adds a wing and a walled courtyard). Both tiers of each are in the Blueprint
  Table, Journeyman Nurses sell any of the three looks, and a village's steward builds its own look of the Center.
- **Festival Cup bouts between villagers** (28.18, with Cobblemon): on Cup day the bracket is fought at the host's Arena,
  one bout at a time, a round when the last one is done. Each trainer stands in their box; their Pokémon (a themed team
  from the same pool as at home) come out one at a time beside the ring, face each other and trade moves with
  Cobblemon's animations and impacts, damage from the theme's level, the moves and the 18-type chart
  (`data/aliveworkplace/type_chart.json`); 90 seconds at most. The winner goes on, both trainers earn trainer XP (kept
  for a far village's Leader until it next loads) and everyone at the Arena reads the result. Bouts with players come
  with 28.20.
- **The quest journal, tracking and quest maps** (31.3, part of 1.5, off until 1.5 is finished): the hall's Quests page
  becomes a journal with Village, Personal, Story and Bounties tabs; each quest shows who asked, its steps with their
  progress, the reward and the days left. Track a quest (a click, or shift-click) to see it as a boss bar at the top of
  your screen, for you only; `/workplace quests` lists your quests in chat with [Track]. Quest files gain the objective
  `reach` (go near a structure, a biome or a point) and the reward `map` (a map with the place marked), the place found
  once when the quest goes up.

### Changed

### Fixed
- **The Steward's planning costs the server less** (B85): his planning at the hall is split over the second (his
  wishes and their plot searches, then his desk half a second later), the morning's ranking counts the village a
  piece a second instead of all in one tick, and one planning second looks up the Blueprint Tables once (every 5
  seconds at most) instead of once per wish. Nothing a player sees changes. City benchmark: p95 0.49 → 0.38 ms a tick,
  his planning's server time about halved, and planning is no longer the slowest call.
- **No more log error when a traveller's camp is set down** (B68): the map's item frame on the camp's barrel is saved
  with the block it hangs on, and structures placed by the mod or by vanilla now hang their frames and paintings on the
  block where they land, so "Block-attached entity at invalid position" is no longer logged. Players saw nothing wrong.
- **A build's crops stay in the village store** (B84): villagers no longer eat, cook or sow the carrots, potatoes and
  seeds a build site near the store still needs (meals, feasts, banquets, a new family's meals, farmers and chefs taking
  from the storehouse). A Steward's Farmstead finishes instead of waiting for materials for days; food a build doesn't
  need is eaten as before, and a site frees its share when it finishes or is cancelled.
- **Builders no longer lose an anvil** (B91): a builder working alongside another could set an anvil (or sand,
  gravel, concrete powder) down before the floor under it was laid; it fell, and the build then waited for an anvil
  that was gone. They now leave such a block until there is something under it.
- **A Steward's wall and old-house renewals get built** (B86): an approved wall line past the builders' reach from
  their tables never opened a piece, and the roads took every free builder ahead of it; wall pieces now go to any
  free builder of the village, the roads and the wall take turns, and an old house at the village's edge is renewed
  too. When he approves everything himself, a renewal takes the free slot ahead of the day's homes.

### Dev

- **The wiki** (22.9): `docs/wiki/` explains how each feature works in plain words (the first twelve pages: Builder, Villager jobs, Village Hall, Steward, Roads and walls, Edicts, Guilds, Legends, Pokémon partners, Classes, Elders, Config switches), and the build now fails when a page names a setting, item, text, test or scene that no longer exists, or a finished player-visible feature has no page.

## 0.140.0 — 2026-10-05

### Changed
- **Village quests run on a new quest engine** (31.2): the hall's daily quests are now data files
  (`data/<namespace>/quests/<group>/<id>.json`) that data packs can add to, switch off or change (`/reload` reads them
  again), with the same odds, numbers and rewards as before. Quests already up on a hall move over with their progress
  the first time the hall is loaded. New setting **Village Quests** (on); off, no new quests go up and open ones can
  still be finished.
- **Expansions still being built are off until the release that finishes them** (B76): 0.139.0 switched on parts of
  1.1 to 1.4 that aren't done yet. The Steward (1.1); partner shows, the nurse at the Healing Machine and the Berry
  Breeder, Camp Cook, Habitat Keeper, Daycare Keeper and Gem Grower jobs (1.2); Legends, the Gifted and strange moods
  (1.3); and edicts, the Work Horn, Village Banners, Cradles, Harvest Idols and tonics (1.4) now stay off, even in a
  config file 0.139.0 wrote with them on, and their switches leave the settings screen until then. Nothing saved is
  lost: a Steward, a Legend or an edict comes back as it was when its expansion is released.

### Fixed
- The settings screen's steward renewal switch now reads "Steward Renewals", so its label fits its button (B87).
- **Foretold raids now come from the side the Seer named** (B82): raiders could gather near the edge of the foretold
  side and spread out across it, so a player told "south-east" might see them come from the east. They now gather
  well inside that side, spread out less in a small village, and look further out on the same side when the ground
  there is water.
- **Builders no longer end up stuck inside the ground** (B60): a builder that couldn't walk to its next block hopped
  to the spot it had picked earlier, even if a crewmate or a neighbouring site had filled that spot with dirt since
  (the soak found one inside a landscaping fill). It now checks the spot is still free and looks for another one.
- **Villagers climb the ladder to a bed upstairs** (B79): a villager whose bed is in an attic reached by a ladder (the
  Stone House's, which a renewed two-bed home becomes) couldn't find a way up, and after a minute gave the bed up. At
  bedtime they now walk to the ladder, climb it, step off towards the bed and sleep there.
- **Every guild shows in the Book of Edicts** (B78): the Book's guild row had room for six, so a City's seventh to
  twelfth guilds never appeared. With more than six, the row now shows five at a time and its last slot, More guilds,
  turns to the next ones (and back to the first after the last).
- **A build saved by 0.138.0 or earlier keeps its progress on upgrade** (B64): opened with a newer jar, a site half
  through a raised foundation (or in its walls) dropped to a few percent the first time it loaded. It now works out
  the progress it showed from the saved stage and step.
- **A farmer's carrots and potatoes reach the chests** (B77): a village farmer kept up to 32 of each in his bag as
  seed stock, so a Farmstead's field could be harvested without anything landing in its chest. He now takes them from the
  chests to plant with, like seeds, and puts the whole harvest away.
- **The Pokémon Professor** (29.21, with Cobblemon), a Legendary Legend in a white lab coat with pens in the pocket:
  a guest at the inn once the village's Pasture Blocks hold 25 Pokémon of 10 types. Likes books. Right-click them with
  an empty hand and hover over a Pokémon of your party: each stat's IV in words (No good to Best), its EVs, the stats
  its nature raises and lowers, and whether it has its hidden ability (a click puts it in chat). While they live in
  the village, every species kept in its pastures is logged in the **village Pokédex** (a book on the hall's Legends
  page counts them), and their **Pokédex** research opens as it grows: Field Notes (15 species: partners help 5% more
  a level), Kinship Studies (25: one more partner per worker), Breeding Records (35: daycare eggs 20% sooner a level),
  Berry Science (45: orchard keepers pick one more berry from each berry plant), Evolution Studies (60: the Professor
  sells one evolution stone a day for 8 emeralds) and Regional Survey (80: the hints give exact IVs and EVs). Without
  Cobblemon the Professor never comes.
- **The Pokémon Ranger** (29.22, with Cobblemon), a Rare Legend in a red field vest with a capture styler on the wrist:
  a guest at the Village Hall, 1 morning in 3, while a wild Alpha Pokémon (or, as Cobblemon 1.7 gives no wild Pokémon
  its Alpha mark, a wild one of level 50 or more) is within 96 blocks of the hall. Likes clothes. Each morning they walk
  to an Alpha near the hall and calm it (sparkles, a chime): it never again hurts a villager or player in the village,
  saved for good. Once a day they befriend a wild Pokémon within 64 blocks (never a legendary, mythical, Ultra Beast or
  paradox one) and lead it to a village Pasture Block with room, where it joins as the hall owner's Pokémon (through
  their PC, as Cobblemon does; it stays in the PC while the owner is away), with a line in the chronicle. None when the
  pastures are full or the hall has no owner. Without Cobblemon the Ranger never comes.
- **The Founder** (29.23), a Mythic Legend in a burgundy mantle with a gold chain of office, who keeps their own
  trade: at the village's first rise to City (only the first), its most experienced Master is seized by the Founder's
  mood and asks for a block of gold, a block of emeralds and a diamond. Their Masterwork is **The Charter of
  <village>**, a written book of the village's story from its chronicle (its founding, each rank, its Legends, its first
  wedding, the raids it beat), signed by the Founder. A failed mood passes to the next most experienced Master after
  the week of sulking. Announced to the whole server, within the Mythic cap. The village's builders raise the
  Founder's statue by the hall from the chests (a stepped plinth, a copper plaque, the Founder with a hand raised):
  5 beauty and everyone 5 happier while it stands. Every 7 days the hall's owner gets a **Founder's Wagon**: a
  Settler's Wagon whose camp brings a Village Hall named "New <village>" and blueprints in the village's styles.
- **The Beastmaster** (29.20), a Rare Legend in brown furs under a wolf-pelt hood: found as a prisoner in a pillager
  outpost once the village keeps a ranch (10 animals within 16 blocks of a Rancher's, Butcher's or Shepherd's
  workstation), or born to a Rancher. Likes clothes. With bones in their chest they tame a war dog for every guard
  without one (a village wolf; when only a pair is left, they breed the pair), fit it with wolf armour from armadillo
  scutes, and send it to the guard: it follows them on patrol and in a raid and goes for what they fight. One per
  guard; a lost dog is replaced after 2 days. Once a day, with golden carrots in the chest, they breed the ranch's two
  best horses: the foal gets the best speed, jump and health of its parents and a little more (never past vanilla's
  best), is saddled from the chest when grown, and guards on cavalry duty ride their horses first.
- **The Bard Laureate** (29.19), a Rare Legend in a green doublet with a lute on the back and a laurel wreath: a guest
  at a festival 30 villagers come to, or born to a Bard. Likes books. On settling they compose the village's anthem,
  16 notes on one instrument made from the village's name (the same name always gives the same tune), kept in the hall
  and played over it in note-block notes at every festival, rank-up, wedding and Legend arrival, and from a new
  button on the hall's Legends page; the hall's owner gets it as a written book, "The Anthem of Thornholm". Twice a day they sing a work song
  for 2 minutes where work is busiest: workers within 16 blocks work 25% faster while they sing, and everyone who
  hears is 5 happier for the day.
- **The Grand Chef** (29.18), a Rare Legend in a tall white toque with a gold band and a gold ladle at the apron: a
  Master Chef inspired to bake "The <village> Midsummer Cake", a guest at the inn, or born to a Chef, once the store
  holds 8 kinds of meal. Likes wine. Every 5 days after work the village gathers round the hall (or the Market Square)
  for a banquet, and at supper each grown-up eats two meals from the store, of as many kinds as it has: everyone who
  came is 20 happier for 3 days, and for those 3 days the village may have two babies a day (beds and food as usual).
  Chefs in the village cook 25% faster.
- **The Merchant Prince** (29.17), a Legendary Legend in a crimson coat and feathered hat: a castaway by a shipwreck
  once the treasury has taken in 500 emeralds and the village sends caravans on 3 routes. With him the treasury earns
  2% a day and holds twice as much; the hall gets a **Bank** page where each player keeps up to 10 stacks of emeralds
  at 5% a week (and takes them out any time, even after he's gone); every 10 days a **trade fair** brings 6 traders and
  a stall for each village you trade with, red and yellow bunting round the square (taken down the next day),
  fireworks, and every trade 10% cheaper for the day; and every stack a caravan brings to a village that was waiting
  for it earns the treasury an emerald (shown on the trade routes page).
### Added
- **The Festival Cup: calendar, themes and entrants** (28.17): with Cobblemon, a village with a hall, a finished Arena
  and at least Village rank holds its festivals as a Festival Cup (config `festivalCup`, and `cupEveryFestivals` for
  every second or third). Its circuit is the host and the villages it has a trade route with; each sends its Trainer
  Leader (or its best Trainer, even from far away), and players sign up for a village that's theirs, a friend's or
  nobody's, two a village. The hall's new Cup page shows the next Cup, its rules in plain words, the circuit, the
  seeds, Sign up / Withdraw and the roll of champions; players hear when sign-up opens and the evening before.
  Themes are data files; the Grand Cup ships first. No bouts yet: they come next.
- **The Arena** (28.16): with Cobblemon, three new blueprints in the Blueprint Table (and sold by an Expert Trainer
  Leader). Arena: a 15×9 battle ring of packed mud with white lines and a centre circle, a trainer's box on a dais at
  each end with a lamp post, benches for 12, banner poles and a notice board. Arena II adds stands on both long sides
  (30 seats), a gate arch, lanterns round the ring and a fair lane with two striped kiosks. Arena III adds a covered
  grandstand on stone terraces, a trainers' room with a Healing Machine at each end and a champion's pole before the
  gate. It's where the Festival Cup will be fought (from 1.2), when the hall's "What next?" starts suggesting one to a
  Village with a Trainer Leader.
- **Villages grow the Pokémon jobs' houses** (28.15): with Cobblemon, villages sometimes grow a Pokémon Center (a nurse
  at a Healing Machine, a PC by the counter), a Camp Kitchen (a Camp Cook at a Campfire Pot), a Berry Nursery (a Berry
  Breeder by farmland beds), a Daycare (a Daycare Keeper at a Pasture Block on a straw floor) and a Gem Grotto (a Gem
  Grower at a stonecutter, an amethyst niche, tumblestones on magma behind glass), in all five village styles, each
  with its worker already in the job and a chest of the job's things. Config `pokemonVillageHouses` (on).
- **Old villages renewed** (27.21): one old house at a time (at most one every 2 days), the Steward proposes to rebuild
  it in its zone's style: "Renew the old house 14 blocks west as a Stone House (Cherry)". A home becomes the first of
  Starter Cottage, Stone House and Terrace with as many beds; a job's house the building that job's workplace rule
  builds (an armorer's becomes a Smithy). It must fit the old plot plus 3 blocks, its front where the old door was. The
  old house is saved as a blueprint (`renewal/<hall>/<n>`), taken down by the builders into the store, and the new one
  goes up on the plot; its villagers keep their beds and jobs, and the chronicle notes it. Ask me first asks for each
  house; Run the village renews by itself, only in zones with "Renew" on. Lists are data
  (`data/<ns>/steward_renewal/`). Config `stewardRenewal`.
- **Old houses, found and measured** (27.20): in zones with "Renew" on, the Steward looks for houses no builder built
  (round a bed or a workstation) and measures each by its blocks. The desk lists them ("Old houses: 4, 3 can be renewed")
  with where each stands and why one is kept (a chest inside, a player built there, not of village blocks, out of the
  zone), and a click outlines them all in the world. Packs add their village blocks to the tag
  `aliveworkplace:village_house_blocks`. Nothing is rebuilt yet.
- **The class engine for 1.8** (34.2, off until 1.8 is finished): households in a village with a hall can climb from
  Peasant to Artisan, Burgher and Noble. The four classes are data files (`data/<ns>/classes/`) listing what each needs
  (a home of some grade, fed days running, a varied diet, beauty near home, a finished build, the village's rank,
  services and luxuries); a household rises after 2 dawns with the next class's needs and falls after 3 without its
  own, one step a day, never below Peasant; a married couple moves together and children follow the grown-ups of
  their house. `villageClasses`, `classRiseDays` and `classFallDays` in the config. Nothing changes in game yet.
- **Services nearby for 1.8** (34.3, off until 1.8 is finished): six services a home can have nearby, as data files
  (`data/<ns>/services/`): a chapel (a finished Chapel), a school (a Teacher, or a Schoolhouse or School), a clinic (a
  Nurse, or a Healing Center or Clinic), a library (a Scholar or Librarian, or a Library), a market (a Market Square,
  village-wide) and a tavern (an Innkeeper, or an Inn). Each reaches homes within 48 blocks; styled and upgraded builds
  count. The hall works out where its services are once a day, and the classes' `services` needs read that list.
- **The Steward is safe by design** (27.19): his plans, roads and walls never go into Keep Clear, another village or a
  protected village that isn't his owner's, and a ledger of what players built in a village (from 1.1 on) keeps his
  plans and walls out of those spots unless the owner approves one by hand. His sites leave a player's block where it
  is ("a player's block is in the way" on the desk); when his builds wait for materials, the desk and the Storehouse
  board show one shopping list (the owner hears it once a day, caravans bring it), and he proposes nothing new while
  two builds have waited a whole day.
- **Edicts and civic items in the village's life** (30.21): villagers talk of each edict in force and of each reformed
  one ("Long shifts again... my back.", "The shift bell's rung. Home we go."), of a rush, their tonic, their guild and
  the village's colours; "What next?" now points out a free edict slot, a reform step waiting, Festival Season with too
  little in the treasury, a guild without its Guildhall, Large Families without a Cradle and harvest season without a
  Harvest Idol; the README has *Edicts* and *Civic items* sections with every edict, tonic and guild.
- **Guild Charters, the Guildhall and the Builders' Guild** (30.17): craft a Guild Charter (three paper, an emerald, a
  gold ingot, red dye) and sneak-right-click a Master in a village of Village rank or more: they become the Guild Master
  of their trade's guild (one per trade, one per rank above Hamlet; refusals say why), told to the village, in the
  chronicle, the hall's list and their status, and they sell the Guildhall I and II blueprints. A guild is founded once
  a finished Guildhall stands for it; the Builders' Guild then makes Builders, Carpenters, Masons and Dyers 15% faster
  and lets 5 idle builders help at a build (not 3). When a Guild Master dies, the most experienced member takes over.
  The Book of Edicts' last row shows each guild. Guilds are data files (`data/<ns>/guilds/`); `guilds` and
  `guildsPerRank` in the config.
- **The Miners', Smiths' and Woodsmen's Guilds** (30.18): members work 15% faster once founded. Miners' pickaxes and
  the netherworker's gear wear half as fast (Miners, Sifters, Netherworkers); each ingot a Weaponsmith mends with puts
  back a third of the durability, not a quarter (Armorers, Toolsmiths, Weaponsmiths, Tinkerers, Ball Smiths); axes and
  fishing rods wear half as fast (Lumberjacks, Fletchers, Fishermen). New guild perks for packs: `tool_wear` and
  `mend_per_unit`.
### Fixed
- A Pathfinder waiting for a player who fell behind now stands still instead of drifting a few blocks back toward
  their table or strolling off (B75).
### Added
- **Roads between villages** (27.17): two villages with a trade route each build their half of a street to the other,
  from the end of their nearest road and in the style of the zone it starts from, up to 256 blocks or halfway; the
  halves meet halfway, and a half that can't reach that far ends at a milestone (a stone post with a lantern and a sign
  naming the other village and how far it is). Only loaded land is planned. Caravans on a finished road arrive in three
  quarters of the time, and both chronicles note the road. Settings `caravanRoads` and `caravanRoadReach`.
- **Walls along the wall line** (27.18): a village raided in the last 7 days, or with bandits camped nearby, has its
  Steward propose a wall on the hall's "What next?" — along the City Plan's wall line, or a line of his own 4 blocks
  round the zones, drawn on the plan so you see it before you approve. Walls are kits as data
  (`data/aliveworkplace/wall_kits/<name>.json`): **Palisade** up to a Village (Palisade, Palisade Gate and the new
  **Palisade Tower**, a log watch platform with a ladder and a lookout under a dark oak roof) and **Stone** from a Town
  (Stone Wall, Wall Tower, Gatehouse), a Town replacing its palisade a piece at a time. A tower stands at every corner
  and at least every 28 blocks, the segments between fit whole, a gate goes wherever a road crosses (shut at night by
  the guards as ever), and each piece sits at its own ground height with its foundation under it. At most 3 wall sites
  are open at once, and the whole wall counts as one building for the village's rank. New setting `stewardWalls`.
- **Lamps, bridges and steps** (27.16): streets and avenues get the Street Lamp in their road's style every 16 blocks on
  alternate sides and before every crossing (never by a door), lanes a lantern post every 12; they count as Street
  Lamps for beauty and light the homes near them. A road that meets water or a drop deeper than 2 blocks, up to 16
  wide, gets a bridge in its style (rails, a pillar every 4 blocks down to the bed, a stair up at each end); a wider
  gap stops the road at the bank, and the Steward's desk says why. Crossings are paved square.
- **The Harvest, Herders' and Scholars' Guilds** (30.19): members work 15% faster once founded. The village's own farms
  and the Orchard Keepers' rounds reach 24 blocks from the composter or basket, not 16 (Farmers, Orchard Keepers,
  Florists, Beekeepers, Composters, Chefs); shepherds, ranchers and butchers breed up to 12 of a kind (not 8) and a hired
  butcher keeps 14 (not 10) (Shepherds, Butchers, Ranchers); research levels cost a quarter less paper, books and
  emeralds, rounded up (Scholars, Teachers, Librarians, Cartographers). New guild perks for packs: `work_reach`,
  `herd_size` and `research_cost`.
- **The Healers', Merchants', Wardens' and Trainers' Guilds** (30.20): with these every trade but the Bard has a guild.
  Healers (Nurses, Clerics, Undertakers): 15% faster, the village's ill get well in two days instead of three, nurses
  and undertakers look 48 blocks out instead of 32. Merchants (Shopkeepers, Innkeepers, Ferrymen, Postmen, Porters):
  15% faster, travellers cost a quarter less to hire, porters carry 3 more stacks. Wardens (Guards): guards train on
  the dummies up to Master instead of Expert and hit 10% harder. Trainers (only with Cobblemon: Trainers, Trainer
  Leaders, Move Tutors, Pokémon Traders, Fossil Scientists): lessons and revivals cost a fifth less, trainers rank up a
  quarter faster. Guild files may carry `fabric:load_conditions`. New guild perks for packs: `recovery_days`,
  `work_radius`, `hire_price`, `carry`, `train_up_to`, `strength`, `lesson_price` and `trainer_xp`.

### Dev
- **The 1.1 yardstick: a village from a plan** (27.22): `CITY=true tools/packtest/run.sh` lays out a plains village on
  the real pack server (a hall, a Steward in Run the village, 3 builders, a stocked storehouse, 12 villagers, an old
  vanilla house; Homes, Workshops, Farms, Market, Gardens and Keep Clear, two streets and a wall line) and lets the
  Steward run it for 6 in-game days, then prints one `City result:` line: builds started and finished, any outside its
  zone or in Keep Clear, stalls, every item whose count is off, and what the Steward's work costs a tick (p50, p95, p99,
  worst; `PERF=true` adds a profile). The new `city_timelapse` showcase scene films the same village growing. The
  README has a "Villages that build themselves" section, and the Guide Book an "A village from a plan" page that shows
  once 1.1 is on.

## 0.139.0 — 2026-10-05

### Added
- **The Seer** (29.16), a Rare Legend in a deep purple hooded robe sewn with silver stars: they come to the village's
  finished Chapel at midnight under a full moon, 1 time in 2, or are born to a Cleric; they like jewels, keep to the
  Chapel by day and drift with end-rod motes at night. Each dawn they foretell, in chat and first on the hall's
  "What next?", whether raiders come that night and from which side, the next festival and market day, and the next
  day's Legend guest; with a Seer the night's raid and the next day's guest are rolled at dawn, so they are never
  wrong. A wedding at the Chapel with the Seer there is blessed: the couple is 10 happier for 7 days and their first
  baby comes within 2 days when a bed is free. `Legends.foretold` gives M32 its two days' warning.
- **The Golem Smith** (29.15), a Legendary Legend: a Master Tinkerer may be inspired in a happy village with 4 iron
  golems (their Masterwork is a heavy core, "The Heart of <name>"; they like wine). From the chests by their smithing
  table they build a golem every 2 days, up to one per 5 villagers: a **Hauler Golem** (4 iron blocks, a carved pumpkin,
  a chest) carries what the workers make to the Storehouse, 9 stacks a trip; a **Farmhand Golem** (an iron hoe instead
  of the chest) harvests and replants the village's fields into the field's chest; a **Wall Sentry** (a shield) holds
  the first point of a Patrol Map you give it, with twice a golem's health, throwing attackers back. Sneak-right-click
  the Smith to choose which comes next. Each golem wears its role (a crate pack, a straw hat, a helm), has its name
  over its head and a line on the hall's guards button; the Smith mends golems twice as fast as a Tinkerer and wears a
  leather apron, goggles and iron-banded gloves.
- **Roads** (27.15): the roads drawn on the City Plan get built. The Steward has each approved road's way found over
  the ground (its width kept clear, round water, buildings and anything a player built, steps of one block at most) and
  hands it, 24 blocks at a time, to the village's idle builders when no building waits (two at once at most). Each road
  is paved in its style: As drawn (dirt path, coarse dirt and gravel edges), Stonework (stone bricks with cracked ones,
  cobblestone edges), Sandstone, Dark Oak (deepslate), Cherry (polished diorite) and, with Cobblemon, Apricorn (bricks
  with mud brick edges), with stairs up and down each step. Every new building's door joins the nearest road with a
  lane (villages without roads keep the dirt path to the bell). Roads aren't buildings: the rank, the map and homes
  leave them out. Road styles are data (`road_styles/`). Config `stewardRoads`.
- **Farmstead, Fisher's Hut, Weaver's Cottage and Bandstand** (27.14), new blueprints in the Blueprint Table: a
  farmhouse with one bed beside a field of farmland round a water channel, a scarecrow and a composter (II: a barn and a
  second field); a shore hut with a jetty out over the water on log posts and a barrel (II: a smokehouse with a smoker
  and a boat shed); a cottage with a loom and a fenced sheep pen (II: a dye garden); and an open eight-sided bandstand
  with a jukebox, which adds 3 to a village's beauty. The Steward now builds them for a Farmer, a Fisherman, a Shepherd
  and a Bard left without a workstation. A Fisher's Hut goes only on a shore, with water within 4 blocks of its front
  and no more than 3 deep under its jetty; a fisherman fishes from the end of a jetty before the bank. Builders put no
  foundation under a top slab or upside-down stairs (they hang, they don't stand).
- **Smithy, Mason's Yard, Fletcher's Lodge and Map Room** (27.13), new blueprints in the Blueprint Table: a stone forge
  open to the street with a blast furnace, smithing table and grindstone (II: a coal and ore store with a second blast
  furnace); a fenced yard of cut stone with a lean-to over a stonecutter (II: a second stonecutter and a hoist); a log
  cabin with a log pile, a straw target and a fletching table (II: a drying-rack wing with a second one); and a narrow
  tower house with a cartography table and a lookout at the top. The Steward now builds them for an Armorer or Miner,
  a Mason, a Fletcher or Lumberjack, and a Cartographer left without a workstation (or a job the village wants with no
  free block).
- **The Old Sage** (29.14), a Rare Legend: once a village has finished 5 research levels, a hermit's hut of mossy stone
  and spruce (a lectern, bookshelves, a cauldron, an herb garden) appears 150-250 blocks out, and the village's players
  hear a rumour with its distance and direction. The Sage asks three riddles from a pool of eight, each answered by
  handing over an item; a wrong one gets a shake of the head and, after two misses, a hint. Three right answers and the
  Sage comes to the village the next morning as a guest (also born to a Scholar). Likes books. They work the
  **Ancient Lore** at a lectern: Old Tongues, Star Charts, Herb Lore, Deep Memory, Runes of Warding, Old Harvests and
  one last pick for good (the Undying Flame, the Golden Age or the Iron Pact). Grey robe, long beard, gnarled staff.
- **The Pathfinder** (29.13), a Rare Cartographer Legend: found at a ruined portal once one of the village's explorers is
  an Expert, or born to a Cartographer; likes clothes. Their own expeditions range twice as far and bring back more
  maps, now and then a trial key or an echo shard. Sneak-right-click them with 8 food in their chests and pick a
  Stronghold, an Ancient City or Trial Chambers: they hand you a map to the nearest within 3,000 blocks and lead the
  way, waiting when you fall behind, catching up when you're far off and fighting whatever attacks either of you. At
  the place they plant a banner; right-click them for Home and after 5 seconds standing still you're both back at the
  Village Hall. Once a day; a logout or a death calls it off. Every trip goes in the chronicle. A hooded travel cloak,
  a pack and a lantern.
- **Seasons and the Harvest Idol** (30.14): the hall's festival icon now names the season and its day ("Autumn:
  harvest season, day 3 of 16"). The Harvest Idol, a straw figure crowned with wheat on a wooden post (a hay bale,
  three wheat, a stick and a gold ingot), makes the crops within 32 blocks grow 25% faster in harvest season (autumn),
  with golden sparkles rising from it; a second idol adds nothing. Vanilla crops and, with Cobblemon, its berries,
  apricorns, mints and Hearty Grains (tag `aliveworkplace:idol_crops`). `harvestIdols` in the config.
- **The Steward's civic rules** (27.12): he now wishes for a Clinic (a Healing Center from a Village) when two or more
  are ill and nobody nurses them, a Graveyard after a death in a village of 8, a Schoolhouse for 3 children, a Library
  with 6 villagers and no scholar, a Chapel while a couple courts, a Lookout Tower (a Barracks in a Town) while guards are
  short, a Street Lamp by the darkest beds, a Well, Park Bench, Fountain and Gazebo while the village has little beauty,
  and a Market Square for a Town. Nine new rule conditions for packs: `guards_short`, `raided_within`,
  `bandit_camp_near`, `ill`, `dark_beds`, `beauty_below`, `children_at_least`, `courting_couples`, `died_within`, and
  `no_worker`.
- **Four more tonics** (30.16), each with its own icon: the Cleric brews **Smith's Draught** (a glass bottle, blaze
  powder and two iron nuggets; for Armorers, Toolsmiths, Weaponsmiths, Tinkerers and Ball Smiths) and **Scholar's
  Infusion** (a glass bottle, an amethyst shard and glow berries; for Scholars, Teachers, Librarians, Cartographers and
  Fossil Scientists); the Chef cooks **Harvest Cordial** (a glass bottle, an apple, wheat and sugar; for Farmers,
  Orchard Keepers, Florists, Beekeepers, Composters, Shepherds, Butchers, Ranchers and Chefs) and **Woodsman's Broth**
  (a bowl, cooked salmon, a carrot and a brown mushroom; for Lumberjacks, Fletchers, Fishermen, Porters and Postmen).
  Each makes its villagers 25% faster for a day. Tooltips now say "2 Iron Nuggets", not "2 Iron Nugget".
- **Tonics: Miner's Brew and Builder's Tea** (30.15): right-click a villager with a tonic that suits their job and they
  drink it, working 25% faster for a day (20 minutes; their status line shows "25% faster (Miner's Brew, 19 min left)").
  Another tonic starts the day again; they never stack, and the work pace cap still holds. One that doesn't suit them is
  refused and you keep it ("Dara has no use for Miner's Brew."). Players can't craft them: the Cleric brews Miner's
  Brew (a glass bottle, glowstone dust, coal and sugar; for Miners, Sifters and Netherworkers) after the guards'
  potions, the Chef cooks Builder's Tea (a glass bottle, two sweet berries and sugar; for Builders, Carpenters, Masons
  and Dyers) after the menu, from the chests by their station and the store, keeping 4 of each. Tonics are data
  (`data/<namespace>/tonics/<id>.json`), so a server can make any item one; the tooltip says what each does, for which
  jobs and who makes it from what. `tonics` in the config.
- **Strange moods and Masterworks** (29.10): once a day a Master in a happy village whose trade an inspired Legend
  names may be taken by a strange mood (1 in 8). She claims her workstation under a purple line and asks for three
  rare materials in the chest beside it within 3 days (the chronicle, the hall, the Storehouse board and the village's
  players hear of it). Brought, she makes a named Masterwork ("The Ember Ladle", lore naming her, the village, the day
  and the materials, with a glint) for the player who brought the most, and becomes the Legend; a Masterwork in an item
  frame in the village is 3 beauty. Not brought, she sulks a week (less happy, half pace) and the village has no mood
  for 10 days. Config `strangeMoods`.
- **Research trees for Legends, as data** (29.11): a Legend can bring a research tree of their own
  (`data/<ns>/research_trees/<tree>.json`, so packs can add more). While they live in the village it gets its own tab
  on the research screen (or sneak-right-click a Legend with no trade). Choose a topic there and the Legend pays for it
  from the chests by a lectern near their home and researches it there; idle scholars help at half speed, and nothing
  moves while the Legend is on strike. Topics can wait for a village count or be one-of-a-kind picks. New village
  effects for trees and edicts: wellbeing, illness (chance and length), raid chance, XP, loot luck and named switches.
  Old halls keep their research as it was.
- **The Master Architect** (29.12), the first Legendary Legend: a guest at the inn once the village is a Town with
  finished buildings in 3 styles. Builders within 32 blocks of the Architect work twice as fast, and every 3 days the Architect hands the
  least busy builder the next upgrade of a finished building (homes first), or redraws it in the new **Grand** style
  (stone-brick plinths, polished andesite and deepslate trim, dark-oak frames, deepslate-tile roofs), rebuilding only
  the blocks that change. Sneak-right-click the Architect to pause it; a strike stops it. A long blue coat, a brass compass and a
  rolled drawing.
- **Legends found in the world** (29.9): standing in a ruined portal, a pillager outpost or a shipwreck, a player whose
  village has earned a Legend found there may come on their camp: a traveller beside the portal (talk to them), a
  prisoner in an iron cage at the tower's foot (break the bars) or a castaway on the nearest beach (hand them a cooked
  meal). Freed, they thank you, walk off and come to your Village Hall the next morning as a guest. Each structure is
  used once; `legendSites` in the config turns it off.
- **The Village Hall's own screen** (30.4a): the hall, all its pages and the Book of Edicts now open on a drawn, vanilla-style window instead of a chest grid: the village's figures on a sunken plaque, the page's actions on an etched toolbar, the page on its own panel, every icon on a raised button that lights up under the mouse. Clicks, pages and permissions are unchanged.
- **The Village Banner** (30.13): any banner and a gold ingot make a Village Banner of that design (its tooltip lists
  it). Right-click the Village Hall with it to make the design the village's colours (it isn't used up; the chronicle
  notes it); place it to hang the design anywhere. The colours then show: a builder who finishes a building in the
  village hangs a wall banner in them over its front door when a banner of their base colour is in the chests (they
  never ask for one); guards gearing up and hired mercenaries carry plain shields painted in them (a shield a player
  painted is left alone); the hall's trade routes page shows each village by its banner and the Book of Edicts the
  village's own, with the colours on its last row; and a festival with a banner in the colours within 16 blocks of the
  square lifts moods by 20 instead of 15, for 3 days instead of 2. `villageBanners` in the config.
- **The Cradle** (30.12): a wooden cradle on rockers with a wool blanket, crafted from planks, sticks and white wool.
  Put one within 4 blocks of a bed in a village with a hall and it becomes a nursery village: children grow up in half
  the time and one more baby a day may be born (three a day with Large Families). At night a child of the house sleeps
  in the cradle. More cradles add nothing. The hall's beds icon and the Book of Edicts' last row say whether the village
  has one; `cradles` in the config.
- **The Work Horn** (30.11): a brass-banded horn crafted from a goat horn, a gold ingot and an emerald. Hold it up in
  a village like a goat horn and, when it sounds, every grown villager works 50% faster for 5 minutes (the speed cap
  still holds), with sparks over them. Once a village a day; afterwards the villagers are worn out (10 less happy) until
  dawn. Only the hall's owner and friends can call a rush in an owned village. The Book of Edicts' last row shows the
  horn ready or used. `workHorns` in the config.
- **A workplace for every worker** (27.11): twelve of the village houses are now in the Blueprint Table for builders to
  build: Builder's Workshop, Carpenter's Workshop, Kitchen, Post Office, Guard House, Clinic and Ferry House, and with
  Cobblemon the Trainer's House, Leader's Hall, Ball Workshop, Trade Hall and School. Each has its job block and a bed;
  the worker who needs it moves in. The Steward now builds a workplace for every worker left without a workstation:
  Kitchen for chefs and butchers, Guard House (Barracks from a Town) for guards and weaponsmiths, Clinic (Healing
  Center from a Village) for nurses and clerics, Library for librarians and scholars, and so on for 27 jobs, six more
  with Cobblemon. He also builds one for a job the village wants when no block for it is free: a Guard House when
  guards are short and no grindstone or Guard Post is free. A Ferry House goes on the shore, with water within 4 blocks of its door. With no builder he asks you
  to place a Blueprint Table and give a villager the job.
- **House looks** (23.10a): the Village Hall's Builds button opens House looks, a list of the village's own houses
  (workshops, clinics, guard houses...). The village's leader (the hall's owner and their friends) can give any of them
  another village style's outside (plains, desert, savanna, snowy or taiga); the village's builder rebuilds the outside
  in place and leaves the room inside, its job block and its chests as they were. Builders now also pick up what falls
  off a block they take down (a lantern under a porch roof) instead of waiting for it.
- A village habitat of its own (Cobblemon 1.8, ROADMAP 28.14): an Expert Habitat Keeper puts one natural Habitat Block under the finished Habitat Garden's mossy centre stone (it still looks like moss), with Pokémon that suit the biome (`data/aliveworkplace/village_habitats`, 20 biome files). One per village; taking the garden down removes it without a drop. Keepers visit the Habitat Blocks round their pasture each day and the hall's list shows who comes today. On Cobblemon 1.7 the keeper's page says it needs 1.8. Config `villageHabitats`.
- **Builds for the Pokémon jobs** (28.13, with Cobblemon): the Camp Kitchen (an open timber shelter round a Campfire
  Pot, benches and a grain store; II adds a Hearty Grain plot and a smokehouse), the Berry Nursery (fenced farmland beds
  in pairs, a composter and a potting bench; II adds a greenhouse with four more beds) and the Daycare (a barn with a
  straw-floored nursery and a paddock round a Pasture Block; II adds a second paddock and a hatchery), the Habitat Garden
  (a wild garden in a hedge with a pond, a Saccharine tree and a Pasture Block round a mossy centre stone; II adds a
  keeper's hide on stilts and a second pond) and the Gem Grotto (a stone shed over a lava pool behind glass, ledges for
  tumblestones, a stonecutter and an amethyst niche; II adds a deeper chamber with four Deepslate Crystal Cores, plain
  deepslate before Cobblemon 1.8). In the Blueprint Table and sold by Journeyman Camp Cooks, Berry Breeders, Daycare
  Keepers, Habitat Keepers and Gem Growers; the Gem Grotto needs no Cobblemon. Builders put the pot on the Campfire Pot.
- **The Daycare Keeper** (28.12, with Cobblemon): a villager at a Pasture Block, picked with an egg. Leave one pair of
  Pokémon per player (three pairs per keeper); her screen says how well they get along. Each dawn she may find an egg
  (70/50/20%, +10% at Expert), 4 emeralds each to collect: a real Cobbreeding egg with Cobbreeding, otherwise the level-1
  hatchling with inherited IVs, Everstone nature, ball, hidden ability and egg moves. Her pairs go to their PCs if she
  dies. Config `daycareKeepers`.
- **Gifted villagers** (29.6): about one villager in 30 has a rare gift as well as their traits, shown in gold on the
  Village Hall's list. Prodigy learns three times as fast; Iron Will never panics and keeps working through raids and
  the bell; Silver Tongue's trades are 20% cheaper; a Night Owl works from dusk to dawn and sleeps from mid-morning to
  mid-afternoon (builders and miners too). A Gifted villager sparkles when they level up, and the chronicle notes one
  who joins from the inn or grows up. Gifts are data (`data/<ns>/gifted/<id>.json`); config `giftedChance` (30; 0: none).
- **Legends who visit** (29.8): once a day a Legend whose village qualifies may come as a guest (1 time in 4 by
  default; Open Gates makes it likelier): to the inn in the morning instead of a traveller, with the traders on market
  day, to a big festival's fireworks, to a finished Chapel at a full-moon midnight, or to the Village Hall in the
  morning. Guests are announced, take no job and stay up to 3 days; right-click one for their terms (who they are, what
  they'd bring, what they want with ticks and crosses, days left). The round every need is met they settle: they claim
  the bed in the tier III home, become a Master of their trade (taking a free workstation) and go in the chronicle.
  Otherwise they leave on the third evening when nobody's looking, the chronicle says what they missed, and they don't
  come back for a week.
- **Four more gifts, and Legends born** (29.7): Lucky (luck +3 on explorer finds, Netherworker trips, sifting and
  fishing; the sifting tables now weigh their rare finds by luck), Hardy (never ill, twice a villager's health), Beloved
  (everyone whose bed is within 16 blocks of theirs is +5 mood, "a beloved neighbour") and Born Leader (workers of their
  trade within 16 blocks work 10% faster). A child of two schooled Masters grows up a Legend 1 time in 20 (a Rare one
  born to a parent's trade, when the village qualifies and the slot is free), else Gifted 1 time in 4, and the chronicle
  says so. Inn travellers are Gifted 1 time in 10, shown on the hire screen, at twice the price.
- **Legends' needs and strikes** (29.5): once a day the Village Hall checks what each settled Legend needs: a home of
  their own (their bed in a finished tier III building, shared with nobody but their spouse), their luxury once a week
  (wine, jewels, books or fine clothes, taken from a chest in their home or else the village store, +10 mood; for now
  honey bottles, amethyst shards and emeralds, books, and leather armour, through the `aliveworkplace:luxury/*` item
  tags) and a happy village (average mood 60, or wellbeing 60% with moods off). After a 3-day grace, a need unmet two
  days running starts a strike: their powers stop, their trade's work stops, they picket by the hall by day under a red
  "On strike: a home of my own" line, and the hall, the Legends page and the chronicle say so. The day every need is
  met they go back to work. Legends never leave: no inn departure, no despawning, and the hall's call-home passes them
  by. `legendNeeds` in the config turns it off.
- **Legends on the hall, and how they look** (29.4): the Village Hall has a Legends page (a nether star in the page
  row): the village's own Legends first, then every Legend as a card with its rarity, how it comes, each condition with
  the village's progress, the luxury it likes, its powers, "lives in ..." for a Legendary taken elsewhere and the Mythic
  line. The hall's list puts Legends first, name and title in gold, with their powers, needs (tick or cross) and any
  strike in red. "What next?" names a Legend one condition short, villagers chat about Legends and guests, and Legends
  wear an outfit over their trade's (a gold circlet and wine-red cape until each has its own), sparkle every 10 seconds
  and have their name in gold over their head. README has a new Legends section.
- **The Steward's rules for homes and storage** (27.10): with beds short he first upgrades a home whose next tier
  sleeps more (beds counted from the blueprints), then wishes for a Starter Cottage, Stone House or Terrace in Homes, or
  an Inn in Market for a town. He also asks for a Storehouse and its upgrades and a Market Stall in Market, and a Berry
  Farm and a Ranch in Farms when food is short.
- **Legends: rarities, caps and the server's record** (29.3): the server keeps a saved record of every Legend. A Rare
  Legend comes once to each village, a Legendary one once to each world, and a village holds Mythic ones by its rank
  (`mythicLegendCap` in the config file, default Hamlet 0, Village 0, Town 1, City 2). Rare and Legendary arrivals are
  told to the village and the hall's owner, Mythic ones to every player in gold with the village's direction from spawn;
  all go in the chronicle under a nether star. A Legend in a grave or turned zombie keeps their slot and comes back as
  themselves; with no grave their slot frees after 7 days; Legends join a hall placed again.
- **Conscription** (30.10): a new edict under a stone sword. While the village is raided (a monster or bandit raid, or a
  pillager raid there) every grown villager who isn't ill fights beside the guards: they wake, never panic, hold a stone
  sword made for the raid (never taken from a chest, gone when the raid ends) and go for the nearest raider within 24
  blocks, 3 damage a blow, 15% more if Strong. Children and the ill hide as before, and conscripts can fall like anyone
  else. The cost: all work in the village stops during the raid and until noon the next day. Reform **The Militia
  Drill** (32 iron swords, 32 shields, clear out 48 monsters): everyone still fights, but work stops only for villagers
  with a raider within 24 blocks, and the morning after is a normal day.
- **Curfew** (30.9): a new edict under a bell. From dusk to dawn every grown villager but the guards and mercenaries
  goes to bed, and a monster can't hurt a villager asleep in their bed; monster raids and bandit camps are half as
  likely, and the nights count as safe in the village's wellbeing. The cost: nobody trades with players at night
  ("Curfew: come back in the morning."), a festival ends at dusk without fireworks, market traders leave at dusk, and
  netherworkers and explorers don't set out after midday. It can't be in force alongside Open Gates. Reform **The
  Lamplighters** (24 lanterns, 8 glowstone, clear out 8 monsters): raids stay half as likely and the nights safe, but
  trading, festivals, markets and night work go on.
- **Festival Season and Tithe** (30.8): the two treasury edicts. **Festival Season** holds a festival every 4 days
  instead of 8; each costs the treasury 3 emeralds and 1 more for every 4 villagers, taken on the festival's morning.
  When the treasury can't pay there's no festival: the chronicle says there was no money and the village is 5 less
  happy that day ("disappointed"). A festival called with a cake stays free, and the hall's festival icon shows the
  cost and what the treasury holds. Its reform **The Festival Fund** (4 cakes, 32 firework rockets, 8 note blocks) keeps
  them every 4 days for free. **Tithe** puts a tenth of the emeralds players pay the village's villagers into the
  treasury (up to its cap), but their emerald prices are 10% higher (rounded: 20 becomes 22, trades under 5 emeralds
  don't change). Its reform **The Fair Ledger** (4 books and quills, 16 gold ingots, beat one of the village's trainers,
  or clear out 8 monsters without one) keeps the tithe at the usual prices. Data packs get the effect types
  `festival_every`, `festival_cost`, `tithe` and `trade_prices`.
- **Open Gates** (30.7): a new edict. Inns in the village take 4 guests (not 2) and up to two travellers arrive a
  morning; market days bring one more trader. The cost: bandits make camp near the village twice as often. Open Gates
  can't be in force alongside Curfew. Its reform **The Watchful Gate** (16 iron ingots, 32 arrows, clear out 12
  monsters) keeps the travellers coming with bandits no likelier than usual. The Book of Edicts tells it in words. Data
  packs get the effect types `inn`, `market_traders`, `bandit_camps` and `legend_visits` (read once Legends visit inns).
- **Free Bread and Large Families** (30.6): two new edicts. **Free Bread** makes everyone fed in the last day 10
  happier ("free bread"), but the village eats 30% more (the hall takes 3 extra meals for every 10 eaten); its reform
  **The Common Granary** (64 wheat, 16 hay bales, 8 barrels) takes the extra food away. **Large Families** lets up to
  two babies be born a day, but a baby needs 24 meals in the store (not 16), the family eats 12 (not 8) and villagers
  fall ill 50% more often; its reform **The Midwives** (8 honey bottles, 16 white wool, 4 golden carrots) keeps two
  babies a day with the usual food and sickness. The Book of Edicts tells both in words. Data packs get the effect
  types `food_use`, `births` and `sickness`.
- The **Gem Grower** (28.11): sneak-right-click a villager by a stonecutter with an amethyst shard. She picks the full
  amethyst clusters round budding amethyst (never the budding block); with Cobblemon she plants tumblestones against
  lava or magma and picks the full clusters, and with Cobblemon 1.8 sets Type Gem Blocks against Deepslate Crystal Cores,
  picks the stage-3 clusters and makes Blank TMs from shards and glass (up to 8). Beds are data files
  (`gem_beds/<name>.json`); sneak-right-click her to pick which she keeps. Rock and Steel partners. Config `gemGrowers`.
- With Cobblemon, the **Habitat Keeper** (28.10): sneak-right-click a villager by a Pasture Block with a honey bottle.
  She keeps Poké Snacks set out on up to three lure spots (marked with a Field Marker, or grass 16-32 blocks out) and
  sets out another when one is eaten up; picks a lure (a type or egg group, Alphas on 1.8) and asks the Camp Cook for
  snacks seasoned with its berries; slathers Saccharine logs with honey and plants Saccharine saplings; and tells the
  village of shiny, rare and Alpha wild Pokémon (chronicle, the hall's list). Flying and Grass partners. Config
  `habitatKeepers`, `habitatSightings` (on).
- **The Steward gives jobs and picks research** (27.9): each morning every grown jobless villager (never a nitwit or a
  child) gets a free workstation for the village's biggest gap: a builder while there's none, a farmer while food is
  short, guards while they're short, a porter at a free Storehouse, a scholar while research is idle, then the nearest
  block. At a shared block he gives its other jobs too: the composter in a Berry Farm with no Orchard Keeper gets one.
  In Ask me first the morning's jobs are one proposal ("Give 3 villagers jobs: Dara, Farmer at the Composter 12 blocks
  east; ..."). When a scholar works with nothing to research, he picks the next topic: Fortification after a raid this
  week, Medicine with 2 or more ill, Green Thumb while food is short, Logistics with the store 80% full, else Swift
  Hands, Hearth and Kinship, never one that can't be taken up yet; in Ask me first it's a proposal. Run the village does
  both by itself; Rest does neither. Both are noted in the chronicle.
- **The Steward's desk** (27.8): with a Steward, the hall's "What next?" page is his desk: his level, the mode (**Ask me
  first**, **Run the village**, **Rest**), his open builds (shift-click cancels one), up to 9 proposals and the tips as
  before. Each proposal shows the building and its style, why, where, the five materials it needs most against the
  store, and which builder takes it after what; its page has **Approve**, **Decline** (not proposed again for 3 days),
  **Show me** (its outline glows for 30 seconds), **Another spot** and **Another style**, and the desk has **Approve
  all**. Approving starts the build for that builder, owned by the hall's owner, and notes it in the chronicle (new
  kind: Plans); upgrades go on the building's own spot. In Run the village he approves them himself each morning and
  tells the owner in one line; Rest plans nothing. Unanswered proposals lapse after 3 days; the owner hears once a
  morning when new ones are waiting. Same rights as the plan. Config `stewardSelfRun` (off: every village asks first).
- **Finding a plot** (27.7): for each building he wishes for, the Steward finds where it fits in a zone of its kind, in
  the zone's style: nearest the hall first, its front facing the nearest road on the plan (else the hall), the
  footprint and 2 blocks round it inside the zone and off its roads, the ground under it within 4 blocks of level, at
  most a tenth over water and none over lava, nothing in the way but natural ground, plants and trees (packs add more
  with the block tag `aliveworkplace:steward_clearable`), 2 blocks clear of every build site and building, within a
  builder's reach of a Blueprint Table, and never the same building mirrored the same way within 24 blocks. The search
  reads at most 64 columns of ground a tick per village and keeps its answer until the plan or a build changes.
- The **Steward's rules** (27.6): what the Steward wants is data, `data/<namespace>/steward_rules/<name>.json` (conditions
  such as `beds_short`, `food_short`, `jobless`, `store_full`, `research_idle`; one effect: build, upgrade, give jobs,
  research or ask a player; priority, reason, cooldown, max, rank and mods). Each morning, back at the hall, he ranks
  the rules that hold into the day's wishes and says the first over his head ("build a Stone House in a Homes zone: 3
  villagers have no bed"); they're saved with the hall. `/workplace steward explain` lists every rule for the nearest
  hall with each condition's number and whether it held. 13 starter rules; they read the same numbers as the hall's
  "What next?" tips, so the two always agree. A broken rule file is skipped with a warning naming the file and field.
- **Reforms, and The Shift Bell** (30.5): while an edict is in force, the hall's quest page shows its reform's next step
  in the row below the daily quests (a book and quill; it never expires). A new step goes up each morning after the last
  was done, and each pays emeralds like a quest (a quarter more a rank). The last step reforms the edict for that
  village for good: its boost stays, its cost goes, fireworks go up over the hall, the village is told and the chronicle
  and the Book of Edicts keep it. Progress is kept while an edict is lifted. Long Shifts' reform is **The Shift Bell**:
  bring 4 clocks, 8 gold ingots and 32 bread, and the village keeps working 20% faster without the mood loss. Data
  packs give their edicts a `reform` with `bring`, `slay` or `battle` steps (a battle falls back to its `fallback` step
  without Cobblemon or a trainer).
- The **Book of Edicts** (30.4): the Village Hall's lectern button (slot 9), or a Village Ledger used while sneaking,
  opens the village's laws: its edict slots (in force with their days, free, or locked until the next rank), and every
  edict with its boost in green and its cost in red. Click an edict twice to proclaim it, once in force to lift it; only
  the hall's owner, friends and operators may. The hall's name icon lists the edicts in force, and the people list's
  page arrows moved to its bottom corners (25 people a page).
- The **Steward** (27.5): sneak-right-click a grown villager beside a Village Hall with that hall's City Plan and he
  becomes its Steward, one per hall. Each morning he walks his rounds holding the plan (each zone, the storehouse),
  three seconds at each stop, then goes back to the hall for the day. He trades City Plans, Blank Blueprints and
  Village Ledgers and buys paper and books; his level will set how many of his builds may be open at once (1 to 4,
  capped by the village's rank and `stewardMaxOpenBuilds`). New outfit; breaking the hall ends the job; config `steward`.
- With Cobblemon, the **Camp Cook**: sneak-right-click a villager by a Campfire Pot with Hearty Grains. She cooks in the
  pot itself (makings in, seasonings on top, lid shut, the pot's own cooking time) from the chest beside it: Poké Snacks
  and Bait, Aprijuice, Exp. Candy, Ponigiri, stews, curry and more, 16 of each, and the sweets and candies for a
  Storehouse's stock orders. Her menu is data (`camp_menu/*.json`); her meals feed the village and count for Diet;
  farmers sow Hearty Grains and Vivichoke. Fire and Normal partners. Config `campCooks` (on).
- **Legends, the engine** (1.3's first piece): Legends are read from data packs (`data/<ns>/legends/<id>.json`, so
  server owners can add their own), each with the conditions a village must meet (rank, villagers, finished buildings
  and styles, Masters of a trade, emeralds the treasury has ever taken in, caravan routes, meals, festival crowd,
  animals at work, research, iron golems, full moon, first City, another Legend), the luxury they like and their
  powers: workers near them work faster (never more than twice as fast) and villagers near them are happier. A new
  `legend` profession with its own outfit, `/workplace legend list|make <id>|clear` for ops, and a `legends` switch in
  the config. No Legend ships yet: the twelve come in the next updates.
- With Cobblemon, the **Berry Breeder**: sneak-right-click a villager by a composter with any Cobblemon berry. Her
  berry book (sneak-right-click her) lists every berry Cobblemon knows (data packs' too): the ones the village has found
  lit, the rest with the pairs that make them. Click one and she works out the chain from the village's berries, plants
  each step's two parents side by side in the farmland round her composter (or a Field Marker's plot), puts Growth and
  Surprise Mulch on them, picks the fruit and notes every new berry in the Village Hall. She sells common berries,
  mulch and, from Journeyman, the berries her village has found; Grass and Bug Pokémon help her, and a Bug partner
  flits between the paired plants. Config `berryBreeders` (on).
- **The City Plan** (the first piece of 1.1, villages that build themselves): a new item, crafted from a Map and a
  Blank Blueprint. Right-click a Village Hall to bind it; right-clicked in the village it tells you which zone of the
  plan you stand in. Each hall now keeps its village's plan (zones of eight kinds: Homes, Workshops, Farms, Market,
  Civic, Gardens, Defences, Keep Clear, which packs can add to), and a broken hall carries its plan to wherever it's
  put down. Only the hall's owner, their friends and operators may change a plan.
- **Painting the plan**: right-click the air with a bound City Plan to open the plan screen: the village map with a
  grid over it, each zone tinted in its colour and named, Keep Clear hatched and build sites going up outlined. Add,
  rename and delete zones, set each one's kind, style and "renew old houses" switch, paint cells with the brush or
  drag an area, erase (right-click erases too), undo the last 10 changes, and read the legend.
- **Roads and the wall line on the plan**: the plan screen's Road tool draws a road point by point (double-click or
  Enter to end) as a lane (1 wide), a street (3) or an avenue (5), in the style of the zone it starts in unless you pick
  one; right-click a road to take it off. The Wall tool draws one wall line round the village, open or closed. A plan
  holds up to 24 roads of up to 64 points, and roads you draw are approved for the builders. While you hold the City
  Plan, the zones' edges, the roads and the wall line show on the ground round you in their colours, and the hall's
  map button now draws the zones and roads on the map, ready to hang in an item frame by the hall.
- With Cobblemon, the **Pokémon Center**: a bright hall with a glass front under a red roof, Cobblemon's Healing
  Machine on the counter, a PC beside it, potions and benches; **Pokémon Center II** adds a lodge with four beds and a
  trade corner with a Shop Counter, and a garden with a Pasture Block. In the Blueprint Table (with Cobblemon only)
  and sold by Journeyman Nurses; the Village Hall's "What next?" suggests one to a village of Village rank.
- A Healing Machine is a Nurse workstation too (hand the villager there a honey bottle). Right-click a nurse working at
  one and she puts your team in the machine, free; while she's on shift it stays charged. Config
  `nurseHealingMachine` (on). Builders now build Cobblemon's two-block PC and Pasture Block.
- Server owners get two new settings for big villages: `maxWorkersPerVillage` (jobless villagers stop taking free
  workstations once a village has that many workers; nobody loses a job) and `workerPathRange` (how far workers look
  for a path in one go). Every config option, including each village system's on/off switch, is now in the README.
- Mirroring a blueprint you already placed (on the style screen) flips it where it stands, and its ghost shows it at
  once. Cancelling a build gives the blueprint back still placed: hand it back to carry on, or click the ground to
  move it; what the builder carried goes back to its chests.
- Builders are now tested against getting stuck: five long runs trap a builder in water, beside lava, in holes, behind
  fences, in a room with a door, on its own roof and through chunk reloads; it gets out every time, finishes the build
  and never breaks a block it placed.
- Importing builds tells you more: blocks from mods that aren't installed are named in the message ("…became air:
  create:shaft, create:andesite_casing"), a build made only of such blocks is refused naming them, a file cut short in
  a download says so, and a damaged Litematica file names the region. Builds from Minecraft 1.12 (before the
  flattening) and WorldEdit's first `.schem` version import, and big builds (48 × 8 × 48) read the same from
  `.litematic`, `.schem` and `.nbt`.
- **A settings screen**: with Mod Menu installed, the mod's Configure button opens every setting of
  `config/aliveworkplace.json` as sliders and on/off buttons, each with a tooltip saying what it does. Closing the
  screen saves the file and puts the settings into effect in your own worlds; a server keeps its own file.
- **Partners at work**: a Fighting-type Pokémon pastured by a Builder's Bench is now seen helping: when the builder
  fetches materials it walks over to the work (never leaving its pasture's range) carrying them, lends a punch and walks
  back. Shows are data packs (`data/<namespace>/partner_shows/`); setting "Partners at Work" turns them off.
- **Partners at work for everyone else**: a Ground, Rock or Steel partner digs alongside the Miner in a shower of
  crumbs; a Water or Ice one swims round the Fisherman's bobber; a Psychic one floats a book among enchanting glyphs
  beside the Scholar (and, Psychic or Normal, beside the Teacher in class); a Fairy, Normal or Psychic one sends a pink
  pulse over whoever the Nurse heals or cures; a Poison or Grass one stirs the Composter's bin (green bubbles); a Grass
  or Fairy one sprinkles the Florist's garden; a Bug or Grass one circles the hive being harvested; a Ground or Rock one
  shakes the dust from the Sifter's sieve; a Fire or Dark one walks the Netherworker to the portal, flames at its feet;
  a Flying or Ground one scouts ahead as the Cartographer sets out; a Normal or Ground one walks beside the wild horse
  the Rancher is breaking in.
- **Partners at work in the post, the forge and the kitchen**: a Flying partner by the Postal Desk takes the air mail
  up with a bundle, climbs out of sight and lands back empty-handed, and on the round flies ahead to the next mailbox.
  A Fire partner breathes fire into the furnace, blast furnace or smoker each time it smelts on the spot for an Armorer,
  Miner or Fisherman. By the Chef's stove a Fire partner fans the flames and a Normal one carries the dish to the
  chest. A Steel or Fire partner sparks at the Toolsmith's or Ball Smith's table, then carries the new tool or the
  batch of balls to the chest; a Steel or Fighting one holds the worn piece at the Weaponsmith's grindstone; a Flying
  one brings the Fletcher a feather and a Bug one string; an Electric or Steel one sparks over the Tinkerer's work and
  over the iron golem being mended.
- **Partners at work on building and the land**: pastured Pokémon now help more workers, each by its type. For the
  builder, a Fighting partner shoulders the logs and planks and punches each block home, a Rock one carries the stone
  and a Steel one the iron parts (bars, doors, chains, lanterns). A Fighting or Normal partner hauls a barrel along on
  the Porter's round; a Fighting, Rock or Steel one holds the board or stone at the Carpenter's or Mason's table. A Water
  partner waters the patch the Farmer tends (that farmland turns fully moist), a Grass one sparkles over the crops and a
  Ground one walks the furrow being tilled. A Fighting partner punches the Lumberjack's trunk and a Grass or Bug one
  brings the sapling to the stump; a Flying or Bug one flutters through the plant the Orchard Keeper picks. Show files
  can now limit what's carried with `carry_tag`, and the toolbox has `crack` and `dust`.
- **Cobblemon 1.8**: tested with Cobblemon 1.8.1 as well as the pack's 1.7.3 (every Pokémon job passes its tests on
  both); 1.8 no longer logs an "outside the tested versions" warning.
- Names read the same everywhere: the Fisherman is never "Fisher", and the mod's blocks and items keep their capitals
  in messages ("Travel Post", "Shop Counter", "Field Marker", "Delivery Note", "Training Dummy").
- Bug reports on GitHub now use a form that asks for the version, the steps, `latest.log` and any crash report.
- Ready for 1.0: the mod's version now names its Minecraft version, as the jar's name does (`0.138.0+1.21.1` today,
  `1.0.0+1.21.1` at 1.0), so Mod Menu, crash reports and bug forms say which game it is for. Mod Menu links to the
  source and the issue tracker; the README's license section says what GPL-3.0 allows and credits MineColonies, whose
  ideas two jobs take; this changelog starts with how to read it.
- Over a builder's head, under its progress: the three materials the build is shortest of, with counts (or that it
  has everything), and where the builder takes materials from (its chests, or the storehouse, by its bench at x y z).
- **The village calendar**: the world now has four seasons of 8 days (`seasonDays` in the config), each with a festival
  on its middle day: the Blossom Fair, the Midsummer Games, the Harvest Feast and the Lantern Night. The Village Hall
  shows the season, the day and the next festival; nothing else changes with the seasons yet (coming expansions use it).
- **Room on the Village Hall's screen**: a row of page tabs under the hall's buttons, with the calendar as the first
  tab and room for eight more pages. Every button stays where it was; the villager list starts one row lower.
- **The Guide Book**: every player is given one the first time they join (on an existing world too, after updating);
  right-click it to read how the mod works, page by page, each page an in-game screenshot with a few short steps:
  getting started, giving villagers jobs, builders and blueprints, the Village Hall, every kind of job, guards, and
  with Cobblemon the Pokémon jobs. Lost it? Craft another from a book and wheat.
- The server log now says "Builder stalled" when a builder's site makes no progress for 30 seconds (once per stall,
  with the build, its stage and what's missing), so a stuck builder shows up without anyone watching. For testers,
  `/workplace soak` (benchmark servers only) sets 10 builders on the whole starter set in hilly woods for 2 days (or more) and
  checks that no item was duplicated or lost.
- **A material list you can take away**: hold a Book and Quill in your other hand and right-click with a blueprint.
  The book lists everything the build needs, biggest first, as a checklist; placed near a Blueprint Table, what its
  chests already hold is taken off, so it says exactly what is still to bring.
- **Villages keep working when nobody is near**: while anyone is online, every worker's workstation (and the chunk the
  worker is in) stays loaded, so a village far from all players carries on, also right after a restart. Server owners
  who'd rather pause them set `keepVillagesWorking` to `false` in `config/aliveworkplace.json`.
- `/workplace soak <days> split` (testers, benchmark servers only): the soak with each builder's materials split
  between at most 3 chests by its bench and the village storehouses.
- **Edicts**, the village's laws, and the first one, **Long Shifts**: everyone works 20% faster, but every grown
  villager is 10 less happy ("long shifts" in the hall's list). A Hamlet may keep 1 edict in force, a Village 2, a Town
  3, a City 4; an edict stays at least 3 days (`edictMinDays`), and a village that drops a rank loses its newest.
  Everyone in the village is told, and the chronicle keeps it. For now operators proclaim and lift them with
  `/workplace edict proclaim|lift <id>` in the village (the Book of Edicts on the hall comes next); only the hall's
  owner, their friends and operators may. Data packs can add edicts (`data/<namespace>/edicts/`) or switch ours off
  (`"enabled": false`); `villageEdicts` turns them all off.

### Changed
- **Reforms are a real grind** (30.1a): every edict's reform now asks for hundreds of items, handed in over as many
  trips as it takes (The Shift Bell: 24 clocks, 128 gold ingots, 300 bread, where it was 4, 8 and 32; the clearing-out
  steps ask for 32 to 40 monsters). What a step already has is kept; the emeralds each step pays are unchanged.
- **A Steward must be a seasoned Builder** (27.1a): only a Builder of Journeyman level or higher can be appointed
  Steward (he starts the job as a Novice Steward); others are refused with what's needed. Stewards already appointed
  keep their job. The City Plan now also takes a Heart of the Sea (Map + Blank Blueprint + Heart of the Sea).
- Builders passing materials to a crewmate close by make only a soft sound (no item flies), and the whole server plays
  at most 4 of those sounds every half second, so many busy crews can't cause lag; the rest are skipped.
- Seasons last 16 days now (a 64-day year), so festivals come every 16 days. A config file that still holds the old
  default of 8 moves to 16 by itself; any other length you chose stays.
- **One pace, one cap**: everything that makes a worker faster (Pokémon partners, a well-kept village, Swift Hands,
  Diligent, a happy mood, Craftsmanship, Expeditions) now adds up to at most **twice the usual pace**, set by the new
  `maxWorkPace` option (100 to 400 percent, default 200). Sickness, a bad mood, Lazy and a badly kept village still slow
  a worker after that (ill: half the capped pace), and a worker's level (Novice to Master) stays outside the cap. A
  builder's status and the hall's people list show the total and why ("100% faster: at the cap (Machop from the
  pasture, a happy mood)"). Teachers, scholars, ranchers, crafters' Craftsmanship and explorers' and netherworkers'
  rests and trips now get the whole pace too, not only their partners. **This slows down the very fastest workers**:
  a Master with three partners in a well-kept village was already past twice as fast.
- **Partners count once**: sifters, beekeepers, florists, composters and explorers searching a stop counted their
  Pokémon partners twice; now once (two partners: 70% of the usual time, not 49%). **These five jobs are slower with
  partners than before.**

### Fixed
- A crew's last blocks no longer wait on a walk to the chests (B66): a helper with nothing left to help with brings
  the lead what the last blocks need, where it used to carry it back to the chests (the build's last lantern) for the
  lead to walk over and fetch. A crew of four built the stone house in 28-35% of the time alone in 15 runs (it was up
  to 40%, and 44-47% on CI).
- The Pathfinder really stands still while the player catches up: between its checks, its everyday routine could walk
  it off (5 blocks once).
- Villagers no longer get stuck against the flower boxes beside the steps of our village houses (every village type):
  the pots now stand on upside-down stair sills instead of head-height trapdoors, which villagers mistook for open
  ground (B69).
- The showcase client starts every scene again without Cobblemon: the Habitat Keeper scene's wild Eevee is spawned
  from a Cobblemon-only helper, so loading the scenes no longer crashes (B59).
- A pastured Pidgey (or any flying partner) taking the postman's air mail no longer vanishes into your PC: it climbs
  only as high as its Pasture Block lets it roam, instead of flying past that and being sent back (B52).
- Every switch on the settings screen fits its button again, on or off: shorter names for Nurse Healing Machines,
  Villages Work Unseen, Legend Needs, Strikes, Stewards Run Villages and Legends at Ruins (each tooltip still says it
  all) (B61).
- Berry Breeders count their Pokémon partners once: two partners make mulching take 70% of the usual time, not 49%,
  so a partnered breeder stays under the `maxWorkPace` cap like every other worker (B53).
- A builder whose path leads somewhere it can't actually walk (under a trapdoor flower box, up into a loft) now hops
  there after two seconds standing still instead of five, so a crew's last blocks no longer drag on.
- **Helpers really speed a build up now**: two builders on one build take about half the time of one, four about a
  third (the stone house: 4200 ticks alone, about 2110 with two, 1220-1470 with four; before, 63-71% and 46%). Helpers
  fetch for a stretch of work instead of a handful per block, pass each other materials when close instead of walking
  over, go for the free block nearest them, help with the end of each stage, and the lead takes work within reach
  instead of walking off while a helper is on its next block.
- A build's progress no longer drops after a server restart while its foundation is being filled (it showed 12%,
  then 1%).
- Builders use the village storehouse on their own: one whose bag is full while its chests are full (or it has none)
  empties it into the storehouse's chests instead of dropping it on the ground, and so does a builder finishing a
  build; a storehouse no longer drops out of the village while its porter is out on a far errand; and a builder whose
  next site is far away takes its first materials from the storehouse too.
- Screens checked at GUI scales 2 and 4: the Shop Counter's title no longer runs off its panel ("Goods on top, prices
  below"), and the Blueprint Table's list no longer draws a blueprint's size over its selection box, nor 3-digit
  material counts over the next icon.
- Messages with a number say "1 parcel" and "3 parcels" instead of "parcel(s)", and item counts read "28× Spruce
  Planks" everywhere (the Village Hall's requests, research costs, the shop's sales log), as the builder's already did.
- A builder whose next build is far from its chests takes the first materials along before clearing the site, instead
  of walking all the way back for them once the site is clear (it stood idle for over half a minute).
- With Chipped or Rechiseled installed, a builder carrying a variant of the block it needed (taken in place of
  another block of the same kind) no longer stands waiting for materials with nothing on its missing list: it turns
  the variant into the block it needs and carries on.
- The Village Hall's name tag now says what a shift-click will do: protect an open village, or open a protected one
  to everyone again (it always said "protect").
- Masons and carpenters helping one builder no longer use up the stone or wood another builder's build is waiting
  for: from that builder's chests they take only what's spare.
- Nurses, shopkeepers and ferrymen keep their block when they get stuck on the way back to it, instead of giving it up
  after a minute and taking another worker's.
- With several builders in a village, a builder who runs short no longer empties another builder's chests of what
  that builder's own build needs (which left it waiting for glass panes it had been given): it takes only what's spare.
- Builders on a hillside, with their site far up the slope from their bench, keep placing through clearing,
  foundation and levelling without half-minute pauses (now covered by tests).
- With Cobblemon, a herder no longer stands waiting under a pastured Pokémon that's flying about: they get on with
  other work and brush or milk it once it has come down.
- Every flower now makes a Florist at a composter, pink petals and spore blossoms too, and the composter's tooltip
  says "Any flower" instead of "A small flower".
- Miners, postmen, tutors, Pokémon traders and trainers keep their workstation too: after a minute stuck, or over 100
  blocks from it, they gave it up and could take another worker's.
- A builder keeps their own bench. A builder who got stuck for a minute, or wandered over 100 blocks from their bench,
  gave it up and took the nearest free one, often another builder's, so two builders could swap benches over and over.
- A builder always takes its materials from the chests by the bench its build was started from. Two builders with
  benches close together could swap benches mid-build and use each other's materials, so one build ended a few
  blocks short and its builder waited for materials its own chests had held.
- A builder no longer stands waiting for materials with nothing on its missing list when the last of a block is
  in a helper's pockets: helpers keep only what the block they are working on needs and pass the rest over.
- Two villagers no longer end up on one workstation when a job is changed by hand or at the Village Hall: a worker far
  from a block that was broken and put back no longer takes it back from its new (unloaded) owner, a job chosen at a
  replaced block holds it, and a Village Hall assignment that fails leaves the worker's own block alone.
- A tall flower (sunflower, lilac, rose bush, peony) given to a farmer at their composter now makes a Florist, as the
  Guide Book says; before, only small flowers did and a tall one silently did nothing.
- Builders levelling the ground carry enough dirt from their chests for the whole hollow, instead of walking back for
  every block (and keep the dirt they dug up for it), so tidying up around a build far from its chests no longer crawls.
- Warding now protects the whole village, corners included: a creeper or TNT going off in a corner of a warded
  village, or just past its edge, no longer breaks the village's blocks.
- The Village Hall's Guards and Mercenaries buttons, and Research's Drill topic, no longer show an iron sword's
  "When in Main Hand: 6 Attack Damage, 1.6 Attack Speed" lines under their own text.
- Giving a villager a new job no longer frees a workstation someone else now works at when that other villager is
  far away in an unloaded chunk (their block had been broken and put back while the first villager was away).
- Leaving the game in the middle of a ferry ride lands you by the far post before you are saved, so you come back
  there, and the ferryman stays at his jetty. The ride now ends on the server thread as you leave, not on the network
  thread.
- The nightly modpack test ran without the pack's configs, datapacks and bundled mods (the pack stores them unreadable
  for anyone but an administrator), so its performance soak measured idle villagers. It now loads all of them, and
  stops if any are missing.
- The orchard house test that failed about 1 run in 50: it placed the house with its air, which dug a pit round it in
  the test floor that the orchard keeper could fall into. Villages never place that air, so players weren't affected.
- Giving a worker a new job (with an item or from the Village Hall) no longer frees another villager's workstation,
  when the worker's old one was broken while they were away and put back for someone else; and a far-off worker who
  still remembers that block isn't sent back to it.
- A lumberjack whose felled tree dropped no sapling (always with a Silk Touch axe) now replants that stump as soon as a
  sapling of its kind is in the chests, instead of forgetting it.
- The mod's recipes now show in the recipe book, each as soon as you hold one of its ingredients (none ever did).
- Zombie villagers in abandoned desert villages no longer get stuck in a house's wall (the same narrow corridor that
  trapped living villagers before 0.138.0).

### Tests
- The showcase's soak scene passes again: its check reads "items off: none" before the village chunk count, and a
  villager brushing an open door's panel is no longer reported as stuck in a wall (B42, B44).
- The builder soak no longer counts a supply run to far chests (30 s there and back without a block placed) as a
  stall: the split soak now ends with 0 stalls (B40).
- The showcase's title check measures from the title to the panel's right edge, so the player inventory's
  "Crafting" no longer fails the `missing` scene (B41).
- Every value the mod saves on a villager (all 72: a builder's site and bag, a lumberjack's tree farm, a couple's
  marriage, every worker's count...) is now tested to survive the villager being saved and loaded again.
- A full build with the compat GameTests now finishes in the 7 GB dev container: the compat server gets a 2.5 GB heap
  and the Gradle daemon hands its unused memory back while it waits (B55).

## 0.138.0 — 2026-10-02

### Changed
- **New item icons, picked by the owner**: the Blueprint is clipped to a drawing board, the Blank Blueprint is two
  sheets, the Shape Planner is a brass compass, the Village Ledger lies open, the Delivery Note is a clipboard, the
  Travel Ticket has a sailing boat, the Price Tag a $ sign, the Rally Banner is an upright standard with crossed swords,
  the Settler's Wagon is seen from the side, the Patrol Map and Field Marker are maps on vanilla's map outline, the
  Quarry Marker has a chequered swallowtail flag on a stake, and the Scan Tool is a drafting pencil.
- **Fewer job blocks**: our jobs now work at vanilla blocks, shared with the vanilla job there. Stand a villager by the
  block and sneak-right-click them with the job's item: a composter makes an Orchard Keeper with sweet berries, a
  Florist with a flower and a Composter with bone meal; a blast furnace makes a Miner with a pickaxe; a grindstone a
  Guard with a sword; a jukebox a Bard with a music disc, and so on (hold Shift over a workstation to see its jobs).
  A jobless villager still takes a vanilla block for the vanilla job by themselves. Builders work at the Blueprint
  Table. 26 job blocks can't be crafted any more (the Builder's Bench, the Guard Post, the Fruit Basket...), but the
  ones in your world keep working. Village houses, the camp and the blueprints use the new blocks, and a village house
  whose block is shared comes with its worker.
- **Fossil Scientists work at Cobblemon's Fossil Analyzer** instead of a block of ours: stand a villager by one and
  sneak-right-click them with a fossil. They still revive the fossil themselves, without the rest of the machine. The
  village fossil labs and the Research Lab blueprint have an analyzer now; scientists already working at a Training Post
  or a Fossil Lab keep working there.
- A postman's mailbox is the post office's counter: with no mailbox of your own, pick up your parcels there.
- **A new look**: every workstation, villager outfit and item is redrawn in Minecraft's own style. Workstations are
  built from their wood or stone like the crafting table, with no more grainy speckle. The outfits are cleaner, and a
  job's hat no longer has the biome's hat showing through it. Items take the shape of their vanilla kind: the Village
  Ledger lies like a book, blueprints and the Patrol Map like maps, and the markers and the Rally Banner are held like
  tools.
- **Cavalry on camels**: a guard rides a saddled camel too, as well as a horse, donkey or mule. A camel needs no taming,
  only a saddle.
- **Villagers sit when they ride**: a guard on horseback sits in the saddle with their legs forward, and a ferryman or
  a fisher sits in the boat at the oars, instead of standing on the horse's back or looking sunk in the boat.

### Fixed
- Villagers that come with a generated village house no longer suffocate in its walls: some vanilla desert houses put
  their villager in a narrow corridor, where it landed on the step beside it with its head in the ceiling.
- A blueprint with a long number at the end of its name (`house_20260929`, from an imported file) no longer breaks the
  moods of the villagers who live in it; only up to three digits count as a tier.
- A bed at the far end of a big building (a large scan, say) now counts as a home in it.
- The Village Hall's "What next?" tips about homes and the next rank showed the wrong numbers.
- **Village protection**: a stranger's arrows, tridents and thrown potions no longer hurt a protected village's
  villagers and animals; and a Village Ledger bound before the village was protected no longer opens its hall for a
  stranger (who could collect the treasury with it).
- Guards get off their horse, and fishers come ashore, as soon as their shift ends (they rode or floated on for up to
  a minute).
- Giving a job from the Village Hall no longer fails with an error when the villager's old workstation was broken
  while they were far away.
- Bandits raiding a village at night can no longer be drawn into a vanilla raid.
- Our village houses no longer leave invisible holes in the ground in front of their doors (villagers and players
  could fall into them, and a new villager sometimes never reached the workshop's bench).

### Dev
- A nightly showcase: GitHub films every job, screen and build family in the real game each night and publishes one
  page with a GIF, stills and a pass or fail per scene (https://jcondedata.github.io/minecraft-alive-workplace/). 33
  new screenshot scenes cover every job and screen that had none.

## 0.137.0 — 2026-09-29

### Added
- **Fishing from boats**: put a boat in a fisher's barrel and, when there's a lake or the sea nearby, they row out to
  open water and fish from the boat — where, as for a player, one catch in twenty is treasure (enchanted books, bows,
  name tags, saddles...). They row back with every few fish and put the boat away.
- **Ferry rides**: a Travel Ticket no longer blinks you across — you sit in a boat for a few seconds, rowed off by the
  ferryman when he's about, the view fades, and you step out by the post you were going to.
- **Cavalry**: leave a tamed, saddled horse (or donkey or mule) near a Guard Post, off its lead, and the guard rides it
  on patrol and into fights. They get down to spar at a Training Dummy and leave the horse where they are at the end of
  their shift. A horse on a lead stays tied up.

## 0.136.0 — 2026-09-29

### Added
- **Homes**: a villager's home is the building a builder put up that their bed is in. A tier II house (a Stone House II,
  a Cottage II...) makes its people happier (+5), tier III more (+10); the Village Hall's list says where each villager
  lives, and "What next?" suggests better homes when most of the village sleeps in first-tier houses or in ones no
  builder put up.

### Changed
- **Redrawn builds** (architect review, round 2): the Starter Cottage gets a gabled porch on fence posts (a porch hood
  on II and III); the Stone House a wall dormer over the door that lights the attic (II and III: over the middle bay,
  plaster with a little diorite in it); the Lookout Tower moss low on its walls and lanterns on beam ends by the door
  (III: a lantern at the guardhouse door and an archery butt); the Market Stall a scalloped awning edge (III: gable
  windows, a soffit and centred windows on the storeroom); the Market Square peaked kiosk canopies and stock beside them.
  Sizes are unchanged, so blueprints already placed still fit.
- **More redrawn builds** (round 2, second batch): the Library's gables are timber-framed over the stone, with a beam
  that runs on round the study tower, a soffit under the eaves and lamps at the steps; each home of the Terrace gets a
  dormer over its bedroom window; the Graveyard's lych-gate a little slate roof. The Inn, Tinker's Workshop, Nether
  Gate and Berry Farm passed the review as they are.
- **Walls and village houses** (round 2, third batch): moss low on the Stone Wall, the Wall Tower (with a spruce band
  at its top floor) and the Gatehouse (with a portcullis drawn up under its outer arch and lanterns either side of the
  way in); new village houses get a lantern over the door, and desert ones the ends of their roof beams showing under
  the parapet. The well, fountain, gazebo, street lamp, park bench and palisade passed as they are.

## 0.135.0 — 2026-09-29

### Added
- **Village protection** (a setting on the Village Hall, off until its owner turns it on): shift-click the hall's name
  tag and only you, your friends (`/workplace friend add`) and operators can break, place or open things in the village,
  or hurt its villagers and animals; everyone can still come in, open doors, trade and ring the bell. Whoever places the
  hall owns it (an older hall goes to whoever protects it first); `villageProtection` in the config switches it off.

## 0.134.0 — 2026-09-29

### Added
- **What next?** on the Village Hall's screen (the compass): what the village lacks, most pressing first, and how to put
  each right — a builder, beds, food, a Storehouse, guards, bandits, the ill, dark beds, jobs, a scholar, decorations,
  upgrades and the next rank.
- **Treasury**: every morning a village with a Village Hall puts by its takings (a fifth of an emerald a worker, more
  for a well-kept village and a higher rank); click the hall's name on its screen to collect them.

### Changed
- The jar is now called `alive-workplace-<version>+1.21.1.jar` (the Minecraft version it's for). When updating a server
  or a game, take the old Alive Workplace jar out of `mods` as usual; nothing else changes (same mod id, saves, config).
- Under the hood, no gameplay changes: the mod is now built the way that lets one codebase make a jar for each
  Minecraft version (a Stonecutter build; for now there is one, 1.21.1). Everything it needs from Fabric goes through
  one small layer and the Minecraft calls that change in newer versions through another, and the build checks the rest
  of the mod keeps to that. The Cobblemon and CobbleDollars integrations switch themselves off with a line in the log,
  instead of crashing, if a future version of those mods changes its API. The build runs on JDK 25 (the game still
  runs on Java 21).

## 0.133.0 — 2026-09-29

### Added
- **Warding** (research, after Fortification): explosions no longer break blocks in the village.
- **Shape Planner** (a Blank Blueprint and a compass): plan a box, cylinder, dome, sphere, cone, pyramid or arch — any
  size up to 32, solid or hollow, in a block you carry — and draw it up as a blueprint for a builder.
- **Village Map**: the Village Hall's map button draws the village on an empty map — centred on the hall, the land as
  it is that day, a coloured banner on every finished building (workplaces named) and the legend on its tooltip.
- **Library III**: an enchanting room behind the reading hall, under its own lower slate roof — an Enchanting Table
  ringed by fifteen bookshelves with candles, a lectern (a librarian moves in) and a chest for the lapis.

### Changed
- The **Ranch** is a proper barn now: a hayloft gable with open barn doors facing the paddock, stable aisles either
  side under a slate roof; the Ranch II adds a stable wing and a brick silo.
- The **Schoolhouse** is white plaster now (not pink), with a little gabled porch over the school bell and a lantern
  cupola on the ridge.
- The **Storehouse** is a granary now: a stone ground floor with open loading doors, a loft above with a hoist over its
  door and a dark oak roof; II's shed and III's warehouse range have their roofs running into it.
- Librarians enchant stronger with bookshelves round their table: a level more for every three (a full ring of fifteen
  lifts a Novice from level 10 to 15; a Master stays at 30).
- The Market Stall's striped awning is a finer slope (carpet steps between the stripes).

## 0.132.0 — 2026-09-29

### Added
- **Daycare** (with Cobblemon): leave up to two Pokémon with a Rancher; they gain experience while they're there, and
  you collect them for an emerald plus one per level gained.
- **Village Ledger** (a book and an emerald): bind it at the Village Hall, then open the hall's screen from anywhere
  nearby.
- **Chapel** (a decoration: nave, pews, a bell tower with a slate spire), where the village's weddings are held.
- **Couples**: villagers court and marry (a wedding at the bell with fireworks lifts the whole village's mood), are
  happier together, have the village's babies first, and mourn a partner who dies.

## 0.131.0 — 2026-09-29

### Added
- **Bandit camps**: bandits make camp near villages of Village rank or more and raid them at night until their chief
  falls. Break the camp up for its loot.
- **Festivals**: every eight days (or sooner, with a cake at the hall) the village gathers at the bell after work for a
  feast, music and fireworks; moods lift, and players are Heroes of the Village for the evening.
- **Chatter**: villagers off work near you now and then say something over their heads about their mood or the
  village's news (festivals, bandits, raids), or just hello.
- **Better-looking builds**, reviewed with a new building skill: the **Healing Center** is now a white clinic with dark
  posts, a red roof and a gabled porch (II: a ward behind under a lower roof; III: the berry garden), the **Supply Shop**
  a timber shop with its gable to the street, a blue-slate roof and a striped awning over display windows (II: the
  shopkeeper's house behind; III: the post office), the **Barracks** got a gabled stone porch, buttresses and lintels,
  the houses we add to generated villages got roofs that contrast with their walls, chimneys, door hoods and flower
  boxes, the **Flower Shop** a dark oak roof, the **Apiary Garden**'s honey shed a gable roof, and the **Research Lab**
  a copper roof (it greens over the years; builders count greened or waxed copper as built).

### Fixed
- Floating bits found by the new build checks: the lantern beams in village houses, the Starter Cottage II attic, the
  Lookout Tower III guardhouse and the Storehouse III now run wall to wall; the Lookout Tower II's lanterns hang on
  chains; the Flower Shop's front pots stand on pedestals; the Supply Shop III's doorway no longer takes the ladder's
  wall.

## 0.130.0 — 2026-09-29

### Added
- **Seven more research topics** for the scholars: Logistics (porters carry more), Craftsmanship (crafters faster),
  Medicine (less illness), Fortification (guards turn aside blows), Commerce (more market traders, cheaper mercenaries),
  Expeditions (explorers and netherworkers back sooner) and Green Thumb (more bone meal from compost).
- **Patrol Map**: mark up to eight points and hand it to a guard; by day they walk your route.
- **Rally Banner**: enlist up to twelve guards, raise the banner, and they follow you and fight at your side.
- New builds: the **Compost Yard** and the **Sifting Shed**, each with an upgrade.

## 0.129.0 — 2026-09-29

### Added
- **Stock orders** at the Storehouse: "keep 64 stone bricks" — the village's crafters make what runs short from what's in
  the store.
- **Repairs**: idle builders put back the blocks missing from the buildings they finished (only holes are filled).
- Masons are the village's **kiln**: with a furnace by the stonecutter they fire stone, smooth stone, terracotta,
  bricks and nether bricks for the builders.

## 0.128.0 — 2026-09-29

### Added
- **Diet**: villagers pick meals they haven't had lately; a varied diet lifts their mood, the same food every day
  lowers it. The hall shows how many kinds of meal the store has.
- **Mercenaries**: hire three fighters in iron at the Village Hall for 12 emeralds; they fight for the village until
  dawn.
- **Composters** (new job, the Compost Bin): the village's scraps and rotten flesh become bone meal. Villages sometimes
  grow a Compost Yard.

## 0.127.0 — 2026-09-29

### Added
- **Scan Tool**: mark two corners of something you've built and save it as a blueprint (for a Blank Blueprint) — it's
  in the Blueprint Table for everyone. Name it by renaming the tool in an anvil.
- **Mirror** a blueprint: a new button on the style screen builds it flipped left to right.
- **Drop Box** (barrel + hopper): anything put in goes to the storehouse with the next porter.

## 0.126.0 — 2026-09-29

### Added
- **Netherworkers** (new job, the Nether Brazier): with food in the chests and a Nether portal nearby they go through
  it on expeditions and come back with the Nether's goods — netherrack, quartz, glowstone, nether wart and more; with
  a pickaxe, an axe, a sword and armor they bring back more (a diamond pickaxe now and then turns up ancient debris),
  and with Cobblemon the odd Fire or Dusk Stone.
- **Nether Gate** blueprint (and its upgrade): an obsidian portal frame in a blackstone arch — the builder lights it when
  it's done if there's a flint and steel in the chests. II adds a gatehouse roof, a storehouse and a nether wart garden.

## 0.125.0 — 2026-09-29

### Added
- **Tinkerers** (new job, the Tinker's Bench): they make the redstone and iron parts builders are waiting for — pistons,
  rails, hoppers, repeaters, lanterns, iron bars, doors, copper blocks — firing raw ore into ingots first when that's all
  there is (a coal for every 8 ore). Between jobs they mend the village's hurt iron golems with iron ingots.
- **Tinker's Workshop** blueprint (and its upgrade): a brick workshop with a forge, a smoking chimney and open trusses;
  II adds a storage loft, a cart track and a lightning rod.
- Villages now grow Tinker's Shops and Sifting Sheds too.

## 0.124.0 — 2026-09-29

### Added
- **Sifters** (new job, the Sieve): gravel, sand, dirt and soul sand shaken through for flint, seeds, nuggets, clay,
  quartz and the odd gem — with Cobblemon, now and then an evolution stone.

## 0.123.0 — 2026-09-29

### Added
- **Moods**: every grown villager in a village with a Village Hall has a mood from their own day (fed, a bed, a job,
  health, decorations near home, company), shown with its reasons on the hall's list. Unhappy villagers work slower,
  happy ones faster. (`villagerMoods` in the config.)

## 0.122.0 — 2026-09-29

### Added
- **Families**: babies remember their parents (the hall's list says whose child they are); grown up and without a job,
  they take up a parent's trade when the village has a free workstation for it. The chronicle notes it.

## 0.121.0 — 2026-09-29

### Added
- **Village ranks**: Hamlet, Village, Town and City, from villagers, finished buildings and research. Each rank pays
  (better quest rewards, more caravan routes, more market traders, room to grow), and a rank up is celebrated with
  fireworks.

## 0.120.0 — 2026-09-29

### Added
- **Kinds of guard**: a guard with a shield is a **Knight** and blocks blows from in front; one holding a healing
  potion is a **Medic** and gives potions to the wounded; with a bow, an **Archer** as before. Sneak-right-click a guard
  with one of these to hand it over.
- The **Barracks** (two Guard Posts, bunks, training dummies) and the **Barracks II** (two more).

## 0.119.0 — 2026-09-29

### Added
- **Walls and gates** in the Blueprint Table: the Palisade, the Palisade Gate, the Stone Wall, the Wall Tower and the
  Gatehouse. In a village with a Village Hall and a guard, the gates are shut at nightfall and opened in the morning.

## 0.118.0 — 2026-09-29

### Added
- **Village raids**: at night, monsters may raid a village with a Village Hall and 8 or more villagers — more often and
  in greater numbers the bigger it is, some in iron when it has many guards. The bell rings, the guards defend the whole
  village, and the chronicle remembers it. (`villageRaids` in the config.)
- In a pillager raid, guards no longer hide: they patrol and fight across the raid's area.

## 0.117.0 — 2026-09-29

### Added
- **Caravans**: villages with a Village Hall can supply each other. On the hall's Trade Routes page, pick villages to
  send caravans to; once a day a caravan takes them what their workers are waiting for from your Storehouses.

## 0.116.0 — 2026-09-29

### Added
- **Market days**: once a week a village with a Village Hall and a Market Square holds a market — two travelling
  traders come to the square for the day, each with a blueprint to sell too. (`marketDays` in the config.)

## 0.115.0 — 2026-09-29

### Added
- The **Settler's Wagon**: right-click open ground and two settlers make camp — a covered wagon with a chest of
  supplies (and a Village Hall), a Builder's Bench, a campfire and two bedrolls. One settler is your builder from the
  start. A village can begin anywhere.

### Fixed
- The blueprints in village builder's workshops' chests showed old sizes before they were placed.

## 0.114.0 — 2026-09-29

### Added
- **Give a villager a job from the Village Hall**: click a jobless villager on the hall's list to see the village's free
  workstations, and click one to give them that job.
- **Call everyone home**: the bell on the hall's screen brings back the villagers who live or work in the village but
  wandered off.

## 0.113.0 — 2026-09-29

### Added
- **The village chronicle**: a page of the Village Hall (the book in the middle) with what happened in the village, day
  by day — births, deaths, revivals, travellers, finished buildings, quests, research and new Masters.

## 0.112.0 — 2026-09-29

### Added
- **Sickness**: in a village with a Village Hall villagers fall ill now and then (more often hungry or without a bed);
  the ill work at half pace until they get well after three days, or a **Nurse** cures them with honey, milk or a
  healing potion from her chest. (`villagerSickness` in the config.)

## 0.111.0 — 2026-09-29

### Added
- **Names**: villagers in a village with a Village Hall get a first name of their own.
- **Traits**: every villager has one or two — Diligent, Lazy, Nimble, Clever, Strong, Cheerful, Glutton or Frugal —
  that change how fast they work and walk, how quickly they learn, how hard they hit, how often they eat and how happy
  the village is. The hall lists them. (`villagerNames`, `villagerTraits` in the config.)

## 0.110.0 — 2026-09-29

### Added
- **Styles**: sneak-right-click the air with a blueprint to build it in **Stonework**, **Sandstone**, **Dark Oak**,
  **Cherry** or (with Cobblemon) **Apricorn** wood instead of the timber it was drawn in — any blueprint, your own too.
  Materials, preview and upgrades follow the style. Data packs can add styles (`blueprint_styles/*.json`).

## 0.109.0 — 2026-09-29

### Added
- The **Stone House** in three tiers (Blueprint Table): a stone cottage with two beds, II a timber-framed upper storey
  with two more, III a stone wing at the back with two more.

## 0.108.0 — 2026-09-29

### Added
- **Decorations** in the Blueprint Table: the **Well** (and Well II, with a roof), the **Street Lamp**, the **Park
  Bench**, the **Fountain**, the **Gazebo** and the **Market Square** (with the village bell, where villagers meet).
- Decorations near a Village Hall add to the village's **beauty**: each point is 1% more wellbeing, up to 10% (shown on
  the hall's Wellbeing icon).

## 0.107.0 — 2026-09-29

### Added
- Six new buildings with upgrades, in the Blueprint Table: the **Schoolhouse**, the **Library**, the **Ranch**, the
  **Apiary Garden**, the **Flower Shop** and the **Graveyard** — each with its job's workstation, so building one brings
  a teacher, a scholar, a rancher, a beekeeper, a florist or an undertaker.

## 0.106.0 — 2026-09-29

### Added
- Villages now grow a **flower shop** (florist), a **ranch house** (rancher), a **schoolhouse** (teacher), an **inn**
  (innkeeper) and a **mortuary** (undertaker) now and then, in every village style, each with its job block, a chest
  of starting supplies and a bed.

## 0.105.0 — 2026-09-29

### Added
- **Paths**: a builder who finishes a building lays a dirt path from its door to the village's bell or Village Hall,
  round water, trees and other buildings (`builderPaths` in the config turns it off).

## 0.104.0 — 2026-09-29

### Added
- **Scholars and research** (new job, Scholar's Desk): a research tree kept in the Village Hall, paid for in paper,
  books and emeralds — Swift Hands (every job faster), Hearth (wellbeing), Drill (guards hit harder), Kinship (more
  Pokémon partners), Lore (schooled children start as Journeymen) and Architecture, which draws up the **Town Hall**
  blueprint: a stone hall with a bell tower and a Village Hall inside. Sneak-right-click a scholar to choose.

## 0.103.0 — 2026-09-29

### Added
- **Village quests**: each morning the Village Hall puts up a quest for players — bring what a worker is waiting for
  (or food, or something the village can use), clear out monsters round the village, or beat a village trainer (with
  Cobblemon). Hand things in from the hall's new Quests page; the reward is paid in emeralds or CobbleDollars.

## 0.102.0 — 2026-09-29

### Added
- **Graves and Undertakers**: a grown villager with a job or a name who dies leaves a grave. An Undertaker (new job,
  Undertaker's Table) with a golden apple, a healing potion or a totem of undying brings them back as they were — job,
  level, trades and name. The Village Hall counts the graves.

## 0.101.0 — 2026-09-29

### Added
- **Innkeepers** (new job, Inn Counter; the Inn blueprint has one): each morning, while there's a free bed, a traveller
  comes to stay. Travellers know a trade already (Apprentice, Journeyman, now and then Expert): right-click one to hire
  them for emeralds or CobbleDollars, and they join the village and start their first job at that level. Unhired
  travellers move on after two days.

## 0.100.0 — 2026-09-29

### Added
- **Teachers** (new job, Teacher's Desk): in the day they call the village's children over for lessons. A child who's
  been to school starts their first job as an Apprentice, with the Novice and Apprentice trades.

## 0.99.0 — 2026-09-29

### Added
- **Villages grow**: with a Village Hall, when there's a free bed, 16 meals in the store and the village is doing well,
  two villagers have a baby (at most one a day, up to 40 villagers — `villageGrowthCap`). The hall says what the
  village still needs to grow.
- **Terrace** and **Inn** blueprints (with upgrades): row houses with four beds (six in the Terrace II) and an inn with
  a tavern and six beds (the Inn II adds a stable with a Feed Trough). In the Blueprint Table.

### Changed
- Builders with no way to walk to where they need to be (upstairs by a ladder, behind a party wall) hop there after
  a moment instead of trying for five seconds: builds with upper floors go a lot quicker.

## 0.98.0 — 2026-09-29

### Added
- **Village needs**: in a village with a Village Hall, grown villagers eat once a day from the store (the chef's
  cooking first), like a bed of their own, guards and light. The hall shows the village's wellbeing, and it sets the
  pace of all the work there — up to 25% faster when everyone's fed, housed and safe, up to 20% slower when they're
  hungry. The hall's list says who's hungry or has no bed.

## 0.97.0 — 2026-09-29

### Added
- **Village Hall** (new block): right-click it for the village at a glance — villagers, beds, food in store, guards,
  requests and buildings going up, then everyone who lives there with their job, level, what they're doing and waiting
  for and where they are. Click a villager to make them glow. The village gets a made-up name, or use a Name Tag on the
  hall to name it.

## 0.96.0 — 2026-09-29

### Added
- **Training Dummy** (hay bale, sticks and wool): guards spar with one near their Guard Post between fights and gain
  experience from it, up to Expert. Players can give it a whack too.

## 0.95.0 — 2026-09-29

### Added
- **Ranchers** (new job, Feed Trough workstation): they break in the wild horses, donkeys and llamas round the trough,
  put the saddles, horse armor and carpets from the chests on the tamed ones, and breed them (golden carrots for
  horses, hay for llamas, cactus for camels). With Cobblemon they groom the pastured Pokémon nearby once a day, raising
  their friendship — more with a berry from the chests as a treat.

## 0.94.0 — 2026-09-29

### Added
- **Florists** (new job, Flower Stand workstation): they grow the biome's flowers with bone meal on the grass round
  their stand (and more tall flowers from tall flowers), pick them into the chests for the dyer and the builders, and
  fill the empty flower pots within 24 blocks.

## 0.93.0 — 2026-09-29

### Added
- **Beekeepers** (new job, Apiary workstation): they harvest the beehives within 16 blocks — honey bottles with glass
  bottles from the chests, honeycomb with shears — keep the hives in flowers and breed bees up to three a hive. A
  campfire under a hive keeps the bees calm.

## 0.92.0 — 2026-09-29

### Added
- **Farmers grow plantation crops**: sugar cane, cactus, bamboo and kelp in their fields are cut down to the bottom
  block and planted — sugar cane and cactus on sand, more of whatever grows next to a bare spot — and a farmer takes
  every kind of seed in the chests along, so one field can mix wheat and sugar cane.
- **Farmers compost** the seeds piling up in their chests (over 64 of a kind) into bone meal for the field.

## 0.91.0 — 2026-09-29

### Added
- **Butchers tend pastured Pokémon** (with Cobblemon): with a bucket, bottle, brush or bone meal in the chest by their
  smoker they do what a player can do to the Pokémon in a Pasture Block nearby — milk a Miltank, fill a bucket with lava
  from a Slugma, brush birds for feathers and Cottonee for string, bone-meal a Cacnea for cactus — all 139 of
  Cobblemon's item interactions, read from its data.

## 0.90.0 — 2026-09-29

### Added
- **Cartographers go exploring**: with food from the chests by their cartography table (and a sword or axe if there is
  one) they set out on expeditions around the village and bring back what the land has — flint, clay, nuggets, the odd
  emerald or diamond, each biome's own finds, meat and monster drops when armed, and with Cobblemon apricorns, berries,
  evolution stones and fossils. Every third expedition, with an empty map in the chests, they draw an Explorer's Map
  to a place nearby. Hire one with a compass (sneak-right-click); `explorerRange` in the config.

## 0.89.0 — 2026-09-29

### Changed
- **Every starter build has a new look**, all 22 blueprints and tiers: a timber-framed cottage with shuttered windows,
  flower boxes and a smoking chimney (its upgrades add a jettied upper storey and a kitchen wing), a market stall under
  a striped awning, a stone watchtower with a crenellated platform and a steep spire, a Healing Center and a Supply
  Shop under stepped concrete roofs with signs over their doors, an open-fronted timber storehouse, a walled berry
  garden and a stone research lab with a skylight. Buildings already standing stay as they are; their upgrades now
  build onto the new designs.
- **Village houses** (workshops, guard houses, clinics, post offices...) are timber-framed now too, with an
  overhanging roof, shutters and a lantern, in each village type's own materials (flat-roofed in the desert).
- Builders can take down a campfire or a bell when an upgrade needs the spot (a chimney growing taller).

## 0.88.0 — 2026-09-29

### Added
- **Masons crush and fire for the builders**: cobblestone into gravel, gravel into sand, and sand into glass when
  there's a furnace by their stonecutter.

## 0.87.0 — 2026-09-29

### Added
- **Librarians enchant the village's gear**: with an Enchanting Table near their lectern and lapis in their chest, they
  enchant the guards' weapons, armor and bows and the other workers' tools, stronger as they level up. They also make
  books, bookshelves and lecterns for the builders. Sneak-right-click with lapis to hire one.

### Fixed
- Tests that turn village sharing on now turn it off again even when they fail (one failure could fail the next
  batches).

## 0.86.0 — 2026-09-29

### Added
- **Clerics brew potions for the guards**: healing, regeneration and strength from the chest by their brewing stand
  (they fill glass bottles at water or a cauldron), and take them to guards who are short. Sneak-right-click with a
  glass bottle to hire one.
- **Guards drink potions**: a guard carries up to three, drinks healing or regeneration below half health and
  strength as a fight starts.

### Fixed
- Brewing stands, jukeboxes, lecterns and crafters near a workstation are no longer treated as chests (a builder
  could take the potions out of a brewing stand).
- A village farmer test that could fail on an unlucky harvest.

## 0.85.0 — 2026-09-28

### Added
- **Leatherworkers dye for the builders**: coloured wool, carpet, glass, terracotta, candles, beds and dyes a builder
  is waiting for are made from what the builder can get at, and concrete powder is hardened into concrete in the
  cauldron.

## 0.84.0 — 2026-09-28

### Added
- **Shepherds shear and breed the sheep** around their loom (shears and wheat in the chest), and with Cobblemon shear
  pastured Wooloo and Dubwool. Sneak-right-click with shears to hire one.
- **Butchers keep the herd**: cows, pigs, chickens and rabbits around their smoker are bred, eggs picked up and cows
  milked into the buckets in the chest; chefs use the milk and eggs. A butcher hired with a lead (sneak-right-click)
  also keeps each kind at 10 grown animals, taking the rest for meat — never babies, named or leashed ones.

## 0.83.0 — 2026-09-28

### Added
- **Fletchers make the guards' bows and arrows**: a guard without a bow gets a crossbow (with iron) or a bow; a guard
  short of special arrows gets spectral ones made from glowstone.
- **Guards shoot special arrows**: spectral and tipped arrows in a guard's chests go into their quiver (up to 16) and
  are shot first.

## 0.82.0 — 2026-09-28

### Added
- **Weaponsmiths mend worn gear**: worn tools, weapons and armor in the chests by their grindstone, by the guards'
  posts and at the other workers are mended with ingots (or planks, diamonds, leather...) from the village's chests —
  a quarter of the durability per ingot, like an anvil — and put back where they were.
- **Weaponsmiths make swords**: a guard with no weapon (and none in their chests) gets an iron sword, or a stone one.

## 0.81.0 — 2026-09-28

### Added
- **Toolsmiths make the workers' tools**: when a miner needs a pickaxe, a lumberjack an axe or a fisherman a rod, the
  village's toolsmith makes one (iron from the storehouse, else stone; diamond only from their own chest) and takes it
  to that worker's chests. Sneak-right-click a toolsmith with an iron ingot to hire them.

### Changed
- Hiring an armorer is now a **sneak**-right-click with coal, so right-clicking with coal still opens their trades.

## 0.80.0 — 2026-09-28

### Added
- **Armorers smelt for the village**: an Armorer with a chest by their blast furnace smelts the ore in it with coal or
  charcoal, and fetches more ore (and fuel) from the storehouse and from miners who don't smelt their own. A porter
  takes the ingots to the storehouse.
- **Armorers make the guards' armor**: a guard with nothing in an armor slot gets an iron piece made from the
  armorer's iron and brought to their Guard Post's chest. Right-click an armorer with coal to hire them.

## 0.79.0 — 2026-09-28

### Added
- **Trainers send their Pokémon out** beside them in battle (and call them back afterwards), like players do.
- **Mega Evolution** (with Mega Showdown, which is in the Cobbleverse pack): every Master trainer's team has an ace
  holding its Mega Stone, and the trainer Mega Evolves it in battle.

## 0.78.0 — 2026-09-28

### Added
- **Guards fight beside their Pokémon** (with Cobblemon): Fighting and Dragon types kept in a Pasture Block near the
  Guard Post follow up every hit the guard lands with a move of their own, animation and all. Stronger Pokémon hit
  harder; monsters turn on the guard, not the Pokémon.

## 0.77.0 — 2026-09-28

### Added
- **Berry Farm** blueprint (and **Berry Farm II**): a fenced garden of sweet berry bushes with a Fruit Basket, then a
  pergola with glow berries. Orchard keepers sell it at Expert level; it's in the Blueprint Table too.
- **Research Lab** blueprint (and **Research Lab II**): a stone lab with a Fossil Lab and a fossil on show, then a
  museum hall with a big skeleton. Fossil scientists sell it at Journeyman level.
- **Glow berries in orchards**: orchard keepers hang glow berries from any solid ceiling in their orchard.

## 0.76.0 — 2026-09-28

### Added
- **Strip mines at any height**: hold a Quarry Marker and type `/workplace strip <height>` (the marker still offers
  Y=16, -16 and -53 by sneak-right-clicking). Heights in between say what ores to expect there.
- **Lit shafts**: every eight blocks down a ladder shaft, the miner sets a torch in a niche across from the ladders.
- **Lumberjacks strip logs** for a builder in the village who's waiting for stripped logs or wood, from the logs in
  their chests, and **burn charcoal** in a furnace by the Chopping Block (up to 32 in the chests; a little coal starts
  it). With no trees to fell, they see to both.

## 0.75.0 — 2026-09-28

### Added
- **Fossil Scientists** (with Cobblemon): a new job at the Fossil Lab (glass, a brush and glass over three smooth
  stone). Hand one a fossil (a Galar fossil's two halves, one in each hand) and 8 emeralds or 800 CobbleDollars, and they
  revive it while they work; the Pokémon joins your party (or PC) when it's done, or the next time you're online. It's
  Cobblemon's own revival. Villages with Cobblemon sometimes grow a fossil lab.

## 0.74.0 — 2026-09-28

### Added
- **Chefs**: a new job at the Kitchen Stove (five iron ingots round a smoker, over three cobblestone). The chef cooks
  whatever the chests nearby have the makings for — bread, cookies, pumpkin pie, cake, stews, steak, fish, baked
  potatoes — and with Cobblemon its Campfire Pot dishes: Poké Bait, Poké Snacks, Poké Cakes, candied apples and
  Aprijuice. They stop at 16 of each dish, and take the makings from the village's storehouse too. Villages sometimes
  grow a kitchen.

## 0.73.1 — 2026-09-28

### Changed
- **Workers are much lighter on the server.** A worker whose path to somewhere failed used to ask for a new one every
  tick; now it waits a second before trying again (and hops when stuck, as before). Lumberjacks look for trees without
  reading every block around them, and don't work out the whole tree again every tick while chopping. In the full
  Cobbleverse pack, 80 busy workers now take about 7 ms of the 50 ms each tick has, down from 10 ms; lumberjacks cost a
  seventh of what they did.

## 0.73.0 — 2026-09-28

### Added
- **Carpenters**: a new job at the Carpenter's Bench (a stick, an iron ingot and a stick over planks, a crafting table and
  planks). When a builder nearby is waiting for something that can be crafted from what they can get at — stairs,
  slabs, doors, fences, planks from logs, sticks, glass panes... — the carpenter fetches the ingredients, makes it and
  brings it to the builder's chests. They never use what the rest of the build still needs. Villages sometimes grow a
  carpenter's workshop.
- **Masons cut stone for the builders**: a vanilla Mason (at a stonecutter) does the same with the stonecutter's recipes,
  cutting stone bricks, stairs, slabs and walls from the builder's stone, and goes about their usual day in between.

## 0.72.0 — 2026-09-28

### Added
- **The requests board**: right-click a Storehouse to see what the workers nearby are waiting for — a builder's missing
  materials, a miner's pickaxe or ladders, a lumberjack's axe, a farmer's seeds, a fisherman's rod — with how much of it
  the storehouse already holds. Click one to hand over yours: it goes straight into that worker's chests.
- **Lumberjacks fell what's wanted first**: when a builder nearby is waiting for a kind of wood (logs, planks, stairs,
  doors...), their village's lumberjacks fell that kind of tree before any other.

## 0.71.0 — 2026-09-28

### Added
- **Porters and the Storehouse**: a new job. Craft a Storehouse (three planks over a barrel, a chest and a barrel), put
  chests around it, and a villager becomes a Porter who walks round the village's workers and carries what they make —
  ore, logs, crops, fish, fruit — to the storehouse chests, leaving what each job needs (tools, torches, seeds, saplings,
  some stone). Builders and everyone else find things in the storehouse. A storehouse you place is yours: its porter only
  carries for your workers and your friends'.
- **Storehouse builds**: the Storehouse Shed blueprint (sold by porters, and in the Blueprint Table) with two upgrades,
  Storehouse II (a second bay) and Storehouse III (a stone warehouse behind); villages of every type sometimes grow a
  small storehouse with a porter.

### Fixed
- A double chest's items were counted twice by the workers (a builder could think it had enough when it didn't).

## 0.70.0 — 2026-09-28

### Added
- **Villages work together**: workers whose workstations are within 48 blocks of each other share their chests. A
  builder out of stone walks over and takes it from the miner's chests; a miner, lumberjack, farmer or fisherman whose
  tool broke takes a spare from another worker's chests, and farmers take seeds the same way. Only workers who answer to
  the same people share (hired by you or your friends; village workers nobody has hired with each other). The distance
  is `villageRadius` in `config/aliveworkplace.json` (0 turns it off).

## 0.69.0 — 2026-09-28

### Added
- **Village farmers look after their farm by themselves**: put a chest near a village farmer's composter and they take
  on the farm there — harvest, replant, till — and fill the chest. They keep some food (wheat baked into bread) to share
  with the other villagers, so the village still grows. Stop one from their status and they leave it alone for good;
  the gamerule `workplaceVillageFarms` turns it off.

## 0.68.1 — 2026-09-28

### Fixed
- A Master trainer's Pokémon that can learn hardly any attacks on its side (say a physical attacker with mostly
  special moves) now gets its best attacks of either kind; a Smeargle keeps Sketch. (A test that picked random teams
  caught a Smeargle and failed one of 0.68.0's builds; it now checks 40 teams every time.)

### Changed
- The README starts with a table of every job: its workstation, what goes in the chests and what to hand the villager.

## 0.68.0 — 2026-09-28

### Added
- **Fishermen cast a real bobber**: it floats on the water on the end of a line from their rod, dips under when a fish
  bites, and comes back in with the catch.

## 0.67.0 — 2026-09-28

### Added
- **Master trainers' Pokémon have real movesets** (with Cobblemon): four strong attacks that fit their nature and held
  item, picked from everything they can learn — their own type first, then moves covering other types. No more
  level-up leftovers on a level 100 team.

## 0.66.0 — 2026-09-28

### Added
- **Orchard keepers till the ground for berries** (with Cobblemon): put a hoe in the chests by the Fruit Basket and they
  till grass and dirt in their orchard into farmland to plant Cobblemon berries — no need to prepare the farmland first.

## 0.65.1 — 2026-09-28

### Fixed
- A test that grew a random huge fungus sometimes failed, which stopped 0.64.0 from being released on its own: test trees
  now grow the same shape every run. **0.64.0's Fire-type partners** (a Fire-type Pokémon pastured near a Miner's Bench
  or a fisherman's barrel smelts 8 of the ores or fish in each furnace there whenever the worker tends it) are in
  0.65.0 and later.

## 0.65.0 — 2026-09-28

### Added
- **Third tiers for every starter build**, each bringing a new villager: Market Stall III (a storeroom shed with chests
  behind the stalls), Lookout Tower III (a guardhouse with a second Guard Post and two bunks), Healing Center III (a
  fenced berry garden with a Fruit Basket for an orchard keeper) and Supply Shop III (a post office annex with a Postal
  Desk). Builders who finish a tier II sell the tier III.

## 0.64.0 — 2026-09-28

### Added
- **Fire-type partners at the furnaces** (with Cobblemon): a Fire-type Pokémon pastured near a Miner's Bench or a
  fisherman's barrel smelts 8 of the ores (or fish) in each furnace there every time the worker tends it — on the
  spot, no coal, straight into the chests. Up to three partners.

## 0.63.0 — 2026-09-28

### Added
- **Price Tags without an anvil**: right-click a Price Tag to set its price with +/− buttons (1, 10, 100, 1000). Renaming
  it in an anvil still works.

## 0.62.0 — 2026-09-28

### Added
- **The post office**: mail for a player with no mailbox waits at the post office — right-click any Postal Desk to pick
  up every parcel handed in for you (night mail on its way to your mailbox too). At dawn you're told when some are
  waiting, and `/workplace mail` says where they are. Before, such parcels were stuck for good.

## 0.61.0 — 2026-09-28

### Added
- **Village farms**: give a farmer a blank Field Marker (no corners marked) and they take on the farm by their
  composter — the nearest farmland within 16 blocks and everything joined to it, across the water channels too.

## 0.60.0 — 2026-09-28

### Added
- **Lumberjacks fell mangroves**, down to the roots (the roots stay), and plant a propagule close by in the water over
  the mud. **Azalea trees** get an azalea bush back instead of an oak sapling; **cherry trees** a cherry sapling.
- **Lumberjacks use bone meal**: with bone meal in the chests, whenever there's no grown tree to fell they give it to
  the saplings on their tree farm and the ones they replanted until they grow.

### Changed
- Workers (builders, miners, lumberjacks and the rest) can now stand in shallow water and on waterlogged blocks, so
  they get to work in swamps and streams. A lumberjack chops a tree from wherever one of its logs can be reached, and
  skips trees nobody can get to instead of trying the same one forever.

## 0.59.0 — 2026-09-28

### Added
- **Strip mines down a ladder shaft**: after "strip mine" the Quarry Marker now offers strip mines at Y=16 (iron;
  ancient debris in the Nether), Y=-16 (redstone, gold, lapis) and Y=-53 (diamonds). Mark the corners on the ground and
  the miner digs a ladder shaft down from the corner nearest their bench, digs the tunnels there, and ladders the
  shaft all the way up (ladders from the chests). Water and lava beside the shaft are sealed off, and caves in its way
  are bridged so the miner never falls.
- Strip mines can be up to 64 blocks long (still up to 32 wide).

## 0.58.0 — 2026-09-28

### Added
- **Starter Cottage III**: the third tier of the cottage adds a kitchen wing on the east side (a smoker, a barrel, a
  table and its own door) with a roof terrace on top, reached through a new door from the upper floor. Builders who
  finish a Starter Cottage II now sell it.

### Fixed
- An upgrade that puts a door (or a bed) where there was a wall: the builder now takes down the bit of wall where the
  door's top half goes, instead of skipping the door.

## 0.57.0 — 2026-09-28

### Added
- **Guards show their armor**: the helmet, chestplate, leggings and boots a guard wears are drawn on them, fitted to
  the villager's shape (dyed leather and enchantment shine included). Any villager wearing armor shows it the same way.

## 0.56.0 — 2026-09-28

### Added
- **Air mail**: with a Flying-type Pokémon in a pasture near the Postal Desk, parcels for mailboxes outside the
  postman's round are delivered as soon as they're handed in, instead of with the next dawn's mail.

## 0.55.0 — 2026-09-28

### Added
- **Ball Smith orders**: sneak-right-click a Ball Smith with an empty hand to choose which balls they make. With
  nothing chosen they make whatever the chests have the makings for, as before.

## 0.54.0 — 2026-09-28

### Added
- **Orchard Keepers plant orchards**: give one a Field Marker with an area marked and they plant it from the chests
  by the Fruit Basket — sweet berry bushes, and with Cobblemon berries (on farmland) and apricorn trees — then pick
  what grows there. Sneak-right-click the keeper to see the orchard or stop it.

## 0.53.0 — 2026-09-28

### Added
- **Price Tags** for shops: craft them from paper, a gold nugget and string, rename one in an anvil to a price
  (`250`) and put it in a Shop Counter's price row. The column then costs exactly that many CobbleDollars; without
  CobbleDollars it's paid in emeralds (100 to the emerald, rounded up).

## 0.52.0 — 2026-09-28

### Added
- **Guards use crossbows**: a crossbow in the guard's chest beats a bow. Bolts fly faster and straighter and hit
  harder; the guard reloads a little slower than with a bow.

## 0.51.0 — 2026-09-28

### Added
- **Letters**: the mailbox screen has a *Letter* line. Whatever you write there goes with the parcel as a letter (a
  book the recipient can read), or on its own if you're not sending anything else.
- **Mail tracking**: `/workplace mail` (or **[Track]** after posting) lists the parcels on their way to and from you
  and where they are: waiting for a postman, in a postman's bag, or in the night mail.

## 0.50.0 — 2026-09-28

### Added
- **Fishers smoke their catch**: put a smoker (or furnace) near the fisherman's barrel and coal or charcoal in the
  barrel. At every drop-off the raw cod and salmon go in and the cooked fish come out into the barrel.

## 0.49.0 — 2026-09-28

### Added
- **Farmers use bone meal**: put bone meal in the chest by the composter and, once the field is sown and nothing is
  ripe, the farmer uses it on the growing crops (never on grass).
- **Farmers pick sweet berries and cocoa** (and glow berries) growing in their field, leaving the plants to grow again.

## 0.48.0 — 2026-09-28

### Added
- **Tree farms**: give a lumberjack a Field Marker with an area marked (within 48 blocks of their Chopping Block) and
  they keep it planted with saplings from the chests, in a grid three blocks apart, and fell the trees that grow
  there. Dark oak goes in as 2 × 2 squares. Sneak-right-click the lumberjack to see the farm or stop it.
- Lumberjacks fell **huge crimson and warped fungi** standing on nylium, and plant a fungus back.

## 0.47.0 — 2026-09-28

### Added
- **Strip mines**: sneak-right-click the air with a Quarry Marker until it says *Strip mine*, then mark the corners at
  head height. The miner digs 2-high tunnels with 2 blocks of rock between them (and one across the end nearest the
  bench), and digs out any ore in that rock: all the ore for about a third of the digging.

### Changed
- A villager who becomes a guard gets the extra health straight away (40), instead of starting at 20 and healing up.

## 0.46.0 — 2026-09-28

### Added
- **Miners smelt their ore**: put a furnace or blast furnace near the Miner's Bench and coal or charcoal in the
  chests. At every drop-off the miner takes the finished ingots out into the chests, loads raw ores and ore blocks
  in, and tops up the coal. Anything you put in a furnace yourself is left alone.

### Changed
- Furnaces, blast furnaces and smokers near a workstation no longer count as supply chests (a builder or miner could
  put leftovers into their input slot).

## 0.45.1 — 2026-09-28

The first release with the 0.44.0 and 0.45.0 changes below (a flaky test stopped those builds before they were released).

### Fixed
- Quarries that the bug fixed in 0.45.0 had already stretched over the ground around the Miner's Bench now stop by
  themselves, before the miner digs any more: the miner hands back a blank Quarry Marker and tells you (if you're
  online) to mark the corners again. (The advice in 0.45.0 to reuse the returned marker was wrong: it carried the
  stretched corners.)

## 0.45.0 — 2026-09-28

### Added
- **Stairs out of the pit**: miners leave a staircase spiralling down the walls of quarries at least 3 × 3 and 3 deep
  (one step per layer, starting at the corner nearest the bench), so you and they can walk out. Sand and gravel steps
  are swapped for cobblestone and gaps are filled in. Quarries started before this update keep digging without stairs.

### Fixed
- **Miners no longer dig up the ground around their Miner's Bench.** Since 0.18.0, keeping a quarry loaded while its
  owner was online stretched the quarry to take in everything within 8 blocks of the bench (and down to the bench's
  level), so the miner dug that too. Quarries started from now on stay the size you marked; a quarry already under
  way may have been stretched already: sneak-right-click the miner, click **[Stop this quarry]**, and give them the
  marker they hand back (it still has your corners).

## 0.44.0 — 2026-09-28

### Added
- **An upgrade for every starter build**: **Market Stall II** (a second stall with a Shop Counter), **Lookout Tower II**
  (a Guard Post at the foot of the ladder, a bell and a pointed roof), **Healing Center II** (a ward with four beds)
  and **Supply Shop II** (a storeroom and the shopkeeper's bedroom upstairs).
- **Builders sell upgrades**: once a builder finishes a build that has an upgrade, they add its blueprint to their
  trades and tell you. Works for your own blueprints too (`my_house` → `my_house_2`).

## 0.43.0 — 2026-09-28

### Added
- **Upgrades**: the new **Starter Cottage II** blueprint adds a second storey to a finished Starter Cottage. Right-click
  the cottage with it and it lines up exactly; the builder only takes the old roof off and builds what's new. Your
  own blueprints can have upgrades too: name them `my_house_2`, `my_house_3`…

## 0.42.0 — 2026-09-28

### Added
- **Server config file** `config/aliveworkplace.json`: how far supply chests, builds, guards, lumberjacks, orchard
  keepers, fishers, Pokémon partners and postmen reach, and how many CobbleDollars an emerald price is worth.

### Fixed
- A guard with a bow no longer ignores a creeper that's out of bow range: they walk closer (keeping their distance)
  and shoot.

## 0.41.0 — 2026-09-28

### Added
- **Leave the ground as it is, per build**: right-click the air with a blueprint to switch off levelling around that
  build (for houses meant to sit in a hillside); right-click again to switch it back. The tooltip shows it.

## 0.40.0 — 2026-09-28

### Changed
- **Stronger trainers bring trained teams**: from Journeyman their Pokémon have better IVs, and Experts, Masters and
  Trainer Leaders add EVs, a fitting nature and a held item (Life Orb, Leftovers, Choice items, Focus Sash…). Same
  species as before. With an RCT level cap, the same trained Pokémon are simply brought down to the cap.

### Added
- **Special requests** from Expert and Master Pokémon Traders: besides their usual offers, one a day asks for a
  particular Pokémon (any of its evolutions will do) and gives one of theirs at the top of their range — shiny one
  time in four.

## 0.39.0 — 2026-09-28

### Added
- **Ferry houses** in villages: a travel post with a ferryman, which joins the travel network under a village name
  ("Willowbrook", "Amberhollow"…) the first time someone uses it. Right-click it once and ferrymen elsewhere sell
  tickets there.

## 0.38.0 — 2026-09-28

### Added
- **Guards use bows.** Put a bow in a guard's chest and they carry it: they shoot creepers from a safe distance
  (backing away if one comes close), shoot fliers and pick off monsters before they get close, then switch to the
  sword. They never shoot while a player, villager or pet is in the line of fire.

### Checked
- Farmers look after fields of Cobblemon mints too: ripe mints are picked (the leaves go to the chest) and planted
  again. Now covered by a test.
- A full Cobbleverse server boots cleanly with 0.37.0, and a new Repurposed Structures village had a workshop, a Trainer
  Leader's hall and an orchard house.

## 0.37.0 — 2026-09-28

### Added
- Villages grow **orchard houses** (a Fruit Basket, a chest of fruit and an indoor bed of sweet berry bushes) and, with
  Cobblemon, **ball workshops** (a Ball Workbench and a chest of copper and dye), each with a villager to work there.

## 0.36.0 — 2026-09-28

### Added
- **Ball Smiths** (Ball Workbench, with Cobblemon): they turn the apricorns and ingots in the chests nearby into Poké
  Balls with Cobblemon's own recipes — copper balls at first, then iron, gold and diamond ones as they level up —
  taking turns between kinds and stopping at 64 of each. Steel and Fire Pokémon in a pasture nearby help.

## 0.35.0 — 2026-09-27

### Added
- **Shops take CobbleDollars.** With CobbleDollars installed, right-clicking a shopkeeper opens the shop's own screen:
  everything in stock, emerald prices shown and paid in CobbleDollars (100 per emerald), two clicks to buy. The money
  goes straight to the shop's owner, or waits for them until they next join. Sneak-right-click for the old trade
  screen.
- **Shop sales log**: the owner sneak-right-clicks the Shop Counter to see the latest sales (who bought what, for how
  much, on which day) and the shop's totals.
- **Ferry fares in CobbleDollars**: with CobbleDollars, a ferryman's screen lists your destinations with their fares
  in CobbleDollars; two clicks buy the ticket.

### Fixed
- Every new workshop, guard house, clinic and other staffed house now gets a jobless adult villager. They used to come
  from the village's own villager pool, which sometimes gave a nitwit (who never works), a baby or, with
  CobbleDollars, a Cobble Merchant.

## 0.34.0 — 2026-09-27

### Added
- **Pokémon partners** (with Cobblemon): Pokémon kept in a Pasture Block within 16 blocks of a workstation help that
  villager when their type suits the job — Fighting/Rock/Steel types build, Ground/Rock/Steel types mine, Grass
  types tend orchards, trees and fields, Water types fish, and so on. Each cuts the work time by 15% (up to three);
  the villager's overhead line says who is helping.
- **Cobbleworkers support**: a postman's courier route can start at a Pasture Block and empties every chest and
  barrel around it (where Cobbleworkers' Pokémon put what they gather).

## 0.33.0 — 2026-09-27

### Added
- **Orchard Keepers** (Fruit Basket workstation): they pick ripe sweet berries, glow berries and cocoa pods — and,
  with Cobblemon, apricorns and berry plants — within 16 blocks of the basket, and store the harvest in the chests
  nearby. The plants are picked, not broken, so they grow again. They trade fruit, apricorns and berries.

### Fixed
- Workers no longer choose a berry bush, fire, powder snow or a cobweb as a place to stand.

## 0.32.0 — 2026-09-27

### Added
- Villages grow three more houses with Cobblemon installed: a **Trainer Leader's hall** (common, so most villages
  get a leader), a **school** with a Move Tutor and a **trade hall** with a Pokémon Trader. Without Cobblemon,
  villages no longer grow trainer's houses (their villagers had nothing to do).

### Fixed
- The villager moving into a new clinic or post office could become a leatherworker or librarian (the house had a
  cauldron or a lectern). Those are gone; every staffed house now has exactly one job block.

## 0.31.0 — 2026-09-27

### Added
- **Guards answer the village bell.** Ring it and, while everyone else runs home to hide, the guards head for the
  bell and fight anything near it for a minute and a half.

### Checked
- Booted a real Cobbleverse 1.7.42 server (all 136 mods) with Alive Workplace: it starts cleanly, and Repurposed
  Structures villages generate with our houses.

## 0.30.0 — 2026-09-27

### Changed
- **Trainers match your Radical Cobblemon Trainers level cap** (RCT is in the Cobbleverse pack): their Pokémon go
  no higher than your cap −6 (Novice), −3, ±0, +3 or +5 (Master). Weaker teams stay as they are. Without RCT nothing
  changes.

## 0.29.0 — 2026-09-27

### Fixed
- Builders (and every other worker) saw **Sophisticated Storage** chests and barrels as empty and waited for
  materials that were right there. They now use them like any chest.

### Changed
- Storage-network blocks (Tom's Storage connectors, terminals and cables; the Sophisticated Storage controller and
  its links) no longer count as supply chests, so the chests behind them aren't counted twice.
- Tested with the Cobbleverse pack's own building mods: Handcrafted, Beautify, CobbleFurnies, Carved Wood and Moar
  Concrete (every block builds and costs its item).

## 0.28.0 — 2026-09-27

### Changed
- **Move Tutor lessons cost CobbleDollars** when that mod is installed (300 to 2,400, the money you win from
  trainers); emeralds as before without it. Trainer prizes now go straight into your CobbleDollars balance instead
  of through a command.

### Added
- **Repurposed Structures villages** (in the Cobbleverse pack) now grow our builder's workshops, trainer's houses,
  guard houses, clinics and post offices too, in the closest matching style (birch and oak villages get plains
  houses, badlands desert ones, and so on). Nether and ocean villages are left alone.

## 0.27.0 — 2026-09-27

### Added
- **Water and lava in blueprints**: fountains, pools and fireplaces get built too. The builder pours each still water
  or lava block from a bucket in the chests once the walls are up (flowing water fills in by itself) and puts the
  empty bucket back. Blueprint materials list the buckets.
- **Item frames, paintings and armor stands in blueprints** go up when the building is finished, paid for with an
  item frame, painting or armor stand from the chests, and turned to match the building. They're always put up
  empty: a blueprint never hands out what was in a frame or on a stand. Works with structure files, .litematic and
  .schem. If some are missing, the builder says so; stock them and hand the blueprint over again.

## 0.26.0 — 2026-09-27

### Added
- **Ground levelling**: when a building is finished, the builder tidies the ground two blocks around it — natural dirt,
  stone, sand and grass sticking up above the floor is dug away (up to 6 blocks high) and holes at floor level are
  filled with dirt. Trees, flowers, farmland, water and anything built are left alone, and it never holds a build
  up waiting for dirt. `/gamerule workplaceLevelGround` sets the width (0 turns it off).

## 0.25.0 — 2026-09-27

### Added
- **What's still missing**: a placed blueprint's tooltip now says what the chests by the nearest Builder's Bench
  are short of (hold Shift for the list), or that everything is there. Blocks already standing where the blueprint
  wants them don't count. It updates every couple of seconds while the blueprint is in your inventory.

## 0.24.0 — 2026-09-27

### Added
- **Pokémon Traders** (with Cobblemon). Craft a **Trade Board** (an item frame on planks) for a villager and
  right-click them to see today's offers: one of their Pokémon for any of yours of a given type and level. Pick an
  offer and a Pokémon that fits, click twice, and they swap (held items come back to you). New offers every in-game
  day, one trade per player per trader per day; traders rank up with every trade — more offers, stronger Pokémon,
  the odd shiny at Master. Sneak-right-click to buy Poké Balls, Exp. Candy, Rare Candy and more for emeralds.

## 0.23.0 — 2026-09-27

### Added
- **Move Tutors** (with Cobblemon). Craft a **Tutor's Desk** (a book on planks) for a villager and right-click them:
  pick one of your Pokémon and a move it could learn (tutor, TM and egg moves), click twice, pay in emeralds and it
  knows the move — straight into its moves if there's room, otherwise ready to swap in from its summary. New tutors
  teach weaker moves; they rank up with every lesson until a Master teaches anything. Tutors buy paper and books too.

### Fixed
- Trainers without a name tag no longer show their job twice ("Novice Trainer Trainer").

## 0.22.0 — 2026-09-27

### Added
- Villages grow more of our houses: a **trainer's house** (often — most new villages get one), a **guard house** with a
  chest of starting gear, a **clinic** and a **post office**, each in the village's own style and with a villager who
  takes the job. Only villages generated after updating get them.

## 0.21.0 — 2026-09-27

### Added
- **Trainer Leaders.** Craft a **Leader's Podium** (gold ingots either side of a Training Post, on polished andesite)
  and a villager becomes the village's Trainer Leader: they battle at Expert strength from day one, pay three times
  the usual prize, and take one challenge a day from each player. One leader per village — if there are several, the
  most experienced one takes the challenges.

## 0.20.0 — 2026-09-27

### Added
- **Pokémon Trainers** (with Cobblemon). Craft a **Training Post** (a target block on planks) and a villager becomes a
  Trainer. Right-click one with an empty hand to battle their team. Trainers start as Novices (2 Pokémon around level
  10) and rank up every time anyone battles them, all the way to Master (6 fully evolved Pokémon, level 80–100, and a
  much smarter battle AI). Beating a trainer pays CobbleDollars (emeralds if CobbleDollars isn't installed) once a day
  per trainer. No badges, no gyms.

## 0.19.0 — 2026-09-27

### Added
- **Bards.** Craft a **Music Stand** (paper on a note block) and a villager becomes a Bard. Twice a day — a morning
  set and an evening set while the village gathers — they play the music discs from the chests near the stand, one
  after another (the discs stay in the chest). With no discs they make up a tune on the harp.

## 0.18.0 — 2026-09-27

### Added
- The **Healing Center** blueprint now has a Nurse Station behind the counter and the **Supply Shop** a Shop Counter,
  so building them brings in a nurse and a shopkeeper (the first player to open the shop's counter owns it).
- Builds and quarries keep going while you're away: as long as the player who ordered them is online, the chunks
  with the site, the workstation, its chests and the worker stay loaded. Turn it off with
  `/gamerule workplaceKeepWorkLoaded false`. Nothing is kept loaded for players who are offline.

## 0.17.0 — 2026-09-27

### Added
- **Courier routes.** Postmen now also carry things between your chests when there's no mail. Craft a **Delivery
  Note** (paper, a feather and an ink sac), right-click the container to take from and then the one to bring to —
  for example your miner's chest and your builder's chest — and give it to a postman. By default they carry
  everything except tools, weapons and armor; hold an item in your other hand and right-click the air with the
  note to carry only that kind (up to 9 kinds). A postman runs up to 4 routes; give them a blank note to end them.

## 0.16.0 — 2026-09-27

### Added
- **Ferrymen and travel posts.** Craft a **Travel Post** (a sign over planks and a boat), name it in an anvil if you
  like, and place it in each village or base you want to connect; a villager takes it as a **Ferryman**. Right-click a
  post to add it to the posts you know. A ferryman sells **Travel Tickets** to every other post you know — 1 emerald
  per 256 blocks. Use a ticket near any travel post and you arrive next to the ticket's post.

## 0.15.0 — 2026-09-27

### Added
- **Shops.** Craft a **Shop Counter** (an emerald over planks and a chest) and place it: it's your shop, and a
  villager takes it as a **Shopkeeper**. Open the counter to set prices: in each column, the top slot is what one
  sale hands over (say 16 cobblestone) and the slot under it is the price (say an emerald — any item works). Put the
  goods in chests within 8 blocks of the counter. Players trade with the shopkeeper like any villager; what's for
  sale is whatever the chests hold, and the payment goes into the chests for you.

### Fixed
- Builders, miners and other workers could take items out of a mailbox within 8 blocks of their workstation, or
  drop their haul into it. Mailboxes are now private.

## 0.14.0 — 2026-09-27

### Added
- **Nurses.** Craft a **Nurse Station** (glass bottles around a glistering melon slice, on white wool) and a villager
  becomes a Nurse. Right-click a nurse with an empty hand to get your health back and poison, wither and other bad
  effects cleared — and with Cobblemon installed, your whole team healed too (not during a battle). Once a minute per
  player, less as the nurse levels up. While at work, nurses also look after hurt villagers and iron golems nearby,
  guards included. Sneak-right-click to trade: they sell healing potions, honey and golden food.

## 0.13.0 — 2026-09-27

### Added
- **Guards.** Craft a **Guard Post** (an iron sword over planks and a shield) and a villager becomes a Guard. Put
  weapons and armor in a chest near the post and they take the best of it. Guards fight any monster that comes
  within 24 blocks of their post, day or night — except creepers — and never run away. They have twice a villager's
  health, heal between fights and hit harder as they level up. They keep the night watch and sleep in the late
  morning. Players, villagers, animals, pets and Pokémon are safe from them.

## 0.12.0 — 2026-09-27

### Added
- **Mail and postmen.** Craft a **Mailbox** and place it: mail for you arrives there and a red flag goes up. Open it,
  put items in the top row, write a player's name and press Send. Craft a **Postal Desk** and a villager becomes a
  **Postman**: they collect parcels from mailboxes within 64 blocks of the desk and carry them to the recipient's
  mailbox. Parcels for mailboxes further away go with the night mail and arrive at dawn. Players hear when mail
  arrives and when theirs was delivered.

## 0.11.0 — 2026-09-27

### Added
- **Fishermen fish for you.** Hand any Fisherman villager (the ones with a barrel) a fishing rod. They walk to the
  nearest water within 16 blocks of their barrel, cast from the shore and reel in fish (and the odd bit of junk —
  no treasure), bringing the catch back to their barrel and the chests next to it. Rods wear out; put spares in
  the barrel. What they're doing and how much they've caught shows above their heads; sneak-right-click them for
  details and a button to stop.

## 0.10.0 — 2026-09-27

### Added
- **Farmers look after your fields.** Craft a **Field Marker** (stick + wheat seeds + paper), right-click two
  opposite corners of a field (up to 32 × 32) and give the marker to any Farmer villager. They harvest what's
  ripe and plant the same crop straight back, sow empty farmland with seeds from the chests near their composter,
  till bare dirt and grass with a hoe from those chests, cut sugar cane down to its bottom block, pick pumpkins
  and melons, and put the harvest in the chests. Works with modded crops too. Their field and what they're doing
  shows above their heads; sneak-right-click them for details and a button to stop. Farmers also sell the marker.

### Changed
- Workers can now stand on farmland and dirt paths while they work.

### Fixed
- A lumberjack whose tree dropped no sapling left the stump bare; they now bring one from the chests.

## 0.9.0 — 2026-09-27

### Added
- **Lumberjacks** — the third job. Craft a **Chopping Block** (a stone axe on top of any log) and a villager without
  a job takes it. Put **axes** in a chest within 8 blocks of the block: the lumberjack cuts down the trees within
  16 blocks, one at a time — leaves first, then the trunk — plants a sapling of the same wood where each tree stood,
  and stores the logs, sticks and apples in the chests. Only real trees are cut: logs someone placed (houses, posts,
  anything with placed leaves), blueprint builds and quarries are left alone. Axes wear out; when the last one breaks
  they wait for another. What they're doing and how many trees they've cut shows above their heads. Lumberjacks
  level up like the other workers and trade sticks and apples for emeralds, and sell logs, saplings and an iron axe.

## 0.8.0 — 2026-09-27

### Added
- **Miners** — the second job. Craft a **Miner's Bench** (cobblestone, a stone pickaxe and planks) and a villager
  without a job takes it. Mark out a quarry with a **Quarry Marker** (stick + red dye + paper): right-click two
  opposite corners, sneak-right-click the air to choose how deep (4 to 64 blocks), then give it to the miner.
  They dig it out layer by layer with a pickaxe from the chests near their bench (bring spares — pickaxes wear out
  and better ones dig harder blocks), put everything they dig into those chests, light the pit with torches if
  there are any, and never break through to lava or water. Progress shows above their heads; sneak-right-click
  them for details. Miners level up like builders and trade coal, ores, torches and pickaxes.

### Fixed
- The villager in a builder's workshop could become a fisherman (the workshop had a barrel) instead of a builder.

## 0.7.0 — 2026-09-26

### Added
- Villages grow builder's workshops (plains, desert, savanna, snowy and taiga styles, in roughly every other new
  village): a small house with a Builder's Bench, a bed, and a chest of building supplies that sometimes holds a
  Starter Cottage or Market Stall blueprint. The villager living there becomes a builder on their own.
- Taking buildings down: place a blueprint over a building and **sneak** while giving it to a builder. They take it
  down carefully (torches and doors first, then from the roof down) and put every block back in the supply chests —
  glass comes back as glass. Only blocks that match the blueprint are touched; chests, furnaces and other blocks that
  hold things are left standing.
- Builders have a boss: the first player to hand a builder a blueprint hires it. After that only they, the friends
  they add with `/workplace friend add <player>` (and operators) can give it blueprints or cancel its builds, and it
  only helps its boss's and their friends' builds. `/workplace friend list` and `/workplace friend remove` manage the
  list; `/gamerule workplaceBuilderOwnership false` turns this off.

## 0.6.0 — 2026-09-26

### Added
- Crews: builders with nothing to do help with builds near their bench (up to 3 helpers per build). They work
  alongside the builder in charge, share the same supply chests, pass each other spare materials, and head home when
  the build is done. Their heads say who they're helping. Handing a helper its own blueprint takes it off the crew.
  Turn it off with `/gamerule workplaceBuildersHelp false`.

### Changed
- "Waiting for materials" messages come at most once a minute per build, and don't count materials a crew mate is
  already carrying as missing.

## 0.5.0 — 2026-09-26

### Added
- Works with **Chipped** and **Rechiseled**: builds that use their block variants (herringbone oak planks, oak beams…)
  can be built from the plain block — the builder "chips" it on the spot, just like the workbench or chisel does for free.
  Missing-material messages, the Blueprint Table and the blueprint tooltip name the plain block ("12 Oak Planks").
- Works with **Supplementaries**: way signs (they cost the fence and the sign), rope knots, timber frames, flower
  boxes, jars, item shelves, flags, blackboards and the rest build correctly. Book piles are left out (they're made of books).
- Potted plants are built (they cost a flower pot and the plant) instead of being skipped.

### Fixed
- Blueprint block data can no longer hand out free items or mobs from any mod: containers (chests, jars, shelves,
  safes…) are always built empty, and item or mob data hidden inside other blocks is removed. Safes and locks are
  never copied with their old owner or password.

### Dev
- `./gradlew runCompatGameTest` (part of `build`) runs in-game tests with Chipped, Rechiseled and Supplementaries installed.

## 0.4.0 — 2026-09-26

### Added
- Builders level up by building: every 5 blocks placed and every finished build earn villager XP, and they level up
  just like villagers you trade with. Each level makes them faster (a Master builder works 2.5× as fast as a Novice)
  and unlocks their next blueprints for sale. Their owner gets a message when they level up, and sneak-right-clicking
  a builder shows their level and XP.
- Build queue: hand a busy builder more placed blueprints (up to 5) and they build them one after another.
  Queued builds reserve their spot, show up in `/workplace sites` and the builder's status ("Next up: …"),
  and can be cancelled on their own.
- Hold Shift over a blueprint to see the materials it needs.
- Two new blueprints: the **Healing Center** (white walls, red roof; sold by level 4 builders) with a Cobblemon
  Healing Machine on the counter when Cobblemon is installed, and the **Supply Shop** (blue roof, shelves and a
  counter; sold by level 5 builders).

## 0.3.0 — 2026-09-26

### Added
- See-through preview: hold a placed blueprint to see the whole building as ghost blocks where it will stand.
  Blocks that are already built disappear from the preview, so you can watch it fill in.
- Builders show what they are working on above their heads: the build's name, percentage, a progress bar,
  and what they are doing or waiting for (e.g. "Needs: 12 Glass").
- Foundations: on uneven ground, builders fill the gap under the floor down to the ground (dirt under grass,
  the floor's own block when it is a plain block, otherwise cobblestone) instead of leaving the house on stilts.
  `workplaceFoundationDepth` gamerule sets how deep they go (default 12, 0 turns it off).

## 0.2.1 — 2026-09-26

### Fixed
- Builders could freeze when standing on the spot they needed to build (found in the first playtest):
  they now actually step aside, and hop if they can't.
- Builders pick work spots that aren't where the building still needs blocks, so they stop getting in their own way.
- Pokémon, animals and other mobs standing on a build spot get shooed off (and moved next to the site if they won't budge);
  players in the way get a polite message. Builders work on other blocks meanwhile instead of waiting.
- Sneak-right-clicking a builder now says what it is waiting for (e.g. "Waiting for Bulbasaur to move out of the way").

### Dev
- `./gradlew runGameTest -PbuilderDebug=true` logs every builder's decisions.

## 0.2.0 — 2026-09-26

### Added
- Blueprint Table: browse every blueprint on the server with its size and full materials list, and take a copy for a Blank Blueprint (craft paper + blue dye).
- Upload your own builds: put `.litematic` or `.schem` files (e.g. from Planet Minecraft) in your `blueprints` folder, pick one at the Blueprint Table and press Upload — it becomes a blueprint everyone on the server can use.
- Imports read Litematica files, WorldEdit/Sponge schematics (v1–v3) and vanilla structure files; old block names are upgraded automatically and blocks from missing mods are reported.
- `/workplace import` (ops) imports files dropped into `<world>/aliveworkplace/import/`.
- `workplaceAllowUploads` gamerule.

### Fixed
- The blueprint list no longer shows other mods' world-generation structures.

## 0.1.0 — 2026-09-26

### Added
- Builder villager profession and the Builder's Bench workstation.
- Blueprints: place (faces you), rotate, pick up; particle outline preview; hand to a builder to start a build.
- Builder AI: clears the site, fetches materials from storage near the bench (modded storage included),
  builds structure then decorations, waits and reports missing materials, returns the blueprint when done.
- Starter blueprints sold by builders: Starter Cottage, Market Stall, Lookout Tower.
- `/workplace` command and the `workplaceFreeMaterials` / `workplaceBuildDelay` gamerules.
- Headless in-game test suite covering building, waiting for materials, clearing, rotation, cancelling and the starter builds.
