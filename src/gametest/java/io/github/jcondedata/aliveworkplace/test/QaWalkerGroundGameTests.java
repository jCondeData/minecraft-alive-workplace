package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Paths;
import io.github.jcondedata.aliveworkplace.work.Walker;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * B1017: workers pick where to stand by {@link Walker#canStand}, builders' paths by {@link Paths#standOn} (B981). Both
 * must agree on what is ground: mud, soul sand and deep snow (lower than a block) are, an enchanting table is not.
 */
public class QaWalkerGroundGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	/** Every kind of ground a worker or a path might meet: the two rules give the same answer for each. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void walkerAndPathsAgreeOnWhatIsGround(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockState[] grounds = {
			Blocks.MUD.defaultBlockState(), Blocks.SOUL_SAND.defaultBlockState(), Blocks.DIRT_PATH.defaultBlockState(),
			Blocks.FARMLAND.defaultBlockState(), Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 8),
			Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 7), Blocks.STONE.defaultBlockState(),
			Blocks.ENCHANTING_TABLE.defaultBlockState(), Blocks.OAK_SLAB.defaultBlockState(), Blocks.CHEST.defaultBlockState(),
			Blocks.OAK_FENCE.defaultBlockState(), Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 3),
		};
		StringBuilder disagree = new StringBuilder();
		for (int i = 0; i < grounds.length; i++) {
			BlockPos ground = new BlockPos(1 + i, 2, 3);
			helper.setBlock(ground, grounds[i]);
			BlockPos abs = helper.absolutePos(ground);
			boolean walker = Walker.canStand(level, abs.above());
			boolean paths = Paths.standOn(level, abs, level.getBlockState(abs));
			if (walker != paths) {
				disagree.append(grounds[i]).append(": worker ").append(walker).append(", path ").append(paths).append("; ");
			}
		}
		helper.assertTrue(disagree.isEmpty(), "the worker's and the path's ground rules disagree: " + disagree);
		for (Block low : new Block[] {Blocks.MUD, Blocks.SOUL_SAND}) {
			BlockPos ground = new BlockPos(2, 2, 6);
			helper.setBlock(ground, low);
			helper.assertTrue(Walker.canStand(level, helper.absolutePos(ground.above())), "a worker can't stand on " + low);
		}
		helper.succeed();
	}

	/** A worker whose only spot in reach of his work is a strip of mud walks out onto it. */
	//$ gametest_ticks_batch AREA '400' '"qaWalkerMud"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaWalkerMud")
	public void aWorkerReachesHisOnlySpotOnMud(GameTestHelper helper) {
		onlySpotOn(helper, Blocks.MUD);
	}

	/** The same on soul sand. */
	//$ gametest_ticks_batch AREA '400' '"qaWalkerSoulSand"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaWalkerSoulSand")
	public void aWorkerReachesHisOnlySpotOnSoulSand(GameTestHelper helper) {
		onlySpotOn(helper, Blocks.SOUL_SAND);
	}

	private static void onlySpotOn(GameTestHelper helper, Block ground) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos work = new BlockPos(8, 2, 6); // the spot he works at (a crop, a fire): nothing to stand on there
		// a pool all round it, with one strip of the low ground leading out to it from the dry floor
		for (int x = 4; x <= 12; x++) {
			for (int z = 2; z <= 10; z++) {
				helper.setBlock(new BlockPos(x, 1, z), x == 8 && z >= 7 ? ground : Blocks.WATER);
			}
		}
		BlockPos start = new BlockPos(8, 2, 14);
		Villager worker = helper.spawn(EntityType.VILLAGER, start);
		worker.setVillagerData(worker.getVillagerData().setProfession(VillagerProfession.FARMER));
		BlockPos spot = Walker.standingSpot(level, helper.absolutePos(work), helper.absolutePos(start), 3.5);
		helper.assertTrue(spot != null, "no spot to stand at in reach of the work: " + ground + " isn't ground for a worker");
		helper.assertTrue(level.getBlockState(spot.below()).is(ground), "the spot isn't on " + ground + ": " + spot + " on " + level.getBlockState(spot.below()));
		int[] retry = {0};
		helper.onEachTick(() -> retry[0] = Walker.requestWalk(worker, spot, 0.5f, 0, retry[0]));
		helper.succeedWhen(() -> {
			double off = worker.position().distanceTo(Vec3.atBottomCenterOf(spot));
			helper.assertTrue(off < 0.9, "the worker didn't reach his spot on " + ground + ": " + String.format("%.2f", off)
				+ " blocks off, at " + worker.blockPosition() + ", spot " + spot);
		});
	}
}
