package io.github.jcondedata.aliveworkplace.compat.create;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.kinetics.saw.TreeCutter;
import io.github.jcondedata.aliveworkplace.compat.create.mixin.ControlledContraptionAccessor;
import io.github.jcondedata.aliveworkplace.compat.create.mixin.TreeAccessor;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Village protection for Create's machines (B808, B993, B1033): where a machine stands, so {@link VillageProtection#mayMachineChange}
 * can judge the blocks it changes. A drill or saw stands at its own block; a contraption (a piston's, a bearing's, a
 * gantry's) at its controller, or where it was put together when nothing controls it (a minecart's).
 */
public final class CreateMachines {
	/** Where each contraption was put together (the controller's block, given to its {@code assemble}), until its entity exists. */
	private static final Map<Contraption, BlockPos> ASSEMBLED = Collections.synchronizedMap(new WeakHashMap<>());

	/** B1532: the Create machine whose tick is running on this thread (null between ticks, or with protection off). */
	private static final ThreadLocal<BlockEntity> TICKING = new ThreadLocal<>();

	private CreateMachines() {
	}

	/** Runs {@code tick} (the tick of the Create machine {@code machine}), remembering for {@link #mayHandOver} whose tick it is. */
	public static void ticking(BlockEntity machine, Runnable tick) {
		BlockEntity before = TICKING.get();
		TICKING.set(machine);
		try {
			tick.run();
		} finally {
			TICKING.set(before);
		}
	}

	/**
	 * B1532: whether the Create machine ticking now may hand an item over to the item acceptor (a depot, a belt, a
	 * drain, a funnel, any block with Create's direct belt input) at {@code target}. A machine outside a protected
	 * village may not hand anything to one inside ({@link VillageProtection#mayMachineChange}). Belts are judged by the
	 * segment at their end (BeltEndMixin), so a belt's tick is left to that; with no machine ticking (an item landing
	 * from the air, a player) there is nothing to judge here.
	 */
	public static boolean mayHandOver(Level level, BlockPos target) {
		BlockEntity machine = TICKING.get();
		if (machine == null || level == null || machine.getLevel() != level
			|| machine instanceof com.simibubi.create.content.kinetics.belt.BeltBlockEntity) {
			return true;
		}
		return VillageProtection.mayMachineChange(level, machine.getBlockPos(), target);
	}

	/** Remembers that {@code contraption} is being put together by the machine at {@code pos}. */
	public static void assembling(Contraption contraption, BlockPos pos) {
		ASSEMBLED.put(contraption, pos.immutable());
	}

	/** Where the machine that moves {@code contraption} stands. */
	public static BlockPos machine(Contraption contraption) {
		if (contraption.entity instanceof ControlledContraptionEntity controlled) {
			BlockPos controller = ((ControlledContraptionAccessor) controlled).aliveworkplace$controllerPos();
			if (controller != null) {
				return controller;
			}
		}
		BlockPos assembled = ASSEMBLED.get(contraption);
		return assembled != null ? assembled : contraption.anchor;
	}

	/** Whether the machine moving {@code contraption} may change the block at {@code target}. */
	public static boolean may(Level level, Contraption contraption, BlockPos target) {
		return VillageProtection.mayMachineChange(level, machine(contraption), target);
	}

	/**
	 * B1518: the part of {@code tree} (found by a saw's cut) that the machine at {@code machine} may fell: its logs, leaves
	 * and attachments inside a protected village it isn't in are left out. The same tree when nothing is left out.
	 */
	public static TreeCutter.Tree fellable(Level level, BlockPos machine, TreeCutter.Tree tree) {
		if (tree == null || level == null || !VillageProtection.guarding()) {
			return tree;
		}
		TreeAccessor parts = (TreeAccessor) tree;
		List<BlockPos> logs = keep(level, machine, parts.aliveworkplace$logs());
		List<BlockPos> leaves = keep(level, machine, parts.aliveworkplace$leaves());
		List<BlockPos> attachments = keep(level, machine, parts.aliveworkplace$attachments());
		if (logs == null && leaves == null && attachments == null) {
			return tree;
		}
		return new TreeCutter.Tree(logs != null ? logs : parts.aliveworkplace$logs(), leaves != null ? leaves : parts.aliveworkplace$leaves(),
			attachments != null ? attachments : parts.aliveworkplace$attachments());
	}

	/** The blocks of {@code blocks} the machine may change, or null when that is all of them. */
	private static List<BlockPos> keep(Level level, BlockPos machine, List<BlockPos> blocks) {
		if (blocks == null || blocks.isEmpty()) {
			return null;
		}
		List<BlockPos> kept = blocks.stream().filter(pos -> VillageProtection.mayMachineChange(level, machine, pos)).toList();
		return kept.size() == blocks.size() ? null : new java.util.ArrayList<>(kept);
	}
}
