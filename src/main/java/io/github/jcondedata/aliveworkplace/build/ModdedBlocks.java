package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.camp.CampCooks;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

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

	/**
	 * Cobblemon's Campfire Pot (ROADMAP 28.13: the Camp Kitchen's job block) is a campfire with a pot put on it and has
	 * no item of its own: a builder sets a campfire and puts a red pot on it ({@link #afterPlaced}).
	 */
	static final ResourceLocation CAMPFIRE_POT = ResourceLocation.fromNamespaceAndPath("cobblemon", "campfire");
	static final ResourceLocation CAMPFIRE_POT_ITEM = ResourceLocation.fromNamespaceAndPath("cobblemon", "campfire_pot_red");

	/**
	 * What a block with no item of its own costs, or false when it isn't one of those: a Campfire Pot costs a campfire
	 * and a pot.
	 */
	static boolean ownCosts(Block block, List<MaterialRules.Requirement> out) {
		if (!BuiltInRegistries.BLOCK.getKey(block).equals(CAMPFIRE_POT)) {
			return false;
		}
		Optional<Item> pot = BuiltInRegistries.ITEM.getOptional(CAMPFIRE_POT_ITEM);
		if (pot.isPresent()) {
			out.add(new MaterialRules.Requirement(Items.CAMPFIRE, 1));
			out.add(new MaterialRules.Requirement(pot.get(), 1));
		}
		return true;
	}

	/** Finishes a block a builder has just placed: puts the pot on a Campfire Pot (its cost included it). */
	static void afterPlaced(ServerLevel level, BlockPos pos, BlockState state) {
		if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(CAMPFIRE_POT)) {
			BuiltInRegistries.ITEM.getOptional(CAMPFIRE_POT_ITEM).ifPresent(pot ->
				CampCooks.Pot.EXTENSION.run(p -> p.fitPot(level, pos, new ItemStack(pot))));
		}
	}

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
		if (Nbt.has(nbt, "Mimic", Tag.TAG_COMPOUND)) {
			String name = Nbt.getString(Nbt.getCompound(nbt, "Mimic"), "Name");
			ResourceLocation id = ResourceLocation.tryParse(name);
			if (id != null) {
				Block held = Lookup.value(BuiltInRegistries.BLOCK, id);
				Item item = held.asItem();
				if (held != Blocks.AIR && item != Items.AIR) {
					out.add(new MaterialRules.Requirement(item, 1));
				}
			}
		}
		// Supplementaries way signs: one sign item per arm, in the arm's wood.
		for (String arm : new String[]{"SignUp", "SignDown"}) {
			if (Nbt.has(nbt, arm, Tag.TAG_COMPOUND) && Nbt.getBoolean(Nbt.getCompound(nbt, arm), "Active")) {
				waySign(Nbt.getString(Nbt.getCompound(nbt, arm), "WoodType")).ifPresent(item -> out.add(new MaterialRules.Requirement(item, 1)));
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
