//? if cobblemon {
package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.net.messages.client.battle.BattleHealthChangePacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

// Mega Evolution for Master trainers when Mega Showdown is installed: one Pokémon on a Master's team holds its Mega
// Stone, and the trainer's battle AI Mega Evolves it the first turn it can. Mega Showdown is found by its item ids only
// (no dependency); its own battle code does the rest, as it does for players.
public final class CobblemonMegas {
	public static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("mega_showdown");

	// Pokémon with a Mega Evolution (Cobblemon's species id) and their Mega Stones; legendary and mythical ones left out.
	static final Map<String, List<String>> STONES = Map.ofEntries(
		Map.entry("venusaur", List.of("venusaurite")), Map.entry("charizard", List.of("charizardite_x", "charizardite_y")),
		Map.entry("blastoise", List.of("blastoisinite")), Map.entry("beedrill", List.of("beedrillite")),
		Map.entry("pidgeot", List.of("pidgeotite")), Map.entry("alakazam", List.of("alakazite")),
		Map.entry("slowbro", List.of("slowbronite")), Map.entry("gengar", List.of("gengarite")),
		Map.entry("kangaskhan", List.of("kangaskhanite")), Map.entry("pinsir", List.of("pinsirite")),
		Map.entry("gyarados", List.of("gyaradosite")), Map.entry("aerodactyl", List.of("aerodactylite")),
		Map.entry("ampharos", List.of("ampharosite")), Map.entry("steelix", List.of("steelixite")),
		Map.entry("scizor", List.of("scizorite")), Map.entry("heracross", List.of("heracronite")),
		Map.entry("houndoom", List.of("houndoominite")), Map.entry("tyranitar", List.of("tyranitarite")),
		Map.entry("sceptile", List.of("sceptilite")), Map.entry("blaziken", List.of("blazikenite")),
		Map.entry("swampert", List.of("swampertite")), Map.entry("gardevoir", List.of("gardevoirite")),
		Map.entry("sableye", List.of("sablenite")), Map.entry("mawile", List.of("mawilite")),
		Map.entry("aggron", List.of("aggronite")), Map.entry("medicham", List.of("medichamite")),
		Map.entry("manectric", List.of("manectite")), Map.entry("sharpedo", List.of("sharpedonite")),
		Map.entry("camerupt", List.of("cameruptite")), Map.entry("altaria", List.of("altarianite")),
		Map.entry("banette", List.of("banettite")), Map.entry("absol", List.of("absolite")),
		Map.entry("glalie", List.of("glalitite")), Map.entry("salamence", List.of("salamencite")),
		Map.entry("metagross", List.of("metagrossite")), Map.entry("lopunny", List.of("lopunnite")),
		Map.entry("garchomp", List.of("garchompite")), Map.entry("lucario", List.of("lucarionite")),
		Map.entry("abomasnow", List.of("abomasite")), Map.entry("gallade", List.of("galladite")),
		Map.entry("audino", List.of("audinite")));

	// A Mega Stone for this species (a random one of Charizard's two), if it has one and Mega Showdown has the item.
	static Optional<Item> stoneFor(Species species, Random random) {
		List<String> stones = STONES.get(species.getResourceIdentifier().getPath());
		if (stones == null) {
			return Optional.empty();
		}
		return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("mega_showdown", stones.get(random.nextInt(stones.size()))));
	}

	// True if this Pokémon holds the Mega Stone for its species.
	public static boolean holdsItsStone(Pokemon pokemon) {
		List<String> stones = STONES.get(pokemon.getSpecies().getResourceIdentifier().getPath());
		ResourceLocation held = BuiltInRegistries.ITEM.getKey(pokemon.heldItem().getItem());
		return stones != null && held.getNamespace().equals("mega_showdown") && stones.contains(held.getPath());
	}

	// Gives a Master's team its Mega Evolution: the first Pokémon that has one holds its stone; if none has, the last is
	// swapped for one that does (picked with {@code random}, same level, trained the same way).
	static void megaAce(List<Pokemon> team, int t, Random random) {
		if (!ENABLED || team.isEmpty()) {
			return;
		}
		for (Pokemon pokemon : team) {
			Optional<Item> stone = stoneFor(pokemon.getSpecies(), random);
			if (stone.isPresent()) {
				pokemon.swapHeldItem(new ItemStack(stone.get()), false, false);
				return;
			}
		}
		List<String> names = STONES.keySet().stream().sorted().toList();
		for (int tries = 0; tries < names.size(); tries++) {
			Species species = PokemonSpecies.getByName(names.get(random.nextInt(names.size())));
			if (species == null || !species.getImplemented() || team.stream().anyMatch(p -> p.getSpecies() == species)) {
				continue;
			}
			Optional<Item> stone = stoneFor(species, random);
			if (stone.isEmpty()) {
				continue;
			}
			Pokemon last = team.get(team.size() - 1);
			Pokemon ace = species.create(last.getLevel());
			CobblemonTrainers.train(ace, t, random);
			ace.swapHeldItem(new ItemStack(stone.get()), false, false);
			team.set(team.size() - 1, ace);
			return;
		}
	}

	// A battle AI that Mega Evolves the first time it can, and otherwise does what {@code delegate} does.
	public static BattleAI megaEvolving(BattleAI delegate) {
		return ENABLED ? new MegaAI(delegate) : delegate;
	}

	static final class MegaAI implements BattleAI {
		private final BattleAI delegate;

		MegaAI(BattleAI delegate) {
			this.delegate = delegate;
		}

		@Override
		public ShowdownActionResponse choose(ActiveBattlePokemon active, PokemonBattle battle, BattleSide side,
				@Nullable ShowdownMoveset moveset, boolean forceSwitch) {
			ShowdownActionResponse response = delegate.choose(active, battle, side, moveset, forceSwitch);
			// Only a Pokémon out in the world: Mega Showdown shows the Mega Evolution on its entity and can't do without one.
			var pokemon = active.getBattlePokemon();
			return pokemon != null && pokemon.getEntity() != null ? withMega(response, moveset) : response;
		}

		@Override
		public void onHealthChange(BattleHealthChangePacket packet) {
			delegate.onHealthChange(packet);
		}
	}

	// The same move, Mega Evolving first, when the moveset says the Pokémon can.
	public static ShowdownActionResponse withMega(ShowdownActionResponse response, @Nullable ShowdownMoveset moveset) {
		if (moveset != null && moveset.getCanMegaEvo() && response instanceof MoveActionResponse move && move.getGimmickID() == null) {
			return new MoveActionResponse(move.getMoveName(), move.getTargetPnx(), ShowdownMoveset.Gimmick.MEGA_EVOLUTION.getId());
		}
		return response;
	}

	private CobblemonMegas() {
	}
}
//?}
