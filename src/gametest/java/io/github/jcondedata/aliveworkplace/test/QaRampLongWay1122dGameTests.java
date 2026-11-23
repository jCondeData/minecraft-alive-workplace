package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Ladders;
import io.github.jcondedata.aliveworkplace.threat.SandRamps;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-d-1122-2208) for B1506, written from the bug's spec: "a sand-ramp climber standing in a pocket (between its
 * finished ramp, a player's pen and the yard wall) whose real walk to the ramp's top is ~45 blocks but which vanilla's
 * search counts as over 64 used to stand there forever; now, when the search to its ramp (top or waiting spot) doesn't
 * reach, it retries once with range 128".
 *
 * <p>The yard is QaRampPens1122dGameTests's (walls 4 high at x 8 and 26, z 1 and 19, the hall at (13, 2, 13), the
 * villager shut in a cell so the husk piles its ramp outside the west wall on the z 13 line, x 4..7, 4 high). The
 * waiting spot (2, 2, 13) is fenced in (x 1 and 3, z 6..20, closed at both ends) and a small fenced pen sits beside the
 * ramp's foot on one side. Once the ramp reaches the top, the husk is put (as a knock-back or a player's push would)
 * into the pocket between the ramp, the side pen and the yard wall: the ramp's sand is right beside it, but the only way
 * onto the ramp's foot is the long way round the waiting spot's pen. Each test asserts what a player sees: the husk
 * never gets into a pen, never through the player's blocks, and climbs the ramp and gets over the wall within the time.
 */
