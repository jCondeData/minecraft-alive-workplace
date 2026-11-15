package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.guard.Warnings;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.work.Reach;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (2026-11-15, qa-lane-c-1115-0352), B805 from its spec and the Village Hall wiki ("a painted cell whose middle is
 * nearer another hall (one placed after it was painted)" is no longer the painter's): what
 * {@link QaVillageReach1102BugTests} (the wall inside the later hall's radius) and {@link QaVillageReachGameTests} don't
 * cover: an exact tie and one block either side of it, from both sides; a cell nearer the later hall but past its radius;
 * the later hall broken (and put back); towers and the rank beside walls; and the plans and records through a save.
 *
 * <p>Scaled down like the other zone tests: hall radius 12, so each plan's grid is ±16 blocks with cells of one block
 * (a cell's middle is its block). Hall A at (4,2,15), hall B at (26,2,15): 22 blocks apart on one row. Only
 * finished-building records matter (nothing is built). Each test is its own batch (the radius is a static).
 */
public class QaZoneNearerHall1115cGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos A_AT = new BlockPos(4, 2, 15);
	private static final BlockPos B_AT = new BlockPos(26, 2, 15);

	/**
	 * A cell exactly as far from both halls stays the painter's (neither hall is nearer); one block toward the later hall
	 * it isn't A's any more, one block toward A it still is. All three are past both radii.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaZoneNearerHall1115cTie"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaZoneNearerHall1115cTie")
	public void aTiedCellStaysThePaintersAndOneBlockEitherWayDecides(GameTestHelper helper) {
		World w = world(helper);
		Village a = w.village(A_AT);
		// offsets from A: (10,10) is 14.1 from A and 15.6 from B; (11,10) 14.9 from both; (12,10) 15.6 from A, 14.1 from B
		a.paint(10, 12, 10, 10);
		Village b = w.village(B_AT);
		BlockPos nearA = a.hall.offset(10, 0, 10);
		BlockPos tie = a.hall.offset(11, 0, 10);
		BlockPos nearB = a.hall.offset(12, 0, 10);
		helper.assertTrue(tie.distSqr(a.hall) == tie.distSqr(b.hall), "the setup: the tied cell isn't as far from both halls");
		for (BlockPos p : List.of(nearA, tie, nearB)) {
			helper.assertFalse(inRadius(a.hall, p) || inRadius(b.hall, p), "the setup: " + p + " is inside a hall's radius");
		}

		helper.assertTrue(Reach.claims(w.level, a.hall, tie), "a painted cell exactly as far from the later hall as from its own isn't the painter's any more");
		helper.assertFalse(Reach.claims(w.level, b.hall, tie), "a cell only village A painted, as far from both halls, is village B's");
		helper.assertTrue(Reach.claims(w.level, a.hall, nearA), "a painted cell one block nearer its own hall than the tie isn't the painter's");
		helper.assertFalse(Reach.claims(w.level, a.hall, nearB), "a painted cell one block nearer the later hall than the tie is still the painter's");
		helper.succeed();
	}

	/** The same from the other side: B paints first, A is placed later; the tie stays B's, one block toward A it is B's no longer. */
	//$ gametest_ticks_batch AREA '200' '"qaZoneNearerHall1115cTieOther"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaZoneNearerHall1115cTieOther")
	public void theTieHoldsFromTheOtherSideToo(GameTestHelper helper) {
		World w = world(helper);
		Village b = w.village(B_AT);
		// offsets from B: (-10,-10) nearer B, (-11,-10) the tie, (-12,-10) nearer A (north of the row this time)
		b.paint(-12, -10, -10, -10);
		Village a = w.village(A_AT);
		BlockPos nearB = b.hall.offset(-10, 0, -10);
		BlockPos tie = b.hall.offset(-11, 0, -10);
		BlockPos nearA = b.hall.offset(-12, 0, -10);
		helper.assertTrue(tie.distSqr(a.hall) == tie.distSqr(b.hall), "the setup: the tied cell isn't as far from both halls");

		helper.assertTrue(Reach.claims(w.level, b.hall, tie), "B's painted cell as far from the later hall A as from B isn't B's any more");
		helper.assertFalse(Reach.claims(w.level, a.hall, tie), "a cell only village B painted, as far from both halls, is village A's");
		helper.assertTrue(Reach.claims(w.level, b.hall, nearB), "B's painted cell one block nearer B than the tie isn't B's");
		helper.assertFalse(Reach.claims(w.level, b.hall, nearA), "B's painted cell one block nearer the later hall A is still B's");

		// a Stone Wall on the tie is B's only
		int aBefore = VillageRanks.score(w.level, a.hall, 0).buildings();
		w.record(StarterBlueprints.STONE_WALL, tie, Rotation.NONE, a, b);
		helper.assertTrue(Sieges.walled(w.level, b.hall), "a Stone Wall on B's tied cell doesn't make village B walled");
		helper.assertFalse(Sieges.walled(w.level, a.hall), "a Stone Wall on a cell only B painted makes village A walled");
		helper.assertTrue(VillageRanks.score(w.level, a.hall, 0).buildings() == aBefore, "B's tied-cell wall counted for village A's rank");
		helper.succeed();
	}

	/**
	 * A cell A painted that is nearer B but past B's radius: a Stone Wall there makes neither village walled (it isn't A's,
	 * and B never painted it); once B paints that cell itself, the wall is B's only.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaZoneNearerHall1115cPastB"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaZoneNearerHall1115cPastB")
	public void aCellNearerTheLaterHallButPastItsRadiusIsNotThePaintersAndBecomesTheLaterHallsWhenItPaintsIt(GameTestHelper helper) {
		World w = world(helper);
		Village a = w.village(A_AT);
		a.paint(13, 15, 9, 11);
		Village b = w.village(B_AT);
		BlockPos middle = a.hall.offset(14, 0, 10); // 17.2 from A, 12.8 from B
		helper.assertFalse(inRadius(b.hall, middle), "the setup: the spot is inside B's radius");
		helper.assertTrue(CityPlan.nearerAnotherHall(w.level, a.hall, CityPlan.cellAt(a.hall, middle)), "the setup: the cell isn't nearer hall B");
		int aBefore = VillageRanks.score(w.level, a.hall, 0).buildings();
		int bBefore = VillageRanks.score(w.level, b.hall, 0).buildings();
		BlueprintData.Placement wall = w.record(StarterBlueprints.STONE_WALL, middle, Rotation.CLOCKWISE_180, a, b);

		helper.assertFalse(Sieges.walled(w.level, a.hall), "a Stone Wall on A's painted cell that is nearer the later hall B still makes village A walled");
		helper.assertTrue(VillageRanks.score(w.level, a.hall, 0).buildings() == aBefore, "that wall still counted for village A's rank");
		helper.assertFalse(Sieges.walled(w.level, b.hall), "a Stone Wall past B's radius on a cell B never painted makes village B walled");

		// B paints the cell under the wall's middle into its own zone
		b.paint(middle.getX() - b.hall.getX(), middle.getX() - b.hall.getX(), middle.getZ() - b.hall.getZ(), middle.getZ() - b.hall.getZ());
		helper.assertTrue(Sieges.walled(w.level, b.hall), "B painted the cell nearer it, the Stone Wall there doesn't make village B walled");
		helper.assertTrue(VillageRanks.score(w.level, b.hall, 0).buildings() == bBefore + 1, "the wall on B's own cell didn't count for village B's rank");
		helper.assertFalse(Sieges.walled(w.level, a.hall), "B painted the cell, the wall makes village A walled too");
		helper.assertTrue(w.manager.finishedInVillage(w.level, b.hall).stream().anyMatch(f -> f.placement().equals(wall)), "the wall isn't among B's buildings");
		helper.assertTrue(w.manager.finishedInVillage(w.level, a.hall).stream().noneMatch(f -> f.placement().equals(wall)), "the wall is among A's buildings");
		helper.succeed();
	}

	/** Hall B broken: no hall is nearer A's painted cell, so the wall there is A's again; B put back, it is B's only again. */
	//$ gametest_ticks_batch AREA '200' '"qaZoneNearerHall1115cBroken"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaZoneNearerHall1115cBroken")
	public void theLaterHallBrokenGivesTheCellBackAndPutBackTakesItAgain(GameTestHelper helper) {
		World w = world(helper);
		Village a = w.village(A_AT);
		a.paint(13, 15, -1, 1);
		Village b = w.village(B_AT);
		BlockPos middle = a.hall.offset(14, 0, 0); // 14 from A, 8 from B: inside B's radius
		w.record(StarterBlueprints.STONE_WALL, middle, Rotation.CLOCKWISE_180, a, b);
		int aWith = VillageRanks.score(w.level, a.hall, 0).buildings();
		helper.assertTrue(Sieges.walled(w.level, b.hall), "the setup: the Stone Wall inside B's radius doesn't make B walled");
		helper.assertFalse(Sieges.walled(w.level, a.hall), "the setup: the Stone Wall in the cell nearer B makes A walled too");

		helper.setBlock(B_AT, Blocks.AIR);
		VillageHalls.forgetNearest();
		helper.assertFalse(CityPlan.nearerAnotherHall(w.level, a.hall, CityPlan.cellAt(a.hall, middle)), "hall B broken, the cell is still nearer another hall");
		helper.assertTrue(Reach.claims(w.level, a.hall, middle), "hall B broken, A's painted cell isn't A's again");
		helper.assertTrue(Sieges.walled(w.level, a.hall), "hall B broken, the Stone Wall on A's painted cell doesn't make village A walled");
		helper.assertTrue(VillageRanks.score(w.level, a.hall, 0).buildings() == aWith + 1,
			"hall B broken, the wall on A's cell didn't count for A's rank: " + aWith + " -> " + VillageRanks.score(w.level, a.hall, 0).buildings());

		helper.setBlock(B_AT, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		helper.assertFalse(Sieges.walled(w.level, a.hall), "hall B put back, the Stone Wall in the cell nearer it makes village A walled again");
		helper.assertTrue(Sieges.walled(w.level, b.hall), "hall B put back, the Stone Wall inside its radius doesn't make it walled");
		helper.assertTrue(VillageRanks.score(w.level, a.hall, 0).buildings() == aWith, "hall B put back, the wall still counts for A's rank");
		helper.succeed();
	}

	/** Not only walls: a Wall Tower on A's cell nearer B (inside B's radius) is B's tower and counts for B's rank, never A's. */
	//$ gametest_ticks_batch AREA '200' '"qaZoneNearerHall1115cTower"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaZoneNearerHall1115cTower")
	public void aTowerOnTheCellIsTheNearerVillagesTowerAndRankOnly(GameTestHelper helper) {
		World w = world(helper);
		Village a = w.village(A_AT);
		a.paint(12, 15, -16, -6);
		Village b = w.village(B_AT);
		BlockPos middle = a.hall.offset(15, 0, -7); // 16.6 from A, 9.9 from B
		helper.assertTrue(inRadius(b.hall, middle), "the setup: the spot isn't inside B's radius");
		int aBefore = VillageRanks.score(w.level, a.hall, 0).buildings();
		int bBefore = VillageRanks.score(w.level, b.hall, 0).buildings();
		w.record(StarterBlueprints.WALL_TOWER, middle, Rotation.NONE, a, b);

		helper.assertTrue(Warnings.towers(w.level, b.hall).size() == 1, "a Wall Tower inside B's radius isn't B's tower: " + Warnings.towers(w.level, b.hall));
		helper.assertTrue(Warnings.towers(w.level, a.hall).isEmpty(), "a Wall Tower on A's cell nearer hall B is also A's tower: " + Warnings.towers(w.level, a.hall));
		helper.assertTrue(VillageRanks.score(w.level, b.hall, 0).buildings() == bBefore + 1, "the tower didn't count for B's rank");
		helper.assertTrue(VillageRanks.score(w.level, a.hall, 0).buildings() == aBefore,
			"the tower on the cell nearer B counted for A's rank too: " + aBefore + " -> " + VillageRanks.score(w.level, a.hall, 0).buildings());
		helper.succeed();
	}

	/** Both halls' plans and the finished records through a save and a load: the wall on A's cell nearer B is still B's only. */
	//$ gametest_ticks_batch AREA '200' '"qaZoneNearerHall1115cSaved"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaZoneNearerHall1115cSaved")
	public void theNearerHallsClaimSurvivesASaveAndLoad(GameTestHelper helper) {
		World w = world(helper);
		Village a = w.village(A_AT);
		a.paint(13, 15, -1, 1);
		// a cell of A's own zone that no hall is nearer (west of A) to prove the plan really came back
		a.paint(-15, -13, -1, 1);
		Village b = w.village(B_AT);
		BlueprintData.Placement shared = w.record(StarterBlueprints.STONE_WALL, a.hall.offset(14, 0, 0), Rotation.CLOCKWISE_180, a, b);
		BlueprintData.Placement own = w.record(StarterBlueprints.WALL_TOWER, a.hall.offset(-14, 0, 0), Rotation.NONE, null, null);
		helper.assertTrue(Reach.claims(w.level, a.hall, a.hall.offset(-14, 0, 0)), "the setup: A's own western cell isn't A's");

		CompoundTag aTag = a.entity.saveWithoutMetadata(w.level.registryAccess());
		CompoundTag bTag = b.entity.saveWithoutMetadata(w.level.registryAccess());
		a.entity.setPlan(CityPlan.EMPTY);
		b.entity.setPlan(CityPlan.EMPTY);
		a.entity.loadWithComponents(aTag, w.level.registryAccess());
		b.entity.loadWithComponents(bTag, w.level.registryAccess());
		helper.assertTrue(a.entity.plan().zones().size() == 1, "A's plan didn't come back from its save: " + a.entity.plan().zones());
		helper.assertTrue(a.entity.plan().zones().get(0).has(CityPlan.cellAt(a.hall, a.hall.offset(14, 0, 0))),
			"A's painted cell nearer B was lost in the save (the claim is decided when it's read, the cell stays painted)");

		BuildSiteManager loaded = BuildSiteManager.load(w.manager.save(new CompoundTag(), w.level.registryAccess()), w.level.registryAccess());
		List<BuildSiteManager.Finished> ofA = loaded.finishedInVillage(w.level, a.hall);
		List<BuildSiteManager.Finished> ofB = loaded.finishedInVillage(w.level, b.hall);
		helper.assertTrue(ofA.stream().noneMatch(f -> f.placement().equals(shared)), "after a save and load the wall on the cell nearer B is A's again: " + names(ofA));
		helper.assertTrue(ofA.stream().anyMatch(f -> f.placement().equals(own)), "after a save and load the tower in A's own zone isn't A's: " + names(ofA));
		helper.assertTrue(ofB.stream().anyMatch(f -> f.placement().equals(shared)), "after a save and load the wall inside B's radius isn't B's: " + names(ofB));
		helper.assertTrue(Sieges.walled(w.level, b.hall), "after the plans' save and load the wall inside B's radius doesn't make B walled");
		helper.succeed();
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	private static boolean inRadius(BlockPos hall, BlockPos p) {
		long dx = p.getX() - hall.getX();
		long dz = p.getZ() - hall.getZ();
		return dx * dx + dz * dz <= (long) VillageHalls.RADIUS * VillageHalls.RADIUS;
	}

	private static List<String> names(List<BuildSiteManager.Finished> list) {
		return list.stream().map(f -> f.structure().getPath() + "@" + f.placement().origin().toShortString()).toList();
	}

	/** Radius 12, nothing left by other batches (entities, records, halls within 64 blocks); all put back after the test. */
	private static World world(GameTestHelper helper) {
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
		World w = new World(helper, level, BuildSiteManager.get(level), new ArrayList<>());
		Leftovers.after(helper, () -> {
			w.recorded.forEach(p -> BuildSiteManager.get(level).forgetFinished(p));
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
		});
		return w;
	}

	private record World(GameTestHelper helper, ServerLevel level, BuildSiteManager manager, List<BlueprintData.Placement> recorded) {
		Village village(BlockPos at) {
			helper.setBlock(at, ModBlocks.VILLAGE_HALL);
			VillageHalls.forgetNearest();
			helper.assertTrue(CityPlan.half() == 16, "the plan's grid reaches " + CityPlan.half() + " blocks from the hall, not 16");
			VillageHallBlockEntity entity = helper.getBlockEntity(at);
			entity.setPlan(CityPlan.EMPTY);
			return new Village(helper, helper.absolutePos(at), entity);
		}

		/**
		 * A finished {@code entry} whose middle is at {@code middle}; its corner, checked, is claimed by neither
		 * {@code a} nor {@code b} (when given), so only the middle decides.
		 */
		BlueprintData.Placement record(StarterBlueprints.Entry entry, BlockPos middle, Rotation rotation, Village a, Village b) {
			ResourceLocation id = entry.id();
			BlockPos first = Builders.spot(level, id, new BlueprintData.Placement(level.dimension().location(), middle, rotation, Mirror.NONE));
			BlockPos origin = middle.offset(middle.getX() - first.getX(), 0, middle.getZ() - first.getZ());
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, rotation, Mirror.NONE);
			BlockPos spot = Builders.spot(level, id, placement);
			helper.assertTrue(spot.getX() == middle.getX() && spot.getZ() == middle.getZ(), "the setup: " + id + "'s middle is " + spot + ", not " + middle);
			if (a != null) {
				helper.assertFalse(Reach.claims(level, a.hall, origin), "the setup: " + id + "'s corner " + origin + " is in village A");
			}
			if (b != null && !inRadius(b.hall, middle)) {
				helper.assertFalse(Reach.claims(level, b.hall, origin), "the setup: " + id + "'s corner " + origin + " is in village B");
			}
			manager.recordFinished(id, placement, UUID.nameUUIDFromBytes(("qa-zone-nearer-1115c-" + id.getPath() + "@" + middle.toShortString())
				.getBytes(StandardCharsets.UTF_8)));
			recorded.add(placement);
			return placement;
		}
	}

	private record Village(GameTestHelper helper, BlockPos hall, VillageHallBlockEntity entity) {
		/** A Homes zone (the plan's first) painted over the offsets dx0..dx1, dz0..dz1 from the hall, bypassing the paint check. */
		void paint(int dx0, int dx1, int dz0, int dz1) {
			BitSet cells = new BitSet();
			for (int dx = dx0; dx <= dx1; dx++) {
				for (int dz = dz0; dz <= dz1; dz++) {
					int cell = CityPlan.cellAt(hall, hall.offset(dx, 0, dz));
					helper.assertTrue(cell >= 0, "the setup: offset " + dx + "," + dz + " is off the plan's grid");
					cells.set(cell);
				}
			}
			CityPlan plan = entity.plan().zones().isEmpty() ? entity.plan().addZone("homes", "Homes 1", "") : entity.plan();
			entity.setPlan(plan.paint(0, cells));
		}
	}
}
