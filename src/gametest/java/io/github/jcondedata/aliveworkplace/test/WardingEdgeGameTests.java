package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.research.Research;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Warding at the edge of the village (the full check, ROADMAP 21.2; {@code ExplosionMixin} and {@code Warding}). The
 * village is the box {@link VillageHalls#area} round its hall, so a blast in a corner of that box must spare the
 * village's blocks there too, and still break the blocks just outside it.
 */
public class WardingEdgeGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** A creeper in the corner of a warded village: the village's blocks stay, the ones past its edge go. */
	//$ gametest_ticks_batch AREA '100' '"warding_edge"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "warding_edge")
	public void aBlastInTheVillagesCornerSparesTheVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 8;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		BlockPos hall = new BlockPos(2, 2, 2);
		helper.setBlock(hall, ModBlocks.VILLAGE_HALL);
		// Offsets 7 and 8 from the hall are in the village's box (it reaches 8 blocks out); 9 and 10 are past it.
		for (int dx = 7; dx <= 10; dx++) {
			helper.setBlock(hall.offset(dx, 0, 8), Blocks.STONE_BRICKS);
		}
		helper.runAfterDelay(2, () -> {
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(hall));
			entity.setResearch(new Research.State(Map.of("warding", 1), Optional.empty(), 0, false));
			Vec3 at = helper.absoluteVec(Vec3.atCenterOf(hall.offset(8, 1, 8)));
			level.explode(null, at.x, at.y, at.z, 3f, Level.ExplosionInteraction.TNT);
			for (int dx = 7; dx <= 8; dx++) {
				helper.assertBlockPresent(Blocks.STONE_BRICKS, hall.offset(dx, 0, 8));
			}
			boolean brokeOutside = false;
			for (int dx = 9; dx <= 10; dx++) {
				brokeOutside |= !level.getBlockState(helper.absolutePos(hall.offset(dx, 0, 8))).is(Blocks.STONE_BRICKS);
			}
			helper.assertTrue(brokeOutside, "the blast broke nothing past the village's edge");
			helper.succeed();
		});
	}
	/**
	 * A creeper just past the village's edge: the blast's centre is outside every village, but the village's blocks it
	 * reaches stay, and the ones outside go.
	 */
	//$ gametest_ticks_batch AREA '100' '"warding_edge_outside"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "warding_edge_outside")
	public void aBlastJustOutsideTheVillageSparesTheVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 8;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		BlockPos hall = new BlockPos(2, 2, 2);
		helper.setBlock(hall, ModBlocks.VILLAGE_HALL);
		// Offsets 7 and 8 are the village's last two columns; the blast goes off at 10, two past its edge.
		for (int dx = 7; dx <= 11; dx++) {
			helper.setBlock(hall.offset(dx, 0, 4), Blocks.STONE_BRICKS);
		}
		helper.runAfterDelay(2, () -> {
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(hall));
			entity.setResearch(new Research.State(Map.of("warding", 1), Optional.empty(), 0, false));
			Vec3 at = helper.absoluteVec(Vec3.atCenterOf(hall.offset(10, 1, 4)));
			level.explode(null, at.x, at.y, at.z, 3f, Level.ExplosionInteraction.TNT);
			for (int dx = 7; dx <= 8; dx++) {
				helper.assertBlockPresent(Blocks.STONE_BRICKS, hall.offset(dx, 0, 4));
			}
			boolean brokeOutside = false;
			for (int dx = 9; dx <= 11; dx++) {
				brokeOutside |= !level.getBlockState(helper.absolutePos(hall.offset(dx, 0, 4))).is(Blocks.STONE_BRICKS);
			}
			helper.assertTrue(brokeOutside, "the blast broke nothing past the village's edge");
			helper.succeed();
		});
	}
}
