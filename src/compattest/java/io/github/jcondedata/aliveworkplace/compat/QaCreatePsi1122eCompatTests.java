package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.HALL;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.done;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.hall;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

/**
 * QA (lane E, 2026-11-22): the village protection against Create's Portable Storage Interface (PSI), the case the
 * B994 and B1015 fixes and create.md leave "not covered yet". B994's expectation: "a funnel (or chute, Mechanical Arm,
 * Portable Storage Interface) outside a protected village takes nothing from a container inside it"; village-hall.md:
 * with protection on only the owner, friends and operators may take from containers in the hall's area; create.md: "the
 * same machines inside the village, or with its protection off, work as usual".
 *
 * <p>The set-up, a common Create loading dock: Frank's chest of 16 diamonds inside his village, a hopper under it feeding
 * a stationary PSI (facing the track) beside his rails. A cart contraption carrying a PSI and a chest (stuck to a slime
 * block) rolls along the track; when its PSI passes the stationary one they dock and the hopper's diamonds flow into the
 * cart's chest. This class holds the controls that must pass however the PSI question is settled: with the village open
 * the stranger's cart loads (also after a save and reload on its way), and Frank's own cart, put together inside his
 * protected village, loads there. The stranger's cases against a protected village are in
 * {@code QaCreatePsi1122eBugTests} (unregistered while they fail).
 *
 * <p>The village is {@link QaCreateIntoProtectedVillage1107eCompatTests}'s (hall at (3, 2, 15), 8 columns). The track
 * runs along x at z = 12 (the village covers x = 10 and less there); the stranger's assembler is at x = 22 (outside),
 * Frank's at x = 9 (inside). The dock is at x = 6: stationary PSI (6, 3, 9) facing south, hopper (6, 3, 8), chest (6, 4, 8).
 */
