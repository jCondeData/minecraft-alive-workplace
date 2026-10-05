package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Tester's checks for bug B69 (from its spec, not the fix): a villager walking along the front wall of a worldgen
 * village house to its front door gets there, in every style, starting from either front corner. Before the fix,
 * head-height trapdoor flower boxes beside the front steps caught a walker for good.
 */
public class QaB69FrontWalkGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	/** Where the house's z=0 corner goes, from the area's lowest corner in world axes: open floor in front of it. */
	private static final BlockPos ORIGIN = new BlockPos(8, 2, 8);

	//$ gametest_ticks_batch AREA '600' '"qaB69Plains"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaB69Plains")
	public void qaB69PlainsGuardHouseFrontWalk(GameTestHelper helper) {
		frontWalk(helper, "plains");
	}

	//$ gametest_ticks_batch AREA '600' '"qaB69Desert"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaB69Desert")
	public void qaB69DesertGuardHouseFrontWalk(GameTestHelper helper) {
		frontWalk(helper, "desert");
	}

	//$ gametest_ticks_batch AREA '600' '"qaB69Savanna"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaB69Savanna")
	public void qaB69SavannaGuardHouseFrontWalk(GameTestHelper helper) {
		frontWalk(helper, "savanna");
	}

	//$ gametest_ticks_batch AREA '600' '"qaB69Snowy"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaB69Snowy")
	public void qaB69SnowyGuardHouseFrontWalk(GameTestHelper helper) {
		frontWalk(helper, "snowy");
	}

	//$ gametest_ticks_batch AREA '600' '"qaB69Taiga"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaB69Taiga")
	public void qaB69TaigaGuardHouseFrontWalk(GameTestHelper helper) {
		frontWalk(helper, "taiga");
	}

	private static void frontWalk(GameTestHelper helper, String style) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		level.setDayTime(6000);
		StructureTemplate template = level.getStructureManager().get(AliveWorkplace.id("village/" + style + "_guard_house"))
			.orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("no " + style + " guard house template"));
		// Everything in world positions from the area's corner (helper.relativePos doesn't undo absolutePos here).
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		BlockPos origin = base.offset(ORIGIN);
		template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1), 2);
		var size = template.getSize();

		// The front door: the lowest-z lower door half in the house.
		BlockPos door = null;
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
					&& (door == null || p.getZ() < door.getZ())) {
				door = p.immutable();
			}
		}
		helper.assertTrue(door != null, style + " guard house has no door");
		BlockPos goal = door.north(); // in front of the door (the front is the z=0 side)
		BlockPos doorAt = door;

		// The walkers: one at each end of the walk row along the front wall (template z = 1, on the ground at y = 0), the
		// row the flower boxes stood over; a step further out if that corner is built on.
		Villager left = helper.spawn(EntityType.VILLAGER, walkStart(level, origin, 0).subtract(base));
		Villager right = helper.spawn(EntityType.VILLAGER, walkStart(level, origin, size.getX() - 1).subtract(base));
		Villager[] walkers = {left, right};
		double[] closest = {Double.MAX_VALUE, Double.MAX_VALUE};
		helper.onEachTick(() -> {
			for (int i = 0; i < 2; i++) {
				Villager v = walkers[i];
				if (v.blockPosition().distSqr(goal) > 2) {
					v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(goal), 0.6f, 0));
				}
				closest[i] = Math.min(closest[i], Math.sqrt(v.blockPosition().distSqr(goal)));
			}
		});
		helper.succeedWhen(() -> {
			for (int i = 0; i < 2; i++) {
				Villager v = walkers[i];
				helper.assertTrue(v.isAlive(), style + ": a walker died");
				helper.assertTrue(v.blockPosition().distSqr(goal) <= 2, style + ": the " + (i == 0 ? "left" : "right")
					+ " walker didn't reach the front door at " + doorAt.subtract(base).toShortString() + " (closest "
					+ String.format("%.1f", closest[i]) + " blocks, now at " + v.blockPosition().subtract(base).toShortString() + "; house size "
					+ size.toShortString() + " at " + ORIGIN.toShortString() + ")");
			}
		});
	}

	/** The walk row's end at template x, or the nearest free spot out from it (feet and head both clear). */
	private static BlockPos walkStart(ServerLevel level, BlockPos origin, int x) {
		for (int out = 0; out < 4; out++) {
			BlockPos p = origin.offset(x + (x == 0 ? -out : out), 0, 1);
			if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()) {
				return p;
			}
		}
		return origin.offset(x == 0 ? -1 : x + 1, 0, -1);
	}
}
