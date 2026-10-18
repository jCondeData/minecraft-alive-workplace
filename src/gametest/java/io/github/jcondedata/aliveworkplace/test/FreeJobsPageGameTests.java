package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.FreeJobsPage;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * The Village Hall's "Free job blocks" page (ROADMAP 36.9), through the hall's own screen: the listing (job, building,
 * distance, who could take it), a pick giving the block (claimed, walked to), a block claimed meanwhile refused cleanly,
 * a villager who found work meanwhile left alone, and the job kept through a save and reload.
 */
public class FreeJobsPageGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	/** A Storehouse inside a finished Well's outline (the Porter's block), nearest the hall. */
	private static final BlockPos STOREHOUSE = new BlockPos(5, 2, 8);
	/** A builder's bench out in the open, further away. */
	private static final BlockPos BENCH = new BlockPos(18, 2, 3);

	/** The village: the hall, a Storehouse in a finished Well, a bench outside, a jobless villager, a nitwit and a farmer who lost his block. */
	private static Villager stage(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		BlueprintData.Placement well = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 2, 5)), Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(StarterBlueprints.WELL.id(), well, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(well));
		Villager jobless = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 14));
		jobless.setVillagerData(jobless.getVillagerData().setProfession(VillagerProfession.NONE));
		Villager nitwit = helper.spawn(EntityType.VILLAGER, new BlockPos(13, 2, 14));
		nitwit.setVillagerData(nitwit.getVillagerData().setProfession(VillagerProfession.NITWIT));
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 15));
		farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER));
		farmer.setVillagerXp(10); // traded with: he keeps the job without a block, and only a composter would be his again
		return jobless;
	}

	private static ChoiceMenu openPage(GameTestHelper helper, ServerPlayer player) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(HALL));
		int tab = HallPages.slot(FreeJobsPage.PAGE);
		helper.assertTrue(tab >= VillageHallScreen.PAGE_ROW, "the Free job blocks page has no tab: " + tab);
		helper.assertTrue(menu.icon(tab).is(Items.CRAFTING_TABLE), "the tab: " + menu.icon(tab));
		menu.press(tab, player);
		helper.assertTrue(menu.icon(4).is(Items.CRAFTING_TABLE), "the page's header: " + menu.icon(4));
		return menu;
	}

	private static List<String> lore(ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	/** The listing: each free block with its job, the building it stands in and how far; the jobless who could take it. */
	//$ gametest_ticks_batch AREA '100' '"freeJobBlocksAreListed"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "freeJobBlocksAreListed")
	public void freeJobBlocksAreListed(GameTestHelper helper) {
		Villager jobless = stage(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			List<FreeJobsPage.FreeBlock> blocks = FreeJobsPage.blocks(level, hall);
			helper.assertTrue(blocks.size() == 2, "free blocks: " + blocks);
			ChoiceMenu menu = openPage(helper, player);
			ItemStack store = menu.icon(VillageHallScreen.FIRST_ROW);
			helper.assertTrue(store.is(ModBlocks.STOREHOUSE.asItem()), "the nearest block first: " + store);
			helper.assertTrue(store.getHoverName().getString().equals(Stations.name(ModVillagers.PORTER).getString()), "its job: " + store.getHoverName().getString());
			List<String> lines = lore(store);
			String in = Component.translatable("screen.aliveworkplace.free_jobs.in", Blueprints.displayName(StarterBlueprints.WELL.id())).getString();
			helper.assertTrue(lines.contains(in), "the building it's in (" + in + "): " + lines);
			String where = VillageHallScreen.where(hall, helper.absolutePos(STOREHOUSE)).getString();
			helper.assertTrue(lines.contains(where), "how far (" + where + "): " + lines);
			helper.assertTrue(lines.contains(Component.translatable("screen.aliveworkplace.free_jobs.takers.one", 1).getString()), "who could take it: " + lines);
			ItemStack bench = menu.icon(VillageHallScreen.FIRST_ROW + 1);
			helper.assertTrue(bench.is(ModBlocks.BUILDERS_BENCH.asItem()), "the bench second: " + bench);
			helper.assertTrue(lore(bench).contains(Component.translatable("screen.aliveworkplace.free_jobs.outside").getString()), "the bench in the open: " + lore(bench));
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW + 2).isEmpty(), "a third block: " + menu.icon(VillageHallScreen.FIRST_ROW + 2));
			menu.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(menu.icon(4).is(ModBlocks.STOREHOUSE.asItem()), "the block's page header: " + menu.icon(4));
			ItemStack who = menu.icon(VillageHallScreen.FIRST_ROW);
			helper.assertTrue(who.is(Items.PAPER), "the jobless villager: " + who);
			helper.assertTrue(lore(who).contains(Component.translatable("screen.aliveworkplace.hall.give_job", jobless.getDisplayName(),
				Stations.name(ModVillagers.PORTER)).getString()), "the click's line: " + lore(who));
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW + 1).isEmpty(), "the nitwit or the farmer is offered the Storehouse: " + menu.icon(VillageHallScreen.FIRST_ROW + 1));
			menu.press(0, player);
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW).is(ModBlocks.STOREHOUSE.asItem()) && menu.icon(4).is(Items.CRAFTING_TABLE), "back to the list");
			helper.succeed();
		});
	}

	/** Picking a villager gives them the block: they're a Porter, the Storehouse is theirs and they walk to it. */
	//$ gametest_ticks_batch AREA '100' '"aPickTakesTheFreeBlock"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "aPickTakesTheFreeBlock")
	public void aPickTakesTheFreeBlock(GameTestHelper helper) {
		Villager jobless = stage(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos store = helper.absolutePos(STOREHOUSE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPage(helper, player);
			menu.press(VillageHallScreen.FIRST_ROW, player);
			menu.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(jobless.getVillagerData().getProfession() == ModVillagers.PORTER, "profession: " + jobless.getVillagerData().getProfession());
			helper.assertTrue(store.equals(jobless.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null)),
				"job site: " + jobless.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			helper.assertTrue(jobless.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(w -> w.getTarget().currentBlockPosition().equals(store)).orElse(false),
				"not walking to the Storehouse: " + jobless.getBrain().getMemory(MemoryModuleType.WALK_TARGET));
			helper.assertTrue(FreeJobsPage.at(level, hall, store) == null, "the Storehouse is still free");
			helper.assertTrue(menu.icon(4).is(Items.CRAFTING_TABLE), "not back on the list: " + menu.icon(4));
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW).is(ModBlocks.BUILDERS_BENCH.asItem()), "the list after: " + menu.icon(VillageHallScreen.FIRST_ROW));
			ItemStack bench = menu.icon(VillageHallScreen.FIRST_ROW);
			helper.assertTrue(lore(bench).contains(Component.translatable("screen.aliveworkplace.free_jobs.nobody").getString()), "the new Porter is still offered the bench: " + lore(bench));
			helper.succeed();
		});
	}

	/** A block claimed by someone else while its page was open: the pick changes nothing and the list comes back without it. */
	//$ gametest_ticks_batch AREA '100' '"aBlockClaimedMeanwhileIsRefused"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "aBlockClaimedMeanwhileIsRefused")
	public void aBlockClaimedMeanwhileIsRefused(GameTestHelper helper) {
		Villager jobless = stage(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos store = helper.absolutePos(STOREHOUSE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Villager quicker = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 10));
		quicker.setVillagerData(quicker.getVillagerData().setProfession(VillagerProfession.NONE));
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPage(helper, player);
			menu.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(menu.icon(4).is(ModBlocks.STOREHOUSE.asItem()), "setup: the Storehouse's page: " + menu.icon(4));
			FreeJobsPage.FreeBlock block = FreeJobsPage.at(level, hall, store);
			helper.assertTrue(block != null && FreeJobsPage.give(level, quicker, block.station()), "setup: the other villager couldn't take the Storehouse");
			int slot = -1;
			for (int i = VillageHallScreen.FIRST_ROW; i < ChoiceMenu.SIZE; i++) {
				if (lore(menu.icon(i)).contains(Component.translatable("screen.aliveworkplace.hall.give_job", jobless.getDisplayName(),
					Stations.name(ModVillagers.PORTER)).getString())) {
					slot = i;
				}
			}
			helper.assertTrue(slot >= 0, "setup: the jobless villager isn't on the Storehouse's page");
			try {
				menu.press(slot, player);
			} catch (RuntimeException e) {
				throw new net.minecraft.gametest.framework.GameTestAssertException("the pick threw " + e);
			}
			helper.assertTrue(jobless.getVillagerData().getProfession() == VillagerProfession.NONE, "they got a job anyway: " + jobless.getVillagerData().getProfession());
			helper.assertTrue(jobless.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(), "they got a job site: " + jobless.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			helper.assertTrue(store.equals(quicker.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null))
				&& quicker.getVillagerData().getProfession() == ModVillagers.PORTER, "the quicker villager lost the Storehouse");
			helper.assertTrue(menu.icon(4).is(Items.CRAFTING_TABLE), "not back on the list: " + menu.icon(4));
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW).is(ModBlocks.BUILDERS_BENCH.asItem()), "the taken Storehouse is still listed: " + menu.icon(VillageHallScreen.FIRST_ROW));
			helper.succeed();
		});
	}

	/** A villager who found work while the block's page was open isn't moved: their own job and block stay theirs. */
	//$ gametest_ticks_batch AREA '100' '"aVillagerWhoFoundWorkMeanwhileStays"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "aVillagerWhoFoundWorkMeanwhileStays")
	public void aVillagerWhoFoundWorkMeanwhileStays(GameTestHelper helper) {
		Villager jobless = stage(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos store = helper.absolutePos(STOREHOUSE);
		BlockPos bench = helper.absolutePos(BENCH);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPage(helper, player);
			menu.press(VillageHallScreen.FIRST_ROW, player);
			FreeJobsPage.FreeBlock other = FreeJobsPage.at(level, hall, bench);
			helper.assertTrue(other != null && FreeJobsPage.give(level, jobless, other.station()), "setup: the bench couldn't be given");
			menu.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(jobless.getVillagerData().getProfession() == ModVillagers.BUILDER, "they were moved: " + jobless.getVillagerData().getProfession());
			helper.assertTrue(bench.equals(jobless.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null)), "their bench: "
				+ jobless.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			helper.assertTrue(FreeJobsPage.at(level, hall, store) != null, "the Storehouse was taken");
			helper.assertTrue(menu.icon(4).is(ModBlocks.STOREHOUSE.asItem()) && menu.icon(VillageHallScreen.FIRST_ROW + 4).is(Items.PAPER),
				"the Storehouse's page doesn't say nobody can take it: " + menu.icon(4) + ", " + menu.icon(VillageHallScreen.FIRST_ROW + 4));
			helper.succeed();
		});
	}

	/** The job given from the page is the villager's own: a save and reload keeps it, and the block stays off the list. */
	//$ gametest_ticks_batch AREA '100' '"aGivenBlockSurvivesAReload"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "aGivenBlockSurvivesAReload")
	public void aGivenBlockSurvivesAReload(GameTestHelper helper) {
		Villager jobless = stage(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos store = helper.absolutePos(STOREHOUSE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPage(helper, player);
			menu.press(VillageHallScreen.FIRST_ROW, player);
			menu.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(jobless.getVillagerData().getProfession() == ModVillagers.PORTER, "setup: not a Porter: " + jobless.getVillagerData().getProfession());
			CompoundTag tag = new CompoundTag();
			jobless.saveWithoutId(tag);
			jobless.discard();
			Villager copy = EntityType.VILLAGER.create(level);
			copy.load(tag);
			level.addFreshEntity(copy);
			helper.assertTrue(copy.getVillagerData().getProfession() == ModVillagers.PORTER, "after a reload: " + copy.getVillagerData().getProfession());
			helper.assertTrue(store.equals(copy.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null)),
				"after a reload the job site: " + copy.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(FreeJobsPage.at(level, hall, store) == null, "after a reload the Storehouse is listed as free");
				ChoiceMenu again = openPage(helper, player);
				for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
					helper.assertTrue(!again.icon(slot).is(ModBlocks.STOREHOUSE.asItem()), "after a reload the Storehouse is on the list, slot " + slot);
				}
				helper.succeed();
			});
		});
	}
}
