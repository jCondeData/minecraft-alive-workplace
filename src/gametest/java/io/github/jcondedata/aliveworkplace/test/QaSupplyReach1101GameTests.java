package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b-1101-1238): a builder's supply chests are the storage within 8 blocks across and 4 up or down of his bench
 * (SupplyContainers). A chest exactly 4 above or 4 below the bench is his; one 5 away is not. Checked through what a
 * player sees: the builder builds from planks in chests 4 above and 4 below, ignores planks 5 away, and the materials a
 * build needs from a chest 4 away are kept back for it from the Carpenter and other builders (B696's reservation), not
 * from one 5 away. Kills the nightly mutation survivor at SupplyContainers.inReach (`<=` to `<`).
 */
public class QaSupplyReach1101GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	/** The bench, raised so chests 5 below still stand inside the area. */
	private static final BlockPos BENCH = new BlockPos(4, 7, 4);
	private static final BlockPos UP4 = BENCH.offset(2, 4, 0);
	private static final BlockPos DOWN4 = BENCH.offset(2, -4, 0);
	private static final BlockPos UP5 = BENCH.offset(4, 5, 0);
	private static final BlockPos DOWN5 = BENCH.offset(4, -5, 0);
	private static final BlockPos ROW = new BlockPos(8, 2, 10);

	private static void rules(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
	}

	/** A bench with a builder, the four chests, and a 3-plank row started for him. */
	private static BuildSite scene(GameTestHelper helper, String name) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		rules(helper);
		helper.setDayTime(2000);
		ResourceLocation row = QaCarpenterStorehouse1101GameTests.blueprint(helper, name, ROW);
		for (BlockPos chest : List.of(UP4, DOWN4, UP5, DOWN5)) {
			helper.setBlock(chest, Blocks.CHEST);
		}
		return QaCarpenterStorehouse1101GameTests.start(helper, QaCarpenterStorehouse1101GameTests.builder(helper, BENCH), row, ROW);
	}

	private static String where(BlockPos chest) {
		int dy = chest.getY() - BENCH.getY();
		return "the chest " + Math.abs(dy) + (dy > 0 ? " above" : " below") + " the bench";
	}

	/**
	 * A chest 4 above or 4 below the bench is one of the builder's supply chests and what his build needs is kept back in
	 * it; one 5 above or below is neither.
	 */
	//$ gametest_ticks_batch BIG '40' '"qaSupplyReach1101Edge"'
	@GameTest(template = BIG, timeoutTicks = 40, batch = "qaSupplyReach1101Edge")
	public void chestsFourAboveAndBelowAreTheBuildersAndFiveAreNot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		scene(helper, "reach_edge");
		BlockPos bench = helper.absolutePos(BENCH);
		helper.runAfterDelay(2, () -> {
			List<BlockPos> found = SupplyContainers.find(level, bench, null);
			for (BlockPos rel : List.of(UP4, DOWN4, UP5, DOWN5)) {
				BlockPos chest = helper.absolutePos(rel);
				boolean his = Math.abs(rel.getY() - BENCH.getY()) <= 4;
				helper.assertTrue(found.contains(chest) == his, where(rel) + (his ? " isn't" : " is") + " counted as the builder's supply chest");
				helper.assertTrue(SupplyContainers.inReach(bench, chest) == his, where(rel) + (his ? " is out of" : " is within") + " the builder's reach");
				Map<Item, Integer> kept = Builders.reservedIn(level, List.of(chest), null);
				helper.assertTrue(kept.isEmpty() != his, his
					? "nothing in " + where(rel) + " is kept back for the builder's row: the Carpenter could craft away the planks it waits for"
					: where(rel) + " keeps back " + kept + " for a builder whose bench it is out of reach of");
			}
			helper.succeed();
		});
	}

	/** Two planks in the chest 4 above the bench and one in the chest 4 below: the builder builds his whole row from them. */
	//$ gametest_ticks_batch BIG '1200' '"qaSupplyReach1101Uses"'
	@GameTest(template = BIG, timeoutTicks = 1200, batch = "qaSupplyReach1101Uses")
	public void aBuilderBuildsFromChestsFourAboveAndBelow(GameTestHelper helper) {
		BuildSite site = scene(helper, "reach_use");
		((Container) helper.getBlockEntity(UP4)).setItem(0, new ItemStack(Items.SPRUCE_PLANKS, 2));
		((Container) helper.getBlockEntity(DOWN4)).setItem(0, new ItemStack(Items.SPRUCE_PLANKS, 1));
		helper.succeedWhen(() -> {
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.done(helper, site), "the builder's row isn't finished with planks 4 above and 4 below his bench: "
				+ site.status() + ", missing " + site.missing());
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.placed(helper, ROW) == 3, "the row has " + QaCarpenterStorehouse1101GameTests.placed(helper, ROW) + " of 3 planks");
		});
	}

	/** Planks only in the chests 5 above and 5 below: after 600 ticks the builder hasn't taken one; they're not his. */
	//$ gametest_ticks_batch BIG '700' '"qaSupplyReach1101Ignores"'
	@GameTest(template = BIG, timeoutTicks = 700, batch = "qaSupplyReach1101Ignores")
	public void aBuilderLeavesChestsFiveAwayAlone(GameTestHelper helper) {
		BuildSite site = scene(helper, "reach_far");
		Container up = helper.getBlockEntity(UP5);
		Container down = helper.getBlockEntity(DOWN5);
		up.setItem(0, new ItemStack(Items.SPRUCE_PLANKS, 2));
		down.setItem(0, new ItemStack(Items.SPRUCE_PLANKS, 2));
		helper.runAfterDelay(600, () -> {
			helper.assertTrue(up.countItem(Items.SPRUCE_PLANKS) == 2 && down.countItem(Items.SPRUCE_PLANKS) == 2,
				"the builder took planks from a chest 5 blocks above or below his bench (left: " + up.countItem(Items.SPRUCE_PLANKS) + " above, "
					+ down.countItem(Items.SPRUCE_PLANKS) + " below)");
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.placed(helper, ROW) == 0, "the row got planks from chests out of the builder's reach");
			helper.assertTrue(!QaCarpenterStorehouse1101GameTests.done(helper, site), "the row was finished without any planks in reach");
			helper.succeed();
		});
	}
}
