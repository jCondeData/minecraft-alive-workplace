package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Ladders;
import io.github.jcondedata.aliveworkplace.threat.Risings;
import io.github.jcondedata.aliveworkplace.threat.SandRamps;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;

/**
 * QA (2026-10-25, lane-d-1025-0108), ROADMAP 32.9 and 32.21, written from the items' text. The edges the feature tests
 * leave open: the Pharaoh's 24 blocks measured at both sides (a husk falling exactly 24 off rises, one a little farther
 * stays down); only husks rise, never a zombie or a skeleton beside him, nor the Pharaoh himself; two husks falling at
 * once both rise; the desert siege still on a moment before noon and over at noon itself; and at threat level 1 a desert
 * raid at the wall lays no siege and piles no sand (raiders walk in only).
 */
public class QaThreatsM32GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation DESERT = AliveWorkplace.id("desert_raiders");
	private static final ResourceLocation PILERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_m32_sand_pilers");
	private static final BlockPos HALL = new BlockPos(14, 2, 8);
	private static final int WEST = 5;
	private static final int EAST = 23;
	private static final int BACK = 4;
	private static final int FRONT = 15;

	private static Culture desert(GameTestHelper helper) {
		Optional<Culture> culture = Threats.get(DESERT);
		helper.assertTrue(culture.isPresent(), "desert_raiders.json didn't load");
		return culture.get();
	}

	/** Husk climbers who pile sand, loaded for the test only. */
	private static Culture pilers(GameTestHelper helper) {
		Culture culture = Culture.read(PILERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 60, \"role\": \"climber\"}, {\"entity\": \"minecraft:husk\", \"share\": 40, \"role\": \"melee\"}],"
			+ " \"hours\": \"until_noon\", \"tactics\": [\"sand_ramps\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new java.util.LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new java.util.LinkedHashMap<>(loaded);
		with.put(PILERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> Threats.setForTest(loaded));
		return culture;
	}

	/** Stone brick walls four high round a yard, a finished Stone Wall in the front one, a villager shut in a cell. */
	private static Villager yard(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		VillageHalls.RADIUS = 20;
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
		level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
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
		BlockPos origin = helper.absolutePos(new BlockPos(11, 2, FRONT - 2));
		level.getStructureManager().get(StarterBlueprints.STONE_WALL.id()).orElseThrow().placeInWorld(level, origin, origin,
			new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(StarterBlueprints.STONE_WALL.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		for (int x = 9; x <= 11; x++) {
			for (int z = 6; z <= 8; z++) {
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), x == 10 && z == 7 && y < 4 ? Blocks.AIR : Blocks.STONE_BRICKS);
				}
			}
		}
		return helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 7));
	}

	/** A raider of {@code culture} in {@code role} at (x, 2, z), the same one every run. */
	private static Mob raider(GameTestHelper helper, Culture culture, Villager villager, Culture.Role role, double x, double z) {
		ServerLevel level = helper.getLevel();
		Culture.Member member = culture.roster().stream().filter(m -> m.role() == role).findFirst().orElseThrow();
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob != null, "no " + member.entity());
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		mob.moveTo(base.getX() + x, base.getY() + 2, base.getZ() + z, 180f, 0f);
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
		Threats.outfit(level, mob, culture, member);
		mob.setTarget(villager);
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

	/** The Pharaoh (a husk marked as the desert raiders' captain, standing still) at the middle of block {@code at}. */
	private static Husk pharaoh(GameTestHelper helper, BlockPos at) {
		Husk pharaoh = helper.spawn(EntityType.HUSK, at);
		pharaoh.setNoAi(true);
		Risings.mark(pharaoh, desert(helper).captain().orElseThrow());
		helper.assertTrue(pharaoh.getTags().contains(Risings.RAISER_TAG), "the Pharaoh isn't marked");
		return pharaoh;
	}

	private static <T extends Mob> T still(GameTestHelper helper, EntityType<T> type, BlockPos at, double dx, double dz) {
		T mob = helper.spawn(type, at);
		mob.setNoAi(true);
		mob.addTag(VillageRaids.TAG);
		mob.moveTo(mob.getX() + dx, mob.getY(), mob.getZ() + dz, 0f, 0f);
		return mob;
	}

	private static void pharaohLeftovers(GameTestHelper helper) {
		Leftovers.clear(helper);
		Risings.forget();
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds();
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, box.inflate(8), m -> m instanceof Zombie || m instanceof Skeleton).forEach(Mob::discard);
			Risings.forget();
		});
	}

	/**
	 * "A husk that falls within 24 blocks of him rises": both sides of the 24. The Pharaoh stands at one corner; a husk
	 * killed exactly 24 blocks east of him rises after 3 s, one killed 24.2 blocks south of him stays down.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaM32Pharaoh24"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaM32Pharaoh24")
	public void aHuskFalling24BlocksFromThePharaohRisesAndOneFartherDoesNot(GameTestHelper helper) {
		pharaohLeftovers(helper);
		ServerLevel level = helper.getLevel();
		Husk pharaoh = pharaoh(helper, new BlockPos(2, 2, 2));
		Husk at24 = still(helper, EntityType.HUSK, new BlockPos(26, 2, 2), 0, 0);
		Husk past = still(helper, EntityType.HUSK, new BlockPos(2, 2, 26), 0, 0.2);
		helper.assertTrue(Math.abs(at24.distanceTo(pharaoh) - 24) < 1e-6, "the near husk isn't 24 off: " + at24.distanceTo(pharaoh));
		helper.assertTrue(past.distanceTo(pharaoh) > 24.1 && past.distanceTo(pharaoh) < 24.3, "the far husk isn't 24.2 off: " + past.distanceTo(pharaoh));
		BlockPos nearAt = at24.blockPosition();
		BlockPos farAt = past.blockPosition();
		at24.kill();
		past.kill();
		AABB box = helper.getBounds();
		Supplier<List<Husk>> risen = () -> level.getEntitiesOfClass(Husk.class, box.inflate(2), h -> h.isAlive() && h.getTags().contains(Risings.RISEN_TAG));
		helper.runAfterDelay(80, () -> {
			List<Husk> up = risen.get();
			helper.assertTrue(up.stream().anyMatch(h -> h.blockPosition().closerThan(nearAt, 1.5)), "the husk that fell exactly 24 blocks from the Pharaoh didn't rise");
			helper.assertFalse(up.stream().anyMatch(h -> h.blockPosition().closerThan(farAt, 1.5)), "the husk that fell 24.2 blocks from the Pharaoh rose");
			helper.assertTrue(up.size() == 1, "risen husks: " + up.size());
			helper.succeed();
		});
	}

	/**
	 * Only husks rise, and the Pharaoh never does himself: a zombie and a skeleton killed beside him stay dead, and so
	 * does he; two husks killed in the same tick beside him both rise, each where it fell.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaM32PharaohKinds"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaM32PharaohKinds")
	public void onlyHusksRiseNeverAZombieASkeletonOrThePharaoh(GameTestHelper helper) {
		pharaohLeftovers(helper);
		ServerLevel level = helper.getLevel();
		Husk pharaoh = pharaoh(helper, new BlockPos(14, 2, 14));
		Zombie zombie = still(helper, EntityType.ZOMBIE, new BlockPos(17, 2, 14), 0, 0);
		Skeleton skeleton = still(helper, EntityType.SKELETON, new BlockPos(11, 2, 14), 0, 0);
		Husk one = still(helper, EntityType.HUSK, new BlockPos(14, 2, 18), 0, 0);
		Husk two = still(helper, EntityType.HUSK, new BlockPos(14, 2, 10), 0, 0);
		BlockPos oneAt = one.blockPosition();
		BlockPos twoAt = two.blockPosition();
		zombie.kill();
		skeleton.kill();
		helper.assertTrue(Risings.pending() == 0, "a zombie or a skeleton is waiting to rise beside the Pharaoh: " + Risings.pending());
		one.kill();
		two.kill();
		helper.assertTrue(Risings.pending() == 2, "two husks fell beside the Pharaoh, waiting to rise: " + Risings.pending());
		AABB box = helper.getBounds().inflate(2);
		helper.runAfterDelay(70, () -> {
			List<Husk> up = level.getEntitiesOfClass(Husk.class, box, h -> h.isAlive() && h.getTags().contains(Risings.RISEN_TAG));
			helper.assertTrue(up.size() == 2 && up.stream().anyMatch(h -> h.blockPosition().closerThan(oneAt, 1.5))
				&& up.stream().anyMatch(h -> h.blockPosition().closerThan(twoAt, 1.5)), "the two husks didn't both rise where they fell: " + up.size());
			helper.assertTrue(level.getEntitiesOfClass(Zombie.class, box, z -> z.isAlive() && !(z instanceof Husk)).isEmpty()
				&& level.getEntitiesOfClass(Skeleton.class, box, Mob::isAlive).isEmpty(), "a zombie or a skeleton got up again");
			// A risen husk standing by: the Pharaoh himself, killed, doesn't rise.
			pharaoh.kill();
			helper.assertTrue(Risings.pending() == 0, "the Pharaoh is waiting to rise");
		});
		helper.runAfterDelay(150, () -> {
			helper.assertTrue(level.getEntitiesOfClass(Husk.class, box, h -> h.isAlive() && h.getTags().contains(Risings.RAISER_TAG)).isEmpty(),
				"the Pharaoh rose");
			helper.succeed();
		});
	}

	/**
	 * Until noon, at its edge: a desert siege is still on at 5 900 (a moment before noon) and over at 6 000, noon itself.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaM32Noon"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaM32Noon")
	public void theDesertSiegeIsOnJustBeforeNoonAndOverAtNoon(GameTestHelper helper) {
		Culture culture = desert(helper);
		Villager villager = yard(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		long day = level.getDayTime() / 24000 + 1;
		level.setDayTime(day * 24000 + 15000);
		Mob husk = raider(helper, culture, villager, Culture.Role.CLIMBER, 14.5, 23.5);
		husk.setNoAi(true);
		VillageRaids.track(level, hall, culture, List.of(husk));
		helper.assertTrue(Sieges.siege(level, hall).isPresent(), "the desert raid laid no siege");
		// The raid began a while ago, as a save of one has it.
		ThreatData data = ThreatData.get(level);
		CompoundTag tag = data.save(new CompoundTag(), level.registryAccess());
		net.minecraft.nbt.ListTag raids = tag.getList("raids", Tag.TAG_COMPOUND);
		boolean found = false;
		for (int i = 0; i < raids.size(); i++) {
			if (raids.getCompound(i).getLong("hall") == hall.asLong()) {
				raids.getCompound(i).putLong("began", level.getGameTime() - 2000);
				found = true;
			}
		}
		helper.assertTrue(found, "the desert raid isn't saved: " + raids);
		data.read(tag);
		helper.runAfterDelay(2, () -> {
			level.setDayTime(day * 24000 + 24000 + 5900);
			VillageRaids.tick(level, hall, 8, 0, day, d -> { });
			helper.assertTrue(VillageRaids.active(hall).isPresent(), "the desert raiders left before noon (at 5900)");
			helper.assertTrue(Sieges.siege(level, hall).map(s -> !s.over).orElse(false), "the siege is over before noon (at 5900)");
			level.setDayTime(day * 24000 + 24000 + 6000);
			VillageRaids.tick(level, hall, 8, 0, day, d -> { });
			helper.assertTrue(VillageRaids.active(hall).isEmpty(), "the desert raid is still on at noon (6000)");
			helper.assertTrue(Sieges.siege(level, hall).map(s -> s.over).orElse(true), "the siege isn't over at noon (6000)");
			helper.succeed();
		});
	}

	/**
	 * Threat level 1, gentle: raiders walk in only. A husk climber of a sand-piling culture comes to the wall (as the
	 * ramp tests' climber does, which piles sand within a few seconds): no siege is laid and no block of the yard changes.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaM32Level1Sand"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaM32Level1Sand")
	public void atThreatLevelOneADesertClimberPilesNoSand(GameTestHelper helper) {
		Culture culture = pilers(helper);
		Villager villager = yard(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		WorkplaceConfig.parse("{\"threatLevel\": 1}").apply();
		Map<BlockPos, BlockState> before = blocks(helper);
		Mob climber = raider(helper, culture, villager, Culture.Role.CLIMBER, 14.5, 23.5);
		VillageRaids.track(level, hall, culture, List.of(climber));
		helper.assertTrue(VillageRaids.active(hall).isPresent(), "the raid didn't come at level 1 (it should, without a siege)");
		helper.onEachTick(() -> {
			helper.assertTrue(Sieges.siege(level, hall).isEmpty(), "a siege was laid at threat level 1");
			helper.assertTrue(SandRamps.sand(level, hall).isEmpty(), "sand was piled at threat level 1: " + SandRamps.sand(level, hall));
			if (helper.getTick() >= 250) {
				List<String> changed = changes(helper, before);
				helper.assertTrue(changed.isEmpty(), "the yard changed at threat level 1: " + changed);
				helper.assertTrue(climber.isAlive(), "the climber is gone (test setup)");
				helper.succeed();
			}
		});
	}
}
