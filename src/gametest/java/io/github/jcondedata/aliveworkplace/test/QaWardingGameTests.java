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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA for B20 (written from its spec, not its code): "every block in a warded village's box is spared, blocks past its
 * edge still break." The village is the square box round its hall ({@link VillageHalls#area}); here RADIUS is shrunk to
 * 8 so the whole box fits in a test area.
 */
public class QaWardingGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	private static void ward(ServerLevel level, GameTestHelper helper, BlockPos hall, int warding) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(hall));
		entity.setResearch(new Research.State(Map.of("warding", warding), Optional.empty(), 0, false));
	}

	private static void shrink(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 8;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
	}

	private static boolean gone(GameTestHelper helper, BlockPos pos) {
		return !helper.getLevel().getBlockState(helper.absolutePos(pos)).is(Blocks.DIRT);
	}

	/** The opposite corner (−x, −z) from the builder's own test: a real primed TNT there spares the village's last row. */
	//$ gametest_ticks_batch AREA '200' '"qa_warding_low_corner"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_warding_low_corner")
	public void qaPrimedTntInTheLowCornerSparesTheVillage(GameTestHelper helper) {
		shrink(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = new BlockPos(14, 2, 14);
		helper.setBlock(hall, ModBlocks.VILLAGE_HALL);
		// −8 and −7 are inside the box (it reaches 8 each way); −9, −10 are past its edge.
		for (int dx = -10; dx <= -7; dx++) {
			helper.setBlock(hall.offset(dx, 0, -8), Blocks.DIRT);
		}
		helper.runAfterDelay(2, () -> {
			ward(level, helper, hall, 1);
			PrimedTnt tnt = EntityType.TNT.create(level);
			Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(hall.offset(-8, 1, -8)));
			tnt.moveTo(at.x, at.y, at.z);
			tnt.setFuse(1);
			level.addFreshEntity(tnt);
		});
		helper.runAfterDelay(10, () -> {
			for (int dx = -8; dx <= -7; dx++) {
				helper.assertBlockPresent(Blocks.DIRT, hall.offset(dx, 0, -8));
			}
			helper.assertTrue(gone(helper, hall.offset(-9, 0, -8)) || gone(helper, hall.offset(-10, 0, -8)),
				"the TNT broke nothing past the village's −x edge");
			helper.succeed();
		});
	}

	/**
	 * Two villages overlap; the blast's nearest hall is the unwarded one. The warded village's blocks still stay (they are
	 * in a warded village's box); blocks only in the unwarded village go.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa_warding_two_halls"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_warding_two_halls")
	public void qaANearerUnwardedHallDoesntUndoWarding(GameTestHelper helper) {
		shrink(helper);
		ServerLevel level = helper.getLevel();
		BlockPos warded = new BlockPos(8, 2, 10);
		BlockPos plain = new BlockPos(21, 2, 10);
		helper.setBlock(warded, ModBlocks.VILLAGE_HALL);
		helper.setBlock(plain, ModBlocks.VILLAGE_HALL);
		// The warded box reaches x = 16; x 14..16 are in both villages, 17..18 only in the plain one.
		for (int x = 14; x <= 18; x++) {
			helper.setBlock(new BlockPos(x, 2, 12), Blocks.DIRT);
		}
		helper.runAfterDelay(2, () -> {
			ward(level, helper, warded, 1);
			ward(level, helper, plain, 0);
			// x = 16.5: 8.5 from the warded hall, 4.5 from the plain one.
			Vec3 at = helper.absoluteVec(new Vec3(16.5, 3.5, 12.5));
			level.explode(null, at.x, at.y, at.z, 3f, Level.ExplosionInteraction.TNT);
			for (int x = 14; x <= 16; x++) {
				helper.assertBlockPresent(Blocks.DIRT, new BlockPos(x, 2, 12));
			}
			helper.assertTrue(gone(helper, new BlockPos(17, 2, 12)) || gone(helper, new BlockPos(18, 2, 12)),
				"the blast broke nothing in the unwarded village");
			helper.succeed();
		});
	}

	/** No Warding researched (level 0): nothing is spared, also in the middle of the village. */
	//$ gametest_ticks_batch AREA '100' '"qa_warding_none"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_warding_none")
	public void qaWithoutWardingTheVillageBreaks(GameTestHelper helper) {
		shrink(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = new BlockPos(10, 2, 10);
		helper.setBlock(hall, ModBlocks.VILLAGE_HALL);
		for (int dx = 3; dx <= 5; dx++) {
			helper.setBlock(hall.offset(dx, 0, 0), Blocks.DIRT);
		}
		helper.runAfterDelay(2, () -> {
			ward(level, helper, hall, 0);
			Vec3 at = helper.absoluteVec(Vec3.atCenterOf(hall.offset(4, 1, 0)));
			level.explode(null, at.x, at.y, at.z, 3f, Level.ExplosionInteraction.TNT);
			helper.assertTrue(gone(helper, hall.offset(4, 0, 0)), "an unwarded village's block survived a blast on top of it");
			helper.succeed();
		});
	}

	/** The warded hall is broken: its village isn't warded any more, so a blast there breaks blocks again. */
	//$ gametest_ticks_batch AREA '100' '"qa_warding_hall_broken"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_warding_hall_broken")
	public void qaABrokenHallWardsNothing(GameTestHelper helper) {
		shrink(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = new BlockPos(10, 2, 10);
		helper.setBlock(hall, ModBlocks.VILLAGE_HALL);
		for (int dx = 3; dx <= 5; dx++) {
			helper.setBlock(hall.offset(dx, 0, 0), Blocks.DIRT);
		}
		helper.runAfterDelay(2, () -> {
			ward(level, helper, hall, 1);
			helper.destroyBlock(hall);
		});
		helper.runAfterDelay(4, () -> {
			Vec3 at = helper.absoluteVec(Vec3.atCenterOf(hall.offset(4, 1, 0)));
			level.explode(null, at.x, at.y, at.z, 3f, Level.ExplosionInteraction.TNT);
			helper.assertTrue(gone(helper, hall.offset(4, 0, 0)), "a broken hall still wards its village");
			helper.succeed();
		});
	}
}
