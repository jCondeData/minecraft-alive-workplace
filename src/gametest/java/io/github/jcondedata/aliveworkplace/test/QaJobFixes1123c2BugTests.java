package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaJobFixes1123c2GameTests.GAP;
import static io.github.jcondedata.aliveworkplace.test.QaJobFixes1123c2GameTests.INSIDE;
import static io.github.jcondedata.aliveworkplace.test.QaJobFixes1123c2GameTests.SMOKER;
import static io.github.jcondedata.aliveworkplace.test.QaJobFixes1123c2GameTests.door;
import static io.github.jcondedata.aliveworkplace.test.QaJobFixes1123c2GameTests.lecternRoom;
import static io.github.jcondedata.aliveworkplace.test.QaJobFixes1123c2GameTests.pen;
import static io.github.jcondedata.aliveworkplace.test.QaJobFixes1123c2GameTests.penned;
import static io.github.jcondedata.aliveworkplace.test.QaJobFixes1123c2GameTests.sees;
import static io.github.jcondedata.aliveworkplace.test.QaJobFixes1123c2GameTests.still;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;

/**
 * QA lane C (lane-c-1123-1255): where B1523's fix (WorkerLimits.canTake) still lets a barrel the villager can't walk to
 * hide the smoker: its walk check passes every door and gate, but a villager opens only wooden doors, never an iron door
 * (without redstone) nor a fence gate. Not registered: these fail on main until fixed (register them with the fix).
 */
public class QaJobFixes1123c2BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = QaFisherSmoker1123cGameTests.HUGE;

	/** A closed iron door (no button, no plate): no villager opens it, so the barrel outside isn't his and doesn't hide the smoker. */
	//$ gametest_ticks_batch HUGE '200' '"qaJobFixBug1123c2IronDoor"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaJobFixBug1123c2IronDoor")
	public void aBarrelBehindAClosedIronDoorDoesntHideTheSmoker(GameTestHelper helper) {
		penned(helper);
		pen(helper, Blocks.GLASS, 3, true);
		door(helper, GAP, Blocks.IRON_DOOR, false);
		sees(helper, still(helper, INSIDE), SMOKER, true, "the only way to the barrel a closed iron door");
	}

	/** A closed fence gate in a pen of oak fences: villagers don't open gates (only doors), so the barrel outside doesn't hide the smoker. */
	//$ gametest_ticks_batch HUGE '200' '"qaJobFixBug1123c2ClosedGate"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaJobFixBug1123c2ClosedGate")
	public void aBarrelBehindAClosedGateDoesntHideTheSmoker(GameTestHelper helper) {
		penned(helper);
		pen(helper, Blocks.OAK_FENCE, 1, false);
		helper.setBlock(GAP, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.EAST).setValue(FenceGateBlock.OPEN, false));
		sees(helper, still(helper, INSIDE), SMOKER, true, "the only way to the barrel a closed fence gate");
	}

	/** B1491 behind an iron door: nobody takes that lectern, so it doesn't hide the barrel. */
	//$ gametest_ticks_batch HUGE '200' '"qaJobFixBug1123c2LecternIron"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaJobFixBug1123c2LecternIron")
	public void aLecternBehindAnIronDoorDoesntHideTheBarrel(GameTestHelper helper) {
		lecternRoom(helper, Blocks.IRON_DOOR);
		sees(helper, still(helper, new BlockPos(10, 2, 9)), new BlockPos(14, 2, 9), true, "the lectern's room shut by an iron door");
	}

	/** Penned in behind a closed iron door with the smoker, the barrel outside: within 2400 ticks he is the smoker's Butcher. */
	//$ gametest_ticks_batch HUGE '2400' '"qaJobFixBug1123c2IronPen"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaJobFixBug1123c2IronPen")
	public void aVillagerPennedBehindAnIronDoorTakesTheSmoker(GameTestHelper helper) {
		penned(helper);
		pen(helper, Blocks.GLASS, 3, true);
		door(helper, GAP, Blocks.IRON_DOOR, false);
		Villager v = QaFisherSmoker1123cGameTests.jobless(helper, INSIDE);
		QaFisherSmoker1123cGameTests.becomes(helper, v, helper.absolutePos(SMOKER), VillagerProfession.BUTCHER, VillagerProfession.FISHERMAN,
			"penned behind a closed iron door with the smoker, the barrel outside");
	}

	/**
	 * B1491's own case behind an iron door: a free lectern in a glass room shut by a closed iron door, a free barrel 5 south
	 * of it outside, a jobless villager beside the barrel, the village with room for a fisherman. No villager can take that
	 * lectern, so it must not keep the barrel from him: within 1200 ticks he is the barrel's Fisherman.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobFixBug1123c2LecternIronJob"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobFixBug1123c2LecternIronJob")
	public void aLecternBehindAnIronDoorDoesntKeepTheFishermanAway(GameTestHelper helper) {
		lecternRoom(helper, Blocks.IRON_DOOR);
		Villager v = QaFisherSmoker1123cGameTests.jobless(helper, new BlockPos(14, 2, 8));
		QaFisherSmoker1123cGameTests.becomes(helper, v, helper.absolutePos(new BlockPos(14, 2, 9)), VillagerProfession.FISHERMAN, VillagerProfession.LIBRARIAN,
			"the lectern's room shut by an iron door, he beside the barrel outside");
	}

	/** Penned in a fence pen with a closed gate, with the smoker, the barrel outside: within 2400 ticks he is the Butcher. */
	//$ gametest_ticks_batch HUGE '2400' '"qaJobFixBug1123c2GatePen"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaJobFixBug1123c2GatePen")
	public void aVillagerPennedBehindAClosedGateTakesTheSmoker(GameTestHelper helper) {
		penned(helper);
		pen(helper, Blocks.OAK_FENCE, 1, false);
		helper.setBlock(GAP, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.EAST).setValue(FenceGateBlock.OPEN, false));
		Villager v = QaFisherSmoker1123cGameTests.jobless(helper, INSIDE);
		QaFisherSmoker1123cGameTests.becomes(helper, v, helper.absolutePos(SMOKER), VillagerProfession.BUTCHER, VillagerProfession.FISHERMAN,
			"penned behind a closed fence gate with the smoker, the barrel outside");
	}
}
