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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * Bug B303: the hall's Free job blocks page named a block several jobs share by its first job only (every composter a
 * Farmer), so its other jobs could never be chosen, and identical lines piled up. Now each kind of block in a building is
 * one line with a count, a shared block says how many jobs it offers and lists them, and a click on it picks the job
 * first, then who takes it: they become that job, kept through a save and reload. A block with one job is as before.
 */
public class B303GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/** The hall (village reach 16) and {@code jobless} villagers without a job, nearest the hall first. */
	private static List<Villager> stage(GameTestHelper helper, int jobless) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		List<Villager> out = new ArrayList<>();
		for (int i = 0; i < jobless; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(9 - i, 2, 14));
			v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
			out.add(v);
		}
		return out;
	}

	private static ChoiceMenu openPage(GameTestHelper helper, ServerPlayer player) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(HALL));
		menu.press(HallPages.slot(FreeJobsPage.PAGE), player);
		helper.assertTrue(menu.icon(4).is(Items.CRAFTING_TABLE), "the Free job blocks page's header: " + menu.icon(4));
		return menu;
	}

	private static List<String> lore(ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	private static String name(ItemStack stack) {
		return stack.getHoverName().getString();
	}

	private static VillageHalls.FreeStation free(ServerLevel level, BlockPos hall, BlockPos pos) {
		return VillageHalls.freeStations(level, hall).stream().filter(f -> f.pos().equals(pos)).findFirst()
			.orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("not free: " + pos));
	}

	/**
	 * Four composters: one line, "Composter ×4: 4 jobs", each job under it. Each job is picked in turn through the page
	 * (the block, the job, the villager): the four villagers become a Farmer, an Orchard Keeper, a Florist and a
	 * Composter, at the composters, and keep those jobs through a save and reload and two seconds of their own brains.
	 */
	//$ gametest_ticks_batch AREA '160' '"aSharedComposterOffersEveryJob"'
	@GameTest(template = AREA, timeoutTicks = 160, batch = "aSharedComposterOffersEveryJob")
	public void aSharedComposterOffersEveryJob(GameTestHelper helper) {
		List<Villager> villagers = stage(helper, 4);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		List<BlockPos> composters = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			BlockPos at = new BlockPos(14 + i, 2, 4);
			helper.setBlock(at, Blocks.COMPOSTER);
			composters.add(helper.absolutePos(at));
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			List<VillagerProfession> jobs = VillageHalls.jobs(free(level, hall, composters.get(0)));
			helper.assertTrue(jobs.size() >= 4 && jobs.get(0) == VillagerProfession.FARMER, "the composter's jobs: " + jobs);
			for (VillagerProfession job : List.of(ModVillagers.ORCHARD_KEEPER, ModVillagers.FLORIST, ModVillagers.COMPOSTER)) {
				helper.assertTrue(jobs.contains(job), "the composter doesn't offer " + job + ": " + jobs);
			}
			ChoiceMenu menu = openPage(helper, player);
			ItemStack line = menu.icon(VillageHallScreen.FIRST_ROW);
			String title = Component.translatable("screen.aliveworkplace.free_jobs.shared_many", Blocks.COMPOSTER.getName(), jobs.size(), 4).getString();
			helper.assertTrue(line.is(Items.COMPOSTER) && name(line).equals(title), "the composters' line (" + title + "): " + name(line));
			helper.assertTrue(lore(line).contains(Component.translatable("screen.aliveworkplace.free_jobs.here", 4).getString()), "how many: " + lore(line));
			for (VillagerProfession job : jobs) {
				String jobLine = Component.translatable("screen.aliveworkplace.free_jobs.job_line", Stations.name(job)).getString();
				helper.assertTrue(lore(line).contains(jobLine), "the line doesn't list " + jobLine + ": " + lore(line));
			}
			helper.assertTrue(lore(line).contains(Component.translatable("screen.aliveworkplace.free_jobs.takers", 4).getString()), "who could take one: " + lore(line));
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW + 1).isEmpty(), "the composters on more than one line: " + menu.icon(VillageHallScreen.FIRST_ROW + 1));
			List<VillagerProfession> given = jobs.subList(0, 4);
			for (int i = 0; i < given.size(); i++) {
				VillagerProfession job = given.get(i);
				menu.press(VillageHallScreen.FIRST_ROW, player);
				helper.assertTrue(menu.icon(4).is(Items.COMPOSTER), "the job picker's header: " + menu.icon(4));
				for (int j = 0; j < jobs.size(); j++) {
					helper.assertTrue(name(menu.icon(VillageHallScreen.FIRST_ROW + j)).equals(Stations.name(jobs.get(j)).getString()),
						"job " + j + " on the picker: " + name(menu.icon(VillageHallScreen.FIRST_ROW + j)));
				}
				menu.press(VillageHallScreen.FIRST_ROW + jobs.indexOf(job), player);
				helper.assertTrue(name(menu.icon(4)).equals(Stations.name(job).getString()), "the takers page isn't for " + job + ": " + name(menu.icon(4)));
				helper.assertTrue(name(menu.icon(0)).equals(Component.translatable("screen.aliveworkplace.free_jobs.back_jobs").getString()), "back: " + name(menu.icon(0)));
				menu.press(VillageHallScreen.FIRST_ROW, player);
				Villager taker = villagers.stream().filter(v -> v.getVillagerData().getProfession() == job).findFirst().orElse(null);
				helper.assertTrue(taker != null, "nobody became " + job);
				BlockPos site = taker.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
				helper.assertTrue(composters.contains(site), job + "'s job site: " + site);
				helper.assertTrue(menu.icon(4).is(Items.CRAFTING_TABLE), "not back on the list after " + job + ": " + menu.icon(4));
				if (i == 0) {
					String three = Component.translatable("screen.aliveworkplace.free_jobs.shared_many", Blocks.COMPOSTER.getName(), jobs.size(), 3).getString();
					helper.assertTrue(name(menu.icon(VillageHallScreen.FIRST_ROW)).equals(three), "the count after one was taken: " + name(menu.icon(VillageHallScreen.FIRST_ROW)));
				}
			}
			Set<VillagerProfession> have = new HashSet<>();
			villagers.forEach(v -> have.add(v.getVillagerData().getProfession()));
			helper.assertTrue(have.equals(new HashSet<>(given)), "the jobs given: " + have);
			helper.assertTrue(FreeJobsPage.blocks(level, hall).isEmpty(), "a composter still free: " + FreeJobsPage.blocks(level, hall));
			// a save and reload of the Orchard Keeper
			Villager keeper = villagers.stream().filter(v -> v.getVillagerData().getProfession() == ModVillagers.ORCHARD_KEEPER).findFirst().orElseThrow();
			BlockPos site = keeper.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElseThrow();
			CompoundTag tag = new CompoundTag();
			keeper.saveWithoutId(tag);
			keeper.discard();
			Villager copy = EntityType.VILLAGER.create(level);
			copy.load(tag);
			level.addFreshEntity(copy);
			helper.runAfterDelay(40, () -> {
				helper.assertTrue(copy.getVillagerData().getProfession() == ModVillagers.ORCHARD_KEEPER, "after a reload: " + copy.getVillagerData().getProfession());
				helper.assertTrue(site.equals(copy.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null)),
					"after a reload the job site: " + copy.getBrain().getMemory(MemoryModuleType.JOB_SITE));
				for (Villager v : villagers) {
					if (v != keeper) {
						helper.assertTrue(given.contains(v.getVillagerData().getProfession()), "a villager lost the job they were given: " + v.getVillagerData().getProfession());
					}
				}
				helper.assertTrue(FreeJobsPage.blocks(level, hall).isEmpty(), "after a reload a composter is listed as free");
				helper.succeed();
			});
		});
	}

	/**
	 * A barrel offers every job that takes a barrel in this game (in this mod alone, only the Fisherman): a block with one
	 * job keeps its old line (the job's name, no count) and its click goes straight to who could take it.
	 */
	//$ gametest_ticks_batch AREA '100' '"aBarrelOffersItsJobs"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "aBarrelOffersItsJobs")
	public void aBarrelOffersItsJobs(GameTestHelper helper) {
		Villager jobless = stage(helper, 1).get(0);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos barrel = helper.absolutePos(new BlockPos(16, 2, 4));
		helper.setBlock(new BlockPos(16, 2, 4), Blocks.BARREL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			VillageHalls.FreeStation station = free(level, hall, barrel);
			List<VillagerProfession> jobs = VillageHalls.jobs(station);
			helper.assertTrue(jobs.get(0) == VillagerProfession.FISHERMAN, "the barrel's first job: " + jobs);
			for (VillagerProfession p : BuiltInRegistries.VILLAGER_PROFESSION) {
				if (p != VillagerProfession.NONE && p != VillagerProfession.NITWIT && p != ModVillagers.TRAINER_LEADER && Stations.available(p)
					&& p.acquirableJobSite().test(station.poi()) && p.heldJobSite().test(station.poi())) {
					helper.assertTrue(jobs.contains(p), "the barrel doesn't offer " + p + ": " + jobs);
				}
			}
			VillagerProfession last = jobs.get(jobs.size() - 1);
			ChoiceMenu menu = openPage(helper, player);
			ItemStack line = menu.icon(VillageHallScreen.FIRST_ROW);
			helper.assertTrue(line.is(Items.BARREL), "the barrel's line: " + line);
			if (jobs.size() == 1) {
				helper.assertTrue(name(line).equals(Stations.name(VillagerProfession.FISHERMAN).getString()), "a one-job block's line changed: " + name(line));
				helper.assertTrue(lore(line).contains(Component.translatable("screen.aliveworkplace.free_jobs.click").getString()), "its click: " + lore(line));
				menu.press(VillageHallScreen.FIRST_ROW, player);
				helper.assertTrue(name(menu.icon(4)).equals(Stations.name(VillagerProfession.FISHERMAN).getString()) && menu.icon(4).is(Items.BARREL),
					"a one-job block's click doesn't go straight to who could take it: " + name(menu.icon(4)));
				helper.assertTrue(name(menu.icon(0)).equals(Component.translatable("screen.aliveworkplace.free_jobs.back").getString()), "back: " + name(menu.icon(0)));
			} else {
				helper.assertTrue(name(line).equals(Component.translatable("screen.aliveworkplace.free_jobs.shared", Blocks.BARREL.getName(), jobs.size()).getString()),
					"the barrel's line: " + name(line));
				menu.press(VillageHallScreen.FIRST_ROW, player);
				for (int j = 0; j < jobs.size(); j++) {
					helper.assertTrue(name(menu.icon(VillageHallScreen.FIRST_ROW + j)).equals(Stations.name(jobs.get(j)).getString()), "job " + j + ": " + name(menu.icon(VillageHallScreen.FIRST_ROW + j)));
				}
				menu.press(VillageHallScreen.FIRST_ROW + jobs.size() - 1, player);
			}
			menu.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(jobless.getVillagerData().getProfession() == last, "the job given: " + jobless.getVillagerData().getProfession() + ", not " + last);
			helper.assertTrue(barrel.equals(jobless.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null)), "job site: "
				+ jobless.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			helper.succeed();
		});
	}

	/**
	 * Grouping: two barrels in a finished Well are one line, "Fisherman ×2", "In the Well", with how many; the Storehouse
	 * there and a barrel out in the open keep a line each, as before, nearest first. A pick takes the nearest of the two;
	 * the one left is then a plain "Fisherman" line, placed by its own distance.
	 */
	//$ gametest_ticks_batch AREA '100' '"freeBlocksAreGroupedByBuilding"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "freeBlocksAreGroupedByBuilding")
	public void freeBlocksAreGroupedByBuilding(GameTestHelper helper) {
		Villager jobless = stage(helper, 1).get(0);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlueprintData.Placement well = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 2, 5)), Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(StarterBlueprints.WELL.id(), well, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(well));
		helper.setBlock(new BlockPos(7, 2, 10), Blocks.BARREL); // in the Well, nearest the hall
		helper.setBlock(new BlockPos(5, 2, 8), ModBlocks.STOREHOUSE); // in the Well
		helper.setBlock(new BlockPos(14, 2, 3), Blocks.BARREL); // in the open
		helper.setBlock(new BlockPos(3, 2, 6), Blocks.BARREL); // in the Well, furthest
		BlockPos nearest = helper.absolutePos(new BlockPos(7, 2, 10));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(FreeJobsPage.blocks(level, hall).size() == 4, "free blocks: " + FreeJobsPage.blocks(level, hall));
			helper.assertTrue(FreeJobsPage.groups(FreeJobsPage.blocks(level, hall)).size() == 3, "lines: " + FreeJobsPage.groups(FreeJobsPage.blocks(level, hall)));
			ChoiceMenu menu = openPage(helper, player);
			String fisherman = Stations.name(VillagerProfession.FISHERMAN).getString();
			String inWell = Component.translatable("screen.aliveworkplace.free_jobs.in", Blueprints.displayName(StarterBlueprints.WELL.id())).getString();
			ItemStack barrels = menu.icon(VillageHallScreen.FIRST_ROW);
			String two = Component.translatable("screen.aliveworkplace.free_jobs.many", Stations.name(VillagerProfession.FISHERMAN), 2).getString();
			helper.assertTrue(barrels.is(Items.BARREL) && name(barrels).equals(two), "the Well's barrels (" + two + "): " + name(barrels));
			helper.assertTrue(lore(barrels).get(0).equals(inWell), "the building first under the name: " + lore(barrels));
			helper.assertTrue(lore(barrels).contains(Component.translatable("screen.aliveworkplace.free_jobs.here", 2).getString()), "how many: " + lore(barrels));
			helper.assertTrue(lore(barrels).contains(VillageHallScreen.where(hall, nearest).getString()), "the nearest's distance: " + lore(barrels));
			ItemStack store = menu.icon(VillageHallScreen.FIRST_ROW + 1);
			helper.assertTrue(store.is(ModBlocks.STOREHOUSE.asItem()) && name(store).equals(Stations.name(ModVillagers.PORTER).getString()), "the Storehouse second: " + name(store));
			helper.assertTrue(lore(store).stream().noneMatch(l -> l.equals(Component.translatable("screen.aliveworkplace.free_jobs.here", 1).getString())), "a lone block counted: " + lore(store));
			ItemStack open = menu.icon(VillageHallScreen.FIRST_ROW + 2);
			helper.assertTrue(open.is(Items.BARREL) && name(open).equals(fisherman), "the barrel in the open third, as before: " + name(open));
			helper.assertTrue(lore(open).contains(Component.translatable("screen.aliveworkplace.free_jobs.outside").getString()), "in the open: " + lore(open));
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW + 3).isEmpty(), "a fourth line: " + menu.icon(VillageHallScreen.FIRST_ROW + 3));
			menu.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(menu.icon(4).is(Items.BARREL) && name(menu.icon(4)).equals(fisherman), "the barrels' click: " + name(menu.icon(4)));
			menu.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(jobless.getVillagerData().getProfession() == VillagerProfession.FISHERMAN, "not a Fisherman: " + jobless.getVillagerData().getProfession());
			helper.assertTrue(nearest.equals(jobless.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null)), "not the nearest barrel: "
				+ jobless.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			// the Well's barrel left is the furthest block now: the Storehouse, the barrel in the open, then it, counted one
			ItemStack after = menu.icon(VillageHallScreen.FIRST_ROW + 2);
			helper.assertTrue(menu.icon(4).is(Items.CRAFTING_TABLE) && name(after).equals(fisherman) && lore(after).get(0).equals(inWell),
				"the Well's one barrel left: " + name(after) + " " + lore(after));
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW).is(ModBlocks.STOREHOUSE.asItem()) && menu.icon(VillageHallScreen.FIRST_ROW + 3).isEmpty(),
				"the list after: " + menu.icon(VillageHallScreen.FIRST_ROW) + ", " + menu.icon(VillageHallScreen.FIRST_ROW + 3));
			helper.succeed();
		});
	}
}
