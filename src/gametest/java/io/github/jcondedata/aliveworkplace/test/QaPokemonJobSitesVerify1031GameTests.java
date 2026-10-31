package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA verification of B697 (lane-a's fix), from the bug's spec: "without Cobblemon a jobless villager doesn't take a
 * Pokémon job by itself (the block stays free; a villager saved with the job keeps it)". QaNoCobblemonBlocksGameTests
 * covers each of the six blocks alone and B697GameTests one saved Trainer; these check that the guard isn't too broad
 * (a mod workstation beside a Training Post is still taken; a smithing table or composter, which a Pokémon job shares,
 * still gives a job, never the Pokémon one) and a saved Trainer beside a jobless villager and a second, free post. This run has no Cobblemon.
 */
public class QaPokemonJobSitesVerify1031GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos POST = new BlockPos(11, 2, 11);
	private static final BlockPos STANDING = new BlockPos(12, 2, 12);
	private static final int WAIT = 600;

	private static String job(Villager v) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()).toString();
	}

	private static void noCobblemon(GameTestHelper helper) {
		helper.assertTrue(!Stations.available(ModVillagers.TRAINER), "setup: this run has Cobblemon");
	}

	/** Fails the test the first tick {@code v} holds a job that can't be had without Cobblemon. */
	private static void neverAPokemonJob(GameTestHelper helper, Villager v, String where) {
		helper.onEachTick(() -> {
			VillagerProfession p = v.getVillagerData().getProfession();
			if (p != VillagerProfession.NONE && !Stations.available(p)) {
				helper.fail("without Cobblemon a jobless villager " + where + " took " + job(v) + " by itself at tick " + helper.getTick());
			}
		});
	}

	/** Control: a Miner's Bench beside a Training Post is still taken (a Miner at the bench), and the post stays free. */
	//$ gametest_ticks_batch AREA '700' '"qaVerifyBenchByPost"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaVerifyBenchByPost")
	public void aMinersBenchBesideATrainingPostIsStillTaken(GameTestHelper helper) {
		Leftovers.clear(helper);
		noCobblemon(helper);
		helper.setDayTime(2000);
		BlockPos bench = new BlockPos(13, 2, 13);
		helper.setBlock(POST, ModBlocks.TRAINING_POST);
		helper.setBlock(bench, ModBlocks.MINERS_BENCH);
		Villager v = helper.spawn(EntityType.VILLAGER, STANDING);
		neverAPokemonJob(helper, v, "between a Training Post and a Miner's Bench");
		helper.runAtTickTime(WAIT, () -> {
			helper.assertTrue(v.getVillagerData().getProfession() == ModVillagers.MINER, "by a Miner's Bench beside a Training Post the villager is " + job(v));
			helper.assertTrue(StationsSpecGameTests.site(v).map(p -> p.equals(helper.absolutePos(bench))).orElse(false),
				"the Miner's site isn't the bench: " + StationsSpecGameTests.site(v));
			helper.assertTrue(Stations.freeAt(helper.getLevel(), helper.absolutePos(POST)), "the Training Post isn't free");
			helper.succeed();
		});
	}

	/**
	 * Smithing tables (shared by vanilla's Toolsmith, our Tinkerer and the Ball Smith, a job that needs Cobblemon): four
	 * jobless villagers by four tables each take a job there, never the Ball Smith.
	 */
	//$ gametest_ticks_batch AREA '700' '"qaVerifySmithingTable"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaVerifySmithingTable")
	public void smithingTablesGiveJobsNeverTheBallSmith(GameTestHelper helper) {
		Leftovers.clear(helper);
		noCobblemon(helper);
		helper.setDayTime(2000);
		java.util.List<Villager> vs = new java.util.ArrayList<>();
		for (int i = 0; i < 4; i++) {
			helper.setBlock(new BlockPos(4 + 4 * i, 2, 4), Blocks.SMITHING_TABLE);
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(4 + 4 * i, 2, 6));
			neverAPokemonJob(helper, v, "by a smithing table");
			vs.add(v);
		}
		helper.runAtTickTime(WAIT, () -> {
			for (Villager v : vs) {
				helper.assertTrue(v.getVillagerData().getProfession() != VillagerProfession.NONE, "a villager by a smithing table is still jobless");
			}
			helper.succeed();
		});
	}

	/** A composter (shared with the Berry Breeder, a job that needs Cobblemon) still gives a job, never the Berry Breeder. */
	//$ gametest_ticks_batch AREA '700' '"qaVerifyComposter"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaVerifyComposter")
	public void aComposterGivesAJobNeverTheBerryBreeder(GameTestHelper helper) {
		Leftovers.clear(helper);
		noCobblemon(helper);
		helper.setDayTime(2000);
		helper.setBlock(POST, Blocks.COMPOSTER);
		Villager v = helper.spawn(EntityType.VILLAGER, STANDING);
		neverAPokemonJob(helper, v, "by a composter");
		helper.runAtTickTime(WAIT, () -> {
			helper.assertTrue(v.getVillagerData().getProfession() != VillagerProfession.NONE, "by a composter the villager is still jobless");
			helper.succeed();
		});
	}

	/**
	 * A Trainer saved before (level 2), loaded by his post next to a jobless villager and a second Training Post: for
	 * 500 ticks the Trainer stays a Trainer at his own post, the jobless one never takes a Pokémon job, and the second
	 * post stays free.
	 */
	//$ gametest_ticks_batch AREA '700' '"qaVerifySavedTrainerBeside"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaVerifySavedTrainerBeside")
	public void aSavedTrainerKeepsHisPostBesideAJoblessVillager(GameTestHelper helper) {
		Leftovers.clear(helper);
		noCobblemon(helper);
		helper.setDayTime(2000);
		BlockPos second = new BlockPos(8, 2, 8);
		BlockPos joblessAt = new BlockPos(9, 2, 9);
		helper.setBlock(POST, ModBlocks.TRAINING_POST);
		helper.setBlock(second, ModBlocks.TRAINING_POST);
		Villager before = helper.spawn(EntityType.VILLAGER, STANDING);
		before.setVillagerData(before.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(2));
		before.setVillagerXp(20);
		CompoundTag saved = new CompoundTag();
		before.saveWithoutId(saved);
		before.discard();
		Villager trainer = helper.spawn(EntityType.VILLAGER, STANDING);
		trainer.load(saved);
		trainer.setPos(helper.absoluteVec(Vec3.atBottomCenterOf(STANDING)));
		Villager jobless = helper.spawn(EntityType.VILLAGER, joblessAt);
		neverAPokemonJob(helper, jobless, "beside a saved Trainer and two Training Posts");
		helper.onEachTick(() -> {
			if (trainer.getVillagerData().getProfession() != ModVillagers.TRAINER) {
				helper.fail("the saved Trainer became " + job(trainer) + " at tick " + helper.getTick());
			}
		});
		helper.runAtTickTime(500, () -> {
			helper.assertTrue(trainer.getVillagerData().getLevel() == 2, "the saved Trainer's level is " + trainer.getVillagerData().getLevel());
			helper.assertTrue(StationsSpecGameTests.site(trainer).isPresent(), "the saved Trainer has no post");
			helper.assertTrue(StationsSpecGameTests.site(jobless).isEmpty(), "the jobless villager claimed a post: " + StationsSpecGameTests.site(jobless));
			helper.assertTrue(jobless.getVillagerData().getProfession() == VillagerProfession.NONE, "the jobless villager is " + job(jobless));
			BlockPos his = StationsSpecGameTests.site(trainer).get();
			BlockPos other = his.equals(helper.absolutePos(POST)) ? helper.absolutePos(second) : helper.absolutePos(POST);
			helper.assertTrue(Stations.freeAt(helper.getLevel(), other), "the post the Trainer doesn't work at isn't free: " + other);
			helper.succeed();
		});
	}
}
