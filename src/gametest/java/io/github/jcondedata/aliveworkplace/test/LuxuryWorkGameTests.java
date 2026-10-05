package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.craft.LuxuryRecipes;
import io.github.jcondedata.aliveworkplace.craft.LuxuryWork;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.store.StockOrders;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * The luxury workshop engine (ROADMAP 34.5): the GameTest data pack's three luxury recipes for a Nitwit (a Glow Ink Sac
 * "cordial" from sugar and a small flower at level 1, a Nautilus Shell "vintage" from a cordial three days old at level 2,
 * and Prismarine Crystals from paper, a good no code knows) made by a {@link LuxuryWork} driven tick by tick.
 */
public class LuxuryWorkGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos STATION = new BlockPos(2, 2, 2);
	private static final BlockPos OWN_CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STOREHOUSE = new BlockPos(12, 2, 2);
	private static final BlockPos STORE_CHEST = new BlockPos(12, 2, 4);

	/** A maker (level {@code level}) at a loom with a chest beside it, and a Storehouse with its chest (the store) far off. */
	private static Villager maker(GameTestHelper helper, int level) {
		ServerLevel world = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		// Only this test's Storehouse is the store: the neighbouring tests' are further than this.
		int range = StockOrders.RANGE;
		StockOrders.RANGE = 12;
		Leftovers.after(helper, () -> StockOrders.RANGE = range);
		helper.setBlock(STATION, Blocks.LOOM);
		helper.setBlock(OWN_CHEST, Blocks.CHEST);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NITWIT).setLevel(level));
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(world.dimension(), helper.absolutePos(STATION)));
		LuxuryWork work = new LuxuryWork();
		helper.onEachTick(() -> {
			if (!v.isAlive()) {
				return;
			}
			long now = world.getGameTime();
			if (work.getStatus() == Behavior.Status.STOPPED) {
				work.tryStart(world, v, now);
			} else {
				work.tickOrStop(world, v, now);
			}
		});
		return v;
	}

	private static Container chest(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockEntity(pos);
	}

	private static int count(Container c, Item item) {
		return c.countItem(item);
	}

	/** Short of glow ink sacs (3 in the store), it makes 5 (sugar from cane on the way), stamped today, and stops at 8. */
	//$ gametest_ticks_batch AREA '1200' '"luxuryShort"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "luxuryShort")
	public void makesWhatsShortAndStopsAtEight(GameTestHelper helper) {
		Villager v = maker(helper, 1);
		Container own = chest(helper, OWN_CHEST);
		Container store = chest(helper, STORE_CHEST);
		store.setItem(0, new ItemStack(Items.GLOW_INK_SAC, 3));
		store.setItem(1, new ItemStack(Items.PRISMARINE_CRYSTALS, 8)); // the crystals (paper from cane) aren't short
		own.setItem(0, new ItemStack(Items.SUGAR_CANE, 12));
		own.setItem(1, new ItemStack(Items.DANDELION, 12));
		long start = helper.getLevel().getGameTime();
		long today = LuxuryRecipes.today(helper.getLevel());
		helper.succeedWhen(() -> {
			helper.assertTrue(count(own, Items.GLOW_INK_SAC) == 5, count(own, Items.GLOW_INK_SAC) + " made, expected 5");
			helper.assertTrue(helper.getLevel().getGameTime() - start > 700, "waiting to see it stops");
			helper.assertTrue(count(own, Items.SUGAR_CANE) == 7 && count(own, Items.DANDELION) == 7,
				count(own, Items.SUGAR_CANE) + " cane and " + count(own, Items.DANDELION) + " flowers left, expected 7 each");
			ItemStack made = null;
			for (int i = 0; i < own.getContainerSize(); i++) {
				if (own.getItem(i).is(Items.GLOW_INK_SAC)) {
					made = own.getItem(i);
				}
			}
			LuxuryRecipes.MadeDay day = made.get(ModComponents.MADE_DAY);
			helper.assertTrue(day != null && day.day() == today && day.vintageDays() == 3, "made day " + day + ", expected day " + today + ", 3 to vintage");
		});
	}

	/** A level 1 maker leaves the level 2 recipe alone; at level 2 they make it. */
	//$ gametest_ticks_batch AREA '1600' '"luxuryLevel"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "luxuryLevel")
	public void keepsToItsLevel(GameTestHelper helper) {
		Villager v = maker(helper, 1);
		Container own = chest(helper, OWN_CHEST);
		own.setItem(0, new ItemStack(Items.GLOW_INK_SAC, 1)); // no made day: old stock
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(count(own, Items.NAUTILUS_SHELL) == 0, "a level 1 maker made the level 2 good");
			v.setVillagerData(v.getVillagerData().setLevel(2));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getLevel().getGameTime() > 0 && v.getVillagerData().getLevel() == 2, "still level 1");
			helper.assertTrue(count(own, Items.NAUTILUS_SHELL) == 1 && count(own, Items.GLOW_INK_SAC) == 0,
				count(own, Items.NAUTILUS_SHELL) + " shells at level 2, expected 1");
		});
	}

	/** Cordials pressed today wait three days before they become vintage; with the day moved on, they're made. */
	//$ gametest_ticks_batch AREA '1600' '"luxuryAged"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "luxuryAged")
	public void waitsForAnAgedInput(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long dayTime = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(dayTime));
		Villager v = maker(helper, 2);
		Container own = chest(helper, OWN_CHEST);
		long today = LuxuryRecipes.today(level);
		ItemStack fresh = new ItemStack(Items.GLOW_INK_SAC, 2);
		fresh.set(ModComponents.MADE_DAY, new LuxuryRecipes.MadeDay(today, 3));
		own.setItem(0, fresh);
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(count(own, Items.NAUTILUS_SHELL) == 0, "made vintage from cordial pressed today");
			helper.assertTrue(count(own, Items.GLOW_INK_SAC) == 2, "the fresh cordial was taken");
			level.setDayTime(level.getDayTime() + 3 * 24000L);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(LuxuryRecipes.today(level) >= today + 3, "the day hasn't moved on yet");
			helper.assertTrue(count(own, Items.NAUTILUS_SHELL) == 2, count(own, Items.NAUTILUS_SHELL) + " shells three days on, expected 2");
		});
	}

	/** With nothing to make a cordial from, the maker asks for sugar (the first making) on the requests board. */
	//$ gametest_ticks_batch AREA '600' '"luxuryAsks"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "luxuryAsks")
	public void asksForMissingMakings(GameTestHelper helper) {
		Villager v = maker(helper, 1);
		helper.succeedWhen(() -> {
			boolean asked = Requests.of(helper.getLevel(), v).stream().anyMatch(r -> r.accepts().test(new ItemStack(Items.SUGAR)));
			helper.assertTrue(asked, "no request for sugar: " + Requests.of(helper.getLevel(), v).stream().map(r -> r.what().getString()).toList());
		});
	}

	/** A stock order for crystals is filled before the cordials the store is short of. */
	//$ gametest_ticks_batch AREA '2400' '"luxuryOrder"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "luxuryOrder")
	public void fillsAStockOrderFirst(GameTestHelper helper) {
		Villager v = maker(helper, 1);
		Container own = chest(helper, OWN_CHEST);
		Container store = chest(helper, STORE_CHEST);
		StockOrders.cycle(helper.getLevel(), helper.absolutePos(STOREHOUSE), Items.PRISMARINE_CRYSTALS); // keep 16
		store.setItem(0, new ItemStack(Items.PAPER, 40));
		own.setItem(0, new ItemStack(Items.SUGAR, 12));
		own.setItem(1, new ItemStack(Items.POPPY, 12));
		helper.onEachTick(() -> {
			if (count(own, Items.GLOW_INK_SAC) > 0 && count(store, Items.PRISMARINE_CRYSTALS) < 16) {
				throw new GameTestAssertException("cordials before the stock order: " + count(store, Items.PRISMARINE_CRYSTALS) + " crystals");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(count(store, Items.PRISMARINE_CRYSTALS) == 16, count(store, Items.PRISMARINE_CRYSTALS) + " crystals in the store, expected 16");
			helper.assertTrue(count(store, Items.PAPER) == 8, count(store, Items.PAPER) + " paper left, expected 8");
			helper.assertTrue(count(own, Items.GLOW_INK_SAC) == 8, count(own, Items.GLOW_INK_SAC) + " cordials after, expected 8");
		});
	}

	/** The data pack's crystal recipe loads and is made, with no code for it. */
	//$ gametest_ticks_batch AREA '1200' '"luxuryData"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "luxuryData")
	public void aDatapackRecipeAddsANewGood(GameTestHelper helper) {
		LuxuryRecipes.Recipe recipe = LuxuryRecipes.all().get(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_new_good"));
		helper.assertTrue(recipe != null && recipe.output() == Items.PRISMARINE_CRYSTALS && recipe.level() == 1 && recipe.ticks() == 10,
			"the data pack's recipe didn't load: " + recipe);
		Villager v = maker(helper, 1);
		Container own = chest(helper, OWN_CHEST);
		own.setItem(0, new ItemStack(Items.PAPER, 6));
		helper.succeedWhen(() -> helper.assertTrue(count(own, Items.PRISMARINE_CRYSTALS) == 3 && count(own, Items.PAPER) == 0,
			count(own, Items.PRISMARINE_CRYSTALS) + " crystals, expected 3"));
	}

	/** The tooltip: "Pressed on day 42 · vintage in 2 days", one day, and vintage. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theMadeDayTooltip(GameTestHelper helper) {
		LuxuryRecipes.MadeDay made = new LuxuryRecipes.MadeDay(42, 3);
		helper.assertTrue(english(LuxuryRecipes.tooltip(made, 43)).equals("Pressed on day 42 · vintage in 2 days"), english(LuxuryRecipes.tooltip(made, 43)));
		helper.assertTrue(english(LuxuryRecipes.tooltip(made, 44)).equals("Pressed on day 42 · vintage in 1 day"), english(LuxuryRecipes.tooltip(made, 44)));
		helper.assertTrue(english(LuxuryRecipes.tooltip(made, 46)).equals("Pressed on day 42 · vintage"), english(LuxuryRecipes.tooltip(made, 46)));
		helper.succeed();
	}

	/** {@code text} in English, from the mod's own en_us.json (the server has no mod language). */
	private static String english(Component text) {
		JsonObject lang;
		try (InputStream in = Files.newInputStream(FabricLoader.getInstance().getModContainer(AliveWorkplace.MOD_ID).orElseThrow()
				.findPath("assets/aliveworkplace/lang/en_us.json").orElseThrow())) {
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			throw new GameTestAssertException("can't read en_us.json: " + e);
		}
		TranslatableContents contents = (TranslatableContents) text.getContents();
		String out = lang.get(contents.getKey()).getAsString();
		for (Object arg : contents.getArgs()) {
			out = out.replaceFirst("%s", String.valueOf(arg instanceof Component c ? c.getString() : arg));
		}
		return out;
	}
}
