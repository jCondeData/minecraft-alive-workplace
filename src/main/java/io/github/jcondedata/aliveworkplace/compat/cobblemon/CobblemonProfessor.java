//? if cobblemon {
package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.abilities.Ability;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.PokemonCensus;
import io.github.jcondedata.aliveworkplace.legend.PokemonProfessor;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

// The Pokémon Professor (ROADMAP 29.21) with Cobblemon: the census of the village's Pasture Blocks, and the hints
// screen. Row 0: what the Professor does, the village Pokédex and (with Evolution Studies) the day's evolution stone;
// row 2: the player's party, each Pokémon's hints in its tooltip, and a click puts them in chat to keep.
public final class CobblemonProfessor implements PokemonCensus {
	public static final int INFO = 0;
	public static final int POKEDEX = 4;
	public static final int STONE = 8;
	public static final int FIRST_PARTY_SLOT = 18;
	static final double REACH = 8;
	// The six stats, in the games' order.
	public static final List<Stats> STATS = List.of(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED);

	@Override
	public Count census(ServerLevel level, BlockPos hall) {
		List<PokemonEntity> pastured = level.getEntitiesOfClass(PokemonEntity.class, VillageHalls.area(hall), e -> e.isAlive() && e.getTethering() != null);
		Set<String> types = new LinkedHashSet<>();
		Set<String> species = new LinkedHashSet<>();
		for (PokemonEntity e : pastured) {
			Pokemon p = e.getPokemon();
			for (ElementalType type : p.getTypes()) {
				types.add(type.getName().toLowerCase(Locale.ROOT));
			}
			species.add(p.getSpecies().getResourceIdentifier().toString());
		}
		return new Count(pastured.size(), Set.copyOf(types), java.util.Collections.unmodifiableSet(species));
	}

	@Override
	public Component speciesName(String id) {
		ResourceLocation rl = ResourceLocation.tryParse(id);
		Species species = rl == null ? null : PokemonSpecies.getByIdentifier(rl);
		return species == null ? Component.literal(id) : species.getTranslatedName();
	}

	@Override
	public void openHints(ServerPlayer player, Villager professor) {
		ChoiceMenu.open(player, Component.translatable("screen.aliveworkplace.professor", professor.getDisplayName()),
			p -> professor.isAlive() && p.isAlive() && p.distanceTo(professor) <= REACH,
			menu -> render(menu, player, professor));
	}

	// The screen without showing it (tests).
	public static ChoiceMenu menuForTest(ServerPlayer player, Villager professor) {
		return ChoiceMenu.detached(player, menu -> render(menu, player, professor));
	}

	// ---- the hints ----

	// Whether {@code pokemon}'s ability is one of its form's hidden abilities.
	public static boolean hasHiddenAbility(Pokemon pokemon) {
		return hiddenAbility(pokemon).map(name -> name.equals(pokemon.getAbility().getName())).orElse(false);
	}

	// The name of the hidden ability {@code pokemon}'s form may have, if it has one.
	public static Optional<String> hiddenAbility(Pokemon pokemon) {
		return hiddenTemplate(pokemon).map(AbilityTemplate::getName);
	}

	private static Optional<AbilityTemplate> hiddenTemplate(Pokemon pokemon) {
		for (PotentialAbility a : pokemon.getForm().getAbilities()) {
			if (a instanceof HiddenAbility) {
				return Optional.of(a.getTemplate());
			}
		}
		return Optional.empty();
	}

	// What the Professor says about {@code pokemon}: its nature, each stat's IV and EVs (exact with Regional Survey) and
	// its hidden ability.
	public static List<Component> hints(Pokemon pokemon, boolean exact) {
		List<Component> out = new ArrayList<>();
		Nature nature = pokemon.getEffectiveNature();
		Stat up = nature.getIncreasedStat();
		Stat down = nature.getDecreasedStat();
		Component natureName = Component.translatable(nature.getDisplayName());
		out.add(up == null || down == null || up == down
			? Component.translatable("message.aliveworkplace.professor.nature_neutral", natureName).withStyle(ChatFormatting.YELLOW)
			: Component.translatable("message.aliveworkplace.professor.nature", natureName, up.getDisplayName(), down.getDisplayName()).withStyle(ChatFormatting.YELLOW));
		for (Stats stat : STATS) {
			int iv = pokemon.getIvs().getOrDefault(stat);
			int ev = pokemon.getEvs().getOrDefault(stat);
			out.add(PokemonProfessor.statLine(stat.getDisplayName(), iv, ev, exact).withStyle(iv >= 30 ? ChatFormatting.GREEN : iv >= 16 ? ChatFormatting.WHITE : ChatFormatting.GRAY));
		}
		Ability ability = pokemon.getAbility();
		Component has = Component.translatable(ability.getDisplayName());
		Optional<AbilityTemplate> hidden = hiddenTemplate(pokemon);
		Component line;
		if (hidden.isEmpty()) {
			line = Component.translatable("message.aliveworkplace.professor.no_hidden_kind", has);
		} else if (hidden.get().getName().equals(ability.getName())) {
			line = Component.translatable("message.aliveworkplace.professor.hidden", has);
		} else {
			line = Component.translatable("message.aliveworkplace.professor.not_hidden", Component.translatable(hidden.get().getDisplayName()), has);
		}
		out.add(line.copy().withStyle(hasHiddenAbility(pokemon) ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA));
		return out;
	}

