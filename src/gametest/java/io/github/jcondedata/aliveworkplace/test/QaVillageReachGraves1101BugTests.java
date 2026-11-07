package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.grave.Graves;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * QA, 2026-11-01: findings from {@link QaVillageReachGraves1101GameTests} that fail on the mod as it is (B222, B562);
 * not registered in fabric.mod.json until fixed.
 */
public class QaVillageReachGraves1101BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * B222 ("the Undertaker moves each new grave to the village's Graveyard"): two villages side by side. This village's
	 * Undertaker lays its own street grave in its Graveyard; a villager of the next village who died inside that village
	 * (outside this one, though in the Undertaker's 32-block reach) isn't laid in this village's Graveyard.
	 */
	//$ gametest_ticks_batch HUGE '2600' '"qa1101GravesNeighbour"'
	@GameTest(template = HUGE, timeoutTicks = 2600, batch = "qa1101GravesNeighbour")
	public void aGraveInTheNextVillageIsNotLaidInThisVillagesGraveyard(GameTestHelper helper) {
		QaVillageReachGraves1101GameTests.graveVillage(helper, true);
		ServerLevel level = helper.getLevel();
		BlockPos hallB = new BlockPos(26, 2, 26);
		helper.setBlock(hallB, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		BlockPos ownAt = new BlockPos(8, 2, 3);
		BlockPos theirsAt = new BlockPos(24, 2, 18);
		BlockPos yard = helper.absolutePos(QaVillageReachGraves1101GameTests.G_YARD);
		helper.assertTrue(VillageHalls.contains(helper.absolutePos(QaVillageReachGraves1101GameTests.G_HALL), helper.absolutePos(ownAt)),
			"setup: the own grave isn't in this village");
		helper.assertTrue(VillageHalls.contains(helper.absolutePos(hallB), helper.absolutePos(theirsAt))
			&& !VillageHalls.contains(helper.absolutePos(QaVillageReachGraves1101GameTests.G_HALL), helper.absolutePos(theirsAt)),
			"setup: the other grave isn't in the next village only");
		BlockPos own = QaVillageReachGraves1101GameTests.dies(helper, ownAt, "Ada", 0);
		BlockPos theirs = QaVillageReachGraves1101GameTests.dies(helper, theirsAt, "Nell", 2);
		QaVillageReachGraves1101GameTests.undertaker(helper);
		helper.runAfterDelay(2400, () -> {
			helper.assertTrue("Ada".equals(QaVillageReachGraves1101GameTests.nameAt(level, QaVillageReachGraves1101GameTests.slot(helper, 1)))
				&& !level.getBlockState(own).is(ModBlocks.GRAVE), "control: this village's own street grave wasn't laid in its Graveyard");
			List<String> inYard = new ArrayList<>();
			for (BlockPos g : Graves.near(level, helper.absolutePos(new BlockPos(15, 2, 15)), 30)) {
				if (g.getX() >= yard.getX() && g.getX() < yard.getX() + 13 && g.getZ() >= yard.getZ() && g.getZ() < yard.getZ() + 13) {
					inYard.add(QaVillageReachGraves1101GameTests.nameAt(level, g));
				}
			}
			helper.assertTrue(!inYard.contains("Nell"), "the next village's villager Nell, who died in her own village, was laid in this "
				+ "village's Graveyard (her grave " + (level.getBlockState(theirs).is(ModBlocks.GRAVE) ? "is also still" : "is no longer") + " where she fell): " + inYard);
			helper.succeed();
		});
	}

	/**
	 * B562 ("any death inside the village (contains) is the village's"; the bug named the chronicle line): Mira is killed on
	 * the village's rim up a hill (14 out and 10 up in a 16-block village). Her death is the village's, so the hall's
	 * chronicle has it, with her job: "...: the village's Farmer, at an age nobody had counted".
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1101RimDeathChronicle"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1101RimDeathChronicle")
	public void aDeathOnTheRimUpAHillIsWrittenInTheHallsChronicle(GameTestHelper helper) {
		BlockPos h = QaVillageReachGraves1101GameTests.mourningVillage(helper, QaVillageReachGraves1101GameTests.M_HALL, 16);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			QaVillageReachGraves1101GameTests.ready(helper, h);
			BlockPos rim = h.offset(14, 10, 0);
			for (int y = h.getY(); y < rim.getY(); y++) {
				level.setBlockAndUpdate(new BlockPos(rim.getX(), y, rim.getZ()), Blocks.STONE.defaultBlockState());
			}
			QaVillageCircleGameTests.staged(helper, h, List.of(rim), List.of());
			helper.assertTrue(rim.distSqr(h) > 16.0 * 16.0, "setup: the rim is within RADIUS in a straight line");
			VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(h);
			int before = hall.chronicle().size();
			Villager mira = QaVillageReachGraves1101GameTests.farmerAt(helper, rim, "Mira");
			mira.kill();
			List<String> deaths = hall.chronicle().stream().skip(before).filter(e -> e.kind() == Chronicle.Kind.DEATH).map(e -> e.text().getString()).toList();
			helper.assertTrue(deaths.stream().anyMatch(l -> l.startsWith("Mira") && l.contains("the village's Farmer")),
				"the hall's chronicle doesn't record Mira's death in the village: " + deaths + " (all new lines: "
					+ hall.chronicle().stream().skip(before).map(e -> e.text().getString()).toList() + ")");
			helper.succeed();
		});
	}
}
