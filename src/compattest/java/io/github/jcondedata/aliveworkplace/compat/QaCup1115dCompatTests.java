package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.BattleStartResult;
import com.cobblemon.mod.common.battles.SuccessfulBattleStart;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonCupBouts;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonCupMatches;
import io.github.jcondedata.aliveworkplace.cup.CupBattlers;
import io.github.jcondedata.aliveworkplace.cup.CupBattles;
import io.github.jcondedata.aliveworkplace.cup.CupBout;
import io.github.jcondedata.aliveworkplace.cup.CupBouts;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupDays;
import io.github.jcondedata.aliveworkplace.cup.CupMatches;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * QA (lane-d-1115-0408), a mutation pass over the Cobblemon side of the Festival Cup (ROADMAP 28.18, 28.20, 28.22),
 * written from the spec: who wins a battle against a villager Leader's delegate, who wins when the second player of a
 * player bout flees, what everyone is told when a player flees, a running bout stops running when its battle ends, the
 * player already in a battle can't [Watch], the fully-evolved rule on a player's party, a bout's Pokémon as fighters (stats, physical or special, status moves left
 * out, a never-missing move), a Cup Pokémon can't be battled, and only Cup Pokémon count as the ring's leftovers.
 */
public class QaCup1115dCompatTests implements FabricGameTest {
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

	/** A named player who keeps every system line they're sent (as QaCupPurseCompatTests). */
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

