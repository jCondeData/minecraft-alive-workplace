package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.ClassPerks;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.vintner.Vintners;
import io.github.jcondedata.aliveworkplace.work.PhysicianWork;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.WorkerLimits;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA (lane-c-1123-0653) of docs/wiki/random-jobs.md, from the page, not the code: what the existing tests leave out.
 * A village with three farmers and no florist most likely gets a florist; a villager's class leaves jobs out of the
 * draw but never the vanilla one; a worker who already has a job, a job that isn't the block's vanilla one, an order
 * given inside the brain's run, a block broken under them and a job site elsewhere are never redrawn; switched-off jobs
 * never come up; the Shop Counter by itself gives the Shopkeeper or the Innkeeper, drawn from the villager's own random;
 * every job name a player reads is written, in American English. Villagers are NoAI and put through vanilla's own entry
 * point (their job set inside their brain's run, the block as their job site), each with a fixed seed.
 */
public class QaRandomJobs1123cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos STATION = new BlockPos(4, 2, 4);
	private static final BlockPos HALL = new BlockPos(14, 2, 14);

	private static String name(VillagerProfession job) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(job).toString();
	}

	private static BlockPos spot(int i) {
		return new BlockPos(6 + i % 6, 2, 6 + (i / 6) % 6);
	}

	/** A NoAI villager with a fixed random, the block at {@code station} (relative) as their job site. */
	private static Villager jobless(GameTestHelper helper, BlockPos station, BlockPos standing, long seed) {
		ServerLevel level = helper.getLevel();
		Villager v = helper.spawn(EntityType.VILLAGER, standing);
		v.setNoAi(true);
		v.getRandom().setSeed(RandomSource.create(seed).nextLong());
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(station)));
		return v;
	}

	/** What vanilla's AssignProfessionFromJobSite does: inside the villager's own brain run, they're given {@code job}. */
	private static void given(Villager v, VillagerProfession job) {
		Villager before = WorkerLimits.thinker();
		WorkerLimits.thinking(v);
		try {
			v.setVillagerData(v.getVillagerData().setProfession(job));
		} finally {
			WorkerLimits.thinking(before);
		}
	}

	/** {@code n} villagers taking the block at STATION by themselves (its vanilla job), each taken away after; the jobs drawn. */
	private static Map<VillagerProfession, Integer> draws(GameTestHelper helper, Block block, int n, long seed, String cls) {
		VillagerProfession vanilla = Stations.at(block).orElseThrow().jobs().get(0).profession().get();
		Map<VillagerProfession, Integer> took = new HashMap<>();
		for (int i = 0; i < n; i++) {
			Villager v = jobless(helper, STATION, spot(i), seed + i);
			if (cls != null) {
				SocialClasses.seed(v, SocialClasses.get(AliveWorkplace.id(cls)));
			}
			given(v, vanilla);
			took.merge(v.getVillagerData().getProfession(), 1, Integer::sum);
			v.discard();
		}
		return took;
	}

	private static String names(Map<VillagerProfession, Integer> took) {
		StringBuilder out = new StringBuilder();
		took.forEach((k, n) -> out.append(name(k)).append('=').append(n).append(' '));
		return out.toString().trim();
	}

	/**
	 * "A village that already has three farmers and no florist will most likely get a florist next": three farmers stand
	 * by the composter; of 120 jobless villagers taking it (each taken away again, so the village stays the same) far
	 * more become florists than farmers (weights 1/4 against 1: about 9 farmers and 37 florists).
	 */
	//$ gametest_batch AREA '"qaRandomJobs1123cFlorist"'
	@GameTest(template = AREA, batch = "qaRandomJobs1123cFlorist")
	public void aVillageWithThreeFarmersMostLikelyGetsAFloristNext(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setBlock(STATION, Blocks.COMPOSTER);
		for (int i = 0; i < 3; i++) {
			Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(1 + i, 2, 13));
			farmer.setNoAi(true);
			farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER));
		}
		Map<VillagerProfession, Integer> took = draws(helper, Blocks.COMPOSTER, 120, 77_000L, null);
		int farmers = took.getOrDefault(VillagerProfession.FARMER, 0);
		int florists = took.getOrDefault(ModVillagers.FLORIST, 0);
		helper.assertTrue(florists >= 25 && farmers <= 20 && florists > 2 * farmers, "120 hires by three farmers: " + names(took));
		helper.assertTrue(farmers >= 1, "a farmer can still come up: " + names(took));
		helper.succeed();
	}

	/**
	 * "A job the villager's class can't hold is left out of the draw ... the vanilla job is always in it", in a village
	 * with a hall and classes on: at a lectern a Peasant is only ever a Librarian, an Artisan a Librarian or a Teacher
	 * (never a Scholar, a Burgher's job), and a Burgher can be any of the three.
	 */
	//$ gametest_batch AREA '"qaRandomJobs1123cClass"'
	@GameTest(template = AREA, batch = "qaRandomJobs1123cClass")
	public void aVillagersClassLeavesJobsOutButNeverTheVanillaOne(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		SocialClasses.ENABLED = true;
		SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
		ClassPerks.forget();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
			ClassPerks.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(STATION, Blocks.LECTERN);
		Map<VillagerProfession, Integer> peasants = draws(helper, Blocks.LECTERN, 30, 81_000L, "peasant");
		helper.assertTrue(peasants.keySet().equals(Set.of(VillagerProfession.LIBRARIAN)), "Peasants at a lectern became: " + names(peasants));
		Map<VillagerProfession, Integer> artisans = draws(helper, Blocks.LECTERN, 30, 82_000L, "artisan");
		helper.assertTrue(!artisans.containsKey(ModVillagers.SCHOLAR) && artisans.containsKey(ModVillagers.TEACHER)
			&& artisans.containsKey(VillagerProfession.LIBRARIAN) && artisans.size() == 2, "Artisans at a lectern became: " + names(artisans));
		Map<VillagerProfession, Integer> burghers = draws(helper, Blocks.LECTERN, 30, 83_000L, "burgher");
		helper.assertTrue(burghers.containsKey(ModVillagers.SCHOLAR) && burghers.size() == 3, "Burghers at a lectern became: " + names(burghers));
		helper.succeed();
	}

	/**
	 * "Once a villager has a job, they keep it": a Farmer at the composter whose data is set again inside their brain's
	 * run (a level-up, vanilla's re-check of their job) stays a Farmer, whatever their random.
	 */
	//$ gametest_batch AREA '"qaRandomJobs1123cKeep"'
	@GameTest(template = AREA, batch = "qaRandomJobs1123cKeep")
	public void aVillagerWithAJobIsNeverRedrawn(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setBlock(STATION, Blocks.COMPOSTER);
		for (int i = 0; i < 16; i++) {
			Villager v = jobless(helper, STATION, spot(i), 90_000L + i);
			WorkerLimits.order(v, VillagerProfession.FARMER);
			helper.assertTrue(v.getVillagerData().getProfession() == VillagerProfession.FARMER, "setup: a " + name(v.getVillagerData().getProfession()));
			Villager before = WorkerLimits.thinker();
			WorkerLimits.thinking(v);
			try {
				v.setVillagerData(v.getVillagerData().setLevel(2));
				v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER));
			} finally {
				WorkerLimits.thinking(before);
			}
			helper.assertTrue(v.getVillagerData().getProfession() == VillagerProfession.FARMER && v.getVillagerData().getLevel() == 2,
				"a Farmer levelled up in their brain became a level " + v.getVillagerData().getLevel() + " " + name(v.getVillagerData().getProfession()));
			v.discard();
		}
		helper.succeed();
	}

	/**
	 * Only the block's first (vanilla) job is swapped: a villager given another of the composter's jobs (a Florist) by
	 * their brain keeps it, and so does one given a job that isn't the composter's at all.
	 */
	//$ gametest_batch AREA '"qaRandomJobs1123cOther"'
	@GameTest(template = AREA, batch = "qaRandomJobs1123cOther")
	public void aJobOtherThanTheBlocksVanillaOneIsKept(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setBlock(STATION, Blocks.COMPOSTER);
		for (VillagerProfession job : List.of(ModVillagers.FLORIST, ModVillagers.COMPOSTER, VillagerProfession.LIBRARIAN)) {
			for (int i = 0; i < 12; i++) {
				Villager v = jobless(helper, STATION, spot(i), 91_000L + i);
				given(v, job);
				helper.assertTrue(v.getVillagerData().getProfession() == job, "given a " + name(job) + " at the composter, became a " + name(v.getVillagerData().getProfession()));
				v.discard();
			}
		}
		helper.succeed();
	}

	/**
	 * The draw needs the shared block at the villager's job site: with the composter broken under them, with no job site,
	 * or with their job site in another dimension, a villager given the Farmer keeps it, and nothing breaks.
	 */
	//$ gametest_batch AREA '"qaRandomJobs1123cBroken"'
	@GameTest(template = AREA, batch = "qaRandomJobs1123cBroken")
	public void aBrokenOrMissingJobBlockGivesTheVanillaJob(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(STATION, Blocks.COMPOSTER);
		helper.setBlock(STATION, Blocks.AIR); // broken: no point of interest left
		for (int i = 0; i < 12; i++) {
			Villager broken = jobless(helper, STATION, spot(i), 92_000L + i);
			given(broken, VillagerProfession.FARMER);
			helper.assertTrue(broken.getVillagerData().getProfession() == VillagerProfession.FARMER, "the composter broken: a " + name(broken.getVillagerData().getProfession()));
			broken.discard();
		}
		helper.setBlock(STATION, Blocks.COMPOSTER);
		for (int i = 0; i < 12; i++) {
			Villager none = jobless(helper, STATION, spot(i), 93_000L + i);
			none.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
			given(none, VillagerProfession.FARMER);
			helper.assertTrue(none.getVillagerData().getProfession() == VillagerProfession.FARMER, "no job site: a " + name(none.getVillagerData().getProfession()));
			none.discard();
			Villager away = jobless(helper, STATION, spot(i), 94_000L + i);
			away.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER, helper.absolutePos(STATION)));
			given(away, VillagerProfession.FARMER);
			helper.assertTrue(away.getVillagerData().getProfession() == VillagerProfession.FARMER, "job site in another dimension: a " + name(away.getVillagerData().getProfession()));
			away.discard();
		}
		helper.succeed();
	}

	/**
	 * "Any order ... is never redrawn": an order given while the villager's own brain is running (the Steward or a
	 * hired traveller during their tick) keeps the job it names.
	 */
	//$ gametest_batch AREA '"qaRandomJobs1123cOrder"'
	@GameTest(template = AREA, batch = "qaRandomJobs1123cOrder")
	public void anOrderInsideTheBrainsRunKeepsTheJobNamed(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setBlock(STATION, Blocks.COMPOSTER);
		for (int i = 0; i < 16; i++) {
			Villager v = jobless(helper, STATION, spot(i), 95_000L + i);
			Villager before = WorkerLimits.thinker();
			WorkerLimits.thinking(v);
			try {
				WorkerLimits.order(v, VillagerProfession.FARMER);
			} finally {
				WorkerLimits.thinking(before);
			}
			helper.assertTrue(v.getVillagerData().getProfession() == VillagerProfession.FARMER, "ordered a Farmer in the brain's run, became a " + name(v.getVillagerData().getProfession()));
			v.discard();
		}
		helper.succeed();
	}

	/**
	 * A job behind a switch that's off never comes up ("that this game has"): the cauldron with {@code vintners} off
	 * never gives a Vintner but does give the Leatherworker and the Sifter; switched on, the Vintner comes up. The brewing
	 * stand with {@code physicians} off never gives a Physician.
	 */
	//$ gametest_batch AREA '"qaRandomJobs1123cSwitches"'
	@GameTest(template = AREA, batch = "qaRandomJobs1123cSwitches")
	public void aJobSwitchedOffIsNeverDrawn(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		boolean vintners = Vintners.ENABLED;
		boolean physicians = PhysicianWork.ENABLED;
		Leftovers.after(helper, () -> {
			Vintners.ENABLED = vintners;
			PhysicianWork.ENABLED = physicians;
		});
		helper.setBlock(STATION, Blocks.CAULDRON);
		Vintners.ENABLED = false;
		Map<VillagerProfession, Integer> off = draws(helper, Blocks.CAULDRON, 40, 96_000L, null);
		helper.assertTrue(!off.containsKey(ModVillagers.VINTNER) && off.containsKey(VillagerProfession.LEATHERWORKER) && off.containsKey(ModVillagers.SIFTER),
			"the cauldron with vintners off: " + names(off));
		Vintners.ENABLED = true;
		Map<VillagerProfession, Integer> on = draws(helper, Blocks.CAULDRON, 40, 96_000L, null);
		helper.assertTrue(on.containsKey(ModVillagers.VINTNER), "the cauldron with vintners on: " + names(on));
		helper.setBlock(STATION, Blocks.AIR);
		helper.setBlock(STATION, Blocks.BREWING_STAND);
		PhysicianWork.ENABLED = false;
		Map<VillagerProfession, Integer> brewing = draws(helper, Blocks.BREWING_STAND, 40, 97_000L, null);
		helper.assertTrue(!brewing.containsKey(ModVillagers.PHYSICIAN) && brewing.containsKey(VillagerProfession.CLERIC) && brewing.containsKey(ModVillagers.NURSE),
			"the brewing stand with physicians off: " + names(brewing));
		helper.succeed();
	}

	/**
	 * "The Shop Counter's Shopkeeper by itself becomes the Shopkeeper or the Innkeeper", drawn from "the villager's own
	 * random": both come up over 24 villagers, and a villager with the same random draws the same job again.
	 */
	//$ gametest_batch AREA '"qaRandomJobs1123cCounter"'
	@GameTest(template = AREA, batch = "qaRandomJobs1123cCounter")
	public void theShopCounterGivesAShopkeeperOrAnInnkeeperFromTheVillagersOwnRandom(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setBlock(STATION, ModBlocks.SHOP_COUNTER);
		Map<VillagerProfession, Integer> took = draws(helper, ModBlocks.SHOP_COUNTER, 24, 98_000L, null);
		helper.assertTrue(took.keySet().equals(Set.of(ModVillagers.SHOPKEEPER, ModVillagers.INNKEEPER)), "at the Shop Counter: " + names(took));
		for (int i = 0; i < 12; i++) {
			Map<VillagerProfession, Integer> first = draws(helper, ModBlocks.SHOP_COUNTER, 1, 99_000L + i * 7, null);
			Map<VillagerProfession, Integer> again = draws(helper, ModBlocks.SHOP_COUNTER, 1, 99_000L + i * 7, null);
			helper.assertTrue(first.equals(again), "the same random drew " + names(first) + ", then " + names(again));
		}
		helper.succeed();
	}

	/**
	 * Every sentence a player reads: each job a shared block can give has its name written (no raw key, no %s or %d) in
	 * American English, and the setting's name reads "Random Station Jobs" with a tooltip of whole words.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyJobNameAndTheSettingsWordsAreWritten(GameTestHelper helper) {
		int checked = 0;
		for (Stations.Station station : Stations.ALL) {
			if (station.jobs().size() < 2) {
				continue;
			}
			for (Stations.Job job : station.jobs()) {
				VillagerProfession p = job.profession().get();
				String text = Stations.name(p).getString();
				helper.assertTrue(!text.startsWith("entity.") && !text.contains("%") && !text.isBlank(), "the " + name(p) + "'s name reads '" + text + "'");
				for (String british : List.of("Jeweller", "Armour", "Colour", "Defence", "Grey")) {
					helper.assertTrue(!text.contains(british), "the " + name(p) + "'s name is British: '" + text + "'");
				}
				checked++;
			}
		}
		helper.assertTrue(checked >= 30, "only " + checked + " job names checked");
		helper.assertTrue(Component.translatable("aliveworkplace.config.randomStationJobs").getString().equals("Random Station Jobs"),
			"the setting reads '" + Component.translatable("aliveworkplace.config.randomStationJobs").getString() + "'");
		String tip = Component.translatable("aliveworkplace.config.randomStationJobs.tooltip").getString();
		helper.assertTrue(!tip.contains("%") && !tip.startsWith("aliveworkplace.") && tip.contains("random"), "the setting's tooltip reads '" + tip + "'");
		helper.succeed();
	}
}