	// ---- the screen ----

	private static void render(ChoiceMenu menu, ServerPlayer player, Villager professor) {
		menu.clearButtons();
		boolean exact = PokemonProfessor.exact(professor);
		menu.button(INFO, named(Items.WRITABLE_BOOK, Component.translatable("screen.aliveworkplace.professor", professor.getDisplayName()),
			Component.translatable("screen.aliveworkplace.professor.info").withStyle(ChatFormatting.GRAY),
			Component.translatable(exact ? "screen.aliveworkplace.professor.exact" : "screen.aliveworkplace.professor.words").withStyle(ChatFormatting.DARK_GRAY)), null);
		ServerLevel level = (ServerLevel) professor.level();
		VillageHalls.nearest(level, professor.blockPosition()).ifPresent(hall -> {
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
				menu.button(POKEDEX, PokemonProfessor.icon(level, hall, entity), null);
			}
		});
		if (PokemonProfessor.sellsStones(professor)) {
			Item stone = PokemonProfessor.stoneOfDay(level);
			if (stone != null) {
				boolean sold = PokemonProfessor.soldToday(professor);
				ItemStack icon = named(stone, Component.translatable("screen.aliveworkplace.professor.stone", new ItemStack(stone).getHoverName()),
					sold ? Component.translatable("screen.aliveworkplace.professor.stone_sold").withStyle(ChatFormatting.GRAY)
						: Component.translatable("screen.aliveworkplace.professor.stone_buy",
							Money.describe((long) PokemonProfessor.STONE_PRICE * Money.DOLLARS_PER_EMERALD, PokemonProfessor.STONE_PRICE)).withStyle(ChatFormatting.GREEN));
				menu.button(STONE, icon, sold ? null : p -> {
					Chat.chat(p, PokemonProfessor.buyStone(p, professor));
					render(menu, player, professor);
				});
			}
		}
		menu.divider(1);
		List<Pokemon> party = new ArrayList<>();
		for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
			party.add(pokemon);
		}
		if (party.isEmpty()) {
			menu.button(FIRST_PARTY_SLOT, named(Items.BARRIER, Component.translatable("screen.aliveworkplace.professor.no_party")), null);
			return;
		}
		for (int i = 0; i < party.size() && i < 6; i++) {
			Pokemon pokemon = party.get(i);
			List<Component> hints = hints(pokemon, exact);
			ItemStack icon = PokemonItem.from(pokemon);
			icon.set(DataComponents.CUSTOM_NAME, plain(Component.translatable("message.aliveworkplace.tutor.pokemon", pokemon.getDisplayName(false),
				pokemon.getLevel()).withStyle(ChatFormatting.WHITE)));
			List<Component> lore = new ArrayList<>(hints);
			lore.add(Component.translatable("screen.aliveworkplace.professor.click").withStyle(ChatFormatting.DARK_GRAY));
			icon.set(DataComponents.LORE, lore(lore.toArray(Component[]::new)));
			menu.button(FIRST_PARTY_SLOT + i, icon, p -> {
				Chat.chat(p, Component.translatable("message.aliveworkplace.professor.says", professor.getDisplayName(), pokemon.getDisplayName(false))
					.withStyle(ChatFormatting.GOLD));
				for (Component line : hints(pokemon, PokemonProfessor.exact(professor))) {
					Chat.chat(p, line);
				}
				professor.level().playSound(null, professor.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 1f, 1f);
			});
		}
	}

	private static ItemStack named(Item item, Component name, Component... lines) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, plain(name));
		if (lines.length > 0) {
			stack.set(DataComponents.LORE, lore(lines));
		}
		return stack;
	}

	private static Component plain(Component c) {
		return c.copy().withStyle(s -> s.withItalic(false));
	}

	private static ItemLore lore(Component... lines) {
		List<Component> out = new ArrayList<>();
		for (Component line : lines) {
			out.add(plain(line));
		}
		return new ItemLore(out);
	}
}
//?}
