package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.TradeDisplay;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane C, 2026-11-01), B766 from the spec: vanilla's trade display touches only an empty hand or a ware it showed
 * itself. The other side of that rule: a ware is the display's, never the worker's. A shown ware is a free copy of an
 * offer's result, so it must never become a worker's tool, go into their bag or reach the village's chest (the failing
 * half, a Shepherd's free emerald, is QaTradeDisplayWares1101BugTests). Here the display itself still works: a player holding
 * what a villager trades for is shown the wares, and the hand is empty again once the player leaves.
 */
public class QaTradeDisplayWares1101GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos WORK = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos AT = new BlockPos(3, 2, 3);

	private static void sells(Villager villager, ItemCost cost, ItemStack result) {
		MerchantOffers offers = new MerchantOffers();
		offers.add(new MerchantOffer(cost, result, 12, 1, 0.05f));
		villager.setOffers(offers);
	}

	/** Where a display ware has got to that isn't the hand: the bag, inventory, chest or the ground; null when nowhere. */
	private static String wareOutsideTheHand(GameTestHelper helper, Villager villager) {
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrElse(villager, null);
		if (bag != null) {
			for (ItemStack stack : bag.stacks()) {
				if (TradeDisplay.isWare(stack)) {
					return "the bag (" + stack + ")";
				}
			}
		}
		for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
			if (TradeDisplay.isWare(villager.getInventory().getItem(i))) {
				return "the inventory (" + villager.getInventory().getItem(i) + ")";
			}
		}
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (TradeDisplay.isWare(chest.getItem(i))) {
				return "the chest (" + chest.getItem(i) + ")";
			}
		}
		for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, villager.getBoundingBox().inflate(16))) {
			if (TradeDisplay.isWare(drop.getItem())) {
				return "the ground (" + drop.getItem() + ")";
			}
		}
		return null;
	}

	private static ServerPlayer customer(GameTestHelper helper, ItemStack held) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		return player;
	}

	/**
	 * Happy path, vanilla's own: a Shepherd who sells shears, a player beside them holding emeralds at the meeting hour.
	 * The Shepherd shows the shears; once the player walks off (empty-handed), the shown shears are gone from the hand.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaWareShown1101"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaWareShown1101")
	public void qaTheDisplayStillShowsWaresAndPutsThemAway(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(9500);
		helper.setBlock(WORK, Blocks.LOOM);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager villager = helper.spawn(EntityType.VILLAGER, AT);
		Jobs.employ(level, villager, helper.absolutePos(WORK), PoiTypes.SHEPHERD, VillagerProfession.SHEPHERD);
		sells(villager, new ItemCost(Items.EMERALD, 2), new ItemStack(Items.SHEARS));
		ServerPlayer player = customer(helper, new ItemStack(Items.EMERALD, 10));
		int[] tick = {0};
		int[] shownAt = {-1};
		helper.onEachTick(() -> {
			tick[0]++;
			ItemStack hand = villager.getMainHandItem();
			if (shownAt[0] < 0) {
				player.teleportTo(villager.getX() + 1.5, villager.getY(), villager.getZ());
				if (hand.is(Items.SHEARS) && TradeDisplay.isWare(hand)) {
					shownAt[0] = tick[0];
					player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				} else if (tick[0] > 700) {
					helper.fail("a player holding emeralds beside a Shepherd who sells shears was never shown them; the hand holds " + hand);
				}
				return;
			}
			player.teleportTo(villager.getX() + 40, villager.getY() + 20, villager.getZ());
			if (hand.isEmpty()) {
				String leak = wareOutsideTheHand(helper, villager);
				helper.assertTrue(leak == null, "the shown shears were put away in " + leak);
				helper.succeed();
			} else if (tick[0] - shownAt[0] > 700) {
				helper.fail("the player left " + (tick[0] - shownAt[0]) + " ticks ago and the Shepherd still holds " + hand);
			}
		});
	}
}
