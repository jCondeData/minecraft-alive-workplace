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
 * QA lane D (2026-11-22, lane-d-1122-0408) for B1382 ("archers on a Palisade's walk couldn't see out", fixed in the
 * drawing of palisade and palisade_2), written from the bug and the wiki's sieges page rather than the code. Earlier
 * runs proved a day siege by rams with raiders 8 and 15 out (QaPalisadeSight1121d, QaPalisadeAim1121d). These go where
 * those don't: a NIGHT attack (the raid comes after dusk, as raids do) by a ladder culture and by rams, with raiders
 * 6 and 12 blocks out from the stake line, the near end of what B1382's fix opened up (B1439 left 2-5 out to the owner).
 *
 * <p>Boundary, from sieges.md: "The ground right at the foot of a wall stays hidden from any walkway ... On a station he
 * never climbs down to chase: when the raider he is shooting at goes out of his sight ... he turns to the nearest raider
 * he can see and reach." So a raider 1 out, at the stakes' foot, is not one the archer can be asked to hit; what the
 * spec does promise is that he stays on the walk and still hits the raider 12 out.
 *
 * <p>Left out (intermittent, a bug to file): a night attack on Palisade I (ladders or rams) with raiders 6 and 12 out,
 * where the archer sometimes walks off the walk after killing the raider 12 out ({@link QaPalisadeNight1122dBugTests}).
 */
