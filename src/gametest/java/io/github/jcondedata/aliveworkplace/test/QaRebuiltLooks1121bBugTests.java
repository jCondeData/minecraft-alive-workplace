package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * QA lane B (2026-11-21), what {@link QaRebuiltLooks1121bGameTests} found wrong in the rebuilt homes' looks (49.10b).
 * The desert Stone House II-IV's added bedroom (the bed at 11, 1, 3) is shut off: its only opening (9, 1, 7) leads onto
 * an upside-down sandstone stair (10, 1, 7) with a glass pane over the doorway (9, 3, 7), so no villager gets in. The
 * as-drawn Stone House II-IV and the savanna and taiga looks are fine (49.10a: a look keeps "the same rooms, beds ...
 * so every home test holds"; stone_house.md: every bed reached from inside).
 */
public class QaRebuiltLooks1121bBugTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	// ---- the walk, copied from QaRebuiltLooks1121bGameTests (so this class stands alone on its branch) ----

	static Optional<Blueprint> load(GameTestHelper helper, ResourceLocation id) {
		return BlueprintLibrary.get(helper.getLevel(), id);
	}

	static Map<BlockPos, BlockState> cells(Blueprint blueprint) {
		return QaBasementGameTests.blocks(blueprint);
	}

	static BlockState at(Map<BlockPos, BlockState> cells, BlockPos pos) {
		return QaBasementGameTests.at(cells, pos);
	}

	static List<BlockPos> bedHeads(Map<BlockPos, BlockState> cells) {
		return cells.entrySet().stream().filter(e -> e.getValue().getBlock() instanceof BedBlock
			&& e.getValue().getValue(BedBlock.PART) == BedPart.HEAD).map(Map.Entry::getKey).sorted().toList();
	}

	static BlockPos bedFoot(Map<BlockPos, BlockState> cells, BlockPos head) {
		return head.relative(at(cells, head).getValue(BedBlock.FACING).getOpposite());
	}

	// ---- a villager's walk (as QaRebuiltHomes1121bGameTests; panes added to the no-floor blocks) --------------------

	private static boolean open(BlockState state, boolean gates) {
		if (state.getBlock() instanceof DoorBlock && !state.is(Blocks.IRON_DOOR)) {
			return true;
		}
		if (gates && state.getBlock() instanceof FenceGateBlock) {
			return true;
		}
		if (state.is(Blocks.LADDER)) {
			return true;
		}
		var shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.2;
	}

	private static boolean noFloor(BlockState state) {
		return state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock || state.getBlock() instanceof FenceGateBlock
			|| state.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock;
	}

	private static boolean standable(Map<BlockPos, BlockState> cells, BlockPos feet, boolean gates) {
		BlockState here = at(cells, feet);
		if (!open(here, gates) || !open(at(cells, feet.above()), gates) || !here.getFluidState().isEmpty()) {
			return false;
		}
		BlockState below = at(cells, feet.below());
		return here.is(Blocks.LADDER) || below.is(Blocks.LADDER) || (!open(below, false) && !noFloor(below) && below.getFluidState().isEmpty());
	}

	/** Every spot a villager reaches on foot from the street in front (z < 0): level, a step up, up to three down, ladders. */
	static Set<BlockPos> walk(Blueprint blueprint, Map<BlockPos, BlockState> cells, boolean gates) {
		int sx = blueprint.size().getX();
		int sz = blueprint.size().getZ();
		int top = blueprint.size().getY();
		Set<BlockPos> reached = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (int x = -2; x <= sx + 1; x++) {
			for (int z = -2; z <= -1; z++) {
				BlockPos s = new BlockPos(x, 0, z);
				if (standable(cells, s, gates) && reached.add(s)) {
					queue.add(s);
				}
			}
		}
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			List<BlockPos> next = new ArrayList<>();
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos side = pos.relative(d);
				next.add(side);
				if (open(at(cells, pos.above(2)), gates)) {
					next.add(side.above());
				}
				for (int drop = 1; drop <= 3; drop++) {
					if (!open(at(cells, side.below(drop - 1)), gates) || !open(at(cells, side.below(drop - 1).above()), gates)) {
						break;
					}
					next.add(side.below(drop));
				}
			}
			if (at(cells, pos).is(Blocks.LADDER) || at(cells, pos.above()).is(Blocks.LADDER)) {
				next.add(pos.above());
			}
			if (at(cells, pos.below()).is(Blocks.LADDER)) {
				next.add(pos.below());
			}
			for (BlockPos nx : next) {
				if (nx.getX() < -2 || nx.getX() > sx + 1 || nx.getZ() < -2 || nx.getZ() > sz + 1 || nx.getY() < -8 || nx.getY() > top) {
					continue;
				}
				if (!reached.contains(nx) && standable(cells, nx, gates)) {
					reached.add(nx);
					queue.add(nx);
				}
			}
		}
		return reached;
	}

	static boolean beside(Set<BlockPos> reached, BlockPos target) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (reached.contains(target.relative(d)) || reached.contains(target.relative(d).below())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A villager gets to the bed: he stands beside its head or foot, or beside a bed right next to it (the game's
	 * pathfinder steps onto a bed, 9/16 high, so it reaches a bed boxed in beside another: see
	 * QaRebuiltLooks1121bGameTests).
	 */
	static boolean bedReached(Set<BlockPos> reached, Map<BlockPos, BlockState> cells, BlockPos head) {
		BlockPos foot = bedFoot(cells, head);
		if (beside(reached, head) || beside(reached, foot)) {
			return true;
		}
		for (BlockPos part : List.of(head, foot)) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos next = part.relative(d);
				if (!next.equals(head) && !next.equals(foot) && at(cells, next).getBlock() instanceof BedBlock && beside(reached, next)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * The game's own pathfinder on placed drawings (the arbiter for the static walk): from the street in front, a villager
	 * finds a path to each bed of {@code ids} (fence gates as placed).
	 */
	static void pathsToEveryBed(GameTestHelper helper, List<ResourceLocation> ids, List<BlockPos> origins) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<Villager> walkers = new ArrayList<>();
		List<List<BlockPos>> beds = new ArrayList<>();
		for (int i = 0; i < ids.size(); i++) {
			BlockPos at = helper.absolutePos(origins.get(i));
			var template = level.getStructureManager().get(ids.get(i)).orElseThrow(() -> new AssertionError("missing template"));
			template.placeInWorld(level, at, at, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
				net.minecraft.util.RandomSource.create(1121L), 2);
			Vec3i size = template.getSize();
			List<BlockPos> heads = new ArrayList<>();
			for (BlockPos p : BlockPos.betweenClosed(at, at.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
				BlockState st = level.getBlockState(p);
				if (st.getBlock() instanceof BedBlock && st.getValue(BedBlock.PART) == BedPart.HEAD) {
					heads.add(p.immutable());
				}
			}
			helper.assertTrue(!heads.isEmpty(), "(setup) no beds placed for " + ids.get(i));
			beds.add(heads);
			Villager v = helper.spawn(EntityType.VILLAGER, origins.get(i).offset(size.getX() / 2, 0, -2));
			v.setNoAi(true);
			walkers.add(v);
		}
		helper.runAfterDelay(10, () -> {
			List<String> cut = new ArrayList<>();
			for (int i = 0; i < ids.size(); i++) {
				Villager v = walkers.get(i);
				v.setOnGround(true);
				BlockPos at = helper.absolutePos(origins.get(i));
				for (BlockPos bed : beds.get(i)) {
					var path = v.getNavigation().createPath(bed, 1);
					if (path == null || !path.canReach()) {
						cut.add(ids.get(i).getPath() + " bed " + bed.subtract(at).toShortString());
					}
				}
			}
			walkers.forEach(Entity::discard);
			helper.assertTrue(cut.isEmpty(), "no path from the street to " + cut);
			helper.succeed();
		});
	}


	/** Every bed of the desert Stone House II, III and IV is walked to from the street (doors open, gates shut). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryBedOfTheDesertStoneHouseIIToIVIsWalkedToFromTheStreet(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (String path : List.of("stone_house", "stone_house_2", "stone_house_3", "stone_house_4")) {
			ResourceLocation id = AliveWorkplace.id("looks/desert/" + path);
			Blueprint b = load(helper, id).orElseThrow(() -> new AssertionError("missing " + id));
			Map<BlockPos, BlockState> cells = cells(b);
			Set<BlockPos> reached = walk(b, cells, false);
			List<String> cut = new ArrayList<>();
			for (BlockPos head : bedHeads(cells)) {
				if (!bedReached(reached, cells, head)) {
					cut.add(head.toShortString());
				}
			}
			if (!cut.isEmpty()) {
				problems.add(id.getPath() + ": beds no villager walks to from the street " + cut);
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** The same, with the game's own pathfinder on the placed desert Stone House II (and the as-drawn II beside it). */
	//$ gametest_ticks_batch HUGE '100' '"qaLooks1121bBugPaths"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaLooks1121bBugPaths")
	public void qaAVillagerFindsAPathToEveryBedOfTheDesertStoneHouseII(GameTestHelper helper) {
		pathsToEveryBed(helper, List.of(AliveWorkplace.id("stone_house_2"), AliveWorkplace.id("looks/desert/stone_house_2")),
			List.of(new BlockPos(1, 2, 5), new BlockPos(16, 2, 5)));
	}
}
