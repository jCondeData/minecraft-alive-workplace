package io.github.jcondedata.aliveworkplace.people;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

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

	/**
	 * The finished building {@code bed} is in (the highest tier, should two overlap), if any. Every finished building in
	 * the dimension is looked at (their sizes are cheap to get): imports can be any size, so no search radius is safe.
	 */
	public static Optional<Home> at(ServerLevel level, BlockPos bed) {
		return building(level, bed).map(Building::home);
	}

	/** A home and the box its building fills in the world. */
	public record Building(Home home, BoundingBox box) {
	}

	/** {@link #at}, with the box the building fills (a Legend's home is shared by every bed in it, ROADMAP 29.5). */
	public static Optional<Building> building(ServerLevel level, BlockPos bed) {
		Building best = null;
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedIn(level)) {
			Vec3i size = size(level, f.structure());
			if (size == null || Math.abs(f.placement().origin().getX() - bed.getX()) >= size.getX() + size.getZ()
				|| Math.abs(f.placement().origin().getZ() - bed.getZ()) >= size.getX() + size.getZ()) {
				continue; // (too far away to be in it whichever way it's turned)
			}
			BoundingBox box = BlueprintOutline.bounds(f.placement(), size);
			if (box.isInside(bed)) {
				Home home = new Home(f.structure(), BlueprintUpgrades.tier(f.structure()));
				if (best == null || home.tier() > best.home().tier()) {
					best = new Building(home, box);
				}
			}
		}
		return Optional.ofNullable(best);
	}

	/**
	 * Every finished building in the dimension with the box it fills, worked out once (each size read once), for checking
	 * many beds in one go with {@link #in} (the hall's dawn class check, 34.2).
	 */
	public static java.util.List<Building> all(ServerLevel level) {
		java.util.List<Building> out = new java.util.ArrayList<>();
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedIn(level)) {
			Vec3i size = size(level, f.structure());
			if (size != null) {
				out.add(new Building(new Home(f.structure(), BlueprintUpgrades.tier(f.structure())), BlueprintOutline.bounds(f.placement(), size)));
			}
		}
		return out;
	}

	/** The building of {@code buildings} (from {@link #all}) {@code bed} is in, the highest tier should two overlap, as {@link #building}. */
	public static Optional<Building> in(java.util.List<Building> buildings, BlockPos bed) {
		Building best = null;
		for (Building b : buildings) {
			if (b.box().isInside(bed) && (best == null || b.home().tier() > best.home().tier())) {
				best = b;
			}
		}
		return Optional.ofNullable(best);
	}

	/** A blueprint's size, from its structure file (a styled one is its base's size), or null if it's gone. */
	@Nullable
	private static Vec3i size(ServerLevel level, ResourceLocation id) {
		try {
			return level.getServer().getStructureManager().get(BlueprintStyles.base(id)).map(StructureTemplate::getSize).orElse(null);
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** {@code villager}'s home: the finished building their bed is in, if they have a bed and it's in one. */
	public static Optional<Home> of(ServerLevel level, Villager villager) {
		BlockPos bed = VillageNeeds.bed(level, villager);
		return bed == null ? Optional.empty() : at(level, bed);
	}

	private Homes() {
	}
}
