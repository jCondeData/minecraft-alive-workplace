package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.StockOrders;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * ROADMAP 46.5, the Beekeeper makes candles and the Leatherworker leather goods. At her Apiary the Beekeeper makes
 * candles from the hives' honeycomb and string, colored candles and honeycomb blocks for builders, step by step like
 * every crafting job, besides her hives; at his cauldron the Leatherworker makes item frames and glow item frames, leads,
 * leather from rabbit hide and leather armor, and no longer anything of hers. Through a builder's request (or a
 * Storehouse stock order for the leather goods, which no builder asks for): a candle from honeycomb and string; a red
 * candle; a honeycomb block; the missing string named on the builder's line word for word; a Beekeeper saved and loaded
 * mid-job carrying on; her hive harvested alongside; item frames and glow item frames from leather and sticks, and from
 * rabbit hide; leads and a leather helmet; and with either job missing, the line saying whom to hire.
 */
public class ChandlerLeatherGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STATION = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 8);
	private static final BlockPos HIVE = new BlockPos(12, 3, 10);
	private static final BlockPos STOREHOUSE = new BlockPos(12, 2, 12);
	private static final BlockPos STORE_CHEST = new BlockPos(12, 2, 14);

	private record Asker(Villager builder, BuildSite site, BuildPlan plan, BlockPos chest) {
	}

	/** Both lists are their own now: the Beekeeper's candles and honeycomb blocks, the Leatherworker's leather goods only. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theBeekeepersAndLeatherworkersListsAreTheirOwn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CraftJobs.Job.BEEKEEPER.craftsNow(), "the Beekeeper still has a stand-in");
		for (Item item : List.of(Items.CANDLE, Items.RED_CANDLE, Items.BLUE_CANDLE, Items.HONEYCOMB_BLOCK)) {
			helper.assertTrue(CraftJobs.maker(level, item) == CraftJobs.Job.BEEKEEPER, item + " is made by " + CraftJobs.maker(level, item));
			helper.assertFalse(CraftJobs.makes(level, CraftJobs.Job.LEATHERWORKER, item), "the Leatherworker still makes " + item);
		}
		for (Item item : List.of(Items.ITEM_FRAME, Items.GLOW_ITEM_FRAME, Items.LEAD, Items.LEATHER, Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE,
				Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS)) {
			helper.assertTrue(CraftJobs.maker(level, item) == CraftJobs.Job.LEATHERWORKER, item + " is made by " + CraftJobs.maker(level, item));
		}
		// he dyes nothing: no wool, glass or terracotta
		for (Item item : List.of(Items.RED_WOOL, Items.RED_STAINED_GLASS, Items.RED_TERRACOTTA, Items.RED_DYE, Items.RED_CARPET)) {
			helper.assertFalse(CraftJobs.makes(level, CraftJobs.Job.LEATHERWORKER, item), "the Leatherworker still makes " + item);
		}
		helper.succeed();
	}

	/** A candle for a builder from a honeycomb and a string in his chest: the Beekeeper makes it and takes it to him. */
	//$ gametest_ticks_batch AREA '2400' '"chandlerCandle"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "chandlerCandle")
	public void theBeekeeperMakesACandleFromHoneycombAndString(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, one(helper, Blocks.CANDLE, "candle"), new ItemStack(Items.HONEYCOMB, 1), new ItemStack(Items.STRING, 1));
		Villager keeper = beekeeper(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(keeper, 0) >= 1, "the Beekeeper made " + ModAttachments.ITEMS_CRAFTED.getOrElse(keeper, 0));
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.HONEYCOMB) == 0 && chest.countItem(Items.STRING) == 0,
				"the candle wasn't made from the makings: " + chest.countItem(Items.HONEYCOMB) + " honeycomb, " + chest.countItem(Items.STRING) + " string left");
		});
	}

	/** A red candle from honeycomb, string and a red dye: the candle first, then the color. */
	//$ gametest_ticks_batch AREA '3600' '"chandlerRed"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "chandlerRed")
	public void theBeekeeperMakesARedCandleStepByStep(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, one(helper, Blocks.RED_CANDLE, "red"), new ItemStack(Items.HONEYCOMB, 1), new ItemStack(Items.STRING, 1),
			new ItemStack(Items.RED_DYE, 1));
		Villager keeper = beekeeper(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.RED_DYE) == 0 && chest.countItem(Items.HONEYCOMB) == 0,
				"the red candle wasn't made from the makings: " + chest.countItem(Items.RED_DYE) + " dye, " + chest.countItem(Items.HONEYCOMB) + " honeycomb left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(keeper, 0) >= 1, "the Beekeeper made " + ModAttachments.ITEMS_CRAFTED.getOrElse(keeper, 0));
		});
	}

	/** A honeycomb block from four honeycomb. */
	//$ gametest_ticks_batch AREA '2400' '"chandlerBlock"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "chandlerBlock")
	public void theBeekeeperMakesAHoneycombBlock(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, one(helper, Blocks.HONEYCOMB_BLOCK, "block"), new ItemStack(Items.HONEYCOMB, 4));
		Villager keeper = beekeeper(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(keeper, 0) >= 1, "the Beekeeper made " + ModAttachments.ITEMS_CRAFTED.getOrElse(keeper, 0));
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.HONEYCOMB) == 0, chest.countItem(Items.HONEYCOMB) + " honeycomb left");
		});
	}

	/** Honeycomb but no string: she makes nothing, takes nothing, and the builder's line names the string word for word. */
	//$ gametest_ticks_batch AREA '1400' '"chandlerNoString"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "chandlerNoString")
	public void aMissingStringIsNamedOnTheShortageLine(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, one(helper, Blocks.CANDLE, "nostring"), new ItemStack(Items.HONEYCOMB, 1));
		Villager keeper = beekeeper(helper);
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.HONEYCOMB) == 1, "the honeycomb was taken");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(keeper, 0) == 0, "the Beekeeper made " + ModAttachments.ITEMS_CRAFTED.getOrElse(keeper, 0));
			String text = line(helper, a);
			helper.assertTrue("Can't be made yet: short of 1 String to make 1 Candle".equals(text), "the builder's line: " + text);
			helper.succeed();
		});
	}

	/** Saved and loaded just as she starts making the candle, the Beekeeper carries on with the makings in her bag. */
	//$ gametest_ticks_batch AREA '3600' '"chandlerReload"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "chandlerReload")
	public void aBeekeeperLoadedMidJobFinishesTheCandle(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, one(helper, Blocks.CANDLE, "reload"), new ItemStack(Items.HONEYCOMB, 1), new ItemStack(Items.STRING, 1));
		AtomicReference<Villager> keeper = new AtomicReference<>(beekeeper(helper));
		AtomicReference<CrafterJob> saved = new AtomicReference<>();
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(keeper.get());
			if (saved.get() != null || job == null || job.phase() != CrafterJob.Phase.CRAFTING) {
				return;
			}
			saved.set(job);
			Villager copy = reload(helper.getLevel(), keeper.get());
			helper.assertTrue(!ModAttachments.BUILDER_BAG.getOrCreate(copy).isEmpty(), "the makings should be in the bag across the reload");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(copy) != null, "the job wasn't saved on the Beekeeper");
			keeper.set(copy);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(saved.get() != null, "the Beekeeper never started making the candle");
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(keeper.get()) == null, "the finished job is still saved on the Beekeeper");
		});
	}

	/**
	 * Her hives are still kept: with a full hive over a campfire and shears in the Apiary's chest, and a builder waiting for
	 * a candle, she does both (the two take turns, neither stalls the other).
	 */
	//$ gametest_ticks_batch AREA '4000' '"chandlerHives"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "chandlerHives")
	public void theBeekeeperStillHarvestsHerHives(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, one(helper, Blocks.CANDLE, "hives"), new ItemStack(Items.HONEYCOMB, 1), new ItemStack(Items.STRING, 1));
		Villager keeper = beekeeper(helper, new ItemStack(Items.SHEARS));
		helper.setBlock(HIVE.below(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		helper.setBlock(HIVE, Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 5));
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(keeper, 0) >= 1, "the Beekeeper made " + ModAttachments.ITEMS_CRAFTED.getOrElse(keeper, 0));
			helper.assertTrue(ModAttachments.HIVES_HARVESTED.getOrElse(keeper, 0) >= 1, "hives harvested: " + ModAttachments.HIVES_HARVESTED.getOrElse(keeper, 0));
			Container own = helper.getBlockEntity(STATION.east());
			helper.assertTrue(own.countItem(Items.HONEYCOMB) == 3, "honeycomb in her chest: " + own.countItem(Items.HONEYCOMB));
		});
	}

	/**
	 * No Beekeeper, only the Leatherworker who made candles before 46.5: he leaves the candle alone (the makings stay in
	 * the chest) and the builder's line says "Hire a Beekeeper to make Candles".
	 */
	//$ gametest_ticks_batch AREA '1400' '"chandlerAbsent"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "chandlerAbsent")
	public void withNoBeekeeperTheLineSaysHireOne(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, one(helper, Blocks.CANDLE, "absent"), new ItemStack(Items.HONEYCOMB, 1), new ItemStack(Items.STRING, 1));
		Villager leatherworker = leatherworker(helper);
		helper.runAfterDelay(200, () -> line(helper, a));
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.HONEYCOMB) == 1 && chest.countItem(Items.STRING) == 1, "the Leatherworker took the makings");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(leatherworker, 0) == 0,
				"the Leatherworker made " + ModAttachments.ITEMS_CRAFTED.getOrElse(leatherworker, 0));
			String text = line(helper, a);
			helper.assertTrue("Hire a Beekeeper to make Candles".equals(text), "the builder's line: " + text);
			helper.succeed();
		});
	}

	/**
	 * A Storehouse's stock order for item frames, from leather and sticks in its store: the Leatherworker makes them and
	 * takes them there. Item frames go through the store because a builder never asks for a blueprint's frames (they go
	 * up last, from the chests).
	 */
	//$ gametest_ticks_batch AREA '4000' '"leatherFrame"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "leatherFrame")
	public void theLeatherworkerMakesAnItemFrameFromLeatherAndSticks(GameTestHelper helper) {
		prepare(helper);
		Container store = store(helper, new ItemStack(Items.LEATHER, 4), new ItemStack(Items.STICK, 64));
		StockOrders.cycle(helper.getLevel(), helper.absolutePos(STOREHOUSE), Items.ITEM_FRAME);
		Villager leatherworker = leatherworker(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(store.countItem(Items.ITEM_FRAME) >= 1, store.countItem(Items.ITEM_FRAME) + " item frames in the store");
			helper.assertTrue(store.countItem(Items.LEATHER) < 4, "the frames weren't made from the store's leather: " + store.countItem(Items.LEATHER) + " left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(leatherworker, 0) >= 1,
				"the Leatherworker made " + ModAttachments.ITEMS_CRAFTED.getOrElse(leatherworker, 0));
		});
	}

	/** A glow item frame from leather, sticks and a glow ink sac: the item frame first, then the glow. */
	//$ gametest_ticks_batch AREA '4000' '"leatherGlow"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "leatherGlow")
	public void theLeatherworkerMakesAGlowItemFrameStepByStep(GameTestHelper helper) {
		prepare(helper);
		Container store = store(helper, new ItemStack(Items.LEATHER, 2), new ItemStack(Items.STICK, 32), new ItemStack(Items.GLOW_INK_SAC, 2));
		StockOrders.cycle(helper.getLevel(), helper.absolutePos(STOREHOUSE), Items.GLOW_ITEM_FRAME);
		Villager leatherworker = leatherworker(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(store.countItem(Items.GLOW_ITEM_FRAME) >= 1, store.countItem(Items.GLOW_ITEM_FRAME) + " glow item frames in the store");
			helper.assertTrue(store.countItem(Items.GLOW_INK_SAC) < 2, store.countItem(Items.GLOW_INK_SAC) + " glow ink sacs left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(leatherworker, 0) >= 1,
				"the Leatherworker made " + ModAttachments.ITEMS_CRAFTED.getOrElse(leatherworker, 0));
		});
	}

	/** Only rabbit hide in the store: the Leatherworker makes the leather from four hides first, then the item frame. */
	//$ gametest_ticks_batch AREA '4000' '"leatherHide"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "leatherHide")
	public void theLeatherworkerMakesLeatherFromRabbitHideForAFrame(GameTestHelper helper) {
		prepare(helper);
		Container store = store(helper, new ItemStack(Items.RABBIT_HIDE, 8), new ItemStack(Items.STICK, 64));
		StockOrders.cycle(helper.getLevel(), helper.absolutePos(STOREHOUSE), Items.ITEM_FRAME);
		Villager leatherworker = leatherworker(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(store.countItem(Items.ITEM_FRAME) >= 1, store.countItem(Items.ITEM_FRAME) + " item frames in the store");
			helper.assertTrue(store.countItem(Items.RABBIT_HIDE) < 8, store.countItem(Items.RABBIT_HIDE) + " rabbit hide left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(leatherworker, 0) >= 1,
				"the Leatherworker made " + ModAttachments.ITEMS_CRAFTED.getOrElse(leatherworker, 0));
		});
	}

	/** A Storehouse's stock orders for leads and leather helmets, filled by the Leatherworker from its store. */
	//$ gametest_ticks_batch AREA '4000' '"leatherStock"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "leatherStock")
	public void leadsAndLeatherHelmetsForAStockOrder(GameTestHelper helper) {
		prepare(helper);
		Container store = store(helper, new ItemStack(Items.STRING, 16), new ItemStack(Items.SLIME_BALL, 4), new ItemStack(Items.LEATHER, 16));
		StockOrders.cycle(helper.getLevel(), helper.absolutePos(STOREHOUSE), Items.LEAD);
		StockOrders.cycle(helper.getLevel(), helper.absolutePos(STOREHOUSE), Items.LEATHER_HELMET);
		Villager leatherworker = leatherworker(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(store.countItem(Items.LEAD) >= 1, store.countItem(Items.LEAD) + " leads in the store");
			helper.assertTrue(store.countItem(Items.LEATHER_HELMET) >= 1, store.countItem(Items.LEATHER_HELMET) + " leather helmets in the store");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(leatherworker, 0) >= 2,
				"the Leatherworker made " + ModAttachments.ITEMS_CRAFTED.getOrElse(leatherworker, 0));
		});
	}

	/**
	 * No Leatherworker, only a Beekeeper: she leaves a stock order for item frames alone (the store keeps its leather and
	 * sticks), and a builder short of an item frame reads "Hire a Leatherworker to make Item Frames".
	 */
	//$ gametest_ticks_batch AREA '1400' '"leatherAbsent"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "leatherAbsent")
	public void withNoLeatherworkerTheLineSaysHireOne(GameTestHelper helper) {
		prepare(helper);
		Container store = store(helper, new ItemStack(Items.LEATHER, 4), new ItemStack(Items.STICK, 64));
		StockOrders.cycle(helper.getLevel(), helper.absolutePos(STOREHOUSE), Items.ITEM_FRAME);
		Villager keeper = beekeeper(helper);
		java.util.UUID site = java.util.UUID.randomUUID();
		Map<Item, Integer> missing = Map.of(Items.ITEM_FRAME, 1);
		helper.runAfterDelay(200, () -> JobShortages.line(helper.getLevel(), site, missing, helper.absolutePos(BENCH)));
		helper.runAfterDelay(1100, () -> {
			helper.assertTrue(store.countItem(Items.LEATHER) == 4 && store.countItem(Items.STICK) == 64 && store.countItem(Items.ITEM_FRAME) == 0,
				"the Beekeeper used the store's leather");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(keeper, 0) == 0, "the Beekeeper made " + ModAttachments.ITEMS_CRAFTED.getOrElse(keeper, 0));
			Component line = JobShortages.line(helper.getLevel(), site, missing, helper.absolutePos(BENCH));
			JobShortages.forget(site);
			String text = line == null ? null : line.getString();
			helper.assertTrue("Hire a Leatherworker to make Item Frames".equals(text), "the builder's line: " + text);
			helper.succeed();
		});
	}

	// ---- helpers ----

	private static void prepare(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
	}

	private static String line(GameTestHelper helper, Asker a) {
		Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing(), helper.absolutePos(BENCH));
		return line == null ? null : line.getString();
	}

	/** A blueprint of one {@code block}, standing on the floor. */
	private static ResourceLocation one(GameTestHelper helper, Block block, String what) {
		helper.setBlock(ROW, block);
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"chandler_" + what + "_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(ROW).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(ROW, Blocks.AIR);
		return id;
	}

	/** A Storehouse and its chest holding {@code items}, its stock orders reaching this test's workers. */
	private static Container store(GameTestHelper helper, ItemStack... items) {
		Leftovers.halls(helper);
		int range = StockOrders.RANGE;
		StockOrders.RANGE = 16;
		Leftovers.after(helper, () -> StockOrders.RANGE = range);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		Container store = helper.getBlockEntity(STORE_CHEST);
		for (int i = 0; i < items.length; i++) {
			store.setItem(i, items[i].copy());
		}
		return store;
	}

	private static Asker asker(GameTestHelper helper, ResourceLocation id, ItemStack... items) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container container = helper.getBlockEntity(CHEST);
		for (int i = 0; i < items.length; i++) {
			container.setItem(i, items[i].copy());
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ROW),
			Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + id);
		}
		return new Asker(builder, site, plan, CHEST);
	}

	/** A Beekeeper at her Apiary, with a chest by it holding {@code own}. */
	private static Villager beekeeper(GameTestHelper helper, ItemStack... own) {
		helper.setBlock(STATION, ModBlocks.APIARY);
		helper.setBlock(STATION.east(), Blocks.CHEST);
		Container chest = helper.getBlockEntity(STATION.east());
		for (int i = 0; i < own.length; i++) {
			chest.setItem(i, own[i].copy());
		}
		Villager keeper = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), keeper, helper.absolutePos(STATION), ModVillagers.APIARY_POI, ModVillagers.BEEKEEPER);
		return keeper;
	}

	/** A Leatherworker at his cauldron, with a chest by it. */
	private static Villager leatherworker(GameTestHelper helper) {
		helper.setBlock(STATION, Blocks.CAULDRON);
		helper.setBlock(STATION.east(), Blocks.CHEST);
		Villager v = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(STATION), PoiTypes.LEATHERWORKER, VillagerProfession.LEATHERWORKER);
		return v;
	}

	private static void assertBuilt(GameTestHelper helper, Asker a) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(BuildSiteManager.get(level).get(a.site().id()) == null,
			"still building: status=" + a.site().status() + " missing=" + a.site().missing());
		List<BlockPos> unfinished = a.plan().unfinished(level);
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build");
	}

	private static Villager reload(ServerLevel level, Villager villager) {
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		villager.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		if (copy == null) {
			throw new GameTestAssertException("no villager made");
		}
		copy.load(tag);
		level.addFreshEntity(copy);
		io.github.jcondedata.aliveworkplace.work.Village.forget(copy);
		return copy;
	}
}
