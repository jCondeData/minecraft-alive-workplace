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
 * QA (lane E, config extremes), the failing half (bug B411): the server owner's hand-edited {@code config/aliveworkplace.json}, read as the game reads
 * it at start (docs/wiki/config-switches.md, "The config file": a value out of range is clamped to the nearest allowed
 * one, a missing value takes its default, and the file is written back complete). Only {@link WorkplaceConfig#load} and
 * {@link WorkplaceConfig#save} on a temporary folder: nothing here is applied to the running game.
 */
public class QaConfigFileBugTests implements FabricGameTest {

	private static Path dirWith(String json) {
		try {
			Path dir = Files.createTempDirectory("aliveworkplace-qa-config");
			Files.writeString(dir.resolve(WorkplaceConfig.FILE), json);
			return dir;
		} catch (IOException e) {
			throw new GameTestAssertException("no temp dir: " + e);
		}
	}

	private static String read(Path dir) {
		try {
			return Files.readString(dir.resolve(WorkplaceConfig.FILE));
		} catch (IOException e) {
			throw new GameTestAssertException("can't read the file back: " + e);
		}
	}

	/**
	 * One value of the wrong kind (a switch written 0, a whole number written 12.5) can't be read as that setting; like a
	 * missing value it should take its default, and every other setting in the file should be kept, not reset with it
	 * (the file is written back at once, so a reset loses the owner's choices for good).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void oneValueOfTheWrongKindLeavesTheOtherSettingsAlone(GameTestHelper helper) {
		for (String bad : new String[] {"\"festivals\": 0", "\"supplyRadius\": 12.5", "\"threatLevel\": \"high\""}) {
			Path dir = dirWith("{\"maxSiteDistance\": 100, \"seasonDays\": 12, " + bad + ", \"configVersion\": 4}");
			WorkplaceConfig config = WorkplaceConfig.load(dir);
			config.save(dir);
			helper.assertTrue(config.maxSiteDistance == 100 && config.seasonDays == 12, "with " + bad + " in the file, the owner's other "
				+ "settings were reset: maxSiteDistance " + config.maxSiteDistance + " (file said 100), seasonDays " + config.seasonDays
				+ " (file said 12)");
			helper.assertTrue(read(dir).contains("\"maxSiteDistance\": 100"), "with " + bad + ", the file written back lost maxSiteDistance 100");
		}
		helper.succeed();
	}

	/**
	 * The file's own bookkeeping, edited or emptied by hand ({@code configVersion} not a number, a gap in
	 * {@code expansionsOpen}), must never stop the game from starting: the config is read like any other file.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void oddBookkeepingInTheFileNeverStopsTheStart(GameTestHelper helper) {
		for (String odd : new String[] {"\"configVersion\": null", "\"configVersion\": \"four\"", "\"expansionsOpen\": [null]",
			"\"expansionsOpen\": \"M39\"", "\"configVersion\": {}"}) {
			WorkplaceConfig config;
			try {
				config = WorkplaceConfig.load(dirWith("{\"maxSiteDistance\": 100, " + odd + "}"));
			} catch (RuntimeException e) {
				throw new GameTestAssertException("with " + odd + " the config can't be loaded and the game can't start: " + e);
			}
			helper.assertTrue(config != null, "no config with " + odd);
		}
		helper.succeed();
	}
}
