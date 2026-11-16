package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * QA (lane-e-1116) for 26.8: questbook sentences the mod disagrees with, found by
 * {@link QaQuestbookClaims1116eGameTests}'s approach (the text is the spec). Not registered: they fail until the mod or
 * the book changes. Each is alone in its batch.
 */
public class QaQuestbookClaims1116eBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	/**
	 * "Build a Starter Cottage": "this quest's reward puts Starter Cottage II in your Blueprint Table." The reward is
	 * {@code /workplace unlock {p} aliveworkplace:starter_cottage_2}, run when the player claims it, wherever they stand
	 * (the quest completes when the builder finishes, the owner anywhere online, and the book can be opened anywhere).
	 * Claimed by the hall's owner while away from the village, the build should still be on the village's Table.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaQb1116eBugRewardAway"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaQb1116eBugRewardAway")
	public void qaARewardClaimedAwayFromTheVillageStillReachesItsTable(GameTestHelper helper) {
		boolean was = BlueprintUnlocks.ENABLED;
		BlueprintUnlocks.ENABLED = true;
		Leftovers.after(helper, () -> BlueprintUnlocks.ENABLED = was);
		VillageHallBlockEntity entity = QaQuestbookClaims1116eGameTests.hall(helper);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = entity.getBlockPos();
			VillageHalls.forgetNearest();
			helper.assertTrue(BlueprintUnlocks.state(level, hall, StarterBlueprints.STARTER_COTTAGE_2.id()) == BlueprintUnlocks.State.LOCKED,
				"setup: Starter Cottage II isn't locked in a new village");
			BlockPos away = null;
			for (int blocks : new int[] {200, 300, 500, 800, 1200}) {
				BlockPos spot = hall.offset(blocks, 0, 0);
				if (VillageHalls.nearest(level, spot).isEmpty()) {
					away = spot;
					break;
				}
			}
			helper.assertTrue(away != null, "setup: no spot away from every village");
			ServerPlayer owner = QaQuestbookClaims1116eGameTests.playerAt(helper, away.getCenter());
			entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
			QaQuestbookClaims1116eGameTests.reward(level, owner, StarterBlueprints.STARTER_COTTAGE_2.id());
			helper.assertTrue(BlueprintUnlocks.state(level, hall, StarterBlueprints.STARTER_COTTAGE_2.id()) == BlueprintUnlocks.State.UNLOCKED,
				"the owner claimed the Starter Cottage's reward " + (away.getX() - hall.getX()) + " blocks from the hall and Starter Cottage II isn't on the "
				+ "village's Table: " + BlueprintUnlocks.state(level, hall, StarterBlueprints.STARTER_COTTAGE_2.id()));
			helper.succeed();
		});
	}

	/**
	 * "Music: a Bandstand": "Its name is gray at the Blueprint Table at first: it's locked. Pick it there and the line
	 * under its name says how to get it." In a new village the Bandstand should be LOCKED (named, gray), not HIDDEN (a
	 * dark ??? with no name, preview or how-to).
	 */
	//$ gametest_ticks_batch AREA '40' '"qaQb1116eBugBandstand"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaQb1116eBugBandstand")
	public void qaTheBandstandIsGrayAndNamedAtFirstNotAQuestionMark(GameTestHelper helper) {
		boolean was = BlueprintUnlocks.ENABLED;
		BlueprintUnlocks.ENABLED = true;
		Leftovers.after(helper, () -> BlueprintUnlocks.ENABLED = was);
		VillageHallBlockEntity entity = QaQuestbookClaims1116eGameTests.hall(helper);
		helper.runAfterDelay(3, () -> {
			BlueprintUnlocks.State state = BlueprintUnlocks.state(helper.getLevel(), entity.getBlockPos(), StarterBlueprints.BANDSTAND.id());
			helper.assertTrue(state == BlueprintUnlocks.State.LOCKED, "the Bandstand at a new village's Table is " + state + ", not LOCKED (gray, named)");
			helper.succeed();
		});
	}

	/**
	 * "Guards: the Barracks" (fill it with guards): "Bigger villages draw bigger raids: 3 raiders, plus one for every 4
	 * villagers." A village of 12 with the Barracks' two guards should draw 6 raiders, as with none.
	 */
	//$ gametest_batch AREA '"qaQb1116eBugRaidGuards"'
	@GameTest(template = AREA, batch = "qaQb1116eBugRaidGuards")
	public void qaGuardsDontChangeTheRaidSizeTheBarracksQuestGives(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(new BlockPos(2, 2, 2));
		helper.assertTrue(Lairs.near(level, hall).isEmpty() && Threats.LEVEL < 3, "setup: a lair near, or a hard threat level");
		int none = VillageRaids.expected(level, hall, 12, 0, Threats.MONSTERS);
		int two = VillageRaids.expected(level, hall, 12, 2, Threats.MONSTERS);
		int eight = VillageRaids.expected(level, hall, 12, 8, Threats.MONSTERS);
		helper.assertTrue(none == 6 && two == 6 && eight == 6,
			"12 villagers draw " + none + " raiders with no guards, " + two + " with 2 guards and " + eight + " with 8; the quest says 3 + 12/4 = 6");
		helper.succeed();
	}
}
