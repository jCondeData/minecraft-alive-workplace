package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.MarketDays;
import io.github.jcondedata.aliveworkplace.hall.Treasury;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Chatter;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Names;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.WorkerLimits;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * ROADMAP 25.5, the server owner's controls: a cap on workers per village, how far workers path, and every "needs"
 * system (names, moods, sickness, couples, chatter, markets, festivals, raids, bandit camps, the treasury) switched off
 * really staying off. (Paths and repairs switched off: {@code BuilderGameTests}; traits, protection, unwatched villages
 * and partner shows have theirs in their own tests.)
 */
public class ServerControlsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos TAKEN = new BlockPos(4, 2, 4);
	private static final BlockPos FREE = new BlockPos(8, 2, 4);
	private static final BlockPos HALL = new BlockPos(9, 2, 9);

	/** One worker at a smithing table, a free fletching table beside it, and a jobless villager by the free one. */
	private static Villager village(GameTestHelper helper, int cap) {
		Leftovers.clear(helper);
		int max = WorkerLimits.MAX_PER_VILLAGE;
		int radius = WorkerLimits.RADIUS;
		WorkerLimits.MAX_PER_VILLAGE = cap;
		WorkerLimits.RADIUS = 8;
		Leftovers.after(helper, () -> {
			WorkerLimits.MAX_PER_VILLAGE = max;
			WorkerLimits.RADIUS = radius;
		});
		ServerLevel level = helper.getLevel();
		helper.setDayTime(1000); // a working morning: sleeping villagers look for no job
		helper.setBlock(TAKEN, Blocks.SMITHING_TABLE);
		helper.setBlock(FREE, Blocks.FLETCHING_TABLE);
		Villager worker = helper.spawn(EntityType.VILLAGER, TAKEN.south());
		Jobs.employ(level, worker, helper.absolutePos(TAKEN), PoiTypes.TOOLSMITH, VillagerProfession.TOOLSMITH);
		return helper.spawn(EntityType.VILLAGER, FREE.south(6)); // a walk away, as villagers usually are
	}

	/** With a cap of one worker, a jobless villager next to a free workstation stays jobless and leaves it free. */
	//$ gametest_ticks_batch AREA '700' '"workerCapHoldsBack"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "workerCapHoldsBack")
	public void aFullVillageTakesNoMoreWorkers(GameTestHelper helper) {
		Villager jobless = village(helper, 1);
		ServerLevel level = helper.getLevel();
		BlockPos free = helper.absolutePos(FREE);
		// The table is hidden from the villager's job search, so it isn't even picked (and if it were, let go at once).
		int[] heldTicks = {0};
		boolean[] ordered = {false};
		helper.onEachTick(() -> {
			if (ordered[0]) {
				return;
			}
			helper.assertTrue(jobless.getVillagerData().getProfession() == VillagerProfession.NONE,
				"took a job in a full village: " + jobless.getVillagerData().getProfession());
			if (level.getPoiManager().getCountInRange(h -> true, free, 0, PoiManager.Occupancy.HAS_SPACE) == 0) {
				heldTicks[0]++;
			}
		});
		helper.runAtTickTime(600, () -> {
			helper.assertTrue(jobless.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(), "the jobless villager has a job site");
			helper.assertTrue(heldTicks[0] < 20, "the free fletching table was held " + heldTicks[0] + " of 600 ticks");
			// An order isn't capped: the hall's job menu and the admin tools still give a job.
			ordered[0] = true;
			Jobs.employ(level, jobless, free, PoiTypes.FLETCHER, VillagerProfession.FLETCHER);
			helper.assertTrue(jobless.getVillagerData().getProfession() == VillagerProfession.FLETCHER, "an order was refused by the cap");
			helper.succeed();
		});
	}

	/** The same village with room for two: the jobless villager becomes a fletcher (so the test above proves the cap). */
	//$ gametest_ticks_batch AREA '1200' '"workerCapRoom"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "workerCapRoom")
	public void aVillageWithRoomTakesTheWorker(GameTestHelper helper) {
		Villager jobless = village(helper, 2);
		helper.succeedWhen(() -> helper.assertTrue(jobless.getVillagerData().getProfession() == VillagerProfession.FLETCHER,
			"still " + jobless.getVillagerData().getProfession()));
	}

	/** The cap counts what's taken: a lower cap fires nobody, and a picked workstation is let go only when the village is full. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theCapCountsTakenWorkstations(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int max = WorkerLimits.MAX_PER_VILLAGE;
		try {
			GlobalPos site = GlobalPos.of(level.dimension(), helper.absolutePos(BlockPos.ZERO));
			WorkerLimits.MAX_PER_VILLAGE = 0;
			helper.assertFalse(WorkerLimits.full(level, site, false), "0 means no cap");
			helper.assertTrue(WorkplaceConfig.parse("{}").maxWorkersPerVillage == 0 && WorkplaceConfig.parse("{}").workerPathRange == 48,
				"the defaults change nothing (no cap, vanilla's 48 blocks)");
			WorkplaceConfig high = WorkplaceConfig.parse("{\"maxWorkersPerVillage\": 9999, \"workerPathRange\": 500}");
			WorkplaceConfig low = WorkplaceConfig.parse("{\"maxWorkersPerVillage\": -3, \"workerPathRange\": 2}");
			helper.assertTrue(high.maxWorkersPerVillage == 500 && high.workerPathRange == 128, "not clamped down: " + high.maxWorkersPerVillage + ", "
				+ high.workerPathRange);
			helper.assertTrue(low.maxWorkersPerVillage == 0 && low.workerPathRange == 16, "not clamped up: " + low.maxWorkersPerVillage + ", "
				+ low.workerPathRange);
		} finally {
			WorkerLimits.MAX_PER_VILLAGE = max;
		}
		helper.succeed();
	}

	/**
	 * Workers path as far as the config says (a modifier that isn't saved, so taking the setting back leaves no trace);
	 * jobless villagers keep vanilla's range.
	 */
	//$ gametest_ticks_batch AREA '100' '"workerPathRange"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "workerPathRange")
	public void workersPathAsFarAsTheConfigSays(GameTestHelper helper) {
		Leftovers.clear(helper);
		int range = WorkerLimits.PATH_RANGE;
		WorkerLimits.PATH_RANGE = 24;
		Leftovers.after(helper, () -> WorkerLimits.PATH_RANGE = range);
		ServerLevel level = helper.getLevel();
		helper.setBlock(TAKEN, Blocks.SMITHING_TABLE);
		Villager worker = helper.spawn(EntityType.VILLAGER, TAKEN.south());
		Jobs.employ(level, worker, helper.absolutePos(TAKEN), PoiTypes.TOOLSMITH, VillagerProfession.TOOLSMITH);
		Villager jobless = helper.spawn(EntityType.VILLAGER, FREE.south(2));
		jobless.setNoAi(true);
		helper.runAtTickTime(30, () -> {
			helper.assertTrue(worker.getAttributeValue(Attributes.FOLLOW_RANGE) == 24, "worker's range " + worker.getAttributeValue(Attributes.FOLLOW_RANGE));
			helper.assertTrue(jobless.getAttributeValue(Attributes.FOLLOW_RANGE) == 48, "jobless range " + jobless.getAttributeValue(Attributes.FOLLOW_RANGE));
			CompoundTag saved = worker.saveWithoutId(new CompoundTag());
			helper.assertFalse(saved.toString().contains("worker_path_range"), "the range was saved with the villager");
			WorkerLimits.PATH_RANGE = 48;
		});
		helper.runAtTickTime(60, () -> {
			helper.assertTrue(worker.getAttributeValue(Attributes.FOLLOW_RANGE) == 48, "back to vanilla's range: "
				+ worker.getAttributeValue(Attributes.FOLLOW_RANGE));
			helper.succeed();
		});
	}

	/**
	 * A village with a hall, every needs system switched off, run through two weeks of the hall's rounds at every time of
	 * day: nobody gets a name, a mood, ill or courted; no market, festival, raid, bandit camp or takings. Each round has a
	 * whole day's chance (illness is made certain), so with the switches on most of these would have happened: their own
	 * tests show them happening.
	 */
	//$ gametest_ticks_batch AREA '100' '"needsSwitchedOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "needsSwitchedOff")
	public void everyNeedsSystemSwitchedOffStaysOff(GameTestHelper helper) {
		Leftovers.clear(helper);
		boolean names = Names.ENABLED, moods = Moods.ENABLED, sickness = Sickness.ENABLED, couples = Couples.ENABLED, markets = MarketDays.ENABLED,
			festivals = Festivals.ENABLED, raids = VillageRaids.ENABLED, camps = BanditCamps.ENABLED, treasury = Treasury.ENABLED;
		float daily = Sickness.DAILY;
		int every = VillageNeeds.CHECK_EVERY;
		int radius = VillageHalls.RADIUS;
		Names.ENABLED = Moods.ENABLED = Sickness.ENABLED = Couples.ENABLED = MarketDays.ENABLED = Festivals.ENABLED = VillageRaids.ENABLED
			= BanditCamps.ENABLED = Treasury.ENABLED = false;
		Sickness.DAILY = 1000f; // certain, were it on
		VillageNeeds.CHECK_EVERY = (int) VillageNeeds.DAY; // one round a day: each round has a day's chance
		VillageHalls.RADIUS = 16;
		ServerLevel level = helper.getLevel();
		var sites = BuildSiteManager.get(level);
		var placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(1, 2, 13)), Rotation.NONE, Mirror.NONE);
		Leftovers.after(helper, () -> {
			Names.ENABLED = names;
			Moods.ENABLED = moods;
			Sickness.ENABLED = sickness;
			Couples.ENABLED = couples;
			MarketDays.ENABLED = markets;
			Festivals.ENABLED = festivals;
			VillageRaids.ENABLED = raids;
			BanditCamps.ENABLED = camps;
			Treasury.ENABLED = treasury;
			Sickness.DAILY = daily;
			VillageNeeds.CHECK_EVERY = every;
			VillageHalls.RADIUS = radius;
			sites.forgetFinished(placement);
			VillageRaids.forget();
			Festivals.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		sites.recordFinished(StarterBlueprints.MARKET_SQUARE.id(), placement, java.util.UUID.randomUUID());
		helper.assertTrue(MarketDays.square(level, hall).isPresent(), "no market square to hold a market on");
		List<Villager> village = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(2 + (i % 6) * 2, 2, 2 + (i / 6) * 3));
			v.setNoAi(true);
			village.add(v);
		}
		long festivalDay = entity.festivalDay();
		// The hall's own tick (its first round runs at once): the treasury's switch is checked there.
		entity.setLastTaxDay(Chronicle.day(level) - 1);
		VillageHallBlockEntity.serverTick(level, hall, level.getBlockState(hall), entity);
		long start = level.getDayTime() - level.getDayTime() % VillageNeeds.DAY;
		long[] raided = {Long.MIN_VALUE};
		for (int day = 0; day < 16; day++) {
			for (int hour : new int[] {1000, 2000, 4000, 6000, 8000, 10000, 12000, 14000, 16000, 18000, 20000, 22000}) {
				level.setDayTime(start + day * VillageNeeds.DAY + hour);
				VillageNeeds.check(level, hall);
				MarketDays.tick(level, hall, entity);
				BanditCamps.round(level, hall);
				Festivals.round(level, hall, entity, village.size());
				Couples.round(level, hall);
				VillageRaids.tick(level, hall, village.size(), 0, -100, d -> raided[0] = d);
			}
		}
		List<String> problems = new ArrayList<>();
		for (Villager v : village) {
			if (v.hasCustomName()) {
				problems.add("named " + v.getCustomName().getString());
			}
			if (Moods.of(v) != null) {
				problems.add("a mood");
			}
			if (Sickness.isIll(v)) {
				problems.add("ill");
			}
			if (Couples.partner(v) != null) {
				problems.add("courting");
			}
		}
		if (!level.getEntitiesOfClass(WanderingTrader.class, new AABB(hall).inflate(40)).isEmpty()) {
			problems.add("market traders came");
		}
		if (entity.festivalDay() != festivalDay) {
			problems.add("a festival was planned for day " + entity.festivalDay());
		}
		if (raided[0] != Long.MIN_VALUE || VillageRaids.active(hall).isPresent()) {
			problems.add("a raid");
		}
		if (BanditCamps.near(level, hall).isPresent()) {
			problems.add("a bandit camp");
		}
		if (entity.treasury() != 0) {
			problems.add("takings " + entity.treasury());
		}
		helper.assertTrue(problems.isEmpty(), "with the switches off: " + String.join(", ", problems.stream().distinct().toList()));
		helper.succeed();
	}

	/** Chatter switched off: a player standing among idle villagers of a hall village hears nothing. */
	//$ gametest_ticks_batch AREA '500' '"chatterSwitchedOff"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "chatterSwitchedOff")
	public void chatterSwitchedOffSaysNothing(GameTestHelper helper) {
		ServerPlayer player = chatter(helper, false);
		helper.runAtTickTime(450, () -> {
			helper.assertFalse(Chatter.spokeTo(player), "a villager chattered with the switch off");
			helper.succeed();
		});
	}

	/** The same with chatter on: someone soon says something (so the test above proves the switch). */
	//$ gametest_ticks_batch AREA '1000' '"chatterSwitchedOn"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "chatterSwitchedOn")
	public void chatterSwitchedOnTalks(GameTestHelper helper) {
		ServerPlayer player = chatter(helper, true);
		helper.succeedWhen(() -> helper.assertTrue(Chatter.spokeTo(player), "nobody chattered"));
	}

	private static ServerPlayer chatter(GameTestHelper helper, boolean on) {
		Leftovers.clear(helper);
		boolean chatter = Chatter.ENABLED;
		int radius = VillageHalls.RADIUS;
		Chatter.ENABLED = on;
		VillageHalls.RADIUS = 16;
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> {
			Chatter.ENABLED = chatter;
			VillageHalls.RADIUS = radius;
			level.getServer().getPlayerList().remove(player);
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(4 + i * 2, 2, 6)).setNoAi(true);
		}
		BlockPos at = helper.absolutePos(new BlockPos(7, 2, 4));
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		return player;
	}
}
