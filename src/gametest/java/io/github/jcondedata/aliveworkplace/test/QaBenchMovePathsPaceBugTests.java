package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Paths;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Traits;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.Pace;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** QA (lane-b-1026-0338): what {@link QaBenchMovePathsPaceGameTests} found wrong. Not registered, not committed. */
public class QaBenchMovePathsPaceBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	/**
	 * The same at the default delay of 8 ticks a block, as a player has it: from start to finish, the Master builds the
	 * whole hut with the wiki's ticks per block as the Novice (the walk to the chests and the site is the same for both).
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qaLevelPaceDefaultBug"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaLevelPaceDefaultBug")
	public void atTheDefaultDelayABlockTakesTheDelayNotATickMore(GameTestHelper helper) {
		ServerLevel level = QaBenchMovePathsPaceGameTests.prepare(helper, 8);
		QaBenchMovePathsPaceGameTests.traitsOff(helper);
		Villager novice = QaBenchMovePathsPaceGameTests.bench(helper, new BlockPos(2, 2, 2), QaBenchMovePathsPaceGameTests.hutMaterials());
		Villager master = QaBenchMovePathsPaceGameTests.bench(helper, new BlockPos(26, 2, 2), QaBenchMovePathsPaceGameTests.hutMaterials());
		master.setVillagerData(master.getVillagerData().setLevel(5));
		BuildSite noviceSite = Builders.start(level, novice, null, TEST_HUT, QaBenchMovePathsPaceGameTests.placement(helper, new BlockPos(4, 2, 12)));
		BuildSite masterSite = Builders.start(level, master, null, TEST_HUT, QaBenchMovePathsPaceGameTests.placement(helper, new BlockPos(20, 2, 12)));
		BuildPlan novicePlan = noviceSite.plan(level);
		BuildPlan masterPlan = masterSite.plan(level);
		if (novicePlan == null || masterPlan == null) {
			throw new GameTestAssertException("no test hut blueprint");
		}
		List<Long> noviceTimes = new ArrayList<>();
		List<Long> masterTimes = new ArrayList<>();
		int[] left = {novicePlan.unfinished(level).size(), masterPlan.unfinished(level).size()};
		long[] done = {-1, -1};
		long start = level.getGameTime();
		helper.onEachTick(() -> {
			long now = level.getGameTime();
			int n = novicePlan.unfinished(level).size();
			int m = masterPlan.unfinished(level).size();
			for (int i = n; i < left[0]; i++) {
				noviceTimes.add(now);
			}
			for (int i = m; i < left[1]; i++) {
				masterTimes.add(now);
			}
			left[0] = n;
			left[1] = m;
			if (n == 0 && done[0] < 0) {
				done[0] = now;
			}
			if (m == 0 && done[1] < 0) {
				done[1] = now;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(done[0] >= 0 && done[1] >= 0, "not both built: novice " + left[0] + " left, master " + left[1] + " left");
			// from each one's first block to his last: the placing itself, without the first walk to the chests
			long noviceSpan = noviceTimes.get(noviceTimes.size() - 1) - noviceTimes.get(0);
			long masterSpan = masterTimes.get(masterTimes.size() - 1) - masterTimes.get(0);
			float ratio = noviceSpan / (float) Math.max(1, masterSpan);
			level.getServer().sendSystemMessage(Component.literal("[qa-level-pace-default] novice " + noviceSpan + " ticks first-to-last block ("
				+ (done[0] - start) + " in all), master " + masterSpan + " (" + (done[1] - start) + "), " + ratio + "x, medians "
				+ QaBenchMovePathsPaceGameTests.median(noviceTimes) + "/" + QaBenchMovePathsPaceGameTests.median(masterTimes)));
			int noviceGap = QaBenchMovePathsPaceGameTests.median(noviceTimes);
			int masterGap = QaBenchMovePathsPaceGameTests.median(masterTimes);
			helper.assertTrue(noviceGap == 8 && masterGap == 3, "at the default delay of 8 the Novice waits " + noviceGap
				+ " ticks between blocks (expected 8) and the Master " + masterGap + " (expected 3): a Master is "
				+ (noviceGap / (float) masterGap) + " times as fast per block, not 2.5 (" + ratio + " first to last block)");
		});
	}

}
