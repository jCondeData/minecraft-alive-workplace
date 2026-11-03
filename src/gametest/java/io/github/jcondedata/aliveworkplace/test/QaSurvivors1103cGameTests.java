package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import io.github.jcondedata.aliveworkplace.city.Roads;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.DimensionDataStorage;

/**
 * QA (lane-c-1103-1253): the two survivors the nightly mutation run left in villages, each killed from what its bug
 * promises.
 * <ul>
 * <li>B754: a wagon-bed list first started on a world older than its first ticks (a save upgraded from before the list)
 * is marked to be saved, so the first autosave writes it and ends the old camp look: a cabin bed placed after that
 * autosave, server still running, is a home. (Mutant: {@code CampBeds.Data.fresh} without its {@code setDirty()}.)</li>
 * <li>B753: roads keep off every laid-out Wonder round the village, as they do finished buildings within the village
 * radius + 32. (Mutant: the Wonder reach as radius - 32.)</li>
 * </ul>
 */
public class QaSurvivors1103cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	/** The Test Folly's stage I is 9 by 9 (RoadWonderGameTests). */
	private static final int FOLLY_SIZE = 9;
	/** CampBeds.NEW_WORLD (package-private): a dimension whose clock is past it when its list starts is an older save. */
	private static final long NEW_WORLD = 100;

	// --- B754: an upgraded save's first list is written at the first autosave ---------------------------------------

	/**
	 * An older world (its clock well past its first ticks) that has never had a wagon-bed list: its list starts now, on
	 * the old camp look. The server's next autosave must write it (it is marked to be saved), which ends the look, so a
	 * cabin with a brown and a green bed by a campfire, a hay bale and the builder's Blueprint Table, built after that
	 * autosave while the server keeps running, is two homes, not two wagon beds.
	 */
	//$ gametest_ticks_batch HUGE '300' '"qaSurvivors1103cFreshList"'
	@GameTest(template = HUGE, timeoutTicks = 300, batch = "qaSurvivors1103cFreshList")
	public void qaAnUpgradedWorldsFirstListIsSavedAtTheFirstAutosaveAndEndsTheOldLook(GameTestHelper helper) {
		QaVillageGuards1031GameTests.campArea(helper); // restores the live list afterwards
		long time = helper.getLevel().getGameTime();
		// run alone, the test world may still be in its first ticks: wait until it is an older world
		helper.runAfterDelay(Math.max(1, NEW_WORLD + 1 - time), () -> upgradedWorldsFirstList(helper));
	}

	private static void upgradedWorldsFirstList(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(level.getGameTime() > NEW_WORLD, "set-up: the test world is still in its first ticks: " + level.getGameTime());
		DimensionDataStorage storage = level.getDataStorage();
		CampBeds.Data before = CampBeds.Data.get(level);
		// put the live list back on disk too, not just in memory: tests that save and reload it read the file
		Leftovers.after(helper, () -> {
			storage.set(CampBeds.Data.NAME, before);
			before.setDirty();
			storage.save();
		});
		forget(storage, CampBeds.Data.NAME); // as on a save from before the list: none in memory, none on disk
		CampBeds.Data first = CampBeds.Data.get(level);
		helper.assertTrue(first != before, "set-up: the list wasn't started afresh");
		helper.assertTrue(first.looks() && !first.complete(), "set-up: an older world's first list doesn't judge by the old camp look");
		helper.assertTrue(first.isDirty(), "an upgraded world's first wagon-bed list isn't marked to be saved: the next autosave skips it and the old camp look lasts until a restart");
		storage.save(); // the server's autosave
		helper.assertTrue(dataFile(storage, CampBeds.Data.NAME).isFile(), "the autosave didn't write the wagon-bed list to the world");
		helper.assertTrue(!first.looks(), "after the first autosave the list still judges unlisted beds by the old camp look");
		QaVillageFixes1101GameTests.cabinRound(helper, (beds, residents) -> {
			CampBeds.Data live = CampBeds.Data.get(level);
			for (int i = 0; i < 2; i++) {
				boolean wagon = CampBeds.isWagonBed(level, beds.get(i));
				helper.assertTrue(!wagon && beds.get(i).equals(QaVillageGuards1031GameTests.home(residents.get(i))),
					"a cabin bed placed after the upgraded world's first autosave is a wagon bed: bed " + i + " wagon " + wagon
						+ ", listed " + live.has(beds.get(i)) + ", looks " + live.looks() + ", home " + QaVillageGuards1031GameTests.home(residents.get(i)));
			}
			helper.succeed();
		});
	}

	/** Forgets {@code name} in memory and deletes its file, so the next get() starts it afresh. */
	private static void forget(DimensionDataStorage storage, String name) {
		try {
			Field cache = DimensionDataStorage.class.getDeclaredField("cache");
			cache.setAccessible(true);
			((Map<?, ?>) cache.get(storage)).remove(name);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
		File file = dataFile(storage, name);
		if (file.exists() && !file.delete()) {
			throw new IllegalStateException("can't delete " + file);
		}
	}

	private static File dataFile(DimensionDataStorage storage, String name) {
		try {
			Method method = DimensionDataStorage.class.getDeclaredMethod("getDataFile", String.class);
			method.setAccessible(true);
			return (File) method.invoke(storage, name);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

	// --- B753: roads keep off a Wonder anywhere within the village radius + 32 ---------------------------------------

	/**
	 * At the default village radius, a Wonder laid out and waiting for its goods with its nearest edge 10 blocks past the
	 * radius (still within radius + 32, where finished buildings are kept off) is among the ground the Steward's roads
	 * keep off; one whose nearest edge is 20 blocks past radius + 32 is not.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaSurvivors1103cWonderReach"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaSurvivors1103cWonderReach")
	public void qaRoadsKeepOffAWonderJustPastTheVillageRadius(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(new BlockPos(2, 2, 2));
		int radius = VillageHalls.RADIUS;
		BlockPos near = hall.offset(radius + 10, 0, -FOLLY_SIZE / 2);
		BlockPos far = hall.offset(radius + 32 + 20, 0, -FOLLY_SIZE / 2);
		layOut(helper, hall, near);
		layOut(helper, hall, far);
		List<BoundingBox> avoid = buildings(level, hall);
		helper.assertTrue(covers(avoid, near) && covers(avoid, near.offset(FOLLY_SIZE - 1, 0, FOLLY_SIZE - 1)),
			"roads don't keep off a waiting Wonder " + (radius + 10) + " blocks from the hall (village radius " + radius + ", reach "
				+ (radius + 32) + "): " + avoid);
		helper.assertTrue(!covers(avoid, far) && !covers(avoid, far.offset(FOLLY_SIZE - 1, 0, FOLLY_SIZE - 1)),
			"roads keep off a Wonder " + (radius + 52) + " blocks from the hall, past the village's reach of " + (radius + 32));
		helper.succeed();
	}

	/** Begins the Test Folly for {@code hall} and lays its stage I out at {@code origin}, waiting for its goods. */
	private static void layOut(GameTestHelper helper, BlockPos hall, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Wonders.loaded().get(FOLLY) != null, "the test folly isn't loaded: " + Wonders.problems());
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(FOLLY, hall, null, "", 1);
		Leftovers.after(helper, () -> manager.remove(site.id()));
		manager.place(site.id(), new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE), origin.offset(-1, 0, -1));
		WonderSite now = manager.site(site.id()).orElseThrow();
		helper.assertTrue(now.goingUp() && !now.started() && now.parts().isEmpty(), "set-up: the Wonder isn't laid out and waiting for its goods");
	}

	private static boolean covers(List<BoundingBox> boxes, BlockPos column) {
		return boxes.stream().anyMatch(b -> column.getX() >= b.minX() && column.getX() <= b.maxX() && column.getZ() >= b.minZ() && column.getZ() <= b.maxZ());
	}

	/** Roads.buildings (package-private): the ground road routing keeps off round {@code hall}. */
	@SuppressWarnings("unchecked")
	private static List<BoundingBox> buildings(ServerLevel level, BlockPos hall) {
		try {
			Method method = Roads.class.getDeclaredMethod("buildings", ServerLevel.class, BlockPos.class);
			method.setAccessible(true);
			return (List<BoundingBox>) method.invoke(null, level, hall);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
