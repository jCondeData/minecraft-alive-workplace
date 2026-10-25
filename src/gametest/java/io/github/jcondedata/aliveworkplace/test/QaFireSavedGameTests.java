package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaFireGameTests.AREA;
import static io.github.jcondedata.aliveworkplace.test.QaFireGameTests.ROOF_TOP;

import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane E, saves; B392 / ROADMAP 32.16): a village fire kept over a save. The case the fix's own tests leave out: the
 * fire went out while the server was down (the roof block was replaced, as rain or a player at a backup would), so the
 * saved fire comes back with nothing burning. It must close by itself with its chronicle line, not stay open for good.
 */
public class QaFireSavedGameTests {

	/**
	 * A fire saved with one block burned; while the server is down the fire block is gone. After the load the fire
	 * closes: the chronicle reads "Fire in the [hut]: 1 block burned before it burned out." and nothing stays saved.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaFireOutWhileDown"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaFireOutWhileDown")
	public void aFireThatWentOutWhileTheServerWasDownClosesAfterTheLoad(GameTestHelper helper) {
		QaFireGameTests.Hut hut = QaFireGameTests.hut(helper, "outwhiledown", Blocks.OAK_LOG);
		ServerLevel level = hut.level();
		String name = io.github.jcondedata.aliveworkplace.blueprint.Blueprints.displayName(hut.id()).getString();
		helper.runAfterDelay(2, () -> {
			level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
			QaFireGameTests.fire(helper, ROOF_TOP);
			VillageFire.burned(level, helper.absolutePos(ROOF_TOP.below()));
			helper.assertTrue(VillageFire.incident(level, hut.hall()).isPresent(), "the fire wasn't noticed before the save");
			ThreatData.get(level).roundTrip(level.registryAccess()); // saved
			VillageFire.forget(); // the server stops
			level.removeBlock(helper.absolutePos(ROOF_TOP), false); // out while the server was down
			ThreatData.get(level).roundTrip(level.registryAccess()); // and loaded again
			VillageFire.forget();
			helper.assertTrue(VillageFire.incident(level, hut.hall()).isPresent(), "the saved fire didn't come back with the load");
		});
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(VillageFire.incident(level, hut.hall()).isEmpty(), "the fire that went out while the server was down is still open "
				+ "200 ticks after the load (" + VillageFire.incident(level, hut.hall()).map(i -> i.fires() + " fire blocks known").orElse("") + ")");
			helper.assertTrue(ThreatData.get(level).fires().get(hut.hall()) == null, "the fire is over but still saved");
			String text = QaFireGameTests.fireEntry(level, hut.hall()).map(e -> e.text().getString()).orElse("nothing");
			String want = "Fire in the " + name + ": 1 block burned before it burned out.";
			helper.assertTrue(text.equals(want), "expected \"" + want + "\", the chronicle says: " + text);
			helper.succeed();
		});
	}
}
