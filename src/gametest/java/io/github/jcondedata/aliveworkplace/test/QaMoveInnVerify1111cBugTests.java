package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaMoveInnVerify1111cGameTests.AREA;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
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
 * QA (lane C, 2026-11-11), the adversarial half of {@link QaMoveInnVerify1111cGameTests} for B1203/B1205: the fix
 * remembers which Innkeeper works each Shop Counter (InnCounters) and trusts the memory while that Innkeeper is unloaded.
 */
public class QaMoveInnVerify1111cBugTests implements FabricGameTest {
	private static final BlockPos AWAY = new BlockPos(15, 2, 27);

	/**
	 * The Innkeeper is in an unloaded chunk; meanwhile the player breaks the inn's Shop Counter, puts a new one in its
	 * place and a Shopkeeper takes it, who is out past the village's edge just now. The counter is a shop, not the inn.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaVerifyInnRebuilt"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaVerifyInnRebuilt")
	public void aCounterRebuiltForAShopkeeperIsNoInnWhileTheOldInnkeeperIsUnloaded(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoverResidents1110cGameTests.Setup s = QaMoveInnVerify1111cGameTests.innVillage(helper, "qaverifyrebuilt");
		helper.runAfterDelay(2, () -> {
			Villager ina = QaMoveInnVerify1111cGameTests.seenInnkeeper(helper, s);
			QaMoveInnVerify1111cGameTests.sendAway(helper, ina);
			QaMoveInnVerify1111cGameTests.unload(ina);
			helper.destroyBlock(QaInnShopCounter1111cGameTests.INN);
		});
		helper.runAfterDelay(5, () -> helper.setBlock(QaInnShopCounter1111cGameTests.INN, ModBlocks.SHOP_COUNTER));
		helper.runAfterDelay(9, () -> {
			Villager sam = QaMoverResidents1110cGameTests.villager(helper, AWAY.east(), "Sam");
			Jobs.employ(level, sam, helper.absolutePos(QaInnShopCounter1111cGameTests.INN), ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
			helper.assertFalse(QaInnShopCounter1111cGameTests.innRolls(helper),
				"the Shop Counter was broken and rebuilt for Sam the Shopkeeper, yet a legend rolls there as at an inn (the old Innkeeper Ina is in an unloaded chunk): counters "
					+ Innkeepers.counters(level, s.hall(), VillageHalls.RADIUS));
			sam.remove(Entity.RemovalReason.DISCARDED);
			helper.succeed();
		});
	}

	/**
	 * An Innkeeper hired at the village's Shop Counter who has been out past the village's edge ever since (an older
	 * world's Innkeeper asleep past the edge on the first night with the fix; the spec: "counts wherever he stands").
	 */
	//$ gametest_ticks_batch AREA '60' '"qaVerifyInnNeverSeen"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaVerifyInnNeverSeen")
	public void anInnkeeperNeverSeenInTheVillageStillMakesHisCounterTheInn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoverResidents1110cGameTests.Setup s = QaMoveInnVerify1111cGameTests.innVillage(helper, "qaverifynever");
		helper.runAfterDelay(2, () -> {
			InnShopCounterB1177GameTests.noOtherInns(level, s.hall());
			Villager ina = QaInnCounters1111dGameTests.innkeeper(helper, QaInnShopCounter1111cGameTests.INN, AWAY);
			helper.assertTrue(QaInnShopCounter1111cGameTests.innRolls(helper),
				"an Innkeeper who works the village's Shop Counter but stood past the village's edge since he was hired: no legend rolls at the inn; counters "
					+ Innkeepers.counters(level, s.hall(), VillageHalls.RADIUS));
			ina.remove(Entity.RemovalReason.DISCARDED);
			helper.succeed();
		});
	}
}
