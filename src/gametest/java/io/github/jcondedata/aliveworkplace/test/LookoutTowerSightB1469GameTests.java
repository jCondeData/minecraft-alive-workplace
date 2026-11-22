package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * QA (lane D, 2026-11-22) for B1469, written from the bug's text: "in a siege an archer posted by the rebuilt Lookout
 * Tower I climbs to its top but never shoots a raider 19 blocks off: from none of the tower's stations can a standing
 * villager see any ground within 24 blocks. The deck's parapet is spruce_fence (1.5 high, top at 10.5) and the eye is at
 * 10.62". The brief (tools/blueprints/briefs/lookout_tower.md, "Battle stations"): "the top needs open shooting cells at
 * standing height on every side". Fixed in the drawing (the gallery's rail is broken by an open cell every other cell).
 *
 * <p>Live: a real siege on a village with a finished Lookout Tower I: the archer guard climbs to its top and from there
 * hits a raider 19 blocks off on the ground. Static: each level (I to IV), stood turned each of the four ways, has a
 * station from which a standing villager's eyes see a raider on the ground 3 to 15 blocks out from that side, in reach.
 */
public class LookoutTowerSightB1469GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_rebuilt_defense_live_1122d");
	private static final BlockPos TOWER = new BlockPos(4, 2, 2);
	private static final BlockPos HALL = new BlockPos(8, 2, 0);
	/** The raider, out past the tower's back (south), 19 blocks from the gallery's back edge. */
	private static final BlockPos FOE = new BlockPos(8, 2, 28);
	private static final int LIMIT = 900;
	/** A villager's eyes over his feet, and a raider's over the ground (as BattleStations.view has them). */
	private static final double EYE = 1.62;
	private static final double FOE_EYE = 1.5;

	private record Village(ServerLevel level, BlockPos hall, Culture culture) {
	}

	private static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 40; // the raid's area (the hall's square) reaches the raider 28 out from the hall
		helper.setDayTime(6000);
		BlockPos hall = helper.absolutePos(HALL);
		Culture culture = Culture.read(BESIEGERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"melee\"}], \"tactics\": [\"ram_gates\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new LinkedHashMap<>(loaded);
		with.put(BESIEGERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> {
			Threats.setForTest(loaded);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Sieges.forget();
			BattleStations.forget();
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return new Village(level, hall, culture);
	}

	/**
	 * Stands {@code build} with its origin (the blueprint's y = 0, on the ground) at the world position {@code origin},
	 * turned by {@code rotation}: a basement (Lookout Tower IV's armory) goes under it, as the builder digs it.
	 */
	private static Blueprint stand(ServerLevel level, StarterBlueprints.Entry build, BlockPos origin, Rotation rotation) {
		BlockPos file = origin.below(build.basement());
		level.getStructureManager().get(build.id()).orElseThrow().placeInWorld(level, file, file, new StructurePlaceSettings().setRotation(rotation),
			RandomSource.create(1L), 2);
		return BlueprintLibrary.get(level, build.id()).orElseThrow();
	}

	private static void finished(GameTestHelper helper, StarterBlueprints.Entry build, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(build.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
	}

	private static String rel(GameTestHelper helper, BlockPos pos) {
		return pos.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString();
	}

	private static boolean hit(Mob foe) {
		return !foe.isAlive() || foe.getHealth() < foe.getMaxHealth();
	}

	/**
	 * A real siege on a village with a finished Lookout Tower I: the archer guard climbs to the tower's top and from
	 * there hits a raider standing on the ground 19 blocks off, without coming down.
	 */
	//$ gametest_ticks_batch AREA '1000' '"lookoutSightB1469Tower"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "lookoutSightB1469Tower")
	public void qaAnArcherClimbsTheRebuiltLookoutTowerInASiegeAndHits(GameTestHelper helper) {
		Village village = village(helper);
		ServerLevel level = village.level();
		StarterBlueprints.Entry build = StarterBlueprints.LOOKOUT_TOWER;
		BlockPos origin = helper.absolutePos(TOWER);
		Blueprint blueprint = stand(level, build, origin, Rotation.NONE);
		finished(helper, build, origin);
		// A raid is a siege only on a village with a wall: a Palisade off to the east (lower than the tower's top, so the
		// archer's station is the tower's: the highest first).
		BlockPos wall = helper.absolutePos(new BlockPos(20, 2, 6));
		stand(level, StarterBlueprints.PALISADE, wall, Rotation.NONE);
		finished(helper, StarterBlueprints.PALISADE, wall);
		List<BlockPos> top = BattleStations.spots(blueprint).stream().map(p -> origin.offset(p).above()).toList();
		helper.assertTrue(!top.isEmpty(), "(setup) the lookout_tower has no stations");
		BlockPos postAt = new BlockPos(18, 2, 16);
		helper.setBlock(postAt, ModBlocks.GUARD_POST);
		Villager archer = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 14));
		Jobs.employ(level, archer, helper.absolutePos(postAt), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		archer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		archer.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW));
		Mob foe = EntityType.HUSK.create(level);
		BlockPos foeAt = helper.absolutePos(FOE);
		foe.moveTo(foeAt.getX() + 0.5, foeAt.getY(), foeAt.getZ() + 0.5, 0f, 0f);
		foe.addTag(VillageRaids.TAG);
		foe.setNoAi(true);
		foe.setPersistenceRequired();
		level.addFreshEntity(foe);
		VillageRaids.track(level, village.hall(), village.culture(), List.of(foe));
		helper.assertTrue(Sieges.siege(level, village.hall()).isPresent(), "no siege on the village");
		long[] on = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (on[0] < 0) {
				if (BattleStations.onStation(archer)) {
					on[0] = tick;
					BlockPos post = BattleStations.post(archer).orElseThrow();
					helper.assertTrue(top.contains(post), "his station " + rel(helper, post) + " isn't on the lookout_tower: " + top.stream().map(p -> rel(helper, p)).toList());
				} else if (tick > 500) {
					helper.fail("the archer isn't on the lookout_tower's top within 500 ticks: at " + helper.relativeVec(archer.position()) + ", his place "
						+ BattleStations.post(archer).map(p -> rel(helper, p)));
				}
				return;
			}
			if (hit(foe)) {
				helper.assertTrue(BattleStations.onStation(archer), "he came down off the lookout_tower to hit the foe: at " + helper.relativeVec(archer.position()));
				helper.succeed();
			} else if (tick > LIMIT) {
				long arrows = level.getEntitiesOfClass(AbstractArrow.class, helper.getBounds().inflate(16), a -> a.getOwner() == archer).size();
				helper.fail(String.format("from the lookout_tower's station %s he never shot at the foe %.1f blocks off (arrows %d); in sight %s; he is at %s",
					BattleStations.post(archer).map(p -> rel(helper, p)).orElse("none"), Math.sqrt(archer.distanceToSqr(foe)), arrows, archer.hasLineOfSight(foe),
					helper.relativeVec(archer.position())));
			}
		});
	}

	/** How far out from the side of the build the raiders stand (each must be seen from some station). */
	private static final int[] OUT = {3, 6, 9, 12, 15};
	private static final int BASE_Z = 2;

	/**
	 * Each way {@code build} can be turned, stood in the area with its outside to the south (+z): for each distance in
	 * {@link #OUT} out from its south face, a raider standing on the ground there (in some column of the build's width)
	 * is in a standing villager's sight, and reach, from at least one of its stations.
	 */
	static void seesTheGround(GameTestHelper helper, StarterBlueprints.Entry build) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = BlueprintLibrary.get(level, build.id()).orElseThrow();
		// A basement (IV's armory) under the area's floor would leave the area: the build stands that much higher, and the
		// raiders stand on its ground (the blueprint's y = 0), in the air where nothing is in the way but the build.
		int lift = build.basement();
		List<String> blind = new ArrayList<>();
		for (Rotation rotation : Rotation.values()) {
			BlockPos a = StructureTemplate.transform(BlockPos.ZERO, Mirror.NONE, rotation, BlockPos.ZERO);
			BlockPos b = StructureTemplate.transform(new BlockPos(build.size().getX() - 1, 0, build.size().getZ() - 1), Mirror.NONE, rotation, BlockPos.ZERO);
			int minX = Math.min(a.getX(), b.getX());
			int minZ = Math.min(a.getZ(), b.getZ());
			int width = Math.abs(a.getX() - b.getX()) + 1;
			int depth = Math.abs(a.getZ() - b.getZ()) + 1;
			int left = (30 - width) / 2;
			BlockPos origin = helper.absolutePos(new BlockPos(left - minX, 2 + lift, BASE_Z - minZ));
			stand(level, build, origin, rotation);
			List<Vec3> eyes = BattleStations.spots(blueprint).stream()
				.map(p -> origin.offset(StructureTemplate.transform(p, Mirror.NONE, rotation, BlockPos.ZERO)).above())
				.map(s -> new Vec3(s.getX() + 0.5, s.getY() + EYE, s.getZ() + 0.5)).toList();
			helper.assertTrue(!eyes.isEmpty(), build.id().getPath() + ": no stations");
			int face = BASE_Z + depth - 1;
			BlockPos base = helper.absolutePos(BlockPos.ZERO);
			for (int out : OUT) {
				if (face + out > 29) {
					continue; // past the area (a build turned long side out): the nearer raiders still count
				}
				boolean seen = false;
				for (int x = left; x < left + width && !seen; x++) {
					Vec3 at = new Vec3(base.getX() + x + 0.5, origin.getY() + FOE_EYE, base.getZ() + face + out + 0.5);
					for (Vec3 eye : eyes) {
						if (eye.distanceToSqr(at) <= BattleStations.RANGE * BattleStations.RANGE && level.clip(new ClipContext(eye, at, ClipContext.Block.COLLIDER,
							ClipContext.Fluid.NONE, CollisionContext.empty())).getType() == HitResult.Type.MISS) {
							seen = true;
							break;
						}
					}
				}
				if (!seen) {
					blind.add(rotation + ": " + out + " out");
				}
			}
			// Clear the build away for the next turn (no drops, no neighbor updates).
			for (BlockPos p : BlockPos.betweenClosed(base.offset(0, 2, 0), base.offset(29, 2 + lift + build.size().getY() + 1, 29))) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
			}
		}
		helper.assertTrue(blind.isEmpty(), build.id().getPath() + ": from none of its " + BattleStations.spots(blueprint).size()
			+ " stations does a standing villager see a raider on the ground: " + blind);
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '100' '"lookoutSightB1469Sees"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "lookoutSightB1469Sees")
	public void qaLookoutTowerIStationsSeeTheGroundOnEverySide(GameTestHelper helper) {
		seesTheGround(helper, StarterBlueprints.LOOKOUT_TOWER);
	}

	//$ gametest_ticks_batch AREA '100' '"lookoutSightB1469Sees"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "lookoutSightB1469Sees")
	public void qaLookoutTowerIIStationsSeeTheGroundOnEverySide(GameTestHelper helper) {
		seesTheGround(helper, StarterBlueprints.LOOKOUT_TOWER_2);
	}

	//$ gametest_ticks_batch AREA '100' '"lookoutSightB1469Sees"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "lookoutSightB1469Sees")
	public void qaLookoutTowerIIIStationsSeeTheGroundOnEverySide(GameTestHelper helper) {
		seesTheGround(helper, StarterBlueprints.LOOKOUT_TOWER_3);
	}

	//$ gametest_ticks_batch AREA '100' '"lookoutSightB1469Sees"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "lookoutSightB1469Sees")
	public void qaLookoutTowerIVStationsSeeTheGroundOnEverySide(GameTestHelper helper) {
		seesTheGround(helper, StarterBlueprints.LOOKOUT_TOWER_4);
	}
}
