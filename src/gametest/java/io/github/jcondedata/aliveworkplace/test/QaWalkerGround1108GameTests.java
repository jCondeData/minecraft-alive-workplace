package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Paths;
import io.github.jcondedata.aliveworkplace.work.Walker;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * QA (lane-b-1108-0337, B1017's verification): B1017 asked for one ground rule for workers ({@link Walker#canStand})
 * and builders' paths ({@link Paths#standOn}, {@link Paths#walkable}: "a full top, or a top a little lower than a block
 * and flat all across ... a lectern, a chest or an enchanting table is not ground"). QaWalkerGroundGameTests checks 12
 * grounds; this checks the rest a village or a build puts under someone's feet: every snow depth, slabs and stairs both
 * ways up, trapdoors shut and open, a honey block, a
 * lectern, a composter, a cauldron, a hopper, scaffolding, powder snow, a bed, a cake, magma and more. For each the two
 * rules give the same answer, and the plain cases are what a player expects: a top slab, top stairs, a shut trapdoor at
 * the top, glass and soul soil are ground; a lectern, powder snow, a honey block, a carpet, a composter, a cauldron and a
 * hopper are not. (A big dripleaf and an end portal frame: QaWalkerGround1108BugTests, B1030.)
 */
public class QaWalkerGround1108GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	//$ gametest AREA
	@GameTest(template = AREA)
	public void workersAndPathsAgreeOnEveryOtherGround(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockState[] grounds = {
			snow(1), snow(2), snow(4), snow(5), snow(6),
			Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
			Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE),
			Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.BOTTOM),
			Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.TOP),
			Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP),
			Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.BOTTOM),
			Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP).setValue(TrapDoorBlock.OPEN, true),
			Blocks.HONEY_BLOCK.defaultBlockState(), Blocks.LECTERN.defaultBlockState(),
			Blocks.COMPOSTER.defaultBlockState(), Blocks.CAULDRON.defaultBlockState(), Blocks.HOPPER.defaultBlockState(),
			Blocks.SCAFFOLDING.defaultBlockState(), Blocks.POWDER_SNOW.defaultBlockState(), Blocks.RED_BED.defaultBlockState(),
			Blocks.CAKE.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.SOUL_SOIL.defaultBlockState(),
			Blocks.GLASS.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState(), Blocks.DAYLIGHT_DETECTOR.defaultBlockState(),
			Blocks.STONECUTTER.defaultBlockState(), Blocks.CHAIN.defaultBlockState(), Blocks.IRON_BARS.defaultBlockState(),
			Blocks.ANVIL.defaultBlockState(), Blocks.BREWING_STAND.defaultBlockState(), Blocks.SCULK_SENSOR.defaultBlockState(),
			Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false),
			Blocks.CONDUIT.defaultBlockState(), Blocks.BELL.defaultBlockState(), Blocks.GRINDSTONE.defaultBlockState(),
			Blocks.PISTON_HEAD.defaultBlockState(), Blocks.MOSS_CARPET.defaultBlockState(),
			Blocks.WHITE_CARPET.defaultBlockState(), Blocks.LILY_PAD.defaultBlockState(), Blocks.FLOWER_POT.defaultBlockState(),
			Blocks.DECORATED_POT.defaultBlockState(), Blocks.BARREL.defaultBlockState(), Blocks.SHULKER_BOX.defaultBlockState(),
		};
		StringBuilder disagree = new StringBuilder();
		StringBuilder all = new StringBuilder();
		for (int i = 0; i < grounds.length; i++) {
			BlockPos ground = new BlockPos(1 + i % 12, 2, 1 + 3 * (i / 12));
			helper.setBlock(ground, grounds[i]);
			BlockPos abs = helper.absolutePos(ground);
			BlockState placed = level.getBlockState(abs);
			boolean walker = Walker.canStand(level, abs.above());
			boolean paths = Paths.standOn(level, abs, placed);
			all.append(placed.getBlock().getDescriptionId()).append(walker ? "+" : "-").append(paths ? "+" : "-").append(' ');
			if (walker != paths) {
				disagree.append(placed).append(": worker ").append(walker).append(", path ").append(paths).append("; ");
			}
		}
		QaBasementGround1104GameTests.LOGGER.info("qa walker ground 1108 (worker, path): {}", all);
		helper.assertTrue(disagree.isEmpty(), "the worker's and the path's ground rules disagree: " + disagree);
		BlockState[] ground = {Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
			Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.TOP),
			Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP), Blocks.GLASS.defaultBlockState(),
			Blocks.SOUL_SOIL.defaultBlockState()};
		BlockState[] notGround = {Blocks.LECTERN.defaultBlockState(), Blocks.POWDER_SNOW.defaultBlockState(), Blocks.HONEY_BLOCK.defaultBlockState(),
			Blocks.WHITE_CARPET.defaultBlockState(), Blocks.COMPOSTER.defaultBlockState(), Blocks.CAULDRON.defaultBlockState(),
			Blocks.HOPPER.defaultBlockState()};
		BlockPos spot = new BlockPos(14, 2, 14);
		BlockPos abs = helper.absolutePos(spot);
		for (BlockState g : ground) {
			helper.setBlock(spot, g);
			helper.assertTrue(Walker.canStand(level, abs.above()) && Paths.standOn(level, abs, level.getBlockState(abs)), g + " isn't ground");
		}
		for (BlockState g : notGround) {
			helper.setBlock(spot, g);
			helper.assertFalse(Walker.canStand(level, abs.above()) || Paths.standOn(level, abs, level.getBlockState(abs)), g + " is taken for ground");
		}
		helper.succeed();
	}

	private static BlockState snow(int layers) {
		return Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers);
	}
}
