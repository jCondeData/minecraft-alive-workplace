package io.github.jcondedata.aliveworkplace.build;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the builder needs to know about individual block states: what item a block costs,
 * whether it goes up in the structure pass or the decoration pass, and whether a spot is "done".
 */
public final class MaterialRules {
	public enum Kind {
		/** Not built (air, technical blocks, fluids, second halves of doors/beds). */
		SKIP,
		/** Placed bottom-up in the main pass. */
		STRUCTURE,
		/** Needs something to hang on or stand on: placed after the structure is up. */
		DECORATION
	}

	public record Requirement(Item item, int count) {
	}

	/** Blocks a builder never places: unobtainable, technical, or exploitable. */
	private static final Set<Block> NEVER_PLACE = Set.of(
		Blocks.BARRIER, Blocks.BEDROCK, Blocks.COMMAND_BLOCK, Blocks.CHAIN_COMMAND_BLOCK, Blocks.REPEATING_COMMAND_BLOCK,
		Blocks.STRUCTURE_BLOCK, Blocks.STRUCTURE_VOID, Blocks.JIGSAW, Blocks.SPAWNER, Blocks.TRIAL_SPAWNER, Blocks.VAULT,
		Blocks.END_PORTAL_FRAME, Blocks.END_PORTAL, Blocks.END_GATEWAY, Blocks.NETHER_PORTAL, Blocks.REINFORCED_DEEPSLATE,
		Blocks.LIGHT, Blocks.BUDDING_AMETHYST, Blocks.MOVING_PISTON, Blocks.PISTON_HEAD, Blocks.FIRE, Blocks.SOUL_FIRE,
		Blocks.FROSTED_ICE, Blocks.BUBBLE_COLUMN
	);

	/** Blocks you cannot hold as an item in survival: the builder uses the base material instead. */
	private static final Map<Block, Item> SUBSTITUTES = Map.of(
		Blocks.GRASS_BLOCK, Items.DIRT,
		Blocks.PODZOL, Items.DIRT,
		Blocks.MYCELIUM, Items.DIRT,
		Blocks.FARMLAND, Items.DIRT,
		Blocks.DIRT_PATH, Items.DIRT
	);

	/** Properties that depend on neighbours or change on their own; ignored when checking if a spot is done. */
	private static final Set<String> VOLATILE_PROPERTY_NAMES = Set.of(
		"north", "east", "south", "west", "up", "down", "shape", "waterlogged", "powered", "open", "lit",
		"distance", "persistent", "snowy", "occupied", "triggered", "attached", "disarmed", "in_wall", "has_book",
		"has_record", "bottom", "berries", "age", "stage", "moisture", "power", "note", "instrument",
		// Supplementaries: a pending rotation its block entity applies (and clears) when it loads
		"rotate_tile",
		// Cobblemon (ROADMAP 28.7): a Healing Machine charges by itself, a PC or Pasture Block lights up while in use
		"charge", "on"
	);

	public static Kind classify(BlockState state) {
		return classify(state, null);
	}

	/** Like {@link #classify(BlockState)}, taking the block-entity data into account (see {@link #requirements}). */
	public static Kind classify(BlockState state, @Nullable CompoundTag nbt) {
		Block block = state.getBlock();
		if (state.isAir() || NEVER_PLACE.contains(block) || ModdedBlocks.neverPlace(block)) {
			return Kind.SKIP;
		}
		if (block instanceof LiquidBlock) {
			// Still water or lava is poured from a bucket once the walls are up; flowing liquid comes by itself.
			return state.getFluidState().isSource() && bucket(state) != Items.AIR ? Kind.DECORATION : Kind.SKIP;
		}
		if (isSecondaryHalf(state) || requirements(state, nbt).isEmpty()) {
			return Kind.SKIP;
		}
		return needsSupport(block) ? Kind.DECORATION : Kind.STRUCTURE;
	}

