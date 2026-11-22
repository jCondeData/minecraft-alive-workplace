package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreatePortableTrains1122eCompatTests.STATION_A;
import static io.github.jcondedata.aliveworkplace.compat.QaCreatePortableTrains1122eCompatTests.STATION_B;
import static io.github.jcondedata.aliveworkplace.compat.QaCreatePortableTrains1122eCompatTests.TRAIN_HALL;

import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane E, lane-e-1122-1613): village protection against a Create Deployer in PUNCH mode on a stranger's real Create
 * train running into Frank's protected village. The promise (VillageProtection's javadoc, StandIns): inside a protected
 * village only the owner, friends and operators may break blocks or hurt the villagers and animals, and a stranger's
 * machine is a stranger. (The earlier train tests covered a deployer placing blocks; the stationary punch cases are
 * QaCreateDeployerUses1122eCompatTests.)
 *
 * <p>The set-up is QaCreateTrainPloughRoller1122eCompatTests': a stranger's glued train with a conductor, assembled at the
 * station at x = 6 and scheduled to x = 23, runs along the track at z = 12 past Frank's hall (22, 2, 4, reach 8). A
 * deployer the stranger placed (its Owner), switched to punch mode (DeployerBlockEntity.changeMode, a wrench's click) and
 * handed an iron sword or an iron pickaxe, is glued to the train's north side facing north; on a contraption it acts two
 * blocks ahead of itself, so its lane is two blocks north of it. In that lane: with the sword, a cow outside the village
 * (x = 13, calibration) and a villager and a cow inside (no AI, so they stay put); with the pickaxe, stone outside
 * (x = 12..14) and inside. Each protected case has a control with the village open.
 */
public class QaCreateTrainPunch1122eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final int TRAIN_REACH = 8;
	private static final int TIMEOUT = 1400;
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");

	enum Tool { SWORD, PICKAXE }

	static boolean inTrainVillage(BlockPos pos) {
		long dx = pos.getX() - TRAIN_HALL.getX();
		long dz = pos.getZ() - TRAIN_HALL.getZ();
		return dx * dx + dz * dz <= (long) TRAIN_REACH * TRAIN_REACH;
	}

	private static String mode(ServerLevel level, BlockPos abs) {
		BlockEntity be = level.getBlockEntity(abs);
		return be == null ? "none" : Nbt.getString(be.saveWithoutMetadata(level.registryAccess()), "Mode");
	}

	private static <T extends Mob> T still(GameTestHelper helper, EntityType<T> type, BlockPos at) {
		helper.setBlock(at.below(), Blocks.STONE);
		helper.setBlock(at, Blocks.AIR);
		helper.setBlock(at.above(), Blocks.AIR);
		T mob = helper.spawn(type, at);
		mob.setNoAi(true);
		mob.setPersistenceRequired();
		return mob;
	}

	private static boolean hurt(Mob mob) {
		return !mob.isAlive() || mob.getHealth() < mob.getMaxHealth();
	}

	private static String health(Mob mob) {
		return mob.getType().getDescription().getString() + " " + mob.getHealth() + "/" + mob.getMaxHealth() + (mob.isAlive() ? "" : " (dead)");
	}

	static void punchTrain(GameTestHelper helper, Tool tool, boolean protect) {
		ServerLevel level = helper.getLevel();
		int radiusBefore = VillageHalls.RADIUS;
		boolean enabledBefore = VillageProtection.ENABLED;
		PartnerShowsCompatTests.clearLeftovers(helper);
		CreateTrainCompatTests.line(helper);
		VillageHalls.RADIUS = TRAIN_REACH;
		VillageProtection.ENABLED = true;
		helper.setBlock(TRAIN_HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(TRAIN_HALL);
		hall.setOwner(UUID.fromString("5b1d2c3e-4f50-4a6b-8c7d-9e0f1a2b3c4d"), "Frank");
		hall.setProtected(protect);
		helper.onEachTick(() -> {
			if (helper.getTick() >= TIMEOUT - 5 && level.getBlockEntity(helper.absolutePos(TRAIN_HALL)) instanceof VillageHallBlockEntity h) {
				h.setProtected(false);
				VillageHalls.RADIUS = radiusBefore;
				VillageProtection.ENABLED = enabledBefore;
			}
		});
		BlockPos a = helper.absolutePos(STATION_A);
		BlockPos b = helper.absolutePos(STATION_B);
		Villager driver = CreateTrainCompatTests.conductor(helper, new BlockPos(6, 2, 16));
		UUID stranger = UUID.randomUUID();
		String tag = stranger.toString().substring(0, 6);
		List<Mob> insideMobs = new ArrayList<>();
		List<Mob> outsideMobs = new ArrayList<>();
		List<BlockPos> insideStone = new ArrayList<>();
		List<BlockPos> outsideStone = new ArrayList<>();
		int[] lane = {0, 0};
		int[] maxX = {Integer.MIN_VALUE};
		int[] phase = {0};
		int[] tick = {0};
		String[] setup = {""};
		Runnable finish = () -> {
			if (level.getBlockEntity(helper.absolutePos(TRAIN_HALL)) instanceof VillageHallBlockEntity h) {
				h.setProtected(false);
			}
			VillageHalls.RADIUS = radiusBefore;
			VillageProtection.ENABLED = enabledBefore;
			CreateTrainCompatTests.railway().scrap(level, stranger);
			for (SuperGlueEntity g : level.getEntitiesOfClass(SuperGlueEntity.class, helper.getBounds().inflate(4))) {
				g.discard();
			}
			driver.discard();
		};
		helper.onEachTick(() -> {
			tick[0]++;
			CaravanTrains.Railway r = CreateTrainCompatTests.railway();
			if (phase[0] == 0) {
				boolean aimed = r.aim(level, a, b, "QaPunA" + tag) & r.aim(level, b, a, "QaPunB" + tag);
				if (!aimed || !r.linked(level, a, b)) {
					if (tick[0] > 300) {
						phase[0] = 2;
						finish.run();
						helper.fail("set-up: Create's railway doesn't know both stations on one network after 300 ticks (aimed " + aimed + ")");
					}
					return;
				}
				Map<BlockPos, BlockState> parts = r.train(level, a, b);
				if (parts == null) {
					phase[0] = 2;
					finish.run();
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
				level.setBlockAndUpdate(machine, MechanicCompatTests.with(MechanicCompatTests.create("deployer").defaultBlockState(), "facing", "north"));
				if (!(level.getBlockEntity(machine) instanceof DeployerBlockEntity dep)) {
					phase[0] = 2;
					finish.run();
					helper.fail("set-up: no deployer block entity at " + machine);
					return;
				}
				CompoundTag t = dep.saveWithoutMetadata(level.registryAccess());
				t.putUUID("Owner", stranger);
				dep.loadWithComponents(t, level.registryAccess());
				ItemStack held = new ItemStack(tool == Tool.SWORD ? Items.IRON_SWORD : Items.IRON_PICKAXE);
				long in = QaCreateDeployerOnOurThings1102eCompatTests.insert(level, machine, held);
				if (!"PUNCH".equalsIgnoreCase(mode(level, machine))) {
					dep.changeMode();
				}
				String m = mode(level, machine);
				if (in != 1 || !"PUNCH".equalsIgnoreCase(m)) {
					phase[0] = 2;
					finish.run();
					helper.fail("set-up: the deployer took " + in + " " + held + " and is in mode " + m);
					return;
				}
				BlockPos origin = helper.absolutePos(BlockPos.ZERO);
				int py = machine.getY() - origin.getY();
				int pz = machine.getZ() - origin.getZ() - 2; // a contraption's deployer acts two blocks ahead
				int mx = machine.getX() - origin.getX();
				lane[0] = py;
				lane[1] = pz;
				List<BlockPos> inLane = new ArrayList<>();
				for (int x = 18; x <= 23; x++) {
					BlockPos p = new BlockPos(x, py, pz);
					if (inTrainVillage(p)) {
						inLane.add(p);
					}
				}
				if (inLane.size() < 3 || mx >= 11 || inTrainVillage(new BlockPos(14, py, pz))) {
					phase[0] = 2;
					finish.run();
					helper.fail("set-up: the targets aren't where the test wants them (machine at " + machine.subtract(origin).toShortString()
						+ ", lane cells inside " + inLane + ")");
					return;
				}
				if (tool == Tool.SWORD) {
					outsideMobs.add(still(helper, EntityType.COW, new BlockPos(13, py, pz)));
					insideMobs.add(still(helper, EntityType.VILLAGER, inLane.get(0)));
					insideMobs.add(still(helper, EntityType.COW, inLane.get(2)));
				} else {
					for (int x = 12; x <= 14; x++) {
						BlockPos p = new BlockPos(x, py, pz);
						helper.setBlock(p, Blocks.STONE);
						outsideStone.add(p);
					}
					for (BlockPos p : inLane) {
						helper.setBlock(p, Blocks.STONE);
						insideStone.add(p);
					}
				}
				setup[0] = "machine " + machine.subtract(origin).toShortString() + ", lane y " + py + " z " + pz;
				BoundingBox box = BoundingBox.encapsulatingPositions(parts.keySet()).orElseThrow();
				level.addFreshEntity(new SuperGlueEntity(level, AABB.encapsulatingFullBlocks(
					new BlockPos(box.minX(), box.minY(), Math.min(box.minZ(), machine.getZ())), new BlockPos(box.maxX(), box.maxY(), box.maxZ()))));
				if (protect && VillageProtection.mayMachineChange(level, a, helper.absolutePos(inLane.get(0)))) {
					phase[0] = 2;
					finish.run();
					helper.fail("set-up: the village isn't protected (a machine at the first station may change " + inLane.get(0).toShortString() + ")");
					return;
				}
				if (r.assemble(level, a, b, stranger, driver) == null) {
					phase[0] = 2;
					finish.run();
					helper.fail("set-up: the station didn't assemble the stranger's train with its punching deployer");
					return;
				}
				phase[0] = 1;
				return;
			}
			if (phase[0] != 1) {
				return;
			}
			for (Entity e : level.getEntitiesOfClass(CarriageContraptionEntity.class, helper.getBounds().inflate(8))) {
				maxX[0] = Math.max(maxX[0], e.blockPosition().getX() - helper.absolutePos(BlockPos.ZERO).getX());
			}
			boolean arrived = r.at(level, stranger, b);
			if (!arrived && tick[0] < TIMEOUT - 100) {
				return;
			}
			phase[0] = 2;
			String status = r.status(level, stranger);
			int outWorked = 0;
			int changed = 0;
			StringBuilder outSeen = new StringBuilder();
			StringBuilder seen = new StringBuilder();
			if (tool == Tool.SWORD) {
				for (Mob mob : outsideMobs) {
					outWorked += hurt(mob) ? 1 : 0;
					outSeen.append(health(mob)).append("; ");
				}
				for (Mob mob : insideMobs) {
					changed += hurt(mob) ? 1 : 0;
					seen.append(health(mob)).append(" at x ").append(mob.blockPosition().getX() - helper.absolutePos(BlockPos.ZERO).getX()).append("; ");
				}
			} else {
				for (BlockPos p : outsideStone) {
					boolean gone = !helper.getBlockState(p).is(Blocks.STONE);
					outWorked += gone ? 1 : 0;
					outSeen.append(p.getX()).append(gone ? ":broken " : ":kept ");
				}
				for (BlockPos p : insideStone) {
					boolean gone = !helper.getBlockState(p).is(Blocks.STONE);
					changed += gone ? 1 : 0;
					seen.append(p.getX()).append(gone ? ":broken " : ":kept ");
				}
			}
			boolean stillProtected = helper.getBlockEntity(TRAIN_HALL) instanceof VillageHallBlockEntity h && h.isProtected();
			finish.run();
			LOG.info("QA train punch {}: protect={}: inside {} ({}), outside {} ({}), {}, front-most x {}, arrived {}",
				tool, protect, changed, seen, outWorked, outSeen, setup[0], maxX[0], arrived);
			String what = tool == Tool.SWORD ? "with an iron sword hurt" : "with an iron pickaxe broke";
			int total = tool == Tool.SWORD ? insideMobs.size() : insideStone.size();
			helper.assertTrue(arrived, "set-up: the stranger's train didn't reach the second station (front-most x " + maxX[0] + "): " + status);
			helper.assertTrue(outWorked > 0, "set-up: the train's punching deployer did nothing outside the village (" + setup[0] + "): " + outSeen);
			if (protect) {
				helper.assertTrue(stillProtected, "Frank's hall isn't protected any more");
				helper.assertTrue(changed == 0, "a stranger's Create train (assembled outside Frank's protected village) ran into it and its Deployer in"
					+ " punch mode " + what + " " + changed + " of " + total + " in it (outside it: " + outSeen + "): " + seen);
			} else {
				helper.assertTrue(changed == total, "the control: in an open village the train's punching deployer " + what + " only " + changed
					+ " of " + total + " (" + setup[0]
					+ ", outside: " + outSeen + "): " + seen);
			}
			helper.succeed();
		});
	}

	/** A punching deployer with a sword on a stranger's train hurts no villager or cow beside its lane in a protected village. */
	//$ gametest_ticks_batch AREA '1400' '"qa_e1122d_train_punch_sword_protected"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qa_e1122d_train_punch_sword_protected")
	public void aStrangersPunchingTrainHurtsNoVillagerOrCowInAProtectedVillage(GameTestHelper helper) {
		punchTrain(helper, Tool.SWORD, true);
	}

	/** The control: the same train hurts them in an open village. */
	//$ gametest_ticks_batch AREA '1400' '"qa_e1122d_train_punch_sword_open"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qa_e1122d_train_punch_sword_open")
	public void aPunchingTrainHurtsMobsInAnOpenVillage(GameTestHelper helper) {
		punchTrain(helper, Tool.SWORD, false);
	}

	/** A punching deployer with a pickaxe on a stranger's train breaks no stone in its path in a protected village. */
	//$ gametest_ticks_batch AREA '1400' '"qa_e1122d_train_punch_pick_protected"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qa_e1122d_train_punch_pick_protected")
	public void aStrangersPunchingTrainBreaksNoStoneInAProtectedVillage(GameTestHelper helper) {
		punchTrain(helper, Tool.PICKAXE, true);
	}

	/** The control: the same train breaks stone in an open village. */
	//$ gametest_ticks_batch AREA '1400' '"qa_e1122d_train_punch_pick_open"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qa_e1122d_train_punch_pick_open")
	public void aPunchingTrainBreaksStoneInAnOpenVillage(GameTestHelper helper) {
		punchTrain(helper, Tool.PICKAXE, false);
	}
}
