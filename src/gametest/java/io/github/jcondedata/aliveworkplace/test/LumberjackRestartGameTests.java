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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane (qa-1003-1333), B9 with a restart: README says the lumberjack "plants a sapling of the same wood where each
 * tree stood", and B9 made a stump with no sapling wait for one. A restart (the lumberjack saved and loaded again, as when
 * the server restarts or their chunk unloads) while the stump waits must not make them forget it.
 */
public class LumberjackRestartGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos BLOCK = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	//$ gametest_ticks_batch AREA '4000' '"qaB9StumpAfterRestart"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaB9StumpAfterRestart")
	public void b9AStumpWaitingForASaplingIsReplantedAfterARestart(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos base = new BlockPos(11, 2, 11);
		helper.setBlock(base.below(), Blocks.GRASS_BLOCK);
		var feature = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolderOrThrow(TreeFeatures.OAK).value();
		if (!feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(20260928L), helper.absolutePos(base))) {
			throw new GameTestAssertException("could not grow the test tree");
		}
		// A Silk Touch axe: the leaves drop no sapling, and the chest has none, so the stump waits.
		ItemStack axe = new ItemStack(Items.IRON_AXE);
		axe.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
			.getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), 1);
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		helper.setBlock(BLOCK, ModBlocks.CHOPPING_BLOCK);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, axe);
		Villager first = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, first, helper.absolutePos(BLOCK), ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK);

		Villager[] lumberjack = {first};
		int[] restartedAt = {-1};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (restartedAt[0] < 0 && ModAttachments.TREES_FELLED.getOrElse(lumberjack[0], 0) == 1 && chest.countItem(Items.OAK_LOG) >= 4) {
				// The tree is down and the logs are in: the lumberjack is saved and loaded again, then a sapling arrives.
				restartedAt[0] = tick[0];
				CompoundTag saved = new CompoundTag();
				helper.assertTrue(lumberjack[0].save(saved), "the lumberjack couldn't be saved");
				lumberjack[0].remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
				Entity back = EntityType.loadEntityRecursive(saved, level, e -> e);
				helper.assertTrue(back instanceof Villager && level.addFreshEntity(back), "the lumberjack couldn't be loaded again");
				lumberjack[0] = (Villager) back;
				helper.assertBlockNotPresent(Blocks.OAK_SAPLING, base);
				chest.setItem(5, new ItemStack(Items.OAK_SAPLING, 4));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restartedAt[0] >= 0, "the tree isn't down yet");
			helper.assertTrue(lumberjack[0].isAlive(), "the lumberjack is gone");
			helper.assertTrue(lumberjack[0].getVillagerData().getProfession() == ModVillagers.LUMBERJACK, "the reloaded villager isn't a lumberjack");
			helper.assertTrue(helper.getBlockState(base).is(Blocks.OAK_SAPLING),
				"after a restart the stump at " + base + " never got its sapling (it's " + helper.getBlockState(base).getBlock()
					+ ", " + chest.countItem(Items.OAK_SAPLING) + " saplings still in the chest)");
		});
	}
}
