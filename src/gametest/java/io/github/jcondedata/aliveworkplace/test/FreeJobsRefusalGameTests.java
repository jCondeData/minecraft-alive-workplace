package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.StationsSpecGameTests.listeningPlayer;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.FreeJobsPage;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Retirement;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * B892: the Village Hall's "Free job blocks" page (36.9) offers nobody the hall would refuse a block to. A retired elder
 * (B885) and a hired mercenary (B889) aren't listed as takers; a villager without a job still is and gets the block; and
 * an elder who retires while the page is open is told why when picked, not that someone took the block first. Every
 * click goes through the menu's slot click, as the player's own.
 */
public class FreeJobsRefusalGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	/** The Porter's block, the only free job block in the village. */
	private static final BlockPos STOREHOUSE = new BlockPos(5, 2, 8);

	private static void village(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
	}

	private static Villager jobless(GameTestHelper helper, String name, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true); // nobody walks off to claim the Storehouse by themselves
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
		v.setCustomName(Component.literal(name));
		return v;
	}

	private static void retire(GameTestHelper helper, Villager v) {
		ModAttachments.RETIRED.set(v, new Retirement.Retired(Chronicle.day(helper.getLevel()) - 1, "minecraft:farmer", 3));
	}

	/** The page's tab, then the Storehouse's line: the block's own page, with who could take it. */
	private static ChoiceMenu openBlock(GameTestHelper helper, ServerPlayer player) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(HALL));
		click(menu, HallPages.slot(FreeJobsPage.PAGE), player);
		helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW).is(ModBlocks.STOREHOUSE.asItem()), "the Storehouse isn't listed: " + menu.icon(VillageHallScreen.FIRST_ROW));
		click(menu, VillageHallScreen.FIRST_ROW, player);
		helper.assertTrue(menu.icon(4).is(ModBlocks.STOREHOUSE.asItem()), "the block's page didn't open: " + menu.icon(4));
		return menu;
	}

	private static void click(ChoiceMenu menu, int slot, ServerPlayer player) {
		menu.clicked(slot, 0, ClickType.PICKUP, player);
	}

	private static List<String> lore(ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	private static String offer(Villager v) {
		return Component.translatable("screen.aliveworkplace.hall.give_job", v.getDisplayName(), Stations.name(ModVillagers.PORTER)).getString();
	}

	/** All the villager rows on the block's page, as their click lines. */
	private static List<String> offered(ChoiceMenu menu) {
		List<String> out = new ArrayList<>();
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			out.addAll(lore(menu.icon(slot)));
		}
		return out;
	}

	private static int free(GameTestHelper helper) {
		return helper.getLevel().getPoiManager().getFreeTickets(helper.absolutePos(STOREHOUSE));
	}

	/** A retired elder isn't offered the free block; the villager without a job beside him is, and a click gives it to her. */
	//$ gametest_ticks_batch AREA '100' '"freeJobsRetiredElder"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "freeJobsRetiredElder")
	public void aRetiredElderIsNotOfferedAFreeBlock(GameTestHelper helper) {
		village(helper);
		Villager bram = jobless(helper, "Bram", new BlockPos(9, 2, 12)); // nearer the hall: he'd be first
		Villager ada = jobless(helper, "Ada", new BlockPos(14, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(2, () -> {
			retire(helper, bram);
			ChoiceMenu menu = openBlock(helper, player);
			List<String> lines = offered(menu);
			helper.assertTrue(!lines.contains(offer(bram)), "the retired elder is offered the Storehouse: " + lines);
			helper.assertTrue(lore(menu.icon(VillageHallScreen.FIRST_ROW)).contains(offer(ada)), "Ada isn't offered it: " + lines);
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW + 1).isEmpty(), "a second taker: " + menu.icon(VillageHallScreen.FIRST_ROW + 1));
			click(menu, VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(ada.getVillagerData().getProfession() == ModVillagers.PORTER, "Ada isn't a Porter: " + ada.getVillagerData());
			helper.assertTrue(free(helper) == 0, "the Storehouse isn't hers");
			String told = Words.articled("message.aliveworkplace.hall.assigned", 1, ada.getDisplayName(), Stations.name(ModVillagers.PORTER)).getString();
			helper.assertTrue(seen.contains(told), "not told (" + told + "): " + seen);
			helper.assertTrue(bram.getVillagerData().getProfession() == VillagerProfession.NONE, "the elder works again: " + bram.getVillagerData());
			helper.succeed();
		});
	}

	/** With only a retired elder and a hired mercenary without a job, the block's page says nobody can take it, and it stays free. */
	//$ gametest_ticks_batch AREA '100' '"freeJobsNobody"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "freeJobsNobody")
	public void noRetiredElderOrMercenaryIsOfferedAFreeBlock(GameTestHelper helper) {
		village(helper);
		Villager bram = jobless(helper, "Bram", new BlockPos(9, 2, 12));
		Villager merc = jobless(helper, "Rook", new BlockPos(14, 2, 15));
		ServerPlayer player = listeningPlayer(helper, new ArrayList<>());
		helper.runAfterDelay(2, () -> {
			retire(helper, bram);
			ModAttachments.MERCENARY_UNTIL.set(merc, helper.getLevel().getGameTime() + 24000L);
			ChoiceMenu menu = openBlock(helper, player);
			List<String> lines = offered(menu);
			helper.assertTrue(!lines.contains(offer(bram)) && !lines.contains(offer(merc)), "a villager the hall refuses is offered the Storehouse: " + lines);
			String nobody = Component.translatable("screen.aliveworkplace.free_jobs.none_jobless").getString();
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW + 4).getHoverName().getString().equals(nobody),
				"not told nobody can take it: " + menu.icon(VillageHallScreen.FIRST_ROW + 4).getHoverName().getString());
			for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
				click(menu, slot, player); // nothing on the page gives the block away
			}
			helper.assertTrue(free(helper) == 1, "the Storehouse is held");
			helper.assertTrue(bram.getVillagerData().getProfession() == VillagerProfession.NONE && merc.getVillagerData().getProfession() == VillagerProfession.NONE,
				"given a job: " + bram.getVillagerData() + ", " + merc.getVillagerData());
			helper.succeed();
		});
	}

	/** An elder who retires while the block's page is open is told why when picked; the block stays free and nothing says it was taken. */
	//$ gametest_ticks_batch AREA '100' '"freeJobsRetiredMeanwhile"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "freeJobsRetiredMeanwhile")
	public void anElderRetiredWhileThePageIsOpenIsToldWhy(GameTestHelper helper) {
		village(helper);
		Villager bram = jobless(helper, "Bram", new BlockPos(9, 2, 12));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openBlock(helper, player);
			helper.assertTrue(lore(menu.icon(VillageHallScreen.FIRST_ROW)).contains(offer(bram)), "Bram isn't offered it before he retires: " + offered(menu));
			retire(helper, bram);
			click(menu, VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(bram.getVillagerData().getProfession() == VillagerProfession.NONE, "the elder works again: " + bram.getVillagerData());
			helper.assertTrue(free(helper) == 1 && bram.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(),
				"the Storehouse is held for a retired elder: " + bram.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			String why = Component.translatable("message.aliveworkplace.job.retired", bram.getDisplayName()).getString();
			helper.assertTrue(seen.contains(why), "not told why (" + why + "): " + seen);
			String taken = Component.translatable("message.aliveworkplace.hall.not_assigned").getString();
			helper.assertTrue(!seen.contains(taken) && seen.stream().noneMatch(s -> s.startsWith("Bram is now")), "told something else: " + seen);
			helper.assertTrue(!offered(menu).contains(offer(bram)), "still offered after the refusal: " + offered(menu));
			helper.succeed();
		});
	}
}