public class QaRampLongWay1122dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation PILERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_ramp_long_way_1122d");

	private static final int WEST = 8;
	private static final int EAST = 26;
	private static final int BACK = 1;
	private static final int FRONT = 19;
	private static final BlockPos HALL = new BlockPos(13, 2, 13);
	private static final int RAMP_Z = 13;
	private static final int TOP = 5;

	private record Yard(ServerLevel level, BlockPos hall, Culture culture, Villager villager) {
	}

	private record Pen(String name, BlockPos from, BlockPos to) {
		AABB box(GameTestHelper helper) {
			return AABB.encapsulatingFullBlocks(helper.absolutePos(from), helper.absolutePos(to));
		}
	}

	private static Culture culture(GameTestHelper helper) {
		Culture culture = Culture.read(PILERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"climber\"}], \"hours\": \"until_noon\", \"tactics\": [\"sand_ramps\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new LinkedHashMap<>(loaded);
		with.put(PILERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> Threats.setForTest(loaded));
		return culture;
	}

	private static Yard yard(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 20;
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Sieges.forget();
			Ladders.forget();
			SandRamps.forget();
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		Culture culture = culture(helper);
		for (int y = 2; y <= 5; y++) {
			for (int x = WEST; x <= EAST; x++) {
				helper.setBlock(new BlockPos(x, y, BACK), Blocks.STONE_BRICKS);
				if (x < 14 || x > 20) {
					helper.setBlock(new BlockPos(x, y, FRONT), Blocks.STONE_BRICKS);
				}
			}
			for (int z = BACK; z <= FRONT; z++) {
				helper.setBlock(new BlockPos(WEST, y, z), Blocks.STONE_BRICKS);
				helper.setBlock(new BlockPos(EAST, y, z), Blocks.STONE_BRICKS);
			}
		}
		BlockPos origin = helper.absolutePos(new BlockPos(14, 2, FRONT - 2));
		level.getStructureManager().get(StarterBlueprints.STONE_WALL.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(StarterBlueprints.STONE_WALL.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		for (int x = 12; x <= 14; x++) {
			for (int z = 9; z <= 11; z++) {
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), x == 13 && z == 10 && y < 4 ? Blocks.AIR : Blocks.STONE_BRICKS);
				}
			}
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(13, 2, 10));
		return new Yard(level, hall, culture, villager);
	}

	private static Mob husk(GameTestHelper helper, Yard yard, double x, double z) {
		ServerLevel level = yard.level();
		Culture.Member member = yard.culture().roster().get(0);
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob != null, "no " + member.entity());
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		mob.moveTo(base.getX() + x, base.getY() + 2, base.getZ() + z, 180f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
		Threats.outfit(level, mob, yard.culture(), member);
		// B1528: finalizeSpawn makes one zombie-kind in 20 a baby (the level's random), now and then on a chicken: a baby
		// walks in through a gap one high or rides off on its chicken, and these tests watch how it gets past the walls. Always an adult.
		if (mob instanceof net.minecraft.world.entity.monster.Zombie zombie && zombie.isBaby()) {
			zombie.setBaby(false);
		}
		net.minecraft.world.entity.Entity vehicle = mob.getVehicle();
		if (vehicle != null) {
			mob.stopRiding();
			vehicle.discard();
			mob.moveTo(base.getX() + x, base.getY() + 2, base.getZ() + z, 180f, 0f);
		}
		mob.setTarget(yard.villager());
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	private static Vec3 rel(GameTestHelper helper, Vec3 pos) {
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		return pos.subtract(base.getX(), base.getY(), base.getZ());
	}

	/** The waiting spot's pen: x 1 and 3, z 6..20 (x 3 runs on to {@code southEnd}), closed at z 6 and z 20, one fence high. */
	private static Pen spotPen(Map<BlockPos, Block> mine, int southEnd) {
		for (int z = 6; z <= 20; z++) {
			mine.put(new BlockPos(1, 2, z), Blocks.OAK_FENCE);
			mine.put(new BlockPos(3, 2, z), Blocks.OAK_FENCE);
		}
		for (int z = 21; z <= southEnd; z++) {
			mine.put(new BlockPos(3, 2, z), Blocks.OAK_FENCE);
		}
		mine.put(new BlockPos(2, 2, 6), Blocks.OAK_FENCE);
		mine.put(new BlockPos(2, 2, 20), Blocks.OAK_FENCE);
		return new Pen("pen round the waiting spot", new BlockPos(2, 2, 7), new BlockPos(2, 3, 19));
	}

	/** The small pen beside the ramp's foot at z {@code RAMP_Z + 2 * dir} (x 4), its near side one block from the sand. */
	private static Pen besidePen(Map<BlockPos, Block> mine, int dir) {
		int near = RAMP_Z + dir;
		int mid = RAMP_Z + 2 * dir;
		int far = RAMP_Z + 3 * dir;
		mine.put(new BlockPos(4, 2, near), Blocks.OAK_FENCE);
		mine.put(new BlockPos(5, 2, near), Blocks.OAK_FENCE);
		mine.put(new BlockPos(5, 2, mid), Blocks.OAK_FENCE);
		mine.put(new BlockPos(5, 2, far), Blocks.OAK_FENCE);
		mine.put(new BlockPos(4, 2, far), Blocks.OAK_FENCE);
		return new Pen("pen beside the ramp at z " + mid, new BlockPos(4, 2, mid), new BlockPos(4, 3, mid));
	}

	private static void build(GameTestHelper helper, Map<BlockPos, Block> mine) {
		mine.forEach(helper::setBlock);
		ServerLevel level = helper.getLevel();
		for (BlockPos p : mine.keySet()) {
			BlockPos at = helper.absolutePos(p);
			level.setBlock(at, Block.updateFromNeighbourShapes(level.getBlockState(at), level, at), 2);
		}
	}

	/**
	 * The run. The husk starts outside the front wall and piles its ramp. The tick the ramp reaches the top, it is put at
	 * {@code pocket} (its navigation stopped, as after a knock-back). {@code reload} > 0: that many ticks later the threats
	 * and the husk are saved and loaded as a restart does. {@code shut} != null: the pocket is closed, so the run passes
	 * when the husk has stayed inside {@code shut} (and never counted over) for {@code within} ticks. Otherwise it passes
	 * when the husk is over the wall and down inside the yard within {@code within} ticks of being put in the pocket.
	 * Every tick: the husk is in no pen of {@code pens}, and the player's blocks stand.
	 */
	private static void run(GameTestHelper helper, String what, Map<BlockPos, Block> mine, List<Pen> pens, Vec3 pocket, int reload, Pen shut, int within, int limit) {
		run(helper, what, mine, Map.of(), pens, pocket, reload, shut, within, limit);
	}

	/** {@code late}: the player's blocks put up the tick the husk is put in the pocket (so they can't change where the ramp goes). */
	private static void run(GameTestHelper helper, String what, Map<BlockPos, Block> mine, Map<BlockPos, Block> late, List<Pen> pens, Vec3 pocket, int reload, Pen shut, int within,
		int limit) {
		Yard yard = yard(helper);
		ServerLevel level = yard.level();
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		build(helper, mine);
		Mob[] husk = {husk(helper, yard, 16.5, 22.5)};
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(husk[0]));
		helper.assertTrue(Sieges.siege(level, yard.hall()).isPresent(), what + ": no siege on the village");
		long[] putAt = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			Mob mob = husk[0];
			Vec3 p = rel(helper, mob.position());
			List<BlockPos> sand = SandRamps.sand(level, yard.hall()).stream().map(s -> s.subtract(base)).toList();
			for (Pen pen : pens) {
				helper.assertFalse(mob.getBoundingBox().intersects(pen.box(helper)), what + ": the husk got into the player's " + pen.name() + " at " + p + " (tick " + tick
					+ ", put in the pocket at " + putAt[0] + ")");
			}
			mine.forEach((at, block) -> helper.assertTrue(helper.getBlockState(at).is(block), what + ": the player's " + block + " at " + at.toShortString() + " is now "
				+ helper.getBlockState(at)));
			boolean over = SandRamps.over(level, yard.hall(), mob.getUUID());
			if (putAt[0] < 0) {
				if (sand.stream().anyMatch(s -> s.getX() == WEST - 1 && s.getY() == TOP && s.getZ() == RAMP_Z)) {
					helper.assertFalse(over, what + ": (setup) the husk was already over when the ramp topped, at " + p);
					BlockPos at = base.offset(0, 0, 0);
					mob.teleportTo(at.getX() + pocket.x, at.getY() + pocket.y, at.getZ() + pocket.z);
					mob.setDeltaMovement(Vec3.ZERO);
					mob.getNavigation().stop();
					build(helper, late);
					mine.putAll(late);
					putAt[0] = tick;
					AliveWorkplace.LOG.info("[qa-ramp-long-way] {}: the ramp topped at tick {} (piled {}), the husk put in the pocket at {}", what, tick,
						sand.stream().map(BlockPos::toShortString).toList(), pocket);
				} else if (tick > limit) {
					helper.fail(what + ": (setup) the ramp never reached the top within " + limit + " ticks, piled " + sand.stream().map(BlockPos::toShortString).toList()
						+ ", the husk at " + p);
				}
				return;
			}
			if (reload > 0 && tick == putAt[0] + reload) {
				ThreatData.get(level).roundTrip(level.registryAccess());
				Sieges.forget();
				Ladders.forget();
				SandRamps.forget();
				CompoundTag tag = new CompoundTag();
				helper.assertTrue(mob.save(tag), what + ": the husk wasn't saved");
				mob.discard();
				Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
				helper.assertTrue(loaded instanceof Mob, what + ": the husk didn't load: " + loaded);
				level.addFreshEntity(loaded);
				husk[0] = (Mob) loaded;
				AliveWorkplace.LOG.info("[qa-ramp-long-way] {}: saved and loaded at tick {}, the husk at {}", what, tick, p);
				return;
			}
			if (shut != null) {
				helper.assertTrue(mob.isAlive(), what + ": the shut-in husk died at " + p + " (tick " + tick + ")");
				helper.assertTrue(shut.box(helper).inflate(0.05).contains(mob.position()), what + ": the husk left its closed " + shut.name() + ": at " + p + " (tick " + tick
					+ ", put there at " + putAt[0] + ")");
				helper.assertFalse(over, what + ": the shut-in husk is counted over the wall at " + p + " (tick " + tick + ")");
				if (tick >= putAt[0] + within) {
					AliveWorkplace.LOG.info("[qa-ramp-long-way] {}: stayed in the closed {} for {} ticks, at {}", what, shut.name(), within, p);
					helper.succeed();
				}
				return;
			}
			if (over && mob.isAlive() && p.x > WEST + 0.5 && p.x < EAST && p.z > BACK + 0.5 && p.z < FRONT - 1.5 && mob.onGround() && p.y < 2.6) {
				AliveWorkplace.LOG.info("[qa-ramp-long-way] {}: over at tick {} ({} ticks after the pocket) at {}", what, tick, tick - putAt[0], p);
				helper.succeed();
				return;
			}
			if (tick > putAt[0] + within) {
				helper.fail(what + ": not over the wall " + within + " ticks after being put in the pocket at " + pocket + ": the husk at " + p + (mob.isAlive() ? "" : " (dead)")
					+ ", counted over " + over + ", navigation done " + mob.getNavigation().isDone());
			}
		});
	}

	/**
	 * The bug's own pocket: the side pen on the ramp's south side, the husk put at (6.5, 2, 15.5) between the ramp, that
	 * pen and the yard wall. Its way onto the ramp's foot runs south out of the pocket, round the waiting spot's pen's
	 * south end, up its west side and back down to the ramp's foot from the north: about 45 blocks over open ground.
	 */
	//$ gametest_ticks_batch AREA '2000' '"qaRampLongWay1122dSouth"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaRampLongWay1122dSouth")
	public void qaAClimberInThePocketSouthOfItsRampWalksTheLongWayRoundAndClimbs(GameTestHelper helper) {
		Map<BlockPos, Block> mine = new LinkedHashMap<>();
		List<Pen> pens = List.of(spotPen(mine, 20), besidePen(mine, 1));
		run(helper, "south pocket, ~45 blocks round", mine, pens, new Vec3(6.5, 2, 15.5), 0, null, 900, 1050);
	}

	/**
	 * The mirror: the side pen on the ramp's north side, the husk put at (6.5, 2, 10.5). Its way runs north out of the
	 * pocket, round the waiting spot's pen's north end, down its west side, round its south end and up to the ramp's foot
	 * from the south: again about 45 blocks.
	 */
	//$ gametest_ticks_batch AREA '2000' '"qaRampLongWay1122dNorth"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaRampLongWay1122dNorth")
	public void qaAClimberInThePocketNorthOfItsRampWalksTheLongWayRoundAndClimbs(GameTestHelper helper) {
		Map<BlockPos, Block> mine = new LinkedHashMap<>();
		List<Pen> pens = List.of(spotPen(mine, 20), besidePen(mine, -1));
		run(helper, "north pocket, ~45 blocks round", mine, pens, new Vec3(6.5, 2, 10.5), 0, null, 900, 1050);
	}

	/**
	 * A longer way: the south pocket with the waiting spot pen's x 3 fence run on to z 27, so the way out of the pocket
	 * goes down to z 28 before it can turn west: about 56 blocks round, still over open ground.
	 */
	//$ gametest_ticks_batch AREA '2200' '"qaRampLongWay1122dLonger"'
	@GameTest(template = AREA, timeoutTicks = 2200, batch = "qaRampLongWay1122dLonger")
	public void qaAClimberWhoseWayRoundIsAboutFiftySixBlocksStillClimbs(GameTestHelper helper) {
		Map<BlockPos, Block> mine = new LinkedHashMap<>();
		List<Pen> pens = List.of(spotPen(mine, 27), besidePen(mine, 1));
		run(helper, "south pocket, ~56 blocks round", mine, pens, new Vec3(6.5, 2, 15.5), 0, null, 1100, 1050);
	}

	/**
	 * The south pocket closed by a fence at z 17 (x 5..7, put up as the husk is put there): no way to the ramp at all. The husk must stay in the pocket,
	 * alive and never counted over (no hop over the pen or the wall), for 600 ticks.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qaRampLongWay1122dShut"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaRampLongWay1122dShut")
	public void qaAClimberShutInThePocketWithNoWayToItsRampStaysThere(GameTestHelper helper) {
		Map<BlockPos, Block> mine = new LinkedHashMap<>();
		List<Pen> pens = List.of(spotPen(mine, 20), besidePen(mine, 1));
		Map<BlockPos, Block> late = new LinkedHashMap<>();
		for (int x = 5; x <= 7; x++) {
			late.put(new BlockPos(x, 2, 17), Blocks.OAK_FENCE);
		}
		Pen shut = new Pen("pocket south of the ramp", new BlockPos(6, 2, 14), new BlockPos(7, 4, 16));
		run(helper, "south pocket shut", mine, late, pens, new Vec3(6.5, 2, 15.5), 0, shut, 600, 1050);
	}

	/** The south pocket, the game saved and loaded 40 ticks after the husk is put there: it still finds the long way and climbs. */
	//$ gametest_ticks_batch AREA '2000' '"qaRampLongWay1122dReload"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaRampLongWay1122dReload")
	public void qaAClimberInThePocketStillFindsTheLongWayAfterASaveAndLoad(GameTestHelper helper) {
		Map<BlockPos, Block> mine = new LinkedHashMap<>();
		List<Pen> pens = List.of(spotPen(mine, 20), besidePen(mine, 1));
		run(helper, "south pocket, saved and loaded", mine, pens, new Vec3(6.5, 2, 15.5), 40, null, 900, 1050);
	}
}
