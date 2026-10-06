//? if cobblemon {
package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonPropertyExtractor;
import com.cobblemon.mod.common.api.pokemon.egg.EggGroup;
import com.cobblemon.mod.common.api.pokemon.evolution.PreEvolution;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

/**
 * The Daycare Keeper's Pokémon (ROADMAP 28.12, see {@link DaycareKeepers}): her screen (row 1 your pair, how well they
 * get along and the eggs waiting; row 3 your party, click one then another to leave them as a pair), how well a pair
 * gets along by Cobblemon's species data, and the eggs: a real Cobbreeding egg (its own {@code givepokemonegg}
 * command) when Cobbreeding is installed, else the hatchling itself.
 */
public final class CobblemonDaycareKeeper implements DaycareKeepers.Breeding {
	public static final int INFO = 0;
	public static final int FIRST = 10, SECOND = 11, GET_ALONG = 13, EGGS = 15, TAKE_BACK = 16;
	public static final int FIRST_PARTY_SLOT = 27;
	static final double REACH = 8;
	public static final ResourceLocation EVERSTONE = ResourceLocation.fromNamespaceAndPath("cobblemon", "everstone");
	public static final ResourceLocation DESTINY_KNOT = ResourceLocation.fromNamespaceAndPath("cobblemon", "destiny_knot");
	private static final Stats[] STATS = {Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};

	private static final class State {
		@Nullable
		UUID pending;
	}

