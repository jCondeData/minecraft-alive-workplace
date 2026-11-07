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
import net.minecraft.world.phys.AABB;import net.minecraft.world.phys.AABB;

/**
 * QA (lane-d-1107-1008), from Sieges II's spec (ROADMAP 32.5, {@link Ladders}): "a guard standing at least two blocks
 * above a ladder's foot and within {@link Ladders#THROW_REACH} blocks of its top rung throws it down". The happy path is
 * LadderGameTests' guard on the walkway, four above the foot; here the edges of who throws: a guard exactly two above
 * the foot throws it, and a guard one above it (on a step by the foot), a farmer and a guard's child on the walkway
 * don't, so the climber gets over. The yard is LadderGameTests' own (copied: its helpers are private).
 */
public class QaLadderThrow1107dGameTests implements FabricGameTest {
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


	/** A villager of {@code profession} standing at the helper position {@code at}, still (no AI), a child if asked. */
	private static Villager stander(GameTestHelper helper, BlockPos at, net.minecraft.world.entity.npc.VillagerProfession profession, boolean child) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setVillagerData(v.getVillagerData().setProfession(profession));
		if (child) {
			v.setAge(-24000);
		}
		v.setNoAi(true);
		v.setInvulnerable(true);
		return v;
	}

	/**
	 * The ladder finished, a guard steps onto a two-block pillar beside its foot (his feet exactly two above the foot,
	 * well within reach of the top rung): he throws it down within 40 ticks.
	 */
	//$ gametest_ticks_batch AREA '700' '"qaLadderThrowTwoUp1107d"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaLadderThrowTwoUp1107d")
	public void aGuardExactlyTwoAboveTheFootThrowsTheLadderDown(GameTestHelper helper) {
		Yard yard = yard(helper);
		ServerLevel level = yard.level();
		Mob climber = raider(helper, yard, Culture.Role.CLIMBER, 14.5, 21.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(climber));
		BlockPos foot = helper.absolutePos(FOOT);
		long[] up = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (up[0] < 0) {
				if (tick > 550) {
					helper.fail("the ladder was never finished: " + Ladders.rungs(level, yard.hall()).size() + " rungs, the climber at " + climber.position());
				}
				if (Ladders.rungs(level, yard.hall()).size() == RUNGS) {
					up[0] = tick;
					helper.setBlock(new BlockPos(12, 2, FRONT + 2), Blocks.STONE_BRICKS);
					helper.setBlock(new BlockPos(12, 3, FRONT + 2), Blocks.STONE_BRICKS);
					Villager guard = stander(helper, new BlockPos(12, 4, FRONT + 2), ModVillagers.GUARD, false);
					helper.assertTrue(guard.getY() == foot.getY() + 2, "setup: the guard stands " + (guard.getY() - foot.getY()) + " above the foot, not 2");
				}
				return;
			}
			if (Ladders.thrown(level, yard.hall()) > 0) {
				helper.assertTrue(Ladders.rungs(level, yard.hall()).isEmpty(), "thrown down, yet rungs stand: " + Ladders.rungs(level, yard.hall()));
				helper.succeed();
				return;
			}
			if (tick > up[0] + 40) {
				helper.fail("a guard exactly two blocks above the ladder's foot, beside it, didn't throw it down in 40 ticks ("
					+ Ladders.rungs(level, yard.hall()).size() + " rungs)");
			}
		});
	}

	/**
	 * The ladder finished, a guard on a one-block step by its foot (one above it, inside reach of the top rung), a farmer
	 * on the walkway and a guard's child on the walkway: none of them may throw it, and the climber gets over.
	 */
	//$ gametest_ticks_batch AREA '700' '"qaLadderThrowNobody1107d"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaLadderThrowNobody1107d")
	public void aGuardOneAboveTheFootAFarmerAndAChildLeaveTheLadderUp(GameTestHelper helper) {
		Yard yard = yard(helper);
		ServerLevel level = yard.level();
		Mob climber = raider(helper, yard, Culture.Role.CLIMBER, 14.5, 21.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(climber));
		long[] up = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (up[0] < 0) {
				if (tick > 550) {
					helper.fail("the ladder was never finished: " + Ladders.rungs(level, yard.hall()).size() + " rungs, the climber at " + climber.position());
				}
				if (Ladders.rungs(level, yard.hall()).size() == RUNGS) {
					up[0] = tick;
					helper.setBlock(new BlockPos(12, 2, FRONT + 2), Blocks.STONE_BRICKS);
					stander(helper, new BlockPos(12, 3, FRONT + 2), ModVillagers.GUARD, false);
					stander(helper, new BlockPos(12, 6, FRONT - 1), net.minecraft.world.entity.npc.VillagerProfession.FARMER, false);
					stander(helper, new BlockPos(16, 6, FRONT - 1), ModVillagers.GUARD, true);
				}
				return;
			}
			helper.assertTrue(Ladders.thrown(level, yard.hall()) == 0, "the ladder was thrown down by a guard one above its foot, a farmer or a child");
			if (inside(helper, climber) || Ladders.over(level, yard.hall(), climber.getUUID())) {
				helper.succeed();
			}
		});
	}
}
