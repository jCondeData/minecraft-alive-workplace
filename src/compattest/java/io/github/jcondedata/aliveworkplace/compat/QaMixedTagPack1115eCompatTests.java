package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.test.FreshPlanning;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane E, 2026-11-15): B1293 (a recipe slot that takes any of a tag takes any mix of it, as a crafting table does;
 * when short, the line counts the whole tag; a crafter uses exactly what the plan says) with the whole pack loaded.
 * MixedTagSlotGameTests covers vanilla woods; here the planks come from Cobblemon (apricorn and saccharine planks are
 * in minecraft:planks, checked from its jar). In the pack, Carved Wood (default config) turns the base game's #planks
 * Chest and Crafting Table recipes off for one-wood ones, so those are made from a mix only when the game's own table
 * makes them of it (B1378: Carved Wood's mixed recipe makes Minecraft's crafting table of mixed planks); the Fletching Table and Create's Large Cogwheel (a shaft and two of #planks) still take any
 * planks. Create's Andesite Alloy is two recipes (two iron OR two zinc nuggets), never made from one of each. A real
 * Carpenter makes Large Cogwheels from a three-wood chest through his own work loop, also saved and loaded mid-plan,
 * and with a plank taken out of the chest under his plan. Nothing here is random.
 */
public class QaMixedTagPack1115eCompatTests implements FreshPlanning {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos WORKBENCH = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 9);

	private static Item item(String id) {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
		if (item == Items.AIR) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("not in the pack: " + id);
		}
		return item;
	}

	private static Item apricorn() {
		return item("cobblemon:apricorn_planks");
	}

	private static Item saccharine() {
		return item("cobblemon:saccharine_planks");
	}

	/** The pack's planks really are in minecraft:planks (else these tests prove nothing about a modded mix). */
	private static void planksAreInTheTag(GameTestHelper helper) {
		helper.assertTrue(apricorn().builtInRegistryHolder().is(ItemTags.PLANKS), "cobblemon:apricorn_planks isn't in #minecraft:planks");
		helper.assertTrue(saccharine().builtInRegistryHolder().is(ItemTags.PLANKS), "cobblemon:saccharine_planks isn't in #minecraft:planks");
	}

	/** Plays the plan's steps from what it takes (every step's makings in hand when it comes); what's left at the end. */
	private static Map<Item, Long> play(GameTestHelper helper, Crafting.Plan plan) {
		Map<Item, Long> have = new HashMap<>();
		plan.takes().forEach((item, n) -> have.merge(item, (long) n, Long::sum));
		for (Crafting.Step step : plan.steps()) {
			step.in().forEach((item, n) -> {
				long left = have.getOrDefault(item, 0L) - (long) n * step.times();
				helper.assertTrue(left >= 0, "step " + step + " takes more " + item + " than the plan has: " + plan);
				have.put(item, left);
			});
			have.merge(step.out().getItem(), (long) step.out().getCount() * step.times(), Long::sum);
		}
		return have;
	}

	private static Map<Item, Integer> ints(Map<Item, Long> chest) {
		Map<Item, Integer> m = new HashMap<>();
		chest.forEach((k, v) -> m.put(k, v.intValue()));
		return m;
	}

	/** Asserts a plan for {@code count} {@code target} from {@code chest} takes all of it and the steps make exactly that. */
	private static void plannedFromExactly(GameTestHelper helper, Item target, int count, Map<Item, Long> chest) {
		Crafting.Plan plan = Crafting.plan(helper.getLevel(), Crafting.Kind.CRAFTING, target, count, chest);
		helper.assertTrue(plan != null, "no plan for " + count + " " + target + " from " + chest + " (lacking "
			+ Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, target, count, chest) + ")");
		helper.assertTrue(plan.takes().equals(ints(chest)), count + " " + target + ": the plan takes " + plan.takes() + ", the chest holds " + chest);
		Map<Item, Long> left = play(helper, plan);
		helper.assertTrue(left.getOrDefault(target, 0L) == count && plan.count() == count, "the steps make " + left.getOrDefault(target, 0L) + " "
			+ target + ": " + plan.steps());
		chest.keySet().forEach(k -> helper.assertTrue(left.getOrDefault(k, 0L) == 0, k + " left over after the steps: " + left));
	}

	// --- plans -----------------------------------------------------------------------------------------------------

	/** A Fletching Table (2 flint, 4 of #planks) from 2 oak and 2 apricorn planks: planned, taking exactly those, one table made. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aFletchingTableFromOakAndApricornPlanksIsPlannedFromExactlyThose(GameTestHelper helper) {
		planksAreInTheTag(helper);
		plannedFromExactly(helper, Items.FLETCHING_TABLE, 1, Map.of(Items.FLINT, 2L, Items.OAK_PLANKS, 2L, apricorn(), 2L));
		helper.succeed();
	}

	/** Only Cobblemon's planks, two kinds (3 apricorn, 1 saccharine): still a Fletching Table, from exactly those. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aFletchingTableFromOnlyCobblemonsTwoPlanksIsPlanned(GameTestHelper helper) {
		planksAreInTheTag(helper);
		plannedFromExactly(helper, Items.FLETCHING_TABLE, 1, Map.of(Items.FLINT, 2L, apricorn(), 3L, saccharine(), 1L));
		helper.succeed();
	}

	/** Three Fletching Tables from four woods split unevenly (5 oak, 4 apricorn, 2 saccharine, 1 spruce): every plank used, three made. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void threeFletchingTablesFromFourWoodsUnevenlyUseEveryPlank(GameTestHelper helper) {
		planksAreInTheTag(helper);
		plannedFromExactly(helper, Items.FLETCHING_TABLE, 3, Map.of(Items.FLINT, 6L, Items.OAK_PLANKS, 5L, apricorn(), 4L, saccharine(), 2L, Items.SPRUCE_PLANKS, 1L));
		helper.succeed();
	}

	/**
	 * Short with a modded mix: 2 flint, 1 oak and 2 apricorn planks are 1 plank short of a Fletching Table (not 2 or 3),
	 * one kind of plank named, never one of Create's crushed ores or a c:ores item; and no table is planned.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void shortOfAFletchingTableTheLineCountsTheModdedMixToo(GameTestHelper helper) {
		planksAreInTheTag(helper);
		ServerLevel level = helper.getLevel();
		Map<Item, Long> chest = Map.of(Items.FLINT, 2L, Items.OAK_PLANKS, 1L, apricorn(), 2L);
		Map<Item, Integer> lacks = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.FLETCHING_TABLE, 1, chest);
		int total = lacks.values().stream().mapToInt(Integer::intValue).sum();
		System.out.println("QaMixedTagPack1115e: lacks " + lacks);
		TagKey<Item> ores = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:ores"));
		for (Item named : lacks.keySet()) {
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(named);
			helper.assertTrue(!(id.getNamespace().equals("create") && id.getPath().startsWith("crushed_")) && !named.builtInRegistryHolder().is(ores),
				"the shortage names " + id + ": " + lacks);
		}
		helper.assertTrue(total == 1 && lacks.size() == 1 && lacks.keySet().iterator().next().builtInRegistryHolder().is(ItemTags.PLANKS),
			"2 flint, 1 oak and 2 apricorn planks: a Fletching Table lacks " + lacks + ", expected 1 plank of one kind");
		helper.assertTrue(Crafting.plan(level, Crafting.Kind.CRAFTING, Items.FLETCHING_TABLE, 1, chest) == null, "a Fletching Table planned from 3 planks");
		helper.succeed();
	}

	/**
	 * The same short case for Create's Large Cogwheel (a shaft and 2 of #planks), with a shaft and one apricorn plank: 1
	 * plank short in total, never a crushed ore or c:ores item named.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void shortOfALargeCogwheelTheLineCountsOnePlank(GameTestHelper helper) {
		planksAreInTheTag(helper);
		ServerLevel level = helper.getLevel();
		Item large = item("create:large_cogwheel");
		Map<Item, Integer> lacks = Crafting.lacking(level, Crafting.Kind.CRAFTING, large, 1, Map.of(item("create:shaft"), 1L, apricorn(), 1L));
		int total = lacks.values().stream().mapToInt(Integer::intValue).sum();
		System.out.println("QaMixedTagPack1115e: lacks " + lacks);
		TagKey<Item> ores = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:ores"));
		for (Item named : lacks.keySet()) {
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(named);
			helper.assertTrue(!(id.getNamespace().equals("create") && id.getPath().startsWith("crushed_")) && !named.builtInRegistryHolder().is(ores),
				"the shortage names " + id + ": " + lacks);
		}
		helper.assertTrue(total == 1 && lacks.size() == 1, "a shaft and one apricorn plank: a Large Cogwheel lacks " + lacks + ", expected 1 in all");
		helper.succeed();
	}

	/**
	 * The pack's own rule wins: Carved Wood (default config) turns the base game's #planks crafting table and chest off and
	 * makes each from one wood only; its mixed crafting table recipe then makes Minecraft's crafting table from any 2x2 of
	 * planks no other recipe takes, mixed kinds included (B1378: this test first said the pack's table refuses 2 oak and 2
	 * apricorn planks, but the game's own table makes a Crafting Table of them). So a crafter plans a Crafting Table from
	 * them exactly when the game's table makes one of them, taking exactly those. 4 oak planks still make one.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void withCarvedWoodACraftingTableIsNeverPlannedFromMixedPlanks(GameTestHelper helper) {
		planksAreInTheTag(helper);
		ServerLevel level = helper.getLevel();
		net.minecraft.world.item.crafting.CraftingInput grid = net.minecraft.world.item.crafting.CraftingInput.of(2, 2,
			List.of(new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS), new ItemStack(apricorn()), new ItemStack(apricorn())));
		boolean gameMakes = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, grid, level)
			.map(r -> r.value().assemble(grid, level.registryAccess()).is(Items.CRAFTING_TABLE)).orElse(false);
		Crafting.Plan mixed = Crafting.plan(level, Crafting.Kind.CRAFTING, Items.CRAFTING_TABLE, 1, Map.of(Items.OAK_PLANKS, 2L, apricorn(), 2L));
		helper.assertTrue((mixed != null) == gameMakes, "2 oak and 2 apricorn planks: the game's table " + (gameMakes ? "makes" : "refuses")
			+ " a Crafting Table, the plan is " + (mixed == null ? null : mixed.steps()));
		if (gameMakes) {
			plannedFromExactly(helper, Items.CRAFTING_TABLE, 1, Map.of(Items.OAK_PLANKS, 2L, apricorn(), 2L));
		}
		plannedFromExactly(helper, Items.CRAFTING_TABLE, 1, Map.of(Items.OAK_PLANKS, 4L));
		helper.succeed();
	}

	/** Create's Large Cogwheel (a shaft and two of #planks) from a shaft, one oak and one apricorn plank: made from exactly those. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aLargeCogwheelFromAShaftAndTwoWoodsPlanksIsPlanned(GameTestHelper helper) {
		planksAreInTheTag(helper);
		plannedFromExactly(helper, item("create:large_cogwheel"), 1, Map.of(item("create:shaft"), 1L, Items.OAK_PLANKS, 1L, apricorn(), 1L));
		helper.succeed();
	}

	/** Three Large Cogwheels from three shafts and 6 planks of three woods (3 oak, 2 apricorn, 1 saccharine): all used, three made. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void threeLargeCogwheelsFromThreeWoodsUseEveryPlank(GameTestHelper helper) {
		planksAreInTheTag(helper);
		plannedFromExactly(helper, item("create:large_cogwheel"), 3,
			Map.of(item("create:shaft"), 3L, Items.OAK_PLANKS, 3L, apricorn(), 2L, saccharine(), 1L));
		helper.succeed();
	}

	/**
	 * Create's Andesite Alloy is two recipes (two iron nuggets OR two zinc nuggets, with two andesite), not one slot of
	 * either: one iron and one zinc nugget make nothing at a crafting table, so no plan; two zinc nuggets do.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void andesiteAlloyIsNeverPlannedFromOneIronAndOneZincNugget(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Item alloy = item("create:andesite_alloy");
		Item zinc = item("create:zinc_nugget");
		Crafting.Plan mixed = Crafting.plan(level, Crafting.Kind.CRAFTING, alloy, 1, Map.of(Items.IRON_NUGGET, 1L, zinc, 1L, Items.ANDESITE, 2L));
		helper.assertTrue(mixed == null, "Andesite Alloy planned from one iron and one zinc nugget, which no crafting table allows: " + (mixed == null ? null
			: mixed.steps()));
		plannedFromExactly(helper, alloy, 1, Map.of(zinc, 2L, Items.ANDESITE, 2L));
		helper.succeed();
	}

	// --- the Carpenter's own work loop -------------------------------------------------------------------------------

	private static Item shaft() {
		return item("create:shaft");
	}

	private static Item cog() {
		return item("create:cogwheel");
	}

	private static Item largeCog() {
		return item("create:large_cogwheel");
	}

	/** The Large Cogwheel (a shaft and two of #planks; Create's own recipe) is the Carpenter's, and his makings in the pack are as read from Create's jar. */
	private static void largeCogIsTheCarpenters(GameTestHelper helper) {
		helper.assertTrue(CraftJobs.maker(helper.getLevel(), largeCog()) == CraftJobs.Job.CARPENTER, "the Large Cogwheel is made by "
			+ CraftJobs.maker(helper.getLevel(), largeCog()));
	}

	/**
	 * A builder short of 3 Large Cogwheels, his chest holding 3 shafts and 3 oak, 2 apricorn and 1 saccharine planks (a
	 * mix that differs from craft to craft): the Carpenter makes the three, they're built, every plank and shaft is used,
	 * no spare cogwheel of either size, nothing in his bag or on the ground.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaMixedPack1115eCogs"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaMixedPack1115eCogs")
	public void aCarpenterMakesLargeCogwheelsFromOakApricornAndSaccharinePlanks(GameTestHelper helper) {
		ServerLevel level = prepare(helper);
		planksAreInTheTag(helper);
		largeCogIsTheCarpenters(helper);
		Container chest = builderChest(helper, new ItemStack(shaft(), 3), new ItemStack(Items.OAK_PLANKS, 3), new ItemStack(apricorn(), 2),
			new ItemStack(saccharine(), 1));
		BuildSite site = startBuilder(helper, spaced(helper, Block.byItem(largeCog()), 3, "cogs"));
		BuildPlan plan = site.plan(level);
		Villager carpenter = hire(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.status() + " missing " + site.missing()
				+ "; the Carpenter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) + "; chest " + planks(chest));
			helper.assertTrue(plan.unfinished(level).isEmpty(), plan.unfinished(level).size() + " block(s) wrong after the build");
			allUsedNoneSpare(helper, chest, List.of(carpenter));
		});
	}

	/**
	 * The same three Large Cogwheels, the Carpenter saved and loaded again (a server restart) once he is past the first of
	 * his plan's several steps: he finishes, the three are built, every plank and shaft is used once, none doubled.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaMixedPack1115eReload"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaMixedPack1115eReload")
	public void aCarpenterReloadedMidMixedPlanFinishesWithExactlyThePlanks(GameTestHelper helper) {
		ServerLevel level = prepare(helper);
		planksAreInTheTag(helper);
		largeCogIsTheCarpenters(helper);
		Container chest = builderChest(helper, new ItemStack(shaft(), 3), new ItemStack(Items.OAK_PLANKS, 3), new ItemStack(apricorn(), 2),
			new ItemStack(saccharine(), 1));
		BuildSite site = startBuilder(helper, spaced(helper, Block.byItem(largeCog()), 3, "reload"));
		BuildPlan plan = site.plan(level);
		Villager[] carpenter = {hire(helper)};
		int[] stepsAtReload = {0};
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(carpenter[0]);
			if (stepsAtReload[0] == 0 && job != null && job.phase() == CrafterJob.Phase.CRAFTING && job.step() >= 1) {
				stepsAtReload[0] = job.steps().size();
				carpenter[0] = QaCarriedPartsGone1107eCompatTests.reload(helper, carpenter[0]);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stepsAtReload[0] > 1, "the Carpenter never got past the first step of a several-step plan to be reloaded there");
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building after the reload: " + site.status() + " missing "
				+ site.missing() + "; job " + ModAttachments.CRAFTER_JOB.get(carpenter[0]) + "; chest " + planks(chest));
			helper.assertTrue(plan.unfinished(level).isEmpty(), plan.unfinished(level).size() + " block(s) wrong after the build");
			allUsedNoneSpare(helper, chest, List.of(carpenter[0]));
		});
	}

	/**
	 * The store changes under the plan: one Large Cogwheel asked, the chest holds a shaft, an oak and an apricorn plank;
	 * once the Carpenter has his job, the apricorn plank is taken out (a player). He never makes a Large Cogwheel from one
	 * plank, the shaft and the oak plank are never lost (whole, or together as a small cogwheel), and the builder's line
	 * becomes a shortage of 1.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qaMixedPack1115eTaken"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaMixedPack1115eTaken")
	public void aPlankTakenFromUnderThePlanNeverMakesACogwheelFromNothing(GameTestHelper helper) {
		ServerLevel level = prepare(helper);
		planksAreInTheTag(helper);
		largeCogIsTheCarpenters(helper);
		Container chest = builderChest(helper, new ItemStack(shaft(), 1), new ItemStack(Items.OAK_PLANKS, 1), new ItemStack(apricorn(), 1));
		BuildSite site = startBuilder(helper, spaced(helper, Block.byItem(largeCog()), 1, "taken"));
		Villager carpenter = hire(helper);
		long[] takenAt = {-1};
		helper.onEachTick(() -> {
			if (takenAt[0] < 0 && ModAttachments.CRAFTER_JOB.get(carpenter) != null) {
				helper.assertTrue(chest.countItem(apricorn()) == 1, "setup: the apricorn plank left the chest before it could be taken out: "
					+ planks(chest) + "; job " + ModAttachments.CRAFTER_JOB.get(carpenter));
				for (int i = 0; i < chest.getContainerSize(); i++) {
					if (chest.getItem(i).is(apricorn())) {
						chest.setItem(i, ItemStack.EMPTY);
					}
				}
				takenAt[0] = helper.getTick();
			}
			int large = everywhere(helper, chest, List.of(carpenter), largeCog()) + (helper.getBlockState(ROW).is(Block.byItem(largeCog())) ? 1 : 0);
			helper.assertTrue(takenAt[0] < 0 || large == 0, "a Large Cogwheel made after half its planks were taken: " + large + "; job "
				+ ModAttachments.CRAFTER_JOB.get(carpenter));
		});
		helper.runAfterDelay(1500, () -> {
			helper.assertTrue(takenAt[0] >= 0, "the Carpenter never took the job");
			int small = everywhere(helper, chest, List.of(carpenter), cog());
			int oak = everywhere(helper, chest, List.of(carpenter), Items.OAK_PLANKS);
			int shafts = everywhere(helper, chest, List.of(carpenter), shaft());
			helper.assertTrue(oak + small == 1 && shafts + small == 1, "the shaft and the oak plank: " + shafts + " shafts, " + oak + " oak planks, " + small
				+ " cogwheels in the chest, his bag and on the ground (chest " + planks(chest) + ", bag " + ModAttachments.BUILDER_BAG.getOrCreate(carpenter).stacks() + ")");
			helper.assertTrue(everywhere(helper, chest, List.of(carpenter), apricorn()) == 0, "an apricorn plank came back from nowhere");
			Component line = JobShortages.line(level, site.id(), site.missing(), helper.absolutePos(BENCH));
			String text = line == null ? null : line.getString();
			System.out.println("QaMixedTagPack1115e: plank taken under the plan, the builder's line: " + text + " (taken at tick " + takenAt[0] + ")");
			helper.assertTrue(text != null && text.startsWith("Can't be made yet: short of 1 "), "the builder's line: '" + text
				+ "', expected 'Can't be made yet: short of 1 ...' (missing " + site.missing() + ")");
			helper.succeed();
		});
	}

	// --- helpers ---------------------------------------------------------------------------------------------------

	private static String planks(Container chest) {
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (!chest.getItem(i).isEmpty()) {
				s.append(chest.getItem(i)).append(' ');
			}
		}
		return s.toString().trim();
	}

	/** How many of {@code item} are in the chest, the villagers' bags and inventories, and on the ground near the area. */
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

	private static void allUsedNoneSpare(GameTestHelper helper, Container chest, List<Villager> villagers) {
		for (Item used : List.of(Items.OAK_PLANKS, apricorn(), saccharine(), shaft())) {
			int left = everywhere(helper, chest, villagers, used);
			helper.assertTrue(left == 0, left + " " + BuiltInRegistries.ITEM.getKey(used) + " left unused (chest " + planks(chest) + ")");
		}
		for (Item made : List.of(cog(), largeCog())) {
			int spare = everywhere(helper, chest, villagers, made);
			helper.assertTrue(spare == 0, spare + " spare " + BuiltInRegistries.ITEM.getKey(made) + " made (chest " + planks(chest) + ")");
		}
	}

	private static ServerLevel prepare(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		FurnicrafterCompatTests.ownStoreOnly(helper);
		ServerLevel level = helper.getLevel();
		int radius = Village.RADIUS;
		Village.RADIUS = 48;
		PartnerShowsCompatTests.after(helper, () -> Village.RADIUS = radius);
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		return level;
	}

	private static Container builderChest(GameTestHelper helper, ItemStack... items) {
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < items.length; i++) {
			chest.setItem(i, items[i].copy());
		}
		return chest;
	}

	private static BuildSite startBuilder(GameTestHelper helper, ResourceLocation id) {
		ServerLevel level = helper.getLevel();
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ROW),
			Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null && site.plan(level) != null, "the build didn't start");
		return site;
	}

	private static Villager hire(GameTestHelper helper) {
		helper.setBlock(WORKBENCH, ModBlocks.CARPENTERS_BENCH);
		Villager worker = helper.spawn(EntityType.VILLAGER, WORKBENCH.south());
		Jobs.employ(helper.getLevel(), worker, helper.absolutePos(WORKBENCH), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		return worker;
	}

	/** A blueprint of {@code n} {@code block}s, two blocks of air between each (Large Cogwheels are wide), drawn where it's to be built and taken away. */
	private static ResourceLocation spaced(GameTestHelper helper, Block block, int n, String what) {
		ServerLevel level = helper.getLevel();
		for (int i = 0; i < n; i++) {
			helper.setBlock(ROW.offset(3 * i, 0, 0), block);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_compat",
			"qa_mixedpack_" + what + "_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(ROW).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(3 * n - 2, 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int i = 0; i < n; i++) {
			helper.setBlock(ROW.offset(3 * i, 0, 0), Blocks.AIR);
		}
		return id;
	}
}
