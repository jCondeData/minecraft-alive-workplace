package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.Gates;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (lane D, 2026-11-03) for the village's gates (guard/Gates, ROADMAP 13.x "the gates of its finished Gatehouses are
 * shut at nightfall and opened again in the morning", 32.4 sieges), written from the spec: the hour the gates shut and
 * open, from both sides and on a later day; a Gatehouse built turned and mirrored (its gates are where the build put
 * them, not where the blueprint has them); a gate a player broke or swapped for a fence; and a Gatehouse outside the
 * hall's village. Before this, the nightly mutation never reached Gates (it mutates changed lines only).
 */
public class QaGates1103dGameTests {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/** A finished Gatehouse placed (and recorded) at {@code at} with the given turn; returns its three gates' spots. */
	private static List<BlockPos> gatehouse(GameTestHelper helper, BlockPos at, Rotation rotation, Mirror mirror) {
		ServerLevel level = helper.getLevel();
		StructureTemplate template = level.getStructureManager().get(StarterBlueprints.GATEHOUSE.id()).orElseThrow();
		BlockPos origin = helper.absolutePos(at);
		template.placeInWorld(level, origin, origin, new StructurePlaceSettings().setRotation(rotation).setMirror(mirror),
			level.getRandom(), 2);
		var placement = new BlueprintData.Placement(level.dimension().location(), origin, rotation, mirror);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(StarterBlueprints.GATEHOUSE.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		List<BlockPos> gates = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(origin.offset(-12, 0, -12), origin.offset(12, 9, 12))) {
			if (level.getBlockState(p).getBlock() instanceof FenceGateBlock) {
				gates.add(p.immutable());
			}
		}
		helper.assertTrue(gates.size() == 3, "the Gatehouse (" + rotation + ", " + mirror + ") has 3 gates: " + gates);
		return gates;
	}

