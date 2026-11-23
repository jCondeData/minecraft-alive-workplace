package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.speed;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * QA (lane-e-1123): Create's pipes and packager at a protected village's edge, from the spec, not the code.
 * create.md ("Create's machines keep out of a protected village", the paragraph ending "Item transport stops at the
 * edge too: a funnel or chute outside finds no inventory in a chest or barrel inside the village (B994)" and "Fluids
 * stop at the edge too"), and village-hall.md on protection: a stranger's Create machine standing outside a protected
 * village takes nothing from, and puts nothing into, a container inside it (B994: vanilla has no horizontal path into a
 * chest from the next column). Two Create paths no test covered:
 *
 * <ul>
 *   <li>Fluid pipes: a Mechanical Pump outside, its Fluid Pipe touching the outside face of Frank's Fluid Tank (or
 *       Basin) in the village's last column, draining it into, or filling it from, the stranger's tank outside.</li>
 *   <li>Create 6's Packager (its inventory behaviour, as a funnel's) on the outside face of Frank's chest: packing
 *       from it on a redstone pulse, and unpacking a package pushed into it.</li>
 * </ul>
 *
 * The protected pipe cases and the protected unpacking case fail today (protection holes) and live, unregistered, in
 * {@link QaCreatePipePackager1123eBugCompatTests}; their open controls stay here and prove the set-up moves fluid and
 * items. Packing from Frank's chest is refused (B994's guard) and is tested here.
 *
 * Frank's hall is {@link QaCreateDeployerUses1122eCompatTests#hall}'s: at (3, 2, 15), 8 columns across, so on z = 15
 * x = 11 is the village's last column and x = 12 is outside. Every refusal has an open-village control on the same
 * set-up that shows the flow.
 */
