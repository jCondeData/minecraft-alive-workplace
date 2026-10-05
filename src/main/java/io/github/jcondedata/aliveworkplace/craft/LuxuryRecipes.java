package io.github.jcondedata.aliveworkplace.craft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.people.ClassNeeds;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The luxury workshops' recipes (ROADMAP 34.5), read from {@code data/<namespace>/luxury_recipes/<id>.json}:
 * {@code {"job": "aliveworkplace:vintner", "level": 3, "inputs": [{"item": "aliveworkplace:berry_wine", "count": 1,
 * "min_age_days": 3}], "output": {"id": "aliveworkplace:vintage_wine"}, "ticks": 200}}. An input is an {@code item} or a
 * {@code tag} ({@code #} optional); {@code level} (default 1), {@code count} (1), {@code min_age_days} (0) and the
 * output's {@code count} (1) may be left out, {@code ticks} is 100 if missing. A file can be switched off with
 * {@code "enabled": false} or load conditions, like the other data files.
 *
 * <p>Goods that some recipe wants aged carry the day they were made ({@link MadeDay}, the {@code made_day} component),
 * stamped by the maker; a stack without one (old stock, a player's) counts as aged.
 */
public final class LuxuryRecipes implements ResourceManagerReloadListener {
	private static final ResourceLocation ID = AliveWorkplace.id("luxury_recipes");
	public static final String FOLDER = "luxury_recipes";

	/** One making: one of {@code options} (an item, or a tag's items), {@code count} a craft, at least {@code minAgeDays} old. */
	public record Input(Optional<Item> item, Optional<TagKey<Item>> tag, int count, int minAgeDays) {
		/** The items that will do, in the tag's order. */
		public List<Item> options() {
			if (item.isPresent()) {
				return List.of(item.get());
			}
			List<Item> out = new ArrayList<>();
			Lookup.tag(BuiltInRegistries.ITEM, tag.get()).ifPresent(set -> {
				for (Holder<Item> h : set) {
					out.add(h.value());
				}
			});
			return out;
		}

		public boolean matches(ItemStack stack) {
			return item.map(stack::is).orElse(false) || tag.map(stack::is).orElse(false);
		}
	}

	/** A luxury recipe: who may make it (the job, from its level up), the makings, what comes out and the ticks one takes. */
	public record Recipe(ResourceLocation id, ResourceLocation job, int level, List<Input> inputs, Item output, int count, int ticks) {
	}

	/** The day a good was made (the chronicle's day number) and how many days it takes to be vintage (0: it doesn't age). */
	public record MadeDay(long day, int vintageDays) {
		public static final Codec<MadeDay> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("day").forGetter(MadeDay::day),
			Codec.INT.optionalFieldOf("vintage_days", 0).forGetter(MadeDay::vintageDays)
		).apply(i, MadeDay::new));
		public static final StreamCodec<io.netty.buffer.ByteBuf, MadeDay> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, MadeDay::day, ByteBufCodecs.VAR_INT, MadeDay::vintageDays, MadeDay::new);
	}

	private static volatile Map<ResourceLocation, Recipe> recipes = Map.of();

	public static void init() {
		Platform.get().onDataReload(ID, new LuxuryRecipes());
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		load(files(manager));
	}

	/** The recipe files {@code manager} holds, by recipe id; an unreadable one is skipped with a warning. */
	public static Map<ResourceLocation, JsonElement> files(ResourceManager manager) {
		Map<ResourceLocation, JsonElement> files = new TreeMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				String path = e.getKey().getPath();
				files.put(ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(), path.substring(FOLDER.length() + 1, path.length() - ".json".length())),
					JsonParser.parseReader(reader));
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping luxury recipe {}: {}", e.getKey(), ex.getMessage());
			}
		}
		return files;
	}

	/** Reads every recipe file; a broken one is skipped with a warning naming it, one switched off is left out. */
	public static void load(Map<ResourceLocation, JsonElement> files) {
		Map<ResourceLocation, Recipe> out = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, JsonElement> e : new TreeMap<>(files).entrySet()) {
			try {
				Recipe r = read(e.getKey(), e.getValue());
				if (r != null) {
					out.put(r.id(), r);
				}
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping luxury recipe {}: {}", e.getKey(), ex.getMessage());
			}
		}
		recipes = java.util.Collections.unmodifiableMap(out); // in the files' order
	}

	/** Reads one recipe; null if it is switched off; throws naming the bad field. */
	@Nullable
	public static Recipe read(ResourceLocation id, JsonElement json) {
		JsonObject o = GsonHelper.convertToJsonObject(json, "luxury recipe");
		if (!GsonHelper.getAsBoolean(o, "enabled", true) || !Guilds.conditionsMet(o)) {
			return null;
		}
		ResourceLocation job = ClassNeeds.id(GsonHelper.getAsString(o, "job"), "job");
		if (!BuiltInRegistries.VILLAGER_PROFESSION.containsKey(job)) {
			throw new JsonSyntaxException("job: no job " + job);
		}
		int level = GsonHelper.getAsInt(o, "level", 1);
		if (level < 1 || level > 5) {
			throw new JsonSyntaxException("level: " + level + " is not 1 to 5");
		}
		List<Input> inputs = new ArrayList<>();
		for (JsonElement el : GsonHelper.getAsJsonArray(o, "inputs")) {
			JsonObject in = GsonHelper.convertToJsonObject(el, "input");
			int count = GsonHelper.getAsInt(in, "count", 1);
			int age = GsonHelper.getAsInt(in, "min_age_days", 0);
			if (count < 1 || age < 0) {
				throw new JsonSyntaxException("inputs: count " + count + " or min_age_days " + age + " out of range");
			}
			if (in.has("tag")) {
				String tag = GsonHelper.getAsString(in, "tag");
				ResourceLocation tagId = ClassNeeds.id(tag.startsWith("#") ? tag.substring(1) : tag, "tag");
				inputs.add(new Input(Optional.empty(), Optional.of(TagKey.create(Registries.ITEM, tagId)), count, age));
			} else {
				inputs.add(new Input(Optional.of(item(GsonHelper.getAsString(in, "item"), "inputs.item")), Optional.empty(), count, age));
			}
		}
		if (inputs.isEmpty()) {
			throw new JsonSyntaxException("inputs: none");
		}
		JsonObject output = GsonHelper.getAsJsonObject(o, "output");
		int count = GsonHelper.getAsInt(output, "count", 1);
		int ticks = GsonHelper.getAsInt(o, "ticks", 100);
		if (count < 1 || ticks < 1) {
			throw new JsonSyntaxException("output.count " + count + " or ticks " + ticks + " below 1");
		}
		return new Recipe(id, job, level, List.copyOf(inputs), item(GsonHelper.getAsString(output, "id"), "output.id"), count, ticks);
	}

	private static Item item(String text, String field) {
		ResourceLocation id = ClassNeeds.id(text, field);
		if (!BuiltInRegistries.ITEM.containsKey(id)) {
			throw new JsonSyntaxException(field + ": no item " + id);
		}
		return Lookup.value(BuiltInRegistries.ITEM, id);
	}

	/** Every loaded recipe, by id. */
	public static Map<ResourceLocation, Recipe> all() {
		return recipes;
	}

	/** The recipes that make {@code item}. */
	public static List<Recipe> making(Item item) {
		List<Recipe> out = new ArrayList<>();
		for (Recipe r : recipes.values()) {
			if (r.output() == item) {
				out.add(r);
			}
		}
		return out;
	}

	/** The recipes {@code job} may make at {@code level}. */
	public static List<Recipe> forJob(VillagerProfession job, int level) {
		ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(job);
		List<Recipe> out = new ArrayList<>();
		for (Recipe r : recipes.values()) {
			if (r.job().equals(id) && r.level() <= level) {
				out.add(r);
			}
		}
		return out;
	}

	/** Whether some recipe names {@code job} (a luxury maker: Craftsmanship speeds them up). */
	public static boolean isMaker(VillagerProfession job) {
		ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(job);
		for (Recipe r : recipes.values()) {
			if (r.job().equals(id)) {
				return true;
			}
		}
		return false;
	}

	/** How many days old {@code stack} must be for any recipe that takes it aged (0: no recipe wants it aged). */
	public static int agingDays(ItemStack stack) {
		int days = 0;
		for (Recipe r : recipes.values()) {
			for (Input in : r.inputs()) {
				if (in.minAgeDays() > days && in.matches(stack)) {
					days = in.minAgeDays();
				}
			}
		}
		return days;
	}

	/** Today's day number, as the chronicle counts (the first day is day 1). */
	public static long today(Level level) {
		return level.getDayTime() / 24000L + 1;
	}

	/** Whether {@code stack} is at least {@code days} old on {@code today} (one without a made day counts as old). */
	public static boolean agedFor(ItemStack stack, int days, long today) {
		MadeDay made = stack.get(ModComponents.MADE_DAY);
		return days <= 0 || made == null || today - made.day() >= days;
	}

	/** Whether {@code stack} is plain apart from its made day (so a maker may take it as a making). */
	public static boolean plainButMadeDay(ItemStack stack) {
		if (stack.getComponentsPatch().isEmpty()) {
			return true;
		}
		return stack.getComponentsPatch().size() == 1 && stack.has(ModComponents.MADE_DAY);
	}

	/** {@code stack} stamped with {@code today} if some recipe wants it aged (unchanged otherwise). */
	public static ItemStack stamp(ItemStack stack, long today) {
		int days = agingDays(stack);
		if (days > 0 && !stack.has(ModComponents.MADE_DAY)) {
			stack.set(ModComponents.MADE_DAY, new MadeDay(today, days));
		}
		return stack;
	}

	/** The tooltip line: "Pressed on day 42 · vintage in 2 days" (or "· vintage" once it's old enough). */
	public static Component tooltip(MadeDay made, long today) {
		long left = made.day() + made.vintageDays() - today;
		Component line;
		if (made.vintageDays() <= 0) {
			line = Component.translatable("tooltip.aliveworkplace.made_day.made", made.day());
		} else if (left <= 0) {
			line = Component.translatable("tooltip.aliveworkplace.made_day.vintage", made.day());
		} else if (left == 1) {
			line = Component.translatable("tooltip.aliveworkplace.made_day.days.one", made.day(), left);
		} else {
			line = Component.translatable("tooltip.aliveworkplace.made_day.days", made.day(), left);
		}
		return line.copy().withStyle(ChatFormatting.GRAY);
	}

	private LuxuryRecipes() {
	}
}
