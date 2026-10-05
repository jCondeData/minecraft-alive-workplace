package io.github.jcondedata.aliveworkplace.camp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Extension;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Camp Cook (ROADMAP 28.8): a villager at a Campfire Pot (Cobblemon's campfire with a pot on it), picked with Hearty
 * Grains, who cooks in the pot itself. Her menu is data ({@code data/<ns>/camp_menu/<dish>.json}): the dish, how many to
 * keep in the chests by the pot, and when she makes it: {@code always}, {@code asked} (while a worker of one of the
 * {@code for} jobs works nearby) or {@code order} (only for a Storehouse's stock order). Needs Cobblemon; config
 * {@code campCooks}.
 */
public final class CampCooks implements ResourceManagerReloadListener {
	public static final ResourceLocation ID = AliveWorkplace.id("camp_menu");
	/** Config switch {@code campCooks}: off, Hearty Grains pick no job and cooks already hired stand idle. */
	public static boolean ENABLED = true;
	/** How far from the pot the workers who ask for a dish may work. */
	public static final int ASK_RANGE = 48;
	/** Her meals: they count as meals in the village store and for Diet variety. */
	public static final TagKey<Item> CAMP_MEALS = TagKey.create(Registries.ITEM, AliveWorkplace.id("camp_meals"));
	public static final ResourceLocation HEARTY_GRAINS = ResourceLocation.fromNamespaceAndPath("cobblemon", "hearty_grains");

	public enum When { ALWAYS, ASKED, ORDER }

	/** One line of the menu. {@code askedBy} is the jobs whose workers ask for it (only for {@code asked}). */
	public record Dish(ResourceLocation name, ResourceLocation item, int keep, When when, Set<ResourceLocation> askedBy, int order) {
	}

	/** One way of cooking a dish in the pot: the 3×3 grid (empty ingredients for empty slots) and what seasons it. */
	public record PotRecipe(List<Ingredient> grid, @Nullable TagKey<Item> seasoning) {
	}

	/** Cobblemon's Campfire Pot, filled by the compat layer. */
	public interface Pot {
		Extension<Pot> EXTENSION = new Extension<>("campfire pot");

		/** Whether this is a campfire with a pot on it. */
		boolean isPot(BlockState state);

		/** Whether the lid is shut (the pot cooks only then). */
		boolean lidShut(BlockState state);

		/** Shuts or opens the lid, as redstone does. */
		void setLid(ServerLevel level, BlockPos pos, boolean shut);

		/** Whether what's in the pot's grid makes a dish (so the shut pot is cooking it). */
		boolean cooks(ServerLevel level, BlockPos pos);

		/** The pot's recipes that make {@code dish}. */
		List<PotRecipe> recipes(ServerLevel level, Item dish);

		/** Puts {@code pot} on the Campfire Pot at {@code pos} (28.13: a builder building a Camp Kitchen). */
		default void fitPot(ServerLevel level, BlockPos pos, ItemStack pot) {
		}

		/** The seasonings (item ids) {@code dish} was cooked with (28.10: a Habitat Keeper's snacks). */
		default Set<ResourceLocation> seasonings(ItemStack dish) {
			return Set.of();
		}
	}

	/**
	 * Workers who ask for a dish seasoned a particular way (28.10: a Habitat Keeper asks for Poké Snacks seasoned with the
	 * berries of her lure): the seasonings wanted for {@code dish} at the pot, or none.
	 */
	public interface SeasoningAsk {
		Set<ResourceLocation> seasonings(ServerLevel level, BlockPos pot, Dish dish);
	}

	private static final List<SeasoningAsk> SEASONING_ASKS = new java.util.concurrent.CopyOnWriteArrayList<>();

	public static void onSeasoningAsk(SeasoningAsk ask) {
		SEASONING_ASKS.add(ask);
	}

	/** The seasonings the workers near the pot ask {@code dish} to be cooked with (empty: any). */
	public static Set<ResourceLocation> askedSeasonings(ServerLevel level, BlockPos pot, Dish dish) {
		if (dish.when() != When.ASKED) {
			return Set.of();
		}
		Set<ResourceLocation> out = new LinkedHashSet<>();
		for (SeasoningAsk ask : SEASONING_ASKS) {
			out.addAll(ask.seasonings(level, pot, dish));
		}
		return out;
	}

	private static List<Dish> menu = List.of();

	public static void init() {
		Platform.get().onDataReload(ID, new CampCooks());
	}

	public static List<Dish> menu() {
		return menu;
	}

	public static boolean isCook(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.CAMP_COOK;
	}

	@Nullable
	public static Pot pot() {
		return Pot.EXTENSION.call(p -> p, null);
	}

	/** Whether the job can be had: Cobblemon is there and the config switch is on. */
	public static boolean available() {
		return ENABLED && Platform.get().isModLoaded("cobblemon") && pot() != null;
	}

	/** Hearty Grains: what picks the job at a Campfire Pot. */
	public static boolean isGrains(ItemStack stack) {
		return available() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(HEARTY_GRAINS);
	}

	/** Her meals (Ponigiri, the stew, the curry, the sandwich, the dip, the tea). */
	public static boolean isCampMeal(ItemStack stack) {
		return !stack.isEmpty() && stack.is(CAMP_MEALS);
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		List<Dish> out = new ArrayList<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources("camp_menu", p -> p.getPath().endsWith(".json")).entrySet()) {
			ResourceLocation file = e.getKey();
			ResourceLocation name = ResourceLocation.fromNamespaceAndPath(file.getNamespace(),
				file.getPath().substring("camp_menu/".length(), file.getPath().length() - ".json".length()));
			try (Reader reader = e.getValue().openAsReader()) {
				out.add(parse(name, JsonParser.parseReader(reader).getAsJsonObject()));
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Alive Workplace: skipping camp menu dish {}: {}", file, ex.getMessage());
			}
		}
		out.sort(Comparator.comparingInt(Dish::order).thenComparing(d -> d.name().toString()));
		menu = List.copyOf(out);
		AliveWorkplace.LOG.info("Alive Workplace: {} camp menu dishes", menu.size());
	}

	/** Reads one menu file; throws {@link IllegalArgumentException} saying what's wrong. */
	public static Dish parse(ResourceLocation name, JsonObject json) {
		if (!json.has("dish")) {
			throw new IllegalArgumentException("no dish");
		}
		ResourceLocation item = ResourceLocation.parse(json.get("dish").getAsString());
		int keep = json.has("keep") ? json.get("keep").getAsInt() : 16;
		if (keep < 1 || keep > 256) {
			throw new IllegalArgumentException("keep must be 1 to 256");
		}
		When when = When.valueOf(json.has("when") ? json.get("when").getAsString().toUpperCase(Locale.ROOT) : "ALWAYS");
		Set<ResourceLocation> askedBy = new LinkedHashSet<>();
		if (json.has("for")) {
			JsonArray jobs = json.getAsJsonArray("for");
			jobs.forEach(j -> askedBy.add(ResourceLocation.parse(j.getAsString())));
		}
		if (when == When.ASKED && askedBy.isEmpty()) {
			throw new IllegalArgumentException("an asked dish needs \"for\": the jobs that ask for it");
		}
		int order = json.has("order") ? json.get("order").getAsInt() : 100;
		return new Dish(name, item, keep, when, Set.copyOf(askedBy), order);
	}

	/**
	 * How many of {@code stack}'s kind a Camp Cook keeps in the chests by her pot: what her menu says of a dish on it (the
	 * porter takes the rest to the store), and all her makings and seasonings.
	 */
	public static int keeps(ItemStack stack) {
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		for (Dish dish : menu) {
			if (dish.item().equals(id) && dish.when() != When.ORDER) {
				return dish.keep();
			}
		}
		for (Dish dish : menu) {
			if (dish.item().equals(id)) {
				return 0; // made for a stock order: it goes to the store
			}
		}
		return io.github.jcondedata.aliveworkplace.store.Porters.ALL;
	}

	/** Sets the menu (tests). */
	public static void setMenu(List<Dish> dishes) {
		menu = List.copyOf(dishes);
	}

	private CampCooks() {
	}
}
