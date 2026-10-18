package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.guard.DenWardens;
import io.github.jcondedata.aliveworkplace.guard.RaidDens;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.trainer.DenChallenges;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * B190: raid dens are found the way a real world brings them, with nothing here calling the detection by hand. A den set
 * down in a chunk that was loaded long before, by a hall that already stands, is listed, gets a warden and is challenged
 * by the Leader; and a hall set down by a den that was there first finds it (before, only a chunk loading found dens,
 * so neither was ever known while the chunk stayed loaded).
 */
public class RaidDenFoundCompatTests implements FabricGameTest {
	static final BlockPos HALL = DenChallengeCompatTests.HALL;
	static final BlockPos DEN = DenChallengeCompatTests.DEN;

	/** A block state's property by name, as its saved word, or "" when it has none. */
	static String property(net.minecraft.world.level.block.state.BlockState state, String name) {
		net.minecraft.world.level.block.state.properties.Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
		return property == null ? "" : named(state, property);
	}

	private static <T extends Comparable<T>> String named(net.minecraft.world.level.block.state.BlockState state,
			net.minecraft.world.level.block.state.properties.Property<T> property) {
		return property.getName(state.getValue(property));
	}

	static void cleanUp(GameTestHelper helper, BlockPos hall, BlockPos den) {
		ServerLevel level = helper.getLevel();
		DenWardens.wardenOf(level, den).ifPresent(w -> DenWardens.recall(level, den));
		level.setBlockAndUpdate(den, Blocks.AIR.defaultBlockState());
		RaidDens.near(level, hall);
	}

	/** The hall stands and its chunk is long loaded; a den set down afterwards is listed, watched and challenged. */
	//$ gametest_ticks_batch 'CompatGameTests.AREA' '200' '"raidDenSetDownLater"'
	@GameTest(template = CompatGameTests.AREA, timeoutTicks = 200, batch = "raidDenSetDownLater")
	public void aDenSetDownByAStandingHallIsFound(GameTestHelper helper) {
		DenWardenCompatTests.clearAround(helper);
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		DenWardenCompatTests.time(level, 1000);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(DenChallengeCompatTests.SMOKER, Blocks.SMOKER);
		helper.setBlock(DenChallengeCompatTests.CHEST, Blocks.CHEST);
		helper.setBlock(DenWardenCompatTests.POST, ModBlocks.GUARD_POST);
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos den = helper.absolutePos(DEN);
		helper.startSequence()
			.thenIdle(20) // the hall and its chunk settle: the den comes well after
			.thenExecute(() -> helper.assertTrue(RaidDenCompatTests.listed(helper, hall, den).isEmpty(), "a den is listed before it's there"))
			.thenExecute(() -> helper.setBlock(DEN, RaidDenCompatTests.den("tier_three", "fire", true)))
			.thenWaitUntil(() -> helper.assertTrue(RaidDenCompatTests.listed(helper, hall, den).isPresent(),
				"the den set down by the hall isn't listed: " + RaidDens.near(level, hall)))
			.thenExecute(() -> {
				// Raid Dens rolled the glowing crystal's raid (a tier and type of its own) on its first tick: the list
				// shows the den as it stands
				RaidDens.Den found = RaidDenCompatTests.listed(helper, hall, den).get();
				var state = level.getBlockState(den);
				helper.assertTrue(found.active() && found.tier() >= 1 && RaidDens.TIERS.get(found.tier() - 1).equals(property(state, "raid_tier"))
					&& found.type().equals(property(state, "raid_type").toLowerCase(java.util.Locale.ROOT)), "the den: " + found + ", the crystal: " + state);
				// a raid a Leader takes on (up to tier 3): the crystal keeps the raid it rolled
				level.setBlockAndUpdate(den, RaidDenCompatTests.den("tier_three", "fire", true));
				helper.assertTrue(RaidDenCompatTests.listed(helper, hall, den).map(d -> d.tier() == 3 && d.type().equals("fire")).orElse(false),
					"the den read again: " + RaidDens.near(level, hall));
				Villager guard = DenWardenCompatTests.guard(helper);
				String said = DenWardens.post(level, hall, den).getString();
				helper.assertTrue(DenWardens.wardenOf(level, den).equals(Optional.of(guard.getUUID())), "no warden for the den: " + said);
				DenWardens.recall(level, den);
				Villager leader = DenChallengeCompatTests.master(helper);
				helper.assertTrue(DenChallenges.target(level, hall, leader).map(RaidDens.Den::pos).equals(Optional.of(den)),
					"the Leader found no den: " + DenChallenges.target(level, hall, leader));
				DenChallenges.Result result = DenChallenges.challenge(level, hall, 0f, RandomSource.create(38));
				helper.assertTrue(result == DenChallenges.Result.WON, "the challenge: " + result);
				cleanUp(helper, hall, den);
				DenWardenCompatTests.time(level, time);
			})
			.thenSucceed();
	}

	/** A den is there first, with no hall near (so it isn't the village's yet); the hall set down by it then finds it. */
	//$ gametest_ticks_batch 'CompatGameTests.AREA' '200' '"raidDenHallLater"'
	@GameTest(template = CompatGameTests.AREA, timeoutTicks = 200, batch = "raidDenHallLater")
	public void aHallSetDownByADenFindsIt(GameTestHelper helper) {
		DenWardenCompatTests.clearAround(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos den = helper.absolutePos(DEN);
		helper.setBlock(DEN, RaidDenCompatTests.den("tier_two", "water", false));
		helper.startSequence()
			.thenIdle(20)
			.thenExecute(() -> helper.assertTrue(RaidDens.Sites.get(level).dens().stream().noneMatch(d -> d.pos().equals(den)),
				"a den with no hall near was recorded"))
			.thenExecute(() -> helper.setBlock(HALL, ModBlocks.VILLAGE_HALL))
			.thenWaitUntil(() -> helper.assertTrue(RaidDenCompatTests.listed(helper, hall, den).isPresent(),
				"the new hall didn't find the den by it: " + RaidDens.near(level, hall)))
			.thenExecute(() -> {
				RaidDens.Den found = RaidDenCompatTests.listed(helper, hall, den).get();
				helper.assertTrue(found.tier() == 2 && found.type().equals("water") && !found.active(), "the den: " + found);
				cleanUp(helper, hall, den);
			})
			.thenSucceed();
	}
}
