package io.github.jcondedata.aliveworkplace.blueprint.io;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reads Litematica's {@code .litematic} format (versions 4-7): one or more regions, each with a block
 * palette and a bit-packed long array of palette indices. Regions are merged into one blueprint.
 */
final class LitematicReader {
	static boolean looksLike(CompoundTag root) {
		return Nbt.has(root, "Regions", Tag.TAG_COMPOUND);
	}

	static Blueprint read(ResourceLocation id, CompoundTag root, BlockStateReader states, long maxVolume) throws BlueprintFormatException {
		CompoundTag regions = Nbt.getCompound(root, "Regions");
		if (regions.isEmpty()) {
			throw new BlueprintFormatException("litematic.empty");
		}

		// First pass: bounds of every region in schematic space.
		record Region(String name, CompoundTag tag, BlockPos position, BlockPos min, Vec3i size) {
		}
		List<Region> list = new ArrayList<>();
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (String name : Nbt.keys(regions)) {
			CompoundTag r = Nbt.getCompound(regions, name);
			BlockPos pos = readPos(Nbt.getCompound(r, "Position"));
			BlockPos rawSize = readPos(Nbt.getCompound(r, "Size"));
			// Negative sizes mean the region extends in the negative direction from Position.
			int sx = Math.abs(rawSize.getX()), sy = Math.abs(rawSize.getY()), sz = Math.abs(rawSize.getZ());
			BlockPos min = new BlockPos(
				rawSize.getX() < 0 ? pos.getX() + rawSize.getX() + 1 : pos.getX(),
				rawSize.getY() < 0 ? pos.getY() + rawSize.getY() + 1 : pos.getY(),
				rawSize.getZ() < 0 ? pos.getZ() + rawSize.getZ() + 1 : pos.getZ());
			Region region = new Region(name, r, pos, min, new Vec3i(sx, sy, sz));
			list.add(region);
			minX = Math.min(minX, min.getX());
			minY = Math.min(minY, min.getY());
			minZ = Math.min(minZ, min.getZ());
			maxX = Math.max(maxX, min.getX() + sx - 1);
			maxY = Math.max(maxY, min.getY() + sy - 1);
			maxZ = Math.max(maxZ, min.getZ() + sz - 1);
		}
		Vec3i size = new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
		BlueprintFiles.checkVolume(size, maxVolume);

		Map<BlockPos, Blueprint.Entry> entries = new HashMap<>();
		List<Blueprint.EntityEntry> entities = new ArrayList<>();
		for (Region region : list) {
			CompoundTag r = region.tag();
			// Entity positions are relative to the region's Position corner.
			ListTag entityList = Nbt.getList(r, "Entities", Tag.TAG_COMPOUND);
			for (int i = 0; i < entityList.size(); i++) {
				CompoundTag e = states.fixEntity(Nbt.compoundAt(entityList, i));
				ListTag p = Nbt.getList(e, "Pos", Tag.TAG_DOUBLE);
				CompoundTag clean = io.github.jcondedata.aliveworkplace.blueprint.BlueprintEntities.clean(e);
				if (p.size() == 3 && clean != null) {
					entities.add(new Blueprint.EntityEntry(new net.minecraft.world.phys.Vec3(
						Nbt.doubleAt(p, 0) + region.position().getX() - minX, Nbt.doubleAt(p, 1) + region.position().getY() - minY,
						Nbt.doubleAt(p, 2) + region.position().getZ() - minZ), clean));
				}
			}
			ListTag paletteTag = Nbt.getList(r, "BlockStatePalette", Tag.TAG_COMPOUND);
			BlockState[] palette = new BlockState[paletteTag.size()];
			for (int i = 0; i < palette.length; i++) {
				palette[i] = states.fromCompound(Nbt.compoundAt(paletteTag, i));
			}
			long[] data = Nbt.getLongArray(r, "BlockStates");
			int sx = region.size().getX(), sy = region.size().getY(), sz = region.size().getZ();
			int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, palette.length - 1)));
			long volume = (long) sx * sy * sz;
			if (data.length * 64L < volume * bits) {
				throw new BlueprintFormatException("litematic.corrupt");
			}
			BlockPos offset = region.min().offset(-minX, -minY, -minZ);

			Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();
			ListTag tes = Nbt.getList(r, "TileEntities", Tag.TAG_COMPOUND);
			for (int i = 0; i < tes.size(); i++) {
				CompoundTag te = Nbt.compoundAt(tes, i).copy();
				BlockPos p = new BlockPos(Nbt.getInt(te, "x"), Nbt.getInt(te, "y"), Nbt.getInt(te, "z"));
				te.remove("x");
				te.remove("y");
				te.remove("z");
				blockEntities.put(p, te);
			}

			for (int y = 0; y < sy; y++) {
				for (int z = 0; z < sz; z++) {
					for (int x = 0; x < sx; x++) {
						long index = (long) y * sx * sz + (long) z * sx + x;
						int paletteIndex = (int) get(data, index, bits);
						BlockState state = paletteIndex < palette.length ? palette[paletteIndex] : palette[0];
						BlockPos local = new BlockPos(x, y, z);
						CompoundTag nbt = blockEntities.get(local);
						if (nbt != null && state.hasBlockEntity()) {
							nbt = BlueprintFiles.withBlockEntityId(states.fixBlockEntity(nbt), state);
						} else {
							nbt = null;
						}
						BlockPos pos = local.offset(offset);
						entries.put(pos, new Blueprint.Entry(pos, BlueprintFiles.normalizeAir(state), nbt));
					}
				}
			}
		}
		return new Blueprint(id, size, List.copyOf(entries.values()), List.copyOf(entities));
	}

	/** Litematica's packed array: entries are {@code bits} wide and may straddle two longs. */
	static long get(long[] data, long index, int bits) {
		long mask = (1L << bits) - 1L;
		long startOffset = index * bits;
		int startArr = (int) (startOffset >> 6);
		int endArr = (int) (((index + 1L) * bits - 1L) >> 6);
		int startBit = (int) (startOffset & 0x3F);
		if (startArr == endArr) {
			return data[startArr] >>> startBit & mask;
		}
		int endOffset = 64 - startBit;
		return (data[startArr] >>> startBit | data[endArr] << endOffset) & mask;
	}

	private static BlockPos readPos(CompoundTag tag) {
		return new BlockPos(Nbt.getInt(tag, "x"), Nbt.getInt(tag, "y"), Nbt.getInt(tag, "z"));
	}

	private LitematicReader() {
	}
}
