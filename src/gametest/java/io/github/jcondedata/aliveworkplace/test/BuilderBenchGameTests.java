package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
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
 * B22: a builder whose job site changes mid-build (two benches close together, swapped) still takes its materials from
 * the chests by the bench the build was started from, never from the other builder's chests. In the 23.1 soak the
 * stone house and the tinker's workshop builders took each other's deepslate tile stairs this way, and the stone house
 * ended one short although its own chests had held its whole material list.
 */
public class BuilderBenchGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos OTHER_BENCH = new BlockPos(14, 2, 2);
	private static final BlockPos OTHER_CHEST = new BlockPos(14, 2, 4);
	private static final BlockPos HUT_ORIGIN = new BlockPos(6, 2, 6);

	//$ gametest_ticks_batch AREA '2400' '"b22_bench"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "b22_bench")
	public void aBuilderWhoseBenchChangesKeepsUsingItsSitesChests(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		helper.setBlock(OTHER_BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(OTHER_CHEST, Blocks.CHEST);
		Container own = helper.getBlockEntity(CHEST);
		own.setItem(0, new ItemStack(Items.COBBLESTONE, 25));
		own.setItem(1, new ItemStack(Items.OAK_PLANKS, 55));
		own.setItem(2, new ItemStack(Items.OAK_DOOR));
		own.setItem(3, new ItemStack(Items.TORCH));
		// Another builder's stock: enough for this hut too, so taking from it would go unnoticed but for this test.
		Container other = helper.getBlockEntity(OTHER_CHEST);
		other.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		other.setItem(1, new ItemStack(Items.OAK_PLANKS, 64));
		other.setItem(2, new ItemStack(Items.OAK_DOOR));
		other.setItem(3, new ItemStack(Items.TORCH));

		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, villager, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT_ORIGIN), Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no plan for the test hut");
		GlobalPos swapped = GlobalPos.of(level.dimension(), helper.absolutePos(OTHER_BENCH));
		// Mid-build, the builder's job site is the other bench (as when two builders swap benches).
		helper.onEachTick(() -> villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, swapped));
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building: stage=" + site.stage() + " status=" + site.status() + " missing=" + site.missing());
			helper.assertTrue(plan.unfinished(level).isEmpty(), "the hut isn't finished: " + plan.unfinished(level).size() + " blocks");
			helper.assertTrue(other.getItem(0).getCount() == 64 && other.getItem(1).getCount() == 64
					&& other.getItem(2).getCount() == 1 && other.getItem(3).getCount() == 1,
				"the builder took from the other builder's chest: " + other.getItem(0) + ", " + other.getItem(1) + ", "
					+ other.getItem(2) + ", " + other.getItem(3));
			helper.assertTrue(own.getItem(0).isEmpty() && own.getItem(1).isEmpty(),
				"its own chest still holds " + own.getItem(0) + ", " + own.getItem(1));
		});
	}

	/** When the site's bench has been broken and put up elsewhere, the builder's bench is where its chests are now. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aMovedBenchBecomesTheSitesBench(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, villager, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT_ORIGIN), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(Builders.siteBench(level, villager, site).orElseThrow().equals(helper.absolutePos(BENCH)), "the site's bench while it stands");

		helper.setBlock(BENCH, Blocks.AIR);
		helper.setBlock(OTHER_BENCH, ModBlocks.BUILDERS_BENCH);
		villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(OTHER_BENCH)));
		helper.assertTrue(Builders.siteBench(level, villager, site).orElseThrow().equals(helper.absolutePos(OTHER_BENCH)),
			"the builder's new bench once the old one is gone");
		helper.assertTrue(helper.absolutePos(OTHER_BENCH).equals(site.bench()), "the site remembers the new bench: " + site.bench());
		helper.succeed();
	}
}
