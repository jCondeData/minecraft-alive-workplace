package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Guards on a real (headless) server. */
public class GuardGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos POST = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	private static Villager guard(GameTestHelper helper, ItemStack... chest) {
		helper.setDayTime(2000); // all tests share the clock; guards fight at any hour anyway
		helper.setBlock(POST, ModBlocks.GUARD_POST);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(POST), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		return villager;
	}

	/** A guard gears up from the chest, takes on a husk and wins, without ever panicking. */
	@GameTest(template = AREA, timeoutTicks = 1200)
	public void guardDefeatsAHusk(GameTestHelper helper) {
		Villager guard = guard(helper, new ItemStack(Items.WOODEN_SWORD), new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_CHESTPLATE));
		helper.runAfterDelay(80, () -> helper.spawn(EntityType.HUSK, new BlockPos(12, 2, 12)));
		helper.onEachTick(() -> {
			if (guard.getBrain().isActive(Activity.PANIC)) {
				helper.fail("the guard panicked");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertEntityNotPresent(EntityType.HUSK);
			helper.assertTrue(guard.isAlive(), "the guard died");
			helper.assertTrue(guard.getAttachedOrElse(ModAttachments.GUARD_KILLS, 0) == 1, "kill not counted");
			helper.assertTrue(guard.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD), "not holding the best sword");
			helper.assertTrue(guard.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "no chestplate");
			helper.assertTrue(guard.getMaxHealth() == 40f, "guards should have 40 health");
		});
	}

	/** Animals are nobody's enemy. */
	@GameTest(template = AREA, timeoutTicks = 400)
	public void guardLeavesAnimalsAlone(GameTestHelper helper) {
		guard(helper, new ItemStack(Items.IRON_SWORD));
		Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(cow.isAlive() && cow.getHealth() == cow.getMaxHealth(), "the cow was attacked");
			helper.succeed();
		});
	}
}
