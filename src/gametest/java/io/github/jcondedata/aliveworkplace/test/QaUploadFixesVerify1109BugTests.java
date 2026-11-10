package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.Requests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * QA (qa-lane-b-1109-1537), from B1098's Expected ("natural sentences" for the Storehouse board's messages that put a
 * Village Hall request name mid-sentence): the fix rewords only the four names written to stand alone capitalized (a
 * pickaxe, an axe, a fishing rod, seeds). Other request names the board shows are written with their article already
 * ("a golden apple (or a healing potion)" for the Undertaker, "a remedy (honey, milk or a healing potion)" for the Nurse,
 * "a Remedy for the ill" for the Physician), so the same messages read "Gave 1 a golden apple ..." and "You have no a
 * golden apple ... to give".
 */
public class QaUploadFixesVerify1109BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	//$ gametest_batch AREA '"qaUploadFixesVerify1109BoardBug"'
	@GameTest(template = AREA, batch = "qaUploadFixesVerify1109BoardBug")
	public void theStorehouseBoardsMessagesReadAsSentencesForTheUndertakersNursesAndPhysiciansRequests(GameTestHelper helper) {
		Leftovers.clear(helper);
		Object[][] kinds = {
			{ModVillagers.UNDERTAKER, new ItemStack(Items.GOLDEN_APPLE), "request.aliveworkplace.revival"},
			{ModVillagers.NURSE, new ItemStack(Items.HONEY_BOTTLE), "request.aliveworkplace.remedy"},
			{ModVillagers.PHYSICIAN, new ItemStack(ModItems.REMEDY), "request.aliveworkplace.remedy_physician"}};
		List<String> wrong = new ArrayList<>();
		int x = 1;
		for (Object[] kind : kinds) {
			Villager v = QaUploadFixesVerify1109GameTests.worker(helper, (VillagerProfession) kind[0], x++);
			// as the Undertaker, Nurse and Physician post them: one item
			Requests.Request r = QaUploadFixesVerify1109GameTests.request(helper, v, (ItemStack) kind[1], 1, Component.translatable((String) kind[2]));
			for (String s : List.of(StorehouseBoard.gaveMessage(r, 1).getString(), StorehouseBoard.noneMessage(r).getString())) {
				List<String> p = QaUploadFixesVerify1109GameTests.problems(s);
				if (!p.isEmpty()) {
					wrong.add("'" + s + "' (" + String.join(", ", p) + ")");
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the Storehouse board's messages for these requests don't read as sentences: "
			+ String.join("; ", wrong));
		helper.succeed();
	}
}