public class QaCreatePipePackager1123eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	static final int Z = 15;
	static final long BUCKET = QaCreateHosePulley1122eCompatTests.BUCKET;
	/** Frank's container, the village's last column on the hall's row. */
	static final BlockPos EDGE = new BlockPos(11, 2, Z);
	static final BlockPos PIPE = EDGE.east();
	static final BlockPos PUMP = PIPE.east();
	static final BlockPos THEIR_TANK = PUMP.east();
	static final BlockPos COG = PUMP.north();

	static boolean inside(BlockPos p) {
		return QaCreateHosePulley1122eCompatTests.inside(p);
	}

	static BlockState create(String id) {
		return MechanicCompatTests.create(id).defaultBlockState();
	}

	static void clear(GameTestHelper helper) {
		for (int x = 7; x <= 18; x++) {
			for (int y = 2; y <= 4; y++) {
				for (int z = Z - 1; z <= Z + 1; z++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
	}

	static Storage<FluidVariant> fluids(GameTestHelper helper, BlockPos pos) {
		Storage<FluidVariant> s = FluidStorage.SIDED.find(helper.getLevel(), helper.absolutePos(pos), Direction.UP);
		if (s == null) {
			throw new GameTestAssertException("set-up: no fluid storage at " + pos.toShortString() + " (" + helper.getBlockState(pos) + ")");
		}
		return s;
	}

	/** Droplets of {@code fluid} held at {@code pos}. */
	static long amount(GameTestHelper helper, BlockPos pos, Fluid fluid) {
		long n = 0;
		for (StorageView<FluidVariant> v : fluids(helper, pos)) {
			if (!v.isResourceBlank() && v.getResource().getFluid().isSame(fluid)) {
				n += v.getAmount();
			}
		}
		return n;
	}

	static void fill(GameTestHelper helper, BlockPos pos, Fluid fluid, long droplets) {
		long put;
		try (Transaction tx = Transaction.openOuter()) {
			put = fluids(helper, pos).insert(FluidVariant.of(fluid), droplets, tx);
			tx.commit();
		}
		helper.assertTrue(put == droplets, "set-up: " + pos.toShortString() + " took only " + put + " of " + droplets + " droplets");
	}

	/**
	 * Frank's {@code target} block ("fluid_tank" or "basin") at {@link #EDGE}, a Fluid Pipe outside at {@link #PIPE}, the
	 * stranger's Mechanical Pump at {@link #PUMP} facing {@code flow} (east: drains Frank's side; west: fills it), his
	 * Fluid Tank at {@link #THEIR_TANK}, a cogwheel north of the pump turned by a creative motor.
	 */
	static void pipes(GameTestHelper helper, String target, String flow) {
		pipes(helper, target, flow, "fluid_pipe");
	}

	/** A straight east-west run of {@code kind}: a plain, encased or glass Fluid Pipe, or a Smart Fluid Pipe on the floor. */
	static BlockState piece(String kind) {
		BlockState state = create(kind);
		return switch (kind) {
			case "fluid_pipe", "encased_fluid_pipe" -> {
				for (String side : List.of("north", "south", "up", "down")) {
					state = MechanicCompatTests.with(state, side, "false");
				}
				yield MechanicCompatTests.with(MechanicCompatTests.with(state, "east", "true"), "west", "true");
			}
			case "glass_fluid_pipe" -> MechanicCompatTests.with(state, "axis", "x");
			case "smart_fluid_pipe" -> MechanicCompatTests.with(MechanicCompatTests.with(state, "face", "floor"), "facing", "east");
			default -> throw new GameTestAssertException("set-up: no pipe piece " + kind);
		};
	}

	/** As {@link #pipes(GameTestHelper, String, String)} with {@code kind} (see {@link #piece}) as the piece at {@link #PIPE}. */
	static void pipes(GameTestHelper helper, String target, String flow, String kind) {
		clear(helper);
		helper.setBlock(EDGE.below(), Blocks.STONE);
		helper.setBlock(EDGE, create(target));
		helper.setBlock(THEIR_TANK, create("fluid_tank"));
		helper.setBlock(PUMP, MechanicCompatTests.with(create("mechanical_pump"), "facing", flow));
		helper.setBlock(PIPE, piece(kind));
		helper.setBlock(COG, MechanicCompatTests.with(create("cogwheel"), "axis", "x"));
		helper.runAfterDelay(2, () -> helper.setBlock(COG.east(), MechanicCompatTests.with(create("creative_motor"), "facing", "west")));
		// a pump turns itself round when its shaft's direction does; once it turns, set it the way the test wants it
		for (int t = 8; t <= 60; t += 4) {
			helper.runAfterDelay(t, () -> {
				BlockState state = helper.getBlockState(PUMP);
				if (speed(helper, PUMP) != 0 && !state.getValue(state.getBlock().getStateDefinition().getProperty("facing")).toString().equals(flow)) {
					helper.getLevel().setBlock(helper.absolutePos(PUMP), MechanicCompatTests.with(state, "facing", flow), 3);
				}
			});
		}
		helper.assertTrue(inside(EDGE) && !inside(PIPE) && !inside(PUMP) && !inside(THEIR_TANK), "set-up: Frank's " + target + " isn't at the village's edge");
	}

	static String pipeState(GameTestHelper helper) {
		return "pipe " + helper.getBlockState(PIPE) + ", pump Speed " + speed(helper, PUMP) + " " + helper.getBlockState(PUMP);
	}

	// ---------------------------------------------------------------- 1. Pipes draining Frank's tank

	/** Frank's tank of 8 buckets of water; the stranger's pump outside pulls through the pipe into his empty tank. */
	static void drain(GameTestHelper helper, boolean protect, boolean reload) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, protect);
		pipes(helper, "fluid_tank", "east");
		fill(helper, EDGE, Fluids.WATER, 8 * BUCKET);
		QaCreateCannonPipes1122eCompatTests.ruleSays(helper, protect, PUMP, EDGE);
		boolean[] reloaded = {false};
		if (reload) {
			helper.runAfterDelay(80, () -> {
				QaCreateEdgeVerify1123eCompatTests.reload(helper, PUMP);
				QaCreateEdgeVerify1123eCompatTests.reload(helper, EDGE);
				QaCreateEdgeVerify1123eCompatTests.reload(helper, QaCreateDeployerUses1122eCompatTests.HALL);
				reloaded[0] = true;
			});
		}
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 300, () -> {
			long franks = amount(helper, EDGE, Fluids.WATER);
			long theirs = amount(helper, THEIR_TANK, Fluids.WATER);
			String state = "Frank's tank " + franks + " of " + 8 * BUCKET + " droplets, the stranger's tank " + theirs + " (" + pipeState(helper) + ")";
			helper.assertTrue(speed(helper, PUMP) != 0, "set-up: the stranger's pump never turned: " + state);
			helper.assertTrue(!reload || reloaded[0], "set-up: the pump, tank and hall were never saved and loaded");
			if (protect) {
				helper.assertTrue(franks == 8 * BUCKET && theirs == 0, "a stranger's Mechanical Pump outside Frank's protected village drained his Fluid Tank"
					+ " in its last column through a Fluid Pipe on its outside face" + (reload ? " (saved and loaded at tick 80)" : "") + ": " + state);
			} else {
				helper.assertTrue(franks < 8 * BUCKET && theirs > 0, "the control (village open): the pump moved no water out of Frank's tank: " + state);
			}
		});
	}

	/** The control: with the village open the same pump drains Frank's tank into the stranger's. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123p_pipe_drain_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123p_pipe_drain_open")
	public void aStrangersPipeDrainsATankInAnOpenVillage(GameTestHelper helper) {
		drain(helper, false, false);
	}

	// ---------------------------------------------------------------- 2. Pipes filling Frank's tank or basin

	/** The stranger's tank of 8 buckets of {@code fluid}; his pump pushes it through the pipe toward Frank's {@code target}. */
	static void fillInto(GameTestHelper helper, boolean protect, String target, Fluid fluid) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, protect);
		pipes(helper, target, "west");
		fill(helper, THEIR_TANK, fluid, 8 * BUCKET);
		QaCreateCannonPipes1122eCompatTests.ruleSays(helper, protect, PUMP, EDGE);
		long[] firstSeen = {-1};
		helper.onEachTick(() -> {
			if (firstSeen[0] < 0 && amount(helper, EDGE, fluid) > 0) {
				firstSeen[0] = helper.getTick();
			}
		});
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 300, () -> {
			long franks = amount(helper, EDGE, fluid);
			long theirs = amount(helper, THEIR_TANK, fluid);
			String name = fluid == Fluids.LAVA ? "lava" : "water";
			String state = "Frank's " + target + " " + franks + " droplets of " + name + (firstSeen[0] >= 0 ? " (first at tick " + firstSeen[0] + ")" : "")
				+ ", the stranger's tank " + theirs + " of " + 8 * BUCKET + " (" + pipeState(helper) + ")";
			helper.assertTrue(speed(helper, PUMP) != 0, "set-up: the stranger's pump never turned: " + state);
			if (protect) {
				helper.assertTrue(firstSeen[0] < 0 && franks == 0, "a stranger's Mechanical Pump outside Frank's protected village pushed " + name + " through a"
					+ " Fluid Pipe into his " + target + " in its last column: " + state);
			} else {
				helper.assertTrue(franks > 0, "the control (village open): the pump pushed no " + name + " into Frank's " + target + ": " + state);
			}
		});
	}

	/** The control: with the village open the pump fills Frank's tank with lava. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123p_pipe_fill_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123p_pipe_fill_open")
	public void aStrangersPipeFillsLavaIntoATankInAnOpenVillage(GameTestHelper helper) {
		fillInto(helper, false, "fluid_tank", Fluids.LAVA);
	}

	/** The control: with the village open the pump fills Frank's basin. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123p_pipe_basin_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123p_pipe_basin_open")
	public void aStrangersPipeFillsWaterIntoABasinInAnOpenVillage(GameTestHelper helper) {
		fillInto(helper, false, "basin", Fluids.WATER);
	}

	// ---------------------------------------------------------------- 2b. B1546's edges: other pipe pieces, a tall tank, protection switched on

	/**
	 * Frank's tank {@code height} blocks tall at {@link #EDGE} (one multiblock tank in his last column), filled at tick 10
	 * with 8 buckets of water a block; the stranger's {@code kind} piece at {@link #PIPE} on its outside face and his pump
	 * pulling it into his tank. Protected: Frank's tank keeps every droplet; open: water leaves it.
	 */
	static void drainVia(GameTestHelper helper, boolean protect, String kind, int height) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, protect);
		pipes(helper, "fluid_tank", "east", kind);
		for (int y = 1; y < height; y++) {
			helper.setBlock(EDGE.above(y), create("fluid_tank"));
		}
		QaCreateCannonPipes1122eCompatTests.ruleSays(helper, protect, PIPE, EDGE);
		long full = height * 8 * BUCKET;
		helper.runAfterDelay(10, () -> fill(helper, EDGE, Fluids.WATER, full));
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 300, () -> {
			long franks = amount(helper, EDGE, Fluids.WATER);
			long theirs = amount(helper, THEIR_TANK, Fluids.WATER);
			String what = (height > 1 ? height + "-block-tall " : "") + "Fluid Tank, through a " + kind;
			String state = "Frank's tank " + franks + " of " + full + " droplets, the stranger's tank " + theirs + " (" + kind + " " + helper.getBlockState(PIPE)
				+ ", pump Speed " + speed(helper, PUMP) + ")";
			helper.assertTrue(speed(helper, PUMP) != 0, "set-up: the stranger's pump never turned: " + state);
			if (protect) {
				helper.assertTrue(franks == full && theirs == 0, "a stranger's Mechanical Pump outside Frank's protected village drained his " + what
					+ " on its outside face: " + state);
			} else {
				helper.assertTrue(franks < full && theirs > 0, "the control (village open): the pump moved no water out of Frank's " + what + ": " + state);
			}
		});
	}

	/** The control: a Smart Fluid Pipe on the edge. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123p_smart_pipe_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123p_smart_pipe_open")
	public void aStrangersSmartPipeDrainsATankInAnOpenVillage(GameTestHelper helper) {
		drainVia(helper, false, "smart_fluid_pipe", 1);
	}

	/** The control: a glass Fluid Pipe on the edge. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123p_glass_pipe_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123p_glass_pipe_open")
	public void aStrangersGlassPipeDrainsATankInAnOpenVillage(GameTestHelper helper) {
		drainVia(helper, false, "glass_fluid_pipe", 1);
	}

	/** The control: an encased Fluid Pipe on the edge. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123p_encased_pipe_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123p_encased_pipe_open")
	public void aStrangersEncasedPipeDrainsATankInAnOpenVillage(GameTestHelper helper) {
		drainVia(helper, false, "encased_fluid_pipe", 1);
	}

	/** The control: Frank's tank three blocks tall in his last column. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123p_tall_tank_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123p_tall_tank_open")
	public void aStrangersPipeDrainsATallTankInAnOpenVillage(GameTestHelper helper) {
		drainVia(helper, false, "fluid_pipe", 3);
	}

	static final int SWITCH_AFTER = 20;

	/**
	 * The village open while the stranger's pump drains Frank's tank; {@link #SWITCH_AFTER} ticks after water first
	 * leaves it, Frank turns protection on ({@code protectMidway}; else it stays open). From {@link #SWITCH_AFTER} ticks
	 * after that, Frank's tank must lose nothing more (the control: it goes on losing).
	 */
	static void drainMidway(GameTestHelper helper, boolean protectMidway) {
		VillageHallBlockEntity hall = QaCreateDeployerUses1122eCompatTests.hall(helper, false);
		pipes(helper, "fluid_tank", "east");
		long full = 8 * BUCKET;
		fill(helper, EDGE, Fluids.WATER, full);
		long[] first = {-1};
		long[] after = {-1};
		helper.onEachTick(() -> {
			long now = amount(helper, EDGE, Fluids.WATER);
			if (first[0] < 0 && now < full) {
				first[0] = helper.getTick();
			}
			if (first[0] >= 0 && protectMidway && helper.getTick() == first[0] + SWITCH_AFTER) {
				hall.setProtected(true);
			}
			if (first[0] >= 0 && after[0] < 0 && helper.getTick() >= first[0] + 2 * SWITCH_AFTER) {
				after[0] = now;
			}
		});
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 300, () -> {
			long franks = amount(helper, EDGE, Fluids.WATER);
			String state = "Frank's tank " + after[0] + " -> " + franks + " droplets from tick " + (first[0] + 2 * SWITCH_AFTER) + " to 300 (water first left it at tick "
				+ first[0] + (protectMidway ? ", protection on at tick " + (first[0] + SWITCH_AFTER) : "") + "; " + pipeState(helper) + ")";
			helper.assertTrue(first[0] >= 0 && after[0] >= 0, "set-up: the stranger's pump never drained the open village's tank: " + state);
			if (protectMidway) {
				helper.assertTrue(franks == after[0], "Frank turned his village's protection on while a stranger's pump outside was draining his edge tank, and"
					+ " the water kept leaving: " + state);
			} else {
				helper.assertTrue(franks < after[0], "the control (village open throughout): the drain stopped by itself, so the protected case proves nothing: " + state);
			}
		});
	}

	/** The control: with the village left open the drain goes on past the moment protection would have been switched on. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123p_pipe_open_midway"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123p_pipe_open_midway")
	public void aStrangersPipeKeepsDrainingAnOpenVillagesTank(GameTestHelper helper) {
		drainMidway(helper, false);
	}

	/**
	 * Over-protection guard: Frank's own tank (8, 2, 15), pipe (9, 2, 15), pump (10, 2, 15) facing east and pipe
	 * (11, 2, 15), all inside his protected village, pump his water out into a stranger's tank just outside at (12, 2, 15).
	 * The owner's machines inside work as usual (create.md), and nothing inside changes against his will.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123p_own_pump_out"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123p_own_pump_out")
	public void theOwnersPumpInsideStillPumpsHisWaterOutToATankOutside(GameTestHelper helper) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, true);
		clear(helper);
		BlockPos tank = new BlockPos(8, 2, Z);
		BlockPos pump = new BlockPos(10, 2, Z);
		BlockPos outside = new BlockPos(12, 2, Z);
		BlockPos cog = pump.north();
		helper.assertTrue(inside(tank) && inside(pump) && inside(pump.east()) && inside(cog) && !inside(outside), "set-up: Frank's pump station isn't inside his village");
		helper.setBlock(tank, create("fluid_tank"));
		helper.setBlock(outside, create("fluid_tank"));
		helper.setBlock(pump, MechanicCompatTests.with(create("mechanical_pump"), "facing", "east"));
		helper.setBlock(pump.west(), piece("fluid_pipe"));
		helper.setBlock(pump.east(), piece("fluid_pipe"));
		helper.setBlock(cog, MechanicCompatTests.with(create("cogwheel"), "axis", "x"));
		helper.runAfterDelay(2, () -> helper.setBlock(cog.west(), MechanicCompatTests.with(create("creative_motor"), "facing", "east")));
		for (int t = 8; t <= 60; t += 4) {
			helper.runAfterDelay(t, () -> {
				BlockState state = helper.getBlockState(pump);
				if (speed(helper, pump) != 0 && !state.getValue(state.getBlock().getStateDefinition().getProperty("facing")).toString().equals("east")) {
					helper.getLevel().setBlock(helper.absolutePos(pump), MechanicCompatTests.with(state, "facing", "east"), 3);
				}
			});
		}
		fill(helper, tank, Fluids.WATER, 8 * BUCKET);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.hall.VillageProtection.mayMachineChange(helper.getLevel(), helper.absolutePos(pump), helper.absolutePos(tank)),
			"set-up: the protection rule refuses Frank's own pump inside his village");
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 300, () -> {
			long franks = amount(helper, tank, Fluids.WATER);
			long out = amount(helper, outside, Fluids.WATER);
			String state = "Frank's tank " + franks + " of " + 8 * BUCKET + " droplets, the tank outside " + out + " (pump Speed " + speed(helper, pump) + " "
				+ helper.getBlockState(pump) + ")";
			helper.assertTrue(speed(helper, pump) != 0, "set-up: Frank's pump never turned: " + state);
			helper.assertTrue(franks < 8 * BUCKET && out > 0, "over-protection: Frank's own pump inside his protected village didn't pump his water out to a tank"
				+ " just outside: " + state);
		});
	}

	// ---------------------------------------------------------------- 3. Packager on Frank's chest

	static final BlockPos PACKAGER = EDGE.east();

	static int count(Container c) {
		return QaCreateEdgeVerify1123eCompatTests.count(c, null);
	}

	/** Frank's chest at {@link #EDGE} holding {@code diamonds}; a stranger's Packager on its outside face (facing east, away from it). */
	static PackagerBlockEntity packager(GameTestHelper helper, int diamonds) {
		clear(helper);
		helper.setBlock(EDGE, Blocks.CHEST);
		Container chest = helper.getBlockEntity(EDGE);
		if (diamonds > 0) {
			chest.setItem(0, new ItemStack(Items.DIAMOND, diamonds));
		}
		helper.setBlock(PACKAGER, MechanicCompatTests.with(create("packager"), "facing", "east"));
		helper.assertTrue(inside(EDGE) && !inside(PACKAGER), "set-up: Frank's chest isn't at the village's edge");
		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(PACKAGER)) instanceof PackagerBlockEntity be)) {
			throw new GameTestAssertException("set-up: no Packager block entity at " + PACKAGER.toShortString());
		}
		return be;
	}

	static String packagerState(PackagerBlockEntity be) {
		return "packager holds " + be.heldBox + ", queued " + be.queuedExitingPackages.size() + ", powered " + be.redstonePowered;
	}

	/** A redstone block on the stranger's packager (a pulse, as a lever does) at tick 10: it packs Frank's chest only if the village is open. */
	static void pack(GameTestHelper helper, boolean protect) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, protect);
		PackagerBlockEntity be = packager(helper, 16);
		Container chest = helper.getBlockEntity(EDGE);
		QaCreateCannonPipes1122eCompatTests.ruleSays(helper, protect, PACKAGER, EDGE);
		helper.runAfterDelay(10, () -> helper.setBlock(PACKAGER.above(), Blocks.REDSTONE_BLOCK));
		helper.runAfterDelay(40, () -> helper.setBlock(PACKAGER.above(), Blocks.AIR));
		helper.runAfterDelay(50, () -> helper.setBlock(PACKAGER.above(), Blocks.REDSTONE_BLOCK));
		boolean[] wasPowered = {false};
		helper.onEachTick(() -> wasPowered[0] |= be.redstonePowered);
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 120, () -> {
			int left = count(chest);
			String state = left + " of 16 diamonds left in Frank's chest; " + packagerState(be);
			helper.assertTrue(wasPowered[0], "set-up: the redstone block never powered the packager: " + state);
			if (protect) {
				helper.assertTrue(left == 16 && be.heldBox.isEmpty() && be.queuedExitingPackages.isEmpty(), "a stranger's Packager outside Frank's protected village,"
					+ " on a redstone pulse, packed items out of his chest in its last column: " + state);
			} else {
				helper.assertTrue(left < 16, "the control (village open): the packager packed nothing from Frank's chest: " + state);
			}
		});
	}

	/** A stranger's packager on the outside face of Frank's chest packs nothing from it in his protected village. */
	//$ gametest_ticks_batch AREA '200' '"qa_e1123p_packager_pack_protected"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_e1123p_packager_pack_protected")
	public void aStrangersPackagerPacksNothingFromAChestInAProtectedVillage(GameTestHelper helper) {
		pack(helper, true);
	}

	/** The control: with the village open the same packager packs the chest's diamonds. */
	//$ gametest_ticks_batch AREA '200' '"qa_e1123p_packager_pack_open"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_e1123p_packager_pack_open")
	public void aStrangersPackagerPacksFromAChestInAnOpenVillage(GameTestHelper helper) {
		pack(helper, false);
	}

	/** A package of 8 gold ingots pushed (as a funnel or belt does) into the stranger's packager at tick 10: does it unpack into Frank's chest? */
	static void unpack(GameTestHelper helper, boolean protect) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, protect);
		PackagerBlockEntity be = packager(helper, 0);
		Container chest = helper.getBlockEntity(EDGE);
		QaCreateCannonPipes1122eCompatTests.ruleSays(helper, protect, PACKAGER, EDGE);
		long[] took = {-1};
		helper.runAfterDelay(10, () -> {
			ItemStack box = PackageItem.containing(List.of(new ItemStack(Items.GOLD_INGOT, 8)));
			Storage<ItemVariant> in = ItemStorage.SIDED.find(helper.getLevel(), helper.absolutePos(PACKAGER), Direction.EAST);
			helper.assertTrue(in != null, "set-up: the packager has no item storage on its east face");
			try (Transaction tx = Transaction.openOuter()) {
				took[0] = in.insert(ItemVariant.of(box), 1, tx);
				tx.commit();
			}
		});
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 80, () -> {
			int gold = QaCreateEdgeVerify1123eCompatTests.count(chest, Items.GOLD_INGOT);
			String state = gold + " gold ingots in Frank's chest; the packager took " + took[0] + " package(s); " + packagerState(be);
			if (protect) {
				helper.assertTrue(gold == 0 && count(chest) == 0, "a stranger's Packager outside Frank's protected village unpacked a package into his chest in its"
					+ " last column: " + state);
			} else {
				helper.assertTrue(gold == 8, "the control (village open): the packager didn't unpack the 8 gold ingots into Frank's chest: " + state);
			}
		});
	}

	/** The control: with the village open the package unpacks into Frank's chest. */
	//$ gametest_ticks_batch AREA '200' '"qa_e1123p_packager_unpack_open"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_e1123p_packager_unpack_open")
	public void aStrangersPackagerUnpacksIntoAChestInAnOpenVillage(GameTestHelper helper) {
		unpack(helper, false);
	}
}
