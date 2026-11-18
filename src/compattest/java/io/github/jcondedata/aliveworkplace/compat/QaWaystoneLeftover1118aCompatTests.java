package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.blay09.mods.waystones.core.WaystoneManagerImpl;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

/**
 * B1375 (lane A, 2026-11-18): "a moved plaza's repaired waystone leaves the village listed twice" was no dead entry of
 * the Mover's or the builder's. QaWaystones1117dCompatTests' moved-stone test counted every waystone named after its
 * village, and its village had two: its own, and the plaza the test placed just west of it had left standing
 * (WaystonesCompatTests' builder-built plaza: a 17-wide area, the plaza at 9,2,3, so its stone stood 16 blocks west of
 * the next test's stone and in the same row). The test's hall, named before its own plaza stood, took that plaza over,
 * as a village with no plaza of its own does with one whose hall is gone (B1188), and its stone got the village's name.
 * Which test stands west of which follows the batch names' hash order, so it came with the tests registered that day
 * and never when the class ran alone.
 *
 * <p>Here the same thing is set up: a village's plaza left standing where that neighbor's stood, 16 blocks west of
 * this test's stone and outside its area. With only the left hall cleared (what
 * {@link RaidHelperCompatTests#clearAround} did), a new hall takes the plaza over: the second entry B1375 saw. As
 * clearAround clears now, the plaza is taken away with the hall, and the new village has one stone, its own.
 */
public class QaWaystoneLeftover1118aCompatTests implements FabricGameTest {
	private static final String AREA = WaystoneQaCompatTests.AREA;
	private static final BlockPos HALL = WaystoneQaCompatTests.HALL_A;
	private static final BlockPos PLAZA = WaystoneQaCompatTests.PLAZA_A;
	/** The village an earlier test left west of this test's area: its plaza 16 blocks west of this test's, and its hall. */
	private static final BlockPos LEFT_PLAZA = PLAZA.offset(-16, 0, 0);
	private static final BlockPos LEFT_HALL = HALL.offset(-16, 0, 0);

	/**
	 * The waystones the Waystones mod lists round this test whose name starts with {@code name}, as "name@spot" (round
	 * this test: the test world is kept between local runs, and with it the list an earlier run left).
	 */
	private static List<String> listed(GameTestHelper helper, String name) {
		return WaystoneManagerImpl.get(helper.getLevel().getServer()).getWaystones().filter(w -> w.getName().getString().startsWith(name)
				&& helper.getBounds().inflate(VillageHalls.RADIUS + 32).contains(net.minecraft.world.phys.Vec3.atCenterOf(w.getPos())))
			.map(w -> w.getName().getString() + "@" + w.getPos().toShortString()).toList();
	}

