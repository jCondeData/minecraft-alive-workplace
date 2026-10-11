package io.github.jcondedata.aliveworkplace.realm;

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
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.TreeEffects;
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
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Shared research (ROADMAP 33.15). A realm's villages share what their scholars know:
 * <ul>
 * <li>for each topic of the scholars' tree a member uses the higher of its own level and the capital's
 * ({@link Research#at} asks {@link #shared}); its research screen says "from the capital". The hall's own levels are
 * left as they are, so shared levels never count toward the member's rank;</li>
 * <li>a member's scholars with nothing chosen at home work on the capital's current topic at half pace
 * ({@link #help}), or on the realm topic while the capital has none;</li>
 * <li>the capital's research screen has a Realm tab on its bottom row: the realm topics, one file each in
 * {@code data/<ns>/realm_research/<topic>.json} (an icon, levels, research points and paper and books a level, emeralds
 * a level from the realm treasury, the topics it needs first, and one effect from the {@link CivicEffects} toolbox that
 * counts once a level). The capital's scholars research them when they have nothing of their own ({@link #work}); the
 * paper and books come from their chests, the emeralds from the realm treasury. Every member gets the effects.</li>
 * </ul>
 * The realm's progress is kept in {@link RealmData.Study}, so it stays with the realm when the capital moves.
 */
public final class RealmResearch implements ResourceManagerReloadListener {
	public static final String FOLDER = "realm_research";
	/** Points a member's idle scholar adds to the capital's topic: half of what they'd make at home. */
	public static final int HELP_SHARE = 2;

	private static final Codec<Item> ITEM = BuiltInRegistries.ITEM.byNameCodec();

	/**
	 * One realm topic, as its file describes it.
	 *
	 * @param points   research points a level
	 * @param paper    paper a level, from the capital's scholars' chests
	 * @param books    books a level, from the village store and the scholars' chests
	 * @param booksFrom the first level that costs books (2: "books a level after the first", 33.16)
	 * @param emeralds emeralds a level, from the realm treasury
	 * @param effect   what each level does in every member village
	 */
	public record Topic(ResourceLocation id, Item icon, Component name, Component description, int levels, int points, int paper, int books,
						int booksFrom, int emeralds, Map<String, Integer> needs, CivicEffects.Effect effect) {
		/** Its id as kept in the realm: ours by path, a pack's in full. */
		public String key() {
			return id.getNamespace().equals(AliveWorkplace.MOD_ID) ? id.getPath() : id.toString();
		}

		/** The paper and books level {@code lvl} costs (the emeralds come from the treasury). */
		public Map<Item, Integer> items(int lvl) {
			return new Research.Cost(paper, lvl >= booksFrom ? books : 0, 0).items();
		}
	}

	record Body(Item icon, Component name, Component description, int levels, int points, int paper, int books, int booksFrom, int emeralds,
				Map<String, Integer> needs, CivicEffects.Effect effect) {
		static final Codec<Body> CODEC = RecordCodecBuilder.create(i -> i.group(
			ITEM.optionalFieldOf("icon", Items.BOOK).forGetter(Body::icon),
			ComponentSerialization.CODEC.fieldOf("name").forGetter(Body::name),
			ComponentSerialization.CODEC.optionalFieldOf("description", Component.empty()).forGetter(Body::description),
			Codec.intRange(1, 10).optionalFieldOf("levels", 1).forGetter(Body::levels),
			Codec.intRange(1, 1_000_000).fieldOf("points").forGetter(Body::points),
			Codec.intRange(0, 4096).optionalFieldOf("paper", 0).forGetter(Body::paper),
			Codec.intRange(0, 4096).optionalFieldOf("books", 0).forGetter(Body::books),
			Codec.intRange(1, 10).optionalFieldOf("books_from_level", 1).forGetter(Body::booksFrom),
			Codec.intRange(0, 4096).optionalFieldOf("emeralds", 0).forGetter(Body::emeralds),
			Codec.unboundedMap(Codec.STRING, Codec.intRange(1, 10)).optionalFieldOf("needs", Map.of()).forGetter(Body::needs),
			CivicEffects.CODEC.fieldOf("effect").forGetter(Body::effect)
		).apply(i, Body::new));
	}

	private static volatile Map<String, Topic> topics = Map.of();
	private static volatile int generation;

	public static void init() {
		TreeEffects.init();
		RealmEffects.init();
		Platform.get().onServerTick(RealmResearch::tick);
		Platform.get().onDataReload(AliveWorkplace.id(FOLDER), new RealmResearch());
		Research.SHARING = RealmResearch::shared;
		CivicEffects.EXTRA = RealmResearch::effects;
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		load(manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")));
	}

	/** Reads every realm topic file; a broken one is skipped with its name, and so is one that needs a topic that isn't there. */
	public static void load(Map<ResourceLocation, Resource> files) {
		List<Topic> out = new ArrayList<>();
		for (Map.Entry<ResourceLocation, Resource> e : files.entrySet()) {
			String path = e.getKey().getPath();
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(),
				path.substring(FOLDER.length() + 1, path.length() - ".json".length()));
			try (Reader reader = e.getValue().openAsReader()) {
				out.add(read(id, JsonParser.parseReader(reader)));
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping realm research {}: {}", e.getKey(), ex.getMessage());
			}
		}
		set(out);
		AliveWorkplace.LOG.info("Realm research: {}", topics.keySet());
	}

	/** Reads one file; throws naming what's wrong. */
	public static Topic read(ResourceLocation id, JsonElement json) {
		Body b = Body.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(IllegalArgumentException::new);
		return new Topic(id, b.icon(), b.name(), b.description(), b.levels(), b.points(), b.paper(), b.books(), b.booksFrom(), b.emeralds(), Map.copyOf(b.needs()), b.effect());
	}

	/** Puts the topics in place, in id order (a reload; tests). A topic that needs one that isn't there, or past its top, is left out. */
	public static void set(Collection<Topic> found) {
		Map<String, Topic> byKey = new HashMap<>();
		found.forEach(t -> byKey.put(t.key(), t));
		Map<String, Topic> out = new LinkedHashMap<>();
		found.stream().sorted(Comparator.comparing(t -> t.id().toString())).forEach(t -> {
			for (Map.Entry<String, Integer> need : t.needs().entrySet()) {
				Topic other = byKey.get(need.getKey());
				if (other == null || need.getValue() > other.levels()) {
					AliveWorkplace.LOG.warn("Skipping realm research {}: it needs '{}' {} which isn't there", t.id(), need.getKey(), need.getValue());
					return;
				}
			}
			out.put(t.key(), t);
		});
		topics = java.util.Collections.unmodifiableMap(out);
		generation++;
	}

	public static Collection<Topic> all() {
		return topics.values();
	}

	public static Optional<Topic> get(String key) {
		return Optional.ofNullable(topics.get(key));
	}

	// ---- whose realm ----

	/** The realm {@code hall} is in, or null (none, or realms off). */
	@Nullable
	public static RealmData.Realm realm(ServerLevel level, BlockPos hall) {
		return Realms.ENABLED ? Realms.realmOf(level, hall) : null;
	}

	public static boolean isCapital(ServerLevel level, BlockPos hall, RealmData.Realm realm) {
		return realm.capital().equals(GlobalPos.of(level.dimension(), hall));
	}

	/** Whether {@code hall} is the capital of a realm (realms on). */
	public static boolean isCapital(ServerLevel level, BlockPos hall) {
		RealmData.Realm realm = realm(level, hall);
		return realm != null && isCapital(level, hall, realm);
	}

	/** The hall at {@code at} if its chunk is loaded, else null (nothing is loaded for it). */
	@Nullable
	static VillageHallBlockEntity loaded(MinecraftServer server, GlobalPos at) {
		ServerLevel level = server.getLevel(at.dimension());
		return level != null && level.isLoaded(at.pos()) && level.getBlockEntity(at.pos()) instanceof VillageHallBlockEntity entity ? entity : null;
	}

	// ---- the scholars' tree, shared ----

	/** The capital's levels of the scholars' tree: live while it's loaded, else as its last round saw them. */
	public static Map<String, Integer> capitalLevels(MinecraftServer server, RealmData.Realm realm) {
		VillageHallBlockEntity capital = loaded(server, realm.capital());
		return capital != null ? scholars(capital.research()) : RealmData.get(server).study(realm.id()).capital();
	}

	private static Map<String, Integer> scholars(Research.State state) {
		Map<String, Integer> out = new HashMap<>();
		for (Research.Topic t : Research.Topic.values()) {
			if (state.level(t) > 0) {
				out.put(t.key(), state.level(t));
			}
		}
		return out;
	}

	/** {@code own} with each scholars' topic at the capital's level where that is higher (a member of a realm; else {@code own}). */
	public static Research.State shared(ServerLevel level, BlockPos hall, Research.State own) {
		RealmData.Realm realm = realm(level, hall);
		if (realm == null || isCapital(level, hall, realm)) {
			return own;
		}
		Map<String, Integer> capital = capitalLevels(level.getServer(), realm);
		Map<String, Integer> merged = null;
		for (Research.Topic t : Research.Topic.values()) {
			int theirs = Math.min(t.maxLevel, capital.getOrDefault(t.key(), 0));
			if (theirs > own.level(t)) {
				if (merged == null) {
					merged = new HashMap<>(own.levels());
				}
				merged.put(t.key(), theirs);
			}
		}
		return merged == null ? own : new Research.State(Map.copyOf(merged), own.current(), own.progress(), own.paid());
	}

	/** The capital's level of {@code topic} where {@code hall} is a member and the capital knows more, else 0 (the research screen). */
	public static int fromCapital(ServerLevel level, BlockPos hall, Research.State own, Research.Topic topic) {
		RealmData.Realm realm = realm(level, hall);
		if (realm == null || isCapital(level, hall, realm)) {
			return 0;
		}
		int theirs = Math.min(topic.maxLevel, capitalLevels(level.getServer(), realm).getOrDefault(topic.key(), 0));
		return theirs > own.level(topic) ? theirs : 0;
	}

	/** The capital's round keeps its levels for the members to read while it isn't loaded. */
	public static void round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		RealmData.Realm realm = realm(level, hall);
		if (realm == null || !isCapital(level, hall, realm)) {
			return;
		}
		RealmData data = RealmData.get(level.getServer());
		RealmData.Study study = data.study(realm.id());
		Map<String, Integer> now = scholars(entity.research());
		if (!now.equals(study.capital())) {
			data.putStudy(study.withCapital(now));
		}
	}

	// ---- the realm topics ----

	/**
	 * The realm's progress on its topics. A topic chosen in a save whose file is gone (33.16 replaced 33.15's interim six)
	 * is dropped quietly, paid or not, so the realm can choose again.
	 */
	public static Research.State state(MinecraftServer server, RealmData.Realm realm) {
		Research.State state = RealmData.get(server).study(realm.id()).research();
		if (state.current().isPresent() && !topics.isEmpty() && !topics.containsKey(state.current().get())) {
			state = new Research.State(state.levels(), Optional.empty(), 0, false);
			setState(server, realm, state);
		}
		return state;
	}

	static void setState(MinecraftServer server, RealmData.Realm realm, Research.State state) {
		RealmData data = RealmData.get(server);
		data.putStudy(data.study(realm.id()).withResearch(state));
	}

	public static int level(Research.State state, Topic topic) {
		return Math.max(0, Math.min(topic.levels(), state.levels().getOrDefault(topic.key(), 0)));
	}

	@Nullable
	public static Topic current(Research.State state) {
		return state.current().map(topics::get).orElse(null);
	}

	/** Why {@code topic} can't be chosen now, or empty. */
	public static Optional<Component> whyNot(Research.State state, Topic topic) {
		if (level(state, topic) >= topic.levels()) {
			return Optional.of(Component.translatable("message.aliveworkplace.research.realm_complete", topic.name()));
		}
		for (Map.Entry<String, Integer> need : topic.needs().entrySet()) {
			Topic other = topics.get(need.getKey());
			if (other == null || level(state, other) < need.getValue()) {
				return Optional.of(Component.translatable("message.aliveworkplace.research.locked", topic.name()));
			}
		}
		Topic busy = current(state);
		if (busy != null && busy != topic && state.paid()) {
			return Optional.of(Component.translatable("message.aliveworkplace.research.busy", busy.name()));
		}
		return Optional.empty();
	}

	/** {@code player} picks {@code topic} for the realm on the capital's research screen (the capital's owner, friends, operators). */
	public static Realms.Result choose(ServerLevel level, BlockPos hall, ServerPlayer player, Topic topic) {
		if (!Realms.ENABLED) {
			return new Realms.Result(Realms.Refusal.OFF, Component.translatable("message.aliveworkplace.realm.off"));
		}
		RealmData.Realm realm = realm(level, hall);
		if (realm == null || !isCapital(level, hall, realm)) {
			return new Realms.Result(Realms.Refusal.NOT_CAPITAL, Component.translatable("message.aliveworkplace.research.realm_capital"));
		}
		if (!Realms.mayRule(level.getServer(), realm.capital(), player)) {
			return new Realms.Result(Realms.Refusal.NOT_YOURS, Component.translatable("screen.aliveworkplace.research.realm.ruler"));
		}
		Research.State state = state(level.getServer(), realm);
		Optional<Component> why = whyNot(state, topic);
		if (why.isPresent()) {
			return new Realms.Result(Realms.Refusal.TAKEN, why.get());
		}
		if (current(state) != topic) {
			setState(level.getServer(), realm, new Research.State(state.levels(), Optional.of(topic.key()), 0, false));
		}
		return new Realms.Result(null, Component.translatable("message.aliveworkplace.research.realm_chosen", topic.name()));
	}

	/** What a scholar's round did for the realm: their status line's state and detail. */
	public record Work(String state, Component detail) {
	}

	/**
	 * A capital's scholar with nothing of their own works on the realm topic: pays a level (paper and books from the
	 * chests, emeralds from the realm treasury), then adds {@code points} at the desk. Null when the village isn't a
	 * capital or no realm topic is chosen.
	 */
	@Nullable
	public static Work work(ServerLevel level, Villager scholar, BlockPos desk, BlockPos hall, int points, boolean atDesk) {
		RealmData.Realm realm = realm(level, hall);
		if (realm == null || !isCapital(level, hall, realm)) {
			return null;
		}
		MinecraftServer server = level.getServer();
		Research.State state = state(server, realm);
		Topic topic = current(state);
		if (topic == null) {
			return null;
		}
		int next = level(state, topic) + 1;
		Component detail = Component.translatable("research.aliveworkplace.level", topic.name(), BuilderLevels.levelName(next));
		if (!state.paid()) {
			List<BlockPos> own = SupplyContainers.find(level, desk, null);
			List<BlockPos> books = new ArrayList<>(io.github.jcondedata.aliveworkplace.hall.VillageNeeds.store(level, hall));
			own.stream().filter(c -> !books.contains(c)).forEach(books::add);
			for (var e : topic.items(next).entrySet()) {
				List<BlockPos> from = e.getKey() == Items.BOOK ? books : own;
				int have = (int) SupplyContainers.count(level, from, e.getKey());
				if (have < e.getValue()) {
					Requests.post(scholar, new ItemStack(e.getKey()), e.getValue() - have, e.getKey().getDescription(), s -> s.is(e.getKey()));
					return new Work("needs", Research.describe(topic.items(next)));
				}
			}
			long cents = topic.emeralds() * 100L;
			RealmData.Realm now = RealmData.get(server).realm(realm.id());
			if (now == null || now.treasury() < cents) {
				Requests.clear(scholar);
				return new Work("treasury", Component.translatable("message.aliveworkplace.research.realm_emeralds", topic.emeralds()));
			}
			Requests.clear(scholar);
			for (var e : topic.items(next).entrySet()) {
				SupplyContainers.extract(level, e.getKey() == Items.BOOK ? books : own, e.getKey(), e.getValue());
			}
			RealmTreasury.spend(server, now, cents, Component.translatable("screen.aliveworkplace.realm.treasury.research", topic.name()));
			setState(server, realm, state.paidFor());
			level.playSound(null, desk, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1f, 1f);
			return new Work("realm", detail);
		}
		if (atDesk) {
			addPoints(server, realm, points);
		}
		return new Work("realm", detail);
	}

	/** {@code points} toward the realm's paid topic; it finishes when they're all done. */
	public static void addPoints(MinecraftServer server, RealmData.Realm realm, int points) {
		Research.State state = state(server, realm);
		Topic topic = current(state);
		if (topic == null || !state.paid()) {
			return;
		}
		int progress = state.progress() + points;
		if (progress < topic.points()) {
			setState(server, realm, state.withProgress(progress));
			return;
		}
		int lvl = level(state, topic) + 1;
		Map<String, Integer> levels = new HashMap<>(state.levels());
		levels.put(topic.key(), lvl);
		setState(server, realm, new Research.State(Map.copyOf(levels), Optional.empty(), 0, false));
		generation++;
		Component name = Component.translatable("research.aliveworkplace.level", topic.name(), BuilderLevels.levelName(lvl));
		Component text = Component.translatable("message.aliveworkplace.research.realm_done", Realms.title(server, realm), name, topic.description())
			.withStyle(ChatFormatting.AQUA);
		java.util.Set<java.util.UUID> told = new java.util.HashSet<>();
		for (GlobalPos member : Realms.members(realm)) {
			java.util.UUID owner = Realms.ownerOf(server, member);
			ServerPlayer player = owner == null ? null : server.getPlayerList().getPlayer(owner);
			if (player != null && told.add(owner)) {
				Chat.chat(player, text);
			}
		}
		ServerLevel capitalLevel = server.getLevel(realm.capital().dimension());
		if (capitalLevel != null && loaded(server, realm.capital()) != null) {
			Chronicle.record(capitalLevel, realm.capital().pos(), Chronicle.Kind.RESEARCH, Component.translatable("chronicle.aliveworkplace.realm_research", name));
			capitalLevel.playSound(null, realm.capital().pos(), SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8f, 1.2f);
		}
	}

	/**
	 * A member's scholar with nothing chosen at home adds {@code points} (already halved) to the capital's paid topic, or
	 * to the realm topic while the capital has none. Returns the topic's name, or null when there is nothing to help with.
	 */
	@Nullable
	public static Component help(ServerLevel level, BlockPos hall, int points, boolean atDesk) {
		RealmData.Realm realm = realm(level, hall);
		if (realm == null || isCapital(level, hall, realm)) {
			return null;
		}
		MinecraftServer server = level.getServer();
		VillageHallBlockEntity capital = loaded(server, realm.capital());
		if (capital != null) {
			Research.State theirs = capital.research();
			Research.Topic topic = theirs.currentTopic();
			if (topic != null && theirs.paid()) {
				int next = theirs.level(topic) + 1;
				if (atDesk) {
					// up to the last point: the capital's own scholar finishes it at their desk
					capital.setResearch(theirs.withProgress(Math.min(topic.points(next), theirs.progress() + points)));
				}
				return Component.translatable("research.aliveworkplace.level", topic.title(), BuilderLevels.levelName(next));
			}
		}
		Research.State state = state(server, realm);
		Topic topic = current(state);
		if (topic != null && state.paid()) {
			Component name = Component.translatable("research.aliveworkplace.level", topic.name(), BuilderLevels.levelName(level(state, topic) + 1));
			if (atDesk) {
				addPoints(server, realm, points);
			}
			return name;
		}
		return null;
	}

	// ---- the effects ----

	private record Kept(List<CivicEffects.Active> effects, int generation, long until) {
	}

	private static final Map<GlobalPos, Kept> EFFECTS = new HashMap<>();

	/** The realm topics' effects in force in {@code hall}'s village: each topic's effect once a level (none outside a realm). */
	public static List<CivicEffects.Active> effects(ServerLevel level, BlockPos hall) {
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		long now = level.getGameTime();
		Kept kept;
		synchronized (EFFECTS) {
			kept = EFFECTS.get(at);
		}
		if (kept != null && kept.generation() == generation && now < kept.until() && now >= kept.until() - 200) {
			return kept.effects();
		}
		List<CivicEffects.Active> out = new ArrayList<>();
		RealmData.Realm realm = realm(level, hall);
		if (realm != null) {
			Research.State state = state(level.getServer(), realm);
			for (Topic topic : topics.values()) {
				for (int i = 0; i < level(state, topic); i++) {
					out.add(new CivicEffects.Active(topic.effect(), topic.name()));
				}
			}
		}
		List<CivicEffects.Active> keep = kept != null && kept.effects().equals(out) ? kept.effects() : List.copyOf(out);
		synchronized (EFFECTS) {
			EFFECTS.put(at, new Kept(keep, generation, now + 100));
		}
		return keep;
	}

	/** Forget what was summed (tests, and a realm joined or left). */
	public static void forget() {
		generation++;
		synchronized (EFFECTS) {
			EFFECTS.clear();
		}
	}

	// ---- the realm topics' own effects (33.16) ----

	/** The effects of type {@code type} {@code realm}'s research gives, once a level (none for no realm, or realms off). */
	public static <E extends CivicEffects.Effect> List<E> effects(MinecraftServer server, @Nullable RealmData.Realm realm, Class<E> type) {
		if (realm == null || !Realms.ENABLED) {
			return List.of();
		}
		List<E> out = new ArrayList<>();
		Research.State state = state(server, realm);
		for (Topic topic : topics.values()) {
			if (type.isInstance(topic.effect())) {
				for (int i = 0; i < level(state, topic); i++) {
					out.add(type.cast(topic.effect()));
				}
			}
		}
		return out;
	}

	/** The effects of type {@code type} the realm of the village at {@code hall} gives it. */
	public static <E extends CivicEffects.Effect> List<E> effects(ServerLevel level, BlockPos hall, Class<E> type) {
		return effects(level.getServer(), realm(level, hall), type);
	}

	/** The realm's level of its topic {@code key} for the village at {@code hall} ("levies"); 0 outside a realm, with realms off or no such topic. */
	public static int level(ServerLevel level, BlockPos hall, String key) {
		RealmData.Realm realm = realm(level, hall);
		Topic topic = topics.get(key);
		return realm == null || topic == null ? 0 : level(state(level.getServer(), realm), topic);
	}

	/**
	 * How many riders bigger the relief bands of the village at {@code hall} are (Levies, one a level; 33.17's relief
	 * bands read this). 0 outside a realm.
	 */
	public static int reliefRiders(ServerLevel level, BlockPos hall) {
		return effects(level, hall, RealmEffects.ReliefRiders.class).stream().mapToInt(RealmEffects.ReliefRiders::riders).sum();
	}

	/** The realm both villages (their halls) are members of, or null (realms off, or not one realm). */
	@Nullable
	public static RealmData.Realm shared(MinecraftServer server, @Nullable GlobalPos a, @Nullable GlobalPos b) {
		if (!Realms.ENABLED || a == null || b == null) {
			return null;
		}
		RealmData data = RealmData.get(server);
		RealmData.Realm realm = data.realmOf(a);
		return realm != null && realm.has(b) ? realm : null;
	}

	/** The hall of the village {@code pos} is in, or null. */
	@Nullable
	public static GlobalPos villageOf(ServerLevel level, BlockPos pos) {
		return VillageHalls.nearest(level, pos).map(h -> GlobalPos.of(level.dimension(), h)).orElse(null);
	}

	/** The realm whose villages hold both {@code a} and {@code b} (any blocks in them, such as mailboxes or posts), or null. */
	@Nullable
	public static RealmData.Realm sharedAt(MinecraftServer server, @Nullable GlobalPos a, @Nullable GlobalPos b) {
		if (!Realms.ENABLED || a == null || b == null) {
			return null;
		}
		ServerLevel la = server.getLevel(a.dimension());
		ServerLevel lb = server.getLevel(b.dimension());
		return la == null || lb == null ? null : shared(server, villageOf(la, a.pos()), villageOf(lb, b.pos()));
	}

	/** How much sooner (percent) a caravan between {@code from} and {@code to} arrives: Royal Roads, when both are of one realm. */
	public static int caravanSpeed(ServerLevel level, BlockPos from, BlockPos to) {
		RealmData.Realm realm = shared(level.getServer(), GlobalPos.of(level.dimension(), from), GlobalPos.of(level.dimension(), to));
		return Math.min(90, effects(level.getServer(), realm, RealmEffects.CaravanSpeed.class).stream().mapToInt(RealmEffects.CaravanSpeed::percent).sum());
	}

	/** Points narrower the board's spread is in the village at {@code hall} (Common Coin). */
	public static int boardSpread(ServerLevel level, BlockPos hall) {
		return effects(level, hall, RealmEffects.BoardSpread.class).stream().mapToInt(RealmEffects.BoardSpread::points).sum();
	}

	/** Whether parcels between {@code from} and {@code to} (mailboxes) also go out at noon (Royal Post). */
	public static boolean noonPost(MinecraftServer server, @Nullable GlobalPos from, @Nullable GlobalPos to) {
		return !effects(server, sharedAt(server, from, to), RealmEffects.NoonPost.class).isEmpty();
	}

	/**
	 * The fare from post {@code from} to post {@code to} for {@code player}: {@code fare} less the Ferry Charter's
	 * discount (at least an emerald) when both posts are in villages of one realm and the player owns a member village or
	 * is a friend of a member's owner; else {@code fare}.
	 */
	public static int fare(MinecraftServer server, GlobalPos from, GlobalPos to, java.util.UUID player, int fare) {
		RealmData.Realm realm = sharedAt(server, from, to);
		int percent = effects(server, realm, RealmEffects.TicketDiscount.class).stream().mapToInt(RealmEffects.TicketDiscount::percent).sum();
		if (percent <= 0) {
			return fare;
		}
		io.github.jcondedata.aliveworkplace.build.Friends friends = io.github.jcondedata.aliveworkplace.build.Friends.get(server);
		for (GlobalPos member : Realms.members(realm)) {
			java.util.UUID owner = Realms.ownerOf(server, member);
			if (owner != null && (owner.equals(player) || friends.mayDirect(owner, player))) {
				return Math.max(1, (int) Math.round(fare * (100 - Math.min(100, percent)) / 100.0));
			}
		}
		return fare;
	}

	/** Percent off a Colony Charter in the village at {@code hall} (Charters). */
	public static int charterDiscount(ServerLevel level, BlockPos hall) {
		return Math.min(100, effects(level, hall, RealmEffects.ColonyCharter.class).stream().mapToInt(RealmEffects.ColonyCharter::costPercent).sum());
	}

	/** How many more of each supply the settlers of the village at {@code mother} bring (Charters: the most any level names, not a sum). */
	public static int charterSupplies(MinecraftServer server, GlobalPos mother) {
		return effects(server, RealmData.get(server).realmOf(mother), RealmEffects.ColonyCharter.class).stream().mapToInt(RealmEffects.ColonyCharter::supplies).max().orElse(0);
	}

	// ---- Granaries: the dawn transfer ----

	private static long lastDawn = -1;

	private static void tick(MinecraftServer server) {
		if (!Realms.ENABLED || topics.isEmpty()) {
			return;
		}
		long day = server.overworld().getDayTime() / 24000L;
		if (lastDawn < 0) {
			lastDawn = day;
			return;
		}
		if (day != lastDawn) {
			lastDawn = day;
			granaries(server);
		}
	}

	/** Meals in the store of the village at {@code hall}. */
	public static long meals(ServerLevel level, BlockPos hall) {
		long meals = 0;
		for (BlockPos chest : io.github.jcondedata.aliveworkplace.hall.VillageNeeds.store(level, hall)) {
			if (level.getBlockEntity(chest) instanceof net.minecraft.world.Container c) {
				for (int i = 0; i < c.getContainerSize(); i++) {
					ItemStack stack = c.getItem(i);
					if (!stack.isEmpty() && io.github.jcondedata.aliveworkplace.hall.VillageNeeds.isMeal(stack)) {
						meals += stack.getCount();
					}
				}
			}
		}
		return meals;
	}

	/**
	 * Granaries at dawn: in every realm with the {@code granary} effect, each loaded member whose store holds fewer than
	 * {@code below} meals is sent up to {@code meals} from the loaded member (same dimension) with the fullest store, which
	 * keeps {@code below} of its own. The meals go by caravan ({@code Caravans.sendGranary}), outside the route cap.
	 * Returns the caravans sent.
	 */
	public static int granaries(MinecraftServer server) {
		if (!Realms.ENABLED) {
			return 0;
		}
		int sent = 0;
		for (RealmData.Realm realm : RealmData.get(server).realms()) {
			List<RealmEffects.Granary> granary = effects(server, realm, RealmEffects.Granary.class);
			if (granary.isEmpty()) {
				continue;
			}
			int below = granary.stream().mapToInt(RealmEffects.Granary::below).max().orElse(16);
			int meals = granary.stream().mapToInt(RealmEffects.Granary::meals).max().orElse(32);
			Map<GlobalPos, Long> stores = new LinkedHashMap<>();
			for (GlobalPos member : Realms.members(realm)) {
				if (loaded(server, member) != null) {
					stores.put(member, meals(server.getLevel(member.dimension()), member.pos()));
				}
			}
			for (Map.Entry<GlobalPos, Long> needy : stores.entrySet()) {
				if (needy.getValue() >= below) {
					continue;
				}
				GlobalPos donor = null;
				long most = below;
				for (Map.Entry<GlobalPos, Long> e : stores.entrySet()) {
					if (!e.getKey().equals(needy.getKey()) && e.getKey().dimension() == needy.getKey().dimension() && e.getValue() > most) {
						donor = e.getKey();
						most = e.getValue();
					}
				}
				if (donor == null) {
					continue;
				}
				ServerLevel level = server.getLevel(donor.dimension());
				int give = (int) Math.min(meals, most - below);
				List<ItemStack> goods = takeMeals(level, donor.pos(), give);
				int took = goods.stream().mapToInt(ItemStack::getCount).sum();
				if (took > 0) {
					io.github.jcondedata.aliveworkplace.hall.Caravans.sendGranary(level, donor.pos(), needy.getKey().pos(), goods);
					stores.put(donor, most - took);
					stores.put(needy.getKey(), needy.getValue() + took);
					sent++;
				}
			}
		}
		return sent;
	}

	/** Takes up to {@code max} meals out of the store of the village at {@code hall}. */
	private static List<ItemStack> takeMeals(ServerLevel level, BlockPos hall, int max) {
		List<ItemStack> out = new ArrayList<>();
		int left = max;
		for (BlockPos chest : io.github.jcondedata.aliveworkplace.hall.VillageNeeds.store(level, hall)) {
			if (left <= 0) {
				break;
			}
			if (level.getBlockEntity(chest) instanceof net.minecraft.world.Container c) {
				for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
					ItemStack stack = c.getItem(i);
					if (!stack.isEmpty() && io.github.jcondedata.aliveworkplace.hall.VillageNeeds.isMeal(stack)) {
						ItemStack taken = c.removeItem(i, Math.min(left, stack.getCount()));
						left -= taken.getCount();
						out.add(taken);
					}
				}
				c.setChanged();
			}
		}
		return out;
	}

	private RealmResearch() {
	}
}
