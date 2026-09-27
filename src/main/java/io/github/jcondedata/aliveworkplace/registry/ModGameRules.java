package io.github.jcondedata.aliveworkplace.registry;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.level.GameRules;

/** Server-admin knobs, set with /gamerule. */
public final class ModGameRules {
	/** When true, builders place blocks without needing materials (handy for creative towns and testing). */
	public static final GameRules.Key<GameRules.BooleanValue> FREE_MATERIALS =
		GameRuleRegistry.register("workplaceFreeMaterials", GameRules.Category.MOBS, GameRuleFactory.createBooleanRule(false));

	/** Ticks a builder spends on each block (20 ticks = 1 second). Lower is faster. */
	public static final GameRules.Key<GameRules.IntegerValue> BUILD_DELAY =
		GameRuleRegistry.register("workplaceBuildDelay", GameRules.Category.MOBS, GameRuleFactory.createIntRule(8, 1));

	/** When true, any player can upload blueprint files at a Blueprint Table (operators always can). */
	public static final GameRules.Key<GameRules.BooleanValue> ALLOW_UPLOADS =
		GameRuleRegistry.register("workplaceAllowUploads", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(true));

	/** How deep builders fill under a build standing on uneven ground (0 = no foundations). */
	public static final GameRules.Key<GameRules.IntegerValue> FOUNDATION_DEPTH =
		GameRuleRegistry.register("workplaceFoundationDepth", GameRules.Category.MOBS, GameRuleFactory.createIntRule(12, 0));

	/** When true, builders with nothing to do help with builds near their bench. */
	public static final GameRules.Key<GameRules.BooleanValue> BUILDERS_HELP =
		GameRuleRegistry.register("workplaceBuildersHelp", GameRules.Category.MOBS, GameRuleFactory.createBooleanRule(true));

	/** When true, a builder only takes orders from the player who hired it, their friends and operators. */
	public static final GameRules.Key<GameRules.BooleanValue> BUILDER_OWNERSHIP =
		GameRuleRegistry.register("workplaceBuilderOwnership", GameRules.Category.MOBS, GameRuleFactory.createBooleanRule(true));

	public static void init() {
	}

	private ModGameRules() {
	}
}
