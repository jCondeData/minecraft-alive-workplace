package io.github.jcondedata.aliveworkplace.compat.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokemon.moves.Learnset;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.tutor.Tutors;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

/**
 * The Cobblemon half of Move Tutors: which moves a Pokémon could be taught, the lesson screen, and
 * teaching. A taught move goes straight into the Pokémon's moves when it knows fewer than four, otherwise
 * into the moves it can swap in from its summary (like any move it has learned before).
 */
public final class CobblemonTutors {
	/** Moves shown per page. */
	private static final int PAGE = 27;
	public static final int FIRST_MOVE_SLOT = 18;
	private static final int PREVIOUS = 45;
	private static final int INFO = 49;
	private static final int NEXT = 53;

	/** A move a tutor could teach: how hard it is (1..5) and whether it's an egg move. */
	public record Lesson(MoveTemplate move, boolean egg, int grade) {
		public int price() {
			return Tutors.price(grade);
		}

		public long dollars() {
			return Tutors.dollars(grade);
		}
	}

	/**
	 * The moves this Pokémon could be taught that it can't use already (tutor and TM moves, then egg
	 * moves), easiest first.
	 */
	public static List<Lesson> lessons(Pokemon pokemon) {
		Learnset learnset = pokemon.getForm().getMoves();
		Set<String> known = new HashSet<>();
		for (MoveTemplate move : pokemon.getAllAccessibleMoves()) {
			known.add(move.getName());
		}
		for (Move move : pokemon.getMoveSet().getMoves()) {
			known.add(move.getName());
		}
		Map<String, Lesson> lessons = new LinkedHashMap<>();
		List<MoveTemplate> taught = new ArrayList<>(learnset.getTutorMoves());
		taught.addAll(learnset.getTmMoves());
		for (MoveTemplate move : taught) {
			if (!known.contains(move.getName())) {
				lessons.putIfAbsent(move.getName(), new Lesson(move, false, Tutors.grade(move.getPower(), false)));
			}
		}
		for (MoveTemplate move : learnset.getEggMoves()) {
			if (!known.contains(move.getName())) {
				lessons.putIfAbsent(move.getName(), new Lesson(move, true, Tutors.grade(move.getPower(), true)));
			}
		}
		List<Lesson> sorted = new ArrayList<>(lessons.values());
		sorted.sort(Comparator.comparingInt(Lesson::grade).thenComparing(l -> l.move().getName()));
		return sorted;
	}

	/** Teaches {@code move}: into an empty move slot if there is one, else among the moves to swap in. */
	public static boolean teach(Pokemon pokemon, MoveTemplate move) {
		if (pokemon.getMoveSet().hasSpace()) {
			pokemon.getMoveSet().add(move.create());
			return true;
		}
		pokemon.getBenchedMoves().add(new BenchedMove(move, 0));
		return false;
	}

	/** What's on the screen: whose moves, which page, and a lesson waiting for the second click. */
	private static final class State {
		@Nullable
		UUID pokemon;
		int page;
		@Nullable
		String pending;
	}

