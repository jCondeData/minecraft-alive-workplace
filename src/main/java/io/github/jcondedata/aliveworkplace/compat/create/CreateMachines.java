package io.github.jcondedata.aliveworkplace.compat.create;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import io.github.jcondedata.aliveworkplace.compat.create.mixin.ControlledContraptionAccessor;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Village protection for Create's machines (B808, B993, B1033): where a machine stands, so {@link VillageProtection#mayMachineChange}
 * can judge the blocks it changes. A drill or saw stands at its own block; a contraption (a piston's, a bearing's, a
 * gantry's) at its controller, or where it was put together when nothing controls it (a minecart's).
 */
public final class CreateMachines {
	/** Where each contraption was put together (the controller's block, given to its {@code assemble}), until its entity exists. */
	private static final Map<Contraption, BlockPos> ASSEMBLED = Collections.synchronizedMap(new WeakHashMap<>());

	private CreateMachines() {
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
}
