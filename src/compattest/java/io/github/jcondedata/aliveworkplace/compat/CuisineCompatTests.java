package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.block.entity.CampfireBlockEntity;
import io.github.jcondedata.aliveworkplace.camp.CampCooks;
import io.github.jcondedata.aliveworkplace.craft.Chefs;
import io.github.jcondedata.aliveworkplace.craft.CuisineDishes;
import io.github.jcondedata.aliveworkplace.craft.DishRequests;
import io.github.jcondedata.aliveworkplace.craft.DishesTab;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * ROADMAP 38.8 with the real Cobblecuisine 2.0.1: the Chef cooks its dishes by his level from their own crafting recipes
 * (since 38.8a only those asked for at the hall's board, once the village has a Chef),
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

	/** Where the tests' Village Hall stands. */
	private static final BlockPos HALL = new BlockPos(6, 2, 1);

	/**
	 * A Village Hall of the tests' own, nobody's but this test's: other halls round the area are taken down, no request
	 * is left from before, and its village reaches 16 blocks (no Chef of another test is the village's) until the test ends.
	 */
	private static BlockPos hall(GameTestHelper helper) {
		PartnerShowsCompatTests.clearHalls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		PartnerShowsCompatTests.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			DishRequests.get(helper.getLevel()).clear();
		});
		DishRequests.get(helper.getLevel()).clear();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return helper.absolutePos(HALL);
	}

	/** The dish's icon on the board's Dishes tab. */
	private static ItemStack dishIcon(ChoiceMenu menu, Item dish) {
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (menu.icon(slot).is(dish)) {
				return menu.icon(slot);
			}
		}
		throw new AssertionError("no " + dish + " on the Dishes tab");
	}

	private static int dishSlot(ChoiceMenu menu, Item dish) {
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (menu.icon(slot).is(dish)) {
				return slot;
			}
		}
		throw new AssertionError("no " + dish + " on the Dishes tab");
	}

	private static List<String> lore(ItemStack icon) {
		List<String> out = new java.util.ArrayList<>();
		var lore = icon.get(net.minecraft.core.component.DataComponents.LORE);
		if (lore != null) {
			lore.lines().forEach(l -> out.add(l.getString()));
		}
		return out;
	}

	/** The hall's screen, its minecart, then the Dishes tab: the way a player gets to the board's dishes. */
	private static ChoiceMenu board(GameTestHelper helper, ServerPlayer player, BlockPos hall) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		menu.press(VillageHallScreen.ROUTES, player);
		helper.assertFalse(menu.icon(TradePage.Tab.DISHES.slot()).isEmpty(),
			"no Dishes tab on the Trade page");
		menu.press(TradePage.Tab.DISHES.slot(), player);
		helper.assertTrue(TradePage.marks(menu.icon(TradePage.Tab.DISHES.slot())).contains(TradePage.OPEN), "the Dishes tab didn't open");
		return menu;
	}

	/**
	 * The Done when (38.8a): with no Chef in the village the board lists the dishes locked, says a Chef is needed, and a
	 * click asks for nothing; once a Novice Chef works there the salads open and the cakes stay locked for his level.
	 */
	//$ gametest_batch AREA '"cuisine_board_locked"'
	@GameTest(template = AREA, batch = "cuisine_board_locked")
	public void withoutAChefTheDishesAreLockedOnTheBoard(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos hall = hall(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Item salad = cc("mixed_vegetable_salad");
		Item cake = cc("fruit_honey_cake");
		ChoiceMenu menu = board(helper, player, hall);
		ItemStack tab = menu.icon(TradePage.Tab.DISHES.slot());
		helper.assertTrue(TradePage.marks(tab).contains(DishesTab.LOCKED), "the tab isn't locked without a Chef");
		helper.assertTrue(lore(tab).contains("Locked: the village needs a Chef to cook these"), "the tab doesn't say a Chef is needed: " + lore(tab));
		ItemStack icon = dishIcon(menu, salad);
		helper.assertTrue(TradePage.marks(icon).contains(DishesTab.LOCKED), "the salad isn't locked");
		helper.assertTrue(lore(icon).contains("Locked: the village needs a Chef to cook these"), "the salad: " + lore(icon));
		menu.press(dishSlot(menu, salad), player);
		helper.assertTrue(DishRequests.get(helper.getLevel()).waiting(hall, BuiltInRegistries.ITEM.getKey(salad)) == 0, "asked for a salad with no Chef");
		helper.assertTrue(DishRequests.request(helper.getLevel(), hall, salad, 4) == DishRequests.Result.NO_CHEF, "the request without a Chef");
		chef(helper, 1);
		menu = board(helper, player, hall);
		helper.assertFalse(TradePage.marks(dishIcon(menu, salad)).contains(DishesTab.LOCKED), "the salad is still locked with a Chef");
		helper.assertTrue(lore(dishIcon(menu, salad)).contains("Click: ask for 4 more. Shift-click: 16 more"), "the salad: " + lore(dishIcon(menu, salad)));
		helper.assertTrue(TradePage.marks(dishIcon(menu, cake)).contains(DishesTab.LOCKED), "a Novice's village can ask for the cake");
		helper.assertTrue(lore(dishIcon(menu, cake)).contains("Locked: the Chef must be Expert"), "the cake: " + lore(dishIcon(menu, cake)));
		helper.succeed();
	}

	/**
	 * The Done when (38.8a; was 38.8's "a Chef with the makings cooks a level-1 dish and keeps 16", reversed by the
	 * owner): a player asks at the board for 4 Mixed Vegetable Salads; the Novice Chef cooks them from their own crafting
	 * recipe with the makings in his chest, delivers them (no Storehouse: to the chest by his stove), the request comes
	 * down, and he cooks no more.
	 */
	//$ gametest_ticks_batch AREA '3000' '"cuisine_chef_salad"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "cuisine_chef_salad")
	public void aRequestedLevelOneDishIsCookedAndDelivered(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos hall = hall(helper);
		Item salad = cc("mixed_vegetable_salad");
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(salad);
		Container chest = chest(helper, new ItemStack(cc("salad_mix"), 20), new ItemStack(cm("bold_mint"), 20), new ItemStack(cc("green_bean"), 20),
			new ItemStack(Items.CARROT, 20));
		chef(helper, 1);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ChoiceMenu menu = board(helper, player, hall);
		menu.press(dishSlot(menu, salad), player);
		helper.assertTrue(DishRequests.get(helper.getLevel()).waiting(hall, id) == 4, "the click asked for " + DishRequests.get(helper.getLevel()).waiting(hall, id));
		helper.assertTrue(lore(dishIcon(menu, salad)).contains("Asked for: 4 waiting"), "the board after the click: " + lore(dishIcon(menu, salad)));
		long[] reached = {-1};
		helper.onEachTick(() -> {
			if (reached[0] < 0 && chest.countItem(salad) >= 4) {
				reached[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reached[0] >= 0, chest.countItem(salad) + " salads so far");
			helper.assertTrue(chest.countItem(salad) == 4, chest.countItem(salad) + " salads: 4 were asked for");
			helper.assertTrue(DishRequests.get(helper.getLevel()).waiting(hall, id) == 0, "the request is still up");
			helper.assertTrue(chest.countItem(cc("salad_mix")) == 16 && chest.countItem(cm("bold_mint")) == 16 && chest.countItem(cc("green_bean")) == 16
				&& chest.countItem(Items.CARROT) == 16, "the makings of 4 weren't used, or more were: " + chest.countItem(cc("salad_mix")) + " salad mix left");
			helper.assertTrue(helper.getTick() - reached[0] >= 400, "waiting to see the chef cooks no more");
		});
	}

	/** The Done when (38.8a): a Master Chef with every dish's makings and nobody asking cooks no Cobblecuisine dish. */
	//$ gametest_ticks_batch AREA '1600' '"cuisine_chef_unasked"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "cuisine_chef_unasked")
	public void anUnrequestedChefCooksNoDish(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		hall(helper);
		Item salad = cc("mixed_vegetable_salad");
		Item cake = cc("fruit_honey_cake");
		Container chest = chest(helper, new ItemStack(cc("salad_mix"), 8), new ItemStack(cm("bold_mint"), 8), new ItemStack(cc("green_bean"), 8),
			new ItemStack(Items.CARROT, 8), new ItemStack(Items.WHEAT, 1), new ItemStack(cc("red_bean"), 4), new ItemStack(Items.HONEY_BOTTLE, 4),
			new ItemStack(Items.APPLE, 4));
		Villager chef = chef(helper, 5);
		for (Item dish : Chefs.menu(helper.getLevel(), chef)) {
			helper.assertTrue(CuisineDishes.of(dish) == null, dish + " is on the Chef's own menu");
		}
		helper.runAfterDelay(1400, () -> {
			helper.assertTrue(chest.countItem(salad) == 0 && chest.countItem(cake) == 0, "cooked unasked: " + chest.countItem(salad) + " salads, "
				+ chest.countItem(cake) + " cakes");
			helper.assertTrue(chest.countItem(cc("salad_mix")) == 8 && chest.countItem(Items.WHEAT) == 1, "the makings were used");
			helper.succeed();
		});
	}

	/**
	 * The Done when: a Novice Chef's village can't ask for a level-4 dish at the board, and a cake request left from a
	 * better Chef (written straight into the requests) isn't cooked by him: the makings stay in the chest.
	 */
	//$ gametest_ticks_batch AREA '1600' '"cuisine_chef_novice_cake"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "cuisine_chef_novice_cake")
	public void aLevelOneChefNeverCooksALevelFourDish(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos hall = hall(helper);
		Item cake = cc("fruit_honey_cake");
		Container chest = chest(helper, new ItemStack(Items.WHEAT, 1), new ItemStack(cc("red_bean"), 4), new ItemStack(Items.HONEY_BOTTLE, 4),
			new ItemStack(Items.APPLE, 4));
		Villager chef = chef(helper, 1);
		helper.assertTrue(CuisineDishes.of(cake) != null && CuisineDishes.of(cake).level() == 4, "the cake isn't a level-4 dish");
		helper.assertTrue(DishRequests.request(helper.getLevel(), hall, cake, 1) == DishRequests.Result.LEVEL, "a Novice's village asked for a cake");
		helper.assertTrue(DishRequests.get(helper.getLevel()).waiting(hall, CuisineDishes.of(cake).id()) == 0, "the cake request went up");
		DishRequests.get(helper.getLevel()).add(hall, CuisineDishes.of(cake).id(), 1);
		helper.runAfterDelay(1400, () -> {
			helper.assertTrue(chest.countItem(cake) == 0, "a Novice cooked " + chest.countItem(cake) + " cakes");
			helper.assertTrue(chest.countItem(Items.WHEAT) == 1 && chest.countItem(Items.HONEY_BOTTLE) == 4, "the cake's makings were used");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(chef, 0) == 0, "cooked " + ModAttachments.ITEMS_CRAFTED.getOrElse(chef, 0));
			helper.succeed();
		});
	}

	/** The same makings, an Expert Chef and a cake asked for: the cake is cooked (so the Novice above held back for his level alone). */
	//$ gametest_ticks_batch AREA '2400' '"cuisine_chef_expert_cake"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "cuisine_chef_expert_cake")
	public void anExpertChefCooksTheRequestedCake(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos hall = hall(helper);
		Item cake = cc("fruit_honey_cake");
		Container chest = chest(helper, new ItemStack(Items.WHEAT, 1), new ItemStack(cc("red_bean"), 4), new ItemStack(Items.HONEY_BOTTLE, 4),
			new ItemStack(Items.APPLE, 4));
		chef(helper, 4);
		helper.assertTrue(DishRequests.request(helper.getLevel(), hall, cake, 1) == DishRequests.Result.ASKED, "an Expert's village can't ask for the cake");
		helper.succeedWhen(() -> {
			helper.assertTrue(chest.countItem(cake) == 1, chest.countItem(cake) + " cakes");
			helper.assertTrue(chest.countItem(Items.WHEAT) == 0 && chest.countItem(Items.GLASS_BOTTLE) == 1, "the wheat or the bottle");
			helper.assertTrue(DishRequests.get(helper.getLevel()).waiting(hall, CuisineDishes.of(cake).id()) == 0, "the cake request is still up");
		});
	}

	/** The Chef's makings missing (no Bold Mint) for the salads asked for: no salad, nothing touched, the request stays up. */
	//$ gametest_ticks_batch AREA '1400' '"cuisine_chef_missing"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "cuisine_chef_missing")
	public void aChefShortOfTheMakingsWaits(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos hall = hall(helper);
		Container chest = chest(helper, new ItemStack(cc("salad_mix"), 4), new ItemStack(cc("green_bean"), 4), new ItemStack(Items.CARROT, 4));
		Villager chef = chef(helper, 5);
		Item salad = cc("mixed_vegetable_salad");
		helper.assertTrue(DishRequests.request(helper.getLevel(), hall, salad, 4) == DishRequests.Result.ASKED, "the salads weren't asked for");
		helper.runAfterDelay(1200, () -> {
			helper.assertTrue(chest.countItem(salad) == 0, "a salad without its mint");
			helper.assertTrue(chest.countItem(cc("salad_mix")) == 4 && chest.countItem(cc("green_bean")) == 4 && chest.countItem(Items.CARROT) == 4,
				"the makings were touched");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(chef, 0) == 0, "cooked " + ModAttachments.ITEMS_CRAFTED.getOrElse(chef, 0));
			helper.assertTrue(DishRequests.get(helper.getLevel()).waiting(hall, BuiltInRegistries.ITEM.getKey(salad)) == 4, "the request came down");
			helper.succeed();
		});
	}

	/** The Done when: the board's requests are saved with the world: a reload brings back each village's, in their order. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void dishRequestsSurviveSaveAndReload(GameTestHelper helper) {
		DishRequests requests = new DishRequests();
		BlockPos one = new BlockPos(100, 64, -40);
		BlockPos two = new BlockPos(-7, 70, 300);
		ResourceLocation salad = BuiltInRegistries.ITEM.getKey(cc("mixed_vegetable_salad"));
		ResourceLocation cake = BuiltInRegistries.ITEM.getKey(cc("fruit_honey_cake"));
		requests.add(one, cake, 2);
		requests.add(one, salad, 12);
		requests.add(two, salad, 100); // capped
		requests.done(one, salad, 4);
		DishRequests back = requests.reloaded(helper.getLevel().registryAccess());
		helper.assertTrue(back.of(one).equals(java.util.Map.of(cake, 2, salad, 8)), "the first village's: " + back.of(one));
		helper.assertTrue(List.copyOf(back.of(one).keySet()).equals(List.of(cake, salad)), "the order: " + back.of(one).keySet());
		helper.assertTrue(back.waiting(two, salad) == DishRequests.MAX, "the second village's: " + back.of(two));
		helper.assertTrue(new DishRequests().reloaded(helper.getLevel().registryAccess()).of(one).isEmpty(), "an older world has none waiting");
		helper.succeed();
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
		helper.assertTrue(DishRequests.shown() && !CuisineDishes.upTo(5).isEmpty(), "no dishes to ask for at the board");
		boolean was = CuisineDishes.ENABLED;
		CuisineDishes.ENABLED = false;
		try {
			helper.assertTrue(Chefs.menu(helper.getLevel(), chef).equals(Chefs.menu(helper.getLevel())), "the menu with the switch off");
			helper.assertTrue(CuisineDishes.drinks().isEmpty() && CuisineDishes.dishes().isEmpty(), "dishes with the switch off");
			helper.assertFalse(DishRequests.shown() || TradePage.tabs().contains(TradePage.Tab.DISHES), "a Dishes tab with the switch off");
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
