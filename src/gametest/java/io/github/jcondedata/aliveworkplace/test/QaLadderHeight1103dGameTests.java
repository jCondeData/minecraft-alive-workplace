package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Ladders;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.LinkedHashMap;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane D, 2026-11-03) for siege ladders (ROADMAP 32.5, threat/Ladders): "a rung every EVERY ticks while a climber
 * stands at its foot, up to {@link Ladders#MAX_HEIGHT} high". The yard of LadderGameTests, but every wall solid stone
 * bricks {@code h} high (the finished Stone Wall the siege needs stands inside): a pillager climber sets a ladder up a
 * wall exactly that high and none up one a block higher; and a wall built higher than a ladder reaches mid-siege takes
 * the ladder down at once, nothing dropped ("the ladders as the world has them now"). Found by a local mutation run
 * (the height loop's {@code <=} turned {@code <}, and the trim of a ladder whose wall changed left out, both survived).
 */
public class QaLadderHeight1103dGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation CLIMBERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_ladder_height_climbers");
	private static final BlockPos HALL = new BlockPos(14, 2, 8);
	private static final int WEST = 5;
	private static final int EAST = 23;
	private static final int BACK = 4;
	private static final int FRONT = 15;

	private record Yard(ServerLevel level, BlockPos hall, Culture culture, Villager villager) {
	}

	/** The test culture: pillager climbers who set ladders, and vindicators (loaded for the test only). */
	private static Culture climbers(GameTestHelper helper) {
		Culture culture = Culture.read(CLIMBERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:pillager\", \"share\": 60, \"role\": \"climber\"}, {\"entity\": \"minecraft:vindicator\", \"share\": 40, \"role\": \"melee\"}],"
			+ " \"tactics\": [\"ladders\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new LinkedHashMap<>(loaded);
		with.put(CLIMBERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> Threats.setForTest(loaded));
		return culture;
	}

	/** Walls {@code high} blocks of stone bricks all round, a finished Stone Wall inside, a villager in a closed cell. */
	private static Yard yard(GameTestHelper helper, int high) {
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
			VillageHalls.RADIUS = radius;
			level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer());
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		for (int y = 2; y < 2 + high; y++) {
			for (int x = WEST; x <= EAST; x++) {
				helper.setBlock(new BlockPos(x, y, BACK), Blocks.STONE_BRICKS);
				helper.setBlock(new BlockPos(x, y, FRONT), Blocks.STONE_BRICKS);
			}
			for (int z = BACK; z <= FRONT; z++) {
				helper.setBlock(new BlockPos(WEST, y, z), Blocks.STONE_BRICKS);
				helper.setBlock(new BlockPos(EAST, y, z), Blocks.STONE_BRICKS);
			}
		}
		// The village's finished wall build (a siege needs one), inside the yard, away from the front wall.
		BlockPos origin = helper.absolutePos(new BlockPos(14, 2, 10));
		level.getStructureManager().get(StarterBlueprints.STONE_WALL.id()).orElseThrow().placeInWorld(level, origin, origin,
			new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(StarterBlueprints.STONE_WALL.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		for (int x = 8; x <= 10; x++) {
			for (int z = 6; z <= 8; z++) {
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), x == 9 && z == 7 && y < 4 ? Blocks.AIR : Blocks.STONE_BRICKS);
				}
			}
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 7));
		return new Yard(level, hall, climbers(helper), villager);
	}

	private static Mob climber(GameTestHelper helper, Yard yard) {
		ServerLevel level = yard.level();
		Culture.Member member = yard.culture().roster().stream().filter(m -> m.role() == Culture.Role.CLIMBER).findFirst().orElseThrow();
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob != null, "no " + member.entity());
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		mob.moveTo(base.getX() + 14.5, base.getY() + 2, base.getZ() + 23.5, 180f, 0f);
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
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(mob));
		return mob;
	}

	/** The lowest rung of the siege's ladders, or null. */
	private static BlockPos foot(ThreatData.Siege siege) {
		return siege.ladders.stream().min(java.util.Comparator.comparingInt(BlockPos::getY)).orElse(null);
	}

	private static long laddersStanding(GameTestHelper helper) {
		AABB box = helper.getBounds();
		long n = 0;
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX - 1, box.maxY - 1, box.maxZ - 1))) {
			if (helper.getLevel().getBlockState(p).is(Blocks.LADDER)) {
				n++;
			}
		}
		return n;
	}

	/** Against walls exactly {@link Ladders#MAX_HEIGHT} high, the climber sets a ladder up the outer face of one. */
	//$ gametest_ticks_batch AREA '600' '"qaLadderHeightMax1103d"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaLadderHeightMax1103d")
	public void aLadderGoesUpAWallAsHighAsTheMost(GameTestHelper helper) {
		Yard yard = yard(helper, Ladders.MAX_HEIGHT);
		ServerLevel level = yard.level();
		Mob climber = climber(helper, yard);
		helper.onEachTick(() -> {
			Optional<ThreatData.Siege> siege = Sieges.siege(level, yard.hall());
			if (siege.isPresent() && siege.get().ladders.size() >= 2) {
				BlockPos foot = foot(siege.get());
				BlockState rung = level.getBlockState(foot);
				helper.assertTrue(rung.is(Blocks.LADDER), "the recorded rung isn't a ladder: " + rung);
				BlockPos wall = foot.relative(rung.getValue(LadderBlock.FACING).getOpposite());
				helper.assertTrue(level.getBlockState(wall).is(Blocks.STONE_BRICKS) && level.getBlockState(wall.above(Ladders.MAX_HEIGHT - 1)).is(Blocks.STONE_BRICKS)
					&& level.getBlockState(wall.above(Ladders.MAX_HEIGHT)).isAir(), "the ladder isn't against the " + Ladders.MAX_HEIGHT + "-high wall: foot "
					+ foot.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString());
				helper.succeed();
				return;
			}
			if (helper.getTick() > 560) {
				helper.fail("no ladder against walls " + Ladders.MAX_HEIGHT + " high: siege " + siege.isPresent()
					+ ", rungs " + siege.map(s -> s.ladders.size()).orElse(0) + ", the climber at " + climber.position() + (climber.isAlive() ? "" : " (dead)"));
			}
		});
	}

	/** Against walls one block higher than that, not a rung is set. */
	//$ gametest_ticks_batch AREA '600' '"qaLadderHeightOver1103d"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaLadderHeightOver1103d")
	public void noLadderUpAWallOneHigherThanTheMost(GameTestHelper helper) {
		Yard yard = yard(helper, Ladders.MAX_HEIGHT + 1);
		ServerLevel level = yard.level();
		long own = laddersStanding(helper); // the Stone Wall's own ladders
		Mob climber = climber(helper, yard);
		helper.onEachTick(() -> {
			Optional<ThreatData.Siege> siege = Sieges.siege(level, yard.hall());
			if (siege.isPresent()) {
				helper.assertTrue(siege.get().ladders.isEmpty() && laddersStanding(helper) == own, "a ladder against walls " + (Ladders.MAX_HEIGHT + 1)
					+ " high: rungs " + siege.get().ladders);
			}
			if (helper.getTick() == 500) {
				helper.assertTrue(siege.isPresent(), "no siege (the test would prove nothing)");
				helper.assertTrue(climber.isAlive(), "the climber died (the test would prove nothing)");
				helper.succeed();
			}
		});
	}

	/**
	 * Walls four high; once the ladder has two rungs a player builds every wall up to 12 high, more than a ladder
	 * reaches: within two seconds the ladder is gone from the wall and from the siege's list, and no ladder item lies
	 * about (nothing drops).
	 */
	//$ gametest_ticks_batch AREA '700' '"qaLadderWallRaised1103d"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaLadderWallRaised1103d")
	public void aWallBuiltHigherThanTheLadderTakesItDown(GameTestHelper helper) {
		Yard yard = yard(helper, 4);
		ServerLevel level = yard.level();
		long own = laddersStanding(helper); // the Stone Wall's own ladders
		Mob climber = climber(helper, yard);
		long[] raised = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			Optional<ThreatData.Siege> siege = Sieges.siege(level, yard.hall());
			if (raised[0] < 0) {
				if (siege.isPresent() && siege.get().ladders.size() >= 2) {
					raised[0] = tick;
					for (int y = 6; y < 14; y++) {
						for (int x = WEST; x <= EAST; x++) {
							helper.setBlock(new BlockPos(x, y, BACK), Blocks.STONE_BRICKS);
							helper.setBlock(new BlockPos(x, y, FRONT), Blocks.STONE_BRICKS);
						}
						for (int z = BACK; z <= FRONT; z++) {
							helper.setBlock(new BlockPos(WEST, y, z), Blocks.STONE_BRICKS);
							helper.setBlock(new BlockPos(EAST, y, z), Blocks.STONE_BRICKS);
						}
					}
				} else if (tick > 500) {
					helper.fail("no ladder to start with: rungs " + siege.map(s -> s.ladders.size()).orElse(0) + ", the climber at " + climber.position());
				}
				return;
			}
			if (tick == raised[0] + 40) {
				helper.assertTrue(siege.isPresent(), "the siege ended (the test would prove nothing)");
				helper.assertTrue(siege.get().ladders.isEmpty(), "rungs still recorded after the wall went up out of reach: " + siege.get().ladders);
				helper.assertTrue(laddersStanding(helper) == own, (laddersStanding(helper) - own) + " rungs still stand against a wall out of reach");
				List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4), e -> e.getItem().is(Items.LADDER));
				helper.assertTrue(dropped.isEmpty(), "ladders dropped: " + dropped);
				helper.succeed();
			}
		});
	}
}
