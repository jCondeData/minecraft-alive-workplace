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
 * style) within the hall's reach, and the spots in it the Cup uses. The spots are known offsets in our own templates
 * (tools/blueprints/arena.py; {@code ArenaGameTests} checks them against the files), turned with the build's rotation
 * and mirror, like {@link MarketDays#square}.
 */
public final class Arenas {
	/** Template spots (x, y, z from the template's origin; y 0 sits on the clicked block). Feet spots: where one stands. */
	public static final BlockPos RING_CENTRE = new BlockPos(12, 1, 9);
	/** The two trainer's boxes, west and east (where the trainer stands, on the dais). */
	public static final List<BlockPos> BOXES = List.of(new BlockPos(2, 2, 9), new BlockPos(22, 2, 9));
	/** The middle of the fair lane (Arena II and III). */
	public static final BlockPos FAIR_LANE = new BlockPos(12, 1, 24);
	/** The top of the champion's pole, where the Cup banner goes (Arena III). */
	public static final BlockPos CHAMPION_POLE = new BlockPos(12, 6, 0);
	/** The notice board's panel (its signs face the street). */
	public static final BlockPos NOTICE_BOARD = new BlockPos(2, 2, 1);

	private static final int[] BENCH_X = {9, 10, 11, 13, 14, 15};
	private static final int[] FRONT_X = {7, 8, 9, 15, 16, 17};
	private static final int[] GRAND_X = {7, 8, 9, 10, 11, 13, 14, 15, 16, 17};

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
	 * The seats of a tier, as template spots (the stair a villager sits on): Arena I's two rows of six benches at the back;
	 * II's two rows of six at the front and three of six at the back (30); III's front rows and the grandstand's four rows
	 * of ten.
	 */
	public static List<BlockPos> seats(int tier) {
		List<BlockPos> out = new ArrayList<>();
		if (tier >= 2) {
			rows(out, FRONT_X, 2, -1, 3);
		}
		switch (tier) {
			case 1 -> rows(out, BENCH_X, 2, 1, 16);
			case 2 -> rows(out, BENCH_X, 3, 1, 16);
			case 3 -> rows(out, GRAND_X, 4, 1, 16);
			default -> {
			}
		}
		return out;
	}

	/** {@code count} rows from z {@code z0}, each a block up and {@code dz} on from the one before, seats at {@code xs}. */
	private static void rows(List<BlockPos> out, int[] xs, int count, int dz, int z0) {
		for (int r = 0; r < count; r++) {
			for (int x : xs) {
				out.add(new BlockPos(x, 1 + r, z0 + r * dz));
			}
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
	 * The village's Arena: a finished one within the hall's reach in this dimension, the highest tier first (an upgraded
	 * Arena is remembered as finished at every tier), then the nearest; empty if the village has none.
	 */
	public static Optional<Arena> find(ServerLevel level, BlockPos hall) {
		return BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS).stream()
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
