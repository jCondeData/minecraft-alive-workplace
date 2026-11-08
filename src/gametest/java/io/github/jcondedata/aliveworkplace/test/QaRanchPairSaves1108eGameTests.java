package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.ranch.RancherWork;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-e-1108-0413, saves; B1021's fix, the shared RanchWork): a Rancher saved and loaded in the middle of breeding a
 * pair (golden carrots in his bag, or one of the pair already fed) loses no food and still breeds the pair. Spec (B1021,
 * B979, villager-jobs): a pair is fed only with food for both; food he picked up goes back to the chests; nothing is
 * wasted or made from nothing. Horses stand still (no AI) until both are in love, as in QaRanchPairs1107cBugTests.
 */
public class QaRanchPairSaves1108eGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos TROUGH = new BlockPos(11, 2, 11);
	private static final BlockPos CHEST = new BlockPos(11, 2, 13);

	/** As a save and load of the world would: the villager from his save (his bag with him), his brain made anew. */
	private static Villager reload(GameTestHelper helper, Villager old) {
		ServerLevel level = helper.getLevel();
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the rancher wasn't saved");
		old.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the rancher didn't load as a villager: " + back);
		level.addFreshEntity(back);
		return (Villager) back;
	}

	private static int carrotsOnTheGround(GameTestHelper helper) {
		return helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).filter(s -> s.is(Items.GOLDEN_CARROT)).mapToInt(ItemStack::getCount).sum();
	}

	/**
	 * Two tamed horses 3 apart, {@code carrots} golden carrots in the chest and a Rancher, reloaded once when
	 * {@code fedBeforeReload} of the pair are in love and he carries 2 carrots (none fed) or 1 is fed. Checks at
	 * {@code checkAt}: a foal when {@code foal}, and every carrot accounted for (chest + bag + ground + the feedings).
	 */
	private static void pairWithReload(GameTestHelper helper, int carrots, int fedBeforeReload, boolean foal, int checkAt) {
		Leftovers.clear(helper);
		Horse a = helper.spawn(EntityType.HORSE, new BlockPos(5, 2, 5));
		Horse b = helper.spawn(EntityType.HORSE, new BlockPos(5, 2, 8));
		for (Horse h : new Horse[] {a, b}) {
			h.setTamed(true);
			h.setNoAi(true);
		}
		helper.setDayTime(2000);
		helper.setBlock(TROUGH, ModBlocks.FEED_TROUGH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.GOLDEN_CARROT, carrots));
		Villager[] rancher = {helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12))};
		Jobs.employ(helper.getLevel(), rancher[0], helper.absolutePos(TROUGH), ModVillagers.FEED_TROUGH_POI, ModVillagers.RANCHER);
		helper.assertTrue(RancherWork.isRancher(rancher[0]), "not a rancher");
		boolean[] wasInLove = {false, false};
		int[] feedings = {0};
		long[] reloadedAt = {-1};
		boolean[] letGo = {false};
		helper.onEachTick(() -> {
			Horse[] pair = {a, b};
			int inLove = 0;
			for (int i = 0; i < 2; i++) {
				boolean now = pair[i].isInLove();
				if (now && !wasInLove[i]) {
					feedings[0]++;
				}
				wasInLove[i] = now;
				inLove += now ? 1 : 0;
			}
			int carrying = ModAttachments.BUILDER_BAG.getOrCreate(rancher[0]).count(Items.GOLDEN_CARROT);
			boolean moment = fedBeforeReload == 0 ? inLove == 0 && carrying == 2 : inLove == 1 && feedings[0] == 1;
			if (reloadedAt[0] < 0 && moment) {
				reloadedAt[0] = helper.getTick();
				rancher[0] = reload(helper, rancher[0]);
				helper.assertTrue(RancherWork.isRancher(rancher[0]), "not a rancher after loading again");
			}
			if (!letGo[0] && inLove == 2) {
				letGo[0] = true;
				a.setNoAi(false);
				b.setNoAi(false);
			}
		});
		helper.runAtTickTime(checkAt, () -> {
			long foals = helper.getEntities(EntityType.HORSE).stream().filter(Horse::isBaby).count();
			int inChest = chest.countItem(Items.GOLDEN_CARROT);
			int inBag = ModAttachments.BUILDER_BAG.getOrCreate(rancher[0]).count(Items.GOLDEN_CARROT);
			int ground = carrotsOnTheGround(helper);
			String state = carrots + " golden carrots, reloaded at tick " + reloadedAt[0] + " with " + fedBeforeReload + " of the pair fed: foals " + foals
				+ ", feedings " + feedings[0] + ", carrots in the chest " + inChest + ", in his bag " + inBag + ", on the ground " + ground;
			helper.assertTrue(reloadedAt[0] >= 0, "the moment to reload never came: " + state);
			helper.assertTrue(inChest + inBag + ground + feedings[0] == carrots, "carrots lost or made: " + state);
			helper.assertTrue(!foal || foals >= 1, "no foal: " + state);
			helper.succeed();
		});
	}

	/** Reloaded while carrying the pair's 2 carrots, none fed yet: both are fed after the load and they have a foal. */
	//$ gametest_ticks_batch AREA '2400' '"qaRanchSaveCarrying"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaRanchSaveCarrying")
	public void aRancherReloadedCarryingThePairsFoodStillBreedsThem(GameTestHelper helper) {
		pairWithReload(helper, 2, 0, true, 2000);
	}

	/** 4 carrots, reloaded after feeding the first of the pair: no carrot is lost, and the pair is bred later (a foal). */
	//$ gametest_ticks_batch AREA '3600' '"qaRanchSaveHalfFedFour"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaRanchSaveHalfFedFour")
	public void aRancherReloadedBetweenThePairLosesNoCarrotAndBreedsThemLater(GameTestHelper helper) {
		pairWithReload(helper, 4, 1, true, 3200);
	}

	/** Exactly 2 carrots, reloaded after feeding the first: the carrot in his bag isn't lost or doubled (it goes back to the chest). */
	//$ gametest_ticks_batch AREA '2000' '"qaRanchSaveHalfFedTwo"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaRanchSaveHalfFedTwo")
	public void aRancherReloadedBetweenThePairWithTwoCarrotsKeepsTheSecond(GameTestHelper helper) {
		pairWithReload(helper, 2, 1, false, 1500);
	}
}
