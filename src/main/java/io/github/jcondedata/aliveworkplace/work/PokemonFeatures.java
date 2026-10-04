package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.platform.Platform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * The parts of Cobblemon that only some of its versions have (ROADMAP 28.2). The Cobbleverse pack ships Cobblemon 1.7.3;
 * Habitat Blocks, Type Gems on Deepslate Crystal Cores, the TM Machine and Alphas came in 1.8.0. Every 1.8-only feature
 * of this mod asks here first, so it waits quietly on 1.7.3 and turns on by itself once the pack moves up. Checked by
 * registry id (and for Alphas, which have no block, by Cobblemon's version), never by Cobblemon's classes.
 */
public enum PokemonFeatures {
	/** Cobblemon's Habitat Block, which spawns a habitat pool's Pokémon round it. */
	HABITATS("habitat_block"),
	/** The Deepslate Crystal Core that Type Gem clusters grow on. */
	TYPE_GEMS("deepslate_crystal_core"),
	/** The TM Machine, which teaches moves from TMs. */
	TM_MACHINE("tm_machine"),
	/** Alpha Pokémon (no block of their own: Cobblemon 1.8 or later). */
	ALPHAS(null);

	/** The first Cobblemon version with Alphas. */
	static final String ALPHAS_FROM = ">=1.8.0";

	private final String block;

	PokemonFeatures(String block) {
		this.block = block;
	}

	/** The block that shows this feature is there ({@code cobblemon:habitat_block}), or null for Alphas. */
	public ResourceLocation blockId() {
		return block == null ? null : ResourceLocation.fromNamespaceAndPath("cobblemon", block);
	}

	/** Whether the installed Cobblemon has this feature (false without Cobblemon). */
	public boolean available() {
		if (!Platform.get().isModLoaded("cobblemon")) {
			return false;
		}
		if (block == null) {
			try {
				return Platform.get().isModLoaded("cobblemon", ALPHAS_FROM);
			} catch (IllegalArgumentException e) {
				return false;
			}
		}
		return BuiltInRegistries.BLOCK.containsKey(blockId());
	}
}
