package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.cup.CupChampions;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupDays;
import io.github.jcondedata.aliveworkplace.cup.CupPage;
import io.github.jcondedata.aliveworkplace.cup.Cups;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1123-0953) for the Festival Cup (ROADMAP 28.17 to 28.22, docs/wiki/festival-cup.md), written from the spec
 * and the wiki, not the code: fewer than 4 entrants when sign-up closes is a plain festival and the page says why, and 4
 * is a Cup of 4 with no byes; Sign up and Withdraw through the Cup page's own buttons (a withdrawal frees the village's
 * place, one Cup a player); the defending champion seeded first (a player champion above every Leader, else the holder
 * village's villager entrant, nobody for an old save's champion that stands for no entrant); delegates are guests and
 * get no pride, nor does another village; a Cup saved mid-event (byes, results, an old champion without an id) comes
 * back whole, and an empty saved Cup loads with no theme and no day; a partner's hall broken mid-sign-up takes it off
 * the circuit; the Cup's sentences in American English with their real numbers.
 */
public class QaFestivalCup1123cGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final long DAY = 24000;

	// ---------------------------------------------------------------- helpers

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	private static ChoiceMenu page(ServerPlayer player, BlockPos hall) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		menu.press(HallPages.slot(CupPage.PAGE), player);
		return menu;
	}

	/** A host with no villagers of its own (so no host Trainers or Leader), its Cup set by a round. */
	private static VillageHallBlockEntity bareHost(GameTestHelper helper, BlockPos hall, List<BlockPos> listed) {
		ServerLevel level = helper.getLevel();
		level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
		return QaCupGameTests.host(helper, hall, (VillageHallBlockEntity) level.getBlockEntity(hall), listed);
	}

	/** Sets the time to the day before the next Cup, in the afternoon: sign-up is open (the festival before is over). */
	private static long dayBefore(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		int gap = Festivals.every(entity);
		long cupDay = Cups.cupDay(400, Festivals.offset(hall), gap, 1);
		level.setDayTime((cupDay - 2) * DAY + 14_000);
		return cupDay;
	}

	/** A far partner (never loaded) on the host's circuit with its Leader on the caravans' list. */
	private static BlockPos farPartner(GameTestHelper helper, BlockPos hall, List<BlockPos> listed, int i, String name, int tier, boolean leader) {
		ServerLevel level = helper.getLevel();
		Caravans.Data data = Caravans.Data.get(level);
		BlockPos p = hall.offset(4000 + 200 * i, 0, 4000);
		helper.assertFalse(level.isLoaded(p), "setup: the far partner " + name + " is loaded");
		data.setWants(p, Component.literal(name + "ton"), List.of());
		listed.add(p);
		helper.assertTrue(data.toggleRoute(hall, p, 10), "setup: route to " + name);
		data.setLeader(p, new Caravans.Leader(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name, tier, tier * 10, leader));
		return p;
	}

	// ---------------------------------------------------------------- fewer than 4, and exactly 4

	/**
	 * Spec 28.17: "Fewer than 4: no Cup, a plain festival, and the page says why." Two players for the host and one far
	 * partner's Leader, nobody else: when the Cup's day dawns sign-up closes with no bracket and the card says "Fewer than 4
	 * entrants: no Cup this time, just a plain festival". With a second far Leader, the same dawn draws a bracket of 4 with
	 * no byes and no reason.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaCupFew1123c"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaCupFew1123c")
	public void threeEntrantsAreAPlainFestivalAndFourAreACup(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			List<BlockPos> listed = new ArrayList<>();
			VillageHallBlockEntity entity = bareHost(helper, hall, listed);
			for (int n : new int[] {3, 4}) {
				CupData.get(level).forget(hall);
				// fresh far partners each time (the page opened last time may have touched the old ones): 1 Leader, then 2
				listed.forEach(Caravans.Data.get(level)::remove);
				for (int i = 0; i < n - 2; i++) {
					farPartner(helper, hall, listed, 10 * n + i, "Lead" + n + "x" + i, 3, true);
				}
				long cupDay = dayBefore(level, hall, entity);
				Cups.round(level, hall, entity);
				CupData.Cup cup = CupData.get(level).existing(hall);
				helper.assertTrue(cup != null && cup.day == cupDay && !cup.closed, n + ": setup: the Cup " + (cup == null ? null : cup.day) + " vs " + cupDay);
				List<ServerPlayer> players = List.of(helper.makeMockServerPlayerInLevel(), helper.makeMockServerPlayerInLevel());
				for (ServerPlayer p : players) {
					String said = Cups.signUp(level, hall, p).getString();
					helper.assertTrue(said.startsWith("You're signed up"), n + ": a player wasn't signed up: " + said);
				}
				helper.assertTrue(Cups.entrants(level, hall, cup).size() == n, n + ": setup: entrants " + Cups.entrants(level, hall, cup));
				level.setDayTime((cupDay - 1) * DAY + 100); // the Cup's morning: sign-up closes
				Cups.round(level, hall, entity);
				helper.assertTrue(cup.closed, n + ": sign-up didn't close on the Cup's morning");
				List<String> card = lore(page(players.get(0), hall).icon(CupPage.CARD));
				if (n == 3) {
					helper.assertTrue(cup.bracket.isEmpty(), "3 entrants drew a bracket of " + cup.bracket.size());
					helper.assertTrue(card.contains("Fewer than 4 entrants: no Cup this time, just a plain festival"), "3 entrants, the card: " + card);
				} else {
					helper.assertTrue(cup.bracket.size() == 4 && Cups.byes(cup.bracket) == 0 && cup.noCup.isEmpty(), "4 entrants: a bracket of "
						+ cup.bracket.size() + " with " + Cups.byes(cup.bracket) + " byes, noCup '" + cup.noCup + "'");
					helper.assertTrue(card.contains("Sign-up has closed: the bracket is drawn") && card.stream().noneMatch(s -> s.startsWith("Fewer than")),
						"4 entrants, the card: " + card);
				}
			}
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the page's own buttons

	/**
	 * Through the Cup page's buttons: two players press Sign up for the host and are on the list, a third pressing it is
	 * not (two a village); the first presses Withdraw and is off, and the third's next press puts them on. A player on the
	 * list can't also sign up for a partner village (one Cup, one village). The Withdraw button of someone not signed up
	 * changes nothing.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaCupButtons1123c"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaCupButtons1123c")
	public void signUpAndWithdrawThroughThePageButtons(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos bAt = new BlockPos(3, 2, 3);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(bAt, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos b = helper.absolutePos(bAt);
			List<BlockPos> listed = new ArrayList<>();
			VillageHallBlockEntity entity = bareHost(helper, hall, listed);
			VillageHallBlockEntity brook = (VillageHallBlockEntity) helper.getBlockEntity(bAt);
			brook.setRank(VillageRanks.Rank.HAMLET);
			brook.setCustomName(Component.literal("Brookend"));
			Caravans.Data data = Caravans.Data.get(level);
			data.setWants(hall, Component.literal("Thornvale"), List.of());
			data.setWants(b, Component.literal("Brookend"), List.of());
			listed.add(b);
			helper.assertTrue(data.toggleRoute(b, hall, 10), "setup: Brookend's route");
			dayBefore(level, hall, entity);
			Cups.round(level, hall, entity);
			CupData.Cup cup = CupData.get(level).existing(hall);
			helper.assertTrue(cup != null && !cup.closed && Cups.signupOpen(level, entity, cup), "setup: sign-up isn't open");

			ServerPlayer p1 = helper.makeMockServerPlayerInLevel();
			ServerPlayer p2 = helper.makeMockServerPlayerInLevel();
			ServerPlayer p3 = helper.makeMockServerPlayerInLevel();
			page(p1, hall).press(CupPage.SIGN_UP, p1);
			page(p2, hall).press(CupPage.SIGN_UP, p2);
			page(p3, hall).press(CupPage.SIGN_UP, p3);
			List<UUID> on = cup.signups.stream().map(CupData.Signup::player).toList();
			helper.assertTrue(on.equals(List.of(p1.getUUID(), p2.getUUID())), "after three presses of Sign up, the list: " + on);
			// p1 is on: the Withdraw button says how to take the name off
			helper.assertTrue(lore(page(p1, hall).icon(CupPage.WITHDRAW)).contains("Click: take your name off the list"), "p1's Withdraw: "
				+ lore(page(p1, hall).icon(CupPage.WITHDRAW)));
			// p1 also tries Brookend's page: one Cup, one village
			page(p1, b).press(CupPage.SIGN_UP, p1);
			helper.assertTrue(cup.signups.size() == 2 && cup.signups.stream().filter(s -> s.player().equals(p1.getUUID())).count() == 1
				&& cup.signups.stream().noneMatch(s -> s.village().equals(b)), "p1 signed up twice: " + cup.signups);
			helper.assertTrue(Cups.signUp(level, b, p1).getString().equals("You've already signed up for this Cup"), "the second village's answer");
			// p3, not on the list, presses Withdraw: nothing changes
			page(p3, hall).press(CupPage.WITHDRAW, p3);
			helper.assertTrue(cup.signups.size() == 2, "a stranger's Withdraw changed the list: " + cup.signups);
			// p1 withdraws: the place is free for p3
			page(p1, hall).press(CupPage.WITHDRAW, p1);
			helper.assertTrue(cup.signups.stream().noneMatch(s -> s.player().equals(p1.getUUID())), "Withdraw didn't take p1 off: " + cup.signups);
			helper.assertTrue(lore(page(p1, hall).icon(CupPage.WITHDRAW)).contains("You haven't signed up"), "p1's Withdraw after: "
				+ lore(page(p1, hall).icon(CupPage.WITHDRAW)));
			page(p3, hall).press(CupPage.SIGN_UP, p3);
			on = cup.signups.stream().map(CupData.Signup::player).toList();
			helper.assertTrue(on.size() == 2 && on.contains(p2.getUUID()) && on.contains(p3.getUUID()), "the freed place: " + on);
			// p1 may now stand for Brookend instead
			page(p1, b).press(CupPage.SIGN_UP, p1);
			helper.assertTrue(cup.signups.stream().anyMatch(s -> s.player().equals(p1.getUUID()) && s.village().equals(b)), "p1 for Brookend: " + cup.signups);
			List<CupData.Entrant> players = Cups.entrants(level, hall, cup).stream().filter(e -> e.kind() == CupData.Kind.PLAYER).toList();
			helper.assertTrue(players.size() == 3, "players entered: " + players);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the defending champion

	/**
	 * Wiki 28.21: "at the host's next Cup the defending champion is seeded first. That's the last champion if they entered,
	 * else the holder village's villager entrant." A player champion who signs up again is seed 1 above two Leaders (tiers
	 * 5 and 4); a Leader who won (and isn't the last champion's id) defends for its village; an old save's champion (no id)
	 * of a village with no villager entrant seeds nobody first, and the tier-5 Leader leads.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaCupDefend1123c"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaCupDefend1123c")
	public void theDefendingChampionIsSeededFirst(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			List<BlockPos> listed = new ArrayList<>();
			VillageHallBlockEntity entity = bareHost(helper, hall, listed);
			BlockPos dara = farPartner(helper, hall, listed, 1, "Dara", 5, true);
			BlockPos mira = farPartner(helper, hall, listed, 2, "Mira", 4, true);
			BlockPos ren = farPartner(helper, hall, listed, 3, "Ren", 2, false);
			dayBefore(level, hall, entity);
			Cups.round(level, hall, entity);
			CupData.Cup cup = CupData.get(level).existing(hall);
			ServerPlayer alex = helper.makeMockServerPlayerInLevel();
			helper.assertTrue(Cups.signUp(level, hall, alex).getString().startsWith("You're signed up"), "setup: Alex's sign-up");
			ResourceLocation theme = cup.theme;
			String name = alex.getGameProfile().getName();

			List<CupData.Entrant> plain = Cups.entrants(level, hall, cup);
			helper.assertTrue(plain.size() == 4 && plain.get(0).name().equals("Dara") && plain.get(3).kind() == CupData.Kind.PLAYER,
				"setup: seeds with no champion " + plain.stream().map(CupData.Entrant::name).toList());

			// 1) Alex won the last Cup for the host and entered again
			cup.champions.add(new CupData.Champion(10, theme, name, hall, alex.getUUID()));
			List<CupData.Entrant> seeds = Cups.entrants(level, hall, cup);
			helper.assertTrue(seeds.get(0).id().equals(alex.getUUID()), "the player champion isn't seed 1: " + seeds.stream().map(CupData.Entrant::name).toList());
			helper.assertTrue(seeds.get(1).name().equals("Dara") && seeds.get(2).name().equals("Mira"), "the rest in order: "
				+ seeds.stream().map(CupData.Entrant::name).toList());

			// 2) Ren's village holds it, won by Ren's predecessor (another id): Ren, its villager entrant, defends
			cup.champions.add(new CupData.Champion(20, theme, "Old Ren", ren, UUID.nameUUIDFromBytes("nobody".getBytes(StandardCharsets.UTF_8))));
			seeds = Cups.entrants(level, hall, cup);
			helper.assertTrue(seeds.get(0).name().equals("Ren"), "the holder village's entrant isn't seed 1: " + seeds.stream().map(CupData.Entrant::name).toList());

			// 3) an old save's champion (no id) for the host, which has no villager entrant (only Alex stands for it)
			cup.champions.add(new CupData.Champion(30, theme, "Someone", hall, null));
			seeds = Cups.entrants(level, hall, cup);
			helper.assertTrue(seeds.get(0).name().equals("Dara"), "a player was seeded first for an old champion with no id: "
				+ seeds.stream().map(CupData.Entrant::name).toList());

			// festivalCup off: nobody holds a Cup, so nobody defends
			cup.champions.add(new CupData.Champion(40, theme, name, hall, alex.getUUID()));
			Cups.ENABLED = false;
			try {
				List<CupData.Entrant> off = Cups.seed(Cups.entrants(level, hall, cup), CupChampions.defending(cup, Cups.entrants(level, hall, cup)));
				helper.assertTrue(off.isEmpty() || off.get(0).name().equals("Dara"), "with the Cup off a champion is seeded first: "
					+ off.stream().map(CupData.Entrant::name).toList());
			} finally {
				Cups.ENABLED = true;
			}
			helper.assertTrue(dara != null && mira != null, "setup");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- pride, and who doesn't get it

	/**
	 * Wiki 28.21: "+5 mood for every villager whose nearest hall holds a Cup. Delegates are guests and don't get it." A
	 * villager by the holder's hall is proud (+5); a delegate standing beside it isn't; a villager of the other village isn't;
	 * with festivalCup off, nobody is.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaCupPride1123c"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaCupPride1123c")
	public void delegatesAndOtherVillagesGetNoPride(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos holderAt = new BlockPos(4, 2, 4);
		BlockPos otherAt = new BlockPos(26, 2, 26);
		helper.setBlock(holderAt, ModBlocks.VILLAGE_HALL);
		helper.setBlock(otherAt, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos holder = helper.absolutePos(holderAt);
			BlockPos other = helper.absolutePos(otherAt);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(holder)).forEach(Villager::discard);
			Leftovers.after(helper, () -> CupData.get(level).forget(other));
			Villager baker = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 4));
			Villager guest = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
			Villager away = helper.spawn(EntityType.VILLAGER, new BlockPos(24, 2, 26));
			List.of(baker, guest, away).forEach(v -> v.setNoAi(true));
			ModAttachments.CUP_DELEGATE.set(guest, new CupDays.Delegate(other, UUID.nameUUIDFromBytes("Lead".getBytes(StandardCharsets.UTF_8)), 3, other, 5L));
			helper.assertTrue(CupDays.isDelegate(guest), "setup: the guest isn't a delegate");
			helper.assertTrue(VillageHalls.nearest(level, guest.blockPosition()).orElse(null) != null
				&& VillageHalls.nearest(level, guest.blockPosition()).get().equals(holder), "setup: the guest's nearest hall isn't the holder's");
			helper.assertTrue(CupChampions.mood(level, baker) == null, "pride before any Cup was won");
			// the other village's Cup was won by the holder village
			CupData.get(level).cup(other).champions.add(new CupData.Champion(3, AliveWorkplace.id("grand_cup"), "Mira", holder, null));
			var pride = CupChampions.mood(level, baker);
			helper.assertTrue(pride != null && pride.points() == 5 && pride.reason().getString().equals("our village holds the Cup"),
				"the holder's villager: " + (pride == null ? null : pride.reason().getString() + " " + pride.points()));
			helper.assertTrue(CupChampions.mood(level, guest) == null, "a delegate (a guest) is proud of the village it's visiting");
			helper.assertTrue(CupChampions.mood(level, away) == null, "the other village's villager is proud of a Cup it lost");
			Cups.ENABLED = false;
			try {
				helper.assertTrue(CupChampions.mood(level, baker) == null, "pride with festivalCup off");
			} finally {
				Cups.ENABLED = true;
			}
			helper.assertTrue(CupChampions.mood(level, baker) != null, "the pride didn't come back when the Cup was switched on again");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- saved mid-event

	/**
	 * A Cup saved on its day, mid-bracket, comes back whole: theme picked, last theme, day, closed, the told days, the
	 * signed-up players, the entrants, a bracket of 8 with 3 byes in their places, two results, the roll with an old
	 * champion without an id, the day it was put off. A saved Cup with nothing but its host loads with no theme, no day and
	 * sign-up not closed (wiki, "Saved data").
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aCupSavedMidEventComesBackWhole(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos host = new BlockPos(-31_000, 70, 12_345);
		BlockPos far = new BlockPos(-30_500, 64, 12_000);
		CupData data = new CupData();
		CupData.Cup cup = data.cup(host);
		cup.theme = AliveWorkplace.id("sun_cup");
		cup.lastTheme = AliveWorkplace.id("little_cup");
		cup.themePicked = true;
		cup.day = 812;
		cup.closed = true;
		cup.toldOpen = 812;
		cup.toldEvening = 812;
		cup.postponed = 2;
		UUID alex = UUID.nameUUIDFromBytes("Alex".getBytes(StandardCharsets.UTF_8));
		cup.signups.add(new CupData.Signup(alex, "Alex", far));
		List<CupData.Entrant> five = new ArrayList<>();
		String[] names = {"Dara", "Mira", "Ren", "Alex", "Pip"};
		CupData.Kind[] kinds = {CupData.Kind.LEADER, CupData.Kind.LEADER, CupData.Kind.TRAINER, CupData.Kind.PLAYER, CupData.Kind.HOST_TRAINER};
		for (int i = 0; i < 5; i++) {
			UUID id = i == 3 ? alex : UUID.nameUUIDFromBytes(names[i].getBytes(StandardCharsets.UTF_8));
			five.add(new CupData.Entrant(kinds[i], id, names[i], 5 - i, 100 - i, i % 2 == 0 ? host : far));
		}
		cup.entrants.addAll(five);
		cup.bracket.addAll(Cups.bracket(five, 8));
		helper.assertTrue(cup.bracket.size() == 8 && Cups.byes(cup.bracket) == 3, "setup: the bracket");
		cup.results.add(new CupData.Result(1, five.get(3).id(), five.get(4).id()));
		cup.results.add(new CupData.Result(2, five.get(0).id(), five.get(3).id()));
		cup.champions.add(new CupData.Champion(700, AliveWorkplace.id("blossom_cup"), "Someone", far, null));
		cup.champions.add(new CupData.Champion(760, AliveWorkplace.id("little_cup"), "Mira", far, five.get(1).id()));

		CompoundTag tag = data.save(new CompoundTag(), level.registryAccess());
		CupData back = CupData.load(tag, level.registryAccess());
		CupData.Cup c = back.existing(host);
		helper.assertTrue(c != null, "the Cup is gone after a reload");
		helper.assertTrue(cup.theme.equals(c.theme) && cup.lastTheme.equals(c.lastTheme) && c.themePicked && c.day == 812 && c.closed,
			"the calendar: " + c.theme + " " + c.lastTheme + " " + c.themePicked + " " + c.day + " " + c.closed);
		helper.assertTrue(c.toldOpen == 812 && c.toldEvening == 812 && c.postponed == 2, "the told days and postponement: " + c.toldOpen + " "
			+ c.toldEvening + " " + c.postponed);
		helper.assertTrue(c.signups.equals(cup.signups), "the signed-up players: " + c.signups);
		helper.assertTrue(c.entrants.equals(cup.entrants), "the entrants: " + c.entrants);
		helper.assertTrue(c.bracket.equals(cup.bracket) && Cups.byes(c.bracket) == 3, "the bracket and its byes: " + c.bracket);
		helper.assertTrue(c.results.equals(cup.results), "the results: " + c.results);
		helper.assertTrue(c.champions.equals(cup.champions) && c.champions.get(0).id() == null && CupChampions.holder(c).id().equals(five.get(1).id()),
			"the roll of champions: " + c.champions);

		// a Cup saved with only its host
		CompoundTag bare = new CompoundTag();
		ListTag list = new ListTag();
		CompoundTag only = new CompoundTag();
		only.putLong("host", host.asLong());
		list.add(only);
		bare.put("cups", list);
		CupData.Cup empty = CupData.load(bare, level.registryAccess()).existing(host);
		helper.assertTrue(empty != null && empty.theme == null && empty.day == -1 && !empty.closed && empty.signups.isEmpty()
			&& empty.bracket.isEmpty() && empty.champions.isEmpty() && empty.noCup.isEmpty(),
			"an empty saved Cup: " + (empty == null ? null : empty.theme + " day " + empty.day + " closed " + empty.closed));
		// and a whole save with nothing in it
		helper.assertTrue(CupData.load(new CompoundTag(), level.registryAccess()).all().isEmpty(), "an empty save has Cups");
		helper.succeed();
	}

	// ---------------------------------------------------------------- a partner's hall broken mid-sign-up

	/**
	 * Spec 28.17: the circuit is "the host and every village with a hall it has a trade route with". A player signed up for
	 * Brookend; Brookend's Village Hall is broken before sign-up closes: Brookend is no longer on the circuit, its player is
	 * no entrant, and nobody can sign up for it.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaCupBroken1123c"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaCupBroken1123c")
	public void aPartnersHallBrokenMidSignUpLeavesTheCircuit(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos bAt = new BlockPos(3, 2, 3);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(bAt, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos b = helper.absolutePos(bAt);
			List<BlockPos> listed = new ArrayList<>();
			VillageHallBlockEntity entity = bareHost(helper, hall, listed);
			VillageHallBlockEntity brook = (VillageHallBlockEntity) helper.getBlockEntity(bAt);
			brook.setRank(VillageRanks.Rank.HAMLET);
			brook.setCustomName(Component.literal("Brookend"));
			Caravans.Data data = Caravans.Data.get(level);
			data.setWants(hall, Component.literal("Thornvale"), List.of());
			data.setWants(b, Component.literal("Brookend"), List.of());
			listed.add(b);
			helper.assertTrue(data.toggleRoute(b, hall, 10), "setup: Brookend's route");
			dayBefore(level, hall, entity);
			Cups.round(level, hall, entity);
			CupData.Cup cup = CupData.get(level).existing(hall);
			ServerPlayer p1 = helper.makeMockServerPlayerInLevel();
			helper.assertTrue(Cups.signUp(level, b, p1).getString().equals("You're signed up for the Cup, standing for Brookend"), "setup: p1 for Brookend");
			// the hall is broken (as a player breaks it)
			level.destroyBlock(b, true);
			helper.assertTrue(level.getBlockState(b).isAir(), "setup: Brookend's hall still stands");
			helper.assertFalse(Cups.circuit(level, hall).contains(b), "a village with no hall is still on the circuit: " + Cups.circuit(level, hall));
			List<CupData.Entrant> players = Cups.entrants(level, hall, cup).stream().filter(e -> e.kind() == CupData.Kind.PLAYER).toList();
			helper.assertTrue(players.isEmpty(), "a player standing for a village with no hall is an entrant: " + players);
			helper.assertTrue(level.getBlockState(b).is(Blocks.AIR), "the hall");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- sentences

	/**
	 * Every Cup line in en_us.json is American English (owner, 2026-10-26: color, defense, center, gray, traveling,
	 * canceled...), and the lines with numbers read right with the real numbers: the cap of 2, the minimum of 4, eight days
	 * called off, a bracket of 8 with 3 byes, a put-off day.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theCupsSentencesInAmericanEnglish(GameTestHelper helper) {
		Map<String, String> lang = new java.util.LinkedHashMap<>();
		try (InputStream in = QaFestivalCup1123cGameTests.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
			helper.assertTrue(in != null, "en_us.json isn't on the classpath");
			// read as the game reads it (a later duplicate key wins), not with a strict map
			com.google.gson.JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().entrySet()
				.forEach(e -> lang.put(e.getKey(), e.getValue().getAsString()));
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
		Pattern cupKey = Pattern.compile("(\\.cup\\b|\\.cup\\.|\\.cup_|cup\\.aliveworkplace|arena|reason\\.cup)");
		Pattern british = Pattern.compile("\\b(colour\\w*|favour\\w*|honour\\w*|centre\\w*|defence|armour\\w*|grey\\w*|travell\\w*|cancell\\w*|"
			+ "organis\\w*|realis\\w*|recognis\\w*|metre\\w*|theatre|neighbour\\w*|labour\\w*|jewell\\w*|harbour\\w*|whilst|amongst|programme)\\b",
			Pattern.CASE_INSENSITIVE);
		List<String> bad = new ArrayList<>();
		int seen = 0;
		for (Map.Entry<String, String> e : lang.entrySet()) {
			if (!cupKey.matcher(e.getKey()).find()) {
				continue;
			}
			seen++;
			Matcher m = british.matcher(e.getValue());
			if (m.find()) {
				bad.add(e.getKey() + " (" + m.group() + ")");
			}
		}
		helper.assertTrue(seen > 100, "only " + seen + " Cup lines found: the key pattern is wrong");
		helper.assertTrue(bad.isEmpty(), "British spellings in the Cup's lines: " + bad);

		String full = Component.translatable("message.aliveworkplace.cup.full", "Brookend", Cups.PLAYERS_PER_VILLAGE).getString();
		helper.assertTrue(full.equals("Brookend already has its 2 players"), full);
		String few = Component.translatable("screen.aliveworkplace.cup.why.few", Cups.MIN_ENTRANTS).getString();
		helper.assertTrue(few.equals("Fewer than 4 entrants: no Cup this time, just a plain festival"), few);
		String off = Component.translatable("message.aliveworkplace.cup.called_off", Component.translatable("cup.aliveworkplace.grand_cup"), "Thornvale", 8)
			.getString();
		helper.assertTrue(off.equals("The Grand Cup at Thornvale is called off: the village wasn't loaded for 8 days."), off);
		String putOff = Component.translatable("message.aliveworkplace.cup.put_off", Component.translatable("cup.aliveworkplace.grand_cup"), "Thornvale", 13)
			.getString();
		helper.assertTrue(putOff.equals("The Grand Cup at Thornvale is put off a day: the village isn't loaded. It's now on day 13."), putOff);
		String byes = Component.translatable("screen.aliveworkplace.cup.byes", 8, 3).getString();
		helper.assertTrue(byes.equals("A bracket of 8, with 3 byes for the top seeds"), byes);
		String pride = Component.translatable("mood.aliveworkplace.reason.cup").getString();
		helper.assertTrue(pride.equals("our village holds the Cup"), pride);
		String notYours = Component.translatable("message.aliveworkplace.cup.not_yours", "Brookend").getString();
		helper.assertTrue(notYours.equals("You may not stand for Brookend: only its owner and the owner's friends may"), notYours);
		helper.succeed();
	}
}
