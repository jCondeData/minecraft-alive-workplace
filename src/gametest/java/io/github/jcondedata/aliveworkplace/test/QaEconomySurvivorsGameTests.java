package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.sift.SifterWork;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

/**
 * QA (lane-c-1025-0352): tests that kill the nightly mutation survivors in the shop counter
 * ({@code ShopCounterBlockEntity#canTakeItemThroughFace} / {@code #canPlaceItemThroughFace} returning true), the trade
 * screen's result slot ({@code MerchantResultSlotMixin#mayPickup} returning true) and the Storehouse board's hand-over
 * ({@code Requests.lower}'s {@code count <= 0} turned to {@code count <= 1}), written from B349, B348 and B344 (ROADMAP)
 * and the guide's "players can't steal a shop's stock with hoppers".
 *
 * <p>They complement {@link QaHallJobsShopsGameTests} (top and bottom hoppers, a shift-click on a lot the chests no
 * longer hold, one cobblestone for a builder) with other ways in: a hopper beside the counter, automation that names no
 * side, a plain click on a lot the chests only partly hold, the in-stock sale through the same click, and one item for
 * a posted request (not a builder's missing list).
 *
 * <p>Note for the canTake/canPlace mutants: the counter offers no slot on any side ({@code getSlotsForFace}), so vanilla
 * hoppers never ask those two methods per slot; real hoppers alone can't tell the mutants apart. What can: vanilla's
 * {@code HopperBlockEntity.addItem} with no side (it asks {@code canPlaceItemThroughFace(slot, stack, null)} for every
 * slot) and other mods' pipes that ask the per-slot methods directly, so both are checked here.
 *
 * <p>Likewise for {@code mayPickup}: when it refuses, it first refreshes the result ({@code updateSellItem}), and
 * {@code MerchantContainerMixin} empties a lot the chests don't hold, so every vanilla click (pick up, shift-click,
 * throw, swap) finds an empty slot whatever the answer. The answer itself is checked as well as the click.
 */
