package io.github.jcondedata.aliveworkplace.blueprint.io;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turns block states from schematic files (written by any Minecraft version) into current states.
 * Old names are upgraded with Minecraft's own data fixer; blocks from mods that aren't installed
 * become air and are counted so the player can be told.
 */
final class BlockStateReader {
	private final HolderLookup<Block> blocks;
	private final DataFixer fixer;
	private final int fromVersion;
	private final int currentVersion = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
	private int unknown;

	BlockStateReader(DataFixer fixer, int fromVersion) {
		this.blocks = BuiltInRegistries.BLOCK.asLookup();
		this.fixer = fixer;
		this.fromVersion = fromVersion;
	}

	/** Palette entry as {Name, Properties} (Litematica, vanilla structures). */
	BlockState fromCompound(CompoundTag tag) {
		CompoundTag fixed = tag;
		if (fromVersion > 0 && fromVersion < currentVersion) {
			fixed = (CompoundTag) fixer.update(References.BLOCK_STATE, new Dynamic<Tag>(NbtOps.INSTANCE, tag), fromVersion, currentVersion).getValue();
		}
		ResourceLocation id = ResourceLocation.tryParse(fixed.getString("Name"));
		if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
			unknown++;
			return Blocks.AIR.defaultBlockState();
		}
		return NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), fixed);
	}

	/** Palette entry as a string, e.g. {@code minecraft:oak_stairs[facing=east]} (Sponge/WorldEdit). */
	BlockState fromString(String state) {
		String fixed = state;
		if (fromVersion > 0 && fromVersion < currentVersion) {
			fixed = fixer.update(References.FLAT_BLOCK_STATE, new Dynamic<Tag>(NbtOps.INSTANCE, StringTag.valueOf(state)), fromVersion, currentVersion)
				.asString(state);
		}
		int bracket = fixed.indexOf('[');
		ResourceLocation id = ResourceLocation.tryParse(bracket < 0 ? fixed : fixed.substring(0, bracket));
		if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
			unknown++;
			return Blocks.AIR.defaultBlockState();
		}
		try {
			return BlockStateParser.parseForBlock(blocks, fixed, false).blockState();
		} catch (CommandSyntaxException e) {
			// Unknown property values (e.g. from a newer version): fall back to the block's default state.
			return BuiltInRegistries.BLOCK.get(ResourceKey.create(Registries.BLOCK, id)).defaultBlockState();
		}
	}

	/** Upgrades block-entity data (signs, banners...) written by an older version. */
	CompoundTag fixBlockEntity(CompoundTag tag) {
		if (fromVersion > 0 && fromVersion < currentVersion) {
			return (CompoundTag) fixer.update(References.BLOCK_ENTITY, new Dynamic<Tag>(NbtOps.INSTANCE, tag), fromVersion, currentVersion).getValue();
		}
		return tag;
	}

	int unknownBlocks() {
		return unknown;
	}
}
