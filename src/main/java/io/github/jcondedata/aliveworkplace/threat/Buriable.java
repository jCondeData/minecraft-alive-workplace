package io.github.jcondedata.aliveworkplace.threat;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a siege's ladder rung or sand ramp may stand in (B553, B558): air, or a plant of the wild ground it buries.
 *
 * <p>The plants are vanilla's {@code #minecraft:replaceable} tag, the blocks a placed block takes the place of (short
 * and tall grass, ferns, dead bushes, snow layers, the Nether's roots and sprouts, and any modded plant a mod adds to
 * it), without the ones that are no wild ground: fluids and what stands in them (water, lava, seagrass, bubble columns:
 * a moat stays a moat), fire, vines, glow lichen and hanging roots (they hang on walls and ceilings, often a player's
 * own), and the operators' light and structure void blocks. Flowers and saplings are not in the tag, so a player's
 * flower bed at a wall's foot still turns a ladder or ramp away. The buried plant is simply replaced (no drop), and when
 * the siege's rung or sand is taken away air is left, as the plant would be after any block was placed in it.
 */
public final class Buriable {
	private Buriable() {
	}

	/** Whether a rung or ramp block may go where {@code state} stands: air, or a plant it buries. */
	public static boolean room(BlockState state) {
		if (state.isAir()) {
			return true;
		}
		return state.is(BlockTags.REPLACEABLE) && state.getFluidState().isEmpty() && !state.is(BlockTags.FIRE)
			&& !state.is(Blocks.VINE) && !state.is(Blocks.GLOW_LICHEN) && !state.is(Blocks.HANGING_ROOTS)
			&& !state.is(Blocks.LIGHT) && !state.is(Blocks.STRUCTURE_VOID);
	}
}
