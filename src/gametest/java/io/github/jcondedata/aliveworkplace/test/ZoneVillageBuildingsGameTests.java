package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.hall.Decorations;
import io.github.jcondedata.aliveworkplace.hall.MarketDays;
import io.github.jcondedata.aliveworkplace.hall.Services;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.RequestErrands;
import io.github.jcondedata.aliveworkplace.work.Reach;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * B620: the village is its claimed area (36.20: the hall's radius or any cell painted into a City Plan zone), so a
 * building finished in a zone painted past the radius is one of the village's buildings for every village-wide list:
 * its beauty (Decorations), its rank's building count (VillageRanks), its Market Square (MarketDays), its services
 * (Services), the Chapel its guests and errands look for (LegendGuests, RequestErrands). The same buildings in an
 * unpainted corner just as far from the hall stay out of the village.
 *
 * <p>Scaled down like {@link QaZoneRepairsPageBugTests}: hall radius 12, so the plan's grid is ±16 blocks with cells of one
 * block. Only the finished-building records matter here (nothing is built), each placed so its outline's middle is 17 blocks
 * from the hall and its corner farther still, so only the middle can be in the village.
 */
public class ZoneVillageBuildingsGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	/** Buildings whose middle is in the painted south-east corner count for the village; the same in the north-west don't. */
	//$ gametest_ticks_batch AREA '200' '"zoneVillageBuildings"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "zoneVillageBuildings")
	public void buildingsInAZonePastTheRadiusCountForTheVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
		});
		VillageHalls.RADIUS = 12;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		BlockPos hall = helper.absolutePos(HALL);
		helper.assertTrue(CityPlan.half() == 16, "the plan's grid reaches " + CityPlan.half() + " blocks from the hall, not 16");

		// a Homes zone painted over the plan's south-east corner only
		BitSet cells = new BitSet();
		for (int dx = 9; dx <= 15; dx++) {
			for (int dz = 9; dz <= 15; dz++) {
				cells.set(CityPlan.cellAt(hall, hall.offset(dx, 0, dz)));
			}
		}
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));

		BlockPos zone = hall.offset(11, 0, 13);
		BlockPos outside = hall.offset(-11, 0, -13);
		BuildSiteManager manager = BuildSiteManager.get(level);
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		Leftovers.after(helper, () -> recorded.forEach(p -> BuildSiteManager.get(level).forgetFinished(p)));

		// before: nothing of the village's own
		int beauty = Decorations.beauty(level, hall);
		int buildings = VillageRanks.score(level, hall, 0).buildings();

		// the same three buildings in the unpainted north-west corner: not the village's
		record(helper, manager, recorded, StarterBlueprints.TOPIARY_GARDEN, outside, false);
		record(helper, manager, recorded, StarterBlueprints.MARKET_SQUARE, outside, false);
		record(helper, manager, recorded, StarterBlueprints.CHAPEL, outside, false);
		helper.assertTrue(Decorations.beauty(level, hall) == beauty,
			"a Topiary Garden outside the village (unpainted corner, 17 blocks off, radius 12) added beauty: " + beauty + " -> " + Decorations.beauty(level, hall));
		helper.assertTrue(VillageRanks.score(level, hall, 0).buildings() == buildings,
			"buildings outside the village counted for its rank: " + buildings + " -> " + VillageRanks.score(level, hall, 0).buildings());
		helper.assertTrue(MarketDays.square(level, hall).isEmpty(), "a Market Square outside the village is its market: " + MarketDays.square(level, hall));
		helper.assertTrue(Services.find(level, hall, List.of()).stream().noneMatch(f -> f.id().getPath().equals("chapel")),
			"a Chapel outside the village is one of its services");
		helper.assertFalse(RequestErrands.finished(level, hall, StarterBlueprints.CHAPEL.id()), "a Chapel outside the village is finished in it");
		helper.assertTrue(LegendGuests.chapel(level, hall).isEmpty(), "a Chapel outside the village is where its guests stand");

		// the same in the painted zone: the village's
		record(helper, manager, recorded, StarterBlueprints.TOPIARY_GARDEN, zone, true);
		record(helper, manager, recorded, StarterBlueprints.MARKET_SQUARE, zone, true);
		record(helper, manager, recorded, StarterBlueprints.CHAPEL, zone, true);
		int points = Decorations.points(StarterBlueprints.TOPIARY_GARDEN.id()) + Decorations.points(StarterBlueprints.MARKET_SQUARE.id())
			+ Decorations.points(StarterBlueprints.CHAPEL.id());
		helper.assertTrue(Decorations.points(StarterBlueprints.TOPIARY_GARDEN.id()) > 0, "the Topiary Garden is worth no beauty");
		helper.assertTrue(Decorations.beauty(level, hall) == beauty + points,
			"a Topiary Garden, Market Square and Chapel in the village's zone past the radius didn't add their " + points + " beauty: " + beauty + " -> " + Decorations.beauty(level, hall));
		int now = VillageRanks.score(level, hall, 0).buildings();
		helper.assertTrue(now == buildings + 3, "three buildings in the village's zone past the radius didn't count for its rank: " + buildings + " -> " + now);
		helper.assertTrue(MarketDays.square(level, hall).map(p -> Reach.claims(level, hall, p) && !p.closerThan(hall, 12)).orElse(false),
			"the Market Square in the village's zone isn't its market: " + MarketDays.square(level, hall));
		helper.assertTrue(Services.find(level, hall, List.of()).stream().anyMatch(f -> f.id().getPath().equals("chapel")),
			"the Chapel in the village's zone isn't one of its services: " + Services.find(level, hall, List.of()));
		helper.assertTrue(RequestErrands.finished(level, hall, StarterBlueprints.CHAPEL.id()), "the Chapel in the village's zone isn't finished in it");
		helper.assertTrue(LegendGuests.chapel(level, hall).isPresent(), "the Chapel in the village's zone isn't where its guests stand");

		// a building forgotten (torn down) stops counting at once
		manager.forgetFinished(recorded.get(3));
		helper.assertTrue(Decorations.beauty(level, hall) == beauty + points - Decorations.points(StarterBlueprints.TOPIARY_GARDEN.id()),
			"a Topiary Garden no longer finished still adds beauty");
		helper.succeed();
	}

	/** A finished {@code entry} whose outline's middle is at {@code middle}, checked to be (or not be) in the claimed area. */
	private static void record(GameTestHelper helper, BuildSiteManager manager, List<BlueprintData.Placement> recorded,
			StarterBlueprints.Entry entry, BlockPos middle, boolean claimed) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		ResourceLocation id = entry.id();
		// turned so its corner lies farther out than its middle (the zone's builds face the hall, the others away), so only the
		// middle can put it in the village: placed once, then moved so its middle is where it should be
		Rotation rotation = claimed ? Rotation.CLOCKWISE_180 : Rotation.NONE;
		BlockPos first = Builders.spot(level, id, new BlueprintData.Placement(level.dimension().location(), middle, rotation, Mirror.NONE));
		BlockPos origin = middle.offset(middle.getX() - first.getX(), 0, middle.getZ() - first.getZ());
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, rotation, Mirror.NONE);
		BlockPos spot = Builders.spot(level, id, placement);
		double off = Math.sqrt(Math.pow(spot.getX() - hall.getX(), 2) + Math.pow(spot.getZ() - hall.getZ(), 2));
		helper.assertTrue(off > VillageHalls.RADIUS, "the setup: " + id + "'s middle is only " + off + " blocks from the hall");
		helper.assertTrue(spot.getX() == middle.getX() && spot.getZ() == middle.getZ(), "the setup: " + id + "'s middle is " + spot + ", not " + middle);
		helper.assertTrue(Reach.claims(level, hall, spot) == claimed, "the setup: " + id + "'s middle " + spot + " claimed=" + !claimed);
		helper.assertFalse(Reach.claims(level, hall, origin), "the setup: " + id + "'s corner " + origin + " is in the village");
		manager.recordFinished(id, placement, UUID.nameUUIDFromBytes(("zone-village-" + id).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		recorded.add(placement);
	}
}