	/**
	 * Everything placing this block uses up. Most blocks cost their own item; a potted plant costs a
	 * flower pot and the plant; blocks that wrap another block (a Supplementaries way sign on a fence)
	 * also cost that block. Empty if the block cannot be built.
	 */
	public static List<Requirement> requirements(BlockState state, @Nullable CompoundTag nbt) {
		Block block = state.getBlock();
		List<Requirement> out = new ArrayList<>(2);
		if (block instanceof FlowerPotBlock pot && block != Blocks.FLOWER_POT) {
			out.add(new Requirement(Items.FLOWER_POT, 1));
			Item plant = pot.getPotted().asItem();
			if (plant != Items.AIR) {
				out.add(new Requirement(plant, 1));
			}
			return out;
		}
		if (!ModdedBlocks.costFromDataOnly(block)) {
			requirement(state).ifPresent(out::add);
		}
		if (nbt != null && (!out.isEmpty() || ModdedBlocks.costFromDataOnly(block))) {
			ModdedBlocks.extraCosts(block, nbt, out);
		}
		return out;
	}

	private static boolean needsSupport(Block b) {
		return b instanceof BaseTorchBlock || b instanceof LadderBlock || b instanceof DoorBlock || b instanceof BedBlock
			|| b instanceof FaceAttachedHorizontalDirectionalBlock || b instanceof BasePressurePlateBlock || b instanceof SignBlock
			|| b instanceof AbstractBannerBlock || b instanceof CarpetBlock || b instanceof BaseRailBlock || b instanceof RedStoneWireBlock
			|| b instanceof DiodeBlock || b instanceof BushBlock || b instanceof LanternBlock || b instanceof VineBlock
			|| b instanceof CandleBlock || b instanceof SnowLayerBlock || b instanceof FlowerPotBlock || b instanceof TrapDoorBlock
			|| b instanceof TripWireHookBlock || b instanceof TripWireBlock || b instanceof CocoaBlock || b instanceof AbstractSkullBlock
			|| b instanceof AmethystClusterBlock || b instanceof PointedDripstoneBlock || b instanceof MultifaceBlock
			|| b instanceof BellBlock || b instanceof CakeBlock;
	}

