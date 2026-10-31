package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.shop.Shops;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * B716: a shop sells only from, and pays only into, its own chests. On a market street a chest nearer another
 * player's Shop Counter is that shop's; at an even distance one counter keeps it, the same one whoever asks; a lone shop
 * and one owner's counters side by side work as before.
 */
public class ShopNeighboursGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	/** Frank's counter is at (2, 2, 2) with his chest at (2, 2, 4) (QaEconomyGameTests.shop); Bob's shop is 6 blocks east. */
	private static final BlockPos BOB_COUNTER = new BlockPos(8, 2, 2);
	private static final BlockPos BOB_CHEST = new BlockPos(8, 2, 4);
	/** As far from Frank's counter as from Bob's (25 squared each). */
	private static final BlockPos MIDDLE_CHEST = new BlockPos(5, 2, 6);

	private static ShopCounterBlockEntity bob(GameTestHelper helper, UUID owner) {
		helper.setBlock(BOB_COUNTER, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity bob = helper.getBlockEntity(BOB_COUNTER);
		bob.setOwner(owner, "Bob");
		bob.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		bob.setItem(ShopCounterBlockEntity.COLUMNS, new ItemStack(Items.EMERALD, 2));
		return bob;
	}

	private static Container chest(GameTestHelper helper, BlockPos pos, ItemStack... stock) {
		helper.setBlock(pos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(pos);
		for (int i = 0; i < stock.length; i++) {
			chest.setItem(i, stock[i]);
		}
		return chest;
	}

	/** A shop with no neighbour sells from its chest and the emerald lands in that chest (as before B716). */
	//$ gametest_ticks_batch AREA '200' '"shopNeighboursLone"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "shopNeighboursLone")
	public void aLoneShopStillSellsAndIsPaidIntoItsChest(GameTestHelper helper) {
		QaEconomyGameTests.Shop frank = QaEconomyGameTests.shop(helper, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 1),
			new ItemStack(Items.COBBLESTONE, 32));
		// a second chest of Frank's, 6 blocks off: still his, with no other counter about
		Container far = chest(helper, BOB_CHEST, new ItemStack(Items.COBBLESTONE, 8));
		helper.assertTrue(Shops.stock(helper.getLevel(), frank.counter()).size() == 2,
			"a lone shop's stock should be both its chests: " + Shops.stock(helper.getLevel(), frank.counter()));
		int got = buyThroughTheTradeScreen(helper, frank);
		helper.assertTrue(got == 16, "the lone shop sold " + got + " cobblestone, not 16");
		helper.assertTrue(frank.chest().countItem(Items.EMERALD) == 1, "the emerald didn't land in Frank's chest: " + frank.chest().countItem(Items.EMERALD));
		helper.assertTrue(frank.chest().countItem(Items.COBBLESTONE) + far.countItem(Items.COBBLESTONE) == 24,
			"the shop's chests should hold 24 cobblestone after the sale, not " + (frank.chest().countItem(Items.COBBLESTONE) + far.countItem(Items.COBBLESTONE)));
		helper.succeed();
	}

	/** Two shops side by side, both stocked: a sale at Frank's takes Frank's stock and pays Frank's chest; Bob's is untouched. */
	//$ gametest_ticks_batch AREA '200' '"shopNeighboursPay"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "shopNeighboursPay")
	public void aSaleTakesAndPaysOnlyTheShopsOwnChest(GameTestHelper helper) {
		QaEconomyGameTests.Shop frank = QaEconomyGameTests.shop(helper, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 1),
			new ItemStack(Items.COBBLESTONE, 32));
		bob(helper, UUID.randomUUID());
		Container bobs = chest(helper, BOB_CHEST, new ItemStack(Items.COBBLESTONE, 32));
		int got = buyThroughTheTradeScreen(helper, frank);
		helper.assertTrue(got == 16, "Frank's shop sold " + got + " cobblestone, not 16");
		helper.assertTrue(frank.chest().countItem(Items.COBBLESTONE) == 16 && frank.chest().countItem(Items.EMERALD) == 1,
			"Frank's chest should hold 16 cobblestone and the emerald: " + frank.chest().countItem(Items.COBBLESTONE) + " and " + frank.chest().countItem(Items.EMERALD));
		helper.assertTrue(bobs.countItem(Items.COBBLESTONE) == 32 && bobs.countItem(Items.EMERALD) == 0,
			"Bob's chest was touched by Frank's sale: " + bobs.countItem(Items.COBBLESTONE) + " cobblestone, " + bobs.countItem(Items.EMERALD) + " emeralds");
		// the shop screen (with CobbleDollars) takes the same way: Frank's last lot, never Bob's
		ServerPlayer p = QaEconomyGameTests.buyer(helper).player();
		p.getInventory().setItem(9, new ItemStack(Items.EMERALD, 3));
		boolean first = Shops.buy(p, frank.keeper(), 0);
		boolean second = Shops.buy(p, frank.keeper(), 0);
		helper.assertTrue(first && !second, "Frank's one lot left should sell once (" + first + ", " + second + ")");
		helper.assertTrue(bobs.countItem(Items.COBBLESTONE) == 32 && bobs.countItem(Items.EMERALD) == 0,
			"Frank's shop screen sold from or paid into Bob's chest: " + bobs.countItem(Items.COBBLESTONE) + " cobblestone, " + bobs.countItem(Items.EMERALD) + " emeralds");
		helper.assertTrue(frank.chest().countItem(Items.EMERALD) == 2, "the second emerald didn't land in Frank's chest: " + frank.chest().countItem(Items.EMERALD));
		helper.succeed();
	}

	/** A chest exactly between two players' counters belongs to one of them, the same one whichever shop asks. */
	//$ gametest_batch AREA '"shopNeighboursTie"'
	@GameTest(template = AREA, batch = "shopNeighboursTie")
	public void aChestMidwayBelongsToExactlyOneShop(GameTestHelper helper) {
		QaEconomyGameTests.Shop frank = QaEconomyGameTests.shop(helper, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 1));
		ShopCounterBlockEntity bob = bob(helper, UUID.randomUUID());
		chest(helper, BOB_CHEST);
		chest(helper, MIDDLE_CHEST, new ItemStack(Items.COBBLESTONE, 16));
		BlockPos middle = helper.absolutePos(MIDDLE_CHEST);
		for (int i = 0; i < 3; i++) {
			List<BlockPos> franks = Shops.stock(helper.getLevel(), frank.counter());
			List<BlockPos> bobs = Shops.stock(helper.getLevel(), bob);
			helper.assertTrue(franks.contains(middle) != bobs.contains(middle),
				"the midway chest should be exactly one shop's (Frank's " + franks.contains(middle) + ", Bob's " + bobs.contains(middle) + ")");
			// Frank's counter comes first by position (same height and row, further west), so it keeps the chest
			helper.assertTrue(franks.contains(middle), "the midway chest went to Bob's counter, not the first by position");
			helper.assertTrue(!franks.contains(helper.absolutePos(BOB_CHEST)) && bobs.contains(helper.absolutePos(BOB_CHEST)),
				"the chest by Bob's counter should be Bob's alone");
		}
		helper.succeed();
	}

	/** One player's two counters side by side share their chests, as they always have. */
	//$ gametest_batch AREA '"shopNeighboursSameOwner"'
	@GameTest(template = AREA, batch = "shopNeighboursSameOwner")
	public void oneOwnersCountersShareTheirChests(GameTestHelper helper) {
		QaEconomyGameTests.Shop frank = QaEconomyGameTests.shop(helper, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 1));
		bob(helper, frank.counter().owner());
		chest(helper, BOB_CHEST, new ItemStack(Items.COBBLESTONE, 32));
		Shops.refreshOffers(helper.getLevel(), frank.keeper());
		helper.assertTrue(!frank.keeper().getOffers().isEmpty() && frank.keeper().getOffers().get(0).getMaxUses() == 2,
			"Frank's two counters should share the chest by his second counter: offers " + frank.keeper().getOffers().size());
		// and once a stranger's counter stands there instead, it's not Frank's stock any more
		bob(helper, UUID.randomUUID());
		Shops.refreshOffers(helper.getLevel(), frank.keeper());
		helper.assertTrue(frank.keeper().getOffers().isEmpty(), "a stranger's counter's chest is still in Frank's stock");
		helper.succeed();
	}

	/** The buyer pays an emerald on the vanilla trade screen and shift-clicks the goods; returns the cobblestone they then hold. */
	private static int buyThroughTheTradeScreen(GameTestHelper helper, QaEconomyGameTests.Shop shop) {
		QaStageFiveGameTests.Client c = QaEconomyGameTests.buyer(helper);
		ServerPlayer p = c.player();
		QaStageFourGameTests.pressOn(p, shop.keeper(), c.seen());
		helper.assertTrue(p.containerMenu instanceof MerchantMenu, "the right-click didn't open the trades: " + p.containerMenu);
		MerchantMenu menu = (MerchantMenu) p.containerMenu;
		helper.assertTrue(!menu.getOffers().isEmpty() && !menu.getOffers().get(0).isOutOfStock(), "the shop offered nothing: " + menu.getOffers().size());
		menu.getSlot(0).set(new ItemStack(Items.EMERALD, 1));
		QaStageSixGameTests.click(p, 2, 0, ClickType.QUICK_MOVE);
		int got = QaEconomyGameTests.carried(p, Items.COBBLESTONE);
		p.closeContainer();
		return got;
	}
}
