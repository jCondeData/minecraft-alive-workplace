package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Law;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane c, 2026-11-15), verifying B1254 and B1255 (the Glassblower's panes with Create loaded) from glassblower.md:
 * "6 sand and a coal make 16 panes on that one coal", "a coal or charcoal for every eight fired (nine glass take two
 * coal)", "the line names the sand", stained panes with a dye, sand and coal in his own chests used too, and "a way that
 * loops back is now only taken from what's to hand". The corners the bug classes leave out: each side of the 6-sand,
 * 1-coal boundary, the coal boundary at 12 glass, stained panes, the sand and the coal in different chests, and Create's
 * Tiled Glass Panes on hand.
 */
public class QaGlassLoopEdges1115cCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final int LOOK = 1000;

	private static Asker ask(GameTestHelper helper, Block block, int dx, int dz, String what, ItemStack... builders) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, block, dx, dz, "qa1115c" + what),
			Blocks.CHEST.defaultBlockState(), builders);
		helper.setBlock(QaCrafterOwnModdedStore1111eCompatTests.OWN, Blocks.CHEST);
		return a;
	}

	private static String site(GameTestHelper helper, Asker a) {
		BuildSite live = BuildSiteManager.get(helper.getLevel()).get(a.site().id());
		return live == null ? "done" : live.stage() + "/" + live.status() + " missing " + live.missing();
	}

	/** Fails with the builder's line just before the timeout, so a build that never finishes says why. */
	private static void deadline(GameTestHelper helper, Asker a, int at, String what) {
		helper.runAfterDelay(at, () -> {
			throw new GameTestAssertException(what + " never built; line '" + QaCrafterOwnModdedStore1111eCompatTests.line(helper, a) + "'; site " + site(helper, a));
		});
	}

	/** The line after {@link #LOOK} ticks is {@code expected} word for word, and the build isn't done. */
	private static void lineIs(GameTestHelper helper, Asker a, String expected) {
		helper.runAfterDelay(LOOK, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue(expected.equals(text), "the builder's line: '" + text + "', expected '" + expected + "'; site " + site(helper, a));
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(a.site().id()) != null, "the build finished, yet it was short");
			helper.succeed();
		});
	}

	/** One sand under the boundary: 5 sand and 1 coal in the builder's chest, 16 panes asked. The line names the one Sand, not the coal. */
	//$ gametest_ticks_batch AREA '1100' '"qaGlassEdges1115cFiveSand"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qaGlassEdges1115cFiveSand")
	public void fiveSandAndOneCoalAreOneSandShort(GameTestHelper helper) {
		Asker a = ask(helper, Blocks.GLASS_PANE, 4, 4, "fivesand", new ItemStack(Items.SAND, 5), new ItemStack(Items.COAL, 1));
		QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper);
		lineIs(helper, a, "Can't be made yet: short of 1 Sand to make 16 Glass Panes");
	}

	/** The coal side of the boundary: 6 sand and no fuel, 16 panes asked. The line names the one Coal. */
	//$ gametest_ticks_batch AREA '1100' '"qaGlassEdges1115cNoCoal"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qaGlassEdges1115cNoCoal")
	public void sixSandAndNoCoalAreOneCoalShort(GameTestHelper helper) {
		Asker a = ask(helper, Blocks.GLASS_PANE, 4, 4, "nocoal", new ItemStack(Items.SAND, 6));
		QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper);
		lineIs(helper, a, "Can't be made yet: short of 1 Coal to make 16 Glass Panes");
	}

	/** 32 panes take 12 glass, so two coal ("a coal for every eight fired"): 12 sand and 2 coal make them and burn both. */
	//$ gametest_ticks_batch AREA '8000' '"qaGlassEdges1115cTwelve"'
	@GameTest(template = AREA, timeoutTicks = 8000, batch = "qaGlassEdges1115cTwelve")
	public void twelveSandAndTwoCoalMakeThirtyTwoPanes(GameTestHelper helper) {
		Asker a = ask(helper, Blocks.GLASS_PANE, 4, 8, "twelve", new ItemStack(Items.SAND, 12), new ItemStack(Items.COAL, 2));
		Villager blower = QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper);
		deadline(helper, a, 7900, "32 panes from 12 sand and 2 coal");
		QaCrafterOwnModdedStore1111eCompatTests.watch(helper, a, blower.getUUID(),
			List.of(Law.of("sand, glass and panes (6 glass make 16 panes)", Items.SAND, 8, Items.GLASS, 8, Items.GLASS_PANE, 3)), (start, end) -> {
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.SAND, 12);
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.COAL, 2);
				QaCrafterOwnModdedStore1111eCompatTests.exactly(helper, end, Items.GLASS_PANE, 32);
			});
	}

	/** The same 32 panes with 12 sand and only 1 coal: the twelve glass need a second coal, and the line says so (never the sand). */
	//$ gametest_ticks_batch AREA '1100' '"qaGlassEdges1115cTwelveOneCoal"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qaGlassEdges1115cTwelveOneCoal")
	public void twelveSandAndOneCoalAreACoalShort(GameTestHelper helper) {
		Asker a = ask(helper, Blocks.GLASS_PANE, 4, 8, "twelveone", new ItemStack(Items.SAND, 12), new ItemStack(Items.COAL, 1));
		QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper);
		helper.runAfterDelay(LOOK, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue(text != null && text.startsWith("Can't be made yet: short of 1 Coal to make ") && text.endsWith(" Glass Panes")
				&& !text.contains("Sand") && !text.contains("Tiled"), "the builder's line: '" + text + "', expected 'short of 1 Coal to make ... Glass Panes'; site "
				+ site(helper, a));
			helper.succeed();
		});
	}

	/** The sand in the builder's chest, the one coal in the Glassblower's own chest by his kiln: 16 panes, his coal burned. */
	//$ gametest_ticks_batch AREA '5000' '"qaGlassEdges1115cSplit"'
	@GameTest(template = AREA, timeoutTicks = 5000, batch = "qaGlassEdges1115cSplit")
	public void sandInTheBuildersChestCoalInHisOwnMakeSixteenPanes(GameTestHelper helper) {
		Asker a = ask(helper, Blocks.GLASS_PANE, 4, 4, "split", new ItemStack(Items.SAND, 6));
		Villager blower = QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper, new ItemStack(Items.COAL, 1));
		deadline(helper, a, 4900, "16 panes from the builder's 6 sand and the Glassblower's coal");
		QaCrafterOwnModdedStore1111eCompatTests.watch(helper, a, blower.getUUID(),
			List.of(Law.of("sand, glass and panes (6 glass make 16 panes)", Items.SAND, 8, Items.GLASS, 8, Items.GLASS_PANE, 3)), (start, end) -> {
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.SAND, 6);
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.COAL, 1);
				QaCrafterOwnModdedStore1111eCompatTests.exactly(helper, end, Items.GLASS_PANE, 16);
			});
	}

	/** Eight red stained panes asked, 8 sand, 1 coal and 1 red dye in the builder's chest: built, one coal and the one dye used. */
	//$ gametest_ticks_batch AREA '6000' '"qaGlassEdges1115cRed"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaGlassEdges1115cRed")
	public void redStainedPanesFromSandCoalAndADye(GameTestHelper helper) {
		Asker a = ask(helper, Blocks.RED_STAINED_GLASS_PANE, 4, 2, "red", new ItemStack(Items.SAND, 8), new ItemStack(Items.COAL, 1),
			new ItemStack(Items.RED_DYE, 1));
		Villager blower = QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper);
		deadline(helper, a, 5900, "8 red stained panes from 8 sand, a coal and a red dye");
		// (weight 0: only so the dye and the sand are counted; how the glass and dye split between ways is his choice)
		QaCrafterOwnModdedStore1111eCompatTests.watch(helper, a, blower.getUUID(), List.of(Law.of("counted", Items.RED_DYE, 0, Items.SAND, 0)), (start, end) -> {
			QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.COAL, 1);
			QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.RED_DYE, 1);
		});
	}

	/**
	 * Create's Tiled Glass Panes on hand: 8 of them and a coal in the builder's chest, no sand anywhere, 8 glass panes
	 * asked. Firing them back into panes is a loop-back way, "only taken from what's to hand": here they are, so the 8
	 * panes are made on the one coal, and tiled and plain panes never add up to more than the 8 there were.
	 */
	//$ gametest_ticks_batch AREA '5000' '"qaGlassEdges1115cTiledPanes"'
	@GameTest(template = AREA, timeoutTicks = 5000, batch = "qaGlassEdges1115cTiledPanes")
	public void tiledGlassPanesOnHandAreFiredIntoPanes(GameTestHelper helper) {
		Item tiled = block("create:tiled_glass_pane").asItem();
		helper.assertTrue(tiled != Items.AIR, "setup: Create's tiled glass pane isn't loaded");
		Asker a = ask(helper, Blocks.GLASS_PANE, 4, 2, "tiledpanes", new ItemStack(tiled, 8), new ItemStack(Items.COAL, 1));
		Villager blower = QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper);
		deadline(helper, a, 4900, "8 panes from 8 Tiled Glass Panes on hand and a coal");
		QaCrafterOwnModdedStore1111eCompatTests.watch(helper, a, blower.getUUID(), List.of(Law.of("tiled and plain panes", tiled, 1, Items.GLASS_PANE, 1)),
			(start, end) -> {
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, tiled, 8);
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.COAL, 1);
				QaCrafterOwnModdedStore1111eCompatTests.exactly(helper, end, Items.GLASS_PANE, 8);
			});
	}
}
