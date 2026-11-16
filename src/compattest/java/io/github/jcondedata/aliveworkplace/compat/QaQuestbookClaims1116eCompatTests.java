package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * QA (lane-e-1116) for 26.8, the questbook's Cobblemon quests, from the player's side: the recipes the quests spell
 * out ("Your first catch", "A Healing Machine", "Pokémon at work") are crafted through the loaded RecipeManager exactly
 * as described, with Cobblemon loaded, and "Food in store"'s "Pokémon berries are not meals" is checked on every
 * Cobblemon berry. Written from the questbook's text, not the code.
 */
public class QaQuestbookClaims1116eCompatTests implements FabricGameTest {
	private static Item item(String id) {
		return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
	}

	private static ItemStack craft(GameTestHelper helper, int w, int h, String... ids) {
		ServerLevel level = helper.getLevel();
		List<ItemStack> stacks = new ArrayList<>();
		for (String id : ids) {
			if (id.isEmpty()) {
				stacks.add(ItemStack.EMPTY);
			} else {
				Item it = item(id);
				helper.assertTrue(it != Items.AIR, "no such item: " + id);
				stacks.add(new ItemStack(it));
			}
		}
		CraftingInput input = CraftingInput.of(w, h, stacks);
		return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
			.map(r -> r.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
	}

	private static void assertMakes(GameTestHelper helper, ItemStack made, String id, int count, String what) {
		helper.assertTrue(made.is(item(id)), what + " makes " + (made.isEmpty() ? "nothing" : made.toString()) + ", not " + id);
		if (count > 0) {
			helper.assertTrue(made.getCount() == count, what + " makes " + made.getCount() + ", not " + count);
		}
	}

	/**
	 * "Your first catch": "a copper ingot in the middle with a red apricorn above, below, left and right of it. That makes
	 * four."
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaPokeBallsCraftAsTheQuestSays(GameTestHelper helper) {
		assertMakes(helper, craft(helper, 3, 3,
			"", "cobblemon:red_apricorn", "",
			"cobblemon:red_apricorn", "minecraft:copper_ingot", "cobblemon:red_apricorn",
			"", "cobblemon:red_apricorn", ""), "cobblemon:poke_ball", 4, "the Poké Ball layout");
		helper.succeed();
	}

	/**
	 * "A Healing Machine": "three copper ingots on top; an iron ingot, a Max Revive and an iron ingot in the middle; an
	 * iron ingot, redstone and an iron ingot at the bottom."
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaHealingMachineCraftsAsTheQuestSays(GameTestHelper helper) {
		assertMakes(helper, craft(helper, 3, 3,
			"minecraft:copper_ingot", "minecraft:copper_ingot", "minecraft:copper_ingot",
			"minecraft:iron_ingot", "cobblemon:max_revive", "minecraft:iron_ingot",
			"minecraft:iron_ingot", "minecraft:redstone", "minecraft:iron_ingot"), "cobblemon:healing_machine", 0, "the Healing Machine layout");
		helper.succeed();
	}

	/**
	 * "Pokémon at work": "three planks on top; planks, a PC and planks in the middle; wheat, a hopper and wheat at the
	 * bottom" makes a Pasture Block.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaPastureBlockCraftsAsTheQuestSays(GameTestHelper helper) {
		assertMakes(helper, craft(helper, 3, 3,
			"minecraft:oak_planks", "minecraft:oak_planks", "minecraft:oak_planks",
			"minecraft:oak_planks", "cobblemon:pc", "minecraft:oak_planks",
			"minecraft:wheat", "minecraft:hopper", "minecraft:wheat"), "cobblemon:pasture", 0, "the Pasture Block layout");
		helper.succeed();
	}

	/**
	 * "Food in store": "Pokémon berries are not meals": no item in Cobblemon's berries tag counts as a meal in the
	 * village's store. (Cooked berry dishes such as the Candied Berry aren't berries: not checked.)
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaNoCobblemonBerryIsAMeal(GameTestHelper helper) {
		TagKey<Item> berries = TagKey.create(Registries.ITEM, ResourceLocation.parse("cobblemon:berries"));
		List<String> meals = new ArrayList<>();
		int seen = 0;
		for (Item it : BuiltInRegistries.ITEM) {
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(it);
			if (new ItemStack(it).is(berries)) { // Cobblemon's own list of its berries
				seen++;
				if (VillageNeeds.isMeal(new ItemStack(it))) {
					ItemStack stack = new ItemStack(it);
					meals.add(id + " (in not_villager_food: " + stack.is(VillageNeeds.NOT_A_MEAL) + ", food: "
						+ stack.get(net.minecraft.core.component.DataComponents.FOOD) + ")");
				}
			}
		}
		helper.assertTrue(seen >= 20, "only " + seen + " Cobblemon berries found");
		helper.assertTrue(meals.isEmpty(), "Pokémon berries that count as meals: " + meals);
		helper.succeed();
	}
}
