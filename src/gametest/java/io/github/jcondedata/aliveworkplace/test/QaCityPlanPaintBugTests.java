package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaCityPlanGameTests.hall;
import static io.github.jcondedata.aliveworkplace.test.QaCityPlanGameTests.otherHalls;
import static io.github.jcondedata.aliveworkplace.test.QaCityPlanGameTests.sent;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * QA bug tests (27.2/27.3): packets the server should check but takes as sent. Each test fails on today's code; not
 * for committing until the mod is fixed.
 */
public class QaCityPlanPaintBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	private static boolean styleOk(String style) {
		return style.isEmpty() || BlueprintStyles.get(style).isPresent();
	}

	/** 27.2: a zone's style is one of BlueprintStyles.all() or "as drawn": a modified client's made-up style must not be saved. */
	//$ gametest_ticks_batch AREA '20' '"qaCityBugStyle"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCityBugStyle")
	public void aStyleThatIsntAStyleIsNotSaved(GameTestHelper helper) {
		Leftovers.clear(helper);
		otherHalls(helper, HALL);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(BlueprintStyles.get("no_such_style").isEmpty(), "setup: no_such_style is a style");
		CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(hall, "homes", "Homes", "no_such_style")));
		for (CityPlan.Zone zone : entity.plan().zones()) {
			helper.assertTrue(styleOk(zone.style()), "addZone saved a style that isn't one: '" + zone.style() + "'");
		}
		if (entity.plan().zones().isEmpty()) {
			helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(hall, "homes", "Homes", ""))), "no zone as drawn");
		}
		CityPlans.apply(player, sent(level, CityPlans.Edit.editZone(hall, 0, "homes", "Homes", "no_such_style", false)));
		helper.assertTrue(styleOk(entity.plan().zones().get(0).style()), "editZone saved a style that isn't one: '" + entity.plan().zones().get(0).style() + "'");
		helper.succeed();
	}

	/** The screen caps a zone's name at MAX_NAME (32); the server must too, or a modified client stores a name of any size on the hall. */
	//$ gametest_ticks_batch AREA '20' '"qaCityBugName"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCityBugName")
	public void aZoneNameLongerThanTheScreenAllowsIsNotSaved(GameTestHelper helper) {
		Leftovers.clear(helper);
		otherHalls(helper, HALL);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(hall, "homes", "a".repeat(CityPlans.MAX_NAME), ""))),
			"a 32-letter name was refused");
		CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(hall, "homes", "b".repeat(20000), "")));
		CityPlans.apply(player, sent(level, CityPlans.Edit.editZone(hall, 0, "homes", "c".repeat(CityPlans.MAX_NAME + 1), "", false)));
		java.util.List<Integer> lengths = entity.plan().zones().stream().map(z -> z.name().length()).toList();
		helper.assertTrue(lengths.stream().allMatch(n -> n <= CityPlans.MAX_NAME), "zone names longer than " + CityPlans.MAX_NAME + " were saved, lengths " + lengths);
		helper.succeed();
	}
}
