package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.HALL;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.REACH;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.done;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.hall;
import static io.github.jcondedata.aliveworkplace.compat.QaCreatePortableTrains1122eCompatTests.STATION_A;
import static io.github.jcondedata.aliveworkplace.compat.QaCreatePortableTrains1122eCompatTests.STATION_B;
import static io.github.jcondedata.aliveworkplace.compat.QaCreatePortableTrains1122eCompatTests.TRAIN_HALL;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane E, lane-e-1122-2213): an independent check of B1498, B1502, B1503 and B1487 (fixed by lane A's HarvesterMixin,
 * PloughMixin, RollerFillMixin and ElevatorCabinMixin), written from the bugs' text and the promise they cite (B993,
 * village-hall.md): a machine outside a protected village changes nothing inside it, on any contraption; a machine inside
 * (and any machine beside an open village) works as before; nothing carries a village's blocks out.
 *
 * <p>The cases the bugs' own tests left out:
 * <ul>
 * <li>a harvester and a plough on a stranger's minecart contraption (a Cart Assembler outside, rolled along a track into
 * {@link QaCreateIntoProtectedVillage1107eCompatTests}'s village; not a train), each with its open-village control, and
 * the same harvester cart put together inside the protected village, which still reaps there (the fix must not stop a
 * machine that stands inside);</li>
 * <li>a harvester on a stranger's real train ({@link QaCreateTrainActors1122eCompatTests}'s set-up) meeting ripe sweet
 * berry bushes and sugar cane in the village (the harvester's other crops), and ripe wheat just outside the village's
 * border on the same lane, which it must still reap (the fix must not over-block); with its open control;</li>
 * <li>a Roller in wide-fill mode (Create 6.0.10's third mode) on a stranger's train, with its open control;</li>
 * <li>the stranger's harvester train saved and loaded (Create's own unload) just before the village, and a plough train
 * the same: the machine's record survives and they still change nothing inside;</li>
 * <li>B1487 turned 90 degrees (the elevator on the village's south edge, its cabin reaching north over it), and the
 * elevator's cabin saved and loaded between being put together and the pulley being broken, each with its open
 * control.</li>
 * </ul>
 */
public class QaCreateActorsVerify1122eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");

	private static BlockState create(String path) {
		return MechanicCompatTests.create(path).defaultBlockState();
	}

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
		return dx * dx + dz * dz <= (long) REACH * REACH;
	}

	private static List<AbstractContraptionEntity> contraptions(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(AbstractContraptionEntity.class, helper.getBounds().inflate(8));
	}

	private static void clearContraptions(GameTestHelper helper) {
		for (AbstractContraptionEntity e : contraptions(helper)) {
			e.discard();
		}
		for (AbstractMinecart m : helper.getLevel().getEntitiesOfClass(AbstractMinecart.class, helper.getBounds().inflate(8))) {
			m.discard();
		}
		for (SuperGlueEntity g : helper.getLevel().getEntitiesOfClass(SuperGlueEntity.class, helper.getBounds().inflate(4))) {
			g.discard();
		}
	}

	private static String name(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).getPath();
	}

	// ---------------------------------------------------------------- 1. a harvester or plough on a stranger's minecart

	/** The track's row; the actor rides beside the cart on the row north of it ({@link #LANE}). */
	private static final int TZ = 12;
	private static final int LANE = 11;

	/**
	 * Powered rails along z = {@link #TZ} from x = 1 to 27 with a powered Cart Assembler at {@code ax}, a plank on it and
	 * {@code actor} beside the plank on {@link #LANE}, glued to it (what the cart carries).
	 */
	private static void cartLine(GameTestHelper helper, int ax, BlockState actor) {
		BlockState rail = Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST);
		for (int x = 1; x <= 27; x++) {
			helper.setBlock(new BlockPos(x, 1, TZ), Blocks.REDSTONE_BLOCK);
			for (int z = TZ - 2; z <= TZ + 1; z++) {
				for (int y = 3; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
			if (x != ax) {
				helper.setBlock(new BlockPos(x, 2, TZ), rail);
			}
		}
		BlockPos assembler = new BlockPos(ax, 2, TZ);
		helper.setBlock(assembler, MechanicCompatTests.with(MechanicCompatTests.with(MechanicCompatTests.with(
			create("cart_assembler"), "shape", "east_west"), "rail_type", "powered_rail"), "powered", "true"));
		helper.setBlock(assembler.above(), Blocks.OAK_PLANKS);
		BlockPos at = new BlockPos(ax, 3, LANE);
		helper.setBlock(at, actor);
		BlockPos a = helper.absolutePos(at);
		BlockPos b = helper.absolutePos(assembler.above());
		helper.getLevel().addFreshEntity(new SuperGlueEntity(helper.getLevel(), AABB.encapsulatingFullBlocks(a, b)));
	}

	private static void rollCart(GameTestHelper helper, int ax) {
		ServerLevel level = helper.getLevel();
		AbstractMinecart cart = EntityType.MINECART.create(level);
		BlockPos at = helper.absolutePos(new BlockPos(ax, 2, TZ));
		cart.moveTo(at.getX() + 1.5, at.getY() + 0.1, at.getZ() + 0.5, 90f, 0f);
		cart.setDeltaMovement(-0.3, 0, 0);
		level.addFreshEntity(cart);
	}

	private static boolean ripeWheat(BlockState s) {
		return s.is(Blocks.WHEAT) && s.getValue(CropBlock.AGE) == CropBlock.MAX_AGE;
	}

	enum CartActor { HARVESTER, PLOUGH }

	/**
	 * A cart with a harvester (facing north, reaping the lane it passes through) or a plough (facing west, the way it
	 * rolls, tilling the ground under the lane) put together at {@code ax} and rolled west. Targets on the lane: ripe wheat
	 * on farmland (harvester) or bare grass and dirt (plough), at {@code outsideX} and at x = 4..9 where they are in the
	 * village.
	 */
	private static void cart(GameTestHelper helper, CartActor actor, boolean protect, int ax, int[] outsideX, boolean expectInsideWorked) {
		VillageHallBlockEntity hall = hall(helper, protect);
		watchdog(helper, 500);
		cartLine(helper, ax, actor == CartActor.HARVESTER ? MechanicCompatTests.with(create("mechanical_harvester"), "facing", "north")
			: MechanicCompatTests.with(create("mechanical_plough"), "facing", "west"));
		List<BlockPos> inside = new ArrayList<>();
		List<BlockPos> outside = new ArrayList<>();
		for (int x : outsideX) {
			outside.add(new BlockPos(x, 3, LANE));
		}
		for (int x = 4; x <= 9; x++) {
			BlockPos p = new BlockPos(x, 3, LANE);
			if (inVillage(p) && x < ax) {
				inside.add(p);
			}
		}
		List<BlockPos> all = new ArrayList<>(outside);
		all.addAll(inside);
		for (int i = 0; i < all.size(); i++) {
			BlockPos p = all.get(i);
			if (actor == CartActor.HARVESTER) {
				helper.setBlock(p.below(), Blocks.FARMLAND);
				helper.setBlock(p, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE));
			} else {
				helper.setBlock(p.below(), i % 2 == 0 ? Blocks.GRASS_BLOCK : Blocks.DIRT);
				helper.setBlock(p, Blocks.AIR);
			}
		}
		if (inside.isEmpty() || outside.stream().anyMatch(QaCreateActorsVerify1122eCompatTests::inVillage) != false) {
			done(helper, hall);
			throw new GameTestAssertException("set-up: the targets aren't where the test wants them");
		}
		BlockPos assembler = new BlockPos(ax, 2, TZ);
		boolean machineInside = inVillage(assembler);
		if (protect && !machineInside && VillageProtection.mayMachineChange(helper.getLevel(), helper.absolutePos(assembler), helper.absolutePos(inside.get(0)))) {
			done(helper, hall);
			throw new GameTestAssertException("set-up: the village isn't protected against a machine at the assembler");
		}
		int[] least = {Integer.MAX_VALUE};
		boolean[] seen = {false};
		helper.onEachTick(() -> {
			for (AbstractContraptionEntity e : contraptions(helper)) {
				seen[0] = true;
				least[0] = Math.min(least[0], e.blockPosition().getX() - helper.absolutePos(BlockPos.ZERO).getX());
			}
		});
		helper.runAfterDelay(3, () -> rollCart(helper, ax));
		helper.runAfterDelay(400, () -> {
			int changed = 0;
			int outWorked = 0;
			StringBuilder in = new StringBuilder();
			StringBuilder out = new StringBuilder();
			for (BlockPos p : all) {
				boolean worked = actor == CartActor.HARVESTER ? !ripeWheat(helper.getBlockState(p))
					: !helper.getBlockState(p.below()).is(Blocks.GRASS_BLOCK) && !helper.getBlockState(p.below()).is(Blocks.DIRT);
				String d = p.getX() + (worked ? ":changed " : ":kept ");
				if (inside.contains(p)) {
					changed += worked ? 1 : 0;
					in.append(d);
				} else {
					outWorked += worked ? 1 : 0;
					out.append(d);
				}
			}
			boolean stillProtected = hall.isProtected();
			clearContraptions(helper);
			done(helper, hall);
			LOG.info("QA verify cart {}: protect={} ax={}: inside changed {} of {} ({}), outside {} of {} ({}), least x {}", actor, protect, ax,
				changed, inside.size(), in, outWorked, outside.size(), out, least[0]);
			String what = actor == CartActor.HARVESTER ? "harvester reaped wheat" : "plough tilled the ground";
			helper.assertTrue(seen[0], "set-up: the Cart Assembler never put the " + actor.name().toLowerCase() + " on the cart");
			helper.assertTrue(outWorked == outside.size(), "set-up: the cart's " + actor.name().toLowerCase() + " didn't work outside the village"
				+ " (least x " + least[0] + "): " + out);
			if (protect) {
				helper.assertTrue(stillProtected, "Frank's hall isn't protected any more");
			}
			if (expectInsideWorked) {
				helper.assertTrue(least[0] <= inside.get(0).getX(), "set-up: the cart never rolled over the village's cells (least x " + least[0] + ")");
				helper.assertTrue(changed == inside.size(), (protect ? "a cart put together inside Frank's protected village" : "the control: a cart in an open village")
					+ ": its " + what + " in only " + changed + " of " + inside.size() + " cells (least x " + least[0] + "): " + in);
			} else {
				helper.assertTrue(changed == 0, "a stranger's minecart contraption (put together outside Frank's protected village at x = " + ax
					+ ") rolled in and its " + what + " in it: " + changed + " of " + inside.size() + " (least x " + least[0] + "): " + in);
			}
			helper.succeed();
		});
	}

	/** B1498 on a minecart, not a train: a stranger's harvester cart reaps none of the protected village's wheat. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122v_cart_harvester_protected"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122v_cart_harvester_protected")
	public void aStrangersHarvesterCartReapsNothingInAProtectedVillage(GameTestHelper helper) {
		cart(helper, CartActor.HARVESTER, true, 22, new int[] {15, 16}, false);
	}

	/** The control: the same harvester cart reaps an open village's wheat. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122v_cart_harvester_open"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122v_cart_harvester_open")
	public void aHarvesterCartReapsAnOpenVillage(GameTestHelper helper) {
		cart(helper, CartActor.HARVESTER, false, 22, new int[] {15, 16}, true);
	}

	/** The fix doesn't over-block: a harvester cart put together inside the protected village (its assembler at x = 10) still reaps there. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122v_cart_harvester_inside"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122v_cart_harvester_inside")
	public void aHarvesterCartPutTogetherInsideAProtectedVillageStillReapsIt(GameTestHelper helper) {
		cart(helper, CartActor.HARVESTER, true, 10, new int[] {}, true);
	}

	/** B1502 on a minecart: a stranger's plough cart tills none of the protected village's grass or dirt. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122v_cart_plough_protected"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122v_cart_plough_protected")
	public void aStrangersPloughCartTillsNothingInAProtectedVillage(GameTestHelper helper) {
		cart(helper, CartActor.PLOUGH, true, 22, new int[] {15, 16}, false);
	}

	/** The control: the same plough cart tills an open village's grass and dirt. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122v_cart_plough_open"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122v_cart_plough_open")
	public void aPloughCartTillsAnOpenVillage(GameTestHelper helper) {
		cart(helper, CartActor.PLOUGH, false, 22, new int[] {15, 16}, true);
	}

	// ---------------------------------------------------------------- 2. a stranger's real train

	private static final int TRAIN_REACH = 8;
	private static final int TIMEOUT = 1200;

	private static boolean inTrainVillage(BlockPos pos) {
		long dx = pos.getX() - TRAIN_HALL.getX();
		long dz = pos.getZ() - TRAIN_HALL.getZ();
		return dx * dx + dz * dz <= (long) TRAIN_REACH * TRAIN_REACH;
	}

	/**
	 * CROPS: a harvester meeting sweet berry bushes and sugar cane inside, and wheat outside including just outside the
	 * border; WHEAT: a harvester on wheat (with a reload); PLOUGH: a plough on grass and dirt (with a reload); WIDE_FILL: a
	 * roller in wide-fill mode over holes.
	 */
	enum TrainCase { CROPS, WHEAT, PLOUGH, WIDE_FILL }

	private static void finish(GameTestHelper helper, VillageHallBlockEntity hall, int radiusBefore, CaravanTrains.Railway r, Entity driver, UUID stranger) {
		hall.setProtected(false);
		VillageHalls.RADIUS = radiusBefore;
		r.scrap(helper.getLevel(), stranger);
		for (SuperGlueEntity g : helper.getLevel().getEntitiesOfClass(SuperGlueEntity.class, helper.getBounds().inflate(4))) {
			g.discard();
		}
		driver.discard();
	}

	/** What a harvester or plough did to the target at {@code p} (kind: 0 wheat, 1 berries, 2 cane, 3 grass, 4 dirt). */
	private static boolean worked(GameTestHelper helper, BlockPos p, int kind) {
		BlockState s = helper.getBlockState(p);
		return switch (kind) {
			case 0 -> !ripeWheat(s);
			case 1 -> !(s.is(Blocks.SWEET_BERRY_BUSH) && s.getValue(SweetBerryBushBlock.AGE) == 3);
			case 2 -> !s.is(Blocks.SUGAR_CANE);
			case 3 -> !helper.getBlockState(p.below()).is(Blocks.GRASS_BLOCK);
			default -> !helper.getBlockState(p.below()).is(Blocks.DIRT);
		};
	}

	private static final String[] KIND = {"wheat", "berries", "cane", "grass", "dirt"};

	private static void target(GameTestHelper helper, BlockPos p, int kind) {
		switch (kind) {
			case 0 -> {
				helper.setBlock(p.below(), Blocks.FARMLAND);
				helper.setBlock(p, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE));
			}
			case 1 -> {
				helper.setBlock(p.below(), Blocks.GRASS_BLOCK);
				helper.setBlock(p, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3));
			}
			case 2 -> {
				// two-high cane on dirt by water (not sand: with air under the floor sand falls and takes the cane along); the
				// harvester at the lane's height cuts the top, leaving the base
				helper.setBlock(p.below(2), Blocks.DIRT);
				helper.setBlock(p.below(2).north(), Blocks.WATER);
				helper.setBlock(p.below(), Blocks.SUGAR_CANE);
				helper.setBlock(p, Blocks.SUGAR_CANE);
			}
			case 3 -> {
				helper.setBlock(p.below(), Blocks.GRASS_BLOCK);
				helper.setBlock(p, Blocks.AIR);
			}
			default -> {
				helper.setBlock(p.below(), Blocks.DIRT);
				helper.setBlock(p, Blocks.AIR);
			}
		}
	}

	private static void train(GameTestHelper helper, TrainCase what, boolean protect, boolean reloadTrain) {
		ServerLevel level = helper.getLevel();
		int radiusBefore = VillageHalls.RADIUS;
		CreateTrainCompatTests.line(helper);
		VillageHalls.RADIUS = TRAIN_REACH;
		helper.setBlock(TRAIN_HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(TRAIN_HALL);
		hall.setOwner(UUID.fromString("5b1d2c3e-4f50-4a6b-8c7d-9e0f1a2b3c4d"), "Frank");
		hall.setProtected(protect);
		helper.onEachTick(() -> {
			if (helper.getTick() >= TIMEOUT - 5 && level.getBlockEntity(helper.absolutePos(TRAIN_HALL)) instanceof VillageHallBlockEntity h) {
				h.setProtected(false);
				VillageHalls.RADIUS = radiusBefore;
			}
		});
		BlockPos a = helper.absolutePos(STATION_A);
		BlockPos b = helper.absolutePos(STATION_B);
		Villager driver = CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		UUID stranger = UUID.randomUUID();
		String tag = stranger.toString().substring(0, 6);
		// target cells: relative pos -> kind; the outside ones split into calibration (x = 12..14) and the border (x = 16..18)
		Map<BlockPos, Integer> inside = new HashMap<>();
		Map<BlockPos, Integer> outside = new HashMap<>();
		Map<BlockPos, Integer> border = new HashMap<>();
		Map<BlockPos, BlockState> snapshot = new HashMap<>();
		int[] lane = {0, 0};
		int[] maxX = {Integer.MIN_VALUE};
		int[] phase = {0};
		int[] tick = {0};
		boolean[] reloaded = {!reloadTrain};
		Contraption[] before = {null};
		CompoundTag[] saved = {null};
		int[] removedAt = {-1};
		String[] how = {"not reloaded"};
		// when each inside target first changed: the tick, the train's front-most x then, and what stood there and below
		Map<BlockPos, String> lost = new HashMap<>();
		boolean harvester = what == TrainCase.CROPS || what == TrainCase.WHEAT;
		helper.onEachTick(() -> {
			tick[0]++;
			CaravanTrains.Railway r = CreateTrainCompatTests.railway();
			if (phase[0] == 0) {
				boolean aimed = r.aim(level, a, b, "QaVa" + tag) & r.aim(level, b, a, "QaVb" + tag);
				if (!aimed || !r.linked(level, a, b)) {
					if (tick[0] > 300) {
						finish(helper, hall, radiusBefore, r, driver, stranger);
						helper.fail("set-up: Create's railway doesn't know both stations on one network after 300 ticks (aimed " + aimed + ")");
					}
					return;
				}
				Map<BlockPos, BlockState> parts = r.train(level, a, b);
				if (parts == null) {
					finish(helper, hall, radiusBefore, r, driver, stranger);
					helper.fail("set-up: no room for the train past the station");
					return;
				}
				parts.forEach((pos, state) -> level.setBlockAndUpdate(pos, state));
				BlockPos side = null;
				for (BlockPos pos : parts.keySet()) {
					if (pos.getY() <= a.getY() || BuiltInRegistries.BLOCK.getKey(parts.get(pos).getBlock()).getPath().endsWith("bogey")) {
						continue;
					}
					if (side == null || pos.getZ() < side.getZ() || (pos.getZ() == side.getZ() && (pos.getX() > side.getX()
						|| (pos.getX() == side.getX() && pos.getY() < side.getY())))) {
						side = pos;
					}
				}
				BlockPos machine = side.north();
				String block = switch (what) {
					case CROPS, WHEAT -> "mechanical_harvester";
					case PLOUGH -> "mechanical_plough";
					case WIDE_FILL -> "mechanical_roller";
				};
				level.setBlockAndUpdate(machine, MechanicCompatTests.with(create(block), "facing", harvester ? "north" : "east"));
				BlockPos chest = null;
				if (what == TrainCase.WIDE_FILL) {
					if (!(level.getBlockEntity(machine) instanceof RollerBlockEntity roller)) {
						finish(helper, hall, radiusBefore, r, driver, stranger);
						helper.fail("set-up: no roller block entity at " + machine);
						return;
					}
					roller.filtering.setFilter(new ItemStack(Items.COBBLESTONE));
					roller.mode.setValue(2); // WIDE_FILL
					roller.setChanged();
					chest = machine.above();
					level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
					if (!(level.getBlockEntity(chest) instanceof Container c)) {
						finish(helper, hall, radiusBefore, r, driver, stranger);
						helper.fail("set-up: no chest");
						return;
					}
					c.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
					c.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
				}
				BlockPos origin = helper.absolutePos(BlockPos.ZERO);
				int py = machine.getY() - origin.getY();
				int pz = machine.getZ() - origin.getZ();
				int mx = machine.getX() - origin.getX();
				lane[0] = py;
				lane[1] = pz;
				int outsideKind = harvester ? 0 : 3;
				// with a reload only x = 12, which the machine passes before the train is unloaded at x >= 13
				for (int x = 12; x <= (reloadTrain ? 12 : 14); x++) {
					outside.put(new BlockPos(x, py, pz), what == TrainCase.PLOUGH ? 3 + (x % 2) : outsideKind);
				}
				if (what == TrainCase.CROPS) {
					for (int x = 16; x <= 18; x++) {
						BlockPos p = new BlockPos(x, py, pz);
						if (!inTrainVillage(p)) {
							border.put(p, 0);
						}
					}
				}
				int i = 0;
				for (int x = 18; x <= 22; x++) {
					BlockPos p = new BlockPos(x, py, pz);
					if (inTrainVillage(p)) {
						int kind = switch (what) {
							case CROPS -> 1 + (i % 2);
							case WHEAT -> 0;
							case PLOUGH -> 3 + (i % 2);
							case WIDE_FILL -> -1;
						};
						inside.put(p, kind);
						i++;
					}
				}
				if (inside.isEmpty() || mx >= 11 || outside.keySet().stream().anyMatch(QaCreateActorsVerify1122eCompatTests::inTrainVillage)
					|| (what == TrainCase.CROPS && border.isEmpty())) {
					finish(helper, hall, radiusBefore, r, driver, stranger);
					helper.fail("set-up: the targets aren't where the test wants them (machine at " + machine.subtract(origin).toShortString() + ")");
					return;
				}
				if (what == TrainCase.WIDE_FILL) {
					List<BlockPos> all = new ArrayList<>(outside.keySet());
					all.addAll(inside.keySet());
					for (BlockPos p : all) {
						helper.setBlock(p.below(), Blocks.AIR);
						helper.setBlock(p.below(2), Blocks.AIR);
						helper.setBlock(p.below(3), Blocks.STONE);
					}
					for (int x = mx + 2; x <= 28; x++) {
						for (int y = 0; y <= py + 1; y++) {
							for (int z = pz - 2; z <= pz; z++) {
								BlockPos p = new BlockPos(x, y, z);
								snapshot.put(p, helper.getBlockState(p));
							}
						}
					}
				} else {
					// cane last: anything placed beside it later would make it check its water again
					List<Map.Entry<BlockPos, Integer>> cells = new ArrayList<>(outside.entrySet());
					cells.addAll(border.entrySet());
					cells.addAll(inside.entrySet());
					cells.sort((x1, x2) -> Integer.compare(x1.getValue() == 2 ? 1 : 0, x2.getValue() == 2 ? 1 : 0));
					for (Map.Entry<BlockPos, Integer> c : cells) {
						target(helper, c.getKey(), c.getValue());
					}
				}
				BoundingBox box = BoundingBox.encapsulatingPositions(parts.keySet()).orElseThrow();
				int topY = Math.max(box.maxY(), chest == null ? machine.getY() : chest.getY());
				level.addFreshEntity(new SuperGlueEntity(level, AABB.encapsulatingFullBlocks(
					new BlockPos(box.minX(), box.minY(), Math.min(box.minZ(), machine.getZ())), new BlockPos(box.maxX(), topY, box.maxZ()))));
				BlockPos firstInside = inside.keySet().iterator().next();
				if (protect && VillageProtection.mayMachineChange(level, a, helper.absolutePos(firstInside))) {
					finish(helper, hall, radiusBefore, r, driver, stranger);
					helper.fail("set-up: the village isn't protected (a machine at the first station may change " + firstInside.toShortString() + ")");
					return;
				}
				if (r.assemble(level, a, b, stranger, driver) == null) {
					finish(helper, hall, radiusBefore, r, driver, stranger);
					helper.fail("set-up: the station didn't assemble the stranger's train with its " + block);
					return;
				}
				phase[0] = 1;
				return;
			}
			if (phase[0] != 1) {
				return;
			}
			List<CarriageContraptionEntity> carriages = level.getEntitiesOfClass(CarriageContraptionEntity.class, helper.getBounds().inflate(8));
			for (AbstractContraptionEntity e : carriages) {
				maxX[0] = Math.max(maxX[0], e.blockPosition().getX() - helper.absolutePos(BlockPos.ZERO).getX());
			}
			if (what != TrainCase.WIDE_FILL) {
				for (Map.Entry<BlockPos, Integer> c : inside.entrySet()) {
					if (!lost.containsKey(c.getKey()) && worked(helper, c.getKey(), c.getValue())) {
						lost.put(c.getKey(), c.getKey().getX() + " at tick " + tick[0] + " (front-most x " + maxX[0] + "; now " + name(helper.getBlockState(c.getKey()).getBlock())
							+ " over " + name(helper.getBlockState(c.getKey().below()).getBlock()) + ")");
					}
				}
			}
			if (!reloaded[0]) {
				if (removedAt[0] < 0 && maxX[0] >= 13 && !carriages.isEmpty()) {
					CarriageContraptionEntity e = carriages.get(0);
					before[0] = e.getContraption();
					CompoundTag t = new CompoundTag();
					if (!e.save(t)) {
						finish(helper, hall, radiusBefore, r, driver, stranger);
						helper.fail("set-up: the train entity didn't save");
						return;
					}
					saved[0] = t;
					try {
						Carriage.DimensionalCarriageEntity dce = e.getCarriage().getDimensional(level);
						java.lang.reflect.Method unload = Carriage.DimensionalCarriageEntity.class.getDeclaredMethod("removeAndSaveEntity",
							CarriageContraptionEntity.class, boolean.class);
						unload.setAccessible(true);
						unload.invoke(dce, e, false);
					} catch (ReflectiveOperationException ex) {
						finish(helper, hall, radiusBefore, r, driver, stranger);
						helper.fail("set-up: couldn't unload the carriage the way Create does: " + ex);
						return;
					}
					if (!e.isRemoved()) {
						e.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
					}
					removedAt[0] = tick[0];
				} else if (removedAt[0] >= 0) {
					for (CarriageContraptionEntity e : carriages) {
						if (!e.isRemoved() && e.getContraption() != null && e.getContraption() != before[0]) {
							reloaded[0] = true;
							how[0] = "Create brought the carriage back from its saved data " + (tick[0] - removedAt[0]) + " ticks after it unloaded";
						}
					}
					if (!reloaded[0] && tick[0] - removedAt[0] >= 20) {
						Entity again = EntityType.loadEntityRecursive(saved[0], level, en -> en);
						if (again == null || !level.addFreshEntity(again)) {
							finish(helper, hall, radiusBefore, r, driver, stranger);
							helper.fail("set-up: the train entity didn't load from its save");
							return;
						}
						reloaded[0] = true;
						how[0] = "the carriage loaded from its save 20 ticks after it unloaded";
					}
				}
			}
			boolean arrived = r.at(level, stranger, b);
			if (!arrived && tick[0] < TIMEOUT - 100) {
				return;
			}
			phase[0] = 2;
			String status = r.status(level, stranger);
			int changed = 0;
			int outWorked = 0;
			int borderWorked = 0;
			StringBuilder seen = new StringBuilder();
			StringBuilder outSeen = new StringBuilder();
			StringBuilder borderSeen = new StringBuilder();
			if (what == TrainCase.WIDE_FILL) {
				for (Map.Entry<BlockPos, BlockState> en : snapshot.entrySet()) {
					BlockState now = helper.getBlockState(en.getKey());
					if (now == en.getValue()) {
						continue;
					}
					BlockPos p = en.getKey();
					String d = p.toShortString() + " " + name(en.getValue().getBlock()) + "->" + name(now.getBlock()) + "; ";
					if (inTrainVillage(p)) {
						changed++;
						seen.append(d);
					} else if (p.getX() < 17) {
						outWorked++;
						outSeen.append(d);
					}
				}
			} else {
				for (Map.Entry<BlockPos, Integer> c : outside.entrySet()) {
					boolean w = worked(helper, c.getKey(), c.getValue());
					outWorked += w ? 1 : 0;
					outSeen.append(c.getKey().getX()).append(' ').append(KIND[c.getValue()]).append(w ? ":changed " : ":kept ");
				}
				for (Map.Entry<BlockPos, Integer> c : border.entrySet()) {
					boolean w = worked(helper, c.getKey(), c.getValue());
					borderWorked += w ? 1 : 0;
					borderSeen.append(c.getKey().getX()).append(' ').append(KIND[c.getValue()]).append(w ? ":changed " : ":kept ");
				}
				for (Map.Entry<BlockPos, Integer> c : inside.entrySet()) {
					boolean w = worked(helper, c.getKey(), c.getValue());
					changed += w ? 1 : 0;
					seen.append(c.getKey().getX()).append(' ').append(KIND[c.getValue()]).append(w ? ":changed " : ":kept ");
				}
			}
			VillageHallBlockEntity now = helper.getBlockEntity(TRAIN_HALL);
			boolean stillProtected = now.isProtected();
			finish(helper, now, radiusBefore, r, driver, stranger);
			LOG.info("QA verify train {}: protect={} reload={}: {} inside changed of {} ({}), outside {} ({}), border {} of {} ({}), lane y {} z {}, front-most x {}, {}; first changed {}",
				what, protect, reloadTrain, changed, inside.size(), seen, outWorked, outSeen, borderWorked, border.size(), borderSeen, lane[0], lane[1], maxX[0], how[0], lost.values());
			String actor = what.name().toLowerCase();
			helper.assertTrue(arrived, "set-up: the stranger's train didn't reach the second station (front-most x " + maxX[0] + ", " + how[0] + "): " + status);
			helper.assertTrue(reloaded[0], "set-up: the train entity was never saved and loaded (front-most x " + maxX[0] + ")");
			if (what == TrainCase.WIDE_FILL) {
				helper.assertTrue(outWorked > 0, "set-up: the train's wide-filling roller changed nothing outside the village (lane y " + lane[0] + " z " + lane[1] + ")");
			} else {
				helper.assertTrue(outWorked == outside.size(), "set-up: the train's " + actor + " machine didn't work outside the village (lane y "
					+ lane[0] + " z " + lane[1] + "): " + outSeen);
			}
			helper.assertTrue(borderWorked == border.size(), "the train's harvester reaped only " + borderWorked + " of " + border.size()
				+ " wheat just outside the border of Frank's " + (protect ? "protected" : "open") + " village (the fix blocks too much): " + borderSeen);
			if (protect) {
				helper.assertTrue(stillProtected, "Frank's hall isn't protected any more");
				helper.assertTrue(changed == 0, "a stranger's Create train (assembled outside Frank's protected village) ran into it and its " + actor
					+ " machine changed " + changed + (what == TrainCase.WIDE_FILL ? " blocks" : " of " + inside.size() + " cells")
					+ (reloadTrain ? " (train saved and loaded on the way: " + how[0] + ")" : "") + ": " + seen + "; first changed: " + lost.values());
			} else if (what == TrainCase.WIDE_FILL) {
				helper.assertTrue(changed > 0, "the control: in an open village the train's wide-filling roller changed nothing (outside: " + outSeen + ")");
			} else {
				helper.assertTrue(changed == inside.size(), "the control: in an open village the train's " + actor + " machine changed only " + changed
					+ " of " + inside.size() + " cells: " + seen);
			}
			helper.succeed();
		});
	}

	/** B1498 for the harvester's other crops: sweet berries and sugar cane inside stay; wheat just outside the border is reaped. */
	//$ gametest_ticks_batch AREA '1200' '"qa_e1122v_train_crops_protected"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_e1122v_train_crops_protected")
	public void aStrangersHarvesterTrainLeavesBerriesAndCaneButReapsJustOutside(GameTestHelper helper) {
		train(helper, TrainCase.CROPS, true, false);
	}

	/** The control: the same train harvests an open village's sweet berries and sugar cane. */
	//$ gametest_ticks_batch AREA '1200' '"qa_e1122v_train_crops_open"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_e1122v_train_crops_open")
	public void aHarvesterTrainHarvestsBerriesAndCaneInAnOpenVillage(GameTestHelper helper) {
		train(helper, TrainCase.CROPS, false, false);
	}

	/** B1503 in wide-fill mode: a stranger's roller train fills nothing in the protected village. */
	//$ gametest_ticks_batch AREA '1200' '"qa_e1122v_train_wide_fill_protected"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_e1122v_train_wide_fill_protected")
	public void aStrangersWideFillingRollerTrainFillsNothingInAProtectedVillage(GameTestHelper helper) {
		train(helper, TrainCase.WIDE_FILL, true, false);
	}

	/** The control: the same wide-filling roller fills an open village's holes. */
	//$ gametest_ticks_batch AREA '1200' '"qa_e1122v_train_wide_fill_open"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_e1122v_train_wide_fill_open")
	public void aWideFillingRollerTrainFillsAnOpenVillage(GameTestHelper helper) {
		train(helper, TrainCase.WIDE_FILL, false, false);
	}

	/** B1498 after a reload: the stranger's harvester train, unloaded and loaded just before the village, still reaps nothing inside. */
	//$ gametest_ticks_batch AREA '1200' '"qa_e1122v_train_harvester_reloaded"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_e1122v_train_harvester_reloaded")
	public void aStrangersHarvesterTrainReloadedOutsideStillReapsNothingInside(GameTestHelper helper) {
		train(helper, TrainCase.WHEAT, true, true);
	}

	/** The control: the reloaded harvester train reaps an open village's wheat. */
	//$ gametest_ticks_batch AREA '1200' '"qa_e1122v_train_harvester_reloaded_open"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_e1122v_train_harvester_reloaded_open")
	public void aHarvesterTrainReloadedOutsideReapsAnOpenVillage(GameTestHelper helper) {
		train(helper, TrainCase.WHEAT, false, true);
	}

	/** B1502 after a reload: the stranger's plough train, unloaded and loaded just before the village, still tills nothing inside. */
	//$ gametest_ticks_batch AREA '1200' '"qa_e1122v_train_plough_reloaded"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_e1122v_train_plough_reloaded")
	public void aStrangersPloughTrainReloadedOutsideStillTillsNothingInside(GameTestHelper helper) {
		train(helper, TrainCase.PLOUGH, true, true);
	}

	// ---------------------------------------------------------------- 3. B1487: an elevator turned, and reloaded

	private static void motorSpeed(GameTestHelper helper, BlockPos pos, int rpm) {
		BlockEntity be = helper.getBlockEntity(pos);
		CompoundTag tag = be.saveWithoutMetadata(helper.getLevel().registryAccess());
		tag.putInt("ScrollValue", rpm);
		be.loadWithComponents(tag, helper.getLevel().registryAccess());
		be.setChanged();
	}

	/**
	 * An Elevator Pulley outside the village with a cabin of slime, slime and an oak plank, the plank in the village's last
	 * cell. TURNED: on the village's south edge (column x = 3: z = 23 the last inside, the pulley at z = 25), the cabin
	 * reaching north. Otherwise B1487's own spot (row z = 15, the pulley at x = 13, the plank at x = 11). RELOAD: the cabin
	 * is saved and loaded again 20 ticks after it was put together, before the pulley is broken.
	 */
	private static void elevator(GameTestHelper helper, boolean protect, boolean turned, boolean reload) {
		VillageHallBlockEntity hall = hall(helper, protect);
		watchdog(helper, 300);
		BlockPos pulley;
		BlockPos end;
		BlockPos mid;
		BlockPos contact;
		BlockPos motor;
		String pulleyFacing;
		String contactFacing;
		String motorFacing;
		if (turned) {
			for (int x = 1; x <= 5; x++) {
				for (int z = 22; z <= 26; z++) {
					for (int y = 2; y <= 12; y++) {
						helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
					}
				}
			}
			pulley = new BlockPos(3, 12, 25);
			mid = new BlockPos(3, 11, 24);
			end = new BlockPos(3, 11, 23);
			contact = new BlockPos(4, 11, 25);
			contactFacing = "east";
			pulleyFacing = "east";
			motor = new BlockPos(3, 12, 26);
			motorFacing = "north";
		} else {
			int z = 15;
			for (int x = 10; x <= 14; x++) {
				for (int zz = z; zz <= z + 1; zz++) {
					for (int y = 2; y <= 11; y++) {
						helper.setBlock(new BlockPos(x, y, zz), Blocks.AIR);
					}
				}
			}
			pulley = new BlockPos(13, 12, z);
			mid = new BlockPos(12, 11, z);
			end = new BlockPos(11, 11, z);
			contact = new BlockPos(13, 11, z + 1);
			contactFacing = "south";
			pulleyFacing = "north";
			motor = new BlockPos(14, 12, z);
			motorFacing = "west";
		}
		if (!inVillage(end) || inVillage(mid) || inVillage(pulley)) {
			done(helper, hall);
			throw new GameTestAssertException("set-up: the cabin's end isn't the village's last cell");
		}
		helper.setBlock(pulley, MechanicCompatTests.with(create("elevator_pulley"), "facing", pulleyFacing));
		helper.setBlock(pulley.below(), Blocks.SLIME_BLOCK);
		helper.setBlock(mid, Blocks.SLIME_BLOCK);
		helper.setBlock(end, Blocks.OAK_PLANKS);
		helper.setBlock(contact, MechanicCompatTests.with(create("redstone_contact"), "facing", contactFacing));
		helper.setBlock(motor, MechanicCompatTests.with(create("creative_motor"), "facing", motorFacing));
		motorSpeed(helper, motor, 64);
		if (protect && VillageProtection.mayMachineChange(helper.getLevel(), helper.absolutePos(pulley), helper.absolutePos(end))) {
			done(helper, hall);
			throw new GameTestAssertException("set-up: the village isn't protected against the pulley");
		}
		int[] seen = {0};
		boolean[] plankWhileAssembled = {true};
		String[] reloadNote = {reload ? "not reloaded" : "no reload"};
		helper.onEachTick(() -> seen[0] = Math.max(seen[0], contraptions(helper).size()));
		helper.runAfterDelay(10, () -> {
			if (helper.getLevel().getBlockEntity(helper.absolutePos(pulley)) instanceof com.simibubi.create.content.contraptions.elevator.ElevatorPulleyBlockEntity e) {
				e.clicked(); // what right-clicking the pulley does
			}
		});
		if (reload) {
			helper.runAfterDelay(30, () -> {
				List<AbstractContraptionEntity> all = contraptions(helper);
				if (all.size() != 1) {
					reloadNote[0] = "reload skipped: " + all.size() + " cabins";
					return;
				}
				AbstractContraptionEntity entity = all.get(0);
				CompoundTag tag = new CompoundTag();
				if (!entity.save(tag)) {
					reloadNote[0] = "reload skipped: the cabin didn't save";
					return;
				}
				entity.discard();
				Entity again = EntityType.loadEntityRecursive(tag, helper.getLevel(), e -> e);
				reloadNote[0] = again != null && helper.getLevel().tryAddFreshEntityWithPassengers(again) ? "reloaded" : "reload failed to add the cabin";
			});
		}
		helper.runAfterDelay(50, () -> plankWhileAssembled[0] = helper.getBlockState(end).is(Blocks.OAK_PLANKS));
		helper.runAfterDelay(60, () -> helper.destroyBlock(pulley));
		helper.runAfterDelay(200, () -> {
			boolean plankAfter = helper.getBlockState(end).is(Blocks.OAK_PLANKS);
			int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4), e -> e.getItem().is(Items.OAK_PLANKS)).size();
			clearContraptions(helper);
			done(helper, hall);
			String state = "plank in the village's last cell " + end.toShortString() + " while the cabin was together: " + plankWhileAssembled[0]
				+ ", after the pulley was broken: " + plankAfter + " (oak plank items on the ground " + dropped + "; cabins seen " + seen[0] + "; "
				+ reloadNote[0] + ")";
			LOG.info("QA verify elevator protect={} turned={} reload={}: {}", protect, turned, reload, state);
			helper.assertTrue(seen[0] > 0, "set-up: the elevator never put its cabin together: " + state);
			if (reload) {
				helper.assertTrue(reloadNote[0].equals("reloaded"), "set-up: the cabin wasn't saved and loaded: " + state);
			}
			if (protect) {
				helper.assertTrue(plankWhileAssembled[0] && plankAfter && dropped == 0, "a stranger's elevator pulley outside Frank's protected village"
					+ (turned ? " (on its south edge)" : "") + " took the plank in the village's last cell: " + state);
			} else {
				helper.assertTrue(!plankWhileAssembled[0], "the control: the elevator's cabin didn't take the plank over the open village's edge: " + state);
				helper.assertTrue(plankAfter, "the control: breaking the elevator pulley didn't set the plank back in its place: " + state);
			}
			helper.succeed();
		});
	}

	/** B1487 turned 90 degrees: a stranger's elevator on the protected village's south edge leaves the plank in its cabin's reach where it is. */
	//$ gametest_ticks_batch AREA '300' '"qa_e1122v_elevator_turned_protected"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_e1122v_elevator_turned_protected")
	public void aStrangersTurnedElevatorDoesntTakeABlockOutOfAProtectedVillage(GameTestHelper helper) {
		elevator(helper, true, true, false);
	}

	/** The control: the turned elevator beside an open village takes the plank into its cabin and sets it back. */
	//$ gametest_ticks_batch AREA '300' '"qa_e1122v_elevator_turned_open"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_e1122v_elevator_turned_open")
	public void aTurnedElevatorBesideAnOpenVillageTakesItsCabinAndSetsItBack(GameTestHelper helper) {
		elevator(helper, false, true, false);
	}

	/** B1487 with a reload between assembling and breaking: the plank stays and nothing drops. */
	//$ gametest_ticks_batch AREA '300' '"qa_e1122v_elevator_reloaded_protected"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_e1122v_elevator_reloaded_protected")
	public void aStrangersElevatorReloadedBeforeBreakingStillLeavesTheBlock(GameTestHelper helper) {
		elevator(helper, true, false, true);
	}

	/** The control: the reloaded elevator beside an open village takes the plank and sets it back when broken. */
	//$ gametest_ticks_batch AREA '300' '"qa_e1122v_elevator_reloaded_open"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_e1122v_elevator_reloaded_open")
	public void anElevatorReloadedBeforeBreakingBesideAnOpenVillageSetsItBack(GameTestHelper helper) {
		elevator(helper, false, false, true);
	}
}
