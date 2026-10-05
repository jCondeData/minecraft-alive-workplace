package io.github.jcondedata.aliveworkplace.people;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Luxuries (ROADMAP 34.4, {@code docs/design/M34.md}): what the higher classes want now and then, read from
 * {@code data/<namespace>/luxuries/<id>.json} ({@code {"item": "aliveworkplace:berry_wine", "every_days": 2}}; {@code item}
 * is an item or a {@code #tag}). A luxury is <b>due</b> for a household when the day they last had one
 * ({@code luxuries_had}) is at least {@code every_days} ago, or they never had one.
 *
 * <p>At dawn, in the class engine's slices ({@link SocialClasses}), each household takes from the village store
 * ({@code VillageNeeds.store}: the kitchens' chests, then the Storehouses') one of every due luxury of its own class and
 * of the class above (the {@code luxury} needs and wants in those class files), and notes the day; a couple takes one
 * between them and both note it. Taking comes before the needs are checked, so a luxury taken at dawn holds that day; an
 * empty store leaves it unmet. Porters carry luxuries out of the makers' chests to the storehouse ({@code Porters.keeps}).
 */
public final class Luxuries implements ResourceManagerReloadListener {
	private static final ResourceLocation ID = AliveWorkplace.id("luxuries");
	public static final String FOLDER = "luxuries";

	/** A luxury: its id (the file's), what counts as one (an item or a tag), and how often a household wants one. */
	public record Luxury(ResourceLocation id, Optional<ResourceLocation> item, Optional<TagKey<Item>> tag, int everyDays) {
		public boolean matches(ItemStack stack) {
			if (stack.isEmpty()) {
				return false;
			}
			return item.map(i -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(i)).orElse(false)
				|| tag.map(stack::is).orElse(false);
		}
	}

	private static volatile Map<ResourceLocation, Luxury> luxuries = Map.of();

	/** The hook {@link ClassNeeds#luxuryEvery} is set to: a loaded luxury's {@code every_days}, 0 for an unknown one. */
	public static final ClassNeeds.LuxuryDays HOOK = id -> {
		Luxury l = luxuries.get(id);
		return l == null ? 0 : l.everyDays();
	};

	public static void init() {
		Platform.get().onDataReload(ID, new Luxuries());
		ClassNeeds.luxuryEvery = HOOK;
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		load(files(manager));
	}

	/** The luxury files {@code manager} holds (the top data pack's of each), by luxury id; an unreadable one is skipped with a warning. */
	public static Map<ResourceLocation, JsonElement> files(ResourceManager manager) {
		Map<ResourceLocation, JsonElement> files = new TreeMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				String path = e.getKey().getPath();
				files.put(ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(), path.substring(FOLDER.length() + 1, path.length() - ".json".length())),
					JsonParser.parseReader(reader));
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping luxury {}: {}", e.getKey(), ex.getMessage());
			}
		}
		return files;
	}

	/** Reads every luxury file; a broken one is skipped with a warning naming it, one switched off is left out. */
	public static void load(Map<ResourceLocation, JsonElement> files) {
		Map<ResourceLocation, Luxury> out = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, JsonElement> e : new TreeMap<>(files).entrySet()) {
			try {
				Luxury l = read(e.getKey(), e.getValue());
				if (l != null) {
					out.put(l.id(), l);
				}
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping luxury {}: {}", e.getKey(), ex.getMessage());
			}
		}
		luxuries = Map.copyOf(out);
	}

	/** Reads one luxury; null if it is switched off ({@code "enabled": false}) or its load conditions fail; throws naming the bad field. */
	@Nullable
	public static Luxury read(ResourceLocation id, JsonElement json) {
		JsonObject o = GsonHelper.convertToJsonObject(json, "luxury");
		if (!GsonHelper.getAsBoolean(o, "enabled", true) || !Guilds.conditionsMet(o)) {
			return null;
		}
		String item = GsonHelper.getAsString(o, "item");
		int every = GsonHelper.getAsInt(o, "every_days");
		if (every < 1) {
			throw new JsonSyntaxException("every_days: " + every + " is below 1");
		}
		if (item.startsWith("#")) {
			ResourceLocation tag = ClassNeeds.id(item.substring(1), "item");
			return new Luxury(id, Optional.empty(), Optional.of(TagKey.create(Registries.ITEM, tag)), every);
		}
		ResourceLocation itemId = ClassNeeds.id(item, "item");
		if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
			throw new JsonSyntaxException("item: no item " + itemId);
		}
		return new Luxury(id, Optional.of(itemId), Optional.empty(), every);
	}

	/** Every loaded luxury, by id. */
	public static Map<ResourceLocation, Luxury> all() {
		return luxuries;
	}

	@Nullable
	public static Luxury get(ResourceLocation id) {
		return luxuries.get(id);
	}

	/** Whether {@code stack} is one of any luxury (what a porter carries from a maker's chests to the storehouse). */
	public static boolean isLuxury(ItemStack stack) {
		for (Luxury l : luxuries.values()) {
			if (l.matches(stack)) {
				return true;
			}
		}
		return false;
	}

	/** The luxuries {@code c} asks for, in its needs and then its wants, that a file defines. */
	public static List<Luxury> of(@Nullable SocialClasses.SocialClass c) {
		List<Luxury> out = new ArrayList<>();
		if (c == null) {
			return out;
		}
		for (List<ClassNeeds.Need> list : List.of(c.needs(), c.wants())) {
			for (ClassNeeds.Need n : list) {
				if (n instanceof ClassNeeds.Luxury l) {
					Luxury luxury = luxuries.get(l.id());
					if (luxury != null && !out.contains(luxury)) {
						out.add(luxury);
					}
				}
			}
		}
		return out;
	}

	/** The day the household last had {@code luxury} (the latest of its members' records), or null if never. */
	@Nullable
	static Long lastHad(List<Villager> household, ResourceLocation luxury) {
		Long last = null;
		for (Villager v : household) {
			Long day = ModAttachments.LUXURIES_HAD.getOrElse(v, Map.of()).get(luxury);
			if (day != null && (last == null || day > last)) {
				last = day;
			}
		}
		return last;
	}

	/** Whether {@code luxury} is due for the household on {@code today}. */
	public static boolean due(List<Villager> household, Luxury luxury, long today) {
		Long last = lastHad(household, luxury.id());
		return last == null || today - last >= luxury.everyDays();
	}

	/**
	 * The household's dawn taking: one of each due luxury of {@code own} and of the class above, from the village store,
	 * the day noted on every member. Returns the luxuries taken (empty when none was due or the store had none).
	 */
	public static List<ResourceLocation> take(ServerLevel level, List<Villager> household, @Nullable SocialClasses.SocialClass own, ClassNeeds.Village village) {
		Set<Luxury> wanted = new LinkedHashSet<>(of(own));
		if (own != null) {
			wanted.addAll(of(SocialClasses.step(own, 1)));
		}
		List<ResourceLocation> taken = new ArrayList<>();
		long today = village.today();
		for (Luxury luxury : wanted) {
			if (!due(household, luxury, today)) {
				continue;
			}
			ItemStack got = SupplyContainers.takeOne(level, village.store(), luxury::matches);
			if (got.isEmpty()) {
				continue; // the store has none: the need goes unmet today
			}
			for (Villager v : household) {
				Map<ResourceLocation, Long> had = new HashMap<>(ModAttachments.LUXURIES_HAD.getOrElse(v, Map.of()));
				had.put(luxury.id(), today);
				ModAttachments.LUXURIES_HAD.set(v, had);
			}
			Villager lead = household.get(0);
			level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, got), lead.getX(), lead.getEyeY() - 0.2, lead.getZ(), 6, 0.2, 0.1, 0.2, 0.05);
			taken.add(luxury.id());
		}
		return taken;
	}

	private Luxuries() {
	}
}
