package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.fish.Fishers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Hired fishermen on a real (headless) server. */
public class FisherGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos BARREL = new BlockPos(2, 2, 2);

	private static Villager fisherman(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		helper.setBlock(BARREL, Blocks.BARREL);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 8), new BlockPos(11, 1, 11))) {
			helper.setBlock(p, Blocks.WATER);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, villager, helper.absolutePos(BARREL), PoiTypes.FISHERMAN, VillagerProfession.FISHERMAN);
		return villager;
	}

	private static int stored(Container container) {
		int n = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			n += container.getItem(i).getCount();
		}
		return n;
	}

	/** Casts into the pond, reels in fish and junk, and brings every fifth catch home to the barrel. */
	@GameTest(template = AREA, timeoutTicks = 3000)
	public void fishermanFillsTheBarrel(GameTestHelper helper) {
		Villager villager = fisherman(helper);
		Fishers.start(helper.getLevel(), villager, new ItemStack(Items.FISHING_ROD));
		helper.succeedWhen(() -> {
			int caught = villager.getAttachedOrElse(ModAttachments.FISH_CAUGHT, 0);
			helper.assertTrue(caught >= 5, "only " + caught + " caught");
			Container barrel = helper.getBlockEntity(BARREL);
			helper.assertTrue(stored(barrel) >= 5, "only " + stored(barrel) + " items in the barrel");
		});
	}

	/** When the rod breaks, the next one comes out of the barrel. */
	@GameTest(template = AREA, timeoutTicks = 3000)
	public void fishermanTakesASpareRod(GameTestHelper helper) {
		Villager villager = fisherman(helper);
		Container barrel = helper.getBlockEntity(BARREL);
		barrel.setItem(0, new ItemStack(Items.FISHING_ROD));
		ItemStack worn = new ItemStack(Items.FISHING_ROD);
		worn.setDamageValue(worn.getMaxDamage() - 1); // breaks on the first catch
		Fishers.start(helper.getLevel(), villager, worn);
		helper.succeedWhen(() -> {
			helper.assertTrue(villager.getAttachedOrElse(ModAttachments.FISH_CAUGHT, 0) >= 2, "no second catch yet");
			helper.assertTrue(villager.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.FISHING_ROD), "no rod in hand");
			helper.assertTrue(barrel.countItem(Items.FISHING_ROD) == 0, "the spare rod is still in the barrel");
		});
	}
}
