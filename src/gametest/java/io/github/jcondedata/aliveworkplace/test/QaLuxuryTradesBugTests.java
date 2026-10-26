package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.LuxuryRecipes;
import io.github.jcondedata.aliveworkplace.craft.LuxuryRecipes.MadeDay;
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
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1025-0653): tests for mod bugs found by {@link QaLuxuryTradesGameTests}' pass over 34.9-34.12.
 */
public class QaLuxuryTradesBugTests implements FabricGameTest {
	private static final String AREA = QaLuxuryJobsGameTests.AREA;

	/**
	 * B443 (34.9): "Vintage Wine: a Berry Wine at least 3 days old". A Berry Wine bought from an Apprentice Vintner is
	 * pressed the day it's sold (stamped like the Vintner's own, when the trade screen fills its result), so a
	 * Journeyman doesn't re-cork it as Vintage Wine the same day. Bought through the trade screen's own container.
	 */
	//$ gametest_ticks_batch AREA '800' '"qaLuxTrBoughtBug"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qaLuxTrBoughtBug")
	public void aBerryWineBoughtTodayIsNotVintageToday(GameTestHelper helper) {
		QaLuxuryJobsGameTests.shop(helper, Blocks.CAULDRON);
		Villager seller = helper.spawn(EntityType.VILLAGER, new net.minecraft.core.BlockPos(5, 2, 5));
		seller.setNoAi(true);
		MerchantOffer wine = null;
		for (VillagerTrades.ItemListing listing : VillagerTrades.TRADES.get(ModVillagers.VINTNER).get(2)) {
			MerchantOffer offer = listing.getOffer(seller, RandomSource.create(3));
			if (offer != null && offer.getResult().is(ModItems.BERRY_WINE)) {
				wine = offer;
			}
		}
		helper.assertTrue(wine != null, "the Apprentice Vintner sells no Berry Wine");
		long saleDay = LuxuryRecipes.today(helper.getLevel());
		MerchantOffers offers = new MerchantOffers();
		offers.add(wine);
		seller.setOffers(offers);
		MerchantContainer screen = new MerchantContainer(seller);
		screen.setItem(0, new ItemStack(Items.EMERALD, 2));
		ItemStack bought = screen.getItem(2).copy();
		seller.discard();
		helper.assertTrue(bought.is(ModItems.BERRY_WINE), "the trade screen offers " + bought);
		MadeDay made = bought.get(ModComponents.MADE_DAY);
		helper.assertTrue(made != null && made.day() == saleDay, "bought on day " + saleDay + ", stamped " + made);
		Container c = QaLuxuryJobsGameTests.chest(helper);
		c.setItem(0, bought.copy());
		Villager v = QaLuxuryJobsGameTests.worker(helper, ModVillagers.VINTNER, 3);
		QaLuxuryJobsGameTests.run(helper, v, new VintnerWork());
		helper.runAfterDelay(600, () -> {
			int vintage = QaLuxuryJobsGameTests.anywhere(helper, v, ModItems.VINTAGE_WINE);
			helper.assertTrue(vintage == 0, "a Berry Wine bought today (made day " + made + ") became " + vintage + " Vintage Wine the same day");
			helper.succeed();
		});
	}
}
