package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-c-1121-2153), from B1384's expectation and CLAUDE.md's builder invariant: "a step that can't be done is
 * deferred once, then skipped", so the build finishes. B1384's builder stood on one roof block from tick 9,000 to the
 * timeout (skipped=0); its fix found that was the builder's day ending, not a stuck step. These tests make a roof
 * block truly impossible (bedrock in the topmost roof cell, which no builder can break or place into) and prove the
 * builder doesn't stop for good on it: the hut is finished with that one cell skipped and every other cell built, also
 * with the sites saved and loaded mid-build. The clock is held in working hours, as B1384's fix does.
 */
public class QaRoofSkip1121c2GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos HUT_ORIGIN = new BlockPos(6, 2, 6);

	private record Run(ServerLevel level, Villager builder, UUID id, BuildSite[] site, BuildPlan plan, BlockPos roof) {
	}

	private static void log(String line) {
		AliveWorkplace.LOG.info("[qa-roof-skip-1121c2] {}", line);
	}

	private static Run start(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = (Container) helper.getBlockEntity(CHEST);
		ItemStack[] items = {new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.OAK_DOOR), new ItemStack(Items.TORCH)};
		for (int i = 0; i < items.length; i++) {
			chest.setItem(i, items[i].copy());
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, villager, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT_ORIGIN), Rotation.NONE, Mirror.NONE));
		if (site == null) {
			throw new GameTestAssertException("the hut's build didn't start");
		}
		BuildPlan plan = site.plan(level);
		// On empty ground every cell still to build is a block of the hut; the topmost is a roof block.
		List<BlockPos> todo = plan.unfinished(level);
		BlockPos roof = todo.stream().max(Comparator.comparingInt(BlockPos::getY)).orElseThrow();
		helper.assertTrue(roof.getY() > helper.absolutePos(HUT_ORIGIN).getY() + 2, "the hut's top cell " + helper.relativePos(roof) + " isn't a roof");
		level.setBlockAndUpdate(roof, Blocks.BEDROCK.defaultBlockState());
		helper.onEachTick(() -> {
			if (helper.getTick() % 20 == 0) {
				helper.setDayTime(2000); // working hours (B1384: the day's end isn't a stuck step)
			}
		});
		log("bedrock in the roof cell " + helper.relativePos(roof) + " of " + todo.size() + " cells");
		return new Run(level, villager, site.id(), new BuildSite[]{site}, plan, roof);
	}

	private static void assertFinishedWithOnlyTheRoofSkipped(GameTestHelper helper, Run r) {
		BuildSite site = r.site()[0];
		helper.assertTrue(BuildSiteManager.get(r.level()).get(r.id()) == null, "the build stopped and never finished: stage " + site.stage()
			+ ", status " + site.status() + ", skipped " + site.skipped() + ", " + r.plan().unfinished(r.level()).size() + " cells left");
		helper.assertTrue(site.skipped() >= 1, "the impossible roof block wasn't counted as skipped: " + site.skipped());
		helper.assertTrue(r.level().getBlockState(r.roof()).is(Blocks.BEDROCK), "the bedrock in the roof is gone: " + r.level().getBlockState(r.roof()));
		List<BlockPos> left = r.plan().unfinished(r.level());
		helper.assertTrue(left.equals(List.of(r.roof())), "cells other than the impossible one left unbuilt: "
			+ left.stream().limit(5).map(helper::relativePos).toList() + " (" + left.size() + ")");
		log("finished at tick " + helper.getTick() + " with " + site.skipped() + " skipped; only " + helper.relativePos(r.roof()) + " left");
	}

	/** A roof block that can't be placed is skipped and the hut is finished, every other block of it built. */
	//$ gametest_ticks_batch BIG '6000' '"qaRoofSkip1121c2"'
	@GameTest(template = BIG, timeoutTicks = 6000, batch = "qaRoofSkip1121c2")
	public void anImpossibleRoofBlockIsSkippedAndTheBuildFinishes(GameTestHelper helper) {
		Run r = start(helper);
		helper.succeedWhen(() -> assertFinishedWithOnlyTheRoofSkipped(helper, r));
	}

	/** The same with the sites saved and loaded again halfway (BuilderWork restartable at any tick). */
	//$ gametest_ticks_batch BIG '6000' '"qaRoofSkip1121c2Reload"'
	@GameTest(template = BIG, timeoutTicks = 6000, batch = "qaRoofSkip1121c2Reload")
	public void anImpossibleRoofBlockIsSkippedAfterASaveMidBuild(GameTestHelper helper) {
		Run r = start(helper);
		int total = r.plan().unfinished(r.level()).size();
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			if (reloaded[0] || helper.getTick() % 10 != 0) {
				return;
			}
			int left = r.plan().unfinished(r.level()).size();
			if (left > total / 2) {
				return;
			}
			HolderLookup.Provider registries = r.level().registryAccess();
			r.level().getDataStorage().set(BuildSiteManager.NAME,
				BuildSiteManager.load(BuildSiteManager.get(r.level()).save(new CompoundTag(), registries), registries));
			BuildSite back = BuildSiteManager.get(r.level()).get(r.id());
			helper.assertTrue(back != null, "the site was lost in the save");
			r.site()[0] = back;
			reloaded[0] = true;
			log("sites saved and loaded at tick " + helper.getTick() + " with " + left + " of " + total + " cells left");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never reached halfway");
			assertFinishedWithOnlyTheRoofSkipped(helper, r);
		});
	}
}
