package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderStatusSync;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.StockOrders;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariants;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-c-1123-0953) of ROADMAP 46.5 (candles and leather goods) and B1159, written from docs/wiki/candles-leather.md
 * where the earlier QA classes left gaps: the Beekeeper's makings taken from her own chest, exact input counts (a
 * honeycomb block from four of five honeycomb, three honeycomb one short), the plural on the "short of" line, a stock
 * order whose store chest is full losing nothing, and three decoration jobs to hire naming two and counting the rest.
 */
public class QaCandlesLeather1123cGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STATION = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 8);
	private static final BlockPos STOREHOUSE = new BlockPos(12, 2, 12);
	private static final BlockPos STORE_CHEST = new BlockPos(12, 2, 14);

	private record Asker(Villager builder, BuildSite site, BuildPlan plan) {
	}

	private static String lang(String key) {
		return QaTinkererShortGameTests.lang(key);
	}

	/** "She fetches the makings from the builder's chests (and her own)": honeycomb and string only in the chest by her Apiary. */
	//$ gametest_ticks_batch AREA '3000' '"qa1123cOwnChest"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa1123cOwnChest")
	public void herOwnChestsMakingsBecomeTheBuildersCandle(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.CANDLE.defaultBlockState(), "own"));
		Villager keeper = beekeeper(helper);
		Container own = helper.getBlockEntity(STATION.east());
		own.setItem(0, new ItemStack(Items.HONEYCOMB, 1));
		own.setItem(1, new ItemStack(Items.STRING, 1));
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(own.countItem(Items.HONEYCOMB) == 0 && own.countItem(Items.STRING) == 0,
				"her chest still holds " + own.countItem(Items.HONEYCOMB) + " honeycomb, " + own.countItem(Items.STRING) + " string");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(keeper, 0) >= 1, "the Beekeeper made " + ModAttachments.ITEMS_CRAFTED.getOrElse(keeper, 0));
		});
	}

	/** Five honeycomb for one honeycomb block: exactly four are used and one stays in the chest. */
	//$ gametest_ticks_batch AREA '3000' '"qa1123cFive"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa1123cFive")
	public void aHoneycombBlockTakesExactlyFourOfFive(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.HONEYCOMB_BLOCK.defaultBlockState(), "five"), new ItemStack(Items.HONEYCOMB, 5));
		beekeeper(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(helper.getBlockState(ROW).is(Blocks.HONEYCOMB_BLOCK), "placed: " + helper.getBlockState(ROW));
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.HONEYCOMB) == 1, chest.countItem(Items.HONEYCOMB) + " honeycomb left, not 1");
		});
	}

	/** Three honeycomb for a honeycomb block: one short, named word for word, and nothing taken. */
	//$ gametest_ticks_batch AREA '1400' '"qa1123cThree"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qa1123cThree")
	public void threeHoneycombAreOneShortOfABlock(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.HONEYCOMB_BLOCK.defaultBlockState(), "three"), new ItemStack(Items.HONEYCOMB, 3));
		Villager keeper = beekeeper(helper);
		helper.runAfterDelay(200, () -> line(helper, a));
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.HONEYCOMB) == 3, "the honeycomb was taken: " + chest.countItem(Items.HONEYCOMB) + " left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(keeper, 0) == 0, "the Beekeeper made " + ModAttachments.ITEMS_CRAFTED.getOrElse(keeper, 0));
			String text = line(helper, a);
			String want = String.format(lang("message.aliveworkplace.job_chain.cant_make"),
				String.format(lang("message.aliveworkplace.job_chain.lacks"), "1 Honeycomb", "1", "Honeycomb Block"));
			helper.assertTrue("Can't be made yet: short of 1 Honeycomb to make 1 Honeycomb Block".equals(want), "lang changed: " + want);
			helper.assertTrue(want.equals(text), "the builder's line: " + text);
			helper.succeed();
		});
	}

	/** Two candles in one block with honeycomb but no string: the line is plural on the candles ("2 Candles"). */
	//$ gametest_ticks_batch AREA '1400' '"qa1123cPlural"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qa1123cPlural")
	public void twoCandlesShortOfStringReadPlural(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 2), "plural"),
			new ItemStack(Items.HONEYCOMB, 2));
		beekeeper(helper);
		helper.runAfterDelay(200, () -> line(helper, a));
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.HONEYCOMB) == 2, "the honeycomb was taken: " + chest.countItem(Items.HONEYCOMB) + " left");
			String text = line(helper, a);
			helper.assertTrue("Can't be made yet: short of 2 String to make 2 Candles".equals(text), "the builder's line: " + text);
			helper.succeed();
		});
	}

	/**
	 * A stock order for candles whose store chest has no free slot: nothing is lost. Every honeycomb and string is still
	 * a honeycomb or string, or became a candle, wherever it is (store, her chest, her bag, the ground).
	 */
	//$ gametest_ticks_batch AREA '2600' '"qa1123cFullStore"'
	@GameTest(template = AREA, timeoutTicks = 2600, batch = "qa1123cFullStore")
	public void aFullStoreChestLosesNoCandle(GameTestHelper helper) {
		prepare(helper);
		Leftovers.halls(helper);
		int range = StockOrders.RANGE;
		StockOrders.RANGE = 16;
		Leftovers.after(helper, () -> StockOrders.RANGE = range);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		Container store = helper.getBlockEntity(STORE_CHEST);
		store.setItem(0, new ItemStack(Items.HONEYCOMB, 4));
		store.setItem(1, new ItemStack(Items.STRING, 4));
		for (int i = 2; i < store.getContainerSize(); i++) {
			store.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		StockOrders.cycle(helper.getLevel(), helper.absolutePos(STOREHOUSE), Items.CANDLE);
		Villager keeper = beekeeper(helper);
		Container own = helper.getBlockEntity(STATION.east());
		helper.runAfterDelay(2400, () -> {
			int combs = 0;
			int strings = 0;
			int candles = 0;
			for (Container c : List.of(store, own)) {
				combs += c.countItem(Items.HONEYCOMB);
				strings += c.countItem(Items.STRING);
				candles += c.countItem(Items.CANDLE);
			}
			var bag = ModAttachments.BUILDER_BAG.getOrCreate(keeper);
			combs += bag.count(Items.HONEYCOMB);
			strings += bag.count(Items.STRING);
			candles += bag.count(Items.CANDLE);
			for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(24))) {
				Item item = e.getItem().getItem();
				if (item == Items.HONEYCOMB) {
					combs += e.getItem().getCount();
				} else if (item == Items.STRING) {
					strings += e.getItem().getCount();
				} else if (item == Items.CANDLE) {
					candles += e.getItem().getCount();
				}
			}
			helper.assertTrue(combs + candles == 4 && strings + candles == 4,
				"lost or duplicated: " + combs + " honeycomb, " + strings + " string, " + candles + " candles (want 4 each with the candles)");
			helper.assertTrue(store.countItem(Items.COBBLESTONE) == 25 * 64, "the store's cobblestone changed: " + store.countItem(Items.COBBLESTONE));
			helper.succeed();
		});
	}

	/**
	 * B1159: a finished build waiting for an item frame, a painting and an armor stand, none of their three jobs here: the
	 * lines name the first two jobs to hire and count the rest ("and 1 more").
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa1123cHireThree"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1123cHireThree")
	public void threeJobsToHireNameTwoAndCountTheRest(GameTestHelper helper) {
		QaBuilderDecorations1110bGameTests.prepare(helper);
		QaBuilderDecorations1110bGameTests.shortWait(helper, 2000);
		ServerLevel level = helper.getLevel();
		ResourceLocation id = blueprint(helper, level, List.of(
			new ItemFrame(level, helper.absolutePos(ROW.south()), Direction.SOUTH),
			new Painting(level, helper.absolutePos(ROW.east(1).south()), Direction.SOUTH,
				level.registryAccess().registryOrThrow(Registries.PAINTING_VARIANT).getHolderOrThrow(PaintingVariants.KEBAB)),
			new ArmorStand(level, helper.absolutePos(ROW.east(2).above()).getX() + 0.5, helper.absolutePos(ROW.east(2).above()).getY(),
				helper.absolutePos(ROW.east(2).above()).getZ() + 0.5)));
		QaBuilderDecorations1110bGameTests.chest(helper, new ItemStack(Items.OAK_PLANKS, 8));
		Villager builder = QaBuilderDecorations1110bGameTests.builder(helper);
		BuildSite site = QaBuilderDecorations1110bGameTests.start(helper, builder, id);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		String frames = String.format(lang("message.aliveworkplace.job_chain.hire"), "Leatherworker", "Item Frames");
		String paintings = String.format(lang("message.aliveworkplace.job_chain.hire"), "Shepherd", "Paintings");
		String stands = String.format(lang("message.aliveworkplace.job_chain.hire"), "Carpenter", "Armor Stands");
		String more = String.format(lang("message.aliveworkplace.status.and_more"), 1);
		AtomicReference<List<String>> seen = new AtomicReference<>(List.of());
		helper.onEachTick(() -> {
			if (level.getGameTime() % 20 != 0) {
				return;
			}
			BuildSite open = BuildSiteManager.get(level).get(site.id());
			if (open != null && open.isGettingDecorations() && open.status() == BuildSite.Status.WAITING_FOR_MATERIALS) {
				BuilderStatusSync.Status s = BuilderStatusSync.status(level, open, builder);
				seen.set(s.more().stream().map(Component::getString).toList());
			}
		});
		helper.succeedWhen(() -> {
			String all = String.join(" | ", seen.get());
			int named = (all.contains(frames) ? 1 : 0) + (all.contains(paintings) ? 1 : 0) + (all.contains(stands) ? 1 : 0);
			helper.assertTrue(named == 2, "want two of the three jobs named, got " + named + ": " + seen.get());
			helper.assertTrue(seen.get().stream().anyMatch(l -> (l.contains("Hire a ")) && l.endsWith(more)), "no hire line ends with '" + more + "': " + seen.get());
		});
	}

	// ---- helpers ----

	private static ResourceLocation blueprint(GameTestHelper helper, ServerLevel level, List<Entity> made) {
		for (int x = 0; x < 3; x++) {
			helper.setBlock(ROW.east(x), Blocks.OAK_PLANKS);
		}
		for (Entity e : made) {
			helper.assertTrue(!(e instanceof net.minecraft.world.entity.decoration.HangingEntity h) || h.survives(), "setup: a decoration can't hang");
			level.addFreshEntity(e);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qa1123c_three_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(ROW).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(3, 3, 2), true, Blocks.STRUCTURE_VOID);
		made.forEach(Entity::discard);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(ROW.east(x), Blocks.AIR);
		}
		return id;
	}

	private static void prepare(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
	}

	private static String line(GameTestHelper helper, Asker a) {
		Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing(), helper.absolutePos(BENCH));
		return line == null ? null : line.getString();
	}

	private static ResourceLocation single(GameTestHelper helper, BlockState state, String what) {
		helper.setBlock(ROW, state);
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qa1123c_" + what + "_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(ROW).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(ROW, Blocks.AIR);
		return id;
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
		return new Asker(builder, site, plan);
	}

	/** A Beekeeper at her Apiary, with an empty chest by it. */
	private static Villager beekeeper(GameTestHelper helper) {
		helper.setBlock(STATION, ModBlocks.APIARY);
		helper.setBlock(STATION.east(), Blocks.CHEST);
		Villager keeper = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), keeper, helper.absolutePos(STATION), ModVillagers.APIARY_POI, ModVillagers.BEEKEEPER);
		return keeper;
	}

	private static void assertBuilt(GameTestHelper helper, Asker a) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(BuildSiteManager.get(level).get(a.site().id()) == null,
			"still building: status=" + a.site().status() + " missing=" + a.site().missing());
		List<BlockPos> unfinished = a.plan().unfinished(level);
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build");
	}
}
