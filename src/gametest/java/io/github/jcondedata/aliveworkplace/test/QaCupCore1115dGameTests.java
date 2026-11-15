package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.cup.CupBout;
import io.github.jcondedata.aliveworkplace.cup.CupBouts;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupMatches;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import io.github.jcondedata.aliveworkplace.cup.Cups;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane d, 2026-11-15): the first mutation pass on the core Festival Cup code, {@code CupMatches} (ROADMAP 28.20,
 * players in the Cup) and {@code CupBouts} (28.18, bouts between villagers), the parts that need no Cobblemon. Written
 * from the spec, each test kills mutants the suite let through: [I'm ready] from the second player in the bracket counts
 * for them; a player against a villager starts on their own ready, and with nobody eligible loses (the villager goes on,
 * with the win's trainer XP); "within 24 of the ring" is the Arena; with both absent the second in the bracket loses,
 * decided by the server's own tick, and a player away from the ring reads the result too, once; a battle that vanished
 * (a restart) is called again with a fresh two minutes, and a saved running bout is still running when it loads; the
 * battle's result and fleeing are told apart, and a result for another battle or a stranger counts for nothing; the
 * bouts' hours from the theme's start to its end; a villagers' walkover in round 2 is kept as round 2 and read as a
 * walkover; the KO line names the Pokémon still standing; a trainer who wanders is put back in the box; each side's
 * Pokémon stands two fifths of the way to its box; a called bout ends when the Cup is turned off.
 */
public class QaCupCore1115dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	// ---------------------------------------------------------------- helpers

	/** A player who keeps every system and chat line they're sent (as QaCupPurseGameTests). */
	private static ServerPlayer listener(GameTestHelper helper, List<Component> seen, String name) {
		ServerLevel level = helper.getLevel();
		var cookie = CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void sendSystemMessage(Component message, boolean overlay) {
				seen.add(message);
				super.sendSystemMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().clearContent();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	private static void at(ServerPlayer p, BlockPos pos, double dx) {
		p.teleportTo(pos.getX() + 0.5 + dx, pos.getY(), pos.getZ() + 0.5);
	}

	private static CupData.Cup cup(GameTestHelper helper, BlockPos host, CupData.Entrant... entrants) {
		CupData.Cup cup = CupData.get(helper.getLevel()).cup(host);
		cup.theme = AliveWorkplace.id("grand_cup");
		cup.day = -1; // never "today": no Cup day here
		cup.closed = true;
		cup.entrants.addAll(List.of(entrants));
		cup.bracket.addAll(List.of(entrants));
		Leftovers.after(helper, () -> CupData.get(helper.getLevel()).forget(host));
		return cup;
	}

	private static CupData.Entrant player(ServerPlayer p, BlockPos village) {
		return new CupData.Entrant(CupData.Kind.PLAYER, p.getUUID(), p.getGameProfile().getName(), 0, 0, village);
	}

	private static CupData.Entrant trainer(String name, BlockPos village) {
		return new CupData.Entrant(CupData.Kind.HOST_TRAINER, UUID.nameUUIDFromBytes(("qa1115d-" + name).getBytes()), name, 3, 0, village);
	}

	/** A village's name as a line names it (the hall's while it's loaded, else made up, never loading it). */
	private static String village(ServerLevel level, BlockPos hall) {
		return (level.isLoaded(hall) ? VillageHalls.name(level, hall) : VillageHalls.madeUpName(hall)).getString();
	}

	private static List<String> strings(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	private static Villager villager(GameTestHelper helper, BlockPos at, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(2));
		v.setCustomName(Component.literal(name));
		return v;
	}

	// ---------------------------------------------------------------- [I'm ready]

	/** The second player in the bracket clicking [I'm ready] is ready on their own side; then the first's click starts the bout. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheSecondPlayersReadyCountsForTheirOwnSide(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = listener(helper, new ArrayList<>(), "qa1115d-r-p");
		ServerPlayer q = listener(helper, new ArrayList<>(), "qa1115d-r-q");
		BlockPos host = helper.absolutePos(new BlockPos(0, 1, 0));
		BlockPos ring = helper.absolutePos(new BlockPos(0, 2, 0));
		at(p, ring, 0);
		at(q, ring, 2);
		CupData.Cup cup = cup(helper, host, player(p, host), player(q, host), trainer("Mira", host), trainer("Kai", host));
		CupMatches.Call call = CupMatches.call(level, host, cup, new CupBouts.Pairing(1, cup.bracket.get(0), cup.bracket.get(1)), ring, ring.west(8), ring.east(8));
		String said = CupMatches.ready(q).getString();
		helper.assertTrue(said.equals("You're ready. Waiting for your opponent."), "the second player read: " + said);
		helper.assertTrue(call.ready[1] && !call.ready[0], "the second player's click counted for side " + (call.ready[0] ? "1 (the first's)" : "none"));
		String go = CupMatches.ready(p).getString();
		helper.assertTrue(go.equals("You're ready. The bout begins!"), "with both ready the first player read: " + go);
		helper.succeed();
	}

	/**
	 * A player against a villager: the player's own [I'm ready] starts the bout (the villager is always ready). With no
	 * Pokémon that may fight (here: none at all) the player loses: told so, and everyone at the ring reads the walkover;
	 * the villager goes on and is credited the win's trainer XP (banked, as their village is away).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAPlayerAgainstAVillagerStartsOnTheirReadyAndLosesWithNoTeam(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<Component> seen = new ArrayList<>();
		ServerPlayer p = listener(helper, seen, "qa1115d-v-p");
		BlockPos host = helper.absolutePos(new BlockPos(1, 1, 0));
		BlockPos far = host.offset(41_000, 0, 41_000);
		Leftovers.after(helper, () -> Caravans.Data.get(level).remove(far));
		BlockPos ring = helper.absolutePos(new BlockPos(1, 2, 0));
		at(p, ring, 0);
		CupData.Entrant mira = trainer("Mira-v", far);
		CupData.Cup cup = cup(helper, host, player(p, host), mira);
		CupMatches.call(level, host, cup, new CupBouts.Pairing(1, cup.bracket.get(0), mira), ring, ring.west(8), ring.east(8));
		seen.clear();
		String said = CupMatches.ready(p).getString();
		helper.assertTrue(said.equals("You're ready. The bout begins!"), "against a villager the player read: " + said);
		helper.assertTrue(cup.call == null && cup.results.size() == 1 && cup.results.get(0).winner().equals(mira.id())
			&& cup.results.get(0).loser().equals(p.getUUID()), "the player with no team didn't lose to Mira: " + cup.results);
		List<String> lines = strings(seen);
		helper.assertTrue(lines.contains("None of your Pokémon may fight in this Cup, so you lose the bout."), "no 'no team' line; read " + lines);
		String result = "Mira-v of " + village(level, far) + " goes on by walkover: qa1115d-v-p had no Pokémon that may fight";
		helper.assertTrue(lines.contains(result), "didn't read \"" + result + "\"; read " + lines);
		int banked = Caravans.Data.get(level).banked(far).getOrDefault(mira.id(), 0);
		helper.assertTrue(banked == Trainers.XP_PER_BATTLE + Trainers.XP_FOR_WIN, "Mira's banked trainer XP for the win is " + banked + ", not "
			+ (Trainers.XP_PER_BATTLE + Trainers.XP_FOR_WIN));
		helper.succeed();
	}

	/** "Inside the Arena" is within 24 blocks of the ring: [I'm ready] counts 20 blocks out, not 30. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaReadyCountsAnywhereWithin24OfTheRing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = listener(helper, new ArrayList<>(), "qa1115d-d-p");
		ServerPlayer q = listener(helper, new ArrayList<>(), "qa1115d-d-q");
		BlockPos host = helper.absolutePos(new BlockPos(2, 1, 0));
		BlockPos ring = helper.absolutePos(new BlockPos(2, 2, 0));
		at(p, ring, 30);
		CupData.Cup cup = cup(helper, host, player(p, host), player(q, host), trainer("Mira", host), trainer("Kai", host));
		CupMatches.Call call = CupMatches.call(level, host, cup, new CupBouts.Pairing(1, cup.bracket.get(0), cup.bracket.get(1)), ring, ring.west(8), ring.east(8));
		String out = CupMatches.ready(p).getString();
		helper.assertTrue(out.startsWith("Come inside the Arena of") && !call.ready[0], "30 blocks out: " + out);
		at(p, ring, 20);
		String in = CupMatches.ready(p).getString();
		helper.assertTrue(in.equals("You're ready. Waiting for your opponent.") && call.ready[0], "20 blocks out: " + in);
		helper.succeed();
	}

	// ---------------------------------------------------------------- the walkover, by the server's tick

	/**
	 * Neither player clicks [I'm ready]: two minutes on, the server's own tick decides it; the second in the bracket loses
	 * (the higher seed goes on). The player at the ring reads the result once, and so does the one away from it.
	 */
	//$ gametest_ticks_batch AREA '120' '"qaCupCore1115dAbsent"'
	@GameTest(template = AREA, timeoutTicks = 120, batch = "qaCupCore1115dAbsent")
	public void qaBothAbsentTheSecondInTheBracketLosesAndBothReadIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<Component> pSeen = new ArrayList<>();
		List<Component> qSeen = new ArrayList<>();
		ServerPlayer p = listener(helper, pSeen, "qa1115d-a-p");
		ServerPlayer q = listener(helper, qSeen, "qa1115d-a-q");
		BlockPos host = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockPos ring = helper.absolutePos(new BlockPos(8, 2, 8));
		at(p, ring, 0);
		at(q, ring, 120);
		CupData.Cup cup = cup(helper, host, player(p, host), player(q, host), trainer("Mira", host), trainer("Kai", host));
		CupMatches.Call call = CupMatches.call(level, host, cup, new CupBouts.Pairing(1, cup.bracket.get(0), cup.bracket.get(1)), ring, ring.west(8), ring.east(8));
		call.calledAt = level.getGameTime() - CupMatches.WINDOW;
		pSeen.clear();
		qSeen.clear();
		String line = "qa1115d-a-p of " + village(level, host) + " goes on by walkover: qa1115d-a-q wasn't ready in time";
		helper.succeedWhen(() -> {
			helper.assertTrue(cup.call == null, "the server's tick never decided the bout");
			helper.assertTrue(cup.results.size() == 1 && cup.results.get(0).winner().equals(p.getUUID()) && cup.results.get(0).loser().equals(q.getUUID()),
				"with both absent the first in the bracket didn't go on: " + cup.results);
			long pCount = strings(pSeen).stream().filter(line::equals).count();
			long qCount = strings(qSeen).stream().filter(line::equals).count();
			helper.assertTrue(pCount == 1, "the player at the ring read \"" + line + "\" " + pCount + " times; read " + strings(pSeen));
			helper.assertTrue(qCount == 1, "the player away from the ring read it " + qCount + " times; read " + strings(qSeen));
			level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(16), e -> e.getItem().is(Items.EMERALD)).forEach(ItemEntity::discard);
		});
	}

	// ---------------------------------------------------------------- a restart mid-battle

	/**
	 * A running bout is saved as running; after a restart its battle is gone, so the bout is called again: nobody ready,
	 * a fresh two minutes from now (no walkover on the next tick), no result, and the player reads the call again.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaABattleLostToARestartIsCalledAgain(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<Component> seen = new ArrayList<>();
		ServerPlayer p = listener(helper, seen, "qa1115d-s-p");
		BlockPos host = helper.absolutePos(new BlockPos(3, 1, 0));
		BlockPos ring = helper.absolutePos(new BlockPos(3, 2, 0));
		at(p, ring, 0);
		CupData.Entrant mira = trainer("Mira-s", host);
		CupData.Cup cup = cup(helper, host, player(p, host), mira, trainer("Kai", host), trainer("Dara", host));
		CupMatches.Call call = CupMatches.call(level, host, cup, new CupBouts.Pairing(1, cup.bracket.get(0), mira), ring, ring.west(8), ring.east(8));
		call.ready[0] = true;
		call.fighting = true;
		call.battle = UUID.randomUUID();
		call.calledAt = level.getGameTime() - CupMatches.WINDOW - 100;
		CompoundTag saved = CupData.get(level).save(new CompoundTag(), level.registryAccess());
		CupData.Cup loaded = CupData.load(saved, level.registryAccess()).existing(host);
		helper.assertTrue(loaded != null && loaded.call != null && loaded.call.fighting && loaded.call.ready[0] && loaded.call.battle == null,
			"a running bout didn't load as running (without its battle)");
		// after the restart: fighting, but no battle
		call.battle = null;
		seen.clear();
		CupMatches.tick(level, host, cup);
		helper.assertTrue(cup.call == call && cup.results.isEmpty(), "a bout whose battle vanished was decided: " + cup.results);
		helper.assertTrue(!call.fighting && !call.ready[0] && !call.ready[1], "not called afresh: fighting " + call.fighting + ", ready " + call.ready[0] + "/" + call.ready[1]);
		helper.assertTrue(call.calledAt == level.getGameTime(), "the new two minutes don't start now: called at " + call.calledAt + ", now " + level.getGameTime());
		helper.assertTrue(strings(seen).stream().anyMatch(s -> s.startsWith("Festival Cup, round 1: your bout against Mira-s of")), "the player wasn't called again: " + strings(seen));
		CupMatches.tick(level, host, cup);
		helper.assertTrue(cup.call == call && cup.results.isEmpty(), "a walkover right after the bout was called again: " + cup.results);
		helper.succeed();
	}

	// ---------------------------------------------------------------- the battle's result

	/**
	 * The battle's result: one for another battle, or naming somebody not in the bout, counts for nothing; fleeing loses
	 * and reads as fleeing; a battle won reads as a win.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaABattlesResultAndFleeingAreToldApart(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<Component> seen = new ArrayList<>();
		ServerPlayer p = listener(helper, seen, "qa1115d-f-p");
		ServerPlayer q = listener(helper, new ArrayList<>(), "qa1115d-f-q");
		BlockPos host = helper.absolutePos(new BlockPos(4, 1, 0));
		BlockPos ring = helper.absolutePos(new BlockPos(4, 2, 0));
		at(p, ring, 0);
		at(q, ring, 2);
		CupData.Cup cup = cup(helper, host, player(p, host), player(q, host), trainer("Mira", host), trainer("Kai", host));
		CupMatches.Call call = CupMatches.call(level, host, cup, new CupBouts.Pairing(1, cup.bracket.get(0), cup.bracket.get(1)), ring, ring.west(8), ring.east(8));
		UUID battle = UUID.randomUUID();
		call.fighting = true;
		call.battle = battle;
		CupMatches.decided(level.getServer(), battle, UUID.nameUUIDFromBytes("qa1115d-stranger".getBytes()), false);
		CupMatches.decided(level.getServer(), UUID.randomUUID(), q.getUUID(), false);
		helper.assertTrue(cup.call == call && cup.results.isEmpty(), "a result for a stranger or another battle counted: " + cup.results);
		seen.clear();
		CupMatches.decided(level.getServer(), battle, q.getUUID(), true);
		helper.assertTrue(cup.call == null && cup.results.size() == 1 && cup.results.get(0).winner().equals(q.getUUID()), "fleeing didn't lose: " + cup.results);
		String v = village(level, host);
		String fled = "qa1115d-f-p of " + v + " fled the battle: qa1115d-f-q of " + v + " goes on";
		helper.assertTrue(strings(seen).contains(fled), "didn't read \"" + fled + "\"; read " + strings(seen));
		// the same in a second Cup, won in battle
		BlockPos host2 = helper.absolutePos(new BlockPos(5, 1, 0));
		CupData.Cup cup2 = cup(helper, host2, player(p, host2), player(q, host2), trainer("Mira", host2), trainer("Kai", host2));
		CupMatches.Call call2 = CupMatches.call(level, host2, cup2, new CupBouts.Pairing(1, cup2.bracket.get(0), cup2.bracket.get(1)), ring, ring.west(8), ring.east(8));
		UUID battle2 = UUID.randomUUID();
		call2.fighting = true;
		call2.battle = battle2;
		seen.clear();
		CupMatches.decided(level.getServer(), battle2, p.getUUID(), false);
		String v2 = village(level, host2);
		String won = "qa1115d-f-p of " + v2 + " beat qa1115d-f-q of " + v2 + "!";
		helper.assertTrue(cup2.results.size() == 1 && cup2.results.get(0).winner().equals(p.getUUID()) && strings(seen).contains(won),
			"a battle won didn't read \"" + won + "\"; read " + strings(seen));
		helper.succeed();
	}

	// ---------------------------------------------------------------- the bouts' hours

	/** Bouts are fought on the Cup's day from the theme's start (inclusive) until its end (exclusive), on no other day. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaBoutTimeRunsFromTheThemesStartToItsEnd(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long now = level.getDayTime();
		CupData.Cup cup = new CupData.Cup();
		cup.day = now / 24000 + 1; // today
		long into = now - (cup.day - 1) * 24000; // how far into today it is
		helper.assertTrue(CupBouts.boutTime(level, cup, theme(into, into + 1)), "not bout time at the theme's start (" + into + ")");
		helper.assertTrue(!CupBouts.boutTime(level, cup, theme(into - 1, into)), "still bout time at the theme's end (" + into + ")");
		helper.assertTrue(!CupBouts.boutTime(level, cup, theme(into + 1, into + 100)), "bout time before the theme's start");
		cup.day += 1; // tomorrow's Cup
		helper.assertTrue(!CupBouts.boutTime(level, cup, theme(0, 24000)), "bout time the day before the Cup");
		cup.day -= 2; // yesterday's Cup
		helper.assertTrue(!CupBouts.boutTime(level, cup, theme(0, 24000)), "bout time the day after the Cup");
		helper.succeed();
	}

	private static CupThemes.Theme theme(long start, long end) {
		return new CupThemes.Theme(AliveWorkplace.id("qa1115d"), "cup.aliveworkplace.grand_cup", 0, "singles", 50, 3, List.of(), "", List.of(), List.of(),
			start, end, List.of(), AliveWorkplace.id("qa1115d_dish"), List.of(), AliveWorkplace.id("qa1115d_disc"));
	}

	// ---------------------------------------------------------------- bouts between villagers

	/**
	 * A round-2 bout where one trainer has no Pokémon: a walkover, kept as round 2's result, read as a walkover at the
	 * ring; the winner (away) has the win's trainer XP banked, the loser (here) gets the bout counted and trainer XP.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaCupCore1115dWalkover"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCupCore1115dWalkover")
	public void qaAVillagersWalkoverIsKeptInItsRoundAndReadAsOne(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos host = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockPos far = host.offset(42_000, 0, 42_000);
		Leftovers.after(helper, () -> Caravans.Data.get(level).remove(far));
		List<Component> seen = new ArrayList<>();
		ServerPlayer p = listener(helper, seen, "qa1115d-w-p");
		BlockPos ring = helper.absolutePos(new BlockPos(10, 2, 8));
		at(p, ring.south(3), 0);
		Villager mira = villager(helper, new BlockPos(5, 2, 8), "Mira");
		UUID dara = UUID.nameUUIDFromBytes("qa1115d-dara-away".getBytes());
		CupData.Entrant a = new CupData.Entrant(CupData.Kind.HOST_TRAINER, mira.getUUID(), "Mira", 2, 0, host);
		CupData.Entrant b = new CupData.Entrant(CupData.Kind.LEADER, dara, "Dara", 4, 300, far);
		CupData.Cup cup = cup(helper, host, a, b);
		int xpBefore = mira.getVillagerXp();
		int boutsBefore = ModAttachments.TRAINER_BATTLES.getOrElse(mira, 0);
		CupBouts.begin(level, host, cup, new CupBout(mira.getUUID(), dara, List.of(), CupBoutGameTests.fire(), 2, 7, ring, ring.west(5), ring.east(5)));
		String line = "Dara of " + village(level, far) + " beat Mira of " + village(level, host) + ", who had no Pokémon to send out";
		helper.succeedWhen(() -> {
			helper.assertTrue(cup.bout == null, "the walkover is still in the ring");
			helper.assertTrue(cup.results.size() == 1 && cup.results.get(0).round() == 2 && cup.results.get(0).winner().equals(dara)
				&& cup.results.get(0).loser().equals(mira.getUUID()), "results: " + cup.results);
			helper.assertTrue(strings(seen).contains(line), "didn't read \"" + line + "\"; read " + strings(seen));
			int banked = Caravans.Data.get(level).banked(far).getOrDefault(dara, 0);
			helper.assertTrue(banked == Trainers.XP_PER_BATTLE + Trainers.XP_FOR_WIN, "the winner's banked XP is " + banked);
			helper.assertTrue(ModAttachments.TRAINER_BATTLES.getOrElse(mira, 0) == boutsBefore + 1, "Mira's bout wasn't counted: "
				+ ModAttachments.TRAINER_BATTLES.getOrElse(mira, 0));
			helper.assertTrue(mira.getVillagerXp() > xpBefore, "Mira got no trainer XP for the bout");
		});
	}

	/** A bout won by knockout reads "... beat ...: <the winner's Pokémon still out> stands". */
	//$ gametest_ticks_batch AREA '100' '"qaCupCore1115dKo"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCupCore1115dKo")
	public void qaTheKnockoutLineNamesThePokemonStillStanding(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos host = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockPos farA = host.offset(43_000, 0, 43_000);
		BlockPos farB = host.offset(-43_000, 0, 43_000);
		Leftovers.after(helper, () -> {
			Caravans.Data.get(level).remove(farA);
			Caravans.Data.get(level).remove(farB);
		});
		List<Component> seen = new ArrayList<>();
		ServerPlayer p = listener(helper, seen, "qa1115d-k-p");
		BlockPos ring = helper.absolutePos(new BlockPos(10, 2, 8));
		at(p, ring.south(3), 0);
		UUID ida = UUID.nameUUIDFromBytes("qa1115d-ko-a".getBytes());
		UUID idb = UUID.nameUUIDFromBytes("qa1115d-ko-b".getBytes());
		CupData.Cup cup = cup(helper, host, new CupData.Entrant(CupData.Kind.LEADER, ida, "Oren", 3, 0, farA),
			new CupData.Entrant(CupData.Kind.LEADER, idb, "Vela", 3, 0, farB));
		long seed = 1;
		CupBout replay = null;
		for (; seed < 60; seed++) {
			replay = new CupBout(ida, idb, CupBoutGameTests.fire(), CupBoutGameTests.grass(), 1, seed, ring, ring.west(5), ring.east(5));
			replay.playOut();
			if (!replay.onTime) {
				break;
			}
		}
		helper.assertTrue(replay != null && !replay.onTime, "no seed under 60 ends by knockout");
		CupBout bout = new CupBout(ida, idb, CupBoutGameTests.fire(), CupBoutGameTests.grass(), 1, seed, ring, ring.west(5), ring.east(5));
		while (bout.ticks < replay.ticks - 2) {
			bout.tick(); // most of the bout already fought
		}
		CupBout done = replay;
		CupBouts.begin(level, host, cup, bout);
		String winner = done.winner == 0 ? "Oren of " + village(level, farA) : "Vela of " + village(level, farB);
		String loser = done.winner == 0 ? "Vela of " + village(level, farB) : "Oren of " + village(level, farA);
		String line = winner + " beat " + loser + ": " + Component.translatable(done.current(done.winner).nameKey()).getString() + " stands";
		helper.succeedWhen(() -> {
			helper.assertTrue(cup.bout == null, "the bout is still in the ring");
			helper.assertTrue(strings(seen).contains(line), "didn't read \"" + line + "\"; read " + strings(seen));
		});
	}

	/** A trainer who strays from their box during a bout is put back in it. */
	//$ gametest_ticks_batch AREA '100' '"qaCupCore1115dBox"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCupCore1115dBox")
	public void qaAStrayingTrainerIsPutBackInTheBox(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos host = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockPos ring = helper.absolutePos(new BlockPos(10, 2, 8));
		BlockPos boxA = ring.west(5);
		Villager mira = villager(helper, new BlockPos(5, 2, 8), "Mira");
		Villager dara = villager(helper, new BlockPos(15, 2, 8), "Dara");
		CupData.Cup cup = cup(helper, host, new CupData.Entrant(CupData.Kind.HOST_TRAINER, mira.getUUID(), "Mira", 2, 0, host),
			new CupData.Entrant(CupData.Kind.HOST_TRAINER, dara.getUUID(), "Dara", 2, 0, host));
		Leftovers.after(helper, () -> CupBouts.stop(level, host, cup));
		CupBouts.begin(level, host, cup, new CupBout(mira.getUUID(), dara.getUUID(), CupBoutGameTests.fire(), CupBoutGameTests.grass(), 1, 7, ring, boxA, ring.east(5)));
		Vec3 box = Vec3.atBottomCenterOf(boxA);
		helper.runAfterDelay(3, () -> mira.teleportTo(box.x, box.y, box.z + 4)); // 4 blocks off
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(cup.bout != null, "the bout ended before the check");
			helper.assertTrue(mira.position().distanceToSqr(box) < 1, "Mira wasn't put back in her box: " + mira.position() + ", box " + box);
			helper.succeed();
		});
	}

	/** Each side's Pokémon stands two fifths of the way from the ring's center to its trainer's box, at the ring's height. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEachSidesPokemonStandsTwoFifthsOfTheWayToItsBox(GameTestHelper helper) {
		BlockPos ring = new BlockPos(0, 64, 0);
		CupBout bout = new CupBout(UUID.randomUUID(), UUID.randomUUID(), List.of(), List.of(), 1, 1, ring, new BlockPos(10, 65, 0), new BlockPos(0, 63, -10));
		Vec3 a = CupBouts.spot(bout, 0);
		Vec3 b = CupBouts.spot(bout, 1);
		helper.assertTrue(Math.abs(a.x - 4.5) < 1e-9 && Math.abs(a.y - 64) < 1e-9 && Math.abs(a.z - 0.5) < 1e-9, "side A's spot: " + a);
		helper.assertTrue(Math.abs(b.x - 0.5) < 1e-9 && Math.abs(b.y - 64) < 1e-9 && Math.abs(b.z + 3.5) < 1e-9, "side B's spot: " + b);
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Cup turned off

	/** A called player bout ends, with no result, when {@code festivalCup} is turned off. */
	//$ gametest_ticks_batch AREA '100' '"qaCupCore1115dOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCupCore1115dOff")
	public void qaACalledBoutEndsWhenTheCupIsTurnedOff(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> Cups.ENABLED = true);
		ServerPlayer p = listener(helper, new ArrayList<>(), "qa1115d-o-p");
		BlockPos host = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockPos ring = helper.absolutePos(new BlockPos(8, 2, 8));
		at(p, ring, 0);
		CupData.Entrant mira = trainer("Mira-o", host);
		CupData.Cup cup = cup(helper, host, player(p, host), mira, trainer("Kai", host), trainer("Dara", host));
		CupMatches.call(level, host, cup, new CupBouts.Pairing(1, cup.bracket.get(0), mira), ring, ring.west(8), ring.east(8));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(cup.call != null, "the call went before the Cup was turned off");
			Cups.ENABLED = false;
		});
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(cup.call == null && cup.results.isEmpty(), "the call goes on with the Cup off: call " + cup.call + ", results " + cup.results);
			Cups.ENABLED = true;
			helper.succeed();
		});
	}
}
