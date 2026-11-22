package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.HALL;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.done;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.hall;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.speed;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane E, 2026-11-22): village protection against Create's Portable Storage Interface (B808/B993/B1033 family). The
 * protection's promise: a machine that stands outside a protected village must not place, break, move, insert into or
 * extract from anything inside it; machines inside and any village with protection off work as before.
 *
 * <p>The realistic attack: Frank (the village's owner) has a Portable Storage Interface at his village's edge, fed by a
 * hopper from his chest and emptied by a hopper into his barrel (an unloading/loading station). A stranger's
 * contraption, a Mechanical Piston standing outside the village pushing a chest and a moving Portable Storage Interface,
 * stops just outside the edge and docks with Frank's interface across the one-block gap. While the village is
 * protected nothing may leave Frank's chest into the stranger's contraption, and nothing from it may arrive in his
 * barrel. With protection off (the control) both transfers happen, so the protected case isn't vacuous. The protected
 * cases fail today (2026-11-22: all 32 emeralds leave) and live in the unregistered
 * {@link QaCreatePortableTrains1122eBugCompatTests}; this class keeps the controls (open village, and open after a save
 * and reload while docked) that prove the set-up docks and trades.
 *
 * <p>Second, a stranger's real Create train (a carriage contraption assembled at a station outside the village) with a
 * Mechanical Drill glued to its side runs into Frank's protected village: it breaks the plank outside (calibration) and
 * none inside, also with the hall saved and loaded on the way; with protection off it breaks them all.
 *
 * <p>The PSI village is {@link QaCreateIntoProtectedVillage1107eCompatTests}'s: hall at (3, 2, 15), 8 columns across, so at
 * z = 15 x = 11 is its last column and x = 12 the first outside.
 */
public class QaCreatePortableTrains1122eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** Frank's stationary interface in the village's last column, facing east (out of the village). */
	static final BlockPos STATIONARY = new BlockPos(11, 2, 15);
	/** Frank's chest (8 emeralds) above the hopper that feeds his interface. */
	static final BlockPos CHEST = new BlockPos(10, 3, 15);
	static final BlockPos FEEDER = new BlockPos(10, 2, 15);
	/** The hopper under Frank's interface, emptying it into his barrel. */
	static final BlockPos DRAIN = new BlockPos(11, 1, 15);
	static final BlockPos BARREL = new BlockPos(10, 1, 15);
	/** The stranger's moving interface (ends at x = 13 after two steps), his chest behind it, his piston behind that. */
	static final BlockPos MOVING = new BlockPos(15, 2, 15);
	static final BlockPos CART_CHEST = new BlockPos(16, 2, 15);
	static final BlockPos PISTON = new BlockPos(17, 2, 15);

	/** Turns the hall's protection off just before {@code timeout}, so a failing test leaves no protected village behind. */
	private static void watchdog(GameTestHelper helper, int timeout) {
		helper.onEachTick(() -> {
			if (helper.getTick() >= timeout - 5 && helper.getLevel().getBlockEntity(helper.absolutePos(HALL)) instanceof VillageHallBlockEntity h) {
				h.setProtected(false);
			}
		});
	}

	/** Saves the block entity at {@code pos} and loads it again in its place, as a chunk unloaded and loaded does. */
	private static void reload(GameTestHelper helper, BlockPos pos) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(pos);
		BlockEntity be = level.getBlockEntity(abs);
		if (be == null) {
			throw new GameTestAssertException("reload: no block entity at " + pos.toShortString());
		}
		CompoundTag tag = be.saveWithFullMetadata(level.registryAccess());
		BlockState state = level.getBlockState(abs);
		level.removeBlockEntity(abs);
		BlockEntity again = BlockEntity.loadStatic(abs, state, tag, level.registryAccess());
		if (again == null) {
			throw new GameTestAssertException("reload: the block entity at " + pos.toShortString() + " didn't load from " + tag);
		}
		level.setBlockEntity(again);
	}

	private static int count(Container c, Item item) {
		int n = 0;
		for (int s = 0; s < c.getContainerSize(); s++) {
			if (item == null ? !c.getItem(s).isEmpty() : c.getItem(s).is(item)) {
				n += c.getItem(s).getCount();
			}
		}
		return n;
	}

	private static BlockState psi(String facing) {
		return MechanicCompatTests.with(MechanicCompatTests.create("portable_storage_interface").defaultBlockState(), "facing", facing);
	}

	private static void clearContraptions(GameTestHelper helper) {
		for (AbstractContraptionEntity e : helper.getLevel().getEntitiesOfClass(AbstractContraptionEntity.class, helper.getBounds().inflate(8))) {
			e.discard();
		}
	}

	/**
	 * Frank's station (chest, feeding hopper, interface, draining hopper, barrel) in his village; the stranger's piston
	 * outside pushes his chest (8 diamonds) and moving interface two steps west, to x = 13, docking across x = 12.
	 */
	static void psiDock(GameTestHelper helper, boolean protect, boolean reloadDocked) {
		VillageHallBlockEntity hall = hall(helper, protect);
		watchdog(helper, 500);
		for (int x = 9; x <= 20; x++) {
			for (int y = 2; y <= 4; y++) {
				helper.setBlock(new BlockPos(x, y, 15), Blocks.AIR);
			}
		}
		helper.setBlock(BARREL, Blocks.BARREL);
		helper.setBlock(DRAIN, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST));
		helper.setBlock(STATIONARY, psi("east"));
		helper.setBlock(FEEDER, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.EMERALD, 32));
		Container barrel = helper.getBlockEntity(BARREL);
		helper.setBlock(CART_CHEST, Blocks.CHEST);
		Container cartChest = helper.getBlockEntity(CART_CHEST);
		cartChest.setItem(0, new ItemStack(Items.DIAMOND, 32));
		helper.setBlock(MOVING, psi("west"));
		if (protect && VillageProtection.mayMachineChange(helper.getLevel(), helper.absolutePos(PISTON), helper.absolutePos(STATIONARY))) {
			done(helper, hall);
			helper.fail("set-up: the village isn't protected (a machine at the piston may change Frank's interface)");
		}
		QaCreateIntoProtectedVillage1107eCompatTests.piston(helper, PISTON);
		int[] moved = {0};
		helper.onEachTick(() -> {
			if (!helper.getLevel().getEntitiesOfClass(AbstractContraptionEntity.class, helper.getBounds().inflate(8)).isEmpty()) {
				moved[0] = 1;
			}
		});
		boolean[] reloaded = {!reloadDocked};
		int[] atReload = {-1};
		if (reloadDocked) {
			// a restart while the stranger's contraption stands docked at the edge: hall and Frank's interface saved and loaded
			helper.runAfterDelay(110, () -> {
				atReload[0] = count(chest, Items.EMERALD);
				reload(helper, HALL);
				reload(helper, STATIONARY);
				reloaded[0] = true;
			});
		}
		helper.runAfterDelay(450, () -> {
			int emeraldsLeft = count(chest, Items.EMERALD);
			int inBarrel = count(barrel, null);
			int diamondsInBarrel = count(barrel, Items.DIAMOND);
			float turning = speed(helper, PISTON);
			VillageHallBlockEntity now = helper.getBlockEntity(HALL);
			boolean stillProtected = now.isProtected();
			clearContraptions(helper);
			done(helper, now);
			helper.assertTrue(turning != 0, "set-up: the stranger's piston never turned");
			helper.assertTrue(moved[0] == 1, "set-up: the stranger's piston never put its contraption together");
			helper.assertTrue(reloaded[0], "set-up: the hall and interface were never saved and loaded");
			if (protect) {
				helper.assertTrue(stillProtected, "Frank's hall isn't protected any more after the save and reload");
				helper.assertTrue(emeraldsLeft == 32 && inBarrel == 0, "a stranger's contraption (piston outside Frank's protected village) docked at his Portable Storage Interface: "
					+ (32 - emeraldsLeft) + " of 32 emeralds left his chest into it and " + inBarrel + " items (" + diamondsInBarrel
					+ " of its 32 diamonds) arrived in his barrel" + (reloadDocked ? " (after a save and reload while docked)" : ""));
			} else {
				helper.assertTrue(emeraldsLeft < 32 && diamondsInBarrel > 0, "the control: in an open village the docked contraption traded nothing: "
					+ emeraldsLeft + " of 32 emeralds still in the chest, " + diamondsInBarrel + " diamonds (" + inBarrel + " items) in the barrel");
				if (reloadDocked) {
					helper.assertTrue(emeraldsLeft < atReload[0], "the control: after the save and reload the open village's interface traded no more ("
						+ atReload[0] + " emeralds at the reload, " + emeraldsLeft + " at the end)");
				}
			}
			helper.succeed();
		});
	}

	/** The control: in an open village the same docked contraption trades through Frank's interface both ways. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_psi_open"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_psi_open")
	public void aContraptionDockedAtAnOpenVillagesInterfaceTrades(GameTestHelper helper) {
		psiDock(helper, false, false);
	}

	/** The control: after the same save and reload an open village's interface trades with the docked contraption. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_psi_reloaded_open"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_psi_reloaded_open")
	public void aDockedContraptionStillTradesWithAnOpenVillageAfterAReload(GameTestHelper helper) {
		psiDock(helper, false, true);
	}

	// ---------------------------------------------------------------- 2. a stranger's Create train with a drill

	/** CreateTrainCompatTests.line's stations: the track runs along x at z = 12 from x = 1 to 28. */
	static final BlockPos STATION_A = new BlockPos(6, 2, 13);
	static final BlockPos STATION_B = new BlockPos(23, 2, 13);
	/** Frank's hall north of the track near the second station; 8 columns across, so the track's north side at x = 17..27 is his. */
	static final BlockPos TRAIN_HALL = new BlockPos(22, 2, 4);
	private static final java.util.UUID FRANK = java.util.UUID.fromString("5b1d2c3e-4f50-4a6b-8c7d-9e0f1a2b3c4d");
	private static final int TRAIN_REACH = 8;

	private static boolean inTrainVillage(BlockPos pos) {
		long dx = pos.getX() - TRAIN_HALL.getX();
		long dz = pos.getZ() - TRAIN_HALL.getZ();
		return dx * dx + dz * dz <= (long) TRAIN_REACH * TRAIN_REACH;
	}

	/**
	 * A stranger's real Create train (CreateTrains' drawn carriage on a bogey, glued, a conductor in the seat, assembled by
	 * the station at x = 6 and given the schedule to the station at x = 23) with a Mechanical Drill glued to its north side
	 * facing north, runs along the track into Frank's village. Oak planks stand in the drill's path: one outside the
	 * village (x = 12, the calibration: the drill works on this train) and five inside it (x = 18..22).
	 */
	static void drillTrain(GameTestHelper helper, boolean protect, boolean reloadHall) {
		ServerLevel level = helper.getLevel();
		int radiusBefore = io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS;
		CreateTrainCompatTests.line(helper);
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS = TRAIN_REACH;
		helper.setBlock(TRAIN_HALL, io.github.jcondedata.aliveworkplace.registry.ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(TRAIN_HALL);
		hall.setOwner(FRANK, "Frank");
		hall.setProtected(protect);
		helper.onEachTick(() -> {
			if (helper.getTick() >= 1195 && level.getBlockEntity(helper.absolutePos(TRAIN_HALL)) instanceof VillageHallBlockEntity h) {
				h.setProtected(false);
				io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS = radiusBefore;
			}
		});
		BlockPos a = helper.absolutePos(STATION_A);
		BlockPos b = helper.absolutePos(STATION_B);
		net.minecraft.world.entity.npc.Villager driver = CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		// a new stranger and new station names each run: a train or station left by an earlier batch (same owner, same name)
		// would otherwise be the one Create's schedule and Railway.at look at
		java.util.UUID stranger = java.util.UUID.randomUUID();
		String tag = stranger.toString().substring(0, 6);
		java.util.List<BlockPos> planks = new java.util.ArrayList<>();
		BlockPos[] outsidePlank = {null};
		int[] maxX = {Integer.MIN_VALUE};
		int[] phase = {0}; // 0 waiting for the network, 1 running, 2 checked
		int[] tick = {0};
		boolean[] reloaded = {!reloadHall};
		helper.onEachTick(() -> {
			tick[0]++;
			io.github.jcondedata.aliveworkplace.city.CaravanTrains.Railway r = CreateTrainCompatTests.railway();
			if (phase[0] == 0) {
				boolean aimed = r.aim(level, a, b, "QaAlpha" + tag) & r.aim(level, b, a, "QaBeta" + tag);
				if (!aimed || !r.linked(level, a, b)) {
					if (tick[0] > 300) {
						finishTrain(helper, hall, radiusBefore, r, driver, stranger);
						helper.fail("set-up: Create's railway doesn't know both stations on one network after 300 ticks (aimed " + aimed + ")");
					}
					return;
				}
				java.util.Map<BlockPos, BlockState> parts = r.train(level, a, b);
				if (parts == null) {
					finishTrain(helper, hall, radiusBefore, r, driver, stranger);
					helper.fail("set-up: no room for the train past the station");
					return;
				}
				parts.forEach((pos, state) -> level.setBlockAndUpdate(pos, state));
				// the drill: north of the train's north-most, front-most block above the bogey
				BlockPos side = null;
				for (BlockPos pos : parts.keySet()) {
					if (pos.getY() <= a.getY() || net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(parts.get(pos).getBlock()).getPath().endsWith("bogey")) {
						continue;
					}
					if (side == null || pos.getZ() < side.getZ() || (pos.getZ() == side.getZ() && (pos.getX() > side.getX()
						|| (pos.getX() == side.getX() && pos.getY() < side.getY())))) {
						side = pos;
					}
				}
				BlockPos drill = side.north();
				level.setBlockAndUpdate(drill, MechanicCompatTests.with(MechanicCompatTests.create("mechanical_drill").defaultBlockState(), "facing", "north"));
				net.minecraft.world.level.levelgen.structure.BoundingBox box = net.minecraft.world.level.levelgen.structure.BoundingBox
					.encapsulatingPositions(parts.keySet()).orElseThrow();
				level.addFreshEntity(new com.simibubi.create.content.contraptions.glue.SuperGlueEntity(level, net.minecraft.world.phys.AABB.encapsulatingFullBlocks(
					new BlockPos(box.minX(), box.minY(), Math.min(box.minZ(), drill.getZ())), new BlockPos(box.maxX(), box.maxY(), box.maxZ()))));
				BlockPos origin = helper.absolutePos(BlockPos.ZERO);
				int pz = drill.getZ() - 1 - origin.getZ();
				int py = drill.getY() - origin.getY();
				outsidePlank[0] = new BlockPos(12, py, pz);
				helper.setBlock(outsidePlank[0], Blocks.OAK_PLANKS);
				for (int x = 18; x <= 22; x++) {
					BlockPos p = new BlockPos(x, py, pz);
					if (inTrainVillage(p)) {
						planks.add(p);
						helper.setBlock(p, Blocks.OAK_PLANKS);
					}
				}
				if (planks.isEmpty() || inTrainVillage(outsidePlank[0])) {
					finishTrain(helper, hall, radiusBefore, r, driver, stranger);
					helper.fail("set-up: the planks aren't where the test wants them (drill at " + drill.subtract(origin).toShortString() + ")");
					return;
				}
				if (protect && VillageProtection.mayMachineChange(level, a, helper.absolutePos(planks.get(0)))) {
					finishTrain(helper, hall, radiusBefore, r, driver, stranger);
					helper.fail("set-up: the village isn't protected (a machine at the first station may change " + planks.get(0).toShortString() + ")");
					return;
				}
				if (r.assemble(level, a, b, stranger, driver) == null) {
					finishTrain(helper, hall, radiusBefore, r, driver, stranger);
					helper.fail("set-up: the station didn't assemble the stranger's train with its drill");
					return;
				}
				phase[0] = 1;
				return;
			}
			if (phase[0] != 1) {
				return;
			}
			for (AbstractContraptionEntity e : level.getEntitiesOfClass(AbstractContraptionEntity.class, helper.getBounds().inflate(8))) {
				maxX[0] = Math.max(maxX[0], e.blockPosition().getX() - helper.absolutePos(BlockPos.ZERO).getX());
			}
			if (!reloaded[0] && maxX[0] >= 13) {
				// a restart with the stranger's train standing just outside the village: the hall saved and loaded again
				reloaded[0] = true;
				reload(helper, TRAIN_HALL);
			}
			boolean arrived = r.at(level, stranger, b);
			if (arrived || tick[0] >= 1100) {
				phase[0] = 2;
				String status = r.status(level, stranger);
				boolean outsideBroken = helper.getBlockState(outsidePlank[0]).isAir();
				StringBuilder kept = new StringBuilder();
				int broken = 0;
				for (BlockPos p : planks) {
					boolean gone = !helper.getBlockState(p).is(Blocks.OAK_PLANKS);
					broken += gone ? 1 : 0;
					kept.append(p.getX()).append(gone ? ":broken " : ":kept ");
				}
				VillageHallBlockEntity now = helper.getBlockEntity(TRAIN_HALL);
				boolean stillProtected = now.isProtected();
				finishTrain(helper, now, radiusBefore, r, driver, stranger);
				helper.assertTrue(arrived, "set-up: the stranger's train didn't reach the second station (front-most x " + maxX[0] + "): " + status);
				helper.assertTrue(reloaded[0], "set-up: the hall was never saved and loaded");
				helper.assertTrue(outsideBroken, "set-up: the train's drill didn't break the plank outside the village at x = 12 (front-most x " + maxX[0] + ")");
				if (protect) {
					helper.assertTrue(stillProtected, "Frank's hall isn't protected any more after the save and reload");
					helper.assertTrue(broken == 0, "a stranger's Create train (assembled outside Frank's protected village) drove its drill into the village and broke "
						+ broken + " of " + planks.size() + " planks beside the track" + (reloadHall ? " (hall saved and reloaded on the way)" : "") + ": " + kept);
				} else {
					helper.assertTrue(broken == planks.size(), "the control: the train's drill broke only " + broken + " of " + planks.size()
						+ " planks beside the track in an open village: " + kept);
				}
				helper.succeed();
			}
		});
	}

	private static void finishTrain(GameTestHelper helper, VillageHallBlockEntity hall, int radiusBefore,
		io.github.jcondedata.aliveworkplace.city.CaravanTrains.Railway r, net.minecraft.world.entity.Entity driver, java.util.UUID stranger) {
		hall.setProtected(false);
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS = radiusBefore;
		r.scrap(helper.getLevel(), stranger);
		for (com.simibubi.create.content.contraptions.glue.SuperGlueEntity g : helper.getLevel().getEntitiesOfClass(
			com.simibubi.create.content.contraptions.glue.SuperGlueEntity.class, helper.getBounds().inflate(4))) {
			g.discard();
		}
		driver.discard();
	}

	/** A stranger's Create train carrying a drill, driven from its station outside into Frank's protected village, breaks nothing there. */
	//$ gametest_ticks_batch AREA '1200' '"qa_e1122_train_drill_protected"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_e1122_train_drill_protected")
	public void aStrangersDrillTrainRunningIntoAProtectedVillageBreaksNothing(GameTestHelper helper) {
		drillTrain(helper, true, false);
	}

	/** The control: the same train in an open village bores through every plank beside the track. */
	//$ gametest_ticks_batch AREA '1200' '"qa_e1122_train_drill_open"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_e1122_train_drill_open")
	public void aDrillTrainRunningIntoAnOpenVillageBreaksItsWay(GameTestHelper helper) {
		drillTrain(helper, false, false);
	}

	/** The hall saved and loaded while the stranger's drill train stands just outside: running on in, it still breaks nothing. */
	//$ gametest_ticks_batch AREA '1200' '"qa_e1122_train_drill_reloaded"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_e1122_train_drill_reloaded")
	public void aStrangersDrillTrainStillBreaksNothingAfterTheHallIsReloaded(GameTestHelper helper) {
		drillTrain(helper, true, true);
	}

	/** The control: after the same reload in an open village the train's drill breaks the planks. */
	//$ gametest_ticks_batch AREA '1200' '"qa_e1122_train_drill_reloaded_open"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_e1122_train_drill_reloaded_open")
	public void aDrillTrainAfterAReloadStillBoresThroughAnOpenVillage(GameTestHelper helper) {
		drillTrain(helper, false, true);
	}
}
