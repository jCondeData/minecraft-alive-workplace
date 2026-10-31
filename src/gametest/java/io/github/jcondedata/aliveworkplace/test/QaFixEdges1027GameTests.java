package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintNames;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * QA (lane E, 2026-10-27) of the fixes for B578 and B477, each boundary from both sides, from the bugs' Expected and the
 * wiki (config-switches.md, "The config file"), not from the code.
 */
public class QaFixEdges1027GameTests implements FabricGameTest {

	/**
	 * B578: a build name is cut to 128 chars on a code point. Just inside the cap an emoji ending at char 128 is kept
	 * whole; one char past, the name is cut after it; an emoji straddling the cap goes whole; a name of plain letters is
	 * cut at exactly 128. Every name kept is the start of what was typed and holds no half emoji.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aBuildNameIsCutOnACodePointOnBothSidesOfTheCap(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_fix_edges/name_cut");
		String castle = "🏰";
		String[][] cases = {
			{"a".repeat(126) + castle, "a".repeat(126) + castle},
			{"a".repeat(126) + castle + "b", "a".repeat(126) + castle},
			{"a".repeat(127) + castle, "a".repeat(127)},
			{"a".repeat(127) + castle + " Castle", "a".repeat(127)},
			{"a".repeat(128) + castle, "a".repeat(128)},
			{"a".repeat(129), "a".repeat(128)},
			{castle.repeat(64), castle.repeat(64)},
			{castle.repeat(65), castle.repeat(64)},
		};
		try {
			for (String[] c : cases) {
				BlueprintNames.put(server, id, c[0]);
				String kept = BlueprintNames.get(id).orElseThrow(() -> new GameTestAssertException("no name kept for a name of " + c[0].length() + " chars"));
				helper.assertTrue(kept.length() <= 128, "a name of " + kept.length() + " chars was kept, past the cap of 128");
				for (int i = 0; i < kept.length(); i++) {
					char ch = kept.charAt(i);
					boolean paired = Character.isHighSurrogate(ch) ? i + 1 < kept.length() && Character.isLowSurrogate(kept.charAt(i + 1))
						: !Character.isLowSurrogate(ch) || i > 0 && Character.isHighSurrogate(kept.charAt(i - 1));
					helper.assertTrue(paired, "the name kept from one of " + c[0].length() + " chars holds half an emoji at char " + i);
				}
				helper.assertTrue(kept.equals(c[1]), "a name of " + c[0].length() + " chars was kept as one of " + kept.length()
					+ " chars, expected " + c[1].length());
			}
		} finally {
			BlueprintNames.put(server, id, "");
		}
		helper.succeed();
	}

	/**
	 * B477: the edges of what the game counts in. 2147483647 is the largest whole number it holds and 2147483648 the first
	 * past it; both, and anything past, give the most allowed. -2147483648 and -2147483649 give the least. The other
	 * settings in the file stay as written.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void numbersAtTheEdgeOfWhatTheGameCountsAreClamped(GameTestHelper helper) {
		WorkplaceConfig.Range range = WorkplaceConfig.RANGES.get("maxSiteDistance");
		helper.assertTrue(range != null, "maxSiteDistance has no range");
		for (String high : new String[] {"2147483647", "2147483648", "\"2147483648\"", "9223372036854775807", "9223372036854775808"}) {
			WorkplaceConfig c = start(dirWith("{\"maxSiteDistance\": " + high + ", \"seasonDays\": 12}"));
			helper.assertTrue(c.maxSiteDistance == range.max(), "maxSiteDistance " + high + " should give the most allowed, " + range.max()
				+ "; it took " + c.maxSiteDistance);
			helper.assertTrue(c.seasonDays == 12, "with maxSiteDistance " + high + " seasonDays wasn't kept: " + c.seasonDays);
		}
		for (String low : new String[] {"-2147483648", "-2147483649", "-9223372036854775809"}) {
			WorkplaceConfig c = start(dirWith("{\"maxSiteDistance\": " + low + ", \"seasonDays\": 12}"));
			helper.assertTrue(c.maxSiteDistance == range.min(), "maxSiteDistance " + low + " should give the least allowed, " + range.min()
				+ "; it took " + c.maxSiteDistance);
			helper.assertTrue(c.seasonDays == 12, "with maxSiteDistance " + low + " seasonDays wasn't kept: " + c.seasonDays);
		}
		helper.succeed();
	}

	/**
	 * B477: the wiki's "a file that can't be read at all (empty, cut short, not a {...} object)" is kept as
	 * aliveworkplace.json.bad before the defaults are written: an empty file, one of only spaces, null and true too.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anEmptyOrNullFileIsKeptAsideToo(GameTestHelper helper) {
		for (String odd : new String[] {"", "   \n", "null", "true", "{\"maxSiteDistance\": 100"}) {
			Path dir = dirWith(odd);
			WorkplaceConfig config = start(dir);
			String shown = "\"" + odd.replace("\n", "\\n") + "\"";
			helper.assertTrue(config.maxSiteDistance == new WorkplaceConfig().maxSiteDistance, "a file " + shown + " should give the defaults");
			Path kept = dir.resolve(WorkplaceConfig.BAD_FILE);
			helper.assertTrue(Files.exists(kept), "the file " + shown + " was written over with the defaults and not kept as " + WorkplaceConfig.BAD_FILE);
			helper.assertTrue(read(kept).equals(odd), "the kept copy isn't the owner's file " + shown);
			helper.assertTrue(read(dir.resolve(WorkplaceConfig.FILE)).contains("\"maxSiteDistance\""), "the file " + shown
				+ " wasn't written back complete with the defaults");
		}
		helper.succeed();
	}

	private static Path dirWith(String json) {
		try {
			Path dir = Files.createTempDirectory("aliveworkplace-qa-fix-edges");
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
}
