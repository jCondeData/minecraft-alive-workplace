package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.battles.BattleRegistry;
import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.cup.CupBouts;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupMatches;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;

/**
 * Bug test (28.20): [Watch] never works. {@code CobblemonCupMatches.watch} calls Cobblemon's
 * {@code SpectateBattleHandler.spectateBattle(target, player)} with the arguments swapped (spectator first), so Cobblemon
 * answers "player not in battle" and the player reads "You can't watch that bout right now."
 */
public class QaCupWatchCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	private static ServerPlayer player(GameTestHelper helper, String... party) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		PlayerPartyStore store = Cobblemon.INSTANCE.getStorage().getParty(p);
		for (String spec : party) {
			store.add(PokemonProperties.Companion.parse(spec, " ", "=").create());
		}
		return p;
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
		return new CupData.Entrant(CupData.Kind.HOST_TRAINER, UUID.nameUUIDFromBytes(("qa" + name).getBytes()), name, 3, 0, host);
	}

	/**
	 * A bout between two players: a third player's [Watch] puts them in the battle's spectators (ROADMAP 28.20: "[Watch]
	 * ... puts a player in Cobblemon's spectator view of a running player bout"), a player in the bout finds nothing to
	 * watch; the winner is paid 100,000 (a round-1 bout of four), the loser nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCupWatchBug"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCupWatchBug")
	public void aThirdPlayerWatchesAndTheWinnerIsPaid(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CupThemes.Theme theme = CupMatchCompatTests.openSingles();
		BlockPos host = helper.absolutePos(new BlockPos(15, 2, 21));
		ServerPlayer p = player(helper, "pikachu level=25");
		ServerPlayer q = player(helper, "rattata level=10");
		ServerPlayer r = player(helper, "pidgey level=10");
		q.teleportTo(p.getX(), p.getY(), p.getZ());
		r.teleportTo(p.getX() + 3, p.getY(), p.getZ());
		BlockPos ring = p.blockPosition();
		CupData.Entrant a = new CupData.Entrant(CupData.Kind.PLAYER, p.getUUID(), "P", 0, 0, host);
		CupData.Entrant b = new CupData.Entrant(CupData.Kind.PLAYER, q.getUUID(), "Q", 0, 0, host);
		CupData.Cup cup = cup(helper, host, theme, a, b, villagerEntrant("Dara", host), villagerEntrant("Kai", host));
		CupMatches.call(level, host, cup, new CupBouts.Pairing(1, a, b), ring, ring.west(10), ring.east(10));
		CupMatches.ready(p);
		CupMatches.ready(q);
		PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayer(p);
		try {
			helper.assertTrue(battle != null && cup.call != null && cup.call.fighting, "no PvP battle started");
			helper.assertTrue(battle.getBattleId().equals(CupMatches.running(level, host)), "the bout isn't running");
			String self = CupMatches.watch(p).getString();
			helper.assertTrue(self.equals("No player bout is being fought right now."), "a player in the bout watched: " + self);
			String watched = CupMatches.watch(r).getString();
			helper.assertTrue(watched.equals("You're watching the bout."), "watch: " + watched);
			helper.assertTrue(battle.getSpectators().contains(r.getUUID()), "the third player isn't a spectator: " + battle.getSpectators());
			long pBefore = CobbleDollarsBank.balance(p);
			long qBefore = CobbleDollarsBank.balance(q);
			CupMatches.decided(level.getServer(), battle.getBattleId(), q.getUUID(), false);
			helper.assertTrue(cup.call == null && cup.results.size() == 1 && cup.results.get(0).winner().equals(q.getUUID()), "results: " + cup.results);
			helper.assertTrue(CobbleDollarsBank.balance(q) - qBefore == CupMatches.PURSE, "the winner was paid " + (CobbleDollarsBank.balance(q) - qBefore));
			helper.assertTrue(CobbleDollarsBank.balance(p) == pBefore, "the loser was paid");
		} finally {
			PokemonBattle left = BattleRegistry.getBattleByParticipatingPlayer(p);
			if (left != null) {
				left.end();
			}
			CupData.get(level).forget(host);
		}
		helper.succeed();
	}
}
