package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.speed;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
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
		clear(helper);
		helper.setBlock(EDGE.below(), Blocks.STONE);
		helper.setBlock(EDGE, create(target));
		helper.setBlock(THEIR_TANK, create("fluid_tank"));
		helper.setBlock(PUMP, MechanicCompatTests.with(create("mechanical_pump"), "facing", flow));
		BlockState pipe = create("fluid_pipe");
		for (String side : List.of("north", "south", "up", "down")) {
			pipe = MechanicCompatTests.with(pipe, side, "false");
		}
		helper.setBlock(PIPE, MechanicCompatTests.with(MechanicCompatTests.with(pipe, "east", "true"), "west", "true"));
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
