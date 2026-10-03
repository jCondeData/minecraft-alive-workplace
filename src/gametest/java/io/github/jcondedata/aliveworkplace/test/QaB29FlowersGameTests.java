package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (qa-1003-2233, B29): "every #minecraft:flowers item makes a Florist". QaB18FlowersGameTests tries a handful of
 * flowers; this one walks the whole tag as the game has it loaded (so tall flowers, flowering azalea, cherry leaves and
 * whatever a datapack or another mod adds), and checks the other side of the boundary: plants that aren't flowers leave
 * a farmer a farmer.
 */
public class QaB29FlowersGameTests {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** Every item in #minecraft:flowers, given at a composter, turns a farmer into a Florist. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaB29EveryItemTaggedFlowersMakesAFlorist(GameTestHelper helper) {
		List<Item> flowers = new ArrayList<>();
		for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.FLOWERS)) {
			flowers.add(holder.value());
		}
		helper.assertTrue(flowers.size() >= 20, "#minecraft:flowers only has " + flowers.size() + " items loaded");
		List<String> wrong = QaGuideClaimsGameTests.follow(helper, Blocks.COMPOSTER, new ItemStack(Items.WHEAT), VillagerProfession.FARMER,
			ModVillagers.FLORIST, flowers);
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		org.slf4j.LoggerFactory.getLogger("aliveworkplace").info("[test] B29: all {} #minecraft:flowers items make a florist", flowers.size());
		helper.succeed();
	}

	/** The other side: saplings, a plain azalea, grass, ferns and lily pads aren't flowers, so the farmer stays a farmer. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaB29PlantsThatArentFlowersLeaveAFarmer(GameTestHelper helper) {
		List<Item> plants = List.of(Items.AZALEA, Items.OAK_SAPLING, Items.CHERRY_SAPLING, Items.SHORT_GRASS, Items.FERN,
			Items.LILY_PAD, Items.OAK_LEAVES, Items.BIG_DRIPLEAF);
		for (Item plant : plants) {
			helper.assertFalse(plant.builtInRegistryHolder().is(ItemTags.FLOWERS), plant + " is tagged a flower; fix the test's list");
		}
		List<String> wrong = QaGuideClaimsGameTests.follow(helper, Blocks.COMPOSTER, new ItemStack(Items.WHEAT), VillagerProfession.FARMER,
			VillagerProfession.FARMER, plants);
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		org.slf4j.LoggerFactory.getLogger("aliveworkplace").info("[test] B29: {} plants that aren't flowers leave a farmer a farmer", plants.size());
		helper.succeed();
	}
}
