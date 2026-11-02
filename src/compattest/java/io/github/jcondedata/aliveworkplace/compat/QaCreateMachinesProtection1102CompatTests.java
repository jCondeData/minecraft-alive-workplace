package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * QA (lane E, Create and the pack, 2026-11-02): Create's machines and a protected village (guide village-protection.md:
 * "only the owner, their friends and operators may break or place blocks", and B745: "a piston outside the village
 * can't push blocks into it, pull them out of it"). A stranger stands outside a protected village, sets a Mechanical
 * Drill down one step past its edge facing in and turns it with a Creative Motor (every click outside, so all allowed):
 * the drill must not break the village's edge block, as the vanilla piston may not move it. The control (the same drill
 * with the hall unprotected) proves the drill would break it, and a drill inside the village still works.
 *
 * <p>The hall is at (3, 2, 8) with the reach shrunk to 4, so on the hall's row x = 7 is the village's last column and
 * x = 8 is outside (VillageProtection counts columns within the reach's circle).
 */
public class QaCreateMachinesProtection1102CompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos HALL = new BlockPos(3, 2, 8);
	private static final BlockPos EDGE = new BlockPos(7, 2, 8);

	/** A hall owned by someone else (not a player in this test), protected or not, the reach shrunk to 4 (put back after). */
	private static VillageHallBlockEntity hall(GameTestHelper helper, boolean protect) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		int radius = VillageHalls.RADIUS;
		boolean enabled = VillageProtection.ENABLED;
		VillageHalls.RADIUS = 4;
		VillageProtection.ENABLED = true;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(HALL);
		hall.setOwner(new UUID(0x1102L, 0xE1L), "Owner");
		hall.setProtected(protect);
		Runnable undo = () -> {
			VillageHalls.RADIUS = radius;
			VillageProtection.ENABLED = enabled;
			if (helper.getBlockEntity(HALL) instanceof VillageHallBlockEntity h) {
				h.setProtected(false);
			}
		};
		cleanups.put(helper, undo);
		return hall;
	}

	private static final java.util.Map<GameTestHelper, Runnable> cleanups = new java.util.concurrent.ConcurrentHashMap<>();

	private static void undo(GameTestHelper helper) {
		Runnable r = cleanups.remove(helper);
		if (r != null) {
			r.run();
		}
	}

	/** A drill at {@code drill} facing west, turned by a creative motor just east of it (facing west into the drill's back). */
	private static void westDrill(GameTestHelper helper, BlockPos drill) {
		helper.setBlock(drill.east(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "west"));
		helper.setBlock(drill, MechanicCompatTests.with(MechanicCompatTests.create("mechanical_drill").defaultBlockState(), "facing", "west"));
	}

	private static float speed(GameTestHelper helper, BlockPos pos) {
		BlockEntity be = helper.getBlockEntity(pos);
		return be == null ? 0 : Nbt.getFloat(be.saveWithoutMetadata(helper.getLevel().registryAccess()), "Speed");
	}

	/** The control: the hall unprotected, the stranger's drill outside breaks the village's edge block (the setup works). */
	//$ gametest_ticks_batch AREA '400' '"qa_create_drill_unprotected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_drill_unprotected")
	public void aDrillOutsideAnUnprotectedVillageBreaksItsEdge(GameTestHelper helper) {
		hall(helper, false);
		helper.setBlock(EDGE, Blocks.OAK_PLANKS);
		westDrill(helper, EDGE.east());
		helper.runAfterDelay(300, () -> {
			boolean broken = helper.getBlockState(EDGE).isAir();
			float turning = speed(helper, EDGE.east());
			undo(helper);
			helper.assertTrue(broken, "the drill didn't break the unprotected edge block in 300 ticks (drill speed " + turning + ")");
			helper.succeed();
		});
	}

	/** A stranger's drill one step outside a protected village, facing in, leaves the village's edge block standing. */
	//$ gametest_ticks_batch AREA '400' '"qa_create_drill_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_drill_protected")
	public void aDrillOutsideAProtectedVillageCantBreakItsEdge(GameTestHelper helper) {
		hall(helper, true);
		helper.setBlock(EDGE, Blocks.OAK_PLANKS);
		BlockPos drill = EDGE.east();
		westDrill(helper, drill);
		helper.runAfterDelay(300, () -> {
			float turning = speed(helper, drill);
			boolean kept = helper.getBlockState(EDGE).is(Blocks.OAK_PLANKS);
			undo(helper);
			helper.assertTrue(turning != 0, "the drill never turned, so the test proves nothing");
			helper.assertTrue(kept, "a stranger's Mechanical Drill outside the protected village broke its edge block at "
				+ helper.absolutePos(EDGE) + " (the drill at " + helper.absolutePos(drill) + ", speed " + turning + ")");
			helper.succeed();
		});
	}

	/** The owner's drill inside the protected village still breaks blocks inside it. */
	//$ gametest_ticks_batch AREA '400' '"qa_create_drill_inside"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_drill_inside")
	public void aDrillInsideAProtectedVillageStillWorks(GameTestHelper helper) {
		hall(helper, true);
		BlockPos target = new BlockPos(5, 2, 8);
		helper.setBlock(target, Blocks.OAK_PLANKS);
		westDrill(helper, target.east());
		helper.runAfterDelay(300, () -> {
			boolean broken = helper.getBlockState(target).isAir();
			undo(helper);
			helper.assertTrue(broken, "the owner's drill inside the protected village didn't break the block in front of it in 300 ticks");
			helper.succeed();
		});
	}
}
