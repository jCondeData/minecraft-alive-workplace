//? if cobblemon {
package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.trader.PokemonTraders;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;

// The Cobblemon half of Pokémon Traders: the day's offers, the trade screen and the swap itself.
// An offer is one of the trader's Pokémon for any of yours of a given type and level; offers are the
// same for everyone on a given day (seeded by the trader and the day).
public final class CobblemonTraders {
	// Offers per tier (Novice..Master), and the level range of the Pokémon offered.
	private static final int[] OFFERS = {1, 2, 2, 3, 3};
	private static final int[] MIN_LEVEL = {5, 15, 25, 35, 50};
	private static final int[] MAX_LEVEL = {15, 25, 35, 50, 70};
	private static final Set<String> NOT_FOR_TRADE = Set.of("legendary", "mythical", "ultra_beast", "paradox");
	// Row 1: offers; row 3: your party.
	public static final int FIRST_PARTY_SLOT = 18;
	private static final int INFO = 8;
	private static final Vector4f GREYED = new Vector4f(0.35f, 0.35f, 0.35f, 1f);

	// One of the day's offers: their Pokémon (species, level, shiny) for yours of {@code wanted} type, level
	// {@code minLevel}+ — or, for a special request, of {@code wantedSpecies}' evolution family.
	public record Offer(Species species, int level, boolean shiny, ElementalType wanted, int minLevel, @Nullable Species wantedSpecies) {
		public Offer(Species species, int level, boolean shiny, ElementalType wanted, int minLevel) {
			this(species, level, shiny, wanted, minLevel, null);
		}

		Pokemon create() {
			Pokemon pokemon = species.create(level);
			pokemon.setShiny(shiny);
			return pokemon;
		}

		// Why {@code pokemon} won't do for this offer, or null if it will.
		@Nullable
		public Component refusal(Pokemon pokemon) {
			if (!pokemon.getTradeable()) {
				return Component.translatable("message.aliveworkplace.pokemon_trader.untradeable");
			}
			if (wantedSpecies != null) {
				if (root(pokemon.getSpecies()) != root(wantedSpecies)) {
					return Component.translatable("message.aliveworkplace.pokemon_trader.wrong_species", wantedSpecies.getTranslatedName());
				}
				return pokemon.getLevel() < minLevel ? Component.translatable("message.aliveworkplace.pokemon_trader.too_low", minLevel) : null;
			}
			boolean typeFits = false;
			for (ElementalType type : pokemon.getTypes()) {
				typeFits |= type.getName().equals(wanted.getName());
			}
			if (!typeFits) {
				return Component.translatable("message.aliveworkplace.pokemon_trader.wrong_type", wanted.getDisplayName());
			}
			if (pokemon.getLevel() < minLevel) {
				return Component.translatable("message.aliveworkplace.pokemon_trader.too_low", minLevel);
			}
			return null;
		}
	}

	// Today's offers from this trader (the same all day, for everyone).
	public static List<Offer> offers(Villager trader) {
		return offers(trader.getUUID(), PokemonTraders.tier(trader), PokemonTraders.day(trader));
	}

	public static List<Offer> offers(UUID trader, int tier, long day) {
		int t = Math.max(1, Math.min(5, tier)) - 1;
		Random random = new Random(trader.getMostSignificantBits() * 31 + trader.getLeastSignificantBits() + day * 1_000_003L + t);
		List<Species> pool = new ArrayList<>();
		for (Species species : PokemonSpecies.getImplemented()) {
			if (species.getLabels().stream().noneMatch(NOT_FOR_TRADE::contains)) {
				boolean basic = species.getPreEvolution() == null;
				boolean finalForm = species.getEvolutions().isEmpty();
				if (t <= 1 ? basic : t == 2 ? (!basic || finalForm) : finalForm) {
					pool.add(species);
				}
			}
		}
		if (pool.isEmpty()) {
			pool.addAll(PokemonSpecies.getImplemented());
		}
		pool.sort(Comparator.comparing(Species::getName));
		List<ElementalType> types = new ArrayList<>(ElementalTypes.all());
		types.sort(Comparator.comparing(ElementalType::getName));
		List<Offer> offers = new ArrayList<>();
		for (int i = 0; i < OFFERS[t] && !pool.isEmpty(); i++) {
			Species species = pool.remove(random.nextInt(pool.size()));
			int level = MIN_LEVEL[t] + random.nextInt(MAX_LEVEL[t] - MIN_LEVEL[t] + 1);
			boolean shiny = t == 4 && random.nextInt(10) == 0;
			// Something different from what they're giving away.
			List<ElementalType> wanted = new ArrayList<>(types);
			for (ElementalType own : species.getTypes()) {
				wanted.removeIf(w -> w.getName().equals(own.getName()));
			}
			ElementalType type = (wanted.isEmpty() ? types : wanted).get(random.nextInt((wanted.isEmpty() ? types : wanted).size()));
			int minLevel = Math.max(5, (level - 5) / 5 * 5);
			offers.add(new Offer(species, level, shiny, type, minLevel));
		}
		// Experts and Masters make one special request a day: a particular Pokémon (any of its evolutions), for
		// one of theirs at the top of their range, shiny one time in four.
		if (t >= 3 && !pool.isEmpty()) {
			List<Species> requests = new ArrayList<>();
			for (Species s : PokemonSpecies.getImplemented()) {
				if (s.getPreEvolution() == null && !s.getEvolutions().isEmpty() && s.getLabels().stream().noneMatch(NOT_FOR_TRADE::contains)) {
					requests.add(s);
				}
			}
			requests.sort(Comparator.comparing(Species::getName));
			if (!requests.isEmpty()) {
				Species species = pool.remove(random.nextInt(pool.size()));
				Species wanted = requests.get(random.nextInt(requests.size()));
				int minLevel = MIN_LEVEL[t] - 10;
				offers.add(new Offer(species, MAX_LEVEL[t], random.nextInt(4) == 0, wanted.getPrimaryType(), minLevel, wanted));
			}
		}
		return offers;
	}

