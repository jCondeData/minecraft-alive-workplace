package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.school.Schools;
import io.github.jcondedata.aliveworkplace.shop.PriceTagItem;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.shop.Shops;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * B692: without CobbleDollars a Price Tag is paid in emeralds at the server's rate, however big (no longer capped at
 * one stack of 64), over the trade screen's two cost slots. B689: a child exactly 5 blocks from the Teacher's lectern,
 * on its floor, is in class.
 */
public class PriceTagRateAndClassEdgeGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos COUNTER = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos KEEPER = new BlockPos(4, 2, 2);
	private static final BlockPos STATION = new BlockPos(11, 2, 11);
	private static final BlockPos STANDING = new BlockPos(12, 2, 12);

	private static ShopCounterBlockEntity shop(GameTestHelper helper, long price) {
		ItemStack tag = new ItemStack(ModItems.PRICE_TAG);
		PriceTagItem.setPrice(tag, price);
		helper.setBlock(COUNTER, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(COUNTER);
		counter.setOwner(UUID.randomUUID(), "Frank");
		counter.setItem(0, new ItemStack(Items.DIAMOND_BLOCK, 1));
		counter.setItem(ShopCounterBlockEntity.COLUMNS, tag);
		helper.setBlock(CHEST, Blocks.CHEST);
		((Container) helper.getBlockEntity(CHEST)).setItem(0, new ItemStack(Items.DIAMOND_BLOCK, 8));
		return counter;
	}

	private static Villager keeper(GameTestHelper helper) {
		Villager keeper = helper.spawn(EntityType.VILLAGER, KEEPER);
		keeper.setNoAi(true);
		Jobs.employ(helper.getLevel(), keeper, helper.absolutePos(COUNTER), ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
		return keeper;
	}

	/** QA's test (tests/shop-ferry-1031, QaShopFerryBug1031Tests): a 10,000 tag asks 100 emeralds at 100 a piece, or nothing. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aPriceTagOver6400IsPaidAtTheRateNotCappedAt64(GameTestHelper helper) {
		ItemStack tag = new ItemStack(ModItems.PRICE_TAG);
		PriceTagItem.setPrice(tag, 10_000);
		shop(helper, 10_000);
		Villager keeper = keeper(helper);
		Shops.refreshOffers(helper.getLevel(), keeper);
		int asked = keeper.getOffers().isEmpty() ? -1 : keeper.getOffers().get(0).getCostA().getCount() + keeper.getOffers().get(0).getCostB().getCount();
		helper.assertTrue(asked == -1 || asked >= 100,
			"a 10,000 CobbleDollar tag costs " + Shops.emeraldPrice(tag) + " emeralds (the trade sells a diamond block for " + asked + "), expected 100 at 100 a piece");
		helper.succeed();
	}

	/**
	 * A buyer opens the trade screen, puts a stack of emeralds and 36 more in its two slots and takes the diamond block:
	 * the 100 emeralds are gone from the screen, all 100 land in the shop's chest, and the sales log says 100 emeralds,
	 * also after the counter is saved and loaded (a stack saves at most 99).
	 */
	//$ gametest_ticks_batch AREA '100' '"priceTagHundredEmeralds"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "priceTagHundredEmeralds")
	public void aTenThousandTagIsBoughtWithAHundredEmeraldsOnTheTradeScreen(GameTestHelper helper) {
		Leftovers.clear(helper);
		ShopCounterBlockEntity counter = shop(helper, 10_000);
		Container chest = helper.getBlockEntity(CHEST);
		Villager keeper = keeper(helper);
		QaStageFiveGameTests.Client buyer = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("priceTagBuyer"));
		ServerPlayer p = buyer.player();
		// with CobbleDollars an empty-hand click opens the shop's own screen; sneaking opens the trade screen
		p.setShiftKeyDown(Money.cobbleDollars());
		QaStageFourGameTests.pressOn(p, keeper, buyer.seen());
		helper.assertTrue(p.containerMenu instanceof MerchantMenu, "the trade screen didn't open: " + p.containerMenu);
		MerchantOffer offer = keeper.getOffers().get(0);
		helper.assertTrue(offer.getCostA().is(Items.EMERALD) && offer.getCostA().getCount() == 64
			&& offer.getCostB().is(Items.EMERALD) && offer.getCostB().getCount() == 36,
			"a 10,000 tag should ask 64 + 36 emeralds, asks " + offer.getCostA() + " + " + offer.getCostB());
		p.containerMenu.getSlot(0).set(new ItemStack(Items.EMERALD, 64));
		p.containerMenu.getSlot(1).set(new ItemStack(Items.EMERALD, 36));
		QaStageSixGameTests.click(p, 2, 0, ClickType.QUICK_MOVE);
		int got = p.getInventory().countItem(Items.DIAMOND_BLOCK);
		int left = p.containerMenu.getSlot(0).getItem().getCount() + p.containerMenu.getSlot(1).getItem().getCount();
		p.closeContainer();
		helper.assertTrue(got == 1, "the buyer got " + got + " diamond blocks for 100 emeralds");
		helper.assertTrue(left == 0, left + " emeralds were left on the screen after a 100-emerald sale");
		helper.assertTrue(chest.countItem(Items.EMERALD) == 100, "the shop's chest holds " + chest.countItem(Items.EMERALD) + " emeralds, expected 100");
		ShopCounterBlockEntity.Sale sale = counter.sales().get(0);
		helper.assertTrue(sale.paid().is(Items.EMERALD) && sale.paid().getCount() == 100, "the sales log says " + sale.paid());
		CompoundTag saved = counter.saveWithoutMetadata(helper.getLevel().registryAccess());
		counter.loadWithComponents(saved, helper.getLevel().registryAccess());
		ItemStack paid = counter.sales().get(0).paid();
		helper.assertTrue(paid.is(Items.EMERALD) && paid.getCount() == 100, "after a save and load the sales log says " + paid);
		helper.succeed();
	}

	/**
	 * Over two stacks the trade asks emerald blocks and the emeralds left over, exactly the price (13,000: 14 blocks and
	 * 4 emeralds; 12,600: 14 blocks alone); a price over what the two slots hold isn't offered at all, and its tag says so.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void bigTagsAskEmeraldBlocksAndTooDearOnesAreNotSoldCheap(GameTestHelper helper) {
		shop(helper, 13_000);
		Villager keeper = keeper(helper);
		Shops.refreshOffers(helper.getLevel(), keeper);
		MerchantOffer offer = keeper.getOffers().get(0);
		helper.assertTrue(offer.getCostA().is(Items.EMERALD_BLOCK) && offer.getCostA().getCount() == 14
			&& offer.getCostB().is(Items.EMERALD) && offer.getCostB().getCount() == 4,
			"13,000 should ask 14 emerald blocks and 4 emeralds, asks " + offer.getCostA() + " + " + offer.getCostB());
		var twoStacks = Shops.emeraldCosts(126);
		helper.assertTrue(twoStacks[0].item().value() == Items.EMERALD && twoStacks[0].count() == 64
			&& twoStacks[1].item().value() == Items.EMERALD && twoStacks[1].count() == 62,
			"126 emeralds should ask 64 + 62 emeralds: " + twoStacks[0] + " + " + twoStacks[1]);
		var noRest = Shops.emeraldCosts(15 * 9);
		helper.assertTrue(noRest[0].item().value() == Items.EMERALD_BLOCK && noRest[0].count() == 15 && noRest[1] == null,
			"135 emeralds should ask 15 emerald blocks alone: " + noRest[0] + " + " + noRest[1]);
		helper.assertTrue(Shops.emeraldCosts(64)[1] == null && Shops.emeraldCosts(64)[0].count() == 64, "64 emeralds fit in one slot");
		helper.assertTrue(Shops.emeraldCosts(Shops.MAX_TRADE_EMERALDS) != null && Shops.emeraldCosts(Shops.MAX_TRADE_EMERALDS + 1) == null,
			"the most the screen can ask is " + Shops.MAX_TRADE_EMERALDS);

		ItemStack dear = new ItemStack(ModItems.PRICE_TAG);
		PriceTagItem.setPrice(dear, 70_000);
		((ShopCounterBlockEntity) helper.getBlockEntity(COUNTER)).setItem(ShopCounterBlockEntity.COLUMNS, dear);
		Shops.refreshOffers(helper.getLevel(), keeper);
		helper.assertTrue(keeper.getOffers().isEmpty(), "a 70,000 tag (700 emeralds) was offered for " + (keeper.getOffers().isEmpty() ? ""
			: keeper.getOffers().get(0).getCostA() + " + " + keeper.getOffers().get(0).getCostB()));
		if (!Money.cobbleDollars()) {
			List<String> lines = dear.getTooltipLines(Item.TooltipContext.of(helper.getLevel()), null, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
			helper.assertTrue(lines.contains("Too dear for the trade screen without CobbleDollars: it asks at most 584 emeralds (in emerald blocks)"),
				"the 70,000 tag's tooltip: " + lines);
			List<String> fine = new ItemStack(ModItems.PRICE_TAG).getTooltipLines(Item.TooltipContext.of(helper.getLevel()), null, TooltipFlag.NORMAL)
				.stream().map(Component::getString).toList();
			helper.assertFalse(fine.stream().anyMatch(s -> s.startsWith("Too dear")), "a blank tag says it's too dear: " + fine);
		}
		helper.succeed();
	}

	/** QA's test (tests/service-jobs-1031, QaServiceJobsBug1031Tests): a child 5 blocks from the lectern has lessons. */
	//$ gametest_ticks_batch AREA '300' '"qaTeacherFive"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaTeacherFive")
	public void aChildFiveBlocksFromTheLecternHasLessons(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(3000);
		helper.setBlock(STATION, Blocks.LECTERN);
		ServerPlayer player = StationsSpecGameTests.player(helper);
		Villager teacher = helper.spawn(EntityType.VILLAGER, STANDING);
		StationsSpecGameTests.rightClick(player, teacher, new ItemStack(Items.BOOK), true);
		helper.assertTrue(teacher.getVillagerData().getProfession() == ModVillagers.TEACHER, "setup: no teacher");
		Villager child = helper.spawn(EntityType.VILLAGER, STATION.west(5));
		child.setAge(-24000);
		child.setNoAi(true);
		helper.runAfterDelay(150, () -> {
			helper.assertTrue(Schools.lessons(child) > 0, "the child 5 blocks from the lectern had no lessons");
			helper.succeed();
		});
	}

	/** The class still ends at 5 blocks: a child 6 blocks off on the same floor is called over but has no lessons yet. */
	//$ gametest_ticks_batch AREA '300' '"teacherSixBlocks"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "teacherSixBlocks")
	public void aChildSixBlocksFromTheLecternIsCalledNotTaught(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(3000);
		helper.setBlock(STATION, Blocks.LECTERN);
		ServerPlayer player = StationsSpecGameTests.player(helper);
		Villager teacher = helper.spawn(EntityType.VILLAGER, STANDING);
		StationsSpecGameTests.rightClick(player, teacher, new ItemStack(Items.BOOK), true);
		helper.assertTrue(teacher.getVillagerData().getProfession() == ModVillagers.TEACHER, "setup: no teacher");
		Villager child = helper.spawn(EntityType.VILLAGER, STATION.west(6));
		child.setAge(-24000);
		child.setNoAi(true);
		helper.runAfterDelay(150, () -> {
			helper.assertTrue(Schools.lessons(child) == 0, "the child 6 blocks from the lectern had " + Schools.lessons(child) + " lessons");
			helper.assertTrue(child.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).isPresent(),
				"the child 6 blocks off wasn't called to class");
			helper.succeed();
		});
	}
}
