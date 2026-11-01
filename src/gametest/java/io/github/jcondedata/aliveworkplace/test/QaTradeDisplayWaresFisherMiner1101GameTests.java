package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.fish.Fishers;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.TradeDisplay;
import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-e-1101-1913), B776 from its spec, the workers it names that its own tests don't reach: "Miners.returnEverything
 * stores the hand's item; Fishers.start also bags the old hand item". A ware the trade display left in the hand (still on
 * show, or kept through a save and load) is the display's: hiring a Fisherman with a rod never bags it, letting him go
 * never puts it in the chest, and a Miner's end-of-shift return never stores it. Each ware is one nobody else here has,
 * so any found outside the hand came from the display. Their real hand items (the rod, the pickaxe) still go back.
 */
public class QaTradeDisplayWaresFisherMiner1101GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos AT = new BlockPos(3, 2, 3);

	/** A Fisherman showing cooked cod is hired with a rod (Fishers.start): the rod in hand, the cod in no bag. */
	//$ gametest_ticks_batch AREA '40' '"qaWareFisherHire1101"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaWareFisherHire1101")
	public void qaHiringAFishermanNeverBagsTheWareOnShow(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Villager fisher = villager(helper, VillagerProfession.FISHERMAN);
		fisher.setItemSlot(EquipmentSlot.MAINHAND, ware(Items.COOKED_COD));
		Fishers.start(level, fisher, new ItemStack(Items.FISHING_ROD));
		Leftovers.after(helper, () -> ModAttachments.FISHER_JOB.remove(fisher));
		helper.assertTrue(fisher.getMainHandItem().is(Items.FISHING_ROD) && !TradeDisplay.isWare(fisher.getMainHandItem()),
			"setup: the hired Fisherman doesn't hold the rod: " + fisher.getMainHandItem());
		String where = stray(helper, fisher, Items.COOKED_COD);
		helper.assertTrue(where == null, "hiring the Fisherman turned the cooked cod on show into a real one: in his " + where);
		helper.succeed();
	}

	/** A hired Fisherman whose hand holds a ware on show (his rod gone) is let go: the ware never reaches the chest. */
	//$ gametest_ticks_batch AREA '40' '"qaWareFisherRelease1101"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaWareFisherRelease1101")
	public void qaLettingAFishermanGoNeverStoresTheWareOnShow(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager fisher = villager(helper, VillagerProfession.FISHERMAN);
		Fishers.start(level, fisher, new ItemStack(Items.FISHING_ROD));
		Leftovers.after(helper, () -> ModAttachments.FISHER_JOB.remove(fisher));
		ModAttachments.BUILDER_BAG.getOrCreate(fisher).add(new ItemStack(Items.SALMON, 3)); // his catch: that does go in
		fisher.setItemSlot(EquipmentSlot.MAINHAND, ware(Items.COOKED_COD));
		Fishers.release(level, fisher);
		Container chest = helper.getBlockEntity(CHEST);
		helper.assertTrue(chest.countItem(Items.SALMON) == 3, "setup: his catch didn't go in the chest (" + chest.countItem(Items.SALMON) + " salmon)");
		String where = stray(helper, fisher, Items.COOKED_COD);
		helper.assertTrue(where == null, "letting the Fisherman go turned the cooked cod on show into a real one: in his " + where);
		helper.succeed();
	}

	/** A hired Fisherman let go with his own rod: the rod still goes in the chest (only a ware is the display's). */
	//$ gametest_ticks_batch AREA '40' '"qaWareFisherOwnRod1101"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaWareFisherOwnRod1101")
	public void qaLettingAFishermanGoStillStoresHisOwnRod(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager fisher = villager(helper, VillagerProfession.FISHERMAN);
		Fishers.start(level, fisher, new ItemStack(Items.FISHING_ROD));
		Leftovers.after(helper, () -> ModAttachments.FISHER_JOB.remove(fisher));
		Fishers.release(level, fisher);
		Container chest = helper.getBlockEntity(CHEST);
		helper.assertTrue(chest.countItem(Items.FISHING_ROD) == 1 && fisher.getMainHandItem().isEmpty(),
			"the Fisherman's own rod: " + chest.countItem(Items.FISHING_ROD) + " in the chest, hand " + fisher.getMainHandItem());
		helper.succeed();
	}

	/**
	 * A Miner whose hand holds a ware on show when the shift's load goes back (Miners.returnEverything): his finds go in
	 * the chest, the ware doesn't; with his own pickaxe instead, the pickaxe goes back.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaWareMinerReturn1101"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaWareMinerReturn1101")
	public void qaAMinersReturnNeverStoresTheWareOnShowButStoresHisPickaxe(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager miner = villager(helper, VillagerProfession.NONE);
		BlockPos bench = helper.absolutePos(new BlockPos(2, 2, 2));
		List<BlockPos> supplies = List.of(helper.absolutePos(CHEST));
		ModAttachments.BUILDER_BAG.getOrCreate(miner).add(new ItemStack(Items.RAW_IRON, 5));
		miner.setItemSlot(EquipmentSlot.MAINHAND, ware(Items.EMERALD));
		returnEverything(level, miner, bench, supplies);
		Container chest = helper.getBlockEntity(CHEST);
		helper.assertTrue(chest.countItem(Items.RAW_IRON) == 5, "setup: the Miner's finds didn't go in the chest (" + chest.countItem(Items.RAW_IRON) + " raw iron)");
		String where = stray(helper, miner, Items.EMERALD);
		helper.assertTrue(where == null, "the Miner's return turned the emerald on show into a real one: in his " + where);
		// his own pickaxe: back in the chest
		miner.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
		returnEverything(level, miner, bench, supplies);
		helper.assertTrue(chest.countItem(Items.IRON_PICKAXE) == 1 && miner.getMainHandItem().isEmpty(),
			"the Miner's own pickaxe: " + chest.countItem(Items.IRON_PICKAXE) + " in the chest, hand " + miner.getMainHandItem());
		helper.succeed();
	}

	private static Villager villager(GameTestHelper helper, VillagerProfession profession) {
		Villager v = helper.spawn(EntityType.VILLAGER, AT);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(profession).setLevel(2));
		return v;
	}

	/** A copy of {@code item} marked as the trade display's ware, as vanilla's display puts it in an empty hand. */
	private static ItemStack ware(Item item) {
		ItemStack stack = new ItemStack(item);
		TradeDisplay.mark(stack);
		return stack;
	}

	/** Where {@code ware} is found other than the main hand (bag, inventory, off hand, chest, ground), or null. */
	private static String stray(GameTestHelper helper, Villager villager, Item ware) {
		if (villager.getItemBySlot(EquipmentSlot.OFFHAND).is(ware)) {
			return "off hand";
		}
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrElse(villager, null);
		if (bag != null && bag.stacks().stream().anyMatch(s -> s.is(ware))) {
			return "bag " + bag.stacks();
		}
		for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
			if (villager.getInventory().getItem(i).is(ware)) {
				return "inventory";
			}
		}
		if (helper.getLevel().getBlockEntity(helper.absolutePos(CHEST)) instanceof Container chest && chest.countItem(ware) > 0) {
			return "chest (" + chest.countItem(ware) + ")";
		}
		for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, villager.getBoundingBox().inflate(16))) {
			if (drop.getItem().is(ware)) {
				return "on the ground (" + drop.getItem() + ")";
			}
		}
		return null;
	}

	private static void returnEverything(ServerLevel level, Villager villager, BlockPos bench, List<BlockPos> supplies) {
		try {
			Method m = Miners.class.getDeclaredMethod("returnEverything", ServerLevel.class, Villager.class, BlockPos.class, List.class);
			m.setAccessible(true);
			m.invoke(null, level, villager, bench, supplies);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
