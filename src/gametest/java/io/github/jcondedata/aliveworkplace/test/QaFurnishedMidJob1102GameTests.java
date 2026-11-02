package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * QA (2026-11-02) for furnished builds mid-job (ROADMAP 37.3, furniture-and-storage.md: "a config change or a removed mod
 * never changes a build going up"; the choice "costs their items"). Played with a real builder on the test nook, the test
 * "mod" {@code aliveworkplace_test_pieces} (its chair an upturned spruce stair) as the furniture mod: the furnishing switch
 * turned off mid-build (with and without a restart), the sites saved and loaded again and again while it builds (nothing
 * used twice, nothing lost), the chair's stair missing (the builder waits for it, no vanilla stand-in), and the chair broken
 * by a player while the job runs (the nook still ends with the mod's chair).
 */
public class QaFurnishedMidJob1102GameTests {
	private static final String AREA = "aliveworkplace_test:build_area";

	/** The nook's builder and site, begun with the test mod as the order and the switch on. */
	private record Job(ServerLevel level, Villager builder, BuildSite site, BlueprintData.Placement placement, BlockPos chairAt, BuildPlan plan) {
	}

	private static Job begin(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaFurnishedGameTests.resetAfter(helper);
		List<String> mods = Furnishings.MODS;
		boolean enabled = Furnishings.ENABLED;
		Leftovers.after(helper, () -> {
			Furnishings.MODS = mods;
			Furnishings.ENABLED = enabled;
		});
		Furnishings.ENABLED = true;
		Furnishings.MODS = List.of(QaFurnishedGameTests.PIECES);
		ServerLevel level = helper.getLevel();
		Villager builder = QaFurnishedGameTests.builderWithChest(helper);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(5, 2, 5)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, QaFurnishedGameTests.NOOK, placement);
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no plan for the nook");
		helper.assertFalse(site.furniture().isVanilla(), "the setup: the nook didn't choose the test mod's chair: " + site.furniture());
		return new Job(level, builder, site, placement, placement.origin().offset(QaFurnishedGameTests.CHAIR), plan);
	}

	private static boolean modChair(BlockState state) {
		return state.is(Blocks.SPRUCE_STAIRS) && state.getValue(StairBlock.HALF) == Half.TOP;
	}

	private static void restart(ServerLevel level) {
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
	}

	private static int count(Container chest, net.minecraft.world.item.Item item) {
		int n = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).is(item)) {
				n += chest.getItem(i).getCount();
			}
		}
		return n;
	}

	/** Every block the plan puts down stands as planned (furniture included), and nothing was skipped. */
	private static void assertWhole(GameTestHelper helper, Job job, String when) {
		BuildSite live = BuildSiteManager.get(job.level).get(job.site.id());
		helper.assertTrue(live == null, when + ": still building: " + (live == null ? "" : live.stage() + " " + live.status() + " missing " + live.missing()));
		helper.assertTrue(job.site.skipped() == 0, when + ": " + job.site.skipped() + " steps skipped");
		for (Map.Entry<BlockPos, BlockState> e : QaFurnishedGameTests.stepStates(job.plan).entrySet()) {
			BlockState there = job.level.getBlockState(e.getKey());
			helper.assertTrue(there.getBlock() == e.getValue().getBlock(), when + ": " + e.getKey() + " is " + there + ", planned " + e.getValue());
		}
		BlockState chair = job.level.getBlockState(job.chairAt);
		helper.assertTrue(modChair(chair), when + ": the finished nook's chair is " + chair + ", wanted the test mod's upturned stair it was begun with");
	}

	/**
	 * Turns {@code furnishedBuilds} off the first tick the build has placed something and its chair is still empty
	 * ({@code restart}: and the server restarts there); the nook must still end with the mod's chair.
	 */
	private static void switchedOff(GameTestHelper helper, boolean restart) {
		Job job = begin(helper);
		QaFurnishedGameTests.stock(helper, job.plan);
		String[] when = {null};
		helper.onEachTick(() -> {
			if (when[0] != null) {
				return;
			}
			BuildSite live = BuildSiteManager.get(job.level).get(job.site.id());
			if (live == null) {
				when[0] = "missed: finished before anything could change";
				return;
			}
			if (live.placed() == 0) {
				return;
			}
			if (!job.level.getBlockState(job.chairAt).isAir()) {
				when[0] = "missed: the chair went down with the first blocks";
				return;
			}
			Furnishings.ENABLED = false;
			BuildSite fresh = new BuildSite(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "test", QaFurnishedGameTests.NOOK,
				new BlueprintData.Placement(job.level.dimension().location(), job.placement.origin().above(10), Rotation.NONE, Mirror.NONE));
			if (fresh.plan(job.level) == null || !fresh.furniture().isVanilla()) {
				when[0] = "missed: with the switch off a new nook still chose " + fresh.furniture();
				return;
			}
			if (restart) {
				restart(job.level);
			}
			when[0] = "switched off at " + live.stage() + ", " + live.placed() + " placed" + (restart ? ", then restarted" : "");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(when[0] != null && !when[0].startsWith("missed"), String.valueOf(when[0]));
			assertWhole(helper, job, when[0]);
			helper.assertFalse(BuildSiteManager.get(job.level).furniture(job.placement).isVanilla(),
				"the finished nook records " + BuildSiteManager.get(job.level).furniture(job.placement) + " after the switch went off mid-build");
		});
	}

	/** The furnishing switch turned off mid-build, no restart: a config change never changes a build going up. */
	//$ gametest_ticks_batch AREA '3600' '"qaFurnishedMidJobOff"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaFurnishedMidJobOff")
	public void theSwitchTurnedOffMidBuildKeepsTheModsChair(GameTestHelper helper) {
		switchedOff(helper, false);
	}

	/** The furnishing switch turned off mid-build and the server restarted there: still the mod's chair. */
	//$ gametest_ticks_batch AREA '3600' '"qaFurnishedMidJobOffRestart"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaFurnishedMidJobOffRestart")
	public void theSwitchTurnedOffAndARestartMidBuildKeepsTheModsChair(GameTestHelper helper) {
		switchedOff(helper, true);
	}

	/**
	 * The sites saved and loaded every 5 ticks while the nook goes up: it finishes whole, with the mod's chair, and the chest
	 * holds exactly the two spare stairs it had beyond the plan (nothing taken twice, nothing lost).
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaFurnishedMidJobRestarts"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaFurnishedMidJobRestarts")
	public void restartsAllThroughTheBuildUseEachPieceOnce(GameTestHelper helper) {
		Job job = begin(helper);
		QaFurnishedGameTests.stock(helper, job.plan);
		Container chest = helper.getBlockEntity(QaFurnishedGameTests.CHEST);
		chest.setItem(20, new ItemStack(Items.SPRUCE_STAIRS, 2));
		int[] restarts = {0};
		helper.onEachTick(() -> {
			if (helper.getTick() % 5 == 0 && BuildSiteManager.get(job.level).get(job.site.id()) != null) {
				restart(job.level);
				restarts[0]++;
			}
		});
		helper.succeedWhen(() -> {
			assertWhole(helper, job, restarts[0] + " restarts");
			helper.assertTrue(restarts[0] >= 3, "only " + restarts[0] + " restarts while it built");
			int left = count(chest, Items.SPRUCE_STAIRS) + job.builder.getInventory().countItem(Items.SPRUCE_STAIRS);
			helper.assertTrue(left == 2, "after " + restarts[0] + " restarts the chest and builder hold " + left + " spruce stairs, wanted the 2 spares");
		});
	}

	/**
	 * The chair's stair missing from the chest: the builder waits for it by name and puts no vanilla stand-in on the spot;
	 * brought, it finishes with the mod's chair.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaFurnishedMidJobMissing"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaFurnishedMidJobMissing")
	public void aMissingPieceIsWaitedForNotReplaced(GameTestHelper helper) {
		Job job = begin(helper);
		QaFurnishedGameTests.stock(helper, job.plan);
		Container chest = helper.getBlockEntity(QaFurnishedGameTests.CHEST);
		helper.assertTrue(count(chest, Items.SPRUCE_STAIRS) >= 1, "the setup: the plan's materials have no spruce stair: " + job.plan.materials());
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).is(Items.SPRUCE_STAIRS)) {
				chest.setItem(i, ItemStack.EMPTY);
			}
		}
		boolean[] brought = {false};
		helper.runAfterDelay(900, () -> {
			BuildSite live = BuildSiteManager.get(job.level).get(job.site.id());
			if (live == null) {
				throw new GameTestAssertException("the nook finished without its chair's stair: the chair is " + job.level.getBlockState(job.chairAt));
			}
			helper.assertTrue(job.level.getBlockState(job.chairAt).isAir(), "waiting for the chair's stair, the spot holds " + job.level.getBlockState(job.chairAt));
			helper.assertTrue(live.missing().containsKey(Items.SPRUCE_STAIRS), "the builder waits for " + live.missing() + ", not the chair's spruce stair");
			helper.assertTrue(live.skipped() == 0, "the chair was skipped instead of waited for");
			chest.setItem(0, new ItemStack(Items.SPRUCE_STAIRS, 1));
			brought[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(brought[0], "not yet brought");
			assertWhole(helper, job, "the stair brought after a wait");
		});
	}

	/**
	 * A player breaks the chair the tick it is put down while the job runs: the nook still ends with the mod's chair, by the
	 * builder or by its repair, never the vanilla one.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaFurnishedMidJobBroken"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaFurnishedMidJobBroken")
	public void aChairBrokenMidJobIsPutBackAsTheModsChair(GameTestHelper helper) {
		Job job = begin(helper);
		QaFurnishedGameTests.stock(helper, job.plan);
		Container chest = helper.getBlockEntity(QaFurnishedGameTests.CHEST);
		chest.setItem(20, new ItemStack(Items.SPRUCE_STAIRS, 2));
		boolean[] broken = {false};
		helper.onEachTick(() -> {
			if (!broken[0] && !job.level.getBlockState(job.chairAt).isAir()) {
				job.level.destroyBlock(job.chairAt, false);
				broken[0] = true;
			}
		});
		String[] result = {null};
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0], "the chair never went down");
			BuildSite live = BuildSiteManager.get(job.level).get(job.site.id());
			helper.assertTrue(live == null, "still building: " + (live == null ? "" : live.stage() + " " + live.status() + " missing " + live.missing()));
			if (result[0] == null) {
				BlockState chair = job.level.getBlockState(job.chairAt);
				if (chair.isAir()) {
					// not put back by the build itself: the repair must ask for the mod's chair
					Upkeep.ENABLED = true;
					Upkeep.ASK = false;
					Furnishings.MODS = List.of();
					BuildSite repair = Upkeep.look(job.level, job.builder);
					BlockState back = repair == null ? null : QaFurnishedGameTests.stepStates(repair.plan(job.level)).get(job.chairAt);
					org.slf4j.LoggerFactory.getLogger("QaFurnishedMidJob").info("broken chair left for the repair, which plans {}", back);
					result[0] = back != null && modChair(back) ? "ok" : "the chair broken mid-job is put back as " + back + " (repair " + repair + "), not the mod's chair";
				} else {
					org.slf4j.LoggerFactory.getLogger("QaFurnishedMidJob").info("broken chair put back by the build: {}", chair);
					result[0] = modChair(chair) ? "ok" : "the chair broken mid-job was put back as " + chair + ", not the mod's chair";
				}
			}
			if (!result[0].equals("ok")) {
				throw new GameTestAssertException(result[0]);
			}
		});
	}
}
