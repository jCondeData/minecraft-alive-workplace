package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.speed;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.schematics.cannon.ConfigureSchematicannonPacket;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import com.simibubi.create.foundation.utility.CreatePaths;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * QA (lane-e-1122-2213, Create and the pack), from the protection promise (wiki create.md, "a stranger's Create machine
 * standing outside a protected village breaks, moves and places nothing inside; the same machines inside the village, or
 * with protection off, work as usual"; village-hall.md, protection) on two Create machines no earlier test drove:
 *
 * <ol>
 *   <li>Create's <b>Schematicannon</b>, driven for real: a stranger's schematic file in Create's uploaded folder, a
 *       written and deployed Schematic naming it in the cannon's slot, a Creative Crate beside the cannon (materials and
 *       gunpowder), the cannon set RUNNING as its Play button does, then the cannon's own tick prints and its launched
 *       blocks land. The schematic is a row of stone, 6 long on z = 15 from x = 8 to 13, so its first four cells
 *       (x = 8..11) are inside Frank's village and the last two outside. "Don't replace" mode fills air; "replace any"
 *       mode against Frank's oak planks at x = 9 and 10.</li>
 *   <li>An <b>open-ended Fluid Pipe</b>: a stranger's Mechanical Pump and pipe outside the village, the pipe's open end
 *       facing into a glass cup on the village's last column (x = 11). Filling: the pump pushes water from a tank kept
 *       topped up. Draining: the cup holds one water source (no neighbouring source, so Create's infinite-water rule
 *       doesn't apply) and the pump pulls into the tank.</li>
 * </ol>
 *
 * Frank's hall is {@link QaCreateDeployerUses1122eCompatTests#hall}'s: at (3, 2, 15), the village 8 columns across, so on
 * z = 15 the last column inside is x = 11. Every case has a control (the village open: the same machine really places or
 * drains inside) and a guard against over-protection (Frank's own machine inside his protected village works). The
 * four protected cases fail today (found bugs) and live in QaCreateCannonPipes1122eBugCompatTests (on
 * tests/create-cannon-pipes-1122e), not registered until the fix. A moving
 * Saw (train) is not here: the moving breaker path is covered by the drill trains (QaCreateTrainActors1122e,
 * QaCreatePortableTrains1122e) and the piston-carried saw (QaCreateMachinesProtection1102).
 */
public class QaCreateCannonPipes1122eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	static final int Z = 15;
	static final String STRANGER_NAME = "qa_e1122_cannon_stranger";
	static final String FILE = "qa_e1122_row.nbt";

	static boolean inside(BlockPos p) {
		return QaCreateHosePulley1122eCompatTests.inside(p);
	}

	/**
	 * The mod's own rule for machines, asked at set-up: with Frank's village protected, a machine at {@code machine} may
	 * not change {@code target} (else the protected case would prove nothing), and with it open it may.
	 */
	static void ruleSays(GameTestHelper helper, boolean protect, BlockPos machine, BlockPos target) {
		boolean may = VillageProtection.mayMachineChange(helper.getLevel(), helper.absolutePos(machine), helper.absolutePos(target));
		helper.assertTrue(may != protect, "set-up: VillageProtection.mayMachineChange(" + machine.toShortString() + " -> " + target.toShortString() + ") is " + may
			+ " with the village " + (protect ? "protected" : "open"));
	}

	static BlockState create(String id) {
		return QaCreateDeployerOnOurThings1102eCompatTests.create(id);
	}

	// ---------------------------------------------------------------- 1. the Schematicannon

	/** A structure file as Create's Schematic Table saves one: a row of stone, {@code length} along x. */
	static CompoundTag row(int length) {
		CompoundTag tag = new CompoundTag();
		tag.put("size", ints(length, 1, 1));
		ListTag palette = new ListTag();
		CompoundTag stone = new CompoundTag();
		stone.putString("Name", "minecraft:stone");
		palette.add(stone);
		tag.put("palette", palette);
		ListTag blocks = new ListTag();
		for (int x = 0; x < length; x++) {
			CompoundTag b = new CompoundTag();
			b.put("pos", ints(x, 0, 0));
			b.putInt("state", 0);
			blocks.add(b);
		}
		tag.put("blocks", blocks);
		tag.put("entities", new ListTag());
		tag.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
		return tag;
	}

	static ListTag ints(int... values) {
		ListTag list = new ListTag();
		for (int v : values) {
			list.add(IntTag.valueOf(v));
		}
		return list;
	}

	/** Create's own uploaded folder (where its Schematicannon reads from) for {@code owner}. */
	static Path folder(String owner) {
		return CreatePaths.UPLOADED_SCHEMATICS_DIR.resolve(owner);
	}

	static void writeRow(String owner) {
		Path file = folder(owner).resolve(FILE);
		try {
			Files.createDirectories(file.getParent());
			NbtIo.writeCompressed(row(6), file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static void clearFolder(String owner) {
		Path dir = folder(owner);
		try {
			if (Files.isDirectory(dir)) {
				try (Stream<Path> walk = Files.walk(dir)) {
					for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
						Files.deleteIfExists(p);
					}
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** A written Schematic, deployed with its anchor at {@code anchor} (absolute), as the Schematic and Quill and placing it leave it. */
	static ItemStack deployed(String owner, BlockPos anchor) {
		ItemStack stack = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
			net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "schematic")));
		stack.set(AllDataComponents.SCHEMATIC_FILE, FILE);
		stack.set(AllDataComponents.SCHEMATIC_OWNER, owner);
		stack.set(AllDataComponents.SCHEMATIC_DEPLOYED, true);
		stack.set(AllDataComponents.SCHEMATIC_ANCHOR, anchor);
		return stack;
	}

	/**
	 * A Schematicannon at {@code cannon} with a Creative Crate on its {@code crateSide}, loaded with the row schematic
	 * anchored at {@code anchor} (test-relative), in {@code mode}, and set running (its Play button).
	 */
	static SchematicannonBlockEntity cannon(GameTestHelper helper, BlockPos cannon, Direction crateSide, BlockPos anchor, ConfigureSchematicannonPacket.Option mode) {
		helper.setBlock(cannon, create("schematicannon"));
		helper.setBlock(cannon.relative(crateSide), create("creative_crate"));
		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(cannon)) instanceof SchematicannonBlockEntity be)) {
			throw new GameTestAssertException("set-up: no Schematicannon block entity at " + cannon);
		}
		be.findInventories();
		be.replaceMode = mode.ordinal();
		be.inventory.setStackInSlot(0, deployed(STRANGER_NAME, helper.absolutePos(anchor)));
		be.state = SchematicannonBlockEntity.State.RUNNING;
		be.setChanged();
		return be;
	}

	static String cannonState(SchematicannonBlockEntity be) {
		return "cannon " + be.state + " \"" + be.statusMsg + "\", placed " + be.blocksPlaced + "/" + be.blocksToPlace + ", creative crate "
			+ be.hasCreativeCrate + ", replace mode " + be.replaceMode + ", flying " + be.flyingBlocks.size();
	}

	static String row(GameTestHelper helper, int from, int to) {
		StringBuilder sb = new StringBuilder();
		for (int x = from; x <= to; x++) {
			sb.append(x).append('=').append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(helper.getBlockState(new BlockPos(x, 2, Z)).getBlock()).getPath()).append(' ');
		}
		return sb.toString().trim();
	}

	/**
	 * The stranger's cannon at (16, 2, 15), outside, prints the row x = 8..13. {@code planks}: Frank's oak planks at x = 9
	 * and 10 and the cannon in "replace any". {@code protect}: no inside cell may change, while the outside cells x = 12,
	 * 13 must be printed (the cannon works); else the inside cells must be printed.
	 */
	static void strangersCannon(GameTestHelper helper, boolean protect, boolean planks) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, protect);
		clearFolder(STRANGER_NAME);
		writeRow(STRANGER_NAME);
		for (int x = 8; x <= 13; x++) {
			helper.setBlock(new BlockPos(x, 2, Z), planks && (x == 9 || x == 10) ? Blocks.OAK_PLANKS : Blocks.AIR);
		}
		BlockPos at = new BlockPos(16, 2, Z);
		SchematicannonBlockEntity be = cannon(helper, at, Direction.EAST, new BlockPos(8, 2, Z),
			planks ? ConfigureSchematicannonPacket.Option.REPLACE_ANY : ConfigureSchematicannonPacket.Option.DONT_REPLACE);
		helper.assertTrue(inside(new BlockPos(11, 2, Z)) && !inside(new BlockPos(12, 2, Z)) && !inside(at), "set-up: the village's edge isn't between x = 11 and 12");
		ruleSays(helper, protect, at, new BlockPos(9, 2, Z));
		AtomicReference<String> changedInside = new AtomicReference<>("");
		List<BlockState> before = new ArrayList<>();
		for (int x = 8; x <= 11; x++) {
			before.add(helper.getBlockState(new BlockPos(x, 2, Z)));
		}
		helper.onEachTick(() -> {
			for (int x = 8; x <= 11; x++) {
				BlockState now = helper.getBlockState(new BlockPos(x, 2, Z));
				if (changedInside.get().isEmpty() && now != before.get(x - 8)) {
					changedInside.set("(" + x + ", 2, " + Z + ") " + before.get(x - 8).getBlock().getName().getString() + " became " + now.getBlock().getName().getString()
						+ " at tick " + helper.getTick());
				}
			}
		});
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 400, () -> {
			clearFolder(STRANGER_NAME);
			boolean outsidePrinted = helper.getBlockState(new BlockPos(12, 2, Z)).is(Blocks.STONE) && helper.getBlockState(new BlockPos(13, 2, Z)).is(Blocks.STONE);
			String where = " (row x 8..13: " + row(helper, 8, 13) + "; " + cannonState(be) + ")";
			if (protect) {
				helper.assertTrue(changedInside.get().isEmpty(), "a stranger's Schematicannon outside Frank's protected village"
					+ (planks ? " in \"replace any\" mode" : "") + " changed a block inside it: " + changedInside.get() + where);
				helper.assertTrue(outsidePrinted, "the stranger's Schematicannon printed nothing even outside the village, so the test proves nothing" + where);
			} else {
				helper.assertTrue(outsidePrinted, "the control's Schematicannon didn't print the row's outside cells" + where);
				if (planks) {
					helper.assertTrue(helper.getBlockState(new BlockPos(9, 2, Z)).is(Blocks.STONE) && helper.getBlockState(new BlockPos(10, 2, Z)).is(Blocks.STONE),
						"the control (village open): the Schematicannon in \"replace any\" didn't replace the oak planks with stone" + where);
				} else {
					helper.assertTrue(helper.getBlockState(new BlockPos(8, 2, Z)).is(Blocks.STONE) && helper.getBlockState(new BlockPos(11, 2, Z)).is(Blocks.STONE),
						"the control (village open): the Schematicannon didn't print inside the village" + where);
				}
			}
		});
	}

	/** The control: with the village open, the same cannon prints the whole row, inside cells too. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_cannon_open"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_cannon_open")
	public void aStrangersSchematicannonPrintsIntoAnOpenVillage(GameTestHelper helper) {
		strangersCannon(helper, false, false);
	}

	/** The control: with the village open, "replace any" replaces Frank's planks with the schematic's stone. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_cannon_replace_open"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_cannon_replace_open")
	public void aStrangersSchematicannonReplacesBlocksInAnOpenVillage(GameTestHelper helper) {
		strangersCannon(helper, false, true);
	}

	/** Frank's own Schematicannon inside his protected village prints a row that is all inside it. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_cannon_owner"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_cannon_owner")
	public void theOwnersSchematicannonStillPrintsInHisProtectedVillage(GameTestHelper helper) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, true);
		clearFolder(STRANGER_NAME);
		writeRow(STRANGER_NAME);
		BlockPos at = new BlockPos(7, 2, 18);
		helper.assertTrue(inside(at) && inside(new BlockPos(5, 2, Z)) && inside(new BlockPos(10, 2, Z)), "set-up: the owner's cannon or row isn't inside");
		SchematicannonBlockEntity be = cannon(helper, at, Direction.SOUTH, new BlockPos(5, 2, Z), ConfigureSchematicannonPacket.Option.DONT_REPLACE);
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 400, () -> {
			clearFolder(STRANGER_NAME);
			for (int x = 5; x <= 10; x++) {
				helper.assertTrue(helper.getBlockState(new BlockPos(x, 2, Z)).is(Blocks.STONE), "Frank's own Schematicannon inside his protected village didn't print (" + x
					+ ", 2, " + Z + ") (row x 5..10: " + row(helper, 5, 10) + "; " + cannonState(be) + ")");
			}
		});
	}

	// ---------------------------------------------------------------- 2. an open-ended fluid pipe

	/**
	 * The cup cell {@code cup} (its open side up and east) walled in glass north, south and west; an open-ended Fluid Pipe
	 * east of it (connected east and west), a Mechanical Pump east of that (facing west when {@code fill}: tank to cup,
	 * else east), a Fluid Tank east of the pump, and a cogwheel north of the pump turned by a creative motor.
	 */
	static void pipe(GameTestHelper helper, BlockPos cup, boolean fill) {
		BlockPos pipe = cup.east();
		BlockPos pump = pipe.east();
		BlockPos tank = pump.east();
		BlockPos cog = pump.north();
		String flow = fill ? "west" : "east";
		helper.setBlock(cup.west(), Blocks.GLASS);
		helper.setBlock(cup.north(), Blocks.GLASS);
		helper.setBlock(cup.south(), Blocks.GLASS);
		helper.setBlock(cup.below(), Blocks.STONE);
		helper.setBlock(cup, fill ? Blocks.AIR : Blocks.WATER);
		BlockState pipeState = create("fluid_pipe");
		for (String side : List.of("north", "south", "up", "down")) {
			pipeState = MechanicCompatTests.with(pipeState, side, "false");
		}
		pipeState = MechanicCompatTests.with(MechanicCompatTests.with(pipeState, "east", "true"), "west", "true");
		helper.setBlock(pipe, pipeState);
		helper.setBlock(tank, create("fluid_tank"));
		helper.setBlock(pump, MechanicCompatTests.with(create("mechanical_pump"), "facing", flow));
		helper.setBlock(cog, MechanicCompatTests.with(create("cogwheel"), "axis", "x"));
		helper.runAfterDelay(2, () -> helper.setBlock(cog.east(), MechanicCompatTests.with(create("creative_motor"), "facing", "west")));
		// a pump turns itself round when its shaft's direction does; once it turns, set it the way the test wants it
		for (int t = 8; t <= 60; t += 4) {
			helper.runAfterDelay(t, () -> {
				BlockState state = helper.getBlockState(pump);
				if (speed(helper, pump) != 0 && !state.getValue(state.getBlock().getStateDefinition().getProperty("facing")).toString().equals(flow)) {
					helper.getLevel().setBlock(helper.absolutePos(pump), MechanicCompatTests.with(state, "facing", flow), 3);
				}
			});
		}
		if (fill) {
			helper.onEachTick(() -> {
				Storage<FluidVariant> storage = FluidStorage.SIDED.find(helper.getLevel(), helper.absolutePos(tank), Direction.UP);
				if (storage != null) {
					try (Transaction tx = Transaction.openOuter()) {
						storage.insert(FluidVariant.of(Fluids.WATER), 4 * QaCreateHosePulley1122eCompatTests.BUCKET, tx);
						tx.commit();
					}
				}
			});
		}
	}

	static String pipeState(GameTestHelper helper, BlockPos cup) {
		BlockPos pump = cup.east(2);
		return "cup " + helper.getBlockState(cup) + ", pipe " + helper.getBlockState(cup.east()) + ", pump Speed " + speed(helper, pump) + " "
			+ helper.getBlockState(pump) + ", tank " + QaPumpHouseTank1121eCompatTests.amount(helper.getLevel(), helper.absolutePos(cup.east(3))) + " droplets";
	}

	/**
	 * The pipe's cup at {@code cup}. {@code fill}: the cup starts dry and must (or must not, {@code wantChange} false) get
	 * water; else it holds one source that must (or must not) be drained.
	 */
	static void openPipe(GameTestHelper helper, boolean protect, BlockPos cup, boolean fill, boolean wantChange, String who) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, protect);
		pipe(helper, cup, fill);
		if (inside(cup) && !inside(cup.east())) {
			ruleSays(helper, protect, cup.east(), cup);
		}
		AtomicReference<String> changed = new AtomicReference<>("");
		helper.onEachTick(() -> {
			if (!changed.get().isEmpty() || helper.getTick() < 3) {
				return;
			}
			var fluid = helper.getLevel().getFluidState(helper.absolutePos(cup));
			boolean now = fill ? !fluid.isEmpty() : !fluid.isSourceOfType(Fluids.WATER);
			if (now) {
				changed.set(helper.getBlockState(cup) + " at tick " + helper.getTick());
			}
		});
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 400, () -> {
			helper.assertTrue(speed(helper, cup.east(2)) != 0, "the pump never turned, so the test proves nothing (" + pipeState(helper, cup) + ")");
			if (wantChange) {
				helper.assertTrue(!changed.get().isEmpty(), who + ": the open-ended pipe " + (fill ? "poured no water into" : "drained no water from")
					+ " the cup in 400 ticks (" + pipeState(helper, cup) + ")");
			} else {
				helper.assertTrue(changed.get().isEmpty(), "a stranger's open-ended Fluid Pipe and Mechanical Pump outside Frank's protected village "
					+ (fill ? "poured water into" : "drained the water source from") + " the cup inside it at " + cup.toShortString() + ": " + changed.get()
					+ " (" + pipeState(helper, cup) + ")");
			}
		});
	}

	/** The stranger's cup: the village's last column on z = 15. */
	static final BlockPos CUP = new BlockPos(11, 2, Z);
	/** Frank's own cup, pipe, pump and tank, all inside his village (x 6..9). */
	static final BlockPos OWN_CUP = new BlockPos(6, 2, Z);

	static void checkLayout(GameTestHelper helper) {
		helper.assertTrue(inside(CUP) && !inside(CUP.east()) && inside(OWN_CUP.east(3)) && inside(OWN_CUP.east(2).north()), "set-up: the pipes aren't where the village's edge is");
	}

	/** The control: with the village open, the same pipe pours water into the cup. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_pipe_fill_open"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_pipe_fill_open")
	public void aStrangersOpenPipePoursWaterIntoAnOpenVillage(GameTestHelper helper) {
		openPipe(helper, false, CUP, true, true, "the control (village open)");
	}

	/** Frank's own open pipe inside his protected village pours water. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_pipe_fill_owner"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_pipe_fill_owner")
	public void theOwnersOpenPipeStillPoursInHisProtectedVillage(GameTestHelper helper) {
		openPipe(helper, true, OWN_CUP, true, true, "Frank's own pipe inside his protected village");
	}

	/** The control: with the village open, the same pipe drains the cup's source. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_pipe_drain_open"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_pipe_drain_open")
	public void aStrangersOpenPipeDrainsWaterFromAnOpenVillage(GameTestHelper helper) {
		openPipe(helper, false, CUP, false, true, "the control (village open)");
	}

	/** Frank's own open pipe inside his protected village drains his cup. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_pipe_drain_owner"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_pipe_drain_owner")
	public void theOwnersOpenPipeStillDrainsInHisProtectedVillage(GameTestHelper helper) {
		openPipe(helper, true, OWN_CUP, false, true, "Frank's own pipe inside his protected village");
	}
}
