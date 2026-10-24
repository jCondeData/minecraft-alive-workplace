package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Bug tests from the lane-c QA pass (lane-c-1024-0353) on ROADMAP 37.14a, each registered with its fix (the rest wait on
 * {@code origin/tests/lane-c-qa-1024}). Helpers are QaCrafterFurnitureGameTests's.
 */
public class QaCrafterFurnitureBugTests {
	private static final String AREA = QaCrafterFurnitureGameTests.AREA;
	private static final BlockPos BENCH = QaCrafterFurnitureGameTests.BENCH;
	private static final BlockPos CHEST = QaCrafterFurnitureGameTests.CHEST;
	private static final BlockPos STATION = QaCrafterFurnitureGameTests.STATION;

	/**
	 * B352: the kiln's fuel counts as a raw material (a coal for every eight fired): twelve clay and one coal for brick
	 * stairs is a coal short, so the Mason takes nothing, and the builder's line names the coal.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaChainMasonFuel"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaChainMasonFuel")
	public void b352AMasonShortOfCoalSaysSoAndTakesNothing(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, BENCH, CHEST, new BlockPos(6, 2, 9), Blocks.BRICK_STAIRS, new ItemStack(Items.CLAY_BALL, 12), new ItemStack(Items.COAL, 1));
		helper.setBlock(STATION, Blocks.STONECUTTER);
		helper.setBlock(STATION.east(), Blocks.FURNACE);
		Villager mason = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), mason, helper.absolutePos(STATION), PoiTypes.MASON, VillagerProfession.MASON);
		helper.runAfterDelay(700, () -> {
			ServerLevel level = helper.getLevel();
			Component line = JobShortages.line(level, a.site().id(), a.site().missing());
			helper.assertTrue(line != null && line.getString().equals("Can't be made yet: short of 1 Coal to make 3 Brick Stairs"),
				"the builder's line with one coal for twelve clay: " + (line == null ? null : line.getString()));
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.CLAY_BALL) == 12 && chest.countItem(Items.COAL) == 1, "the mason took some: "
				+ chest.countItem(Items.CLAY_BALL) + " clay, " + chest.countItem(Items.COAL) + " coal left");
			helper.assertTrue(chest.countItem(Items.BRICK) == 0 && QaCrafterFurnitureGameTests.loose(helper, Items.BRICK) == 0, "bricks were fired without the fuel for the rest");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(mason).isEmpty() && ModAttachments.CRAFTER_JOB.get(mason) == null,
				"the mason took a job it can't finish: " + ModAttachments.CRAFTER_JOB.get(mason));
			helper.succeed();
		});
	}
}
