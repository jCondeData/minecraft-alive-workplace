package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Scouts;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Bug test (lane D QA, 2026-10-24), ROADMAP 32.12 from the spec: the Scouting Report is "a filled map (like an explorer map)
 * between the village and the lair with a red X on it". ScoutGameTests checks one report's X; the nightly sweep found
 * the choice of the map's scale had no test (a mutant of Scouts.java:466 survived). Here, for villages at several
 * places and lairs 60 to 120 blocks off in eight directions, the map drawn shows both the village and the lair inside
 * its edges, with the red X on the lair. Fails on main: when the lair lies across a line where the game's 2048-block
 * map squares meet, no scale from 1 to 3 holds both, Scouts.scale falls back to 4, and the scale-4 square holds only one.
 */
public class QaScoutReportGameTests implements FabricGameTest {
	//$ gametest_batch EMPTY_STRUCTURE '"qaScoutReportMaps"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaScoutReportMaps")
	public void everyScoutingReportShowsBothTheVillageAndTheLair(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		int[][] dirs = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
		List<String> wrong = new ArrayList<>();
		int maps = 0;
		// Villages here and a little way off, and villages whose lair lies across a line where the game's largest map
		// squares (2048 blocks, at x or z = 2048k - 64) meet: a lair 60 to 120 blocks off can be on either side of one.
		int lineX = Math.round((base.getX() + 64) / 2048f) * 2048 - 64;
		int lineZ = Math.round((base.getZ() + 64) / 2048f) * 2048 - 64;
		List<BlockPos> halls = new ArrayList<>(List.of(base, base.offset(37, 0, -53), base.offset(-91, 0, 70),
			new BlockPos(lineX - 40, base.getY(), base.getZ()), new BlockPos(lineX + 5, base.getY(), base.getZ()),
			new BlockPos(base.getX(), base.getY(), lineZ - 40), new BlockPos(base.getX(), base.getY(), lineZ + 5),
			new BlockPos(lineX - 30, base.getY(), lineZ + 20)));
		for (BlockPos hall : halls) {
			for (int d = 0; d < dirs.length; d++) {
				int far = 60 + 20 * (d % 4);
				double n = Math.sqrt(dirs[d][0] * dirs[d][0] + dirs[d][1] * dirs[d][1]);
				BlockPos at = hall.offset((int) Math.round(dirs[d][0] * far / n), 0, (int) Math.round(dirs[d][1] * far / n));
				Lairs.Lair lair = new Lairs.Lair(at, hall, new UUID(7, d), 1, Threats.BANDITS, 6, 1, 0, 0, 0);
				ItemStack report = Scouts.report(level, hall, lair, Component.literal("Tester"), 1);
				maps++;
				MapItemSavedData data = report.is(Items.FILLED_MAP) ? MapItem.getSavedData(report, level) : null;
				if (data == null) {
					wrong.add("no map for a lair at " + at.subtract(hall).toShortString());
					continue;
				}
				int half = 64 << data.scale;
				boolean village = Math.abs(hall.getX() - data.centerX) < half && Math.abs(hall.getZ() - data.centerZ) < half;
				boolean camp = Math.abs(at.getX() - data.centerX) < half && Math.abs(at.getZ() - data.centerZ) < half;
				MapDecorations.Entry x = report.getOrDefault(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY).decorations().get("+");
				boolean marked = x != null && (int) x.x() == at.getX() && (int) x.z() == at.getZ();
				if (!village || !camp || !marked) {
					wrong.add("village at " + hall.toShortString() + ", lair " + at.subtract(hall).toShortString() + " off: map centred "
						+ data.centerX + ", " + data.centerZ + " at scale " + data.scale + " shows the village " + village + ", the lair " + camp + ", X " + marked);
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " of " + maps + " reports: " + wrong);
		helper.succeed();
	}
}
