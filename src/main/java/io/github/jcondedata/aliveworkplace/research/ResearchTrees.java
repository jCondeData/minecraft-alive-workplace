package io.github.jcondedata.aliveworkplace.research;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The Legends' research trees (ROADMAP 29.11), one file each in {@code data/<ns>/research_trees/}, read when data packs
 * load. A file that fails is logged with its name and skipped; one whose {@code requires} names a missing mod is skipped
 * quietly.
 *
 * <p>A village's progress lives in its hall's {@link Research.State} map, so old halls load unchanged and no save field is
 * added: a topic's level under {@code <tree>/<topic>}, and the topic the tree's Legend is on under
 * {@code @<tree>/<topic>} (-1 while it waits to be paid for, else the points done). {@link Research.State#isLevel} tells
 * the two apart for everything that counts levels.
 *
 * <p>A tree's tab shows on the research screen while its Legend lives in the village; the Legend works it at a lectern
 * within {@link TreeWork#REACH} blocks of their home ({@link TreeWork}), never while on strike, and the village's
 * scholars with nothing of their own to research help at half speed ({@link #help}).
 */
public final class ResearchTrees implements ResourceManagerReloadListener {
	public static final String FOLDER = "research_trees";

	private static volatile Map<ResourceLocation, ResearchTree> trees = Map.of();
	private static volatile int generation;

	/** A village counter a topic's {@code unlock} waits for: its name on the screen, and how to count it. */
	public record Counter(Component label, Count count) {
	}

	@FunctionalInterface
	public interface Count {
		int count(ServerLevel level, BlockPos hall);
	}

	private static final Map<String, Counter> COUNTERS = new LinkedHashMap<>();

	static {
		TreeEffects.init();
		counter("research_levels", Component.translatable("research_tree.aliveworkplace.counter.research_levels"),
			(level, hall) -> level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity ? entity.research().totalLevels() : 0);
		counter("villagers", Component.translatable("research_tree.aliveworkplace.counter.villagers"),
			(level, hall) -> level.getEntitiesOfClass(Villager.class, new AABB(hall).inflate(VillageHalls.RADIUS), v -> v.isAlive() && !v.isBaby()).size());
	}

	public static void init() {
		Platform.get().onDataReload(AliveWorkplace.id(FOLDER), new ResearchTrees());
	}

	/** Adds a village counter (later items: the village Pokédex's species, 29.21). */
	public static synchronized void counter(String name, Component label, Count count) {
		COUNTERS.put(name, new Counter(label, count));
	}

	@Nullable
	public static synchronized Counter counter(String name) {
		return COUNTERS.get(name);
	}

	/** The village's count of {@code name} (0 for a counter nobody has added). */
	public static int count(ServerLevel level, BlockPos hall, String name) {
		Counter c = counter(name);
		return c == null ? 0 : c.count().count(level, hall);
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		load(manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")));
	}

	/** Reads every tree file; a broken one is skipped with its name. */
	public static void load(Map<ResourceLocation, Resource> files) {
		Map<ResourceLocation, ResearchTree> out = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : files.entrySet()) {
			String path = e.getKey().getPath();
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(),
				path.substring(FOLDER.length() + 1, path.length() - ".json".length()));
			try (Reader reader = e.getValue().openAsReader()) {
				ResearchTree tree = read(id, JsonParser.parseReader(reader));
				if (tree != null) {
					out.put(id, tree);
				}
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping research tree {}: {}", e.getKey(), ex.getMessage());
			}
		}
		set(out.values());
		AliveWorkplace.LOG.info("Research trees: {}", trees.keySet());
	}

	/** Reads one tree file; null when it needs a mod that isn't installed. Throws naming what's wrong. */
	@Nullable
	public static ResearchTree read(ResourceLocation id, JsonElement json) {
		ResearchTree.Body body = ResearchTree.Body.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(IllegalArgumentException::new);
		for (String mod : body.requires()) {
			if (!Platform.get().isModLoaded(mod)) {
				return null;
			}
		}
		return ResearchTree.of(id, body);
	}

	/** Puts trees in place (a reload; tests and scenes). */
	public static void set(Collection<ResearchTree> found) {
		Map<ResourceLocation, ResearchTree> out = new LinkedHashMap<>();
		found.forEach(t -> out.put(t.id(), t));
		trees = java.util.Collections.unmodifiableMap(out);
		generation++;
		synchronized (EFFECTS) {
			EFFECTS.clear();
		}
	}

	public static Collection<ResearchTree> all() {
		return trees.values();
	}

	public static Optional<ResearchTree> get(ResourceLocation id) {
		return Optional.ofNullable(trees.get(id));
	}

	/** The trees {@code legend} researches. */
	public static List<ResearchTree> of(ResourceLocation legend) {
		return trees.values().stream().filter(t -> t.legend().equals(legend)).toList();
	}

	/**
	 * The trees whose Legend lives in the village of {@code hall} (settled there and holding their slot, not in a grave
	 * or turned), from the server's record of Legends; none while Legends are off.
	 */
	public static List<ResearchTree> inVillage(ServerLevel level, BlockPos hall) {
		if (!Legends.ENABLED || trees.isEmpty()) {
			return List.of();
		}
		String dim = level.dimension().location().toString();
		List<ResearchTree> out = new ArrayList<>();
		List<LegendRecord.Entry> here = LegendRecord.get(level).entries().stream()
			.filter(e -> e.holds() && !e.zombie() && e.grave().isEmpty() && e.in(dim, hall)).toList();
		for (ResearchTree tree : trees.values()) {
			if (here.stream().anyMatch(e -> e.id().equals(tree.legend()))) {
				out.add(tree);
			}
		}
		return out;
	}

	// ---- the hall's map ----

	public static String levelKey(ResearchTree tree, ResearchTree.Topic topic) {
		return tree.key() + "/" + topic.id();
	}

	private static String currentPrefix(ResearchTree tree) {
		return Research.State.CURRENT + tree.key() + "/";
	}

	public static int level(Research.State state, ResearchTree tree, ResearchTree.Topic topic) {
		return Math.max(0, state.levels().getOrDefault(levelKey(tree, topic), 0));
	}

	/** The topic a tree's Legend is on: how far along, and whether it's paid for. */
	public record Current(ResearchTree.Topic topic, int progress, boolean paid) {
	}

	public static Optional<Current> current(Research.State state, ResearchTree tree) {
		String prefix = currentPrefix(tree);
		for (Map.Entry<String, Integer> e : state.levels().entrySet()) {
			if (e.getKey().startsWith(prefix)) {
				Optional<ResearchTree.Topic> topic = tree.topic(e.getKey().substring(prefix.length()));
				if (topic.isPresent()) {
					return Optional.of(new Current(topic.get(), Math.max(0, e.getValue()), e.getValue() >= 0));
				}
			}
		}
		return Optional.empty();
	}

	private static Research.State withMap(Research.State state, Map<String, Integer> levels) {
		return new Research.State(Map.copyOf(levels), state.current(), state.progress(), state.paid());
	}

	private static Map<String, Integer> withoutCurrent(Research.State state, ResearchTree tree) {
		Map<String, Integer> next = new HashMap<>(state.levels());
		next.keySet().removeIf(k -> k.startsWith(currentPrefix(tree)));
		return next;
	}

	/** {@code topic} chosen next, waiting to be paid for. */
	public static Research.State choose(Research.State state, ResearchTree tree, ResearchTree.Topic topic) {
		Map<String, Integer> next = withoutCurrent(state, tree);
		next.put(currentPrefix(tree) + topic.id(), -1);
		return withMap(state, next);
	}

	/** The current topic with {@code progress} points done (paid for). */
	public static Research.State withProgress(Research.State state, ResearchTree tree, int progress) {
		Optional<Current> now = current(state, tree);
		if (now.isEmpty()) {
			return state;
		}
		Map<String, Integer> next = withoutCurrent(state, tree);
		next.put(currentPrefix(tree) + now.get().topic().id(), Math.max(0, progress));
		return withMap(state, next);
	}

	/** The current topic done: a level up, nothing being researched in the tree. */
	public static Research.State finish(Research.State state, ResearchTree tree) {
		Optional<Current> now = current(state, tree);
		Map<String, Integer> next = withoutCurrent(state, tree);
		now.ifPresent(c -> next.put(levelKey(tree, c.topic()), level(state, tree, c.topic()) + 1));
		return withMap(state, next);
	}

	/** Whether {@code topic}'s next level waits only on its counter: what it needs is done but the count is short. */
	public static boolean waitsForCounter(ServerLevel level, BlockPos hall, ResearchTree.Topic topic) {
		return topic.unlock().isPresent() && count(level, hall, topic.unlock().get().counter()) < topic.unlock().get().at();
	}

	/** The rival of {@code topic}'s exclusive group the village has taken for good (researched, or paid for), if any. */
	public static Optional<ResearchTree.Topic> takenRival(Research.State state, ResearchTree tree, ResearchTree.Topic topic) {
		Optional<Current> now = current(state, tree);
		for (ResearchTree.Topic rival : tree.rivals(topic)) {
			if (level(state, tree, rival) > 0 || now.isPresent() && now.get().paid() && now.get().topic() == rival) {
				return Optional.of(rival);
			}
		}
		return Optional.empty();
	}

	/**
	 * Why {@code topic} can't be chosen now (empty: it can): the tree's Legend busy with a paid-for topic, the topic at
	 * its top, what it needs not done, its counter short, or a rival of its exclusive group taken.
	 */
	public static Optional<Component> whyNot(ServerLevel level, BlockPos hall, Research.State state, ResearchTree tree, ResearchTree.Topic topic) {
		Optional<Current> now = current(state, tree);
		if (now.isPresent() && now.get().paid()) {
			return Optional.of(Component.translatable("message.aliveworkplace.research.tree_busy", now.get().topic().name()));
		}
		if (level(state, tree, topic) >= topic.levels()) {
			return Optional.of(Component.translatable("screen.aliveworkplace.research.complete"));
		}
		Optional<ResearchTree.Topic> rival = takenRival(state, tree, topic);
		if (rival.isPresent()) {
			return Optional.of(Component.translatable("message.aliveworkplace.research.exclusive", rival.get().name(), topic.name()));
		}
		for (Map.Entry<String, Integer> need : topic.needs().entrySet()) {
			ResearchTree.Topic other = tree.topic(need.getKey()).orElse(null);
			if (other != null && level(state, tree, other) < need.getValue()) {
				return Optional.of(Component.translatable("message.aliveworkplace.research.locked", topic.name()));
			}
		}
		if (waitsForCounter(level, hall, topic)) {
			ResearchTree.Unlock unlock = topic.unlock().get();
			return Optional.of(Component.translatable("message.aliveworkplace.research.unlock", topic.name(), unlock.at(), counterLabel(unlock.counter())));
		}
		return Optional.empty();
	}

	public static Component counterLabel(String name) {
		Counter c = counter(name);
		return c == null ? Component.literal(name) : c.label();
	}

	/** "4× Paper, 1× Amethyst Shard". */
	public static Component describe(Map<Item, Integer> cost) {
		return Research.describe(cost);
	}

	// ---- effects ----

	private record Kept(Map<String, Integer> levels, int generation, CivicEffects.Sum sum) {
	}

	private static final Map<VillageHallBlockEntity, Kept> EFFECTS = new WeakHashMap<>();

	/** The effects of the research trees' levels in {@code hall}'s village: each topic's effects once per level. */
	public static CivicEffects.Sum effects(VillageHallBlockEntity hall) {
		Map<String, Integer> levels = hall.research().levels();
		synchronized (EFFECTS) {
			Kept kept = EFFECTS.get(hall);
			if (kept != null && kept.levels() == levels && kept.generation() == generation) {
				return kept.sum();
			}
		}
		List<CivicEffects.Active> out = new ArrayList<>();
		for (ResearchTree tree : trees.values()) {
			for (ResearchTree.Topic topic : tree.topics()) {
				int lvl = Math.min(topic.levels(), levels.getOrDefault(levelKey(tree, topic), 0));
				for (int i = 0; i < lvl; i++) {
					for (CivicEffects.Effect effect : topic.effects()) {
						out.add(new CivicEffects.Active(effect, topic.name()));
					}
				}
			}
		}
		CivicEffects.Sum sum = out.isEmpty() ? CivicEffects.Sum.EMPTY : new CivicEffects.Sum(out);
		synchronized (EFFECTS) {
			EFFECTS.put(hall, new Kept(levels, generation, sum));
		}
		return sum;
	}

	// ---- the work ----

	/** Whether the Legend of {@code tree} is at work in the village of {@code hall}: settled there and not on strike. */
	public static boolean legendWorking(ServerLevel level, BlockPos hall, ResearchTree tree) {
		for (LegendPowers.Active a : LegendPowers.settled(level)) {
			if (a.legend().id().equals(tree.legend())
				&& (a.data().hall().isPresent() ? a.data().hall() : VillageHalls.nearest(level, a.villager().blockPosition())).equals(Optional.of(hall))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Pays for the current topic of {@code tree} from the chests by {@code at}, if they hold its cost; true if it is paid
	 * for now. Missing items are returned in {@code missing} (empty when paid).
	 */
	static boolean pay(ServerLevel level, VillageHallBlockEntity entity, ResearchTree tree, BlockPos at, Map<Item, Integer> missing) {
		Research.State state = entity.research();
		Optional<Current> now = current(state, tree);
		if (now.isEmpty()) {
			return false;
		}
		if (now.get().paid()) {
			return true;
		}
		Map<Item, Integer> cost = now.get().topic().cost(level(state, tree, now.get().topic()) + 1);
		List<BlockPos> own = SupplyContainers.find(level, at, null);
		for (Map.Entry<Item, Integer> e : cost.entrySet()) {
			int have = (int) Math.min(Integer.MAX_VALUE, SupplyContainers.count(level, own, e.getKey()));
			if (have < e.getValue()) {
				missing.put(e.getKey(), e.getValue() - have);
			}
		}
		if (!missing.isEmpty()) {
			return false;
		}
		for (Map.Entry<Item, Integer> e : cost.entrySet()) {
			SupplyContainers.extract(level, own, e.getKey(), e.getValue());
		}
		entity.setResearch(withProgress(state, tree, 0));
		level.playSound(null, at, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1f, 1f);
		return true;
	}

	/**
	 * Adds {@code points} to the paid-for current topic of {@code tree}, finishing it when it's done (told to the
	 * village's players and kept in the chronicle under {@code by}'s name). True if a level was finished.
	 */
	static boolean addProgress(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, ResearchTree tree, int points, Villager by) {
		Research.State state = entity.research();
		Optional<Current> now = current(state, tree);
		if (now.isEmpty() || !now.get().paid()) {
			return false;
		}
		ResearchTree.Topic topic = now.get().topic();
		int next = level(state, tree, topic) + 1;
		int progress = now.get().progress() + points;
		if (progress < topic.points(next)) {
			entity.setResearch(withProgress(state, tree, progress));
			return false;
		}
		entity.setResearch(finish(state, tree));
		Research.forget();
		CivicEffects.forget();
		level.playSound(null, by.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.8f, 1.2f);
		Component levelText = Component.translatable("research.aliveworkplace.level", topic.name(), BuilderLevels.levelName(next));
		Component text = Component.translatable("message.aliveworkplace.research.tree_done", VillageHalls.name(level, hall), tree.name(),
			levelText, topic.description()).withStyle(ChatFormatting.AQUA);
		for (ServerPlayer player : level.getPlayers(p -> p.distanceToSqr(hall.getCenter()) < (double) VillageHalls.RADIUS * VillageHalls.RADIUS)) {
			Chat.chat(player, text);
		}
		Chronicle.record(level, hall, Chronicle.Kind.RESEARCH, Component.translatable("chronicle.aliveworkplace.research.tree", levelText, by.getDisplayName()));
		return true;
	}

	/**
	 * A scholar with nothing of their own to research helps a tree of the village whose topic is paid for and whose
	 * Legend is at work, at half their pace (at their desk). The tree helped, or null.
	 */
	@Nullable
	public static ResearchTree help(ServerLevel level, Villager scholar, BlockPos hall, VillageHallBlockEntity entity, int points, boolean atDesk) {
		for (ResearchTree tree : inVillage(level, hall)) {
			Optional<Current> now = current(entity.research(), tree);
			if (now.isPresent() && now.get().paid() && legendWorking(level, hall, tree)) {
				if (atDesk) {
					addProgress(level, hall, entity, tree, Math.max(1, points / 2), scholar);
				}
				return tree;
			}
		}
		return null;
	}

	/** Whether any tree has anything chosen in {@code state}. */
	static boolean anyCurrent(Research.State state) {
		return state.levels().keySet().stream().anyMatch(k -> k.startsWith(Research.State.CURRENT));
	}

	private ResearchTrees() {
	}
}
