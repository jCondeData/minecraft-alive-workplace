package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trade.Economy;
import io.github.jcondedata.aliveworkplace.trade.Market;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-c-1025-0053): the trade goods engine, the price board and trading at the board (ROADMAP 33.2-33.5), from
 * the spec, at its boundaries from both sides.
 *
 * <p>Expectations from ROADMAP 33.2: "up to 3 goods it's known for (2 points for each worker of a making job, up to 6; 3
 * if the hall stands in a making biome; 1 if its Storehouses hold two bundles or more; 4 points to count) and up to 3
 * it's short of (3 if its biome wants it; 1 for each worker of a job that uses it, up to 3; 2 if a worker is waiting for
 * it ...; 1 if the Storehouses hold less than a bundle; 4 points to count)"; "its base times 1 + 0.2 x (demand - supply),
 * kept between half and twice the base (supply: bundles in the Storehouses, up to 5, plus 3 if known for it; demand: 3 if
 * short of it, plus bundles workers are waiting for, up to 3, plus 2 for food while the store is under 16 meals ...)";
 * "Off: nothing is worked out" (and what was stays). 33.5: "it never pays more than its treasury holds"; "The village
 * sells at 10% over what it pays, each bundle moves that day's price 2%"; the goods come out of the Storehouse's chests.
 * Uses the six test goods of the gametest data pack (Test Timber: logs, 16 a bundle, base 1 emerald, made by lumberjacks
 * in forests, wanted in deserts and by builders and carpenters).
 */
