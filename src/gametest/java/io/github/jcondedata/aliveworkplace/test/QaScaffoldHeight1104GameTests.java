package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.Scaffolds;
import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-b-1104-0938), the wiki's scaffolding page (scaffolding.md, "Where"): "Under it a column runs down through air
 * ... to the ground, a floor, a wall top or a column already standing, at most 32 blocks. The one needing the fewest new
 * scaffolds wins." Each test opens a shaft of air over the area's floor and asks where a column would go for a block
 * high over it: the lowest place to stand within reach is five below the block, so a block {@code n + 6} over the floor
 * needs a column of exactly {@code n} scaffolds. A column of 31 is planned and one of 33 is not; the column of exactly 32
 * is the boundary (its own test). Planning only, no builder: a real build of a 38-high spire can't fit in a test area.
 */
public class QaScaffoldHeight1104GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation PILLAR = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_pillar");
	/** Where the shaft is: the area's floor is at y = 1 here. */
	private static final BlockPos SHAFT = new BlockPos(6, 1, 6);
	/** The reach the builder plans with (Scaffolds: reach - 0.5, reach 5). */
	private static final double REACH = 4.5;
	private static final Method COLUMN;
	private static final Method CELLS;
	private static final Method STAND;

	static {
		try {
			COLUMN = Scaffolds.class.getDeclaredMethod("column", ServerLevel.class, BuildPlan.class, BlockPos.class, BlockPos.class, double.class);
			COLUMN.setAccessible(true);
			Class<?> column = Class.forName("io.github.jcondedata.aliveworkplace.build.Scaffolds$Column");
			CELLS = column.getDeclaredMethod("cells");
			STAND = column.getDeclaredMethod("stand");
			CELLS.setAccessible(true);
			STAND.setAccessible(true);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Scaffolds.column has changed shape: update this test", e);
		}
	}

	/** The column planned for a block {@code n + 6} over the floor (null for none), as {cells, stand y over the floor}. */
	static int[] planned(GameTestHelper helper, int n) {
		ServerLevel level = helper.getLevel();
		BlockPos floor = helper.absolutePos(SHAFT);
		helper.assertTrue(level.getBlockState(floor).isFaceSturdy(level, floor, net.minecraft.core.Direction.UP),
			"setup: the floor under the shaft isn't solid: " + level.getBlockState(floor));
		BlockPos target = floor.above(n + 6);
		// every cell the search could look at, over the floor, is made air: the area's barrier lid (its top) and
		// anything left above the area by older tests would otherwise be a floor to stand a column on
		for (int y = 1; y <= n + 8; y++) {
			for (int dx = -5; dx <= 5; dx++) {
				for (int dz = -5; dz <= 5; dz++) {
					BlockPos p = floor.offset(dx, y, dz);
					if (!level.getBlockState(p).isAir()) {
						level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		}
		Blueprint pillar = BlueprintLibrary.get(level, PILLAR).orElseThrow(() -> new GameTestAssertException("no blueprint " + PILLAR));
		// the drawing is placed well away from the shaft, so it leaves the whole shaft air
		BuildPlan plan = BuildPlan.create(pillar, new BlueprintData.Placement(level.dimension().location(),
			helper.absolutePos(new BlockPos(20, 2, 20)), Rotation.NONE, Mirror.NONE));
		try {
			Object column = COLUMN.invoke(null, level, plan, target, floor.above(), REACH);
			if (column == null) {
				return null;
			}
			List<?> cells = (List<?>) CELLS.invoke(column);
			BlockPos stand = (BlockPos) STAND.invoke(column);
			return new int[]{cells.size(), stand.getY() - floor.getY()};
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** A block 37 over the floor: a column of 31 scaffolds from the floor, the builder standing on it 32 up. */
	//$ gametest 'HUGE'
	@GameTest(template = HUGE)
	public void aColumnOfThirtyOneScaffoldsIsPlanned(GameTestHelper helper) {
		int[] c = planned(helper, 31);
		helper.assertTrue(c != null, "no column planned for a block 37 over the floor (31 scaffolds needed)");
		helper.assertTrue(c[0] == 31 && c[1] == 32, "a block 37 over the floor: expected a column of 31 scaffolds standing 32 up, seen "
			+ c[0] + " scaffolds, standing " + c[1] + " up");
		helper.succeed();
	}

	/** A block 39 over the floor needs 33 scaffolds, more than the 32 the page allows: no column is planned. */
	//$ gametest 'HUGE'
	@GameTest(template = HUGE)
	public void aColumnOfThirtyThreeScaffoldsIsNeverPlanned(GameTestHelper helper) {
		int[] c = planned(helper, 33);
		helper.assertTrue(c == null, "a column was planned for a block 39 over the floor, past the 32-block limit: " + (c == null ? ""
			: c[0] + " scaffolds, standing " + c[1] + " up"));
		helper.succeed();
	}

	/** Nearer the floor the fewest scaffolds still win: a block 10 over it gets a column of 4, not a taller one. */
	//$ gametest 'HUGE'
	@GameTest(template = HUGE)
	public void aLowBlockGetsTheShortestColumn(GameTestHelper helper) {
		int[] c = planned(helper, 4);
		helper.assertTrue(c != null && c[0] == 4 && c[1] == 5, "a block 10 over the floor: expected a column of 4 scaffolds standing 5 up, seen "
			+ (c == null ? "none" : c[0] + " scaffolds, standing " + c[1] + " up"));
		helper.succeed();
	}
}
