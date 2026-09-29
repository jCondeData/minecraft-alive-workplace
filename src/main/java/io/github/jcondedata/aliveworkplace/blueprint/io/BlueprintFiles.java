package io.github.jcondedata.aliveworkplace.blueprint.io;

import com.mojang.datafixers.DataFixer;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reads any supported blueprint file into a {@link Blueprint}, and writes blueprints as vanilla
 * structure NBT. Supported: Litematica ({@code .litematic}), Sponge/WorldEdit ({@code .schem} v1-v3)
 * and vanilla structure files ({@code .nbt}). The format is detected from the content, not the name.
 */
public final class BlueprintFiles {
	/** Largest build (width × height × depth) accepted by default. */
	public static final long DEFAULT_MAX_VOLUME = 1_000_000L;
	/** Largest file accepted, compressed. */
	public static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
	private static final long MAX_NBT_BYTES = 256L * 1024 * 1024;

	public record Result(Blueprint blueprint, String format, int unknownBlocks) {
	}

	public static Result read(ResourceLocation id, byte[] bytes, DataFixer fixer, long maxVolume) throws BlueprintFormatException {
		if (bytes.length > MAX_FILE_BYTES) {
			throw new BlueprintFormatException("file_too_big", bytes.length / 1024, MAX_FILE_BYTES / 1024);
		}
		CompoundTag root;
		try {
			root = readNbt(bytes);
		} catch (IOException | RuntimeException e) {
			throw new BlueprintFormatException("not_nbt");
		}

		if (LitematicReader.looksLike(root)) {
			BlockStateReader states = new BlockStateReader(fixer, Nbt.getInt(root, "MinecraftDataVersion"));
			return new Result(LitematicReader.read(id, root, states, maxVolume), "litematic", states.unknownBlocks());
		}
		if (SpongeSchematicReader.looksLike(root)) {
			BlockStateReader states = new BlockStateReader(fixer, SpongeSchematicReader.dataVersion(root));
			return new Result(SpongeSchematicReader.read(id, root, states, maxVolume), "schem", states.unknownBlocks());
		}
		if (Nbt.has(root, "size", Tag.TAG_LIST) && Nbt.has(root, "blocks", Tag.TAG_LIST)) {
			CompoundTag fixed = DataFixTypes.STRUCTURE.updateToCurrentVersion(fixer, root, NbtUtils.getDataVersion(root, 500));
			ListTag s = Nbt.getList(fixed, "size", Tag.TAG_INT);
			checkVolume(new Vec3i(Nbt.intAt(s, 0), Nbt.intAt(s, 1), Nbt.intAt(s, 2)), maxVolume);
			return new Result(Blueprint.fromStructureNbt(id, fixed, Lookup.lookup(BuiltInRegistries.BLOCK)), "nbt", 0);
		}
		if (Nbt.has(root, "Blocks", Tag.TAG_BYTE_ARRAY) && root.contains("Materials")) {
			throw new BlueprintFormatException("mcedit");
		}
		throw new BlueprintFormatException("unknown_format");
	}

	static CompoundTag readNbt(byte[] bytes) throws IOException {
		try {
			return NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(MAX_NBT_BYTES));
		} catch (IOException gzipFailed) {
			return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)), NbtAccounter.create(MAX_NBT_BYTES));
		}
	}

	static void checkVolume(Vec3i size, long maxVolume) throws BlueprintFormatException {
		long volume = (long) size.getX() * size.getY() * size.getZ();
		if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
			throw new BlueprintFormatException("empty");
		}
		if (volume > maxVolume) {
			throw new BlueprintFormatException("too_big", size.getX(), size.getY(), size.getZ(), maxVolume);
		}
	}

	static BlockState normalizeAir(BlockState state) {
		return state.is(Blocks.CAVE_AIR) || state.is(Blocks.VOID_AIR) ? Blocks.AIR.defaultBlockState() : state;
	}

	/** Makes sure block-entity data carries its type id, which vanilla structure files require. */
	static CompoundTag withBlockEntityId(CompoundTag nbt, BlockState state) {
		if (!nbt.contains("id") && state.getBlock() instanceof EntityBlock entityBlock) {
			BlockEntity be = entityBlock.newBlockEntity(BlockPos.ZERO, state);
			if (be != null) {
				ResourceLocation key = BlockEntityType.getKey(be.getType());
				if (key != null) {
					nbt.putString("id", key.toString());
				}
			}
		}
		return nbt;
	}

	/** Vanilla structure-template NBT (what Structure Blocks save), at the current data version. */
	public static CompoundTag toStructureNbt(Blueprint blueprint) {
		Map<BlockState, Integer> index = new HashMap<>();
		ListTag palette = new ListTag();
		ListTag blocks = new ListTag();
		for (Blueprint.Entry entry : blueprint.blocks()) {
			Integer i = index.get(entry.state());
			if (i == null) {
				i = palette.size();
				index.put(entry.state(), i);
				palette.add(NbtUtils.writeBlockState(entry.state()));
			}
			CompoundTag b = new CompoundTag();
			ListTag pos = new ListTag();
			pos.add(IntTag.valueOf(entry.pos().getX()));
			pos.add(IntTag.valueOf(entry.pos().getY()));
			pos.add(IntTag.valueOf(entry.pos().getZ()));
			b.put("pos", pos);
			b.putInt("state", i);
			if (entry.nbt() != null) {
				b.put("nbt", entry.nbt().copy());
			}
			blocks.add(b);
		}
		CompoundTag tag = new CompoundTag();
		ListTag size = new ListTag();
		size.add(IntTag.valueOf(blueprint.size().getX()));
		size.add(IntTag.valueOf(blueprint.size().getY()));
		size.add(IntTag.valueOf(blueprint.size().getZ()));
		tag.put("size", size);
		tag.put("palette", palette);
		tag.put("blocks", blocks);
		ListTag entities = new ListTag();
		for (Blueprint.EntityEntry entity : blueprint.entities()) {
			CompoundTag e = new CompoundTag();
			ListTag pos = new ListTag();
			pos.add(net.minecraft.nbt.DoubleTag.valueOf(entity.pos().x));
			pos.add(net.minecraft.nbt.DoubleTag.valueOf(entity.pos().y));
			pos.add(net.minecraft.nbt.DoubleTag.valueOf(entity.pos().z));
			e.put("pos", pos);
			BlockPos block = BlockPos.containing(entity.pos());
			ListTag blockPos = new ListTag();
			blockPos.add(IntTag.valueOf(block.getX()));
			blockPos.add(IntTag.valueOf(block.getY()));
			blockPos.add(IntTag.valueOf(block.getZ()));
			e.put("blockPos", blockPos);
			e.put("nbt", entity.nbt().copy());
			entities.add(e);
		}
		tag.put("entities", entities);
		tag.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
		return tag;
	}

	private BlueprintFiles() {
	}
}