public class QaPalisadeNight1122dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	static final ResourceLocation NIGHT_CLIMBERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_palisade_night_1122d_ladders");
	static final ResourceLocation NIGHT_RAMS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_palisade_night_1122d_rams");
	static final BlockPos WALL = new BlockPos(11, 2, 5);
	private static final BlockPos HALL = new BlockPos(14, 2, 1);
	/** The walk's row in both palisade blueprints. */
	private static final int WALK_Z = 2;
	/** An hour after dusk: raids come at night. */
	private static final int NIGHT = 14000;
	private static final int LIMIT = 1000;

	record Village(ServerLevel level, BlockPos hall, Culture culture) {
	}

	static Village village(GameTestHelper helper, ResourceLocation id, String tactic) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 24;
		helper.setDayTime(NIGHT);
		BlockPos hall = helper.absolutePos(HALL);
		Culture culture = Culture.read(id, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"melee\"}], \"tactics\": [\"" + tactic + "\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new LinkedHashMap<>(loaded);
		with.put(id, culture);
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

	static Blueprint stand(GameTestHelper helper, StarterBlueprints.Entry build) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(WALL);
		level.getStructureManager().get(build.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(build.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		return BlueprintLibrary.get(level, build.id()).orElseThrow();
	}

	static Mob raider(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(at);
		Mob mob = EntityType.HUSK.create(level);
		mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
		mob.addTag(VillageRaids.TAG);
		mob.setNoAi(true);
		mob.setPersistenceRequired();
		level.addFreshEntity(mob);
		return mob;
	}

	static Villager archer(GameTestHelper helper) {
		BlockPos post = new BlockPos(4, 2, 3);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 1));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		v.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW));
		return v;
	}

	private static boolean hit(Mob foe) {
		return !foe.isAlive() || foe.getHealth() < foe.getMaxHealth();
	}

	private static String rel(GameTestHelper helper, BlockPos p) {
		return p.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString();
	}

	/**
	 * A night attack on a village behind {@code build}: the archer must reach a station on the walk within 500 ticks,
	 * then hit {@code mustHit} without ever leaving the walk. {@code others} stand there too (a raider at the foot).
	 */
	static void nightAttack(GameTestHelper helper, StarterBlueprints.Entry build, ResourceLocation cultureId, String tactic, int mustOutA, int mustOutB,
		int footOut) {
		Village village = village(helper, cultureId, tactic);
		ServerLevel level = village.level();
		Blueprint blueprint = stand(helper, build);
		int face = WALL.getZ() + blueprint.size().getZ() - 1;
		Villager archer = archer(helper);
		String what = build.id().getPath() + " at night (" + tactic + ")";
		List<Mob> must = new java.util.ArrayList<>();
		must.add(raider(helper, new BlockPos(WALL.getX() + 1, 2, face + mustOutA)));
		if (mustOutB > 0) {
			must.add(raider(helper, new BlockPos(WALL.getX() + 5, 2, face + mustOutB)));
		}
		List<Mob> all = new java.util.ArrayList<>(must);
		if (footOut > 0) {
			all.add(raider(helper, new BlockPos(WALL.getX() + 3, 2, face + footOut)));
		}
		List<BlockPos> walk = BattleStations.spots(blueprint).stream().filter(p -> p.getZ() == WALK_Z)
			.map(p -> helper.absolutePos(WALL).offset(StructureTemplate.transform(p, Mirror.NONE, Rotation.NONE, BlockPos.ZERO)).above()).toList();
		helper.assertTrue(!walk.isEmpty(), what + ": (setup) no stations on the walk");
		VillageRaids.track(level, village.hall(), village.culture(), all);
		helper.assertTrue(Sieges.siege(level, village.hall()).isPresent(), what + ": a night attack on a village behind a Palisade is no siege");
		long[] on = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (on[0] < 0) {
				if (BattleStations.onStation(archer)) {
					on[0] = tick;
					BlockPos post = BattleStations.post(archer).orElseThrow();
					helper.assertTrue(walk.contains(post), what + ": his station " + rel(helper, post) + " isn't on the walk " + walk.stream().map(p -> rel(helper, p)).toList());
				} else if (tick > 500) {
					helper.fail(what + ": the archer isn't on the walk within 500 ticks of the attack: at " + helper.relativeVec(archer.position()));
				}
				return;
			}
			if (!BattleStations.onStation(archer) || archer.getY() < BattleStations.post(archer).orElseThrow().getY() - 0.1) {
				helper.fail(what + ": the archer came down off the walk at tick " + tick + " (on the walk from tick " + on[0] + "; at " + helper.relativeVec(archer.position())
					+ "; his place " + BattleStations.post(archer).map(p -> rel(helper, p)) + ", stationed " + BattleStations.stationed(archer) + ", siege on "
					+ Sieges.siege(level, village.hall()).map(s -> !s.over).orElse(false) + ", raid on " + VillageRaids.active(village.hall()).isPresent()
					+ ", raiders alive " + all.stream().filter(Mob::isAlive).count() + "/" + all.size() + ", hit " + must.stream().map(QaPalisadeNight1122dGameTests::hit).toList()
					+ ", ladders " + level.getBlockStates(helper.getBounds()).filter(s -> s.is(net.minecraft.world.level.block.Blocks.LADDER)).count() + ")");
			}
			if (must.stream().allMatch(QaPalisadeNight1122dGameTests::hit)) {
				helper.succeed();
			} else if (tick > LIMIT) {
				StringBuilder s = new StringBuilder();
				for (Mob m : must) {
					s.append(String.format(" raider at %.0f out: hit %s, in sight %s;", m.getZ() - helper.absolutePos(BlockPos.ZERO).getZ() - face, hit(m), archer.hasLineOfSight(m)));
				}
				long arrows = level.getEntitiesOfClass(AbstractArrow.class, helper.getBounds().inflate(16), a -> a.getOwner() == archer).size();
				helper.fail(what + ": from " + BattleStations.post(archer).map(p -> rel(helper, p)) + " by tick " + LIMIT + ":" + s + " arrows loosed and lying: " + arrows
					+ "; his target " + archer.getTarget());
			}
		});
	}

	/** A ram culture's night attack on Palisade II: the archer on the walk hits raiders 6 and 12 out. */
	//$ gametest_ticks_batch AREA '1100' '"qaPalisadeNight1122dRams2"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qaPalisadeNight1122dRams2")
	public void qaAtNightAnArcherOnPalisadeIIsWalkHitsRamRaidersSixAndTwelveOut(GameTestHelper helper) {
		nightAttack(helper, StarterBlueprints.PALISADE_2, NIGHT_RAMS, "ram_gates", 6, 12, 0);
	}

	/** Ladders on Palisade II, the other pairing. */
	//$ gametest_ticks_batch AREA '1100' '"qaPalisadeNight1122dLadders2"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qaPalisadeNight1122dLadders2")
	public void qaAtNightAnArcherOnPalisadeIIsWalkHitsLadderRaidersSixAndTwelveOut(GameTestHelper helper) {
		nightAttack(helper, StarterBlueprints.PALISADE_2, NIGHT_CLIMBERS, "ladders", 6, 12, 0);
	}

	/**
	 * Boundary: a raider right at the stakes' foot (1 out, hidden from every walkway by the spec) and one 12 out. The
	 * archer never climbs down after the hidden one and hits the one 12 out, on Palisade I.
	 */
	//$ gametest_ticks_batch AREA '1100' '"qaPalisadeNight1122dFoot1"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qaPalisadeNight1122dFoot1")
	public void qaARaiderAtThePalisadesFootDoesntPullTheArcherOffTheWalk(GameTestHelper helper) {
		nightAttack(helper, StarterBlueprints.PALISADE, NIGHT_RAMS, "ram_gates", 12, 0, 1);
	}

	/** The same boundary on Palisade II (the foot raider 1 out past its ditch row, the blueprint's outermost row). */
	//$ gametest_ticks_batch AREA '1100' '"qaPalisadeNight1122dFoot2"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qaPalisadeNight1122dFoot2")
	public void qaARaiderAtPalisadeIIsFootDoesntPullTheArcherOffTheWalk(GameTestHelper helper) {
		nightAttack(helper, StarterBlueprints.PALISADE_2, NIGHT_RAMS, "ram_gates", 12, 0, 1);
	}
}
