package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.work.Money;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;

/**
 * QA (lane E, 2026-10-31): a player's shop sells at its owner's price, also to a buyer with Hero of the Village
 * (FriendPerks and ROADMAP 31.12: "players' shops aren't touched"). Fails on main 19d15b2b9: vanilla's Hero discount
 * takes 30% off the shopkeeper's trades, out of the owner's takings.
 */
public class QaShopHeroBug1031Tests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	private static int value(ItemStack cost) {
		return cost.is(Items.EMERALD_BLOCK) ? 9 * cost.getCount() : cost.is(Items.EMERALD) ? cost.getCount() : cost.isEmpty() ? 0 : -100_000;
	}

	/**
	 * A player's shop sells at the owner's price: a buyer with Hero of the Village (as after a raid, or the festival's
	 * round) is asked the same 10 emeralds, and a 13,000 tag the same 14 blocks and 4 emeralds, as anyone. Vanilla's
	 * Hero discount takes 30% and more off a villager's own trades; here it would come out of the shop owner's takings.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopHero"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopHero")
	public void aHeroOfTheVillagePaysTheOwnersPrice(GameTestHelper helper) {
		Leftovers.clear(helper);
		ShopCounterBlockEntity counter = QaShopPaymentSaves1031GameTests.shop(helper, new ItemStack(Items.EMERALD, 10));
		Villager keeper = QaShopPaymentSaves1031GameTests.keeper(helper);
		QaStageFiveGameTests.Client buyer = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaHeroBuyer"));
		ServerPlayer p = buyer.player();
		p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 2400, 0));
		p.setShiftKeyDown(Money.cobbleDollars());
		QaStageFourGameTests.pressOn(p, keeper, buyer.seen());
		helper.assertTrue(p.containerMenu instanceof MerchantMenu, "the trade screen didn't open: " + p.containerMenu);
		MerchantOffer offer = keeper.getOffers().get(0);
		String asked = offer.getCostA().toString();
		boolean ten = offer.getCostA().getCount() == 10 && offer.getCostA().is(Items.EMERALD);
		p.closeContainer();

		counter.setItem(ShopCounterBlockEntity.COLUMNS, QaShopPaymentSaves1031GameTests.tag(13_000));
		QaStageFourGameTests.pressOn(p, keeper, buyer.seen());
		helper.assertTrue(p.containerMenu instanceof MerchantMenu, "the trade screen didn't open the second time: " + p.containerMenu);
		MerchantOffer big = keeper.getOffers().get(0);
		int value = value(big.getCostA()) + value(big.getCostB());
		String bigAsked = big.getCostA() + " + " + big.getCostB();
		p.closeContainer();
		helper.assertTrue(ten && value == 130, "a buyer with Hero of the Village is asked " + asked + " for a diamond block the owner priced at 10 emeralds, and "
			+ bigAsked + " (" + value + " emeralds) for one on a 13,000 tag (130 emeralds)");
		helper.succeed();
	}
}
