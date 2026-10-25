package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-b-1025-1838), from B173's spec ("every blueprint's doorways leave 2 blocks of headroom above the step-up", a
 * villager being 1.95 tall), not from the fix or tools/blueprints/check.py.
 * <p>
 * The check: for every door (lower half) in every build the Blueprint Table lists, read from the .nbt the mod loads, a
 * villager's 0.6-wide body is slid along the door's axis from the middle of the cell in front of the door to the middle
 * of the cell behind it, in steps of 1/20 of a block. At each step its feet stand on the highest collision box under
 * its footprint (the floor blocks of the three cells and whatever is on them: carpet 1/16, slab 1/2, stair 1, by each
 * block's real collision shape), and its head must fit under the lowest collision box of the door cell's column above
 * the door (the lintel) that overlaps its footprint: at least 1.95 between them. The doors themselves don't count
 * (they open), nor do fence gates. The sweep over every build is QaDoorwayBugGameTests' (unregistered: it finds 5 builds
 * on main); here are the rule on vanilla blocks, a mutation check on two fixed builds, and three real walks: a villager
 * walks in by the front door, shut as built, of the Supply Shop and the Village Hall (both fixed by B173: their carpets
 * start a block in), and of the Supply Shop turned a quarter round. Each build is placed with its y = 0 layer on the
 * area's floor, as a Build Site places it on the clicked block.
 */
