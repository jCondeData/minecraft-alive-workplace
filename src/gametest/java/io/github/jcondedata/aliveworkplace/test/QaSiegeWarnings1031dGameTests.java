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
 * QA (lane D, 2026-10-31) for A day's warning (ROADMAP 32.14) and Sieges III (32.6), written from the spec. What
 * WarningGameTests, QaWarningGameTests, BattleStationGameTests and QaBattleStationGameTests leave out: a Wall Tower
 * (not only a Lookout Tower) watches from a post 24 blocks off, not 25 and not 24.04 on the diagonal; "from noon" and
 * "at dusk" from both sides of the tick; the warning's sentences word for word (tomorrow, tonight, counted, the side and
 * the chronicle); the shut portcullis and the warning through a save and load at noon, told once; and battle stations
 * taken on the warned evening ending the morning after the siege.
 */
public class QaSiegeWarnings1031dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_warned_1031d");
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
	 * "A guard on the night watch whose post is within 24 blocks of a finished ... Wall Tower": with only a Wall Tower
	 * (no Lookout Tower), a post 24 blocks east of its middle is a source and the round warns; 25 blocks off is none,
	 * and neither is a post 17 east and 17 south (24.04 blocks: not within 24), while 14 east and 19 south (23.6) is.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaSiegeWarnWallTowerReach"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaSiegeWarnWallTowerReach")
	public void aWallTowerWatchesFromTwentyFourNotTwentyFiveOrTheDiagonal(GameTestHelper helper) {
		Village village = village(helper, new BlockPos(15, 2, 15), false, new BlockPos(0, 2, 0));
		ServerLevel level = village.level();
		List<BlockPos> towers = Warnings.towers(level, village.hall());
		helper.assertTrue(towers.size() == 1, "the village's towers: " + towers);
		BlockPos middle = towers.get(0);
		int y = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
		setAttack(village, NORTH);
		// 25 east: out of reach.
		BlockPos p25 = new BlockPos(middle.getX() + 25, y, middle.getZ());
		Villager g25 = guardAt(helper, new ItemStack(Items.BOW), p25);
		helper.assertTrue(Warnings.source(level, village.hall()).isEmpty(), "a post 25 blocks from the Wall Tower watches");
		Warnings.round(level, village.hall(), 10, 1);
		helper.assertTrue(ThreatData.get(level).warned(village.hall()) < ATTACK_DAY, "warned from a post 25 blocks off");
		g25.discard();
		level.setBlockAndUpdate(p25, Blocks.AIR.defaultBlockState());
		// 17 east, 17 south: 24.04 blocks.
		BlockPos diag = new BlockPos(middle.getX() + 17, y, middle.getZ() + 17);
		Villager gDiag = guardAt(helper, new ItemStack(Items.BOW), diag);
		helper.assertTrue(Warnings.source(level, village.hall()).isEmpty(), "a post 24.04 blocks (17, 17) from the Wall Tower watches");
		gDiag.discard();
		level.setBlockAndUpdate(diag, Blocks.AIR.defaultBlockState());
		// 14 east, 19 south: 23.6 blocks.
		BlockPos near = new BlockPos(middle.getX() + 14, y, middle.getZ() + 19);
		Villager gNear = guardAt(helper, new ItemStack(Items.BOW), near);
		helper.assertTrue(Warnings.source(level, village.hall()).map(s -> s.who().getString().equals(gNear.getName().getString())).orElse(false),
			"a post 23.6 blocks (14, 19) from the Wall Tower doesn't watch");
		gNear.discard();
		level.setBlockAndUpdate(near, Blocks.AIR.defaultBlockState());
		// 24 east: within reach, and the round warns.
		BlockPos p24 = new BlockPos(middle.getX() + 24, y, middle.getZ());
		Villager g24 = guardAt(helper, new ItemStack(Items.BOW), p24);
		helper.assertTrue(Warnings.source(level, village.hall()).map(s -> s.who().getString().equals(g24.getName().getString())).orElse(false),
			"a post exactly 24 blocks from the Wall Tower doesn't watch");
		Warnings.round(level, village.hall(), 10, 1);
		helper.assertTrue(ThreatData.get(level).warned(village.hall()) == ATTACK_DAY, "not warned from 24 blocks: " + ThreatData.get(level).warned(village.hall()));
		helper.succeed();
	}

	/**
	 * "On the day, from noon, the gates shut and the portcullis drops; at dusk the guards take battle stations and the
	 * villagers go in": not on the day before (even after noon), not at 11:59 (5999), from 6000; the evening not one
	 * tick before dusk, from dusk.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaSiegeWarnNoon"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaSiegeWarnNoon")
	public void fromNoonNotATickBeforeAndTheEveningFromDusk(GameTestHelper helper) {
		Village village = village(helper);
		ServerLevel level = village.level();
		BlockPos hall = village.hall();
		guard(helper, new ItemStack(Items.BOW), 16, 14);
		setAttack(village, NORTH);
		Warnings.round(level, hall, 10, 1);
		helper.assertTrue(ThreatData.get(level).warned(hall) == ATTACK_DAY, "not warned: " + ThreatData.get(level).warned(hall));
		// The day before, after noon (it is 12100 already): nothing shuts.
		helper.assertFalse(Warnings.ready(level, hall), "ready the day before the attack");
		helper.assertFalse(Sieges.portcullisDown(level, hall), "the portcullis dropped the day before");
		// The day, 5999: not yet.
		level.setDayTime(BASE + VillageNeeds.DAY + Warnings.NOON - 1);
		Gates.round(level, hall, 1);
		Warnings.round(level, hall, 10, 1);
		helper.assertFalse(Warnings.ready(level, hall), "ready at 5999");
		helper.assertFalse(Sieges.portcullisDown(level, hall), "the portcullis dropped before noon");
		helper.assertTrue(ThreatData.get(level).braced(hall).isEmpty(), "bars braced before noon");
		// 6000: shut and down.
		level.setDayTime(BASE + VillageNeeds.DAY + Warnings.NOON);
		Gates.round(level, hall, 1);
		Warnings.round(level, hall, 10, 1);
		helper.assertTrue(Warnings.ready(level, hall), "not ready at noon");
		Sieges.Status status = Sieges.status(level, hall);
		helper.assertTrue(status.gates() > 0 && status.open() == 0, "the gates at noon: " + status);
		helper.assertTrue(Sieges.portcullisDown(level, hall), "the portcullis isn't down at noon");
		helper.assertFalse(Warnings.evening(level, hall), "evening at noon");
		// Dusk from both sides.
		level.setDayTime(BASE + VillageNeeds.DAY + VillageRaids.DUSK - 1);
		helper.assertFalse(Warnings.evening(level, hall), "evening one tick before dusk");
		level.setDayTime(BASE + VillageNeeds.DAY + VillageRaids.DUSK);
		helper.assertTrue(Warnings.evening(level, hall), "not evening at dusk");
		helper.succeed();
	}

	/**
	 * The sentences word for word, as en_us.json has them: "Smoke on the north road: <who> will strike <village>
	 * tomorrow night." when told the day before; "... tonight." on the day; a scout's ", about N strong,"; the south
	 * road for an attack from the south; and the chronicle's "<guard> saw them coming: <who> from the north".
	 */
	//$ gametest_ticks_batch AREA '60' '"qaSiegeWarnSentences"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaSiegeWarnSentences")
	public void theWarningsSentencesWordForWord(GameTestHelper helper) {
		Village village = village(helper);
		ServerLevel level = village.level();
		BlockPos hall = village.hall();
		Villager archer = guard(helper, new ItemStack(Items.BOW), 16, 14);
		ThreatData.Attack attack = setAttack(village, NORTH);
		String who = Warnings.who(level, hall, attack).getString();
		String name = VillageHalls.name(level, hall).getString();
		helper.assertTrue(!who.isEmpty() && !name.isEmpty(), "who '" + who + "', village '" + name + "'");
		String tomorrow = Warnings.message(level, hall, attack, 0).getString();
		helper.assertTrue(tomorrow.equals("Smoke on the north road: " + who + " will strike " + name + " tomorrow night."), "tomorrow: " + tomorrow);
		String counted = Warnings.message(level, hall, attack, 7).getString();
		helper.assertTrue(counted.equals("Smoke on the north road: " + who + ", about 7 strong, will strike " + name + " tomorrow night."), "counted: " + counted);
		Warnings.round(level, hall, 10, 1);
		String chronicle = archer.getName().getString() + " saw them coming: " + who + " from the north";
		helper.assertTrue(village.entity().chronicle().stream().anyMatch(e -> e.text().getString().equals(chronicle)),
			"the chronicle: " + village.entity().chronicle().stream().map(e -> e.text().getString()).toList());
		// On the day: tonight.
		level.setDayTime(BASE + VillageNeeds.DAY + 1000);
		String tonight = Warnings.message(level, hall, attack, 0).getString();
		helper.assertTrue(tonight.equals("Smoke on the north road: " + who + " will strike " + name + " tonight."), "tonight: " + tonight);
		String tonightCounted = Warnings.message(level, hall, attack, 12).getString();
		helper.assertTrue(tonightCounted.equals("Smoke on the north road: " + who + ", about 12 strong, will strike " + name + " tonight."), "tonight counted: " + tonightCounted);
		// From the south.
		ThreatData.Attack south = new ThreatData.Attack(ATTACK_DAY, BESIEGERS, SOUTH, 13600);
		String fromSouth = Warnings.message(level, hall, south, 0).getString();
		helper.assertTrue(fromSouth.equals("Smoke on the south road: " + who + " will strike " + name + " tonight."), "from the south: " + fromSouth);
		helper.succeed();
	}

	/**
	 * A restart at noon of the warned day: the warning, the shut portcullis and the braced bars come back from the save,
	 * the next round still has them down and does not tell the village again.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaSiegeWarnReload"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaSiegeWarnReload")
	public void aRestartAtNoonKeepsTheBarsDownAndTellsNoOneTwice(GameTestHelper helper) {
		Village village = village(helper);
		ServerLevel level = village.level();
		BlockPos hall = village.hall();
		guard(helper, new ItemStack(Items.BOW), 16, 14);
		setAttack(village, NORTH);
		Warnings.round(level, hall, 10, 1);
		level.setDayTime(BASE + VillageNeeds.DAY + Warnings.NOON + 200);
		Gates.round(level, hall, 1);
		Warnings.round(level, hall, 10, 1);
		helper.assertTrue(Sieges.portcullisDown(level, hall), "the portcullis isn't down at noon");
		int braced = ThreatData.get(level).braced(hall).size();
		helper.assertTrue(braced > 0, "nothing braced");
		int entries = village.entity().chronicle().size();
		// Saved and loaded.
		ThreatData data = ThreatData.get(level);
		data.roundTrip(level.registryAccess());
		Sieges.forget();
		BattleStations.forget();
		helper.assertTrue(data.warned(hall) == ATTACK_DAY, "the warning after a load: " + data.warned(hall));
		helper.assertTrue(data.braced(hall).size() == braced, "the braced bars after a load: " + data.braced(hall).size() + " of " + braced);
		helper.assertTrue(Warnings.ready(level, hall), "not ready after a load");
		level.setDayTime(BASE + VillageNeeds.DAY + Warnings.NOON + 400);
		Gates.round(level, hall, 1);
		Warnings.round(level, hall, 10, 1);
		helper.assertTrue(Sieges.portcullisDown(level, hall), "the portcullis went up after a load");
		Sieges.Status status = Sieges.status(level, hall);
		helper.assertTrue(status.gates() > 0 && status.open() == 0, "the gates after a load: " + status);
		helper.assertTrue(village.entity().chronicle().size() == entries, "told again after a load");
		helper.succeed();
	}

	/**
	 * Battle stations end with the siege: the archer takes the Wall Tower's top on the warned evening, the siege begins
	 * and is lifted, and at the morning after he no longer has a place (back to his round).
	 */
	//$ gametest_ticks_batch AREA '900' '"qaSiegeWarnStationsEnd"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaSiegeWarnStationsEnd")
	public void battleStationsEndTheMorningAfter(GameTestHelper helper) {
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
		long[] lifted = {-1};
		helper.onEachTick(() -> {
			if (lifted[0] < 0) {
				helper.assertTrue(helper.getTick() - start <= 500, "not on the station after 500 ticks");
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
				ThreatData.Siege siege = Sieges.siege(level, hall).orElseThrow();
				husk.discard();
				Sieges.lift(level, siege);
				VillageRaids.forget(hall);
				level.setDayTime(BASE + 2 * VillageNeeds.DAY + 1000);
				lifted[0] = helper.getTick();
				return;
			}
			if (helper.getTick() - lifted[0] < 40) {
				return;
			}
			helper.assertFalse(Warnings.evening(level, hall), "still the warned evening the morning after");
			helper.assertFalse(BattleStations.stationed(archer), "the archer still has a battle station the morning after the siege");
			helper.succeed();
		});
	}
}
