package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.NobleBalls;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (lane B), from the grander-homes page and 34.16 ("with no ballroom the ball gathers in a Manor I or II's hall (its
 * middle)"; the ballroom "turned and mirrored with the build") and 23.8 (a build may be placed mirrored; a Steward's
 * street mirrors its houses): wherever a Manor stands and however it was placed, the ball's venue is inside it.
 */
public class QaMirroredManorBallGameTests {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");

	//$ gametest_batch AREA '"qaMirroredManorBall"'
	@GameTest(template = AREA, batch = "qaMirroredManorBall")
	public void theBallGathersInsideAManorPlacedAnyWay(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlockPos hall = helper.absolutePos(new BlockPos(2, 2, 2));
		BlockPos origin = helper.absolutePos(new BlockPos(16, 2, 16));
		List<String> outside = new ArrayList<>();
		int checked = 0;
		for (StarterBlueprints.Entry manor : List.of(StarterBlueprints.MANOR, StarterBlueprints.MANOR_2, StarterBlueprints.MANOR_3)) {
			for (Mirror mirror : List.of(Mirror.NONE, Mirror.FRONT_BACK)) {
				for (Rotation rotation : Rotation.values()) {
					BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, rotation, mirror);
					sites.recordFinished(manor.id(), placement, UUID.randomUUID());
					try {
						BlockPos venue = NobleBalls.venue(level, hall);
						BoundingBox box = BlueprintOutline.bounds(placement, manor.size());
						if (!box.isInside(venue)) {
							outside.add(manor.id().getPath() + " " + rotation + (mirror == Mirror.NONE ? "" : " mirrored") + ": the venue "
								+ venue.subtract(origin).toShortString() + " is outside the Manor " + box);
						}
						checked++;
					} finally {
						sites.forgetFinished(placement);
					}
				}
			}
		}
		LOGGER.info("qa mirrored manor ball: {} Manor placements; venues outside them: {}", checked, outside);
		helper.assertTrue(outside.isEmpty(), "the ball gathers outside the Manor: " + outside);
		helper.succeed();
	}
}
