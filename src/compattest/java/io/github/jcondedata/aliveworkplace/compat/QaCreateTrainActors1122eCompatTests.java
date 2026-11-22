package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreatePortableTrains1122eCompatTests.STATION_A;
import static io.github.jcondedata.aliveworkplace.compat.QaCreatePortableTrains1122eCompatTests.STATION_B;
import static io.github.jcondedata.aliveworkplace.compat.QaCreatePortableTrains1122eCompatTests.TRAIN_HALL;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane E, lane-e-1122-1313): village protection against machines riding a stranger's real Create train, the cases
 * lane-e-1122-0713 listed as not tested. The promise (B993/B808, village-hall.md): a machine whose owner or machine stands
 * outside a protected village places, breaks and changes nothing inside it; in an open village the same machine works.
 *
 * <p>The set-up is {@link QaCreatePortableTrains1122eCompatTests#drillTrain}'s: a stranger's train (CreateTrains' drawn
 * carriage, glued, a conductor in the seat) assembled at the station at x = 6 and scheduled to the station at x = 23 runs
 * along the track at z = 12 past Frank's hall (22, 2, 4, reach 8), whose village covers the track's north side at x = 17..27.
 * A machine is glued to the train's north side facing north:
 * <ul>
 * <li>a Deployer the stranger placed, holding 32 cobblestone: it places in front of it outside the village (calibration)
 * and must place none inside a protected one (open village: it does);</li>
 * <li>a Mechanical Harvester: ripe wheat stands in its path at x = 12 (outside, calibration) and x = 18..22 (inside); a
 * protected village's wheat stays ripe (fails today, B-pending: {@link QaCreateTrainActors1122eBugCompatTests}), an open
 * village's is reaped (the control, here);</li>
 * <li>a Mechanical Drill, with the train entity itself saved and loaded (as a chunk unloading and loading does) while it
 * stands just outside the village: on its way in it still breaks none of the planks inside (open village: all).</li>
 * </ul>
 */