	// The first form of a Pokémon's evolution line (Charizard → Charmander).
	static Species root(Species species) {
		Species current = species;
		for (int i = 0; i < 5 && current.getPreEvolution() != null; i++) {
			current = current.getPreEvolution().getSpecies();
		}
		return current;
	}

	// What's on the screen: the offer being looked at and a Pokémon waiting for the second click.
	private static final class State {
		int offer;
		@Nullable
		UUID pending;
	}

	public static void open(ServerPlayer player, Villager trader) {
		if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.pokemon_trader.in_battle").withStyle(ChatFormatting.YELLOW));
			return;
		}
		State state = new State();
		ChoiceMenu.open(player, PokemonTraders.title(trader),
			p -> trader.isAlive() && !trader.isSleeping() && p.isAlive() && p.distanceTo(trader) <= PokemonTraders.REACH,
			menu -> render(menu, player, trader, state));
	}

	// The trade screen without showing it (tests).
	public static ChoiceMenu menuForTest(ServerPlayer player, Villager trader) {
		State state = new State();
		return ChoiceMenu.detached(player, menu -> render(menu, player, trader, state));
	}

	// Row 1: the offers (and info). Row 2: a divider. Row 3: your party, greyed out when it won't do.
	private static void render(ChoiceMenu menu, ServerPlayer player, Villager trader, State state) {
		menu.clearButtons();
		List<Offer> offers = offers(trader);
		state.offer = Math.max(0, Math.min(offers.size() - 1, state.offer));
		boolean done = PokemonTraders.tradedToday(trader, player.getUUID());
		for (int i = 0; i < offers.size(); i++) {
			Offer offer = offers.get(i);
			int index = i;
			ItemStack icon = PokemonItem.from(offer.create());
			icon.set(DataComponents.CUSTOM_NAME, plain(pokemonName(offer.species().getTranslatedName(), offer.level(), offer.shiny())));
			icon.set(DataComponents.LORE, lore(
				types(offer.species().getTypes()),
				offer.wantedSpecies() != null
					? Component.translatable("message.aliveworkplace.pokemon_trader.wants_species", offer.wantedSpecies().getTranslatedName(),
						offer.minLevel()).withStyle(ChatFormatting.LIGHT_PURPLE)
					: Component.translatable("message.aliveworkplace.pokemon_trader.wants", offer.wanted().getDisplayName().copy()
						.withColor(offer.wanted().getHue()), offer.minLevel()).withStyle(ChatFormatting.YELLOW),
				Component.translatable("message.aliveworkplace.pokemon_trader.pick_offer").withStyle(ChatFormatting.GRAY)));
			if (i == state.offer) {
				icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			menu.button(i, icon, p -> {
				state.offer = index;
				state.pending = null;
				render(menu, player, trader, state);
			});
		}
		ItemStack info = named(Items.BOOK, PokemonTraders.title(trader));
		info.set(DataComponents.LORE, lore(
			Component.translatable("message.aliveworkplace.pokemon_trader.info").withStyle(ChatFormatting.GRAY),
			done ? Component.translatable("message.aliveworkplace.pokemon_trader.come_back").withStyle(ChatFormatting.RED)
				: Component.translatable("message.aliveworkplace.pokemon_trader.one_a_day").withStyle(ChatFormatting.DARK_GRAY)));
		menu.button(INFO, info, null);
		menu.divider(1);
		if (offers.isEmpty()) {
			return;
		}
		Offer offer = offers.get(state.offer);
		List<Pokemon> party = new ArrayList<>();
		for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
			party.add(pokemon);
		}
		for (int i = 0; i < party.size() && i < 6; i++) {
			Pokemon pokemon = party.get(i);
			Component refusal = done ? Component.translatable("message.aliveworkplace.pokemon_trader.come_back") : offer.refusal(pokemon);
			boolean pending = pokemon.getUuid().equals(state.pending);
			ItemStack icon = refusal == null ? PokemonItem.from(pokemon) : PokemonItem.from(pokemon, 1, GREYED);
			icon.set(DataComponents.CUSTOM_NAME, plain(pokemonName(pokemon.getDisplayName(false), pokemon.getLevel(), pokemon.getShiny())));
			icon.set(DataComponents.LORE, lore(
				types(pokemon.getTypes()),
				refusal != null ? refusal.copy().withStyle(ChatFormatting.RED)
					: pending ? Component.translatable("message.aliveworkplace.pokemon_trader.confirm", pokemon.getDisplayName(false),
						offer.species().getTranslatedName()).withStyle(ChatFormatting.YELLOW)
					: Component.translatable("message.aliveworkplace.pokemon_trader.click").withStyle(ChatFormatting.GREEN),
				refusal == null && !pokemon.heldItem().isEmpty()
					? Component.translatable("message.aliveworkplace.pokemon_trader.held_item").withStyle(ChatFormatting.DARK_GRAY) : null));
			if (pending) {
				icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			menu.button(FIRST_PARTY_SLOT + i, icon, refusal != null ? null : p -> {
				if (!pokemon.getUuid().equals(state.pending)) {
					state.pending = pokemon.getUuid();
				} else {
					state.pending = null;
					trade(p, trader, pokemon, offer);
				}
				render(menu, player, trader, state);
			});
		}
	}

	// Swaps {@code yours} for the offer's Pokémon (the second click).
	public static boolean trade(ServerPlayer player, Villager trader, Pokemon yours, Offer offer) {
		if (BattleRegistry.getBattleByParticipatingPlayer(player) != null || PokemonTraders.tradedToday(trader, player.getUUID())
			|| offer.refusal(yours) != null || !offers(trader).contains(offer)) {
			return false;
		}
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		boolean inParty = false;
		for (Pokemon member : party) {
			inParty |= member == yours;
		}
		if (!inParty) {
			return false;
		}
		if (yours.getEntity() != null) {
			yours.recall();
		}
		ItemStack held = yours.removeHeldItem();
		if (!party.remove(yours)) {
			return false;
		}
		Pokemon theirs = offer.create();
		theirs.setOriginalTrainer(trader.getDisplayName().getString()); // an NPC trainer, like in the games
		if (!party.add(theirs)) {
			Cobblemon.INSTANCE.getStorage().getPC(player).add(theirs);
		}
		if (!held.isEmpty() && !player.getInventory().add(held)) {
			player.drop(held, false);
		}
		player.sendSystemMessage(Component.translatable("message.aliveworkplace.pokemon_trader.traded", yours.getDisplayName(false),
			theirs.getDisplayName(false), trader.getDisplayName()).withStyle(ChatFormatting.GREEN));
		trader.level().playSound(null, trader, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		trader.level().playSound(null, trader, SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.5f, 1.2f);
		PokemonTraders.traded(player, trader);
		return true;
	}

	private static Component pokemonName(Component name, int level, boolean shiny) {
		MutableComponent text = Component.translatable("message.aliveworkplace.tutor.pokemon", name, level).withStyle(ChatFormatting.WHITE);
		return shiny ? text.append(Component.literal(" ★").withStyle(ChatFormatting.GOLD)) : text;
	}

	private static Component types(Iterable<ElementalType> types) {
		MutableComponent line = Component.empty();
		boolean first = true;
		for (ElementalType type : types) {
			if (!first) {
				line.append(Component.literal(" / ").withStyle(ChatFormatting.DARK_GRAY));
			}
			line.append(type.getDisplayName().copy().withColor(type.getHue()));
			first = false;
		}
		return line;
	}

	private static ItemStack named(net.minecraft.world.item.Item item, Component name) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, plain(name));
		return stack;
	}

	private static Component plain(Component c) {
		return c.copy().withStyle(s -> s.withItalic(false));
	}

	private static ItemLore lore(@Nullable Component... lines) {
		List<Component> out = new ArrayList<>();
		for (Component line : lines) {
			if (line != null) {
				out.add(plain(line));
			}
		}
		return new ItemLore(out);
	}

	private CobblemonTraders() {
	}
}
//?}
