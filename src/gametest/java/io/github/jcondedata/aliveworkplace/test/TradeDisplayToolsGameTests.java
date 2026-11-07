package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.TradeDisplay;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * B766: vanilla's trade display may show and put away its own wares, but never takes a worker's tool out of their hand.
 * The QA tests (QaToolsByTheTradeDisplay1101*) cover an empty-handed player by the Farmer, Shepherd, Miner and
 * Lumberjack; here a player holding the price of a trade (the display then wants to show the ware in that hand), and the
 * control: with an empty hand the display still shows its ware and puts it away again, so wares never stick.
 */
public class TradeDisplayToolsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos WORK = new BlockPos(2, 2, 2);
	private static final BlockPos AT = new BlockPos(3, 2, 3);
	private static final String NAME = "B766 hoe";

	/** An employed Farmer, at the meeting hour, who sells bread for an emerald (one fixed offer). */
	private static Villager farmer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(9500);
		Leftovers.after(helper, () -> helper.setDayTime(2000));
		helper.setBlock(WORK, Blocks.COMPOSTER);
		Villager villager = helper.spawn(EntityType.VILLAGER, AT);
		Jobs.employ(level, villager, helper.absolutePos(WORK), PoiTypes.FARMER, VillagerProfession.FARMER);
		MerchantOffers offers = new MerchantOffers();
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD), new ItemStack(Items.BREAD, 6), 16, 1, 0.05f));
		villager.setOffers(offers);
		return villager;
	}

	private static ServerPlayer playerBeside(GameTestHelper helper, Villager villager, ItemStack inHand) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemInHand(InteractionHand.MAIN_HAND, inHand);
		helper.onEachTick(() -> player.teleportTo(villager.getX() + 1.5, villager.getY(), villager.getZ()));
		return player;
	}

	/** The player holds an emerald, the price of the bread: the display wants the hand, the Farmer keeps the hoe. */
	//$ gametest_ticks_batch AREA '1200' '"tradeDisplayToolPrice"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "tradeDisplayToolPrice")
	public void aFarmersHoeStaysWhenThePlayerHoldsThePrice(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager villager = farmer(helper);
		Fields.start(helper.getLevel(), villager,
			BoundingBox.fromCorners(helper.absolutePos(new BlockPos(5, 1, 5)), helper.absolutePos(new BlockPos(7, 1, 7))));
		ItemStack hoe = new ItemStack(Items.IRON_HOE);
		hoe.set(DataComponents.CUSTOM_NAME, Component.literal(NAME));
		villager.setItemSlot(EquipmentSlot.MAINHAND, hoe);
		playerBeside(helper, villager, new ItemStack(Items.EMERALD, 5));
		int[] ticks = {0};
		helper.onEachTick(() -> {
			ItemStack held = villager.getMainHandItem();
			if (!held.is(Items.IRON_HOE) || !NAME.equals(held.getHoverName().getString())) {
				helper.fail("after " + ticks[0] + " ticks the hand holds " + held + ", not the Farmer's hoe");
			}
			if (++ticks[0] >= 600) {
				helper.succeed();
			}
		});
	}

	/** Control: with nothing of the worker's in hand the display shows its ware (marked) and puts it away again. */
	//$ gametest_ticks_batch AREA '1200' '"tradeDisplayWares"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "tradeDisplayWares")
	public void theDisplayStillShowsAndPutsAwayItsWares(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager villager = farmer(helper);
		ServerPlayer player = playerBeside(helper, villager, new ItemStack(Items.EMERALD, 5));
		boolean[] shown = {false};
		helper.onEachTick(() -> {
			ItemStack held = villager.getMainHandItem();
			if (!shown[0]) {
				if (held.is(Items.BREAD)) {
					helper.assertTrue(TradeDisplay.isWare(held), "the shown bread isn't marked as the display's: " + held);
					shown[0] = true;
					player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); // nothing to trade now
				}
				return;
			}
			if (held.isEmpty()) {
				helper.succeed();
			}
		});
	}
}
