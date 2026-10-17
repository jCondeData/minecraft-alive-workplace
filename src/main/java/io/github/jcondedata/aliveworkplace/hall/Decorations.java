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
	static final Map<ResourceLocation, Integer> POINTS = Map.ofEntries(
		Map.entry(StarterBlueprints.WELL.id(), 2),
		Map.entry(StarterBlueprints.WELL_2.id(), 3),
		Map.entry(StarterBlueprints.STREET_LAMP.id(), 1),
		Map.entry(StarterBlueprints.PARK_BENCH.id(), 1),
		Map.entry(StarterBlueprints.FOUNTAIN.id(), 3),
		Map.entry(StarterBlueprints.GAZEBO.id(), 3),
		Map.entry(StarterBlueprints.MARKET_SQUARE.id(), 5),
		Map.entry(StarterBlueprints.CHAPEL.id(), 4),
		Map.entry(StarterBlueprints.BANDSTAND.id(), 3), // the Bard's (ROADMAP 27.14)
		// ROADMAP 26.14: the small ones 1, the rest 2, the Topiary Garden 3
		Map.entry(StarterBlueprints.SIGNPOST.id(), 1),
		Map.entry(StarterBlueprints.FLOWER_PLANTERS.id(), 1),
		Map.entry(StarterBlueprints.HAY_CART.id(), 1),
		Map.entry(StarterBlueprints.NOTICE_BOARD.id(), 1),
		Map.entry(StarterBlueprints.MARKET_CORNER.id(), 1),
		Map.entry(StarterBlueprints.MARKET_CROSS.id(), 2),
		Map.entry(StarterBlueprints.STAG_STATUE.id(), 2),
		Map.entry(StarterBlueprints.STONE_ARCHWAY.id(), 2),
		Map.entry(StarterBlueprints.LILY_POND.id(), 2),
		Map.entry(StarterBlueprints.LANTERN_PERGOLA.id(), 2),
		Map.entry(StarterBlueprints.FIRE_PIT.id(), 2),
		Map.entry(StarterBlueprints.SUNDIAL.id(), 2),
		Map.entry(StarterBlueprints.FOOTBRIDGE.id(), 2),
		Map.entry(StarterBlueprints.TOPIARY_GARDEN.id(), 3));
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
		beauty += io.github.jcondedata.aliveworkplace.legend.Founder.beauty(level, hall); // the Founder's statue while it stands (29.23)
		beauty += io.github.jcondedata.aliveworkplace.wonder.powers.BeautyPower.beauty(level, hall); // a finished Wonder's beauty while it stands (35.8)
		return beauty + io.github.jcondedata.aliveworkplace.legend.StrangeMoods.beauty(level, hall); // Masterworks in item frames (29.10)
	}

	/** The wellbeing that much beauty adds. */
	public static float bonus(int beauty) {
		return Math.min(MAX_BONUS, beauty * PER_POINT);
	}

	private Decorations() {
	}
}
