package io.github.jcondedata.aliveworkplace.legend;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;

/**
 * Where the shared powers act. Auras never scan for entities: each dimension keeps a list of its settled Legends, made
 * again at most every {@link #REFRESH} ticks from the Legends seen lately ({@link Legends#tick} adds each one every 200
 * ticks of its life). A Legend on strike has no powers.
 */
public final class LegendPowers {
	/** The most any boosts together may speed a worker up: twice as fast (the shared cap, read in {@code BuilderLevels.delay}). */
	public static final float PACE_CAP = 2f;
	public static final int REFRESH = 200;

	/** A settled Legend working its powers, as the aura list holds it. */
	public record Active(Villager villager, Legend legend, LegendData data) {
	}

	private static final Map<ResourceKey<Level>, Set<Villager>> SEEN = new HashMap<>();
	private static final Map<ResourceKey<Level>, List<Active>> LISTS = new HashMap<>();
	private static final Map<ResourceKey<Level>, Long> MADE = new HashMap<>();

	static void seen(Villager villager) {
		boolean added = SEEN.computeIfAbsent(villager.level().dimension(), k -> Collections.newSetFromMap(new WeakHashMap<>())).add(villager);
		if (added) {
			MADE.remove(villager.level().dimension()); // a newcomer is in the list at once
		}
	}

	/** Forgets the lists (a reload, a Legend cleared, tests). */
	public static void forget() {
		LISTS.clear();
		MADE.clear();
	}

	/** The settled Legends of {@code level} with their powers working. */
	public static List<Active> settled(ServerLevel level) {
		if (!Legends.ENABLED) {
			return List.of();
		}
		long now = level.getGameTime();
		Long made = MADE.get(level.dimension());
		if (made != null && now - made < REFRESH && now >= made) {
			return LISTS.getOrDefault(level.dimension(), List.of());
		}
		List<Active> out = new ArrayList<>();
		Set<Villager> seen = SEEN.getOrDefault(level.dimension(), Set.of());
		for (Villager v : List.copyOf(seen)) {
			LegendData data = ModAttachments.LEGEND.get(v);
			if (!v.isAlive() || v.level() != level || data == null) {
				seen.remove(v);
				continue;
			}
			if (data.settled() && !data.onStrike()) {
				Legends.get(data.id()).ifPresent(legend -> out.add(new Active(v, legend, data)));
			}
		}
		LISTS.put(level.dimension(), List.copyOf(out));
		MADE.put(level.dimension(), now);
		return LISTS.get(level.dimension());
	}

	/** Whether {@code pos} is within {@code radius} of the Legend, or (radius 0) in the Legend's village. */
	private static boolean inReach(ServerLevel level, Active legend, BlockPos pos, int radius, Optional<BlockPos> village) {
		if (radius > 0) {
			return legend.villager().blockPosition().distSqr(pos) <= (double) radius * radius;
		}
		Optional<BlockPos> theirs = legend.data().hall().isPresent() ? legend.data().hall() : VillageHalls.nearest(level, legend.villager().blockPosition());
		return theirs.isPresent() && theirs.equals(village);
	}

	/** How many times as fast {@code worker} works thanks to Legends near them: 1 to {@link #PACE_CAP}. */
	public static float pace(Villager worker) {
		if (!Legends.ENABLED || !(worker.level() instanceof ServerLevel level)) {
			return 1f;
		}
		List<Active> legends = settled(level);
		if (legends.isEmpty()) {
			return 1f;
		}
		var trade = BuiltInRegistries.VILLAGER_PROFESSION.getKey(worker.getVillagerData().getProfession());
		float factor = 1f;
		Optional<BlockPos> village = null;
		for (Active legend : legends) {
			for (PacePower p : legend.legend().powers(PacePower.class)) {
				if (!p.covers(trade)) {
					continue;
				}
				if (p.radius() == 0 && village == null) {
					village = VillageHalls.nearest(level, worker.blockPosition());
				}
				if (inReach(level, legend, worker.blockPosition(), p.radius(), village)) {
					factor *= p.factor();
				}
			}
		}
		return Math.min(PACE_CAP, factor);
	}

	/** A mood reason from a Legend: what the villager reads, and the points. */
	public record MoodReason(Component reason, int points) {
	}

	/** The mood {@code villager} gets from Legends near them. */
	public static List<MoodReason> moods(ServerLevel level, Villager villager) {
		List<Active> legends = settled(level);
		if (legends.isEmpty()) {
			return List.of();
		}
		List<MoodReason> out = new ArrayList<>();
		Optional<BlockPos> village = null;
		for (Active legend : legends) {
			for (MoodPower p : legend.legend().powers(MoodPower.class)) {
				if (p.radius() == 0 && village == null) {
					village = VillageHalls.nearest(level, villager.blockPosition());
				}
				if (inReach(level, legend, villager.blockPosition(), p.radius(), village)) {
					out.add(new MoodReason(Component.translatable("mood.aliveworkplace.reason.legend", legend.legend().titleText()), p.points()));
				}
			}
		}
		return out;
	}

	private LegendPowers() {
	}
}
