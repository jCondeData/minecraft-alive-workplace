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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (lane D, 2026-11-03) for the caps and the sharing of siege ladders (ROADMAP 32.5, threat/Ladders: "A ladder
 * already within 8 blocks of that spot is used ... At most 3 ladders a siege") and sand ramps (32.9, threat/SandRamps:
 * "At most 2 ramps a siege"). A walled yard three blocks high round the hall, and outside it pens of stone bricks
 * (four high, open on top) against the yard's wall, one climber shut in each: a climber's path to the hall can only
 * end in its own pen, straight out from the yard's wall, so each one's spot at the wall is known. Found by a local
 * mutation run (the caps' {@code >=} turned {@code >}, and the sharing's {@code ||} turned {@code &&} or {@code <=}
 * turned {@code <}, survived the threat tests). Also: climbers get over ladders on two sides, ramps on every side, a
 * ramp starts in a player's sand at the wall's foot, the wall is found past a strip of powder snow (no raider walks
 * on it, so the path ends short of the wall) and past a one-high curb, and a ladder siege whose raiders are all gone
 * goes on (the path cursor's {@code n > 0} turned {@code n >= 0} crashed the server).
 */
public class QaSiegeCaps1103dGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation CLIMBERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_caps_climbers");
	private static final ResourceLocation PILERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_caps_pilers");
	private static final BlockPos HALL = new BlockPos(14, 2, 14);
	/** The yard's wall height (a ladder of 3 rungs, a ramp 3 high and 3 long). */
	private static final int HIGH = 3;

	private record Yard(ServerLevel level, BlockPos hall, Culture culture, Villager villager) {
	}

	/** The test culture (loaded for the test only): pillager climbers who set ladders, or husk climbers who pile sand. */
	private static Culture culture(GameTestHelper helper, boolean sand) {
		Culture culture = sand
			? Culture.read(PILERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
				+ "{\"entity\": \"minecraft:husk\", \"share\": 60, \"role\": \"climber\"}, {\"entity\": \"minecraft:husk\", \"share\": 40, \"role\": \"melee\"}],"
				+ " \"hours\": \"until_noon\", \"tactics\": [\"sand_ramps\"]}").getAsJsonObject())
			: Culture.read(CLIMBERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
				+ "{\"entity\": \"minecraft:pillager\", \"share\": 60, \"role\": \"climber\"}, {\"entity\": \"minecraft:vindicator\", \"share\": 40, \"role\": \"melee\"}],"
				+ " \"tactics\": [\"ladders\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new LinkedHashMap<>(loaded);
		with.put(culture.id(), culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> Threats.setForTest(loaded));
		return culture;
	}

	/** A column of stone bricks {@code high} tall over every block of the rectangle (x0, z0)-(x1, z1), from y = 2. */
	private static void wall(GameTestHelper helper, int x0, int z0, int x1, int z1, int high) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int y = 2; y < 2 + high; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE_BRICKS);
				}
			}
		}
	}

	/**
	 * The yard: walls {@link #HIGH} high from x {@code west} to {@code east}, z 9 to 19; the finished Stone Wall the siege
	 * needs inside; a villager in a closed cell inside.
	 */
	private static Yard yard(GameTestHelper helper, boolean sand, int west, int east) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		VillageHalls.RADIUS = 20;
		level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
		BlockPos hall = helper.absolutePos(HALL);
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
			level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer());
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		wall(helper, west, 9, east, 9, HIGH);
		wall(helper, west, 19, east, 19, HIGH);
		wall(helper, west, 9, west, 19, HIGH);
		wall(helper, east, 9, east, 19, HIGH);
		// The village's finished wall build (a siege needs one), inside the yard.
		BlockPos origin = helper.absolutePos(new BlockPos(11, 2, 15));
		level.getStructureManager().get(StarterBlueprints.STONE_WALL.id()).orElseThrow().placeInWorld(level, origin, origin,
			new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(StarterBlueprints.STONE_WALL.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		for (int x = 10; x <= 12; x++) {
			for (int z = 10; z <= 12; z++) {
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), x == 11 && z == 11 && y < 4 ? Blocks.AIR : Blocks.STONE_BRICKS);
				}
			}
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(11, 2, 11));
		return new Yard(level, hall, culture(helper, sand), villager);
	}

	/** Pens against all four sides of a yard from x 9 to 19, three wide, six deep, each straight out from the hall. */
	private static void fourPens(GameTestHelper helper) {
		// south: inside x 13..15, z 20..25
		wall(helper, 12, 20, 12, 26, 4);
		wall(helper, 16, 20, 16, 26, 4);
		wall(helper, 12, 26, 16, 26, 4);
		// north: inside x 13..15, z 3..8
		wall(helper, 12, 2, 12, 8, 4);
		wall(helper, 16, 2, 16, 8, 4);
		wall(helper, 12, 2, 16, 2, 4);
		// west: inside x 3..8, z 13..15
		wall(helper, 2, 12, 8, 12, 4);
		wall(helper, 2, 16, 8, 16, 4);
		wall(helper, 2, 12, 2, 16, 4);
		// east: inside x 20..25, z 13..15
		wall(helper, 20, 12, 26, 12, 4);
		wall(helper, 20, 16, 26, 16, 4);
		wall(helper, 26, 12, 26, 16, 4);
	}

	/** A pen against the south wall, inside x {@code x0} to {@code x0 + 2}, z 20 to 25. */
	private static void southPen(GameTestHelper helper, int x0) {
		wall(helper, x0 - 1, 20, x0 - 1, 26, 4);
		wall(helper, x0 + 3, 20, x0 + 3, 26, 4);
		wall(helper, x0 - 1, 26, x0 + 3, 26, 4);
	}

	/** Climbers at the given (relative) spots, raiders of one raid on the yard's village. */
	private static List<Mob> climbers(GameTestHelper helper, Yard yard, double[][] at) {
		ServerLevel level = yard.level();
		Culture.Member member = yard.culture().roster().stream().filter(m -> m.role() == Culture.Role.CLIMBER).findFirst().orElseThrow();
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		List<Mob> mobs = new ArrayList<>();
		for (double[] p : at) {
			Mob mob = Threats.create(level, member);
			helper.assertTrue(mob != null, "no " + member.entity());
			mob.moveTo(base.getX() + p[0], base.getY() + 2, base.getZ() + p[1], 0f, 0f);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
			if (mob instanceof Zombie zombie) {
				zombie.setBaby(false);
			}
			if (mob.getVehicle() != null) {
				net.minecraft.world.entity.Entity vehicle = mob.getVehicle();
				mob.stopRiding();
				vehicle.discard();
			}
			mob.getRandom().setSeed(1L);
			Threats.outfit(level, mob, yard.culture(), member);
			mob.setTarget(yard.villager());
			level.addFreshEntityWithPassengers(mob);
			mobs.add(mob);
		}
		VillageRaids.track(level, yard.hall(), yard.culture(), mobs);
		return mobs;
	}

	/** The middles of the four pens of {@link #fourPens}. */
	private static final double[][] FOUR = {{14.5, 23.5}, {14.5, 4.5}, {4.5, 14.5}, {24.5, 14.5}};

	/** The ladders the siege's rungs make: one for each column, with its rung count. */
	private static Map<Long, Integer> ladders(ThreatData.Siege siege) {
		Map<Long, Integer> columns = new LinkedHashMap<>();
		for (BlockPos pos : siege.ladders) {
			columns.merge(BlockPos.asLong(pos.getX(), 0, pos.getZ()), 1, Integer::sum);
		}
		return columns;
	}

	private static String rel(GameTestHelper helper, long column) {
		BlockPos p = BlockPos.of(column);
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		return "(" + (p.getX() - base.getX()) + ", " + (p.getZ() - base.getZ()) + ")";
	}

	private static String where(GameTestHelper helper, Map<Long, Integer> columns) {
		List<String> out = new ArrayList<>();
		columns.forEach((c, n) -> out.add(rel(helper, c) + " x" + n));
		return out.toString();
	}

	/**
	 * Four pillager climbers, one in a pen against each side of the yard, every spot more than 8 from the others: three
	 * ladders go up, never a fourth ("At most 3 ladders a siege").
	 */
	//$ gametest_ticks_batch AREA '700' '"qaLadderCap1103d"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaLadderCap1103d")
	public void noMoreThanThreeLaddersInASiege(GameTestHelper helper) {
		Yard yard = yard(helper, false, 9, 19);
		fourPens(helper);
		List<Mob> mobs = climbers(helper, yard, FOUR);
		ServerLevel level = yard.level();
		helper.onEachTick(() -> {
			Optional<ThreatData.Siege> siege = Sieges.siege(level, yard.hall());
			if (siege.isEmpty()) {
				return;
			}
			Map<Long, Integer> columns = ladders(siege.get());
			helper.assertTrue(columns.size() <= Ladders.MAX_LADDERS, columns.size() + " ladders in one siege, more than " + Ladders.MAX_LADDERS + ": "
				+ where(helper, columns));
			if (helper.getTick() >= 400) {
				helper.assertTrue(columns.size() == Ladders.MAX_LADDERS, "not " + Ladders.MAX_LADDERS + " ladders with four climbers far apart (the test would"
					+ " prove nothing): " + where(helper, columns) + ", climbers alive " + mobs.stream().filter(Mob::isAlive).count());
				helper.succeed();
			}
		});
	}

	/** Four husk climbers, one in a pen against each side of the yard: two ramps are begun, never a third ("At most 2 ramps a siege"). */
	//$ gametest_ticks_batch AREA '700' '"qaRampCap1103d"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaRampCap1103d")
	public void noMoreThanTwoRampsInASiege(GameTestHelper helper) {
		Yard yard = yard(helper, true, 9, 19);
		fourPens(helper);
		List<Mob> mobs = climbers(helper, yard, FOUR);
		ServerLevel level = yard.level();
		helper.onEachTick(() -> {
			Optional<ThreatData.Siege> siege = Sieges.siege(level, yard.hall());
			if (siege.isEmpty()) {
				return;
			}
			// the ramps the sand makes: the pens it lies in (each pen's own ramp)
			Set<String> pens = new HashSet<>();
			BlockPos base = helper.absolutePos(BlockPos.ZERO);
			for (BlockPos pos : siege.get().sand) {
				int x = pos.getX() - base.getX();
				int z = pos.getZ() - base.getZ();
				pens.add(z >= 20 ? "south" : z <= 8 ? "north" : x <= 8 ? "west" : x >= 20 ? "east" : "inside " + x + "," + z);
			}
			helper.assertTrue(siege.get().rampFeet.size() <= SandRamps.MAX_RAMPS && pens.size() <= SandRamps.MAX_RAMPS, "more than " + SandRamps.MAX_RAMPS
				+ " ramps in one siege: feet " + siege.get().rampFeet.size() + ", sand by the " + pens);
			if (helper.getTick() >= 400) {
				helper.assertTrue(pens.size() == SandRamps.MAX_RAMPS, "not " + SandRamps.MAX_RAMPS + " ramps with four climbers far apart (the test would"
					+ " prove nothing): sand by the " + pens + ", husks alive " + mobs.stream().filter(Mob::isAlive).count());
				helper.succeed();
			}
		});
	}

	/**
	 * Two pillager climbers in pens against the south wall whose spots are exactly 8 blocks apart: the second uses the
	 * first one's ladder ("A ladder already within 8 blocks of that spot is used"), so one ladder goes up, never two.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaLadderShare8_1103d"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaLadderShare8_1103d")
	public void climbersEightApartShareOneLadder(GameTestHelper helper) {
		Yard yard = yard(helper, false, 6, 22);
		southPen(helper, 8); // its spot (10, 20)
		southPen(helper, 18); // its spot (18, 20)
		List<Mob> mobs = climbers(helper, yard, new double[][] {{9.5, 23.5}, {19.5, 23.5}});
		ServerLevel level = yard.level();
		helper.onEachTick(() -> {
			Optional<ThreatData.Siege> siege = Sieges.siege(level, yard.hall());
			if (siege.isEmpty()) {
				return;
			}
			Map<Long, Integer> columns = ladders(siege.get());
			helper.assertTrue(columns.size() <= 1, "two climbers 8 apart set " + columns.size() + " ladders: " + where(helper, columns));
			if (helper.getTick() >= 400) {
				helper.assertTrue(columns.size() == 1, "no ladder at all (the test would prove nothing): climbers alive " + mobs.stream().filter(Mob::isAlive).count());
				helper.succeed();
			}
		});
	}

	/** The same with the spots 9 blocks apart: each climber sets its own ladder. */
	//$ gametest_ticks_batch AREA '600' '"qaLadderShare9_1103d"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaLadderShare9_1103d")
	public void climbersNineApartSetALadderEach(GameTestHelper helper) {
		Yard yard = yard(helper, false, 6, 22);
		southPen(helper, 8); // its spot (10, 20)
		southPen(helper, 19); // its spot (19, 20)
		List<Mob> mobs = climbers(helper, yard, new double[][] {{9.5, 23.5}, {20.5, 23.5}});
		ServerLevel level = yard.level();
		helper.onEachTick(() -> {
			Optional<ThreatData.Siege> siege = Sieges.siege(level, yard.hall());
			if (siege.isPresent() && ladders(siege.get()).size() == 2) {
				helper.succeed();
				return;
			}
			if (helper.getTick() >= 500) {
				helper.fail("not a ladder each for two climbers 9 apart: " + siege.map(s -> where(helper, ladders(s))).orElse("no siege")
					+ ", climbers alive " + mobs.stream().filter(Mob::isAlive).count());
			}
		});
	}

	/**
	 * The only raider of a ladder siege is gone (killed, or wandered off) while the raid is still under way: the ladders
	 * go on ticking without it, the rungs set stay recorded, and nothing breaks.
	 */
	//$ gametest_ticks_batch AREA '700' '"qaLadderNoRaiders1103d"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaLadderNoRaiders1103d")
	public void aLadderSiegeWithNoRaidersLeftGoesOn(GameTestHelper helper) {
		Yard yard = yard(helper, false, 9, 19);
		fourPens(helper);
		Mob climber = climbers(helper, yard, new double[][] {FOUR[0]}).get(0);
		ServerLevel level = yard.level();
		long[] gone = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			Optional<ThreatData.Siege> siege = Sieges.siege(level, yard.hall());
			if (gone[0] < 0) {
				if (siege.isPresent() && !siege.get().ladders.isEmpty()) {
					gone[0] = tick;
					climber.discard();
				} else if (tick > 500) {
					helper.fail("no rung to start with: siege " + siege.isPresent() + ", the climber at " + climber.position());
				}
				return;
			}
			if (tick == gone[0] + 100) {
				helper.assertTrue(siege.isPresent() && !siege.get().over, "the siege ended (the test would prove nothing)");
				helper.assertTrue(ThreatData.get(level).raid(yard.hall()).isPresent(), "the raid ended (the test would prove nothing)");
				helper.assertTrue(!siege.get().ladders.isEmpty(), "the rungs were forgotten while the raid is under way");
				helper.succeed();
			}
		});
	}
	/**
	 * Two pillager climbers in pens against the south and the east wall (spots more than 8 apart): each sets its own
	 * ladder, climbs it, crosses the wall's top and drops inside ("They climb over and drop inside").
	 */
	//$ gametest_ticks_batch AREA '900' '"qaLadderOverTwoSides1103d"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaLadderOverTwoSides1103d")
	public void climbersGetOverLaddersOnTwoSides(GameTestHelper helper) {
		Yard yard = yard(helper, false, 9, 19);
		fourPens(helper);
		List<Mob> mobs = climbers(helper, yard, new double[][] {FOUR[0], FOUR[3]});
		ServerLevel level = yard.level();
		helper.onEachTick(() -> {
			if (mobs.stream().allMatch(m -> Ladders.over(level, yard.hall(), m.getUUID()))) {
				BlockPos base = helper.absolutePos(BlockPos.ZERO);
				for (Mob m : mobs) {
					double x = m.getX() - base.getX();
					double z = m.getZ() - base.getZ();
					helper.assertTrue(x > 9 && x < 19 && z > 9 && z < 19, "a climber counted over the wall stands outside the yard: " + x + ", " + z);
				}
				helper.succeed();
				return;
			}
			if (helper.getTick() >= 850) {
				helper.fail("not both climbers over the wall: " + mobs.stream().map(m -> (Ladders.over(level, yard.hall(), m.getUUID()) ? "over at " : "not over, at ")
					+ m.position().subtract(helper.absolutePos(BlockPos.ZERO).getCenter()) + (m.isAlive() ? "" : " (dead)")).toList()
					+ ", ladders " + Sieges.siege(level, yard.hall()).map(s -> where(helper, ladders(s))).orElse("no siege"));
			}
		});
	}

	/** Two husk climbers in pens against the west and the east wall: each piles a ramp of its own. */
	//$ gametest_ticks_batch AREA '600' '"qaRampWestEast1103d"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaRampWestEast1103d")
	public void husksWestAndEastPileARampEach(GameTestHelper helper) {
		twoRamps(helper, FOUR[2], FOUR[3], "west", "east");
	}

	/** Two husk climbers in pens against the north and the south wall: each piles a ramp of its own. */
	//$ gametest_ticks_batch AREA '600' '"qaRampNorthSouth1103d"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaRampNorthSouth1103d")
	public void husksNorthAndSouthPileARampEach(GameTestHelper helper) {
		twoRamps(helper, FOUR[1], FOUR[0], "north", "south");
	}

	private static String pen(GameTestHelper helper, BlockPos pos) {
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		int x = pos.getX() - base.getX();
		int z = pos.getZ() - base.getZ();
		return z >= 20 ? "south" : z <= 8 ? "north" : x <= 8 ? "west" : x >= 20 ? "east" : "inside " + x + "," + z;
	}

	private static void twoRamps(GameTestHelper helper, double[] a, double[] b, String one, String two) {
		Yard yard = yard(helper, true, 9, 19);
		fourPens(helper);
		List<Mob> mobs = climbers(helper, yard, new double[][] {a, b});
		ServerLevel level = yard.level();
		helper.onEachTick(() -> {
			Optional<ThreatData.Siege> siege = Sieges.siege(level, yard.hall());
			Set<String> pens = new HashSet<>();
			siege.ifPresent(s -> s.sand.forEach(p -> pens.add(pen(helper, p))));
			if (pens.contains(one) && pens.contains(two)) {
				helper.succeed();
				return;
			}
			if (helper.getTick() >= 500) {
				helper.fail("not a ramp by both the " + one + " and the " + two + " pen: sand by the " + pens + ", feet "
					+ siege.map(s -> s.rampFeet.stream().map(p -> pen(helper, p)).toList().toString()).orElse("no siege")
					+ ", husks alive " + mobs.stream().filter(Mob::isAlive).count());
			}
		});
	}

	/**
	 * A player's block of sand at the wall's foot, right where the husk's path ends: the ramp starts in it, that sand
	 * its first block, and is never recorded as the siege's own ("Sand already there ... counts as part of the ramp and is
	 * built on, never recorded").
	 */
	//$ gametest_ticks_batch AREA '600' '"qaRampOnPlayersSand1103d"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaRampOnPlayersSand1103d")
	public void aRampStartsInAPlayersSandAtTheWallsFoot(GameTestHelper helper) {
		Yard yard = yard(helper, true, 9, 19);
		fourPens(helper);
		// the pen's front row narrowed to its middle, so the husk's path can only end on the sand
		wall(helper, 13, 20, 13, 20, 4);
		wall(helper, 15, 20, 15, 20, 4);
		BlockPos sand = new BlockPos(14, 2, 20);
		helper.setBlock(sand, Blocks.SAND);
		BlockPos at = helper.absolutePos(sand);
		Mob husk = climbers(helper, yard, new double[][] {FOUR[0]}).get(0);
		ServerLevel level = yard.level();
		helper.onEachTick(() -> {
			Optional<ThreatData.Siege> siege = Sieges.siege(level, yard.hall());
			if (siege.isPresent() && !siege.get().rampFeet.isEmpty() && siege.get().sand.size() >= 2) {
				BlockPos foot = siege.get().rampFeet.get(0);
				helper.assertTrue(foot.equals(at), "the ramp doesn't start in the sand at the wall's foot (14, 2, 20) but at "
					+ foot.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString());
				helper.assertTrue(!siege.get().sand.contains(at), "the player's sand is recorded as the siege's (it would be taken away)");
				helper.assertTrue(level.getBlockState(at).is(Blocks.SAND), "the player's sand is gone");
				helper.succeed();
				return;
			}
			if (helper.getTick() >= 500) {
				helper.fail("no ramp: feet " + siege.map(s -> s.rampFeet.toString()).orElse("no siege") + ", the husk at " + husk.position());
			}
		});
	}
	/**
	 * The south pen with a strip of powder snow across it, {@code gap} blocks of air out from the wall: no raider walks
	 * on powder snow, so the climber's path ends short of the wall, beyond the strip. The wall is looked for from there,
	 * straight toward the hall ("the first thing at least two blocks high"), over the snow and the air, and the
	 * climber's spot is at the wall's foot, (14, 2, 20).
	 */
	private static Mob behindPowderSnow(GameTestHelper helper, Yard yard, int gap) {
		fourPens(helper);
		for (int x = 13; x <= 15; x++) {
			helper.setBlock(new BlockPos(x, 2, 20 + gap), Blocks.POWDER_SNOW);
		}
		return climbers(helper, yard, new double[][] {{14.5, 25.5}}).get(0);
	}

	/** A strip of powder snow one block out from the wall's foot: the ladder still goes up at the wall's foot. */
	//$ gametest_ticks_batch AREA '600' '"qaLadderPastSnow1103d"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaLadderPastSnow1103d")
	public void aLadderIsSetAtTheWallPastAStripOfPowderSnow(GameTestHelper helper) {
		Yard yard = yard(helper, false, 9, 19);
		Mob climber = behindPowderSnow(helper, yard, 1);
		ServerLevel level = yard.level();
		long foot = BlockPos.asLong(helper.absolutePos(new BlockPos(14, 2, 20)).getX(), 0, helper.absolutePos(new BlockPos(14, 2, 20)).getZ());
		helper.onEachTick(() -> {
			Optional<ThreatData.Siege> siege = Sieges.siege(level, yard.hall());
			Map<Long, Integer> columns = siege.map(QaSiegeCaps1103dGameTests::ladders).orElse(Map.of());
			if (!columns.isEmpty()) {
				helper.assertTrue(columns.size() == 1 && columns.containsKey(foot), "the ladder isn't at the wall's foot (14, 20): " + where(helper, columns));
				helper.succeed();
				return;
			}
			if (helper.getTick() >= 500) {
				helper.fail("no ladder past the powder snow: siege " + siege.isPresent() + ", the climber at "
					+ climber.position().subtract(helper.absolutePos(BlockPos.ZERO).getCenter()));
			}
		});
	}

	/** A strip of powder snow three blocks out from the wall's foot (past where the ramp will reach): the ramp starts at the wall's foot. */
	//$ gametest_ticks_batch AREA '600' '"qaRampPastSnow1103d"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaRampPastSnow1103d")
	public void aRampIsBegunAtTheWallPastAStripOfPowderSnow(GameTestHelper helper) {
		Yard yard = yard(helper, true, 9, 19);
		Mob husk = behindPowderSnow(helper, yard, 3);
		ServerLevel level = yard.level();
		BlockPos foot = helper.absolutePos(new BlockPos(14, 2, 20));
		helper.onEachTick(() -> {
			Optional<ThreatData.Siege> siege = Sieges.siege(level, yard.hall());
			if (siege.isPresent() && !siege.get().rampFeet.isEmpty() && !siege.get().sand.isEmpty()) {
				helper.assertTrue(siege.get().rampFeet.equals(List.of(foot)), "the ramp isn't begun at the wall's foot (14, 2, 20): feet "
					+ siege.get().rampFeet.stream().map(p -> p.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString()).toList());
				helper.assertTrue(siege.get().sand.contains(foot), "the first sand isn't at the ramp's foot: " + siege.get().sand);
				helper.succeed();
				return;
			}
			if (helper.getTick() >= 500) {
				helper.fail("no ramp past the powder snow: siege " + siege.isPresent() + ", the husk at "
					+ husk.position().subtract(helper.absolutePos(BlockPos.ZERO).getCenter()));
			}
		});
	}
	/**
	 * A curb one block high along the wall's foot, past a strip of powder snow: a curb isn't the wall ("the first thing
	 * at least two blocks high"), so the climber's spot is on the curb, and the ladder goes up from there, (14, 3, 20).
	 */
	//$ gametest_ticks_batch AREA '600' '"qaLadderOnCurb1103d"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaLadderOnCurb1103d")
	public void aLadderIsSetOnACurbAtTheWallsFoot(GameTestHelper helper) {
		Yard yard = yard(helper, false, 9, 19);
		Mob climber = behindPowderSnow(helper, yard, 1);
		wall(helper, 13, 20, 15, 20, 1);
		ServerLevel level = yard.level();
		BlockPos foot = helper.absolutePos(new BlockPos(14, 3, 20));
		helper.onEachTick(() -> {
			Optional<ThreatData.Siege> siege = Sieges.siege(level, yard.hall());
			if (siege.isPresent() && !siege.get().ladders.isEmpty()) {
				BlockPos lowest = siege.get().ladders.stream().min(java.util.Comparator.comparingInt(BlockPos::getY)).orElseThrow();
				helper.assertTrue(lowest.equals(foot), "the ladder doesn't start on the curb at (14, 3, 20) but at "
					+ lowest.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString());
				helper.succeed();
				return;
			}
			if (helper.getTick() >= 500) {
				helper.fail("no ladder from the curb: siege " + siege.isPresent() + ", the climber at "
					+ climber.position().subtract(helper.absolutePos(BlockPos.ZERO).getCenter()));
			}
		});
	}
}
