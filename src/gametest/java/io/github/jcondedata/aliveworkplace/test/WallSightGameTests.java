package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * B1382: archers on a wall segment's walk must see out. The Palisade's walk stood its archers' eyes (1.62 over the
 * walk) behind the stakes' spruce fence points, whose collider (a block and a half tall) made a rail from 4 to 5.5 in
 * front of them: every walk station saw none of the ground outside ({@link BattleStations#view}) and a raider in plain
 * view was out of the archer's sight ({@code hasLineOfSight}, what GuardCombat asks before it shoots).
 *
 * <p>Each segment is stood in the world as its blueprint has it (the outside to the south), and every station of its
 * walk is asked what it sees of the ground straight out in front of it (the breach put at the wall's outer foot, in
 * the station's column), the way the siege's watch asks; and a villager on a walk station is asked whether a raider
 * eight blocks out is in sight. The stone walls are measured too: their crenels see out, their merlons by design not.
 */
public class WallSightGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos AT = new BlockPos(3, 2, 2);
	/** Of the 15 spots {@link BattleStations#VIEW_NEAR} to {@link BattleStations#VIEW_FAR} out, a walk station sees at least this many. */
	private static final int SEES = 8;

	/** Stands {@code build} at {@link #AT} and returns its blueprint. */
	private static Blueprint stand(GameTestHelper helper, StarterBlueprints.Entry build) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(AT);
		level.getStructureManager().get(build.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		return BlueprintLibrary.get(level, build.id()).orElseThrow();
	}

	/** The walk station of {@code local} (the block stood on, in the blueprint) in the world: where one stands. */
	private static BlockPos station(GameTestHelper helper, BlockPos local) {
		return helper.absolutePos(AT).offset(local).above();
	}

	/** What the station sees of the ground straight out (south) from the wall's outer foot in its column. */
	private static int sees(GameTestHelper helper, Blueprint blueprint, BlockPos s) {
		BlockPos origin = helper.absolutePos(AT);
		ThreatData.Siege siege = new ThreatData.Siege(origin, 0, 0);
		siege.breach = new BlockPos(s.getX(), origin.getY(), origin.getZ() + blueprint.size().getZ() - 1);
		siege.out = Direction.SOUTH;
		return BattleStations.view(helper.getLevel(), siege, s, false);
	}

	/** Every station of {@code build} with its view, as "local=seen". */
	private static List<String> views(GameTestHelper helper, Blueprint blueprint) {
		List<String> out = new ArrayList<>();
		for (BlockPos local : BattleStations.spots(blueprint)) {
			out.add(local.toShortString() + "=" + sees(helper, blueprint, station(helper, local)));
		}
		return out;
	}

	private static void walkSeesOut(GameTestHelper helper, StarterBlueprints.Entry build, int walkZ) {
		Blueprint blueprint = stand(helper, build);
		List<BlockPos> spots = BattleStations.spots(blueprint);
		List<BlockPos> walk = spots.stream().filter(p -> p.getZ() == walkZ).toList();
		helper.assertTrue(walk.size() >= 4, build.id() + ": only " + walk.size() + " stations on the walk at z " + walkZ + ": " + spots);
		for (BlockPos local : walk) {
			int seen = sees(helper, blueprint, station(helper, local));
			helper.assertTrue(seen >= SEES, build.id() + ": the walk station " + local.toShortString() + " sees " + seen + " of 15 spots of the ground in front "
				+ "(at least " + SEES + "); every station: " + views(helper, blueprint));
		}
		// An archer on the walk's middle station has a raider eight blocks out in sight (GuardCombat shoots only then).
		BlockPos middle = walk.get(walk.size() / 2);
		BlockPos s = station(helper, middle);
		ServerLevel level = helper.getLevel();
		Villager archer = EntityType.VILLAGER.create(level);
		archer.moveTo(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, 0f, 0f);
		archer.setNoAi(true);
		level.addFreshEntity(archer);
		Mob foe = EntityType.HUSK.create(level);
		foe.moveTo(s.getX() + 0.5, helper.absolutePos(AT).getY(), helper.absolutePos(AT).getZ() + blueprint.size().getZ() - 1 + 8.5, 0f, 0f);
		foe.setNoAi(true);
		level.addFreshEntity(foe);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(archer.hasLineOfSight(foe), build.id() + ": from the walk station " + middle.toShortString() + " the raider eight blocks out is out of the archer's sight"
				+ " (he stands at " + helper.relativeVec(archer.position()) + ", the raider at " + helper.relativeVec(foe.position()) + ")");
			archer.discard();
			foe.discard();
			helper.succeed();
		});
	}

	/** The Palisade's walk (z 2): every station sees the ground in front, and a raider out there is in an archer's sight. */
	//$ gametest_ticks_batch AREA '100' '"wallSightPalisade"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "wallSightPalisade")
	public void palisadeWalkSeesOut(GameTestHelper helper) {
		walkSeesOut(helper, StarterBlueprints.PALISADE, 2);
	}

	/** Palisade II keeps the walk's view (its front row, z 2; the fighting step behind it is the reserve's). */
	//$ gametest_ticks_batch AREA '100' '"wallSightPalisade2"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "wallSightPalisade2")
	public void palisadeIIWalkSeesOut(GameTestHelper helper) {
		walkSeesOut(helper, StarterBlueprints.PALISADE_2, 2);
	}

	/** The Stone Wall: its crenels (the walk's stations between merlons) see the ground in front; the merlons cover the rest. */
	//$ gametest_ticks_batch AREA '100' '"wallSightStone"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "wallSightStone")
	public void stoneWallCrenelsSeeOut(GameTestHelper helper) {
		crenelsSeeOut(helper, StarterBlueprints.STONE_WALL);
	}

	/** Stone Wall II, its hoarding included. */
	//$ gametest_ticks_batch AREA '100' '"wallSightStone2"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "wallSightStone2")
	public void stoneWallIICrenelsSeeOut(GameTestHelper helper) {
		crenelsSeeOut(helper, StarterBlueprints.STONE_WALL_2);
	}

	private static void crenelsSeeOut(GameTestHelper helper, StarterBlueprints.Entry build) {
		Blueprint blueprint = stand(helper, build);
		int best = 0;
		int seeing = 0;
		for (BlockPos local : BattleStations.spots(blueprint)) {
			int seen = sees(helper, blueprint, station(helper, local));
			best = Math.max(best, seen);
			seeing += seen > 0 ? 1 : 0;
		}
		helper.assertTrue(best >= SEES / 2 && seeing >= 3, build.id() + ": " + seeing + " stations see any ground in front, the best " + best + " of 15; every station: "
			+ views(helper, blueprint));
		helper.succeed();
	}
}
