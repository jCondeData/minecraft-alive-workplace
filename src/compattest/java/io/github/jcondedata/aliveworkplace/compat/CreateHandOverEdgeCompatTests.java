package io.github.jcondedata.aliveworkplace.compat;

import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * B1532 (B1520's kin): Create machines other than the Weighted Ejector and the belt also hand items straight onto the
 * block next to them through its direct belt input (DirectBeltInputBehaviour), which no guard covered: a stranger's
 * Item Drain or horizontal Mechanical Saw just outside Frank's protected village put diamonds onto his Depot in the
 * village's last column. Fixed at the one point they all pass (HandOverMixin, with MachineTickMixin telling it which
 * machine is ticking). Promise (wiki create.md): item transport stops at the edge.
 *
 * <p>The geometry of {@link QaCreateItemEdge1122eCompatTests}: Frank's hall at (3, 2, 15), his Depot at x = 11 (the
 * village's last column), the stranger's machine at x = 12 (just outside). Each machine has three cases: the village
 * protected (nothing reaches the depot), the village open (the control: the diamonds do reach it, so the machine
 * really hands over westward), and Frank's own machine wholly inside his protected village (x = 7, depot at x = 6),
 * which still works.
 */
public class CreateHandOverEdgeCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final int Z = QaCreateItemEdge1122eCompatTests.Z;
	/** The stranger's machine, one block east of Frank's depot and outside the village. */
	static final BlockPos OUTSIDE = QaCreateItemEdge1122eCompatTests.DEPOT.east();
	/** Frank's own machine and depot, both inside his village. */
	static final BlockPos INSIDE = new BlockPos(7, 2, Z);
	static final BlockPos INSIDE_DEPOT = INSIDE.west();

	enum Village { PROTECTED, OPEN, OWNERS_INSIDE }

	/** Sets up Frank's village and empty depot for the case; returns {machine, depot}. */
	private static BlockPos[] setUp(GameTestHelper helper, Village village) {
		if (village == Village.OWNERS_INSIDE) {
			CreateItemEdgeInsideCompatTests.frank(helper, INSIDE_DEPOT, INSIDE);
			return new BlockPos[] {INSIDE, INSIDE_DEPOT};
		}
		QaCreateItemEdge1122eCompatTests.frank(helper, village == Village.PROTECTED, OUTSIDE);
		return new BlockPos[] {OUTSIDE, QaCreateItemEdge1122eCompatTests.DEPOT};
	}

	/** Checks the depot after {@code ticks}: empty with the village protected, holding diamonds otherwise. */
	private static void check(GameTestHelper helper, Village village, String what, BlockPos machine, BlockPos depot, int[] fed, int ticks,
		boolean needsTurning) {
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, ticks, () -> {
			float turning = needsTurning ? QaCreateDeployerOnOurThings1102eCompatTests.speed(helper, machine) : 1;
			long onDepot = QaCreateItemEdge1122eCompatTests.diamonds(helper, depot);
			long inMachine = QaCreateItemEdge1122eCompatTests.diamonds(helper, machine);
			long loose = QaCreateItemEdge1122eCompatTests.looseAll(helper);
			QaCreateItemEdge1122eCompatTests.discardItems(helper);
			helper.assertTrue(turning != 0, "set-up: the " + what + " never turned, so the test proves nothing");
			helper.assertTrue(fed[0] > 0, "set-up: the " + what + " took no diamond");
			String state = fed[0] + " fed, " + inMachine + " still in the " + what + ", " + loose + " loose";
			switch (village) {
				case PROTECTED -> helper.assertTrue(onDepot == 0, "a stranger's " + what + " outside Frank's protected village handed diamonds onto"
					+ " his Depot inside it: the depot holds " + onDepot + " (" + state + ")");
				case OPEN -> helper.assertTrue(onDepot > 0, "the control (village open): the " + what + " outside handed no diamond onto the depot"
					+ " west of it (" + state + ")");
				case OWNERS_INSIDE -> helper.assertTrue(onDepot > 0, "Frank's own " + what + " inside his protected village handed no diamond onto"
					+ " his depot there (" + state + ")");
			}
		});
	}

	// ---------------------------------------------------------------- 1. an Item Drain passing items through

	/** An Item Drain at the machine's place; diamonds come in from its east side, so (nothing to drain) it passes them west. */
	static void drain(GameTestHelper helper, Village village) {
		BlockPos[] at = setUp(helper, village);
		BlockPos drain = at[0];
		helper.setBlock(drain, MechanicCompatTests.create("item_drain"));
		int[] fed = {0};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 20 == 0 && fed[0] < 4 && QaCreateItemEdge1122eCompatTests.diamonds(helper, drain) == 0) {
				DirectBeltInputBehaviour input = BlockEntityBehaviour.get(helper.getLevel(), helper.absolutePos(drain), DirectBeltInputBehaviour.TYPE);
				if (input == null) {
					throw new GameTestAssertException("set-up: the Item Drain has no direct belt input");
				}
				// as a belt running west would hand it over (no machine ticking: the drain itself is what's judged)
				if (input.handleInsertion(new ItemStack(Items.DIAMOND), Direction.WEST, false).isEmpty()) {
					fed[0]++;
				}
			}
		});
		check(helper, village, "Item Drain", drain, at[1], fed, 260, false);
	}

	//$ gametest_ticks_batch AREA '360' '"create_hand_over_drain_protected"'
	@GameTest(template = AREA, timeoutTicks = 360, batch = "create_hand_over_drain_protected")
	public void anItemDrainOutsideAProtectedVillagePassesNothingOntoADepotInside(GameTestHelper helper) {
		drain(helper, Village.PROTECTED);
	}

	//$ gametest_ticks_batch AREA '360' '"create_hand_over_drain_open"'
	@GameTest(template = AREA, timeoutTicks = 360, batch = "create_hand_over_drain_open")
	public void anItemDrainOutsideAnOpenVillagePassesOntoTheDepot(GameTestHelper helper) {
		drain(helper, Village.OPEN);
	}

	//$ gametest_ticks_batch AREA '360' '"create_hand_over_drain_inside"'
	@GameTest(template = AREA, timeoutTicks = 360, batch = "create_hand_over_drain_inside")
	public void anOwnersItemDrainInsideHisProtectedVillageStillPassesOntoHisDepot(GameTestHelper helper) {
		drain(helper, Village.OWNERS_INSIDE);
	}

	// ---------------------------------------------------------------- 2. a horizontal Mechanical Saw's output

	/**
	 * A Mechanical Saw facing up (its shaft along z, a Creative Motor at its south side, which turns it so that items on it
	 * move west; with the motor at its north side they move east); diamonds (nothing to cut) leave it westward onto the depot.
	 */
	static void saw(GameTestHelper helper, Village village) {
		BlockPos[] at = setUp(helper, village);
		BlockPos saw = at[0];
		helper.setBlock(saw.south(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "north"));
		helper.setBlock(saw, MechanicCompatTests.with(MechanicCompatTests.with(MechanicCompatTests.create("mechanical_saw").defaultBlockState(),
			"facing", "up"), "axis_along_first", "false"));
		int[] fed = {0};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 20 == 0 && fed[0] < 4 && QaCreateItemEdge1122eCompatTests.diamonds(helper, saw) == 0) {
				fed[0] += (int) QaCreateDeployerOnOurThings1102eCompatTests.insert(helper.getLevel(), helper.absolutePos(saw), new ItemStack(Items.DIAMOND, 1));
			}
		});
		check(helper, village, "Mechanical Saw", saw, at[1], fed, 260, true);
	}

	//$ gametest_ticks_batch AREA '360' '"create_hand_over_saw_protected"'
	@GameTest(template = AREA, timeoutTicks = 360, batch = "create_hand_over_saw_protected")
	public void aSawOutsideAProtectedVillageHandsNothingOntoADepotInside(GameTestHelper helper) {
		saw(helper, Village.PROTECTED);
	}

	//$ gametest_ticks_batch AREA '360' '"create_hand_over_saw_open"'
	@GameTest(template = AREA, timeoutTicks = 360, batch = "create_hand_over_saw_open")
	public void aSawOutsideAnOpenVillageHandsOntoTheDepot(GameTestHelper helper) {
		saw(helper, Village.OPEN);
	}

	//$ gametest_ticks_batch AREA '360' '"create_hand_over_saw_inside"'
	@GameTest(template = AREA, timeoutTicks = 360, batch = "create_hand_over_saw_inside")
	public void anOwnersSawInsideHisProtectedVillageStillHandsOntoHisDepot(GameTestHelper helper) {
		saw(helper, Village.OWNERS_INSIDE);
	}
}
