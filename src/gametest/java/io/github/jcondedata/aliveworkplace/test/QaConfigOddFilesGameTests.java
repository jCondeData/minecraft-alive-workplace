package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane E, config extremes, 2026-10-25): odd files a server owner can leave in {@code config/aliveworkplace.json}, read
 * as the game reads it at start ({@link WorkplaceConfig#load}). The wiki (config-switches.md, "The config file"): a
 * missing value takes its default, an empty value (null) takes its default, and a file that can't be read at all gives
 * the defaults. None of these may stop the game from starting. Only a temporary folder: nothing is applied.
 */
public class QaConfigOddFilesGameTests implements FabricGameTest {

	private static Path dirWith(String json) {
		try {
			Path dir = Files.createTempDirectory("aliveworkplace-qa-oddconfig");
			Files.writeString(dir.resolve(WorkplaceConfig.FILE), json);
			return dir;
		} catch (IOException e) {
			throw new GameTestAssertException("no temp dir: " + e);
		}
	}

	/** Loads {@code json} as the start does; a throw here is a server that won't start. */
	private static WorkplaceConfig loadOrFail(String json, String what) {
		try {
			return WorkplaceConfig.load(dirWith(json));
		} catch (RuntimeException e) {
			throw new GameTestAssertException(what + " stops the game from starting: " + e);
		}
	}

	/** An empty file, a file cut short mid-way and a file of only spaces: the defaults, and the game starts. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anEmptyOrCutShortFileGivesTheDefaults(GameTestHelper helper) {
		WorkplaceConfig defaults = new WorkplaceConfig();
		for (String json : new String[] {"", "   \n", "{\"maxSiteDistance\": 100, \"festivals\": fal", "[]", "42"}) {
			WorkplaceConfig config = loadOrFail(json, "the file \"" + json + "\"");
			helper.assertTrue(config.maxSiteDistance == defaults.maxSiteDistance && config.festivals == defaults.festivals,
				"the file \"" + json + "\" should give the defaults: maxSiteDistance " + config.maxSiteDistance);
		}
		helper.succeed();
	}

	/** Empty entries in the number lists and the maps take their defaults (or are dropped), and the start goes on. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void emptyEntriesInListsAndMapsTakeTheirDefaults(GameTestHelper helper) {
		WorkplaceConfig defaults = new WorkplaceConfig();
		WorkplaceConfig config = loadOrFail("{\"wonderCaps\": [null, -3, 99], \"mythicLegendCap\": [null], "
			+ "\"disasters\": {\"aliveworkplace:village_fire\": null}, \"raiderCultures\": {\"bandits\": null}, "
			+ "\"disabledWonders\": null, \"disabledArcs\": null, \"furnitureMods\": null, \"colonyRank\": \"  town \"}", "empty entries");
		helper.assertTrue(config.wonderCaps.size() == defaults.wonderCaps.size(), "wonderCaps should keep its length: " + config.wonderCaps);
		helper.assertTrue(config.wonderCaps.get(0).equals(defaults.wonderCaps.get(0)), "an empty cap takes its default: " + config.wonderCaps);
		if (config.wonderCaps.size() > 2) {
			helper.assertTrue(config.wonderCaps.get(1) == 0 && config.wonderCaps.get(2) == 8, "caps are clamped to 0-8: " + config.wonderCaps);
		}
		helper.assertTrue(config.mythicLegendCap.equals(defaults.mythicLegendCap), "an empty mythic cap takes its default: " + config.mythicLegendCap);
		helper.assertTrue(Boolean.TRUE.equals(config.disasters.get("aliveworkplace:village_fire")), "an empty disaster switch is on: " + config.disasters);
		helper.assertTrue(Boolean.TRUE.equals(config.raiderCultures.get("bandits")), "an empty culture switch is on: " + config.raiderCultures);
		helper.assertTrue(config.disabledWonders != null && config.disabledArcs != null && config.furnitureMods != null,
			"empty lists should come back as lists");
		helper.assertTrue(config.colonyRank.equals("town"), "\"  town \" should read as town, not " + config.colonyRank);
		helper.succeed();
	}

	/** A folder with no file yet: the defaults, and writing them makes a file that reads back the same. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aFirstStartWritesAFileThatReadsBackTheSame(GameTestHelper helper) {
		Path dir;
		try {
			dir = Files.createTempDirectory("aliveworkplace-qa-oddconfig").resolve("not-yet");
		} catch (IOException e) {
			throw new GameTestAssertException("no temp dir: " + e);
		}
		WorkplaceConfig first = WorkplaceConfig.load(dir);
		first.save(dir);
		helper.assertTrue(Files.exists(dir.resolve(WorkplaceConfig.FILE)), "the first start should write the file (and its folder)");
		WorkplaceConfig again = WorkplaceConfig.load(dir);
		for (String name : WorkplaceConfig.RANGES.keySet()) {
			try {
				java.lang.reflect.Field field = WorkplaceConfig.class.getField(name);
				helper.assertTrue(field.getInt(first) == field.getInt(again), name + " changed over a write and read: " + field.getInt(first)
					+ " -> " + field.getInt(again));
			} catch (ReflectiveOperationException e) {
				throw new GameTestAssertException("no option " + name + ": " + e);
			}
		}
		helper.succeed();
	}
}
