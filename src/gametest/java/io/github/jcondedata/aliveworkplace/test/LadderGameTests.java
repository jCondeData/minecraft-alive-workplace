package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Ladders;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;

/**
 * Sieges II: ladders over the walls (ROADMAP 32.5). Each test walls a yard in the middle of the area with a finished
 * Stone Wall in its front wall (no gate anywhere: the only way in is over), a villager shut in a stone cell inside,
 * and the raiders of a culture that sets ladders outside. The tests ask what the item promises: a pillager climber is
 * over the Stone Wall within 600 ticks, by a ladder set into air on its outer face, and another raider follows up the
 * same ladder; a guard on the walkway throws the ladder down and the climber on it falls; after the siege, and after a
 * save and load half-way up, no raider ladder is left and no other block changed; a siege gathers outside a ring of
 * Stone Walls; and the ways it could break: {@code mobGriefing} off, {@code sieges} off, and a player breaking the
 * wall behind the ladder half-way up.
 */
public class LadderGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation CLIMBERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_climbers");
	/** The hall's place, inside the yard. */
	private static final BlockPos HALL = new BlockPos(14, 2, 8);
	/** The yard's walls: x 5 and 23, z 4 (the back) and 15 (the front, with the Stone Wall in it, its outside to the south). */
	private static final int WEST = 5;
	private static final int EAST = 23;
	private static final int BACK = 4;
	private static final int FRONT = 15;
	/** Where the climbers' ladder goes: the outer face of the Stone Wall's middle, a crenel over it (five rungs). */
	private static final BlockPos FOOT = new BlockPos(14, 2, FRONT + 1);
	private static final int RUNGS = 5;

	private record Yard(ServerLevel level, BlockPos hall, Culture culture, Villager villager) {
		ThreatData.Siege siege(GameTestHelper helper) {
			Optional<ThreatData.Siege> siege = Sieges.siege(level, hall);
			helper.assertTrue(siege.isPresent(), "no siege on the village");
			return siege.get();
		}
	}

	/** The culture of the tests: pillager climbers and vindicators who follow, with the tactic {@code ladders}. Loaded for the test only. */
	private static Culture culture(GameTestHelper helper) {
		Culture culture = Culture.read(CLIMBERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:pillager\", \"share\": 60, \"role\": \"climber\"}, {\"entity\": \"minecraft:vindicator\", \"share\": 40, \"role\": \"melee\"}],"
			+ " \"tactics\": [\"ladders\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new java.util.LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new java.util.LinkedHashMap<>(loaded);
		with.put(CLIMBERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> Threats.setForTest(loaded));
		return culture;
	}

	/** Sets the village up (a small radius, the test's culture) and puts everything back when the test ends. */
	private static Culture village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper); // (walls another batch's test recorded here would be this village's walls too)
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		VillageHalls.RADIUS = 20;
		BlockPos hall = helper.absolutePos(HALL);
		Culture culture = culture(helper);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Sieges.forget();
			Ladders.forget();
			VillageHalls.RADIUS = radius;
			level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer());
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		return culture;
	}

	/** The yard: stone brick walls four high, a finished Stone Wall in the middle of the front one, a villager in a closed cell. */
	private static Yard yard(GameTestHelper helper) {
		Culture culture = village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		for (int y = 2; y <= 5; y++) {
			for (int x = WEST; x <= EAST; x++) {
				helper.setBlock(new BlockPos(x, y, BACK), Blocks.STONE_BRICKS);
				if (x < 11 || x > 17) {
					helper.setBlock(new BlockPos(x, y, FRONT), Blocks.STONE_BRICKS);
				}
			}
			for (int z = BACK; z <= FRONT; z++) {
				helper.setBlock(new BlockPos(WEST, y, z), Blocks.STONE_BRICKS);
				helper.setBlock(new BlockPos(EAST, y, z), Blocks.STONE_BRICKS);
			}
		}
		// The Stone Wall (7 x 6 x 3): its outer row, with the battlements, stands in the front wall.
		place(helper, new BlockPos(11, 2, FRONT - 2), Rotation.NONE);
		for (int x = 9; x <= 11; x++) {
			for (int z = 6; z <= 8; z++) {
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), x == 10 && z == 7 && y < 4 ? Blocks.AIR : Blocks.STONE_BRICKS);
				}
			}
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 7));
		return new Yard(level, hall, culture, villager);
	}

	/** A finished Stone Wall with its origin at the helper position {@code at}, turned by {@code rotation}. */
	private static void place(GameTestHelper helper, BlockPos at, Rotation rotation) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(at);
		level.getStructureManager().get(StarterBlueprints.STONE_WALL.id()).orElseThrow().placeInWorld(level, origin, origin,
			new StructurePlaceSettings().setRotation(rotation), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, rotation, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(StarterBlueprints.STONE_WALL.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
	}

	/** One of the culture's raiders with {@code role}, as a raid spawns it, at the helper position (x, 2, z), after the villager. */
	private static Mob raider(GameTestHelper helper, Yard yard, Culture.Role role, double x, double z) {
		ServerLevel level = yard.level();
		Culture.Member member = yard.culture().roster().stream().filter(m -> m.role() == role).findFirst().orElseThrow();
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

	/** Whether {@code mob} is over the wall: alive, inside the yard and down from the wall. */
	private static boolean inside(GameTestHelper helper, Mob mob) {
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		double x = mob.getX() - base.getX();
		double y = mob.getY() - base.getY();
		double z = mob.getZ() - base.getZ();
		return mob.isAlive() && z < FRONT - 1 && z > BACK + 1 && x > WEST + 1 && x < EAST && y < 3.5;
	}

	private static boolean outside(GameTestHelper helper, Mob mob) {
		return mob.getZ() - helper.absolutePos(BlockPos.ZERO).getZ() > FRONT + 1;
	}

	/** Every block of the test area, by position. */
	private static Map<BlockPos, BlockState> blocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds();
		Map<BlockPos, BlockState> out = new HashMap<>();
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX - 1, box.maxY - 1, box.maxZ - 1))) {
			out.put(pos.immutable(), level.getBlockState(pos));
		}
		return out;
	}

	/** The ladders in the test area that aren't in {@code before} (the Stone Wall has one of its own, inside). */
	private static List<BlockPos> raiderLadders(GameTestHelper helper, Map<BlockPos, BlockState> before) {
		ServerLevel level = helper.getLevel();
		List<BlockPos> out = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
			if (level.getBlockState(e.getKey()).is(Blocks.LADDER) && !e.getValue().is(Blocks.LADDER)) {
				out.add(e.getKey());
			}
		}
		return out;
	}

	/** What differs between the test area now and {@code before}. */
	private static List<String> changes(GameTestHelper helper, Map<BlockPos, BlockState> before) {
		ServerLevel level = helper.getLevel();
		List<String> out = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
			BlockState now = level.getBlockState(e.getKey());
			if (now != e.getValue()) {
				out.add(e.getKey().toShortString() + ": " + e.getValue() + " -> " + now);
			}
		}
		return out;
	}

	/**
	 * The item's first promise. A pillager climber outside a Stone Wall with a villager inside sets a ladder up the
	 * wall's outer face, rung by rung and only into air, climbs it and is inside within 600 ticks; the vindicator with
	 * it follows up the same ladder. The players are told once, in a sentence the lang file has.
	 */
	//$ gametest_ticks_batch AREA '700' '"ladderClimb"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "ladderClimb")
	public void aClimberLaddersTheStoneWallAndTheRestFollow(GameTestHelper helper) {
		Yard yard = yard(helper);
		ServerLevel level = yard.level();
		Map<BlockPos, BlockState> before = blocks(helper);
		BlockPos foot = helper.absolutePos(FOOT);
		for (int i = 0; i < RUNGS; i++) {
			helper.assertTrue(level.getBlockState(foot.above(i)).isAir(), "the wall's outer face isn't air at " + foot.above(i));
		}
		helper.assertTrue(Language.getInstance().has("message.aliveworkplace.siege.ladders"), "the ladders' message has no text");
		Mob climber = raider(helper, yard, Culture.Role.CLIMBER, 14.5, 21.5);
		Mob follower = raider(helper, yard, Culture.Role.MELEE, 16.5, 22.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(climber, follower));
		ThreatData.Siege siege = yard.siege(helper);
		helper.assertTrue(siege.ladders.isEmpty() && !siege.laddered, "ladders before anyone set one");
		long[] seen = {-1, -1, -1, 0}; // the ladder finished, the climber inside, the follower inside, the most rungs at once
		int[] last = {0, 0}; // the rungs last tick, and the tick they last grew
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			List<BlockPos> rungs = Ladders.rungs(level, yard.hall());
			if (rungs.size() > last[0]) {
				helper.assertTrue(rungs.size() == last[0] + 1, "more than a rung at once: " + last[0] + " -> " + rungs.size());
				helper.assertTrue(last[0] == 0 || tick - last[1] >= Ladders.EVERY, "a rung after " + (tick - last[1]) + " ticks");
				last[1] = (int) tick;
			}
			last[0] = rungs.size();
			seen[3] = Math.max(seen[3], rungs.size());
			List<BlockPos> standing = raiderLadders(helper, before);
			helper.assertTrue(standing.size() == rungs.size() && rungs.containsAll(standing), "the ladders standing aren't the ones recorded: " + standing + " / " + rungs);
			if (seen[0] < 0 && rungs.size() == RUNGS) {
				seen[0] = tick;
				for (int i = 0; i < RUNGS; i++) {
					BlockState rung = level.getBlockState(foot.above(i));
					helper.assertTrue(rung.is(Blocks.LADDER) && rung.getValue(LadderBlock.FACING) == Direction.SOUTH, "no rung on the outer face at " + foot.above(i) + ": " + rung);
				}
				helper.assertTrue(siege.laddered, "the players weren't told");
			}
			if (seen[1] < 0 && inside(helper, climber)) {
				seen[1] = tick;
				helper.assertTrue(seen[0] >= 0, "the climber is inside without a finished ladder");
			}
			if (seen[2] < 0 && inside(helper, follower)) {
				seen[2] = tick;
			}
			if (tick > 600 && (seen[1] < 0 || seen[2] < 0)) {
				helper.fail("not over the wall within 600 ticks: the climber " + (seen[1] < 0 ? "is at " + climber.position() : "was in at " + seen[1])
					+ ", the follower " + (seen[2] < 0 ? "is at " + follower.position() : "was in at " + seen[2]) + "; the ladder had " + seen[3]
					+ " rungs, finished at " + seen[0]);
			}
			if (seen[1] >= 0 && seen[2] >= 0) {
				helper.assertTrue(seen[3] == RUNGS, "one ladder of " + RUNGS + " rungs, not " + seen[3]);
				helper.assertTrue(Ladders.over(level, yard.hall(), climber.getUUID()) && Ladders.over(level, yard.hall(), follower.getUUID()), "in, but not by the ladder");
				helper.assertTrue(Ladders.mostPaths(level, yard.hall()) <= Ladders.MAX_PATHS, "paths in a tick: " + Ladders.mostPaths(level, yard.hall()));
				List<String> changed = changes(helper, before);
				changed.removeIf(c -> c.contains("-> Block{minecraft:ladder}"));
				helper.assertTrue(changed.isEmpty(), "something other than ladders changed: " + changed);
				helper.succeed();
			}
		});
	}

	/**
	 * QA (lane D, 2026-10-24): "Ladders go on any wall, a player's too, but only into air." A player's hay bale stands
	 * against the wall's outer face right where the climber would set his ladder: the bale is never replaced, the ladder
	 * goes up in air beside it, and the climber is still over the wall within 600 ticks with nothing else changed.
	 */
	//$ gametest_ticks_batch AREA '700' '"qaLadderBesideABlock"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaLadderBesideABlock")
	public void aLadderGoesUpInAirBesideAPlayersBlockNotOverIt(GameTestHelper helper) {
		Yard yard = yard(helper);
		ServerLevel level = yard.level();
		helper.setBlock(FOOT, Blocks.HAY_BLOCK);
		Map<BlockPos, BlockState> before = blocks(helper);
		BlockPos bale = helper.absolutePos(FOOT);
		Mob climber = raider(helper, yard, Culture.Role.CLIMBER, 14.5, 21.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(climber));
		yard.siege(helper);
		int[] most = {0};
		helper.onEachTick(() -> {
			helper.assertTrue(level.getBlockState(bale).is(Blocks.HAY_BLOCK), "the player's hay bale was replaced: " + level.getBlockState(bale));
			List<BlockPos> rungs = Ladders.rungs(level, yard.hall());
			most[0] = Math.max(most[0], rungs.size());
			for (BlockPos rung : rungs) {
				helper.assertTrue(before.get(rung) == null || before.get(rung).isAir(), "a rung where there was " + before.get(rung) + " at " + rung);
			}
			if (inside(helper, climber)) {
				List<String> changed = changes(helper, before);
				changed.removeIf(c -> c.contains("-> Block{minecraft:ladder}"));
				helper.assertTrue(changed.isEmpty(), "something other than ladders changed: " + changed);
				helper.succeed();
			} else if (helper.getTick() > 600) {
				helper.fail("not over the wall within 600 ticks beside the bale: the climber is at " + climber.position() + ", most rungs " + most[0]);
			}
		});
	}

	/**
	 * A guard on the walkway. He comes while the climber is half-way up the finished ladder: the whole column goes, no
	 * raider ladder is left within 3 blocks of him, the climber falls back to the ground outside, and with him standing
	 * there the ladder isn't raised again before {@link Ladders#RAISE_AGAIN} ticks are over.
	 */
	//$ gametest_ticks_batch AREA '700' '"ladderGuard"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "ladderGuard")
	public void aGuardOnTheWalkwayThrowsTheLadderDown(GameTestHelper helper) {
		Yard yard = yard(helper);
		ServerLevel level = yard.level();
		Map<BlockPos, BlockState> before = blocks(helper);
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		Mob climber = raider(helper, yard, Culture.Role.CLIMBER, 14.5, 21.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(climber));
		Villager[] guard = {null};
		long[] seen = {-1, 0}; // thrown down, the most rungs
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			seen[1] = Math.max(seen[1], Ladders.rungs(level, yard.hall()).size());
			if (guard[0] == null) {
				if (tick > 500) {
					helper.fail("the climber never got half-way up: it is at " + climber.position() + ", the ladder had " + seen[1] + " rungs");
				}
				if (climber.getY() - base.getY() >= 3.5) {
					helper.assertTrue(seen[1] == RUNGS, "climbing a ladder of " + seen[1] + " rungs");
					// The walkway is the wall's inner row (z 14), two blocks from the ladder (z 16).
					Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 6, FRONT - 1));
					v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.GUARD));
					v.setNoAi(true);
					guard[0] = v;
				}
				return;
			}
			if (seen[0] < 0) {
				if (Ladders.thrown(level, yard.hall()) > 0) {
					seen[0] = tick;
				} else {
					helper.assertTrue(!inside(helper, climber) && !Ladders.over(level, yard.hall(), climber.getUUID()), "the climber got over with a guard at the ladder's top");
					return;
				}
			}
			List<BlockPos> near = raiderLadders(helper, before).stream().filter(p -> p.closerThan(guard[0].blockPosition(), 3.5)).toList();
			helper.assertTrue(near.isEmpty(), "a raider ladder within 3 blocks of the guard after he threw it down: " + near);
			helper.assertTrue(raiderLadders(helper, before).isEmpty() && Ladders.rungs(level, yard.hall()).isEmpty(), "part of the column still stands");
			helper.assertTrue(!inside(helper, climber) && !Ladders.over(level, yard.hall(), climber.getUUID()), "the climber got over a ladder that was thrown down");
			if (tick == seen[0] + 40) {
				helper.assertTrue(climber.isAlive() && climber.getY() - base.getY() < 2.6 && outside(helper, climber), "the climber didn't fall back outside: " + climber.position());
			}
			if (tick >= seen[0] + Ladders.RAISE_AGAIN - 20) {
				helper.assertTrue(Ladders.thrown(level, yard.hall()) == 1, "thrown down " + Ladders.thrown(level, yard.hall()) + " times");
				helper.assertTrue(changes(helper, before).isEmpty(), "a block changed: " + changes(helper, before));
				helper.succeed();
			}
		});
	}

	/**
	 * Nothing is left behind. A save and load with the ladder half up: the rungs come back with the siege and the climber
	 * carries on from them. A second one with the ladder standing, and then the raid ends: every ladder is taken away
	 * though nothing was in memory, and the area's blocks are what they were before the siege.
	 */
	//$ gametest_ticks_batch AREA '700' '"ladderRestart"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "ladderRestart")
	public void laddersAreTakenAwayAlsoAfterARestart(GameTestHelper helper) {
		Yard yard = yard(helper);
		ServerLevel level = yard.level();
		Map<BlockPos, BlockState> before = blocks(helper);
		Mob climber = raider(helper, yard, Culture.Role.CLIMBER, 14.5, 21.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(climber));
		long[] seen = {-1, -1}; // the first restart, the raid ended
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			List<BlockPos> rungs = Ladders.rungs(level, yard.hall());
			if (seen[0] < 0) {
				if (tick > 400) {
					helper.fail("no ladder half up within 400 ticks: " + rungs.size() + " rungs, the climber at " + climber.position());
				}
				if (rungs.size() == 3) {
					seen[0] = tick;
					ThreatData.get(level).roundTrip(level.registryAccess());
					Sieges.forget();
					Ladders.forget();
					List<BlockPos> back = Ladders.rungs(level, yard.hall());
					helper.assertTrue(back.equals(rungs), "the rungs didn't come back: " + back + " for " + rungs);
				}
				return;
			}
			if (seen[1] < 0) {
				if (tick > 600) {
					helper.fail("the climber didn't get over after the restart: it is at " + climber.position() + ", " + rungs.size() + " rungs");
				}
				helper.assertTrue(rungs.size() >= 3 || tick > seen[0] + 20, "the rungs set before the restart were lost: " + rungs);
				if (inside(helper, climber)) {
					helper.assertTrue(rungs.size() == RUNGS && raiderLadders(helper, before).size() == RUNGS, "over a ladder of " + rungs.size() + " rungs: " + rungs + ", raider ladders " + raiderLadders(helper, before) + ", the climber at " + climber.position() + ", tick " + tick + " (restart at " + seen[0] + "), raiders near the hall "
							+ level.getEntitiesOfClass(Mob.class, new AABB(yard.hall()).inflate(VillageHalls.RADIUS + 32, 48, VillageHalls.RADIUS + 32),
								m -> m.getTags().contains(Threats.RAIDER_TAG)).stream().map(m -> m.getType().toShortString() + "@" + m.blockPosition().toShortString() + (m == climber ? " (the climber)" : "")).toList());
					seen[1] = tick;
					// A second restart, and the raid ends before anything ran again.
					ThreatData.get(level).roundTrip(level.registryAccess());
					Sieges.forget();
					Ladders.forget();
					climber.discard();
					VillageRaids.forget(yard.hall());
				}
				return;
			}
			if (tick == seen[1] + 60) {
				helper.assertTrue(yard.siege(helper).over, "the siege's raid isn't over");
				helper.assertTrue(Ladders.rungs(level, yard.hall()).isEmpty(), "rungs still recorded: " + Ladders.rungs(level, yard.hall()));
				helper.assertTrue(raiderLadders(helper, before).isEmpty(), "a raider ladder is left: " + raiderLadders(helper, before));
				helper.assertTrue(changes(helper, before).isEmpty(), "a block changed: " + changes(helper, before));
				// And at dawn, when the siege itself is forgotten, still nothing.
				Sieges.lift(level, yard.siege(helper));
				helper.assertTrue(changes(helper, before).isEmpty(), "a block changed when the siege was lifted: " + changes(helper, before));
				helper.succeed();
			}
		});
	}

	/**
	 * A player breaks the wall behind the ladder when it is nearly up. The game drops that rung; the ones above it come
	 * down with it, the climbers set them again, and the climber still gets over. When the raid ends no ladder is left.
	 */
	//$ gametest_ticks_batch AREA '800' '"ladderBroken"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "ladderBroken")
	public void aLadderBrokenHalfWayIsSetAgain(GameTestHelper helper) {
		Yard yard = yard(helper);
		ServerLevel level = yard.level();
		Map<BlockPos, BlockState> before = blocks(helper);
		BlockPos foot = helper.absolutePos(FOOT);
		BlockPos wall = foot.above(2).north(); // the wall block the third rung leans on
		Mob climber = raider(helper, yard, Culture.Role.CLIMBER, 14.5, 21.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(climber));
		long[] seen = {-1, -1}; // the wall broken, the raid ended
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			List<BlockPos> rungs = Ladders.rungs(level, yard.hall());
			if (seen[0] < 0) {
				if (rungs.size() == 4) {
					seen[0] = tick;
					level.destroyBlock(wall, false);
					helper.assertTrue(!level.getBlockState(foot.above(2)).is(Blocks.LADDER), "the rung held on to a wall that is gone");
				} else if (tick > 400) {
					helper.fail("no ladder of 4 rungs within 400 ticks: " + rungs.size());
				}
				return;
			}
			if (tick == seen[0] + Ladders.EVERY) {
				// One tick of the ladders later: what is recorded stands, from the foot up without a gap.
				for (int i = 0; i < rungs.size(); i++) {
					helper.assertTrue(rungs.contains(foot.above(i)) && level.getBlockState(foot.above(i)).is(Blocks.LADDER), "a gap in the ladder at " + foot.above(i) + ": " + rungs);
				}
				helper.assertTrue(rungs.size() <= 3, "the rung over the gap still hangs there: " + rungs);
			}
			if (seen[1] < 0) {
				if (tick > 700) {
					helper.fail("the climber didn't get over the mended ladder: it is at " + climber.position() + ", " + rungs.size() + " rungs");
				}
				if (inside(helper, climber)) {
					seen[1] = tick;
					climber.discard();
					VillageRaids.forget(yard.hall());
				}
				return;
			}
			if (tick == seen[1] + 60) {
				helper.assertTrue(raiderLadders(helper, before).isEmpty() && Ladders.rungs(level, yard.hall()).isEmpty(), "a raider ladder is left: " + raiderLadders(helper, before));
				List<String> changed = changes(helper, before);
				changed.removeIf(c -> c.startsWith(wall.toShortString() + ":")); // (the block the player broke)
				helper.assertTrue(changed.isEmpty(), "a block changed: " + changed);
				helper.succeed();
			}
		});
	}

	/**
	 * The switches. With the {@code mobGriefing} rule off it is a siege, but no rung is ever set and the climber stays
	 * outside. With {@code sieges} off the same raid is a plain one: no siege, no ladders.
	 */
	//$ gametest_ticks_batch AREA '700' '"ladderSwitches"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "ladderSwitches")
	public void noLaddersWithMobGriefingOffOrSiegesOff(GameTestHelper helper) {
		Yard yard = yard(helper);
		ServerLevel level = yard.level();
		Map<BlockPos, BlockState> before = blocks(helper);
		level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, level.getServer());
		helper.assertTrue(!Ladders.allowed(level), "mobGriefing is off");
		Mob climber = raider(helper, yard, Culture.Role.CLIMBER, 14.5, 21.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(climber));
		yard.siege(helper);
		Mob[] second = {null};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			helper.assertTrue(raiderLadders(helper, before).isEmpty() && Ladders.rungs(level, yard.hall()).isEmpty(), "a ladder at tick " + tick + ": " + raiderLadders(helper, before));
			if (tick == 300) {
				helper.assertTrue(climber.isAlive() && !inside(helper, climber), "the climber is inside: " + climber.position());
				// Now with the rule on again, and sieges off.
				climber.discard();
				Sieges.lift(level, yard.siege(helper));
				VillageRaids.forget(yard.hall());
				level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
				WorkplaceConfig.parse("{\"sieges\": false}").apply();
				helper.assertTrue(!Sieges.ENABLED, "sieges false");
				second[0] = raider(helper, yard, Culture.Role.CLIMBER, 14.5, 21.5);
				VillageRaids.track(level, yard.hall(), yard.culture(), List.of(second[0]));
				helper.assertTrue(VillageRaids.active(yard.hall()).isPresent(), "the raid itself should go on");
				helper.assertTrue(Sieges.siege(level, yard.hall()).isEmpty(), "a siege with sieges off");
			}
			if (tick == 600) {
				helper.assertTrue(second[0].isAlive() && !inside(helper, second[0]), "the climber is inside with sieges off: " + second[0].position());
				helper.assertTrue(changes(helper, before).isEmpty(), "a block changed: " + changes(helper, before));
				helper.succeed();
			}
		});
	}

	/**
	 * A village ringed by Stone Walls: wherever a siege would gather inside the ring, or too close outside it, its
	 * gathering point is moved out along the same line to beyond the wall on that side; one already beyond stays, and so
	 * does every point with {@code sieges} off. A raid started by a culture that sets ladders then has every raider
	 * outside the ring.
	 */
	//$ gametest_ticks_batch AREA '1200' '"ladderRing"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "ladderRing")
	public void aSiegeGathersOutsideTheRingOfWalls(GameTestHelper helper) {
		Culture culture = village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos centre = new BlockPos(15, 2, 15);
		helper.setBlock(centre, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(centre);
		Leftovers.after(helper, () -> {
			level.removeBlock(hall, false);
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
		});
		// The ring: x and z 5 to 25, every piece with its battlements outward.
		for (int x : new int[] {11, 18, 25}) {
			place(helper, new BlockPos(x, 2, 7), Rotation.CLOCKWISE_180); // north
		}
		for (int x : new int[] {5, 12, 19}) {
			place(helper, new BlockPos(x, 2, 23), Rotation.NONE); // south
		}
		for (int z : new int[] {8, 15}) {
			place(helper, new BlockPos(7, 2, z), Rotation.CLOCKWISE_90); // west
			place(helper, new BlockPos(23, 2, z + 6), Rotation.COUNTERCLOCKWISE_90); // east
		}
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		for (int[] at : new int[][] {{5, 5}, {25, 5}, {5, 25}, {25, 25}, {15, 5}, {15, 25}, {5, 15}, {25, 15}}) {
			helper.assertTrue(!level.getBlockState(base.offset(at[0], 3, at[1])).isAir(), "the ring has no wall at " + at[0] + ", " + at[1]);
		}
		helper.assertTrue(Sieges.walled(level, hall) && Sieges.lays(culture), "a walled village and a culture that lays sieges");
		for (int i = 0; i < 16; i++) {
			double angle = i * Math.PI / 8;
			for (int distance : new int[] {3, 8, 12}) { // inside, at the wall, just outside it
				BlockPos point = hall.offset((int) Math.round(Math.cos(angle) * distance), 0, (int) Math.round(Math.sin(angle) * distance));
				BlockPos moved = Sieges.outside(level, hall, point);
				int x = moved.getX() - base.getX();
				int z = moved.getZ() - base.getZ();
				// Beyond the ring by more than the 3 blocks the raiders scatter round the point.
				helper.assertTrue(x < 5 - 3 || x > 25 + 3 || z < 5 - 3 || z > 25 + 3, "the gathering point for " + point.subtract(base) + " is " + x + ", " + z + ": not beyond the ring");
				double before = Math.atan2(point.getZ() - hall.getZ(), point.getX() - hall.getX());
				double after = Math.atan2(moved.getZ() - hall.getZ(), moved.getX() - hall.getX());
				double turn = Math.abs(Math.atan2(Math.sin(after - before), Math.cos(after - before)));
				helper.assertTrue(turn < 0.2, "it left its line: " + point.subtract(base) + " -> " + x + ", " + z);
			}
			BlockPos far = hall.offset((int) Math.round(Math.cos(angle) * 40), 0, (int) Math.round(Math.sin(angle) * 40));
			helper.assertTrue(Sieges.outside(level, hall, far).equals(far), "a point beyond the ring was moved");
		}
		WorkplaceConfig.parse("{\"sieges\": false}").apply();
		helper.assertTrue(Sieges.outside(level, hall, hall.east(3)).equals(hall.east(3)), "moved with sieges off");
		new WorkplaceConfig().apply();
		// The raid's own start, on the east side, once the ground out there is live. A raid gathers only where mobs move
		// (B176), and only the area's own chunks are kept loaded, so how much east of it was live depended on the batches
		// before. A ticket of the test's own keeps it live: never the forced chunks the batches share (taking those away
		// again hung the next batch, whose area they also were).
		List<net.minecraft.world.level.ChunkPos> east = new ArrayList<>();
		for (int x = 26; x <= 64; x += 2) { // (the hall's radius is the config's again: they gather about 38 east of it)
			for (int z = 10; z <= 20; z += 2) {
				net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(base.offset(x, 0, z));
				if (!east.contains(chunk)) {
					east.add(chunk);
				}
			}
		}
		for (net.minecraft.world.level.ChunkPos chunk : east) {
			level.getChunkSource().addRegionTicket(RING_TICKET, chunk, 2, chunk);
			level.getChunk(chunk.x, chunk.z); // (generated now; the ticket's level spreads with the next ticks)
		}
		Leftovers.after(helper, () -> east.forEach(chunk -> level.getChunkSource().removeRegionTicket(RING_TICKET, chunk, 2, chunk)));
		boolean[] started = {false};
		helper.onEachTick(() -> {
			if (started[0]) {
				return;
			}
			if (!east.stream().allMatch(chunk -> level.isPositionEntityTicking(chunk.getMiddleBlockPosition(hall.getY())))) {
				helper.assertTrue(helper.getTick() < 1100, "the ground east of the ring never came live");
				return;
			}
			started[0] = true;
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 12, 0, 0.0, culture, RandomSource.create(7L));
			helper.assertTrue(raid != null && raid.raiders() >= 3, "no raid started");
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(raiders.size() == raid.raiders(), "the raiders aren't about: " + raiders.size() + " of " + raid.raiders());
			for (Mob mob : raiders) {
				helper.assertTrue(mob.getX() - base.getX() > 26, "a raider appeared inside the ring, at " + mob.position().subtract(base.getX(), base.getY(), base.getZ()));
				helper.assertTrue(level.isPositionEntityTicking(mob.blockPosition()), "a raider stands where mobs don't move, at " + mob.position().subtract(base.getX(), base.getY(), base.getZ()));
			}
			helper.assertTrue(Sieges.siege(level, hall).isPresent(), "the raid on the ringed village isn't a siege");
			helper.succeed();
		});
	}

	/** Keeps the ground east of the ring live for {@link #aSiegeGathersOutsideTheRingOfWalls} (B176); it lapses by itself after 1500 ticks. */
	private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> RING_TICKET = net.minecraft.server.level.TicketType.create(
		"aliveworkplace_test_ring", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong), 1500);

	/**
	 * QA (lane D, 2026-10-26): short grass, as on any plains village's ground, along the whole outer foot of the wall
	 * (on grass blocks): the climber still ladders over within 600 ticks; the grass his ladder stands in may go, nothing
	 * else changes. Kin of B553 (dead bushes at a desert wall's foot stop the sand ramp).
	 */
	//$ gametest_ticks_batch AREA '700' '"qaLadderInGrass"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaLadderInGrass")
	public void aLadderGoesUpThroughShortGrassAtTheWallsFoot(GameTestHelper helper) {
		Yard yard = yard(helper);
		ServerLevel level = yard.level();
		for (int x = WEST + 1; x < EAST; x++) {
			helper.setBlock(new BlockPos(x, 1, FRONT + 1), Blocks.GRASS_BLOCK);
			helper.setBlock(new BlockPos(x, 2, FRONT + 1), Blocks.SHORT_GRASS);
		}
		Map<BlockPos, BlockState> before = blocks(helper);
		Mob climber = raider(helper, yard, Culture.Role.CLIMBER, 14.5, 21.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(climber));
		yard.siege(helper);
		int[] most = {0};
		helper.onEachTick(() -> {
			most[0] = Math.max(most[0], Ladders.rungs(level, yard.hall()).size());
			if (inside(helper, climber)) {
				List<String> changed = changes(helper, before);
				changed.removeIf(c -> c.contains("-> Block{minecraft:ladder}") || c.contains("Block{minecraft:short_grass} -> Block{minecraft:air}"));
				helper.assertTrue(changed.isEmpty(), "something other than ladders and grass changed: " + changed);
				helper.succeed();
			} else if (helper.getTick() > 600) {
				helper.fail("not over the wall within 600 ticks with grass at its foot: the climber is at " + climber.position() + ", most rungs " + most[0]);
			}
		});
	}

	/**
	 * B558: tall grass (both halves) along the wall's outer foot: the climber still ladders over within 600 ticks; the
	 * grass's two halves may go (the lower buried by the first rung, the upper falling with it), nothing else changes and
	 * nothing drops (no seeds or grass lying about).
	 */
	//$ gametest_ticks_batch AREA '700' '"ladderInTallGrass"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "ladderInTallGrass")
	public void aLadderGoesUpThroughTallGrassAtTheWallsFoot(GameTestHelper helper) {
		Yard yard = yard(helper);
		ServerLevel level = yard.level();
		BlockState lower = Blocks.TALL_GRASS.defaultBlockState().setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF,
			net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER);
		for (int x = WEST + 1; x < EAST; x++) {
			helper.setBlock(new BlockPos(x, 1, FRONT + 1), Blocks.GRASS_BLOCK);
			helper.setBlock(new BlockPos(x, 2, FRONT + 1), lower);
			helper.setBlock(new BlockPos(x, 3, FRONT + 1), lower.setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF,
				net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
		}
		Map<BlockPos, BlockState> before = blocks(helper);
		Mob climber = raider(helper, yard, Culture.Role.CLIMBER, 14.5, 21.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(climber));
		yard.siege(helper);
		helper.onEachTick(() -> {
			if (inside(helper, climber)) {
				List<String> changed = changes(helper, before);
				changed.removeIf(c -> c.contains("-> Block{minecraft:ladder}")
					|| c.contains(": Block{minecraft:tall_grass}") && c.endsWith("-> Block{minecraft:air}"));
				helper.assertTrue(changed.isEmpty(), "something other than ladders and tall grass changed: " + changed);
				List<net.minecraft.world.entity.item.ItemEntity> drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					new AABB(helper.absolutePos(new BlockPos(WEST, 0, FRONT - 2))).inflate(20));
				helper.assertTrue(drops.isEmpty(), "the buried grass dropped " + drops);
				helper.succeed();
			} else if (helper.getTick() > 600) {
				helper.fail("not over the wall within 600 ticks with tall grass at its foot: the climber is at " + climber.position());
			}
		});
	}

	/**
	 * QA (lane D, 2026-10-30), B558's Expected names snow layers: a snowy plains village's wall with fallen snow (one
	 * layer, as snowfall leaves it) along its whole outer foot is still laddered within 600 ticks; only the snow a rung
	 * stands in goes, and nothing drops (no snowballs). Snow two layers deep isn't fallen snow (snowfall stacks one) and
	 * isn't buried: no ladder rose through it in this test, as with any block a player put there.
	 */
	//$ gametest_ticks_batch AREA '700' '"qaLadderInSnow1030"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaLadderInSnow1030")
	public void qaALadderGoesUpThroughSnowAtTheWallsFoot(GameTestHelper helper) {
		Yard yard = yard(helper);
		ServerLevel level = yard.level();
		BlockState snow = Blocks.SNOW.defaultBlockState().setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, 1);
		for (int x = WEST + 1; x < EAST; x++) {
			helper.setBlock(new BlockPos(x, 1, FRONT + 1), Blocks.GRASS_BLOCK);
			helper.setBlock(new BlockPos(x, 2, FRONT + 1), snow);
		}
		Map<BlockPos, BlockState> before = blocks(helper);
		Mob climber = raider(helper, yard, Culture.Role.CLIMBER, 14.5, 21.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(climber));
		yard.siege(helper);
		int[] most = {0};
		helper.onEachTick(() -> {
			most[0] = Math.max(most[0], Ladders.rungs(level, yard.hall()).size());
			if (inside(helper, climber)) {
				List<String> changed = changes(helper, before);
				changed.removeIf(c -> c.contains("-> Block{minecraft:ladder}")
					|| c.contains(": Block{minecraft:snow}") && c.endsWith("-> Block{minecraft:air}")
					// the grass block under a snow layer is drawn snowy; with the layer gone it may turn back
					|| c.contains(": Block{minecraft:grass_block}") && c.contains("-> Block{minecraft:grass_block}"));
				helper.assertTrue(changed.isEmpty(), "something other than ladders and snow changed: " + changed);
				List<net.minecraft.world.entity.item.ItemEntity> drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					new AABB(helper.absolutePos(new BlockPos(WEST, 0, FRONT - 2))).inflate(20));
				helper.assertTrue(drops.isEmpty(), "the buried snow dropped " + drops);
				helper.succeed();
			} else if (helper.getTick() > 600) {
				helper.fail("not over the wall within 600 ticks with snow at its foot: the climber is at " + climber.position() + ", most rungs " + most[0]);
			}
		});
	}

	/**
	 * QA (lane D, 2026-10-30), B558's "not a player's flowers": poppies planted all along the wall's outer foot are never
	 * buried by a rung, whatever the climber does in 500 ticks of siege, and none is dropped as an item.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaLadderPoppies1030"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaLadderPoppies1030")
	public void qaALadderNeverBuriesThePlayersFlowers(GameTestHelper helper) {
		Yard yard = yard(helper);
		ServerLevel level = yard.level();
		List<BlockPos> flowers = new ArrayList<>();
		for (int x = WEST + 1; x < EAST; x++) {
			helper.setBlock(new BlockPos(x, 1, FRONT + 1), Blocks.GRASS_BLOCK);
			helper.setBlock(new BlockPos(x, 2, FRONT + 1), Blocks.POPPY);
			flowers.add(new BlockPos(x, 2, FRONT + 1));
		}
		Mob climber = raider(helper, yard, Culture.Role.CLIMBER, 14.5, 21.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(climber));
		yard.siege(helper);
		helper.onEachTick(() -> {
			for (BlockPos at : flowers) {
				if (!helper.getBlockState(at).is(Blocks.POPPY)) {
					helper.fail("the poppy at " + at.toShortString() + " is now " + helper.getBlockState(at) + " (tick " + helper.getTick() + ")");
				}
			}
			if (helper.getTick() >= 500) {
				List<net.minecraft.world.entity.item.ItemEntity> drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					new AABB(helper.absolutePos(new BlockPos(WEST, 0, FRONT - 2))).inflate(20), e -> e.getItem().is(net.minecraft.world.item.Items.POPPY));
				helper.assertTrue(drops.isEmpty(), "a poppy lies dropped: " + drops);
				helper.succeed();
			}
		});
	}

	/**
	 * B553/B558's shared rule ({@code Buriable}): the wild ground's plants a rung or ramp may bury count as room; a
	 * player's flowers, saplings and blocks, water and vines never do.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void onlyTheWildGroundsPlantsMayBeBuried(GameTestHelper helper) {
		for (net.minecraft.world.level.block.Block room : List.of(Blocks.AIR, Blocks.CAVE_AIR, Blocks.SHORT_GRASS, Blocks.TALL_GRASS,
			Blocks.FERN, Blocks.LARGE_FERN, Blocks.DEAD_BUSH, Blocks.SNOW, Blocks.CRIMSON_ROOTS, Blocks.NETHER_SPROUTS)) {
			helper.assertTrue(io.github.jcondedata.aliveworkplace.threat.Buriable.room(room.defaultBlockState()), room + " should be buriable");
		}
		helper.assertTrue(io.github.jcondedata.aliveworkplace.threat.Buriable.room(Blocks.TALL_GRASS.defaultBlockState().setValue(
			net.minecraft.world.level.block.DoublePlantBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)),
			"tall grass's upper half should be buriable");
		for (net.minecraft.world.level.block.Block kept : List.of(Blocks.POPPY, Blocks.DANDELION, Blocks.SUNFLOWER, Blocks.ROSE_BUSH,
			Blocks.OAK_SAPLING, Blocks.WATER, Blocks.LAVA, Blocks.SEAGRASS, Blocks.VINE, Blocks.GLOW_LICHEN, Blocks.FIRE, Blocks.LIGHT,
			Blocks.TORCH, Blocks.SAND, Blocks.MOSS_CARPET, Blocks.WHITE_CARPET, Blocks.LADDER, Blocks.STONE)) {
			helper.assertFalse(io.github.jcondedata.aliveworkplace.threat.Buriable.room(kept.defaultBlockState()), kept + " should not be buriable");
		}
		helper.succeed();
	}
}
