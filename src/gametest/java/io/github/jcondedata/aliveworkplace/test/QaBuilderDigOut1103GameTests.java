package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.LOGGER;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;

/**
 * QA (2026-11-03) of B869 from its spec (ROADMAP B869; docs/wiki/builder.md "Stages"): once LANDSCAPE has run out the
 * builder goes back over CLEAR in a dig-out that takes only sand, gravel, concrete powder and the like standing where the
 * drawing has air (never a block of the build or an anvil), into his chests, then straight back to the end of the build;
 * dig-outs share CLEAR's 16 passes, so the build always ends; sand a player puts in a room mid-build is dug out with it
 * (never lost). Next to {@link QaBuilderRestartClear1103bBugTests} (sand from props, a reload in the dig-out, sand
 * refilled for ever) and QaBuilderFixes1103GameTests (sand put in the finished hut stays).
 * <p>
 * The test hut (QaBuilderTerrain1102GameTests.start) at (6, 2, 6): a cobblestone floor at y 2, plank walls x/z 6 and 10,
 * a plank roof at y 5, the room's air x/z 7-9, y 3-4 (a torch at (8, 4, 9)), the door at (8, 3-4, 6).
 */
public class QaBuilderDigOut1103GameTests {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos ORIGIN = new BlockPos(6, 2, 6);

	// ------------------------------------------------------------------------------------- what the dig-out takes

