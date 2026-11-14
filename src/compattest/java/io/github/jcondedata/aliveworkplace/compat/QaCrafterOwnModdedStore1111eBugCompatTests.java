package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane E, 2026-11-11): two things the Glassblower's spec (46.6, glassblower.md: "a coal for every eight"; the
 * shortage line names the sand: "Can't be made yet: short of 4 Sand, 1 Coal to make 4 Glass") gets wrong only with the
 * pack's mods loaded (Create's smelting recipes for glass from tiled and framed glass among them). The same set-ups pass
 * in the plain GameTest suite (QaCrafterOwnChest1111cGameTests, QaGlassblower1111bGameTests).
 */
public class QaCrafterOwnModdedStore1111eBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** No sand anywhere, a coal in the builder's chest, 4 glass asked: the line names the sand, never Create's Tiled Glass. */
	//$ gametest_ticks_batch AREA '1100' '"qaOwnMod1111eBugLine"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qaOwnMod1111eBugLine")
	public void withCreateTheGlassLineStillNamesTheSand(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.GLASS, 4, 1, "bugline"),
			Blocks.CHEST.defaultBlockState(), new ItemStack(Items.COAL, 1));
		helper.setBlock(QaCrafterOwnModdedStore1111eCompatTests.OWN, Blocks.CHEST);
		QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper);
		helper.runAfterDelay(1000, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue("Can't be made yet: short of 4 Sand to make 4 Glass".equals(text), "the builder's line: '" + text
				+ "', the spec's: 'Can't be made yet: short of 4 Sand to make 4 Glass'");
			helper.succeed();
		});
	}

	/** 8 sand and 2 coal in the Glassblower's own chest, 8 glass asked: 8 sand and exactly 1 coal fired ("a coal for every eight"). */
	//$ gametest_ticks_batch AREA '4000' '"qaOwnMod1111eBugCoal"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaOwnMod1111eBugCoal")
	public void eightGlassBurnOneCoalWithThePacksMods(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.GLASS, 4, 2, "bugcoal"),
			Blocks.CHEST.defaultBlockState());
		helper.setBlock(QaCrafterOwnModdedStore1111eCompatTests.OWN, Blocks.CHEST);
		Villager blower = QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper, new ItemStack(Items.SAND, 8), new ItemStack(Items.COAL, 2));
		QaCrafterOwnModdedStore1111eCompatTests.watch(helper, a, blower.getUUID(), List.of(QaCrafterOwnModdedStore1111eCompatTests.SAND_AND_GLASS),
			(start, end) -> {
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.SAND, 8);
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.COAL, 1);
				QaCrafterOwnModdedStore1111eCompatTests.exactly(helper, end, Items.GLASS, 8);
			});
	}
}
