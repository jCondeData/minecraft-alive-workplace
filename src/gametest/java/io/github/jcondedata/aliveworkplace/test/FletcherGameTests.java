package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Village;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Fletchers make the guards' bows and arrows; guards shoot the special ones. */
public class FletcherGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos POST = new BlockPos(19, 2, 19);
	private static final BlockPos POST_CHEST = new BlockPos(19, 2, 17);

	/** A guard without a bow gets one, made from the sticks and string in the fletcher's chest. */
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "fletcher_bow")
	public void fletcherMakesABowForTheGuard(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		helper.setDayTime(2000);
		helper.setBlock(TABLE, Blocks.FLETCHING_TABLE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.STICK, 3));
		chest.setItem(1, new ItemStack(Items.STRING, 3));
		Villager fletcher = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), fletcher, helper.absolutePos(TABLE), PoiTypes.FLETCHER, VillagerProfession.FLETCHER);
		helper.setBlock(POST, ModBlocks.GUARD_POST);
		helper.setBlock(POST_CHEST, Blocks.CHEST);
		Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 18));
		Jobs.employ(helper.getLevel(), guard, helper.absolutePos(POST), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		Container guardsChest = helper.getBlockEntity(POST_CHEST);
		helper.succeedWhen(() -> {
			boolean got = guard.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.BOW) || guardsChest.countItem(Items.BOW) == 1;
			helper.assertTrue(got, "no bow for the guard");
			Village.RADIUS = 0;
		});
	}

	/** A guard with a bow but no special arrows gets spectral ones: glowstone and arrows from the fletcher's chest. */
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "fletcher_arrows")
	public void fletcherMakesSpectralArrowsForTheGuard(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		helper.setDayTime(2000);
		helper.setBlock(TABLE, Blocks.FLETCHING_TABLE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.GLOWSTONE_DUST, 16));
		chest.setItem(1, new ItemStack(Items.ARROW, 4));
		Villager fletcher = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), fletcher, helper.absolutePos(TABLE), PoiTypes.FLETCHER, VillagerProfession.FLETCHER);
		helper.setBlock(POST, ModBlocks.GUARD_POST);
		helper.setBlock(POST_CHEST, Blocks.CHEST);
		Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 18));
		Jobs.employ(helper.getLevel(), guard, helper.absolutePos(POST), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		guard.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW));
		Container guardsChest = helper.getBlockEntity(POST_CHEST);
		helper.succeedWhen(() -> {
			int arrows = guardsChest.countItem(Items.SPECTRAL_ARROW) + io.github.jcondedata.aliveworkplace.guard.Guards.quiver(guard);
			helper.assertTrue(arrows == 8, "spectral arrows for the guard: " + arrows);
			Village.RADIUS = 0;
		});
	}

	/** A guard with a bow takes the spectral arrows from their chest and shoots them: the husk glows. */
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "guard_spectral")
	public void guardShootsSpectralArrows(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos post = new BlockPos(12, 2, 12);
		BlockPos postChest = new BlockPos(12, 2, 14);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		helper.setBlock(postChest, Blocks.CHEST);
		Container chest = helper.getBlockEntity(postChest);
		chest.setItem(0, new ItemStack(Items.SPECTRAL_ARROW, 8));
		Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(13, 2, 13));
		Jobs.employ(helper.getLevel(), guard, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		guard.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW));
		Husk[] husk = new Husk[1];
		// Once the guard has the arrows (they go for them straight away), a husk turns up a few steps off.
		helper.onEachTick(() -> {
			if (husk[0] == null && chest.countItem(Items.SPECTRAL_ARROW) == 0) {
				husk[0] = helper.spawn(EntityType.HUSK, new BlockPos(12, 2, 21));
				husk[0].setNoAi(true);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(husk[0] != null && (husk[0].hasEffect(MobEffects.GLOWING) || !husk[0].isAlive() && io.github.jcondedata.aliveworkplace.guard.Guards.quiver(guard) < 8),
				"no spectral arrow hit the husk: quiver " + io.github.jcondedata.aliveworkplace.guard.Guards.quiver(guard)
				+ (husk[0] == null ? "" : ", husk health " + husk[0].getHealth() + " alive " + husk[0].isAlive())
				+ ", guard at " + helper.relativePos(guard.blockPosition()) + ", fighting " + io.github.jcondedata.aliveworkplace.guard.GuardCombat.isFighting(guard));
		});
	}
}
