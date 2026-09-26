package io.github.jcondedata.aliveworkplace.blueprint.io;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
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
			&& (s.contains("Palette", Tag.TAG_COMPOUND) || s.contains("Blocks", Tag.TAG_COMPOUND));
	}

	static CompoundTag unwrap(CompoundTag root) {
		return root.contains("Schematic", Tag.TAG_COMPOUND) ? root.getCompound("Schematic") : root;
	}

	static int dataVersion(CompoundTag root) {
		CompoundTag s = unwrap(root);
		return s.contains("DataVersion") ? s.getInt("DataVersion") : V1_DATA_VERSION;
	}

	static Blueprint read(ResourceLocation id, CompoundTag root, BlockStateReader states, long maxVolume) throws BlueprintFormatException {
		CompoundTag s = unwrap(root);
		int version = s.getInt("Version");
		int width = s.getShort("Width") & 0xFFFF;
		int height = s.getShort("Height") & 0xFFFF;
		int length = s.getShort("Length") & 0xFFFF;
		Vec3i size = new Vec3i(width, height, length);
		BlueprintFiles.checkVolume(size, maxVolume);

		CompoundTag paletteTag;
		byte[] data;
		ListTag blockEntities;
		boolean nested = version >= 3 || s.contains("Blocks", Tag.TAG_COMPOUND);
		if (nested) {
			CompoundTag blocks = s.getCompound("Blocks");
			paletteTag = blocks.getCompound("Palette");
			data = blocks.getByteArray("Data");
			blockEntities = blocks.getList("BlockEntities", Tag.TAG_COMPOUND);
		} else {
			paletteTag = s.getCompound("Palette");
			data = s.getByteArray("BlockData");
			blockEntities = s.contains("BlockEntities") ? s.getList("BlockEntities", Tag.TAG_COMPOUND) : s.getList("TileEntities", Tag.TAG_COMPOUND);
		}

		int paletteSize = 0;
		for (String key : paletteTag.getAllKeys()) {
			paletteSize = Math.max(paletteSize, paletteTag.getInt(key) + 1);
		}
		BlockState[] palette = new BlockState[paletteSize];
		for (String key : paletteTag.getAllKeys()) {
			palette[paletteTag.getInt(key)] = states.fromString(key);
		}

		Map<BlockPos, CompoundTag> tiles = new HashMap<>();
		for (int i = 0; i < blockEntities.size(); i++) {
			CompoundTag be = blockEntities.getCompound(i);
			int[] pos = be.getIntArray("Pos");
			if (pos.length != 3) {
				continue;
			}
			CompoundTag nbt;
			if (be.contains("Data", Tag.TAG_COMPOUND)) {
				nbt = be.getCompound("Data").copy(); // v3
			} else {
				nbt = be.copy(); // v1/v2: data lives next to Pos and Id
				nbt.remove("Pos");
				nbt.remove("Id");
			}
			if (be.contains("Id")) {
				nbt.putString("id", be.getString("Id"));
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
			BlockPos pos = new BlockPos(x, y, z);
			CompoundTag nbt = tiles.get(pos);
			nbt = nbt != null && state.hasBlockEntity() ? BlueprintFiles.withBlockEntityId(states.fixBlockEntity(nbt), state) : null;
			entries.add(new Blueprint.Entry(pos, BlueprintFiles.normalizeAir(state), nbt));
		}
		return new Blueprint(id, size, List.copyOf(entries));
	}

	private SpongeSchematicReader() {
	}
}
