package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.command.FurnishedPackTest;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * ROADMAP 37.11: {@code /workplace furnished} (the pack test's furnished builds) sets each build's builder up with barrels
 * by its bench holding exactly what its plan costs: every item the plan needs, nothing more, and no empty barrel.
 */
public class FurnishedPackTestGameTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";

	/** The Starter Cottage's lot: its bench stands 8 blocks west and 2 south of it, the barrels west of the bench. */
	//$ gametest_ticks_batch HUGE_AREA '100' '"furnished_pack_barrels"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 100, batch = "furnished_pack_barrels")
	public void theBarrelsHoldExactlyWhatThePlanCosts(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos lot = helper.absolutePos(new BlockPos(14, 2, 8));
		BlockPos bench = lot.offset(-8, 0, 2);
		ResourceLocation cottage = AliveWorkplace.id("starter_cottage");
		BuildSiteManager sites = BuildSiteManager.get(level);
		BuildSite site = null;
		try {
			start(level, lot, cottage);
			site = sites.all().stream().filter(s -> s.placement().origin().equals(lot)).findFirst().orElse(null);
			helper.assertTrue(site != null, "no build site started at the lot");
			BuildPlan plan = site.plan(level);
			helper.assertTrue(plan != null, "the site has no plan");

			// Every barrel near the bench (well past where they stand), and what they hold.
			Map<Item, Integer> held = new HashMap<>();
			int barrels = 0;
			int empty = 0;
			for (BlockPos p : BlockPos.betweenClosed(bench.offset(-8, -1, -8), bench.offset(1, 4, 8))) {
				if (!level.getBlockState(p).is(Blocks.BARREL)) {
					continue;
				}
				barrels++;
				helper.assertTrue(level.getBlockEntity(p) instanceof Container, "a barrel without its inventory at " + helper.relativePos(p));
				Container barrel = (Container) level.getBlockEntity(p);
				boolean any = false;
				for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
					ItemStack stack = barrel.getItem(slot);
					if (!stack.isEmpty()) {
						any = true;
						held.merge(stack.getItem(), stack.getCount(), Integer::sum);
					}
				}
				if (!any) {
					empty++;
				}
			}
			helper.assertTrue(barrels > 0, "no barrels by the bench");
			helper.assertTrue(empty == 0, empty + " of " + barrels + " barrels by the bench are empty");
			helper.assertTrue(held.equals(plan.materials()), "the barrels hold " + held + ", the plan costs " + plan.materials());
		} finally {
			if (site != null) {
				sites.remove(site.id());
			}
			Leftovers.clear(helper);
		}
		helper.succeed();
	}

	/** The command's own set-up of one build (it's registered only for the pack test's measuring runs). */
	private static void start(ServerLevel level, BlockPos lot, ResourceLocation id) {
		try {
			Method start = FurnishedPackTest.class.getDeclaredMethod("start", ServerLevel.class, BlockPos.class, ResourceLocation.class);
			start.setAccessible(true);
			start.invoke(null, level, lot, id);
		} catch (ReflectiveOperationException e) {
			throw new GameTestAssertException("can't start the furnished pack test's build: " + e);
		}
	}
}