public class QaEconomySurvivorsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String SMALL = "aliveworkplace_test:build_area";

	// --- B349: hoppers and pipes around a Shop Counter ----------------------------------------------------------------

	/**
	 * A hopper beside a Shop Counter pointing into it (bread in it) pushes nothing in, a hopper under it pulls nothing
	 * out, in 60 ticks; automation that names no side (vanilla's {@code HopperBlockEntity.addItem} with a null side, as a
	 * pipe that doesn't say where it comes from) puts nothing in either; and no slot gives or takes through any face or
	 * none. The lot and its price stay exactly as the owner set them.
	 */
	//$ gametest_ticks_batch SMALL '200' '"qaEsCounterSide"'
	@GameTest(template = SMALL, timeoutTicks = 200, batch = "qaEsCounterSide")
	public void aHopperBesideOrUnderAShopCounterMovesNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos counterAt = new BlockPos(3, 3, 3);
		BlockPos underAt = new BlockPos(3, 2, 3);
		BlockPos besideAt = new BlockPos(2, 3, 3);
		helper.setBlock(underAt, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.SOUTH));
		helper.setBlock(counterAt, ModBlocks.SHOP_COUNTER);
		// the hopper west of the counter points east, into it
		helper.setBlock(besideAt, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
		ShopCounterBlockEntity counter = helper.getBlockEntity(counterAt);
		counter.setOwner(UUID.randomUUID(), "Frank");
		counter.setItem(0, new ItemStack(Items.IRON_INGOT, 8));
		counter.setItem(ShopCounterBlockEntity.COLUMNS, new ItemStack(Items.EMERALD, 3));
		HopperBlockEntity under = helper.getBlockEntity(underAt);
		HopperBlockEntity beside = helper.getBlockEntity(besideAt);
		beside.setItem(0, new ItemStack(Items.BREAD, 5));

		ItemStack sideless = HopperBlockEntity.addItem(null, counter, new ItemStack(Items.BREAD, 3), null);
		helper.assertTrue(sideless.getCount() == 3, "automation without a side put " + (3 - sideless.getCount()) + " bread into the counter");
		for (Direction face : Direction.values()) {
			ItemStack rest = HopperBlockEntity.addItem(null, counter, new ItemStack(Items.BREAD, 2), face);
			helper.assertTrue(rest.getCount() == 2, "automation from " + face + " put " + (2 - rest.getCount()) + " bread into the counter");
		}
		for (int slot = 0; slot < counter.getContainerSize(); slot++) {
			ItemStack inSlot = counter.getItem(slot).isEmpty() ? new ItemStack(Items.IRON_INGOT) : counter.getItem(slot);
			helper.assertFalse(counter.canPlaceItemThroughFace(slot, new ItemStack(Items.BREAD), null), "slot " + slot + " takes in through no side");
			for (Direction face : Direction.values()) {
				helper.assertFalse(counter.canTakeItemThroughFace(slot, inSlot, face), "slot " + slot + " gives out through " + face);
				helper.assertFalse(counter.canPlaceItemThroughFace(slot, new ItemStack(Items.BREAD), face), "slot " + slot + " takes in through " + face);
			}
		}
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(under.isEmpty(), "the hopper under the counter pulled " + under.getItem(0) + " " + under.getItem(1));
			helper.assertTrue(beside.countItem(Items.BREAD) == 5, "the hopper beside pushed " + (5 - beside.countItem(Items.BREAD)) + " bread in");
			for (int slot = 0; slot < counter.getContainerSize(); slot++) {
				helper.assertTrue(!counter.getItem(slot).is(Items.BREAD), "bread got into slot " + slot);
			}
			helper.assertTrue(counter.goods(0).is(Items.IRON_INGOT) && counter.goods(0).getCount() == 8, "the lot changed: " + counter.goods(0));
			helper.assertTrue(counter.price(0).is(Items.EMERALD) && counter.price(0).getCount() == 3, "the price changed: " + counter.price(0));
			helper.succeed();
		});
	}

	// --- B348: the trade screen's result slot -------------------------------------------------------------------------

	private static MerchantMenu openTrades(GameTestHelper helper, QaEconomyGameTests.Shop shop, QaStageFiveGameTests.Client c) {
		ServerPlayer p = c.player();
		QaStageFourGameTests.pressOn(p, shop.keeper(), c.seen());
		helper.assertTrue(p.containerMenu instanceof MerchantMenu, "setup: the right-click didn't open the trades: " + p.containerMenu);
		MerchantMenu menu = (MerchantMenu) p.containerMenu;
		menu.getSlot(0).set(new ItemStack(Items.EMERALD, 1));
		helper.assertTrue(menu.getSlot(2).getItem().is(Items.COBBLESTONE) && menu.getSlot(2).getItem().getCount() == 16,
			"setup: the result slot shows " + menu.getSlot(2).getItem());
		return menu;
	}

	/**
	 * The chests drop to 10 cobblestone (a lot is 16) while the buyer has the trade screen open with the emerald in: a plain
	 * click on the result picks up nothing (the cursor stays empty), the shop isn't paid, the 10 stay in the chest, and
	 * closing the screen gives the emerald back.
	 */
	//$ gametest_batch AREA '"qaEsTradeShort"'
	@GameTest(template = AREA, batch = "qaEsTradeShort")
	public void aPlainClickTakesNoLotTheChestsOnlyPartlyHold(GameTestHelper helper) {
		QaEconomyGameTests.Shop shop = QaEconomyGameTests.shop(helper, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 1),
			new ItemStack(Items.COBBLESTONE, 32));
		QaStageFiveGameTests.Client c = QaEconomyGameTests.buyer(helper);
		ServerPlayer p = c.player();
		MerchantMenu menu = openTrades(helper, shop, c);
		shop.chest().setItem(0, new ItemStack(Items.COBBLESTONE, 10));
		// What the slot answers whoever asks first (vanilla's clicks, other mods' auto-trading or inventory tweaks): no.
		// For vanilla's own clicks the refusal's refresh already empties the slot, so the click alone can't see the answer.
		helper.assertFalse(menu.getSlot(2).mayPickup(p), "the slot lets the buyer take a 16 lot from a shop holding 10");
		QaStageSixGameTests.click(p, 2, 0, ClickType.PICKUP);
		helper.assertTrue(QaEconomyGameTests.carried(p, Items.COBBLESTONE) == 0,
			"the buyer got " + QaEconomyGameTests.carried(p, Items.COBBLESTONE) + " cobblestone from a shop holding 10 of a 16 lot");
		helper.assertTrue(shop.chest().countItem(Items.EMERALD) == 0, "the short shop was paid " + shop.chest().countItem(Items.EMERALD));
		helper.assertTrue(shop.chest().countItem(Items.COBBLESTONE) == 10, "the chest now holds " + shop.chest().countItem(Items.COBBLESTONE));
		helper.assertTrue(menu.getSlot(0).getItem().is(Items.EMERALD) || QaEconomyGameTests.carried(p, Items.EMERALD) == 1,
			"the emerald vanished: payment slot " + menu.getSlot(0).getItem());
		p.closeContainer();
		helper.assertTrue(QaEconomyGameTests.carried(p, Items.EMERALD) == 1, "the unspent emerald didn't come back: " + QaEconomyGameTests.carried(p, Items.EMERALD));
		helper.succeed();
	}

	/**
	 * The other side of the same slot: with the lot in the chests, the same plain click buys it. The buyer holds 16
	 * cobblestone on the cursor, the emerald is spent, and the chest has 16 cobblestone fewer and the emerald.
	 */
	//$ gametest_batch AREA '"qaEsTradeStocked"'
	@GameTest(template = AREA, batch = "qaEsTradeStocked")
	public void aPlainClickBuysALotTheChestsHold(GameTestHelper helper) {
		QaEconomyGameTests.Shop shop = QaEconomyGameTests.shop(helper, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 1),
			new ItemStack(Items.COBBLESTONE, 32));
		QaStageFiveGameTests.Client c = QaEconomyGameTests.buyer(helper);
		ServerPlayer p = c.player();
		MerchantMenu menu = openTrades(helper, shop, c);
		helper.assertTrue(menu.getSlot(2).mayPickup(p), "a stocked lot can't be taken");
		QaStageSixGameTests.click(p, 2, 0, ClickType.PICKUP);
		helper.assertTrue(QaEconomyGameTests.carried(p, Items.COBBLESTONE) == 16, "the buyer got " + QaEconomyGameTests.carried(p, Items.COBBLESTONE) + " cobblestone");
		helper.assertTrue(menu.getSlot(0).getItem().isEmpty() && QaEconomyGameTests.carried(p, Items.EMERALD) == 0,
			"the emerald wasn't spent: payment slot " + menu.getSlot(0).getItem());
		helper.assertTrue(shop.chest().countItem(Items.COBBLESTONE) == 16, "the chest holds " + shop.chest().countItem(Items.COBBLESTONE) + " cobblestone after the sale");
		helper.assertTrue(shop.chest().countItem(Items.EMERALD) == 1, "the shop was paid " + shop.chest().countItem(Items.EMERALD));
		helper.succeed();
	}

	// --- B344: one item handed over for a posted request --------------------------------------------------------------

	/**
	 * A sifter with nothing to sift asks for 16 gravel (posted, not a builder's list). A player with ONE gravel clicks it
	 * on the Storehouse board: the gravel goes into the sifter's chest and the board, still open, asks for 15 at once
	 * (as does what the sifter is waiting for), so a second click hands nothing more.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaEsPostOne"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaEsPostOne")
	public void oneItemForAPostedRequestLowersItAtOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		helper.setDayTime(2000);
		BlockPos sieve = new BlockPos(2, 2, 2);
		BlockPos chestAt = new BlockPos(2, 2, 4);
		BlockPos storehouse = new BlockPos(14, 2, 2);
		helper.setBlock(sieve, ModBlocks.SIEVE);
		helper.setBlock(chestAt, Blocks.CHEST);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		Villager sifter = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), sifter, helper.absolutePos(sieve), ModVillagers.SIEVE_POI, ModVillagers.SIFTER);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(SifterWork.sift(helper.getLevel(), sifter, helper.absolutePos(sieve)) == null, "setup: sifted from an empty chest");
			QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaEsOne"));
			ServerPlayer p = c.player();
			Container chest = helper.getBlockEntity(chestAt);
			p.getInventory().setItem(5, new ItemStack(Items.GRAVEL, 1));
			QaStageFourGameTests.press(helper, p, storehouse);
			helper.assertTrue(p.containerMenu instanceof ChoiceMenu, "setup: the board didn't open: " + p.containerMenu);
			ChoiceMenu board = (ChoiceMenu) p.containerMenu;
			int slot = QaStageSixGameTests.slotOf(board, Items.GRAVEL);
			helper.assertTrue(slot >= 0 && board.icon(slot).getCount() == 16, "setup: the board asks for " + (slot < 0 ? "no gravel" : board.icon(slot).getCount()));
			QaStageSixGameTests.click(p, slot, 0, ClickType.PICKUP);
			helper.assertTrue(chest.countItem(Items.GRAVEL) == 1 && p.getInventory().countItem(Items.GRAVEL) == 0,
				"the click: chest " + chest.countItem(Items.GRAVEL) + ", kept " + p.getInventory().countItem(Items.GRAVEL));
			int after = QaStageSixGameTests.slotOf(board, Items.GRAVEL);
			helper.assertTrue(after >= 0 && board.icon(after).getCount() == 15, "after one gravel the board asks for " + (after < 0 ? "none" : board.icon(after).getCount()));
			int asked = Requests.of(helper.getLevel(), sifter).stream().filter(r -> r.accepts().test(new ItemStack(Items.GRAVEL)))
				.mapToInt(Requests.Request::count).sum();
			helper.assertTrue(asked == 15, "the sifter still asks for " + asked + " gravel");
			helper.succeed();
		});
	}
}
