package io.github.jcondedata.aliveworkplace.furnish;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * B300: no furnishing floats. The small things a room is dressed with (a flower pot, a candle, a lantern standing on a
 * table, a carpet, a skull, a cake) rest on the block under them, and a flower pot or a skull doesn't even need one in
 * the game ({@code FlowerPotBlock} has no {@code canSurvive}), so a pot whose table or shelf went stays in mid-air.
 *
 * <p>Two nets: {@link #reseat} sets such a thing down on the furniture piece that took its spot's place, when that piece
 * is lower than the vanilla one (a fence-and-carpet table turned into a one-block modded table left the carpet's cell
 * air, and the pot on it one block up in the air); and {@link #settle}, once a builder has levelled the ground (the end
 * of LANDSCAPE, which fills the hollows a pot on a hillside grave stands over), looks
 * at what was placed and sets down (or drops, as an item) anything resting on nothing or that the game says can't stay.
 */
public final class Resting {
	/** How far down {@link #settle} looks for something to set a piece on before dropping it as an item instead. */
	static final int MAX_DROP = 8;

	private Resting() {
	}

	/**
	 * B641: other mods' small things that rest on the block under them (Handcrafted's crockery, which the modded library
	 * sets on every table), by block id in {@code data/aliveworkplace/tags/block/rests_on_floor.json}.
	 */
	public static final TagKey<Block> RESTS_ON_FLOOR = TagKey.create(Registries.BLOCK, io.github.jcondedata.aliveworkplace.AliveWorkplace.id("rests_on_floor"));

	/** Whether {@code state} is a small thing that rests on the block under it (not one hanging or on a wall). */
	public static boolean standsOnFloor(BlockState state) {
		if (state.is(RESTS_ON_FLOOR)) {
			return true;
		}
		Block block = state.getBlock();
		if (block instanceof LanternBlock) {
			return !state.getValue(LanternBlock.HANGING);
		}
		if (block instanceof TorchBlock) {
			return !(block instanceof WallTorchBlock);
		}
		return block instanceof FlowerPotBlock || block instanceof CandleBlock || block instanceof CarpetBlock
			|| block instanceof BasePressurePlateBlock || block instanceof SkullBlock || block instanceof CakeBlock
			|| block instanceof CandleCakeBlock || block instanceof DecoratedPotBlock;
	}

	/**
	 * {@code blueprint} with every small thing ({@link #standsOnFloor}) that stood on one of the {@code emptied} cells
	 * (cells a furniture swap made air) set down onto whatever is under it now. A pot hanging from a chain is left.
	 */
	public static Blueprint reseat(Blueprint blueprint, Set<BlockPos> emptied) {
		if (emptied.isEmpty()) {
			return blueprint;
		}
		Map<BlockPos, Integer> index = new HashMap<>();
		List<Blueprint.Entry> entries = new ArrayList<>(blueprint.blocks());
		int minY = Integer.MAX_VALUE;
		for (int i = 0; i < entries.size(); i++) {
			index.put(entries.get(i).pos(), i);
			minY = Math.min(minY, entries.get(i).pos().getY());
		}
		boolean changed = false;
		List<BlockPos> cells = new ArrayList<>(emptied);
		cells.sort(Comparator.comparingInt(BlockPos::getY));
		for (BlockPos cell : cells) {
			BlockPos from = cell.above();
			Integer at = index.get(from);
			if (at == null) {
				continue;
			}
			Blueprint.Entry thing = entries.get(at);
			if (!standsOnFloor(thing.state()) || hangs(entries, index, from)) {
				continue;
			}
			BlockPos seat = from;
			while (seat.getY() > minY && empty(entries, index, seat.below())) {
				seat = seat.below();
			}
			if (seat.equals(from)) {
				continue;
			}
			entries.set(at, new Blueprint.Entry(from, Blocks.AIR.defaultBlockState(), null));
			Blueprint.Entry moved = new Blueprint.Entry(seat, thing.state(), thing.nbt());
			Integer to = index.get(seat);
			if (to != null) {
				entries.set(to, moved);
			} else {
				index.put(seat, entries.size());
				entries.add(moved);
			}
			changed = true;
		}
		return changed ? new Blueprint(blueprint.id(), blueprint.size(), List.copyOf(entries), blueprint.entities()) : blueprint;
	}

	private static boolean empty(List<Blueprint.Entry> entries, Map<BlockPos, Integer> index, BlockPos pos) {
		Integer i = index.get(pos);
		return i == null || entries.get(i).state().isAir();
	}

	private static boolean hangs(List<Blueprint.Entry> entries, Map<BlockPos, Integer> index, BlockPos pos) {
		Integer i = index.get(pos.above());
		return i != null && entries.get(i).state().getBlock() instanceof ChainBlock;
	}

	/**
	 * Whether the thing at {@code pos} in the world rests on nothing: a small thing ({@link #standsOnFloor}) over a block with
	 * no top to stand on, or any other non-solid block the game says can't stay there ({@code canSurvive}).
	 */
	public static boolean unsupported(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir()) {
			return false;
		}
		if (standsOnFloor(state)) {
			if (level.getBlockState(pos.above()).getBlock() instanceof ChainBlock) {
				return false; // a pot hanging from a chain (37.5)
			}
			return !holds(level, pos.below());
		}
		return !state.isSolid() && !state.canSurvive(level, pos); // a wall torch with no wall, a modded plant on nothing
	}

	private static boolean holds(ServerLevel level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	/**
	 * The builder's last look over a finished build (the end of LANDSCAPE): every block of {@code plan} that stands where it was
	 * placed but rests on nothing is set down on the first block under it (within {@link #MAX_DROP}), or broken and
	 * dropped as an item when there's none or it can't stay there. Returns how many were moved or dropped.
	 */
	public static int settle(ServerLevel level, BuildPlan plan) {
		int fixed = 0;
		List<BuildPlan.Step> steps = new ArrayList<>(plan.steps(BuildPlan.Stage.STRUCTURE));
		steps.addAll(plan.steps(BuildPlan.Stage.DECORATION));
		steps.sort(Comparator.comparingInt(s -> s.pos().getY()));
		for (BuildPlan.Step step : steps) {
			if (step.state().isAir()) {
				continue;
			}
			BlockState world = level.getBlockState(step.pos());
			if (!world.is(step.state().getBlock()) || !unsupported(level, step.pos(), world)) {
				continue;
			}
			fixed++;
			BlockPos seat = seat(level, step.pos(), world);
			if (seat == null) {
				level.destroyBlock(step.pos(), true);
			} else {
				level.setBlock(step.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				level.setBlock(seat, world, Block.UPDATE_ALL);
			}
		}
		return fixed;
	}

	/** Where a floating small thing at {@code pos} comes to rest, or null when it is dropped instead. */
	private static BlockPos seat(ServerLevel level, BlockPos pos, BlockState state) {
		if (!standsOnFloor(state) || state.hasBlockEntity()) {
			return null;
		}
		BlockPos seat = pos;
		for (int i = 0; i < MAX_DROP && level.getBlockState(seat.below()).isAir(); i++) {
			seat = seat.below();
		}
		return holds(level, seat.below()) && state.canSurvive(level, seat) ? seat : null;
	}
}
