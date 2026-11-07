package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.ranch.RancherWork;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Ranchers tame, saddle, dress and breed the horses (and llamas) round their Feed Trough. */
public class RancherGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos TROUGH = new BlockPos(11, 2, 11);
	private static final BlockPos CHEST = new BlockPos(11, 2, 13);

	/** A wild horse is broken in (a few tries) and then gets the saddle from the chest. */
	//$ gametest_ticks_batch AREA '2400' '"rancherTamesAndSaddlesAHorse"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "rancherTamesAndSaddlesAHorse")
	public void rancherTamesAndSaddlesAHorse(GameTestHelper helper) {
		Leftovers.clear(helper);
		Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(14, 2, 11));
		helper.assertTrue(!horse.isTamed(), "the horse starts wild");
		Villager rancher = rancher(helper, new ItemStack(Items.SADDLE));
		helper.succeedWhen(() -> {
			helper.assertTrue(horse.isTamed(), "the horse isn't tamed");
			helper.assertTrue(horse.isSaddled(), "the horse isn't saddled");
			helper.assertTrue(ModAttachments.HORSES_TAMED.getOrElse(rancher, 0) == 1, "horses tamed: "
				+ ModAttachments.HORSES_TAMED.getOrElse(rancher, 0));
		});
	}

	/** Two tamed horses fed golden carrots from the chest have a foal. */
	//$ gametest_ticks_batch AREA '2000' '"rancherBreedsTamedHorses"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "rancherBreedsTamedHorses")
	public void rancherBreedsTamedHorses(GameTestHelper helper) {
		Leftovers.clear(helper);
		for (int i = 0; i < 2; i++) {
			Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(14, 2, 10 + i * 2));
			horse.setTamed(true);
		}
		rancher(helper, new ItemStack(Items.GOLDEN_CARROT, 4));
		helper.succeedWhen(() -> {
			long foals = helper.getEntities(EntityType.HORSE).stream().filter(Horse::isBaby).count();
			helper.assertTrue(foals >= 1, "no foal");
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.GOLDEN_CARROT) == 2, "golden carrots left: " + chest.countItem(Items.GOLDEN_CARROT));
		});
	}

	/**
	 * Two tamed horses 12 blocks apart could never find each other in love (vanilla looks within 8 blocks), so the rancher
	 * spends no golden carrots on them (B979). They're held still so they can't wander together.
	 */
	//$ gametest_ticks_batch AREA '1000' '"rancherSkipsAPairTooFarApart"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "rancherSkipsAPairTooFarApart")
	public void rancherSkipsAPairTooFarApart(GameTestHelper helper) {
		Leftovers.clear(helper);
		Horse west = stillHorse(helper, new BlockPos(4, 2, 11));
		Horse east = stillHorse(helper, new BlockPos(16, 2, 11));
		helper.assertTrue(west.distanceTo(east) > 11.5, "the horses stand " + west.distanceTo(east) + " apart");
		rancher(helper, new ItemStack(Items.GOLDEN_CARROT, 2));
		helper.onEachTick(() -> {
			if (west.isInLove() || east.isInLove()) {
				helper.fail("a horse too far from the other was fed");
			}
		});
		helper.runAtTickTime(800, () -> {
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.GOLDEN_CARROT) == 2, "golden carrots left: " + chest.countItem(Items.GOLDEN_CARROT));
			helper.succeed();
		});
	}

	/**
	 * Of three ready horses, the two standing together are fed and have a foal; the one far off (held still, and spawned
	 * first so it would be the first one found) gets no carrot (B979).
	 */
	//$ gametest_ticks_batch AREA '2000' '"rancherFeedsThePairThatCanMeet"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "rancherFeedsThePairThatCanMeet")
	public void rancherFeedsThePairThatCanMeet(GameTestHelper helper) {
		Leftovers.clear(helper);
		Horse loner = stillHorse(helper, new BlockPos(3, 2, 4));
		for (int i = 0; i < 2; i++) {
			Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(16, 2, 13 + i * 2));
			horse.setTamed(true);
		}
		rancher(helper, new ItemStack(Items.GOLDEN_CARROT, 4));
		helper.onEachTick(() -> {
			if (loner.isInLove()) {
				helper.fail("the horse far from the others was fed");
			}
		});
		helper.succeedWhen(() -> {
			long foals = helper.getEntities(EntityType.HORSE).stream().filter(Horse::isBaby).count();
			helper.assertTrue(foals >= 1, "no foal");
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.GOLDEN_CARROT) == 2, "golden carrots left: " + chest.countItem(Items.GOLDEN_CARROT));
		});
	}

	/** A tamed horse that stays where it's put (no AI), so the distance between two can't change on its own. */
	private static Horse stillHorse(GameTestHelper helper, BlockPos pos) {
		Horse horse = helper.spawn(EntityType.HORSE, pos);
		horse.setTamed(true);
		horse.setNoAi(true);
		return horse;
	}

	/** A tamed llama gets a carpet from the chest. */
	//$ gametest_ticks_batch AREA '1600' '"rancherPutsACarpetOnALlama"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "rancherPutsACarpetOnALlama")
	public void rancherPutsACarpetOnALlama(GameTestHelper helper) {
		Leftovers.clear(helper);
		Llama llama = helper.spawn(EntityType.LLAMA, new BlockPos(14, 2, 11));
		llama.setTamed(true);
		rancher(helper, new ItemStack(Items.RED_CARPET, 2));
		helper.succeedWhen(() -> {
			helper.assertTrue(llama.getBodyArmorItem().is(Items.RED_CARPET), "the llama wears " + llama.getBodyArmorItem());
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.RED_CARPET) == 1, "carpets left: " + chest.countItem(Items.RED_CARPET));
		});
	}

	private static Villager rancher(GameTestHelper helper, ItemStack... chest) {
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
		return villager;
	}
}
