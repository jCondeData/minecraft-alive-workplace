package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA lane (B1173, owner 2026-11-11): the Village Hall's People table (ROADMAP 40.2) must show every villager with a job
 * under that job. The owner's Emberwood listed 15 working and no Undertaker: he was counted with the jobless. These go
 * through the player's own entry points (a right-click on the hall, the sidebar's People, the table's scroll as the
 * client sends it): every job the mod registers at its own work block, an Undertaker and a Farmer whose work block was
 * broken (their job kept, as vanilla keeps a villager's job once he has traded), and a plain jobless villager (the
 * control, which passes today).
 */
public class QaHallPeopleJobs1111cBugTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final String NO_JOB = "screen.aliveworkplace.hall.people.no_job";

	/** The village kept to this test's area (the hall's reach set while the test runs). */
	private static void village(GameTestHelper helper, int radius) {
		Leftovers.clear(helper);
		HallSections.forget();
		int was = VillageHalls.RADIUS;
		VillageHalls.RADIUS = radius;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = was);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	private static Villager villager(GameTestHelper helper, int x, int z) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		v.setNoAi(true); // they stay where they're put, and keep their jobs
		return v;
	}

	/** Opens the hall as a player does (a right-click on the block) and its People section (the sidebar's click). */
	private static ChoiceMenu openPeople(GameTestHelper helper, ServerPlayer player) {
		BlockPos hall = helper.absolutePos(HALL);
		player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		helper.useBlock(HALL, player);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "the hall didn't open: " + player.containerMenu);
		ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
		helper.assertTrue(menu.clickMenuButton(player, Section.PEOPLE.ordinal()), "the sidebar didn't take People");
		helper.assertTrue(HallSections.current(menu) == Section.PEOPLE, "People isn't open: " + HallSections.current(menu));
		return menu;
	}

	/** The rows loaded now, by villager. */
	private static void collect(ChoiceMenu menu, Map<String, CompoundTag> into) {
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			CompoundTag row = HallPeople.row(menu.icon(slot));
			if (row != null) {
				into.put(row.getString("uuid"), row);
			}
		}
	}

	/** Every row of the table, scrolled through as the client scrolls it (a menu-button click of SCROLL + the first shown). */
	private static Map<String, CompoundTag> allRows(GameTestHelper helper, ServerPlayer player, ChoiceMenu menu) {
		Map<String, CompoundTag> rows = new LinkedHashMap<>();
		collect(menu, rows);
		int total = menu.state(HallSections.PEOPLE_TOTAL);
		for (int first = VillageHallScreen.PER_PAGE; first < total; first += VillageHallScreen.PER_PAGE) {
			helper.assertTrue(menu.clickMenuButton(player, HallPeople.SCROLL + first), "the scroll wasn't taken");
			helper.assertTrue(HallSections.current(menu) == Section.PEOPLE, "the scroll left People");
			collect(menu, rows);
		}
		return rows;
	}

	private static String counts(ChoiceMenu menu) {
		return menu.state(HallSections.PEOPLE_TOTAL) + " · " + menu.state(HallSections.PEOPLE_WORKING) + " working · "
			+ menu.state(HallSections.PEOPLE_JOBLESS) + " jobless";
	}

	private static ResourceLocation key(VillagerProfession job) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(job);
	}

	/** A job's work block as a new villager takes it: its POI and a block state that is one (null: none in this game). */
	private record Station(ResourceKey<PoiType> poi, BlockState state) {
	}

	/** The block {@code job} takes, preferring the mod's own (the Undertaker's Table over the Cleric's brewing stand). */
	private static Station stationOf(VillagerProfession job) {
		Station fallback = null;
		for (Holder.Reference<PoiType> poi : BuiltInRegistries.POINT_OF_INTEREST_TYPE.holders().toList()) {
			if (!job.acquirableJobSite().test(poi) || poi.value().matchingStates().isEmpty()) {
				continue;
			}
			BlockState any = poi.value().matchingStates().iterator().next();
			BlockState state = poi.value().matchingStates().contains(any.getBlock().defaultBlockState()) ? any.getBlock().defaultBlockState() : any;
			Station station = new Station(poi.key(), state);
			if (poi.key().location().getNamespace().equals("aliveworkplace")) {
				return station;
			}
			if (fallback == null) {
				fallback = station;
			}
		}
		return fallback;
	}

	/** One villager hired for a job at its block. */
	private record Hire(VillagerProfession job, Villager villager, BlockPos station, ResourceKey<PoiType> poi) {
	}

	/**
	 * Every job the mod registers (read from the registry, so a new job is covered by itself), and a vanilla Farmer, each
	 * held by a villager at its own work block: each row shows that job (not "No job"), and the header counts them all
	 * working and none jobless. The Steward works at the hall itself; the Builder is hired as the Builder tests hire him.
	 * Jobs with no work block in this game (the Legend, the retired Crafter) aren't expected.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHallPeopleEveryJob"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallPeopleEveryJob")
	public void everyModJobAtItsWorkstationIsListedUnderItsJob(GameTestHelper helper) {
		village(helper, 20);
		List<VillagerProfession> jobs = new ArrayList<>();
		for (VillagerProfession job : BuiltInRegistries.VILLAGER_PROFESSION) {
			if (key(job).getNamespace().equals("aliveworkplace")) {
				jobs.add(job);
			}
		}
		jobs.add(VillagerProfession.FARMER);
		// The grid: work blocks at z = 2, 5, 8 ... 26 and x = 2, 5, 8 ... 26, each villager just south of his block.
		List<BlockPos> spots = new ArrayList<>();
		for (int z = 2; z <= 26; z += 3) {
			for (int x = 2; x <= 26; x += 3) {
				spots.add(new BlockPos(x, 2, z));
			}
		}
		List<String> noStation = new ArrayList<>();
		List<Hire> hires = new ArrayList<>();
		int next = 0;
		for (VillagerProfession job : jobs) {
			if (job == ModVillagers.STEWARD) {
				Villager v = villager(helper, HALL.getX() - 1, HALL.getZ() + 1);
				hires.add(new Hire(job, v, HALL, ModVillagers.VILLAGE_HALL_POI));
				continue;
			}
			if (job == ModVillagers.BUILDER) {
				BlockPos at = spots.get(next++);
				helper.setBlock(at, ModBlocks.BLUEPRINT_TABLE);
				hires.add(new Hire(job, villager(helper, at.getX(), at.getZ() + 1), at, ModVillagers.BLUEPRINT_TABLE_POI));
				continue;
			}
			Station station = stationOf(job);
			if (station == null) {
				noStation.add(key(job).toString());
				continue;
			}
			helper.assertTrue(next < spots.size(), "setup: more jobs than room in the area (" + jobs.size() + ")");
			BlockPos at = spots.get(next++);
			helper.setBlock(at, station.state());
			hires.add(new Hire(job, villager(helper, at.getX(), at.getZ() + 1), at, station.poi()));
		}
		helper.assertTrue(hires.stream().anyMatch(h -> h.job() == ModVillagers.UNDERTAKER), "setup: no Undertaker hired; no work block: " + noStation);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(3, () -> {
			List<String> setup = new ArrayList<>();
			List<Hire> working = new ArrayList<>();
			for (Hire hire : hires) {
				if (hire.job() == ModVillagers.BUILDER) {
					Builders.employ(helper.getLevel(), hire.villager(), helper.absolutePos(hire.station()));
				} else {
					Jobs.employ(helper.getLevel(), hire.villager(), helper.absolutePos(hire.station()), hire.poi(), hire.job());
				}
				hire.villager().setCustomName(net.minecraft.network.chat.Component.literal(key(hire.job()).getPath()));
				if (hire.villager().getVillagerData().getProfession() == hire.job()) {
					working.add(hire);
				} else {
					setup.add(key(hire.job()) + " (became " + key(hire.villager().getVillagerData().getProfession()) + ")");
				}
			}
			helper.assertTrue(working.size() >= 30, "setup: only " + working.size() + " of " + hires.size() + " villagers took their job; refused: " + setup);
			ChoiceMenu menu = openPeople(helper, player);
			Map<String, CompoundTag> rows = allRows(helper, player, menu);
			List<String> missing = new ArrayList<>();
			List<String> untranslated = new ArrayList<>();
			Language lang = Language.getInstance();
			for (Hire hire : working) {
				CompoundTag row = rows.get(hire.villager().getStringUUID());
				String expected = "entity.minecraft.villager." + hire.job().name();
				boolean onSite = hire.villager().getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent();
				if (row == null) {
					missing.add(key(hire.job()) + " (no row at all)");
				} else if (!row.getString("job").equals(expected) || row.getString("kind").equals("jobless")) {
					missing.add(key(hire.job()) + " (shown as " + row.getString("kind") + " '" + row.getString("job") + "', job site memory "
						+ (onSite ? "set" : "empty") + ", available " + Stations.available(hire.job()) + ")");
				} else if (!lang.has(expected)) {
					untranslated.add(expected);
				}
			}
			int jobless = hires.size() - working.size();
			helper.assertTrue(missing.isEmpty(), missing.size() + " of " + working.size() + " jobs aren't shown under their job: " + missing
				+ "; header " + counts(menu) + (setup.isEmpty() ? "" : "; refused at setup: " + setup) + (noStation.isEmpty() ? "" : "; no work block: " + noStation));
			helper.assertTrue(menu.state(HallSections.PEOPLE_TOTAL) == hires.size() && menu.state(HallSections.PEOPLE_WORKING) == working.size()
				&& menu.state(HallSections.PEOPLE_JOBLESS) == jobless, "the header reads " + counts(menu) + ", expected " + hires.size() + " · "
				+ working.size() + " working · " + jobless + " jobless");
			helper.assertTrue(untranslated.isEmpty(), "a job's name isn't in the language file: " + untranslated);
			helper.succeed();
		});
	}

	/**
	 * B1173's likeliest cause: a worker whose work block was broken (his job kept, as vanilla keeps it once he has traded,
	 * but the brain's JOB_SITE memory gone). An Undertaker and a vanilla Farmer: both rows still show their job, not "No
	 * job", and the header counts both working and nobody jobless.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHallPeopleBrokenStation"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallPeopleBrokenStation")
	public void anUndertakerWhoseWorkstationWasBrokenStillShowsHisJob(GameTestHelper helper) {
		village(helper, 14);
		BlockPos table = new BlockPos(8, 2, 8);
		BlockPos composter = new BlockPos(20, 2, 8);
		helper.setBlock(table, ModBlocks.UNDERTAKERS_TABLE);
		helper.setBlock(composter, Blocks.COMPOSTER);
		Villager undertaker = villager(helper, 8, 9);
		Villager farmer = villager(helper, 20, 9);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(3, () -> {
			Jobs.employ(helper.getLevel(), undertaker, helper.absolutePos(table), ModVillagers.UNDERTAKERS_TABLE_POI, ModVillagers.UNDERTAKER);
			Jobs.employ(helper.getLevel(), farmer, helper.absolutePos(composter), PoiTypes.FARMER, VillagerProfession.FARMER);
			// Before: both at work, both listed under their jobs (so what follows is the broken block's doing).
			ChoiceMenu before = openPeople(helper, player);
			Map<String, CompoundTag> was = allRows(helper, player, before);
			helper.assertTrue(was.get(undertaker.getStringUUID()) != null
				&& was.get(undertaker.getStringUUID()).getString("job").equals("entity.minecraft.villager." + ModVillagers.UNDERTAKER.name()),
				"the Undertaker at his table isn't shown as one: " + was.get(undertaker.getStringUUID()) + "; header " + counts(before));
			helper.assertTrue(before.state(HallSections.PEOPLE_WORKING) == 2 && before.state(HallSections.PEOPLE_JOBLESS) == 0,
				"at their blocks the header reads " + counts(before));
			player.closeContainer();

			// Their work blocks broken: the job stays (they've traded), the job site memory goes.
			helper.setBlock(table, Blocks.AIR);
			helper.setBlock(composter, Blocks.AIR);
			undertaker.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
			farmer.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
			helper.assertTrue(undertaker.getVillagerData().getProfession() == ModVillagers.UNDERTAKER
				&& farmer.getVillagerData().getProfession() == VillagerProfession.FARMER,
				"setup: a broken block took the job: " + undertaker.getVillagerData() + " " + farmer.getVillagerData());

			ChoiceMenu menu = openPeople(helper, player);
			Map<String, CompoundTag> rows = allRows(helper, player, menu);
			CompoundTag u = rows.get(undertaker.getStringUUID());
			CompoundTag f = rows.get(farmer.getStringUUID());
			helper.assertTrue(u != null && f != null, "a row is missing: Undertaker " + u + ", Farmer " + f);
			helper.assertTrue(!u.getString("kind").equals("jobless")
				&& u.getString("job").equals("entity.minecraft.villager." + ModVillagers.UNDERTAKER.name()),
				"the Undertaker whose table was broken is shown as " + u.getString("kind") + " '" + u.getString("job") + "' (B1173)");
			helper.assertTrue(!f.getString("kind").equals("jobless") && f.getString("job").equals("entity.minecraft.villager." + VillagerProfession.FARMER.name()),
				"the Farmer whose composter was broken is shown as " + f.getString("kind") + " '" + f.getString("job") + "'");
			helper.assertTrue(menu.state(HallSections.PEOPLE_TOTAL) == 2 && menu.state(HallSections.PEOPLE_WORKING) == 2
				&& menu.state(HallSections.PEOPLE_JOBLESS) == 0, "with their blocks broken the header reads " + counts(menu) + ", expected 2 · 2 working · 0 jobless");
			helper.succeed();
		});
	}

	/** The control (passes today): a villager with no job is listed as "No job" and counted jobless, beside one Farmer at work. */
	//$ gametest_ticks_batch AREA '200' '"qaHallPeopleJoblessControl"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallPeopleJoblessControl")
	public void aVillagerWithNoJobIsListedJobless(GameTestHelper helper) {
		village(helper, 14);
		BlockPos composter = new BlockPos(20, 2, 8);
		helper.setBlock(composter, Blocks.COMPOSTER);
		Villager farmer = villager(helper, 20, 9);
		Villager pip = villager(helper, 10, 20);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(3, () -> {
			Jobs.employ(helper.getLevel(), farmer, helper.absolutePos(composter), PoiTypes.FARMER, VillagerProfession.FARMER);
			helper.assertTrue(pip.getVillagerData().getProfession() == VillagerProfession.NONE, "setup: Pip has a job: " + pip.getVillagerData());
			ChoiceMenu menu = openPeople(helper, player);
			Map<String, CompoundTag> rows = allRows(helper, player, menu);
			CompoundTag p = rows.get(pip.getStringUUID());
			CompoundTag f = rows.get(farmer.getStringUUID());
			helper.assertTrue(p != null && p.getString("kind").equals("jobless") && p.getString("job").equals(NO_JOB) && p.getInt("level") == 0,
				"the jobless villager's row: " + p);
			helper.assertTrue(f != null && f.getString("kind").equals("worker") && f.getString("job").equals("entity.minecraft.villager." + VillagerProfession.FARMER.name()),
				"the Farmer's row: " + f);
			helper.assertTrue(menu.state(HallSections.PEOPLE_TOTAL) == 2 && menu.state(HallSections.PEOPLE_WORKING) == 1
				&& menu.state(HallSections.PEOPLE_JOBLESS) == 1, "the header reads " + counts(menu) + ", expected 2 · 1 working · 1 jobless");
			helper.assertTrue(Language.getInstance().has(NO_JOB), "no text for " + NO_JOB);
			helper.succeed();
		});
	}
}
