package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.ranch.RancherWork;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane qa-lane-c-1108-0352) for B1021, written from the spec: with exactly 2 breeding foods both of a pair are fed
 * (only the second needs the one left in hand), for the Rancher, the Shepherd and the Herder alike (they share the
 * pairing); 1 food feeds nobody; 3 foods feed one pair and keep the last; and the one in hand is never lost: fed to the
 * nearest ready third when the mate dies, or brought back to the chest when the fed one dies. Animals are held still (no
 * AI) so the distances are the test's.
 */
public class QaRanchTwoFood1108cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos TROUGH = new BlockPos(11, 2, 11);
	private static final BlockPos CHEST = new BlockPos(11, 2, 13);
	/** The loom or smoker of a Shepherd or Herder, and its chest. */
	private static final BlockPos STATION = new BlockPos(2, 2, 2);
	private static final BlockPos STATION_CHEST = new BlockPos(2, 2, 4);

	/** The Shepherd with exactly 2 wheat and two shorn sheep 3 apart: both are fed and no wheat is left. */
	//$ gametest_ticks_batch AREA '1600' '"qaRanchTwoWheatShepherd"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaRanchTwoWheatShepherd")
	public void shepherdFeedsBothSheepWithExactlyTwoWheat(GameTestHelper helper) {
		Leftovers.clear(helper);
		Sheep a = helper.spawn(EntityType.SHEEP, new BlockPos(6, 2, 8));
		Sheep b = helper.spawn(EntityType.SHEEP, new BlockPos(9, 2, 8));
		for (Sheep s : List.of(a, b)) {
			s.setNoAi(true);
			s.setSheared(true);
		}
		Container chest = station(helper, Blocks.LOOM, new ItemStack(Items.WHEAT, 2));
		Villager shepherd = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), shepherd, helper.absolutePos(STATION), PoiTypes.SHEPHERD, VillagerProfession.SHEPHERD);
		bothFed(helper, a, b, chest, Items.WHEAT, "sheep");
	}

	/** The Herder (a butcher) with exactly 2 carrots and two pigs 3 apart: both are fed and no carrot is left. */
	//$ gametest_ticks_batch AREA '1600' '"qaRanchTwoCarrotsHerder"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaRanchTwoCarrotsHerder")
	public void herderFeedsBothPigsWithExactlyTwoCarrots(GameTestHelper helper) {
		Leftovers.clear(helper);
		Pig a = helper.spawn(EntityType.PIG, new BlockPos(6, 2, 8));
		Pig b = helper.spawn(EntityType.PIG, new BlockPos(9, 2, 8));
		a.setNoAi(true);
		b.setNoAi(true);
		Container chest = station(helper, Blocks.SMOKER, new ItemStack(Items.CARROT, 2));
		Villager herder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), herder, helper.absolutePos(STATION), PoiTypes.BUTCHER, VillagerProfession.BUTCHER);
		bothFed(helper, a, b, chest, Items.CARROT, "pig");
	}

	/** The Rancher with a single golden carrot and two horses 3 apart: neither is fed and the carrot stays in the chest. */
	//$ gametest_ticks_batch AREA '1200' '"qaRanchOneCarrot"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaRanchOneCarrot")
	public void rancherFeedsNobodyWithOneCarrot(GameTestHelper helper) {
		Leftovers.clear(helper);
		Horse a = stillHorse(helper, new BlockPos(5, 2, 5));
		Horse b = stillHorse(helper, new BlockPos(5, 2, 8));
		Container chest = rancher(helper, new ItemStack(Items.GOLDEN_CARROT, 1));
		helper.onEachTick(() -> {
			if (a.isInLove() || b.isInLove()) {
				helper.fail("with 1 golden carrot a horse was fed alone (A " + a.isInLove() + ", B " + b.isInLove() + ")");
			}
		});
		helper.runAtTickTime(1000, () -> {
			int left = chest.countItem(Items.GOLDEN_CARROT);
			helper.assertTrue(left == 1, "golden carrots left: " + left + " (expected the 1, untouched)");
			helper.succeed();
		});
	}

	/**
	 * Two pairs 3 apart, 12 blocks from each other, and 3 golden carrots: one pair is fed (2 carrots), the other gets
	 * nothing (1 isn't enough for a pair), and 1 carrot stays in the chest.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qaRanchThreeCarrots"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaRanchThreeCarrots")
	public void rancherFeedsOnePairOfTwoWithThreeCarrotsAndKeepsTheLast(GameTestHelper helper) {
		Leftovers.clear(helper);
		Horse a = stillHorse(helper, new BlockPos(3, 2, 3));
		Horse b = stillHorse(helper, new BlockPos(3, 2, 6));
		Horse c = stillHorse(helper, new BlockPos(15, 2, 3));
		Horse d = stillHorse(helper, new BlockPos(15, 2, 6));
		helper.assertTrue(a.distanceTo(c) > 8 && b.distanceTo(d) > 8 && a.distanceTo(d) > 8 && b.distanceTo(c) > 8, "set-up distances");
		Container chest = rancher(helper, new ItemStack(Items.GOLDEN_CARROT, 3));
		Horse[] horses = {a, b, c, d};
		boolean[] ever = new boolean[4];
		helper.onEachTick(() -> {
			int fed = 0;
			for (int i = 0; i < 4; i++) {
				ever[i] |= horses[i].isInLove();
				fed += ever[i] ? 1 : 0;
			}
			if (fed > 2) {
				helper.fail("3 golden carrots fed " + fed + " horses");
			}
		});
		helper.runAtTickTime(1500, () -> {
			boolean first = ever[0] && ever[1];
			boolean second = ever[2] && ever[3];
			helper.assertTrue(first ^ second, "exactly one pair should be fed: A/B " + ever[0] + "/" + ever[1] + ", C/D " + ever[2] + "/" + ever[3]);
			int left = chest.countItem(Items.GOLDEN_CARROT);
			helper.assertTrue(left == 1, "golden carrots left: " + left + " (expected 1 of 3)");
			helper.succeed();
		});
	}

	/**
	 * Exactly 2 golden carrots: once the first of a pair is fed its mate dies, and a ready third stands within 8 of the
	 * one in love. The carrot left in hand goes to the third: both carrots fed, two horses in love.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qaRanchTwoCarrotsMateDies"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaRanchTwoCarrotsMateDies")
	public void rancherFeedsTheThirdWithTheLastCarrotWhenTheMateDies(GameTestHelper helper) {
		Leftovers.clear(helper);
		Horse a = stillHorse(helper, new BlockPos(5, 2, 5));
		Horse b = stillHorse(helper, new BlockPos(5, 2, 8));
		Horse c = stillHorse(helper, new BlockPos(10, 2, 6));
		helper.assertTrue(c.distanceTo(a) < 8 && c.distanceTo(b) < 8 && c.distanceTo(a) > 3 && c.distanceTo(b) > 3, "set-up distances");
		Container chest = rancher(helper, new ItemStack(Items.GOLDEN_CARROT, 2));
		Horse[] fed = {null};
		helper.onEachTick(() -> {
			if (fed[0] == null && (a.isInLove() || b.isInLove())) {
				fed[0] = a.isInLove() ? a : b;
				Horse mate = fed[0] == a ? b : a;
				helper.assertTrue(!mate.isInLove(), "both of the pair were fed in one tick");
				mate.kill();
			}
			if (fed[0] == null && c.isInLove()) {
				helper.fail("horse C was fed before the pair 3 apart");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(fed[0] != null, "neither of the pair 3 apart was fed");
			helper.assertTrue(c.isInLove(), "horse C wasn't fed the last carrot after the mate died (carrots in the chest: " + chest.countItem(Items.GOLDEN_CARROT) + ")");
			helper.assertTrue(chest.countItem(Items.GOLDEN_CARROT) == 0, "golden carrots left: " + chest.countItem(Items.GOLDEN_CARROT) + " (expected 0)");
		});
	}

	/**
	 * Exactly 2 golden carrots: the first of a pair is fed, then dies, and no other horse is near. Its mate is never fed
	 * alone, and the carrot left in hand comes back to the chest (1 spent, 1 back).
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaRanchTwoCarrotsFedDies"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaRanchTwoCarrotsFedDies")
	public void rancherBringsTheLastCarrotBackWhenTheFedOneDies(GameTestHelper helper) {
		Leftovers.clear(helper);
		Horse a = stillHorse(helper, new BlockPos(5, 2, 5));
		Horse b = stillHorse(helper, new BlockPos(5, 2, 8));
		Container chest = rancher(helper, new ItemStack(Items.GOLDEN_CARROT, 2));
		Horse[] fed = {null};
		helper.onEachTick(() -> {
			if (fed[0] == null && (a.isInLove() || b.isInLove())) {
				fed[0] = a.isInLove() ? a : b;
				Horse mate = fed[0] == a ? b : a;
				helper.assertTrue(!mate.isInLove(), "both of the pair were fed in one tick");
				fed[0].kill();
			}
			if (fed[0] != null) {
				Horse mate = fed[0] == a ? b : a;
				if (mate.isInLove()) {
					helper.fail("the mate was fed alone after the one in love died");
				}
			}
		});
		helper.runAtTickTime(1400, () -> {
			helper.assertTrue(fed[0] != null, "neither of the pair 3 apart was fed");
			int left = chest.countItem(Items.GOLDEN_CARROT);
			helper.assertTrue(left == 1, "golden carrots in the chest: " + left + " (expected 1: one fed, the one in hand brought back)");
			helper.succeed();
		});
	}

	/** Both are fed, then no food of the kind is left in the chest. */
	private static void bothFed(GameTestHelper helper, Animal a, Animal b, Container chest, Item food, String what) {
		boolean[] fed = {false, false};
		helper.onEachTick(() -> {
			fed[0] |= a.isInLove();
			fed[1] |= b.isInLove();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(fed[0] && fed[1], "with exactly 2 " + food + ": " + what + " A fed " + fed[0] + ", " + what + " B fed " + fed[1]
				+ ", left in the chest " + chest.countItem(food));
			helper.assertTrue(chest.countItem(food) == 0, food + " left in the chest: " + chest.countItem(food) + " (expected 0)");
		});
	}

	private static Container station(GameTestHelper helper, Block block, ItemStack... food) {
		helper.setDayTime(2000);
		helper.setBlock(STATION, block);
		helper.setBlock(STATION_CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(STATION_CHEST);
		for (int i = 0; i < food.length; i++) {
			chest.setItem(i, food[i]);
		}
		return chest;
	}

	/** A tamed horse that stays where it's put (no AI). */
	private static Horse stillHorse(GameTestHelper helper, BlockPos pos) {
		Horse horse = helper.spawn(EntityType.HORSE, pos);
		horse.setTamed(true);
		horse.setNoAi(true);
		return horse;
	}

	private static Container rancher(GameTestHelper helper, ItemStack... chest) {
		helper.setDayTime(2000);
		helper.setBlock(TROUGH, ModBlocks.FEED_TROUGH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(TROUGH), ModVillagers.FEED_TROUGH_POI, ModVillagers.RANCHER);
		helper.assertTrue(RancherWork.isRancher(villager), "not a rancher");
		return c;
	}
}
