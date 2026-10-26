package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (2026-10-26, lane-b-1026-0639): the builder's last look before a build is called done (B508, wiki builder.md
 * "Unfinished blocks are never claimed done"), tested from the spec: a block the builder laid that is broken later in the
 * build is put back before DONE, or counted as skipped; never a finished build with a hole and nothing skipped. Here the
 * break comes late (after the walls are up), with no spare left to put it back, and on a crew's build. Upkeep (the later
 * repair round) is off in every test, so only the build itself can put a block back.
 */
public class QaBuilderRecheck1026GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	/** The chest and barrels {@link QaBuilderDisruptionGameTests#start} stocks. */
	private static final List<BlockPos> SUPPLIES = List.of(new BlockPos(2, 2, 4), new BlockPos(1, 2, 4), new BlockPos(3, 2, 4),
		new BlockPos(4, 2, 4), new BlockPos(5, 2, 4));
	private static final BlockPos MATE_TABLE = new BlockPos(13, 2, 2);

	private static void upkeepOff(GameTestHelper helper) {
		boolean upkeep = Upkeep.ENABLED;
		Upkeep.ENABLED = false;
		Leftovers.after(helper, () -> Upkeep.ENABLED = upkeep);
	}

	private static boolean late(BuildSite site) {
		return site.stage() == BuildPlan.Stage.DECORATION || site.stage() == BuildPlan.Stage.LANDSCAPE;
	}

	/**
	 * A wall plank standing as drawn with no decoration (a torch) drawn on or beside it, or null. Breaking a plank a torch
	 * hangs on pops the torch too; the wiki says a block given up while its wall was gone is left to the repair round,
	 * so that case is not this test's.
	 */
	private static BlockPos aBarePlank(QaBuilderDisruptionGameTests.Run r) {
		for (BuildPlan.Step s : QaBuilderDisruptionGameTests.placed(r)) {
			if (s.state().is(Blocks.OAK_PLANKS) && r.plan().steps(BuildPlan.Stage.DECORATION).stream()
				.noneMatch(d -> !d.state().isAir() && d.pos().distManhattan(s.pos()) <= 1)) {
				return s.pos().immutable();
			}
		}
		return null;
	}

	/**
	 * The walls are all up and the builder is on the decoration or the landscaping when a player breaks a bare wall plank
	 * (nothing hangs on it) and keeps it. B508: the build goes back for it before it's called done. The hut ends exactly
	 * as drawn, the plank comes from the chest's spare (one fewer spare plank, nothing made up or lost) and nothing is
	 * counted skipped.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaRecheckLate"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaRecheckLate")
	public void aWallPlankBrokenAfterTheWallsAreUpIsBackBeforeDone(GameTestHelper helper) {
		QaBuilderDisruptionGameTests.Run r = QaBuilderDisruptionGameTests.start(helper, ModBlocks.BUILDERS_BENCH);
		upkeepOff(helper);
		BlockPos[] broken = new BlockPos[1];
		BuildPlan.Stage[] when = new BuildPlan.Stage[1];
		helper.onEachTick(() -> {
			if (broken[0] != null || !late(r.site())) {
				return;
			}
			BlockPos at = aBarePlank(r);
			if (at != null) {
				broken[0] = at;
				when[0] = r.site().stage();
				r.level().destroyBlock(at, false); // the player mined it and keeps the plank
				LOGGER.info("qa recheck late: plank at {} broken in {}, {}", helper.relativePos(at), when[0], QaBuilderDisruptionGameTests.state(helper, r));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0] != null, "setup: the build never reached decoration or landscaping with a plank standing: "
				+ QaBuilderDisruptionGameTests.state(helper, r));
			helper.assertFalse(QaBuilderDisruptionGameTests.building(r), "still building: " + QaBuilderDisruptionGameTests.state(helper, r));
			helper.assertTrue(r.level().getBlockState(broken[0]).is(Blocks.OAK_PLANKS), "the build ended (" + QaBuilderDisruptionGameTests.state(helper, r)
				+ ") with the plank broken in " + when[0] + " at " + helper.relativePos(broken[0]) + " still gone: " + r.level().getBlockState(broken[0]));
			QaBuilderDisruptionGameTests.assertFinished(helper, r, List.of());
			helper.assertTrue(r.site().skipped() == 0, "the plank was put back, yet " + r.site().skipped() + " step(s) counted skipped");
			QaBuilderDisruptionGameTests.assertNothingMadeOrLost(helper, r, Map.of(Items.OAK_PLANKS, -1));
		});
	}

	/**
	 * Late in the build a player takes every spare plank from the chest, the barrels and the builder's bag, then breaks a
	 * wall plank. There is nothing to put it back with, so the build must not be claimed finished with the hole: either it
	 * ends with the plank counted as skipped, or it still waits for planks (the builder's list shows them missing). No
	 * plank is ever made up: the spot stays open and no planks turn up anywhere.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaRecheckNoSpare"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaRecheckNoSpare")
	public void aPlankBrokenWithNoSpareLeftIsNeverClaimedDone(GameTestHelper helper) {
		QaBuilderDisruptionGameTests.Run r = QaBuilderDisruptionGameTests.start(helper, ModBlocks.BUILDERS_BENCH);
		upkeepOff(helper);
		BlockPos[] broken = new BlockPos[1];
		int[] after = {0};
		helper.onEachTick(() -> {
			if (broken[0] != null) {
				after[0]++;
				return;
			}
			if (!late(r.site())) {
				return;
			}
			BlockPos at = aBarePlank(r);
			if (at == null) {
				return;
			}
			int taken = 0;
			for (BlockPos p : SUPPLIES) {
				Container c = helper.getBlockEntity(p);
				for (int i = 0; i < c.getContainerSize(); i++) {
					if (c.getItem(i).is(Items.OAK_PLANKS)) {
						taken += c.getItem(i).getCount();
						c.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
					}
				}
			}
			taken += ModAttachments.BUILDER_BAG.getOrCreate(r.builder()).remove(Items.OAK_PLANKS, 10_000);
			broken[0] = at;
			r.level().destroyBlock(at, false);
			LOGGER.info("qa recheck no spare: took {} spare planks, broke the plank at {} in {}", taken, helper.relativePos(at), r.site().stage());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0] != null, "setup: the build never reached decoration or landscaping with a plank standing: "
				+ QaBuilderDisruptionGameTests.state(helper, r));
			helper.assertTrue(after[0] > 200, "giving the builder time");
			helper.assertTrue(r.level().getBlockState(broken[0]).isAir(), "a plank was made up from nothing at " + helper.relativePos(broken[0])
				+ ": " + r.level().getBlockState(broken[0]));
			int loose = QaBuilderDisruptionGameTests.loose(helper, Items.OAK_PLANKS);
			helper.assertTrue(loose == 0, loose + " planks turned up after every spare was taken");
			if (QaBuilderDisruptionGameTests.building(r)) {
				Integer wanted = r.site().missing().get(Items.OAK_PLANKS);
				helper.assertTrue(wanted != null && wanted > 0, "the build is neither finished nor waiting for planks: "
					+ QaBuilderDisruptionGameTests.state(helper, r));
			} else {
				helper.assertTrue(r.site().skipped() >= 1, "the build was called done with the broken plank's spot open and skipped "
					+ r.site().skipped() + ": a hole claimed done");
			}
			LOGGER.info("qa recheck no spare: {}", QaBuilderDisruptionGameTests.state(helper, r));
		});
	}

	/**
	 * A crew's build (workplaceBuildersHelp on, an idle builder at his own bench nearby helps). Mid-structure a player
	 * breaks a wall plank the crew laid. B508 holds for a crew too: the hut ends exactly as drawn with nothing skipped,
	 * nothing made up or lost (one spare plank fewer), and the helper is idle again with an empty bag.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaRecheckCrew"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaRecheckCrew")
	public void aPlankBrokenOnACrewBuildIsBackBeforeDone(GameTestHelper helper) {
		QaBuilderDisruptionGameTests.Run r = QaBuilderDisruptionGameTests.start(helper, ModBlocks.BUILDERS_BENCH);
		upkeepOff(helper);
		boolean protection = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = protection);
		r.level().getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, r.level().getServer()); // start() puts it back after
		helper.setBlock(MATE_TABLE, ModBlocks.BUILDERS_BENCH);
		QaBuilderDisruptionGameTests.ensurePoi(r.level(), helper.absolutePos(MATE_TABLE));
		Villager mate = helper.spawn(EntityType.VILLAGER, MATE_TABLE.south().east());
		Builders.employ(r.level(), mate, helper.absolutePos(MATE_TABLE));
		BlockPos[] broken = new BlockPos[1];
		boolean[] helped = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			helped[0] |= Builders.isHelping(mate);
			if (broken[0] != null || r.site().stage() != BuildPlan.Stage.STRUCTURE || QaBuilderDisruptionGameTests.placed(r).size() < 4) {
				return;
			}
			BlockPos at = aBarePlank(r);
			if (at != null) {
				broken[0] = at;
				r.level().destroyBlock(at, false);
				LOGGER.info("qa recheck crew: plank at {} broken, helping {}, {}", helper.relativePos(at), Builders.isHelping(mate),
					QaBuilderDisruptionGameTests.state(helper, r));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0] != null, "setup: no plank was ever placed to break");
			helper.assertTrue(helped[0], "setup: the idle builder never helped");
			helper.assertFalse(QaBuilderDisruptionGameTests.building(r), "still building: " + QaBuilderDisruptionGameTests.state(helper, r));
			helper.assertTrue(r.level().getBlockState(broken[0]).is(Blocks.OAK_PLANKS), "the crew's build ended (" + QaBuilderDisruptionGameTests.state(helper, r)
				+ ") with the broken plank at " + helper.relativePos(broken[0]) + " still gone");
			QaBuilderDisruptionGameTests.assertFinished(helper, r, List.of());
			helper.assertTrue(r.site().skipped() == 0, r.site().skipped() + " step(s) counted skipped on the crew's build");
			helper.assertFalse(Builders.isHelping(mate), "the helper is still on the finished build");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(mate).isEmpty(), "the helper's bag isn't empty after the build: "
				+ ModAttachments.BUILDER_BAG.getOrCreate(mate).stacks());
			QaBuilderDisruptionGameTests.assertNothingMadeOrLost(helper, r, Map.of(Items.OAK_PLANKS, -1));
		});
	}

	/**
	 * Wiki builder.md: the Builder "goes back at most twice a build; a block broken again after that is counted in skipped
	 * and left for the repair round". A player breaks the same bare plank late in the build every time it is put back: the
	 * build still ends, it went back exactly twice (so the plank was laid again twice, never a third time), and the plank
	 * is counted as skipped.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaRecheckTwice"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaRecheckTwice")
	public void aPlankBrokenEveryTimeIsLaidAgainTwiceAndNoMore(GameTestHelper helper) {
		QaBuilderDisruptionGameTests.Run r = QaBuilderDisruptionGameTests.start(helper, ModBlocks.BUILDERS_BENCH);
		upkeepOff(helper);
		BlockPos[] broken = new BlockPos[1];
		int[] breaks = new int[1];
		helper.onEachTick(() -> {
			if (broken[0] == null) {
				if (late(r.site())) {
					broken[0] = aBarePlank(r);
				}
			}
			if (broken[0] != null && r.level().getBlockState(broken[0]).is(Blocks.OAK_PLANKS)) {
				r.level().destroyBlock(broken[0], false);
				breaks[0]++;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0] != null, "setup: the build never reached decoration or landscaping with a plank standing: "
				+ QaBuilderDisruptionGameTests.state(helper, r));
			helper.assertFalse(QaBuilderDisruptionGameTests.building(r), "still building: " + QaBuilderDisruptionGameTests.state(helper, r));
			helper.assertTrue(r.site().rechecks() == 2, "the build went back " + r.site().rechecks() + " times for the broken plank, wanted 2");
			helper.assertTrue(breaks[0] == 3, "the plank was laid again " + (breaks[0] - 1) + " times, wanted twice");
			helper.assertTrue(r.site().skipped() >= 1, "the build ended with the plank gone and skipped " + r.site().skipped());
		});
	}

	/**
	 * Save and reload (the plank broken during decoration or landscaping) while the build has gone back for a broken block (the server restarts at that moment). The saved
	 * site keeps where it was (the stage it went back to), how often it went back and its skipped count, so a restart
	 * neither forgets the broken block nor lets the build go back without limit.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaRecheckSave"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaRecheckSave")
	public void aSaveWhileGoingBackForABrokenBlockKeepsTheWayBack(GameTestHelper helper) {
		QaBuilderDisruptionGameTests.Run r = QaBuilderDisruptionGameTests.start(helper, ModBlocks.BUILDERS_BENCH);
		upkeepOff(helper);
		BlockPos[] broken = new BlockPos[1];
		boolean[] checked = {false};
		helper.onEachTick(() -> {
			if (checked[0]) {
				return;
			}
			if (broken[0] == null) {
				if (late(r.site())) {
					broken[0] = aBarePlank(r);
					if (broken[0] != null) {
						r.level().destroyBlock(broken[0], false);
					}
				}
				return;
			}
			if (r.site().rechecks() > 0) {
				BuildSite back = BuildSite.load(r.site().save());
				helper.assertTrue(back != null, "the site didn't load back");
				helper.assertTrue(back.stage() == r.site().stage(), "the stage it went back to wasn't saved: " + r.site().stage() + " -> " + back.stage());
				helper.assertTrue(back.rechecks() == r.site().rechecks(), "rechecks not saved: " + r.site().rechecks() + " -> " + back.rechecks());
				helper.assertTrue(back.skipped() == r.site().skipped(), "skipped not saved: " + r.site().skipped() + " -> " + back.skipped());
				checked[0] = true;
				LOGGER.info("qa recheck save: went back to {} (rechecks {}), saved and loaded the same", back.stage(), back.rechecks());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0] != null, "setup: the build never reached decoration or landscaping with a plank standing: "
				+ QaBuilderDisruptionGameTests.state(helper, r));
			helper.assertTrue(checked[0], "the build never went back for the plank broken late: " + QaBuilderDisruptionGameTests.state(helper, r));
		});
	}
}
