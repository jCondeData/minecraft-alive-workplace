package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trade.Economy;
import io.github.jcondedata.aliveworkplace.trade.Market;
import io.github.jcondedata.aliveworkplace.trade.Specialties;
import io.github.jcondedata.aliveworkplace.trade.TradeGoods;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;

/**
 * ROADMAP 33.3, the 28 trade goods: the 25 that ship for every world load with their names, icons, items, bundles and
 * prices as the roadmap gives them (the three Cobblemon goods load only with Cobblemon: {@code TradeGoodsCompatTests}),
 * every job and biome they name exists, a data pack can switch one off, and five kinds of village come out known for and
 * short of what they should: a plains farm village (grain and bread), a taiga lumber village (timber), a mountain mining
 * village (stone, coal and iron), a coastal fishing village (fish) and a desert village (short of timber and fish).
 */
public class TradeGoodsDataGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(9, 2, 9);

	/** id, English name, bundle, base price (cents), in board order. */
	private static final Object[][] SHIPPED = {
		{"grain", "Grain", 20, 100}, {"bread", "Bread", 6, 100}, {"roots", "Roots", 22, 100}, {"fish", "Fish", 6, 100},
		{"meat", "Meat", 5, 100}, {"fine_meals", "Fine Meals", 4, 100}, {"fruit", "Fruit", 12, 100}, {"honey", "Honey", 4, 100},
		{"bone_meal", "Bone Meal", 16, 100},
		{"timber", "Timber", 16, 100}, {"stone", "Stone", 64, 100}, {"glass", "Glass", 8, 100}, {"bricks_and_clay", "Bricks and Clay", 10, 100},
		{"coal", "Coal", 15, 100}, {"iron", "Iron", 4, 100}, {"gold", "Gold", 3, 100},
		{"wool", "Wool", 18, 100}, {"leather", "Leather", 6, 100}, {"dyes_and_flowers", "Dyes and Flowers", 12, 100}, {"paper", "Paper", 24, 100},
		{"tools", "Tools", 1, 200}, {"arms_and_armour", "Arms and Armour", 1, 300}, {"arrows", "Arrows", 16, 100}, {"remedies", "Remedies", 1, 200},
		{"nether_goods", "Nether Goods", 8, 100}};
	static final List<String> COBBLEMON_GOODS = List.of("apricorns", "berries", "poke_balls");

	static ResourceLocation id(String good) {
		return ResourceLocation.fromNamespaceAndPath("aliveworkplace", good);
	}

	/** Only the goods from {@code namespace}'s files for this test (the test goods and ours would crowd each other's top 3); all of them again after. */
	static void goodsFrom(GameTestHelper helper, String namespace) {
		Map<ResourceLocation, JsonElement> files = TradeGoods.files(helper.getLevel().getServer().getResourceManager());
		Map<ResourceLocation, JsonElement> only = new LinkedHashMap<>();
		files.forEach((k, v) -> {
			if (k.getNamespace().equals(namespace)) {
				only.put(k, v);
			}
		});
		TradeGoods.load(only);
		Leftovers.after(helper, () -> TradeGoods.load(files));
	}

	private static TradeGoods.Good good(GameTestHelper helper, String good) {
		TradeGoods.Good g = TradeGoods.get(id(good));
		helper.assertTrue(g != null, good + " didn't load: " + TradeGoods.all().stream().map(TradeGoods.Good::id).toList());
		return g;
	}

	/** Every item of the registry that is one of {@code good}'s items. */
	private static List<Item> items(TradeGoods.Good good) {
		List<Item> out = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			if (good.matches(item)) {
				out.add(item);
			}
		}
		return out;
	}

	/** The 25 goods for every world load, in board order, with their names, prices, icons among their items, and real jobs and biomes. */
	//$ gametest_batch AREA '"tradeShippedGoods"'
	@GameTest(template = AREA, batch = "tradeShippedGoods")
	public void everyShippedGoodLoadsAndIsValid(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<ResourceLocation> ours = TradeGoods.all().stream().map(TradeGoods.Good::id).filter(i -> i.getNamespace().equals("aliveworkplace")).toList();
		List<ResourceLocation> expected = new ArrayList<>();
		for (Object[] row : SHIPPED) {
			expected.add(id((String) row[0]));
		}
		helper.assertTrue(ours.equals(expected), "the 25 goods in board order: " + ours);
		// the 28 files are there; the three for Cobblemon don't load without it
		Map<ResourceLocation, JsonElement> files = TradeGoods.files(level.getServer().getResourceManager());
		long shipped = files.keySet().stream().filter(i -> i.getNamespace().equals("aliveworkplace")).count();
		helper.assertTrue(shipped == 28, "28 good files, found " + shipped);
		for (String c : COBBLEMON_GOODS) {
			helper.assertTrue(files.containsKey(id(c)), "no file for " + c);
			helper.assertTrue(TradeGoods.get(id(c)) == null, c + " loaded without Cobblemon");
		}

		Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
		int lastOrder = Integer.MIN_VALUE;
		for (Object[] row : SHIPPED) {
			TradeGoods.Good g = good(helper, (String) row[0]);
			helper.assertTrue(g.name().getString().equals(row[1]), row[0] + "'s name: '" + g.name().getString() + "'");
			helper.assertTrue(g.bundle() == (int) row[2] && g.basePrice() == (int) row[3], row[0] + ": " + g.bundle() + " for " + g.basePrice());
			helper.assertTrue(g.icon() != Items.AIR && g.matches(g.icon()), row[0] + "'s icon isn't one of its items: " + g.icon());
			List<Item> items = items(g);
			helper.assertTrue(!items.isEmpty(), row[0] + " has no items");
			helper.assertTrue(g.order() > lastOrder, row[0] + " is out of board order");
			lastOrder = g.order();
			helper.assertTrue(!g.makers().isEmpty() && !g.users().isEmpty(), row[0] + " has no makers or no users");
			for (ResourceLocation job : g.makers()) {
				helper.assertTrue(BuiltInRegistries.VILLAGER_PROFESSION.containsKey(job), row[0] + ": no job " + job);
			}
			for (ResourceLocation job : g.users()) {
				helper.assertTrue(BuiltInRegistries.VILLAGER_PROFESSION.containsKey(job), row[0] + ": no job " + job);
			}
			for (TradeGoods.Matcher<Biome> m : List.of(g.makingBiomes(), g.wantingBiomes())) {
				for (ResourceLocation b : m.ids()) {
					helper.assertTrue(biomes.containsKey(b), row[0] + ": no biome " + b);
				}
				for (TagKey<Biome> t : m.tags()) {
					helper.assertTrue(biomes.getTag(t).map(s -> s.size() > 0).orElse(false), row[0] + ": empty or missing biome tag " + t.location());
				}
			}
		}
		// what each several-item good takes, as the roadmap lists it
		helper.assertTrue(items(good(helper, "roots")).containsAll(List.of(Items.POTATO, Items.CARROT, Items.BEETROOT)) && items(good(helper, "roots")).size() == 3,
			"roots: " + items(good(helper, "roots")));
		helper.assertTrue(items(good(helper, "fine_meals")).containsAll(List.of(Items.PUMPKIN_PIE, Items.CAKE, Items.RABBIT_STEW, Items.MUSHROOM_STEW,
			Items.BEETROOT_SOUP)) && items(good(helper, "fine_meals")).size() == 5, "fine meals: " + items(good(helper, "fine_meals")));
		helper.assertTrue(good(helper, "timber").matches(Items.SPRUCE_LOG) && good(helper, "timber").matches(Items.MANGROVE_LOG)
			&& !good(helper, "timber").matches(Items.OAK_PLANKS), "timber: any logs");
		helper.assertTrue(good(helper, "coal").matches(Items.CHARCOAL) && good(helper, "wool").matches(Items.LIME_WOOL), "coal and wool");
		helper.assertTrue(good(helper, "bricks_and_clay").matches(Items.CLAY_BALL) && good(helper, "bricks_and_clay").matches(Items.BRICKS)
			&& good(helper, "bricks_and_clay").matches(Items.CYAN_TERRACOTTA), "bricks and clay");
		helper.assertTrue(good(helper, "dyes_and_flowers").matches(Items.RED_DYE) && good(helper, "dyes_and_flowers").matches(Items.CORNFLOWER)
			&& good(helper, "dyes_and_flowers").matches(Items.PEONY) && !good(helper, "dyes_and_flowers").matches(Items.CHERRY_LEAVES), "dyes and flowers");
		helper.assertTrue(items(good(helper, "arms_and_armour")).size() == 5 && good(helper, "arms_and_armour").matches(Items.IRON_BOOTS)
			&& !good(helper, "arms_and_armour").matches(Items.DIAMOND_SWORD), "arms and armour");
		helper.assertTrue(items(good(helper, "tools")).size() == 4 && !good(helper, "tools").matches(Items.IRON_SWORD), "tools");
		helper.assertTrue(items(good(helper, "nether_goods")).size() == 4 && good(helper, "nether_goods").matches(Items.BLAZE_ROD), "nether goods");
		// the food goods count toward a village short of meals; the rest don't
		for (Object[] row : SHIPPED) {
			boolean food = List.of("grain", "bread", "roots", "fish", "meat", "fine_meals", "fruit", "honey").contains(row[0]);
			helper.assertTrue(good(helper, (String) row[0]).food() == food, row[0] + " food: " + !food);
		}
		// raids raise arms, arrows and remedies for 3 days; illness raises remedies
		helper.assertTrue(good(helper, "arms_and_armour").events().equals(List.of(new TradeGoods.Event(TradeGoods.RAID, 2, 3)))
			&& good(helper, "arrows").events().equals(List.of(new TradeGoods.Event(TradeGoods.RAID, 2, 3))), "arms' and arrows' raid");
		helper.assertTrue(good(helper, "remedies").events().stream().anyMatch(e -> e.event().equals(TradeGoods.RAID))
			&& good(helper, "remedies").events().stream().anyMatch(e -> e.event().equals(TradeGoods.ILLNESS)), "remedies' events");
		helper.assertTrue(TradeGoods.all().stream().filter(g -> g.id().getNamespace().equals("aliveworkplace") && !g.events().isEmpty()).count() == 3,
			"only arms, arrows and remedies have events");
		helper.succeed();
	}

	/** A data pack switches one of ours off by path, or replaces it; the others stay; /reload brings ours back. */
	//$ gametest_batch AREA '"tradeShippedOff"'
	@GameTest(template = AREA, batch = "tradeShippedOff")
	public void aDataPackSwitchesAGoodOffOrChangesIt(GameTestHelper helper) {
		Map<ResourceLocation, JsonElement> files = TradeGoods.files(helper.getLevel().getServer().getResourceManager());
		int before = TradeGoods.all().size();
		Map<ResourceLocation, JsonElement> pack = new LinkedHashMap<>(files);
		pack.put(id("timber"), JsonParser.parseString("{\"enabled\": false}"));
		pack.put(id("stone"), JsonParser.parseString("{\"name\": {\"translate\": \"good.aliveworkplace.stone\"}, \"items\": [\"minecraft:cobblestone\"], "
			+ "\"bundle\": 32, \"base_price\": 100}"));
		try {
			TradeGoods.load(pack);
			helper.assertTrue(TradeGoods.get(id("timber")) == null && TradeGoods.all().size() == before - 1, "timber switched off: " + TradeGoods.all().size());
			TradeGoods.Good stone = TradeGoods.get(id("stone"));
			helper.assertTrue(stone != null && stone.bundle() == 32 && !stone.matches(Items.STONE_BRICKS) && stone.name().getString().equals("Stone"),
				"the pack's stone: " + stone);
		} finally {
			TradeGoods.load(files);
		}
		helper.assertTrue(TradeGoods.all().size() == before && good(helper, "timber").bundle() == 16 && good(helper, "stone").bundle() == 64, "ours back");
		helper.succeed();
	}

	/** A worker of {@code job} with a job site by the hall (no AI: nothing changes their job while the test runs). */
	private static void worker(GameTestHelper helper, VillagerProfession job, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job));
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(at.below())));
	}

	private static VillagerProfession job(String id) {
		return Lookup.value(BuiltInRegistries.VILLAGER_PROFESSION, ResourceLocation.parse(id));
	}

	/**
	 * A village with only our goods: a hall in {@code biome}, {@code jobs} as workers in a row, a Storehouse whose chest holds
	 * {@code stock}; then the hall's round as it runs (census, caravans' list, the day's count).
	 */
	private static Market village(GameTestHelper helper, String biome, List<String> jobs, List<ItemStack> stock) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		goodsFrom(helper, "aliveworkplace");
		BlockPos hallAt = helper.absolutePos(HALL);
		String old = level.getBiome(hallAt).unwrapKey().map(k -> k.location().toString()).orElse("minecraft:plains");
		fill(level, hallAt, biome);
		Leftovers.after(helper, () -> fill(level, hallAt, old));
		helper.assertTrue(level.getBiome(hallAt).is(ResourceLocation.parse(biome)), "the hall isn't in " + biome);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 8;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Leftovers.after(helper, () -> Caravans.Data.get(level).remove(hallAt));
		helper.setBlock(new BlockPos(13, 2, 9), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(13, 2, 11), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(13, 2, 11));
		for (int i = 0; i < stock.size(); i++) {
			chest.setItem(i, stock.get(i).copy());
		}
		for (int i = 0; i < jobs.size(); i++) {
			worker(helper, job(jobs.get(i)), new BlockPos(3 + 2 * (i % 5), 2, 3 + 2 * (i / 5)));
		}
		VillageHalls.Census census = VillageHalls.census(level, hallAt);
		helper.assertTrue(census.workers().size() == jobs.size(), "the census counted " + census.workers().size() + " workers, not " + jobs.size());
		Caravans.round(level, hallAt, census);
		Market m = Economy.count(level, hallAt, census, Chronicle.day(level));
		helper.assertTrue(m != null, "no count");
		for (ResourceLocation g : m.knownFor()) {
			helper.assertTrue(!m.shortOf().contains(g), g + " is both known for and short of");
		}
		helper.assertTrue(m.knownFor().size() <= Specialties.MOST && m.shortOf().size() <= Specialties.MOST, "more than 3: " + m);
		return m;
	}

	private static void fill(ServerLevel level, BlockPos at, String biome) {
		String cmd = "fillbiome " + (at.getX() - 6) + " " + (at.getY() - 4) + " " + (at.getZ() - 6) + " " + (at.getX() + 6) + " " + (at.getY() + 4)
			+ " " + (at.getZ() + 6) + " " + biome;
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(), cmd);
	}

	private static ItemStack stack(Item item, int count) {
		return new ItemStack(item, count);
	}

	/** Three farmers in the plains with wheat and bread in store: known for grain and bread, short of neither. */
	//$ gametest_ticks_batch AREA '40' '"tradeFarmVillage"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "tradeFarmVillage")
	public void aPlainsFarmVillageIsKnownForGrainAndBread(GameTestHelper helper) {
		helper.runAfterDelay(1, () -> {
			Market m = village(helper, "minecraft:plains", List.of("minecraft:farmer", "minecraft:farmer", "minecraft:farmer"),
				List.of(stack(Items.WHEAT, 64), stack(Items.BREAD, 20)));
			helper.assertTrue(m.knownFor().contains(id("grain")) && m.knownFor().contains(id("bread")), "known for " + m.knownFor());
			helper.assertTrue(!m.shortOf().contains(id("grain")) && !m.shortOf().contains(id("bread")), "short of " + m.shortOf());
			// plenty of grain here: cheaper than its base
			helper.assertTrue(m.prices().get(id("grain")).cents() < 100, "grain's price: " + m.prices().get(id("grain")));
			helper.succeed();
		});
	}

	/** Three lumberjacks in a taiga with logs in store: known for timber; short of meat (the taiga lacks it) and of the tools they use. */
	//$ gametest_ticks_batch AREA '40' '"tradeLumberVillage"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "tradeLumberVillage")
	public void aTaigaLumberVillageIsKnownForTimber(GameTestHelper helper) {
		helper.runAfterDelay(1, () -> {
			Market m = village(helper, "minecraft:taiga", List.of("aliveworkplace:lumberjack", "aliveworkplace:lumberjack", "aliveworkplace:lumberjack"),
				List.of(stack(Items.SPRUCE_LOG, 64)));
			helper.assertTrue(m.knownFor().contains(id("timber")), "known for " + m.knownFor());
			helper.assertTrue(!m.shortOf().contains(id("timber")), "short of timber: " + m.shortOf());
			helper.assertTrue(m.shortOf().contains(id("meat")) && m.shortOf().contains(id("tools")), "short of " + m.shortOf());
			helper.assertTrue(m.prices().get(id("timber")).cents() < 100, "timber's price: " + m.prices().get(id("timber")));
			helper.succeed();
		});
	}

	/** Three miners in the stony peaks with stone, coal and iron in store: known for exactly stone, coal and iron. */
	//$ gametest_ticks_batch AREA '40' '"tradeMiningVillage"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "tradeMiningVillage")
	public void aMountainMiningVillageIsKnownForStoneCoalAndIron(GameTestHelper helper) {
		helper.runAfterDelay(1, () -> {
			Market m = village(helper, "minecraft:stony_peaks", List.of("aliveworkplace:miner", "aliveworkplace:miner", "aliveworkplace:miner"),
				List.of(stack(Items.COBBLESTONE, 64), stack(Items.STONE, 64), stack(Items.COAL, 40), stack(Items.RAW_IRON, 12)));
			helper.assertTrue(m.knownFor().size() == 3 && m.knownFor().containsAll(List.of(id("stone"), id("coal"), id("iron"))), "known for " + m.knownFor());
			// gold: the miners make it (6 points) but not here, so it doesn't push one of the three out
			helper.assertTrue(!m.knownFor().contains(id("gold")), "known for gold: " + m.knownFor());
			helper.succeed();
		});
	}

	/** Two fishermen on a beach with cooked fish in store: known for fish. */
	//$ gametest_ticks_batch AREA '40' '"tradeFishingVillage"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "tradeFishingVillage")
	public void aCoastalFishingVillageIsKnownForFish(GameTestHelper helper) {
		helper.runAfterDelay(1, () -> {
			Market m = village(helper, "minecraft:beach", List.of("minecraft:fisherman", "minecraft:fisherman"),
				List.of(stack(Items.COOKED_COD, 10), stack(Items.COOKED_SALMON, 8)));
			helper.assertTrue(m.knownFor().contains(id("fish")), "known for " + m.knownFor());
			helper.assertTrue(!m.shortOf().contains(id("fish")), "short of fish: " + m.shortOf());
			// a beach is short of timber and stone (none in store)
			helper.assertTrue(m.shortOf().contains(id("timber")), "short of " + m.shortOf());
			helper.succeed();
		});
	}

	/** A desert village with a builder, a carpenter and an innkeeper and an empty store: short of timber and fish, known for neither. */
	//$ gametest_ticks_batch AREA '40' '"tradeDesertVillage"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "tradeDesertVillage")
	public void aDesertVillageIsShortOfTimberAndFish(GameTestHelper helper) {
		helper.runAfterDelay(1, () -> {
			Market m = village(helper, "minecraft:desert", List.of("aliveworkplace:builder", "aliveworkplace:carpenter", "aliveworkplace:innkeeper"),
				List.of());
			helper.assertTrue(m.shortOf().contains(id("timber")) && m.shortOf().contains(id("fish")), "short of " + m.shortOf());
			helper.assertTrue(!m.knownFor().contains(id("timber")) && !m.knownFor().contains(id("fish")), "known for " + m.knownFor());
			// short of it and none in store: dearer than its base
			helper.assertTrue(m.prices().get(id("timber")).cents() > 100 && m.prices().get(id("fish")).cents() > 100, "prices: " + m.prices());
			// the same village scored without the hall: the desert alone wants timber and fish (3 + 1 for an empty store)
			Holder<Biome> desert = helper.getLevel().getBiome(helper.absolutePos(HALL));
			Specialties.Score timber = Specialties.score(good(helper, "timber"), new Specialties.Village(List.of(), desert, Map.of(), List.of()));
			helper.assertTrue(timber.shortOf() == 4 && timber.known() == 0, "timber in an empty desert: " + timber);
			helper.succeed();
		});
	}
}
