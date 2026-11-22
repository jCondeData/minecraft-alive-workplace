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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (lane D, 2026-11-21) for B1382, written from the bug's text: "archers on a Palisade's walk can't see out ... targets
 * 2-4 blocks out are hidden by the stake log and 5-18 out by the fence rail; every walk station sees 0 cells and arrows
 * are stopped"; fixed in the drawing (no fence points, the stakes end one block over the walk).
 *
 * <p>Live: a real siege on a village behind a finished Palisade (or Palisade II): the archer guard climbs to its walk and
 * from there hits a raider 8 blocks out and one 15 out. Static: each walk station of each palisade, stood as built, turned
 * a quarter either way and half round, has raiders 8, 12 and 15 blocks out from the stake line in its sight.
 */
public class QaPalisadeSight1121dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_palisade_sight_1121d");
	/** The palisade's corner in the live tests (outside to the south). */
	private static final BlockPos WALL = new BlockPos(11, 2, 5);
	private static final BlockPos HALL = new BlockPos(14, 2, 1);
	/** The walk's row in the blueprints (the stations an archer looks out from; WallSightGameTests). */
	private static final int WALK_Z = 2;
	/** How far out from the stake line the live raiders stand. */
	private static final int NEAR = 8;
	private static final int FAR = 15;
	private static final int LIMIT = 900;

	private record Village(ServerLevel level, BlockPos hall, Culture culture) {
	}

	private static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 24;
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

	/** Stands {@code build} with its corner at helper {@code at}, turned by {@code rotation}, and records it finished. */
	private static Blueprint stand(GameTestHelper helper, StarterBlueprints.Entry build, BlockPos at, Rotation rotation, boolean record) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(at);
		level.getStructureManager().get(build.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings().setRotation(rotation),
			RandomSource.create(1L), 2);
		if (record) {
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, rotation, Mirror.NONE);
			BuildSiteManager sites = BuildSiteManager.get(level);
			sites.recordFinished(build.id(), placement, UUID.randomUUID());
			Leftovers.after(helper, () -> sites.forgetFinished(placement));
		}
		return BlueprintLibrary.get(level, build.id()).orElseThrow();
	}

	/** The world position of the blueprint position {@code local} of a build at helper {@code at} turned by {@code rotation}. */
	private static BlockPos world(GameTestHelper helper, BlockPos at, Rotation rotation, BlockPos local) {
		return helper.absolutePos(at).offset(StructureTemplate.transform(local, Mirror.NONE, rotation, BlockPos.ZERO));
	}

	/** A raider standing still on the ground at world block {@code pos}: a husk with the raid's tag. */
	private static Mob raider(GameTestHelper helper, BlockPos pos) {
		ServerLevel level = helper.getLevel();
		Mob mob = EntityType.HUSK.create(level);
		mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
		mob.addTag(VillageRaids.TAG);
		mob.setNoAi(true);
		mob.setPersistenceRequired();
		level.addFreshEntity(mob);
		return mob;
	}

	/** An archer guard (a bow in the off hand) with a Guard Post of his own, at helper ({@code x}, 2, {@code z}). */
	private static Villager archer(GameTestHelper helper, int x, int z) {
		BlockPos post = new BlockPos(x, 2, z + 2);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		v.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW));
		return v;
	}

	private static long arrows(GameTestHelper helper, Villager archer) {
		return helper.getLevel().getEntitiesOfClass(AbstractArrow.class, helper.getBounds().inflate(16), a -> a.getOwner() == archer).size();
	}

	/** Where (helper positions) the archer's arrows lie, for the messages. */
	private static List<String> stuck(GameTestHelper helper, Villager archer) {
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		return helper.getLevel().getEntitiesOfClass(AbstractArrow.class, helper.getBounds().inflate(16), a -> a.getOwner() == archer).stream().limit(10)
			.map(a -> String.format("(%.1f, %.1f, %.1f)", a.getX() - base.getX(), a.getY() - base.getY(), a.getZ() - base.getZ())).toList();
	}

	private static boolean hit(Mob foe) {
		return !foe.isAlive() || foe.getHealth() < foe.getMaxHealth();
	}

	/**
	 * A real siege: the archer climbs to a station on the palisade's walk (the row over the stakes) and from it hits both
	 * raiders, 8 and 15 blocks out from the stake line, without coming down.
	 */
	static void shootsFromTheWalk(GameTestHelper helper, StarterBlueprints.Entry build) {
		shootsFromTheWalk(helper, build, Items.BOW, 1, NEAR, 5, FAR);
	}

	/**
	 * The same siege with the archer holding {@code weapon} in his off hand, the raiders standing {@code nearOut} and
	 * {@code farOut} blocks out from the stake line in the columns {@code nearX} and {@code farX} (helper x minus the
	 * palisade's west end: 0 to 6 is in front of it, below 0 or above 6 off to the side, a slanting shot over the stakes).
	 */
	static void shootsFromTheWalk(GameTestHelper helper, StarterBlueprints.Entry build, net.minecraft.world.item.Item weapon, int nearX, int nearOut,
		int farX, int farOut) {
		Village village = village(helper);
		ServerLevel level = village.level();
		Blueprint blueprint = stand(helper, build, WALL, Rotation.NONE, true);
		int face = WALL.getZ() + blueprint.size().getZ() - 1;
		Villager archer = archer(helper, 4, 1);
		archer.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(weapon));
		String shooter = build.id() + " (" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(weapon).getPath() + ")";
		Mob near = raider(helper, helper.absolutePos(new BlockPos(WALL.getX() + nearX, 2, face + nearOut)));
		Mob far = raider(helper, helper.absolutePos(new BlockPos(WALL.getX() + farX, 2, face + farOut)));
		List<BlockPos> walk = BattleStations.spots(blueprint).stream().filter(p -> p.getZ() == WALK_Z).map(p -> world(helper, WALL, Rotation.NONE, p).above()).toList();
		helper.assertTrue(!walk.isEmpty(), shooter + ": no stations on the walk: " + BattleStations.spots(blueprint));
		VillageRaids.track(level, village.hall(), village.culture(), List.of(near, far));
		helper.assertTrue(Sieges.siege(level, village.hall()).isPresent(), "no siege on the village");
		long[] on = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (on[0] < 0) {
				if (BattleStations.onStation(archer)) {
					on[0] = tick;
					BlockPos post = BattleStations.post(archer).orElseThrow();
					helper.assertTrue(walk.contains(post), shooter + ": his station " + post.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString() + " isn't on the palisade's walk "
						+ walk.stream().map(p -> p.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString()).toList());
				} else if (tick > 500) {
					helper.fail(shooter + ": the archer isn't on the palisade's walk within 500 ticks: at " + helper.relativeVec(archer.position()) + ", his place "
						+ BattleStations.post(archer).map(p -> p.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString()));
				}
				return;
			}
			if (hit(near) && hit(far)) {
				helper.assertTrue(BattleStations.onStation(archer), shooter + ": he came down off the walk to hit them: at " + helper.relativeVec(archer.position()));
				helper.succeed();
			} else if (tick > LIMIT) {
				helper.fail(shooter + ": from the walk station " + BattleStations.post(archer).map(p -> p.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString()) + " the archer hit "
					+ (hit(near) ? "the raider " + nearOut + " out" : "") + (hit(far) ? "the raider " + farOut + " out" : "") + (!hit(near) && !hit(far) ? "neither raider" : " only")
					+ "; in sight: " + nearOut + " out " + archer.hasLineOfSight(near) + ", " + farOut + " out " + archer.hasLineOfSight(far) + "; arrows " + arrows(helper, archer) + " stuck at " + stuck(helper, archer)
					+ "; he is at " + helper.relativeVec(archer.position()));
			}
		});
	}

	//$ gametest_ticks_batch AREA '1000' '"qaPalisadeSight1121dShoot2"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaPalisadeSight1121dShoot2")
	public void qaAnArcherOnPalisadeIIsWalkHitsRaidersEightAndFifteenOut(GameTestHelper helper) {
		shootsFromTheWalk(helper, StarterBlueprints.PALISADE_2);
	}

	/** Distances out from the stake line every walk station must see a raider at (the fence rail hid 5 to 18, B1382; the fix promises a raider 8 out in sight). */
	private static final int[] OUT = {8, 12, 15};
	/** Just out from the stake line, where B1382 says the stake log hid a raider (see QaPalisadeSight1121dBugTests). */
	static final int[] NEAR_OUT = {2, 3, 4, 5};

	/**
	 * Every station of {@code build}'s walk, stood at helper {@code at} turned by {@code rotation}: a villager there has
	 * a raider in sight in its column {@code out} blocks out from the stake line, one raider at a time.
	 */
	static void walkSees(GameTestHelper helper, StarterBlueprints.Entry build, BlockPos at, Rotation rotation, int[] out) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = stand(helper, build, at, rotation, false);
		int face = blueprint.size().getZ() - 1;
		List<BlockPos> walk = BattleStations.spots(blueprint).stream().filter(p -> p.getZ() == WALK_Z).toList();
		helper.assertTrue(walk.size() >= 4, build.id() + ": only " + walk.size() + " stations on the walk: " + BattleStations.spots(blueprint));
		List<Villager> archers = new ArrayList<>();
		List<Mob> foes = new ArrayList<>();
		List<String> where = new ArrayList<>();
		for (BlockPos local : walk) {
			BlockPos s = world(helper, at, rotation, local).above();
			for (int d : out) {
				Villager archer = EntityType.VILLAGER.create(level);
				archer.moveTo(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, 0f, 0f);
				archer.setNoAi(true);
				archer.setInvulnerable(true);
				Mob foe = EntityType.HUSK.create(level);
				BlockPos g = world(helper, at, rotation, new BlockPos(local.getX(), 0, face + d));
				foe.moveTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5, 0f, 0f);
				foe.setNoAi(true);
				archers.add(archer);
				foes.add(foe);
				where.add(local.toShortString() + " -> " + d + " out");
			}
		}
		// One pair at a time (a raider in front of another would hide it).
		int[] i = {0};
		List<String> hidden = new ArrayList<>();
		helper.onEachTick(() -> {
			int k = i[0] / 3;
			int phase = i[0] % 3;
			i[0]++;
			if (k >= archers.size()) {
				helper.assertTrue(hidden.isEmpty(), build.id() + " turned " + rotation + ": out of sight from the walk: " + hidden);
				helper.succeed();
				return;
			}
			if (phase == 0) {
				level.addFreshEntity(archers.get(k));
				level.addFreshEntity(foes.get(k));
			} else if (phase == 2) {
				if (!archers.get(k).hasLineOfSight(foes.get(k))) {
					hidden.add(where.get(k));
				}
				archers.get(k).discard();
				foes.get(k).discard();
			}
		});
	}

	//$ gametest_ticks_batch AREA '300' '"qaPalisadeSight1121dSees"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaPalisadeSight1121dSees")
	public void qaEveryPalisadeWalkStationSeesEightToFifteenOut(GameTestHelper helper) {
		walkSees(helper, StarterBlueprints.PALISADE, new BlockPos(11, 2, 2), Rotation.NONE, OUT);
	}

	//$ gametest_ticks_batch AREA '300' '"qaPalisadeSight1121dSees2"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaPalisadeSight1121dSees2")
	public void qaEveryPalisadeIIWalkStationSeesEightToFifteenOut(GameTestHelper helper) {
		walkSees(helper, StarterBlueprints.PALISADE_2, new BlockPos(11, 2, 2), Rotation.NONE, OUT);
	}

	/** Turned a quarter clockwise the outside is to the west: corner at x 26, the stakes at x 26 - 3 (or 4), raiders to x 7. */
	//$ gametest_ticks_batch AREA '300' '"qaPalisadeSight1121dSeesCw"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaPalisadeSight1121dSeesCw")
	public void qaPalisadeWalkSeesOutTurnedAQuarterClockwise(GameTestHelper helper) {
		walkSees(helper, StarterBlueprints.PALISADE, new BlockPos(26, 2, 11), Rotation.CLOCKWISE_90, OUT);
	}

	//$ gametest_ticks_batch AREA '300' '"qaPalisadeSight1121dSeesCw2"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaPalisadeSight1121dSeesCw2")
	public void qaPalisadeIIWalkSeesOutTurnedAQuarterClockwise(GameTestHelper helper) {
		walkSees(helper, StarterBlueprints.PALISADE_2, new BlockPos(26, 2, 11), Rotation.CLOCKWISE_90, OUT);
	}

	/** Turned a quarter the other way the outside is to the east: corner at x 2. */
	//$ gametest_ticks_batch AREA '300' '"qaPalisadeSight1121dSeesCcw"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaPalisadeSight1121dSeesCcw")
	public void qaPalisadeWalkSeesOutTurnedAQuarterCounterclockwise(GameTestHelper helper) {
		walkSees(helper, StarterBlueprints.PALISADE, new BlockPos(2, 2, 18), Rotation.COUNTERCLOCKWISE_90, OUT);
	}

	/** Turned half round the outside is to the north: corner at (18, 2, 26). */
	//$ gametest_ticks_batch AREA '300' '"qaPalisadeSight1121dSees180"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaPalisadeSight1121dSees180")
	public void qaPalisadeIIWalkSeesOutTurnedHalfRound(GameTestHelper helper) {
		walkSees(helper, StarterBlueprints.PALISADE_2, new BlockPos(18, 2, 26), Rotation.CLOCKWISE_180, OUT);
	}
}
