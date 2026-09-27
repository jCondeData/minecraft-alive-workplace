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
 * A blueprint laid out in the world: ordered work lists for each stage. Recomputed on load rather
 * than saved: a pure function of (blueprint, placement), plus the terrain under the build for the
 * foundation (columns already filled count as ground, so recomputing mid-build is safe).
 */
public final class BuildPlan {
	public enum Stage {
		/** Break whatever is in the way, top to bottom. */
		CLEAR,
		/** Fill the gap under the bottom layer down to solid ground, bottom to top. */
		FOUNDATION,
		/** Place self-supporting blocks, bottom to top. */
		STRUCTURE,
		/** Place torches, doors, beds, plants... once there is something to attach them to. */
		DECORATION,
		/** Taking a build down (deconstruct mode): decorations first, then everything else top to bottom. */
		DECONSTRUCT,
		DONE;

		public Stage next() {
			return this == DECORATION || this == DECONSTRUCT ? DONE : values()[Math.min(ordinal() + 1, DONE.ordinal())];
		}
	}

	/**
	 * One unit of work. For two-block blocks (doors, beds, tall flowers) {@code secondaryPos/State}
	 * is the other half, placed in the same action.
	 */
	public record Step(BlockPos pos, BlockState state, @Nullable CompoundTag nbt,
					   @Nullable BlockPos secondaryPos, @Nullable BlockState secondaryState) {
		/** What placing this step uses up (see {@link MaterialRules#requirements}). */
		public List<MaterialRules.Requirement> requirements() {
			return MaterialRules.requirements(state, nbt);
		}
	}

	private final Map<BlockPos, BlockState> targets;
	private final List<Step> clear;
	private final List<Step> foundation;
	private final List<Step> structure;
	private final List<Step> decoration;
	private final List<Step> deconstruct;
	private final BoundingBox bounds;

	private BuildPlan(List<Step> clear, List<Step> foundation, List<Step> structure, List<Step> decoration, BoundingBox bounds) {
		this(clear, foundation, structure, decoration, List.of(), bounds);
	}

	private BuildPlan(List<Step> clear, List<Step> foundation, List<Step> structure, List<Step> decoration, List<Step> deconstruct,
					  BoundingBox bounds) {
		Map<BlockPos, BlockState> t = new HashMap<>();
		for (List<Step> list : List.of(foundation, structure, decoration)) {
			for (Step s : list) {
				t.put(s.pos(), s.state());
				if (s.secondaryPos() != null && s.secondaryState() != null) {
					t.put(s.secondaryPos(), s.secondaryState());
				}
			}
		}
		this.targets = t;
		this.clear = clear;
		this.foundation = foundation;
		this.structure = structure;
		this.decoration = decoration;
		this.deconstruct = deconstruct;
		this.bounds = bounds;
	}

	/** Plan without a foundation (no world to look at: material lists, previews). */
	public static BuildPlan create(Blueprint blueprint, BlueprintData.Placement placement) {
		return create(blueprint, placement, null, 0);
	}

	/**
	 * Plan for building in {@code level}: includes foundation columns under the bottom layer, at most
	 * {@code maxFoundationDepth} blocks deep (0 = no foundation).
	 */
	public static BuildPlan create(Blueprint blueprint, BlueprintData.Placement placement, @Nullable Level level, int maxFoundationDepth) {
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
			MaterialRules.Kind kind = MaterialRules.classify(step.state(), step.nbt());
			if (kind == MaterialRules.Kind.SKIP) {
				continue;
			}
			Step full = withSecondary(step, byPos);
			(kind == MaterialRules.Kind.STRUCTURE ? structure : decoration).add(full);
		}