public class QaCreateTrainActors1122eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final int TRAIN_REACH = 8;
	private static final int TIMEOUT = 1200;

	enum Actor { DEPLOYER, HARVESTER, DRILL }

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");

	private static boolean inTrainVillage(BlockPos pos) {
		long dx = pos.getX() - TRAIN_HALL.getX();
		long dz = pos.getZ() - TRAIN_HALL.getZ();
		return dx * dx + dz * dz <= (long) TRAIN_REACH * TRAIN_REACH;
	}

	private static boolean ripe(BlockState state) {
		return state.is(Blocks.WHEAT) && state.getValue(CropBlock.AGE) == CropBlock.MAX_AGE;
	}

	static void actorTrain(GameTestHelper helper, Actor actor, boolean protect, boolean reloadTrain) {
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
		// a new stranger and new station names each run (an earlier batch's train or station would otherwise be the one found)
		UUID stranger = UUID.randomUUID();
		String tag = stranger.toString().substring(0, 6);
		List<BlockPos> inside = new ArrayList<>();
		BlockPos[] outside = {null};
		int[] maxX = {Integer.MIN_VALUE};
		int[] phase = {0}; // 0 waiting for the network, 1 running, 2 checked
		int[] tick = {0};
		boolean[] reloaded = {!reloadTrain};
		Contraption[] before = {null};
		CompoundTag[] saved = {null};
		int[] removedAt = {-1};
		String[] how = {"not reloaded"};
		helper.onEachTick(() -> {
			tick[0]++;
			CaravanTrains.Railway r = CreateTrainCompatTests.railway();
			if (phase[0] == 0) {
				boolean aimed = r.aim(level, a, b, "QaGamma" + tag) & r.aim(level, b, a, "QaDelta" + tag);
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
				// the machine: north of the train's north-most, front-most block above the bogey
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
				String block = switch (actor) {
					case DEPLOYER -> "deployer";
					case HARVESTER -> "mechanical_harvester";
					case DRILL -> "mechanical_drill";
				};
				level.setBlockAndUpdate(machine, MechanicCompatTests.with(MechanicCompatTests.create(block).defaultBlockState(), "facing", "north"));
				BlockPos origin = helper.absolutePos(BlockPos.ZERO);
				int py = machine.getY() - origin.getY();
				// a deployer and a drill work on the block in front of them; a harvester on the crops it passes through
				int pz = machine.getZ() - origin.getZ() - (actor == Actor.HARVESTER ? 0 : 1);
				if (actor == Actor.DEPLOYER) {
					BlockEntity be = level.getBlockEntity(machine);
					if (be == null) {
						finish(helper, hall, radiusBefore, r, driver, stranger);
						throw new GameTestAssertException("set-up: no deployer block entity");
					}
					CompoundTag t = be.saveWithoutMetadata(level.registryAccess());
					t.putUUID("Owner", stranger);
					be.loadWithComponents(t, level.registryAccess());
					long in = QaCreateDeployerOnOurThings1102eCompatTests.insert(level, machine, new ItemStack(Items.COBBLESTONE, 32));
					if (in == 0) {
						finish(helper, hall, radiusBefore, r, driver, stranger);
						helper.fail("set-up: the deployer took no cobblestone");
						return;
					}
				}
				BoundingBox box = BoundingBox.encapsulatingPositions(parts.keySet()).orElseThrow();
				level.addFreshEntity(new SuperGlueEntity(level, AABB.encapsulatingFullBlocks(
					new BlockPos(box.minX(), box.minY(), Math.min(box.minZ(), machine.getZ())), new BlockPos(box.maxX(), box.maxY(), box.maxZ()))));
				outside[0] = new BlockPos(12, py, pz);
				for (int x = 18; x <= 22; x++) {
					BlockPos p = new BlockPos(x, py, pz);
					if (inTrainVillage(p)) {
						inside.add(p);
					}
				}
				List<BlockPos> targets = new ArrayList<>(inside);
				targets.add(outside[0]);
				for (BlockPos p : targets) {
					switch (actor) {
						case HARVESTER -> {
							helper.setBlock(p.below(), Blocks.FARMLAND);
							helper.setBlock(p, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE));
						}
						case DRILL -> helper.setBlock(p, Blocks.OAK_PLANKS);
						case DEPLOYER -> {
							// the deployer places into the air in front of it all along its way; nothing set
						}
					}
				}
				if (inside.isEmpty() || inTrainVillage(outside[0])) {
					finish(helper, hall, radiusBefore, r, driver, stranger);
					helper.fail("set-up: the targets aren't where the test wants them (machine at " + machine.subtract(origin).toShortString() + ")");
					return;
				}
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
			List<CarriageContraptionEntity> carriages = level.getEntitiesOfClass(CarriageContraptionEntity.class, helper.getBounds().inflate(8));
			for (AbstractContraptionEntity e : carriages) {
				maxX[0] = Math.max(maxX[0], e.blockPosition().getX() - helper.absolutePos(BlockPos.ZERO).getX());
			}
			if (!reloaded[0]) {
				if (removedAt[0] < 0 && maxX[0] >= 13 && !carriages.isEmpty()) {
					// the train entity saved and unloaded with its chunk, standing just outside the village
					CarriageContraptionEntity e = carriages.get(0);
					before[0] = e.getContraption();
					CompoundTag t = new CompoundTag();
					if (!e.save(t)) {
						finish(helper, hall, radiusBefore, r, driver, stranger);
						helper.fail("set-up: the train entity didn't save");
						return;
					}
					saved[0] = t;
					// Create's own unload path (what its carriage does when it leaves loaded chunks): the conductor and the
					// carriage saved into the train's data and the entities discarded; Create brings them back from that data
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
						// Create didn't bring it back by itself: load it from its save, as a chunk loading does
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
			if (arrived || tick[0] >= TIMEOUT - 100) {
				phase[0] = 2;
				String status = r.status(level, stranger);
				boolean calibrated;
				int changed = 0;
				StringBuilder seen = new StringBuilder();
				int cobbleOut = 0;
				if (actor == Actor.DEPLOYER) {
					for (BlockPos p : BlockPos.betweenClosed(new BlockPos(0, 2, 0), new BlockPos(29, 7, 29))) {
						if (helper.getBlockState(p).is(Blocks.COBBLESTONE)) {
							if (inTrainVillage(p)) {
								changed++;
								seen.append("in:").append(p.toShortString()).append("; ");
							} else {
								cobbleOut++;
							}
						}
					}
					calibrated = cobbleOut > 0;
				} else {
					calibrated = actor == Actor.HARVESTER ? !ripe(helper.getBlockState(outside[0])) : helper.getBlockState(outside[0]).isAir();
					for (BlockPos p : inside) {
						BlockState s = helper.getBlockState(p);
						boolean gone = actor == Actor.HARVESTER ? !ripe(s) : !s.is(Blocks.OAK_PLANKS);
						changed += gone ? 1 : 0;
						seen.append(p.getX()).append(gone ? ":changed " : ":kept ");
					}
				}
				VillageHallBlockEntity now = helper.getBlockEntity(TRAIN_HALL);
				boolean stillProtected = now.isProtected();
				finish(helper, now, radiusBefore, r, driver, stranger);
				String what = switch (actor) {
					case DEPLOYER -> "deployer placed cobblestone in";
					case HARVESTER -> "harvester reaped wheat in";
					case DRILL -> "drill broke planks in";
				};
				helper.assertTrue(arrived, "set-up: the stranger's train didn't reach the second station (front-most x " + maxX[0] + ", " + how[0] + "): " + status);
				helper.assertTrue(reloaded[0], "set-up: the train entity was never saved and loaded (front-most x " + maxX[0] + ")");
				helper.assertTrue(calibrated, "set-up: the train's " + actor.name().toLowerCase() + " did nothing outside the village (front-most x "
					+ maxX[0] + (actor == Actor.DEPLOYER ? "" : ", target at x = 12: " + helper.getBlockState(outside[0])) + ")");
				if (protect) {
					helper.assertTrue(stillProtected, "Frank's hall isn't protected any more");
					helper.assertTrue(changed == 0, "a stranger's Create train (assembled outside Frank's protected village) ran into it and its "
						+ what + " it: " + changed + (actor == Actor.DEPLOYER ? " blocks" : " of " + inside.size()) + (reloadTrain ? " (train entity saved and loaded on the way: " + how[0] + ")" : "") + ": " + seen);
				} else if (actor == Actor.DEPLOYER) {
					helper.assertTrue(changed > 0, "the control: the train's deployer placed no cobblestone in an open village (" + cobbleOut + " outside)");
				} else {
					helper.assertTrue(changed == inside.size(), "the control: in an open village the train's " + what + " only " + changed + " of "
						+ inside.size() + " cells" + (reloadTrain ? " (" + how[0] + ")" : "") + ": " + seen);
				}
				LOG.info("QA train actors: {} protect={} reload={}: {} inside changed of {} ({}), outside worked={} ({} cobblestone outside), front-most x {}, {}",
					actor, protect, reloadTrain, changed, actor == Actor.DEPLOYER ? "-" : inside.size(), seen, calibrated, cobbleOut, maxX[0], how[0]);
				helper.succeed();
			}
		});
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

	/** A Deployer a stranger put on his train places nothing in Frank's protected village as the train runs in. */
	//$ gametest_ticks_batch AREA '1200' '"qa_e1122b_train_deployer_protected"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_e1122b_train_deployer_protected")
	public void aStrangersDeployerTrainPlacesNothingInAProtectedVillage(GameTestHelper helper) {
		actorTrain(helper, Actor.DEPLOYER, true, false);
	}

	/** The control: the same deployer train places cobblestone beside the track in an open village. */
	//$ gametest_ticks_batch AREA '1200' '"qa_e1122b_train_deployer_open"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_e1122b_train_deployer_open")
	public void aDeployerTrainPlacesInAnOpenVillage(GameTestHelper helper) {
		actorTrain(helper, Actor.DEPLOYER, false, false);
	}

	/** The control: the same harvester train reaps the wheat of an open village. */
	//$ gametest_ticks_batch AREA '1200' '"qa_e1122b_train_harvester_open"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_e1122b_train_harvester_open")
	public void aHarvesterTrainReapsAnOpenVillage(GameTestHelper helper) {
		actorTrain(helper, Actor.HARVESTER, false, false);
	}

	/** The train entity saved and loaded just outside Frank's protected village: its drill still breaks nothing inside. */
	//$ gametest_ticks_batch AREA '1200' '"qa_e1122b_train_reloaded_protected"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_e1122b_train_reloaded_protected")
	public void aStrangersDrillTrainReloadedOutsideStillBreaksNothingInside(GameTestHelper helper) {
		actorTrain(helper, Actor.DRILL, true, true);
	}

	/** The control: the same reloaded train's drill bores through an open village. */
	//$ gametest_ticks_batch AREA '1200' '"qa_e1122b_train_reloaded_open"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_e1122b_train_reloaded_open")
	public void aDrillTrainReloadedOutsideStillBoresThroughAnOpenVillage(GameTestHelper helper) {
		actorTrain(helper, Actor.DRILL, false, true);
	}
}
