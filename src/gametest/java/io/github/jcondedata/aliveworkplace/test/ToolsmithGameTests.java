package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.Porters;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Village;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Toolsmiths make the tools the village's workers are waiting for. */
public class ToolsmithGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** A miner without a pickaxe gets an iron one, made from the storehouse's iron and a log. */
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "toolsmith_pickaxe")
	public void toolsmithMakesAPickaxeForTheMiner(GameTestHelper helper) {
		Leftovers.clear(helper);
		Village.RADIUS = 48;
		helper.setDayTime(2000);
		var level = helper.getLevel();
		// The miner, waiting for a pickaxe.
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.MINERS_BENCH);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(6, 2, 6), new BlockPos(7, 2, 7))) {
			helper.setBlock(p, Blocks.STONE);
		}
		Villager miner = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Miners.employ(level, miner, helper.absolutePos(new BlockPos(2, 2, 2)));
		Miners.start(level, miner, null, BoundingBox.fromCorners(helper.absolutePos(new BlockPos(6, 2, 6)), helper.absolutePos(new BlockPos(7, 2, 7))), 1);
		// The storehouse, with iron and a log.
		helper.setBlock(new BlockPos(19, 2, 19), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(19, 2, 17), Blocks.CHEST);
		Container store = helper.getBlockEntity(new BlockPos(19, 2, 17));
		store.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
		store.setItem(1, new ItemStack(Items.OAK_LOG, 1));
		Villager porter = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 18));
		Porters.employ(level, porter, helper.absolutePos(new BlockPos(19, 2, 19)));
		// The toolsmith.
		helper.setBlock(new BlockPos(10, 2, 20), Blocks.SMITHING_TABLE);
		helper.setBlock(new BlockPos(10, 2, 18), Blocks.CHEST);
		Villager toolsmith = helper.spawn(EntityType.VILLAGER, new BlockPos(11, 2, 19));
		Jobs.employ(level, toolsmith, helper.absolutePos(new BlockPos(10, 2, 20)), PoiTypes.TOOLSMITH, VillagerProfession.TOOLSMITH);
		Container minersChest = helper.getBlockEntity(new BlockPos(2, 2, 4));
		helper.succeedWhen(() -> {
			boolean got = miner.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_PICKAXE) || minersChest.countItem(Items.IRON_PICKAXE) == 1;
			helper.assertTrue(got, "no iron pickaxe for the miner; requests " + io.github.jcondedata.aliveworkplace.work.Requests.forVillage(level, toolsmith,
				helper.absolutePos(new BlockPos(10, 2, 20))).stream().map(r -> r.what().getString()).toList()
				+ ", stashes " + Village.stashes(level, toolsmith, helper.absolutePos(new BlockPos(10, 2, 20)), null).stream().map(st -> st.job().name()).toList()
				+ ", busy " + io.github.jcondedata.aliveworkplace.craft.CrafterWork.isBusy(toolsmith)
				+ ", bag " + toolsmith.getAttachedOrCreate(io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG).stacks()
				+ ", activity " + toolsmith.getBrain().getActiveNonCoreActivity() + ", job " + toolsmith.getVillagerData().getProfession());
			helper.assertTrue(store.countItem(Items.IRON_INGOT) == 0, "iron left in the storehouse: " + store.countItem(Items.IRON_INGOT));
			Village.RADIUS = 0;
		});
	}
}
