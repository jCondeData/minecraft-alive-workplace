package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.city.Roads;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * QA (lane-e-1104-0413), B898's sentences a player reads, without Create (the words don't need it): the site of the
 * station a hub's builder puts up for a second neighbor's train is called "Second Train Station" wherever a site's name
 * shows (the builder's status, the hall's list), for halls on either side of 0 (negative positions in the id), never the
 * "Road" or "Village Train" of its neighbors in the same folder; and the chronicle's line reads as written.
 */
public class QaSecondStationWords1104eGameTests implements FabricGameTest {
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void qaTheSecondStationsSiteIsCalledSecondTrainStation(GameTestHelper helper) {
		BlockPos[][] pairs = {
			{new BlockPos(120, 64, 35), new BlockPos(300, 70, 35)},
			{new BlockPos(-120, 64, -35), new BlockPos(-300, -12, 35)},
			{new BlockPos(0, 0, 0), new BlockPos(-1, 0, -1)},
		};
		for (BlockPos[] p : pairs) {
			ResourceLocation id = CaravanTrains.stationSiteId(p[0], p[1]);
			helper.assertTrue(Roads.isSegment(id) && CaravanTrains.isStationSite(id), "a second station's site isn't known as one: " + id);
			helper.assertFalse(CaravanTrains.isTrainSite(id) || CaravanTrains.isTrackSite(id), "a second station's site is taken for a train or track site: " + id);
			helper.assertValueEqual(Blueprints.displayName(id).getString(), "Second Train Station", "the site's name for " + id);
			ResourceLocation train = CaravanTrains.trainId(p[0], p[1]);
			helper.assertFalse(CaravanTrains.isStationSite(train), "the village train's site is taken for a second station's: " + train);
			helper.assertValueEqual(Blueprints.displayName(train).getString(), "Village Train", "the train site's name for " + train);
			helper.assertTrue(!id.equals(CaravanTrains.stationSiteId(p[1], p[0])), "the two villages' second stations share a site id: " + id);
		}
		helper.assertValueEqual(Component.translatable("chronicle.aliveworkplace.second_station_built", Component.literal("Bram")).getString(),
			"Bram put up a second station by the track, so trains can come in from both sides", "the chronicle's line");
		helper.succeed();
	}
}
