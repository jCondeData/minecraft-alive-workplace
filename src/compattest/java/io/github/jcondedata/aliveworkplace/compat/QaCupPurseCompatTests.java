package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.BattleRegistry;
import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.cup.CupBouts;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupMatches;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
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
import net.minecraft.world.level.GameType;

/**
 * QA (lane d, 2026-10-27) of ROADMAP 28.1a with the real Cobblemon and CobbleDollars: "100,000 PokéDollars per bout won
 * and 500,000 for the final". Not covered before: the final's winner is really paid 600,000 into their CobbleDollars
 * balance (a two-player final won by walkover) and reads "The Cup's purse for your win: 600,000 CobbleDollars", the sum
 * with its thousands marked; the loser is paid nothing and reads no purse line.
 */
public class QaCupPurseCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** A player with {@code party} who keeps every system line they're sent. */
	private static ServerPlayer listener(GameTestHelper helper, List<Component> seen, String name, String... party) {
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
		for (String spec : party) {
			Cobblemon.INSTANCE.getStorage().getParty(player).add(PokemonProperties.Companion.parse(spec, " ", "=").create());
		}
		return player;
	}

	/** The final (two entrants) won by walkover: 600,000 CobbleDollars to the champion, the purse line with that sum; nothing to the other. */
	//$ gametest_ticks_batch AREA '200' '"qaCupPurseFinalDollars"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCupPurseFinalDollars")
	public void qaTheFinalsWinnerIsPaid600000CobbleDollars(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CupThemes.Theme theme = CupMatchCompatTests.openSingles();
		List<Component> pSeen = new ArrayList<>();
		List<Component> qSeen = new ArrayList<>();
		ServerPlayer p = listener(helper, pSeen, "qa-purse-cp", "pikachu level=25");
		ServerPlayer q = listener(helper, qSeen, "qa-purse-cq", "rattata level=10");
		BlockPos ring = helper.absolutePos(new BlockPos(15, 2, 15));
		p.teleportTo(ring.getX() + 0.5, ring.getY(), ring.getZ() + 0.5);
		q.teleportTo(ring.getX() + 0.5, ring.getY(), ring.getZ() + 3.5);
		BlockPos host = helper.absolutePos(new BlockPos(15, 2, 24));
		CupData.Cup cup = CupData.get(level).cup(host);
		cup.theme = theme.id();
		cup.day = -1;
		cup.closed = true;
		CupData.Entrant a = new CupData.Entrant(CupData.Kind.PLAYER, p.getUUID(), "qa-purse-cp", 0, 0, host);
		CupData.Entrant b = new CupData.Entrant(CupData.Kind.PLAYER, q.getUUID(), "qa-purse-cq", 0, 0, host);
		cup.entrants.addAll(List.of(a, b));
		cup.bracket.addAll(List.of(a, b));
		long pBefore = CobbleDollarsBank.balance(p);
		long qBefore = CobbleDollarsBank.balance(q);
		try {
			CupMatches.Call call = CupMatches.call(level, host, cup, new CupBouts.Pairing(1, a, b), ring, ring.west(10), ring.east(10));
			CupMatches.ready(p);
			helper.assertTrue(BattleRegistry.getBattleByParticipatingPlayer(p) == null, "a battle started with only one player ready");
			pSeen.clear();
			qSeen.clear();
			call.calledAt = level.getGameTime() - CupMatches.WINDOW;
			CupMatches.tick(level, host, cup);
			helper.assertTrue(cup.call == null && cup.results.size() == 1 && cup.results.get(0).winner().equals(p.getUUID()),
				"the ready player didn't win the final: " + cup.results);
			helper.assertTrue(CobbleDollarsBank.balance(p) - pBefore == 600_000, "the champion was paid " + (CobbleDollarsBank.balance(p) - pBefore));
			helper.assertTrue(CobbleDollarsBank.balance(q) == qBefore, "the loser was paid " + (CobbleDollarsBank.balance(q) - qBefore));
			List<String> lines = pSeen.stream().map(Component::getString).toList();
			helper.assertTrue(lines.contains("The Cup's purse for your win: 600,000 CobbleDollars"), "the champion read " + lines);
			helper.assertTrue(qSeen.stream().map(Component::getString).noneMatch(s -> s.startsWith("The Cup's purse")), "the loser read the purse line");
		} finally {
			PokemonBattle left = BattleRegistry.getBattleByParticipatingPlayer(p);
			if (left != null) {
				left.end();
			}
			CupData.get(level).forget(host);
			level.getServer().getPlayerList().remove(p);
			level.getServer().getPlayerList().remove(q);
		}
		helper.succeed();
	}
}
