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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (lane D, 2026-11-03) for siege ladders when the gate opens mid-climb (ROADMAP 32.5; wiki sieges.md: "They climb
 * it, cross the wall's top and drop inside; the other raiders follow up the same ladder while the gate is still
 * shut"). A yard whose front wall holds a finished Stone Wall (the climber's ladder goes up its middle) and, well to
 * its east, a finished Palisade Gate (the siege's breach). Pillager climbers set the ladder; a pillager who only
 * follows (role {@code ranged}: no axe, so nobody rams the gate) is sent up the ladder once it stands. Then "a player"
 * breaks the gate: a follower still on the ground must let go of the ladder and walk in through the gateway instead
 * (the rule at Ladders.step's {@code release}, whose {@code || !shut} survived a mutation run); with the gate still shut
 * it climbs; one already up the ladder when the gate goes isn't left hanging there.
 */
public class QaSiegeLadderBreach1103dGameTests {
	static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation CULTURE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_ladder_breach_climbers");
	private static final BlockPos HALL = new BlockPos(14, 2, 8);
	private static final int WEST = 5;
	private static final int EAST = 28;
	private static final int BACK = 4;
	private static final int FRONT = 15;
	/** The Stone Wall's x (its middle, a crenel over it, is where the ladder goes: 14). */
	private static final int WALL_X = 11;
	/** The Palisade Gate's x (7 wide: 19 to 25). */
	private static final int GATE_X = 19;

	record Yard(ServerLevel level, BlockPos hall, Culture culture, Villager villager) {
		ThreatData.Siege siege(GameTestHelper helper) {
			Optional<ThreatData.Siege> siege = Sieges.siege(level, hall);
			helper.assertTrue(siege.isPresent(), "no siege on the village");
			return siege.get();
		}
	}

	private static Culture culture(GameTestHelper helper) {
		Culture culture = Culture.read(CULTURE, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:pillager\", \"share\": 50, \"role\": \"climber\"}, {\"entity\": \"minecraft:pillager\", \"share\": 50, \"role\": \"ranged\"}],"
			+ " \"tactics\": [\"ladders\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new LinkedHashMap<>(loaded);
		with.put(CULTURE, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> Threats.setForTest(loaded));
		return culture;
	}

	private static void finished(GameTestHelper helper, StarterBlueprints.Entry build, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(at);
		level.getStructureManager().get(build.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(build.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
	}

	/** The yard: brick walls four high, the Stone Wall and the Palisade Gate in the front one, a villager in a closed cell. */
	static Yard yard(GameTestHelper helper) {
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
		Culture culture = culture(helper);
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
		for (int y = 2; y <= 5; y++) {
			for (int x = WEST; x <= EAST; x++) {
				helper.setBlock(new BlockPos(x, y, BACK), Blocks.STONE_BRICKS);
				boolean stoneWall = x >= WALL_X && x < WALL_X + 7;
				boolean gate = x >= GATE_X && x < GATE_X + 7;
				if (!stoneWall && !gate) {
					helper.setBlock(new BlockPos(x, y, FRONT), Blocks.STONE_BRICKS);
				}
			}
			for (int z = BACK; z <= FRONT; z++) {
				helper.setBlock(new BlockPos(WEST, y, z), Blocks.STONE_BRICKS);
				helper.setBlock(new BlockPos(EAST, y, z), Blocks.STONE_BRICKS);
			}
		}
		finished(helper, StarterBlueprints.STONE_WALL, new BlockPos(WALL_X, 2, FRONT - 2));
		finished(helper, StarterBlueprints.PALISADE_GATE, new BlockPos(GATE_X, 2, FRONT - (StarterBlueprints.PALISADE_GATE.size().getZ() - 1)));
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

	private static Mob raider(GameTestHelper helper, Yard yard, Culture.Role role, double x, double z) {
		ServerLevel level = yard.level();
		Culture.Member member = yard.culture().roster().stream().filter(m -> m.role() == role).findFirst().orElseThrow();
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob != null, "no " + member.entity());
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		mob.moveTo(base.getX() + x, base.getY() + 2, base.getZ() + z, 180f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
		if (mob.getVehicle() != null) {
			net.minecraft.world.entity.Entity vehicle = mob.getVehicle();
			mob.stopRiding();
			vehicle.discard();
		}
		mob.getRandom().setSeed(1L);
		Threats.outfit(level, mob, yard.culture(), member);
		mob.setTarget(yard.villager());
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	/** The climber and the follower, raiders of one raid on the yard's village; the siege begins with the gate shut. */
	static Mob[] raiders(GameTestHelper helper, Yard yard) {
		Mob climber = raider(helper, yard, Culture.Role.CLIMBER, 14.5, 21.5);
		Mob follower = raider(helper, yard, Culture.Role.RANGED, 22.5, 23.5);
		VillageRaids.track(yard.level(), yard.hall(), yard.culture(), List.of(climber, follower));
		ThreatData.Siege siege = yard.siege(helper);
		helper.assertTrue(siege.breach != null, "the siege has no breach (the Palisade Gate isn't its gate): the test would prove nothing");
		helper.assertTrue(!Sieges.open(siege), "the gate isn't shut as the siege begins");
		return new Mob[] {climber, follower};
	}

	/** The foot (lowest rung) of the siege's ladder, or null while none is set. */
	static BlockPos foot(ThreatData.Siege siege) {
		return siege.ladders.stream().min(java.util.Comparator.comparingInt(BlockPos::getY)).orElse(null);
	}

	/** Whether the ladder stands: as many rungs as the Stone Wall's crenel needs (5), all set. */
	static boolean standing(ThreatData.Siege siege) {
		return siege.ladders.size() >= 5;
	}

	/** Whether the siege's ladders hold {@code mob} (the director's own view of it says so). */
	static boolean onLadderDuty(Yard yard, Mob mob) {
		return Sieges.directorState(yard.level(), yard.hall(), mob).contains(", ladder true");
	}

	/** "A player" breaks every gate block of the breach. */
	static void breakTheGate(Yard yard, ThreatData.Siege siege) {
		for (ThreatData.Gate gate : siege.gates) {
			if (Sieges.isGate(yard.level().getBlockState(gate.pos))) {
				yard.level().destroyBlock(gate.pos, false);
			}
		}
	}

	/** Whether {@code mob} is up the ladder at {@code foot}: in its column (within a block), more than a block above its foot. */
	static boolean upTheLadder(Mob mob, BlockPos foot) {
		return foot != null && Math.abs(mob.getX() - (foot.getX() + 0.5)) < 1.2 && Math.abs(mob.getZ() - (foot.getZ() + 0.5)) < 1.2
			&& mob.getY() > foot.getY() + 1.0;
	}

	/** Whether {@code mob} is inside the yard, down on its floor. */
	static boolean inside(GameTestHelper helper, Mob mob) {
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		double x = mob.getX() - base.getX();
		double y = mob.getY() - base.getY();
		double z = mob.getZ() - base.getZ();
		return mob.isAlive() && z < FRONT - 1 && z > BACK + 1 && x > WEST + 1 && x < EAST && y < 3.5;
	}

	static String state(GameTestHelper helper, Yard yard, Mob mob) {
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		return String.format(Locale.ROOT, "at (%.1f, %.1f, %.1f)%s, %s", mob.getX() - base.getX(), mob.getY() - base.getY(), mob.getZ() - base.getZ(),
			mob.isAlive() ? "" : " dead", Sieges.directorState(yard.level(), yard.hall(), mob))
			+ ", over by ladder " + Ladders.over(yard.level(), yard.hall(), mob.getUUID()) + ", through the gate " + Sieges.through(yard.level(), yard.hall(), mob.getUUID());
	}

	static String rel(GameTestHelper helper, BlockPos pos) {
		return pos == null ? "none" : pos.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString();
	}

	/**
	 * The survivor's case. The ladder stands and the follower has been sent up it, but it is still on the ground walking
	 * to the foot, more than two blocks from it, when the gate goes: it lets go of the ladder and walks in through the gateway ("follow up the same
	 * ladder while the gate is still shut"), and is never counted over the wall by the ladder. (That it still walks to the
	 * ladder's foot and up a few rungs first is a bug of its own, QaSiegeLadderBreach1103dBugTests.)
	 */
	//$ gametest_ticks_batch AREA '1500' '"qaLadderBreachFollowerLetsGo1103d"'
	@GameTest(template = AREA, timeoutTicks = 1500, batch = "qaLadderBreachFollowerLetsGo1103d")
	public void aFollowerOnTheGroundLetsGoOfTheLadderWhenTheGateOpens(GameTestHelper helper) {
		Yard yard = yard(helper);
		Mob[] mobs = raiders(helper, yard);
		Mob climber = mobs[0];
		Mob follower = mobs[1];
		ThreatData.Siege siege = yard.siege(helper);
		long[] opened = {-1};
		BlockPos[] foot = {null};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (opened[0] < 0) {
				helper.assertTrue(!Ladders.over(yard.level(), yard.hall(), follower.getUUID()), "the follower got over before the test could open the gate: "
					+ state(helper, yard, follower));
				if (standing(siege) && onLadderDuty(yard, follower) && follower.onGround()) {
					foot[0] = foot(siege);
					double dx = follower.getX() - (foot[0].getX() + 0.5);
					double dz = follower.getZ() - (foot[0].getZ() + 0.5);
					if (dx * dx + dz * dz > 4 && follower.getY() < foot[0].getY() + 0.5) {
						opened[0] = tick;
						breakTheGate(yard, siege);
					}
				} else if (tick > 1100) {
					helper.fail("the follower was never sent up a standing ladder while still on the ground (the test would prove nothing): rungs "
						+ siege.ladders.size() + " from " + rel(helper, foot(siege)) + "; follower " + state(helper, yard, follower) + "; climber " + state(helper, yard, climber));
				}
				return;
			}
			helper.assertTrue(!Ladders.over(yard.level(), yard.hall(), follower.getUUID()),
				"the follower went over the wall by the ladder " + (tick - opened[0]) + " ticks after the gate was broken open (it should walk in through the gate): "
					+ state(helper, yard, follower) + "; ladder foot " + rel(helper, foot[0]) + ", way open " + Sieges.open(siege));
			if (inside(helper, follower) && Sieges.through(yard.level(), yard.hall(), follower.getUUID())) {
				helper.assertTrue(Sieges.open(siege), "inside, but the siege doesn't count the way open");
				helper.succeed();
			} else if (tick - opened[0] > 400) {
				helper.fail("the follower isn't in through the gate 400 ticks after it was broken open: " + state(helper, yard, follower)
					+ "; ladder foot " + rel(helper, foot[0]) + ", way open " + Sieges.open(siege));
			}
		});
	}

	/** The control: with the gate still shut the follower sent up the ladder climbs it and is over the wall by it. */
	//$ gametest_ticks_batch AREA '1500' '"qaLadderBreachShutFollowerClimbs1103d"'
	@GameTest(template = AREA, timeoutTicks = 1500, batch = "qaLadderBreachShutFollowerClimbs1103d")
	public void withTheGateShutTheFollowerClimbsTheLadder(GameTestHelper helper) {
		Yard yard = yard(helper);
		Mob[] mobs = raiders(helper, yard);
		Mob climber = mobs[0];
		Mob follower = mobs[1];
		ThreatData.Siege siege = yard.siege(helper);
		boolean[] sent = {false};
		helper.onEachTick(() -> {
			helper.assertTrue(!Sieges.open(siege), "the gate opened by itself (nobody here breaks gates)");
			helper.assertTrue(!Sieges.through(yard.level(), yard.hall(), follower.getUUID()), "the follower is counted through a shut gate: " + state(helper, yard, follower));
			sent[0] |= onLadderDuty(yard, follower);
			if (inside(helper, follower) && Ladders.over(yard.level(), yard.hall(), follower.getUUID())) {
				helper.assertTrue(sent[0], "over by the ladder but never sent up it");
				helper.succeed();
			} else if (helper.getTick() > 1300) {
				helper.fail("the follower isn't over the wall by the ladder with the gate shut: " + state(helper, yard, follower) + ", sent up it " + sent[0]
					+ "; rungs " + siege.ladders.size() + " from " + rel(helper, foot(siege)) + "; climber " + state(helper, yard, climber));
			}
		});
	}

	/**
	 * The gate goes while the follower is already up the ladder: it isn't let go halfway up (to fall off it), it climbs
	 * on, crosses the wall's top and is over by the ladder, inside the yard and alive.
	 */
	//$ gametest_ticks_batch AREA '1500' '"qaLadderBreachMidClimb1103d"'
	@GameTest(template = AREA, timeoutTicks = 1500, batch = "qaLadderBreachMidClimb1103d")
	public void aFollowerUpTheLadderWhenTheGateOpensClimbsOn(GameTestHelper helper) {
		Yard yard = yard(helper);
		Mob[] mobs = raiders(helper, yard);
		Mob climber = mobs[0];
		Mob follower = mobs[1];
		ThreatData.Siege siege = yard.siege(helper);
		long[] opened = {-1};
		BlockPos[] foot = {null};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (opened[0] < 0) {
				if (standing(siege) && onLadderDuty(yard, follower) && upTheLadder(follower, foot(siege))) {
					foot[0] = foot(siege);
					opened[0] = tick;
					breakTheGate(yard, siege);
				} else if (tick > 1100) {
					helper.fail("the follower never got up the ladder (the test would prove nothing): rungs " + siege.ladders.size() + " from "
						+ rel(helper, foot(siege)) + "; follower " + state(helper, yard, follower) + "; climber " + state(helper, yard, climber));
				}
				return;
			}
			if (inside(helper, follower) && Ladders.over(yard.level(), yard.hall(), follower.getUUID())) {
				helper.succeed();
			} else if (tick - opened[0] > 300) {
				helper.fail("the follower up the ladder when the gate was broken isn't over the wall by it 300 ticks later: " + state(helper, yard, follower)
					+ "; ladder foot " + rel(helper, foot[0]) + ", up it now " + upTheLadder(follower, foot[0]) + ", inside " + inside(helper, follower));
			}
		});
	}

	/**
	 * The boundary between "on the ground" and "up the ladder": the follower stands at the ladder's very foot, still on the
	 * ground (not a rung climbed: pressed there by the crowd, held by the test), when the gate goes. It hasn't started up,
	 * so it lets go of the ladder like one still walking there (Ladders.onIt: up the ladder only past half a block) and,
	 * once free to move, walks in through the gateway and is never counted over the wall by the ladder.
	 */
	//$ gametest_ticks_batch AREA '1500' '"qaLadderBreachAtFoot1103d"'
	@GameTest(template = AREA, timeoutTicks = 1500, batch = "qaLadderBreachAtFoot1103d")
	public void aFollowerStillOnTheGroundAtTheFootLetsGoWhenTheGateOpens(GameTestHelper helper) {
		Yard yard = yard(helper);
		Mob[] mobs = raiders(helper, yard);
		Mob climber = mobs[0];
		Mob follower = mobs[1];
		ThreatData.Siege siege = yard.siege(helper);
		long[] opened = {-1};
		long[] letGo = {-1};
		BlockPos[] foot = {null};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (opened[0] < 0) {
				helper.assertTrue(!Ladders.over(yard.level(), yard.hall(), follower.getUUID()), "the follower got over before the test could open the gate: "
					+ state(helper, yard, follower));
				if (standing(siege) && onLadderDuty(yard, follower) && follower.getY() < foot(siege).getY() + 0.5) {
					foot[0] = foot(siege);
					opened[0] = tick;
					breakTheGate(yard, siege);
				} else if (tick > 1100) {
					helper.fail("the follower was never sent up a standing ladder while still on the ground (the test would prove nothing): rungs "
						+ siege.ladders.size() + " from " + rel(helper, foot(siege)) + "; follower " + state(helper, yard, follower) + "; climber " + state(helper, yard, climber));
				}
			}
			if (opened[0] < 0) {
				return;
			}
			if (letGo[0] < 0) {
				// Held standing at the foot, on the ground, until the ladders let go of it.
				follower.getNavigation().stop();
				follower.teleportTo(foot[0].getX() + 0.5, foot[0].getY(), foot[0].getZ() + 0.5);
				follower.setDeltaMovement(0, 0, 0);
				if (!onLadderDuty(yard, follower)) {
					letGo[0] = tick;
				} else if (tick - opened[0] > 60) {
					helper.fail("the follower standing at the ladder's foot, not a rung up, is still held to the ladder " + (tick - opened[0])
						+ " ticks after the gate was broken open (way open " + Sieges.open(siege) + "): " + state(helper, yard, follower) + "; ladder foot " + rel(helper, foot[0]));
				}
				return;
			}
			helper.assertTrue(!Ladders.over(yard.level(), yard.hall(), follower.getUUID()),
				"the follower at the ladder's foot went over the wall by the ladder " + (tick - opened[0]) + " ticks after the gate was broken open (it should walk in "
					+ "through the gate): " + state(helper, yard, follower) + "; ladder foot " + rel(helper, foot[0]));
			if (inside(helper, follower) && Sieges.through(yard.level(), yard.hall(), follower.getUUID())) {
				helper.succeed();
			} else if (tick - opened[0] > 460) {
				helper.fail("the follower isn't in through the gate 400 ticks after it was let go of the ladder: " + state(helper, yard, follower)
					+ "; ladder foot " + rel(helper, foot[0]) + ", way open " + Sieges.open(siege));
			}
		});
	}

	/**
	 * With the gate shut, the follower walking to the ladder when "a player" breaks its lowest rung (the rest come down
	 * with it) and its climber is gone (shot): it goes back to wait with the others in front of the gate instead of
	 * waiting at a ladder that isn't there (Ladders.step: "back to the others at the gate: its ladder went down").
	 */
	//$ gametest_ticks_batch AREA '1500' '"qaLadderBreachLadderDown1103d"'
	@GameTest(template = AREA, timeoutTicks = 1500, batch = "qaLadderBreachLadderDown1103d")
	public void aFollowerGoesBackToTheGateWhenItsLadderComesDown(GameTestHelper helper) {
		Yard yard = yard(helper);
		Mob[] mobs = raiders(helper, yard);
		Mob climber = mobs[0];
		Mob follower = mobs[1];
		ThreatData.Siege siege = yard.siege(helper);
		long[] down = {-1};
		BlockPos[] foot = {null};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (down[0] < 0) {
				if (standing(siege) && onLadderDuty(yard, follower) && follower.onGround()) {
					foot[0] = foot(siege);
					double dx = follower.getX() - (foot[0].getX() + 0.5);
					double dz = follower.getZ() - (foot[0].getZ() + 0.5);
					if (dx * dx + dz * dz > 4 && follower.getY() < foot[0].getY() + 0.5) {
						down[0] = tick;
						climber.discard();
						yard.level().destroyBlock(foot[0], false);
					}
				} else if (tick > 1100) {
					helper.fail("the follower was never sent up a standing ladder while still on the ground (the test would prove nothing): rungs "
						+ siege.ladders.size() + "; follower " + state(helper, yard, follower));
				}
				return;
			}
			helper.assertTrue(!Sieges.open(siege), "the gate opened by itself (nobody here breaks gates)");
			helper.assertTrue(!Ladders.over(yard.level(), yard.hall(), follower.getUUID()), "the follower got over by a ladder that came down: " + state(helper, yard, follower));
			if (tick - down[0] < 200) {
				return;
			}
			// In front of the gate, where the director has the others wait (five out from the breach, spread three either way
			// along the wall): from 2 to 8 out, at most 6 aside. The ladder's foot is 8 aside.
			BlockPos gate = siege.breach;
			double ox = follower.getX() - (gate.getX() + 0.5);
			double oz = follower.getZ() - (gate.getZ() + 0.5);
			double out = ox * siege.out.getStepX() + oz * siege.out.getStepZ();
			double aside = Math.abs(ox * siege.out.getStepZ() + oz * siege.out.getStepX());
			if (siege.ladders.isEmpty() && out >= 2 && out <= 8 && aside <= 6 && !onLadderDuty(yard, follower)) {
				helper.succeed();
			} else if (tick - down[0] > 500) {
				helper.fail("the follower isn't back waiting in front of the gate 500 ticks after its ladder came down: " + state(helper, yard, follower)
					+ "; gate " + rel(helper, gate) + ", rungs " + siege.ladders.size() + " (foot " + rel(helper, foot[0]) + ")");
			}
		});
	}
}
