package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.BattleStartResult;
import com.cobblemon.mod.common.battles.SuccessfulBattleStart;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.actor.TrainerBattleActor;
import com.cobblemon.mod.common.battles.ai.RandomBattleAI;
import com.cobblemon.mod.common.battles.ai.StrongBattleAI;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/**
 * The Cobblemon half of Trainers: building a trainer's team, starting the battle, noticing how it ended.
 * Only touched when Cobblemon is installed.
 */
public final class CobblemonTrainers {
	/** Team size and level range by tier (Novice..Master). */
	private static final int[] SIZE = {2, 3, 4, 5, 6};
	private static final int[] MIN_LEVEL = {5, 15, 30, 50, 80};
	private static final int[] MAX_LEVEL = {12, 25, 42, 65, 100};
	private static final Set<String> NOT_FOR_TRAINERS = Set.of("legendary", "mythical", "ultra_beast", "paradox");

	private record Challenge(UUID trainer, UUID player) {
	}

	private static final Map<UUID, Challenge> BATTLES = new ConcurrentHashMap<>();
	private static MinecraftServer server;

	/** Called once at startup (when Cobblemon is installed): listen for battles ending. */
	public static void init() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, event -> {
			Challenge c = BATTLES.remove(event.getBattle().getBattleId());
			if (c != null && server != null) {
				boolean playerWon = event.getWinners().stream().anyMatch(a -> a.getUuid().equals(c.player()));
				server.execute(() -> Trainers.battleOver(server, c.trainer(), c.player(), playerWon));
			}
		});
		CobblemonEvents.BATTLE_FLED.subscribe(Priority.NORMAL, event -> {
			Challenge c = BATTLES.remove(event.getBattle().getBattleId());
			if (c != null && server != null) {
				server.execute(() -> Trainers.battleOver(server, c.trainer(), c.player(), false));
			}
		});
	}

	public static boolean isBattling(Villager trainer) {
		return BATTLES.values().stream().anyMatch(c -> c.trainer().equals(trainer.getUUID()));
	}

	/** Starts a battle between {@code player} and {@code trainer}. */
	public static void challenge(ServerPlayer player, Villager trainer) {
		if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
			return;
		}
		if (isBattling(trainer)) {
			player.displayClientMessage(Component.translatable("message.aliveworkplace.trainer.busy", trainer.getDisplayName()).withStyle(ChatFormatting.YELLOW), true);
			return;
		}
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		List<BattlePokemon> mine = party.toBattleTeam(false, true, null);
		if (mine.isEmpty()) {
			player.displayClientMessage(Component.translatable("message.aliveworkplace.trainer.no_team").withStyle(ChatFormatting.YELLOW), true);
			return;
		}
		int tier = Trainers.tier(trainer);
		List<Pokemon> team = team(trainer.getUUID(), tier);
		java.util.OptionalInt cap = io.github.jcondedata.aliveworkplace.compat.rct.RctLevelCaps.levelCap(player);
		if (cap.isPresent()) {
			team = scaleToCap(team, tier, cap.getAsInt());
		}
		List<BattlePokemon> theirs = new ArrayList<>();
		for (Pokemon pokemon : team) {
			theirs.add(BattlePokemon.Companion.safeCopyOf(pokemon));
		}
		BattleAI ai = tier <= 1 ? new RandomBattleAI() : new StrongBattleAI(tier);
		TrainerBattleActor them = new TrainerBattleActor(Trainers.title(trainer).getString(), trainer.getUUID(), theirs, ai);
		PlayerBattleActor us = new PlayerBattleActor(player.getUUID(), mine);
		BattleStartResult result = BattleRegistry.startBattle(BattleFormat.Companion.getGEN_9_SINGLES(), new BattleSide(us), new BattleSide(them), false);
		if (result instanceof SuccessfulBattleStart started) {
			BATTLES.put(started.getBattle().getBattleId(), new Challenge(trainer.getUUID(), player.getUUID()));
			player.sendSystemMessage(Component.translatable("message.aliveworkplace.trainer.challenge", Trainers.title(trainer), theirs.size())
				.withStyle(ChatFormatting.GOLD));
		} else {
			player.displayClientMessage(Component.translatable("message.aliveworkplace.trainer.cant_start").withStyle(ChatFormatting.RED), true);
		}
	}

	/**
	 * The trainer's team at this tier: always the same for the same trainer and tier (seeded by the
	 * villager), never legendaries; young Pokémon for beginners, fully evolved ones at the top.
	 */
	public static List<Pokemon> team(UUID trainer, int tier) {
		int t = Math.max(1, Math.min(5, tier)) - 1;
		Random random = new Random(trainer.getMostSignificantBits() ^ trainer.getLeastSignificantBits() ^ (31L * t));
		List<Species> pool = new ArrayList<>();
		for (Species species : PokemonSpecies.getImplemented()) {
			if (species.getLabels().stream().anyMatch(NOT_FOR_TRAINERS::contains)) {
				continue;
			}
			boolean basic = species.getPreEvolution() == null;
			boolean finalForm = species.getEvolutions().isEmpty();
			boolean fits = switch (t) {
				case 0, 1 -> basic;
				case 2 -> !basic || finalForm;
				default -> finalForm;
			};
			if (fits) {
				pool.add(species);
			}
		}
		if (pool.isEmpty()) {
			pool.addAll(PokemonSpecies.getImplemented());
		}
		pool.sort(Comparator.comparing(Species::getName));
		List<Pokemon> team = new ArrayList<>();
		for (int i = 0; i < SIZE[t] && !pool.isEmpty(); i++) {
			Species species = pool.remove(random.nextInt(pool.size()));
			int level = MIN_LEVEL[t] + random.nextInt(MAX_LEVEL[t] - MIN_LEVEL[t] + 1);
			Pokemon pokemon = species.create(level);
			// Its own random, so training doesn't change which species the trainer picks.
			train(pokemon, t, new Random(trainer.getLeastSignificantBits() ^ (7919L * (i + 1)) ^ (131L * t)));
			team.add(pokemon);
		}
		return team;
	}

	/**
	 * Competitive touches for the stronger trainers: better IVs from Journeyman (15+), Expert (25+) and Master
	 * (31); from Expert, EVs in their better attacking stat and Speed, a nature to match (Adamant or Modest)
	 * and a held item suited to that.
	 */
	static void train(Pokemon pokemon, int t, Random random) {
		if (t < 2) {
			return;
		}
		int minIv = t == 2 ? 15 : t == 3 ? 25 : 31;
		for (Stats stat : List.of(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED)) {
			pokemon.getIvs().set(stat, minIv + random.nextInt(32 - minIv));
		}
		if (t >= 3) {
			Map<com.cobblemon.mod.common.api.pokemon.stats.Stat, Integer> base = pokemon.getForm().getBaseStats();
			boolean physical = base.getOrDefault(Stats.ATTACK, 0) >= base.getOrDefault(Stats.SPECIAL_ATTACK, 0);
			pokemon.getEvs().set(physical ? Stats.ATTACK : Stats.SPECIAL_ATTACK, 252);
			pokemon.getEvs().set(Stats.SPEED, 252);
			pokemon.getEvs().set(Stats.HP, 4);
			pokemon.setNature(physical ? com.cobblemon.mod.common.api.pokemon.Natures.ADAMANT : com.cobblemon.mod.common.api.pokemon.Natures.MODEST);
			List<net.minecraft.world.item.Item> items = physical
				? List.of(CobblemonItems.LIFE_ORB, CobblemonItems.CHOICE_BAND, CobblemonItems.MUSCLE_BAND, CobblemonItems.LEFTOVERS,
					CobblemonItems.SITRUS_BERRY, CobblemonItems.FOCUS_SASH, CobblemonItems.EXPERT_BELT, CobblemonItems.CHOICE_SCARF)
				: List.of(CobblemonItems.LIFE_ORB, CobblemonItems.CHOICE_SPECS, CobblemonItems.WISE_GLASSES, CobblemonItems.LEFTOVERS,
					CobblemonItems.SITRUS_BERRY, CobblemonItems.FOCUS_SASH, CobblemonItems.EXPERT_BELT, CobblemonItems.ASSAULT_VEST);
			pokemon.swapHeldItem(new net.minecraft.world.item.ItemStack(items.get(random.nextInt(items.size()))), false, false);
		}
		pokemon.heal();
	}

	/**
	 * With Radical Cobblemon Trainers: no Pokémon above the tier's ceiling for this player's level cap (see
	 * {@link Trainers#capCeiling}), so a village's trainers stay beatable for new players and tough for
	 * everyone. Lower-level teams are left as they are.
	 */
	public static List<Pokemon> scaleToCap(List<Pokemon> team, int tier, int levelCap) {
		int ceiling = Trainers.capCeiling(tier, levelCap);
		List<Pokemon> out = new ArrayList<>();
		for (int i = 0; i < team.size(); i++) {
			Pokemon pokemon = team.get(i);
			if (pokemon.getLevel() > ceiling) {
				pokemon.setLevel(Math.max(1, ceiling - i % 3)); // same Pokémon, training and all, just younger
				pokemon.heal();
			}
			out.add(pokemon);
		}
		return out;
	}

	/** Ends a battle as if it had been decided (tests). */
	public static void finishForTest(PokemonBattle battle, boolean playerWon) {
		Challenge c = BATTLES.remove(battle.getBattleId());
		battle.end();
		if (c != null && server != null) {
			Trainers.battleOver(server, c.trainer(), c.player(), playerWon);
		}
	}

	private CobblemonTrainers() {
	}
}
