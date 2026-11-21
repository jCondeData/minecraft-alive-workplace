package io.github.jcondedata.aliveworkplace.test;

import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

/**
 * QA, lane-d-1121-1908, ROADMAP 52.5: failing checks of the rebuilt story sites, kept out of the suite (not registered)
 * until the drawing is fixed. STYLE.md rule 4, "Nothing floats": in legend/castaway_camp.nbt the torn sail's tail at
 * template z = 9 ((2,4,9), (2,5,9), (3,5,9), (3,6,9), (4,7,9)) touches the sail at z = 8 and the mast only at edges,
 * so five wool blocks hang in the air in every turn.
 */
public class QaRebuiltStorySites1121dBugGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** Every block of the castaway camp is joined face to face to its ground, in each of the four turns. */
	//$ gametest_ticks_batch AREA '100' '"qaRebuiltCastawayFloats1121d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRebuiltCastawayFloats1121d")
	public void qaNothingOfTheCastawayCampFloatsInEveryTurn(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		QaRebuiltStorySites1121dGameTests.setUp(helper, owner, Blocks.SAND);
		Map<Rotation, Villager> camps = QaRebuiltStorySites1121dGameTests.fourCamps(helper, "shipwreck");
		for (Map.Entry<Rotation, Villager> e : camps.entrySet()) {
			List<String> floats = QaRebuiltStorySites1121dGameTests.floating(helper.getLevel(),
				QaRebuiltStorySites1121dGameTests.box(QaRebuiltStorySites1121dGameTests.captive(helper, e.getValue())));
			helper.assertTrue(floats.isEmpty(), "castaway camp turned " + e.getKey() + ": blocks float: " + floats);
		}
		helper.succeed();
	}
}
