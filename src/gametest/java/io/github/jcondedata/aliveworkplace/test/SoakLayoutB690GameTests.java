package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.command.Soak;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * B690: the builder soak (23.1) laid each builder's builds a fixed 18 blocks apart in columns 70 apart, so the 19-wide
 * guildhall and manor ran into their neighbours' plots and the soak counted those holes against the builders. Every
 * plot, in both layouts, must now stand clear of every other with ground between them.
 */
public class SoakLayoutB690GameTests implements FabricGameTest {
	/** The plain layout: no two plots touch, and every starter build has one. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void soakPlotsNeverOverlap(GameTestHelper helper) {
		check(helper, false);
		helper.succeed();
	}

	/** The split layout (storehouses west of the benches): the same. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void splitSoakPlotsNeverOverlap(GameTestHelper helper) {
		check(helper, true);
		helper.succeed();
	}

	private static void check(GameTestHelper helper, boolean split) {
		List<BoundingBox> boxes = Soak.footprints(split);
		if (boxes.size() != Soak.starterSet().size()) {
			helper.fail(boxes.size() + " plots for " + Soak.starterSet().size() + " starter builds (split " + split + ")");
		}
		for (int i = 0; i < boxes.size(); i++) {
			// One block of ground all round, so two builds never share a wall column.
			BoundingBox grown = boxes.get(i).inflatedBy(1);
			for (int j = i + 1; j < boxes.size(); j++) {
				if (grown.intersects(boxes.get(j))) {
					helper.fail("soak plot " + i + " " + boxes.get(i) + " runs into plot " + j + " " + boxes.get(j)
						+ " (split " + split + ")");
				}
			}
		}
	}
}
