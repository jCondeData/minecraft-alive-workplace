package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * B873: a block placed from a bucket gives the empty bucket back, as it does for a player. The builder returned only an
 * item's crafting remainder, which the water and lava buckets have and vanilla's powder snow bucket doesn't, so every
 * powder snow block a paid build placed cost an iron bucket.
 */
public class PowderSnowBucketGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** Two scanned powder snow blocks built back from a chest of exactly two powder snow buckets give two buckets back. */
	//$ gametest_ticks_batch HUGE '3000' '"b873PowderSnow"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "b873PowderSnow")
	public void paidPowderSnowGivesTheBucketsBack(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.STONE);
		s.put(1, 0, 0, Blocks.STONE);
		s.put(0, 1, 0, Blocks.POWDER_SNOW);
		s.put(1, 1, 0, Blocks.POWDER_SNOW);
		QaScanRebuild1102dGameTests.Built b = QaScanItemless1103GameTests.paidRoundTrip(helper, s);
		ServerLevel level = helper.getLevel();
		Container chest = QaScanItemless1103GameTests.stock(helper, b.b().site().plan(level).materials());
		helper.assertTrue(chest.countItem(Items.POWDER_SNOW_BUCKET) == 2, "the list should ask 2 powder snow buckets, the chest holds "
			+ chest.countItem(Items.POWDER_SNOW_BUCKET));
		helper.succeedWhen(() -> {
			b.done(helper);
			Villager builder = level.getEntitiesOfClass(Villager.class, helper.getBounds()).stream().findFirst().orElse(null);
			int buckets = QaScanItemless1103GameTests.held(chest, builder, Items.BUCKET);
			helper.assertTrue(buckets == 2, "of 2 powder snow buckets poured, " + buckets + " empty buckets came back");
			int full = QaScanItemless1103GameTests.held(chest, builder, Items.POWDER_SNOW_BUCKET);
			helper.assertTrue(full == 0, full + " powder snow buckets were never poured");
		});
	}

	/** Every bucket a block is placed from leaves an empty bucket; the empty bucket and ordinary items leave nothing. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyPouredBucketLeavesAnEmptyOne(GameTestHelper helper) {
		helper.assertTrue(MaterialRules.leftAfterPlacing(Items.POWDER_SNOW_BUCKET) == Items.BUCKET, "powder snow bucket");
		helper.assertTrue(MaterialRules.leftAfterPlacing(Items.WATER_BUCKET) == Items.BUCKET, "water bucket");
		helper.assertTrue(MaterialRules.leftAfterPlacing(Items.LAVA_BUCKET) == Items.BUCKET, "lava bucket");
		helper.assertTrue(MaterialRules.leftAfterPlacing(Items.AXOLOTL_BUCKET) == Items.BUCKET, "axolotl bucket");
		helper.assertTrue(MaterialRules.leftAfterPlacing(Items.BUCKET) == null, "an empty bucket gave something back");
		helper.assertTrue(MaterialRules.leftAfterPlacing(Items.STONE) == null, "stone gave something back");
		helper.succeed();
	}
}
