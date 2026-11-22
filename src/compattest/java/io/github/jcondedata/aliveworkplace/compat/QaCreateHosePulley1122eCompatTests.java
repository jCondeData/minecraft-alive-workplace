package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.speed;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * QA (lane-e-1122-1613, Create and the pack): a stranger's Create Hose Pulley standing just outside a protected village,
 * tested from VillageProtection's promise: inside a protected village "only the hall's owner, their friends and operators
 * may break or place blocks ... empty buckets" (B719: a stranger outside can't pour into or scoop out of a village), and
 * (StandIns) a stranger's machine is a stranger. Create's hose pulley keeps no owner, so "the stranger's" is the one
 * standing outside the village; Frank's is the one standing inside his own.
 *
 * <p>Frank's hall is {@link QaCreateDeployerUses1122eCompatTests#hall}'s: at (3, 2, 15), the village 8 columns across,
 * so on the row z = 15 the last column inside is x = 11. The stranger's pulley stands at x = 12 (outside), its hose
 * hanging into a one-deep basin (y = 2) running from x = 9 to x = 12 and z = 14 to 16, walled in glass, so the basin
 * spans the village's edge. Draining: the basin is full of water sources, and a Mechanical Pump at the pulley's pipe
 * side pumps into a Fluid Tank; no source inside the village may go. Filling: the basin is dry, and the pump pushes
 * water from a tank kept topped up into the pulley; no source may appear inside the village. Each has a control (the
 * village open: the hose really drains or floods the inside cells) and a guard against over-protection (Frank's own
 * pulley inside his protected village drains and floods his basin). The two protected cases fail today (found bugs) and
 * live in {@link QaCreateHosePulley1122eBugCompatTests}, not registered until the fix. Each basin is 12 blocks, far from Create's 10,000
 * "bottomless" limit.
 */
