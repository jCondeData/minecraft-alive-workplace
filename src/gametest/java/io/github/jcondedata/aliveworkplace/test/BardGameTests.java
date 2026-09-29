package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.bard.BardWork;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxSongs;
import net.minecraft.world.level.block.Blocks;

/** Bards on a real (headless) server. */
public class BardGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** At the morning set, a bard plays the disc from the chest by the stand (and the disc stays there). */
	//$ gametest_ticks AREA '400'
	@GameTest(template = AREA, timeoutTicks = 400)
	public void bardPlaysTheDiscFromTheChest(GameTestHelper helper) {
		helper.setDayTime(2000);
		BlockPos stand = new BlockPos(2, 2, 2);
		BlockPos chest = new BlockPos(2, 2, 4);
		helper.setBlock(stand, ModBlocks.MUSIC_STAND);
		helper.setBlock(chest, Blocks.CHEST);
		Container c = helper.getBlockEntity(chest);
		c.setItem(0, new ItemStack(Items.MUSIC_DISC_CAT));
		Villager bard = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), bard, helper.absolutePos(stand), ModVillagers.MUSIC_STAND_POI, ModVillagers.BARD);
		helper.succeedWhen(() -> {
			helper.assertTrue(BardWork.playing(bard).map(s -> s.is(JukeboxSongs.CAT)).orElse(false), "not playing Cat");
			helper.assertTrue(c.countItem(Items.MUSIC_DISC_CAT) == 1, "the disc should stay in the chest");
		});
	}
}
