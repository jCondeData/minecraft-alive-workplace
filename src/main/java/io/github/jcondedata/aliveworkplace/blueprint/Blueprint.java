package io.github.jcondedata.aliveworkplace.blueprint;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Format-independent, immutable description of a build: every block position (relative to the
 * template origin) and the state that belongs there. Air entries mean "this spot must be empty";
 * positions that are absent (structure void) are left untouched.
 *
 * <p>All import formats (.nbt structure templates today; .schem / .litematic later) are converted
 * into this model so the builder AI only needs to understand one thing.
 */
public record Blueprint(ResourceLocation id, Vec3i size, List<Entry> blocks, List<EntityEntry> entities) {
	public record Entry(BlockPos pos, BlockState state, @Nullable CompoundTag nbt) {
	}

	/**
	 * An entity the builder puts up (item frame, glow item frame, painting, armor stand): where, relative to
	 * the template origin, and its data without contents (see {@link BlueprintEntities#clean}).
	 */
	public record EntityEntry(Vec3 pos, CompoundTag nbt) {
	}

	public Blueprint(ResourceLocation id, Vec3i size, List<Entry> blocks) {
		this(id, size, blocks, List.of());
	}

	/** Reads a vanilla structure template (already data-fixed by the template manager). */
	public static Blueprint fromTemplate(ResourceLocation id, StructureTemplate template, HolderGetter<Block> blockLookup) {
		return fromStructureNbt(id, template.save(new CompoundTag()), blockLookup);
	}

	/** Reads the vanilla structure NBT layout: size, palette(s), blocks[{pos, state, nbt}]. */
	public static Blueprint fromStructureNbt(ResourceLocation id, CompoundTag tag, HolderGetter<Block> blockLookup) {
		ListTag sizeTag = tag.getList("size", Tag.TAG_INT);
		Vec3i size = new Vec3i(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2));

		ListTag paletteTag = tag.contains("palettes", Tag.TAG_LIST)
			? tag.getList("palettes", Tag.TAG_LIST).getList(0)
			: tag.getList("palette", Tag.TAG_COMPOUND);
		List<BlockState> palette = new ArrayList<>(paletteTag.size());
		for (int i = 0; i < paletteTag.size(); i++) {
			palette.add(NbtUtils.readBlockState(blockLookup, paletteTag.getCompound(i)));
		}

		ListTag blocksTag = tag.getList("blocks", Tag.TAG_COMPOUND);
		List<Entry> entries = new ArrayList<>(blocksTag.size());
		for (int i = 0; i < blocksTag.size(); i++) {
			CompoundTag b = blocksTag.getCompound(i);
			ListTag p = b.getList("pos", Tag.TAG_INT);
			int stateIndex = b.getInt("state");
			BlockState state = stateIndex >= 0 && stateIndex < palette.size() ? palette.get(stateIndex) : Blocks.AIR.defaultBlockState();
			if (state.is(Blocks.STRUCTURE_VOID)) {
				continue;
			}
			CompoundTag nbt = b.contains("nbt", Tag.TAG_COMPOUND) ? b.getCompound("nbt") : null;
			entries.add(new Entry(new BlockPos(p.getInt(0), p.getInt(1), p.getInt(2)), state, nbt));
		}
		List<EntityEntry> entities = new ArrayList<>();
		ListTag entitiesTag = tag.getList("entities", Tag.TAG_COMPOUND);
		for (int i = 0; i < entitiesTag.size(); i++) {
			CompoundTag e = entitiesTag.getCompound(i);
			ListTag p = e.getList("pos", Tag.TAG_DOUBLE);
			CompoundTag nbt = BlueprintEntities.clean(e.getCompound("nbt"));
			if (p.size() == 3 && nbt != null) {
				entities.add(new EntityEntry(new Vec3(p.getDouble(0), p.getDouble(1), p.getDouble(2)), nbt));
			}
		}
		return new Blueprint(id, size, List.copyOf(entries), List.copyOf(entities));
	}

	/** Number of non-air blocks — what a player would call the size of the job. */
	public int solidBlockCount() {
		int n = 0;
		for (Entry e : blocks) {
			if (!e.state().isAir()) {
				n++;
			}
		}
		return n;
	}
}
