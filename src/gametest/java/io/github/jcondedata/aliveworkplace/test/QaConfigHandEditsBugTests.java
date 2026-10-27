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
 * QA (lane E, config extremes), failing on main 6e82b3d (bug filed with these tests): two hand edits of
 * {@code config/aliveworkplace.json} the wiki (docs/wiki/config-switches.md, "The config file") promises to handle and
 * the game doesn't. Only {@link WorkplaceConfig#load} and {@link WorkplaceConfig#save} on a temporary folder.
 */
public class QaConfigHandEditsBugTests implements FabricGameTest {

	private static Path dirWith(String json) {
		try {
			Path dir = Files.createTempDirectory("aliveworkplace-qa-handedit-bug");
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
	 * A file the JSON reader accepts but that isn't a {@code {...}} object ({@code [...]}, a bare word, a number) holds no
	 * settings: it gives the defaults and, as the wiki promises for a file that is "not a {...} object", is first kept
	 * as aliveworkplace.json.bad before the defaults are written over it.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aFileThatIsNoObjectIsKeptAside(GameTestHelper helper) {
		for (String odd : new String[] {"[{\"maxSiteDistance\": 100}]", "\"maxSiteDistance 100\"", "100"}) {
			Path dir = dirWith(odd);
			WorkplaceConfig config = start(dir);
			helper.assertTrue(config.maxSiteDistance == new WorkplaceConfig().maxSiteDistance, "a file " + odd + " should give the defaults");
			Path kept = dir.resolve(WorkplaceConfig.BAD_FILE);
			helper.assertTrue(Files.exists(kept), "the file " + odd + " (not a {...} object) was written over with the defaults and "
				+ "not kept as " + WorkplaceConfig.BAD_FILE);
			helper.assertTrue(read(kept).equals(odd), "the kept copy isn't the owner's file " + odd);
		}
		helper.succeed();
	}

	/**
	 * A whole number past what the game counts in is still "a value out of range": clamped to the nearest allowed one,
	 * as 100000 is. Today it wraps round first: 3000000000 (meant as "as far as it goes") becomes the least allowed,
	 * 4294967396 becomes exactly 100, and 1e12 the least.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aHugeNumberIsClampedLikeAnyOutOfRangeOne(GameTestHelper helper) {
		WorkplaceConfig.Range range = WorkplaceConfig.RANGES.get("maxSiteDistance");
		helper.assertTrue(range != null, "maxSiteDistance has no range");
		int clampedHigh = start(dirWith("{\"maxSiteDistance\": 100000}")).maxSiteDistance;
		helper.assertTrue(clampedHigh == range.max(), "100000 should be clamped to " + range.max() + ", was " + clampedHigh);
		for (String huge : new String[] {"3000000000", "4294967396", "99999999999", "1e12"}) {
			WorkplaceConfig config = start(dirWith("{\"maxSiteDistance\": " + huge + ", \"seasonDays\": 12}"));
			helper.assertTrue(config.seasonDays == 12, "with maxSiteDistance " + huge + " the other settings were reset");
			helper.assertTrue(config.maxSiteDistance == range.max(), "maxSiteDistance " + huge + " should be clamped to the most allowed, "
				+ range.max() + ", like 100000 is; it took " + config.maxSiteDistance);
		}
		for (String huge : new String[] {"-3000000000", "-99999999999"}) {
			WorkplaceConfig low = start(dirWith("{\"maxSiteDistance\": " + huge + "}"));
			helper.assertTrue(low.maxSiteDistance == range.min(), "maxSiteDistance " + huge + " should be clamped to the least allowed, "
				+ range.min() + "; it took " + low.maxSiteDistance);
		}
		helper.succeed();
	}

	/**
	 * Every whole-number option, quoted or not, and an entry of a list of whole numbers: a number past what the game
	 * counts in is clamped to the nearest allowed value, never wrapped round (B477).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyWholeNumberOptionClampsAHugeNumber(GameTestHelper helper) {
		StringBuilder high = new StringBuilder("{\"configVersion\": 5");
		StringBuilder low = new StringBuilder("{\"configVersion\": 5");
		int i = 0;
		for (String name : WorkplaceConfig.RANGES.keySet()) {
			String huge = i++ % 2 == 0 ? "3000000000" : "\"3000000000\"";
			high.append(", \"").append(name).append("\": ").append(huge);
			low.append(", \"").append(name).append("\": -").append("4294967396");
		}
		high.append(", \"retrainPrices\": [3000000000, 5]}");
		low.append("}");
		WorkplaceConfig h = start(dirWith(high.toString()));
		WorkplaceConfig l = start(dirWith(low.toString()));
		WorkplaceConfig.RANGES.forEach((name, range) -> {
			helper.assertTrue(h.getInt(name) == range.max(), name + " 3000000000 should be clamped to " + range.max() + ", was " + h.getInt(name));
			helper.assertTrue(l.getInt(name) == range.min(), name + " -4294967396 should be clamped to " + range.min() + ", was " + l.getInt(name));
		});
		helper.assertTrue(h.retrainPrices.get(0) == 1000 && h.retrainPrices.get(1) == 5,
			"a retraining price of 3000000000 should be clamped to 1000, the rest kept: " + h.retrainPrices);
		helper.succeed();
	}
}
