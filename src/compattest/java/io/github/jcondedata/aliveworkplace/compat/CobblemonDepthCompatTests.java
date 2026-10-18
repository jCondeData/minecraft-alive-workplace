package io.github.jcondedata.aliveworkplace.compat;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Milestone 38 (Cobblemon depth, design note M38) builds on three of the pack's mods by their ids alone: Cobblemon Raid
 * Dens 0.11.3, Mega Showdown 1.8.4 and Cobblecuisine 2.0.1. This test lists every id the milestone reads and fails
 * naming any that's missing, so an update of one of those mods that renames something shows up here first.
 */
public class CobblemonDepthCompatTests implements FabricGameTest {
	static final String DENS = "cobblemonraiddens";
	static final String MEGA = "mega_showdown";
	static final String CUISINE = "cobblecuisine";
	static final List<String> TIERS = List.of("tier_one", "tier_two", "tier_three", "tier_four", "tier_five", "tier_six", "tier_seven");

	/** The three mods are in the compat runtime (stonecutter.properties.toml, tests.compat_mods). */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theThreeModsAreLoaded(GameTestHelper helper) {
		List<String> missing = new ArrayList<>();
		for (String mod : List.of(DENS, "geckolib", MEGA, CUISINE)) {
			if (!FabricLoader.getInstance().isModLoaded(mod)) {
				missing.add(mod);
			}
		}
		helper.assertTrue(missing.isEmpty(), "not loaded in the compat runtime: " + missing);
		helper.succeed();
	}

