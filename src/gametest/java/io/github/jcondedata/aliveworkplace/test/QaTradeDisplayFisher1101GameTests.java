package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.fish.Fishers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;

/** QA (lane C, 2026-11-01), B766/B776 from the spec: a hired fisher waiting for a rod never fishes with one the trade display shows. */
public class QaTradeDisplayFisher1101GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BARREL = new BlockPos(2, 2, 2);

	/**
	 * A hired Fisherman whose rod is gone (none in the barrel or the village) waits for one. A player holding emeralds
	 * stands by him and he shows the fishing rod he sells. A shown rod is a copy of his ware, not a rod from the
	 * village: he must not fish with it (nothing caught) and it must never reach the barrel.
	 */
	//$ gametest_ticks_batch AREA '2000' '"qaWareFisher1101"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaWareFisher1101")
	public void qaAFisherWaitingForARodNeverFishesWithAShownOne(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer()));
		helper.setBlock(BARREL, Blocks.BARREL);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(5, 1, 5), new BlockPos(7, 1, 7))) {
			helper.setBlock(p, Blocks.WATER);
		}
		Villager fisher = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, fisher, helper.absolutePos(BARREL), PoiTypes.FISHERMAN, VillagerProfession.FISHERMAN);
		Fishers.start(level, fisher, new ItemStack(Items.FISHING_ROD));
		fisher.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); // the rod broke
		MerchantOffers offers = new MerchantOffers();
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.FISHING_ROD), 12, 1, 0.05f));
		fisher.setOffers(offers);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD, 16));
		int[] tick = {0};
		boolean[] shown = {false};
		helper.onEachTick(() -> {
			tick[0]++;
			player.teleportTo(fisher.getX() + 1.5, fisher.getY(), fisher.getZ());
			ItemStack hand = fisher.getMainHandItem();
			shown[0] |= hand.is(Items.FISHING_ROD) && TradeDisplay.isWare(hand);
			int caught = ModAttachments.FISH_CAUGHT.getOrElse(fisher, 0);
			helper.assertTrue(caught == 0, "the Fisherman caught " + caught + " with no rod but the one on show (" + tick[0] + " ticks; shown: "
				+ shown[0] + ", hand " + hand + ")");
			Container barrel = helper.getBlockEntity(BARREL);
			for (int i = 0; i < barrel.getContainerSize(); i++) {
				helper.assertTrue(!TradeDisplay.isWare(barrel.getItem(i)), "a shown rod reached the barrel: " + barrel.getItem(i));
			}
			if (tick[0] >= 1600) {
				helper.assertTrue(shown[0], "setup: the Fisherman never showed the rod to a player holding emeralds");
				helper.succeed();
			}
		});
	}
}
