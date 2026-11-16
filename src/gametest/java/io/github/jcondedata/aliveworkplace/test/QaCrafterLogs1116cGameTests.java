package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane C, 2026-11-16) past B1301, from crafter.md: a slot that takes any of a tag is filled from planks made from the
 * logs of several woods, each wood making what it can, taking only the makings needed; truly short, the line names what's
 * short ("planks still to be sawn from the logs at hand read short of 1 Oak Log") and nothing is taken. Here: four woods
 * one log each, stems, stripped logs and bamboo in the mix, logs topping up loose planks, other tag recipes (a barrel,
 * whose slabs need one wood, a wooden pickaxe), the Carpenter's other kind, a real Carpenter's job with a save and reload
 * mid-job, and a real job truly short of logs. Nothing here is random.
 */
public class QaCrafterLogs1116cGameTests implements FabricGameTest {
	private static final String AREA = QaCrafterFurnitureGameTests.AREA;

	private static Crafting.Plan plan(GameTestHelper helper, Item target, int count, Map<Item, Long> chest) {
		return Crafting.plan(helper.getLevel(), Crafting.Kind.CRAFTING, target, count, chest);
	}

	private static String ids(Map<Item, Integer> lack) {
		return lack.entrySet().stream().map(e -> e.getValue() + " " + BuiltInRegistries.ITEM.getKey(e.getKey()).getPath()).toList().toString();
	}

	private static int sum(Map<Item, Integer> m) {
		return m.values().stream().mapToInt(Integer::intValue).sum();
	}

