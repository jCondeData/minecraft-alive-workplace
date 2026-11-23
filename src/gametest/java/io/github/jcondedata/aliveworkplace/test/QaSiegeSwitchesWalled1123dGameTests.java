package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
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
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-d-1123-0108) of the two siege switches on a real walled village, from docs/wiki/sieges.md and warbands.md,
 * "Switches", with the shipped pillager warband's raiders (its ravager ram and a pillager climber) outside a yard of
 * stone brick walls with a finished Palisade Gate in the front one:
 * <ul>
 * <li>{@code siegeDamage} off: "it is still a siege and the rams still pound, but gates lose no hit points and never
 * break"; "{@code siegeDamage} off alone leaves the ladders". Switched on again in the same siege (it is read live),
 * the ram's blows cost the gate hit points.</li>
 * <li>{@code sieges} off: "the warband's raids are plain raids (no ram at the gate, no ladders)": the raid goes on, no
 * siege, no rung, the gate blocks as they were. Switched on again, the next raid is a siege with its ladder or its
 * ram's damage.</li>
 * </ul>
 * Earlier tests check the switches with a test culture by a Palisade Gate (SiegeGameTests) and by a gateless Stone
 * Wall (LadderGameTests), and the warband's sieges-off raid by an unwalled village (QaWarbandSpec1123dGameTests); none
 * the warband at a walled village with a gate, and none that ladders still go up with {@code siegeDamage} off.
 */
