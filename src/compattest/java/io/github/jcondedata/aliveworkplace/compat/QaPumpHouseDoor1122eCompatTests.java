package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * QA lane E (2026-11-22), B1405/B1406 from the bug's words, with Create loaded: "A villager can't walk through the Pump
 * House's upper door at (13,8,1): the fence at (13,8,0) in front of it leaves 0.5 blocks of headroom under the log at
 * (13,10,1). Expected: every doorway leaves a villager room." The shipped drawing is placed whole (as drawn, and turned
 * and flipped as the blueprint table can), a villager on the balcony outside the upper door walks into the tank room
 * through the shut door and back out the way vanilla walks him (a walk target in his brain); and every door of the
 * drawing is measured from the placed blocks' own collision shapes (not the mod's doorway checker): a villager standing
 * in the doorway or on the cell either side of it has his own height clear above his feet.
 * <p>
 * The Pump House has no looks and no modded copy (only {@code structure/pump_house.nbt}; {@code blueprint_history}
 * keeps old drawings for refreshes, not built).
 */
public class QaPumpHouseDoor1122eCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	/** The upper door's lower half, the balcony cell outside it and a cell in the tank room two blocks past it. */
	private static final BlockPos DOOR = new BlockPos(13, 8, 1);
	private static final BlockPos BALCONY = new BlockPos(13, 8, 0);
	private static final BlockPos INSIDE = new BlockPos(12, 8, 4);
	/** The Pump House's mechanical pump (Create), to tell a run without Create. */
	private static final BlockPos PUMP = new BlockPos(13, 2, 3);
	/** Half a villager's width: the footprint measured round a cell's middle. */
	private static final double HALF = 0.3;

	/** The drawing placed with its template origin where its turned box fits the area; returns the origin (absolute). */
	private static BlockPos place(GameTestHelper helper, Rotation rotation, Mirror mirror) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		StarterBlueprints.Entry entry = StarterBlueprints.PUMP_HOUSE;
		StructureTemplate template = level.getStructureManager().get(entry.id()).orElseThrow(() -> new GameTestAssertException("(setup) no pump_house"));
		BlockPos far = StructureTemplate.transform(new BlockPos(entry.size().getX() - 1, 0, entry.size().getZ() - 1), mirror, rotation, BlockPos.ZERO);
		int width = Math.abs(far.getX()) + 1;
		int depth = Math.abs(far.getZ()) + 1;
		BlockPos corner = new BlockPos(Math.min(6, 29 - width), 2, Math.min(6, 29 - depth));
		BlockPos origin = helper.absolutePos(corner.offset(-Math.min(0, far.getX()), 0, -Math.min(0, far.getZ())));
		template.placeInWorld(level, origin, origin, new StructurePlaceSettings().setRotation(rotation).setMirror(mirror),
			net.minecraft.util.RandomSource.create(1122L), 2);
		BlockPos pump = at(origin, rotation, mirror, PUMP);
		String pumpId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pump).getBlock()).toString();
		helper.assertTrue(pumpId.equals("create:mechanical_pump"), "(setup) no Create pump at " + PUMP.toShortString() + " (is Create loaded?): " + pumpId);
		return origin;
	}

	private static BlockPos at(BlockPos origin, Rotation rotation, Mirror mirror, BlockPos local) {
		return origin.offset(StructureTemplate.transform(local, mirror, rotation, BlockPos.ZERO));
	}

	/**
	 * A nitwit (a nitwit's brain: no job site to go after) on the balcony outside the upper door walks to the tank room
	 * cell {@link #INSIDE} through the door as drawn (shut), then back out onto the balcony, each within 20 seconds.
	 */
	private static void inAndOut(GameTestHelper helper, Rotation rotation, Mirror mirror) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = place(helper, rotation, mirror);
		BlockPos door = at(origin, rotation, mirror, DOOR);
		BlockState doorState = level.getBlockState(door);
		helper.assertTrue(doorState.getBlock() instanceof DoorBlock && doorState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER,
			"(setup) the upper door has moved: " + doorState);
		BlockPos out = at(origin, rotation, mirror, BALCONY);
		BlockPos in = at(origin, rotation, mirror, INSIDE);
		Villager villager = EntityType.VILLAGER.create(level);
		villager.moveTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5, 0, 0);
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NITWIT));
		level.addFreshEntity(villager);
		villager.refreshBrain(level);
		String how = rotation + "/" + mirror;
		long[] reachedIn = {-1};
		long[] start = {-1};
		helper.onEachTick(() -> {
			if (start[0] < 0) {
				start[0] = helper.getTick();
			}
			if (level.getDayTime() % 24000 > 9000) {
				helper.setDayTime(2000);
			}
			helper.assertTrue(villager.isAlive(), how + ": the villager died (" + villager.getRemovalReason() + ")");
			if (reachedIn[0] < 0 && villager.blockPosition().equals(in) && villager.onGround()) {
				reachedIn[0] = helper.getTick();
			}
			BlockPos goal = reachedIn[0] < 0 ? in : out;
			if (reachedIn[0] < 0) {
				helper.assertTrue(helper.getTick() - start[0] <= 400, how + ": no way in by the upper door in 20 s: the villager is at "
					+ local(origin, rotation, mirror, villager) + " (drawing cells), wanted " + INSIDE.toShortString());
			} else {
				helper.assertTrue(helper.getTick() - reachedIn[0] <= 400, how + ": no way back out by the upper door in 20 s: the villager is at "
					+ local(origin, rotation, mirror, villager) + " (drawing cells), wanted " + BALCONY.toShortString());
			}
			villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(goal));
			villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(goal, 0.6f, 0));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reachedIn[0] >= 0, how + ": not in the tank room yet");
			helper.assertTrue(villager.blockPosition().equals(out) && villager.onGround(), how + ": not back on the balcony yet: at "
				+ local(origin, rotation, mirror, villager));
			villager.discard();
		});
	}

	/** Where {@code v} stands, in the drawing's own cells. */
	private static String local(BlockPos origin, Rotation rotation, Mirror mirror, Villager v) {
		BlockPos rel = v.blockPosition().subtract(origin);
		// undo the turn: rotate back, then mirror (the mirror is its own inverse)
		Rotation back = switch (rotation) {
			case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
			case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
			default -> rotation;
		};
		BlockPos unturned = StructureTemplate.transform(rel, Mirror.NONE, back, BlockPos.ZERO);
		BlockPos plain = StructureTemplate.transform(unturned, mirror, Rotation.NONE, BlockPos.ZERO);
		return plain.toShortString() + String.format(" (feet %.2f)", v.getY() - origin.getY());
	}

	/** As drawn: in through the upper door and back out. */
	//$ gametest_ticks_batch HUGE_AREA '1200' '"qaPumpHouseDoor1122eDrawn"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1200, batch = "qaPumpHouseDoor1122eDrawn")
	public void aVillagerWalksInAndOutOfThePumpHousesUpperDoor(GameTestHelper helper) {
		inAndOut(helper, Rotation.NONE, Mirror.NONE);
	}

	/** Turned a quarter: the same. */
	//$ gametest_ticks_batch HUGE_AREA '1200' '"qaPumpHouseDoor1122eTurned"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1200, batch = "qaPumpHouseDoor1122eTurned")
	public void aVillagerWalksInAndOutOfATurnedPumpHousesUpperDoor(GameTestHelper helper) {
		inAndOut(helper, Rotation.CLOCKWISE_90, Mirror.NONE);
	}

	/** Flipped left to right and turned half way: the same. */
	//$ gametest_ticks_batch HUGE_AREA '1200' '"qaPumpHouseDoor1122eFlipped"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1200, batch = "qaPumpHouseDoor1122eFlipped")
	public void aVillagerWalksInAndOutOfAFlippedPumpHousesUpperDoor(GameTestHelper helper) {
		inAndOut(helper, Rotation.CLOCKWISE_180, Mirror.LEFT_RIGHT);
	}

	/**
	 * Every door of the placed drawing: in the doorway and on the cell before and behind it (along the door's facing), a
	 * villager-wide footprint round the cell's middle stands on the highest collision box under it that is no more than a
	 * step (0.6) up, and the lowest collision box above his feet (the door's own two halves left out) is at least a
	 * villager's height (1.95) higher.
	 */
	//$ gametest_ticks_batch HUGE_AREA '200' '"qaPumpHouseDoor1122eRoom"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 200, batch = "qaPumpHouseDoor1122eRoom")
	public void everyPumpHouseDoorwayLeavesAVillagerRoom(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = place(helper, Rotation.NONE, Mirror.NONE);
		var size = StarterBlueprints.PUMP_HOUSE.size();
		double tall = EntityType.VILLAGER.getHeight();
		List<String> doors = new ArrayList<>();
		List<String> cramped = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
			BlockState state = level.getBlockState(p);
			if (!(state.getBlock() instanceof DoorBlock) || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
				continue;
			}
			BlockPos door = p.immutable();
			Direction facing = state.getValue(DoorBlock.FACING);
			doors.add(door.subtract(origin).toShortString());
			for (BlockPos cell : List.of(door.relative(facing), door, door.relative(facing.getOpposite()))) {
				String what = room(level, origin, cell, door, tall);
				if (what != null) {
					cramped.add("door " + door.subtract(origin).toShortString() + ", cell " + cell.subtract(origin).toShortString() + ": " + what);
				}
			}
		}
		helper.assertTrue(doors.size() >= 3, "(setup) the drawing has " + doors.size() + " doors (" + doors + "), it draws 3");
		helper.assertTrue(doors.contains(DOOR.toShortString()), "(setup) no door at " + DOOR.toShortString() + ": " + doors);
		helper.assertTrue(cramped.isEmpty(), cramped.size() + " doorway cells without room for a villager (" + tall + " tall): " + cramped);
		helper.succeed();
	}

	/** Null when a villager has room at {@code cell}, else what's wrong. */
	private static String room(ServerLevel level, BlockPos origin, BlockPos cell, BlockPos door, double tall) {
		double cx = cell.getX() + 0.5;
		double cz = cell.getZ() + 0.5;
		AABB column = new AABB(cx - HALF, cell.getY() - 1, cz - HALF, cx + HALF, cell.getY() + 4, cz + HALF);
		List<AABB> boxes = new ArrayList<>();
		for (int dy = -1; dy <= 3; dy++) {
			BlockPos q = cell.above(dy);
			if (q.equals(door) || q.equals(door.above())) {
				continue; // the door itself opens out of the way
			}
			for (AABB b : level.getBlockState(q).getCollisionShape(level, q).toAabbs()) {
				AABB w = b.move(q);
				if (w.maxX > column.minX && w.minX < column.maxX && w.maxZ > column.minZ && w.minZ < column.maxZ) {
					boxes.add(w);
				}
			}
		}
		double feet = Double.NEGATIVE_INFINITY;
		for (AABB b : boxes) {
			if (b.maxY <= cell.getY() + 0.6 && b.maxY > feet) {
				feet = b.maxY;
			}
		}
		if (feet == Double.NEGATIVE_INFINITY) {
			return "nothing to stand on";
		}
		double over = Double.POSITIVE_INFINITY;
		String by = "";
		for (AABB b : boxes) {
			if (b.maxY > feet + 1.0E-6 && b.minY < over) {
				over = b.minY;
				by = level.getBlockState(BlockPos.containing(b.getCenter())).getBlock().getName().getString() + " at "
					+ BlockPos.containing(new Vec3(b.getCenter().x, b.minY + 1.0E-6, b.getCenter().z)).subtract(origin).toShortString();
			}
		}
		double clear = over - feet;
		return clear + 1.0E-6 >= tall ? null : String.format("%.2f clear (feet at %.2f, under %s)", clear, feet - cell.getY(), by);
	}
}
