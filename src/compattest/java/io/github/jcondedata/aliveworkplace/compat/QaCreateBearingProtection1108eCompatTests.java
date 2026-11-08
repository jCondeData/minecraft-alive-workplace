package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.EDGE;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.done;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.hall;

import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * QA (lane-e-1108-0413, Create and the pack): a Mechanical Bearing outside a protected village swinging a block into it,
 * the contraption B993's notes left "not tested". village-hall.md: with protection on only the owner, friends and
 * operators may place blocks in the hall's area, and (B745) machines outside stop at its edge. The village is the one of
 * {@link QaCreateIntoProtectedVillage1107eCompatTests} (x = 11, {@code EDGE}, is its last column; x = 12 is outside).
 *
 * <p>The bearing stands at x = 13 facing up, a Creative Motor under it; on top an arm of slime, slime and an oak plank
 * reaching east (x = 13, 14, 15). Half a turn puts the plank at x = 11, inside. When the arm is half way round the test
 * stops the bearing (Create's own {@code disassemble}, what stopping its rotation does in the default "place when
 * stopped" mode), which sets the blocks down where they are. The open village is the control (here); with protection on
 * the plank is set down inside all the same: B1032, in the unregistered QaCreateBearingProtection1108eBugCompatTests on
 * tests/create-bearing-1108e.
 */
public class QaCreateBearingProtection1108eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	static final BlockPos BEARING = new BlockPos(EDGE.getX() + 2, EDGE.getY(), EDGE.getZ());
	/** Where the plank lands after half a turn: the village's last column, one up. */
	static final BlockPos LANDING = EDGE.above();

	/** The bearing, its motor and the arm. */
	static void bearing(GameTestHelper helper) {
		helper.setBlock(BEARING.below(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "up"));
		helper.setBlock(BEARING, MechanicCompatTests.with(MechanicCompatTests.create("mechanical_bearing").defaultBlockState(), "facing", "up"));
		helper.setBlock(BEARING.above(), Blocks.SLIME_BLOCK);
		helper.setBlock(BEARING.above().east(), Blocks.SLIME_BLOCK);
		helper.setBlock(BEARING.above().east(2), Blocks.OAK_PLANKS);
	}

	private static float angle(MechanicalBearingBlockEntity bearing) {
		return ((bearing.getInterpolatedAngle(1f) % 360) + 360) % 360;
	}

	/**
	 * Runs the bearing until the arm is half way round (170 to 190 degrees), stops it there, and after 200 ticks more calls
	 * {@code check} with the angle it stopped at (NaN if it never got there).
	 */
	static void halfTurn(GameTestHelper helper, VillageHallBlockEntity hall, java.util.function.Consumer<Float> check) {
		bearing(helper);
		float[] stoppedAt = {Float.NaN};
		boolean[] ran = {false};
		helper.onEachTick(() -> {
			if (!Float.isNaN(stoppedAt[0])) {
				return;
			}
			BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(BEARING));
			if (be instanceof MechanicalBearingBlockEntity bearing && bearing.isRunning()) {
				ran[0] = true;
				float a = angle(bearing);
				if (a >= 170 && a <= 190) {
					stoppedAt[0] = a;
					bearing.disassemble();
				}
			}
		});
		helper.runAfterDelay(400, () -> {
			done(helper, hall);
			BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(BEARING));
			helper.assertTrue(ran[0], "the bearing never turned its arm: " + (be instanceof MechanicalBearingBlockEntity b ? b.getLastAssemblyException() : be));
			helper.assertTrue(!Float.isNaN(stoppedAt[0]), "the arm never got half way round");
			check.accept(stoppedAt[0]);
			helper.succeed();
		});
	}

	static String around(GameTestHelper helper) {
		StringBuilder out = new StringBuilder();
		for (int x = LANDING.getX() - 1; x <= BEARING.getX() + 2; x++) {
			BlockPos p = new BlockPos(x, LANDING.getY(), LANDING.getZ());
			out.append(x).append(": ").append(helper.getBlockState(p).getBlock().getDescriptionId()).append("; ");
		}
		return out.toString();
	}

	/** The control: with the village open, half a turn sets the plank down in the village's last column. */
	//$ gametest_ticks_batch AREA '600' '"qa_cbear_open"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_cbear_open")
	public void aMechanicalBearingOutsideAnOpenVillageSwingsAPlankIn(GameTestHelper helper) {
		VillageHallBlockEntity hall = hall(helper, false);
		halfTurn(helper, hall, angle -> helper.assertTrue(helper.getBlockState(LANDING).is(Blocks.OAK_PLANKS),
			"the control bearing (stopped at " + angle + " degrees) didn't set the plank down at the open village's edge: " + around(helper)));
	}
}
