package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (2026-11-11, lane c), B1171's failing tests, split from {@link QaCrafterLoop1111cGameTests} (same village and
 * watching). With a second builder helping on the build, every part is made once and reaches the lead's bench chest,
 * but the lead stalls for good with one oak plank in his bag (STRUCTURE, missing nothing) and the build is never done.
 * The helper test fails every run on main; the two-builder test about 1 run in 7. Commit with the fix.
 */
public class QaCrafterLoop1111cBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String HUGE = QaCrafterLoop1111cGameTests.HUGE;

	/**
	 * Builders help each other (the game's default): a second builder of the village, with nothing of his own to build,
	 * lives at his own table with a chest across the village and comes to help on the player's build. What the crafters
	 * make goes to the chests the build takes from, never to the helper's house, and is made once.
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaLoop1111cBugHelper"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaLoop1111cBugHelper")
	public void aVillageBuilderHelpingGetsNothingMadeIntoHisHouse(GameTestHelper helper) {
		QaCrafterLoop1111cGameTests.Run run = QaCrafterLoop1111cGameTests.village(helper, "helper", QaCrafterLoop1111cGameTests.mixedRow(), true, true);
		QaCrafterLoop1111cGameTests.chests(helper, false);
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, level.getServer());
		helper.setBlock(QaCrafterLoop1111cGameTests.BENCH_B, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(QaCrafterLoop1111cGameTests.BENCH_B_CHEST, Blocks.CHEST);
		QaCrafterLoop1111cGameTests.put(helper, QaCrafterLoop1111cGameTests.BENCH_B_CHEST, new ItemStack(Items.BREAD, 3));
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(QaCrafterLoop1111cGameTests.BENCH_B));
		Villager mate = helper.spawn(EntityType.VILLAGER, QaCrafterLoop1111cGameTests.BENCH_B.north());
		Builders.employ(level, mate, helper.absolutePos(QaCrafterLoop1111cGameTests.BENCH_B));
		run.others.add(mate.getUUID());
		AtomicBoolean helped = new AtomicBoolean();
		helper.onEachTick(() -> {
			Villager v = run.villager(mate.getUUID());
			if (v != null && Builders.isHelping(v)) {
				helped.set(true);
			}
		});
		run.mustHappen.put("the second builder came to help", helped);
		QaCrafterLoop1111cGameTests.watch(helper, run, List.of(QaCrafterLoop1111cGameTests.BENCH_B_CHEST));
	}

	/**
	 * Scenario 4: two builders on two separate builds in the village (builders may help each other, the game's default),
	 * each needing 4 oak planks and 4 oak stairs, each with a chest by his own table; the Storehouse holds 6 oak logs; one
	 * Carpenter. Both builds are done, no more than 8 oak stairs ever exist, and nothing more is crafted after.
	 */
	//$ gametest_ticks_batch HUGE '7000' '"qaLoop1111cBugTwo"'
	@GameTest(template = HUGE, timeoutTicks = 7000, batch = "qaLoop1111cBugTwo")
	public void twoBuildersEachGetTheirOwnWoodworkOnce(GameTestHelper helper) {
		Map<BlockPos, BlockState> wood = new LinkedHashMap<>();
		for (int x = 0; x < 4; x++) {
			wood.put(new BlockPos(x, 0, 0), Blocks.OAK_PLANKS.defaultBlockState());
			wood.put(new BlockPos(x, 0, 1), Blocks.OAK_STAIRS.defaultBlockState());
		}
		QaCrafterLoop1111cGameTests.Run run = QaCrafterLoop1111cGameTests.village(helper, "two", wood, true, false);
		helper.getLevel().getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, helper.getLevel().getServer());
		helper.setBlock(QaCrafterLoop1111cGameTests.BENCH_CHEST, Blocks.CHEST);
		helper.setBlock(QaCrafterLoop1111cGameTests.STORE_CHEST, Blocks.CHEST);
		helper.setBlock(QaCrafterLoop1111cGameTests.STORE_CHEST_2, Blocks.CHEST);
		QaCrafterLoop1111cGameTests.put(helper, QaCrafterLoop1111cGameTests.STORE_CHEST, new ItemStack(Items.OAK_LOG, 6));
		helper.setBlock(QaCrafterLoop1111cGameTests.CARPENTER_CHEST, Blocks.CHEST);
		// the second builder, his table and chest, and the same player's second blueprint
		helper.setBlock(QaCrafterLoop1111cGameTests.BENCH_B, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(QaCrafterLoop1111cGameTests.BENCH_B_CHEST, Blocks.CHEST);
		run.builders.add(QaCrafterLoop1111cGameTests.builder(helper, run, QaCrafterLoop1111cGameTests.BENCH_B, QaCrafterLoop1111cGameTests.SITE_B));
		run.caps.put(Items.OAK_STAIRS, 8);
		QaCrafterLoop1111cGameTests.watch(helper, run, List.of());
	}
}