	/**
	 * Gravel and red concrete powder (not sand) put on the room's floor mid-STRUCTURE are dug out before the hut is done:
	 * none left in the box, the hut as drawn, every material kept, and each dug block loose again (gravel as gravel or
	 * its flint, the powder as powder), in the builder's chests.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaDigOut1103GravelPowder"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaDigOut1103GravelPowder")
	public void gravelAndConcretePowderInTheRoomAreDugOutIntoHisChests(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.Run r = setUp(helper);
		BlockPos gravel = new BlockPos(9, 3, 7); // (7, 3, 7) is where he stands all STRUCTURE long
		BlockPos powder = new BlockPos(9, 3, 9);
		long[] put = {-1};
		boolean[] digOut = {false};
		boolean[] each = {false, false};
		midStructure(helper, r, () -> {
			if (!each[0] && free(helper, gravel)) {
				helper.setBlock(gravel, Blocks.GRAVEL);
				each[0] = true;
			}
			if (!each[1] && free(helper, powder)) {
				helper.setBlock(powder, Blocks.RED_CONCRETE_POWDER);
				each[1] = true;
			}
			if (each[0] && each[1]) {
				put[0] = helper.getTick();
			}
			return each[0] && each[1];
		});
		watchDigOut(helper, r, digOut);
		helper.succeedWhen(() -> {
			helper.assertTrue(put[0] >= 0, "setup: gravel put " + each[0] + ", powder put " + each[1] + " in the room mid-STRUCTURE");
			helper.assertTrue(r.site() == null, "still building: " + QaBuilderTerrain1102GameTests.state(helper, r));
			helper.assertTrue(digOut[0], "the hut was finished without a dig-out of the room's gravel and powder");
			QaBuilderTerrain1102GameTests.assertNoForeign(helper, r, st -> st.getBlock() instanceof FallingBlock);
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
			int gravelLoose = QaBuilderTerrain1102GameTests.loose(helper, r, Items.GRAVEL) + QaBuilderTerrain1102GameTests.loose(helper, r, Items.FLINT);
			int powderLoose = QaBuilderTerrain1102GameTests.loose(helper, r, Items.RED_CONCRETE_POWDER);
			helper.assertTrue(gravelLoose == 1, "1 gravel dug, " + gravelLoose + " gravel or flint loose (chests, bags, ground)");
			helper.assertTrue(powderLoose == 1, "1 red concrete powder dug, " + powderLoose + " loose (chests, bags, ground)");
			int inChests = inSupplies(helper, r, Items.GRAVEL) + inSupplies(helper, r, Items.FLINT) + inSupplies(helper, r, Items.RED_CONCRETE_POWDER);
			helper.assertTrue(inChests == 2, "the dug gravel and powder aren't in his chests: " + inChests + " of 2 there");
			LOGGER.info("qa dig-out 1103 gravel/powder: put at tick {}, finished, both back in his chests", put[0]);
		});
	}

	/**
	 * Sand a player puts in the room mid-STRUCTURE, two high (the top one standing on the bottom one), is dug out with
	 * the rest and never lost: both blocks are back as sand in his chests, the room empty, the hut as drawn.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaDigOut1103PlayerSand"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaDigOut1103PlayerSand")
	public void sandAPlayerPutsInTheRoomMidBuildIsDugOutAndNeverLost(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.Run r = setUp(helper);
		BlockPos low = new BlockPos(8, 3, 8);
		long[] put = {-1};
		boolean[] digOut = {false};
		midStructure(helper, r, () -> {
			if (!free(helper, low) || !free(helper, low.above())) {
				return false;
			}
			helper.setBlock(low, Blocks.SAND);
			helper.setBlock(low.above(), Blocks.SAND);
			put[0] = helper.getTick();
			return true;
		});
		watchDigOut(helper, r, digOut);
		helper.succeedWhen(() -> {
			helper.assertTrue(put[0] >= 0, "setup: no sand put in the room mid-STRUCTURE");
			helper.assertTrue(r.site() == null, "still building: " + QaBuilderTerrain1102GameTests.state(helper, r));
			helper.assertTrue(digOut[0], "the hut was finished without a dig-out of the player's sand");
			QaBuilderTerrain1102GameTests.assertNoForeign(helper, r, st -> st.getBlock() instanceof FallingBlock);
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
			int loose = QaBuilderTerrain1102GameTests.loose(helper, r, Items.SAND);
			helper.assertTrue(loose == 2, "2 sand put in the room, " + loose + " loose after (chests, bags, ground)");
			int inChests = inSupplies(helper, r, Items.SAND);
			helper.assertTrue(inChests == 2, "the player's sand isn't back in his chests: " + inChests + " of 2 there");
		});
	}

	// ------------------------------------------------------------------------------------- what the dig-out leaves

	/**
	 * An anvil put on the room's floor mid-STRUCTURE (a falling block, but a player's) is never dug: it stands where it
	 * was put, none turns up in the chests, the hut's blocks are as drawn and the build still ends within CLEAR's 16
	 * passes (no endless going back for it).
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaDigOut1103Anvil"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaDigOut1103Anvil")
	public void anAnvilInTheRoomIsNeverDugAndTheHutStillFinishes(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.Run r = setUp(helper);
		BlockPos anvil = new BlockPos(8, 3, 8);
		long[] put = {-1};
		int[] passes = {0};
		BuildSite[] last = {null};
		midStructure(helper, r, () -> {
			if (!free(helper, anvil)) {
				return false;
			}
			helper.setBlock(anvil, Blocks.ANVIL);
			put[0] = helper.getTick();
			return true;
		});
		helper.onEachTick(() -> {
			BuildSite s = r.site();
			if (s != null) {
				last[0] = s;
				passes[0] = Math.max(passes[0], s.clearPasses());
			}
			if (put[0] >= 0) {
				helper.assertTrue(helper.getBlockState(anvil).is(Blocks.ANVIL), "the anvil in the room was taken at tick " + helper.getTick()
					+ " (stage " + (s == null ? "done" : s.stage() + ", dig-out " + s.digOut()) + "): " + helper.getBlockState(anvil));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(put[0] >= 0, "setup: no anvil put in the room mid-STRUCTURE");
			helper.assertTrue(r.site() == null, "still building after " + passes[0] + " CLEAR passes: " + QaBuilderTerrain1102GameTests.state(helper, r));
			helper.assertTrue(r.died()[0] == null, "the builder died on the site: " + r.died()[0]);
			helper.assertTrue(passes[0] <= 16, "more than CLEAR's 16 passes: " + passes[0]);
			helper.assertTrue(helper.getBlockState(anvil).is(Blocks.ANVIL), "the anvil isn't where it was put: " + helper.getBlockState(anvil));
			int loose = QaBuilderTerrain1102GameTests.loose(helper, r, Items.ANVIL);
			helper.assertTrue(loose == 0, loose + " anvil(s) turned up in the chests, bags or on the ground");
			QaBuilderRestartClear1103bGameTests.assertBlocksAsDrawn(helper, r);
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
			LOGGER.info("qa dig-out 1103 anvil: finished after {} CLEAR passes, skipped {}, the anvil kept", passes[0], last[0] == null ? -1 : last[0].skipped());
		});
	}

	/**
	 * The dig-out only takes what stands inside the build's box where the drawing has air. With ground levelling off
	 * (nothing for LANDSCAPE to do, so the dig-out must still come), sand put mid-STRUCTURE in the room is dug out, while
	 * sand put on the lawn a block past the east wall, and sand put on the finished roof (above the box), stay.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaDigOut1103Outside"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaDigOut1103Outside")
	public void sandOutsideTheBoxStaysWhileTheRoomsIsDugOut(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int ring = level.getGameRules().getInt(ModGameRules.LEVEL_GROUND);
		level.getGameRules().getRule(ModGameRules.LEVEL_GROUND).set(0, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.LEVEL_GROUND).set(ring, level.getServer()));
		QaBuilderTerrain1102GameTests.Run r = QaBuilderTerrain1102GameTests.start(helper, 2, ORIGIN);
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		dayTime(helper);
		helper.assertTrue(r.plan().steps(BuildPlan.Stage.LANDSCAPE).isEmpty(), "setup: LANDSCAPE has steps with levelling off: "
			+ r.plan().steps(BuildPlan.Stage.LANDSCAPE).size());
		BlockPos room = new BlockPos(8, 3, 8);
		BlockPos lawn = new BlockPos(11, 2, 8);
		BlockPos roofTop = new BlockPos(8, 6, 8);
		helper.assertFalse(r.plan().bounds().isInside(helper.absolutePos(lawn)) || r.plan().bounds().isInside(helper.absolutePos(roofTop)),
			"setup: the outside spots are in the build's box " + r.plan().bounds());
		long[] put = {-1, -1}; // room and lawn, roof
		boolean[] digOut = {false};
		midStructure(helper, r, () -> {
			if (!free(helper, room) || !free(helper, lawn)) {
				return false;
			}
			helper.setBlock(room, Blocks.SAND);
			helper.setBlock(lawn, Blocks.SAND);
			put[0] = helper.getTick();
			return true;
		});
		helper.onEachTick(() -> {
			if (put[1] < 0 && put[0] >= 0 && helper.getBlockState(roofTop.below()).is(Blocks.OAK_PLANKS) && free(helper, roofTop)) {
				helper.setBlock(roofTop, Blocks.SAND);
				put[1] = helper.getTick();
			}
		});
		watchDigOut(helper, r, digOut);
		helper.succeedWhen(() -> {
			helper.assertTrue(put[0] >= 0 && put[1] >= 0, "setup: sand put in the room/lawn at " + put[0] + ", on the roof at " + put[1]);
			helper.assertTrue(r.site() == null, "still building: " + QaBuilderTerrain1102GameTests.state(helper, r));
			helper.assertTrue(digOut[0], "the hut was finished without a dig-out (LANDSCAPE empty)");
			helper.assertTrue(helper.getBlockState(lawn).is(Blocks.SAND), "sand on the lawn past the box was taken: " + helper.getBlockState(lawn));
			helper.assertTrue(helper.getBlockState(roofTop).is(Blocks.SAND), "sand on the roof (above the box) was taken: " + helper.getBlockState(roofTop));
			QaBuilderTerrain1102GameTests.assertNoForeign(helper, r, st -> st.getBlock() instanceof FallingBlock);
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
			int loose = QaBuilderTerrain1102GameTests.loose(helper, r, Items.SAND);
			helper.assertTrue(loose == 1, "1 sand dug from the room, " + loose + " loose after");
		});
	}

	// ------------------------------------------------------------------------------- with a wall broken (B508)

	/**
	 * Sand put in the room and a wall block broken, both mid-STRUCTURE: the wall is put back and the sand dug out, and
	 * the build ends with the hut as drawn, one plank used over the list for the broken one and nothing else made or lost.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaDigOut1103WallThenSand"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaDigOut1103WallThenSand")
	public void aBrokenWallAndSandInTheRoomMidStructureAreBothPutRight(GameTestHelper helper) {
		wallAndSand(helper, false);
	}

	/**
	 * Sand put in the room mid-STRUCTURE; then, while the dig-out is going on, a wall block is broken, and once the build
	 * is back in STRUCTURE to put it back, sand is put in the room again. Both rounds of sand are dug out, the wall is put
	 * back, the build ends within CLEAR's 16 passes, and the two sand blocks are loose again.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaDigOut1103SandThenWall"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaDigOut1103SandThenWall")
	public void aWallBrokenInTheDigOutAndSandAgainInTheRepairAreBothPutRight(GameTestHelper helper) {
		wallAndSand(helper, true);
	}

	private static void wallAndSand(GameTestHelper helper, boolean wallInDigOut) {
		QaBuilderTerrain1102GameTests.Run r = setUp(helper);
		BlockPos room = new BlockPos(8, 3, 8);
		BlockPos wall = new BlockPos(6, 3, 8); // the west wall's lowest row, over the floor
		BlockPos again = new BlockPos(9, 3, 7);
		long[] sandAt = {-1, -1}; // first, second
		long[] wallAt = {-1};
		boolean[] digOut = {false};
		int[] passes = {0};
		midStructure(helper, r, () -> {
			if (!free(helper, room)) {
				return false;
			}
			helper.setBlock(room, Blocks.SAND);
			sandAt[0] = helper.getTick();
			return true;
		});
		helper.onEachTick(() -> {
			BuildSite s = r.site();
			if (s == null || sandAt[0] < 0) {
				return;
			}
			passes[0] = Math.max(passes[0], s.clearPasses());
			boolean time = wallInDigOut ? s.digOut() : s.stage() == BuildPlan.Stage.STRUCTURE;
			if (wallAt[0] < 0 && time && helper.getBlockState(wall).is(Blocks.OAK_PLANKS) && free(helper, wall)) {
				helper.setBlock(wall, Blocks.AIR); // broken, no drop: one plank gone
				wallAt[0] = helper.getTick();
			}
			if (wallInDigOut && wallAt[0] >= 0 && sandAt[1] < 0 && s.stage() == BuildPlan.Stage.STRUCTURE && free(helper, again)) {
				helper.setBlock(again, Blocks.SAND);
				sandAt[1] = helper.getTick();
			}
		});
		watchDigOut(helper, r, digOut);
		helper.succeedWhen(() -> {
			helper.assertTrue(sandAt[0] >= 0 && wallAt[0] >= 0 && (!wallInDigOut || sandAt[1] >= 0),
				"setup: sand put at " + sandAt[0] + " and " + sandAt[1] + ", wall broken at " + wallAt[0]);
			helper.assertTrue(r.site() == null, "still building after " + passes[0] + " CLEAR passes: " + QaBuilderTerrain1102GameTests.state(helper, r));
			helper.assertTrue(digOut[0], "the hut was finished without a dig-out");
			helper.assertTrue(passes[0] <= 16, "more than CLEAR's 16 passes: " + passes[0]);
			helper.assertTrue(helper.getBlockState(wall).is(Blocks.OAK_PLANKS), "the broken wall block wasn't put back: " + helper.getBlockState(wall));
			QaBuilderTerrain1102GameTests.assertNoForeign(helper, r, st -> st.getBlock() instanceof FallingBlock);
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r, Map.of(Items.OAK_PLANKS, -1));
			int want = wallInDigOut ? 2 : 1;
			int loose = QaBuilderTerrain1102GameTests.loose(helper, r, Items.SAND);
			helper.assertTrue(loose == want, want + " sand put in the room, " + loose + " loose after");
			LOGGER.info("qa dig-out 1103 wall+sand (wall in dig-out {}): finished after {} CLEAR passes", wallInDigOut, passes[0]);
		});
	}

	// ----------------------------------------------------------------------------------------- the dig-out's flag

	/**
	 * Sand put in the room mid-STRUCTURE, watched every tick: the site is in a dig-out only in CLEAR; a dig-out ends
	 * straight in LANDSCAPE (never FOUNDATION, STRUCTURE or DECORATION); no block of the hut's walls, roof, floor or
	 * decoration is taken while it runs; and the finished site never has it on.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaDigOut1103Flag"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaDigOut1103Flag")
	public void theDigOutIsOnlyInClearEndsInLandscapeAndTakesNothingBuilt(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.Run r = setUp(helper);
		BlockPos room = new BlockPos(8, 3, 8);
		long[] put = {-1};
		List<String> wrong = new ArrayList<>();
		boolean[] was = {false};
		int[] digOuts = {0};
		int[] built = {-1}; // structure and decoration blocks as drawn when a dig-out began
		BuildSite[] last = {null};
		midStructure(helper, r, () -> {
			if (!free(helper, room)) {
				return false;
			}
			helper.setBlock(room, Blocks.SAND);
			put[0] = helper.getTick();
			return true;
		});
		helper.onEachTick(() -> {
			BuildSite s = r.site();
			if (s != null) {
				last[0] = s;
			}
			boolean now = s != null && s.digOut();
			if (now && s.stage() != BuildPlan.Stage.CLEAR && wrong.size() < 5) {
				wrong.add("a dig-out in " + s.stage() + " at tick " + helper.getTick());
			}
			if (now && !was[0]) {
				digOuts[0]++;
				built[0] = asDrawn(r);
			}
			if (now && built[0] >= 0) {
				int n = asDrawn(r);
				if (n < built[0] && wrong.size() < 5) {
					wrong.add("the dig-out took a block of the hut at tick " + helper.getTick() + ": " + n + " as drawn, " + built[0] + " when it began");
				}
			}
			if (!now && was[0] && s != null && s.stage() != BuildPlan.Stage.LANDSCAPE && wrong.size() < 5) {
				wrong.add("a dig-out ended in " + s.stage() + " at tick " + helper.getTick() + ", not LANDSCAPE");
			}
			was[0] = now;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(put[0] >= 0, "setup: no sand put in the room mid-STRUCTURE");
			helper.assertTrue(wrong.isEmpty(), "the dig-out went wrong: " + wrong);
			helper.assertTrue(r.site() == null, "still building: " + QaBuilderTerrain1102GameTests.state(helper, r));
			helper.assertTrue(digOuts[0] >= 1, "no dig-out seen for the room's sand");
			helper.assertFalse(last[0].digOut(), "the finished site still has dig_out on (stage " + last[0].stage() + ")");
			helper.assertTrue(last[0].stage() == BuildPlan.Stage.DONE, "the finished site's stage is " + last[0].stage() + ", not DONE");
			QaBuilderTerrain1102GameTests.assertNoForeign(helper, r, st -> st.getBlock() instanceof FallingBlock);
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			LOGGER.info("qa dig-out 1103 flag: {} dig-out(s), each in CLEAR only, ended in LANDSCAPE", digOuts[0]);
		});
	}

	// ---------------------------------------------------------------------------------------------------- helpers

	private static QaBuilderTerrain1102GameTests.Run setUp(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		Leftovers.finished(helper);
		QaBuilderTerrain1102GameTests.Run r = QaBuilderTerrain1102GameTests.start(helper, 2, ORIGIN);
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		dayTime(helper);
		return r;
	}

	private static void dayTime(GameTestHelper helper) {
		helper.onEachTick(() -> {
			if (helper.getTick() % 20 == 0) {
				helper.setDayTime(2000);
			}
		});
	}

	/** Runs {@code put} once mid-STRUCTURE (the floor down and the walls begun: 30 blocks laid), until it says it did. */
	private static void midStructure(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r, java.util.function.BooleanSupplier put) {
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
			if (atStart[0] - unbuilt >= 30) {
				done[0] = put.getAsBoolean();
			}
		});
	}

	/** Records whether the site was ever in a dig-out. */
	private static void watchDigOut(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r, boolean[] seen) {
		helper.onEachTick(() -> {
			BuildSite s = r.site();
			if (s != null && s.digOut()) {
				seen[0] = true;
			}
		});
	}

	/** Whether no villager stands in {@code rel} (so a block put there doesn't bury one). */
	private static boolean free(GameTestHelper helper, BlockPos rel) {
		return helper.getLevel().getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(helper.absolutePos(rel))).isEmpty();
	}

	/** {@code item} in the chest and barrels by the bench. */
	private static int inSupplies(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r, Item item) {
		int n = 0;
		for (BlockPos p : r.supplies()) {
			n += ((Container) helper.getBlockEntity(p)).countItem(item);
		}
		return n;
	}

	/** Structure and decoration blocks standing as drawn. */
	private static int asDrawn(QaBuilderTerrain1102GameTests.Run r) {
		int n = 0;
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step step : r.plan().steps(stage)) {
				if (MaterialRules.matches(r.level().getBlockState(step.pos()), step.state())) {
					n++;
				}
			}
		}
		return n;
	}
}
