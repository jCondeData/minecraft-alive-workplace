package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.rules.Conditions;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Rewards;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1031-0653) for the quest engine (ROADMAP 31.2), written from the spec: each condition on both sides of its
 * edge, {@code not}, {@code chance} at its ends, {@code quest_done} per village, money times the rank factor, the
 * {@code days} deadline to the tick, a {@code bring} into the store or a station's chests, a kill in the village or
 * anywhere, and {@code villageQuests} off leaving the rest of the round alone.
 */
public class QaStoryEngine1031GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(9, 2, 9);

	private static VillageHallBlockEntity hall(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		entity.setLastQuestDay(Long.MAX_VALUE / 2); // the board posts nothing of its own
		return entity;
	}

	private static Condition cond(String json) {
		return Conditions.parse(JsonParser.parseString(json).getAsJsonObject());
	}

	private static ResourceLocation file(String name) {
		return AliveWorkplace.id("qa_engine/" + name);
	}

	private static Quest quest(ServerLevel level, String name, long posted, long due, Objectives.Objective objective) {
		return new Quest(UUID.randomUUID(), file(name), "hall", Optional.empty(), "Ana", posted, due, List.of(objective), new int[1], List.of());
	}

	private static int count(ServerLevel level, BlockPos chest, Item item) {
		return level.getBlockEntity(chest) instanceof Container c ? c.countItem(item) : -1;
	}

	private static boolean open(ServerLevel level, BlockPos hall, Quest q) {
		return Stories.open(level, hall).stream().anyMatch(o -> o.id.equals(q.id));
	}

	/** villagers, food_below, rank_at_least, quest_done and not, each from both sides of its edge; chance at 0 and 1. */
	//$ gametest_ticks_batch AREA '80' '"qaEngineConditionEdges"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaEngineConditionEdges")
	public void everyConditionHoldsOnlyOnItsSideOfTheEdge(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		for (BlockPos p : List.of(new BlockPos(4, 2, 4), new BlockPos(14, 2, 4))) {
			Villager v = helper.spawn(EntityType.VILLAGER, p);
			v.setNoAi(true);
		}
		helper.runAfterDelay(3, () -> {
			// villagers: 2 live here
			helper.assertTrue(cond("{\"type\": \"villagers\", \"count\": 2}").met(level, hall), "2 of 2 villagers");
			helper.assertFalse(cond("{\"type\": \"villagers\", \"count\": 3}").met(level, hall), "2 of 3 villagers held");
			helper.assertTrue(cond("{\"type\": \"not\", \"condition\": {\"type\": \"villagers\", \"count\": 3}}").met(level, hall), "not 3 villagers");
			helper.assertFalse(cond("{\"type\": \"not\", \"condition\": {\"type\": \"villagers\", \"count\": 2}}").met(level, hall), "not 2 villagers held");
			String line = cond("{\"type\": \"villagers\", \"count\": 3}").progress(level, hall).line().getString();
			helper.assertTrue(line.equals("Villagers: 2 of 3"), "the villagers line: " + line);

			// food_below: fewer than, strictly
			helper.assertTrue(cond("{\"type\": \"food_below\", \"count\": 1}").met(level, hall), "no store: 0 below 1");
			helper.assertFalse(cond("{\"type\": \"food_below\", \"count\": 0}").met(level, hall), "0 below 0 held");
			helper.setBlock(new BlockPos(11, 2, 13), ModBlocks.STOREHOUSE);
			helper.setBlock(new BlockPos(11, 2, 14), Blocks.CHEST);
			((Container) level.getBlockEntity(helper.absolutePos(new BlockPos(11, 2, 14)))).setItem(0, new ItemStack(Items.BREAD, 5));
			long food = VillageHalls.food(level, hall);
			helper.assertTrue(food > 0, "the store's bread isn't counted: " + food);
			helper.assertFalse(cond("{\"type\": \"food_below\", \"count\": " + food + "}").met(level, hall), food + " below " + food + " held");
			helper.assertTrue(cond("{\"type\": \"food_below\", \"count\": " + (food + 1) + "}").met(level, hall), food + " below " + (food + 1));

			// rank_at_least
			entity.setRank(VillageRanks.Rank.TOWN);
			helper.assertTrue(cond("{\"type\": \"rank_at_least\", \"rank\": \"town\"}").met(level, hall), "a town is at least a town");
			helper.assertTrue(cond("{\"type\": \"rank_at_least\", \"rank\": \"village\"}").met(level, hall), "a town is at least a village");
			helper.assertFalse(cond("{\"type\": \"rank_at_least\", \"rank\": \"city\"}").met(level, hall), "a town counted as a city");

			// chance at its ends, many rolls; not of each
			Condition always = cond("{\"type\": \"chance\", \"chance\": 1}");
			Condition never = cond("{\"type\": \"chance\", \"chance\": 0}");
			Condition notNever = cond("{\"type\": \"not\", \"condition\": {\"type\": \"chance\", \"chance\": 0}}");
			for (int i = 0; i < 200; i++) {
				helper.assertTrue(always.met(level, hall) && !never.met(level, hall) && notNever.met(level, hall), "chance rolled wrong at its end, roll " + i);
			}
			String eight = cond("{\"type\": \"chance\", \"chance\": 0.08}").progress(level, hall).line().getString();
			String half = cond("{\"type\": \"chance\", \"chance\": 0.5}").progress(level, hall).line().getString();
			String eleven = cond("{\"type\": \"chance\", \"chance\": 0.11}").progress(level, hall).line().getString();
			helper.assertTrue(eight.equals("Luck (an 8% chance)") && half.equals("Luck (a 50% chance)") && eleven.equals("Luck (an 11% chance)"),
				"the chance lines: " + eight + " / " + half + " / " + eleven);

			// quest_done: this village only
			ResourceLocation key = file("key");
			Condition done = cond("{\"type\": \"quest_done\", \"quest\": \"" + key + "\"}");
			helper.assertFalse(done.met(level, hall), "done before anyone did it");
			BlockPos other = hall.offset(500, 0, 500);
			Stories.Data.get(level).noteFinished(other, key);
			helper.assertFalse(done.met(level, hall), "another village's finish counted here");
			Stories.Data.get(level).noteFinished(hall, key);
			helper.assertTrue(done.met(level, hall), "finished here but not done");
			Stories.Data.get(level).remove(other);
			helper.succeed();
		});
	}

	/** money: emeralds times a quarter more a rank (rank_factor false: as written); "1 emerald" in the singular. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void moneyIsTimesTheRankFactor(GameTestHelper helper) {
		Rewards.Reward four = Rewards.parse(JsonParser.parseString("{\"type\": \"money\", \"emeralds\": 4}").getAsJsonObject());
		Rewards.Reward flat = Rewards.parse(JsonParser.parseString("{\"type\": \"money\", \"emeralds\": 4, \"rank_factor\": false}").getAsJsonObject());
		Rewards.Reward per = Rewards.parse(JsonParser.parseString("{\"type\": \"money\", \"emeralds\": 1, \"per_count\": 8}").getAsJsonObject());
		List<Objectives.Objective> none = List.of(new Objectives.Wait(1));
		List<Objectives.Objective> bring32 = List.of(new Objectives.Bring("minecraft:wheat", 32, "hall", Optional.empty()));
		int[] expected = {4, 5, 6, 7};
		for (VillageRanks.Rank rank : VillageRanks.Rank.values()) {
			float f = VillageRanks.questRewardFactor(rank);
			int got = Rewards.emeralds(List.of(four.resolve(none, f)));
			helper.assertTrue(got == expected[rank.ordinal()], rank + ": 4 emeralds became " + got);
			int asWritten = Rewards.emeralds(List.of(flat.resolve(none, f)));
			helper.assertTrue(asWritten == 4, rank + ": rank_factor false paid " + asWritten);
		}
		int perHamlet = Rewards.emeralds(List.of(per.resolve(bring32, VillageRanks.questRewardFactor(VillageRanks.Rank.HAMLET))));
		helper.assertTrue(perHamlet == 5, "1 + 32/8 at a hamlet: " + perHamlet);
		int perCity = Rewards.emeralds(List.of(per.resolve(bring32, VillageRanks.questRewardFactor(VillageRanks.Rank.CITY))));
		helper.assertTrue(perCity == Math.round(5 * 1.75f), "1 + 32/8 at a city: " + perCity);
		String one = Money.describe(Money.DOLLARS_PER_EMERALD, 1).getString();
		String two = Money.describe(2L * Money.DOLLARS_PER_EMERALD, 2).getString();
		helper.assertTrue(one.equals("1 emerald") && two.equals("2 emeralds"), "money reads: " + one + " / " + two);
		helper.succeed();
	}

	/**
	 * days: a quest comes down at its due tick, not one before; with villageQuests off the round still counts a wait
	 * to its end (the quest is done) and still takes overdue quests down.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaEngineDeadlines"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaEngineDeadlines")
	public void aQuestComesDownOnItsDueTickAndTheSwitchLeavesTheRoundAlone(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> Stories.ENABLED = true);
		helper.runAfterDelay(3, () -> {
			long now = level.getGameTime();
			Quest dueNow = quest(level, "due_now", now - 100, now, new Objectives.Bring("minecraft:apple", 3, "hall", Optional.empty()));
			Quest dueNext = quest(level, "due_next", now - 100, now + 1, new Objectives.Bring("minecraft:apple", 3, "hall", Optional.empty()));
			Stories.post(level, hall, dueNow);
			Stories.post(level, hall, dueNext);
			Stories.round(level, hall, entity);
			helper.assertFalse(open(level, hall, dueNow), "still up on its due tick");
			helper.assertTrue(open(level, hall, dueNext), "came down a tick early");

			// The switch off: no new quests, but the rest of the round goes on.
			Stories.ENABLED = false;
			int doneBefore = entity.questsDone();
			Quest partWay = quest(level, "part_way", now - 2 * VillageNeeds.DAY, -1, new Objectives.Wait(3));
			Stories.post(level, hall, partWay);
			Quest waited = quest(level, "waited", now - VillageNeeds.DAY, -1, new Objectives.Wait(1));
			Quest overdue = quest(level, "overdue", now - 200, now - 1, new Objectives.Bring("minecraft:apple", 3, "hall", Optional.empty()));
			Stories.post(level, hall, waited);
			Stories.post(level, hall, overdue);
			Stories.round(level, hall, entity);
			helper.assertFalse(open(level, hall, waited), "a day's wait isn't done with villageQuests off: " + waited);
			helper.assertTrue(entity.questsDone() == doneBefore + 1, "the wait wasn't counted done: " + entity.questsDone() + " vs " + doneBefore);
			helper.assertFalse(open(level, hall, overdue), "an overdue quest stayed up with villageQuests off");
			helper.assertTrue(open(level, hall, partWay) && partWay.progress[0] == 2, "2 of a 3-day wait: " + partWay);
			helper.assertTrue(open(level, hall, dueNext), "the quest not yet due went with the switch");
			Stories.Data.get(level).remove(hall);
			helper.succeed();
		});
	}

	/** bring: a hall quest's goods go into the store; one for a worker's station into that station's chests, not the store. */
	//$ gametest_ticks_batch AREA '60' '"qaEngineBringInto"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaEngineBringInto")
	public void aBringGoesIntoTheStoreOrTheStationsChests(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		helper.setBlock(new BlockPos(11, 2, 13), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(11, 2, 14), Blocks.CHEST);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.SMITHING_TABLE);
		helper.setBlock(new BlockPos(3, 2, 2), Blocks.CHEST);
		BlockPos store = helper.absolutePos(new BlockPos(11, 2, 14));
		BlockPos stationChest = helper.absolutePos(new BlockPos(3, 2, 2));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		player.getInventory().add(new ItemStack(Items.APPLE, 5));
		helper.runAfterDelay(3, () -> {
			long now = level.getGameTime();
			Quest toHall = quest(level, "to_hall", now, now + VillageNeeds.DAY, new Objectives.Bring("minecraft:apple", 3, "hall", Optional.empty()));
			Stories.post(level, hall, toHall);
			int given = Stories.handIn(player, hall, toHall.id);
			helper.assertTrue(given == 3 && player.getInventory().countItem(Items.APPLE) == 2, "handed in " + given + ", left " + player.getInventory().countItem(Items.APPLE));
			helper.assertTrue(count(level, store, Items.APPLE) == 3, "the store holds " + count(level, store, Items.APPLE) + " apples");
			helper.assertFalse(open(level, hall, toHall), "done but still up");

			Quest toStation = quest(level, "to_station", now, now + VillageNeeds.DAY,
				new Objectives.Bring("minecraft:apple", 2, "station", Optional.of(helper.absolutePos(new BlockPos(2, 2, 2)))));
			Stories.post(level, hall, toStation);
			given = Stories.handIn(player, hall, toStation.id);
			helper.assertTrue(given == 2, "handed in at the station: " + given);
			helper.assertTrue(count(level, stationChest, Items.APPLE) == 2, "the station's chest holds " + count(level, stationChest, Items.APPLE));
			helper.assertTrue(count(level, store, Items.APPLE) == 3, "the station's apples went to the store: " + count(level, store, Items.APPLE));

			// An item tag takes every item in it, and nothing else.
			player.getInventory().add(new ItemStack(Items.OAK_LOG, 2));
			player.getInventory().add(new ItemStack(Items.BIRCH_LOG, 2));
			player.getInventory().add(new ItemStack(Items.OAK_PLANKS, 4));
			Quest logs = quest(level, "logs", now, now + VillageNeeds.DAY, new Objectives.Bring("#minecraft:logs", 3, "hall", Optional.empty()));
			Stories.post(level, hall, logs);
			given = Stories.handIn(player, hall, logs.id);
			int logsLeft = player.getInventory().countItem(Items.OAK_LOG) + player.getInventory().countItem(Items.BIRCH_LOG);
			helper.assertTrue(given == 3 && logsLeft == 1 && player.getInventory().countItem(Items.OAK_PLANKS) == 4,
				"the logs tag took " + given + ", left " + logsLeft + " logs and " + player.getInventory().countItem(Items.OAK_PLANKS) + " planks");
			Stories.Data.get(level).remove(hall);
			helper.succeed();
		});
	}

	/**
	 * repeatable: a one-time quest file can't be done twice by the same player in the village, but another player may
	 * do it; a repeatable one the same player may do again.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaEngineRepeatable"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaEngineRepeatable")
	public void aOneTimeQuestIsOncePerPlayerARepeatableOneIsNot(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		helper.setBlock(new BlockPos(11, 2, 13), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(11, 2, 14), Blocks.CHEST);
		ServerPlayer first = helper.makeMockServerPlayerInLevel();
		ServerPlayer second = helper.makeMockServerPlayerInLevel();
		for (ServerPlayer p : List.of(first, second)) {
			p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			p.getInventory().add(new ItemStack(Items.APPLE, 10));
		}
		helper.runAfterDelay(3, () -> {
			var manager = level.getServer().getResourceManager();
			java.util.Map<ResourceLocation, net.minecraft.server.packs.resources.Resource> real =
				manager.listResources(io.github.jcondedata.aliveworkplace.story.QuestFiles.FOLDER, p -> p.getPath().endsWith(".json"));
			net.minecraft.server.packs.resources.Resource any = real.values().iterator().next();
			java.util.Map<ResourceLocation, net.minecraft.server.packs.resources.Resource> withOurs = new java.util.HashMap<>(real);
			for (String[] f : new String[][] {{"once", "false"}, {"again", "true"}}) {
				String json = "{\"giver\": \"hall\", \"weight\": 0, \"repeatable\": " + f[1]
					+ ", \"objectives\": [{\"type\": \"bring\", \"item\": \"minecraft:apple\", \"count\": 1}]}";
				withOurs.put(AliveWorkplace.id("quests/qa_engine/" + f[0] + ".json"), new net.minecraft.server.packs.resources.Resource(any.source(),
					() -> new java.io.ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
			}
			try {
				io.github.jcondedata.aliveworkplace.story.QuestFiles.load(withOurs);
				helper.assertTrue(io.github.jcondedata.aliveworkplace.story.QuestFiles.get(file("once")).isPresent(), "the test file didn't load");
				long now = level.getGameTime();
				Objectives.Bring apple = new Objectives.Bring("minecraft:apple", 1, "hall", Optional.empty());
				Quest once1 = quest(level, "once", now, now + VillageNeeds.DAY, apple);
				Stories.post(level, hall, once1);
				helper.assertTrue(Stories.handIn(first, hall, once1.id) == 1 && !open(level, hall, once1), "the first time");
				Quest once2 = quest(level, "once", now, now + VillageNeeds.DAY, apple);
				Stories.post(level, hall, once2);
				helper.assertTrue(Stories.handIn(first, hall, once2.id) == 0 && first.getInventory().countItem(Items.APPLE) == 9,
					"the same player did a one-time quest twice");
				helper.assertTrue(Stories.handIn(second, hall, once2.id) == 1 && !open(level, hall, once2), "another player was refused a one-time quest");
				Quest again1 = quest(level, "again", now, now + VillageNeeds.DAY, apple);
				Quest again2 = quest(level, "again", now, now + VillageNeeds.DAY, apple);
				Stories.post(level, hall, again1);
				Stories.post(level, hall, again2);
				helper.assertTrue(Stories.handIn(first, hall, again1.id) == 1 && Stories.handIn(first, hall, again2.id) == 1,
					"a repeatable quest refused the same player");
			} finally {
				io.github.jcondedata.aliveworkplace.story.QuestFiles.load(real);
				Stories.Data.get(level).remove(hall);
			}
			helper.succeed();
		});
	}

	/** kill: a village quest counts only kills near its hall; an "anywhere" one counts a kill far from any village. */
	//$ gametest_ticks_batch AREA '60' '"qaEngineKillWhere"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaEngineKillWhere")
	public void aVillageKillCountsOnlyNearItsHall(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		hall(helper);
		VillageHalls.RADIUS = 6; // the far corner is then outside every village (restored by hall()'s after)
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		helper.runAfterDelay(3, () -> {
			long now = level.getGameTime();
			Quest village = quest(level, "kill_here", now, now + VillageNeeds.DAY, new Objectives.Kill("minecraft:zombie", 1, false));
			Quest anywhere = quest(level, "kill_anywhere", now, now + VillageNeeds.DAY, new Objectives.Kill("minecraft:zombie", 1, true));
			Stories.post(level, hall, village);
			Stories.post(level, hall, anywhere);
			helper.assertTrue(VillageHalls.nearest(level, helper.absolutePos(new BlockPos(20, 2, 20))).isEmpty(), "the far corner is in a village");
			var far = helper.spawn(EntityType.ZOMBIE, new BlockPos(20, 2, 20));
			far.hurt(level.damageSources().playerAttack(player), 1000f);
			helper.assertTrue(village.progress[0] == 0, "a far kill counted for the village quest");
			helper.assertFalse(open(level, hall, anywhere), "a far kill didn't count anywhere: " + anywhere);
			var near = helper.spawn(EntityType.ZOMBIE, new BlockPos(11, 2, 9));
			near.hurt(level.damageSources().playerAttack(player), 1000f);
			helper.assertFalse(open(level, hall, village), "a kill by the hall didn't count: " + village);
			Stories.Data.get(level).remove(hall);
			helper.succeed();
		});
	}
}
