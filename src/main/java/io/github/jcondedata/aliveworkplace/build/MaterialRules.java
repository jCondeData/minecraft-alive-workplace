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

	/**
	 * Blocks of other mods with no item of their own, placed with another item, by id (no class of theirs): Create's belt
	 * is laid with a belt connector (ROADMAP 39.4's Workshop). A builder lays a belt segment by segment, one connector each.
	 */
	private static final Map<String, String> PLACED_WITH = Map.of("create:belt", "create:belt_connector",
		"create:small_bogey", "create:railway_casing"); // 39.7b: a bogey is train casing used on the track

	/** Whether {@code state} is laid in one piece with its neighbours of the same block (Create's belt): see BuilderWork. */
	public static boolean laidWhole(BlockState state) {
		return "create:belt".equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
	}

	private static Item placedByAnotherItem(Block block) {
		String with = PLACED_WITH.get(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString());
		return with == null ? Items.AIR
			: io.github.jcondedata.aliveworkplace.mc.Lookup.value(net.minecraft.core.registries.BuiltInRegistries.ITEM, net.minecraft.resources.ResourceLocation.parse(with));
	}

	/** Properties that depend on neighbours or change on their own; ignored when checking if a spot is done. */
	private static final Set<String> VOLATILE_PROPERTY_NAMES = Set.of(
		"north", "east", "south", "west", "up", "down", "shape", "waterlogged", "powered", "open", "lit",
		"distance", "persistent", "snowy", "occupied", "triggered", "attached", "disarmed", "in_wall", "has_book",
		"has_record", "bottom", "berries", "age", "stage", "moisture", "power", "note", "instrument",
		// Supplementaries: a pending rotation its block entity applies (and clears) when it loads
		"rotate_tile",
		// Cobblemon (ROADMAP 28.7): a Healing Machine charges by itself, a PC or Pasture Block lights up while in use
		"charge", "on",
		// Cobblemon (ROADMAP 28.13): a Campfire Pot's lid, which its cook shuts and opens
		"lid"
	);

	/**
	 * Properties a furniture mod's piece takes from its neighbours when it's placed (ROADMAP 37.4), so they're no part
	 * of whether the piece is built: CobbleFurnies sofas join up (`type` single, left, middle, right) and its curtains
	 * pair up and hang down (`horizontal_part`, `vertical_part`, `hinge`). By namespace, as M37 goes by ids only: the
	 * same names on other blocks (a slab's `type`, a door's `hinge`) still count.
	 */
	private static final Map<String, Set<String>> CONNECTED_BY_MOD = Map.of(
		"cobblefurnies", Set.of("type", "horizontal_part", "vertical_part", "hinge"));

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
		if (ModdedBlocks.ownCosts(block, out)) {
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
			|| b instanceof BellBlock || b instanceof CakeBlock || modLantern(b);
	}

	/**
	 * B433: a modded lantern (Carved Wood's wooden lanterns) stands on or hangs from its neighbour as vanilla's does, so it
	 * goes up with the decoration, after the trapdoor or post it stands on. Found by its boolean {@code hanging}
	 * property (vanilla's lantern has the same), so no mod class is needed.
	 */
	private static boolean modLantern(Block b) {
		return b.getStateDefinition().getProperty("hanging") instanceof net.minecraft.world.level.block.state.properties.BooleanProperty;
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

	/** The age a pitcher crop grows its upper half at (vanilla's private PitcherCropBlock.DOUBLE_PLANT_AGE_INTERSECTION). */
	private static final int PITCHER_DOUBLE_AGE = 3;

	/** Where the other half of a two-block block goes, or null for normal blocks. */
	@Nullable
	public static BlockPos secondaryPos(BlockPos pos, BlockState state) {
		if (state.getBlock() instanceof net.minecraft.world.level.block.PitcherCropBlock && state.getValue(BlockStateProperties.AGE_4) < PITCHER_DOUBLE_AGE) {
			return null; // B560: a young pitcher crop is one block tall (vanilla's PitcherCropBlock grows its top at age 3)
		}
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
			item = placedByAnotherItem(block);
		}
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

	/** Finishes a block a builder has just placed (a Campfire Pot gets its pot). */
	public static void afterPlaced(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, BlockState state) {
		ModdedBlocks.afterPlaced(level, pos, state);
		io.github.jcondedata.aliveworkplace.hall.VillageWaystones.placed(level, pos, state); // a plaza's waystone, named after the village (41.1)
	}

	/**
	 * A blueprint state turned and flipped as its placement says. Like {@code state.mirror(mirror).rotate(rotation)},
	 * except that a double chest half swaps left and right when mirrored (B438): vanilla's chest only turns its facing, so
	 * a mirrored double chest's halves pointed away from each other and came out as two single chests. A mirror always
	 * flips handedness, whichever way the chest faces.
	 */
	public static BlockState transformed(BlockState state, net.minecraft.world.level.block.Mirror mirror,
										 net.minecraft.world.level.block.Rotation rotation) {
		BlockState out = state.mirror(mirror);
		if (mirror != net.minecraft.world.level.block.Mirror.NONE && out.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
			&& out.hasProperty(net.minecraft.world.level.block.ChestBlock.TYPE)) {
			out = out.setValue(net.minecraft.world.level.block.ChestBlock.TYPE, out.getValue(net.minecraft.world.level.block.ChestBlock.TYPE).getOpposite());
		}
		return out.rotate(rotation);
	}

	/** Adjust a blueprint state before placing it: no free water, no decaying leaves. */
	public static BlockState forPlacement(BlockState state) {
		if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
			state = state.setValue(BlockStateProperties.WATERLOGGED, false);
		}
		if (state.getBlock() instanceof LeavesBlock && state.hasProperty(BlockStateProperties.PERSISTENT)) {
			state = state.setValue(BlockStateProperties.PERSISTENT, true);
		}
		return emptied(state);
	}

	/**
	 * What a block holds as part of its state (B429, B434), never copied: a builder places it empty, as it places a chest
	 * without its contents (BlockEntityData). A lava or powder snow cauldron is built as an empty cauldron (it costs only a
	 * cauldron), a composter at level 0 (no ready bone meal), a beehive or bee nest with no honey, a respawn anchor
	 * uncharged, and a chiseled bookshelf or brewing stand shows no books or bottles (it holds none). A water cauldron is
	 * still built full, as shipped blueprints have it: water is free in vanilla (an endless spring), so it gives nothing
	 * away. Also no glow berries, lectern book or jukebox disc shown (B494), no crop ripe (B493), no furnace lit (B495) and no Mailbox showing mail (B536).
	 * By vanilla's properties, so a modded block that uses them is emptied too.
	 */
	public static BlockState emptied(BlockState state) {
		if (state.is(Blocks.LAVA_CAULDRON) || state.is(Blocks.POWDER_SNOW_CAULDRON)) {
			return Blocks.CAULDRON.defaultBlockState();
		}
		for (Property<?> property : state.getProperties()) {
			if (CONTENT_PROPERTIES.contains(property) || "age".equals(property.getName()) && plantedFresh(state.getBlock())) {
				state = emptyValue(state, property);
			}
		}
		if (state.getBlock() instanceof net.minecraft.world.level.block.PitcherCropBlock && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
			// B560: a pitcher crop is planted as vanilla plants a pod, one block tall (age 0); its top grows by itself, so
			// a ripe crop's upper half is no block of its own (not placed, not charged)
			return Blocks.AIR.defaultBlockState();
		}
		if (state.getBlock() instanceof net.minecraft.world.level.block.AbstractFurnaceBlock && state.hasProperty(BlockStateProperties.LIT)) {
			// B495: a furnace, smoker or blast furnace scanned while burning is built cold (it has no fuel); campfires keep
			// their lit, as blueprints place them lit on purpose
			state = state.setValue(BlockStateProperties.LIT, false);
		}
		return state;
	}

	/**
	 * B493: a crop is planted, never built ripe: wheat and other crops, melon and pumpkin stems, nether wart, a sweet berry
	 * bush and cocoa go in at age 0 (one seed grows; it is no free harvest). Growth is no part of whether it is built.
	 * B560: a pitcher crop too (a double plant, not a CropBlock), one block tall as vanilla plants it.
	 */
	private static boolean plantedFresh(Block block) {
		return block instanceof net.minecraft.world.level.block.CropBlock || block instanceof net.minecraft.world.level.block.StemBlock
			|| block instanceof net.minecraft.world.level.block.PitcherCropBlock
			|| block instanceof net.minecraft.world.level.block.NetherWartBlock || block instanceof net.minecraft.world.level.block.SweetBerryBushBlock
			|| block instanceof net.minecraft.world.level.block.CocoaBlock;
	}

	/** Properties that show what a block holds: emptied when placed, and no part of whether the block is built. */
	private static final Set<Property<?>> CONTENT_PROPERTIES = Set.of(
		BlockStateProperties.LEVEL_COMPOSTER, BlockStateProperties.LEVEL_HONEY, BlockStateProperties.RESPAWN_ANCHOR_CHARGES,
		BlockStateProperties.CHISELED_BOOKSHELF_SLOT_0_OCCUPIED, BlockStateProperties.CHISELED_BOOKSHELF_SLOT_1_OCCUPIED,
		BlockStateProperties.CHISELED_BOOKSHELF_SLOT_2_OCCUPIED, BlockStateProperties.CHISELED_BOOKSHELF_SLOT_3_OCCUPIED,
		BlockStateProperties.CHISELED_BOOKSHELF_SLOT_4_OCCUPIED, BlockStateProperties.CHISELED_BOOKSHELF_SLOT_5_OCCUPIED,
		BlockStateProperties.HAS_BOTTLE_0, BlockStateProperties.HAS_BOTTLE_1, BlockStateProperties.HAS_BOTTLE_2,
		// B493, B494: cave vines' glow berries, a lectern's book and a jukebox's disc (built empty, they hold none)
		BlockStateProperties.BERRIES, BlockStateProperties.HAS_BOOK, BlockStateProperties.HAS_RECORD,
		// B536: our Mailbox's mail flag (built empty, it holds no letters, so it shows none and signals 0)
		io.github.jcondedata.aliveworkplace.mail.MailboxBlock.HAS_MAIL
	);

	private static BlockState emptyValue(BlockState state, Property<?> property) {
		if (property instanceof net.minecraft.world.level.block.state.properties.BooleanProperty b) {
			return state.setValue(b, false);
		}
		if (property instanceof net.minecraft.world.level.block.state.properties.IntegerProperty i) {
			return state.setValue(i, i.getPossibleValues().stream().min(Integer::compare).orElse(0));
		}
		return state;
	}

	/** True if {@code actual} counts as "the right block is already there". */
	public static boolean matches(BlockState actual, BlockState wanted) {
		if (actual == wanted) {
			return true;
		}
		if (wanted.is(Blocks.CAULDRON) && actual.getBlock() instanceof net.minecraft.world.level.block.AbstractCauldronBlock) {
			// A cauldron is built empty (emptied); one filled since is still the cauldron that was built
			return true;
		}
		if (actual.getBlock() != wanted.getBlock() && unweathered(actual.getBlock()) != unweathered(wanted.getBlock())) {
			return false;
		}
		for (Property<?> property : wanted.getProperties()) {
			if (VOLATILE_PROPERTY_NAMES.contains(property.getName()) || property == BlockStateProperties.CHEST_TYPE || CONTENT_PROPERTIES.contains(property)
				|| connected(wanted, property) || property == net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING && facingFree(wanted.getBlock())) {
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
	 * B448: the mod's own facing blocks that hold data (Village Hall, Storehouse, Mailbox, Shop Counter, Grave) only turn
	 * with a turned build since this fix; one built turned before it faces as the blueprint was drawn. It is still the block
	 * that was built: a builder never breaks it (it holds the hall's records, mail, a shop's list, a grave's items), so a
	 * different facing counts as standing, in a finished build (repairs, upgrades, Wonders) and in one still going up.
	 */
	private static boolean facingFree(Block block) {
		return block == io.github.jcondedata.aliveworkplace.registry.ModBlocks.VILLAGE_HALL || block == io.github.jcondedata.aliveworkplace.registry.ModBlocks.STOREHOUSE
			|| block == io.github.jcondedata.aliveworkplace.registry.ModBlocks.MAILBOX || block == io.github.jcondedata.aliveworkplace.registry.ModBlocks.SHOP_COUNTER
			|| block == io.github.jcondedata.aliveworkplace.registry.ModBlocks.GRAVE;
	}

	/** Whether {@code property} of {@code state} is one its mod's piece takes from its neighbours ({@link #CONNECTED_BY_MOD}). */
	private static boolean connected(BlockState state, Property<?> property) {
		for (Map.Entry<String, Set<String>> e : CONNECTED_BY_MOD.entrySet()) {
			if (e.getValue().contains(property.getName())
				&& e.getKey().equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())) {
				return true;
			}
		}
		return false;
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
	 * (it drops what's cooking on it) and a bell hold nothing: an upgrade can move the smoke up a taller chimney. A
	 * Wonder's Foundation Stone stays too (its last stage levels the ground round it).
	 */
	public static boolean isProtected(BlockState state, float destroySpeed) {
		Block block = state.getBlock();
		return destroySpeed < 0 || block == io.github.jcondedata.aliveworkplace.registry.ModBlocks.FOUNDATION_STONE // a Wonder's (35.4)
			|| state.hasBlockEntity() && !(block instanceof SignBlock) && !(block instanceof AbstractBannerBlock)
			&& !(block instanceof AbstractSkullBlock) && !(block instanceof BedBlock) && !(block instanceof net.minecraft.world.level.block.CampfireBlock)
			&& !(block instanceof BellBlock);
	}

	private MaterialRules() {
	}
}
