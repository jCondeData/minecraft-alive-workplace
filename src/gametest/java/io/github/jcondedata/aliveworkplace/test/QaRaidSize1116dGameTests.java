package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.HostileSpawns;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.item.Items;

/**
 * QA (lane-d-1116-1907), B1320 from the spec: the questbook's "Guards: the Barracks" now says "Bigger villages draw
 * bigger raids: 3 raiders, plus one for every 4 villagers and one for every 2 guards (up to 4 more, in iron), never more
 * than 16." Written from that sentence, through the raid's real start (not only the scout's estimate): the size at
 * 0, 1, 2, 8, 9 and 10 guards, the iron on the guards' share, and the cap of 16 from both sides.
 */
public class QaRaidSize1116dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);

	/** The size the quest's sentence gives. */
	private static int questSize(int villagers, int guards) {
		return Math.min(16, 3 + villagers / 4 + Math.min(4, guards / 2));
	}

	/** A hall with four villagers, nothing left over from earlier batches; all of it undone at the end (as B208GameTests). */
	private static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		Lairs.forget(level);
		VillageRaids.forget();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).forgetPast(hall);
		ThreatData.get(level).forgetClock(hall);
		HostileSpawns.forget();
		Leftovers.after(helper, () -> {
			HostileSpawns.forget();
			cleanRaid(helper, hall);
			BanditCamps.forget(level);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
		});
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i, 2, 3));
		}
		level.setDayTime(39 * 24000L + 15000);
		helper.assertTrue(Lairs.near(level, hall).isEmpty() && Threats.LEVEL < 3, "setup: a lair near, or a hard threat level");
		return hall;
	}

	private static void cleanRaid(GameTestHelper helper, BlockPos hall) {
		helper.getLevel().getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
			m -> m.getTags().contains(VillageRaids.TAG) || m.getTags().contains(Threats.EXTRA_TAG)).forEach(Mob::discard);
		VillageRaids.forget();
		ThreatData.get(helper.getLevel()).forgetPast(hall);
	}

	private static boolean inIron(Mob mob) {
		return mob.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET) && mob.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE);
	}

	/**
	 * Starts a real raid of the plain monsters on {@code hall} for {@code villagers} and {@code guards} and checks it
	 * against the quest: how many come, that the scout's estimate agrees, and that the guards' share (beyond the plain
	 * 3 + villagers/4) comes in iron (spiders aside, they wear nothing). Adds what's wrong to {@code problems}.
	 */
	private static void raidOf(GameTestHelper helper, BlockPos hall, int villagers, int guards, long seed, List<String> problems) {
		ServerLevel level = helper.getLevel();
		int want = questSize(villagers, guards);
		int plain = Math.min(16, 3 + villagers / 4);
		int expected = VillageRaids.expected(level, hall, villagers, guards, Threats.MONSTERS);
		if (expected != want) {
			problems.add(villagers + "v/" + guards + "g: the estimate says " + expected + ", the quest " + want);
		}
		VillageRaids.Raid raid = VillageRaids.start(level, hall, villagers, guards, Double.NaN, Threats.on(Threats.MONSTERS).orElseThrow(),
			RandomSource.create(seed));
		if (raid == null) {
			problems.add(villagers + "v/" + guards + "g: no raid started");
			cleanRaid(helper, hall);
			return;
		}
		List<Mob> band = new ArrayList<>(VillageRaids.raiders(level, hall).stream().filter(m -> !m.getTags().contains(Threats.EXTRA_TAG)).toList());
		if (raid.raiders() != want || band.size() != want) {
			problems.add(villagers + "v/" + guards + "g: " + raid.raiders() + " raiders recorded, " + band.size() + " in the village; the quest says " + want);
		}
		long spiders = band.stream().filter(m -> m instanceof Spider).count();
		long iron = band.stream().filter(m -> !(m instanceof Spider) && inIron(m)).count();
		int share = Math.max(0, band.size() - plain);
		if (iron < share - spiders) {
			problems.add(villagers + "v/" + guards + "g: " + iron + " in iron (" + spiders + " spiders), the guards' share is " + share);
		}
		cleanRaid(helper, hall);
	}

	/** 12 villagers: 0 and 1 guard draw the plain 6 (one guard is less than 2); 2 guards draw 7, the 7th in iron. */
	//$ gametest_ticks_batch AREA '100' '"qaRaidSize1116dFew"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidSize1116dFew")
	public void qaFewGuardsDrawTheQuestsRaid(GameTestHelper helper) {
		BlockPos hall = village(helper);
		helper.runAfterDelay(2, () -> {
			List<String> problems = new ArrayList<>();
			raidOf(helper, hall, 12, 0, 13200L, problems);
			raidOf(helper, hall, 12, 1, 13201L, problems);
			raidOf(helper, hall, 12, 2, 13202L, problems);
			raidOf(helper, hall, 12, 3, 13203L, problems);
			helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
			helper.succeed();
		});
	}

	/** 12 villagers: 8 guards add the full 4 (10); 9 and 10 guards add no more than 4 ("up to 4 more"). */
	//$ gametest_ticks_batch AREA '100' '"qaRaidSize1116dMany"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidSize1116dMany")
	public void qaManyGuardsAddNoMoreThanFour(GameTestHelper helper) {
		BlockPos hall = village(helper);
		helper.runAfterDelay(2, () -> {
			List<String> problems = new ArrayList<>();
			raidOf(helper, hall, 12, 7, 13207L, problems);
			raidOf(helper, hall, 12, 8, 13208L, problems);
			raidOf(helper, hall, 12, 9, 13209L, problems);
			raidOf(helper, hall, 12, 10, 13210L, problems);
			helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
			helper.succeed();
		});
	}

	/**
	 * "Never more than 16", from both sides: 47 villagers (14 plain) with 2 guards draw 15 and with 4 draw 16; 51
	 * villagers (15) with 8 guards still 16; 52 villagers (16 plain) draw 16 with or without guards, none of it the
	 * guards' share.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRaidSize1116dCap"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidSize1116dCap")
	public void qaNeverMoreThanSixteen(GameTestHelper helper) {
		BlockPos hall = village(helper);
		helper.runAfterDelay(2, () -> {
			List<String> problems = new ArrayList<>();
			raidOf(helper, hall, 47, 2, 13247L, problems);
			raidOf(helper, hall, 47, 4, 13248L, problems);
			raidOf(helper, hall, 51, 8, 13251L, problems);
			raidOf(helper, hall, 52, 0, 13252L, problems);
			raidOf(helper, hall, 52, 10, 13253L, problems);
			helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
			helper.succeed();
		});
	}

	/** The quest gives the size once, in the sentence the raids above were checked against. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheBarracksQuestNamesEveryPartOfTheSize(GameTestHelper helper) {
		String quest = QaQuestbookClaims1116eBugTests.quest("Guards: the Barracks");
		String sentence = "Bigger villages draw bigger raids: 3 raiders, plus one for every 4 villagers and one for every 2 guards (up to 4 more, in iron), never more than 16.";
		helper.assertTrue(quest.contains("\"" + sentence + "\""), "the Barracks quest's raid sentence: " + quest);
		helper.assertTrue(quest.indexOf("Bigger villages draw bigger raids") == quest.lastIndexOf("Bigger villages draw bigger raids"),
			"the raid size is given twice in the quest");
		helper.succeed();
	}
}