	private static Villager trainer(GameTestHelper helper, BlockPos at, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(3));
		v.setCustomName(Component.literal(name));
		return v;
	}

	private static CupData.Entrant villagerEntrant(String name, BlockPos host) {
		return new CupData.Entrant(CupData.Kind.HOST_TRAINER, UUID.nameUUIDFromBytes(("qa1115d-" + name).getBytes()), name, 3, 0, host);
	}

	/** A Cup at {@code host} with {@code theme} whose bracket is {@code entrants} (four: the first bout isn't the final). */
	private static CupData.Cup cup(GameTestHelper helper, BlockPos host, CupThemes.Theme theme, CupData.Entrant... entrants) {
		CupData.Cup cup = CupData.get(helper.getLevel()).cup(host);
		cup.theme = theme.id();
		cup.day = -1;
		cup.closed = true;
		cup.entrants.addAll(List.of(entrants));
		cup.bracket.addAll(List.of(entrants));
		return cup;
	}

	@Nullable
	private static BattleActor actorOf(PokemonBattle battle, UUID id) {
		for (BattleActor a : battle.getActors()) {
			if (a.getUuid().equals(id)) {
				return a;
			}
		}
		return null;
	}

	private static void endBattles(ServerPlayer... players) {
		for (ServerPlayer p : players) {
			PokemonBattle left = BattleRegistry.getBattleByParticipatingPlayer(p);
			if (left != null) {
				left.end();
			}
		}
	}

	// ------------------------------------------------------------------ who wins (CobblemonCupMatches.won / fled)

	/**
	 * A Leader at the Cup sends a delegate (28.22): the delegate villager fights the player's bout for the Leader. When the
	 * delegate's side wins the battle, the bout goes to the Leader (the entrant), the player loses and isn't paid.
	 */
	private void delegateWins(GameTestHelper helper, int z) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		CupThemes.Theme theme = CupMatchCompatTests.openSingles();
		BlockPos host = helper.absolutePos(new BlockPos(15, 2, z));
		ServerPlayer p = player(helper, "bulbasaur level=20");
		BlockPos stand = helper.absolutePos(new BlockPos(15, 2, 12));
		p.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
		BlockPos ring = p.blockPosition();
		UUID leader = UUID.nameUUIDFromBytes(("qa1115d-leader-" + z).getBytes());
		Villager dara = trainer(helper, new BlockPos(20, 2, 12), "Dara");
		ModAttachments.CUP_DELEGATE.set(dara, new CupDays.Delegate(BlockPos.ZERO, leader, 3, host, level.getDayTime() / 24000L));
		CupData.Entrant me = new CupData.Entrant(CupData.Kind.PLAYER, p.getUUID(), "Player", 0, 0, host);
		CupData.Entrant her = new CupData.Entrant(CupData.Kind.LEADER, leader, "Leader", 3, 0, host);
		CupData.Cup cup = cup(helper, host, theme, me, her, villagerEntrant("Kai" + z, host), villagerEntrant("Ren" + z, host));
		try {
			CupMatches.call(level, host, cup, new CupBouts.Pairing(1, me, her), ring, ring.west(10), ring.east(10));
			String said = CupMatches.ready(p).getString();
			PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayer(p);
			helper.assertTrue(battle != null && cup.call != null && battle.getBattleId().equals(cup.call.battle), "no battle against the delegate: " + said);
			helper.assertTrue(actorOf(battle, dara.getUUID()) != null, "the delegate isn't the one fighting");
			long before = CobbleDollarsBank.balance(p);
			CobblemonCupMatches.won(battle, Set.of(dara.getUUID()));
			battle.end();
			helper.runAfterDelay(3, () -> {
				try {
					helper.assertTrue(cup.call == null && cup.results.size() == 1, "results: " + cup.results);
					CupData.Result r = cup.results.get(0);
					helper.assertTrue(r.winner().equals(leader) && r.loser().equals(p.getUUID()),
						"the delegate won, but the result is " + (r.winner().equals(p.getUUID()) ? "the player's win" : r.toString()));
					helper.assertTrue(CobbleDollarsBank.balance(p) == before, "the player who lost was paid " + (CobbleDollarsBank.balance(p) - before));
				} finally {
					endBattles(p);
					CupData.get(level).forget(host);
				}
				helper.succeed();
			});
		} catch (RuntimeException e) {
			endBattles(p);
			CupData.get(level).forget(host);
			throw e;
		}
	}

	//$ gametest_ticks_batch AREA '200' '"qaCup1115dDelegate"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCup1115dDelegate")
	public void aDelegatesWinGoesToItsLeader(GameTestHelper helper) {
		delegateWins(helper, 26);
	}

	//$ gametest_ticks_batch AREA '200' '"qaCup1115dDelegate2"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCup1115dDelegate2")
	public void aDelegatesWinGoesToItsLeader2(GameTestHelper helper) {
		delegateWins(helper, 25);
	}

	//$ gametest_ticks_batch AREA '200' '"qaCup1115dDelegate3"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCup1115dDelegate3")
	public void aDelegatesWinGoesToItsLeader3(GameTestHelper helper) {
		delegateWins(helper, 24);
	}

	/**
	 * A bout between two players (28.20): "a win, or a flee (the one who fled loses)". The second player flees: the first
	 * player wins the bout and is paid the purse; the one who fled gets nothing.
	 */
	private void secondPlayerFlees(GameTestHelper helper, int z) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		CupThemes.Theme theme = CupMatchCompatTests.openSingles();
		BlockPos host = helper.absolutePos(new BlockPos(15, 2, z));
		ServerPlayer p = player(helper, "pikachu level=25");
		ServerPlayer q = player(helper, "rattata level=10");
		q.teleportTo(p.getX(), p.getY(), p.getZ());
		BlockPos ring = p.blockPosition();
		CupData.Entrant a = new CupData.Entrant(CupData.Kind.PLAYER, p.getUUID(), "P", 0, 0, host);
		CupData.Entrant b = new CupData.Entrant(CupData.Kind.PLAYER, q.getUUID(), "Q", 0, 0, host);
		CupData.Cup cup = cup(helper, host, theme, a, b, villagerEntrant("Dara" + z, host), villagerEntrant("Kai" + z, host));
		try {
			CupMatches.call(level, host, cup, new CupBouts.Pairing(1, a, b), ring, ring.west(10), ring.east(10));
			CupMatches.ready(p);
			CupMatches.ready(q);
			PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayer(p);
			helper.assertTrue(battle != null && battle.isPvP() && cup.call != null && battle.getBattleId().equals(cup.call.battle), "no PvP battle started");
			long pBefore = CobbleDollarsBank.balance(p);
			long qBefore = CobbleDollarsBank.balance(q);
			CobblemonCupMatches.fled(battle, q.getUUID());
			battle.end();
			helper.runAfterDelay(3, () -> {
				try {
					helper.assertTrue(cup.call == null && cup.results.size() == 1, "results: " + cup.results);
					CupData.Result r = cup.results.get(0);
					helper.assertTrue(r.winner().equals(p.getUUID()) && r.loser().equals(q.getUUID()),
						"Q fled, but " + (r.winner().equals(q.getUUID()) ? "Q won the bout" : "the result is " + r));
					helper.assertTrue(CobbleDollarsBank.balance(p) - pBefore == CupMatches.PURSE, "the winner was paid " + (CobbleDollarsBank.balance(p) - pBefore));
					helper.assertTrue(CobbleDollarsBank.balance(q) == qBefore, "the player who fled was paid");
				} finally {
					endBattles(p, q);
					CupData.get(level).forget(host);
				}
				helper.succeed();
			});
		} catch (RuntimeException e) {
			endBattles(p, q);
			CupData.get(level).forget(host);
			throw e;
		}
	}

	//$ gametest_ticks_batch AREA '200' '"qaCup1115dPvpFlee"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCup1115dPvpFlee")
	public void theSecondPlayerFleesAndTheFirstWins(GameTestHelper helper) {
		secondPlayerFlees(helper, 26);
	}

	//$ gametest_ticks_batch AREA '200' '"qaCup1115dPvpFlee2"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCup1115dPvpFlee2")
	public void theSecondPlayerFleesAndTheFirstWins2(GameTestHelper helper) {
		secondPlayerFlees(helper, 25);
	}

	//$ gametest_ticks_batch AREA '200' '"qaCup1115dPvpFlee3"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCup1115dPvpFlee3")
	public void theSecondPlayerFleesAndTheFirstWins3(GameTestHelper helper) {
		secondPlayerFlees(helper, 24);
	}

	/**
	 * A player flees a villager bout: while the battle goes on the bout is running, once the battle ended it isn't; the
	 * Arena is told the player fled ("%3$s of %4$s fled the battle: %1$s of %2$s goes on"), not that the trainer beat them.
	 */
	private void fleeIsToldAsAFlee(GameTestHelper helper, int z) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		CupThemes.Theme theme = CupMatchCompatTests.openSingles();
		BlockPos host = helper.absolutePos(new BlockPos(15, 2, z));
		List<Component> seen = new ArrayList<>();
		ServerPlayer p = listener(helper, seen, "Ash" + z, "bulbasaur level=20");
		BlockPos stand = helper.absolutePos(new BlockPos(15, 2, 12));
		p.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
		BlockPos ring = p.blockPosition();
		Villager mira = trainer(helper, new BlockPos(5, 2, 12), "Mira");
		CupData.Entrant me = new CupData.Entrant(CupData.Kind.PLAYER, p.getUUID(), "Ash" + z, 0, 0, host);
		CupData.Entrant her = new CupData.Entrant(CupData.Kind.HOST_TRAINER, mira.getUUID(), "Mira", 2, 0, host);
		CupData.Cup cup = cup(helper, host, theme, me, her, villagerEntrant("Kai" + z, host), villagerEntrant("Ren" + z, host));
		Runnable cleanup = () -> {
			endBattles(p);
			CupData.get(level).forget(host);
			level.getServer().getPlayerList().remove(p);
		};
		try {
			CupMatches.call(level, host, cup, new CupBouts.Pairing(1, me, her), ring, ring.west(10), ring.east(10));
			CupMatches.ready(p);
			PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayer(p);
			helper.assertTrue(battle != null && cup.call != null && battle.getBattleId().equals(cup.call.battle), "no battle started");
			UUID id = battle.getBattleId();
			helper.assertTrue(CupBattles.EXTENSION.call(b -> b.running(id), false), "the battle isn't running");
			seen.clear();
			CobblemonCupMatches.fled(battle, p.getUUID());
			battle.end();
			helper.assertTrue(!CupBattles.EXTENSION.call(b -> b.running(id), true), "the battle ended but still counts as running");
			helper.runAfterDelay(3, () -> {
				try {
					helper.assertTrue(cup.results.size() == 1 && cup.results.get(0).winner().equals(mira.getUUID()), "results: " + cup.results);
					List<String> lines = seen.stream().map(Component::getString).toList();
					helper.assertTrue(lines.stream().anyMatch(l -> l.startsWith("Ash" + z + " of ") && l.contains(" fled the battle: Mira of ") && l.endsWith(" goes on")),
						"the Arena wasn't told Ash fled: " + lines);
					helper.assertTrue(lines.stream().noneMatch(l -> l.contains(" beat ")), "told as a win in battle: " + lines);
				} finally {
					cleanup.run();
				}
				helper.succeed();
			});
		} catch (RuntimeException e) {
			cleanup.run();
			throw e;
		}
	}

	//$ gametest_ticks_batch AREA '200' '"qaCup1115dFleeTold"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCup1115dFleeTold")
	public void aFleeIsToldAsAFlee(GameTestHelper helper) {
		fleeIsToldAsAFlee(helper, 26);
	}

	//$ gametest_ticks_batch AREA '200' '"qaCup1115dFleeTold2"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCup1115dFleeTold2")
	public void aFleeIsToldAsAFlee2(GameTestHelper helper) {
		fleeIsToldAsAFlee(helper, 25);
	}

	//$ gametest_ticks_batch AREA '200' '"qaCup1115dFleeTold3"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCup1115dFleeTold3")
	public void aFleeIsToldAsAFlee3(GameTestHelper helper) {
		fleeIsToldAsAFlee(helper, 24);
	}

	/**
	 * [Watch] (28.20) puts a player in Cobblemon's spectator view of a running player bout, but a player already in a
	 * battle of their own (here a wild one) can't watch: they're told "You can't watch that bout right now." and aren't
	 * one of the bout's spectators.
	 */
	private void busyPlayerCantWatch(GameTestHelper helper, int z) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		CupThemes.Theme theme = CupMatchCompatTests.openSingles();
		BlockPos host = helper.absolutePos(new BlockPos(15, 2, z));
		ServerPlayer p = player(helper, "pikachu level=25");
		ServerPlayer q = player(helper, "rattata level=10");
		ServerPlayer r = player(helper, "pidgey level=30");
		q.teleportTo(p.getX(), p.getY(), p.getZ());
		r.teleportTo(p.getX() + 3, p.getY(), p.getZ());
		BlockPos ring = p.blockPosition();
		PokemonEntity wild = new PokemonEntity(level, PokemonProperties.Companion.parse("caterpie level=3", " ", "=").create(), CobblemonEntities.POKEMON);
		wild.moveTo(r.getX() + 2, r.getY(), r.getZ());
		wild.setNoAi(true);
		level.addFreshEntity(wild);
		CupData.Entrant a = new CupData.Entrant(CupData.Kind.PLAYER, p.getUUID(), "P", 0, 0, host);
		CupData.Entrant b = new CupData.Entrant(CupData.Kind.PLAYER, q.getUUID(), "Q", 0, 0, host);
		CupData.Cup cup = cup(helper, host, theme, a, b, villagerEntrant("Dara" + z, host), villagerEntrant("Kai" + z, host));
		try {
			CupMatches.call(level, host, cup, new CupBouts.Pairing(1, a, b), ring, ring.west(10), ring.east(10));
			CupMatches.ready(p);
			CupMatches.ready(q);
			PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayer(p);
			helper.assertTrue(battle != null && battle.getBattleId().equals(CupMatches.running(level, host)), "no player bout running");
			BattleStartResult own = BattleBuilder.INSTANCE.pve(r, wild);
			helper.assertTrue(own instanceof SuccessfulBattleStart && BattleRegistry.getBattleByParticipatingPlayer(r) != null, "R's wild battle didn't start: " + own);
			String said = CupMatches.watch(r).getString();
			helper.assertTrue(said.equals("You can't watch that bout right now."), "a player in a battle of their own: " + said);
			helper.assertTrue(!battle.getSpectators().contains(r.getUUID()), "a player in a battle of their own became a spectator");
		} finally {
			endBattles(p, r);
			wild.discard();
			CupData.get(level).forget(host);
		}
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '100' '"qaCup1115dBusyWatch"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCup1115dBusyWatch")
	public void aPlayerInABattleCantWatch(GameTestHelper helper) {
		busyPlayerCantWatch(helper, 26);
	}

	//$ gametest_ticks_batch AREA '100' '"qaCup1115dBusyWatch2"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCup1115dBusyWatch2")
	public void aPlayerInABattleCantWatch2(GameTestHelper helper) {
		busyPlayerCantWatch(helper, 25);
	}

	//$ gametest_ticks_batch AREA '100' '"qaCup1115dBusyWatch3"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCup1115dBusyWatch3")
	public void aPlayerInABattleCantWatch3(GameTestHelper helper) {
		busyPlayerCantWatch(helper, 24);
	}

	// ------------------------------------------------------------------ the player's team (CobblemonCupMatches.eligible)

	/** A fully-evolved theme: Charizard and Arcanine go; Charmander stays home, told "only fully evolved Pokémon may come". */
	private void onlyFullyEvolvedGoUnderAFinalStageThemeBody(GameTestHelper helper) {
		CupThemes.Theme t = CupBoutCompatTests.fireTheme();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		for (String spec : List.of("charmander", "charizard", "arcanine")) {
			party.add(PokemonProperties.Companion.parse(spec + " level=30", " ", "=").create());
		}
		List<Component> leftOut = new ArrayList<>();
		List<String> going = CobblemonCupMatches.eligible(party, t, leftOut).stream().map(p -> p.getSpecies().getName()).toList();
		List<String> why = leftOut.stream().map(Component::getString).toList();
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.assertTrue(going.equals(List.of("Charizard", "Arcanine")), "going " + going);
		helper.assertTrue(why.equals(List.of("Charmander: only fully evolved Pokémon may come")), "left out " + why);
		helper.succeed();
	}

	// ------------------------------------------------------------------ the bouts' Pokémon (CobblemonCupBouts)

	/**
	 * A Pokémon as a bout's fighter (28.18: "its species, name, types, stats at its level and its attacking moves"):
	 * Charmander's own stats in their places; Scratch physical, Ember special, Swift never misses (0); Growl, a status
	 * move, isn't one of its moves.
	 */
	private void aFighterKeepsItsStatsAndMoveSidesBody(GameTestHelper helper) {
		Pokemon p = PokemonProperties.Companion.parse("charmander level=40", " ", "=").create();
		String[] moves = {"scratch", "growl", "ember", "swift"};
		for (int i = 0; i < moves.length; i++) {
			p.getMoveSet().setMove(i, Moves.getByName(moves[i]).create());
		}
		CupBout.Fighter f = CobblemonCupBouts.fighter(p);
		helper.assertTrue(f.species().equals("cobblemon:charmander") && f.types().equals(List.of("fire")) && f.level() == 40, "fighter " + f);
		helper.assertTrue(f.hp() == p.getMaxHealth() && f.attack() == p.getAttack() && f.defence() == p.getDefence() && f.spAttack() == p.getSpecialAttack()
			&& f.spDefence() == p.getSpecialDefence() && f.speed() == p.getSpeed(),
			"stats " + List.of(f.hp(), f.attack(), f.defence(), f.spAttack(), f.spDefence(), f.speed()) + " for "
				+ List.of(p.getMaxHealth(), p.getAttack(), p.getDefence(), p.getSpecialAttack(), p.getSpecialDefence(), p.getSpeed()));
		helper.assertTrue(f.moves().size() == 3, "moves " + f.moves());
		CupBout.Move scratch = f.moves().get(0);
		CupBout.Move ember = f.moves().get(1);
		CupBout.Move swift = f.moves().get(2);
		helper.assertTrue(scratch.id().equals("scratch") && scratch.type().equals("normal") && scratch.power() == 40 && scratch.accuracy() == 100 && scratch.physical(),
			"scratch " + scratch);
		helper.assertTrue(ember.id().equals("ember") && ember.type().equals("fire") && ember.power() == 40 && !ember.physical(), "ember " + ember);
		helper.assertTrue(swift.id().equals("swift") && swift.accuracy() == 0 && !swift.physical(), "swift " + swift);
		helper.succeed();
	}

	/** A Cup Pokémon at the ring can't be battled ("can't be caught, hurt or battled"): a player's wild battle with it doesn't start. */
	private void aCupPokemonCantBeBattledBody(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer p = player(helper, "bulbasaur level=30");
		Vec3 at = helper.absoluteVec(new Vec3(10.5, 2, 10.5));
		p.teleportTo(at.x + 2, at.y, at.z);
		CupBout.Fighter f = CobblemonCupBouts.fighter(PokemonProperties.Companion.parse("charmander level=20", " ", "=").create());
		Entity e = CupBattlers.EXTENSION.call(b -> b.sendOut(level, f, at, p.position()), null);
		try {
			helper.assertTrue(e instanceof PokemonEntity, "nothing sent out: " + e);
			BattleStartResult result = BattleBuilder.INSTANCE.pve(p, (PokemonEntity) e);
			helper.assertTrue(!(result instanceof SuccessfulBattleStart) && BattleRegistry.getBattleByParticipatingPlayer(p) == null,
				"a battle with a Cup Pokémon started: " + result);
		} finally {
			endBattles(p);
			if (e != null) {
				e.discard();
			}
		}
		helper.succeed();
	}

	/** The ring's leftovers (a bout's end clears what it sent out) are Cup Pokémon only: a wild Pokémon by the ring isn't one. */
	private void onlyCupPokemonAreTheRingsLeftoversBody(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		Vec3 at = helper.absoluteVec(new Vec3(10.5, 2, 10.5));
		CupBout.Fighter f = CobblemonCupBouts.fighter(PokemonProperties.Companion.parse("charmander level=20", " ", "=").create());
		Entity cupOne = CupBattlers.EXTENSION.call(b -> b.sendOut(level, f, at, at.add(0, 0, 1)), null);
		PokemonEntity wild = new PokemonEntity(level, PokemonProperties.Companion.parse("pidgey level=5", " ", "=").create(), CobblemonEntities.POKEMON);
		wild.moveTo(at.x + 3, at.y, at.z);
		wild.setNoAi(true);
		level.addFreshEntity(wild);
		try {
			helper.assertTrue(cupOne instanceof PokemonEntity && wild.isAlive(), "setup: " + cupOne + ", wild alive " + wild.isAlive());
			List<Entity> found = CupBattlers.EXTENSION.call(b -> b.inBox(level, new AABB(BlockPos.containing(at)).inflate(8)), List.<Entity>of());
			helper.assertTrue(found.contains(cupOne), "the Cup Pokémon isn't found: " + found);
			helper.assertTrue(!found.contains(wild), "a wild Pokémon counts as the ring's leftover: " + found);
		} finally {
			if (cupOne != null) {
				cupOne.discard();
			}
			wild.discard();
		}
		helper.succeed();
	}

	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaCup1115dPure"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaCup1115dPure")
	public void onlyFullyEvolvedGoUnderAFinalStageTheme(GameTestHelper helper) {
		onlyFullyEvolvedGoUnderAFinalStageThemeBody(helper);
	}

	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaCup1115dPure2"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaCup1115dPure2")
	public void onlyFullyEvolvedGoUnderAFinalStageTheme2(GameTestHelper helper) {
		onlyFullyEvolvedGoUnderAFinalStageThemeBody(helper);
	}

	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaCup1115dPure3"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaCup1115dPure3")
	public void onlyFullyEvolvedGoUnderAFinalStageTheme3(GameTestHelper helper) {
		onlyFullyEvolvedGoUnderAFinalStageThemeBody(helper);
	}

	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaCup1115dPure"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaCup1115dPure")
	public void aFighterKeepsItsStatsAndMoveSides(GameTestHelper helper) {
		aFighterKeepsItsStatsAndMoveSidesBody(helper);
	}

	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaCup1115dPure2"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaCup1115dPure2")
	public void aFighterKeepsItsStatsAndMoveSides2(GameTestHelper helper) {
		aFighterKeepsItsStatsAndMoveSidesBody(helper);
	}

	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaCup1115dPure3"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaCup1115dPure3")
	public void aFighterKeepsItsStatsAndMoveSides3(GameTestHelper helper) {
		aFighterKeepsItsStatsAndMoveSidesBody(helper);
	}

	//$ gametest_ticks_batch AREA '100' '"qaCup1115dNoBattle"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCup1115dNoBattle")
	public void aCupPokemonCantBeBattled(GameTestHelper helper) {
		aCupPokemonCantBeBattledBody(helper);
	}

	//$ gametest_ticks_batch AREA '100' '"qaCup1115dNoBattle2"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCup1115dNoBattle2")
	public void aCupPokemonCantBeBattled2(GameTestHelper helper) {
		aCupPokemonCantBeBattledBody(helper);
	}

	//$ gametest_ticks_batch AREA '100' '"qaCup1115dNoBattle3"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCup1115dNoBattle3")
	public void aCupPokemonCantBeBattled3(GameTestHelper helper) {
		aCupPokemonCantBeBattledBody(helper);
	}

	//$ gametest_ticks_batch AREA '100' '"qaCup1115dInBox"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCup1115dInBox")
	public void onlyCupPokemonAreTheRingsLeftovers(GameTestHelper helper) {
		onlyCupPokemonAreTheRingsLeftoversBody(helper);
	}

	//$ gametest_ticks_batch AREA '100' '"qaCup1115dInBox2"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCup1115dInBox2")
	public void onlyCupPokemonAreTheRingsLeftovers2(GameTestHelper helper) {
		onlyCupPokemonAreTheRingsLeftoversBody(helper);
	}

	//$ gametest_ticks_batch AREA '100' '"qaCup1115dInBox3"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCup1115dInBox3")
	public void onlyCupPokemonAreTheRingsLeftovers3(GameTestHelper helper) {
		onlyCupPokemonAreTheRingsLeftoversBody(helper);
	}
}
