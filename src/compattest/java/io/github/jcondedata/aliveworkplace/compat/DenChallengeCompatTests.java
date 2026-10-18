package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.guard.RaidDens;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.DenChallenges;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * ROADMAP 38.4: the village's best trainer challenges a raid den, settled at once by rank against the den's tier (a
 * fixed roll here). A win puts that tier's loot in the store and turns the den inactive; a loss leaves it active and the
 * trainer tired; a tier 6 den is never challenged, nor one a player is at. Each test runs alone and takes its den away.
 */
public class DenChallengeCompatTests implements FabricGameTest {
	static final BlockPos HALL = new BlockPos(2, 2, 2);
	static final BlockPos SMOKER = new BlockPos(6, 2, 2);
	static final BlockPos CHEST = new BlockPos(7, 2, 2);
	static final BlockPos POST = new BlockPos(2, 2, 8);
	static final BlockPos DEN = new BlockPos(14, 2, 14);

	/** A hall with a store (a smoker and its chest), a Master trainer and a den; returns {hall, den}. */
	static BlockPos[] village(GameTestHelper helper, String tier) {
		DenWardenCompatTests.clearAround(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(SMOKER, Blocks.SMOKER);
		helper.setBlock(CHEST, Blocks.CHEST);
		helper.setBlock(DEN, RaidDenCompatTests.den(tier, "fire", true));
		BlockPos den = helper.absolutePos(DEN);
		RaidDens.chunkLoaded(level, level.getChunkAt(den));
		RaidDens.tick(level);
		helper.assertTrue(RaidDenCompatTests.listed(helper, helper.absolutePos(HALL), den).isPresent(), "the den isn't known");
		return new BlockPos[] {helper.absolutePos(HALL), den};
	}

	static Villager master(GameTestHelper helper) {
		helper.setBlock(POST, ModBlocks.TRAINING_POST);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 6));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(POST), ModVillagers.TRAINING_POST_POI, ModVillagers.TRAINER);
		villager.setVillagerData(villager.getVillagerData().setLevel(5));
		return villager;
	}

	static int stored(GameTestHelper helper) {
		Container chest = (Container) helper.getBlockEntity(CHEST);
		int n = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			n += chest.getItem(i).getCount();
		}
		return n;
	}

	static void cleanUp(GameTestHelper helper, BlockPos hall, BlockPos den) {
		helper.getLevel().setBlockAndUpdate(den, Blocks.AIR.defaultBlockState());
		RaidDens.near(helper.getLevel(), hall);
	}

	/** A Master beats a tier 3 den: its loot is in the store, the den rests, the chronicle tells it. */
	//$ gametest_batch 'CompatGameTests.AREA' '"denChallengeWin"'
	@GameTest(template = CompatGameTests.AREA, batch = "denChallengeWin")
	public void aWinBringsTheLootHomeAndTheDenRests(GameTestHelper helper) {
		BlockPos[] at = village(helper, "tier_three");
		Villager trainer = master(helper);
		helper.assertTrue(DenChallenges.champion(helper.getLevel(), at[0]).map(Villager::getUUID).equals(Optional.of(trainer.getUUID())), "the Master isn't the champion");
		DenChallenges.Result result = DenChallenges.challenge(helper.getLevel(), at[0], 0f, RandomSource.create(38));
		helper.assertTrue(result == DenChallenges.Result.WON, "the challenge: " + result);
		helper.assertTrue(stored(helper) > 0, "no raid loot in the store");
		Optional<RaidDens.Den> den = RaidDenCompatTests.listed(helper, at[0], at[1]);
		helper.assertTrue(den.isPresent() && !den.get().active(), "the den after the win: " + den);
		helper.assertFalse(DenChallenges.tired(helper.getLevel(), trainer.getUUID()), "a winner isn't tired");
		var hall = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) helper.getLevel().getBlockEntity(at[0]);
		String last = hall.chronicle().get(hall.chronicle().size() - 1).text().getString();
		helper.assertTrue(last.contains("beat the tier 3 Fire raid den"), "the chronicle: " + last);
		cleanUp(helper, at[0], at[1]);
		helper.succeed();
	}

	/** A lost challenge leaves the den glowing and the trainer tired, so nobody is sent the next time. */
	//$ gametest_batch 'CompatGameTests.AREA' '"denChallengeLoss"'
	@GameTest(template = CompatGameTests.AREA, batch = "denChallengeLoss")
	public void aLossLeavesTheDenActive(GameTestHelper helper) {
		BlockPos[] at = village(helper, "tier_four");
		Villager trainer = master(helper);
		DenChallenges.Result result = DenChallenges.challenge(helper.getLevel(), at[0], 0.99f, RandomSource.create(38));
		helper.assertTrue(result == DenChallenges.Result.LOST, "the challenge: " + result);
		helper.assertTrue(stored(helper) == 0, "loot after a loss");
		helper.assertTrue(RaidDenCompatTests.listed(helper, at[0], at[1]).map(RaidDens.Den::active).orElse(false), "the den went quiet after a loss");
		helper.assertTrue(DenChallenges.tired(helper.getLevel(), trainer.getUUID()), "the loser isn't tired");
		helper.assertTrue(DenChallenges.challenge(helper.getLevel(), at[0], 0f, RandomSource.create(38)) == DenChallenges.Result.NO_TRAINER,
			"a tired trainer was sent again");
		cleanUp(helper, at[0], at[1]);
		helper.succeed();
	}

	/** A tier 6 den is never challenged, not even by a Master who would surely win. */
	//$ gametest_batch 'CompatGameTests.AREA' '"denChallengeTier6"'
	@GameTest(template = CompatGameTests.AREA, batch = "denChallengeTier6")
	public void aTierSixDenIsNeverChallenged(GameTestHelper helper) {
		BlockPos[] at = village(helper, "tier_six");
		master(helper);
		DenChallenges.Result result = DenChallenges.challenge(helper.getLevel(), at[0], 0f, RandomSource.create(38));
		helper.assertTrue(result == DenChallenges.Result.NO_DEN, "a tier 6 den: " + result);
		helper.assertTrue(RaidDenCompatTests.listed(helper, at[0], at[1]).map(RaidDens.Den::active).orElse(false), "the tier 6 den went quiet");
		cleanUp(helper, at[0], at[1]);
		helper.succeed();
	}

	/** A den a player is at (within 8 blocks) is theirs: nobody is sent. */
	//$ gametest_batch 'CompatGameTests.AREA' '"denChallengePlayer"'
	@GameTest(template = CompatGameTests.AREA, batch = "denChallengePlayer")
	public void aDenInUseByAPlayerIsntChallenged(GameTestHelper helper) {
		BlockPos[] at = village(helper, "tier_two");
		master(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.teleportTo(at[1].getX() + 2.5, at[1].getY(), at[1].getZ() + 0.5);
		try {
			DenChallenges.Result result = DenChallenges.challenge(helper.getLevel(), at[0], 0f, RandomSource.create(38));
			helper.assertTrue(result == DenChallenges.Result.NO_DEN, "a den with a player at it: " + result);
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		cleanUp(helper, at[0], at[1]);
		helper.succeed();
	}

	/** A den broken before the trainer sets off is not challenged. */
	//$ gametest_batch 'CompatGameTests.AREA' '"denChallengeGone"'
	@GameTest(template = CompatGameTests.AREA, batch = "denChallengeGone")
	public void aBrokenDenIsntChallenged(GameTestHelper helper) {
		BlockPos[] at = village(helper, "tier_two");
		master(helper);
		RaidDenCompatTests.clear(helper, at[0], at[1]);
		DenChallenges.Result result = DenChallenges.challenge(helper.getLevel(), at[0], 0f, RandomSource.create(38));
		helper.assertTrue(result == DenChallenges.Result.NO_DEN, "a broken den: " + result);
		helper.assertTrue(stored(helper) == 0, "loot from a broken den");
		helper.succeed();
	}

	/** A tired trainer stays tired through a save: the challenge data is read back as it was. */
	//$ gametest_batch 'CompatGameTests.AREA' '"denChallengeSave"'
	@GameTest(template = CompatGameTests.AREA, batch = "denChallengeSave")
	public void tiredTrainersSurviveASave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos[] at = village(helper, "tier_four");
		Villager trainer = master(helper);
		helper.assertTrue(DenChallenges.challenge(level, at[0], 0.99f, RandomSource.create(38)) == DenChallenges.Result.LOST, "the challenge should be lost");
		net.minecraft.nbt.CompoundTag saved = DenChallenges.Data.get(level).save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
		helper.assertTrue(saved.toString().contains("tiredUntil"), "the save: " + saved);
		net.minecraft.nbt.CompoundTag again = DenChallenges.Data.load(saved.copy(), level.registryAccess()).save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
		helper.assertTrue(again.equals(saved), "read back: " + again + " saved: " + saved);
		DenChallenges.Data empty = DenChallenges.Data.load(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
		helper.assertTrue(empty.last(at[0]) == -1, "an old save without challenges: never challenged");
		helper.assertTrue(DenChallenges.tired(level, trainer.getUUID()), "the loser isn't tired");
		cleanUp(helper, at[0], at[1]);
		helper.succeed();
	}
}
