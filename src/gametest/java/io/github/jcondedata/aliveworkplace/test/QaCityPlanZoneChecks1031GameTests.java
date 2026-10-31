package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaCityPlanGameTests.hall;
import static io.github.jcondedata.aliveworkplace.test.QaCityPlanGameTests.otherHalls;
import static io.github.jcondedata.aliveworkplace.test.QaCityPlanGameTests.sent;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * QA (qa-lane-b-1031-1238) for B468 and B503: the City Plan's add-zone and edit-zone packets are checked on the server
 * (a zone's style is one of BlueprintStyles.all() or as drawn; a name is 1 to CityPlans.MAX_NAME = 32 letters). Their
 * tests prove the refusals; these prove the other side and what a refusal leaves behind: every real style still goes on,
 * a refused edit changes nothing (not cut, no undo step, nothing different after a save and reload), the refusal's
 * sentence as the player reads it, and that a gone style is kept only by the zone that already had it.
 */
public class QaCityPlanZoneChecks1031GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final String BAD_ZONE = "That zone can't go on the plan: its name must be 1 to 32 letters and its style one the plan lists";

	private static boolean apply(ServerPlayer player, CityPlans.Edit edit) {
		return CityPlans.apply(player, sent(player.serverLevel(), edit));
	}

	private static String zones(CityPlan plan) {
		return plan.zones().stream().map(z -> z.kind() + "/" + z.name() + "/" + z.style()).toList().toString();
	}

	/** Every style the plan lists is accepted, both on a new zone and when an existing zone is changed to it. */
	//$ gametest_ticks_batch AREA '20' '"qaZoneChecks1031Styles"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaZoneChecks1031Styles")
	public void everyStyleThePlanListsGoesOnAZone(GameTestHelper helper) {
		Leftovers.clear(helper);
		otherHalls(helper, HALL);
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		List<BlueprintStyles.Style> styles = BlueprintStyles.all();
		helper.assertTrue(!styles.isEmpty(), "setup: no styles are loaded");
		helper.assertTrue(apply(player, CityPlans.Edit.addZone(hall, "homes", "Homes", "")), "an as-drawn zone was refused");
		List<String> refused = new ArrayList<>();
		for (BlueprintStyles.Style style : styles) {
			if (!apply(player, CityPlans.Edit.editZone(hall, 0, "homes", "Homes", style.name(), false))
				|| !entity.plan().zones().get(0).style().equals(style.name())) {
				refused.add("edit " + style.name());
			}
			if (entity.plan().zones().size() < CityPlan.MAX_ZONES
				&& (!apply(player, CityPlans.Edit.addZone(hall, "homes", "H " + style.name(), style.name()))
				|| !entity.plan().zones().get(entity.plan().zones().size() - 1).style().equals(style.name()))) {
				refused.add("add " + style.name());
			}
		}
		AliveWorkplace.LOG.info("QA B468 styles tried: {}, zones {}", styles.stream().map(BlueprintStyles.Style::name).toList(), zones(entity.plan()));
		helper.assertTrue(refused.isEmpty(), "styles the plan lists were refused: " + refused);
		helper.succeed();
	}

	/**
	 * A zone renamed to exactly 32 letters, then sent a 33-letter name and a made-up style: both refused with the
	 * bad_zone sentence (as en_us.json words it, with 32), the zone keeps its 32-letter name and style (not cut to 32,
	 * not half-changed), undo goes back past the last accepted rename (refusals add no undo step), and the hall saved
	 * and loaded again holds the same zone.
	 */
	//$ gametest_ticks_batch AREA '20' '"qaZoneChecks1031Refused"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaZoneChecks1031Refused")
	public void aRefusedZoneEditLeavesThePlanAsItWas(GameTestHelper helper) {
		Leftovers.clear(helper);
		otherHalls(helper, HALL);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		List<Component> heard = new ArrayList<>();
		ServerPlayer player = WonderFinishedGameTests.listener(helper, heard, "qa-zone-1031");
		String style = BlueprintStyles.all().get(0).name();
		helper.assertTrue(apply(player, CityPlans.Edit.addZone(hall, "homes", "H", style)), "a 1-letter name was refused");
		String full = "N".repeat(CityPlans.MAX_NAME);
		helper.assertTrue(apply(player, CityPlans.Edit.editZone(hall, 0, "homes", full, style, false)), "a rename to 32 letters was refused");
		String before = zones(entity.plan());
		heard.clear();
		helper.assertFalse(apply(player, CityPlans.Edit.editZone(hall, 0, "homes", "M".repeat(CityPlans.MAX_NAME + 1), style, false)),
			"a 33-letter rename was accepted");
		helper.assertFalse(apply(player, CityPlans.Edit.editZone(hall, 0, "homes", full, "no_such_style", false)), "a made-up style was accepted");
		helper.assertFalse(apply(player, CityPlans.Edit.addZone(hall, "homes", "M".repeat(CityPlans.MAX_NAME + 1), "")), "a 33-letter new zone was accepted");
		helper.assertTrue(zones(entity.plan()).equals(before), "refused edits changed the plan: " + before + " -> " + zones(entity.plan()));
		List<String> told = heard.stream().map(Component::getString).toList();
		AliveWorkplace.LOG.info("QA B503 refusals told: {}", told);
		helper.assertTrue(told.size() >= 3 && told.stream().filter(BAD_ZONE::equals).count() == 3,
			"each refusal should read '" + BAD_ZONE + "', heard " + told);
		helper.assertTrue(told.stream().map(String::toLowerCase).noneMatch(t -> t.contains("colour") || t.contains("aliveworkplace.")), "odd wording: " + told);
		// saved and loaded again: the same zone
		CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
		VillageHallBlockEntity loaded = QaCityPlanGameTests.reload(level, entity, tag);
		helper.assertTrue(loaded != null && zones(loaded.plan()).equals(before), "after a save and reload the plan is " + (loaded == null ? null : zones(loaded.plan()))
			+ ", not " + before);
		// undo: back to before the 32-letter rename, not to a refused edit
		helper.assertTrue(apply(player, CityPlans.Edit.undo(hall)), "undo did nothing");
		helper.assertTrue(entity.plan().zones().size() == 1 && entity.plan().zones().get(0).name().equals("H"),
			"undo should go back to the zone named 'H' (the last accepted change), plan " + zones(entity.plan()));
		helper.succeed();
	}

	/**
	 * A zone saved before the check with a style that's gone keeps it when renamed (QaCityPlanPaintBugTests), but that
	 * leniency is the zone's own: another zone can't be changed to the gone style, and an edit of a zone that isn't on the
	 * plan with it is refused without harm.
	 */
	//$ gametest_ticks_batch AREA '20' '"qaZoneChecks1031Gone"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaZoneChecks1031Gone")
	public void aGoneStyleStaysOnlyWithTheZoneThatHadIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		otherHalls(helper, HALL);
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(BlueprintStyles.get("no_such_style").isEmpty(), "setup: no_such_style is a style");
		CityPlan old = entity.plan().addZone("homes", "Old Town", "no_such_style"); // as an older version could save it
		helper.assertTrue(old != null, "setup: no zone");
		entity.changePlan(old, CityPlans.UNDO_STEPS);
		helper.assertTrue(apply(player, CityPlans.Edit.addZone(hall, "homes", "New Town", "")), "an as-drawn zone was refused");
		helper.assertFalse(apply(player, CityPlans.Edit.editZone(hall, 1, "homes", "New Town", "no_such_style", false)),
			"a second zone was changed to the first zone's gone style");
		helper.assertFalse(apply(player, CityPlans.Edit.editZone(hall, 5, "homes", "Nowhere", "no_such_style", false)),
			"an edit of a zone that isn't on the plan was accepted");
		helper.assertFalse(apply(player, CityPlans.Edit.editZone(hall, -1, "homes", "Nowhere", "no_such_style", false)),
			"an edit of zone -1 was accepted");
		helper.assertTrue(entity.plan().zones().size() == 2 && entity.plan().zones().get(1).style().isEmpty()
			&& entity.plan().zones().get(0).style().equals("no_such_style"), "zones now " + zones(entity.plan()));
		helper.succeed();
	}
}
