package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * B349: a Shop Counter's price list belongs to its owner. Hoppers (and other automation) can neither pull the samples
 * and prices out nor push new prices in; the owner still sets them through the counter's own screen.
 */
public class ShopCounterHopperGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** A hopper under a counter pulls nothing out of its price list, while a hopper under a chest beside it does. */
	//$ gametest_ticks_batch AREA '100' '"shop_counter_hopper"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "shop_counter_hopper")
	public void aHopperUnderACounterPullsNothingOut(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos counterPos = new BlockPos(2, 3, 2);
		BlockPos hopperPos = new BlockPos(2, 2, 2);
		helper.setBlock(hopperPos, Blocks.HOPPER);
		helper.setBlock(counterPos, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(counterPos);
		counter.setOwner(UUID.randomUUID(), "Frank");
		counter.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		counter.setItem(ShopCounterBlockEntity.COLUMNS, new ItemStack(Items.EMERALD, 2));

		// The control: the same hopper set-up under a chest does empty it, so the hoppers really run.
		BlockPos chestPos = new BlockPos(5, 3, 2);
		helper.setBlock(new BlockPos(5, 2, 2), Blocks.HOPPER);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 2));

		helper.runAfterDelay(60, () -> {
			Container hopper = helper.getBlockEntity(hopperPos);
			helper.assertTrue(hopper.isEmpty(), "the hopper under the counter should hold nothing, not " + hopper.getItem(0));
			helper.assertTrue(counter.goods(0).is(Items.COBBLESTONE) && counter.goods(0).getCount() == 16,
				"the sample should still be 16 cobblestone, not " + counter.goods(0));
			helper.assertTrue(counter.price(0).is(Items.EMERALD) && counter.price(0).getCount() == 2,
				"the price should still be 2 emeralds, not " + counter.price(0));
			helper.assertTrue(chest.isEmpty(), "the control hopper should have emptied the chest");
			helper.succeed();
		});
	}

	/** A hopper on top of a counter pushes nothing in as a new price; it keeps its items. */
	//$ gametest_ticks_batch AREA '100' '"shop_counter_hopper"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "shop_counter_hopper")
	public void aHopperOnACounterPushesNothingIn(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos counterPos = new BlockPos(2, 2, 2);
		BlockPos hopperPos = new BlockPos(2, 3, 2);
		helper.setBlock(counterPos, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(counterPos);
		counter.setOwner(UUID.randomUUID(), "Frank");
		counter.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		counter.setItem(ShopCounterBlockEntity.COLUMNS, new ItemStack(Items.EMERALD, 2));
		helper.setBlock(hopperPos, Blocks.HOPPER); // faces down, into the counter
		Container hopper = helper.getBlockEntity(hopperPos);
		hopper.setItem(0, new ItemStack(Items.DIRT, 5));

		helper.runAfterDelay(60, () -> {
			helper.assertTrue(hopper.getItem(0).is(Items.DIRT) && hopper.getItem(0).getCount() == 5,
				"the hopper should keep its 5 dirt, not " + hopper.getItem(0));
			for (int slot = 0; slot < counter.getContainerSize(); slot++) {
				helper.assertFalse(counter.getItem(slot).is(Items.DIRT), "dirt got into the price list at slot " + slot);
			}
			helper.assertTrue(counter.goods(0).getCount() == 16 && counter.price(0).getCount() == 2,
				"the existing column should be untouched");
			helper.succeed();
		});
	}

	/** The owner still opens the counter by using it and sets a column's sample and price on its screen. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void theOwnerStillSetsPricesOnTheCountersScreen(GameTestHelper helper) {
		BlockPos counterPos = new BlockPos(2, 2, 2);
		helper.setBlock(counterPos, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(counterPos);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		owner.setGameMode(GameType.SURVIVAL);
		BlockPos at = helper.absolutePos(counterPos);
		helper.getBlockState(counterPos).useWithoutItem(helper.getLevel(), owner,
			new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
		helper.assertTrue(owner.getUUID().equals(counter.owner()), "the first player to use the counter should own it");
		helper.assertTrue(owner.containerMenu instanceof ChestMenu menu && menu.getContainer() == counter,
			"using the counter should open its price list, not " + owner.containerMenu);

		ChestMenu menu = (ChestMenu) owner.containerMenu;
		menu.setCarried(new ItemStack(Items.COBBLESTONE, 16));
		menu.clicked(0, 0, ClickType.PICKUP, owner);
		menu.setCarried(new ItemStack(Items.EMERALD, 2));
		menu.clicked(ShopCounterBlockEntity.COLUMNS, 0, ClickType.PICKUP, owner);
		helper.assertTrue(counter.goods(0).is(Items.COBBLESTONE) && counter.goods(0).getCount() == 16,
			"the owner's sample should be in the price list, not " + counter.goods(0));
		helper.assertTrue(counter.price(0).is(Items.EMERALD) && counter.price(0).getCount() == 2,
			"the owner's price should be in the price list, not " + counter.price(0));

		menu.setCarried(ItemStack.EMPTY);
		menu.clicked(ShopCounterBlockEntity.COLUMNS, 0, ClickType.PICKUP, owner);
		helper.assertTrue(counter.price(0).isEmpty() && menu.getCarried().is(Items.EMERALD),
			"the owner should be able to take a price back out");
		helper.succeed();
	}
}
