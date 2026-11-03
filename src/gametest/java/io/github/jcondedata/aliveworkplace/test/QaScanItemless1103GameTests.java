package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * QA (qa-lane-b-1103-0938, 2026-11-03): Scan Tool round trips for blocks with no item of their own, or whose placed
 * state differs from their item (docs/wiki/builder.md: "built from what a player would use"). Each scene is scanned with
 * real clicks and built back by a builder at the bench, as {@link QaScanRebuild1102dGameTests} does: still water and lava
 * (buckets), powder snow (its bucket), redstone dust and a tripwire (string between two hooks), double plants (both
 * halves), wall-attached signs, banners, heads and torches, cocoa on a jungle log, crops planted from their seeds,
 * bamboo, nether wart, hanging and under-water blocks, and potted plants. A paid run checks the empty water and lava
 * buckets come back, as they do for a player. What failed is in {@code QaScanItemless1103BugTests} (branch
 * tests/scan-itemless-1103).
 */
public class QaScanItemless1103GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos SRC = QaScanRebuild1102dGameTests.SRC;
	private static final BlockPos SUPPLY = new BlockPos(2, 2, 4);

	// ------------------------------------------------------------------------------------------------ helpers

	static Container stock(GameTestHelper helper, Map<Item, Integer> asked) {
		helper.setBlock(SUPPLY, Blocks.CHEST);
		Container chest = helper.getBlockEntity(SUPPLY);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : asked.entrySet()) {
			int left = e.getValue();
			while (left > 0) {
				int n = Math.min(left, new ItemStack(e.getKey()).getMaxStackSize());
				chest.setItem(slot++, new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
		return chest;
	}

	/** Scans the scene with real clicks, then a builder at the bench builds it back from a chest (not free). */
	static QaScanRebuild1102dGameTests.Built paidRoundTrip(GameTestHelper helper, QaScanRebuild1102dGameTests.Scene scene) {
		Leftovers.clear(helper);
		for (Map.Entry<BlockPos, BlockState> e : scene.blocks.entrySet()) {
			helper.setBlock(SRC.offset(e.getKey()), e.getValue());
		}
		ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, SRC.offset(scene.max()));
		helper.setBlock(SUPPLY, Blocks.CHEST);
		QaBuilders1102bGameTests.Built b = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.NONE, Mirror.NONE, false);
		return new QaScanRebuild1102dGameTests.Built(b, scene);
	}

	static int held(Container chest, Villager builder, Item item) {
		return chest.countItem(item) + (builder == null ? 0 : ModAttachments.BUILDER_BAG.getOrCreate(builder).count(item));
	}

	static void finished(GameTestHelper helper, QaScanRebuild1102dGameTests.Built b) {
		helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(b.b().site().id()) == null, "still building: " + b.b().site().stage()
			+ " " + b.b().site().status() + " missing " + b.b().site().missing());
	}

	/** Every block of the scene with {@code prop} has the same value again. */
	static String sameProperty(ServerLevel level, QaScanRebuild1102dGameTests.Built b,
							   net.minecraft.world.level.block.state.properties.Property<?> prop) {
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> e : b.scene().blocks.entrySet()) {
			if (e.getValue().hasProperty(prop)) {
				BlockState now = level.getBlockState(b.b().origin().offset(e.getKey()));
				if (!now.hasProperty(prop) || !now.getValue(prop).equals(e.getValue().getValue(prop))) {
					wrong.add(e.getKey().toShortString() + " " + e.getValue() + " built as " + now);
				}
			}
		}
		return wrong.isEmpty() ? null : String.join("; ", wrong);
	}

	// ------------------------------------------------------------------------------------------------ liquids

	/** A stone basin: a water source and a lava source, each in its own walled cell. */
	private static QaScanRebuild1102dGameTests.Scene pools() {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx < 5; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				s.put(dx, 0, dz, Blocks.STONE);
				s.put(dx, 1, dz, Blocks.STONE);
			}
		}
		s.put(1, 1, 1, Blocks.WATER);
		s.put(3, 1, 1, Blocks.LAVA);
		return s;
	}

	private static String poolsWrong(ServerLevel level, QaScanRebuild1102dGameTests.Built b) {
		BlockState water = level.getBlockState(b.at(1, 1, 1));
		BlockState lava = level.getBlockState(b.at(3, 1, 1));
		List<String> wrong = new ArrayList<>();
		if (!water.is(Blocks.WATER) || !water.getFluidState().isSource()) {
			wrong.add("water=" + water);
		}
		if (!lava.is(Blocks.LAVA) || !lava.getFluidState().isSource()) {
			wrong.add("lava=" + lava);
		}
		return wrong.isEmpty() ? null : String.join("; ", wrong);
	}

	/** A scanned water source and lava source ask one water bucket and one lava bucket and are sources again. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1103ilPools"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1103ilPools")
	public void aScannedWaterAndLavaSourceAreBuiltFromBuckets(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, pools(),
			Map.of(Items.WATER_BUCKET, 1, Items.LAVA_BUCKET, 1), true);
		helper.succeedWhen(() -> {
			finished(helper, b);
			String wrong = poolsWrong(helper.getLevel(), b);
			helper.assertTrue(wrong == null, "the pools weren't poured again: " + wrong);
		});
	}

	/** Paid from a chest: both sources poured, and both empty buckets come back. */
	//$ gametest_ticks_batch HUGE '4000' '"qa1103ilPoolsPaid"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qa1103ilPoolsPaid")
	public void paidPoolsGiveBothBucketsBack(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Built b = paidRoundTrip(helper, pools());
		ServerLevel level = helper.getLevel();
		Container chest = stock(helper, b.b().site().plan(level).materials());
		helper.succeedWhen(() -> {
			finished(helper, b);
			String wrong = poolsWrong(level, b);
			helper.assertTrue(wrong == null, "the paid pools weren't poured again: " + wrong);
			Villager builder = level.getEntitiesOfClass(Villager.class, helper.getBounds()).stream().findFirst().orElse(null);
			helper.assertTrue(held(chest, builder, Items.BUCKET) == 2, "of a water and a lava bucket poured, " + held(chest, builder, Items.BUCKET)
				+ " empty buckets came back");
		});
	}

	/** Powder snow (placed from a powder snow bucket) asks one powder snow bucket and stands again. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1103ilPowder"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1103ilPowder")
	public void scannedPowderSnowIsBuiltFromItsBucket(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.STONE);
		s.put(1, 0, 0, Blocks.STONE);
		s.put(0, 1, 0, Blocks.POWDER_SNOW);
		s.put(1, 1, 0, Blocks.POWDER_SNOW);
		QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.POWDER_SNOW_BUCKET, 2), false);
	}

	// ------------------------------------------------------------------------------------------------ redstone, string

	/** Three redstone dust and a tripwire between two hooks ask 3 redstone, 1 string and 2 hooks and are built again. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1103ilWire"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1103ilWire")
	public void scannedRedstoneDustAndATripwireAreBuiltFromDustAndString(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx < 5; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				s.put(dx, 0, dz, Blocks.STONE);
			}
		}
		for (int dx = 0; dx < 3; dx++) {
			s.put(dx, 1, 0, Blocks.REDSTONE_WIRE);
		}
		s.put(0, 1, 2, Blocks.STONE);
		s.put(4, 1, 2, Blocks.STONE);
		s.put(1, 1, 2, Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		s.put(2, 1, 2, Blocks.TRIPWIRE);
		s.put(3, 1, 2, Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
		QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.REDSTONE, 3, Items.STRING, 1, Items.TRIPWIRE_HOOK, 2), false);
	}

	// ------------------------------------------------------------------------------------------------ double plants

	private static void tall(QaScanRebuild1102dGameTests.Scene s, int x, Block plant) {
		s.put(x, 1, 0, plant.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
		s.put(x, 2, 0, plant.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
	}

	/** A sunflower, tall grass, a large fern and a rose bush on grass ask one each and stand two tall again. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1103ilTall"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1103ilTall")
	public void scannedDoublePlantsStandTwoTallAgain(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx < 4; dx++) {
			s.put(dx, 0, 0, Blocks.GRASS_BLOCK);
		}
		tall(s, 0, Blocks.SUNFLOWER);
		tall(s, 1, Blocks.TALL_GRASS);
		tall(s, 2, Blocks.LARGE_FERN);
		tall(s, 3, Blocks.ROSE_BUSH);
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s,
			Map.of(Items.SUNFLOWER, 1, Items.TALL_GRASS, 1, Items.LARGE_FERN, 1, Items.ROSE_BUSH, 1), true);
		helper.succeedWhen(() -> {
			b.done(helper);
			String wrong = sameProperty(helper.getLevel(), b, BlockStateProperties.DOUBLE_BLOCK_HALF);
			helper.assertTrue(wrong == null, "halves wrong: " + wrong);
		});
	}

	// ------------------------------------------------------------------------------------------------ on walls

	/**
	 * On a stone wall: an oak wall sign, a white wall banner, a skeleton wall skull, a soul wall torch and a redstone wall
	 * torch, and cocoa on a jungle log: each asks its item (sign, banner, skull, torch, cocoa beans) and is built on the
	 * wall again (the wall variant, facing the same way), never standing on the floor or left out.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qa1103ilWall"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1103ilWall")
	public void scannedWallSignsBannersHeadsTorchesAndCocoaHangOnTheWallAgain(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx < 6; dx++) {
			for (int dz = 0; dz < 2; dz++) {
				s.put(dx, 0, dz, Blocks.STONE);
			}
			s.put(dx, 1, 0, dx == 5 ? Blocks.JUNGLE_LOG : Blocks.STONE);
			s.put(dx, 2, 0, Blocks.STONE);
		}
		Direction south = Direction.SOUTH;
		s.put(0, 2, 1, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, south));
		s.put(1, 2, 1, Blocks.WHITE_WALL_BANNER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, south));
		s.put(2, 2, 1, Blocks.SKELETON_WALL_SKULL.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, south));
		s.put(3, 2, 1, Blocks.SOUL_WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, south));
		s.put(4, 2, 1, Blocks.REDSTONE_WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, south));
		s.put(5, 1, 1, Blocks.COCOA.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.OAK_SIGN, 1, Items.WHITE_BANNER, 1,
			Items.SKELETON_SKULL, 1, Items.SOUL_TORCH, 1, Items.REDSTONE_TORCH, 1, Items.COCOA_BEANS, 1), true);
		helper.succeedWhen(() -> {
			b.done(helper);
			String wrong = sameProperty(helper.getLevel(), b, BlockStateProperties.HORIZONTAL_FACING);
			helper.assertTrue(wrong == null, "facing wrong: " + wrong);
		});
	}

	// ------------------------------------------------------------------------------------------------ crops, shoots, pots

	/**
	 * Carrots, potatoes, beetroots, a torchflower crop and a young pitcher crop on farmland (the crops have no item of
	 * their own: they are planted from a carrot, a potato and seeds) ask one each and are planted again.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qa1103ilCrops"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1103ilCrops")
	public void scannedCropsArePlantedFromTheirSeeds(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		Block[] crops = {Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS, Blocks.TORCHFLOWER_CROP, Blocks.PITCHER_CROP};
		for (int dx = 0; dx < crops.length; dx++) {
			s.put(dx, 0, 0, Blocks.STONE);
			s.put(dx, 1, 0, Blocks.FARMLAND);
			s.put(dx, 2, 0, crops[dx]);
		}
		QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.CARROT, 1, Items.POTATO, 1, Items.BEETROOT_SEEDS, 1,
			Items.TORCHFLOWER_SEEDS, 1, Items.PITCHER_POD, 1), false);
	}

	/** Potted plants a player pots: an oak sapling, bamboo, crimson roots, an azalea and a cactus; one pot and plant each. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1103ilPots"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1103ilPots")
	public void scannedPottedPlantsAreBuiltFromAPotAndThePlant(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		Block[] pots = {Blocks.POTTED_OAK_SAPLING, Blocks.POTTED_BAMBOO, Blocks.POTTED_CRIMSON_ROOTS, Blocks.POTTED_AZALEA, Blocks.POTTED_CACTUS};
		for (int dx = 0; dx < pots.length; dx++) {
			s.put(dx, 0, 0, Blocks.STONE);
			s.put(dx, 1, 0, pots[dx]);
		}
		QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.FLOWER_POT, 5, Items.OAK_SAPLING, 1, Items.BAMBOO, 1,
			Items.CRIMSON_ROOTS, 1, Items.AZALEA, 1, Items.CACTUS, 1), false);
	}

	/** Bamboo three tall on grass asks three bamboo and stands three tall again (built bottom-up, each on the last). */
	//$ gametest_ticks_batch HUGE '3000' '"qa1103ilBamboo"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1103ilBamboo")
	public void scannedBambooThreeTallStandsThreeTallAgain(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.GRASS_BLOCK);
		for (int dy = 1; dy <= 3; dy++) {
			s.put(0, dy, 0, Blocks.BAMBOO);
		}
		QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.BAMBOO, 3), false);
	}

	/** Nether wart on soul sand (planted from nether wart, a crop block) asks one nether wart and is planted again. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1103ilWart"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1103ilWart")
	public void scannedNetherWartIsPlantedFromNetherWart(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.SOUL_SAND);
		s.put(0, 1, 0, Blocks.NETHER_WART.defaultBlockState().setValue(BlockStateProperties.AGE_3, 3));
		QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.NETHER_WART, 1), false);
	}

	/** A grown pitcher plant on grass and a small dripleaf on moss (both two tall) ask one each and stand two tall again. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1103ilTall2"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1103ilTall2")
	public void aScannedPitcherPlantAndSmallDripleafStandTwoTallAgain(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.GRASS_BLOCK);
		s.put(1, 0, 0, Blocks.MOSS_BLOCK);
		tall(s, 0, Blocks.PITCHER_PLANT);
		tall(s, 1, Blocks.SMALL_DRIPLEAF);
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s,
			Map.of(Items.PITCHER_PLANT, 1, Items.SMALL_DRIPLEAF, 1), true);
		helper.succeedWhen(() -> {
			b.done(helper);
			String wrong = sameProperty(helper.getLevel(), b, BlockStateProperties.DOUBLE_BLOCK_HALF);
			helper.assertTrue(wrong == null, "halves wrong: " + wrong);
		});
	}

	/**
	 * Under a stone ceiling: a spore blossom, hanging roots, and a hanging lantern on a chain; on a wall: an oak wall
	 * hanging sign, a player wall head and a dead tube coral wall fan. Each asks its own item and hangs again.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qa1103ilHang"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1103ilHang")
	public void scannedHangingAndWallThingsHangAgain(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx < 4; dx++) {
			for (int dz = 0; dz < 2; dz++) {
				s.put(dx, 0, dz, Blocks.STONE);
			}
			s.put(dx, 1, 0, Blocks.STONE);
			s.put(dx, 2, 0, Blocks.STONE);
			s.put(dx, 3, 0, Blocks.STONE);
			s.put(dx, 3, 1, Blocks.STONE);
		}
		s.put(0, 2, 1, Blocks.SPORE_BLOSSOM);
		s.put(1, 2, 1, Blocks.HANGING_ROOTS);
		s.put(2, 2, 1, Blocks.CHAIN);
		s.put(2, 1, 1, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
		s.put(3, 1, 1, Blocks.OAK_WALL_HANGING_SIGN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		s.put(0, 1, 1, Blocks.PLAYER_WALL_HEAD.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		s.put(1, 1, 1, Blocks.DEAD_TUBE_CORAL_WALL_FAN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
			.setValue(BlockStateProperties.WATERLOGGED, false));
		QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.SPORE_BLOSSOM, 1, Items.HANGING_ROOTS, 1, Items.CHAIN, 1,
			Items.LANTERN, 1, Items.OAK_HANGING_SIGN, 1, Items.PLAYER_HEAD, 1, Items.DEAD_TUBE_CORAL_FAN, 1), false);
	}

	/**
	 * In a stone pool: living tube coral on a wall, under water, and seagrass on the pool's floor. The coral fan asks a
	 * fan and a water bucket and is built waterlogged (B817's rule for living coral, which dies dry); the seagrass stands.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qa1103ilReef"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1103ilReef")
	public void aScannedLivingCoralWallFanAndSeagrassAreBuiltUnderWater(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx < 3; dx++) {
			for (int dz = 0; dz < 4; dz++) {
				s.put(dx, 0, dz, Blocks.STONE);
				s.put(dx, 1, dz, Blocks.STONE);
			}
		}
		s.put(1, 1, 1, Blocks.TUBE_CORAL_WALL_FAN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
			.setValue(BlockStateProperties.WATERLOGGED, true));
		s.put(1, 1, 2, Blocks.SEAGRASS);
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s,
			Map.of(Items.TUBE_CORAL_FAN, 1, Items.SEAGRASS, 1), true);
		helper.succeedWhen(() -> {
			b.done(helper);
			BlockState fan = helper.getLevel().getBlockState(b.at(1, 1, 1));
			helper.assertTrue(fan.is(Blocks.TUBE_CORAL_WALL_FAN) && fan.getValue(BlockStateProperties.WATERLOGGED), "the living fan was built as " + fan);
		});
	}
}
