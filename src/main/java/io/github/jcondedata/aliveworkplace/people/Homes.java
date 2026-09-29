package io.github.jcondedata.aliveworkplace.people;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/**
 * Homes: a villager's home is the finished building (one a builder put up) that their bed is in. Its tier goes by the
 * blueprint's name ({@code <name>_2} is tier II, see {@link BlueprintUpgrades}): a tier II home lifts its people's mood by
 * {@link #TIER_2_MOOD}, tier III and up by {@link #TIER_3_MOOD} (in {@link Moods}). The Village Hall's list says where
 * each villager lives, and its "What next?" page suggests upgrading when most of the village sleeps in tier I buildings
 * or in none a builder put up.
 */
public final class Homes {
	public static final int TIER_2_MOOD = 5;
	public static final int TIER_3_MOOD = 10;
	/** How far a finished building's origin may be from a bed inside it (the largest blueprints are 48 across). */
	static final int SEARCH = 64;

	/** A home: the building's blueprint and its tier. */
	public record Home(ResourceLocation structure, int tier) {
		public Component name() {
			return Blueprints.displayName(structure);
		}

		/** How much living here lifts a mood. */
		public int mood() {
			return tier >= 3 ? TIER_3_MOOD : tier == 2 ? TIER_2_MOOD : 0;
		}
	}

	/** The finished building {@code bed} is in (the highest tier, should two overlap), if any. */
	public static Optional<Home> at(ServerLevel level, BlockPos bed) {
		Home best = null;
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedNear(level, bed, SEARCH)) {
			boolean inside = BlueprintLibrary.get(level, f.structure())
				.map(b -> BlueprintOutline.bounds(f.placement(), b.size()).isInside(bed))
				.orElse(false);
			if (inside) {
				Home home = new Home(f.structure(), BlueprintUpgrades.tier(f.structure()));
				if (best == null || home.tier() > best.tier()) {
					best = home;
				}
			}
		}
		return Optional.ofNullable(best);
	}

	/** {@code villager}'s home: the finished building their bed is in, if they have a bed and it's in one. */
	public static Optional<Home> of(ServerLevel level, Villager villager) {
		BlockPos bed = VillageNeeds.bed(level, villager);
		return bed == null ? Optional.empty() : at(level, bed);
	}

	private Homes() {
	}
}
