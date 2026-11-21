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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-11-21) for two landed siege-climb fixes, written from the bug text, not the code.
 * <ul>
 * <li>B1389 (threat/Ladders): "a climber who gets over the wall comes down inside, on the ground, and goes for his
 * target; he never stays standing on the inner stairs or on the wall's top". The rebuilt Stone Wall has stairs on its
 * inner face; the climber's ladder may stand on a player's hay bale (its foot one up), the ground inside may be one
 * higher than outside, and a Palisade (an inner bank of steps) must behave as before. Each test follows the climber
 * past the moment he is let go: he may not stand up on a stair or the wall for long, and he must shoot his target
 * (a villager kept still, more than a crossbow's 8 blocks from where he lands and from the wall's top) from the ground
 * inside.</li>
 * <li>B726 (threat/SandRamps): "a raider is let go as over the wall only when it was on the ramp's top or has the ramp's
 * wall beside it; one whose waiting spot out from the ramp can't be reached waits beside the ramp". A husk whose ramp
 * is on a side wall (the west one) walks round the front corner to it; a player's garden wall stands on the west
 * wall's line outside the yard (a different wall); a player's fenced pen covers the ramp's waiting spot. The husk is
 * never counted over while it is outside the yard, the ramp is piled to the wall's top, and the husk gets in.</li>
 * </ul>
 */
