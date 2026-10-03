package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * B28: a builder keeps their own bench while it stands. In the 23.1 soak two builders whose benches stood 26 blocks
 * apart swapped benches mid-build, over and over: vanilla's walk-back-to-the-job-site behaviour gives the job site up
 * when the villager hasn't reached a walk target for a minute (or is over 100 blocks off), and the builder then took
 * the nearest free bench, the other builder's.
 */
public class BuilderKeepsBenchGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos BENCH_A = new BlockPos(2, 2, 3);
	private static final BlockPos BENCH_B = new BlockPos(2, 2, 12);

	/**
	 * Two idle builders, each standing by the other's bench, who haven't reached a walk target for over a minute
	 * (stuck on a hillside, say): they walk home, and neither ever holds the other's bench.
	 */
	//$ gametest_ticks_batch AREA '400' '"b28_stuck"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "b28_stuck")
	public void aStuckBuilderKeepsTheirBench(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000); // work time
		helper.setBlock(BENCH_A, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(BENCH_B, ModBlocks.BUILDERS_BENCH);
		Villager a = helper.spawn(EntityType.VILLAGER, BENCH_B.south());
		Villager b = helper.spawn(EntityType.VILLAGER, BENCH_A.south());
		Builders.employ(level, a, helper.absolutePos(BENCH_A));
		Builders.employ(level, b, helper.absolutePos(BENCH_B));
		long stuckSince = level.getGameTime() - 1300;
		a.getBrain().setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, stuckSince);
		b.getBrain().setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, stuckSince);

		List<String> lost = new ArrayList<>();
		helper.onEachTick(() -> {
			watch(helper, a, BENCH_A, "A", lost);
			watch(helper, b, BENCH_B, "B", lost);
		});
		helper.runAtTickTime(360, () -> {
			helper.assertTrue(lost.isEmpty(), "a builder lost their bench: " + lost);
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(BENCH_A)) == 0
					&& level.getPoiManager().getFreeTickets(helper.absolutePos(BENCH_B)) == 0,
				"a bench's place was freed");
			helper.assertTrue(a.blockPosition().distManhattan(helper.absolutePos(BENCH_A)) <= 9
					&& b.blockPosition().distManhattan(helper.absolutePos(BENCH_B)) <= 9,
				"the builders didn't walk home: A at " + a.blockPosition().toShortString() + ", B at " + b.blockPosition().toShortString());
			helper.succeed();
		});
	}

	/** Two builders with benches near each other each build a hut (helping each other) and keep their own bench. */
	//$ gametest_ticks_batch AREA '3000' '"b28_day"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "b28_day")
	public void twoBuildersNearEachOtherKeepTheirBenches(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		Villager a = builder(helper, BENCH_A, new BlockPos(2, 2, 5));
		Villager b = builder(helper, BENCH_B, new BlockPos(2, 2, 14));
		BuildSite siteA = start(helper, a, new BlockPos(8, 2, 1));
		BuildSite siteB = start(helper, b, new BlockPos(8, 2, 10));
		BuildPlan planA = siteA.plan(level);
		BuildPlan planB = siteB.plan(level);
		helper.assertTrue(planA != null && planB != null, "no plan for the test hut");

		List<String> lost = new ArrayList<>();
		helper.onEachTick(() -> {
			watch(helper, a, BENCH_A, "A", lost);
			watch(helper, b, BENCH_B, "B", lost);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(lost.isEmpty(), "a builder lost their bench: " + lost);
			helper.assertTrue(BuildSiteManager.get(level).get(siteA.id()) == null && BuildSiteManager.get(level).get(siteB.id()) == null,
				"still building: A " + siteA.stage() + "/" + siteA.status() + ", B " + siteB.stage() + "/" + siteB.status());
			helper.assertTrue(planA.unfinished(level).isEmpty() && planB.unfinished(level).isEmpty(), "a hut isn't finished");
		});
	}

	private static Villager builder(GameTestHelper helper, BlockPos bench, BlockPos chest) {
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(chest, Blocks.CHEST);
		Container stock = helper.getBlockEntity(chest);
		stock.setItem(0, new ItemStack(Items.COBBLESTONE, 25));
		stock.setItem(1, new ItemStack(Items.OAK_PLANKS, 55));
		stock.setItem(2, new ItemStack(Items.OAK_DOOR));
		stock.setItem(3, new ItemStack(Items.TORCH));
		Villager villager = helper.spawn(EntityType.VILLAGER, bench.east());
		Builders.employ(helper.getLevel(), villager, helper.absolutePos(bench));
		return villager;
	}

	private static BuildSite start(GameTestHelper helper, Villager builder, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		return Builders.start(level, builder, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE));
	}

	/** Notes the first tick {@code builder}'s job site isn't {@code bench} (once per builder). */
	private static void watch(GameTestHelper helper, Villager builder, BlockPos bench, String name, List<String> lost) {
		GlobalPos own = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(bench));
		var site = builder.getBrain().getMemory(MemoryModuleType.JOB_SITE);
		if (!site.equals(java.util.Optional.of(own)) && lost.stream().noneMatch(s -> s.startsWith(name))) {
			lost.add(name + " at tick " + helper.getTick() + ": " + site.map(g -> g.pos().toShortString()).orElse("none"));
		}
	}
}
