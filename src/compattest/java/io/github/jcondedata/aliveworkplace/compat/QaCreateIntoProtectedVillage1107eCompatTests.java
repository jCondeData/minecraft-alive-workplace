package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * QA (lane-e-1107-0413, Create and the pack): Create's machines at the edge of a protected village.
 *
 * <p>village-hall.md: with protection on, "only the owner, their friends and operators may break or place blocks" in
 * the hall's area, and (B745) a piston outside the village "stops at its edge, as land-claim mods do": it can't push a
 * block in. Create's Mechanical Piston moves blocks without going through vanilla's piston code, and its Mechanical
 * Drill breaks blocks with no player at all, so both meet the protection only with Create installed.
 *
 * <p>The hall is at {@link #HALL} with the village shrunk to {@link #REACH} columns, so on the hall's row (z = 15) x = 11
 * is the village's last column and x = 12 is outside. Each case has a control: the same machine with the village open
 * (protection off) does push or break, and a Mechanical Piston inside the village still works with protection on.
 */
public class QaCreateIntoProtectedVillage1107eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	static final BlockPos HALL = new BlockPos(3, 2, 15);
	static final int REACH = 8;
	private static final UUID FRANK = UUID.fromString("5b1d2c3e-4f50-4a6b-8c7d-9e0f1a2b3c4d");

	/** Frank's hall at {@link #HALL}, its village {@link #REACH} columns wide, protected or not; {@link #done} undoes it. */
	static VillageHallBlockEntity hall(GameTestHelper helper, boolean protect) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		if (VillageHalls.RADIUS != REACH) {
			RADIUS_BEFORE[0] = VillageHalls.RADIUS;
		}
		VillageHalls.RADIUS = REACH;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(HALL);
		hall.setOwner(FRANK, "Frank");
		hall.setProtected(protect);
		return hall;
	}

	/** The village radius before these tests shrank it (every check puts it back first). */
	private static final int[] RADIUS_BEFORE = {VillageHalls.RADIUS};

	/** Puts the village back as it was before the test (radius, protection) before the checks run. */
	static void done(GameTestHelper helper, VillageHallBlockEntity hall) {
		hall.setProtected(false);
		VillageHalls.RADIUS = RADIUS_BEFORE[0];
	}

	/** A Mechanical Piston at {@code piston} facing west with two poles behind it, turned from below. */
	static void piston(GameTestHelper helper, BlockPos piston) {
		helper.setBlock(piston.east(2), MechanicCompatTests.with(MechanicCompatTests.create("piston_extension_pole").defaultBlockState(), "facing", "west"));
		helper.setBlock(piston.east(), MechanicCompatTests.with(MechanicCompatTests.create("piston_extension_pole").defaultBlockState(), "facing", "west"));
		helper.setBlock(piston, MechanicCompatTests.with(MechanicCompatTests.with(MechanicCompatTests.create("mechanical_piston").defaultBlockState(), "facing", "west"),
			"axis_along_first", "true"));
		helper.setBlock(piston.below(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "up"));
	}

	/** A Mechanical Drill at {@code drill} facing west, a Creative Motor behind it. */
	static void drill(GameTestHelper helper, BlockPos drill) {
		helper.setBlock(drill.east(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "west"));
		helper.setBlock(drill, MechanicCompatTests.with(MechanicCompatTests.create("mechanical_drill").defaultBlockState(), "facing", "west"));
	}

	static float speed(GameTestHelper helper, BlockPos pos) {
		BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
		return be == null ? 0 : Nbt.getFloat(be.saveWithoutMetadata(helper.getLevel().registryAccess()), "Speed");
	}

	/** Where along the row {@code from} to 5 blocks west of it {@code block} stands (x in the test's frame), or -1. */
	static int where(GameTestHelper helper, BlockPos from, Block block) {
		for (int i = 0; i <= 5; i++) {
			if (helper.getBlockState(from.west(i)).is(block)) {
				return from.west(i).getX();
			}
		}
		return -1;
	}

	static String row(GameTestHelper helper, BlockPos from) {
		StringBuilder out = new StringBuilder();
		for (int i = 5; i >= -1; i--) {
			BlockPos p = from.west(i);
			out.append(p.getX()).append(": ").append(helper.getBlockState(p).getBlock().getDescriptionId()).append("; ");
		}
		return out.toString();
	}

	// ---------------------------------------------------------------- the Mechanical Piston

	/** The plank starts just outside (x = 13), the piston behind it; two steps would put it at x = 11, inside. */
	static final BlockPos OUTSIDE_PLANK = new BlockPos(13, 2, 15);

	/** The control: with the village open the same piston pushes the plank two steps, into the village. */
	//$ gametest_ticks_batch AREA '400' '"qa_cprot_piston_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_cprot_piston_open")
	public void aMechanicalPistonOutsideAnOpenVillagePushesIn(GameTestHelper helper) {
		VillageHallBlockEntity hall = hall(helper, false);
		helper.setBlock(OUTSIDE_PLANK, Blocks.OAK_PLANKS);
		piston(helper, OUTSIDE_PLANK.east());
		helper.runAfterDelay(300, () -> {
			done(helper, hall);
			helper.assertTrue(speed(helper, OUTSIDE_PLANK.east()) != 0, "the piston never turned");
			helper.assertTrue(where(helper, OUTSIDE_PLANK, Blocks.OAK_PLANKS) == OUTSIDE_PLANK.west(2).getX(),
				"the control plank wasn't pushed two steps into the open village: " + row(helper, OUTSIDE_PLANK));
			helper.succeed();
		});
	}

	/**
	 * The calibration: in the same protected village a vanilla piston at the Mechanical Piston's place (outside, facing
	 * in) can't push the plank in (B745), so the protection is on and the edge is where these tests put it.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa_cprot_vanilla_protected"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_cprot_vanilla_protected")
	public void aVanillaPistonOutsideTheSameProtectedVillageCantPushIn(GameTestHelper helper) {
		VillageHallBlockEntity hall = hall(helper, true);
		BlockPos plank = EDGE.east(); // x = 12, just outside: one push puts it at x = 11, inside
		helper.setBlock(plank, Blocks.OAK_PLANKS);
		helper.setBlock(plank.east(), Blocks.PISTON.defaultBlockState().setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING, net.minecraft.core.Direction.WEST));
		helper.setBlock(plank.east(2), Blocks.REDSTONE_BLOCK);
		helper.runAfterDelay(20, () -> {
			done(helper, hall);
			helper.assertTrue(helper.getBlockState(plank).is(Blocks.OAK_PLANKS) && !helper.getBlockState(EDGE).is(Blocks.OAK_PLANKS),
				"a vanilla piston outside the protected village pushed the plank in (B745): " + row(helper, plank.east()));
			helper.succeed();
		});
	}

	/** A Mechanical Piston inside the protected village still pushes there (inside to inside), as a vanilla one does. */
	//$ gametest_ticks_batch AREA '400' '"qa_cprot_piston_inside"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_cprot_piston_inside")
	public void aMechanicalPistonInsideAProtectedVillageStillPushes(GameTestHelper helper) {
		VillageHallBlockEntity hall = hall(helper, true);
		BlockPos plank = new BlockPos(7, 2, 12); // the village reaches x = 10 on this row; the poles end at x = 10
		helper.setBlock(plank, Blocks.OAK_PLANKS);
		piston(helper, plank.east());
		helper.runAfterDelay(300, () -> {
			done(helper, hall);
			helper.assertTrue(speed(helper, plank.east()) != 0, "the piston never turned");
			helper.assertTrue(where(helper, plank, Blocks.OAK_PLANKS) == plank.west(2).getX(),
				"a Mechanical Piston inside Frank's protected village didn't push the plank two steps: " + row(helper, plank));
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the Mechanical Drill

	/** The village's last column on the hall's row, and the drill just outside it. */
	static final BlockPos EDGE = new BlockPos(11, 2, 15);

	/** The control: with the village open the drill outside breaks the stone at its edge. */
	//$ gametest_ticks_batch AREA '400' '"qa_cprot_drill_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_cprot_drill_open")
	public void aMechanicalDrillOutsideAnOpenVillageBreaksItsEdge(GameTestHelper helper) {
		VillageHallBlockEntity hall = hall(helper, false);
		helper.setBlock(EDGE, Blocks.STONE);
		drill(helper, EDGE.east());
		helper.runAfterDelay(300, () -> {
			done(helper, hall);
			helper.assertTrue(speed(helper, EDGE.east()) != 0, "the drill never turned");
			helper.assertTrue(helper.getBlockState(EDGE).isAir(), "the control drill hasn't broken the stone: " + helper.getBlockState(EDGE));
			helper.succeed();
		});
	}
}
