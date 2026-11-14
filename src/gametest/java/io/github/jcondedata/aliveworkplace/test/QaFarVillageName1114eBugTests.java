package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * QA (qa-1114e), B1217: a caravan-list entry whose saved name reads back as empty text (a hall given an empty custom
 * name, or the load's {@code Component.empty()} fallback) names the far village with nothing: the quest line reads
 * "(): ". Spec: the line names the village (its own name or a saved one, else the made-up name). Not registered.
 */
public class QaFarVillageName1114eBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	//$ gametest_ticks_batch AREA '40' '"qa1114eBugEmptySavedName"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1114eBugEmptySavedName")
	public void anEmptySavedNameFallsBackToTheMadeUpName(GameTestHelper helper) {
		BlockPos hall = QaFarVillageName1114eGameTests.far(helper, 2400);
		QaFarVillageName1114eGameTests.setUp(helper, hall);
		ServerLevel level = helper.getLevel();
		Caravans.Data.get(level).setWants(hall, Component.literal("Willowmere Hollow"), List.of());
		QaFarVillageName1114eGameTests.reload(helper, tag -> QaFarVillageName1114eGameTests.entry(tag, hall).putString("name", "\"\""));
		helper.assertTrue(Caravans.Data.get(level).village(hall) != null, "setup: the entry with an empty name was dropped on load");
		List<Component> seen = new ArrayList<>();
		ServerPlayer me = QaFarVillageName1114eGameTests.player(helper, seen, "qa1114e-empty");
		QaFarVillageName1114eGameTests.track(helper, me, hall);
		Component line = QaFarVillageName1114eGameTests.questLine(helper, me, seen);
		String made = VillageHalls.madeUpName(hall).getString();
		helper.assertTrue(QaFarVillageName1114eGameTests.village(line).getString().equals(made),
			"the far village is named '" + QaFarVillageName1114eGameTests.village(line).getString() + "' (expected the made-up name " + made + "): " + line.getString());
		helper.assertTrue(!QaFarVillageName1114eGameTests.loaded(level, hall), "naming it loaded the far hall's chunk");
		helper.succeed();
	}
}
