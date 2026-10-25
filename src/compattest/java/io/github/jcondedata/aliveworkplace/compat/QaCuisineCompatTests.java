package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.block.entity.CampfireBlockEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonPartners;
import io.github.jcondedata.aliveworkplace.craft.CuisineDishes;
import io.github.jcondedata.aliveworkplace.craft.DishRequests;
import io.github.jcondedata.aliveworkplace.craft.DishesTab;
import io.github.jcondedata.aliveworkplace.farm.BeanFarms;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Luxuries;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.PartnerTreats;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-d-1025-0408), written from ROADMAP 38.8, 38.8a, 38.9, 38.10 and 38.11 and the wiki page "Raid dens, Megas and
 * Cobblecuisine", not from the code: the boundaries the builders' tests leave out (both sides of 16 beans and 16
 * candies, a bean crop one stage short, exactly 4 and 8 days for the dish luxuries, a partner's treat across a day and
 * only from the store, one making missing for the Camp Cook, the board's shift-click, cap and right-click and the
 * Chef's level exactly met) and the board's sentences with their real arguments. The 16 pot dishes kept (38.11) are
 * in the bug tests on branch tests/cuisine-candies-1025: they fail on today's code.
 */
public class QaCuisineCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos STATION = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos HALL = new BlockPos(6, 2, 1);
	private static final TagKey<Item> BEANS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("cobblecuisine", "beans"));

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

	private static Container chest(GameTestHelper helper, BlockPos at, ItemStack... items) {
		helper.setBlock(at, Blocks.CHEST);
		Container chest = helper.getBlockEntity(at);
		for (int i = 0; i < items.length; i++) {
			chest.setItem(i, items[i]);
		}
		return chest;
	}

	/** What a chest holds, for failure messages. */
	private static String dump(Container c) {
		StringBuilder b = new StringBuilder("[");
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (!c.getItem(i).isEmpty()) {
				b.append(c.getItem(i).getCount()).append(' ').append(BuiltInRegistries.ITEM.getKey(c.getItem(i).getItem()).getPath()).append(", ");
			}
		}
		return b.append(']').toString();
	}

	private static Villager chef(GameTestHelper helper, int level) {
		helper.setDayTime(2000);
		helper.setBlock(STATION, ModBlocks.KITCHEN_STOVE);
		Villager chef = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), chef, helper.absolutePos(STATION), ModVillagers.KITCHEN_STOVE_POI, ModVillagers.CHEF);
		chef.setVillagerData(chef.getVillagerData().setLevel(level));
		return chef;
	}

	private static List<String> lore(ItemStack icon) {
		List<String> out = new ArrayList<>();
		var lore = icon.get(net.minecraft.core.component.DataComponents.LORE);
		if (lore != null) {
			lore.lines().forEach(l -> out.add(l.getString()));
		}
		return out;
	}

	private static int dishSlot(ChoiceMenu menu, Item dish) {
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (menu.icon(slot).is(dish)) {
				return slot;
			}
		}
		throw new AssertionError("no " + dish + " on the Dishes tab");
	}

	/** The hall's screen, its minecart, then the Dishes tab, as a player gets there. */
	private static ChoiceMenu board(GameTestHelper helper, ServerPlayer player, BlockPos hall) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		menu.press(VillageHallScreen.ROUTES, player);
		menu.press(TradePage.Tab.DISHES.slot(), player);
		helper.assertTrue(TradePage.marks(menu.icon(TradePage.Tab.DISHES.slot())).contains(TradePage.OPEN), "the Dishes tab didn't open");
		return menu;
	}

	/** A hall of this test's own (the others round the area taken down, no request left over, a 16-block village). */
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

	// ---- 38.8a: the board ----

	/**
	 * The wiki: "a click asks for 4 more, shift-click 16, right-click takes the request down (at most 64 of a dish wait)";
	 * a dish exactly at the Chef's level opens (the drinks at Apprentice). Clicked the way a client clicks, with every
	 * line and answer read as the player reads them.
	 */
	//$ gametest_batch AREA '"qa_cuisine_board"'
	@GameTest(template = AREA, batch = "qa_cuisine_board")
	public void theBoardsShiftClickCapAndRightClick(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos hall = hall(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Item salad = cc("mixed_vegetable_salad");
		Item soda = cc("lemon_soda");
		ResourceLocation saladId = BuiltInRegistries.ITEM.getKey(salad);
		String saladName = salad.getDescription().getString();
		Villager chef = chef(helper, 1);
		helper.assertTrue(CuisineDishes.of(soda) != null && CuisineDishes.of(soda).level() == 2, "the drinks aren't Apprentice dishes: " + CuisineDishes.of(soda));

		ChoiceMenu menu = board(helper, player, hall);
		List<String> tab = lore(menu.icon(TradePage.Tab.DISHES.slot()));
		helper.assertTrue(tab.contains("The village's Chef: Novice"), "the tab with a Novice Chef: " + tab);
		helper.assertTrue(tab.contains("Ask the village's Chef for Cobblecuisine dishes; he cooks them from the store's makings and brings them to the Storehouse."),
			"the tab's about line: " + tab);
		List<String> saladLore = lore(menu.icon(dishSlot(menu, salad)));
		helper.assertTrue(saladLore.contains("Chef's level: Novice or better") && saladLore.contains("In the village's store: 0")
			&& !saladLore.contains("Right-click: take the request down"), "the salad before asking: " + saladLore);
		List<String> sodaLore = lore(menu.icon(dishSlot(menu, soda)));
		helper.assertTrue(sodaLore.contains("Locked: the Chef must be Apprentice") && sodaLore.contains("Chef's level: Apprentice or better"),
			"the soda for a Novice: " + sodaLore);
		helper.assertTrue(DishRequests.request(level, hall, soda, 4) == DishRequests.Result.LEVEL, "a Novice's village asked for a soda");

		// shift-click: 16 at a time, up to 64
		for (int i = 1; i <= 4; i++) {
			menu.clicked(dishSlot(menu, salad), 0, ClickType.QUICK_MOVE, player);
			helper.assertTrue(DishRequests.get(level).waiting(hall, saladId) == 16 * i, "after " + i + " shift-clicks: " + DishRequests.get(level).waiting(hall, saladId));
		}
		menu.clicked(dishSlot(menu, salad), 0, ClickType.PICKUP, player);
		helper.assertTrue(DishRequests.get(level).waiting(hall, saladId) == DishRequests.MAX, "past the cap: " + DishRequests.get(level).waiting(hall, saladId));
		helper.assertTrue(DishRequests.request(level, hall, salad, 4) == DishRequests.Result.FULL, "a full request was asked again");
		saladLore = lore(menu.icon(dishSlot(menu, salad)));
		helper.assertTrue(saladLore.contains("Asked for: 64 waiting") && saladLore.contains("Right-click: take the request down"), "the salad at the cap: " + saladLore);
		tab = lore(menu.icon(TradePage.Tab.DISHES.slot()));
		helper.assertTrue(tab.contains("64 dishes waiting"), "the tab at the cap: " + tab);

		// right-click: down to none
		menu.clicked(dishSlot(menu, salad), 1, ClickType.PICKUP, player);
		helper.assertTrue(DishRequests.get(level).waiting(hall, saladId) == 0, "a right-click left " + DishRequests.get(level).waiting(hall, saladId));
		saladLore = lore(menu.icon(dishSlot(menu, salad)));
		helper.assertTrue(saladLore.stream().noneMatch(l -> l.startsWith("Asked for")), "the salad after the right-click: " + saladLore);
		menu.clicked(dishSlot(menu, salad), 1, ClickType.PICKUP, player);
		helper.assertTrue(DishRequests.get(level).waiting(hall, saladId) == 0, "a second right-click");

		// the Chef rises to Apprentice: the soda opens, exactly at his level
		chef.setVillagerData(chef.getVillagerData().setLevel(2));
		menu = board(helper, player, hall);
		sodaLore = lore(menu.icon(dishSlot(menu, soda)));
		helper.assertFalse(TradePage.marks(menu.icon(dishSlot(menu, soda))).contains(DishesTab.LOCKED), "the soda is locked for an Apprentice: " + sodaLore);
		helper.assertTrue(sodaLore.contains("Click: ask for 4 more. Shift-click: 16 more"), "the soda for an Apprentice: " + sodaLore);
		menu.clicked(dishSlot(menu, soda), 0, ClickType.PICKUP, player);
		helper.assertTrue(DishRequests.get(level).waiting(hall, CuisineDishes.of(soda).id()) == 4, "an Apprentice's village couldn't ask for a soda");

		// the answers, word for word
		String sodaName = soda.getDescription().getString();
		String[][] said = {
			{DishRequests.message(DishRequests.Result.ASKED, salad, 4, 12).getString(), "Asked the Chef for 4 " + saladName + " (12 waiting)"},
			{DishRequests.message(DishRequests.Result.NO_CHEF, salad, 4, 0).getString(), "The village has no Chef to cook it"},
			{DishRequests.message(DishRequests.Result.LEVEL, soda, 4, 0).getString(), sodaName + " needs the Chef to be Apprentice"},
			{DishRequests.message(DishRequests.Result.FULL, salad, 4, 64).getString(), "64 " + saladName + " are already waiting"},
			{DishRequests.message(DishRequests.Result.UNKNOWN, Items.BREAD, 4, 0).getString(), "That isn't a dish the Chef cooks"},
		};
		for (String[] pair : said) {
			helper.assertTrue(pair[0].equals(pair[1]), "said \"" + pair[0] + "\", expected \"" + pair[1] + "\"");
		}
		helper.assertTrue(DishRequests.request(level, hall, Items.BREAD, 4) == DishRequests.Result.UNKNOWN, "bread is a dish");
		// the wiki names the three drinks the Apprentice opens and the inn serves
		String drinks = cc("lemon_soda").getDescription().getString() + ", " + cc("coffee").getDescription().getString() + ", "
			+ cc("fruit_punch").getDescription().getString();
		helper.assertTrue(drinks.equals("Bouncy Brionne Soda, Teddiursa Iced Coffee, Fruit Punch"), "the drinks are called " + drinks);
		level.getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	// ---- 38.8: the Camp Cook ----

	/** The Camp Cook with every making of the Beanburger Curry but the cooked chicken: no curry, nothing taken. */
	//$ gametest_ticks_batch AREA '1400' '"qa_cuisine_camp_short"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qa_cuisine_camp_short")
	public void theCampCookShortOfOneMakingCooksNoCurry(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Container chest = chest(helper, CHEST, new ItemStack(cc("red_bean"), 4), new ItemStack(Items.BREAD, 4), new ItemStack(Items.POTATO, 4),
			new ItemStack(cm("charti_berry"), 4));
		helper.setDayTime(2000);
		Block campfire = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "campfire"));
		helper.setBlock(STATION, campfire.defaultBlockState());
		((CampfireBlockEntity) helper.getBlockEntity(STATION)).setPotItem(new ItemStack(cm("campfire_pot_red")));
		Villager cook = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), cook, helper.absolutePos(STATION), ModVillagers.CAMPFIRE_POT_POI, ModVillagers.CAMP_COOK);
		helper.runAfterDelay(1200, () -> {
			helper.assertTrue(chest.countItem(cc("beanburger_curry")) == 0, "a curry without its chicken");
			helper.assertTrue(chest.countItem(cc("red_bean")) == 4 && chest.countItem(cm("charti_berry")) == 4 && chest.countItem(Items.POTATO) == 4,
				"the makings were taken: " + chest.countItem(cc("red_bean")) + " beans, " + chest.countItem(cm("charti_berry")) + " berries, "
					+ chest.countItem(Items.POTATO) + " potatoes");
			helper.assertTrue(ModAttachments.DISHES_COOKED.getOrElse(cook, 0) == 0, "cooked " + ModAttachments.DISHES_COOKED.getOrElse(cook, 0));
			helper.succeed();
		});
	}

	// ---- 38.9: bean farms ----

	private static List<BlockPos> beanSpots() {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(5, 1, 5), new BlockPos(7, 1, 7))) {
			if (!p.equals(new BlockPos(6, 1, 6))) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	/** The other side of 16: every wanted colour at 16 but the green bean at 15, and the farmer sows. */
	//$ gametest_ticks_batch AREA '2400' '"qa_bean_fifteen"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_bean_fifteen")
	public void fifteenOfOneColourSowsBeans(GameTestHelper helper) {
		Set<Item> wanted = BeanFarms.wanted(helper.getLevel());
		helper.assertTrue(wanted.contains(cc("green_bean")), "the green bean isn't wanted: " + wanted);
		List<ItemStack> chest = new ArrayList<>();
		chest.add(new ItemStack(cc("bean_seeds"), 16));
		for (Item bean : wanted) {
			chest.add(new ItemStack(bean, bean == cc("green_bean") ? BeanFarms.LOW - 1 : BeanFarms.LOW));
		}
		BeanFarmCompatTests.farmer(helper, Blocks.AIR.defaultBlockState(), chest.toArray(ItemStack[]::new));
		helper.succeedWhen(() -> {
			int sown = 0;
			for (BlockPos p : beanSpots()) {
				if (helper.getBlockState(p.above()).is(BeanFarmCompatTests.crop())) {
					sown++;
				}
			}
			helper.assertTrue(sown == beanSpots().size(), sown + " of " + beanSpots().size() + " spots sown with 15 green beans in the store");
		});
	}

	/**
	 * A bean crop one stage short of ripe is never cut, the one ripe plant beside them is (random ticks off, so none
	 * ripens on its own).
	 */
	//$ gametest_ticks_batch AREA '1600' '"qa_bean_one_short"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa_bean_one_short")
	public void aBeanOneStageShortIsLeftStanding(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GameRules.IntegerValue ticking = level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING);
		int was = ticking.get();
		ticking.set(0, level.getServer());
		PartnerShowsCompatTests.after(helper, () -> ticking.set(was, level.getServer()));
		int max = BeanFarmCompatTests.maxAge();
		Villager farmer = BeanFarmCompatTests.farmer(helper, BeanFarmCompatTests.cropAt(max - 1));
		List<BlockPos> spots = beanSpots();
		BlockPos ripe = spots.get(0).above();
		helper.setBlock(ripe, BeanFarmCompatTests.cropAt(max));
		helper.runAtTickTime(1400, () -> {
			for (BlockPos p : spots.subList(1, spots.size())) {
				helper.assertBlockPresent(BeanFarmCompatTests.crop(), p.above());
				int age = helper.getBlockState(p.above()).getValue(BeanFarmCompatTests.age());
				helper.assertTrue(age == max - 1, "the bean at " + p.above() + " is now age " + age + ", was " + (max - 1));
			}
			int harvested = ModAttachments.FARM_HARVESTED.getOrElse(farmer, 0);
			helper.assertTrue(harvested == 1, harvested + " harvested: only the one ripe plant should be");
			Container c = helper.getBlockEntity(new BlockPos(2, 2, 4));
			int beans = 0;
			for (int i = 0; i < c.getContainerSize(); i++) {
				if (c.getItem(i).is(BEANS)) {
					beans += c.getItem(i).getCount();
				}
			}
			helper.assertTrue(beans >= 1, "the ripe plant's beans didn't reach the chest");
			helper.succeed();
		});
	}

	// ---- 38.10: luxuries every 4 and 8 days ----

	private static final BlockPos STOREHOUSE = new BlockPos(12, 2, 2);
	private static final BlockPos STORE_CHEST = new BlockPos(12, 2, 4);

	private static Container store(GameTestHelper helper) {
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		return helper.getBlockEntity(STORE_CHEST);
	}

	/**
	 * A hall of the test's own at (2, 2, 2) whose village reaches 16 blocks: other batches' storehouses (the éclairs of
	 * the Noble's test) are within a full village's reach and would count as this village's store.
	 */
	private static BlockPos luxuryHall(GameTestHelper helper) {
		PartnerShowsCompatTests.clearHalls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		PartnerShowsCompatTests.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.VILLAGE_HALL);
		return helper.absolutePos(new BlockPos(2, 2, 2));
	}

	private static Villager household(GameTestHelper helper, String classId) {
		SocialClasses.SocialClass c = SocialClasses.get(AliveWorkplace.id(classId));
		helper.assertTrue(c != null, "no class " + classId);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 8));
		v.setNoAi(true);
		SocialClasses.seed(v, c);
		return v;
	}

	/**
	 * The Deluxe Shake every 4 days, exactly: had on day 10, not due on day 13, due on day 14; and a Burgher takes the
	 * class above's éclair too (the wiki: "a Burgher an éclair").
	 */
	//$ gametest_ticks_batch AREA '100' '"qa_luxury_burgher_days"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_luxury_burgher_days")
	public void theDeluxeShakeIsDueOnTheFourthDayNotTheThird(GameTestHelper helper) {
		BlockPos at = luxuryHall(helper);
		Container store = store(helper);
		store.setItem(0, new ItemStack(cc("deluxe_jewel_shake"), 3));
		store.setItem(1, new ItemStack(cc("eclair"), 1));
		Villager bea = household(helper, "burgher");
		ServerLevel level = helper.getLevel();
		SocialClasses.check(level, at, List.of(bea), 10, SocialClasses.SLICE);
		helper.assertTrue(store.countItem(cc("deluxe_jewel_shake")) == 2, "day 10: " + store.countItem(cc("deluxe_jewel_shake")) + " shakes left");
		helper.assertTrue(store.countItem(cc("eclair")) == 0, "a Burgher didn't take the class above's éclair: store " + dump(store) + ", had "
			+ ModAttachments.LUXURIES_HAD.getOrElse(bea, Map.of()) + ", class " + SocialClasses.of(bea).id() + ", above " + SocialClasses.step(SocialClasses.of(bea), 1)
			.id() + " wants " + Luxuries.of(SocialClasses.step(SocialClasses.of(bea), 1)).stream().map(Luxuries.Luxury::id).toList());
		SocialClasses.check(level, at, List.of(bea), 13, SocialClasses.SLICE);
		helper.assertTrue(store.countItem(cc("deluxe_jewel_shake")) == 2, "taken on day 13, three days after: " + store.countItem(cc("deluxe_jewel_shake")));
		SocialClasses.check(level, at, List.of(bea), 14, SocialClasses.SLICE);
		Map<ResourceLocation, Long> had = ModAttachments.LUXURIES_HAD.getOrElse(bea, Map.of());
		helper.assertTrue(store.countItem(cc("deluxe_jewel_shake")) == 1 && Long.valueOf(14L).equals(had.get(AliveWorkplace.id("deluxe_shake"))),
			"day 14: " + store.countItem(cc("deluxe_jewel_shake")) + " left, had " + had);
		helper.succeed();
	}

	/** The Clodsire Eclair every 8 days for a Noble: had on day 20, not on day 27, again on day 28. */
	//$ gametest_ticks_batch AREA '100' '"qa_luxury_noble_days"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_luxury_noble_days")
	public void theEclairIsDueOnTheEighthDayNotTheSeventh(GameTestHelper helper) {
		Luxuries.Luxury eclair = Luxuries.get(AliveWorkplace.id("clodsire_eclair"));
		helper.assertTrue(eclair != null && Luxuries.of(SocialClasses.get(AliveWorkplace.id("noble"))).contains(eclair), "a Noble doesn't want the éclair");
		int fall = SocialClasses.FALL_DAYS;
		SocialClasses.FALL_DAYS = 100; // the bare test village meets no Noble need: keep the household Noble for the three dawns
		PartnerShowsCompatTests.after(helper, () -> SocialClasses.FALL_DAYS = fall);
		BlockPos at = luxuryHall(helper);
		Container store = store(helper);
		store.setItem(0, new ItemStack(cc("eclair"), 3));
		Villager noble = household(helper, "noble");
		ServerLevel level = helper.getLevel();
		SocialClasses.check(level, at, List.of(noble), 20, SocialClasses.SLICE);
		helper.assertTrue(store.countItem(cc("eclair")) == 2, "day 20: " + store.countItem(cc("eclair")) + " left");
		SocialClasses.check(level, at, List.of(noble), 27, SocialClasses.SLICE);
		helper.assertTrue(store.countItem(cc("eclair")) == 2, "taken on day 27, seven days after: " + store.countItem(cc("eclair")));
		SocialClasses.check(level, at, List.of(noble), 28, SocialClasses.SLICE);
		Map<ResourceLocation, Long> had = ModAttachments.LUXURIES_HAD.getOrElse(noble, Map.of());
		helper.assertTrue(store.countItem(cc("eclair")) == 1 && Long.valueOf(28L).equals(had.get(eclair.id())),
			"day 28: " + store.countItem(cc("eclair")) + " left, had " + had);
		helper.assertTrue(SocialClasses.of(noble) == SocialClasses.get(AliveWorkplace.id("noble")), "the household is no longer Noble: " + SocialClasses.of(noble));
		helper.succeed();
	}

	// ---- 38.10: the partner's treat ----

	/**
	 * "Once a day only from the store": Poké Puffs in a chest by the bench but no store are never fed; with a Storehouse
	 * one is, not twice that day; the next day a store of only luxuries and bread feeds nothing and keeps them; a puff
	 * put in, and it is fed again.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_partner_store_only"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_partner_store_only")
	public void aPartnerIsFedOnlyFromTheStoreOnceADay(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		BlockPos by = helper.absolutePos(new BlockPos(1, 2, 8));
		owner.teleportTo(by.getX() + 0.5, by.getY(), by.getZ() + 0.5);
		BlockPos pasture = PastureCompatTests.pasture(helper, new BlockPos(6, 2, 6));
		PastureCompatTests.pastured(helper, pasture, owner, "machop", Direction.NORTH);
		BlockPos bench = new BlockPos(9, 2, 6);
		helper.setBlock(bench, Blocks.CRAFTING_TABLE);
		Container byBench = chest(helper, new BlockPos(10, 2, 6), new ItemStack(cc("sweet_pokepuff"), 3));
		ServerLevel level = helper.getLevel();
		BlockPos site = helper.absolutePos(bench);
		helper.runAfterDelay(10, () -> {
			PokemonEntity machop = PartnersAtWorkCompatTests.pokemon(helper, "machop");
			long today = Chronicle.day(level);
			int fed = PartnerTreats.feed(level, site, Set.of("fighting"), 3, today);
			helper.assertTrue(fed == 0 && byBench.countItem(cc("sweet_pokepuff")) == 3 && CobblemonPartners.treatDay(machop) == -1,
				"fed from a chest that isn't the store: " + fed + " fed, " + byBench.countItem(cc("sweet_pokepuff")) + " puffs left");
			helper.setBlock(new BlockPos(10, 2, 6), Blocks.AIR);

			Container store = store(helper);
			store.setItem(0, new ItemStack(cc("sweet_pokepuff"), 1));
			fed = PartnerTreats.feed(level, site, Set.of("fighting"), 3, today);
			helper.assertTrue(fed == 1 && store.countItem(cc("sweet_pokepuff")) == 0 && CobblemonPartners.treatDay(machop) == today,
				"from the store: " + fed + " fed, day " + CobblemonPartners.treatDay(machop));
			store.setItem(0, new ItemStack(cc("sweet_pokepuff"), 1));
			fed = PartnerTreats.feed(level, site, Set.of("fighting"), 3, today);
			helper.assertTrue(fed == 0 && store.countItem(cc("sweet_pokepuff")) == 1, "fed twice the same day: " + fed);

			store.setItem(0, ItemStack.EMPTY);
			store.setItem(1, new ItemStack(cc("deluxe_jewel_shake"), 1));
			store.setItem(2, new ItemStack(cc("eclair"), 1));
			store.setItem(3, new ItemStack(Items.BREAD, 4));
			fed = PartnerTreats.feed(level, site, Set.of("fighting"), 3, today + 1);
			helper.assertTrue(fed == 0 && store.countItem(cc("deluxe_jewel_shake")) == 1 && store.countItem(cc("eclair")) == 1 && store.countItem(Items.BREAD) == 4
				&& CobblemonPartners.treatDay(machop) == today, "the next day, no puff or shake in the store: " + fed + " fed");
			store.setItem(4, new ItemStack(cc("sweet_pokepuff"), 1));
			fed = PartnerTreats.feed(level, site, Set.of("fighting"), 3, today + 1);
			helper.assertTrue(fed == 1 && CobblemonPartners.treatDay(machop) == today + 1, "the next day with a puff: " + fed + " fed");
			level.getServer().getPlayerList().remove(owner);
			helper.succeed();
		});
	}
}
