package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b-1115-2137), written from crafter.md ("The line names what the chest is short of"): a honey block is 4 honey
 * bottles; a honey bottle is unpacked from a honey block with 4 glass bottles. From nothing the line should name the
 * Honey Bottles, never Honey Blocks to make Honey Blocks (with glass bottles and glass for the round trip).
 */
public class QaHoneyBlockLine1115bBugTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos STATION = new BlockPos(14, 2, 2);

	/** 3 honey blocks from nothing lack 12 honey bottles and nothing else; from 12 bottles nothing. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaHoney1115bLogic"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaHoney1115bLogic")
	public void honeyBlocksNameTheBottles(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<Item, Integer> none = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.HONEY_BLOCK, 3, Map.of());
		Map<Item, Integer> some = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.HONEY_BLOCK, 3, Map.of(Items.HONEY_BOTTLE, 12L));
		helper.assertTrue(none.equals(Map.of(Items.HONEY_BOTTLE, 12)) && some.isEmpty(),
			"3 honey blocks from nothing lack " + none + " (expected 12 honey bottles), from 12 bottles " + some + " (expected nothing)");
		helper.succeed();
	}

	/** Through the player's entry point: a builder short of 3 honey blocks, empty chest, a Carpenter: the bottles are named. */
	//$ gametest_ticks_batch AREA '1400' '"qaHoney1115bLine"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaHoney1115bLine")
	public void aBuilderShortOfHoneyBlocksReadsHoneyBottles(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.HONEY_BLOCK);
		helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		helper.runAfterDelay(1200, () -> {
			BuildSite live = BuildSiteManager.get(helper.getLevel()).get(a.site().id());
			helper.assertTrue(live != null, "the build ended with no honey");
			Component line = JobShortages.line(helper.getLevel(), live.id(), live.missing());
			String text = line == null ? null : line.getString();
			helper.assertTrue("Can't be made yet: short of 12 Honey Bottles to make 3 Honey Blocks".equals(text), "the builder's line: '" + text
				+ "', expected 'Can't be made yet: short of 12 Honey Bottles to make 3 Honey Blocks'");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) == 0, "the Carpenter made something from nothing");
			helper.succeed();
		});
	}
}
