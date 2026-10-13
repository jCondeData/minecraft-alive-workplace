package io.github.jcondedata.aliveworkplace.people;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
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
 * Homes: a villager's home is the finished building (one a builder put up) that their bed is in. Its tier (its grade,
 * 34.15) goes by the blueprint's name ({@code <name>_2} is tier II, see {@link io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades}), or what a
 * {@code homes/} data file gives it ({@link HomeGrades}: the Townhouse is grade II): a tier II home lifts its people's mood by
 * {@link #TIER_2_MOOD}, tier III and up by {@link #TIER_3_MOOD} (in {@link Moods}). The Village Hall's list says where
 * each villager lives, and its "What next?" page suggests upgrading when most of the village sleeps in tier I buildings
 * or in none a builder put up.
 */
public final class Homes {
	public static final int TIER_2_MOOD = 5;
	public static final int TIER_3_MOOD = 10;
	/** A home: the building's blueprint and its tier, which is its grade ({@link HomeGrades#grade}). */
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
		return in(homes(level), bed);
	}

	/**
	 * Every finished building in the dimension but roads (a road is nobody's home, 27.15), with its box. On the server
	 * thread the list is worked out once a tick (B103: a mood or pace check for every villager in one tick looked up
	 * every building's size once per villager) and again whenever a building is added or forgotten in that tick.
	 */
	private static java.util.List<Building> homes(ServerLevel level) {
		BuildSiteManager manager = BuildSiteManager.get(level);
		if (!level.getServer().isSameThread()) {
			return homesNow(level, manager);
		}
		long now = level.getGameTime();
		if (homesLevel != level || homesManager != manager || homesTick != now || homesChanges != manager.changes()) {
			homesCached = homesNow(level, manager);
			homesLevel = level;
			homesManager = manager;
			homesTick = now;
			homesChanges = manager.changes();
		}
		return homesCached;
	}

	private static java.util.List<Building> homesNow(ServerLevel level, BuildSiteManager manager) {
		java.util.List<Building> out = new java.util.ArrayList<>();
		for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
			if (io.github.jcondedata.aliveworkplace.city.Roads.isSegment(f.structure())) {
				continue;
			}
			Vec3i size = size(level, f.structure());
			if (size != null) {
				out.add(new Building(new Home(f.structure(), HomeGrades.grade(f.structure())), BlueprintOutline.bounds(f.placement(), size)));
			}
		}
		return java.util.List.copyOf(out);
	}

	@Nullable
	private static ServerLevel homesLevel;
	@Nullable
	private static BuildSiteManager homesManager;
	private static long homesTick = Long.MIN_VALUE;
	private static int homesChanges;
	private static java.util.List<Building> homesCached = java.util.List.of();

	/**
	 * Every finished building in the dimension with the box it fills, worked out once (each size read once), for checking
	 * many beds in one go with {@link #in} (the hall's dawn class check, 34.2).
	 */
	public static java.util.List<Building> all(ServerLevel level) {
		java.util.List<Building> out = new java.util.ArrayList<>();
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedIn(level)) {
			Vec3i size = size(level, f.structure());
			if (size != null) {
				out.add(new Building(new Home(f.structure(), HomeGrades.grade(f.structure())), BlueprintOutline.bounds(f.placement(), size)));
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
