package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA lane D (2026-11-22), B1469 written from the spec, not the fix. lookout_tower.md, "Battle stations": "the top needs
 * open shooting cells at standing height on every side"; archers posted on the tower in a siege see and shoot raiders on
 * the ground in range. The existing live test is level I with a raider 19 out behind it. These run real sieges on
 * levels II, III and IV (IV with its basement dug in), and two archers on Lookout Tower I's deck at once who must both
 * hit, from their own stations. (Turned towers, raiders close in and off to the side: QaLookoutSight1122dBugTests,
 * not registered.)
 */
public class QaLookoutSight1122dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_lookout_sight_1122d");
	/** The build's footprint starts this far into the area (z); raiders come from the south (+z). */
	private static final int BACK = 2;

	private record Tower(ServerLevel level, BlockPos origin, Rotation rotation, int ground, int face, Set<BlockPos> top, BlockPos hall, Culture culture) {
	}

	/**
	 * A village (hall, ram culture, a Palisade for a wall) with {@code build} stood finished turned by {@code rotation}, its
	 * footprint from (4, {@link #BACK}) south-east, the hall north of it. Lookout Tower IV's basement goes into ground raised under it.
	 */
	private static Tower tower(GameTestHelper helper, StarterBlueprints.Entry build, Rotation rotation) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int lift = build.basement();
		int ground = 2 + lift;
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 40;
		helper.setDayTime(6000);
		BlockPos hall = helper.absolutePos(new BlockPos(8, ground, 0)); // the village lies north: raiders come at the tower from outside, the south
		Culture culture = Culture.read(BESIEGERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"melee\"}], \"tactics\": [\"ram_gates\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new LinkedHashMap<>(loaded);
		with.put(BESIEGERS, culture);
		Threats.setForTest(with);
		List<BlueprintData.Placement> placed = new ArrayList<>();
		Leftovers.after(helper, () -> {
			Threats.setForTest(loaded);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Sieges.forget();
			BattleStations.forget();
			placed.forEach(p -> BuildSiteManager.get(level).forgetFinished(p));
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		BlockPos a = StructureTemplate.transform(BlockPos.ZERO, Mirror.NONE, rotation, BlockPos.ZERO);
		BlockPos b = StructureTemplate.transform(new BlockPos(build.size().getX() - 1, 0, build.size().getZ() - 1), Mirror.NONE, rotation, BlockPos.ZERO);
		int width = Math.abs(a.getX() - b.getX()) + 1;
		int depth = Math.abs(a.getZ() - b.getZ()) + 1;
		int left = 4;
		BlockPos origin = helper.absolutePos(new BlockPos(left - Math.min(a.getX(), b.getX()), ground, BACK - Math.min(a.getZ(), b.getZ())));
		BlockPos file = origin.below(lift);
		level.getStructureManager().get(build.id()).orElseThrow().placeInWorld(level, file, file, new StructurePlaceSettings().setRotation(rotation),
			RandomSource.create(1L), 2);
		if (lift > 0) { // raise the ground round the basement, so the tower stands on it as on a hillside dug out
			BlockPos base = helper.absolutePos(BlockPos.ZERO);
			for (int x = 0; x < 30; x++) {
				for (int z = 0; z < 30; z++) {
					boolean inside = x >= left && x < left + width && z >= BACK && z < BACK + depth;
					for (int y = 2; y < ground && !inside; y++) {
						level.setBlockAndUpdate(base.offset(x, y, z), Blocks.DIRT.defaultBlockState());
					}
				}
			}
		}
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, rotation, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(build.id(), placement, UUID.randomUUID());
		placed.add(placement);
		// A raid is a siege only on a village with a wall: a Palisade in the south-east corner, lower than the tower.
		BlockPos wall = helper.absolutePos(new BlockPos(22, ground, 24));
		level.getStructureManager().get(StarterBlueprints.PALISADE.id()).orElseThrow().placeInWorld(level, wall, wall, new StructurePlaceSettings(),
			RandomSource.create(1L), 2);
		BlueprintData.Placement palisade = new BlueprintData.Placement(level.dimension().location(), wall, Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.PALISADE.id(), palisade, UUID.randomUUID());
		placed.add(palisade);
		level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
		Set<BlockPos> top = new HashSet<>();
		for (BlockPos s : BattleStations.spots(BlueprintLibrary.get(level, build.id()).orElseThrow())) {
			top.add(origin.offset(StructureTemplate.transform(s, Mirror.NONE, rotation, BlockPos.ZERO)).above());
		}
		helper.assertTrue(!top.isEmpty(), "(setup) " + build.id().getPath() + " has no stations");
		return new Tower(level, origin, rotation, ground, BACK + depth - 1, top, hall, culture);
	}

	/** Archer guards (bow in the off hand), posts south-east of the tower, standing two blocks north of them. */
	private static List<Villager> archers(GameTestHelper helper, Tower tower, int count) {
		List<Villager> out = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			BlockPos post = new BlockPos(18 + 2 * i, tower.ground(), 16);
			helper.setBlock(post, ModBlocks.GUARD_POST);
			Villager v = helper.spawn(EntityType.VILLAGER, post.north(2));
			Jobs.employ(tower.level(), v, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
			v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			v.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW));
			out.add(v);
		}
		return out;
	}

	/**
	 * A still raider on the ground {@code out} blocks south of the build's south face, in line with the middle of its
	 * stations (or at relative x {@code x} when given), with plenty of health so every archer's hits count: the siege starts.
	 */
	private static Mob raider(GameTestHelper helper, Tower tower, int out, Integer x) {
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		int fx = x != null ? x : (int) Math.round(tower.top().stream().mapToInt(BlockPos::getX).average().orElseThrow()) - base.getX();
		int fz = tower.face() + out;
		helper.assertTrue(fz <= 29 && fx >= 0 && fx <= 29, "(setup) the raider at " + fx + ", " + fz + " is off the area");
		ServerLevel level = tower.level();
		Mob foe = EntityType.HUSK.create(level);
		foe.moveTo(base.getX() + fx + 0.5, base.getY() + tower.ground(), base.getZ() + fz + 0.5, 0f, 0f);
		foe.addTag(VillageRaids.TAG);
		foe.setNoAi(true);
		foe.setPersistenceRequired();
		foe.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
		foe.setHealth(500);
		level.addFreshEntity(foe);
		VillageRaids.track(level, tower.hall(), tower.culture(), List.of(foe));
		helper.assertTrue(Sieges.siege(level, tower.hall()).isPresent(), "(setup) no siege on the village");
		return foe;
	}

	private static String rel(GameTestHelper helper, BlockPos pos) {
		return pos.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString();
	}

	/**
	 * Within 500 ticks every archer stands on a station, all but one of them (archers spread over the village's builds:
	 * the Palisade may take one) on the tower's top; then within 600 more each one on the top hits the raider from up
	 * there (the raider's last attacker is him while he is on his station).
	 */
	private static void allHit(GameTestHelper helper, Tower tower, List<Villager> archers, Mob foe, String what) {
		Set<Villager> hit = new HashSet<>();
		List<Villager> onTop = new ArrayList<>();
		long[] up = {-1};
		float[] health = {foe.getHealth()};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (up[0] < 0) {
				if (archers.stream().allMatch(BattleStations::onStation)) {
					up[0] = tick;
					for (Villager a : archers) {
						if (tower.top().contains(BattleStations.post(a).orElseThrow())) {
							onTop.add(a);
						}
					}
					helper.assertTrue(onTop.size() >= Math.max(1, archers.size() - 1), what + ": only " + onTop.size() + " of " + archers.size()
						+ " archers took the tower's top: stations " + archers.stream().map(a -> rel(helper, BattleStations.post(a).orElseThrow())).toList());
				} else if (tick > 500) {
					helper.fail(what + ": archers not on the tower's top within 500 ticks: " + archers.stream().filter(a -> !BattleStations.onStation(a))
						.map(a -> "at " + helper.relativeVec(a.position()) + ", sent to " + BattleStations.post(a).map(p -> rel(helper, p))).toList());
				}
				return;
			}
			if (foe.getHealth() < health[0]) {
				LivingEntity by = foe.getLastHurtByMob();
				if (by instanceof Villager v && archers.contains(v)) {
					helper.assertTrue(BattleStations.onStation(v), what + ": an archer hit the raider off his station, at " + helper.relativeVec(v.position()));
					hit.add(v);
				}
				health[0] = foe.getHealth();
			}
			if (hit.containsAll(onTop)) {
				helper.succeed();
			} else if (tick > up[0] + 600) {
				List<String> missed = new ArrayList<>();
				for (Villager a : onTop) {
					if (!hit.contains(a)) {
						long arrows = tower.level().getEntitiesOfClass(AbstractArrow.class, helper.getBounds().inflate(24), r -> r.getOwner() == a).size();
						missed.add(String.format("from %s (%.1f off, in sight %s, arrows %d)", BattleStations.post(a).map(p -> rel(helper, p)).orElse("none"),
							Math.sqrt(a.distanceToSqr(foe)), a.hasLineOfSight(foe), arrows));
					}
				}
				helper.fail(what + ": the raider at " + helper.relativeVec(foe.position()) + " was never hit by " + missed);
			}
		});
	}

	private static void live(GameTestHelper helper, StarterBlueprints.Entry build, Rotation rotation, int out, Integer x, int archers, String what) {
		Tower tower = tower(helper, build, rotation);
		List<Villager> guards = archers(helper, tower, archers);
		Mob foe = raider(helper, tower, out, x);
		allHit(helper, tower, guards, foe, build.id().getPath() + " turned " + rotation + ", " + what);
	}

	/** Level II as drawn: a raider out on the ground 18 south of it is hit from the top. */
	//$ gametest_ticks_batch AREA '1200' '"qaLookoutSight1122dII"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaLookoutSight1122dII")
	public void qaAnArcherOnLookoutTowerIIHitsARaiderInASiege(GameTestHelper helper) {
		live(helper, StarterBlueprints.LOOKOUT_TOWER_2, Rotation.NONE, 18, null, 1, "18 out");
	}

	/** Level III as drawn: a raider 18 out. */
	//$ gametest_ticks_batch AREA '1200' '"qaLookoutSight1122dIII"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaLookoutSight1122dIII")
	public void qaAnArcherOnLookoutTowerIIIHitsARaiderInASiege(GameTestHelper helper) {
		live(helper, StarterBlueprints.LOOKOUT_TOWER_3, Rotation.NONE, 18, null, 1, "18 out");
	}

	/** Level IV (its armory dug into raised ground): a raider 18 out. */
	//$ gametest_ticks_batch AREA '1200' '"qaLookoutSight1122dIV"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaLookoutSight1122dIV")
	public void qaAnArcherOnLookoutTowerIVHitsARaiderInASiege(GameTestHelper helper) {
		live(helper, StarterBlueprints.LOOKOUT_TOWER_4, Rotation.NONE, 18, null, 1, "18 out");
	}

	/** Three archers (one may take the Palisade): at least two on Lookout Tower I's top at once, and each hits a raider 18 out. */
	//$ gametest_ticks_batch AREA '1200' '"qaLookoutSight1122dTwo"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaLookoutSight1122dTwo")
	public void qaTwoArchersOnLookoutTowerIBothHit(GameTestHelper helper) {
		live(helper, StarterBlueprints.LOOKOUT_TOWER, Rotation.NONE, 18, null, 3, "three archers, 18 out");
	}
}
