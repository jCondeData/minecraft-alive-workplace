package io.github.jcondedata.aliveworkplace.research;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.RealmTreasury;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

/**
 * Shared research (ROADMAP 33.15). A realm's villages share what their scholars know, and the realm has research of its
 * own:
 * <ul>
 * <li>a member uses, for each topic of the scholars' tree, the higher of its own level and the capital's
 * ({@link #shared}, through {@link Research#at}); its research screen says "from the capital". Shared levels don't count
 * toward the village's rank, which reads the hall's own levels;</li>
 * <li>a member's scholars with nothing chosen at home work on the capital's current topic instead, at half pace
 * ({@link #helpCapital});</li>
 * <li>realm topics, one file each in {@code data/<ns>/realm_research/<topic>.json} (read when data packs load; a broken
 * file is logged with its path and the reason, and skipped), are chosen on the capital's research screen (its Realm
 * row) by the capital's owner, researched by the capital's scholars before the village's own topic ({@link #work}),
 * their paper and books taken from the chests by the desk and their emeralds from the realm treasury. A topic's
 * effect is one of a toolbox of effect types ({@link #EFFECT_TYPES}) with an amount a level, in force in every village
 * of the realm ({@link #effects}).</li>
 * </ul>
 * The realm's research is kept in {@link RealmData.Realm#research} (a {@link Research.State}: levels under the topics'
 * keys), so it stays with the realm when the crown moves.
 */
public final class RealmResearch implements ResourceManagerReloadListener {
	public static final String FOLDER = "realm_research";
	/** The capital's research screen: the Realm row's label, then its topics. */
	public static final int LABEL = 38;
	public static final int[] SLOTS = {39, 40, 41, 42, 43};
	/** The most realm topics: the Realm row's slots. */
	public static final int MAX_TOPICS = SLOTS.length;

	private static volatile List<Topic> topics = List.of();
	private static volatile int generation;

	// ---- the toolbox of effects ----

	/** Makes the effect a topic's {@code amount} a level gives at level {@code lvl}. */
	@FunctionalInterface
	public interface EffectType {
		CivicEffects.Effect make(int amount, int lvl);
	}

	/**
	 * The effect types a realm topic may have, each read by the system it changes ({@link TreeEffects}):
	 * {@code wellbeing} (points a level), {@code xp} (percent more a level), {@code illness} (percent likelier a level;
	 * negative: rarer), {@code loot_luck} (luck a level), {@code raid_chance} (percent likelier a level; negative: rarer).
	 */
	public static final Map<String, EffectType> EFFECT_TYPES = new LinkedHashMap<>();

	static {
		TreeEffects.init();
		EFFECT_TYPES.put("wellbeing", (amount, lvl) -> new TreeEffects.Wellbeing(clamp(amount * lvl, -100, 100), Optional.empty(), List.of()));
		EFFECT_TYPES.put("xp", (amount, lvl) -> new TreeEffects.Xp(clamp(amount * lvl, -100, 1000), List.of()));
		EFFECT_TYPES.put("illness", (amount, lvl) -> new TreeEffects.Illness(clamp(amount * lvl, -100, 1000), 0, List.of()));
		EFFECT_TYPES.put("loot_luck", (amount, lvl) -> new TreeEffects.LootLuck(clamp(amount * lvl, -100, 100), List.of()));
		EFFECT_TYPES.put("raid_chance", (amount, lvl) -> new TreeEffects.RaidChance(Math.max(0f, 1f + amount * lvl / 100f), List.of()));
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	/** A topic's effect: its type (one of {@link #EFFECT_TYPES}) and its amount a level. */
	public record Effect(String type, int amount) {
		public static final Codec<Effect> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.xmap(s -> s.startsWith(AliveWorkplace.MOD_ID + ":") ? s.substring(AliveWorkplace.MOD_ID.length() + 1) : s, s -> s)
				.fieldOf("type").forGetter(Effect::type),
			Codec.intRange(-100, 100).fieldOf("amount").forGetter(Effect::amount)
		).apply(i, Effect::new));

		/** The effect at level {@code lvl}. */
		public CivicEffects.Effect at(int lvl) {
			return EFFECT_TYPES.get(type).make(amount, lvl);
		}

