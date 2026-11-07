package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.HallJobs;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * QA for 40.3 (Job ▾ in the hall's People table: "a jobless villager is given a free block for nothing, in one click")
 * at B803's edge: the only Stonecutter in the village is the one the jobless villager is walking to on his own (vanilla
 * holds it for him on the way). Nobody works there, so it's free for him: the Mason line offers it and one click makes
 * him the Mason at it.
 */
public class QaHallJobWalkedTo1102GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos STONECUTTER = new BlockPos(22, 2, 22);
	private static final BlockPos NEAR = new BlockPos(10, 2, 10);

	//$ gametest_ticks_batch AREA '200' '"qaHallJobWalkedTo1102"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallJobWalkedTo1102")
	public void qaAJoblessVillagerIsGivenTheBlockHeWalksTo(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		Villager pip = QaHallScreenGameTests.villager(helper, "Pip", new BlockPos(20, 2, 20));
		QaHallScreenGameTests.bed(helper, NEAR);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 0);
		ServerLevel level = helper.getLevel();
		PoiManager poi = level.getPoiManager();
		BlockPos cutter = helper.absolutePos(STONECUTTER);
		helper.runAfterDelay(3, () -> {
			// as vanilla's AcquirePoi holds a block a jobless villager walks to
			helper.assertTrue(poi.take(h -> true, (h, p) -> p.equals(cutter), cutter, 1).isPresent(), "setup: the stonecutter couldn't be held");
			pip.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, GlobalPos.of(level.dimension(), cutter));
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			QaHallScreenGameTests.jobDropdown(helper, player, menu, pip);
			List<ItemStack> lines = HallJobs.lines(menu.icon(QaHallScreenGameTests.slotOf(helper, menu, pip)));
			ItemStack mason = QaHallScreenGameTests.jobLine(helper, lines, VillagerProfession.MASON);
			helper.assertTrue(mason.getHoverName().getString().startsWith("Mason · 1 free Stonecutter"),
				"the Mason line for the villager walking to the only Stonecutter: '" + mason.getHoverName().getString() + "'");
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(pip.getVillagerData().getProfession() == VillagerProfession.MASON
				&& QaHallScreenGameTests.site(pip).filter(cutter::equals).isPresent(),
				"one click didn't make Pip the Mason at the Stonecutter he walked to: " + pip.getVillagerData().getProfession() + " at "
					+ QaHallScreenGameTests.site(pip) + "; told " + seen);
			helper.assertTrue(poi.getFreeTickets(cutter) == 0 && pip.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isEmpty(),
				"the Stonecutter isn't held once, for him (free " + poi.getFreeTickets(cutter) + ")");
			helper.succeed();
		});
	}
}
