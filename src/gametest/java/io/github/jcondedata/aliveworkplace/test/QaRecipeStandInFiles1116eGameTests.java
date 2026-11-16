package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.RecipeStandIns;
import io.github.jcondedata.aliveworkplace.mc.Recipes;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * QA (lane E, 2026-11-16), the recipe stand-in data files from a datapack author's side (wiki crafter.md, "Special
 * recipes as data (B1302)"): a file in {@code data/<namespace>/recipe_stand_ins/} adds a one-slot recipe (any one of
 * {@code from}, not {@code except}, makes {@code result}) while a recipe of its {@code serializer} is loaded; of
 * {@code from}, only what a crafting recipe makes is kept when anything is; "a datapack can add more". In the base game
 * (no Carved Wood) the shipped chest file counts for nothing. Vanilla's own special recipe
 * ({@code minecraft:crafting_special_repairitem}, always loaded) stands in for another mod's here. Each check reads the
 * files through the mod's real loader (as a {@code /reload} does) and indexes a fresh copy of the server's recipes (what a
 * reload makes); the server's own files are read back at the end.
 */
public class QaRecipeStandInFiles1116eGameTests {
	private static final String BATCH = "qaStandInFiles1116e";
	private static final String LOADED = "minecraft:crafting_special_repairitem";

	private static String standIn(String serializer, String from, String except, String result) {
		return "{\"serializer\": \"" + serializer + "\", \"from\": [" + from + "]"
			+ (except == null ? "" : ", \"except\": [" + except + "]") + ", \"result\": \"" + result + "\"}";
	}

	/** The loader's real reload, given the server's data plus a datapack's {@code recipe_stand_ins} files. */
	static void reloadWith(GameTestHelper helper, Map<String, String> files) {
		ResourceManager real = helper.getLevel().getServer().getResourceManager();
		Map<ResourceLocation, Resource> shipped = real.listResources(RecipeStandIns.FOLDER, p -> p.getPath().endsWith(".json"));
		PackResources pack = shipped.values().stream().findFirst().map(Resource::source)
			.orElseThrow(() -> new AssertionError("setup: the server has no recipe_stand_ins file at all"));
		Map<ResourceLocation, Resource> datapack = new LinkedHashMap<>();
		files.forEach((name, text) -> {
			byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
			datapack.put(ResourceLocation.fromNamespaceAndPath("qa_pack", RecipeStandIns.FOLDER + "/" + name + ".json"),
				new Resource(pack, () -> new ByteArrayInputStream(bytes)));
		});
		ResourceManager withPack = new ResourceManager() {
			@Override
			public Set<String> getNamespaces() {
				return real.getNamespaces();
			}

			@Override
			public List<Resource> getResourceStack(ResourceLocation id) {
				return datapack.containsKey(id) ? List.of(datapack.get(id)) : real.getResourceStack(id);
			}

			@Override
			public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) {
				Map<ResourceLocation, Resource> out = new LinkedHashMap<>(real.listResources(path, filter));
				datapack.forEach((id, r) -> {
					if (id.getPath().startsWith(path + "/") && filter.test(id)) {
						out.put(id, r);
					}
				});
				return out;
			}

			@Override
			public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> filter) {
				return real.listResourceStacks(path, filter);
			}

			@Override
			public Stream<PackResources> listPacks() {
				return real.listPacks();
			}

