package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.EDGE;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.HALL;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.done;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.hall;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.speed;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

/**
 * QA (lane E, 2026-11-22): B993, B994 and B1015 (fixed 2026-11-18) for machines with no fixed controller. village-hall.md:
 * with protection on "only the owner, their friends and operators may break or place blocks, open chests" in the hall's
 * area; B745: a machine outside stops at the village's edge. A Create contraption on a minecart (put together by a Cart
 * Assembler outside the village and rolled along a track into it) has no controller block in the village at all.
 *
 * <p>Written from those promises: a stranger's drill cart and deployer cart rolling into a protected village (each with
 * its open-village control), a stranger's funnel outside dropping items into a chest at the edge, a stranger's funnel
 * outside that takes nothing while the village is protected and takes once protection is off, and a hall saved and
 * loaded again with a stranger's drill outside its edge.
 *
 * <p>The village is {@link QaCreateIntoProtectedVillage1107eCompatTests}'s (hall at (3, 2, 15), 8 columns across). The
 * track runs along x at z = 12 (the village covers x = 10 and less there), from x = 1 to x = 27, the assembler at x = 22.
 */
public class QaCreateMovingMachines1122eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final UUID STRANGER = UUID.fromString("7c0ffee0-1122-4e00-9a00-00000000ca27");
	private static final int TZ = 12;
	private static final int AX = 22;
	private static final BlockPos ASSEMBLER = new BlockPos(AX, 2, TZ);

	/** Turns the hall's protection off just before {@code timeout}, so a failing test leaves no protected village behind. */
	private static void watchdog(GameTestHelper helper, int timeout) {
		helper.onEachTick(() -> {
			if (helper.getTick() >= timeout - 5 && helper.getLevel().getBlockEntity(helper.absolutePos(HALL)) instanceof VillageHallBlockEntity h) {
				h.setProtected(false);
			}
		});
	}

	private static boolean inVillage(BlockPos pos) {
		long dx = pos.getX() - HALL.getX();
		long dz = pos.getZ() - HALL.getZ();
		return dx * dx + dz * dz <= (long) QaCreateIntoProtectedVillage1107eCompatTests.REACH * QaCreateIntoProtectedVillage1107eCompatTests.REACH;
	}

	/**
	 * Powered rails (on redstone blocks) along z = {@link #TZ} from x = 1 to 27 with a powered Cart Assembler at
	 * {@link #ASSEMBLER}; {@code top} goes on the assembler (what the cart carries); a minecart rolls west onto it.
	 */
	private static void line(GameTestHelper helper, BlockState top) {
		BlockState rail = Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST);
		for (int x = 1; x <= 27; x++) {
			helper.setBlock(new BlockPos(x, 1, TZ), Blocks.REDSTONE_BLOCK);
			for (int z = TZ - 1; z <= TZ + 1; z++) {
				for (int y = 3; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
			if (x != AX) {
				helper.setBlock(new BlockPos(x, 2, TZ), rail);
			}
		}
		BlockState assembler = MechanicCompatTests.with(MechanicCompatTests.with(MechanicCompatTests.with(
			MechanicCompatTests.create("cart_assembler").defaultBlockState(), "shape", "east_west"), "rail_type", "powered_rail"), "powered", "true");
		helper.setBlock(ASSEMBLER, assembler);
		helper.setBlock(ASSEMBLER.above(), top);
	}

	private static void rollCart(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		AbstractMinecart cart = EntityType.MINECART.create(level);
		BlockPos at = helper.absolutePos(ASSEMBLER);
		cart.moveTo(at.getX() + 1.5, at.getY() + 0.1, at.getZ() + 0.5, 90f, 0f);
		cart.setDeltaMovement(-0.3, 0, 0);
		level.addFreshEntity(cart);
	}

	private static List<AbstractContraptionEntity> contraptions(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(AbstractContraptionEntity.class, helper.getBounds().inflate(8));
	}

	/** Where the cart's contraption has been: {[0]} put together (1/0), {[1]} the least x it reached (test frame). */
	private static int[] follow(GameTestHelper helper) {
		int[] seen = {0, Integer.MAX_VALUE};
		helper.onEachTick(() -> {
			for (AbstractContraptionEntity e : contraptions(helper)) {
				seen[0] = 1;
				seen[1] = Math.min(seen[1], e.blockPosition().getX() - helper.absolutePos(BlockPos.ZERO).getX());
			}
		});
		return seen;
	}

	private static void clearCarts(GameTestHelper helper) {
		for (AbstractContraptionEntity e : contraptions(helper)) {
			e.discard();
		}
		for (AbstractMinecart m : helper.getLevel().getEntitiesOfClass(AbstractMinecart.class, helper.getBounds().inflate(8))) {
			m.discard();
		}
	}

	private static void assertGuarded(GameTestHelper helper, VillageHallBlockEntity hall, BlockPos machine, BlockPos inside) {
		if (VillageProtection.mayMachineChange(helper.getLevel(), helper.absolutePos(machine), helper.absolutePos(inside))) {
			done(helper, hall);
			helper.fail("set-up: the village isn't protected (a machine at " + machine.toShortString() + " may change " + inside.toShortString() + ")");
		}
	}

	// ---------------------------------------------------------------- 1. a drill cart

	/** An oak plank on the track, four columns inside the village (x = 6), in the drill's way. */
	private static final BlockPos ON_TRACK = new BlockPos(6, 3, TZ);

	private static void drillCart(GameTestHelper helper, boolean protect) {
		drillCart(helper, protect, false);
	}

	/**
	 * Saves the cart and its contraption and loads them again in their place (as a chunk unloaded and loaded, or a
	 * restart, does), keeping their motion.
	 */
	private static void reloadCart(GameTestHelper helper) {
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
		for (net.minecraft.world.entity.Entity e : cart.getIndirectPassengers()) {
			e.discard();
		}
		cart.discard();
		net.minecraft.world.entity.Entity again = EntityType.loadEntityRecursive(tag, level, e -> e);
		if (again == null || !level.tryAddFreshEntityWithPassengers(again)) {
			throw new GameTestAssertException("reload: the cart didn't load from " + tag);
		}
	}

	private static void drillCart(GameTestHelper helper, boolean protect, boolean reload) {
		VillageHallBlockEntity hall = hall(helper, protect);
		watchdog(helper, 500);
		line(helper, MechanicCompatTests.with(MechanicCompatTests.create("mechanical_drill").defaultBlockState(), "facing", "west"));
		helper.setBlock(ON_TRACK, Blocks.OAK_PLANKS);
		if (protect) {
			assertGuarded(helper, hall, ASSEMBLER, ON_TRACK);
		}
		int[] seen = follow(helper);
		boolean[] reloaded = {!reload};
		if (reload) {
			helper.onEachTick(() -> {
				if (!reloaded[0] && seen[1] <= 16) {
					reloaded[0] = true;
					reloadCart(helper);
				}
			});
		}
		helper.runAfterDelay(3, () -> rollCart(helper));
		helper.runAfterDelay(400, () -> {
			BlockState there = helper.getBlockState(ON_TRACK);
			clearCarts(helper);
			done(helper, hall);
			helper.assertTrue(seen[0] == 1, "set-up: the Cart Assembler never put the drill on the cart");
			helper.assertTrue(reloaded[0], "set-up: the cart was never saved and loaded again (least x " + seen[1] + ")");
			if (protect) {
				helper.assertTrue(there.is(Blocks.OAK_PLANKS), "a stranger's drill cart, put together outside Frank's protected village, rolled in (as far as x = "
					+ seen[1] + ") and broke the plank on the track at x = 6: " + there);
			} else {
				helper.assertTrue(seen[1] <= 7, "the control cart never reached the plank: least x " + seen[1]);
				helper.assertTrue(there.isAir(), "the control: a drill cart in an open village didn't break the plank on the track (least x "
					+ seen[1] + "): " + there);
			}
			helper.succeed();
		});
	}

	/** A stranger's drill cart rolling from outside into the protected village breaks nothing in its way there. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_cart_drill_protected"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_cart_drill_protected")
	public void aStrangersDrillCartRollingIntoAProtectedVillageBreaksNothing(GameTestHelper helper) {
		drillCart(helper, true);
	}

	/** The control: the same cart in an open village bores through the plank. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_cart_drill_open"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_cart_drill_open")
	public void aDrillCartRollingIntoAnOpenVillageBreaksItsWay(GameTestHelper helper) {
		drillCart(helper, false);
	}

	/**
	 * The stranger's drill cart is saved and loaded again (a restart) while it rolls outside, before the village: rolling
	 * on into the protected village it still breaks nothing there.
	 */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_cart_drill_reloaded"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_cart_drill_reloaded")
	public void aStrangersDrillCartSavedAndLoadedOnTheWayStillBreaksNothingInside(GameTestHelper helper) {
		drillCart(helper, true, true);
	}

	/** The control: the same cart saved and loaded on the way bores through the plank of an open village. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_cart_drill_reloaded_open"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_cart_drill_reloaded_open")
	public void aDrillCartSavedAndLoadedOnTheWayStillBoresThroughAnOpenVillage(GameTestHelper helper) {
		drillCart(helper, false, true);
	}

	// ---------------------------------------------------------------- 2. a deployer cart

	private static void deployerCart(GameTestHelper helper, boolean protect) {
		deployerCart(helper, protect, false);
	}

	private static void deployerCart(GameTestHelper helper, boolean protect, boolean owners) {
		VillageHallBlockEntity hall = hall(helper, protect);
		watchdog(helper, 500);
		ServerLevel level = helper.getLevel();
		line(helper, MechanicCompatTests.with(MechanicCompatTests.create("deployer").defaultBlockState(), "facing", "north"));
		for (int x = 1; x <= 27; x++) {
			helper.setBlock(new BlockPos(x, 2, TZ - 1), Blocks.STONE);
			helper.setBlock(new BlockPos(x, 2, TZ - 2), Blocks.STONE);
			helper.setBlock(new BlockPos(x, 2, TZ + 1), Blocks.STONE);
		}
		BlockEntity be = level.getBlockEntity(helper.absolutePos(ASSEMBLER.above()));
		if (be == null) {
			done(helper, hall);
			throw new GameTestAssertException("set-up: no deployer block entity");
		}
		CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
		tag.putUUID("Owner", owners ? hall.owner() : STRANGER);
		be.loadWithComponents(tag, level.registryAccess());
		long in = QaCreateDeployerOnOurThings1102eCompatTests.insert(level, helper.absolutePos(ASSEMBLER.above()), new ItemStack(Items.COBBLESTONE, 32));
		if (in == 0) {
			done(helper, hall);
			helper.fail("set-up: the deployer took no cobblestone");
		}
		if (protect) {
			assertGuarded(helper, hall, ASSEMBLER, new BlockPos(5, 3, TZ - 1));
		}
		int[] seen = follow(helper);
		helper.runAfterDelay(4, () -> rollCart(helper));
		helper.runAfterDelay(300, () -> {
			StringBuilder inside = new StringBuilder();
			int insideN = 0;
			int outsideN = 0;
			for (BlockPos p : BlockPos.betweenClosed(new BlockPos(0, 2, 0), new BlockPos(29, 6, 29))) {
				if (helper.getBlockState(p).is(Blocks.COBBLESTONE)) {
					inside.append(inVillage(p) ? "in:" : "out:").append(p.toShortString()).append("; ");
					if (inVillage(p)) {
						insideN++;
					} else {
						outsideN++;
					}
				}
			}
			clearCarts(helper);
			done(helper, hall);
			helper.assertTrue(seen[0] == 1, "set-up: the Cart Assembler never put the deployer on the cart");
			if (owners) {
				helper.assertTrue(insideN > 0, "the village owner's own deployer cart rolling through his protected village (least x " + seen[1]
					+ ") placed nothing in it (" + outsideN + " placed outside): " + inside);
			} else if (protect) {
				helper.assertTrue(insideN == 0, "a stranger's deployer cart rolling into Frank's protected village (as far as x = " + seen[1]
					+ ") placed " + insideN + " cobblestone inside it (" + outsideN + " placed outside): " + inside);
			} else {
				helper.assertTrue(insideN > 0, "the control: a deployer cart rolling through an open village placed nothing in it (least x "
					+ seen[1] + ", " + outsideN + " placed outside): " + inside + " cobblestone items on the ground: "
					+ helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4), e -> e.getItem().is(Items.COBBLESTONE)).size());
			}
			helper.succeed();
		});
	}

	/** A stranger's deployer cart rolling into the protected village places nothing beside the track there. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_cart_deployer_protected"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_cart_deployer_protected")
	public void aStrangersDeployerCartRollingIntoAProtectedVillagePlacesNothing(GameTestHelper helper) {
		deployerCart(helper, true);
	}

	/** The control: the same cart rolling through an open village places cobblestone beside the track in it. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_cart_deployer_open"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_cart_deployer_open")
	public void aDeployerCartRollingThroughAnOpenVillagePlaces(GameTestHelper helper) {
		deployerCart(helper, false);
	}

	/**
	 * The owner's side: a deployer cart the village's owner placed (a Deployer acts for the player who placed it) rolls
	 * through his protected village and places beside the track there, as he may.
	 */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_cart_deployer_owner"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_cart_deployer_owner")
	public void theOwnersDeployerCartPlacesInHisProtectedVillage(GameTestHelper helper) {
		deployerCart(helper, true, true);
	}

	// ---------------------------------------------------------------- 3. items: a funnel outside putting in, and toggling

	private static int diamonds(Container c) {
		return QaCreateFunnelAtProtectedVillage1107eCompatTests.diamonds(c);
	}

	/** An andesite funnel just outside (x = 12) on the chest at the edge; 16 diamonds dropped onto it. */
	private static void funnelInto(GameTestHelper helper, boolean protect) {
		VillageHallBlockEntity hall = hall(helper, protect);
		watchdog(helper, 300);
		helper.setBlock(EDGE, Blocks.CHEST);
		Container chest = helper.getBlockEntity(EDGE);
		helper.setBlock(EDGE.east(), MechanicCompatTests.with(MechanicCompatTests.create("andesite_funnel").defaultBlockState(), "facing", "east"));
		if (protect) {
			assertGuarded(helper, hall, EDGE.east(), EDGE);
		}
		helper.runAfterDelay(5, () -> {
			BlockPos f = helper.absolutePos(EDGE.east());
			ItemEntity drop = new ItemEntity(helper.getLevel(), f.getX() + 0.7, f.getY() + 0.5, f.getZ() + 0.5, new ItemStack(Items.DIAMOND, 16));
			drop.setDeltaMovement(0, 0, 0);
			helper.getLevel().addFreshEntity(drop);
		});
		helper.runAfterDelay(200, () -> {
			int got = diamonds(chest);
			int lying = QaCreateFunnelAtProtectedVillage1107eCompatTests.diamondsOnTheGround(helper);
			done(helper, hall);
			if (protect) {
				helper.assertTrue(got == 0, "a stranger's funnel outside Frank's protected village put " + got + " of 16 diamonds into the chest in its last column"
					+ " (B994's fix: inserting from outside is refused too); " + lying + " lie on the ground");
				helper.assertTrue(lying == 16, "the diamonds the funnel couldn't put in should still lie outside: " + lying + " of 16 (chest " + got + ")");
			} else {
				helper.assertTrue(got == 16, "the control: the funnel put " + got + " of 16 diamonds into the chest of an open village (" + lying + " on the ground)");
			}
			helper.succeed();
		});
	}

	/** A stranger's funnel outside a protected village puts nothing into the chest at its edge; the items stay outside. */
	//$ gametest_ticks_batch AREA '300' '"qa_e1122_funnel_into_protected"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_e1122_funnel_into_protected")
	public void aStrangersFunnelOutsideAProtectedVillagePutsNothingIntoItsChest(GameTestHelper helper) {
		funnelInto(helper, true);
	}

	/** The control: in an open village the same funnel puts the diamonds into the chest. */
	//$ gametest_ticks_batch AREA '300' '"qa_e1122_funnel_into_open"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_e1122_funnel_into_open")
	public void aFunnelOutsideAnOpenVillagePutsItemsIntoItsChest(GameTestHelper helper) {
		funnelInto(helper, false);
	}

	/**
	 * A stranger's brass funnel outside pulling from the chest at the edge takes nothing for 150 ticks while the village is
	 * protected; the owner turns protection off and from then on it takes (judged now, not when the funnel was placed).
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_funnel_toggle"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_funnel_toggle")
	public void aStrangersFunnelTakesNothingWhileProtectedAndTakesOnceOpened(GameTestHelper helper) {
		VillageHallBlockEntity hall = hall(helper, true);
		watchdog(helper, 400);
		Container chest = QaCreateFunnelAtProtectedVillage1107eCompatTests.chestAndFunnel(helper);
		assertGuarded(helper, hall, EDGE.east(), EDGE);
		int[] whileProtected = {-1};
		helper.runAfterDelay(150, () -> {
			whileProtected[0] = diamonds(chest);
			hall.setProtected(false);
		});
		helper.runAfterDelay(300, () -> {
			int after = diamonds(chest);
			done(helper, hall);
			helper.assertTrue(whileProtected[0] == 16, "a stranger's funnel outside took " + (16 - whileProtected[0])
				+ " of 16 diamonds from the chest at the edge while Frank's village was protected");
			helper.assertTrue(after < 16, "with protection turned off the stranger's funnel still took nothing (16 of 16 left): the change didn't take effect");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- 4. the hall saved and loaded again

	/** Saves the block entity at {@code pos} and loads it again in its place, as a chunk unloaded and loaded does. */
	private static void reload(GameTestHelper helper, BlockPos pos) {
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
			throw new GameTestAssertException("reload: the block entity at " + pos.toShortString() + " didn't load from " + tag);
		}
		level.setBlockEntity(again);
	}

	/**
	 * Frank's protected hall is saved and loaded again (a restart) just after a stranger's drill outside starts turning:
	 * the loaded hall is still protected and the drill still can't break the stone in the village's last column.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_reloaded_hall_drill"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_reloaded_hall_drill")
	public void aReloadedProtectedHallStillKeepsAStrangersDrillOut(GameTestHelper helper) {
		VillageHallBlockEntity hall = hall(helper, true);
		watchdog(helper, 400);
		helper.setBlock(EDGE, Blocks.STONE);
		QaCreateIntoProtectedVillage1107eCompatTests.drill(helper, EDGE.east());
		assertGuarded(helper, hall, EDGE.east(), EDGE);
		helper.runAfterDelay(3, () -> reload(helper, HALL));
		helper.runAfterDelay(300, () -> {
			VillageHallBlockEntity loaded = helper.getBlockEntity(HALL);
			boolean stillProtected = loaded.isProtected();
			boolean guarded = !VillageProtection.mayMachineChange(helper.getLevel(), helper.absolutePos(EDGE.east()), helper.absolutePos(EDGE));
			float turning = speed(helper, EDGE.east());
			BlockState there = helper.getBlockState(EDGE);
			done(helper, loaded);
			helper.assertTrue(turning != 0, "the drill never turned");
			helper.assertTrue(stillProtected, "after a save and reload Frank's hall isn't protected any more");
			helper.assertTrue(there.is(Blocks.STONE), "after the hall was saved and loaded again a stranger's drill outside broke the stone in the village's last column: "
				+ there + " (machines outside refused: " + guarded + ")");
			helper.assertTrue(guarded, "after the reload a machine outside may change the village's edge (stone kept only by chance)");
			helper.succeed();
		});
	}
}
