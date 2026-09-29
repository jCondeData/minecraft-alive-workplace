package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.smelt.Smelters;
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

/** Armorers smelt the village's ore at their blast furnace. */
public class SmelterGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos FURNACE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STOREHOUSE = new BlockPos(19, 2, 19);
	private static final BlockPos STORE_CHEST = new BlockPos(19, 2, 17);

	/** A smelter keeps its ore (and iron for armor) from the porter; the other ingots go to the storehouse. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void smeltersKeepTheirOre(GameTestHelper helper) {
		helper.assertTrue(Porters.keeps(VillagerProfession.ARMORER, new ItemStack(Items.RAW_IRON), true) == Porters.ALL, "raw iron");
		helper.assertTrue(Porters.keeps(VillagerProfession.ARMORER, new ItemStack(Items.IRON_INGOT), true) == Smelters.KEEP_INGOTS, "iron ingots");
		helper.assertTrue(Porters.keeps(VillagerProfession.ARMORER, new ItemStack(Items.COPPER_INGOT), true) == 0, "copper ingots");
		helper.assertTrue(Porters.keeps(VillagerProfession.ARMORER, new ItemStack(Items.COAL), true) == Porters.KEEP_FUEL, "coal");
		helper.succeed();
	}

	/** An armorer with ore and coal in the chest by their blast furnace smelts it and puts the ingots in the chest. */
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "smelter_own")
	public void armorerSmeltsTheOreInTheirChest(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		Villager smelter = smelter(helper);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.RAW_IRON, 4));
		chest.setItem(1, new ItemStack(Items.COAL, 2));
		helper.succeedWhen(() -> {
			helper.assertTrue(chest.countItem(Items.IRON_INGOT) == 4, "iron ingots in the chest: " + chest.countItem(Items.IRON_INGOT));
			int smelted = ModAttachments.INGOTS_SMELTED.getOrElse(smelter, 0);
			helper.assertTrue(smelted == 4, "smelted " + smelted);
		});
	}

	/** With nothing to smelt, the armorer fetches the ore from the village's storehouse (and the coal to smelt it with). */
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "smelter_fetches")
	public void armorerFetchesOreFromTheStorehouse(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		helper.setDayTime(2000);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		Container store = helper.getBlockEntity(STORE_CHEST);
		store.setItem(0, new ItemStack(Items.RAW_COPPER, 6));
		store.setItem(1, new ItemStack(Items.COAL, 8));
		Villager porter = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 18));
		Porters.employ(helper.getLevel(), porter, helper.absolutePos(STOREHOUSE));
		smelter(helper);
		Container chest = helper.getBlockEntity(CHEST);
		helper.succeedWhen(() -> {
			int ingots = chest.countItem(Items.COPPER_INGOT) + store.countItem(Items.COPPER_INGOT);
			helper.assertTrue(ingots == 6, "copper ingots: " + ingots);
			helper.assertTrue(store.countItem(Items.RAW_COPPER) == 0, "raw copper left in the storehouse");
			Village.RADIUS = 0;
		});
	}

	/** A guard of the village with no chestplate gets one: the armorer makes it from iron in their chest and brings it over. */
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "smelter_armor")
	public void armorerMakesAChestplateForTheGuard(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		helper.setDayTime(2000);
		Villager smelter = smelter(helper);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.IRON_INGOT, 8));
		helper.setBlock(STOREHOUSE, ModBlocks.GUARD_POST);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 18));
		Jobs.employ(helper.getLevel(), guard, helper.absolutePos(STOREHOUSE), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		Container guardsChest = helper.getBlockEntity(STORE_CHEST);
		helper.succeedWhen(() -> {
			boolean delivered = guard.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE) || guardsChest.countItem(Items.IRON_CHESTPLATE) == 1;
			helper.assertTrue(delivered, "no chestplate for the guard");
			helper.assertTrue(chest.countItem(Items.IRON_INGOT) == 0, "iron left: " + chest.countItem(Items.IRON_INGOT));
			helper.assertTrue(ModAttachments.ARMOR_MADE.getOrElse(smelter, 0) == 1, "armor made: " + ModAttachments.ARMOR_MADE.getOrElse(smelter, 0));
			Village.RADIUS = 0;
		});
	}

	private static Villager smelter(GameTestHelper helper) {
		helper.setBlock(FURNACE, Blocks.BLAST_FURNACE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager smelter = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), smelter, helper.absolutePos(FURNACE), PoiTypes.ARMORER, VillagerProfession.ARMORER);
		return smelter;
	}
}
