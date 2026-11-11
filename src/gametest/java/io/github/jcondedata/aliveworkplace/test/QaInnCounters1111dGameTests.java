package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane D, 2026-11-11 16:08), B1177 from its spec: the village's inn is "a Shop Counter worked by an Innkeeper", the
 * same way the Innkeeper's job takes it. The bug's own tests cover an Innkeeper standing at the counter, a Shopkeeper's
 * counter and the old Inn Counter; this covers the village's edge: a counter one block past
 * it is not the inn, from both sides of the edge. (An Innkeeper away from his counter: QaInnKeeperAway1111dBugTests, B1205.)
 */
public class QaInnCounters1111dGameTests implements FabricGameTest {
	static final String AREA = QaMoverResidents1110cGameTests.AREA;
	static final BlockPos HALL = QaMoverResidents1110cGameTests.HALL; // (15, 2, 2)
	static final BlockPos INN = QaMoverResidents1110cGameTests.INN; // (25, 2, 3): 10.05 blocks from the Hall

	/** The village's edge for the test, restored after. */
	static void radius(GameTestHelper helper, int radius) {
		int was = VillageHalls.RADIUS;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = was);
		VillageHalls.RADIUS = radius;
	}

	/** An Innkeeper standing at {@code standing}, whose job site is the Shop Counter at {@code counter}. */
	static Villager innkeeper(GameTestHelper helper, BlockPos counter, BlockPos standing) {
		Villager keeper = QaMoverResidents1110cGameTests.villager(helper, standing, "Ina");
		Jobs.employ(helper.getLevel(), keeper, helper.absolutePos(counter), ModVillagers.SHOP_COUNTER_POI, ModVillagers.INNKEEPER);
		return keeper;
	}

	/** The village's edge from both sides: the counter at 10.05 blocks is in a village of radius 11, and out of one of radius 10. */
	//$ gametest_ticks_batch AREA '60' '"qaInnEdge1111d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaInnEdge1111d")
	public void anInnCounterJustPastTheVillagesEdgeIsNotItsInn(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		radius(helper, 11);
		helper.setBlock(INN, ModBlocks.SHOP_COUNTER);
		helper.runAfterDelay(2, () -> {
			InnShopCounterB1177GameTests.noOtherInns(level, hall);
			Villager keeper = innkeeper(helper, INN, INN.west()); // stands on the Hall's side, inside both edges
			helper.assertTrue(LegendGuests.rollsOn(level, hall, null, "inn", 1), "an inn 10.05 blocks from the Hall isn't the inn of a village of radius 11");
			VillageHalls.RADIUS = 10;
			helper.assertFalse(LegendGuests.rollsOn(level, hall, null, "inn", 1), "an inn 10.05 blocks from the Hall counts as the inn of a village of radius 10");
			helper.assertTrue(Innkeepers.counters(level, hall, VillageHalls.RADIUS).isEmpty(), "an inn counter past the village's edge was found");
			keeper.remove(Entity.RemovalReason.DISCARDED);
			helper.succeed();
		});
	}
}
