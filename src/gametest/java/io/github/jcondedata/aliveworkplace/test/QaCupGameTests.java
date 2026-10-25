package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupPage;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import io.github.jcondedata.aliveworkplace.cup.Cups;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * QA for the Festival Cup's calendar, themes and entrants (ROADMAP 28.17), written from the spec: sign-up opens when the
 * festival before ends (one tick either side) and the players in the circuit's villages are told once, and the evening
 * before (one tick either side), players outside the circuit not; the next Cup rolls over the moment the bouts end, for
 * every festival, every second and a dusk-to-dawn theme; players sign up for any village on the circuit (seven with the
 * host), two a village, and an eighth village is refused; the bracket's limits (16 and one over, Town and City); with
 * config {@code festivalCup} off there's no Cup, no sign-up and the page says why; the theme can't be changed once
 * sign-up has closed; the bracket line of the Cup page with 3 byes.
 */
public class QaCupGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final long DAY = 24000;
	private static final ResourceLocation THEME = AliveWorkplace.id("qa_cup");
	private static final ResourceLocation NIGHT = AliveWorkplace.id("qa_night_cup");

	// ---------------------------------------------------------------- helpers

	static CupThemes.Theme theme(ResourceLocation id, int order, long start, long end) {
		return new CupThemes.Theme(id, "cup.aliveworkplace.grand_cup", order, "singles", 50, 3, List.of(), "any", List.of(), List.of(), start, end,
			List.of(), ResourceLocation.withDefaultNamespace("bread"), List.of(0xFF0000), ResourceLocation.withDefaultNamespace("music_disc_cat"));
	}

	/** Makes {@code hall} a host: Village rank, an Arena on record by it, Cobblemon assumed, the test themes; all put back after. */
	static VillageHallBlockEntity host(GameTestHelper helper, BlockPos hall, VillageHallBlockEntity entity, List<BlockPos> listed) {
		ServerLevel level = helper.getLevel();
		entity.setRank(VillageRanks.Rank.VILLAGE);
		entity.setCustomName(Component.literal("Thornvale"));
		BlueprintData.Placement arena = new BlueprintData.Placement(level.dimension().location(), hall.offset(-12, -1, 3), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.ARENA.id(), arena, UUID.randomUUID());
		Map<ResourceLocation, CupThemes.Theme> saved = new LinkedHashMap<>(CupThemes.loaded());
		Map<ResourceLocation, CupThemes.Theme> mine = new LinkedHashMap<>();
		mine.put(THEME, theme(THEME, 1, CupThemes.NOON, CupThemes.MIDNIGHT));
		mine.put(NIGHT, theme(NIGHT, 2, 12000, 24000));
		CupThemes.setForTest(mine);
		Cups.COBBLEMON = true;
		Leftovers.after(helper, () -> {
			BuildSiteManager.get(level).forgetFinished(arena);
			CupData.get(level).forget(hall);
			Caravans.Data.get(level).remove(hall);
			listed.forEach(Caravans.Data.get(level)::remove);
			CupThemes.setForTest(saved);
			Cups.COBBLEMON = io.github.jcondedata.aliveworkplace.trainer.Trainers.COBBLEMON;
			Cups.EVERY = 1;
			Cups.ENABLED = true;
		});
		return entity;
	}

	private static void at(ServerLevel level, long day, long t) {
		level.setDayTime((day - 1) * DAY + t);
	}

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	private static String name(ItemStack stack) {
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		return name == null ? "" : name.getString();
	}

	private static ChoiceMenu page(ServerPlayer player, BlockPos hall) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		menu.press(HallPages.slot(CupPage.PAGE), player);
		return menu;
	}

	/** Messages starting {@code start}, each counted once (a chat line is seen both as displayed and as sent). */
	private static long count(List<Component> seen, String start) {
		java.util.Set<Component> once = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		seen.stream().filter(c -> c.getString().startsWith(start)).forEach(once::add);
		return once.size();
	}

	private static CupData.Entrant entrant(CupData.Kind kind, int tier, String name, BlockPos village) {
		return new CupData.Entrant(kind, UUID.nameUUIDFromBytes(name.getBytes()), name, tier, tier * 10, village);
	}

	// ---------------------------------------------------------------- sign-up opens, and who's told

	/**
	 * With a Cup every second festival: on the festival before, sign-up is still shut at 12999 and opens at 13000 (the
	 * festival's end), and the players in the host's village are told once (a second round says nothing); a player far
	 * outside the circuit isn't told. The evening before: nothing at 11999, "tomorrow" at 12000, once.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaCupTold"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaCupTold")
	public void signUpOpensWhenTheFestivalBeforeEndsAndTheCircuitIsTold(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
			VillageHallBlockEntity entity = host(helper, hall, (VillageHallBlockEntity) helper.getBlockEntity(HALL), List.of());
			Cups.EVERY = 2;
			QaStageFiveGameTests.Client near = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaNear"));
			QaStageFiveGameTests.Client far = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaFar"));
			far.player().moveTo(Vec3.atCenterOf(hall.offset(400, 0, 400)));
			helper.assertTrue(VillageHalls.area(hall).contains(near.player().position()) && !VillageHalls.area(hall).contains(far.player().position()),
				"setup: the players aren't where they should be");

			int gap = Festivals.every(entity);
			long offset = Festivals.offset(hall);
			long cupDay = Cups.cupDay(300, offset, gap, 2);
			long before = cupDay - gap; // the festival before: a plain one
			helper.assertTrue(!Cups.isCup(Math.floorDiv(before - offset, gap), 2), "setup: the festival before is a Cup");
			String open = "Sign-up is open for the Grand Cup at Thornvale on day " + cupDay;

			at(level, before, 12_999);
			Cups.round(level, hall, entity);
			CupData.Cup cup = CupData.get(level).existing(hall);
			helper.assertTrue(cup != null && cup.day == cupDay && !cup.closed, "the next Cup on the festival before: " + (cup == null ? null : cup.day)
				+ ", expected " + cupDay);
			helper.assertFalse(Cups.signupOpen(level, entity, cup), "sign-up open before the festival before has ended");
			helper.assertTrue(count(near.seen(), "Sign-up is open") == 0, "told sign-up is open a tick early: " + near.seen());
			List<String> card = lore(page(near.player(), hall).icon(CupPage.CARD));
			helper.assertTrue(card.contains("Sign-up opens after the festival on day " + before), "card before it opens: " + card);

			at(level, before, 13_000);
			Cups.round(level, hall, entity);
			helper.assertTrue(Cups.signupOpen(level, entity, cup), "sign-up not open when the festival before ends");
			helper.assertTrue(count(near.seen(), open) == 1, "the player in the village isn't told once: " + near.seen().stream().map(Component::getString).toList());
			String told = near.seen().stream().map(Component::getString).filter(s -> s.startsWith(open)).findFirst().orElse("");
			helper.assertTrue(told.equals(open + ": sign up on the Cup page of any Village Hall on its circuit."), "the sentence: " + told);
			helper.assertTrue(count(far.seen(), "Sign-up is open") == 0, "a player outside the circuit is told");
			card = lore(page(near.player(), hall).icon(CupPage.CARD));
			helper.assertTrue(card.contains("Sign-up is open"), "card once open: " + card);
			at(level, before, 14_000);
			Cups.round(level, hall, entity);
			helper.assertTrue(count(near.seen(), "Sign-up is open") == 1, "told twice");

			String tomorrow = "The Grand Cup at Thornvale is tomorrow: the bouts start at noon.";
			at(level, cupDay - 1, 11_999);
			Cups.round(level, hall, entity);
			helper.assertTrue(count(near.seen(), "The Grand Cup at Thornvale is tomorrow") == 0, "told 'tomorrow' before the evening");
			helper.assertTrue(cup.day == cupDay && !cup.closed, "the Cup moved or closed the day before: day " + cup.day + " closed " + cup.closed);
			at(level, cupDay - 1, 12_000);
			Cups.round(level, hall, entity);
			Cups.round(level, hall, entity);
			helper.assertTrue(count(near.seen(), tomorrow) == 1, "the evening before: "
				+ near.seen().stream().map(Component::getString).toList());
			helper.assertTrue(count(far.seen(), "The Grand Cup") == 0, "a player outside the circuit is told it's tomorrow");
			helper.assertTrue(count(near.seen(), "Sign-up is open") == 1, "told sign-up is open again");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the calendar's edges

	/**
	 * The next Cup is the one whose bouts aren't over: a tick before they end it's still today's, at the end the next one
	 * (the next festival; two festivals on with cupEveryFestivals 2); a dusk-to-dawn theme stays today's until dawn.
	 */
	//$ gametest_batch AREA '"qaCupCalendar"'
	@GameTest(template = AREA, batch = "qaCupCalendar")
	public void theNextCupRollsOverTheMomentTheBoutsEnd(GameTestHelper helper) {
		BlockPos at = new BlockPos(4, 2, 4);
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(at);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(at);
		int gap = Festivals.every(entity);
		long offset = Festivals.offset(hall);
		CupThemes.Theme noon = theme(THEME, 1, CupThemes.NOON, CupThemes.MIDNIGHT);
		CupThemes.Theme night = theme(NIGHT, 2, 12000, 24000);
		try {
			Cups.EVERY = 1;
			long d = Cups.cupDay(500, offset, gap, 1);
			helper.assertTrue(Cups.upcoming(hall, entity, (d - 1) * DAY + 17_999, noon) == d, "a tick before midnight: "
				+ Cups.upcoming(hall, entity, (d - 1) * DAY + 17_999, noon) + ", expected " + d);
			helper.assertTrue(Cups.upcoming(hall, entity, (d - 1) * DAY + 18_000, noon) == d + gap, "at midnight: "
				+ Cups.upcoming(hall, entity, (d - 1) * DAY + 18_000, noon) + ", expected " + (d + gap));
			helper.assertTrue(Cups.upcoming(hall, entity, (d - 2) * DAY + 23_999, noon) == d, "the evening before");
			helper.assertTrue(Cups.upcoming(hall, entity, (d - 1) * DAY + 23_999, night) == d && Cups.upcoming(hall, entity, d * DAY, night) == d + gap,
				"dusk to dawn: " + Cups.upcoming(hall, entity, (d - 1) * DAY + 23_999, night) + ", " + Cups.upcoming(hall, entity, d * DAY, night));
			Cups.EVERY = 2;
			long d2 = Cups.cupDay(500, offset, gap, 2);
			helper.assertTrue(Cups.upcoming(hall, entity, (d2 - 1) * DAY + 17_999, noon) == d2
				&& Cups.upcoming(hall, entity, (d2 - 1) * DAY + 18_000, noon) == d2 + 2L * gap, "every second festival: "
				+ Cups.upcoming(hall, entity, (d2 - 1) * DAY + 18_000, noon) + ", expected " + (d2 + 2L * gap));
			// the festival between two Cups is a plain one: from the day after a Cup, the next is two festivals on
			helper.assertTrue(Cups.cupDay(d2 + 1, offset, gap, 2) == d2 + 2L * gap && Cups.cupDay(d2 + gap, offset, gap, 2) == d2 + 2L * gap,
				"the plain festival between two Cups");
			Cups.EVERY = 3;
			long d3 = Cups.cupDay(500, offset, gap, 3);
			helper.assertTrue(Math.floorMod(Math.floorDiv(d3 - offset, gap), 3) == 0 && Cups.cupDay(d3 + 1, offset, gap, 3) == d3 + 3L * gap,
				"every third festival: " + d3 + ", then " + Cups.cupDay(d3 + 1, offset, gap, 3));
		} finally {
			Cups.EVERY = 1;
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- sign-up from the circuit

	/**
	 * Players sign up for any village on the circuit, two a village (two for the host and two for a partner: four); a
	 * village with a route that's the seventh partner (an eighth on the circuit) is refused, and its players aren't
	 * entrants; when a nearer partner drops its route, it's on the circuit and may sign up.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaCupCircuit"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaCupCircuit")
	public void playersSignUpFromEveryCircuitVillageButNotAnEighth(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos bAt = new BlockPos(2, 2, 2);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(bAt, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos b = helper.absolutePos(bAt);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
			List<BlockPos> listed = new ArrayList<>();
			VillageHallBlockEntity entity = host(helper, hall, (VillageHallBlockEntity) helper.getBlockEntity(HALL), listed);
			entity.setRank(VillageRanks.Rank.CITY);
			VillageHallBlockEntity other = (VillageHallBlockEntity) helper.getBlockEntity(bAt);
			other.setRank(VillageRanks.Rank.HAMLET);
			other.setCustomName(Component.literal("Brookend"));
			Caravans.Data data = Caravans.Data.get(level);
			data.setWants(hall, Component.literal("Thornvale"), List.of());
			// six partners nearer than Brookend (list entries only), then Brookend: the seventh
			List<BlockPos> near = new ArrayList<>();
			for (int i = 1; i <= Cups.MAX_CIRCUIT - 1; i++) {
				BlockPos p = hall.offset(i, 0, 1);
				data.setWants(p, Component.literal("Near" + i), List.of());
				helper.assertTrue(data.toggleRoute(hall, p, 10), "setup: route " + i);
				near.add(p);
				listed.add(p);
			}
			data.setWants(b, Component.literal("Brookend"), List.of());
			listed.add(b);
			helper.assertTrue(data.toggleRoute(b, hall, 10), "setup: Brookend's route"); // its route to the host
			Cups.round(level, hall, entity);
			CupData.Cup cup = CupData.get(level).existing(hall);
			helper.assertTrue(cup != null && cup.day >= 0, "no Cup");
			cup.closed = false;
			cup.noCup = "";
			helper.assertTrue(Cups.circuit(level, hall).size() == Cups.MAX_CIRCUIT && !Cups.circuit(level, hall).contains(b),
				"setup: the circuit " + Cups.circuit(level, hall));

			ServerPlayer p1 = helper.makeMockServerPlayerInLevel();
			ServerPlayer p2 = helper.makeMockServerPlayerInLevel();
			ServerPlayer p3 = helper.makeMockServerPlayerInLevel();
			ServerPlayer p4 = helper.makeMockServerPlayerInLevel();
			String off = Cups.signUp(level, b, p1).getString();
			helper.assertTrue(off.equals("This village is on no Festival Cup's circuit"), "an eighth village signed up: " + off);
			helper.assertTrue(cup.signups.isEmpty(), "signed up anyway: " + cup.signups);

			// a nearer partner drops its route: Brookend is the sixth partner now
			helper.assertTrue(!data.toggleRoute(hall, near.get(0), 10), "setup: the route isn't dropped");
			helper.assertTrue(Cups.circuit(level, hall).contains(b) && Cups.circuit(level, hall).size() == Cups.MAX_CIRCUIT, "Brookend isn't on the circuit");
			helper.assertTrue(Cups.signUp(level, b, p1).getString().equals("You're signed up for the Cup, standing for Brookend"), "p1 for Brookend");
			helper.assertTrue(Cups.signUp(level, b, p2).getString().startsWith("You're signed up"), "p2 for Brookend");
			helper.assertTrue(Cups.signUp(level, hall, p3).getString().equals("You're signed up for the Cup, standing for Thornvale"), "p3 for the host");
			helper.assertTrue(Cups.signUp(level, hall, p4).getString().startsWith("You're signed up"), "p4 for the host: two a village, not two a Cup");
			ServerPlayer p5 = helper.makeMockServerPlayerInLevel();
			helper.assertTrue(Cups.signUp(level, b, p5).getString().equals("Brookend already has its 2 players"), "a third for Brookend");
			List<CupData.Entrant> players = Cups.entrants(level, hall, cup).stream().filter(e -> e.kind() == CupData.Kind.PLAYER).toList();
			helper.assertTrue(players.size() == 4 && players.stream().filter(e -> e.village().equals(b)).count() == 2, "players entered: " + players);
			// the page of Brookend's hall shows the host's Cup and its signed-up players
			ChoiceMenu menu = page(p1, b);
			helper.assertTrue(name(menu.icon(CupPage.CARD)).equals("Grand Cup") && lore(menu.icon(CupPage.CARD)).contains("At Thornvale, on day " + cup.day),
				"Brookend's page: " + name(menu.icon(CupPage.CARD)) + " " + lore(menu.icon(CupPage.CARD)));
			helper.assertTrue(lore(menu.icon(CupPage.WITHDRAW)).contains("Click: take your name off the list"), "p1 isn't shown as signed up");

			// the route comes back: Brookend is off the circuit, and its players are no entrants
			data.toggleRoute(hall, near.get(0), 10);
			players = Cups.entrants(level, hall, cup).stream().filter(e -> e.kind() == CupData.Kind.PLAYER).toList();
			helper.assertTrue(players.size() == 2 && players.stream().noneMatch(e -> e.village().equals(b)), "players of a village off the circuit: " + players);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the bracket's limits

	/** A City's bracket holds 16: 16 entrants, no byes; 17, the lowest seed left out; 9, seven byes. A Town's holds 8: 9, the ninth out. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theBracketsLimitsAndOneOver(GameTestHelper helper) {
		List<CupData.Entrant> all = new ArrayList<>();
		for (int i = 0; i < 17; i++) {
			// tiers 5 down to 1 for the villages' Trainers, then players, then the host's Trainers
			CupData.Kind kind = i < 10 ? CupData.Kind.TRAINER : i < 14 ? CupData.Kind.PLAYER : CupData.Kind.HOST_TRAINER;
			all.add(entrant(kind, kind == CupData.Kind.PLAYER ? 0 : 5 - i / 2 % 5, "e" + i, BlockPos.ZERO));
		}
		List<CupData.Entrant> seeds = Cups.seed(all);
		int city = Cups.capacity(VillageRanks.Rank.CITY);
		int town = Cups.capacity(VillageRanks.Rank.TOWN);
		List<CupData.Entrant> sixteen = Cups.bracket(seeds.subList(0, 16), city);
		helper.assertTrue(sixteen.size() == 16 && Cups.byes(sixteen) == 0 && sixteen.containsAll(seeds.subList(0, 16)), "16 in a City's Cup: " + sixteen.size());
		List<CupData.Entrant> seventeen = Cups.bracket(seeds, city);
		helper.assertTrue(seventeen.size() == 16 && Cups.byes(seventeen) == 0 && !seventeen.contains(seeds.get(16)) && seventeen.contains(seeds.get(15)),
			"17 in a City's Cup: the 17th seed should be left out");
		List<CupData.Entrant> nine = Cups.bracket(seeds.subList(0, 9), city);
		helper.assertTrue(nine.size() == 16 && Cups.byes(nine) == 7, "9 in a City's Cup: " + nine.size() + " slots, " + Cups.byes(nine) + " byes");
		List<CupData.Entrant> townNine = Cups.bracket(seeds.subList(0, 9), town);
		helper.assertTrue(townNine.size() == 8 && Cups.byes(townNine) == 0 && !townNine.contains(seeds.get(8)), "9 in a Town's Cup");
		List<CupData.Entrant> villageFive = Cups.bracket(seeds.subList(0, 5), Cups.capacity(VillageRanks.Rank.VILLAGE));
		helper.assertTrue(villageFive.size() == 4 && !villageFive.contains(seeds.get(4)), "5 in a Village's Cup: the fifth out");
		helper.assertTrue(Cups.capacity(VillageRanks.Rank.HAMLET) == 4, "a Hamlet's (never a host) capacity");
		// the host's own Trainers are the lowest seeds, below every player
		helper.assertTrue(seeds.get(16).kind() == CupData.Kind.HOST_TRAINER && seeds.get(10).kind() == CupData.Kind.PLAYER
			&& seeds.get(9).kind() == CupData.Kind.TRAINER, "seed order: " + seeds.stream().map(e -> e.kind().name()).toList());
		helper.succeed();
	}

	// ---------------------------------------------------------------- config off

	/** With festivalCup off a would-be host holds no Cup: its round sets none, sign-up is refused, the tab and the page say why; a Cup set before is shut too. */
	//$ gametest_ticks_batch HUGE '200' '"qaCupOff"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaCupOff")
	public void withFestivalCupOffThereIsNoCupAndThePageSaysWhy(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
			VillageHallBlockEntity entity = host(helper, hall, (VillageHallBlockEntity) helper.getBlockEntity(HALL), List.of());
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			Cups.ENABLED = false;
			Cups.round(level, hall, entity);
			helper.assertTrue(CupData.get(level).existing(hall) == null, "a Cup set with festivalCup off");
			helper.assertTrue(Cups.signUp(level, hall, player).getString().equals("This village is on no Festival Cup's circuit"), "sign-up with it off");
			ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
			helper.assertTrue(lore(menu.icon(HallPages.slot(CupPage.PAGE))).contains("No Festival Cup here yet"), "tab: " + lore(menu.icon(HallPages.slot(CupPage.PAGE))));
			menu = page(player, hall);
			helper.assertTrue(name(menu.icon(CupPage.CARD)).equals("No Festival Cup here yet")
				&& lore(menu.icon(CupPage.CARD)).contains("The Festival Cup is switched off (config festivalCup)"), "card: " + lore(menu.icon(CupPage.CARD)));

			// on: a Cup is set; switched off again, it's shut
			Cups.ENABLED = true;
			Cups.round(level, hall, entity);
			CupData.Cup cup = CupData.get(level).existing(hall);
			helper.assertTrue(cup != null && cup.day >= 0, "no Cup with it on");
			cup.closed = false;
			Cups.ENABLED = false;
			helper.assertTrue(Cups.signUp(level, hall, player).getString().equals("This village is on no Festival Cup's circuit") && cup.signups.isEmpty(),
				"signed up for a Cup that's switched off");
			helper.assertTrue(Cups.nextTheme(level, hall, player).getString().equals("This village is on no Festival Cup's circuit"), "theme picked while off");
			menu = page(player, hall);
			helper.assertTrue(lore(menu.icon(CupPage.CARD)).contains("The Festival Cup is switched off (config festivalCup)"), "card when off: "
				+ lore(menu.icon(CupPage.CARD)));
			Cups.ENABLED = true;
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the theme until sign-up closes

	/** The host's owner may pick the theme (in order, round to the first again) until sign-up closes, not after; nobody else may. */
	//$ gametest_ticks_batch HUGE '200' '"qaCupTheme"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaCupTheme")
	public void theThemeIsPickedOnlyUntilSignUpCloses(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
			VillageHallBlockEntity entity = host(helper, hall, (VillageHallBlockEntity) helper.getBlockEntity(HALL), List.of());
			Cups.round(level, hall, entity);
			CupData.Cup cup = CupData.get(level).existing(hall);
			cup.closed = false;
			cup.theme = THEME;
			cup.themePicked = false;
			ServerPlayer owner = helper.makeMockServerPlayerInLevel();
			ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
			entity.setOwner(owner.getUUID(), "Owner");
			helper.assertTrue(Cups.nextTheme(level, hall, stranger).getString().equals("Only the host village's owner may pick the Cup's theme")
				&& THEME.equals(cup.theme), "a stranger picked");
			helper.assertTrue(Cups.nextTheme(level, hall, owner).getString().equals("The next Cup will be the Grand Cup") && NIGHT.equals(cup.theme)
				&& cup.themePicked, "the owner can't pick: " + cup.theme);
			helper.assertTrue(Cups.nextTheme(level, hall, owner).getString().equals("The next Cup will be the Grand Cup") && THEME.equals(cup.theme),
				"again: after the last theme, the first: " + cup.theme);
			// the page's theme button says it was picked
			ChoiceMenu menu = page(owner, hall);
			helper.assertTrue(lore(menu.icon(CupPage.THEME)).contains("Picked by the host's owner"), "theme button " + lore(menu.icon(CupPage.THEME)));
			// sign-up closes: the theme stays
			Cups.close(level, hall, cup);
			helper.assertTrue(Cups.nextTheme(level, hall, owner).getString().equals("Sign-up for this Cup has closed") && THEME.equals(cup.theme),
				"theme picked after sign-up closed: " + cup.theme);
			menu = page(owner, hall);
			menu.press(CupPage.THEME, owner);
			helper.assertTrue(THEME.equals(cup.theme), "the page's button changed the theme after sign-up closed: " + cup.theme);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the bracket line on the page

	/** A closed Town Cup with 5 entrants: the page says the bracket holds 8, five entrants, a bracket of 8 with 3 byes, the seeds in order. */
	//$ gametest_ticks_batch HUGE '200' '"qaCupByes"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaCupByes")
	public void thePageShowsTheBracketAndItsByes(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
			VillageHallBlockEntity entity = host(helper, hall, (VillageHallBlockEntity) helper.getBlockEntity(HALL), List.of());
			entity.setRank(VillageRanks.Rank.TOWN);
			Cups.round(level, hall, entity);
			CupData.Cup cup = CupData.get(level).existing(hall);
			List<CupData.Entrant> five = Cups.seed(List.of(entrant(CupData.Kind.HOST_TRAINER, 2, "Pip", hall), entrant(CupData.Kind.LEADER, 4, "Mira", hall),
				entrant(CupData.Kind.PLAYER, 0, "Alex", hall), entrant(CupData.Kind.TRAINER, 3, "Ren", hall), entrant(CupData.Kind.LEADER, 5, "Dara", hall)));
			cup.closed = true;
			cup.noCup = "";
			cup.entrants.clear();
			cup.entrants.addAll(five);
			cup.bracket.clear();
			cup.bracket.addAll(Cups.bracket(five, Cups.capacity(VillageRanks.Rank.TOWN)));
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			ChoiceMenu menu = page(player, hall);
			ItemStack bracket = menu.icon(CupPage.BRACKET);
			helper.assertTrue(name(bracket).equals("The bracket holds 8"), "bracket " + name(bracket));
			helper.assertTrue(lore(bracket).contains("Entrants: 5") && lore(bracket).contains("A bracket of 8, with 3 byes for the top seeds"), "lines " + lore(bracket));
			helper.assertTrue(lore(menu.icon(CupPage.CARD)).contains("Sign-up has closed: the bracket is drawn"), "card " + lore(menu.icon(CupPage.CARD)));
			List<String> seeds = new ArrayList<>();
			for (int i = 0; i < 5; i++) {
				seeds.add(name(menu.icon(CupPage.SEEDS_ROW + i)) + " / " + lore(menu.icon(CupPage.SEEDS_ROW + i)).get(0));
			}
			helper.assertTrue(seeds.equals(List.of("Seed 1: Dara / Trainer Leader of Thornvale", "Seed 2: Mira / Trainer Leader of Thornvale",
				"Seed 3: Ren / Best Trainer of Thornvale", "Seed 4: Alex / Player, for Thornvale", "Seed 5: Pip / Trainer of the host, Thornvale")), "seeds " + seeds);
			helper.succeed();
		});
	}
}
