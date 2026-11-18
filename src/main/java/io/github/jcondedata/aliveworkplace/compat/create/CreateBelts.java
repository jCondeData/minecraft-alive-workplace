package io.github.jcondedata.aliveworkplace.compat.create;

import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import io.github.jcondedata.aliveworkplace.mechanic.BeltLayer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * B669: the Mechanic lays a broken belt again the way a player does with a belt connector, through Create's own
 * {@link BeltConnectorItem#canConnect} and {@link BeltConnectorItem#createBelts}, never one segment at a time.
 */
final class CreateBelts implements BeltLayer {
	private static final ResourceLocation SHAFT = ResourceLocation.fromNamespaceAndPath("create", "shaft");

	@Override
	public List<List<BlockPos>> runs(Map<BlockPos, BlockState> segments) {
		List<List<BlockPos>> out = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		for (Map.Entry<BlockPos, BlockState> e : segments.entrySet()) {
			if (seen.contains(e.getKey()) || !(e.getValue().getBlock() instanceof BeltBlock)) {
				continue;
			}
			// Walk both ways along the belt from this segment (its part ignored: a flipped build turns a belt end for end)
			Deque<BlockPos> run = new ArrayDeque<>();
			run.add(e.getKey());
			seen.add(e.getKey());
			for (boolean forward : new boolean[] {true, false}) {
				BlockPos at = e.getKey();
				while (true) {
					BlockPos next = step(segments.get(at), at, forward);
					if (next == null || seen.contains(next) || !(segments.get(next) != null && segments.get(next).getBlock() instanceof BeltBlock)) {
						break;
					}
					seen.add(next);
					if (forward) {
						run.addLast(next);
					} else {
						run.addFirst(next);
					}
					at = next;
				}
			}
			if (run.size() >= 2) {
				out.add(List.copyOf(run)); // first: the end the belt's facing leads away from, as Create lays it
			}
		}
		return out;
	}

	@Nullable
	private static BlockPos step(BlockState state, BlockPos pos, boolean forward) {
		return BeltBlock.nextSegmentPosition(state.setValue(BeltBlock.PART, BeltPart.MIDDLE), pos, forward);
	}

	@Override
	@Nullable
	public BlockState pulley(BlockState belt) {
		if (!(belt.getBlock() instanceof BeltBlock block) || !BuiltInRegistries.BLOCK.containsKey(SHAFT)) {
			return null;
		}
		Block shaft = BuiltInRegistries.BLOCK.get(SHAFT);
		BlockState state = shaft.defaultBlockState();
		return state.hasProperty(BlockStateProperties.AXIS) ? state.setValue(BlockStateProperties.AXIS, block.getRotationAxis(belt)) : null;
	}

	@Override
	public boolean canLay(ServerLevel level, BlockPos start, BlockPos end) {
		return BeltConnectorItem.canConnect(level, start, end);
	}

	@Override
	public boolean lay(ServerLevel level, BlockPos start, BlockPos end) {
		if (!BeltConnectorItem.canConnect(level, start, end)) {
			return false;
		}
		BeltConnectorItem.createBelts(level, start, end);
		return true;
	}
}
