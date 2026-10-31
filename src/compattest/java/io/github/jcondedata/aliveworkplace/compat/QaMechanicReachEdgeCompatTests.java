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
 * QA (lane E, Create, 2026-10-27) for B598's fix: "a build within 32 blocks (its nearest point) counts". The 32 from
 * both sides: a one-block build whose shaft is gone exactly 32 blocks from the toolbox is the Mechanic's to mend; the
 * same 33 blocks off is not.
 */
public class QaMechanicReachEdgeCompatTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_compat:huge_area";
	private static final BlockPos TOOLBOX = new BlockPos(1, 2, 1);
	private static final AtomicInteger IDS = new AtomicInteger();

	//$ gametest_batch HUGE '"qa_mechanic_reach_edge_32"'
	@GameTest(template = HUGE, batch = "qa_mechanic_reach_edge_32")
	public void aBuildThirtyTwoBlocksOffIsMendedAndThirtyThreeIsNot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		forgetOthers(helper);
		helper.setBlock(TOOLBOX, MechanicCompatTests.create("brown_toolbox").defaultBlockState());
		BlockPos toolbox = helper.absolutePos(TOOLBOX);
		for (int off : new int[] {32, 33}) {
			ResourceLocation id = shaftBlueprint(helper, 1);
			BlockPos at = TOOLBOX.south(off);
			helper.setBlock(at, Blocks.AIR);
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(at), Rotation.NONE, Mirror.NONE);
			BuildSiteManager.get(level).recordFinished(id, placement, UUID.randomUUID());
			List<MechanicWork.Part> holes = MechanicWork.missingParts(level, toolbox);
			boolean listed = holes.stream().anyMatch(p -> p.pos().equals(helper.absolutePos(at)));
			BuildSiteManager.get(level).forgetFinished(placement);
			helper.assertTrue(listed == (off <= 32), "a shaft gone " + off + " blocks from the toolbox is " + (listed ? "" : "not ")
				+ "on the Mechanic's list: " + holes.stream().map(p -> helper.relativePos(p.pos())).toList());
		}
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
		ResourceLocation id = AliveWorkplace.id("compat_test/mechanic_line_qa_reachedge_" + IDS.incrementAndGet());
		StructureTemplate template = level.getStructureManager().getOrCreate(id);
		template.fillFromWorld(level, helper.absolutePos(row), new Vec3i(length, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.assertTrue(BlueprintLibrary.get(level, id).isPresent(), "setup: could not register the test blueprint");
		for (int x = 0; x < length; x++) {
			helper.setBlock(row.offset(x, 0, 0), Blocks.AIR);
		}
		return id;
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
