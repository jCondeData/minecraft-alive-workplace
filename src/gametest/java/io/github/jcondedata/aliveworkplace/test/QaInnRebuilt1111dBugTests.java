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
import net.minecraft.world.level.block.Blocks;

/**
 * Bug test (QA, lane D, 2026-11-11 22:08), B1203/B1205's fix from their spec: "an Innkeeper's job-site Shop Counter
 * inside the village counts wherever he stands". The remembered counter (InnCounters) is taken at its word while its old
 * Innkeeper is in an unloaded chunk, even after the player broke that counter and put a new one in its place that a
 * Shopkeeper now works: while the Shopkeeper is out past the village's edge, his shop is the village's inn.
 */
public class QaInnRebuilt1111dBugTests implements FabricGameTest {
	static final String AREA = QaInnCounters1111dGameTests.AREA;
	private static final BlockPos HALL = QaInnCounters1111dGameTests.HALL;
	private static final BlockPos INN = QaInnCounters1111dGameTests.INN;

	/**
	 * Ina works the Shop Counter (the inn), then is far off in an unloaded chunk; the player breaks the counter and puts a
	 * new one there, and Sam the Shopkeeper takes it. While Sam is out past the village's edge, the counter is no inn.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaInnRebuilt1111d"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaInnRebuilt1111d")
	public void aShopCounterRebuiltForAShopkeeperIsNoInnWhileTheOldInnkeeperIsUnloaded(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos counter = helper.absolutePos(INN);
		QaInnCounters1111dGameTests.radius(helper, 12);
		helper.setBlock(INN, ModBlocks.SHOP_COUNTER);
		helper.runAfterDelay(2, () -> {
			InnShopCounterB1177GameTests.noOtherInns(level, hall);
			Villager ina = QaInnCounters1111dGameTests.innkeeper(helper, INN, INN.south());
			helper.assertTrue(LegendGuests.rollsOn(level, hall, null, "inn", 1), "setup: the Innkeeper's Shop Counter isn't the inn");
			ina.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); // her chunk unloads far away: still an Innkeeper, not gone for good
			helper.setBlock(INN, Blocks.AIR);
			helper.runAfterDelay(2, () -> {
				helper.setBlock(INN, ModBlocks.SHOP_COUNTER);
				helper.runAfterDelay(2, () -> {
					Villager sam = QaMoverResidents1110cGameTests.villager(helper, new BlockPos(15, 2, 27), "Sam"); // 25 blocks off: past the edge
					Jobs.employ(level, sam, counter, ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
					helper.assertFalse(Innkeepers.counters(level, hall, VillageHalls.RADIUS).contains(counter),
						"a Shop Counter the player rebuilt for a Shopkeeper (out past the edge) is still the inn because its old Innkeeper is unloaded");
					helper.assertFalse(LegendGuests.rollsOn(level, hall, null, "inn", 1),
						"a legend can come to the inn of a village whose only Shop Counter is a Shopkeeper's");
					sam.remove(Entity.RemovalReason.DISCARDED);
					helper.succeed();
				});
			});
		});
	}
}
