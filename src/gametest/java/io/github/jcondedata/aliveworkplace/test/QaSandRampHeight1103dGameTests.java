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
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * QA (lane D, 2026-11-03) for sand ramps (ROADMAP 32.9, threat/SandRamps): "a wall is whatever stands at least two
 * blocks high in the way (up to {@link SandRamps#MAX_HEIGHT})". The yard of DesertRaiderGameTests, but every wall, the
 * front one too, solid stone bricks {@code h} high (the finished Stone Wall the siege needs stands inside the yard): at
 * the highest wall a ramp may be piled against, a husk climber begins one; at one block higher it never piles a grain.
 * Found by a local mutation run (the wall-height loop's {@code <=} turned {@code <} survived the threat tests).
 */
public class QaSandRampHeight1103dGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation PILERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_sand_height_pilers");
	private static final BlockPos HALL = new BlockPos(14, 2, 8);
	private static final int WEST = 5;
	private static final int EAST = 23;
	private static final int BACK = 4;
	private static final int FRONT = 15;

	private record Yard(ServerLevel level, BlockPos hall, Culture culture, Villager villager) {
	}

	/** The test culture: husk climbers who pile sand (loaded for the test only). */
	private static Culture pilers(GameTestHelper helper) {
		Culture culture = Culture.read(PILERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 60, \"role\": \"climber\"}, {\"entity\": \"minecraft:husk\", \"share\": 40, \"role\": \"melee\"}],"
			+ " \"hours\": \"until_noon\", \"tactics\": [\"sand_ramps\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new LinkedHashMap<>(loaded);
		with.put(PILERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> Threats.setForTest(loaded));
		return culture;
	}

	/** Walls {@code high} blocks of stone bricks all round, a finished Stone Wall inside, a villager in a closed cell. */
	private static Yard yard(GameTestHelper helper, int high) {
		return yard(helper, high, false);
	}

	/**
	 * The yard; with {@code ledge}, a row of stone bricks juts out over every wall's whole outer foot two above the
	 * wall's top (a gallery over the wall's foot, as Stone Wall II's hoardings have), so a ramp's top has no room.
	 */
	private static Yard yard(GameTestHelper helper, int high, boolean ledge) {
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
		if (ledge) {
			// all round, so a husk that goes round to a side wall finds no room either
			for (int x = WEST - 1; x <= EAST + 1; x++) {
				helper.setBlock(new BlockPos(x, 2 + high + 1, FRONT + 1), Blocks.STONE_BRICKS);
				helper.setBlock(new BlockPos(x, 2 + high + 1, BACK - 1), Blocks.STONE_BRICKS);
			}
			for (int z = BACK - 1; z <= FRONT + 1; z++) {
				helper.setBlock(new BlockPos(WEST - 1, 2 + high + 1, z), Blocks.STONE_BRICKS);
				helper.setBlock(new BlockPos(EAST + 1, 2 + high + 1, z), Blocks.STONE_BRICKS);
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
		return new Yard(level, hall, pilers(helper), villager);
	}

	private static Mob climber(GameTestHelper helper, Yard yard) {
		ServerLevel level = yard.level();
		Culture.Member member = yard.culture().roster().stream().filter(m -> m.role() == Culture.Role.CLIMBER).findFirst().orElseThrow();
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob != null, "no " + member.entity());
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		mob.moveTo(base.getX() + 14.5, base.getY() + 2, base.getZ() + 23.5, 180f, 0f);
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
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(mob));
		return mob;
	}

	/** Against a front wall exactly {@link SandRamps#MAX_HEIGHT} high, the climber begins a ramp and piles sand at its foot. */
	//$ gametest_ticks_batch AREA '600' '"qaSandHeightMax1103d"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaSandHeightMax1103d")
	public void aRampIsBegunAgainstAWallAsHighAsTheMost(GameTestHelper helper) {
		Yard yard = yard(helper, SandRamps.MAX_HEIGHT);
		ServerLevel level = yard.level();
		Mob climber = climber(helper, yard);
		helper.onEachTick(() -> {
			Optional<ThreatData.Siege> siege = Sieges.siege(level, yard.hall());
			if (siege.isPresent() && !siege.get().rampFeet.isEmpty() && SandRamps.sand(level, yard.hall()).size() >= 3) {
				ThreatData.Siege s = siege.get();
				BlockPos foot = s.rampFeet.get(0);
				Direction into = Direction.from3DDataValue(s.rampDirs.get(0));
				BlockPos wall = foot.relative(into);
				helper.assertTrue(level.getBlockState(wall).is(Blocks.STONE_BRICKS) && level.getBlockState(wall.above(SandRamps.MAX_HEIGHT - 1)).is(Blocks.STONE_BRICKS)
					&& level.getBlockState(wall.above(SandRamps.MAX_HEIGHT)).isAir(), "the ramp isn't against the " + SandRamps.MAX_HEIGHT + "-high wall: foot "
					+ foot.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString() + " into " + into);
				helper.succeed();
				return;
			}
			if (helper.getTick() > 560) {
				helper.fail("no ramp begun against a wall " + SandRamps.MAX_HEIGHT + " high: siege " + siege.isPresent()
					+ ", feet " + siege.map(s -> s.rampFeet.toString()).orElse("-") + ", sand " + SandRamps.sand(level, yard.hall()).size()
					+ ", the husk at " + climber.position() + (climber.isAlive() ? "" : " (dead)"));
			}
		});
	}

	/** Against walls one block higher than that, no ramp is begun and not a grain of sand is piled. */
	//$ gametest_ticks_batch AREA '600' '"qaSandHeightOver1103d"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaSandHeightOver1103d")
	public void noSandAgainstAWallOneHigherThanTheMost(GameTestHelper helper) {
		noRamp(helper, yard(helper, SandRamps.MAX_HEIGHT + 1), "a wall " + (SandRamps.MAX_HEIGHT + 1) + " high");
	}

	/**
	 * Walls four high with a ledge over their whole outer foot two above its top: the ramp's top would have no room to
	 * stand ("no room on top"), so no ramp is begun and no sand piled.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaSandLedge1103d"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaSandLedge1103d")
	public void noSandUnderALedgeOverTheWallsFoot(GameTestHelper helper) {
		noRamp(helper, yard(helper, 4, true), "a wall with a ledge over its foot");
	}

	private static void noRamp(GameTestHelper helper, Yard yard, String what) {
		ServerLevel level = yard.level();
		Mob climber = climber(helper, yard);
		helper.onEachTick(() -> {
			Optional<ThreatData.Siege> siege = Sieges.siege(level, yard.hall());
			if (siege.isPresent()) {
				helper.assertTrue(siege.get().rampFeet.isEmpty() && siege.get().sand.isEmpty(), "a ramp against " + what + ": feet " + siege.get().rampFeet + ", sand " + siege.get().sand.size());
			}
			if (helper.getTick() == 500) {
				helper.assertTrue(siege.isPresent(), "no siege (the test would prove nothing)");
				helper.assertTrue(climber.isAlive(), "the climber died (the test would prove nothing)");
				helper.succeed();
			}
		});
	}
}
