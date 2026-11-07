package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.LOGGER;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (2026-11-03, lane b's tester) of the builder's stages, from the spec (CLAUDE.md "How the builder works";
 * docs/wiki/builder.md "Stages"), not from the code. The sentences checked:
 * <ul>
 * <li>"FOUNDATION and LANDSCAPE depend on the terrain, so a site loaded in the middle of those stages makes its list again
 * from the terrain; steps already done are skipped." Cases the plain reload tests (QaBuildStagesGameTests,
 * QaBuildsGameTests, QaBuilderReloadGameTests, QaBuilderConfig1102GameTests) leave out: the terrain changed by a player
 * while the site is unloaded. Mid-FOUNDATION a filled foundation block dug out and an unfilled spot filled by the player
 * (the hole is filled again, the player's block is not paid for twice: every material exact); mid-LANDSCAPE a new mound
 * put on the ring and a new hole dug in it (both leveled after the load).</li>
 * <li>"LANDSCAPE (leveling the ground round the build, dirt) never waits for materials" with a Storehouse in the village
 * and no dirt anywhere (bench chest, Storehouse, bag): DONE within 1500 ticks of LANDSCAPE, never waiting, the status
 * line "Leveling the ground around it" with no "Needs:", and every planned hole left open counted in skipped
 * ("A step that cannot be done yet is put off once, then skipped (counted in skipped)").</li>
 * <li>"A step that cannot be done yet is put off once, then skipped (counted in skipped)", both sides of the boundary: a
 * wall torch with nothing to hang on gets its support from a player after it was put off and before it is gone back
 * to (it is hung, skipped 0, the torch used); with no support it is skipped once (skipped 1, the torch back in the
 * chest, the status line "1 block skipped (unbuildable or unreachable)").</li>
 * <li>"Nobody is built into a block: before a block goes down the builder checks there is room for it" (and a villager or
 * animal in the way is moved to the nearest place to stand): a cow and a villager (no AI, so they never walk off) standing
 * on the hut's floor spot by its wall. At no tick are they inside a block, they live, the build ends, every block not as
 * drawn is counted in skipped, and when the hut is whole skipped is 0 and every material exact. Meanwhile the status reads
 * "Waiting for Cow to move out of the way (at x y z)" at the spot it stands in.</li>
 * <li>builder.md "Whichever table (B290)": "Beyond its bench's chests a Builder counts and takes the village's stock":
 * every material only in the Storehouse, the hut built and exactly the spare left there; the Storehouse and its chests
 * still stand ("never breaks containers").</li>
 * </ul>
 * A Storehouse chest broken mid-fetch and the builder never breaking or copying containers are covered by
 * QaBuilderStockGameTests, QaBuilderSupply1026bGameTests, QaBuilderSuppliesGameTests and
 * QaBuilderKeepsContainers1028GameTests. Helpers: {@link QaBuilderTerrain1102GameTests} (the test hut in huge_area from a
 * chest and barrels, the hut's list plus 2 of each) and {@link QaBuilderStockGameTests} (Jesse's Storehouse).
 */
public class QaBuilderStages1103bGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";

	private static void keepDay(GameTestHelper helper) {
		helper.onEachTick(() -> {
			if (helper.getTick() % 20 == 0) {
				helper.setDayTime(2000);
			}
		});
	}

	// ===================================================================================================== FOUNDATION

	/**
	 * The hut raised two blocks over flat ground, so FOUNDATION fills two layers under its floor. Halfway through FOUNDATION
	 * the site is saved; while it is unloaded a player digs out one foundation block already laid (and keeps it) and lays
	 * one of the same block in a foundation spot not yet filled (from his own pocket). After the load the hut ends as drawn
	 * with no hole in its foundation, and the supplies end at exactly the 2 spare of each: the dug block laid again, the
	 * player's block not paid for.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaStages1103bFoundationDug"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaStages1103bFoundationDug")
	public void aFoundationDugAndFilledWhileUnloadedEndsWholeAndExact(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		Leftovers.finished(helper);
		QaBuilderTerrain1102GameTests.Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 4, 6));
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		ServerLevel level = r.level();
		List<BuildPlan.Step> foundation = r.plan().steps(BuildPlan.Stage.FOUNDATION);
		helper.assertTrue(foundation.size() >= 25, "setup: the raised hut has only " + foundation.size() + " foundation steps");
		String[] done = {null};
		helper.onEachTick(() -> {
			if (helper.getTick() % 20 == 0) {
				helper.setDayTime(2000);
			}
			BuildSite s = r.site();
			if (done[0] != null || s == null || s.stage() != BuildPlan.Stage.FOUNDATION || s.isRetrying() || s.cursor() * 2 < foundation.size()) {
				return;
			}
			BuildPlan.Step dug = null;
			BuildPlan.Step filled = null;
			for (BuildPlan.Step step : foundation) {
				BlockState now = level.getBlockState(step.pos());
				// the top of a laid column (air over it): dug out, its gap is open up to the floor, not hidden under a block
				if (dug == null && !now.isAir() && MaterialRules.matches(now, step.state()) && level.getBlockState(step.pos().above()).isAir()) {
					dug = step;
				}
			}
			if (dug == null) {
				return;
			}
			for (BuildPlan.Step step : foundation) {
				// another column's next block (laid ground or foundation under it)
				if (filled == null && level.getBlockState(step.pos()).isAir() && !level.getBlockState(step.pos().below()).isAir()
					&& (step.pos().getX() != dug.pos().getX() || step.pos().getZ() != dug.pos().getZ()) && step.state().getBlock() == dug.state().getBlock()) {
					filled = step;
				}
			}
			if (filled == null) {
				return;
			}
			// unloaded: the save is taken, then the player changes the terrain, then it is loaded
			level.setBlock(dug.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(filled.pos(), filled.state(), Block.UPDATE_ALL);
			r.builder()[0] = QaBuilderTerrain1102GameTests.reload(helper, level, r.builder()[0]);
			BuildSite back = r.site();
			helper.assertTrue(back != null && back.stage() == BuildPlan.Stage.FOUNDATION, "after the load the site is " + (back == null ? "gone" : back.stage()));
			done[0] = "dug " + helper.relativePos(dug.pos()).toShortString() + ", filled " + helper.relativePos(filled.pos()).toShortString() + " ("
				+ dug.state().getBlock().getDescriptionId() + ") at cursor " + s.cursor() + " of " + foundation.size();
			LOGGER.info("qa stages1103b foundation: {}", done[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(done[0] != null, "never halfway through FOUNDATION: " + QaBuilderTerrain1102GameTests.state(helper, r));
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			List<String> holes = foundation.stream().filter(st -> !MaterialRules.matches(level.getBlockState(st.pos()), st.state()))
				.map(st -> helper.relativePos(st.pos()).toShortString() + "=" + level.getBlockState(st.pos()).getBlock().getDescriptionId()).toList();
			helper.assertTrue(holes.isEmpty(), "after the load (" + done[0] + ") the foundation isn't whole: " + holes);
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
		});
	}

	// ===================================================================================================== LANDSCAPE

	/**
	 * Five dirt mounds on the ring round the hut, and 16 dirt in a barrel. Mid-LANDSCAPE (two mounds dug) the site is saved;
	 * while it is unloaded a player puts a new mound on the ring and digs a hole in the ring's ground. After the load the
	 * list is made again from the terrain: every mound (the new one too) is dug away and the new hole filled; the hut is as
	 * drawn and its materials exact.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaStages1103bLandscapeChanged"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaStages1103bLandscapeChanged")
	public void aMoundAndAHoleMadeWhileUnloadedMidLandscapeAreLeveled(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		Leftovers.finished(helper);
		List<BlockPos> mounds = List.of(new BlockPos(11, 2, 7), new BlockPos(11, 2, 9), new BlockPos(8, 2, 11), new BlockPos(9, 2, 11),
			new BlockPos(7, 2, 11));
		for (BlockPos p : mounds) {
			helper.setBlock(p, Blocks.DIRT);
		}
		BlockPos newMound = new BlockPos(5, 2, 9);
		BlockPos newHole = new BlockPos(5, 1, 7);
		QaBuilderTerrain1102GameTests.Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		((Container) helper.getBlockEntity(r.supplies().get(0))).setItem(26, new ItemStack(Items.DIRT, 16));
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		ServerLevel level = r.level();
		List<BuildPlan.Step> landscape = r.plan().steps(BuildPlan.Stage.LANDSCAPE);
		long planned = landscape.stream().filter(st -> mounds.stream().anyMatch(m -> helper.absolutePos(m).equals(st.pos()))).count();
		helper.assertTrue(planned == mounds.size(), "setup: only " + planned + " of the " + mounds.size() + " mounds are in the LANDSCAPE list");
		helper.assertTrue(landscape.stream().noneMatch(st -> st.pos().equals(helper.absolutePos(newMound)) || st.pos().equals(helper.absolutePos(newHole))),
			"setup: the new mound's or hole's spot is already in the LANDSCAPE list");
		String[] done = {null};
		helper.onEachTick(() -> {
			if (helper.getTick() % 20 == 0) {
				helper.setDayTime(2000);
			}
			BuildSite s = r.site();
			if (done[0] != null || s == null || s.stage() != BuildPlan.Stage.LANDSCAPE || s.isRetrying()) {
				return;
			}
			long dug = mounds.stream().filter(m -> helper.getBlockState(m).isAir()).count();
			if (dug < 2 || dug == mounds.size()) {
				return;
			}
			helper.setBlock(newMound, Blocks.DIRT);
			helper.setBlock(newHole, Blocks.AIR);
			r.builder()[0] = QaBuilderTerrain1102GameTests.reload(helper, level, r.builder()[0]);
			BuildSite back = r.site();
			helper.assertTrue(back != null && back.stage() == BuildPlan.Stage.LANDSCAPE, "after the load the site is " + (back == null ? "gone" : back.stage()));
			done[0] = dug + " mounds dug, cursor " + s.cursor();
			LOGGER.info("qa stages1103b landscape: changed and reloaded with {}", done[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(done[0] != null, "never mid-LANDSCAPE with 2 mounds dug: " + QaBuilderTerrain1102GameTests.state(helper, r));
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			List<String> left = new ArrayList<>();
			for (BlockPos m : mounds) {
				if (!helper.getBlockState(m).isAir()) {
					left.add(m.toShortString());
				}
			}
			helper.assertTrue(left.isEmpty(), "mounds from before the load still stand: " + left);
			helper.assertTrue(helper.getBlockState(newMound).isAir(), "the mound put on the ring while the site was unloaded still stands ("
				+ helper.getBlockState(newMound) + "): LANDSCAPE's list wasn't made again from the terrain");
			BlockState hole = helper.getBlockState(newHole);
			helper.assertTrue(!hole.isAir() && !hole.canBeReplaced(), "the hole dug in the ring while the site was unloaded is still open (" + hole
				+ ") though dirt was in the barrel");
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
		});
	}

	/**
	 * Jesse's Storehouse holds the hut's cobblestone and door, the table's chest its planks and torch, and no dirt is
	 * anywhere (chests, Storehouse, the builder's bag). Three holes in the ring's ground. LANDSCAPE never waits: the site is
	 * DONE within 1500 ticks of reaching LANDSCAPE, never WAITING_FOR_MATERIALS or short of dirt there, its status line
	 * reads "Leveling the ground around it" with no "Needs:", and every planned hole still open at the end is counted in
	 * skipped.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaStages1103bNoDirt"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaStages1103bNoDirt")
	public void landscapeWithNoDirtInTheStorehouseOrBagEndsAndCountsTheHolesSkipped(GameTestHelper helper) {
		List<BlockPos> holes = List.of(new BlockPos(5, 1, 8), new BlockPos(11, 1, 8), new BlockPos(8, 1, 11));
		ServerLevel level = QaBuilderStockGameTests.setup(helper, new BlockPos(26, 2, 26), 25, 1);
		QaBuilderStockGameTests.fill(helper.getBlockEntity(QaBuilderStockGameTests.TABLE_CHEST), new ItemStack(Items.OAK_PLANKS, 55),
			new ItemStack(Items.TORCH));
		for (BlockPos h : holes) {
			helper.setBlock(h, Blocks.AIR);
		}
		QaBuilderStockGameTests.Builder b = QaBuilderStockGameTests.builder(helper, QaBuilderStockGameTests.HUT);
		BuildSite site = b.site();
		List<BlockPos> planned = new ArrayList<>();
		for (BuildPlan.Step st : b.plan().steps(BuildPlan.Stage.LANDSCAPE)) {
			for (BlockPos h : holes) {
				if (helper.absolutePos(h).equals(st.pos())) {
					planned.add(h);
				}
			}
		}
		helper.assertTrue(!planned.isEmpty(), "setup: none of the holes is in the LANDSCAPE list");
		keepDay(helper);
		long[] landscapeAt = {-1};
		int[] skippedBefore = {-1};
		String[] bad = {null};
		String[] status = {null};
		helper.onEachTick(() -> {
			if (bad[0] != null || BuildSiteManager.get(level).get(site.id()) == null || site.stage() != BuildPlan.Stage.LANDSCAPE) {
				return;
			}
			if (landscapeAt[0] < 0) {
				landscapeAt[0] = helper.getTick();
				skippedBefore[0] = site.skipped();
				int dirt = ModAttachments.BUILDER_BAG.getOrCreate(b.villager()).count(Items.DIRT);
				helper.assertTrue(dirt == 0, "setup: the builder carries " + dirt + " dirt into LANDSCAPE");
				status[0] = Builders.statusText(level, site, b.villager()).getString();
				LOGGER.info("qa stages1103b no dirt: LANDSCAPE at {}, skipped {}, status {}", landscapeAt[0], skippedBefore[0], status[0].replace('\n', '|'));
			}
			if (site.status() == BuildSite.Status.WAITING_FOR_MATERIALS || site.missing().containsKey(Items.DIRT)) {
				bad[0] = "at tick " + helper.getTick() + " in LANDSCAPE the site was " + site.status() + ", short of " + site.missing();
			}
			helper.assertTrue(helper.getTick() - landscapeAt[0] <= 1500, "still in LANDSCAPE " + (helper.getTick() - landscapeAt[0])
				+ " ticks after it began, with no dirt anywhere: " + site.status() + " cursor " + site.cursor() + " skipped " + site.skipped());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(bad[0] == null, bad[0]);
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing "
				+ site.missing());
			helper.assertTrue(landscapeAt[0] >= 0, "the site never showed LANDSCAPE");
			helper.assertTrue(status[0].contains("Leveling the ground around it"), "the status in LANDSCAPE doesn't say \"Leveling the ground around it\": "
				+ status[0]);
			helper.assertTrue(!status[0].contains("Needs:"), "the status in LANDSCAPE with no dirt anywhere says it needs something: " + status[0]);
			List<BlockPos> unfinished = b.plan().unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) of the hut not built");
			List<String> open = new ArrayList<>();
			for (BlockPos h : planned) {
				BlockState s = helper.getBlockState(h);
				if (s.isAir() || s.canBeReplaced()) {
					open.add(h.toShortString());
				} else {
					helper.assertTrue(s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK), "the hole at " + h.toShortString() + " was filled with " + s
						+ " though no dirt was anywhere");
				}
			}
			int skippedInLandscape = site.skipped() - skippedBefore[0];
			LOGGER.info("qa stages1103b no dirt: done {} ticks after LANDSCAPE began, {} planned hole(s) open {}, skipped in LANDSCAPE {}",
				helper.getTick() - landscapeAt[0], open.size(), open, skippedInLandscape);
			int steps = b.plan().steps(BuildPlan.Stage.LANDSCAPE).size();
			LOGGER.info("qa stages1103b no dirt: the LANDSCAPE list has {} step(s): {}", steps, b.plan().steps(BuildPlan.Stage.LANDSCAPE).stream()
				.map(st -> st.pos().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString() + "=" + st.state().getBlock().getDescriptionId()).toList());
			helper.assertTrue(skippedInLandscape <= steps, "skipped went up by " + skippedInLandscape + " in LANDSCAPE, a list of only " + steps
				+ " step(s): a step counted more than once (\"put off once, then skipped\")");
			helper.assertTrue(skippedInLandscape >= open.size(), open.size() + " planned hole(s) left open with no dirt anywhere (" + open
				+ "), but skipped went up by only " + skippedInLandscape + " in LANDSCAPE: what wasn't done isn't counted in skipped");
		});
	}

	// ===================================================================================================== the Storehouse

	/**
	 * builder.md "Whichever table (B290)": "Beyond its bench's chests a Builder counts and takes the village's stock". Every
	 * material of the hut, 2 spare of each, is only in Jesse's Storehouse (two chests by it); the table's chest is empty.
	 * The hut is built as drawn, the Storehouse's chests end with exactly the 2 spare of each (nothing used twice, nothing
	 * made), the table's chest and the builder's bag hold none of them, and the Storehouse block and its chests still stand.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaStages1103bAllInStore"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaStages1103bAllInStore")
	public void aHutWhoseMaterialsAreAllInTheStorehouseIsBuiltFromItExactly(GameTestHelper helper) {
		BlockPos storehouse = new BlockPos(26, 2, 26);
		List<BlockPos> chests = List.of(new BlockPos(23, 2, 26), new BlockPos(26, 2, 23));
		ServerLevel level = QaBuilderStockGameTests.setup(helper, storehouse, 27, 3);
		Container first = helper.getBlockEntity(chests.get(0));
		first.setItem(1, new ItemStack(Items.OAK_PLANKS, 57));
		Container second = helper.getBlockEntity(chests.get(1));
		second.setItem(1, new ItemStack(Items.TORCH, 3));
		QaBuilderStockGameTests.Builder b = QaBuilderStockGameTests.builder(helper, QaBuilderStockGameTests.HUT);
		java.util.Map<Item, Integer> list = b.plan().materials();
		helper.assertTrue(list.equals(java.util.Map.of(Items.COBBLESTONE, 25, Items.OAK_PLANKS, 55, Items.OAK_DOOR, 1, Items.TORCH, 1)),
			"setup: the hut's list is " + list);
		keepDay(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building: " + b.site().stage() + " " + b.site().status()
				+ " missing " + b.site().missing());
			List<BlockPos> wrong = b.plan().unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the hut not built");
			helper.assertBlockPresent(ModBlocks.STOREHOUSE, storehouse);
			for (BlockPos c : chests) {
				helper.assertBlockPresent(Blocks.CHEST, c);
			}
			java.util.Map<Item, Integer> off = new java.util.LinkedHashMap<>();
			Container table = helper.getBlockEntity(QaBuilderStockGameTests.TABLE_CHEST);
			for (Item item : list.keySet()) {
				int inStore = first.countItem(item) + second.countItem(item);
				int elsewhere = table.countItem(item) + ModAttachments.BUILDER_BAG.getOrCreate(b.villager()).count(item);
				if (inStore != 2 || elsewhere != 0) {
					off.put(item, inStore * 1000 + elsewhere);
				}
			}
			helper.assertTrue(off.isEmpty(), "after the hut, materials not left as 2 spare in the Storehouse and none elsewhere (Storehouse*1000 + table "
				+ "and bag): " + off);
		});
	}

	// ===================================================================================================== put off, then skipped

	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos ROW = new BlockPos(6, 2, 9);
	/** Where the first torch hangs from: just west of the scan, outside the build's box. */
	private static final BlockPos SUPPORT = ROW.west();

	private record Scan(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan) {
		boolean done() {
			return BuildSiteManager.get(level).get(site.id()) == null;
		}
	}

	/**
	 * A scanned row: a wall torch at the west end hung on a stone just outside the scan, then four cobblestone with a
	 * torch standing on each. The stone is taken away before the build, so the first torch has nothing to hang on. The
	 * chest holds exactly 4 cobblestone and 5 torches. Leveling is off (the stone's spot is on the ring).
	 */
	private static Scan torchRow(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		int ground = level.getGameRules().getInt(ModGameRules.LEVEL_GROUND);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.LEVEL_GROUND).set(0, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.LEVEL_GROUND).set(ground, level.getServer());
		});
		helper.setBlock(SUPPORT, Blocks.STONE);
		helper.setBlock(ROW, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST));
		for (int x = 1; x <= 4; x++) {
			helper.setBlock(ROW.east(x), Blocks.COBBLESTONE);
			helper.setBlock(ROW.east(x).above(), Blocks.TORCH);
		}
		helper.assertBlockPresent(Blocks.WALL_TORCH, ROW);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qastages1103b_torches_" + Long.toHexString(level.getGameTime()));
		Vec3i size = new Vec3i(5, 2, 1);
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), size, false, Blocks.STRUCTURE_VOID);
		for (int y = 1; y >= 0; y--) {
			for (int x = 0; x < 5; x++) {
				level.setBlock(helper.absolutePos(ROW.offset(x, y, 0)), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		level.setBlock(helper.absolutePos(SUPPORT), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(BENCH));
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 4));
		chest.setItem(1, new ItemStack(Items.TORCH, 5));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ROW), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, villager, null, id, placement);
		helper.assertTrue(site != null, "the Builder took no build");
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no plan for " + id);
		long torches = plan.steps(BuildPlan.Stage.DECORATION).stream().filter(st -> st.state().is(Blocks.WALL_TORCH) || st.state().is(Blocks.TORCH)).count();
		helper.assertTrue(torches == 5, "setup: " + torches + " torch steps in DECORATION, not 5: " + plan.steps(BuildPlan.Stage.DECORATION));
		keepDay(helper);
		return new Scan(level, villager, site, plan);
	}

	private static int held(GameTestHelper helper, Scan s, Item item) {
		return ((Container) helper.getBlockEntity(CHEST)).countItem(item) + ModAttachments.BUILDER_BAG.getOrCreate(s.villager()).count(item);
	}

	private static int torchIndex(GameTestHelper helper, Scan s) {
		List<BuildPlan.Step> deco = s.plan().steps(BuildPlan.Stage.DECORATION);
		for (int i = 0; i < deco.size(); i++) {
			if (deco.get(i).pos().equals(helper.absolutePos(ROW))) {
				return i;
			}
		}
		throw new net.minecraft.gametest.framework.GameTestAssertException("setup: the hanging torch isn't a DECORATION step");
	}

	/**
	 * The boundary from the far side: the hanging torch is put off (DECORATION's cursor past it, the spot empty, not yet
	 * going back), and only then a player puts a stone where it hangs from. Going back over it, the builder hangs it: the
	 * torch stands, skipped is 0, every torch and cobblestone used.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaStages1103bSupportLate"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaStages1103bSupportLate")
	public void aTorchWhoseSupportComesAfterItWasPutOffIsHungOnTheRetry(GameTestHelper helper) {
		Scan s = torchRow(helper);
		int index = torchIndex(helper, s);
		long[] supportAt = {-1};
		String[] when = {null};
		helper.onEachTick(() -> {
			if (supportAt[0] >= 0 || s.done()) {
				return;
			}
			BuildSite site = s.site();
			if (site.stage() == BuildPlan.Stage.DECORATION && (site.cursor() > index || site.isRetrying()) && helper.getBlockState(ROW).isAir()) {
				helper.assertTrue(site.skipped() == 0, "setup: the torch was skipped before its support could come (skipped " + site.skipped() + ")");
				when[0] = (site.isRetrying() ? "retrying" : "put off") + ", cursor " + site.cursor() + " (torch at " + index + ")";
				helper.setBlock(SUPPORT, Blocks.STONE);
				supportAt[0] = helper.getTick();
				LOGGER.info("qa stages1103b support late: the stone put in at {} ({})", supportAt[0], when[0]);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(supportAt[0] >= 0, "setup: never saw the torch put off");
			helper.assertTrue(s.done(), "still building: " + s.site().stage() + " " + s.site().status() + " skipped " + s.site().skipped());
			helper.assertTrue(helper.getBlockState(ROW).is(Blocks.WALL_TORCH), "the torch's support came after it was put off (" + when[0]
				+ ") and before it was gone back to, but the torch isn't hung: " + helper.getBlockState(ROW));
			helper.assertTrue(s.site().skipped() == 0, "skipped is " + s.site().skipped() + " though every block got built");
			helper.assertTrue(held(helper, s, Items.TORCH) == 0 && held(helper, s, Items.COBBLESTONE) == 0, "materials left: "
				+ held(helper, s, Items.TORCH) + " torch(es), " + held(helper, s, Items.COBBLESTONE) + " cobblestone");
		});
	}

	/**
	 * The near side: no support ever comes. The torch is put off once, then skipped: skipped 1 and the status says "1 block
	 * skipped (unbuildable or unreachable)" (singular), the four standing torches are up, and the fifth torch is back in
	 * the chest or bag (not used, not lost).
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaStages1103bNoSupport"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaStages1103bNoSupport")
	public void aTorchThatNeverGetsItsSupportIsSkippedOnceAndSaysSo(GameTestHelper helper) {
		Scan s = torchRow(helper);
		String[] line = {null};
		helper.onEachTick(() -> {
			if (line[0] == null && !s.done() && s.site().skipped() > 0) {
				line[0] = Builders.statusText(s.level(), s.site(), s.villager()).getString();
				LOGGER.info("qa stages1103b no support: status {}", line[0].replace('\n', '|'));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(s.done(), "still building: " + s.site().stage() + " " + s.site().status() + " skipped " + s.site().skipped());
			helper.assertTrue(s.site().skipped() == 1, "skipped is " + s.site().skipped() + ", not the 1 torch with nothing to hang on");
			helper.assertTrue(helper.getBlockState(ROW).isAir(), "where the torch had nothing to hang on stands " + helper.getBlockState(ROW));
			for (int x = 1; x <= 4; x++) {
				helper.assertBlockPresent(Blocks.TORCH, ROW.east(x).above());
			}
			helper.assertTrue(held(helper, s, Items.TORCH) == 1, held(helper, s, Items.TORCH) + " torch(es) left, not the 1 skipped");
			helper.assertTrue(line[0] != null, "the site was never seen with a skipped block while it stood");
			helper.assertTrue(line[0].contains("1 block skipped (unbuildable or unreachable)"), "the status with one block skipped doesn't read \"1 block skipped "
				+ "(unbuildable or unreachable)\": " + line[0]);
		});
	}

	// ===================================================================================================== someone in the way

	/** The hut's floor spot at its west wall, (6, 2, 8): someone there stands in that block and the wall block over it. */
	private static final BlockPos IN_THE_WAY = new BlockPos(6, 2, 8);

	/**
	 * Someone of {@code type} (no AI, so it never walks off by itself) stands on the hut's floor spot by its west wall from
	 * the start. "Nobody is built into a block": at no tick is it inside a block (suffocating), it is alive at the end, and
	 * the build ends (never stuck on it). Every block of the hut not as drawn is counted in skipped; when all are as drawn
	 * (the builder moved it out of the way), skipped is 0 and every material is exact.
	 */
	private static void inTheWay(GameTestHelper helper, EntityType<? extends net.minecraft.world.entity.Mob> type) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		Leftovers.finished(helper);
		QaBuilderTerrain1102GameTests.Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		net.minecraft.world.entity.Mob who = helper.spawn(type, IN_THE_WAY);
		who.setNoAi(true);
		who.setPersistenceRequired();
		helper.assertTrue(r.plan().steps(BuildPlan.Stage.STRUCTURE).stream().anyMatch(st -> st.pos().equals(helper.absolutePos(IN_THE_WAY))),
			"setup: the spot isn't a STRUCTURE step");
		BuildSite site = r.site();
		String[] walled = {null};
		String[] detail = {null};
		helper.onEachTick(() -> {
			if (helper.getTick() % 20 == 0) {
				helper.setDayTime(2000);
			}
			if (detail[0] == null && r.site() != null && r.site().detail() != null && r.site().detail().getString().startsWith("Waiting for")) {
				detail[0] = r.site().detail().getString();
				LOGGER.info("qa stages1103b in the way ({}): the status says \"{}\"", type.getDescription().getString(), detail[0]);
			}
			if (walled[0] == null && who.isAlive() && who.isInWall()) {
				walled[0] = "at tick " + helper.getTick() + " inside " + r.level().getBlockState(BlockPos.containing(who.getEyePosition())) + " ("
					+ QaBuilderTerrain1102GameTests.state(helper, r) + ")";
			}
			helper.assertTrue(who.isAlive(), "the " + type.getDescription().getString() + " in the way died at tick " + helper.getTick() + " (last hurt by "
				+ who.getLastDamageSource() + "): " + QaBuilderTerrain1102GameTests.state(helper, r));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(walled[0] == null, "a block was built into the " + type.getDescription().getString() + " standing in the way " + walled[0]);
			helper.assertTrue(r.died()[0] == null, "the builder died on the site: " + r.died()[0]);
			helper.assertTrue(r.site() == null, "still building with someone in the way: " + QaBuilderTerrain1102GameTests.state(helper, r));
			int wrong = r.plan().unfinished(r.level()).size();
			LOGGER.info("qa stages1103b in the way ({}): done, it stands at {} (absolute {}), {} block(s) not as drawn, skipped {}", type.getDescription().getString(),
				who.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString(), who.blockPosition().toShortString(), wrong, site.skipped());
			helper.assertTrue(site.skipped() >= wrong, wrong + " block(s) of the hut not as drawn but skipped counts " + site.skipped());
			// the sentence a player reads meanwhile: "Waiting for %s to move out of the way (at %s %s %s)", at one of the two spots
			helper.assertTrue(detail[0] != null, "the status never said who was in the way");
			BlockPos floor = helper.absolutePos(IN_THE_WAY);
			String name = type.getDescription().getString();
			String atFloor = "Waiting for " + name + " to move out of the way (at " + floor.getX() + " " + floor.getY() + " " + floor.getZ() + ")";
			String atWall = "Waiting for " + name + " to move out of the way (at " + floor.getX() + " " + (floor.getY() + 1) + " " + floor.getZ() + ")";
			helper.assertTrue(detail[0].equals(atFloor) || detail[0].equals(atWall), "the status said \"" + detail[0] + "\", not \"" + atFloor + "\" (or the wall "
				+ "block over it)");
			if (wrong == 0) {
				helper.assertTrue(site.skipped() == 0, "every block is as drawn but skipped counts " + site.skipped());
				QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
			}
		});
	}

	/** A cow (no AI) standing where the floor and the wall go. */
	//$ gametest_ticks_batch HUGE '9000' '"qaStages1103bCowInTheWay"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaStages1103bCowInTheWay")
	public void aCowStandingWhereTheWallGoesIsNeverBuiltInAndTheHutEnds(GameTestHelper helper) {
		inTheWay(helper, EntityType.COW);
	}

	/** A villager (no AI, no job) standing where the floor and the wall go. */
	//$ gametest_ticks_batch HUGE '9000' '"qaStages1103bVillagerInTheWay"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaStages1103bVillagerInTheWay")
	public void aVillagerStandingWhereTheWallGoesIsNeverBuiltInAndTheHutEnds(GameTestHelper helper) {
		inTheWay(helper, EntityType.VILLAGER);
	}
}
