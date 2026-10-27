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
 * <li>Green Thumb (after Hearth I): a layer less compost a bone meal a level;</li>
 * <li>Ramparts (after Fortification I): in a siege the gates have twice the hit points and archers on battle stations reach 28 blocks.</li>
 * </ul>
 * While a Great Library stands in the village (35.14, its {@code research_tier} power: {@link #SECOND_TIER}) a second
 * tier opens, each level {@link Cost#library} and twice the points: Swift Hands IV and V, Scholarship, Engineering,
 * Cartography, Trade Law and Pharmacy. A village keeps those levels if the Library is damaged later, but their bonuses
 * pause with its powers ({@link #bonus}): Swift Hands counts to III again, the others to nothing.
 */
public final class Research {
	public enum Topic {
		SWIFT_HANDS(3, 2, Items.FEATHER), // IV and V with the Great Library (35.14)
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
		WARDING(1, Items.OBSIDIAN),
		/** Gates with twice the hit points in a siege, and archers on battle stations reach 28 blocks (32.6). */
		RAMPARTS(1, Items.STONE_BRICK_WALL),
		// The Great Library's second tier (35.14): a level each, researched only while it stands.
		/** Scholars 25% faster a level; the second level makes research cost a quarter less. */
		SCHOLARSHIP(0, 2, Items.LECTERN),
		/** Builders' bags hold 9 more stacks a level, and a Wonder's crew takes 2 more builders a level. */
		ENGINEERING(0, 2, Items.PISTON),
		/** Explorers range twice as far, and their maps can lead to trial chambers and ancient cities. */
		CARTOGRAPHY(0, 1, Items.FILLED_MAP),
		/** Caravans carry 2 more stacks a level, and the treasury takes 10% more a level. */
		TRADE_LAW(0, 2, Items.GOLD_INGOT),
		/** The ill get well in a day, and each Nurse cures one villager a day without a remedy. */
		PHARMACY(0, 1, Items.BREWING_STAND);

		/** The first tier's top level (0 for a topic of the second tier only). */
		public final int maxLevel;
		/** The levels above {@link #maxLevel} that need the Great Library (35.14): Swift Hands IV and V, all of a second-tier topic's. */
		public final int libraryLevels;
		public final Item icon;
		private final String key;

		Topic(int maxLevel, Item icon) {
			this(maxLevel, 0, icon);
		}

		Topic(int maxLevel, int libraryLevels, Item icon) {
			this.maxLevel = maxLevel;
			this.libraryLevels = libraryLevels;
			this.icon = icon;
			this.key = name().toLowerCase(); // B412: asked for on every bonus lookup (a builder's every tick, every pace)
		}

		/** The topic's top level with the second tier open. */
		public int top() {
			return maxLevel + libraryLevels;
		}

		/** Whether level {@code lvl} of this topic is one of the Great Library's second tier. */
		public boolean libraryLevel(int lvl) {
			return lvl > maxLevel;
		}

		/** Whether the topic has levels in the second tier (shown in the Great Library's row). */
		public boolean inSecondTier() {
			return libraryLevels > 0;
		}

		/** Whether the topic has levels in the first tier (shown in the scholars' rows). */
		public boolean inFirstTier() {
			return maxLevel > 0;
		}

		public String key() {
			return key;
		}

		public Component title() {
			return Component.translatable("research.aliveworkplace." + key());
		}

		public Component effect() {
			return Component.translatable("research.aliveworkplace." + key() + ".effect");
		}

		/** Research points level {@code lvl} takes (a scholar makes a point a tick at the desk); twice as many in the second tier. */
		public int points(int lvl) {
			return POINTS * lvl * (libraryLevel(lvl) ? 2 : 1);
		}

		/** Paper, books and emeralds level {@code lvl} costs ({@link Cost#library} in the second tier). */
		public Cost cost(int lvl) {
			if (libraryLevel(lvl)) {
				return Cost.library(lvl);
			}
			return new Cost(16 * lvl, 2 * (lvl - 1) + (this == ARCHITECTURE || this == LORE ? 4 : 0), 4 * lvl);
		}

		/**
		 * What level {@code lvl} costs in {@code hall}'s village: a quarter less with a founded Scholars' Guild (30.19), and
		 * a quarter less again with Scholarship II while the Great Library stands (35.14).
		 */
		public Cost cost(int lvl, ServerLevel level, @Nullable VillageHallBlockEntity hall) {
			Cost usual = cost(lvl);
			Cost guild = new Cost(io.github.jcondedata.aliveworkplace.hall.Guilds.researchCost(level, hall, usual.paper()),
				io.github.jcondedata.aliveworkplace.hall.Guilds.researchCost(level, hall, usual.books()),
				io.github.jcondedata.aliveworkplace.hall.Guilds.researchCost(level, hall, usual.emeralds()));
			return hall != null && bonus(level, hall.getBlockPos(), hall.research(), SCHOLARSHIP) >= 2 ? guild.scholarship() : guild;
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
				case WARDING, RAMPARTS -> Map.of(FORTIFICATION, 1);
				case EXPEDITIONS -> Map.of(LOGISTICS, 1);
				default -> Map.of();
			};
		}
	}

	/** What a level costs. */
	public record Cost(int paper, int books, int emeralds) {
		/** A second-tier level (35.14): 24 paper, 6 books and 8 emeralds times its level. */
		public static Cost library(int lvl) {
			return new Cost(24 * lvl, 6 * lvl, 8 * lvl);
		}

		/** A quarter less of each (Scholarship II, 35.14), rounded to the nearest, at least one of what cost any. */
		public Cost scholarship() {
			return new Cost(quarterLess(paper), quarterLess(books), quarterLess(emeralds));
		}

		private static int quarterLess(int n) {
			return n <= 0 ? n : Math.max(1, Math.round(n * 0.75f));
		}

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

		/** Whether {@code topic}'s next level can be taken up in the first tier: not at its top, and what it needs is done. */
		public boolean available(Topic topic) {
			return available(topic, false);
		}

		/**
		 * Whether {@code topic}'s next level can be taken up, with the Great Library's second tier open or not
		 * ({@code library}): not at its top, and what it needs is done.
		 */
		public boolean available(Topic topic, boolean library) {
			return level(topic) < (library ? topic.top() : topic.maxLevel)
				&& topic.needs().entrySet().stream().allMatch(e -> level(e.getKey()) >= e.getValue());
		}

		/**
		 * {@code topic}'s level as its bonus counts it: all of it with the second tier open ({@code library}), else no
		 * more than the first tier's top (35.14: the levels are kept, their bonuses pause).
		 */
		public int bonus(Topic topic, boolean library) {
			int lvl = level(topic);
			return library ? Math.min(lvl, topic.top()) : Math.min(lvl, topic.maxLevel);
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

	private record Cached(State state, boolean library, long until) {
	}

	/** Whether the village round {@code hall} has its second tier open (35.14: filled by {@code wonder/LibraryPowers}). */
	@FunctionalInterface
	public interface SecondTier {
		boolean open(ServerLevel level, BlockPos hall);
	}

	/** Filled by {@code wonder/LibraryPowers}: a finished, standing Great Library (its {@code research_tier} power). */
	public static SecondTier SECOND_TIER = (level, hall) -> false;

	/** Whether the village round {@code hall} has the Great Library's second tier open now. */
	public static boolean secondTier(ServerLevel level, BlockPos hall) {
		return SECOND_TIER.open(level, hall);
	}

	/** {@code topic}'s level in {@code state}, the village round {@code hall}'s, as its bonus counts it (35.14). */
	public static int bonus(ServerLevel level, BlockPos hall, State state, Topic topic) {
		return state.bonus(topic, topic.inSecondTier() && secondTier(level, hall));
	}

	/** {@code topic}'s level in the village {@code villager} lives in, as its bonus counts it (35.14; looked up for 200 ticks). */
	public static int bonus(Villager villager, Topic topic) {
		if (!(villager.level() instanceof ServerLevel)) {
			return 0;
		}
		Cached cached = cached(villager);
		return cached.state().bonus(topic, cached.library());
	}

	private static final Map<Villager, Cached> CACHE = new WeakHashMap<>();

	/** The research of the village {@code villager} lives in (none without a Village Hall nearby). */
	public static State of(Villager villager) {
		if (!(villager.level() instanceof ServerLevel)) {
			return State.EMPTY;
		}
		return cached(villager).state();
	}

	private static Cached cached(Villager villager) {
		ServerLevel level = (ServerLevel) villager.level();
		long now = level.getGameTime();
		Cached cached = CACHE.get(villager);
		if (cached != null && now < cached.until()) {
			return cached;
		}
		BlockPos hall = VillageHalls.nearest(level, villager.blockPosition()).orElse(null);
		State state = hall == null ? State.EMPTY : at(level, villager.blockPosition());
		cached = new Cached(state, hall != null && secondTier(level, hall), now + 200);
		CACHE.put(villager, cached);
		return cached;
	}

	/** How a village's research is shared (33.15: a realm member gets the capital's higher levels). */
	@FunctionalInterface
	public interface Sharing {
		State share(ServerLevel level, BlockPos hall, State own);
	}

	/** Filled by {@code realm/RealmResearch}; by default a village knows only its own. */
	public static Sharing SHARING = (level, hall, own) -> own;

	/**
	 * The research of the village round {@code pos}, as its bonuses use it: in a realm, each topic at the higher of the
	 * village's own level and the capital's (33.15). The hall's own levels ({@code entity.research()}) count for its rank.
	 */
	public static State at(ServerLevel level, BlockPos pos) {
		return VillageHalls.nearest(level, pos)
			.map(hall -> level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity ? SHARING.share(level, hall, entity.research()) : State.EMPTY)
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