	/** Raid Dens: the den block and the states 38.2 reads by name, the items, advancements, loot tables and biome tag. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void raidDenIdsExist(GameTestHelper helper) {
		List<String> missing = new ArrayList<>();
		MinecraftServer server = helper.getLevel().getServer();
		Block den = block(DENS, "raid_crystal_block", missing);
		if (den != null) {
			for (String name : List.of("is_active", "raid_tier", "raid_type", "can_reset", "is_natural")) {
				if (property(den, name) == null) {
					missing.add("raid_crystal_block[" + name + "]");
				}
			}
			Property<?> tier = property(den, "raid_tier");
			if (tier != null) {
				for (String value : TIERS) {
					if (tier.getValue(value).isEmpty()) {
						missing.add("raid_tier=" + value + " (has " + tier.getPossibleValues() + ")");
					}
				}
			}
			Property<?> type = property(den, "raid_type");
			if (type != null && type.getValue("fire").isEmpty()) {
				missing.add("raid_type=fire");
			}
		}
		for (String item : List.of("raid_shard", "raid_pouch", "cheer_attack", "cheer_defense", "cheer_heal", "catching_charm")) {
			item(DENS, item, missing);
		}
		List<String> advancements = new ArrayList<>(List.of("join_raid"));
		advancements.addAll(TIERS);
		for (String advancement : advancements) {
			if (server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(DENS, advancement)) == null) {
				missing.add("advancement " + DENS + ":" + advancement);
			}
		}
		for (String tier : TIERS) {
			ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(DENS, "raid/tier/" + tier));
			if (server.reloadableRegistries().getLootTable(key) == LootTable.EMPTY) {
				missing.add("loot table " + key.location());
			}
		}
		TagKey<net.minecraft.world.level.biome.Biome> spawnable = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(DENS, "raid_spawnable"));
		if (server.registryAccess().registryOrThrow(Registries.BIOME).getTag(spawnable).isEmpty()) {
			missing.add("biome tag #" + spawnable.location());
		}
		helper.assertTrue(missing.isEmpty(), "Raid Dens ids missing: " + missing);
		helper.succeed();
	}

	/** Mega Showdown: the Key Stone, the bracelet and stone tags, the ores and crystal (38.6) and the shrine's blocks (38.7). */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void megaIdsExist(GameTestHelper helper) {
		List<String> missing = new ArrayList<>();
		item(MEGA, "keystone", missing);
		for (String tag : List.of("mega_bracelet", "mega_stone")) {
			TagKey<net.minecraft.world.item.Item> key = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(MEGA, tag));
			if (BuiltInRegistries.ITEM.getTag(key).map(set -> set.size() == 0).orElse(true)) {
				missing.add("item tag #" + key.location());
			}
		}
		for (String block : List.of("keystone_ore", "mega_stone_crystal", "pedestal", "mega_meteorid_block", "mega_meteorid_brick")) {
			block(MEGA, block, missing);
		}
		helper.assertTrue(missing.isEmpty(), "Mega Showdown ids missing: " + missing);
		helper.succeed();
	}

	/** Cobblecuisine: the bean crop and its age (38.9), the seven beans, the dishes the design note names, and their recipes. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void cuisineIdsExist(GameTestHelper helper) {
		List<String> missing = new ArrayList<>();
		MinecraftServer server = helper.getLevel().getServer();
		Block crop = block(CUISINE, "bean_crop", missing);
		if (crop != null && property(crop, "age") == null) {
			missing.add("bean_crop[age]");
		}
		// The ids the bean farms read (38.9): the crop and the seeds, by name.
		if (crop != null && !io.github.jcondedata.aliveworkplace.farm.BeanFarms.isCrop(crop.defaultBlockState())) {
			missing.add("BeanFarms doesn't know cobblecuisine:bean_crop");
		}
		net.minecraft.world.item.Item seeds = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(CUISINE, "bean_seeds"));
		if (!io.github.jcondedata.aliveworkplace.farm.BeanFarms.isSeed(new net.minecraft.world.item.ItemStack(seeds))) {
			missing.add("BeanFarms doesn't know cobblecuisine:bean_seeds");
		}
		List<String> beans = List.of("red_bean", "green_bean", "yellow_bean", "blue_bean", "orange_bean", "violet_bean", "indigo_bean");
		item(CUISINE, "bean_seeds", missing);
		beans.forEach(bean -> item(CUISINE, bean, missing));
		TagKey<net.minecraft.world.item.Item> beanTag = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(CUISINE, "beans"));
		int tagged = BuiltInRegistries.ITEM.getTag(beanTag).map(set -> set.size()).orElse(0);
		if (tagged != beans.size()) {
			missing.add("#cobblecuisine:beans holds " + tagged + " items, not " + beans.size());
		}
		// The design note's example dishes: a salad, a sandwich, a curry, a cake, the three drinks, a Poképuff, a deluxe shake, the éclair.
		List<String> dishes = List.of("mixed_vegetable_salad", "sweet_jam_sandwich", "bean_medley_curry", "fruit_honey_cake", "lemon_soda",
			"coffee", "fruit_punch", "sweet_pokepuff", "deluxe_jewel_shake", "eclair");
		// A dish is cooked from a recipe whose result it is: recipe ids don't always match (cake_honey_fruit makes fruit_honey_cake).
		java.util.Set<net.minecraft.world.item.Item> cooked = new java.util.HashSet<>();
		int recipes = 0;
		for (net.minecraft.world.item.crafting.RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
			if (holder.id().getNamespace().equals(CUISINE)) {
				recipes++;
				cooked.add(holder.value().getResultItem(server.registryAccess()).getItem());
			}
		}
		if (recipes < 200) {
			missing.add("only " + recipes + " Cobblecuisine recipes");
		}
		for (String dish : dishes) {
			net.minecraft.world.item.Item item = item(CUISINE, dish, missing);
			if (item != null && !cooked.contains(item)) {
				missing.add("a recipe making " + CUISINE + ":" + dish);
			}
		}
		for (String food : List.of("mixed_vegetable_salad", "bean_medley_curry", "lemon_soda")) {
			BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(CUISINE, food))
				.filter(item -> !new ItemStack(item).has(DataComponents.FOOD))
				.ifPresent(item -> missing.add(food + " isn't food (the villagers' diet needs it to be)"));
		}
		// The partner treats and dish luxuries (38.10): every Poké Puff and shake the treat tag lists, the six deluxe
		// shakes and the éclair, each an item the tags then hold.
		List<String> treats = new ArrayList<>();
		for (String puff : List.of("sweet", "mint", "citrus", "mocha", "spice")) {
			treats.add(puff + "_pokepuff");
		}
		for (String strength : List.of("low", "medium", "high")) {
			for (String colour : List.of("green", "red", "blue", "purple", "yellow", "pink")) {
				treats.add(strength + "_" + colour + "_shake");
			}
		}
		for (String treat : treats) {
			net.minecraft.world.item.Item item = item(CUISINE, treat, missing);
			if (item != null && !io.github.jcondedata.aliveworkplace.work.PartnerTreats.isTreat(new ItemStack(item))) {
				missing.add(treat + " isn't a partner treat");
			}
		}
		TagKey<net.minecraft.world.item.Item> deluxeTag = TagKey.create(Registries.ITEM, io.github.jcondedata.aliveworkplace.AliveWorkplace.id("deluxe_shakes"));
		for (String shake : List.of("jewel", "earthy", "violet", "verdant", "coral", "bb")) {
			net.minecraft.world.item.Item item = item(CUISINE, "deluxe_" + shake + "_shake", missing);
			if (item != null && !new ItemStack(item).is(deluxeTag)) {
				missing.add("deluxe_" + shake + "_shake isn't in #aliveworkplace:deluxe_shakes");
			}
		}
		item(CUISINE, "eclair", missing);
		helper.assertTrue(missing.isEmpty(), "Cobblecuisine ids missing: " + missing);
		helper.succeed();
	}

	/**
	 * 38.8: every dish the shipped dish lists ({@code cuisine_dishes}) and the Camp Cook's curries ({@code camp_menu})
	 * name is a Cobblecuisine item with a recipe that makes it, and every ingredient of one such recipe matches some item
	 * (a recipe asking for a tag nobody fills, as Cobblecuisine's {@code #cobblemon:mint_leaf} is on Cobblemon 1.7.3,
	 * could never be cooked).
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void cuisineDishListIdsExist(GameTestHelper helper) {
		List<String> missing = new ArrayList<>();
		MinecraftServer server = helper.getLevel().getServer();
		net.minecraft.server.packs.resources.ResourceManager resources = server.getResourceManager();
		List<ResourceLocation> ids = new ArrayList<>();
		resources.listResources("cuisine_dishes", p -> p.getNamespace().equals("aliveworkplace") && p.getPath().endsWith(".json")).forEach((file, res) -> {
			try (java.io.Reader reader = res.openAsReader()) {
				com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
				io.github.jcondedata.aliveworkplace.craft.CuisineDishes.parse(json, FabricLoader.getInstance()::isModLoaded).forEach(e -> ids.add(e.id()));
			} catch (Exception ex) {
				missing.add(file + " doesn't read: " + ex.getMessage());
			}
		});
		resources.listResources("camp_menu", p -> p.getNamespace().equals("aliveworkplace") && p.getPath().contains("cobblecuisine")).forEach((file, res) -> {
			try (java.io.Reader reader = res.openAsReader()) {
				ids.add(ResourceLocation.parse(com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().get("dish").getAsString()));
			} catch (Exception ex) {
				missing.add(file + " doesn't read: " + ex.getMessage());
			}
		});
		if (ids.size() < 25) {
			missing.add("only " + ids.size() + " dishes listed");
		}
		for (ResourceLocation id : ids) {
			net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
			if (item == null) {
				missing.add("item " + id);
				continue;
			}
			boolean cookable = false;
			boolean anyRecipe = false;
			for (net.minecraft.world.item.crafting.RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
				if (holder.value().getType() != net.minecraft.world.item.crafting.RecipeType.CRAFTING
					|| !holder.value().getResultItem(server.registryAccess()).is(item)) {
					continue;
				}
				anyRecipe = true;
				// (an empty tag shows as a named barrier, which isn't a plain item: Crafting.options leaves it out)
				cookable |= holder.value().getIngredients().stream()
					.allMatch(ing -> ing.isEmpty() || !io.github.jcondedata.aliveworkplace.craft.Crafting.options(helper.getLevel(), ing).isEmpty());
			}
			if (!anyRecipe) {
				missing.add("a crafting recipe making " + id);
			} else if (!cookable) {
				missing.add(id + ": every recipe asks for something no item is");
			}
			if (io.github.jcondedata.aliveworkplace.craft.CuisineDishes.of(item) == null) {
				missing.add(id + " isn't on the loaded dish lists");
			}
		}
		helper.assertTrue(missing.isEmpty(), "Cobblecuisine dish list ids missing: " + missing);
		helper.succeed();
	}

	static Block block(String namespace, String path, List<String> missing) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
		Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
		if (block == null) {
			missing.add("block " + id);
		}
		return block;
	}

	static net.minecraft.world.item.Item item(String namespace, String path, List<String> missing) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
		net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
		if (item == null) {
			missing.add("item " + id);
		}
		return item;
	}

	static Property<?> property(Block block, String name) {
		return block.getStateDefinition().getProperty(name);
	}
}
