package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * B377: count lines read "1 chest" and "1 job", not "1 chests" and "1 jobs": the Storehouse board's stock line and the
 * Steward's reason for a workplace nobody's trade has. B395: the stock line's items and empty slots too.
 */
public class CountWordsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos STOREHOUSE = new BlockPos(3, 2, 3);

	private static String stockLine(GameTestHelper helper, ServerPlayer player) {
		var info = StorehouseBoard.boardForTest(player, helper.absolutePos(STOREHOUSE)).icon(StorehouseBoard.INFO_SLOT);
		List<String> lore = info.get(DataComponents.LORE).lines().stream().map(Component::getString).toList();
		return lore.stream().filter(l -> l.startsWith("Storehouse: ")).findFirst().orElse("no stock line in " + lore);
	}

	/** One chest by the Storehouse reads "1 chest"; a second makes it "2 chests". */
	//$ gametest_ticks_batch AREA '40' '"countWordsStock"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "countWordsStock")
	public void theStorehouseStockLineCountsOneChestAndTwoChests(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(5, 2, 3), Blocks.CHEST);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		String one = stockLine(helper, player);
		helper.assertTrue(one.startsWith("Storehouse: 1 chest, "), "one chest: " + one);
		helper.setBlock(new BlockPos(5, 2, 5), Blocks.CHEST);
		String two = stockLine(helper, player);
		helper.assertTrue(two.startsWith("Storehouse: 2 chests, "), "two chests: " + two);
		helper.succeed();
	}

	/** One item in the Storehouse's chest reads "1 item"; a second makes it "2 items". */
	//$ gametest_ticks_batch AREA '40' '"countWordsItems"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "countWordsItems")
	public void theStorehouseStockLineCountsOneItemAndTwoItems(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		BlockPos chestPos = new BlockPos(5, 2, 3);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 1));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		String one = stockLine(helper, player);
		helper.assertTrue(one.equals("Storehouse: 1 chest, 1 item, 26 empty slots"), "one item: " + one);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 2));
		String two = stockLine(helper, player);
		helper.assertTrue(two.equals("Storehouse: 1 chest, 2 items, 26 empty slots"), "two items: " + two);
		helper.succeed();
	}

	/** A chest with one slot left reads "1 empty slot"; with two left, "2 empty slots". */
	//$ gametest_ticks_batch AREA '40' '"countWordsSlots"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "countWordsSlots")
	public void theStorehouseStockLineCountsOneEmptySlotAndTwo(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		BlockPos chestPos = new BlockPos(5, 2, 3);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		for (int i = 0; i < chest.getContainerSize() - 1; i++) {
			chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		String one = stockLine(helper, player);
		helper.assertTrue(one.equals("Storehouse: 1 chest, " + 26 * 64 + " items, 1 empty slot"), "one empty slot: " + one);
		chest.setItem(0, ItemStack.EMPTY);
		String two = stockLine(helper, player);
		helper.assertTrue(two.equals("Storehouse: 1 chest, " + 25 * 64 + " items, 2 empty slots"), "two empty slots: " + two);
		helper.succeed();
	}

	private static String workplaceReason(long jobs) {
		return new StewardWishes.Wish(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "workplace_guard_house"),
			new StewardRules.Effect(StewardRules.Kind.BUILD, Optional.empty(), Optional.of("defences"), Optional.empty(), Optional.empty()), 50,
			"steward.aliveworkplace.why.workplace", List.of(jobs)).reason().getString();
	}

	/** The Steward's reason for a workplace: "1 job here has ...", "2 jobs here have ...". */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theWorkplaceReasonCountsOneJobAndTwoJobs(GameTestHelper helper) {
		String one = workplaceReason(1);
		helper.assertTrue(one.equals("1 job here has no workplace of its trade"), "one job: " + one);
		String two = workplaceReason(2);
		helper.assertTrue(two.equals("2 jobs here have no workplace of their trade"), "two jobs: " + two);
		helper.succeed();
	}
}
