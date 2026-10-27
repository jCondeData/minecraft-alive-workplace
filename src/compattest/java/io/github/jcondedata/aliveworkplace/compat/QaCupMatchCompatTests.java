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
 * QA for players in the Cup (ROADMAP 28.20) with the real Cobblemon, the parts {@code CupMatchCompatTests} leaves out: a
 * player whose party has nobody eligible clicks [I'm ready] and loses the bout (no battle, no purse); a player in a
 * running bout finds nothing to [Watch]; the winner of a bout between two players is paid and the loser isn't.
 */
public class QaCupMatchCompatTests implements FabricGameTest {
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

	/** Only a legendary in the party under a theme that bans legendaries: [I'm ready] starts no battle, the trainer goes on, nothing is paid. */
	//$ gametest_ticks_batch AREA '200' '"qaCupNoTeam"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCupNoTeam")
	public void aPlayerWithNobodyEligibleLosesTheBout(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CupThemes.Theme theme = CupMatchCompatTests.openSingles();
		BlockPos host = helper.absolutePos(new BlockPos(15, 2, 20));
		ServerPlayer p = player(helper, "mewtwo level=70");
		BlockPos ring = p.blockPosition();
		Villager mira = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 12));
		mira.setNoAi(true);
		mira.setVillagerData(mira.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(3));
		mira.setCustomName(Component.literal("Mira"));
		CupData.Entrant me = new CupData.Entrant(CupData.Kind.PLAYER, p.getUUID(), "Player", 0, 0, host);
		CupData.Entrant her = new CupData.Entrant(CupData.Kind.HOST_TRAINER, mira.getUUID(), "Mira", 2, 0, host);
		CupData.Cup cup = cup(helper, host, theme, me, her, villagerEntrant("Dara", host), villagerEntrant("Kai", host));
		long before = CobbleDollarsBank.balance(p);
		CupMatches.call(level, host, cup, new CupBouts.Pairing(1, me, her), ring, ring.west(10), ring.east(10));
		CupMatches.ready(p);
		PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayer(p);
		if (battle != null) {
			battle.end();
		}
		CupData.get(level).forget(host);
		helper.assertTrue(battle == null, "a battle started with nobody eligible");
		helper.assertTrue(cup.call == null && cup.results.size() == 1 && cup.results.get(0).winner().equals(mira.getUUID())
			&& cup.results.get(0).loser().equals(p.getUUID()), "results: " + cup.results);
		helper.assertTrue(CobbleDollarsBank.balance(p) == before, "a player with no team was paid");
		helper.succeed();
	}

	/**
	 * A bout between two players: it's the running bout, a player in it finds nothing to [Watch]; the winner is paid
	 * 100,000 (a round-1 bout of four), the loser nothing. (A third player's [Watch]: {@code QaCupWatchCompatTests} on branch tests/cup-day-1027, B563.)
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCupWatch"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCupWatch")
	public void theWinnerOfAPlayersBoutIsPaidTheLoserNot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CupThemes.Theme theme = CupMatchCompatTests.openSingles();
		BlockPos host = helper.absolutePos(new BlockPos(15, 2, 21));
		ServerPlayer p = player(helper, "pikachu level=25");
		ServerPlayer q = player(helper, "rattata level=10");
		q.teleportTo(p.getX(), p.getY(), p.getZ());
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
