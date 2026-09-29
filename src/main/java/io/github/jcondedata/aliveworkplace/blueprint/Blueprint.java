package io.github.jcondedata.aliveworkplace.blueprint;

import io.github.jcondedata.aliveworkplace.mc.Nbt;
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
		ListTag sizeTag = Nbt.getList(tag, "size", Tag.TAG_INT);
		Vec3i size = new Vec3i(Nbt.intAt(sizeTag, 0), Nbt.intAt(sizeTag, 1), Nbt.intAt(sizeTag, 2));

		ListTag paletteTag = Nbt.has(tag, "palettes", Tag.TAG_LIST)
			? Nbt.listAt(Nbt.getList(tag, "palettes", Tag.TAG_LIST), 0)
			: Nbt.getList(tag, "palette", Tag.TAG_COMPOUND);
		List<BlockState> palette = new ArrayList<>(paletteTag.size());
		for (int i = 0; i < paletteTag.size(); i++) {
			palette.add(NbtUtils.readBlockState(blockLookup, Nbt.compoundAt(paletteTag, i)));
		}

		ListTag blocksTag = Nbt.getList(tag, "blocks", Tag.TAG_COMPOUND);
		List<Entry> entries = new ArrayList<>(blocksTag.size());
		for (int i = 0; i < blocksTag.size(); i++) {
			CompoundTag b = Nbt.compoundAt(blocksTag, i);
			ListTag p = Nbt.getList(b, "pos", Tag.TAG_INT);
			int stateIndex = Nbt.getInt(b, "state");
			BlockState state = stateIndex >= 0 && stateIndex < palette.size() ? palette.get(stateIndex) : Blocks.AIR.defaultBlockState();
			if (state.is(Blocks.STRUCTURE_VOID)) {
				continue;
			}
			CompoundTag nbt = Nbt.has(b, "nbt", Tag.TAG_COMPOUND) ? Nbt.getCompound(b, "nbt") : null;
			entries.add(new Entry(new BlockPos(Nbt.intAt(p, 0), Nbt.intAt(p, 1), Nbt.intAt(p, 2)), state, nbt));
		}
		List<EntityEntry> entities = new ArrayList<>();
		ListTag entitiesTag = Nbt.getList(tag, "entities", Tag.TAG_COMPOUND);
		for (int i = 0; i < entitiesTag.size(); i++) {
			CompoundTag e = Nbt.compoundAt(entitiesTag, i);
			ListTag p = Nbt.getList(e, "pos", Tag.TAG_DOUBLE);
			CompoundTag nbt = BlueprintEntities.clean(Nbt.getCompound(e, "nbt"));
			if (p.size() == 3 && nbt != null) {
				entities.add(new EntityEntry(new Vec3(Nbt.doubleAt(p, 0), Nbt.doubleAt(p, 1), Nbt.doubleAt(p, 2)), nbt));
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
