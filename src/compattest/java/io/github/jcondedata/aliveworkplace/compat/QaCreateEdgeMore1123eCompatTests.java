package io.github.jcondedata.aliveworkplace.compat;

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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * QA (lane-e-1123-1313): Create's machines at a protected village's edge, the cases earlier runs left "not tested".
 * Written from docs/wiki/create.md: "Create's machines keep out of a protected village the way a vanilla piston does:
 * one standing outside breaks, moves and places nothing inside", "a stranger's Elevator Pulley outside leaves the
 * village's blocks out of its cabin (B1487)", "Fluids stop at the edge too", "A stranger's contraption (on his piston or
 * cart) can't dock its Portable Storage Interface or Portable Fluid Interface with the village's own interface ... no
 * items or fluid pass either way" and "The same machines inside the village, or with its protection off, work as usual".
 *
 * <p>Frank's hall is {@link QaCreateDeployerUses1122eCompatTests#hall}'s: at (3, 2, 15), 8 columns across. Every
 * refusal has an open-village control on the same set-up that shows the machine really works.
 * <ol>
 *   <li>A 3x3 Fluid Tank straddling the edge (x = 10 to 12, z = 14 to 16; its controller at (10, 2, 14) inside, the cell
 *       (12, 2, 15) the stranger's pipe touches outside): the tank's water is one store, so its contents may be neither
 *       lost nor doubled whatever the pump does. Whether the stranger may drain it through an outside cell is B1546's
 *       family; that case lives in {@link QaCreateEdgeMore1123eBugCompatTests} while it fails.</li>
 *   <li>A moving elevator: the stranger's Elevator Pulley outside, its cabin called from the top floor to a lower
 *       Elevator Contact past Frank's wall, plank and poppy.</li>
 *   <li>A cart contraption carrying a Portable Fluid Interface and a tank of water past Frank's fluid interface dock.</li>
 *   <li>A Mechanical Piston outside pushing a slime block up, beside Frank's grass and his flower on dirt.</li>
 * </ol>
 */
public class QaCreateEdgeMore1123eCompatTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:huge_area";
	static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");
	static final long BUCKET = QaCreateHosePulley1122eCompatTests.BUCKET;

	static boolean inside(BlockPos p) {
		return QaCreateHosePulley1122eCompatTests.inside(p);
	}

	static BlockState create(String id) {
		return MechanicCompatTests.create(id).defaultBlockState();
	}

	static BlockState with(BlockState state, String key, String value) {
		return MechanicCompatTests.with(state, key, value);
	}

	static List<AbstractContraptionEntity> contraptions(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(AbstractContraptionEntity.class, helper.getBounds().inflate(8));
	}

	static void discardContraptions(GameTestHelper helper) {
		for (AbstractContraptionEntity e : contraptions(helper)) {
			e.discard();
		}
	}

	/** Turns the hall's protection off just before {@code timeout}, so a failing test leaves no protected village behind. */
	static void watchdog(GameTestHelper helper, int timeout) {
		helper.onEachTick(() -> {
			if (helper.getTick() >= timeout - 5) {
				QaCreateDeployerUses1122eCompatTests.undo(helper);
			}
		});
	}

	static void air(GameTestHelper helper, int x0, int x1, int y0, int y1, int z0, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
	}

	static void motorSpeed(GameTestHelper helper, BlockPos pos, int rpm) {
		BlockEntity be = helper.getBlockEntity(pos);
		CompoundTag tag = be.saveWithoutMetadata(helper.getLevel().registryAccess());
		tag.putInt("ScrollValue", rpm);
		be.loadWithComponents(tag, helper.getLevel().registryAccess());
		be.setChanged();
	}

	static int drops(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4)).size();
	}

	/** A pump at {@code pump} turned by a cogwheel at {@code cog} (axis {@code axis}) and a creative motor beyond it. */
	static void turnedPump(GameTestHelper helper, BlockPos pump, String flow, BlockPos cog, String axis, BlockPos motor, String motorFacing) {
		helper.setBlock(pump, with(create("mechanical_pump"), "facing", flow));
		helper.setBlock(cog, with(create("cogwheel"), "axis", axis));
		helper.runAfterDelay(2, () -> helper.setBlock(motor, with(create("creative_motor"), "facing", motorFacing)));
		// a pump turns itself round when its shaft's direction does; once it turns, set it the way the test wants it
		for (int t = 8; t <= 60; t += 4) {
			helper.runAfterDelay(t, () -> {
				BlockState state = helper.getBlockState(pump);
				if (speed(helper, pump) != 0 && !state.getValue(state.getBlock().getStateDefinition().getProperty("facing")).toString().equals(flow)) {
					helper.getLevel().setBlock(helper.absolutePos(pump), with(state, "facing", flow), 3);
				}
			});
		}
	}

	// ---------------------------------------------------------------- 1. a 3x3 tank straddling the edge

	static final BlockPos TANK_CONTROLLER = new BlockPos(10, 2, 14);
	/** The tank's cell the stranger's pipe touches: outside the village. */
	static final BlockPos TANK_EDGE = new BlockPos(12, 2, 15);
	static final BlockPos T_PIPE = TANK_EDGE.east();
	static final BlockPos T_PUMP = T_PIPE.east();
	static final BlockPos T_THEIRS = T_PUMP.east();
	static final long T_WATER = 36 * BUCKET;

	/**
	 * Frank's 3x3 Fluid Tank (36 buckets of water) straddling the edge, the stranger's Fluid Pipe on its outside cell, his
	 * Mechanical Pump draining it into his own tank. Checks after 300 ticks: {@code expectDrained} says whether water may
	 * leave Frank's tank; either way the water in both tanks adds up to the 36 buckets (nothing lost, nothing doubled).
	 */
	static void straddle(GameTestHelper helper, boolean protect, boolean expectDrained) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, protect);
		watchdog(helper, 400);
		air(helper, 7, 18, 2, 4, 12, 18);
		for (int x = 10; x <= 12; x++) {
			for (int z = 14; z <= 16; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
				helper.setBlock(new BlockPos(x, 2, z), create("fluid_tank"));
			}
		}
		helper.assertTrue(inside(TANK_CONTROLLER) && inside(new BlockPos(11, 2, 15)) && !inside(TANK_EDGE) && !inside(T_PIPE) && !inside(T_PUMP),
			"set-up: the tank doesn't straddle the village's edge");
		helper.setBlock(T_THEIRS, create("fluid_tank"));
		helper.setBlock(T_PIPE, QaCreatePipePackager1123eCompatTests.piece("fluid_pipe"));
		long[] start = {-1};
		helper.runAfterDelay(4, () -> {
			// the nine blocks join into one tank on their first ticks; fill it through its controller
			QaCreatePipePackager1123eCompatTests.fill(helper, TANK_CONTROLLER, Fluids.WATER, T_WATER);
			start[0] = QaCreatePipePackager1123eCompatTests.amount(helper, TANK_EDGE, Fluids.WATER);
			turnedPump(helper, T_PUMP, "east", T_PUMP.north(), "x", T_PUMP.north().east(), "west");
		});
		QaCreateCannonPipes1122eCompatTests.ruleSays(helper, protect, T_PUMP, TANK_CONTROLLER);
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 300, () -> {
			long franks = QaCreatePipePackager1123eCompatTests.amount(helper, TANK_CONTROLLER, Fluids.WATER);
			long franksEdge = QaCreatePipePackager1123eCompatTests.amount(helper, TANK_EDGE, Fluids.WATER);
			long theirs = QaCreatePipePackager1123eCompatTests.amount(helper, T_THEIRS, Fluids.WATER);
			String state = "Frank's 3x3 tank " + franks + " of " + T_WATER + " droplets (read at its outside cell: " + franksEdge + ", at the start: " + start[0]
				+ "), the stranger's tank " + theirs + " (pump Speed " + speed(helper, T_PUMP) + " " + helper.getBlockState(T_PUMP) + ")";
			LOG.info("QA edge-more straddle protect={}: {}", protect, state);
			helper.assertTrue(start[0] == T_WATER, "set-up: the nine tank blocks didn't join into one tank (its outside cell read " + start[0] + "): " + state);
			helper.assertTrue(speed(helper, T_PUMP) != 0, "set-up: the stranger's pump never turned: " + state);
			helper.assertTrue(franks == franksEdge, "Frank's 3x3 tank reads differently at its controller and its outside cell: " + state);
			helper.assertTrue(franks + theirs == T_WATER, "water was lost or doubled between Frank's straddling tank and the stranger's: " + state);
			if (expectDrained) {
				helper.assertTrue(franks < T_WATER && theirs > 0, "the control (village open): the stranger's pump moved no water out of the tank: " + state);
			} else {
				helper.assertTrue(franks == T_WATER && theirs == 0, "a stranger's Mechanical Pump outside Frank's protected village drained his 3x3 Fluid"
					+ " Tank (controller and four cells inside the village) through a Fluid Pipe on one of its outside cells: " + state);
			}
		});
	}

	/** The control: with the village open the stranger's pump drains the straddling tank, and no water is lost or doubled. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123x_straddle_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123x_straddle_open")
	public void aStrangersPumpDrainsATankStraddlingAnOpenVillagesEdgeWithoutLosingWater(GameTestHelper helper) {
		straddle(helper, false, true);
	}

	// ---------------------------------------------------------------- 2. a moving elevator

	/**
	 * The elevator on row z = 15: its pulley at {@code px} (13: just outside; 8: inside), the cabin hanging at y = 11
	 * (slime under the pulley, slime, oak plank at px - 2), a Redstone Contact on the cabin at (px, 11, 16) facing a
	 * stationary one at (px, 11, 17) (it becomes the top floor's Elevator Contact), an Elevator Contact at (px, 5, 17) for
	 * the lower floor. Frank's wall of stone bricks stands at x = px - 3 from y = 3 to 11 beside the cabin's way down, and
	 * his poppy on dirt at (px - 2, 3, 15) under it. At tick 70 a redstone block calls the cabin to the lower floor; at
	 * tick 260 the pulley is broken (the cabin is set down where it is); checked at tick 330.
	 */
	static void elevator(GameTestHelper helper, boolean protect, int px) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, protect);
		watchdog(helper, 400);
		int z = 15;
		air(helper, px - 3, px + 1, 2, 12, z - 1, z + 3);
		BlockPos pulley = new BlockPos(px, 12, z);
		BlockPos top = pulley.below();
		BlockPos mid = new BlockPos(px - 1, 11, z);
		BlockPos plank = new BlockPos(px - 2, 11, z);
		BlockPos cabinContact = new BlockPos(px, 11, z + 1);
		BlockPos topFloor = new BlockPos(px, 11, z + 2);
		BlockPos lowFloor = new BlockPos(px, 5, z + 2);
		BlockPos motor = new BlockPos(px + 1, 12, z);
		BlockPos poppy = new BlockPos(px - 2, 3, z);
		int wallX = px - 3;
		boolean outside = !inside(pulley);
		if (!inside(plank) || !inside(new BlockPos(wallX, 2, z)) || (outside == inside(mid))) {
			throw new GameTestAssertException("set-up: the cabin isn't where this test needs it");
		}
		helper.setBlock(pulley, with(create("elevator_pulley"), "facing", "north"));
		helper.setBlock(top, Blocks.SLIME_BLOCK);
		helper.setBlock(mid, Blocks.SLIME_BLOCK);
		helper.setBlock(plank, Blocks.OAK_PLANKS);
		helper.setBlock(cabinContact, with(create("redstone_contact"), "facing", "south"));
		helper.setBlock(topFloor, with(create("redstone_contact"), "facing", "north"));
		helper.setBlock(lowFloor, with(create("elevator_contact"), "facing", "north"));
		for (int y = 3; y <= 11; y++) {
			helper.setBlock(new BlockPos(wallX, y, z), Blocks.STONE_BRICKS);
		}
		helper.setBlock(poppy.below(), Blocks.DIRT);
		helper.setBlock(poppy, Blocks.POPPY);
		helper.setBlock(motor, with(create("creative_motor"), "facing", "west"));
		motorSpeed(helper, motor, 64);
		boolean may = VillageProtection.mayMachineChange(helper.getLevel(), helper.absolutePos(pulley), helper.absolutePos(plank));
		if (protect && outside && may || !outside && !may) {
			throw new GameTestAssertException("set-up: the village's protection against the pulley isn't what this test needs (may change the plank: " + may + ")");
		}
		boolean takesPlank = !(protect && outside);
		int[] seen = {0};
		double[] lowest = {Double.MAX_VALUE};
		int[] wallMissing = {0};
		boolean[] poppyGone = {false};
		boolean[] plankLeftWhileMoving = {false};
		helper.onEachTick(() -> {
			for (AbstractContraptionEntity e : contraptions(helper)) {
				seen[0] = 1;
				lowest[0] = Math.min(lowest[0], e.getY() - helper.absolutePos(BlockPos.ZERO).getY());
			}
			for (int y = 3; y <= 11; y++) {
				if (!helper.getBlockState(new BlockPos(wallX, y, z)).is(Blocks.STONE_BRICKS)) {
					wallMissing[0]++;
				}
			}
			poppyGone[0] |= !helper.getBlockState(poppy).is(Blocks.POPPY);
			if (helper.getTick() > 70 && helper.getTick() < 260) {
				plankLeftWhileMoving[0] |= !helper.getBlockState(plank).is(Blocks.OAK_PLANKS);
			}
		});
		helper.runAfterDelay(10, () -> {
			if (helper.getLevel().getBlockEntity(helper.absolutePos(pulley))
				instanceof com.simibubi.create.content.contraptions.elevator.ElevatorPulleyBlockEntity e) {
				e.clicked(); // what right-clicking the pulley does
			}
		});
		helper.runAfterDelay(70, () -> helper.setBlock(lowFloor.south(), Blocks.REDSTONE_BLOCK)); // call the lower floor
		helper.runAfterDelay(260, () -> helper.destroyBlock(pulley));
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 330, () -> {
			boolean plankHome = helper.getBlockState(plank).is(Blocks.OAK_PLANKS);
			boolean plankDown = helper.getBlockState(plank.below(6)).is(Blocks.OAK_PLANKS);
			boolean slimeDown = helper.getBlockState(top.below(6)).is(Blocks.SLIME_BLOCK) && helper.getBlockState(mid.below(6)).is(Blocks.SLIME_BLOCK);
			int items = drops(helper);
			discardContraptions(helper);
			String state = "pulley at " + pulley.toShortString() + (outside ? " (outside)" : " (inside)") + ", village " + (protect ? "protected" : "open")
				+ "; cabins seen " + seen[0] + ", the cabin's lowest y " + lowest[0] + "; its slime set down on the lower floor: " + slimeDown
				+ "; Frank's plank at " + plank.toShortString() + " now: " + plankHome + " (left it while the cabin moved: " + plankLeftWhileMoving[0]
				+ "), a plank on the lower floor: " + plankDown + "; wall blocks missing (summed over ticks) " + wallMissing[0] + ", poppy gone at some tick "
				+ poppyGone[0] + "; items on the ground " + items;
			LOG.info("QA edge-more elevator protect={} px={}: {}", protect, px, state);
			helper.assertTrue(seen[0] > 0, "set-up: the elevator never put its cabin together: " + state);
			helper.assertTrue(lowest[0] <= 6, "set-up: the cabin never travelled down to the lower floor: " + state);
			helper.assertTrue(slimeDown, "the elevator's cabin wasn't set down on the lower floor when its pulley broke: " + state);
			helper.assertTrue(wallMissing[0] == 0 && !poppyGone[0], "the moving elevator's cabin broke or carried Frank's wall or poppy beside its way: " + state);
			if (takesPlank) {
				helper.assertTrue(!plankHome && plankDown && items == 0, "the elevator didn't carry its whole cabin (with the plank) to the lower floor as before: " + state);
			} else {
				helper.assertTrue(plankHome && !plankLeftWhileMoving[0] && !plankDown && items == 0,
					"a stranger's moving elevator outside Frank's protected village carried, broke or set down the plank in the village's last cell: " + state);
			}
		});
	}

	/** The control: beside an open village the elevator takes its whole cabin down to the lower floor. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123x_elevator_move_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123x_elevator_move_open")
	public void anElevatorBesideAnOpenVillageCarriesItsWholeCabinToTheLowerFloor(GameTestHelper helper) {
		elevator(helper, false, 13);
	}

	/** A stranger's elevator outside Frank's protected village travels down and leaves his plank, wall and poppy alone. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123x_elevator_move_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123x_elevator_move_protected")
	public void aStrangersMovingElevatorLeavesAProtectedVillagesBlocksAlone(GameTestHelper helper) {
		elevator(helper, true, 13);
	}

	/** Frank's own elevator inside his protected village carries its whole cabin down as before. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123x_elevator_move_inside"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123x_elevator_move_inside")
	public void anElevatorInsideAProtectedVillageCarriesItsWholeCabinDown(GameTestHelper helper) {
		elevator(helper, true, 8);
	}

	// ---------------------------------------------------------------- 3. a cart's Portable Fluid Interface

	/** Frank's dock: his fluid interface (facing the track), his pump pulling from it into his empty tank, all inside. */
	static final BlockPos F_DOCK = new BlockPos(3, 3, 9);
	static final BlockPos F_PUMP = new BlockPos(3, 3, 8);
	static final BlockPos F_TANK = new BlockPos(3, 3, 7);

	/**
	 * A cart (the stranger's put together at x = 22 outside, or Frank's at x = 9 inside) carrying a tank of 8 buckets of
	 * water and a Portable Fluid Interface facing north rolls along {@link QaCreatePsi1122eCompatTests}'s track past
	 * Frank's dock. {@code expectFlow}: water must reach Frank's tank; otherwise none may.
	 */
	static void cart(GameTestHelper helper, boolean protect, int ax, boolean expectFlow) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, protect);
		watchdog(helper, 500);
		QaCreatePsi1122eCompatTests.line(helper, ax);
		BlockPos assembler = new BlockPos(ax, 2, QaCreatePsi1122eCompatTests.TZ);
		helper.setBlock(assembler.above().above(), create("fluid_tank"));
		helper.setBlock(assembler.above().north(), with(create("portable_fluid_interface"), "facing", "north"));
		air(helper, 2, 5, 3, 4, 6, 10);
		helper.setBlock(F_DOCK, with(create("portable_fluid_interface"), "facing", "south"));
		helper.setBlock(F_TANK, create("fluid_tank"));
		turnedPump(helper, F_PUMP, "north", F_PUMP.east(), "z", F_PUMP.east().north(), "south");
		helper.assertTrue(inside(F_DOCK) && inside(F_PUMP) && inside(F_TANK), "set-up: Frank's dock isn't inside his village");
		boolean owner = inside(assembler);
		boolean may = VillageProtection.mayMachineChange(helper.getLevel(), helper.absolutePos(assembler), helper.absolutePos(F_TANK));
		helper.assertTrue(may == (!protect || owner), "set-up: the village's protection judges the assembler at x = " + ax + " wrongly (may: " + may + ")");
		helper.runAfterDelay(2, () -> QaCreatePipePackager1123eCompatTests.fill(helper, assembler.above().above(), Fluids.WATER, 8 * BUCKET));
		int[] least = {Integer.MAX_VALUE};
		int[] put = {0};
		helper.onEachTick(() -> {
			for (AbstractContraptionEntity e : contraptions(helper)) {
				put[0] = 1;
				least[0] = Math.min(least[0], e.blockPosition().getX() - helper.absolutePos(BlockPos.ZERO).getX());
			}
		});
		helper.runAfterDelay(65, () -> QaCreatePsi1122eCompatTests.rollCart(helper, ax));
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 460, () -> {
			long franks = QaCreatePipePackager1123eCompatTests.amount(helper, F_TANK, Fluids.WATER);
			String state = (owner ? "Frank's own cart (put together inside)" : "a stranger's cart (put together outside)") + ", village "
				+ (protect ? "protected" : "open") + ": Frank's tank " + franks + " droplets; the cart reached x = " + least[0] + " (dock at 3); pump Speed "
				+ speed(helper, F_PUMP) + " " + helper.getBlockState(F_PUMP);
			QaCreatePsi1122eCompatTests.clearCarts(helper);
			LOG.info("QA edge-more cart pfi protect={} ax={}: {}", protect, ax, state);
			helper.assertTrue(put[0] == 1, "set-up: the Cart Assembler never put the tank and fluid interface on the cart: " + state);
			helper.assertTrue(least[0] <= 4, "set-up: the cart never reached Frank's dock: " + state);
			helper.assertTrue(speed(helper, F_PUMP) != 0, "set-up: Frank's pump never turned: " + state);
			if (expectFlow) {
				helper.assertTrue(franks > 0, "the cart docked at Frank's fluid interface but no water reached his tank: " + state);
			} else {
				helper.assertTrue(franks == 0, "a stranger's cart docked its Portable Fluid Interface at Frank's in his protected village and water passed: " + state);
			}
		});
	}

	/** The control: in an open village the stranger's cart docks and its water reaches Frank's tank. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1123x_cart_pfi_open"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1123x_cart_pfi_open")
	public void aCartsFluidInterfaceDocksAtAnOpenVillagesInterface(GameTestHelper helper) {
		cart(helper, false, QaCreatePsi1122eCompatTests.STRANGER_AX, true);
	}

	/** A stranger's cart passes Frank's fluid interface in his protected village and no water passes. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1123x_cart_pfi_protected"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1123x_cart_pfi_protected")
	public void aStrangersCartPassesNoFluidThroughAProtectedVillagesInterface(GameTestHelper helper) {
		cart(helper, true, QaCreatePsi1122eCompatTests.STRANGER_AX, false);
	}

	/** Frank's own cart, put together inside his protected village, docks at his fluid interface as before. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1123x_cart_pfi_owner"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1123x_cart_pfi_owner")
	public void theOwnersCartDocksAtHisFluidInterfaceInHisProtectedVillage(GameTestHelper helper) {
		cart(helper, true, QaCreatePsi1122eCompatTests.OWNER_AX, true);
	}

	// ---------------------------------------------------------------- 4. a piston beside the village's flower and grass

	/** The stranger's piston column: x = 11, z = 14 (outside; (11, 15) and (10, 14) beside it are inside). */
	static final BlockPos P_PISTON = new BlockPos(11, 5, 14);
	static final BlockPos P_LOAD = P_PISTON.above();
	static final BlockPos P_GRASS = P_LOAD.south();
	static final BlockPos P_DIRT = P_LOAD.west();

	/**
	 * The stranger's Mechanical Piston at {@link #P_PISTON} facing up with two poles under it and a motor east of it; on
	 * it a {@code load} block (slime glues its neighbours, a plank doesn't). Beside the load inside the village: Frank's
	 * grass block with short grass on it (south) and his dirt with a poppy on it (west). After 200 ticks:
	 * {@code expectTaken} says whether the village's blocks went up with the load; {@code expectMoved} whether the load
	 * itself rose.
	 */
	static void piston(GameTestHelper helper, boolean protect, net.minecraft.world.level.block.Block load, boolean expectTaken, boolean expectMoved) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, protect);
		watchdog(helper, 300);
		air(helper, 9, 13, 2, 10, 12, 16);
		helper.assertTrue(!inside(P_PISTON) && !inside(P_LOAD) && inside(P_GRASS) && inside(P_DIRT), "set-up: the piston isn't at the village's edge");
		helper.setBlock(P_PISTON.below(2), with(create("piston_extension_pole"), "facing", "up"));
		helper.setBlock(P_PISTON.below(), with(create("piston_extension_pole"), "facing", "up"));
		helper.setBlock(P_LOAD, load);
		helper.setBlock(P_GRASS, Blocks.GRASS_BLOCK);
		helper.setBlock(P_GRASS.above(), Blocks.SHORT_GRASS);
		helper.setBlock(P_DIRT, Blocks.DIRT);
		helper.setBlock(P_DIRT.above(), Blocks.POPPY);
		helper.setBlock(P_PISTON, with(with(create("mechanical_piston"), "facing", "up"), "axis_along_first", "true"));
		helper.runAfterDelay(2, () -> helper.setBlock(P_PISTON.east(), with(create("creative_motor"), "facing", "west")));
		QaCreateCannonPipes1122eCompatTests.ruleSays(helper, protect, P_PISTON, P_GRASS);
		boolean[] moved = {false};
		helper.onEachTick(() -> moved[0] |= !contraptions(helper).isEmpty());
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 200, () -> {
			boolean grassHome = helper.getBlockState(P_GRASS).is(Blocks.GRASS_BLOCK) && helper.getBlockState(P_GRASS.above()).is(Blocks.SHORT_GRASS);
			boolean poppyHome = helper.getBlockState(P_DIRT).is(Blocks.DIRT) && helper.getBlockState(P_DIRT.above()).is(Blocks.POPPY);
			boolean loadHome = helper.getBlockState(P_LOAD).is(load);
			boolean loadUp = false;
			boolean grassUp = false;
			boolean poppyUp = false;
			boolean dirtUp = false;
			for (AbstractContraptionEntity e : contraptions(helper)) {
				for (var info : e.getContraption().getBlocks().values()) {
					loadUp |= info.state().is(load);
					grassUp |= info.state().is(Blocks.GRASS_BLOCK);
					poppyUp |= info.state().is(Blocks.POPPY);
					dirtUp |= info.state().is(Blocks.DIRT);
				}
			}
			for (int dy = 1; dy <= 3; dy++) {
				loadUp |= helper.getBlockState(P_LOAD.above(dy)).is(load);
				grassUp |= helper.getBlockState(P_GRASS.above(dy)).is(Blocks.GRASS_BLOCK);
				dirtUp |= helper.getBlockState(P_DIRT.above(dy)).is(Blocks.DIRT);
				poppyUp |= helper.getBlockState(P_DIRT.above(dy + 1)).is(Blocks.POPPY);
			}
			int items = drops(helper);
			String state = "village " + (protect ? "protected" : "open") + ", load " + load + ": contraption seen " + moved[0] + ", load at home " + loadHome
				+ ", load risen " + loadUp + "; Frank's grass at home " + grassHome + ", risen " + grassUp + "; his poppy on dirt at home " + poppyHome
				+ " (dirt risen " + dirtUp + ", poppy risen " + poppyUp + "); items on the ground " + items + "; piston Speed " + speed(helper, P_PISTON);
			discardContraptions(helper);
			LOG.info("QA edge-more piston protect={} load={}: {}", protect, load, state);
			helper.assertTrue(speed(helper, P_PISTON) != 0 || moved[0], "set-up: the piston never turned: " + state);
			if (expectMoved) {
				helper.assertTrue(moved[0] && loadUp && !loadHome, "the piston didn't push its own block up: " + state);
			}
			if (expectTaken) {
				// Create takes the dirt and grass glued to the slime; the poppy on the dirt may pop off as an item on the way
				helper.assertTrue(grassUp && dirtUp && !grassHome && !poppyHome, "the control (village open): the slime block didn't take the blocks glued to it: " + state);
			} else {
				helper.assertTrue(grassHome && poppyHome && !grassUp && !dirtUp && !poppyUp && items == 0,
					"a stranger's Mechanical Piston outside Frank's protected village moved or broke his grass or his flower beside its load: " + state);
			}
		});
	}

	/** The control: in an open village the slime block takes Frank's grass and his dirt up with it (the poppy on the dirt rides along or pops off). */
	//$ gametest_ticks_batch AREA '300' '"qa_e1123x_piston_slime_open"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_e1123x_piston_slime_open")
	public void aSlimeBlockOnAPistonTakesTheGrassAndFlowerBesideItInAnOpenVillage(GameTestHelper helper) {
		piston(helper, false, Blocks.SLIME_BLOCK, true, true);
	}

	/** A stranger's slime-block piston outside a protected village leaves Frank's grass and flower where they are. */
	//$ gametest_ticks_batch AREA '300' '"qa_e1123x_piston_slime_protected"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_e1123x_piston_slime_protected")
	public void aStrangersSlimePistonLeavesAProtectedVillagesGrassAndFlower(GameTestHelper helper) {
		piston(helper, true, Blocks.SLIME_BLOCK, false, false);
	}

	/** A stranger's piston outside a protected village still pushes its plank up, and the grass and flower beside it stay. */
	//$ gametest_ticks_batch AREA '300' '"qa_e1123x_piston_plank_protected"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_e1123x_piston_plank_protected")
	public void aStrangersPistonStillPushesItsPlankBesideAProtectedVillagesFlower(GameTestHelper helper) {
		piston(helper, true, Blocks.OAK_PLANKS, false, true);
	}
}
