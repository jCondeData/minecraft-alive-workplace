package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.speed;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * QA (lane-e-1122-2213, Create and the pack), from the protection promise (wiki create.md: "a stranger's Create machine
 * standing outside a protected village breaks, moves and places nothing inside; the same machines inside the village, or
 * with protection off, work as usual"; village-hall.md, protection) on two Create paths no earlier test drove:
 *
 * <ol>
 *   <li><b>Tree felling.</b> A Mechanical Saw that cuts a tree's bottom log fells the whole tree (Create's TreeCutter
 *       finds every connected log and leaf and breaks them at once). Earlier tests only had the saw cut a lone log at
 *       the edge. Here a stranger's oak stands outside Frank's village (trunk at x = 14, z = 15) with a long branch
 *       reaching west over the edge (logs x = 9..13 at y = 5, the cells x = 9..11 inside) and leaves on it inside. The
 *       saw (a fixed saw facing the trunk, or one on the head of a stranger's Mechanical Piston) cuts the trunk outside;
 *       protected, no log or leaf in the village's cells may go. The control: with the village open the whole tree
 *       falls.</li>
 *   <li><b>Portable Fluid Interface.</b> Frank's interface in his village's last column (x = 11) with his Fluid Tank
 *       behind it and his Mechanical Pump between them pushing into the interface (an unloading/loading station); a
 *       stranger's Mechanical Piston outside pushes his Fluid Tank and moving Portable Fluid Interface to x = 13, docking
 *       across the gap (the geometry of QaCreatePortableTrains1122e's item interface, B1488). Protected, Frank's water
 *       must stay in his tank; open, it flows into the stranger's contraption.</li>
 *   <li><b>A Deployer with bone meal.</b> A stranger's deployer outside facing an oak sapling in the village's last
 *       column: protected, the sapling doesn't grow; open, the deployer spends bone meal on it.</li>
 * </ol>
 *
 * Frank's hall is {@link QaCreateDeployerUses1122eCompatTests#hall}'s: at (3, 2, 15), the village 8 columns across. The
 * protected felling and fluid-interface cases fail today (found bugs, 3 of 3 runs: the whole tree falls, and water leaves
 * Frank's tank) and live in QaCreateFellingFluids1122eBugCompatTests (on tests/create-felling-fluids-1122e), not
 * registered until the fix; this class keeps their controls and the bone meal case.
 */
public class QaCreateFellingFluids1122eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	static final int Z = 15;

	static boolean inside(BlockPos p) {
		return QaCreateHosePulley1122eCompatTests.inside(p);
	}

	static BlockState create(String id) {
		return MechanicCompatTests.create(id).defaultBlockState();
	}

	// ---------------------------------------------------------------- 1. tree felling over the edge

	static final BlockPos TRUNK = new BlockPos(14, 2, Z);
	static final BlockPos SAW = TRUNK.east();

	/** The stranger's oak: trunk x = 14 y 2..6, branch y = 5 x = 9..13, leaves on the branch's inner end and the trunk's top. */
	static List<BlockPos> tree(GameTestHelper helper) {
		List<BlockPos> blocks = new ArrayList<>();
		for (int x = 6; x <= 18; x++) {
			for (int y = 2; y <= 8; y++) {
				for (int z = Z - 1; z <= Z + 1; z++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		for (int y = 2; y <= 6; y++) {
			BlockPos p = new BlockPos(TRUNK.getX(), y, Z);
			helper.setBlock(p, Blocks.OAK_LOG);
			blocks.add(p);
		}
		for (int x = 9; x <= 13; x++) {
			BlockPos p = new BlockPos(x, 5, Z);
			helper.setBlock(p, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
			blocks.add(p);
		}
		BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
		for (BlockPos p : List.of(new BlockPos(9, 6, Z), new BlockPos(10, 6, Z), new BlockPos(8, 5, Z), new BlockPos(9, 5, Z - 1),
			new BlockPos(14, 7, Z), new BlockPos(13, 6, Z))) {
			helper.setBlock(p, leaf);
			blocks.add(p);
		}
		return blocks;
	}

	/** An oak log or leaf still at {@code p} (the piston's saw moving into the cut trunk's cell isn't the tree). */
	static boolean treePart(GameTestHelper helper, BlockPos p) {
		BlockState state = helper.getBlockState(p);
		return state.is(Blocks.OAK_LOG) || state.is(Blocks.OAK_LEAVES);
	}

	static String standing(GameTestHelper helper, List<BlockPos> blocks, boolean in) {
		StringBuilder out = new StringBuilder();
		for (BlockPos p : blocks) {
			if (inside(p) == in) {
				out.append(p.toShortString()).append(treePart(helper, p) ? " kept; " : " gone; ");
			}
		}
		return out.toString();
	}

	static void felling(GameTestHelper helper, boolean protect, boolean onPiston) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, protect);
		List<BlockPos> blocks = tree(helper);
		BlockPos machine;
		if (onPiston) {
			machine = SAW.east();
			QaCreateIntoProtectedVillage1107eCompatTests.piston(helper, machine);
			helper.setBlock(SAW, MechanicCompatTests.with(create("mechanical_saw"), "facing", "west"));
		} else {
			machine = SAW;
			helper.setBlock(SAW.east(), MechanicCompatTests.with(create("creative_motor"), "facing", "west"));
			helper.setBlock(SAW, MechanicCompatTests.with(create("mechanical_saw"), "facing", "west"));
		}
		List<BlockPos> in = blocks.stream().filter(QaCreateFellingFluids1122eCompatTests::inside).toList();
		helper.assertTrue(!inside(TRUNK) && !inside(machine) && in.size() >= 6, "set-up: the tree isn't across the village's edge (" + in.size() + " cells inside)");
		boolean may = VillageProtection.mayMachineChange(helper.getLevel(), helper.absolutePos(machine), helper.absolutePos(in.get(0)));
		helper.assertTrue(may != protect, "set-up: the protection rule says a machine at " + machine.toShortString() + (may ? " may" : " may not")
			+ " change " + in.get(0).toShortString() + " with the village " + (protect ? "protected" : "open"));
		String who = onPiston ? "a Mechanical Saw on a stranger's Mechanical Piston" : "a stranger's Mechanical Saw";
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 300, () -> {
			float turning = speed(helper, machine);
			boolean trunkCut = !helper.getBlockState(TRUNK).is(Blocks.OAK_LOG);
			long keptIn = in.stream().filter(p -> treePart(helper, p)).count();
			long keptOut = blocks.stream().filter(p -> !inside(p) && treePart(helper, p)).count();
			for (AbstractContraptionEntity e : helper.getLevel().getEntitiesOfClass(AbstractContraptionEntity.class, helper.getBounds().inflate(8))) {
				e.discard();
			}
			helper.assertTrue(turning != 0, "set-up: the " + (onPiston ? "piston" : "saw") + " never turned, so the test proves nothing");
			helper.assertTrue(trunkCut, "set-up: " + who + " didn't cut the trunk outside the village in 300 ticks (" + helper.getBlockState(TRUNK) + ")");
			if (protect) {
				helper.assertTrue(keptIn == in.size(), who + " outside Frank's protected village cut a tree whose branch reaches over the edge, and the"
					+ " felling took " + (in.size() - keptIn) + " of the " + in.size() + " logs and leaves inside the village: " + standing(helper, blocks, true));
			} else {
				helper.assertTrue(keptIn == 0 && keptOut == 0, "the control (village open): cutting the trunk didn't fell the whole tree: inside "
					+ standing(helper, blocks, true) + "outside " + standing(helper, blocks, false));
			}
		});
	}

	/** The control: with the village open, the fixed saw fells the whole tree, branch and leaves over the edge included. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_fell_saw_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_fell_saw_open")
	public void aSawFellsATreeReachingIntoAnOpenVillage(GameTestHelper helper) {
		felling(helper, false, false);
	}

	/** The control: with the village open, the piston-carried saw fells the whole tree. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_fell_piston_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_fell_piston_open")
	public void aPistonSawFellsATreeReachingIntoAnOpenVillage(GameTestHelper helper) {
		felling(helper, false, true);
	}

	// ---------------------------------------------------------------- 2. a Portable Fluid Interface docked at the edge

	/** Frank's interface in the village's last column facing east; his pump west of it, his tank west of that. */
	static final BlockPos STATIONARY = new BlockPos(11, 2, Z);
	static final BlockPos PUMP = STATIONARY.west();
	static final BlockPos TANK = PUMP.west();
	static final BlockPos COG = PUMP.north();
	/** The stranger's moving interface (x = 13 after two steps), his tank behind it, his piston behind that. */
	static final BlockPos MOVING = new BlockPos(15, 2, Z);
	static final BlockPos CART_TANK = new BlockPos(16, 2, Z);
	static final BlockPos PISTON = new BlockPos(17, 2, Z);
	static final long BUCKET = QaCreateHosePulley1122eCompatTests.BUCKET;

	static long amount(GameTestHelper helper, BlockPos pos) {
		return QaPumpHouseTank1121eCompatTests.amount(helper.getLevel(), helper.absolutePos(pos));
	}

	static void fluidDock(GameTestHelper helper, boolean protect) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, protect);
		for (int x = 7; x <= 20; x++) {
			for (int y = 2; y <= 4; y++) {
				for (int z = Z - 1; z <= Z; z++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(TANK, create("fluid_tank"));
		helper.setBlock(STATIONARY, MechanicCompatTests.with(create("portable_fluid_interface"), "facing", "east"));
		helper.setBlock(PUMP, MechanicCompatTests.with(create("mechanical_pump"), "facing", "east"));
		helper.setBlock(COG, MechanicCompatTests.with(create("cogwheel"), "axis", "x"));
		helper.runAfterDelay(2, () -> helper.setBlock(COG.east(), MechanicCompatTests.with(create("creative_motor"), "facing", "west")));
		// a pump turns itself round when its shaft's direction does; once it turns, set it pushing east (tank to interface)
		for (int t = 8; t <= 60; t += 4) {
			helper.runAfterDelay(t, () -> {
				BlockState state = helper.getBlockState(PUMP);
				if (speed(helper, PUMP) != 0 && !state.getValue(state.getBlock().getStateDefinition().getProperty("facing")).toString().equals("east")) {
					helper.getLevel().setBlock(helper.absolutePos(PUMP), MechanicCompatTests.with(state, "facing", "east"), 3);
				}
			});
		}
		Storage<FluidVariant> franks = FluidStorage.SIDED.find(helper.getLevel(), helper.absolutePos(TANK), Direction.UP);
		helper.assertTrue(franks != null, "set-up: Frank's Fluid Tank has no fluid storage");
		long put;
		try (Transaction tx = Transaction.openOuter()) {
			put = franks.insert(FluidVariant.of(Fluids.WATER), 8 * BUCKET, tx);
			tx.commit();
		}
		helper.assertTrue(put == 8 * BUCKET, "set-up: Frank's tank took only " + put + " droplets of 8 buckets");
		helper.setBlock(CART_TANK, create("fluid_tank"));
		helper.setBlock(MOVING, MechanicCompatTests.with(create("portable_fluid_interface"), "facing", "west"));
		helper.assertTrue(inside(STATIONARY) && inside(TANK) && !inside(STATIONARY.east()), "set-up: Frank's station isn't at the village's edge");
		boolean may = VillageProtection.mayMachineChange(helper.getLevel(), helper.absolutePos(PISTON), helper.absolutePos(STATIONARY));
		helper.assertTrue(may != protect, "set-up: the protection rule says a machine at the piston" + (may ? " may" : " may not")
			+ " change Frank's interface with the village " + (protect ? "protected" : "open"));
		QaCreateIntoProtectedVillage1107eCompatTests.piston(helper, PISTON);
		int[] moved = {0};
		helper.onEachTick(() -> {
			if (moved[0] == 0 && !helper.getLevel().getEntitiesOfClass(AbstractContraptionEntity.class, helper.getBounds().inflate(8)).isEmpty()) {
				moved[0] = (int) helper.getTick();
			}
		});
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 400, () -> {
			long left = amount(helper, TANK);
			float pistonTurning = speed(helper, PISTON);
			float pumpTurning = speed(helper, PUMP);
			String pump = helper.getBlockState(PUMP).toString();
			for (AbstractContraptionEntity e : helper.getLevel().getEntitiesOfClass(AbstractContraptionEntity.class, helper.getBounds().inflate(8))) {
				e.discard();
			}
			helper.assertTrue(pistonTurning != 0 && moved[0] > 0, "set-up: the stranger's piston never put its contraption together (speed " + pistonTurning + ")");
			helper.assertTrue(pumpTurning != 0, "set-up: Frank's pump never turned (" + pump + ")");
			if (protect) {
				helper.assertTrue(left == 8 * BUCKET, "a stranger's contraption (piston outside Frank's protected village) docked at his Portable Fluid Interface: "
					+ (8 * BUCKET - left) + " of " + 8 * BUCKET + " droplets (8 buckets) of water left Frank's tank into it");
			} else {
				helper.assertTrue(left < 8 * BUCKET, "the control (village open): the docked contraption took no water through Frank's interface ("
					+ left + " droplets still in his tank, pump " + pump + ")");
			}
		});
	}

	/** The control: in an open village the docked contraption fills from Frank's tank through his interface. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_pfi_open"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_pfi_open")
	public void aContraptionDockedAtAnOpenVillagesFluidInterfaceFills(GameTestHelper helper) {
		fluidDock(helper, false);
	}

	// ---------------------------------------------------------------- 3. a Deployer with bone meal

	/** The stranger's deployer at x = 13 facing west reaches x = 11, the village's last column (QaCreateDeployerUses1122e's). */
	static final BlockPos DEPLOYER = QaCreateDeployerUses1122eCompatTests.DEPLOYER;
	static final BlockPos SAPLING = QaCreateDeployerUses1122eCompatTests.TARGET;

	static void boneMeal(GameTestHelper helper, boolean protect) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, protect);
		for (int x = 7; x <= 15; x++) {
			for (int y = 2; y <= 12; y++) {
				for (int z = Z - 3; z <= Z + 3; z++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(SAPLING.below(), Blocks.DIRT);
		helper.setBlock(SAPLING, Blocks.OAK_SAPLING);
		helper.assertTrue(inside(SAPLING) && !inside(SAPLING.east()), "set-up: the sapling isn't in the village's last column");
		boolean may = VillageProtection.mayMachineChange(helper.getLevel(), helper.absolutePos(DEPLOYER), helper.absolutePos(SAPLING));
		helper.assertTrue(may != protect, "set-up: the protection rule says the deployer" + (may ? " may" : " may not")
			+ " change the sapling with the village " + (protect ? "protected" : "open"));
		QaCreateDeployerOnOurThings1102eCompatTests.deployer(helper, DEPLOYER, QaCreateDeployerUses1122eCompatTests.STRANGER,
			new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE_MEAL, 64));
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 300, () -> {
			float turning = speed(helper, DEPLOYER);
			BlockState now = helper.getBlockState(SAPLING);
			int held = QaCreateDeployerOnOurThings1102eCompatTests.held(helper, DEPLOYER).getCount();
			helper.assertTrue(turning != 0, "set-up: the deployer never turned, so the test proves nothing");
			if (protect) {
				helper.assertTrue(now.is(Blocks.OAK_SAPLING) && now.getValue(net.minecraft.world.level.block.SaplingBlock.STAGE) == 0,
					"a stranger's Deployer with bone meal, outside Frank's protected village, grew the oak sapling in its last column: now " + now + " (holding " + held + " bone meal)");
			} else {
				// each use spends one bone meal and grows the sapling only by chance (45%: a stage, then the tree), so either
				// shows the deployer's bone meal reached the sapling
				helper.assertTrue(held < 64 || !now.is(Blocks.OAK_SAPLING) || now.getValue(net.minecraft.world.level.block.SaplingBlock.STAGE) > 0,
					"the control (village open): the stranger's deployer spent no bone meal on the sapling in 300 ticks: now " + now + ", holding " + held);
			}
		});
	}

	/** A stranger's deployer with bone meal outside a protected village doesn't grow the sapling at its edge. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_bonemeal_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_bonemeal_protected")
	public void aStrangersDeployerGrowsNoSaplingInAProtectedVillage(GameTestHelper helper) {
		boneMeal(helper, true);
	}

	/** The control: with the village open the same deployer's bone meal grows the sapling (a stage, or into a tree). */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_bonemeal_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_bonemeal_open")
	public void aStrangersDeployerGrowsASaplingInAnOpenVillage(GameTestHelper helper) {
		boneMeal(helper, false);
	}
}
