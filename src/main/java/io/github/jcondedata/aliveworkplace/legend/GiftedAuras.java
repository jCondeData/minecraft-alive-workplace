package io.github.jcondedata.aliveworkplace.legend;

import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.Level;

/**
 * The Gifted whose gift reaches others (29.7): a Beloved's neighbours are happier ({@link MoodPower}, measured bed to
 * bed) and a Born Leader's fellow workers quicker ({@link PacePower}, own trade). As with {@link LegendPowers}, nobody
 * scans for entities: each dimension keeps a list of such Gifted, made again at most every {@link #REFRESH} ticks from
 * those {@link Gifted#tick} has seen lately.
 */
public final class GiftedAuras {
	public static final int REFRESH = 200;

	/** A Gifted villager whose gift has an aura. */
	public record Active(Villager villager, Gifted.Gift gift) {
	}

	private static final Map<ResourceKey<Level>, Set<Villager>> SEEN = new HashMap<>();
	private static final Map<ResourceKey<Level>, List<Active>> LISTS = new HashMap<>();
	private static final Map<ResourceKey<Level>, Long> MADE = new HashMap<>();

	/** Whether {@code gift} reaches others. */
	static boolean hasAura(Gifted.Gift gift) {
		return gift.effect(MoodPower.class) != null || gift.effect(PacePower.class) != null;
	}

	/** {@code villager}, Gifted with an aura, is around (a newcomer is in the list at once). */
	static void seen(Villager villager) {
		boolean added = SEEN.computeIfAbsent(villager.level().dimension(), k -> Collections.newSetFromMap(new WeakHashMap<>())).add(villager);
		if (added) {
			MADE.remove(villager.level().dimension());
		}
	}

	/** Forgets the lists (a gift changed, tests). */
	public static void forget() {
		LISTS.clear();
		MADE.clear();
	}

	/** The Gifted of {@code level} whose gift has an aura. */
	public static List<Active> active(ServerLevel level) {
		long now = level.getGameTime();
		Long made = MADE.get(level.dimension());
		if (made != null && now - made < REFRESH && now >= made) {
			return LISTS.getOrDefault(level.dimension(), List.of());
		}
		List<Active> out = new ArrayList<>();
		Set<Villager> seen = SEEN.getOrDefault(level.dimension(), Set.of());
		for (Villager v : List.copyOf(seen)) {
			Gifted.Gift gift = v.isAlive() && v.level() == level ? Gifted.of(v) : null;
			if (gift == null || !hasAura(gift)) {
				seen.remove(v);
				continue;
			}
			out.add(new Active(v, gift));
		}
		LISTS.put(level.dimension(), List.copyOf(out));
		MADE.put(level.dimension(), now);
		return LISTS.get(level.dimension());
	}

	private static boolean working(Villager villager) {
		VillagerProfession p = villager.getVillagerData().getProfession();
		return !villager.isBaby() && p != VillagerProfession.NONE && p != VillagerProfession.NITWIT;
	}

	/**
	 * How many times as fast {@code worker} works thanks to Born Leaders of their trade near them: 1 to
	 * {@link LegendPowers#PACE_CAP}; {@code Pace}'s shared cap then holds it with every other bonus.
	 */
	public static float pace(Villager worker) {
		if (!(worker.level() instanceof ServerLevel level) || !working(worker)) {
			return 1f;
		}
		List<Active> gifted = active(level);
		if (gifted.isEmpty()) {
			return 1f;
		}
		ResourceLocation trade = BuiltInRegistries.VILLAGER_PROFESSION.getKey(worker.getVillagerData().getProfession());
		float factor = 1f;
		for (Active a : gifted) {
			PacePower p = a.gift().effect(PacePower.class);
			if (p == null || a.villager() == worker || !working(a.villager())) {
				continue;
			}
			ResourceLocation theirs = BuiltInRegistries.VILLAGER_PROFESSION.getKey(a.villager().getVillagerData().getProfession());
			if (p.covers(trade, theirs) && (p.radius() <= 0 || a.villager().blockPosition().distSqr(worker.blockPosition()) <= (double) p.radius() * p.radius())) {
				factor *= p.factor();
			}
		}
		return Math.min(LegendPowers.PACE_CAP, factor);
	}

	/** The mood {@code villager} gets from Beloved neighbours: one reason each whose bed is within reach of theirs. */
	public static List<LegendPowers.MoodReason> moods(ServerLevel level, Villager villager) {
		List<Active> gifted = active(level);
		if (gifted.isEmpty()) {
			return List.of();
		}
		BlockPos bed = VillageNeeds.bed(level, villager);
		if (bed == null) {
			return List.of();
		}
		List<LegendPowers.MoodReason> out = new ArrayList<>();
		for (Active a : gifted) {
			MoodPower p = a.gift().effect(MoodPower.class);
			if (p == null || a.villager() == villager) {
				continue;
			}
			BlockPos theirs = VillageNeeds.bed(level, a.villager());
			if (theirs != null && (p.radius() <= 0 || theirs.distSqr(bed) <= (double) p.radius() * p.radius())) {
				out.add(new LegendPowers.MoodReason(Component.translatable("mood.aliveworkplace.reason.beloved", a.villager().getDisplayName()), p.points()));
			}
		}
		return out;
	}

	private GiftedAuras() {
	}
}
