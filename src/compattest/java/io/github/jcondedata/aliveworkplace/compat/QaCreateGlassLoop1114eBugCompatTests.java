package io.github.jcondedata.aliveworkplace.compat;
import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Law;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane E, 2026-11-14), failing: with Create loaded the Glassblower's glass panes loop through Create's Tiled Glass Pane
 * (glass -> Tiled Glass -> Tiled Glass Pane -> fired back into Glass Pane), which B1196's one-step loop-back ordering misses:
 * 6 sand and 1 coal never make 16 panes, 3 coal are burned for 16, and with no sand the builder's line is empty. The plain
 * suite does all three right (GlassblowerGameTests#theGlassblowerMakesPanesFromSand). Moves to main with the fix.
 */
public class QaCreateGlassLoop1114eBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final int LOOK = 1000;

	/**
	 * Glass panes: Create also fires Glass Panes back out of its Tiled Glass Pane. 6 sand and 3 coal in his own chest, 16
	 * panes asked: the vanilla chain (6 sand fired into 6 glass, crafted into 16 panes) on one coal.
	 */
	//$ gametest_ticks_batch AREA '5000' '"qaGlassLoop1114ePanes"'
	@GameTest(template = AREA, timeoutTicks = 5000, batch = "qaGlassLoop1114ePanes")
	public void sixteenPanesFromSixSandBurnOneCoal(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.GLASS_PANE, 4, 4, "panes"),
			Blocks.CHEST.defaultBlockState());
		helper.setBlock(QaCrafterOwnModdedStore1111eCompatTests.OWN, Blocks.CHEST);
		Villager blower = QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper, new ItemStack(Items.SAND, 6), new ItemStack(Items.COAL, 3));
		QaCrafterOwnModdedStore1111eCompatTests.watch(helper, a, blower.getUUID(),
			List.of(Law.of("sand, glass and panes (6 glass make 16 panes)", Items.SAND, 8, Items.GLASS, 8, Items.GLASS_PANE, 3)), (start, end) -> {
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.SAND, 6);
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.COAL, 1);
				QaCrafterOwnModdedStore1111eCompatTests.exactly(helper, end, Items.GLASS_PANE, 16);
				QaCrafterOwnModdedStore1111eCompatTests.exactly(helper, end, Items.GLASS, 0);
			});
	}

	/**
	 * The plain suite's pane set-up (GlassblowerGameTests#theGlassblowerMakesPanesFromSand: six sand and ONE coal make the
	 * panes) with Create loaded: 6 sand and 1 coal in the builder's chest, 16 panes asked: built, the sand and the coal used.
	 */
	//$ gametest_ticks_batch AREA '5000' '"qaGlassLoop1114ePanesOneCoal"'
	@GameTest(template = AREA, timeoutTicks = 5000, batch = "qaGlassLoop1114ePanesOneCoal")
	public void sixSandAndOneCoalMakeSixteenPanes(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.GLASS_PANE, 4, 4, "panes1"),
			Blocks.CHEST.defaultBlockState(), new ItemStack(Items.SAND, 6), new ItemStack(Items.COAL, 1));
		helper.setBlock(QaCrafterOwnModdedStore1111eCompatTests.OWN, Blocks.CHEST);
		Villager blower = QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper);
		helper.runAfterDelay(4800, () -> {
			throw new GameTestAssertException("16 panes from 6 sand and 1 coal never built; line '" + QaCrafterOwnModdedStore1111eCompatTests.line(helper, a)
				+ "'; site " + site(helper, a) + "; made " + ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0));
		});
		QaCrafterOwnModdedStore1111eCompatTests.watch(helper, a, blower.getUUID(),
			List.of(Law.of("sand, glass and panes (6 glass make 16 panes)", Items.SAND, 8, Items.GLASS, 8, Items.GLASS_PANE, 3)), (start, end) -> {
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.SAND, 6);
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.COAL, 1);
				QaCrafterOwnModdedStore1111eCompatTests.exactly(helper, end, Items.GLASS_PANE, 16);
			});
	}

	/** No sand, a coal in the builder's chest, 16 panes asked: the line names the 6 sand, never Create's tiled or framed glass. */
	//$ gametest_ticks_batch AREA '1100' '"qaGlassLoop1114ePaneLine"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qaGlassLoop1114ePaneLine")
	public void withoutSandThePaneLineNamesTheSand(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.GLASS_PANE, 4, 4, "paneline"),
			Blocks.CHEST.defaultBlockState(), new ItemStack(Items.COAL, 1));
		helper.setBlock(QaCrafterOwnModdedStore1111eCompatTests.OWN, Blocks.CHEST);
		QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper);
		helper.runAfterDelay(LOOK, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			// the plain suite's line for this set-up (9 panes there): "Can't be made yet: short of 6 Sand to make 9 Glass Panes"
			helper.assertTrue("Can't be made yet: short of 6 Sand to make 16 Glass Panes".equals(text), "the builder's line: '" + text
				+ "', expected 'Can't be made yet: short of 6 Sand to make 16 Glass Panes'; site " + site(helper, a));
			helper.succeed();
		});
	}

	private static String site(GameTestHelper helper, Asker a) {
		BuildSite live = BuildSiteManager.get(helper.getLevel()).get(a.site().id());
		return live == null ? "done" : live.stage() + "/" + live.status() + " missing " + live.missing();
	}

}
