package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.LOGGER;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.Run;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (2026-11-03, the tester of lane-a-1102-2123's fixes): the three Builder fixes checked from their spec, on cases the
 * bug tests that came with them don't cover.
 * <ul>
 * <li>B837 (builder.md "CLEAR takes away what is in the way ... sand or gravel standing over the box falls into each layer as
 * it is taken: the builder waits ... and digs out what fell in"; ROADMAP 23.1 "no item is duplicated or lost"): a red sand
 * box under a concrete powder dune, a sand column 18 high on a dirt lid, a dune with the world saved and loaded every 200
 * ticks of CLEAR; every dug block ends up loose (chests, bags, ground), none lost or made. Control: sand a player puts in
 * the finished hut is left alone.</li>
 * <li>B838 (builder.md "LANDSCAPE leaves the ground in and under a neighboring build alone ... a site under way or a
 * finished building"): the pad under a finished neighbor, under a turned (rotated) neighbor site waiting with no builder,
 * under a neighbor touching only at a corner. Control: a plain dirt hill in the ring, no neighbor, is still leveled.</li>
 * <li>B839 (builder.md "Working hours ... works to dusk (11000) ... stops building as soon as his schedule leaves work"):
 * dusk with the clock running from 10950, a save and load at midnight, and the clock held at midday as a control.</li>
 * </ul>
 * Layout and helpers: {@link QaBuilderTerrain1102GameTests} (the test hut, 5x4x5, from a chest and barrels by the bench).
 */
public class QaBuilderFixes1103GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final int EXTRA = QaBuilderTerrain1102GameTests.EXTRA;

	// ===================================================================================================== B837

	/**
	 * Red sand fills the hut's box, and a white concrete powder dune three deep stands over it (both fall). The hut ends
	 * as drawn with no falling block in it, and all 100 red sand and 75 concrete powder dug out are loose (chests, bags,
	 * ground): none lost, none made.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaFix1103SandRed"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaFix1103SandRed")
	public void redSandUnderAConcretePowderDuneIsAllDugOutAndKept(GameTestHelper helper) {
		setUp(helper);
		fill(helper, 6, 10, 2, 5, 6, 10, Blocks.RED_SAND);
		fill(helper, 6, 10, 6, 8, 6, 10, Blocks.WHITE_CONCRETE_POWDER);
		Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		helper.succeedWhen(() -> {
			assertDugOut(helper, r);
			assertDugKept(helper, r, Map.of(Items.RED_SAND, 100, Items.WHITE_CONCRETE_POWDER, 75));
		});
	}

	/**
	 * The hut's box is empty but for a dirt lid on its top layer, and a sand column 3x3 and 18 high (far taller than the
	 * box) stands on the lid over the room. Taking the lid lets 162 sand come down in a cascade that runs on after the
	 * lid is gone. The hut ends as drawn with no sand in it, and the 25 dirt and 162 sand are all loose.
	 */
	//$ gametest_ticks_batch HUGE '14000' '"qaFix1103SandTall"'
	@GameTest(template = HUGE, timeoutTicks = 14000, batch = "qaFix1103SandTall")
	public void aSandColumnTallerThanTheBoxOnADirtLidIsAllDugOut(GameTestHelper helper) {
		setUp(helper);
		fill(helper, 6, 10, 5, 5, 6, 10, Blocks.DIRT);
		fill(helper, 7, 9, 6, 23, 7, 9, Blocks.SAND);
		Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		helper.succeedWhen(() -> {
			assertDugOut(helper, r);
			assertDugKept(helper, r, Map.of(Items.DIRT, 25, Items.SAND, 162));
		});
	}

	/**
	 * A sand dune eight deep (the box and four over it, 200 sand) with the world saved and loaded (sites from their save,
	 * the builder as a fresh entity) every 200 ticks while the site is in CLEAR, so loads fall between and inside the
	 * passes back over the CLEAR list. The hut ends as drawn with no sand in it, and all 200 sand are loose.
	 */
	//$ gametest_ticks_batch HUGE '14000' '"qaFix1103SandReloads"'
	@GameTest(template = HUGE, timeoutTicks = 14000, batch = "qaFix1103SandReloads")
	public void aDuneReloadedEvery200TicksOfClearIsAllDugOut(GameTestHelper helper) {
		setUp(helper);
		fill(helper, 6, 10, 2, 9, 6, 10, Blocks.SAND);
		Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		int[] reloads = {0};
		long[] last = {0};
		helper.onEachTick(() -> {
			BuildSite now = r.site();
			if (now == null || now.stage() != BuildPlan.Stage.CLEAR || reloads[0] >= 8 || helper.getTick() - last[0] < 200) {
				return;
			}
			last[0] = helper.getTick();
			reloads[0]++;
			int sand = QaBuilderTerrain1102GameTests.count(r, s -> s.is(Blocks.SAND));
			r.builder()[0] = QaBuilderTerrain1102GameTests.reload(helper, r.level(), r.builder()[0]);
			LOGGER.info("qa fix1103 reloads: reload {} mid-CLEAR at tick {}, {} sand in the box", reloads[0], helper.getTick(), sand);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloads[0] >= 2, "setup: reloaded only " + reloads[0] + " time(s) in CLEAR");
			assertDugOut(helper, r);
			assertDugKept(helper, r, Map.of(Items.SAND, 200));
		});
	}

	/**
	 * Control: once the hut is done (its site gone) a player puts a sand block on its floor. It isn't a site any more:
	 * 600 ticks on the sand is still there, no site is open on the spot, and the builder took nothing.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaFix1103SandAfter"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaFix1103SandAfter")
	public void sandAPlayerPutsInTheFinishedHutIsLeftAlone(GameTestHelper helper) {
		setUp(helper);
		Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		BlockPos room = new BlockPos(8, 3, 8);
		long[] putAt = {-1};
		helper.onEachTick(() -> {
			if (putAt[0] < 0 && r.site() == null) {
				QaBuilderTerrain1102GameTests.assertFinished(helper, r);
				helper.setBlock(room, Blocks.SAND);
				putAt[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(putAt[0] >= 0 && helper.getTick() - putAt[0] >= 600, "the hut isn't done and watched 600 ticks yet");
			helper.assertTrue(helper.getBlockState(room).is(Blocks.SAND), "the player's sand in the finished hut was taken: " + helper.getBlockState(room));
			BlueprintData.Placement at = new BlueprintData.Placement(r.level().dimension().location(), helper.absolutePos(new BlockPos(6, 2, 6)),
				Rotation.NONE, Mirror.NONE);
			for (BuildSite s : BuildSiteManager.get(r.level()).all()) {
				helper.assertFalse(s.placement().equals(at), "a site is open again on the finished hut: stage " + s.stage());
			}
			helper.assertTrue(QaBuilderTerrain1102GameTests.loose(helper, r, Items.SAND) == 0, "sand turned up in the chests or bags");
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
		});
	}

	// ===================================================================================================== B838

	/**
	 * Hut B is built on a one-high stone pad (x 11-15) and finished first: its site is gone and it is a finished building.
	 * Then hut A is built beside it, boxes touching. A's levelling ring never digs B's pad, B still stands as drawn, and
	 * every material of both adds up exactly (no stone dug, so no cobblestone either).
	 */
	//$ gametest_ticks_batch HUGE '14000' '"qaFix1103NbFinished"'
	@GameTest(template = HUGE, timeoutTicks = 14000, batch = "qaFix1103NbFinished")
	public void aFinishedNeighborsPadIsNeverDug(GameTestHelper helper) {
		setUp(helper);
		fill(helper, 11, 15, 2, 2, 6, 10, Blocks.STONE);
		Run b = QaBuilderMidJob1102GameTests.startAt(helper, new BlockPos(24, 2, 8), new BlockPos(11, 3, 6));
		QaBuilderTerrain1102GameTests.watchDeath(helper, b);
		Run[] a = {null};
		helper.onEachTick(() -> {
			if (a[0] == null && b.site() == null) {
				QaBuilderTerrain1102GameTests.assertFinished(helper, b);
				BlueprintData.Placement bAt = new BlueprintData.Placement(b.level().dimension().location(), helper.absolutePos(new BlockPos(11, 3, 6)),
					Rotation.NONE, Mirror.NONE);
				helper.assertTrue(BuildSiteManager.get(b.level()).finishedIn(b.level()).stream().anyMatch(f -> f.placement().equals(bAt)),
					"setup: hut B isn't recorded as a finished building");
				a[0] = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
				helper.assertTrue(a[0].plan().bounds().maxX() + 1 == b.plan().bounds().minX(), "setup: the boxes don't touch");
				LOGGER.info("qa fix1103 finished neighbor: hut B done at tick {}, hut A started", helper.getTick());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(a[0] != null, "hut B isn't finished yet: " + QaBuilderTerrain1102GameTests.state(helper, b));
			QaBuilderTerrain1102GameTests.assertFinished(helper, a[0]);
			helper.assertTrue(b.plan().unfinished(b.level()).isEmpty(), "hut B no longer as drawn after hut A was built");
			assertPad(helper, 11, 15, 6, 10);
			assertBothAddUp(helper, a[0], b);
		});
	}

	/**
	 * Hut B is a site under way that hasn't started (placed turned 90 degrees, no builder: it stays in CLEAR), its box
	 * touching hut A's on the east, a stone pad under it. Hut A's levelling ring leaves B's pad alone (a turned box too),
	 * and A's materials add up exactly.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaFix1103NbWaiting"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaFix1103NbWaiting")
	public void aTurnedNeighborSiteStillInClearKeepsItsPad(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		// turned 90 degrees clockwise about its origin, the hut's box runs west of the origin: x 11-15 from an origin at 15
		BlueprintData.Placement bAt = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(15, 3, 6)),
			Rotation.CLOCKWISE_90, Mirror.NONE);
		BuildSite bSite = BuildSiteManager.get(level).create(UUID.randomUUID(), "qa", TEST_HUT, bAt);
		UUID bId = bSite.id();
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(bId) != null) {
				BuildSiteManager.get(level).remove(bId);
			}
		});
		BoundingBox bBox = bSite.plan(level).bounds();
		BlockPos lo = helper.relativePos(new BlockPos(bBox.minX(), bBox.minY(), bBox.minZ()));
		BlockPos hi = helper.relativePos(new BlockPos(bBox.maxX(), bBox.maxY(), bBox.maxZ()));
		helper.assertTrue(lo.getY() == 3, "setup: hut B's box starts at y " + lo.getY());
		fill(helper, lo.getX(), hi.getX(), 2, 2, lo.getZ(), hi.getZ(), Blocks.STONE);
		Run a = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		helper.assertTrue(a.plan().bounds().maxX() + 1 == bBox.minX() && !a.plan().bounds().intersects(bBox),
			"setup: the boxes don't touch: " + a.plan().bounds() + " " + bBox);
		QaBuilderTerrain1102GameTests.watchDeath(helper, a);
		helper.succeedWhen(() -> {
			QaBuilderTerrain1102GameTests.assertFinished(helper, a);
			BuildSite still = BuildSiteManager.get(level).get(bId);
			LOGGER.info("qa fix1103 waiting neighbor: hut A done, hut B {}", still == null ? "gone" : still.stage());
			assertPad(helper, lo.getX(), hi.getX(), lo.getZ(), hi.getZ());
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, a);
		});
	}

	/**
	 * Hut B (with its own builder, built at the same time) touches hut A's box only at a corner: A at x 6-10, z 6-10, B
	 * at x 11-15, z 11-15, on a stone pad. A's ring reaches four columns of B's pad diagonally; none is dug, both huts end
	 * as drawn and every material of both adds up exactly.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaFix1103NbCorner"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaFix1103NbCorner")
	public void aNeighborTouchingOnlyAtACornerKeepsItsPad(GameTestHelper helper) {
		setUp(helper);
		fill(helper, 11, 15, 2, 2, 11, 15, Blocks.STONE);
		Run a = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		Run b = QaBuilderMidJob1102GameTests.startAt(helper, new BlockPos(24, 2, 4), new BlockPos(11, 3, 11));
		BoundingBox ab = a.plan().bounds();
		BoundingBox bb = b.plan().bounds();
		helper.assertTrue(ab.maxX() + 1 == bb.minX() && ab.maxZ() + 1 == bb.minZ(), "setup: the boxes don't meet at a corner: " + ab + " " + bb);
		QaBuilderTerrain1102GameTests.watchDeath(helper, a);
		QaBuilderTerrain1102GameTests.watchDeath(helper, b);
		helper.succeedWhen(() -> {
			QaBuilderTerrain1102GameTests.assertFinished(helper, a);
			QaBuilderTerrain1102GameTests.assertFinished(helper, b);
			assertPad(helper, 11, 15, 11, 15);
			assertBothAddUp(helper, a, b);
		});
	}

	/**
	 * Control (the fix mustn't be too broad): a plain dirt hill two high (x 11-15, z 6-10) beside the hut, no neighbor
	 * build anywhere. workplaceLevelGround 2: the two columns of the hill inside the ring (x 11-12) are dug away, 20
	 * dirt, which end up loose; the hill past the ring (x 13-15) stands.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaFix1103NbHill"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaFix1103NbHill")
	public void aPlainHillInTheRingWithNoNeighborIsStillLeveled(GameTestHelper helper) {
		setUp(helper);
		fill(helper, 11, 15, 2, 3, 6, 10, Blocks.DIRT);
		Run a = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		QaBuilderTerrain1102GameTests.watchDeath(helper, a);
		helper.succeedWhen(() -> {
			QaBuilderTerrain1102GameTests.assertFinished(helper, a);
			List<String> left = new ArrayList<>();
			int beyond = 0;
			for (int x = 11; x <= 15; x++) {
				for (int z = 6; z <= 10; z++) {
					for (int y = 2; y <= 3; y++) {
						boolean dirt = helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.DIRT);
						if (x <= 12 && dirt) {
							left.add("(" + x + "," + y + "," + z + ")");
						} else if (x >= 13 && dirt) {
							beyond++;
						}
					}
				}
			}
			helper.assertTrue(left.isEmpty(), left.size() + " block(s) of the hill inside the leveled ring not dug: " + left);
			helper.assertTrue(beyond == 30, "the hill past the ring changed: " + beyond + " of 30 dirt left");
			int dirt = QaBuilderTerrain1102GameTests.loose(helper, a, Items.DIRT);
			helper.assertTrue(dirt == 20, "dug 20 dirt but " + dirt + " are loose (chests, bags, ground)");
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, a);
		});
	}

	// ===================================================================================================== B839

	/**
	 * Mid-STRUCTURE the clock is set to 10950 and runs on a tick a tick: dusk (11000) comes 50 ticks later. From 200
	 * ticks after dusk to 12600 (in bed time) no block of the hut changes and nor does his bag. In the morning he
	 * finishes: as drawn, every material adds up.
	 */
	//$ gametest_ticks_batch HUGE '16000' '"qaFix1103Dusk"'
	@GameTest(template = HUGE, timeoutTicks = 16000, batch = "qaFix1103Dusk")
	public void aBuilderStopsWithin200TicksOfDusk(GameTestHelper helper) {
		Run r = slowHut(helper);
		int[] phase = {0};
		long[] since = {-1};
		int[] unbuilt = {-1};
		String[] bagAt = {null};
		helper.onEachTick(() -> {
			if (phase[0] == 0) {
				helper.setDayTime(2000);
				return;
			}
			if (phase[0] == 2) {
				if (helper.getTick() % 20 == 0) {
					helper.setDayTime(2000);
				}
				return;
			}
			long t = helper.getTick() - since[0];
			helper.setDayTime((int) (10950 + t));
			Villager b = r.builder()[0];
			int now = r.plan().unfinished(r.level()).size();
			if (t == 250) {
				helper.assertTrue(r.site() != null, "setup: the hut was done before dusk");
				unbuilt[0] = now;
				bagAt[0] = bag(b, r.plan()).toString();
				LOGGER.info("qa fix1103 dusk: 200 ticks after dusk {} unbuilt, activity {}", now, activity(b));
			} else if (t > 250) {
				helper.assertTrue(now == unbuilt[0], "the hut changed after dusk, day time " + (10950 + t) + ": " + unbuilt[0] + " unbuilt, then " + now
					+ " (activity " + activity(b) + ", asleep=" + b.isSleeping() + ")");
				helper.assertTrue(bag(b, r.plan()).toString().equals(bagAt[0]), "the builder's bag changed after dusk: " + bagAt[0] + " then " + bag(b, r.plan()));
				if (t >= 1650) {
					phase[0] = 2;
					helper.setDayTime(1000);
					LOGGER.info("qa fix1103 dusk: morning, {}", QaBuilderTerrain1102GameTests.state(helper, r));
				}
			}
		});
		midStructureWithABag(helper, r, () -> {
			phase[0] = 1;
			since[0] = helper.getTick();
			LOGGER.info("qa fix1103 dusk: the clock set to 10950 at {}", QaBuilderTerrain1102GameTests.state(helper, r));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(phase[0] == 2, "the evening isn't over yet");
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
		});
	}

	/**
	 * Mid-STRUCTURE midnight comes (clock held at 18000). 250 ticks later the world is saved and loaded (sites from their
	 * save, the builder a fresh entity). The fresh builder doesn't take up the job at night: from 200 ticks after midnight
	 * to 900 the hut and his bag stay as they were. In the morning he finishes with nothing doubled or lost.
	 */
	//$ gametest_ticks_batch HUGE '16000' '"qaFix1103NightReload"'
	@GameTest(template = HUGE, timeoutTicks = 16000, batch = "qaFix1103NightReload")
	public void aBuilderReloadedAtNightDoesntResumeUntilMorning(GameTestHelper helper) {
		Run r = slowHut(helper);
		int[] phase = {0};
		long[] since = {-1};
		int[] unbuilt = {-1};
		String[] bagAt = {null};
		helper.onEachTick(() -> {
			if (phase[0] == 0) {
				helper.setDayTime(2000);
				return;
			}
			if (phase[0] == 2) {
				if (helper.getTick() % 20 == 0) {
					helper.setDayTime(2000);
				}
				return;
			}
			helper.setDayTime(18000);
			long t = helper.getTick() - since[0];
			if (t == 250) {
				r.builder()[0] = QaBuilderTerrain1102GameTests.reload(helper, r.level(), r.builder()[0]);
				LOGGER.info("qa fix1103 night reload: reloaded at midnight + 250, {}", QaBuilderTerrain1102GameTests.state(helper, r));
			}
			Villager b = r.builder()[0];
			int now = r.plan().unfinished(r.level()).size();
			if (t == 200) {
				helper.assertTrue(r.site() != null, "setup: the hut was done before midnight");
				unbuilt[0] = now;
				bagAt[0] = bag(b, r.plan()).toString();
			} else if (t > 200) {
				helper.assertTrue(now == unbuilt[0], "the hut changed at night, " + t + " ticks after midnight (reloaded at 250): " + unbuilt[0]
					+ " unbuilt, then " + now + " (activity " + activity(b) + ", asleep=" + b.isSleeping() + ")");
				helper.assertTrue(bag(b, r.plan()).toString().equals(bagAt[0]), "the builder's bag changed at night: " + bagAt[0] + " then " + bag(b, r.plan()));
				if (t >= 900) {
					phase[0] = 2;
					helper.setDayTime(1000);
				}
			}
		});
		midStructureWithABag(helper, r, () -> {
			phase[0] = 1;
			since[0] = helper.getTick();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(phase[0] == 2, "the night isn't over yet");
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
		});
	}

	/**
	 * Control (the fix mustn't stop him by day): mid-STRUCTURE the clock is held at midday (6000). In the next 400 ticks
	 * he lays at least three more of the hut's blocks (one every 40 ticks at workplaceBuildDelay 40), and finishes.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaFix1103Midday"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaFix1103Midday")
	public void aBuilderKeepsBuildingAtMidday(GameTestHelper helper) {
		Run r = slowHut(helper);
		long[] since = {-1};
		int[] unbuilt = {-1};
		boolean[] checked = {false};
		helper.onEachTick(() -> {
			if (since[0] < 0) {
				helper.setDayTime(2000);
				return;
			}
			helper.setDayTime(6000);
			if (!checked[0] && helper.getTick() - since[0] >= 400) {
				int now = r.plan().unfinished(r.level()).size();
				checked[0] = true;
				helper.assertTrue(unbuilt[0] - now >= 3 || r.site() == null, "at midday he laid only " + (unbuilt[0] - now) + " block(s) in 400 ticks (activity "
					+ activity(r.builder()[0]) + ", " + QaBuilderTerrain1102GameTests.state(helper, r) + ")");
				LOGGER.info("qa fix1103 midday: {} blocks laid in 400 ticks", unbuilt[0] - now);
			}
		});
		midStructureWithABag(helper, r, () -> {
			since[0] = helper.getTick();
			unbuilt[0] = r.plan().unfinished(r.level()).size();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(checked[0], "midday not watched 400 ticks yet");
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
		});
	}

	// ===================================================================================================== helpers

	/** prepare(), the stale finished buildings here forgotten, levelling at 2, and the clock kept by day (re-set every 20 ticks). */
	private static void setUp(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int ring = level.getGameRules().getInt(ModGameRules.LEVEL_GROUND);
		level.getGameRules().getRule(ModGameRules.LEVEL_GROUND).set(2, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.LEVEL_GROUND).set(ring, level.getServer()));
		helper.onEachTick(() -> {
			if (helper.getTick() % 20 == 0) {
				helper.setDayTime(2000);
			}
		});
	}

	/** The night tests' hut: prepare(), a slow builder (workplaceBuildDelay 40) with a bed by the bench. */
	private static Run slowHut(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(40, level.getServer());
		Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		Beds.home(r.builder()[0], helper.absolutePos(new BlockPos(1, 2, 8)));
		return r;
	}

	private static void fill(GameTestHelper helper, int x0, int x1, int y0, int y1, int z0, int z1, Block block) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int y = y0; y <= y1; y++) {
					helper.setBlock(new BlockPos(x, y, z), block);
				}
			}
		}
	}

	/** Done, as drawn, no falling block left in the box or still falling into it, the hut's materials exact. */
	private static void assertDugOut(GameTestHelper helper, Run r) {
		QaBuilderTerrain1102GameTests.assertFinished(helper, r);
		QaBuilderTerrain1102GameTests.assertNoForeign(helper, r, s -> s.getBlock() instanceof FallingBlock);
		QaBuilderTerrain1102GameTests.assertNothingFalling(helper, r);
		QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
	}

	/** Every block of {@code dug} that the build dug out is loose (chests, bags, on the ground): none lost, none made. */
	private static void assertDugKept(GameTestHelper helper, Run r, Map<Item, Integer> dug) {
		Map<Item, String> off = new HashMap<>();
		for (Map.Entry<Item, Integer> e : dug.entrySet()) {
			int got = QaBuilderTerrain1102GameTests.loose(helper, r, e.getKey());
			if (got != e.getValue()) {
				off.put(e.getKey(), got + " loose of " + e.getValue() + " dug");
			}
		}
		helper.assertTrue(off.isEmpty(), "dug blocks lost or made: " + off);
	}

	/** Every block of the stone pad at y 2 (x0..x1, z0..z1) still stands. */
	private static void assertPad(GameTestHelper helper, int x0, int x1, int z0, int z1) {
		List<String> dug = new ArrayList<>();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				BlockState s = helper.getBlockState(new BlockPos(x, 2, z));
				if (!s.is(Blocks.STONE)) {
					dug.add("(" + x + ",2," + z + ")=" + s.getBlock().getDescriptionId());
				}
			}
		}
		helper.assertTrue(dug.isEmpty(), dug.size() + " block(s) of the neighbor's pad dug out or changed: " + dug);
	}

	/** Both huts' materials: A's loose (chests, every bag, ground) plus B's chests, exactly 2 x EXTRA of each. */
	private static void assertBothAddUp(GameTestHelper helper, Run a, Run b) {
		Map<Item, Integer> off = new HashMap<>();
		for (Item item : a.plan().materials().keySet()) {
			int got = QaBuilderTerrain1102GameTests.loose(helper, a, item) + QaBuilderMidJob1102GameTests.supplies(helper, b, item);
			if (got != 2 * EXTRA) {
				off.put(item, got);
			}
		}
		helper.assertTrue(off.isEmpty(), "materials made or lost by the two huts: left loose " + off + ", wanted " + 2 * EXTRA + " of each");
	}

	private static String activity(Villager b) {
		return b.getBrain().getActiveNonCoreActivity().map(Object::toString).orElse("none");
	}

	private static Map<Item, Integer> bag(Villager v, BuildPlan plan) {
		Map<Item, Integer> out = new HashMap<>();
		for (Item item : plan.materials().keySet()) {
			int n = ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
			if (n > 0) {
				out.put(item, n);
			}
		}
		return out;
	}

	/** Runs {@code then} once, mid-STRUCTURE: four or more blocks built since STRUCTURE began, and materials in the bag. */
	private static void midStructureWithABag(GameTestHelper helper, Run r, Runnable then) {
		int[] atStart = {-1};
		boolean[] done = {false};
		helper.onEachTick(() -> {
			BuildSite s = r.site();
			if (done[0] || s == null || s.stage() != BuildPlan.Stage.STRUCTURE) {
				return;
			}
			int unbuilt = r.plan().unfinished(r.level()).size();
			if (atStart[0] < 0) {
				atStart[0] = unbuilt;
			}
			if (atStart[0] - unbuilt < 4 || bag(r.builder()[0], r.plan()).isEmpty()) {
				return;
			}
			done[0] = true;
			then.run();
		});
	}
}
