//? if cobblemon {
package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.BattleStartResult;
import com.cobblemon.mod.common.battles.SuccessfulBattleStart;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.ai.RandomBattleAI;
import com.cobblemon.mod.common.battles.ai.StrongBattleAI;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.net.serverhandling.battle.SpectateBattleHandler;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import io.github.jcondedata.aliveworkplace.cup.CupBattles;
import io.github.jcondedata.aliveworkplace.cup.CupMatches;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

// The real battles of the Festival Cup's player bouts (ROADMAP 28.20). A player's team is their party's first eligible
// Pokémon (the theme's labels, stage and types) up to the theme's count; the rest are named with the reason. Against a
// villager: the trainer as VillagerTrainerActor with their themed team; against a player: BattleBuilder.pvp1v1. Both sides
// fight with healed copies (Cobblemon gives a copy no experience, and the party keeps its health), in the theme's format,
// at its level (Cobblemon's level adjust; RCT's caps aren't applied here) and with its Showdown rules. No ordinary
// challenge's limits apply. A win, or a flee (the one who fled loses), goes back to CupMatches.decided.
public final class CobblemonCupMatches implements CupBattles {
	// By battle: the entrant on each side (the player's own UUID; a villager entrant's, not its delegate's).
	private static final Map<UUID, UUID[]> BATTLES = new ConcurrentHashMap<>();
	private static MinecraftServer server;

