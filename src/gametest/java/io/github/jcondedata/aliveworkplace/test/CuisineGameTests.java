package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.craft.Chefs;
import io.github.jcondedata.aliveworkplace.craft.CuisineDishes;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/** ROADMAP 38.8 without Cobblecuisine: the Chef's menu is today's, and the dish lists are read as their format says. */
public class CuisineGameTests implements FabricGameTest {
	/** The Done when: without Cobblecuisine a Master Chef's menu is the plain one, and no dish is listed. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void withoutCobblecuisineTheMenuIsTodays(GameTestHelper helper) {
		helper.assertTrue(CuisineDishes.ENABLED, "cuisineDishes should be on in GameTests");
		helper.assertTrue(CuisineDishes.dishes().isEmpty(), "dishes without Cobblecuisine: " + CuisineDishes.dishes());
		helper.assertTrue(CuisineDishes.drinks().isEmpty(), "inn drinks without Cobblecuisine");
		helper.assertFalse(CuisineDishes.listed(ResourceLocation.fromNamespaceAndPath("cobblecuisine", "mixed_vegetable_salad")),
			"a dish list was read though its mod isn't installed");
		Villager chef = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
		chef.setVillagerData(chef.getVillagerData().setProfession(ModVillagers.CHEF).setLevel(5));
		helper.assertTrue(Chefs.menu(helper.getLevel(), chef).equals(Chefs.menu(helper.getLevel())),
			"a Master's menu " + Chefs.menu(helper.getLevel(), chef) + " isn't today's " + Chefs.menu(helper.getLevel()));
		helper.succeed();
	}

	/** A dish list: its mod missing reads as nothing; a dish's level, group and inn flag; mistakes say what's wrong. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void dishListsAreReadAsTheirFormatSays(GameTestHelper helper) {
		String list = "{\"requires\": \"%s\", \"dishes\": [{\"id\": \"minecraft:bread\", \"level\": 2, \"group\": \"Loaf\"},"
			+ " {\"id\": \"minecraft:milk_bucket\", \"group\": \"drink\", \"inn\": true}]}";
		helper.assertTrue(CuisineDishes.parse(JsonParser.parseString(String.format(list, "cobblecuisine")).getAsJsonObject(), m -> false).isEmpty(),
			"a list for a mod that isn't installed");
		List<CuisineDishes.Entry> read = CuisineDishes.parse(JsonParser.parseString(String.format(list, "minecraft")).getAsJsonObject(), m -> true);
		helper.assertTrue(read.size() == 2, "read " + read);
		helper.assertTrue(read.get(0).level() == 2 && read.get(0).group().equals("loaf") && !read.get(0).inn(), "the bread: " + read.get(0));
		helper.assertTrue(read.get(1).level() == 1 && read.get(1).inn(), "the milk (level 1 by default, served at the inn): " + read.get(1));
		for (String bad : List.of("{\"dishes\": [{\"id\": \"minecraft:bread\", \"level\": 6, \"group\": \"loaf\"}]}",
			"{\"dishes\": [{\"id\": \"minecraft:bread\", \"level\": 1}]}", "{\"dishes\": [{\"level\": 1, \"group\": \"loaf\"}]}", "{}")) {
			try {
				CuisineDishes.parse(JsonParser.parseString(bad).getAsJsonObject(), m -> true);
				helper.fail("read a bad list: " + bad);
			} catch (IllegalArgumentException expected) {
				// says what's wrong
			}
		}
		helper.succeed();
	}

	/** The new sentences: the Innkeeper's status as she serves a drink, and the switch's name and tooltip. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theText(GameTestHelper helper) {
		String serving = JewellerGameTests.english(net.minecraft.network.chat.Component.translatable("message.aliveworkplace.innkeeper.state.serving",
			net.minecraft.network.chat.Component.literal("Bouncy Brionne Soda")));
		helper.assertTrue(serving.equals("served a guest Bouncy Brionne Soda"), serving);
		String name = JewellerGameTests.english(net.minecraft.network.chat.Component.translatable("aliveworkplace.config.cuisineDishes"));
		helper.assertTrue(name.equals("Cobblecuisine Dishes"), name);
		String tip = JewellerGameTests.english(net.minecraft.network.chat.Component.translatable("aliveworkplace.config.cuisineDishes.tooltip"));
		helper.assertTrue(tip.contains("curries at Journeyman, cakes at Expert") && tip.contains("Needs Cobblecuisine"), tip);
		helper.succeed();
	}
}
