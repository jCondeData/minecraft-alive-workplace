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
	/** How far from their bench a builder takes a build. */
	public int maxSiteDistance = 48;
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
	/** Idle builders repair the buildings they finished when blocks go missing. */
	public boolean builderRepairs = true;
	/** A village with a Village Hall and a Market Square holds a market once a week. */
	public boolean marketDays = true;
	/** Monsters raid bigger villages with a Village Hall at night now and then. */
	public boolean villageRaids = true;
	/** Bandits make camp near villages of Village rank or more now and then, and raid them until their chief falls. */
	public boolean banditCamps = true;
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
	/** Named villagers keep a friendship with each player, shown in hearts (ROADMAP 31.5). Off: no points, no hearts shown; saved friendship stays. */
	public boolean friendship = Expansions.on(Expansions.M31);
	/** Story arcs (31.4) unfold in villages, chapter by chapter. Off: none starts, and a running one ends quietly at its next round. */
	public boolean storyArcs = Expansions.on(Expansions.M31);
	/** Days between two story arcs in one village (also before a village's first). */
	public int arcCooldownDays = 8;
	/** Story arcs running at once on the whole server (side arcs not counted); 0: none. */
	public int arcsAtOnce = 3;
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
	/** Households in villages with a hall climb the class ladder (ROADMAP 34.2). Off: no classes; classes and progress stay saved. */
	public boolean villageClasses = Expansions.on(Expansions.M34);
	/** Dawns running the next class's needs must hold for a household to rise one class. */
	public int classRiseDays = 2;
	/** Dawns running a need of their own class must fail for a household to fall one class. */
	public int classFallDays = 3;
	/** Grown villagers count their days and become elders (ROADMAP 34.19). Off: nobody is an elder, so nobody passes of old age; the day they grew up stays saved. */
	public boolean villagerAges = Expansions.on(Expansions.M34);
	/** Grown days before a villager is an elder. */
	public int villagerElderDays = 120;
	/** An elder passes away in the night after 40 elder days and leaves a grave (owner, 2026-10-06). Off: elders never die of old age. */
	public boolean elderPassing = Expansions.on(Expansions.M34);
	/** An Evergreen Charm makes an elder with good traits ageless (ROADMAP 34.19a). Off: charms are refused; elders already ageless stay so. */
	public boolean agelessElders = Expansions.on(Expansions.M34);
	/** Villagers at a cauldron can be made Vintners with sweet berries, glow berries or an apple (ROADMAP 34.9). Off: no Vintner job, and Vintners already hired stand idle. */
	public boolean vintners = Expansions.on(Expansions.M34);
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
	/** Habitat Keepers tell the village of shiny, rare and Alpha wild Pokémon near their pasture (ROADMAP 28.10). */
	public boolean habitatSightings = Expansions.on(Expansions.M28);
	/** An Expert Habitat Keeper puts one Habitat Block in a finished Habitat Garden, with Cobblemon 1.8 (ROADMAP 28.14). Off: none founded. */
	public boolean villageHabitats = Expansions.on(Expansions.M28);
	/** With Cobblemon, villages grow a Pokémon Center, Camp Kitchen, Berry Nursery, Daycare and Gem Grotto, each with its worker (ROADMAP 28.15). Off: they don't (from the next server start). */
	public boolean pokemonVillageHouses = Expansions.on(Expansions.M28);
	/** With Cobblemon, a village with a hall, a finished Arena and Village rank holds its festivals as a Festival Cup (ROADMAP 28.17). Off: no Cups. */
	public boolean festivalCup = Expansions.on(Expansions.M28);
	/** Which of a host's festivals are Cups: every one (1), every second (2), ... (owner, 28.1a: every festival). */
	public int cupEveryFestivals = 1;
	/** Days in each of the village calendar's four seasons (each has a festival on its middle day). */
	public int seasonDays = 16;
	/** Hundredths of an emerald each worker brings the treasury a day (before wellbeing and rank). */
	public int treasuryPerWorker = 20;
	/** What an emerald price comes to in CobbleDollars (lessons, shops, fares). */
	public int dollarsPerEmerald = 100;
	/**
	 * The file's format (not an option: private, so it's no switch or slider). A file without it was written before
	 * the seasons grew to 16 days (owner, 2026-10-04): its {@code seasonDays} of 8 was the old default, not a choice.
	 */
	@SuppressWarnings("unused")
	private int configVersion = VERSION;
	static final int VERSION = 2;

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
				return parse(Files.readString(file));
			} catch (IOException | JsonParseException e) {
				AliveWorkplace.LOG.warn("Couldn't read {}, using the defaults: {}", file, e.getMessage());
			}
		}
		return new WorkplaceConfig();
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

	/** The config in {@code json} (missing values take their default), clamped to sensible ranges. */
	public static WorkplaceConfig parse(String json) {
		com.google.gson.JsonElement tree = com.google.gson.JsonParser.parseString(json);
		WorkplaceConfig config = tree.isJsonObject() ? GSON.fromJson(tree, WorkplaceConfig.class) : null;
		if (config == null) {
			config = new WorkplaceConfig();
		}
		boolean old = tree.isJsonObject() && !tree.getAsJsonObject().has("configVersion");
		if (old && config.seasonDays == 8) {
			config.seasonDays = 16;
		}
		config.configVersion = VERSION;
		config.clamp();
		return config;
	}

	/** The whole-number options' ranges: the file is clamped to them, and the config screen's sliders span them. */
	public record Range(int min, int max) {
	}

	public static final Map<String, Range> RANGES = ranges(
		"supplyRadius", 2, 32,
		"maxSiteDistance", 16, 256,
		"guardRadius", 8, 64,
		"lumberjackRadius", 4, 48,
		"orchardRadius", 4, 48,
		"fisherRadius", 4, 48,
		"explorerRange", 16, 128,
		"partnerRadius", 4, 48,
		"postmanRange", 16, 256,
		"maxWorkersPerVillage", 0, 500,
		"workerPathRange", 16, 128,
		"villageRadius", 0, 128,
		"villageHallRadius", 16, 160,
		"villageGrowthCap", 0, 500,
		"maxWorkPace", 100, 400,
		"edictMinDays", 0, 30,
		"arcCooldownDays", 0, 60,
		"arcsAtOnce", 0, 20,
		"guildsPerRank", 1, 4,
		"classRiseDays", 1, 30,
		"classFallDays", 1, 30,
		"villagerElderDays", 20, 1000,
		"giftedChance", 0, 1000,
		"seasonDays", 1, 120,
		"treasuryPerWorker", 0, 500,
		"dollarsPerEmerald", 1, 10_000,
		"stewardMaxOpenBuilds", 1, 8,
		"caravanRoadReach", 32, 512,
		"cupEveryFestivals", 1, 8);

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
		if (disabledArcs == null) {
			disabledArcs = new ArrayList<>();
		}
	}

	/** The Mythic Legend caps by rank (Hamlet, Village, Town, City) when the file doesn't say. */
	public static final List<Integer> DEFAULT_MYTHIC_CAP = List.of(0, 0, 1, 2);

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
		io.github.jcondedata.aliveworkplace.people.LifeStages.PASSING = elderPassing && Expansions.on(Expansions.M34);
		io.github.jcondedata.aliveworkplace.people.LifeStages.AGELESS = agelessElders && Expansions.on(Expansions.M34);
		// Off in gametests (a hall round could move a test's household a class); the class tests turn it on.
		io.github.jcondedata.aliveworkplace.people.SocialClasses.ENABLED = villageClasses && Expansions.on(Expansions.M34) && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.people.SocialClasses.RISE_DAYS = classRiseDays;
		io.github.jcondedata.aliveworkplace.people.SocialClasses.FALL_DAYS = classFallDays;
		io.github.jcondedata.aliveworkplace.hall.HarvestIdols.ENABLED = harvestIdols && Expansions.on(Expansions.M30);
		io.github.jcondedata.aliveworkplace.explore.ExplorerWork.RANGE = explorerRange;
		PostOffice.ROUND = postmanRange;
		// Gametests run side by side: workers sharing chests across them would mix the tests up. The village tests
		// switch sharing on in batches of their own.
		io.github.jcondedata.aliveworkplace.work.Village.RADIUS = System.getProperty("fabric-api.gametest") != null ? 0 : villageRadius;
		io.github.jcondedata.aliveworkplace.work.WorkerLimits.MAX_PER_VILLAGE = maxWorkersPerVillage;
		io.github.jcondedata.aliveworkplace.work.WorkerLimits.RADIUS = villageRadius;
		io.github.jcondedata.aliveworkplace.work.WorkerLimits.PATH_RANGE = workerPathRange;
		Money.DOLLARS_PER_EMERALD = dollarsPerEmerald;
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
		io.github.jcondedata.aliveworkplace.gem.GemGrowers.ENABLED = gemGrowers && Expansions.on(Expansions.M28);
		io.github.jcondedata.aliveworkplace.vintner.Vintners.ENABLED = vintners && Expansions.on(Expansions.M34);
		io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers.ENABLED = daycareKeepers && Expansions.on(Expansions.M28);
		io.github.jcondedata.aliveworkplace.cup.Cups.ENABLED = festivalCup && Expansions.on(Expansions.M28);
		io.github.jcondedata.aliveworkplace.cup.Cups.EVERY = cupEveryFestivals;
		// Off in gametests (a trait picked by chance would change a test's numbers); the people tests turn them on.
		io.github.jcondedata.aliveworkplace.people.Names.ENABLED = villagerNames && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.people.Traits.ENABLED = villagerTraits && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.people.Sickness.ENABLED = villagerSickness && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.people.Moods.ENABLED = villagerMoods && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.build.Upkeep.ENABLED = builderRepairs && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.hall.MarketDays.ENABLED = marketDays && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.guard.VillageRaids.ENABLED = villageRaids && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.guard.BanditCamps.ENABLED = banditCamps && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.hall.Festivals.ENABLED = festivals && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.people.Chatter.ENABLED = villagerChatter && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.people.Couples.ENABLED = villagerCouples && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.hall.Treasury.ENABLED = villageTreasury && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = villageProtection;
		io.github.jcondedata.aliveworkplace.story.Stories.ENABLED = villageQuests;
		io.github.jcondedata.aliveworkplace.story.Friendship.ENABLED = friendship && Expansions.on(Expansions.M31);
		io.github.jcondedata.aliveworkplace.story.Arcs.ENABLED = storyArcs && Expansions.on(Expansions.M31);
		// Off in gametests (an arc rolled by chance would start under a test's hall); the arc tests start theirs.
		io.github.jcondedata.aliveworkplace.story.Arcs.AUTO = System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.story.Arcs.COOLDOWN_DAYS = arcCooldownDays;
		io.github.jcondedata.aliveworkplace.story.Arcs.AT_ONCE = arcsAtOnce;
		io.github.jcondedata.aliveworkplace.story.Arcs.DISABLED = disabledArcs.stream().filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toUnmodifiableSet());
		io.github.jcondedata.aliveworkplace.legend.Legends.ENABLED = legends && Expansions.on(Expansions.M29);
		io.github.jcondedata.aliveworkplace.legend.LegendSites.ENABLED = legendSites && Expansions.on(Expansions.M29);
		// Off in gametests (a round could seize a test's Master); the strange mood tests turn it on.
		io.github.jcondedata.aliveworkplace.legend.StrangeMoods.ENABLED = strangeMoods && Expansions.on(Expansions.M29) && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.legend.Gifted.CHANCE = Expansions.on(Expansions.M29) ? giftedChance : 0;
		// Off in gametests (a round could start or end a strike a test staged); the needs tests turn it on.
		io.github.jcondedata.aliveworkplace.legend.LegendNeeds.ENABLED = legendNeeds && Expansions.on(Expansions.M29) && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.legend.Legends.MYTHIC_CAP = mythicLegendCap.stream().mapToInt(Integer::intValue).toArray();
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
