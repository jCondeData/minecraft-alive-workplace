package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Stations;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane E, 2026-10-31, optional-mod absence), B697's fix from the other side: without Cobblemon a jobless villager
 * skips a Training Post by himself, but he isn't stuck on it: with the post right beside him and a lectern a few blocks
 * off, he takes the lectern (a Librarian or the Teacher, who shares it) in the same wait a Miner's Bench takes (QaNoCobblemonBlocksGameTests' control), and never
 * the Trainer on the way. The post is still free afterwards.
 */
public class QaNoCobblemonNearbyJob1031GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos POST = new BlockPos(12, 2, 11);
	private static final BlockPos LECTERN = new BlockPos(7, 2, 8);
	private static final BlockPos STANDING = new BlockPos(12, 2, 12);
	private static final int WAIT = 600;

	//$ gametest_ticks_batch AREA '700' '"qaNoCobblemonNearbyLectern"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaNoCobblemonNearbyLectern")
	public void aJoblessVillagerByATrainingPostTakesTheLecternFurtherOff(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(POST, ModBlocks.TRAINING_POST);
		helper.setBlock(LECTERN, Blocks.LECTERN);
		Villager v = helper.spawn(EntityType.VILLAGER, STANDING);
		helper.onEachTick(() -> {
			VillagerProfession p = v.getVillagerData().getProfession();
			if (p != VillagerProfession.NONE && !Stations.available(p)) {
				helper.fail("without Cobblemon the villager took " + BuiltInRegistries.VILLAGER_PROFESSION.getKey(p) + " at tick " + helper.getTick());
			}
		});
		helper.runAtTickTime(WAIT, () -> {
			VillagerProfession p = v.getVillagerData().getProfession();
			java.util.Optional<net.minecraft.core.GlobalPos> site = v.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
			helper.assertTrue(p != VillagerProfession.NONE && site.isPresent() && site.get().pos().equals(helper.absolutePos(LECTERN)),
				"a jobless villager by a Training Post (skipped without Cobblemon) didn't go on to the lectern 5 blocks off in " + WAIT + " ticks: he is "
					+ BuiltInRegistries.VILLAGER_PROFESSION.getKey(p) + ", job site " + site.map(g -> g.pos().toShortString()).orElse("none"));
			helper.succeed();
		});
	}
}
