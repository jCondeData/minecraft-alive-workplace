package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * QA (2026-11-16, qa-lane-b) for the Builder's furnishing step with "any mod's piece" (ROADMAP 37.3, 37.13;
 * furniture-and-storage.md: "the Builder builds whichever mod's piece its chests (or its bag) hold ... in the same place,
 * footprint and facing. A spot already going up keeps its piece, a spot none of whose pieces is in the chests keeps the
 * planned piece (the builder waits for it)"; "a building, and a build already going up (over a restart too), keeps the
 * pieces it began with whatever changes later"). Played with a real builder on the test nook and two test "mods":
 * {@code aliveworkplace_test_pieces} (first in the order, its chair an upturned spruce stair) and
 * {@code aliveworkplace_test_alt} (its spruce chair a block of spruce wood, the same piece by the test equivalents file).
 * Corners no other class plays end to end: only the other mod's chair in the chest while the sites are saved and loaded
 * again and again; that chair broken after the build and repaired; a restart while waiting and then the other mod's chair
 * brought; and {@code furnishedBuilds} switched off while waiting, then only the other mod's chair brought.
 */
public class QaFurnishReload1116bGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String PIECES = QaFurnishedGameTests.PIECES;
	private static final String ALT = "aliveworkplace_test_alt";

	private record Job(ServerLevel level, Villager builder, BuildSite site, BlueprintData.Placement placement, BlockPos chairAt, BuildPlan plan) {
	}

	private static Job begin(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaFurnishedGameTests.resetAfter(helper);
		List<String> mods = Furnishings.MODS;
		boolean enabled = Furnishings.ENABLED;
		Leftovers.after(helper, () -> {
			new WorkplaceConfig().apply();
			Furnishings.MODS = mods;
			Furnishings.ENABLED = enabled;
		});
		WorkplaceConfig.parse("{\"furnishedBuilds\": true, \"furnitureMods\": [\"" + PIECES + "\", \"" + ALT + "\"]}").apply();
		helper.assertTrue(Furnishings.ENABLED && Furnishings.MODS.equals(List.of(PIECES, ALT)), "the setup: the config gave "
			+ Furnishings.ENABLED + " " + Furnishings.MODS);
		ServerLevel level = helper.getLevel();
		Villager builder = QaFurnishedGameTests.builderWithChest(helper);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(5, 2, 5)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, QaFurnishedGameTests.NOOK, placement);
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no plan for the nook");
		helper.assertTrue(PIECES.equals(chairMod(site.furniture())), "the setup: the nook didn't choose the first mod's chair: " + site.furniture());
		QaFurnishedGameTests.stock(helper, plan);
		Container chest = helper.getBlockEntity(QaFurnishedGameTests.CHEST);
		helper.assertTrue(count(chest, Items.SPRUCE_STAIRS) == 1, "the setup: the plan wants " + count(chest, Items.SPRUCE_STAIRS)
			+ " spruce stairs, the test expects the chair's one: " + plan.materials());
		helper.assertTrue(count(chest, Items.SPRUCE_WOOD) == 0, "the setup: the nook already uses spruce wood: " + plan.materials());
		take(chest, Items.SPRUCE_STAIRS);
		return new Job(level, builder, site, placement, placement.origin().offset(QaFurnishedGameTests.CHAIR), plan);
	}

	private static String chairMod(FurnitureChoice choice) {
		return choice.spots().isEmpty() ? "vanilla" : choice.spots().values().iterator().next().mod();
	}

	private static boolean firstModsChair(BlockState state) {
		return state.is(Blocks.SPRUCE_STAIRS) && state.getValue(StairBlock.HALF) == Half.TOP;
	}

	private static BuildSite live(Job job) {
		return BuildSiteManager.get(job.level).get(job.site.id());
	}

	private static void restart(ServerLevel level) {
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
	}

	private static int count(Container chest, Item item) {
		int n = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).is(item)) {
				n += chest.getItem(i).getCount();
			}
		}
		return n;
	}

	private static void take(Container chest, Item item) {
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).is(item)) {
				chest.setItem(i, ItemStack.EMPTY);
			}
		}
	}

	private static void put(Container chest, ItemStack stack) {
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).isEmpty()) {
				chest.setItem(i, stack);
				return;
			}
		}
		throw new GameTestAssertException("the chest is full");
	}

	/** The chair spot holds only air or one of the two mods' chairs while the job runs; skipped steps are tracked. */
	private static void watch(GameTestHelper helper, Job job, int[] skipped) {
		helper.onEachTick(() -> {
			BuildSite live = live(job);
			if (live != null) {
				skipped[0] = Math.max(skipped[0], live.skipped());
			}
			BlockState chair = job.level.getBlockState(job.chairAt);
			if (!chair.isAir() && !firstModsChair(chair) && !chair.is(Blocks.SPRUCE_WOOD)) {
				throw new GameTestAssertException("tick " + helper.getTick() + ": the chair spot holds a stand-in " + chair);
			}
		});
	}

	/** The nook is finished, nothing skipped, every planned block but the chair stands, and the chair is {@code chair}. */
	private static void assertWhole(GameTestHelper helper, Job job, int skipped, boolean altChair, String when) {
		BuildSite live = live(job);
		helper.assertTrue(live == null, when + ": still building: " + (live == null ? "" : live.stage() + " " + live.status() + " missing " + live.missing()));
		helper.assertTrue(skipped == 0, when + ": " + skipped + " steps skipped");
		for (Map.Entry<BlockPos, BlockState> e : QaFurnishedGameTests.stepStates(job.plan).entrySet()) {
			if (e.getKey().equals(job.chairAt)) {
				continue;
			}
			BlockState there = job.level.getBlockState(e.getKey());
			helper.assertTrue(there.getBlock() == e.getValue().getBlock(), when + ": " + e.getKey() + " is " + there + ", planned " + e.getValue());
		}
		BlockState chair = job.level.getBlockState(job.chairAt);
		if (altChair) {
			helper.assertTrue(chair.is(Blocks.SPRUCE_WOOD), when + ": the finished nook's chair is " + chair + ", wanted the other mod's chair "
				+ "(spruce wood), the only chair in its chest");
		} else {
			helper.assertTrue(firstModsChair(chair), when + ": the finished nook's chair is " + chair + ", wanted the first mod's upturned stair it was begun with");
		}
	}

	/**
	 * Only the other mod's chair in the chest, the sites saved and loaded every 7 ticks while the nook goes up: the builder
	 * builds that chair (its chest holds it), uses it once, skips nothing, and the finished nook records the other mod.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qa1116bAltRestarts"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qa1116bAltRestarts")
	public void onlyTheOtherModsChairInTheChestIsBuiltThroughRestarts(GameTestHelper helper) {
		Job job = begin(helper);
		Container chest = helper.getBlockEntity(QaFurnishedGameTests.CHEST);
		put(chest, new ItemStack(Items.SPRUCE_WOOD, 2));
		int[] skipped = {0};
		watch(helper, job, skipped);
		int[] restarts = {0};
		helper.onEachTick(() -> {
			if (helper.getTick() % 7 == 0 && live(job) != null) {
				restart(job.level);
				restarts[0]++;
			}
		});
		helper.succeedWhen(() -> {
			assertWhole(helper, job, skipped[0], true, restarts[0] + " restarts");
			helper.assertTrue(restarts[0] >= 3, "only " + restarts[0] + " restarts while it built");
			int left = count(chest, Items.SPRUCE_WOOD) + job.builder.getInventory().countItem(Items.SPRUCE_WOOD);
			helper.assertTrue(left == 1, "after " + restarts[0] + " restarts the chest and builder hold " + left + " spruce wood, wanted 1 of the 2 (one chair)");
			FurnitureChoice kept = BuildSiteManager.get(job.level).furniture(job.placement);
			helper.assertTrue(ALT.equals(chairMod(kept)), "the finished nook records its chair as " + chairMod(kept) + ", wanted " + ALT);
		});
	}

	/**
	 * The other mod's chair built (the only one in the chest), then broken by a player after the build; with that mod since
	 * taken out of the order, the builder's repair still puts back the other mod's chair: the building keeps its pieces.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qa1116bAltRepair"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qa1116bAltRepair")
	public void theOtherModsChairBrokenAfterTheBuildIsRepairedAsThatChair(GameTestHelper helper) {
		Job job = begin(helper);
		Container chest = helper.getBlockEntity(QaFurnishedGameTests.CHEST);
		put(chest, new ItemStack(Items.SPRUCE_WOOD, 1));
		int[] skipped = {0};
		watch(helper, job, skipped);
		String[] result = {null};
		helper.succeedWhen(() -> {
			if (result[0] == null) {
				assertWhole(helper, job, skipped[0], true, "built");
				job.level.destroyBlock(job.chairAt, false);
				WorkplaceConfig.parse("{\"furnishedBuilds\": true, \"furnitureMods\": [\"" + PIECES + "\"]}").apply();
				restart(job.level);
				Upkeep.ENABLED = true;
				Upkeep.ASK = false;
				BuildSite repair = Upkeep.look(job.level, job.builder);
				BlockState back = repair == null ? null : QaFurnishedGameTests.stepStates(repair.plan(job.level)).get(job.chairAt);
				result[0] = back != null && back.is(Blocks.SPRUCE_WOOD) ? "ok"
					: "the other mod's chair broken after the build is put back as " + back + " (repair " + repair + "), not that chair (spruce wood)";
			}
			if (!result[0].equals("ok")) {
				throw new GameTestAssertException(result[0]);
			}
		});
	}

	/**
	 * No chair in the chest: the builder waits for it. The server restarts while it waits, then a player brings only the
	 * other mod's chair: the builder builds that one (whichever mod's piece its chests hold) and the nook finishes whole.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qa1116bWaitRestartAlt"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qa1116bWaitRestartAlt")
	public void aRestartWhileWaitingThenTheOtherModsChairBroughtIsBuilt(GameTestHelper helper) {
		Job job = begin(helper);
		Container chest = helper.getBlockEntity(QaFurnishedGameTests.CHEST);
		int[] skipped = {0};
		watch(helper, job, skipped);
		boolean[] brought = {false};
		helper.runAfterDelay(500, () -> restart(job.level));
		helper.runAfterDelay(900, () -> {
			BuildSite live = live(job);
			if (live == null) {
				throw new GameTestAssertException("the nook finished with no chair in its chest: the chair is " + job.level.getBlockState(job.chairAt));
			}
			helper.assertTrue(job.level.getBlockState(job.chairAt).isAir(), "waiting for the chair, the spot holds " + job.level.getBlockState(job.chairAt));
			helper.assertTrue(PIECES.equals(chairMod(live.furniture())), "after the restart the waiting nook's chair is " + chairMod(live.furniture()));
			put(chest, new ItemStack(Items.SPRUCE_WOOD, 1));
			brought[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(brought[0], "not yet brought");
			assertWhole(helper, job, skipped[0], true, "a restart while waiting, then the other mod's chair brought");
		});
	}

	/**
	 * No chair in the chest; while the builder waits, {@code furnishedBuilds} is switched off in the config and then only the
	 * other mod's chair is brought. The build going up keeps the piece it began with and nothing switches with the switch
	 * off: the builder keeps waiting for the first mod's chair (no stand-in, no swap, nothing skipped), and finishes with it
	 * once a player brings it.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qa1116bOffWaiting"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qa1116bOffWaiting")
	public void switchedOffWhileWaitingTheBuildKeepsWaitingForTheChairItBegan(GameTestHelper helper) {
		Job job = begin(helper);
		Container chest = helper.getBlockEntity(QaFurnishedGameTests.CHEST);
		int[] skipped = {0};
		helper.onEachTick(() -> {
			BuildSite live = live(job);
			if (live != null) {
				skipped[0] = Math.max(skipped[0], live.skipped());
			}
			BlockState chair = job.level.getBlockState(job.chairAt);
			if (!chair.isAir() && !firstModsChair(chair)) {
				throw new GameTestAssertException("tick " + helper.getTick() + ": with the switch off while waiting the chair spot got " + chair
					+ ", wanted it kept for the first mod's chair the build began with");
			}
		});
		boolean[] brought = {false};
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(live(job) != null, "the nook finished with no chair in its chest");
			WorkplaceConfig.parse("{\"furnishedBuilds\": false, \"furnitureMods\": [\"" + PIECES + "\", \"" + ALT + "\"]}").apply();
			put(chest, new ItemStack(Items.SPRUCE_WOOD, 1));
		});
		helper.runAfterDelay(900, () -> {
			BuildSite live = live(job);
			if (live == null) {
				throw new GameTestAssertException("with the switch off the nook finished without the chair it began with: the chair is "
					+ job.level.getBlockState(job.chairAt));
			}
			helper.assertTrue(PIECES.equals(chairMod(live.furniture())), "with the switch off the waiting nook's chair became " + chairMod(live.furniture()));
			helper.assertTrue(live.missing().containsKey(Items.SPRUCE_STAIRS), "with the switch off the builder waits for " + live.missing()
				+ ", not the chair's spruce stair");
			put(chest, new ItemStack(Items.SPRUCE_STAIRS, 1));
			brought[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(brought[0], "not yet brought");
			assertWhole(helper, job, skipped[0], false, "switched off while waiting, then the stair brought");
			helper.assertTrue(count(chest, Items.SPRUCE_WOOD) + job.builder.getInventory().countItem(Items.SPRUCE_WOOD) == 1,
				"the other mod's chair was used though the switch was off");
		});
	}
}
