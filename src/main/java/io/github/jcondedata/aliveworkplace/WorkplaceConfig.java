package io.github.jcondedata.aliveworkplace;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.fish.FisherWork;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.mail.PostOffice;
import io.github.jcondedata.aliveworkplace.orchard.OrchardWork;
import io.github.jcondedata.aliveworkplace.wood.LumberjackWork;
import io.github.jcondedata.aliveworkplace.work.Money;
import io.github.jcondedata.aliveworkplace.work.Partners;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code config/aliveworkplace.json}: server-wide distances and the CobbleDollar rate. Written with the defaults
 * the first time the game starts; out-of-range values are clamped, missing ones take their default, and the file
 * is rewritten so every option is listed. Per-world tuning (build speed, levelling, …) stays in the gamerules.
 */
public final class WorkplaceConfig {
	/** Chests and barrels within this many blocks of a workstation are that villager's supply chests. */
	public int supplyRadius = 8;
	/** How far from their bench a builder takes a build outside the village (36.20: anywhere in the village it stands in). */
	public int maxSiteDistance = 128;
	/** 36.20: how far from the workstation, outside the village, a quarry, field, tree farm, orchard or berry plot may be. */
	public int quarryReach = 128;
	public int fieldReach = 128;
	public int treeFarmReach = 128;
	public int orchardAreaReach = 128;
	public int berryPlotReach = 128;
	/** How far from the Guard Post guards patrol and fight. */
	public int guardRadius = 24;
	/** How far from their workstation lumberjacks cut, orchard keepers pick and fishers fish. */
	public int lumberjackRadius = 16;
	public int orchardRadius = 16;
	public int fisherRadius = 16;
	/** How far from their cartography table explorers go on an expedition. */
	public int explorerRange = 48;
	/** How far from a workstation pastured Pokémon count as partners. */
	public int partnerRadius = 16;
	/** How far from the Postal Desk a postman walks to deliver (farther mail arrives at dawn). */
	public int postmanRange = 64;
	/** At most this many workers per village (taken workstations within villageRadius); 0: no cap. */
	public int maxWorkersPerVillage = 0;
	/**
	 * A jobless villager who takes a block several jobs share (a composter, a lectern...) by themselves gets one of its
	 * jobs at random, likelier the fewer the village has (36.17). Off: always the block's vanilla job, as before.
	 */
	public boolean randomStationJobs = true;
	/** A Miner with no pickaxe makes one at their workstation from what they mined (36.11). Off: they wait for one. */
	public boolean minerCraftsPickaxe = true;
	/** The Quarry build (36.12): its Miner works its pit without a marker, and what they dig grows back. Off: not offered, nothing grows back. */
	public boolean quarryBuild = true;
	/**
	 * With Waystones installed, the Steward proposes a waystone plaza by the Village Hall, its stone named after the village
	 * (ROADMAP 41.1). Off: the plaza isn't proposed (stones already standing stay as they are). Without Waystones it does nothing.
	 */
	public boolean villageWaystones = true;
	/** Blocks that grow back in a Quarry build's pit in a Minecraft day (0: none). */
	public int quarryRegrowth = 100;
	/** A village takes only so many fishermen (ROADMAP 26.13: storage barrels made most villagers fishermen). Off: vanilla. */
	public boolean fishermanCap = true;
	/** Fishermen every village takes, with the cap on. */
	public int fishermenPerVillage = 1;
	/** And one more fisherman for every this many villagers. */
	public int villagersPerFisherman = 10;
	/** How far villagers with a job look for a path in one go (vanilla: 48). */
	public int workerPathRange = 48;
	/** Workers whose workstations are this close together are one village and share their chests. */
	public int villageRadius = 48;
	/** How far from a Village Hall its village reaches. */
	public int villageHallRadius = 64;
	/** Villages keep working while no player is near them (their chunks stay loaded while anyone is online). */
	public boolean keepVillagesWorking = true;
	/** Whether builders lay a dirt path from each finished building to the village's bell or hall. */
	public boolean builderPaths = true;
	/** A village with a hall stops having babies at this many villagers (0: villages don't grow). */
	public int villageGrowthCap = 40;
	/** Villagers in a village with a Village Hall get names. */
	public boolean villagerNames = true;
	/** Villagers have traits (diligent, lazy, nimble...). */
	public boolean villagerTraits = true;
	/** Villagers in a village with a Village Hall fall ill now and then (a Nurse cures them). */
	public boolean villagerSickness = true;
	/** Villagers in a village with a Village Hall have moods that change how fast they work. */
	public boolean villagerMoods = true;
	/** Legends (rare named villagers with powers) can come to villages that earn them. */
	public boolean legends = Expansions.on(Expansions.M29);
	/** A settled Legend needs a home of their own, their luxury and a happy village, and strikes without them. */
	public boolean legendNeeds = Expansions.on(Expansions.M29);
	/** Legends can be found at ruined portals, pillager outposts and shipwrecks (a camp set down for a player who qualifies). */
	public boolean legendSites = Expansions.on(Expansions.M29);
	/** Once a day a Master in a happy village may be taken by a strange mood, asking for three rare materials to make a Masterwork and become a Legend. */
	public boolean strangeMoods = Expansions.on(Expansions.M29);
	/** One villager in this many is Gifted, with a rare trait (0: nobody is; nothing is erased). */
	public int giftedChance = 30;
	/**
	 * Mythic Legends a village may hold, by its rank: Hamlet, Village, Town, City. Edited in the file only (a list isn't
	 * on the settings screen); each is clamped to 0-10, a short list is filled from the defaults.
	 */
	public List<Integer> mythicLegendCap = new ArrayList<>(DEFAULT_MYTHIC_CAP);
	/** Idle builders repair the buildings they finished when blocks go missing. Off: they still put back what threats took (32.16a). */
	public boolean builderRepairs = true;
	/**
	 * A builder who finds blocks missing from a finished build asks its owner first (36.15: "Repair? Yes / Not now", in chat
	 * and on the Village Hall's Repairs page) and repairs only what they agreed to. Off: they repair by themselves, as
	 * before. What threats took is always repaired without asking (32.16a).
	 */
	public boolean repairAsk = true;
	/** A village with a Village Hall and a Market Square holds a market once a week. */
	public boolean marketDays = true;
	/** Monsters raid bigger villages with a Village Hall at night now and then. */
	public boolean villageRaids = true;
	/** Bandits make camp near villages of Village rank or more now and then, and raid them until their chief falls. */
	public boolean banditCamps = true;
	/**
	 * How hard the village's threats are (ROADMAP 32.21), beside each system's own switch: 0, none at all (no raids, lairs,
	 * sieges or disasters); 1, gentle (half as often, no sieges, no hexes, no plague); 2, normal; 3, hard (half again as
	 * often, a quarter more raiders). On Peaceful difficulty nothing comes whatever this says.
	 */
	public int threatLevel = 2;
	/**
	 * A raider, bandit or camp mob another mod refuses to spawn (a mob blocklist such as MobsBeGone, which Cobbleverse ships
	 * with pillagers and zombies on it) is put in the world anyway, so camps and raids aren't empty (B208). Off: such mobs
	 * are left out, and the log says which.
	 */
	public boolean forceThreatSpawns = true;
	/**
	 * The village's own iron golems, relief riders' horses, the Beastmaster's foals and wolf pups and a caravan's pack
	 * llamas, when another mod refuses them (MobsBeGone, which Cobbleverse ships with iron golems, wolves, horses, cats and
	 * llamas on it), are put in the world anyway (B211). Off: they are left out, and the log says which. Never vanilla's
	 * own spawns.
	 */
	public boolean forceVillageSpawns = true;
	/** The Undertaker moves each grave lying about the village to its Graveyard (or a plot near the hall), in rows (B222). Off: graves stay where villagers fall. */
	public boolean gravesToGraveyard = true;
	/**
	 * A bandit camp or other raiders' lair that is broken up (its captain killed) or leaves is left as ruins, its fire put
	 * out, instead of taken down (B209). Off: the camp comes down a few seconds after, its chests' loot spilling out.
	 */
	public boolean campRuins = false;
	/**
	 * Every raider culture (ROADMAP 32.2) and whether it's on: ours by name ({@code monsters}), a datapack's by its id
	 * ({@code pack:id}). One set to false is never picked. In the file only (a map isn't on the settings screen); a
	 * culture the file doesn't list is added, on, when the world's data loads. {@code villageRaids} and
	 * {@code banditCamps} still switch {@code monsters} and {@code bandits}.
	 */
	public Map<String, Boolean> raiderCultures = defaultCultures();
	/**
	 * Every disaster by id (ROADMAP 32.15): ours by their path ({@code drought}), a datapack's by its whole id. One set to
	 * false never starts, and one under way ends at once. In the file only; a disaster the file doesn't list is added,
	 * on, when the world's data loads.
	 */
	public Map<String, Boolean> disasters = new java.util.TreeMap<>();
	/**
	 * A raid by a culture that rams gates on a village with a finished wall or gate build is a siege: the gates shut, the
	 * portcullis drops and the rams go for the gate (ROADMAP 32.4). Off: every raid is a plain one.
	 */
	public boolean sieges = Expansions.on(Expansions.M32);
	/**
	 * In a siege, rams break only the gate blocks of finished wall and gate builds, and a village fire can burn the
	 * builders' blocks; the builders always put back what either took (32.16a). Off: gates hold however hard they are hit
	 * and a village fire burns nothing. The {@code mobGriefing} gamerule off holds the gates too.
	 */
	public boolean siegeDamage = Expansions.on(Expansions.M32);
	/**
	 * Villagers at a grindstone can be made Firewardens with a water bucket (ROADMAP 32.16): they put out the village's
	 * fires. Off: no Firewarden job, and Firewardens already hired stand idle.
	 */
	public boolean firewardens = Expansions.on(Expansions.M32);
	/**
	 * Villagers at a cauldron can be made Well Keepers with a shovel (ROADMAP 32.17): in a drought they carry water to
	 * the fields, and between droughts they keep the cauldrons full and the farmland wet. Off: no Well Keeper job, and
	 * Well Keepers already hired stand idle.
	 */
	public boolean wellKeepers = Expansions.on(Expansions.M32);
	/**
	 * Villagers at a brewing stand can be made Physicians with a glistering melon slice (ROADMAP 32.18): in a plague they
	 * go house to house, send the ill home and cure them with Remedies. Off: no Physician job, and Physicians already
	 * hired stand idle.
	 */
	public boolean physicians = Expansions.on(Expansions.M32);
	/**
	 * Players can hire the village's trainers from the hall's Defence page to help in a raid at a den the village knows
	 * (ROADMAP 38.1a, Cobblemon Raid Dens): they follow the player, cheer once a raid with one of the raid mod's cheer
	 * items each, and wear the boss down. Off: no hiring, and those hired walk home. (Replaces 38.3's denWardens and
	 * 38.4's denChallenges and denChallengeDays; old files with those keys load, the keys are dropped.)
	 */
	public boolean denHelpers = Expansions.on(Expansions.M38);
	/** Emeralds a raid helper costs (38.1a). */
	public int denHelperPrice = 5;
	/**
	 * More village trainers Mega Evolve (ROADMAP 38.5, Mega Showdown): a City's Trainer Leaders, the Festival Cup's
	 * finalists and the Stadium Day's visitors carry a Mega Stone too, and every trainer who can Mega Evolve wears a Mega
	 * Bracelet. 38.6: the Jeweller cuts Mega Stones and Key Stones, Miners take the Mega finds to the store, and a player
	 * who beats every Trainer Leader of a realm gets a Mega Bracelet. Off: only Masters carry one, as before, nobody wears
	 * a bracelet and the village makes no Mega Stones.
	 */
	public boolean villageMegas = Expansions.on(Expansions.M38);
	/**
	 * Cobblecuisine's dishes on the menus (ROADMAP 38.8): the Chef cooks them from their own recipes by his level, each
	 * counts as its food group for the villagers' diet, the Camp Cook adds its curries and the Innkeeper serves its
	 * drinks. 38.11: with Cobblemon, the Chef also cooks every Campfire Pot recipe (candies, medicines, sweets) from the
	 * store's makings. Off: the menus and the diet are as they were.
	 */
	public boolean cuisineDishes = Expansions.on(Expansions.M38);
	/**
	 * Bean farms (ROADMAP 38.9): with Cobblecuisine, farmers sow its beans when the store holds fewer than 16 of a colour
	 * a dish on the menu needs, and cut them when ripe, into the store. Off: farmers never sow beans.
	 */
	public boolean beanFarms = Expansions.on(Expansions.M38);
	/**
	 * With CobbleFurnies installed, a jobless villager by its Furnicrafter becomes the village's Furnicrafter (ROADMAP
	 * 38.12), who makes the CobbleFurnies furniture the builders nearby are waiting for (a furnished home's chairs, tables
	 * and lamps) and the storehouse's stock orders ask for, from the store's materials. Off: no new Furnicrafters, and
	 * those hired stand idle. Without CobbleFurnies it does nothing.
	 */
	public boolean furnicrafters = Expansions.on(Expansions.M38);
	/**
	 * With Create installed, a jobless villager by a Create Toolbox becomes the village's Mechanic (ROADMAP 39.2): they walk
	 * a round of the village's machines, empty full millstones into the store and refill empty ones, and put back the
	 * shafts, cogwheels and belts gone from finished builds. Off: no new Mechanics, and those hired stand idle. Without
	 * Create it does nothing.
	 */
	public boolean mechanics = Expansions.on(Expansions.M39);
	/**
	 * With Create installed, villagers work Create's machines (ROADMAP 39.3): the farmer mills wheat into flour, the
	 * armorer presses ingots into sheets, the chef mixes dough and the miner crushes raw ore, taking the input from the
	 * store and bringing what's made back, only while the machine turns and the store has room. Off: nobody feeds a
	 * machine. Without Create it does nothing.
	 */
	public boolean machineWork = Expansions.on(Expansions.M39);
	/**
	 * With Create installed, the Blueprint Table offers the Watermill, the Windmill and the Workshop (ROADMAP 39.4) and the
	 * Brass Foundry, the Sawmill, the Crushing Mill, the Greenhouse, the Clock Tower and the Pump House (39.8),
	 * workplaces whose machines turn as soon as they're built. Off: they aren't offered (sites already started still
	 * finish). Without Create it does nothing.
	 */
	public boolean createBuilds = Expansions.on(Expansions.M39);
	/**
	 * With Create installed, the Blueprint Table lists the schematics players uploaded with Create's Schematic Table, and
	 * a written Schematic used on the table imports its file (ROADMAP 39.6). Off: the table leaves Create's schematics
	 * alone (those already imported stay). Without Create it does nothing.
	 */
	public boolean createSchematics = Expansions.on(Expansions.M39);
	/**
	 * With Create installed, the Blueprint Table offers the Train Station (ROADMAP 39.7); two villages with a caravan
	 * route and a station each get Create track laid along the road between them by their builders, and their caravans go
	 * by train: a third of the trip, never lost. A jobless villager by a Train Station becomes the village's Conductor
	 * (39.7d), who drives its train and calls its stops. Off: no station at the table, no track laid, no new Conductors,
	 * caravans walk (a train already on its way still comes in). Without Create it does nothing.
	 */
	public boolean villageTrains = Expansions.on(Expansions.M39);
	/** Villages with a Village Hall hold a festival every eight days (players can still call one with a cake). */
	public boolean festivals = true;
	/** Villagers near a player now and then say something about their day, over their heads. */
	public boolean villagerChatter = true;
	/** Villagers court, marry (a wedding at the bell) and mourn. */
	public boolean villagerCouples = true;
	/** Villages with a Village Hall put by takings every morning for players to collect at the hall. */
	public boolean villageTreasury = true;
	/** Village Halls post quests for players (the quest engine, 31.2); off: no new quests, open ones can still be finished. */
	public boolean villageQuests = true;
	/** With FTB Quests, keep every questbook chapter file of ours as it is on start (off: a new version of a chapter overwrites it). */
	public boolean questbookKeepEdits = false;
	/** The Blueprint Table is a collection (26.10): each village unlocks builds beyond the basics. Off: every build is unlocked, as before. */
	public boolean blueprintUnlocks = true;
	/** Named villagers keep a friendship with each player, shown in hearts (ROADMAP 31.5). Off: no points, no hearts shown; saved friendship stays. */
	public boolean friendship = Expansions.on(Expansions.M31);
	/** Villagers tell their friends about their lives at 2, 4, 6, 8 and 10 hearts (heart events, 31.7). Off: nobody starts telling; what was told stays. */
	public boolean heartEvents = Expansions.on(Expansions.M31);
	/** Villagers with 3 hearts ask a player for help now and then (personal requests, ROADMAP 31.9). Off: nobody asks; requests already accepted can still be finished. */
	public boolean personalRequests = Expansions.on(Expansions.M31);
	/** Players earn a standing and titles in each village (reputation, ROADMAP 31.11). Off: nothing earned, lost, told or shown; saved standings stay. */
	public boolean reputation = Expansions.on(Expansions.M31);
	/** A player's title goes before what they say in chat (31.11). Off: chat lines aren't decorated; titles still count and are told. */
	public boolean titlesInChat = Expansions.on(Expansions.M31);
	/** Story arcs (31.4) unfold in villages, chapter by chapter. Off: none starts, and a running one ends quietly at its next round. */
	public boolean storyArcs = Expansions.on(Expansions.M31);
	/** From Village rank, villages post bounties on named bandit captains (the Bounty Board, ROADMAP 31.13). Off: nothing new is posted; open bounties still pay. */
	public boolean bountyBoard = Expansions.on(Expansions.M31);
	/** With Cobblemon, half the bounties are on a rogue Pokémon roaming 150 to 250 blocks out (ROADMAP 31.14). Off: no rogue is posted; open ones still pay. Never without Cobblemon. */
	public boolean rogueBounties = Expansions.on(Expansions.M31);
	/** Days between two story arcs in one village (also before a village's first). */
	public int arcCooldownDays = 8;
	/** Story arcs running at once on the whole server (side arcs not counted); 0: none. */
	public int arcsAtOnce = 3;
	/** One caravan in this many on a road over 300 blocks goes missing, starting the Lost Caravan (ROADMAP 31.20); 0: none. */
	public int lostCaravanChance = 20;
	/** Story arc ids that never start ({@code bandit_king} or {@code pack:id}); a running one ends at its next round. File only. */
	public List<String> disabledArcs = new ArrayList<>();
	/** A Village Hall's owner may protect the village from other players (a setting on the hall, off until they turn it on). */
	public boolean villageProtection = true;
	/** Pokémon pastured by a workstation are seen helping at work (with Cobblemon): they carry, water, spark... */
	public boolean partnerShows = Expansions.on(Expansions.M28);
	/** A nurse at Cobblemon's Healing Machine heals your team in it, and keeps it charged while on shift (ROADMAP 28.7). */
	public boolean nurseHealingMachine = Expansions.on(Expansions.M28);
	/**
	 * How fast every bonus together can make a worker, in percent of the usual pace (partners, a well-kept village,
	 * research, traits, mood, edicts...). Sickness and bad moods still slow them after that; their level doesn't count.
	 */
	public int maxWorkPace = 200;
	/** Villages' owners proclaim edicts at the hall (off: none can be, and those in force do nothing but stay saved). */
	public boolean villageEdicts = Expansions.on(Expansions.M30);
	/** The Work Horn calls a rush when blown in a village (ROADMAP 30.11). Off: it only sounds. */
	public boolean workHorns = Expansions.on(Expansions.M30);
	/** Village Banners (ROADMAP 30.13) can be crafted and set a village's colours at its hall. Off: neither; colours stay saved. */
	public boolean villageBanners = Expansions.on(Expansions.M30);
	/** A Cradle near a bed makes a nursery village (ROADMAP 30.12): children grow up twice as fast, one more baby a day. Off: cradles are furniture. */
	public boolean cradles = Expansions.on(Expansions.M30);
	/** Harvest Idols (ROADMAP 30.14): in harvest season the crops within 32 blocks of one grow 25% faster. Off: idols are ornaments. */
	public boolean harvestIdols = Expansions.on(Expansions.M30);
	/** Tonics (ROADMAP 30.15): the alchemist and the chef make them and villagers drink them. Off: neither; a tonic drunk does nothing. */
	public boolean tonics = Expansions.on(Expansions.M30);
	/** Guilds (ROADMAP 30.17): Guild Charters make Masters Guild Masters, and founded guilds' perks reach their members. Off: charters are refused and perks are off; guilds stay saved. */
	public boolean guilds = Expansions.on(Expansions.M30);
	/** Guilds a village may have per rank above Hamlet (Village 1x, Town 2x, City 3x). */
	public int guildsPerRank = 1;
	/** Days an edict stays in force before it can be lifted. */
	public int edictMinDays = 3;
	/** Villages work out once a day what goods they're known for and short of, and a price for each (ROADMAP 33.2). Off: nothing is worked out; what was stays saved. */
	public boolean villageEconomy = Expansions.on(Expansions.M33);
	/** A caravan leaving or arriving near a player is seen: a carter leading two pack llamas (ROADMAP 33.7). Off: none is shown; the goods travel the same. */
	public boolean visibleCaravans = Expansions.on(Expansions.M33);
	/** A City can found a sister village with a Colony Charter from its hall's Colonies tab (ROADMAP 33.8). Off: no Colonies tab and no charters; an order on the road arrives anyway. */
	public boolean colonies = Expansions.on(Expansions.M33);
	/** The rank a village needs to buy a Colony Charter: "hamlet", "village", "town" or "city" (in the file only). */
	public String colonyRank = "city";
	/** Days between two colonies of one village. */
	public int colonyCooldownDays = 7;
	/** Colonies a village may found. */
	public int coloniesPerVillage = 3;
	/** A Town can found a realm on its hall's Realm tab and add its owner's other villages (ROADMAP 33.11). Off: no Realm tab, nothing founded or joined; realms stay saved and come back. */
	public boolean realms = Expansions.on(Expansions.M33);
	/** Villages a realm may hold. */
	public int realmMaxMembers = 12;
	/** When a realm village is raided, the capital and members within 1,024 blocks send riders to help, paid from the realm treasury (ROADMAP 33.17). Off: nobody comes. */
	public boolean realmRelief = Expansions.on(Expansions.M33);
	/** A caravan route to another player's village needs a trade pact, offered and accepted on the hall's Pacts tab; pact caravans are paid for what they bring (ROADMAP 33.18). Off: routes need no pact and nothing is offered; pacts stay saved. */
	public boolean tradePacts = Expansions.on(Expansions.M33);
	/** Villages of different owners can ally: allies send relief riders to each other's raids within 1,024 blocks (ROADMAP 33.18). Off: no alliances offered, no riders; alliances stay saved. */
	public boolean alliances = Expansions.on(Expansions.M33);
	/** An owner can declare a feud on another owner's village or realm on the Pacts tab: pacts and routes end, each board pays the rival's people 25% less and charges them 25% more; peace by letter, or it fades after 7 days (ROADMAP 33.20). Off: no feuds; existing ones end quietly. */
	public boolean feuds = Expansions.on(Expansions.M33);
	/** A member's hall's Realm tab draws the realm on an empty map: every member's banner, colonies, allies and rivals, the caravan routes (ROADMAP 33.21). Off: no map button. */
	public boolean realmMap = Expansions.on(Expansions.M33);
	/** With PvP on, a feud can be raised to a war of up to 3 days, in which each side's guards fight the other side's owners inside their own village; nothing else changes (ROADMAP 33.20). Off: no wars, even with PvP on. */
	public boolean feudWars = Expansions.on(Expansions.M33);
	/** Once a week everyone online hears how the villages did: Biggest, Richest, Happiest, Most Legends and the Greatest realm (ROADMAP 33.19). Off: nothing counted or announced; what was counted stays saved. */
	public boolean weeklyLeaderboard = Expansions.on(Expansions.M33);
	/** The day the weekly leaderboard is read out, server time: "monday" to "sunday" (in the file only). */
	public String leaderboardDay = "sunday";
	/** The hour (0 to 23, server time) the weekly leaderboard is read out. */
	public int leaderboardHour = 19;
	/** Households in villages with a hall climb the class ladder (ROADMAP 34.2). Off: no classes; classes and progress stay saved. */
	public boolean villageClasses = Expansions.on(Expansions.M34);
	/** Dawns running the next class's needs must hold for a household to rise one class. */
	public int classRiseDays = 2;
	/** Dawns running a need of their own class must fail for a household to fall one class. */
	public int classFallDays = 3;
	/** Days a village settles in after classes are switched on there (ROADMAP 34.22): nobody falls a class, luxuries aren't needed yet, nobody retires. */
	public int classGraceDays = 7;
	/** Grown villagers count their days and become elders (ROADMAP 34.19). Off: nobody is an elder, so nobody passes of old age; the day they grew up stays saved. */
	public boolean villagerAges = Expansions.on(Expansions.M34);
	/** Grown days before a villager is an elder (36.16: 1200, ten times the first 120; a file of before with 120 moves to 1200). */
	public int villagerElderDays = 1200;
	/**
	 * A death matters (ROADMAP 36.16): the village is told who died, their job and years, their family grieve for 3 days,
	 * and the Undertaker leads a funeral at the grave. Off: none of it (a partner still mourns, as before).
	 */
	public boolean villageMourning = true;
	/** An elder passes away in the night after 400 elder days (36.16; 40 before) and leaves a grave (owner, 2026-10-06). Off: elders never die of old age. */
	public boolean elderPassing = Expansions.on(Expansions.M34);
	/** An Evergreen Charm makes an elder with good traits ageless (ROADMAP 34.19a). Off: charms are refused; elders already ageless stay so. */
	public boolean agelessElders = Expansions.on(Expansions.M34);
	/** Villagers wear their class over their job's outfit, and elders an elder's look (ROADMAP 34.18). Off: only the job's outfit is drawn. */
	public boolean classOutfits = Expansions.on(Expansions.M34);
	/** An elder with a job retires once someone can take over, and mentors the village's Novices and Apprentices (ROADMAP 34.20). Off: nobody starts retiring; those retired stay retired. */
	public boolean elderRetirement = Expansions.on(Expansions.M34);
	/** Every villager in a village with a hall has a family name, shown on the hall ("Dara (Ashby)") and in the chronicle (ROADMAP 34.21). Off: no family names are shown; the Book of Families, the Family page and printed trees go by first names. */
	public boolean familyNames = Expansions.on(Expansions.M34);
	/** Villagers at a cauldron can be made Vintners with sweet berries, glow berries or an apple (ROADMAP 34.9). Off: no Vintner job, and Vintners already hired stand idle. */
	public boolean vintners = Expansions.on(Expansions.M34);
	/** Villagers at a loom can be made Tailors with string (ROADMAP 34.10). Off: no Tailor job, and Tailors already hired stand idle. */
	public boolean tailors = Expansions.on(Expansions.M34);
	/** Villagers at a cartography table can be made Printers with an ink sac (ROADMAP 34.11). Off: no Printer job, Printers already hired stand idle, and no Spread the news quest goes up. */
	public boolean printers = Expansions.on(Expansions.M34);
	/** Villagers at a composter can be made Berry Breeders with a Cobblemon berry (ROADMAP 28.9). Off: no Berry Breeder job. */
	public boolean berryBreeders = Expansions.on(Expansions.M28);
	/** A Journeyman Builder (or higher) by a Village Hall can be made its Steward with the hall's City Plan (ROADMAP 27.5). Off: no new Stewards, and those appointed stand idle. */
	public boolean steward = Expansions.on(Expansions.M27);
	/** The most builds a Steward may have open at once, whatever his level and the village's rank. */
	public int stewardMaxOpenBuilds = 4;
	/** A Steward set to "Run the village" starts the builds he proposes by himself (ROADMAP 27.8). Off: every village asks first. */
	public boolean stewardSelfRun = Expansions.on(Expansions.M27);
	/** A Steward's builders build the approved roads on the plan, and new buildings' doors join them with lanes (ROADMAP 27.15). Off: roads are drawn but not built. */
	public boolean stewardRoads = Expansions.on(Expansions.M27);
	/** Villages with a trade route each build their half of a road to the other, ending at a milestone if it stops short (ROADMAP 27.17). Off: no roads between villages. */
	public boolean caravanRoads = Expansions.on(Expansions.M27);
	/** The longest half of a road a village builds towards another (ROADMAP 27.17); it goes halfway at most. */
	public int caravanRoadReach = 256;
	/** A raided village's Steward proposes a wall along the plan's wall line, built from a wall kit (ROADMAP 27.18). Off: he never proposes walls. */
	public boolean stewardWalls = Expansions.on(Expansions.M27);
	/** A Steward rebuilds the old village houses in zones whose "renew old houses" switch is on, one at a time, in the zone's style (ROADMAP 27.21). Off: he never proposes to renew a house. */
	public boolean stewardRenewal = Expansions.on(Expansions.M27);
	/** Villagers at a Campfire Pot can be made Camp Cooks with Hearty Grains (ROADMAP 28.8). Off: no Camp Cook job. */
	public boolean campCooks = Expansions.on(Expansions.M28);
	/** Villagers at a Pasture Block can be made Habitat Keepers with a honey bottle (ROADMAP 28.10). Off: no Habitat Keeper job. */
	public boolean habitatKeepers = Expansions.on(Expansions.M28);
	/** Villagers at a Pasture Block can be made Daycare Keepers with an egg (ROADMAP 28.12). Off: no Daycare Keeper job, and no eggs. */
	public boolean daycareKeepers = Expansions.on(Expansions.M28);
	/** Villagers at a stonecutter can be made Gem Growers with an amethyst shard (ROADMAP 28.11). Off: no Gem Grower job. */
	public boolean gemGrowers = Expansions.on(Expansions.M28);
	/** Villagers at a stonecutter can be made Jewellers with a gold nugget (ROADMAP 34.12). Off: no Jeweller job, and Jewellers already hired stand idle. */
	public boolean jewellers = Expansions.on(Expansions.M34);
	/** Habitat Keepers tell the village of shiny, rare and Alpha wild Pokémon near their pasture (ROADMAP 28.10). */
	public boolean habitatSightings = Expansions.on(Expansions.M28);
	/** An Expert Habitat Keeper puts one Habitat Block in a finished Habitat Garden, with Cobblemon 1.8 (ROADMAP 28.14). Off: none founded. */
	public boolean villageHabitats = Expansions.on(Expansions.M28);
	/** With Cobblemon, villages grow a Pokémon Center, Camp Kitchen, Berry Nursery, Daycare and Gem Grotto, each with its worker (ROADMAP 28.15). Off: they don't (from the next server start). */
	public boolean pokemonVillageHouses = Expansions.on(Expansions.M28);
	/** With Cobblemon, a village with a hall, a finished Arena and Village rank holds its festivals as a Festival Cup (ROADMAP 28.17). Off: no Cups. */
	public boolean festivalCup = Expansions.on(Expansions.M28);
	/** Towns and Cities can raise Wonders (M35). Off: no new Wonders; finished ones keep their blocks, their powers stop, and everything stays saved. */
	public boolean wonders = Expansions.on(Expansions.M35);
	/**
	 * Wonders a village may hold, by its rank: Hamlet, Village, Town, City. Edited in the file only (a list isn't on the
	 * settings screen); each is clamped to 0-8, a short list is filled from the defaults.
	 */
	public List<Integer> wonderCaps = new ArrayList<>(DEFAULT_WONDER_CAPS);
	/** A Wonder needs its Legends in the village. Off: each Legend's stand-in (a Master of a trade, ...) is asked for instead. */
	public boolean wonderLegends = Expansions.on(Expansions.M35);
	/** Builders put up scaffolding to reach what has nowhere to stand near it, and take it down after (ROADMAP 35.6). Off: those blocks are skipped. */
	public boolean builderScaffolding = Expansions.on(Expansions.M35);
	/** How many villages on the server may each build the same kind of Wonder (0: no limit). */
	public int wonderServerLimit = 0;
	/** Most builders on one Wonder's crew, its lead included (35.5); a Wonder's file may allow fewer. */
	public int wonderCrew = 12;
	/** The share (%) of a finished Wonder that must stand for its powers (35.8); under it, the builders repair it. */
	public int wonderStanding = 90;
	/** Builds use the pack's furniture mods for their chairs, tables, beds and lamps when installed (M37). Off: every build is the vanilla build. */
	public boolean furnishedBuilds = Expansions.on(Expansions.M37);
	/** The furniture mods tried in order for each piece (M37); a mod that isn't installed is passed over. File only. */
	public List<String> furnitureMods = new ArrayList<>(io.github.jcondedata.aliveworkplace.furnish.Furnishings.DEFAULT_MODS);
	/** A Sophisticated Storage controller or Tom's Simple Storage connector in the Storehouse counts as one store for all it links (M37). Off: each linked chest is its own store. */
	public boolean moddedStorageNetworks = Expansions.on(Expansions.M37);
	/** Homes and workshops are built from the modded library (dressed with Rechiseled, Supplementaries, Carved Wood, Chipped and Handcrafted) when every one of its blocks is installed and furnished builds are on (M37, 37.12). Off: the vanilla library. */
	public boolean moddedLibrary = Expansions.on(Expansions.M37);
	/** Wonder ids that can't be started ({@code great_cathedral} or {@code pack:id}); one already finished keeps its blocks and powers. File only. */
	public List<String> disabledWonders = new ArrayList<>();
	/** Which of a host's festivals are Cups: every one (1), every second (2), ... (owner, 28.1a: every festival). */
	public int cupEveryFestivals = 1;
	/** Days in each of the village calendar's four seasons (each has a festival on its middle day). */
	public int seasonDays = 16;
	/** Hundredths of an emerald each worker brings the treasury a day (before wellbeing and rank). */
	public int treasuryPerWorker = 20;
	/** What an emerald price comes to in CobbleDollars (lessons, shops, fares). */
	public int dollarsPerEmerald = 100;
	/**
	 * What retraining a villager who has a job costs in emeralds, by their level: Novice, Apprentice, Journeyman, Expert,
	 * Master (ROADMAP 26.6; in CobbleDollars, times dollarsPerEmerald). Edited in the file only; each is clamped to 0-1000,
	 * a short list is filled from the defaults.
	 */
	public List<Integer> retrainPrices = new ArrayList<>(io.github.jcondedata.aliveworkplace.work.Retraining.DEFAULT_PRICES);
	/**
	 * The file's format (not an option: private, so it's no switch or slider). A file without it was written before
	 * the seasons grew to 16 days (owner, 2026-10-04): its {@code seasonDays} of 8 was the old default, not a choice.
	 */
	@SuppressWarnings("unused")
	private int configVersion = VERSION;
	static final int VERSION = 5;
	/**
	 * The expansions whose gates were open when the file was written ({@link Expansions#open()}; not an option). A gated
	 * switch saved while its gate was closed holds its closed default, {@code false}, which is no player's choice: when
	 * that expansion is finished, {@link #parse} gives such a switch its new default (34.23's rehearsal: a 0.142.0 file
	 * kept classes off under 1.8).
	 */
	@SuppressWarnings("unused")
	private java.util.List<String> expansionsOpen = Expansions.open();

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final String FILE = "aliveworkplace.json";

