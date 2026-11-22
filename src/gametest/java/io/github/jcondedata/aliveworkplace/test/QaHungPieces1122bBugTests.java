package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** QA lane B (2026-11-22): {@link QaHungPieces1122bGameTests}' check over the whole catalog; fails on B1485 (the Quarry's lever on a fence top). */
public class QaHungPieces1122bBugTests implements FabricGameTest {

	/** Every shipped blueprint: every hung or standing piece has a hold in the drawing. */
	//$ gametest_ticks 'FabricGameTest.EMPTY_STRUCTURE' '400'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
	public void qaEveryHungPieceOfEveryShippedBlueprintHasAHold(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<String> problems = new ArrayList<>();
		int[] unknown = {0};
		int checked = 0;
		for (ResourceLocation id : QaHungPieces1122bGameTests.shipped(server)) {
			Blueprint blueprint = BlueprintLibrary.get(server, id).orElse(null);
			if (blueprint == null) {
				continue;
			}
			checked++;
			problems.addAll(QaHungPieces1122bGameTests.unheld(blueprint, helper.getLevel(), unknown));
		}
		helper.assertTrue(checked > 300, "only " + checked + " shipped blueprints were found");
		helper.assertTrue(unknown[0] == 0, unknown[0] + " piece(s) couldn't be checked against the drawing alone");
		helper.assertTrue(problems.isEmpty(), problems.size() + " piece(s) with nothing to hold them: "
			+ String.join("; ", problems.subList(0, Math.min(10, problems.size()))));
		helper.succeed();
	}
}
