package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreatePortableTrains1122eCompatTests.STATION_A;
import static io.github.jcondedata.aliveworkplace.compat.QaCreatePortableTrains1122eCompatTests.STATION_B;
import static io.github.jcondedata.aliveworkplace.compat.QaCreatePortableTrains1122eCompatTests.TRAIN_HALL;

import com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
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
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane E, lane-e-1122-1613): village protection against the two block-changing actors the earlier train-actor tests
 * ({@link QaCreateTrainActors1122eCompatTests}: Deployer, Harvester, Drill) left out, each on a stranger's real Create
 * train running into Frank's village. The promise (VillageProtection's javadoc, StandIns): inside a protected village only
 * the owner, friends and operators may break or place blocks or trample the fields, and a stranger's machine is a stranger.
 *
 * <p>The set-up is the train-actor tests': a stranger's train (glued, a conductor in the seat) assembled at the station at
 * x = 6 and scheduled to x = 23 runs along the track at z = 12 past Frank's hall (22, 2, 4, reach 8). The actor is glued to
 * the train's north side facing east, the way it travels (Create's plough and roller act only moving forward):
 * <ul>
 * <li>a Mechanical Plough: in its row a torch, a poppy (on grass) and bare grass (which it tills to farmland), at x = 12..14
 * outside the village (calibration) and x = 19..22 inside. (Create's plough spares crops standing on farmland by design,
 * so wheat isn't a target: an open village's wheat would stay too.)</li>
 * <li>a Mechanical Roller in its default mode (tunnel pave) with a cobblestone filter and a chest of cobblestone glued
 * on: in its lane under it, planks to clear and a hole to pave, outside and inside. A protected village's cells stay as
 * they were; an open village's are paved or cleared. The same roller set to straight fill (nothing to clear, a hole one
 * block deep in each cell) fills an open village's holes; that it fills none of a protected one's, and the
 * plough's tilling none there, are B1502 and B1503 ({@link QaCreateTrainPloughRoller1122eBugCompatTests}).</li>
 * </ul>
 */