	/** Reads the config (or the defaults), clamps it, writes it back complete and applies it. */
	public static WorkplaceConfig loadAndApply(Path configDir) {
		WorkplaceConfig config = load(configDir);
		config.save(configDir);
		config.apply();
		return config;
	}

	/** The config in {@code configDir} (the defaults if there is none or it can't be read), clamped. */
	public static WorkplaceConfig load(Path configDir) {
		Path file = configDir.resolve(FILE);
		if (Files.exists(file)) {
			try {
				com.google.gson.JsonElement tree = com.google.gson.JsonParser.parseString(Files.readString(file));
				if (!tree.isJsonObject()) {
					// A list, a bare word, a number or nothing at all is valid JSON but holds no settings (B477): kept
					// aside like a file that isn't JSON, never written over unseen.
					throw new JsonParseException("not a {...} object");
				}
				return parse(tree);
			} catch (IOException | JsonParseException e) {
				AliveWorkplace.LOG.warn("Couldn't read {}, using the defaults: {}", file, e.getMessage());
			} catch (RuntimeException e) {
				// Last line of defence (B457): a file shaped in a way parse didn't foresee gives the defaults, never a
				// server that won't start.
				AliveWorkplace.LOG.warn("Couldn't read {}, using the defaults: {}", file, e.toString());
			}
			keepAside(file);
		}
		return new WorkplaceConfig();
	}

