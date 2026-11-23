package io.github.jcondedata.aliveworkplace.test;

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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-d-1122-1009) for B1445 and B1479 (32.9 sand ramps), written from the spec: "a raider with the role climber
 * whose path to the hall ends at a wall (two to 8 blocks high, not a gate) starts a ramp there"; a climber that can walk
 * in does not ramp; no sand goes onto a raider; a climber waiting by a rising ramp waits, then climbs over. The fix
 * (SandRamps.walledOff) counts a path that ends within five of the hall as not getting in when a column two high stands
 * on the line from its end to the hall, so these tests probe both sides of that: walls right at the five-block edge on
 * every side and diagonally, a two-high wall, and the save and load of a half-built ramp (must ramp); a one-high wall
 * (must not ramp).
 *
 * <p>Each test builds its own walls round a hall, with a finished Stone Wall standing away in the open (a siege comes
 * only to a village with a defense) and the village's one villager in a closed cell. The husk is a climber of a culture
 * of sand pilers, with no target: where it goes is the siege's doing.
 */
public class QaRampWalledOff1122dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation PILERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_ramp_walled_off_1122d");
	/** The hall in the middle of the area; the "edge" yard's walls stand four out from it, so their outside foot is exactly five away. */
	static final BlockPos HALL = new BlockPos(14, 2, 14);
	static final int R = 4;
	/** By when a ramp must stand and the husk be over it. */
	private static final int RAMP_BY = 900;
	/** How long a husk that must not ramp is watched (a climber asks for its path every 40 ticks). */
	private static final int HOLD = 300;

	record Yard(ServerLevel level, BlockPos hall, Culture culture) {
	}

	/** Clears the area, sets a small village radius and a culture of husks piling sand, and puts it all back at the end. */
	static Yard village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 20;
		Culture culture = Culture.read(PILERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"climber\"}], \"hours\": \"until_noon\", \"tactics\": [\"sand_ramps\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new LinkedHashMap<>(loaded);
		with.put(PILERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> {
			Threats.setForTest(loaded);
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
		// The villager in a closed stone cell in the area's corner, inside the village, away from every wall and way.
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(26 + dx, y, 26 + dz), dx == 0 && dz == 0 && y < 4 ? Blocks.AIR : Blocks.STONE_BRICKS);
				}
			}
		}
		helper.spawn(EntityType.VILLAGER, new BlockPos(26, 2, 26));
		// A finished Stone Wall out in the open, in the area's other corner.
		BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 25));
		level.getStructureManager().get(StarterBlueprints.STONE_WALL.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(),
			RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(StarterBlueprints.STONE_WALL.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		return new Yard(level, hall, culture);
	}

	/** Walls of {@code block}, {@code high} blocks high, closed round the hall {@code r} blocks out from it on every side. */
	static void walls(GameTestHelper helper, int r, int high, Block block) {
		int west = HALL.getX() - r;
		int east = HALL.getX() + r;
		int back = HALL.getZ() - r;
		int front = HALL.getZ() + r;
		for (int y = 2; y < 2 + high; y++) {
			for (int x = west; x <= east; x++) {
				helper.setBlock(new BlockPos(x, y, back), block);
				helper.setBlock(new BlockPos(x, y, front), block);
			}
			for (int z = back; z <= front; z++) {
				helper.setBlock(new BlockPos(west, y, z), block);
				helper.setBlock(new BlockPos(east, y, z), block);
			}
		}
	}

	/** A climbing husk of the siege's culture at helper ({@code x}, 2, {@code z}), with no target, and the siege begun with it. */
	static Mob husk(GameTestHelper helper, Yard yard, double x, double z) {
		ServerLevel level = yard.level();
		Culture.Member member = yard.culture().roster().get(0);
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob != null, "no " + member.entity());
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		mob.moveTo(base.getX() + x, base.getY() + 2, base.getZ() + z, 0f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
		Threats.outfit(level, mob, yard.culture(), member);
		// B1528: finalizeSpawn makes one husk in 20 a baby (the level's random), now and then on a chicken. A baby is
		// under a block tall, so it walks in through a gap one high that these tests count as no way in. Always an adult.
		if (mob instanceof net.minecraft.world.entity.monster.Zombie zombie && zombie.isBaby()) {
			zombie.setBaby(false);
		}
		net.minecraft.world.entity.Entity vehicle = mob.getVehicle();
		if (vehicle != null) {
			mob.stopRiding();
			vehicle.discard();
			mob.moveTo(base.getX() + x, base.getY() + 2, base.getZ() + z, 0f, 0f);
		}
		mob.setPersistenceRequired();
		level.addFreshEntityWithPassengers(mob);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(mob));
		helper.assertTrue(Sieges.siege(level, yard.hall()).isPresent(), "no siege on the village");
		return mob;
	}

	private static String where(GameTestHelper helper, Mob mob) {
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		Vec3 p = mob.position().subtract(base.getX(), base.getY(), base.getZ());
		return String.format("(%.2f, %.2f, %.2f)", p.x, p.y, p.z);
	}

	private static List<String> rel(GameTestHelper helper, List<BlockPos> sand) {
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		return sand.stream().map(p -> p.subtract(base).toShortString()).toList();
	}

	/** Fails if a block of the raiders' sand appeared where a raider stands (no sand is piled onto a raider). */
	private static void noSandOnARaider(GameTestHelper helper, Yard yard, Set<BlockPos> seen) {
		for (BlockPos p : SandRamps.sand(yard.level(), yard.hall())) {
			if (seen.add(p)) {
				List<Mob> on = yard.level().getEntitiesOfClass(Mob.class, new AABB(p), m -> m.getTags().contains(VillageRaids.TAG));
				helper.assertTrue(on.isEmpty(), "sand piled at " + p.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString() + " onto the raider at "
					+ (on.isEmpty() ? "" : where(helper, on.get(0))) + " at tick " + helper.getTick());
			}
		}
	}

	/**
	 * The ramp must stand (the siege says ramped) and the husk be counted over it by {@link #RAMP_BY}, never with sand
	 * piled onto it; {@code sandOk} checks every block of sand (relative), {@code each} runs every tick first.
	 */
	private static void expectRampOver(GameTestHelper helper, Yard yard, Mob husk, String what, Predicate<BlockPos> sandOk, String sandRule,
		Consumer<Long> each) {
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		Set<BlockPos> seen = new HashSet<>();
		boolean[] ramped = {false};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (each != null) {
				each.accept(tick);
			}
			noSandOnARaider(helper, yard, seen);
			List<BlockPos> sand = SandRamps.sand(yard.level(), yard.hall());
			for (BlockPos p : sand) {
				helper.assertTrue(sandOk.test(p.subtract(base)), what + ": sand at " + p.subtract(base).toShortString() + " isn't " + sandRule + ": " + rel(helper, sand));
			}
			Optional<ThreatData.Siege> siege = Sieges.siege(yard.level(), yard.hall());
			if (siege.isPresent() && siege.get().ramped) {
				ramped[0] = true;
			}
			if (ramped[0] && SandRamps.over(yard.level(), yard.hall(), husk.getUUID())) {
				helper.succeed();
			} else if (tick > RAMP_BY) {
				helper.fail(what + ": " + (ramped[0] ? "the ramp stands but the husk isn't over it" : "no ramp in time") + " by tick " + tick + ": piled "
					+ rel(helper, sand) + ", the husk at " + where(helper, husk) + (husk.isAlive() ? "" : " (dead)"));
			}
		});
	}

	/** No sand is piled for {@link #HOLD} ticks. */
	static void expectNoRamp(GameTestHelper helper, Yard yard, Mob husk, String what) {
		helper.onEachTick(() -> {
			List<BlockPos> sand = SandRamps.sand(yard.level(), yard.hall());
			helper.assertTrue(sand.isEmpty(), what + ": sand piled by tick " + helper.getTick() + ": " + rel(helper, sand) + ", the husk at " + where(helper, husk));
			if (helper.getTick() >= HOLD) {
				helper.succeed();
			}
		});
	}

	// ---------------------------------------------------------------- At the five-block edge, on each side.

	/** A husk three out from the middle of one side of the edge yard (stone brick walls four high, four out from the hall). */
	private static void edgeSide(GameTestHelper helper, double x, double z, String side, Predicate<BlockPos> sandOk) {
		Yard yard = village(helper);
		walls(helper, R, 4, Blocks.STONE_BRICKS);
		Mob husk = husk(helper, yard, x, z);
		expectRampOver(helper, yard, husk, "from outside the " + side + " wall", sandOk, "outside the " + side + " wall", null);
	}

	/**
	 * B1445 on the north side: the husk three blocks north of the north wall, whose outside foot (14, 2, 9) is exactly five
	 * from the hall along the axes, so the path "reaches"; the wall is in the way, so a ramp goes up against it outside,
	 * and the husk climbs over.
	 */
	//$ gametest_ticks_batch AREA '1000' '"qaWalledOff1122dNorth"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaWalledOff1122dNorth")
	public void qaAClimberAtTheNorthWallFiveFromTheHallRampsOver(GameTestHelper helper) {
		edgeSide(helper, 14.5, 6.5, "north", p -> p.getZ() < HALL.getZ() - R);
	}

	/** The same at the south wall. */
	//$ gametest_ticks_batch AREA '1000' '"qaWalledOff1122dSouth"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaWalledOff1122dSouth")
	public void qaAClimberAtTheSouthWallFiveFromTheHallRampsOver(GameTestHelper helper) {
		edgeSide(helper, 14.5, 22.5, "south", p -> p.getZ() > HALL.getZ() + R);
	}

	/** The same at the west wall. */
	//$ gametest_ticks_batch AREA '1000' '"qaWalledOff1122dWest"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaWalledOff1122dWest")
	public void qaAClimberAtTheWestWallFiveFromTheHallRampsOver(GameTestHelper helper) {
		edgeSide(helper, 6.5, 14.5, "west", p -> p.getX() < HALL.getX() - R);
	}

	/** The same at the east wall. */
	//$ gametest_ticks_batch AREA '1000' '"qaWalledOff1122dEast"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaWalledOff1122dEast")
	public void qaAClimberAtTheEastWallFiveFromTheHallRampsOver(GameTestHelper helper) {
		edgeSide(helper, 22.5, 14.5, "east", p -> p.getX() > HALL.getX() + R);
	}

	/**
	 * From the yard's outside corner, off the axes: the walls three out from the hall, so the nearest the path gets outside
	 * is beside the middle of a side, a block off it (five along the axes), and the line from there to the hall runs
	 * slantwise through the wall. A ramp still goes up outside the walls and the husk gets over.
	 */
	//$ gametest_ticks_batch AREA '1000' '"qaWalledOff1122dCorner"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaWalledOff1122dCorner")
	public void qaAClimberAtTheYardsCornerRampsOverASideWall(GameTestHelper helper) {
		Yard yard = village(helper);
		walls(helper, 3, 4, Blocks.STONE_BRICKS);
		Mob husk = husk(helper, yard, 8.5, 8.5);
		int in = 3;
		expectRampOver(helper, yard, husk, "from the north-west corner", p -> p.getX() < HALL.getX() - in || p.getX() > HALL.getX() + in
			|| p.getZ() < HALL.getZ() - in || p.getZ() > HALL.getZ() + in, "outside the walls", null);
	}

	// ---------------------------------------------------------------- Wall height.

	/** A wall only two high (the lowest the spec ramps), its foot exactly five from the hall: a ramp two high, and over. */
	//$ gametest_ticks_batch AREA '1000' '"qaWalledOff1122dTwoHigh"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaWalledOff1122dTwoHigh")
	public void qaATwoHighWallAtTheFiveBlockEdgeGetsARamp(GameTestHelper helper) {
		Yard yard = village(helper);
		walls(helper, R, 2, Blocks.STONE_BRICKS);
		Mob husk = husk(helper, yard, 14.5, 6.5);
		expectRampOver(helper, yard, husk, "two-high north wall", p -> p.getZ() < HALL.getZ() - R && p.getY() <= 3, "outside the wall, at most two high", null);
	}

	/** A wall one high, which a husk jumps: it is no wall (the spec's walls are two to 8 high), so no sand is piled. */
	//$ gametest_ticks_batch AREA '400' '"qaWalledOff1122dOneHigh"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaWalledOff1122dOneHigh")
	public void qaAOneHighWallGetsNoRamp(GameTestHelper helper) {
		Yard yard = village(helper);
		walls(helper, R, 1, Blocks.STONE_BRICKS);
		Mob husk = husk(helper, yard, 14.5, 6.5);
		expectNoRamp(helper, yard, husk, "one-high wall");
	}

	// ---------------------------------------------------------------- The husk in the way; save and load.

	/**
	 * The husk starts standing on the ramp's first block, right at the north wall's foot (14.5, 2, 9.5): no sand goes onto
	 * it, yet the ramp still begins (it is sent off the spot), stands, and the husk gets over.
	 */
	//$ gametest_ticks_batch AREA '1000' '"qaWalledOff1122dOnFoot"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaWalledOff1122dOnFoot")
	public void qaAClimberStandingOnTheRampsFootIsNotBuriedAndStillRampsOver(GameTestHelper helper) {
		Yard yard = village(helper);
		walls(helper, R, 4, Blocks.STONE_BRICKS);
		Mob husk = husk(helper, yard, 14.5, 9.5);
		expectRampOver(helper, yard, husk, "starting on the foot", p -> p.getZ() < HALL.getZ() - R, "outside the north wall", null);
	}

	/**
	 * The siege is saved and loaded (the ramps' memory forgotten, as a restart does) once three blocks of the ramp stand:
	 * the blocks piled before stay recorded, and the ramp still goes up to the top and the husk over it.
	 */
	//$ gametest_ticks_batch AREA '1000' '"qaWalledOff1122dReload"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaWalledOff1122dReload")
	public void qaAHalfBuiltRampSavedAndLoadedStillRisesAndTheHuskGetsOver(GameTestHelper helper) {
		Yard yard = village(helper);
		walls(helper, R, 4, Blocks.STONE_BRICKS);
		Mob husk = husk(helper, yard, 14.5, 6.5);
		List<BlockPos>[] before = new List[] {null};
		expectRampOver(helper, yard, husk, "after a save and load", p -> p.getZ() < HALL.getZ() - R, "outside the north wall", tick -> {
			List<BlockPos> sand = SandRamps.sand(yard.level(), yard.hall());
			if (before[0] == null && sand.size() >= 3) {
				before[0] = List.copyOf(sand);
				ThreatData.get(yard.level()).roundTrip(yard.level().registryAccess());
				SandRamps.forget();
				Sieges.forget();
				List<BlockPos> after = SandRamps.sand(yard.level(), yard.hall());
				helper.assertTrue(after.containsAll(before[0]), "the sand piled before the save isn't recorded after it: before " + rel(helper, before[0])
					+ ", after " + rel(helper, after));
			}
			if (tick > 600 && before[0] == null) {
				helper.fail("no three blocks of sand by tick 600 to save the siege with: " + rel(helper, sand));
			}
		});
	}
}
