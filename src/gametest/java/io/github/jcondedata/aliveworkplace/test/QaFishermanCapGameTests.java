package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.fish.FishermanCap;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane E, 2026-10-27) for 26.13, from the item's text: "a village takes at most 1 fisherman, plus 1 more per 10
 * villagers"; "a jobless villager skips a barrel when the cap is reached". The village is the villagers round the barrel
 * (the config's villageRadius). On top of FishermanCapGameTests: the cap counts living fishermen, so one who dies leaves
 * room for the next; a fisherman outside the village's radius is not the village's, and one just inside it is.
 */
public class QaFishermanCapGameTests {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos FISHER_BARREL = new BlockPos(3, 2, 3);
	private static final BlockPos FREE_BARREL = new BlockPos(11, 2, 10);
	private static final BlockPos JOBLESS = new BlockPos(11, 2, 12);

	private static void cap(GameTestHelper helper, int radius) {
		Leftovers.clear(helper);
		boolean enabled = FishermanCap.ENABLED;
		int b = FishermanCap.BASE;
		int per = FishermanCap.PER_VILLAGERS;
		int r = FishermanCap.RADIUS;
		FishermanCap.ENABLED = true;
		FishermanCap.BASE = 1;
		FishermanCap.PER_VILLAGERS = 10;
		FishermanCap.RADIUS = radius;
		Leftovers.after(helper, () -> {
			FishermanCap.ENABLED = enabled;
			FishermanCap.BASE = b;
			FishermanCap.PER_VILLAGERS = per;
			FishermanCap.RADIUS = r;
		});
		helper.setDayTime(1000);
	}

	private static Villager fisherman(GameTestHelper helper) {
		helper.setBlock(FISHER_BARREL, Blocks.BARREL);
		Villager fisher = helper.spawn(EntityType.VILLAGER, FISHER_BARREL.east());
		fisher.setNoAi(true);
		Jobs.employ(helper.getLevel(), fisher, helper.absolutePos(FISHER_BARREL), PoiTypes.FISHERMAN, VillagerProfession.FISHERMAN);
		return fisher;
	}

	private static VillagerProfession job(Villager v) {
		return v.getVillagerData().getProfession();
	}

	/** The village of two has its fisherman; he is killed, and the jobless villager by the free barrel takes it. */
	//$ gametest_ticks_batch AREA '1200' '"qaFishermanCapDeath"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaFishermanCapDeath")
	public void aFishermanWhoDiesLeavesRoomForTheNext(GameTestHelper helper) {
		cap(helper, 24);
		ServerLevel level = helper.getLevel();
		Villager fisher = fisherman(helper);
		helper.setBlock(FREE_BARREL, Blocks.BARREL);
		BlockPos free = helper.absolutePos(FREE_BARREL);
		helper.assertTrue(FishermanCap.full(level, free), "a village with its fisherman has room for another");
		Villager jobless = helper.spawn(EntityType.VILLAGER, JOBLESS);
		fisher.kill();
		helper.assertTrue(!FishermanCap.full(level, free), "the village still counts its dead fisherman");
		helper.succeedWhen(() -> {
			helper.assertTrue(job(jobless) == VillagerProfession.FISHERMAN, "the jobless villager is still " + job(jobless) + " after the fisherman died");
			GlobalPos site = jobless.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
			helper.assertTrue(site != null && (site.pos().equals(free) || site.pos().equals(helper.absolutePos(FISHER_BARREL))), "works at " + site);
		});
	}

	/**
	 * The fisherman stands about 11 blocks from the free barrel. With the village's radius at 8 he isn't the barrel's
	 * village's, so the jobless villager takes the barrel; with it at 12 he is, and the barrel is passed by.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaFishermanCapRadius"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaFishermanCapRadius")
	public void aFishermanOutsideTheVillagesRadiusIsNotCounted(GameTestHelper helper) {
		cap(helper, 12);
		ServerLevel level = helper.getLevel();
		Villager fisher = fisherman(helper);
		helper.setBlock(FREE_BARREL, Blocks.BARREL);
		BlockPos free = helper.absolutePos(FREE_BARREL);
		double off = Math.sqrt(fisher.distanceToSqr(free.getX() + 0.5, free.getY() + 0.5, free.getZ() + 0.5));
		helper.assertTrue(off > 8 && off < 12, "the fisherman is " + off + " blocks from the free barrel, not between 8 and 12");
		helper.assertTrue(FishermanCap.full(level, free), "radius 12: the fisherman " + off + " blocks off isn't counted");
		FishermanCap.RADIUS = 8;
		helper.assertTrue(!FishermanCap.full(level, free), "radius 8: the fisherman " + off + " blocks off is still counted");
		Villager jobless = helper.spawn(EntityType.VILLAGER, JOBLESS);
		helper.succeedWhen(() -> {
			helper.assertTrue(job(jobless) == VillagerProfession.FISHERMAN, "still " + job(jobless) + ": the fisherman outside the village held the cap");
			GlobalPos site = jobless.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
			helper.assertTrue(site != null && site.pos().equals(free), "works at " + site);
		});
	}
}