	static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> BATTLES.clear());
		CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, event -> {
			if (BATTLES.containsKey(event.getBattle().getBattleId())) {
				Set<UUID> winners = new HashSet<>();
				for (BattleActor a : event.getWinners()) {
					winners.add(a.getUuid());
				}
				won(event.getBattle(), winners);
			}
		});
		CobblemonEvents.BATTLE_FLED.subscribe(Priority.NORMAL, event -> {
			if (BATTLES.containsKey(event.getBattle().getBattleId())) {
				fled(event.getBattle(), event.getPlayer().getUuid());
			}
		});
	}

	// The battle was won by the actors {@code winners} (a player's UUID; a villager trainer's actor counts for its side).
	public static void won(PokemonBattle battle, Set<UUID> winners) {
		UUID[] sides = BATTLES.remove(battle.getBattleId());
		if (sides == null || server == null) {
			return;
		}
		// a player side won if its player is among the winners; otherwise the other side (a villager has no player UUID)
		UUID winner = winners.contains(sides[0]) ? sides[0] : winners.contains(sides[1]) ? sides[1]
			: isPlayer(battle, sides[0]) ? sides[1] : sides[0];
		server.execute(() -> CupMatches.decided(server, battle.getBattleId(), winner, false));
	}

	// {@code player} fled: they lose the bout.
	public static void fled(PokemonBattle battle, UUID player) {
		UUID[] sides = BATTLES.remove(battle.getBattleId());
		if (sides == null || server == null) {
			return;
		}
		UUID winner = sides[0].equals(player) ? sides[1] : sides[0];
		server.execute(() -> CupMatches.decided(server, battle.getBattleId(), winner, true));
	}

	private static boolean isPlayer(PokemonBattle battle, UUID id) {
		for (UUID p : battle.getPlayerUUIDs()) {
			if (p.equals(id)) {
				return true;
			}
		}
		return false;
	}

	// The theme's battle format: singles or doubles, its Showdown rules, every Pokémon at its level.
	public static BattleFormat format(CupThemes.Theme theme) {
		BattleFormat base = theme.doubles() ? BattleFormat.Companion.getGEN_9_DOUBLES() : BattleFormat.Companion.getGEN_9_SINGLES();
		Set<String> rules = new LinkedHashSet<>(base.getRuleSet());
		rules.addAll(theme.rules());
		return new BattleFormat(base.getMod(), base.getBattleType(), rules, base.getGen(), theme.level());
	}

	// The party's Pokémon that may go, in party order, up to the theme's count; and why each other one stays home.
	public static List<Pokemon> eligible(PartyStore party, CupThemes.Theme theme, List<Component> leftOut) {
		Set<String> types = new HashSet<>();
		theme.types().forEach(t -> types.add(t.toLowerCase(Locale.ROOT)));
		List<Pokemon> out = new ArrayList<>();
		for (int i = 0; i < party.size(); i++) {
			Pokemon p = party.get(i);
			if (p == null) {
				continue;
			}
			Component why = whyNot(p, theme, types);
			if (why == null && out.size() >= theme.bring()) {
				why = Component.translatable("message.aliveworkplace.cup.why_count", theme.bring());
			}
			if (why == null) {
				out.add(p);
			} else {
				leftOut.add(Component.translatable("message.aliveworkplace.cup.left_out_one", p.getDisplayName(false), why));
			}
		}
		return out;
	}

	// Why a Pokémon may not go under a theme (a banned label, the stage, not one of its types, too few days helping a
	// villager at work for the Workers' Cup), or null if it may.
	static Component whyNot(Pokemon pokemon, CupThemes.Theme theme, Set<String> types) {
		Species species = pokemon.getSpecies();
		for (String label : theme.banned()) {
			if (species.getLabels().contains(label)) {
				return Component.translatable("message.aliveworkplace.cup.why_banned",
					Component.translatableWithFallback("screen.aliveworkplace.cup.label." + label, label.replace('_', ' ')));
			}
		}
		if (theme.stage().equals("first") && (species.getPreEvolution() != null || species.getEvolutions().isEmpty())) {
			return Component.translatable("message.aliveworkplace.cup.why_first");
		}
		if (theme.stage().equals("final") && !species.getEvolutions().isEmpty()) {
			return Component.translatable("message.aliveworkplace.cup.why_final");
		}
		if (theme.partnerDays() > 0 && CobblemonPartners.partnerDays(pokemon) < theme.partnerDays()) {
			return Component.translatable("message.aliveworkplace.cup.why_partner", theme.partnerDays(), CobblemonPartners.partnerDays(pokemon));
		}
		if (!types.isEmpty()) {
			for (ElementalType type : species.getStandardForm().getTypes()) {
				if (types.contains(type.getName().toLowerCase(Locale.ROOT))) {
					return null;
				}
			}
			return Component.translatable("message.aliveworkplace.cup.why_type");
		}
		return null;
	}

	@Override
	public Team team(ServerPlayer player, CupThemes.Theme theme) {
		List<Component> leftOut = new ArrayList<>();
		List<Component> going = new ArrayList<>();
		for (Pokemon p : eligible(Cobblemon.INSTANCE.getStorage().getParty(player), theme, leftOut)) {
			going.add(p.getDisplayName(false));
		}
		return new Team(going, leftOut);
	}

	// Healed copies of the player's eligible Pokémon, for their side of a battle.
	static List<BattlePokemon> copies(ServerPlayer player, CupThemes.Theme theme) {
		List<BattlePokemon> out = new ArrayList<>();
		for (Pokemon p : eligible(Cobblemon.INSTANCE.getStorage().getParty(player), theme, new ArrayList<>())) {
			BattlePokemon copy = BattlePokemon.Companion.safeCopyOf(p);
			copy.getEffectedPokemon().heal();
			out.add(copy);
		}
		return out;
	}

	@Override
	public UUID versusVillager(ServerPlayer player, Villager trainer, Component name, int tier, UUID entrant, CupThemes.Theme theme) {
		if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
			return null;
		}
		List<BattlePokemon> mine = copies(player, theme);
		List<BattlePokemon> theirs = new ArrayList<>();
		for (Pokemon pokemon : CobblemonTrainers.team(entrant, tier, theme)) {
			pokemon.getPersistentData().putBoolean(VillagerTrainerActor.TRAINER_POKEMON, true);
			BattlePokemon copy = BattlePokemon.Companion.safeCopyOf(pokemon);
			copy.getEffectedPokemon().heal();
			theirs.add(copy);
		}
		if (mine.isEmpty() || theirs.isEmpty()) {
			return null;
		}
		BattleAI ai = tier <= 1 ? new RandomBattleAI() : CobblemonMegas.megaEvolving(new StrongBattleAI(tier));
		VillagerTrainerActor them = new VillagerTrainerActor(trainer, name.getString(), theirs, ai);
		PlayerBattleActor us = new PlayerBattleActor(player.getUUID(), mine);
		BattleStartResult result = BattleRegistry.startBattle(format(theme), new BattleSide(us), new BattleSide(them), false);
		if (result instanceof SuccessfulBattleStart started) {
			UUID id = started.getBattle().getBattleId();
			BATTLES.put(id, new UUID[] {player.getUUID(), entrant});
			return id;
		}
		return null;
	}

	@Override
	public UUID versusPlayer(ServerPlayer a, ServerPlayer b, CupThemes.Theme theme) {
		if (BattleRegistry.getBattleByParticipatingPlayer(a) != null || BattleRegistry.getBattleByParticipatingPlayer(b) != null) {
			return null;
		}
		// each side's party for the battle: copies of its eligible Pokémon (pvp1v1 copies and heals them again)
		BattleStartResult result = BattleBuilder.INSTANCE.pvp1v1(a, b, null, null, format(theme), true, true, player -> {
			PartyStore party = new PartyStore(player.getUUID());
			for (Pokemon p : eligible(Cobblemon.INSTANCE.getStorage().getParty(player), theme, new ArrayList<>())) {
				party.add(p.clone(true, player.registryAccess()));
			}
			return party;
		});
		if (result instanceof SuccessfulBattleStart started) {
			UUID id = started.getBattle().getBattleId();
			BATTLES.put(id, new UUID[] {a.getUUID(), b.getUUID()});
			return id;
		}
		return null;
	}

	@Override
	public boolean running(UUID battle) {
		PokemonBattle b = BattleRegistry.getBattle(battle);
		return b != null && !b.getEnded();
	}

	@Override
	public boolean watch(ServerPlayer spectator, UUID battle) {
		PokemonBattle b = BattleRegistry.getBattle(battle);
		if (b == null || b.getEnded() || BattleRegistry.getBattleByParticipatingPlayer(spectator) != null) {
			return false;
		}
		for (ServerPlayer target : b.getPlayers()) {
			SpectateBattleHandler.INSTANCE.spectateBattle(spectator, target);
			return b.getSpectators().contains(spectator.getUUID());
		}
		return false;
	}

	// The two players' battle teams, as started (tests): the species of each side's Pokémon.
	public static List<List<String>> teams(UUID battle) {
		PokemonBattle b = BattleRegistry.getBattle(battle);
		List<List<String>> out = new ArrayList<>();
		if (b != null) {
			for (BattleActor actor : b.getActors()) {
				out.add(actor.getPokemonList().stream().map(p -> p.getEffectedPokemon().getSpecies().getName()).toList());
			}
		}
		return out;
	}

	private CobblemonCupMatches() {
	}

	static final CobblemonCupMatches INSTANCE = new CobblemonCupMatches();
}
//?}
