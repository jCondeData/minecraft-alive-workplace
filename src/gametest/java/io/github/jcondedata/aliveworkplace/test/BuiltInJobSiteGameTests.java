package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.JobSiteApproach;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SmokerBlock;

/**
 * B168: a workstation built in under solid blocks (the Kitchen's smoker, the mouth of a brick oven) is still taken. Vanilla
 * walks a villager to the block it picked, but its ground navigation lifts a target on a solid block up through the bricks,
 * and the villager walked to the nearest raised spot instead (a chest beside the oven), two steps from the smoker, and
 * stood there until its pick ran out: the Kitchen failed whenever a decoration took the worktop's top.
 */
public class BuiltInJobSiteGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos SMOKER = new BlockPos(8, 2, 8);

	/** The oven: a smoker facing west under three bricks, a chest two blocks along its front (as the
	 * Kitchen's larder chest stands). */
	private static void oven(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(SMOKER, Blocks.SMOKER.defaultBlockState().setValue(SmokerBlock.FACING, Direction.WEST));
		for (int y = 1; y <= 3; y++) {
			helper.setBlock(SMOKER.above(y), Blocks.BRICKS);
		}
		helper.setBlock(new BlockPos(7, 2, 6), Blocks.CHEST);
	}

	private static void takesTheSmoker(GameTestHelper helper, Villager villager, VillagerProfession job) {
		BlockPos smoker = helper.absolutePos(SMOKER);
		helper.succeedWhen(() -> {
			helper.assertTrue(villager.isAlive(), "the villager died");
			helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos().equals(smoker)).orElse(false),
				"the villager works at " + villager.getBrain().getMemory(MemoryModuleType.JOB_SITE) + " standing at "
					+ helper.relativePos(villager.blockPosition()).toShortString() + ", picked "
					+ villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).map(g -> helper.relativePos(g.pos()).toShortString()).orElse("none"));
			helper.assertTrue(villager.getVillagerData().getProfession() == job, "the villager is a " + villager.getVillagerData().getProfession());
		});
	}

	/** A chef with no workstation walks in from the north past the chest and takes the built-in smoker. */
	//$ gametest_ticks_batch AREA '1200' '"builtInJobSiteChef"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "builtInJobSiteChef")
	public void chefTakesASmokerUnderBricks(GameTestHelper helper) {
		oven(helper);
		Villager chef = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 2));
		chef.setVillagerData(chef.getVillagerData().setProfession(ModVillagers.CHEF));
		chef.setVillagerXp(1);
		chef.refreshBrain(helper.getLevel()); // as when a villager takes a job: its brain looks for that job's blocks
		takesTheSmoker(helper, chef, ModVillagers.CHEF);
	}

	/** A jobless villager takes it too, and becomes its butcher, as at any smoker. */
	//$ gametest_ticks_batch AREA '1200' '"builtInJobSiteJobless"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "builtInJobSiteJobless")
	public void joblessVillagerTakesASmokerUnderBricks(GameTestHelper helper) {
		oven(helper);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 2));
		takesTheSmoker(helper, villager, VillagerProfession.BUTCHER);
	}

	/**
	 * Where a villager is walked: the open floor the workstation faces, another side when that's walled, nothing when
	 * vanilla's own walk is right (open air over the workstation) or no side has floor.
	 */
	//$ gametest_ticks_batch AREA '20' '"builtInJobSiteSpot"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "builtInJobSiteSpot")
	public void besideABuiltInWorkstation(GameTestHelper helper) {
		oven(helper);
		var level = helper.getLevel();
		BlockPos smoker = helper.absolutePos(SMOKER);
		BlockPos from = helper.absolutePos(new BlockPos(14, 2, 8)); // the east side is nearer, the front comes first
		helper.assertTrue(JobSiteApproach.approach(level, smoker, from).equals(Optional.of(smoker.west())),
			"not the front: " + JobSiteApproach.approach(level, smoker, from));
		helper.setBlock(SMOKER.west(), Blocks.BRICKS);
		helper.assertTrue(JobSiteApproach.approach(level, smoker, from).equals(Optional.of(smoker.east())),
			"front walled, not the nearest side: " + JobSiteApproach.approach(level, smoker, from));
		helper.setBlock(SMOKER.west(), Blocks.AIR);
		helper.setBlock(SMOKER.west().above(), Blocks.OAK_SLAB); // no head room over the front
		helper.assertTrue(JobSiteApproach.approach(level, smoker, from).equals(Optional.of(smoker.east())),
			"a front with no head room: " + JobSiteApproach.approach(level, smoker, from));
		for (Direction side : Direction.Plane.HORIZONTAL) {
			helper.setBlock(SMOKER.relative(side), Blocks.BRICKS);
		}
		helper.assertTrue(JobSiteApproach.approach(level, smoker, from).isEmpty(), "walled in, yet a spot: " + JobSiteApproach.approach(level, smoker, from));
		for (Direction side : Direction.Plane.HORIZONTAL) {
			helper.setBlock(SMOKER.relative(side), Blocks.AIR);
		}
		helper.setBlock(SMOKER.west().above(), Blocks.AIR);
		helper.setBlock(SMOKER.above(), Blocks.AIR);
		helper.assertTrue(JobSiteApproach.approach(level, smoker, from).isEmpty(), "open air over it, yet vanilla's walk changed");
		helper.succeed();
	}
}
