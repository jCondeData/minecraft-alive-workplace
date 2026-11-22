package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.Cellars;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA lane E (2026-11-22), from the specs:
 * <ul>
 * <li>B1418: after a server start or /reload the first question about older drawings must not read every shipped older
 * drawing (over a thousand files): a blueprint's own drawings are read the first time it is asked about and kept until
 * the next reload; a blueprint with no older drawing reads nothing; the answers are unchanged. Measured by files read,
 * not by the clock.</li>
 * <li>B1402: the Healing Center IV's modded copy ships its older drawings (the v0.157.0 one among them, under its content
 * key), each readable.</li>
 * </ul>
 */
public class QaHistoryLazy1122eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String DIR = "blueprint_history";
	private static final ResourceLocation STONE_HOUSE = AliveWorkplace.id("stone_house");
	private static final ResourceLocation HEALING_IV_MODDED = AliveWorkplace.id("modded/healing_center_4");
	/** git: the v0.157.0 tag's structure/modded/healing_center_4.nbt (blob 79c24dd6) ships as this file. */
	private static final String V0157 = "9c45f74f7";

	/** Shipped older drawing files by blueprint (a listing, nothing read). */
	private static Map<ResourceLocation, Integer> listed(MinecraftServer server) {
		Map<ResourceLocation, Integer> out = new TreeMap<>();
		for (ResourceLocation file : server.getResourceManager().listResources(DIR, id -> id.getPath().endsWith(".nbt")).keySet()) {
			String path = file.getPath().substring(DIR.length() + 1, file.getPath().length() - ".nbt".length());
			int slash = path.lastIndexOf('/');
			if (slash > 0) {
				out.merge(ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path.substring(0, slash)), 1, Integer::sum);
			}
		}
		return out;
	}

	/**
	 * A real /reload (the server's own resource reload, as the command does), then the Village Hall's cellar question
	 * about a Stone House: only the Stone House's files are read (not the thousand), the answers are those of before the
	 * reload, and asking again reads nothing.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qa1122eHistoryReload"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa1122eHistoryReload")
	public void afterARealReloadTheFirstHallQuestionReadsOnlyItsBuild(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Map<ResourceLocation, Integer> files = listed(server);
		int total = files.values().stream().mapToInt(Integer::intValue).sum();
		int stoneHouse = files.getOrDefault(STONE_HOUSE, 0);
		helper.assertTrue(stoneHouse > 0 && total > 500, "(setup) " + total + " shipped files, " + stoneHouse + " of the Stone House");
		List<String> keysBefore = Refreshes.olderKeys(server, STONE_HOUSE);
		List<String> healingBefore = Refreshes.olderKeys(server, HEALING_IV_MODDED);
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(),
			helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, Mirror.NONE);
		CompletableFuture<Void> reload = server.reloadResources(server.getPackRepository().getSelectedIds());
		boolean[] checked = {false};
		String[] failed = {null};
		helper.succeedWhen(() -> {
			helper.assertTrue(failed[0] == null, String.valueOf(failed[0]));
			helper.assertTrue(reload.isDone(), "the reload is still running");
			helper.assertTrue(!reload.isCompletedExceptionally(), "the reload failed");
			if (!checked[0]) {
				checked[0] = true;
				try {
				int before = Refreshes.filesRead();
				Cellars.needsDigging(helper.getLevel(), STONE_HOUSE, placement);
				int read = Refreshes.filesRead() - before;
				helper.assertTrue(read <= stoneHouse, "the first hall question after /reload read " + read + " drawing files (the Stone House has "
					+ stoneHouse + ", " + total + " ship)");
				List<String> keysAfter = Refreshes.olderKeys(server, STONE_HOUSE);
				// (at most its own files: a test running alongside may have asked about it first)
				helper.assertTrue(Refreshes.filesRead() - before <= stoneHouse, "after /reload the Stone House's " + stoneHouse
					+ " drawings took " + (Refreshes.filesRead() - before) + " file reads (wanted each at most once)");
				helper.assertTrue(keysAfter.equals(keysBefore), "the Stone House's older drawings changed over /reload: " + keysBefore + " -> " + keysAfter);
				int mid = Refreshes.filesRead();
				helper.assertTrue(Refreshes.olderKeys(server, HEALING_IV_MODDED).equals(healingBefore), "the modded Healing Center IV's keys changed over /reload");
				helper.assertTrue(Refreshes.filesRead() - mid <= files.getOrDefault(HEALING_IV_MODDED, 0), "asking about the modded Healing Center IV read "
					+ (Refreshes.filesRead() - mid) + " files, more than its " + files.getOrDefault(HEALING_IV_MODDED, 0));
				mid = Refreshes.filesRead();
				Cellars.needsDigging(helper.getLevel(), STONE_HOUSE, placement);
				Refreshes.olderKeys(server, HEALING_IV_MODDED);
				helper.assertTrue(Refreshes.filesRead() == mid, "asked again after /reload, " + (Refreshes.filesRead() - mid) + " files were read again");
				} catch (RuntimeException e) {
					failed[0] = e.getMessage();
					throw e;
				}
			}
		});
	}

	/**
	 * Every blueprint asked about in turn after a reload reads each shipped file exactly once (no file read for a
	 * neighbour such as stone_house_2 while asking about stone_house, none twice), and a second round reads nothing.
	 * Blueprints with no older drawing (a made-up one, a real one without history) read nothing.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa1122eHistorySweep"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa1122eHistorySweep")
	public void askingEveryBlueprintReadsEachFileOnce(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Map<ResourceLocation, Integer> files = listed(server);
		Refreshes.forgetShipped();
		int start = Refreshes.filesRead();
		Refreshes.olderKeys(server, AliveWorkplace.id("qa1122e_no_such_blueprint"));
		Refreshes.olderKeys(server, AliveWorkplace.id("stone")); // a prefix of stone_house, no folder of its own
		helper.assertTrue(Refreshes.filesRead() == start, "blueprints with no older drawing read " + (Refreshes.filesRead() - start) + " files");
		List<String> off = new ArrayList<>();
		for (Map.Entry<ResourceLocation, Integer> e : files.entrySet()) {
			int before = Refreshes.filesRead();
			List<String> keys = Refreshes.olderKeys(server, e.getKey());
			int read = Refreshes.filesRead() - before;
			if (read != e.getValue() || keys.size() != e.getValue()) {
				off.add(e.getKey() + ": read " + read + ", " + keys.size() + " keys, " + e.getValue() + " files");
			}
		}
		int total = files.values().stream().mapToInt(Integer::intValue).sum();
		helper.assertTrue(off.isEmpty(), off.size() + " blueprints read other than their own files once, e.g. " + off.stream().limit(6).toList());
		helper.assertTrue(Refreshes.filesRead() - start == total, "the round read " + (Refreshes.filesRead() - start) + " files of " + total);
		int second = Refreshes.filesRead();
		for (ResourceLocation structure : files.keySet()) {
			Refreshes.olderKeys(server, structure);
		}
		helper.assertTrue(Refreshes.filesRead() == second, "a second round read " + (Refreshes.filesRead() - second) + " files again");
		helper.succeed();
	}

	/** B1402: the modded Healing Center IV ships its v0.157.0 drawing, and every one of its older drawings reads. */
	//$ gametest_ticks 'FabricGameTest.EMPTY_STRUCTURE' '100'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void theModdedHealingCenterIvShipsItsV0157Drawing(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<String> keys = Refreshes.olderKeys(server, HEALING_IV_MODDED);
		helper.assertTrue(keys.contains(V0157), "the modded Healing Center IV's older drawings are " + keys + ", without v0.157.0's (" + V0157 + ")");
		helper.assertTrue(keys.size() == listed(server).getOrDefault(HEALING_IV_MODDED, 0), "not every shipped file read as a drawing: " + keys);
		for (String key : keys) {
			helper.assertTrue(Refreshes.olderDrawing(server, HEALING_IV_MODDED, key).isPresent(), "older drawing " + key + " doesn't read");
		}
		helper.succeed();
	}
}
