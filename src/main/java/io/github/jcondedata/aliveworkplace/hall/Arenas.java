package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The village's Arena (ROADMAP 28.16), where the Festival Cup is fought: a finished Arena (any of its three tiers, any
 * style) in the village (its claimed area), and the spots in it the Cup uses. The spots are known offsets in our own templates
 * (tools/blueprints/js/arena/plan.mjs, SPOTS and SEATS; {@code ArenaGameTests} checks them against the files), turned with the
 * build's rotation and mirror, like {@link MarketDays#square}. ROADMAP 50.12 redrew the Arena ("the Market Stadium") and moved
 * every spot; an Arena standing from the old drawing (tools/blueprints/arena.py, blueprint_history/arena*) is read with the
 * new spots until it is refreshed.
 */
public final class Arenas {
	/** Template spots (x, y, z from the template's origin; y 0 sits on the clicked block). Feet spots: where one stands. */
	public static final BlockPos RING_CENTRE = new BlockPos(12, 1, 11);
	/** The two trainer's boxes, west (red) and east (blue): where the trainer stands, on the dais. */
	public static final List<BlockPos> BOXES = List.of(new BlockPos(2, 2, 11), new BlockPos(22, 2, 11));
	/** The middle of the fair lane (Arena II and III): the walk under the colonnade. */
	public static final BlockPos FAIR_LANE = new BlockPos(8, 1, 4);
	/** The top of the champion's mast, where the Cup banner goes (Arena III). */
	public static final BlockPos CHAMPION_POLE = new BlockPos(12, 12, 0);
	/** The notice board's panel in the west gate pier (its sign faces the street). */
	public static final BlockPos NOTICE_BOARD = new BlockPos(10, 2, 2);

	/** A row of the back stand: 12 seats, the middle (x 11 to 13) left for the podium and the Leader's box. */
	private static final int[] BACK_X = {5, 6, 7, 8, 9, 10, 14, 15, 16, 17, 18, 19};
	/** The colonnade's deck (Arena II): 18 seats, the gate's loggia (x 11 to 13) between its halves. */
	private static final int[] FRONT_X = {2, 3, 4, 5, 6, 7, 8, 9, 10, 14, 15, 16, 17, 18, 19, 20, 21, 22};

	/** A finished Arena: its blueprint, tier (1-3) and placement, and its spots in the world. */
	public record Arena(ResourceLocation structure, int tier, BlueprintData.Placement placement, BlockPos ring, List<BlockPos> boxes,
		List<BlockPos> seats, Optional<BlockPos> fairLane, Optional<BlockPos> championPole, BlockPos noticeBoard) {
	}

	/** Which tier of Arena {@code structure} is (styled ones go by their base), or 0 if it's no Arena. */
	public static int tier(ResourceLocation structure) {
		ResourceLocation base = BlueprintStyles.base(structure);
		for (int i = 0; i < StarterBlueprints.ARENAS.size(); i++) {
			if (StarterBlueprints.ARENAS.get(i).id().equals(base)) {
				return i + 1;
			}
		}
		return 0;
	}

	/**
	 * The seats of a tier, as template spots (the stair a villager sits on): Arena I's row of 12 along the back wall (12);
	 * II adds 18 on the colonnade's deck along the front, facing the ring (30); III's grandstand adds two more rows of 12
	 * behind the first (54).
	 */
	public static List<BlockPos> seats(int tier) {
		List<BlockPos> out = new ArrayList<>();
		if (tier >= 1) {
			row(out, BACK_X, 1, 17);
		}
		if (tier >= 2) {
			row(out, FRONT_X, 5, 5);
		}
		if (tier >= 3) {
			row(out, BACK_X, 2, 18);
			row(out, BACK_X, 3, 19);
		}
		return out;
	}

	/** A row of seats at height {@code y} and depth {@code z}, at {@code xs}. */
	private static void row(List<BlockPos> out, int[] xs, int y, int z) {
		for (int x : xs) {
			out.add(new BlockPos(x, y, z));
		}
	}

	/** The Arena of {@code structure} (tier {@link #tier} > 0) placed at {@code placement}, its spots in the world. */
	public static Arena at(ResourceLocation structure, BlueprintData.Placement placement) {
		int tier = tier(structure);
		return new Arena(structure, tier, placement, world(placement, RING_CENTRE), BOXES.stream().map(p -> world(placement, p)).toList(),
			seats(tier).stream().map(p -> world(placement, p)).toList(),
			tier >= 2 ? Optional.of(world(placement, FAIR_LANE)) : Optional.empty(),
			tier >= 3 ? Optional.of(world(placement, CHAMPION_POLE)) : Optional.empty(),
			world(placement, NOTICE_BOARD));
	}

	/** A template spot of a build placed at {@code placement}, in the world. */
	public static BlockPos world(BlueprintData.Placement placement, BlockPos spot) {
		return placement.origin().offset(StructureTemplate.transform(spot, placement.mirror(), placement.rotation(), BlockPos.ZERO));
	}

	/**
	 * The village's Arena: a finished one in the village (its claimed area) in this dimension, the highest tier first (an upgraded
	 * Arena is remembered as finished at every tier), then the nearest; empty if the village has none.
	 */
	public static Optional<Arena> find(ServerLevel level, BlockPos hall) {
		Optional<Arena> stadium = io.github.jcondedata.aliveworkplace.wonder.StadiumDays.arena(level, hall);
		if (stadium.isPresent()) {
			return stadium; // 35.24: a finished Stadium (tournament_host) is where its village's Cup is held
		}
		return BuildSiteManager.get(level).finishedInVillage(level, hall).stream()
			.filter(f -> tier(f.structure()) > 0)
			.min(Comparator.<BuildSiteManager.Finished>comparingInt(f -> -tier(f.structure()))
				.thenComparingDouble(f -> f.placement().origin().distSqr(hall)))
			.map(f -> at(f.structure(), f.placement()));
	}

	/** Whether the village round {@code hall} has a finished Arena. */
	public static boolean has(ServerLevel level, BlockPos hall) {
		return find(level, hall).isPresent();
	}

	private Arenas() {
	}
}
