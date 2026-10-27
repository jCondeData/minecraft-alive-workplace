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
import io.github.jcondedata.aliveworkplace.threat.SandRamps;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-d-1027-1909), two survivors of the 2026-10-27 nightly mutation run in the threats code, each tested from its
 * spec:
 * <ul>
 * <li>Threats.java:416 ({@code extra > 0} made {@code >= 0}): a captain's file gives him extra health over his mob's
 * own (31.13, 32.x); a file that gives none leaves him at his mob's health with no bonus of ours on him, and vanilla's
 * leader-zombie bonus is still taken off (B579).</li>
 * <li>SandRamps.java:313 ({@code builder == null ||} made {@code &&}): 32.9, sand goes up on a ramp only while a climber
 * stands by it. A ramp site recorded by the siege, with no climber left anywhere near it, gets no sand at all.</li>
 * </ul>
 */
public class QaThreatSurvivors1027GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final ResourceLocation PILERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_sand_nobody_pilers");
	private static final BlockPos HALL = new BlockPos(14, 2, 8);
	private static final int WEST = 5;
	private static final int EAST = 23;
	private static final int BACK = 4;
	private static final int FRONT = 15;

	/**
	 * A captain whose file gives him no extra health (0) keeps exactly his mob's own health: a leader drowned vanilla
	 * made (with its leader bonus) is brought back to a plain drowned's health, and no modifier of ours is on him.
	 */
	//$ gametest_ticks_batch BIG '40' '"qaCaptainNoExtra"'
	@GameTest(template = BIG, timeoutTicks = 40, batch = "qaCaptainNoExtra")
	public void aCaptainWhoseFileGivesNoExtraHealthHasHisMobsOwn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		double base = EntityType.DROWNED.create(level).getMaxHealth();
		DifficultyInstance hardest = new DifficultyInstance(Difficulty.HARD, 3_600_000L, 3_600_000L, 1f);
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 2, 4.5));
		Mob leader = null;
		for (int i = 0; i < 2000 && leader == null; i++) {
			Mob drowned = EntityType.DROWNED.create(level);
			drowned.moveTo(at.x, at.y, at.z, 0f, 0f);
			drowned.finalizeSpawn(level, hardest, MobSpawnType.EVENT, new Zombie.ZombieGroupData(false, false));
			if (drowned.getMaxHealth() > base + 0.01) {
				leader = drowned;
			} else {
				drowned.discard();
			}
		}
		helper.assertTrue(leader != null, "vanilla made no leader drowned in 2000 at the hardest difficulty");
		ResourceLocation id = AliveWorkplace.id("bandit_chief");
		Threats.captainHealth(leader, id, 0);
		helper.assertTrue(Math.abs(leader.getMaxHealth() - base) < 0.01,
			"a captain with 0 extra health in his file has " + leader.getMaxHealth() + " health, not his mob's own " + base);
		helper.assertTrue(leader.getAttribute(Attributes.MAX_HEALTH).getModifier(id) == null,
			"a captain with 0 extra health carries a health bonus of ours anyway");
		helper.assertTrue(leader.getHealth() == leader.getMaxHealth(), "the captain isn't healed to his max: " + leader.getHealth());
		// And a plain drowned given 0: unchanged, nothing added.
		Mob plain = EntityType.DROWNED.create(level);
		plain.moveTo(at.x, at.y, at.z, 0f, 0f);
		Threats.captainHealth(plain, id, 0);
		helper.assertTrue(Math.abs(plain.getMaxHealth() - base) < 0.01 && plain.getAttribute(Attributes.MAX_HEALTH).getModifier(id) == null,
			"a plain drowned given 0 extra changed: " + plain.getMaxHealth());
		leader.discard();
		plain.discard();
		helper.succeed();
	}

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

	/** The desert tests' yard: stone brick walls four high, a finished Stone Wall in the front one, a villager shut in a cell. */
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

	/** A melee husk of the culture standing still at (x, 2, z), inside the yard: it never walks. */
	private static Mob still(GameTestHelper helper, Culture culture, Villager villager, double x, double z) {
		ServerLevel level = helper.getLevel();
		Culture.Member member = culture.roster().stream().filter(m -> m.role() == Culture.Role.MELEE).findFirst().orElseThrow();
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob != null, "no " + member.entity());
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		mob.moveTo(base.getX() + x, base.getY() + 2, base.getZ() + z, 0f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
		Threats.outfit(level, mob, culture, member);
		if (mob.isBaby()) {
			((Zombie) mob).setBaby(false);
		}
		mob.ejectPassengers();
		mob.stopRiding();
		mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
		mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
		mob.setTarget(villager);
		level.addFreshEntity(mob);
		return mob;
	}

	/**
	 * The siege's record holds a ramp site against the front wall at x = 7 (foot just outside, z = 16), not yet piled,
	 * and the raid has no climber at all: only a melee husk standing still inside the yard. For 160 ticks no sand goes
	 * up anywhere along that ramp.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaSandRampNobody"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaSandRampNobody")
	public void aRampWithNoClimberByItGetsNoSand(GameTestHelper helper) {
		Culture culture = pilers(helper);
		Villager villager = yard(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Mob guard = still(helper, culture, villager, 14.5, 10.5);
		VillageRaids.track(level, hall, culture, List.of(guard));
		Optional<ThreatData.Siege> laid = Sieges.siege(level, hall);
		helper.assertTrue(laid.isPresent(), "no siege on the yard");
		ThreatData.Siege siege = laid.get();
		BlockPos foot = helper.absolutePos(new BlockPos(7, 2, 16));
		siege.rampFeet.add(foot);
		siege.rampDirs.add(Direction.NORTH.get3DDataValue());
		SandRamps.forget(); // (the storm is restored from the siege's record, with this site)
		helper.onEachTick(() -> {
			for (int out = 0; out <= 5; out++) {
				for (int up = 0; up <= 4; up++) {
					BlockPos pos = foot.relative(Direction.SOUTH, out).above(up);
					helper.assertFalse(level.getBlockState(pos).is(Blocks.SAND),
						"sand went up on the ramp with no climber by it, at " + pos.subtract(helper.absolutePos(BlockPos.ZERO)));
				}
			}
			helper.assertTrue(guard.isAlive(), "the melee husk died (test setup)");
			if (helper.getTick() >= 160) {
				helper.assertTrue(Sieges.siege(level, hall).isPresent(), "the siege ended (test setup)");
				helper.succeed();
			}
		});
	}
}
