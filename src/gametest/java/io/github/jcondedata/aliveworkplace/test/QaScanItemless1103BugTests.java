package io.github.jcondedata.aliveworkplace.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (qa-lane-b-1103-0938, 2026-11-03): the Scan Tool round trips of {@link QaScanItemless1103GameTests} that fail on
 * main: infested stone, suspicious sand and a petrified oak slab ask items no survival player can hold (the spec: such
 * blocks use their base material, B874); and a powder snow bucket poured by a builder never gives its empty bucket back,
 * as a player's does and the builder's water and lava buckets do (B873). The bamboo shoot and chorus plant round trips
 * (B872, open) stay on tests/scan-itemless-1103 until that fix.
 */
public class QaScanItemless1103BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * Blocks a player finds but cannot hold in survival (the spec: "some blocks you cannot hold in survival use their base
	 * material"): infested stone (a mountain's silverfish stone), suspicious sand (a desert well's) and a petrified oak slab
	 * (a mineshaft's or village's old slab). The list asks stone, sand and an oak slab, never an item no player can get.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qa1103ilFound"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1103ilFound")
	public void scannedUnobtainableBlocksAskTheirBaseMaterial(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.INFESTED_STONE);
		s.put(1, 0, 0, Blocks.SUSPICIOUS_SAND);
		s.put(2, 0, 0, Blocks.PETRIFIED_OAK_SLAB);
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(), true);
		Map<Item, Integer> asked = b.b().site().plan(helper.getLevel()).materials();
		List<String> wrong = new ArrayList<>();
		for (Item impossible : List.of(Items.INFESTED_STONE, Items.SUSPICIOUS_SAND, Items.PETRIFIED_OAK_SLAB)) {
			if (asked.containsKey(impossible)) {
				wrong.add(impossible.toString());
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the list " + asked + " asks items no survival player can get: " + wrong);
		helper.assertTrue(asked.getOrDefault(Items.STONE, 0) == 1 && asked.getOrDefault(Items.SAND, 0) == 1
			&& asked.getOrDefault(Items.OAK_SLAB, 0) == 1, "the list asks " + asked + ", not 1 stone, 1 sand and 1 oak slab");
		helper.succeedWhen(() -> QaScanItemless1103GameTests.finished(helper, b));
	}

	/** Paid: two powder snow blocks poured from two powder snow buckets give both empty buckets back, as for a player. */
	//$ gametest_ticks_batch HUGE '4000' '"qa1103ilPowderPaid"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qa1103ilPowderPaid")
	public void paidPowderSnowGivesTheBucketsBack(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.STONE);
		s.put(1, 0, 0, Blocks.STONE);
		s.put(0, 1, 0, Blocks.POWDER_SNOW);
		s.put(1, 1, 0, Blocks.POWDER_SNOW);
		QaScanRebuild1102dGameTests.Built b = QaScanItemless1103GameTests.paidRoundTrip(helper, s);
		ServerLevel level = helper.getLevel();
		Container chest = QaScanItemless1103GameTests.stock(helper, b.b().site().plan(level).materials());
		helper.succeedWhen(() -> {
			b.done(helper);
			Villager builder = level.getEntitiesOfClass(Villager.class, helper.getBounds()).stream().findFirst().orElse(null);
			helper.assertTrue(QaScanItemless1103GameTests.held(chest, builder, Items.BUCKET) == 2, "of 2 powder snow buckets poured, " + QaScanItemless1103GameTests.held(chest, builder, Items.BUCKET)
				+ " empty buckets came back");
		});
	}
}
