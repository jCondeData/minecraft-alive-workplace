package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * B46: a site's progress never goes down across a restart. A hut placed three blocks above the ground needs a
 * foundation; halfway through it the site is saved and loaded as a restart does. The loaded site works its foundation
 * list out again from the terrain (where the filled part now counts as ground), but shows at least the progress it had.
 */
public class B46GameTests {
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final String BIG_AREA = "aliveworkplace_test:big_area";

	//$ gametest_ticks_batch BIG_AREA '3000' '"b46_progress"'
	@GameTest(template = BIG_AREA, timeoutTicks = 3000, batch = "b46_progress")
	public void progressNeverDropsAcrossARestart(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer()));
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		helper.setDayTime(2000);
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, villager, helper.absolutePos(new BlockPos(2, 2, 2)));
		BuildSite site = Builders.start(level, villager, null, TEST_HUT, new BlueprintData.Placement(level.dimension().location(),
			helper.absolutePos(new BlockPos(7, 5, 7)), Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null && !plan.steps(BuildPlan.Stage.FOUNDATION).isEmpty(), "the raised hut has no foundation to fill");
		int foundation = plan.steps(BuildPlan.Stage.FOUNDATION).size();
		String[] result = {null};
		helper.onEachTick(() -> {
			// A third of the way through the foundation: save and load the site, as a restart does.
			if (result[0] != null || site.stage() != BuildPlan.Stage.FOUNDATION
				|| site.progress(plan) < foundation / 3f / plan.placeableCount()) {
				return;
			}
			float before = site.progress(plan);
			BuildSite loaded = BuildSite.load(site.save());
			helper.assertTrue(loaded != null, "the site didn't load");
			BuildPlan again = loaded.plan(level);
			float after = loaded.progress(again);
			result[0] = after >= before ? "" : "progress dropped across the restart: " + Math.round(before * 100) + "% before, "
				+ Math.round(after * 100) + "% after (" + again.steps(BuildPlan.Stage.FOUNDATION).size() + " foundation steps now, "
				+ foundation + " before)";
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(result[0] != null, "never a third through the foundation (stage " + site.stage() + ", "
				+ Math.round(site.progress(plan) * 100) + "%)");
			helper.assertTrue(result[0].isEmpty(), result[0]);
		});
	}
}
