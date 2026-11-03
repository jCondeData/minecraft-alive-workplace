package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.shop.Shops;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1103, round 3) bugs: not registered, they fail until the mod is fixed. Found by
 * {@link QaHallMidActivity1103cGameTests}.
 */
public class QaHallMidActivity1103cBugTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos STONECUTTER = new BlockPos(9, 2, 27);
	private static final BlockPos COUNTER = new BlockPos(10, 2, 20);
	private static final BlockPos CHEST = new BlockPos(10, 2, 22);

	/**
	 * A buyer has Sal's shop trades open (16 cobblestone for an emerald) when another player retrains Sal as a Mason at
	 * the hall. Sal's trades are gone with the job (40.3: "trades and level lost"), so the screen the buyer still has open
	 * should close. Today it stays open, its list still showing the shop's cobblestone, while the server now answers with
	 * the Mason's trades: 3 emeralds in the payment slot and a shift-click on the result buy 30 bricks the buyer never saw
	 * offered.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHmaBugShopScreen"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHmaBugShopScreen")
	public void aShopkeepersOpenTradeScreenClosesWhenHeIsRetrained(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		helper.setBlock(COUNTER, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(COUNTER);
		counter.setOwner(UUID.randomUUID(), "Frank");
		counter.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		counter.setItem(ShopCounterBlockEntity.COLUMNS, new ItemStack(Items.EMERALD, 1));
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		Villager sal = QaHallScreenGameTests.villager(helper, "Sal", COUNTER.offset(1, 0, 0));
		Jobs.employ(helper.getLevel(), sal, helper.absolutePos(COUNTER), ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
		sal.setVillagerData(sal.getVillagerData().setLevel(2));
		sal.setVillagerXp(140);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 10);
		helper.runAfterDelay(3, () -> {
			Shops.refreshOffers(helper.getLevel(), sal);
			ServerPlayer buyer = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaHmaBug")).player();
			QaStageFourGameTests.pressOn(buyer, sal, new ArrayList<>());
			helper.assertTrue(buyer.containerMenu instanceof MerchantMenu, "setup: the right-click didn't open Sal's trades: " + buyer.containerMenu);
			MerchantMenu trades = (MerchantMenu) buyer.containerMenu;
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			QaHallScreenGameTests.jobDropdown(helper, player, menu, sal);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(sal.getVillagerData().getProfession() == VillagerProfession.MASON, "setup: not retrained: " + sal.getVillagerData() + "; " + seen);
			helper.runAfterDelay(2, () -> {
				boolean open = buyer.containerMenu == trades;
				int bricks = 0;
				if (open) {
					trades.getSlot(0).set(new ItemStack(Items.EMERALD, 3));
					QaStageSixGameTests.click(buyer, 2, 0, ClickType.QUICK_MOVE);
					bricks = buyer.getInventory().countItem(Items.BRICK);
					buyer.closeContainer();
				}
				helper.assertTrue(!open, "the buyer's shop screen stayed open after Sal became a Mason, and 3 emeralds in it bought "
					+ bricks + " bricks");
				helper.succeed();
			});
		});
	}
}
