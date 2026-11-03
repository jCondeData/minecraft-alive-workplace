package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.block.entity.HealingMachineBlockEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane D, 2026-11-03) for B850 (README "Nurses": a nurse whose Healing Machine is busy with another player's team
 * treats nothing until it's free, and your minute isn't used), written from that sentence, through a real player's
 * right-click. Corners the bug's own tests leave: a player still in their minute who clicks while the machine is busy
 * is told their wait (not "busy"), and the busy machine doesn't give them their minute back; many busy clicks in a row
 * heal, cure and spend nothing, and the first click once it's free treats in full; a player with no team at all is
 * also asked to wait while it's busy, then patched up. Setup is {@link QaNurseMachine1102dCompatTests}'s.
 */
public class QaNurseBusy1103dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/**
	 * A Novice nurse at the machine whose minute is exactly 1200 ticks: traits (from her random UUID) are off for the
	 * test, since a diligent or lazy nurse's minute is 55 or 66 seconds.
	 */
	private static Villager novice(GameTestHelper helper) {
		boolean traits = io.github.jcondedata.aliveworkplace.people.Traits.ENABLED;
		io.github.jcondedata.aliveworkplace.people.Traits.ENABLED = false;
		PartnerShowsCompatTests.after(helper, () -> io.github.jcondedata.aliveworkplace.people.Traits.ENABLED = traits);
		Villager nurse = QaNurseMachine1102dCompatTests.nurse(helper, QaNurseMachine1102dCompatTests.MACHINE, QaNurseMachine1102dCompatTests.NURSE_AT, 1);
		helper.assertValueEqual(io.github.jcondedata.aliveworkplace.build.BuilderLevels.delay(io.github.jcondedata.aliveworkplace.nurse.Nurses.COOLDOWN, nurse),
			io.github.jcondedata.aliveworkplace.nurse.Nurses.COOLDOWN, "setup: a Novice nurse's minute in ticks");
		return nurse;
	}

	private static void hurt(QaNurseMachine1102dCompatTests.Visitor v) {
		v.player().setHealth(5f);
		v.player().addEffect(new MobEffectInstance(MobEffects.POISON, 1200));
	}

	private static void untreated(GameTestHelper helper, QaNurseMachine1102dCompatTests.Visitor v, String when) {
		helper.assertTrue(v.player().getHealth() == 5f, when + ": healed (" + v.player().getHealth() + "); heard " + v.seen());
		helper.assertTrue(v.player().hasEffect(MobEffects.POISON), when + ": poison cleared; heard " + v.seen());
	}

	/**
	 * B was treated two seconds before A's team goes in the machine. B clicks while it's busy: told to come back in 58
	 * seconds (their own minute, which comes first), not that the machine is busy, and nothing is treated: the busy
	 * machine neither restarted nor forgot B's minute. Once the machine is free, B is still in their minute.
	 */
	//$ gametest_ticks_batch AREA '1100' '"qaNurseBusyInMinute1103d"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qaNurseBusyInMinute1103d")
	public void qaAPlayerInTheirMinuteIsToldTheWaitNotBusy(GameTestHelper helper) {
		QaNurseMachine1102dCompatTests.clean(helper);
		Villager nurse = novice(helper);
		QaNurseMachine1102dCompatTests.Visitor a = QaNurseMachine1102dCompatTests.visitor(helper, "qaBusyMinA");
		QaNurseMachine1102dCompatTests.Visitor b = QaNurseMachine1102dCompatTests.visitor(helper, "qaBusyMinB");
		QaNurseMachine1102dCompatTests.pokemon(a, "pikachu", 1);
		HealingMachineBlockEntity machine = helper.getBlockEntity(QaNurseMachine1102dCompatTests.MACHINE);
		hurt(b);
		QaNurseMachine1102dCompatTests.rightClick(b, nurse);
		helper.assertTrue(b.player().getHealth() == b.player().getMaxHealth(), "setup: B (no team) wasn't patched up: " + b.seen());
		helper.assertFalse(machine.isInUse(), "setup: B's empty team started the machine");
		boolean[] busyChecked = {false};
		helper.runAfterDelay(40, () -> {
			QaNurseMachine1102dCompatTests.rightClick(a, nurse);
			helper.assertTrue(machine.isInUse(), "setup: A's team didn't go in: " + a.seen());
			b.seen().clear();
			hurt(b);
			QaNurseMachine1102dCompatTests.rightClick(b, nurse);
			helper.assertTrue(QaNurseMachine1102dCompatTests.said(b, "Come back in 58 seconds"), "B, 2 seconds into their minute, while busy: " + b.seen());
			helper.assertFalse(QaNurseMachine1102dCompatTests.said(b, "busy"), "B, in their minute, was told the machine is busy: " + b.seen());
			untreated(helper, b, "B in their minute, machine busy");
			busyChecked[0] = true;
		});
		boolean[] done = {false};
		helper.succeedWhen(() -> {
			helper.assertTrue(busyChecked[0], "A's team isn't in yet");
			if (!done[0] && !machine.isInUse()) {
				b.seen().clear();
				hurt(b);
				QaNurseMachine1102dCompatTests.rightClick(b, nurse);
				helper.assertTrue(QaNurseMachine1102dCompatTests.said(b, "Come back in"), "B, once free but still in their minute: " + b.seen());
				untreated(helper, b, "B once free but in their minute");
				done[0] = true;
			}
			helper.assertTrue(done[0], "the machine is still busy");
		});
	}

	/**
	 * Five busy clicks on five ticks in a row: each says busy, none heals, cures or touches B's team or starts a minute; on
	 * the first tick the machine is free, B's click treats in full and puts B's team in; then it's a 60-second wait.
	 */
	//$ gametest_ticks_batch AREA '1100' '"qaNurseBusyManyClicks1103d"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qaNurseBusyManyClicks1103d")
	public void qaManyBusyClicksSpendNothingAndTheFirstFreeClickTreats(GameTestHelper helper) {
		QaNurseMachine1102dCompatTests.clean(helper);
		Villager nurse = novice(helper);
		QaNurseMachine1102dCompatTests.Visitor a = QaNurseMachine1102dCompatTests.visitor(helper, "qaBusyManyA");
		QaNurseMachine1102dCompatTests.Visitor b = QaNurseMachine1102dCompatTests.visitor(helper, "qaBusyManyB");
		QaNurseMachine1102dCompatTests.pokemon(a, "pikachu", 1);
		Pokemon charmander = QaNurseMachine1102dCompatTests.pokemon(b, "charmander", 2);
		HealingMachineBlockEntity machine = helper.getBlockEntity(QaNurseMachine1102dCompatTests.MACHINE);
		QaNurseMachine1102dCompatTests.rightClick(a, nurse);
		helper.assertTrue(machine.isInUse(), "setup: A's team didn't go in: " + a.seen());
		helper.assertTrue(machine.getHealTimeLeft() > 6, "setup: the machine heals in " + machine.getHealTimeLeft() + " ticks, too few for five clicks");
		int[] busy = {0};
		for (int i = 0; i < 5; i++) {
			int click = i;
			helper.runAfterDelay(1 + i, () -> {
				helper.assertTrue(machine.isInUse(), "setup: the machine was free by busy click " + click);
				b.seen().clear();
				hurt(b);
				QaNurseMachine1102dCompatTests.rightClick(b, nurse);
				helper.assertTrue(QaNurseMachine1102dCompatTests.said(b, "busy with another team"), "busy click " + click + ": " + b.seen());
				helper.assertFalse(QaNurseMachine1102dCompatTests.said(b, "Come back in"), "busy click " + click + " started B's minute: " + b.seen());
				untreated(helper, b, "busy click " + click);
				helper.assertFalse(charmander.isFullHealth(), "busy click " + click + " healed B's team");
				helper.assertTrue(a.player().getUUID().equals(machine.getCurrentUser()), "busy click " + click + " took the machine from A");
				busy[0]++;
			});
		}
		boolean[] treated = {false};
		helper.onEachTick(() -> {
			if (!treated[0] && busy[0] == 5 && !machine.isInUse()) {
				b.seen().clear();
				hurt(b);
				QaNurseMachine1102dCompatTests.rightClick(b, nurse);
				helper.assertTrue(b.player().getHealth() == b.player().getMaxHealth(), "first free click didn't heal B: " + b.seen());
				helper.assertFalse(b.player().hasEffect(MobEffects.POISON), "first free click didn't cure B");
				helper.assertTrue(QaNurseMachine1102dCompatTests.said(b, "put your 1 Pokémon in the Healing Machine"), "first free click: " + b.seen());
				b.seen().clear();
				QaNurseMachine1102dCompatTests.rightClick(b, nurse);
				helper.assertTrue(QaNurseMachine1102dCompatTests.said(b, "Come back in 60 seconds"), "right after the free click: " + b.seen());
				treated[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(treated[0], "B not treated yet");
			helper.assertTrue(charmander.isFullHealth(), "B's team not healed yet");
		});
	}

	/**
	 * README: busy with another player's team, she treats nothing until it's free. B has no Pokémon at all: told it's
	 * busy, not healed; once free, patched up (the plain line, no machine) and the minute starts.
	 */
	//$ gametest_ticks_batch AREA '1100' '"qaNurseBusyNoTeam1103d"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qaNurseBusyNoTeam1103d")
	public void qaAPlayerWithNoTeamWaitsForTheMachineThenIsPatchedUp(GameTestHelper helper) {
		QaNurseMachine1102dCompatTests.clean(helper);
		Villager nurse = novice(helper);
		QaNurseMachine1102dCompatTests.Visitor a = QaNurseMachine1102dCompatTests.visitor(helper, "qaBusyNoTeamA");
		QaNurseMachine1102dCompatTests.Visitor b = QaNurseMachine1102dCompatTests.visitor(helper, "qaBusyNoTeamB");
		QaNurseMachine1102dCompatTests.pokemon(a, "pikachu", 1);
		HealingMachineBlockEntity machine = helper.getBlockEntity(QaNurseMachine1102dCompatTests.MACHINE);
		QaNurseMachine1102dCompatTests.rightClick(a, nurse);
		helper.assertTrue(machine.isInUse(), "setup: A's team didn't go in: " + a.seen());
		hurt(b);
		QaNurseMachine1102dCompatTests.rightClick(b, nurse);
		helper.assertTrue(QaNurseMachine1102dCompatTests.said(b, "busy with another team"), "B (no team) while busy: " + b.seen());
		untreated(helper, b, "B (no team) while busy");
		boolean[] treated = {false};
		helper.succeedWhen(() -> {
			if (!treated[0] && !machine.isInUse()) {
				b.seen().clear();
				hurt(b);
				QaNurseMachine1102dCompatTests.rightClick(b, nurse);
				String name = nurse.getDisplayName().getString();
				helper.assertTrue(b.seen().contains(name + " patched you up. Take care out there!"), "B (no team) once free: " + b.seen());
				helper.assertTrue(b.player().getHealth() == b.player().getMaxHealth(), "B (no team) not healed once free");
				helper.assertFalse(machine.isInUse(), "B's empty team started the machine");
				b.seen().clear();
				QaNurseMachine1102dCompatTests.rightClick(b, nurse);
				helper.assertTrue(QaNurseMachine1102dCompatTests.said(b, "Come back in 60 seconds"), "right after: " + b.seen());
				treated[0] = true;
			}
			helper.assertTrue(treated[0], "the machine is still busy");
		});
	}
}
