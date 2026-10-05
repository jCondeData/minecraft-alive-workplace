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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * Bug test (QA, B46's Expected): a site saved by 0.138.0 or earlier (no shown_progress), opened with the fixed jar, still
 * drops: half through its foundation it shows 24% before and 0% after loading. The save still holds the old cursor, so
 * the progress it had can be worked out before the foundation list is redone.
 */
public class B46OldSaveGameTests {
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final String BIG_AREA = "aliveworkplace_test:big_area";

	//$ gametest_ticks_batch BIG_AREA '3000' '"b46_old_save"'
	@GameTest(template = BIG_AREA, timeoutTicks = 3000, batch = "b46_old_save")
	public void aSiteSavedBeforeTheFixKeepsItsProgress(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
		});
		helper.setDayTime(2000);
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, villager, helper.absolutePos(new BlockPos(2, 2, 2)));
		BuildSite site = Builders.start(level, villager, null, TEST_HUT, new BlueprintData.Placement(level.dimension().location(),
			helper.absolutePos(new BlockPos(7, 5, 7)), Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null && !plan.steps(BuildPlan.Stage.FOUNDATION).isEmpty(), "the raised hut has no foundation to fill");
		int foundation = plan.steps(BuildPlan.Stage.FOUNDATION).size();
		helper.assertTrue(site.progress(plan) == 0f, "a new site starts at " + site.progress(plan));
		String[] result = {null};
		helper.onEachTick(() -> {
			// Half way through the foundation: restart twice in a row.
			if (result[0] != null || site.stage() != BuildPlan.Stage.FOUNDATION
				|| site.progress(plan) < foundation / 2f / plan.placeableCount()) {
				return;
			}
			float before = site.progress(plan);
			CompoundTag saved = site.save();
			helper.assertTrue(saved.contains("shown_progress"), "a site with progress doesn't save what it showed");
			BuildSite first = BuildSite.load(saved);
			float afterFirst = first.progress(first.plan(level));
			BuildSite second = BuildSite.load(first.save());
			float afterSecond = second.progress(second.plan(level));
			// A save from before the fix: no shown_progress. It loads, at what its own steps say (0% or more).
			CompoundTag old = site.save();
			old.remove("shown_progress");
			BuildSite oldSite = BuildSite.load(old);
			helper.assertTrue(oldSite != null && oldSite.stage() == BuildPlan.Stage.FOUNDATION, "a save without shown_progress didn't load");
			float oldProgress = oldSite.progress(oldSite.plan(level));
			result[0] = afterFirst < before || afterSecond < before
				? "progress dropped: " + pct(before) + " before, " + pct(afterFirst) + " after one restart, " + pct(afterSecond) + " after two"
				: oldProgress < before ? "a site saved before the fix (no shown_progress) dropped from " + pct(before) + " to " + pct(oldProgress) : "";
			helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("[b46-old-save] before " + pct(before)
				+ ", one restart " + pct(afterFirst) + ", two " + pct(afterSecond) + ", old save " + pct(oldProgress)));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(result[0] != null, "never half through the foundation (stage " + site.stage() + ", " + pct(site.progress(plan)) + ")");
			helper.assertTrue(result[0].isEmpty(), result[0]);
		});
	}

	private static String pct(float f) {
		return Math.round(f * 100) + "%";
	}
}
