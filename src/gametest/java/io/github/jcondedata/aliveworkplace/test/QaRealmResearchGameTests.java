package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.RealmResearch;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchScreen;
import io.github.jcondedata.aliveworkplace.research.ScholarWork;
import io.github.jcondedata.aliveworkplace.research.TreeEffects;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (ROADMAP 33.15, 33.16), written from the spec: prerequisites refuse a topic on the capital's Realm row; one cent
 * short in the realm treasury refuses and the capital's own treasury is never touched; a member uses the higher of its
 * own and the capital's level, its own levels and rank unchanged, and loses the shared level on leaving the realm; an
 * idle member scholar helps the realm topic at half pace; a topic mid-way survives a save; the topics' effects reach
 * only realm villages; Ferry Charter's one-emerald floor; Granaries' 16-meal boundary and the fullest member as donor.
 * Each test is alone in its batch.
 */
public class QaRealmResearchGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final String TEST_TOPIC = "aliveworkplace_test:test_census";

	private static RealmData.Realm realmOf(RealmGameTests.Land land) {
		return land.data().realmOf(land.at(0));
	}

	private static Research.State realmState(RealmGameTests.Land land) {
		return RealmResearch.state(land.level().getServer(), realmOf(land));
	}

	private static int slot(RealmResearch.Topic topic) {
		return ResearchScreen.TOPIC_SLOTS[new ArrayList<>(RealmResearch.all()).indexOf(topic)];
	}

	private static RealmResearch.Topic topic(String key) {
		return RealmResearch.get(key).orElseThrow(() -> new AssertionError("no realm topic " + key));
	}

	/** Prerequisites: each topic with "needs" can't be chosen before them, from the Realm row or directly; once known, it can. */
	//$ gametest_ticks_batch AREA '100' '"qaRealmPrereq"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmPrereq")
	public void qaPrerequisitesRefuseEarlyTopics(GameTestHelper helper) {
		RealmGameTests.Land land = RealmResearchGameTests.realm(helper);
		ServerLevel level = land.level();
		// the player's way: the Realm row, Common Coin clicked with no Royal Roads known
		ResearchScreen.View view = new ResearchScreen.View();
		ChoiceMenu menu = ResearchScreen.forTest(land.owner(), land.hall(0), view);
		menu.press(ResearchScreen.REALM_TAB, land.owner());
		ItemStack coin = menu.icon(slot(topic("common_coin")));
		helper.assertTrue(RealmTopicsGameTests.lore(coin).contains("First: Royal Roads " + BuilderLevels.levelName(1).getString()),
			"Common Coin doesn't say what it needs: " + RealmTopicsGameTests.lore(coin));
		menu.press(slot(topic("common_coin")), land.owner());
		helper.assertTrue(realmState(land).current().isEmpty(), "Common Coin was chosen before Royal Roads I: " + realmState(land));
		// each topic against its spec's prerequisites, one level short
		Object[][] cases = {
			{"common_coin", Map.of()},
			{"royal_post", Map.of()},
			{"ferry_charter", Map.of("royal_roads", 1)},
			{"charters", Map.of("royal_roads", 1)},
			{"concord", Map.of()},
			{"granaries", Map.of("royal_roads", 2, "levies", 1)},
			{"granaries", Map.of("royal_roads", 1, "levies", 1, "concord", 1)},
		};
		for (Object[] c : cases) {
			@SuppressWarnings("unchecked") Map<String, Integer> known = (Map<String, Integer>) c[1];
			RealmTopicsGameTests.know(land, known);
			Realms.Result r = RealmResearch.choose(level, land.hall(0), land.owner(), topic((String) c[0]));
			helper.assertTrue(!r.ok() && realmState(land).current().isEmpty(), c[0] + " was chosen knowing only " + known + ": " + r.message().getString());
			helper.assertTrue(r.message().getString().equals(topic((String) c[0]).name().getString() + " can't be researched yet"), "the refusal: " + r.message().getString());
		}
		// with them known, each can
		Object[][] open = {
			{"royal_roads", Map.of()},
			{"levies", Map.of()},
			{"common_coin", Map.of("royal_roads", 1)},
			{"royal_post", Map.of("royal_roads", 1)},
			{"ferry_charter", Map.of("royal_roads", 1, "royal_post", 1)},
			{"charters", Map.of("royal_roads", 2)},
			{"concord", Map.of("levies", 1)},
			{"granaries", Map.of("royal_roads", 2, "levies", 1, "concord", 1)},
		};
		for (Object[] c : open) {
			@SuppressWarnings("unchecked") Map<String, Integer> known = (Map<String, Integer>) c[1];
			RealmTopicsGameTests.know(land, known);
			Realms.Result r = RealmResearch.choose(level, land.hall(0), land.owner(), topic((String) c[0]));
			helper.assertTrue(r.ok() && RealmResearch.current(realmState(land)) == topic((String) c[0]), c[0] + " refused knowing " + known + ": " + r.message().getString());
		}
		// a member can't choose the realm's research
		Realms.Result member = RealmResearch.choose(level, land.hall(1), land.owner(), topic("royal_roads"));
		helper.assertTrue(!member.ok() && member.message().getString().equals("Only a realm's capital chooses the realm's research."), "a member chose: " + member.message().getString());
		helper.succeed();
	}

	/** One cent short of a level's emeralds in the realm treasury: not paid, nothing taken; the capital's own treasury is never used. */
	//$ gametest_ticks_batch AREA '100' '"qaRealmCent"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmCent")
	public void qaOneCentShortRefuses(GameTestHelper helper) {
		RealmGameTests.Land land = RealmResearchGameTests.realm(helper);
		RealmResearch.Topic census = RealmResearch.get(TEST_TOPIC).orElse(null);
		helper.assertTrue(census != null && census.emeralds() == 3, "the test topic: " + census);
		helper.assertTrue(RealmResearch.choose(land.level(), land.hall(0), land.owner(), census).ok(), "not chosen");
		land.entity(0).setTreasury(5000);
		land.entity(1).setTreasury(4000);
		land.data().putRealm(realmOf(land).withTreasury(299, List.of()));
		RealmResearchGameTests.Desk capital = RealmResearchGameTests.desk(helper, land, 0);
		capital.chest().setItem(0, new ItemStack(Items.PAPER, 2));
		capital.chest().setItem(1, new ItemStack(Items.BOOK, 1));
		new ScholarWork().work(land.level(), capital.scholar(), capital.desk(), true);
		new ScholarWork().work(land.level(), capital.scholar(), capital.desk(), true);
		helper.assertTrue(!realmState(land).paid(), "paid with 2.99 of 3 emeralds: " + realmState(land));
		helper.assertTrue(realmOf(land).treasury() == 299 && capital.chest().countItem(Items.PAPER) == 2 && capital.chest().countItem(Items.BOOK) == 1,
			"something was taken: treasury " + realmOf(land).treasury() + ", paper " + capital.chest().countItem(Items.PAPER));
		helper.assertTrue(land.entity(0).treasury() == 5000 && land.entity(1).treasury() == 4000,
			"a village's own treasury paid: " + land.entity(0).treasury() + ", " + land.entity(1).treasury());
		// exactly the price
		land.data().putRealm(realmOf(land).withTreasury(300, List.of()));
		new ScholarWork().work(land.level(), capital.scholar(), capital.desk(), true);
		helper.assertTrue(realmState(land).paid() && realmOf(land).treasury() == 0, "not paid with exactly 3 emeralds: " + realmState(land) + " " + realmOf(land).treasury());
		helper.assertTrue(land.entity(0).treasury() == 5000 && land.entity(1).treasury() == 4000, "a village's own treasury paid");
		helper.succeed();
	}

	/** Shared levels: the higher of own and capital's, both ways; the member's own levels and rank unchanged; gone when it leaves. */
	//$ gametest_ticks_batch AREA '200' '"qaRealmShared"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRealmShared")
	public void qaSharedLevelIsTheHigherAndLeavesWithTheRealm(GameTestHelper helper) {
		RealmGameTests.Land land = RealmResearchGameTests.realm(helper);
		ServerLevel level = land.level();
		land.entity(1).setResearch(new Research.State(Map.of("drill", 2, "hearth", 1), Optional.empty(), 0, false));
		land.entity(0).setResearch(new Research.State(Map.of("drill", 1), Optional.empty(), 0, false));
		VillageRanks.Score before = VillageRanks.score(level, land.hall(1), 5);
		helper.assertTrue(Research.at(level, land.hall(1)).level(Research.Topic.DRILL) == 2, "the member's own higher Drill II lost to the capital's I: "
			+ Research.at(level, land.hall(1)));
		helper.assertTrue(Research.at(level, land.hall(1)).level(Research.Topic.HEARTH) == 1, "the member's own Hearth lost");
		land.entity(0).setResearch(new Research.State(Map.of("drill", 3), Optional.empty(), 0, false));
		helper.assertTrue(Research.at(level, land.hall(1)).level(Research.Topic.DRILL) == 3 && Research.at(level, land.hall(1)).level(Research.Topic.HEARTH) == 1,
			"the capital's Drill III not shared: " + Research.at(level, land.hall(1)));
		helper.assertTrue(Research.at(level, land.hall(0)).level(Research.Topic.HEARTH) == 0, "the member's Hearth went up to the capital");
		helper.assertTrue(land.entity(1).research().level(Research.Topic.DRILL) == 2, "the member's own level changed: " + land.entity(1).research());
		VillageRanks.Score after = VillageRanks.score(level, land.hall(1), 5);
		helper.assertTrue(after.research() == before.research() && VillageRanks.rank(after) == VillageRanks.rank(before), "the shared level counted for rank: " + before + " -> " + after);
		// a realm topic's effect too
		RealmTopicsGameTests.know(land, Map.of(TEST_TOPIC, 1));
		helper.assertTrue(CivicEffects.of(level, land.hall(1)).marketTraders() == 2, "the member hasn't the realm topic's effect");
		// it leaves the realm
		helper.assertTrue(Realms.leave(level, land.hall(1), land.owner()).ok(), "the member didn't leave");
		helper.assertTrue(Research.at(level, land.hall(1)).level(Research.Topic.DRILL) == 2, "the capital's Drill III kept after leaving: " + Research.at(level, land.hall(1)));
		ChoiceMenu menu = ResearchScreen.forTest(land.owner(), land.hall(1));
		helper.assertTrue(RealmGameTests.lore(menu.icon(ResearchScreen.TOPIC_SLOTS[Research.Topic.DRILL.ordinal()])).stream().noneMatch(l -> l.startsWith("From the capital")),
			"the screen still says 'from the capital' after leaving");
		helper.assertTrue(RealmResearch.level(level, land.hall(1), TEST_TOPIC) == 0, "the realm topic's level kept after leaving");
		// the realm topics' effects are summed again within 5 seconds of a member leaving (realms.md, "How it works")
		helper.runAfterDelay(105, () -> {
			helper.assertTrue(CivicEffects.of(level, land.hall(1)).marketTraders() == 0,
				"the realm topic's effect kept 5 seconds after leaving: " + CivicEffects.of(level, land.hall(1)).marketTraders());
			helper.succeed();
		});
	}

	/** A member scholar with nothing chosen, the capital with no topic of its own: half their points go to the realm topic; with a topic at home, none. */
	//$ gametest_ticks_batch AREA '100' '"qaRealmHelpTopic"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmHelpTopic")
	public void qaIdleMemberHelpsTheRealmTopicAtHalfPace(GameTestHelper helper) {
		RealmGameTests.Land land = RealmResearchGameTests.realm(helper);
		RealmData.Realm realm = realmOf(land);
		land.data().putStudy(land.data().study(realm.id()).withResearch(new Research.State(Map.of(), Optional.of("royal_roads"), 0, true)));
		RealmResearch.forget();
		RealmResearchGameTests.Desk member = RealmResearchGameTests.desk(helper, land, 1);
		int full = ScholarWork.progress(member.scholar());
		new ScholarWork().work(land.level(), member.scholar(), member.desk(), true);
		int added = realmState(land).progress();
		helper.assertTrue(added == Math.max(1, full / 2) && added < full, "not half pace on the realm topic: " + added + " of " + full);
		helper.assertTrue(land.entity(1).research().currentTopic() == null && land.entity(1).research().progress() == 0, "the member's own research changed");
		// with a topic of their own, they work at home
		land.entity(1).setResearch(Research.State.EMPTY.choose(Research.Topic.HEARTH));
		new ScholarWork().work(land.level(), member.scholar(), member.desk(), true);
		helper.assertTrue(realmState(land).progress() == added, "helped the realm with work of their own: " + realmState(land));
		helper.succeed();
	}

	/** A realm topic chosen, paid and part-way survives a save and load of the realm file. */
	//$ gametest_ticks_batch AREA '100' '"qaRealmSaveMid"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmSaveMid")
	public void qaRealmTopicMidWaySurvivesSave(GameTestHelper helper) {
		RealmGameTests.Land land = RealmResearchGameTests.realm(helper);
		RealmData.Realm realm = realmOf(land);
		land.data().putStudy(land.data().study(realm.id()).withResearch(new Research.State(Map.of("royal_roads", 1), Optional.of("royal_roads"), 1234, true)));
		CompoundTag tag = land.data().save(new CompoundTag(), land.level().registryAccess());
		RealmData back = RealmData.load(tag, land.level().registryAccess());
		Research.State s = back.study(realm.id()).research();
		helper.assertTrue(s.levels().equals(Map.of("royal_roads", 1)) && s.current().equals(Optional.of("royal_roads")) && s.progress() == 1234 && s.paid(),
			"lost on save: " + s);
		helper.succeed();
	}

	/** Every topic's effect reaches realm villages only: a village of the same owner outside the realm gets none; Ferry Charter's floor. */
	//$ gametest_ticks_batch AREA '100' '"qaRealmOutside"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmOutside")
	public void qaTopicsReachOnlyRealmVillages(GameTestHelper helper) {
		RealmGameTests.Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET, VillageRanks.Rank.HAMLET);
		Research.forget();
		RealmResearch.forget();
		Leftovers.after(helper, () -> {
			Research.forget();
			RealmResearch.forget();
		});
		RealmGameTests.found(helper, land);
		helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(1), land.owner()).ok(), "the hamlet didn't join");
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		Caravans.Data data = Caravans.Data.get(level);
		long tripOut = Caravans.trip(level, land.hall(0), land.hall(2), data);
		long tripIn = Caravans.trip(level, land.hall(0), land.hall(1), data);
		float wellOut = TreeEffects.wellbeing(land.entity(2));
		int costOut = Colonies.cost(level, land.hall(2));
		GlobalPos postIn = GlobalPos.of(level.dimension(), land.hall(1).offset(2, 0, 0));
		GlobalPos postCap = GlobalPos.of(level.dimension(), land.hall(0).offset(2, 0, 0));
		GlobalPos postOut = GlobalPos.of(level.dimension(), land.hall(2).offset(2, 0, 0));
		RealmTopicsGameTests.know(land, Map.of("royal_roads", 2, "common_coin", 2, "royal_post", 1, "ferry_charter", 1, "levies", 2, "concord", 2, "charters", 2));
		// inside the realm
		helper.assertTrue(Caravans.trip(level, land.hall(0), land.hall(1), data) == tripIn / 2, "Royal Roads II inside the realm");
		helper.assertTrue(TradePage.sellPercent(level, land.hall(1)) == 104 && RealmResearch.reliefRiders(level, land.hall(1)) == 2, "Common Coin II / Levies II inside");
		helper.assertTrue(RealmResearch.noonPost(server, postCap, postIn), "Royal Post inside the realm");
		// outside it
		helper.assertTrue(Caravans.trip(level, land.hall(0), land.hall(2), data) == tripOut, "Royal Roads sped a caravan to a village outside the realm");
		helper.assertTrue(TradePage.sellPercent(level, land.hall(2)) == 110, "Common Coin outside the realm: " + TradePage.sellPercent(level, land.hall(2)));
		helper.assertTrue(RealmResearch.reliefRiders(level, land.hall(2)) == 0, "Levies outside the realm");
		helper.assertTrue(!RealmResearch.noonPost(server, postIn, postOut) && !RealmResearch.noonPost(server, postOut, postCap), "the noon post to a village outside the realm");
		helper.assertTrue(Math.abs(TreeEffects.wellbeing(land.entity(2)) - wellOut) < 1e-4, "Concord outside the realm");
		helper.assertTrue(Colonies.cost(level, land.hall(2)) == costOut, "Charters outside the realm: " + costOut + " -> " + Colonies.cost(level, land.hall(2)));
		helper.assertTrue(RealmResearch.fare(server, postIn, postOut, land.owner().getUUID(), 6) == 6, "the Ferry Charter to a post outside the realm");
		// Ferry Charter: half, at least an emerald
		java.util.UUID owner = land.owner().getUUID();
		helper.assertTrue(RealmResearch.fare(server, postCap, postIn, owner, 10) == 5, "half of 10");
		helper.assertTrue(RealmResearch.fare(server, postCap, postIn, owner, 2) == 1, "half of 2");
		helper.assertTrue(RealmResearch.fare(server, postCap, postIn, owner, 1) == 1, "the floor of one emerald");
		helper.succeed();
	}

	/**
	 * Granaries at the boundary: a member at exactly 16 meals gets nothing, at 15 it gets meals (up to 32) from the member
	 * with the fullest store, not from a fuller-than-16 one that holds less.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRealmGranary"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRealmGranary")
	public void qaGranariesBoundaryAndFullestDonor(GameTestHelper helper) {
		RealmGameTests.Land land = RealmResearchGameTests.realm(helper);
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		BlockPos poorHall = land.hall(0).offset(80, 0, 0);
		BlockPos fullHall = land.hall(0).offset(0, 0, 80);
		List<BlockPos> placed = List.of(poorHall, fullHall, land.hall(0).offset(0, 0, -3), land.hall(0).offset(1, 0, -3), poorHall.offset(0, 0, -3),
			poorHall.offset(1, 0, -3), fullHall.offset(0, 0, -3), fullHall.offset(1, 0, -3));
		Caravans.Data data = Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			data.clearRoad(poorHall);
			data.clearRoad(fullHall);
			data.clearRoad(land.hall(0));
			placed.forEach(p -> level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState()));
		});
		for (BlockPos h : List.of(poorHall, fullHall)) {
			level.setBlockAndUpdate(h, ModBlocks.VILLAGE_HALL.defaultBlockState());
			VillageHallBlockEntity e = (VillageHallBlockEntity) level.getBlockEntity(h);
			e.setOwner(land.owner().getUUID(), "Owner");
			e.setRank(VillageRanks.Rank.HAMLET);
			Realms.round(level, h, e, 5);
			helper.assertTrue(Realms.add(level, land.hall(0), GlobalPos.of(level.dimension(), h), land.owner()).ok(), "a far village didn't join");
		}
		for (int i = 2; i < placed.size(); i += 2) {
			level.setBlockAndUpdate(placed.get(i), Blocks.SMOKER.defaultBlockState());
			level.setBlockAndUpdate(placed.get(i + 1), Blocks.CHEST.defaultBlockState());
		}
		Container capital = (Container) level.getBlockEntity(placed.get(3));
		Container poor = (Container) level.getBlockEntity(placed.get(5));
		Container full = (Container) level.getBlockEntity(placed.get(7));
		capital.setItem(0, new ItemStack(Items.BREAD, 30));
		full.setItem(0, new ItemStack(Items.BREAD, 60));
		poor.setItem(0, new ItemStack(Items.BREAD, 16));
		helper.assertTrue(RealmResearch.meals(level, poorHall) == 16 && RealmResearch.meals(level, fullHall) == 60 && RealmResearch.meals(level, land.hall(0)) == 30,
			"the stores: " + RealmResearch.meals(level, poorHall) + ", " + RealmResearch.meals(level, fullHall) + ", " + RealmResearch.meals(level, land.hall(0)));
		RealmTopicsGameTests.know(land, Map.of("royal_roads", 2, "levies", 1, "concord", 1, "granaries", 1));
		helper.assertTrue(RealmResearch.granaries(server) == 0 && full.countItem(Items.BREAD) == 60 && capital.countItem(Items.BREAD) == 30,
			"sent to a member with exactly 16 meals");
		poor.setItem(0, new ItemStack(Items.BREAD, 15));
		int sent = RealmResearch.granaries(server);
		List<Caravans.Shipment> road = data.onTheRoad().stream().filter(s -> s.to().equals(poorHall)).toList();
		helper.assertTrue(sent == 1 && road.size() == 1, "at 15 meals: " + sent + " caravans, " + road);
		int count = road.get(0).goods().stream().mapToInt(ItemStack::getCount).sum();
		helper.assertTrue(road.get(0).from().equals(fullHall), "not from the fullest store: from " + road.get(0).from());
		helper.assertTrue(count > 0 && count <= 32 && full.countItem(Items.BREAD) == 60 - count && capital.countItem(Items.BREAD) == 30,
			"the meals: " + count + " sent, fullest left " + full.countItem(Items.BREAD) + ", capital " + capital.countItem(Items.BREAD));
		helper.assertTrue(count == 32, "a member at 15 meals with a 60-meal donor wasn't sent the full 32: " + count);
		helper.succeed();
	}

	/** A real {@code /reload}: the realm topics are read again, and the realm's levels, its chosen topic and progress, and their effects stay. */
	//$ gametest_ticks_batch AREA '1200' '"qaRealmReload"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaRealmReload")
	public void qaARealReloadKeepsTheRealmsResearch(GameTestHelper helper) {
		RealmGameTests.Land land = RealmResearchGameTests.realm(helper);
		MinecraftServer server = land.level().getServer();
		RealmData.Realm realm = realmOf(land);
		land.data().putStudy(land.data().study(realm.id()).withResearch(new Research.State(Map.of(TEST_TOPIC, 1, "royal_roads", 1), Optional.of("royal_roads"), 500, true)));
		RealmResearch.forget();
		RealmResearch.Topic old = RealmResearch.get(TEST_TOPIC).orElseThrow();
		helper.assertTrue(CivicEffects.of(land.level(), land.hall(1)).marketTraders() == 2, "the effect before the reload");
		server.reloadResources(server.getPackRepository().getSelectedIds());
		helper.succeedWhen(() -> {
			RealmResearch.Topic now = RealmResearch.get(TEST_TOPIC).orElse(null);
			helper.assertTrue(now != null && now != old, "the reload hasn't read the topics again");
			helper.assertTrue(RealmResearch.all().stream().filter(t -> t.id().getNamespace().equals("aliveworkplace")).count() == 8, "not eight topics after the reload");
			Research.State s = realmState(land);
			helper.assertTrue(RealmResearch.level(s, now) == 1 && RealmResearch.current(s) == RealmResearch.get("royal_roads").orElse(null) && s.progress() == 500 && s.paid(),
				"the realm's research after the reload: " + s);
			helper.assertTrue(CivicEffects.of(land.level(), land.hall(1)).marketTraders() == 2, "the effect after the reload: " + CivicEffects.of(land.level(), land.hall(1)).marketTraders());
		});
	}

	/** Royal Post through the post office: a parcel to a village outside the realm waits for dawn; one inside goes at noon. */
	//$ gametest_ticks_batch AREA '100' '"qaRealmNoonPost"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmNoonPost")
	public void qaRoyalPostAtNoonOnlyInsideTheRealm(GameTestHelper helper) {
		RealmGameTests.Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET, VillageRanks.Rank.HAMLET);
		Research.forget();
		RealmResearch.forget();
		Leftovers.after(helper, () -> {
			Research.forget();
			RealmResearch.forget();
		});
		RealmGameTests.found(helper, land);
		helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(1), land.owner()).ok(), "the hamlet didn't join");
		ServerLevel level = land.level();
		MinecraftServer server = level.getServer();
		BlockPos fromBox = land.hall(0).offset(2, 0, 0);
		BlockPos inBox = land.hall(1).offset(2, 0, 0);
		BlockPos outBox = land.hall(2).offset(2, 0, 0);
		for (BlockPos p : List.of(fromBox, inBox, outBox)) {
			level.setBlockAndUpdate(p, ModBlocks.MAILBOX.defaultBlockState());
		}
		java.util.UUID inside = java.util.UUID.randomUUID();
		java.util.UUID outside = java.util.UUID.randomUUID();
		io.github.jcondedata.aliveworkplace.mail.PostOffice office = io.github.jcondedata.aliveworkplace.mail.PostOffice.get(server);
		office.register(inside, GlobalPos.of(level.dimension(), inBox));
		office.register(outside, GlobalPos.of(level.dimension(), outBox));
		var toOut = office.post(land.owner().getUUID(), "Owner", outside, "Far", GlobalPos.of(level.dimension(), fromBox), level.getGameTime(),
			List.of(new ItemStack(Items.APPLE, 2)));
		toOut.setStatus(io.github.jcondedata.aliveworkplace.mail.Parcel.Status.IN_TRANSIT);
		var toIn = office.post(land.owner().getUUID(), "Owner", inside, "Near", GlobalPos.of(level.dimension(), fromBox), level.getGameTime(),
			List.of(new ItemStack(Items.CARROT, 2)));
		toIn.setStatus(io.github.jcondedata.aliveworkplace.mail.Parcel.Status.IN_TRANSIT);
		Leftovers.after(helper, () -> {
			office.remove(toOut);
			office.remove(toIn);
			office.unregister(inside, GlobalPos.of(level.dimension(), inBox));
			office.unregister(outside, GlobalPos.of(level.dimension(), outBox));
		});
		RealmTopicsGameTests.know(land, Map.of("royal_roads", 1, "royal_post", 1));
		int noon = office.noon(server);
		helper.assertTrue(noon == 1 && office.parcel(toIn.id()) == null, "the parcel inside the realm didn't go at noon: " + noon);
		helper.assertTrue(office.parcel(toOut.id()) != null, "the parcel to a village outside the realm went at noon");
		helper.assertTrue(((Container) level.getBlockEntity(outBox)).countItem(Items.APPLE) == 0 && ((Container) level.getBlockEntity(inBox)).countItem(Items.CARROT) == 2,
			"the mailboxes' contents");
		helper.succeed();
	}

	/** A member's help fills the capital's own topic up to its last point and never finishes it: the capital's scholar does. */
	//$ gametest_ticks_batch AREA '100' '"qaRealmHelpCap"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmHelpCap")
	public void qaMemberHelpNeverFinishesTheCapitalsTopic(GameTestHelper helper) {
		RealmGameTests.Land land = RealmResearchGameTests.realm(helper);
		int points = Research.Topic.SWIFT_HANDS.points(1);
		land.entity(0).setResearch(new Research.State(Map.of(), Optional.of("swift_hands"), points - 1, true));
		RealmResearchGameTests.Desk member = RealmResearchGameTests.desk(helper, land, 1);
		new ScholarWork().work(land.level(), member.scholar(), member.desk(), true);
		new ScholarWork().work(land.level(), member.scholar(), member.desk(), true);
		Research.State capital = land.entity(0).research();
		helper.assertTrue(capital.level(Research.Topic.SWIFT_HANDS) == 0 && capital.currentTopic() == Research.Topic.SWIFT_HANDS && capital.progress() == points,
			"the member's help: " + capital + " of " + points);
		helper.succeed();
	}

	/** Charters reach every realm village: a member's Colony Charter costs a quarter less a level too. */
	//$ gametest_ticks_batch AREA '100' '"qaRealmChartersMember"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmChartersMember")
	public void qaChartersCheapenAMembersCharter(GameTestHelper helper) {
		RealmGameTests.Land land = RealmResearchGameTests.realm(helper);
		int base = Colonies.cost(land.level(), land.hall(1));
		RealmTopicsGameTests.know(land, Map.of("royal_roads", 2, "charters", 1));
		int one = Colonies.cost(land.level(), land.hall(1));
		RealmTopicsGameTests.know(land, Map.of("royal_roads", 2, "charters", 2));
		int two = Colonies.cost(land.level(), land.hall(1));
		helper.assertTrue(one == base * 3 / 4 && two == base / 2, "a member's charter: " + base + " -> " + one + " -> " + two);
		helper.succeed();
	}
}
