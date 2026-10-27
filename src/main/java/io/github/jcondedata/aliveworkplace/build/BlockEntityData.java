package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.platform.Platform;
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
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * Decides which block-entity data from a blueprint a builder copies onto a block it places. Looks
 * (sign text, banner patterns, a way sign's arms, a blackboard drawing) are copied; anything that would
 * hand out free items or mobs is not — chest contents, a jar's cookies, an item shelf's sword, a caged
 * mob, a safe's owner, the lava in a Create tank. Works for blocks from any mod, not just vanilla.
 */
public final class BlockEntityData {
	/** Keys that hold items, loot, mobs or ownership in vanilla and common mods. */
	private static final String[] STRIP_KEYS = {
		"Items", "LootTable", "LootTableSeed", "RecordItem", "Book", "item", "Bees", "bees", "SpawnData", "SpawnPotentials",
		"Owner", "OwnerName", "owner", "Password", "password", "Lock", "lock"
	};

	/**
	 * The data to load into a freshly placed block entity, or null to load nothing. {@code blockEntity}
	 * is the new, empty block entity at {@code pos}.
	 */
	@Nullable
	public static CompoundTag sanitize(ServerLevel level, BlockPos pos, BlockState state, BlockEntity blockEntity, CompoundTag nbt) {
		if (blockEntity instanceof io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) {
			// A Village Hall from a scanned build starts a village of its own: never another village's name, treasury
			// or chronicle
			return null;
		}
		if (blockEntity instanceof io.github.jcondedata.aliveworkplace.grave.GraveBlockEntity) {
			// B449: a grave from a scanned build is a fresh, empty one: never the dead villager the scanned grave held
			// (the Undertaker could revive a copy for every build), nor their name on it
			return null;
		}
		// A belt carries items but is no store: its place on the belt is copied (39.5; the items on it never are)
		if (!io.github.jcondedata.aliveworkplace.mechanic.KineticParts.isBelt(state) && isStorage(level, pos, state, blockEntity)) {
			// Containers (chests, barrels, jars, shelves, safes...): only the name, never the contents.
			if (!Nbt.has(nbt, "CustomName", Tag.TAG_STRING)) {
				return null;
			}
			CompoundTag named = new CompoundTag();
			named.putString("CustomName", Nbt.getString(nbt, "CustomName"));
			return named;
		}
		CompoundTag tag = nbt.copy();
		for (String key : STRIP_KEYS) {
			tag.remove(key);
		}
		if (stripStacksAndEntities(tag)) {
			// B486: a block that held a fluid caches the light that fluid gave (Create's tank keeps "Luminosity"); built
			// empty, it starts dark, as it does when placed fresh
			tag.remove("Luminosity");
		}
		return tag;
	}

	private static boolean isStorage(ServerLevel level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
		if (blockEntity instanceof Container) {
			return true;
		}
		try {
			return Platform.get().items().isStore(level, pos, state, blockEntity);
		} catch (RuntimeException e) {
			return false;
		}
	}

	/**
	 * Removes every nested compound that is an item stack, an entity (anything with an item/entity "id", or an entity
	 * saved without one) or a fluid stack. True if a fluid was removed.
	 */
	static boolean stripStacksAndEntities(CompoundTag tag) {
		boolean fluid = false;
		for (String key : Nbt.keys(tag).toArray(String[]::new)) {
			Tag child = tag.get(key);
			if (child instanceof CompoundTag compound) {
				if (isFluid(compound)) {
					// B486: the fluid a block holds in its data (Create's tank, spout, basin and drain, any mod's tank) is
					// never copied. The tank around it stays (its size, its controller), so it reads back as empty; an amount
					// kept beside the fluid, as Fabric's storages save it, goes with it
					tag.remove(key);
					tag.remove("amount");
					tag.remove("Amount");
					fluid = true;
				} else if (isStackOrEntity(compound)) {
					tag.remove(key);
				} else {
					fluid |= stripStacksAndEntities(compound);
				}
			} else if (child instanceof ListTag list) {
				fluid |= stripList(list);
			}
		}
		return fluid;
	}

	private static boolean stripList(ListTag list) {
		boolean fluid = false;
		for (int i = list.size() - 1; i >= 0; i--) {
			Tag element = list.get(i);
			if (element instanceof CompoundTag compound) {
				if (isFluid(compound)) {
					list.remove(i);
					fluid = true;
				} else if (isStackOrEntity(compound)) {
					list.remove(i);
				} else {
					fluid |= stripStacksAndEntities(compound);
				}
			} else if (element instanceof ListTag inner) {
				fluid |= stripList(inner);
			}
		}
		return fluid;
	}

	/**
	 * A saved fluid stack: a compound naming a fluid other than {@code minecraft:empty} under one of the keys mods save it
	 * by: "id" (NeoForge's and Porting Lib's FluidStack, which Create uses: {@code {id, amount}}), "FluidName" (the older
	 * Forge FluidStack) or "fluid" (Fabric's FluidVariant). By registry, so it covers every mod's fluids.
	 */
	static boolean isFluid(CompoundTag compound) {
		for (String key : FLUID_KEYS) {
			if (Nbt.has(compound, key, Tag.TAG_STRING)) {
				ResourceLocation id = ResourceLocation.tryParse(Nbt.getString(compound, key));
				if (id != null && BuiltInRegistries.FLUID.containsKey(id) && BuiltInRegistries.FLUID.get(id) != Fluids.EMPTY) {
					return true;
				}
			}
		}
		return false;
	}

	private static final String[] FLUID_KEYS = {"id", "FluidName", "fluid"};

	private static boolean isStackOrEntity(CompoundTag compound) {
		if (Nbt.hasUuid(compound, "UUID") && Nbt.has(compound, "Pos", Tag.TAG_LIST)) {
			// B449: an entity saved without its id (Entity.saveWithoutId, as a mod's block keeps a mob it holds) is still
			// an entity: its UUID and position give it away
			return true;
		}
		if (!Nbt.has(compound, "id", Tag.TAG_STRING)) {
			return false;
		}
		ResourceLocation id = ResourceLocation.tryParse(Nbt.getString(compound, "id"));
		return id != null && (BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.ENTITY_TYPE.containsKey(id));
	}

	private BlockEntityData() {
	}
}