public class QaCreateTrainPloughRoller1122eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final int TRAIN_REACH = 8;
	private static final int TIMEOUT = 1200;
	private static final int[] OUTSIDE = {12, 13, 14};

	/**
	 * PLOUGH: torch, poppy, grass and dirt inside (the whole promise); PLOUGH_FRAGILE: torches and poppies only (what it
	 * breaks); ROLLER: tunnel pave over planks and a hole; ROLLER_FILL: straight fill into holes, nothing to clear.
	 */
	enum Actor { PLOUGH, PLOUGH_FRAGILE, ROLLER, ROLLER_FILL }

	private static boolean plough(Actor actor) {
		return actor == Actor.PLOUGH || actor == Actor.PLOUGH_FRAGILE;
	}

	private static int kind(Actor actor, int i) {
		return actor == Actor.PLOUGH_FRAGILE ? i % 2 : i % 4;
	}

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");

	static boolean inTrainVillage(BlockPos pos) {
		long dx = pos.getX() - TRAIN_HALL.getX();
		long dz = pos.getZ() - TRAIN_HALL.getZ();
		return dx * dx + dz * dz <= (long) TRAIN_REACH * TRAIN_REACH;
	}

	/** The plough's targets: index 0 a torch on stone, 1 a poppy on grass, 2 bare grass, 3 bare dirt (both to till). */
	private static void ploughCell(GameTestHelper helper, BlockPos p, int kind) {
		switch (kind) {
			case 0 -> {
				helper.setBlock(p.below(), Blocks.STONE);
				helper.setBlock(p, Blocks.TORCH);
			}
			case 1 -> {
				helper.setBlock(p.below(), Blocks.GRASS_BLOCK);
				helper.setBlock(p, Blocks.POPPY);
			}
			case 2 -> {
				helper.setBlock(p.below(), Blocks.GRASS_BLOCK);
				helper.setBlock(p, Blocks.AIR);
			}
			default -> {
				helper.setBlock(p.below(), Blocks.DIRT);
				helper.setBlock(p, Blocks.AIR);
			}
		}
	}

	/** Whether the plough changed its target (broke the torch or poppy, or tilled the ground). */
	private static boolean ploughed(GameTestHelper helper, BlockPos p, int kind) {
		return switch (kind) {
			case 0 -> !helper.getBlockState(p).is(Blocks.TORCH);
			case 1 -> !helper.getBlockState(p).is(Blocks.POPPY);
			case 2 -> !helper.getBlockState(p.below()).is(Blocks.GRASS_BLOCK);
			default -> !helper.getBlockState(p.below()).is(Blocks.DIRT);
		};
	}

	private static final String[] PLOUGH_KIND = {"torch", "poppy", "grass", "dirt"};

	static void actorTrain(GameTestHelper helper, Actor actor, boolean protect) {
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
		List<BlockPos> inside = new ArrayList<>();
		List<BlockPos> outside = new ArrayList<>();
		Map<BlockPos, BlockState> snapshot = new HashMap<>();
		int[] lane = {0, 0}; // the actor's relative y and z
		int[] maxX = {Integer.MIN_VALUE};
		int[] phase = {0};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			CaravanTrains.Railway r = CreateTrainCompatTests.railway();
			if (phase[0] == 0) {
				boolean aimed = r.aim(level, a, b, "QaEps" + tag) & r.aim(level, b, a, "QaZeta" + tag);
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
				String block = plough(actor) ? "mechanical_plough" : "mechanical_roller";
				level.setBlockAndUpdate(machine, MechanicCompatTests.with(MechanicCompatTests.create(block).defaultBlockState(), "facing", "east"));
				BlockPos chest = null;
				if (!plough(actor)) {
					if (!(level.getBlockEntity(machine) instanceof RollerBlockEntity roller)) {
						finish(helper, hall, radiusBefore, r, driver, stranger);
						helper.fail("set-up: no roller block entity at " + machine);
						return;
					}
					roller.filtering.setFilter(new ItemStack(Items.COBBLESTONE));
					if (actor == Actor.ROLLER_FILL) {
						roller.mode.setValue(1); // STRAIGHT_FILL: fills below its lane, breaks nothing
					}
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
				lane[0] = py;
				lane[1] = pz;
				int mx = machine.getX() - origin.getX();
				for (int x : OUTSIDE) {
					outside.add(new BlockPos(x, py, pz));
				}
				for (int x = 18; x <= 22; x++) {
					BlockPos p = new BlockPos(x, py, pz);
					if (inTrainVillage(p)) {
						inside.add(p);
					}
				}
				if (inside.isEmpty() || mx >= OUTSIDE[0] - 1 || outside.stream().anyMatch(QaCreateTrainPloughRoller1122eCompatTests::inTrainVillage)) {
					finish(helper, hall, radiusBefore, r, driver, stranger);
					helper.fail("set-up: the targets aren't where the test wants them (machine at " + machine.subtract(origin).toShortString() + ")");
					return;
				}
				if (plough(actor)) {
					for (int i = 0; i < outside.size(); i++) {
						ploughCell(helper, outside.get(i), i);
					}
					for (int i = 0; i < inside.size(); i++) {
						ploughCell(helper, inside.get(i), kind(actor, i));
					}
				} else {
					// under the roller: (tunnel pave) planks to clear just below it; a hole two below, where it paves
					List<BlockPos> all = new ArrayList<>(outside);
					all.addAll(inside);
					for (BlockPos p : all) {
						helper.setBlock(p.below(), actor == Actor.ROLLER ? Blocks.OAK_PLANKS : Blocks.AIR);
						helper.setBlock(p.below(2), Blocks.AIR);
						helper.setBlock(p.below(3), Blocks.STONE); // a hole one deep: a filling roller fills just it
					}
					for (int x = mx + 2; x <= 28; x++) {
						for (int y = 0; y <= py + 1; y++) {
							for (int z = pz - 1; z <= pz; z++) {
								BlockPos p = new BlockPos(x, y, z);
								snapshot.put(p, helper.getBlockState(p));
							}
						}
					}
				}
				BoundingBox box = BoundingBox.encapsulatingPositions(parts.keySet()).orElseThrow();
				int topY = Math.max(box.maxY(), chest == null ? machine.getY() : chest.getY());
				level.addFreshEntity(new SuperGlueEntity(level, AABB.encapsulatingFullBlocks(
					new BlockPos(box.minX(), box.minY(), Math.min(box.minZ(), machine.getZ())), new BlockPos(box.maxX(), topY, box.maxZ()))));
				if (protect && VillageProtection.mayMachineChange(level, a, helper.absolutePos(inside.get(0)))) {
					finish(helper, hall, radiusBefore, r, driver, stranger);
					helper.fail("set-up: the village isn't protected (a machine at the first station may change " + inside.get(0).toShortString() + ")");
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
			for (Entity e : level.getEntitiesOfClass(com.simibubi.create.content.trains.entity.CarriageContraptionEntity.class, helper.getBounds().inflate(8))) {
				maxX[0] = Math.max(maxX[0], e.blockPosition().getX() - helper.absolutePos(BlockPos.ZERO).getX());
			}
			boolean arrived = r.at(level, stranger, b);
			if (!arrived && tick[0] < TIMEOUT - 100) {
				return;
			}
			phase[0] = 2;
			String status = r.status(level, stranger);
			int changed = 0;
			int outWorked = 0;
			StringBuilder seen = new StringBuilder();
			StringBuilder outSeen = new StringBuilder();
			if (plough(actor)) {
				for (int i = 0; i < outside.size(); i++) {
					boolean done = ploughed(helper, outside.get(i), i);
					outWorked += done ? 1 : 0;
					outSeen.append(PLOUGH_KIND[i]).append(done ? ":changed " : ":kept ");
				}
				for (int i = 0; i < inside.size(); i++) {
					int k = kind(actor, i);
					boolean done = ploughed(helper, inside.get(i), k);
					changed += done ? 1 : 0;
					seen.append(inside.get(i).getX()).append(' ').append(PLOUGH_KIND[k]).append(done ? ":changed " : ":kept ");
				}
			} else {
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
			}
			VillageHallBlockEntity now = helper.getBlockEntity(TRAIN_HALL);
			boolean stillProtected = now.isProtected();
			finish(helper, now, radiusBefore, r, driver, stranger);
			LOG.info("QA train {}: protect={}: {} inside changed ({}), outside worked {} ({}), lane y {} z {}, front-most x {}",
				actor, protect, changed, seen, outWorked, outSeen, lane[0], lane[1], maxX[0]);
			String what = plough(actor) ? "plough" : "roller";
			helper.assertTrue(arrived, "set-up: the stranger's train didn't reach the second station (front-most x " + maxX[0] + "): " + status);
			if (plough(actor)) {
				helper.assertTrue(outWorked == outside.size(), "set-up: the train's plough didn't work outside the village (lane y "
					+ lane[0] + " z " + lane[1] + "): " + outSeen);
			} else {
				helper.assertTrue(outWorked > 0, "set-up: the train's roller changed nothing outside the village (lane y " + lane[0] + " z " + lane[1] + ")");
			}
			if (protect) {
				helper.assertTrue(stillProtected, "Frank's hall isn't protected any more");
				helper.assertTrue(changed == 0, "a stranger's Create train (assembled outside Frank's protected village) ran into it and its "
					+ what + " changed " + changed + " blocks in it (outside it: " + outSeen + "): " + seen);
			} else if (plough(actor)) {
				helper.assertTrue(changed == inside.size(), "the control: in an open village the train's plough changed only " + changed + " of "
					+ inside.size() + " cells: " + seen);
			} else {
				helper.assertTrue(changed > 0, "the control: in an open village the train's roller changed nothing (outside: " + outSeen + ")");
			}
			helper.succeed();
		});
	}

	private static String name(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).getPath();
	}

	private static void finish(GameTestHelper helper, VillageHallBlockEntity hall, int radiusBefore, CaravanTrains.Railway r, Entity driver, UUID stranger) {
		hall.setProtected(false);
		VillageHalls.RADIUS = radiusBefore;
		r.scrap(helper.getLevel(), stranger);
		for (SuperGlueEntity g : helper.getLevel().getEntitiesOfClass(SuperGlueEntity.class, helper.getBounds().inflate(4))) {
			g.discard();
		}
		driver.discard();
	}

	/**
	 * A Plough on a stranger's train breaks no torch or flower in Frank's protected village. (That it tills none of its
	 * ground either is B1502: {@link QaCreateTrainPloughRoller1122eBugCompatTests}.)
	 */
	//$ gametest_ticks_batch AREA '1200' '"qa_e1122c_train_plough_protected"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_e1122c_train_plough_protected")
	public void aStrangersPloughTrainBreaksNoTorchOrFlowerInAProtectedVillage(GameTestHelper helper) {
		actorTrain(helper, Actor.PLOUGH_FRAGILE, true);
	}

	/** The control: the same plough train breaks the torch and the flower and tills the ground of an open village. */
	//$ gametest_ticks_batch AREA '1200' '"qa_e1122c_train_plough_open"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_e1122c_train_plough_open")
	public void aPloughTrainWorksAnOpenVillage(GameTestHelper helper) {
		actorTrain(helper, Actor.PLOUGH, false);
	}

	/** A Roller on a stranger's train clears and paves nothing in Frank's protected village. */
	//$ gametest_ticks_batch AREA '1200' '"qa_e1122c_train_roller_protected"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_e1122c_train_roller_protected")
	public void aStrangersRollerTrainChangesNothingInAProtectedVillage(GameTestHelper helper) {
		actorTrain(helper, Actor.ROLLER, true);
	}

	/** The control: the same roller train paves through an open village. */
	//$ gametest_ticks_batch AREA '1200' '"qa_e1122c_train_roller_open"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_e1122c_train_roller_open")
	public void aRollerTrainPavesAnOpenVillage(GameTestHelper helper) {
		actorTrain(helper, Actor.ROLLER, false);
	}

	/**
	 * The control: the same filling roller train fills the holes of an open village. (That it fills none in a protected
	 * one is B1503: {@link QaCreateTrainPloughRoller1122eBugCompatTests}.)
	 */
	//$ gametest_ticks_batch AREA '1200' '"qa_e1122c_train_roller_fill_open"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_e1122c_train_roller_fill_open")
	public void aFillingRollerTrainFillsAnOpenVillage(GameTestHelper helper) {
		actorTrain(helper, Actor.ROLLER_FILL, false);
	}
}