public class QaSiegeClimbs1121dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	// ---------------------------------------------------------------- B1389: ladders.

	private static final ResourceLocation CLIMBERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_climbs_1121d_ladders");
	/** The yard of LadderGameTests: walls at x 5 and 23, z 4 (back) and 15 (front, with the defence in it, outside to the south). */
	private static final int WEST = 5;
	private static final int EAST = 23;
	private static final int BACK = 4;
	private static final int FRONT = 15;
	/** The outer face of the defence's middle, where the climbers' ladder goes (a player's bale here in the bale tests). */
	private static final BlockPos FOOT = new BlockPos(14, 2, FRONT + 1);
	/**
	 * How long, after he is let go, the climber may stand up on a stair or the wall before it counts as staying there (5 s;
	 * B1389's climber stayed for good. Measured: about 1 run in 6, the ladder goes up a merlon, six rungs, and the climber
	 * stands on the Stone Wall's bottom step at the ground, (13, 2, 13), up to 38 ticks before he walks on; on the
	 * Palisade's bank up to 33).
	 */
	private static final int STAY = 100;
	/** By when he must have shot his target from the ground inside. */
	private static final int LIMIT = 950;

	private record Yard(ServerLevel level, BlockPos hall, Culture culture, Villager villager, int ground) {
	}

	private static Culture culture(GameTestHelper helper, ResourceLocation id, String json) {
		Culture culture = Culture.read(id, com.google.gson.JsonParser.parseString(json).getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new LinkedHashMap<>(loaded);
		with.put(id, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> Threats.setForTest(loaded));
		return culture;
	}

	/** Clears the area, sets a small village radius, and puts the siege state back when the test ends. */
	private static void village(GameTestHelper helper, BlockPos hall) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
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
	}

	/** A finished defence blueprint with its origin at the helper position {@code at}, recorded as built. */
	private static void place(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(at);
		level.getStructureManager().get(entry.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(entry.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
	}

	/** Stone brick walls four high round x {@code west}..{@code east}, z {@code back}..{@code front}, with a 7-wide gap in the front wall from {@code gap}. */
	private static void walls(GameTestHelper helper, int west, int east, int back, int front, int gap) {
		for (int y = 2; y <= 5; y++) {
			for (int x = west; x <= east; x++) {
				helper.setBlock(new BlockPos(x, y, back), Blocks.STONE_BRICKS);
				if (x < gap || x > gap + 6) {
					helper.setBlock(new BlockPos(x, y, front), Blocks.STONE_BRICKS);
				}
			}
			for (int z = back; z <= front; z++) {
				helper.setBlock(new BlockPos(west, y, z), Blocks.STONE_BRICKS);
				helper.setBlock(new BlockPos(east, y, z), Blocks.STONE_BRICKS);
			}
		}
	}

	/**
	 * The ladder yard with {@code defence} (Stone Wall or Palisade) in the middle of its front wall, outer face at z 15.
	 * {@code raised}: the ground inside is one block higher (dirt on every free block of the floor inside). The target is
	 * a villager kept still (no AI) at (7.5, ground, 6.5): 9 blocks from where a climber over the middle lands, 11 from the
	 * wall's top, so a crossbow (8) reaches it only after he walks in.
	 */
	private static Yard ladderYard(GameTestHelper helper, StarterBlueprints.Entry defence, boolean raised) {
		int ground = raised ? 3 : 2;
		BlockPos hall = helper.absolutePos(new BlockPos(14, ground, 8));
		village(helper, hall);
		Culture culture = culture(helper, CLIMBERS, "{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:pillager\", \"share\": 100, \"role\": \"climber\"}], \"tactics\": [\"ladders\"]}");
		walls(helper, WEST, EAST, BACK, FRONT, 11);
		place(helper, defence, new BlockPos(11, 2, FRONT - (defence.size().getZ() - 1)));
		if (raised) {
			for (int x = WEST + 1; x < EAST; x++) {
				for (int z = BACK + 1; z < FRONT; z++) {
					if (helper.getBlockState(new BlockPos(x, 2, z)).isAir()) {
						helper.setBlock(new BlockPos(x, 2, z), Blocks.DIRT);
					}
				}
			}
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(7, ground, 6));
		villager.setNoAi(true);
		return new Yard(helper.getLevel(), hall, culture, villager, ground);
	}

	private static Mob raider(GameTestHelper helper, Yard yard, double x, double z) {
		ServerLevel level = yard.level();
		Culture.Member member = yard.culture().roster().stream().filter(m -> m.role() == Culture.Role.CLIMBER).findFirst().orElseThrow();
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob != null, "no " + member.entity());
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		mob.moveTo(base.getX() + x, base.getY() + 2, base.getZ() + z, 180f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
		Threats.outfit(level, mob, yard.culture(), member);
		mob.setTarget(yard.villager());
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	private static Vec3 rel(GameTestHelper helper, Vec3 pos) {
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		return pos.subtract(base.getX(), base.getY(), base.getZ());
	}

	/** What {@code mob} stands on, for the messages. */
	private static String under(GameTestHelper helper, Mob mob) {
		BlockPos below = BlockPos.containing(mob.getX(), mob.getY() - 0.2, mob.getZ());
		return helper.getLevel().getBlockState(below) + " at " + below.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString();
	}

	/** Down inside the ladder yard: alive, in from the defence's inner face and the other walls, its feet under half a block above the ground. */
	private static boolean downInside(GameTestHelper helper, Mob mob, int ground, int innerFace) {
		Vec3 p = rel(helper, mob.position());
		return mob.isAlive() && p.x > WEST + 1 && p.x < EAST && p.z > BACK + 1 && p.z < innerFace && p.y < ground + 0.6;
	}

	/** Standing a block or more above the ground: on a stair's top, the wall's top, the walkway. */
	private static boolean standingUp(GameTestHelper helper, Mob mob, int ground) {
		return mob.isAlive() && mob.onGround() && rel(helper, mob.position()).y >= ground + 0.9;
	}

	/**
	 * The climb, watched past the let-go: within {@link #LIMIT} ticks the climber shoots the villager while down inside,
	 * having crossed by the ladder; after he is let go he never stands up on a stair or the wall for more than
	 * {@link #STAY} ticks in a row; a player's bale at the foot is never replaced.
	 */
	private static void climbAndShoot(GameTestHelper helper, Yard yard, String what, int innerFace, boolean bale) {
		ServerLevel level = yard.level();
		BlockPos baleAt = helper.absolutePos(FOOT);
		Mob climber = raider(helper, yard, 14.5, 22.5);
		Villager villager = yard.villager();
		float health = villager.getHealth();
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(climber));
		helper.assertTrue(Sieges.siege(level, yard.hall()).isPresent(), what + ": no siege on the village");
		String[] upOn = {"nothing"};
		long[] seen = {-1, 0, 0, -1, 0}; // let go at, ticks stood up in a row, most rungs, first down inside at, most ticks stood up in a row
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (bale) {
				helper.assertTrue(level.getBlockState(baleAt).is(Blocks.HAY_BLOCK), what + ": the player's hay bale was replaced: " + level.getBlockState(baleAt));
			}
			seen[2] = Math.max(seen[2], Ladders.rungs(level, yard.hall()).size());
			boolean over = Ladders.over(level, yard.hall(), climber.getUUID());
			if (over && seen[0] < 0) {
				seen[0] = tick;
			}
			if (over) {
				if (standingUp(helper, climber, yard.ground())) {
					seen[1]++;
					if (seen[1] > seen[4]) {
						seen[4] = seen[1];
						upOn[0] = rel(helper, climber.position()) + " on " + under(helper, climber);
					}
					helper.assertTrue(seen[1] <= STAY, what + ": let go at tick " + seen[0] + ", the climber has stood up for " + seen[1]
						+ " ticks at " + rel(helper, climber.position()) + " on " + under(helper, climber) + " (ground inside y " + yard.ground() + ")");
				} else {
					seen[1] = 0;
				}
				if (seen[3] < 0 && downInside(helper, climber, yard.ground(), innerFace)) {
					seen[3] = tick;
				}
			}
			if (villager.getHealth() < health || !villager.isAlive()) {
				helper.assertTrue(villager.getLastHurtByMob() == climber, what + ": the villager was hurt by " + villager.getLastHurtByMob() + ", not the climber");
				helper.assertTrue(over && downInside(helper, climber, yard.ground(), innerFace), what + ": the climber hit the villager from "
					+ rel(helper, climber.position()) + " on " + under(helper, climber) + (over ? "" : " before being let go over the wall") + ", not from the ground inside");
				io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("[qa-climbs] {}: let go at {}, first down inside at {}, shot the villager at tick {} from {} on {}, most rungs {}, stood up at most {} ticks in a row (last at {})",
					what, seen[0], seen[3], tick, rel(helper, climber.position()), under(helper, climber), seen[2], seen[4], upOn[0]);
				helper.succeed();
				return;
			}
			if (tick > LIMIT) {
				helper.fail(what + ": no shot at the villager from the ground inside within " + LIMIT + " ticks: the climber is at "
					+ rel(helper, climber.position()) + " on " + under(helper, climber) + (climber.isAlive() ? "" : " (dead)") + ", let go at " + seen[0]
					+ ", first down inside at " + seen[3] + ", most rungs " + seen[2] + ", target " + climber.getTarget() + ", villager health " + villager.getHealth());
			}
		});
	}

	/**
	 * B1389's own case, followed through: the player's hay bale against the Stone Wall's outer face (the ladder's foot
	 * one up), ground inside level with outside. The climber gets in and down past the inner stairs, never stays on a
	 * stair or the wall's top, and shoots his target from the ground.
	 */
	//$ gametest_ticks_batch AREA '1000' '"qaClimbs1121dBale"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaClimbs1121dBale")
	public void qaAClimberOverTheStoneWallFromABaleComesDownAndShootsHisTarget(GameTestHelper helper) {
		Yard yard = ladderYard(helper, StarterBlueprints.STONE_WALL, false);
		helper.setBlock(FOOT, Blocks.HAY_BLOCK);
		climbAndShoot(helper, yard, "bale, Stone Wall", FRONT - 2, true);
	}

	/**
	 * Ground inside one higher than outside, no bale: the case the fix keeps "as before" (a column free down to one above
	 * the foot). The inner stair whose top is a block above that ground is not where he ends up: he comes down on the
	 * raised ground and shoots his target from it.
	 */
	//$ gametest_ticks_batch AREA '1000' '"qaClimbs1121dRaised"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaClimbs1121dRaised")
	public void qaAClimberOverTheStoneWallOntoRaisedGroundComesDownOffTheStairs(GameTestHelper helper) {
		Yard yard = ladderYard(helper, StarterBlueprints.STONE_WALL, true);
		climbAndShoot(helper, yard, "raised ground, Stone Wall", FRONT - 2, false);
	}

	/** Ground inside one higher and the bale outside too (foot and ground inside at the same height). */
	//$ gametest_ticks_batch AREA '1000' '"qaClimbs1121dRaisedBale"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaClimbs1121dRaisedBale")
	public void qaAClimberFromABaleOntoRaisedGroundComesDownOffTheStairs(GameTestHelper helper) {
		Yard yard = ladderYard(helper, StarterBlueprints.STONE_WALL, true);
		helper.setBlock(FOOT, Blocks.HAY_BLOCK);
		climbAndShoot(helper, yard, "raised ground and bale, Stone Wall", FRONT - 2, true);
	}

	/** The plain climb from flat ground beside the rebuilt Stone Wall, followed past the let-go. */
	//$ gametest_ticks_batch AREA '1000' '"qaClimbs1121dFlat"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaClimbs1121dFlat")
	public void qaAClimberOverTheStoneWallFromFlatGroundComesDownAndShootsHisTarget(GameTestHelper helper) {
		Yard yard = ladderYard(helper, StarterBlueprints.STONE_WALL, false);
		climbAndShoot(helper, yard, "flat ground, Stone Wall", FRONT - 2, false);
	}

	/**
	 * A Palisade (logs outside, a bank of steps inside: a cobblestone stair at the ground, a mud brick stair, a log) with
	 * the bale at its foot: unchanged, the climber gets over, comes down off the bank and shoots his target.
	 */
	//$ gametest_ticks_batch AREA '1000' '"qaClimbs1121dPalisade"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaClimbs1121dPalisade")
	public void qaAClimberOverAPalisadeFromABaleComesDownAndShootsHisTarget(GameTestHelper helper) {
		Yard yard = ladderYard(helper, StarterBlueprints.PALISADE, false);
		helper.setBlock(FOOT, Blocks.HAY_BLOCK);
		climbAndShoot(helper, yard, "bale, Palisade", FRONT - 3, true);
	}

	// ---------------------------------------------------------------- B726: sand ramps on a side wall.

	private static final ResourceLocation PILERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_climbs_1121d_pilers");
	/**
	 * The ramp yard: walls at x 8 and 26 (room for a ramp and its waiting spot outside the west wall), z 1 (the back) and
	 * 19 (the front, a Stone Wall in it at x 14..20).
	 */
	private static final int R_WEST = 8;
	private static final int R_EAST = 26;
	private static final int R_BACK = 1;
	private static final int R_FRONT = 19;
	/**
	 * The hall: 6 blocks (walking) in from the west wall's outside, 7 from the front's, 12 from the back's, so the husk's
	 * way to it (not reachable: more than the 5 a raider's way may end short of the hall) ends outside the west wall.
	 */
	private static final BlockPos R_HALL = new BlockPos(13, 2, 13);
	/** The ramp's top stage, against the west wall's 4-high top: y 5 at x 7. */
	private static final int R_TOP = 5;
	private static final int R_LIMIT = 1150;

	private static Yard rampYard(GameTestHelper helper) {
		BlockPos hall = helper.absolutePos(R_HALL);
		village(helper, hall);
		Culture culture = culture(helper, PILERS, "{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"climber\"}], \"hours\": \"until_noon\", \"tactics\": [\"sand_ramps\"]}");
		walls(helper, R_WEST, R_EAST, R_BACK, R_FRONT, 14);
		place(helper, StarterBlueprints.STONE_WALL, new BlockPos(14, 2, R_FRONT - 2));
		// the villager's closed cell, by the hall (x 12..14, z 9..11), off the strip where a raider over the west ramp lands
		for (int x = 12; x <= 14; x++) {
			for (int z = 9; z <= 11; z++) {
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), x == 13 && z == 10 && y < 4 ? Blocks.AIR : Blocks.STONE_BRICKS);
				}
			}
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(13, 2, 10));
		return new Yard(helper.getLevel(), hall, culture, villager, 2);
	}

	/** Outside the ramp yard's walls (not on them): west of the west wall, south of the front wall, north of the back, east of the east. */
	private static boolean outsideRampYard(Vec3 p) {
		return p.x < R_WEST || p.x > R_EAST + 1 || p.z > R_FRONT + 1 || p.z < R_BACK;
	}

	/**
	 * A husk from ({@code x}, {@code z}) piles its ramp at the west wall. At no tick is it counted over the wall while it
	 * is outside the yard; the ramp stands only outside the west wall and reaches its top; and the husk gets over and
	 * down inside, counted over.
	 */
	private static void rampRun(GameTestHelper helper, Yard yard, String what, double x, double z, Map<BlockPos, Block> mine) {
		ServerLevel level = yard.level();
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		mine.forEach(helper::setBlock);
		Mob husk = raider(helper, yard, x, z);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(husk));
		helper.assertTrue(Sieges.siege(level, yard.hall()).isPresent(), what + ": no siege on the village");
		boolean[] topped = {false};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			Vec3 p = rel(helper, husk.position());
			List<BlockPos> sand = SandRamps.sand(level, yard.hall()).stream().map(s -> s.subtract(base)).toList();
			boolean over = SandRamps.over(level, yard.hall(), husk.getUUID());
			helper.assertTrue(!(over && outsideRampYard(p)), what + ": the husk is counted over the wall while outside the yard, at " + p
				+ " (tick " + tick + "), the ramp piled " + sand.stream().map(BlockPos::toShortString).toList());
			mine.forEach((rel, block) -> helper.assertTrue(helper.getBlockState(rel).is(block), what + ": the player's " + block + " at " + rel.toShortString()
				+ " is now " + helper.getBlockState(rel)));
			for (BlockPos s : sand) {
				helper.assertTrue(s.getX() < R_WEST && s.getZ() > R_BACK && s.getZ() < R_FRONT, what + ": sand away from the west wall's outside at "
					+ s.toShortString() + ": the ramp isn't at the side wall, piled " + sand.stream().map(BlockPos::toShortString).toList());
			}
			if (!topped[0] && sand.stream().anyMatch(s -> s.getX() == R_WEST - 1 && s.getY() == R_TOP)) {
				topped[0] = true;
			}
			if (topped[0] && over && husk.isAlive() && p.x > R_WEST + 0.5 && p.x < R_EAST && p.z > R_BACK + 0.5 && p.z < R_FRONT - 1.5 && husk.onGround() && p.y < 2.6) {
				io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("[qa-climbs] {}: over at tick {} at {}, piled {}", what, tick, p, sand.stream().map(BlockPos::toShortString).toList());
				helper.succeed();
				return;
			}
			if (tick > R_LIMIT) {
				helper.fail(what + ": not over by the side wall's ramp within " + R_LIMIT + " ticks: the ramp " + (topped[0] ? "reached the top" : "never reached the top (y " + R_TOP + ")")
					+ ", piled " + sand.stream().map(BlockPos::toShortString).toList() + ", the husk at " + p + (husk.isAlive() ? "" : " (dead)")
					+ ", counted over " + over);
			}
		});
	}

	/**
	 * The husk comes from outside the front wall, east of the Stone Wall; its ramp goes at the west (side) wall, so it walks
	 * west along the front wall's outside and round the south-west corner. On the way it passes, outside, the strip in
	 * from the ramp's foot where a raider over the ramp lands (x 9 to 12): it must not be let go there.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaClimbs1121dSideRamp"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaClimbs1121dSideRamp")
	public void qaAHuskWalkingRoundTheCornerToASideWallRampPilesItAndGetsOver(GameTestHelper helper) {
		Yard yard = rampYard(helper);
		rampRun(helper, yard, "side-wall ramp round the corner", 16.5, 22.5, Map.of());
	}

	/**
	 * A player's fenced pen (x 1 to 3, z 6 to 20) covers the spot out from the side-wall ramp where the husk would wait
	 * while the sand goes up (two blocks out from the ramp's outer end, x 2). It can't get there, so it waits beside the
	 * ramp: the ramp is still piled to the top, and the husk gets over.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaClimbs1121dPen"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaClimbs1121dPen")
	public void qaAHuskWhoseWaitingSpotIsFencedOffWaitsBesideTheSideRamp(GameTestHelper helper) {
		Yard yard = rampYard(helper);
		Map<BlockPos, Block> pen = new LinkedHashMap<>();
		for (int z = 6; z <= 20; z++) {
			pen.put(new BlockPos(1, 2, z), Blocks.OAK_FENCE);
			pen.put(new BlockPos(3, 2, z), Blocks.OAK_FENCE);
		}
		pen.put(new BlockPos(2, 2, 6), Blocks.OAK_FENCE);
		pen.put(new BlockPos(2, 2, 20), Blocks.OAK_FENCE);
		rampRun(helper, yard, "fenced waiting spot", 16.5, 22.5, pen);
	}
}
