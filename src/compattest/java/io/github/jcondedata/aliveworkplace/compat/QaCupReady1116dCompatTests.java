package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.BattleStartResult;
import com.cobblemon.mod.common.battles.SuccessfulBattleStart;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import io.github.jcondedata.aliveworkplace.cup.CupBouts;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupMatches;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;

/**
 * QA (lane-d-1116-1907), B1291 from the spec: a player already in a battle who clicks [I'm ready] must not read "You're
 * ready. The bout begins!" when the bout didn't start. Through the button's own command ({@code /workplace cup ready},
 * what the chat button runs) and every line each player reads: alone against a villager trainer (told once, then the
 * bout begins once they're free), and in a players' bout where either player is in a battle when the second one clicks
 * (both told once, nobody reads that it begins, nobody stays ready, and a new pair of clicks starts it).
 */
public class QaCupReady1116dCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final String CANT = "The bout couldn't start (are you already in a battle?). Click [I'm ready] again.";
	private static final String GO = "You're ready. The bout begins!";
	private static final String WAIT = "You're ready. Waiting for your opponent.";

	/** A named player who keeps every chat line they're sent (as QaCupWatchBoard1115d2CompatTests). */
	private static ServerPlayer listener(GameTestHelper helper, List<String> seen, String name, String... party) {
		ServerLevel level = helper.getLevel();
		var cookie = CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void sendSystemMessage(Component message, boolean overlay) {
				seen.add(message.getString());
				super.sendSystemMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		for (String spec : party) {
			Cobblemon.INSTANCE.getStorage().getParty(player).add(PokemonProperties.Companion.parse(spec, " ", "=").create());
		}
		return player;
	}

	/** The player clicks [I'm ready]: the command the chat button runs, as that player. */
	private static void click(ServerPlayer p) {
		p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "workplace cup ready");
	}

	private static long count(List<String> seen, String line) {
		return seen.stream().filter(line::equals).count();
	}

	private static void at(ServerPlayer p, BlockPos pos) {
		p.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
	}

	/** A wild Pokemon by {@code p} and a battle with it; returns the Pokemon (to discard). */
	private static PokemonEntity wildBattle(GameTestHelper helper, ServerPlayer p) {
		PokemonEntity wild = new PokemonEntity(helper.getLevel(), PokemonProperties.Companion.parse("caterpie level=3", " ", "=").create(), CobblemonEntities.POKEMON);
		wild.moveTo(p.getX() + 2, p.getY(), p.getZ());
		wild.setNoAi(true);
		helper.getLevel().addFreshEntity(wild);
		BattleStartResult own = BattleBuilder.INSTANCE.pve(p, wild);
		helper.assertTrue(own instanceof SuccessfulBattleStart, "setup: the wild battle didn't start: " + own);
		return wild;
	}

	private static void endBattle(ServerPlayer p) {
		PokemonBattle left = BattleRegistry.getBattleByParticipatingPlayer(p);
		if (left != null) {
			left.end();
		}
	}

	private static CupData.Cup cup(GameTestHelper helper, BlockPos host, CupThemes.Theme theme, CupData.Entrant... entrants) {
		CupData.Cup cup = CupData.get(helper.getLevel()).cup(host);
		cup.theme = theme.id();
		cup.day = -1;
		cup.closed = true;
		cup.entrants.addAll(List.of(entrants));
		cup.bracket.addAll(List.of(entrants));
		return cup;
	}

	private static CupData.Entrant villagerEntrant(String name, BlockPos host) {
		return new CupData.Entrant(CupData.Kind.HOST_TRAINER, UUID.nameUUIDFromBytes(("qa1116d-" + name).getBytes()), name, 3, 0, host);
	}

	/**
	 * Alone against Mira, in a wild battle: the click's whole chat is the "couldn't start" line, once, and not "begins";
	 * the bout isn't fought. Free of the wild battle, the next click reads "begins" once and the bout is fought.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCupReady1116dAlone"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCupReady1116dAlone")
	public void qaTheButtonInABattleSaysOnceThatItCouldntStartThenBegins(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		CupThemes.Theme theme = CupMatchCompatTests.openSingles();
		BlockPos host = helper.absolutePos(new BlockPos(15, 2, 24));
		BlockPos ring = helper.absolutePos(new BlockPos(15, 2, 12));
		List<String> seen = new ArrayList<>();
		ServerPlayer p = listener(helper, seen, "QaAsh", "bulbasaur level=20");
		at(p, ring);
		Villager mira = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 12));
		mira.setNoAi(true);
		mira.setVillagerData(mira.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(3));
		mira.setCustomName(Component.literal("Mira"));
		PokemonEntity wild = wildBattle(helper, p);
		try {
			CupData.Entrant me = new CupData.Entrant(CupData.Kind.PLAYER, p.getUUID(), "QaAsh", 0, 0, host);
			CupData.Entrant her = new CupData.Entrant(CupData.Kind.HOST_TRAINER, mira.getUUID(), "Mira", 2, 0, host);
			CupData.Cup cup = cup(helper, host, theme, me, her, villagerEntrant("Dara", host), villagerEntrant("Kai", host));
			CupMatches.call(level, host, cup, new CupBouts.Pairing(1, me, her), ring, ring.west(10), ring.east(10));
			seen.clear();
			click(p);
			helper.assertValueEqual(count(seen, CANT), 1L, "\"couldn't start\" lines after the click in a battle: " + seen);
			helper.assertValueEqual(count(seen, GO), 0L, "\"begins\" lines after the click in a battle: " + seen);
			helper.assertTrue(cup.call != null && !cup.call.fighting && !cup.call.ready[0], "the bout counts as fought, or the player as ready");
			endBattle(p);
			seen.clear();
			click(p);
			helper.assertValueEqual(count(seen, GO), 1L, "\"begins\" lines once free: " + seen);
			helper.assertValueEqual(count(seen, CANT), 0L, "\"couldn't start\" lines once free: " + seen);
			PokemonBattle bout = BattleRegistry.getBattleByParticipatingPlayer(p);
			helper.assertTrue(bout != null && cup.call.fighting && bout.getBattleId().equals(cup.call.battle), "the bout didn't start once free");
		} finally {
			endBattle(p);
			wild.discard();
			CupData.get(level).forget(host);
			level.getServer().getPlayerList().remove(p);
		}
		helper.succeed();
	}

	/**
	 * A players' bout: P clicks first and waits, then gets into a wild battle; Q's click can't start the bout. Q (who
	 * isn't in a battle) reads the "couldn't start" line once as the reply, P reads it once in chat; neither reads that
	 * it begins, neither stays ready. P free again, both click and the bout begins.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCupReady1116dFirstBusy"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCupReady1116dFirstBusy")
	public void qaTheWaitingPlayerInABattleKeepsTheBoutFromStartingAndBothAreToldOnce(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		CupThemes.Theme theme = CupMatchCompatTests.openSingles();
		BlockPos host = helper.absolutePos(new BlockPos(15, 2, 24));
		BlockPos ring = helper.absolutePos(new BlockPos(15, 2, 12));
		List<String> ps = new ArrayList<>();
		List<String> qs = new ArrayList<>();
		ServerPlayer p = listener(helper, ps, "QaPia", "pikachu level=25");
		ServerPlayer q = listener(helper, qs, "QaQuin", "rattata level=10");
		at(p, ring);
		at(q, ring.east(1));
		PokemonEntity[] wild = {null};
		try {
			CupData.Entrant a = new CupData.Entrant(CupData.Kind.PLAYER, p.getUUID(), "QaPia", 0, 0, host);
			CupData.Entrant b = new CupData.Entrant(CupData.Kind.PLAYER, q.getUUID(), "QaQuin", 0, 0, host);
			CupData.Cup cup = cup(helper, host, theme, a, b, villagerEntrant("Dara", host), villagerEntrant("Kai", host));
			CupMatches.call(level, host, cup, new CupBouts.Pairing(1, a, b), ring, ring.west(10), ring.east(10));
			ps.clear();
			qs.clear();
			click(p);
			helper.assertValueEqual(count(ps, WAIT), 1L, "P's first click: " + ps);
			wild[0] = wildBattle(helper, p);
			ps.clear();
			click(q);
			helper.assertValueEqual(count(qs, CANT), 1L, "Q's \"couldn't start\" lines: " + qs);
			helper.assertValueEqual(count(ps, CANT), 1L, "P's \"couldn't start\" lines: " + ps);
			helper.assertValueEqual(count(qs, GO) + count(ps, GO), 0L, "\"begins\" lines: Q " + qs + ", P " + ps);
			helper.assertTrue(!cup.call.fighting && !cup.call.ready[0] && !cup.call.ready[1], "a side still ready, or the bout counts as fought");
			endBattle(p);
			ps.clear();
			qs.clear();
			click(p);
			click(q);
			helper.assertValueEqual(count(qs, GO), 1L, "Q's \"begins\" once both are free: " + qs);
			PokemonBattle bout = BattleRegistry.getBattleByParticipatingPlayer(p);
			helper.assertTrue(bout != null && bout.isPvP() && cup.call.fighting && bout.getBattleId().equals(cup.call.battle),
				"the players' bout didn't start once both were free");
			helper.assertValueEqual(count(qs, CANT) + count(ps, CANT), 0L, "\"couldn't start\" once free: Q " + qs + ", P " + ps);
		} finally {
			endBattle(p);
			endBattle(q);
			if (wild[0] != null) {
				wild[0].discard();
			}
			CupData.get(level).forget(host);
			level.getServer().getPlayerList().remove(p);
			level.getServer().getPlayerList().remove(q);
		}
		helper.succeed();
	}

	/**
	 * A players' bout where the second to click is the one in a battle: through the button's command, Q reads the
	 * "couldn't start" line once and not "begins"; P, who waited, reads it once; nobody stays ready.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCupReady1116dSecondBusy"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCupReady1116dSecondBusy")
	public void qaTheSecondPlayerInABattleReadsTheLineOnceAsDoesTheFirst(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		CupThemes.Theme theme = CupMatchCompatTests.openSingles();
		BlockPos host = helper.absolutePos(new BlockPos(15, 2, 24));
		BlockPos ring = helper.absolutePos(new BlockPos(15, 2, 12));
		List<String> ps = new ArrayList<>();
		List<String> qs = new ArrayList<>();
		ServerPlayer p = listener(helper, ps, "QaPeg", "pikachu level=25");
		ServerPlayer q = listener(helper, qs, "QaQiu", "rattata level=10");
		at(p, ring);
		at(q, ring.east(1));
		PokemonEntity wild = wildBattle(helper, q);
		try {
			CupData.Entrant a = new CupData.Entrant(CupData.Kind.PLAYER, p.getUUID(), "QaPeg", 0, 0, host);
			CupData.Entrant b = new CupData.Entrant(CupData.Kind.PLAYER, q.getUUID(), "QaQiu", 0, 0, host);
			CupData.Cup cup = cup(helper, host, theme, a, b, villagerEntrant("Dara", host), villagerEntrant("Kai", host));
			CupMatches.call(level, host, cup, new CupBouts.Pairing(1, a, b), ring, ring.west(10), ring.east(10));
			click(p);
			ps.clear();
			qs.clear();
			click(q);
			helper.assertValueEqual(count(qs, CANT), 1L, "Q's \"couldn't start\" lines: " + qs);
			helper.assertValueEqual(count(qs, GO), 0L, "Q's \"begins\" lines: " + qs);
			helper.assertValueEqual(count(ps, CANT), 1L, "P's \"couldn't start\" lines: " + ps);
			helper.assertValueEqual(count(ps, GO), 0L, "P's \"begins\" lines: " + ps);
			helper.assertTrue(!cup.call.fighting && !cup.call.ready[0] && !cup.call.ready[1], "a side still ready, or the bout counts as fought");
		} finally {
			endBattle(p);
			endBattle(q);
			wild.discard();
			CupData.get(level).forget(host);
			level.getServer().getPlayerList().remove(p);
			level.getServer().getPlayerList().remove(q);
		}
		helper.succeed();
	}
}
