package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA 2026-11-07 (d), B1002's gap. docs/wiki/blueprint-library.md: an unlocked Wonder is "named and previewed, never
 * handed out; Wonders are raised from their plan", and with the {@code blueprintUnlocks} switch off the Table "lists
 * exactly what it did before" (no Wonder). The fix refuses a Get Blueprint request for a Wonder's whole id
 * ({@code wonder/<id>}), but the Wonder's stage blueprints ({@code wonder/<id>/stage_<n>_0}, the parts its builders
 * raise it from) are handed out for a Blank Blueprint each, so a modified client still gets the Colossus out of the
 * Table, stage by stage. Helpers are {@link QaBuilderFixes1107dGameTests}'s.
 */
public class QaBuilderFixes1107dBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");

	private static List<ResourceLocation> stages(String namespace, String wonder) {
		return List.of(1, 2, 3).stream().map(n -> ResourceLocation.fromNamespaceAndPath(namespace, "wonder/" + wonder + "/stage_" + n + "_0")).toList();
	}

	/** With the switch off, Get Blueprint for the Colossus' and the test folly's stages hands out nothing and uses no blank. */
	//$ gametest_ticks_batch AREA '60' '"qa1107dTableOffStages"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1107dTableOffStages")
	public void withTheSwitchOffNoStageOfAWonderIsHandedOut(GameTestHelper helper) {
		stagesRefused(helper, false);
	}

	/** With the switch on (the Wonders still "???"), the same requests hand out nothing either. */
	//$ gametest_ticks_batch AREA '60' '"qa1107dTableOnStages"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1107dTableOnStages")
	public void withTheSwitchOnNoStageOfAHiddenWonderIsHandedOut(GameTestHelper helper) {
		stagesRefused(helper, true);
	}

	private static void stagesRefused(GameTestHelper helper, boolean on) {
		QaBuilderFixes1107dGameTests.atTable(helper, on, (h, c, p, table, hall) -> {
			List<ResourceLocation> asked = new java.util.ArrayList<>(stages("aliveworkplace", "colossus"));
			asked.addAll(stages("aliveworkplace_test", "test_folly"));
			asked.forEach(id -> c.send(new TablePayloads.Take(table, id)));
			helper.runAfterDelay(3, () -> {
				int got = QaBuilderFixes1107dGameTests.blueprints(p);
				int blanks = QaBuilderFixes1107dGameTests.blanks(p);
				LOGGER.info("qa 1107d stages, switch {}: {} blueprints, {} blanks: {}", on ? "on" : "off", got, blanks, QaStageFourGameTests.keys(c.seen()));
				helper.assertTrue(got == 0 && blanks == 2, "with the switch " + (on ? "on" : "off") + ", asking for the Wonders' stages " + asked + " left " + got
					+ " blueprints and " + blanks + " of 2 blanks: " + QaStageFourGameTests.keys(c.seen()));
				helper.succeed();
			});
		});
	}
}
