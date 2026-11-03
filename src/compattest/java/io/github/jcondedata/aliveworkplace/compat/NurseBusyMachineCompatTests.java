package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.block.entity.HealingMachineBlockEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;

/**
 * B850: while the Healing Machine is busy with another team, the nurse treats nothing and the player's minute isn't
 * spent; once it's free, the player is treated in full and the minute starts then. Setup is
 * {@link QaNurseMachine1102dCompatTests}'s.
 */
public class NurseBusyMachineCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	//$ gametest_ticks_batch AREA '1200' '"nurseBusyMachineThenFree"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "nurseBusyMachineThenFree")
	public void aBusyMachineTreatsNothingAndTheMinuteStartsWhenItIsFree(GameTestHelper helper) {
		QaNurseMachine1102dCompatTests.clean(helper);
		Villager nurse = QaNurseMachine1102dCompatTests.nurse(helper, QaNurseMachine1102dCompatTests.MACHINE, QaNurseMachine1102dCompatTests.NURSE_AT, 1);
		QaNurseMachine1102dCompatTests.Visitor a = QaNurseMachine1102dCompatTests.visitor(helper, "nurseBusyA");
		QaNurseMachine1102dCompatTests.Visitor b = QaNurseMachine1102dCompatTests.visitor(helper, "nurseBusyB");
		QaNurseMachine1102dCompatTests.pokemon(a, "pikachu", 1);
		Pokemon charmander = QaNurseMachine1102dCompatTests.pokemon(b, "charmander", 2);
		HealingMachineBlockEntity machine = helper.getBlockEntity(QaNurseMachine1102dCompatTests.MACHINE);
		QaNurseMachine1102dCompatTests.rightClick(a, nurse);
		helper.assertTrue(machine.isInUse(), "setup: A's team didn't go in: " + a.seen());

		b.player().setHealth(5);
		b.player().addEffect(new MobEffectInstance(MobEffects.POISON, 1200));
		QaNurseMachine1102dCompatTests.rightClick(b, nurse);
		helper.assertTrue(QaNurseMachine1102dCompatTests.said(b, "busy"), "B isn't told the machine is busy: " + b.seen());
		helper.assertTrue(b.player().getHealth() == 5f, "B was healed while the machine was busy: " + b.player().getHealth());
		helper.assertTrue(b.player().hasEffect(MobEffects.POISON), "B's poison was cleared while the machine was busy");
		helper.assertFalse(charmander.isFullHealth(), "B's team was healed while the machine was busy");

		boolean[] treated = {false};
		helper.succeedWhen(() -> {
			if (!treated[0] && !machine.isInUse()) {
				b.seen().clear();
				QaNurseMachine1102dCompatTests.rightClick(b, nurse);
				helper.assertTrue(b.player().getHealth() == b.player().getMaxHealth(), "once free, B wasn't healed: " + b.player().getHealth()
					+ "; B heard " + b.seen());
				helper.assertFalse(b.player().hasEffect(MobEffects.POISON), "once free, B is still poisoned");
				helper.assertTrue(QaNurseMachine1102dCompatTests.said(b, "put your 1 Pokémon in the Healing Machine"),
					"once free, B's team didn't go in: " + b.seen());
				// the minute starts now: asking again straight away is a full minute's wait, and treats nothing
				b.seen().clear();
				b.player().setHealth(5);
				QaNurseMachine1102dCompatTests.rightClick(b, nurse);
				helper.assertTrue(QaNurseMachine1102dCompatTests.said(b, "Come back in 60 seconds"), "right after B's treatment: " + b.seen());
				helper.assertTrue(b.player().getHealth() == 5f, "B was treated twice in a minute");
				treated[0] = true; // only once every check passed: a failed check fails again on the next try
			}
			helper.assertTrue(treated[0], "the machine is still busy with A's team");
			helper.assertTrue(charmander.isFullHealth(), "B's team isn't healed yet");
		});
	}
}
