package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureEquivalents;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * "Any mod's spruce chair will do" (ROADMAP 37.13): when a builder goes for a furniture piece its chests don't hold, the
 * pieces of the build's furniture spots not yet started switch to whichever equivalent piece the chests (and its bag)
 * do hold, first in {@code furnitureMods} order ({@link FurnitureChoice#restocked}). The site's plan is then worked out
 * again; it keeps its order, the steps being sorted by position, and a spot's new piece has the same footprint.
 */
public final class FurnitureStock {
	private FurnitureStock() {
	}

	/**
	 * Switches {@code site}'s unstarted furniture to the pieces in {@code supplies} and {@code bag} when {@code wanted} is
	 * a furniture piece (one of a set of equivalents) and something changes; true if it did (the plan is worked out
	 * again on the next tick). Only a new build: a repair, a taking-down or a cellar keeps what its building has.
	 */
	public static boolean restock(ServerLevel level, BuildSite site, BuilderBag bag, List<BlockPos> supplies, Item wanted) {
		if (!Furnishings.ENABLED || site.isRepair() || site.isDeconstruction() || site.isCellarOnly()
			|| !(wanted instanceof BlockItem block) || FurnitureEquivalents.of(block.getBlock()).isEmpty()) {
			return false;
		}
		List<io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot> spots = BlueprintFurniture.of(site.structure());
		FurnitureChoice current = site.furniture().matched(spots); // B641: keyed by today's spots file
		if (current.spots().isEmpty()) {
			return false;
		}
		Map<Item, Long> stock = new HashMap<>(SupplyContainers.contents(level, supplies));
		for (ItemStack stack : bag.stacks()) {
			if (!stack.isEmpty()) {
				stock.merge(stack.getItem(), (long) stack.getCount(), Long::sum);
			}
		}
		FurnitureChoice next = current.restocked(spots, Furnishings.MODS,
			i -> started(level, site.placement(), current.spots().get(i)), stock);
		if (next == current) {
			return false;
		}
		site.setFurniture(next);
		return true;
	}

	/**
	 * Whether a spot's piece is going up already: any of its cells holds a block (the piece, or anything else the
	 * builder hasn't cleared), or isn't loaded (it can't be told: left alone).
	 */
	static boolean started(ServerLevel level, BlueprintData.Placement placement, FurnitureChoice.Placed placed) {
		if (placed == null) {
			return true;
		}
		for (Map.Entry<BlockPos, BlockState> cell : placed.cells().entrySet()) {
			if (cell.getValue().isAir()) {
				continue;
			}
			BlockPos world = placement.origin().offset(StructureTemplate.transform(placed.pos().offset(cell.getKey()), placement.mirror(),
				placement.rotation(), BlockPos.ZERO));
			if (!level.isLoaded(world) || !level.getBlockState(world).isAir()) {
				return true;
			}
		}
		return false;
	}
}
