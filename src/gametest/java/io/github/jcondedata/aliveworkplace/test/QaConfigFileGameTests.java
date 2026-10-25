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
 * QA (lane E, config extremes): the server owner's hand-edited {@code config/aliveworkplace.json}, read as the game reads
 * it at start (docs/wiki/config-switches.md, "The config file": a value out of range is clamped to the nearest allowed
 * one, a missing value takes its default, and the file is written back complete). Only {@link WorkplaceConfig#load} and
 * {@link WorkplaceConfig#save} on a temporary folder: nothing here is applied to the running game.
 */
public class QaConfigFileGameTests implements FabricGameTest {

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

	/** The owner's own choices, a whole file of them, survive the start's read and write-back unchanged. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theOwnersChoicesSurviveTheStartsReadAndWriteBack(GameTestHelper helper) {
		Path dir = dirWith("{\"maxSiteDistance\": 100, \"threatLevel\": 0, \"festivals\": false, \"supplyRadius\": 32, \"configVersion\": 4}");
		WorkplaceConfig config = WorkplaceConfig.load(dir);
		config.save(dir);
		WorkplaceConfig again = WorkplaceConfig.load(dir);
		helper.assertTrue(again.maxSiteDistance == 100 && again.threatLevel == 0 && !again.festivals && again.supplyRadius == 32,
			"the owner's settings should come back after a start: maxSiteDistance " + again.maxSiteDistance + ", threatLevel "
				+ again.threatLevel + ", festivals " + again.festivals + ", supplyRadius " + again.supplyRadius);
		helper.assertTrue(read(dir).contains("\"villageProtection\""), "the file should be written back complete");
		helper.succeed();
	}

	/**
	 * Values a person types that are close enough to read: a number in quotes and a switch written "false" in quotes
	 * are taken as meant, and the rest of the file with them.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aQuotedNumberAndAQuotedSwitchAreReadAsMeant(GameTestHelper helper) {
		WorkplaceConfig config = WorkplaceConfig.load(dirWith("{\"threatLevel\": \"1\", \"festivals\": \"false\", \"maxSiteDistance\": 100}"));
		helper.assertTrue(config.threatLevel == 1, "\"1\" in quotes should read as 1, not " + config.threatLevel);
		helper.assertTrue(!config.festivals, "\"false\" in quotes should switch festivals off");
		helper.assertTrue(config.maxSiteDistance == 100, "the rest of the file should be kept: maxSiteDistance " + config.maxSiteDistance);
		helper.succeed();
	}

	/** A value left empty ({@code null}) is a missing value: it takes its default, and the others are kept. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aNullValueTakesItsDefaultAndTheRestAreKept(GameTestHelper helper) {
		WorkplaceConfig config = WorkplaceConfig.load(dirWith("{\"supplyRadius\": null, \"festivals\": null, \"maxSiteDistance\": 100}"));
		WorkplaceConfig defaults = new WorkplaceConfig();
		helper.assertTrue(config.supplyRadius == defaults.supplyRadius && config.festivals == defaults.festivals,
			"empty values should take their defaults: supplyRadius " + config.supplyRadius + ", festivals " + config.festivals);
		helper.assertTrue(config.maxSiteDistance == 100, "the rest of the file should be kept: maxSiteDistance " + config.maxSiteDistance);
		helper.succeed();
	}
}
