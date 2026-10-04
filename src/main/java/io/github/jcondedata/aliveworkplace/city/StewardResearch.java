package io.github.jcondedata.aliveworkplace.city;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.research.Research;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/**
 * The Steward's {@code research} effect (ROADMAP 27.9): when a scholar works and nothing is being researched, he picks
 * the next topic. A rule naming its topic gets that one while it's available; otherwise Fortification after a raid in
 * the last {@link #RAID_DAYS} days, Medicine with {@link #ILL} or more ill, Green Thumb while food is short, Logistics
 * with a store {@link #STORE_FULL} full, else Swift Hands, Hearth and Kinship in that order, each only when
 * {@link Research.State#available}.
 */
public final class StewardResearch {
	public static final int RAID_DAYS = 7;
	public static final int ILL = 2;
	public static final double STORE_FULL = 0.8;
	/** The last of the order, when nothing is pressing. */
	public static final List<Research.Topic> AFTER = List.of(Research.Topic.SWIFT_HANDS, Research.Topic.HEARTH, Research.Topic.KINSHIP);

	/** What the village looks like to the Steward choosing a topic. */
	public record Village(long daysSinceRaid, int ill, boolean foodShort, int storePercent) {
	}

	/** A topic he picked, why (a translation key) and its number. */
	public record Pick(Research.Topic topic, String why, long number) {
	}

	/** Counts the village round {@code hall}. */
	public static Village village(ServerLevel level, BlockPos hall) {
		StewardConditions.Facts facts = StewardConditions.Facts.of(level, hall);
		long raid = level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity ? entity.lastRaidDay() : -100;
		int ill = level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && Sickness.isIll(v)).size();
		boolean food = new StewardConditions.FoodShort(VillageAdvice.MEALS_PER_ADULT).test(facts).held();
		StewardConditions.Check store = new StewardConditions.StoreFull(STORE_FULL).test(facts);
		return new Village(Chronicle.day(level) - raid, ill, food, store.held() ? (int) store.value() : 0);
	}

	/** The next topic, or empty when none in the order is available. */
	public static Optional<Pick> pick(Research.State research, Village village, Optional<Research.Topic> named) {
		if (named.isPresent() && research.available(named.get())) {
			return Optional.of(new Pick(named.get(), "steward.aliveworkplace.research.why.named", 0));
		}
		if (village.daysSinceRaid() >= 0 && village.daysSinceRaid() < RAID_DAYS && research.available(Research.Topic.FORTIFICATION)) {
			return Optional.of(new Pick(Research.Topic.FORTIFICATION, "steward.aliveworkplace.research.why.raid", village.daysSinceRaid()));
		}
		if (village.ill() >= ILL && research.available(Research.Topic.MEDICINE)) {
			return Optional.of(new Pick(Research.Topic.MEDICINE, "steward.aliveworkplace.research.why.ill", village.ill()));
		}
		if (village.foodShort() && research.available(Research.Topic.GREEN_THUMB)) {
			return Optional.of(new Pick(Research.Topic.GREEN_THUMB, "steward.aliveworkplace.research.why.food", 0));
		}
		if (village.storePercent() >= Math.round(STORE_FULL * 100) && research.available(Research.Topic.LOGISTICS)) {
			return Optional.of(new Pick(Research.Topic.LOGISTICS, "steward.aliveworkplace.research.why.store", village.storePercent()));
		}
		for (Research.Topic topic : AFTER) {
			if (research.available(topic)) {
				return Optional.of(new Pick(topic, "steward.aliveworkplace.research.why.next", 0));
			}
		}
		return Optional.empty();
	}

	private StewardResearch() {
	}
}
