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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reads Sponge schematics ({@code .schem}), the format WorldEdit and most build sites use.
 * Versions 1-2 keep everything at the root; version 3 nests blocks under {@code Schematic.Blocks}.
 * Block indices are varint-encoded, ordered x, then z, then y.
 */
final class SpongeSchematicReader {
	/** Data version of Minecraft 1.13, assumed for v1 files that do not say. */
	private static final int V1_DATA_VERSION = 1519;

	static boolean looksLike(CompoundTag root) {
		CompoundTag s = unwrap(root);
		return s.contains("Width") && s.contains("Height") && s.contains("Length")
			&& (Nbt.has(s, "Palette", Tag.TAG_COMPOUND) || Nbt.has(s, "Blocks", Tag.TAG_COMPOUND));
	}

	static CompoundTag unwrap(CompoundTag root) {
		return Nbt.has(root, "Schematic", Tag.TAG_COMPOUND) ? Nbt.getCompound(root, "Schematic") : root;
	}

	static int dataVersion(CompoundTag root) {
		CompoundTag s = unwrap(root);
		return s.contains("DataVersion") ? Nbt.getInt(s, "DataVersion") : V1_DATA_VERSION;
	}

	static Blueprint read(ResourceLocation id, CompoundTag root, BlockStateReader states, long maxVolume) throws BlueprintFormatException {
		CompoundTag s = unwrap(root);
		int version = Nbt.getInt(s, "Version");
		int width = Nbt.getShort(s, "Width") & 0xFFFF;
		int height = Nbt.getShort(s, "Height") & 0xFFFF;
		int length = Nbt.getShort(s, "Length") & 0xFFFF;
		Vec3i size = new Vec3i(width, height, length);
		BlueprintFiles.checkVolume(size, maxVolume);

		CompoundTag paletteTag;
		byte[] data;
		ListTag blockEntities;
		boolean nested = version >= 3 || Nbt.has(s, "Blocks", Tag.TAG_COMPOUND);
		if (nested) {
			CompoundTag blocks = Nbt.getCompound(s, "Blocks");
			paletteTag = Nbt.getCompound(blocks, "Palette");
			data = Nbt.getByteArray(blocks, "Data");
			blockEntities = Nbt.getList(blocks, "BlockEntities", Tag.TAG_COMPOUND);
		} else {
			paletteTag = Nbt.getCompound(s, "Palette");
			data = Nbt.getByteArray(s, "BlockData");
			blockEntities = s.contains("BlockEntities") ? Nbt.getList(s, "BlockEntities", Tag.TAG_COMPOUND) : Nbt.getList(s, "TileEntities", Tag.TAG_COMPOUND);
		}

		int paletteSize = 0;
		for (String key : Nbt.keys(paletteTag)) {
			paletteSize = Math.max(paletteSize, Nbt.getInt(paletteTag, key) + 1);
		}
		BlockState[] palette = new BlockState[paletteSize];
		String[] unknown = new String[paletteSize];
		for (String key : Nbt.keys(paletteTag)) {
			int i = Nbt.getInt(paletteTag, key);
			if (i < 0) {
				throw new BlueprintFormatException("schem.corrupt");
			}
			palette[i] = states.fromString(key);
			unknown[i] = states.lastUnknown();
		}

		Map<BlockPos, CompoundTag> tiles = new HashMap<>();
		for (int i = 0; i < blockEntities.size(); i++) {
			CompoundTag be = Nbt.compoundAt(blockEntities, i);
			int[] pos = Nbt.getIntArray(be, "Pos");
			if (pos.length != 3) {
				continue;
			}
			CompoundTag nbt;
			if (Nbt.has(be, "Data", Tag.TAG_COMPOUND)) {
				nbt = Nbt.getCompound(be, "Data").copy(); // v3
			} else {
				nbt = be.copy(); // v1/v2: data lives next to Pos and Id
				nbt.remove("Pos");
				nbt.remove("Id");
			}
			if (be.contains("Id")) {
				nbt.putString("id", Nbt.getString(be, "Id"));
			}
			tiles.put(new BlockPos(pos[0], pos[1], pos[2]), nbt);
		}

		List<Blueprint.Entry> entries = new ArrayList<>(width * height * length);
		int cursor = 0;
		long total = (long) width * height * length;
		for (long index = 0; index < total; index++) {
			// Read one unsigned varint.
			int value = 0;
			int shift = 0;
			while (true) {
				if (cursor >= data.length) {
					throw new BlueprintFormatException("schem.corrupt");
				}
				byte b = data[cursor++];
				value |= (b & 0x7F) << shift;
				if ((b & 0x80) == 0) {
					break;
				}
				shift += 7;
				if (shift > 28) {
					throw new BlueprintFormatException("schem.corrupt");
				}
			}
			int y = (int) (index / ((long) width * length));
			int rest = (int) (index % ((long) width * length));
			int z = rest / width;
			int x = rest % width;
			BlockState state = value < palette.length && palette[value] != null ? palette[value] : Blocks.AIR.defaultBlockState();
			if (value < palette.length && unknown[value] != null) {
				states.unknownBlock(unknown[value]);
			}
			BlockPos pos = new BlockPos(x, y, z);
			CompoundTag nbt = tiles.get(pos);
			nbt = nbt != null && state.hasBlockEntity() ? BlueprintFiles.withBlockEntityId(states.fixBlockEntity(nbt), state) : null;
			entries.add(new Blueprint.Entry(pos, BlueprintFiles.normalizeAir(state), nbt));
		}
		List<Blueprint.EntityEntry> entities = new ArrayList<>();
		ListTag entityList = Nbt.getList(s, "Entities", Tag.TAG_COMPOUND);
		for (int i = 0; i < entityList.size(); i++) {
			CompoundTag e = Nbt.compoundAt(entityList, i);
			ListTag p = Nbt.getList(e, "Pos", Tag.TAG_DOUBLE);
			CompoundTag entityData;
			if (Nbt.has(e, "Data", Tag.TAG_COMPOUND)) {
				entityData = Nbt.getCompound(e, "Data").copy(); // v3
			} else {
				entityData = e.copy(); // v2: data next to Pos and Id
				entityData.remove("Pos");
				entityData.remove("Id");
			}
			if (e.contains("Id")) {
				entityData.putString("id", Nbt.getString(e, "Id"));
			}
			CompoundTag clean = io.github.jcondedata.aliveworkplace.blueprint.BlueprintEntities.clean(states.fixEntity(entityData));
			if (p.size() == 3 && clean != null) {
				entities.add(new Blueprint.EntityEntry(new net.minecraft.world.phys.Vec3(Nbt.doubleAt(p, 0), Nbt.doubleAt(p, 1), Nbt.doubleAt(p, 2)), clean));
			}
		}
		return new Blueprint(id, size, List.copyOf(entries), List.copyOf(entities));
	}

	private SpongeSchematicReader() {
	}
}
