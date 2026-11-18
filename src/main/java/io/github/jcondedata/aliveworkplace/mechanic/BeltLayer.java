package io.github.jcondedata.aliveworkplace.mechanic;

import io.github.jcondedata.aliveworkplace.work.Extension;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * How the Mechanic lays a belt again (B669): as a player does, with one belt connector between its two pulley shafts,
 * through Create's own belt laying ({@code compat/create}). A belt is never put back segment by segment: a lone segment
 * isn't a belt, and Create breaks it into a belt connector. Without a filling (no Create) no belt is ever mended.
 */
public interface BeltLayer {
	Extension<BeltLayer> EXTENSION = new Extension<>("laying Create belts");

	/**
	 * The belts among a build's belt segments ({@code segments}: where each stands in the world, and its state there),
	 * each as its segments in order from the end it starts at to the end it ends at.
	 */
	List<List<BlockPos>> runs(Map<BlockPos, BlockState> segments);

	/** The shaft a belt's end turns on ({@code belt}: the end segment's state), or null if it has none. */
	@Nullable
	BlockState pulley(BlockState belt);

	/** Whether a belt can be laid from the shaft at {@code start} to the shaft at {@code end} now, as a player would. */
	boolean canLay(ServerLevel level, BlockPos start, BlockPos end);

	/** Lays the belt from the shaft at {@code start} to the shaft at {@code end}; false if it can't be laid now. */
	boolean lay(ServerLevel level, BlockPos start, BlockPos end);
}
