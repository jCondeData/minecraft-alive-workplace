package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.Plots;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.BitSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * Failing QA tests: the mod disagrees with the spec. NOT registered in fabric.mod.json; register one to reproduce.
 */
public class QaStewardBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 14);
	private static final ResourceLocation WELL = AliveWorkplace.id("well");

	/**
	 * 27.7: "never the same blueprint with the same mirroring within 24 blocks". A finished Well whose centre is 25 blocks
	 * from the candidate's (and one 17, 17 off: 24.04) is refused as SAME_NEARBY: Plots.Build keeps an integer centre
	 * ((min + max) / 2, half a block off the candidate's (min + max + 1) / 2.0) and the check truncates the difference with
	 * a (long) cast, so the real reach is up to about 25.5 blocks.
	 */
	//$ gametest_ticks_batch AREA '20' '"qaBugSameRange"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaBugSameRange")
	public void aWellTwentyFiveBlocksOffIsNotWithinTwentyFour(GameTestHelper helper) {
		Leftovers.clear(helper);
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 12; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(2, 2, 10), ModBlocks.BLUEPRINT_TABLE);
		BlockPos hall = helper.absolutePos(HALL);
		BitSet cells = new BitSet();
		for (int x = 6; x <= 29; x++) {
			for (int z = 2; z <= 25; z++) {
				cells.set(CityPlan.cellAt(hall, helper.absolutePos(new BlockPos(x, 2, z))));
			}
		}
		CityPlan plan = CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells);
		BuildSiteManager manager = BuildSiteManager.get(helper.getLevel());
		// the candidate Well at (11, 13): box x 8..14, z 10..16, centre (11.5, 13.5)
		for (int[] c : new int[][] {{33, 10}, {25, 27}}) { // centres (36.5, 13.5): 25 off; (28.5, 30.5): 17, 17 = 24.04 off
			BlueprintData.Placement p = new BlueprintData.Placement(Ids.of(helper.getLevel().dimension()), helper.absolutePos(new BlockPos(c[0], 2, c[1])),
				Rotation.NONE, Mirror.NONE);
			manager.recordFinished(WELL, p, UUID.randomUUID());
			Leftovers.after(helper, () -> manager.forgetFinished(p));
			Plots.Verdict v = Plots.check(helper.getLevel(), hall, plan, "homes", WELL, helper.absolutePos(new BlockPos(11, 2, 13)), Rotation.COUNTERCLOCKWISE_90, Mirror.NONE);
			helper.assertTrue(v.plot().isPresent(), "a Well with origin (" + c[0] + ", " + c[1] + "), more than 24 blocks off, refused: " + v.reason());
			manager.forgetFinished(p);
		}
		helper.succeed();
	}

	/** 27.6's food rule with 1 meal short reads "The store is 1 meals short": the why line has no singular. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void oneMealShortReadsInTheSingular(GameTestHelper helper) {
		String one = QaStewardRulesGameTests.english(Words.countedFirst("steward.aliveworkplace.why.food", 1L));
		helper.assertTrue(!one.contains("1 meals"), "'" + one + "'");
		helper.succeed();
	}
}
