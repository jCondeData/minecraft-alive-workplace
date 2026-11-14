package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.bounty.Rogues;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.ClassMigration;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * B436, the lag when an old world is first opened: a hall read with its chunk had its whole round on its first tick,
 * while the server was still loading the world and before its villagers were in; every hall loaded together had it in
 * the same tick; and a rogue's bounty read the biome 150 to 250 blocks out by waiting for chunks. Now such a hall waits
 * {@code FIRST_ROUND_WAIT} ticks, two halls' first rounds are {@code FIRST_ROUND_GAP} ticks apart, and the biome is
 * read without a chunk. A hall that is placed still has its round on its first tick, and everyone is seeded as before.
 */
public class HallFirstRoundGameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** The hall at {@code pos} (absolute) as a server restart leaves it: saved, and read again the way a chunk reads it. */
	static VillageHallBlockEntity restarted(ServerLevel level, BlockPos pos) {
		VillageHallBlockEntity was = (VillageHallBlockEntity) level.getBlockEntity(pos);
		CompoundTag saved = was.saveWithFullMetadata(level.registryAccess());
		BlockEntity read = BlockEntity.loadStatic(pos, level.getBlockState(pos), saved, level.registryAccess());
		level.setBlockEntity(read);
		return (VillageHallBlockEntity) level.getBlockEntity(pos);
	}

	/** Ticks until {@code hall}'s turn in the round every {@code CHECK_EVERY} ticks (0: this tick), which no wait holds back. */
	private static int ticksToTurn(ServerLevel level, BlockPos hall) {
		return Math.floorMod(-(level.getGameTime() + hall.hashCode()), VillageNeeds.CHECK_EVERY);
	}

	/** Whether a first round seen {@code seen} ticks in was the hall's turn {@code turn} ticks in (seen then or a tick later). */
	private static boolean atTurn(long seen, int turn) {
		return seen == turn || seen == turn + 1;
	}

	/** The waits as the test wants them, put back when it ends. */
	private static void waits(GameTestHelper helper, int wait, int gap) {
		int wasWait = VillageHallBlockEntity.FIRST_ROUND_WAIT;
		int wasGap = VillageHallBlockEntity.FIRST_ROUND_GAP;
		VillageHallBlockEntity.FIRST_ROUND_WAIT = wait;
		VillageHallBlockEntity.FIRST_ROUND_GAP = gap;
		Leftovers.after(helper, () -> {
			VillageHallBlockEntity.FIRST_ROUND_WAIT = wasWait;
			VillageHallBlockEntity.FIRST_ROUND_GAP = wasGap;
		});
	}

	/**
	 * Four halls read with their chunks in the same tick: none has its round before the wait is over (or its own turn
	 * comes), no two have their first round within the gap of each other, and all four have had it soon after. A fifth
	 * hall, placed by hand, has its round on its first tick as it always did.
	 */
	//$ gametest_ticks_batch HUGE '300' '"hallFirstRoundTogether"'
	@GameTest(template = HUGE, timeoutTicks = 300, batch = "hallFirstRoundTogether")
	public void hallsLoadedTogetherWaitAndTakeTurnsForTheirFirstRound(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 6;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		int wait = 30;
		int gap = 10;
		waits(helper, wait, gap);
		BlockPos[] spots = {new BlockPos(5, 2, 5), new BlockPos(5, 2, 25), new BlockPos(25, 2, 5), new BlockPos(25, 2, 25)};
		List<BlockPos> halls = new ArrayList<>();
		int[] quiet = new int[spots.length];
		int[] turn = new int[spots.length];
		for (int i = 0; i < spots.length; i++) {
			helper.setBlock(spots[i], ModBlocks.VILLAGE_HALL);
			BlockPos pos = helper.absolutePos(spots[i]);
			VillageHallBlockEntity hall = restarted(level, pos);
			helper.assertTrue(hall.awaitingFirstRound() && hall.needs() == null, "a hall read with its chunk waits for its first round");
			halls.add(pos);
			turn[i] = ticksToTurn(level, pos);
			quiet[i] = Math.min(wait, turn[i]);
		}
		BlockPos placedAt = new BlockPos(15, 2, 15);
		helper.setBlock(placedAt, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity placed = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(placedAt));
		helper.assertTrue(!placed.awaitingFirstRound(), "a placed hall waits for nothing");
		long start = level.getGameTime();
		long[] firstRound = {-1, -1, -1, -1};
		helper.onEachTick(() -> {
			long tick = level.getGameTime() - start;
			if (tick == 3) {
				helper.assertTrue(placed.needs() != null, "the placed hall had its round on its first tick");
			}
			for (int i = 0; i < halls.size(); i++) {
				VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(halls.get(i));
				if (firstRound[i] < 0 && hall.needs() != null) {
					firstRound[i] = tick;
					// the round ran in the tick before this look at it, or in this one
					helper.assertTrue(tick >= quiet[i], "hall " + i + " had its first round after " + tick + " ticks, before its wait of " + quiet[i] + " was over");
				}
			}
		});
		helper.runAfterDelay(wait + gap * spots.length + 20, () -> {
			for (int i = 0; i < firstRound.length; i++) {
				helper.assertTrue(firstRound[i] >= 0, "hall " + i + " never had its first round: " + java.util.Arrays.toString(firstRound));
				for (int j = 0; j < i; j++) {
					// a hall whose own turn came first had its round then, whatever the others did: during the wait, or
					// after it but before its place behind the others (B1164: that second case was not excused, so the
					// test failed whenever a hall's turn fell there, about 1 run in 15: the halls at (5, 25) and (25, 5) always
					// share a turn and the one at (5, 5) has it 20 ticks later; QaHallFirstRound1111eGameTests sets it up)
					boolean turns = atTurn(firstRound[i], turn[i]) || atTurn(firstRound[j], turn[j]);
					helper.assertTrue(turns || Math.abs(firstRound[i] - firstRound[j]) >= gap,
						"halls " + j + " and " + i + " had their first rounds within " + gap + " ticks of each other: " + java.util.Arrays.toString(firstRound));
				}
			}
			helper.succeed();
		});
	}

	/**
	 * An old world's hall with forty villagers and classes on, opened by the new jar: nobody is seeded while the hall
	 * waits, no tick seeds more than a round's sixteen, a restart after the first round keeps who was seeded and the hall
	 * waits again, and in the end all forty have a class, the hall is seeded and its chronicle says so once.
	 */
	//$ gametest_ticks_batch BIG '600' '"hallFirstRoundSeeding"'
	@GameTest(template = BIG, timeoutTicks = 600, batch = "hallFirstRoundSeeding")
	public void anOldVillageIsSeededRoundByRoundAfterTheWaitAndThroughARestart(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaClassMigrationGameTests.oldHall(helper, false);
		BlockPos pos = helper.absolutePos(new BlockPos(11, 2, 11));
		List<Villager> villagers = QaClassMigrationGameTests.singles(helper, 40);
		int wait = 30;
		waits(helper, wait, 5);
		int every = VillageNeeds.CHECK_EVERY;
		VillageNeeds.CHECK_EVERY = 40;
		ClassMigration.AUTO = true;
		Leftovers.after(helper, () -> {
			VillageNeeds.CHECK_EVERY = every;
			ClassMigration.AUTO = false;
		});
		VillageHallBlockEntity opened = restarted(level, pos);
		helper.assertTrue(opened.awaitingFirstRound() && opened.classesVersion() == 0, "the old hall waits for its first round, unseeded");
		int[] quiet = {Math.min(wait, ticksToTurn(level, pos))};
		long[] since = {level.getGameTime()};
		long[] last = {0};
		boolean[] restartedOnce = {false};
		List<Long> rounds = new ArrayList<>();
		helper.onEachTick(() -> {
			long seeded = QaClassMigrationGameTests.seededCount(villagers);
			long tick = level.getGameTime() - since[0];
			if (seeded > last[0]) {
				helper.assertTrue(tick >= quiet[0], "someone was seeded " + tick + " ticks after the hall loaded, before its wait of " + quiet[0] + " was over");
				helper.assertTrue(seeded - last[0] <= ClassMigration.SLICE, "one tick seeded " + (seeded - last[0]) + ", more than a round's " + ClassMigration.SLICE);
				rounds.add(seeded - last[0]);
				last[0] = seeded;
				if (!restartedOnce[0] && seeded < villagers.size()) {
					// The server stops and starts part-way: the hall is read again, and who was seeded stays seeded.
					restartedOnce[0] = true;
					VillageHallBlockEntity again = restarted(level, pos);
					helper.assertTrue(again.awaitingFirstRound() && again.classesVersion() == 0, "after the restart the hall waits again, still unseeded");
					since[0] = level.getGameTime();
					quiet[0] = Math.min(wait, ticksToTurn(level, pos));
				}
			}
		});
		helper.succeedWhen(() -> {
			VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(pos);
			helper.assertTrue(hall.classesVersion() == ClassMigration.VERSION, "the hall isn't seeded yet: " + last[0] + " of " + villagers.size());
			helper.assertTrue(villagers.stream().allMatch(ModAttachments.SOCIAL_CLASS::has), "not everyone has a class: " + QaClassMigrationGameTests.seededCount(villagers));
			helper.assertTrue(restartedOnce[0] && rounds.size() >= 3, "forty were seeded in rounds of at most sixteen, through a restart: " + rounds);
			helper.assertTrue(hall.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.CLASS).count() == 1, "the chronicle says it once");
			helper.assertTrue(hall.needs() != null && hall.needs().villagers() >= villagers.size(), "the round counted the whole village: " + hall.needs());
		});
	}

	/**
	 * A rogue's biome is read without loading or waiting for a chunk: far out, where nothing is loaded, it is what the
	 * world itself says is there, and no chunk is there afterwards; where the world is loaded it is the chunk's own.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aRoguesBiomeIsReadWithoutAChunk(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos here = helper.absolutePos(BlockPos.ZERO);
		helper.assertTrue(level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(here.getX()), SectionPos.blockToSectionCoord(here.getZ())) != null, "the test stands in a loaded chunk");
		helper.assertTrue(Rogues.biome(level, here).equals(level.getBiome(here)), "in a loaded chunk, the chunk's own biome");
		BlockPos far = here.offset(40_000, 0, 40_000);
		int cx = SectionPos.blockToSectionCoord(far.getX());
		int cz = SectionPos.blockToSectionCoord(far.getZ());
		helper.assertTrue(!level.getChunkSource().hasChunk(cx, cz), "nothing is loaded far out");
		var biome = Rogues.biome(level, far);
		helper.assertTrue(!level.getChunkSource().hasChunk(cx, cz) && level.getChunkSource().getChunkNow(cx, cz) == null, "reading the biome loaded no chunk");
		helper.assertTrue(biome.equals(level.getBiome(far)), "far out, the biome the world says is there: " + biome.unwrapKey() + " and " + level.getBiome(far).unwrapKey());
		helper.assertTrue(Rogues.group(level, far) == Rogues.group(biome), "the group is the one of that biome");
		helper.succeed();
	}
}
