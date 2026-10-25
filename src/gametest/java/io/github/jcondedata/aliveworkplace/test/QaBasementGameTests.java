package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane-b-1025-0038) for ROADMAP 26.15 and 36.7, from their spec ("a basement under it, stairs or ladder down,
 * lit"), not the code: in every build with a basement, the way down comes up through the ground floor (a ladder or
 * the top stair has room above it on y 0 and 1, so it isn't a ladder in a corner that ends under the floor), and no
 * spot on the basement's floor where a monster could stand is left dark (block light 0, where 1.21 spawns monsters).
 * The light is worked out from the blueprint itself the way the game spreads block light (a step costs 1, or what the
 * block in the way takes), with the sun left out and anything not in the blueprint below the ground counted as earth.
 */
public class QaBasementGameTests implements FabricGameTest {

	private static Blueprint blueprint(GameTestHelper helper, StarterBlueprints.Entry entry) {
		return BlueprintLibrary.get(helper.getLevel(), entry.id()).orElseThrow(() -> new GameTestAssertException("missing " + entry.id()));
	}

	/** What stands at {@code pos}: the blueprint's block, air above the ground where it has none, earth below. */
	private static BlockState at(Map<BlockPos, BlockState> blocks, BlockPos pos) {
		BlockState state = blocks.get(pos);
		if (state != null) {
			return state;
		}
		return pos.getY() >= 0 ? Blocks.AIR.defaultBlockState() : Blocks.DIRT.defaultBlockState();
	}

	private static Map<BlockPos, BlockState> blocks(Blueprint blueprint) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		for (Blueprint.Entry e : blueprint.blocks()) {
			out.put(e.pos(), e.state());
		}
		return out;
	}

	/** A player or villager can pass through {@code state}: no full block in the way (a door or trapdoor opens). */
	private static boolean passable(BlockState state) {
		return state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof DoorBlock
			|| !state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	/**
	 * Every basement's way down comes up through the ground floor: some ladder or stair on the basement's top layer
	 * (y = -1) has room to climb out above it, on y = 0 and y = 1. (The Quarry's pit is left out: it is open to the sky.)
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyBasementsWayDownComesUpThroughTheGroundFloor(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (StarterBlueprints.Entry entry : StarterBlueprints.WITH_BASEMENTS) {
			if (entry == StarterBlueprints.QUARRY) {
				continue; // an open pit under the sky (36.12), not a room under a floor: its steps go down from the ground
			}
			Blueprint blueprint = blueprint(helper, entry);
			Map<BlockPos, BlockState> blocks = blocks(blueprint);
			boolean out = false;
			for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
				BlockPos pos = e.getKey();
				BlockState state = e.getValue();
				if (pos.getY() == -1 && (state.is(Blocks.LADDER) || state.is(net.minecraft.tags.BlockTags.STAIRS))
					&& passable(at(blocks, pos.above())) && passable(at(blocks, pos.above(2)))) {
					out = true;
					break;
				}
			}
			if (!out) {
				problems.add(entry.id() + " (no ladder or stair at y -1 with room above it)");
			}
		}
		helper.assertTrue(problems.isEmpty(), "basements with no way up: " + problems);
		helper.succeed();
	}

	/**
	 * No basement leaves a dark spot a monster could stand on: every air block on the basement's layers with a sturdy
	 * floor under it and air over it has block light above 0, from the lanterns and other lights of the build.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noBasementLeavesADarkSpotForMonsters(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (StarterBlueprints.Entry entry : StarterBlueprints.WITH_BASEMENTS) {
			Blueprint blueprint = blueprint(helper, entry);
			Map<BlockPos, BlockState> blocks = blocks(blueprint);
			int sx = blueprint.size().getX();
			int sz = blueprint.size().getZ();
			int top = blueprint.size().getY();
			int bottom = -blueprint.basement();
			// Block light, spread from every light in the build within its box (one block of margin round it).
			Map<BlockPos, Integer> light = new HashMap<>();
			ArrayDeque<BlockPos> queue = new ArrayDeque<>();
			for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
				int emitted = e.getValue().getLightEmission();
				if (emitted > 0) {
					light.put(e.getKey(), emitted);
					queue.add(e.getKey());
				}
			}
			while (!queue.isEmpty()) {
				BlockPos pos = queue.poll();
				int level = light.get(pos);
				for (Direction d : Direction.values()) {
					BlockPos next = pos.relative(d);
					if (next.getX() < -1 || next.getX() > sx || next.getZ() < -1 || next.getZ() > sz || next.getY() < bottom - 1 || next.getY() > top) {
						continue;
					}
					BlockState state = at(blocks, next);
					int cost = Math.max(1, state.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
					int reached = level - cost;
					if (reached > light.getOrDefault(next, 0)) {
						light.put(next, reached);
						queue.add(next);
					}
				}
			}
			List<BlockPos> dark = new ArrayList<>();
			int checked = 0;
			for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
				BlockPos pos = e.getKey();
				if (pos.getY() >= 0 || !e.getValue().isAir() || !at(blocks, pos.above()).isAir()) {
					continue;
				}
				BlockState floor = at(blocks, pos.below());
				if (!floor.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP) || floor.getLightEmission() >= 14) {
					continue;
				}
				checked++;
				if (light.getOrDefault(pos, 0) == 0) {
					dark.add(pos);
				}
			}
			if (checked == 0) {
				problems.add(entry.id() + ": no spot on its basement floor was checked (no air in the blueprint there)");
			} else if (!dark.isEmpty()) {
				problems.add(entry.id() + ": " + dark.size() + " dark spots, e.g. " + dark.subList(0, Math.min(3, dark.size())).stream().map(BlockPos::toShortString).toList());
			}
		}
		helper.assertTrue(problems.isEmpty(), "dark basement floors: " + problems);
		helper.succeed();
	}

	// --- walking -------------------------------------------------------------------------------------------------------

	/** A body's feet or head can be in {@code state}: no collision, or one under a fifth of a block (a carpet), or a door, trapdoor, ladder or open gate. */
	private static boolean open(BlockState state) {
		if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock || state.is(Blocks.LADDER)) {
			return true;
		}
		if (state.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock) {
			return state.getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN);
		}
		var shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.2;
	}

	/**
	 * Someone can stand at {@code feet}: room for feet and head, and ground under them, or a ladder to hold (in it, or
	 * just under the feet: the top of a ladder, climbing out of a hatch).
	 */
	private static boolean standable(Map<BlockPos, BlockState> blocks, BlockPos feet) {
		BlockState here = at(blocks, feet);
		if (!open(here) || !open(at(blocks, feet.above()))) {
			return false;
		}
		BlockState below = at(blocks, feet.below());
		return here.is(Blocks.LADDER) || below.is(Blocks.LADDER) || !open(below);
	}

	/** Every spot reachable on foot from {@code starts} (see the test below), inside the build's box and two blocks round it. */
	private static java.util.Set<BlockPos> walk(Map<BlockPos, BlockState> blocks, List<BlockPos> starts, int sx, int sz, int top, int bottom) {
		java.util.Set<BlockPos> reached = new java.util.HashSet<>(starts);
		ArrayDeque<BlockPos> queue = new ArrayDeque<>(starts);
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			List<BlockPos> next = new ArrayList<>();
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos side = pos.relative(d);
				next.add(side);
				if (open(at(blocks, pos.above(2)))) {
					next.add(side.above()); // a jump or a stair up, with room over the head first
				}
				for (int drop = 1; drop <= 3; drop++) {
					if (!open(at(blocks, side.below(drop - 1))) || !open(at(blocks, side.below(drop - 1).above()))) {
						break;
					}
					next.add(side.below(drop));
				}
			}
			if (at(blocks, pos).is(Blocks.LADDER) || at(blocks, pos.above()).is(Blocks.LADDER)) {
				next.add(pos.above());
			}
			if (at(blocks, pos.below()).is(Blocks.LADDER)) {
				next.add(pos.below());
			}
			for (BlockPos n : next) {
				if (n.getX() < -2 || n.getX() > sx + 1 || n.getZ() < -2 || n.getZ() > sz + 1 || n.getY() < bottom || n.getY() > top) {
					continue;
				}
				if (!reached.contains(n) && standable(blocks, n)) {
					reached.add(n);
					queue.add(n);
				}
			}
		}
		return reached;
	}

	/**
	 * Every build with a basement (but the Quarry's open pit) can be walked into from outside and down to its basement
	 * floor, and every chest or barrel down there is within a player's reach of a spot reached that way: two blocks or
	 * less across, one up or down (36.7: "keeps its workers' chests reachable"; the Jeweller's vault keeps its stock
	 * chests behind iron bars, opened from the floor in front of the cage), and from the deepest spot reached the walk
	 * gets back outside. The walk goes a block at a time: level, one block up (a jump or a stair), up to three blocks
	 * down, and up or down a ladder; it starts on the ground all round the build.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyBasementAndItsChestsCanBeWalkedToFromOutside(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (StarterBlueprints.Entry entry : StarterBlueprints.WITH_BASEMENTS) {
			if (entry == StarterBlueprints.QUARRY) {
				continue;
			}
			Blueprint blueprint = blueprint(helper, entry);
			Map<BlockPos, BlockState> blocks = blocks(blueprint);
			int sx = blueprint.size().getX();
			int sz = blueprint.size().getZ();
			int top = blueprint.size().getY();
			int bottom = -blueprint.basement();
			List<BlockPos> outside = new ArrayList<>();
			for (int x = -2; x <= sx + 1; x++) {
				for (int z = -2; z <= sz + 1; z++) {
					if (x < -1 || x > sx || z < -1 || z > sz) {
						outside.add(new BlockPos(x, 0, z));
					}
				}
			}
			java.util.Set<BlockPos> reached = walk(blocks, outside, sx, sz, top, bottom);
			if (reached.stream().noneMatch(p -> p.getY() < 0)) {
				problems.add(entry.id() + ": its basement can't be walked to");
				continue;
			}
			// ... and back out: from the deepest spot reached down there, the walk gets outside again (up the ladder or stairs)
			BlockPos deep = reached.stream().filter(p -> p.getY() < 0)
				.min(java.util.Comparator.<BlockPos>comparingInt(p -> p.getY()).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ)).orElseThrow();
			java.util.Set<BlockPos> back = walk(blocks, List.of(deep), sx, sz, top, bottom);
			if (back.stream().noneMatch(p -> p.getX() < -1 || p.getX() > sx || p.getZ() < -1 || p.getZ() > sz)) {
				problems.add(entry.id() + ": no way back up out of its basement from " + deep.toShortString());
			}
			List<String> cutOff = new ArrayList<>();
			for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
				BlockPos c = e.getKey();
				BlockState state = e.getValue();
				if (c.getY() >= 0 || !(state.is(Blocks.CHEST) || state.is(Blocks.BARREL) || state.is(Blocks.TRAPPED_CHEST))) {
					continue;
				}
				boolean beside = false;
				for (BlockPos p : BlockPos.betweenClosed(c.offset(-2, -1, -2), c.offset(2, 1, 2))) {
					if (reached.contains(p)) {
						beside = true;
						break;
					}
				}
				if (!beside) {
					cutOff.add(state.getBlock().getName().getString() + " at " + c.toShortString());
				}
			}
			if (!cutOff.isEmpty()) {
				problems.add(entry.id() + ": nobody can get beside " + cutOff);
			}
		}
		helper.assertTrue(problems.isEmpty(), "basements cut off: " + problems);
		helper.succeed();
	}
}
