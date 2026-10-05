package io.github.jcondedata.aliveworkplace.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * A research tree a Legend works (ROADMAP 29.11), as its file {@code data/<ns>/research_trees/<tree>.json} describes
 * it: the Legend who researches it, an icon, its name and its topics.
 *
 * @param id      the file's id
 * @param legend  the Legend who works it ({@code legend/Legends})
 * @param requires mods it needs (the file is skipped quietly without them)
 */
public record ResearchTree(ResourceLocation id, ResourceLocation legend, Item icon, Component name, List<String> requires, List<Topic> topics) {
	/** The most topics a tree may have: the research screen's topic slots. */
	public static final int MAX_TOPICS = 14;

	private static final Codec<Item> ITEM = BuiltInRegistries.ITEM.byNameCodec();

	/** A topic's {@code unlock}: it opens once the village's counter {@code counter} reaches {@code at}. */
	public record Unlock(String counter, int at) {
		public static final Codec<Unlock> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("counter").forGetter(Unlock::counter),
			Codec.intRange(0, Integer.MAX_VALUE).fieldOf("at").forGetter(Unlock::at)
		).apply(i, Unlock::new));
	}

	/**
	 * One topic: its levels, the items each level costs (the last entry for every level after it), the research points
	 * a level takes (times its level, as the scholars' tree), the topics (and levels) it needs, an optional
	 * {@code unlock}, an optional {@code exclusive} group (the village may take one topic of a group, for good) and the
	 * effects of each level.
	 */
	public record Topic(String id, Item icon, Component name, Component description, int levels, List<Map<Item, Integer>> cost,
						int points, Map<String, Integer> needs, Optional<Unlock> unlock, Optional<String> exclusive,
						List<CivicEffects.Effect> effects) {
		public static final Codec<Topic> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(Topic::id),
			ITEM.optionalFieldOf("icon", Items.BOOK).forGetter(Topic::icon),
			ComponentSerialization.CODEC.fieldOf("name").forGetter(Topic::name),
			ComponentSerialization.CODEC.optionalFieldOf("description", Component.empty()).forGetter(Topic::description),
			Codec.intRange(1, 10).optionalFieldOf("levels", 1).forGetter(Topic::levels),
			Codec.unboundedMap(ITEM, Codec.intRange(1, 4096)).listOf().fieldOf("cost").forGetter(Topic::cost),
			Codec.intRange(1, 1_000_000).fieldOf("points").forGetter(Topic::points),
			Codec.unboundedMap(Codec.STRING, Codec.intRange(1, 10)).optionalFieldOf("needs", Map.of()).forGetter(Topic::needs),
			Unlock.CODEC.optionalFieldOf("unlock").forGetter(Topic::unlock),
			Codec.STRING.optionalFieldOf("exclusive").forGetter(Topic::exclusive),
			CivicEffects.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(Topic::effects)
		).apply(i, Topic::new));

		/** What level {@code lvl} costs. */
		public Map<Item, Integer> cost(int lvl) {
			return cost.isEmpty() ? Map.of() : ordered(cost.get(Math.max(0, Math.min(cost.size() - 1, lvl - 1))));
		}

		/** Research points level {@code lvl} takes. */
		public int points(int lvl) {
			return points * lvl;
		}
	}

	/** The file's fields (the id comes from its path). */
	record Body(ResourceLocation legend, Item icon, Component name, List<String> requires, List<Topic> topics) {
		static final Codec<Body> CODEC = RecordCodecBuilder.create(i -> i.group(
			ResourceLocation.CODEC.fieldOf("legend").forGetter(Body::legend),
			ITEM.optionalFieldOf("icon", Items.BOOK).forGetter(Body::icon),
			ComponentSerialization.CODEC.fieldOf("name").forGetter(Body::name),
			Codec.STRING.listOf().optionalFieldOf("requires", List.of()).forGetter(Body::requires),
			Topic.CODEC.listOf().fieldOf("topics").forGetter(Body::topics)
		).apply(i, Body::new));
	}

	/** Checks what a codec can't: unique topic ids of plain letters, needs that name topics of the tree, at most {@link #MAX_TOPICS}. */
	static ResearchTree of(ResourceLocation id, Body body) {
		if (body.topics().isEmpty() || body.topics().size() > MAX_TOPICS) {
			throw new IllegalArgumentException("a tree has 1 to " + MAX_TOPICS + " topics, not " + body.topics().size());
		}
		Set<String> ids = new HashSet<>();
		for (Topic t : body.topics()) {
			if (!t.id().matches("[a-z0-9_]+")) {
				throw new IllegalArgumentException("bad topic id '" + t.id() + "'");
			}
			if (!ids.add(t.id())) {
				throw new IllegalArgumentException("topic '" + t.id() + "' twice");
			}
			if (t.cost().isEmpty()) {
				throw new IllegalArgumentException("topic '" + t.id() + "' has no cost");
			}
		}
		for (Topic t : body.topics()) {
			for (Map.Entry<String, Integer> need : t.needs().entrySet()) {
				Topic other = body.topics().stream().filter(o -> o.id().equals(need.getKey())).findFirst()
					.orElseThrow(() -> new IllegalArgumentException("topic '" + t.id() + "' needs unknown topic '" + need.getKey() + "'"));
				if (need.getValue() > other.levels()) {
					throw new IllegalArgumentException("topic '" + t.id() + "' needs '" + other.id() + "' past its top level");
				}
			}
		}
		return new ResearchTree(id, body.legend(), body.icon(), body.name(), body.requires(), List.copyOf(body.topics()));
	}

	public Optional<Topic> topic(String topicId) {
		return topics.stream().filter(t -> t.id().equals(topicId)).findFirst();
	}

	/** The other topics of {@code topic}'s exclusive group. */
	public List<Topic> rivals(Topic topic) {
		return topic.exclusive().map(g -> topics.stream().filter(t -> t != topic && t.exclusive().equals(Optional.of(g))).toList()).orElse(List.of());
	}

	/** The tree's id as kept in the hall: ours by path ({@code ancient_lore}), a pack's in full. */
	public String key() {
		return id.getNamespace().equals(io.github.jcondedata.aliveworkplace.AliveWorkplace.MOD_ID) ? id.getPath() : id.toString();
	}

	/** Costs in the file's order, for the screen. */
	static Map<Item, Integer> ordered(Map<Item, Integer> cost) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		cost.entrySet().stream().sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString())))
			.forEach(e -> out.put(e.getKey(), e.getValue()));
		return out;
	}
}
