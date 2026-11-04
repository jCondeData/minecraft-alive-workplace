package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.SupplyReport;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * B953: the held blueprint's supply report (its tooltip, {@link BlueprintSupplies#check}) and the Material List book
 * ({@link BlueprintSupplies#checklist}) asked for the blocks over the world's build limit (or under its bottom) that the
 * builder leaves out (B937), so a player hauled materials that were never used. From QA's
 * QaBuilderEdges1104bBugTests#theHeldBlueprintsListsAcrossTheTopAskOnlyForWhatCanStand on tests/builders-1104b.
 */
public class B953GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * The hut laid across the world's top (two layers under it, the rest over), the chest by the bench holding exactly what
	 * can stand: the report says nothing is missing, and the book lists only those blocks with nothing still to bring.
	 */
	//$ gametest_ticks_batch HUGE '100' '"b953Top"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "b953Top")
	public void theHeldBlueprintsListsAcrossTheTopAskOnlyForWhatCanStand(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		int g = QaBuilderEdges1104bGameTests.top(helper, 2);
		check(helper, new BlockPos(2, g, 2), new BlockPos(2, g, 4), new BlockPos(6, g, 6), "top");
	}

	/**
	 * The same hut sunk two layers under the world's bottom (a basement dug past bedrock), bench and chest on the bottom
	 * layer: only the blocks inside the world are asked for.
	 */
	//$ gametest_ticks_batch HUGE '100' '"b953Bottom"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "b953Bottom")
	public void theHeldBlueprintsListsUnderTheBottomAskOnlyForWhatCanStand(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		int base = helper.absolutePos(BlockPos.ZERO).getY();
		int min = level.getMinBuildHeight() - base; // the world's bottom layer, relative to the test area
		for (int x = -1; x <= 24; x++) {
			for (int z = -1; z <= 16; z++) {
				for (int y = min; y < min + 12; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		Leftovers.after(helper, () -> {
			for (int x = -1; x <= 24; x++) {
				for (int z = -1; z <= 16; z++) {
					for (int y = min; y < min + 12; y++) {
						level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		});
		check(helper, new BlockPos(2, min, 2), new BlockPos(2, min, 4), new BlockPos(6, min - 2, 6), "bottom");
	}

	private static void check(GameTestHelper helper, BlockPos bench, BlockPos chest, BlockPos origin, String where) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(bench));
		helper.setBlock(chest, Blocks.CHEST);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildPlan plan = BuildPlan.create(BlueprintLibrary.get(level, QaBuilderEdges1104bGameTests.TEST_HUT).orElseThrow(), placement);
		Map<Item, Integer> under = QaBuilderEdges1104bGameTests.underLimitNeed(level, plan);
		int all = plan.materials().values().stream().mapToInt(Integer::intValue).sum();
		int standing = under.values().stream().mapToInt(Integer::intValue).sum();
		helper.assertTrue(standing > 0 && standing < all, "setup: the hut isn't across the world's " + where + " (" + standing + " of " + all + " blocks inside)");
		Container c = helper.getBlockEntity(chest);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : under.entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				c.setItem(slot++, new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		BlueprintData data = new BlueprintData(QaBuilderEdges1104bGameTests.TEST_HUT, Optional.empty(), Optional.of(placement));
		List<String> wrong = new ArrayList<>();
		SupplyReport report = BlueprintSupplies.check(level, data).orElseThrow();
		if (report.bench().isEmpty()) {
			wrong.add("the supply report found no bench");
		}
		if (!report.missing().isEmpty()) {
			wrong.add("the tooltip's supply report says missing " + report.missing().stream().map(m -> m.count() + " " + m.item()).toList());
		}
		BlueprintSupplies.Checklist list = BlueprintSupplies.checklist(level, data).orElseThrow();
		Map<Item, Integer> listed = new HashMap<>();
		list.lines().forEach(l -> listed.put(l.item(), l.need()));
		if (!listed.equals(under) || list.toBring() != 0) {
			wrong.add("the Material List book lists " + listed + " with " + list.toBring() + " still to bring");
		}
		helper.assertTrue(wrong.isEmpty(), "across the world's " + where + ", with the chest holding exactly what can stand " + under
			+ " (the whole list " + plan.materials() + "): " + String.join("; ", wrong));
		helper.succeed();
	}
}
