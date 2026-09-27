package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;

/** Lumberjacks cutting trees on a real (headless) server. */
public class LumberjackGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos BLOCK = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	private static Villager setup(GameTestHelper helper, ItemStack... chest) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		helper.setBlock(BLOCK, ModBlocks.CHOPPING_BLOCK);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, villager, helper.absolutePos(BLOCK), ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK);
		return villager;
	}

	/** Grows a real oak tree (the vanilla feature) on a grass block at {@code base}. */
	private static void growOak(GameTestHelper helper, BlockPos base) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(base.below(), Blocks.GRASS_BLOCK);
		var feature = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolderOrThrow(TreeFeatures.OAK).value();
		boolean grown = feature.place(level, level.getChunkSource().getGenerator(), level.getRandom(), helper.absolutePos(base));
		if (!grown) {
			throw new GameTestAssertException("could not grow the test tree");
		}
	}

	/** The tree comes down, a sapling goes in where it stood, the logs end up in the chest. */
	@GameTest(template = AREA, timeoutTicks = 3000)
	public void lumberjackFellsATreeAndReplants(GameTestHelper helper) {
		BlockPos base = new BlockPos(11, 2, 11);
		growOak(helper, base);
		// A sapling in the chest too: the leaves drop one only 1 time in 20, and the lumberjack replants from the chests.
		Villager villager = setup(helper, new ItemStack(Items.STONE_AXE), new ItemStack(Items.OAK_SAPLING));
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.OAK_SAPLING, base);
			for (int y = 1; y < 8; y++) {
				helper.assertBlockNotPresent(Blocks.OAK_LOG, base.above(y));
			}
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.OAK_LOG) >= 4, "only " + chest.countItem(Items.OAK_LOG) + " logs in the chest");
			helper.assertTrue(villager.getAttachedOrElse(ModAttachments.TREES_FELLED, 0) == 1, "tree count not updated");
		});
	}

	/** A log post with leaves someone placed is part of a build, not a tree: it stays. */
	@GameTest(template = AREA, timeoutTicks = 1200)
	public void lumberjackLeavesBuiltLogsAlone(GameTestHelper helper) {
		BlockPos post = new BlockPos(11, 2, 11);
		helper.setBlock(post.below(), Blocks.GRASS_BLOCK);
		for (int y = 0; y < 4; y++) {
			helper.setBlock(post.above(y), Blocks.OAK_LOG);
		}
		for (BlockPos leaf : BlockPos.betweenClosed(post.offset(-1, 4, -1), post.offset(1, 4, 1))) {
			helper.setBlock(leaf, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
		}
		setup(helper, new ItemStack(Items.STONE_AXE));
		helper.runAfterDelay(800, () -> {
			for (int y = 0; y < 4; y++) {
				helper.assertBlockPresent(Blocks.OAK_LOG, post.above(y));
			}
			helper.succeed();
		});
	}
}
