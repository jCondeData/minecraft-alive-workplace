package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

/**
 * The Cradle (ROADMAP 30.12). A Cradle within {@link #BED_RANGE} blocks of a bed (its head, where the bed's point of
 * interest is) in a village with a hall makes it a nursery village: each hall round ages every child in the village by
 * the round's length again (so they grow up in half the time), and one more baby a day may be born
 * ({@link VillageGrowth#every}). More cradles add nothing. At night a child of the house (the nearest within
 * {@link #CHILD_RANGE}) sleeps in each such cradle, seated in a {@link CradleSeat}. Nothing here is saved: the cradles
 * are points of interest, so the village is a nursery again as soon as it loads. Off ({@code cradles}): furniture.
 */
public final class Cradles {
	public static boolean ENABLED = true;
	/** How near a bed a cradle must be. */
	public static final int BED_RANGE = 4;
	/** How far a child may be from a cradle to sleep in it. */
	public static final int CHILD_RANGE = 8;

	/** The cradles near a bed in the village of the hall at {@code hall}, nearest the hall first. */
	public static List<BlockPos> cradles(ServerLevel level, BlockPos hall) {
		if (!ENABLED) {
			return List.of();
		}
		PoiManager poi = level.getPoiManager();
		return poi.getInRange(h -> h.is(ModVillagers.CRADLE_POI), hall, VillageHalls.RADIUS, PoiManager.Occupancy.ANY)
			.map(PoiRecord::getPos)
			.filter(c -> poi.getInRange(h -> h.is(PoiTypes.HOME), c, BED_RANGE, PoiManager.Occupancy.ANY).findAny().isPresent())
			.sorted(Comparator.comparingDouble(c -> c.distSqr(hall)))
			.toList();
	}

	/** Whether the village of the hall at {@code hall} is a nursery village. */
	public static boolean nursery(ServerLevel level, BlockPos hall) {
		return !cradles(level, hall).isEmpty();
	}

	/** The extra babies a day the village may have: 1 in a nursery village, else 0. */
	public static int extraBirths(ServerLevel level, BlockPos hall) {
		return nursery(level, hall) ? 1 : 0;
	}

	/** A hall round of {@code ticks}: in a nursery village the children age by it again, and at night they're put to bed in the cradles. */
	public static void round(ServerLevel level, BlockPos hall, int ticks) {
		List<BlockPos> cradles = cradles(level, hall);
		if (cradles.isEmpty()) {
			return;
		}
		for (Villager child : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && v.isBaby())) {
			child.setAge(Math.min(0, child.getAge() + ticks));
		}
		if (Curfew.isNight(level)) {
			for (BlockPos cradle : cradles) {
				putToBed(level, cradle);
			}
		}
	}

	/** Seats the nearest child not yet in a cradle in the one at {@code cradle} (if it's empty); returns the child. */
	public static Optional<Villager> putToBed(ServerLevel level, BlockPos cradle) {
		if (!level.getEntitiesOfClass(CradleSeat.class, new AABB(cradle), s -> s.isAlive() && !s.getPassengers().isEmpty()).isEmpty()) {
			return Optional.empty();
		}
		Optional<Villager> child = level.getEntitiesOfClass(Villager.class, new AABB(cradle).inflate(CHILD_RANGE),
				v -> v.isAlive() && v.isBaby() && !v.isPassenger())
			.stream().min(Comparator.comparingDouble(v -> v.distanceToSqr(cradle.getCenter())));
		child.ifPresent(v -> {
			if (v.isSleeping()) {
				v.stopSleeping();
			}
			CradleSeat seat = CradleSeat.at(level, cradle);
			level.addFreshEntity(seat);
			if (!v.startRiding(seat, true)) {
				seat.discard();
			}
		});
		return child.filter(Villager::isPassenger);
	}

	/** The line for the hall's beds icon and the Book's last row: whether the village has a Cradle. */
	public static Component status(ServerLevel level, BlockPos hall) {
		if (!ENABLED) {
			return VillageHallScreen.line("screen.aliveworkplace.hall.cradle.off", ChatFormatting.DARK_GRAY);
		}
		return nursery(level, hall)
			? VillageHallScreen.line("screen.aliveworkplace.hall.cradle.yes", ChatFormatting.GREEN)
			: VillageHallScreen.line("screen.aliveworkplace.hall.cradle.no", ChatFormatting.GRAY);
	}

	private Cradles() {
	}
}