public class QaSiegeSwitchesWalled1123dGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation WARBAND = AliveWorkplace.id("pillager_warband");
	private static final BlockPos HALL = new BlockPos(14, 2, 8);
	private static final int WEST = 5;
	private static final int EAST = 23;
	private static final int BACK = 4;
	private static final int FRONT = 15;

	private record Yard(ServerLevel level, BlockPos hall, Culture culture, Villager villager, List<BlockPos> gates) {
	}

	/** The walled yard with a finished Palisade Gate in its front wall, a villager in a closed cell, and the warband's culture. */
	private static Yard yard(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		boolean open = Expansions.openForTests;
		Expansions.openForTests = true;
		level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
		VillageHalls.RADIUS = 20;
		BlockPos hall = helper.absolutePos(HALL);
		Optional<Culture> warband = Threats.get(WARBAND);
		helper.assertTrue(warband.isPresent(), "pillager_warband.json isn't loaded");
		Culture culture = warband.get();
		helper.assertTrue(culture.tactics().contains(Sieges.TACTIC) && culture.tactics().contains(Ladders.TACTIC), "the warband's tactics: " + culture.tactics());
		StarterBlueprints.Entry build = StarterBlueprints.PALISADE_GATE;
		int width = build.size().getX();
		int depth = build.size().getZ();
		int x0 = 14 - width / 2;
		int z0 = FRONT - (depth - 1);
		for (int y = 2; y <= 5; y++) {
			for (int x = WEST; x <= EAST; x++) {
				helper.setBlock(new BlockPos(x, y, BACK), Blocks.STONE_BRICKS);
				if (x < x0 || x >= x0 + width) {
					helper.setBlock(new BlockPos(x, y, FRONT), Blocks.STONE_BRICKS);
				}
			}
			for (int z = BACK; z <= FRONT; z++) {
				helper.setBlock(new BlockPos(WEST, y, z), Blocks.STONE_BRICKS);
				helper.setBlock(new BlockPos(EAST, y, z), Blocks.STONE_BRICKS);
			}
		}
		BlockPos origin = helper.absolutePos(new BlockPos(x0, 2, z0));
		level.getStructureManager().get(build.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(build.id(), placement, UUID.randomUUID());
		for (int x = 9; x <= 11; x++) {
			for (int z = 6; z <= 8; z++) {
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), x == 10 && z == 7 && y < 4 ? Blocks.AIR : Blocks.STONE_BRICKS);
				}
			}
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 7));
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Sieges.forget();
			Ladders.forget();
			sites.forgetFinished(placement);
			VillageHalls.RADIUS = radius;
			Expansions.openForTests = open;
			level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer());
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		// The Palisade Gate's fence gates, wherever the blueprint has them.
		List<BlockPos> gates = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(width - 1, build.size().getY() - 1, depth - 1))) {
			if (level.getBlockState(pos).getBlock() instanceof FenceGateBlock) {
				gates.add(pos.immutable());
			}
		}
		helper.assertTrue(!gates.isEmpty(), "the Palisade Gate has no fence gate");
		return new Yard(level, hall, culture, villager, gates);
	}

	/** One of the warband's raiders with {@code role} (from its roster or its extras), as a raid spawns it, at (x, 2, z). */
	private static Mob raider(GameTestHelper helper, Yard yard, Culture.Role role, double x, double z) {
		ServerLevel level = yard.level();
		List<Culture.Member> members = new ArrayList<>(yard.culture().roster());
		yard.culture().extras().forEach(e -> members.add(e.member()));
		Culture.Member member = members.stream().filter(m -> m.role() == role).findFirst().orElseThrow();
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

	private static Map<BlockPos, BlockState> blocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds();
		Map<BlockPos, BlockState> out = new HashMap<>();
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX - 1, box.maxY - 1, box.maxZ - 1))) {
			out.put(pos.immutable(), level.getBlockState(pos));
		}
		return out;
	}

	/** Ladders standing in the area that weren't in {@code before}. */
	private static List<BlockPos> newLadders(GameTestHelper helper, Map<BlockPos, BlockState> before) {
		ServerLevel level = helper.getLevel();
		List<BlockPos> out = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
			if (level.getBlockState(e.getKey()).is(Blocks.LADDER) && !e.getValue().is(Blocks.LADDER)) {
				out.add(e.getKey());
			}
		}
		return out;
	}

	private static double toGate(Mob mob, List<BlockPos> gates) {
		return gates.stream().mapToDouble(g -> mob.position().distanceTo(g.getCenter())).min().orElse(99);
	}

	/**
	 * {@code siegeDamage} off: the warband's raid on the walled village is still a siege (laid, gates shut), its ravager
	 * goes to the gate and pounds, yet every gate block keeps all its hit points and stands through 600 ticks; and the
	 * pillager still sets a ladder against the wall. Then {@code siegeDamage} is switched on mid-siege: the next blows
	 * cost the gate hit points.
	 */
	//$ gametest_ticks_batch AREA '1300' '"qaSiegeSwitchesWalled1123dDamage"'
	@GameTest(template = AREA, timeoutTicks = 1300, batch = "qaSiegeSwitchesWalled1123dDamage")
	public void qaSiegeDamageOffTheGateHoldsAndTheLaddersStillGoUp(GameTestHelper helper) {
		Yard yard = yard(helper);
		ServerLevel level = yard.level();
		Map<BlockPos, BlockState> before = blocks(helper);
		WorkplaceConfig.parse("{\"siegeDamage\": false}").apply();
		helper.assertTrue(Sieges.ENABLED && !Sieges.DAMAGE && !Sieges.damaging(level), "siegeDamage off isn't applied");
		Mob ram = raider(helper, yard, Culture.Role.RAM, 14.5, 21.5);
		Mob climber = raider(helper, yard, Culture.Role.CLIMBER, 8.5, 21.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(ram, climber));
		Optional<ThreatData.Siege> laid = Sieges.siege(level, yard.hall());
		helper.assertTrue(laid.isPresent() && !laid.get().over, "with siegeDamage off it is no longer a siege");
		ThreatData.Siege siege = laid.get();
		helper.assertTrue(!siege.gates.isEmpty(), "the siege lists no gate blocks");
		List<BlockPos> listed = siege.gates.stream().map(g -> g.pos).toList();
		helper.assertTrue(yard.gates().stream().anyMatch(listed::contains), "the Palisade Gate's fence gates " + yard.gates() + " aren't in the siege: " + listed);
		long[] seen = {-1, -1, 0}; // the ram at the gate since, the first rung, the ticks the ram stood at the gate
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (tick <= 600) {
				for (ThreatData.Gate gate : siege.gates) {
					int max = Sieges.hitPoints(before.get(gate.pos)) * siege.hpFactor;
					helper.assertTrue(level.getBlockState(gate.pos).equals(before.get(gate.pos)) || Sieges.isGate(level.getBlockState(gate.pos)),
						"with siegeDamage off the gate block at " + gate.pos + " is now " + level.getBlockState(gate.pos) + " (tick " + tick + ")");
					helper.assertTrue(Sieges.hitPointsLeft(level, yard.hall(), gate.pos) == max,
						"with siegeDamage off the gate at " + gate.pos + " has " + Sieges.hitPointsLeft(level, yard.hall(), gate.pos) + " of " + max + " (tick " + tick + ")");
				}
				helper.assertTrue(!siege.breached, "breached with siegeDamage off");
				if (ram.isAlive() && toGate(ram, listed) < 3.5) {
					seen[2]++;
					if (seen[0] < 0) {
						seen[0] = tick;
					}
				}
				if (seen[1] < 0 && !Ladders.rungs(level, yard.hall()).isEmpty()) {
					seen[1] = tick;
				}
			}
			if (tick == 600) {
				helper.assertTrue(seen[0] >= 0, "the ram never came to the gate with siegeDamage off: " + ram.position() + (ram.isAlive() ? "" : " dead"));
				helper.assertTrue(seen[2] >= 2 * Sieges.RAM_EVERY, "the ram stood at the gate only " + seen[2] + " ticks: not long enough for two blows");
				helper.assertTrue(seen[1] >= 0 || !newLadders(helper, before).isEmpty(),
					"siegeDamage off alone leaves the ladders, but none was set in 600 ticks; the climber is at " + climber.position());
				helper.assertTrue(Sieges.siege(level, yard.hall()).map(s -> !s.over).orElse(false), "the siege ended with the gates holding");
				// The control: switched on again, live, the blows cost hit points.
				WorkplaceConfig.parse("{}").apply();
				helper.assertTrue(Sieges.DAMAGE && Sieges.damaging(level), "siegeDamage on again isn't applied");
				if (!ram.isAlive()) {
					helper.fail("the ram died before the control");
				}
			}
			if (tick > 600) {
				boolean hurt = siege.gates.stream().anyMatch(g -> g.hp < Sieges.hitPoints(before.get(g.pos)) * siege.hpFactor);
				if (hurt) {
					helper.succeed();
				} else if (tick > 1200) {
					helper.fail("siegeDamage on again, but 600 ticks later no gate block lost a hit point; the ram is "
						+ String.format("%.1f", toGate(ram, listed)) + " from the gate");
				}
			}
		});
	}

	/**
	 * {@code sieges} off: the warband's raid on the walled village is a plain raid. The raid itself is on, but no siege is
	 * laid, no rung goes up against the wall in 400 ticks and every gate block stands as it was (no ram broke it, none was
	 * shut or opened by a siege). Then {@code sieges} on again: the next raid by the same village is a siege, its gates in
	 * the list, and within 600 ticks a ladder is set or the ram's blows cost the gate hit points.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaSiegeSwitchesWalled1123dOff"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaSiegeSwitchesWalled1123dOff")
	public void qaSiegesOffTheWarbandsRaidOnAWalledVillageIsPlain(GameTestHelper helper) {
		Yard yard = yard(helper);
		ServerLevel level = yard.level();
		Map<BlockPos, BlockState> before = blocks(helper);
		WorkplaceConfig.parse("{\"sieges\": false}").apply();
		helper.assertTrue(!Sieges.ENABLED, "sieges off isn't applied");
		Mob ram = raider(helper, yard, Culture.Role.RAM, 14.5, 21.5);
		Mob climber = raider(helper, yard, Culture.Role.CLIMBER, 8.5, 21.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(ram, climber));
		helper.assertTrue(VillageRaids.active(yard.hall()).isPresent(), "with sieges off the raid itself didn't come");
		Mob[] again = {null, null};
		ThreatData.Siege[] siege = {null};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (tick <= 400) {
				helper.assertTrue(Sieges.siege(level, yard.hall()).isEmpty(), "a siege with sieges off (tick " + tick + ")");
				helper.assertTrue(newLadders(helper, before).isEmpty() && Ladders.rungs(level, yard.hall()).isEmpty(),
					"a ladder with sieges off at tick " + tick + ": " + newLadders(helper, before));
				for (BlockPos gate : yard.gates()) {
					helper.assertTrue(level.getBlockState(gate).equals(before.get(gate)),
						"with sieges off the gate block at " + gate + " went from " + before.get(gate) + " to " + level.getBlockState(gate) + " (tick " + tick + ")");
				}
			}
			if (tick == 400) {
				helper.assertTrue(VillageRaids.active(yard.hall()).isPresent(), "the plain raid ended by itself");
				// The control: sieges on again, a new raid.
				ram.discard();
				climber.discard();
				VillageRaids.forget(yard.hall());
				ThreatData.get(level).forgetPast(yard.hall());
				WorkplaceConfig.parse("{}").apply();
				helper.assertTrue(Sieges.ENABLED, "sieges on again isn't applied");
				again[0] = raider(helper, yard, Culture.Role.RAM, 14.5, 21.5);
				again[1] = raider(helper, yard, Culture.Role.CLIMBER, 8.5, 21.5);
				VillageRaids.track(level, yard.hall(), yard.culture(), List.of(again[0], again[1]));
				Optional<ThreatData.Siege> laid = Sieges.siege(level, yard.hall());
				helper.assertTrue(laid.isPresent() && !laid.get().over, "sieges on again, but the walled village's next raid is no siege");
				siege[0] = laid.get();
				List<BlockPos> listed = siege[0].gates.stream().map(g -> g.pos).toList();
				helper.assertTrue(yard.gates().stream().anyMatch(listed::contains), "the gates aren't in the siege: " + listed);
			}
			if (tick > 400) {
				boolean hurt = siege[0].gates.stream().anyMatch(g -> g.hp < Sieges.hitPoints(before.get(g.pos)) * siege[0].hpFactor);
				boolean laddered = !Ladders.rungs(level, yard.hall()).isEmpty();
				if (hurt || laddered) {
					helper.succeed();
				} else if (tick > 1000) {
					helper.fail("sieges on again, but in 600 ticks no ladder went up and no gate lost a hit point");
				}
			}
		});
	}
}
