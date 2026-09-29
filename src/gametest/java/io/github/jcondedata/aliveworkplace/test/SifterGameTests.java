package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.sift.SifterWork;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Requests;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;

/** Sifters at the sieve. */
public class SifterGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos SIEVE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	private static Villager sifter(GameTestHelper helper, ItemStack... chest) {
		helper.setDayTime(2000);
		helper.setBlock(SIEVE, ModBlocks.SIEVE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(SIEVE), ModVillagers.SIEVE_POI, ModVillagers.SIFTER);
		return villager;
	}

	/** Every sifting table loads. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void siftingTablesLoad(GameTestHelper helper) {
		for (var key : SifterWork.SIFTABLE.values()) {
			helper.assertTrue(helper.getLevel().getServer().reloadableRegistries().getLootTable(key) != LootTable.EMPTY, "missing " + key.location());
		}
		helper.succeed();
	}

	/** Gravel through the sieve: it's used up, flint and the like come out into the chest. */
	@GameTest(template = AREA)
	public void siftingGravelTurnsUpFlint(GameTestHelper helper) {
		Villager sifter = sifter(helper, new ItemStack(Items.GRAVEL, 40));
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < 40; i++) {
			helper.assertTrue(SifterWork.sift(helper.getLevel(), sifter, helper.absolutePos(SIEVE)) != null, "nothing to sift at " + i);
		}
		helper.assertTrue(chest.countItem(Items.GRAVEL) == 0, "gravel left: " + chest.countItem(Items.GRAVEL));
		helper.assertTrue(chest.countItem(Items.FLINT) > 0, "no flint from 40 gravel");
		helper.assertTrue(ModAttachments.BLOCKS_SIFTED.getOrElse(sifter, 0) == 40, "counted " + ModAttachments.BLOCKS_SIFTED.getOrElse(sifter, 0));
		// Nothing left to sift: they ask for gravel.
		helper.assertTrue(SifterWork.sift(helper.getLevel(), sifter, helper.absolutePos(SIEVE)) == null, "sifted air");
		helper.assertTrue(Requests.of(helper.getLevel(), sifter).stream().anyMatch(r -> r.accepts().test(new ItemStack(Items.GRAVEL))), "no request for gravel");
		helper.succeed();
	}

	/** At work, a sifter sifts on their own. */
	@GameTest(template = AREA, timeoutTicks = 1200)
	public void aSifterAtWork(GameTestHelper helper) {
		Villager sifter = sifter(helper, new ItemStack(Items.SAND, 8));
		Container chest = helper.getBlockEntity(CHEST);
		helper.succeedWhen(() -> helper.assertTrue(chest.countItem(Items.SAND) <= 6, "sand left: " + chest.countItem(Items.SAND)));
	}
}
