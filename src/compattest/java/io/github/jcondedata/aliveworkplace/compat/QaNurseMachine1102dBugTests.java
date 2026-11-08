package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.block.entity.HealingMachineBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.Villager;

/**
 * Player-visible bugs QA found in the Nurse at a Healing Machine (README "Nurses", ROADMAP 28.7); they fail until fixed.
 * Setup is {@link QaNurseMachine1102dCompatTests}'s.
 */
public class QaNurseMachine1102dBugTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/**
	 * README "once a minute per player": while the machine is busy with someone else, a hurt player clicking twice in a
	 * few seconds gets their health back at most once.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaNurseMachineBusyHealth"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaNurseMachineBusyHealth")
	public void qaBusyMachineDoesNotGiveFreeHealthEveryClick(GameTestHelper helper) {
		QaNurseMachine1102dCompatTests.clean(helper);
		Villager nurse = QaNurseMachine1102dCompatTests.nurse(helper, QaNurseMachine1102dCompatTests.MACHINE, QaNurseMachine1102dCompatTests.NURSE_AT, 1);
		QaNurseMachine1102dCompatTests.Visitor a = QaNurseMachine1102dCompatTests.visitor(helper, "qaNurseA");
		QaNurseMachine1102dCompatTests.Visitor b = QaNurseMachine1102dCompatTests.visitor(helper, "qaNurseB");
		QaNurseMachine1102dCompatTests.pokemon(a, "pikachu", 1);
		QaNurseMachine1102dCompatTests.pokemon(b, "charmander", 2);
		QaNurseMachine1102dCompatTests.rightClick(a, nurse);
		HealingMachineBlockEntity machine = helper.getBlockEntity(QaNurseMachine1102dCompatTests.MACHINE);
		helper.assertTrue(machine.isInUse(), "setup: A's team didn't go in");
		b.player().setHealth(5);
		QaNurseMachine1102dCompatTests.rightClick(b, nurse);
		helper.assertTrue(QaNurseMachine1102dCompatTests.said(b, "busy"), "B isn't told the machine is busy: " + b.seen());
		b.player().setHealth(5);
		helper.runAfterDelay(20, () -> {
			QaNurseMachine1102dCompatTests.rightClick(b, nurse);
			helper.assertTrue(b.player().getHealth() == 5f, "B was healed again a second later (health " + b.player().getHealth()
				+ "); B heard " + b.seen());
			helper.succeed();
		});
	}

	/**
	 * Once a minute: asked right after a treatment, she says to come back in a minute (60 seconds), not 61.
	 */
	//$ gametest_batch AREA '"qaNurseMachineWaitSeconds"'
	@GameTest(template = AREA, batch = "qaNurseMachineWaitSeconds")
	public void qaTheWaitRightAfterAHealIsAMinute(GameTestHelper helper) {
		QaNurseMachine1102dCompatTests.clean(helper);
		Villager nurse = QaNurseMachine1102dCompatTests.nurse(helper, QaNurseMachine1102dCompatTests.MACHINE, QaNurseMachine1102dCompatTests.NURSE_AT, 1);
		QaNurseMachine1102dCompatTests.Visitor v = QaNurseMachine1102dCompatTests.visitor(helper, "qaNurseA");
		QaNurseMachine1102dCompatTests.pokemon(v, "pikachu", 1);
		QaNurseMachine1102dCompatTests.rightClick(v, nurse);
		v.seen().clear();
		QaNurseMachine1102dCompatTests.rightClick(v, nurse);
		// Her minute is 1,200 ticks at her pace (traits and mood move it a few seconds, B867): 60 seconds at a pace of 1,
		// and never one second more than her minute, rounded up.
		String wait = "Come back in " + io.github.jcondedata.aliveworkplace.nurse.Nurses.secondsLeft(
			io.github.jcondedata.aliveworkplace.build.BuilderLevels.delay(io.github.jcondedata.aliveworkplace.nurse.Nurses.COOLDOWN, nurse)) + " seconds";
		helper.assertTrue(QaNurseMachine1102dCompatTests.said(v, wait), "right after a treatment she says " + v.seen() + ", not \"" + wait + "\"");
		helper.succeed();
	}
}
