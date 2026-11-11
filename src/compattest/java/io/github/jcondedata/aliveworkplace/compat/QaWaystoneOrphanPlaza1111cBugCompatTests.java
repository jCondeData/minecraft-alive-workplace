package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA bug test (lane C, 2026-11-11), B996's "possibly related" note, confirmed: with Brookmere's Village Hall broken (its
 * plaza and stone still standing), Ashford's next hall round counts Brookmere's plaza as Ashford's own (its nearest hall
 * is now Ashford) and moves Ashford's network stone there, so settlers and caravans for Ashford step out of the
 * hall-less plaza's stone and Ashford's Map distances are measured from it. Expected: a village keeps its own plaza's
 * stone as its stone on the network.
 */
public class QaWaystoneOrphanPlaza1111cBugCompatTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_compat:huge_area";

	// ---- B996 (two villages with plazas, as QaWaystones1107dBugCompatTests sets them up) ------------------------------

	private static final BlockPos HALL_A = new BlockPos(1, 2, 1);
	private static final BlockPos HALL_B = new BlockPos(28, 2, 28);
	private static final BlockPos PLAZA_A = new BlockPos(3, 2, 3);
	private static final BlockPos PLAZA_B = new BlockPos(19, 2, 19);
	private static final BlockPos STONE = new BlockPos(3, 2, 3);

	private static BlockPos a(GameTestHelper helper) {
		return helper.absolutePos(HALL_A);
	}

	private static BlockPos b(GameTestHelper helper) {
		return helper.absolutePos(HALL_B);
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos origin) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
	}

	private static void plaza(GameTestHelper helper, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = BlueprintLibrary.get(level, VillageWaystones.PLAZA).orElseThrow(() -> new GameTestAssertException("no plaza blueprint"));
		BuildPlan plan = BuildPlan.create(blueprint, placement(helper, origin));
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION, BuildPlan.Stage.LANDSCAPE)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				level.setBlock(step.pos(), step.state(), 2);
				if (step.secondaryPos() != null && step.secondaryState() != null) {
					level.setBlock(step.secondaryPos(), step.secondaryState(), 2);
				}
			}
		}
		BuildSiteManager.get(level).recordFinished(VillageWaystones.PLAZA, placement(helper, origin), UUID.randomUUID());
	}

	private static BlockPos stone(GameTestHelper helper, BlockPos plaza) {
		return helper.absolutePos(plaza).offset(STONE);
	}

	private static void hall(GameTestHelper helper, BlockPos at, String name, UUID owner) {
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(at);
		hall.setCustomName(Component.literal(name));
		hall.setOwner(owner, "Jesse");
	}

	private static void cleanUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (BlockPos at : List.of(HALL_A, HALL_B)) {
			VillageWaystones.Data.get(level).forget(helper.absolutePos(at));
		}
		for (BlockPos plaza : List.of(PLAZA_A, PLAZA_B)) {
			BuildSiteManager.get(level).forgetFinished(placement(helper, plaza));
		}
	}

	/** Ashford (A) and Brookmere (B), each with a hall and a plaza whose stone is raised, both on the network. */
	private static UUID start(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSiteManager.Finished f : manager.finishedNear(level, a(helper), 160)) {
			if (VillageWaystones.isPlaza(f.structure())) {
				manager.forgetFinished(f.placement());
			}
		}
		for (io.github.jcondedata.aliveworkplace.build.BuildSite site : List.copyOf(manager.all())) {
			if (VillageWaystones.isPlaza(site.structure()) && site.placement().origin().closerThan(a(helper), 160)) {
				manager.remove(site.id());
			}
		}
		cleanUp(helper);
		VillageWaystones.Data.get(level).halls().forEach(VillageWaystones.Data.get(level)::forget);
		UUID owner = UUID.randomUUID();
		hall(helper, HALL_A, "Ashford", owner);
		hall(helper, HALL_B, "Brookmere", owner);
		plaza(helper, PLAZA_A);
		plaza(helper, PLAZA_B);
		VillageWaystones.round(level, a(helper));
		VillageWaystones.round(level, b(helper));
		helper.assertTrue(VillageWaystones.linked(level, a(helper), b(helper)), "setup: the two stones don't stand");
		return owner;
	}

	/**
	 * The bug's "possibly related" note: with Brookmere's hall gone, Ashford's own round still keeps Ashford's own stone as
	 * its stone (settlers to Ashford step out of Ashford's plaza, not the hall-less one), and Brookmere stays off the
	 * network.
	 */
	//$ gametest_batch HUGE '"qaWaystoneOrphanPlaza1111cBug"'
	@GameTest(template = HUGE, batch = "qaWaystoneOrphanPlaza1111cBug")
	public void theRemainingVillagesRoundKeepsItsOwnStone(GameTestHelper helper) {
		start(helper);
		ServerLevel level = helper.getLevel();
		try {
			helper.assertTrue(VillageWaystones.stoneOf(level, a(helper)).equals(java.util.Optional.of(stone(helper, PLAZA_A))),
				"setup: Ashford's stone is " + VillageWaystones.stoneOf(level, a(helper)));
			helper.setBlock(HALL_B, Blocks.AIR);
			io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
			VillageWaystones.round(level, a(helper));
			VillageWaystones.round(level, a(helper));
			helper.assertTrue(VillageWaystones.stoneOf(level, a(helper)).equals(java.util.Optional.of(stone(helper, PLAZA_A))),
				"after Brookmere's hall went, Ashford's round moved Ashford's stone to " + VillageWaystones.stoneOf(level, a(helper))
					+ " (its own is " + stone(helper, PLAZA_A) + ", Brookmere's " + stone(helper, PLAZA_B) + ")");
			helper.assertFalse(VillageWaystones.villages(level).contains(b(helper)), "Brookmere, whose hall is gone, is back on the network");
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}
}
