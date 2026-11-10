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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * QA (lane c, 2026-11-09), B1098's kin: the Storehouse board's click messages for the requests whose names are
 * written with their own article ("a remedy (honey, milk or a healing potion)", "a golden apple (or a healing
 * potion)", "a Remedy for the ill") still read "Gave 1 a remedy ..." and "You have no a golden apple ... to give".
 * Each request is posted under the exact name its job posts (NurseWork, UndertakerWork, PhysicianWork).
 */
public class QaBoardWords1109cBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	private static Requests.Request posted(GameTestHelper helper, VillagerProfession profession, int x, ItemStack icon, String key) {
		BlockPos at = new BlockPos(x, 2, 1);
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setVillagerData(villager.getVillagerData().setProfession(profession).setLevel(2));
		villager.setNoAi(true);
		villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(at.north())));
		Requests.post(villager, icon, 1, Component.translatable(key), s -> s.is(icon.getItem()));
		List<Requests.Request> list = Requests.of(helper.getLevel(), villager);
		Requests.clear(villager);
		helper.assertTrue(list.size() == 1, "the " + key + " request isn't on the worker's list: " + list.size());
		return list.get(0);
	}

	/** A Nurse's remedy, an Undertaker's golden apple and a Physician's remedy, each one item: no "Gave 1 a", no "no a". */
	//$ gametest_batch AREA '"qaBoardWords1109cBug"'
	@GameTest(template = AREA, batch = "qaBoardWords1109cBug")
	public void namesWithTheirOwnArticleReadAsSentences(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Requests.Request> requests = List.of(
			posted(helper, ModVillagers.NURSE, 1, new ItemStack(Items.HONEY_BOTTLE), "request.aliveworkplace.remedy"),
			posted(helper, ModVillagers.UNDERTAKER, 3, new ItemStack(Items.GOLDEN_APPLE), "request.aliveworkplace.revival"),
			posted(helper, ModVillagers.PHYSICIAN, 5, new ItemStack(Items.HONEY_BOTTLE), "request.aliveworkplace.remedy_physician"));
		List<String> wrong = new ArrayList<>();
		for (Requests.Request r : requests) {
			String gave = StorehouseBoard.gaveMessage(r, 1).getString();
			String none = StorehouseBoard.noneMessage(r).getString();
			if (gave.startsWith("Gave 1 a ") || gave.startsWith("Gave 1 an ")) {
				wrong.add("'" + gave + "'");
			}
			if (none.contains(" no a ") || none.contains(" no an ")) {
				wrong.add("'" + none + "'");
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the board's messages don't read as sentences: " + String.join(", ", wrong));
		helper.succeed();
	}
}
