package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.held;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.speed;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.schematics.cannon.ConfigureSchematicannonPacket;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * QA (lane-e-1123, independent check of lane A's fixes for B1488/B1495, B1501, B1505, B1516, B1517 and B1519), written
 * from the bug reports and the protection promise (create.md: a stranger's Create machine standing outside a protected
 * village breaks, places, moves, pours, drains or trades nothing inside it; the owner, his friends and operators, and an
 * open village are unaffected), on the cases the reports or the fixes left untested:
 *
 * <ul>
 *   <li>B1519 the other way: a stranger's docked tank filling Frank's tank through his Portable Fluid Interface.</li>
 *   <li>B1488: Frank's own piston contraption docking at his own interface inside his protected village; protection
 *       switched on while a stranger's contraption is already docked (the re-link every tick).</li>
 *   <li>B1501: a lava bucket; a water bucket aimed at a cell outside (still pours); a deployer turned north, into the
 *       village from another side; a friend of Frank's deployer outside (may pour).</li>
 *   <li>B1505: only the basin's outside cells fill; the pulley and hall saved and loaded mid-drain.</li>
 *   <li>B1516: the Schematicannon fed from a chest with gunpowder instead of a Creative Crate.</li>
 *   <li>B1517: lava through the open pipe. (A pipe pointing straight down or up into a village cell would itself stand
 *       in the village's column, so "vertical from outside" can't exist: protection counts columns.)</li>
 * </ul>
 *
 * Frank's hall is {@link QaCreateDeployerUses1122eCompatTests#hall}'s: at (3, 2, 15), 8 columns across, so on z = 15 the
 * last column inside is x = 11. Each refusal carries evidence that the machine ran (an outside part changed, or the
 * open-village control here passes on the same set-up).
 */
public class QaCreateEdgeVerify1123eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	static final int Z = 15;
	static final UUID FRANK = QaCreateDeployerUses1122eCompatTests.FRANK;
	static final UUID STRANGER = QaCreateDeployerUses1122eCompatTests.STRANGER;
	static final UUID FRIEND = UUID.fromString("f1e0d123-0000-4e00-9a00-0000000e1123");
	static final long BUCKET = QaCreateHosePulley1122eCompatTests.BUCKET;

	static boolean inside(BlockPos p) {
		return QaCreateHosePulley1122eCompatTests.inside(p);
	}

	static BlockState create(String id) {
		return MechanicCompatTests.create(id).defaultBlockState();
	}

	static void discardContraptions(GameTestHelper helper) {
		for (AbstractContraptionEntity e : helper.getLevel().getEntitiesOfClass(AbstractContraptionEntity.class, helper.getBounds().inflate(8))) {
			e.discard();
		}
	}

	static boolean contraptionsExist(GameTestHelper helper) {
		return !helper.getLevel().getEntitiesOfClass(AbstractContraptionEntity.class, helper.getBounds().inflate(8)).isEmpty();
	}

	static int count(Container c, Item item) {
		int n = 0;
		for (int s = 0; s < c.getContainerSize(); s++) {
			if (item == null ? !c.getItem(s).isEmpty() : c.getItem(s).is(item)) {
				n += c.getItem(s).getCount();
			}
		}
		return n;
	}

	/** Saves the block entity at {@code pos} and loads it again in its place, as a chunk unloaded and loaded does. */
	static void reload(GameTestHelper helper, BlockPos pos) {
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
			throw new GameTestAssertException("reload: the block entity at " + pos.toShortString() + " didn't load");
		}
		level.setBlockEntity(again);
	}

	static void ruleSays(GameTestHelper helper, boolean protect, BlockPos machine, BlockPos target) {
		QaCreateCannonPipes1122eCompatTests.ruleSays(helper, protect, machine, target);
	}

	// ---------------------------------------------------------------- 1. Portable Storage Interface (B1488)

	/**
	 * A PSI station on row {@code z} shifted {@code shift} columns from QaCreatePortableTrains1122e's: Frank's chest of 32
	 * emeralds over a hopper feeding his interface (facing east), a hopper under it into his barrel; a piston east of it
	 * pushes a chest of 32 diamonds and a moving interface two steps west, docking across one gap.
	 */
	record Station(BlockPos stationary, BlockPos chest, BlockPos feeder, BlockPos drain, BlockPos barrel, BlockPos moving, BlockPos cartChest, BlockPos piston) {
		static Station at(int shift, int z) {
			return new Station(new BlockPos(11 + shift, 2, z), new BlockPos(10 + shift, 3, z), new BlockPos(10 + shift, 2, z), new BlockPos(11 + shift, 1, z),
				new BlockPos(10 + shift, 1, z), new BlockPos(15 + shift, 2, z), new BlockPos(16 + shift, 2, z), new BlockPos(17 + shift, 2, z));
		}
	}

	static BlockState psi(String facing) {
		return MechanicCompatTests.with(create("portable_storage_interface"), "facing", facing);
	}

	static void build(GameTestHelper helper, Station s) {
		for (int x = Math.max(0, s.feeder().getX() - 1); x <= s.piston().getX() + 3; x++) {
			for (int y = 2; y <= 4; y++) {
				helper.setBlock(new BlockPos(x, y, s.stationary().getZ()), Blocks.AIR);
			}
		}
		helper.setBlock(s.barrel(), Blocks.BARREL);
		helper.setBlock(s.drain(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.WEST));
		helper.setBlock(s.stationary(), psi("east"));
		helper.setBlock(s.feeder(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
		helper.setBlock(s.chest(), Blocks.CHEST);
		((Container) helper.getBlockEntity(s.chest())).setItem(0, new ItemStack(Items.EMERALD, 32));
		helper.setBlock(s.cartChest(), Blocks.CHEST);
		((Container) helper.getBlockEntity(s.cartChest())).setItem(0, new ItemStack(Items.DIAMOND, 32));
		helper.setBlock(s.moving(), psi("west"));
		QaCreateIntoProtectedVillage1107eCompatTests.piston(helper, s.piston());
	}

	/** Emeralds still on Frank's side (chest and feeder hopper), and every item in his barrel. */
	static int franksEmeralds(GameTestHelper helper, Station s) {
		return count((Container) helper.getBlockEntity(s.chest()), Items.EMERALD) + count((Container) helper.getBlockEntity(s.feeder()), Items.EMERALD);
	}

	static int barrel(GameTestHelper helper, Station s) {
		return count((Container) helper.getBlockEntity(s.barrel()), null);
	}

	/** Over-protection guard: Frank's own piston, inside his protected village, docks its interface at his and trades. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1123_psi_owner_piston"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1123_psi_owner_piston")
	public void theOwnersPistonContraptionStillTradesAtHisInterfaceInHisProtectedVillage(GameTestHelper helper) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, true);
		Station s = Station.at(-10, 18);
		helper.assertTrue(inside(s.feeder()) && inside(s.piston()) && inside(s.piston().east(2)), "set-up: Frank's piston station isn't all inside his village");
		build(helper, s);
		boolean[] moved = {false};
		helper.onEachTick(() -> moved[0] |= contraptionsExist(helper));
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 420, () -> {
			int kept = franksEmeralds(helper, s);
			int diamonds = count((Container) helper.getBlockEntity(s.barrel()), Items.DIAMOND);
			float turning = speed(helper, s.piston());
			discardContraptions(helper);
			helper.assertTrue(turning != 0 && moved[0], "set-up: Frank's piston never put its contraption together (speed " + turning + ")");
			helper.assertTrue(kept < 32 || diamonds > 0, "Frank's own piston contraption, inside his protected village, docked at his own Portable Storage"
				+ " Interface and traded nothing: " + kept + " of 32 emeralds still on his side, " + diamonds + " diamonds in his barrel");
		});
	}

	/**
	 * A stranger's contraption docks at Frank's interface while his village is open; the tick the first item passes
	 * (an emerald leaves his side or something lands in his barrel) Frank turns protection on. {@code protectMidway}:
	 * from {@link #GRACE} ticks later nothing more may pass; else (the control, still open) trading goes on past then.
	 */
	static void midDock(GameTestHelper helper, boolean protectMidway) {
		VillageHallBlockEntity hall = QaCreateDeployerUses1122eCompatTests.hall(helper, false);
		Station s = Station.at(0, Z);
		build(helper, s);
		boolean[] moved = {false};
		long[] first = {-1};
		int[] after = {-1, -1};
		helper.onEachTick(() -> {
			moved[0] |= contraptionsExist(helper);
			if (first[0] < 0 && (franksEmeralds(helper, s) < 32 || barrel(helper, s) > 0)) {
				first[0] = helper.getTick();
				if (protectMidway) {
					hall.setProtected(true);
				}
			}
			if (first[0] >= 0 && after[0] < 0 && helper.getTick() >= first[0] + GRACE) {
				after[0] = franksEmeralds(helper, s);
				after[1] = barrel(helper, s);
			}
		});
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 450, () -> {
			int kept = franksEmeralds(helper, s);
			int inBarrel = barrel(helper, s);
			discardContraptions(helper);
			helper.assertTrue(moved[0], "set-up: the stranger's piston never put its contraption together");
			helper.assertTrue(first[0] >= 0 && after[0] >= 0, "set-up: the stranger's contraption never docked and traded with the open village (first item at tick "
				+ first[0] + ")");
			String counts = "emeralds on Frank's side " + after[0] + " -> " + kept + ", items in his barrel " + after[1] + " -> " + inBarrel
				+ " (ticks " + (first[0] + GRACE) + " -> 450; first item passed at tick " + first[0] + ")";
			if (protectMidway) {
				helper.assertTrue(kept == after[0] && inBarrel == after[1], "Frank turned his village's protection on while a stranger's contraption (piston"
					+ " outside) was docked at his Portable Storage Interface, and it kept trading: " + counts);
			} else {
				helper.assertTrue(kept < after[0] || inBarrel > after[1], "the control (village open throughout): the docked contraption stopped trading "
					+ GRACE + " ticks after it began, so the protected case proves nothing: " + counts);
			}
		});
	}

	static final int GRACE = 12;

	/** Protection switched on while a stranger's contraption is docked at Frank's interface: from then on nothing passes. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1123_psi_protect_midway"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1123_psi_protect_midway")
	public void protectionSwitchedOnWhileAStrangersContraptionIsDockedStopsTheTrade(GameTestHelper helper) {
		midDock(helper, true);
	}

	/** The control: with the village left open the docked contraption is still trading after tick 170. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1123_psi_open_midway"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1123_psi_open_midway")
	public void aDockedContraptionKeepsTradingWithAnOpenVillage(GameTestHelper helper) {
		midDock(helper, false);
	}

	// ---------------------------------------------------------------- 2. Portable Fluid Interface, the other way (B1519)

	static final BlockPos PFI = new BlockPos(11, 2, Z);
	static final BlockPos PFI_PUMP = PFI.west();
	static final BlockPos PFI_TANK = PFI_PUMP.west();
	static final BlockPos PFI_COG = PFI_PUMP.north();
	static final BlockPos PFI_MOVING = new BlockPos(15, 2, Z);
	static final BlockPos PFI_CART_TANK = new BlockPos(16, 2, Z);
	static final BlockPos PFI_PISTON = new BlockPos(17, 2, Z);

	static long water(GameTestHelper helper, BlockPos pos) {
		return QaPumpHouseTank1121eCompatTests.amount(helper.getLevel(), helper.absolutePos(pos));
	}

	/**
	 * Frank's empty tank, his pump pulling from his interface (facing west: interface to tank); the stranger's piston
	 * pushes his tank of 8 buckets and moving interface to the edge. Protected: none of the stranger's water may reach
	 * Frank's tank; open: it does.
	 */
	static void fluidInto(GameTestHelper helper, boolean protect) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, protect);
		for (int x = 7; x <= 20; x++) {
			for (int y = 2; y <= 4; y++) {
				for (int z = Z - 1; z <= Z; z++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(PFI_TANK, create("fluid_tank"));
		helper.setBlock(PFI, MechanicCompatTests.with(create("portable_fluid_interface"), "facing", "east"));
		helper.setBlock(PFI_PUMP, MechanicCompatTests.with(create("mechanical_pump"), "facing", "west"));
		helper.setBlock(PFI_COG, MechanicCompatTests.with(create("cogwheel"), "axis", "x"));
		helper.runAfterDelay(2, () -> helper.setBlock(PFI_COG.east(), MechanicCompatTests.with(create("creative_motor"), "facing", "west")));
		for (int t = 8; t <= 60; t += 4) {
			helper.runAfterDelay(t, () -> {
				BlockState state = helper.getBlockState(PFI_PUMP);
				if (speed(helper, PFI_PUMP) != 0 && !state.getValue(state.getBlock().getStateDefinition().getProperty("facing")).toString().equals("west")) {
					helper.getLevel().setBlock(helper.absolutePos(PFI_PUMP), MechanicCompatTests.with(state, "facing", "west"), 3);
				}
			});
		}
		helper.setBlock(PFI_CART_TANK, create("fluid_tank"));
		Storage<FluidVariant> theirs = FluidStorage.SIDED.find(helper.getLevel(), helper.absolutePos(PFI_CART_TANK), Direction.UP);
		helper.assertTrue(theirs != null, "set-up: the stranger's Fluid Tank has no fluid storage");
		long put;
		try (Transaction tx = Transaction.openOuter()) {
			put = theirs.insert(FluidVariant.of(Fluids.WATER), 8 * BUCKET, tx);
			tx.commit();
		}
		helper.assertTrue(put == 8 * BUCKET, "set-up: the stranger's tank took only " + put + " droplets of 8 buckets");
		helper.setBlock(PFI_MOVING, MechanicCompatTests.with(create("portable_fluid_interface"), "facing", "west"));
		helper.assertTrue(inside(PFI) && inside(PFI_TANK) && !inside(PFI.east()), "set-up: Frank's station isn't at the village's edge");
		ruleSays(helper, protect, PFI_PISTON, PFI);
		QaCreateIntoProtectedVillage1107eCompatTests.piston(helper, PFI_PISTON);
		boolean[] moved = {false};
		helper.onEachTick(() -> moved[0] |= contraptionsExist(helper));
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 400, () -> {
			long franks = water(helper, PFI_TANK);
			float pumpTurning = speed(helper, PFI_PUMP);
			String pump = helper.getBlockState(PFI_PUMP).toString();
			discardContraptions(helper);
			helper.assertTrue(moved[0], "set-up: the stranger's piston never put its contraption together");
			helper.assertTrue(pumpTurning != 0, "set-up: Frank's pump never turned (" + pump + ")");
			if (protect) {
				helper.assertTrue(franks == 0, "a stranger's contraption (piston outside Frank's protected village) docked at his Portable Fluid Interface and"
					+ " filled his tank: " + franks + " droplets of its water arrived there");
			} else {
				helper.assertTrue(franks > 0, "the control (village open): no water came from the docked contraption into Frank's tank (pump " + pump + ")");
			}
		});
	}

	/** B1519 the other way: a stranger's docked tank puts no water into Frank's tank through his fluid interface. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1123_pfi_into_protected"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1123_pfi_into_protected")
	public void aStrangersContraptionPoursNothingThroughAProtectedVillagesFluidInterface(GameTestHelper helper) {
		fluidInto(helper, true);
	}

	/** The control: in an open village the docked contraption's water reaches Frank's tank. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1123_pfi_into_open"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1123_pfi_into_open")
	public void aContraptionPoursThroughAnOpenVillagesFluidInterface(GameTestHelper helper) {
		fluidInto(helper, false);
	}

	// ---------------------------------------------------------------- 3. Deployer buckets (B1501)

	/**
	 * A deployer at {@code at} facing {@code facing} (a creative motor on its {@code motorSide}), owned by {@code owner},
	 * holding {@code stack}; turned the way that meets the motor's shaft.
	 */
	static void deployer(GameTestHelper helper, BlockPos at, String facing, Direction motorSide, UUID owner, ItemStack stack) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(at, MechanicCompatTests.with(create("deployer"), "facing", facing));
		BlockEntity be = level.getBlockEntity(helper.absolutePos(at));
		if (be == null) {
			throw new GameTestAssertException("set-up: no deployer block entity at " + at);
		}
		CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
		tag.putUUID("Owner", owner);
		be.loadWithComponents(tag, level.registryAccess());
		helper.runAfterDelay(2, () -> {
			long in = QaCreateDeployerOnOurThings1102eCompatTests.insert(level, helper.absolutePos(at), stack);
			helper.assertTrue(in == stack.getCount(), "set-up: the deployer took " + in + " of " + stack);
			helper.setBlock(at.relative(motorSide), MechanicCompatTests.with(create("creative_motor"), "facing", motorSide.getOpposite().getSerializedName()));
		});
		helper.runAfterDelay(6, () -> {
			if (speed(helper, at) == 0) {
				BlockState state = helper.getBlockState(at);
				boolean first = Boolean.parseBoolean(state.getValue(state.getBlock().getStateDefinition().getProperty("axis_along_first")).toString());
				helper.getLevel().setBlock(helper.absolutePos(at), MechanicCompatTests.with(state, "axis_along_first", String.valueOf(!first)), 3);
			}
		});
	}

	/** Cells in the box {@code from}..{@code to} (inclusive) whose fluid is {@code fluid} ({@code sources}: sources only), in or out of the village. */
	static String fluidAt(GameTestHelper helper, BlockPos from, BlockPos to, Fluid fluid, boolean sources, boolean in) {
		StringBuilder at = new StringBuilder();
		for (BlockPos p : BlockPos.betweenClosed(from, to)) {
			if (inside(p) != in) {
				continue;
			}
			var state = helper.getLevel().getFluidState(helper.absolutePos(p));
			boolean hit = sources ? state.isSourceOfType(fluid) : state.getType().isSame(fluid) || (fluid == Fluids.LAVA && state.is(FluidTags.LAVA));
			if (hit) {
				at.append(' ').append(p.getX()).append(',').append(p.getY()).append(',').append(p.getZ());
			}
		}
		return at.toString();
	}

	/** A stone wall one column behind {@code target}'s column (away from the deployer, {@code back}) at the hand's and eye's heights. */
	static void wall(GameTestHelper helper, BlockPos target, Direction back) {
		for (int y = target.getY(); y <= target.getY() + 2; y++) {
			helper.setBlock(new BlockPos(target.getX(), y, target.getZ()).relative(back), Blocks.STONE);
		}
	}

	/** Records the first time {@code probe} returns something, with the tick. */
	static AtomicReference<String> watch(GameTestHelper helper, java.util.function.Supplier<String> probe) {
		AtomicReference<String> seen = new AtomicReference<>("");
		helper.onEachTick(() -> {
			if (seen.get().isEmpty()) {
				String now = probe.get();
				if (!now.isEmpty()) {
					seen.set(now + " (tick " + helper.getTick() + ")");
				}
			}
		});
		return seen;
	}

	static final BlockPos DEPLOYER = QaCreateDeployerUses1122eCompatTests.DEPLOYER;
	static final BlockPos TARGET = QaCreateDeployerUses1122eCompatTests.TARGET;

	/** A stranger's deployer at the edge with a lava bucket pours no lava into the protected village. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123_duse_lava_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123_duse_lava_protected")
	public void aStrangersDeployerPoursNoLavaIntoAProtectedVillage(GameTestHelper helper) {
		lava(helper, true);
	}

	/** The control: with the village open the same deployer pours lava into its last column. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123_duse_lava_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123_duse_lava_open")
	public void aStrangersDeployerPoursLavaIntoAnOpenVillage(GameTestHelper helper) {
		lava(helper, false);
	}

	static void lava(GameTestHelper helper, boolean protect) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, protect);
		wall(helper, TARGET, Direction.WEST);
		deployer(helper, DEPLOYER, "west", Direction.NORTH, STRANGER, new ItemStack(Items.LAVA_BUCKET));
		BlockPos from = new BlockPos(4, 2, Z - 2);
		BlockPos to = new BlockPos(11, 5, Z + 2);
		AtomicReference<String> seen = watch(helper, () -> fluidAt(helper, from, to, Fluids.LAVA, false, true));
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 300, () -> {
			helper.assertTrue(speed(helper, DEPLOYER) != 0, "the deployer never turned, so the test proves nothing");
			if (protect) {
				helper.assertTrue(seen.get().isEmpty(), "a stranger's Deployer with a lava bucket, outside Frank's protected village, poured lava inside it at x,y,z"
					+ seen.get() + " (holding " + held(helper, DEPLOYER) + ")");
			} else {
				helper.assertTrue(!seen.get().isEmpty(), "the control (village open): the stranger's deployer poured no lava into the village (holding "
					+ held(helper, DEPLOYER) + ")");
			}
		});
	}

	/**
	 * Outside unaffected: a stranger's deployer two columns further out (x = 15) aims at x = 13 against a wall at x = 12,
	 * every cell outside the protected village: it pours its water there, and no source appears inside.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123_duse_pour_outside"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123_duse_pour_outside")
	public void aStrangersDeployerStillPoursJustOutsideAProtectedVillage(GameTestHelper helper) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, true);
		BlockPos at = new BlockPos(15, 2, Z);
		BlockPos target = at.west(2);
		helper.assertTrue(!inside(target) && !inside(target.west()) && inside(target.west(2)), "set-up: the target and wall aren't just outside the edge");
		wall(helper, target, Direction.WEST);
		deployer(helper, at, "west", Direction.NORTH, STRANGER, new ItemStack(Items.WATER_BUCKET));
		AtomicReference<String> outside = watch(helper, () -> fluidAt(helper, new BlockPos(12, 2, Z - 2), new BlockPos(14, 5, Z + 2), Fluids.WATER, true, false));
		AtomicReference<String> in = watch(helper, () -> fluidAt(helper, new BlockPos(4, 2, Z - 3), new BlockPos(11, 5, Z + 3), Fluids.WATER, true, true));
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 300, () -> {
			helper.assertTrue(speed(helper, at) != 0, "the deployer never turned, so the test proves nothing");
			helper.assertTrue(in.get().isEmpty(), "a stranger's deployer aimed outside Frank's protected village made a water source inside it at" + in.get());
			helper.assertTrue(!outside.get().isEmpty(), "over-protection: a stranger's Deployer with a water bucket aimed at x = 13 against a wall at x = 12, all"
				+ " outside Frank's protected village, poured nothing there (holding " + held(helper, at) + ")");
		});
	}

	/**
	 * A stranger's deployer beyond the village's south side at (3, 2, 25), facing north (toward the hall), acting on
	 * (3, 2, 23), the village's last cell on that column, against a wall at z = 22.
	 */
	static void turned(GameTestHelper helper, boolean protect) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, protect);
		BlockPos at = new BlockPos(3, 2, 25);
		BlockPos target = at.north(2);
		helper.assertTrue(!inside(at) && !inside(at.north()) && inside(target), "set-up: the turned deployer isn't just outside the village's north side");
		for (int x = 1; x <= 5; x++) {
			for (int y = 2; y <= 5; y++) {
				for (int z = 21; z <= 24; z++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		wall(helper, target, Direction.NORTH);
		deployer(helper, at, "north", Direction.EAST, STRANGER, new ItemStack(Items.WATER_BUCKET));
		AtomicReference<String> in = watch(helper, () -> fluidAt(helper, new BlockPos(0, 2, 19), new BlockPos(7, 5, 24), Fluids.WATER, true, true));
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 300, () -> {
			helper.assertTrue(speed(helper, at) != 0, "the turned deployer never turned, so the test proves nothing");
			if (protect) {
				helper.assertTrue(in.get().isEmpty(), "a stranger's Deployer facing north into Frank's protected village from outside it poured water inside at x,y,z"
					+ in.get() + " (holding " + held(helper, at) + ")");
			} else {
				helper.assertTrue(!in.get().isEmpty(), "the control (village open): the north-facing deployer poured no water into the village (holding "
					+ held(helper, at) + ", at the target " + helper.getBlockState(target) + ")");
			}
		});
	}

	/** A stranger's deployer facing the village from its north side (not west) still pours nothing inside. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123_duse_turned_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123_duse_turned_protected")
	public void aStrangersDeployerFacingNorthPoursNoWaterIntoAProtectedVillage(GameTestHelper helper) {
		turned(helper, true);
	}

	/** The control: with the village open the north-facing deployer pours inside. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123_duse_turned_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123_duse_turned_open")
	public void aStrangersDeployerFacingNorthPoursWaterIntoAnOpenVillage(GameTestHelper helper) {
		turned(helper, false);
	}

	/** Over-protection guard: a deployer owned by Frank's friend, outside his protected village, may pour water into it. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1123_duse_friend_pour"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1123_duse_friend_pour")
	public void aFriendsDeployerOutsideStillPoursIntoTheProtectedVillage(GameTestHelper helper) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, true);
		Friends friends = Friends.get(helper.getLevel().getServer());
		friends.add(FRANK, FRIEND, "Fred");
		wall(helper, TARGET, Direction.WEST);
		deployer(helper, DEPLOYER, "west", Direction.NORTH, FRIEND, new ItemStack(Items.WATER_BUCKET));
		AtomicReference<String> in = watch(helper, () -> fluidAt(helper, new BlockPos(4, 2, Z - 2), new BlockPos(11, 5, Z + 2), Fluids.WATER, true, true));
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 300, () -> {
			friends.remove(FRANK, FRIEND);
			helper.assertTrue(speed(helper, DEPLOYER) != 0, "the deployer never turned, so the test proves nothing");
			helper.assertTrue(!in.get().isEmpty(), "over-protection: a Deployer owned by Frank's friend, outside his protected village, poured no water into"
				+ " it (holding " + held(helper, DEPLOYER) + "); friends may empty buckets there");
		});
	}

	// ---------------------------------------------------------------- 4. Hose Pulley (B1505)

	static final int PULLEY_X = QaCreateHosePulley1122eCompatTests.STRANGER_X;

	static boolean source(GameTestHelper helper, BlockPos p) {
		return QaCreateHosePulley1122eCompatTests.source(helper, p);
	}

	/** Each basin cell (in or out of the village) with the tick it first held a source, or "dry". */
	static String cells(List<BlockPos> cells, java.util.Map<BlockPos, Long> wet) {
		StringBuilder sb = new StringBuilder();
		for (BlockPos p : cells) {
			sb.append(p.getX()).append(',').append(p.getZ()).append(wet.containsKey(p) ? "@" + wet.get(p) : ":dry").append(' ');
		}
		return sb.toString().trim();
	}

	static void fillEdge(GameTestHelper helper, boolean protect, int ticks) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, protect);
		QaCreateHosePulley1122eCompatTests.basin(helper, PULLEY_X, false);
		QaCreateHosePulley1122eCompatTests.machine(helper, PULLEY_X, false);
		List<BlockPos> all = QaCreateHosePulley1122eCompatTests.basin(PULLEY_X);
		List<BlockPos> in = all.stream().filter(QaCreateEdgeVerify1123eCompatTests::inside).toList();
		List<BlockPos> out = all.stream().filter(p -> !inside(p)).toList();
		java.util.Map<BlockPos, Long> wet = new java.util.HashMap<>();
		helper.onEachTick(() -> {
			for (BlockPos p : all) {
				if (!wet.containsKey(p) && source(helper, p)) {
					wet.put(p, helper.getTick());
				}
			}
		});
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, ticks, () -> {
			String state = QaCreateHosePulley1122eCompatTests.state(helper, PULLEY_X);
			long wetOut = out.stream().filter(wet::containsKey).count();
			long wetIn = in.stream().filter(wet::containsKey).count();
			String where = "outside " + cells(out, wet) + "; inside " + cells(in, wet) + " (" + state + ")";
			helper.assertTrue(!in.isEmpty() && !out.isEmpty(), "set-up: the basin doesn't span the edge");
			if (protect) {
				helper.assertTrue(wetIn == 0, "a stranger's Hose Pulley outside Frank's protected village poured a water source inside it: " + where);
				helper.assertTrue(wetOut == out.size(), "the stranger's pulley filled only " + wetOut + " of the basin's " + out.size() + " cells outside Frank's"
					+ " protected village, which should be unaffected: " + where);
			} else {
				// (an open village's whole basin takes longer than a test: about 125 ticks a source; the open control is in
				// QaCreateHosePulley1122eCompatTests)
				helper.assertTrue(wetIn > 0, "the control (village open): the pulley filled no basin cell inside the village: " + where);
			}
		});
	}

	/** The stranger's pulley over the dry basin, fed water: every basin cell outside the village fills, none inside. */
	//$ gametest_ticks_batch AREA '1100' '"qa_e1123_hose_fill_outside_only"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qa_e1123_hose_fill_outside_only")
	public void aStrangersHosePulleyFillsOnlyTheBasinsOutsideCells(GameTestHelper helper) {
		fillEdge(helper, true, 1000);
	}

	/** The pulley and hall saved and loaded mid-drain: the stranger's pulley still drains no source inside, and goes on outside. */
	//$ gametest_ticks_batch AREA '600' '"qa_e1123_hose_drain_reloaded"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_e1123_hose_drain_reloaded")
	public void aStrangersHosePulleyStillDrainsNothingInsideAfterASaveAndLoad(GameTestHelper helper) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, true);
		QaCreateHosePulley1122eCompatTests.basin(helper, PULLEY_X, true);
		QaCreateHosePulley1122eCompatTests.machine(helper, PULLEY_X, true);
		BlockPos pulley = new BlockPos(PULLEY_X, QaCreateHosePulley1122eCompatTests.Y, Z);
		List<BlockPos> in = QaCreateHosePulley1122eCompatTests.basin(PULLEY_X).stream().filter(QaCreateEdgeVerify1123eCompatTests::inside).toList();
		List<BlockPos> out = QaCreateHosePulley1122eCompatTests.basin(PULLEY_X).stream().filter(p -> !inside(p)).toList();
		AtomicReference<String> lostIn = watch(helper, () -> in.stream().filter(p -> !source(helper, p)).map(p -> p.toShortString() + " became "
			+ helper.getBlockState(p)).findFirst().orElse(""));
		int[] outAtReload = {-1};
		boolean[] reloaded = {false};
		helper.runAfterDelay(150, () -> {
			outAtReload[0] = (int) out.stream().filter(p -> !source(helper, p)).count();
			reload(helper, pulley);
			reload(helper, QaCreateDeployerUses1122eCompatTests.HALL);
			reloaded[0] = true;
		});
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 500, () -> {
			String state = QaCreateHosePulley1122eCompatTests.state(helper, PULLEY_X);
			long drainedOut = out.stream().filter(p -> !source(helper, p)).count();
			long tank = QaCreateHosePulley1122eCompatTests.tank(helper, PULLEY_X);
			helper.assertTrue(reloaded[0], "set-up: the pulley was never saved and loaded");
			helper.assertTrue(lostIn.get().isEmpty(), "a stranger's Hose Pulley outside Frank's protected village, saved and loaded at tick 150, drained a water"
				+ " source inside it: " + lostIn.get() + " (" + state + ")");
			helper.assertTrue(drainedOut > 0 || tank > 0, "the pulley drained nothing at all, not even outside, so the test proves nothing (" + outAtReload[0]
				+ " outside drained at the reload; " + state + ")");
		});
	}

	// ---------------------------------------------------------------- 5. Schematicannon fed from a chest (B1516)

	/** A stranger's cannon outside with a chest of stone and gunpowder (no Creative Crate) prints the row's outside part only. */
	//$ gametest_ticks_batch AREA '600' '"qa_e1123_cannon_chest_protected"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_e1123_cannon_chest_protected")
	public void aStrangersChestFedSchematicannonPrintsOnlyOutsideAProtectedVillage(GameTestHelper helper) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, true);
		String owner = QaCreateCannonPipes1122eCompatTests.STRANGER_NAME;
		QaCreateCannonPipes1122eCompatTests.clearFolder(owner);
		QaCreateCannonPipes1122eCompatTests.writeRow(owner);
		for (int x = 8; x <= 13; x++) {
			helper.setBlock(new BlockPos(x, 2, Z), Blocks.AIR);
		}
		BlockPos at = new BlockPos(16, 2, Z);
		helper.setBlock(at, create("schematicannon"));
		helper.setBlock(at.east(), Blocks.CHEST);
		Container chest = helper.getBlockEntity(at.east());
		chest.setItem(0, new ItemStack(Items.STONE, 64));
		chest.setItem(1, new ItemStack(Items.GUNPOWDER, 16));
		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(at)) instanceof SchematicannonBlockEntity be)) {
			throw new GameTestAssertException("set-up: no Schematicannon block entity");
		}
		be.findInventories();
		be.replaceMode = ConfigureSchematicannonPacket.Option.DONT_REPLACE.ordinal();
		be.skipMissing = true;
		be.inventory.setStackInSlot(0, QaCreateCannonPipes1122eCompatTests.deployed(owner, helper.absolutePos(new BlockPos(8, 2, Z))));
		be.inventory.setStackInSlot(4, new ItemStack(Items.GUNPOWDER, 16));
		be.state = SchematicannonBlockEntity.State.RUNNING;
		be.setChanged();
		// a cannon loading its schematic stops at "ready" (paused) until its Play button is pressed again, as a player does
		helper.onEachTick(() -> {
			if (helper.getTick() < 100 && be.state == SchematicannonBlockEntity.State.PAUSED && "ready".equals(be.statusMsg)) {
				be.state = SchematicannonBlockEntity.State.RUNNING;
			}
		});
		ruleSays(helper, true, at, new BlockPos(9, 2, Z));
		List<BlockState> before = new ArrayList<>();
		for (int x = 8; x <= 11; x++) {
			before.add(helper.getBlockState(new BlockPos(x, 2, Z)));
		}
		AtomicReference<String> changed = watch(helper, () -> {
			for (int x = 8; x <= 11; x++) {
				BlockState now = helper.getBlockState(new BlockPos(x, 2, Z));
				if (now != before.get(x - 8)) {
					return "(" + x + ", 2, " + Z + ") became " + now.getBlock().getName().getString();
				}
			}
			return "";
		});
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 500, () -> {
			QaCreateCannonPipes1122eCompatTests.clearFolder(owner);
			String where = " (row x 8..13: " + QaCreateCannonPipes1122eCompatTests.row(helper, 8, 13) + "; " + QaCreateCannonPipes1122eCompatTests.cannonState(be)
				+ ", chest stone " + count(chest, Items.STONE) + ")";
			helper.assertTrue(changed.get().isEmpty(), "a stranger's Schematicannon fed from a chest, outside Frank's protected village, changed a block inside it: "
				+ changed.get() + where);
			helper.assertTrue(helper.getBlockState(new BlockPos(12, 2, Z)).is(Blocks.STONE) && helper.getBlockState(new BlockPos(13, 2, Z)).is(Blocks.STONE),
				"the chest-fed Schematicannon didn't print the row's outside cells, so the test proves nothing" + where);
		});
	}

	// ---------------------------------------------------------------- 6. Open pipe with lava (B1517)

	static final BlockPos CUP = QaCreateCannonPipes1122eCompatTests.CUP;

	/** The cannon-pipes cup and pipe with the stranger's tank kept topped up with lava; the pump pushes it toward the cup. */
	static void lavaPipe(GameTestHelper helper, boolean protect) {
		QaCreateDeployerUses1122eCompatTests.hall(helper, protect);
		QaCreateCannonPipes1122eCompatTests.pipe(helper, CUP, true);
		BlockPos tank = CUP.east(3);
		// the cannon-pipes set-up tops its tank up with water each tick; the tank here is replaced with a lava one first
		helper.setBlock(tank, create("fluid_tank"));
		Storage<FluidVariant> storage = FluidStorage.SIDED.find(helper.getLevel(), helper.absolutePos(tank), Direction.UP);
		helper.assertTrue(storage != null, "set-up: the tank has no fluid storage");
		try (Transaction tx = Transaction.openOuter()) {
			long put = storage.insert(FluidVariant.of(Fluids.LAVA), 8 * BUCKET, tx);
			tx.commit();
			helper.assertTrue(put == 8 * BUCKET, "set-up: the tank took only " + put + " droplets of lava");
		}
		helper.assertTrue(inside(CUP) && !inside(CUP.east()), "set-up: the cup isn't at the edge");
		ruleSays(helper, protect, CUP.east(), CUP);
		AtomicReference<String> seen = watch(helper, () -> helper.getLevel().getFluidState(helper.absolutePos(CUP)).is(FluidTags.LAVA) ? "lava in the cup" : "");
		AtomicReference<String> water = watch(helper, () -> helper.getLevel().getFluidState(helper.absolutePos(CUP)).is(FluidTags.WATER) ? "water in the cup" : "");
		QaCreateDeployerUses1122eCompatTests.thenCheck(helper, 400, () -> {
			String state = QaCreateCannonPipes1122eCompatTests.pipeState(helper, CUP);
			helper.assertTrue(speed(helper, CUP.east(2)) != 0, "the pump never turned, so the test proves nothing (" + state + ")");
			if (protect) {
				helper.assertTrue(seen.get().isEmpty() && water.get().isEmpty(), "a stranger's open-ended Fluid Pipe outside Frank's protected village poured into the"
					+ " cup inside it: " + seen.get() + water.get() + " (" + state + ")");
			} else {
				helper.assertTrue(!seen.get().isEmpty() || !water.get().isEmpty(), "the control (village open): the open pipe poured nothing into the cup (" + state + ")");
			}
		});
	}

	/** A stranger's open pipe outside a protected village pours no lava into the cup at its edge. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1123_pipe_lava_protected"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1123_pipe_lava_protected")
	public void aStrangersOpenPipePoursNoLavaIntoAProtectedVillage(GameTestHelper helper) {
		lavaPipe(helper, true);
	}

	/** The control: with the village open the same pipe pours lava into the cup. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1123_pipe_lava_open"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1123_pipe_lava_open")
	public void aStrangersOpenPipePoursLavaIntoAnOpenVillage(GameTestHelper helper) {
		lavaPipe(helper, false);
	}
}
