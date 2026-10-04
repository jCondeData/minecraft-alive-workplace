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
	/** A Village Hall's owner may protect the village from other players (a setting on the hall, off until they turn it on). */
	public boolean villageProtection = true;
	/** Pokémon pastured by a workstation are seen helping at work (with Cobblemon): they carry, water, spark... */
	public boolean partnerShows = true;
	/** A nurse at Cobblemon's Healing Machine heals your team in it, and keeps it charged while on shift (ROADMAP 28.7). */
	public boolean nurseHealingMachine = true;
	/**
	 * How fast every bonus together can make a worker, in percent of the usual pace (partners, a well-kept village,
	 * research, traits, mood, edicts...). Sickness and bad moods still slow them after that; their level doesn't count.
	 */
	public int maxWorkPace = 200;
	/** Villages' owners proclaim edicts at the hall (off: none can be, and those in force do nothing but stay saved). */
	public boolean villageEdicts = true;
	/** Days an edict stays in force before it can be lifted. */
	public int edictMinDays = 3;
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
		"seasonDays", 1, 120,
		"treasuryPerWorker", 0, 500,
		"dollarsPerEmerald", 1, 10_000);

	private static Map<String, Range> ranges(Object... nameMinMax) {
		Map<String, Range> map = new LinkedHashMap<>();
		for (int i = 0; i < nameMinMax.length; i += 3) {
			map.put((String) nameMinMax[i], new Range((Integer) nameMinMax[i + 1], (Integer) nameMinMax[i + 2]));
		}
		return Collections.unmodifiableMap(map);
	}

	void clamp() {
		RANGES.forEach((name, range) -> setInt(name, clamp(getInt(name), range.min(), range.max())));
	}

	/** Every option's name, in the file's order: a boolean (a switch) or an int (with its {@link #RANGES range}). */
	public static List<String> optionNames() {
		List<String> names = new ArrayList<>();
		for (Field field : WorkplaceConfig.class.getDeclaredFields()) {
			int mods = field.getModifiers();
			if (Modifier.isPublic(mods) && !Modifier.isStatic(mods)) {
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

	/** Puts the values into effect. */
	public void apply() {
		SupplyContainers.RADIUS = supplyRadius;
		Builders.MAX_SITE_DISTANCE = maxSiteDistance;
		Guards.RADIUS = guardRadius;
		LumberjackWork.RADIUS = lumberjackRadius;
		OrchardWork.RADIUS = orchardRadius;
		FisherWork.RADIUS = fisherRadius;
		Partners.RADIUS = partnerRadius;
		io.github.jcondedata.aliveworkplace.work.Pace.MAX_PERCENT = maxWorkPace;
		io.github.jcondedata.aliveworkplace.hall.Edicts.setEnabled(villageEdicts);
		io.github.jcondedata.aliveworkplace.hall.Edicts.MIN_DAYS = edictMinDays;
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
		io.github.jcondedata.aliveworkplace.nurse.Nurses.HEALING_MACHINE = nurseHealingMachine;
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
		// Off in gametests (tickets around every test's workers would keep the test areas loaded); KeepLoaded's tests turn it on.
		io.github.jcondedata.aliveworkplace.work.KeepLoaded.VILLAGES = keepVillagesWorking && System.getProperty("fabric-api.gametest") == null;
		// Off in gametests (a partner walking off mid-test would move the numbers); the show tests turn them on.
		io.github.jcondedata.aliveworkplace.work.PartnerShows.ENABLED = partnerShows && System.getProperty("fabric-api.gametest") == null;
		io.github.jcondedata.aliveworkplace.build.Paths.ENABLED = builderPaths && System.getProperty("fabric-api.gametest") == null;
	}
}
