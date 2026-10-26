package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.city.CityPlans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** QA tests for ROADMAP 27.2 and 27.3 (B468): the add/edit-zone packet refuses a style or name the screen can't send. */
public class QaCityPlanBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(8, 2, 8);

	/**
	 * 27.2: a zone's style is "one of BlueprintStyles.all(), or as drawn", and the edit packet is "checked on the server".
	 * A modified client's ADD_ZONE or EDIT_ZONE with a style that doesn't exist is stored on the hall anyway.
	 */
	//$ gametest_ticks_batch AREA '20' '"qaCityPlanBugStyle"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCityPlanBugStyle")
	public void aZoneWithAStyleThatDoesntExistIsRefused(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = QaCityPlanGameTests.hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(BlueprintStyles.get("no_such_style").isEmpty(), "setup: no_such_style is a style");
		helper.assertTrue(CityPlans.apply(player, QaCityPlanGameTests.sent(level, CityPlans.Edit.addZone(hall, "homes", "Homes", ""))), "an as-drawn zone was refused");
		helper.assertFalse(CityPlans.apply(player, QaCityPlanGameTests.sent(level, CityPlans.Edit.addZone(hall, "homes", "Bad", "no_such_style"))),
			"a zone in a style that doesn't exist went on the plan");
		helper.assertFalse(CityPlans.apply(player, QaCityPlanGameTests.sent(level, CityPlans.Edit.editZone(hall, 0, "homes", "Homes", "no_such_style", false))),
			"a zone was changed to a style that doesn't exist");
		helper.assertTrue(entity.plan().zones().stream().allMatch(z -> z.style().isEmpty() || BlueprintStyles.get(z.style()).isPresent()),
			"styles on the plan: " + entity.plan().zones().stream().map(z -> z.style()).toList());
		helper.succeed();
	}

	/**
	 * 27.3: the screen keeps a zone's name to {@link CityPlans#MAX_NAME} letters, and a road's style is refused past it on
	 * the server, but a zone name of any length from a modified client is saved on the hall (and its item) as sent.
	 */
	//$ gametest_ticks_batch AREA '20' '"qaCityPlanBugName"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCityPlanBugName")
	public void aZoneNameLongerThanTheScreenAllowsIsRefused(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = QaCityPlanGameTests.hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(CityPlans.apply(player, QaCityPlanGameTests.sent(level, CityPlans.Edit.addZone(hall, "homes", "a".repeat(CityPlans.MAX_NAME), ""))),
			"a 32-letter name was refused");
		String huge = "x".repeat(20000);
		CityPlans.apply(player, QaCityPlanGameTests.sent(level, CityPlans.Edit.addZone(hall, "homes", huge, "")));
		CityPlans.apply(player, QaCityPlanGameTests.sent(level, CityPlans.Edit.editZone(hall, 0, "homes", huge, "", false)));
		int longest = entity.plan().zones().stream().mapToInt(z -> z.name().length()).max().orElse(0);
		helper.assertTrue(longest <= CityPlans.MAX_NAME, "a zone name of " + longest + " letters is on the plan (the screen allows " + CityPlans.MAX_NAME + ")");
		helper.succeed();
	}
}
