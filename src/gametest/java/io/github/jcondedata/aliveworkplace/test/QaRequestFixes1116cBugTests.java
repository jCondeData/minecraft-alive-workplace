package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane-c-1116-1852): a failure found while testing B1316, kept off main until fixed (register it in
 * src/gametest/resources/fabric.mod.json with the fix). The wiki (personal-requests.md, "A chapel wedding"): "The Chapel
 * finished, they marry in it at once ... and the chronicle says 'Dara and Odo were married in the new Chapel, and the
 * whole village sat down to a feast'". The chronicle also gets "Dara and Odo were married at the bell" for the same
 * wedding (RequestErrands marries them through Couples.wed, which always records the bell line), so the village's
 * history says they married twice, once at a bell they never stood at.
 */
public class QaRequestFixes1116cBugTests implements FabricGameTest {
	static final String AREA = PersonalRequestGameTests.AREA;
	static final BlockPos HALL = PersonalRequestGameTests.HALL;

	/** Married in the Chapel through the request: the chronicle has the Chapel wedding, and no "married at the bell" for it. */
	//$ gametest_ticks_batch AREA '80' '"qa1116cBugChapelChronicle"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1116cBugChapelChronicle")
	public void aChapelWeddingIsntAlsoChronicledAtTheBell(GameTestHelper helper) {
		Leftovers.clear(helper);
		PersonalRequestGameTests.village(helper, () -> {
			Leftovers.finished(helper);
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager[] couple = QaRequestFixes1116cGameTests.courting(helper, 3);
			Villager dara = couple[0];
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			PersonalRequestGameTests.accepted(helper, "chapel_wedding", dara, me);
			QaRequestFixes1116cGameTests.chapelFinished(helper);
			PersonalRequests.look(level, hall, PersonalRequestGameTests.hallEntity(helper), false);
			helper.assertTrue(PersonalRequests.of(dara) == null && Couples.partner(dara).married(), "setup: not married in the Chapel");
			List<String> weddings = PersonalErrandGameTests.chronicle(level, hall, Chronicle.Kind.WEDDING);
			helper.assertTrue(weddings.equals(List.of("Dara and Odo were married in the new Chapel, and the whole village sat down to a feast")),
				"the chronicle's weddings for one Chapel wedding: " + weddings);
			helper.succeed();
		});
	}
}