		/** "Wellbeing in every village of the realm: +5 a level". */
		public Component text() {
			return Component.translatable("realm_research.aliveworkplace.effect." + type, (amount > 0 ? "+" : "") + amount);
		}
	}

	/**
	 * A realm topic, as its file describes it: icon, name, description, levels, research points, paper and books and
	 * emeralds a level (each times the level, as the scholars' tree), the topics (and levels) it needs and its effect.
	 */
	public record Topic(ResourceLocation id, Item icon, Component name, Component description, int levels, int points, int paper, int books,
						int emeralds, Map<String, Integer> needs, Effect effect, int order) {
		/** The key the realm keeps it under: ours by path ({@code royal_post}), a pack's in full. */
		public String key() {
			return id.getNamespace().equals(AliveWorkplace.MOD_ID) ? id.getPath() : id.toString();
		}

		public int points(int lvl) {
			return points * lvl;
		}

		/** Paper and books level {@code lvl} takes from the chests by the desk. */
		public Map<Item, Integer> items(int lvl) {
			Map<Item, Integer> out = new LinkedHashMap<>();
			if (paper > 0) {
				out.put(Items.PAPER, paper * lvl);
			}
			if (books > 0) {
				out.put(Items.BOOK, books * lvl);
			}
			return out;
		}

		/** Emeralds level {@code lvl} takes from the realm treasury. */
		public int emeralds(int lvl) {
			return emeralds * lvl;
		}
	}

	/** The file's fields (the id comes from its path). */
	record Body(Item icon, Component name, Component description, int levels, int points, int paper, int books, int emeralds,
				Map<String, Integer> needs, Effect effect, int order) {
		static final Codec<Body> CODEC = RecordCodecBuilder.create(i -> i.group(
			BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("icon", Items.BOOK).forGetter(Body::icon),
			ComponentSerialization.CODEC.fieldOf("name").forGetter(Body::name),
			ComponentSerialization.CODEC.optionalFieldOf("description", Component.empty()).forGetter(Body::description),
			Codec.intRange(1, 10).optionalFieldOf("levels", 1).forGetter(Body::levels),
			Codec.intRange(1, 1_000_000).fieldOf("points").forGetter(Body::points),
			Codec.intRange(0, 4096).optionalFieldOf("paper", 0).forGetter(Body::paper),
			Codec.intRange(0, 4096).optionalFieldOf("books", 0).forGetter(Body::books),
			Codec.intRange(0, 100_000).optionalFieldOf("emeralds", 0).forGetter(Body::emeralds),
			Codec.unboundedMap(Codec.STRING, Codec.intRange(1, 10)).optionalFieldOf("needs", Map.of()).forGetter(Body::needs),
			Effect.CODEC.fieldOf("effect").forGetter(Body::effect),
			Codec.INT.optionalFieldOf("order", 100).forGetter(Body::order)
		).apply(i, Body::new));
	}

	// ---- loading ----

	public static void init() {
		Platform.get().onDataReload(AliveWorkplace.id(FOLDER), new RealmResearch());
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<ResourceLocation, Callable<Reader>> files = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
			files.put(e.getKey(), e.getValue()::openAsReader);
		}
		load(files);
	}

	/**
	 * Reads every topic file (keyed by its path, {@code <ns>:realm_research/<topic>.json}) and puts the good ones in
	 * place; each broken one is logged with its path and the reason, and skipped. Returns the problems, "path: reason".
	 */
	public static List<String> load(Map<ResourceLocation, Callable<Reader>> files) {
		List<String> problems = new ArrayList<>();
		List<Topic> read = new ArrayList<>();
		for (Map.Entry<ResourceLocation, Callable<Reader>> e : files.entrySet()) {
			String path = e.getKey().getPath();
			try (Reader reader = e.getValue().call()) {
				ResourceLocation id = ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(),
					path.substring(FOLDER.length() + 1, path.length() - ".json".length()));
				read.add(read(id, JsonParser.parseReader(reader)));
			} catch (Exception ex) {
				problems.add(e.getKey() + ": " + ex.getMessage());
			}
		}
		read.sort(Comparator.comparing((Topic t) -> !t.id().getNamespace().equals(AliveWorkplace.MOD_ID))
			.thenComparingInt(Topic::order).thenComparing(t -> t.id().toString()));
		Map<String, Topic> byKey = new LinkedHashMap<>();
		read.forEach(t -> byKey.put(t.key(), t));
		List<Topic> out = new ArrayList<>();
		for (Topic t : read) {
			String why = null;
			for (Map.Entry<String, Integer> need : t.needs().entrySet()) {
				Topic other = byKey.get(need.getKey());
				if (other == null) {
					why = "needs unknown topic '" + need.getKey() + "'";
				} else if (need.getValue() > other.levels()) {
					why = "needs '" + need.getKey() + "' past its top level";
				}
			}
			if (why == null && out.size() >= MAX_TOPICS) {
				why = "the Realm row holds " + MAX_TOPICS + " topics";
			}
			if (why != null) {
				problems.add(t.id().getNamespace() + ":" + FOLDER + "/" + t.id().getPath() + ".json: " + why);
			} else {
				out.add(t);
			}
		}
		for (String problem : problems) {
			AliveWorkplace.LOG.warn("Skipping realm research {}", problem);
		}
		set(out);
		AliveWorkplace.LOG.info("Realm research: {}", out.stream().map(Topic::key).toList());
		return problems;
	}

	/** Reads one topic file. Throws naming what's wrong. */
	public static Topic read(ResourceLocation id, JsonElement json) {
		Body b = Body.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(IllegalArgumentException::new);
		if (!EFFECT_TYPES.containsKey(b.effect().type())) {
			throw new IllegalArgumentException("unknown effect type '" + b.effect().type() + "' (one of " + EFFECT_TYPES.keySet() + ")");
		}
		return new Topic(id, b.icon(), b.name(), b.description(), b.levels(), b.points(), b.paper(), b.books(), b.emeralds(),
			Map.copyOf(b.needs()), b.effect(), b.order());
	}

	/** Puts topics in place (a reload; tests and scenes). */
	public static void set(Collection<Topic> found) {
		topics = List.copyOf(found);
		generation++;
		synchronized (EFFECTS) {
			EFFECTS.clear();
		}
	}

	public static List<Topic> all() {
		return topics;
	}

	/** Counts the loads (a reload has finished when it moves on). */
	public static int generation() {
		return generation;
	}

	public static Optional<Topic> get(String key) {
		return topics.stream().filter(t -> t.key().equals(key)).findFirst();
	}

	// ---- the realm's state ----

	public static int level(Research.State state, Topic topic) {
		return Math.max(0, state.levels().getOrDefault(topic.key(), 0));
	}

	/** The topic the capital's scholars are on for the realm, if it is still loaded. */
	@Nullable
	public static Topic current(Research.State state) {
		return state.current().flatMap(RealmResearch::get).orElse(null);
	}

	/** Why {@code topic} can't be chosen now (empty: it can): a paid-for topic in hand, at its top, or what it needs not done. */
	public static Optional<Component> whyNot(Research.State state, Topic topic) {
		Topic now = current(state);
		if (now != null && state.paid() && now != topic) {
			return Optional.of(Component.translatable("message.aliveworkplace.realm_research.busy", now.name()));
		}
		if (level(state, topic) >= topic.levels()) {
			return Optional.of(Component.translatable("screen.aliveworkplace.research.complete"));
		}
		for (Map.Entry<String, Integer> need : topic.needs().entrySet()) {
			if (state.levels().getOrDefault(need.getKey(), 0) < need.getValue()) {
				return Optional.of(Component.translatable("message.aliveworkplace.research.locked", topic.name()));
			}
		}
		if (now == topic) {
			return Optional.of(Component.translatable("screen.aliveworkplace.research.current"));
		}
		return Optional.empty();
	}

	/** The realm whose capital is the village of {@code hall} (realms on), or null. */
	@Nullable
	public static RealmData.Realm capitalOf(ServerLevel level, BlockPos hall) {
		if (!Realms.ENABLED) {
			return null;
		}
		RealmData.Realm realm = Realms.realmOf(level, hall);
		return realm != null && realm.capital().equals(GlobalPos.of(level.dimension(), hall)) ? realm : null;
	}

	/** {@code player} chooses {@code topic} for the realm whose capital is the village of {@code hall}: what to tell them, and whether it was chosen. */
	public static Component choose(ServerLevel level, BlockPos hall, ServerPlayer player, Topic topic) {
		RealmData.Realm realm = capitalOf(level, hall);
		if (realm == null) {
			return Component.translatable("message.aliveworkplace.realm.not_capital", VillageHalls.name(level, hall)).withStyle(ChatFormatting.YELLOW);
		}
		if (!Realms.mayRule(level.getServer(), realm.capital(), player)) {
			return Component.translatable("message.aliveworkplace.realm_research.not_yours").withStyle(ChatFormatting.YELLOW);
		}
		Optional<Component> why = whyNot(realm.research(), topic);
		if (why.isPresent()) {
			return why.get().copy().withStyle(ChatFormatting.YELLOW);
		}
		Research.State state = realm.research();
		RealmData.get(level.getServer()).putRealm(realm.withResearch(new Research.State(state.levels(), Optional.of(topic.key()), 0, false)));
		level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
		return Component.translatable("message.aliveworkplace.realm_research.chosen", topic.name()).withStyle(ChatFormatting.GREEN);
	}

	// ---- shared levels ----

	/** The capital's levels as last seen, by realm (for a capital whose chunk isn't loaded). */
	private static final Map<UUID, Map<String, Integer>> CAPITALS = new HashMap<>();

	/**
	 * The research {@code own} of the village of {@code hall} with the capital's levels shared: for each topic of the
	 * scholars' tree the higher of its own and the capital's. Its own state outside a realm, at the capital, or with
	 * realms off.
	 */
	public static Research.State shared(ServerLevel level, BlockPos hall, Research.State own) {
		Map<String, Integer> capital = capitalLevels(level, hall);
		if (capital.isEmpty()) {
			return own;
		}
		Map<String, Integer> merged = null;
		for (Research.Topic topic : Research.Topic.values()) {
			int theirs = capital.getOrDefault(topic.key(), 0);
			if (theirs > own.level(topic)) {
				if (merged == null) {
					merged = new HashMap<>(own.levels());
				}
				merged.put(topic.key(), theirs);
			}
		}
		return merged == null ? own : new Research.State(Map.copyOf(merged), own.current(), own.progress(), own.paid());
	}

	/** The capital's levels of the realm the village of {@code hall} is a member of (empty at the capital, outside a realm, or with realms off). */
	public static Map<String, Integer> capitalLevels(ServerLevel level, BlockPos hall) {
		if (!Realms.ENABLED || level.getServer() == null) {
			return Map.of();
		}
		RealmData.Realm realm = Realms.realmOf(level, hall);
		if (realm == null || realm.capital().equals(GlobalPos.of(level.dimension(), hall))) {
			return Map.of();
		}
		VillageHallBlockEntity capital = loaded(level.getServer(), realm.capital());
		synchronized (CAPITALS) {
			if (capital != null) {
				CAPITALS.put(realm.id(), capital.research().levels());
			}
			return CAPITALS.getOrDefault(realm.id(), Map.of());
		}
	}

	@Nullable
	private static VillageHallBlockEntity loaded(MinecraftServer server, GlobalPos at) {
		ServerLevel level = server.getLevel(at.dimension());
		if (level == null || !level.isLoaded(at.pos())) {
			return null;
		}
		return level.getBlockEntity(at.pos()) instanceof VillageHallBlockEntity e ? e : null;
	}

	/** Forget the capitals' levels seen (tests). */
	public static void forget() {
		synchronized (CAPITALS) {
			CAPITALS.clear();
		}
	}

	// ---- effects ----

	private record Kept(Map<String, Integer> levels, int generation, long until, CivicEffects.Sum sum) {
	}

	private static final Map<VillageHallBlockEntity, Kept> EFFECTS = new WeakHashMap<>();

	/** The effects of the realm topics in force in {@code hall}'s village: those of its realm, each at its level (none with realms off). */
	public static CivicEffects.Sum effects(@Nullable VillageHallBlockEntity hall) {
		if (hall == null || !Realms.ENABLED || topics.isEmpty() || !(hall.getLevel() instanceof ServerLevel level)) {
			return CivicEffects.Sum.EMPTY;
		}
		long now = level.getGameTime();
		synchronized (EFFECTS) {
			Kept kept = EFFECTS.get(hall);
			if (kept != null && kept.generation() == generation && now < kept.until()) {
				return kept.sum();
			}
		}
		RealmData.Realm realm = Realms.realmOf(level, hall.getBlockPos());
		Map<String, Integer> levels = realm == null ? Map.of() : realm.research().levels();
		List<CivicEffects.Active> out = new ArrayList<>();
		for (Topic topic : topics) {
			int lvl = Math.min(topic.levels(), levels.getOrDefault(topic.key(), 0));
			if (lvl > 0) {
				out.add(new CivicEffects.Active(topic.effect().at(lvl), topic.name()));
			}
		}
		CivicEffects.Sum sum = out.isEmpty() ? CivicEffects.Sum.EMPTY : new CivicEffects.Sum(out);
		synchronized (EFFECTS) {
			EFFECTS.put(hall, new Kept(levels, generation, now + 100, sum));
		}
		return sum;
	}

	/** Forget the effects worked out (a realm topic finished). */
	public static void forgetEffects() {
		synchronized (EFFECTS) {
			EFFECTS.clear();
		}
	}

	// ---- the work ----

	/** What a scholar's round came to: the state for the status line ({@code message.aliveworkplace.scholar.state.<state>}) and its detail. */
	public record Outcome(String state, Component detail) {
	}

	/**
	 * A scholar of the capital at their desk: the realm's chosen topic comes before the village's own. Pays for its
	 * level (paper and books from the chests by the desk and the village store, emeralds from the realm treasury), then
	 * works on it at {@code points} a round, finishing it. Null when the village isn't a capital or nothing is chosen.
	 */
	@Nullable
	public static Outcome work(ServerLevel level, Villager scholar, BlockPos desk, BlockPos hall, int points, boolean atDesk) {
		RealmData.Realm realm = capitalOf(level, hall);
		if (realm == null) {
			return null;
		}
		Research.State state = realm.research();
		Topic topic = current(state);
		if (topic == null) {
			return null;
		}
		int next = level(state, topic) + 1;
		if (next > topic.levels()) {
			RealmData.get(level.getServer()).putRealm(realm.withResearch(new Research.State(state.levels(), Optional.empty(), 0, false)));
			return null;
		}
		Component levelText = Component.translatable("research.aliveworkplace.level", topic.name(), BuilderLevels.levelName(next));
		if (!state.paid()) {
			List<BlockPos> chests = new ArrayList<>(VillageNeeds.store(level, hall));
			SupplyContainers.find(level, desk, null).stream().filter(c -> !chests.contains(c)).forEach(chests::add);
			Map<Item, Integer> items = topic.items(next);
			for (Map.Entry<Item, Integer> e : items.entrySet()) {
				long have = SupplyContainers.count(level, chests, e.getKey());
				if (have < e.getValue()) {
					Requests.post(scholar, new ItemStack(e.getKey()), (int) (e.getValue() - have), e.getKey().getDescription(), s -> s.is(e.getKey()));
					return new Outcome("needs", Research.describe(items));
				}
			}
			long cents = 100L * topic.emeralds(next);
			if (realm.treasury() < cents) {
				Requests.clear(scholar);
				return new Outcome("realm_treasury", Component.literal(String.valueOf(topic.emeralds(next))));
			}
			Requests.clear(scholar);
			if (!RealmTreasury.spend(level.getServer(), realm, cents, Component.translatable("realm_research.aliveworkplace.paid", levelText))) {
				return new Outcome("realm_treasury", Component.literal(String.valueOf(topic.emeralds(next))));
			}
			for (Map.Entry<Item, Integer> e : items.entrySet()) {
				SupplyContainers.extract(level, chests, e.getKey(), e.getValue());
			}
			RealmData.Realm now = RealmData.get(level.getServer()).realm(realm.id());
			RealmData.get(level.getServer()).putRealm(now.withResearch(now.research().paidFor()));
			level.playSound(null, desk, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1f, 1f);
			return new Outcome("realm", levelText);
		}
		Requests.clear(scholar);
		if (atDesk) {
			scholar.swing(InteractionHand.MAIN_HAND);
			level.sendParticles(ParticleTypes.ENCHANT, desk.getX() + 0.5, desk.getY() + 1.3, desk.getZ() + 0.5, 5, 0.3, 0.2, 0.3, 0.5);
			addProgress(level, realm, points, scholar);
		}
		return new Outcome("realm", levelText);
	}

	/** Adds {@code points} to the realm's paid-for topic, finishing its level when it's done. True if a level was finished. */
	static boolean addProgress(ServerLevel level, RealmData.Realm realm, int points, Villager by) {
		Research.State state = realm.research();
		Topic topic = current(state);
		if (topic == null || !state.paid()) {
			return false;
		}
		int next = level(state, topic) + 1;
		int progress = state.progress() + points;
		RealmData data = RealmData.get(level.getServer());
		if (progress < topic.points(next)) {
			data.putRealm(realm.withResearch(state.withProgress(progress)));
			return false;
		}
		Map<String, Integer> levels = new HashMap<>(state.levels());
		levels.put(topic.key(), next);
		data.putRealm(realm.withResearch(new Research.State(Map.copyOf(levels), Optional.empty(), 0, false)));
		forgetEffects();
		level.playSound(null, by.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.8f, 1.2f);
		MinecraftServer server = level.getServer();
		Component levelText = Component.translatable("research.aliveworkplace.level", topic.name(), BuilderLevels.levelName(next));
		Component text = Component.translatable("message.aliveworkplace.realm_research.done", Realms.title(server, realm), levelText, topic.effect().text())
			.withStyle(ChatFormatting.AQUA);
		List<ServerPlayer> told = new ArrayList<>();
		for (GlobalPos member : Realms.members(realm)) {
			ServerLevel there = server.getLevel(member.dimension());
			if (there == null) {
				continue;
			}
			for (ServerPlayer player : there.getPlayers(p -> p.distanceToSqr(member.pos().getCenter()) < (double) VillageHalls.RADIUS * VillageHalls.RADIUS)) {
				if (!told.contains(player)) {
					told.add(player);
					Chat.chat(player, text);
				}
			}
		}
		Chronicle.record(level, realm.capital().pos(), Chronicle.Kind.RESEARCH,
			Component.translatable("chronicle.aliveworkplace.realm_research", levelText, by.getDisplayName()));
		return true;
	}

	/**
	 * A member's scholar with nothing chosen at home helps the capital at half pace: its paid-for current topic, else
	 * the realm's paid-for topic (at their desk). The capital's name, or null when there's nothing to help with.
	 */
	@Nullable
	public static Component helpCapital(ServerLevel level, Villager scholar, BlockPos hall, int points, boolean atDesk) {
		if (!Realms.ENABLED) {
			return null;
		}
		RealmData.Realm realm = Realms.realmOf(level, hall);
		if (realm == null || realm.capital().equals(GlobalPos.of(level.dimension(), hall))) {
			return null;
		}
		VillageHallBlockEntity capital = loaded(level.getServer(), realm.capital());
		if (capital == null) {
			return null;
		}
		ServerLevel capitalLevel = level.getServer().getLevel(realm.capital().dimension());
		BlockPos capitalHall = realm.capital().pos();
		int half = Math.max(1, points / 2);
		Research.State theirs = capital.research();
		Research.Topic topic = theirs.currentTopic();
		if (topic != null && theirs.paid()) {
			if (atDesk) {
				int next = theirs.level(topic) + 1;
				int progress = theirs.progress() + half;
				if (progress < topic.points(next)) {
					capital.setResearch(theirs.withProgress(progress));
				} else {
					ScholarWork.finish(capitalLevel, scholar, capitalHall, capitalHall, capital, topic, next);
				}
			}
			return VillageHalls.name(capitalLevel, capitalHall);
		}
		Topic realmTopic = current(realm.research());
		if (realmTopic != null && realm.research().paid()) {
			if (atDesk) {
				addProgress(level, realm, half, scholar);
			}
			return VillageHalls.name(capitalLevel, capitalHall);
		}
		return null;
	}

	// ---- the Realm row ----

	/** The capital's Realm row on its research screen ({@link #LABEL}, {@link #SLOTS}); nothing outside a capital or without topics. */
	static void row(ChoiceMenu menu, ServerLevel level, BlockPos hall, Runnable again) {
		RealmData.Realm realm = capitalOf(level, hall);
		if (realm == null || topics.isEmpty()) {
			return;
		}
		MinecraftServer server = level.getServer();
		Research.State state = realm.research();
		Topic current = current(state);
		List<Component> about = new ArrayList<>();
		about.add(line(Component.translatable("screen.aliveworkplace.realm_research.about", Realms.title(server, realm)), ChatFormatting.GRAY));
		if (current == null) {
			about.add(line(Component.translatable("screen.aliveworkplace.realm_research.nothing"), ChatFormatting.YELLOW));
		} else {
			int next = level(state, current) + 1;
			about.add(line(Component.translatable("screen.aliveworkplace.research.now",
				Component.translatable("research.aliveworkplace.level", current.name(), BuilderLevels.levelName(next)),
				Math.round(100f * state.progress() / current.points(next))), ChatFormatting.GREEN));
			if (!state.paid()) {
				about.add(line(Component.translatable("screen.aliveworkplace.realm_research.unpaid", Research.describe(current.items(next)),
					current.emeralds(next)), ChatFormatting.YELLOW));
			}
		}
		about.add(line(Component.translatable("screen.aliveworkplace.realm_research.treasury", RealmTreasury.emeralds(realm.treasury())), ChatFormatting.GREEN));
		about.add(line(Component.translatable("screen.aliveworkplace.realm_research.how"), ChatFormatting.DARK_GRAY));
		menu.button(LABEL, icon(Items.GOLDEN_HELMET, Component.translatable("screen.aliveworkplace.realm_research.title"), ChatFormatting.GOLD, about), null);
		for (int i = 0; i < topics.size() && i < SLOTS.length; i++) {
			Topic topic = topics.get(i);
			menu.button(SLOTS[i], topicIcon(state, topic), p -> {
				Component said = choose(level, hall, p, topic);
				Chat.actionBar(p, said);
				again.run();
			});
		}
	}

	static ItemStack topicIcon(Research.State state, Topic topic) {
		int lvl = level(state, topic);
		boolean isCurrent = current(state) == topic;
		List<Component> lore = new ArrayList<>();
		if (!topic.description().getString().isEmpty()) {
			lore.add(line(topic.description(), ChatFormatting.GRAY));
		}
		lore.add(line(topic.effect().text(), ChatFormatting.AQUA));
		lore.add(line(Component.translatable("screen.aliveworkplace.research.level", lvl, topic.levels()), ChatFormatting.GRAY));
		if (lvl >= topic.levels()) {
			lore.add(line(Component.translatable("screen.aliveworkplace.research.complete"), ChatFormatting.GREEN));
		} else {
			lore.add(line(Component.translatable("screen.aliveworkplace.realm_research.cost", Research.describe(topic.items(lvl + 1)),
				topic.emeralds(lvl + 1)), ChatFormatting.GRAY));
			for (Map.Entry<String, Integer> need : topic.needs().entrySet()) {
				if (state.levels().getOrDefault(need.getKey(), 0) < need.getValue()) {
					Component name = get(need.getKey()).map(Topic::name).orElse(Component.literal(need.getKey()));
					lore.add(line(Component.translatable("screen.aliveworkplace.research.needs",
						Component.translatable("research.aliveworkplace.level", name, BuilderLevels.levelName(need.getValue()))), ChatFormatting.RED));
				}
			}
			if (isCurrent) {
				lore.add(line(Component.translatable("screen.aliveworkplace.research.current"), ChatFormatting.GREEN));
			} else if (whyNot(state, topic).isEmpty()) {
				lore.add(line(Component.translatable("screen.aliveworkplace.realm_research.click"), ChatFormatting.YELLOW));
			}
		}
		ItemStack icon = icon(topic.icon(), topic.name(), lvl >= topic.levels() ? ChatFormatting.GREEN : ChatFormatting.GOLD, lore);
		icon.setCount(Math.max(1, lvl));
		if (isCurrent) {
			icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return icon;
	}

	private static ItemStack icon(Item item, Component name, ChatFormatting color, List<Component> lore) {
		ItemStack icon = new ItemStack(item);
		icon.set(DataComponents.CUSTOM_NAME, line(name, color));
		icon.set(DataComponents.LORE, new ItemLore(lore));
		return io.github.jcondedata.aliveworkplace.mc.Tooltips.nameAndLoreOnly(icon);
	}

	private static Component line(Component text, ChatFormatting color) {
		return text.copy().withStyle(style -> style.withItalic(false).withColor(color));
	}

	private RealmResearch() {
	}
}