public class QaDoorwayClearanceGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	static final double VILLAGER = 1.95;
	private static final double HALF_WIDTH = 0.3;

	private static List<AABB> boxes(Map<BlockPos, BlockState> blocks, BlockPos pos) {
		BlockState state = QaBasementGameTests.at(blocks, pos);
		// doors and fence gates open: they're not a step or a lintel
		if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock) {
			return List.of();
		}
		List<AABB> out = new ArrayList<>();
		for (AABB box : state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs()) {
			out.add(box.move(pos));
		}
		return out;
	}

	private static boolean under(AABB box, AABB foot) {
		return box.maxX > foot.minX + 1e-6 && box.minX < foot.maxX - 1e-6 && box.maxZ > foot.minZ + 1e-6 && box.minZ < foot.maxZ - 1e-6;
	}

	/**
	 * The least room a villager has walking through the door at {@code door} (its lower half), and where: {@code null}
	 * when it has at least {@link #VILLAGER} all the way through.
	 */
	static String tightest(Map<BlockPos, BlockState> blocks, BlockPos door, Direction facing) {
		double least = Double.MAX_VALUE;
		String where = null;
		for (int i = -20; i <= 20; i++) {
			double s = i / 20.0;
			double cx = door.getX() + 0.5 + facing.getStepX() * s;
			double cz = door.getZ() + 0.5 + facing.getStepZ() * s;
			AABB foot = new AABB(cx - HALF_WIDTH, 0, cz - HALF_WIDTH, cx + HALF_WIDTH, 1, cz + HALF_WIDTH);
			double floor = door.getY();
			String standing = "the floor";
			for (int k = -1; k <= 1; k++) {
				BlockPos cell = door.relative(facing, k);
				for (BlockPos layer : new BlockPos[] {cell.below(), cell}) {
					for (AABB box : boxes(blocks, layer)) {
						if (under(box, foot) && box.maxY > floor + 1e-6) {
							floor = box.maxY;
							standing = QaBasementGameTests.at(blocks, layer).getBlock().getName().getString() + " " + layer.toShortString();
						}
					}
				}
			}
			double ceiling = Double.MAX_VALUE;
			String lintel = "sky";
			for (BlockPos layer : new BlockPos[] {door.above(), door.above(2)}) {
				for (AABB box : boxes(blocks, layer)) {
					if (under(box, foot) && box.minY >= floor - 1e-6 && box.minY < ceiling) {
						ceiling = box.minY;
						lintel = QaBasementGameTests.at(blocks, layer).getBlock().getName().getString() + " " + layer.toShortString();
					}
				}
			}
			double room = ceiling - floor;
			if (room < least) {
				least = room;
				where = String.format("%.2f between %s (feet at %.2f, %.2f along) and %s", room, standing, floor - door.getY(), s, lintel);
			}
		}
		return least >= VILLAGER - 1e-6 ? null : where;
	}

	/**
	 * Every wooden door of every listed build whose doorway leaves less than {@link #VILLAGER}, with where; {@code counts}
	 * gets the builds and doors checked. An iron door is left out: no villager opens one, so no villager's path goes
	 * through it (the Jeweler's vault, a chest right behind its iron door). The sweep's own test is in
	 * QaDoorwayBugGameTests (not registered): on main, 2026-10-25, it finds 5 builds.
	 */
	static List<String> tightDoorways(GameTestHelper helper, int[] counts) {
		List<String> problems = new ArrayList<>();
		for (ResourceLocation id : BlueprintLibrary.list(helper.getLevel().getServer(), true)) {
			Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), id).orElse(null);
			if (blueprint == null || !QaChairFacingGameTests.listed(id)) {
				continue;
			}
			counts[0]++;
			Map<BlockPos, BlockState> blocks = QaBasementGameTests.blocks(blueprint);
			List<String> tight = new ArrayList<>();
			for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
				BlockState state = e.getValue();
				if (!(state.getBlock() instanceof DoorBlock) || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER
					|| !DoorBlock.isWoodenDoor(state)) {
					continue;
				}
				counts[1]++;
				String where = tightest(blocks, e.getKey(), state.getValue(DoorBlock.FACING));
				if (where != null) {
					tight.add("door " + e.getKey().toShortString() + ": " + where);
				}
			}
			if (!tight.isEmpty()) {
				problems.add(id.getPath() + ": " + tight);
			}
		}
		return problems;
	}

	/** The rule on vanilla's blocks: a carpet right inside a two-high door is too tight, a block further in it isn't. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theDoorwayRuleCatchesACarpetUnderTheLintel(GameTestHelper helper) {
		Map<BlockPos, BlockState> room = new java.util.HashMap<>();
		for (int z = -2; z <= 3; z++) {
			room.put(new BlockPos(0, 0, z), Blocks.OAK_PLANKS.defaultBlockState());
		}
		BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
		room.put(new BlockPos(0, 1, 0), door);
		room.put(new BlockPos(0, 2, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		room.put(new BlockPos(0, 3, 0), Blocks.OAK_PLANKS.defaultBlockState());
		String bare = tightest(room, new BlockPos(0, 1, 0), Direction.SOUTH);
		room.put(new BlockPos(0, 1, 1), Blocks.RED_CARPET.defaultBlockState());
		String carpetIn = tightest(room, new BlockPos(0, 1, 0), Direction.SOUTH);
		room.remove(new BlockPos(0, 1, 1));
		room.put(new BlockPos(0, 1, 2), Blocks.RED_CARPET.defaultBlockState());
		String carpetFurther = tightest(room, new BlockPos(0, 1, 0), Direction.SOUTH);
		room.put(new BlockPos(0, 1, -1), Blocks.OAK_SLAB.defaultBlockState());
		String slabOutside = tightest(room, new BlockPos(0, 1, 0), Direction.SOUTH);
		helper.getLevel().getServer().sendSystemMessage(Component.literal("[QA B173] rule check: bare " + bare + ", carpet in "
			+ carpetIn + ", carpet a block further " + carpetFurther + ", slab outside " + slabOutside));
		helper.assertTrue(bare == null && carpetFurther == null, "an open doorway was flagged: " + bare + " / " + carpetFurther);
		helper.assertTrue(carpetIn != null && slabOutside != null, "a step under the lintel wasn't flagged: " + carpetIn + " / " + slabOutside);
		helper.succeed();
	}

	/**
	 * Mutation check on shipped data: the Village Hall's and Supply Shop's front doors pass as loaded, and fail with their
	 * carpet pulled back to the cell right inside the door (B173's old layout; in a copy of the blocks, never the file).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aCarpetMovedUnderTheVillageHallsAndSupplyShopsLintelIsCaught(GameTestHelper helper) {
		String[] names = {"village_hall", "supply_shop"};
		BlockPos[] doors = {new BlockPos(6, 1, 3), new BlockPos(5, 1, 2)};
		StringBuilder log = new StringBuilder();
		for (int i = 0; i < names.length; i++) {
			Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), ResourceLocation.fromNamespaceAndPath("aliveworkplace", names[i])).orElseThrow();
			Map<BlockPos, BlockState> blocks = QaBasementGameTests.blocks(blueprint);
			BlockState door = blocks.get(doors[i]);
			helper.assertTrue(door != null && door.getBlock() instanceof DoorBlock, names[i] + ": no door at " + doors[i].toShortString() + " but " + door);
			Direction facing = door.getValue(DoorBlock.FACING);
			String shipped = tightest(blocks, doors[i], facing);
			blocks.put(doors[i].relative(facing), Blocks.RED_CARPET.defaultBlockState());
			String mutated = tightest(blocks, doors[i], facing);
			log.append(names[i]).append(": shipped ").append(shipped).append(", carpet inside ").append(mutated).append("; ");
			helper.assertTrue(shipped == null, names[i] + "'s front door is tight as shipped: " + shipped);
			helper.assertTrue(mutated != null, names[i] + "'s front door with a carpet right inside it wasn't flagged");
		}
		helper.getLevel().getServer().sendSystemMessage(Component.literal("[QA B173] mutation: " + log));
		helper.succeed();
	}

	/** Places a blueprint's .nbt with template (0,0,0) at {@code origin}, turned by {@code rotation}. */
	private static void place(GameTestHelper helper, String name, BlockPos origin, Rotation rotation) {
		ServerLevel level = helper.getLevel();
		StructureTemplate template = level.getStructureManager().get(ResourceLocation.fromNamespaceAndPath("aliveworkplace", name)).orElseThrow();
		BlockPos at = helper.absolutePos(origin);
		template.placeInWorld(level, at, at, new StructurePlaceSettings().setRotation(rotation), RandomSource.create(1173L), 2);
	}

	private static BlockPos cell(BlockPos origin, Rotation rotation, int x, int y, int z) {
		return origin.offset(StructureTemplate.transform(new BlockPos(x, y, z), Mirror.NONE, rotation, BlockPos.ZERO));
	}

	/**
	 * A villager starting outside, in front of the door, is walked (vanilla's walk target in its brain, every tick) to
	 * {@code goal} inside, by day; it passes once standing in the goal's cell.
	 */
	private static void walkIn(GameTestHelper helper, String name, Rotation rotation, int[] start, int[] goal) {
		// turned a quarter round, the build reaches back from its origin along x
		walkIn(helper, name, rotation, rotation == Rotation.NONE ? new BlockPos(8, 2, 8) : new BlockPos(20, 2, 8), start, goal);
	}

	/** The same with the template's origin at {@code origin}: y = 2 puts its y = 0 layer on the area's floor, as a site does. */
	static void walkIn(GameTestHelper helper, String name, Rotation rotation, BlockPos origin, int[] start, int[] goal) {
		walkIn(helper, name, rotation, origin, start, goal, null);
	}

	/**
	 * With {@code onlyDoor} (a template cell), every other door of the build is walled up after it's placed, so the
	 * villager can only get in by that one (it can't go round by another).
	 */
	static void walkIn(GameTestHelper helper, String name, Rotation rotation, BlockPos origin, int[] start, int[] goal,
		@org.jetbrains.annotations.Nullable int[] onlyDoor) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setDayTime(6000);
		place(helper, name, origin, rotation);
		if (onlyDoor != null) {
			BlockPos keep = helper.absolutePos(cell(origin, rotation, onlyDoor[0], onlyDoor[1], onlyDoor[2]));
			AABB box = helper.getBounds();
			int walled = 0;
			for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
				BlockState state = helper.getLevel().getBlockState(pos);
				if (state.getBlock() instanceof DoorBlock && !pos.equals(keep) && !pos.equals(keep.above())) {
					helper.getLevel().setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 2 | 16);
					walled++;
				}
			}
			helper.assertTrue(helper.getLevel().getBlockState(keep).getBlock() instanceof DoorBlock, "no door at " + keep.toShortString());
			helper.getLevel().getServer().sendSystemMessage(Component.literal("[QA B173] " + name + ": " + walled / 2 + " other doors walled up"));
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, cell(origin, rotation, start[0], start[1], start[2]));
		villager.getRandom().setSeed(1173L);
		// a nitwit with a nitwit's brain: no job site to wander off to (see DoorwayHeadroomGameTests.nitwit)
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NITWIT));
		villager.refreshBrain(helper.getLevel());
		BlockPos target = helper.absolutePos(cell(origin, rotation, goal[0], goal[1], goal[2]));
		helper.onEachTick(() -> {
			villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
			villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.6f, 0));
		});
		helper.succeedWhen(() -> {
			BlockPos at = villager.blockPosition();
			helper.assertTrue(at.getX() == target.getX() && at.getZ() == target.getZ(),
				"the villager hasn't walked in by the " + name + "'s front door (" + rotation + ") to " + target.toShortString()
					+ ": at " + villager.position());
			helper.getLevel().getServer().sendSystemMessage(Component.literal("[QA B173] a villager walked in by the " + name
				+ "'s shut front door (" + rotation + ") to " + target.toShortString() + " in " + helper.getTick() + " ticks"));
		});
	}

	/** The Supply Shop: front door template (5,1,2), shut; its carpet starts at (5,1,4); goal (5,1,5) by the counter. */
	//$ gametest_ticks_batch AREA '500' '"qaB173SupplyShop"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaB173SupplyShop")
	public void aVillagerWalksInByTheSupplyShopsShutFrontDoor(GameTestHelper helper) {
		walkIn(helper, "supply_shop", Rotation.NONE, new int[] {5, 0, -1}, new int[] {5, 1, 5});
	}

	/** The same Supply Shop turned a quarter round, as a player turns a blueprint at the Table. */
	//$ gametest_ticks_batch AREA '500' '"qaB173SupplyShopTurned"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaB173SupplyShopTurned")
	public void aVillagerWalksInByTheTurnedSupplyShopsShutFrontDoor(GameTestHelper helper) {
		walkIn(helper, "supply_shop", Rotation.CLOCKWISE_90, new int[] {5, 0, -1}, new int[] {5, 1, 5});
	}

	/** The Village Hall: front door template (6,1,3), shut; its red carpet runs (6,1,5) to (6,1,9); goal (6,1,8). */
	//$ gametest_ticks_batch AREA '500' '"qaB173VillageHall"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaB173VillageHall")
	public void aVillagerWalksInByTheVillageHallsShutFrontDoor(GameTestHelper helper) {
		walkIn(helper, "village_hall", Rotation.NONE, new int[] {6, 0, -1}, new int[] {6, 1, 8});
	}
}
