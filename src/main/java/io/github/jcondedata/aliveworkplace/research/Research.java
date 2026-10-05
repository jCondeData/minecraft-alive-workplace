package io.github.jcondedata.aliveworkplace.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The research tree: what a village's scholars can find out (kept in its Village Hall), each topic with levels that cost
 * paper, books and emeralds and take a scholar a while at the desk. Every level is a bonus for the whole village:
 * <ul>
 * <li>Swift Hands: every job 5% faster a level;</li>
 * <li>Hearth: the village's wellbeing 10% higher a level;</li>
 * <li>Drill: guards hit 10% harder a level;</li>
 * <li>Kinship (after Swift Hands I): one more Pokémon partner per worker a level;</li>
 * <li>Lore (after Hearth I): children who went to school start as Journeymen;</li>
 * <li>Architecture (after Swift Hands II and Hearth I): the scholars draw up the Town Hall blueprint;</li>
 * <li>Logistics (after Swift Hands I): porters carry 3 more stacks a level;</li>
 * <li>Craftsmanship (after Swift Hands I): carpenters, masons, tinkerers, chefs and the other crafters 15% faster a level;</li>
 * <li>Medicine (after Hearth I): villagers a third as likely to fall ill a level;</li>
 * <li>Fortification (after Drill I): guards turn aside one blow in ten a level;</li>
 * <li>Commerce (after Hearth I): one more trader on market days and mercenaries 3 emeralds cheaper a level;</li>
 * <li>Expeditions (after Logistics I): explorers and netherworkers back 20% sooner a level;</li>
 * <li>Green Thumb (after Hearth I): a layer less compost a bone meal a level.</li>
 * </ul>
 */
public final class Research {
	public enum Topic {
		SWIFT_HANDS(3, Items.FEATHER),
		HEARTH(2, Items.CAMPFIRE),
		DRILL(3, Items.IRON_SWORD),
		KINSHIP(2, Items.LEAD),
		LORE(1, Items.WRITABLE_BOOK),
		ARCHITECTURE(1, Items.BRICKS),
		LOGISTICS(2, Items.CHEST_MINECART),
		CRAFTSMANSHIP(2, Items.CRAFTING_TABLE),
		MEDICINE(2, Items.GOLDEN_APPLE),
		FORTIFICATION(2, Items.SHIELD),
		COMMERCE(2, Items.EMERALD),
		EXPEDITIONS(2, Items.COMPASS),
		GREEN_THUMB(2, Items.BONE_MEAL),
		WARDING(1, Items.OBSIDIAN);

		public final int maxLevel;
		public final Item icon;

		Topic(int maxLevel, Item icon) {
			this.maxLevel = maxLevel;
			this.icon = icon;
		}

		public String key() {
			return name().toLowerCase();
		}

		public Component title() {
			return Component.translatable("research.aliveworkplace." + key());
		}

		public Component effect() {
			return Component.translatable("research.aliveworkplace." + key() + ".effect");
		}

		/** Research points level {@code lvl} takes (a scholar makes a point a tick at the desk). */
		public int points(int lvl) {
			return POINTS * lvl;
		}

		/** Paper, books and emeralds level {@code lvl} costs. */
		public Cost cost(int lvl) {
			return new Cost(16 * lvl, 2 * (lvl - 1) + (this == ARCHITECTURE || this == LORE ? 4 : 0), 4 * lvl);
		}

		/** The topics (and levels) that must be done first. */
		public Map<Topic, Integer> needs() {
			return switch (this) {
				case KINSHIP -> Map.of(SWIFT_HANDS, 1);
				case LORE -> Map.of(HEARTH, 1);
				case ARCHITECTURE -> Map.of(SWIFT_HANDS, 2, HEARTH, 1);
				case LOGISTICS, CRAFTSMANSHIP -> Map.of(SWIFT_HANDS, 1);
				case MEDICINE, COMMERCE, GREEN_THUMB -> Map.of(HEARTH, 1);
				case FORTIFICATION -> Map.of(DRILL, 1);
				case WARDING -> Map.of(FORTIFICATION, 1);
				case EXPEDITIONS -> Map.of(LOGISTICS, 1);
				default -> Map.of();
			};
		}
	}

	/** What a level costs. */
	public record Cost(int paper, int books, int emeralds) {
		public Map<Item, Integer> items() {
			Map<Item, Integer> out = new java.util.LinkedHashMap<>();
			if (paper > 0) {
				out.put(Items.PAPER, paper);
			}
			if (books > 0) {
				out.put(Items.BOOK, books);
			}
			if (emeralds > 0) {
				out.put(Items.EMERALD, emeralds);
			}
			return out;
		}
	}

