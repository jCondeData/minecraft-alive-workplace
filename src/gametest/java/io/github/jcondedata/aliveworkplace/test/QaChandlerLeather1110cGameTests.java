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
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * QA (qa-lane-c-1110) of ROADMAP 46.5, the Beekeeper makes candles and the Leatherworker leather goods, written from the
 * spec: candles (and dyed ones) and honeycomb blocks from the hives' wax, made by the Beekeeper at her Apiary for
 * builders and stock orders; item frames, leads and leather armor from leather, made by the Leatherworker and by no one
 * else's job; nothing taken when something is short, and the lines a player reads ("Can't be made yet: ...", "Hire a
 * Beekeeper to make ...", the head lines, the Storehouse's "Made by") compared word for word with en_us.json.
 */
public class QaChandlerLeather1110cGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STATION = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 8);
	private static final BlockPos STOREHOUSE = new BlockPos(12, 2, 12);
	private static final BlockPos STORE_CHEST = new BlockPos(12, 2, 14);

	private record Asker(Villager builder, BuildSite site, BuildPlan plan, BlockPos chest) {
	}

	/** A blue candle from a plain candle and a blue dye already in the builder's chest: only the color step is needed. */
	//$ gametest_ticks_batch AREA '2400' '"qaChandlerBlue"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaChandlerBlue")
	public void aBlueCandleFromAPlainCandleAndADye(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.BLUE_CANDLE.defaultBlockState(), "blue"), new ItemStack(Items.CANDLE, 1),
			new ItemStack(Items.BLUE_DYE, 1));
		Villager keeper = beekeeper(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(keeper, 0) >= 1, "the Beekeeper made " + ModAttachments.ITEMS_CRAFTED.getOrElse(keeper, 0));
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.CANDLE) == 0 && chest.countItem(Items.BLUE_DYE) == 0,
				"the blue candle wasn't made from the makings: " + chest.countItem(Items.CANDLE) + " candles, " + chest.countItem(Items.BLUE_DYE) + " dye left");
		});
	}

	/** A candle block holding three candles takes three candles: three honeycomb and three string, all made by her. */
	//$ gametest_ticks_batch AREA '3600' '"qaChandlerThree"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaChandlerThree")
	public void threeCandlesInOneBlockTakeThreeHoneycomb(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3), "three"),
			new ItemStack(Items.HONEYCOMB, 3), new ItemStack(Items.STRING, 3));
		Villager keeper = beekeeper(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			BlockState placed = helper.getBlockState(ROW);
			helper.assertTrue(placed.is(Blocks.CANDLE) && placed.getValue(CandleBlock.CANDLES) == 3, "placed: " + placed);
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(keeper, 0) >= 3, "the Beekeeper made " + ModAttachments.ITEMS_CRAFTED.getOrElse(keeper, 0));
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.HONEYCOMB) == 0 && chest.countItem(Items.STRING) == 0,
				chest.countItem(Items.HONEYCOMB) + " honeycomb, " + chest.countItem(Items.STRING) + " string left");
		});
	}

	/** A red candle's steps are the candle, then the color; saved and loaded between them she carries on and it's built. */
	//$ gametest_ticks_batch AREA '4000' '"qaChandlerRedReload"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaChandlerRedReload")
	public void aBeekeeperLoadedBetweenStepsFinishesTheRedCandle(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.RED_CANDLE.defaultBlockState(), "redreload"), new ItemStack(Items.HONEYCOMB, 1),
			new ItemStack(Items.STRING, 1), new ItemStack(Items.RED_DYE, 1));
		AtomicReference<Villager> keeper = new AtomicReference<>(beekeeper(helper));
		AtomicReference<List<Item>> steps = new AtomicReference<>();
		AtomicBoolean reloaded = new AtomicBoolean();
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(keeper.get());
			if (job == null || reloaded.get()) {
				return;
			}
			if (steps.get() == null) {
				steps.set(job.steps().stream().map(s -> s.out().getItem()).toList());
			}
			if (job.step() >= 1) {
				reloaded.set(true);
				Villager copy = reload(helper.getLevel(), keeper.get());
				CrafterJob after = ModAttachments.CRAFTER_JOB.get(copy);
				helper.assertTrue(after != null && after.step() == job.step(), "the step wasn't saved: " + job.step() + " -> " + (after == null ? null : after.step()));
				helper.assertTrue(!ModAttachments.BUILDER_BAG.getOrCreate(copy).isEmpty(), "the bag was lost across the reload");
				keeper.set(copy);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(List.of(Items.CANDLE, Items.RED_CANDLE).equals(steps.get()), "the red candle's steps: " + steps.get());
			helper.assertTrue(reloaded.get(), "never reloaded between steps");
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(keeper.get()) == null, "the finished job is still saved on the Beekeeper");
		});
	}

	/** String but no honeycomb: nothing is taken and the line names the honeycomb word for word. */
	//$ gametest_ticks_batch AREA '1400' '"qaChandlerNoComb"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaChandlerNoComb")
	public void aMissingHoneycombIsNamedWordForWord(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.CANDLE.defaultBlockState(), "nocomb"), new ItemStack(Items.STRING, 1));
		Villager keeper = beekeeper(helper);
		helper.runAfterDelay(200, () -> line(helper, a));
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.STRING) == 1, "the string was taken");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(keeper, 0) == 0, "the Beekeeper made " + ModAttachments.ITEMS_CRAFTED.getOrElse(keeper, 0));
			String text = line(helper, a);
			helper.assertTrue("Can't be made yet: short of 1 Honeycomb to make 1 Candle".equals(text), "the builder's line: " + text);
			helper.succeed();
		});
	}

	/** Honeycomb and string but no red dye: the dye is named as the player hands it over, and nothing is taken. */
	//$ gametest_ticks_batch AREA '1400' '"qaChandlerNoDye"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaChandlerNoDye")
	public void aMissingDyeIsNamedAsTheDye(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.RED_CANDLE.defaultBlockState(), "nodye"), new ItemStack(Items.HONEYCOMB, 1),
			new ItemStack(Items.STRING, 1));
		beekeeper(helper);
		helper.runAfterDelay(200, () -> line(helper, a));
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.HONEYCOMB) == 1 && chest.countItem(Items.STRING) == 1, "the makings were taken");
			String text = line(helper, a);
			helper.assertTrue("Can't be made yet: short of 1 Red Dye to make 1 Red Candle".equals(text), "the builder's line: " + text);
			helper.succeed();
		});
	}

	/** A Carpenter at his bench with honeycomb and string about makes no candle; the line names the Beekeeper. */
	//$ gametest_ticks_batch AREA '1400' '"qaChandlerCarpenter"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaChandlerCarpenter")
	public void aCarpenterNeverMakesACandle(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.CANDLE.defaultBlockState(), "carp"), new ItemStack(Items.HONEYCOMB, 1), new ItemStack(Items.STRING, 1));
		helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
		helper.setBlock(STATION.east(), Blocks.CHEST);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		assertLeftAlone(helper, a, carpenter, "Hire a Beekeeper to make Candles");
	}

	/** A Shepherd at his loom (he has string) makes no candle; the line names the Beekeeper. */
	//$ gametest_ticks_batch AREA '1400' '"qaChandlerShepherd"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaChandlerShepherd")
	public void aShepherdNeverMakesACandle(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.CANDLE.defaultBlockState(), "shep"), new ItemStack(Items.HONEYCOMB, 1), new ItemStack(Items.STRING, 1));
		helper.setBlock(STATION, Blocks.LOOM);
		helper.setBlock(STATION.east(), Blocks.CHEST);
		Villager shepherd = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), shepherd, helper.absolutePos(STATION), PoiTypes.SHEPHERD, VillagerProfession.SHEPHERD);
		assertLeftAlone(helper, a, shepherd, "Hire a Beekeeper to make Candles");
	}

	/** A Leatherworker (the old dyer) with the makings about leaves a blue candle alone: the line names the Beekeeper. */
	//$ gametest_ticks_batch AREA '1400' '"qaChandlerOldDyer"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaChandlerOldDyer")
	public void theOldDyerLeavesADyedCandleAlone(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.BLUE_CANDLE.defaultBlockState(), "olddyer"), new ItemStack(Items.HONEYCOMB, 1),
			new ItemStack(Items.STRING, 1), new ItemStack(Items.BLUE_DYE, 1));
		Villager leatherworker = leatherworker(helper);
		assertLeftAlone(helper, a, leatherworker, "Hire a Beekeeper to make Blue Candles");
	}

	/** No Beekeeper, a candle and a honeycomb block wanted: one line names her for both, plural. */
	//$ gametest_ticks_batch AREA '1400' '"qaChandlerHireBoth"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaChandlerHireBoth")
	public void theHireLineNamesBothOfHerThings(GameTestHelper helper) {
		prepare(helper);
		helper.setBlock(ROW, Blocks.CANDLE);
		helper.setBlock(ROW.east(), Blocks.HONEYCOMB_BLOCK);
		Asker a = asker(helper, capture(helper, ROW, new Vec3i(2, 1, 1), "both"));
		helper.runAfterDelay(200, () -> line(helper, a));
		helper.runAfterDelay(1100, () -> {
			String text = line(helper, a);
			helper.assertTrue("Hire a Beekeeper to make Candles, Honeycomb Blocks".equals(text) || "Hire a Beekeeper to make Honeycomb Blocks, Candles".equals(text),
				"the builder's line: " + text);
			helper.succeed();
		});
	}

	/** A Storehouse's stock order for candles is filled by the Beekeeper from its honeycomb and string. */
	//$ gametest_ticks_batch AREA '4000' '"qaChandlerStock"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaChandlerStock")
	public void theBeekeeperFillsAStockOrderForCandles(GameTestHelper helper) {
		prepare(helper);
		Container store = store(helper, new ItemStack(Items.HONEYCOMB, 4), new ItemStack(Items.STRING, 4));
		StockOrders.cycle(helper.getLevel(), helper.absolutePos(STOREHOUSE), Items.CANDLE);
		Villager keeper = beekeeper(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(store.countItem(Items.CANDLE) >= 1, store.countItem(Items.CANDLE) + " candles in the store");
			helper.assertTrue(store.countItem(Items.HONEYCOMB) < 4, "the candles weren't made from the store's honeycomb");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(keeper, 0) >= 1, "the Beekeeper made " + ModAttachments.ITEMS_CRAFTED.getOrElse(keeper, 0));
		});
	}

	/** Only a Leatherworker by a store with honeycomb and a stock order for honeycomb blocks: he leaves it alone. */
	//$ gametest_ticks_batch AREA '1400' '"qaChandlerLeatherNoBlock"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaChandlerLeatherNoBlock")
	public void theLeatherworkerNeverMakesAHoneycombBlock(GameTestHelper helper) {
		prepare(helper);
		Container store = store(helper, new ItemStack(Items.HONEYCOMB, 8));
		StockOrders.cycle(helper.getLevel(), helper.absolutePos(STOREHOUSE), Items.HONEYCOMB_BLOCK);
		Villager leatherworker = leatherworker(helper);
		helper.runAfterDelay(1100, () -> {
			helper.assertTrue(store.countItem(Items.HONEYCOMB) == 8 && store.countItem(Items.HONEYCOMB_BLOCK) == 0, "the Leatherworker used the honeycomb");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(leatherworker, 0) == 0,
				"the Leatherworker made " + ModAttachments.ITEMS_CRAFTED.getOrElse(leatherworker, 0));
			helper.succeed();
		});
	}

	/** A stock order for leather horse armor (on his list, though no builder asks for it), filled from the store's leather. */
	//$ gametest_ticks_batch AREA '4000' '"qaLeatherHorse"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaLeatherHorse")
	public void theLeatherworkerMakesLeatherHorseArmor(GameTestHelper helper) {
		prepare(helper);
		Container store = store(helper, new ItemStack(Items.LEATHER, 16));
		StockOrders.cycle(helper.getLevel(), helper.absolutePos(STOREHOUSE), Items.LEATHER_HORSE_ARMOR);
		Villager leatherworker = leatherworker(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(store.countItem(Items.LEATHER_HORSE_ARMOR) >= 1, store.countItem(Items.LEATHER_HORSE_ARMOR) + " leather horse armor in the store");
			helper.assertTrue(store.countItem(Items.LEATHER) <= 16 - 7, store.countItem(Items.LEATHER) + " leather left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(leatherworker, 0) >= 1,
				"the Leatherworker made " + ModAttachments.ITEMS_CRAFTED.getOrElse(leatherworker, 0));
		});
	}

	/**
	 * Her Apiary broken while the makings are in her bag: nothing is lost. After the grace time the honeycomb and string
	 * are back in the builder's chest or on the ground, or the candle was made.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaChandlerApiaryBroken"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaChandlerApiaryBroken")
	public void herApiaryBrokenMidJobLosesNothing(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.CANDLE.defaultBlockState(), "broken"), new ItemStack(Items.HONEYCOMB, 1), new ItemStack(Items.STRING, 1));
		Villager keeper = beekeeper(helper);
		AtomicLong broke = new AtomicLong(-1);
		helper.onEachTick(() -> {
			if (broke.get() >= 0) {
				return;
			}
			if (ModAttachments.CRAFTER_JOB.get(keeper) != null && !ModAttachments.BUILDER_BAG.getOrCreate(keeper).isEmpty()) {
				helper.setBlock(STATION, Blocks.AIR);
				broke.set(helper.getTick());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broke.get() >= 0, "she never had the makings in her bag");
			helper.assertTrue(helper.getTick() > broke.get() + 700, "waiting out the grace time");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(keeper) == null, "the job is still saved on her");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(keeper).isEmpty(), "still in her bag");
			Container chest = helper.getBlockEntity(a.chest());
			int combs = chest.countItem(Items.HONEYCOMB);
			int strings = chest.countItem(Items.STRING);
			int candles = chest.countItem(Items.CANDLE) + (helper.getBlockState(ROW).is(Blocks.CANDLE) ? 1 : 0);
			AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(24);
			for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
				if (e.getItem().is(Items.HONEYCOMB)) {
					combs += e.getItem().getCount();
				} else if (e.getItem().is(Items.STRING)) {
					strings += e.getItem().getCount();
				} else if (e.getItem().is(Items.CANDLE)) {
					candles += e.getItem().getCount();
				}
			}
			helper.assertTrue((combs == 1 && strings == 1) || candles >= 1, "lost: " + combs + " honeycomb, " + strings + " string and " + candles + " candles found");
		});
	}

	/** Over her head while she makes a candle: "Beekeeper · 1 made for builders", "making 1 Candle", "taking 1 Candle to the builder". */
	//$ gametest_ticks_batch AREA '2400' '"qaChandlerStatus"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaChandlerStatus")
	public void theStatusOverHerHeadWordForWord(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.CANDLE.defaultBlockState(), "status"), new ItemStack(Items.HONEYCOMB, 1), new ItemStack(Items.STRING, 1));
		Villager keeper = beekeeper(helper);
		Set<String> titles = ConcurrentHashMap.newKeySet();
		Set<String> lines = ConcurrentHashMap.newKeySet();
		helper.onEachTick(() -> {
			WorkerStatus.Entry e = WorkerStatus.get(keeper, helper.getLevel().getGameTime());
			if (e != null) {
				titles.add(e.title().getString());
				lines.add(e.line().getString());
			}
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(titles.contains("Beekeeper · 1 made for builders"), "titles: " + titles);
			helper.assertTrue(lines.stream().anyMatch(l -> l.startsWith("making 1 Candle")), "lines: " + lines);
			helper.assertTrue(lines.contains("taking 1 Candle to the builder"), "lines: " + lines);
		});
	}

	/** Over the Leatherworker's head while he fills a stock order: "Leatherworker · N made for builders", never "Dyer". */
	//$ gametest_ticks_batch AREA '4000' '"qaLeatherStatus"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaLeatherStatus")
	public void theLeatherworkersHeadLineSaysLeatherworker(GameTestHelper helper) {
		prepare(helper);
		Container store = store(helper, new ItemStack(Items.STRING, 8), new ItemStack(Items.SLIME_BALL, 4));
		StockOrders.cycle(helper.getLevel(), helper.absolutePos(STOREHOUSE), Items.LEAD);
		Villager leatherworker = leatherworker(helper);
		Set<String> titles = ConcurrentHashMap.newKeySet();
		helper.onEachTick(() -> {
			WorkerStatus.Entry e = WorkerStatus.get(leatherworker, helper.getLevel().getGameTime());
			if (e != null) {
				titles.add(e.title().getString());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(store.countItem(Items.LEAD) >= 1, store.countItem(Items.LEAD) + " leads in the store");
			helper.assertTrue(titles.stream().anyMatch(t -> t.matches("Leatherworker · \\d+ made for builders")), "titles: " + titles);
			helper.assertTrue(titles.stream().noneMatch(t -> t.contains("Dyer")), "titles: " + titles);
		});
	}

	/**
	 * The Storehouse's details line for a candle and an item frame: "Hire a Beekeeper to make it" / "Hire a Leatherworker
	 * to make it" with none here, then "Made by: the Beekeeper (Hilda)" once she works nearby.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaChandlerStoreLines"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaChandlerStoreLines")
	public void theStorehouseNamesWhoMakesThem(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(STOREHOUSE);
		helper.assertTrue("Hire a Beekeeper to make it".equals(storeLine(level, at, Items.CANDLE)), "candle, nobody: " + storeLine(level, at, Items.CANDLE));
		helper.assertTrue("Hire a Beekeeper to make it".equals(storeLine(level, at, Items.HONEYCOMB_BLOCK)),
			"honeycomb block, nobody: " + storeLine(level, at, Items.HONEYCOMB_BLOCK));
		helper.assertTrue("Hire a Leatherworker to make it".equals(storeLine(level, at, Items.ITEM_FRAME)),
			"item frame, nobody: " + storeLine(level, at, Items.ITEM_FRAME));
		Villager keeper = beekeeper(helper);
		keeper.setCustomName(Component.literal("Hilda"));
		helper.runAfterDelay(5, () -> {
			helper.assertTrue("Made by: the Beekeeper (Hilda)".equals(storeLine(level, at, Items.GREEN_CANDLE)),
				"green candle, Hilda here: " + storeLine(level, at, Items.GREEN_CANDLE));
			helper.assertTrue("Hire a Leatherworker to make it".equals(storeLine(level, at, Items.LEAD)), "lead, only Hilda here: " + storeLine(level, at, Items.LEAD));
			helper.succeed();
		});
	}

	// ---- helpers ----

	private static String storeLine(ServerLevel level, BlockPos at, Item item) {
		List<Component> lines = CraftJobs.storeLines(level, at, 16, item);
		return lines.isEmpty() ? null : lines.get(0).getString();
	}

	/** Over 1100 ticks the worker takes nothing, makes nothing, and the builder's line reads {@code expected}. */
	private static void assertLeftAlone(GameTestHelper helper, Asker a, Villager worker, String expected) {
		Container chest = helper.getBlockEntity(a.chest());
		int[] before = new int[chest.getContainerSize()];
		for (int i = 0; i < before.length; i++) {
			before[i] = chest.getItem(i).getCount();
		}
		helper.runAfterDelay(200, () -> line(helper, a));
		helper.runAfterDelay(1100, () -> {
			for (int i = 0; i < before.length; i++) {
				helper.assertTrue(chest.getItem(i).getCount() == before[i], "slot " + i + " changed: " + before[i] + " -> " + chest.getItem(i));
			}
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(worker, 0) == 0, "the " + worker.getVillagerData().getProfession() + " made "
				+ ModAttachments.ITEMS_CRAFTED.getOrElse(worker, 0));
			String text = line(helper, a);
			helper.assertTrue(expected.equals(text), "the builder's line: " + text);
			helper.succeed();
		});
	}

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

	private static ResourceLocation single(GameTestHelper helper, BlockState state, String what) {
		helper.setBlock(ROW, state);
		return capture(helper, ROW, new Vec3i(1, 1, 1), what);
	}

	private static ResourceLocation capture(GameTestHelper helper, BlockPos origin, Vec3i size, String what) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qachandler_" + what + "_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(origin).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin), size, false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < size.getX(); x++) {
			for (int y = 0; y < size.getY(); y++) {
				for (int z = 0; z < size.getZ(); z++) {
					helper.setBlock(origin.offset(x, y, z), Blocks.AIR);
				}
			}
		}
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

	/** A Beekeeper at her Apiary, with an empty chest by it. */
	private static Villager beekeeper(GameTestHelper helper) {
		helper.setBlock(STATION, ModBlocks.APIARY);
		helper.setBlock(STATION.east(), Blocks.CHEST);
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