	/** Upper door half, bed head, top of a tall flower: placed together with their other half. */
	public static boolean isSecondaryHalf(BlockState state) {
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
			return true;
		}
		if ("top".equals(modPart(state))) {
			return true;
		}
		return state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
	}

	/**
	 * A modded two-block-tall block's {@code part} ({@code "top"} or {@code "bottom"}), as Cobblemon's PC and Pasture
	 * Block have (ROADMAP 28.7); null for every other block. Found by the property's name and values, so no mod class is
	 * needed.
	 */
	@Nullable
	static String modPart(BlockState state) {
		net.minecraft.world.level.block.state.properties.Property<?> part = state.getBlock().getStateDefinition().getProperty("part");
		if (part == null || part.getPossibleValues().size() != 2) {
			return null;
		}
		String value = valueName(state, part);
		return value.equals("top") || value.equals("bottom") ? value : null;
	}

	private static <T extends Comparable<T>> String valueName(BlockState state, net.minecraft.world.level.block.state.properties.Property<T> property) {
		return property.getName(state.getValue(property));
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static BlockState withPart(BlockState state, String value) {
		net.minecraft.world.level.block.state.properties.Property part = state.getBlock().getStateDefinition().getProperty("part");
		java.util.Optional<Comparable> v = part.getValue(value);
		return v.isPresent() ? (BlockState) state.setValue(part, v.get()) : state;
	}

	/** Where the other half of a two-block block goes, or null for normal blocks. */
	@Nullable
	public static BlockPos secondaryPos(BlockPos pos, BlockState state) {
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
			return pos.above();
		}
		if (state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT) {
			Direction toHead = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
			return pos.relative(toHead);
		}
		if ("bottom".equals(modPart(state))) {
			return pos.above();
		}
		return null;
	}

	/** Default state for the other half when the blueprint does not contain it. */
	@Nullable
	public static BlockState defaultSecondary(BlockState primary) {
		if (primary.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			return primary.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
		}
		if (primary.hasProperty(BlockStateProperties.BED_PART)) {
			return primary.setValue(BlockStateProperties.BED_PART, BedPart.HEAD);
		}
		if (modPart(primary) != null) {
			return withPart(primary, "top");
		}
		return null;
	}

	/** The item (and how many) consumed to place this state. Empty if it cannot be built. */
	public static Optional<Requirement> requirement(BlockState state) {
		Block block = state.getBlock();
		if (block instanceof LiquidBlock) {
			Item bucket = state.getFluidState().isSource() ? bucket(state) : Items.AIR;
			return bucket == Items.AIR ? Optional.empty() : Optional.of(new Requirement(bucket, 1));
		}
		Item item = SUBSTITUTES.getOrDefault(block, block.asItem());
		if (item == Items.AIR) {
			return Optional.empty();
		}
		int count = 1;
		if (state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) {
			count = 2;
		} else if (state.hasProperty(BlockStateProperties.CANDLES)) {
			count = state.getValue(BlockStateProperties.CANDLES);
		} else if (state.hasProperty(BlockStateProperties.PICKLES)) {
			count = state.getValue(BlockStateProperties.PICKLES);
		} else if (state.hasProperty(BlockStateProperties.EGGS)) {
			count = state.getValue(BlockStateProperties.EGGS);
		} else if (state.hasProperty(BlockStateProperties.FLOWER_AMOUNT)) {
			count = state.getValue(BlockStateProperties.FLOWER_AMOUNT);
		} else if (block instanceof SnowLayerBlock) {
			count = state.getValue(BlockStateProperties.LAYERS);
		}
		return Optional.of(new Requirement(item, count));
	}

	/** The filled bucket that pours this liquid (a water bucket for water), or air if there is none. */
	public static Item bucket(BlockState state) {
		return state.getFluidState().getType().getBucket();
	}

	/** Adjust a blueprint state before placing it: no free water, no decaying leaves. */
	public static BlockState forPlacement(BlockState state) {
		if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
			state = state.setValue(BlockStateProperties.WATERLOGGED, false);
		}
		if (state.getBlock() instanceof LeavesBlock && state.hasProperty(BlockStateProperties.PERSISTENT)) {
			state = state.setValue(BlockStateProperties.PERSISTENT, true);
		}
		return state;
	}

	/** True if {@code actual} counts as "the right block is already there". */
	public static boolean matches(BlockState actual, BlockState wanted) {
		if (actual == wanted) {
			return true;
		}
		if (actual.getBlock() != wanted.getBlock() && unweathered(actual.getBlock()) != unweathered(wanted.getBlock())) {
			return false;
		}
		for (Property<?> property : wanted.getProperties()) {
			if (VOLATILE_PROPERTY_NAMES.contains(property.getName()) || property == BlockStateProperties.CHEST_TYPE) {
				continue;
			}
			if (!actual.hasProperty(property)) {
				return false;
			}
			if (!actual.getValue(property).equals(wanted.getValue(property))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Copper as it was placed: a copper roof greens over the years (and may be waxed), and it's still the roof that was
	 * built, so a weathered or waxed copper block matches the fresh one.
	 */
	static net.minecraft.world.level.block.Block unweathered(net.minecraft.world.level.block.Block block) {
		net.minecraft.world.level.block.Block unwaxed = net.minecraft.world.item.HoneycombItem.WAX_OFF_BY_BLOCK.get().getOrDefault(block, block);
		return net.minecraft.world.level.block.WeatheringCopper.getFirst(unwaxed);
	}

	/**
	 * Blocks the builder must not break while clearing (they would lose someone's stuff or can't be broken). A campfire
	 * (it drops what's cooking on it) and a bell hold nothing: an upgrade can move the smoke up a taller chimney.
	 */
	public static boolean isProtected(BlockState state, float destroySpeed) {
		Block block = state.getBlock();
		return destroySpeed < 0 || state.hasBlockEntity() && !(block instanceof SignBlock) && !(block instanceof AbstractBannerBlock)
			&& !(block instanceof AbstractSkullBlock) && !(block instanceof BedBlock) && !(block instanceof net.minecraft.world.level.block.CampfireBlock)
			&& !(block instanceof BellBlock);
	}

	private MaterialRules() {
	}
}
