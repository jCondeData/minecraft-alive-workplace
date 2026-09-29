package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.brew.AlchemistWork;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Village;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;

/** Clerics brew potions for the guards; guards drink them. */
public class AlchemistGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos STAND = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos POST = new BlockPos(19, 2, 19);
	private static final BlockPos POST_CHEST = new BlockPos(19, 2, 17);

	/** Glass bottles filled at the cauldron, nether wart and a glistering melon slice: three potions of healing. */
	//$ gametest_ticks_batch AREA '2400' '"cleric_brews"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "cleric_brews")
	public void clericBrewsHealingPotions(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		Villager cleric = cleric(helper);
		helper.setBlock(new BlockPos(5, 2, 2), Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.GLASS_BOTTLE, 3));
		chest.setItem(1, new ItemStack(Items.NETHER_WART));
		chest.setItem(2, new ItemStack(Items.GLISTERING_MELON_SLICE));
		chest.setItem(3, new ItemStack(Items.BLAZE_POWDER));
		ItemStack healing = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
		helper.succeedWhen(() -> {
			int potions = 0;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				if (ItemStack.isSameItemSameComponents(chest.getItem(i), healing)) {
					potions += chest.getItem(i).getCount();
				}
			}
			helper.assertTrue(potions == 3, "healing potions in the chest: " + potions);
			helper.assertTrue(ModAttachments.POTIONS_BREWED.getOrElse(cleric, 0) == 3, "brewed " + ModAttachments.POTIONS_BREWED.getOrElse(cleric, 0));
		});
	}

	/** A cleric brings one of their potions to a guard who has none; a badly hurt guard drinks it. */
	//$ gametest_ticks_batch AREA '1600' '"cleric_delivers"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "cleric_delivers")
	public void clericBringsAPotionToTheGuard(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		helper.setDayTime(2000);
		cleric(helper);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, PotionContents.createItemStack(Items.POTION, Potions.HEALING));
		helper.setBlock(POST, ModBlocks.GUARD_POST);
		helper.setBlock(POST_CHEST, Blocks.CHEST);
		Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 18));
		Jobs.employ(helper.getLevel(), guard, helper.absolutePos(POST), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		guard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		Container guardsChest = helper.getBlockEntity(POST_CHEST);
		helper.succeedWhen(() -> {
			int got = Guards.potions(guard);
			for (int i = 0; i < guardsChest.getContainerSize(); i++) {
				if (AlchemistWork.isGuardPotion(guardsChest.getItem(i))) {
					got++;
				}
			}
			helper.assertTrue(got == 1, "potions for the guard: " + got);
			Village.RADIUS = 0;
		});
	}

	/** A guard below half health drinks the healing potion they carry. */
	//$ gametest_ticks_batch AREA '400' '"guard_drinks"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "guard_drinks")
	public void guardDrinksAHealingPotion(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(POST, ModBlocks.GUARD_POST);
		Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 18));
		Jobs.employ(helper.getLevel(), guard, helper.absolutePos(POST), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		ModAttachments.BUILDER_BAG.getOrCreate(guard).add(PotionContents.createItemStack(Items.POTION, Potions.HEALING));
		helper.runAfterDelay(5, () -> guard.setHealth(12f));
		helper.succeedWhen(() -> {
			helper.assertTrue(Guards.potions(guard) == 0, "the potion is still in the bag");
			helper.assertTrue(guard.getHealth() >= 16f, "health " + guard.getHealth());
		});
	}

	private static Villager cleric(GameTestHelper helper) {
		helper.setBlock(STAND, Blocks.BREWING_STAND);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager cleric = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), cleric, helper.absolutePos(STAND), PoiTypes.CLERIC, VillagerProfession.CLERIC);
		return cleric;
	}
}
