package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * A blueprint laid out in the world: ordered work lists for each stage. Pure function of
 * (blueprint, placement) so it is recomputed on load rather than saved.
 */
public final class BuildPlan {
	public enum Stage {
		/** Break whatever is in the way, top to bottom. */
		CLEAR,
		/** Place self-supporting blocks, bottom to top. */
		STRUCTURE,
		/** Place torches, doors, beds, plants... once there is something to attach them to. */
		DECORATION,
		DONE;

		public Stage next() {
			return values()[Math.min(ordinal() + 1, DONE.ordinal())];
		}
	}

	/**
	 * One unit of work. For two-block blocks (doors, beds, tall flowers) {@code secondaryPos/State}
	 * is the other half, placed in the same action.
	 */
	public record Step(BlockPos pos, BlockState state, @Nullable CompoundTag nbt,
					   @Nullable BlockPos secondaryPos, @Nullable BlockState secondaryState) {
	}

	private final List<Step> clear;
	private final List<Step> structure;
	private final List<Step> decoration;
	private final BoundingBox bounds;

	private BuildPlan(List<Step> clear, List<Step> structure, List<Step> decoration, BoundingBox bounds) {
		this.clear = clear;
		this.structure = structure;
		this.decoration = decoration;
		this.bounds = bounds;
	}

	public static BuildPlan create(Blueprint blueprint, BlueprintData.Placement placement) {
		Map<BlockPos, Step> byPos = new HashMap<>();
		for (Blueprint.Entry entry : blueprint.blocks()) {
			BlockPos world = placement.origin().offset(StructureTemplate.transform(entry.pos(), placement.mirror(), placement.rotation(), BlockPos.ZERO));
			BlockState state = MaterialRules.forPlacement(entry.state().mirror(placement.mirror()).rotate(placement.rotation()));
			byPos.put(world, new Step(world, state, entry.nbt(), null, null));
		}

		List<Step> clear = new ArrayList<>(byPos.values());
		List<Step> structure = new ArrayList<>();
		List<Step> decoration = new ArrayList<>();
		for (Step step : byPos.values()) {
			MaterialRules.Kind kind = MaterialRules.classify(step.state());
			if (kind == MaterialRules.Kind.SKIP) {
				continue;
			}
			Step full = withSecondary(step, byPos);
			(kind == MaterialRules.Kind.STRUCTURE ? structure : decoration).add(full);
		}

		BoundingBox bounds = BlueprintOutline.bounds(placement, blueprint.size());
		clear.sort(order(bounds, true));
		structure.sort(order(bounds, false));
		decoration.sort(order(bounds, false));
		return new BuildPlan(List.copyOf(clear), List.copyOf(structure), List.copyOf(decoration), bounds);
	}

	private static Step withSecondary(Step step, Map<BlockPos, Step> byPos) {
		BlockPos secondaryPos = MaterialRules.secondaryPos(step.pos(), step.state());
		if (secondaryPos == null) {
			return step;
		}
		Step other = byPos.get(secondaryPos);
		BlockState secondaryState = other != null && other.state().getBlock() == step.state().getBlock()
			? other.state()
			: MaterialRules.defaultSecondary(step.state());
		return new Step(step.pos(), step.state(), step.nbt(), secondaryPos, secondaryState);
	}

	/** Layer by layer; within a layer, row by row in a snake so the builder does not zig-zag. */
	private static Comparator<Step> order(BoundingBox bounds, boolean topDown) {
		return (a, b) -> {
			int ay = a.pos().getY(), by = b.pos().getY();
			if (ay != by) {
				return topDown ? Integer.compare(by, ay) : Integer.compare(ay, by);
			}
			int az = a.pos().getZ(), bz = b.pos().getZ();
			if (az != bz) {
				return Integer.compare(az, bz);
			}
			boolean reverse = ((az - bounds.minZ()) & 1) == 1;
			return reverse ? Integer.compare(b.pos().getX(), a.pos().getX()) : Integer.compare(a.pos().getX(), b.pos().getX());
		};
	}

	public List<Step> steps(Stage stage) {
		return switch (stage) {
			case CLEAR -> clear;
			case STRUCTURE -> structure;
			case DECORATION -> decoration;
			case DONE -> List.of();
		};
	}

	public BoundingBox bounds() {
		return bounds;
	}

	/** Blocks that will be placed (structure + decoration). */
	public int placeableCount() {
		return structure.size() + decoration.size();
	}

	/** Positions where the world does not (yet) look like the blueprint. Empty when the build is complete. */
	public List<BlockPos> unfinished(Level level) {
		List<BlockPos> out = new ArrayList<>();
		for (Step step : structure) {
			if (!MaterialRules.matches(level.getBlockState(step.pos()), step.state())) {
				out.add(step.pos());
			}
		}
		for (Step step : decoration) {
			if (!MaterialRules.matches(level.getBlockState(step.pos()), step.state())) {
				out.add(step.pos());
			}
		}
		for (Step step : clear) {
			if (step.state().isAir() && !level.getBlockState(step.pos()).isAir()) {
				out.add(step.pos());
			}
		}
		return out;
	}

	/** Total materials for the whole build (what a player needs to gather). */
	public Map<Item, Integer> materials() {
		Map<Item, Integer> out = new java.util.LinkedHashMap<>();
		for (List<Step> list : List.of(structure, decoration)) {
			for (Step step : list) {
				MaterialRules.requirement(step.state()).ifPresent(r -> out.merge(r.item(), r.count(), Integer::sum));
			}
		}
		return out;
	}
}
