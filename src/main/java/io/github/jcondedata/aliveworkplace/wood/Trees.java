package io.github.jcondedata.aliveworkplace.wood;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Recognising trees. A tree is a group of touching logs that carries natural leaves (leaves that
 * grew, not ones a player placed) and stands on dirt; log walls, cabins and log piles have no natural
 * leaves, so they are never cut down. Huge nether fungi count too: stems on nylium under a cap of wart
 * blocks (wart blocks have no "placed by a player" mark, so the cap must sit on stems that stand on nylium).
 */
public final class Trees {
	/** Bigger than any vanilla tree (a big jungle tree is ~150 logs); anything larger is not a tree. */
	public static final int MAX_LOGS = 320;
	/** A tree needs at least this many natural leaves touching its logs. */
	private static final int MIN_LEAVES = 4;
	private static final int LEAF_REACH = 6;

	public record Tree(List<BlockPos> logs, List<BlockPos> leaves, List<BlockPos> base, BlockState logState) {
		public BlockPos lowest() {
			return logs.get(0);
		}
	}

	public static boolean isLog(BlockState state) {
		return state.is(BlockTags.LOGS);
	}

	private static boolean isNaturalLeaves(BlockState state) {
		return state.getBlock() instanceof LeavesBlock && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT)
			|| isFungusCap(state);
	}

	/** The "leaves" of a huge fungus: wart blocks and shroomlights. */
	private static boolean isFungusCap(BlockState state) {
		return state.is(BlockTags.WART_BLOCKS) || state.is(Blocks.SHROOMLIGHT);
	}

	/** What a tree can grow on: dirt and grass, or nylium for nether fungi. */
	public static boolean isGround(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.NYLIUM);
	}

	/** A huge fungus's cap spreads up to this far from the stem. */
	private static final int CAP_REACH = 4;

	/** The tree {@code start} belongs to, or empty if it isn't part of a (natural, grown) tree. */
	public static Optional<Tree> treeAt(ServerLevel level, BlockPos start) {
		BlockState startState = level.getBlockState(start);
		if (!isLog(startState)) {
			return Optional.empty();
		}
		Set<BlockPos> logs = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start.immutable());
		logs.add(start.immutable());
		while (!queue.isEmpty()) {
			BlockPos p = queue.poll();
			for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
				if (!logs.contains(n) && isLog(level.getBlockState(n))) {
					if (logs.size() >= MAX_LOGS) {
						return Optional.empty(); // too big to be a tree: a log building
					}
					BlockPos copy = n.immutable();
					logs.add(copy);
					queue.add(copy);
				}
			}
		}
		// Natural leaves reachable from the logs through other leaves.
		Set<BlockPos> leaves = new HashSet<>();
		ArrayDeque<BlockPos> leafQueue = new ArrayDeque<>();
		for (BlockPos log : logs) {
			for (BlockPos n : BlockPos.betweenClosed(log.offset(-1, -1, -1), log.offset(1, 1, 1))) {
				if (!leaves.contains(n) && isNaturalLeaves(level.getBlockState(n))) {
					BlockPos copy = n.immutable();
					leaves.add(copy);
					leafQueue.add(copy);
				}
			}
		}
		if (leaves.size() < MIN_LEAVES) {
			return Optional.empty();
		}
		int stemX = start.getX();
		int stemZ = start.getZ();
		while (!leafQueue.isEmpty() && leaves.size() < 1500) {
			BlockPos p = leafQueue.poll();
			BlockState st = level.getBlockState(p);
			if (isFungusCap(st)) {
				// No distance on wart blocks: follow the cap as far as a huge fungus's reaches from its stem.
				if (Math.abs(p.getX() - stemX) >= CAP_REACH || Math.abs(p.getZ() - stemZ) >= CAP_REACH) {
					continue;
				}
			} else {
				int distance = st.hasProperty(LeavesBlock.DISTANCE) ? st.getValue(LeavesBlock.DISTANCE) : LEAF_REACH;
				if (distance >= LEAF_REACH) {
					continue;
				}
			}
			for (BlockPos n : new BlockPos[]{p.above(), p.below(), p.north(), p.south(), p.east(), p.west()}) {
				if (!leaves.contains(n) && isNaturalLeaves(level.getBlockState(n))) {
					leaves.add(n);
					leafQueue.add(n);
				}
			}
		}
		List<BlockPos> sortedLogs = new ArrayList<>(logs);
		sortedLogs.sort(Comparator.comparingInt(BlockPos::getY));
		int bottom = sortedLogs.get(0).getY();
		List<BlockPos> base = new ArrayList<>();
		for (BlockPos log : sortedLogs) {
			if (log.getY() == bottom && isGround(level.getBlockState(log.below()))) {
				base.add(log);
			}
		}
		if (base.isEmpty()) {
			return Optional.empty(); // not standing on the ground: part of something built
		}
		// Leaves go first, top down, so nothing is left floating; then the logs, top down.
		List<BlockPos> sortedLeaves = new ArrayList<>(leaves);
		sortedLeaves.sort(Comparator.comparingInt((BlockPos p) -> p.getY()).reversed());
		return Optional.of(new Tree(sortedLogs, sortedLeaves, base, level.getBlockState(sortedLogs.get(0))));
	}

	/** The sapling that grows this kind of tree: oak_log → oak_sapling (and modded woods named the same way). */
	@Nullable
	public static Block saplingFor(BlockState log) {
		ResourceLocation id = BuiltInRegistries.BLOCK.getKey(log.getBlock());
		String path = id.getPath().replace("stripped_", "");
		if (path.equals("mangrove_log")) {
			return Blocks.MANGROVE_PROPAGULE;
		}
		if (path.endsWith("_stem")) {
			// crimson_stem → crimson_fungus
			ResourceLocation fungus = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path.substring(0, path.length() - 5) + "_fungus");
			return BuiltInRegistries.BLOCK.getOptional(fungus).orElse(null);
		}
		if (!path.endsWith("_log")) {
			return null;
		}
		ResourceLocation sapling = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path.substring(0, path.length() - 4) + "_sapling");
		return BuiltInRegistries.BLOCK.getOptional(sapling).orElse(null);
	}

	private Trees() {
	}
}
