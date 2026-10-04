package io.github.jcondedata.aliveworkplace.habitat;

import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/**
 * The Habitat Keeper (ROADMAP 28.10): a villager at a Pasture Block, picked with a honey bottle, who keeps the wild
 * Pokémon round her pasture: Poké Snacks set out on lure spots, Saccharine logs slathered with honey, and the shiny,
 * rare and Alpha ones she sights told to the village. Cobblemon's blocks are known by id only. Needs Cobblemon; config
 * {@code habitatKeepers} (and {@code habitatSightings} for the sightings).
 */
public final class HabitatKeepers {
	/** Config switch {@code habitatKeepers}: off, a honey bottle picks no job at a pasture and keepers already hired stand idle. */
	public static boolean ENABLED = true;
	/** Config switch {@code habitatSightings}: off, she tells nobody of the Pokémon she sights. */
	public static boolean SIGHTINGS = true;
	/** How far from the pasture she keeps lure spots and slathers logs. */
	public static final int RANGE = 32;
	/** How far below and above the pasture she looks for Saccharine logs. */
	public static final int LOG_HEIGHT = 16;
	/** How far from the pasture she sights wild Pokémon. */
	public static final int SIGHT_RANGE = 48;
	/** The most blocks the log scan looks at in one tick. */
	public static final int SCAN_PER_TICK = 4096;

	public static final ResourceLocation SACCHARINE_LOG = ResourceLocation.fromNamespaceAndPath("cobblemon", "saccharine_log");
	public static final ResourceLocation SLATHERED_LOG = ResourceLocation.fromNamespaceAndPath("cobblemon", "saccharine_log_slathered");
	public static final ResourceLocation SACCHARINE_SAPLING = ResourceLocation.fromNamespaceAndPath("cobblemon", "saccharine_sapling");

	public static boolean isKeeper(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.HABITAT_KEEPER;
	}

	/** Whether the job can be had: Cobblemon is there with its Pasture Block, and the config switch is on. */
	public static boolean available() {
		return ENABLED && Platform.get().isModLoaded("cobblemon") && BuiltInRegistries.BLOCK.containsKey(ModVillagers.PASTURE_BLOCK);
	}

	/** A honey bottle: what picks the job at a Pasture Block. */
	public static boolean isHoney(ItemStack stack) {
		return available() && stack.is(Items.HONEY_BOTTLE);
	}

	private static boolean is(BlockState state, ResourceLocation id) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(id);
	}

	/** A bare Saccharine log, which honey slathers. */
	public static boolean isBareLog(BlockState state) {
		return is(state, SACCHARINE_LOG);
	}

	/** A Saccharine log slathered with honey. */
	public static boolean isSlathered(BlockState state) {
		return is(state, SLATHERED_LOG);
	}

	/**
	 * The slathered log that honey makes of a bare one, with its honey on the side facing {@code toward} (as Cobblemon's
	 * own honey bottle does on the face a player clicks); null without Cobblemon's slathered log.
	 */
	@Nullable
	public static BlockState slathered(Direction toward) {
		Block block = BuiltInRegistries.BLOCK.getOptional(SLATHERED_LOG).orElse(null);
		if (block == null) {
			return null;
		}
		BlockState state = block.defaultBlockState();
		Property<?> facing = block.getStateDefinition().getProperty("facing");
		if (facing instanceof DirectionProperty direction && direction.getPossibleValues().contains(toward)) {
			state = state.setValue(direction, toward);
		}
		return state;
	}

	/** Slathers the bare log at {@code pos} with one honey bottle: true if it is now slathered. */
	public static boolean slather(Level level, BlockPos pos, Direction toward) {
		BlockState state = level.getBlockState(pos);
		BlockState honeyed = slathered(toward.getAxis().isHorizontal() ? toward : Direction.NORTH);
		if (!isBareLog(state) || honeyed == null) {
			return false;
		}
		return level.setBlock(pos, honeyed, Block.UPDATE_ALL);
	}

	/** The side of the log at {@code log} that faces {@code from} (the keeper), as a horizontal direction. */
	public static Direction faceToward(BlockPos log, BlockPos from) {
		int dx = from.getX() - log.getX();
		int dz = from.getZ() - log.getZ();
		if (dx == 0 && dz == 0) {
			return Direction.NORTH;
		}
		return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	/** How many blocks the log scan covers round one pasture. */
	public static int scanSize() {
		int side = RANGE * 2 + 1;
		return side * side * (LOG_HEIGHT * 2 + 1);
	}

	/** The block at step {@code index} of the log scan round {@code center}. */
	public static BlockPos scanPos(BlockPos center, int index) {
		int side = RANGE * 2 + 1;
		int x = index % side;
		int z = (index / side) % side;
		int y = index / (side * side);
		return center.offset(x - RANGE, y - LOG_HEIGHT, z - RANGE);
	}

	/**
	 * Looks at up to {@code budget} blocks of the scan round {@code center} from step {@code cursor}, adding each
	 * Saccharine log (bare or slathered) to {@code found} once, and returns the next step (back to 0 when the scan has
	 * covered everything). Unloaded chunks are passed over.
	 */
	public static int scan(Level level, BlockPos center, int cursor, int budget, List<BlockPos> found) {
		int size = scanSize();
		int index = Math.max(0, cursor) % size;
		for (int n = 0; n < Math.min(budget, SCAN_PER_TICK); n++) {
			BlockPos pos = scanPos(center, index);
			if (level.isLoaded(pos)) {
				BlockState state = level.getBlockState(pos);
				if ((isBareLog(state) || isSlathered(state)) && !found.contains(pos)) {
					found.add(pos.immutable());
				}
			}
			index++;
			if (index >= size) {
				return 0;
			}
		}
		return index;
	}

	private HabitatKeepers() {
	}
}
