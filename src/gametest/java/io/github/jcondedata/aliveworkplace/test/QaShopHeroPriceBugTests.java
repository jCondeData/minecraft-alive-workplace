package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane D, 2026-10-31), from the guide (shop-counter.md, shopkeeper.md: "you set the prices") and 31.12 ("Players'
 * shops aren't touched" by the friendship discounts): a buyer who just won a raid (Hero of the Village I) is asked the
 * owner's price at a player's shop. Fails on main: vanilla's Hero discount
 * (and any other special price) comes off the shopkeeper's emerald offers, and the owner's chest gets the lower sum.
 */
public class QaShopHeroPriceBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos COUNTER = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos KEEPER = new BlockPos(4, 2, 2);

	//$ gametest_ticks_batch AREA '100' '"qaShopHero"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopHero")
	public void aHeroPaysTheOwnersPrice(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(COUNTER, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(COUNTER);
		counter.setOwner(UUID.randomUUID(), "Frank");
		counter.setItem(0, new ItemStack(Items.DIAMOND, 1));
		counter.setItem(ShopCounterBlockEntity.COLUMNS, new ItemStack(Items.EMERALD, 20));
		helper.setBlock(CHEST, Blocks.CHEST);
		((Container) helper.getBlockEntity(CHEST)).setItem(0, new ItemStack(Items.DIAMOND, 8));
		Villager keeper = helper.spawn(EntityType.VILLAGER, KEEPER);
		keeper.setNoAi(true);
		Jobs.employ(helper.getLevel(), keeper, helper.absolutePos(COUNTER), ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
		QaStageFiveGameTests.Client buyer = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("heroBuyer"));
		ServerPlayer p = buyer.player();
		p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 24000, 0));
		p.setShiftKeyDown(Money.cobbleDollars());
		QaStageFourGameTests.pressOn(p, keeper, buyer.seen());
		helper.assertTrue(p.containerMenu instanceof MerchantMenu, "the trade screen didn't open: " + p.containerMenu);
		MerchantOffer offer = keeper.getOffers().get(0);
		int asked = offer.getCostA().getCount();
		p.closeContainer();
		helper.assertTrue(asked == 20, "a Hero of the Village is asked " + asked + " emeralds for a diamond the owner priced at 20");
		helper.succeed();
	}
}
