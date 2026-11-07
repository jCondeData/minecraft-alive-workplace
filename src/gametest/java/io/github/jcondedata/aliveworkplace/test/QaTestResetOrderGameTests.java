package io.github.jcondedata.aliveworkplace.test;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.RetryOptions;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;

/**
 * B977/B765: batch 'qaChainLibrarianShort' never started after 'qaCampNewWarband'. When a batch's last test ends, the
 * runner's listener (on the test from the start) un-forces every chunk and lays out the next batch, forcing its area
 * chunks, inside the same call; a test's {@link Leftovers#after} reset heard after it let go of a chunk it had forced
 * for itself (the camp ring) that the next batch's area had just been given, so that test waited for its chunks
 * forever. A test's resets have to run before the runner hears of its end.
 */
public class QaTestResetOrderGameTests {
	/** The runner's batch listener: declared inside GameTestRunner (its anonymous class). */
	private static boolean runners(GameTestListener listener) {
		return listener.getClass().getEnclosingClass() == GameTestRunner.class;
	}

	/**
	 * The mechanism, played out on a test of its own: a test forces a chunk for itself and lets it go in its reset; the
	 * "runner" (added first, as vanilla's is) un-forces it and forces it again for the next batch's area when the test
	 * ends. The chunk is still forced afterwards.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aTestsResetLettingGoOfAChunkNeverUnforcesTheNextBatchsArea(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ChunkPos chunk = new ChunkPos(helper.absolutePos(net.minecraft.core.BlockPos.ZERO).offset(0, 0, 4096));
		helper.assertFalse(level.getForcedChunks().contains(chunk.toLong()), "(setup) the far chunk " + chunk + " is forced already");
		try {
			GameTestInfo info = new GameTestInfo(new TestFunction("qaResetOrder", "qaResetOrder.probe", FabricGameTest.EMPTY_STRUCTURE, 100, 0, true, h -> {
			}), Rotation.NONE, level, new RetryOptions(1, false));
			List<String> heard = new ArrayList<>();
			info.addListener(new GameTestListener() { // the runner's: un-forces every chunk, then lays out the next batch here
				@Override
				public void testStructureLoaded(GameTestInfo test) {
				}

				@Override
				public void testPassed(GameTestInfo test, GameTestRunner runner) {
					heard.add("runner");
					level.setChunkForced(chunk.x, chunk.z, false);
					level.setChunkForced(chunk.x, chunk.z, true);
				}

				@Override
				public void testFailed(GameTestInfo test, GameTestRunner runner) {
				}

				@Override
				public void testAddedForRerun(GameTestInfo old, GameTestInfo test, GameTestRunner runner) {
				}
			});
			GameTestHelper probe = new GameTestHelper(info);
			// the test's own chunk, forced for itself and let go when it ends (as qaCampNewWarband's camp ring)
			List<ChunkPos> forced = new ArrayList<>();
			if (level.setChunkForced(chunk.x, chunk.z, true)) {
				forced.add(chunk);
			}
			Leftovers.after(probe, () -> {
				heard.add("reset");
				forced.forEach(c -> level.setChunkForced(c.x, c.z, false));
			});
			info.getListeners().forEach(l -> l.testPassed(info, null));
			helper.assertTrue(heard.equals(List.of("reset", "runner")), "heard in this order: " + heard);
			helper.assertTrue(level.getForcedChunks().contains(chunk.toLong()), "the next batch's chunk " + chunk + " was un-forced by the earlier test's reset");
		} finally {
			level.setChunkForced(chunk.x, chunk.z, false);
		}
		helper.succeed();
	}

	/** On a real test: its reset is ahead of the runner's own listener, and resets keep the order they were added in. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aRealTestsResetsComeBeforeTheRunnerInTheirOwnOrder(GameTestHelper helper) {
		GameTestInfo info;
		try {
			java.lang.reflect.Field field = GameTestHelper.class.getDeclaredField("testInfo");
			field.setAccessible(true);
			info = (GameTestInfo) field.get(helper);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		List<String> ran = new ArrayList<>();
		Runnable first = () -> ran.add("first");
		Runnable second = () -> ran.add("second");
		Leftovers.after(helper, first);
		Leftovers.after(helper, second);
		List<GameTestListener> listeners = info.getListeners().toList();
		int runner = -1;
		for (int i = 0; i < listeners.size(); i++) {
			if (runners(listeners.get(i))) {
				runner = i;
				break;
			}
		}
		helper.assertTrue(runner >= 0, "(setup) the runner's listener isn't on the test: " + listeners);
		helper.assertTrue(listeners.get(0) instanceof Leftovers.Reset && listeners.get(1) instanceof Leftovers.Reset && runner > 1,
			"the resets aren't ahead of the runner (at " + runner + "): " + listeners);
		// their order: run the two resets' end by hand (they only add to a list)
		listeners.get(0).testFailed(info, null);
		listeners.get(1).testFailed(info, null);
		helper.assertTrue(ran.equals(List.of("first", "second")), "the resets ran in this order: " + ran);
		helper.succeed();
	}
}
