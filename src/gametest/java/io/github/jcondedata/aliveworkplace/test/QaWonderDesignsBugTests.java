package io.github.jcondedata.aliveworkplace.test;

import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane-b, 2026-10-26): Wonder design checks that fail on the shipped blueprints (bugs for the builder; not registered
 * in fabric.mod.json until fixed). See {@link QaWonderDesignsGameTests} for the passing ones.
 */
public class QaWonderDesignsBugTests implements FabricGameTest {
	/**
	 * 35.17's star-chart wing: every bed's foot has its head where the foot points. The Observatory's purple bed has its
	 * foot at 28, 4, 23 facing north and its head south of it at 28, 4, 24 (also facing north): two half beds, the
	 * headboard in the middle, and a player using the foot can't sleep (the game looks for the head north of it).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaWonderBedsWhole_observatory(GameTestHelper helper) {
		List<String> wrong = QaWonderDesignsGameTests.brokenBeds(QaWonderDesignsGameTests.whole(helper, "observatory"));
		helper.assertTrue(wrong.isEmpty(), "observatory: " + wrong);
		helper.succeed();
	}

	/**
	 * 35.13: built for real, every block of the Great Library can stay where it is. A standing lantern sits on a lectern
	 * (8, 4, 22 on the lectern at 8, 3, 22): a lectern's slanted top can't hold a lantern, so it pops off as an item as
	 * soon as the lectern changes (a book put on it).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaWonderStagesStand_great_library(GameTestHelper helper) {
		QaWonderDesignsGameTests.stand(helper, "great_library");
		helper.succeed();
	}

	/**
	 * 35.19: built for real, every block of the Hanging Gardens can stay where it is. Dirt paths run under oak fences and
	 * sandstone walls (38, 6, 5; 34, 12, 37; 38, 6, 10; 29, 12, 37 ...): a path under a solid block turns to dirt when the
	 * block above is placed, so the builder's path isn't what the blueprint asks for.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaWonderStagesStand_hanging_gardens(GameTestHelper helper) {
		QaWonderDesignsGameTests.stand(helper, "hanging_gardens");
		helper.succeed();
	}

	/** 35.21: "a cloak of cut copper that weathers green over the weeks (about 250 copper blocks)", within a quarter. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaWonderColossusCopperAbout250(GameTestHelper helper) {
		Map<BlockPos, BlockState> all = QaWonderDesignsGameTests.whole(helper, "colossus");
		int copper = QaWonderDesignsGameTests.count(all, s -> QaWonderDesignsGameTests.name(s).contains("copper"));
		helper.assertTrue(Math.abs(copper - 250) <= 250 / 4, "the Colossus has " + copper + " copper blocks, not about 250");
		helper.succeed();
	}
}
