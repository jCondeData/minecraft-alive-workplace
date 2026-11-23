package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane B, 2026-11-23): B1500, B1511 and B1513, a Builder killed mid-refresh of the owner's furnished old Stone
 * House. What a player expects (the bugs' "Expected"): whatever turn the house has, however far the first Builder got
 * and whether or not the server restarted in between, the new Builder's refresh ends as a fresh build of today's drawing,
 * taken up from the drawing it was begun from; an older save without that memory still loads and refreshes; a
 * remembered drawing that is no longer shipped doesn't hold a refresh back. Staged with QaJobFixes1122cGameTests' house.
 */
public class QaRefreshResume1123bGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** What happens to the world's build sites between the kill and the new Builder. */
	private enum Between {
		NOTHING,
		/** saved and loaded, as a server restart does */
		SAVE,
		/** saved, the B1500 memory taken out of the save (as a save written before it has none), and loaded */
		OLDER_SAVE
	}

	/**
	 * The refresh pressed, the Builder killed {@code killAfter} ticks after the refresh reached {@code killIn} (or a later
	 * stage), {@code between}, then a new Builder at the first one's bench takes it up through the Repairs page. With
	 * {@code fresh}, the house must end as a fresh build of today's drawing; without (an older save, where the drawing is
	 * guessed again), it must still finish with nothing left remembered or running.
	 */
	private static void killedAndTakenUp(GameTestHelper helper, Rotation rotation, BuildPlan.Stage killIn, int killAfter, Between between, boolean fresh) {
		QaJobFixes1122cGameTests.Stage s = QaJobFixes1122cGameTests.house(helper, rotation);
		Map<BlockPos, BlockState> was = BuildPlan.create(s.asBuilt(), s.placement()).targets();
		Map<BlockPos, BlockState> now = BuildPlan.create(QaJobFixes1122cGameTests.currentFor(s), s.placement()).targets();
		Villager[] second = {null};
		boolean[] killed = {false};
		boolean[] repressed = {false};
		boolean[] takenUp = {false};
		int[] ticks = {0};
		String[] from = {null};
		String[] killedIn = {"?"};
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(QaJobFixes1122cGameTests.pressRefresh(helper, s), "the old Stone House has no Refresh on the Repairs page");
			BuildSite site = Refreshes.siteOf(s.level(), s.placement());
			helper.assertTrue(site != null, "no Builder took the refresh up");
			from[0] = site.refreshFrom();
			helper.assertTrue(from[0].equals(BuildSiteManager.get(s.level()).refreshBegun(s.placement())),
				"begun from " + from[0] + " but remembered as " + BuildSiteManager.get(s.level()).refreshBegun(s.placement()));
			QaJobFixes1122cGameTests.stock(helper, s, site.plan(s.level()).materials());
		});
		helper.onEachTick(() -> {
			if (killed[0]) {
				helper.assertTrue(second[0].isAlive(), "the new Builder died at " + QaJobFixes1122cGameTests.rel(helper, second[0].blockPosition()));
				if (!repressed[0] && Refreshes.siteOf(s.level(), s.placement()) == null && !BuildSiteManager.get(s.level()).refreshAsked(s.placement())) {
					repressed[0] = true;
					helper.assertTrue(QaJobFixes1122cGameTests.pressRefresh(helper, s),
						"the house left half refreshed (killed in " + killedIn[0] + ") isn't offered a Refresh on the Repairs page any more");
					helper.assertTrue(Refreshes.siteOf(s.level(), s.placement()) != null, "the new Builder didn't take the refresh up");
				}
				BuildSite taken = Refreshes.siteOf(s.level(), s.placement());
				if (taken != null && second[0].getUUID().equals(taken.builder())) {
					takenUp[0] = true;
				}
				if (fresh) {
					helper.assertTrue(taken == null || from[0].equals(taken.refreshFrom()),
						"begun from the older drawing " + from[0] + ", taken up again from " + (taken == null ? "?" : taken.refreshFrom()));
				}
				return;
			}
			BuildSite site = Refreshes.siteOf(s.level(), s.placement());
			if (site == null || site.stage().ordinal() < killIn.ordinal() || ++ticks[0] < killAfter) {
				return;
			}
			killed[0] = true;
			killedIn[0] = site.stage().name();
			s.builder().kill();
			BuildSiteManager manager = BuildSiteManager.get(s.level());
			if (between != Between.NOTHING) {
				HolderLookup.Provider registries = s.level().registryAccess();
				CompoundTag saved = manager.save(new CompoundTag(), registries);
				if (between == Between.OLDER_SAVE) {
					ListTag finished = saved.getList("finished", Tag.TAG_COMPOUND);
					int stripped = 0;
					for (int i = 0; i < finished.size(); i++) {
						if (finished.getCompound(i).contains("refresh_begun")) {
							finished.getCompound(i).remove("refresh_begun");
							stripped++;
						}
					}
					helper.assertTrue(stripped > 0, "the save holds no refresh_begun to take out: the drawing it was begun from wasn't saved");
				}
				s.level().getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(saved, registries));
				manager = BuildSiteManager.get(s.level());
			}
			String remembered = manager.refreshBegun(s.placement());
			if (between == Between.OLDER_SAVE) {
				helper.assertTrue(remembered == null, "an older save remembers a begun drawing: " + remembered);
			} else {
				helper.assertTrue(from[0].equals(remembered), "the drawing the refresh was begun from (" + from[0] + ") is forgotten after the kill"
					+ (between == Between.SAVE ? " and a save and load: " : ": ") + remembered);
			}
			BuildSite left = Refreshes.siteOf(s.level(), s.placement());
			helper.assertTrue(left == null || !s.builder().getUUID().equals(left.builder()), "the refresh is still set to the dead Builder");
			BlockPos bench = s.corner().offset(2, 0, 4);
			if (!s.level().getBlockState(helper.absolutePos(bench)).is(ModBlocks.BUILDERS_BENCH)) {
				helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
			}
			Villager next = helper.spawn(EntityType.VILLAGER, s.corner().offset(2, 0, 2));
			Builders.employ(s.level(), next, helper.absolutePos(bench));
			ModAttachments.BUILDER_EMPLOYER.set(next, new Employer(s.owner().getUUID(), s.owner().getGameProfile().getName()));
			second[0] = next;
			AliveWorkplace.LOG.info("QA refresh resume 1123b: {} house, Builder killed {} ticks into {} (between: {})", rotation, ticks[0], killedIn[0], between);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(killed[0], "the Builder was never killed mid-way");
			helper.assertTrue(takenUp[0], "the new Builder never took the refresh up");
			if (fresh) {
				QaJobFixes1122cGameTests.assertFresh(helper, s, was, now);
			} else {
				helper.assertTrue(Refreshes.siteOf(s.level(), s.placement()) == null, "still refreshing");
				helper.assertTrue(BuildSiteManager.get(s.level()).all().stream().noneMatch(b -> b.placement().equals(s.placement())), "a site left on the house");
				helper.assertTrue(BuildSiteManager.get(s.level()).refreshBegun(s.placement()) == null, "the finished refresh is still remembered as begun");
				AliveWorkplace.LOG.info("QA refresh resume 1123b: after the older save, {} spots differ from a fresh build",
					RefreshMatchesFreshBuildGameTests.differences(s.level(), s.placement(), s.asBuilt(), QaJobFixes1122cGameTests.currentFor(s)).size());
			}
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(second[0]), "the new Builder is still at it");
		});
	}

	/** B1511/B1513 unturned: the house as drawn (no turn), its Builder killed 2,500 ticks in, still clearing. */
	//$ gametest_ticks_batch HUGE '20000' '"qaRefreshResume1123bNone"'
	@GameTest(template = HUGE, timeoutTicks = 20000, batch = "qaRefreshResume1123bNone")
	public void qaAnUnturnedStoneHouseKilledLateInItsRefreshEndsFresh(GameTestHelper helper) {
		killedAndTakenUp(helper, Rotation.NONE, BuildPlan.Stage.CLEAR, 2500, Between.NOTHING, true);
	}

	/** The quarter turn the bugs never tried (CLOCKWISE_90), killed 2,500 ticks in, the world saved and loaded before the new Builder. */
	//$ gametest_ticks_batch HUGE '20000' '"qaRefreshResume1123bClockwise"'
	@GameTest(template = HUGE, timeoutTicks = 20000, batch = "qaRefreshResume1123bClockwise")
	public void qaAStoneHouseTurnedClockwiseKilledLateThenSavedEndsFresh(GameTestHelper helper) {
		killedAndTakenUp(helper, Rotation.CLOCKWISE_90, BuildPlan.Stage.CLEAR, 2500, Between.SAVE, true);
	}

	/**
	 * Past the clearing: the half-turned house (B1500's turn), its Builder killed 200 ticks into putting the new drawing up,
	 * and a save and load before the new Builder: the house is still offered, and ends fresh.
	 */
	//$ gametest_ticks_batch HUGE '20000' '"qaRefreshResume1123bStructure"'
	@GameTest(template = HUGE, timeoutTicks = 20000, batch = "qaRefreshResume1123bStructure")
	public void qaAStoneHouseKilledWhileTheNewDrawingGoesUpEndsFresh(GameTestHelper helper) {
		killedAndTakenUp(helper, Rotation.CLOCKWISE_180, BuildPlan.Stage.STRUCTURE, 200, Between.SAVE, true);
	}

	/**
	 * A save written before B1500 (no refresh_begun) loaded after the kill: no crash, the house is still offered a Refresh
	 * (the drawing guessed again, as before), the new Builder finishes it, and nothing is left remembered or running.
	 */
	//$ gametest_ticks_batch HUGE '20000' '"qaRefreshResume1123bOlderSave"'
	@GameTest(template = HUGE, timeoutTicks = 20000, batch = "qaRefreshResume1123bOlderSave")
	public void qaAnOlderSaveWithoutTheBegunDrawingStillRefreshesAfterAKill(GameTestHelper helper) {
		killedAndTakenUp(helper, Rotation.NONE, BuildPlan.Stage.CLEAR, 1500, Between.OLDER_SAVE, false);
	}

	/**
	 * A remembered drawing that is no longer shipped (a key the history doesn't hold, as after a drawing is dropped) doesn't
	 * hold the refresh back or steer it: Refresh starts from the drawing the house stands as, remembers that one instead,
	 * and the house ends fresh with nothing remembered.
	 */
	//$ gametest_ticks_batch HUGE '20000' '"qaRefreshResume1123bStale"'
	@GameTest(template = HUGE, timeoutTicks = 20000, batch = "qaRefreshResume1123bStale")
	public void qaARememberedDrawingNoLongerShippedDoesNotSteerTheRefresh(GameTestHelper helper) {
		QaJobFixes1122cGameTests.Stage s = QaJobFixes1122cGameTests.house(helper, Rotation.COUNTERCLOCKWISE_90);
		Map<BlockPos, BlockState> was = BuildPlan.create(s.asBuilt(), s.placement()).targets();
		Map<BlockPos, BlockState> now = BuildPlan.create(QaJobFixes1122cGameTests.currentFor(s), s.placement()).targets();
		String[] guessed = {null};
		helper.runAfterDelay(2, () -> {
			BuildSiteManager manager = BuildSiteManager.get(s.level());
			BuildSiteManager.Finished f = manager.finishedIn(s.level()).stream().filter(b -> b.placement().equals(s.placement())).findFirst()
				.orElseThrow(() -> new IllegalStateException("the house isn't recorded as finished"));
			guessed[0] = Refreshes.change(s.level(), f).orElseThrow(() -> new IllegalStateException("the old house would change nothing")).from();
			manager.setRefreshBegun(s.placement(), "0000000gone");
			// a save and load in between, so the stale key is the one a server would read back
			HolderLookup.Provider registries = s.level().registryAccess();
			s.level().getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(manager.save(new CompoundTag(), registries), registries));
			helper.assertTrue("0000000gone".equals(BuildSiteManager.get(s.level()).refreshBegun(s.placement())), "the stale key didn't survive the save");
			helper.assertTrue(QaJobFixes1122cGameTests.pressRefresh(helper, s), "a remembered drawing no longer shipped hid the Refresh button");
			BuildSite site = Refreshes.siteOf(s.level(), s.placement());
			helper.assertTrue(site != null, "no Builder took the refresh up");
			helper.assertTrue(guessed[0].equals(site.refreshFrom()), "the refresh began from " + site.refreshFrom() + ", not the drawing the house stands as, " + guessed[0]);
			helper.assertTrue(guessed[0].equals(BuildSiteManager.get(s.level()).refreshBegun(s.placement())),
				"remembered as begun from " + BuildSiteManager.get(s.level()).refreshBegun(s.placement()) + ", not " + guessed[0]);
			QaJobFixes1122cGameTests.stock(helper, s, site.plan(s.level()).materials());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(guessed[0] != null, "not started");
			QaJobFixes1122cGameTests.assertFresh(helper, s, was, now);
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(s.builder()), "the Builder is still at it");
		});
	}
}
