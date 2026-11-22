package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.guard.GuardCombat;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * B1474: an archer on his battle station with no raider left to shoot stays on it while the siege lasts. His patrol
 * (GuardPatrol) only waited while he fought: once the raider in his sight was dead it picked a point near his Guard Post,
 * and as he made no way toward it (the station holds him), its walker hopped him there, off the wall, 80 ticks later.
 */
public class ArcherStationPatrolB1474GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	/** How long he is watched after his last raider in sight died (a patrol's longest wait, 160, and its walker's 80, twice over). */
	private static final int WATCH = 480;

	/**
	 * A night siege on a village behind Palisade I. One raider stands 12 out, in his sight; a second, farther than
	 * guardRadius from his post and walled in, keeps the siege on and is never his foe. He kills the first, and then has
	 * nobody to fight: he must still be on the walk {@link #WATCH} ticks later.
	 */
	//$ gametest_ticks_batch AREA '1700' '"archerStationPatrolB1474"'
	@GameTest(template = AREA, timeoutTicks = 1700, batch = "archerStationPatrolB1474")
	public void anArcherWithNoRaiderLeftInSightStaysOnHisStation(GameTestHelper helper) {
		QaPalisadeNight1122dGameTests.Village village = QaPalisadeNight1122dGameTests.village(helper, QaPalisadeNight1122dGameTests.NIGHT_RAMS, "ram_gates");
		ServerLevel level = village.level();
		Blueprint blueprint = QaPalisadeNight1122dGameTests.stand(helper, StarterBlueprints.PALISADE);
		BlockPos wall = QaPalisadeNight1122dGameTests.WALL;
		int face = wall.getZ() + blueprint.size().getZ() - 1;
		Villager archer = QaPalisadeNight1122dGameTests.archer(helper);
		Mob seen = QaPalisadeNight1122dGameTests.raider(helper, new BlockPos(wall.getX() + 5, 2, face + 12));
		BlockPos cell = new BlockPos(wall.getX() + 5, 2, 27);
		for (BlockPos p : BlockPos.betweenClosed(cell.offset(-1, 0, -1), cell.offset(1, 2, 1))) {
			if (p.getX() != cell.getX() || p.getZ() != cell.getZ() || p.getY() == cell.getY() + 2) {
				helper.setBlock(p, Blocks.STONE);
			}
		}
		Mob hidden = QaPalisadeNight1122dGameTests.raider(helper, cell);
		VillageRaids.track(level, village.hall(), village.culture(), List.of(seen, hidden));
		helper.assertTrue(Sieges.siege(level, village.hall()).isPresent(), "(setup) a night attack on a village behind a Palisade is no siege");
		long[] on = {-1};
		long[] alone = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (on[0] < 0) {
				if (BattleStations.onStation(archer)) {
					on[0] = tick;
				} else if (tick > 500) {
					helper.fail("the archer isn't on the walk within 500 ticks of the attack: at " + helper.relativeVec(archer.position()));
				}
				return;
			}
			if (!BattleStations.onStation(archer) || archer.getY() < BattleStations.post(archer).orElseThrow().getY() - 0.1) {
				helper.fail("the archer left his station at tick " + tick + " (on it from tick " + on[0] + ", his last raider in sight dead since tick " + alone[0]
					+ "; now at " + helper.relativeVec(archer.position()) + ", stationed " + BattleStations.stationed(archer) + ", fighting " + GuardCombat.isFighting(archer)
					+ ", siege on " + Sieges.siege(level, village.hall()).map(s -> !s.over).orElse(false) + ", doing "
					+ archer.getBrain().getRunningBehaviors().stream().map(b -> b.debugString()).toList() + ")");
			}
			if (alone[0] < 0) {
				if (!seen.isAlive()) {
					alone[0] = tick;
				} else if (tick > 1100) {
					helper.fail("(setup) the archer didn't kill the raider 12 out by tick 1100: in sight " + archer.hasLineOfSight(seen) + ", health " + seen.getHealth());
				}
				return;
			}
			if (tick >= alone[0] + WATCH) {
				helper.assertTrue(hidden.isAlive() && Sieges.siege(level, village.hall()).map(s -> !s.over).orElse(false) && BattleStations.stationed(archer),
					"(setup) the siege didn't last: walled-in raider alive " + hidden.isAlive() + ", stationed " + BattleStations.stationed(archer));
				helper.assertTrue(archer.hasLineOfSight(hidden) || GuardCombat.foe(archer) == null, "(setup) the walled-in raider is his foe");
				helper.succeed();
			}
		});
	}
}