public class QaCreatePsi1122eCompatTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:huge_area";
	static final int TZ = 12;
	static final int STRANGER_AX = 22;
	static final int OWNER_AX = 9;
	static final BlockPos DOCK = new BlockPos(6, 3, 9);
	static final BlockPos HOPPER = new BlockPos(6, 3, 8);
	static final BlockPos CHEST = new BlockPos(6, 4, 8);
	static final int DIAMONDS = 16;

	/** Turns the hall's protection off just before {@code timeout}, so a failing test leaves no protected village behind. */
	static void watchdog(GameTestHelper helper, int timeout) {
		helper.onEachTick(() -> {
			if (helper.getTick() >= timeout - 5 && helper.getLevel().getBlockEntity(helper.absolutePos(HALL)) instanceof VillageHallBlockEntity h) {
				h.setProtected(false);
			}
		});
	}

	/**
	 * Powered rails (on redstone blocks) along z = {@link #TZ} from x = 1 to 27 with a powered Cart Assembler at
	 * x = {@code ax}; on it a slime block holding a chest on top and a PSI facing north (toward the dock's side).
	 */
	static void line(GameTestHelper helper, int ax) {
		BlockState rail = Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST);
		for (int x = 1; x <= 27; x++) {
			helper.setBlock(new BlockPos(x, 1, TZ), Blocks.REDSTONE_BLOCK);
			for (int z = TZ - 1; z <= TZ + 1; z++) {
				for (int y = 3; y <= 5; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
			if (x != ax) {
				helper.setBlock(new BlockPos(x, 2, TZ), rail);
			}
		}
		BlockPos assembler = new BlockPos(ax, 2, TZ);
		helper.setBlock(assembler, MechanicCompatTests.with(MechanicCompatTests.with(MechanicCompatTests.with(
			MechanicCompatTests.create("cart_assembler").defaultBlockState(), "shape", "east_west"), "rail_type", "powered_rail"), "powered", "true"));
		helper.setBlock(assembler.above(), Blocks.SLIME_BLOCK);
		helper.setBlock(assembler.above().above(), Blocks.CHEST);
		helper.setBlock(assembler.above().north(), MechanicCompatTests.with(MechanicCompatTests.create("portable_storage_interface").defaultBlockState(), "facing", "north"));
	}

	/** Frank's dock: a chest of {@link #DIAMONDS} diamonds on a hopper feeding the stationary PSI that faces the track. */
	static void dock(GameTestHelper helper) {
		helper.setBlock(DOCK.north(), Blocks.AIR);
		helper.setBlock(DOCK.south(), Blocks.AIR);
		helper.setBlock(DOCK, MechanicCompatTests.with(MechanicCompatTests.create("portable_storage_interface").defaultBlockState(), "facing", "south"));
		helper.setBlock(HOPPER, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.SOUTH));
		helper.setBlock(CHEST, Blocks.CHEST);
		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(CHEST)) instanceof Container chest)) {
			throw new GameTestAssertException("set-up: no chest at the dock");
		}
		chest.setItem(0, new ItemStack(Items.DIAMOND, DIAMONDS));
	}

	static void rollCart(GameTestHelper helper, int ax) {
		ServerLevel level = helper.getLevel();
		AbstractMinecart cart = EntityType.MINECART.create(level);
		BlockPos at = helper.absolutePos(new BlockPos(ax, 2, TZ));
		cart.moveTo(at.getX() + 1.5, at.getY() + 0.1, at.getZ() + 0.5, 90f, 0f);
		cart.setDeltaMovement(-0.3, 0, 0);
		level.addFreshEntity(cart);
	}

	static List<AbstractContraptionEntity> contraptions(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(AbstractContraptionEntity.class, helper.getBounds().inflate(8));
	}

	static long diamondsOn(AbstractContraptionEntity e) {
		long n = 0;
		for (StorageView<ItemVariant> view : e.getContraption().getStorage().getAllItems()) {
			if (!view.isResourceBlank() && view.getResource().isOf(Items.DIAMOND)) {
				n += view.getAmount();
			}
		}
		return n;
	}

	/**
	 * Where the cart's contraption has been: {[0]} put together (1/0), {[1]} the least x it reached (test frame), {[2]}
	 * the most diamonds its chest held.
	 */
	static long[] follow(GameTestHelper helper) {
		long[] seen = {0, Integer.MAX_VALUE, 0};
		helper.onEachTick(() -> {
			for (AbstractContraptionEntity e : contraptions(helper)) {
				seen[0] = 1;
				seen[1] = Math.min(seen[1], e.blockPosition().getX() - helper.absolutePos(BlockPos.ZERO).getX());
				seen[2] = Math.max(seen[2], diamondsOn(e));
			}
		});
		return seen;
	}

	static void clearCarts(GameTestHelper helper) {
		for (AbstractContraptionEntity e : contraptions(helper)) {
			e.discard();
		}
		for (AbstractMinecart m : helper.getLevel().getEntitiesOfClass(AbstractMinecart.class, helper.getBounds().inflate(8))) {
			m.discard();
		}
	}

	/** Saves the cart and its contraption and loads them again in their place (as a chunk unloaded and loaded does). */
	static void reloadCart(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<AbstractMinecart> carts = level.getEntitiesOfClass(AbstractMinecart.class, helper.getBounds().inflate(8), m -> !m.getPassengers().isEmpty());
		if (carts.size() != 1) {
			throw new GameTestAssertException("reload: " + carts.size() + " carts with a contraption, not 1");
		}
		AbstractMinecart cart = carts.get(0);
		CompoundTag tag = new CompoundTag();
		if (!cart.save(tag)) {
			throw new GameTestAssertException("reload: the cart didn't save");
		}
		for (Entity e : cart.getIndirectPassengers()) {
			e.discard();
		}
		cart.discard();
		Entity again = EntityType.loadEntityRecursive(tag, level, e -> e);
		if (again == null || !level.tryAddFreshEntityWithPassengers(again)) {
			throw new GameTestAssertException("reload: the cart didn't load");
		}
	}

	/** The diamonds still on Frank's side: his chest, his hopper and any lying on the ground in the village's columns. */
	static int villageDiamonds(GameTestHelper helper) {
		int n = 0;
		for (BlockPos p : new BlockPos[] {CHEST, HOPPER}) {
			if (helper.getLevel().getBlockEntity(helper.absolutePos(p)) instanceof Container c) {
				n += c.countItem(Items.DIAMOND);
			}
		}
		for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4), e -> e.getItem().is(Items.DIAMOND))) {
			n += item.getItem().getCount();
		}
		return n;
	}

	enum Cart { STRANGER, OWNER }

	/**
	 * Rolls a cart (the stranger's from outside, or Frank's put together inside) past Frank's dock, with the village
	 * protected or open, optionally saving and loading the cart on its way while it is still outside, and checks after
	 * 400 ticks whether the dock loaded it. {@code expectLoaded}: the cart's chest must hold diamonds from the dock;
	 * otherwise all {@link #DIAMONDS} must still be on Frank's side and none on the cart.
	 */
	static void run(GameTestHelper helper, Cart who, boolean protect, boolean reload, boolean expectLoaded) {
		VillageHallBlockEntity hall = hall(helper, protect);
		watchdog(helper, 500);
		int ax = who == Cart.OWNER ? OWNER_AX : STRANGER_AX;
		line(helper, ax);
		dock(helper);
		if (protect) {
			BlockPos machine = helper.absolutePos(new BlockPos(ax, 2, TZ));
			boolean may = VillageProtection.mayMachineChange(helper.getLevel(), machine, helper.absolutePos(CHEST));
			if (may != (who == Cart.OWNER)) {
				done(helper, hall);
				helper.fail("set-up: the village's protection doesn't judge the assembler at x = " + ax + " as expected (may change the chest: " + may + ")");
			}
		}
		long[] seen = follow(helper);
		boolean[] reloaded = {!reload};
		if (reload) {
			helper.onEachTick(() -> {
				if (!reloaded[0] && seen[1] <= 16) {
					reloaded[0] = true;
					reloadCart(helper);
				}
			});
		}
		helper.runAfterDelay(3, () -> rollCart(helper, ax));
		helper.runAfterDelay(400, () -> {
			long onCart = 0;
			for (AbstractContraptionEntity e : contraptions(helper)) {
				onCart += diamondsOn(e);
			}
			long most = Math.max(onCart, seen[2]);
			int left = villageDiamonds(helper);
			clearCarts(helper);
			done(helper, hall);
			String who1 = who == Cart.OWNER ? "Frank's own cart (put together inside his village)" : "a stranger's cart (put together outside)";
			String state = protect ? "protected" : "open";
			helper.assertTrue(seen[0] == 1, "set-up: the Cart Assembler never put the PSI and chest on the cart");
			helper.assertTrue(reloaded[0], "set-up: the cart was never saved and loaded again (least x " + seen[1] + ")");
			helper.assertTrue(seen[1] <= 7, "set-up: " + who1 + " never reached Frank's dock at x = 6 (least x " + seen[1] + ")");
			if (expectLoaded) {
				helper.assertTrue(most > 0, who1 + " docking at Frank's PSI in his " + state + " village took no diamonds (its chest held "
					+ most + ", " + left + " of " + DIAMONDS + " still on Frank's side, least x " + seen[1] + ")");
			} else {
				helper.assertTrue(most == 0 && left == DIAMONDS, who1 + (reload ? ", saved and loaded on its way," : "") + " docking at Frank's PSI in his "
					+ state + " village took his diamonds: its chest held " + most + ", " + left + " of " + DIAMONDS
					+ " still on Frank's side (least x " + seen[1] + ")");
			}
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- a piston's PSI that stays outside the village

	/** On the hall's row (z = 15) the village ends at x = 11: Frank's PSI there faces east, out of the village. */
	static final BlockPos EDGE_DOCK = new BlockPos(11, 2, 15);
	static final BlockPos EDGE_HOPPER = new BlockPos(10, 2, 15);
	static final BlockPos EDGE_CHEST = new BlockPos(10, 3, 15);
	/** The stranger's Mechanical Piston, facing west with three poles behind it: it pushes a chest and a PSI (x = 17, 16). */
	static final BlockPos PISTON = new BlockPos(18, 2, 15);

	static int count(GameTestHelper helper, BlockPos... at) {
		int n = 0;
		for (BlockPos p : at) {
			if (helper.getLevel().getBlockEntity(helper.absolutePos(p)) instanceof Container c) {
				n += c.countItem(Items.DIAMOND);
			}
		}
		return n;
	}

	/**
	 * A stranger's Mechanical Piston standing outside the village (x = 18) pushes a chest and a PSI west; fully out
	 * (three poles) its PSI stops at x = 13, one gap from Frank's PSI at the village's last column, so it never enters the
	 * village. Frank's chest of diamonds on a hopper feeds his PSI. After 400 ticks: did his diamonds leave his side?
	 */
	static void pistonRun(GameTestHelper helper, boolean protect, boolean expectLoaded) {
		VillageHallBlockEntity hall = hall(helper, protect);
		watchdog(helper, 500);
		for (int x = 4; x <= 22; x++) {
			for (int y = 2; y <= 4; y++) {
				helper.setBlock(new BlockPos(x, y, 15), Blocks.AIR);
			}
		}
		helper.setBlock(EDGE_DOCK, MechanicCompatTests.with(MechanicCompatTests.create("portable_storage_interface").defaultBlockState(), "facing", "east"));
		helper.setBlock(EDGE_HOPPER, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
		helper.setBlock(EDGE_CHEST, Blocks.CHEST);
		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(EDGE_CHEST)) instanceof Container chest)) {
			done(helper, hall);
			throw new GameTestAssertException("set-up: no chest at the edge dock");
		}
		chest.setItem(0, new ItemStack(Items.DIAMOND, DIAMONDS));
		helper.setBlock(PISTON.west(2), MechanicCompatTests.with(MechanicCompatTests.create("portable_storage_interface").defaultBlockState(), "facing", "west"));
		helper.setBlock(PISTON.west(), Blocks.CHEST);
		for (int i = 1; i <= 3; i++) {
			helper.setBlock(PISTON.east(i), MechanicCompatTests.with(MechanicCompatTests.create("piston_extension_pole").defaultBlockState(), "facing", "west"));
		}
		helper.setBlock(PISTON, MechanicCompatTests.with(MechanicCompatTests.with(MechanicCompatTests.create("mechanical_piston").defaultBlockState(), "facing", "west"),
			"axis_along_first", "true"));
		helper.setBlock(PISTON.below(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "up"));
		if (protect && VillageProtection.mayMachineChange(helper.getLevel(), helper.absolutePos(PISTON), helper.absolutePos(EDGE_CHEST))) {
			done(helper, hall);
			helper.fail("set-up: the village isn't protected against a machine at the piston");
		}
		long[] seen = {0, Integer.MAX_VALUE, 0};
		helper.onEachTick(() -> {
			for (AbstractContraptionEntity e : contraptions(helper)) {
				seen[0] = 1;
				seen[2] = Math.max(seen[2], diamondsOn(e));
			}
			for (int x = 12; x <= 17; x++) {
				if (helper.getBlockState(new BlockPos(x, 2, 15)).getBlock() == MechanicCompatTests.create("portable_storage_interface")) {
					seen[1] = Math.min(seen[1], x);
				}
			}
		});
		helper.runAfterDelay(400, () -> {
			long onContraption = 0;
			for (AbstractContraptionEntity e : contraptions(helper)) {
				onContraption += diamondsOn(e);
			}
			int outside = (int) onContraption;
			for (int x = 12; x <= 17; x++) {
				outside += count(helper, new BlockPos(x, 2, 15));
			}
			int left = count(helper, EDGE_CHEST, EDGE_HOPPER);
			done(helper, hall);
			helper.assertTrue(seen[0] == 1, "set-up: the piston never moved its chest and PSI");
			if (expectLoaded) {
				helper.assertTrue(outside > 0 || seen[2] > 0, "the control: a stranger's piston PSI docking at Frank's edge PSI in his open village took no diamonds ("
					+ left + " of " + DIAMONDS + " still on Frank's side; PSI least x " + seen[1] + ")");
			} else {
				helper.assertTrue(left == DIAMONDS && outside == 0 && seen[2] == 0, "a stranger's piston outside Frank's protected village docked its PSI at his edge PSI and took his diamonds: "
					+ (DIAMONDS - left) + " gone from his chest and hopper, " + outside + " outside now (most on the contraption " + seen[2] + ")");
			}
			helper.succeed();
		});
	}

	/** The control: with the village open a stranger's piston outside docks its PSI at Frank's edge PSI and is loaded. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122x_psi_piston_open"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122x_psi_piston_open")
	public void aStrangersPistonPsiOutsideAnOpenVillageIsLoadedAtItsEdge(GameTestHelper helper) {
		pistonRun(helper, false, true);
	}

	// ---------------------------------------------------------------- carts

	/** The control: with the village open a stranger's cart docking at Frank's PSI is loaded from his chest. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122x_psi_open"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122x_psi_open")
	public void aStrangersCartDockingAtAPsiInAnOpenVillageIsLoaded(GameTestHelper helper) {
		run(helper, Cart.STRANGER, false, false, true);
	}

	/** The control for the reload case: saved and loaded on its way, the cart still docks and loads in an open village. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122x_psi_open_reloaded"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122x_psi_open_reloaded")
	public void aCartSavedAndLoadedOnTheWayStillDocksAtAPsiInAnOpenVillage(GameTestHelper helper) {
		run(helper, Cart.STRANGER, false, true, true);
	}

	/** Over-protection guard: Frank's own cart, put together inside his protected village, loads at his own dock. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122x_psi_owner"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122x_psi_owner")
	public void theOwnersCartDocksAtHisPsiInHisProtectedVillage(GameTestHelper helper) {
		run(helper, Cart.OWNER, true, false, true);
	}
}
