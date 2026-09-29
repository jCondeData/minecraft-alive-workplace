package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Shepherds shear and breed the sheep around their loom. */
public class ShepherdGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos LOOM = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	/** With shears in the chest, the shepherd shears both sheep and puts the wool in the chest. */
	//$ gametest_ticks_batch AREA '1600' '"shepherd_shears"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "shepherd_shears")
	public void shepherdShearsTheSheep(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		Villager shepherd = shepherd(helper);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.SHEARS));
		Sheep a = helper.spawn(EntityType.SHEEP, new BlockPos(9, 2, 9));
		Sheep b = helper.spawn(EntityType.SHEEP, new BlockPos(11, 2, 8));
		a.setNoAi(true);
		b.setNoAi(true);
		helper.succeedWhen(() -> {
			helper.assertTrue(a.isSheared() && b.isSheared(), "sheep sheared: " + a.isSheared() + ", " + b.isSheared());
			int wool = 0;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				if (chest.getItem(i).is(ItemTags.WOOL)) {
					wool += chest.getItem(i).getCount();
				}
			}
			helper.assertTrue(wool >= 2, "wool in the chest: " + wool);
			helper.assertTrue(ModAttachments.ANIMALS_SHEARED.getOrElse(shepherd, 0) == 2, "sheared " + ModAttachments.ANIMALS_SHEARED.getOrElse(shepherd, 0));
		});
	}

	/** With wheat in the chest and only two sheep, the shepherd feeds them to breed. */
	//$ gametest_ticks_batch AREA '1200' '"shepherd_breeds"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "shepherd_breeds")
	public void shepherdFeedsTheSheepToBreed(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		shepherd(helper);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.WHEAT, 4));
		Sheep a = helper.spawn(EntityType.SHEEP, new BlockPos(9, 2, 9));
		Sheep b = helper.spawn(EntityType.SHEEP, new BlockPos(10, 2, 9));
		a.setNoAi(true);
		b.setNoAi(true);
		helper.succeedWhen(() -> {
			helper.assertTrue(a.isInLove() && b.isInLove(), "in love: " + a.isInLove() + ", " + b.isInLove());
			helper.assertTrue(chest.countItem(Items.WHEAT) == 2, "wheat left: " + chest.countItem(Items.WHEAT));
		});
	}

	private static Villager shepherd(GameTestHelper helper) {
		helper.setBlock(LOOM, Blocks.LOOM);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager shepherd = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), shepherd, helper.absolutePos(LOOM), PoiTypes.SHEPHERD, VillagerProfession.SHEPHERD);
		return shepherd;
	}
}
