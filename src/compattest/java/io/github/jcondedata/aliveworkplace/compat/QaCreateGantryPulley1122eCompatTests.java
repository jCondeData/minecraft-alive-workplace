package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.HALL;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.REACH;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.done;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.hall;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.speed;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane E, 2026-11-22): B808, B993 and B1033's promise for the machines no earlier test drove: "a Mechanical Piston,
 * Gantry or Rope Pulley outside a protected village stops at its edge". With protection on, only the owner, friends and
 * operators may change blocks in the hall's area (village-hall.md), a full-height column; a contraption whose machine
 * stands outside must not carry blocks into it or out of it, and its drills must not break blocks in it. Machines inside
 * (and any with protection off) work as before.
 *
 * <p>Written from that promise: a stranger's Gantry carriage running along a shaft from outside into the village, with a
 * block on it and with a Mechanical Drill on it, the same carriage saved and loaded again on the way, a Rope Pulley
 * outside lowering a platform that reaches over the edge, an Elevator Pulley outside whose cabin reaches over the edge,
 * and a Gantry wholly inside the protected village. Every
 * protection case has its open-village control, so none passes for want of movement.
 *
 * <p>The village is {@link QaCreateIntoProtectedVillage1107eCompatTests}'s (hall at (3, 2, 15), 8 columns across): on the
 * hall's row z = 15, x = 11 is the last column inside and x = 12 the first outside. The shaft runs along that row at
 * y = 2 from x = 6 (inside) to x = 22 (outside); the carriage starts at x = 18 facing up with its load on top (y = 4); a
 * Creative Motor at the shaft's outer end (x = 23) turns it so the carriage runs west, into the village.
 */
