package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.cup.CupBattles;
import io.github.jcondedata.aliveworkplace.cup.CupBouts;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupMatches;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Players in the Festival Cup (ROADMAP 28.20), the parts that need no Cobblemon: without it a player's bout is never
 * called (it's settled at the theme's end as before); the call's text and its [I'm ready] button; [I'm ready] only
 * counts inside the Arena; an absent or offline player loses by walkover after two minutes, not before; a called bout
 * survives a save; the purse. The real battles: {@code CupMatchCompatTests}.
 */
public class CupMatchGameTests implements FabricGameTest {

	private static CupData.Cup cup(GameTestHelper helper, BlockPos host, CupData.Entrant... entrants) {
		CupData.Cup cup = CupData.get(helper.getLevel()).cup(host);
		cup.theme = AliveWorkplace.id("grand_cup");
		cup.day = -1; // never "today", so the Cup's day (no Arena here) leaves the call alone
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
		return new CupData.Entrant(CupData.Kind.HOST_TRAINER, UUID.nameUUIDFromBytes(name.getBytes()), name, 3, 0, village);
	}

	/** Without Cobblemon a bout with a player is never called: the next bout is the villagers' (or none). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void withoutCobblemonAPlayersBoutIsNotCalled(GameTestHelper helper) {
		helper.assertFalse(CupBattles.EXTENSION.present(), "this run has a Cup battles filling");
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		BlockPos host = helper.absolutePos(new BlockPos(0, 1, 0));
		CupData.Entrant mira = trainer("Mira", host);
		CupData.Entrant dara = trainer("Dara", host);
		CupData.Entrant kai = trainer("Kai", host);
		CupData.Cup cup = cup(helper, host, player(p, host), mira, dara, kai);
		CupBouts.Pairing next = CupBouts.next(cup);
		helper.assertTrue(next != null && next.a().id().equals(dara.id()) && next.b().id().equals(kai.id()), "next: " + next);
		helper.assertTrue(cup.call == null, "a call went out");
		helper.succeed();
	}

	/** The call: the round, the opponent and their village, where, two minutes, and a clickable [I'm ready] that runs the command. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theCallSaysWhoWhereAndHasAReadyButton(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		BlockPos host = helper.absolutePos(new BlockPos(1, 1, 0));
		CupData.Entrant mira = trainer("Mira", host);
		CupData.Cup cup = cup(helper, host, player(p, host), mira);
		BlockPos ring = p.blockPosition();
		CupMatches.Call call = CupMatches.call(level, host, cup, new CupBouts.Pairing(1, cup.bracket.get(0), mira), ring, ring.west(8), ring.east(8));
		helper.assertTrue(cup.call == call && !call.fighting && call.calledAt == level.getGameTime(), "the call wasn't kept");
		Component text = CupMatches.callText(level, host, call, mira);
		String s = text.getString();
		helper.assertTrue(s.contains("round 1") && s.contains("Mira") && s.contains("2 minutes") && s.contains("[I'm ready]")
			&& s.contains(ring.getX() + ", " + ring.getY() + ", " + ring.getZ()), "the call reads: " + s);
		boolean button = text.toFlatList().stream().anyMatch(c -> c.getString().equals("[I'm ready]") && c.getStyle().getClickEvent() != null
			&& c.getStyle().getClickEvent().getAction() == ClickEvent.Action.RUN_COMMAND
			&& c.getStyle().getClickEvent().getValue().equals("/workplace cup ready"));
		helper.assertTrue(button, "no clickable [I'm ready]");
		String watch = CupMatches.watchButton().getString();
		helper.assertTrue(watch.equals("[Watch]") && CupMatches.watchButton().getStyle().getClickEvent().getValue().equals("/workplace cup watch"), "watch: " + watch);
		helper.succeed();
	}

	/** [I'm ready] only counts inside the Arena; then the player waits for the other player. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void readyOnlyCountsInsideTheArena(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		ServerPlayer q = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		BlockPos host = helper.absolutePos(new BlockPos(2, 1, 0));
		CupData.Cup cup = cup(helper, host, player(p, host), player(q, host));
		BlockPos far = p.blockPosition().offset(200, 0, 0);
		CupMatches.call(level, host, cup, new CupBouts.Pairing(1, cup.bracket.get(0), cup.bracket.get(1)), far, far.west(8), far.east(8));
		String away = CupMatches.ready(p).getString();
		helper.assertTrue(away.contains("Come inside the Arena") && !cup.call.ready[0], "from far away: " + away);
		cup.call = null;
		BlockPos ring = p.blockPosition();
		CupMatches.call(level, host, cup, new CupBouts.Pairing(1, cup.bracket.get(0), cup.bracket.get(1)), ring, ring.west(8), ring.east(8));
		String here = CupMatches.ready(p).getString();
		helper.assertTrue(here.contains("Waiting for your opponent") && cup.call.ready[0] && !cup.call.ready[1] && !cup.call.fighting, "inside: " + here);
		helper.succeed();
	}

	/** A player who isn't ready loses by walkover once two minutes are up, not before; one who's offline too. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anAbsentPlayerLosesByWalkoverAfterTwoMinutes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		BlockPos host = helper.absolutePos(new BlockPos(3, 1, 0));
		CupData.Entrant mira = trainer("Mira", host);
		CupData.Cup cup = cup(helper, host, player(p, host), mira);
		BlockPos ring = p.blockPosition().offset(100, 0, 0);
		CupMatches.Call call = CupMatches.call(level, host, cup, new CupBouts.Pairing(1, cup.bracket.get(0), mira), ring, ring.west(8), ring.east(8));
		call.calledAt = level.getGameTime() - CupMatches.WINDOW + 40;
		CupMatches.tick(level, host, cup);
		helper.assertTrue(cup.call != null && cup.results.isEmpty(), "decided before two minutes were up");
		call.calledAt = level.getGameTime() - CupMatches.WINDOW;
		CupMatches.tick(level, host, cup);
		helper.assertTrue(cup.call == null && cup.results.size() == 1 && cup.results.get(0).winner().equals(mira.id())
			&& cup.results.get(0).loser().equals(p.getUUID()), "results: " + cup.results);
		helper.assertTrue(CupBouts.champion(cup) != null && CupBouts.champion(cup).id().equals(mira.id()), "Mira didn't go on");
		// a player who isn't online at all
		BlockPos host2 = helper.absolutePos(new BlockPos(4, 1, 0));
		CupData.Entrant ghost = new CupData.Entrant(CupData.Kind.PLAYER, UUID.nameUUIDFromBytes("offline".getBytes()), "Gone", 0, 0, host2);
		CupData.Entrant dara = trainer("Dara", host2);
		CupData.Cup cup2 = cup(helper, host2, dara, ghost);
		CupMatches.Call call2 = CupMatches.call(level, host2, cup2, new CupBouts.Pairing(1, dara, ghost), ring, ring.west(8), ring.east(8));
		call2.calledAt = level.getGameTime() - CupMatches.WINDOW;
		CupMatches.tick(level, host2, cup2);
		helper.assertTrue(cup2.results.size() == 1 && cup2.results.get(0).winner().equals(dara.id()), "offline: " + cup2.results);
		helper.succeed();
	}

	/** A called bout survives a save (who's ready, when it was called), and an old save without one loads with none. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aCalledBoutIsSaved(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		BlockPos host = helper.absolutePos(new BlockPos(5, 1, 0));
		CupData.Entrant mira = trainer("Mira", host);
		CupData.Cup cup = cup(helper, host, player(p, host), mira);
		BlockPos ring = p.blockPosition();
		CupMatches.Call call = CupMatches.call(level, host, cup, new CupBouts.Pairing(1, cup.bracket.get(0), mira), ring, ring.west(8), ring.east(8));
		call.ready[0] = true;
		HolderLookup.Provider registries = level.registryAccess();
		CompoundTag saved = CupData.get(level).save(new CompoundTag(), registries);
		CupData.Cup loaded = CupData.load(saved, registries).existing(host);
		helper.assertTrue(loaded != null && loaded.call != null && loaded.call.ready[0] && !loaded.call.ready[1] && loaded.call.round == 1
			&& loaded.call.calledAt == call.calledAt && loaded.call.ring.equals(ring) && loaded.call.sides[1].equals(mira.id()), "the call wasn't kept");
		helper.assertTrue(CupMatches.Call.load(new CompoundTag()) == null, "an empty call loaded");
		helper.succeed();
	}

	/** The purse: 100,000 for a bout won, 500,000 more for the final. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePursePaysMoreForTheFinal(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		BlockPos host = helper.absolutePos(new BlockPos(6, 1, 0));
		CupData.Entrant mira = trainer("Mira", host);
		CupData.Entrant dara = trainer("Dara", host);
		CupData.Entrant kai = trainer("Kai", host);
		CupData.Cup cup = cup(helper, host, player(p, host), mira, dara, kai);
		cup.results.add(new CupData.Result(1, p.getUUID(), mira.id()));
		helper.assertTrue(CupMatches.purse(level, host, cup, p.getUUID()) == 100_000, "a bout: " + CupMatches.purse(level, host, cup, p.getUUID()));
		cup.results.add(new CupData.Result(1, dara.id(), kai.id()));
		cup.results.add(new CupData.Result(2, p.getUUID(), dara.id()));
		helper.assertTrue(CupMatches.purse(level, host, cup, p.getUUID()) == 600_000, "the final: " + CupMatches.purse(level, host, cup, p.getUUID()));
		helper.succeed();
	}
}
