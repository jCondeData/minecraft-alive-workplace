package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.Builders;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA 2026-11-07 (b), roadmap 23.1a, B1019 (fixed: a lead takes from a crewmate only what the work ahead needs). A lead Builder with no chest by its
 * bench and no Storehouse takes the one stone its build needs from a crewmate close by, but takes up to needed + 32
 * (BuilderWork#takeFromCrewmate), more than "the stretch of work ahead still needs" (builder.md, Materials). When the
 * build ends it empties its bag where there's no chest: the spare stone is dropped on the ground by the bench, out of
 * the crewmate's bag, for anyone (or despawning after 5 minutes). Expected: the spare is never taken, or goes back to
 * a bag or a chest; no build material lies on the ground.
 */
public class QaCrewPass1107bBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");

	//$ gametest_ticks_batch 'QaCrewPass1107bGameTests.HUGE' '1200' '"qa1107bCrewPassSpareBug"'
	@GameTest(template = QaCrewPass1107bGameTests.HUGE, timeoutTicks = 1200, batch = "qa1107bCrewPassSpareBug")
	public void aLeadWithNoChestLeavesNoSpareStoneOfACrewmatesBagOnTheGround(GameTestHelper helper) {
		final int stock = 40;
		Vec3 start = QaCrewPass1107bGameTests.cell(helper);
		QaCrewPass1107bGameTests.Run r = QaCrewPass1107bGameTests.setUp(helper, 2, stock, start.add(4, 0, 0));
		helper.onEachTick(() -> QaCrewPass1107bGameTests.keepOnCrew(r));
		helper.succeedWhen(() -> {
			helper.assertTrue(QaCrewPass1107bGameTests.placed(helper) == 1 && r.site().stage() == BuildPlan.Stage.DONE
				&& Builders.activeSite(r.level(), r.l()) == null, "not built yet: " + QaCrewPass1107bGameTests.where(helper, r));
			int ground = QaCrewPass1107bGameTests.onGround(helper);
			int bags = QaCrewPass1107bGameTests.bag(r.m()) + QaCrewPass1107bGameTests.bag(r.l());
			helper.assertTrue(ground == 0 && bags == stock - 1, "the build used 1 of the crewmate's " + stock + " stone, and "
				+ ground + " spare stone lie on the ground by the bench (bags hold " + bags + "): "
				+ QaCrewPass1107bGameTests.where(helper, r));
			LOGGER.info("qa 1107b crew pass spare: nothing on the ground, {} stone back in bags", bags);
		});
	}
}
