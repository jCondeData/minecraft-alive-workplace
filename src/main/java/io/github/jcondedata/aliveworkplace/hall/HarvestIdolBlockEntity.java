package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Harvest Idol's place in {@link HarvestIdols}' set while its chunk is loaded: the chunk gives a block entity its level
 * when it loads (or the idol is placed) and removes it when it unloads (or the idol is broken). Nothing is saved.
 */
public class HarvestIdolBlockEntity extends BlockEntity {
	public HarvestIdolBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.HARVEST_IDOL_ENTITY, pos, state);
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		if (level instanceof ServerLevel server && !isRemoved()) {
			HarvestIdols.loaded(server, getBlockPos(), this);
		}
	}

	@Override
	public void clearRemoved() {
		super.clearRemoved();
		if (level instanceof ServerLevel server) {
			HarvestIdols.loaded(server, getBlockPos(), this);
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if (level instanceof ServerLevel server) {
			HarvestIdols.unloaded(server, getBlockPos(), this);
		}
	}
}