	private static void setUp(GameTestHelper helper, int radius) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		int was = VillageHalls.RADIUS;
		VillageHalls.RADIUS = radius;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = was);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	private static long open(ServerLevel level, List<BlockPos> gates) {
		return gates.stream().filter(p -> level.getBlockState(p).getBlock() instanceof FenceGateBlock
			&& level.getBlockState(p).getValue(FenceGateBlock.OPEN)).count();
	}

	/**
	 * Nightfall and morning from both sides: open at 12,499, shut at 12,500, still shut at 23,499, open at 23,500 (the
	 * wiki's "from dusk until just before dawn"), and the same on day 3 (the day time keeps counting past 24,000).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaGatesHours1103d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaGatesHours1103d")
	public void theGatesShutAtDuskAndOpenJustBeforeDawnEveryDay(GameTestHelper helper) {
		setUp(helper, 20);
		ServerLevel level = helper.getLevel();
		List<BlockPos> gates = gatehouse(helper, new BlockPos(5, 2, 14), Rotation.NONE, Mirror.NONE);
		BlockPos hall = helper.absolutePos(HALL);
		for (long day : new long[] {0, 3}) {
			long base = day * 24000;
			helper.setDayTime((int) (base + 2000));
			Gates.round(level, hall, 1);
			helper.assertTrue(open(level, gates) == 3, "day " + day + ", morning: open " + open(level, gates) + " of 3");
			helper.setDayTime((int) (base + 12499));
			helper.assertTrue(Gates.round(level, hall, 1) == 0 && open(level, gates) == 3, "day " + day + ", 12,499 (still day): open " + open(level, gates));
			helper.setDayTime((int) (base + 12500));
			int shut = Gates.round(level, hall, 1);
			helper.assertTrue(shut == 3 && open(level, gates) == 0, "day " + day + ", 12,500 (dusk): moved " + shut + ", open " + open(level, gates));
			helper.setDayTime((int) (base + 23499));
			helper.assertTrue(Gates.round(level, hall, 1) == 0 && open(level, gates) == 0, "day " + day + ", 23,499 (night): open " + open(level, gates));
			helper.setDayTime((int) (base + 23500));
			int opened = Gates.round(level, hall, 1);
			helper.assertTrue(opened == 3 && open(level, gates) == 3, "day " + day + ", 23,500 (dawn): moved " + opened + ", open " + open(level, gates));
		}
		helper.succeed();
	}

	/** A Gatehouse built turned a quarter and mirrored: its own gates shut at night and open in the morning. */
	//$ gametest_ticks_batch AREA '100' '"qaGatesTurned1103d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaGatesTurned1103d")
	public void aTurnedAndMirroredGatehouseShutsItsOwnGates(GameTestHelper helper) {
		setUp(helper, 20);
		ServerLevel level = helper.getLevel();
		// mirrored front to back and turned a quarter clockwise, the build runs towards -x and -z from its origin
		List<BlockPos> gates = gatehouse(helper, new BlockPos(19, 2, 15), Rotation.CLOCKWISE_90, Mirror.FRONT_BACK);
		BlockPos hall = helper.absolutePos(HALL);
		helper.setDayTime(2000);
		Gates.round(level, hall, 1);
		helper.assertTrue(open(level, gates) == 3, "morning: open " + open(level, gates) + " of 3 at " + gates);
		helper.setDayTime(14000);
		int shut = Gates.round(level, hall, 1);
		helper.assertTrue(shut == 3 && open(level, gates) == 0, "night: moved " + shut + ", still open " + open(level, gates) + " of " + gates);
		helper.succeed();
	}

	/**
	 * A gate a player broke stays broken (the round puts nothing back) and one swapped for a fence stays a fence; the other
	 * gate still shuts. Players can still open a shut gate by hand; the hall's next round that night shuts it again, and
	 * one left open by hand stays open in the morning.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaGatesBroken1103d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaGatesBroken1103d")
	public void aBrokenGateIsLeftAloneAndTheRestStillMove(GameTestHelper helper) {
		setUp(helper, 20);
		ServerLevel level = helper.getLevel();
		List<BlockPos> gates = gatehouse(helper, new BlockPos(5, 2, 14), Rotation.NONE, Mirror.NONE);
		BlockPos hall = helper.absolutePos(HALL);
		helper.setDayTime(2000);
		Gates.round(level, hall, 1);
		level.removeBlock(gates.get(0), false);
		level.setBlock(gates.get(1), Blocks.OAK_FENCE.defaultBlockState(), 3);
		helper.setDayTime(14000);
		int shut = Gates.round(level, hall, 1);
		helper.assertTrue(shut == 1, "night: moved " + shut + " (only the one gate left)");
		helper.assertTrue(level.getBlockState(gates.get(0)).isAir(), "the broken gate came back: " + level.getBlockState(gates.get(0)));
		helper.assertTrue(level.getBlockState(gates.get(1)).is(Blocks.OAK_FENCE), "the fence became " + level.getBlockState(gates.get(1)));
		helper.assertFalse(level.getBlockState(gates.get(2)).getValue(FenceGateBlock.OPEN), "the last gate is still open at night");
		// a player opens it by hand at night: it opens (the gate is a plain gate)
		BlockPos last = gates.get(2);
		level.setBlock(last, level.getBlockState(last).setValue(FenceGateBlock.OPEN, true), 10);
		helper.assertTrue(level.getBlockState(last).getValue(FenceGateBlock.OPEN), "the player couldn't open it");
		// the hall's next round that night shuts it again (the wiki says so); morning opens it
		helper.assertTrue(Gates.round(level, hall, 1) == 1 && !level.getBlockState(last).getValue(FenceGateBlock.OPEN),
			"the next round at night didn't shut the gate the player opened");
		level.setBlock(last, level.getBlockState(last).setValue(FenceGateBlock.OPEN, true), 10);
		helper.setDayTime(2000);
		Gates.round(level, hall, 1);
		helper.assertTrue(level.getBlockState(last).getValue(FenceGateBlock.OPEN), "morning shut the gate the player opened");
		helper.succeed();
	}

	/** A Gatehouse beyond the hall's village (its corner and its middle both out of reach) is never shut by that hall. */
	//$ gametest_ticks_batch AREA '100' '"qaGatesOutside1103d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaGatesOutside1103d")
	public void aGatehouseOutsideTheVillageIsLeftAlone(GameTestHelper helper) {
		setUp(helper, 3);
		ServerLevel level = helper.getLevel();
		List<BlockPos> gates = gatehouse(helper, new BlockPos(5, 2, 16), Rotation.NONE, Mirror.NONE);
		BlockPos hall = helper.absolutePos(HALL);
		// opened by hand (the template's gates come shut)
		for (BlockPos g : gates) {
			level.setBlock(g, level.getBlockState(g).setValue(FenceGateBlock.OPEN, true), 10);
		}
		long before = open(level, gates);
		helper.setDayTime(14000);
		int shut = Gates.round(level, hall, 1);
		helper.assertTrue(shut == 0 && open(level, gates) == before, "a hall 3 blocks' reach shut a Gatehouse 5+ blocks away: moved " + shut);
		// the control: the same Gatehouse inside a wider village is shut
		VillageHalls.RADIUS = 20;
		int control = Gates.round(level, hall, 1);
		helper.assertTrue(control > 0 && open(level, gates) == 0, "the control: with the village wider, moved " + control + ", open " + open(level, gates));
		helper.succeed();
	}
}
