package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane C, 2026-11-16) past B1302 and B1286, from crafter.md: with Carved Wood's wooden_chests on (the owner's pack)
 * the Carpenter makes a builder's plain chest the way a player does, 8 planks of one wood into that wood's carved chest,
 * then the plain chest; turns a carved chest of any wood that's there into one; never uses a trapped chest; and a chest
 * from nothing names logs (never Pale Oak, which needs pale oak). Here, at real jobs: from oak logs, from logs of two woods
 * (only one of which has the 8 planks), a carved chest already there with planks to spare, only trapped chests, planks
 * taken away before the Carpenter comes and given back, and the Carpenter saved and loaded mid-job. The plain chest built
 * is checked to be minecraft:chest itself, not a carved one. Nothing here is random.
 */
public class QaCarvedChest1116cCompatTests implements FabricGameTest {
	private static final String AREA = QaCarvedChest1115eCompatTests.AREA;
	private static final BlockPos ROW = new BlockPos(6, 2, 9);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	private static Item item(String id) {
		return QaCarvedChest1115eCompatTests.block(id).asItem();
	}

	private static String ids(Map<Item, Integer> lack) {
		return lack.entrySet().stream().map(e -> e.getValue() + " " + BuiltInRegistries.ITEM.getKey(e.getKey())).toList().toString();
	}

	/** Everything in the builder's chest but his blueprint, as id=count. */
	private static Map<Item, Integer> held(Container chest) {
		Map<Item, Integer> held = new java.util.HashMap<>();
		for (int i = 0; i < chest.getContainerSize(); i++) {
			ItemStack s = chest.getItem(i);
			if (!s.isEmpty() && !s.is(ModItems.BLUEPRINT)) {
				held.merge(s.getItem(), s.getCount(), Integer::sum);
			}
		}
		return held;
	}

	private record Job(BuildSite site, Container chest, AtomicReference<Villager> carpenter) {
		String report(GameTestHelper helper) {
			return QaCarvedChest1115eCompatTests.report(helper, site, chest, carpenter.get());
		}
	}

	private static Job job(GameTestHelper helper, String what, ItemStack... supply) {
		QaCarvedChest1115eCompatTests.prepare(helper);
		helper.assertTrue(QaCarvedChest1115eCompatTests.block("carved_wood:oak_chest") != Blocks.AIR, "Carved Wood isn't loaded");
		Container chest = QaCarvedChest1115eCompatTests.builderChest(helper, supply);
		BuildSite site = QaCarvedChest1115eCompatTests.startBuilder(helper, QaCarvedChest1115eCompatTests.spaced(helper, Blocks.CHEST, what));
		return new Job(site, chest, new AtomicReference<>(QaCarvedChest1115eCompatTests.hire(helper)));
	}

	/** The plain chest is built (minecraft:chest, not a carved one), the build is done, and the chest holds exactly {@code left}. */
	private static void builtLeaving(GameTestHelper helper, Job job, Map<Item, Integer> left) {
		builtLeaving(helper, job, left, () -> true);
	}

