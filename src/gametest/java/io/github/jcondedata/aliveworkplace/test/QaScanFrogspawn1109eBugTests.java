package io.github.jcondedata.aliveworkplace.test;

import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-e-1109-1913, B874 kin): a swamp pond scanned with the frogs' spawn on it. Frogspawn breaks into nothing for a
 * survival player (not even with Silk Touch), so its item is creative-only; the spec ("some blocks you cannot hold in
 * survival use their base material", docs/wiki/builder.md) means a list never asks it, or a paid build waits forever.
 */
public class QaScanFrogspawn1109eBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** A stone basin of water with frogspawn floating on it: the list asks no frogspawn, and asks the water's bucket. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1109eFrogspawn"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1109eFrogspawn")
	public void aScannedPondsFrogspawnIsNeverAsked(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				s.put(x, 0, z, Blocks.STONE);
				if (x != 1 || z != 1) {
					s.put(x, 1, z, Blocks.STONE);
				}
			}
		}
		s.put(1, 1, 1, Blocks.WATER);
		s.put(1, 2, 1, Blocks.FROGSPAWN);
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(), true);
		Map<Item, Integer> asked = b.b().site().plan(helper.getLevel()).materials();
		helper.assertTrue(!asked.containsKey(Items.FROGSPAWN), "the list " + asked + " asks frogspawn, which no survival player can hold");
		helper.assertTrue(asked.getOrDefault(Items.STONE, 0) == 17, "the list " + asked + " doesn't ask the basin's 17 stone");
		helper.succeedWhen(() -> QaScanItemless1103GameTests.finished(helper, b));
	}
}
