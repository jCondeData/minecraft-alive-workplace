package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaFireGameTests.AREA;
import static io.github.jcondedata.aliveworkplace.test.QaFireGameTests.ROOF_TOP;

import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import io.github.jcondedata.aliveworkplace.work.FirewardenWork;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (ROADMAP 32.16): the cases that fail against the spec today, kept apart from QaFireGameTests so they stay off
 * {@code main} until the mod is fixed.
 */
public class QaFireBugTests {
	/**
	 * A Firewarden with no name of their own (villager names off, or hired before the village named them) puts a fire
	 * out: the chronicle must not read "Firewarden the Firewarden put it out."
	 */
	//$ gametest_ticks_batch AREA '300' '"qaFireUnnamed"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaFireUnnamed")
	public void anUnnamedFirewardenIsNotCalledFirewardenTheFirewarden(GameTestHelper helper) {
		QaFireGameTests.Hut hut = QaFireGameTests.hut(helper, "unnamed", Blocks.OAK_LOG);
		ServerLevel level = hut.level();
		Villager warden = QaFireGameTests.firewarden(helper, new BlockPos(3, 2, 4));
		warden.setNoAi(true);
		helper.runAfterDelay(2, () -> {
			level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
			QaFireGameTests.fire(helper, ROOF_TOP);
			VillageFire.douse(level, helper.absolutePos(ROOF_TOP), 0, warden);
		});
		helper.runAfterDelay(120, () -> {
			String text = QaFireGameTests.fireEntry(level, hut.hall()).map(e -> e.text().getString()).orElse("nothing");
			helper.assertTrue(text.startsWith("Fire in the "), "no fire in the chronicle: " + text);
			helper.assertFalse(text.contains("Firewarden the Firewarden"), "the chronicle says: " + text);
			helper.succeed();
		});
	}

	/**
	 * Spec: "Chronicle: 'Fire in the Terrace: 18 blocks burned; Rowan the Firewarden put it out.'" A fire the mod sets on
	 * the hut's roof itself (as lightning or a chimney spark does: on top of its highest wooden block) is written up
	 * under the hut's name, not as "Fire in the village".
	 */
	//$ gametest_ticks_batch AREA '300' '"qaFireNamed"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaFireNamed")
	public void aRoofFireIsNamedAfterItsBuild(GameTestHelper helper) {
		QaFireGameTests.Hut hut = QaFireGameTests.hut(helper, "named", Blocks.OAK_LOG);
		ServerLevel level = hut.level();
		Villager warden = QaFireGameTests.firewarden(helper, new BlockPos(3, 2, 4));
		warden.setNoAi(true);
		warden.setCustomName(net.minecraft.network.chat.Component.literal("Rowan"));
		String name = io.github.jcondedata.aliveworkplace.blueprint.Blueprints.displayName(hut.id()).getString();
		helper.runAfterDelay(2, () -> {
			level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
			var build = VillageFire.buildAt(level, helper.absolutePos(new BlockPos(14, 4, 14)));
			helper.assertTrue(build.isPresent(), "the hut isn't a finished build");
			var fire = VillageFire.igniteRoof(level, build.get(), helper.absolutePos(ROOF_TOP), net.minecraft.util.RandomSource.create(1L));
			helper.assertTrue(fire.isPresent(), "the hut's roof didn't catch");
			VillageFire.douse(level, fire.get(), 0, warden);
		});
		helper.runAfterDelay(120, () -> {
			String text = QaFireGameTests.fireEntry(level, hut.hall()).map(e -> e.text().getString()).orElse("nothing");
			helper.assertTrue(text.startsWith("Fire in the " + name + ":"), "the hut is \"" + name + "\" and the chronicle says: " + text);
			helper.succeed();
		});
	}

	/**
	 * Save and reload mid-fire, the fire held still (doFireTick off, as many servers have it, so nothing new is placed):
	 * after the restart the Firewarden still runs to the fire burning on the roof and puts it out within 400 ticks.
	 */
	//$ gametest_ticks_batch AREA '700' '"qaFireRestartStill"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaFireRestartStill")
	public void aFireHeldStillAcrossARestartIsStillPutOut(GameTestHelper helper) {
		QaFireGameTests.Hut hut = QaFireGameTests.hut(helper, "restartstill", Blocks.OAK_LOG);
		ServerLevel level = hut.level();
		helper.setBlock(new BlockPos(4, 1, 6), Blocks.WATER);
		Villager warden = QaFireGameTests.firewarden(helper, new BlockPos(3, 2, 4));
		helper.runAfterDelay(2, () -> {
			level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
			QaFireGameTests.fire(helper, ROOF_TOP);
			helper.assertTrue(VillageFire.incident(level, hut.hall()).isPresent(), "the fire wasn't noticed before the restart");
		});
		helper.runAfterDelay(4, VillageFire::forget); // the server restarts: what it kept in memory is gone
		helper.runAfterDelay(404, () -> {
			helper.assertTrue(QaFireGameTests.fires(helper) == 0, "400 ticks after the restart the roof still burns (" + QaFireGameTests.fires(helper)
				+ " fire blocks; the village knows of a fire: " + VillageFire.incident(level, hut.hall()).isPresent() + "; the firewarden has "
				+ FirewardenWork.water(warden) + " buckets, at " + warden.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString() + ")");
			helper.succeed();
		});
	}
	/**
	 * Save and reload mid-fire: the server restarts (what it keeps in memory is forgotten, as on a real start) while the
	 * roof burns by the game's own fire; the Firewarden still puts it all out within 400 ticks.
	 */
	//$ gametest_ticks_batch AREA '900' '"qaFireRestart"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaFireRestart")
	public void aFireBurningAcrossARestartIsStillPutOut(GameTestHelper helper) {
		QaFireGameTests.Hut hut = QaFireGameTests.hut(helper, "restart", Blocks.OAK_LOG);
		helper.setBlock(new BlockPos(4, 1, 6), Blocks.WATER);
		Villager warden = QaFireGameTests.firewarden(helper, new BlockPos(3, 2, 4));
		long[] lit = {-1};
		long[] out = {-1};
		helper.runAfterDelay(2, () -> {
			QaFireGameTests.fire(helper, ROOF_TOP);
			QaFireGameTests.fire(helper, ROOF_TOP.offset(-1, 0, -1));
			lit[0] = helper.getTick();
		});
		helper.runAfterDelay(4, VillageFire::forget);
		helper.onEachTick(() -> {
			if (lit[0] >= 0 && out[0] < 0 && helper.getTick() > 5 && QaFireGameTests.fires(helper) == 0) {
				out[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(out[0] >= 0, "after the restart the fire still burns (" + QaFireGameTests.fires(helper) + " fire blocks; incident "
				+ VillageFire.incident(hut.level(), hut.hall()).isPresent() + "; firewarden at "
				+ warden.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString() + ")");
			helper.assertTrue(out[0] - lit[0] <= 400, "the fire took " + (out[0] - lit[0]) + " ticks to put out after the restart");
		});
	}

}
