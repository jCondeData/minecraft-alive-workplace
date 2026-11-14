package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane D, 2026-11-13): what changes on a village's attack clock is saved. Don't break saves (CLAUDE.md): a change
 * the world's saved data isn't told about is lost at the next restart unless something else happens to change it too.
 * A local mutation run of ThreatData (lane-d-1113-2207, 8 mutants against the 46 warning, gate and call-off classes) let
 * two such deletions survive: B1155's call-off of one culture's attacks (its lair broken up), whose attack would come
 * back after a restart, and the portcullis bars braced ahead of a warned day (32.14), which would come back up or stay
 * down wrongly. Each test uses a hall of its own far off the test areas and forgets its clock after.
 */
public class QaThreatClockSaves1113dGameTests implements FabricGameTest {
	private static final BlockPos HALL_CULTURE = new BlockPos(29_000_011, 70, 29_000_011);
	private static final BlockPos HALL_BARS = new BlockPos(29_000_031, 70, 29_000_031);

	/** B1155: calling off one culture's attack (its lair broken up) marks the threat data to be saved. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aCultureCalledOffIsSaved(GameTestHelper helper) {
		ThreatData data = ThreatData.get(helper.getLevel());
		data.forgetClock(HALL_CULTURE);
		data.rolled(HALL_CULTURE, 4, new ThreatData.Attack(5, Threats.BANDITS, 0.0, 6000));
		data.rolled(HALL_CULTURE, 4, new ThreatData.Attack(6, Threats.MONSTERS, 0.0, 18000));
		data.warned(HALL_CULTURE, 5);
		data.setDirty(false);
		data.callOff(HALL_CULTURE, Threats.BANDITS);
		boolean dirty = data.isDirty();
		List<ThreatData.Attack> left = data.attacks(HALL_CULTURE);
		long warned = data.warned(HALL_CULTURE);
		data.forgetClock(HALL_CULTURE);
		helper.assertTrue(left.size() == 1 && left.get(0).culture().equals(Threats.MONSTERS), "setup: the bandits' attack wasn't called off alone: " + left);
		helper.assertTrue(warned == -1, "setup: the warning of the bandits' day stayed: " + warned);
		helper.assertTrue(dirty, "the bandits' attack was called off but the threat data wasn't marked to be saved: after a restart it comes back");
		helper.succeed();
	}

	/** Control and boundary: calling off a culture with no attack on the clock changes nothing and saves nothing. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aCultureWithNoAttackCalledOffChangesNothing(GameTestHelper helper) {
		ThreatData data = ThreatData.get(helper.getLevel());
		data.forgetClock(HALL_CULTURE.east(4));
		data.rolled(HALL_CULTURE.east(4), 4, new ThreatData.Attack(5, Threats.MONSTERS, 0.0, 18000));
		data.warned(HALL_CULTURE.east(4), 5);
		data.callOff(HALL_CULTURE.east(4), Threats.BANDITS);
		List<ThreatData.Attack> left = data.attacks(HALL_CULTURE.east(4));
		long warned = data.warned(HALL_CULTURE.east(4));
		data.forgetClock(HALL_CULTURE.east(4));
		helper.assertTrue(left.size() == 1, "calling off the bandits took the monsters' attack too: " + left);
		helper.assertTrue(warned == 5, "calling off the bandits cleared the monsters' warning: " + warned);
		helper.succeed();
	}

	/** 32.14: the portcullis bars dropped ahead of a warned attack, and drawn up again, mark the threat data to be saved. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void braceBarsAreSaved(GameTestHelper helper) {
		ThreatData data = ThreatData.get(helper.getLevel());
		data.forgetClock(HALL_BARS);
		data.setDirty(false);
		data.braced(HALL_BARS, List.of(HALL_BARS.north(20), HALL_BARS.north(20).east()));
		boolean droppedDirty = data.isDirty();
		List<BlockPos> down = data.braced(HALL_BARS);
		data.setDirty(false);
		data.braced(HALL_BARS, List.of());
		boolean upDirty = data.isDirty();
		List<BlockPos> after = data.braced(HALL_BARS);
		data.forgetClock(HALL_BARS);
		helper.assertTrue(down.size() == 2, "setup: the two bars weren't kept: " + down);
		helper.assertTrue(droppedDirty, "two portcullis bars braced for a warned attack weren't marked to be saved");
		helper.assertTrue(after.isEmpty(), "the bars drawn up are still listed: " + after);
		helper.assertTrue(upDirty, "the bars drawn up after the warned day weren't marked to be saved");
		helper.succeed();
	}
}
