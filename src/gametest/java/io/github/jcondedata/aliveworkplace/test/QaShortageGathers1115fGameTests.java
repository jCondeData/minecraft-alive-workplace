package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (qa-lane-b-1115-1237) past B1278 and B1277's fix, written from crafter.md: "The line names what the chest is short of,
 * not some other way to make it ... It goes further down only when that uses something that's there", never a Silk-Touch
 * ore (B489) and never a way that only unpacks a storage block. Boundaries from both sides (0 vs 1 rabbit hide, 1 leather,
 * a slime block there or not), other chains of the same kind (a lead's slimeball, a painting's wool), the exact sentence
 * through a builder and his Librarian or Tinkerer, and a save and reload of the site.
 */
public class QaShortageGathers1115fGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final TagKey<Item> ORES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ores"));
	private static final BlockPos STATION = new BlockPos(14, 2, 2);

	/** A book from 3 paper: 0 hide and 0 leather names 1 Leather; 1 hide names the 3 more hides; 4 hides or 1 leather lack nothing. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaSG1115fPABookNamesLeatherOrTheRestOfTheHideByWhatIsThere"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaSG1115fPABookNamesLeatherOrTheRestOfTheHideByWhatIsThere")
	public void aBookNamesLeatherOrTheRestOfTheHideByWhatIsThere(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		expect(wrong, "3 paper", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.BOOK, 1, Map.of(Items.PAPER, 3L)), Map.of(Items.LEATHER, 1));
		expect(wrong, "3 paper, 1 hide", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.BOOK, 1, Map.of(Items.PAPER, 3L, Items.RABBIT_HIDE, 1L)),
			Map.of(Items.RABBIT_HIDE, 3));
		expect(wrong, "3 paper, 4 hides", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.BOOK, 1, Map.of(Items.PAPER, 3L, Items.RABBIT_HIDE, 4L)),
			Map.of());
		expect(wrong, "3 paper, 1 leather", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.BOOK, 1, Map.of(Items.PAPER, 3L, Items.LEATHER, 1L)),
			Map.of());
		// a book from nothing: the cane a player gathers and the leather, never the hide
		Map<Item, Integer> fromNothing = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.BOOK, 1, Map.of());
		if (!fromNothing.containsKey(Items.LEATHER) || fromNothing.containsKey(Items.RABBIT_HIDE)) {
			wrong.add("a book from nothing lacks " + fromNothing + ", expected Leather named and no Rabbit Hide");
		}
		helper.assertTrue(wrong.isEmpty(), "a book's line: " + wrong);
		helper.succeed();
	}

	/** Three bookshelves (9 books) from cane and planks: 0 leather names 9 Leather; 1 leather 8 Leather; 1 hide 35 more hides. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaSG1115fPBookshelvesCountTheLeatherOrTheHideThere"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaSG1115fPBookshelvesCountTheLeatherOrTheHideThere")
	public void bookshelvesCountTheLeatherOrTheHideThere(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		Map<Item, Long> base = Map.of(Items.SUGAR_CANE, 27L, Items.OAK_PLANKS, 18L);
		expect(wrong, "no leather", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.BOOKSHELF, 3, base), Map.of(Items.LEATHER, 9));
		expect(wrong, "1 leather", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.BOOKSHELF, 3, with(base, Items.LEATHER, 1L)), Map.of(Items.LEATHER, 8));
		expect(wrong, "1 hide", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.BOOKSHELF, 3, with(base, Items.RABBIT_HIDE, 1L)),
			Map.of(Items.RABBIT_HIDE, 35));
		expect(wrong, "9 leather", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.BOOKSHELF, 3, with(base, Items.LEATHER, 9L)), Map.of());
		helper.assertTrue(wrong.isEmpty(), "three bookshelves' line: " + wrong);
		helper.succeed();
	}

	/**
	 * Other things a leather goes into, with all else there and no leather: two item frames, a leather cap from nothing and
	 * a book and quill. Each names Leather, never Rabbit Hide.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaSG1115fPOtherLeatherThingsNameLeatherNotHide"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaSG1115fPOtherLeatherThingsNameLeatherNotHide")
	public void otherLeatherThingsNameLeatherNotHide(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		expect(wrong, "an item frame from 8 sticks", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.ITEM_FRAME, 2, Map.of(Items.STICK, 16L)),
			Map.of(Items.LEATHER, 2));
		expect(wrong, "a leather cap from nothing", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.LEATHER_HELMET, 1, Map.of()),
			Map.of(Items.LEATHER, 5));
		expect(wrong, "a writable book from an ink sac and a feather", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.WRITABLE_BOOK, 1,
			Map.of(Items.INK_SAC, 1L, Items.FEATHER, 1L, Items.PAPER, 3L)), Map.of(Items.LEATHER, 1));
		helper.assertTrue(wrong.isEmpty(), "leather things' lines: " + wrong);
		helper.succeed();
	}

	/**
	 * A slimeball's only way in the base game is unpacking a slime block. From nothing a sticky piston and a lead name the
	 * Slimeball, never a Slime Block; with a slime block there it is used (lacks nothing more than the piston's own).
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaSG1115fPASlimeballIsNamedUnlessASlimeBlockIsThere"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaSG1115fPASlimeballIsNamedUnlessASlimeBlockIsThere")
	public void aSlimeballIsNamedUnlessASlimeBlockIsThere(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		expect(wrong, "a sticky piston from a piston", Crafting.lacking(level, Crafting.Kind.WORKSHOP, Items.STICKY_PISTON, 1, Map.of(Items.PISTON, 1L)),
			Map.of(Items.SLIME_BALL, 1));
		expect(wrong, "a sticky piston from a piston and a slimeball", Crafting.lacking(level, Crafting.Kind.WORKSHOP, Items.STICKY_PISTON, 1,
			Map.of(Items.PISTON, 1L, Items.SLIME_BALL, 1L)), Map.of());
		expect(wrong, "a sticky piston from a piston and a slime block", Crafting.lacking(level, Crafting.Kind.WORKSHOP, Items.STICKY_PISTON, 1,
			Map.of(Items.PISTON, 1L, Items.SLIME_BLOCK, 1L)), Map.of());
		expect(wrong, "a lead from 4 string", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.LEAD, 2, Map.of(Items.STRING, 4L)),
			Map.of(Items.SLIME_BALL, 1));
		for (Item item : List.of(Items.STICKY_PISTON, Items.LEAD)) {
			Map<Item, Integer> lack = Crafting.lacking(level, Crafting.Kind.CRAFTING, item, 1, Map.of());
			if (!lack.containsKey(Items.SLIME_BALL) || lack.containsKey(Items.SLIME_BLOCK)) {
				wrong.add(item + " from nothing lacks " + lack + ", expected the Slimeball named and no Slime Block");
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the slimeball's line: " + wrong);
		helper.succeed();
	}

	/**
	 * The metals and redstone things a Tinkerer makes, from nothing: each names something, never an ore a player only gets
	 * with Silk Touch, and the metal blocks name the raw metal a player mines.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaSG1115fPTinkeringFromNothingNamesWhatAPlayerMines"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaSG1115fPTinkeringFromNothingNamesWhatAPlayerMines")
	public void tinkeringFromNothingNamesWhatAPlayerMines(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		expect(wrong, "a Block of Iron", Crafting.lacking(level, Crafting.Kind.WORKSHOP, Items.IRON_BLOCK, 1, Map.of(Items.COAL, 2L)), Map.of(Items.RAW_IRON, 9));
		expect(wrong, "a Block of Copper", Crafting.lacking(level, Crafting.Kind.WORKSHOP, Items.COPPER_BLOCK, 1, Map.of(Items.COAL, 2L)),
			Map.of(Items.RAW_COPPER, 9));
		expect(wrong, "a Block of Gold", Crafting.lacking(level, Crafting.Kind.WORKSHOP, Items.GOLD_BLOCK, 1, Map.of(Items.COAL, 2L)), Map.of(Items.RAW_GOLD, 9));
		for (Item item : List.of(Items.PISTON, Items.STICKY_PISTON, Items.REPEATER, Items.COMPARATOR, Items.REDSTONE_TORCH, Items.HOPPER,
			Items.RAIL, Items.POWERED_RAIL, Items.COMPASS, Items.CLOCK, Items.LANTERN, Items.CHAIN, Items.IRON_BARS)) {
			Map<Item, Integer> lack = Crafting.lacking(level, Crafting.Kind.WORKSHOP, item, 1, Map.of());
			if (lack.isEmpty() || lack.keySet().stream().anyMatch(i -> i.builtInRegistryHolder().is(ORES))) {
				wrong.add(item + " lacks " + lack);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "from nothing, nothing named or an ore named: " + wrong);
		helper.succeed();
	}

	/** A smoker from a furnace: the recipe's 4 logs, of Minecraft's wood; a campfire from sticks and a coal its 3 logs. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaSG1115fPLogThingsNameTheRecipesLogs"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaSG1115fPLogThingsNameTheRecipesLogs")
	public void logThingsNameTheRecipesLogs(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		logs(wrong, "a smoker from a furnace", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.SMOKER, 1, Map.of(Items.FURNACE, 1L)), 4);
		logs(wrong, "a campfire from 3 sticks and a coal", Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CAMPFIRE, 1,
			Map.of(Items.STICK, 3L, Items.COAL, 1L)), 3);
		helper.assertTrue(wrong.isEmpty(), "log things: " + wrong);
		helper.succeed();
	}

	/**
	 * Through the player's entry point, then a save and reload: a Librarian at his lectern, a builder short of three
	 * bookshelves with the cane, the planks and one rabbit hide: "short of 35 Rabbit Hides" (going down uses the hide
	 * there); after a reload it reads the same; nothing is taken.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaShortGathers1115fHide"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaShortGathers1115fHide")
	public void aLibrarianWithOneHideNamesTheRestOfTheHide(GameTestHelper helper) {
		ItemStack[] items = {new ItemStack(Items.SUGAR_CANE, 27), new ItemStack(Items.OAK_PLANKS, 18), new ItemStack(Items.RABBIT_HIDE, 1)};
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.BOOKSHELF, items);
		Villager librarian = QaJobChainsGameTests.librarian(helper);
		String want = "Can't be made yet: short of 35 Rabbit Hides to make 3 Bookshelves";
		sayAcrossAReload(helper, a, want, librarian, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.SUGAR_CANE) == 27 && chest.countItem(Items.OAK_PLANKS) == 18 && chest.countItem(Items.RABBIT_HIDE) == 1,
				"the chest was touched");
		});
	}

	/**
	 * Through the player's entry point: a Tinkerer at his bench, a builder short of three sticky pistons with three pistons
	 * and a coal and no slime at all: exactly "short of 3 Slimeballs to make 3 Sticky Pistons", the whole time; nothing
	 * taken; after a reload it says the same.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaShortGathers1115fSlime"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaShortGathers1115fSlime")
	public void aTinkererWithNoSlimeNamesTheSlimeballs(GameTestHelper helper) {
		helper.assertTrue(CraftJobs.maker(helper.getLevel(), Items.STICKY_PISTON) == CraftJobs.Job.TINKERER,
			"setup: a sticky piston's maker is " + CraftJobs.maker(helper.getLevel(), Items.STICKY_PISTON) + ", expected the Tinkerer");
		ItemStack[] items = {new ItemStack(Items.PISTON, 3), new ItemStack(Items.COAL, 1)};
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.STICKY_PISTON, items);
		Villager tinkerer = tinkerer(helper);
		String want = "Can't be made yet: short of 3 Slimeballs to make 3 Sticky Pistons";
		sayAcrossAReload(helper, a, want, tinkerer, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.PISTON) == 3 && chest.countItem(Items.COAL) == 1, "the chest was touched");
		});
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------

	/**
	 * Every shortage line the builder says reads exactly {@code want}. Once said, the builder and the crafter are saved and
	 * loaded and the site's unsaved shortage list is dropped, as a server restart would; the line must come back the same,
	 * the crafter's bag stays empty and {@code after} holds.
	 */
	private static void sayAcrossAReload(GameTestHelper helper, QaJobChainsGameTests.Asker a, String want, Villager crafter, Runnable after) {
		AtomicBoolean reloaded = new AtomicBoolean();
		AtomicBoolean saidAgain = new AtomicBoolean();
		Villager[] maker = {crafter};
		helper.onEachTick(() -> {
			String got = read(helper, a.site());
			if (got == null || !got.startsWith("Can't be made yet")) {
				return;
			}
			helper.assertTrue(want.equals(got), "the builder's shortage line: " + got + " (want '" + want + "')" + (reloaded.get() ? " after the reload" : ""));
			if (!reloaded.get()) {
				reloaded.set(true);
				reload(helper.getLevel(), a.builder());
				maker[0] = reload(helper.getLevel(), crafter);
				live(helper, a.site()).setMissing(Map.of());
			} else if (live(helper, a.site()).missing().size() > 0) {
				saidAgain.set(true);
			}
		});
		helper.runAfterDelay(1400, () -> {
			helper.assertTrue(reloaded.get(), "no shortage line in 1400 ticks: " + read(helper, a.site()));
			helper.assertTrue(saidAgain.get(), "after the reload the line never came back: " + read(helper, a.site()));
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(maker[0]).isEmpty(), "the crafter took something");
			after.run();
			helper.succeed();
		});
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

	private static Villager tinkerer(GameTestHelper helper) {
		helper.setBlock(STATION, ModBlocks.TINKERS_BENCH);
		Villager v = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(STATION), ModVillagers.TINKERS_BENCH_POI, ModVillagers.TINKERER);
		return v;
	}

	private static BuildSite live(GameTestHelper helper, BuildSite site) {
		BuildSite live = BuildSiteManager.get(helper.getLevel()).get(site.id());
		helper.assertTrue(live != null, "the build ended");
		return live;
	}

	private static String read(GameTestHelper helper, BuildSite site) {
		BuildSite live = BuildSiteManager.get(helper.getLevel()).get(site.id());
		if (live == null) {
			return "(the build is done)";
		}
		Component line = JobShortages.line(helper.getLevel(), live.id(), live.missing());
		return line == null ? null : line.getString();
	}

	private static void logs(List<String> wrong, String what, Map<Item, Integer> got, int want) {
		boolean ok = got.size() == 1;
		for (Map.Entry<Item, Integer> e : got.entrySet()) {
			ok &= e.getKey().builtInRegistryHolder().is(net.minecraft.tags.ItemTags.LOGS) && e.getValue() == want;
		}
		if (!ok) {
			wrong.add(what + " lacks " + names(got) + ", expected " + want + " logs");
		}
	}

	private static Map<Item, Long> with(Map<Item, Long> base, Item item, long count) {
		Map<Item, Long> out = new java.util.HashMap<>(base);
		out.put(item, count);
		return out;
	}

	private static void expect(List<String> wrong, String what, Map<Item, Integer> got, Map<Item, Integer> want) {
		if (!got.equals(want)) {
			wrong.add(what + " lacks " + names(got) + ", expected " + names(want));
		}
	}

	private static String names(Map<Item, Integer> m) {
		StringBuilder sb = new StringBuilder("{");
		m.forEach((k, v) -> sb.append(BuiltInRegistries.ITEM.getKey(k).getPath()).append('=').append(v).append(' '));
		return sb.append('}').toString();
	}
}
