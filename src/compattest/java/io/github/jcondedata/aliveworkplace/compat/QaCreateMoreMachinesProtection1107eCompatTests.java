package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.EDGE;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.done;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.hall;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.speed;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * QA (lane-e-1107-1313, Create and the pack): more of Create's machines at the edge of a protected village, the ones
 * B993 and B994 left "not tested": a Mechanical Saw, a Deployer and a Mechanical Arm, each standing outside the village
 * and reaching into its last column.
 *
 * <p>village-hall.md: with protection on, "only the owner, their friends and operators may break or place blocks" and
 * open chests in the hall's area, and (B745) machines outside stop at its edge, as land-claim mods do. The village is
 * the one of {@link QaCreateIntoProtectedVillage1107eCompatTests}: on the hall's row x = 11 ({@code EDGE}) is its last
 * column and x = 12 is outside. Every machine has a control in the same set-up with the village open, which proves the
 * machine really reaches the edge (so "nothing changed" with protection on means the protection held). With protection
 * on, all three reach in (the saw and the arm are B993's and B994's family; the deployer is B844, which places into air
 * unchecked anywhere): those tests are in the unregistered {@link QaCreateMoreMachinesProtection1107eBugCompatTests}
 * until the bug lane fixes them.
 */
public class QaCreateMoreMachinesProtection1107eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	// ---------------------------------------------------------------- set-ups

	/** A Mechanical Saw at {@code saw} facing west (it cuts the block west of it), a Creative Motor at its back. */
	static void saw(GameTestHelper helper, BlockPos saw) {
		helper.setBlock(saw.east(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "west"));
		helper.setBlock(saw, MechanicCompatTests.with(MechanicCompatTests.create("mechanical_saw").defaultBlockState(), "facing", "west"));
	}

	/** The Deployer stands two blocks east of the edge, facing west: it acts on the edge (x = 11) from x = 13, outside. */
	static final BlockPos DEPLOYER = EDGE.east(2);

	/** A stranger's (nobody's) Deployer holding one cobblestone, which it would place at {@link #EDGE}. */
	static void deployer(GameTestHelper helper) {
		QaCreateDeployerOnOurThings1102eCompatTests.deployer(helper, DEPLOYER, null, new ItemStack(Items.COBBLESTONE, 1));
	}

	/** The Mechanical Arm, two blocks east of the edge (outside). */
	static final BlockPos ARM = EDGE.east(2);
	/** Where the arm drops what it takes: a Depot east of it, outside the village. */
	static final BlockPos OUT_DEPOT = ARM.east(2);

	/**
	 * Frank's Depot at {@link #EDGE} holding 16 diamonds (an arm can't reach into a chest, only into Create's own item
	 * holders such as a depot, belt or basin), an empty Depot at {@link #OUT_DEPOT}, and a Mechanical Arm at {@link #ARM}
	 * told (as a player does by clicking the two with the arm item before placing it) to take from the first and put into
	 * the second.
	 */
	static void depotsAndArm(GameTestHelper helper) {
		helper.setBlock(EDGE, MechanicCompatTests.create("depot"));
		helper.setBlock(OUT_DEPOT, MechanicCompatTests.create("depot"));
		ServerLevel level = helper.getLevel();
		long in = QaCreateDeployerOnOurThings1102eCompatTests.insert(level, helper.absolutePos(EDGE), new ItemStack(Items.DIAMOND, 16));
		if (in != 16) {
			throw new GameTestAssertException("set-up: the depot took " + in + " of 16 diamonds");
		}
		// the arm is a cogwheel: a cogwheel beside it, on a Creative Motor, turns it
		helper.setBlock(ARM, MechanicCompatTests.create("mechanical_arm"));
		helper.setBlock(ARM.north().below(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "up"));
		helper.setBlock(ARM.north(), MechanicCompatTests.with(MechanicCompatTests.create("cogwheel").defaultBlockState(), "axis", "y"));
		BlockPos arm = helper.absolutePos(ARM);
		ListTag points = new ListTag();
		points.add(point(level, helper.absolutePos(EDGE), arm, true));
		points.add(point(level, helper.absolutePos(OUT_DEPOT), arm, false));
		BlockEntity be = level.getBlockEntity(arm);
		if (be == null) {
			throw new GameTestAssertException("set-up: no Mechanical Arm block entity at " + ARM);
		}
		CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
		tag.put("InteractionPoints", points);
		be.loadWithComponents(tag, level.registryAccess());
	}

	private static CompoundTag point(ServerLevel level, BlockPos pos, BlockPos arm, boolean take) {
		ArmInteractionPoint point = ArmInteractionPoint.create(level, pos, level.getBlockState(pos));
		if (point == null) {
			throw new GameTestAssertException("set-up: the arm can't use the block at " + pos + ": " + level.getBlockState(pos));
		}
		if ((point.getMode() == ArmInteractionPoint.Mode.TAKE) != take) {
			point.cycleMode();
		}
		if ((point.getMode() == ArmInteractionPoint.Mode.TAKE) != take) {
			throw new GameTestAssertException("set-up: the arm point at " + pos + " is " + point.getMode());
		}
		return point.serialize(arm);
	}

	/** The diamonds the item holder at {@code at} (a depot) holds, through its item storage. */
	static long diamonds(GameTestHelper helper, BlockPos at) {
		Storage<ItemVariant> storage = ItemStorage.SIDED.find(helper.getLevel(), helper.absolutePos(at), Direction.UP);
		if (storage == null) {
			storage = ItemStorage.SIDED.find(helper.getLevel(), helper.absolutePos(at), null);
		}
		long n = 0;
		if (storage != null) {
			for (StorageView<ItemVariant> view : storage) {
				if (!view.isResourceBlank() && view.getResource().isOf(Items.DIAMOND)) {
					n += view.getAmount();
				}
			}
		}
		return n;
	}

	// ---------------------------------------------------------------- the Mechanical Saw

	/** The control: with the village open, a saw just outside cuts the log in the village's last column. */
	//$ gametest_ticks_batch AREA '400' '"qa_cmore_saw_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_cmore_saw_open")
	public void aMechanicalSawOutsideAnOpenVillageCutsTheLogAtItsEdge(GameTestHelper helper) {
		VillageHallBlockEntity hall = hall(helper, false);
		helper.setBlock(EDGE, Blocks.OAK_LOG);
		saw(helper, EDGE.east());
		helper.runAfterDelay(300, () -> {
			done(helper, hall);
			helper.assertTrue(speed(helper, EDGE.east()) != 0, "the saw never turned");
			helper.assertTrue(!helper.getBlockState(EDGE).is(Blocks.OAK_LOG), "the control saw hasn't cut the log at the open village's edge: "
				+ helper.getBlockState(EDGE));
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the Deployer

	/** The control: with the village open, a stranger's Deployer outside places its cobblestone in the village's last column. */
	//$ gametest_ticks_batch AREA '300' '"qa_cmore_deployer_open"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_cmore_deployer_open")
	public void aDeployerOutsideAnOpenVillagePlacesAtItsEdge(GameTestHelper helper) {
		VillageHallBlockEntity hall = hall(helper, false);
		deployer(helper);
		helper.runAfterDelay(200, () -> {
			done(helper, hall);
			helper.assertTrue(speed(helper, DEPLOYER) != 0, "the deployer never turned");
			helper.assertTrue(helper.getBlockState(EDGE).is(Blocks.COBBLESTONE), "the control deployer hasn't placed its cobblestone at the open"
				+ " village's edge: " + helper.getBlockState(EDGE) + " (holding " + QaCreateDeployerOnOurThings1102eCompatTests.held(helper, DEPLOYER) + ")");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the Mechanical Arm

	/** The control: with the village open, a Mechanical Arm outside moves diamonds off the depot at its edge. */
	//$ gametest_ticks_batch AREA '500' '"qa_cmore_arm_open"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_cmore_arm_open")
	public void aMechanicalArmOutsideAnOpenVillageTakesFromTheDepotAtItsEdge(GameTestHelper helper) {
		VillageHallBlockEntity hall = hall(helper, false);
		depotsAndArm(helper);
		helper.runAfterDelay(400, () -> {
			done(helper, hall);
			helper.assertTrue(speed(helper, ARM) != 0, "the arm never turned");
			helper.assertTrue(diamonds(helper, EDGE) < 16, "the control arm took nothing from the open village's depot: it holds "
				+ diamonds(helper, EDGE) + " of 16 diamonds, the depot outside " + diamonds(helper, OUT_DEPOT));
			helper.succeed();
		});
	}
}
