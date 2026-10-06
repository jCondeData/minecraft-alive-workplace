package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonCupMatches;
import io.github.jcondedata.aliveworkplace.cup.CupBattles;
import io.github.jcondedata.aliveworkplace.cup.CupBouts;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupMatches;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.Nullable;

/**
 * ROADMAP 28.20 with the real Cobblemon: a signed-up player's bout, once they click [I'm ready] in the Arena, is a real
 * battle with only their eligible Pokémon (the rest named with the reason), a double battle for a doubles theme, at the
 * theme's level; a win moves them on and pays the purse; fleeing loses; a player who isn't ready loses by walkover after
 * two minutes; a bout between two players starts with both eligible teams. The call's text and the save: {@code CupMatchGameTests}.
 */
public class CupMatchCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** A Fire doubles theme: two each at level 50, any stage. */
	static CupThemes.Theme fireDoubles() {
		return theme("test_fire_doubles", """
			{"name": "cup.aliveworkplace.grand_cup", "order": 97, "format": "doubles", "level": 50, "bring": 2, "types": ["fire"],
			 "banned": ["legendary", "mythical", "ultra_beast", "paradox"], "rules": ["Species Clause"], "dish": "minecraft:bread",
			 "fireworks": ["#FF0000"], "disc": "minecraft:music_disc_cat"}""");
	}

	/** A singles theme of any type: three each at level 30. */
	static CupThemes.Theme openSingles() {
		return theme("test_open_singles", """
			{"name": "cup.aliveworkplace.grand_cup", "order": 96, "format": "singles", "level": 30, "bring": 3,
			 "banned": ["legendary", "mythical", "ultra_beast", "paradox"], "dish": "minecraft:bread", "fireworks": ["#FF0000"],
			 "disc": "minecraft:music_disc_cat"}""");
	}

	private static CupThemes.Theme theme(String id, String json) {
		ResourceLocation key = AliveWorkplace.id(id);
		CupThemes.Theme theme = CupThemes.read(key, JsonParser.parseString(json).getAsJsonObject());
		Map<ResourceLocation, CupThemes.Theme> all = new HashMap<>(CupThemes.loaded());
		all.put(key, theme);
		CupThemes.setForTest(all);
		return theme;
	}

	private static Villager trainer(GameTestHelper helper, BlockPos at, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(3));
		v.setCustomName(Component.literal(name));
		return v;
	}

	private static ServerPlayer player(GameTestHelper helper, String... party) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		PlayerPartyStore store = Cobblemon.INSTANCE.getStorage().getParty(p);
		for (String spec : party) {
			store.add(PokemonProperties.Companion.parse(spec, " ", "=").create());
		}
		return p;
	}

	/** A Cup at {@code host} with {@code theme} whose bracket is {@code entrants} (a bracket of four: the first bout isn't the final). */
	private static CupData.Cup cup(GameTestHelper helper, BlockPos host, CupThemes.Theme theme, CupData.Entrant... entrants) {
		CupData.Cup cup = CupData.get(helper.getLevel()).cup(host);
		cup.theme = theme.id();
		cup.day = -1; // never "today", so the Cup's day (no Arena here) leaves the call alone
		cup.closed = true;
		cup.entrants.addAll(List.of(entrants));
		cup.bracket.addAll(List.of(entrants));
		return cup;
	}

	private static List<String> species(BattleActor actor) {
		return actor.getPokemonList().stream().map(bp -> bp.getEffectedPokemon().getSpecies().getName().toLowerCase()).toList();
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

	/**
	 * Against a villager: [I'm ready] in the Arena starts a double battle at level 50 with only the eligible Fire Pokémon
	 * (Squirtle is left out for its type, Growlithe for the count), the trainer's themed team on the other side; a win
	 * moves the player on and pays 100,000; the party keeps its health.
	 */
	//$ gametest_ticks_batch AREA '400' '"cupMatchCompat"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "cupMatchCompat")
	public void aReadyPlayersBattleStartsWithOnlyEligiblePokemonAndAWinPays(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CupThemes.Theme theme = fireDoubles();
		BlockPos host = helper.absolutePos(new BlockPos(15, 2, 26));
		ServerPlayer p = player(helper, "charmander level=12", "squirtle level=20", "vulpix level=15", "growlithe level=18");
		Pokemon hurt = Cobblemon.INSTANCE.getStorage().getParty(p).get(0);
		hurt.setCurrentHealth(1);
		BlockPos ring = p.blockPosition();
		Villager mira = trainer(helper, new BlockPos(5, 2, 12), "Mira");
		CupData.Entrant me = new CupData.Entrant(CupData.Kind.PLAYER, p.getUUID(), "Player", 0, 0, host);
		CupData.Entrant her = new CupData.Entrant(CupData.Kind.HOST_TRAINER, mira.getUUID(), "Mira", 3, 0, host);
		CupData.Entrant dara = new CupData.Entrant(CupData.Kind.HOST_TRAINER, UUID.nameUUIDFromBytes("dara".getBytes()), "Dara", 3, 0, host);
		CupData.Entrant kai = new CupData.Entrant(CupData.Kind.HOST_TRAINER, UUID.nameUUIDFromBytes("kai".getBytes()), "Kai", 3, 0, host);
		CupData.Cup cup = cup(helper, host, theme, me, her, dara, kai);
		Runnable cleanup = () -> {
			PokemonBattle left = BattleRegistry.getBattleByParticipatingPlayer(p);
			if (left != null) {
				left.end();
			}
			CupData.get(level).forget(host);
		};
		helper.assertTrue(CupBattles.EXTENSION.present(), "Cobblemon didn't fill in the Cup battles");
		CupBouts.Pairing next = CupBouts.next(cup);
		helper.assertTrue(next != null && next.a().id().equals(p.getUUID()) && CupMatches.withPlayer(next), "the player's bout isn't next: " + next);
		// what the player is told before the battle
		CupBattles.Team team = CupBattles.EXTENSION.call(b -> b.team(p, theme), null);
		List<String> going = team.going().stream().map(Component::getString).toList();
		List<String> left = team.leftOut().stream().map(Component::getString).toList();
		helper.assertTrue(going.equals(List.of("Charmander", "Vulpix")), "going: " + going);
		helper.assertTrue(left.size() == 2 && left.get(0).contains("Squirtle") && left.get(0).contains("not one of the Cup's types")
			&& left.get(1).contains("Growlithe") && left.get(1).contains("only the first 2"), "left out: " + left);
		CupMatches.call(level, host, cup, next, ring, ring.west(10), ring.east(10));
		String said = CupMatches.ready(p).getString();
		helper.assertTrue(said.contains("The bout begins"), "ready: " + said);
		PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayer(p);
		helper.assertTrue(battle != null && cup.call != null && cup.call.fighting && battle.getBattleId().equals(cup.call.battle), "no battle started");
		helper.assertTrue(battle.getFormat().getBattleType().getSlotsPerActor() == 2, "not a double battle: " + battle.getFormat().getBattleType().getName());
		helper.assertTrue(battle.getFormat().getAdjustLevel() == 50, "level adjust " + battle.getFormat().getAdjustLevel());
		helper.assertTrue(battle.getFormat().getRuleSet().contains("Species Clause"), "rules " + battle.getFormat().getRuleSet());
		BattleActor mine = actorOf(battle, p.getUUID());
		helper.assertTrue(mine != null && species(mine).equals(List.of("charmander", "vulpix")), "the player's side: " + (mine == null ? null : species(mine)));
		helper.assertTrue(mine.getPokemonList().stream().allMatch(bp -> bp.getEffectedPokemon() != bp.getOriginalPokemon()
			&& bp.getEffectedPokemon().isFullHealth()), "the player's side isn't healed copies");
		BattleActor theirs = actorOf(battle, mira.getUUID());
		helper.assertTrue(theirs != null && theirs.getPokemonList().size() == 2 && theirs.getPokemonList().stream().allMatch(bp ->
			bp.getEffectedPokemon().getLevel() == 50), "Mira's side: " + (theirs == null ? null : species(theirs)));
		long before = CobbleDollarsBank.balance(p);
		CobblemonCupMatches.won(battle, Set.of(p.getUUID()));
		battle.end();
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(cup.call == null && cup.results.size() == 1 && cup.results.get(0).winner().equals(p.getUUID()), "results: " + cup.results);
			helper.assertTrue(CobbleDollarsBank.balance(p) - before == CupMatches.PURSE, "paid " + (CobbleDollarsBank.balance(p) - before));
			helper.assertTrue(hurt.getCurrentHealth() == 1, "the party's own Pokémon changed: " + hurt.getCurrentHealth());
			cleanup.run();
			helper.succeed();
		});
	}

	/** Fleeing a Cup battle loses the bout; nothing is paid. */
	//$ gametest_ticks_batch AREA '400' '"cupMatchFlee"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "cupMatchFlee")
	public void fleeingLosesTheBout(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CupThemes.Theme theme = openSingles();
		BlockPos host = helper.absolutePos(new BlockPos(15, 2, 27));
		ServerPlayer p = player(helper, "bulbasaur level=20");
		BlockPos ring = p.blockPosition();
		Villager mira = trainer(helper, new BlockPos(5, 2, 12), "Mira");
		CupData.Entrant me = new CupData.Entrant(CupData.Kind.PLAYER, p.getUUID(), "Player", 0, 0, host);
		CupData.Entrant her = new CupData.Entrant(CupData.Kind.HOST_TRAINER, mira.getUUID(), "Mira", 2, 0, host);
		CupData.Cup cup = cup(helper, host, theme, her, me);
		Runnable cleanup = () -> {
			PokemonBattle left = BattleRegistry.getBattleByParticipatingPlayer(p);
			if (left != null) {
				left.end();
			}
			CupData.get(level).forget(host);
		};
		CupMatches.call(level, host, cup, new CupBouts.Pairing(1, her, me), ring, ring.west(10), ring.east(10));
		CupMatches.ready(p);
		PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayer(p);
		helper.assertTrue(battle != null && battle.getFormat().getBattleType().getSlotsPerActor() == 1 && battle.getFormat().getAdjustLevel() == 30,
			"no singles battle at level 30");
		long before = CobbleDollarsBank.balance(p);
		CobblemonCupMatches.fled(battle, p.getUUID());
		battle.end();
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(cup.results.size() == 1 && cup.results.get(0).winner().equals(mira.getUUID()) && cup.results.get(0).loser().equals(p.getUUID()),
				"results: " + cup.results);
			helper.assertTrue(CobbleDollarsBank.balance(p) == before, "a player who fled was paid");
			cleanup.run();
			helper.succeed();
		});
	}

	/** A player who doesn't click [I'm ready] loses by walkover after two minutes, with Cobblemon too; no battle starts. */
	//$ gametest_ticks_batch AREA '2700' '"cupMatchAbsent"'
	@GameTest(template = AREA, timeoutTicks = 2700, batch = "cupMatchAbsent")
	public void anAbsentPlayerLosesByWalkover(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CupThemes.Theme theme = openSingles();
		BlockPos host = helper.absolutePos(new BlockPos(15, 2, 28));
		ServerPlayer p = player(helper, "bulbasaur level=20");
		BlockPos ring = p.blockPosition();
		Villager mira = trainer(helper, new BlockPos(5, 2, 12), "Mira");
		CupData.Entrant me = new CupData.Entrant(CupData.Kind.PLAYER, p.getUUID(), "Player", 0, 0, host);
		CupData.Entrant her = new CupData.Entrant(CupData.Kind.HOST_TRAINER, mira.getUUID(), "Mira", 2, 0, host);
		CupData.Cup cup = cup(helper, host, theme, me, her);
		Runnable cleanup = () -> CupData.get(level).forget(host);
		CupMatches.call(level, host, cup, new CupBouts.Pairing(1, me, her), ring, ring.west(10), ring.east(10));
		// the server's own ticks decide it: still open at 1:55, a walkover by 2:05
		helper.runAfterDelay(CupMatches.WINDOW - 100, () -> helper.assertTrue(cup.results.isEmpty() && cup.call != null, "decided too soon"));
		helper.runAfterDelay(CupMatches.WINDOW + 100, () -> {
			helper.assertTrue(cup.call == null && cup.results.size() == 1 && cup.results.get(0).winner().equals(mira.getUUID()), "results: " + cup.results);
			helper.assertTrue(BattleRegistry.getBattleByParticipatingPlayer(p) == null, "a battle started");
			cleanup.run();
			helper.succeed();
		});
	}

	/** A bout between two players: both ready, Cobblemon's own PvP battle with both eligible teams as healed copies. */
	//$ gametest_ticks_batch AREA '400' '"cupMatchPvp"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "cupMatchPvp")
	public void aBoutBetweenTwoPlayersStartsWithBothTeams(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CupThemes.Theme theme = openSingles();
		BlockPos host = helper.absolutePos(new BlockPos(15, 2, 29));
		ServerPlayer p = player(helper, "pikachu level=25", "mewtwo level=70", "eevee level=20");
		ServerPlayer q = player(helper, "rattata level=10", "pidgey level=12");
		BlockPos ring = p.blockPosition();
		CupData.Entrant a = new CupData.Entrant(CupData.Kind.PLAYER, p.getUUID(), "P", 0, 0, host);
		CupData.Entrant b = new CupData.Entrant(CupData.Kind.PLAYER, q.getUUID(), "Q", 0, 0, host);
		CupData.Cup cup = cup(helper, host, theme, a, b);
		Runnable cleanup = () -> {
			PokemonBattle left = BattleRegistry.getBattleByParticipatingPlayer(p);
			if (left != null) {
				left.end();
			}
			CupData.get(level).forget(host);
		};
		CupMatches.call(level, host, cup, new CupBouts.Pairing(1, a, b), ring, ring.west(10), ring.east(10));
		helper.assertTrue(CupMatches.ready(p).getString().contains("Waiting for your opponent"), "the first ready didn't wait");
		helper.assertTrue(BattleRegistry.getBattleByParticipatingPlayer(p) == null, "a battle started with one player ready");
		CupMatches.ready(q);
		PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayer(p);
		helper.assertTrue(battle != null && battle.isPvP() && battle.getBattleId().equals(cup.call.battle), "no PvP battle started");
		List<String> mine = species(actorOf(battle, p.getUUID()));
		List<String> theirs = species(actorOf(battle, q.getUUID()));
		helper.assertTrue(mine.equals(List.of("pikachu", "eevee")), "P's side (Mewtwo is legendary): " + mine);
		helper.assertTrue(theirs.equals(List.of("rattata", "pidgey")), "Q's side: " + theirs);
		helper.assertTrue(battle.getFormat().getAdjustLevel() == 30, "level adjust " + battle.getFormat().getAdjustLevel());
		helper.assertTrue(new ArrayList<>(battle.getActors().iterator().next().getPokemonList()).stream()
			.allMatch(bp -> bp.getEffectedPokemon() != bp.getOriginalPokemon()), "not copies");
		cleanup.run();
		helper.succeed();
	}
}
