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
 * QA (lane E, config extremes, 2026-10-25; the bug lane merges this with B457's fix): odd files a server owner can leave in {@code config/aliveworkplace.json}, read
 * as the game reads it at start ({@link WorkplaceConfig#load}). The wiki (config-switches.md, "The config file"): a
 * missing value takes its default, an empty value (null) takes its default, and a file that can't be read at all gives
 * the defaults. None of these may stop the game from starting. Only a temporary folder: nothing is applied.
 */
public class QaConfigOddFilesBugTests implements FabricGameTest {

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

}
