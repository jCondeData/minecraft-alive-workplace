package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.QuestJournal;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.Founder;
import io.github.jcondedata.aliveworkplace.legend.LeadColoniesPower;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.TradeReachPower;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Places;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.QuestTracker;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.trade.Economy;
import io.github.jcondedata.aliveworkplace.trade.Market;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.GameType;

/**
 * QA (2026-10-31) for ROADMAP 33.23 (Legends of the realm) and 31.3 (the quest journal, tracking and quest maps), from
 * the spec: the Founder's and Merchant Prince's powers only for their own village, the rank boundary, caravan range from
 * both sides, the Legend dying and the Legends switch off; one tracked quest per player, the bar following a quest of
 * several objectives, a tracked quest that runs out of time, and the journal's quest lines (who asked, days left).
 */
public class QaRealmLegendsJournal1031GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final BlockPos HALL_A = new BlockPos(3, 2, 3);
	private static final BlockPos HALL_B = new BlockPos(3, 2, 25);
	private static final ResourceLocation PRINCE = AliveWorkplace.id("merchant_prince");
	private static final ResourceLocation TIMBER = BoardTradeGameTests.TIMBER;

	// ---- 33.23 helpers (as RealmLegendsGameTests stages them) ----

	private static void staged(GameTestHelper helper, ResourceLocation id, Consumer<Legend> body) {
		ServerLevel level = helper.getLevel();
		boolean moods = Moods.ENABLED;
		boolean needs = LegendNeeds.ENABLED;
		boolean legends = Legends.ENABLED;
		Leftovers.after(helper, () -> {
			Legends.ENABLED = legends;
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(32), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		});
		try {
			Moods.ENABLED = false;
			LegendNeeds.ENABLED = false;
			Legends.reload(level.getServer().getResourceManager());
			Legend legend = Legends.get(id).orElseThrow(() -> new AssertionError(id + " didn't load"));
			Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
			map.put(id, legend);
			Legends.setForTest(map);
			LegendPowers.forget();
			body.accept(legend);
		} finally {
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
			Legends.ENABLED = legends;
		}
	}

	private static Villager legend(GameTestHelper helper, Legend legend, BlockPos at, BlockPos hall) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		Legends.make(helper.getLevel(), v, legend, "test", Optional.of(hall));
		LegendPowers.forget();
		return v;
	}

	private static void twoHalls(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		int every = VillageNeeds.CHECK_EVERY;
		VillageHalls.RADIUS = 8;
		VillageNeeds.CHECK_EVERY = 1_000_000;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageNeeds.CHECK_EVERY = every;
		});
		helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
		helper.setBlock(HALL_B, ModBlocks.VILLAGE_HALL);
	}

	/**
	 * The Founder's powers are for "a village he lives in": with him living in Ashford, Thornholm (a Town 22 blocks
	 * away) still needs City rank; Ashford gets Town. Below Town (a Village) even his village is refused for its rank.
	 * Killed (not forgotten by the record), his powers go.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaFounderOwnVillage1031"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaFounderOwnVillage1031")
	public void theFounderHelpsOnlyHisOwnVillageAndNotBelowTown(GameTestHelper helper) {
		twoHalls(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(HALL_A);
		BlockPos b = helper.absolutePos(HALL_B);
		boolean enabled = Colonies.ENABLED;
		VillageRanks.Rank needed = Colonies.RANK;
		Colonies.ENABLED = true;
		Colonies.RANK = VillageRanks.Rank.CITY;
		RealmData.get(level.getServer()).clearColonies();
		Leftovers.after(helper, () -> {
			Colonies.ENABLED = enabled;
			Colonies.RANK = needed;
			RealmData.get(level.getServer()).clearColonies();
		});
		((VillageHallBlockEntity) helper.getBlockEntity(HALL_A)).setRank(VillageRanks.Rank.TOWN);
		VillageHallBlockEntity ashford = helper.getBlockEntity(HALL_B);
		ashford.setRank(VillageRanks.Rank.TOWN);
		for (int i = 0; i < 6; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(4 + i, 2, 23));
			v.setNoAi(true);
			v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
		}
		staged(helper, Founder.ID, founder -> {
			Villager him = legend(helper, founder, new BlockPos(3, 2, 22), b);
			helper.assertTrue(LeadColoniesPower.of(level, b).isPresent(), "no power in his own village");
			helper.assertTrue(LeadColoniesPower.of(level, a).isEmpty() && Colonies.rankNeeded(level, a) == VillageRanks.Rank.CITY
				&& Colonies.cooldownDays(level, a) == 7, "the Founder's power reached the other village: " + Colonies.rankNeeded(level, a));
			helper.assertTrue(Colonies.rankNeeded(level, b) == VillageRanks.Rank.TOWN, "his village: " + Colonies.rankNeeded(level, b));
			helper.assertTrue(Colonies.limit(level, b) == null, "a Town with the Founder may found: " + Colonies.limit(level, b));
			ashford.setRank(VillageRanks.Rank.VILLAGE);
			helper.assertTrue(Colonies.limit(level, b) == Colonies.Refusal.RANK, "a Village with the Founder: " + Colonies.limit(level, b));
			ashford.setRank(VillageRanks.Rank.TOWN);

			// The Legends switch off: no powers.
			Legends.ENABLED = false;
			LegendPowers.forget();
			helper.assertTrue(LeadColoniesPower.of(level, b).isEmpty() && Colonies.rankNeeded(level, b) == VillageRanks.Rank.CITY,
				"Legends off, the Founder still works: " + Colonies.rankNeeded(level, b));
			Legends.ENABLED = true;
			LegendPowers.forget();

			// Killed: the powers go with him.
			him.kill();
			LegendPowers.forget();
			helper.assertTrue(LeadColoniesPower.of(level, b).isEmpty() && Colonies.rankNeeded(level, b) == VillageRanks.Rank.CITY,
				"a dead Founder still works: " + Colonies.rankNeeded(level, b));
		});
		helper.succeed();
	}

	private static void market(ServerLevel level, Caravans.Data data, BlockPos hall, List<ResourceLocation> knownFor, int cents) {
		Map<ResourceLocation, Market.Price> prices = new LinkedHashMap<>();
		prices.put(TIMBER, new Market.Price(cents, cents, 0));
		data.setMarket(hall, new Market(knownFor, List.of(), prices, Chronicle.day(level), List.of()));
	}

	/**
	 * "Every village within caravan range": with the range at 400, a village exactly 400 away is on the Prince's board
	 * and one 401 away is not. A Prince living in another village changes nothing for Thornholm (spread 110%, partners
	 * only). Killed, his board and spread go back.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaPrinceRange1031"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPrinceRange1031")
	public void thePrincesBoardStopsAtCaravanRangeAndOnlyInHisVillage(GameTestHelper helper) {
		twoHalls(helper);
		TradeGoodsDataGameTests.goodsFrom(helper, "aliveworkplace_test");
		ServerLevel level = helper.getLevel();
		boolean economy = Economy.ENABLED;
		int range = Caravans.RANGE;
		Economy.ENABLED = true;
		Caravans.RANGE = 400;
		BlockPos a = helper.absolutePos(HALL_A);
		BlockPos other = helper.absolutePos(HALL_B);
		BlockPos partner = a.offset(0, 0, -300);
		BlockPos edge = a.offset(400, 0, 0);
		BlockPos past = a.offset(-401, 0, 0);
		Caravans.Data data = Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			Economy.ENABLED = economy;
			Caravans.RANGE = range;
			for (BlockPos p : List.of(a, other, partner, edge, past)) {
				data.remove(p);
				data.clearRoad(p);
			}
		});
		data.setWants(a, Component.literal("Thornholm"), List.of());
		data.setWants(other, Component.literal("Ashford"), List.of());
		data.setWants(partner, Component.literal("Brightwater"), List.of());
		data.setWants(edge, Component.literal("Dunmore"), List.of());
		data.setWants(past, Component.literal("Farhold"), List.of());
		market(level, data, a, List.of(TIMBER), 100);
		market(level, data, other, List.of(), 110);
		market(level, data, partner, List.of(), 130);
		market(level, data, edge, List.of(), 200);
		market(level, data, past, List.of(), 300);
		helper.assertTrue(data.toggleRoute(a, partner), "no route");
		staged(helper, PRINCE, prince -> {
			// He lives in Ashford, next door: Thornholm's board is as before.
			Villager him = legend(helper, prince, new BlockPos(3, 2, 22), other);
			helper.assertTrue(TradeReachPower.of(level, other).isPresent(), "no power in his own village");
			helper.assertTrue(TradeReachPower.of(level, a).isEmpty() && TradePage.sellPercent(level, a) == 110
				&& TradePage.boardVillages(level, a, data).equals(Set.of(partner)),
				"a Prince in another village changed Thornholm: " + TradePage.sellPercent(level, a) + " " + TradePage.boardVillages(level, a, data));
			him.discard();
			LegendRecord.get(level).forget(him.getUUID());

			// He lives in Thornholm: Dunmore (exactly at range) yes, Farhold (one past) no.
			Villager here = legend(helper, prince, new BlockPos(4, 2, 6), a);
			Set<BlockPos> board = TradePage.boardVillages(level, a, data);
			helper.assertTrue(board.contains(partner) && board.contains(edge), "the board at caravan range: " + board);
			helper.assertTrue(!board.contains(past), "the board reached past caravan range: " + board);
			helper.assertTrue(TradePage.sellPercent(level, a) == 105, "the spread with him: " + TradePage.sellPercent(level, a));
			helper.assertTrue(TradePage.selling(level, a, 200) == 210, "a bundle the board pays 2 emeralds for sells for " + TradePage.selling(level, a, 200));

			// Killed: as before.
			here.kill();
			LegendPowers.forget();
			helper.assertTrue(TradePage.sellPercent(level, a) == 110 && TradePage.boardVillages(level, a, data).equals(Set.of(partner)),
				"a dead Prince still works: " + TradePage.sellPercent(level, a));
		});
		helper.succeed();
	}

	// ---- 31.3 ----

	private static BlockPos questHall(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		BlockPos at = new BlockPos(9, 2, 9);
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = helper.getBlockEntity(at);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		return helper.absolutePos(at);
	}

	private static Quest quest(ServerLevel level, Optional<Component> name, List<Objectives.Objective> objectives, long lasts) {
		long now = level.getGameTime();
		return new Quest(UUID.randomUUID(), AliveWorkplace.id("daily/test_journal"), "hall", name, "Ana", now, now + lasts,
			objectives, new int[objectives.size()], List.of());
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> QuestTracker.untrack(player));
		return player;
	}

	/** One tracked quest per player: tracking a second one moves the bar to it, and the first is no longer tracked. */
	//$ gametest_ticks_batch BIG '60' '"qaOneTracked1031"'
	@GameTest(template = BIG, timeoutTicks = 60, batch = "qaOneTracked1031")
	public void trackingASecondQuestReplacesTheFirst(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = questHall(helper);
		Quest first = quest(level, Optional.of(Component.literal("Wolves at the gate")), List.of(new Objectives.Kill("minecraft:zombie", 3, false)), 3 * VillageNeeds.DAY);
		Quest second = quest(level, Optional.of(Component.literal("Long watch")), List.of(new Objectives.Wait(2)), 3 * VillageNeeds.DAY);
		Stories.post(level, hall, first);
		Stories.post(level, hall, second);
		ServerPlayer me = player(helper);
		QuestTracker.track(me, level, hall, first.id);
		ServerBossEvent bar = QuestTracker.bar(me);
		helper.assertTrue(bar != null && bar.getName().getString().startsWith("Wolves at the gate"), "first bar: " + (bar == null ? null : bar.getName().getString()));
		QuestTracker.track(me, level, hall, second.id);
		ServerBossEvent now = QuestTracker.bar(me);
		helper.assertTrue(QuestTracker.isTracked(me, second.id) && !QuestTracker.isTracked(me, first.id), "both tracked");
		helper.assertTrue(now != null && now.getName().getString().equals("Long watch (0/2)") && now.getPlayers().size() == 1,
			"the bar after tracking the second: " + (now == null ? null : now.getName().getString() + " " + now.getPlayers().size()));
		helper.assertTrue(bar == now || bar.getPlayers().isEmpty(), "two bars on screen");
		helper.succeed();
	}

	/**
	 * A named quest of two objectives (kill one zombie, then reach a spot): the bar names the quest and its current
	 * objective; standing on the spot before its turn counts nothing; after the kill the bar moves to the reach line at
	 * half full; reaching the spot ends the quest and the bar.
	 */
	//$ gametest_ticks_batch BIG '60' '"qaBarFollowsSteps1031"'
	@GameTest(template = BIG, timeoutTicks = 60, batch = "qaBarFollowsSteps1031")
	public void theBarFollowsTheCurrentObjectiveInOrder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = questHall(helper);
		BlockPos spot = helper.absolutePos(new BlockPos(3, 2, 3));
		Objectives.Kill kill = new Objectives.Kill("minecraft:zombie", 1, false);
		Objectives.Reach reach = new Objectives.Reach(Places.point(spot), 3);
		Quest quest = quest(level, Optional.of(Component.literal("Follow the tracks")), List.of(kill, reach), 3 * VillageNeeds.DAY);
		Stories.post(level, hall, quest);
		ServerPlayer me = player(helper);
		QuestTracker.track(me, level, hall, quest.id);
		ServerBossEvent bar = QuestTracker.bar(me);
		String expected = "Follow the tracks: " + kill.line().getString() + " (0/1)";
		helper.assertTrue(bar != null && bar.getName().getString().equals(expected), "fresh bar: " + (bar == null ? null : bar.getName().getString()) + " (expected " + expected + ")");

		me.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		QuestTracker.checkReach(me);
		helper.assertTrue(quest.progress[1] == 0 && quest.current() == 0, "reach counted before its turn: " + java.util.Arrays.toString(quest.progress));

		var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 6));
		zombie.hurt(level.damageSources().playerAttack(me), 1000f);
		helper.assertTrue(quest.current() == 1, "the kill didn't move it on: " + java.util.Arrays.toString(quest.progress));
		bar = QuestTracker.bar(me);
		String second = "Follow the tracks: " + reach.line().getString() + " (0/1)";
		helper.assertTrue(bar != null && bar.getName().getString().equals(second) && Math.abs(bar.getProgress() - 0.5f) < 0.01f,
			"after the kill: " + (bar == null ? null : bar.getName().getString() + " " + bar.getProgress()));

		QuestTracker.checkReach(me);
		helper.assertTrue(quest.done() && QuestTracker.bar(me) == null && !QuestTracker.isTracked(me, quest.id), "the bar outlived the finished quest");
		helper.succeed();
	}

	/** A tracked quest whose days run out comes down in the hall's round, and its bar and tracking go with it. */
	//$ gametest_ticks_batch BIG '100' '"qaTrackedExpires1031"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaTrackedExpires1031")
	public void aTrackedQuestThatRunsOutOfTimeLosesItsBar(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = questHall(helper);
		Quest quest = quest(level, Optional.empty(), List.of(new Objectives.Kill("minecraft:zombie", 4, false)), 5);
		Stories.post(level, hall, quest);
		ServerPlayer me = player(helper);
		QuestTracker.track(me, level, hall, quest.id);
		helper.assertTrue(QuestTracker.bar(me) != null, "no bar");
		helper.runAfterDelay(10, () -> {
			Stories.round(level, hall, (VillageHallBlockEntity) level.getBlockEntity(hall));
			helper.assertTrue(Stories.open(level, hall).stream().noneMatch(q -> q.id.equals(quest.id)), "the overdue quest stayed up");
		});
		helper.runAfterDelay(12, () -> helper.succeedWhen(() -> helper.assertTrue(QuestTracker.bar(me) == null && !QuestTracker.isTracked(me, quest.id),
			"the bar of an expired quest is still up: " + (QuestTracker.bar(me) == null ? null : QuestTracker.bar(me).getName().getString()))));
	}

	/**
	 * The journal's quest line: who asked, one line per objective with its progress, the days left in American
	 * English, singular for the last day ("Up for 1 more day") and plural otherwise ("Up for 3 more days").
	 */
	//$ gametest_ticks_batch BIG '60' '"qaJournalLines1031"'
	@GameTest(template = BIG, timeoutTicks = 60, batch = "qaJournalLines1031")
	public void theJournalShowsWhoAskedEachObjectiveAndTheDaysLeft(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		questHall(helper);
		ServerPlayer me = player(helper);
		Objectives.Kill kill = new Objectives.Kill("minecraft:zombie", 2, false);
		Objectives.Wait wait = new Objectives.Wait(2);
		Quest three = quest(level, Optional.empty(), List.of(kill, wait), 3 * VillageNeeds.DAY);
		List<String> lore = BoardTradeGameTests.lore(QuestJournal.questIcon(level, three, me));
		helper.assertTrue(lore.contains("Asked by Ana"), "who asked: " + lore);
		helper.assertTrue(lore.contains(kill.line().getString() + " (0/2)") && lore.contains(wait.line().getString() + " (0/2)"), "the objective lines: " + lore);
		helper.assertTrue(lore.contains("Up for 3 more days"), "three days left: " + lore);
		// The wiki's examples, plural for more than one (B568 fixed): "Defeat 2 Zombies", "Bring 2 Apples".
		helper.assertTrue(kill.line().getString().equals("Defeat 2 Zombies"), "the kill line: " + kill.line().getString());
		String bring = new Objectives.Bring("minecraft:apple", 2, "hall", Optional.empty()).line().getString();
		helper.assertTrue(bring.equals("Bring 2 Apples"), "the bring line: " + bring);
		helper.assertTrue(wait.line().getString().equals("Wait 2 days") && new Objectives.Wait(1).line().getString().equals("Wait 1 day"),
			"the wait lines: " + wait.line().getString() + " / " + new Objectives.Wait(1).line().getString());
		Quest last = quest(level, Optional.empty(), List.of(kill), 100);
		lore = BoardTradeGameTests.lore(QuestJournal.questIcon(level, last, me));
		helper.assertTrue(lore.contains("Up for 1 more day"), "the last day: " + lore);
		helper.assertTrue(lore.stream().anyMatch(l -> l.toLowerCase().contains("track")), "no Track click on a kill quest: " + lore);
		helper.succeed();
	}
	/**
	 * A tracked bring quest handed in through the hall's own screen (the player's entry point): the bar follows the
	 * hand-in at once ("(2/5)", 40% full), and the journal still offers the hand-in, not Track, as the plain click.
	 */
	//$ gametest_ticks_batch BIG '60' '"qaHandInBar1031"'
	@GameTest(template = BIG, timeoutTicks = 60, batch = "qaHandInBar1031")
	public void aHandInThroughTheHallMovesTheTrackedBar(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = questHall(helper);
		helper.setBlock(new BlockPos(11, 2, 13), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(11, 2, 14), net.minecraft.world.level.block.Blocks.CHEST);
		Quest quest = quest(level, Optional.empty(), List.of(new Objectives.Bring("minecraft:apple", 5, "hall", Optional.empty())), 3 * VillageNeeds.DAY);
		Stories.post(level, hall, quest);
		ServerPlayer me = player(helper);
		QuestTracker.track(me, level, hall, quest.id);
		me.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE, 2));
		me.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		helper.useBlock(new BlockPos(9, 2, 9), me);
		helper.assertTrue(me.containerMenu instanceof io.github.jcondedata.aliveworkplace.work.ChoiceMenu, "the hall didn't open: " + me.containerMenu);
		var menu = (io.github.jcondedata.aliveworkplace.work.ChoiceMenu) me.containerMenu;
		menu.clicked(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.QUESTS, 0, net.minecraft.world.inventory.ClickType.PICKUP, me);
		int slot = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.QUEST_SLOTS[0];
		List<String> lore = BoardTradeGameTests.lore(menu.icon(slot));
		helper.assertTrue(lore.contains("Click: hand in 2"), "the hint: " + lore);
		menu.clicked(slot, 0, net.minecraft.world.inventory.ClickType.PICKUP, me);
		helper.assertTrue(quest.progress[0] == 2 && QuestTracker.isTracked(me, quest.id), "after the hand-in: " + java.util.Arrays.toString(quest.progress));
		ServerBossEvent bar = QuestTracker.bar(me);
		helper.assertTrue(bar != null && bar.getName().getString().endsWith("(2/5)") && Math.abs(bar.getProgress() - 0.4f) < 0.01f,
			"the bar after handing in 2 of 5: " + (bar == null ? null : bar.getName().getString() + " " + bar.getProgress()));
		me.closeContainer();
		helper.succeed();
	}

	/**
	 * /workplace quests far from every village: the quest you track elsewhere is still listed, with [Untrack]; with
	 * nothing tracked it says there are none.
	 */
	//$ gametest_ticks_batch BIG '60' '"qaTrackedFarAway1031"'
	@GameTest(template = BIG, timeoutTicks = 60, batch = "qaTrackedFarAway1031")
	public void theQuestsCommandListsTheTrackedQuestFromAfar(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = questHall(helper);
		Quest quest = quest(level, Optional.of(Component.literal("Far errand")), List.of(new Objectives.Kill("minecraft:zombie", 2, false)), 3 * VillageNeeds.DAY);
		Stories.post(level, hall, quest);
		ServerPlayer me = player(helper);
		QuestTracker.track(me, level, hall, quest.id);
		me.teleportTo(hall.getX() + 600.5, hall.getY(), hall.getZ() + 0.5);
		List<Component> lines = QuestJournal.chatList(me);
		helper.assertTrue(lines.size() == 2 && lines.get(1).getString().startsWith("• Far errand") && lines.get(1).getString().endsWith("[Untrack]"),
			"the list from afar: " + lines.stream().map(Component::getString).toList());
		QuestTracker.untrack(me);
		lines = QuestJournal.chatList(me);
		helper.assertTrue(lines.size() == 1 && lines.get(0).getString().equals("No open quests in the village nearest you."),
			"untracked, from afar: " + lines.stream().map(Component::getString).toList());
		helper.succeed();
	}
	/**
	 * Four settlers is what the Founder's village sends when it can spare them: with only three jobless villagers it
	 * still founds, sending the three (never him), not refusing for want of a fourth.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaFounderThreeSpare1031"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaFounderThreeSpare1031")
	public void theFoundersVillageWithThreeToSpareSendsThree(GameTestHelper helper) {
		twoHalls(helper);
		ServerLevel level = helper.getLevel();
		BlockPos b = helper.absolutePos(HALL_B);
		boolean enabled = Colonies.ENABLED;
		VillageRanks.Rank needed = Colonies.RANK;
		Colonies.ENABLED = true;
		Colonies.RANK = VillageRanks.Rank.CITY;
		RealmData.get(level.getServer()).clearColonies();
		Leftovers.after(helper, () -> {
			Colonies.ENABLED = enabled;
			Colonies.RANK = needed;
			RealmData.get(level.getServer()).clearColonies();
		});
		((VillageHallBlockEntity) helper.getBlockEntity(HALL_B)).setRank(VillageRanks.Rank.TOWN);
		for (int i = 0; i < 3; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(4 + i, 2, 23));
			v.setNoAi(true);
			v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
		}
		staged(helper, Founder.ID, founder -> {
			Villager him = legend(helper, founder, new BlockPos(3, 2, 22), b);
			helper.assertTrue(Colonies.limit(level, b) == null, "refused: " + Colonies.limit(level, b));
			var go = io.github.jcondedata.aliveworkplace.colony.Settlers.volunteers(level, b);
			helper.assertTrue(go != null && go.all().size() == 3 && !go.all().contains(him), "with three to spare: " + (go == null ? null : go.all().size()));
		});
		helper.succeed();
	}
}
