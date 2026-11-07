package io.github.jcondedata.aliveworkplace.threat;

import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The ground under a camp, before it was set down (B225). Before, a camp taken down (B209) left its levelled pad and its
 * patch of trampled earth and gravel for good, so camps coming and going scarred the land. Now a lair on land records the
 * ground it covers when it is made ({@link #take}: its box, and {@link #DEPTH} blocks under it where the camp fills the
 * hollows with dirt), saved with the lair and then with its clearing; once the camp is down that ground is put back
 * ({@link #restore}). Only where the camp's doing is still there: air, the camp's own ground blocks, its dirt filling
 * and floor (even once grass has crept over them, B288), fire, or plants grown since. A block a player set down, and every block with contents, stays.
 *
 * <p>A camp made before this fix has no record: its pad is blended back into the land round it instead ({@link #blend}):
 * each column raised or lowered towards the height the ground round the camp has there, topped with that ground's own
 * surface (grass, sand, snow), with no trampled earth or gravel left.
 *
 * <p>Left as ruins ({@code campRuins}), the tents and fire stay, so only the open ground round them goes back.
 */
public record CampGround(BlockPos min, int sizeX, int sizeY, int sizeZ, int rotation, List<BlockState> palette, int[] cells) {
	/** How far under a camp's floor it is recorded (the camp fills up to four blocks of hollow under it). */
	public static final int DEPTH = 4;
	/** How far round a camp's footprint the land is looked at to blend an unrecorded pad back. */
	static final int RING = 3;

	/** The ground in {@code box} (a camp's, before it is set down) and {@link #DEPTH} under it; {@code rotation} the camp's turn. */
	public static CampGround take(ServerLevel level, BoundingBox box, int rotation) {
		BlockPos min = new BlockPos(box.minX(), box.minY() - DEPTH, box.minZ());
		int sx = box.getXSpan();
		int sy = box.getYSpan() + DEPTH;
		int sz = box.getZSpan();
		List<BlockState> palette = new ArrayList<>();
		Map<BlockState, Integer> index = new HashMap<>();
		int[] cells = new int[sx * sy * sz];
		for (int y = 0; y < sy; y++) {
			for (int z = 0; z < sz; z++) {
				for (int x = 0; x < sx; x++) {
					BlockPos p = min.offset(x, y, z);
					BlockState s = level.getBlockState(p);
					if (s.hasBlockEntity()) {
						s = Blocks.AIR.defaultBlockState(); // (its contents can't come back: nothing is made up)
					}
					BlockState state = s;
					cells[(y * sz + z) * sx + x] = index.computeIfAbsent(state, k -> {
						palette.add(k);
						return palette.size() - 1;
					});
				}
			}
		}
		return new CampGround(min.immutable(), sx, sy, sz, rotation, List.copyOf(palette), cells);
	}

	/** The recorded state at {@code p}, or null outside the record. */
	@Nullable
	BlockState at(BlockPos p) {
		int x = p.getX() - min.getX();
		int y = p.getY() - min.getY();
		int z = p.getZ() - min.getZ();
		if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
			return null;
		}
		int i = cells[(y * sizeZ + z) * sizeX + x];
		return i >= 0 && i < palette.size() ? palette.get(i) : null;
	}

	/** The camp's floor (the bottom layer of its blueprint). */
	int floor() {
		return min.getY() + DEPTH;
	}

	CompoundTag write() {
		CompoundTag c = new CompoundTag();
		c.putLong("min", min.asLong());
		c.putInt("x", sizeX);
		c.putInt("y", sizeY);
		c.putInt("z", sizeZ);
		c.putInt("rotation", rotation);
		ListTag states = new ListTag();
		for (BlockState s : palette) {
			states.add(NbtUtils.writeBlockState(s));
		}
		c.put("palette", states);
		c.putIntArray("cells", cells);
		return c;
	}

	/** A record read back; null when it is damaged (the camp's pad is then blended instead). */
	@Nullable
	static CampGround read(CompoundTag c) {
		int sx = Nbt.getInt(c, "x");
		int sy = Nbt.getInt(c, "y");
		int sz = Nbt.getInt(c, "z");
		int[] cells = Nbt.getIntArray(c, "cells");
		if (sx <= 0 || sy <= 0 || sz <= 0 || sx > 64 || sy > 64 || sz > 64 || cells.length != sx * sy * sz) {
			return null;
		}
		ListTag states = Nbt.getList(c, "palette", Tag.TAG_COMPOUND);
		List<BlockState> palette = new ArrayList<>();
		for (int i = 0; i < states.size(); i++) {
			palette.add(NbtUtils.readBlockState(Lookup.lookup(BuiltInRegistries.BLOCK), Nbt.compoundAt(states, i)));
		}
		int rotation = Nbt.getInt(c, "rotation");
		return new CampGround(BlockPos.of(Nbt.getLong(c, "min")), sx, sy, sz, rotation < 0 || rotation > 3 ? 0 : rotation, List.copyOf(palette), cells);
	}

	// Putting it back.

	/**
	 * Puts the recorded ground back, bottom up, wherever the camp's doing is still there ({@code camp}: the camp's blocks
	 * by where they went; {@code keep}: columns left alone, a ruined camp's standing ones). Anyone inside ground that came
	 * back is lifted onto it.
	 */
	public void restore(ServerLevel level, Map<BlockPos, BlockState> camp, java.util.Set<Long> keep) {
		int floor = floor();
		for (int y = 0; y < sizeY; y++) {
			for (int z = 0; z < sizeZ; z++) {
				for (int x = 0; x < sizeX; x++) {
					BlockPos p = min.offset(x, y, z);
					if (keep.contains(column(p)) || !level.isLoaded(p)) {
						continue;
					}
					BlockState was = at(p);
					BlockState now = level.getBlockState(p);
					if (was == null || now.equals(was) || level.getBlockEntity(p) != null) {
						continue; // (a chest, a player's or the camp's, keeps its contents)
					}
					BlockState placed = camp.get(p);
					boolean ours = now.isAir() || fire(now) || placed != null && MaterialRules.matches(now, placed)
						// the camp's filling and its floor, as the dirt it laid or as grass (or mycelium) crept over it while
						// the camp stood (B288: a grassy rise came back a block too high where grass had crept over the fill)
						|| p.getY() <= floor && now.is(BlockTags.DIRT)
						|| now.canBeReplaced() && now.getFluidState().isEmpty(); // grass or flowers grown since
					if (ours) {
						level.setBlock(p, was, Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		lift(level, new BoundingBox(min.getX(), min.getY(), min.getZ(), min.getX() + sizeX - 1, min.getY() + sizeY - 1, min.getZ() + sizeZ - 1));
	}

	/**
	 * Blends the pad of a camp with no record back into the land round it ({@code box}: the camp's box; {@code camp} and
	 * {@code keep} as for {@link #restore}): each open column goes to the height the land round the camp has there, topped
	 * with that land's surface, its trampled earth, gravel and fire gone.
	 */
	static void blend(ServerLevel level, BoundingBox box, Map<BlockPos, BlockState> camp, java.util.Set<Long> keep) {
		int floor = box.minY();
		// The land round the camp: its natural columns' heights, and the surface most of them have.
		List<int[]> ring = new ArrayList<>();
		Map<BlockState, Integer> surfaces = new HashMap<>();
		for (int x = box.minX() - RING; x <= box.maxX() + RING; x++) {
			for (int z = box.minZ() - RING; z <= box.maxZ() + RING; z++) {
				if (x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ()) {
					continue;
				}
				BlockPos column = new BlockPos(x, floor, z);
				if (!level.isLoaded(column)) {
					continue;
				}
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
				BlockState s = level.getBlockState(top);
				if (!natural(s) || Math.abs(top.getY() - floor) > box.getYSpan() + DEPTH) {
					continue; // (a wall, a tree trunk, a cliff: not the lie of the land)
				}
				ring.add(new int[] {x, z, top.getY()});
				surfaces.merge(s, 1, Integer::sum);
			}
		}
		BlockState surface = surfaces.entrySet().stream().filter(e -> surface(e.getKey())).max(Map.Entry.comparingByValue())
			.map(Map.Entry::getKey).orElse(Blocks.GRASS_BLOCK.defaultBlockState());
		BlockState fill = surface.is(BlockTags.SAND) ? surface : surface.is(Blocks.SNOW_BLOCK) ? surface : Blocks.DIRT.defaultBlockState();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				if (keep.contains(BlockPos.asLong(x, 0, z)) || !level.isLoaded(new BlockPos(x, floor, z))) {
					continue;
				}
				int target = Math.max(floor - DEPTH + 1, Math.min(box.maxY(), height(ring, x, z, floor)));
				// Above the land's height: the camp's ground and filling go, never a player's block.
				for (int y = box.maxY(); y > target; y--) {
					BlockPos p = new BlockPos(x, y, z);
					BlockState now = level.getBlockState(p);
					if (level.getBlockEntity(p) != null) {
						continue;
					}
					BlockState placed = camp.get(p);
					if (fire(now) || placed != null && MaterialRules.matches(now, placed) && y <= floor
						|| y <= floor && (now.is(BlockTags.DIRT) || now.is(Blocks.DIRT_PATH) || now.is(Blocks.GRAVEL))) {
						level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
				// Up to it: filled, the top its surface; the camp's trampled ground turned back to land.
				for (int y = floor - DEPTH + 1; y <= target; y++) {
					BlockPos p = new BlockPos(x, y, z);
					BlockState now = level.getBlockState(p);
					if (level.getBlockEntity(p) != null) {
						continue;
					}
					BlockState placed = camp.get(p);
					BlockState want = y == target ? surface : fill;
					boolean open = now.isAir() || fire(now) || now.canBeReplaced() && now.getFluidState().isEmpty();
					boolean ours = placed != null && MaterialRules.matches(now, placed) || now.is(Blocks.DIRT_PATH) || now.is(Blocks.COARSE_DIRT)
						|| now.is(Blocks.GRAVEL) && y >= floor || now.is(Blocks.GRASS_BLOCK) && y < target
						|| y == target && now.is(BlockTags.DIRT); // (ground the pad was cut into: its top turned to the land's surface)
					if ((open && y >= floor || ours) && !now.equals(want)) {
						level.setBlock(p, want, Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		lift(level, box);
	}

	/** The land's height at a column of the pad: the heights round it, the nearer the more; the floor if none. */
	private static int height(List<int[]> ring, int x, int z, int floor) {
		if (ring.isEmpty()) {
			return floor;
		}
		double sum = 0;
		double weights = 0;
		for (int[] r : ring) {
			double d = (r[0] - x) * (r[0] - x) + (r[1] - z) * (r[1] - z);
			double w = 1.0 / d;
			sum += r[2] * w;
			weights += w;
		}
		return (int) Math.round(sum / weights);
	}

	/** Ground the world made: earth, sand, stone, snow. */
	static boolean natural(BlockState s) {
		return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.GRAVEL) || s.is(BlockTags.TERRACOTTA)
			|| s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.CLAY);
	}

	/** A land's top: grass, podzol, mycelium, sand, snow (not the camp's trampled earth). */
	private static boolean surface(BlockState s) {
		return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM) || s.is(BlockTags.SAND) || s.is(Blocks.SNOW_BLOCK)
			|| s.is(Blocks.MUD) || s.is(Blocks.MOSS_BLOCK);
	}

	private static boolean fire(BlockState s) {
		return s.is(BlockTags.FIRE);
	}

	static long column(BlockPos p) {
		return BlockPos.asLong(p.getX(), 0, p.getZ());
	}

	/**
	 * Anyone (or any item) inside ground that came back is set on top of it: any whose box the ground now overlaps, not
	 * only those whose feet are in it (B702: a diamond lying across a column's edge, half in a block that came back, was
	 * left there, and vanilla's push out of blocks let it sink into the ground under it).
	 */
	private static void lift(ServerLevel level, BoundingBox box) {
		AABB area = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
		for (Entity e : level.getEntitiesOfClass(Entity.class, area)) {
			AABB b = e.getBoundingBox();
			if (!buried(level, e, b)) {
				continue;
			}
			int top = level.getMinBuildHeight();
			for (int x = (int) Math.floor(b.minX); x <= (int) Math.floor(b.maxX); x++) {
				for (int z = (int) Math.floor(b.minZ); z <= (int) Math.floor(b.maxZ); z++) {
					top = Math.max(top, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
				}
			}
			// The first block height up where it is free, at most the land's top over every column it covers.
			double y = Math.floor(e.getY()) + 1;
			while (y < top && buried(level, e, b.move(0, y - e.getY(), 0))) {
				y++;
			}
			e.teleportTo(e.getX(), Math.min(y, top), e.getZ());
			e.setDeltaMovement(e.getDeltaMovement().multiply(1, 0, 1));
		}
	}

	/** Whether blocks overlap {@code box} (touching them is not overlapping, as vanilla's own push out of blocks). */
	private static boolean buried(ServerLevel level, Entity e, AABB box) {
		return level.getBlockCollisions(e, box.deflate(1.0E-7)).iterator().hasNext();
	}
}
