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

	/** The stripped kind of a log or wood ({@code oak_log} → {@code stripped_oak_log}), or null. */
	@org.jetbrains.annotations.Nullable
	public static net.minecraft.world.item.Item stripped(net.minecraft.world.item.Item log) {
		if (!(log instanceof net.minecraft.world.item.BlockItem block)) {
			return null;
		}
		net.minecraft.world.level.block.Block to = io.github.jcondedata.aliveworkplace.mixin.AxeItemAccessor.aliveworkplace$strippables().get(block.getBlock());
		return to == null || to.asItem() == net.minecraft.world.item.Items.AIR ? null : to.asItem();
	}

	/** What a stripped log or wood is stripped from ({@code stripped_oak_log} → {@code oak_log}), or null. */
	@org.jetbrains.annotations.Nullable
	public static net.minecraft.world.item.Item unstripped(net.minecraft.world.item.Item stripped) {
		if (!(stripped instanceof net.minecraft.world.item.BlockItem block)) {
			return null;
		}
		for (var e : io.github.jcondedata.aliveworkplace.mixin.AxeItemAccessor.aliveworkplace$strippables().entrySet()) {
			if (e.getValue() == block.getBlock() && e.getKey().asItem() != net.minecraft.world.item.Items.AIR) {
				return e.getKey().asItem();
			}
		}
		return null;
	}

	/**
	 * Calls {@code action} with every log in {@code box} (loaded chunks only). Chunk sections with no log in them at all
	 * are skipped without looking at their blocks, which makes this cheap in open country.
	 */
	public static void forEachLog(net.minecraft.server.level.ServerLevel level, net.minecraft.world.level.levelgen.structure.BoundingBox box,
			java.util.function.Consumer<net.minecraft.core.BlockPos> action) {
		int minY = Math.max(box.minY(), level.getMinBuildHeight());
		int maxY = Math.min(box.maxY(), level.getMaxBuildHeight() - 1);
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				if (!level.hasChunk(cx, cz)) {
					continue;
				}
				net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(cx, cz);
				for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
					net.minecraft.world.level.chunk.LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
					if (section.hasOnlyAir() || !section.maybeHas(Trees::isLog)) {
						continue;
					}
					int x0 = Math.max(box.minX(), cx << 4), x1 = Math.min(box.maxX(), (cx << 4) + 15);
					int z0 = Math.max(box.minZ(), cz << 4), z1 = Math.min(box.maxZ(), (cz << 4) + 15);
					int y0 = Math.max(minY, sy << 4), y1 = Math.min(maxY, (sy << 4) + 15);
					for (int y = y0; y <= y1; y++) {
						for (int z = z0; z <= z1; z++) {
							for (int x = x0; x <= x1; x++) {
								if (isLog(section.getBlockState(x & 15, y & 15, z & 15))) {
									action.accept(new net.minecraft.core.BlockPos(x, y, z));
								}
							}
						}
					}
				}
			}
		}
	}

	private static boolean isNaturalLeaves(BlockState state) {
		return state.getBlock() instanceof LeavesBlock && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT)
			|| isFungusCap(state);
	}

	/** The "leaves" of a huge fungus: wart blocks and shroomlights. */
	private static boolean isFungusCap(BlockState state) {
		return state.is(BlockTags.WART_BLOCKS) || state.is(Blocks.SHROOMLIGHT);
	}

	/** What a tree can grow on: dirt, grass and mud, nylium for nether fungi, and a mangrove's own roots. */
	public static boolean isGround(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.NYLIUM) || state.is(Blocks.MANGROVE_ROOTS);
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

	/** The sapling that grows this tree: an azalea for an azalea tree (oak logs, azalea leaves), else {@link #saplingFor(BlockState)}. */
	@Nullable
	public static Block saplingFor(ServerLevel level, Tree tree) {
		for (BlockPos leaf : tree.leaves()) {
			BlockState state = level.getBlockState(leaf);
			if (state.is(Blocks.AZALEA_LEAVES) || state.is(Blocks.FLOWERING_AZALEA_LEAVES)) {
				return Blocks.AZALEA;
			}
		}
		return saplingFor(tree.logState());
	}

	/**
	 * Where to plant the new tree once {@code tree} is down: where its trunk stood, or if nothing grows there (a
	 * mangrove stands on its roots) the nearest spot close by that takes the sapling — in the water over mud, for a
	 * mangrove propagule. A 2 × 2 trunk is replanted as four or not at all.
	 */
	public static List<BlockPos> replantSpots(ServerLevel level, Tree tree, Block sapling) {
		List<BlockPos> spots = new ArrayList<>();
		for (BlockPos base : tree.base()) {
			if (canPlant(level, base, sapling)) {
				spots.add(base);
			}
		}
		if (!spots.isEmpty() || tree.base().size() > 1) {
			return spots;
		}
		BlockPos base = tree.base().get(0);
		BlockPos best = null;
		for (BlockPos p : BlockPos.betweenClosed(base.offset(-REPLANT_REACH, -5, -REPLANT_REACH), base.offset(REPLANT_REACH, 0, REPLANT_REACH))) {
			if ((best == null || p.distSqr(base) < best.distSqr(base)) && canPlant(level, p, sapling)) {
				best = p.immutable();
			}
		}
		return best == null ? List.of() : List.of(best);
	}

	/** How far from the old trunk a sapling may go when the trunk's own spot won't take one. */
	private static final int REPLANT_REACH = 3;

	/** Whether {@code sapling} can go in at {@code pos}: air (or still water, for one that can stand in it) over ground it grows on. */
	public static boolean canPlant(ServerLevel level, BlockPos pos, Block sapling) {
		BlockState at = level.getBlockState(pos);
		BlockState plant = sapling.defaultBlockState();
		boolean water = at.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock && at.getFluidState().isSource()
			&& at.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
		if (!at.isAir() && !(water && plant.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED))) {
			return false;
		}
		return plant.canSurvive(level, pos);
	}

	/** The block to set when planting {@code sapling} at {@code pos} (waterlogged in water). */
	public static BlockState plantState(ServerLevel level, BlockPos pos, Block sapling) {
		BlockState plant = sapling.defaultBlockState();
		if (plant.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED) && !level.getFluidState(pos).isEmpty()) {
			plant = plant.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true);
		}
		return plant;
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
