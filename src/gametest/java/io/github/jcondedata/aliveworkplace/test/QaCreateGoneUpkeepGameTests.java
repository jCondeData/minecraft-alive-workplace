package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane E, optional mods, 2026-10-27): Create taken out of the pack after the village built its Create builds. Each
 * Create build (the nine workplaces and the Train Station) standing as built, its Create blocks gone with Create: the
 * builders' repair round finds nothing of it to put back (no "Repair it?" about parts that no longer exist, no Create
 * item wanted), now and once its water has settled.
 */
public class QaCreateGoneUpkeepGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos AT = new BlockPos(2, 2, 2);

	//$ gametest_ticks_batch HUGE '600' '"qa_create_gone_upkeep"'
	@GameTest(template = HUGE, timeoutTicks = 600, batch = "qa_create_gone_upkeep")
	public void aCreateBuildStandingWithoutCreateNeedsNoRepair(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.assertFalse(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("create"), "this test is for a pack without Create");
		List<StarterBlueprints.Entry> builds = Stream.concat(StarterBlueprints.CREATE_ONLY.stream(), StarterBlueprints.TRAINS_ONLY.stream()).toList();
		List<String> wrong = new ArrayList<>();
		int[] index = {0};
		int[] wait = {0};
		int[] checked = {0};
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(AT), Rotation.NONE, Mirror.NONE);
		helper.onEachTick(() -> {
			if (index[0] >= builds.size()) {
				return;
			}
			StarterBlueprints.Entry build = builds.get(index[0]);
			Optional<Blueprint> blueprint = BlueprintLibrary.get(level, build.id());
			if (blueprint.isEmpty()) {
				index[0]++; // not readable without Create: nothing to repair from
				return;
			}
			if (wait[0] == 0) {
				clear(helper);
				for (Blueprint.Entry e : blueprint.get().blocks()) {
					helper.assertTrue(!"create".equals(BuiltInRegistries.BLOCK.getKey(e.state().getBlock()).getNamespace()), "a Create block without Create");
					if (!e.state().isAir()) {
						level.setBlock(helper.absolutePos(AT.offset(e.pos())), e.state(), 2 | 16);
					}
				}
				check(level, build, blueprint.get(), placement, "as it stands", wrong);
				checked[0]++;
			}
			if (++wait[0] >= 30) { // the water has run where it runs
				check(level, build, blueprint.get(), placement, "after 30 ticks", wrong);
				wait[0] = 0;
				index[0]++;
				if (index[0] == builds.size()) {
					clear(helper);
					helper.assertTrue(checked[0] == builds.size(), "only " + checked[0] + " of " + builds.size() + " Create builds could be read without Create");
					helper.assertTrue(wrong.isEmpty(), "Create builds want repairs once Create is gone: " + wrong);
					helper.succeed();
				}
			}
		});
	}

	private static void check(ServerLevel level, StarterBlueprints.Entry build, Blueprint blueprint, BlueprintData.Placement placement, String when,
		List<String> wrong) {
		BuildPlan repair = BuildPlan.repair(blueprint, placement, level);
		if (repair.size() > 0) {
			List<String> what = repair.targets().entrySet().stream().limit(4)
				.map(e -> e.getKey().subtract(placement.origin()).toShortString() + " " + e.getValue()).toList();
			wrong.add(build.id().getPath() + " " + when + ": " + repair.size() + " (" + what + ")");
		}
	}

	private static void clear(GameTestHelper helper) {
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				for (int y = 2; y < 29; y++) {
					helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState(), 2 | 16);
				}
			}
		}
	}
}
