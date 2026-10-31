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

/** QA tests (27.2/27.3, B503): the zone fields of the City Plan's edit packet are checked on the server. */
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

	/** A blank name, or one with a formatting code no text box lets through, is refused too. */
	//$ gametest_ticks_batch AREA '20' '"qaCityBugOddName"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCityBugOddName")
	public void aBlankOrFormattedZoneNameIsRefused(GameTestHelper helper) {
		Leftovers.clear(helper);
		otherHalls(helper, HALL);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertFalse(CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(hall, "homes", "   ", ""))), "a blank name went on the plan");
		helper.assertFalse(CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(hall, "homes", "\u00a7cRed", ""))), "a formatted name went on the plan");
		helper.assertTrue(entity.plan().zones().isEmpty(), "zones on the plan: " + entity.plan().zones().size());
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(hall, "homes", "North Homes", ""))), "a plain name was refused");
		helper.succeed();
	}

	/** A zone saved before the check with a style that doesn't exist still loads, can be renamed and builds as drawn. */
	//$ gametest_ticks_batch AREA '20' '"qaCityBugOldStyle"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCityBugOldStyle")
	public void aZoneSavedWithAGoneStyleCanBeRenamedAndBuildsAsDrawn(GameTestHelper helper) {
		Leftovers.clear(helper);
		otherHalls(helper, HALL);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		CityPlan old = entity.plan().addZone("homes", "Homes", "no_such_style"); // as an older version could save it
		helper.assertTrue(old != null, "setup: no zone");
		entity.changePlan(old, CityPlans.UNDO_STEPS);
		helper.assertTrue(CityPlans.buildStyle(entity.plan().zones().get(0)).isEmpty(), "a gone style isn't built as drawn");
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.editZone(hall, 0, "homes", "Old Town", "no_such_style", false))),
			"a zone with a gone style couldn't be renamed");
		helper.assertTrue(entity.plan().zones().get(0).name().equals("Old Town"), "name: " + entity.plan().zones().get(0).name());
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.editZone(hall, 0, "homes", "Old Town", "", false))),
			"a zone couldn't be set back to as drawn");
		helper.assertFalse(CityPlans.apply(player, sent(level, CityPlans.Edit.editZone(hall, 0, "homes", "Old Town", "no_such_style", false))),
			"a gone style could be picked again");
		helper.succeed();
	}
}