	private static void breakStone(GameTestHelper helper, BlockPos plaza) {
		ServerLevel level = helper.getLevel();
		BlockPos stone = WaystoneQaCompatTests.stone(helper, plaza);
		if (VillageWaystones.isStone(level.getBlockState(stone))) {
			level.setBlock(stone.above(), Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(stone, Blocks.AIR.defaultBlockState(), 3);
		}
	}

	//$ gametest_ticks_batch AREA '200' '"qaWaystoneLeftover1118a"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaWaystoneLeftover1118a")
	public void aPlazaAnEarlierTestLeftIsClearedWithItsHallNotTakenOverByTheNextTestsVillage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<BlockPos> halls = List.of(HALL, LEFT_HALL);
		List<BlockPos> plazas = List.of(PLAZA, LEFT_PLAZA);
		// the ground west of this area may be in chunks no test keeps loaded
		List<ChunkPos> forced = new ArrayList<>();
		BlockPos from = helper.absolutePos(LEFT_HALL);
		BlockPos to = helper.absolutePos(LEFT_PLAZA.offset(7, 0, 7));
		for (int cx = from.getX() >> 4; cx <= to.getX() >> 4; cx++) {
			for (int cz = from.getZ() >> 4; cz <= to.getZ() >> 4; cz++) {
				if (level.setChunkForced(cx, cz, true)) {
					forced.add(new ChunkPos(cx, cz));
				}
				level.getChunk(cx, cz);
			}
		}
		PartnerShowsCompatTests.after(helper, () -> {
			WaystoneQaCompatTests.cleanUp(helper, halls, plazas);
			plazas.forEach(p -> breakStone(helper, p));
			halls.forEach(h -> level.removeBlock(helper.absolutePos(h), false));
			forced.forEach(c -> level.setChunkForced(c.x, c.z, false));
		});
		RaidHelperCompatTests.clearAround(helper);
		VillageHalls.forgetNearest();
		WaystoneQaCompatTests.cleanUp(helper, halls, plazas);
		VillageWaystones.Data.get(level).halls().forEach(VillageWaystones.Data.get(level)::forget);

		// An earlier test's village, left as WaystonesCompatTests leaves its own: hall, plaza and a working stone.
		WaystoneQaCompatTests.hall(helper, LEFT_HALL, "Newbury", new UUID(0x1118aL, 0x1L));
		WaystoneQaCompatTests.plaza(helper, LEFT_PLAZA);
		BlockPos leftHall = helper.absolutePos(LEFT_HALL);
		VillageWaystones.round(level, leftHall);
		BlockPos left = WaystoneQaCompatTests.stone(helper, LEFT_PLAZA);
		BlockPos own = WaystoneQaCompatTests.stone(helper, PLAZA);
		helper.assertTrue(WaystoneQaCompatTests.nameAt(helper, LEFT_PLAZA).equals(Optional.of("Newbury")) && left.equals(own.west(16)),
			"setup: the earlier village's stone at " + left.toShortString() + " is " + WaystoneQaCompatTests.nameAt(helper, LEFT_PLAZA));

		// The next test's start as it was: the left hall cleared, its plaza not, then the test's own hall, named. With no
		// plaza of its own yet, the new village takes the left one over (B1188) and its stone takes the village's name;
		// the test's own plaza then makes two stones of the village in the Waystones mod's list, as B1375 saw them.
		level.removeBlock(leftHall, false);
		VillageHalls.forgetNearest();
		WaystoneQaCompatTests.hall(helper, HALL, "Ravensholt", new UUID(0x1118aL, 0x2L));
		BlockPos hallAt = helper.absolutePos(HALL);
		WaystoneQaCompatTests.plaza(helper, PLAZA);
		VillageWaystones.round(level, hallAt);
		helper.assertTrue(Set.copyOf(listed(helper, "Ravensholt")).equals(Set.of("Ravensholt@" + left.toShortString(), "Ravensholt@" + own.toShortString())),
			"a new village with no plaza should take over the plaza left by a village whose hall is gone (B1188), and name its stone: the Waystones mod lists "
				+ listed(helper, "Ravensholt") + ", the left stone is " + WaystoneQaCompatTests.nameAt(helper, LEFT_PLAZA));

		// The same start as it is now: clearAround takes the left plaza away with the hall. The village has one stone.
		breakStone(helper, PLAZA);
		level.removeBlock(hallAt, false);
		WaystoneQaCompatTests.cleanUp(helper, List.of(HALL), List.of(PLAZA));
		RaidHelperCompatTests.clearAround(helper);
		VillageHalls.forgetNearest();
		helper.assertTrue(level.getBlockState(left).isAir() && level.getBlockState(left.above()).isAir(), "the left plaza's stone still stands: " + level.getBlockState(left));
		helper.assertTrue(listed(helper, "Ravensholt").isEmpty() && listed(helper, "Newbury").isEmpty(), "the left stone is still on the Waystones mod's list: "
			+ listed(helper, "Ravensholt") + listed(helper, "Newbury"));
		helper.assertTrue(BuildSiteManager.get(level).finishedIn(level).stream().noneMatch(f -> VillageWaystones.isPlaza(f.structure())
			&& f.placement().origin().equals(helper.absolutePos(LEFT_PLAZA))), "the left plaza is still a finished build");
		WaystoneQaCompatTests.hall(helper, HALL, "Ravensholt", new UUID(0x1118aL, 0x2L));
		WaystoneQaCompatTests.plaza(helper, PLAZA);
		VillageWaystones.round(level, hallAt);
		helper.assertTrue(listed(helper, "Ravensholt").equals(List.of("Ravensholt@" + own.toShortString())),
			"the Waystones mod should list one stone of the village, its own at " + own.toShortString() + ", lists " + listed(helper, "Ravensholt"));
		helper.assertTrue(VillageWaystones.stones(level, hallAt).equals(List.of(own)), "the village's stones: " + VillageWaystones.stones(level, hallAt));
		helper.succeed();
	}
}
