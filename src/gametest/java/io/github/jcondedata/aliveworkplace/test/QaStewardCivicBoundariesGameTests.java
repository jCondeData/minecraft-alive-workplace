package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane-c-1025-0953) on 27.12's Steward rules, from its text, at the edges StewardCivicRulesGameTests steps over:
 * "a villager died in the last 30 days, 8 or more villagers" (a death 29 days back still counts, 30 days back no
 * longer; 7 villagers are too few), "raided_within {days}" from the hall's last raid day (6 days ago is within 7, 7
 * days ago is not), and "3 or more children" for the Schoolhouse (2 children and a crowd of grown-ups are not).
 */
public class QaStewardCivicBoundariesGameTests {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	private static BlockPos hall(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> helper.getLevel().removeBlock(hall, false));
		((VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall)).setRank(VillageRanks.Rank.HAMLET);
		return hall;
	}

	private static void villagers(GameTestHelper helper, int n, int row, boolean children) {
		for (int i = 0; i < n; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(2 + (i % 9) * 2, 2, row + (i / 9) * 2));
			v.setNoAi(true);
			if (children) {
				v.setAge(-24000);
			}
		}
	}

	private static List<String> wishes(GameTestHelper helper, BlockPos hall, String rule) {
		ServerLevel level = helper.getLevel();
		List<StewardRules.Rule> ours = StewardRules.all().stream().filter(r -> r.id().getPath().equals(rule)).toList();
		helper.assertTrue(ours.size() == 1, "setup: the shipped rule " + rule + " isn't loaded");
		return StewardWishes.rank(ours, StewardConditions.Facts.of(level, hall), StewardWishes.State.EMPTY, StewardWishes.day(level))
			.stream().map(w -> w.rule().getPath()).toList();
	}

	private static StewardConditions.Check check(GameTestHelper helper, BlockPos hall, String json) {
		StewardRules.Rule rule = StewardRules.read(AliveWorkplace.id("test/qa_civic"), com.google.gson.JsonParser.parseString(
			"{\"when\": [" + json + "], \"do\": {\"assign_jobs\": {}}, \"priority\": 1, \"why\": \"x\"}"));
		return rule.when().get(0).test(StewardConditions.Facts.of(helper.getLevel(), hall));
	}

	/** The days since the death, by moving the clock on and back (in the same tick, so nothing else sees it). */
	private static List<String> daysLater(GameTestHelper helper, BlockPos hall, int days) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		level.setDayTime(time + days * VillageNeeds.DAY);
		try {
			return wishes(helper, hall, "graveyard");
		} finally {
			level.setDayTime(time);
		}
	}

	/**
	 * Eight villagers and a death today, 29 days on (the graveyard is wished for), 30 days on (it no longer is); seven
	 * villagers with the same fresh death: none.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaCivicGraveyardEdges"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaCivicGraveyardEdges")
	public void aGraveyardIsWishedForWithinThirtyDaysOfADeathAndFromEightVillagers(GameTestHelper helper) {
		BlockPos hall = hall(helper);
		villagers(helper, 7, 19, false);
		Chronicle.record(helper.getLevel(), hall, Chronicle.Kind.DEATH, Component.literal("a death"));
		helper.assertTrue(wishes(helper, hall, "graveyard").isEmpty(), "seven villagers and a death got a graveyard");
		villagers(helper, 1, 21, false);
		helper.assertTrue(wishes(helper, hall, "graveyard").contains("graveyard"), "eight villagers and a death today: no graveyard");
		helper.assertTrue(daysLater(helper, hall, 29).contains("graveyard"), "a death 29 days back no longer counted");
		helper.assertTrue(daysLater(helper, hall, 30).isEmpty(), "a death 30 days back still got a graveyard");
		helper.succeed();
	}

	/** The last raid today, 6 days ago and 7 days ago against raided_within 7: held (0 days), held (6), not held (7). */
	//$ gametest_ticks_batch AREA '40' '"qaCivicRaidEdges"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaCivicRaidEdges")
	public void aRaidSixDaysAgoIsWithinSevenAndSevenDaysAgoIsNot(GameTestHelper helper) {
		BlockPos hall = hall(helper);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall);
		long today = Chronicle.day(helper.getLevel());
		long[] ago = {0, 6, 7};
		boolean[] held = {true, true, false};
		for (int i = 0; i < ago.length; i++) {
			entity.setLastRaidDay(today - ago[i]);
			StewardConditions.Check c = check(helper, hall, "{\"raided_within\": {\"days\": 7}}");
			helper.assertTrue(c.held() == held[i] && c.value() == ago[i], "a raid " + ago[i] + " days ago: expected " + (held[i] ? "held" : "not held")
				+ " with " + ago[i] + ", got " + c.held() + " with " + c.value() + " (" + c.shown().getString() + ")");
		}
		helper.succeed();
	}

	/** Two children and nine grown-ups: no Schoolhouse; the third child: the Schoolhouse. */
	//$ gametest_ticks_batch AREA '40' '"qaCivicSchoolEdges"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaCivicSchoolEdges")
	public void twoChildrenAndACrowdOfGrownUpsGetNoSchoolhouse(GameTestHelper helper) {
		BlockPos hall = hall(helper);
		villagers(helper, 9, 19, false);
		villagers(helper, 2, 3, true);
		helper.assertTrue(wishes(helper, hall, "schoolhouse").isEmpty(), "two children got a schoolhouse");
		villagers(helper, 1, 5, true);
		helper.assertTrue(wishes(helper, hall, "schoolhouse").contains("schoolhouse"), "three children got no schoolhouse");
		helper.succeed();
	}
}
