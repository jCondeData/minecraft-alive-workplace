package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trade.Economy;
import io.github.jcondedata.aliveworkplace.trade.Market;
import io.github.jcondedata.aliveworkplace.trade.Prices;
import io.github.jcondedata.aliveworkplace.trade.Specialties;
import io.github.jcondedata.aliveworkplace.trade.TradeGoods;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * ROADMAP 33.2, the trade goods engine: goods load from {@code trade_goods/} (six test goods in the gametest data pack),
 * the hall's daily count makes a village known for what its workers and biome make and short of what it lacks, prices
 * move a third of the way to their target each dawn between half and twice the base, events raise demand for their
 * days only, the new keys on {@code Caravans.Data} survive a reload (and a 1.6 entry loads empty), and with
 * {@code villageEconomy} off nothing is worked out while caravans carry as before.
 */
public class TradeGoodsGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(9, 2, 9);
	private static final ResourceLocation TIMBER = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_timber");
	private static final ResourceLocation GRAIN = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_grain");
	private static final ResourceLocation FISH = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_fish");
	private static final ResourceLocation STONE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_stone");
	private static final ResourceLocation ARMS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_arms");
	private static final ResourceLocation REMEDIES = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_remedies");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	/**
	 * A hall at {@link #HALL} with a small village radius, alone in its batch; put back when the test ends. Only the six
	 * test goods count while it runs (33.3's real goods would crowd their top 3 and add their own raid demand).
	 */
	private static BlockPos hall(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		TradeGoodsDataGameTests.goodsFrom(helper, "aliveworkplace_test");
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 8;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> Caravans.Data.get(helper.getLevel()).remove(hall));
		return hall;
	}

	/** Makes the biome round the hall {@code biome} (as {@code /fillbiome} does), and puts the old one back when the test ends. */
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

	/** A worker of {@code job} with a job site by the hall (no AI: nothing changes their job while the test runs). */
	private static Villager worker(GameTestHelper helper, VillagerProfession job, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job));
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(at.below())));
		return v;
	}

	/** A builder at a bench by the hall whose build waits for {@code logs} oak logs. */
	private static Villager waitingBuilder(GameTestHelper helper, int logs) {
		ServerLevel level = helper.getLevel();
		BlockPos bench = new BlockPos(14, 2, 4);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(13, 2, 4));
		builder.setNoAi(true);
		Builders.employ(level, builder, helper.absolutePos(bench));
		builder.setVillagerData(builder.getVillagerData().setProfession(ModVillagers.BUILDER));
		BuildSite site = Builders.start(level, builder, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 2, 12)), Rotation.NONE, Mirror.NONE));
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		site.setStatus(BuildSite.Status.WAITING_FOR_MATERIALS);
		site.setMissing(new HashMap<>(Map.of(Items.OAK_LOG, logs)));
		return builder;
	}

	/** The hall's round as it runs: the census, the caravans' list brought up to date, then the day's count on {@code day}. */
	private static Market count(GameTestHelper helper, BlockPos hall, long day) {
		ServerLevel level = helper.getLevel();
		VillageHalls.Census census = VillageHalls.census(level, hall);
		Caravans.round(level, hall, census);
		Market m = Economy.count(level, hall, census, day);
		helper.assertTrue(m != null, "no count");
		return m;
	}

	private static TradeGoods.Good good(GameTestHelper helper, ResourceLocation id) {
		TradeGoods.Good g = TradeGoods.get(id);
		helper.assertTrue(g != null, id + " didn't load");
		return g;
	}

	/** The six test goods load with every field; a broken file is skipped naming its field; one switched off, or for a missing mod, is left out. */
	//$ gametest_batch AREA '"tradeGoodFiles"'
	@GameTest(template = AREA, batch = "tradeGoodFiles")
	public void theTestGoodsLoad(GameTestHelper helper) {
		List<ResourceLocation> ours = TradeGoods.all().stream().map(TradeGoods.Good::id).filter(id -> id.getNamespace().equals("aliveworkplace_test")).toList();
		helper.assertTrue(ours.equals(List.of(GRAIN, FISH, TIMBER, STONE, ARMS, REMEDIES)), "the six test goods in board order: " + ours);
		TradeGoods.Good timber = good(helper, TIMBER);
		helper.assertTrue(timber.bundle() == 16 && timber.basePrice() == 100 && timber.icon() == Items.OAK_LOG && !timber.food(), "timber: " + timber);
		helper.assertTrue(timber.matches(Items.SPRUCE_LOG) && timber.matches(Items.OAK_LOG) && !timber.matches(Items.OAK_PLANKS), "timber's #minecraft:logs");
		helper.assertTrue(timber.makers().contains(ResourceLocation.parse("aliveworkplace:lumberjack"))
			&& timber.users().contains(ResourceLocation.parse("aliveworkplace:builder")), "timber's jobs");
		helper.assertTrue(good(helper, GRAIN).food() && good(helper, GRAIN).icon() == Items.WHEAT, "grain: food, and its first item is its icon");
		helper.assertTrue(good(helper, ARMS).events().equals(List.of(new TradeGoods.Event(TradeGoods.RAID, 2, 3))), "arms' raid event");
		helper.assertTrue(good(helper, TIMBER).name().getString().equals("Test Timber"), "name: " + good(helper, TIMBER).name().getString());

		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "probe");
		String[][] broken = {
			{"{\"items\": [\"minecraft:wheat\"]}", "base_price"},
			{"{\"items\": [], \"base_price\": 100}", "items"},
			{"{\"items\": [\"minecraft:no_such_thing\"], \"base_price\": 100}", "items"},
			{"{\"items\": [\"minecraft:wheat\"], \"base_price\": 100, \"bundle\": 0}", "bundle"},
			{"{\"items\": [\"#minecraft:logs\"], \"base_price\": 100}", "icon"},
			{"{\"items\": [\"minecraft:wheat\"], \"base_price\": 100, \"events\": [{\"event\": \"aliveworkplace:raid\", \"days\": 0}]}", "days"}};
		for (String[] b : broken) {
			String message = "";
			try {
				TradeGoods.read(id, JsonParser.parseString(b[0]));
			} catch (Exception e) {
				message = String.valueOf(e.getMessage());
			}
			helper.assertTrue(message.contains(b[1]), b[0] + " should fail naming " + b[1] + ", got '" + message + "'");
		}
		helper.assertTrue(TradeGoods.read(id, JsonParser.parseString("{\"enabled\": false, \"items\": [\"minecraft:wheat\"], \"base_price\": 100}")) == null,
			"a good switched off loaded");
		helper.assertTrue(TradeGoods.read(id, JsonParser.parseString("{\"items\": [\"minecraft:wheat\"], \"base_price\": 100, \"fabric:load_conditions\": "
			+ "[{\"condition\": \"fabric:all_mods_loaded\", \"values\": [\"no_such_mod_aw\"]}]}")) == null, "a good for a missing mod loaded");
		TradeGoods.Good plain = TradeGoods.read(id, JsonParser.parseString("{\"items\": [\"minecraft:wheat\"], \"base_price\": 100}"));
		helper.assertTrue(plain != null && plain.name().getString().equals("good.aliveworkplace_test.probe"), "default name key: " + plain);

		// a broken file among good ones: skipped, the others load; then the real files again
		Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>(TradeGoods.files(helper.getLevel().getServer().getResourceManager()));
		int before = TradeGoods.all().size();
		Map<ResourceLocation, JsonElement> withBroken = new LinkedHashMap<>(files);
		withBroken.put(id, JsonParser.parseString("{\"items\": 3}"));
		try {
			TradeGoods.load(withBroken);
			helper.assertTrue(TradeGoods.all().size() == before && TradeGoods.get(id) == null, "a broken file changed the list: " + TradeGoods.all().size());
		} finally {
			TradeGoods.load(files);
		}
		helper.succeed();
	}

	/** Three lumberjacks by a hall in a forest: its own round makes the village known for timber, and timber is cheap there. */
	//$ gametest_ticks_batch AREA '100' '"tradeKnownFor"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "tradeKnownFor")
	public void lumberjacksInAForestMakeItKnownForTimber(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		biome(helper, "minecraft:forest");
		BlockPos hall = hall(helper);
		for (int i = 0; i < 3; i++) {
			worker(helper, ModVillagers.LUMBERJACK, new BlockPos(4 + 2 * i, 2, 4));
		}
		helper.runAfterDelay(20, () -> {
			// the hall's own round counted the village
			Market m = Caravans.Data.get(level).market(hall);
			helper.assertTrue(m.priceDay() == Chronicle.day(level), "the hall's round didn't count today: " + m);
			helper.assertTrue(m.knownFor().contains(TIMBER), "not known for timber: " + m.knownFor());
			helper.assertTrue(!m.shortOf().contains(TIMBER), "short of timber too: " + m.shortOf());
			helper.assertTrue(m.knownFor().size() <= Specialties.MOST && m.shortOf().size() <= Specialties.MOST, "more than 3: " + m);
			// known for it: supply 3, demand 0, a target of 0.40 kept at half the base; the first price is the target
			helper.assertTrue(m.prices().get(TIMBER).cents() == Prices.floor(100), "timber's price: " + m.prices().get(TIMBER));
			// the forest makes nothing else here: grain (made in plains) isn't known for
			helper.assertTrue(!m.knownFor().contains(GRAIN), "known for grain without farmers: " + m.knownFor());
			// two lumberjacks without the forest are 4 points; in a plain biome one lumberjack isn't enough
			TradeGoods.Good timber = good(helper, TIMBER);
			ResourceLocation lumberjack = ResourceLocation.parse("aliveworkplace:lumberjack");
			helper.assertTrue(Specialties.score(timber, new Specialties.Village(List.of(lumberjack), null, Map.of(), List.of())).known() == 2,
				"one lumberjack, no biome: 2 points");
			helper.assertTrue(Specialties.score(timber, new Specialties.Village(List.of(lumberjack, lumberjack, lumberjack, lumberjack), null,
				Map.of(Items.OAK_LOG, 32L), List.of())).known() == 7, "four lumberjacks count 6 at most, plus 1 for two bundles");
			helper.succeed();
		});
	}

	/** A desert hall whose builder waits for logs is short of timber, which is dear there; with three lumberjacks too it's still never both. */
	//$ gametest_ticks_batch AREA '100' '"tradeShortOf"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "tradeShortOf")
	public void aDesertHallWhoseBuilderWaitsForLogsIsShortOfTimber(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		biome(helper, "minecraft:desert");
		BlockPos hall = hall(helper);
		waitingBuilder(helper, 20);
		helper.runAfterDelay(20, () -> {
			Market m = Caravans.Data.get(level).market(hall);
			helper.assertTrue(m.priceDay() == Chronicle.day(level), "the hall's round didn't count today: " + m);
			helper.assertTrue(m.shortOf().contains(TIMBER), "not short of timber: " + m.shortOf() + ", wants " + Caravans.Data.get(level).village(hall));
			helper.assertTrue(!m.knownFor().contains(TIMBER), "known for timber in a desert: " + m.knownFor());
			// short of it (3) and two bundles waited for (20 logs): demand 5, supply 0, so twice the base, the most
			helper.assertTrue(m.prices().get(TIMBER).cents() == Prices.ceiling(100), "timber's price: " + m.prices().get(TIMBER));
			// short of fish too (the desert wants it, none in store): the top 3, by points then id
			helper.assertTrue(m.shortOf().contains(FISH), "not short of fish: " + m.shortOf());

			// three lumberjacks: known for 6 points, short of 7 (desert 3, builder 1, waiting 2, store 1): short of only
			for (int i = 0; i < 3; i++) {
				worker(helper, ModVillagers.LUMBERJACK, new BlockPos(4 + 2 * i, 2, 4));
			}
			Market both = count(helper, hall, Chronicle.day(level));
			helper.assertTrue(both.shortOf().contains(TIMBER) && !both.knownFor().contains(TIMBER), "timber: known " + both.knownFor() + ", short " + both.shortOf());
			for (ResourceLocation g : both.knownFor()) {
				helper.assertTrue(!both.shortOf().contains(g), g + " is both");
			}
			// a tie is neither; the higher score wins
			Specialties.Lists tie = Specialties.lists(Map.of(TIMBER, new Specialties.Score(5, 5), STONE, new Specialties.Score(6, 4), FISH, new Specialties.Score(4, 7)));
			helper.assertTrue(tie.knownFor().equals(List.of(STONE)) && tie.shortOf().equals(List.of(FISH)), "tie: " + tie);
			// at most three of each, by points then id
			Map<ResourceLocation, Specialties.Score> many = new LinkedHashMap<>();
			many.put(TIMBER, new Specialties.Score(4, 0));
			many.put(STONE, new Specialties.Score(9, 0));
			many.put(FISH, new Specialties.Score(4, 0));
			many.put(GRAIN, new Specialties.Score(5, 0));
			helper.assertTrue(Specialties.lists(many).knownFor().equals(List.of(STONE, GRAIN, FISH)), "top 3: " + Specialties.lists(many).knownFor());
			helper.succeed();
		});
	}

	/** Prices move a third of the way to their target each dawn, once a day, and never leave half to twice the base. */
	//$ gametest_ticks_batch AREA '40' '"tradePrices"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "tradePrices")
	public void aPriceMovesAThirdOfTheWayEachDawn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		helper.setBlock(new BlockPos(13, 2, 9), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(13, 2, 11), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(13, 2, 11));
		for (int i = 0; i < 6; i++) {
			chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64)); // 6 bundles of stone: supply 5, the most
		}
		helper.runAfterDelay(2, () -> {
			Caravans.Data data = Caravans.Data.get(level);
			long day = 5000;
			count(helper, hall, day - 1);
			// yesterday stone was at twice its base; its target today is half (supply 5, nothing wanted)
			Map<ResourceLocation, Market.Price> seeded = new LinkedHashMap<>(data.market(hall).prices());
			seeded.put(STONE, new Market.Price(200, 200, 0));
			Market m0 = data.market(hall);
			data.setMarket(hall, new Market(m0.knownFor(), m0.shortOf(), seeded, day - 1, m0.demand()));
			int[] expected = {150, 117, 95, 80, 70, 63, 59, 56, 54, 53, 52, 51};
			int last = 200;
			for (int i = 0; i < expected.length; i++) {
				Market m = count(helper, hall, day + i);
				Market.Price p = m.prices().get(STONE);
				helper.assertTrue(p.cents() == expected[i] && p.yesterday() == last, "day " + i + ": " + p + ", expected " + expected[i] + " (yesterday " + last + ")");
				helper.assertTrue(p.cents() >= Prices.floor(100) && p.cents() <= Prices.ceiling(100), "out of range: " + p);
				// a second count the same day doesn't move it again
				helper.assertTrue(count(helper, hall, day + i).prices().get(STONE).equals(p), "moved twice on day " + i);
				last = p.cents();
			}
			// the target itself: 1 + 0.2 a point, between half and twice
			helper.assertTrue(Prices.target(100, 2, 0) == 140 && Prices.target(100, 0, 1) == 80, "target");
			helper.assertTrue(Prices.target(100, 20, 0) == 200 && Prices.target(100, 0, 20) == 50, "target's bounds");
			helper.assertTrue(Prices.move(100, 40, 60) == 50 && Prices.move(100, 300, 200) == 200, "a price outside the bounds comes back in");
			// a good without a price shows its base
			helper.assertTrue(Prices.at(level, helper.absolutePos(new BlockPos(1, 2, 1)), STONE) == 100, "base for a village without prices");
			helper.assertTrue(Prices.at(level, hall, STONE) == 51, "Prices.at: " + Prices.at(level, hall, STONE));
			helper.succeed();
		});
	}

	/** A raid raises arms' and remedies' demand for their days only (from the next dawn), and illness raises remedies' while it lasts. */
	//$ gametest_ticks_batch AREA '40' '"tradeEvents"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "tradeEvents")
	public void aRaidRaisesDemandForItsDaysOnly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		helper.runAfterDelay(2, () -> {
			Caravans.Data data = Caravans.Data.get(level);
			long day = 7000;
			helper.assertTrue(count(helper, hall, day).prices().get(ARMS).cents() == 300, "arms before the raid");
			helper.assertTrue(TradeGoods.event(level, hall, TradeGoods.RAID, day) == 2, "the raid raised arms and remedies");
			helper.assertTrue(TradeGoods.event(level, helper.absolutePos(new BlockPos(1, 2, 1)), TradeGoods.RAID, day) == 0, "a village not on the list");
			List<Integer> arms = new ArrayList<>();
			List<Integer> remedies = new ArrayList<>();
			for (int i = 1; i <= 5; i++) {
				Market m = count(helper, hall, day + i);
				arms.add(m.prices().get(ARMS).cents());
				remedies.add(m.prices().get(REMEDIES).cents());
			}
			// arms: demand 2 for 3 days (target 420), then back toward 300
			helper.assertTrue(arms.equals(List.of(340, 367, 385, 357, 338)), "arms after a raid: " + arms);
			// remedies: demand 1 for 2 days (target 240), then back toward 200
			helper.assertTrue(remedies.equals(List.of(213, 222, 215, 210, 207)), "remedies after a raid: " + remedies);
			helper.assertTrue(data.market(hall).demand().isEmpty(), "spent demand kept: " + data.market(hall).demand());
			// illness: while a villager here is ill, remedies are wanted (demand 2)
			Villager ill = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
			ill.setNoAi(true);
			Sickness.fallIll(level, ill);
			helper.assertTrue(Sickness.isIll(ill), "not ill");
			int before = data.market(hall).prices().get(REMEDIES).cents();
			int during = count(helper, hall, day + 6).prices().get(REMEDIES).cents();
			helper.assertTrue(during == before + Math.round((280 - before) / 3f), "remedies while ill: " + before + " -> " + during);
			ill.discard();
			helper.succeed();
		});
	}

	/** A night raid on the village, beaten through the hall's own raid round, raises arms' and remedies' demand from tomorrow for their days. */
	//$ gametest_ticks_batch AREA '60' '"tradeNightRaid"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "tradeNightRaid")
	public void aBeatenNightRaidRaisesArmsDemand(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		Leftovers.after(helper, io.github.jcondedata.aliveworkplace.guard.VillageRaids::forget);
		helper.runAfterDelay(2, () -> {
			long today = Chronicle.day(level);
			count(helper, hall, today);
			helper.assertTrue(Caravans.Data.get(level).market(hall).demand().isEmpty(), "demand before the raid");
			helper.assertTrue(io.github.jcondedata.aliveworkplace.guard.VillageRaids.start(level, hall, 8, 0) != null, "no raid");
			io.github.jcondedata.aliveworkplace.guard.VillageRaids.raiders(level, hall)
				.forEach(m -> m.hurt(level.damageSources().fellOutOfWorld(), 1000f));
			io.github.jcondedata.aliveworkplace.guard.VillageRaids.tick(level, hall, 8, 0, today, d -> { });
			helper.assertTrue(io.github.jcondedata.aliveworkplace.guard.VillageRaids.active(hall).isEmpty(), "the raid didn't end");
			List<Market.Demand> demand = Caravans.Data.get(level).market(hall).demand();
			helper.assertTrue(demand.contains(new Market.Demand(ARMS, 2, today + 1, today + 3))
				&& demand.contains(new Market.Demand(REMEDIES, 1, today + 1, today + 2)) && demand.size() == 2, "demand after the raid: " + demand);
			helper.succeed();
		});
	}

	/** The new keys survive save and reload; a village entry saved before 33.2 loads with empty lists. */
	//$ gametest_batch AREA '"tradeSave"'
	@GameTest(template = AREA, batch = "tradeSave")
	public void theNewKeysSurviveSaveAndReload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var registries = level.registryAccess();
		BlockPos a = new BlockPos(100, 64, 100);
		BlockPos b = new BlockPos(300, 64, 100);
		Caravans.Data data = new Caravans.Data();
		data.setWants(a, Component.literal("Ashford"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
		data.setWants(b, Component.literal("Bramble"), List.of());
		Map<ResourceLocation, Market.Price> prices = new LinkedHashMap<>();
		prices.put(TIMBER, new Market.Price(80, 90, 2));
		prices.put(STONE, new Market.Price(150, 160, 0));
		Market market = new Market(List.of(TIMBER, STONE), List.of(FISH), prices, 42, List.of(new Market.Demand(ARMS, 2, 40, 43)));
		data.setMarket(a, market);
		CompoundTag saved = data.save(new CompoundTag(), registries);
		Caravans.Data loaded = Caravans.Data.load(saved, registries);
		helper.assertTrue(loaded.market(a).equals(market), "after a reload: " + loaded.market(a) + " instead of " + market);
		helper.assertTrue(loaded.market(b).isEmpty() && loaded.market(b).priceDay() == -1, "B had nothing: " + loaded.market(b));
		helper.assertTrue(loaded.village(a).wants().size() == 1, "the wants went");
		// B's entry has none of the new keys, so 1.6's entries stay as they were
		ListTag villages = saved.getList("villages", 10);
		CompoundTag entryB = null;
		for (int i = 0; i < villages.size(); i++) {
			if (villages.getCompound(i).getLong("hall") == b.asLong()) {
				entryB = villages.getCompound(i);
			}
		}
		helper.assertTrue(entryB != null && !entryB.contains("knownFor") && !entryB.contains("prices") && !entryB.contains("priceDay"), "keys on an empty entry: " + entryB);
		// an entry as 1.6 wrote it
		CompoundTag old = new CompoundTag();
		ListTag list = new ListTag();
		CompoundTag entry = new CompoundTag();
		entry.putLong("hall", a.asLong());
		entry.putString("name", "\"Oldbury\"");
		entry.putLong("lastCaravanDay", 3);
		entry.put("wants", new ListTag());
		entry.put("routes", new ListTag());
		list.add(entry);
		old.put("villages", list);
		old.put("road", new ListTag());
		Caravans.Data fromOld = Caravans.Data.load(old, registries);
		Market empty = fromOld.market(a);
		helper.assertTrue(fromOld.village(a) != null && empty.knownFor().isEmpty() && empty.shortOf().isEmpty() && empty.prices().isEmpty()
			&& empty.priceDay() == -1 && empty.demand().isEmpty(), "a 1.6 entry: " + empty);
		// a hall taken away takes its market with it
		data.remove(a);
		helper.assertTrue(data.market(a).isEmpty(), "a removed village kept its prices");
		helper.succeed();
	}

	/** With villageEconomy off, nothing is worked out or raised, and caravans carry what another village needs as before. */
	//$ gametest_ticks_batch AREA '200' '"tradeOff"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "tradeOff")
	public void withVillageEconomyOffNothingIsWorkedOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		long travel = Caravans.MIN_TRAVEL;
		int radius = VillageHalls.RADIUS;
		WorkplaceConfig.parse("{\"villageEconomy\": false}").apply();
		Caravans.MIN_TRAVEL = 0;
		VillageHalls.RADIUS = 6;
		Leftovers.after(helper, () -> {
			new WorkplaceConfig().apply();
			Caravans.MIN_TRAVEL = travel;
			VillageHalls.RADIUS = radius;
		});
		helper.assertTrue(!Economy.ENABLED, "the switch didn't turn it off");
		// as caravansCarryWhatAnotherVillageNeeds sets them out
		helper.setBlock(new BlockPos(3, 2, 3), ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(5, 2, 3), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(5, 2, 5), Blocks.CHEST);
		Container ours = helper.getBlockEntity(new BlockPos(5, 2, 5));
		ours.setItem(0, new ItemStack(Items.OAK_LOG, 64));
		helper.setBlock(new BlockPos(18, 2, 18), ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(16, 2, 18), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(16, 2, 20), Blocks.CHEST);
		Container theirs = helper.getBlockEntity(new BlockPos(16, 2, 20));
		BlockPos a = helper.absolutePos(new BlockPos(3, 2, 3));
		BlockPos b = helper.absolutePos(new BlockPos(18, 2, 18));
		Leftovers.after(helper, () -> {
			Caravans.Data.get(level).remove(a);
			Caravans.Data.get(level).remove(b);
		});
		for (int i = 0; i < 3; i++) {
			worker(helper, ModVillagers.LUMBERJACK, new BlockPos(2 + i, 2, 7));
		}
		Caravans.Data data = Caravans.Data.get(level);
		helper.runAfterDelay(2, () -> {
			int countings = Economy.countings();
			VillageHalls.Census census = VillageHalls.census(level, a);
			Caravans.round(level, a, census);
			Economy.round(level, a, census);
			helper.assertTrue(Economy.count(level, a, census, Chronicle.day(level)) == null, "a count with the economy off");
			helper.assertTrue(TradeGoods.event(level, a, TradeGoods.RAID) == 0, "an event raised with the economy off");
			helper.assertTrue(Economy.countings() == countings && data.market(a).isEmpty(), "worked out with the economy off: " + data.market(a));
			// the caravan as before: B waits for logs, A has plenty
			data.setWants(b, Component.literal("Bramble"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
			data.setWants(a, Component.literal("Ashford"), List.of());
			helper.assertTrue(data.toggleRoute(a, b), "no route");
			data.sent(a, -1);
			Caravans.round(level, a, null);
			helper.assertTrue(ours.countItem(Items.OAK_LOG) == 32, "loaded: " + ours.countItem(Items.OAK_LOG));
		});
		helper.runAfterDelay(40, () -> {
			Caravans.round(level, b, null);
			helper.assertTrue(theirs.countItem(Items.OAK_LOG) == 32, "arrived: " + theirs.countItem(Items.OAK_LOG));
			helper.assertTrue(data.market(a).isEmpty() && data.market(b).isEmpty(), "the halls' rounds worked prices out with the economy off");
			helper.succeed();
		});
	}
}