	/** Research points a level takes, times its level (two minutes of work for level I). */
	public static int POINTS = 2400;

	/**
	 * A village's research: the level of each topic, what's being researched now, how far along, and whether it's been
	 * paid for (the scholar takes the cost from the chests before starting).
	 */
	public record State(Map<String, Integer> levels, Optional<String> current, int progress, boolean paid) {
		public static final State EMPTY = new State(Map.of(), Optional.empty(), 0, false);
		/**
		 * Keys in {@link #levels} starting with this hold a research tree's topic in progress ({@code @<tree>/<topic>},
		 * 29.11), not a level; levels of the trees are kept under {@code <tree>/<topic>}.
		 */
		public static final String CURRENT = "@";
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("levels", Map.of()).forGetter(State::levels),
			Codec.STRING.optionalFieldOf("current").forGetter(State::current),
			Codec.INT.optionalFieldOf("progress", 0).forGetter(State::progress),
			Codec.BOOL.optionalFieldOf("paid", false).forGetter(State::paid)
		).apply(i, State::new));

		public int level(Topic topic) {
			return levels.getOrDefault(topic.key(), 0);
		}

		/** Whether a key of {@link #levels} is a level (of the scholars' tree or a Legend's), not a tree's work in progress. */
		public static boolean isLevel(String key) {
			return !key.startsWith(CURRENT);
		}

		/** Every level researched, in every tree. */
		public int totalLevels() {
			return levels.entrySet().stream().filter(e -> isLevel(e.getKey())).mapToInt(e -> Math.max(0, e.getValue())).sum();
		}

		@Nullable
		public Topic currentTopic() {
			return current.map(k -> {
				try {
					return Topic.valueOf(k.toUpperCase());
				} catch (IllegalArgumentException e) {
					return null;
				}
			}).orElse(null);
		}

		/** Whether {@code topic}'s next level can be taken up: not at its top, and what it needs is done. */
		public boolean available(Topic topic) {
			return level(topic) < topic.maxLevel && topic.needs().entrySet().stream().allMatch(e -> level(e.getKey()) >= e.getValue());
		}

		public State choose(Topic topic) {
			return new State(levels, Optional.of(topic.key()), 0, false);
		}

		public State paidFor() {
			return new State(levels, current, progress, true);
		}

		public State withProgress(int p) {
			return new State(levels, current, p, paid);
		}

		/** The current topic done: a level up, nothing being researched. */
		public State finish() {
			Topic topic = currentTopic();
			Map<String, Integer> next = new HashMap<>(levels);
			if (topic != null) {
				next.put(topic.key(), level(topic) + 1);
			}
			return new State(Map.copyOf(next), Optional.empty(), 0, false);
		}
	}

	private record Cached(State state, long until) {
	}

	private static final Map<Villager, Cached> CACHE = new WeakHashMap<>();

	/** The research of the village {@code villager} lives in (none without a Village Hall nearby). */
	public static State of(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return State.EMPTY;
		}
		long now = level.getGameTime();
		Cached cached = CACHE.get(villager);
		if (cached != null && now < cached.until()) {
			return cached.state();
		}
		State state = at(level, villager.blockPosition());
		CACHE.put(villager, new Cached(state, now + 200));
		return state;
	}

	/** The research of the village round {@code pos}. */
	public static State at(ServerLevel level, BlockPos pos) {
		return VillageHalls.nearest(level, pos)
			.map(hall -> level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity ? entity.research() : State.EMPTY)
			.orElse(State.EMPTY);
	}

	public static int level(Villager villager, Topic topic) {
		return of(villager).level(topic);
	}

	/** Forget what was looked up (tests). */
	public static void forget() {
		CACHE.clear();
	}

	/** "16 Paper, 2 Book, 4 Emerald". */
	public static Component describe(Cost cost) {
		return describe(cost.items());
	}

	/** "4× Paper, 1× Amethyst Shard" (also a research tree's cost, 29.11). */
	public static Component describe(Map<Item, Integer> cost) {
		net.minecraft.network.chat.MutableComponent out = Component.empty();
		boolean first = true;
		for (var e : cost.entrySet()) {
			if (!first) {
				out.append(", ");
			}
			first = false;
			out.append(Component.translatable("screen.aliveworkplace.hall.count", e.getValue(), e.getKey().getDescription()));
		}
		return out;
	}

	/** Blueprints the Architecture research draws up (kept out of the Blueprint Table until then). */
	public static final List<net.minecraft.resources.ResourceLocation> ARCHITECTURE_BLUEPRINTS =
		List.of(io.github.jcondedata.aliveworkplace.AliveWorkplace.id("research/town_hall"));

	private Research() {
	}
}
