package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * Decorations make a village prettier: every well, lamp post, bench, fountain, gazebo, bandstand or market square a builder
 * finished within a Village Hall's reach adds to the village's beauty (and each Masterwork in an item frame, 29.10), and each point of beauty adds 1% to its
 * wellbeing, up to {@link #MAX_BONUS}.
 */
public final class Decorations {
	/** Beauty points per decoration (an upgrade replaces its base on the same spot, so it counts instead of it). */
	static final Map<ResourceLocation, Integer> POINTS = Map.of(
		StarterBlueprints.WELL.id(), 2,
		StarterBlueprints.WELL_2.id(), 3,
		StarterBlueprints.STREET_LAMP.id(), 1,
		StarterBlueprints.PARK_BENCH.id(), 1,
		StarterBlueprints.FOUNTAIN.id(), 3,
		StarterBlueprints.GAZEBO.id(), 3,
		StarterBlueprints.MARKET_SQUARE.id(), 5,
		StarterBlueprints.CHAPEL.id(), 4,
		StarterBlueprints.BANDSTAND.id(), 3); // the Bard's (ROADMAP 27.14)
	/** Wellbeing per point of beauty. */
	public static final float PER_POINT = 0.01f;
	/** Most wellbeing decorations add. */
	public static final float MAX_BONUS = 0.10f;

	/** The beauty points a finished build of {@code blueprint} is worth (0 for anything that isn't a decoration). */
	public static int points(ResourceLocation blueprint) {
		return POINTS.getOrDefault(io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles.base(blueprint), 0);
	}

	/** The beauty of the village round the hall at {@code hall}: the points of the decorations finished within its reach. */
	public static int beauty(ServerLevel level, BlockPos hall) {
		int beauty = 0;
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS)) {
			beauty += points(f.structure());
		}
		beauty += io.github.jcondedata.aliveworkplace.city.Roads.lamps(level, hall) * POINTS.get(StarterBlueprints.STREET_LAMP.id()); // the roads' lamps (27.16)
		return beauty + io.github.jcondedata.aliveworkplace.legend.StrangeMoods.beauty(level, hall); // Masterworks in item frames (29.10)
	}

	/** The wellbeing that much beauty adds. */
	public static float bonus(int beauty) {
		return Math.min(MAX_BONUS, beauty * PER_POINT);
	}

	private Decorations() {
	}
}
