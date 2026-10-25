package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA (lane E, config extremes): a server owner may set any whole-number option anywhere its range allows (the file is
 * clamped to {@link WorkplaceConfig#RANGES}, the settings screen's sliders span them). At every option's lowest value
 * together, and at every option's highest together, the file survives the game's read and write-back unchanged, and a
 * small village (a hall, beds, a farmer's composter and field, a builder's bench, a guard post, a librarian's lectern,
 * four villagers who find their jobs themselves) keeps running through work hours and three day turns: nothing
 * crashes, nobody dies. {@code threatLevel} stays at its default (its top value sends raids and camps into the shared
 * test world, which would walk into later batches); it is covered by the threat tests.
 */
public class QaConfigExtremesGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);
	private static final List<String> KEPT = List.of("threatLevel");

	private static String file(Function<WorkplaceConfig.Range, Integer> pick) {
		StringBuilder json = new StringBuilder("{");
		for (Map.Entry<String, WorkplaceConfig.Range> e : WorkplaceConfig.RANGES.entrySet()) {
			if (!KEPT.contains(e.getKey())) {
				json.append('"').append(e.getKey()).append("\": ").append(pick.apply(e.getValue())).append(", ");
			}
		}
		return json.append("\"configVersion\": 99}").toString();
	}

	/** Every option at its lowest (then its highest) is read, written back and read again as the same numbers. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theExtremesSurviveTheFilesReadAndWriteBack(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (boolean low : new boolean[] {true, false}) {
			Path dir;
			try {
				dir = Files.createTempDirectory("aw-config-extremes");
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
			WorkplaceConfig first = WorkplaceConfig.parse(file(r -> low ? r.min() : r.max()));
			first.save(dir);
			WorkplaceConfig again = WorkplaceConfig.load(dir);
			again.save(dir);
			WorkplaceConfig third = WorkplaceConfig.load(dir);
			for (Map.Entry<String, WorkplaceConfig.Range> e : WorkplaceConfig.RANGES.entrySet()) {
				if (KEPT.contains(e.getKey())) {
					continue;
				}
				int want = low ? e.getValue().min() : e.getValue().max();
				if (first.getInt(e.getKey()) != want || again.getInt(e.getKey()) != want || third.getInt(e.getKey()) != want) {
					wrong.add(e.getKey() + (low ? " (lowest " : " (highest ") + want + "): " + first.getInt(e.getKey()) + ", "
						+ again.getInt(e.getKey()) + ", " + third.getInt(e.getKey()));
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "changed by the read and write-back: " + wrong);
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '900' '"qaConfigLowest"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaConfigLowest")
	public void aVillageKeepsRunningWithEveryOptionAtItsLowest(GameTestHelper helper) {
		village(helper, WorkplaceConfig.parse(file(WorkplaceConfig.Range::min)), "lowest");
	}

	//$ gametest_ticks_batch AREA '900' '"qaConfigHighest"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaConfigHighest")
	public void aVillageKeepsRunningWithEveryOptionAtItsHighest(GameTestHelper helper) {
		village(helper, WorkplaceConfig.parse(file(WorkplaceConfig.Range::max)), "highest");
	}

	private static void village(GameTestHelper helper, WorkplaceConfig config, String which) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		long dayTime = level.getDayTime();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		for (int x = 2; x <= 8; x++) {
			for (int z = 20; z <= 26; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.FARMLAND);
				helper.setBlock(new BlockPos(x, 2, z), Blocks.WHEAT);
			}
		}
		helper.setBlock(new BlockPos(5, 1, 23), Blocks.WATER);
		helper.setBlock(new BlockPos(5, 2, 23), Blocks.AIR);
		helper.setBlock(new BlockPos(10, 2, 22), Blocks.COMPOSTER);
		helper.setBlock(new BlockPos(20, 2, 8), ModBlocks.BUILDERS_BENCH);
		helper.setBlock(new BlockPos(20, 2, 20), ModBlocks.GUARD_POST);
		helper.setBlock(new BlockPos(8, 2, 8), Blocks.LECTERN);
		helper.setBlock(new BlockPos(24, 2, 14), Blocks.CHEST);
		List<Villager> people = new ArrayList<>();
		String[] names = {"Ada", "Bram", "Cora", "Dell"};
		for (int i = 0; i < names.length; i++) {
			BlockPos head = new BlockPos(16 + i * 2, 2, 26);
			helper.setBlock(head, Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.PART,
				net.minecraft.world.level.block.state.properties.BedPart.HEAD));
			helper.setBlock(head.south(), Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.PART,
				net.minecraft.world.level.block.state.properties.BedPart.FOOT));
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(12 + i * 2, 2, 14));
			v.setCustomName(Component.literal(names[i]));
			v.setPersistenceRequired();
			people.add(v);
		}
		try {
			config.apply();
		} catch (RuntimeException e) {
			new WorkplaceConfig().apply();
			throw e;
		}
		level.setDayTime((dayTime / 24000) * 24000 + 2000); // work hours
		// Three day turns while the village runs: each hall round, season and daily count meets the new day.
		for (int turn = 1; turn <= 3; turn++) {
			int at = turn * 200;
			helper.runAfterDelay(at, () -> level.setDayTime(level.getDayTime() + 24000));
		}
		helper.runAtTickTime(850, () -> {
			try {
				List<String> dead = people.stream().filter(v -> !v.isAlive()).map(v -> v.getName().getString()).toList();
				helper.assertTrue(dead.isEmpty(), "with every option at its " + which + ", gone: " + dead);
				helper.assertTrue(level.getBlockState(helper.absolutePos(HALL)).is(ModBlocks.VILLAGE_HALL), "the hall is gone");
			} finally {
				new WorkplaceConfig().apply();
				level.setDayTime(dayTime);
				people.forEach(v -> v.discard());
			}
			helper.succeed();
		});
	}
}
