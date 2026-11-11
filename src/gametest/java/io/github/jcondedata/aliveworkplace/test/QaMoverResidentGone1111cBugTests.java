package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaMoveHallBed1111cGameTests.AREA;
import static io.github.jcondedata.aliveworkplace.test.QaMoveHallBed1111cGameTests.BED;
import static io.github.jcondedata.aliveworkplace.test.QaMoveHallBed1111cGameTests.MIN;
import static io.github.jcondedata.aliveworkplace.test.QaMoveHallBed1111cGameTests.setup;
import static io.github.jcondedata.aliveworkplace.test.QaMoveHallBed1111cGameTests.tomas;
import static io.github.jcondedata.aliveworkplace.test.QaMoveHallBed1111cGameTests.work;

import io.github.jcondedata.aliveworkplace.move.MoveResidents;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane C, 2026-11-11) of 45.4 and B1154 from the spec: a resident who isn't loaded when his bed goes up is waited
 * for five days (MoveResidents.KEEP_TICKS), as is a far-off owner's claim, which is then let go for someone else. Here a
 * resident noted as the move starts (Tomas, sleeping in the house) never comes back: he dies while the house is packed.
 * Five days after the move (looked ahead with MoveSites.sweep, as MoverResidentsAwayGameTests does for claims) his moved
 * bed should be free again, not held for nobody for good, where no villager can ever claim it.
 */
public class QaMoverResidentGone1111cBugTests implements FabricGameTest {

	private static boolean waitedFor(ServerLevel level, Villager v) {
		return MoveSites.get(level).homecomings().stream().anyMatch(r -> r.id().equals(v.getUUID()));
	}

	/** Tomas dies while his house is packed: after five days the moved bed is let go, as a far-off owner's claim is. */
	//$ gametest_batch AREA '"qaMoverResidentDies"'
	@GameTest(template = AREA, batch = "qaMoverResidentDies")
	public void aResidentWhoDiesMidMoveDoesntHoldTheMovedBedForGood(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = setup(helper, "dies");
		helper.runAfterDelay(2, () -> {
			Villager tomas = tomas(helper, s);
			MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(site.residents().stream().anyMatch(r -> r.id().equals(tomas.getUUID())), "Tomas wasn't noted as a resident");
			tomas.kill(); // a zombie got him while his house was packed
			tomas.remove(Entity.RemovalReason.KILLED);
			work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			BlockPos moved = s.before().at(BED.subtract(MIN), s.to(), Rotation.CLOCKWISE_90);
			helper.assertTrue(level.getBlockState(moved).getBlock() instanceof BedBlock, "the moved bed isn't standing");
			long now = level.getGameTime();
			MoveSites.get(level).sweep(level, now + MoveResidents.KEEP_TICKS / 2);
			MoveSites.get(level).sweep(level, now + MoveResidents.KEEP_TICKS + 1200);
			helper.assertTrue(level.getPoiManager().getFreeTickets(moved) == 1,
				"five days after the move the bed the dead Tomas had is still held for him: " + level.getPoiManager().getFreeTickets(moved)
					+ " places free; still waited for: " + waitedFor(level, tomas));
			helper.succeed();
		});
	}
}