public class QaCreateGantryPulley1122eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final int Z = 15;
	private static final int SHAFT_WEST = 6;
	private static final int SHAFT_EAST = 22;
	private static final int CARRIAGE_X = 18;
	/** The motor's speed: negative turns the shaft so a carriage runs away from the motor (west). */
	private static final int RPM = -32;

	/** Turns the hall's protection off just before {@code timeout}, so a failing test leaves no protected village behind. */
	private static void watchdog(GameTestHelper helper, int timeout) {
		helper.onEachTick(() -> {
			if (helper.getTick() >= timeout - 5 && helper.getLevel().getBlockEntity(helper.absolutePos(HALL)) instanceof VillageHallBlockEntity h) {
				h.setProtected(false);
			}
		});
	}

	private static boolean inVillage(BlockPos pos) {
		long dx = pos.getX() - HALL.getX();
		long dz = pos.getZ() - HALL.getZ();
		return dx * dx + dz * dz <= (long) REACH * REACH;
	}

	private static BlockState create(String path) {
		return MechanicCompatTests.create(path).defaultBlockState();
	}

	/** Sets the creative motor at {@code pos} to {@code rpm}, as a player does with its value panel (saved as {@code ScrollValue}). */
	private static void motorSpeed(GameTestHelper helper, BlockPos pos, int rpm) {
		BlockEntity be = helper.getBlockEntity(pos);
		CompoundTag tag = be.saveWithoutMetadata(helper.getLevel().registryAccess());
		tag.putInt("ScrollValue", rpm);
		be.loadWithComponents(tag, helper.getLevel().registryAccess());
		be.setChanged();
	}

	/**
	 * A gantry shaft along x from {@code west} to {@code east} at y = 2 on row {@code z}, a carriage on it at
	 * {@code carriageX} facing up with {@code load} on top, and a Creative Motor at the east end turning it so the
	 * carriage runs west.
	 */
	private static void gantry(GameTestHelper helper, int z, int west, int east, int carriageX, BlockState load) {
		for (int x = west; x <= east; x++) {
			for (int y = 3; y <= 5; y++) {
				helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
			}
			helper.setBlock(new BlockPos(x, 2, z), MechanicCompatTests.with(create("gantry_shaft"), "facing", "east"));
		}
		helper.setBlock(new BlockPos(carriageX, 3, z), MechanicCompatTests.with(MechanicCompatTests.with(create("gantry_carriage"), "facing", "up"),
			"axis_along_first", "true"));
		helper.setBlock(new BlockPos(carriageX, 4, z), load);
		BlockPos motor = new BlockPos(east + 1, 2, z);
		helper.setBlock(motor, MechanicCompatTests.with(create("creative_motor"), "facing", "west"));
		motorSpeed(helper, motor, RPM);
	}

	private static List<AbstractContraptionEntity> contraptions(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(AbstractContraptionEntity.class, helper.getBounds().inflate(8));
	}

	/** The least x (test frame, exact) any contraption entity reached; see {@link #follow}. */
	private static final java.util.Map<GameTestHelper, double[]> LEAST = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

	/** {[0]}: a contraption was seen (1/0); {[1]}: the least block x any contraption's anchor reached (test frame). */
	private static int[] follow(GameTestHelper helper) {
		int[] seen = {0, Integer.MAX_VALUE};
		double[] least = {Double.MAX_VALUE};
		LEAST.put(helper, least);
		helper.onEachTick(() -> {
			for (AbstractContraptionEntity e : contraptions(helper)) {
				seen[0] = 1;
				seen[1] = Math.min(seen[1], e.blockPosition().getX() - helper.absolutePos(BlockPos.ZERO).getX());
				least[0] = Math.min(least[0], e.getX() - helper.absolutePos(BlockPos.ZERO).getX());
			}
		});
		return seen;
	}

	private static String least(GameTestHelper helper) {
		return String.format(java.util.Locale.ROOT, "%.3f", leastX(helper));
	}

	/**
	 * Whether the contraption stayed out of the village: its block never got as much as half a block into the last column
	 * (x = 11). Create's collision lets a contraption overshoot by one tick's motion (1/16 block here) before it stops and
	 * sets it down flush, as it does against any wall, so "at x = 12" means at least 11.5.
	 */
	private static boolean stayedOut(GameTestHelper helper) {
		return leastX(helper) >= 11.5;
	}

	private static double leastX(GameTestHelper helper) {
		double[] l = LEAST.get(helper);
		return l == null ? Double.NaN : l[0];
	}

	private static void clearContraptions(GameTestHelper helper) {
		for (AbstractContraptionEntity e : contraptions(helper)) {
			e.discard();
		}
	}

	/** Where {@code block} stands on row {@code z} at y = 2..12 (test frame), as "x,y" items; {[0]} inside, {[1]} outside. */
	private static String[] places(GameTestHelper helper, int z, Block block) {
		StringBuilder in = new StringBuilder();
		StringBuilder out = new StringBuilder();
		for (int x = 0; x < 30; x++) {
			for (int y = 2; y <= 12; y++) {
				BlockPos p = new BlockPos(x, y, z);
				if (helper.getBlockState(p).is(block)) {
					(inVillage(p) ? in : out).append(x).append(',').append(y).append(' ');
				}
			}
		}
		return new String[] {in.toString().trim(), out.toString().trim()};
	}

	private static void assertGuarded(GameTestHelper helper, VillageHallBlockEntity hall, BlockPos machine, BlockPos inside) {
		if (VillageProtection.mayMachineChange(helper.getLevel(), helper.absolutePos(machine), helper.absolutePos(inside))) {
			done(helper, hall);
			helper.fail("set-up: the village isn't protected (a machine at " + machine.toShortString() + " may change " + inside.toShortString() + ")");
		}
	}

	/**
	 * Saves the gantry's moving contraption and loads it again in its place (as a chunk unloaded and loaded, or a restart,
	 * does), keeping its motion.
	 */
	private static void reloadContraption(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<AbstractContraptionEntity> all = contraptions(helper);
		if (all.size() != 1) {
			throw new GameTestAssertException("reload: " + all.size() + " contraptions, not 1");
		}
		AbstractContraptionEntity entity = all.get(0);
		CompoundTag tag = new CompoundTag();
		if (!entity.save(tag)) {
			throw new GameTestAssertException("reload: the contraption didn't save");
		}
		entity.discard();
		Entity again = EntityType.loadEntityRecursive(tag, level, e -> e);
		if (again == null || !level.tryAddFreshEntityWithPassengers(again)) {
			throw new GameTestAssertException("reload: the contraption didn't load from its save");
		}
	}

	// ---------------------------------------------------------------- 1. a Gantry carrying a block (and 5. saved and loaded)

	private static void gantryBlock(GameTestHelper helper, boolean protect, boolean reload) {
		VillageHallBlockEntity hall = hall(helper, protect);
		watchdog(helper, 400);
		gantry(helper, Z, SHAFT_WEST, SHAFT_EAST, CARRIAGE_X, Blocks.OAK_PLANKS.defaultBlockState());
		if (protect) {
			assertGuarded(helper, hall, new BlockPos(CARRIAGE_X, 3, Z), new BlockPos(11, 4, Z));
		}
		int[] seen = follow(helper);
		boolean[] reloaded = {!reload};
		if (reload) {
			helper.onEachTick(() -> {
				if (!reloaded[0] && seen[1] <= 15 && contraptions(helper).size() == 1) {
					reloaded[0] = true;
					reloadContraption(helper);
				}
			});
		}
		helper.runAfterDelay(300, () -> {
			String[] planks = places(helper, Z, Blocks.OAK_PLANKS);
			String[] carriages = places(helper, Z, MechanicCompatTests.create("gantry_carriage"));
			int moving = contraptions(helper).size();
			clearContraptions(helper);
			done(helper, hall);
			helper.assertTrue(seen[0] == 1, "set-up: the gantry carriage never put its contraption together (carriage at " + carriages[1] + ")");
			helper.assertTrue(reloaded[0], "set-up: the carriage was never saved and loaded again (least x " + seen[1] + ")");
			if (protect) {
				helper.assertTrue(planks[0].isEmpty() && carriages[0].isEmpty(), "a stranger's gantry carriage from outside Frank's protected village"
					+ " carried its plank (and itself) across the edge: planks inside at " + planks[0] + ", carriage inside at " + carriages[0]
					+ " (least x reached " + seen[1] + ", x = 11 is the village's last column)");
				helper.assertTrue(seen[1] <= 13, "set-up: the carriage never ran up to the edge (least x " + seen[1] + ")");
				helper.assertTrue(stayedOut(helper), "a stranger's moving gantry carriage crossed into Frank's protected village (least x " + least(helper)
					+ ", x = 11 is the village's last column), carrying its plank in");
				helper.assertTrue(!planks[1].isEmpty() || moving > 0, "the plank the gantry couldn't take in is gone altogether");
			} else {
				helper.assertTrue(!planks[0].isEmpty(), "the control: the gantry carriage never carried its plank into the open village (least x "
					+ seen[1] + ", planks outside at " + planks[1] + ", carriage at " + carriages[1] + ")");
			}
			helper.succeed();
		});
	}

	/** A stranger's gantry carriage running along its shaft from outside never carries its block into the protected village. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122g_gantry_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122g_gantry_protected")
	public void aStrangersGantryStopsAtTheEdgeOfAProtectedVillage(GameTestHelper helper) {
		gantryBlock(helper, true, false);
	}

	/** The control: with the village open the same carriage runs on in with its block. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122g_gantry_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122g_gantry_open")
	public void aGantryRunsIntoAnOpenVillage(GameTestHelper helper) {
		gantryBlock(helper, false, false);
	}

	/** The stranger's carriage is saved and loaded again (a restart) on its way: it still stops at the protected village's edge. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122g_gantry_reloaded"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122g_gantry_reloaded")
	public void aStrangersGantrySavedAndLoadedOnTheWayStillStopsAtTheEdge(GameTestHelper helper) {
		gantryBlock(helper, true, true);
	}

	/** The control: the carriage saved and loaded on the way keeps running, on into an open village. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122g_gantry_reloaded_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122g_gantry_reloaded_open")
	public void aGantrySavedAndLoadedOnTheWayRunsOnIntoAnOpenVillage(GameTestHelper helper) {
		gantryBlock(helper, false, true);
	}

	// ---------------------------------------------------------------- 2. a Gantry carrying a drill

	/** A stone in the village's last column (x = 11) in the drill's way, at the drill's height. */
	private static final BlockPos STONE = new BlockPos(11, 4, Z);

	private static void gantryDrill(GameTestHelper helper, boolean protect) {
		VillageHallBlockEntity hall = hall(helper, protect);
		watchdog(helper, 400);
		gantry(helper, Z, SHAFT_WEST, SHAFT_EAST, CARRIAGE_X, MechanicCompatTests.with(create("mechanical_drill"), "facing", "west"));
		helper.setBlock(STONE, Blocks.STONE);
		if (protect) {
			assertGuarded(helper, hall, new BlockPos(CARRIAGE_X, 3, Z), STONE);
		}
		int[] seen = follow(helper);
		helper.runAfterDelay(300, () -> {
			BlockState there = helper.getBlockState(STONE);
			String[] drills = places(helper, Z, MechanicCompatTests.create("mechanical_drill"));
			clearContraptions(helper);
			done(helper, hall);
			helper.assertTrue(seen[0] == 1, "set-up: the gantry carriage never put its contraption together");
			if (protect) {
				helper.assertTrue(seen[1] <= 13, "set-up: the drill gantry never ran up to the edge (least x " + seen[1] + ")");
				helper.assertTrue(there.is(Blocks.STONE), "a stranger's drill on a gantry carriage from outside Frank's protected village broke the stone"
					+ " in its last column (x = 11): " + there + " (least x reached " + seen[1] + ", drill inside at " + drills[0] + ")");
				helper.assertTrue(drills[0].isEmpty() && stayedOut(helper), "the stranger's drill gantry got into the protected village: drill at " + drills[0]
					+ ", least x " + least(helper));
			} else {
				helper.assertTrue(there.isAir(), "the control: a drill gantry running into an open village didn't break the stone at x = 11 (least x "
					+ seen[1] + "): " + there);
			}
			helper.succeed();
		});
	}

	/** A stranger's drill gantry running up to the protected village breaks nothing in it. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122g_drill_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122g_drill_protected")
	public void aStrangersDrillGantryBreaksNothingInAProtectedVillage(GameTestHelper helper) {
		gantryDrill(helper, true);
	}

	/** The control: the same drill gantry bores through the stone of an open village. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122g_drill_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122g_drill_open")
	public void aDrillGantryBoresIntoAnOpenVillage(GameTestHelper helper) {
		gantryDrill(helper, false);
	}

	// ---------------------------------------------------------------- 3. a Rope Pulley whose platform reaches over the edge

	/** The pulley just outside (x = 13), its motor east of it (x = 14); the platform (slime, slime, plank) hangs at y = 9 from x = 13 to 11. */
	private static final BlockPos PULLEY = new BlockPos(13, 10, Z);
	/** The platform's end over the edge: the village's last column. */
	private static final BlockPos OVER = new BlockPos(11, 9, Z);

	private static void pulley(GameTestHelper helper, boolean protect) {
		VillageHallBlockEntity hall = hall(helper, protect);
		watchdog(helper, 400);
		for (int x = 10; x <= 14; x++) {
			for (int y = 2; y <= 9; y++) {
				helper.setBlock(new BlockPos(x, y, Z), Blocks.AIR);
			}
		}
		helper.setBlock(PULLEY, MechanicCompatTests.with(create("rope_pulley"), "axis", "x"));
		helper.setBlock(PULLEY.below(), Blocks.SLIME_BLOCK);
		helper.setBlock(PULLEY.below().west(), Blocks.SLIME_BLOCK);
		helper.setBlock(OVER, Blocks.OAK_PLANKS);
		// the motor on the pulley's outer side, clear of the slime (a slime block would take it along); turned backwards so the rope pays out
		helper.setBlock(PULLEY.east(), MechanicCompatTests.with(create("creative_motor"), "facing", "west"));
		motorSpeed(helper, PULLEY.east(), RPM);
		if (protect) {
			assertGuarded(helper, hall, PULLEY, OVER);
		}
		float[] turned = {0};
		helper.onEachTick(() -> turned[0] = Math.max(turned[0], Math.abs(speed(helper, PULLEY))));
		helper.runAfterDelay(300, () -> {
			String[] planks = places(helper, Z, Blocks.OAK_PLANKS);
			String[] slime = places(helper, Z, Blocks.SLIME_BLOCK);
			float turning = turned[0];
			boolean stillThere = helper.getBlockState(OVER).is(Blocks.OAK_PLANKS);
			int moving = contraptions(helper).size();
			clearContraptions(helper);
			done(helper, hall);
			helper.assertTrue(turning != 0, "set-up: the rope pulley never turned");
			if (protect) {
				helper.assertTrue(stillThere && planks[0].equals("11,9"), "a stranger's rope pulley outside Frank's protected village lowered its platform"
					+ " and moved the plank in the village's last column: planks inside at [" + planks[0] + "] (was 11,9), slime at " + slime[1]
					+ ", contraptions moving " + moving);
			} else {
				helper.assertTrue(!stillThere && !planks[0].isEmpty(), "the control: the rope pulley never lowered the platform over the open village's"
					+ " edge: planks inside at [" + planks[0] + "], slime at " + slime[1] + ", contraptions moving " + moving);
			}
			helper.succeed();
		});
	}

	/** A stranger's rope pulley outside can't lower a platform whose end hangs over the protected village's edge. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122g_pulley_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122g_pulley_protected")
	public void aStrangersRopePulleyDoesntLowerAPlatformOverAProtectedVillage(GameTestHelper helper) {
		pulley(helper, true);
	}

	/** The control: with the village open the same pulley lowers the platform, the plank over the edge going down with it. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122g_pulley_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122g_pulley_open")
	public void aRopePulleyLowersAPlatformOverAnOpenVillage(GameTestHelper helper) {
		pulley(helper, false);
	}

	// ---------------------------------------------------------------- 4. an Elevator Pulley whose cabin reaches over the edge

	/** The elevator pulley just outside (x = 13), its motor east of it (x = 14); the cabin hangs at y = 11 from x = 13 to 11. */
	private static final BlockPos ELEVATOR = new BlockPos(13, 12, Z);
	/** The cabin's end over the edge: the village's last column. */
	static final BlockPos CABIN_END = new BlockPos(11, 11, Z);

	/**
	 * A cabin of slime, slime and an oak plank (the plank in the village's last column) with a Redstone Contact on its side
	 * (an elevator cabin needs one), hung from an Elevator Pulley outside and turned by a motor. A player right-clicks the
	 * pulley (tick 10: Create puts the cabin together), then breaks it (tick 60: Create sets the cabin down again).
	 *
	 * <p>Open village (the control): the plank goes into the cabin and comes back to its place. Protected: a stranger's
	 * elevator outside may not take the village's plank, so it stays put the whole time (B993's rule; Create's elevators
	 * skip the terrain check a rope pulley makes, so this is the path the gantry and rope pulley fixes don't cover).
	 */
	static void elevator(GameTestHelper helper, boolean protect) {
		VillageHallBlockEntity hall = hall(helper, protect);
		watchdog(helper, 300);
		for (int x = 10; x <= 14; x++) {
			for (int z = Z; z <= Z + 1; z++) {
				for (int y = 2; y <= 11; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(ELEVATOR, MechanicCompatTests.with(create("elevator_pulley"), "facing", "north"));
		helper.setBlock(ELEVATOR.below(), Blocks.SLIME_BLOCK);
		helper.setBlock(ELEVATOR.below().west(), Blocks.SLIME_BLOCK);
		helper.setBlock(CABIN_END, Blocks.OAK_PLANKS);
		helper.setBlock(ELEVATOR.below().south(), MechanicCompatTests.with(create("redstone_contact"), "facing", "south"));
		helper.setBlock(ELEVATOR.east(), MechanicCompatTests.with(create("creative_motor"), "facing", "west"));
		motorSpeed(helper, ELEVATOR.east(), 64);
		if (protect) {
			assertGuarded(helper, hall, ELEVATOR, CABIN_END);
		}
		int[] seen = {0};
		boolean[] plankWhileAssembled = {true};
		helper.onEachTick(() -> seen[0] = Math.max(seen[0], contraptions(helper).size()));
		helper.runAfterDelay(10, () -> {
			if (helper.getLevel().getBlockEntity(helper.absolutePos(ELEVATOR)) instanceof com.simibubi.create.content.contraptions.elevator.ElevatorPulleyBlockEntity e) {
				e.clicked(); // what right-clicking the pulley does
			}
		});
		helper.runAfterDelay(50, () -> plankWhileAssembled[0] = helper.getBlockState(CABIN_END).is(Blocks.OAK_PLANKS));
		helper.runAfterDelay(60, () -> helper.destroyBlock(ELEVATOR));
		helper.runAfterDelay(200, () -> {
			boolean plankAfter = helper.getBlockState(CABIN_END).is(Blocks.OAK_PLANKS);
			String[] planks = places(helper, Z, Blocks.OAK_PLANKS);
			int dropped = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, helper.getBounds().inflate(4),
				e -> e.getItem().is(net.minecraft.world.item.Items.OAK_PLANKS)).size();
			clearContraptions(helper);
			done(helper, hall);
			String state = "plank in the village's last column while the cabin was together: " + plankWhileAssembled[0] + ", after the pulley was broken: "
				+ plankAfter + " (planks inside at [" + planks[0] + "], outside at [" + planks[1] + "], oak plank items on the ground " + dropped
				+ "; cabins seen " + seen[0] + ")";
			helper.assertTrue(seen[0] > 0, "set-up: the elevator never put its cabin together: " + state);
			if (protect) {
				helper.assertTrue(plankWhileAssembled[0] && plankAfter, "a stranger's elevator pulley outside Frank's protected village took the plank in"
					+ " the village's last column into its cabin: " + state);
			} else {
				helper.assertTrue(!plankWhileAssembled[0], "the control: the elevator's cabin didn't take the plank over the open village's edge: " + state);
				helper.assertTrue(plankAfter, "the control: breaking the elevator pulley didn't set the plank back in its place: " + state);
			}
			helper.succeed();
		});
	}

	/**
	 * The control: beside an open village an elevator outside takes the plank over the edge into its cabin, and sets it back
	 * when the pulley is broken. (The protected case, B1487, is {@link QaCreateGantryPulley1122eBugCompatTests}.)
	 */
	//$ gametest_ticks_batch AREA '300' '"qa_e1122g_elevator_open"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_e1122g_elevator_open")
	public void anElevatorBesideAnOpenVillageTakesItsCabinAndSetsItBack(GameTestHelper helper) {
		elevator(helper, false);
	}

	// ---------------------------------------------------------------- 6. a Gantry wholly inside

	/**
	 * A gantry wholly inside the protected village (row z = 17, which the village covers up to x = 10: shaft x = 1..9,
	 * motor at x = 10, carriage from x = 8) still runs along its shaft with its plank, as before.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122g_gantry_inside"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122g_gantry_inside")
	public void aGantryInsideAProtectedVillageStillRuns(GameTestHelper helper) {
		VillageHallBlockEntity hall = hall(helper, true);
		watchdog(helper, 400);
		int z = 17;
		gantry(helper, z, 1, 9, 8, Blocks.OAK_PLANKS.defaultBlockState());
		assertGuarded(helper, hall, new BlockPos(13, 3, z), new BlockPos(8, 4, z));
		if (!inVillage(new BlockPos(10, 2, z)) || !inVillage(new BlockPos(1, 2, z))) {
			done(helper, hall);
			helper.fail("set-up: the inside gantry isn't wholly inside the village");
		}
		int[] seen = follow(helper);
		helper.runAfterDelay(300, () -> {
			String[] planks = places(helper, z, Blocks.OAK_PLANKS);
			clearContraptions(helper);
			done(helper, hall);
			helper.assertTrue(seen[0] == 1, "a gantry carriage inside Frank's protected village never started (plank at " + planks[0] + ")");
			helper.assertTrue(seen[1] <= 3, "a gantry inside Frank's protected village didn't run along its shaft: least x " + seen[1]
				+ " (from 8 toward 1), plank at " + planks[0]);
			helper.assertTrue(planks[1].isEmpty() && !planks[0].isEmpty(), "the inside gantry's plank went missing or outside: inside " + planks[0]
				+ ", outside " + planks[1]);
			helper.succeed();
		});
	}
}