	/** Plans {@code count} of {@code target} from {@code chest}, checks it takes exactly {@code takes} and its steps make them. */
	private static void exact(GameTestHelper helper, Item target, int count, Map<Item, Long> chest, Map<Item, Integer> takes) {
		String what = count + " " + BuiltInRegistries.ITEM.getKey(target).getPath() + " from " + chest;
		Crafting.Plan made = plan(helper, target, count, chest);
		helper.assertTrue(made != null, "no plan for " + what + "; lacks " + ids(Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, target, count, chest)));
		helper.assertTrue(made.takes().equals(takes), what + ": the plan takes " + made.takes() + ", expected " + takes);
		Map<Item, Long> left = MixedTagSlotGameTests.play(helper, made);
		helper.assertTrue(left.getOrDefault(target, 0L) == count, what + ": the steps make " + left.getOrDefault(target, 0L) + ": " + made.steps());
		helper.assertTrue(Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, target, count, chest).isEmpty(),
			what + ": lacks " + ids(Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, target, count, chest)));
	}

	/**
	 * Four woods, one log each (16 planks): exactly two chests, all four logs taken. Stems and stripped logs are a wood's
	 * logs too: one crimson stem and one stripped birch log make a chest. Bamboo is a wood: a bamboo block (2 bamboo
	 * planks), an oak log (4) and 2 spruce planks make 8, a chest, taking all of them.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void fourWoodsStemsStrippedLogsAndBambooFillTheChestsPlanks(GameTestHelper helper) {
		exact(helper, Items.CHEST, 2, Map.of(Items.OAK_LOG, 1L, Items.SPRUCE_LOG, 1L, Items.BIRCH_LOG, 1L, Items.JUNGLE_LOG, 1L),
			Map.of(Items.OAK_LOG, 1, Items.SPRUCE_LOG, 1, Items.BIRCH_LOG, 1, Items.JUNGLE_LOG, 1));
		exact(helper, Items.CHEST, 1, Map.of(Items.CRIMSON_STEM, 1L, Items.STRIPPED_BIRCH_LOG, 1L),
			Map.of(Items.CRIMSON_STEM, 1, Items.STRIPPED_BIRCH_LOG, 1));
		exact(helper, Items.CHEST, 1, Map.of(Items.BAMBOO_BLOCK, 1L, Items.OAK_LOG, 1L, Items.SPRUCE_PLANKS, 2L),
			Map.of(Items.BAMBOO_BLOCK, 1, Items.OAK_LOG, 1, Items.SPRUCE_PLANKS, 2));
		helper.succeed();
	}

	/**
	 * Logs topping up loose planks of other woods: 2 birch planks, an oak log and a spruce log are 10 planks, a chest takes
	 * all of them (2 + 4 is too few) and 2 planks are left after the crafts; 5 cherry planks and an acacia log make a chest
	 * (the log must be sawn), and whatever the crafts don't use is left as planks: never a plank more than the 8 used up.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void logsTopUpLoosePlanksOfOtherWoodsTakingOnlyWhatsNeeded(GameTestHelper helper) {
		Map<Item, Long> chest = Map.of(Items.BIRCH_PLANKS, 2L, Items.OAK_LOG, 1L, Items.SPRUCE_LOG, 1L);
		Crafting.Plan made = plan(helper, Items.CHEST, 1, chest);
		helper.assertTrue(made != null && made.takes().equals(Map.of(Items.BIRCH_PLANKS, 2, Items.OAK_LOG, 1, Items.SPRUCE_LOG, 1)),
			"a chest from 2 birch planks, an oak and a spruce log takes " + (made == null ? "no plan" : made.takes()));
		Map<Item, Long> left = MixedTagSlotGameTests.play(helper, made);
		long planksLeft = left.entrySet().stream().filter(e -> e.getKey().builtInRegistryHolder().is(ItemTags.PLANKS)).mapToLong(Map.Entry::getValue).sum();
		helper.assertTrue(left.getOrDefault(Items.CHEST, 0L) == 1 && planksLeft == 2, "the crafts leave " + left + ", expected a chest and 2 planks");

		Map<Item, Long> cherryChest = Map.of(Items.CHERRY_PLANKS, 5L, Items.ACACIA_LOG, 1L);
		Crafting.Plan cherry = plan(helper, Items.CHEST, 1, cherryChest);
		helper.assertTrue(cherry != null && cherry.takes().getOrDefault(Items.ACACIA_LOG, 0) == 1 && cherry.takes().getOrDefault(Items.CHERRY_PLANKS, 0) >= 4,
			"a chest from 5 cherry planks and an acacia log takes " + (cherry == null ? "no plan" : cherry.takes()) + ", expected the log and 4 or 5 planks");
		Map<Item, Long> cherryLeft = MixedTagSlotGameTests.play(helper, cherry);
		long spare = cherryLeft.entrySet().stream().filter(e -> e.getKey().builtInRegistryHolder().is(ItemTags.PLANKS)).mapToLong(Map.Entry::getValue).sum();
		helper.assertTrue(cherryLeft.getOrDefault(Items.CHEST, 0L) == 1 && spare == cherry.takes().get(Items.CHERRY_PLANKS) - 4,
			"the crafts leave " + cherryLeft + " from " + cherry.takes() + ": a plank more than the 8 was used up");
		helper.succeed();
	}

	/**
	 * Truly short of mixed logs: an oak and a spruce log (8 planks) for 2 chests: no plan and no chain (the count of what's
	 * short is QaCrafterLogs1116cBugGameTests). 1 oak log and 3 spruce planks (7) for a chest: short of exactly 1, a plank
	 * or a log.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '100' '"qaCrafterLogs1116cMixedLogsTrulyShortPlanNothing"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100, batch = "qaCrafterLogs1116cMixedLogsTrulyShortPlanNothing")
	public void mixedLogsTrulyShortPlanNothing(GameTestHelper helper) {
		// alone in its batch, a tick after the batch starts: the level's per-tick planning (Crafting.TICK_TRIALS) is
		// shared, and a plan cut short by it is 'not planned yet' (B1348), so it must not be spent by other tests
		Leftovers.clear(helper);
		helper.runAfterDelay(1, () -> {
			ServerLevel level = helper.getLevel();
			Map<Item, Long> two = Map.of(Items.OAK_LOG, 1L, Items.SPRUCE_LOG, 1L);
			helper.assertTrue(plan(helper, Items.CHEST, 2, two) == null, "2 chests planned from 2 logs: " + plan(helper, Items.CHEST, 2, two));
			helper.assertTrue(Crafting.chain(level, Crafting.Kind.CRAFTING, Items.CHEST, 2, two) == null, "2 chests chained from 2 logs");
			Map<Item, Long> seven = Map.of(Items.OAK_LOG, 1L, Items.SPRUCE_PLANKS, 3L);
			helper.assertTrue(plan(helper, Items.CHEST, 1, seven) == null, "a chest planned from 7 planks' worth");
			Map<Item, Integer> one = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CHEST, 1, seven);
			helper.assertTrue(sum(one) == 1 && one.keySet().stream().allMatch(i -> i.builtInRegistryHolder().is(ItemTags.PLANKS) || i.builtInRegistryHolder().is(ItemTags.LOGS)),
				"an oak log and 3 spruce planks: a chest lacks " + ids(one) + ", expected 1 plank or log");
			helper.succeed();
		});
	}

	/**
	 * Other tag recipes from mixed logs: a barrel (6 planks, any wood, and 2 slabs, which a player makes from 3 planks of
	 * ONE wood) from an oak, a spruce and a birch log (12 planks: 6 + 3 of one wood for the slabs) is planned and made;
	 * a wooden pickaxe (3 planks and 2 sticks) from an oak log and 1 spruce plank takes both; and the Carpenter's crafter
	 * kind plans a chest from an oak and a spruce log like the crafting table's.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aBarrelAPickaxeAndTheCrafterKindFromMixedLogs(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<Item, Long> three = Map.of(Items.OAK_LOG, 1L, Items.SPRUCE_LOG, 1L, Items.BIRCH_LOG, 1L);
		Crafting.Plan barrel = plan(helper, Items.BARREL, 1, three);
		helper.assertTrue(barrel != null, "no plan for a barrel from an oak, a spruce and a birch log; lacks "
			+ ids(Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.BARREL, 1, three)));
		helper.assertTrue(barrel.takes().equals(Map.of(Items.OAK_LOG, 1, Items.SPRUCE_LOG, 1, Items.BIRCH_LOG, 1)), "the barrel takes " + barrel.takes());
		helper.assertTrue(MixedTagSlotGameTests.play(helper, barrel).getOrDefault(Items.BARREL, 0L) == 1, "the barrel's steps make none: " + barrel.steps());
		helper.assertTrue(plan(helper, Items.BARREL, 1, Map.of(Items.OAK_LOG, 1L, Items.SPRUCE_LOG, 1L)) == null, "a barrel planned from 8 planks' worth (it needs 9)");

		exact(helper, Items.WOODEN_PICKAXE, 1, Map.of(Items.OAK_LOG, 1L, Items.SPRUCE_PLANKS, 1L), Map.of(Items.OAK_LOG, 1, Items.SPRUCE_PLANKS, 1));

		Map<Item, Long> two = Map.of(Items.OAK_LOG, 1L, Items.SPRUCE_LOG, 1L);
		for (Crafting.Kind kind : List.of(Crafting.Kind.CRAFTING, Crafting.Kind.CRAFTER)) {
			Crafting.Plan chain = Crafting.chain(level, kind, Items.CHEST, 1, two);
			helper.assertTrue(chain != null && chain.takes().equals(Map.of(Items.OAK_LOG, 1, Items.SPRUCE_LOG, 1)),
				kind + ": a chest from an oak and a spruce log chains " + (chain == null ? "nothing" : chain.takes()));
		}
		helper.succeed();
	}

	/**
	 * The whole job with a save and reload mid-job: the builder's row of 3 chests (24 planks) and his chest holds 4 oak
	 * planks, 2 spruce logs, 3 birch logs (exactly 24) and 2 cobblestone. The Carpenter is saved and loaded again at tick
	 * 300; the row is built, every plank and log is used, nothing dropped, nothing in the bag, and the cobblestone is left.
	 */
	//$ gametest_ticks_batch AREA '4800' '"qaCrafterLogs1116cReload"'
	@GameTest(template = AREA, timeoutTicks = 4800, batch = "qaCrafterLogs1116cReload")
	public void aCarpenterMakesChestsFromLogsAndPlanksAcrossAReload(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, QaCrafterFurnitureGameTests.BENCH, QaCrafterFurnitureGameTests.CHEST,
			QaJobChainFixesGameTests.ORIGIN, Blocks.CHEST, new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.SPRUCE_LOG, 2),
			new ItemStack(Items.BIRCH_LOG, 3), new ItemStack(Items.COBBLESTONE, 2));
		AtomicReference<Villager> carpenter = new AtomicReference<>(QaCrafterFurnitureGameTests.carpenter(helper, QaCrafterFurnitureGameTests.STATION));
		helper.runAfterDelay(300, () -> carpenter.set(QaCrafterFurnitureGameTests.reload(helper.getLevel(), carpenter.get())));
		Item[] wood = {Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.OAK_LOG};
		helper.succeedWhen(() -> {
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			int left = 0;
			for (Item item : wood) {
				left += chest.countItem(item) + QaCrafterFurnitureGameTests.loose(helper, item);
			}
			helper.assertTrue(left == 0, left + " planks or logs left over (exactly 24 planks' worth for 3 chests)");
			int other = 0;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				if (!chest.getItem(i).is(ModItems.BLUEPRINT)) {
					other += chest.getItem(i).getCount();
				}
			}
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 2 && other == 2, "the chest holds " + chest.countItem(Items.COBBLESTONE)
				+ " cobblestone, " + other + " items in all");
			helper.assertTrue(chest.countItem(Items.CHEST) + QaCrafterFurnitureGameTests.loose(helper, Items.CHEST) == 0, "spare chests about");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(carpenter.get()).isEmpty(), "still in the carpenter's bag: "
				+ ModAttachments.BUILDER_BAG.getOrCreate(carpenter.get()).stacks());
		});
	}

	/**
	 * A real job truly short of mixed logs: the row of 3 chests (24 planks) with 2 oak, 2 spruce and 1 birch log (20). The
	 * build doesn't finish; nothing is lost or made from nothing (every log, plank and chest counted in plank's worth stays
	 * 20, whether still logs, sawn, or built); the builder's line names logs short, never "Nobody here makes".
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaCrafterLogs1116cShort"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaCrafterLogs1116cShort")
	public void aCarpenterShortOfMixedLogsLosesNothingAndNamesLogs(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, QaCrafterFurnitureGameTests.BENCH, QaCrafterFurnitureGameTests.CHEST,
			QaJobChainFixesGameTests.ORIGIN, Blocks.CHEST, new ItemStack(Items.OAK_LOG, 2), new ItemStack(Items.SPRUCE_LOG, 2),
			new ItemStack(Items.BIRCH_LOG, 1));
		Villager carpenter = QaCrafterFurnitureGameTests.carpenter(helper, QaCrafterFurnitureGameTests.STATION);
		helper.runAfterDelay(2000, () -> {
			ServerLevel level = helper.getLevel();
			Container chest = helper.getBlockEntity(a.chest());
			int worth = 0;
			StringBuilder seen = new StringBuilder();
			for (Item log : List.of(Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG)) {
				int n = chest.countItem(log) + QaCrafterFurnitureGameTests.loose(helper, log) + bag(carpenter, log);
				worth += 4 * n;
				seen.append(n).append(' ').append(BuiltInRegistries.ITEM.getKey(log).getPath()).append(", ");
			}
			for (Item plank : List.of(Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS)) {
				int n = chest.countItem(plank) + QaCrafterFurnitureGameTests.loose(helper, plank) + bag(carpenter, plank);
				worth += n;
				seen.append(n).append(' ').append(BuiltInRegistries.ITEM.getKey(plank).getPath()).append(", ");
			}
			int built = 0;
			for (int x = 0; x < 3; x++) {
				if (helper.getBlockState(QaJobChainFixesGameTests.ORIGIN.offset(x, 0, 0)).is(Blocks.CHEST)) {
					built++;
				}
			}
			int chests = chest.countItem(Items.CHEST) + QaCrafterFurnitureGameTests.loose(helper, Items.CHEST) + bag(carpenter, Items.CHEST) + built;
			worth += 8 * chests;
			seen.append(chests).append(" chests (").append(built).append(" built)");
			helper.assertTrue(worth == 20, "planks' worth " + worth + ", expected 20: " + seen);
			helper.assertTrue(BuildSiteManager.get(level).get(a.site().id()) != null, "the build finished short of 4 planks: " + seen);
			Component line = JobShortages.line(level, a.site().id(), a.site().missing());
			String text = line == null ? null : line.getString();
			helper.assertTrue(text != null && text.startsWith("Can't be made yet: short of ") && text.contains(" Log") && text.contains("Chest"),
				"the builder's line: " + text + "; " + seen + "; missing " + a.site().missing());
			helper.succeed();
		});
	}

	private static int bag(Villager villager, Item item) {
		return ModAttachments.BUILDER_BAG.getOrCreate(villager).stacks().stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
	}
}
