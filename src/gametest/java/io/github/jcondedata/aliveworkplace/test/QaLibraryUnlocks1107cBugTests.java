package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderBlueprints;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * QA (lane-b-1107-0637) bug test for the Blueprint library (26.10, docs/wiki/blueprint-library.md): Wonders are "named
 * and previewed, never handed out; Wonders are raised from their plan", and with the {@code blueprintUnlocks} switch off
 * the Table "lists exactly what it did before" (no Wonder). Yet with the switch off, Get Blueprint for a Wonder's id
 * over the network hands out a Blueprint of the Wonder for a Blank Blueprint. With the switch on the same request is
 * refused (QaLibraryUnlocks1107cGameTests.aWonderPlanUsedOnTheTableNamesItsWonderViewOnly).
 */
public class QaLibraryUnlocks1107cBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");

	/** With the switch off, Get Blueprint for the folly's id gives nothing and uses no Blank Blueprint. */
	//$ gametest_ticks_batch AREA '60' '"qa1107cWonderTakeSwitchOff"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1107cWonderTakeSwitchOff")
	public void withTheSwitchOffTheTableStillHandsOutNoWonder(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		BlueprintUnlocks.ENABLED = false;
		BlockPos hallRel = QaBlueprintUnlocksGameTests.HALL;
		BlockPos tableRel = QaBlueprintUnlocksGameTests.TABLE;
		QaBlueprintUnlocksGameTests.village(helper, hallRel, tableRel);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			BlockPos table = helper.absolutePos(tableRel);
			Wonder folly = Wonders.loaded().get(FOLLY);
			helper.assertTrue(folly != null, "the test folly isn't loaded: " + Wonders.problems());
			ResourceLocation id = WonderBlueprints.idOf(folly.id());
			helper.assertTrue(TableServer.listing(level, helper.absolutePos(hallRel)).stream().noneMatch(e -> e.id().equals(id)), "with the switch off the Table lists the folly");
			QaStageFiveGameTests.Client c = QaBlueprintUnlocksGameTests.client(helper, tableRel);
			ServerPlayer p = c.player();
			p.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 2));
			c.send(new TablePayloads.Take(table, id));
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(p.getInventory().countItem(ModItems.BLUEPRINT) == 0 && p.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 2,
					"with the switch off the Table handed out the folly: " + p.getInventory().countItem(ModItems.BLUEPRINT) + " blueprints, "
						+ p.getInventory().countItem(ModItems.BLANK_BLUEPRINT) + " blanks");
				helper.succeed();
			});
		});
	}
}
