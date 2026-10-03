package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * QA for B28 (spec: "a builder employed at a bench keeps it while it stands"), from the other sides than the fix's own
 * tests: far from the bench (vanilla's 100-block give-up), stuck with a free bench right beside them, and the "while
 * it stands" boundary (a broken bench is let go).
 */
public class QaB28BenchGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 3);
	/** A ledger high above the area: over 100 blocks (Manhattan) from the bench, with no way down. */
	private static final int UP = 104;

	/**
	 * A builder stranded far above their bench (over 100 blocks off, no way down, stuck for over a minute), with a free
	 * bench at their feet: they keep their own bench, its place stays taken, and the free bench stays free.
	 */
	//$ gametest_ticks_batch AREA '500' '"qa_b28_far"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_b28_far")
	public void qaB28AFarStrandedBuilderKeepsTheirBenchOverAFreeOneBeside(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		BlockPos ledge = helper.absolutePos(new BlockPos(4, 2 + UP, 4));
		List<BlockPos> placed = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos p = ledge.offset(dx, -1, dz);
				level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
				placed.add(p);
			}
		}
		BlockPos otherBench = ledge.east();
		level.setBlockAndUpdate(otherBench, ModBlocks.BUILDERS_BENCH.defaultBlockState());
		placed.add(otherBench);
		Villager builder = EntityType.VILLAGER.create(level);
		builder.moveTo(ledge.getX() + 0.5, ledge.getY(), ledge.getZ() + 0.5, 0, 0);
		builder.setPersistenceRequired();
		level.addFreshEntity(builder);
		BlockPos bench = helper.absolutePos(BENCH);
		helper.assertTrue(builder.blockPosition().distManhattan(bench) > 100, "the ledge isn't over 100 blocks from the bench");
		Builders.employ(level, builder, bench);
		builder.getBrain().setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, level.getGameTime() - 1300);
		GlobalPos own = GlobalPos.of(level.dimension(), bench);

		List<String> lost = new ArrayList<>();
		int[] walkingHome = {0};
		helper.onEachTick(() -> {
			if (builder.getBrain().getMemory(MemoryModuleType.WALK_TARGET).filter(w -> w.getTarget().currentBlockPosition().equals(bench)).isPresent()) {
				walkingHome[0]++; // ticks spent trying the unreachable bench; the fix retries every 5 s, not every tick
			}
			var site = builder.getBrain().getMemory(MemoryModuleType.JOB_SITE);
			if (lost.isEmpty() && !site.equals(Optional.of(own))) {
				lost.add("tick " + helper.getTick() + ": " + site.map(g -> g.pos().toShortString()).orElse("none"));
			}
		});
		helper.runAtTickTime(440, () -> {
			boolean ownTaken = level.getPoiManager().getFreeTickets(bench) == 0;
			boolean otherFree = level.getPoiManager().getFreeTickets(otherBench) == 1;
			boolean alive = builder.isAlive();
			placed.forEach(p -> level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState()));
			builder.discard();
			helper.assertTrue(alive, "the stranded builder died on the ledge");
			helper.assertTrue(lost.isEmpty(), "the far builder let their bench go: " + lost);
			helper.assertTrue(ownTaken, "the far builder's bench was freed");
			helper.assertTrue(otherFree, "the far builder took the free bench beside them");
			helper.assertTrue(walkingHome[0] > 0, "the far builder never tried to walk home");
			helper.assertTrue(walkingHome[0] <= 40, "the far builder tried the unreachable bench on " + walkingHome[0] + " of 440 ticks (every 5 s expected)");
			helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("qaB28 far: tried home on " + walkingHome[0] + " ticks"));
			helper.succeed();
		});
	}

	/** "While it stands": the bench is broken under a builder standing by it, and they don't keep a bench that's gone. */
	//$ gametest_ticks_batch AREA '400' '"qa_b28_broken"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_b28_broken")
	public void qaB28ABrokenBenchIsLetGo(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		helper.runAtTickTime(20, () -> {
			helper.assertTrue(builder.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent(), "not employed at the bench");
			helper.destroyBlock(BENCH);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 20, "bench not broken yet");
			var site = builder.getBrain().getMemory(MemoryModuleType.JOB_SITE);
			helper.assertTrue(site.isEmpty(), "still holds the broken bench at " + site.map(g -> g.pos().toShortString()).orElse(""));
		});
	}

	/**
	 * Two builders stuck for over a minute, each standing between both benches, run through a whole night and back to
	 * work: neither ever holds the other's bench (vanilla's rest and meet activities in between).
	 */
	//$ gametest_ticks_batch AREA '900' '"qa_b28_night"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qa_b28_night")
	public void qaB28StuckBuildersKeepTheirBenchesThroughTheEvening(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(8000); // work, then the evening's meet, idle and rest activities (driven below)
		BlockPos benchA = BENCH;
		BlockPos benchB = new BlockPos(2, 2, 12);
		helper.setBlock(benchA, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(benchB, ModBlocks.BUILDERS_BENCH);
		Villager a = helper.spawn(EntityType.VILLAGER, benchB.north());
		Villager b = helper.spawn(EntityType.VILLAGER, benchA.south());
		Builders.employ(level, a, helper.absolutePos(benchA));
		Builders.employ(level, b, helper.absolutePos(benchB));
		List<String> lost = new ArrayList<>();
		helper.onEachTick(() -> {
			helper.setDayTime((int) (8000 + 5 * helper.getTick())); // 8000 → 12300 by tick 860
			long stuck = level.getGameTime() - 1300;
			for (Villager v : List.of(a, b)) { // stuck the whole time, every activity
				v.getBrain().setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, stuck);
			}
			check(helper, a, benchA, "A", lost);
			check(helper, b, benchB, "B", lost);
		});
		helper.runAtTickTime(860, () -> {
			helper.assertTrue(level.getDayTime() % 24000 > 12000, "the evening never came: day " + level.getDayTime() % 24000);
			helper.assertTrue(lost.isEmpty(), "a builder lost their bench: " + lost);
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(benchA)) == 0
				&& level.getPoiManager().getFreeTickets(helper.absolutePos(benchB)) == 0, "a bench's place was freed");
			helper.succeed();
		});
	}

	private static void check(GameTestHelper helper, Villager v, BlockPos bench, String name, List<String> lost) {
		GlobalPos own = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(bench));
		var site = v.getBrain().getMemory(MemoryModuleType.JOB_SITE);
		if (!site.equals(Optional.of(own)) && lost.stream().noneMatch(s -> s.startsWith(name))) {
			lost.add(name + " at tick " + helper.getTick() + " (day " + helper.getLevel().getDayTime() % 24000 + "): "
				+ site.map(g -> g.pos().toShortString()).orElse("none"));
		}
	}
}
