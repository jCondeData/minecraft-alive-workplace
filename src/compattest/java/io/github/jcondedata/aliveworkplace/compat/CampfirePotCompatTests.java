package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.CobblemonRecipeTypes;
import io.github.jcondedata.aliveworkplace.craft.Chefs;
import io.github.jcondedata.aliveworkplace.craft.CuisineDishes;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;

/**
 * ROADMAP 38.11 with the real Cobblemon: the Chef cooks every Campfire Pot recipe, found by its recipe type (Cobblemon's
 * own, and one this test pack adds: a Rare Candy), from the makings in the chests, and nothing without all of them.
 */
public class CampfirePotCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos STATION = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	private static Item cm(String path) {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", path));
		if (item == Items.AIR) {
			throw new AssertionError("no cobblemon:" + path);
		}
		return item;
	}

	private static Container chest(GameTestHelper helper, ItemStack... items) {
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < items.length; i++) {
			chest.setItem(i, items[i]);
		}
		return chest;
	}

	private static Villager chef(GameTestHelper helper) {
		helper.setDayTime(2000);
		helper.setBlock(STATION, ModBlocks.KITCHEN_STOVE);
		Villager chef = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), chef, helper.absolutePos(STATION), ModVillagers.KITCHEN_STOVE_POI, ModVillagers.CHEF);
		return chef;
	}

	/** Every plain result of the recipes of Cobblemon's two pot types, read from the recipe manager. */
	private static Set<Item> potResults(GameTestHelper helper) {
		Set<Item> out = new LinkedHashSet<>();
		var manager = helper.getLevel().getRecipeManager();
		var registries = helper.getLevel().registryAccess();
		for (RecipeHolder<?> holder : manager.getAllRecipesFor(CobblemonRecipeTypes.INSTANCE.getCOOKING_POT_COOKING())) {
			ItemStack result = holder.value().getResultItem(registries);
			if (!result.isEmpty() && result.getComponentsPatch().isEmpty()) {
				out.add(result.getItem());
			}
		}
		for (RecipeHolder<?> holder : manager.getAllRecipesFor(CobblemonRecipeTypes.INSTANCE.getCOOKING_POT_SHAPELESS())) {
			ItemStack result = holder.value().getResultItem(registries);
			if (!result.isEmpty() && result.getComponentsPatch().isEmpty()) {
				out.add(result.getItem());
			}
		}
		return out;
	}

	/**
	 * The Done when: every Campfire Pot recipe the installed mods register is on a Novice Chef's menu, counted from the
	 * recipe manager by type: Cobblemon's candies, medicines and sweets, and the test pack's own Rare Candy recipe.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyCampfirePotRecipeIsOnTheMenu(GameTestHelper helper) {
		Villager chef = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
		chef.setVillagerData(chef.getVillagerData().setProfession(ModVillagers.CHEF).setLevel(1));
		Set<Item> results = potResults(helper);
		helper.assertTrue(results.size() >= 80, "only " + results.size() + " Campfire Pot dishes in the recipe manager");
		List<Item> menu = Chefs.menu(helper.getLevel(), chef);
		for (Item dish : results) {
			helper.assertTrue(menu.contains(dish), BuiltInRegistries.ITEM.getKey(dish) + " isn't on the menu");
		}
		helper.assertTrue(Chefs.potDishes(helper.getLevel()).size() == results.size(),
			Chefs.potDishes(helper.getLevel()).size() + " pot dishes on the menu, " + results.size() + " in the recipe manager");
		for (String candy : List.of("rare_candy", "exp_candy_xs", "exp_candy_xl", "health_candy", "mighty_candy", "tough_candy")) {
			helper.assertTrue(menu.contains(cm(candy)), candy + " isn't on the menu");
		}
		helper.assertFalse(Chefs.menu(helper.getLevel()).contains(cm("exp_candy_xs")), "the candies were on the plain menu already");
		helper.succeed();
	}

	/** The Done when: with sculk and honeycomb in the chest, the Chef cooks EXP Candy XS (the pot's recipe) and uses them up. */
	//$ gametest_ticks_batch AREA '2400' '"campfire_pot_candy"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "campfire_pot_candy")
	public void aChefCooksAnExpCandyFromTheMakings(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Container chest = chest(helper, new ItemStack(Items.SCULK, 4), new ItemStack(Items.HONEYCOMB, 4));
		chef(helper);
		Item candy = cm("exp_candy_xs");
		helper.succeedWhen(() -> {
			helper.assertTrue(chest.countItem(candy) == 4, chest.countItem(candy) + " EXP Candies");
			helper.assertTrue(chest.countItem(Items.SCULK) == 0 && chest.countItem(Items.HONEYCOMB) == 0,
				chest.countItem(Items.SCULK) + " sculk and " + chest.countItem(Items.HONEYCOMB) + " honeycomb left");
		});
	}

	/** A data pack's Campfire Pot recipe is cooked too: the test pack's Rare Candy from an amethyst shard, sugar and glow berries. */
	//$ gametest_ticks_batch AREA '2400' '"campfire_pot_pack"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "campfire_pot_pack")
	public void aChefCooksADataPacksPotRecipe(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Container chest = chest(helper, new ItemStack(Items.AMETHYST_SHARD, 2), new ItemStack(Items.SUGAR, 2), new ItemStack(Items.GLOW_BERRIES, 2));
		chef(helper);
		Item candy = cm("rare_candy");
		helper.succeedWhen(() -> {
			helper.assertTrue(chest.countItem(candy) == 2, chest.countItem(candy) + " Rare Candies");
			helper.assertTrue(chest.countItem(Items.AMETHYST_SHARD) == 0, chest.countItem(Items.AMETHYST_SHARD) + " shards left");
		});
	}

	/** Half the makings (sculk, no honeycomb) and an empty chest: no candy, nothing taken. */
	//$ gametest_ticks_batch AREA '1400' '"campfire_pot_partial"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "campfire_pot_partial")
	public void aChefShortOfTheMakingsCooksNoCandy(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Container chest = chest(helper, new ItemStack(Items.SCULK, 4));
		Villager chef = chef(helper);
		helper.runAfterDelay(1200, () -> {
			helper.assertTrue(chest.countItem(cm("exp_candy_xs")) == 0, "a candy without its honeycomb");
			helper.assertTrue(chest.countItem(Items.SCULK) == 4, "the sculk was taken");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(chef, 0) == 0, "cooked " + ModAttachments.ITEMS_CRAFTED.getOrElse(chef, 0));
			chest.clearContent();
			helper.assertTrue(Chefs.menu(helper.getLevel(), chef).contains(cm("exp_candy_xs")), "the candy was never on the menu");
			helper.succeed();
		});
	}

	/** {@code cuisineDishes} off (as with M38's gate shut): no pot dishes on the menu, and the makings stay in the chest. */
	//$ gametest_ticks_batch AREA '1000' '"campfire_pot_off"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "campfire_pot_off")
	public void theSwitchOffCooksNoCandy(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		boolean was = CuisineDishes.ENABLED;
		CuisineDishes.ENABLED = false;
		Container chest = chest(helper, new ItemStack(Items.SCULK, 4), new ItemStack(Items.HONEYCOMB, 4));
		Villager chef = chef(helper);
		try {
			helper.assertTrue(Chefs.potDishes(helper.getLevel()).isEmpty(), "pot dishes with the switch off");
			helper.assertFalse(Chefs.menu(helper.getLevel(), chef).contains(cm("exp_candy_xs")), "a candy on the menu with the switch off");
		} catch (RuntimeException e) {
			CuisineDishes.ENABLED = was;
			throw e;
		}
		helper.runAfterDelay(800, () -> {
			CuisineDishes.ENABLED = was;
			helper.assertTrue(chest.countItem(cm("exp_candy_xs")) == 0 && chest.countItem(Items.SCULK) == 4, "cooked with the switch off");
			helper.succeed();
		});
	}

	/** The new sentence: the switch's tooltip names the Campfire Pot recipes. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theTooltipNamesThePotRecipes(GameTestHelper helper) {
		String tip;
		try (java.io.InputStream in = java.nio.file.Files.newInputStream(net.fabricmc.loader.api.FabricLoader.getInstance()
				.getModContainer(io.github.jcondedata.aliveworkplace.AliveWorkplace.MOD_ID).orElseThrow()
				.findPath("assets/aliveworkplace/lang/en_us.json").orElseThrow())) {
			tip = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
				.getAsJsonObject().get("aliveworkplace.config.cuisineDishes.tooltip").getAsString();
		} catch (java.io.IOException e) {
			throw new AssertionError("can't read en_us.json: " + e);
		}
		helper.assertTrue(tip.contains("With Cobblemon alone, the Chef also cooks every Campfire Pot recipe (the EXP and IV/EV candies, medicines, sweets) when the store holds its makings.")
			&& tip.contains("no treats, dish luxuries or candies"), tip);
		helper.succeed();
	}
}
