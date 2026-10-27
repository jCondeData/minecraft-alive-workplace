package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.craft.Chefs;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
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
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane d, 2026-10-25) of ROADMAP 38.11 with the real Cobblemon, from the spec ("the cook only makes what the store
 * has ingredients for"), on a SHAPED Campfire Pot recipe ({@code cobblemon:cooking_pot}; the existing tests cook only
 * shapeless ones): Mighty Candy from the XL recipe, a 3 x 3 of four Kelpsy Berries, four Honeycombs and one EXP Candy
 * XL, which makes three. With exactly the makings the Chef cooks the three and uses all nine; one Kelpsy Berry short,
 * he cooks nothing and takes nothing.
 */
public class QaCampfirePotCompatTests implements FabricGameTest {
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

	/** What the chest and the chef hold, for the failure messages. */
	private static String held(Container chest, Villager chef) {
		StringBuilder out = new StringBuilder("chest [");
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (!chest.getItem(i).isEmpty()) {
				out.append(' ').append(chest.getItem(i));
			}
		}
		out.append(" ], chef [");
		for (int i = 0; i < chef.getInventory().getContainerSize(); i++) {
			if (!chef.getInventory().getItem(i).isEmpty()) {
				out.append(' ').append(chef.getInventory().getItem(i));
			}
		}
		return out.append(" ], cooked ").append(ModAttachments.ITEMS_CRAFTED.getOrElse(chef, 0)).toString();
	}

	private static Villager chef(GameTestHelper helper) {
		helper.setDayTime(2000);
		helper.setBlock(STATION, ModBlocks.KITCHEN_STOVE);
		Villager chef = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), chef, helper.absolutePos(STATION), ModVillagers.KITCHEN_STOVE_POI, ModVillagers.CHEF);
		return chef;
	}

	/** Exactly the makings of the shaped recipe: the three Mighty Candies it makes, and nothing of the makings left. */
	//$ gametest_ticks_batch AREA '2400' '"qa_pot_shaped"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_pot_shaped")
	public void qaAChefCooksAShapedPotRecipeFromExactlyItsMakings(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Item kelpsy = cm("kelpsy_berry");
		Item xl = cm("exp_candy_xl");
		Item mighty = cm("mighty_candy");
		Container chest = chest(helper, new ItemStack(kelpsy, 4), new ItemStack(Items.HONEYCOMB, 4), new ItemStack(xl, 1));
		Villager chef = chef(helper);
		helper.assertTrue(Chefs.menu(helper.getLevel(), chef).contains(mighty), "setup: Mighty Candy isn't on the menu");
		helper.succeedWhen(() -> {
			helper.assertTrue(chest.countItem(mighty) == 3, chest.countItem(mighty) + " Mighty Candies, not the recipe's 3: " + held(chest, chef));
			helper.assertTrue(chest.countItem(kelpsy) == 0 && chest.countItem(Items.HONEYCOMB) == 0 && chest.countItem(xl) == 0,
				chest.countItem(kelpsy) + " berries, " + chest.countItem(Items.HONEYCOMB) + " honeycomb and " + chest.countItem(xl) + " EXP Candy XL left: " + held(chest, chef));
		});
	}

	/** One Kelpsy Berry short of the shaped recipe: no candy is cooked and every making stays in the chest. */
	//$ gametest_ticks_batch AREA '1400' '"qa_pot_shaped_short"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qa_pot_shaped_short")
	public void qaAChefOneMakingShortOfAShapedPotRecipeCooksNothing(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Item kelpsy = cm("kelpsy_berry");
		Item xl = cm("exp_candy_xl");
		Container chest = chest(helper, new ItemStack(kelpsy, 3), new ItemStack(Items.HONEYCOMB, 4), new ItemStack(xl, 1));
		Villager chef = chef(helper);
		helper.runAfterDelay(1200, () -> {
			helper.assertTrue(chest.countItem(cm("mighty_candy")) == 0, "a Mighty Candy from three berries of four");
			helper.assertTrue(chest.countItem(kelpsy) == 3 && chest.countItem(Items.HONEYCOMB) == 4 && chest.countItem(xl) == 1,
				"makings taken: " + chest.countItem(kelpsy) + " berries, " + chest.countItem(Items.HONEYCOMB) + " honeycomb, " + chest.countItem(xl) + " EXP Candy XL left: " + held(chest, chef));
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(chef, 0) == 0, "cooked " + ModAttachments.ITEMS_CRAFTED.getOrElse(chef, 0));
			helper.succeed();
		});
	}
}
