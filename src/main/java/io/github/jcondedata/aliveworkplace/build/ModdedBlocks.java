package io.github.jcondedata.aliveworkplace.build;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Build rules for blocks from other mods in the Cobbleverse pack, matched by id so none of those mods
 * is needed at compile time or run time. Covered by the compatibility tests ({@code src/compattest}).
 */
final class ModdedBlocks {
	private static final String SUPP = "supplementaries";

	/** Blocks builders leave out: made of loot (book piles) or left-over effects (confetti). */
	private static final Set<ResourceLocation> NEVER_PLACE = Set.of(
		ResourceLocation.fromNamespaceAndPath(SUPP, "book_pile"),
		ResourceLocation.fromNamespaceAndPath(SUPP, "book_pile_horizontal"),
		ResourceLocation.fromNamespaceAndPath(SUPP, "confetti_litter")
	);

	/** Blocks whose own item says nothing about their cost; the block-entity data does (way signs). */
	private static final Set<ResourceLocation> COST_FROM_DATA_ONLY = Set.of(
		ResourceLocation.fromNamespaceAndPath(SUPP, "way_sign"),
		ResourceLocation.fromNamespaceAndPath(SUPP, "way_sign_wall")
	);

	/**
	 * Storage-network access points: they show the contents of chests elsewhere, which workers would then
	 * count twice (once through the network, once in the chest itself). Workers use the chests directly.
	 * Tom's Simple Storage: everything but the filing cabinet (which holds items itself); Sophisticated
	 * Storage: the controller and its links and I/O blocks.
	 */
	private static final Set<ResourceLocation> STORAGE_NETWORK = Set.of(
		ResourceLocation.fromNamespaceAndPath("sophisticatedstorage", "controller"),
		ResourceLocation.fromNamespaceAndPath("sophisticatedstorage", "storage_link"),
		ResourceLocation.fromNamespaceAndPath("sophisticatedstorage", "storage_io"),
		ResourceLocation.fromNamespaceAndPath("sophisticatedstorage", "storage_input"),
		ResourceLocation.fromNamespaceAndPath("sophisticatedstorage", "storage_output")
	);

	static boolean isStorageNetwork(Block block) {
		ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
		return STORAGE_NETWORK.contains(id) || id.getNamespace().equals("toms_storage") && !id.getPath().equals("filing_cabinet");
	}

	static boolean neverPlace(Block block) {
		return NEVER_PLACE.contains(BuiltInRegistries.BLOCK.getKey(block));
	}

	static boolean costFromDataOnly(Block block) {
		return COST_FROM_DATA_ONLY.contains(BuiltInRegistries.BLOCK.getKey(block));
	}

	/** Extra items placing this block uses up, read from its block-entity data. */
	static void extraCosts(Block block, CompoundTag nbt, List<MaterialRules.Requirement> out) {
		// Blocks that wrap another block (Moonlight "Mimic": way signs and rope knots on fences, filled
		// timber frames, and other mods using the same library) also cost the wrapped block.
		if (nbt.contains("Mimic", Tag.TAG_COMPOUND)) {
			String name = nbt.getCompound("Mimic").getString("Name");
			ResourceLocation id = ResourceLocation.tryParse(name);
			if (id != null) {
				Block held = BuiltInRegistries.BLOCK.get(id);
				Item item = held.asItem();
				if (held != Blocks.AIR && item != Items.AIR) {
					out.add(new MaterialRules.Requirement(item, 1));
				}
			}
		}
		// Supplementaries way signs: one sign item per arm, in the arm's wood.
		for (String arm : new String[]{"SignUp", "SignDown"}) {
			if (nbt.contains(arm, Tag.TAG_COMPOUND) && nbt.getCompound(arm).getBoolean("Active")) {
				waySign(nbt.getCompound(arm).getString("WoodType")).ifPresent(item -> out.add(new MaterialRules.Requirement(item, 1)));
			}
		}
	}

	private static Optional<Item> waySign(String woodType) {
		ResourceLocation wood = ResourceLocation.tryParse(woodType.isEmpty() ? "minecraft:oak" : woodType);
		if (wood == null) {
			return Optional.empty();
		}
		for (String path : new String[]{
			"way_sign_" + wood.getPath(),
			wood.getNamespace() + "/way_sign_" + wood.getPath(),
			"way_sign_" + wood.getNamespace() + "_" + wood.getPath()}) {
			Optional<Item> item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(SUPP, path));
			if (item.isPresent()) {
				return item;
			}
		}
		return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(SUPP, "way_sign_oak"));
	}

	private ModdedBlocks() {
	}
}
