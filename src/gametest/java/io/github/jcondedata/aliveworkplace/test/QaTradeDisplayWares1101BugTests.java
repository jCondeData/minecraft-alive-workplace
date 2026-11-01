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
 * QA (lane C, 2026-11-01), the failing half (bug filed), B766 from the spec: vanilla's trade display touches only an empty hand or a ware it showed
 * itself. The other side of that rule: a ware is the display's, never the worker's. A shown ware is a free copy of an
 * offer's result, so it must never become a worker's tool, go into their bag or reach the village's chest (that would
 * hand the player a diamond pickaxe or emeralds for standing by). And the display itself still works: a player holding
 * what a villager trades for is shown the wares, and the hand is empty again once the player leaves.
 */
public class QaTradeDisplayWares1101BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
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
	 * A Shepherd who buys wool shows an emerald to a player holding white wool. Work starts (shears in the chest, a sheep
	 * ready): the Shepherd takes the shears; the shown emerald is the display's, never the Shepherd's, so it must not go
	 * into their bag or the chest as a free emerald.
	 */
	//$ gametest_ticks_batch AREA '2600' '"qaWareShepherdBag1101"'
	@GameTest(template = AREA, timeoutTicks = 2600, batch = "qaWareShepherdBag1101")
	public void qaAShepherdNeverPocketsAShownEmerald(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(9500);
		helper.setBlock(WORK, Blocks.LOOM);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.SHEARS));
		Villager villager = helper.spawn(EntityType.VILLAGER, AT);
		Jobs.employ(level, villager, helper.absolutePos(WORK), PoiTypes.SHEPHERD, VillagerProfession.SHEPHERD);
		sells(villager, new ItemCost(Items.WHITE_WOOL, 18), new ItemStack(Items.EMERALD));
		helper.spawn(EntityType.SHEEP, new BlockPos(6, 2, 6));
		ServerPlayer player = customer(helper, new ItemStack(Items.WHITE_WOOL, 32));
		int[] tick = {0};
		int[] morning = {-1};
		helper.onEachTick(() -> {
			tick[0]++;
			ItemStack hand = villager.getMainHandItem();
			if (morning[0] < 0) {
				player.teleportTo(villager.getX() + 1.5, villager.getY(), villager.getZ());
				if (hand.is(Items.EMERALD) && TradeDisplay.isWare(hand)) {
					morning[0] = tick[0];
					helper.setDayTime(2000); // the working day starts with the emerald on show
				} else if (tick[0] > 1200) {
					helper.fail("the Shepherd never showed the emerald to a player holding white wool; the hand holds " + hand);
				}
				return;
			}
			String leak = wareOutsideTheHand(helper, villager);
			helper.assertTrue(leak == null, "the shown emerald became the Shepherd's: it's in " + leak + " ("
				+ (tick[0] - morning[0]) + " ticks into the day)");
			if (tick[0] - morning[0] >= 1200) {
				helper.succeed();
			}
		});
	}
}
