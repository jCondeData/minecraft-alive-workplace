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
 * QA, lane-d-1121-1908, ROADMAP 52.5 and B1455: STYLE.md rule 4, "Nothing floats". In legend/castaway_camp.nbt the
 * torn sail's tail at template z = 9 touched the sail at z = 8 and the mast only at edges, so five wool blocks hung in
 * the air in every turn. Kept on tests/story-sites-1121d until lane A's fix (a0a76e1d6); on main since lane-d-1122-1308.
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
