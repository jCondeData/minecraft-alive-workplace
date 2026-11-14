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
 * QA (lane c, 2026-11-14) for B1196 and B1197 (the Glassblower with Create's loop-back glass recipes), from
 * glassblower.md: "a coal or charcoal for every eight fired (nine glass take two coal)", "With sand but no fuel: 'Can't
 * be made yet: short of 1 Coal to make 1 Glass Pane'" and "A missing dye is named as the dye ('short of 1 Red Dye to
 * make 1 Red Stained Glass')", all with the pack's mods loaded. The fix's own tests cover four and eight glass; these
 * are the boundary above eight and the other glass lines, so Create's recipes never stand in for sand, coal or a dye.
 */
public class QaGlassCreate1114cCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** Nine sand and three coal in his own chest, nine glass asked: nine sand and exactly two coal fired. */
	//$ gametest_ticks_batch AREA '4000' '"qaGlassCreate1114cNine"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaGlassCreate1114cNine")
	public void nineGlassTakeTwoCoalWithThePacksMods(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.GLASS, 3, 3, "qa1114cnine"),
			Blocks.CHEST.defaultBlockState());
		helper.setBlock(QaCrafterOwnModdedStore1111eCompatTests.OWN, Blocks.CHEST);
		Villager blower = QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper, new ItemStack(Items.SAND, 9), new ItemStack(Items.COAL, 3));
		QaCrafterOwnModdedStore1111eCompatTests.watch(helper, a, blower.getUUID(), List.of(QaCrafterOwnModdedStore1111eCompatTests.SAND_AND_GLASS),
			(start, end) -> {
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.SAND, 9);
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.COAL, 2);
				QaCrafterOwnModdedStore1111eCompatTests.exactly(helper, end, Items.GLASS, 9);
			});
	}

	/** Six sand and no fuel anywhere, one glass pane asked: the line names the coal, word for word. */
	//$ gametest_ticks_batch AREA '1100' '"qaGlassCreate1114cPane"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qaGlassCreate1114cPane")
	public void withCreateAPaneShortOfFuelNamesTheCoal(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.GLASS_PANE, 1, 1, "qa1114cpane"),
			Blocks.CHEST.defaultBlockState(), new ItemStack(Items.SAND, 6));
		helper.setBlock(QaCrafterOwnModdedStore1111eCompatTests.OWN, Blocks.CHEST);
		QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper);
		helper.runAfterDelay(1000, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			String want = "Can't be made yet: short of 1 Coal to make 1 Glass Pane";
			helper.assertTrue(want.equals(text), "the builder's line: '" + text + "', the spec's: '" + want + "'");
			helper.succeed();
		});
	}

	/** Eight sand and a coal but no dye, one red stained glass asked: the line names the red dye, word for word. */
	//$ gametest_ticks_batch AREA '1100' '"qaGlassCreate1114cDye"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qaGlassCreate1114cDye")
	public void withCreateAMissingDyeIsNamedAsTheDye(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.RED_STAINED_GLASS, 1, 1, "qa1114cdye"),
			Blocks.CHEST.defaultBlockState(), new ItemStack(Items.SAND, 8), new ItemStack(Items.COAL, 1));
		helper.setBlock(QaCrafterOwnModdedStore1111eCompatTests.OWN, Blocks.CHEST);
		QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper);
		helper.runAfterDelay(1000, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			String want = "Can't be made yet: short of 1 Red Dye to make 1 Red Stained Glass";
			helper.assertTrue(want.equals(text), "the builder's line: '" + text + "', the spec's: '" + want + "'");
			helper.succeed();
		});
	}
}
