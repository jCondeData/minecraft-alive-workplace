package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.ProfessorsThesis;
import io.github.jcondedata.aliveworkplace.wonder.powers.StadiumDaysPower;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (qa-lane-d-1028-0109) bug tests for ROADMAP 35.24, failing until the mod is fixed (they travel uncommitted with
 * the bug, see the tester skill). The Stadium's {@code stadium_days} power reads {@code min_visitors} and
 * {@code max_visitors} from 1 to 4, and its page line has a branch for one fixed count, but the line is not counted:
 * a Wonder file with one visitor reads "1 visiting trainers" (the mod's rule: counted sentences go through
 * {@code Words.counted} with a {@code .one} key).
 * <p>
 * ROADMAP 31.21: Thessaly "comes to the hall and battles at Master strength through the trainer engine, then leaves"
 * (and her own javadoc: "standing by the hall until she's beaten or the arc ends"). She is dismissed only when beaten or
 * at the published ending; a thesis that ends any other way (stopped with {@code /workplace story stop}, or switched
 * off with {@code disabledArcs}) leaves her standing by the hall for good, a persistent Master trainer who pays the
 * Master's prize to anyone who beats her, every day.
 */
public class QaStadiumThesisBugCompatTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_compat:huge_area";

	/** A Stadium Days power of one visitor: "Stadium Days every second afternoon: 1 visiting trainer in its boxes". */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void onePowerVisitorIsOneVisitingTrainer(GameTestHelper helper) {
		String line = new StadiumDaysPower(2, 1, 1).describe().getString();
		helper.assertTrue(line.equals("Stadium Days every second afternoon: 1 visiting trainer in its boxes"), "the page reads '" + line + "'");
		helper.succeed();
	}

	private static Villager peerReviewWithThessaly(GameTestHelper helper, ArcState s) {
		ServerLevel level = helper.getLevel();
		QaThesis1028CompatTests.fieldNotes(helper, QaThesis1028CompatTests.player(helper));
		Arcs.next(level, helper.absolutePos(new BlockPos(3, 2, 3)));
		Villager thessaly = ProfessorsThesis.thessaly(level, s);
		helper.assertTrue(s.chapter == 2 && thessaly != null && thessaly.isAlive(), "setup: Peer Review with Thessaly");
		PartnerShowsCompatTests.after(helper, thessaly::discard);
		return thessaly;
	}

	/** The thesis stopped (/workplace story stop) during Peer Review: Thessaly leaves with it. */
	//$ gametest_ticks_batch HUGE '200' '"qaThesisBugStop"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaThesisBugStop")
	public void thessalyLeavesWhenTheThesisIsStopped(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ArcState s = QaThesis1028CompatTests.start(helper, "cold");
		Villager thessaly = peerReviewWithThessaly(helper, s);
		BlockPos hall = helper.absolutePos(new BlockPos(3, 2, 3));
		helper.assertTrue(Arcs.stop(level, hall) && Arcs.running(level, hall) == null, "the thesis didn't stop");
		helper.assertTrue(thessaly.isRemoved(), "the thesis was stopped, but Thessaly (a Master trainer, tags " + thessaly.getTags()
			+ ") still stands by the hall");
		helper.succeed();
	}

	/** The thesis switched off (disabledArcs) during Peer Review: the hall's round ends it, and Thessaly leaves. */
	//$ gametest_ticks_batch HUGE '200' '"qaThesisBugOff"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaThesisBugOff")
	public void thessalyLeavesWhenTheThesisIsSwitchedOff(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ArcState s = QaThesis1028CompatTests.start(helper, "cold");
		Villager thessaly = peerReviewWithThessaly(helper, s);
		BlockPos hall = helper.absolutePos(new BlockPos(3, 2, 3));
		Set<String> was = Arcs.DISABLED;
		Arcs.DISABLED = Set.of("professors_thesis");
		try {
			Arcs.round(level, hall, (VillageHallBlockEntity) level.getBlockEntity(hall));
		} finally {
			Arcs.DISABLED = was;
		}
		helper.assertTrue(Arcs.running(level, hall) == null, "the switched-off thesis didn't end");
		helper.assertTrue(thessaly.isRemoved(), "the thesis was switched off, but Thessaly still stands by the hall");
		helper.succeed();
	}
}
