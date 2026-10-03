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
import net.minecraft.util.RandomSource;
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
	/**
	 * Trees grown with the same random numbers every run, so a test sees the same tree each time (a huge fungus grows
	 * twice as tall one time in twelve, a mangrove's roots vary...).
	 */
	private static RandomSource shapes() {
		return RandomSource.create(20260928L);
	}

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

	/** Wood a builder of the village is waiting for gets felled first: the birch further off before the oak close by. */
	//$ gametest_ticks AREA '200'
	@GameTest(template = AREA, timeoutTicks = 200)
	public void lumberjacksFellTheWoodTheVillageWantsFirst(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BLOCK, ModBlocks.CHOPPING_BLOCK);
		growOak(helper, new BlockPos(6, 2, 6));
		helper.setBlock(new BlockPos(14, 1, 14), Blocks.GRASS_BLOCK);
		grow(helper, new BlockPos(14, 2, 14), TreeFeatures.BIRCH);
		BlockPos from = helper.absolutePos(new BlockPos(3, 2, 3));
		BlockPos plain = io.github.jcondedata.aliveworkplace.wood.LumberjackWork.findTree(level, helper.absolutePos(BLOCK), from, null,
			java.util.Set.of(), java.util.Set.of());
		helper.assertTrue(plain != null && level.getBlockState(plain).is(Blocks.OAK_LOG), "the nearest tree should be the oak, not " + plain);
		BlockPos wanted = io.github.jcondedata.aliveworkplace.wood.LumberjackWork.findTree(level, helper.absolutePos(BLOCK), from, null,
			java.util.Set.of(), java.util.Set.of(Items.BIRCH_LOG));
		helper.assertTrue(wanted != null && level.getBlockState(wanted).is(Blocks.BIRCH_LOG), "birch is wanted: " + wanted);

		// What a builder's missing planks, stairs and signs are made from.
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 5));
		BlockPos at = helper.absolutePos(BLOCK);
		java.util.Set<net.minecraft.world.item.Item> logs = io.github.jcondedata.aliveworkplace.work.Requests.wantedLogs(java.util.List.of(
			io.github.jcondedata.aliveworkplace.work.Requests.forItem(builder, at, Items.BIRCH_PLANKS, 40),
			io.github.jcondedata.aliveworkplace.work.Requests.forItem(builder, at, Items.SPRUCE_STAIRS, 4),
			io.github.jcondedata.aliveworkplace.work.Requests.forItem(builder, at, Items.CRIMSON_PLANKS, 4),
			io.github.jcondedata.aliveworkplace.work.Requests.forItem(builder, at, Items.OAK_HANGING_SIGN, 1),
			io.github.jcondedata.aliveworkplace.work.Requests.forItem(builder, at, Items.JUNGLE_LOG, 2),
			io.github.jcondedata.aliveworkplace.work.Requests.forItem(builder, at, Items.COBBLESTONE, 30)));
		helper.assertTrue(logs.equals(java.util.Set.of(Items.BIRCH_LOG, Items.SPRUCE_LOG, Items.CRIMSON_STEM, Items.OAK_LOG, Items.JUNGLE_LOG)),
			"wanted logs: " + logs);
		helper.succeed();
	}

	/** A furnace by the Chopping Block: the lumberjack burns logs from the chest into charcoal (a little coal starts it). */
	//$ gametest_ticks AREA '2000'
	@GameTest(template = AREA, timeoutTicks = 2000)
	public void lumberjackBurnsCharcoal(GameTestHelper helper) {
		Villager villager = setup(helper, new ItemStack(Items.IRON_AXE), new ItemStack(Items.OAK_LOG, 8), new ItemStack(Items.COAL, 2));
		helper.setBlock(new BlockPos(3, 2, 4), Blocks.FURNACE);
		Container chest = helper.getBlockEntity(CHEST);
		helper.succeedWhen(() -> {
			helper.assertTrue(chest.countItem(Items.CHARCOAL) >= 1, "no charcoal yet (" + chest.countItem(Items.OAK_LOG) + " logs left)");
			helper.assertTrue(villager.isAlive(), "the lumberjack is gone");
		});
	}

	/** Grows a real oak tree (the vanilla feature) on a grass block at {@code base}. */
	private static void growOak(GameTestHelper helper, BlockPos base) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(base.below(), Blocks.GRASS_BLOCK);
		var feature = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolderOrThrow(TreeFeatures.OAK).value();
		boolean grown = feature.place(level, level.getChunkSource().getGenerator(), shapes(), helper.absolutePos(base));
		if (!grown) {
			throw new GameTestAssertException("could not grow the test tree");
		}
	}

	/** The tree comes down, a sapling goes in where it stood, the logs end up in the chest. */
	//$ gametest_ticks AREA '3000'
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
			helper.assertTrue(ModAttachments.TREES_FELLED.getOrElse(villager, 0) == 1, "tree count not updated");
		});
	}

	/** A tree farm (a Field Marker's area): saplings from the chest go in a grid, 3 apart, on the grass. */
	//$ gametest_ticks AREA '2400'
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void lumberjackPlantsATreeFarm(GameTestHelper helper) {
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(10, 1, 10), new BlockPos(16, 1, 16))) {
			helper.setBlock(p, Blocks.GRASS_BLOCK);
		}
		Villager villager = setup(helper, new ItemStack(Items.OAK_SAPLING, 20));
		io.github.jcondedata.aliveworkplace.wood.TreeFarms.start(villager,
			net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(helper.absolutePos(new BlockPos(10, 1, 10)), helper.absolutePos(new BlockPos(16, 1, 16))));
		helper.succeedWhen(() -> {
			for (int x = 10; x <= 16; x++) {
				for (int z = 10; z <= 16; z++) {
					BlockPos spot = new BlockPos(x, 2, z);
					boolean grid = (x - 10) % 3 == 0 && (z - 10) % 3 == 0;
					var state = helper.getBlockState(spot);
					if (grid) {
						helper.assertTrue(state.is(Blocks.OAK_SAPLING) || state.is(Blocks.OAK_LOG), "nothing planted at " + spot + ": " + state);
					} else {
						helper.assertFalse(state.is(Blocks.OAK_SAPLING), "a sapling off the grid at " + spot);
					}
				}
			}
			helper.assertTrue(ModAttachments.SAPLINGS_PLANTED.getOrElse(villager, 0) == 9, "planted " + ModAttachments.SAPLINGS_PLANTED.getOrElse(villager, 0));
		});
	}

	/** A tree on the tree farm is felled even when it's further from the Chopping Block than the lumberjack looks. */
	//$ gametest_ticks AREA '3000'
	@GameTest(template = AREA, timeoutTicks = 3000)
	public void lumberjackFellsTreesOnAFarFarm(GameTestHelper helper) {
		BlockPos base = new BlockPos(19, 2, 19); // 17 blocks out: beyond the 16 the lumberjack searches by itself
		growOak(helper, base);
		Villager villager = setup(helper, new ItemStack(Items.STONE_AXE), new ItemStack(Items.OAK_SAPLING, 2));
		io.github.jcondedata.aliveworkplace.wood.TreeFarms.start(villager,
			net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(helper.absolutePos(new BlockPos(18, 1, 18)), helper.absolutePos(new BlockPos(20, 1, 20))));
		helper.succeedWhen(() -> {
			helper.assertTrue(ModAttachments.TREES_FELLED.getOrElse(villager, 0) == 1, "the farm's tree wasn't felled");
			for (int y = 1; y < 6; y++) {
				helper.assertBlockNotPresent(Blocks.OAK_LOG, base.above(y));
			}
		});
	}

	/** Grows the vanilla tree {@code tree} at {@code base} on {@code ground}. */
	private static void grow(GameTestHelper helper, BlockPos base, net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>> tree) {
		ServerLevel level = helper.getLevel();
		var feature = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolderOrThrow(tree).value();
		if (!feature.place(level, level.getChunkSource().getGenerator(), shapes(), helper.absolutePos(base))) {
			throw new GameTestAssertException("could not grow " + tree.location());
		}
	}

	/**
	 * A mangrove stands on its roots, where nothing can be planted: it's still a tree, felled (the roots stay), and a
	 * propagule goes in close by, in the water over the mud.
	 */
	//$ gametest_ticks_batch AREA '3000' '"mangrove"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "mangrove")
	public void lumberjackFellsAMangroveAndPlantsAPropagule(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos base = new BlockPos(12, 2, 12);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 8), new BlockPos(16, 1, 16))) {
			helper.setBlock(p, Blocks.MUD);
			helper.setBlock(p.above(), Blocks.WATER);
		}
		grow(helper, base, TreeFeatures.MANGROVE);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.wood.Trees.treeAt(level, lowestLog(helper, base)).isPresent(),
			"a mangrove on its roots should count as a tree");
		Villager villager = setup(helper, new ItemStack(Items.STONE_AXE), new ItemStack(Items.MANGROVE_PROPAGULE, 2));
		helper.succeedWhen(() -> {
			helper.assertTrue(ModAttachments.TREES_FELLED.getOrElse(villager, 0) >= 1, "the mangrove wasn't felled");
			boolean planted = false;
			for (BlockPos p : BlockPos.betweenClosed(base.offset(-4, -3, -4), base.offset(4, 1, 4))) {
				var state = helper.getBlockState(p);
				planted |= state.is(Blocks.MANGROVE_PROPAGULE) && !state.getValue(net.minecraft.world.level.block.MangrovePropaguleBlock.HANGING);
			}
			helper.assertTrue(planted, "no propagule planted by the old mangrove");
		});
	}

	/** The lowest log above {@code base} (a grown mangrove's trunk starts a little way up, on its roots). */
	private static BlockPos lowestLog(GameTestHelper helper, BlockPos base) {
		for (int y = -2; y < 8; y++) {
			if (helper.getBlockState(base.above(y)).is(net.minecraft.tags.BlockTags.LOGS)) {
				return helper.absolutePos(base.above(y));
			}
		}
		throw new GameTestAssertException("no trunk above " + base);
	}

	/** An azalea tree (oak logs, azalea leaves) is replanted as an azalea bush, not an oak sapling. */
	//$ gametest_ticks AREA '3000'
	@GameTest(template = AREA, timeoutTicks = 3000)
	public void lumberjackReplantsAnAzalea(GameTestHelper helper) {
		BlockPos base = new BlockPos(11, 2, 11);
		helper.setBlock(base.below(), Blocks.ROOTED_DIRT);
		grow(helper, base, TreeFeatures.AZALEA_TREE);
		Villager villager = setup(helper, new ItemStack(Items.STONE_AXE), new ItemStack(Items.AZALEA));
		helper.succeedWhen(() -> {
			helper.assertTrue(ModAttachments.TREES_FELLED.getOrElse(villager, 0) == 1, "the azalea tree wasn't felled");
			var state = helper.getBlockState(base);
			helper.assertTrue(state.is(Blocks.AZALEA) || state.is(Blocks.FLOWERING_AZALEA), "expected an azalea at the stump, found " + state);
		});
	}

	/** A cherry tree comes down and a cherry sapling goes back. */
	//$ gametest_ticks AREA '3000'
	@GameTest(template = AREA, timeoutTicks = 3000)
	public void lumberjackFellsACherryTree(GameTestHelper helper) {
		BlockPos base = new BlockPos(11, 2, 11);
		helper.setBlock(base.below(), Blocks.GRASS_BLOCK);
		grow(helper, base, TreeFeatures.CHERRY);
		Villager villager = setup(helper, new ItemStack(Items.STONE_AXE), new ItemStack(Items.CHERRY_SAPLING));
		helper.succeedWhen(() -> {
			helper.assertTrue(ModAttachments.TREES_FELLED.getOrElse(villager, 0) == 1, "the cherry tree wasn't felled");
			helper.assertBlockPresent(Blocks.CHERRY_SAPLING, base);
			for (int y = 1; y < 6; y++) {
				helper.assertBlockNotPresent(Blocks.CHERRY_LOG, base.above(y));
			}
		});
	}

	/** With bone meal in the chests, the sapling on the tree farm is grown on the spot, then felled. */
	//$ gametest_ticks AREA '3000'
	@GameTest(template = AREA, timeoutTicks = 3000)
	public void lumberjackGrowsTheFarmWithBoneMeal(GameTestHelper helper) {
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(10, 1, 10), new BlockPos(12, 1, 12))) {
			helper.setBlock(p, Blocks.GRASS_BLOCK);
		}
		Villager villager = setup(helper, new ItemStack(Items.STONE_AXE), new ItemStack(Items.OAK_SAPLING), new ItemStack(Items.BONE_MEAL, 32));
		io.github.jcondedata.aliveworkplace.wood.TreeFarms.start(villager,
			net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(helper.absolutePos(new BlockPos(10, 1, 10)), helper.absolutePos(new BlockPos(12, 1, 12))));
		helper.succeedWhen(() -> {
			helper.assertTrue(ModAttachments.TREES_FELLED.getOrElse(villager, 0) >= 1, "the farm's sapling wasn't grown and felled");
			Container chest = helper.getBlockEntity(CHEST);
			int left = chest.countItem(Items.BONE_MEAL) + ModAttachments.BUILDER_BAG.getOrCreate(villager).count(Items.BONE_MEAL);
			helper.assertTrue(left < 32, "no bone meal used");
		});
	}

	/** A dark oak (a 2 × 2 trunk) is replanted as four saplings in a square: one wouldn't grow. */
	//$ gametest_ticks AREA '3000'
	@GameTest(template = AREA, timeoutTicks = 3000)
	public void lumberjackReplantsADarkOakAsFour(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = new BlockPos(11, 2, 11);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(10, 1, 10), new BlockPos(13, 1, 13))) {
			helper.setBlock(p, Blocks.GRASS_BLOCK);
		}
		var feature = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolderOrThrow(TreeFeatures.DARK_OAK).value();
		if (!feature.place(level, level.getChunkSource().getGenerator(), shapes(), helper.absolutePos(base))) {
			throw new GameTestAssertException("could not grow the dark oak");
		}
		Villager villager = setup(helper, new ItemStack(Items.STONE_AXE), new ItemStack(Items.DARK_OAK_SAPLING, 4));
		helper.succeedWhen(() -> {
			helper.assertTrue(ModAttachments.TREES_FELLED.getOrElse(villager, 0) == 1, "the dark oak wasn't felled");
			for (BlockPos p : new BlockPos[]{base, base.east(), base.south(), base.east().south()}) {
				helper.assertBlockPresent(Blocks.DARK_OAK_SAPLING, p);
			}
		});
	}

	/** A huge crimson fungus on nylium is a tree too: felled, cap and all, and a crimson fungus planted back. */
	//$ gametest_ticks AREA '3000'
	@GameTest(template = AREA, timeoutTicks = 3000)
	public void lumberjackFellsAHugeFungus(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = new BlockPos(11, 2, 11);
		helper.setBlock(base.below(), Blocks.CRIMSON_NYLIUM);
		var feature = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolderOrThrow(TreeFeatures.CRIMSON_FUNGUS_PLANTED).value();
		if (!feature.place(level, level.getChunkSource().getGenerator(), shapes(), helper.absolutePos(base))) {
			throw new GameTestAssertException("could not grow the fungus");
		}
		helper.assertTrue(io.github.jcondedata.aliveworkplace.wood.Trees.treeAt(level, helper.absolutePos(base)).isPresent(), "a huge fungus should count as a tree");
		Villager villager = setup(helper, new ItemStack(Items.STONE_AXE), new ItemStack(Items.CRIMSON_FUNGUS));
		helper.succeedWhen(() -> {
			helper.assertTrue(ModAttachments.TREES_FELLED.getOrElse(villager, 0) == 1, "the fungus wasn't felled");
			helper.assertBlockPresent(Blocks.CRIMSON_FUNGUS, base);
			for (int y = 1; y < 8; y++) {
				helper.assertBlockNotPresent(Blocks.CRIMSON_STEM, base.above(y));
			}
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.CRIMSON_STEM) >= 4, "only " + chest.countItem(Items.CRIMSON_STEM) + " stems in the chest");
		});
	}

	/** A log post with leaves someone placed is part of a build, not a tree: it stays. */
	//$ gametest_ticks AREA '1200'
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

	/**
	 * Bug (found by the B9 tester): README says the lumberjack "plants a sapling of the same wood where each tree stood".
	 * When the leaves drop no sapling (1 time in 20 per leaf; never with a Silk Touch axe, as here) and the chests have
	 * none at the next drop-off, LumberjackWork forgets the stump for good: a sapling put in the chest afterwards is never
	 * planted there. Expected: the stump gets its sapling once one is in the chests.
	 */
	//$ gametest_ticks AREA '4000'
	@GameTest(template = AREA, timeoutTicks = 4000)
	public void aStumpLeftWithoutASaplingIsReplantedWhenOneComes(GameTestHelper helper) {
		BlockPos base = new BlockPos(11, 2, 11);
		growOak(helper, base);
		ItemStack axe = new ItemStack(Items.IRON_AXE);
		axe.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
			.getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), 1);
		Villager villager = setup(helper, axe);
		Container chest = helper.getBlockEntity(CHEST);
		int[] given = {-1};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			// Once the tree is down and its logs are in the chest (the drop-off is done), a sapling arrives in the chest.
			if (given[0] < 0 && ModAttachments.TREES_FELLED.getOrElse(villager, 0) == 1 && chest.countItem(Items.OAK_LOG) >= 4) {
				given[0] = tick[0];
				chest.setItem(5, new ItemStack(Items.OAK_SAPLING, 4));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(given[0] >= 0, "the tree isn't down yet");
			helper.assertBlockPresent(Blocks.OAK_SAPLING, base);
		});
	}

	/**
	 * Tester (B9 round 2): stumps waiting for a sapling don't hold the lumberjack up. With a Silk Touch axe (no sapling
	 * from the leaves) and no saplings anywhere, they still fell both trees and bring the logs back, the first stump
	 * waiting while they fell the second; once saplings are put in the chest, both stumps get one.
	 */
	//$ gametest_ticks AREA '6000'
	@GameTest(template = AREA, timeoutTicks = 6000)
	public void stumpsWaitingForSaplingsDontStopTheFelling(GameTestHelper helper) {
		BlockPos a = new BlockPos(11, 2, 4);
		BlockPos b = new BlockPos(11, 2, 11);
		growOak(helper, a);
		growOak(helper, b);
		ItemStack axe = new ItemStack(Items.IRON_AXE);
		axe.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
			.getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), 1);
		Villager villager = setup(helper, axe);
		Container chest = helper.getBlockEntity(CHEST);
		int[] given = {-1};
		int[] tick = {0};
		String[] before = {""};
		helper.onEachTick(() -> {
			tick[0]++;
			if (given[0] < 0 && ModAttachments.TREES_FELLED.getOrElse(villager, 0) == 2 && chest.countItem(Items.OAK_LOG) >= 8) {
				given[0] = tick[0];
				before[0] = helper.getBlockState(a).getBlock() + " / " + helper.getBlockState(b).getBlock();
				chest.setItem(6, new ItemStack(Items.OAK_SAPLING, 4));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(given[0] >= 0, "after " + tick[0] + " ticks the lumberjack has felled "
				+ ModAttachments.TREES_FELLED.getOrElse(villager, 0) + " of 2 trees (" + chest.countItem(Items.OAK_LOG) + " logs in the chest)");
			helper.assertTrue(before[0].equals("Block{minecraft:air} / Block{minecraft:air}"),
				"before any sapling was given the stumps held " + before[0]);
			helper.assertBlockPresent(Blocks.OAK_SAPLING, a);
			helper.assertBlockPresent(Blocks.OAK_SAPLING, b);
			helper.assertTrue(villager.isAlive(), "the lumberjack is gone");
		});
	}
}