		BoundingBox bounds = BlueprintOutline.bounds(placement, blueprint.size());
		List<Step> foundation = level != null && maxFoundationDepth > 0 ? foundation(level, structure, bounds, maxFoundationDepth) : new ArrayList<>();
		if (Boolean.getBoolean("aliveworkplace.debug") && !foundation.isEmpty()) {
			io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("[plan] {} foundation steps for {}, first {} -> {} (ground there: {})", foundation.size(), blueprint.id(),
				foundation.get(0).pos().toShortString(), foundation.get(0).state(), level.getBlockState(foundation.get(0).pos()));
		}
		clear.sort(order(bounds, true));
		foundation.sort(order(bounds, false));
		structure.sort(order(bounds, false));
		decoration.sort(order(bounds, false));
		return new BuildPlan(List.copyOf(clear), List.copyOf(foundation), List.copyOf(structure), List.copyOf(decoration), bounds);
	}

	/**
	 * Plan for taking the build down: every block the blueprint places, decorations first (so nothing
	 * pops off and drops), then the rest from the top down. Blocks that are not what the blueprint says
	 * are left alone.
	 */
	public static BuildPlan deconstruct(Blueprint blueprint, BlueprintData.Placement placement) {
		BuildPlan build = create(blueprint, placement);
		BoundingBox bounds = build.bounds();
		List<Step> decorations = new ArrayList<>(build.decoration);
		List<Step> structure = new ArrayList<>(build.structure);
		decorations.sort(order(bounds, true));
		structure.sort(order(bounds, true));
		List<Step> steps = new ArrayList<>(decorations);
		steps.addAll(structure);
		return new BuildPlan(List.of(), List.of(), List.of(), List.of(), List.copyOf(steps), bounds);
	}

	public boolean isDeconstruction() {
		return !deconstruct.isEmpty();
	}

	/**
	 * Under every solid block of the bottom layer, fill down to the first solid ground (leaves, plants,
	 * snow and water don't count as ground). The fill uses the block above it when that is a plain full
	 * block (so a cobblestone rim gets a cobblestone footing), otherwise cobblestone; grass becomes dirt.
	 */
	private static List<Step> foundation(Level level, List<Step> structure, BoundingBox bounds, int maxDepth) {
		List<Step> out = new ArrayList<>();
		int bottom = bounds.minY();
		for (Step step : structure) {
			BlockPos top = step.pos();
			if (top.getY() != bottom || step.state().getCollisionShape(level, top).isEmpty()) {
				continue;
			}
			BlockState fill = foundationBlock(level, top, step.state());
			for (int depth = 1; depth <= maxDepth; depth++) {
				BlockPos p = top.below(depth);
				if (p.getY() < level.getMinBuildHeight() || isGround(level, p)) {
					break;
				}
				out.add(new Step(p, fill, null, null, null));
			}
		}
		return out;
	}

	private static boolean isGround(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !state.isAir() && !state.canBeReplaced() && !(state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock)
			&& !(state.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock);
	}

	private static BlockState foundationBlock(Level level, BlockPos pos, BlockState above) {
		net.minecraft.world.level.block.Block b = above.getBlock();
		if (b == net.minecraft.world.level.block.Blocks.GRASS_BLOCK || b == net.minecraft.world.level.block.Blocks.PODZOL
			|| b == net.minecraft.world.level.block.Blocks.MYCELIUM || b == net.minecraft.world.level.block.Blocks.DIRT_PATH
			|| b == net.minecraft.world.level.block.Blocks.FARMLAND) {
			return net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
		}
		boolean plain = above.isCollisionShapeFullBlock(level, pos) && !above.hasBlockEntity()
			&& !(b instanceof net.minecraft.world.level.block.FallingBlock) && MaterialRules.requirement(above).isPresent()
			&& MaterialRules.classify(above) == MaterialRules.Kind.STRUCTURE;
		return plain ? above : net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState();
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
			case FOUNDATION -> foundation;
			case STRUCTURE -> structure;
			case DECORATION -> decoration;
			case DECONSTRUCT -> deconstruct;
			case DONE -> List.of();
		};
	}

	public BoundingBox bounds() {
		return bounds;
	}

	/**
	 * True if the plan still has to put a solid block at {@code pos}: somewhere a builder should not
	 * stand (or send anyone else to stand), or it will be in its own way later.
	 */
	public boolean needsSolidAt(Level level, BlockPos pos) {
		BlockState wanted = targets.get(pos);
		if (wanted == null || wanted.getCollisionShape(level, pos).isEmpty()) {
			return false;
		}
		return !MaterialRules.matches(level.getBlockState(pos), wanted);
	}

	/** Blocks that will be placed (foundation + structure + decoration). */
	public int placeableCount() {
		return foundation.size() + structure.size() + decoration.size() + deconstruct.size();
	}

	/** Positions where the world does not (yet) look like the blueprint. Empty when the build is complete. */
	public List<BlockPos> unfinished(Level level) {
		List<BlockPos> out = new ArrayList<>();
		for (Step step : deconstruct) {
			if (MaterialRules.matches(level.getBlockState(step.pos()), step.state())) {
				out.add(step.pos()); // still standing
			}
		}
		for (Step step : foundation) {
			if (!MaterialRules.matches(level.getBlockState(step.pos()), step.state())) {
				out.add(step.pos());
			}
		}
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
		for (List<Step> list : List.of(foundation, structure, decoration)) {
			for (Step step : list) {
				for (MaterialRules.Requirement r : step.requirements()) {
					out.merge(r.item(), r.count(), Integer::sum);
				}
			}
		}
		return out;
	}
}