public class QaTradeBoardGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(9, 2, 9);
	private static final ResourceLocation TIMBER = BoardTradeGameTests.TIMBER;
	private static final ResourceLocation GRAIN = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_grain");
	private static final ResourceLocation FISH = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_fish");
	private static final ResourceLocation STONE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_stone");
	private static final ResourceLocation REMEDIES = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_remedies");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	// ---- helpers ----

	/** Makes the biome round the hall {@code biome}; the old one goes back when the test ends. */
	private static void biome(GameTestHelper helper, String biome) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		String old = level.getBiome(hall).unwrapKey().map(k -> k.location().toString()).orElse("minecraft:plains");
		fill(level, hall, biome);
		Leftovers.after(helper, () -> fill(level, hall, old));
		helper.assertTrue(level.getBiome(hall).is(ResourceLocation.parse(biome)), "the hall isn't in " + biome);
	}

	private static void fill(ServerLevel level, BlockPos at, String biome) {
		String cmd = "fillbiome " + (at.getX() - 6) + " " + (at.getY() - 4) + " " + (at.getZ() - 6) + " " + (at.getX() + 6) + " " + (at.getY() + 4)
			+ " " + (at.getZ() + 6) + " " + biome;
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(), cmd);
	}

	/** A worker of {@code job} with a job site, no AI. */
	private static Villager worker(GameTestHelper helper, VillagerProfession job, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job));
		v.refreshBrain(helper.getLevel());
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(at.below())));
		return v;
	}

	/** {@code n} workers of {@code job} in row {@code z} (x = 3, 4, 5 ...). */
	private static void workers(GameTestHelper helper, VillagerProfession job, int n, int z) {
		for (int i = 0; i < n; i++) {
			worker(helper, job, new BlockPos(3 + i, 2, z));
		}
	}

	/** A builder at a bench by the hall whose build waits for {@code logs} oak logs. */
	private static BuildSite waitingBuilder(GameTestHelper helper, int logs) {
		ServerLevel level = helper.getLevel();
		BlockPos bench = new BlockPos(14, 2, 4);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(13, 2, 4));
		builder.setNoAi(true);
		Builders.employ(level, builder, helper.absolutePos(bench));
		builder.setVillagerData(builder.getVillagerData().setProfession(ModVillagers.BUILDER));
		BuildSite site = Builders.start(level, builder, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 2, 14)), Rotation.NONE, Mirror.NONE));
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		site.setStatus(BuildSite.Status.WAITING_FOR_MATERIALS);
		site.setMissing(new HashMap<>(Map.of(Items.OAK_LOG, logs)));
		return site;
	}

	/**
	 * The hall's round for a fresh market (no price from yesterday, so every price is today's target): the census, the
	 * caravans' list, then the count.
	 */
	private static Market fresh(GameTestHelper helper, BlockPos hall) {
		ServerLevel level = helper.getLevel();
		VillageHalls.Census census = VillageHalls.census(level, hall);
		Caravans.round(level, hall, census);
		Caravans.Data.get(level).setMarket(hall, new Market(List.of(), List.of(), new LinkedHashMap<>(), -1, List.of()));
		Market m = Economy.count(level, hall, census, Chronicle.day(level));
		helper.assertTrue(m != null, "no count");
		return m;
	}

	private static void logs(Container chest, int n) {
		chest.clearContent();
		int slot = 0;
		while (n > 0) {
			int k = Math.min(64, n);
			chest.setItem(slot++, new ItemStack(Items.OAK_LOG, k));
			n -= k;
		}
	}

	private static String what(Market m) {
		return "known " + m.knownFor() + ", short " + m.shortOf();
	}

	private static Market.Price price(GameTestHelper helper, BlockPos hall, ResourceLocation good) {
		return Caravans.Data.get(helper.getLevel()).market(hall).prices().get(good);
	}

	/** The hall's screen, its minecart, then the Prices tab. */
	private static ChoiceMenu board(GameTestHelper helper, ServerPlayer player, BlockPos hall) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		menu.press(VillageHallScreen.ROUTES, player);
		helper.assertTrue(menu.icon(TradePage.Tab.PRICES.slot()).is(Items.EMERALD), "no Prices tab: " + menu.icon(TradePage.Tab.PRICES.slot()));
		menu.press(TradePage.Tab.PRICES.slot(), player);
		return menu;
	}

	private static String said(ChoiceMenu menu, int slot) {
		List<String> lore = BoardTradeGameTests.lore(menu.icon(slot));
		return lore.isEmpty() ? "" : lore.get(0);
	}

	// ---- 33.2: known for, short of, prices ----

	/** Known for needs 4 points: one lumberjack outside a forest (2) isn't enough, two (4) are. */
	//$ gametest_ticks_batch AREA '100' '"qaTradeKnownWorkers"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaTradeKnownWorkers")
	public void twoLumberjacksMakeAVillageKnownForTimberOneDoesNot(GameTestHelper helper) {
		biome(helper, "minecraft:plains");
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		BoardTradeGameTests.chest(helper).clearContent();
		helper.runAfterDelay(2, () -> {
			worker(helper, ModVillagers.LUMBERJACK, new BlockPos(3, 2, 4));
			Market one = fresh(helper, hall);
			helper.assertTrue(!one.knownFor().contains(TIMBER), "one lumberjack (2 points) made it known for timber: " + what(one));
			worker(helper, ModVillagers.LUMBERJACK, new BlockPos(4, 2, 4));
			Market two = fresh(helper, hall);
			helper.assertTrue(two.knownFor().contains(TIMBER), "two lumberjacks (4 points) didn't make it known for timber: " + what(two));
			helper.succeed();
		});
	}

	/** A forest alone is 3 points (not known for timber); with two bundles of logs in the Storehouse it's 4 (known); 31 logs aren't two bundles. */
	//$ gametest_ticks_batch AREA '100' '"qaTradeKnownBiome"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaTradeKnownBiome")
	public void aForestWithTwoBundlesInStoreIsKnownForTimberWithoutWorkers(GameTestHelper helper) {
		biome(helper, "minecraft:forest");
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		helper.runAfterDelay(2, () -> {
			Container chest = BoardTradeGameTests.chest(helper);
			logs(chest, 0);
			Market none = fresh(helper, hall);
			helper.assertTrue(!none.knownFor().contains(TIMBER), "a forest alone (3 points): " + what(none));
			logs(chest, 31);
			Market almost = fresh(helper, hall);
			helper.assertTrue(!almost.knownFor().contains(TIMBER), "a forest and 31 logs (under two bundles, 3 points): " + what(almost));
			logs(chest, 32);
			Market two = fresh(helper, hall);
			helper.assertTrue(two.knownFor().contains(TIMBER), "a forest and two bundles (4 points): " + what(two));
			helper.assertTrue(!two.shortOf().contains(TIMBER), "known for and short of timber at once: " + what(two));
			helper.succeed();
		});
	}

	/** A desert wants timber (3 points): short of it with less than a bundle in store (15 logs, 4 points), not with a whole bundle (16). */
	//$ gametest_ticks_batch AREA '100' '"qaTradeShortBiome"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaTradeShortBiome")
	public void aDesertIsShortOfTimberOnlyWithLessThanABundleInStore(GameTestHelper helper) {
		biome(helper, "minecraft:desert");
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		helper.runAfterDelay(2, () -> {
			Container chest = BoardTradeGameTests.chest(helper);
			logs(chest, 16);
			Market bundle = fresh(helper, hall);
			helper.assertTrue(!bundle.shortOf().contains(TIMBER), "a desert with a bundle in store (3 points) is short of timber: " + what(bundle));
			logs(chest, 15);
			Market less = fresh(helper, hall);
			helper.assertTrue(less.shortOf().contains(TIMBER), "a desert with 15 logs (4 points) isn't short of timber: " + what(less));
			helper.succeed();
		});
	}

	/** Workers who use a good count 1 each, up to 3: four carpenters with a bundle in store are still 3 points; three with less than a bundle are 4. */
	//$ gametest_ticks_batch AREA '100' '"qaTradeShortUsers"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaTradeShortUsers")
	public void carpentersCountForTimberUpToThree(GameTestHelper helper) {
		biome(helper, "minecraft:plains");
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		helper.runAfterDelay(2, () -> {
			Container chest = BoardTradeGameTests.chest(helper);
			logs(chest, 16);
			workers(helper, ModVillagers.CARPENTER, 3, 4);
			Market three = fresh(helper, hall);
			helper.assertTrue(!three.shortOf().contains(TIMBER), "three carpenters and a bundle in store (3 points): " + what(three));
			worker(helper, ModVillagers.CARPENTER, new BlockPos(6, 2, 4));
			Market four = fresh(helper, hall);
			helper.assertTrue(!four.shortOf().contains(TIMBER), "four carpenters count more than 3: " + what(four));
			logs(chest, 15);
			Market low = fresh(helper, hall);
			helper.assertTrue(low.shortOf().contains(TIMBER), "carpenters (3) and less than a bundle (1) aren't 4 points: " + what(low));
			helper.succeed();
		});
	}

	/** Never more than 3 goods: four goods with enough points to be known for, and the village names only three, the highest. */
	//$ gametest_ticks_batch AREA '100' '"qaTradeMostThree"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaTradeMostThree")
	public void aVillageIsKnownForAndShortOfThreeGoodsAtMost(GameTestHelper helper) {
		biome(helper, "minecraft:desert");
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.chest(helper).clearContent();
			// short of: timber, grain and fish (desert, 3, store empty, 1) and remedies (3 nurses, 3, store empty, 1); a chef
			// makes fish and grain 5
			workers(helper, ModVillagers.NURSE, 3, 4);
			worker(helper, ModVillagers.CHEF, new BlockPos(3, 2, 6));
			Market shortM = fresh(helper, hall);
			helper.assertTrue(shortM.shortOf().size() == 3 && shortM.shortOf().contains(FISH) && shortM.shortOf().contains(GRAIN),
				"short of: " + what(shortM));

			// known for: lumberjacks (6), miners (6), fishermen (6), armorers (4): only three, and not the armorers' arms
			workers(helper, ModVillagers.LUMBERJACK, 3, 11);
			workers(helper, ModVillagers.MINER, 3, 12);
			workers(helper, VillagerProfession.FISHERMAN, 3, 13);
			worker(helper, VillagerProfession.ARMORER, new BlockPos(7, 2, 6));
			worker(helper, VillagerProfession.ARMORER, new BlockPos(8, 2, 6));
			Market known = fresh(helper, hall);
			helper.assertTrue(known.knownFor().size() <= 3 && known.shortOf().size() <= 3, "more than three: " + what(known));
			helper.assertTrue(known.knownFor().size() == 3 && known.knownFor().containsAll(List.of(TIMBER, STONE, FISH)), "the highest three: " + what(known));
			for (ResourceLocation g : known.knownFor()) {
				helper.assertTrue(!known.shortOf().contains(g), g + " is both: " + what(known));
			}
			helper.succeed();
		});
	}

	/**
	 * Supply counts the bundles in store up to 5 and demand the bundles a worker waits for up to 3: in a desert whose
	 * builder waits for logs (short of timber, demand 3 + waited for), the price is 1 + 0.2 x (demand - supply).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaTradePriceCaps"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaTradePriceCaps")
	public void supplyStopsAtFiveBundlesAndWaitingAtThree(GameTestHelper helper) {
		biome(helper, "minecraft:desert");
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		BuildSite site = waitingBuilder(helper, 80);
		helper.runAfterDelay(2, () -> {
			Container chest = BoardTradeGameTests.chest(helper);
			int[][] cases = {
				// logs in store, logs waited for, the price in hundredths
				{80, 80, 120}, // supply 5, demand 3 + 3 (5 waited for, 3 count)
				{96, 80, 120}, // 6 bundles in store count 5
				{64, 80, 140}, // supply 4
				{80, 48, 120}, // 3 waited for
				{80, 32, 100}, // 2 waited for: demand 5, supply 5
			};
			for (int[] c : cases) {
				logs(chest, c[0]);
				site.setMissing(new HashMap<>(Map.of(Items.OAK_LOG, c[1])));
				Market m = fresh(helper, hall);
				helper.assertTrue(m.shortOf().contains(TIMBER) && !m.knownFor().contains(TIMBER), c[0] + " in store, " + c[1] + " waited for: " + what(m));
				helper.assertTrue(m.prices().get(TIMBER).cents() == c[2], c[0] + " logs in store, " + c[1] + " waited for: timber at "
					+ m.prices().get(TIMBER).cents() + ", expected " + c[2]);
			}
			helper.succeed();
		});
	}

	/** Food is wanted (+2 demand) while the store holds under 16 meals: grain at 1.4 with 15 bread in store, at its base with 16. */
	//$ gametest_ticks_batch AREA '100' '"qaTradeFood"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaTradeFood")
	public void foodIsDearWhileTheStoreHoldsUnderSixteenMeals(GameTestHelper helper) {
		biome(helper, "minecraft:forest");
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		helper.runAfterDelay(2, () -> {
			Container chest = BoardTradeGameTests.chest(helper);
			chest.clearContent();
			chest.setItem(0, new ItemStack(Items.BREAD, 15));
			Market hungry = fresh(helper, hall);
			helper.assertTrue(hungry.prices().get(GRAIN).cents() == 140, "grain with 15 meals in store: " + hungry.prices().get(GRAIN) + " " + what(hungry));
			helper.assertTrue(hungry.prices().get(TIMBER).cents() == 100, "timber isn't food: " + hungry.prices().get(TIMBER));
			chest.setItem(0, new ItemStack(Items.BREAD, 16));
			Market fed = fresh(helper, hall);
			helper.assertTrue(fed.prices().get(GRAIN).cents() == 100, "grain with 16 meals in store: " + fed.prices().get(GRAIN) + " " + what(fed));
			helper.succeed();
		});
	}

	/**
	 * With villageEconomy off a hall's round works nothing out and leaves the saved prices alone; switched back on, the
	 * next count moves on a third of the way from the saved price (not from scratch).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaTradeOffKeeps"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaTradeOffKeeps")
	public void switchedOffTheEconomyKeepsItsLastPrices(GameTestHelper helper) {
		biome(helper, "minecraft:plains");
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, false);
		Leftovers.after(helper, () -> new WorkplaceConfig().apply());
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			Caravans.Data data = Caravans.Data.get(level);
			VillageHalls.Census census = VillageHalls.census(level, hall);
			Caravans.round(level, hall, census);
			long today = Chronicle.day(level);
			Map<ResourceLocation, Market.Price> prices = new LinkedHashMap<>();
			prices.put(TIMBER, new Market.Price(200, 190, 0));
			Market saved = new Market(List.of(TIMBER), List.of(), prices, today - 1, List.of());
			data.setMarket(hall, saved);

			WorkplaceConfig.parse("{\"villageEconomy\": false}").apply();
			VillageHalls.RADIUS = 8; // the config puts the village radius back; keep this test's small village
			helper.assertTrue(!Economy.ENABLED, "the switch didn't turn it off");
			Economy.round(level, hall, census);
			helper.assertTrue(Economy.count(level, hall, census, today) == null, "a count with the economy off");
			helper.assertTrue(data.market(hall).equals(saved), "the economy off changed the saved market: " + data.market(hall));

			new WorkplaceConfig().apply();
			VillageHalls.RADIUS = 8;
			helper.assertTrue(Economy.ENABLED, "the economy didn't come back on");
			Market on = Economy.count(level, hall, census, today);
			// plains, no workers, no Storehouse: timber's target is its base (1.00); from 2.00 a third of the way is 1.67
			helper.assertTrue(on != null && on.prices().get(TIMBER).cents() == 167 && on.prices().get(TIMBER).yesterday() == 200,
				"back on, timber moved from the saved 2.00 to " + (on == null ? null : on.prices().get(TIMBER)));
			helper.assertTrue(!on.knownFor().contains(TIMBER), "known for timber with nothing that makes it: " + what(on));
			helper.succeed();
		});
	}

	// ---- 33.5: trading at the board ----

	/** The treasury exactly at a bundle's price pays for it; a cent short, it doesn't, and says what it holds; shift stops at exactly what it can pay. */
	//$ gametest_ticks_batch AREA '100' '"qaBoardTreasuryEdge"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBoardTreasuryEdge")
	public void theTreasuryPaysWithExactlyEnoughButNotACentLess(GameTestHelper helper) {
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			BoardTradeGameTests.price(helper, hall, TIMBER, 100);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			String village = VillageHalls.name(helper.getLevel(), hall).getString();
			Container chest = BoardTradeGameTests.chest(helper);
			player.getInventory().add(new ItemStack(Items.OAK_LOG, 64));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");

			// a cent short of 1.00: nothing moves
			entity.setTreasury(99);
			menu.press(timber, player);
			helper.assertTrue(BoardTradeGameTests.has(player, Items.OAK_LOG) == 64 && BoardTradeGameTests.count(chest, Items.OAK_LOG) == 0
				&& entity.treasury() == 99 && BoardTradeGameTests.has(player, Items.EMERALD) == 0 && price(helper, hall, TIMBER).cents() == 100,
				"a sale with 0.99 in the treasury: treasury " + entity.treasury() + ", chest " + BoardTradeGameTests.count(chest, Items.OAK_LOG));
			helper.assertTrue(said(menu, timber).equals(village + "'s treasury can't pay for a bundle of Test Timber: it holds 0.99 emeralds."),
				"what it said: " + said(menu, timber));

			// exactly 1.00: sold
			entity.setTreasury(100);
			menu.press(timber, player);
			helper.assertTrue(BoardTradeGameTests.has(player, Items.OAK_LOG) == 48 && BoardTradeGameTests.count(chest, Items.OAK_LOG) == 16
				&& entity.treasury() == 0 && BoardTradeGameTests.has(player, Items.EMERALD) == 1, "a sale with exactly 1.00 in the treasury: treasury "
				+ entity.treasury() + ", paid " + BoardTradeGameTests.has(player, Items.EMERALD));
			helper.assertTrue(said(menu, timber).equals("You sell 16 × Test Timber to " + village + " for 1 emerald."), "what it said: " + said(menu, timber));

			// shift with exactly 0.98 + 0.96 = 1.94 in the treasury: two bundles, then it says the treasury stopped it
			entity.setTreasury(194);
			BoardTradeGameTests.price(helper, hall, TIMBER, 98);
			menu.clicked(timber, 0, ClickType.QUICK_MOVE, player);
			helper.assertTrue(BoardTradeGameTests.has(player, Items.OAK_LOG) == 16 && BoardTradeGameTests.count(chest, Items.OAK_LOG) == 48,
				"shift with 1.94: the player kept " + BoardTradeGameTests.has(player, Items.OAK_LOG));
			helper.assertTrue(said(menu, timber).equals("You sell 32 × Test Timber to " + village + " for 1 emerald. Its treasury can't pay for more."),
				"what it said: " + said(menu, timber));
			helper.assertTrue(entity.treasury() >= 0, "the treasury went below nothing: " + entity.treasury());
			helper.succeed();
		});
	}

	/** Buying costs 10% over the board's price, charged up to whole emeralds: 0.90 costs 0.99 (1 emerald), 1.00 costs 1.10 (2); exactly enough buys. */
	//$ gametest_ticks_batch AREA '100' '"qaBoardMarkup"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBoardMarkup")
	public void aPlayerWithExactlyTheMarkedUpPriceBuys(GameTestHelper helper) {
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			String village = VillageHalls.name(helper.getLevel(), hall).getString();
			Container chest = BoardTradeGameTests.chest(helper);
			logs(chest, 64);
			BoardTradeGameTests.price(helper, hall, TIMBER, 90);
			player.getInventory().add(new ItemStack(Items.EMERALD, 1));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			helper.assertTrue(BoardTradeGameTests.lore(menu.icon(timber)).contains("We sell: 0.99 emeralds"), "0.90 plus 10%: " + BoardTradeGameTests.lore(menu.icon(timber)));
			menu.press(timber, player);
			helper.assertTrue(BoardTradeGameTests.has(player, Items.OAK_LOG) == 16 && BoardTradeGameTests.has(player, Items.EMERALD) == 0 && entity.treasury() == 100
				&& BoardTradeGameTests.count(chest, Items.OAK_LOG) == 48, "0.99 with 1 emerald: " + BoardTradeGameTests.has(player, Items.OAK_LOG) + " logs, treasury "
				+ entity.treasury());
			helper.assertTrue(said(menu, timber).equals("You buy 16 × Test Timber from " + village + " for 1 emerald."), "what it said: " + said(menu, timber));
			helper.assertTrue(price(helper, hall, TIMBER).cents() > 90, "a purchase didn't raise the price: " + price(helper, hall, TIMBER));

			// 1.10 with exactly 2 emeralds: bought; then with none left, nothing moves
			player.getInventory().clearOrCountMatchingItems(s -> s.is(Items.OAK_LOG), 64, player.inventoryMenu.getCraftSlots());
			BoardTradeGameTests.price(helper, hall, TIMBER, 100);
			player.getInventory().add(new ItemStack(Items.EMERALD, 2));
			menu.press(timber, player);
			helper.assertTrue(BoardTradeGameTests.has(player, Items.OAK_LOG) == 16 && BoardTradeGameTests.has(player, Items.EMERALD) == 0 && entity.treasury() == 300,
				"1.10 with 2 emeralds: " + BoardTradeGameTests.has(player, Items.OAK_LOG) + " logs, " + BoardTradeGameTests.has(player, Items.EMERALD) + " emeralds");
			player.getInventory().clearOrCountMatchingItems(s -> s.is(Items.OAK_LOG), 64, player.inventoryMenu.getCraftSlots());
			int before = price(helper, hall, TIMBER).cents();
			menu.press(timber, player);
			helper.assertTrue(BoardTradeGameTests.has(player, Items.OAK_LOG) == 0 && BoardTradeGameTests.count(chest, Items.OAK_LOG) == 32 && entity.treasury() == 300
				&& price(helper, hall, TIMBER).cents() == before, "a purchase without money");
			helper.assertTrue(said(menu, timber).startsWith("You can't pay ") && said(menu, timber).endsWith(" for a bundle of Test Timber."), "what it said: " + said(menu, timber));
			helper.succeed();
		});
	}

	/** Each bundle moves the price 2%, never past half or twice the base: sold at 0.51 it stops at 0.50 and stays; bought at 1.99 it stops at 2.00. */
	//$ gametest_ticks_batch AREA '100' '"qaBoardClamp"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBoardClamp")
	public void boardTradesNeverPushAPricePastHalfOrTwiceItsBase(GameTestHelper helper) {
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			entity.setTreasury(6400);
			Container chest = BoardTradeGameTests.chest(helper);
			BoardTradeGameTests.price(helper, hall, TIMBER, 51);
			player.getInventory().add(new ItemStack(Items.OAK_LOG, 64));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			// 0.51 + 0.50 + 0.50 + 0.50: four bundles for 2.01, paid 2 emeralds
			menu.clicked(timber, 0, ClickType.QUICK_MOVE, player);
			helper.assertTrue(BoardTradeGameTests.count(chest, Items.OAK_LOG) == 64 && price(helper, hall, TIMBER).cents() == 50,
				"sold at the floor: " + price(helper, hall, TIMBER) + ", chest " + BoardTradeGameTests.count(chest, Items.OAK_LOG));
			helper.assertTrue(BoardTradeGameTests.has(player, Items.EMERALD) == 2, "four bundles at 0.51, 0.50, 0.50, 0.50 paid " + BoardTradeGameTests.has(player, Items.EMERALD));

			// at the ceiling
			BoardTradeGameTests.price(helper, hall, TIMBER, 199);
			logs(chest, 64);
			player.getInventory().add(new ItemStack(Items.EMERALD, 30));
			menu.clicked(timber, 1, ClickType.PICKUP, player);
			helper.assertTrue(price(helper, hall, TIMBER).cents() == 200, "bought at 1.99: " + price(helper, hall, TIMBER));
			menu.clicked(timber, 1, ClickType.PICKUP, player);
			helper.assertTrue(price(helper, hall, TIMBER).cents() == 200 && BoardTradeGameTests.count(chest, Items.OAK_LOG) == 32, "bought at 2.00: "
				+ price(helper, hall, TIMBER) + ", chest " + BoardTradeGameTests.count(chest, Items.OAK_LOG));
			helper.assertTrue(BoardTradeGameTests.lore(menu.icon(timber)).contains("We sell: 2.2 emeralds"), "the board at the ceiling: " + BoardTradeGameTests.lore(menu.icon(timber)));
			helper.succeed();
		});
	}

	/**
	 * A player whose pack is full loses nothing buying: the bundle is paid for once and ends up with them (in the pack or
	 * at their feet); with one free slot a shift-click buys one bundle and says they can't carry more.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaBoardFullPack"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBoardFullPack")
	public void aFullPackLosesNothingAndShiftStopsWhenItIsFull(GameTestHelper helper) {
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			String village = VillageHalls.name(helper.getLevel(), hall).getString();
			Container chest = BoardTradeGameTests.chest(helper);
			logs(chest, 64);
			BoardTradeGameTests.price(helper, hall, TIMBER, 100);
			var inv = player.getInventory();
			inv.setItem(0, new ItemStack(Items.EMERALD, 64));
			for (int i = 1; i < 36; i++) {
				inv.setItem(i, new ItemStack(Items.DIRT, 64));
			}
			ChoiceMenu menu = board(helper, player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			menu.press(timber, player);
			AABB around = player.getBoundingBox().inflate(4);
			int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, around, e -> e.getItem().is(Items.OAK_LOG)).stream()
				.mapToInt(e -> e.getItem().getCount()).sum();
			int got = BoardTradeGameTests.has(player, Items.OAK_LOG) + dropped;
			int paid = 64 - BoardTradeGameTests.has(player, Items.EMERALD);
			int left = BoardTradeGameTests.count(chest, Items.OAK_LOG);
			helper.assertTrue(left + got == 64, "logs went missing: the chest " + left + ", the player " + got);
			helper.assertTrue((got == 16 && paid == 2 && entity.treasury() == 200) || (got == 0 && paid == 0 && entity.treasury() == 0),
				"full pack: got " + got + " logs, paid " + paid + ", treasury " + entity.treasury());
			helper.getLevel().getEntitiesOfClass(ItemEntity.class, around, e -> e.getItem().is(Items.OAK_LOG)).forEach(ItemEntity::discard);
			inv.clearOrCountMatchingItems(s -> s.is(Items.OAK_LOG), 64, player.inventoryMenu.getCraftSlots());

			// one free slot: shift buys one bundle and stops
			logs(chest, 64);
			inv.setItem(35, ItemStack.EMPTY);
			int emeralds = BoardTradeGameTests.has(player, Items.EMERALD);
			menu.clicked(timber, 0, ClickType.QUICK_MOVE, player);
			helper.assertTrue(BoardTradeGameTests.has(player, Items.OAK_LOG) == 16 && BoardTradeGameTests.count(chest, Items.OAK_LOG) == 48,
				"one free slot: the player got " + BoardTradeGameTests.has(player, Items.OAK_LOG));
			helper.assertTrue(emeralds - BoardTradeGameTests.has(player, Items.EMERALD) == 2, "paid " + (emeralds - BoardTradeGameTests.has(player, Items.EMERALD)));
			helper.assertTrue(said(menu, timber).equals("You buy 16 × Test Timber from " + village + " for 2 emeralds. You can't carry more."), "what it said: " + said(menu, timber));
			helper.succeed();
		});
	}

	/**
	 * A hall not yet on the caravans' list (before its first round) doesn't trade at the board, and says so; nothing
	 * moves. (A listed village whose goods aren't counted yet trades at the base prices.)
	 */
	//$ gametest_ticks_batch AREA '100' '"qaBoardNotCounted"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBoardNotCounted")
	public void aHallBeforeItsFirstRoundTradesNothing(GameTestHelper helper) {
		BlockPos hall = BoardTradeGameTests.hall(helper, HALL, true);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BoardTradeGameTests.price(helper, hall, TIMBER, 100);
			VillageHallBlockEntity entity = BoardTradeGameTests.entity(helper, hall);
			entity.setTreasury(1000);
			player.getInventory().add(new ItemStack(Items.OAK_LOG, 16));
			ChoiceMenu menu = board(helper, player, hall);
			int timber = BoardTradeGameTests.slot(helper, menu, "Test Timber");
			// the entry gone, as before the hall's first round; clicked in the same tick, before any round could list it
			Caravans.Data.get(level).remove(hall);
			menu.press(timber, player);
			helper.assertTrue(BoardTradeGameTests.has(player, Items.OAK_LOG) == 16 && entity.treasury() == 1000 && BoardTradeGameTests.has(player, Items.EMERALD) == 0
				&& BoardTradeGameTests.count(BoardTradeGameTests.chest(helper), Items.OAK_LOG) == 0, "traded before the hall's first round");
			helper.assertTrue(said(menu, timber).equals("The board isn't open yet: the hall is still taking stock."), "what it said: " + said(menu, timber));
			helper.succeed();
		});
	}
}
