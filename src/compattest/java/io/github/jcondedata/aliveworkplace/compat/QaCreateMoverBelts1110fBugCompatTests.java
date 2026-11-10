package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverBelts1110fCompatTests.AREA;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverBelts1110fCompatTests.lay;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverBelts1110fCompatTests.moved;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.held;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.loose;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.onto;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setupAround;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.tidy;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.work;

import io.github.jcondedata.aliveworkplace.compat.QaCreateMoverBelts1110fCompatTests.Belt;
import io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.Setup;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane E, 2026-11-10, run 1012), failing on main: the Mover (45.3, "nothing dropped, nothing lost") moving a
 * building with a Create belt that runs out through its wall: the segment outside is turned into a bare shaft and the
 * item on it is lost. Helpers and the passing belt tests: QaCreateMoverBelts1110fCompatTests.
 */
public class QaCreateMoverBelts1110fBugCompatTests implements FabricGameTest {
	/**
	 * A belt that runs out of the building through a gap in its east wall (five segments: three inside, the one in the
	 * wall, one outside; laid after the house, its controller inside), an iron ingot on its start and a gold ingot on
	 * its end outside. The Mover moves the building, not what stands outside it (45.3: nothing dropped, nothing lost):
	 * the segment outside the walls still stands where it was, and neither ingot is lost (on a belt or loose).
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_e1110f_belt_out"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1110f_belt_out")
	public void aBeltRunningOutThroughTheWallLeavesItsOutsideSegmentStanding(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		Setup s = setupAround(helper, "belt_out");
		Belt out = new Belt(List.of(new BlockPos(5, 3, 5), new BlockPos(6, 3, 5), new BlockPos(7, 3, 5), new BlockPos(8, 3, 5),
			new BlockPos(9, 3, 5)), "east", "horizontal");
		lay(helper, out);
		helper.runAfterDelay(1, () -> {
			int on = (onto(level, helper.absolutePos(out.segments().get(0)), Items.IRON_INGOT) ? 1 : 0)
				+ (onto(level, helper.absolutePos(out.segments().get(4)), Items.GOLD_INGOT) ? 1 : 0);
			helper.assertTrue(on == 2, "setup: the belt took " + on + " of 2 ingots");
		});
		helper.runAfterDelay(3, () -> {
			MoveSite site = work(level, s, x -> false);
			MoveSite.Stage end = site.stage();
			int skipped = site.skipped();
			helper.runAfterDelay(5, () -> {
				BlockPos outside = helper.absolutePos(out.segments().get(4));
				BlockState there = level.getBlockState(outside);
				long gold = held(level, outside, Items.GOLD_INGOT);
				long iron = held(level, outside, Items.IRON_INGOT);
				List<String> inside = new ArrayList<>();
				for (int i = 0; i < 4; i++) {
					BlockPos at = moved(s, out.segments().get(i));
					BlockState st = level.getBlockState(at);
					gold += held(level, at, Items.GOLD_INGOT);
					iron += held(level, at, Items.IRON_INGOT);
					inside.add(i + ": " + (st.is(block("create:belt")) ? "belt" : st.toString()));
				}
				Map<String, Integer> looseNow = loose(helper);
				gold += looseNow.getOrDefault("minecraft:gold_ingot", 0);
				iron += looseNow.getOrDefault("minecraft:iron_ingot", 0);
				try {
					helper.assertTrue(end == MoveSite.Stage.DONE, "the move ended " + end + ", skipped " + skipped);
					helper.assertTrue(there.is(block("create:belt")) && gold == 1 && iron == 1, "after the move the belt segment outside the "
						+ "building's wall is " + there + " (it stood there before and isn't the building's), the gold ingot that was on it is "
						+ (gold == 1 ? "kept" : "found " + gold + " times") + ", the iron ingot " + (iron == 1 ? "kept" : "found " + iron + " times")
						+ "; the belt's four inside segments at the new spot: " + inside + ", skipped " + skipped + ", loose " + looseNow);
				} finally {
					tidy(helper, s);
				}
				helper.succeed();
			});
		});
	}
}
