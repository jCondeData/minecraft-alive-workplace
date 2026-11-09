package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.PieceLooks;
import io.github.jcondedata.aliveworkplace.world.VillagePieces;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (qa-1108c), house looks: failures against docs/wiki/house-looks.md, kept out of the suite (not registered) until the
 * mod is fixed. Setup: {@link QaHouseLooks1108cGameTests}.
 */
public class QaHouseLooks1108cBugTests implements FabricGameTest {
	private static final String AREA = QaHouseLooks1108cGameTests.AREA;

	/**
	 * The leader once picked the outside the house already had (refused as "already has", but remembered, as the wiki
	 * says); the house is then torn down. A pick now should say "That house isn't there any more." and start nothing,
	 * like for a house with no remembered choice; instead the builder is sent to put an outside up on the empty lot.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLooksBugTornRemembered"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLooksBugTornRemembered")
	public void aTornDownHouseWithARememberedLookIsGone(GameTestHelper helper) {
		QaHouseLooks1108cGameTests.Village v = QaHouseLooks1108cGameTests.village(helper, true);
		VillagePieces.Piece found = QaHouseLooks1108cGameTests.piece(v);
		helper.assertTrue(PieceLooks.choose(v.level(), v.hall(), v.owner(), found, "plains") == PieceLooks.Outcome.SAME, "setup: not SAME");
		helper.assertTrue(v.entity().pieceLooks().size() == 1, "setup: the choice isn't remembered");
		QaHouseLooks1108cGameTests.tearDown(v);
		PieceLooks.Outcome outcome = PieceLooks.choose(v.level(), v.hall(), v.owner(), found, "desert");
		BuildSite site = PieceLooks.rebuild(v.level(), found);
		if (site != null) {
			Builders.cancel(v.level(), site);
		}
		helper.assertTrue(outcome == PieceLooks.Outcome.GONE && site == null, "a house that's gone (its look remembered): " + outcome
			+ ", rebuild " + (site == null ? "none" : site.structure() + " on the empty lot"));
		helper.succeed();
	}

	/**
	 * The builder is busy with four builds waiting, and the desert pick waits as the fifth. Changing one's mind to taiga
	 * should call the desert rebuild off and queue the taiga one in its place (the wiki's "Changing your mind"), the queue
	 * staying at five; instead it's refused as "No builder of this village can reach the Guard House", with the bench
	 * a few blocks away.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLooksBugQueueChange"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLooksBugQueueChange")
	public void changingTheLookOfTheFifthWaitingBuild(GameTestHelper helper) {
		QaHouseLooks1108cGameTests.Village v = QaHouseLooks1108cGameTests.village(helper, true);
		ServerLevel level = v.level();
		VillagePieces.Piece found = QaHouseLooks1108cGameTests.piece(v);
		QaHouseLooks1108cGameTests.busy(helper, v, Builders.MAX_QUEUE - 1);
		helper.assertTrue(PieceLooks.choose(level, v.hall(), v.owner(), found, "desert") == PieceLooks.Outcome.QUEUED, "setup: desert not queued");
		PieceLooks.Outcome outcome = PieceLooks.choose(level, v.hall(), v.owner(), found, "taiga");
		String said = PieceLooks.message(level, v.hall(), found, "taiga", outcome, v.owner()).getString();
		BuildSite site = PieceLooks.rebuild(level, found);
		helper.assertTrue(outcome.ok() && site != null && site.structure().equals(VillagePieces.outsideId("taiga", "guard_house")),
			"changing the queued desert look to taiga: " + outcome + " (\"" + said + "\"), the open rebuild " + (site == null ? null : site.structure()));
		helper.succeed();
	}

	/**
	 * The only builder's queue is full (five waiting). The pick is refused, but the player is told "No builder of this
	 * village can reach the Guard House: give one a bench within 128 blocks of it." though his bench is a few blocks
	 * off: the wrong reason, and following its advice changes nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLooksBugQueueFull"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLooksBugQueueFull")
	public void aFullQueueIsNotExplainedAsOutOfReach(GameTestHelper helper) {
		QaHouseLooks1108cGameTests.Village v = QaHouseLooks1108cGameTests.village(helper, true);
		ServerLevel level = v.level();
		VillagePieces.Piece found = QaHouseLooks1108cGameTests.piece(v);
		QaHouseLooks1108cGameTests.busy(helper, v, Builders.MAX_QUEUE);
		PieceLooks.Outcome outcome = PieceLooks.choose(level, v.hall(), v.owner(), found, "desert");
		String said = PieceLooks.message(level, v.hall(), found, "desert", outcome, v.owner()).getString();
		helper.assertTrue(!outcome.ok(), "setup: a sixth build was queued");
		helper.assertTrue(!said.contains("can reach") && !said.contains("within 128 blocks"), "a full queue is told as: " + said);
		helper.succeed();
	}
}
