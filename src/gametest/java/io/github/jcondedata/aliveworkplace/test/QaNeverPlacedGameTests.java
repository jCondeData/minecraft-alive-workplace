package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-b-1025-0937) from the wiki (builder.md, Materials): "A fixed set of blocks is never placed (bedrock, command
 * blocks, spawners, portals, barrier, fire...)". A blueprint made from a row holding a spawner, bedrock and budding
 * amethyst between cobblestone (as a player's scan of a dungeon or a geode would be): the builder asks only for the
 * cobblestone, waits for it while it's missing, and once it's there finishes the build with none of the three placed.
 */
public class QaNeverPlacedGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos ORIGIN = new BlockPos(6, 2, 9);
	private static final List<Block> ROW = List.of(Blocks.SPAWNER, Blocks.COBBLESTONE, Blocks.BEDROCK, Blocks.COBBLESTONE, Blocks.BUDDING_AMETHYST);

	//$ gametest_ticks_batch AREA '3000' '"qaNeverPlaced"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaNeverPlaced")
	public void aSpawnerBedrockAndBuddingAmethystAreLeftOutOfABuild(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < ROW.size(); x++) {
			helper.setBlock(ORIGIN.offset(x, 0, 0), ROW.get(x));
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qaneverplaced_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ORIGIN), new Vec3i(ROW.size(), 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < ROW.size(); x++) {
			helper.setBlock(ORIGIN.offset(x, 0, 0), Blocks.AIR);
		}
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN),
			Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null, "the builder took no build");
		AtomicBoolean stocked = new AtomicBoolean();
		helper.runAfterDelay(600, () -> {
			Map<Item, Integer> missing = site.missing();
			helper.assertTrue(missing.equals(Map.of(Items.COBBLESTONE, 2)), "with an empty chest the builder should be short of 2 cobblestone only: " + missing);
			Container chest = helper.getBlockEntity(CHEST);
			chest.setItem(0, new ItemStack(Items.COBBLESTONE, 2));
			stocked.set(true);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stocked.get(), "not stocked yet");
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: status=" + site.status() + " missing=" + site.missing());
			for (int x = 0; x < ROW.size(); x++) {
				Block want = ROW.get(x) == Blocks.COBBLESTONE ? Blocks.COBBLESTONE : Blocks.AIR;
				helper.assertBlockPresent(want, ORIGIN.offset(x, 0, 0));
			}
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 0, "cobblestone left: " + chest.countItem(Items.COBBLESTONE));
		});
	}
}
