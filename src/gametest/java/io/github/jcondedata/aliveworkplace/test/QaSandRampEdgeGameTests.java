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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (2026-10-24), ROADMAP 32.9 sand ramps, the let-go's far edge (the nightly mutant at SandRamps.java:547,
 * {@code inward < site.inside + 3} made {@code <=}, survived). A raider held to a standing ramp is let go as "over the
 * wall" once it is down on its feet in the landing ground past the wall: from 1.5 in from the ramp's foot up to, but not
 * including, three past the column it drops into. Here a ramp stands against the yard's front wall (laid by hand as the
 * siege records it) and two raiders held to it stand still on the ground inside: one 2 past the landing column (let go
 * as over the wall) and one exactly 3 past it (not one that came over by the ramp; not counted as over).
 */
public class QaSandRampEdgeGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation PILERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_sand_edge_pilers");
	private static final BlockPos HALL = new BlockPos(14, 2, 8);
	private static final int WEST = 5;
	private static final int EAST = 23;
	private static final int BACK = 4;
	private static final int FRONT = 15;

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

	/** A melee husk of the culture standing still at (x, 2, z): it never walks, so where it stands is exact. */
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
			((net.minecraft.world.entity.monster.Zombie) mob).setBaby(false);
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
	 * A ramp stands against the front wall at x = 7 (its foot just outside, at z = 16; the column it drops into is
	 * z = 14, two in). Raiders held to it on the ground inside: the one 2 past that column (z = 12.5, 4 in from the foot)
	 * is let go as over the wall; the one exactly 3 past it (z = 11.5, 5 in) is not.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaSandRampEdge"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaSandRampEdge")
	public void aRaiderThreePastTheLandingIsNotCountedOverTheWall(GameTestHelper helper) {
		Culture culture = pilers(helper);
		Villager villager = yard(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Mob near = still(helper, culture, villager, 9.5, 12.5);
		Mob edge = still(helper, culture, villager, 7.5, 11.5);
		VillageRaids.track(level, hall, culture, List.of(near, edge));
		Optional<ThreatData.Siege> laid = Sieges.siege(level, hall);
		helper.assertTrue(laid.isPresent(), "no siege on the yard");
		ThreatData.Siege siege = laid.get();
		// The ramp as a husk piles it: four high, each stage a whole ramp, outside in.
		BlockPos foot = helper.absolutePos(new BlockPos(7, 2, 16));
		for (int s = 1; s <= 4; s++) {
			for (int d = s; d >= 1; d--) {
				BlockPos sand = foot.relative(Direction.NORTH, -(d - 1)).above(s - d);
				level.setBlock(sand, Blocks.SAND.defaultBlockState(), 3);
				siege.sand.add(sand);
			}
		}
		siege.rampFeet.add(foot);
		siege.rampDirs.add(Direction.NORTH.get3DDataValue());
		siege.ramped = true;
		SandRamps.forget(); // (the storm is restored from the siege's record)
		double[] edgeAt = {Double.NaN, Double.NaN};
		helper.onEachTick(() -> {
			if (Double.isNaN(edgeAt[0])) {
				edgeAt[0] = edge.getX();
				edgeAt[1] = edge.getZ();
			}
			BlockPos base = helper.absolutePos(BlockPos.ZERO);
			helper.assertTrue(edge.getX() == base.getX() + 7.5 && edge.getZ() == base.getZ() + 11.5,
				"the husk at the edge moved: " + edge.position().subtract(base.getX(), base.getY(), base.getZ()));
			helper.assertFalse(SandRamps.over(level, hall, edge.getUUID()),
				"the husk standing 3 past the landing column (5 in from the ramp's foot) was counted over the wall by the ramp");
			if (helper.getTick() >= 120) {
				helper.assertTrue(edge.onGround() && near.onGround(), "a husk isn't on the ground");
				helper.assertTrue(SandRamps.over(level, hall, near.getUUID()),
					"the husk 2 past the landing column was never let go as over the wall: the ramp isn't holding them (test setup)");
				helper.succeed();
			}
		});
	}
}
