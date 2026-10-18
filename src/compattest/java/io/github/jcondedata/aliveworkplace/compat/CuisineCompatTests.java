package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.block.entity.CampfireBlockEntity;
import io.github.jcondedata.aliveworkplace.camp.CampCooks;
import io.github.jcondedata.aliveworkplace.craft.Chefs;
import io.github.jcondedata.aliveworkplace.craft.CuisineDishes;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.inn.InnDrinks;
import io.github.jcondedata.aliveworkplace.inn.Traveller;
import io.github.jcondedata.aliveworkplace.people.Diet;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * ROADMAP 38.8 with the real Cobblecuisine 2.0.1: the Chef cooks its dishes by his level from their own crafting recipes,
 * the diet counts them by food group, the Camp Cook cooks its curries and the Innkeeper serves its drinks.
 */
public class CuisineCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos STATION = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	private static Item cc(String path) {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblecuisine", path));
		if (item == Items.AIR) {
			throw new AssertionError("no cobblecuisine:" + path);
		}
		return item;
	}

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

	/** A Chef of {@code level} at a Kitchen Stove, with the chest beside it. */
	private static Villager chef(GameTestHelper helper, int level) {
		helper.setDayTime(2000);
		helper.setBlock(STATION, ModBlocks.KITCHEN_STOVE);
		Villager chef = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), chef, helper.absolutePos(STATION), ModVillagers.KITCHEN_STOVE_POI, ModVillagers.CHEF);
		chef.setVillagerData(chef.getVillagerData().setLevel(level));
		return chef;
	}

	/**
	 * The Done when: a Novice Chef with the makings of a Mixed Vegetable Salad (Salad Mix, a Bold Mint, a green bean and
	 * a carrot, twenty of each) cooks it from its own crafting recipe, and stops once the chest holds 16.
	 */
	//$ gametest_ticks_batch AREA '3000' '"cuisine_chef_salad"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "cuisine_chef_salad")
	public void aChefWithTheMakingsCooksALevelOneDishAndKeeps16(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Item salad = cc("mixed_vegetable_salad");
		Container chest = chest(helper, new ItemStack(cc("salad_mix"), 20), new ItemStack(cm("bold_mint"), 20), new ItemStack(cc("green_bean"), 20),
			new ItemStack(Items.CARROT, 20));
		Villager chef = chef(helper, 1);
		helper.assertTrue(Chefs.menu(helper.getLevel(), chef).contains(salad), "the salad isn't on a Novice's menu");
		long[] reached = {-1};
		helper.onEachTick(() -> {
			if (reached[0] < 0 && chest.countItem(salad) >= 16) {
				reached[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reached[0] >= 0, chest.countItem(salad) + " salads so far");
			helper.assertTrue(chest.countItem(salad) == 16, chest.countItem(salad) + " salads: the chef keeps 16");
			helper.assertTrue(chest.countItem(cc("salad_mix")) == 4 && chest.countItem(cm("bold_mint")) == 4 && chest.countItem(cc("green_bean")) == 4
				&& chest.countItem(Items.CARROT) == 4, "the makings of 16 weren't used, or more were: " + chest.countItem(cc("salad_mix")) + " salad mix left");
			helper.assertTrue(helper.getTick() - reached[0] >= 400, "waiting to see the chef stops at 16");
		});
	}

	/** A Novice Chef never cooks a level-4 dish: the makings of a Fruit & Honey Cake stay in the chest. */
	//$ gametest_ticks_batch AREA '1600' '"cuisine_chef_novice_cake"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "cuisine_chef_novice_cake")
	public void aLevelOneChefNeverCooksALevelFourDish(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Item cake = cc("fruit_honey_cake");
		Container chest = chest(helper, new ItemStack(Items.WHEAT, 1), new ItemStack(cc("red_bean"), 4), new ItemStack(Items.HONEY_BOTTLE, 4),
			new ItemStack(Items.APPLE, 4));
		Villager chef = chef(helper, 1);
		helper.assertFalse(Chefs.menu(helper.getLevel(), chef).contains(cake), "a cake is on a Novice's menu");
		helper.assertTrue(CuisineDishes.of(cake) != null && CuisineDishes.of(cake).level() == 4, "the cake isn't a level-4 dish");
		helper.runAfterDelay(1400, () -> {
			helper.assertTrue(chest.countItem(cake) == 0, "a Novice cooked " + chest.countItem(cake) + " cakes");
			helper.assertTrue(chest.countItem(Items.WHEAT) == 1 && chest.countItem(Items.HONEY_BOTTLE) == 4, "the cake's makings were used");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(chef, 0) == 0, "cooked " + ModAttachments.ITEMS_CRAFTED.getOrElse(chef, 0));
			helper.succeed();
		});
	}

	/** The same makings and an Expert Chef: the cake is cooked (so the Novice above held back for his level alone). */
	//$ gametest_ticks_batch AREA '2400' '"cuisine_chef_expert_cake"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "cuisine_chef_expert_cake")
	public void anExpertChefCooksTheCake(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Item cake = cc("fruit_honey_cake");
		Container chest = chest(helper, new ItemStack(Items.WHEAT, 1), new ItemStack(cc("red_bean"), 4), new ItemStack(Items.HONEY_BOTTLE, 4),
			new ItemStack(Items.APPLE, 4));
		Villager chef = chef(helper, 4);
		helper.assertTrue(Chefs.menu(helper.getLevel(), chef).contains(cake), "the cake isn't on an Expert's menu");
		helper.succeedWhen(() -> {
			helper.assertTrue(chest.countItem(cake) == 1, chest.countItem(cake) + " cakes");
			helper.assertTrue(chest.countItem(Items.WHEAT) == 0 && chest.countItem(Items.GLASS_BOTTLE) == 1, "the wheat or the bottle");
		});
	}

	/** The Chef's makings missing (no Bold Mint): no salad, and nothing else of the chest is touched. */
	//$ gametest_ticks_batch AREA '1400' '"cuisine_chef_missing"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "cuisine_chef_missing")
	public void aChefShortOfTheMakingsWaits(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Container chest = chest(helper, new ItemStack(cc("salad_mix"), 4), new ItemStack(cc("green_bean"), 4), new ItemStack(Items.CARROT, 4));
		Villager chef = chef(helper, 5);
		helper.runAfterDelay(1200, () -> {
			helper.assertTrue(chest.countItem(cc("mixed_vegetable_salad")) == 0, "a salad without its mint");
			helper.assertTrue(chest.countItem(cc("salad_mix")) == 4 && chest.countItem(cc("green_bean")) == 4 && chest.countItem(Items.CARROT) == 4,
				"the makings were touched");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(chef, 0) == 0, "cooked " + ModAttachments.ITEMS_CRAFTED.getOrElse(chef, 0));
			helper.succeed();
		});
	}

	/**
	 * The Done when: a villager who ate a salad, a sandwich and a curry has a varied diet; three different salads are one
	 * kind of food (the dish's group), so that's the same thing every time. The dishes are meals whatever their effects.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aVillagerWhoAteThreeKindsHasAVariedDiet(GameTestHelper helper) {
		Villager varied = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
		for (String dish : List.of("mixed_vegetable_salad", "sweet_jam_sandwich", "beanburger_curry")) {
			ItemStack meal = new ItemStack(cc(dish));
			helper.assertTrue(VillageNeeds.isMeal(meal), dish + " isn't a meal from the store");
			Diet.ate(varied, meal);
		}
		helper.assertTrue(Diet.of(varied) == Diet.Kind.VARIED, "three kinds: " + Diet.of(varied));
		Villager salads = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		for (String dish : List.of("mixed_vegetable_salad", "spore_mushroom_salad", "pumpkin_pie_salad")) {
			Diet.ate(salads, new ItemStack(cc(dish)));
		}
		helper.assertTrue(Diet.of(salads) == Diet.Kind.SAME, "three salads are one kind: " + Diet.of(salads));
		helper.assertTrue(Diet.hadLately(salads, new ItemStack(cc("fancy_apple_salad"))), "another salad counts as had lately");
		helper.assertFalse(Diet.hadLately(salads, new ItemStack(cc("beanburger_curry"))), "a curry isn't a salad");
		helper.succeed();
	}

	/** With {@code cuisineDishes} off the menu is the plain one, the dishes are no food group and the inn serves nothing. */
	//$ gametest_batch EMPTY_STRUCTURE '"cuisine_switch_off"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "cuisine_switch_off")
	public void theSwitchOffLeavesTheMenusAsTheyWere(GameTestHelper helper) {
		Villager chef = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
		chef.setVillagerData(chef.getVillagerData().setProfession(ModVillagers.CHEF).setLevel(5));
		helper.assertTrue(Chefs.menu(helper.getLevel(), chef).size() > Chefs.menu(helper.getLevel()).size(), "no dishes on a Master's menu");
		boolean was = CuisineDishes.ENABLED;
		CuisineDishes.ENABLED = false;
		try {
			helper.assertTrue(Chefs.menu(helper.getLevel(), chef).equals(Chefs.menu(helper.getLevel())), "the menu with the switch off");
			helper.assertTrue(CuisineDishes.drinks().isEmpty() && CuisineDishes.dishes().isEmpty(), "dishes with the switch off");
			Villager salads = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
			for (String dish : List.of("mixed_vegetable_salad", "spore_mushroom_salad", "pumpkin_pie_salad")) {
				Diet.ate(salads, new ItemStack(cc(dish)));
			}
			helper.assertTrue(Diet.of(salads) == Diet.Kind.VARIED, "without the groups, three salads are three foods as before");
		} finally {
			CuisineDishes.ENABLED = was;
		}
		helper.succeed();
	}

	/** Cobblemon's campfire with a red pot on it, and a Camp Cook at it with the chest beside it. */
	private static Villager campCook(GameTestHelper helper) {
		helper.setDayTime(2000);
		Block campfire = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "campfire"));
		helper.setBlock(STATION, campfire.defaultBlockState());
		((CampfireBlockEntity) helper.getBlockEntity(STATION)).setPotItem(new ItemStack(cm("campfire_pot_red")));
		Villager cook = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), cook, helper.absolutePos(STATION), ModVillagers.CAMPFIRE_POT_POI, ModVillagers.CAMP_COOK);
		helper.assertTrue(CampCooks.isCook(cook), "not a camp cook");
		return cook;
	}

	private static ItemStack[] beanburgerMakings() {
		return new ItemStack[] {new ItemStack(cc("red_bean"), 4), new ItemStack(Items.BREAD, 4), new ItemStack(Items.POTATO, 4),
			new ItemStack(cm("charti_berry"), 4), new ItemStack(Items.COOKED_CHICKEN, 4)};
	}

	/** The Done when: the Camp Cook makes a curry (a Beanburger Curry, by its own recipe, over the fire) into the chest. */
	//$ gametest_ticks_batch AREA '2400' '"cuisine_camp_curry"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "cuisine_camp_curry")
	public void theCampCookMakesACurry(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.assertTrue(CampCooks.menu().stream().anyMatch(d -> d.item().getPath().equals("beanburger_curry")), "no curry on the camp menu");
		Container chest = chest(helper, beanburgerMakings());
		Villager cook = campCook(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(chest.countItem(cc("beanburger_curry")) >= 1, "no curry in the chest; " + ModAttachments.DISHES_COOKED.getOrElse(cook, 0)
				+ " cooked, bag " + ModAttachments.BUILDER_BAG.getOrCreate(cook).stacks());
			helper.assertTrue(chest.countItem(cm("charti_berry")) < 4 && chest.countItem(Items.COOKED_CHICKEN) < 4, "the makings are all still there");
			helper.assertTrue(ModAttachments.DISHES_COOKED.getOrElse(cook, 0) >= 1, "no dish counted");
		});
	}

	/** {@code cuisineDishes} off: the Camp Cook leaves the curry's makings alone. */
	//$ gametest_ticks_batch AREA '1400' '"cuisine_camp_off"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "cuisine_camp_off")
	public void theCampCookMakesNoCurryWithTheSwitchOff(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		boolean was = CuisineDishes.ENABLED;
		CuisineDishes.ENABLED = false;
		Container chest = chest(helper, beanburgerMakings());
		Villager cook = campCook(helper);
		helper.runAfterDelay(1200, () -> {
			CuisineDishes.ENABLED = was;
			helper.assertTrue(chest.countItem(cc("beanburger_curry")) == 0, "a curry with the switch off");
			helper.assertTrue(chest.countItem(cm("charti_berry")) == 4 && chest.countItem(cc("red_bean")) == 4, "the makings were used");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(cook).isEmpty(), "she carries something");
			helper.succeed();
		});
	}

	/**
	 * The Innkeeper serves a guest one of the drinks from the chest by her counter, once a stay: it counts in the guest's
	 * diet, and with the chest out of drinks (or the guest served) there's nothing more.
	 */
	//$ gametest_batch AREA '"cuisine_inn"'
	@GameTest(template = AREA, batch = "cuisine_inn")
	public void theInnkeeperServesAGuestADrinkOnceAStay(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setBlock(STATION, ModBlocks.INN_COUNTER);
		Container chest = chest(helper, new ItemStack(cc("lemon_soda"), 2), new ItemStack(Items.BREAD, 4));
		Villager innkeeper = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), innkeeper, helper.absolutePos(STATION), ModVillagers.INN_COUNTER_POI, ModVillagers.INNKEEPER);
		Villager guest = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		ModAttachments.TRAVELLER.set(guest, new Traveller(helper.getLevel().getGameTime(), 2));
		helper.assertTrue(CuisineDishes.drinks().contains(cc("lemon_soda")) && CuisineDishes.drinks().contains(cc("coffee"))
			&& CuisineDishes.drinks().contains(cc("fruit_punch")), "the inn's drinks: " + CuisineDishes.drinks());
		ItemStack served = InnDrinks.serve(helper.getLevel(), innkeeper, helper.absolutePos(STATION));
		helper.assertTrue(served.is(cc("lemon_soda")), "served " + served);
		helper.assertTrue(chest.countItem(cc("lemon_soda")) == 1 && chest.countItem(Items.BREAD) == 4, "the chest after serving");
		helper.assertTrue(Diet.hadLately(guest, new ItemStack(cc("lemon_soda"))), "the guest's diet didn't count the drink");
		helper.assertTrue(InnDrinks.serve(helper.getLevel(), innkeeper, helper.absolutePos(STATION)).isEmpty(), "served the same guest twice");
		helper.assertTrue(chest.countItem(cc("lemon_soda")) == 1, "a second soda left the chest");
		helper.succeed();
	}

	/** The same through her shift: an Innkeeper at work at her counter serves the staying guest a drink from the chest. */
	//$ gametest_ticks_batch AREA '1200' '"cuisine_inn_shift"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "cuisine_inn_shift")
	public void anInnkeeperAtWorkServesTheDrink(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setDayTime(8000); // past the morning: no new traveller comes, the one staying is served
		helper.setBlock(STATION, ModBlocks.INN_COUNTER);
		Container chest = chest(helper, new ItemStack(cc("fruit_punch"), 3));
		Villager innkeeper = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), innkeeper, helper.absolutePos(STATION), ModVillagers.INN_COUNTER_POI, ModVillagers.INNKEEPER);
		Villager guest = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		ModAttachments.TRAVELLER.set(guest, new Traveller(helper.getLevel().getGameTime(), 2));
		helper.succeedWhen(() -> {
			helper.assertTrue(Diet.hadLately(guest, new ItemStack(cc("fruit_punch"))), "the guest hasn't had the punch");
			helper.assertTrue(chest.countItem(cc("fruit_punch")) == 2, chest.countItem(cc("fruit_punch")) + " punch left");
		});
	}
}
