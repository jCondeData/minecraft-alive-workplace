package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.tailor.Tailors;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.RandomJobs;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.WorkerLimits;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * ROADMAP 36.17: a job block with several jobs gives one at random. A jobless villager taking a shared block by
 * themselves gets one of its jobs, each weighted {@code 1 / (1 + how many the village has)}; a block with one job, a
 * job picked by item or by order, and {@code randomStationJobs} off give the job as before; the job drawn stays through
 * a save and reload.
 */
public class RandomJobsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos STATION = new BlockPos(3, 2, 3);

	private static String name(VillagerProfession job) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(job).toString();
	}

	/**
	 * What vanilla's AssignProfessionFromJobSite does when a jobless villager reaches the block they picked: inside their
	 * own brain's run, the block is their job site and they're given the first job registered at it.
	 */
	static Villager takesByThemselves(GameTestHelper helper, BlockPos station, BlockPos standing, long seed) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(station);
		Villager villager = helper.spawn(EntityType.VILLAGER, standing);
		villager.setNoAi(true);
		// (a seed mixed from the test's: nearby seeds start a legacy random almost alike, so 24 such villagers drew alike)
		villager.getRandom().setSeed(RandomSource.create(seed).nextLong());
		VillagerProfession vanilla = level.getPoiManager().getType(at)
			.flatMap(h -> BuiltInRegistries.VILLAGER_PROFESSION.stream().filter(p -> p.heldJobSite().test(h)).findFirst())
			.orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("setup: no job at " + at));
		villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), at));
		Villager before = WorkerLimits.thinker();
		WorkerLimits.thinking(villager);
		try {
			villager.setVillagerData(villager.getVillagerData().setProfession(vanilla));
		} finally {
			WorkerLimits.thinking(before);
		}
		return villager;
	}

	/** Spots in the area, one per villager, so a village of them can stand together. */
	private static BlockPos spot(int i) {
		return new BlockPos(5 + i % 8, 2, 5 + (i / 8) % 8);
	}

	/**
	 * Every block that offers several jobs, over 200 hires drawn with a fixed random (each hire then counts towards the
	 * village's): every job it offers in this game comes up, the fewest-first weighting keeps them even, and the drawn
	 * jobs are exactly those its tooltip names (the Trainer Leader aside).
	 */
	//$ gametest_batch AREA '"randomJobsEvery"'
	@GameTest(template = AREA, batch = "randomJobsEvery")
	public void everyJobOfASharedBlockComesUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper); // no village hall near, so no class rule leaves a job out
		ServerLevel level = helper.getLevel();
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		RandomSource random = RandomSource.create(3617L);
		List<String> shared = new ArrayList<>();
		for (Stations.Station station : Stations.ALL) {
			if (station.jobs().size() < 2 || !station.byItself()) {
				continue;
			}
			List<VillagerProfession> jobs = RandomJobs.candidates(level, villager, station);
			// the block's own job (vanilla's, always given), then the others this game has, the Trainer Leader aside
			List<VillagerProfession> offered = new ArrayList<>(List.of(station.jobs().get(0).profession().get()));
			station.jobs().stream().skip(1).filter(Stations::available).map(j -> j.profession().get())
				.filter(p -> p != ModVillagers.TRAINER_LEADER).forEach(offered::add);
			helper.assertTrue(jobs.equals(offered), "at the " + station.block() + ": drawn from " + jobs + ", offered " + offered);
			if (jobs.size() < 2) {
				continue;
			}
			shared.add(BuiltInRegistries.BLOCK.getKey(station.block()).getPath());
			// each job drawn keeps the block (vanilla's checks on a job site would take it away from a job that doesn't hold it)
			var poi = PoiTypes.forState(station.block().defaultBlockState()).orElseThrow();
			for (VillagerProfession job : jobs) {
				helper.assertTrue(job.heldJobSite().test(poi), name(job) + " doesn't hold the " + station.block() + " they could draw");
			}
			Map<VillagerProfession, Integer> counts = new HashMap<>();
			for (int i = 0; i < 200; i++) {
				counts.merge(RandomJobs.pick(jobs, counts, random), 1, Integer::sum);
			}
			for (VillagerProfession job : jobs) {
				int n = counts.getOrDefault(job, 0);
				helper.assertTrue(n >= 200 / jobs.size() / 2, "at the " + station.block() + ", " + name(job) + " came up " + n + " times of 200: " + counts);
			}
		}
		// Without Cobblemon: the composter, blast furnace, fletching and smithing tables, cauldron, loom, lectern,
		// cartography table, brewing stand, smoker, stonecutter, grindstone and the Shop Counter (the Training Post's
		// second job is the Tutor, Cobblemon's like the post's Trainer, so it draws from one there).
		helper.assertTrue(shared.containsAll(List.of("composter", "blast_furnace", "fletching_table", "smithing_table", "cauldron", "loom", "lectern",
			"cartography_table", "brewing_stand", "smoker", "stonecutter", "grindstone", "shop_counter")), "shared blocks: " + shared);
		helper.succeed();
	}

	/** The weighting: a job the village has ten of is drawn far less often than one it has none of. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theJobsTheVillageHasFewestOfAreLikeliest(GameTestHelper helper) {
		List<VillagerProfession> jobs = List.of(VillagerProfession.FARMER, ModVillagers.ORCHARD_KEEPER, ModVillagers.FLORIST, ModVillagers.COMPOSTER);
		Map<VillagerProfession, Integer> village = Map.of(VillagerProfession.FARMER, 10, ModVillagers.ORCHARD_KEEPER, 1);
		RandomSource random = RandomSource.create(17L);
		Map<VillagerProfession, Integer> drawn = new HashMap<>();
		for (int i = 0; i < 2000; i++) {
			drawn.merge(RandomJobs.pick(jobs, village, random), 1, Integer::sum);
		}
		int farmers = drawn.getOrDefault(VillagerProfession.FARMER, 0);
		int keepers = drawn.getOrDefault(ModVillagers.ORCHARD_KEEPER, 0);
		int florists = drawn.getOrDefault(ModVillagers.FLORIST, 0);
		// weights 1/11, 1/2, 1, 1: about 70, 385, 770 and 770 of 2000
		helper.assertTrue(farmers > 0 && farmers < 150, "farmers drawn: " + drawn);
		helper.assertTrue(keepers > farmers * 2 && keepers < florists, "orchard keepers drawn: " + drawn);
		helper.assertTrue(florists > 600 && drawn.getOrDefault(ModVillagers.COMPOSTER, 0) > 600, "florists and composters drawn: " + drawn);
		// A new village (no workers yet) draws evenly.
		Map<VillagerProfession, Integer> even = new HashMap<>();
		for (int i = 0; i < 2000; i++) {
			even.merge(RandomJobs.pick(jobs, Map.of(), random), 1, Integer::sum);
		}
		helper.assertTrue(jobs.stream().allMatch(j -> even.getOrDefault(j, 0) > 400), "a new village: " + even);
		helper.succeed();
	}

	/**
	 * The player's own entry point, vanilla's: 24 jobless villagers take a composter by themselves, one after another,
	 * staying in the village. Each becomes one of its jobs, and every one of the four comes up, not 24 farmers.
	 */
	//$ gametest_batch AREA '"randomJobsComposter"'
	@GameTest(template = AREA, batch = "randomJobsComposter")
	public void joblessVillagersAtAComposterTakeEachOfItsJobs(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(STATION, Blocks.COMPOSTER);
		Stations.Station composter = Stations.at(Blocks.COMPOSTER).orElseThrow();
		Map<VillagerProfession, Integer> took = new HashMap<>();
		for (int i = 0; i < 24; i++) {
			Villager v = takesByThemselves(helper, STATION, spot(i), 1000L + i);
			VillagerProfession job = v.getVillagerData().getProfession();
			helper.assertTrue(composter.has(job), "a villager at the composter became a " + name(job));
			took.merge(job, 1, Integer::sum);
		}
		for (VillagerProfession job : List.of(VillagerProfession.FARMER, ModVillagers.ORCHARD_KEEPER, ModVillagers.FLORIST, ModVillagers.COMPOSTER)) {
			helper.assertTrue(took.getOrDefault(job, 0) >= 2, name(job) + " came up " + took.getOrDefault(job, 0) + " times of 24: " + took);
		}
		helper.succeed();
	}

	/**
	 * In play, with nothing but vanilla's brain: a jobless villager by a lectern walks to it and takes one of its jobs
	 * (Librarian, Scholar or Teacher), works there, and keeps it.
	 */
	//$ gametest_ticks_batch AREA '1200' '"randomJobsInPlay"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "randomJobsInPlay")
	public void aJoblessVillagerTakesOneOfTheBlocksJobsInPlay(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(STATION, Blocks.LECTERN);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		Stations.Station lectern = Stations.at(Blocks.LECTERN).orElseThrow();
		VillagerProfession[] taken = {null};
		long[] since = {0};
		helper.succeedWhen(() -> {
			VillagerProfession job = villager.getVillagerData().getProfession();
			helper.assertTrue(job != VillagerProfession.NONE, "still jobless");
			helper.assertTrue(lectern.has(job), "by the lectern: a " + name(job));
			helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos().equals(helper.absolutePos(STATION))).orElse(false),
				"works at " + villager.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			if (taken[0] == null) {
				taken[0] = job;
				since[0] = helper.getTick();
			}
			helper.assertTrue(job == taken[0], "took " + name(taken[0]) + ", now " + name(job));
			helper.assertTrue(helper.getTick() - since[0] >= 200, "kept for " + (helper.getTick() - since[0]) + " ticks");
		});
	}

	/**
	 * A block with one job is unchanged: a barrel always gives the Fisherman, a Storehouse the Porter; and a shared block
	 * whose other jobs are switched off (the loom with {@code tailors} off) gives its one job left. Tailors on again: the
	 * loom gives the Tailor too.
	 */
	//$ gametest_batch AREA '"randomJobsSingle"'
	@GameTest(template = AREA, batch = "randomJobsSingle")
	public void aSingleJobBlockIsUnchanged(GameTestHelper helper) {
		Leftovers.clear(helper);
		boolean tailors = Tailors.ENABLED;
		Leftovers.after(helper, () -> Tailors.ENABLED = tailors);
		List<Block> blocks = List.of(Blocks.BARREL, ModBlocks.STOREHOUSE, Blocks.LOOM);
		List<VillagerProfession> jobs = List.of(VillagerProfession.FISHERMAN, ModVillagers.PORTER, VillagerProfession.SHEPHERD);
		Tailors.ENABLED = false;
		for (int b = 0; b < blocks.size(); b++) {
			helper.setBlock(STATION, blocks.get(b));
			for (int i = 0; i < 12; i++) {
				Villager v = takesByThemselves(helper, STATION, spot(i), 2000L + i);
				helper.assertTrue(v.getVillagerData().getProfession() == jobs.get(b),
					"by the " + BuiltInRegistries.BLOCK.getKey(blocks.get(b)) + ": a " + name(v.getVillagerData().getProfession()));
				v.discard();
			}
			helper.setBlock(STATION, Blocks.AIR);
		}
		Tailors.ENABLED = true;
		helper.setBlock(STATION, Blocks.LOOM);
		Set<VillagerProfession> took = new LinkedHashSet<>();
		for (int i = 0; i < 12; i++) {
			took.add(takesByThemselves(helper, STATION, spot(i), 3000L + i).getVillagerData().getProfession());
		}
		helper.assertTrue(took.equals(Set.of(VillagerProfession.SHEPHERD, ModVillagers.TAILOR)), "at the loom with tailors on: " + took);
		helper.succeed();
	}

	/**
	 * Only a villager taking the block by themselves draws: a job given by order (the hall, a test, an admin tool), picked
	 * with an item, or set as a villager loads stays the one named. {@code randomStationJobs} off: always the vanilla job.
	 */
	//$ gametest_batch AREA '"randomJobsOrders"'
	@GameTest(template = AREA, batch = "randomJobsOrders")
	public void ordersItemsAndTheSwitchOffKeepTheJobNamed(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(STATION, Blocks.COMPOSTER);
		BlockPos at = helper.absolutePos(STATION);
		for (int i = 0; i < 12; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, spot(i));
			v.getRandom().setSeed(4000L + i);
			Jobs.employ(level, v, at, PoiTypes.FARMER, VillagerProfession.FARMER);
			helper.assertTrue(v.getVillagerData().getProfession() == VillagerProfession.FARMER, "an order gave a " + name(v.getVillagerData().getProfession()));
			v.discard();
			level.getPoiManager().release(at);
		}
		Villager picked = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		Stations.choose(helper.makeMockServerPlayerInLevel(), picked, new ItemStack(Items.WHEAT));
		helper.assertTrue(picked.getVillagerData().getProfession() == VillagerProfession.FARMER, "wheat gave a " + name(picked.getVillagerData().getProfession()));
		picked.discard();
		level.getPoiManager().release(at);
		// Not in their brain's run (as when a villager is loaded, or set by a story): left as given.
		Villager plain = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		plain.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), at));
		plain.setVillagerData(plain.getVillagerData().setProfession(VillagerProfession.FARMER));
		helper.assertTrue(plain.getVillagerData().getProfession() == VillagerProfession.FARMER, "outside the brain: a " + name(plain.getVillagerData().getProfession()));
		plain.discard();
		boolean random = RandomJobs.ENABLED;
		Leftovers.after(helper, () -> RandomJobs.ENABLED = random);
		io.github.jcondedata.aliveworkplace.WorkplaceConfig.parse("{\"randomStationJobs\": false}").apply();
		helper.assertTrue(!RandomJobs.ENABLED && new io.github.jcondedata.aliveworkplace.WorkplaceConfig().randomStationJobs, "the switch: on by default, off from the file");
		try {
			for (int i = 0; i < 12; i++) {
				Villager v = takesByThemselves(helper, STATION, spot(i), 1000L + i);
				helper.assertTrue(v.getVillagerData().getProfession() == VillagerProfession.FARMER, "switched off: a " + name(v.getVillagerData().getProfession()));
				v.discard();
			}
		} finally {
			new io.github.jcondedata.aliveworkplace.WorkplaceConfig().apply();
		}
		helper.assertTrue(RandomJobs.ENABLED, "the default config switches it on again");
		for (String key : List.of("aliveworkplace.config.randomStationJobs", "aliveworkplace.config.randomStationJobs.tooltip")) {
			String text = Component.translatable(key).getString();
			helper.assertTrue(!text.equals(key) && !text.contains("aliveworkplace."), "no words for " + key);
		}
		helper.succeed();
	}

	/** The job drawn stays: saved and loaded again, the villager is still the Florist (or whichever) they became, and keeps it. */
	//$ gametest_ticks_batch AREA '300' '"randomJobsReload"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "randomJobsReload")
	public void theJobDrawnSurvivesASaveAndReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		ServerLevel level = helper.getLevel();
		helper.setBlock(STATION, Blocks.COMPOSTER);
		BlockPos at = helper.absolutePos(STATION);
		Villager drawn = null;
		for (long seed = 5000L; drawn == null && seed < 5100L; seed++) {
			Villager v = takesByThemselves(helper, STATION, new BlockPos(4, 2, 4), seed);
			if (v.getVillagerData().getProfession() != VillagerProfession.FARMER) {
				drawn = v;
			} else {
				v.discard();
			}
		}
		helper.assertTrue(drawn != null, "100 villagers at the composter all became farmers");
		VillagerProfession job = drawn.getVillagerData().getProfession();
		level.getPoiManager().take(h -> h.is(PoiTypes.FARMER), (h, p) -> p.equals(at), at, 1);
		drawn.setVillagerXp(0); // as vanilla leaves a fresh worker
		CompoundTag saved = new CompoundTag();
		helper.assertTrue(drawn.save(saved), "setup: the villager couldn't be saved");
		drawn.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
		Entity back = EntityType.loadEntityRecursive(saved, level, e -> e);
		helper.assertTrue(back instanceof Villager && level.addFreshEntity(back), "setup: the villager couldn't be loaded again");
		Villager loaded = (Villager) back;
		loaded.setNoAi(false);
		helper.assertTrue(loaded.getVillagerData().getProfession() == job, "reloaded: a " + name(loaded.getVillagerData().getProfession()) + ", not a " + name(job));
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(loaded.getVillagerData().getProfession() == job, "200 ticks on: a " + name(loaded.getVillagerData().getProfession()) + ", not a " + name(job));
			helper.assertTrue(loaded.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos().equals(at)).orElse(false),
				"works at " + loaded.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			helper.succeed();
		});
	}
}
