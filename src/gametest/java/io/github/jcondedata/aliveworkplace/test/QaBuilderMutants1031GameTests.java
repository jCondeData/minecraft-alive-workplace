package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (2026-10-31), a mutation sweep of the builder's fixes since 2026-10-26; each test kills a planted bug the suite let
 * through. B670 (01db9b4ab): when a build ends, ITS helpers stop and carry their bags back to its lead's chests. Nothing
 * says a helper on another build nearby should be touched: two builds go up side by side, one is canceled or finished,
 * and the other build's helper keeps helping it.
 */
public class QaBuilderMutants1031GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos TABLE_A = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST_A = new BlockPos(2, 2, 4);
	private static final BlockPos TABLE_B = new BlockPos(13, 2, 2);
	private static final BlockPos CHEST_B = new BlockPos(13, 2, 4);
	private static final BlockPos TABLE_H = new BlockPos(15, 2, 2);

	/** Two leads with a hut each under way, and a third builder helping lead B's hut. */
	private record Two(ServerLevel level, Villager leadA, Villager leadB, Villager helperOfB, BuildSite a, BuildSite b) {
	}

	private static Two twoBuilds(GameTestHelper helper) {
		QaHelperLeftoversGameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(TABLE_A, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST_A, Blocks.CHEST);
		helper.setBlock(TABLE_B, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST_B, Blocks.CHEST);
		helper.setBlock(TABLE_H, ModBlocks.BUILDERS_BENCH);
		Villager leadA = helper.spawn(EntityType.VILLAGER, TABLE_A.east());
		Builders.employ(level, leadA, helper.absolutePos(TABLE_A));
		Villager leadB = helper.spawn(EntityType.VILLAGER, TABLE_B.south().east());
		Builders.employ(level, leadB, helper.absolutePos(TABLE_B));
		Villager mate = helper.spawn(EntityType.VILLAGER, TABLE_H.south().east());
		Builders.employ(level, mate, helper.absolutePos(TABLE_H));
		BuildSite a = Builders.start(level, leadA, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(5, 2, 7)), Rotation.NONE, Mirror.NONE));
		BuildSite b = Builders.start(level, leadB, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(12, 2, 7)), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(a != null && b != null, "setup: a hut didn't start (" + a + ", " + b + ")");
		Leftovers.after(helper, () -> {
			BuildSiteManager.get(level).remove(a.id());
			BuildSiteManager.get(level).remove(b.id());
		});
		// the third builder joins lead B's hut as a helper, as an idle builder's recruit does
		ModAttachments.BUILDER_JOB.set(mate, new BuilderJob(b.id(), true));
		b.seen(mate.getUUID(), level.getGameTime());
		return new Two(level, leadA, leadB, mate, a, b);
	}

	private static void assertBStillGoing(GameTestHelper helper, Two t, String when) {
		BuilderJob job = ModAttachments.BUILDER_JOB.get(t.helperOfB());
		helper.assertTrue(job != null && job.helper() && job.siteId().equals(t.b().id()),
			when + ": the helper of the OTHER hut (B) stopped helping it: its job is now " + job);
		helper.assertTrue(BuildSiteManager.get(t.level()).get(t.b().id()) != null, when + ": hut B's site is gone");
		BuilderJob leadJob = ModAttachments.BUILDER_JOB.get(t.leadB());
		helper.assertTrue(leadJob != null && !leadJob.helper() && leadJob.siteId().equals(t.b().id()),
			when + ": hut B's own builder lost his job: " + leadJob);
	}

	/** Hut A is canceled by its owner: hut B's helper (and its builder) carry on with hut B. */
	//$ gametest_ticks_batch AREA '200' '"qaBuilderMutants1031Cancel"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaBuilderMutants1031Cancel")
	public void cancelingOneBuildLeavesTheOtherBuildsHelperAtWork(GameTestHelper helper) {
		Leftovers.clear(helper);
		Two t = twoBuilds(helper);
		assertBStillGoing(helper, t, "before the cancel");
		Builders.cancel(t.level(), t.a());
		helper.assertTrue(BuildSiteManager.get(t.level()).get(t.a().id()) == null, "hut A wasn't canceled");
		assertBStillGoing(helper, t, "after hut A was canceled");
		LOGGER.info("qa builder mutants 1031: hut A canceled, hut B's helper still helps hut B");
		helper.succeed();
	}

	/** Hut A is finished: hut B's helper (and its builder) carry on with hut B. */
	//$ gametest_ticks_batch AREA '200' '"qaBuilderMutants1031Finish"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaBuilderMutants1031Finish")
	public void finishingOneBuildLeavesTheOtherBuildsHelperAtWork(GameTestHelper helper) {
		Leftovers.clear(helper);
		Two t = twoBuilds(helper);
		Builders.finish(t.level(), t.leadA(), t.a());
		assertBStillGoing(helper, t, "after hut A was finished");
		LOGGER.info("qa builder mutants 1031: hut A finished, hut B's helper still helps hut B");
		helper.succeed();
	}

	/**
	 * B499 + B636 (BuilderWork.refreshShortfall keeps the scaffolding count): no scaffolding at all for the pillar. Once
	 * the board asks for scaffolding, it keeps asking on every tick for the next 250 ticks (two of the builder's 100-tick
	 * rechecks of the rest of the build's shortfall) while none has come: the recheck must not wipe it off the board
	 * ("the shortfall shows on the Storehouse's requests board ... while the builder carries on").
	 */
	//$ gametest_ticks_batch BIG '3000' '"qaBuilderMutants1031Scaffold"'
	@GameTest(template = BIG, timeoutTicks = 3000, batch = "qaBuilderMutants1031Scaffold")
	public void scaffoldingStaysOnTheBoardThroughTheShortfallRechecks(GameTestHelper helper) {
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, 0);
		Leftovers.after(helper, () -> BuildSiteManager.get(s.level()).remove(s.site().id()));
		long[] askedAt = {-1};
		long[] gapAt = {-1};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			if (gapAt[0] >= 0 || BuildSiteManager.get(s.level()).get(s.site().id()) == null) {
				return;
			}
			boolean asks = false;
			for (io.github.jcondedata.aliveworkplace.work.Requests.Request r : QaScaffoldingGameTests.board(s)) {
				asks |= r.icon().is(net.minecraft.world.item.Items.SCAFFOLDING);
			}
			long t = helper.getTick();
			if (askedAt[0] < 0) {
				if (asks) {
					askedAt[0] = t;
				}
			} else if (t <= askedAt[0] + 250 && !asks && QaScaffoldingGameTests.chestCount(helper, net.minecraft.world.item.Items.SCAFFOLDING) == 0
				&& QaScaffoldingGameTests.bagCount(s.builder()) == 0) {
				gapAt[0] = t;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(askedAt[0] >= 0, "the board never asked for scaffolding with none in the chest");
			helper.assertTrue(gapAt[0] < 0, "the board asked for scaffolding at tick " + askedAt[0] + " but not at tick " + gapAt[0]
				+ ", with none brought (missing " + s.site().missing() + ", status " + s.site().status() + ")");
			helper.assertTrue(helper.getTick() > askedAt[0] + 250 || BuildSiteManager.get(s.level()).get(s.site().id()) == null,
				"watching the board for 250 ticks");
			LOGGER.info("qa builder mutants 1031: scaffolding asked from tick {} on, every tick for 250 ticks", askedAt[0]);
		});
	}
}
