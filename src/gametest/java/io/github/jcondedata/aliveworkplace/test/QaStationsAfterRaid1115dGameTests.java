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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (lane D, 2026-11-15) for Sieges III's battle stations (ROADMAP 32.6) with A day's warning (32.14), written from
 * the spec: stations are taken "when a siege begins (or a warned one is near)"; once the raid is fought off they end,
 * even though it is still the warned evening and the siege itself waits for dawn.
 */
public class QaStationsAfterRaid1115dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_stations_after_1115d");
	private static final BlockPos HALL = new BlockPos(15, 2, 26);
	private static final BlockPos GATEHOUSE = new BlockPos(9, 2, 5);
	private static final BlockPos TOWER = new BlockPos(21, 2, 5);
	/** Day 13 begins here: a day no other test of the warning uses. */
	private static final long BASE = 12 * VillageNeeds.DAY;
	private static final long ATTACK_DAY = 14;
	private static final double NORTH = -Math.PI / 2;
	private static final double SOUTH = Math.PI / 2;

	private record Village(ServerLevel level, BlockPos hall, Culture culture, VillageHallBlockEntity entity) {
	}

	/** A hall at {@code hall}; a finished Gatehouse and Wall Tower when {@code gatehouse}, else only a Wall Tower at {@code tower}. */
	private static Village village(GameTestHelper helper, BlockPos hallAt, boolean gatehouse, BlockPos tower) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 24;
		VillageRaids.ENABLED = true;
		BlockPos hall = helper.absolutePos(hallAt);
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
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		if (gatehouse) {
			finished(helper, StarterBlueprints.GATEHOUSE, GATEHOUSE);
		}
		finished(helper, StarterBlueprints.WALL_TOWER, tower);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(entity != null, "no hall");
		ThreatData.get(level).forgetClock(hall);
		return new Village(level, hall, culture, entity);
	}

	private static Village village(GameTestHelper helper) {
		return village(helper, HALL, true, TOWER);
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

	/** A guard whose Guard Post is at the absolute {@code post}, standing two blocks north of it; {@code off} in the off hand. */
	private static Villager guardAt(GameTestHelper helper, ItemStack off, BlockPos post) {
		ServerLevel level = helper.getLevel();
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

	private static Villager guard(GameTestHelper helper, ItemStack off, int x, int z) {
		return guardAt(helper, off, helper.absolutePos(new BlockPos(x, 2, z + 2)));
	}

	/** Dusk of day 13: the clock sets day 14's attack (tomorrow night) from {@code angle}, at 13600. */
	private static ThreatData.Attack setAttack(Village village, double angle) {
		village.level().setDayTime(BASE + 12100);
		ThreatData.Attack attack = new ThreatData.Attack(ATTACK_DAY, BESIEGERS, angle, 13600);
		ThreatData.get(village.level()).rolled(village.hall(), ATTACK_DAY - 1, attack);
		return attack;
	}

	/**
	 * The raid on a warned village is fought off before dawn: the siege waits for the morning, but the guards' battle
	 * stations end with the raid (they go back to their rounds). It is still the warned evening (warned, after dusk, no
	 * raid out), and that evening's own battle stations (taken before the raiders come) must not be taken again for a
	 * raid already beaten. Kills the nightly's BattleStations:341 survivor ({@code live.contains || siege present} to
	 * {@code &&}): with it the warned-evening watch comes back and the archer is put on the wall again till dawn.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaStationsAfterRaid1115d"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaStationsAfterRaid1115d")
	public void aRaidFoughtOffOnTheWarnedEveningSendsTheArchersBackToTheirRounds(GameTestHelper helper) {
		Village village = village(helper);
		ServerLevel level = village.level();
		BlockPos hall = village.hall();
		Villager archer = guard(helper, new ItemStack(Items.BOW), 16, 14);
		setAttack(village, NORTH);
		Warnings.round(level, hall, 10, 1);
		level.setDayTime(BASE + VillageNeeds.DAY + Warnings.NOON + 100);
		Gates.round(level, hall, 1);
		Warnings.round(level, hall, 10, 1);
		level.setDayTime(BASE + VillageNeeds.DAY + VillageRaids.DUSK + 10);
		long start = helper.getTick();
		long[] began = {-1};
		long[] beaten = {-1};
		helper.onEachTick(() -> {
			long now = helper.getTick();
			if (began[0] < 0) {
				helper.assertTrue(now - start <= 500, "the archer not on his station on the warned evening after 500 ticks");
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
				helper.assertTrue(Sieges.siege(level, hall).isPresent(), "no siege when the raid came");
				began[0] = now;
				return;
			}
			if (beaten[0] < 0) {
				if (now - began[0] < 2 * BattleStations.EVERY) {
					return;
				}
				helper.assertTrue(BattleStations.stationed(archer), "the archer has no place while the siege is on");
				// the raid is fought off: its last raider falls
				level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
				VillageRaids.forget(hall);
				beaten[0] = now;
				return;
			}
			if (now - beaten[0] < 4 * BattleStations.EVERY) {
				return;
			}
			ThreatData.Siege siege = Sieges.siege(level, hall).orElse(null);
			helper.assertTrue(siege != null && siege.over, "the siege is not waiting for the morning after the raid was beaten: " + siege);
			helper.assertTrue(Warnings.evening(level, hall), "not the warned evening any more after the raid (the test no longer reaches its case)");
			helper.assertFalse(BattleStations.stationed(archer), "the archer is still on battle stations after the raid was fought off (the warned evening's watch came back)");
			if (now - beaten[0] >= 12 * BattleStations.EVERY) {
				helper.succeed();
			}
		});
	}
}
