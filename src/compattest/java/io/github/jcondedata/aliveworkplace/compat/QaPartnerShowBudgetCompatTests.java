package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.PartnerShows;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane d, 2026-10-25) for ROADMAP 28.3's budget, from its spec: "one show per worker every 10 seconds at most, 6
 * running per level, none when no player is within 48 blocks". Each boundary from both sides: a worker's next cue 199
 * ticks after its last is refused and at 200 starts one; with six shows on the seventh worker's cue is refused, and
 * once they end it starts; work 47.5 blocks from the only player is shown and 48.5 blocks is not. Real Machops in real
 * pastures by real Builder's Benches. Each test runs alone in its batch.
 */
public class QaPartnerShowBudgetCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	static Villager builder(GameTestHelper helper, BlockPos bench) {
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, bench.south());
		Jobs.employ(helper.getLevel(), builder, helper.absolutePos(bench), ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
		return builder;
	}

	static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos by = helper.absolutePos(at);
		player.teleportTo(by.getX() + 0.5, by.getY(), by.getZ() + 0.5);
		PartnerShowsCompatTests.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		return player;
	}

	static ItemStack planks() {
		return new ItemStack(Items.OAK_PLANKS);
	}

	/** One worker, one Machop: the next cue 199 ticks after a show started is refused for the wait; at 200 it starts one. */
	//$ gametest_ticks_batch AREA '400' '"qaShowCooldown"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaShowCooldown")
	public void aWorkersNextShowWaitsExactlyTenSeconds(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		PartnerShowsCompatTests.clearHalls(helper);
		PartnerShowsCompatTests.showsOn(helper);
		ServerLevel level = helper.getLevel();
		Villager builder = builder(helper, new BlockPos(2, 2, 2));
		ServerPlayer player = player(helper, new BlockPos(1, 2, 6));
		BlockPos pasture = PastureCompatTests.pasture(helper, new BlockPos(10, 2, 10));
		PastureCompatTests.pastured(helper, pasture, player, "machop", Direction.NORTH);
		BlockPos work = helper.absolutePos(new BlockPos(5, 2, 5));
		long[] started = {Long.MIN_VALUE};
		boolean[] done = new boolean[1];
		helper.runAfterDelay(20, () -> {
			PartnerShows.forget(builder);
			helper.assertTrue(PartnerShows.cue(builder, "fetch", work, planks()), "the first cue started no show: " + PartnerShows.lastRefusal(builder));
			started[0] = level.getGameTime();
			PartnerShows.stopAll(); // the Machop is free again: only the worker's wait can refuse the next cue
		});
		helper.onEachTick(() -> {
			if (started[0] == Long.MIN_VALUE || done[0]) {
				return;
			}
			long since = level.getGameTime() - started[0];
			if (since == PartnerShows.WORKER_COOLDOWN - 1) {
				helper.assertFalse(PartnerShows.cue(builder, "fetch", work, planks()), "a second show " + since + " ticks after the first");
				helper.assertTrue(PartnerShows.lastRefusal(builder).contains("ticks ago"), "refused for another reason: " + PartnerShows.lastRefusal(builder));
			} else if (since == PartnerShows.WORKER_COOLDOWN) {
				helper.assertTrue(PartnerShows.cue(builder, "fetch", work, planks()), "10 s after the first show the next didn't start: " + PartnerShows.lastRefusal(builder));
				PartnerShows.stopAll();
				done[0] = true;
			}
		});
		helper.succeedWhen(() -> helper.assertTrue(done[0], "the 10 s mark wasn't reached"));
	}

	/** Seven builders, eight Machops: six shows start, the seventh worker's cue is refused while they're on, then starts. */
	//$ gametest_ticks_batch AREA '200' '"qaShowLevelCap"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaShowLevelCap")
	public void noMoreThanSixShowsAtOnceInALevel(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		PartnerShowsCompatTests.clearHalls(helper);
		PartnerShowsCompatTests.showsOn(helper);
		PartnerShows.stopAll();
		ServerLevel level = helper.getLevel();
		List<Villager> builders = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			builders.add(builder(helper, new BlockPos(1 + 2 * i, 2, 2)));
		}
		ServerPlayer player = player(helper, new BlockPos(1, 2, 6));
		Direction[] sides = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
		for (BlockPos at : List.of(new BlockPos(5, 2, 10), new BlockPos(11, 2, 10))) {
			BlockPos pasture = PastureCompatTests.pasture(helper, at);
			for (Direction side : sides) {
				PastureCompatTests.pastured(helper, pasture, player, "machop", side);
			}
		}
		BlockPos work = helper.absolutePos(new BlockPos(7, 2, 6));
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(PartnerShows.running(level) == 0, "shows already on: " + PartnerShows.running(level));
			for (int i = 0; i < 6; i++) {
				Villager b = builders.get(i);
				PartnerShows.forget(b);
				helper.assertTrue(PartnerShows.cue(b, "fetch", work, planks()), "builder " + (i + 1) + "'s show didn't start: " + PartnerShows.lastRefusal(b));
			}
			helper.assertTrue(PartnerShows.running(level) == 6, "shows on: " + PartnerShows.running(level));
			Villager seventh = builders.get(6);
			PartnerShows.forget(seventh);
			helper.assertFalse(PartnerShows.cue(seventh, "fetch", work, planks()), "a seventh show started with six on");
			helper.assertTrue(PartnerShows.lastRefusal(seventh).contains("6 shows already on"), "refused for another reason: " + PartnerShows.lastRefusal(seventh));
			PartnerShows.stopAll();
			helper.assertTrue(PartnerShows.cue(seventh, "fetch", work, planks()), "with the six over the seventh still didn't start: " + PartnerShows.lastRefusal(seventh));
			PartnerShows.stopAll();
			helper.succeed();
		});
	}

	/** The only player: work 47.5 blocks from them gets its show, work 48.5 blocks off gets none. */
	//$ gametest_ticks_batch AREA '200' '"qaShowPlayerRange"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaShowPlayerRange")
	public void aShowNeedsAPlayerWithinFortyEightBlocksOfTheWork(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		PartnerShowsCompatTests.clearHalls(helper);
		PartnerShowsCompatTests.showsOn(helper);
		ServerLevel level = helper.getLevel();
		Villager builder = builder(helper, new BlockPos(2, 2, 2));
		ServerPlayer player = player(helper, new BlockPos(1, 2, 6));
		BlockPos pasture = PastureCompatTests.pasture(helper, new BlockPos(10, 2, 10));
		PastureCompatTests.pastured(helper, pasture, player, "machop", Direction.NORTH);
		BlockPos by = helper.absolutePos(new BlockPos(1, 2, 6));
		helper.runAfterDelay(20, () -> {
			// straight up from the player: the distance is to the work block's centre
			BlockPos far = by.above(48);
			BlockPos near = by.above(47);
			helper.assertTrue(level.getNearestPlayer(far.getX() + 0.5, far.getY() + 0.5, far.getZ() + 0.5, 60, false) == player,
				"setup: another player is nearer the work than this test's");
			PartnerShows.forget(builder);
			helper.assertFalse(PartnerShows.cue(builder, "fetch", far, planks()), "a show for work 48.5 blocks from the only player");
			helper.assertTrue(PartnerShows.lastRefusal(builder).contains("no player within"), "refused for another reason: " + PartnerShows.lastRefusal(builder));
			PartnerShows.forget(builder);
			helper.assertTrue(PartnerShows.cue(builder, "fetch", near, planks()), "no show for work 47.5 blocks from the player: " + PartnerShows.lastRefusal(builder));
			PartnerShows.stopAll();
			helper.succeed();
		});
	}
}
