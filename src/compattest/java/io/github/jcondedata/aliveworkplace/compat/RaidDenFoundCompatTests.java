package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.guard.RaidDens;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.trainer.RaidHelpers;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * B190: raid dens are found the way a real world brings them, with nothing here calling the detection by hand. A den set
 * down in a chunk that was loaded long before, by a hall that already stands, is listed and takes raid helpers (38.1a); and a hall set down by a den that was there first finds it (before, only a chunk loading found dens,
 * so neither was ever known while the chunk stayed loaded).
 */
public class RaidDenFoundCompatTests implements FabricGameTest {
	static final BlockPos HALL = RaidHelperCompatTests.HALL;
	static final BlockPos DEN = RaidHelperCompatTests.DEN;

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
		RaidHelpers.Data.get(level).hires().clear();
		level.setBlockAndUpdate(den, Blocks.AIR.defaultBlockState());
		RaidDens.near(level, hall);
	}

	/** The hall stands and its chunk is long loaded; a den set down afterwards is listed and takes raid helpers. */
	//$ gametest_ticks_batch 'CompatGameTests.AREA' '200' '"raidDenSetDownLater"'
	@GameTest(template = CompatGameTests.AREA, timeoutTicks = 200, batch = "raidDenSetDownLater")
	public void aDenSetDownByAStandingHallIsFound(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		RaidHelperCompatTests.time(level, 1000);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
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
				// the crystal keeps the raid it rolled; set to a known one, the list reads it again
				level.setBlockAndUpdate(den, RaidDenCompatTests.den("tier_three", "fire", true));
				helper.assertTrue(RaidDenCompatTests.listed(helper, hall, den).map(d -> d.tier() == 3 && d.type().equals("fire")).orElse(false),
					"the den read again: " + RaidDens.near(level, hall));
				Villager master = RaidHelperCompatTests.master(helper);
				net.minecraft.server.level.ServerPlayer player = RaidHelperCompatTests.player(helper, helper.absolutePos(new BlockPos(3, 2, 3)), RaidHelperCompatTests.PRICE);
				String said = RaidHelpers.hire(level, hall, den, player).getString();
				helper.assertTrue(RaidHelpers.hireOf(level.getServer(), player.getUUID()).map(h -> h.helpers.contains(master.getUUID())).orElse(false),
					"no helper for the den: " + said);
				level.getServer().getPlayerList().remove(player);
				cleanUp(helper, hall, den);
				RaidHelperCompatTests.time(level, time);
			})
			.thenSucceed();
	}

	/** A den is there first, with no hall near (so it isn't the village's yet); the hall set down by it then finds it. */
	//$ gametest_ticks_batch 'CompatGameTests.AREA' '200' '"raidDenHallLater"'
	@GameTest(template = CompatGameTests.AREA, timeoutTicks = 200, batch = "raidDenHallLater")
	public void aHallSetDownByADenFindsIt(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos den = helper.absolutePos(DEN);
		helper.setBlock(DEN, RaidDenCompatTests.den("tier_two", "water", false));
		helper.startSequence()
			.thenIdle(20)
			// Halls left by earlier compat batches can stand within reach, so the "not recorded" half only holds with none.
			.thenExecute(() -> helper.assertTrue(RaidDens.hallNear(level, den).isPresent()
				|| RaidDens.Sites.get(level).dens().stream().noneMatch(d -> d.pos().equals(den)),
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
