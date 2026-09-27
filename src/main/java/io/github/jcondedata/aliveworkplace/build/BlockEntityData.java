package io.github.jcondedata.aliveworkplace.build;

import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Decides which block-entity data from a blueprint a builder copies onto a block it places. Looks
 * (sign text, banner patterns, a way sign's arms, a blackboard drawing) are copied; anything that would
 * hand out free items or mobs is not — chest contents, a jar's cookies, an item shelf's sword, a caged
 * mob, a safe's owner. Works for blocks from any mod, not just vanilla.
 */
public final class BlockEntityData {
	/** Keys that hold items, loot, mobs or ownership in vanilla and common mods. */
	private static final String[] STRIP_KEYS = {
		"Items", "LootTable", "LootTableSeed", "RecordItem", "Book", "item", "Bees", "SpawnData", "SpawnPotentials",
		"Owner", "OwnerName", "owner", "Password", "password", "Lock", "lock"
	};

	/**
	 * The data to load into a freshly placed block entity, or null to load nothing. {@code blockEntity}
	 * is the new, empty block entity at {@code pos}.
	 */
	@Nullable
	public static CompoundTag sanitize(ServerLevel level, BlockPos pos, BlockState state, BlockEntity blockEntity, CompoundTag nbt) {
		if (isStorage(level, pos, state, blockEntity)) {
			// Containers (chests, barrels, jars, shelves, safes...): only the name, never the contents.
			if (!nbt.contains("CustomName", Tag.TAG_STRING)) {
				return null;
			}
			CompoundTag named = new CompoundTag();
			named.putString("CustomName", nbt.getString("CustomName"));
			return named;
		}
		CompoundTag tag = nbt.copy();
		for (String key : STRIP_KEYS) {
			tag.remove(key);
		}
		stripStacksAndEntities(tag);
		return tag;
	}

	private static boolean isStorage(ServerLevel level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
		if (blockEntity instanceof Container) {
			return true;
		}
		try {
			return ItemStorage.SIDED.find(level, pos, state, blockEntity, null) != null;
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** Removes every nested compound that is an item stack or an entity (anything with an item/entity "id"). */
	static void stripStacksAndEntities(CompoundTag tag) {
		for (String key : tag.getAllKeys().toArray(String[]::new)) {
			Tag child = tag.get(key);
			if (child instanceof CompoundTag compound) {
				if (isStackOrEntity(compound)) {
					tag.remove(key);
				} else {
					stripStacksAndEntities(compound);
				}
			} else if (child instanceof ListTag list) {
				stripList(list);
			}
		}
	}

	private static void stripList(ListTag list) {
		for (int i = list.size() - 1; i >= 0; i--) {
			Tag element = list.get(i);
			if (element instanceof CompoundTag compound) {
				if (isStackOrEntity(compound)) {
					list.remove(i);
				} else {
					stripStacksAndEntities(compound);
				}
			} else if (element instanceof ListTag inner) {
				stripList(inner);
			}
		}
	}

	private static boolean isStackOrEntity(CompoundTag compound) {
		if (!compound.contains("id", Tag.TAG_STRING)) {
			return false;
		}
		ResourceLocation id = ResourceLocation.tryParse(compound.getString("id"));
		return id != null && (BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.ENTITY_TYPE.containsKey(id));
	}

	private BlockEntityData() {
	}
}