	@Override
	public void open(ServerPlayer player, Villager keeper) {
		if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.pokemon_trader.in_battle").withStyle(ChatFormatting.YELLOW));
			return;
		}
		DaycareKeepers.dawn(Players.level(player), keeper, keeper.getRandom());
		State state = new State();
		ChoiceMenu.open(player, Component.translatable("screen.aliveworkplace.daycare_keeper", keeper.getDisplayName()),
			p -> keeper.isAlive() && p.isAlive() && p.distanceTo(keeper) <= REACH,
			menu -> render(menu, player, keeper, state));
	}

	// The screen without showing it (tests).
	public static ChoiceMenu menuForTest(ServerPlayer player, Villager keeper) {
		State state = new State();
		return ChoiceMenu.detached(player, menu -> render(menu, player, keeper, state));
	}

	@Override
	public int getAlong(ServerLevel level, DaycareKeepers.Pair pair) {
		Pokemon a = load(level, pair.first());
		Pokemon b = load(level, pair.second());
		return a == null || b == null ? DaycareKeepers.NOT_AT_ALL : getAlong(a, b);
	}

	private static Set<EggGroup> groups(Pokemon pokemon) {
		return pokemon.getForm().getEggGroups();
	}

	private static boolean ditto(Pokemon pokemon) {
		return groups(pokemon).contains(EggGroup.DITTO);
	}

	/**
	 * How well {@code a} and {@code b} get along: very well (same species, different original trainers), well (same
	 * species, or an egg group in common), so-so (Ditto with anything that breeds), not at all (no group in common, the
	 * Undiscovered group, two Ditto, or not a mother and a father).
	 */
	public static int getAlong(Pokemon a, Pokemon b) {
		if (groups(a).contains(EggGroup.UNDISCOVERED) || groups(b).contains(EggGroup.UNDISCOVERED)) {
			return DaycareKeepers.NOT_AT_ALL;
		}
		if (ditto(a) || ditto(b)) {
			return ditto(a) && ditto(b) ? DaycareKeepers.NOT_AT_ALL : DaycareKeepers.SO_SO;
		}
		boolean parents = a.getGender() == Gender.MALE && b.getGender() == Gender.FEMALE || a.getGender() == Gender.FEMALE && b.getGender() == Gender.MALE;
		Set<EggGroup> common = new HashSet<>(groups(a));
		common.retainAll(groups(b));
		if (!parents || common.isEmpty()) {
			return DaycareKeepers.NOT_AT_ALL;
		}
		if (a.getSpecies() == b.getSpecies()) {
			return Objects.equals(a.getOriginalTrainer(), b.getOriginalTrainer()) ? DaycareKeepers.WELL : DaycareKeepers.VERY_WELL;
		}
		return DaycareKeepers.WELL;
	}

	/** The parent the egg takes after: the one that isn't Ditto, else the mother. */
	static Pokemon mother(Pokemon a, Pokemon b) {
		if (ditto(a)) {
			return b;
		}
		if (ditto(b)) {
			return a;
		}
		return b.getGender() == Gender.FEMALE ? b : a;
	}

	private static boolean holds(Pokemon pokemon, ResourceLocation item) {
		ItemStack held = pokemon.heldItem();
		return !held.isEmpty() && BuiltInRegistries.ITEM.getKey(held.getItem()).equals(item);
	}

	/**
	 * The hatchling of {@code a} and {@code b}, at level 1: the base form of the mother (or the parent that isn't Ditto),
	 * 3 IVs from the parents (5 when one holds a Destiny Knot), the nature of a parent holding an Everstone, the mother's
	 * ball, a 1 in 5 chance of a hidden ability the mother has, and the egg moves both parents know.
	 */
	public static Pokemon hatchling(Pokemon a, Pokemon b, RandomSource random) {
		Pokemon mother = mother(a, b);
		Species species = mother.getSpecies();
		FormData form = mother.getForm();
		for (int i = 0; i < 8; i++) {
			PreEvolution pre = form.getPreEvolution();
			if (pre == null) {
				break;
			}
			species = pre.getSpecies();
			form = pre.getForm();
		}
		Pokemon baby = PokemonProperties.Companion.parse(species.getResourceIdentifier().toString() + " level=1", " ", "=").create();
		if (form != baby.getForm() && species.getForms().contains(form)) {
			baby.setForm(form);
		}
		// IVs: 3 (5 with a Destiny Knot) stats, each from one parent
		int inherited = holds(a, DESTINY_KNOT) || holds(b, DESTINY_KNOT) ? 5 : 3;
		List<Stats> stats = new ArrayList<>(List.of(STATS));
		for (int i = 0; i < inherited; i++) {
			Stats stat = stats.remove(random.nextInt(stats.size()));
			Pokemon from = random.nextBoolean() ? a : b;
			baby.getIvs().set(stat, from.getIvs().getOrDefault(stat));
		}
		// Nature: a parent holding an Everstone
		List<Pokemon> everstone = new ArrayList<>();
		for (Pokemon parent : List.of(a, b)) {
			if (holds(parent, EVERSTONE)) {
				everstone.add(parent);
			}
		}
		if (!everstone.isEmpty()) {
			baby.setNature(everstone.get(random.nextInt(everstone.size())).getNature());
		}
		baby.setCaughtBall(mother.getCaughtBall());
		// A hidden ability the mother has: 1 in 5
		if (mother.getAbility().getPriority() == Priority.LOW && random.nextInt(5) == 0) {
			for (PotentialAbility potential : baby.getForm().getAbilities()) {
				if (potential.getPriority() == Priority.LOW) {
					baby.updateAbility(potential.getTemplate().create(false, Priority.LOW));
					break;
				}
			}
		}
		// Egg moves both parents know
		Set<String> known = moveNames(a);
		known.retainAll(moveNames(b));
		int replace = 0;
		for (MoveTemplate move : baby.getForm().getMoves().getEggMoves()) {
			if (!known.contains(move.getName()) || moveNames(baby).contains(move.getName())) {
				continue;
			}
			if (baby.getMoveSet().hasSpace()) {
				baby.getMoveSet().add(move.create());
			} else {
				baby.getMoveSet().setMove(replace++ % 4, move.create());
			}
		}
		baby.heal();
		return baby;
	}

	private static Set<String> moveNames(Pokemon pokemon) {
		Set<String> out = new HashSet<>();
		for (Move move : pokemon.getMoveSet().getMoves()) {
			out.add(move.getName());
		}
		return out;
	}

	public static boolean cobbreeding() {
		return Platform.get().isModLoaded("cobbreeding");
	}

	/**
	 * One egg of the pair for {@code player}: with Cobbreeding a Cobbreeding egg made by its own {@code givepokemonegg}
	 * (the species, form, IVs, nature, ability and ball the hatchling would have; Cobbreeding hatches it and rolls its
	 * shininess), else the hatchling itself, to the party or the PC. Returns the hatchling's name, or null if it failed.
	 */
	@Nullable
	public static Component giveEgg(ServerPlayer player, Pokemon a, Pokemon b, RandomSource random) {
		return giveEgg(player, a, b, random, cobbreeding());
	}

	/** {@link #giveEgg}, as a Cobbreeding egg or not. */
	@Nullable
	public static Component giveEgg(ServerPlayer player, Pokemon a, Pokemon b, RandomSource random, boolean asCobbreedingEgg) {
		Pokemon baby = hatchling(a, b, random);
		if (asCobbreedingEgg) {
			PokemonProperties properties = baby.createPokemonProperties(List.of(PokemonPropertyExtractor.SPECIES, PokemonPropertyExtractor.FORM,
				PokemonPropertyExtractor.IVS, PokemonPropertyExtractor.NATURE, PokemonPropertyExtractor.ABILITY, PokemonPropertyExtractor.POKEBALL));
			var server = Players.level(player).getServer();
			// Run as the player at full permission, aimed at @s (a UUID isn't taken as a player by its selector)
			var source = player.createCommandSourceStack().withSuppressedOutput().withPermission(4);
			int before = countEggs(player);
			String command = "givepokemonegg @s " + properties.asString(" ");
			String failure = "";
			try {
				server.getCommands().getDispatcher().execute(command, source);
			} catch (com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException e) {
				failure = e.getMessage();
			}
			if (countEggs(player) <= before) {
				AliveWorkplace.LOG.warn("Cobbreeding gave no egg for {} ({}): {}", player.getGameProfile().getName(), command, failure);
				return null;
			}
			return Component.translatable("message.aliveworkplace.daycare_keeper.egg", baby.getSpecies().getTranslatedName());
		}
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		if (!party.add(baby)) {
			Cobblemon.INSTANCE.getStorage().getPC(player).add(baby);
		}
		return Component.translatable("message.aliveworkplace.daycare_keeper.hatched", baby.getDisplayName(false));
	}

	/** Cobbreeding's egg items in {@code player}'s inventory. */
	public static int countEggs(ServerPlayer player) {
		int n = 0;
		for (ItemStack stack : player.getInventory().items) {
			if (!stack.isEmpty() && isCobbreedingEgg(stack)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	public static boolean isCobbreedingEgg(ItemStack stack) {
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return id.getNamespace().equals("cobbreeding") && id.getPath().endsWith("egg");
	}

	private static void render(ChoiceMenu menu, ServerPlayer player, Villager keeper, State state) {
		menu.clearButtons();
		ServerLevel level = Players.level(player);
		List<DaycareKeepers.Pair> pairs = DaycareKeepers.pairs(keeper);
		ItemStack info = named(Items.EGG, Component.translatable("screen.aliveworkplace.daycare_keeper", keeper.getDisplayName()));
		info.set(DataComponents.LORE, lore(
			Component.translatable("screen.aliveworkplace.daycare_keeper.info", DaycareKeepers.MAX_PAIRS).withStyle(ChatFormatting.GRAY),
			Component.translatable("screen.aliveworkplace.daycare_keeper.pairs", pairs.size(), DaycareKeepers.MAX_PAIRS).withStyle(ChatFormatting.DARK_GRAY),
			Component.translatable("screen.aliveworkplace.daycare_keeper.odds", DaycareKeepers.chance(keeper, DaycareKeepers.VERY_WELL),
				DaycareKeepers.chance(keeper, DaycareKeepers.WELL),
				DaycareKeepers.chance(keeper, DaycareKeepers.SO_SO)).withStyle(ChatFormatting.DARK_GRAY)));
		menu.button(INFO, info, null);
		// Row 1: your pair
		DaycareKeepers.Pair mine = pairs.stream().filter(p -> p.owner().equals(player.getUUID())).findFirst().orElse(null);
		if (mine != null) {
			Pokemon a = load(level, mine.first());
			Pokemon b = load(level, mine.second());
			if (a != null && b != null) {
				menu.button(FIRST, icon(a), null);
				menu.button(SECOND, icon(b), null);
				int along = getAlong(a, b);
				menu.button(GET_ALONG, getAlongIcon(along, keeper), null);
				int price = DaycareKeepers.EGG_PRICE * mine.eggs();
				ItemStack eggs = named(Items.EGG, Component.translatable("screen.aliveworkplace.daycare_keeper.eggs", mine.eggs()));
				eggs.setCount(Math.max(1, mine.eggs()));
				eggs.set(DataComponents.LORE, lore(mine.eggs() == 0
					? Component.translatable("screen.aliveworkplace.daycare_keeper.no_eggs").withStyle(ChatFormatting.GRAY)
					: Component.translatable("screen.aliveworkplace.daycare_keeper.collect",
						Money.describe((long) price * Money.DOLLARS_PER_EMERALD, price)).withStyle(ChatFormatting.GREEN)));
				menu.button(EGGS, eggs, mine.eggs() == 0 ? null : p -> {
					Chat.chat(p, collect(p, keeper));
					render(menu, player, keeper, state);
				});
				ItemStack back = named(Items.LEAD, Component.translatable("screen.aliveworkplace.daycare_keeper.take_back"));
				back.set(DataComponents.LORE, lore(mine.eggs() > 0
					? Component.translatable("screen.aliveworkplace.daycare_keeper.collect_first").withStyle(ChatFormatting.RED)
					: Component.translatable("screen.aliveworkplace.daycare_keeper.take_back.lore").withStyle(ChatFormatting.GRAY)));
				menu.button(TAKE_BACK, back, mine.eggs() > 0 ? null : p -> {
					Chat.chat(p, takeBack(p, keeper));
					render(menu, player, keeper, state);
				});
			}
		}
		menu.divider(2);
		// Row 3: your party
		List<Pokemon> party = new ArrayList<>();
		for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
			party.add(pokemon);
		}
		Pokemon pendingPokemon = party.stream().filter(p -> p.getUuid().equals(state.pending)).findFirst().orElse(null);
		for (int i = 0; i < party.size() && i < 6; i++) {
			Pokemon pokemon = party.get(i);
			Component refusal = mine != null ? Component.translatable("screen.aliveworkplace.daycare_keeper.have_pair")
				: pairs.size() >= DaycareKeepers.MAX_PAIRS ? Component.translatable("screen.aliveworkplace.daycare_keeper.full", DaycareKeepers.MAX_PAIRS)
				: party.size() <= 2 ? Component.translatable("screen.aliveworkplace.daycare_keeper.last") : null;
			boolean pending = pokemon == pendingPokemon;
			ItemStack icon = PokemonItem.from(pokemon);
			icon.set(DataComponents.CUSTOM_NAME, plain(Component.translatable("message.aliveworkplace.tutor.pokemon", pokemon.getDisplayName(false),
				pokemon.getLevel()).withStyle(ChatFormatting.WHITE)));
			Component line;
			if (refusal != null) {
				line = refusal.copy().withStyle(ChatFormatting.RED);
			} else if (pending) {
				line = Component.translatable("screen.aliveworkplace.daycare_keeper.picked").withStyle(ChatFormatting.YELLOW);
			} else if (pendingPokemon != null) {
				int along = getAlong(pendingPokemon, pokemon);
				line = Component.translatable("screen.aliveworkplace.daycare_keeper.with", pendingPokemon.getDisplayName(false),
					Component.translatable("screen.aliveworkplace.daycare_keeper.get_along." + DaycareKeepers.GET_ALONG[along]))
					.withStyle(along == DaycareKeepers.NOT_AT_ALL ? ChatFormatting.RED : ChatFormatting.GREEN);
			} else {
				line = Component.translatable("screen.aliveworkplace.daycare_keeper.pick").withStyle(ChatFormatting.GREEN);
			}
			icon.set(DataComponents.LORE, lore(line));
			if (pending) {
				icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			menu.button(FIRST_PARTY_SLOT + i, icon, refusal != null ? null : p -> {
				if (state.pending == null || pokemon.getUuid().equals(state.pending)) {
					state.pending = pokemon.getUuid().equals(state.pending) ? null : pokemon.getUuid();
				} else {
					Pokemon first = pendingPokemon;
					state.pending = null;
					if (first != null) {
						leave(p, keeper, first, pokemon);
					}
				}
				render(menu, player, keeper, state);
			});
		}
	}

	private static ItemStack icon(Pokemon pokemon) {
		ItemStack icon = PokemonItem.from(pokemon);
		icon.set(DataComponents.CUSTOM_NAME, plain(Component.translatable("message.aliveworkplace.tutor.pokemon", pokemon.getDisplayName(false),
			pokemon.getLevel()).withStyle(ChatFormatting.WHITE)));
		return icon;
	}

	private static ItemStack getAlongIcon(int along, Villager keeper) {
		ItemStack icon = named(along == DaycareKeepers.NOT_AT_ALL ? Items.DEAD_BUSH : along == DaycareKeepers.SO_SO ? Items.DANDELION
				: along == DaycareKeepers.WELL ? Items.POPPY : Items.ROSE_BUSH,
			Component.translatable("screen.aliveworkplace.daycare_keeper.get_along." + DaycareKeepers.GET_ALONG[along])
				.withStyle(along == DaycareKeepers.NOT_AT_ALL ? ChatFormatting.RED : ChatFormatting.LIGHT_PURPLE));
		icon.set(DataComponents.LORE, lore(Component.translatable("screen.aliveworkplace.daycare_keeper.chance",
			DaycareKeepers.chance(keeper, along)).withStyle(ChatFormatting.GRAY)));
		return icon;
	}

	/** {@code player} leaves {@code a} and {@code b} with the keeper as their pair. */
	public static boolean leave(ServerPlayer player, Villager keeper, Pokemon a, Pokemon b) {
		List<DaycareKeepers.Pair> pairs = new ArrayList<>(DaycareKeepers.pairs(keeper));
		if (a == b || BattleRegistry.getBattleByParticipatingPlayer(player) != null || pairs.size() >= DaycareKeepers.MAX_PAIRS
			|| pairs.stream().anyMatch(p -> p.owner().equals(player.getUUID()))) {
			return false;
		}
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		int count = 0;
		boolean hasA = false, hasB = false;
		for (Pokemon member : party) {
			count++;
			hasA |= member == a;
			hasB |= member == b;
		}
		if (!hasA || !hasB || count <= 2) {
			return false;
		}
		for (Pokemon pokemon : List.of(a, b)) {
			if (pokemon.getEntity() != null) {
				pokemon.recall();
			}
		}
		if (!party.remove(a)) {
			return false;
		}
		if (!party.remove(b)) {
			party.add(a);
			return false;
		}
		pairs.add(new DaycareKeepers.Pair(player.getUUID(), player.getGameProfile().getName(),
			a.saveToNBT(player.registryAccess(), new CompoundTag()), b.saveToNBT(player.registryAccess(), new CompoundTag()),
			0, DaycareKeepers.day(Players.level(player))));
		DaycareKeepers.setPairs(keeper, pairs);
		Chat.chat(player, Component.translatable("message.aliveworkplace.daycare_keeper.left", a.getDisplayName(false), b.getDisplayName(false),
			keeper.getDisplayName(), Component.translatable("screen.aliveworkplace.daycare_keeper.get_along." + DaycareKeepers.GET_ALONG[getAlong(a, b)]))
			.withStyle(ChatFormatting.GREEN));
		keeper.level().playSound(null, keeper, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		return true;
	}

	/** {@code player} collects the eggs waiting for their pair, {@link DaycareKeepers#EGG_PRICE} emeralds each. */
	public static Component collect(ServerPlayer player, Villager keeper) {
		ServerLevel level = Players.level(player);
		List<DaycareKeepers.Pair> pairs = new ArrayList<>(DaycareKeepers.pairs(keeper));
		int index = -1;
		for (int i = 0; i < pairs.size(); i++) {
			if (pairs.get(i).owner().equals(player.getUUID())) {
				index = i;
			}
		}
		if (index < 0 || pairs.get(index).eggs() <= 0) {
			return Component.empty();
		}
		DaycareKeepers.Pair pair = pairs.get(index);
		Pokemon a = load(level, pair.first());
		Pokemon b = load(level, pair.second());
		if (a == null || b == null) {
			return Component.translatable("message.aliveworkplace.daycare.lost").withStyle(ChatFormatting.RED);
		}
		int price = DaycareKeepers.EGG_PRICE;
		int collected = 0;
		List<Component> names = new ArrayList<>();
		for (int i = 0; i < pair.eggs(); i++) {
			if (!Money.charge(player, (long) price * Money.DOLLARS_PER_EMERALD, price)) {
				break;
			}
			Component name = giveEgg(player, a, b, keeper.getRandom());
			if (name == null) {
				Money.pay(player, (long) price * Money.DOLLARS_PER_EMERALD, price);
				break;
			}
			names.add(name);
			collected++;
		}
		pairs.set(index, pair.withEggs(pair.eggs() - collected, pair.day()));
		DaycareKeepers.setPairs(keeper, pairs);
		if (collected == 0) {
			return Component.translatable("message.aliveworkplace.daycare_keeper.cant_afford", Money.describe((long) price * Money.DOLLARS_PER_EMERALD, price))
				.withStyle(ChatFormatting.RED);
		}
		keeper.level().playSound(null, keeper, SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 1f, 1f);
		Component list = names.get(0);
		for (int i = 1; i < names.size(); i++) {
			list = Component.translatable("message.aliveworkplace.daycare_keeper.and", list, names.get(i));
		}
		return Component.translatable("message.aliveworkplace.daycare_keeper.collected", list).withStyle(ChatFormatting.GREEN);
	}

	/** {@code player} takes their pair back (no eggs waiting). */
	public static Component takeBack(ServerPlayer player, Villager keeper) {
		List<DaycareKeepers.Pair> pairs = new ArrayList<>(DaycareKeepers.pairs(keeper));
		DaycareKeepers.Pair pair = pairs.stream().filter(p -> p.owner().equals(player.getUUID())).findFirst().orElse(null);
		if (pair == null || pair.eggs() > 0) {
			return Component.empty();
		}
		pairs.remove(pair);
		DaycareKeepers.setPairs(keeper, pairs);
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		for (CompoundTag tag : List.of(pair.first(), pair.second())) {
			Pokemon pokemon = load(Players.level(player), tag);
			if (pokemon != null && !party.add(pokemon)) {
				Cobblemon.INSTANCE.getStorage().getPC(player).add(pokemon);
			}
		}
		return Component.translatable("message.aliveworkplace.daycare_keeper.taken_back").withStyle(ChatFormatting.GREEN);
	}

	@Override
	public void returnAll(ServerLevel level, Villager keeper) {
		for (DaycareKeepers.Pair pair : DaycareKeepers.pairs(keeper)) {
			for (CompoundTag tag : List.of(pair.first(), pair.second())) {
				Pokemon pokemon = load(level, tag);
				if (pokemon == null) {
					continue;
				}
				try {
					Cobblemon.INSTANCE.getStorage().getPC(pair.owner(), level.registryAccess()).add(pokemon);
				} catch (RuntimeException e) {
					AliveWorkplace.LOG.error("Could not return {}'s Pokémon from the daycare keeper", pair.ownerName(), e);
				}
			}
		}
		DaycareKeepers.setPairs(keeper, List.of());
	}

	@Nullable
	public static Pokemon load(ServerLevel level, CompoundTag tag) {
		try {
			return Pokemon.Companion.loadFromNBT(level.registryAccess(), tag.copy());
		} catch (RuntimeException e) {
			AliveWorkplace.LOG.error("A Pokémon at the daycare keeper's could not be read", e);
			return null;
		}
	}

	private static ItemStack named(net.minecraft.world.item.Item item, Component name) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, plain(name));
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
