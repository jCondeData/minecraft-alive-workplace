package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

/**
 * Failing QA tests for 34.8 (lane-c-1024-1553), NOT registered in fabric.mod.json: each fails on main because the mod is
 * wrong. The builder registers each with its fix.
 *
 * Families.round matches a parent's job only against VillageHalls.freeStations' one profession per block (the block's
 * first, vanilla job: a lectern is a Librarian's), so a grown child never takes up a parent's trade that shares a block
 * (Scholar, Teacher, Tinkerer, Chef, Nurse, Undertaker, Jeweller, Netherworker, Printer, Innkeeper...), whatever their
 * class; only the Shopkeeper (the counter's first job) is ever inherited. 34.8 names this as one of the four ways a gated
 * job is taken.
 */
public class QaClassesBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);

	/** A grown child takes up a Scholar parent's trade only as a Burgher: an Artisan child stays jobless by the free lectern. */
	//$ gametest_ticks_batch AREA '60' '"qaBugClassFamilyScholar"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaBugClassFamilyScholar")
	public void bugAGrownBurgherChildNeverTakesUpAScholarParentsTrade(GameTestHelper helper) {
		QaClassesGameTests.setUpForBugs(helper);
		BlockPos lectern = new BlockPos(8, 2, 4);
		helper.setBlock(lectern, Blocks.LECTERN);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			helper.assertTrue(VillageHalls.freeStations(level, hall).stream().anyMatch(f -> f.pos().equals(helper.absolutePos(lectern))),
				"setup: the lectern isn't a free station of the hall");
			Families.Parents parents = new Families.Parents(Component.literal("Mara"), Component.literal("Tom"), "aliveworkplace:scholar", "", true, false, false);
			Villager art = QaClassesGameTests.spawn(helper, new BlockPos(4, 2, 10), "Art", "artisan");
			ModAttachments.PARENTS.set(art, parents);
			Families.round(level, hall, art, RandomSource.create(1));
			helper.assertTrue(art.getVillagerData().getProfession() == VillagerProfession.NONE && art.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(),
				"the Artisan child took the Scholar's trade: " + QaClassesGameTests.jobIdOf(art) + " " + art.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			Villager bur = QaClassesGameTests.spawn(helper, new BlockPos(6, 2, 10), "Bur", "burgher");
			ModAttachments.PARENTS.set(bur, parents);
			Families.round(level, hall, bur, RandomSource.create(1));
			helper.assertTrue(bur.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(helper.absolutePos(lectern)::equals).isPresent(),
				"the Burgher child didn't take the Scholar's trade: " + bur.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			helper.succeed();
		});
	}

}