			@Override
			public Optional<Resource> getResource(ResourceLocation id) {
				return datapack.containsKey(id) ? Optional.of(datapack.get(id)) : real.getResource(id);
			}
		};
		loader().onResourceManagerReload(withPack);
	}

	/** The server's own files read again (what a {@code /reload} without the datapack does). */
	static void restore(GameTestHelper helper) {
		loader().onResourceManagerReload(helper.getLevel().getServer().getResourceManager());
	}

	private static ResourceManagerReloadListener loader() {
		try {
			Constructor<?> c = Class.forName(RecipeStandIns.class.getName() + "$Loader").getDeclaredConstructor();
			c.setAccessible(true);
			return (ResourceManagerReloadListener) c.newInstance();
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("setup: can't make the stand-in loader", e);
		}
	}

	/** A fresh copy of the server's recipes (what a reload makes), indexed anew: the crafting recipes that make {@code target}. */
	static List<RecipeHolder<?>> freshRecipesFor(GameTestHelper helper, Item target) {
		ServerLevel level = helper.getLevel();
		RecipeManager fresh = new RecipeManager(level.registryAccess());
		fresh.replaceRecipes(new ArrayList<>(Recipes.all(Recipes.manager(level))));
		return Crafting.recipesFor(fresh, level.registryAccess(), Crafting.Kind.CRAFTING, target);
	}

	/** The single slot's items of the recipes that make it: the stand-in's options. */
	private static List<Item> options(List<RecipeHolder<?>> recipes) {
		List<Item> out = new ArrayList<>();
		for (RecipeHolder<?> holder : recipes) {
			for (Ingredient ingredient : Recipes.ingredients(holder.value())) {
				for (ItemStack stack : ingredient.getItems()) {
					out.add(stack.getItem());
				}
			}
		}
		return out;
	}

	/** A datapack's stand-in for a loaded special recipe makes its result plannable; one for a serializer nobody loads doesn't. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' 'BATCH'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
	public void aDatapacksStandInCountsOnlyWhileItsSerializerIsLoaded(GameTestHelper helper) {
		try {
			helper.assertTrue(freshRecipesFor(helper, Items.BELL).isEmpty(), "setup: the base game already crafts a bell");
			reloadWith(helper, Map.of(
				"bell", standIn(LOADED, "\"minecraft:gold_block\"", null, "minecraft:bell"),
				"saddle", standIn("qa_nobody:not_loaded", "\"minecraft:leather\"", null, "minecraft:saddle")));
			helper.assertTrue(RecipeStandIns.all().size() == 3, "the shipped file and the datapack's two didn't all load: " + RecipeStandIns.all());
			List<Item> bell = options(freshRecipesFor(helper, Items.BELL));
			helper.assertTrue(bell.equals(List.of(Items.GOLD_BLOCK)), "a stand-in for a loaded special recipe made a bell from " + bell + ", not a gold block");
			helper.assertTrue(freshRecipesFor(helper, Items.SADDLE).isEmpty(), "a stand-in for a serializer nobody loads made a saddle plannable");
			restore(helper);
			helper.assertTrue(freshRecipesFor(helper, Items.BELL).isEmpty(), "the datapack's bell stayed after a reload without it");
		} finally {
			restore(helper);
		}
		helper.succeed();
	}

	/** In the base game the shipped Carved Wood chest file counts for nothing: a chest is made from planks only. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' 'BATCH'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
	public void withoutCarvedWoodTheChestFileAddsNothing(GameTestHelper helper) {
		restore(helper);
		helper.assertTrue(RecipeStandIns.all().stream().anyMatch(s -> s.result().equals(ResourceLocation.parse("minecraft:chest"))),
			"setup: the shipped chest stand-in isn't loaded");
		List<RecipeHolder<?>> chests = freshRecipesFor(helper, Items.CHEST);
		helper.assertTrue(!chests.isEmpty(), "no chest recipe in the base game");
		for (RecipeHolder<?> holder : chests) {
			helper.assertTrue(holder.id().getNamespace().equals("minecraft"), "a chest recipe that isn't the game's own in the base game: " + holder.id());
		}
		helper.succeed();
	}

	/**
	 * The boundaries of what a stand-in is made from: {@code except} takes things out (all of them: no recipe), the
	 * result is never made from itself (a stand-in of only that: no recipe), and of {@code from} only what a crafting
	 * recipe makes is kept when anything is (none: all kept).
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' 'BATCH'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
	public void exceptTheResultItselfAndUncraftableOptionsAreLeftOut(GameTestHelper helper) {
		try {
			reloadWith(helper, Map.of(
				"bell_all_except", standIn(LOADED, "\"minecraft:gold_block\", \"minecraft:iron_block\"", "\"minecraft:gold_block\", \"minecraft:iron_block\"", "minecraft:bell")));
			helper.assertTrue(freshRecipesFor(helper, Items.BELL).isEmpty(), "a stand-in whose except takes out everything still made a bell");

			reloadWith(helper, Map.of("bell_itself", standIn(LOADED, "\"minecraft:bell\"", null, "minecraft:bell")));
			helper.assertTrue(freshRecipesFor(helper, Items.BELL).isEmpty(), "a bell was made from a bell");

			reloadWith(helper, Map.of("bell_some_except", standIn(LOADED, "\"minecraft:gold_block\", \"minecraft:iron_block\"", "\"minecraft:iron_block\"", "minecraft:bell")));
			List<Item> some = options(freshRecipesFor(helper, Items.BELL));
			helper.assertTrue(some.equals(List.of(Items.GOLD_BLOCK)), "except one of two left " + some + ", not the gold block alone");

			// gold block is crafted (from ingots), nether star and dragon egg are not
			reloadWith(helper, Map.of("bell_mixed", standIn(LOADED, "\"minecraft:nether_star\", \"minecraft:gold_block\"", null, "minecraft:bell")));
			List<Item> mixed = options(freshRecipesFor(helper, Items.BELL));
			helper.assertTrue(mixed.equals(List.of(Items.GOLD_BLOCK)), "a crafted and an uncrafted option left " + mixed + ", not the crafted gold block alone");

			reloadWith(helper, Map.of("bell_none_crafted", standIn(LOADED, "\"minecraft:nether_star\", \"minecraft:dragon_egg\"", null, "minecraft:bell")));
			List<Item> none = options(freshRecipesFor(helper, Items.BELL));
			helper.assertTrue(none.size() == 2 && none.contains(Items.NETHER_STAR) && none.contains(Items.DRAGON_EGG),
				"with no option a recipe makes, both should stay; got " + none);

			reloadWith(helper, Map.of("bell_tag", standIn(LOADED, "\"#minecraft:logs\", \"qa_absent:not_an_item\"", null, "minecraft:bell")));
			List<Item> logs = options(freshRecipesFor(helper, Items.BELL));
			// #logs holds wood blocks a recipe makes (oak wood from oak logs), so only those are kept; the absent item is left out
			helper.assertTrue(logs.contains(Items.OAK_WOOD) && logs.contains(Items.SPRUCE_WOOD) && !logs.contains(Items.OAK_LOG),
				"a tag in from gave " + logs + ", not its crafted woods alone");
		} finally {
			restore(helper);
		}
		helper.succeed();
	}

	/**
	 * A datapack author's broken files are skipped with the rest still loaded (as the loader's 'Skipping recipe stand-in'
	 * warning promises): no serializer, no result, an empty from, a result id that isn't one. Crafting keeps planning.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' 'BATCH'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
	public void brokenFilesAreSkippedAndTheRestLoad(GameTestHelper helper) {
		try {
			reloadWith(helper, Map.of(
				"a_good", standIn(LOADED, "\"minecraft:gold_block\"", null, "minecraft:bell"),
				"no_serializer", "{\"from\": [\"minecraft:leather\"], \"result\": \"minecraft:saddle\"}",
				"no_result", "{\"serializer\": \"" + LOADED + "\", \"from\": [\"minecraft:leather\"]}",
				"empty_from", standIn(LOADED, "", null, "minecraft:saddle"),
				"bad_result", standIn(LOADED, "\"minecraft:leather\"", null, "minecraft:Not A Saddle"),
				"not_json", "{ this is not json",
				"an_array", "[1, 2, 3]"));
			helper.assertTrue(RecipeStandIns.all().size() == 2, "only the shipped file and the good one should load: " + RecipeStandIns.all());
			helper.assertTrue(options(freshRecipesFor(helper, Items.BELL)).equals(List.of(Items.GOLD_BLOCK)), "the good file's bell isn't plannable");
			helper.assertTrue(freshRecipesFor(helper, Items.SADDLE).isEmpty(), "a broken file made a saddle plannable");
			helper.assertTrue(!freshRecipesFor(helper, Items.CHEST).isEmpty(), "crafting lost the chest");
		} finally {
			restore(helper);
		}
		helper.succeed();
	}
}
