package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.vintner.VintnerWork;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1025-0653): failing tests for mod bugs found by {@link QaLuxuryTradesGameTests}' pass over 34.9-34.12.
 */
public class QaLuxuryTradesBugTests implements FabricGameTest {
	private static final String AREA = QaLuxuryJobsGameTests.AREA;

	/**
	 * 34.9: "Vintage Wine: a Berry Wine at least 3 days old". A Berry Wine bought from an Apprentice Vintner today comes
	 * with no pressed day, so it counts as old: put in a Journeyman's chest it is re-corked as Vintage Wine the same
	 * day. Expected: a bought Berry Wine is pressed today (stamped like the Vintner's own), and waits its 3 days.
	 */
	//$ gametest_ticks_batch AREA '800' '"qaLuxTrBoughtBug"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qaLuxTrBoughtBug")
	public void aBerryWineBoughtTodayIsNotVintageToday(GameTestHelper helper) {
		QaLuxuryJobsGameTests.shop(helper, Blocks.CAULDRON);
		Villager seller = helper.spawn(EntityType.VILLAGER, new net.minecraft.core.BlockPos(5, 2, 5));
		seller.setNoAi(true);
		ItemStack bought = ItemStack.EMPTY;
		for (VillagerTrades.ItemListing listing : VillagerTrades.TRADES.get(ModVillagers.VINTNER).get(2)) {
			MerchantOffer offer = listing.getOffer(seller, RandomSource.create(3));
			if (offer != null && offer.getResult().is(ModItems.BERRY_WINE)) {
				bought = offer.assemble();
			}
		}
		seller.discard();
		helper.assertTrue(bought.is(ModItems.BERRY_WINE), "the Apprentice Vintner sells no Berry Wine");
		Container c = QaLuxuryJobsGameTests.chest(helper);
		c.setItem(0, bought.copy());
		Villager v = QaLuxuryJobsGameTests.worker(helper, ModVillagers.VINTNER, 3);
		QaLuxuryJobsGameTests.run(helper, v, new VintnerWork());
		ItemStack sold = bought;
		helper.runAfterDelay(600, () -> {
			int vintage = QaLuxuryJobsGameTests.anywhere(helper, v, ModItems.VINTAGE_WINE);
			helper.assertTrue(vintage == 0, "a Berry Wine bought today (made day " + sold.get(ModComponents.MADE_DAY) + ") became " + vintage
				+ " Vintage Wine the same day");
			helper.succeed();
		});
	}
}
