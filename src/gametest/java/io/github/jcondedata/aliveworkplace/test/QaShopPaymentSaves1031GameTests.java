package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.shop.PriceTagItem;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.shop.Shops;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane E, saves and config, 2026-10-31) for B692 (a Price Tag without CobbleDollars is paid in full at the server's
 * rate, over the trade screen's two cost slots, or not offered; the sales log keeps the whole sum, saved beside the
 * stack since a saved stack holds at most 99; what doesn't fit the chests lands in whole stacks), from the bug's text
 * and the guide (docs/guide/price-tag.md, shopkeeper.md), not from the code:
 * <ul>
 * <li>every price from 1 to 700 emeralds: asked exactly (blocks counted as nine), each slot a legal stack, or not at all
 * past 584;</li>
 * <li>the sales log across a save: 99 and 100 (the stack's save limit from both sides), 584, a sale paid in emerald
 * blocks alone, and a log written before the fix (no {@code paidCount});</li>
 * <li>the server's rate at both ends of its range (1 and 10,000 dollars an emerald);</li>
 * <li>a full chest: a 100-emerald payment lands on the counter as whole stacks, nothing lost.</li>
 * </ul>
 */
public class QaShopPaymentSaves1031GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos COUNTER = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos KEEPER = new BlockPos(4, 2, 2);

	static ShopCounterBlockEntity shop(GameTestHelper helper, ItemStack price) {
		helper.setBlock(COUNTER, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(COUNTER);
		counter.setOwner(UUID.randomUUID(), "Frank");
		counter.setItem(0, new ItemStack(Items.DIAMOND_BLOCK, 1));
		counter.setItem(ShopCounterBlockEntity.COLUMNS, price);
		helper.setBlock(CHEST, Blocks.CHEST);
		((Container) helper.getBlockEntity(CHEST)).setItem(0, new ItemStack(Items.DIAMOND_BLOCK, 8));
		return counter;
	}

	static ItemStack tag(long dollars) {
		ItemStack tag = new ItemStack(ModItems.PRICE_TAG);
		PriceTagItem.setPrice(tag, dollars);
		return tag;
	}

	static Villager keeper(GameTestHelper helper) {
		Villager keeper = helper.spawn(EntityType.VILLAGER, KEEPER);
		keeper.setNoAi(true);
		Jobs.employ(helper.getLevel(), keeper, helper.absolutePos(COUNTER), ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
		return keeper;
	}

	private static int value(ItemCost cost) {
		if (cost == null) {
			return 0;
		}
		return cost.item().value() == Items.EMERALD_BLOCK ? 9 * cost.count() : cost.item().value() == Items.EMERALD ? cost.count() : -100_000;
	}

	private static int value(ItemStack cost) {
		return cost.is(Items.EMERALD_BLOCK) ? 9 * cost.getCount() : cost.is(Items.EMERALD) ? cost.getCount() : cost.isEmpty() ? 0 : -100_000;
	}

	/** Every price 1-700: the two slots ask exactly it in emeralds and emerald blocks, each a stack of 1-64; past 584 nothing. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyEmeraldPriceIsAskedExactlyOrNotAtAll(GameTestHelper helper) {
		for (int n = 1; n <= 700; n++) {
			ItemCost[] costs = Shops.emeraldCosts(n);
			if (n > 584) {
				helper.assertTrue(costs == null, n + " emeralds is more than the two slots hold, yet it was asked as " + (costs == null ? "" : costs[0] + " + " + costs[1]));
				continue;
			}
			helper.assertTrue(costs != null && costs.length == 2 && costs[0] != null, n + " emeralds wasn't asked at all");
			for (ItemCost c : costs) {
				if (c != null) {
					helper.assertTrue(c.count() >= 1 && c.count() <= 64, n + " emeralds asks a stack of " + c.count() + " in one slot");
				}
			}
			int asked = value(costs[0]) + value(costs[1]);
			helper.assertTrue(asked == n, n + " emeralds is asked as " + costs[0] + " + " + costs[1] + " (" + asked + " emeralds)");
		}
		helper.succeed();
	}

	/**
	 * The sales log through a save and load: 99 emeralds (the most a saved stack holds) and 100 (one over), 584 (the
	 * dearest sale), 15 emerald blocks paid alone; and a log saved before B692's fix (an entry without paidCount) reads
	 * as it was.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void theSalesLogKeepsEveryPaymentAcrossASave(GameTestHelper helper) {
		ShopCounterBlockEntity counter = shop(helper, tag(100));
		int[] sums = {99, 100, 584};
		for (int n : sums) {
			counter.logSale(new ShopCounterBlockEntity.Sale(3, "Ann", new ItemStack(Items.DIAMOND_BLOCK), 0, new ItemStack(Items.EMERALD, n)));
		}
		counter.logSale(new ShopCounterBlockEntity.Sale(3, "Bob", new ItemStack(Items.DIAMOND_BLOCK), 0, new ItemStack(Items.EMERALD_BLOCK, 15)));
		var access = helper.getLevel().registryAccess();
		CompoundTag saved = counter.saveWithoutMetadata(access);
		counter.loadWithComponents(saved, access);
		List<ShopCounterBlockEntity.Sale> sales = counter.sales();
		helper.assertTrue(sales.size() == 4, "after a save and load the log has " + sales.size() + " sales, expected 4");
		java.util.List<String> got = sales.stream().map(s -> s.paid().getCount() + " " + s.paid().getItem()).toList();
		for (int n : sums) {
			helper.assertTrue(sales.stream().anyMatch(s -> s.paid().is(Items.EMERALD) && s.paid().getCount() == n),
				"a sale of " + n + " emeralds didn't come back from the save: " + got);
		}
		helper.assertTrue(sales.stream().anyMatch(s -> s.paid().is(Items.EMERALD_BLOCK) && s.paid().getCount() == 15),
			"the sale paid in 15 emerald blocks didn't come back: " + got);
		// a second save is the first (nothing grows or shrinks on each load)
		CompoundTag again = counter.saveWithoutMetadata(access);
		helper.assertTrue(again.get("sales").equals(saved.get("sales")), "the sales log saves differently the second time:\n" + saved.get("sales") + "\n" + again.get("sales"));

		// A log written by 0.151.1 (before the fix): no paidCount anywhere, stacks of at most 64.
		BlockPos elsewhere = new BlockPos(6, 2, 6);
		helper.setBlock(elsewhere, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity old = helper.getBlockEntity(elsewhere);
		old.logSale(new ShopCounterBlockEntity.Sale(2, "Cy", new ItemStack(Items.DIAMOND_BLOCK), 0, new ItemStack(Items.EMERALD, 40)));
		old.logSale(new ShopCounterBlockEntity.Sale(2, "Cy", new ItemStack(Items.DIAMOND_BLOCK), 0, new ItemStack(Items.GOLD_INGOT, 64)));
		CompoundTag oldTag = old.saveWithoutMetadata(access);
		ListTag log = oldTag.getList("sales", Tag.TAG_COMPOUND);
		for (int i = 0; i < log.size(); i++) {
			helper.assertFalse(log.getCompound(i).contains("paidCount"), "a sale of 64 or fewer saved a paidCount: " + log.getCompound(i));
		}
		old.loadWithComponents(oldTag, access);
		helper.assertTrue(old.sales().size() == 2 && old.sales().stream().anyMatch(s -> s.paid().is(Items.EMERALD) && s.paid().getCount() == 40)
			&& old.sales().stream().anyMatch(s -> s.paid().is(Items.GOLD_INGOT) && s.paid().getCount() == 64),
			"an old sales log read back as " + old.sales().stream().map(s -> s.paid().toString()).toList());
		helper.succeed();
	}

	/**
	 * The server's rate at both ends of its range (config dollarsPerEmerald, 1-10,000): at 1 a dollar is an emerald,
	 * so a 584 tag asks 64 blocks and 8 emeralds and a 585 one isn't offered; at 10,000 a 1-dollar tag still asks an
	 * emerald, 10,000 one and 10,001 two (rounded up).
	 */
	//$ gametest_batch AREA '"qaShopRateEnds"'
	@GameTest(template = AREA, batch = "qaShopRateEnds")
	public void theRateAtBothEndsOfItsRange(GameTestHelper helper) {
		int was = Money.DOLLARS_PER_EMERALD;
		try {
			Money.DOLLARS_PER_EMERALD = 1;
			helper.assertTrue(PriceTagItem.emeralds(584) == 584 && PriceTagItem.emeralds(585) == 585 && PriceTagItem.emeralds(1_000_000_000L) == 1_000_000_000,
				"at 1 a piece: 584 -> " + PriceTagItem.emeralds(584) + ", 585 -> " + PriceTagItem.emeralds(585) + ", 1e9 -> " + PriceTagItem.emeralds(1_000_000_000L));
			ShopCounterBlockEntity counter = shop(helper, tag(584));
			Villager keeper = keeper(helper);
			Shops.refreshOffers(helper.getLevel(), keeper);
			helper.assertTrue(keeper.getOffers().size() == 1, "at 1 a piece a 584 tag made " + keeper.getOffers().size() + " offers");
			MerchantOffer offer = keeper.getOffers().get(0);
			helper.assertTrue(value(offer.getCostA()) + value(offer.getCostB()) == 584,
				"at 1 a piece a 584 tag asks " + offer.getCostA() + " + " + offer.getCostB());
			counter.setItem(ShopCounterBlockEntity.COLUMNS, tag(585));
			Shops.refreshOffers(helper.getLevel(), keeper);
			helper.assertTrue(keeper.getOffers().isEmpty(), "at 1 a piece a 585 tag was offered for "
				+ (keeper.getOffers().isEmpty() ? "" : keeper.getOffers().get(0).getCostA() + " + " + keeper.getOffers().get(0).getCostB()));

			Money.DOLLARS_PER_EMERALD = 10_000;
			helper.assertTrue(PriceTagItem.emeralds(1) == 1 && PriceTagItem.emeralds(10_000) == 1 && PriceTagItem.emeralds(10_001) == 2,
				"at 10,000 a piece: 1 -> " + PriceTagItem.emeralds(1) + ", 10,000 -> " + PriceTagItem.emeralds(10_000) + ", 10,001 -> " + PriceTagItem.emeralds(10_001));
			counter.setItem(ShopCounterBlockEntity.COLUMNS, tag(1_000_000_000L));
			Shops.refreshOffers(helper.getLevel(), keeper);
			helper.assertTrue(keeper.getOffers().isEmpty(), "at 10,000 a piece the dearest tag (1,000,000,000: 100,000 emeralds) was offered for "
				+ (keeper.getOffers().isEmpty() ? "" : keeper.getOffers().get(0).getCostA() + " + " + keeper.getOffers().get(0).getCostB()));
			counter.setItem(ShopCounterBlockEntity.COLUMNS, tag(5_840_000L));
			Shops.refreshOffers(helper.getLevel(), keeper);
			helper.assertTrue(keeper.getOffers().size() == 1 && value(keeper.getOffers().get(0).getCostA()) + value(keeper.getOffers().get(0).getCostB()) == 584,
				"at 10,000 a piece a 5,840,000 tag (584 emeralds) " + (keeper.getOffers().isEmpty() ? "wasn't offered" : "asks "
				+ keeper.getOffers().get(0).getCostA() + " + " + keeper.getOffers().get(0).getCostB()));
		} catch (RuntimeException e) {
			Money.DOLLARS_PER_EMERALD = was;
			throw e;
		}
		Money.DOLLARS_PER_EMERALD = was;
		helper.succeed();
	}

	/**
	 * The shop's chest is full: a buyer pays a 10,000 tag's 100 emeralds on the trade screen and every one of them lands
	 * on the counter, as stacks of at most 64 (the commit: "payments that don't fit the chests drop in whole stacks").
	 */
	//$ gametest_ticks_batch AREA '100' '"qaShopFullChest"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaShopFullChest")
	public void aFullChestLeavesTheWholePaymentOnTheCounter(GameTestHelper helper) {
		Leftovers.clear(helper);
		shop(helper, tag(10_000));
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 1; i < chest.getContainerSize(); i++) {
			chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		Villager keeper = keeper(helper);
		QaStageFiveGameTests.Client buyer = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaFullChestBuyer"));
		ServerPlayer p = buyer.player();
		p.setShiftKeyDown(Money.cobbleDollars());
		QaStageFourGameTests.pressOn(p, keeper, buyer.seen());
		helper.assertTrue(p.containerMenu instanceof MerchantMenu, "the trade screen didn't open: " + p.containerMenu);
		p.containerMenu.getSlot(0).set(new ItemStack(Items.EMERALD, 64));
		p.containerMenu.getSlot(1).set(new ItemStack(Items.EMERALD, 36));
		QaStageSixGameTests.click(p, 2, 0, ClickType.QUICK_MOVE);
		p.closeContainer();
		helper.assertTrue(p.getInventory().countItem(Items.DIAMOND_BLOCK) == 1, "the buyer got no diamond block");
		// the chest held a diamond block in slot 0 (sold) and cobblestone elsewhere: room for at most one stack in slot 0
		int inChest = chest.countItem(Items.EMERALD);
		AABB box = new AABB(helper.absolutePos(COUNTER)).inflate(4);
		List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(Items.EMERALD));
		int onCounter = dropped.stream().mapToInt(e -> e.getItem().getCount()).sum();
		helper.assertTrue(inChest + onCounter == 100, inChest + " emeralds in the chest and " + onCounter + " on the counter, expected 100 in all");
		for (ItemEntity e : dropped) {
			helper.assertTrue(e.getItem().getCount() <= e.getItem().getMaxStackSize(), "a dropped stack of " + e.getItem().getCount() + " emeralds");
		}
		helper.succeed();
	}
}
