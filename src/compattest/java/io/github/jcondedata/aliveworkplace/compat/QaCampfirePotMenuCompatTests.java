package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.craft.Chefs;
import io.github.jcondedata.aliveworkplace.craft.CuisineDishes;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
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
 * QA for ROADMAP 38.11 (Campfire Pot recipes at the Chef's stove), written from the item and its wiki page
 * (raid-dens-megas-cuisine.md), not from the code: makings spread over two chests by the stove still count, a
 * shapeless pot recipe with a tag ingredient and one that makes four at a time are cooked like the rest, a result with
 * its own components stays off the menu, and with {@code cuisineDishes} off the dishes he always had stay on it. "Keeps
 * 16 of each" fails while EXP Candies are in the chests (he turns them into bigger candies and back): that is B435,
 * whose tests are on tests/cuisine-candies-1025.
 */
public class QaCampfirePotMenuCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos STATION = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos OTHER_CHEST = new BlockPos(5, 2, 2);

	private static Item cm(String path) {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", path));
		if (item == Items.AIR) {
			throw new AssertionError("no cobblemon:" + path);
		}
		return item;
	}

	private static Container chest(GameTestHelper helper, BlockPos at, ItemStack... items) {
		helper.setBlock(at, Blocks.CHEST);
		Container chest = helper.getBlockEntity(at);
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

	/** Every making need not sit in one chest: sculk in one chest by the stove and honeycomb in another still make candies. */
	//$ gametest_ticks_batch AREA '2400' '"qa_pot_menu_two_chests"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_pot_menu_two_chests")
	public void makingsInTwoChestsByTheStoveAreCooked(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Item candy = cm("exp_candy_xs");
		Container one = chest(helper, CHEST, new ItemStack(Items.SCULK, 3));
		Container two = chest(helper, OTHER_CHEST, new ItemStack(Items.HONEYCOMB, 3));
		chef(helper);
		helper.succeedWhen(() -> {
			int made = one.countItem(candy) + two.countItem(candy);
			helper.assertTrue(made == 3, made + " EXP Candies from sculk and honeycomb in two chests");
			helper.assertTrue(one.countItem(Items.SCULK) == 0 && two.countItem(Items.HONEYCOMB) == 0,
				one.countItem(Items.SCULK) + " sculk and " + two.countItem(Items.HONEYCOMB) + " honeycomb left");
		});
	}

	/**
	 * A shapeless pot recipe ({@code cobblemon:cooking_pot_shapeless}) whose making is a tag: Strawberry Sweet from any red
	 * dye ({@code c:dyes/red}), a honey bottle and sugar.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa_pot_menu_shapeless"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_pot_menu_shapeless")
	public void aShapelessPotRecipeWithATagMakingIsCooked(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Item sweet = cm("strawberry_sweet");
		helper.assertTrue(Chefs.potDishes(helper.getLevel()).contains(sweet), "Strawberry Sweet isn't among the pot dishes");
		Container chest = chest(helper, CHEST, new ItemStack(Items.RED_DYE, 2), new ItemStack(Items.HONEY_BOTTLE, 2), new ItemStack(Items.SUGAR, 2));
		chef(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(chest.countItem(sweet) == 2, chest.countItem(sweet) + " Strawberry Sweets");
			helper.assertTrue(chest.countItem(Items.RED_DYE) == 0 && chest.countItem(Items.SUGAR) == 0,
				chest.countItem(Items.RED_DYE) + " red dye and " + chest.countItem(Items.SUGAR) + " sugar left");
		});
	}

	/** A pot recipe that makes four at once (X Defense: a glass bottle, a Pep-Up Flower, bone meal, a Ganlon Berry). */
	//$ gametest_ticks_batch AREA '2400' '"qa_pot_menu_four"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_pot_menu_four")
	public void aPotRecipeThatMakesFourIsCooked(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Item x = cm("x_defence");
		Container chest = chest(helper, CHEST, new ItemStack(Items.GLASS_BOTTLE, 2), new ItemStack(cm("pep_up_flower"), 2),
			new ItemStack(Items.BONE_MEAL, 2), new ItemStack(cm("ganlon_berry"), 2));
		chef(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(chest.countItem(x) == 8, chest.countItem(x) + " X Defense, expected 8 from two cooks of four");
			helper.assertTrue(chest.countItem(cm("ganlon_berry")) == 0 && chest.countItem(Items.BONE_MEAL) == 0,
				chest.countItem(cm("ganlon_berry")) + " berries and " + chest.countItem(Items.BONE_MEAL) + " bone meal left");
		});
	}

	/**
	 * "A result with its own components (a potion's contents) is left out": the pot's suspicious stews carry their
	 * effect, so the stew isn't a pot dish; the pot's plain beetroot soup is still on the menu.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aResultWithItsOwnComponentsIsLeftOut(GameTestHelper helper) {
		List<Item> pot = Chefs.potDishes(helper.getLevel());
		helper.assertFalse(pot.contains(Items.SUSPICIOUS_STEW), "the suspicious stew (with its effect) is a pot dish");
		helper.assertTrue(pot.contains(Items.BEETROOT_SOUP), "the pot's beetroot soup isn't a pot dish");
		helper.assertTrue(pot.contains(cm("x_defence")) && pot.contains(cm("strawberry_sweet")), "a shapeless pot recipe's dish is missing");
		helper.succeed();
	}

	/**
	 * {@code cuisineDishes} off: the pot's candies leave the menu, but the Cobblemon dishes he always had (Poké Bait,
	 * Poké Snacks, Poké Cakes, candied apples, Aprijuice) stay on it.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void withTheSwitchOffTheOldCobblemonDishesStay(GameTestHelper helper) {
		Villager chef = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
		chef.setVillagerData(chef.getVillagerData().setProfession(ModVillagers.CHEF).setLevel(1));
		boolean was = CuisineDishes.ENABLED;
		CuisineDishes.ENABLED = false;
		try {
			List<Item> menu = Chefs.menu(helper.getLevel(), chef);
			helper.assertFalse(menu.contains(cm("exp_candy_xs")), "a candy on the menu with the switch off");
			for (String dish : List.of("poke_bait", "poke_snack", "poke_cake", "candied_apple", "aprijuice_red", "aprijuice_white")) {
				BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("cobblemon", dish)).ifPresent(item ->
					helper.assertTrue(menu.contains(item), dish + " left the menu with the switch off"));
			}
		} finally {
			CuisineDishes.ENABLED = was;
		}
		helper.succeed();
	}
}
