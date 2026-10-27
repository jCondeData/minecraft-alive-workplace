package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mechanic.MechanicWork;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (lane E, Create, 2026-10-27), from the wiki's Create page (39.2): "When a shaft, gearbox, cogwheel or belt goes
 * missing from a finished build within 32 blocks of the toolbox, the Mechanic fetches the part". Both sides of the 32
 * (a big build whose corner lies farther: QaMechanicReachBugCompatTests on tests/mechanic-reach-1027). Each test has its own batch: the builds it records as
 * finished are forgotten after it.
 */
public class QaMechanicReachCompatTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_compat:huge_area";
	private static final BlockPos TOOLBOX = new BlockPos(1, 2, 1);
	private static final AtomicInteger IDS = new AtomicInteger();

	/** A shaft gone from a build 31.1 blocks from the toolbox is on the Mechanic's list; one 33.2 blocks off isn't. */
	//$ gametest_batch HUGE '"qa_mechanic_reach_edge"'
	@GameTest(template = HUGE, batch = "qa_mechanic_reach_edge")
	public void aBuildThirtyOneBlocksAwayIsMendedAndOneThirtyThreeAwayIsNot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		forgetOthers(helper);
		helper.setBlock(TOOLBOX, MechanicCompatTests.create("brown_toolbox").defaultBlockState());
		BlockPos near = new BlockPos(23, 2, 23); // 31.1 from the toolbox
		BlockPos far = new BlockPos(24, 2, 25); // 33.2
		ResourceLocation one = shaftBlueprint(helper, 1);
		record(helper, one, near, Rotation.NONE);
		record(helper, one, far, Rotation.NONE);
		List<BlockPos> holes = MechanicWork.missingParts(level, helper.absolutePos(TOOLBOX)).stream().map(MechanicWork.Part::pos).toList();
		helper.assertTrue(holes.contains(helper.absolutePos(near)), "a shaft gone from a build 31 blocks from the toolbox isn't the Mechanic's: "
			+ holes.stream().map(helper::relativePos).toList());
		helper.assertTrue(!holes.contains(helper.absolutePos(far)), "a shaft gone from a build 33 blocks from the toolbox is the Mechanic's");
		helper.succeed();
	}

	/** A blueprint {@code length} long along x with one shaft (axis x) at its far end, the rest left as it stands. */
	private static ResourceLocation shaftBlueprint(GameTestHelper helper, int length) {
		ServerLevel level = helper.getLevel();
		BlockPos row = new BlockPos(2, 6, 28);
		for (int x = 0; x < length; x++) {
			helper.setBlock(row.offset(x, 0, 0), Blocks.STRUCTURE_VOID);
		}
		BlockState shaft = MechanicCompatTests.with(MechanicCompatTests.create("shaft").defaultBlockState(), "axis", "x");
		helper.setBlock(row.offset(length - 1, 0, 0), shaft);
		ResourceLocation id = AliveWorkplace.id("compat_test/mechanic_line_qa_reach_" + IDS.incrementAndGet());
		StructureTemplate template = level.getStructureManager().getOrCreate(id);
		template.fillFromWorld(level, helper.absolutePos(row), new Vec3i(length, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.assertTrue(BlueprintLibrary.get(level, id).isPresent(), "setup: could not register the test blueprint");
		for (int x = 0; x < length; x++) {
			helper.setBlock(row.offset(x, 0, 0), Blocks.AIR);
		}
		return id;
	}

	/** {@code id} recorded as a finished village build at {@code origin} (relative), forgotten after the test. */
	private static void record(GameTestHelper helper, ResourceLocation id, BlockPos origin, Rotation rotation) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), rotation, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id, placement, UUID.randomUUID());
		PartnerShowsCompatTests.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
	}

	/** The test lines earlier tests left recorded as finished (their areas cleared): holes within this toolbox's reach. */
	private static void forgetOthers(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		BuildSiteManager sites = BuildSiteManager.get(helper.getLevel());
		for (BuildSiteManager.Finished f : sites.finishedIn(helper.getLevel())) {
			if (f.structure().getPath().startsWith("compat_test/mechanic_line_")) {
				sites.forgetFinished(f.placement());
			}
		}
	}
}
