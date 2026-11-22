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
 * QA (2026-11-22) for B1434 (32.9 sand ramps, B726), written from the bug's spec: "a climber waiting while its ramp goes
 * up is sent to a waiting spot two out from the ramp's outer end; one whose waiting spot can only be reached over a
 * player's blocks waits beside the ramp, then climbs it; it never steps onto the half-built ramp's sand and over the
 * player's blocks into a pen".
 *
 * <p>The yard: stone brick walls 4 high at x 8 and 26, z 1 and 19 (a Stone Wall in the front at x 14..20), the hall at
 * (13, 2, 13). A husk from outside the front wall piles its ramp outside the west wall at z 13 (x 4..7, 4 high), so its
 * waiting spot is (2, 2, 13). Each test pens that spot (and in some, the spots beside the ramp) with a different player
 * build, holds the ramp's last stage back with a player's block once its outer sand (4, 2, 13) is down (the moment the
 * half-built sand is a step up onto the pen's top), and asserts what a player sees: the husk never gets into a pen,
 * the player's blocks stay, and the husk piles the ramp to the top and gets over the wall within the budget.
 */
public class QaRampPens1122dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation PILERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_ramp_pens_1122d");

	private static final int WEST = 8;
	private static final int EAST = 26;
	private static final int BACK = 1;
	private static final int FRONT = 19;
	private static final BlockPos HALL = new BlockPos(13, 2, 13);
	/** The ramp's line and its top stage (sand at y 5 against the west wall). */
	private static final int RAMP_Z = 13;
	private static final int TOP = 5;
	/** The last stage's outer block and the block after it, which a player's cobblestone holds back. */
	private static final BlockPos OUTER = new BlockPos(WEST - 4, 2, RAMP_Z);
	private static final BlockPos HELD = new BlockPos(WEST - 3, 3, RAMP_Z);

	private record Yard(ServerLevel level, BlockPos hall, Culture culture, Villager villager) {
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
		// the villager shut in a cell by the hall, so the husk's way to the hall ends outside the west wall
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
		mob.setTarget(yard.villager());
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	private static Vec3 rel(GameTestHelper helper, Vec3 pos) {
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		return pos.subtract(base.getX(), base.getY(), base.getZ());
	}

	/** A pen's inside, as blocks (relative), with its name for the messages. */
	private record Pen(String name, BlockPos from, BlockPos to) {
		AABB box(GameTestHelper helper) {
			return AABB.encapsulatingFullBlocks(helper.absolutePos(from), helper.absolutePos(to));
		}
	}

	/** The pen round the waiting spot: x 1 and 3, z 6 to 20, closed at both ends ({@code gap}: the south end left open), {@code high} blocks of {@code block}. */
	private static Pen spotPen(Map<BlockPos, Block> mine, Block block, int high, boolean gap) {
		for (int y = 2; y < 2 + high; y++) {
			for (int z = 6; z <= 20; z++) {
				mine.put(new BlockPos(1, y, z), block);
				mine.put(new BlockPos(3, y, z), block);
			}
			mine.put(new BlockPos(2, y, 6), block);
			if (!gap) {
				mine.put(new BlockPos(2, y, 20), block);
			}
		}
		return new Pen("pen round the waiting spot", new BlockPos(2, 2, 7), new BlockPos(2, 3, gap ? 20 : 19));
	}

	/**
	 * A small fenced pen round the spot beside the ramp at z {@code RAMP_Z + 2 * dir} (x 4), closed by the spot pen's x 3
	 * fence: its near side runs along the ramp, one block from the sand.
	 */
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
	 * The run. The husk starts at (16.5, 22.5), outside the front wall. Once the last stage's outer block is sand, a player's
	 * cobblestone takes the stage's next block for {@code hold} ticks; {@code reload} > 0: that many ticks into the hold the
	 * threats are saved and loaded (the siege's data through a round trip, what is kept in memory forgotten) and the husk
	 * too (saved to NBT, removed, loaded again), as a restart does. Every tick: the husk is not inside a pen in
	 * {@code pens}; the player's blocks stand; the ramp is only on the z 13 line outside the west wall. Passes when the ramp
	 * has reached the top and the husk is counted over and down inside the yard, before {@code limit}.
	 */
	private static void run(GameTestHelper helper, Yard yard, String what, Map<BlockPos, Block> mine, List<Pen> pens, int hold, int reload, int limit) {
		run(helper, yard, what, mine, pens, hold, reload, limit, 0);
	}

	/**
	 * {@code watch} > 0: the finished ramp can't be climbed (its outer step is boxed in by the player's pens), so the run
	 * passes once the ramp has stood at the top for {@code watch} ticks with the husk out of every pen and never counted over.
	 */
	private static void run(GameTestHelper helper, Yard yard, String what, Map<BlockPos, Block> mine, List<Pen> pens, int hold, int reload, int limit, int watch) {
		ServerLevel level = yard.level();
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		build(helper, mine);
		Mob[] husk = {husk(helper, yard, 16.5, 22.5)};
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(husk[0]));
		helper.assertTrue(Sieges.siege(level, yard.hall()).isPresent(), what + ": no siege on the village");
		boolean[] topped = {false};
		long[] toppedAt = {-1};
		long[] heldAt = {-1};
		String[] waited = {"(never held)"};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			Mob mob = husk[0];
			Vec3 p = rel(helper, mob.position());
			List<BlockPos> sand = SandRamps.sand(level, yard.hall()).stream().map(s -> s.subtract(base)).toList();
			for (BlockPos s : sand) {
				helper.assertTrue(s.getX() < WEST && s.getZ() == RAMP_Z, what + ": (setup) the ramp isn't on the z " + RAMP_Z + " line outside the west wall: piled "
					+ sand.stream().map(BlockPos::toShortString).toList());
			}
			for (Pen pen : pens) {
				helper.assertFalse(mob.getBoundingBox().intersects(pen.box(helper)), what + ": the husk got into the player's " + pen.name() + " at " + p
					+ " (tick " + tick + ", last stage held back since " + heldAt[0] + "), the ramp piled " + sand.stream().map(BlockPos::toShortString).toList());
			}
			mine.forEach((at, block) -> helper.assertTrue(helper.getBlockState(at).is(block), what + ": the player's " + block + " at " + at.toShortString()
				+ " is now " + helper.getBlockState(at)));
			if (hold > 0) {
				if (heldAt[0] < 0 && helper.getBlockState(OUTER).is(Blocks.SAND) && helper.getBlockState(HELD).isAir()) {
					helper.setBlock(HELD, Blocks.COBBLESTONE);
					heldAt[0] = tick;
				} else if (heldAt[0] >= 0 && tick == heldAt[0] + hold) {
					waited[0] = p.toString();
					helper.setBlock(HELD, Blocks.AIR);
				} else if (heldAt[0] >= 0 && reload > 0 && tick == heldAt[0] + reload) {
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
					io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("[qa-ramp-pens] {}: saved and loaded at tick {}, the husk at {}", what, tick, p);
					return;
				}
			}
			if (!topped[0] && sand.stream().anyMatch(s -> s.getX() == WEST - 1 && s.getY() == TOP)) {
				topped[0] = true;
				toppedAt[0] = tick;
			}
			boolean over = SandRamps.over(level, yard.hall(), mob.getUUID());
			if (watch > 0) {
				helper.assertFalse(over, what + ": the husk is counted over the wall at " + p + " (tick " + tick + ") though the ramp's foot is boxed in");
				if (topped[0] && tick >= toppedAt[0] + watch) {
					io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("[qa-ramp-pens] {}: the ramp topped at tick {}, held back from {}, waited at {}, the husk at {} at tick {}, out of every pen",
						what, toppedAt[0], heldAt[0], waited[0], p, tick);
					helper.succeed();
				} else if (tick > limit) {
					helper.fail(what + ": the ramp " + (topped[0] ? "topped at " + toppedAt[0] : "never reached the top (y " + TOP + ")") + " within " + limit
						+ " ticks, held back from " + heldAt[0] + ", piled " + sand.stream().map(BlockPos::toShortString).toList() + ", the husk at " + p);
				}
				return;
			}
			if (topped[0] && over && mob.isAlive() && p.x > WEST + 0.5 && p.x < EAST && p.z > BACK + 0.5 && p.z < FRONT - 1.5 && mob.onGround() && p.y < 2.6) {
				io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("[qa-ramp-pens] {}: over at tick {} at {}, held back from {}, waited at {}, piled {}", what, tick, p,
					heldAt[0], waited[0], sand.stream().map(BlockPos::toShortString).toList());
				helper.succeed();
				return;
			}
			if (tick > limit) {
				helper.fail(what + ": not over by the ramp within " + limit + " ticks: the ramp " + (topped[0] ? "reached the top" : "never reached the top (y " + TOP + ")")
					+ ", held back from " + heldAt[0] + ", piled " + sand.stream().map(BlockPos::toShortString).toList() + ", the husk at " + p
					+ (mob.isAlive() ? "" : " (dead)") + ", counted over " + over);
			}
		});
	}

	/** A cobblestone wall pen (1.5 high, out of reach from the ground, half a step from the sand's top) round the waiting spot. */
	//$ gametest_ticks_batch AREA '1200' '"qaRampPens1122dCobbleWall"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaRampPens1122dCobbleWall")
	public void qaAHuskNeverStepsOffTheHalfBuiltRampIntoACobblestoneWallPen(GameTestHelper helper) {
		Yard yard = yard(helper);
		Map<BlockPos, Block> mine = new LinkedHashMap<>();
		Pen pen = spotPen(mine, Blocks.COBBLESTONE_WALL, 1, false);
		run(helper, yard, "cobblestone wall pen", mine, List.of(pen), 120, 0, 1150);
	}

	/**
	 * A pen of solid cobblestone two high: from the ground its top is two up (out of reach), from the outer sand's top only
	 * one (a jump). The half-built sand must not be the step into it.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaRampPens1122dTwoHigh"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaRampPens1122dTwoHigh")
	public void qaAHuskNeverJumpsOffTheHalfBuiltRampIntoATwoHighStonePen(GameTestHelper helper) {
		Yard yard = yard(helper);
		Map<BlockPos, Block> mine = new LinkedHashMap<>();
		Pen pen = spotPen(mine, Blocks.COBBLESTONE, 2, false);
		run(helper, yard, "two-high cobblestone pen", mine, List.of(pen), 120, 0, 1150);
	}

	/** The waiting spot fenced off, and a second fenced pen round the spot beside the ramp on its south side (z 15), its fence along the ramp. */
	//$ gametest_ticks_batch AREA '1200' '"qaRampPens1122dSouth"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaRampPens1122dSouth")
	public void qaAHuskNeverEntersAPenBesideTheRampsSouthSide(GameTestHelper helper) {
		Yard yard = yard(helper);
		Map<BlockPos, Block> mine = new LinkedHashMap<>();
		List<Pen> pens = List.of(spotPen(mine, Blocks.OAK_FENCE, 1, false), besidePen(mine, 1));
		run(helper, yard, "spot and south side penned", mine, pens, 120, 0, 1150);
	}

	/** The mirror: the second pen on the ramp's north side (z 11). */
	//$ gametest_ticks_batch AREA '1200' '"qaRampPens1122dNorth"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaRampPens1122dNorth")
	public void qaAHuskNeverEntersAPenBesideTheRampsNorthSide(GameTestHelper helper) {
		Yard yard = yard(helper);
		Map<BlockPos, Block> mine = new LinkedHashMap<>();
		List<Pen> pens = List.of(spotPen(mine, Blocks.OAK_FENCE, 1, false), besidePen(mine, -1));
		run(helper, yard, "spot and north side penned", mine, pens, 120, 0, 1150);
	}

	/**
	 * The waiting spot and both spots beside the ramp fenced off: no clean place to wait (the way to each runs over the
	 * piled sand and a fence), so by the spec it stays put. The pens box in the ramp's outer step on all three sides, so the
	 * finished ramp can't be climbed from the ground either (the only ways onto it are off a fence's top): the husk must
	 * still pile the ramp to the top, and for 200 ticks after that stay out of all three pens and never be counted over.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaRampPens1122dBoth"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaRampPens1122dBoth")
	public void qaAHuskWithEveryWaitingPlacePennedStaysOutOfEveryPen(GameTestHelper helper) {
		Yard yard = yard(helper);
		Map<BlockPos, Block> mine = new LinkedHashMap<>();
		List<Pen> pens = new ArrayList<>(List.of(spotPen(mine, Blocks.OAK_FENCE, 1, false), besidePen(mine, 1), besidePen(mine, -1)));
		run(helper, yard, "spot and both sides penned", mine, pens, 120, 0, 1150, 200);
	}

	/**
	 * The fenced spot with the last stage held back 300 ticks, and the game saved and loaded 100 ticks into the wait (the
	 * siege's data, what the ramps keep in memory, and the husk itself): after the load it still never gets into the pen,
	 * and it finishes the ramp and gets over.
	 */
	//$ gametest_ticks_batch AREA '1500' '"qaRampPens1122dReload"'
	@GameTest(template = AREA, timeoutTicks = 1500, batch = "qaRampPens1122dReload")
	public void qaAHuskWaitingBesideAHeldRampStaysOutOfThePenAfterASaveAndLoad(GameTestHelper helper) {
		Yard yard = yard(helper);
		Map<BlockPos, Block> mine = new LinkedHashMap<>();
		Pen pen = spotPen(mine, Blocks.OAK_FENCE, 1, false);
		run(helper, yard, "held 300 ticks, saved and loaded", mine, List.of(pen), 300, 100, 1450);
	}

	/**
	 * The other side of the line: the pen's south end left open, so the waiting spot can be reached on the ground. The
	 * husk may wait inside it; it is never shut in, and it walks out, finishes the ramp and gets over.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaRampPens1122dOpen"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaRampPens1122dOpen")
	public void qaAHuskWhoseWaitingSpotIsInAnOpenPenStillClimbs(GameTestHelper helper) {
		Yard yard = yard(helper);
		Map<BlockPos, Block> mine = new LinkedHashMap<>();
		spotPen(mine, Blocks.OAK_FENCE, 1, true);
		run(helper, yard, "open pen round the spot", mine, List.of(), 120, 0, 1150);
	}
}