	public static void open(ServerPlayer player, Villager tutor) {
		if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.tutor.in_battle").withStyle(ChatFormatting.YELLOW));
			return;
		}
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		if (party.occupied() == 0) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.tutor.no_pokemon").withStyle(ChatFormatting.YELLOW));
			return;
		}
		State state = new State();
		ChoiceMenu.open(player, Tutors.title(tutor),
			p -> tutor.isAlive() && !tutor.isSleeping() && p.isAlive() && p.distanceTo(tutor) <= Tutors.REACH,
			menu -> render(menu, player, tutor, state));
	}

	/** The lesson screen without showing it (tests). */
	public static ChoiceMenu menuForTest(ServerPlayer player, Villager tutor) {
		State state = new State();
		return ChoiceMenu.detached(player, menu -> render(menu, player, tutor, state));
	}

	/** Row 1: the party. Row 2: a divider. Rows 3–5: the chosen Pokémon's lessons. Row 6: pages and info. */
	private static void render(ChoiceMenu menu, ServerPlayer player, Villager tutor, State state) {
		menu.clearButtons();
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		List<Pokemon> members = new ArrayList<>();
		for (Pokemon pokemon : party) {
			members.add(pokemon);
		}
		Pokemon chosen = members.stream().filter(p -> p.getUuid().equals(state.pokemon)).findFirst()
			.orElse(members.isEmpty() ? null : members.get(0));
		if (chosen != null && !chosen.getUuid().equals(state.pokemon)) {
			state.pokemon = chosen.getUuid();
			state.page = 0;
			state.pending = null;
		}
		int tier = Tutors.tier(tutor);
		for (int i = 0; i < members.size() && i < 6; i++) {
			Pokemon pokemon = members.get(i);
			List<Lesson> lessons = lessons(pokemon);
			long teachable = lessons.stream().filter(l -> l.grade() <= tier).count();
			ItemStack icon = PokemonItem.from(pokemon);
			icon.set(DataComponents.CUSTOM_NAME, Component.translatable("message.aliveworkplace.tutor.pokemon",
				pokemon.getDisplayName(false), pokemon.getLevel()).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.WHITE)));
			icon.set(DataComponents.LORE, lore(
				teachable > 0
					? Component.translatable("message.aliveworkplace.tutor.can_learn", teachable).withStyle(ChatFormatting.GREEN)
					: Component.translatable("message.aliveworkplace.tutor.nothing_to_learn").withStyle(ChatFormatting.GRAY),
				lessons.size() > teachable
					? Component.translatable("message.aliveworkplace.tutor.more_later", lessons.size() - teachable).withStyle(ChatFormatting.DARK_GRAY)
					: null));
			if (pokemon == chosen) {
				icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			menu.button(i, icon, p -> {
				state.pokemon = pokemon.getUuid();
				state.page = 0;
				state.pending = null;
				render(menu, player, tutor, state);
			});
		}
		menu.divider(1);

		List<Lesson> lessons = chosen == null ? List.of() : lessons(chosen);
		int pages = Math.max(1, (lessons.size() + PAGE - 1) / PAGE);
		state.page = Math.max(0, Math.min(pages - 1, state.page));
		for (int i = 0; i < PAGE; i++) {
			int index = state.page * PAGE + i;
			if (index >= lessons.size()) {
				break;
			}
			Lesson lesson = lessons.get(index);
			boolean canTeach = lesson.grade() <= tier;
			boolean pending = lesson.move().getName().equals(state.pending);
			menu.button(FIRST_MOVE_SLOT + i, lessonIcon(lesson, chosen, canTeach, pending,
				io.github.jcondedata.aliveworkplace.work.Money.canAfford(player, lesson.dollars(), lesson.price())),
				!canTeach ? null : p -> {
					if (!lesson.move().getName().equals(state.pending)) {
						state.pending = lesson.move().getName();
					} else {
						state.pending = null;
						give(p, tutor, chosen, lesson);
					}
					render(menu, player, tutor, state);
				});
		}
		if (state.page > 0) {
			menu.button(PREVIOUS, named(Items.ARROW, Component.translatable("message.aliveworkplace.tutor.previous")), p -> {
				state.page--;
				state.pending = null;
				render(menu, player, tutor, state);
			});
		}
		if (state.page < pages - 1) {
			menu.button(NEXT, named(Items.ARROW, Component.translatable("message.aliveworkplace.tutor.next")), p -> {
				state.page++;
				state.pending = null;
				render(menu, player, tutor, state);
			});
		}
		ItemStack info = named(Items.BOOK, Tutors.title(tutor));
		info.set(DataComponents.LORE, lore(
			Component.translatable("message.aliveworkplace.tutor.info_level", BuilderLevels.levelName(tier)).withStyle(ChatFormatting.GRAY),
			Component.translatable("message.aliveworkplace.tutor.info_money", io.github.jcondedata.aliveworkplace.work.Money.balance(player))
				.withStyle(ChatFormatting.GREEN),
			lessons.isEmpty() ? null : Component.translatable("message.aliveworkplace.tutor.info_page", state.page + 1, pages).withStyle(ChatFormatting.DARK_GRAY)));
		menu.button(INFO, info, null);
	}

	private static ItemStack lessonIcon(Lesson lesson, @Nullable Pokemon pokemon, boolean canTeach, boolean pending, boolean affordable) {
		MoveTemplate move = lesson.move();
		Item item = !canTeach ? Items.PAPER : lesson.egg() ? Items.EGG : Items.BOOK;
		MutableComponent name = move.getDisplayName().copy();
		ItemStack icon = named(item, canTeach ? name.withColor(move.getElementalType().getHue()) : name.withStyle(ChatFormatting.GRAY));
		List<Component> lines = new ArrayList<>();
		MutableComponent stats = move.getElementalType().getDisplayName().copy().withColor(move.getElementalType().getHue())
			.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
			.append(move.getDamageCategory().getDisplayName().copy().withStyle(ChatFormatting.GRAY));
		if (move.getPower() > 0) {
			stats.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.translatable("message.aliveworkplace.tutor.power", (int) move.getPower()).withStyle(ChatFormatting.GRAY));
		}
		if (move.getAccuracy() > 0) {
			stats.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.translatable("message.aliveworkplace.tutor.accuracy", (int) move.getAccuracy()).withStyle(ChatFormatting.GRAY));
		}
		lines.add(stats);
		if (lesson.egg()) {
			lines.add(Component.translatable("message.aliveworkplace.tutor.egg_move").withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		if (!canTeach) {
			lines.add(Component.translatable("message.aliveworkplace.tutor.needs_tier", BuilderLevels.levelName(lesson.grade())).withStyle(ChatFormatting.RED));
		} else {
			lines.add(Component.translatable("message.aliveworkplace.tutor.price",
				io.github.jcondedata.aliveworkplace.work.Money.describe(lesson.dollars(), lesson.price()))
				.withStyle(affordable ? ChatFormatting.GREEN : ChatFormatting.RED));
			lines.add(pending && pokemon != null
				? Component.translatable("message.aliveworkplace.tutor.confirm", move.getDisplayName(), pokemon.getDisplayName(false)).withStyle(ChatFormatting.YELLOW)
				: Component.translatable("message.aliveworkplace.tutor.click").withStyle(ChatFormatting.GRAY));
		}
		icon.set(DataComponents.LORE, new ItemLore(lines.stream().map(CobblemonTutors::plain).toList()));
		if (pending) {
			icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return icon;
	}

	/** Pays for and gives a lesson (the second click). */
	static void give(ServerPlayer player, Villager tutor, @Nullable Pokemon pokemon, Lesson lesson) {
		if (pokemon == null || BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
			return;
		}
		boolean inParty = false;
		for (Pokemon member : Cobblemon.INSTANCE.getStorage().getParty(player)) {
			inParty |= member.getUuid().equals(pokemon.getUuid());
		}
		if (!inParty || lesson.grade() > Tutors.tier(tutor) || !lessons(pokemon).stream().anyMatch(l -> l.move().getName().equals(lesson.move().getName()))) {
			return;
		}
		if (!io.github.jcondedata.aliveworkplace.work.Money.charge(player, lesson.dollars(), lesson.price())) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.tutor.too_poor",
				io.github.jcondedata.aliveworkplace.work.Money.describe(lesson.dollars(), lesson.price())).withStyle(ChatFormatting.RED));
			return;
		}
		boolean inMoves = teach(pokemon, lesson.move());
		player.sendSystemMessage(Component.translatable(inMoves ? "message.aliveworkplace.tutor.learned" : "message.aliveworkplace.tutor.learned_benched",
			pokemon.getDisplayName(false), lesson.move().getDisplayName()).withStyle(ChatFormatting.GREEN));
		tutor.level().playSound(null, tutor, SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.5f, 1.4f);
		Tutors.taught(player, tutor, lesson.grade());
	}

	private static ItemStack named(Item item, Component name) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, plain(name));
		return stack;
	}

	/** Item names and lore are italic unless told otherwise. */
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

	private CobblemonTutors() {
	}
}
