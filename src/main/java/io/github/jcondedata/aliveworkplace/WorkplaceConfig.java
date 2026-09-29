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
import java.nio.file.Files;
import java.nio.file.Path;

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
	/** Workers whose workstations are this close together are one village and share their chests. */
	public int villageRadius = 48;
	/** What an emerald price comes to in CobbleDollars (lessons, shops, fares). */
	public int dollarsPerEmerald = 100;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final String FILE = "aliveworkplace.json";

	/** Reads the config (or the defaults), clamps it, writes it back complete and applies it. */
	public static WorkplaceConfig loadAndApply(Path configDir) {
		Path file = configDir.resolve(FILE);
		WorkplaceConfig config = new WorkplaceConfig();
		if (Files.exists(file)) {
			try {
				config = parse(Files.readString(file));
			} catch (IOException | JsonParseException e) {
				AliveWorkplace.LOG.warn("Couldn't read {}, using the defaults: {}", file, e.getMessage());
			}
		}
		try {
			Files.createDirectories(configDir);
			Files.writeString(file, GSON.toJson(config));
		} catch (IOException e) {
			AliveWorkplace.LOG.warn("Couldn't write {}: {}", file, e.getMessage());
		}
		config.apply();
		return config;
	}

	/** The config in {@code json} (missing values take their default), clamped to sensible ranges. */
	public static WorkplaceConfig parse(String json) {
		WorkplaceConfig config = GSON.fromJson(json, WorkplaceConfig.class);
		if (config == null) {
			config = new WorkplaceConfig();
		}
		config.clamp();
		return config;
	}

	void clamp() {
		supplyRadius = clamp(supplyRadius, 2, 32);
		maxSiteDistance = clamp(maxSiteDistance, 16, 256);
		guardRadius = clamp(guardRadius, 8, 64);
		lumberjackRadius = clamp(lumberjackRadius, 4, 48);
		orchardRadius = clamp(orchardRadius, 4, 48);
		fisherRadius = clamp(fisherRadius, 4, 48);
		partnerRadius = clamp(partnerRadius, 4, 48);
		explorerRange = clamp(explorerRange, 16, 128);
		postmanRange = clamp(postmanRange, 16, 256);
		villageRadius = clamp(villageRadius, 0, 128);
		dollarsPerEmerald = clamp(dollarsPerEmerald, 1, 10_000);
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
		io.github.jcondedata.aliveworkplace.explore.ExplorerWork.RANGE = explorerRange;
		PostOffice.ROUND = postmanRange;
		// Gametests run side by side: workers sharing chests across them would mix the tests up. The village tests
		// switch sharing on in batches of their own.
		io.github.jcondedata.aliveworkplace.work.Village.RADIUS = System.getProperty("fabric-api.gametest") != null ? 0 : villageRadius;
		Money.DOLLARS_PER_EMERALD = dollarsPerEmerald;
	}
}
