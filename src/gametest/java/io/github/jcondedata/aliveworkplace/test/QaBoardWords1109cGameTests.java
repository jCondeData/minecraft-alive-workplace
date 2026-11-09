package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.Requests;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * QA (lane c, 2026-11-09) for B1098, from its Expected: the Storehouse board's click messages read as natural sentences
 * for every stand-alone request name the Hall uses, not only the Miner's pickaxe and the Farmer's seeds lane a's test
 * covers. The Lumberjack's axe and the Fisherman's rod are posted the way their jobs post them (Requests.postTool) and
 * read back off the worker's request list, as the board does.
 */
public class QaBoardWords1109cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BATCH = "qaBoardWords1109c";

	private static Villager worker(GameTestHelper helper, VillagerProfession profession, int x) {
		BlockPos at = new BlockPos(x, 2, 1);
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setVillagerData(villager.getVillagerData().setProfession(profession).setLevel(2));
		villager.setNoAi(true);
		villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(at.north())));
		return villager;
	}

	private static Requests.Request posted(GameTestHelper helper, Villager worker, String what) {
		List<Requests.Request> list = Requests.of(helper.getLevel(), worker);
		for (Requests.Request r : list) {
			if (r.what().getString().equals(what)) {
				return r;
			}
		}
		throw new net.minecraft.gametest.framework.GameTestAssertException("the worker's list has no '" + what + "': "
			+ list.stream().map(r -> r.what().getString()).toList());
	}

	private static void says(GameTestHelper helper, Component message, String expected) {
		String text = message.getString();
		helper.assertTrue(text.equals(expected), "'" + text + "' should read '" + expected + "'");
	}

	/** The Lumberjack's axe and the Fisherman's rod, posted as their jobs post them: lower case mid-sentence, no "1". */
	//$ gametest_batch AREA BATCH
	@GameTest(template = AREA, batch = BATCH)
	public void anAxeAndARodReadAsSentences(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager lumberjack = worker(helper, ModVillagers.LUMBERJACK, 1);
		Requests.postTool(lumberjack, Items.IRON_AXE, "axe", s -> s.is(ItemTags.AXES));
		Requests.Request axe = posted(helper, lumberjack, "An axe");
		says(helper, StorehouseBoard.gaveMessage(axe, 1), "Gave an axe to the Lumberjack's chests");
		says(helper, StorehouseBoard.noneMessage(axe), "You don't have an axe to give (or their chests are full)");

		Villager fisher = worker(helper, VillagerProfession.FISHERMAN, 4);
		Requests.postTool(fisher, Items.FISHING_ROD, "fishing_rod", s -> s.is(Items.FISHING_ROD));
		Requests.Request rod = posted(helper, fisher, "A fishing rod");
		says(helper, StorehouseBoard.gaveMessage(rod, 1), "Gave a fishing rod to the Fisherman's chests");
		says(helper, StorehouseBoard.noneMessage(rod), "You don't have a fishing rod to give (or their chests are full)");
		Requests.clear(lumberjack);
		Requests.clear(fisher);
		helper.succeed();
	}

	/** Seeds both sides of one: one seed handed over drops the count, two keep it; neither capitalizes "Seeds". */
	//$ gametest_batch AREA BATCH
	@GameTest(template = AREA, batch = BATCH)
	public void seedsOneAndTwo(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager farmer = worker(helper, VillagerProfession.FARMER, 1);
		Requests.Request seeds = new Requests.Request(farmer, helper.absolutePos(new BlockPos(1, 2, 0)), new ItemStack(Items.WHEAT_SEEDS), 16,
			Component.translatable("request.aliveworkplace.seeds"), s -> s.is(Items.WHEAT_SEEDS));
		says(helper, StorehouseBoard.gaveMessage(seeds, 1), "Gave seeds to the Farmer's chests");
		says(helper, StorehouseBoard.gaveMessage(seeds, 2), "Gave 2 seeds to the Farmer's chests");
		helper.succeed();
	}
}
