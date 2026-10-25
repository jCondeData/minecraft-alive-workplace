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
 * QA (lane E, config extremes, 2026-10-25; B457): odd files a server owner can leave in {@code config/aliveworkplace.json}, read
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

	/** {@code "configVersion": null} is an empty value like any other: the rest of the file is read, nothing throws. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anEmptyFormatNumberIsAMissingOne(GameTestHelper helper) {
		WorkplaceConfig config = loadOrFail("{\"configVersion\": null, \"maxSiteDistance\": 100}", "\"configVersion\": null");
		helper.assertTrue(config.maxSiteDistance == 100, "the rest of the file should be kept: maxSiteDistance " + config.maxSiteDistance);
		helper.succeed();
	}

	/** An empty entry in the list of open expansions ({@code [null]}) is skipped; the rest of the file is read. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anEmptyEntryInTheOpenExpansionsIsSkipped(GameTestHelper helper) {
		WorkplaceConfig config = loadOrFail("{\"expansionsOpen\": [null], \"configVersion\": 4, \"maxSiteDistance\": 100}",
			"\"expansionsOpen\": [null]");
		helper.assertTrue(config.maxSiteDistance == 100, "the rest of the file should be kept: maxSiteDistance " + config.maxSiteDistance);
		helper.succeed();
	}

	/** A format number written as text that isn't a number ({@code "four"}) is a missing one; the rest of the file is read. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aFormatNumberInWordsIsAMissingOne(GameTestHelper helper) {
		WorkplaceConfig config = loadOrFail("{\"configVersion\": \"four\", \"maxSiteDistance\": 100}", "\"configVersion\": \"four\"");
		helper.assertTrue(config.maxSiteDistance == 100, "the rest of the file should be kept: maxSiteDistance " + config.maxSiteDistance);
		helper.succeed();
	}

	/** A list or an object where the format number goes is a missing one; the rest of the file is read. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aFormatNumberThatIsAListOrAnObjectIsAMissingOne(GameTestHelper helper) {
		for (String odd : new String[] {"[4]", "{}"}) {
			WorkplaceConfig config = loadOrFail("{\"configVersion\": " + odd + ", \"maxSiteDistance\": 100}", "\"configVersion\": " + odd);
			helper.assertTrue(config.maxSiteDistance == 100, "configVersion " + odd + ": the rest of the file should be kept: maxSiteDistance "
				+ config.maxSiteDistance);
		}
		helper.succeed();
	}

	/** A format number in quotes ({@code "4"}) still counts: a format-4 file's own elder age is kept, not moved. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aFormatNumberInQuotesStillCounts(GameTestHelper helper) {
		WorkplaceConfig config = loadOrFail("{\"configVersion\": \"4\", \"villagerElderDays\": 120}", "\"configVersion\": \"4\"");
		helper.assertTrue(config.villagerElderDays == 120, "a format-4 file's 120 is a choice and should stay: " + config.villagerElderDays);
		helper.succeed();
	}

	/** Entries of the open expansions that aren't names (a list, an object, a mix with a real name) are skipped. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void oddEntriesInTheOpenExpansionsAreSkipped(GameTestHelper helper) {
		WorkplaceConfig config = loadOrFail("{\"expansionsOpen\": [[], {}, null, \"34\"], \"configVersion\": 4, \"maxSiteDistance\": 100}",
			"odd entries in \"expansionsOpen\"");
		helper.assertTrue(config.maxSiteDistance == 100, "the rest of the file should be kept: maxSiteDistance " + config.maxSiteDistance);
		helper.succeed();
	}

	/** Open expansions that aren't a list at all (a name, an object, a number) are as if the file had none. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void openExpansionsThatAreNoListAreAMissingOne(GameTestHelper helper) {
		for (String odd : new String[] {"\"34\"", "{}", "7"}) {
			WorkplaceConfig config = loadOrFail("{\"expansionsOpen\": " + odd + ", \"configVersion\": 4, \"maxSiteDistance\": 100}",
				"\"expansionsOpen\": " + odd);
			helper.assertTrue(config.maxSiteDistance == 100, "expansionsOpen " + odd + ": the rest of the file should be kept: maxSiteDistance "
				+ config.maxSiteDistance);
		}
		helper.succeed();
	}
}