	private static void builtLeaving(GameTestHelper helper, Job job, Map<Item, Integer> left, java.util.function.BooleanSupplier ready) {
		helper.succeedWhen(() -> {
			helper.assertTrue(ready.getAsBoolean(), "not yet");
			helper.assertTrue(helper.getBlockState(ROW).is(Blocks.CHEST), "no plain chest built: " + job.report(helper));
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(job.site().id()) == null, "the build isn't done: " + job.report(helper));
			helper.assertTrue(held(job.chest()).equals(left), "the builder's chest holds " + held(job.chest()) + ", expected " + left + ": " + job.report(helper));
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(job.carpenter().get()).isEmpty(), "still in the carpenter's bag: " + job.report(helper));
			helper.assertTrue(QaCarvedChestLoose.loose(helper) == 0, "chests, planks or logs dropped about: " + job.report(helper));
		});
	}

	/** Pure planning: a chest from mixed logs uses the wood that has the 8 planks; from nothing (or only trapped chests) it names logs, 2 a chest, never pale oak. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aChestFromMixedLogsOrNothingIsPlannedAsAPlayerWould(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Crafting.Plan two = Crafting.chain(level, Crafting.Kind.CRAFTING, Items.CHEST, 1, Map.of(Items.OAK_LOG, 1L, Items.SPRUCE_LOG, 2L));
		helper.assertTrue(two != null && two.takes().equals(Map.of(Items.SPRUCE_LOG, 2)), "a chest from an oak and 2 spruce logs: " + (two == null ? "no plan" : two.takes()));
		Crafting.Plan oak = Crafting.chain(level, Crafting.Kind.CRAFTING, Items.CHEST, 2, Map.of(Items.OAK_LOG, 4L));
		helper.assertTrue(oak != null && oak.takes().equals(Map.of(Items.OAK_LOG, 4)), "2 chests from 4 oak logs: " + (oak == null ? "no plan" : oak.takes()));
		for (Map<Item, Long> chest : java.util.List.of(Map.<Item, Long>of(), Map.of(Items.TRAPPED_CHEST, 1L, item("carved_wood:trapped_oak_chest"), 1L))) {
			for (int n : new int[] {1, 3}) {
				Map<Item, Integer> lack = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.CHEST, n, chest);
				int sum = lack.values().stream().mapToInt(Integer::intValue).sum();
				helper.assertTrue(sum == 2 * n && lack.keySet().stream().allMatch(i -> i.builtInRegistryHolder().is(ItemTags.LOGS))
						&& lack.keySet().stream().noneMatch(i -> BuiltInRegistries.ITEM.getKey(i).getPath().contains("pale")),
					n + " chests from " + chest + " lack " + ids(lack) + ", expected " + 2 * n + " logs, not pale oak");
				String line = JobShortages.lackLine(lack, n, Items.CHEST).getString();
				helper.assertTrue(line.matches("short of " + 2 * n + " [A-Z][a-z]+( [A-Z][a-z]+)? Logs to make " + n + (n == 1 ? " Chest" : " Chests")), "the line reads \"" + line + "\"");
			}
		}
		helper.succeed();
	}

	/** A plain chest from 2 oak logs (and 3 cobblestone besides): sawn, the Oak Chest, then the plain chest; the cobblestone is left. */
	//$ gametest_ticks_batch AREA '3000' '"qaCarvedChest1116cLogs"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaCarvedChest1116cLogs")
	public void aPlainChestFromOakLogsIsBuiltPlain(GameTestHelper helper) {
		Job job = job(helper, "oaklogs", new ItemStack(Items.OAK_LOG, 2), new ItemStack(Items.COBBLESTONE, 3));
		builtLeaving(helper, job, Map.of(Items.COBBLESTONE, 3));
	}

	/** An oak log and 2 spruce logs: only spruce has the 8 planks of one wood, so the spruce is used and the oak log is left. */
	//$ gametest_ticks_batch AREA '3000' '"qaCarvedChest1116cTwoWoods"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaCarvedChest1116cTwoWoods")
	public void aPlainChestFromTwoWoodsLogsUsesTheWoodThatHasEnough(GameTestHelper helper) {
		Job job = job(helper, "twowoods", new ItemStack(Items.OAK_LOG, 1), new ItemStack(Items.SPRUCE_LOG, 2));
		builtLeaving(helper, job, Map.of(Items.OAK_LOG, 1));
	}

	/** A carved spruce chest and 8 oak planks: the carved chest is turned into the plain one (one craft) and the planks are kept. */
	//$ gametest_ticks_batch AREA '3000' '"qaCarvedChest1116cCarved"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaCarvedChest1116cCarved")
	public void aCarvedChestThereIsUsedAndThePlanksKept(GameTestHelper helper) {
		Job job = job(helper, "carvedthere", new ItemStack(item("carved_wood:spruce_chest"), 1), new ItemStack(Items.OAK_PLANKS, 8));
		builtLeaving(helper, job, Map.of(Items.OAK_PLANKS, 8));
	}

	/**
	 * Only trapped chests (a vanilla one and Carved Wood's trapped oak): neither is used, nothing is built or made, and the
	 * builder's line names 2 logs (never "Nobody here makes", never Pale Oak).
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaCarvedChest1116cTrapped"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaCarvedChest1116cTrapped")
	public void onlyTrappedChestsAreNeverUsedAndTheLineNamesLogs(GameTestHelper helper) {
		Item trappedOak = item("carved_wood:trapped_oak_chest");
		Job job = job(helper, "trapped", new ItemStack(Items.TRAPPED_CHEST, 1), new ItemStack(trappedOak, 1));
		helper.runAfterDelay(1200, () -> {
			helper.assertTrue(held(job.chest()).equals(Map.of(Items.TRAPPED_CHEST, 1, trappedOak, 1)), "the trapped chests were touched: " + job.report(helper));
			helper.assertTrue(helper.getBlockState(ROW).isAir() && ModAttachments.ITEMS_CRAFTED.getOrElse(job.carpenter().get(), 0) == 0,
				"something was made or built: " + job.report(helper));
			Component line = JobShortages.line(helper.getLevel(), job.site().id(), job.site().missing(), helper.absolutePos(QaCarvedChest1115eCompatTests.BENCH));
			String text = line == null ? "" : line.getString();
			helper.assertTrue(text.matches("Can't be made yet: short of 2 [A-Z][a-z]+( [A-Z][a-z]+)? Logs to make 1 Chest") && !text.contains("Pale"),
				"the builder's line: " + job.report(helper));
			helper.succeed();
		});
	}

	/**
	 * Planks taken away before the Carpenter comes: 8 oak planks, 4 taken at once. Nothing is made from the 4 left, which
	 * stay; the line says what's short. The 4 given back at tick 800: the plain chest is built and nothing is left.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaCarvedChest1116cTaken"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaCarvedChest1116cTaken")
	public void planksTakenAwayThenGivenBackMakeTheChestLosingNothing(GameTestHelper helper) {
		Job job = job(helper, "taken", new ItemStack(Items.OAK_PLANKS, 8));
		job.chest().setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
		helper.runAfterDelay(780, () -> {
			helper.assertTrue(held(job.chest()).equals(Map.of(Items.OAK_PLANKS, 4)) && ModAttachments.ITEMS_CRAFTED.getOrElse(job.carpenter().get(), 0) == 0
				&& helper.getBlockState(ROW).isAir(), "with 4 oak planks: " + job.report(helper));
			Component line = JobShortages.line(helper.getLevel(), job.site().id(), job.site().missing(), helper.absolutePos(QaCarvedChest1115eCompatTests.BENCH));
			String text = line == null ? "" : line.getString();
			helper.assertTrue(text.startsWith("Can't be made yet: short of ") && text.endsWith(" to make 1 Chest") && !text.contains("Pale"),
				"the builder's line with 4 oak planks: " + job.report(helper));
		});
		java.util.concurrent.atomic.AtomicBoolean given = new java.util.concurrent.atomic.AtomicBoolean();
		helper.runAfterDelay(800, () -> {
			job.chest().setItem(1, new ItemStack(Items.OAK_PLANKS, 4));
			given.set(true);
		});
		builtLeaving(helper, job, Map.of(), given::get);
	}

	/** 8 oak planks, the Carpenter saved and loaded again at ticks 150 and 300: the plain chest is still built, nothing lost or doubled. */
	//$ gametest_ticks_batch AREA '3600' '"qaCarvedChest1116cReload"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaCarvedChest1116cReload")
	public void theCarpenterSavedAndLoadedMidJobStillMakesThePlainChest(GameTestHelper helper) {
		Job job = job(helper, "reload", new ItemStack(Items.OAK_PLANKS, 8), new ItemStack(Items.OAK_PLANKS, 1));
		for (int t : new int[] {150, 300}) {
			helper.runAfterDelay(t, () -> job.carpenter().set(reload(helper.getLevel(), job.carpenter().get())));
		}
		builtLeaving(helper, job, Map.of(Items.OAK_PLANKS, 1));
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

	/** Items lying about near the test: any chest, carved chest, plank or log. */
	private static final class QaCarvedChestLoose {
		static int loose(GameTestHelper helper) {
			net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(20);
			return helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box, e -> {
				ItemStack s = e.getItem();
				String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
				return s.is(ItemTags.PLANKS) || s.is(ItemTags.LOGS) || path.endsWith("chest");
			}).stream().mapToInt(e -> e.getItem().getCount()).sum();
		}
	}
}
