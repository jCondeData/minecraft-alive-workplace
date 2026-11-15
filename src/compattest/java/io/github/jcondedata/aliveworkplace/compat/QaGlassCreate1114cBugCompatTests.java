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
 * Bug test (QA, lane c, 2026-11-14), found while checking B1196/B1197 (the Glassblower with Create's loop-back glass
 * recipes): glassblower.md says "Glass panes from sand, the glass fired first (a builder with sand and coal gets panes)"
 * and "a coal or charcoal for every eight fired". With the pack's mods loaded a builder asking for four panes with six
 * sand and a coal in his chest is told "short of 1 Coal to make 4 Glass Panes" and never gets them; one pane from the
 * same chest is made, and nine panes from the same chest are made in the plain suite. Not registered on main: fails until
 * the mod is fixed.
 */
public class QaGlassCreate1114cBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/** Six sand and a coal in the builder's chest, four panes asked: the glass is fired first, sixteen panes from one coal. */
	//$ gametest_ticks_batch AREA '4000' '"qaGlassCreate1114cPanes"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaGlassCreate1114cPanes")
	public void withCreatePanesComeFromSandOnOneCoal(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.GLASS_PANE, 4, 1, "qa1114cpanes"),
			Blocks.CHEST.defaultBlockState(), new ItemStack(Items.SAND, 6), new ItemStack(Items.COAL, 1));
		helper.setBlock(QaCrafterOwnModdedStore1111eCompatTests.OWN, Blocks.CHEST);
		Villager blower = QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper);
		// six sand fire six glass, cut into sixteen panes: 6 x 8 = 16 x 3
		QaCrafterOwnModdedStore1111eCompatTests.Law panes = QaCrafterOwnModdedStore1111eCompatTests.Law.of("sand, glass and panes",
			Items.SAND, 8, Items.GLASS, 8, Items.GLASS_PANE, 3);
		QaCrafterOwnModdedStore1111eCompatTests.watch(helper, a, blower.getUUID(), List.of(panes),
			(start, end) -> {
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.SAND, 6);
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.COAL, 1);
				QaCrafterOwnModdedStore1111eCompatTests.exactly(helper, end, Items.GLASS_PANE, 16);
			});
		helper.runAfterDelay(3900, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			var chest = helper.getLevel().getBlockEntity(helper.absolutePos(new net.minecraft.core.BlockPos(2, 2, 4)));
			StringBuilder held = new StringBuilder();
			if (chest instanceof net.minecraft.world.Container c) {
				for (int i = 0; i < c.getContainerSize(); i++) {
					if (!c.getItem(i).isEmpty()) {
						held.append(c.getItem(i)).append(' ');
					}
				}
			}
			throw new net.minecraft.gametest.framework.GameTestAssertException("four panes from six sand and a coal weren't built in 3900 ticks; the builder's line: '" + text
				+ "'; his chest: " + held + "; the Glassblower's bag/inventory: " + blower.getInventory());
		});
	}
}
