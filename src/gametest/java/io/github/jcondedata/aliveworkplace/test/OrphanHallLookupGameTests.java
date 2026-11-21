package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.RaidDens;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import io.github.jcondedata.aliveworkplace.work.Reach;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.block.Blocks;

/**
 * B1356 (B1266/B427 kin): a Village Hall point of interest left with no hall block under it (a region edit or a mod
 * swapping the block without an update) is no village. Three lookups took it for one: {@link Reach#village}'s square
 * search past the radius, {@link RaidDens#hallNear} and {@link VillageFire#hall}'s chunk cache (which also kept a hall
 * swapped away as the chunk's village until the cache ran out). Each now skips a point in a loaded chunk with no hall
 * standing ({@link VillageHalls#standsAt}), as {@link VillageHalls#nearest} does; a real hall there still counts.
 *
 * <p>Hall radius 12 for the test. The orphan point at (26,2,15) on air; a real hall, where one is wanted, at (4,2,15),
 * 22 blocks west, so a spot by the orphan point is outside its radius.
 */
public class OrphanHallLookupGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos A_AT = new BlockPos(4, 2, 15);
	private static final BlockPos ORPHAN_AT = new BlockPos(26, 2, 15);

	/** A work block next to the orphan point is in no village (Reach's square search); next to a real hall it is. */
	//$ gametest_ticks_batch AREA '200' '"orphanHallLookupReach"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "orphanHallLookupReach")
	public void aWorkBlockNearAHallPointWithNoHallIsInNoVillage(GameTestHelper helper) {
		ServerLevel level = alone(helper);
		BlockPos orphan = orphan(helper);
		BlockPos work = orphan.offset(2, 0, 0);
		Optional<BlockPos> found = Reach.village(level, work);
		helper.assertTrue(found.isEmpty(), "a work block next to a hall point with no hall under it is in the village of " + found.orElse(null));

		helper.setBlock(A_AT, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		BlockPos hall = helper.absolutePos(A_AT);
		helper.assertTrue(helper.getBlockEntity(A_AT) instanceof VillageHallBlockEntity, "the setup: hall A has no block entity");
		helper.assertTrue(Reach.village(level, hall.offset(2, 0, 0)).filter(hall::equals).isPresent(), "a work block next to a real hall isn't in its village");
		helper.assertTrue(Reach.village(level, work).isEmpty(), "with a real hall elsewhere, a work block by the orphan point is in a village");

		// a real hall on the orphan's spot is a village again
		helper.setBlock(ORPHAN_AT, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		helper.assertTrue(Reach.village(level, work).filter(orphan::equals).isPresent(), "a work block next to a real hall on that spot isn't in its village");
		helper.succeed();
	}

	/** A raid den next to the orphan point doesn't take it for its hall; with a real hall there it does. */
	//$ gametest_ticks_batch AREA '200' '"orphanHallLookupDens"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "orphanHallLookupDens")
	public void aRaidDenNearAHallPointWithNoHallHasNoHall(GameTestHelper helper) {
		ServerLevel level = alone(helper);
		BlockPos orphan = orphan(helper);
		BlockPos den = orphan.offset(3, 0, 0);
		Optional<BlockPos> found = RaidDens.hallNear(level, den);
		// (a hall of another batch more than 64 blocks off may be found within the dens' reach; the orphan point never)
		helper.assertFalse(found.filter(orphan::equals).isPresent(), "a raid den's nearest hall is a hall point with no hall under it");

		helper.setBlock(ORPHAN_AT, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		helper.assertTrue(RaidDens.hallNear(level, den).filter(orphan::equals).isPresent(), "a raid den next to a real hall has no hall near");
		helper.succeed();
	}

	/**
	 * A fire next to the orphan point is in no village; a real hall there makes it one; that hall swapped away (its point
	 * left behind) is no village again at once, not when the chunk's cached answer runs out.
	 */
	//$ gametest_ticks_batch AREA '200' '"orphanHallLookupFire"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "orphanHallLookupFire")
	public void aFireNearAHallPointWithNoHallIsInNoVillage(GameTestHelper helper) {
		ServerLevel level = alone(helper);
		VillageFire.forgetHalls();
		Leftovers.after(helper, VillageFire::forgetHalls);
		BlockPos orphan = orphan(helper);
		BlockPos fire = orphan.offset(1, 0, 1);
		Optional<BlockPos> found = VillageFire.hall(level, fire);
		helper.assertTrue(found.isEmpty(), "a fire next to a hall point with no hall under it is in the village of " + found.orElse(null));

		helper.setBlock(ORPHAN_AT, ModBlocks.VILLAGE_HALL);
		VillageFire.forgetHalls();
		helper.assertTrue(VillageFire.hall(level, fire).filter(orphan::equals).isPresent(), "a fire next to a real hall isn't in its village");

		// the hall swapped for air, its point put back as a region edit leaves it; the chunk's answer is still cached
		helper.setBlock(ORPHAN_AT, Blocks.AIR);
		orphan(helper);
		found = VillageFire.hall(level, fire);
		helper.assertTrue(found.isEmpty(), "a fire by a hall swapped away is still in the village of " + found.orElse(null));
		helper.succeed();
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	/** Radius 12, nothing left by other batches (entities, records, halls and hall points within 64 blocks); put back after. */
	private static ServerLevel alone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 12;
		BlockPos centre = helper.absolutePos(new BlockPos(15, 2, 15));
		for (BlockPos old : level.getPoiManager().findAll(h -> h.is(ModVillagers.VILLAGE_HALL_POI), p -> true, centre, 64, PoiManager.Occupancy.ANY).toList()) {
			level.removeBlock(old, false);
			if (level.getPoiManager().getType(old).filter(h -> h.is(ModVillagers.VILLAGE_HALL_POI)).isPresent()) {
				level.getPoiManager().remove(old);
			}
		}
		VillageHalls.forgetNearest();
		Leftovers.after(helper, () -> {
			BlockPos orphan = helper.absolutePos(ORPHAN_AT);
			if (!(level.getBlockEntity(orphan) instanceof VillageHallBlockEntity)
				&& level.getPoiManager().getType(orphan).filter(h -> h.is(ModVillagers.VILLAGE_HALL_POI)).isPresent()) {
				level.getPoiManager().remove(orphan);
			}
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
		});
		return level;
	}

	/** A Village Hall point of interest on air at {@link #ORPHAN_AT}, in a loaded chunk, as a region edit leaves one. */
	private static BlockPos orphan(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos orphan = helper.absolutePos(ORPHAN_AT);
		helper.assertTrue(level.getBlockState(orphan).isAir(), "the setup: the orphan point's block isn't air");
		helper.assertTrue(level.getChunkSource().getChunkNow(orphan.getX() >> 4, orphan.getZ() >> 4) != null, "the setup: the orphan point's chunk isn't loaded");
		if (level.getPoiManager().getType(orphan).isEmpty()) {
			level.getPoiManager().add(orphan, BuiltInRegistries.POINT_OF_INTEREST_TYPE.getHolderOrThrow(ModVillagers.VILLAGE_HALL_POI));
		}
		VillageHalls.forgetNearest();
		helper.assertTrue(level.getPoiManager().getType(orphan).filter(h -> h.is(ModVillagers.VILLAGE_HALL_POI)).isPresent(), "the setup: the orphan point didn't go in");
		return orphan;
	}
}
