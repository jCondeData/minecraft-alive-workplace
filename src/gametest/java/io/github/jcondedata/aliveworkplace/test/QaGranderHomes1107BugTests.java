package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (2026-11-07), fixed by B988: the grander homes' sales gate (ROADMAP 34.15, 34.16) asks for the hall
 * {@code VillageHalls.nearest} finds, a ball of the village's radius round the builder, while the village itself is a
 * circle with a height ({@code VillageHalls.contains}, B562). A builder on the village's rim up a hill (in the village,
 * but more than the radius from the hall in a straight line) sells no Townhouse and no Manor however many Artisans and
 * Burghers live there. At the default radius that is a builder 60 blocks out and 25 up; here the same shape, scaled to
 * a radius of 10 so it fits a test area.
 */
public class QaGranderHomes1107BugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * "Every builder there" (the village round the nearest hall): a builder up a hill on the village's rim, 8 blocks across
	 * from the hall (inside its circle of 10) and 7 up (well inside its height), is in the village like the Artisan beside
	 * the hall, so he sells the Townhouse as his neighbour on the ground does. (B562 made the village a circle with a
	 * height, not a ball: a spot on the rim up a hill is in the village though more than the radius away in a straight
	 * line.)
	 */
	//$ gametest_batch AREA '"qaGrand1107BugRimUpAHill"'
	@GameTest(template = AREA, batch = "qaGrand1107BugRimUpAHill")
	public void bugABuilderOnTheVillagesRimUpAHillSellsTheTownhouse(GameTestHelper helper) {
		BlockPos hallAt = new BlockPos(4, 2, 4);
		BlockPos hall = QaGranderHomes1107GameTests.village(helper, hallAt, 10);
		QaGranderHomes1107GameTests.classed(helper, new BlockPos(6, 2, 6), "Ann", "artisan");
		Villager ground = QaGranderHomes1107GameTests.builder(helper, new BlockPos(12, 2, 4), "Hob", 2);
		helper.setBlock(new BlockPos(12, 8, 6), Blocks.STONE);
		Villager hill = QaGranderHomes1107GameTests.builder(helper, new BlockPos(12, 9, 6), "Hilda", 2);
		BlockPos up = helper.absolutePos(new BlockPos(12, 9, 6));
		helper.assertTrue(VillageHalls.contains(hall, up) && VillageHalls.containing(helper.getLevel(), up).equals(java.util.Optional.of(hall)),
			"the test's spot isn't in the village: " + up);
		ServerPlayer player = QaGranderHomes1107GameTests.player(helper);
		try {
			QaGranderHomes1107GameTests.openTrades(helper, ground, player);
			QaGranderHomes1107GameTests.openTrades(helper, hill, player);
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.assertTrue(QaGranderHomes1107GameTests.sold(ground, StarterBlueprints.TOWNHOUSE) == 1, "the builder on the ground doesn't sell the Townhouse: " + QaGranderHomes1107GameTests.blueprintsSold(ground));
		helper.assertTrue(QaGranderHomes1107GameTests.sold(hill, StarterBlueprints.TOWNHOUSE) == 1, "the builder up the hill on the village's rim (8 across, 7 up, in the village) doesn't sell "
			+ "the Townhouse though an Artisan household lives by the hall: " + QaGranderHomes1107GameTests.blueprintsSold(hill));
		helper.succeed();
	}

	/**
	 * B988's other side: a builder just outside the village's circle (8 and 7 across from the hall, 10.6 blocks, outside
	 * its circle of 10), on the ground or up a hill, sells no Townhouse and no Manor though an Artisan and a Burgher live by
	 * the hall: finding the hall by the village's own shape mustn't stretch the village.
	 */
	//$ gametest_batch AREA '"qaGrand1107BugJustOutside"'
	@GameTest(template = AREA, batch = "qaGrand1107BugJustOutside")
	public void aBuilderJustOutsideTheVillageSellsNoTownhouseOrManor(GameTestHelper helper) {
		BlockPos hallAt = new BlockPos(4, 2, 4);
		QaGranderHomes1107GameTests.village(helper, hallAt, 10);
		QaGranderHomes1107GameTests.classed(helper, new BlockPos(6, 2, 6), "Ann", "artisan");
		QaGranderHomes1107GameTests.classed(helper, new BlockPos(6, 2, 4), "Bertram", "burgher");
		Villager ground = QaGranderHomes1107GameTests.builder(helper, new BlockPos(12, 2, 11), "Hob", 2);
		helper.setBlock(new BlockPos(11, 8, 12), Blocks.STONE);
		Villager hill = QaGranderHomes1107GameTests.builder(helper, new BlockPos(11, 9, 12), "Hilda", 2);
		for (BlockPos at : new BlockPos[] {new BlockPos(12, 2, 11), new BlockPos(11, 9, 12)}) {
			BlockPos abs = helper.absolutePos(at);
			helper.assertTrue(VillageHalls.containing(helper.getLevel(), abs).isEmpty(), "the test's spot is in the village: " + abs);
		}
		ServerPlayer player = QaGranderHomes1107GameTests.player(helper);
		try {
			QaGranderHomes1107GameTests.openTrades(helper, ground, player);
			QaGranderHomes1107GameTests.openTrades(helper, hill, player);
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		for (Villager b : new Villager[] {ground, hill}) {
			helper.assertTrue(QaGranderHomes1107GameTests.sold(b, StarterBlueprints.TOWNHOUSE) == 0 && QaGranderHomes1107GameTests.sold(b, StarterBlueprints.MANOR) == 0,
				b.getName().getString() + ", just outside the village, sells a grander home: " + QaGranderHomes1107GameTests.blueprintsSold(b));
		}
		helper.succeed();
	}

}
