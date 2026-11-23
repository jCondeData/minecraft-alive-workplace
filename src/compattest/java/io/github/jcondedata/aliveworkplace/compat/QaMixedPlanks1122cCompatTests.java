package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane c, 2026-11-22) for B1378, from its spec, not the code: with Chipped and Carved Wood, Carved Wood's mixed
 * crafting table recipe makes Minecraft's crafting table from any 2x2 of planks no other recipe takes, mixed kinds
 * included. Expected: "whenever the game's table makes Minecraft's crafting table from the planks there, nothing is
 * short". QaJobsShops1117cBugCompatTests checks seven fixed mixes; these go over every mix of six kinds of plank (four
 * and five planks), the boundary of three planks (and whether the named shortfall is enough), what the plan takes, and
 * the player's way: a Carpenter makes a builder's crafting table from the mixed planks in the builder's chest, across a
 * save and reload of the Carpenter mid-job.
 */
public class QaMixedPlanks1122cCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos BENCH_CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos TABLE_AT = new BlockPos(11, 2, 11);
	private static final BlockPos CARPENTER_TABLE = new BlockPos(8, 2, 2);
	private static final BlockPos TEMPLATE_SOURCE = new BlockPos(14, 2, 14);

	// ---------------------------------------------------------------------------------------------- the planner alone

	/**
	 * Every multiset of four planks from six kinds (oak, spruce, acacia, crimson, bamboo, a Chipped plank): 126 chests.
	 * The planner lacks nothing for one crafting table exactly when the game's own table makes minecraft:crafting_table
	 * from some 2x2 arrangement of those four; and when it lacks nothing, the plan takes exactly those four planks and
	 * every crafting-table step's grid is one the game's table makes Minecraft's crafting table of.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '200' '"qaMixedPlanks1122cFour"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200, batch = "qaMixedPlanks1122cFour")
	public void everyFourPlankMixLacksNothingExactlyWhenTheGamesTableMakesATable(GameTestHelper helper) {
		helper.runAfterDelay(1, () -> {
			checkAllMixes(helper, kinds(), 4);
			helper.succeed();
		});
	}

	/**
	 * Every multiset of five planks from four kinds (oak, acacia, bamboo, a Chipped plank): 56 chests, among them four
	 * acacia and one other (four acacia alone make Carved Wood's acacia table; three and the other make Minecraft's).
	 * The planner lacks nothing exactly when some four of the five make minecraft:crafting_table at the game's table, and
	 * the plan takes four of them that do.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '200' '"qaMixedPlanks1122cFive"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200, batch = "qaMixedPlanks1122cFive")
	public void everyFivePlankMixLacksNothingExactlyWhenSomeFourMakeATable(GameTestHelper helper) {
		helper.runAfterDelay(1, () -> {
			List<Item> k = kinds();
			checkAllMixes(helper, List.of(Items.OAK_PLANKS, Items.ACACIA_PLANKS, Items.BAMBOO_PLANKS, k.get(k.size() - 1)), 5);
			helper.succeed();
		});
	}

	/**
	 * The boundary from below: three planks of a mix whose four make Minecraft's crafting table (2 oak and 1 acacia,
	 * 2 Chipped and 1 oak, oak, spruce and crimson, 3 acacia) lack something, and with what they lack added to the
	 * chest nothing is short any more (the named shortfall is enough, so a player who brings it isn't told he's short
	 * again: 3 acacia and 1 more acacia would make the acacia table). How much is named is
	 * QaMixedPlanks1122cBugCompatTests's (it overstates).
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '100' '"qaMixedPlanks1122cThree"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100, batch = "qaMixedPlanks1122cThree")
	public void threePlanksLackSomethingAndWhatTheyLackIsEnough(GameTestHelper helper) {
		helper.runAfterDelay(1, () -> {
			ServerLevel level = helper.getLevel();
			Item chipped = kinds().get(kinds().size() - 1);
			List<Item[]> threes = List.of(
				new Item[] {Items.OAK_PLANKS, Items.OAK_PLANKS, Items.ACACIA_PLANKS},
				new Item[] {chipped, chipped, Items.OAK_PLANKS},
				new Item[] {Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.CRIMSON_PLANKS},
				new Item[] {Items.ACACIA_PLANKS, Items.ACACIA_PLANKS, Items.ACACIA_PLANKS});
			List<String> wrong = new ArrayList<>();
			List<String> seen = new ArrayList<>();
			for (Item[] three : threes) {
				Map<Item, Long> chest = new LinkedHashMap<>();
				for (Item i : three) {
					chest.merge(i, 1L, Long::sum);
				}
				Crafting.freshPlanning(level);
				Map<Item, Integer> lack = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CRAFTING_TABLE, 1, chest);
				String what = describe(chest) + " lacks " + describe(lack);
				if (lack.isEmpty()) {
					wrong.add(what + ": three planks can't make a crafting table");
					seen.add(what);
					continue;
				}
				Map<Item, Long> more = new LinkedHashMap<>(chest);
				lack.forEach((item, n) -> more.merge(item, (long) n, Long::sum));
				Crafting.freshPlanning(level);
				Map<Item, Integer> after = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CRAFTING_TABLE, 1, more);
				what += "; with that added " + describe(more) + " lacks " + describe(after);
				if (!after.isEmpty()) {
					wrong.add(what + ": the named shortfall isn't enough");
				}
				seen.add(what);
			}
			helper.assertTrue(wrong.isEmpty(), "three planks: " + wrong);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------------------------------------- the player's way

	/**
	 * A builder whose build is one crafting table, his chest holding 2 oak and 2 acacia planks beside cobblestone and
	 * dirt; a Carpenter at a crafting table nearby. The table is made and built, the chest loses exactly those four planks
	 * and keeps the rest, nothing of Carved Wood's is made, and the builder's line never says he is short of planks or logs.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaMixedPlanks1122cCarpenter"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaMixedPlanks1122cCarpenter")
	public void aCarpenterMakesTheBuildersTableFromTwoOakAndTwoAcacia(GameTestHelper helper) {
		carpenterBuildsTable(helper, false, new ItemStack(Items.OAK_PLANKS, 2), new ItemStack(Items.ACACIA_PLANKS, 2),
			new ItemStack(Items.COBBLESTONE, 6), new ItemStack(Items.DIRT, 3));
	}

	/**
	 * As above with 4 acacia planks and 1 Chipped plank: four acacia alone make Carved Wood's acacia table, so the
	 * Carpenter must take three acacia and the Chipped plank (one acacia left), and Minecraft's crafting table is built.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaMixedPlanks1122cAcacia"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaMixedPlanks1122cAcacia")
	public void fourAcaciaAndAChippedPlankMakeMinecraftsTableNotCarvedWoods(GameTestHelper helper) {
		Item chipped = kinds().get(kinds().size() - 1);
		helper.assertTrue(gameMakesTable(helper.getLevel(), List.of(Items.ACACIA_PLANKS, Items.ACACIA_PLANKS, Items.ACACIA_PLANKS, chipped)),
			"setup: the game's table makes no crafting table from 3 acacia and 1 " + id(chipped));
		carpenterBuildsTable(helper, false, new ItemStack(Items.ACACIA_PLANKS, 4), new ItemStack(chipped, 1), new ItemStack(Items.DIRT, 3));
	}

	/**
	 * 3 Chipped planks and 1 oak in the builder's chest; the Carpenter is saved and loaded again as soon as he has a job
	 * planned. He still makes Minecraft's crafting table from exactly those four, and it's built.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaMixedPlanks1122cReload"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaMixedPlanks1122cReload")
	public void aCarpenterReloadedMidJobStillMakesTheTableFromChippedAndOak(GameTestHelper helper) {
		Item chipped = kinds().get(kinds().size() - 1);
		carpenterBuildsTable(helper, true, new ItemStack(chipped, 3), new ItemStack(Items.OAK_PLANKS, 1), new ItemStack(Items.COBBLESTONE, 5));
	}

	private static void carpenterBuildsTable(GameTestHelper helper, boolean reload, ItemStack... stock) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		FurnicrafterCompatTests.ownStoreOnly(helper);
		ServerLevel level = helper.getLevel();
		Village.RADIUS = 48;
		PartnerShowsCompatTests.after(helper, () -> Village.RADIUS = 0);
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		ResourceLocation build = template(helper, "qa_mixed_planks_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(BENCH).getX());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(BENCH_CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(BENCH_CHEST);
		Map<Item, Integer> before = new LinkedHashMap<>();
		int planksBefore = 0;
		for (int i = 0; i < stock.length; i++) {
			chest.setItem(i, stock[i].copy());
			before.merge(stock[i].getItem(), stock[i].getCount(), Integer::sum);
			planksBefore += stock[i].is(ItemTags.PLANKS) ? stock[i].getCount() : 0;
		}
		final int planks = planksBefore;
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, build,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(TABLE_AT), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null, "the table's build didn't start");
		helper.setBlock(CARPENTER_TABLE, Blocks.CRAFTING_TABLE);
		Villager first = helper.spawn(EntityType.VILLAGER, CARPENTER_TABLE.south());
		Stations.assign(level, first, helper.absolutePos(CARPENTER_TABLE), ModVillagers.CARPENTER);
		AtomicReference<Villager> carpenter = new AtomicReference<>(first);
		AtomicBoolean reloaded = new AtomicBoolean(!reload);
		List<String> shortLines = new ArrayList<>();
		BlockPos benchAbs = helper.absolutePos(BENCH);
		helper.onEachTick(() -> {
			if (site.status() == BuildSite.Status.WAITING_FOR_MATERIALS) {
				Component line = JobShortages.line(level, site.id(), site.missing(), benchAbs);
				String text = line == null ? "" : line.getString();
				if ((text.contains("Planks") || text.contains("Log")) && shortLines.size() < 5 && !shortLines.contains(text)) {
					shortLines.add(text);
				}
			}
			if (!reloaded.get() && ModAttachments.CRAFTER_JOB.get(carpenter.get()) != null) {
				reloaded.set(true);
				carpenter.set(reloadVillager(level, carpenter.get()));
			}
		});
		helper.succeedWhen(() -> {
			Villager c = carpenter.get();
			helper.assertTrue(shortLines.isEmpty(), "the builder's line said he was short of planks or logs: " + shortLines);
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.status() + " " + site.stage()
				+ " missing " + site.missing() + "; the Carpenter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(c, 0) + "; chest " + contents(chest));
			helper.assertBlockPresent(Blocks.CRAFTING_TABLE, TABLE_AT);
			helper.assertTrue(reloaded.get(), "the Carpenter was never reloaded mid-job (he never had a job planned)");
			int planksAfter = 0;
			for (int slot = 0; slot < chest.getContainerSize(); slot++) {
				ItemStack s = chest.getItem(slot);
				planksAfter += s.is(ItemTags.PLANKS) ? s.getCount() : 0;
				helper.assertTrue(s.isEmpty() || !BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace().equals("carved_wood"),
					"something of Carved Wood's is in the chest: " + contents(chest));
			}
			helper.assertTrue(planksAfter == planks - 4, "the chest had " + planks + " planks and has " + planksAfter + " (" + contents(chest)
				+ "): a crafting table takes exactly 4");
			for (Map.Entry<Item, Integer> e : before.entrySet()) {
				if (!new ItemStack(e.getKey()).is(ItemTags.PLANKS)) {
					helper.assertTrue(chest.countItem(e.getKey()) == e.getValue(), id(e.getKey()) + ": " + e.getValue() + " before, "
						+ chest.countItem(e.getKey()) + " after (" + contents(chest) + ")");
				}
			}
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(c, 0) >= 1, "the table wasn't the Carpenter's: he made "
				+ ModAttachments.ITEMS_CRAFTED.getOrElse(c, 0));
		});
	}

	// ---------------------------------------------------------------------------------------------- helpers

	static void checkAllMixes(GameTestHelper helper, List<Item> kinds, int size) {
		ServerLevel level = helper.getLevel();
		List<List<Item>> mixes = new ArrayList<>();
		multisets(kinds, size, 0, new ArrayList<>(), mixes);
		List<String> wrong = new ArrayList<>();
		int tables = 0;
		int refused = 0;
		for (List<Item> mix : mixes) {
			Map<Item, Long> chest = new LinkedHashMap<>();
			for (Item i : mix) {
				chest.merge(i, 1L, Long::sum);
			}
			boolean game = someFourMakeATable(level, mix);
			tables += game ? 1 : 0;
			refused += game ? 0 : 1;
			Crafting.freshPlanning(level);
			Map<Item, Integer> lack = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CRAFTING_TABLE, 1, chest);
			String what = describe(chest) + " -> game " + (game ? "makes" : "doesn't make") + " a crafting table, planner lacks " + describe(lack);
			if (game != lack.isEmpty()) {
				wrong.add(what);
				continue;
			}
			if (!game) {
				continue;
			}
			Crafting.freshPlanning(level);
			Crafting.Plan plan = Crafting.plan(level, Crafting.Kind.CRAFTING, Items.CRAFTING_TABLE, 1, chest);
			if (plan == null) {
				wrong.add(what + ", but no plan");
				continue;
			}
			List<Item> taken = new ArrayList<>();
			plan.takes().forEach((item, n) -> {
				for (int i = 0; i < n; i++) {
					taken.add(item);
				}
			});
			boolean within = plan.takes().entrySet().stream().allMatch(e -> chest.getOrDefault(e.getKey(), 0L) >= e.getValue());
			if (taken.size() != 4 || !within || !gameMakesTable(level, taken)) {
				wrong.add(what + ", but the plan takes " + describe(plan.takes()));
				continue;
			}
			for (Crafting.Step step : plan.steps()) {
				if (!step.out().is(Items.CRAFTING_TABLE)) {
					continue;
				}
				List<Item> grid = new ArrayList<>();
				step.in().forEach((item, n) -> {
					for (int i = 0; i < n; i++) {
						grid.add(item);
					}
				});
				if (!gameMakesTable(level, grid)) {
					wrong.add(what + ", but a step's grid is " + describe(step.in()));
				}
			}
		}
		helper.assertTrue(tables >= 3 && refused >= 1, "setup: of " + mixes.size() + " mixes the game made a table of " + tables + " and refused "
			+ refused + ": the test proves nothing");
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " of " + mixes.size() + " mixes of " + size + " planks wrong (" + tables
			+ " make a table at the game's table): " + (wrong.size() > 4 ? wrong.subList(0, 4) + " ..." : wrong));
	}

	private static void multisets(List<Item> kinds, int left, int from, List<Item> current, List<List<Item>> out) {
		if (left == 0) {
			out.add(List.copyOf(current));
			return;
		}
		for (int i = from; i < kinds.size(); i++) {
			current.add(kinds.get(i));
			multisets(kinds, left - 1, i, current, out);
			current.remove(current.size() - 1);
		}
	}

	/** Oak, spruce, acacia, crimson, bamboo and a Chipped plank (an acacia mosaic one when there is one). */
	static List<Item> kinds() {
		Item chipped = null;
		for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.PLANKS)) {
			ResourceLocation key = BuiltInRegistries.ITEM.getKey(h.value());
			if (!key.getNamespace().equals("chipped")) {
				continue;
			}
			if (chipped == null || key.getPath().contains("acacia") && key.getPath().contains("mosaic")
				&& !BuiltInRegistries.ITEM.getKey(chipped).getPath().contains("mosaic")) {
				chipped = h.value();
			}
		}
		if (chipped == null) {
			throw new GameTestAssertException("setup: no Chipped planks in the planks tag");
		}
		return List.of(Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.ACACIA_PLANKS, Items.CRIMSON_PLANKS, Items.BAMBOO_PLANKS, chipped);
	}

	/** Whether some four of {@code planks} make minecraft:crafting_table at the game's table, in some 2x2 arrangement. */
	private static boolean someFourMakeATable(ServerLevel level, List<Item> planks) {
		if (planks.size() == 4) {
			return gameMakesTable(level, planks);
		}
		for (int skip = 0; skip < planks.size(); skip++) {
			List<Item> four = new ArrayList<>(planks);
			four.remove(skip);
			if (someFourMakeATable(level, four)) {
				return true;
			}
		}
		return false;
	}

	/** Whether the game's own table makes minecraft:crafting_table from these four in any 2x2 arrangement. */
	private static boolean gameMakesTable(ServerLevel level, List<Item> four) {
		if (four.size() != 4) {
			return false;
		}
		Set<List<Item>> orders = new HashSet<>();
		permute(new ArrayList<>(four), 0, orders);
		for (List<Item> order : orders) {
			List<ItemStack> grid = order.stream().map(ItemStack::new).toList();
			CraftingInput input = CraftingInput.of(2, 2, grid);
			boolean table = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level)
				.map(r -> r.value().assemble(input, level.registryAccess()).is(Items.CRAFTING_TABLE)).orElse(false);
			if (table) {
				return true;
			}
		}
		return false;
	}

	private static void permute(List<Item> items, int k, Set<List<Item>> out) {
		if (k == items.size()) {
			out.add(List.copyOf(items));
			return;
		}
		for (int i = k; i < items.size(); i++) {
			java.util.Collections.swap(items, k, i);
			permute(items, k + 1, out);
			java.util.Collections.swap(items, k, i);
		}
	}

	private static Villager reloadVillager(ServerLevel level, Villager villager) {
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		villager.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		if (copy == null) {
			throw new GameTestAssertException("no villager made");
		}
		copy.load(tag);
		level.addFreshEntity(copy);
		Village.forget(copy);
		return copy;
	}

	/** A one-block build of a crafting table, saved as a template from the corner of the area and cleared again. */
	private static ResourceLocation template(GameTestHelper helper, String name) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(TEMPLATE_SOURCE, Blocks.CRAFTING_TABLE);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_compat", name);
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(TEMPLATE_SOURCE), new Vec3i(1, 1, 1), false,
			Blocks.STRUCTURE_VOID);
		helper.setBlock(TEMPLATE_SOURCE, Blocks.AIR);
		return id;
	}

	private static String contents(Container chest) {
		Map<Item, Integer> all = new LinkedHashMap<>();
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			ItemStack s = chest.getItem(slot);
			if (!s.isEmpty()) {
				all.merge(s.getItem(), s.getCount(), Integer::sum);
			}
		}
		return describe(all);
	}

	static String describe(Map<Item, ? extends Number> items) {
		return items.entrySet().stream().map(e -> e.getValue() + " " + id(e.getKey())).toList().toString();
	}

	private static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}
}
