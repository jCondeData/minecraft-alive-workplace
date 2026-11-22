package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA lane D (2026-11-22, lane-d-1122-0408): the Barracks' guards turn out to a night raid and go back after, never tested
 * live before (lane-d-1122-0108's notes). From the brief ({@code tools/blueprints/briefs/barracks.md}: "A Guard works at
 * a grindstone ... From a grindstone the Guard patrols, sleeps and takes better gear") and the wiki: raids.md, "Guards
 * fight anything that carries the raider tag", config-switches.md, {@code guardRadius} 24, "how far from their
 * workstation guards patrol and fight", and the patrol's rule that at night a guard keeps close to his post.
 *
 * <p>How guards turn out (GuardRally): "When [the village bell] rings, other villagers run home and hide; a guard gets up,
 * heads for the bell and fights anything near it for a minute and a half." The finished Barracks I (and II), its
 * guards employed at its drawn grindstones, by day (a raid that stays through the morning); raiders (husks of 60 health with the raid's tag,
 * standing still so the guards must come to them) 8 to 10 blocks out in front of it, round the village bell, which a
 * player rings as the raid comes. Every guard comes out to them and every raider is hurt; once the raid is over (every
 * raider dead) and the rally's minute and a half has run, every guard is back on his round, within the day patrol's
 * square (14) plus a block of his own grindstone. The same at night, and without the bell: {@link QaBarracksTurnOut1122dBugTests}.
 */
public class QaBarracksTurnOut1122dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation RAIDERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_barracks_turnout_1122d");
	private static final BlockPos HALL = new BlockPos(2, 2, 2);
	private static final BlockPos AT = new BlockPos(8, 2, 14);
	private static final int NIGHT = 14000;
	/** By day a guard walks up to 14 blocks from his post on each axis (GuardPatrol.DAY_RADIUS), and a block more for his step. */
	static final int DAY_HOME = 15;
	static final int DAY = 6000;
	/** At night a guard keeps within 6 blocks of his post on each axis (GuardPatrol's night square), and a block more for his step. */
	static final int HOME = 7;

	private static boolean hurt(Mob m) {
		return !m.isAlive() || m.getHealth() < m.getMaxHealth();
	}

	private static double flat(Villager v, BlockPos p) {
		return Math.max(Math.abs(v.getX() - (p.getX() + 0.5)), Math.abs(v.getZ() - (p.getZ() + 0.5)));
	}

	static void turnOut(GameTestHelper helper, StarterBlueprints.Entry build, int expectedGuards, boolean bell) {
		turnOut(helper, build, expectedGuards, bell, NIGHT, HOME);
	}

	/** The same at day time {@code dayTime}, "back" meaning within {@code home} blocks of the grindstone on each axis. */
	static void turnOut(GameTestHelper helper, StarterBlueprints.Entry build, int expectedGuards, boolean bell, int dayTime, int homeRadius) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 32;
		helper.setDayTime(dayTime);
		BlockPos hall = helper.absolutePos(HALL);
		Culture culture = Culture.read(RAIDERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"melee\"}], \"tactics\": []}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new LinkedHashMap<>(loaded);
		with.put(RAIDERS, culture);
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
		BlockPos origin = helper.absolutePos(AT);
		level.getStructureManager().get(build.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(build.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		List<BlockPos> grindstones = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(build.size().getX() - 1, build.size().getY() - 1, build.size().getZ() - 1))) {
			if (level.getBlockState(p).is(Blocks.GRINDSTONE)) {
				grindstones.add(p.immutable());
			}
		}
		helper.assertTrue(grindstones.size() == expectedGuards, "(setup) " + build.id().getPath() + " draws " + grindstones.size() + " grindstones, not " + expectedGuards);
		List<Villager> guards = new ArrayList<>();
		for (BlockPos g : grindstones) {
			BlockPos stand = null;
			for (BlockPos n : List.of(g.north(), g.south(), g.east(), g.west())) {
				if (level.getBlockState(n).isAir() && level.getBlockState(n.above()).isAir() && level.getBlockState(n.below()).isFaceSturdy(level, n.below(), net.minecraft.core.Direction.UP)) {
					stand = n;
					break;
				}
			}
			helper.assertTrue(stand != null, "(setup) no free cell beside the grindstone at " + g.subtract(origin).toShortString());
			Villager v = EntityType.VILLAGER.create(level);
			v.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0f, 0f);
			level.addFreshEntity(v);
			Jobs.employ(level, v, g, PoiTypes.WEAPONSMITH, ModVillagers.GUARD);
			v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			guards.add(v);
			Leftovers.after(helper, v::discard);
		}
		// Tough raiders, standing still out in front (the front is the blueprint's z = 0 side), one per guard.
		List<Mob> raiders = new ArrayList<>();
		for (int i = 0; i < guards.size(); i++) {
			Mob m = EntityType.HUSK.create(level);
			BlockPos p = helper.absolutePos(new BlockPos(AT.getX() + 2 + i * 3, 2, AT.getZ() - 8 - (i % 2) * 2));
			m.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
			m.addTag(VillageRaids.TAG);
			m.setNoAi(true);
			m.setPersistenceRequired();
			m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(60);
			m.setHealth(60);
			level.addFreshEntity(m);
			raiders.add(m);
		}
		VillageRaids.track(level, hall, culture, raiders);
		helper.assertTrue(VillageRaids.active(hall).isPresent(), "(setup) no raid on the village");
		// The village bell, out in front among the raiders; a player rings it as the raid comes.
		BlockPos bellAt = helper.absolutePos(new BlockPos(AT.getX() + 6, 2, AT.getZ() - 6));
		if (bell) {
			level.setBlockAndUpdate(bellAt, Blocks.BELL.defaultBlockState());
			helper.assertTrue(((net.minecraft.world.level.block.BellBlock) Blocks.BELL).attemptToRing(null, level, bellAt, net.minecraft.core.Direction.NORTH),
				"(setup) the bell didn't ring");
		}
		long limit = bell ? 3000 : 1200;
		boolean[] came = new boolean[guards.size()];
		long[] over = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (over[0] < 0) {
				for (int i = 0; i < guards.size(); i++) {
					Villager g = guards.get(i);
					helper.assertTrue(g.isAlive(), "(setup) a guard died");
					for (Mob m : raiders) {
						if (g.distanceTo(m) < 3.5) {
							came[i] = true;
						}
					}
				}
				boolean allCame = true;
				for (boolean c : came) {
					allCame &= c;
				}
				if (allCame && raiders.stream().allMatch(QaBarracksTurnOut1122dGameTests::hurt)) {
					over[0] = tick;
					raiders.forEach(Mob::kill); // the raid is beaten
				} else if (tick > 900) {
					List<String> where = new ArrayList<>();
					for (int i = 0; i < guards.size(); i++) {
						where.add("guard " + i + " " + (came[i] ? "came" : "never came") + ", at " + helper.relativeVec(guards.get(i).position()) + " ("
							+ String.format("%.1f", flat(guards.get(i), grindstones.get(i))) + " from his grindstone, sleeping " + guards.get(i).isSleeping() + ")");
					}
					helper.fail(build.id().getPath() + (bell ? " (bell rung)" : "") + ": the guards didn't turn out to the raid within 900 ticks: " + where + "; raiders hurt "
						+ raiders.stream().map(QaBarracksTurnOut1122dGameTests::hurt).toList());
				}
				return;
			}
			List<String> away = new ArrayList<>();
			for (int i = 0; i < guards.size(); i++) {
				Villager g = guards.get(i);
				BlockPos home = grindstones.get(i);
				if (flat(g, home) > homeRadius || Math.abs(g.getY() - home.getY()) > 4) {
					away.add("guard " + i + " at " + helper.relativeVec(g.position()) + ", " + String.format("%.1f", flat(g, home)) + " from his grindstone "
						+ helper.relativePos(home).toShortString());
				}
			}
			if (away.isEmpty() && tick > over[0] + 20) {
				helper.succeed();
			} else if (tick > over[0] + limit) {
				helper.fail(build.id().getPath() + (bell ? " (bell rung)" : "") + ": " + limit + " ticks after the raid the guards aren't back by their grindstones: " + away + "; raid still on "
					+ VillageRaids.active(hall).isPresent());
			}
		});
	}

	/** Barracks I by day (a raid that stays through the morning), a player rings the bell: both guards turn out to it and go back to their patrol round their grindstones after. */
	//$ gametest_ticks_batch AREA '4400' '"qaBarracksTurnOut1122dBell1"'
	@GameTest(template = AREA, timeoutTicks = 4400, batch = "qaBarracksTurnOut1122dBell1")
	public void qaBarracksGuardsTurnOutToTheBellInARaidAndGoBackAfter(GameTestHelper helper) {
		turnOut(helper, StarterBlueprints.BARRACKS, 2, true, DAY, DAY_HOME);
	}

	/** Barracks II by day: all four guards answer the bell and go back. */
	//$ gametest_ticks_batch AREA '4400' '"qaBarracksTurnOut1122dBell2"'
	@GameTest(template = AREA, timeoutTicks = 4400, batch = "qaBarracksTurnOut1122dBell2")
	public void qaBarracksIIsFourGuardsTurnOutToTheBellInARaidAndGoBackAfter(GameTestHelper helper) {
		turnOut(helper, StarterBlueprints.BARRACKS_2, 4, true, DAY, DAY_HOME);
	}
}
