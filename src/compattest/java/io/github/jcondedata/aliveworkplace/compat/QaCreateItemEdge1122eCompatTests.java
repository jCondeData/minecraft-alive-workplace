package io.github.jcondedata.aliveworkplace.compat;

import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.logistics.depot.EjectorBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-e-1122-2213, Create and the pack), from the protection promise (wiki create.md: "Item transport stops at the
 * edge too: a funnel or chute outside finds no inventory in a chest or barrel inside the village (B994), and a
 * Mechanical Arm outside neither takes from nor puts onto a depot, belt, basin or funnel inside it (B1015)") on two
 * item paths no earlier test drove, both handing items straight onto Frank's Depot in his village's last column
 * (x = 11), through Create's direct belt input rather than an inventory lookup:
 *
 * <ol>
 *   <li><b>A Weighted Ejector.</b> A stranger's ejector outside (x = 15, facing west, a creative motor at its north side) aimed
 *       4 blocks west at Frank's depot, loaded with diamonds.</li>
 *   <li><b>A Mechanical Belt.</b> A stranger's belt from x = 16 to x = 12 (all outside), running west, its end facing
 *       Frank's depot, with diamonds put on it.</li>
 * </ol>
 *
 * Frank's hall is {@link QaCreateDeployerUses1122eCompatTests#hall}'s: at (3, 2, 15), the village 8 columns across.
 * Each case has its control with the village open: the diamonds land on Frank's depot. The protected cases fail today
 * (found bugs, 3 of 3 runs: the ejector and the belt both hand a diamond onto Frank's depot) and live in
 * QaCreateItemEdge1122eBugCompatTests (on tests/create-item-edge-1122e), not registered until the fix; this class keeps
 * their controls.
 */
public class QaCreateItemEdge1122eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	static final int Z = 15;
	/** Frank's depot, in the village's last column. */
	static final BlockPos DEPOT = new BlockPos(11, 2, Z);

	static boolean inside(BlockPos p) {
		return QaCreateHosePulley1122eCompatTests.inside(p);
	}

	static void clear(GameTestHelper helper) {
		for (int x = 6; x <= 20; x++) {
			for (int y = 2; y <= 8; y++) {
				for (int z = Z - 2; z <= Z + 2; z++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
	}

	/** Frank's empty depot, and the protection rule checked for a machine at {@code machine}. */
	static void frank(GameTestHelper helper, boolean protect, BlockPos machine) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, protect);
		clear(helper);
		helper.setBlock(DEPOT, MechanicCompatTests.create("depot"));
		helper.assertTrue(inside(DEPOT) && !inside(machine) && !inside(DEPOT.east()), "set-up: the depot isn't in the village's last column");
		boolean may = VillageProtection.mayMachineChange(helper.getLevel(), helper.absolutePos(machine), helper.absolutePos(DEPOT));
		helper.assertTrue(may != protect, "set-up: the protection rule says a machine at " + machine.toShortString() + (may ? " may" : " may not")
			+ " change the depot with the village " + (protect ? "protected" : "open"));
	}

	static long diamonds(GameTestHelper helper, BlockPos at) {
		return QaCreateMoreMachinesProtection1107eCompatTests.diamonds(helper, at);
	}

	/** Loose diamonds lying inside the village (thrown, not handed in). */
	static long looseInside(GameTestHelper helper) {
		long n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds())) {
			BlockPos rel = e.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO));
			if (e.getItem().is(Items.DIAMOND) && inside(rel)) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	/** Loose diamonds lying anywhere near the test. */
	static long looseAll(GameTestHelper helper) {
		long n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4))) {
			if (e.getItem().is(Items.DIAMOND)) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	static void discardItems(GameTestHelper helper) {
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4))) {
			e.discard();
		}
	}

	// ---------------------------------------------------------------- 1. a Weighted Ejector aimed into the village

	static final BlockPos EJECTOR = new BlockPos(15, 2, Z);

	static void ejector(GameTestHelper helper, boolean protect) {
		frank(helper, protect, EJECTOR);
		helper.setBlock(EJECTOR.north(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "south"));
		helper.setBlock(EJECTOR, MechanicCompatTests.with(MechanicCompatTests.create("weighted_ejector").defaultBlockState(), "facing", "west"));
		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(EJECTOR)) instanceof EjectorBlockEntity be)) {
			throw new GameTestAssertException("set-up: no Weighted Ejector block entity at " + EJECTOR.toShortString());
		}
		be.setTarget(EJECTOR.getX() - DEPOT.getX(), 0);
		BlockPos aimed = be.getTargetPosition().subtract(helper.absolutePos(BlockPos.ZERO));
		helper.assertTrue(aimed.equals(DEPOT), "set-up: the ejector aims at " + aimed.toShortString() + ", not Frank's depot " + DEPOT.toShortString());
		int[] loaded = {0};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			// one diamond at a time, as a funnel would feed it (the ejector launches what its plate holds)
			if (tick[0] % 20 == 0 && loaded[0] < 4 && diamonds(helper, EJECTOR) == 0) {
				loaded[0] += (int) QaCreateDeployerOnOurThings1102eCompatTests.insert(helper.getLevel(), helper.absolutePos(EJECTOR), new ItemStack(Items.DIAMOND, 1));
			}
		});
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 300, () -> {
			float turning = QaCreateDeployerOnOurThings1102eCompatTests.speed(helper, EJECTOR);
			long onDepot = diamonds(helper, DEPOT);
			long onPlate = diamonds(helper, EJECTOR);
			long loose = looseInside(helper);
			long looseAll = looseAll(helper);
			discardItems(helper);
			helper.assertTrue(turning != 0, "set-up: the ejector never turned, so the test proves nothing");
			helper.assertTrue(loaded[0] > 0, "set-up: the ejector took no diamond");
			if (protect) {
				helper.assertTrue(onDepot == 0, "a stranger's Weighted Ejector outside Frank's protected village launched diamonds onto his Depot"
					+ " inside it: the depot holds " + onDepot + " of the " + loaded[0] + " loaded (" + onPlate + " still on the ejector, " + loose
					+ " lying loose inside)");
			} else {
				helper.assertTrue(onDepot > 0, "the control (village open): the ejector put no diamond on the depot it aims at: " + loaded[0]
					+ " loaded, " + onPlate + " still on the ejector, " + loose + " lying loose inside, " + looseAll + " loose in all");
			}
		});
	}

	/** The control: with the village open, a Weighted Ejector outside launches diamonds onto Frank's depot. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_ejector_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_ejector_open")
	public void anEjectorOutsideAnOpenVillageLaunchesOntoItsDepot(GameTestHelper helper) {
		ejector(helper, false);
	}

	// ---------------------------------------------------------------- 2. a Mechanical Belt ending at the village's edge

	static final BlockPos BELT_END = DEPOT.east();
	static final BlockPos BELT_START = new BlockPos(16, 2, Z);

	static void belt(GameTestHelper helper, boolean protect, boolean reverse) {
		frank(helper, protect, BELT_END);
		var shaft = MechanicCompatTests.with(MechanicCompatTests.create("shaft").defaultBlockState(), "axis", "z");
		helper.setBlock(BELT_START, shaft);
		helper.setBlock(BELT_END, shaft);
		BlockPos start = helper.absolutePos(BELT_START);
		BlockPos end = helper.absolutePos(BELT_END);
		helper.assertTrue(BeltConnectorItem.canConnect(helper.getLevel(), start, end), "set-up: Create won't connect the two shafts");
		BeltConnectorItem.createBelts(helper.getLevel(), start, end);
		helper.setBlock(BELT_START.relative(reverse ? net.minecraft.core.Direction.NORTH : net.minecraft.core.Direction.SOUTH),
			MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", reverse ? "south" : "north"));
		int[] loaded = {0};
		int[] tick = {0};
		BlockPos feed = BELT_START.west();
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] % 20 == 0 && tick[0] <= 100) {
				loaded[0] += (int) QaCreateDeployerOnOurThings1102eCompatTests.insert(helper.getLevel(), helper.absolutePos(feed), new ItemStack(Items.DIAMOND, 1));
			}
		});
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 360, () -> {
			float turning = QaCreateDeployerOnOurThings1102eCompatTests.speed(helper, BELT_START);
			long onDepot = diamonds(helper, DEPOT);
			long onBelt = 0;
			for (int x = BELT_END.getX(); x <= BELT_START.getX(); x++) {
				onBelt += diamonds(helper, new BlockPos(x, 2, Z));
			}
			long loose = looseInside(helper);
			long looseAll = looseAll(helper);
			discardItems(helper);
			helper.assertTrue(turning != 0, "set-up: the belt never turned, so the test proves nothing");
			helper.assertTrue(loaded[0] > 0, "set-up: the belt took no diamond");
			if (protect) {
				helper.assertTrue(onDepot == 0, "a stranger's Mechanical Belt outside Frank's protected village carried diamonds onto his Depot"
					+ " inside it: the depot holds " + onDepot + " of the " + loaded[0] + " put on the belt (" + onBelt + " still on the belt, "
					+ loose + " lying loose inside)");
			} else {
				helper.assertTrue(onDepot > 0, "the control (village open): the belt put no diamond on the depot at its end: " + loaded[0]
					+ " put on, " + onBelt + " still on the belt, " + loose + " lying loose inside, " + looseAll + " loose in all");
			}
		});
	}

	/** The control: with the village open, a belt outside carries diamonds onto Frank's depot at its end. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_belt_open"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_belt_open")
	public void aBeltOutsideAnOpenVillageCarriesOntoTheDepotAtItsEnd(GameTestHelper helper) {
		belt(helper, false, true);
	}
}
