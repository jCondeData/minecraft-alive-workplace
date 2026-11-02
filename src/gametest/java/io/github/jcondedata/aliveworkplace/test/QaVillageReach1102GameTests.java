package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.guard.Warnings;
import io.github.jcondedata.aliveworkplace.hall.Arenas;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (2026-11-02), B620 from its spec: whatever counts "the village's buildings" uses the claimed area (36.20: the hall's
 * radius or a cell painted into the hall's own City Plan zone). Covers what {@link ZoneVillageBuildingsGameTests} doesn't:
 * the defences (a siege's walls, the watchtowers that warn), the Arena, both sides of the radius's edge, a zone that is
 * another village's, a zone erased or removed after the building finished, a building whose corner (not middle) is in the
 * zone, the saved data and the plan round-tripped through a save, and a building in another dimension.
 *
 * <p>Scaled down like ZoneVillageBuildingsGameTests: hall radius 12, so the plan's grid is ±16 blocks with cells of one
 * block. Only finished-building records matter (nothing is built). Each test is its own batch (the radius is a static).
 */
public class QaVillageReach1102GameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	/** The radius before the test's first village (a test with two villages puts the true one back, not 12). */
	private static final int[] ORIGINAL = {0};
	private static final java.util.Set<GameTestHelper> FIRST_DONE = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
	/** The zone: offsets 9..12 east, 9..13 south of the hall, so each zone build's middle is in it and its corner past it. */
	private static final int[] ZONE = {9, 12, 9, 13};

	/** A Stone Wall and a Lookout Tower in the zone past the radius make the village walled and watched; unpainted, they don't. */
	//$ gametest_ticks_batch AREA '200' '"qaVillageReach1102Defences"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaVillageReach1102Defences")
	public void defencesInAZonePastTheRadiusCountForSiegesAndWarnings(GameTestHelper helper) {
		Village v = village(helper, HALL);
		v.paint(ZONE[0], ZONE[1], ZONE[2], ZONE[3]);
		helper.assertFalse(Sieges.walled(v.level, v.hall), "the setup: a fresh village is walled");
		helper.assertTrue(Warnings.towers(v.level, v.hall).isEmpty(), "the setup: a fresh village has towers");

		// unpainted north-west corner: not the village's
		v.record(StarterBlueprints.STONE_WALL, v.hall.offset(-11, 0, -13), Rotation.NONE, false);
		v.record(StarterBlueprints.LOOKOUT_TOWER, v.hall.offset(-13, 0, -10), Rotation.NONE, false);
		helper.assertFalse(Sieges.walled(v.level, v.hall), "a Stone Wall outside the village (unpainted, 17 blocks off, radius 12) made it walled");
		helper.assertTrue(Warnings.towers(v.level, v.hall).isEmpty(), "a Lookout Tower outside the village is one of its towers: " + Warnings.towers(v.level, v.hall));

		// the painted south-east corner: the village's
		BlockPos tower = v.hall.offset(11, 0, 12);
		v.record(StarterBlueprints.LOOKOUT_TOWER, tower, Rotation.CLOCKWISE_180, true);
		helper.assertFalse(Sieges.walled(v.level, v.hall), "a Lookout Tower alone made the village walled (it isn't a wall or gate)");
		List<BlockPos> towers = Warnings.towers(v.level, v.hall);
		helper.assertTrue(towers.size() == 1, "the Lookout Tower in the village's zone past the radius isn't its one tower: " + towers);
		BlockPos seen = towers.get(0);
		helper.assertTrue(Math.abs(seen.getX() - tower.getX()) <= 1 && Math.abs(seen.getZ() - tower.getZ()) <= 1,
			"the tower's middle is given as " + seen + ", not near " + tower);
		v.record(StarterBlueprints.STONE_WALL, v.hall.offset(11, 0, 13), Rotation.CLOCKWISE_180, true);
		helper.assertTrue(Sieges.walled(v.level, v.hall), "a Stone Wall in the village's zone past the radius doesn't make it walled for a siege");
		helper.succeed();
	}

	/** The radius's edge, unpainted: a middle exactly RADIUS blocks off counts, one a block farther (corner farther still) doesn't. */
	//$ gametest_ticks_batch AREA '200' '"qaVillageReach1102Edge"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaVillageReach1102Edge")
	public void theRadiusEdgeCountsOnlyUpToTheRadius(GameTestHelper helper) {
		Village v = village(helper, HALL);
		int before = VillageRanks.score(v.level, v.hall, 0).buildings();
		v.record(StarterBlueprints.STONE_WALL, v.hall.offset(-13, 0, 0), Rotation.NONE, false);
		helper.assertFalse(Sieges.walled(v.level, v.hall), "a Stone Wall 13 blocks off (radius 12, no zone) counts for the village");
		helper.assertTrue(VillageRanks.score(v.level, v.hall, 0).buildings() == before,
			"a building 13 blocks off (radius 12, no zone) counted for the rank: " + before + " -> " + VillageRanks.score(v.level, v.hall, 0).buildings());
		v.record(StarterBlueprints.STONE_WALL, v.hall.offset(12, 0, 0), Rotation.CLOCKWISE_180, true);
		helper.assertTrue(Sieges.walled(v.level, v.hall), "a Stone Wall whose middle is exactly 12 blocks off (radius 12) doesn't count");
		helper.assertTrue(VillageRanks.score(v.level, v.hall, 0).buildings() == before + 1,
			"the building on the radius's edge didn't count once for the rank: " + before + " -> " + VillageRanks.score(v.level, v.hall, 0).buildings());
		helper.succeed();
	}

	/**
	 * Two villages with the very same plan (same cells, each relative to its own hall): a wall in the east village's zone,
	 * inside the west village's grid but not its zone, is the east village's only.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaVillageReach1102OtherVillage"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaVillageReach1102OtherVillage")
	public void anotherVillagesZoneDoesNotCountForThisVillage(GameTestHelper helper) {
		Village west = village(helper, new BlockPos(4, 2, 15));
		Village east = village(helper, new BlockPos(26, 2, 15));
		west.paint(-10, -6, 11, 15);
		east.paint(-10, -6, 11, 15);
		int westBefore = VillageRanks.score(west.level, west.hall, 0).buildings();
		BlockPos middle = east.hall.offset(-8, 0, 13);
		helper.assertTrue(CityPlan.cellAt(west.hall, middle) >= 0, "the setup: the wall's middle is off the west village's grid");
		helper.assertFalse(Reach.claims(west.level, west.hall, middle), "the setup: the wall's middle is in the west village");
		east.record(StarterBlueprints.STONE_WALL, middle, Rotation.CLOCKWISE_180, true);
		helper.assertFalse(Reach.claims(west.level, west.hall, east.recorded.get(0).origin()), "the setup: the wall's corner is in the west village");
		helper.assertTrue(Sieges.walled(east.level, east.hall), "a Stone Wall in the east village's zone past its radius doesn't make it walled");
		helper.assertFalse(Sieges.walled(west.level, west.hall), "the east village's Stone Wall (in its zone) made the west village walled");
		helper.assertTrue(VillageRanks.score(west.level, west.hall, 0).buildings() == westBefore,
			"the east village's building counted for the west village's rank: " + westBefore + " -> " + VillageRanks.score(west.level, west.hall, 0).buildings());
		helper.succeed();
	}

	/** The zone's cells erased (then painted again), and the zone removed: the building stops counting, then counts again. */
	//$ gametest_ticks_batch AREA '200' '"qaVillageReach1102Erased"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaVillageReach1102Erased")
	public void aZoneErasedOrRemovedAfterwardsStopsTheBuildingCounting(GameTestHelper helper) {
		Village v = village(helper, HALL);
		BitSet cells = v.paint(ZONE[0], ZONE[1], ZONE[2], ZONE[3]);
		v.record(StarterBlueprints.STONE_WALL, v.hall.offset(11, 0, 13), Rotation.CLOCKWISE_180, true);
		helper.assertTrue(Sieges.walled(v.level, v.hall), "a Stone Wall in the village's zone past the radius doesn't make it walled");
		int counted = VillageRanks.score(v.level, v.hall, 0).buildings();

		v.entity.setPlan(v.entity.plan().erase(cells));
		helper.assertFalse(Sieges.walled(v.level, v.hall), "the zone's cells erased, its Stone Wall past the radius still makes the village walled");
		helper.assertTrue(VillageRanks.score(v.level, v.hall, 0).buildings() == counted - 1,
			"the zone's cells erased, its building still counts for the rank: " + counted + " -> " + VillageRanks.score(v.level, v.hall, 0).buildings());

		v.entity.setPlan(v.entity.plan().paint(0, cells));
		helper.assertTrue(Sieges.walled(v.level, v.hall), "the cells painted back into the zone, the Stone Wall doesn't count again");

		v.entity.setPlan(v.entity.plan().removeZone(0));
		helper.assertFalse(Sieges.walled(v.level, v.hall), "the zone removed from the plan, its Stone Wall past the radius still makes the village walled");
		helper.succeed();
	}

	/** A building whose middle is outside the zone but whose corner is in it counts (B620: corner or middle). */
	//$ gametest_ticks_batch AREA '200' '"qaVillageReach1102Corner"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaVillageReach1102Corner")
	public void aBuildingWhoseCornerIsInTheZoneCounts(GameTestHelper helper) {
		Village v = village(helper, HALL);
		// a Wall Tower north-west of the hall turned NONE: its corner is farther out than its middle, both past the radius
		// and on the grid; only the corner's cell is painted
		BlockPos middle = v.hall.offset(-11, 0, -13);
		BlueprintData.Placement p = v.placed(StarterBlueprints.WALL_TOWER, middle, Rotation.NONE);
		BlockPos corner = p.origin();
		helper.assertTrue(CityPlan.cellAt(v.hall, corner) >= 0, "the setup: the tower's corner " + corner + " is off the grid");
		v.paint(corner.getX() - v.hall.getX(), corner.getX() - v.hall.getX(), corner.getZ() - v.hall.getZ(), corner.getZ() - v.hall.getZ());
		helper.assertFalse(Reach.claims(v.level, v.hall, Builders.spot(v.level, StarterBlueprints.WALL_TOWER.id(), p)), "the setup: the tower's middle is in the village");
		helper.assertTrue(Reach.claims(v.level, v.hall, corner), "the setup: the tower's corner isn't in the zone");
		v.manager.recordFinished(StarterBlueprints.WALL_TOWER.id(), p, owner("corner"));
		v.recorded.add(p);
		helper.assertTrue(Warnings.towers(v.level, v.hall).size() == 1,
			"a Wall Tower whose corner is in the village's zone (middle just outside it) isn't its tower: " + Warnings.towers(v.level, v.hall));
		helper.succeed();
	}

	/** An Arena in the zone past the radius is the village's Arena (the Festival Cup's venue); unpainted, it isn't. */
	//$ gametest_ticks_batch AREA '200' '"qaVillageReach1102Arena"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaVillageReach1102Arena")
	public void anArenaInAZonePastTheRadiusIsTheVillagesArena(GameTestHelper helper) {
		Village v = village(helper, HALL);
		helper.assertFalse(Arenas.has(v.level, v.hall), "the setup: a fresh village has an Arena");
		v.record(StarterBlueprints.ARENA, v.hall.offset(-11, 0, -13), Rotation.NONE, false);
		helper.assertFalse(Arenas.has(v.level, v.hall), "an Arena outside the village (unpainted, 17 blocks off, radius 12) is its Arena");
		v.paint(ZONE[0], ZONE[1], ZONE[2], ZONE[3]);
		v.record(StarterBlueprints.ARENA, v.hall.offset(11, 0, 13), Rotation.CLOCKWISE_180, true);
		helper.assertTrue(Arenas.has(v.level, v.hall), "an Arena in the village's zone past the radius isn't its Arena");
		helper.succeed();
	}

	/** The finished buildings and the hall's plan through a save and a load: the zone's wall still counts, the outside one still doesn't. */
	//$ gametest_ticks_batch AREA '200' '"qaVillageReach1102Saved"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaVillageReach1102Saved")
	public void theZoneBuildingStillCountsAfterASaveAndLoad(GameTestHelper helper) {
		Village v = village(helper, HALL);
		v.paint(ZONE[0], ZONE[1], ZONE[2], ZONE[3]);
		v.record(StarterBlueprints.STONE_WALL, v.hall.offset(11, 0, 13), Rotation.CLOCKWISE_180, true);
		v.record(StarterBlueprints.WALL_TOWER, v.hall.offset(-13, 0, -10), Rotation.NONE, false);
		List<BuildSiteManager.Finished> live = v.manager.finishedInVillage(v.level, v.hall);

		// the hall's plan saved and loaded back (after being lost in between)
		CompoundTag hallTag = v.entity.saveWithoutMetadata(v.level.registryAccess());
		v.entity.setPlan(CityPlan.EMPTY);
		helper.assertFalse(Sieges.walled(v.level, v.hall), "the plan cleared, the zone's Stone Wall still counts");
		v.entity.loadWithComponents(hallTag, v.level.registryAccess());
		helper.assertTrue(v.entity.plan().zones().size() == 1, "the hall's plan didn't come back from its save: " + v.entity.plan().zones());
		helper.assertTrue(Sieges.walled(v.level, v.hall), "the hall's plan loaded from its save, the zone's Stone Wall doesn't count");

		// the finished buildings saved and loaded into a fresh manager (the spot cache isn't saved)
		CompoundTag tag = v.manager.save(new CompoundTag(), v.level.registryAccess());
		BuildSiteManager loaded = BuildSiteManager.load(tag, v.level.registryAccess());
		List<BuildSiteManager.Finished> after = loaded.finishedInVillage(v.level, v.hall);
		helper.assertTrue(after.equals(live), "the village's buildings after a save and load: " + names(after) + ", before: " + names(live));
		helper.assertTrue(after.stream().anyMatch(f -> f.structure().equals(StarterBlueprints.STONE_WALL.id())),
			"the zone's Stone Wall isn't the village's after a save and load: " + names(after));
		helper.assertTrue(after.stream().noneMatch(f -> f.structure().equals(StarterBlueprints.WALL_TOWER.id())),
			"the unpainted Wall Tower is the village's after a save and load: " + names(after));
		helper.succeed();
	}

	/** A wall finished in the Nether at the zone's coordinates isn't the overworld village's. */
	//$ gametest_ticks_batch AREA '200' '"qaVillageReach1102Nether"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaVillageReach1102Nether")
	public void aBuildingInAnotherDimensionAtTheZonesSpotDoesNotCount(GameTestHelper helper) {
		Village v = village(helper, HALL);
		v.paint(ZONE[0], ZONE[1], ZONE[2], ZONE[3]);
		BlueprintData.Placement here = v.placed(StarterBlueprints.STONE_WALL, v.hall.offset(11, 0, 13), Rotation.CLOCKWISE_180);
		BlueprintData.Placement nether = new BlueprintData.Placement(Level.NETHER.location(), here.origin(), here.rotation(), here.mirror());
		v.manager.recordFinished(StarterBlueprints.STONE_WALL.id(), nether, owner("nether"));
		v.recorded.add(nether);
		helper.assertFalse(Sieges.walled(v.level, v.hall), "a Stone Wall finished in the Nether at the zone's coordinates makes the overworld village walled");
		helper.assertTrue(v.manager.finishedInVillage(v.level, v.hall).isEmpty(), "a Nether building is the overworld village's: " + names(v.manager.finishedInVillage(v.level, v.hall)));
		helper.succeed();
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	private static UUID owner(String what) {
		return UUID.nameUUIDFromBytes(("qa-village-reach-1102-" + what).getBytes(StandardCharsets.UTF_8));
	}

	private static List<String> names(List<BuildSiteManager.Finished> list) {
		return list.stream().map(f -> f.structure().getPath() + "@" + f.placement().origin().toShortString()).toList();
	}

	/** A hall at {@code at} with radius 12 and an empty plan; the radius and the records are put back after the test. */
	private static Village village(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		boolean first = !FIRST_DONE.contains(helper);
		int radius = first ? VillageHalls.RADIUS : ORIGINAL[0];
		if (first) {
			Leftovers.clear(helper);
			Leftovers.finished(helper);
			ORIGINAL[0] = radius;
			FIRST_DONE.add(helper);
		}
		VillageHalls.RADIUS = 12;
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		helper.assertTrue(CityPlan.half() == 16, "the plan's grid reaches " + CityPlan.half() + " blocks from the hall, not 16");
		VillageHallBlockEntity entity = helper.getBlockEntity(at);
		entity.setPlan(CityPlan.EMPTY);
		Village v = new Village(helper, level, helper.absolutePos(at), entity, BuildSiteManager.get(level), new ArrayList<>());
		Leftovers.after(helper, () -> {
			v.recorded.forEach(p -> BuildSiteManager.get(level).forgetFinished(p));
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
			FIRST_DONE.remove(helper);
		});
		return v;
	}

	private record Village(GameTestHelper helper, ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, BuildSiteManager manager,
			List<BlueprintData.Placement> recorded) {
		/** A Homes zone (the plan's first) painted over the offsets dx0..dx1, dz0..dz1 from the hall; the cells painted. */
		BitSet paint(int dx0, int dx1, int dz0, int dz1) {
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
			return cells;
		}

		/** The placement of {@code entry} turned {@code rotation} whose outline's middle is at {@code middle}. */
		BlueprintData.Placement placed(StarterBlueprints.Entry entry, BlockPos middle, Rotation rotation) {
			ResourceLocation id = entry.id();
			BlockPos first = Builders.spot(level, id, new BlueprintData.Placement(level.dimension().location(), middle, rotation, Mirror.NONE));
			BlockPos origin = middle.offset(middle.getX() - first.getX(), 0, middle.getZ() - first.getZ());
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, rotation, Mirror.NONE);
			BlockPos spot = Builders.spot(level, id, placement);
			helper.assertTrue(spot.getX() == middle.getX() && spot.getZ() == middle.getZ(), "the setup: " + id + "'s middle is " + spot + ", not " + middle);
			return placement;
		}

		/**
		 * A finished {@code entry} whose middle is at {@code middle}, checked to be (or not be) in the claimed area by its
		 * middle, with its corner (farther out, by the turn) never in it.
		 */
		void record(StarterBlueprints.Entry entry, BlockPos middle, Rotation rotation, boolean claimed) {
			BlueprintData.Placement placement = placed(entry, middle, rotation);
			BlockPos spot = Builders.spot(level, entry.id(), placement);
			helper.assertTrue(Reach.claims(level, hall, spot) == claimed, "the setup: " + entry.id() + "'s middle " + spot + " claimed=" + !claimed);
			helper.assertFalse(Reach.claims(level, hall, placement.origin()), "the setup: " + entry.id() + "'s corner " + placement.origin() + " is in the village");
			manager.recordFinished(entry.id(), placement, owner(entry.id().getPath() + "@" + middle.toShortString()));
			recorded.add(placement);
		}
	}
}
