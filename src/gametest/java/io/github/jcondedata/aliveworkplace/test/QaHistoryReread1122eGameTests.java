package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * QA lane E (2026-11-22, lane-e-1122-1913): B1418's promise (its commit and docs/wiki/village-hall.md): a blueprint's
 * older drawings are read the first time it is asked about "and cached until the next reload". The other half of that
 * promise: after a /reload (a datapack may have changed or added an older drawing), the next question reads the
 * blueprint's drawings again from the reloaded files instead of answering from what was read before. Written after a
 * mutation spot-check (the cache kept over a reload) survived the suite.
 */
public class QaHistoryReread1122eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation STONE_HOUSE = AliveWorkplace.id("stone_house");

	/**
	 * The Stone House's older drawings asked about (read once, a second question reads nothing), then a real /reload: the
	 * first question after it reads each of its drawings again, once, with the same answer.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qa1122eHistoryReread"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa1122eHistoryReread")
	public void afterAReloadABlueprintsOlderDrawingsAreReadAgain(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<String> keysBefore = Refreshes.olderKeys(server, STONE_HOUSE); // (read now, if nothing asked before)
		helper.assertTrue(!keysBefore.isEmpty(), "(setup) the Stone House ships no older drawing");
		int cached = Refreshes.filesRead();
		Refreshes.olderKeys(server, STONE_HOUSE);
		helper.assertTrue(Refreshes.filesRead() == cached, "(setup) asked twice before the reload, the second question read "
			+ (Refreshes.filesRead() - cached) + " files");
		CompletableFuture<Void> reload = server.reloadResources(server.getPackRepository().getSelectedIds());
		boolean[] checked = {false};
		String[] failed = {null};
		helper.succeedWhen(() -> {
			helper.assertTrue(failed[0] == null, String.valueOf(failed[0]));
			helper.assertTrue(reload.isDone(), "the reload is still running");
			helper.assertTrue(!reload.isCompletedExceptionally(), "the reload failed");
			if (checked[0]) {
				return;
			}
			checked[0] = true;
			try {
				int before = Refreshes.filesRead();
				List<String> keysAfter = Refreshes.olderKeys(server, STONE_HOUSE);
				int read = Refreshes.filesRead() - before;
				helper.assertTrue(read == keysBefore.size(), "the first question about the Stone House after /reload read " + read
					+ " drawing files (wanted its " + keysBefore.size() + " drawings read again from the reloaded files)");
				helper.assertTrue(keysAfter.equals(keysBefore), "the Stone House's older drawings changed over /reload: " + keysBefore + " -> " + keysAfter);
				AliveWorkplace.LOG.info("[qa1122e] after /reload the Stone House's {} older drawings were read again ({} files)", keysAfter.size(), read);
			} catch (RuntimeException e) {
				failed[0] = e.getMessage();
				throw e;
			}
		});
	}
}
