package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.Requests;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * B1098: the Storehouse board's messages read as sentences for the Hall's stand-alone request names ("A pickaxe",
 * "Seeds"): "Gave a pickaxe to the Miner's chests", never "Gave 1 A pickaxe"; "You don't have a pickaxe to give", never
 * "You have no A pickaxe to give". Item names read as before ("Gave 25 Cobblestone").
 */
public class StorehouseBoardWordsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	private static Villager worker(GameTestHelper helper, VillagerProfession profession) {
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
		villager.setVillagerData(villager.getVillagerData().setProfession(profession).setLevel(2));
		villager.setNoAi(true);
		return villager;
	}

	private static Requests.Request request(GameTestHelper helper, Villager worker, ItemStack icon, int count, Component what) {
		return new Requests.Request(worker, helper.absolutePos(new BlockPos(1, 2, 1)), icon, count, what, stack -> stack.is(icon.getItem()));
	}

	private static void says(GameTestHelper helper, Component message, String expected) {
		String text = message.getString();
		helper.assertTrue(text.equals(expected), "'" + text + "' should read '" + expected + "'");
	}

	/** A Miner's pickaxe request, a Farmer's seeds and a builder's cobblestone, as the board's messages word them. */
	//$ gametest_batch AREA '"storehouseBoardWords"'
	@GameTest(template = AREA, batch = "storehouseBoardWords")
	public void boardMessagesReadAsSentences(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager miner = worker(helper, ModVillagers.MINER);
		Requests.Request pickaxe = request(helper, miner, new ItemStack(Items.STONE_PICKAXE), 1, Component.translatable("request.aliveworkplace.pickaxe"));
		says(helper, StorehouseBoard.gaveMessage(pickaxe, 1), "Gave a pickaxe to the Miner's chests");
		says(helper, StorehouseBoard.noneMessage(pickaxe), "You don't have a pickaxe to give (or their chests are full)");

		Villager farmer = worker(helper, VillagerProfession.FARMER);
		Requests.Request seeds = request(helper, farmer, new ItemStack(Items.WHEAT_SEEDS), 12, Component.translatable("request.aliveworkplace.seeds"));
		says(helper, StorehouseBoard.gaveMessage(seeds, 12), "Gave 12 seeds to the Farmer's chests");
		says(helper, StorehouseBoard.noneMessage(seeds), "You don't have seeds to give (or their chests are full)");

		Requests.Request cobble = request(helper, miner, new ItemStack(Items.COBBLESTONE), 25, Items.COBBLESTONE.getDefaultInstance().getHoverName());
		says(helper, StorehouseBoard.gaveMessage(cobble, 25), "Gave 25 Cobblestone to the Miner's chests");
		says(helper, StorehouseBoard.noneMessage(cobble), "You have no Cobblestone to give (or their chests are full)");
		helper.succeed();
	}
}
