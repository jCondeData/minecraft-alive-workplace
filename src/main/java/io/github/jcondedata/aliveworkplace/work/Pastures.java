package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cobblemon Pasture Blocks as courier stops. Pokémon working a pasture for Cobbleworkers drop what they
 * gather into the chests and barrels nearest the pasture, so a Delivery Note that starts (or ends) at a
 * pasture means every container within {@link SupplyContainers#RADIUS} blocks of it. Recognised by id, so
 * nothing here needs Cobblemon or Cobbleworkers classes.
 */
public final class Pastures {
	private static final ResourceLocation PASTURE = ResourceLocation.fromNamespaceAndPath("cobblemon", "pasture");

	public static boolean isPasture(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(PASTURE);
	}

	/** The containers a route stop stands for: the container itself, or every one around a pasture (nearest first). */
	public static List<BlockPos> containers(ServerLevel level, BlockPos stop) {
		return isPasture(level.getBlockState(stop)) ? SupplyContainers.find(level, stop, null) : List.of(stop);
	}

	private Pastures() {
	}
}
