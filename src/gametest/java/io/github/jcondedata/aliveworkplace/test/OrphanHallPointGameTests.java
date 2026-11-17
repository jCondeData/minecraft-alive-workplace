package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.work.Reach;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * B1266 (B427 kin): a Village Hall point of interest left with no hall block under it (a region edit or a mod swapping
 * the block without an update) is no village. {@link CityPlan#nearerAnotherHall} counted it as another hall, so near
 * such an orphan point City Plan cells were refused when painted ("another village") and, since B805, cells painted
 * there stopped counting for the village. A point in a loaded chunk with no hall standing is ignored, as
 * {@link VillageHalls#nearest} does; a real hall there still takes the cell.
 *
 * <p>Scaled down like the other zone tests: hall radius 12, so the plan's grid is ±16 blocks with cells of one block.
 * Hall A at (4,2,15); the orphan point at (26,2,15), on air: a cell 14 blocks east of A is 8 from the point.
 */
public class OrphanHallPointGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos A_AT = new BlockPos(4, 2, 15);
	private static final BlockPos ORPHAN_AT = new BlockPos(26, 2, 15);

	/** The player paints cells nearer the orphan point than their hall: all go on; with a real hall there they're refused. */
	//$ gametest_ticks_batch AREA '200' '"orphanHallPointPaint"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "orphanHallPointPaint")
	public void cellsNearAHallPointWithNoHallCanBePainted(GameTestHelper helper) {
		ServerLevel level = alone(helper);
		VillageHallBlockEntity a = hall(helper, A_AT);
		BlockPos hall = a.getBlockPos();
		BlockPos orphan = orphan(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(CityPlans.apply(player, QaCityPlanGameTests.sent(level, CityPlans.Edit.addZone(hall, "homes", "Homes", ""))), "no zone added");
		BitSet stroke = cells(hall, 13, 15, -1, 1);
		helper.assertTrue(stroke.stream().allMatch(c -> CityPlan.cellCentre(hall, c).distSqr(orphan) < CityPlan.cellCentre(hall, c).distSqr(hall)),
			"the setup: a cell of the stroke isn't nearer the orphan point than hall A");

		helper.assertFalse(CityPlan.nearerAnotherHall(level, hall, CityPlan.cellAt(hall, hall.offset(14, 0, 0))),
			"a cell nearer a hall point with no hall under it counts as nearer another hall");
		helper.assertTrue(CityPlans.apply(player, QaCityPlanGameTests.sent(level, CityPlans.Edit.paint(hall, 0, stroke))),
			"the stroke near a hall point with no hall under it was refused whole");
		helper.assertTrue(a.plan().zones().get(0).cells().equals(stroke),
			"near a hall point with no hall the plan painted " + a.plan().zones().get(0).cells().cardinality() + " of " + stroke.cardinality() + " cells");

		// a real hall on that spot is another village: the next stroke there is refused
		helper.setBlock(ORPHAN_AT, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		helper.assertTrue(CityPlan.nearerAnotherHall(level, hall, CityPlan.cellAt(hall, hall.offset(14, 0, 3))), "a real hall there isn't another village");
		helper.assertFalse(CityPlans.apply(player, QaCityPlanGameTests.sent(level, CityPlans.Edit.paint(hall, 0, cells(hall, 13, 15, 2, 4)))),
			"a stroke nearer a real hall B was painted");
		helper.succeed();
	}

	/** A cell painted near the orphan point stays the village's: a Stone Wall finished there makes it walled. */
	//$ gametest_ticks_batch AREA '200' '"orphanHallPointCounts"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "orphanHallPointCounts")
	public void aCellPaintedNearAHallPointWithNoHallStillCounts(GameTestHelper helper) {
		ServerLevel level = alone(helper);
		VillageHallBlockEntity a = hall(helper, A_AT);
		BlockPos hall = a.getBlockPos();
		a.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells(hall, 13, 15, -1, 1)));
		orphan(helper);
		BlockPos middle = hall.offset(14, 0, 0);

		helper.assertTrue(Reach.claims(level, hall, middle), "a painted cell nearer a hall point with no hall under it isn't the village's");
		BlueprintData.Placement wall = record(helper, level, middle);
		helper.assertFalse(Reach.claims(level, hall, wall.origin()), "the setup: the wall's corner " + wall.origin() + " is in village A");
		helper.assertTrue(BuildSiteManager.get(level).finishedInVillage(level, hall).stream().anyMatch(f -> f.placement().equals(wall)),
			"a Stone Wall on a cell painted near a hall point with no hall isn't among the village's buildings");
		helper.assertTrue(Sieges.walled(level, hall), "a Stone Wall on a cell painted near a hall point with no hall doesn't make the village walled");
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

	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		helper.assertTrue(CityPlan.half() == 16, "the plan's grid reaches " + CityPlan.half() + " blocks from the hall, not 16");
		VillageHallBlockEntity entity = helper.getBlockEntity(at);
		entity.setPlan(CityPlan.EMPTY);
		return entity;
	}

	/** A Village Hall point of interest on air at {@link #ORPHAN_AT}, in a loaded chunk, as a region edit leaves one. */
	private static BlockPos orphan(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos orphan = helper.absolutePos(ORPHAN_AT);
		helper.assertTrue(level.getBlockState(orphan).isAir(), "the setup: the orphan point's block isn't air");
		helper.assertTrue(level.getChunkSource().getChunkNow(orphan.getX() >> 4, orphan.getZ() >> 4) != null, "the setup: the orphan point's chunk isn't loaded");
		level.getPoiManager().add(orphan, BuiltInRegistries.POINT_OF_INTEREST_TYPE.getHolderOrThrow(ModVillagers.VILLAGE_HALL_POI));
		VillageHalls.forgetNearest();
		helper.assertTrue(level.getPoiManager().getType(orphan).filter(h -> h.is(ModVillagers.VILLAGE_HALL_POI)).isPresent(), "the setup: the orphan point didn't go in");
		return orphan;
	}

	/** The cells at offsets dx0..dx1, dz0..dz1 from the hall. */
	private static BitSet cells(BlockPos hall, int dx0, int dx1, int dz0, int dz1) {
		BitSet out = new BitSet();
		for (int dx = dx0; dx <= dx1; dx++) {
			for (int dz = dz0; dz <= dz1; dz++) {
				int cell = CityPlan.cellAt(hall, hall.offset(dx, 0, dz));
				if (cell < 0) {
					throw new IllegalStateException("offset " + dx + "," + dz + " is off the plan's grid");
				}
				out.set(cell);
			}
		}
		return out;
	}

	/** A finished Stone Wall whose middle is at {@code middle} (forgotten after the test). */
	private static BlueprintData.Placement record(GameTestHelper helper, ServerLevel level, BlockPos middle) {
		ResourceLocation id = StarterBlueprints.STONE_WALL.id();
		BlockPos first = Builders.spot(level, id, new BlueprintData.Placement(level.dimension().location(), middle, Rotation.CLOCKWISE_180, Mirror.NONE));
		BlockPos origin = middle.offset(middle.getX() - first.getX(), 0, middle.getZ() - first.getZ());
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.CLOCKWISE_180, Mirror.NONE);
		BlockPos spot = Builders.spot(level, id, placement);
		helper.assertTrue(spot.getX() == middle.getX() && spot.getZ() == middle.getZ(), "the setup: the wall's middle is " + spot + ", not " + middle);
		BuildSiteManager.get(level).recordFinished(id, placement, UUID.nameUUIDFromBytes(("orphan-hall-point@" + middle.toShortString()).getBytes(StandardCharsets.UTF_8)));
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		return placement;
	}
}
