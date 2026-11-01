package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * B779 and B780, the edges of the fixes (the bugs themselves: QaVillageReachGraves1101BugTests). B779: an Undertaker
 * leaves a grave that lies in another village, but still moves one that lies in no village, and one whose village's hall
 * is gone. B780: the chronicle line is written for a death inside the village's circle and not for one a block past it.
 */
public class B779B780GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** The relative spot that is in no village (17 blocks across from the 12-block village's hall), within the Undertaker's reach. */
	private static final BlockPos WILDS = new BlockPos(20, 2, 4);

	/** A grave out in the wilds, in no village, near this village's Undertaker is laid in the village's Graveyard as before. */
	//$ gametest_ticks_batch HUGE '2000' '"b779Wilds"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "b779Wilds")
	public void aGraveInNoVillageIsStillLaidInTheGraveyard(GameTestHelper helper) {
		QaVillageReachGraves1101GameTests.graveVillage(helper, true);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(VillageHalls.containing(level, helper.absolutePos(WILDS)).isEmpty(), "setup: the wilds are in a village");
		BlockPos fell = QaVillageReachGraves1101GameTests.dies(helper, WILDS, "Wren", 0);
		QaVillageReachGraves1101GameTests.undertaker(helper);
		helper.succeedWhen(() -> helper.assertTrue("Wren".equals(QaVillageReachGraves1101GameTests.nameAt(level, QaVillageReachGraves1101GameTests.slot(helper, 1)))
			&& !level.getBlockState(fell).is(ModBlocks.GRAVE), "Wren's grave, in no village, wasn't laid in the Graveyard"));
	}

	/**
	 * Nell's grave lies in the next village, so it's left there; once that village's hall is broken her grave is in no
	 * village, and this village's Undertaker lays it in his Graveyard.
	 */
	//$ gametest_ticks_batch HUGE '2600' '"b779HallGone"'
	@GameTest(template = HUGE, timeoutTicks = 2600, batch = "b779HallGone")
	public void aGraveIsMovedOnceTheNextVillagesHallIsGone(GameTestHelper helper) {
		QaVillageReachGraves1101GameTests.graveVillage(helper, true);
		ServerLevel level = helper.getLevel();
		BlockPos hallB = new BlockPos(26, 2, 26);
		helper.setBlock(hallB, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		BlockPos at = new BlockPos(24, 2, 18);
		helper.assertTrue(VillageHalls.containing(level, helper.absolutePos(at)).equals(java.util.Optional.of(helper.absolutePos(hallB))),
			"setup: Nell's spot isn't in the next village");
		BlockPos fell = QaVillageReachGraves1101GameTests.dies(helper, at, "Nell", 2);
		QaVillageReachGraves1101GameTests.undertaker(helper);
		helper.runAfterDelay(600, () -> {
			helper.assertTrue(level.getBlockState(fell).is(ModBlocks.GRAVE), "Nell's grave was moved out of her own village");
			helper.setBlock(hallB, Blocks.AIR);
			VillageHalls.forgetNearest();
			helper.succeedWhen(() -> helper.assertTrue("Nell".equals(QaVillageReachGraves1101GameTests.nameAt(level, QaVillageReachGraves1101GameTests.slot(helper, 1)))
				&& !level.getBlockState(fell).is(ModBlocks.GRAVE), "with the next village's hall gone, Nell's grave wasn't laid in the Graveyard"));
		});
	}

	/**
	 * In a 16-block village, Mira killed exactly 16 out (on the rim) is written in the hall's chronicle; Tam killed 17 out
	 * (in no village) isn't.
	 */
	//$ gametest_ticks_batch HUGE '100' '"b780Edge"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "b780Edge")
	public void aDeathOnTheRimIsWrittenAndOneJustPastItIsNot(GameTestHelper helper) {
		BlockPos h = QaVillageReachGraves1101GameTests.mourningVillage(helper, QaVillageReachGraves1101GameTests.M_HALL, 16);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			QaVillageReachGraves1101GameTests.ready(helper, h);
			BlockPos rim = h.offset(16, 0, 0);
			BlockPos past = h.offset(0, 0, -17);
			QaVillageCircleGameTests.staged(helper, h, List.of(rim), List.of(past));
			helper.assertTrue(VillageHalls.containing(level, past).isEmpty(), "setup: the spot past the rim is in a village");
			VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(h);
			int before = hall.chronicle().size();
			QaVillageReachGraves1101GameTests.farmerAt(helper, past, "Tam").kill();
			QaVillageReachGraves1101GameTests.farmerAt(helper, rim, "Mira").kill();
			List<String> deaths = hall.chronicle().stream().skip(before).filter(e -> e.kind() == Chronicle.Kind.DEATH).map(e -> e.text().getString()).toList();
			helper.assertTrue(deaths.stream().anyMatch(l -> l.startsWith("Mira")), "Mira's death on the rim wasn't written: " + deaths);
			helper.assertTrue(deaths.stream().noneMatch(l -> l.startsWith("Tam")), "Tam's death outside the village was written: " + deaths);
			helper.succeed();
		});
	}
}