public class QaCreateHosePulley1122eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	/** The stranger's pulley's column, the village's first outside one. */
	static final int STRANGER_X = 12;
	/** Frank's own pulley's column, inside his village. */
	static final int OWN_X = 8;
	static final int Y = 5;
	static final int Z = 15;
	static final int RADIUS = QaCreateDeployerUses1122eCompatTests.REACH;
	static final long BUCKET = 81000L;

	/** Whether {@code p}'s column is in Frank's village (VillageProtection's columns test, around the hall). */
	static boolean inside(BlockPos p) {
		long dx = QaCreateDeployerUses1122eCompatTests.HALL.getX() - p.getX();
		long dz = QaCreateDeployerUses1122eCompatTests.HALL.getZ() - p.getZ();
		return dx * dx + dz * dz <= (long) RADIUS * RADIUS;
	}

	/** The basin's cells for a pulley at column {@code x}: x - 3 .. x, z 14..16, y = 2. */
	static List<BlockPos> basin(int x) {
		List<BlockPos> cells = new ArrayList<>();
		for (int bx = x - 3; bx <= x; bx++) {
			for (int bz = Z - 1; bz <= Z + 1; bz++) {
				cells.add(new BlockPos(bx, 2, bz));
			}
		}
		return cells;
	}

	/** The basin walled in glass on y = 2 (its floor is the area's own), full of water sources or dry. */
	static void basin(GameTestHelper helper, int x, boolean water) {
		for (int bx = x - 4; bx <= x + 1; bx++) {
			for (int bz = Z - 2; bz <= Z + 2; bz++) {
				boolean wall = bx == x - 4 || bx == x + 1 || bz == Z - 2 || bz == Z + 2;
				helper.setBlock(new BlockPos(bx, 1, bz), Blocks.STONE);
				helper.setBlock(new BlockPos(bx, 2, bz), wall ? Blocks.GLASS : water ? Blocks.WATER : Blocks.AIR);
			}
		}
	}

	/**
	 * A hose pulley at (x, 5, 15) facing west (its shaft north, its pipe south; a creative motor north of it turns it the
	 * way that lowers the hose); a Mechanical Pump south of it (axis z), a cogwheel east of the pump with a creative motor
	 * driving it, and a Fluid Tank south of the pump. {@code drain}: the pump faces south (from the pulley to the tank);
	 * else north (tank to pulley).
	 */
	static void machine(GameTestHelper helper, int x, boolean drain) {
		BlockPos pulley = new BlockPos(x, Y, Z);
		BlockPos pump = pulley.south();
		BlockPos tank = pump.south();
		BlockPos cog = pump.east();
		String flow = drain ? "south" : "north";
		helper.setBlock(pulley, MechanicCompatTests.with(QaCreateDeployerOnOurThings1102eCompatTests.create("hose_pulley"), "facing", "west"));
		helper.setBlock(tank, QaCreateDeployerOnOurThings1102eCompatTests.create("fluid_tank"));
		helper.setBlock(pump, MechanicCompatTests.with(QaCreateDeployerOnOurThings1102eCompatTests.create("mechanical_pump"), "facing", flow));
		helper.setBlock(cog, MechanicCompatTests.with(QaCreateDeployerOnOurThings1102eCompatTests.create("cogwheel"), "axis", "z"));
		helper.runAfterDelay(2, () -> {
			helper.setBlock(pulley.north(), MechanicCompatTests.with(QaCreateDeployerOnOurThings1102eCompatTests.create("creative_motor"), "facing", "south"));
			helper.setBlock(cog.south(), MechanicCompatTests.with(QaCreateDeployerOnOurThings1102eCompatTests.create("creative_motor"), "facing", "north"));
		});
		// a pump turns itself round when its shaft's direction does; once it turns, set it the way the test wants it
		for (int t = 8; t <= 60; t += 4) {
			helper.runAfterDelay(t, () -> {
				BlockState state = helper.getBlockState(pump);
				if (speed(helper, pump) != 0 && !state.getValue(state.getBlock().getStateDefinition().getProperty("facing")).toString().equals(flow)) {
					helper.getLevel().setBlock(helper.absolutePos(pump), MechanicCompatTests.with(state, "facing", flow), 3);
				}
			});
		}
		if (!drain) {
			// the stranger's supply: the tank kept topped up with water
			helper.onEachTick(() -> {
				Storage<FluidVariant> storage = FluidStorage.SIDED.find(helper.getLevel(), helper.absolutePos(tank), Direction.UP);
				if (storage != null) {
					try (Transaction tx = Transaction.openOuter()) {
						storage.insert(FluidVariant.of(Fluids.WATER), 4 * BUCKET, tx);
						tx.commit();
					}
				}
			});
		}
	}

	/** The pulley's saved hose length ("Offset") and fluid-handler state, for failure messages. */
	static String hose(GameTestHelper helper, BlockPos pulley) {
		var be = helper.getLevel().getBlockEntity(helper.absolutePos(pulley));
		if (be == null) {
			return "none";
		}
		var tag = be.saveWithoutMetadata(helper.getLevel().registryAccess());
		tag.remove("Speed");
		String text = tag.toString();
		return text.length() > 300 ? text.substring(0, 300) : text;
	}

	static long tank(GameTestHelper helper, int x) {
		return QaPumpHouseTank1121eCompatTests.amount(helper.getLevel(), helper.absolutePos(new BlockPos(x, Y, Z + 2)));
	}

	static boolean source(GameTestHelper helper, BlockPos p) {
		return helper.getLevel().getFluidState(helper.absolutePos(p)).isSourceOfType(Fluids.WATER);
	}

	static String state(GameTestHelper helper, int x) {
		BlockPos pulley = new BlockPos(x, Y, Z);
		return "pulley Speed " + speed(helper, pulley) + ", pump Speed " + speed(helper, pulley.south()) + " " + helper.getBlockState(pulley.south())
			+ ", tank " + tank(helper, x) + " droplets, hose " + hose(helper, pulley);
	}

	// ---------------------------------------------------------------- 1. draining

	/** {@code x}'s pulley over a full basin; {@code wantInsideDrained}: an inside source must go, else none may. */
	static void drain(GameTestHelper helper, boolean protect, int x, boolean wantInsideDrained, String who) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, protect);
		basin(helper, x, true);
		machine(helper, x, true);
		List<BlockPos> in = basin(x).stream().filter(QaCreateHosePulley1122eCompatTests::inside).toList();
		List<BlockPos> out = basin(x).stream().filter(p -> !inside(p)).toList();
		AtomicReference<String> lostInside = new AtomicReference<>("");
		AtomicReference<String> lostOutside = new AtomicReference<>("");
		helper.onEachTick(() -> {
			for (BlockPos p : in) {
				if (lostInside.get().isEmpty() && !source(helper, p)) {
					lostInside.set(p.toShortString() + " became " + helper.getBlockState(p) + " at tick " + helper.getTick());
				}
			}
			for (BlockPos p : out) {
				if (lostOutside.get().isEmpty() && !source(helper, p)) {
					lostOutside.set(p.toShortString() + " at tick " + helper.getTick());
				}
			}
		});
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 500, () -> {
			helper.assertTrue(!in.isEmpty(), "set-up: no basin cell inside the village");
			helper.assertTrue(speed(helper, new BlockPos(x, Y, Z)) != 0, "the hose pulley never turned, so the test proves nothing (" + state(helper, x) + ")");
			if (wantInsideDrained) {
				helper.assertTrue(!lostInside.get().isEmpty(), who + ": the hose pulley and pump drained no water source inside the village in 500 ticks"
					+ " (outside: " + (lostOutside.get().isEmpty() ? "none drained" : lostOutside.get()) + "; " + state(helper, x) + ")");
			} else {
				helper.assertTrue(lostInside.get().isEmpty(), "a stranger's Hose Pulley outside Frank's protected village, pumped by a Mechanical Pump,"
					+ " drained a water source inside it: " + lostInside.get() + " (" + state(helper, x) + ")");
				helper.assertTrue(!lostOutside.get().isEmpty() || tank(helper, x) > 0, "the pump took no water at all, not even outside the village,"
					+ " so the test proves nothing (" + state(helper, x) + ")");
			}
		});
	}

	/** The control: with the village open, the same pulley drains sources inside it. */
	//$ gametest_ticks_batch AREA '600' '"qa_e1122_hose_drain_open"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_e1122_hose_drain_open")
	public void aStrangersHosePulleyDrainsWaterFromAnOpenVillage(GameTestHelper helper) {
		drain(helper, false, STRANGER_X, true, "the control (village open)");
	}

	/** Frank's own pulley inside his protected village still drains his pond. */
	//$ gametest_ticks_batch AREA '600' '"qa_e1122_hose_drain_owner"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_e1122_hose_drain_owner")
	public void theOwnersHosePulleyStillDrainsInHisProtectedVillage(GameTestHelper helper) {
		drain(helper, true, OWN_X, true, "Frank's own hose pulley inside his protected village");
	}

	// ---------------------------------------------------------------- 2. filling

	/** {@code x}'s pulley over a dry basin, fed water; {@code wantInsideFilled}: an inside cell must get a source, else none may. */
	static void fill(GameTestHelper helper, boolean protect, int x, boolean wantInsideFilled, String who) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, protect);
		basin(helper, x, false);
		machine(helper, x, false);
		List<BlockPos> in = basin(x).stream().filter(QaCreateHosePulley1122eCompatTests::inside).toList();
		List<BlockPos> out = basin(x).stream().filter(p -> !inside(p)).toList();
		AtomicReference<String> wetInside = new AtomicReference<>("");
		AtomicReference<String> wetOutside = new AtomicReference<>("");
		helper.onEachTick(() -> {
			for (BlockPos p : in) {
				if (wetInside.get().isEmpty() && source(helper, p)) {
					wetInside.set(p.toShortString() + " at tick " + helper.getTick());
				}
			}
			for (BlockPos p : out) {
				if (wetOutside.get().isEmpty() && source(helper, p)) {
					wetOutside.set(p.toShortString() + " at tick " + helper.getTick());
				}
			}
		});
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 500, () -> {
			helper.assertTrue(!in.isEmpty(), "set-up: no basin cell inside the village");
			helper.assertTrue(speed(helper, new BlockPos(x, Y, Z)) != 0, "the hose pulley never turned, so the test proves nothing (" + state(helper, x) + ")");
			if (wantInsideFilled) {
				helper.assertTrue(!wetInside.get().isEmpty(), who + ": the hose pulley fed by the pump poured no water source inside the village in 500 ticks"
					+ " (outside: " + (wetOutside.get().isEmpty() ? "none" : wetOutside.get()) + "; " + state(helper, x) + ")");
			} else {
				helper.assertTrue(wetInside.get().isEmpty(), "a stranger's Hose Pulley outside Frank's protected village, fed water by a Mechanical Pump,"
					+ " poured a water source inside it: " + wetInside.get() + " (outside first: " + wetOutside.get() + "; " + state(helper, x) + ")");
			}
		});
	}

	/** The control: with the village open, the same pulley floods the inside cells of the basin. */
	//$ gametest_ticks_batch AREA '600' '"qa_e1122_hose_fill_open"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_e1122_hose_fill_open")
	public void aStrangersHosePulleyPoursWaterIntoAnOpenVillage(GameTestHelper helper) {
		fill(helper, false, STRANGER_X, true, "the control (village open)");
	}

	/** Frank's own pulley inside his protected village still fills his basin. */
	//$ gametest_ticks_batch AREA '600' '"qa_e1122_hose_fill_owner"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_e1122_hose_fill_owner")
	public void theOwnersHosePulleyStillFillsInHisProtectedVillage(GameTestHelper helper) {
		fill(helper, true, OWN_X, true, "Frank's own hose pulley inside his protected village");
	}
}
