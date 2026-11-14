package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.guard.Gates;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.guard.Warnings;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Jobs;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (lane D, 2026-11-14) for A day's warning (ROADMAP 32.14) with Sieges III (32.6), from the spec: "at dusk the
 * guards take battle stations" is the evening's alert before the raiders come. Once the night's raid is fought off the
 * guards stand down to their rounds (the siege stays, over, until its dawn); the warned evening must not put them back
 * on the walls. Killed survivor: BattleStations.tick's warned-evening loop skipping a hall whose siege is over only
 * when the siege is also live (2026-11-13 mutation run, BattleStations.java:341).
 *
 * <p>Each test first sees the archer on his station on the warned evening before the raid (the control: the test can
 * see a station at all), then the raid comes and is fought off through the hall's own raid round.
 */
public class QaStandDown1114dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_standdown_1114d");
	private static final BlockPos HALL = new BlockPos(15, 2, 26);
	private static final BlockPos GATEHOUSE = new BlockPos(9, 2, 5);
	private static final BlockPos TOWER = new BlockPos(21, 2, 5);
	/** Day 13 begins here. */
	private static final long BASE = 12 * VillageNeeds.DAY;
	private static final long ATTACK_DAY = 14;
	private static final double NORTH = -Math.PI / 2;
	/** Ticks after the raid is fought off before the watch must be gone (one watch round and a little). */
	private static final int GRACE = BattleStations.EVERY + 5;
	/** Ticks the guards are watched after that. */
	private static final int WATCHED = 140;

	private record Village(ServerLevel level, BlockPos hall, Culture culture, VillageHallBlockEntity entity) {
	}

	/**
	 * A raid fought off on its warned evening: the archer stood on his station before it, and once the last raider is
	 * dead (the siege over, kept till dawn, and still the warned evening by the clock) he has no battle station for the
	 * rest of the watch: back to his round.
	 */
	//$ gametest_ticks_batch AREA '1100' '"qa1114dStandDown"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qa1114dStandDown")
	public void aRaidFoughtOffOnItsWarnedEveningStandsTheGuardsDown(GameTestHelper helper) {
		standDown(helper, false);
	}

	/**
	 * As above, with a restart between the raid fought off and dawn (the watches in memory forgotten, the over siege
	 * kept in ThreatData): the warned evening doesn't alert the guards again.
	 */
	//$ gametest_ticks_batch AREA '1100' '"qa1114dStandDownRestart"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qa1114dStandDownRestart")
	public void aRestartAfterTheRaidIsFoughtOffDoesntSendTheGuardsBackUp(GameTestHelper helper) {
		standDown(helper, true);
	}

	private static void standDown(GameTestHelper helper, boolean restart) {
		Village village = village(helper);
		ServerLevel level = village.level();
		BlockPos hall = village.hall();
		ThreatData data = ThreatData.get(level);
		Villager archer = guard(helper, new ItemStack(Items.BOW), 16, 14);
		setAttack(village, NORTH);
		Warnings.round(level, hall, 10, 1);
		helper.assertTrue(data.warned(hall) == ATTACK_DAY, "setup: not warned: " + data.warned(hall));
		level.setDayTime(BASE + VillageNeeds.DAY + Warnings.NOON + 100);
		Gates.round(level, hall, 1);
		Warnings.round(level, hall, 10, 1);
		level.setDayTime(BASE + VillageNeeds.DAY + VillageRaids.DUSK + 10);
		long start = helper.getTick();
		long[] over = {-1};
		helper.onEachTick(() -> {
			if (over[0] < 0) {
				// The control: before the raid, on the warned evening, he takes his station.
				helper.assertTrue(helper.getTick() - start <= 500, "control: the archer isn't on a battle station 500 ticks into the warned evening");
				if (!BattleStations.onStation(archer)) {
					return;
				}
				Mob husk = EntityType.HUSK.create(level);
				BlockPos out = helper.absolutePos(new BlockPos(14, 2, 1));
				husk.moveTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5, 0f, 0f);
				husk.addTag(VillageRaids.TAG);
				husk.setNoAi(true);
				level.addFreshEntity(husk);
				VillageRaids.track(level, hall, village.culture(), List.of(husk));
				helper.assertTrue(data.siege(hall).isPresent(), "setup: the raid laid no siege");
				// The last raider falls; the hall's raid round finds the raid fought off.
				husk.kill();
				VillageRaids.tick(level, hall, 10, 1, -100, day -> {
				});
				helper.assertTrue(data.raid(hall).isEmpty(), "setup: the raid isn't over with its last raider dead: " + data.raid(hall));
				helper.assertTrue(data.siege(hall).map(s -> s.over).orElse(false), "setup: the siege isn't over (or was lifted) after the raid was fought off: " + data.siege(hall));
				if (restart) {
					BattleStations.forget();
				}
				over[0] = helper.getTick();
				return;
			}
			long since = helper.getTick() - over[0];
			if (since < GRACE) {
				return;
			}
			// The setup still holds: the siege is over and kept, and by the clock it is the warned evening.
			helper.assertTrue(data.siege(hall).map(s -> s.over).orElse(false), "setup: the over siege was lifted before dawn (" + since + " ticks after)");
			helper.assertTrue(Warnings.evening(level, hall), "setup: no longer the warned evening " + since + " ticks after the raid (day time " + level.getDayTime() + ")");
			helper.assertFalse(BattleStations.stationed(archer), "the raid was fought off " + since + " ticks ago but the archer still has a battle station"
				+ (BattleStations.onStation(archer) ? " and stands on it" : "") + (restart ? " (after a restart)" : "") + ": the guards didn't stand down");
			if (since >= GRACE + WATCHED) {
				helper.succeed();
			}
		});
	}

	// The village (as QaSiegeWarnings1031dGameTests).

	private static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		boolean raids = VillageRaids.ENABLED;
		VillageHalls.RADIUS = 24;
		VillageRaids.ENABLED = true;
		BlockPos hall = helper.absolutePos(HALL);
		Culture culture = Culture.read(BESIEGERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"melee\"}], \"tactics\": [\"ram_gates\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new java.util.LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new java.util.LinkedHashMap<>(loaded);
		with.put(BESIEGERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> {
			Threats.setForTest(loaded);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			Sieges.raise(level, hall, false);
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			Sieges.forget();
			BattleStations.forget();
			VillageHalls.RADIUS = radius;
			VillageRaids.ENABLED = raids;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		finished(helper, StarterBlueprints.GATEHOUSE, GATEHOUSE);
		finished(helper, StarterBlueprints.WALL_TOWER, TOWER);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(entity != null, "no hall");
		ThreatData.get(level).forgetClock(hall);
		return new Village(level, hall, culture, entity);
	}

	private static void finished(GameTestHelper helper, StarterBlueprints.Entry build, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(at);
		level.getStructureManager().get(build.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(build.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
	}

	/** A guard whose Guard Post is at ({@code x}, 2, {@code z} + 2), standing two blocks north of it; {@code off} in the off hand. */
	private static Villager guard(GameTestHelper helper, ItemStack off, int x, int z) {
		ServerLevel level = helper.getLevel();
		BlockPos post = helper.absolutePos(new BlockPos(x, 2, z + 2));
		level.setBlockAndUpdate(post, ModBlocks.GUARD_POST.defaultBlockState());
		Villager v = EntityType.VILLAGER.create(level);
		BlockPos at = post.north(2);
		v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		level.addFreshEntity(v);
		Jobs.employ(level, v, post, ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		v.setItemSlot(EquipmentSlot.OFFHAND, off);
		return v;
	}

	/** Dusk of day 13: the clock sets day 14's attack (tomorrow night) from {@code angle}, at 13600. */
	private static void setAttack(Village village, double angle) {
		village.level().setDayTime(BASE + 12100);
		ThreatData.Attack attack = new ThreatData.Attack(ATTACK_DAY, BESIEGERS, angle, 13600);
		ThreatData.get(village.level()).rolled(village.hall(), ATTACK_DAY - 1, attack);
	}
}
