package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

/**
 * Bug test (QA, lane D, 2026-11-11, B1205, B1177's kin): the village's inn is "a Shop Counter worked by an Innkeeper", but
 * Innkeepers.counters only finds a Shop Counter whose Innkeeper is standing inside the village's box, so the inn stops
 * being one (no legend foretold at the inn, a move's residents don't stay there) while its Innkeeper is out past the edge.
 */
public class QaInnKeeperAway1111dBugTests implements FabricGameTest {
	static final String AREA = QaInnCounters1111dGameTests.AREA;
	private static final BlockPos HALL = QaInnCounters1111dGameTests.HALL;
	private static final BlockPos INN = QaInnCounters1111dGameTests.INN;

	/** The Innkeeper has gone out past the village's edge (to bed, a walk); the counter he works is still in the village, and still its inn. */
	//$ gametest_ticks_batch AREA '60' '"qaInnKeeperAway1111d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaInnKeeperAway1111d")
	public void anInnWhoseKeeperIsAwayFromTheCounterIsStillTheInn(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		QaInnCounters1111dGameTests.radius(helper, 12);
		helper.setBlock(INN, ModBlocks.SHOP_COUNTER);
		helper.runAfterDelay(2, () -> {
			InnShopCounterB1177GameTests.noOtherInns(level, hall);
			Villager keeper = QaInnCounters1111dGameTests.innkeeper(helper, INN, INN.south());
			helper.assertTrue(LegendGuests.rollsOn(level, hall, null, "inn", 1), "setup: the Innkeeper's Shop Counter isn't the inn");
			BlockPos away = helper.absolutePos(new BlockPos(15, 2, 27)); // 25 blocks from the Hall: past the edge (12)
			keeper.moveTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
			helper.assertTrue(Innkeepers.counters(level, hall, VillageHalls.RADIUS).contains(helper.absolutePos(INN)),
				"the inn's Shop Counter, 10 blocks from the Hall, stops being an inn counter while its Innkeeper is 25 blocks away");
			helper.assertTrue(LegendGuests.rollsOn(level, hall, null, "inn", 1),
				"a legend can't come to the inn while the Innkeeper is away from his counter (at the far side of the village)");
			keeper.remove(Entity.RemovalReason.DISCARDED);
			helper.succeed();
		});
	}

}
