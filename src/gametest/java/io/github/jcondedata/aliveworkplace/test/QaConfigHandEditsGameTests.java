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
 * QA (lane E, config extremes), after B411's fix: more of the ways a server owner edits {@code config/aliveworkplace.json}
 * by hand, read as the game reads it at start. From the wiki's promise (docs/wiki/config-switches.md, "The config
 * file"): a value out of range is clamped to the nearest allowed one; a value of the wrong kind takes its default alone
 * and every other value is kept and written back; a file that can't be read at all (empty, cut short, not a
 * {@code {...}} object) gives the defaults but is first kept as aliveworkplace.json.bad; nothing stops the start. Only
 * {@link WorkplaceConfig#load} and {@link WorkplaceConfig#save} on a temporary folder: nothing is applied to the game.
 */
public class QaConfigHandEditsGameTests implements FabricGameTest {

	private static Path dirWith(String json) {
		try {
			Path dir = Files.createTempDirectory("aliveworkplace-qa-handedit");
			Files.writeString(dir.resolve(WorkplaceConfig.FILE), json);
			return dir;
		} catch (IOException e) {
			throw new GameTestAssertException("no temp dir: " + e);
		}
	}

	private static String read(Path file) {
		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new GameTestAssertException("can't read " + file + ": " + e);
		}
	}

	/** Loads the way the game starts: read, then the file written back complete. */
	private static WorkplaceConfig start(Path dir) {
		try {
			WorkplaceConfig config = WorkplaceConfig.load(dir);
			config.save(dir);
			return config;
		} catch (RuntimeException e) {
			throw new GameTestAssertException("the config couldn't be loaded, so the game couldn't start: " + e);
		}
	}

	/**
	 * A list of the wrong kind in one entry ({@code "wonderCaps": [1, "lots", 2]}), a map with a value of the wrong kind
	 * and a list where a switch goes cost only that one option: the owner's other settings stay.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aBadListOrMapCostsOnlyItself(GameTestHelper helper) {
		for (String bad : new String[] {"\"wonderCaps\": [1, \"lots\", 2]", "\"raiderCultures\": {\"aliveworkplace:bandits\": [1]}",
			"\"festivals\": [true]", "\"disabledWonders\": {\"a\": 1}", "\"retrainPrices\": \"cheap\""}) {
			Path dir = dirWith("{\"maxSiteDistance\": 100, " + bad + ", \"seasonDays\": 12}");
			WorkplaceConfig config = start(dir);
			helper.assertTrue(config.maxSiteDistance == 100 && config.seasonDays == 12, "with " + bad + " the owner's other settings were "
				+ "reset: maxSiteDistance " + config.maxSiteDistance + ", seasonDays " + config.seasonDays);
			String written = read(dir.resolve(WorkplaceConfig.FILE));
			helper.assertTrue(written.contains("\"maxSiteDistance\": 100") && written.contains("\"seasonDays\": 12"),
				"with " + bad + " the file written back lost the owner's settings");
		}
		helper.succeed();
	}

	/**
	 * Read twice, the way two restarts read it: a file with a value of the wrong kind is written back with that value's
	 * default, and the second start reads exactly what the first one did (nothing drifts, nothing is reset later).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSecondStartReadsWhatTheFirstWrote(GameTestHelper helper) {
		Path dir = dirWith("{\"maxSiteDistance\": 100, \"festivals\": 0, \"seasonDays\": 12, \"threatLevel\": \"high\"}");
		start(dir);
		String first = read(dir.resolve(WorkplaceConfig.FILE));
		WorkplaceConfig second = start(dir);
		String again = read(dir.resolve(WorkplaceConfig.FILE));
		helper.assertTrue(first.equals(again), "the second start wrote a different file than the first");
		helper.assertTrue(second.maxSiteDistance == 100 && second.seasonDays == 12, "the second start lost the owner's settings");
		helper.assertTrue(!Files.exists(dir.resolve(WorkplaceConfig.BAD_FILE)), "a readable file was copied aside");
		helper.succeed();
	}

	/**
	 * Comments, a trailing comma and keys in any order are how people hand-edit JSON: a file the game can read anyway
	 * keeps every setting in it.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void commentsAndATrailingCommaKeepTheSettings(GameTestHelper helper) {
		Path dir = dirWith("{\n  // my server\n  \"seasonDays\": 12,\n  \"maxSiteDistance\": 100,\n}\n");
		WorkplaceConfig config = start(dir);
		boolean kept = config.maxSiteDistance == 100 && config.seasonDays == 12;
		boolean aside = Files.exists(dir.resolve(WorkplaceConfig.BAD_FILE));
		// Either the file is read as meant, or (if it counts as unreadable) the owner's text is kept aside: never lost.
		helper.assertTrue(kept || aside, "a commented file with a trailing comma lost the owner's settings and wasn't kept aside");
		helper.succeed();
	}

	/**
	 * A file saved by an editor that puts a byte-order mark first (Windows Notepad's "UTF-8 with BOM") is the owner's
	 * settings all the same: they're read, or at worst the file is kept aside, never silently lost.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aByteOrderMarkFirstKeepsTheSettings(GameTestHelper helper) {
		Path dir = dirWith("﻿{\"maxSiteDistance\": 100, \"seasonDays\": 12}");
		WorkplaceConfig config = start(dir);
		boolean kept = config.maxSiteDistance == 100 && config.seasonDays == 12;
		helper.assertTrue(kept || Files.exists(dir.resolve(WorkplaceConfig.BAD_FILE)), "a file starting with a byte-order mark "
			+ "lost the owner's settings (maxSiteDistance " + config.maxSiteDistance + ") and wasn't kept aside");
		helper.succeed();
	}

	/** An old aliveworkplace.json.bad from an earlier start doesn't stop a new unreadable file from being kept. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aNewUnreadableFileReplacesAnOldKeptCopy(GameTestHelper helper) {
		Path dir = dirWith("{\"maxSiteDistance\": 100,,");
		try {
			Files.writeString(dir.resolve(WorkplaceConfig.BAD_FILE), "old");
		} catch (IOException e) {
			throw new GameTestAssertException("can't write the old copy: " + e);
		}
		start(dir);
		helper.assertTrue(read(dir.resolve(WorkplaceConfig.BAD_FILE)).equals("{\"maxSiteDistance\": 100,,"),
			"the newest unreadable file wasn't the one kept aside");
		helper.succeed();
	}
}
