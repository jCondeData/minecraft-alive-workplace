package io.github.jcondedata.aliveworkplace.registry;

import io.github.jcondedata.aliveworkplace.platform.Platform;
import net.minecraft.world.level.GameRules;

/** Server-admin knobs, set with /gamerule. */
public final class ModGameRules {
	/** When true, builders place blocks without needing materials (handy for creative towns and testing). */
	public static final GameRules.Key<GameRules.BooleanValue> FREE_MATERIALS =
		Platform.get().booleanRule("workplaceFreeMaterials", GameRules.Category.MOBS, false);

	/** Ticks a builder spends on each block (20 ticks = 1 second). Lower is faster. */
	public static final GameRules.Key<GameRules.IntegerValue> BUILD_DELAY =
		Platform.get().intRule("workplaceBuildDelay", GameRules.Category.MOBS, 8, 1, Integer.MAX_VALUE);

	/** When true, any player can upload blueprint files at a Blueprint Table (operators always can). */
	public static final GameRules.Key<GameRules.BooleanValue> ALLOW_UPLOADS =
		Platform.get().booleanRule("workplaceAllowUploads", GameRules.Category.MISC, true);

	/** How deep builders fill under a build standing on uneven ground (0 = no foundations). */
	public static final GameRules.Key<GameRules.IntegerValue> FOUNDATION_DEPTH =
		Platform.get().intRule("workplaceFoundationDepth", GameRules.Category.MOBS, 12, 0, Integer.MAX_VALUE);

	/** When true, builders with nothing to do help with builds near their bench. */
	public static final GameRules.Key<GameRules.BooleanValue> BUILDERS_HELP =
		Platform.get().booleanRule("workplaceBuildersHelp", GameRules.Category.MOBS, true);

	/** When true, a builder only takes orders from the player who hired it, their friends and operators. */
	public static final GameRules.Key<GameRules.BooleanValue> BUILDER_OWNERSHIP =
		Platform.get().booleanRule("workplaceBuilderOwnership", GameRules.Category.MOBS, true);

	/**
	 * When true, builds and quarries keep running while the player who ordered them is online but far away
	 * (their chunks stay loaded; nothing is kept loaded for offline players).
	 */
	public static final GameRules.Key<GameRules.BooleanValue> KEEP_WORK_LOADED =
		Platform.get().booleanRule("workplaceKeepWorkLoaded", GameRules.Category.MISC, true);

	/**
	 * How many blocks around a finished build the builder levels: natural ground sticking up above the
	 * build's floor is dug away and holes at floor level are filled with dirt (0 = leave the ground alone).
	 */
	public static final GameRules.Key<GameRules.IntegerValue> LEVEL_GROUND =
		Platform.get().intRule("workplaceLevelGround", GameRules.Category.MOBS, 2, 0, 8);

	/**
	 * When true, a village farmer with no field takes on the farm by their composter by themselves, as soon as there's a
	 * chest near the composter for the harvest.
	 */
	public static final GameRules.Key<GameRules.BooleanValue> VILLAGE_FARMS =
		Platform.get().booleanRule("workplaceVillageFarms", GameRules.Category.MOBS, true);

	public static void init() {
	}

	private ModGameRules() {
	}
}