	/** The name a file that couldn't be read is kept under, beside it, before the defaults are written over it (B411). */
	public static final String BAD_FILE = FILE + ".bad";

	/** Copies a file that couldn't be read to {@link #BAD_FILE}, so the owner's edits aren't lost when it's written back. */
	private static void keepAside(Path file) {
		Path copy = file.resolveSibling(BAD_FILE);
		try {
			Files.copy(file, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			AliveWorkplace.LOG.warn("Kept the unreadable config as {}", copy);
		} catch (IOException | RuntimeException e) {
			AliveWorkplace.LOG.warn("Couldn't keep the unreadable config as {}: {}", copy, e.toString());
		}
	}

	/** Writes every option to {@code configDir} (clamped first). */
	public void save(Path configDir) {
		clamp();
		Path file = configDir.resolve(FILE);
		try {
			Files.createDirectories(configDir);
			Files.writeString(file, GSON.toJson(this));
		} catch (IOException e) {
			AliveWorkplace.LOG.warn("Couldn't write {}: {}", file, e.getMessage());
		}
	}

	/** The config in {@code json} (missing values and values that can't be read take their default), clamped to sensible ranges. */
	public static WorkplaceConfig parse(String json) {
		return parse(com.google.gson.JsonParser.parseString(json));
	}

	private static WorkplaceConfig parse(com.google.gson.JsonElement tree) {
		com.google.gson.JsonObject object = tree.isJsonObject() ? tree.getAsJsonObject() : new com.google.gson.JsonObject();
		// The format number and the open expansions are no options: read here by hand, so an empty or odd value in them
		// (null, text, a list where a number goes) is a missing one and never costs the rest of the file (B457).
		Integer writtenFormat = formatNumber(object.get("configVersion"));
		com.google.gson.JsonObject options = object.deepCopy();
		options.remove("configVersion");
		options.remove("expansionsOpen");
		// A value of the wrong kind ("festivals": 0, "supplyRadius": 12.5, "threatLevel": "high") makes Gson refuse the
		// whole object: each one is tried alone first, and one that can't be read is dropped, so it takes its default and
		// every other value in the file is kept (B411).
		for (String key : new java.util.ArrayList<>(options.keySet())) {
			options.add(key, wholeNumbersInRange(key, options.get(key)));
			com.google.gson.JsonObject single = new com.google.gson.JsonObject();
			single.add(key, options.get(key));
			try {
				GSON.fromJson(single, WorkplaceConfig.class);
			} catch (RuntimeException e) {
				options.remove(key);
				AliveWorkplace.LOG.warn("Couldn't read \"{}\" in {} ({}), it takes its default", key, FILE, e.getMessage());
			}
		}
		WorkplaceConfig config = tree.isJsonObject() ? GSON.fromJson(options, WorkplaceConfig.class) : null;
		if (config == null) {
			config = new WorkplaceConfig();
		}
		boolean old = tree.isJsonObject() && writtenFormat == null;
		if (old && config.seasonDays == 8) {
			config.seasonDays = 16;
		}
		// Format 4 (36.16): villagers live ten times longer. A file of before holding the old default 120 moves to the new
		// default; any other number was someone's choice and stays.
		int format = writtenFormat == null ? 0 : writtenFormat;
		if (format < 4 && config.villagerElderDays == 120) {
			config.villagerElderDays = new WorkplaceConfig().villagerElderDays;
		}
		// Format 5 (36.20): a builder's reach outside the village goes from 48 to 128 (the owner's "this won't work for big
		// villages"). A file of before holding the old default moves to the new one; any other number stays.
		if (format < 5 && config.maxSiteDistance == 48) {
			config.maxSiteDistance = new WorkplaceConfig().maxSiteDistance;
		}
		config.configVersion = VERSION;
		// Which gates were open when the file was written: its list, or none in a file a jar of format 2 wrote (0.139.0 to
		// the last release before this); a file with neither (written by hand, or older) is taken as it says.
		java.util.List<String> written = new java.util.ArrayList<>();
		boolean recorded = object.get("expansionsOpen") instanceof com.google.gson.JsonArray;
		if (recorded) {
			// An entry that isn't a name (null, a list, an object) is skipped.
			object.getAsJsonArray("expansionsOpen").forEach(e -> {
				if (e.isJsonPrimitive()) {
					written.add(e.getAsString());
				}
			});
		}
		boolean closedWhenWritten = recorded || (writtenFormat != null && writtenFormat < 3);
		WorkplaceConfig defaults = new WorkplaceConfig();
		for (java.lang.reflect.Field field : WorkplaceConfig.class.getFields()) {
			String milestone = Expansions.milestone(field.getName());
			if (field.getType() == boolean.class && !java.lang.reflect.Modifier.isStatic(field.getModifiers()) && milestone != null && closedWhenWritten
				&& Expansions.on(Expansions.flag(milestone)) && !written.contains(milestone)) {
				try {
					field.setBoolean(config, field.getBoolean(defaults)); // saved while its gate was closed: not a choice
				} catch (IllegalAccessException e) {
					throw new IllegalStateException(e);
				}
			}
		}
		config.expansionsOpen = Expansions.open();
		config.clamp();
		return config;
	}

	/**
	 * {@code value} with every whole number of a whole-number option ({@code int} or a list of them) past what an
	 * {@code int} holds put at the nearest end of it, so it's clamped to the option's range as meant (B477): Gson casts
	 * such a number round ({@code 3000000000} became {@code -1294967296}, so the least allowed value). Anything else is
	 * left as it is.
	 */
	private static com.google.gson.JsonElement wholeNumbersInRange(String key, com.google.gson.JsonElement value) {
		java.lang.reflect.Field field;
		try {
			field = WorkplaceConfig.class.getField(key);
		} catch (NoSuchFieldException e) {
			return value;
		}
		if (field.getType() == int.class) {
			return intInRange(value);
		}
		if (List.class.isAssignableFrom(field.getType()) && field.getGenericType() instanceof java.lang.reflect.ParameterizedType type
			&& type.getActualTypeArguments()[0] == Integer.class && value instanceof com.google.gson.JsonArray array) {
			com.google.gson.JsonArray fixed = new com.google.gson.JsonArray();
			array.forEach(e -> fixed.add(intInRange(e)));
			return fixed;
		}
		return value;
	}

	private static com.google.gson.JsonElement intInRange(com.google.gson.JsonElement value) {
		if (value == null || !value.isJsonPrimitive() || value.getAsJsonPrimitive().isBoolean()) {
			return value;
		}
		java.math.BigDecimal number;
		try {
			number = new java.math.BigDecimal(value.getAsString().trim());
		} catch (NumberFormatException e) {
			return value;
		}
		if (number.signum() != 0 && number.stripTrailingZeros().scale() > 0) {
			return value; // not a whole number: read (or refused) as before
		}
		if (number.compareTo(java.math.BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
			return new com.google.gson.JsonPrimitive(Integer.MAX_VALUE);
		}
		if (number.compareTo(java.math.BigDecimal.valueOf(Integer.MIN_VALUE)) < 0) {
			return new com.google.gson.JsonPrimitive(Integer.MIN_VALUE);
		}
		return value;
	}

	/**
	 * The format number {@code value} holds ({@code 3}, {@code "3"}), or null when it holds none (missing, null, text
	 * that isn't a whole number, a list or an object): such a file is read as one without a number.
	 */
	private static Integer formatNumber(com.google.gson.JsonElement value) {
		if (value == null || !value.isJsonPrimitive() || value.getAsJsonPrimitive().isBoolean()) {
			return null;
		}
		try {
			return new java.math.BigDecimal(value.getAsString().trim()).intValueExact();
		} catch (NumberFormatException | ArithmeticException e) {
			return null;
		}
	}

	/** The whole-number options' ranges: the file is clamped to them, and the config screen's sliders span them. */
	public record Range(int min, int max) {
	}

	public static final Map<String, Range> RANGES = ranges(
		"supplyRadius", 2, 32,
		"maxSiteDistance", 16, 256,
		"quarryReach", 16, 256,
		"fieldReach", 16, 256,
		"treeFarmReach", 16, 256,
		"orchardAreaReach", 16, 256,
		"berryPlotReach", 16, 256,
		"guardRadius", 8, 64,
		"lumberjackRadius", 4, 48,
		"orchardRadius", 4, 48,
		"fisherRadius", 4, 48,
		"explorerRange", 16, 128,
		"partnerRadius", 4, 48,
		"postmanRange", 16, 256,
		"maxWorkersPerVillage", 0, 500,
		"fishermenPerVillage", 1, 20,
		"villagersPerFisherman", 1, 100,
		"workerPathRange", 16, 128,
		"villageRadius", 0, 128,
		"villageHallRadius", 16, 160,
		"villageGrowthCap", 0, 500,
		"maxWorkPace", 100, 400,
		"edictMinDays", 0, 30,
		"arcCooldownDays", 0, 60,
		"arcsAtOnce", 0, 20,
		"lostCaravanChance", 0, 1000,
		"guildsPerRank", 1, 4,
		"colonyCooldownDays", 0, 60,
		"coloniesPerVillage", 0, 16,
		"realmMaxMembers", 2, 32,
		"classRiseDays", 1, 30,
		"classFallDays", 1, 30,
		"classGraceDays", 0, 30,
		"villagerElderDays", 20, 10_000,
		"giftedChance", 0, 1000,
		"seasonDays", 1, 120,
		"treasuryPerWorker", 0, 500,
		"dollarsPerEmerald", 1, 10_000,
		"stewardMaxOpenBuilds", 1, 8,
		"caravanRoadReach", 32, 512,
		"cupEveryFestivals", 1, 8,
		"threatLevel", 0, 3,
		"leaderboardHour", 0, 23,
		"wonderServerLimit", 0, 64,
		"wonderCrew", 1, 32,
		"wonderStanding", 50, 100,
		"denHelperPrice", 1, 64,
		"quarryRegrowth", 0, 2400);

	private static Map<String, Range> ranges(Object... nameMinMax) {
		Map<String, Range> map = new LinkedHashMap<>();
		for (int i = 0; i < nameMinMax.length; i += 3) {
			map.put((String) nameMinMax[i], new Range((Integer) nameMinMax[i + 1], (Integer) nameMinMax[i + 2]));
		}
		return Collections.unmodifiableMap(map);
	}

	void clamp() {
		RANGES.forEach((name, range) -> setInt(name, clamp(getInt(name), range.min(), range.max())));
		List<Integer> caps = new ArrayList<>();
		for (int i = 0; i < DEFAULT_MYTHIC_CAP.size(); i++) {
			Integer v = mythicLegendCap != null && i < mythicLegendCap.size() ? mythicLegendCap.get(i) : null;
			caps.add(v == null ? DEFAULT_MYTHIC_CAP.get(i) : clamp(v, 0, 10));
		}
		mythicLegendCap = caps;
		List<Integer> wonderCapList = new ArrayList<>();
		for (int i = 0; i < DEFAULT_WONDER_CAPS.size(); i++) {
			Integer v = wonderCaps != null && i < wonderCaps.size() ? wonderCaps.get(i) : null;
			wonderCapList.add(v == null ? DEFAULT_WONDER_CAPS.get(i) : clamp(v, 0, 8));
		}
		wonderCaps = wonderCapList;
		List<Integer> prices = new ArrayList<>();
		for (int i = 0; i < io.github.jcondedata.aliveworkplace.work.Retraining.DEFAULT_PRICES.size(); i++) {
			Integer v = retrainPrices != null && i < retrainPrices.size() ? retrainPrices.get(i) : null;
			prices.add(v == null ? io.github.jcondedata.aliveworkplace.work.Retraining.DEFAULT_PRICES.get(i) : clamp(v, 0, 1000));
		}
		retrainPrices = prices;
		if (disabledWonders == null) {
			disabledWonders = new ArrayList<>();
		}
		furnitureMods = io.github.jcondedata.aliveworkplace.furnish.Furnishings.cleanMods(furnitureMods);
		colonyRank = colonyRank(colonyRank).name().toLowerCase(java.util.Locale.ROOT);
		leaderboardDay = leaderboardDay(leaderboardDay).name().toLowerCase(java.util.Locale.ROOT);
		if (disabledArcs == null) {
			disabledArcs = new ArrayList<>();
		}
		Map<String, Boolean> cultures = defaultCultures();
		if (raiderCultures != null) {
			raiderCultures.forEach((id, on) -> {
				if (id != null) {
					cultures.put(id, on == null || on);
				}
			});
		}
		raiderCultures = cultures;
		if (disasters == null) {
			disasters = new java.util.TreeMap<>();
		}
		disasters.replaceAll((id, on) -> on == null || on);
		disasters.keySet().removeIf(java.util.Objects::isNull);
	}

	/** The rank {@code name} means for {@code colonyRank} ("town", "City"...); a City for anything else. */
	static io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank colonyRank(String name) {
		for (io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank rank : io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.values()) {
			if (name != null && rank.name().equalsIgnoreCase(name.trim())) {
				return rank;
			}
		}
		return io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.CITY;
	}

	/** The day {@code name} means for {@code leaderboardDay} ("friday", "Sunday"...); Sunday for anything else. */
	static java.time.DayOfWeek leaderboardDay(String name) {
		for (java.time.DayOfWeek day : java.time.DayOfWeek.values()) {
			if (name != null && day.name().equalsIgnoreCase(name.trim())) {
				return day;
			}
		}
		return java.time.DayOfWeek.SUNDAY;
	}

	/** The cultures the mod ships, all on. */
	private static Map<String, Boolean> defaultCultures() {
		Map<String, Boolean> map = new LinkedHashMap<>();
		map.put("monsters", true);
		map.put("bandits", true);
		return map;
	}

	/**
	 * Lists every culture of {@code ids} in the file in {@code configDir} (a new one is on), so the file names them all;
	 * the file is rewritten only when one was missing.
	 */
	public static void listCultures(Path configDir, java.util.Collection<String> ids) {
		WorkplaceConfig config = load(configDir);
		config.clamp();
		boolean added = false;
		for (String id : ids) {
			added |= config.raiderCultures.putIfAbsent(id, true) == null;
		}
		if (added || !Files.exists(configDir.resolve(FILE))) {
			config.save(configDir);
		}
	}

	/** Lists the disasters {@code ids} in the config file, each on, if they aren't there yet (32.15). */
	public static void listDisasters(Path configDir, java.util.Collection<String> ids) {
		WorkplaceConfig config = load(configDir);
		config.clamp();
		boolean added = false;
		for (String id : ids) {
			added |= config.disasters.putIfAbsent(id, true) == null;
		}
		if (added) {
			config.save(configDir);
		}
	}

	/** The disasters switched off: those set to false in {@code disasters}. */
	public java.util.Set<String> disastersOff() {
		java.util.Set<String> off = new java.util.TreeSet<>();
		disasters.forEach((id, on) -> {
			if (!on) {
				off.add(id);
			}
		});
		return off;
	}

	/** The cultures switched off: those set to false in {@code raiderCultures}, and ours whose own switch is off. */
	public java.util.Set<String> culturesOff() {
		java.util.Set<String> off = new java.util.TreeSet<>();
		raiderCultures.forEach((id, on) -> {
			if (!on) {
				off.add(id);
			}
		});
		if (!villageRaids) {
			off.add("monsters");
		}
		if (!banditCamps) {
			off.add("bandits");
		}
		return java.util.Collections.unmodifiableSet(off);
	}

	/** The Mythic Legend caps by rank (Hamlet, Village, Town, City) when the file doesn't say. */
	public static final List<Integer> DEFAULT_MYTHIC_CAP = List.of(0, 0, 1, 2);
	/** The Wonder caps by rank (Hamlet, Village, Town, City) when the file doesn't say. */
	public static final List<Integer> DEFAULT_WONDER_CAPS = List.of(0, 0, 1, 2);

	/** {@code disabledWonders}' entries as ids: a bare name is ours ({@code great_cathedral}: {@code aliveworkplace:great_cathedral}). */
	static java.util.Set<net.minecraft.resources.ResourceLocation> wonderIds(List<String> names) {
		java.util.Set<net.minecraft.resources.ResourceLocation> out = new java.util.LinkedHashSet<>();
		for (String name : names) {
			if (name == null || name.isBlank()) {
				continue;
			}
			String full = name.trim().contains(":") ? name.trim() : AliveWorkplace.MOD_ID + ":" + name.trim();
			net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(full);
			if (id != null) {
				out.add(id);
			}
		}
		return java.util.Collections.unmodifiableSet(out);
	}

	/**
	 * Every option on the settings screen, in the file's order: a boolean (a switch) or an int (with its
	 * {@link #RANGES range}). Lists ({@code mythicLegendCap}) are in the file only.
	 * Options of an expansion that isn't finished ({@link Expansions}) are left off: they'd do nothing.
	 */
	public static List<String> optionNames() {
		List<String> names = new ArrayList<>();
		for (Field field : WorkplaceConfig.class.getDeclaredFields()) {
			int mods = field.getModifiers();
			boolean onScreen = field.getType() == boolean.class || field.getType() == int.class;
			if (Modifier.isPublic(mods) && !Modifier.isStatic(mods) && onScreen && Expansions.optionLive(field.getName())) {
				names.add(field.getName());
			}
		}
		return names;
	}

	public static boolean isSwitch(String name) {
		return field(name).getType() == boolean.class;
	}

	public boolean getBoolean(String name) {
		try {
			return field(name).getBoolean(this);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	public void setBoolean(String name, boolean value) {
		try {
			field(name).setBoolean(this, value);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	public int getInt(String name) {
		try {
			return field(name).getInt(this);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	public void setInt(String name, int value) {
		try {
			field(name).setInt(this, value);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Field field(String name) {
		try {
			return WorkplaceConfig.class.getField(name);
		} catch (NoSuchFieldException e) {
			throw new IllegalArgumentException("no config option " + name, e);
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	/**
	 * Puts the values into effect. The switches of an expansion that isn't finished stay off whatever the file says
	 * (ROADMAP B76: 0.139.0 wrote them on): each is ANDed with its milestone's flag in {@link Expansions}.
	 */
	public void apply() {
		SupplyContainers.RADIUS = supplyRadius;
		Builders.MAX_SITE_DISTANCE = maxSiteDistance;
		io.github.jcondedata.aliveworkplace.mine.Miners.MAX_DISTANCE = quarryReach;
		io.github.jcondedata.aliveworkplace.farm.Fields.MAX_DISTANCE = fieldReach;
		io.github.jcondedata.aliveworkplace.work.AreaJobs.TREE_FARM_REACH = treeFarmReach;
		io.github.jcondedata.aliveworkplace.work.AreaJobs.ORCHARD_REACH = orchardAreaReach;
		io.github.jcondedata.aliveworkplace.work.AreaJobs.BERRY_PLOT_REACH = berryPlotReach;
		Guards.RADIUS = guardRadius;
		LumberjackWork.RADIUS = lumberjackRadius;
		OrchardWork.RADIUS = orchardRadius;
		FisherWork.RADIUS = fisherRadius;
		Partners.RADIUS = partnerRadius;
		io.github.jcondedata.aliveworkplace.work.Pace.MAX_PERCENT = maxWorkPace;
		io.github.jcondedata.aliveworkplace.hall.Edicts.setEnabled(villageEdicts && Expansions.on(Expansions.M30));
		io.github.jcondedata.aliveworkplace.hall.Edicts.MIN_DAYS = edictMinDays;
		io.github.jcondedata.aliveworkplace.hall.WorkHorn.ENABLED = workHorns && Expansions.on(Expansions.M30);
		io.github.jcondedata.aliveworkplace.hall.VillageBanners.ENABLED = villageBanners && Expansions.on(Expansions.M30);
		io.github.jcondedata.aliveworkplace.hall.Cradles.ENABLED = cradles && Expansions.on(Expansions.M30);
		io.github.jcondedata.aliveworkplace.people.Tonics.ENABLED = tonics && Expansions.on(Expansions.M30);
		io.github.jcondedata.aliveworkplace.hall.Guilds.ENABLED = guilds && Expansions.on(Expansions.M30);
		io.github.jcondedata.aliveworkplace.hall.Guilds.PER_RANK = guildsPerRank;
		io.github.jcondedata.aliveworkplace.people.LifeStages.AGES = villagerAges && Expansions.on(Expansions.M34);
		io.github.jcondedata.aliveworkplace.people.LifeStages.ELDER_DAYS = villagerElderDays;
		io.github.jcondedata.aliveworkplace.people.Mourning.ENABLED = villageMourning; // 36.16
		io.github.jcondedata.aliveworkplace.people.LifeStages.PASSING = elderPassing && Expansions.on(Expansions.M34);
		io.github.jcondedata.aliveworkplace.people.LifeStages.AGELESS = agelessElders && Expansions.on(Expansions.M34);
		io.github.jcondedata.aliveworkplace.people.ClassLook.ENABLED = classOutfits && Expansions.on(Expansions.M34);
		io.github.jcondedata.aliveworkplace.people.Retirement.ENABLED = elderRetirement && Expansions.on(Expansions.M34);
		io.github.jcondedata.aliveworkplace.people.FamilyBook.ENABLED = Expansions.on(Expansions.M34); // the Book of Families (34.21) has no switch of its own
		io.github.jcondedata.aliveworkplace.people.FamilyBook.NAMES = familyNames && Expansions.on(Expansions.M34);
		// Off in gametests (a hall round could move a test's household a class); the class tests turn it on.
		io.github.jcondedata.aliveworkplace.people.SocialClasses.ENABLED = villageClasses && Expansions.on(Expansions.M34) && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.people.SocialClasses.RISE_DAYS = classRiseDays;
		io.github.jcondedata.aliveworkplace.trade.Economy.ENABLED = villageEconomy && Expansions.on(Expansions.M33);
		// Off in gametests (a carter and llamas would walk through the tests next door); the caravan sight tests turn it on.
		io.github.jcondedata.aliveworkplace.hall.CaravanSights.ENABLED = visibleCaravans && Expansions.on(Expansions.M33) && System.getProperty("fabric-api.gametest") == null;
		// Off in gametests (the Trade page's tests count its tabs, the advice tests their tips); the colony tests turn it on.
		io.github.jcondedata.aliveworkplace.colony.Colonies.ENABLED = colonies && Expansions.on(Expansions.M33) && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.colony.Colonies.RANK = colonyRank(colonyRank);
		io.github.jcondedata.aliveworkplace.colony.Colonies.COOLDOWN_DAYS = colonyCooldownDays;
		io.github.jcondedata.aliveworkplace.colony.Colonies.PER_VILLAGE = coloniesPerVillage;
		// Off in gametests (the Trade page's tests count its tabs, the advice tests their tips); the realm tests turn it on.
		io.github.jcondedata.aliveworkplace.realm.Realms.ENABLED = realms && Expansions.on(Expansions.M33) && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.realm.Realms.MAX_MEMBERS = realmMaxMembers;
		io.github.jcondedata.aliveworkplace.guard.Relief.ENABLED = realmRelief && Expansions.on(Expansions.M33);
		// Off in gametests like realms (its tab would turn the routes page into the trade page); the pact tests turn it on.
		io.github.jcondedata.aliveworkplace.realm.Pacts.ENABLED = tradePacts && Expansions.on(Expansions.M33) && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.realm.Pacts.ALLIANCES = alliances && Expansions.on(Expansions.M33);
		// Off in gametests (the Pacts tab's buttons would change other tests); the feud tests turn it on.
		io.github.jcondedata.aliveworkplace.realm.Feuds.ENABLED = feuds && Expansions.on(Expansions.M33) && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.realm.Feuds.WARS = feudWars && Expansions.on(Expansions.M33);
		io.github.jcondedata.aliveworkplace.realm.RealmMaps.ENABLED = realmMap && Expansions.on(Expansions.M33);
		// Off in gametests (its Trade page toggle and its counting would change other tests); the leaderboard tests turn it on.
		io.github.jcondedata.aliveworkplace.realm.Leaderboard.ENABLED = weeklyLeaderboard && Expansions.on(Expansions.M33) && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.realm.Leaderboard.DAY = leaderboardDay(leaderboardDay);
		io.github.jcondedata.aliveworkplace.realm.Leaderboard.HOUR = leaderboardHour;
		io.github.jcondedata.aliveworkplace.people.SocialClasses.FALL_DAYS = classFallDays;
		io.github.jcondedata.aliveworkplace.people.ClassMigration.GRACE_DAYS = classGraceDays;
		// Off in gametests (a hall's round would seed a test's villagers mid-test); the migration tests turn it on.
		io.github.jcondedata.aliveworkplace.people.ClassMigration.AUTO = System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.hall.HarvestIdols.ENABLED = harvestIdols && Expansions.on(Expansions.M30);
		io.github.jcondedata.aliveworkplace.explore.ExplorerWork.RANGE = explorerRange;
		PostOffice.ROUND = postmanRange;
		// Gametests run side by side: workers sharing chests across them would mix the tests up. The village tests
		// switch sharing on in batches of their own.
		io.github.jcondedata.aliveworkplace.work.Village.RADIUS = System.getProperty("fabric-api.gametest") != null ? 0 : villageRadius;
		io.github.jcondedata.aliveworkplace.work.WorkerLimits.MAX_PER_VILLAGE = maxWorkersPerVillage;
		io.github.jcondedata.aliveworkplace.work.RandomJobs.ENABLED = randomStationJobs; // 36.17
		io.github.jcondedata.aliveworkplace.work.WorkerLimits.RADIUS = villageRadius;
		io.github.jcondedata.aliveworkplace.work.WorkerLimits.PATH_RANGE = workerPathRange;
		// Off in gametests (fishermen in the tests beside one would count against it); the cap's tests turn it on.
		io.github.jcondedata.aliveworkplace.fish.FishermanCap.ENABLED = fishermanCap && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.mine.PickaxeCrafting.ENABLED = minerCraftsPickaxe;
		io.github.jcondedata.aliveworkplace.mine.QuarryBuilds.ENABLED = quarryBuild;
		io.github.jcondedata.aliveworkplace.hall.VillageWaystones.ENABLED = villageWaystones;
		io.github.jcondedata.aliveworkplace.mine.QuarryBuilds.PER_DAY = quarryRegrowth;
		io.github.jcondedata.aliveworkplace.fish.FishermanCap.BASE = fishermenPerVillage;
		io.github.jcondedata.aliveworkplace.fish.FishermanCap.PER_VILLAGERS = villagersPerFisherman;
		io.github.jcondedata.aliveworkplace.fish.FishermanCap.RADIUS = villageRadius;
		Money.DOLLARS_PER_EMERALD = dollarsPerEmerald;
		io.github.jcondedata.aliveworkplace.work.Retraining.prices(retrainPrices);
		io.github.jcondedata.aliveworkplace.hall.Treasury.CENTS_PER_WORKER = treasuryPerWorker;
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS = villageHallRadius;
		io.github.jcondedata.aliveworkplace.hall.VillageGrowth.CAP = villageGrowthCap;
		io.github.jcondedata.aliveworkplace.hall.Seasons.DAYS = seasonDays;
		io.github.jcondedata.aliveworkplace.nurse.Nurses.HEALING_MACHINE = nurseHealingMachine && Expansions.on(Expansions.M28);
		io.github.jcondedata.aliveworkplace.berry.BerryBreeders.ENABLED = berryBreeders && Expansions.on(Expansions.M28);
		io.github.jcondedata.aliveworkplace.city.Stewards.ENABLED = steward && Expansions.on(Expansions.M27);
		io.github.jcondedata.aliveworkplace.city.Stewards.MAX_OPEN_BUILDS = stewardMaxOpenBuilds;
		io.github.jcondedata.aliveworkplace.city.StewardDesk.SELF_RUN = stewardSelfRun && Expansions.on(Expansions.M27);
		io.github.jcondedata.aliveworkplace.camp.CampCooks.ENABLED = campCooks && Expansions.on(Expansions.M28);
		io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers.ENABLED = habitatKeepers && Expansions.on(Expansions.M28);
		io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers.SIGHTINGS = habitatSightings && Expansions.on(Expansions.M28);
		io.github.jcondedata.aliveworkplace.habitat.VillageHabitats.ENABLED = villageHabitats && Expansions.on(Expansions.M28);
		io.github.jcondedata.aliveworkplace.world.VillageHouses.POKEMON_JOBS = pokemonVillageHouses && Expansions.on(Expansions.M28);
		io.github.jcondedata.aliveworkplace.jeweller.Jewellers.ENABLED = jewellers && Expansions.on(Expansions.M34);
		io.github.jcondedata.aliveworkplace.gem.GemGrowers.ENABLED = gemGrowers && Expansions.on(Expansions.M28);
		io.github.jcondedata.aliveworkplace.vintner.Vintners.ENABLED = vintners && Expansions.on(Expansions.M34);
		io.github.jcondedata.aliveworkplace.tailor.Tailors.ENABLED = tailors && Expansions.on(Expansions.M34);
		io.github.jcondedata.aliveworkplace.printer.Printers.ENABLED = printers && Expansions.on(Expansions.M34);
		io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers.ENABLED = daycareKeepers && Expansions.on(Expansions.M28);
		io.github.jcondedata.aliveworkplace.cup.Cups.ENABLED = festivalCup && Expansions.on(Expansions.M28);
		io.github.jcondedata.aliveworkplace.cup.Cups.EVERY = cupEveryFestivals;
		// Off in gametests (a trait picked by chance would change a test's numbers); the people tests turn them on.
		io.github.jcondedata.aliveworkplace.people.Names.ENABLED = villagerNames && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.people.Traits.ENABLED = villagerTraits && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.people.Sickness.ENABLED = villagerSickness && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.people.Moods.ENABLED = villagerMoods && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.build.Upkeep.ENABLED = builderRepairs && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.build.Upkeep.THREATS = System.getProperty("fabric-api.gametest") == null; // (32.16a: whatever builderRepairs says)
		io.github.jcondedata.aliveworkplace.build.Upkeep.ASK = repairAsk; // 36.15 (in GameTests too: the repair tests turn it off or answer)
		io.github.jcondedata.aliveworkplace.hall.MarketDays.ENABLED = marketDays && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.guard.VillageRaids.ENABLED = villageRaids && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.guard.BanditCamps.ENABLED = banditCamps && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.threat.HostileSpawns.FORCE = forceThreatSpawns;
		io.github.jcondedata.aliveworkplace.threat.HostileSpawns.FORCE_VILLAGE = forceVillageSpawns;
		// (off in GameTests, where graves would be moved out of other tests' areas; B222GameTests turns it on)
		io.github.jcondedata.aliveworkplace.grave.Graveyards.ENABLED = gravesToGraveyard && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.threat.CampCleanup.RUINS = campRuins;
		// The other cultures' lairs (32.3) come by chance as the bandits' do; each has its switch in raiderCultures.
		io.github.jcondedata.aliveworkplace.threat.Lairs.ENABLED = System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.threat.Sieges.ENABLED = sieges && Expansions.on(Expansions.M32);
		io.github.jcondedata.aliveworkplace.threat.Sieges.DAMAGE = siegeDamage && Expansions.on(Expansions.M32);
		io.github.jcondedata.aliveworkplace.work.FirewardenWork.ENABLED = firewardens && Expansions.on(Expansions.M32);
		io.github.jcondedata.aliveworkplace.work.WellKeeperWork.ENABLED = wellKeepers && Expansions.on(Expansions.M32);
		io.github.jcondedata.aliveworkplace.trainer.RaidHelpers.ENABLED = denHelpers && Expansions.on(Expansions.M38);
		io.github.jcondedata.aliveworkplace.trainer.RaidHelpers.PRICE = denHelperPrice;
		io.github.jcondedata.aliveworkplace.trainer.MegaBracelets.ENABLED = villageMegas && Expansions.on(Expansions.M38);
		io.github.jcondedata.aliveworkplace.craft.CuisineDishes.ENABLED = cuisineDishes && Expansions.on(Expansions.M38);
		io.github.jcondedata.aliveworkplace.farm.BeanFarms.ENABLED = beanFarms && Expansions.on(Expansions.M38);
		io.github.jcondedata.aliveworkplace.craft.FurnicrafterWork.ENABLED = furnicrafters && Expansions.on(Expansions.M38);
		io.github.jcondedata.aliveworkplace.mechanic.MechanicWork.ENABLED = mechanics && Expansions.on(Expansions.M39);
		io.github.jcondedata.aliveworkplace.mechanic.MachineWork.ENABLED = machineWork && Expansions.on(Expansions.M39);
		io.github.jcondedata.aliveworkplace.mechanic.CreateBuilds.ENABLED = createBuilds && Expansions.on(Expansions.M39);
		io.github.jcondedata.aliveworkplace.table.CreateSchematics.ENABLED = createSchematics && Expansions.on(Expansions.M39);
		io.github.jcondedata.aliveworkplace.city.CaravanTrains.ENABLED = villageTrains && Expansions.on(Expansions.M39);
		io.github.jcondedata.aliveworkplace.work.PhysicianWork.ENABLED = physicians && Expansions.on(Expansions.M32);
		// Which cultures may come at all (32.2); the two switches above stay the chance-driven raids' and camps' own.
		io.github.jcondedata.aliveworkplace.threat.Threats.DISABLED = culturesOff();
		io.github.jcondedata.aliveworkplace.threat.Disasters.DISABLED = disastersOff(); // 32.15
		io.github.jcondedata.aliveworkplace.threat.Threats.LEVEL = threatLevel; // 32.21
		io.github.jcondedata.aliveworkplace.hall.Festivals.ENABLED = festivals && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.people.Chatter.ENABLED = villagerChatter && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.people.Couples.ENABLED = villagerCouples && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.hall.Treasury.ENABLED = villageTreasury && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = villageProtection;
		io.github.jcondedata.aliveworkplace.story.Stories.ENABLED = villageQuests;
		// Off in gametests (the Table tests take builds of every kind); the unlock tests turn it on.
		io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks.ENABLED = blueprintUnlocks && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.story.Friendship.ENABLED = friendship && Expansions.on(Expansions.M31);
		io.github.jcondedata.aliveworkplace.story.HeartEvents.ENABLED = heartEvents && Expansions.on(Expansions.M31);
		io.github.jcondedata.aliveworkplace.story.PersonalRequests.ENABLED = personalRequests && Expansions.on(Expansions.M31);
		io.github.jcondedata.aliveworkplace.story.Reputation.ENABLED = reputation && Expansions.on(Expansions.M31);
		io.github.jcondedata.aliveworkplace.story.Reputation.CHAT = titlesInChat && Expansions.on(Expansions.M31);
		io.github.jcondedata.aliveworkplace.story.Arcs.ENABLED = storyArcs && Expansions.on(Expansions.M31);
		io.github.jcondedata.aliveworkplace.bounty.Bounties.ENABLED = bountyBoard && Expansions.on(Expansions.M31);
		io.github.jcondedata.aliveworkplace.bounty.Rogues.ENABLED = rogueBounties && Expansions.on(Expansions.M31);
		io.github.jcondedata.aliveworkplace.bounty.Bounties.AUTO = System.getProperty("fabric-api.gametest") == null;
		// Off in gametests (an arc rolled by chance would start under a test's hall); the arc tests start theirs.
		io.github.jcondedata.aliveworkplace.story.Arcs.AUTO = System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.story.Arcs.COOLDOWN_DAYS = arcCooldownDays;
		io.github.jcondedata.aliveworkplace.story.Arcs.AT_ONCE = arcsAtOnce;
		io.github.jcondedata.aliveworkplace.story.LostCaravanStory.CHANCE = lostCaravanChance;
		io.github.jcondedata.aliveworkplace.story.Arcs.DISABLED = disabledArcs.stream().filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toUnmodifiableSet());
		io.github.jcondedata.aliveworkplace.legend.Legends.ENABLED = legends && Expansions.on(Expansions.M29);
		io.github.jcondedata.aliveworkplace.legend.LegendSites.ENABLED = legendSites && Expansions.on(Expansions.M29);
		// Off in gametests (a round could seize a test's Master); the strange mood tests turn it on.
		io.github.jcondedata.aliveworkplace.legend.StrangeMoods.ENABLED = strangeMoods && Expansions.on(Expansions.M29) && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.legend.Gifted.CHANCE = Expansions.on(Expansions.M29) ? giftedChance : 0;
		// Off in gametests (a round could start or end a strike a test staged); the needs tests turn it on.
		io.github.jcondedata.aliveworkplace.legend.LegendNeeds.ENABLED = legendNeeds && Expansions.on(Expansions.M29) && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.legend.Legends.MYTHIC_CAP = mythicLegendCap.stream().mapToInt(Integer::intValue).toArray();
		// Wonders (35.2), behind M35's gate.
		io.github.jcondedata.aliveworkplace.wonder.Wonders.ENABLED = wonders && Expansions.on(Expansions.M35);
		io.github.jcondedata.aliveworkplace.wonder.Wonders.LEGENDS = wonderLegends && Expansions.on(Expansions.M35);
		io.github.jcondedata.aliveworkplace.wonder.Wonders.CAPS = wonderCaps.stream().mapToInt(Integer::intValue).toArray();
		io.github.jcondedata.aliveworkplace.wonder.Wonders.SERVER_LIMIT = wonderServerLimit;
		io.github.jcondedata.aliveworkplace.wonder.WonderCrews.LIMIT = wonderCrew;
		io.github.jcondedata.aliveworkplace.wonder.WonderUpkeep.STANDING = wonderStanding;
		io.github.jcondedata.aliveworkplace.wonder.Wonders.DISABLED = wonderIds(disabledWonders);
		io.github.jcondedata.aliveworkplace.build.Scaffolds.ENABLED = builderScaffolding && Expansions.on(Expansions.M35);
		// Furnished homes and modded storage (M37), behind its gate.
		io.github.jcondedata.aliveworkplace.furnish.Furnishings.ENABLED = furnishedBuilds && Expansions.on(Expansions.M37);
		io.github.jcondedata.aliveworkplace.furnish.Furnishings.MODS = java.util.List.copyOf(io.github.jcondedata.aliveworkplace.furnish.Furnishings.cleanMods(furnitureMods));
		io.github.jcondedata.aliveworkplace.store.StorageNetworks.ENABLED = moddedStorageNetworks && Expansions.on(Expansions.M37);
		io.github.jcondedata.aliveworkplace.blueprint.ModdedLibrary.ENABLED = moddedLibrary && Expansions.on(Expansions.M37); // 37.12
		// Off in gametests (tickets around every test's workers would keep the test areas loaded); KeepLoaded's tests turn it on.
		io.github.jcondedata.aliveworkplace.work.KeepLoaded.VILLAGES = keepVillagesWorking && System.getProperty("fabric-api.gametest") == null;
		// Off in gametests (a partner walking off mid-test would move the numbers); the show tests turn them on.
		io.github.jcondedata.aliveworkplace.work.PartnerShows.ENABLED = partnerShows && Expansions.on(Expansions.M28) && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.build.Paths.ENABLED = builderPaths && System.getProperty("fabric-api.gametest") == null;
		// Off in gametests (a test's approved road would be built under other tests); the road tests turn it on.
		io.github.jcondedata.aliveworkplace.city.Roads.ENABLED = stewardRoads && Expansions.on(Expansions.M27) && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.city.CaravanRoads.ENABLED = caravanRoads && Expansions.on(Expansions.M27);
		io.github.jcondedata.aliveworkplace.city.CaravanRoads.REACH = caravanRoadReach;
		// Off in gametests (a test's wall would be built under other tests); the wall tests turn it on.
		io.github.jcondedata.aliveworkplace.city.Walls.ENABLED = stewardWalls && Expansions.on(Expansions.M27) && System.getProperty("fabric-api.gametest") == null;
		// Off in gametests (another test's old house would be renewed under it); the renewal tests turn it on.
		io.github.jcondedata.aliveworkplace.city.Renewals.ENABLED = stewardRenewal && Expansions.on(Expansions.M27) && System.getProperty("fabric-api.gametest") == null;
	}
}
