package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.test.FreshPlanning;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-b-1122-2138), B1378's boundaries from its spec ("whenever the game's table makes Minecraft's crafting table
 * from the planks there, nothing is short"), with Chipped and Carved Wood loaded: the game's own crafting table decides
 * every case, and the planner must agree both ways, including with four kinds of plank, one too few, and two tables'
 * worth that only a mix makes. And through a real builder: short of a crafting table with 2 Chipped and 2 oak planks in
 * his chest, the crafting table is made from exactly those and built.
 */
public class QaMixedPlanks1122b2CompatTests implements FreshPlanning {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos WORKBENCH = new BlockPos(14, 2, 2);
	private static final BlockPos SPOT = new BlockPos(6, 2, 9);

	private static List<Item> chippedPlanks() {
		List<Item> out = new ArrayList<>();
		for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.PLANKS)) {
			if (BuiltInRegistries.ITEM.getKey(h.value()).getNamespace().equals("chipped")) {
				out.add(h.value());
			}
		}
		if (out.size() < 2) {
			throw new GameTestAssertException("setup: fewer than two Chipped planks in the planks tag");
		}
		return out;
	}

	private static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	/** What the game's own crafting table makes of these in a 2x2 grid, in order, empty slots for AIR (empty: nothing). */
	private static ItemStack gameMakes(ServerLevel level, Item... four) {
		List<ItemStack> grid = new ArrayList<>();
		for (Item i : four) {
			grid.add(i == Items.AIR ? ItemStack.EMPTY : new ItemStack(i));
		}
		CraftingInput input = CraftingInput.of(2, 2, grid);
		Optional<RecipeHolder<CraftingRecipe>> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
		return recipe.map(r -> r.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
	}

	private static Map<Item, Long> chest(Item... items) {
		Map<Item, Long> chest = new LinkedHashMap<>();
		for (Item i : items) {
			chest.merge(i, 1L, Long::sum);
		}
		return chest;
	}

	private static Map<Item, Integer> lacking(ServerLevel level, int count, Map<Item, Long> chest) {
		Crafting.freshPlanning(level);
		return Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CRAFTING_TABLE, count, chest);
	}

	/**
	 * One plank of each of four kinds (Chipped, oak, spruce, birch; and two Chipped kinds with oak and acacia): when the
	 * game's table makes a crafting table of them, the planner lacks nothing, and a plan takes exactly those four.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '100' '"qaMixedPlanks1122b2Four"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100, batch = "qaMixedPlanks1122b2Four")
	public void onePlankOfEachOfFourKindsAgreesWithTheGamesTable(GameTestHelper helper) {
		helper.runAfterDelay(1, () -> {
			ServerLevel level = helper.getLevel();
			List<Item> chipped = chippedPlanks();
			Item a = chipped.get(0);
			Item b = chipped.get(chipped.size() - 1);
			List<Item[]> mixes = List.of(
				new Item[] {a, Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS},
				new Item[] {Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.ACACIA_PLANKS},
				new Item[] {a, b, Items.OAK_PLANKS, Items.ACACIA_PLANKS},
				new Item[] {Items.ACACIA_PLANKS, Items.ACACIA_PLANKS, Items.ACACIA_PLANKS, Items.OAK_PLANKS});
			List<String> wrong = new ArrayList<>();
			List<String> seen = new ArrayList<>();
			int tables = 0;
			for (Item[] mix : mixes) {
				Map<Item, Long> chest = chest(mix);
				ItemStack made = gameMakes(level, mix);
				boolean table = made.is(Items.CRAFTING_TABLE);
				tables += table ? 1 : 0;
				Map<Item, Integer> lack = lacking(level, 1, chest);
				String what = chest + " -> game: " + (made.isEmpty() ? "nothing" : id(made.getItem())) + ", planner lacks " + lack;
				seen.add(what);
				if (table != lack.isEmpty()) {
					wrong.add(what);
				}
				if (table) {
					Crafting.freshPlanning(level);
					Crafting.Plan plan = Crafting.plan(level, Crafting.Kind.CRAFTING, Items.CRAFTING_TABLE, 1, chest);
					Map<Item, Integer> want = new LinkedHashMap<>();
					chest.forEach((k, v) -> want.put(k, v.intValue()));
					if (plan == null || !plan.takes().equals(want)) {
						wrong.add(what + ": plan takes " + (plan == null ? "nothing (no plan)" : plan.takes()));
					}
				}
			}
			helper.assertTrue(tables >= 2, "setup: the game's table made Minecraft's crafting table from only " + tables + " of these mixes: " + seen);
			helper.assertTrue(wrong.isEmpty(), "the planner and the game's table disagree: " + wrong + " (all: " + seen + ")");
			helper.succeed();
		});
	}

	/**
	 * One too few: three planks of any mix make no crafting table at the game's table (3 in a 2x2, one slot empty), so the
	 * planner must lack something; three oak planks are exactly one plank short. Also 7 planks for two tables: short.
	 * (How much a mixed three is short of is QaMixedPlanks1122b2BugCompatTests', on tests/mixed-planks-1122b2.)
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '100' '"qaMixedPlanks1122b2Short"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100, batch = "qaMixedPlanks1122b2Short")
	public void threePlanksAreShortOfOneTableAndSevenOfTwo(GameTestHelper helper) {
		helper.runAfterDelay(1, () -> {
			ServerLevel level = helper.getLevel();
			Item a = chippedPlanks().get(0);
			List<Item[]> threes = List.of(
				new Item[] {a, a, Items.OAK_PLANKS},
				new Item[] {Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS},
				new Item[] {Items.OAK_PLANKS, Items.ACACIA_PLANKS, Items.BIRCH_PLANKS});
			List<String> wrong = new ArrayList<>();
			for (Item[] three : threes) {
				ItemStack made = gameMakes(level, three[0], three[1], three[2], Items.AIR);
				helper.assertFalse(made.is(Items.CRAFTING_TABLE), "setup: the game's table made a crafting table from three planks: " + List.of(three));
				Map<Item, Integer> lack = lacking(level, 1, chest(three));
				int short_ = lack.values().stream().mapToInt(Integer::intValue).sum();
				boolean oakOnly = chest(three).keySet().equals(java.util.Set.of(Items.OAK_PLANKS));
				if (short_ < 1 || (oakOnly && short_ != 1)) {
					wrong.add(chest(three) + ": the planner lacks " + lack + (oakOnly ? " (expected 1 Oak Planks short)" : " (expected something short)"));
				}
			}
			Map<Item, Long> seven = chest(a, a, Items.OAK_PLANKS, Items.OAK_PLANKS, Items.ACACIA_PLANKS, Items.ACACIA_PLANKS, Items.BIRCH_PLANKS);
			Map<Item, Integer> lackSeven = lacking(level, 2, seven);
			if (lackSeven.isEmpty()) {
				wrong.add("7 planks " + seven + " for two crafting tables: the planner lacks nothing");
			}
			helper.assertTrue(wrong.isEmpty(), "short of planks, the planner says otherwise: " + wrong);
			helper.succeed();
		});
	}

	/**
	 * Two tables' worth only a mix makes: 4 acacia and 4 oak planks. Four acacia make Carved Wood's own table, not
	 * Minecraft's, but 2 acacia + 2 oak twice make two of Minecraft's (checked at the game's table first): nothing short,
	 * and a plan for two takes all eight. And 4 acacia alone for one: short.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '100' '"qaMixedPlanks1122b2Two"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100, batch = "qaMixedPlanks1122b2Two")
	public void twoTablesFromFourAcaciaAndFourOakNeedTheMix(GameTestHelper helper) {
		helper.runAfterDelay(1, () -> {
			ServerLevel level = helper.getLevel();
			Item ac = Items.ACACIA_PLANKS;
			Item oak = Items.OAK_PLANKS;
			helper.assertTrue(gameMakes(level, ac, ac, oak, oak).is(Items.CRAFTING_TABLE), "setup: 2 acacia + 2 oak make " + gameMakes(level, ac, ac, oak, oak));
			ItemStack acacia = gameMakes(level, ac, ac, ac, ac);
			Map<Item, Integer> lackAcacia = lacking(level, 1, chest(ac, ac, ac, ac));
			helper.assertTrue(acacia.is(Items.CRAFTING_TABLE) == lackAcacia.isEmpty(),
				"4 acacia planks: the game makes " + acacia + ", the planner lacks " + lackAcacia);
			Map<Item, Long> eight = chest(ac, ac, ac, ac, oak, oak, oak, oak);
			Map<Item, Integer> lack = lacking(level, 2, eight);
			helper.assertTrue(lack.isEmpty(), "4 acacia + 4 oak planks make two crafting tables at the game's table (2+2 twice), but the planner lacks " + lack);
			Crafting.freshPlanning(level);
			Crafting.Plan plan = Crafting.plan(level, Crafting.Kind.CRAFTING, Items.CRAFTING_TABLE, 2, eight);
			helper.assertTrue(plan != null && plan.count() == 2 && plan.takes().equals(Map.of(ac, 4, oak, 4)),
				"the plan for two crafting tables from 4 acacia + 4 oak: " + (plan == null ? "none" : plan.takes() + " steps " + plan.steps()));
			helper.succeed();
		});
	}

	// --- a real builder and crafter -----------------------------------------------------------------------------------

	private static int everywhere(GameTestHelper helper, Container chest, List<Villager> villagers, Item item) {
		int n = chest.countItem(item);
		for (Villager v : villagers) {
			n += v.getInventory().countItem(item);
			for (ItemStack s : ModAttachments.BUILDER_BAG.getOrCreate(v).stacks()) {
				if (s.is(item)) {
					n += s.getCount();
				}
			}
		}
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> e.getItem().is(item))) {
			n += e.getItem().getCount();
		}
		return n;
	}

	private static String contents(Container chest) {
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (!chest.getItem(i).isEmpty()) {
				s.append(chest.getItem(i)).append(' ');
			}
		}
		return s.toString().trim();
	}

	/**
	 * A builder short of one crafting table (a blueprint of just that block), his chest holding 2 Chipped planks and 2
	 * oak planks (the game's table makes a crafting table of them; checked first): the village's crafter makes it from
	 * those, the builder builds it, and no plank is left over nor a spare crafting table anywhere.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaMixedPlanks1122b2Builder"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaMixedPlanks1122b2Builder")
	public void aBuilderShortOfACraftingTableGetsItMadeFromChippedAndOakPlanks(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		FurnicrafterCompatTests.ownStoreOnly(helper);
		ServerLevel level = helper.getLevel();
		int radius = Village.RADIUS;
		Village.RADIUS = 48;
		PartnerShowsCompatTests.after(helper, () -> Village.RADIUS = radius);
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());

		Item chipped = chippedPlanks().get(0);
		helper.assertTrue(gameMakes(level, chipped, chipped, Items.OAK_PLANKS, Items.OAK_PLANKS).is(Items.CRAFTING_TABLE),
			"setup: the game's table doesn't make a crafting table of 2 " + id(chipped) + " and 2 oak planks");
		CraftJobs.Job maker = CraftJobs.maker(level, Items.CRAFTING_TABLE);
		helper.assertTrue(maker == CraftJobs.Job.CARPENTER, "setup: the crafting table is made by " + maker + ", not the Carpenter");

		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(chipped, 2));
		chest.setItem(1, new ItemStack(Items.OAK_PLANKS, 2));

		// The blueprint: one crafting table, drawn where it's to be built and taken away.
		helper.setBlock(SPOT, Blocks.CRAFTING_TABLE);
		ResourceLocation blueprint = ResourceLocation.fromNamespaceAndPath("aliveworkplace_compat",
			"qa_mixedplanks_table_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(SPOT).getX());
		level.getStructureManager().getOrCreate(blueprint).fillFromWorld(level, helper.absolutePos(SPOT), new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(SPOT, Blocks.AIR);

		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, blueprint, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(SPOT),
			Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null && site.plan(level) != null, "the build didn't start");
		BuildPlan plan = site.plan(level);

		helper.setBlock(WORKBENCH, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, WORKBENCH.south());
		Jobs.employ(level, carpenter, helper.absolutePos(WORKBENCH), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);

		List<Villager> both = List.of(builder, carpenter);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.status() + " missing " + site.missing()
				+ "; the Carpenter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) + "; chest " + contents(chest));
			helper.assertTrue(plan.unfinished(level).isEmpty(), plan.unfinished(level).size() + " block(s) wrong after the build");
			helper.assertTrue(helper.getBlockState(SPOT).is(Blocks.CRAFTING_TABLE), "no crafting table where the blueprint has one: " + helper.getBlockState(SPOT));
			for (Item used : List.of(chipped, Items.OAK_PLANKS)) {
				int left = everywhere(helper, chest, both, used);
				helper.assertTrue(left == 0, left + " " + id(used) + " left unused (chest " + contents(chest) + ")");
			}
			int spare = everywhere(helper, chest, both, Items.CRAFTING_TABLE);
			helper.assertTrue(spare == 0, spare + " spare crafting table(s) made (chest " + contents(chest) + ")");
		});
	}
}
