package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.Porters;
import io.github.jcondedata.aliveworkplace.store.StorehouseBlockEntity;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Porters carry the other workers' goods to the storehouse. */
public class PorterGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos MINERS_BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos MINERS_CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STOREHOUSE = new BlockPos(19, 2, 19);
	private static final BlockPos STORE_CHEST = new BlockPos(19, 2, 17);

	/** What a worker's job needs stays; what it makes is the porter's to carry. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void portersLeaveWhatTheJobNeeds(GameTestHelper helper) {
		check(helper, ModVillagers.MINER, Items.TORCH, false, Porters.ALL);
		check(helper, ModVillagers.MINER, Items.LADDER, false, Porters.ALL);
		check(helper, ModVillagers.MINER, Items.COBBLESTONE, false, Porters.KEEP_FILLER);
		check(helper, ModVillagers.MINER, Items.RAW_IRON, false, 0);
		check(helper, ModVillagers.MINER, Items.RAW_IRON, true, Porters.ALL); // waiting for the furnace
		check(helper, ModVillagers.MINER, Items.IRON_INGOT, true, 0);
		check(helper, ModVillagers.MINER, Items.COAL, true, Porters.KEEP_FUEL);
		check(helper, ModVillagers.MINER, Items.IRON_PICKAXE, false, Porters.ALL); // tools never
		check(helper, ModVillagers.LUMBERJACK, Items.OAK_LOG, false, 0);
		check(helper, ModVillagers.LUMBERJACK, Items.OAK_SAPLING, false, Porters.KEEP_SAPLINGS);
		check(helper, ModVillagers.LUMBERJACK, Items.BONE_MEAL, false, Porters.ALL);
		check(helper, VillagerProfession.FARMER, Items.WHEAT, false, 0);
		check(helper, VillagerProfession.FARMER, Items.WHEAT_SEEDS, false, Porters.KEEP_SEEDS);
		check(helper, VillagerProfession.FARMER, Items.CARROT, false, Porters.KEEP_SEEDS);
		check(helper, VillagerProfession.FISHERMAN, Items.COD, false, 0);
		check(helper, VillagerProfession.FISHERMAN, Items.COD, true, Porters.ALL);
		check(helper, ModVillagers.ORCHARD_KEEPER, Items.APPLE, false, 0);
		check(helper, ModVillagers.ORCHARD_KEEPER, Items.SWEET_BERRIES, false, Porters.KEEP_SAPLINGS);
		check(helper, ModVillagers.BUILDER, Items.COBBLESTONE, false, Porters.ALL);
		check(helper, ModVillagers.BALL_SMITH, Items.IRON_INGOT, false, Porters.ALL);
		helper.succeed();
	}

	private static void check(GameTestHelper helper, VillagerProfession job, net.minecraft.world.item.Item item, boolean furnace, int keeps) {
		int got = Porters.keeps(job, new ItemStack(item), furnace);
		helper.assertTrue(got == keeps, job.name() + " keeps " + got + " " + item + (furnace ? " (furnace)" : "") + ", expected " + keeps);
	}

	/** A double chest is one set of items: listed once, counted once. */
	@GameTest(template = AREA)
	public void doubleChestsCountOnce(GameTestHelper helper) {
		helper.setBlock(new BlockPos(5, 2, 5), ModBlocks.MINERS_BENCH);
		helper.setBlock(new BlockPos(5, 2, 7), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.LEFT));
		helper.setBlock(new BlockPos(6, 2, 7), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.RIGHT));
		Container chest = helper.getBlockEntity(new BlockPos(5, 2, 7));
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 40));
		List<BlockPos> found = SupplyContainers.find(helper.getLevel(), helper.absolutePos(new BlockPos(5, 2, 5)), null);
		helper.assertTrue(found.size() == 1, "a double chest listed as " + found.size());
		long count = SupplyContainers.count(helper.getLevel(), found, Items.COBBLESTONE);
		helper.assertTrue(count == 40, "40 cobblestone counted as " + count);
		helper.succeed();
	}

	/** The porter fetches the miner's ore and extra stone, leaves the torches, pickaxe and some stone, and stores the rest. */
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "porter_carries")
	public void porterCarriesTheMinersGoodsToTheStorehouse(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		Villager miner = miner(helper);
		Container minersChest = helper.getBlockEntity(MINERS_CHEST);
		minersChest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		minersChest.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
		minersChest.setItem(2, new ItemStack(Items.RAW_IRON, 20));
		minersChest.setItem(3, new ItemStack(Items.TORCH, 16));
		minersChest.setItem(4, new ItemStack(Items.IRON_PICKAXE));
		minersChest.setItem(5, new ItemStack(Items.COAL, 10));
		Villager porter = porter(helper);
		helper.succeedWhen(() -> {
			Container store = helper.getBlockEntity(STORE_CHEST);
			helper.assertTrue(store.countItem(Items.COBBLESTONE) == 128 - Porters.KEEP_FILLER, "stored cobblestone: " + store.countItem(Items.COBBLESTONE));
			helper.assertTrue(store.countItem(Items.RAW_IRON) == 20, "stored raw iron: " + store.countItem(Items.RAW_IRON));
			helper.assertTrue(store.countItem(Items.COAL) == 10, "stored coal: " + store.countItem(Items.COAL));
			helper.assertTrue(minersChest.countItem(Items.COBBLESTONE) == Porters.KEEP_FILLER, "the miner should keep some stone");
			helper.assertTrue(minersChest.countItem(Items.TORCH) == 16 && minersChest.countItem(Items.IRON_PICKAXE) == 1, "the miner's gear went too");
			helper.assertTrue(store.countItem(Items.TORCH) == 0 && store.countItem(Items.IRON_PICKAXE) == 0, "the porter took the miner's gear");
			int carried = porter.getAttachedOrElse(ModAttachments.ITEMS_CARRIED, 0);
			helper.assertTrue(carried == 96 + 20 + 10, "carried " + carried);
			helper.assertTrue(miner.isAlive(), "the miner is gone");
			Village.RADIUS = 0;
		});
	}

	/** A porter works for the storehouse's owner, and leaves the chests of someone else's workers alone. */
	@GameTest(template = AREA, timeoutTicks = 400, batch = "porter_strangers")
	public void porterLeavesStrangersWorkersAlone(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		Villager miner = miner(helper);
		miner.setAttached(ModAttachments.BUILDER_EMPLOYER, new Employer(UUID.randomUUID(), "Bea"));
		Container minersChest = helper.getBlockEntity(MINERS_CHEST);
		minersChest.setItem(0, new ItemStack(Items.RAW_IRON, 40));
		UUID al = UUID.randomUUID();
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		StorehouseBlockEntity storehouse = helper.getBlockEntity(STOREHOUSE);
		storehouse.setOwner(al, "Al");
		Villager porter = porter(helper);
		helper.runAfterDelay(300, () -> {
			Employer boss = porter.getAttached(ModAttachments.BUILDER_EMPLOYER);
			helper.assertTrue(boss != null && boss.id().equals(al), "the porter should work for the storehouse's owner, not " + boss);
			helper.assertTrue(minersChest.countItem(Items.RAW_IRON) == 40, "the porter took from a stranger's miner");
			Village.RADIUS = 0;
			helper.succeed();
		});
	}

	private static Villager miner(GameTestHelper helper) {
		helper.setBlock(MINERS_BENCH, ModBlocks.MINERS_BENCH);
		helper.setBlock(MINERS_CHEST, Blocks.CHEST);
		Villager miner = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		io.github.jcondedata.aliveworkplace.mine.Miners.employ(helper.getLevel(), miner, helper.absolutePos(MINERS_BENCH));
		return miner;
	}

	private static Villager porter(GameTestHelper helper) {
		if (!helper.getBlockState(STOREHOUSE).is(ModBlocks.STOREHOUSE)) {
			helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		}
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		Villager porter = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 18));
		Porters.employ(helper.getLevel(), porter, helper.absolutePos(STOREHOUSE));
		return porter;
	}
}
